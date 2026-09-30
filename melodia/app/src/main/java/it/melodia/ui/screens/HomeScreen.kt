package it.melodia.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import it.melodia.MelodiaApp
import it.melodia.innertube.HomePage
import it.melodia.innertube.SongItem
import it.melodia.ui.LocalActions
import it.melodia.ui.Routes
import it.melodia.ui.components.LoadState
import it.melodia.ui.components.dataOrNull
import it.melodia.ui.components.LoadStateView
import it.melodia.ui.components.LoaderViewModel
import it.melodia.ui.components.SectionView
import it.melodia.ui.components.Thumb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel : LoaderViewModel<HomePage>({ MelodiaApp.instance.yt.home() }) {
    private var loadingMore = false

    fun loadMore() {
        val current = state.value.dataOrNull() ?: return
        val token = current.continuation ?: return
        if (loadingMore) return
        loadingMore = true
        viewModelScope.launch {
            try {
                val more = withContext(Dispatchers.IO) { MelodiaApp.instance.yt.homeContinuation(token) }
                _state.value = LoadState.Ready(HomePage(current.sections + more.sections, more.continuation))
            } catch (_: Exception) {
                _state.value = LoadState.Ready(current.copy(continuation = null))
            } finally {
                loadingMore = false
            }
        }
    }
}

@Composable
fun HomeScreen(contentPadding: PaddingValues) {
    val vm: HomeViewModel = viewModel()
    val state by vm.state.collectAsState()
    val actions = LocalActions.current
    val account by MelodiaApp.instance.prefs.account.collectAsState()

    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        LoadStateView(state, onRetry = { vm.reload() }) { home ->
            val listState = rememberLazyListState()
            val nearEnd by remember {
                derivedStateOf {
                    val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    last >= listState.layoutInfo.totalItemsCount - 3
                }
            }
            LaunchedEffect(nearEnd, home.continuation) { if (nearEnd) vm.loadMore() }

            PullToRefreshBox(isRefreshing = false, onRefresh = { vm.reload() }) {
                LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (account?.photo != null) {
                                Thumb(account?.photo, Modifier.size(34.dp), CircleShape)
                                Box(Modifier.size(12.dp))
                            }
                            Text(greeting(), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { actions.nav.navigate(Routes.SETTINGS) }) {
                                Icon(Icons.Default.Settings, "Impostazioni")
                            }
                        }
                    }
                    if (account == null) {
                        item { LoginBanner { actions.nav.navigate(Routes.LOGIN) } }
                    }
                    itemsIndexed(home.sections, key = { i, s -> "$i-${s.title}" }) { _, section ->
                        SectionView(
                            section,
                            onItemClick = { item, all -> actions.open(item, all.filterIsInstance<SongItem>().takeIf { item is SongItem && !item.isVideo && all.size > 1 && all.all { it is SongItem } }) },
                            onItemMore = { actions.showMenu(it) },
                            onMore = section.moreBrowseId?.let { id -> { actions.openMore(id, section.moreParams) } },
                        )
                    }
                    if (home.continuation != null) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(28.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginBanner(onLogin: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Collega il tuo account YouTube", style = MaterialTheme.typography.titleMedium)
            Text(
                "Per vedere le tue playlist, i brani che ti piacciono e consigli personalizzati.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onLogin) { Text("Accedi") }
        }
    }
}

private fun greeting(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (h) {
        in 5..12 -> "Buongiorno"
        in 13..17 -> "Buon pomeriggio"
        else -> "Buonasera"
    }
}
