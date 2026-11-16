import {test} from 'node:test';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {parse} from '@babel/parser';
import {acodeVendorJSFilter, transformAcodeJS} from '../scripts/acode-build.mjs';

for (const filename of [
  '/workspace/Notebook Pocket/src/acode/vendor/components/inputhints/index.js',
  'C:\\Users\\user\\Notebook Pocket\\src\\acode\\vendor\\components\\inputhints\\index.js',
  '\\\\server\\share\\Notebook Pocket\\src\\acode\\vendor\\components\\inputhints\\index.js',
]) {
  test(`Acode JSX is loaded and transformed for ${filename}`, () => {
    assert.equal(acodeVendorJSFilter.test(filename), true);
    const result = transformAcodeJS('const node = <div className="sample">text</div>;', filename);
    assert.ok(result?.code);
    // Parse without the JSX extension: the output must be ordinary JavaScript.
    assert.doesNotThrow(() => parse(result.code, {sourceType: 'module'}));
  });
}
test('the Acode transform leaves host JavaScript and TypeScript to their own loaders', () => {
  assert.equal(transformAcodeJS('const value = 1;', 'C:\\project\\src\\editor.js'), null);
  assert.equal(acodeVendorJSFilter.test('C:\\project\\src\\acode\\vendor\\cm\\editor.ts'), false);
});
test('file URLs decode to native Windows drive and UNC paths without /C: or percent escapes', () => {
  assert.equal(fileURLToPath(new URL('file:///C:/Users/user/Notebook%20Pocket/web/src/preview.js'), {windows: true}),
    'C:\\Users\\user\\Notebook Pocket\\web\\src\\preview.js');
  assert.equal(fileURLToPath(new URL('file://server/share/Notebook%20Pocket/web/src/preview.js'), {windows: true}),
    '\\\\server\\share\\Notebook Pocket\\web\\src\\preview.js');
});
