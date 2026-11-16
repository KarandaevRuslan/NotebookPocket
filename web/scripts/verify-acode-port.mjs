import assert from 'node:assert/strict';
import {readFile,access} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {parse} from '@babel/parser';
const root=new URL('../../',import.meta.url),hash=b=>createHash('sha256').update(b).digest('hex');
const manifest=JSON.parse(await readFile(new URL('third_party/acode/full-editor-manifest.json',root),'utf8'));
for(const entry of manifest.files){
 const original=await readFile(new URL(entry.snapshot,root)),local=await readFile(new URL(entry.local,root));
 assert.equal(hash(original),entry.sourceSha256,entry.source);
 assert.equal(hash(local),entry.localSha256,entry.local);
 if(entry.exact)assert.deepEqual(local,original,entry.local);
}
const manager=await readFile(new URL('third_party/acode/upstream/src/lib/editorManager.js',root),'utf8');
const projection=await readFile(new URL('web/src/acode/vendor/lib/singlePaneRuntime.js',root),'utf8');
const selected=['revealEditorRange','cancelPendingScrollRestore','registerSoftKeyboardCursorReveal','scrollCursorIntoView','suppressCursorReveal','isCursorRevealSuppressed','lockScrollbarScrollPosition','clearScrollbarScrollLock','restoreScrollbarScrollLock','isCursorVisible','safeCoordsAtPos','onscrollV','onscrollVend','onscrollH','onscrollHEnd','setHScrollValue','onscrollleft','readScrollMetrics','updateScrollbarsFromMetrics','setVScrollValue','onscrolltop','clamp01'];
const ast=parse(manager,{sourceType:'module',plugins:['jsx']});const found=new Map();
function walk(n){if(!n||typeof n!=='object')return;if(n.type==='FunctionDeclaration'&&selected.includes(n.id?.name)&&!found.has(n.id.name))found.set(n.id.name,manager.slice(n.start,n.end));for(const[k,v]of Object.entries(n)){if(k==='loc')continue;if(Array.isArray(v))v.forEach(walk);else if(v&&typeof v==='object')walk(v);}}
walk(ast.program);assert.equal(found.size,selected.length);
for(const[name,body]of found)assert.ok(projection.includes(body),`Changed upstream manager function: ${name}`);
const hook=await readFile(new URL('third_party/acode/upstream/modify-java-files.js',root),'utf8'),java=await readFile(new URL('app/src/main/java/dev/pocket/notebook/web/EditorActionModeWebView.java',root),'utf8');
let methods;
function nativeWalk(n){if(!n||typeof n!=='object')return;if(n.type==='ObjectProperty'&&n.key.value==='SystemWebView.java'&&n.value.type==='ObjectExpression'&&n.value.properties.some(p=>p.key?.name==='methods'))methods=n.value.properties.find(p=>p.key?.name==='methods').value.elements;for(const[k,v]of Object.entries(n)){if(k==='loc')continue;if(Array.isArray(v))v.forEach(nativeWalk);else if(v&&typeof v==='object')nativeWalk(v);}}
nativeWalk(parse(hook,{sourceType:'unambiguous'}).program);
const nativeNames=new Set(['setNativeContextMenuDisabled','onCreateInputConnection','startActionMode','startActionModeForChild','wrapActionModeCallback','suppressActionMode','suppressActionModeUi']);let copied=0;
for(const m of methods){const name=m.properties.find(p=>p.key?.name==='name')?.value.value;if(!nativeNames.has(name))continue;const body=m.properties.find(p=>p.key?.name==='body').value.elements.map(v=>v.value??v.quasis.map(q=>q.value.cooked).join('')).join('\n');assert.ok(java.includes(body),'Changed native Acode method: '+name);copied++;}
assert.equal(copied,9);
await assert.rejects(access(new URL('web/src/editor/quicktools/QuickTools.js',root)));
await assert.rejects(access(new URL('web/src/editor/selection/TouchSelection.js',root)));
console.log(`Acode: ${manifest.files.length} imported files, ${manifest.files.filter(f=>f.exact).length} byte-for-byte, ${found.size} exact manager functions, ${copied} preserved native core bodies (diagnostic prologues).`);
