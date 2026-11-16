import test from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import {compile} from 'sass';
import {fileURLToPath} from 'node:url';
import {readFile} from 'node:fs/promises';
import {acodeModule} from './helpers/acode-modules.mjs';
const dom=new JSDOM('<!doctype html><html lang="en"><body class="editor-page no-animation"><div id="editor-shell"><div id="editor"></div></div></body></html>',{url:'https://editor.local/',pretendToBeVisual:true});
for(const name of ['Window','window','document','navigator','Node','Text','HTMLElement','Element','SVGElement','HTMLInputElement','HTMLTextAreaElement','HTMLSelectElement','HTMLButtonElement','CharacterData','DocumentType','MutationObserver','Event','KeyboardEvent','MouseEvent','CustomEvent','getComputedStyle','localStorage','screen','devicePixelRatio','innerWidth','innerHeight'])
 Object.defineProperty(globalThis,name,{configurable:true,value:typeof dom.window[name]==='function'&&name==='getComputedStyle'?dom.window[name].bind(dom.window):dom.window[name]});
globalThis.requestAnimationFrame=dom.window.requestAnimationFrame.bind(dom.window);globalThis.cancelAnimationFrame=dom.window.cancelAnimationFrame.bind(dom.window);
// Exercise the Android path. The production opt-out must prevent constructing
// the EditContext that clips long anchors to its small text buffer.
Object.defineProperty(navigator,'userAgent',{value:'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/133.0.0.0 Mobile Safari/537.36'});
window.EditContext=class {constructor(){throw new Error('Clipping EditContext must not be used');}};
window.Range.prototype.getClientRects=()=>[];window.Range.prototype.getBoundingClientRect=()=>({left:0,right:0,top:0,bottom:0,width:0,height:0});
window.matchMedia=globalThis.matchMedia=q=>({matches:q.includes('reduced-motion'),addEventListener(){},removeEventListener(){}});
const errors=[];window.addEventListener('error',event=>errors.push(String(event.error||event.message)));
const originalConsoleError=console.error;
console.error=(...args)=>{if(String(args[0]).includes('CodeMirror plugin crashed'))errors.push(args.map(String).join(' '));else originalConsoleError(...args);};
HTMLElement.prototype.scrollTo=function(options,y){this.scrollTop=typeof options==='object'?(options.top??this.scrollTop):(y??this.scrollTop);if(typeof options==='object'&&options.left!=null)this.scrollLeft=options.left;};
globalThis.ResizeObserver=class{observe(){}unobserve(){}disconnect(){}};
const calls={ready:0,source:[],nativeMenu:[],viewport:[],search:[],clipboard:'',keyboard:[],keyboardHeight:0,traces:[]};
window.NativeEditor={ready(){calls.ready++;},changed:v=>calls.source.push(v),cursorSelection:(...v)=>calls.viewport.push(v),searchState:v=>calls.search.push(JSON.parse(v)),
 diagnosticsEnabled:()=>false,trace:json=>calls.traces.push(JSON.parse(json)),
 setNativeContextMenuDisabled:v=>calls.nativeMenu.push(v),systemConfiguration:()=>JSON.stringify({keyboardHeight:calls.keyboardHeight,keyboardVisible:calls.keyboardHeight>0,keyboardRevision:7,hardKeyboardHidden:2}),keepKeyboardAfterCut:r=>calls.keyboard.push(r),copyToClipboard:v=>{calls.clipboard=v;},readClipboard:()=>calls.clipboard};
const cm=await acodeModule('tests/helpers/full-acode-runtime.js');
const api=window.EditorApi,view=cm.EditorView.findFromDOM(document.querySelector('.cm-editor'));
const tick=ms=>new Promise(r=>setTimeout(r,ms));
const load=source=>{api.setSource(source);view.dispatch({selection:{anchor:0}});};

