"""
Thin boto3 wrapper around whatever S3-compatible bucket is configured
(Cloudflare R2 or Backblaze B2 — both speak the S3 API). Every call here
only ever touches presigned URLs or multipart-upload bookkeeping; actual
file bytes go straight from the phone to the bucket, never through this
server.
"""
import boto3
from botocore.client import Config
from django.conf import settings


class StorageNotConfigured(Exception):
    """Raised when S3_* env vars aren't set yet — a real, expected state
    before a bucket has been provisioned, not a bug."""


def get_s3_client():
    if not (settings.S3_ENDPOINT_URL and settings.S3_ACCESS_KEY_ID and settings.S3_SECRET_ACCESS_KEY):
        raise StorageNotConfigured(
            "Cloud storage isn't configured yet - set S3_ENDPOINT_URL, S3_ACCESS_KEY_ID, "
            "S3_SECRET_ACCESS_KEY and S3_BUCKET_NAME (Cloudflare R2 or Backblaze B2 credentials).",
        )
    return boto3.client(
        "s3",
        endpoint_url=settings.S3_ENDPOINT_URL,
        aws_access_key_id=settings.S3_ACCESS_KEY_ID,
        aws_secret_access_key=settings.S3_SECRET_ACCESS_KEY,
        config=Config(signature_version="s3v4"),
        region_name="auto",
    )


def bucket_name() -> str:
    if not settings.S3_BUCKET_NAME:
        raise StorageNotConfigured("S3_BUCKET_NAME is not set.")
    return settings.S3_BUCKET_NAME
