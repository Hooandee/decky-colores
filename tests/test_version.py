import json
import pathlib

from py_modules.version import read_version

ROOT = pathlib.Path(__file__).resolve().parent.parent


def test_read_version_matches_package_json():
    pkg = json.loads((ROOT / "package.json").read_text())
    assert read_version() == pkg["version"]


def test_read_version_is_semver_like():
    parts = read_version().split(".")
    assert len(parts) == 3
    assert all(p.isdigit() for p in parts)
