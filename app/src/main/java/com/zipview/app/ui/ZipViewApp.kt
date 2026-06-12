package com.zipview.app.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zipview.app.data.ArchiveRepository
import com.zipview.app.ui.screens.ArchiveListScreen
import com.zipview.app.ui.screens.BrowseScreen
import com.zipview.app.ui.screens.ImageViewerScreen
import com.zipview.app.ui.screens.TextPreviewScreen

/** 集中管理导航路由与参数编码。 */
object Routes {
    const val LIST = "list"
    const val BROWSE = "browse?key={key}&path={path}"
    const val VIEWER = "viewer?key={key}&dir={dir}&start={start}"
    const val TEXT = "text?key={key}&path={path}"

    fun browse(key: String, path: String) =
        "browse?key=${Uri.encode(key)}&path=${Uri.encode(path)}"

    fun viewer(key: String, dir: String, start: String) =
        "viewer?key=${Uri.encode(key)}&dir=${Uri.encode(dir)}&start=${Uri.encode(start)}"

    fun text(key: String, path: String) =
        "text?key=${Uri.encode(key)}&path=${Uri.encode(path)}"
}

@Composable
fun ZipViewApp(initialArchiveUri: Uri?) {
    val nav = rememberNavController()
    val context = LocalContext.current

    LaunchedEffect(initialArchiveUri) {
        if (initialArchiveUri != null) {
            ArchiveRepository.get(context).add(initialArchiveUri)
            nav.navigate(Routes.browse(initialArchiveUri.toString(), ""))
        }
    }

    NavHost(navController = nav, startDestination = Routes.LIST) {

        composable(Routes.LIST) {
            ArchiveListScreen(
                onOpen = { key -> nav.navigate(Routes.browse(key, "")) },
            )
        }

        composable(
            route = Routes.BROWSE,
            arguments = listOf(
                navArgument("key") { type = NavType.StringType },
                navArgument("path") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val key = Uri.decode(entry.arguments?.getString("key").orEmpty())
            val path = Uri.decode(entry.arguments?.getString("path").orEmpty())
            BrowseScreen(
                archiveKey = key,
                path = path,
                onBack = { nav.popBackStack() },
                onOpenFolder = { folderPath -> nav.navigate(Routes.browse(key, folderPath)) },
                onOpenImage = { dir, start -> nav.navigate(Routes.viewer(key, dir, start)) },
                onOpenText = { entryPath -> nav.navigate(Routes.text(key, entryPath)) },
                onOpenNestedKey = { nestedKey -> nav.navigate(Routes.browse(nestedKey, "")) },
            )
        }

        composable(
            route = Routes.VIEWER,
            arguments = listOf(
                navArgument("key") { type = NavType.StringType },
                navArgument("dir") { type = NavType.StringType; defaultValue = "" },
                navArgument("start") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            ImageViewerScreen(
                archiveKey = Uri.decode(entry.arguments?.getString("key").orEmpty()),
                dir = Uri.decode(entry.arguments?.getString("dir").orEmpty()),
                startPath = Uri.decode(entry.arguments?.getString("start").orEmpty()),
                onBack = { nav.popBackStack() },
            )
        }

        composable(
            route = Routes.TEXT,
            arguments = listOf(
                navArgument("key") { type = NavType.StringType },
                navArgument("path") { type = NavType.StringType },
            ),
        ) { entry ->
            TextPreviewScreen(
                archiveKey = Uri.decode(entry.arguments?.getString("key").orEmpty()),
                entryPath = Uri.decode(entry.arguments?.getString("path").orEmpty()),
                onBack = { nav.popBackStack() },
            )
        }
    }
}
