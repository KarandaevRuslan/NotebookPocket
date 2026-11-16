import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {JSDOM} from 'jsdom';

// Execute the actual Vite output. This catches build/CSP-variant import and
// modal lifecycle regressions; JSDOM cannot validate layout or Android IME.
const base = new URL('../dist/', import.meta.url);
const manifest = JSON.parse(await readFile(new URL('.vite/manifest-editor.json', base)));
const [script, css] = await Promise.all([
  readFile(new URL(manifest['editor.html'].file, base), 'utf8'),
  readFile(new URL(manifest['style.css'].file, base), 'utf8')
]);
const dom = new JSDOM('<!doctype html><html lang="en"><head></head><body class="editor-page"><div id="editor-shell"><div id="editor"></div></div></body></html>', {
  url: 'https://appassets.androidplatform.net/assets/web/editor.html',
  runScripts: 'outside-only', pretendToBeVisual: true
});
const w = dom.window, errors = [], traces = [];
w.console.error = (...args) => errors.push(args.map(String).join(' '));
w.addEventListener('error', event => errors.push(String(event.error || event.message)));
w.Range.prototype.getClientRects = () => [];
w.Range.prototype.getBoundingClientRect = () => ({left:0,right:0,top:0,bottom:0,width:0,height:0});
w.matchMedia = query => ({matches:query.includes('reduced-motion'),addEventListener(){},removeEventListener(){}});
w.ResizeObserver = class {observe(){}unobserve(){}disconnect(){}};
w.HTMLElement.prototype.scrollTo = function(options){if(typeof options?.top==='number')this.scrollTop=options.top;};
w.HTMLDialogElement.prototype.showModal = function() {
  assert.equal(this.isConnected, true);
  assert.ok(this.querySelector('.picker_wrapper'));
  this.setAttribute('open', '');
};
w.HTMLDialogElement.prototype.close = function() {this.removeAttribute('open');};
const batches=[];
w.NativeEditor = {ready(){},changed(){},cursorSelection(){},searchState(){},setNativeContextMenuDisabled(){},diagnosticsEnabled:()=>false,trace:json=>traces.push(JSON.parse(json)),traceBatch:json=>{const batch=JSON.parse(json);batches.push(batch);traces.push(...batch);},systemConfiguration:()=>'{"keyboardHeight":0,"hardKeyboardHidden":2}'};
const style = w.document.createElement('style');style.textContent = css;w.document.head.append(style);
try {
  w.eval(script);
  assert.ok(w.EditorApi);
  assert.ok(parseFloat(w.document.documentElement.style.getPropertyValue('--editor-ui-height'))>0);
  const selectionPrototype=Object.getPrototypeOf(w.document.getSelection());
  const collapse=selectionPrototype.collapse,focus=w.HTMLElement.prototype.focus;
  const scroller=w.document.querySelector('.cm-scroller');
  const view=w.editorManager.editor,originalMeasure=view.measure;
  const originalScrollTop=Object.getOwnPropertyDescriptor(scroller,'scrollTop');
  w.EditorApi.setDiagnosticsEnabled(true);
  assert.ok(traces.some(event=>event.event==='diagnostics-installed'));
  assert.notEqual(selectionPrototype.collapse,collapse);
  assert.notEqual(w.HTMLElement.prototype.focus,focus);
  w.EditorApi.setDiagnosticsOptions({selectionMode:'observe',revealMode:'legacy'});
  w.EditorApi.setSource('const diagnosticProbe = 1;');
  w.EditorApi.focus();
  const content=w.document.querySelector('.cm-content');
  w.document.getSelection().collapse(content.firstChild.firstChild,1);
  scroller.scrollTop=23;
  assert.equal(w.EditorApi.hasEditingFocus(),true);
  const originalHasFocus=w.document.hasFocus;
  w.document.hasFocus=()=>false;
  assert.equal(w.EditorApi.hasEditingFocus(),true);
  w.document.hasFocus=originalHasFocus;
  // Execute the original pointer reveal in the minified bundle, with the
  // precise geometry-unavailable short-range regression from the review.
  w.EditorApi.setDiagnosticsOptions({selectionMode:'correct',revealMode:'caret'});
  const coords=view.coordsAtPos,block=view.lineBlockAt,rect=scroller.getBoundingClientRect;
  const height=Object.getOwnPropertyDescriptor(scroller,'clientHeight');
  try{
    view.coordsAtPos=pos=>pos===1?null:{left:10,right:11,top:400,bottom:420};
    view.lineBlockAt=()=>({top:0,bottom:10000});
    scroller.getBoundingClientRect=()=>({left:0,right:400,top:0,bottom:300,width:400,height:300});
    Object.defineProperty(scroller,'clientHeight',{configurable:true,value:300});
    scroller.scrollTop=23;
    view.dispatch({selection:{anchor:1,head:4},userEvent:'select.pointer'});
    await new Promise(resolve=>setTimeout(resolve,70));
    assert.ok(scroller.scrollTop>23,'short range with unrendered anchor must reveal its head');
  }finally{
    view.coordsAtPos=coords;view.lineBlockAt=block;scroller.getBoundingClientRect=rect;
    if(height)Object.defineProperty(scroller,'clientHeight',height);else delete scroller.clientHeight;
  }
  await new Promise(resolve=>setTimeout(resolve,170));
  assert.ok(batches.length>0);
  assert.ok(batches.every(batch=>batch.length<=32));
  assert.ok(traces.some(event=>event.event==='selection-dom-write'&&event.operation==='collapse'));
  assert.ok(traces.some(event=>event.event==='scroll-js-write'&&event.requestedY===23));
  assert.ok(traces.some(event=>event.event==='focus-editing-check'&&event.documentFocused===false&&event.active===true));
  assert.ok(traces.some(event=>event.event==='scroll-auto-decision'&&event.reason==='geometry-fallback'&&event.allowed));
  assert.ok(traces.some(event=>event.event==='pipeline-scrollbar-read'));
  assert.ok(traces.some(event=>event.event==='pipeline-scrollbar-write'));
  assert.ok(traces.some(event=>event.event==='pipeline-cm-measure'));
  assert.equal(JSON.stringify(traces).includes('diagnosticProbe'),false);
  w.EditorApi.setDiagnosticsEnabled(false);
  assert.ok(traces.some(event=>event.event==='diagnostics-disabled'));
  assert.equal(selectionPrototype.collapse,collapse);
  assert.equal(w.HTMLElement.prototype.focus,focus);
  assert.equal(view.measure,originalMeasure);
  assert.deepEqual(Object.getOwnPropertyDescriptor(scroller,'scrollTop'),originalScrollTop);
  const source = 'const a = 1;';w.EditorApi.setSource(source);w.EditorApi.focus();
  w.acode.exec('insert-color');await new Promise(resolve=>setTimeout(resolve,40));
  const dialog = w.document.querySelector('dialog.acode-colour-dialog[open]');
  assert.ok(dialog);assert.equal(w.getComputedStyle(dialog).display, 'flex');
  assert.equal(w.getComputedStyle(dialog).opacity, '1');
  assert.match(w.getComputedStyle(dialog).maxHeight,/--editor-ui-height/);
  assert.ok(dialog.classList.contains('has-top-layer'));
  assert.equal(w.EditorApi.handleBack(), true);
  await new Promise(resolve=>setTimeout(resolve,340));
  assert.equal(w.document.querySelector('.acode-colour-dialog'), null);
  assert.equal(w.EditorApi.getSource(), source);
  assert.deepEqual(errors, []);
  console.log('Built editor DOM: boot, diagnostic hooks/batches/restore, Cut editing ownership, colour mount/Back and source preservation passed (no layout/IME validation).');
} finally {w.EditorApi?.destroy();w.close();}
