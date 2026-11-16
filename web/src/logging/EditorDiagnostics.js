import {Transaction} from '@codemirror/state';
import {completionStatus, currentCompletions} from '@codemirror/autocomplete';
import {notebookLog} from './NotebookLog.js';
import {version} from '../../package.json';

// Explicit scalar envelope, checked again by NotebookLog.webTrace on Android.
// Never pass document strings, input data, queries, DOM text/HTML or clipboard.
export const diagnosticNumbers = new Set(`sequence clock capturedClock anchor head from to beforeAnchor beforeHead nextAnchor nextHead correctedAnchor correctedHead docLength viewportFrom viewportTo scrollY scrollHeight clientHeight width height clientWidth offsetHeight x y cssWidth cssHeight cssMinHeight cssMaxHeight inlineHeight flexBasis flexShrink fontSize lineHeight paddingY paddingBottom borderY opacity children optionCount selectedIndex innerWidth innerHeight visualWidth visualHeight visualTop visualLeft visualScale deviceScale nativeAnchor nativeHead nativeAnchorOffset nativeFocusOffset freshPointerMs keyboardHeight revision token`.split(' '));
export const diagnosticBooleans = new Set(`empty hasFocus composing selectionSet docChanged pointerDown pointerScrolled corrected nativeCollapsed nativeInside nativeAnchorEditable nativeHeadEditable connected modal topLayer transformed pending keyboardVisible android editContext`.split(' '));
export const diagnosticLabels = new Set(`target tag decision reason userEvent inputType display position overflowY boxSizing flexDirection widthMode heightMode minHeightMode maxHeightMode inlineHeightMode flexBasisMode paddingBottomMode contentVisibility visibility status version focusTarget`.split(' '));
for (const key of `writeId durationMs mono beforeNativeAnchor beforeNativeHead pointerAgeMs requestedY resultY previousY deltaY hostHeight shellHeight editorHeight quickToolsHeight count maxMs avgMs frameCount slowFrames maxGapMs dropped resizeCalls windowResizeCalls visualResizeCalls hostResizeCalls measureRequests scrollbarReads scrollbarWrites updateCalls selectionWrites scrollWrites rafFrames coordinateReads selectionHeight availableHeight blockGap cmMeasureCalls`.split(' ')) diagnosticNumbers.add(key);
for (const key of ['threw','rangeActive','allowed','active','documentFocused']) diagnosticBooleans.add(key);
for (const key of ['operation','actor','trigger','selectionMode','revealMode','stage']) diagnosticLabels.add(key);
const defaultOptions = Object.freeze({selectionMode:'correct',revealMode:'caret'});
let options = defaultOptions;
let enabled = false, sequence = 0;
try { enabled = window.NativeEditor?.diagnosticsEnabled?.() === true; } catch {}
try {
  if (enabled) {
    const initial = JSON.parse(window.NativeEditor?.diagnosticOptions?.() || '{}');
    options = Object.freeze({selectionMode:initial.selectionMode === 'observe' ? 'observe' : 'correct',revealMode:initial.revealMode === 'legacy' ? 'legacy' : 'caret'});
  }
} catch {}
const listeners = new Set(), sampled = new Map();

export function sanitizeDiagnostic(event, fields = {}) {
  if (!/^[a-z][a-z0-9-]{0,75}$/.test(event)) return null;
  const payload = {event};
  for (const [key, value] of Object.entries(fields)) {
    if (diagnosticNumbers.has(key) && typeof value === 'number' && Number.isFinite(value)) payload[key] = Math.round(value * 100) / 100;
    else if (diagnosticBooleans.has(key) && typeof value === 'boolean') payload[key] = value;
    else if (diagnosticLabels.has(key) && typeof value === 'string' && /^[A-Za-z0-9_.-]{1,31}$/.test(value)) payload[key] = value;
  }
  return payload;
}

