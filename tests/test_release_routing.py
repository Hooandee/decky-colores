import json
from pathlib import Path


def test_release_please_excludes_non_decky_surfaces():
    root = Path(__file__).resolve().parents[1]
    config = json.loads((root / "release-please-config.json").read_text())
    excluded = set(config["packages"]["."]["exclude-paths"])

    assert excluded == {
        ".github",
        "android",
        "release-please-config.json",
        "shared",
        "tests/test_release_routing.py",
        "windows",
    }
