/**
 * Prevent the error: "Unrecognized extension value" from multiple
 * @codemirror/state or @codemirror/view copies. Checks actual installed paths.
 */
import {spawnSync} from 'node:child_process';

for (const pkg of ['@codemirror/state', '@codemirror/view']) {
  const cmd = process.platform === 'win32' ? 'npm.cmd' : 'npm';
  const result = spawnSync(cmd, ['ls', '--all', '--parseable', pkg], {
    encoding: 'utf8',
    shell: process.platform === 'win32'
  });
  const suffix = `/node_modules/${pkg}`;
  const paths = (result.stdout || '').split(/\r?\n/)
    .map(line => line.trim().replace(/\\/g, '/'))
    .filter(line => line.endsWith(suffix));
  const unique = [...new Set(paths)];
  if (result.status !== 0 || unique.length !== 1) {
    console.error(`[CodeMirror] Expected one ${pkg}, found ${unique.length}:`);
    console.error(unique.join('\n') || result.stderr || 'No dependency installed');
    console.error('Run npm install and npm dedupe in the web directory.');
    process.exitCode = 1;
  } else {
    console.log(`[CodeMirror] Single ${pkg} instance: ${unique[0]}`);
  }
}