export const editorDiagnosticLog = {
  get enabled() { return enabled; },
  get options() { return options; },
  setOptions(value = {}) {
    if (!enabled) return;
    options = Object.freeze({selectionMode:value.selectionMode === 'observe' ? 'observe' : 'correct',revealMode:value.revealMode === 'legacy' ? 'legacy' : 'caret'});
    this.emit('diagnostics-options',options);
  },
  setEnabled(value) {
    value = value === true;
    if (value === enabled) return;
    if (!value) this.emit('diagnostics-disabled');
    enabled = value;
    if (!enabled) options = defaultOptions;
    if (enabled) sequence = 0;
    for (const entry of sampled.values()) clearTimeout(entry.timer);
    sampled.clear();
    for (const listener of listeners) listener(enabled);
  },
  subscribe(listener) { listeners.add(listener); return () => listeners.delete(listener); },
  capture(event, fields = {}) {
    return enabled ? sanitizeDiagnostic(event,{...fields,sequence:++sequence,clock:Date.now()}) : null;
  },
  emitBatch(payloads) {
    if (!enabled || !payloads.length) return;
    try {
      if (window.NativeEditor?.traceBatch) window.NativeEditor.traceBatch(JSON.stringify(payloads));
      else for (const payload of payloads) {
        if (window.NativeEditor?.trace) window.NativeEditor.trace(JSON.stringify(payload));
        else notebookLog.debug('EDITOR.DIAG',JSON.stringify(payload));
      }
    } catch { /* No diagnostic failure may affect editing. */ }
  },
  emit(event, fields = {}) {
    if (!enabled) return;
    const payload = sanitizeDiagnostic(event, {...fields, sequence: ++sequence, clock: Date.now()});
    if (!payload) return;
    try {
      if (window.NativeEditor?.trace) window.NativeEditor.trace(JSON.stringify(payload));
      else notebookLog.debug('EDITOR.DIAG', JSON.stringify(payload));
    } catch { /* Diagnostics must not interrupt editing. */ }
  },
  sample(event, fields, interval = 100) {
    if (!enabled) return;
    const now = Date.now(), pending = sampled.get(event);
    fields = {...fields, capturedClock: now};
    if (!pending) {
      const entry = {timer: 0, fields: null};sampled.set(event, entry);
      this.emit(event, fields);
      entry.timer = setTimeout(() => {
        sampled.delete(event);
        if (entry.fields) this.emit(event, entry.fields);
      }, interval);
    } else pending.fields = fields;
  }
};

export function selectionDiagnosticFields(view, selection = view.state.selection.main) {
  return {anchor:selection.anchor, head:selection.head, from:selection.from, to:selection.to,
    empty:selection.empty, docLength:view.state.doc.length, hasFocus:view.hasFocus,composing:view.composing,
    viewportFrom:view.viewport.from, viewportTo:view.viewport.to,
    scrollY:view.scrollDOM.scrollTop, scrollHeight:view.scrollDOM.scrollHeight, clientHeight:view.scrollDOM.clientHeight};
}

export function nativeSelectionFields(view) {
  const selection = document.getSelection();
  const inside = !!selection?.anchorNode && !!selection?.focusNode && view.contentDOM.contains(selection.anchorNode) && view.contentDOM.contains(selection.focusNode);
  const point = (node, offset) => {try {return inside ? view.posAtDOM(node, offset) : -1;} catch {return -1;}};
  const editable = node => {
    const element = node?.nodeType === 1 ? node : node?.parentElement;
    return !!element && !element.closest('[contenteditable="false"]');
  };
  return {nativeInside:inside, nativeCollapsed:selection?.isCollapsed === true,
    nativeAnchor:point(selection?.anchorNode, selection?.anchorOffset || 0), nativeHead:point(selection?.focusNode, selection?.focusOffset || 0),
    nativeAnchorOffset:selection?.anchorOffset || 0, nativeFocusOffset:selection?.focusOffset || 0,
    nativeAnchorEditable:editable(selection?.anchorNode), nativeHeadEditable:editable(selection?.focusNode)};
}

function number(value) {const parsed = parseFloat(value);return Number.isFinite(parsed) ? parsed : -1;}
function mode(value) {return !value ? 'none' : value.endsWith('%') ? 'percent' : value.endsWith('px') ? 'px' : /^[a-z-]+$/.test(value) ? value : 'expression';}
function focusTarget(view) {
  const element = document.activeElement;
  if (element === view.contentDOM) return 'editor';
  if (element?.closest('.acode-colour-dialog')) return 'colour';
  if (element?.closest('#quick-tools')) return 'quicktools';
  return element?.tagName || 'none';
}

