/** Reproduce the transplant from the supplied Acode tree; explicit patches only. */
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {resolve,dirname} from 'node:path';
import {parse} from '@babel/parser';
import {createHash} from 'node:crypto';
const root=resolve(import.meta.dirname,'../..');
const source=resolve(process.argv[2]||'../../acode-reference/Acode-main');
const files=[
 'cm/baseExtensions.ts','cm/mainEditorExtensions.ts','cm/indentationFolding.ts',
 'cm/touchSelectionMenu.js','cm/selectionMenuUtils.ts','cm/editorReadOnly.ts','cm/createEditorView.ts',
 'cm/editorUtils.ts','cm/shiftSelection.ts','cm/lineNumberSelection.ts','cm/keyBindingUtils.js',
 'cm/quickToolsModifierKeys.ts','cm/quickToolsModifierInput.ts','cm/quickToolsNavigation.ts',
 'cm/foldingCommands.js','cm/foldAwareLineCommands.ts','cm/searchMatchHighlighter.ts',
 'cm/scrollPastEnd.ts','cm/indentedLineWrapping.ts','cm/punctuationWrapping.ts',
 'cm/localWordCompletions.ts','cm/tagAutoRename.ts','cm/rainbowBrackets.ts','cm/highlightTags.ts','cm/indentGuides.ts',
 'cm/commandRegistry.js','cm/themes/githubLight.js','lib/selectionMenu.js','palettes/commandPalette/index.js',
 'components/checkbox/index.js','components/checkbox/styles.scss',
 'components/quickTools/index.js','components/quickTools/footer.js','components/quickTools/items.js','components/quickTools/style.scss',
 'components/scrollbar/index.js','components/scrollbar/style.scss',
 'components/tooltip/index.js','components/tooltip/longPress.js','components/tooltip/style.scss',
 'components/palette/index.js','components/palette/style.scss','components/inputhints/index.js','components/inputhints/style.scss',
 'handlers/quickTools.js','handlers/quickToolsInit.js','handlers/quickToolsState.js','handlers/readOnlyQuickToolsCapture.ts',
 'handlers/editorWorkaround.js','handlers/keyboard.js','handlers/windowResize.js',
 'lib/editorInteractionGuard.ts','lib/quickToolsAdapter.js','lib/quickToolsOverlays.js','lib/searchHistory.js','lib/actionStack.js','lib/keyBindings.js',
 'utils/keyboardEvent.js','utils/color/regex.js','dialogs/color.js','dialogs/style.scss','styles/mixins.scss','styles/codemirror.scss',
 'res/icons/style.css','res/icons/icons.ttf','res/fonts/RobotoMono.ttf','lang/en-us.json','lang/ru-ru.json',
];
const supportedApp=new Set(['focusEditor','saveFile','find','replace','gotoline','toggleFullscreen','openCommandPalette','toggleQuickTools','increaseFontSize','decreaseFontSize']);
function adaptRegistry(text){
 const ast=parse(text,{sourceType:'module'}),edits=[];
 const imports=new Set(['fileSystem','@codemirror/lint','@codemirror/lsp-client','cm/lsp','cm/lsp/references','components/symbolsPanel','components/toast','dialogs/prompt','utils/Url']);
 const functions=new Set(['registerLspCommands','registerLintCommands','notifyLspUnavailable','runLspCommand','shareCommand','openInAppBrowserCommand','adjustUiZoom','loadCustomKeyBindings','resetKeyBindings','setKeyBindings']);
 for(const n of ast.program.body){
  if(n.type==='ImportDeclaration'&&imports.has(n.source.value))edits.push([n.start,n.end,'']);
  const fn=n.type==='ExportNamedDeclaration'?n.declaration:n;
  if(fn?.type==='FunctionDeclaration'&&functions.has(fn.id.name))edits.push([n.start,n.end,'']);
  if(n.type==='ExpressionStatement'&&['registerLspCommands','registerLintCommands'].includes(n.expression?.callee?.name))edits.push([n.start,n.end,'']);
  if(fn?.type==='FunctionDeclaration'&&fn.id.name==='registerCoreCommands')for(const st of fn.body.body){
   const arg=st.expression?.arguments?.[0];if(st.expression?.callee?.name!=='addCommand'||arg?.type!=='ObjectExpression')continue;
   const field=name=>arg.properties.find(p=>p.key?.name===name)?.value;
   if((field('requiresView')?.value!==true&&!supportedApp.has(field('name')?.value))||field('name')?.value==='share')edits.push([st.start,st.end,'']);
  }
 }
 for(const[a,b,v]of edits.sort((a,b)=>b[0]-a[0]))text=text.slice(0,a)+v+text.slice(b);
 text=text.replace('if (commandMap.has(name)) return;','if (commandMap.has(name) || APP_KEY_BINDING_NAMES.has(name)) return;');
 // One host request around Cut only; no focus cycling, retry timers or
 // changes to upstream clipboard/delete functions.
 text=text.replace('try {\n\t\tconst result = command.run(targetView, args);',
  'const keyboardRequest = name === "cut" && targetView?.hasFocus ? globalThis.system?.beginClipboardCut?.() : null;\n\ttry {\n\t\tconst result = command.run(targetView, args);');
 text=text.replace('console.error(`Failed to execute command ${name}`, error);\n\t\treturn false;\n\t}',
  'console.error(`Failed to execute command ${name}`, error);\n\t\treturn false;\n\t} finally {\n\t\tif (keyboardRequest != null) globalThis.system?.completeClipboardCut?.(keyboardRequest);\n\t}');
 // Unlike normal DOM deletion, upstream Cut didn't reveal its resulting
 // caret. In Android a caret left outside the viewport can dismiss the IME.
 text=text.replace('changes[0]?.from ?? state.selection.main.from,\n\t\t),\n\t});',
  'changes[0]?.from ?? state.selection.main.from,\n\t\t),\n\t\tuserEvent: "delete.cut",\n\t\tscrollIntoView: true,\n\t});');
 return text;
}
const entries=[];
for(const name of files){
 const original=await readFile(resolve(source,'src',name));let bytes=original;
 if(!name.endsWith('.ttf')){
  let text=original.toString();
  if(name==='cm/touchSelectionMenu.js'){
   const ast=parse(text,{sourceType:'module'}),edits=[];
   for(const n of ast.program.body){
    if(n.type==='ImportDeclaration'&&n.source.value==='@codemirror/lsp-client')edits.push([n.start,n.end,'']);
    if(n.type==='FunctionDeclaration'&&['hasCodeActionProvider','hasLspActions'].includes(n.id.name))edits.push([n.body.start,n.body.end,'{ return false; /* No LSP service in this host. */ }']);
   }
   for(const[a,b,v]of edits.sort((a,b)=>b[0]-a[0]))text=text.slice(0,a)+v+text.slice(b);
  }
  if(name==='cm/createEditorView.ts'){
   text=text.replace('import { CanvasView, type CanvasViewConfig } from "@acode.app/canvas-view";','// DOM renderer; optional IDE canvas backend omitted.\ntype CanvasViewConfig = Record<string, unknown>;').replace('return view instanceof CanvasView;','return false;');
   const a=text.indexOf('\tif (canvas) {'),b=text.indexOf('\n\treturn new EditorView',a);text=text.slice(0,a)+text.slice(b);
  }
  if(name==='lib/selectionMenu.js'){
   const ast=parse(text,{sourceType:'module',plugins:['jsx']}),edits=[];
   for(const n of ast.program.body)if(n.type==='VariableDeclaration')for(const d of n.declarations)if(['showCodeActions','showLspActions'].includes(d.id.name))edits.push([n.start,n.end,'']);
   const visit=node=>{if(!node||typeof node!=='object')return;if(node.type==='ArrayExpression')for(const e of node.elements){if(e?.type==='LogicalExpression'&&['codeActionsAvailable','lspActionsAvailable'].includes(e.left?.name)){let end=e.end;while(/\s/.test(text[end]))end++;if(text[end]===',')end++;edits.push([e.start,end,'']);}}for(const [k,v]of Object.entries(node))if(k!=='loc')if(Array.isArray(v))v.forEach(visit);else if(v&&typeof v==='object')visit(v);};visit(ast.program);
   for(const[a,b,v]of edits.sort((a,b)=>b[0]-a[0]))text=text.slice(0,a)+v+text.slice(b);
  }
  if(name==='cm/commandRegistry.js')text=adaptRegistry(text);
  if(name==='utils/keyboardEvent.js')text=text.replace('initKeyboardEventType = 0;', 'return 0;');
  if(name==='components/quickTools/footer.js')text=text.replace('items, { description, ref }','items, { description }');
  if(name==='lib/searchHistory.js')text=text.replace('const stored = localStorage.getItem(key);','const stored = null; // Host policy: search history stays in memory.').replace('localStorage.setItem(HISTORY_KEY, JSON.stringify(this.history));','// No plaintext search history on disk.');
  if(name==='dialogs/color.js'){
   text=text.replace('from "vanilla-picker"','from "vanilla-picker/csp"');
   text=text.replace('let color = null;', 'let color = null;\n\t\tlet picker = null, modal = false, hiding = false;');
   text=text.replace('const box = tag("div", {','const box = tag("dialog", {');
   text=text.replace('className: "prompt box",','className: "prompt box acode-colour-dialog",\n\t\t\t"aria-modal": "true", "aria-label": strings["choose color"],');
   text=text.replace('const picker = new Picker({','document.body.append(box, mask);\n\t\ttry {\n\t\tpicker = new Picker({');
   text=text.replace('parent.append(formatPopup);','parent.append(formatPopup);\n\t\tcancelBtn.autofocus = true;\n\t\tif (typeof box.showModal === "function") { box.showModal(); modal = true; box.classList.add("has-top-layer"); }\n\t\telse box.setAttribute("open", "");\n\t\t} catch (error) { picker?.destroy(); box.remove(); mask.remove(); reject(error); return; }\n\t\tbox.addEventListener("cancel", event => { event.preventDefault(); hide(); reject(new Error("cancelled")); });');
   text=text.replace('\n\t\tdocument.body.append(box, mask);\n\n\t\trestoreTheme(true);','\n\t\trestoreTheme(true);');
   text=text.replace('function hide() {','function hide() {\n\t\t\tif (hiding) return;\n\t\t\thiding = true;\n\t\t\tif (modal) box.close(); else box.removeAttribute("open");');
  }
  if(name==='handlers/keyboard.js'){
   text=text.replace('import { setBannerKeyboardVisible } from "lib/startAd";','').replace(/\n\tdocument\.addEventListener\("admob\.banner\.size",[\s\S]*?\n\t}\);/,'').replace(/\n\s*toggleBannerAd\((?:false|true)\);/g,'');
   text=text.replace('const { hardKeyboardHidden } = await getSystemConfiguration();','const { hardKeyboardHidden, keyboardHeight } = await getSystemConfiguration();');
   text=text.replace('if (externalKeyboard || !softKeyboardHeight) return;', 'if (externalKeyboard || (!softKeyboardHeight && keyboardHeight <= MIN_KEYBOARD_HEIGHT)) return;');
   text=text.replace('const keyboardHiddenYes = windowHeight <= window.innerHeight;', 'const keyboardHiddenYes = keyboardHeight <= MIN_KEYBOARD_HEIGHT;\n\t\tsoftKeyboardHeight = keyboardHiddenYes ? 0 : keyboardHeight;');
   const a=text.indexOf('function toggleBannerAd(');if(a>=0)text=text.slice(0,a);
  }
  bytes=Buffer.from(text);
 }
 const local='web/src/acode/vendor/'+name,snapshot='third_party/acode/upstream/src/'+name;
 for(const[path,data]of [[local,bytes],[snapshot,original]]){await mkdir(dirname(resolve(root,path)),{recursive:true});await writeFile(resolve(root,path),data);}
 const hash=b=>createHash('sha256').update(b).digest('hex');
 entries.push({source:'src/'+name,local,snapshot,sourceSha256:hash(original),localSha256:hash(bytes),exact:original.equals(bytes)});
}
const main=await readFile(resolve(source,'src/main.scss'),'utf8');
const a=main.indexOf('footer {'),b=main.indexOf('\n.cursor-menu {'),c=main.indexOf('\n}',b)+2;
const icon=main.indexOf('\n.icon {');
const ui='@use "./mixins.scss";\n'+main.slice(icon,main.indexOf('\n}',icon)+2)+'\n'+main.slice(a,c);
await writeFile(resolve(root,'web/src/acode/vendor/styles/editor-ui.scss'),ui);
await writeFile(resolve(root,'third_party/acode/full-editor-manifest.json'),JSON.stringify({sourceCommit:'6962af1e805b8996e743df97cad4df3ecef687e5',files:entries},null,2)+'\n');
console.log(`Imported ${entries.length} files; ${entries.filter(e=>e.exact).length} byte-for-byte.`);
