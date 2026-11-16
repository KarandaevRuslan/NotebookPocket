package dev.pocket.notebook.i18n

import android.content.Context
import android.content.res.Configuration
import dev.pocket.notebook.R
import dev.pocket.notebook.settings.AppLanguage
import java.util.Locale

/** App-owned UI strings; notebook source and user content are never translated. */
data class AppStrings(
    val library: String, val librarySubtitle: String, val search: String,
    val newNotebook: String, val importFile: String, val exportFile: String,
    val settings: String, val theme: String, val language: String,
    val system: String, val light: String, val dark: String,
    val russian: String, val english: String, val close: String,
    val code: String, val preview: String, val previewCompact: String, val addJs: String, val addMarkdown: String,
    val findInCode: String, val back: String, val delete: String,
    val deleteTitle: String, val deleteDescription: String, val cancel: String,
    val notebook: String, val noResults: String, val noResultsSubtitle: String,
    val documents: String, val imported: String, val exported: String,
    val importError: String, val exportError: String, val saveError: String,
    val paneHint: String, val previewHint: String, val railLibrary: String,
    val editMetadata: String, val titleLabel: String, val descriptionLabel: String,
    val saveMetadata: String, val moreActions: String, val reorderHint: String,
    val retry: String, val editorFailedToStart: String, val previewFailedToStart: String,
    val untitled: String, val workspace: String, val resetPreview: String,
    val resetPreviewTitle: String, val resetPreviewDescription: String,
    val authorLabel: String, val versionLabel: String,
    val chooseCover: String, val removeCover: String, val coverError: String,
    val encryptedLabel: String, val setPassword: String, val changePassword: String,
    val removePassword: String, val unlockDocument: String, val passwordLabel: String,
    val passwordHelp: String, val removePasswordWarning: String,
    val confirm: String, val passwordError: String,
    val newFolder: String, val renameFolder: String, val moveToFolder: String,
    val folderName: String, val rootFolder: String, val folderDeleteHint: String,
    val folderEmpty: String, val folderMoveHint: String,
    val expandActions: String, val collapseActions: String
)

fun resolveLanguage(choice: AppLanguage): String = when (choice) {
    AppLanguage.RU -> "ru"
    AppLanguage.EN -> "en"
    AppLanguage.SYSTEM -> if (Locale.getDefault().language == "ru") "ru" else "en"
}

/** Resource-backed translations. User notebooks are never translated at render time. */
fun localizedContext(context: Context, language: String): Context {
    val config = Configuration(context.resources.configuration)
    config.setLocale(Locale.forLanguageTag(language))
    return context.createConfigurationContext(config)
}

fun localizedString(context: Context, language: String, resource: Int): String =
    localizedContext(context, language).getString(resource)

fun stringsFor(context: Context, language: String): AppStrings {
    val res = localizedContext(context, language)
    return AppStrings(
        library = res.getString(R.string.library),
        librarySubtitle = res.getString(R.string.library_subtitle),
        search = res.getString(R.string.search),
        newNotebook = res.getString(R.string.new_notebook),
        importFile = res.getString(R.string.import_file),
        exportFile = res.getString(R.string.export_file),
        settings = res.getString(R.string.settings),
        theme = res.getString(R.string.theme),
        language = res.getString(R.string.language),
        system = res.getString(R.string.system),
        light = res.getString(R.string.light),
        dark = res.getString(R.string.dark),
        russian = res.getString(R.string.russian),
        english = res.getString(R.string.english),
        close = res.getString(R.string.close),
        code = res.getString(R.string.code),
        preview = res.getString(R.string.preview),
        previewCompact = res.getString(R.string.preview_compact),
        addJs = res.getString(R.string.add_js),
        addMarkdown = res.getString(R.string.add_markdown),
        findInCode = res.getString(R.string.find_in_code),
        back = res.getString(R.string.back),
        delete = res.getString(R.string.delete),
        deleteTitle = res.getString(R.string.delete_title),
        deleteDescription = res.getString(R.string.delete_description),
        cancel = res.getString(R.string.cancel),
        notebook = res.getString(R.string.notebook),
        noResults = res.getString(R.string.no_results),
        noResultsSubtitle = res.getString(R.string.no_results_subtitle),
        documents = res.getString(R.string.documents),
        imported = res.getString(R.string.imported),
        exported = res.getString(R.string.exported),
        importError = res.getString(R.string.import_error),
        exportError = res.getString(R.string.export_error),
        saveError = res.getString(R.string.save_error),
        paneHint = res.getString(R.string.pane_hint),
        previewHint = res.getString(R.string.preview_hint),
        railLibrary = res.getString(R.string.rail_library),
        editMetadata = res.getString(R.string.edit_metadata),
        titleLabel = res.getString(R.string.title_label),
        descriptionLabel = res.getString(R.string.description_label),
        saveMetadata = res.getString(R.string.save_metadata),
        moreActions = res.getString(R.string.more_actions),
        reorderHint = res.getString(R.string.reorder_hint),
        retry = res.getString(R.string.retry),
        editorFailedToStart = res.getString(R.string.editor_failed_to_start),
        previewFailedToStart = res.getString(R.string.preview_failed_to_start),
        untitled = res.getString(R.string.untitled),
        workspace = res.getString(R.string.workspace),
        resetPreview = res.getString(R.string.reset_preview),
        resetPreviewTitle = res.getString(R.string.reset_preview_title),
        resetPreviewDescription = res.getString(R.string.reset_preview_description),
        authorLabel = res.getString(R.string.author_label),
        versionLabel = res.getString(R.string.version_label),
        chooseCover = res.getString(R.string.choose_cover),
        removeCover = res.getString(R.string.remove_cover),
        coverError = res.getString(R.string.cover_error),
        encryptedLabel = res.getString(R.string.encrypted_label),
        setPassword = res.getString(R.string.set_password),
        changePassword = res.getString(R.string.change_password),
        removePassword = res.getString(R.string.remove_password),
        unlockDocument = res.getString(R.string.unlock_document),
        passwordLabel = res.getString(R.string.password_label),
        passwordHelp = res.getString(R.string.password_help),
        removePasswordWarning = res.getString(R.string.remove_password_warning),
        confirm = res.getString(R.string.confirm),
        passwordError = res.getString(R.string.password_error),
        newFolder = res.getString(R.string.new_folder),
        renameFolder = res.getString(R.string.rename_folder),
        moveToFolder = res.getString(R.string.move_to_folder),
        folderName = res.getString(R.string.folder_name),
        rootFolder = res.getString(R.string.root_folder),
        folderDeleteHint = res.getString(R.string.folder_delete_hint),
        folderEmpty = res.getString(R.string.folder_empty),
        folderMoveHint = res.getString(R.string.folder_move_hint),
        expandActions = res.getString(R.string.expand_actions),
        collapseActions = res.getString(R.string.collapse_actions)
    )
}
