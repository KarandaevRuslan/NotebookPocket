import test from 'node:test';
import assert from 'node:assert/strict';
import {attachmentResolver} from '../src/attachment-resolver.js';
import {JSDOM} from 'jsdom';
const base = 'https://appassets.androidplatform.net/notebook-resources/1234-abcd/';
test('attachment resolver scopes private/shared names and encodes Unicode and spaces', () => {
  const resolve=attachmentResolver(['images/мой файл.png','shared/info.json'],base);
  assert.equal(resolve('images/мой файл.png').href,base+'images/'+encodeURIComponent('мой файл.png'));
  assert.equal(resolve('shared/info.json').href,base+'shared/info.json');assert.equal(resolve('unknown'),null);
  for(const name of ['../file','a/../file','/file','https://other/file','a\\b','a//b']) assert.equal(attachmentResolver([name],base)(name),null);
  assert.throws(()=>attachmentResolver(['x'],'https://other.example/'));
});
test('real Notebook Kit FileAttachment reads personal/shared resources and participates in its runtime', async () => {
  const dom=new JSDOM('',{url:'https://appassets.androidplatform.net/assets/web/preview.html'});
  globalThis.document=dom.window.document;globalThis.window=dom.window;globalThis.location=dom.window.location;
  const {NotebookRuntime}=await import('@observablehq/notebook-kit/runtime');
  const runtime=new NotebookRuntime();const original=globalThis.fetch;
  const data=new Map([[base+'own.txt','personal'],[base+'shared/data.json','{"a":3}']]);
  globalThis.fetch=async url=>new Response(data.get(String(url))??'',{status:data.has(String(url))?200:404});
  try {
    const FileAttachment=runtime.runtime.fileAttachments(attachmentResolver(['own.txt','shared/data.json'],base));
    runtime.main.builtin('FileAttachment',FileAttachment);
    assert.equal(await FileAttachment('own.txt').text(),'personal');assert.deepEqual(await FileAttachment('shared/data.json').json(),{a:3});
    assert.equal(new TextDecoder().decode(await FileAttachment('own.txt').arrayBuffer()),'personal');assert.throws(()=>FileAttachment('other-notebook/secret'));
    runtime.main.define('fromFile',['FileAttachment'],FileAttachment=>FileAttachment('shared/data.json').json());assert.deepEqual(await runtime.main.value('fromFile'),{a:3});
    globalThis.Image=class {set src(url){this.url=url;queueMicrotask(()=>this.onload?.());}};
    const image=await FileAttachment('own.txt').image({width:120});assert.equal(image.url,base+'own.txt');assert.equal(image.width,120);
  } finally {globalThis.fetch=original;runtime.runtime.dispose();dom.window.close();}
});