export function elementDiagnosticFields(target, element) {
  const rect = element.getBoundingClientRect(), css = getComputedStyle(element);
  return {target, tag:element.tagName, connected:element.isConnected, width:rect.width, height:rect.height, x:rect.left, y:rect.top,
    clientHeight:element.clientHeight, clientWidth:element.clientWidth, scrollHeight:element.scrollHeight, offsetHeight:element.offsetHeight,
    cssWidth:number(css.width),widthMode:mode(css.width),cssHeight:number(css.height), heightMode:mode(css.height), cssMinHeight:number(css.minHeight), minHeightMode:mode(css.minHeight),
    cssMaxHeight:number(css.maxHeight), maxHeightMode:mode(css.maxHeight), inlineHeight:number(element.style.height), inlineHeightMode:mode(element.style.height),
    flexBasis:number(css.flexBasis), flexBasisMode:mode(css.flexBasis), flexShrink:number(css.flexShrink), flexDirection:css.flexDirection,
    display:css.display, position:css.position, overflowY:css.overflowY, boxSizing:css.boxSizing, contentVisibility:css.contentVisibility,
    opacity:number(css.opacity),visibility:css.visibility,transformed:!!css.transform&&css.transform!=='none',topLayer:element.classList.contains('has-top-layer')&&element.hasAttribute('open'),
    fontSize:number(css.fontSize), lineHeight:number(css.lineHeight), paddingY:number(css.paddingTop) + number(css.paddingBottom),paddingBottom:number(css.paddingBottom),paddingBottomMode:mode(css.paddingBottom),
    borderY:number(css.borderTopWidth) + number(css.borderBottomWidth), children:element.children.length, modal:element.matches('dialog[open]')};
}

