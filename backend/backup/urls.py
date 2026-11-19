from django.urls import path

from . import views

urlpatterns = [
    path("uploads/create/", views.CreateUploadView.as_view(), name="create-upload"),
    path("uploads/chunk/", views.ChunkUploadView.as_view(), name="upload-chunk"),
    path("uploads/complete/", views.CompleteUploadView.as_view(), name="complete-upload"),
    path("uploads/abort/", views.AbortUploadView.as_view(), name="abort-upload"),
    path("files/", views.ListFilesView.as_view(), name="list-files"),
    path("usage/", views.UsageView.as_view(), name="usage"),
]
