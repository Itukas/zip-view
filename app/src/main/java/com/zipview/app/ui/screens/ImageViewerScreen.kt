package com.zipview.app.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.zipview.app.archive.ArchiveEntry
import com.zipview.app.archive.ArchiveManager
import com.zipview.app.archive.EntryType
import com.zipview.app.coil.ArchiveImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewerViewModel(
    private val app: Application,
    private val key: String,
    private val dir: String,
) : AndroidViewModel(app) {

    private val _images = MutableStateFlow<List<ArchiveEntry>>(emptyList())
    val images: StateFlow<List<ArchiveEntry>> = _images.asStateFlow()

    init {
        viewModelScope.launch {
            _images.value = withContext(Dispatchers.IO) {
                val all = ArchiveManager.entriesOf(app, key)
                childrenOf(all, dir).filter { it.type == EntryType.IMAGE }
            }
        }
    }
}

@Composable
fun ImageViewerScreen(
    archiveKey: String,
    dir: String,
    startPath: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application

    val vm: ViewerViewModel = viewModel(
        key = "viewer:$archiveKey:$dir",
        factory = SimpleFactory { ViewerViewModel(app, archiveKey, dir) },
    )
    val images by vm.images.collectAsState()

    if (images.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("没有图片", color = Color.White)
        }
        return
    }

    val startIndex = remember(images, startPath) {
        images.indexOfFirst { it.path == startPath }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = startIndex) { images.size }

    // 预加载当前页前后各一张，窗口受限以避免内存膨胀
    LaunchedEffect(pagerState.currentPage, images) {
        listOf(pagerState.currentPage - 1, pagerState.currentPage + 1).forEach { i ->
            images.getOrNull(i)?.let { e ->
                context.imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(ArchiveImage(archiveKey, e.path))
                        .build(),
                )
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            ZoomableImage(model = ArchiveImage(archiveKey, images[page].path))
        }

        IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
        }

        Text(
            text = "${pagerState.currentPage + 1} / ${images.size}",
            color = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
        )
    }
}

@Composable
private fun ZoomableImage(model: ArchiveImage) {
    var scale by remember(model) { mutableFloatStateOf(1f) }
    var offsetX by remember(model) { mutableStateOf(0f) }
    var offsetY by remember(model) { mutableStateOf(0f) }

    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(model) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = offsetY,
            ),
    )
}
