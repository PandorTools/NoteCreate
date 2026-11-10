package com.pandortools.notecreate

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pandortools.notecreate.data.AppGraph
import com.pandortools.notecreate.data.NotebookSpec
import com.pandortools.notecreate.data.NotebookTemplate
import com.pandortools.notecreate.data.CoverMaterial
import com.pandortools.notecreate.data.Palette
import com.pandortools.notecreate.data.StartScreen
import com.pandortools.notecreate.ui.editor.EditorScreen
import com.pandortools.notecreate.ui.library.LibraryScreen
import com.pandortools.notecreate.ui.onboarding.OnboardingScreen
import com.pandortools.notecreate.ui.settings.SettingsScreen
import com.pandortools.notecreate.ui.theme.NoteCreateTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Какую тетрадь открыть сразу (ярлык, «Поделиться», последний открытый). */
    private val launchTarget = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppGraph.init(this)
        var ready by mutableStateOf(false)
        lifecycleScope.launch {
            AppGraph.repository.refresh()
            if (savedInstanceState == null) handleIntent(intent, coldStart = true)
            ready = true
        }

        setContent {
            val settings by AppGraph.settings.state.collectAsState()
            NoteCreateTheme(settings.theme) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (ready) {
                        val nav = rememberNavController()
                        val target by launchTarget.collectAsState()
                        LaunchedEffect(target) {
                            target?.let { id -> launchTarget.value = null; nav.openNotebook(id) }
                        }
                        val start = if (settings.onboardingDone) "library" else "onboarding"
                        NavHost(
                            navController = nav,
                            startDestination = remember { start },
                            enterTransition = { fadeIn(tween(220)) },
                            exitTransition = { fadeOut(tween(180)) },
                        ) {
                            composable("onboarding") {
                                OnboardingScreen(
                                    onSkip = {
                                        AppGraph.settings.update { it.copy(onboardingDone = true) }
                                        nav.navigate("library") { popUpTo("onboarding") { inclusive = true } }
                                    },
                                    onDone = { nb ->
                                        nav.navigate("editor/${nb.id}") { popUpTo("onboarding") { inclusive = true } }
                                    },
                                )
                            }
                            composable("library") {
                                LibraryScreen(
                                    onOpen = { nb -> nav.openNotebook(nb.id) },
                                    onSettings = { nav.navigate("settings") },
                                )
                            }
                            composable("editor/{id}") { entry ->
                                val id = entry.arguments?.getString("id") ?: return@composable
                                EditorScreen(notebookId = id, onBack = {
                                    if (nav.previousBackStackEntry != null) nav.popBackStack() else nav.navigate("library") {
                                        popUpTo(nav.graph.id) { inclusive = true }
                                    }
                                })
                            }
                            composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lifecycleScope.launch { handleIntent(intent, coldStart = false) }
    }

    private suspend fun handleIntent(intent: Intent?, coldStart: Boolean) {
        val repo = AppGraph.repository
        val settings = AppGraph.settings.value
        if (!settings.onboardingDone) return
        val alive = repo.notebooks.value.filter { it.deletedAt == null }
        val last = alive.firstOrNull { it.id == settings.lastNotebookId } ?: alive.firstOrNull()
        when (intent?.action) {
            ACTION_CONTINUE -> last?.let { launchTarget.value = it.id }
            ACTION_QUICK_NOTE -> {
                val quick = alive.firstOrNull { it.title == QUICK_TITLE } ?: repo.create(
                    NotebookSpec(
                        title = QUICK_TITLE, template = NotebookTemplate.NOTEBOOK,
                        coverColor = Palette.coverColors[7], coverMaterial = CoverMaterial.PLAIN,
                        elastic = false, ribbon = false, paperType = settings.defaultPaper,
                        paperColor = settings.defaultPaperColor, format = settings.defaultFormat,
                        orientation = settings.defaultOrientation,
                    )
                )
                launchTarget.value = quick.id
            }
            Intent.ACTION_SEND -> {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
                if (uri != null && last != null) {
                    AppGraph.pendingSharedImage.value = uri
                    launchTarget.value = last.id
                }
            }
            else -> if (coldStart && settings.startScreen == StartScreen.LAST_NOTEBOOK) last?.let { launchTarget.value = it.id }
        }
    }

    companion object {
        const val ACTION_CONTINUE = "com.pandortools.notecreate.CONTINUE"
        const val ACTION_QUICK_NOTE = "com.pandortools.notecreate.QUICK_NOTE"
        private const val QUICK_TITLE = "Быстрые заметки"
    }
}

private fun NavHostController.openNotebook(id: String) {
    navigate("editor/$id") {
        popUpTo("library")
        launchSingleTop = true
    }
}
