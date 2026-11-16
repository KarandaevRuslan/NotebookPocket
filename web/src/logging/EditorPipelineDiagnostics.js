import {editorDiagnosticLog, nativeSelectionFields, selectionDiagnosticFields} from './EditorDiagnostics.js';

// Frequent pipeline records stay in JS during the operation being observed.
// No bridge calls, DOM layout reads or raw stack/source text in these hooks.
const limit = 256;
let queue = [], timer = 0, dropped = 0, cause = 'unattributed-js', writeId = 0;
const counters = Object.create(null);
export function pipelineCount(key) {
  if (editorDiagnosticLog.enabled) counters[key] = (counters[key] || 0) + 1;
}
export function recordPipeline(event, fields = {}) {
  if (!editorDiagnosticLog.enabled) return;
  const capturedClock = Date.now();
  const payload = editorDiagnosticLog.capture(event,{...editorDiagnosticLog.options,...fields,capturedClock,mono:performance.now()});
  if (!payload) return;
  if (queue.length >= limit) {queue.shift();dropped++;}
  queue.push(payload);
  if (!timer) timer = setTimeout(flushPipeline,100);
}
export function flushPipeline() {
  clearTimeout(timer);timer=0;
  if (!editorDiagnosticLog.enabled) {queue=[];dropped=0;return;}
  if (dropped) queue.push(editorDiagnosticLog.capture('diagnostics-pipeline-dropped',{dropped}));
  dropped=0;
  const ready=queue;queue=[];
  for(let i=0;i<ready.length;i+=32) editorDiagnosticLog.emitBatch(ready.slice(i,i+32));
}
export function withPipelineCause(next, callback) {
  const previous=cause;cause=next;
  try {return callback();} finally {cause=previous;}
}
export function tracePipelineDuration(event,counter,callback) {
  if(!editorDiagnosticLog.enabled)return callback();
  const started=performance.now();
  try{return callback();}
  finally{pipelineCount(counter);recordPipeline(event,{durationMs:performance.now()-started});}
}
function actor() {
  if(cause!=='unattributed-js')return cause;
  // Classify fixed method names only. Never retain or send a raw stack.
  const stack=new Error().stack || '';
  if(stack.includes('updateSelection'))return 'cm-selection-sync';
  if(stack.includes('scrollIntoView'))return 'cm-reveal';
  if(stack.includes('.focus'))return 'focus';
  return 'unattributed-js';
}
function model(view) {
  const range=view.state.selection.main;
  return {anchor:range.anchor,head:range.head,empty:range.empty,docLength:view.state.doc.length,hasFocus:view.hasFocus};
}
function targetLabel(target,view) {
  const el=target?.nodeType===1?target:target?.parentElement;
  if(el===view.contentDOM||view.contentDOM.contains(el))return 'editor-content';
  if(el?.closest('.cursor-menu'))return 'selection-menu';
  if(el?.closest('#quick-tools'))return 'quicktools';
  if(el?.closest('.acode-colour-dialog'))return 'colour';
  return 'outside';
}
function replaceMethod(object,name,make,cleanups) {
  const original=object[name], own=Object.getOwnPropertyDescriptor(object,name);
  if(typeof original!=='function')return;
  const wrapped=make(original);
  try {Object.defineProperty(object,name,{configurable:true,writable:true,value:wrapped});}
  catch {recordPipeline('diagnostics-hook-unavailable',{operation:name});return;}
  cleanups.push(()=>{
    if(object[name]!==wrapped)return;
    if(own)Object.defineProperty(object,name,own);else delete object[name];
  });
}

/** Debug-only hooks. Originals receive the same receiver/arguments/result;
 * exceptions propagate. Every installed property/listener/timer is restored.
 * These are observations of JS writes, not a simulation of Android gestures.
 */
