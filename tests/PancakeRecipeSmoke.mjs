/** Keep one executable recipe test suite: use the real Observable runtime and attachment fixture. */
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const result = spawnSync(process.execPath, ['--test', fileURLToPath(new URL('../web/tests/pancake-demo.test.mjs', import.meta.url))], {stdio: 'inherit'});
if (result.error) throw result.error;
process.exitCode = result.status ?? 1;
