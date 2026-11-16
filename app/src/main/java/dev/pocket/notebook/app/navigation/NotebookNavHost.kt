package dev.pocket.notebook.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.pocket.notebook.app.scaffold.NotebookScaffold
import dev.pocket.notebook.core.logging.NotebookLog as Log
import dev.pocket.notebook.data.NotebookEntry
import dev.pocket.notebook.data.VfsItem
import dev.pocket.notebook.data.security.SecurityAction
import dev.pocket.notebook.feature.files.LocalPocketTools
import dev.pocket.notebook.feature.library.NotebookLibraryScreen
import dev.pocket.notebook.feature.library.state.LibraryViewModel
import dev.pocket.notebook.feature.settings.SettingsScreen
import dev.pocket.notebook.i18n.AppStrings
import dev.pocket.notebook.settings.AppPreferences

/** Route registration and back-stack policy; UI and repository remain separate. */
@Composable
fun NotebookNavHost(
    entries: List<NotebookEntry>,
    revision: Int,
    children: (String?) -> List<VfsItem>,
    folderPath: (String?) -> List<VfsItem.Folder>,
    folders: () -> List<VfsItem.Folder>,
    strings: AppStrings,
    language: String,
    dark: Boolean,
    preferences: AppPreferences,
    read: (String) -> dev.pocket.notebook.domain.DocumentSnapshot,
    writer: dev.pocket.notebook.core.state.DocumentWriteCoordinator,
    onRefresh: () -> Unit,
    onCreate: (String?) -> NotebookEntry,
    onCreateFolder: (String, String?) -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onMoveToFolder: (String, String?) -> Unit,
    onImport: (String?) -> Unit,
    onExport: (String) -> Unit,
    onDelete: (VfsItem) -> Unit,
    onSetOrder: (String?, List<String>) -> Unit,
    onEditMetadata: (String) -> Unit,
    onSecurityAction: (String, SecurityAction) -> Unit,
    onRequireUnlock: (String) -> Unit,
    loadCover: (String) -> ByteArray?,
    openUnlockedId: String?,
    onConsumedOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Own the library location above NavHost destinations so opening a document cannot
    // discard the folder. SavedStateHandle restores it even after rotation/process death.
    val libraryState: LibraryViewModel =
        viewModel(
            factory =
                viewModelFactory { initializer { LibraryViewModel(createSavedStateHandle()) } }
        )
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val route = currentEntry?.destination?.route
    val currentId = currentEntry?.arguments?.getString("id")
    var lastNotebookId by rememberSaveable { mutableStateOf<String?>(null) }
    // The fullscreen editor is tied to its document, not to the activity instance.
    // Leaving the workspace restores the normal navigation immediately.
    var fullscreenDocumentId by rememberSaveable { mutableStateOf<String?>(null) }

    val tools = dev.pocket.notebook.feature.files.LocalPocketTools.current
    val visibleEncrypted =
        if (route == Routes.WORKSPACE) entries.firstOrNull { it.id == currentId }?.encrypted == true
        else if (route == Routes.LIBRARY)
            folders()
                .firstOrNull { it.id == libraryState.folderId }
                ?.let { it.encrypted && it.unlocked } == true
        else false
    androidx.compose.runtime.DisposableEffect(visibleEncrypted) {
        tools.privateVisible = visibleEncrypted
        onDispose { tools.privateVisible = false }
    }
    LaunchedEffect(route) {
        Log.i("NotebookNav", "destination route=$route")
        if (route == Routes.LIBRARY) onRefresh()
    }
    LaunchedEffect(entries, route, currentId) {
        if (lastNotebookId != null && entries.none { it.id == lastNotebookId }) {
            Log.i("NotebookNav", "last notebook removed; clearing selection")
            lastNotebookId = null
        }
        // A document may be deleted while its destination remains in the back stack.
        if (
            route == Routes.WORKSPACE &&
                currentId != null &&
                entries.none { it.id == currentId && it.unlocked }
        ) {
            Log.w("NotebookNav", "active notebook was deleted; returning to library")
            if (!navController.popBackStack(Routes.LIBRARY, false)) {
                navController.navigate(Routes.LIBRARY) {
                    popUpTo(navController.graph.id) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    fun openNotebook(id: String) {
        // New notebook may be saved by the previous composition. Only block known locked docs.
        if (entries.firstOrNull { it.id == id }?.unlocked == false) {
            onRequireUnlock(id)
            return
        }
        lastNotebookId = id
        Log.i("NotebookNav", "open workspace") // Do not log document identifiers or source.
        navController.navigate(Routes.workspace(id)) { launchSingleTop = true }
    }

    LaunchedEffect(openUnlockedId, entries) {
        val id = openUnlockedId
        val container = folders().firstOrNull { it.id == id }
        if (id != null && container?.unlocked == true) {
            libraryState.openFolder(id)
            onConsumedOpen()
        } else if (id != null && entries.firstOrNull { it.id == id }?.unlocked == true) {
            lastNotebookId = id
            navController.navigate(Routes.workspace(id)) { launchSingleTop = true }
            onConsumedOpen()
        }
    }

    fun navigateToTab(target: String) {
        Log.i("NotebookNav", "tab clicked target=$target from=$route entries=${entries.size}")
        when (target) {
            Routes.LIBRARY -> {
                if (route == Routes.LIBRARY) return
                // Crucial: library is the root of our graph, not a second instance
                // restored from a saved top-level state.
                if (!navController.popBackStack(Routes.LIBRARY, false)) {
                    navController.navigate(Routes.LIBRARY) {
                        launchSingleTop = true
                        popUpTo(navController.graph.id) { inclusive = false }
                    }
                }
            }
            Routes.WORKSPACE -> {
                val id =
                    lastNotebookId?.takeIf { saved -> entries.any { it.id == saved } }
                        ?: entries.firstOrNull()?.id
                if (id == null) {
                    Log.i("NotebookNav", "workspace unavailable: no documents")
                    return
                }
                if (entries.firstOrNull { it.id == id }?.unlocked == false) {
                    onRequireUnlock(id)
                    return
                }
                if (route == Routes.WORKSPACE && currentId == id) return
                lastNotebookId = id
                navController.navigate(Routes.workspace(id)) {
                    popUpTo(Routes.LIBRARY) { inclusive = false }
                    launchSingleTop = true
                }
            }
            Routes.SETTINGS -> {
                if (route == Routes.SETTINGS) return
                navController.navigate(Routes.SETTINGS) {
                    popUpTo(Routes.LIBRARY) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    val destinations =
        topLevelDestinations(strings).filter {
            it.route != Routes.WORKSPACE || entries.isNotEmpty()
        }
    NotebookScaffold(
        route = route,
        destinations = destinations,
        navigate = ::navigateToTab,
        fullscreen =
            route == Routes.WORKSPACE && currentId != null && currentId == fullscreenDocumentId,
    ) { contentModifier ->
        NavHost(
            navController = navController,
            startDestination = Routes.LIBRARY,
            modifier = modifier.then(contentModifier),
        ) {
            composable(Routes.LIBRARY) {
                NotebookLibraryScreen(
                    state = libraryState,
                    revision = revision,
                    children = children,
                    path = folderPath,
                    allFolders = folders,
                    strings = strings,
                    locale = language,
                    onOpen = ::openNotebook,
                    onCreate = { folderId -> openNotebook(onCreate(folderId).id) },
                    onCreateFolder = onCreateFolder,
                    onRenameFolder = onRenameFolder,
                    onMoveToFolder = onMoveToFolder,
                    onImport = onImport,
                    onExport = onExport,
                    onDelete = onDelete,
                    onSetOrder = onSetOrder,
                    onEditMetadata = onEditMetadata,
                    selectedId = lastNotebookId,
                    loadCover = loadCover,
                )
            }
            composable(Routes.WORKSPACE) { entry ->
                val id = entry.arguments?.getString("id")
                val notebook = entries.firstOrNull { it.id == id }
                if (notebook != null && notebook.unlocked) {
                    WorkspaceDestination(
                        notebook,
                        read,
                        writer,
                        dark,
                        language,
                        strings,
                        preferences,
                        fullscreenDocumentId == notebook.id,
                        { enabled -> fullscreenDocumentId = notebook.id.takeIf { enabled } },
                        onRefresh,
                        onExport,
                        onEditMetadata,
                        onSecurityAction,
                    )
                }
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(preferences = preferences, strings = strings)
            }
        }
    }
}
