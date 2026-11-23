package com.vaultkeep.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * The one error popup every screen uses instead of small inline red text
 * that's easy to miss or scroll past — a failure should interrupt, not
 * whisper. [message] is the real reason whenever the app has one (e.g. "This
 * upload would put you over your storage quota", "Encryption is on but
 * locked...") rather than a bare failure count.
 *
 * Dismissal is tracked locally (per distinct [message]) rather than
 * requiring every ViewModel to add a "clear error" method: closing the
 * dialog just hides this instance of the message, and a genuinely new
 * error (even with the same text) shows again since callers normally
 * reset the field to null before starting a new action anyway.
 */
@Composable
fun VoidErrorDialog(message: String?) {
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(message) { dismissed = false }
    if (message == null || dismissed) return

    AlertDialog(
        onDismissRequest = { dismissed = true },
        confirmButton = { TextButton(onClick = { dismissed = true }) { Text("OK") } },
        title = { Text("Something went wrong", color = MaterialTheme.colorScheme.error) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
    )
}
