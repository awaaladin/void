# VaultKeep

A phone backup app (Android, Kotlin/Compose) that backs up to USB/OTG, a
Wi-Fi NAS (SMB/WebDAV), our own cloud backend, and the user's Google Drive —
incremental, resumable, and tuned for low-end phones and unstable networks.
Owner-installed and owner-visible: it runs quietly (no per-file spam, minimal
notification) but is never hidden from the device's own user.

## Structure

- `android/` — the Kotlin/Compose app
- `backend/` — Django REST backend (auth, presigned uploads, quotas)

## Phase 1 status (this commit)

Design tokens, theme (light/dark from the same tokens), navigation shell, and
a Home screen wired to mock data. No real backup logic yet — that starts
Phase 2.

Backend: Django project skeleton (settings, a custom `accounts.User` model,
a `/api/accounts/health/` endpoint) so the layout is real from day one. Auth,
presigned R2/B2 uploads, and quotas land in Phase 5/8.

## Run & test — Phase 1

### Android
1. Open `android/` in Android Studio (Koala+). Let it generate the Gradle
   wrapper on first sync (File > Sync, or it prompts automatically).
2. Run the `app` configuration on an emulator or device (minSdk 26).
3. You should see the Home screen: title, the BackupHeroCard (mock
   "Backing up…" state with an animated progress ring), four destination
   cards (USB/NAS/Cloud/Drive), and five toggleable backup-set rows.
4. Toggle a backup set switch — it should flip instantly and persist across
   recomposition (backed by `HomeViewModel`'s `StateFlow`).
5. Switch system dark/light mode — both themes should look intentional, not
   just inverted.
6. Unit tests:
   ```
   cd android
   ./gradlew testDebugUnitTest
   ```
   Covers `HomeViewModelTest` (mock state shape, toggle isolation, WhatsApp
   row staying non-interactive).

### Backend
```
cd backend
python -m venv .venv && .venv\Scripts\activate   # Windows
pip install -r requirements.txt
cp .env.example .env   # fill in a real DJANGO_SECRET_KEY; Postgres optional for now
python manage.py migrate
python manage.py runserver
```
Then `GET http://127.0.0.1:8000/api/accounts/health/` should return
`{"status": "ok", "service": "vaultkeep-backend"}`.

## Next phase (2)

SAF folder picking, Room incremental index (`FileEntry`, `BackupRecord`),
and a real copy-to-USB flow replacing the mock hero/destination data.
