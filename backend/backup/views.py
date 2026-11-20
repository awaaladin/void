import hashlib

from django.db.models import Sum
from django.http import HttpResponse
from django.shortcuts import get_object_or_404
from django.utils import timezone
from rest_framework import status
from rest_framework.parsers import BaseParser
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import CloudFile, CloudFileChunk
from .serializers import (
    AbortUploadSerializer,
    CloudFileSerializer,
    CompleteUploadSerializer,
    CreateUploadSerializer,
)
from .storage import StorageNotConfigured, delete_blobs, download_chunk, upload_chunk

# Vercel Functions cap every request body at 4.5MB platform-wide; 4MB per
# chunk leaves headroom for headers so we never brush that ceiling.
MAX_CHUNK_SIZE_BYTES = 4 * 1024 * 1024


class RawBinaryParser(BaseParser):
    """Chunk bytes arrive as the literal request body — no JSON, no
    multipart/base64 overhead eating into the already-tight 4.5MB cap."""

    media_type = "application/octet-stream"

    def parse(self, stream, media_type=None, parser_context=None):
        return stream.read()


def _storage_error_response(exc: StorageNotConfigured) -> Response:
    return Response({"error": str(exc)}, status=status.HTTP_503_SERVICE_UNAVAILABLE)


class CreateUploadView(APIView):
    """
    Registers the file (PENDING/UPLOADING) before any chunk arrives.

    Dedup: if the client already knows this file's (plaintext) sha256 and
    we already have a VERIFIED file with that same hash for this user,
    link the new file straight to the existing chunks and mark it VERIFIED
    immediately — no bytes need to move at all, encrypted or not (the
    hash is always the plaintext hash, computed before encryption, same
    content in = same hash out regardless of the random IV encryption
    used this time). Scoped to `user=request.user` only: never reuse
    another user's objects, even if the content happens to match.
    """

    permission_classes = [IsAuthenticated]

    def post(self, request):
        serializer = CreateUploadSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        key = CloudFile.key_for(request.user.id, data["relative_path"])
        sha256 = data.get("sha256", "")
        size_bytes = data["size_bytes"]

        # Re-uploading an existing path (retry, or a changed file at the same path) replaces
        # that path's own prior contribution to `used`, so only count it once here.
        already_counted = CloudFile.objects.filter(
            user=request.user, key=key, status=CloudFile.Status.VERIFIED,
        ).aggregate(total=Sum("size_bytes"))["total"] or 0
        used = _used_bytes(request.user) - already_counted
        if used + size_bytes > request.user.quota_bytes:
            return Response(
                {
                    "error": (
                        f"This upload would put you over your storage quota "
                        f"({used + size_bytes} of {request.user.quota_bytes} bytes). "
                        "Free up space or delete some backed-up files first."
                    ),
                },
                status=status.HTTP_403_FORBIDDEN,
            )

        dedup_source = None
        if sha256:
            dedup_source = (
                CloudFile.objects.filter(user=request.user, sha256=sha256, status=CloudFile.Status.VERIFIED)
                .exclude(key=key)
                .first()
            )

        cloud_file, created = CloudFile.objects.update_or_create(
            key=key,
            defaults={
                "user": request.user,
                "relative_path": data["relative_path"],
                "size_bytes": data["size_bytes"],
                "sha256": sha256,
                "status": CloudFile.Status.UPLOADING,
                "completed_at": None,
            },
        )
        if not created:
            # Re-uploading an existing path (e.g. retry) — drop any stale chunks from a previous attempt.
            stale = list(cloud_file.chunks.values_list("blob_pathname", flat=True))
            delete_blobs(stale)
            cloud_file.chunks.all().delete()

        if dedup_source:
            CloudFileChunk.objects.bulk_create(
                [
                    CloudFileChunk(
                        file=cloud_file,
                        chunk_index=chunk.chunk_index,
                        blob_pathname=chunk.blob_pathname,
                        size_bytes=chunk.size_bytes,
                        sha256=chunk.sha256,
                    )
                    for chunk in dedup_source.chunks.all()
                ],
            )
            cloud_file.status = CloudFile.Status.VERIFIED
            cloud_file.size_bytes = dedup_source.size_bytes
            cloud_file.completed_at = timezone.now()
            cloud_file.save(update_fields=["status", "size_bytes", "completed_at"])
            return Response({"file_id": str(cloud_file.id), "key": key, "deduped": True}, status=status.HTTP_201_CREATED)

        return Response({"file_id": str(cloud_file.id), "key": key, "deduped": False}, status=status.HTTP_201_CREATED)


