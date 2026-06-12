package com.zipview.app.ui.screens

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zipview.app.data.ArchiveItem
import com.zipview.app.data.ArchiveRepository
import com.zipview.app.util.formatSize

class ArchiveListViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ArchiveRepository.get(app)
    val items = repo.items
    fun add(uri: Uri) = repo.add(uri)
    fun remove(item: ArchiveItem) = repo.remove(item.uri)
    fun rename(item: ArchiveItem, newName: String) = repo.rename(item.uri, newName)
    fun isAccessible(item: ArchiveItem) = repo.isAccessible(item.uri)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveListScreen(
    onOpen: (key: String) -> Unit,
    vm: ArchiveListViewModel = viewModel(),
) {
    val items by vm.items.collectAsState()

    var renameTarget by remember { mutableStateOf<ArchiveItem?>(null) }
    var infoTarget by remember { mutableStateOf<ArchiveItem?>(null) }
    var deleteTarget by remember { mutableStateOf<ArchiveItem?>(null) }
    var inaccessibleTarget by remember { mutableStateOf<ArchiveItem?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let(vm::add) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("ZipView") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("添加压缩包") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = { picker.launch(arrayOf("*/*")) },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("还没有压缩包，点击右下角添加")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(items, key = { it.key }) { item ->
                    ArchiveRow(
                        item = item,
                        onClick = {
                            if (vm.isAccessible(item)) onOpen(item.key) else inaccessibleTarget = item
                        },
                        onRename = { renameTarget = item },
                        onDelete = { deleteTarget = item },
                        onInfo = { infoTarget = item },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    renameTarget?.let { target ->
        var text by remember(target) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名") },
            text = { TextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) vm.rename(target, text.trim())
                    renameTarget = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } },
        )
    }

    infoTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { infoTarget = null },
            title = { Text("信息") },
            text = {
                Column {
                    Text("名称：${target.name}")
                    Text("格式：${target.format}")
                    Text("大小：${formatSize(target.sizeBytes)}")
                }
            },
            confirmButton = { TextButton(onClick = { infoTarget = null }) { Text("好") } },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("移除") },
            text = { Text("从列表移除「${target.name}」？源文件不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.remove(target)
                    deleteTarget = null
                }) { Text("移除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }

    inaccessibleTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { inaccessibleTarget = null },
            title = { Text("无法访问") },
            text = { Text("「${target.name}」的访问授权已失效，需要重新选择该文件。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.remove(target)
                    inaccessibleTarget = null
                    picker.launch(arrayOf("*/*"))
                }) { Text("重新选择") }
            },
            dismissButton = { TextButton(onClick = { inaccessibleTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ArchiveRow(
    item: ArchiveItem,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(Icons.Default.Archive, contentDescription = null) },
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("${item.format} · ${formatSize(item.sizeBytes)}") },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("重命名") }, onClick = { menuOpen = false; onRename() })
                    DropdownMenuItem(text = { Text("信息") }, onClick = { menuOpen = false; onInfo() })
                    DropdownMenuItem(text = { Text("移除") }, onClick = { menuOpen = false; onDelete() })
                }
            }
        },
    )
}
