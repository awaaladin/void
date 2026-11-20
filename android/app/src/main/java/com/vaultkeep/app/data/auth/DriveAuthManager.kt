package com.vaultkeep.app.data.auth

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps the Identity Services Authorization API for the one scope Void
 * needs: `drive.file` (only files/folders this app itself creates — never
 * the user's whole Drive). This is deliberately "online access" only, no
 * `requestOfflineAccess`/server auth code: Google Play Services caches the
 * grant on-device, so calling [authorize] again before a later backup
 * returns a fresh access token non-interactively (no PendingIntent) unless
 * the user has revoked access — no refresh token to store, no backend
 * change needed, nothing for this app to manage long-term.
 *
 * No client ID appears anywhere in this code: an Android-type OAuth client
 * is resolved automatically by Google from this app's package name +
 * signing certificate SHA-1, which is why the debug keystore has to stay
 * fixed (see app/debug.keystore and its signingConfig in build.gradle.kts).
 */
@Singleton
class DriveAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    sealed class AuthorizeOutcome {
        data class Authorized(val accessToken: String) : AuthorizeOutcome()
        data class NeedsConsent(val intentSender: IntentSender) : AuthorizeOutcome()
    }

    private val request: AuthorizationRequest by lazy {
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()
    }

    suspend fun authorize(): AuthorizeOutcome {
        val result = Identity.getAuthorizationClient(context).authorize(request).await()
        val pendingIntent = result.pendingIntent
        return if (result.hasResolution() && pendingIntent != null) {
            AuthorizeOutcome.NeedsConsent(pendingIntent.intentSender)
        } else {
            AuthorizeOutcome.Authorized(
                result.accessToken ?: error("Drive authorization succeeded but returned no access token"),
            )
        }
    }

    /** Call from the Activity-result callback after launching [AuthorizeOutcome.NeedsConsent]'s intentSender. */
    fun accessTokenFromConsentResult(data: Intent?): String {
        val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        return result.accessToken ?: error("Drive consent completed but returned no access token")
    }

    companion object {
        private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}