class ChunkUploadView(APIView):
    """
    POST /api/backup/uploads/chunk/?file_id=...&chunk_index=...
    Body: raw chunk bytes (<= MAX_CHUNK_SIZE_BYTES).
    Each chunk becomes its own private Blob object; re-POSTing the same
    (file_id, chunk_index) overwrites it, so a dropped connection just
    means retrying that one chunk, not the whole file.
    """

    permission_classes = [IsAuthenticated]
    parser_classes = [RawBinaryParser]

    def post(self, request):
        file_id = request.query_params.get("file_id")
        chunk_index_raw = request.query_params.get("chunk_index")
        if not file_id or chunk_index_raw is None:
            return Response({"error": "file_id and chunk_index query params are required."}, status=status.HTTP_400_BAD_REQUEST)
        try:
            chunk_index = int(chunk_index_raw)
        except ValueError:
            return Response({"error": "chunk_index must be an integer."}, status=status.HTTP_400_BAD_REQUEST)

        cloud_file = get_object_or_404(CloudFile, id=file_id, user=request.user)

        data: bytes = request.data
        if len(data) > MAX_CHUNK_SIZE_BYTES:
            return Response(
                {"error": f"Chunk exceeds the {MAX_CHUNK_SIZE_BYTES} byte limit."},
                status=status.HTTP_400_BAD_REQUEST,
            )

        pathname = f"{cloud_file.key}.chunk{chunk_index:06d}"
        try:
            upload_chunk(pathname, data)
        except StorageNotConfigured as exc:
            return _storage_error_response(exc)
        except Exception as exc:
            return Response({"error": f"Chunk upload failed: {exc}"}, status=status.HTTP_502_BAD_GATEWAY)

        chunk_sha256 = hashlib.sha256(data).hexdigest()
        CloudFileChunk.objects.update_or_create(
            file=cloud_file,
            chunk_index=chunk_index,
            defaults={"blob_pathname": pathname, "size_bytes": len(data), "sha256": chunk_sha256},
        )

        return Response({"chunk_index": chunk_index, "size_bytes": len(data)}, status=status.HTTP_201_CREATED)


class CompleteUploadView(APIView):
    """Verifies every expected chunk (0..total_chunks-1) landed, then marks the file VERIFIED."""

    permission_classes = [IsAuthenticated]

    def post(self, request):
        serializer = CompleteUploadSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        cloud_file = get_object_or_404(CloudFile, id=data["file_id"], user=request.user)
        total_chunks = data["total_chunks"]

        existing_indices = set(cloud_file.chunks.values_list("chunk_index", flat=True))
        expected_indices = set(range(total_chunks))
        missing = sorted(expected_indices - existing_indices)
        if missing:
            return Response(
                {"error": f"Missing chunk(s): {missing[:10]}{'…' if len(missing) > 10 else ''}"},
                status=status.HTTP_409_CONFLICT,
            )

        total_bytes = cloud_file.chunks.aggregate(total=Sum("size_bytes"))["total"] or 0

        cloud_file.status = CloudFile.Status.VERIFIED
        cloud_file.size_bytes = total_bytes
        if data.get("sha256"):
            cloud_file.sha256 = data["sha256"]
        cloud_file.completed_at = timezone.now()
        cloud_file.save(update_fields=["status", "size_bytes", "sha256", "completed_at"])

        return Response(CloudFileSerializer(cloud_file).data)


class AbortUploadView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request):
        serializer = AbortUploadSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        cloud_file = get_object_or_404(CloudFile, id=serializer.validated_data["file_id"], user=request.user)

        pathnames = list(cloud_file.chunks.values_list("blob_pathname", flat=True))
        try:
            delete_blobs(pathnames)
        except StorageNotConfigured:
            pass  # nothing to clean up on a store that was never configured
        cloud_file.chunks.all().delete()

        cloud_file.status = CloudFile.Status.FAILED
        cloud_file.save(update_fields=["status"])
        return Response(status=status.HTTP_204_NO_CONTENT)


class DownloadChunkView(APIView):
    """
    GET /api/backup/uploads/chunk/?file_id=...&chunk_index=...
    Returns the raw chunk bytes for restore — the download-side mirror of
    ChunkUploadView. The Android client reassembles chunks 0..chunk_count-1
    (chunk_count comes from CloudFileSerializer) into the original file.
    """

    permission_classes = [IsAuthenticated]

    def get(self, request):
        file_id = request.query_params.get("file_id")
        chunk_index_raw = request.query_params.get("chunk_index")
        if not file_id or chunk_index_raw is None:
            return Response({"error": "file_id and chunk_index query params are required."}, status=status.HTTP_400_BAD_REQUEST)
        try:
            chunk_index = int(chunk_index_raw)
        except ValueError:
            return Response({"error": "chunk_index must be an integer."}, status=status.HTTP_400_BAD_REQUEST)

        cloud_file = get_object_or_404(CloudFile, id=file_id, user=request.user)
        chunk = get_object_or_404(CloudFileChunk, file=cloud_file, chunk_index=chunk_index)

        try:
            data = download_chunk(chunk.blob_pathname)
        except StorageNotConfigured as exc:
            return _storage_error_response(exc)
        except Exception as exc:
            return Response({"error": f"Chunk download failed: {exc}"}, status=status.HTTP_502_BAD_GATEWAY)

        return HttpResponse(data, content_type="application/octet-stream")


class ListFilesView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        files = CloudFile.objects.filter(user=request.user).order_by("-created_at")
        return Response(CloudFileSerializer(files, many=True).data)


def _used_bytes(user) -> int:
    return CloudFile.objects.filter(user=user, status=CloudFile.Status.VERIFIED).aggregate(
        total=Sum("size_bytes"),
    )["total"] or 0


class UsageView(APIView):
    """
    Storage used so far, plus the plan's quota (see User.quota_bytes) — a
    generous default, not a hard product cap. Void doesn't sell storage
    tiers; this exists so the app can warn a user before they genuinely
    run out, not to gate features.
    """

    permission_classes = [IsAuthenticated]

    def get(self, request):
        used = _used_bytes(request.user)
        return Response({"used_bytes": used, "quota_bytes": request.user.quota_bytes, "plan": request.user.plan})
