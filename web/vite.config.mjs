import {defineConfig} from 'vite';
import {resolve} from 'node:path';
import {acodeAliases,acodeJsxPlugin,acodeRuntimeAudit} from './scripts/acode-build.mjs';

/**
 * Build editor and preview independently. Do NOT inline dynamic imports.
 * Notebook Kit's recommended libraries include optional remote imports;
 * forcing every lazy module into one eager bundle can try to load these URLs
 * during Preview startup (blocked by our offline CSP), preventing PreviewApi.
 * Keep lazy JS chunks local, bundle CSS in one eagerly-loaded stylesheet,
 * and verify every emitted asset before packaging into Android.
 */
export default defineConfig(({mode}) => {
  if (!['editor', 'preview'].includes(mode)) {
    throw new Error('Use npm run build (or vite build --mode editor/preview)');
  }
  return {
    base: './',
    plugins:[acodeJsxPlugin,...(mode==='editor'?[acodeRuntimeAudit]:[])],
    resolve: {alias:acodeAliases,dedupe: [
      '@codemirror/state', '@codemirror/view', '@codemirror/language',
      '@codemirror/search', '@codemirror/commands', '@codemirror/autocomplete',
      '@codemirror/lang-javascript', '@lezer/highlight'
    ]},
    // Notebook Kit's optional CDN-backed Inputs loader also imports a remote
    // stylesheet. We load the *real* Inputs CSS from node_modules instead.
    // Drop only this duplicate CDN @import, without relaxing the offline CSP.
    css: {
      postcss: {
        plugins: [{
          postcssPlugin: 'offline-inputs-css',
          AtRule: {
            import: (rule) => {
              if (/cdn\.jsdelivr\.net\/npm\/@observablehq\/inputs\/dist\/index\.css/.test(rule.params)) {
                rule.remove();
              }
            }
          }
        }]
      }
    },
    build: {
      target: 'es2022',
      // Avoid CSS fetches during dynamic imports in Android WebView.
      cssCodeSplit: false,
      manifest: `.vite/manifest-${mode}.json`,
      outDir: 'dist',
      emptyOutDir: mode === 'editor',
      rollupOptions: {
        input: resolve(import.meta.dirname, `${mode}.html`)
      }
    },
    server: {host: '127.0.0.1'}
  };
});
