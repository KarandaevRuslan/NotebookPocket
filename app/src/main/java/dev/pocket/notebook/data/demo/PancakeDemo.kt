package dev.pocket.notebook.data.demo

import android.content.Context
import dev.pocket.notebook.data.NotebookMetadata
import dev.pocket.notebook.data.NotebookMetadataCodec
import org.json.JSONArray
import org.json.JSONObject

/** One executable source and one attachment set, independent of the app language. */
internal object PancakeDemo {
    private const val ROOT = "demo/pancakes/"
    private val names =
        listOf(
            "styles/pancakes.css",
            "i18n/en.json",
            "i18n/ru.json",
            "images/pancakes.png",
            "images/preparation.png",
        )
    @Volatile private var cached: Map<String, ByteArray>? = null

    fun resources(context: Context): Map<String, ByteArray> =
        cached
            ?: synchronized(this) {
                cached
                    ?: names
                        .associateWith {
                            context.assets.open(ROOT + it).use { stream -> stream.readBytes() }
                        }
                        .also { cached = it }
            }

    fun source(context: Context, language: String): String {
        val code =
            context.assets.open(ROOT + "notebook.html").bufferedReader().use { it.readText() }
        val text =
            JSONObject(
                resources(context)
                    .getValue("i18n/${if(language == "ru") "ru" else "en"}.json")
                    .toString(Charsets.UTF_8)
            )
        return NotebookMetadataCodec.update(
            code,
            NotebookMetadata(text.getString("title"), text.getString("lede"), "", "2.0"),
        )
    }

    fun canUpgradeV5(context: Context, source: String, resources: Map<String, ByteArray>): Boolean {
        val previous =
            context.assets.open(ROOT + "upgrade/v5-source.html").bufferedReader().use {
                it.readText()
            }
        if (source != NotebookMetadataCodec.update(previous, NotebookMetadataCodec.read(source)))
            return false
        val hashes =
            JSONObject(
                context.assets.open(ROOT + "upgrade/v5-resources.json").bufferedReader().use {
                    it.readText()
                }
            )
        if (resources.keys != hashes.keys().asSequence().toSet()) return false
        return resources.all { (name, bytes) ->
            dev.pocket.notebook.data.security.NotebookCrypto.sha256(bytes) == hashes.getString(name)
        }
    }

    /** v6 must match exactly (apart from public metadata) before any automatic upgrade. */
    fun canUpgradeV6(context: Context, source: String, resources: Map<String, ByteArray>): Boolean {
        val previous =
            context.assets.open(ROOT + "upgrade/v6-source.html").bufferedReader().use {
                it.readText()
            }
        if (source != NotebookMetadataCodec.update(previous, NotebookMetadataCodec.read(source)))
            return false
        val hashes =
            JSONObject(
                context.assets.open(ROOT + "upgrade/v6-resources.json").bufferedReader().use {
                    it.readText()
                }
            )
        if (resources.keys != hashes.keys().asSequence().toSet()) return false
        return resources.all { (name, bytes) ->
            dev.pocket.notebook.data.security.NotebookCrypto.sha256(bytes) == hashes.getString(name)
        }
    }

    fun legacyHashes(context: Context): Set<String> {
        val array =
            JSONArray(
                context.assets.open(ROOT + "legacy-hashes.json").bufferedReader().use {
                    it.readText()
                }
            )
        return (0 until array.length()).map { array.getString(it) }.toSet()
    }
}
