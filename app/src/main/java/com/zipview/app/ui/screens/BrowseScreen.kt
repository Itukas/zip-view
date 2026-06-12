package com.zipview.app.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zipview.app.archive.ArchiveEntry
import com.zipview.app.archive.ArchiveManager
import com.zipview.app.archive.EntryType
import com.zipview.app.openwith.OpenWith
import com.zipview.app.util.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface BrowseUi {
    data object Loading : BrowseUi
    data object Preparing : BrowseUi
    data class Ready(val entries: List<ArchiveEntry>) : BrowseUi
    data class Error(val message: String) : BrowseUi
}

class BrowseViewModel(private val app: Application, private val key: String) : AndroidViewModel(app) {
    private val _ui = MutableStateFlow<BrowseUi>(BrowseUi.Loading)
    val ui: StateFlow<BrowseUi> = _ui.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            _ui.value = BrowseUi.Loading
            try {
                val all = withContext(Dispatchers.IO) {
                    val provider = ArchiveManager.openUri(app, key)
                    if (!provider.supportsRandomAccess) {
                        withContext(Dispatchers.Main) { _ui.value = BrowseUi.Preparing }
                    }
                    ArchiveManager.entriesOf(app, key)
                }
                _ui.value = BrowseUi.Ready(all)
            } catch (e: Exception) {
                _ui.value = BrowseUi.Error(e.message ?: "打开失败")
            }
        }
    }
}

/** 计算某目录下的直接子项；对 zip 中未显式存放的中间目录做合成。 */
fun childrenOf(all: List<ArchiveEntry>, dir: String): List<ArchiveEntry> {
    val prefix = if (dir.isEmpty()) "" else "$dir/"
    val seen = LinkedHashMap<String, ArchiveEntry>()
    for (e in all) {
        if (e.path == dir) continue
        if (prefix.isNotEmpty() && !e.path.startsWith(prefix)) continue
        val rest = e.path.substring(prefix.length)
        if (rest.isEmpty()) continue
        val slash = rest.indexOf('/')
        if (slash < 0) {
            seen.putIfAbsent(e.path, e)
        } else {
            val childName = rest.substring(0, slash)
            val childPath = prefix + childName
            seen.putIfAbsent(
                childPath,
                ArchiveEntry(childPath, childName, true, 0L, EntryType.DIRECTORY),
            )
        }
    }
    return seen.values.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    archiveKey: String,
    path: String,
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenImage: (dir: String, start: String) -> Unit,
    onOpenText: (String) -> Unit,
    onOpenNestedKey: (String) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val vm: BrowseViewModel = viewModel(
        key = "browse:$archiveKey",
        factory = SimpleFactory { BrowseViewModel(app, archiveKey) },
    )
    val ui by vm.ui.collectAsState()

    val title = if (path.isEmpty()) archiveKey.substringAfterLast('/').ifEmpty { "压缩包" }
    else path.substringAfterLast('/')

    fun openExternally(entry: ArchiveEntry) = scope.launch {
        val ok = withContext(Dispatchers.IO) { OpenWith.openExternally(context, archiveKey, entry.path) }
        if (!ok) snackbar.showSnackbar("没有可用的应用")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val state = ui) {
                BrowseUi.Loading -> Centered { CircularProgressIndicator() }
                BrowseUi.Preparing -> Centered {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text("正在准备…", Modifier.padding(top = 12.dp))
                    }
                }
                is BrowseUi.Error -> Centered { Text(state.message) }
                is BrowseUi.Ready -> {
                    val children = remember(state.entries, path) { childrenOf(state.entries, path) }
                    if (children.isEmpty()) {
                        Centered { Text("（空目录）") }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(children, key = { it.path }) { entry ->
                                EntryRow(
                                    entry = entry,
                                    onClick = {
                                        when (entry.type) {
                                            EntryType.DIRECTORY -> onOpenFolder(entry.path)
                                            EntryType.IMAGE -> onOpenImage(path, entry.path)
                                            EntryType.TEXT -> onOpenText(entry.path)
                                            EntryType.ARCHIVE -> scope.launch {
                                                val nested = withContext(Dispatchers.IO) {
                                                    ArchiveManager.openNested(context, archiveKey, entry.path)
                                                }
                                                onOpenNestedKey(nested)
                                            }
                                            EntryType.OTHER -> openExternally(entry)
                                        }
                                    },
                                    onOpenWith = { openExternally(entry) },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun EntryRow(
    entry: ArchiveEntry,
    onClick: () -> Unit,
    onOpenWith: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val icon = when (entry.type) {
        EntryType.DIRECTORY -> Icons.Default.Folder
        EntryType.IMAGE -> Icons.Default.Image
        EntryType.TEXT -> Icons.Default.Description
        EntryType.ARCHIVE -> Icons.Default.Archive
        EntryType.OTHER -> Icons.Default.InsertDriveFile
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (entry.isDirectory) null else {
            { Text(formatSize(entry.size)) }
        },
        trailingContent = if (entry.isDirectory) null else {
            {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("用其他应用打开") },
                            onClick = { menuOpen = false; onOpenWith() },
                        )
                    }
                }
            }
        },
    )
}