/** No observers, style reads or timers exist until explicitly enabled via adb. */
export function installEditorDiagnostics(view) {
  let stop = () => {};
  const start = () => {
    const timers = new Set(), tracked = new Map(), watchedParents = new WeakSet();
    let stopped = false, frame = 0, geometryTimer = 0, lastGeometry = 0;
    const later = (fn, ms) => {const id=setTimeout(()=>{timers.delete(id);if(!stopped)fn();},ms);timers.add(id);};
    const viewport = () => {
      const vv=window.visualViewport;
      editorDiagnosticLog.emit('diagnostics-viewport', {version,innerWidth,innerHeight,deviceScale:devicePixelRatio,
        width:number(document.documentElement.style.getPropertyValue('--editor-ui-width')),
        height:number(document.documentElement.style.getPropertyValue('--editor-ui-height')),
        android:/Android/i.test(navigator.userAgent),editContext:!!view.contentDOM.editContext,
        visualWidth:vv?.width || innerWidth,visualHeight:vv?.height || innerHeight,visualTop:vv?.offsetTop || 0,visualLeft:vv?.offsetLeft || 0,visualScale:vv?.scale || 1,
        ...selectionDiagnosticFields(view),focusTarget:focusTarget(view)});
    };
    const describe = root => {
      if (!root.isConnected) return;
      const colour=root.matches('.acode-colour-dialog'), prefix=colour?'colour':'completion';
      const items=colour ? [['dialog',root],['body',root.querySelector('.color-picker')],['picker',root.querySelector('.picker_wrapper')],['wheel',root.querySelector('.picker_sl')],['buttons',root.querySelector('.button-container')]] :
        [['popup',root],['list',root.querySelector('ul')],['option',root.querySelector('li[role="option"]')]];
      items.push(['parent',root.parentElement]);
      const options=colour?0:currentCompletions(view.state).length;
      for (const [target,element] of items) if (element) editorDiagnosticLog.emit(prefix+'-box', {...elementDiagnosticFields(target,element),optionCount:options,status:completionStatus(view.state)||'none',focusTarget:focusTarget(view)});
      const wheel=colour?root.querySelector('.picker_sl'):null;
      if(wheel){
        const pseudo=getComputedStyle(wheel,'::before');
        editorDiagnosticLog.emit('colour-box',{target:'wheel-before',cssWidth:number(pseudo.width),widthMode:mode(pseudo.width),cssHeight:number(pseudo.height),heightMode:mode(pseudo.height),display:pseudo.display,paddingBottom:number(pseudo.paddingBottom),paddingBottomMode:mode(pseudo.paddingBottom)});
      }
    };
    const snapshot = () => {
      if (stopped || frame) return;
      const delay=Math.max(0,lastGeometry+150-Date.now());
      if(delay){if(!geometryTimer)geometryTimer=setTimeout(()=>{geometryTimer=0;snapshot();},delay);return;}
      frame=requestAnimationFrame(()=>{frame=0;lastGeometry=Date.now();if(!stopped)for(const root of tracked.keys())describe(root);});
    };
    const resizeObserver = typeof ResizeObserver==='function' ? new ResizeObserver(snapshot) : null;
    const scan = () => {
      if (stopped) return;
      for(const [root,observer] of tracked) if(!root.isConnected) {
        editorDiagnosticLog.emit(root.matches('.acode-colour-dialog')?'colour-closed':'completion-closed');observer.disconnect();tracked.delete(root);
      }
      for(const root of document.querySelectorAll('.cm-tooltip-autocomplete,.acode-colour-dialog')) if(!tracked.has(root)) {
        const observer=new MutationObserver(snapshot);observer.observe(root,{attributes:true,attributeFilter:['style','class','open']});
        const list=root.querySelector('ul');if(list)observer.observe(list,{childList:true});
        tracked.set(root,observer);resizeObserver?.observe(root);if(list)resizeObserver?.observe(list);
        snapshot();later(()=>describe(root),150);later(()=>describe(root),500);
      }
      // The editor's text subtree is deliberately excluded. Tooltip portals
      // and dialog roots are direct body children in this Acode host.
      for(const child of document.body.children) if(child.id!=='editor-shell'&&!watchedParents.has(child)) {
        bodyObserver.observe(child,{childList:true});watchedParents.add(child);
      }
    };
    const bodyObserver=new MutationObserver(scan);bodyObserver.observe(document.body,{childList:true});
    const nativeChange=()=>editorDiagnosticLog.sample('selection-native', {...selectionDiagnosticFields(view),...nativeSelectionFields(view)},50);
    const scroll=()=>editorDiagnosticLog.sample('selection-scroll',selectionDiagnosticFields(view),120);
    const focus=()=>editorDiagnosticLog.emit('diagnostics-focus',{...selectionDiagnosticFields(view),focusTarget:focusTarget(view)});
    const input=event=>editorDiagnosticLog.emit('input-before',{...selectionDiagnosticFields(view),inputType:event.inputType||'none',focusTarget:focusTarget(view)});
    const composition=event=>editorDiagnosticLog.emit(event.type==='compositionstart'?'input-composition-start':'input-composition-end',{...selectionDiagnosticFields(view),focusTarget:focusTarget(view)});
    let resizeTimer=0;
    const resize=()=>{clearTimeout(resizeTimer);resizeTimer=setTimeout(()=>{viewport();snapshot();},150);};
    document.addEventListener('selectionchange',nativeChange,true);
    document.addEventListener('focusin',focus,true);document.addEventListener('focusout',focus,true);
    view.contentDOM.addEventListener('beforeinput',input,true);view.contentDOM.addEventListener('compositionstart',composition,true);view.contentDOM.addEventListener('compositionend',composition,true);
    view.scrollDOM.addEventListener('scroll',scroll,{passive:true});
    window.addEventListener('resize',resize);window.visualViewport?.addEventListener('resize',resize);
    editorDiagnosticLog.emit('diagnostics-installed',{version});viewport();nativeChange();scan();
    return () => {
      stopped=true;bodyObserver.disconnect();resizeObserver?.disconnect();cancelAnimationFrame(frame);clearTimeout(resizeTimer);clearTimeout(geometryTimer);
      for(const id of timers)clearTimeout(id);for(const observer of tracked.values())observer.disconnect();tracked.clear();
      document.removeEventListener('selectionchange',nativeChange,true);document.removeEventListener('focusin',focus,true);document.removeEventListener('focusout',focus,true);
      view.contentDOM.removeEventListener('beforeinput',input,true);view.contentDOM.removeEventListener('compositionstart',composition,true);view.contentDOM.removeEventListener('compositionend',composition,true);
      view.scrollDOM.removeEventListener('scroll',scroll);window.removeEventListener('resize',resize);window.visualViewport?.removeEventListener('resize',resize);
    };
  };
  const sync = active => {stop();stop=active?start():()=>{};};
  const unsubscribe=editorDiagnosticLog.subscribe(sync);sync(editorDiagnosticLog.enabled);
  return {update(update) {
    if(editorDiagnosticLog.enabled&&(update.selectionSet||update.docChanged))editorDiagnosticLog.sample('selection-applied', {
      ...selectionDiagnosticFields(view),beforeAnchor:update.startState.selection.main.anchor,beforeHead:update.startState.selection.main.head,
      selectionSet:update.selectionSet,docChanged:update.docChanged,userEvent:update.transactions.map(tr=>tr.annotation(Transaction.userEvent)).filter(Boolean)[0]||'none'
    },50);
  },destroy(){unsubscribe();stop();}};
}
