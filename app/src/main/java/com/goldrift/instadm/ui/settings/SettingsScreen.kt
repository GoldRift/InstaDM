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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.goldrift.instadm.R
import com.goldrift.instadm.data.AppSettings
import com.goldrift.instadm.data.ThemeMode
import com.goldrift.instadm.ui.components.EmojiPickerDialog
import com.goldrift.instadm.ui.theme.InstaDMTheme
import kotlinx.coroutines.launch

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
    onClearMediaCache: suspend () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showEmojiPicker by remember { mutableStateOf(false) }
    var confirmClearCache by remember { mutableStateOf(false) }
    var clearingCache by remember { mutableStateOf(false) }
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
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
                            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
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
                            shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3),
                        )
                        SettingSwitchRow(
                            checked = settings.sendReadReceipts,
                            onCheckedChange = onSendReadReceiptsChange,
                            icon = Icons.Rounded.DoneAll,
                            label = stringResource(R.string.settings_send_read_receipts),
                            supporting = stringResource(
                                R.string.settings_send_read_receipts_supporting,
                            ),
                            shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
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
            onClearMediaCache = {},
            onBack = {},
        )
    }
}
