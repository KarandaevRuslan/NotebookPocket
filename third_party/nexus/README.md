# Nexus 0.1.20

- Project: https://github.com/KarandaevRuslan/Nexus
- Release: https://github.com/KarandaevRuslan/Nexus/releases/tag/v0.1.20
- License: MIT; preserved in `LICENSE.md` and in the dependency package.
- Local dependency: `web/vendor/nexus-0.1.20.tgz` (also recorded by npm lockfile integrity).
- SHA-256: `dcd77a9a845c1a9874d1a85a00e7e197a5710ba42e27f1db8e551cc9925f6e42`.

The package was built from the user-supplied `nexus(3).zip` source snapshot,
whose `package.json` identifies version 0.1.20. It is not claimed to be byte-identical
to the GitHub release asset. GitHub retrieval was unavailable in the integration
environment. No Nexus source files were modified.

The supplied snapshot omits example modules referenced by its tests, so the
original project-wide `npm run build` cannot finish. Only package source was
compiled with this temporary configuration in the Nexus root:

```json
{
  "extends": "./tsconfig.json",
  "include": ["src/**/*.ts"],
  "exclude": ["tests", "examples"]
}
```

After `npm ci`, use `npx tsc -p tsconfig.package.json` and
`npm pack --ignore-scripts`. The upstream `files` allowlist packs `dist/src`,
package metadata, README, architecture, license and icon. Tests and examples
are not distributed. The local package includes the upstream public entry
points and declarations; NotebookPocket imports only `nexus/core`. No Frida
adapters enter the preview bundle. Do not replace this dependency with the
unrelated public npm package named `nexus`.

For an upgrade, review the chosen release archive, replace the vendored package,
update this provenance and its SHA-256, run npm install on the local tarball to
refresh the lockfile, then run web tests, the production build and Android checks.
