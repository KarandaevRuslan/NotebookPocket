/** Local fallback for editor selection/scroll on Android Activity recreation.
 * SavedStateHandle remains authoritative across process restarts; localStorage
 * catches a final touch gesture before the async JS-to-Kotlin bridge delivers.
 * Never stores source text or clipboard content.
 */
const PREFIX='np-editor-session-v1:';
const integer=(value,low=0)=>Math.max(low,Math.floor(Number(value)||0));
export function saveEditorSession(storage,id,sourceLength,selection,scrollTop, extra={}){
  if(!id)return;
  const record={version:1,sourceLength:integer(sourceLength),anchor:integer(selection.anchor),
    head:integer(selection.head),scrollTop:integer(scrollTop),savedAt:Date.now(), scrollLeft:integer(extra.scrollLeft),
    folds:Array.isArray(extra.folds)?extra.folds:[],selection:extra.selection||null};
  try{storage?.setItem(PREFIX+id,JSON.stringify(record));}catch{}
}
export function readEditorSession(storage,id,sourceLength){
  if(!id)return null;
  try{
    const record=JSON.parse(storage?.getItem(PREFIX+id)||'null');
    if(record?.version!==1||record.sourceLength!==sourceLength||
       !Number.isFinite(record.anchor)||!Number.isFinite(record.head)||
       !Number.isFinite(record.scrollTop)||record.anchor<0||record.head<0||record.scrollTop<0||
       record.anchor>sourceLength||record.head>sourceLength)
      return null;
    record.scrollLeft=integer(record.scrollLeft);
    record.folds=Array.isArray(record.folds)?record.folds.slice(0,10000).filter(f=>f &&
      ['fromLine','fromCol','toLine','toCol'].every(k=>Number.isSafeInteger(f[k])) &&
      f.fromLine>=1 && f.toLine>=f.fromLine && f.fromCol>=0 && f.toCol>=0):[];
    return record;
  }catch{return null;}
}
