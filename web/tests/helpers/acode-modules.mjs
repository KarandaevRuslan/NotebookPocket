import {build} from 'esbuild';
import {mkdir,readFile} from 'node:fs/promises';
import {existsSync} from 'node:fs';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {resolve} from 'node:path';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
import {acodeAliases,transformAcodeJS,acodeVendorJSFilter} from '../../scripts/acode-build.mjs';
export async function acodeModule(entry){
 const root=fileURLToPath(new URL('../../',import.meta.url)),out=resolve(root,'.test-cache/acode-runtime-'+process.pid+'.mjs');
 await mkdir(resolve(root,'.test-cache'),{recursive:true});
 await build({entryPoints:[resolve(root,entry)],outfile:out,bundle:true,platform:'node',format:'esm',packages:'external',
  loader:{'.scss':'empty','.css':'empty','.ttf':'empty'},logLevel:'silent',plugins:[{name:'original-acode',setup(b){
   b.onResolve({filter:/\.css$/},args=>({path:args.path,namespace:'test-style'}));
   b.onLoad({filter:/.*/,namespace:'test-style'},()=>({contents:'',loader:'js'}));
   b.onResolve({filter:/^html-tag-js(?:\/.*)?$/},args=>({path:require.resolve(args.path),external:false}));
   b.onResolve({filter:/^(cm|components|handlers|lib|utils|palettes|dialogs|settings)\//},args=>{
    const rule=acodeAliases.find(r=>r.find.test(args.path));if(!rule)return;
    const path=args.path.replace(rule.find,rule.replacement);
    const file=[path,path+'.js',path+'.ts',path+'/index.js'].find(p=>existsSync(p)&&!p.endsWith('/index')&&/\.(js|ts)$/.test(p));
    if(!file)throw new Error('Unresolved Acode import '+args.path+' -> '+path);return {path:file};
   });
   b.onLoad({filter:acodeVendorJSFilter},async args=>{
    const code=await readFile(args.path,'utf8'),result=transformAcodeJS(code,args.path);return {contents:result?.code||code,loader:'js'};
   });
  }}]});
 return import(pathToFileURL(out));
}
