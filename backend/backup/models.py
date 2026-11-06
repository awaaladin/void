import uuid

from django.conf import settings
from django.db import models


class CloudFile(models.Model):
    """
    One row per file the user has (or is) uploading to Void Cloud. `key` is
    the full object key in the bucket — always prefixed with the user's id
    (see `key_for`) so one bucket safely serves every user. Files never pass
    through this server: the Android app uploads parts directly to R2/B2
    using the presigned URLs these endpoints hand out.
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
    upload_id = models.CharField(max_length=255, blank=True, default="")  # S3 multipart upload id while in progress
    created_at = models.DateTimeField(auto_now_add=True)
    completed_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        indexes = [models.Index(fields=["user", "status"])]

    @staticmethod
    def key_for(user_id, relative_path: str) -> str:
        """Per-user prefix — the only thing standing between users' files in one shared bucket."""
        return f"users/{user_id}/{relative_path.lstrip('/')}"

    def __str__(self) -> str:
        return self.key
