"""
Django settings for the Void backend.

Auth (username/password + JWT) and Void Cloud storage (chunked uploads to
Vercel Blob — see backup/storage.py for why it's chunked rather than a
direct S3-style presigned multipart) are both live as of Phase 5.
"""
import os
from pathlib import Path

import dj_database_url
from dotenv import load_dotenv

load_dotenv()

BASE_DIR = Path(__file__).resolve().parent.parent

SECRET_KEY = os.environ.get("DJANGO_SECRET_KEY", "dev-only-change-me")
DEBUG = os.environ.get("DJANGO_DEBUG", "true").lower() == "true"
ALLOWED_HOSTS = os.environ.get(
    "DJANGO_ALLOWED_HOSTS",
    # ".vercel.app" (leading dot) matches the domain and every subdomain, so
    # both preview and production deploy URLs work without per-deploy config.
    "localhost,127.0.0.1,.vercel.app",
).split(",")

INSTALLED_APPS = [
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.staticfiles",
    "rest_framework",
    "corsheaders",
    "accounts",
    "backup",
]

MIDDLEWARE = [
    "corsheaders.middleware.CorsMiddleware",
    "django.middleware.security.SecurityMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
]

ROOT_URLCONF = "config.urls"

TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.debug",
                "django.template.context_processors.request",
                "django.contrib.auth.context_processors.auth",
            ],
        },
    },
]

WSGI_APPLICATION = "config.wsgi.application"

# DATABASE_URL (e.g. from Neon/Supabase/any managed Postgres) takes priority —
# that's the one env var most hosting dashboards hand you directly. Falls
# back to the individual POSTGRES_* vars for local docker-compose-style dev.
# Until a real DATABASE_URL is set, this still lets the app boot (e.g. for
# /api/accounts/health/) — only DB-backed requests fail until one is wired up.
if os.environ.get("DATABASE_URL"):
    DATABASES = {
        "default": dj_database_url.parse(os.environ["DATABASE_URL"], conn_max_age=600, ssl_require=True),
    }
else:
    DATABASES = {
        "default": {
            "ENGINE": "django.db.backends.postgresql",
            "NAME": os.environ.get("POSTGRES_DB", "vaultkeep"),
            "USER": os.environ.get("POSTGRES_USER", "vaultkeep"),
            "PASSWORD": os.environ.get("POSTGRES_PASSWORD", "vaultkeep"),
            "HOST": os.environ.get("POSTGRES_HOST", "localhost"),
            "PORT": os.environ.get("POSTGRES_PORT", "5432"),
        }
    }

AUTH_USER_MODEL = "accounts.User"

LANGUAGE_CODE = "en-us"
TIME_ZONE = "UTC"
USE_I18N = True
USE_TZ = True

STATIC_URL = "static/"
DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

REST_FRAMEWORK = {
    "DEFAULT_AUTHENTICATION_CLASSES": (
        "rest_framework_simplejwt.authentication.JWTAuthentication",
    ),
}

# Phase 5: Void Cloud storage via Vercel Blob (backup/storage.py). Credentials
# resolve automatically from BLOB_READ_WRITE_TOKEN in the environment — no
# settings entry needed here, see backup/storage.py for why.

CORS_ALLOWED_ORIGINS: list[str] = []
