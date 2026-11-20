package com.goldrift.instadm.ui.search

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.goldrift.instadm.R
import com.goldrift.instadm.ui.chat.initialsOf
import com.goldrift.instadm.ui.components.Avatar
import com.goldrift.instadm.ui.components.ExpressiveStateBadge
import com.goldrift.instadm.ui.theme.InstaDMTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Wait for a pause in typing before querying Instagram. */
private const val SEARCH_DEBOUNCE_MS = 350L

/** One account returned by the Go core's user search. */
data class SearchUser(
    val fbid: String,
    val igId: String = "",
    val username: String,
    val name: String,
    val avatarUrl: String = "",
    val verified: Boolean = false,
) {
    val displayName: String get() = name.ifBlank { username }
}

/** Maps the `users` array of a search response; results without an FBID cannot be messaged. */
fun JSONObject.toSearchUsers(): List<SearchUser> {
    val array = optJSONArray("users") ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val user = array.optJSONObject(index) ?: continue
            val fbid = user.optLong("fbid").takeIf { it > 0 } ?: continue
            add(
                SearchUser(
                    fbid = fbid.toString(),
                    igId = user.optString("ig_id"),
                    username = user.optString("username"),
                    name = user.optString("name"),
                    avatarUrl = user.optString("avatar_url"),
                    verified = user.optBoolean("is_verified"),
                ),
            )
        }
    }
}

private sealed interface SearchResults {
    data object Idle : SearchResults
    data object Searching : SearchResults
    data class Found(val users: List<SearchUser>) : SearchResults
    data class Failed(val message: String) : SearchResults
}

/**
 * Account search. Picking a result opens its chat: the existing conversation
 * when the inbox has one, otherwise a chat that starts on the first message.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    search: suspend (query: String) -> List<SearchUser>,
    onPick: (SearchUser) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<SearchResults>(SearchResults.Idle) }
    var retryToken by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // A changed query cancels the pending debounce, so only the last keystroke
    // reaches Instagram. retryToken re-runs the same query after a failure.
    LaunchedEffect(query, retryToken) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            results = SearchResults.Idle
            return@LaunchedEffect
        }
        results = SearchResults.Searching
        delay(SEARCH_DEBOUNCE_MS)
        // A re-typed query cancels the request in flight; that cancellation is
        // Compose leaving this effect, not a search failure, so it must not
        // become an error state.
        results = try {
            SearchResults.Found(search(needle))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            SearchResults.Failed(error.message.orEmpty())
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                        ),
                    )
                    .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.chat_back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { query = "" },
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.search_clear),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }
        },
    ) { innerPadding ->
        val sheetShape = RoundedCornerShape(
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
                )
                .imePadding(),
            shape = sheetShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            when (val state = results) {
                SearchResults.Idle -> SearchHint(
                    badgeShape = MaterialShapes.Cookie7Sided.toShape(),
                    badgeIcon = Icons.Rounded.PersonSearch,
                    badgeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    badgeIconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    text = stringResource(R.string.search_prompt),
                )

                SearchResults.Searching -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    val loading = stringResource(R.string.search_searching)
                    LoadingIndicator(
                        modifier = Modifier.semantics { contentDescription = loading },
                    )
                }

                is SearchResults.Failed -> SearchHint(
                    badgeShape = MaterialShapes.SoftBurst.toShape(),
                    badgeIcon = Icons.Rounded.CloudOff,
                    badgeContainerColor = MaterialTheme.colorScheme.errorContainer,
                    badgeIconTint = MaterialTheme.colorScheme.onErrorContainer,
                    text = state.message.ifBlank { stringResource(R.string.search_error) },
                    actionLabel = stringResource(R.string.inbox_retry),
                    onAction = { retryToken++ },
                )

                is SearchResults.Found -> if (state.users.isEmpty()) {
                    SearchHint(
                        badgeShape = MaterialShapes.Clover4Leaf.toShape(),
                        badgeIcon = Icons.Rounded.SearchOff,
                        badgeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        badgeIconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        text = stringResource(R.string.search_no_results, query.trim()),
                    )
                } else {
                    val navBarBottom = WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = navBarBottom + 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        itemsIndexed(
                            items = state.users,
                            key = { _, user -> user.fbid },
                            contentType = { _, _ -> "user" },
                        ) { index, user ->
                            UserRow(
                                user = user,
                                shapes = ListItemDefaults.segmentedShapes(
                                    index = index,
                                    count = state.users.size,
                                ),
                                onClick = { onPick(user) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UserRow(
    user: SearchUser,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        leadingContent = {
            Avatar(
                initials = initialsOf(user.displayName),
                size = 48.dp,
                imageUrl = user.avatarUrl,
                // Matches the draft chat header, which seeds on the same FBID.
                colorSeed = user.fbid,
            )
        },
        supportingContent = if (user.username.isNotBlank()) {
            {
                Text(
                    text = "@${user.username}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            null
        },
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        content = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (user.verified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.Verified,
                        contentDescription = stringResource(R.string.search_verified),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchHint(
    badgeShape: Shape,
    badgeIcon: ImageVector,
    badgeContainerColor: Color,
    badgeIconTint: Color,
    text: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ExpressiveStateBadge(
            shape = badgeShape,
            icon = badgeIcon,
            containerColor = badgeContainerColor,
            iconTint = badgeIconTint,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onAction,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Preview(name = "Search light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SearchLightPreview() {
    InstaDMTheme(darkTheme = false, dynamicColor = false) {
        SearchScreen(
            search = { sampleSearchUsers() },
            onPick = {},
            onBack = {},
        )
    }
}

private fun sampleSearchUsers(): List<SearchUser> = listOf(
    SearchUser(
        fbid = "1",
        username = "maya.chen",
        name = "Maya Chen",
        verified = true,
    ),
    SearchUser(fbid = "2", username = "maya.design", name = "Maya Design"),
    SearchUser(fbid = "3", username = "mayav.108", name = "Maya V"),
)
