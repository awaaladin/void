import uuid

from django.contrib.auth.models import AbstractUser
from django.db import models


class User(AbstractUser):
    """
    Custom user so each account can carry a Google subject id and a plan,
    without an awkward migration later. Per-user storage isolation (Phase 8)
    is enforced by prefixing every object key in the bucket with str(id).
    """
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    google_sub = models.CharField(max_length=255, unique=True, null=True, blank=True)
    plan = models.CharField(
        max_length=20,
        choices=[("free", "Free"), ("paid", "Paid")],
        default="free",
    )

    def __str__(self) -> str:
        return self.email or self.username
