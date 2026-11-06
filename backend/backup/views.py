from django.db.models import Sum
from django.shortcuts import get_object_or_404
from django.utils import timezone
from rest_framework import status
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import CloudFile
from .serializers import (
    AbortUploadSerializer,
    CloudFileSerializer,
    CompleteUploadSerializer,
    CreateUploadSerializer,
    PresignPartSerializer,
)
from .storage import StorageNotConfigured, bucket_name, get_s3_client

# Presigned part/download URLs are valid for an hour — long enough for a slow
# mobile upload of one chunk, short enough not to leak access indefinitely.
PRESIGN_EXPIRY_SECONDS = 3600


def _storage_error_response(exc: StorageNotConfigured) -> Response:
    return Response({"error": str(exc)}, status=status.HTTP_503_SERVICE_UNAVAILABLE)


class CreateUploadView(APIView):
    """Starts an S3 multipart upload and records the file row (PENDING)."""

    permission_classes = [IsAuthenticated]

    def post(self, request):
        serializer = CreateUploadSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        key = CloudFile.key_for(request.user.id, data["relative_path"])

        try:
            s3 = get_s3_client()
            created = s3.create_multipart_upload(Bucket=bucket_name(), Key=key)
        except StorageNotConfigured as exc:
            return _storage_error_response(exc)

        cloud_file, _ = CloudFile.objects.update_or_create(
            key=key,
            defaults={
                "user": request.user,
                "relative_path": data["relative_path"],
                "size_bytes": data["size_bytes"],
                "sha256": data.get("sha256", ""),
                "status": CloudFile.Status.UPLOADING,
                "upload_id": created["UploadId"],
                "completed_at": None,
            },
        )

        return Response(
            {"file_id": str(cloud_file.id), "key": key, "upload_id": created["UploadId"]},
            status=status.HTTP_201_CREATED,
        )


class PresignPartView(APIView):
    """Hands back a presigned PUT URL for one multipart-upload part."""

    permission_classes = [IsAuthenticated]

    def post(self, request):
        serializer = PresignPartSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        cloud_file = get_object_or_404(CloudFile, id=data["file_id"], user=request.user)
        if not cloud_file.upload_id:
            return Response({"error": "No multipart upload in progress for this file."}, status=status.HTTP_409_CONFLICT)

        try:
            s3 = get_s3_client()
            url = s3.generate_presigned_url(
                "upload_part",
                Params={
                    "Bucket": bucket_name(),
                    "Key": cloud_file.key,
                    "UploadId": cloud_file.upload_id,
                    "PartNumber": data["part_number"],
                },
                ExpiresIn=PRESIGN_EXPIRY_SECONDS,
            )
        except StorageNotConfigured as exc:
            return _storage_error_response(exc)

        return Response({"url": url})


class CompleteUploadView(APIView):
    """Finalizes the multipart upload once every part has been PUT directly to the bucket."""

    permission_classes = [IsAuthenticated]

    def post(self, request):
        serializer = CompleteUploadSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        cloud_file = get_object_or_404(CloudFile, id=data["file_id"], user=request.user)

        try:
            s3 = get_s3_client()
            s3.complete_multipart_upload(
                Bucket=bucket_name(),
                Key=cloud_file.key,
                UploadId=cloud_file.upload_id,
                MultipartUpload={"Parts": data["parts"]},
            )
        except StorageNotConfigured as exc:
            return _storage_error_response(exc)
        except Exception as exc:  # genuine S3-side failure (bad ETags, etc.) — surface it, don't silently mark verified
            cloud_file.status = CloudFile.Status.FAILED
            cloud_file.save(update_fields=["status"])
            return Response({"error": str(exc)}, status=status.HTTP_502_BAD_GATEWAY)

        cloud_file.status = CloudFile.Status.VERIFIED
        cloud_file.upload_id = ""
        cloud_file.completed_at = timezone.now()
        cloud_file.save(update_fields=["status", "upload_id", "completed_at"])

        return Response(CloudFileSerializer(cloud_file).data)


class AbortUploadView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request):
        serializer = AbortUploadSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        cloud_file = get_object_or_404(CloudFile, id=serializer.validated_data["file_id"], user=request.user)

        if cloud_file.upload_id:
            try:
                s3 = get_s3_client()
                s3.abort_multipart_upload(Bucket=bucket_name(), Key=cloud_file.key, UploadId=cloud_file.upload_id)
            except StorageNotConfigured:
                pass  # nothing to abort on a bucket that was never configured

        cloud_file.status = CloudFile.Status.FAILED
        cloud_file.upload_id = ""
        cloud_file.save(update_fields=["status", "upload_id"])
        return Response(status=status.HTTP_204_NO_CONTENT)


class ListFilesView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        files = CloudFile.objects.filter(user=request.user).order_by("-created_at")
        return Response(CloudFileSerializer(files, many=True).data)


class PresignDownloadView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request, file_id):
        cloud_file = get_object_or_404(CloudFile, id=file_id, user=request.user, status=CloudFile.Status.VERIFIED)

        try:
            s3 = get_s3_client()
            url = s3.generate_presigned_url(
                "get_object",
                Params={"Bucket": bucket_name(), "Key": cloud_file.key},
                ExpiresIn=PRESIGN_EXPIRY_SECONDS,
            )
        except StorageNotConfigured as exc:
            return _storage_error_response(exc)

        return Response({"url": url})


class UsageView(APIView):
    """
    Storage used so far, plus the plan's quota. Per product decision, the
    default quota is effectively unbounded — Void is meant to hold as much
    as a user throws at it, not nudge them toward a paid tier early.
    """

    permission_classes = [IsAuthenticated]

    # 10 TB — not infinite (a literal unlimited quota makes abuse/runaway
    # billing undetectable), but far beyond what any personal backup needs.
    DEFAULT_QUOTA_BYTES = 10 * 1024 * 1024 * 1024 * 1024

    def get(self, request):
        used = CloudFile.objects.filter(user=request.user, status=CloudFile.Status.VERIFIED).aggregate(
            total=Sum("size_bytes"),
        )["total"] or 0
        return Response({"used_bytes": used, "quota_bytes": self.DEFAULT_QUOTA_BYTES, "plan": request.user.plan})
