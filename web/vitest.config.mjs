import {defineConfig} from 'vitest/config';
import {acodeAliases,acodeJsxPlugin} from './scripts/acode-build.mjs';
export default defineConfig({plugins:[acodeJsxPlugin],resolve:{alias:acodeAliases,dedupe:['@codemirror/state','@codemirror/view']},test:{include:['tests/upstream/all.test.js'],environment:'jsdom',pool:'threads',maxWorkers:1,minWorkers:1,isolate:false}});
