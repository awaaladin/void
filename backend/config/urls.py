from django.urls import include, path

urlpatterns = [
    path("api/accounts/", include("accounts.urls")),
    # path("api/backup/", include("backup.urls")),  # added in Phase 5
]
