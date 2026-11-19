package com.goldrift.instadm.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.goldrift.instadm.R
import com.goldrift.instadm.update.UpdateApk
import com.goldrift.instadm.update.UpdateManifest
import com.goldrift.instadm.update.UpdateState
import java.util.Locale

@Composable
fun UpdateDialog(
    update: UpdateManifest,
    apk: UpdateApk,
    state: UpdateState,
    canInstall: Boolean,
    onInstall: () -> Unit,
    onOpenInstallPermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    val busy = state is UpdateState.Downloading || state is UpdateState.Installing
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.updates_dialog_title, update.version)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.updates_dialog_size, formatMegabytes(apk.size)))
                if (update.notes.isNotBlank()) {
                    Text(
                        text = update.notes,
                        modifier = Modifier
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                when (state) {
                    is UpdateState.Downloading -> LinearProgressIndicator(
                        progress = { state.fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    UpdateState.Installing -> Text(
                        stringResource(R.string.updates_state_installing),
                    )
                    is UpdateState.Failed -> Text(
                        text = state.message ?: stringResource(R.string.updates_install_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Unit
                }
                if (!canInstall) Text(stringResource(R.string.updates_dialog_permission))
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = if (canInstall) onInstall else onOpenInstallPermission,
            ) {
                Text(
                    stringResource(
                        if (canInstall) {
                            R.string.updates_dialog_install
                        } else {
                            R.string.updates_dialog_permission_action
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(R.string.chat_cancel))
            }
        },
    )
}

private fun formatMegabytes(bytes: Long): String =
    String.format(Locale.US, "%.1f", bytes / (1024f * 1024f))
