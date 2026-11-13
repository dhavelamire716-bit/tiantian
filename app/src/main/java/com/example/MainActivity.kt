package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.AppRoute
import com.example.data.local.VideoEntity
import com.example.firebase.configureAppCheck
import com.example.ui.GenerationUiState
import com.example.ui.StudioViewModel
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.VerdeReelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            val viewModel: StudioViewModel = viewModel(
                factory = StudioViewModel.provideFactory(application)
            )
            val controls by viewModel.controls.collectAsStateWithLifecycle()

            VerdeReelTheme(darkTheme = controls.isDarkTheme) {
                VerdeReelApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun VerdeReelApp(viewModel: StudioViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val controls by viewModel.controls.collectAsStateWithLifecycle()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val exportBanner by viewModel.exportBanner.collectAsStateWithLifecycle()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isPlayerRoute = currentDestination?.hasRoute<AppRoute.Player>() == true

    LaunchedEffect(generationState) {
        val completed = generationState as? GenerationUiState.Completed
        if (completed != null) {
            viewModel.resetGenerationState()
            navController.navigate(AppRoute.Player(completed.videoId))
        }
    }

    LaunchedEffect(exportBanner) {
        val msg = exportBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    fun shareVideo(video: VideoEntity) {
        val uri = viewModel.getShareUri(video.videoFilePath) ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, video.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share ${video.title}"))
    }

    fun navigateTopLevel(route: AppRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val isStudioSelected = currentDestination?.hasRoute<AppRoute.Studio>() == true
    val isLibrarySelected = currentDestination?.hasRoute<AppRoute.Library>() == true

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavigationRail && !isPlayerRoute) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 0.dp
                    ) {
                        NavigationBarItem(
                            selected = isStudioSelected,
                            onClick = { navigateTopLevel(AppRoute.Studio) },
                            icon = {
                                Icon(
                                    imageVector = if (isStudioSelected) Icons.Filled.MovieCreation else Icons.Outlined.MovieCreation,
                                    contentDescription = "Studio"
                                )
                            },
                            label = { Text("Studio") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("nav_studio")
                        )
                        NavigationBarItem(
                            selected = isLibrarySelected,
                            onClick = { navigateTopLevel(AppRoute.Library) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (videos.isNotEmpty()) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ) {
                                                Text(videos.size.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isLibrarySelected) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary,
                                        contentDescription = "My Reels"
                                    )
                                }
                            },
                            label = { Text("My Reels") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("nav_library")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (useNavigationRail && !isPlayerRoute) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            NavigationRailItem(
                                selected = isStudioSelected,
                                onClick = { navigateTopLevel(AppRoute.Studio) },
                                icon = {
                                    Icon(
                                        imageVector = if (isStudioSelected) Icons.Filled.MovieCreation else Icons.Outlined.MovieCreation,
                                        contentDescription = "Studio"
                                    )
                                },
                                label = { Text("Studio") },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag("rail_studio")
                            )
                            NavigationRailItem(
                                selected = isLibrarySelected,
                                onClick = { navigateTopLevel(AppRoute.Library) },
                                icon = {
                                    Icon(
                                        imageVector = if (isLibrarySelected) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary,
                                        contentDescription = "My Reels"
                                    )
                                },
                                label = { Text("My Reels") },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag("rail_library")
                            )
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = AppRoute.Studio,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable<AppRoute.Studio> {
                            StudioScreen(
                                controls = controls,
                                generationState = generationState,
                                onPromptChange = viewModel::updatePrompt,
                                onEnhancePrompt = viewModel::enhancePrompt,
                                onAspectRatioSelected = viewModel::updateAspectRatio,
                                onStyleSelected = viewModel::updateStyle,
                                onCameraMotionSelected = viewModel::updateCameraMotion,
                                onSceneCountSelected = viewModel::updateSceneCount,
                                onBurnInCaptionsChanged = viewModel::updateBurnInCaptions,
                                onGenerateVideo = viewModel::generateVideo,
                                onCancelGeneration = viewModel::cancelGeneration,
                                onDismissError = viewModel::resetGenerationState,
                                onToggleTheme = viewModel::toggleTheme
                            )
                        }

                        composable<AppRoute.Library> {
                            LibraryScreen(
                                videos = videos,
                                onOpenVideo = { videoId ->
                                    navController.navigate(AppRoute.Player(videoId))
                                },
                                onShareVideo = ::shareVideo,
                                onExportVideo = viewModel::exportVideoToGallery,
                                onDeleteVideo = viewModel::deleteVideo,
                                onNavigateToStudio = { navigateTopLevel(AppRoute.Studio) }
                            )
                        }

                        composable<AppRoute.Player> { backStackEntry ->
                            val route = backStackEntry.toRoute<AppRoute.Player>()
                            val video by viewModel.observeVideo(route.videoId)
                                .collectAsStateWithLifecycle(initialValue = null)

                            PlayerScreen(
                                video = video,
                                onBack = { navController.popBackStack() },
                                onExportToGallery = viewModel::exportVideoToGallery,
                                onShareVideo = ::shareVideo,
                                onRemixPrompt = { sourceVideo ->
                                    viewModel.loadPromptFromVideo(sourceVideo)
                                    navigateTopLevel(AppRoute.Studio)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
