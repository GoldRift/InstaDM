package com.goldrift.instadm.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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

    Surface(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
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
                            IconButton(onClick = { query = "" }) {
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
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }

            when (val state = results) {
                SearchResults.Idle -> SearchHint(text = stringResource(R.string.search_prompt))

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
                    text = state.message.ifBlank { stringResource(R.string.search_error) },
                    actionLabel = stringResource(R.string.inbox_retry),
                    onAction = { retryToken++ },
                )

                is SearchResults.Found -> if (state.users.isEmpty()) {
                    SearchHint(text = stringResource(R.string.search_no_results, query.trim()))
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.users, key = { it.fbid }) { user ->
                            UserRow(user = user, onClick = { onPick(user) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRow(user: SearchUser, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            initials = initialsOf(user.displayName),
            size = 48.dp,
            imageUrl = user.avatarUrl,
            // Matches the draft chat header, which seeds on the same FBID.
            colorSeed = user.fbid,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
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
            if (user.username.isNotBlank()) {
                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SearchHint(
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
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onAction) { Text(actionLabel) }
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
