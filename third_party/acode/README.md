# Acode editor provenance

The editor snapshot was supplied from Acode-Foundation/Acode at commit
`6962af1e805b8996e743df97cad4df3ecef687e5`. The MIT notice is in `LICENSE`;
Roboto Mono's SIL Open Font License is in `RobotoMono-OFL.txt`.

- `full-editor-manifest.json` records original/local SHA-256 values for the 68 imported files.
- `upstream/` preserves the original source used by this integration.
- `patches/` contains reviewed adaptation diffs.
- `editor-source-inventory.json` records the source classification.
- The import/projection scripts are under the project root `web/scripts`.

`npm run check:acode` verifies hashes, projected manager functions and native core
method bodies. `npm run build` checks that imported JS/TS modules participate in
the real editor bundle. `web/src/acode/host` contains NotebookPocket-owned service
adapters. This is an editor integration, not the complete Cordova IDE.

Keep snapshots, patches and notices when changing the integration. Do not modify
upstream copies merely to reformat or reorganize NotebookPocket host code.
