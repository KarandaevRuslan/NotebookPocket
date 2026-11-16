package dev.pocket.notebook.data.demo

import dev.pocket.notebook.data.*

import android.content.Context
import dev.pocket.notebook.R
import dev.pocket.notebook.data.collection.CollectionStore
import dev.pocket.notebook.data.document.DocumentContent
import dev.pocket.notebook.data.packing.NotebookPackage
import dev.pocket.notebook.data.resource.NotebookResources
import dev.pocket.notebook.data.security.NotebookCrypto
import dev.pocket.notebook.data.storage.StorageAccess

internal class DemoUpgrade(
    private val context: Context,
    private val storage: StorageAccess,
    private val collections: CollectionStore,
    private val content: DocumentContent,
    private val resources: NotebookResources,
) {
    private fun sampleSource(language: String): String =
        dev.pocket.notebook.data.demo.PancakeDemo.source(context, language)

    suspend fun migrateUntouchedLegacyDemo(language: String): Boolean {
        val preferences = context.getSharedPreferences("demo-upgrades", 0)
        if (preferences.getBoolean("pancakes-v7", false)) return false
        val legacy =
            context.resources.openRawResource(R.raw.legacy_rice_demo).bufferedReader().use {
                it.readText()
            }
        val hashes =
            dev.pocket.notebook.data.demo.PancakeDemo.legacyHashes(context) +
                NotebookCrypto.sha256(legacy.toByteArray())
        var changed = false
        run {
            for (node in storage.dao.allNotebooks()) {
                if (node.encrypted || collections.parent(node)?.encrypted == true) continue
                // Do not replace an apparently unchanged committed source when a user has a draft.
                if (content.hasPendingDraft(node)) continue
                val old = runCatching { content.readNode(node) }.getOrNull() ?: continue
                val existingResources = resources.read(node)
                val legacyDemo =
                    NotebookCrypto.sha256(old.toByteArray()) in hashes &&
                        existingResources.isEmpty()
                val v5Demo =
                    runCatching {
                            dev.pocket.notebook.data.demo.PancakeDemo.canUpgradeV5(
                                context,
                                old,
                                existingResources,
                            )
                        }
                        .getOrDefault(false)
                val v6Demo =
                    runCatching {
                            dev.pocket.notebook.data.demo.PancakeDemo.canUpgradeV6(
                                context,
                                old,
                                existingResources,
                            )
                        }
                        .getOrDefault(false)
                if (!legacyDemo && !v5Demo && !v6Demo) continue
                val source =
                    if (v5Demo || v6Demo)
                        NotebookMetadataCodec.update(
                            sampleSource(language),
                            NotebookMetadataCodec.read(old),
                        )
                    else sampleSource(language)
                val meta = NotebookMetadataCodec.read(source)
                val image = content.coverOf(node)
                val packet = if (node.packageFormat) content.packageFrom(node) else null
                val bytes =
                    NotebookPackage.pack(
                        source,
                        meta,
                        image,
                        packet?.coverMime ?: image?.let(content::detectMime),
                        resources = dev.pocket.notebook.data.demo.PancakeDemo.resources(context),
                    )
                content.persist(node, bytes, true, meta, image, false)
                changed = true
            }
        }
        check(preferences.edit().putBoolean("pancakes-v7", true).commit())
        return changed
    }
}
