import json
from pathlib import Path


def test_release_please_excludes_non_decky_surfaces():
    root = Path(__file__).resolve().parents[1]
    config = json.loads((root / "release-please-config.json").read_text())
    excluded = set(config["packages"]["."]["exclude-paths"])

    assert excluded == {
        ".github",
        "android",
        "shared",
        "tests",
        "windows",
    }


def test_release_history_starts_after_android_foundation():
    root = Path(__file__).resolve().parents[1]
    config = json.loads((root / "release-please-config.json").read_text())

    assert config["last-release-sha"] == "af885f14f8756734a72bfac876084585bf4ad056"
