package com.goldrift.instadm.ui.settings

import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.goldrift.instadm.R
import com.goldrift.instadm.data.AppSettings
import com.goldrift.instadm.data.ThemeMode
import com.goldrift.instadm.ui.components.EmojiPickerDialog
import com.goldrift.instadm.ui.theme.InstaDMTheme
import com.goldrift.instadm.update.UpdateApk
import com.goldrift.instadm.update.UpdateInstaller
import com.goldrift.instadm.update.UpdateManifest
import com.goldrift.instadm.update.UpdateState
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Device-local preferences for appearance, chat signals, notifications, and
 * the media cache. The theme selector is the screen's single expressive
 * accent; the remaining rows stay on quiet tonal containers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onDoubleTapEmojiChange: (String) -> Unit,
    onSendTypingChange: (Boolean) -> Unit,
    onSendReadReceiptsChange: (Boolean) -> Unit,
    onShowNotificationPreviewsChange: (Boolean) -> Unit,
    onVoiceBackgroundPlaybackChange: (Boolean) -> Unit,
    onAutoCheckUpdatesChange: (Boolean) -> Unit,
    updateState: UpdateState,
    installedVersion: String,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
    onClearMediaCache: suspend () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showEmojiPicker by remember { mutableStateOf(false) }
    var confirmClearCache by remember { mutableStateOf(false) }
    var clearingCache by remember { mutableStateOf(false) }
    var updateDialog by remember { mutableStateOf<Pair<UpdateManifest, UpdateApk>?>(null) }
    var canInstall by remember { mutableStateOf(UpdateInstaller.canInstall(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    // Granting install permission happens on a system page, so the dialog
    // re-reads it when the screen comes back instead of staying stale.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canInstall = UpdateInstaller.canInstall(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.chat_back),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { innerPadding ->
        val listTopShape = RoundedCornerShape(
            topStart = MaterialTheme.shapes.extraLarge.topStart,
            topEnd = MaterialTheme.shapes.extraLarge.topEnd,
            bottomEnd = CornerSize(0.dp),
            bottomStart = CornerSize(0.dp),
        )
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                ),
            shape = listTopShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            val navBarBottom = WindowInsets.navigationBars
                .asPaddingValues()
                .calculateBottomPadding()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = navBarBottom + 24.dp,
                ),
            ) {
                item(key = "appearance") {
                    SettingsSectionHeader(R.string.settings_section_appearance)
                }
                item(key = "appearance-group") {
                    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                        SegmentedListItem(
                            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                            colors = settingsRowColors(),
                            content = {
                                Column {
                                    Text(
                                        text = stringResource(R.string.settings_theme),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    ThemeModeGroup(
                                        selected = settings.themeMode,
                                        onSelect = onThemeModeChange,
                                    )
                                }
                            },
                            supportingContent = {
                                Text(stringResource(R.string.settings_theme_supporting))
                            },
                        )
                        SettingSwitchRow(
                            checked = settings.dynamicColor && dynamicColorSupported,
                            onCheckedChange = onDynamicColorChange,
                            icon = Icons.Rounded.Palette,
                            label = stringResource(R.string.settings_dynamic_color),
                            supporting = if (dynamicColorSupported) {
                                stringResource(R.string.settings_dynamic_color_supporting)
                            } else {
                                stringResource(R.string.settings_dynamic_color_unavailable)
                            },
                            shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                            enabled = dynamicColorSupported,
                        )
                    }
                }

                item(key = "chats") {
                    SettingsSectionHeader(R.string.settings_section_chats)
                }
                item(key = "chats-group") {
                    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                        SettingActionRow(
                            onClick = { showEmojiPicker = true },
                            icon = Icons.Rounded.EmojiEmotions,
                            label = stringResource(R.string.settings_double_tap_emoji),
                            supporting = stringResource(
                                R.string.settings_double_tap_emoji_supporting,
                                settings.doubleTapEmoji,
                            ),
                            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 4),
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = settings.doubleTapEmoji,
                                        fontSize = 22.sp,
                                        modifier = Modifier.clearAndSetSemantics {},
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                        SettingSwitchRow(
                            checked = settings.sendTypingIndicators,
                            onCheckedChange = onSendTypingChange,
                            icon = Icons.Rounded.Keyboard,
                            label = stringResource(R.string.settings_send_typing),
                            supporting = stringResource(R.string.settings_send_typing_supporting),
                            shapes = ListItemDefaults.segmentedShapes(index = 1, count = 4),
                        )
                        SettingSwitchRow(
                            checked = settings.sendReadReceipts,
                            onCheckedChange = onSendReadReceiptsChange,
                            icon = Icons.Rounded.DoneAll,
                            label = stringResource(R.string.settings_send_read_receipts),
                            supporting = stringResource(
                                R.string.settings_send_read_receipts_supporting,
                            ),
                            shapes = ListItemDefaults.segmentedShapes(index = 2, count = 4),
                        )
                        SettingSwitchRow(
                            checked = settings.voiceBackgroundPlayback,
                            onCheckedChange = onVoiceBackgroundPlaybackChange,
                            icon = Icons.Rounded.GraphicEq,
                            label = stringResource(R.string.settings_voice_background),
                            supporting = stringResource(
                                R.string.settings_voice_background_supporting,
                            ),
                            shapes = ListItemDefaults.segmentedShapes(index = 3, count = 4),
                        )
                    }
                }

                item(key = "notifications") {
                    SettingsSectionHeader(R.string.settings_section_notifications)
                }
                item(key = "notifications-group") {
                    SettingSwitchRow(
                        checked = settings.showNotificationPreviews,
                        onCheckedChange = onShowNotificationPreviewsChange,
                        icon = Icons.Rounded.Notifications,
                        label = stringResource(R.string.settings_notification_previews),
                        supporting = stringResource(
                            R.string.settings_notification_previews_supporting,
                        ),
                        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                    )
                }

                item(key = "updates") {
                    SettingsSectionHeader(R.string.settings_section_updates)
                }
                item(key = "updates-group") {
                    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                        SettingSwitchRow(
                            checked = settings.autoCheckUpdates,
                            onCheckedChange = onAutoCheckUpdatesChange,
                            icon = Icons.Rounded.Sync,
                            label = stringResource(R.string.settings_auto_check_updates),
                            supporting = stringResource(
                                R.string.settings_auto_check_updates_supporting,
                            ),
                            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                        )
                        SettingActionRow(
                            onClick = {
                                val shown = when (val current = updateState) {
                                    is UpdateState.Available -> current.update to current.apk
                                    is UpdateState.Downloading -> current.update to current.apk
                                    else -> null
                                }
                                if (shown != null) updateDialog = shown else onCheckUpdates()
                            },
                            icon = Icons.Rounded.Refresh,
                            label = stringResource(R.string.settings_check_updates),
                            supporting = stringResource(
                                R.string.settings_check_updates_supporting,
                                installedVersion,
                                updateStateLabel(updateState),
                            ),
                            shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                            trailing = {
                                when (val current = updateState) {
                                    is UpdateState.Downloading -> CircularProgressIndicator(
                                        progress = { current.fraction },
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    UpdateState.Checking -> CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    else -> Text(
                                        text = stringResource(
                                            R.string.settings_check_updates_action,
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                }

                item(key = "storage") {
                    SettingsSectionHeader(R.string.settings_section_storage)
                }
                item(key = "storage-group") {
                    SettingActionRow(
                        onClick = { confirmClearCache = true },
                        icon = Icons.Rounded.DeleteSweep,
                        label = stringResource(R.string.settings_clear_cache),
                        supporting = stringResource(R.string.settings_clear_cache_supporting),
                        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                        trailing = {
                            Text(
                                text = stringResource(R.string.settings_clear_cache_confirm),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }
    }

    if (showEmojiPicker) {
        EmojiPickerDialog(
            onPick = { emoji ->
                showEmojiPicker = false
                onDoubleTapEmojiChange(emoji)
            },
            onDismiss = { showEmojiPicker = false },
            title = stringResource(R.string.settings_choose_reaction),
        )
    }

    updateDialog?.let { (update, apk) ->
        UpdateDialog(
            update = update,
            apk = apk,
            state = updateState,
            canInstall = canInstall,
            onInstall = onInstallUpdate,
            onOpenInstallPermission = { UpdateInstaller.openInstallPermission(context) },
            onDismiss = { updateDialog = null },
        )
    }

    if (confirmClearCache) {
        AlertDialog(
            onDismissRequest = { if (!clearingCache) confirmClearCache = false },
            title = { Text(stringResource(R.string.settings_clear_cache_title)) },
            text = { Text(stringResource(R.string.settings_clear_cache_body)) },
            confirmButton = {
                TextButton(
                    enabled = !clearingCache,
                    onClick = {
                        scope.launch {
                            clearingCache = true
                            val cleared = runCatching { onClearMediaCache() }.isSuccess
                            clearingCache = false
                            confirmClearCache = false
                            Toast.makeText(
                                context,
                                if (cleared) {
                                    R.string.settings_clear_cache_done
                                } else {
                                    R.string.settings_clear_cache_failed
                                },
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                ) {
                    Text(stringResource(R.string.settings_clear_cache_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !clearingCache,
                    onClick = { confirmClearCache = false },
                ) {
                    Text(stringResource(R.string.chat_cancel))
                }
            },
        )
    }
}

@Composable
private fun UpdateDialog(
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

@Composable
private fun updateStateLabel(state: UpdateState): String = when (state) {
    UpdateState.Idle -> stringResource(R.string.updates_state_up_to_date)
    UpdateState.Checking -> stringResource(R.string.updates_state_checking)
    is UpdateState.Available -> stringResource(
        R.string.updates_state_available,
        state.update.version,
    )
    is UpdateState.Downloading -> stringResource(
        R.string.updates_state_downloading,
        (state.fraction * 100).toInt(),
    )
    UpdateState.Installing -> stringResource(R.string.updates_state_installing)
    is UpdateState.Failed -> state.message ?: stringResource(R.string.updates_check_failed)
}

private fun formatMegabytes(bytes: Long): String =
    String.format(Locale.US, "%.1f", bytes / (1024f * 1024f))

@Composable
private fun SettingsSectionHeader(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun ThemeModeGroup(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val modes = ThemeMode.entries
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        modes.forEachIndexed { index, mode ->
            ToggleButton(
                checked = mode == selected,
                onCheckedChange = { onSelect(mode) },
                buttonSize = ToggleButtonSize.Small,
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
            ) {
                Text(
                    text = stringResource(mode.labelRes()),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    label: String,
    supporting: String,
    shapes: ListItemShapes,
    enabled: Boolean = true,
) {
    SegmentedListItem(
        onClick = { onCheckedChange(!checked) },
        shapes = shapes,
        enabled = enabled,
        modifier = Modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
        },
        leadingContent = { SettingsIconChip(icon) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        },
        supportingContent = { Text(supporting) },
        colors = settingsRowColors(),
        content = {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
            )
        },
    )
}

@Composable
private fun SettingActionRow(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    supporting: String,
    shapes: ListItemShapes,
    trailing: @Composable () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        leadingContent = { SettingsIconChip(icon) },
        trailingContent = trailing,
        supportingContent = { Text(supporting) },
        colors = settingsRowColors(),
        content = {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
            )
        },
    )
}

@Composable
private fun SettingsIconChip(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun settingsRowColors() = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.System -> R.string.settings_theme_system
    ThemeMode.Light -> R.string.settings_theme_light
    ThemeMode.Dark -> R.string.settings_theme_dark
}

@Preview(name = "Settings light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SettingsLightPreview() {
    InstaDMTheme(darkTheme = false, dynamicColor = false) {
        SettingsScreen(
            settings = AppSettings(),
            onThemeModeChange = {},
            onDynamicColorChange = {},
            onDoubleTapEmojiChange = {},
            onSendTypingChange = {},
            onSendReadReceiptsChange = {},
            onShowNotificationPreviewsChange = {},
            onVoiceBackgroundPlaybackChange = {},
            onAutoCheckUpdatesChange = {},
            updateState = UpdateState.Idle,
            installedVersion = "1.0",
            onCheckUpdates = {},
            onInstallUpdate = {},
            onClearMediaCache = {},
            onBack = {},
        )
    }
}

@Preview(
    name = "Settings dark",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    backgroundColor = 0xFF10131C,
)
@Composable
private fun SettingsDarkPreview() {
    InstaDMTheme(darkTheme = true, dynamicColor = false) {
        SettingsScreen(
            settings = AppSettings(),
            onThemeModeChange = {},
            onDynamicColorChange = {},
            onDoubleTapEmojiChange = {},
            onSendTypingChange = {},
            onSendReadReceiptsChange = {},
            onShowNotificationPreviewsChange = {},
            onVoiceBackgroundPlaybackChange = {},
            onAutoCheckUpdatesChange = {},
            updateState = UpdateState.Idle,
            installedVersion = "1.0",
            onCheckUpdates = {},
            onInstallUpdate = {},
            onClearMediaCache = {},
            onBack = {},
        )
    }
}
