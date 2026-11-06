from django.urls import path

from . import views

urlpatterns = [
    path("uploads/create/", views.CreateUploadView.as_view(), name="create-upload"),
    path("uploads/presign-part/", views.PresignPartView.as_view(), name="presign-part"),
    path("uploads/complete/", views.CompleteUploadView.as_view(), name="complete-upload"),
    path("uploads/abort/", views.AbortUploadView.as_view(), name="abort-upload"),
    path("files/", views.ListFilesView.as_view(), name="list-files"),
    path("files/<uuid:file_id>/presign-download/", views.PresignDownloadView.as_view(), name="presign-download"),
    path("usage/", views.UsageView.as_view(), name="usage"),
]
