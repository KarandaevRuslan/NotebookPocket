package dev.pocket.notebook.web

import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviewOriginInstrumentedTest {
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private fun view(host: String, protected: Boolean, editor: Boolean = false): WebView {
        val loaded = CountDownLatch(1)
        lateinit var web: WebView
        instrumentation.runOnMainSync {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            web =
                createNotebookWebView(
                    context,
                    LocalAssetsClient(
                        context,
                        originHost = host,
                        pageFinished = { loaded.countDown() },
                    ),
                    false,
                    editor,
                )
            if (!editor) applyPreviewStoragePolicy(web, host, protected)
            web.loadDataWithBaseURL(
                "https://$host/",
                "<!doctype html><html><body>Storage isolation test</body></html>",
                "text/html",
                "UTF-8",
                null,
            )
        }
        check(loaded.await(30, TimeUnit.SECONDS)) { "WebView did not load" }
        return web
    }

    private fun evaluate(web: WebView, script: String): String {
        val done = CountDownLatch(1)
        var value = ""
        instrumentation.runOnMainSync {
            web.evaluateJavascript(script) {
                value = it
                done.countDown()
            }
        }
        check(done.await(15, TimeUnit.SECONDS)) { "WebView did not evaluate script" }
        return value
    }

    @Test
    fun trustedLegacyReaderReadsOnlyTheActiveUnprotectedNotebookKey() {
        val id = "legacy-migration-test"
        val html = "<notebook data-state-version=\"a2\"></notebook>"
        val key = LegacyPreviewStateMigration.stateKey(id, html)
        val unrelated = "notebook-pocket:state:other-notebook"
        val editor = view(EDITOR_HOST, false, true)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        try {
            evaluate(editor, "localStorage.setItem('${key}', '{\"a:0\":{\"value\":\"test\"}}');true")
            evaluate(editor, "localStorage.setItem('${unrelated}', 'unrelated-private-state');true")
            val result = kotlinx.coroutines.runBlocking {
                LegacyPreviewStateMigration.pending(context, id, html, false)
            }
            assertEquals(key, result?.key)
            assertEquals("{\"a:0\":{\"value\":\"test\"}}", result?.value)
            assertNull(result?.timers)
            assertNull(kotlinx.coroutines.runBlocking {
                LegacyPreviewStateMigration.pending(context, id, html, true)
            })
            LegacyPreviewStateMigration.markComplete(context, requireNotNull(result))
            assertNull(kotlinx.coroutines.runBlocking {
                LegacyPreviewStateMigration.pending(context, id, html, false)
            })
        } finally {
            evaluate(editor, "localStorage.removeItem('${key}');localStorage.removeItem('${unrelated}');true")
            instrumentation.runOnMainSync { editor.destroy() }
        }
    }

    @Test
    fun instructionCannotReadEditorOrAnotherPreviewStorageAndProtectedStorageIsDisabled() {
        val editor = view(EDITOR_HOST, false, true)
        val first = view(previewHost("origin-test-one"), false)
        val second = view(previewHost("origin-test-two"), false)
        val protectedView = view(previewHost("origin-test-protected"), true)
        try {
            evaluate(editor, "localStorage.setItem('np-test-secret', 'EDITOR_SECRET'); true")
            assertEquals("null", evaluate(first, "localStorage.getItem('np-test-secret')"))
            evaluate(first, "localStorage.setItem('np-test-secret', 'FIRST_PREVIEW_SECRET'); true")
            assertEquals("null", evaluate(second, "localStorage.getItem('np-test-secret')"))
            assertFalse(
                evaluate(
                        protectedView,
                        "(function(){try{return localStorage.getItem('np-test-secret')}catch(e){return e.name}})()",
                    )
                    .contains("SECRET")
            )
            instrumentation.runOnMainSync { assertFalse(protectedView.settings.domStorageEnabled) }
        } finally {
            evaluate(editor, "localStorage.removeItem('np-test-secret')")
            evaluate(first, "localStorage.removeItem('np-test-secret')")
            instrumentation.runOnMainSync {
                listOf(editor, first, second, protectedView).forEach { it.destroy() }
            }
        }
    }
}
