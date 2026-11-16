import {parse} from '@babel/parser';
import {readFile,writeFile,rm} from 'node:fs/promises';
import {resolve} from 'node:path';
const source=resolve(process.argv[2]),root=resolve(import.meta.dirname,'../..');
const code=await readFile(resolve(source,'hooks/modify-java-files.js'),'utf8');const tree=parse(code,{sourceType:'unambiguous'});
let spec;
function visit(n){if(!n||typeof n!=='object')return;if(n.type==='ObjectProperty'&&n.key.value==='SystemWebView.java')spec=n.value;for(const[k,v]of Object.entries(n)){if(k==='loc')continue;if(Array.isArray(v))v.forEach(visit);else if(v&&typeof v==='object')visit(v);}}visit(tree.program);
if(!spec)throw Error('SystemWebView.java spec not found');
const val=n=>n.type==='StringLiteral'?n.value:n.type==='ArrayExpression'?n.elements.map(val):n.type==='ObjectExpression'?Object.fromEntries(n.properties.filter(p=>p.type==='ObjectProperty').map(p=>[p.key.name||p.key.value,val(p.value)])):n.type==='TemplateLiteral'?n.quasis.map(q=>q.value.cooked).join(''):null;
const config=val(spec);
const names=new Set(['setNativeContextMenuDisabled','onCreateInputConnection','startActionMode','startActionModeForChild','wrapActionModeCallback','suppressActionMode','suppressActionModeUi']);
const methods=config.methods.filter(m=>names.has(m.name));
const src=`package dev.pocket.notebook.web;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.text.InputType;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowInsets;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import java.util.List;
import dev.pocket.notebook.core.logging.NotebookLog;

/** Acode hooks/modify-java-files.js SystemWebView core bodies retained.
 * Diagnostic prologues, constructor and IME configuration are the host boundary.
 */
public final class EditorActionModeWebView extends WebView {
    private int type = 0;
    private final int NO_SUGGESTIONS = 0;
    private final int NO_SUGGESTIONS_AGGRESSIVE = 1;
    private boolean nativeContextMenuDisabled = false;
    private volatile int keyboardHeight = 0;
    private volatile boolean keyboardVisible = false;
    private volatile int keyboardRevision = 0;
    private final CutImeContinuation cutImeContinuation = new CutImeContinuation();
    private boolean diagnosticLoggingEnabled = false;
    private long lastSizeTrace = 0, lastHeightTrace = 0, lastAnimationTrace = 0, lastMoveTrace = 0;
    public void setDiagnosticLoggingEnabled(boolean enabled) {
        if (!enabled) logDiagnostic("diagnostics-native-disabled", 0);
        diagnosticLoggingEnabled = enabled;
        if (enabled) logDiagnostic("diagnostics-native-enabled", 0);
    }
    public void logDiagnostic(String event, int token) {
        logDiagnostic(event, token, "");
    }
    private void logDiagnostic(String event, int token, String extra) {
        if (!diagnosticLoggingEnabled) return;
        long now = SystemClock.uptimeMillis();
        if (event.equals("webview-size")) { if (now - lastSizeTrace < 150) return; lastSizeTrace = now; }
        if (event.equals("ime-height")) { if (now - lastHeightTrace < 150) return; lastHeightTrace = now; }
        if (event.equals("ime-animation-progress")) { if (now - lastAnimationTrace < 100) return; lastAnimationTrace = now; }
        WindowInsetsCompat root = ViewCompat.getRootWindowInsets(this);
        boolean rootIme = root != null && root.isVisible(WindowInsetsCompat.Type.ime());
        int rootHeight = root == null ? -1 : Math.round(root.getInsets(WindowInsetsCompat.Type.ime()).bottom / getResources().getDisplayMetrics().density);
        NotebookLog.Area area = event.startsWith("selection") ? NotebookLog.Area.SELECTION : event.startsWith("diagnostics") ? NotebookLog.Area.DIAGNOSTICS : event.equals("webview-size") ? NotebookLog.Area.VIEWPORT : NotebookLog.Area.IME;
        NotebookLog.INSTANCE.trace(area, event, "clock=" + System.currentTimeMillis() + " token=" + token + " revision=" + keyboardRevision
            + " ime=" + keyboardVisible + " imeHeight=" + keyboardHeight + " rootIme=" + rootIme + " rootHeight=" + rootHeight
            + " pending=" + (cutImeContinuation != null && cutImeContinuation.hasPending()) + " focus=" + hasFocus() + " windowFocus=" + hasWindowFocus()
            + " attached=" + isAttachedToWindow() + " visibility=" + getVisibility() + " width=" + getWidth() + " height=" + getHeight()
            + " nativeMenuDisabled=" + nativeContextMenuDisabled
            + (cutImeContinuation == null ? "" : cutImeContinuation.diagnosticState(now)) + extra);
    }
    public int getKeyboardRevision() { return keyboardRevision; }
    public void cancelKeyboardRequest() {
        cancelKeyboardRequest("explicit");
    }
    public void cancelKeyboardRequest(String reason) {
        if (cutImeContinuation != null && cutImeContinuation.hasPending()) logDiagnostic("cut-cancel-" + reason, keyboardRevision);
        keyboardRevision++;
        if (cutImeContinuation != null) cutImeContinuation.cancel();
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (diagnosticLoggingEnabled && (action != MotionEvent.ACTION_MOVE || SystemClock.uptimeMillis() - lastMoveTrace >= 80)) {
            if (action == MotionEvent.ACTION_MOVE) lastMoveTrace = SystemClock.uptimeMillis();
            logDiagnostic("selection-native-touch", action, " xPx=" + event.getX() + " yPx=" + event.getY()
                + " pointers=" + event.getPointerCount() + " eventUptime=" + event.getEventTime());
        }
        if (action == MotionEvent.ACTION_DOWN) cancelKeyboardRequest("touch");
        return super.onTouchEvent(event);
    }
    private boolean canKeepCutIme() {
        return isAttachedToWindow() && hasFocus() && getVisibility() == View.VISIBLE;
    }
    public void armKeyboardAfterCut(int revision) {
        logDiagnostic("cut-arm-request", revision);
        if (revision != keyboardRevision) { logDiagnostic("cut-arm-stale", revision); return; }
        // The trusted bridge only arms after capturing a visible IME.
        cutImeContinuation.arm(revision, SystemClock.uptimeMillis(), true, canKeepCutIme());
        updateImeConfiguration(getRootWindowInsets());
        logDiagnostic("cut-arm-result", revision);
    }
    public void completeKeyboardAfterCut(int revision) {
        logDiagnostic("cut-complete-request", revision);
        if (cutImeContinuation.complete(revision, SystemClock.uptimeMillis(), keyboardVisible, canKeepCutIme(), hasWindowFocus())) showCutImeOnce();
        logDiagnostic("cut-complete-result", revision);
    }
    private void showCutImeOnce() {
        logDiagnostic("cut-show-scheduled", keyboardRevision);
        final int revision = keyboardRevision;
        post(() -> {
            if (revision != keyboardRevision || !canKeepCutIme() || !hasWindowFocus()) { logDiagnostic("cut-show-skipped", revision); return; }
            logDiagnostic("cut-show-issued", revision);
            WindowInsetsControllerCompat controller = ViewCompat.getWindowInsetsController(this);
            if (controller != null) controller.show(WindowInsetsCompat.Type.ime());
            else ((InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE))
                .showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        });
    }
    @Override public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) { logDiagnostic("ime-back", keyboardRevision); cancelKeyboardRequest("back"); }
        return super.onKeyPreIme(keyCode, event);
    }
    @Override public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        logDiagnostic("ime-window-focus", hasWindowFocus ? 1 : 0);
        // Device traces show a 28 ms clipboard window-focus interruption.
        // A window-focus callback alone must not invalidate a captured Cut.
        // Activity pause, native focus loss, Back and visibility still cancel.
        if (hasWindowFocus) updateImeConfiguration(getRootWindowInsets());
    }
    @Override protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        logDiagnostic("ime-view-focus", gainFocus ? 1 : 0);
        if (!gainFocus) cancelKeyboardRequest("view-focus");
    }
    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        logDiagnostic("ime-view-visibility", visibility);
        if (visibility != View.VISIBLE) cancelKeyboardRequest("visibility");
    }
    public EditorActionModeWebView(Context context) {
        super(context);
        ViewCompat.setWindowInsetsAnimationCallback(this, new WindowInsetsAnimationCompat.Callback(
            WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
            @Override public void onPrepare(WindowInsetsAnimationCompat animation) {
                super.onPrepare(animation);
                logDiagnostic("ime-animation-prepare", keyboardRevision, " typeMask=" + animation.getTypeMask());
            }
            @Override public WindowInsetsCompat onProgress(WindowInsetsCompat insets, List<WindowInsetsAnimationCompat> running) {
                if (diagnosticLoggingEnabled) logDiagnostic("ime-animation-progress", keyboardRevision,
                    " passedIme=" + insets.isVisible(WindowInsetsCompat.Type.ime())
                    + " passedHeightPx=" + insets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
                if (cutImeContinuation.hasPending()) updateImeConfiguration(null);
                return insets;
            }
            @Override public void onEnd(WindowInsetsAnimationCompat animation) { updateImeConfiguration(null); logDiagnostic("ime-animation-end", keyboardRevision); }
        });
    }
    public String systemConfigurationJson() {
        return "{\\"keyboardHeight\\":" + keyboardHeight + ",\\"keyboardVisible\\":" + keyboardVisible + ",\\"keyboardRevision\\":" + keyboardRevision + ",\\"hardKeyboardHidden\\":" + getResources().getConfiguration().hardKeyboardHidden + "}";
    }
    private void updateImeConfiguration(WindowInsets fallback) {
        WindowInsetsCompat compat = ViewCompat.getRootWindowInsets(this);
        if (compat == null && fallback != null) compat = WindowInsetsCompat.toWindowInsetsCompat(fallback, this);
        if (compat == null) return;
        boolean oldVisible = keyboardVisible;
        int oldHeight = keyboardHeight;
        keyboardVisible = compat.isVisible(WindowInsetsCompat.Type.ime());
        keyboardHeight = keyboardVisible
            ? Math.round(compat.getInsets(WindowInsetsCompat.Type.ime()).bottom / getResources().getDisplayMetrics().density) : 0;
        if (oldVisible != keyboardVisible) logDiagnostic("ime-visibility", keyboardRevision);
        else if (oldHeight != keyboardHeight) logDiagnostic("ime-height", keyboardRevision);
        if (cutImeContinuation != null && cutImeContinuation.insetsChanged(keyboardRevision, SystemClock.uptimeMillis(), keyboardVisible, canKeepCutIme(), hasWindowFocus())) showCutImeOnce();
    }
    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        updateImeConfiguration(insets);
        return super.onApplyWindowInsets(insets);
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateImeConfiguration(getRootWindowInsets());
        logDiagnostic("webview-size", keyboardRevision);
    }
    @Override protected void onScrollChanged(int x, int y, int oldX, int oldY) {
        super.onScrollChanged(x, y, oldX, oldY);
        if (diagnosticLoggingEnabled) logDiagnostic("viewport-native-page-scroll", keyboardRevision,
            " xPx=" + x + " yPx=" + y + " oldXPx=" + oldX + " oldYPx=" + oldY);
    }

`+methods.map(m=>`${m.notation||''}\n${m.modifier} ${m.returnType} ${m.name}(${m.params.map(p=>p.type+' '+p.name).join(', ')}) {\n${m.name==='onCreateInputConnection'?'logDiagnostic("ime-input-connection", keyboardRevision);\n':m.name.startsWith('startActionMode')?'logDiagnostic("selection-action-mode", keyboardRevision);\n':''}${m.body.join('\n')}\n}`).join('\n\n')+'\n}\n';
await writeFile(resolve(root,'app/src/main/java/dev/pocket/notebook/web/EditorActionModeWebView.java'),src);
await rm(resolve(root,'app/src/main/java/dev/pocket/notebook/web/EditorActionModeWebView.kt'),{force:true});
await writeFile(resolve(root,'third_party/acode/upstream/modify-java-files.js'),code);
console.log('Projected',methods.length,'Acode native methods.');
