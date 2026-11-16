/** Project the single-editor portions out of Acode's multi-pane IDE manager.
 * Function bodies and selection extensions stay upstream text. */
import {readFile,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {parse} from '@babel/parser';
const source=resolve(process.argv[2]);const root=resolve(import.meta.dirname,'..');
const code=await readFile(resolve(source,'src/lib/editorManager.js'),'utf8');
const ast=parse(code,{sourceType:'module',plugins:['jsx']});const functions=new Map(),variables=new Map(),assignments=new Map();
function visit(n){if(!n||typeof n!=='object')return;
 if(n.type==='FunctionDeclaration'&&n.id&&!functions.has(n.id.name))functions.set(n.id.name,n);
 if(n.type==='VariableDeclaration')for(const d of n.declarations)if(d.id.type==='Identifier'&&!variables.has(d.id.name))variables.set(d.id.name,n);
 if(n.type==='ExpressionStatement'&&n.expression?.type==='AssignmentExpression'){
  const lhs=n.expression.left;if(lhs.object?.name==='editor'&&lhs.property?.name&&!assignments.has(lhs.property.name))assignments.set(lhs.property.name,n);
 }
 for(const[k,v]of Object.entries(n)){if(k==='loc')continue;if(Array.isArray(v))v.forEach(visit);else if(v&&typeof v==='object')visit(v);}
}visit(ast.program);
const fn=name=>{const n=functions.get(name);if(!n)throw Error(name);return code.slice(n.start,n.end);};
const decl=name=>{const n=variables.get(name);if(!n)throw Error(name);return code.slice(n.start,n.end);};
const compatibility=['insert','execCommand','getCopyText','gotoLine','scrollToRow','setSelection','setMenu'].map(name=>{const n=assignments.get(name);if(!n)throw Error(name);return code.slice(n.start,n.end);}).join('\n');
let setup=fn('setupEditor').replace('\n\t\tupdateMargin(true);','').replace('\n\t\tupdateSideButtonContainer();','').replace('\n\t\ttoggleProblemButton();','');
const prefix=`import {EditorView,hasHoverTooltips,closeHoverTooltips} from '@codemirror/view';
import {EditorSelection} from '@codemirror/state';
import {executeCommand} from 'cm/commandRegistry';
import {focusEditorIfEditable,blurEditorIfReadOnly} from 'cm/editorReadOnly';
import {isShiftSelectionActive as resolveShiftSelectionActive,isMultiCursorSelectionActive as resolveMultiCursorSelectionActive} from 'cm/shiftSelection';
import createPaneTouchSelectionMenu from 'cm/touchSelectionMenu';
import ScrollBar from 'components/scrollbar';
import quickTools from 'components/quickTools';
import keyboardHandler,{keydownState} from 'handlers/keyboard';
import {getSystemConfiguration,HARDKEYBOARDHIDDEN_NO} from 'lib/systemConfiguration';
import appSettings from 'lib/settings';
import {editorDiagnosticLog} from '../../../logging/EditorDiagnostics.js';
import {runAutomaticCursorReveal,recordPipeline,tracePipelineDuration} from '../../../logging/EditorPipelineDiagnostics.js';

export function createSinglePaneRuntime($container){
 let editor,touchSelectionController,touchSelectionSyncRaf=0;
 const manager=globalThis.editorManager;
 const pane={editor:null,touchSelectionController:null,activeFile:manager.activeFile};
 const activePane=pane;
 const getActivePane=()=>pane;
 const setActivePane=p=>{if(p!==pane)throw new Error('This host has one editor pane');};
 let preventScrollbarV=false,preventScrollbarH=false,isScrolling=false;
 let lastScrollTop=0,lastScrollLeft=0,suppressCursorRevealUntil=0;
 let scrollbarScrollLockUntil=0,scrollbarScrollLockTop=null,scrollbarScrollLockLeft=null;
 let nativeContextMenuDisabled=null;
 let scrollRestoreFrame=0,scrollRestoreNestedFrame=0,scrollRestoreTimeout=0;
 const $vScrollbar=ScrollBar({parent:$container,placement:'right',width:20,thumbHeight:50,onscroll:onscrollV,onscrollend:onscrollVend});
 const $hScrollbar=ScrollBar({parent:$container,placement:'bottom',width:20,thumbHeight:50,onscroll:onscrollH,onscrollend:onscrollHEnd});
`;
const extensions=['pointerCursorVisibilityExtension','isShiftClickSelectionEnabled','isShiftSelectionActive','isMultiCursorSelectionActive','isQuickToolsMultiCursorSelectionActive','shiftClickSelectionExtension','multiCursorSelectionExtension','touchSelectionUpdateExtension'];
const funcs=['revealEditorRange','cancelPendingScrollRestore','registerSoftKeyboardCursorReveal','scrollCursorIntoView','suppressCursorReveal','isCursorRevealSuppressed','lockScrollbarScrollPosition','clearScrollbarScrollLock','restoreScrollbarScrollLock','isCursorVisible','safeCoordsAtPos','onscrollV','onscrollVend','onscrollH','onscrollHEnd','setHScrollValue','onscrollleft','readScrollMetrics','updateScrollbarsFromMetrics','setVScrollValue','onscrolltop','clamp01'];
const tail=`
 // Host policy surrounds the original function, whose body remains upstream.
 const upstreamCursorReveal=scrollCursorIntoView;
 scrollCursorIntoView=options=>runAutomaticCursorReveal(editor,options,()=>upstreamCursorReveal(options));
 // Cover both runtime.refresh and upstream setupEditor/syncScrollUi without
 // replacing request objects, their callbacks or the original function bodies.
 const upstreamReadScrollMetrics=readScrollMetrics,upstreamUpdateScrollbars=updateScrollbarsFromMetrics;
 readScrollMetrics=()=>tracePipelineDuration('pipeline-scrollbar-read','scrollbarReads',upstreamReadScrollMetrics);
 updateScrollbarsFromMetrics=(metrics,sourcePane)=>{
  if(!editorDiagnosticLog.enabled)return upstreamUpdateScrollbars(metrics,sourcePane);
  return tracePipelineDuration('pipeline-scrollbar-write','scrollbarWrites',()=>upstreamUpdateScrollbars(metrics,sourcePane));
 };
 return {
  extensions:{pointerCursorVisibilityExtension,shiftClickSelectionExtension,multiCursorSelectionExtension,touchSelectionUpdateExtension},
  attach(view){editor=view;pane.editor=view;manager.editor=view;
   touchSelectionController=createPaneTouchSelectionMenu(view,{container:$container,getActiveFile:()=>manager.activeFile,isShiftSelectionActive,isMultiCursorSelectionActive:isQuickToolsMultiCursorSelectionActive});
   pane.touchSelectionController=touchSelectionController;
   ${compatibility}
   setupEditor(pane);registerSoftKeyboardCursorReveal();
  },
  get selectionMenu(){return touchSelectionController;},
  refresh(reason='runtime-refresh'){
   if(!editor)return;
   if(editorDiagnosticLog.enabled)recordPipeline('pipeline-refresh',{reason});
   editor.requestMeasure({read:readScrollMetrics,write:m=>updateScrollbarsFromMetrics(m,pane)});
  },
  destroy(){pane.cleanupEditorListeners?.();cancelAnimationFrame(touchSelectionSyncRaf);touchSelectionController?.destroy();$vScrollbar.destroy();$hScrollbar.destroy();$vScrollbar.remove();$hScrollbar.remove();},
 };
}
`;
await writeFile(resolve(root,'src/acode/vendor/lib/singlePaneRuntime.js'),prefix+decl('setNativeContextMenuDisabled')+'\n'+extensions.map(decl).join('\n')+'\n'+funcs.map(fn).join('\n')+'\n'+setup+tail);
const file=await readFile(resolve(source,'src/lib/editorFile.js'),'utf8');const tree=parse(file,{sourceType:'module',plugins:['jsx']});
const exported=tree.program.body.find(n=>n.type==='ExportNamedDeclaration'&&n.declaration?.id?.name==='syncQuickToolsVisibility');
await writeFile(resolve(root,'src/acode/host/editorFile.js'),`import quickTools from 'components/quickTools';\nimport quickToolsAdapters from 'lib/quickToolsAdapter';\nimport appSettings from 'lib/settings';\nimport actions from 'handlers/quickTools';\n`+file.slice(exported.start,exported.end)+'\n');
console.log('Projected Acode manager: 22 scroll/focus functions, 8 selection extensions, full setupEditor, 7 compatibility methods.');
