# Third-party licenses and attribution

NotebookPocket is an independent project. Its MIT license covers **original NotebookPocket code**; it does not replace upstream licenses or convey ownership of reused software.

| Upstream project | Role | Attribution & license |
| --- | --- | --- |
| [Observable Notebook Kit 2.x](https://github.com/observablehq/notebook-kit) | Notebooks 2.0 file format and executable runtime | ISC; installed by npm, license supplied with the package |
| [Acode](https://github.com/Acode-Foundation/Acode) | **Adapted source editor**, commands and UI behavior | MIT; source provenance in `third_party/acode/`, license in `third_party/acode/LICENSE` |
| [CodeMirror 6](https://codemirror.net/) | Editor engine, highlighting and extensions | MIT; package notice and version via `web/package-lock.json` |
| [Observable Inputs](https://github.com/observablehq/inputs) | Input controls and styles in preview | ISC; npm dependency notice |
| [Nexus Core 0.1.20](docs/NEXUS.md) | Reactive Store / Dispatcher / Handler integration | Locally packed npm dependency (`web/vendor/nexus-0.1.20.tgz`); license and packaging notes under `third_party/nexus/` |
| Roboto Mono | Bundled editor font | SIL Open Font License in `third_party/acode/RobotoMono-OFL.txt` |
| Gradle wrapper | Gradle launch scripts/binaries | Wrapper-specific notices in `third_party/gradle` |
| AndroidX / Kotlin and other npm packages | Android and web runtime dependencies | Respective upstream package licenses; versions locked in Gradle and npm dependencies |

Upstream Acode snapshots and adaptation records are **deliberate source files**; do not delete their copyright notices. For a binary distribution, review licenses and notice obligations for every resolved dependency. The repository's new app artwork does not include upstream logos or trademarks.
