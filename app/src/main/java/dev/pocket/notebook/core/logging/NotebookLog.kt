package dev.pocket.notebook.core.logging

import android.util.Log
import org.json.JSONObject

/**
 * One Android Logcat tag for the entire app. Source modules identify themselves
 * with a scope, not an Android tag. Examples:
 *   NotebookPocket: [EDITOR.SELECTION] native-action-mode ...
 *   NotebookPocket: [EDITOR.IME] input-connection ...
 *   NotebookPocket: [WEB.ASSETS] request ...
 *
 * Never log the document source, clipboard payload, encryption keys, passwords,
 * search queries or document identifiers via the structured trace bridge.
 */
object NotebookLog {
    const val TAG = "NotebookPocket"

    enum class Area {
        APP, NAV, MIGRATION, EDITOR, PREVIEW, ASSETS, SELECTION, FOLD,
        IME, SEARCH, INPUT, VIEWPORT, WEB, BOOT, STORAGE, COMPLETION, COLOUR, DIAGNOSTICS, OTHER
    }

    /** Legacy source scopes are mapped here, never used as separate Logcat tags. */
    private fun scope(source: String): String = source.removePrefix("Notebook")
        .replace(Regex("[^A-Za-z0-9.]"), "")
        .uppercase().ifBlank { "APP" }
        .take(40)

    private fun write(priority: Int, source: String, message: String, error: Throwable? = null): Int {
        val line = "[${scope(source)}] $message"
        return if (error != null) Log.println(priority, TAG, "$line | ${error.javaClass.simpleName}: ${error.message}")
            else Log.println(priority, TAG, line)
    }
    fun v(source: String, message: String): Int = write(Log.VERBOSE, source, message)
    fun d(source: String, message: String): Int = write(Log.DEBUG, source, message)
    fun i(source: String, message: String): Int = write(Log.INFO, source, message)
    fun w(source: String, message: String): Int = write(Log.WARN, source, message)
    fun w(source: String, message: String, error: Throwable?): Int = write(Log.WARN, source, message, error)
    fun e(source: String, message: String): Int = write(Log.ERROR, source, message)
    fun e(source: String, message: String, error: Throwable?): Int = write(Log.ERROR, source, message, error)

    fun trace(area: Area, event: String, details: String = "") =
        d("EDITOR.${area.name}", "event=${event.take(70)} ${details.take(2000)}")

    /** JS instrumentation is a restricted numeric/boolean event envelope. */
    fun webTrace(json: String) {
        if (json.length > 2400) return
        runCatching {
            val data = JSONObject(json)
            val event = data.optString("event")
            if (!event.matches(Regex("[a-z][a-z0-9-]{0,75}"))) return
            val category = when {
                event.startsWith("completion") -> Area.COMPLETION
                event.startsWith("colour") -> Area.COLOUR
                event.startsWith("diagnostics") -> Area.DIAGNOSTICS
                event.contains("fold") || event.contains("ghost") -> Area.FOLD
                event.contains("ime") || event.contains("keyboard") || event.contains("input") || event.contains("cut") -> Area.IME
                event.contains("scroll") || event.contains("resize") || event.contains("orientation") || event.contains("viewport") || event.contains("size") -> Area.VIEWPORT
                event.contains("search") || event.contains("find") -> Area.SEARCH
                event.contains("boot") || event.contains("installed") -> Area.BOOT
                else -> Area.SELECTION
            }
            // Only explicitly permitted fields. No notebook contents or IDs.
            val allowed = listOf("sequence", "clock", "anchor", "head", "from", "to",
                "empty", "docLength", "scrollY", "scrollHeight", "clientHeight", "hasFocus",
                "pointerType", "pointerId", "x", "y", "hitPos", "hitFold", "foldCount",
                "active", "selectionSet", "docChanged", "effectCount", "inputType", "target",
                "firstX", "firstY", "restoreFrom", "restoreTo", "viewportFrom", "viewportTo",
                "capturedClock", "beforeAnchor", "beforeHead", "nextAnchor", "nextHead", "correctedAnchor", "correctedHead",
                "width", "height", "clientWidth", "offsetHeight", "cssHeight", "cssMinHeight", "cssMaxHeight", "inlineHeight",
                "flexBasis", "flexShrink", "fontSize", "lineHeight", "paddingY", "borderY", "children", "optionCount", "selectedIndex",
                "innerWidth", "innerHeight", "visualWidth", "visualHeight", "visualTop", "visualLeft", "visualScale", "deviceScale",
                "nativeAnchor", "nativeHead", "nativeAnchorOffset", "nativeFocusOffset", "freshPointerMs", "keyboardHeight", "revision", "token",
                "pointerDown", "pointerScrolled", "corrected", "nativeCollapsed", "nativeInside", "nativeAnchorEditable", "nativeHeadEditable",
                "connected", "modal", "pending", "keyboardVisible", "tag", "decision", "reason", "userEvent", "display", "position", "overflowY",
                "boxSizing", "flexDirection", "heightMode", "minHeightMode", "maxHeightMode", "inlineHeightMode", "flexBasisMode", "contentVisibility", "status", "version", "focusTarget",
                "opacity", "visibility", "topLayer", "transformed", "android", "editContext", "cssWidth", "widthMode", "paddingBottom", "paddingBottomMode", "composing",
                "writeId", "durationMs", "mono", "beforeNativeAnchor", "beforeNativeHead", "pointerAgeMs", "requestedY", "resultY", "previousY", "deltaY", "coordinateReads",
                "hostHeight", "shellHeight", "editorHeight", "quickToolsHeight", "count", "maxMs", "avgMs", "frameCount", "slowFrames", "maxGapMs", "dropped", "selectionHeight", "availableHeight", "blockGap", "cmMeasureCalls",
                "resizeCalls", "windowResizeCalls", "visualResizeCalls", "hostResizeCalls", "measureRequests", "scrollbarReads", "scrollbarWrites", "updateCalls", "selectionWrites", "scrollWrites", "rafFrames",
                "threw", "rangeActive", "allowed", "documentFocused", "operation", "actor", "trigger", "selectionMode", "revealMode", "stage")
            val scalars = allowed.filter { data.has(it) }.mapNotNull { key ->
                val value = data.opt(key)
                if (value is Number || value is Boolean ||
                    value is String && value.length < 32 && value.matches(Regex("[A-Za-z0-9_.-]+")))
                    "$key=$value" else null
            }
            val events = data.optJSONArray("userEvents")
            val safeEvents = (0 until (events?.length() ?: 0).coerceAtMost(12)).mapNotNull { index ->
                events?.optString(index)?.takeIf { it.matches(Regex("[a-zA-Z0-9._-]{1,35}")) }
            }
            val folds = data.optJSONObject("folds")
            val native = data.optJSONObject("native")
            val counts = buildList {
                if (safeEvents.isNotEmpty()) add("userEvents=${safeEvents.joinToString(",")}")
                if (folds != null) add("foldCount=${folds.optInt("count")}")
                if (native != null) {
                    add("nativeCollapsed=${native.optBoolean("collapsed")}")
                    add("nativeAnchorOffset=${native.optInt("anchorOffset")}")
                    add("nativeFocusOffset=${native.optInt("focusOffset")}")
                }
            }
            trace(category, event, (scalars + counts).joinToString(" "))
        }
    }

    /** Every JS console entry (also preview with no JS bridge) goes through here. */
    fun webConsole(owner: Area, priority: Int, message: String, source: String, line: Int) {
        write(priority, "${owner.name}.JS", "${message.take(700)} (line=$line)")
    }
}