test('complete original Acode graph boots with one footer and upstream DOM',()=>{
 assert.equal(api.getDiagnostics().diagnosticsEnabled,false);assert.deepEqual(calls.traces,[]);
 assert.equal(calls.ready,1);assert.equal(document.querySelectorAll('#quick-tools').length,1);
 assert.ok(document.querySelector('#quick-tools #row1 .section .icon.letters'));
 assert.equal(document.querySelectorAll('.np-quicktools-host,.np-cursor-menu,.np-search-panel').length,0);
 assert.ok(document.querySelector('#quicktools-toggler'));assert.equal(api.getDiagnostics().engine,'Acode DOM CodeMirror');
 assert.equal(document.querySelector('#quick-tools [data-id="save"]'),null);
 assert.equal(cm.EditorView.EDIT_CONTEXT,false);assert.equal(view.contentDOM.editContext,undefined);
 for(const row of [cm.quickTools.$row1,cm.quickTools.$row2])for(const section of row.querySelectorAll('.section'))assert.equal(section.children.length,8);
 assert.ok(document.querySelector('#quick-tools [data-id="tilde"]'));
});
test('the replacement toolbar slot types a tilde through the original action handler',()=>{
 load('');view.focus();document.querySelector('#quick-tools [data-id="tilde"]').click();assert.equal(api.getSource(),'~');
});
test('original Find selects the actual result; Next and Replace use the same CodeMirror query',()=>{
 load('alpha one alpha two');cm.actions('search');cm.quickTools.$searchInput.el.value='alpha';cm.quickTools.$searchInput.el.dispatchEvent(new Event('input'));
 assert.equal(view.state.sliceDoc(view.state.selection.main.from,view.state.selection.main.to),'alpha');
 cm.actions('search-next');assert.equal(view.state.selection.main.from,10);
 cm.quickTools.$replaceInput.el.value='B';cm.actions('search-replace');
 assert.equal(api.getSource(),'alpha one B two');assert.equal(cm.getSearchQuery(view.state).replace,'B');
 cm.actions('toggle');assert.equal(document.querySelector('#search_row1'),null);
});
test('original QuickTools routes Ctrl+C through capture and leaves selected source intact',()=>{
 load('selected tail');view.focus();view.dispatch({selection:{anchor:0,head:8}});
 cm.actions('ctrl');assert.equal(document.activeElement,cm.quickTools.$input);
 cm.quickTools.$input.value='c';cm.quickTools.$input.dispatchEvent(new Event('input',{bubbles:true}));
 assert.equal(calls.clipboard,'selected');assert.equal(api.getSource(),'selected tail');assert.equal(cm.key.ctrl,false);
});
test('selection menu is original Acode menu, native UI is suppressed and Cut is a state edit',async()=>{
 load('abcdef');view.focus();view.dispatch({selection:{anchor:1,head:4}});view.setSelection(true);view.setMenu(true);
 const oldCoords=view.coordsAtPos;view.coordsAtPos=()=>({left:40,right:40,top:100,bottom:120});
 for(let attempt=0;attempt<100&&!document.querySelector('.cursor-menu__primary');attempt++) await tick(25);
 assert.ok(document.querySelector('.cursor-menu__primary'));view.coordsAtPos=oldCoords;
 assert.ok(calls.nativeMenu.includes(true));document.querySelector('.cursor-menu__primary [aria-label="Cut"]').dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true,detail:0}));assert.equal(api.getSource(),'aef');
 assert.equal(calls.clipboard,'bcd');assert.equal(view.hasFocus,true);cm.undo(view);assert.equal(api.getSource(),'abcdef');
});
test('folded block movement is routed by the original command registry',()=>{
 const doc='before\nfunction f() {\n work();\n}\nafter';load(doc);
 const from=doc.indexOf('{')+1,to=doc.indexOf('}');view.dispatch({selection:{anchor:doc.indexOf('function')},effects:cm.foldEffect.of({from,to})});
 assert.equal(cm.executeCommand('movelinesdown',view),true);assert.equal(api.getSource(),'before\nafter\nfunction f() {\n work();\n}');
 let folds=0;cm.foldedRanges(view.state).between(0,view.state.doc.length,()=>folds++);assert.equal(folds,1);
});
test('document reload resets history; preferences never replace source or selection',()=>{
 load('old');view.dispatch({changes:{from:0,insert:'edit'}});api.setSource('new external');assert.equal(cm.undo(view),false);
 view.dispatch({selection:{anchor:5,head:1}});const before=view.state.selection.toJSON();api.setPreferences({language:'ru',fontSize:18,wordWrap:false});
 assert.deepEqual(view.state.selection.toJSON(),before);assert.equal(api.getSource(),'new external');assert.equal(document.documentElement.lang,'ru');
});
test('original command palette opens real hints and has no unavailable IDE commands',async()=>{
 cm.executeCommand('openCommandPalette',view);await tick(20);assert.ok(document.querySelector('#palette input'));assert.ok(document.querySelector('#hints'));
 const names=cm.getRegisteredCommands().map(c=>c.name);assert.ok(names.includes('foldAll'));assert.ok(names.includes('notebookInsertJS'));assert.ok(!names.includes('openTerminal'));
 api.handleBack();
});
test('viewport resize keeps the same view and selection without range recovery code',async()=>{
 load('abcdef');view.focus();view.dispatch({selection:{anchor:5,head:1}});const selected=view.state.selection.toJSON(),node=view.dom;
 for(let i=0;i<80;i++)window.dispatchEvent(new Event('resize'));await tick(180);
 assert.equal(view.dom,node);assert.deepEqual(view.state.selection.toJSON(),selected);
});
test('empty or denied native clipboard cannot delete an existing range',()=>{
 load('abcdef');view.dispatch({selection:{anchor:1,head:4}});calls.clipboard='';
 cm.executeCommand('paste',view);assert.equal(api.getSource(),'abcdef');
});
test('upstream gotoLine handles relative line and column navigation',()=>{
 load('first\nsecond\nthird');view.gotoLine('2:3');
 assert.equal(view.state.selection.main.head,8);view.gotoLine('+1');assert.equal(view.state.doc.lineAt(view.state.selection.main.head).number,3);
});
test('original regex search, replacement captures and invalid queries work through the footer',()=>{
 load('value12 value34');cm.settings.value.search={regExp:true,caseSensitive:false,wholeWord:false};
 cm.actions('search');cm.quickTools.$searchInput.el.value='value(\\d+)';cm.quickTools.$searchInput.el.dispatchEvent(new Event('input'));
 cm.quickTools.$replaceInput.el.value='[$1]';cm.actions('search-replace-all');assert.equal(api.getSource(),'[12] [34]');
 cm.quickTools.$searchInput.el.value='[';cm.quickTools.$searchInput.el.dispatchEvent(new Event('input'));
 assert.equal(cm.getSearchQuery(view.state).valid,false);assert.equal(api.getSource(),'[12] [34]');
 assert.equal(api.handleBack(),true);assert.equal(calls.search.at(-1).open,false);cm.settings.value.search.regExp=false;
});
test('long forward/reverse ranges survive scrolling, resize and original Copy outside the viewport',async()=>{
 const source=Array.from({length:2400},(_,i)=>`  row${i} = ${i};`).join('\n');load(source);view.focus();
 for(const [anchor,head] of [[17,source.length-25],[source.length-25,17]]){
  view.dispatch({selection:{anchor,head}});const before=view.state.selection.toJSON();
  for(const top of [600,12000,200,30000]){view.scrollDOM.scrollTop=top;view.scrollDOM.dispatchEvent(new Event('scroll'));}
  window.dispatchEvent(new Event('resize'));await tick(150);
  assert.deepEqual(view.state.selection.toJSON(),before);
  cm.executeCommand('copy',view);assert.equal(calls.clipboard,source.slice(17,source.length-25));
 }
});
test('select all, scroll, drag lower native handle retains the offscreen upper endpoint',async()=>{
 const source=Array.from({length:60},(_,i)=>`row${i} = value;`).join('\n');load(source);view.focus();
 cm.executeCommand('selectall',view);
 const top=view.state.doc.line(15).from,end=view.state.doc.line(20).to;
 const descriptor=Object.getOwnPropertyDescriptor(view,'viewport');
 Object.defineProperty(view,'viewport',{configurable:true,get:()=>({from:top,to:source.length})});
 try{
  // Android's selection-only notification after moving the lower handle:
  // the lower endpoint moves, but the unchanged offscreen endpoint is
  // reported as the start of the rendered part. Feed that through CM's
  // real DOM observer, not a direct model selection update.
  const a=view.domAtPos(top),b=view.domAtPos(end);
  assert.equal(view.posAtDOM(a.node,a.offset),top);assert.equal(view.posAtDOM(b.node,b.offset),end);
  // Device log 13:26:26.519: Chromium first moves ONLY the offscreen
  // anchor (0 -> 26714), while leaving the lower handle at 29703. The
  // actual head movement arrives in the following selectionchange.
  const last=view.domAtPos(source.length);
  document.getSelection().setBaseAndExtent(a.node,a.offset,last.node,last.offset);
  document.dispatchEvent(new Event('selectionchange'));await tick(30);
  assert.equal(view.state.selection.main.from,0,'anchor-only clipping must not commit');
  assert.equal(view.state.selection.main.to,source.length);
  assert.equal(view.posAtDOM(document.getSelection().anchorNode,document.getSelection().anchorOffset),0,'CodeMirror must resync the native anchor');
  document.getSelection().setBaseAndExtent(a.node,a.offset,b.node,b.offset);
  document.dispatchEvent(new Event('selectionchange'));await tick(30);
  assert.equal(view.state.selection.main.from,0);
  assert.equal(view.state.selection.main.to,end);
  cm.executeCommand('copy',view);assert.equal(calls.clipboard,source.slice(0,end));
  view.dispatch({selection:{anchor:source.length,head:0}});
  document.getSelection().setBaseAndExtent(b.node,b.offset,a.node,a.offset);document.dispatchEvent(new Event('selectionchange'));await tick(30);
  assert.equal(view.state.selection.main.anchor,end);assert.equal(view.state.selection.main.head,0);
  // Mirror: with only the upper handle visible, Chromium can clip the lower
  // endpoint first. Preserve it until the real upper endpoint moves.
  Object.defineProperty(view,'viewport',{configurable:true,get:()=>({from:0,to:end})});
  view.dispatch({selection:{anchor:source.length,head:0}});
  document.getSelection().setBaseAndExtent(b.node,b.offset,view.contentDOM.firstChild,0);
  document.dispatchEvent(new Event('selectionchange'));await tick(30);
  assert.equal(view.state.selection.main.anchor,source.length);assert.equal(view.state.selection.main.head,0);
  const near=view.domAtPos(view.state.doc.line(2).from);
  document.getSelection().setBaseAndExtent(b.node,b.offset,near.node,near.offset);
  document.dispatchEvent(new Event('selectionchange'));await tick(30);
  assert.equal(view.state.selection.main.anchor,source.length);assert.equal(view.state.selection.main.head,view.state.doc.line(2).from);
  Object.defineProperty(view,'viewport',{configurable:true,get:()=>({from:top,to:source.length})});
  // A fresh pointer selection really replaces the range; do not pin it.
  view.dispatch({selection:{anchor:0,head:source.length}});view.contentDOM.dispatchEvent(new MouseEvent('pointerdown',{bubbles:true}));
  document.getSelection().setBaseAndExtent(a.node,a.offset,b.node,b.offset);document.dispatchEvent(new Event('selectionchange'));await tick(30);
  assert.equal(view.state.selection.main.from,top);assert.equal(view.state.selection.main.to,end);
  view.contentDOM.dispatchEvent(new MouseEvent('pointerup',{bubbles:true}));
  document.getSelection().collapse(b.node,b.offset);document.dispatchEvent(new Event('selectionchange'));await tick(30);assert.equal(view.state.selection.main.empty,true);
  // A pan/cancel must not suppress the very next handle movement.
  view.dispatch({selection:{anchor:0,head:source.length}});view.scrollDOM.dispatchEvent(new Event('scroll'));
  document.getSelection().setBaseAndExtent(a.node,a.offset,b.node,b.offset);document.dispatchEvent(new Event('selectionchange'));await tick(30);
  assert.equal(view.state.selection.main.from,0);assert.equal(view.state.selection.main.to,end);
  // Overscan may keep the whole document rendered. The visible scrolling
  // window still has an offscreen endpoint and needs the same correction.
  const rect=view.scrollDOM.getBoundingClientRect,pos=view.posAtCoords;
  Object.defineProperty(view,'viewport',{configurable:true,get:()=>({from:0,to:source.length})});
  view.scrollDOM.getBoundingClientRect=()=>({left:0,right:400,top:0,bottom:300,width:400,height:300});view.posAtCoords=point=>point.y<150?top:source.length;
  try{
   view.dispatch({selection:{anchor:0,head:source.length}});
   document.getSelection().setBaseAndExtent(a.node,a.offset,b.node,b.offset);document.dispatchEvent(new Event('selectionchange'));await tick(30);
   assert.equal(view.state.selection.main.from,0);assert.equal(view.state.selection.main.to,end);
  }finally{view.scrollDOM.getBoundingClientRect=rect;view.posAtCoords=pos;}
 }finally{if(descriptor)Object.defineProperty(view,'viewport',descriptor);else delete view.viewport;}
});
test('native handle expansion to the full document preserves DOM direction instead of reversing the active end',async()=>{
 const source=Array.from({length:60},(_,i)=>`row${i} = value;`).join('\n');load(source);view.focus();
 const descriptor=Object.getOwnPropertyDescriptor(view,'viewport');
 Object.defineProperty(view,'viewport',{configurable:true,get:()=>({from:view.state.doc.line(15).from,to:source.length})});
 try{
  // Device 14:02:44.369: DOM 0 -> docLength, while CM's Chrome select-all
  // heuristic unconditionally produces docLength -> 0. The head then jumps
  // offscreen and the original cursor reveal scrolls the editor to the top.
  view.dispatch({selection:{anchor:0,head:source.length-1}});
  const start=view.domAtPos(0),end=view.domAtPos(source.length);
  document.getSelection().setBaseAndExtent(start.node,start.offset,end.node,end.offset);
  document.dispatchEvent(new Event('selectionchange'));await tick(30);
  assert.equal(view.state.selection.main.anchor,0,'full range must retain the stationary upper anchor');
  assert.equal(view.state.selection.main.head,source.length,'the lower handle remains the active end');
  assert.equal(view.posAtDOM(document.getSelection().focusNode,document.getSelection().focusOffset),source.length);
  // Reverse direction is also meaningful and must not be normalized forward.
  view.dispatch({selection:{anchor:source.length,head:1}});
  document.getSelection().setBaseAndExtent(end.node,end.offset,start.node,start.offset);
  document.dispatchEvent(new Event('selectionchange'));await tick(30);
  assert.equal(view.state.selection.main.anchor,source.length);assert.equal(view.state.selection.main.head,0);
 }finally{if(descriptor)Object.defineProperty(view,'viewport',descriptor);else delete view.viewport;}
});
test('original indentation guides decorate whitespace without changing text or range',()=>{
 load('<notebook>\n  <script type="module">\n    const value = 1;\n  </script>\n</notebook>');
 view.dispatch({selection:{anchor:5,head:view.state.doc.length-4}});const before=view.state.selection.toJSON(),source=api.getSource();
 assert.ok(document.querySelector('.cm-indent-guides,.cm-indent-guides-line'));
 assert.deepEqual(view.state.selection.toJSON(),before);assert.equal(api.getSource(),source);assert.equal(view.state.tabSize,2);
});
test('fold placeholders expose hidden-line count and unfold through original CodeMirror effects',()=>{
 const source='<notebook>\n  <script type="module">\n    const x = 1;\n    display(x);\n  </script>\n</notebook>';load(source);
 const from=source.indexOf('>\n',source.indexOf('<script'))+1,to=source.indexOf('  </script>');
 view.dispatch({effects:cm.foldEffect.of({from,to})});
 const placeholder=document.querySelector('.cm-foldPlaceholder');assert.ok(placeholder);assert.match(placeholder.textContent,/… \d+/);assert.equal(placeholder.getAttribute('role'),'button');
 placeholder.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true}));let count=0;cm.foldedRanges(view.state).between(0,source.length,()=>count++);assert.equal(count,0);assert.equal(api.getSource(),source);
});
test('search distinguishes current result while retaining every other visible match',()=>{
 load('alpha alpha alpha');cm.actions('search');cm.quickTools.$searchInput.el.value='alpha';cm.quickTools.$searchInput.el.dispatchEvent(new Event('input'));cm.actions('search-next');
 const ranges=[];cm.buildSearchMatchDecorations(view.state,[{from:0,to:view.state.doc.length}]).between(0,view.state.doc.length,(from,to,deco)=>ranges.push({from,to,selected:deco.spec.class.includes('cm-searchMatch-selected')}));
 assert.equal(ranges.length,3);assert.deepEqual(ranges.filter(r=>r.selected).map(r=>r.from),[6]);
 view.dispatch({selection:{anchor:1,head:14}});assert.deepEqual([view.state.selection.main.anchor,view.state.selection.main.head],[1,14]);
 const after=[];cm.buildSearchMatchDecorations(view.state,[{from:0,to:view.state.doc.length}]).between(0,view.state.doc.length,()=>after.push(1));assert.equal(after.length,3);cm.actions('toggle');
});
test('Cut requests one native IME continuation only when previously open; Copy and delete do not',()=>{
 load('abcdef');view.focus();calls.keyboard.length=0;calls.keyboardHeight=280;view.dispatch({selection:{anchor:1,head:4}});
 cm.executeCommand('copy',view);assert.deepEqual(calls.keyboard,[]);
 cm.executeCommand('cut',view);assert.deepEqual(calls.keyboard,[7]);assert.equal(api.getSource(),'aef');cm.undo(view);assert.equal(api.getSource(),'abcdef');
 calls.keyboard.length=0;calls.keyboardHeight=0;view.dispatch({selection:{anchor:1,head:4}});cm.executeCommand('cut',view);assert.deepEqual(calls.keyboard,[]);
 load('abcdef');view.focus();calls.keyboardHeight=40;view.dispatch({selection:{anchor:1,head:4}});cm.executeCommand('cut',view);assert.deepEqual(calls.keyboard,[7]);
 calls.keyboard.length=0;view.dispatch({changes:{from:0,to:1},userEvent:'delete.selection'});assert.deepEqual(calls.keyboard,[]);calls.keyboardHeight=0;
});
test('autocomplete offers real notebook symbols and portals out of the clipped editor',async()=>{
 api.setPreferences({autocomplete:true,language:'en'});load('<notebook>\n<script type="module">\n\n</script>\n</notebook>');view.focus();const at=api.getSource().indexOf('\n\n')+1;view.dispatch({selection:{anchor:at}});
 view.dispatch({changes:{from:at,insert:'dis'},selection:{anchor:at+3},userEvent:'input.type'});await tick(380);
 assert.ok(cm.currentCompletions(view.state).some(c=>c.label==='display'));
 const popup=document.querySelector('.cm-tooltip-autocomplete');assert.ok(popup);assert.ok(popup.querySelector('[role="option"]'));assert.equal(document.querySelector('#editor').contains(popup),false);
 // CodeMirror's default interactionDelay deliberately rejects acceptance
 // during the first 75ms after the popup appears.
 await tick(100);
 assert.equal(cm.acceptCompletion(view),true);assert.match(api.getSource(),/display/);
 const oldRect=view.scrollDOM.getBoundingClientRect;view.scrollDOM.getBoundingClientRect=()=>({left:0,right:412,top:0,bottom:300});
 assert.deepEqual(cm.completionSpace(view),{left:4,right:408,top:4,bottom:296});view.scrollDOM.getBoundingClientRect=oldRect;
});
test('overlay limits use the measured shell and visual viewport through hidden, IME and rotation sizes',()=>{
 const shell=document.getElementById('editor-shell'),rect=shell.getBoundingClientRect;
 const visual=Object.getOwnPropertyDescriptor(window,'visualViewport');
 const originalWidth=Object.getOwnPropertyDescriptor(window,'innerWidth'),originalHeight=Object.getOwnPropertyDescriptor(window,'innerHeight');
 const source=api.getSource(),selection=view.state.selection.toJSON();
 try{
  shell.getBoundingClientRect=()=>({left:0,top:0,right:400,bottom:642,width:400,height:642});
  Object.defineProperty(window,'visualViewport',{configurable:true,value:{offsetLeft:0,offsetTop:0,width:400,height:413.33}});
  cm.updateOverlayViewport();
  assert.equal(document.documentElement.style.getPropertyValue('--editor-ui-height'),'413.33px');
  assert.equal(document.documentElement.style.getPropertyValue('--editor-ui-width'),'400px');
  shell.getBoundingClientRect=()=>({left:0,top:0,right:800,bottom:280,width:800,height:280});
  Object.defineProperty(window,'visualViewport',{configurable:true,value:{offsetLeft:0,offsetTop:0,width:800,height:280}});
  window.dispatchEvent(new Event('resize'));
  assert.equal(document.documentElement.style.getPropertyValue('--editor-ui-height'),'280px');
  assert.equal(document.documentElement.style.getPropertyValue('--editor-ui-width'),'800px');
  shell.getBoundingClientRect=()=>({left:0,top:0,right:0,bottom:0,width:0,height:0});
  Object.defineProperty(window,'visualViewport',{configurable:true,value:{width:0,height:0}});
  Object.defineProperty(window,'innerWidth',{configurable:true,value:0});Object.defineProperty(window,'innerHeight',{configurable:true,value:0});
  cm.updateOverlayViewport();
  assert.equal(document.documentElement.style.getPropertyValue('--editor-ui-height'),'280px');
  assert.equal(api.getSource(),source);assert.deepEqual(view.state.selection.toJSON(),selection);
 }finally{
  shell.getBoundingClientRect=rect;if(visual)Object.defineProperty(window,'visualViewport',visual);else delete window.visualViewport;
  Object.defineProperty(window,'innerWidth',originalWidth);Object.defineProperty(window,'innerHeight',originalHeight);
  cm.updateOverlayViewport();
 }
});
test('Acode colour dialog has format/alpha controls; Cancel preserves and OK replaces only the colour',async()=>{
 const prototype=dom.window.HTMLDialogElement.prototype,oldShow=prototype.showModal,oldClose=prototype.close;
 let modalCalls=0;
 prototype.showModal=function(){assert.equal(this.isConnected,true);assert.ok(this.querySelector('.picker_wrapper'));this.setAttribute('open','');modalCalls++;};prototype.close=function(){this.removeAttribute('open');};
 try{
 load('const color = "#123456";');view.focus();view.dispatch({selection:{anchor:15,head:22}});const source=api.getSource();
 globalThis.acode.exec('insert-color');await tick(25);
 const dialog=document.querySelector('.prompt.box');assert.ok(dialog);assert.equal(dialog.tagName,'DIALOG');assert.ok(dialog.hasAttribute('open'));assert.ok(dialog.querySelector('.picker_wrapper'));assert.ok(dialog.querySelector('.picker_alpha'));assert.ok(dialog.querySelector('.format-toggle'));
 assert.equal(api.getSource(),source);assert.equal(api.handleBack(),true);await tick(330);assert.equal(document.querySelector('.prompt.box'),null);assert.equal(api.getSource(),source);
 globalThis.acode.exec('insert-color');await tick(25);const next=document.querySelector('.prompt.box');
 const input=next.querySelector('.picker_editor input');input.value='#abcdef';input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));
 next.querySelector('.button-container').lastElementChild.click();await tick(330);assert.equal(api.getSource(),'const color = "#abcdef";');cm.undo(view);assert.equal(api.getSource(),source);
 assert.equal(modalCalls,2);
 }finally{if(oldShow)prototype.showModal=oldShow;else delete prototype.showModal;if(oldClose)prototype.close=oldClose;else delete prototype.close;}
});
test('colour opens through native-style pointer activation of the actual context menu with a corrupt saved colour',async()=>{
 load('const value = 1;');view.focus();view.dispatch({selection:{anchor:0,head:5}});localStorage.__picker_last_picked='null';
 const oldCoords=view.coordsAtPos;view.coordsAtPos=()=>({left:40,right:40,top:100,bottom:120});view.setSelection(true);view.setMenu(true);await tick(250);
 const tap=button=>{
  assert.ok(button);
  for(const type of ['pointerdown','pointerup']){
   const event=new MouseEvent(type,{bubbles:true,cancelable:true,clientX:20,clientY:20});Object.defineProperties(event,{pointerId:{value:1},pointerType:{value:'touch'},isPrimary:{value:true}});button.dispatchEvent(event);
  }
  button.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true,detail:1}));
 };
 try{
  tap(document.querySelector('.cursor-menu__more'));
  tap(document.querySelector('.cursor-menu__overflow .color_lenspalette')?.closest('button'));await tick(30);
  assert.equal(document.querySelectorAll('.acode-colour-dialog[open]').length,1);
  assert.equal(document.querySelector('.editor-ui-error'),null);assert.equal(api.getSource(),'const value = 1;');
  assert.equal(api.handleBack(),true);await tick(330);
 }finally{view.coordsAtPos=oldCoords;delete localStorage.__picker_last_picked;}
});
test('colour mount failure is visible and logged, with no lost text or orphan dialog',async()=>{
 load('unchanged');view.focus();const prototype=dom.window.HTMLDialogElement.prototype,oldShow=prototype.showModal,log=console.error,messages=[];
 prototype.showModal=function(){throw new TypeError('simulated mount failure');};console.error=(...args)=>messages.push(args[0]);
 try{
  globalThis.acode.exec('insert-color');await tick(30);
  assert.equal(document.querySelector('.acode-colour-dialog'),null);assert.equal(document.querySelector('.mask'),null);
  assert.ok(document.querySelector('.editor-ui-error[role="alert"]'));assert.ok(messages.includes('[NotebookEditor] Colour dialog failed'));assert.equal(api.getSource(),'unchanged');
  document.querySelector('.editor-ui-error button').click();
 }finally{console.error=log;if(oldShow)prototype.showModal=oldShow;else delete prototype.showModal;}
});
test('original scrollbar drag changes scroll without changing a long selection',async()=>{
 load(Array.from({length:1500},(_,i)=>`row${i}`).join('\n'));view.focus();view.dispatch({selection:{anchor:17,head:view.state.doc.length-10}});const selection=view.state.selection.toJSON();
 const scroll=view.scrollDOM;
 // jsdom reports 0 for both viewport axes. Since the parked-WebView fix,
 // 0x0 is intentionally treated as unknown layout, not as overflow. Simulate
 // a *visible* 400x300 editor so the integration test exercises real drag logic.
 const geometry=[['scrollHeight',5000],['clientHeight',300],['scrollWidth',400],['clientWidth',400]];
 const original=geometry.map(([key])=>[key,Object.getOwnPropertyDescriptor(scroll,key)]);
 try{
  for(const [key,value] of geometry)Object.defineProperty(scroll,key,{configurable:true,value});
  api.refresh();await tick(80);
  const bar=document.querySelector('.scrollbar-container.right');assert.ok(bar,'visible editor with vertical overflow must show scrollbar');
  bar.getBoundingClientRect=()=>({left:365,right:412,top:10,bottom:310,width:47,height:300});bar.resize();
  bar.querySelector('.thumb').dispatchEvent(new MouseEvent('mousedown',{bubbles:true,cancelable:true,clientY:20}));
  document.dispatchEvent(new MouseEvent('mousemove',{bubbles:true,cancelable:true,clientY:120}));
  assert.ok(scroll.scrollTop>1000);assert.deepEqual(view.state.selection.toJSON(),selection);
 }finally{
  document.dispatchEvent(new MouseEvent('mouseup',{bubbles:true,cancelable:true}));
  for(const [key,desc] of original){if(desc)Object.defineProperty(scroll,key,desc);else delete scroll[key];}
 }
});
test('compiled host styles preserve scrollbar width, theme the picker and distinguish active search',()=>{
 const css=['../src/acode/host/skin.scss','../src/acode/vendor/components/scrollbar/style.scss'].map(path=>compile(fileURLToPath(new URL(path,import.meta.url)),{logger:{warn(){},debug(){}}}).css).join('\n');
 const fixture=new JSDOM('<html data-theme="dark"><head><style>'+css+'</style></head><body><div id="editor-shell"><div id="editor"><div class="scrollbar-container right" style="width:20px"><div class="container"><span class="thumb"></span></div></div><span class="cm-searchMatch"></span><span class="cm-searchMatch cm-searchMatch-selected"></span></div></div></body></html>',{url:'https://editor.local/'});
 const style=el=>fixture.window.getComputedStyle(el),doc=fixture.window.document;
 const barStyle=style(doc.querySelector('.scrollbar-container'));assert.notEqual(barStyle.boxSizing,'border-box');assert.equal(barStyle.width,'20px');assert.equal(barStyle.paddingRight,'17px');
 assert.equal(style(doc.documentElement).getPropertyValue('--popup-background-color').trim(),'#282c34');
 const [normal,current]=doc.querySelectorAll('.cm-searchMatch');assert.notEqual(style(normal).outline,style(current).outline);fixture.window.close();
 const popupFixture=new JSDOM('<html><head><style>'+css+'</style></head><body class="editor-page"><div class="cm-tooltip cm-tooltip-autocomplete" style="height:80px"><ul><li>display</li></ul></div><dialog class="prompt acode-colour-dialog" open><div class="message"></div><div class="button-container"></div></dialog></body></html>',{url:'https://editor.local/'});
 const doc2=popupFixture.window.document,style2=el=>popupFixture.window.getComputedStyle(el),popup=doc2.querySelector('.cm-tooltip');
 assert.equal(style2(popup).display,'flex');assert.equal(style2(popup).flexDirection,'column');
 assert.equal(style2(popup.querySelector('ul')).minHeight,'0');assert.equal(style2(popup.querySelector('ul')).overflow,'auto');
 assert.match(style2(popup).maxHeight,/--editor-ui-height/);assert.doesNotMatch(style2(popup).maxHeight,/vh/);
 const modal=doc2.querySelector('dialog');assert.match(style2(modal).maxHeight,/--editor-ui-height/);assert.doesNotMatch(style2(modal).maxHeight,/vh/);
 assert.equal(style2(modal.querySelector('.message')).minHeight,'0');assert.equal(style2(modal.querySelector('.button-container')).flexShrink,'0');
 popupFixture.window.close();
});
test('diagnostic scalar fields are preserved by the centralized Android whitelist; content is rejected',async()=>{
 const log=await readFile(new URL('../../app/src/main/java/dev/pocket/notebook/core/logging/NotebookLog.kt',import.meta.url),'utf8');
 for(const key of [...cm.diagnosticNumbers,...cm.diagnosticBooleans,...cm.diagnosticLabels])assert.ok(log.includes('"'+key+'"'),'Android would drop '+key);
 assert.deepEqual(cm.sanitizeDiagnostic('selection-native',{anchor:1,head:4,text:'SECRET',source:'SECRET',query:'SECRET',clipboard:'SECRET',html:'SECRET',data:'SECRET',height:NaN,reason:'bad label with spaces'}),{event:'selection-native',anchor:1,head:4});
 assert.equal(cm.sanitizeDiagnostic('invalid event SECRET',{}),null);
});
test('opt-in diagnostics capture actual DOM/geometry events without source and stop completely when disabled',async()=>{
 const secret='PRIVATE_NOTEBOOK_PAYLOAD';calls.traces.length=0;
 api.setDiagnosticsEnabled(true);assert.ok(calls.traces.some(e=>e.event==='diagnostics-installed'));
 load('<notebook>\n<script type="module">\ndis\n</script>\n'+secret+'\n</notebook>');view.focus();view.dispatch({selection:{anchor:api.getSource().indexOf('dis\n')+3}});
 document.dispatchEvent(new Event('selectionchange'));cm.startCompletion(view);await tick(220);
 view.contentDOM.dispatchEvent(new dom.window.InputEvent('beforeinput',{bubbles:true,inputType:'insertText',data:secret}));
 assert.ok(calls.traces.some(e=>e.event==='selection-native'&&Number.isFinite(e.nativeAnchor)));
 assert.ok(calls.traces.some(e=>e.event==='selection-filter'&&typeof e.reason==='string'));
 assert.ok(calls.traces.some(e=>e.event==='completion-box'&&e.target==='list'&&typeof e.height==='number'&&e.optionCount>0));
 assert.ok(calls.traces.some(e=>e.event==='input-before'&&e.inputType==='insertText'));
 assert.ok(!JSON.stringify(calls.traces).includes(secret));
 const before=api.getSource();api.setDiagnosticsEnabled(false);const count=calls.traces.length;
 document.dispatchEvent(new Event('selectionchange'));window.dispatchEvent(new Event('resize'));await tick(360);
 assert.equal(calls.traces.length,count);assert.equal(api.getSource(),before);
});
test('automatic reveal preserves a nonempty range and scroll; caret and explicit goto/find still work',async()=>{
 load(Array.from({length:60},(_,i)=>`row${i} alpha`).join('\n'));view.focus();
 const coords=view.coordsAtPos,height=Object.getOwnPropertyDescriptor(view.scrollDOM,'clientHeight');
 view.coordsAtPos=pos=>({top:pos===3?-1000:1000,bottom:pos===3?-980:1020});
 Object.defineProperty(view.scrollDOM,'clientHeight',{configurable:true,value:300});
 try{
 view.dispatch({selection:{anchor:3,head:view.state.doc.length-4}});
 const range=view.state.selection.toJSON();let callsToUpstream=0;
 const upstream=()=>{callsToUpstream++;view.scrollDOM.scrollTop=900;return 'upstream-result';};
 view.scrollDOM.scrollTop=123;
 assert.equal(cm.runAutomaticCursorReveal(view,{},upstream),undefined);
 assert.equal(callsToUpstream,0);assert.equal(view.scrollDOM.scrollTop,123);assert.deepEqual(view.state.selection.toJSON(),range);
 api.setDiagnosticsEnabled(true);api.setDiagnosticsOptions({selectionMode:'correct',revealMode:'legacy'});
 assert.equal(cm.runAutomaticCursorReveal(view,{},upstream),'upstream-result');assert.equal(callsToUpstream,1);
 api.setDiagnosticsEnabled(false);
 assert.deepEqual(cm.editorDiagnosticLog.options,{selectionMode:'correct',revealMode:'caret'});
 view.dispatch({selection:{anchor:5}});
 assert.equal(cm.runAutomaticCursorReveal(view,{},upstream),'upstream-result');assert.equal(callsToUpstream,2);
 view.gotoLine('3:2');assert.equal(view.state.doc.lineAt(view.state.selection.main.head).number,3);
 cm.actions('search');cm.quickTools.$searchInput.el.value='alpha';cm.quickTools.$searchInput.el.dispatchEvent(new Event('input'));
 const first=view.state.selection.main.from;cm.actions('search-next');assert.ok(view.state.selection.main.from>first);cm.actions('toggle');
 }finally{api.setDiagnosticsEnabled(false);view.coordsAtPos=coords;if(height)Object.defineProperty(view.scrollDOM,'clientHeight',height);else delete view.scrollDOM.clientHeight;}
});
test('pipeline observes real CM DOM writes in bounded batches and restores hooks completely on Off',async()=>{
 load(Array.from({length:60},(_,i)=>`row${i} = value;`).join('\n'));view.focus();
 const proto=Object.getPrototypeOf(document.getSelection()),collapse=proto.collapse,extend=proto.extend;
 const focus=window.HTMLElement.prototype.focus,blur=window.HTMLElement.prototype.blur;
 const scrollTo=view.scrollDOM.scrollTo,requestMeasure=view.requestMeasure,measure=view.measure,update=view.update;
 const oldBatch=window.NativeEditor.traceBatch,batches=[];
 window.NativeEditor.traceBatch=json=>{const batch=JSON.parse(json);batches.push(batch);calls.traces.push(...batch);};
 calls.traces.length=0;
 const descriptor=Object.getOwnPropertyDescriptor(view,'viewport');
 Object.defineProperty(view,'viewport',{configurable:true,get:()=>({from:view.state.doc.line(15).from,to:view.state.doc.length})});
 try{
  api.setDiagnosticsEnabled(true);
  assert.notEqual(proto.collapse,collapse);assert.notEqual(view.requestMeasure,requestMeasure);
  view.dispatch({selection:{anchor:0,head:view.state.doc.length}});
  const start=view.domAtPos(view.state.doc.line(15).from),end=view.domAtPos(view.state.doc.line(20).to);
  document.getSelection().setBaseAndExtent(start.node,start.offset,end.node,end.offset);
  document.dispatchEvent(new Event('selectionchange'));await tick(40);
  assert.equal(view.state.selection.main.from,0);
  cm.flushPipeline();
  const writes=calls.traces.filter(e=>e.event==='selection-dom-write');
  assert.ok(writes.some(e=>e.operation==='collapse'&&e.actor==='cm-selection-sync'));
  assert.ok(writes.some(e=>e.operation==='extend'&&e.nativeHead===view.state.doc.line(20).to));
  assert.ok(calls.traces.some(e=>e.event==='selection-filter'&&e.corrected));
  assert.ok(writes.every(e=>typeof e.mono==='number'&&typeof e.durationMs==='number'&&typeof e.beforeNativeAnchor==='number'));
  assert.ok(batches.length>0&&batches.every(batch=>batch.length<=32));
  const source=api.getSource(),selection=view.state.selection.toJSON();
  view.scrollDOM.scrollTo({top:321});cm.flushPipeline();
  assert.ok(calls.traces.some(e=>e.event==='scroll-js-call'&&e.requestedY===321));
  assert.equal(api.getSource(),source);assert.deepEqual(view.state.selection.toJSON(),selection);
  // Invalid DOM operation must keep the native exception; the hook cannot
  // turn a failure into success or insert source/text into its event fields.
  assert.throws(()=>document.getSelection().extend(document.body,999999));
  cm.recordPipeline('pipeline-summary',{source:'PRIVATE_SECRET',html:'PRIVATE_SECRET',dropped:0});cm.flushPipeline();
  assert.ok(!JSON.stringify(batches).includes('PRIVATE_SECRET'));
  api.setDiagnosticsEnabled(false);
  assert.equal(proto.collapse,collapse);assert.equal(proto.extend,extend);assert.equal(view.scrollDOM.scrollTo,scrollTo);
  assert.equal(window.HTMLElement.prototype.focus,focus);assert.equal(window.HTMLElement.prototype.blur,blur);
  assert.equal(view.requestMeasure,requestMeasure);assert.equal(view.measure,measure);assert.equal(view.update,update);
  const count=calls.traces.length;await tick(200);assert.equal(calls.traces.length,count);
 }finally{
  api.setDiagnosticsEnabled(false);if(oldBatch)window.NativeEditor.traceBatch=oldBatch;else delete window.NativeEditor.traceBatch;
  if(descriptor)Object.defineProperty(view,'viewport',descriptor);else delete view.viewport;
 }
});
test('observe mode bypasses only the correction for an A/B gesture experiment and Off restores it',async()=>{
 load(Array.from({length:60},(_,i)=>`row${i} = value;`).join('\n'));view.focus();
 const descriptor=Object.getOwnPropertyDescriptor(view,'viewport'),top=view.state.doc.line(15).from,end=view.state.doc.line(20).to;
 Object.defineProperty(view,'viewport',{configurable:true,get:()=>({from:top,to:view.state.doc.length})});
 const notify=async()=>{const a=view.domAtPos(top),b=view.domAtPos(end);document.getSelection().setBaseAndExtent(a.node,a.offset,b.node,b.offset);document.dispatchEvent(new Event('selectionchange'));await tick(30);};
 try{
  api.setDiagnosticsEnabled(true);api.setDiagnosticsOptions({selectionMode:'observe',revealMode:'caret'});
  view.dispatch({selection:{anchor:0,head:view.state.doc.length}});await notify();
  assert.equal(view.state.selection.main.from,top);assert.equal(view.state.selection.main.to,end);
  cm.flushPipeline();assert.ok(calls.traces.some(e=>e.event==='selection-filter'&&e.reason==='correction-bypassed'));
  api.setDiagnosticsEnabled(false);view.dispatch({selection:{anchor:0,head:view.state.doc.length}});await notify();
  assert.equal(view.state.selection.main.from,0);assert.equal(view.state.selection.main.to,end);
 }finally{api.setDiagnosticsEnabled(false);if(descriptor)Object.defineProperty(view,'viewport',descriptor);else delete view.viewport;}
});
test('the original Acode pointer reveal is connected to the host range guard and legacy A/B mode',async()=>{
 // Earlier scrollbar gestures intentionally suppress automatic reveal for 1200ms.
 // Isolate this pointer test from that still-active interaction window.
 await tick(1250);
 load('first\nsecond\nthird');view.focus();view.scrollDOM.scrollTop=100;
 const coords=view.coordsAtPos,rect=view.scrollDOM.getBoundingClientRect,scrollTo=view.scrollDOM.scrollTo;
 const height=Object.getOwnPropertyDescriptor(view.scrollDOM,'clientHeight');let scrollCalls=0;
 view.coordsAtPos=pos=>({left:10,right:11,top:pos===0?-100:1000,bottom:pos===0?-80:1020});
 view.scrollDOM.getBoundingClientRect=()=>({left:0,right:400,top:0,bottom:300,width:400,height:300});
 Object.defineProperty(view.scrollDOM,'clientHeight',{configurable:true,value:300});
 view.scrollDOM.scrollTo=function(options){scrollCalls++;this.scrollTop=options.top;};
 try{
  api.setDiagnosticsEnabled(true);api.setDiagnosticsOptions({revealMode:'caret'});calls.traces.length=0;
  view.dispatch({selection:{anchor:view.state.doc.length,head:0},userEvent:'select.pointer'});await tick(70);cm.flushPipeline();
  assert.equal(scrollCalls,0);assert.equal(view.scrollDOM.scrollTop,100);
  assert.ok(calls.traces.some(e=>e.event==='scroll-auto-decision'&&e.allowed===false&&e.reason==='large-range'));
  api.setDiagnosticsOptions({revealMode:'legacy'});
  view.dispatch({selection:{anchor:view.state.doc.length,head:0},userEvent:'select.pointer'});await tick(70);cm.flushPipeline();
  assert.ok(scrollCalls>0);assert.ok(calls.traces.some(e=>e.event==='scroll-js-call'&&e.actor==='acode-auto'));
 }finally{
  api.setDiagnosticsEnabled(false);view.coordsAtPos=coords;view.scrollDOM.getBoundingClientRect=rect;view.scrollDOM.scrollTo=scrollTo;
  if(height)Object.defineProperty(view.scrollDOM,'clientHeight',height);else delete view.scrollDOM.clientHeight;
 }
});
test('measurement diagnostics preserve CodeMirror request identity and coalescing keys',()=>{
 const original=view.requestMeasure,received=[];
 const sink=function(request){received.push(request);};view.requestMeasure=sink;
 try{
  api.setDiagnosticsEnabled(true);
  const key={},request={key,read:()=>17,write:()=>{}};
  view.requestMeasure(request);view.requestMeasure(request);
  assert.equal(received.at(-1),request);assert.equal(received.at(-2),request);assert.equal(request.key,key);
  api.setDiagnosticsEnabled(false);assert.equal(view.requestMeasure,sink);
 }finally{api.setDiagnosticsEnabled(false);view.requestMeasure=original;}
});
test('short selections keep automatic reveal above the keyboard while tall ranges stay in place',()=>{
 load('short selected text');view.focus();view.dispatch({selection:{anchor:1,head:4}});
 const coords=view.coordsAtPos,height=Object.getOwnPropertyDescriptor(view.scrollDOM,'clientHeight');let upstream=0;
 Object.defineProperty(view.scrollDOM,'clientHeight',{configurable:true,value:300});
 try{
  view.coordsAtPos=pos=>({left:10,right:11,top:pos===1?400:420,bottom:pos===1?420:440});
  cm.runAutomaticCursorReveal(view,{},()=>upstream++);assert.equal(upstream,1);
  view.coordsAtPos=pos=>({left:10,right:11,top:pos===1?-1000:420,bottom:pos===1?-980:440});
  cm.runAutomaticCursorReveal(view,{},()=>upstream++);assert.equal(upstream,1);
 }finally{view.coordsAtPos=coords;if(height)Object.defineProperty(view.scrollDOM,'clientHeight',height);else delete view.scrollDOM.clientHeight;}
});
test('missing endpoint geometry keeps short reveal, including inside a tall wrapped line',()=>{
 load('short selected text');view.focus();
 const coords=view.coordsAtPos,block=view.lineBlockAt,height=Object.getOwnPropertyDescriptor(view.scrollDOM,'clientHeight');
 Object.defineProperty(view.scrollDOM,'clientHeight',{configurable:true,value:300});
 let upstream=0;
 try{
  // A whole wrapped line may be very tall even for a three-character range.
  // Its total block height must not be mistaken for the selected height.
  view.lineBlockAt=()=>({top:0,bottom:10000});
  for(const [anchor,head] of [[1,4],[4,1]]){
   view.dispatch({selection:{anchor,head}});
   const selected=view.state.selection.toJSON();
   for(const missing of [1,4,'both']){
    view.coordsAtPos=pos=>missing==='both'||pos===missing?null:{top:400,bottom:420};
    assert.equal(cm.runAutomaticCursorReveal(view,{},()=>{upstream++;return 'revealed';}),'revealed');
    assert.deepEqual(view.state.selection.toJSON(),selected);
   }
  }
  view.coordsAtPos=()=>{throw new Error('geometry not available during update');};
  view.lineBlockAt=()=>{throw new Error('block map unavailable');};
  assert.equal(cm.runAutomaticCursorReveal(view,{},()=>{upstream++;return 'revealed';}),'revealed');
  assert.equal(upstream,7);assert.equal(api.getSource(),'short selected text');
 }finally{view.coordsAtPos=coords;view.lineBlockAt=block;if(height)Object.defineProperty(view.scrollDOM,'clientHeight',height);else delete view.scrollDOM.clientHeight;}
});
test('unrendered endpoint still blocks reveal for a multiline range with a large block gap',()=>{
 load(Array.from({length:60},(_,i)=>`row${i}`).join('\n'));view.focus();
 const coords=view.coordsAtPos,block=view.lineBlockAt,height=Object.getOwnPropertyDescriptor(view.scrollDOM,'clientHeight');
 Object.defineProperty(view.scrollDOM,'clientHeight',{configurable:true,value:300});
 let upstream=0;
 try{
  view.dispatch({selection:{anchor:0,head:view.state.doc.length}});
  view.coordsAtPos=pos=>pos===0?null:{top:500,bottom:520};
  view.lineBlockAt=pos=>pos===0?{top:0,bottom:20}:{top:1000,bottom:1020};
  api.setDiagnosticsEnabled(true);calls.traces.length=0;
  cm.runAutomaticCursorReveal(view,{},()=>upstream++);cm.flushPipeline();
  assert.equal(upstream,0);
  assert.ok(calls.traces.some(e=>e.event==='scroll-auto-decision'&&e.reason==='large-block-gap'&&e.blockGap===980&&!e.allowed));
  // A missing/zero viewport must also defer to upstream instead of silently
  // treating every nonempty range as too tall during a visibility transition.
  Object.defineProperty(view.scrollDOM,'clientHeight',{configurable:true,value:0});
  cm.runAutomaticCursorReveal(view,{},()=>upstream++);assert.equal(upstream,1);
 }finally{api.setDiagnosticsEnabled(false);view.coordsAtPos=coords;view.lineBlockAt=block;if(height)Object.defineProperty(view.scrollDOM,'clientHeight',height);else delete view.scrollDOM.clientHeight;}
});
test('scrollbar diagnostics cover upstream scroll sync and API refresh without changing measure requests',async()=>{
 load(Array.from({length:60},(_,i)=>`row${i}`).join('\n'));await tick(150);
 const original=view.requestMeasure,requests=[];
 view.requestMeasure=function(request){if(request)requests.push(request);};
 try{
  api.setDiagnosticsEnabled(true);calls.traces.length=0;
  view.scrollDOM.dispatchEvent(new Event('scroll'));await tick(40);
  assert.ok(requests.length>0,'upstream syncScrollUi must issue its measure request');
  const scrollRequest=requests.at(-1);
  scrollRequest.write(scrollRequest.read(view),view);cm.flushPipeline();
  assert.equal(calls.traces.filter(e=>e.event==='pipeline-scrollbar-read').length,1);
  assert.equal(calls.traces.filter(e=>e.event==='pipeline-scrollbar-write').length,1);
  api.refresh();const apiRequest=requests.at(-1);
  assert.notEqual(apiRequest,scrollRequest);
  apiRequest.write(apiRequest.read(view),view);
  view.measure(false);cm.flushPipeline();
  assert.equal(calls.traces.filter(e=>e.event==='pipeline-scrollbar-read').length,2);
  assert.equal(calls.traces.filter(e=>e.event==='pipeline-scrollbar-write').length,2);
  assert.ok(calls.traces.some(e=>e.event==='pipeline-cm-measure'&&typeof e.durationMs==='number'));
 }finally{api.setDiagnosticsEnabled(false);view.requestMeasure=original;}
});
test('pipeline overflow is bounded, reported, and never sends unapproved fields',()=>{
 const original=window.NativeEditor.traceBatch,batches=[];
 window.NativeEditor.traceBatch=json=>batches.push(JSON.parse(json));
 try{
  api.setDiagnosticsEnabled(true);
  for(let i=0;i<500;i++)cm.recordPipeline('pipeline-summary',{count:i,source:'PRIVATE_SECRET'});
  cm.flushPipeline();
  const events=batches.flat();
  assert.ok(events.length<=257);assert.ok(batches.every(batch=>batch.length<=32));
  assert.ok(events.some(e=>e.event==='diagnostics-pipeline-dropped'&&e.dropped>=244));
  assert.ok(!JSON.stringify(events).includes('PRIVATE_SECRET'));
 }finally{api.setDiagnosticsEnabled(false);if(original)window.NativeEditor.traceBatch=original;else delete window.NativeEditor.traceBatch;}
});
test('Cut editing ownership survives document focus loss but rejects another active field or blur',()=>{
 load('abcdef');view.focus();
 const own=Object.getOwnPropertyDescriptor(document,'hasFocus');
 const outside=document.createElement('input');document.body.append(outside);
 try{
  Object.defineProperty(document,'hasFocus',{configurable:true,value:()=>false});
  assert.equal(view.hasFocus,false,'CM correctly reports a window/document interruption');
  assert.equal(document.activeElement,view.contentDOM);
  assert.equal(api.hasEditingFocus(),true,'Cut ownership is independent of window focus; native waits for return');
  outside.focus();assert.equal(api.hasEditingFocus(),false,'another input must never receive a Cut IME continuation');
  view.contentDOM.focus();view.contentDOM.blur();assert.equal(api.hasEditingFocus(),false);
 }finally{outside.remove();if(own)Object.defineProperty(document,'hasFocus',own);else delete document.hasFocus;}
});
test('resource mode edits real files with syntax, undo and explicit native save without notebook injection',async()=>{
 const {syntaxTree}=await import('@codemirror/language');
 const fixtures=[['style.css','body { color: red; }','StyleSheet'],['strings.json','{"title":"Hello"}','JsonText'],['logic.ts','const n: number = 1;','Script'],['help.md','# Heading\n\nText','Document'],['icon.svg','<svg><path/></svg>','Document'],['plain.txt','ordinary text','']];
 let saves=0;const old=window.NativeEditor.save;window.NativeEditor.save=()=>saves++;
 try{
  for(const [name,source,tree] of fixtures){
   api.setDocument(name,'resource');load(source);
   assert.equal(api.getSource(),source);assert.equal(document.documentElement.dataset.documentKind,'resource');
   if(tree)assert.equal(syntaxTree(view.state).type.name,tree);
   view.dispatch({changes:{from:view.state.doc.length,insert:'x'}});assert.equal(api.getSource(),source+'x');
   assert.equal(cm.undo(view),true);assert.equal(api.getSource(),source);
   const before=api.getSource();cm.executeCommand('notebookInsertJS',view);assert.equal(api.getSource(),before);
  }
  cm.executeCommand('saveFile',view);assert.equal(saves,1);assert.equal(calls.source.at(-1),'ordinary text');
  api.setSessionKey('private-resource-session');api.restoreSession(0,0,0);await tick(180);view.dispatch({selection:{anchor:0,head:3}});await tick(180);
  assert.ok(![...Array(localStorage.length)].some((_,i)=>localStorage.key(i).includes('private-resource-session')));
 }finally{if(old)window.NativeEditor.save=old;else delete window.NativeEditor.save;api.setDocument('notebook.html','notebook');load('<notebook></notebook>');}
});
test.after(()=>{api.destroy();dom.window.close();console.error=originalConsoleError;assert.deepEqual(errors,[]);});
