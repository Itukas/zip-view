package com.zipview.app.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zipview.app.archive.ArchiveManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TextPreviewViewModel(
    private val app: Application,
    private val key: String,
    private val entryPath: String,
) : AndroidViewModel(app) {

    private val _text = MutableStateFlow<String?>(null)
    val text: StateFlow<String?> = _text.asStateFlow()

    init {
        viewModelScope.launch {
            _text.value = withContext(Dispatchers.IO) {
                runCatching {
                    val provider = ArchiveManager.openUri(app, key)
                    val entry = ArchiveManager.entriesOf(app, key)
                        .first { it.path == entryPath }
                    provider.openStream(entry).use { input ->
                        // 限制最大读取量，避免超大文本拖垮内存
                        input.bufferedReader().use { it.readText().take(MAX_CHARS) }
                    }
                }.getOrElse { "无法读取该文件：${it.message}" }
            }
        }
    }

    companion object {
        private const val MAX_CHARS = 1_000_000
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextPreviewScreen(
    archiveKey: String,
    entryPath: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application

    val vm: TextPreviewViewModel = viewModel(
        key = "text:$archiveKey:$entryPath",
        factory = SimpleFactory { TextPreviewViewModel(app, archiveKey, entryPath) },
    )
    val text by vm.text.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(entryPath.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val content = text
            if (content == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Text(
                    text = content,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                )
            }
        }
    }
}
