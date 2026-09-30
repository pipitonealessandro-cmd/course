package it.melodia.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import it.melodia.MelodiaApp
import it.melodia.innertube.SearchFilter
import it.melodia.innertube.SearchPage
import it.melodia.innertube.SongItem
import it.melodia.ui.LocalActions
import it.melodia.ui.components.ErrorView
import it.melodia.ui.components.ItemRow
import it.melodia.ui.components.LoadState
import it.melodia.ui.components.dataOrNull
import it.melodia.ui.components.LoadingView
import it.melodia.ui.theme.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchViewModel : ViewModel() {
    private val app = MelodiaApp.instance
    val query = MutableStateFlow("")
    val suggestions = MutableStateFlow<List<String>>(emptyList())
    val recent = MutableStateFlow(app.prefs.recentSearches)
    val filter = MutableStateFlow(SearchFilter.ALL)
    /** Null while the user is typing (showing suggestions). */
    val results = MutableStateFlow<LoadState<SearchPage>?>(null)
    private var suggestJob: Job? = null
    private var searchJob: Job? = null

    fun onQueryChange(q: String) {
        query.value = q
        results.value = null
        suggestJob?.cancel()
        if (q.isBlank()) {
            suggestions.value = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(250)
            suggestions.value = withContext(Dispatchers.IO) {
                runCatching { app.yt.searchSuggestions(q) }.getOrDefault(emptyList())
            }
        }
    }

    fun search(q: String = query.value, f: SearchFilter = filter.value) {
        val text = q.trim()
        if (text.isEmpty()) return
        query.value = text
        filter.value = f
        suggestJob?.cancel()
        app.prefs.recentSearches = (listOf(text) + app.prefs.recentSearches.filter { !it.equals(text, true) })
        recent.value = app.prefs.recentSearches
        results.value = LoadState.Loading
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            results.value = try {
                LoadState.Ready(withContext(Dispatchers.IO) { app.yt.search(text, f) })
            } catch (e: Exception) {
                LoadState.Error(e.message ?: "Errore")
            }
        }
    }

    fun loadMore() {
        val current = results.value?.dataOrNull() ?: return
        val token = current.continuation ?: return
        results.value = LoadState.Ready(current.copy(continuation = null))
        viewModelScope.launch {
            val more = withContext(Dispatchers.IO) { runCatching { app.yt.searchContinuation(token) }.getOrNull() } ?: return@launch
            val merged = current.sections.toMutableList()
            if (merged.isNotEmpty() && more.sections.isNotEmpty()) {
                val last = merged.removeAt(merged.lastIndex)
                merged += last.copy(items = (last.items + more.sections.flatMap { it.items }).distinctBy { it.key })
            } else merged += more.sections
            results.value = LoadState.Ready(SearchPage(merged, more.continuation))
        }
    }

    fun clearRecent() {
        app.prefs.recentSearches = emptyList()
        recent.value = emptyList()
    }
}

private val FILTER_LABELS = listOf(
    SearchFilter.ALL to "Tutto",
    SearchFilter.SONGS to "Brani",
    SearchFilter.VIDEOS to "Video",
    SearchFilter.ALBUMS to "Album",
    SearchFilter.ARTISTS to "Artisti",
    SearchFilter.PLAYLISTS to "Playlist",
)

@Composable
fun SearchScreen(contentPadding: PaddingValues) {
    val vm: SearchViewModel = viewModel()
    val query by vm.query.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val recent by vm.recent.collectAsState()
    val results by vm.results.collectAsState()
    val filter by vm.filter.collectAsState()
    val actions = LocalActions.current
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        Text("Cerca", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp))
        TextField(
            value = query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focusRequester),
            placeholder = { Text("Cosa vuoi ascoltare?") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Default.Clear, "Cancella") }
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
                focusedLeadingIconColor = Color.Black,
                unfocusedLeadingIconColor = Color.Black,
                focusedTrailingIconColor = Color.Black,
                unfocusedTrailingIconColor = Color.Black,
                focusedPlaceholderColor = Color.DarkGray,
                unfocusedPlaceholderColor = Color.DarkGray,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Color.Black,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focus.clearFocus()
                vm.search()
            }),
        )

        val r = results
        if (r == null) {
            // Suggestions or recent searches
            val list = if (query.isBlank()) recent else suggestions
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                if (query.isBlank() && recent.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Ricerche recenti", style = SectionTitle, modifier = Modifier.weight(1f))
                            Text(
                                "Cancella",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { vm.clearRecent() }.padding(8.dp),
                            )
                        }
                    }
                }
                items(list) { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            focus.clearFocus()
                            vm.search(s)
                        }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (query.isBlank()) Icons.Default.History else Icons.Default.Search,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(s, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(FILTER_LABELS) { (f, label) ->
                    FilterChip(selected = f == filter, onClick = { vm.search(f = f) }, label = { Text(label) })
                }
            }
            when (r) {
                is LoadState.Loading -> LoadingView()
                is LoadState.Error -> ErrorView(r.message, onRetry = { vm.search() })
                is LoadState.Ready -> {
                    val sections = r.data.sections
                    if (sections.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Nessun risultato", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                            sections.forEachIndexed { si, section ->
                                if (section.title != null) {
                                    item(key = "title-$si") {
                                        Text(section.title!!, style = SectionTitle, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
                                    }
                                }
                                items(section.items, key = { "$si-${it.key}" }) { item ->
                                    ItemRow(
                                        item,
                                        onClick = {
                                            // In the "songs" filter, play the song within the result list.
                                            val queue = if (filter == SearchFilter.SONGS) section.items.filterIsInstance<SongItem>() else null
                                            actions.open(item, queue)
                                        },
                                        onMore = { actions.showMenu(item) },
                                    )
                                }
                            }
                            if (r.data.continuation != null) {
                                item { androidx.compose.runtime.LaunchedEffect(Unit) { vm.loadMore() } }
                            }
                        }
                    }
                }
            }
        }
    }
}
