from django.http import JsonResponse


def health(request):
    """Trivial liveness check so the Android app's networking layer has
    something real to hit before Phase 5 adds actual auth/upload endpoints."""
    return JsonResponse({"status": "ok", "service": "void-backend"})
