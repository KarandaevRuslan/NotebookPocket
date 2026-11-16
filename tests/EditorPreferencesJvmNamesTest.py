#!/usr/bin/env python3
"""Catch Kotlin/JVM property setter collisions before Android compilation."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
prefs = (root / "app/src/main/java/dev/pocket/notebook/settings/EditorPreferences.kt").read_text(encoding="utf-8")
# properties with generated JVM setters (including properties with private setters)
properties = set(re.findall(r"\bvar\s+(\w+)\s+by\s+", prefs))
functions = set(re.findall(r"\bfun\s+(\w+)\s*\(", prefs))
conflicts = {"set" + name[0].upper() + name[1:] for name in properties} & functions
assert not conflicts, f"JVM setter collisions: {sorted(conflicts)}"
settings = (root / "app/src/main/java/dev/pocket/notebook/feature/settings/EditorSettingsSection.kt").read_text(encoding="utf-8")
for name in ("updateFontSize", "updateWordWrap", "updateAutocomplete", "updateQuickTools"):
    assert name in functions, f"missing preferences function {name}"
    assert "preferences." + name in settings or "preferences::" + name in settings, f"missing call to {name}"
print("EditorPreferences JVM naming regression test: OK")
