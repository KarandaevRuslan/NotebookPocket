# NotebookPocket documentation

All developer-facing documentation is written in **English**. The application itself supports **English and Russian**; UI strings and translated sample resources are intentionally separate from this documentation.

## Start here

| Document | Contents |
| --- | --- |
| [Project README](../README.md) | What NotebookPocket does, prerequisites, getting started, attribution. |
| [Architecture](../ARCHITECTURE.md) | Renderable architecture diagrams, Android and web logical modules, data flows, storage and security boundaries. |
| **[Host API reference](API.md)** | `Pocket` preferences/time/timers, `pocketNow`, Notebook Kit attachments, Kotlin resources, `Nexus` / `nexus`, and internal WebView boundaries. |
| [Nexus integration](NEXUS.md) | Practical transactions, Stores, Handlers, scope cleanup and Observable subscriptions. |
| [Building](BUILDING.md) | Toolchain, local validation and device testing. |
| [Publishing](PUBLISHING.md) | Repository setup, tags and GitHub settings. |
| [Releases](RELEASE.md) | CI gates, automatic debug APKs from tags, signed draft releases and keystore setup. |
| [Branding](BRANDING.md) | Icon and social preview assets. |
| [Audit record](AUDIT.md) | Dated code-remediation and verification record. |
| [Nexus validation record](NEXUS-VALIDATION.md) | Historical integration-test results (not live CI status). |

### Key references

- [Observable Notebook Kit](https://github.com/observablehq/notebook-kit): notebook format, compilation and runtime.
- [Acode](https://github.com/Acode-Foundation/Acode): editor components adapted for this application; powered by [CodeMirror 6](https://codemirror.net/).
- [Nexus](https://github.com/KarandaevRuslan/Nexus): Core 0.1.20 is bundled locally in `web/vendor/`.

The source of truth for each API is linked from [API.md](API.md). The [architecture diagram](architecture/system-overview.svg) is also available as [Graphviz source](architecture/system-overview.dot).