export function installEditorPipelineDiagnostics(view) {
  let stop=()=>{};
  const start=()=>{
    const cleanups=[];
    let down=false,lastPointer=0,lastFrame=0,raf=0,frames=0,slow=0,maxGap=0,lastSummary=performance.now();
    const gesture=()=>({pointerDown:down,pointerAgeMs:lastPointer?Date.now()-lastPointer:-1});
    const selection=document.getSelection(), proto=selection&&Object.getPrototypeOf(selection);
    if(proto)for(const operation of ['collapse','extend','setBaseAndExtent','addRange','removeAllRanges','selectAllChildren']) {
      replaceMethod(proto,operation,original=>function(...args){
        const first=args[0], node=first?.nodeType?first:first?.startContainer;
        const inside=this===document.getSelection()&&
          (view.contentDOM.contains(this.anchorNode)||view.contentDOM.contains(node||null)||view.hasFocus);
        if(!inside)return Reflect.apply(original,this,args);
        const id=++writeId,before=nativeSelectionFields(view),started=performance.now(),source=actor();
        let threw=true;
        try {const result=Reflect.apply(original,this,args);threw=false;return result;}
        finally {
          pipelineCount('selectionWrites');
          recordPipeline('selection-dom-write',{...model(view),...nativeSelectionFields(view),...gesture(),writeId:id,operation,actor:source,
            beforeNativeAnchor:before.nativeAnchor,beforeNativeHead:before.nativeHead,durationMs:performance.now()-started,threw});
        }
      },cleanups);
    }
    for(const operation of ['focus','blur'])replaceMethod(window.HTMLElement.prototype,operation,original=>function(...args){
      const target=targetLabel(this,view);
      if(target==='outside')return Reflect.apply(original,this,args);
      const started=performance.now(),source=actor();let threw=true;
      try{const result=Reflect.apply(original,this,args);threw=false;return result;}
      finally{recordPipeline('focus-js-call',{...model(view),operation,target,actor:source,durationMs:performance.now()-started,threw});}
    },cleanups);
    const scroll=view.scrollDOM;
    for(const operation of ['scrollTo','scrollBy','scrollIntoView'])replaceMethod(scroll,operation,original=>function(...args){
      const previousY=scroll.scrollTop,started=performance.now(),source=actor();let threw=true;
      const requestedY=typeof args[0]==='object'?args[0]?.top:args[1];
      try{const result=Reflect.apply(original,this,args);threw=false;return result;}
      finally{pipelineCount('scrollWrites');recordPipeline('scroll-js-call',{...model(view),...gesture(),actor:source,operation,previousY,resultY:scroll.scrollTop,requestedY:typeof requestedY==='number'?requestedY:-1,durationMs:performance.now()-started,threw});}
    },cleanups);
    // Direct JS assignments are separate from scroll events caused by the
    // browser, layout or an animated scroll. No attribution by guesswork.
    let descriptor,owner=scroll;
    while(owner&&!descriptor){descriptor=Object.getOwnPropertyDescriptor(owner,'scrollTop');owner=Object.getPrototypeOf(owner);}
    const own=Object.getOwnPropertyDescriptor(scroll,'scrollTop');
    if(descriptor?.get&&descriptor?.set&&own?.configurable!==false){
      const sourceDescriptor=descriptor;
      const getter=function(){return sourceDescriptor.get.call(this);};
      const setter=function(value){
        const previousY=sourceDescriptor.get.call(this),source=actor();
        sourceDescriptor.set.call(this,value);
        pipelineCount('scrollWrites');recordPipeline('scroll-js-write',{...model(view),...gesture(),operation:'scrollTop',actor:source,previousY,resultY:sourceDescriptor.get.call(this),requestedY:typeof value==='number'?value:-1});
      };
      Object.defineProperty(scroll,'scrollTop',{configurable:true,get:getter,set:setter});
      cleanups.push(()=>{if(Object.getOwnPropertyDescriptor(scroll,'scrollTop')?.set!==setter)return;if(own)Object.defineProperty(scroll,'scrollTop',own);else delete scroll.scrollTop;});
    }
    // Requests may coalesce. measure() is a CM method call, not a count of
    // browser layouts or the iterations inside CM's measurement loop.
    replaceMethod(view,'requestMeasure',original=>function(request){
      pipelineCount('measureRequests');
      // Preserve request identity/key for CM coalescing. Do not wrap callbacks.
      return Reflect.apply(original,this,arguments);
    },cleanups);
    replaceMethod(view,'measure',original=>function(...args){
      return tracePipelineDuration('pipeline-cm-measure','cmMeasureCalls',()=>Reflect.apply(original,this,args));
    },cleanups);
    replaceMethod(view,'update',original=>function(transactions){
      const started=performance.now();pipelineCount('updateCalls');
      try{return Reflect.apply(original,this,arguments);}
      finally{recordPipeline('pipeline-update',{...model(view),durationMs:performance.now()-started,count:transactions?.length||0});}
    },cleanups);
    const pointer=event=>{
      if(event.type.endsWith('down'))down=true;
      if(event.type.endsWith('up')||event.type.endsWith('cancel'))down=false;
      lastPointer=Date.now();
      recordPipeline('interaction-pointer',{...model(view),...gesture(),operation:event.type,target:targetLabel(event.target,view),x:event.clientX,y:event.clientY});
    };
    for(const name of ['pointerdown','pointerup','pointercancel']){
      document.addEventListener(name,pointer,true);cleanups.push(()=>document.removeEventListener(name,pointer,true));
    }
    const scrollEvent=()=>recordPipeline('scroll-observed',{...model(view),...gesture(),scrollY:scroll.scrollTop,reason:'origin-not-inferred'});
    scroll.addEventListener('scroll',scrollEvent,{passive:true});cleanups.push(()=>scroll.removeEventListener('scroll',scrollEvent));
    const visibility=()=>{lastFrame=0;recordPipeline('diagnostics-visibility',{active:!document.hidden});};
    document.addEventListener('visibilitychange',visibility);cleanups.push(()=>document.removeEventListener('visibilitychange',visibility));
    const frame=now=>{
      if(!document.hidden){if(lastFrame){const gap=now-lastFrame;maxGap=Math.max(maxGap,gap);if(gap>34)slow++;}frames++;lastFrame=now;}
      if(now-lastSummary>=1000){
        recordPipeline('pipeline-summary',{...counters,rafFrames:frames,slowFrames:slow,maxGapMs:maxGap});
        for(const key of Object.keys(counters))delete counters[key];
        frames=slow=maxGap=0;lastSummary=now;
      }
      raf=requestAnimationFrame(frame);
    };
    raf=requestAnimationFrame(frame);
    recordPipeline('diagnostics-pipeline-installed',editorDiagnosticLog.options);
    return ()=>{
      cancelAnimationFrame(raf);for(const cleanup of cleanups.reverse())cleanup();
      clearTimeout(timer);timer=0;queue=[];dropped=0;
      for(const key of Object.keys(counters))delete counters[key];
    };
  };
  const sync=enabled=>{stop();stop=enabled?start():()=>{};};
  const unsubscribe=editorDiagnosticLog.subscribe(sync);sync(editorDiagnosticLog.enabled);
  return {destroy(){unsubscribe();stop();}};
}

