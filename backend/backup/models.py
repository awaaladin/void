import uuid

from django.conf import settings
from django.db import models


class CloudFile(models.Model):
    """
    One row per file the user has (or is) uploading to Void Cloud. `key` is
    the logical per-user path — always prefixed with the user's id (see
    `key_for`) so one Blob store safely serves every user.

    Unlike a true S3 bucket, Vercel Blob's Python SDK has no client-direct
    presigned-multipart flow and every request to this server is capped at
    4.5MB (a Vercel Functions platform limit, not ours). So files route
    through this server in chunks — see CloudFileChunk — rather than
    phone-to-bucket directly. Each chunk is its own private Blob object;
    `size_bytes`/`sha256` here are the *declared* values from the client
    until the upload completes, at which point they're verified against
    the actual uploaded chunks.
    """

    class Status(models.TextChoices):
        PENDING = "PENDING"
        UPLOADING = "UPLOADING"
        VERIFIED = "VERIFIED"
        FAILED = "FAILED"

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="cloud_files")
    key = models.CharField(max_length=1024, unique=True)
    relative_path = models.CharField(max_length=1024)
    size_bytes = models.BigIntegerField()
    sha256 = models.CharField(max_length=64, blank=True, default="")
    status = models.CharField(max_length=20, choices=Status.choices, default=Status.PENDING)
    created_at = models.DateTimeField(auto_now_add=True)
    completed_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        indexes = [models.Index(fields=["user", "status"])]

    @staticmethod
    def key_for(user_id, relative_path: str) -> str:
        """Per-user prefix — the only thing standing between users' files in one shared Blob store."""
        return f"users/{user_id}/{relative_path.lstrip('/')}"

    def __str__(self) -> str:
        return self.key


class CloudFileChunk(models.Model):
    """
    One row per uploaded chunk — our own stand-in for S3 multipart "parts",
    since Vercel Blob doesn't offer that for a resumable, request-at-a-time
    client. Each chunk is a fully independent Blob object; a dropped
    connection only costs that one chunk's retry, not the whole file.
    """

    file = models.ForeignKey(CloudFile, on_delete=models.CASCADE, related_name="chunks")
    chunk_index = models.IntegerField()
    blob_pathname = models.CharField(max_length=1024)
    size_bytes = models.BigIntegerField()
    sha256 = models.CharField(max_length=64, blank=True, default="")
    uploaded_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        constraints = [
            models.UniqueConstraint(fields=["file", "chunk_index"], name="unique_chunk_per_file"),
        ]
        indexes = [models.Index(fields=["file", "chunk_index"])]

    def __str__(self) -> str:
        return f"{self.file_id} #{self.chunk_index}"
