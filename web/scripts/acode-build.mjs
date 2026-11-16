import {resolve} from 'node:path';
import {transformSync} from '@babel/core';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
export const acodeRoot=resolve(import.meta.dirname,'../src/acode');
export const acodeAliases=[
 ...Object.entries({
  'lib/settings':'settings.js','lib/systemConfiguration':'systemConfiguration.js','lib/config':'config.js',
  'lib/editorFile':'editorFile.js','lib/restoreTheme':'restoreTheme.js','utils/helpers':'helpers.js',
  'dialogs/confirm':'confirm.js','settings/searchSettings':'searchSettings.js','components/settingsPage':'settingsPage.js'
 }).map(([name,file])=>({find:new RegExp('^'+name+'$'),replacement:resolve(acodeRoot,'host',file)})),
 ...['cm','components','handlers','lib','utils','palettes','dialogs'].map(name=>({find:new RegExp('^'+name+'/'),replacement:resolve(acodeRoot,'vendor',name)+'/'}))
];
// esbuild gives native paths; Vite generally gives slash-separated module IDs.
export const acodeVendorJSFilter = /[\\/]acode[\\/]vendor[\\/].*\.js$/;
export function transformAcodeJS(code,filename){
 if(!acodeVendorJSFilter.test(filename) || !/<[A-Za-z]/.test(code))return null;
 return transformSync(code,{filename,babelrc:false,configFile:false,plugins:[require.resolve('html-tag-js/jsx/syntax-parser.js'),require.resolve('html-tag-js/jsx/jsx-to-tag.js')],sourceMaps:true});
}
export const acodeJsxPlugin={name:'acode-original-jsx',enforce:'pre',transform(code,id){return transformAcodeJS(code,id.split('?')[0]);}};

// Record modules in the built editor, so provenance is checked against the
// actual runtime graph as well as against files lying in the source tree.
export const acodeRuntimeAudit={name:'acode-runtime-audit',generateBundle(_options,bundle){
 const modules=new Map();
 for(const chunk of Object.values(bundle))if(chunk.type==='chunk')for(const [id,details]of Object.entries(chunk.modules)){
  const path=id.replaceAll('\\','/').split('?')[0];const at=path.indexOf('/src/acode/');
  if(at>=0)modules.set('web'+path.slice(at),details.renderedLength);
 }
 this.emitFile({type:'asset',fileName:'.vite/acode-runtime-modules.json',source:JSON.stringify(Object.fromEntries([...modules].sort()),null,2)+'\n'});
}};
