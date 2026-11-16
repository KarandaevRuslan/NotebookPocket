"""Documentation regression checks: host contracts, language and reviewed diagram."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DOCS = [ROOT / "README.md", ROOT / "ARCHITECTURE.md", ROOT / "NEXUS_PANCAKES_PATCH.md"] + list((ROOT / "docs").glob("*.md"))


class DocumentationReferenceTest(unittest.TestCase):
    def test_developer_markdown_is_english(self):
        for path in DOCS:
            with self.subTest(path=path.relative_to(ROOT)):
                self.assertNotRegex(path.read_text(encoding="utf-8"), r"[\u0400-\u052f]")

    def test_documented_public_host_api_matches_source_names(self):
        reference = (ROOT / "docs/API.md").read_text(encoding="utf-8")
        pocket = (ROOT / "web/src/pocket-context.js").read_text(encoding="utf-8")
        nexus = (ROOT / "web/src/preview/nexusRuntime.js").read_text(encoding="utf-8")
        runtime = (ROOT / "web/src/preview/notebookRuntime.js").read_text(encoding="utf-8")
        for name in ["Pocket.theme", "Pocket.language", "Pocket.locale", "Pocket.timeZone", "Pocket.hourCycle", "Pocket.now()", "Pocket.timer", "pocketNow", "FileAttachment", "nexus.observe", "nexus.scope"]:
            with self.subTest(name=name):
                self.assertIn(name, reference)
        self.assertIn("elapsedRealtime", pocket)
        self.assertIn("timestamp:wall", pocket)
        self.assertIn("observe(scope, store)", nexus)
        self.assertIn("scope(invalidation)", nexus)
        self.assertIn("'FileAttachment'", runtime)
        self.assertIn("'pocketNow'", runtime)

    def test_architecture_diagram_and_its_source_exist(self):
        svg = ROOT / "docs/architecture/system-overview.svg"
        dot = ROOT / "docs/architecture/system-overview.dot"
        ET.parse(svg)
        self.assertIn("digraph NotebookPocket", dot.read_text(encoding="utf-8"))
        self.assertIn("```mermaid", (ROOT / "ARCHITECTURE.md").read_text(encoding="utf-8"))

    def test_local_markdown_links_resolve(self):
        for file in DOCS:
            content = file.read_text(encoding="utf-8")
            for destination in re.findall(r"!?(?:\[[^\]]*\])\(([^)]+)\)", content):
                link = destination.strip().split("#", 1)[0]
                if not link or link.startswith(("http://", "https://", "mailto:")):
                    continue
                with self.subTest(file=file.relative_to(ROOT), href=destination):
                    self.assertTrue((file.parent / link).exists())


if __name__ == "__main__":
    unittest.main()
