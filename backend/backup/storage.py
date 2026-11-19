"""
Thin wrapper around the Vercel Blob Python SDK (sync API — Django's views
here are sync, so `vercel.blob.sync` matches without introducing asyncio).

Credentials: the SDK auto-resolves `BLOB_READ_WRITE_TOKEN` from the
environment (set via `vercel storage update <store> --add-rw-token`), so
nothing to configure here beyond that env var existing.
"""
from vercel.blob import sync as blob


class StorageNotConfigured(Exception):
    """Raised when BLOB_READ_WRITE_TOKEN isn't set yet — a real, expected
    state before the store is connected, not a bug."""


def _check_configured():
    import os

    if not os.environ.get("BLOB_READ_WRITE_TOKEN"):
        raise StorageNotConfigured(
            "Cloud storage isn't configured yet - the Vercel Blob store needs to be connected "
            "with a static read-write token (vercel storage update <store> --add-rw-token).",
        )


def upload_chunk(pathname: str, data: bytes) -> blob.PutResult:
    """Uploads one chunk as its own private Blob object. Raises blob.BlobError subclasses on failure."""
    _check_configured()
    return blob.put_blob(pathname, data, access="private", allow_overwrite=True)


def download_chunk(pathname: str) -> bytes:
    """Downloads one chunk's full bytes. Raises blob.BlobNotFoundError if it's missing."""
    _check_configured()
    return blob.get_blob(pathname, access="private").body


def delete_blobs(pathnames: list[str]) -> None:
    """Best-effort cleanup (e.g. aborting an upload) — deleting a missing blob is not an error."""
    _check_configured()
    if not pathnames:
        return
    # The SDK accepts up to 1,000 pathnames per call.
    for i in range(0, len(pathnames), 1000):
        blob.delete_blob(pathnames[i : i + 1000])
