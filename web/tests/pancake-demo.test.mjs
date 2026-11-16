import {fileURLToPath} from 'node:url';
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {JSDOM} from 'jsdom';
import {build} from 'esbuild';
import {deserialize,transpile} from '@observablehq/notebook-kit';
const folder=new URL('../../app/src/main/assets/demo/pancakes/',import.meta.url);
const source=await readFile(new URL('notebook.html',folder),'utf8');
const previewCss=await Promise.all([
 readFile(new URL('../src/styles.css',import.meta.url),'utf8'),
 readFile(new URL('../src/preview/inputs-theme.css',import.meta.url),'utf8'),
]).then(([base,inputs])=>`${base}\n${inputs}`);
const previewPage=await readFile(new URL('../preview.html',import.meta.url),'utf8');
const base='https://appassets.androidplatform.net/notebook-resources/test/';
const names=['styles/pancakes.css','i18n/en.json','i18n/ru.json','images/pancakes.png','images/preparation.png'];
const text=Object.fromEntries(await Promise.all(['en','ru'].map(async lang=>[lang,JSON.parse(await readFile(new URL(`i18n/${lang}.json`,folder),'utf8'))])));
const bundle=await build({entryPoints:[fileURLToPath(new URL('../src/preview.js',import.meta.url))],bundle:true,write:false,format:'iife',platform:'browser',loader:{'.css':'empty'},external:['https://*'],logLevel:'silent',plugins:[{name:'keep-optional-builtins-lazy',setup(b){b.onResolve({filter:/.*/},a=>a.kind==='dynamic-import'?{path:a.path,external:true}:null)}}]});
const delay=ms=>new Promise(r=>setTimeout(r,ms));
async function until(fn){for(let i=0;i<200;i++){if(fn())return;await delay(30);}throw new Error('Notebook did not render');}
async function preview(lang,dark=false){
 const dom=new JSDOM('<!doctype html><html><body class="preview-page"><main id="notebook"></main></body></html>',{url:'https://appassets.androidplatform.net/assets/web/preview.html',runScripts:'outside-only',pretendToBeVisual:true});
 const w=dom.window;const style=w.document.createElement("style");style.textContent=previewCss;w.document.head.append(style);w.__appPreferences={language:lang,dark,locale:lang,timeZone:'Asia/Krasnoyarsk',epochMillis:Date.now(),elapsedRealtimeMillis:50000,bootCount:4};
 w.matchMedia=()=>({matches:false,addListener(){},removeListener(){},addEventListener(){},removeEventListener(){}});
 w.TextDecoder=TextDecoder;w.TextEncoder=TextEncoder;
 w.fetch=async url=>{const path=String(url).startsWith(base)?decodeURIComponent(String(url).slice(base.length)):null;if(!names.includes(path))throw new Error('Blocked resource '+url);return new Response(await readFile(new URL(path,folder)),{status:200});};
 w.eval(bundle.outputFiles[0].text);await w.PreviewApi.render(source,'pancake-test',false,{names,base});return dom;
}
test('common template compiles all cells and loads local CSS, pictures and equal-key translations',()=>{
 assert.match(previewPage,/connect-src 'self';/);assert.deepEqual(Object.keys(text.en).sort(),Object.keys(text.ru).sort());assert.equal(text.ru.steps.length,8);
 const dom=new JSDOM();const n=deserialize(source,{parser:new dom.window.DOMParser()});assert.equal(n.cells.length,22);for(const cell of n.cells)assert.doesNotThrow(()=>transpile(cell));dom.window.close();
 assert.doesNotMatch(source,/<style>|const L\s*=\s*\{/);assert.match(source,/Pocket\.language/);assert.match(source,/pocketNow/);for(const name of names)assert.ok(source.includes(`FileAttachment("${name}")`));
});
for(const lang of ['en','ru'])test(`real Observable renders ${lang}, preserves controls and runs the timer`,async()=>{
 const dom=await preview(lang),w=dom.window,d=w.document;
 try {
  await until(()=>d.querySelector('.blini-timer output')&&d.querySelector('.blini-step h3'));
  assert.equal(d.querySelector('.blini-hero h1').textContent,text[lang].hero);assert.equal(d.querySelector('.blini-step h3').textContent,text[lang].steps[0].title);
  assert.equal(d.querySelectorAll('.blini-ingredients .blini-item').length,8);assert.ok(d.querySelector('.blini-hero-photo').src.endsWith('/images/pancakes.png'));assert.ok(d.querySelector('.blini-preparation img').src.endsWith('/images/preparation.png'));
  assert.equal(d.querySelectorAll('.error').length,0,[...d.querySelectorAll('.error')].map(e=>e.textContent).join('\n'));
  const timer=d.querySelector('.blini-timer'),input=timer.querySelector('[data-duration]');input.value='2';input.dispatchEvent(new w.Event('change',{bubbles:true}));timer.querySelector('[data-start]').click();await until(()=>timer.querySelector('output').textContent==='00:01');
  timer.querySelector('[data-pause]').click();const paused=timer.querySelector('output').textContent;await delay(300);assert.equal(timer.querySelector('output').textContent,paused);
  timer.querySelector('[data-start]').click();await until(()=>timer.querySelector('[data-status]').textContent===text[lang].timerDone);timer.querySelector('[data-reset]').click();assert.equal(timer.querySelector('output').textContent,'00:02');
  for(let step=1;step<8;step++){const next=[...d.querySelectorAll('form[data-np-persist-step] button')].find(b=>b.textContent===text[lang].next);next.click();await until(()=>d.querySelector('.blini-step h3').textContent===text[lang].steps[step].title);}
  const other=lang==='en'?'ru':'en';w.PreviewApi.setPreferences({language:other,timeZone:'Asia/Krasnoyarsk'});await w.PreviewApi.render(source,'pancake-test',false,{names,base});await until(()=>d.querySelector('.blini-hero h1')?.textContent===text[other].hero&&d.querySelector('.blini-timer [data-duration]'));assert.equal(d.querySelector('.blini-timer [data-duration]').value,'2');await until(()=>d.querySelector('.blini-step h3')?.textContent===text[other].steps[7].title);
  const range=d.querySelector('.blini-control-wrap input[type=range]');range.value='4';range.dispatchEvent(new w.Event('input',{bubbles:true}));const stamp=d.querySelector('.blini-clock time').getAttribute('datetime');await delay(1200);assert.equal(d.querySelector('.blini-control-wrap input[type=range]').value,'4');assert.notEqual(d.querySelector('.blini-clock time').getAttribute('datetime'),stamp);assert.equal(d.querySelectorAll('.error').length,0);
 }finally{dom.window.close();}
});

test('empty/style cells create no stripes and both explicit app palettes keep errors visible',async()=>{
 const dom=await preview('en'),w=dom.window,d=w.document;
 try {
  await until(()=>d.querySelector('.blini-hero')&&d.querySelector('.blini-timer'));
  const first=d.querySelector('.blini-hero').closest('.cell');
  for(const cell of d.querySelectorAll('.cell')) {
   if(cell===first)break;
   assert.equal(w.getComputedStyle(cell).display,'none');
  }
  const hero=d.querySelector('.blini-hero');assert.equal(hero.dataset.pancakeTheme,'light');
  const light=w.getComputedStyle(hero).getPropertyValue('--b-ink');assert.ok(light);
  w.PreviewApi.setPreferences({language:'en',dark:true,timeZone:'Asia/Krasnoyarsk'});
  await w.PreviewApi.render(source,'pancake-test',false,{names,base});
  await until(()=>d.querySelector('.blini-hero')?.dataset.pancakeTheme==='dark');
  assert.notEqual(w.getComputedStyle(d.querySelector('.blini-hero')).getPropertyValue('--b-ink'),light);
  assert.equal(d.querySelector('.blini-timer').dataset.pancakeTheme,'dark');
  // A computation which later produces an error must become visible again.
  await w.PreviewApi.render('<notebook><title>Failure</title><script id="1" type="module">throw new Error("visible error")</script></notebook>','error-test',false,{names:[],base});
  await until(()=>d.querySelector('.cell')?.textContent.includes('visible error'));
  assert.notEqual(w.getComputedStyle(d.querySelector('.cell')).display,'none');
 }finally{dom.window.close();}
});

test('official Observable step buttons use modern grouped styles and remain interactive',async()=>{
 const dom=await preview('en'),w=dom.window,d=w.document;
 try{
  await until(()=>d.querySelector('form[data-np-persist-step].np-input-button-group > button'));
  const form=d.querySelector('form[data-np-persist-step]'),buttons=[...form.querySelectorAll(':scope > button')];
  assert.equal(buttons.length,3);assert.equal(w.getComputedStyle(form).display,'flex');assert.equal(w.getComputedStyle(form).flexWrap,'wrap');
  for(const button of buttons){const css=w.getComputedStyle(button);assert.equal(css.borderRadius,'999px');assert.equal(css.minHeight,'44px');}
  buttons[1].click();await until(()=>d.querySelector('.blini-step h3')?.textContent===text.en.steps[1].title);
  const back=[...d.querySelectorAll('form[data-np-persist-step] > button')][0];back.click();await until(()=>d.querySelector('.blini-step h3')?.textContent===text.en.steps[0].title);
  const next=[...d.querySelectorAll('form[data-np-persist-step] > button')][1];next.click();await until(()=>d.querySelector('.blini-step h3')?.textContent===text.en.steps[1].title);
  const again=[...d.querySelectorAll('form[data-np-persist-step] > button')][2];again.click();await until(()=>d.querySelector('.blini-step h3')?.textContent===text.en.steps[0].title);
 }finally{dom.window.close();}
});

test('Nexus cooking counter uses transactions, a derived Handler and Observable subscriptions', async () => {
 const dom=await preview('ru'),w=dom.window,d=w.document;
 try {
  const number=selector=>d.querySelector(selector)?.textContent;
  await until(()=>number('[data-nexus-done]')==='0'&&number('[data-nexus-servings]')==='0');
  const card=d.querySelector('[data-nexus-tracker]');
  assert.equal(card.querySelector('h2').textContent,text.ru.nexusTitle);
  assert.equal(card.querySelectorAll('.blini-nexus-how li').length,4);
  const add=card.querySelector('[data-nexus-add]');
  for(let n=1;n<=3;n++){add.click();await until(()=>number('[data-nexus-done]')===String(n));}
  await until(()=>number('[data-nexus-servings]')==='1');
  assert.equal(card.querySelector('.blini-nexus-progress').getAttribute('aria-valuenow'),'3');
  card.querySelector('[data-nexus-undo]').click();
  await until(()=>number('[data-nexus-done]')==='2');
  assert.equal(number('[data-nexus-servings]'),'1');
  const range=d.querySelector('.blini-control-wrap input[type=range]');
  range.value='6';range.dispatchEvent(new w.Event('input',{bubbles:true}));
  await until(()=>Number(card.querySelector('.blini-nexus-progress').getAttribute('aria-valuemax'))>20);
  assert.equal(number('[data-nexus-done]'),'2','changes to recipe settings must not reset Nexus Store');
  card.querySelector('[data-nexus-reset]').click();
  await until(()=>number('[data-nexus-done]')==='0'&&number('[data-nexus-servings]')==='0');
  add.click();await until(()=>number('[data-nexus-done]')==='1');
  w.PreviewApi.resetState();
  await until(()=>number('[data-nexus-done]')==='0');
  add.click(); // callback from the old, disposed Scope must not affect the new one.
  await delay(60);
  assert.equal(number('[data-nexus-done]'),'0');
  assert.equal(d.querySelectorAll('.error,.observablehq--error').length,0);
 }finally{w.PreviewApi.destroy();dom.window.close();}
});
