from rest_framework import serializers

from .models import CloudFile


class CloudFileSerializer(serializers.ModelSerializer):
    class Meta:
        model = CloudFile
        fields = ["id", "relative_path", "size_bytes", "sha256", "status", "created_at", "completed_at"]
        read_only_fields = fields


class CreateUploadSerializer(serializers.Serializer):
    relative_path = serializers.CharField(max_length=1024)
    size_bytes = serializers.IntegerField(min_value=0)
    sha256 = serializers.CharField(max_length=64, required=False, allow_blank=True)


class CompleteUploadSerializer(serializers.Serializer):
    file_id = serializers.UUIDField()
    total_chunks = serializers.IntegerField(min_value=0)
    sha256 = serializers.CharField(max_length=64, required=False, allow_blank=True)


class AbortUploadSerializer(serializers.Serializer):
    file_id = serializers.UUIDField()