export function recordResize(view,host,trigger) {
  pipelineCount('resizeCalls');pipelineCount(trigger==='window-resize'?'windowResizeCalls':trigger==='visual-resize'?'visualResizeCalls':'hostResizeCalls');
  if(!editorDiagnosticLog.enabled)return;
  // This snapshot runs at an existing resize boundary, not in a handle write.
  recordPipeline('pipeline-resize',{...selectionDiagnosticFields(view),trigger,innerHeight:window.innerHeight,
    visualHeight:window.visualViewport?.height||window.innerHeight,hostHeight:host.clientHeight,
    shellHeight:document.getElementById('editor-shell')?.clientHeight||0,editorHeight:view.dom.clientHeight,
    quickToolsHeight:document.getElementById('quick-tools')?.offsetHeight||0});
}

/** The only behavior change: automatic Acode head reveal does not move the
 * viewport for a range known to be too tall. Missing endpoint geometry alone
 * must not block reveal. CM's block map can still identify large multiline
 * ranges with unrendered endpoints. Otherwise retain upstream head reveal.
 * Explicit Find/Go-to commands stay CM's.
 * Debug legacy mode permits a controlled comparison on the same device.
 */
export function runAutomaticCursorReveal(view,options,upstream) {
  if(!view)return upstream();
  const rangeActive=!view.state.selection.main.empty;
  let allowed=true,reason=rangeActive?'legacy':'caret',selectionHeight=-1,availableHeight=-1,blockGap=-1;
  if(rangeActive&&editorDiagnosticLog.options.revealMode!=='legacy'){
    availableHeight=Math.max(0,view.scrollDOM.clientHeight-40);
    try{
      const range=view.state.selection.main,from=view.coordsAtPos(range.from,1),to=view.coordsAtPos(range.to,-1);
      if(from&&to&&[from.top,from.bottom,to.top,to.bottom].every(Number.isFinite))
        selectionHeight=Math.max(from.bottom,to.bottom)-Math.min(from.top,to.top);
    }catch{ /* Same conservative fallback as upstream safeCoordsAtPos. */ }
    if(availableHeight>0&&selectionHeight>=0){
      allowed=selectionHeight<=availableHeight;
      reason=allowed?'short-range':'large-range';
    }else{
      // The gap excludes both endpoint blocks. Using a whole wrapped line's
      // height would wrongly classify a short selection in that line as tall.
      // If the gap alone exceeds the viewport, the range cannot fit either.
      try{
        const range=view.state.selection.main,from=view.lineBlockAt(range.from),to=view.lineBlockAt(range.to);
        if(Number.isFinite(from?.bottom)&&Number.isFinite(to?.top))blockGap=Math.max(0,to.top-from.bottom);
      }catch{ /* No reliable height-map bounds: use the original reveal. */ }
      allowed=!(availableHeight>0&&blockGap>availableHeight);
      reason=allowed?'geometry-fallback':'large-block-gap';
    }
  }
  if(editorDiagnosticLog.enabled)recordPipeline('scroll-auto-decision',{...model(view),actor:'acode-auto',rangeActive,allowed,reason,selectionHeight,availableHeight,blockGap,revealMode:editorDiagnosticLog.options.revealMode});
  if(!allowed)return;
  return editorDiagnosticLog.enabled?withPipelineCause('acode-auto',upstream):upstream();
}
