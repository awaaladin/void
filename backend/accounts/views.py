from django.http import JsonResponse
from rest_framework import status
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework_simplejwt.tokens import RefreshToken

from .serializers import RegisterSerializer


def health(request):
    """Trivial liveness check — no DB touched, works even before a real
    Postgres is wired up via DATABASE_URL."""
    return JsonResponse({"status": "ok", "service": "void-backend"})


class RegisterView(APIView):
    """
    Username/password registration — a real, working auth path for Void
    Cloud now, ahead of full Google sign-in (that's Phase 8, which also
    needs a Google Cloud OAuth app only the account owner can create).
    """

    permission_classes = [AllowAny]

    def post(self, request):
        serializer = RegisterSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()
        refresh = RefreshToken.for_user(user)
        return Response(
            {"access": str(refresh.access_token), "refresh": str(refresh)},
            status=status.HTTP_201_CREATED,
        )
