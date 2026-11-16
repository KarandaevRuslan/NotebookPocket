package dev.pocket.notebook.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.pocket.notebook.R
import dev.pocket.notebook.core.logging.NotebookLog as Log
import dev.pocket.notebook.data.NotebookRepository
import dev.pocket.notebook.i18n.localizedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Migration and first Room open never run on the UI thread. */
@Composable
fun RepositoryBootstrap(language: String, content: @Composable (NotebookRepository) -> Unit) {
    val context = LocalContext.current.applicationContext
    val result by
        produceState<Result<NotebookRepository>?>(null, context) {
            value = withContext(Dispatchers.IO) { runCatching { ProcessRepository.get(context) } }
        }
    when {
        result == null ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        result!!.isFailure -> {
            Log.e(
                "NotebookMigration",
                "Room migration did not finish; legacy files left untouched",
                result!!.exceptionOrNull(),
            )
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(localizedString(context, language, R.string.database_open_error))
            }
        }
        else -> content(result!!.getOrThrow())
    }
}

/**
 * Production repository lifetime matches the app process; test repositories remain explicitly
 * injected.
 */
private object ProcessRepository {
    @Volatile private var repository: NotebookRepository? = null

    fun get(context: android.content.Context): NotebookRepository =
        repository
            ?: synchronized(this) {
                repository
                    ?: NotebookRepository(context.applicationContext).also { repository = it }
            }
}
