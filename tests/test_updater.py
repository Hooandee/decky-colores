import json

import self_updater as updater


def test_is_newer_basic():
    assert updater._is_newer("0.15.0", "0.14.0")
    assert updater._is_newer("1.0.0", "0.14.0")
    assert not updater._is_newer("0.14.0", "0.14.0")
    assert not updater._is_newer("0.13.0", "0.14.0")


def test_is_newer_v_prefix():
    assert updater._is_newer("v0.15.0", "0.14.0")
    assert updater._is_newer("v0.15.0", "v0.14.0")
    assert not updater._is_newer("v0.14.0", "0.14.0")


def test_is_newer_dev_suffix():
    # -dev suffix is dropped, so it compares equal to the base version.
    assert not updater._is_newer("0.14.0-dev", "0.14.0")
    assert updater._is_newer("0.15.0-dev", "0.14.0")


def _release_payload(tag="v0.15.0", body="notes here", asset_name="Colores.zip"):
    return {
        "tag_name": tag,
        "body": body,
        "assets": [
            {"name": "other.txt", "browser_download_url": "https://example/other.txt"},
            {"name": asset_name, "browser_download_url": "https://example/Colores.zip"},
        ],
    }


def test_shape_selects_colores_zip(monkeypatch):
    monkeypatch.setattr(updater, "_plugin_name", lambda: "Colores")
    result = updater._shape(_release_payload(), "0.14.0")
    assert result["download_url"] == "https://example/Colores.zip"
    assert result["latest"] == "0.15.0"
    assert result["notes"] == "notes here"
    assert result["has_update"] is True
    assert result["error"] == ""


def test_shape_no_matching_asset(monkeypatch):
    monkeypatch.setattr(updater, "_plugin_name", lambda: "Colores")
    payload = _release_payload(asset_name="NotColores.zip")
    result = updater._shape(payload, "0.14.0")
    assert result["download_url"] == ""
    assert result["has_update"] is False


def test_check_shapes_release(monkeypatch):
    monkeypatch.setattr(updater, "_cache", None)
    monkeypatch.setattr(updater, "read_version", lambda: "0.14.0")
    monkeypatch.setattr(updater, "_repo_slug", lambda: "decky-colores")
    monkeypatch.setattr(updater, "_plugin_name", lambda: "Colores")
    monkeypatch.setattr(
        updater, "_http_get", lambda url, accept: json.dumps(_release_payload()).encode()
    )

    result = updater.check(force=True)
    assert result["current"] == "0.14.0"
    assert result["latest"] == "0.15.0"
    assert result["has_update"] is True
    assert result["download_url"] == "https://example/Colores.zip"
    assert result["error"] == ""


def test_check_network_error(monkeypatch):
    monkeypatch.setattr(updater, "_cache", None)
    monkeypatch.setattr(updater, "read_version", lambda: "0.14.0")
    monkeypatch.setattr(updater, "_repo_slug", lambda: "decky-colores")

    def boom(url, accept):
        raise OSError("no network")

    monkeypatch.setattr(updater, "_http_get", boom)

    result = updater.check(force=True)
    assert result["error"] == "network"
    assert result["has_update"] is False
    assert result["current"] == "0.14.0"


def test_extract_semver_release_please_component_tag():
    # release-please tags this repo as "<package>-v<semver>", not "v<semver>".
    assert updater._extract_semver("decky-colores-v0.14.0") == "0.14.0"
    assert updater._extract_semver("v1.2.3") == "1.2.3"
    assert updater._extract_semver("1.2.3") == "1.2.3"
    assert updater._extract_semver("no-semver-here") == ""


def test_is_newer_component_tag():
    assert updater._is_newer("decky-colores-v0.15.0", "0.14.0")
    assert not updater._is_newer("decky-colores-v0.14.0", "0.14.0")


def test_shape_component_tag(monkeypatch):
    monkeypatch.setattr(updater, "_plugin_name", lambda: "Colores")
    result = updater._shape(_release_payload(tag="decky-colores-v0.15.0"), "0.14.0")
    assert result["latest"] == "0.15.0"
    assert result["has_update"] is True


def test_check_404_is_benign(monkeypatch):
    import urllib.error

    monkeypatch.setattr(updater, "_cache", None)
    monkeypatch.setattr(updater, "read_version", lambda: "0.14.0")
    monkeypatch.setattr(updater, "_repo_slug", lambda: "decky-colores")

    def not_found(url, accept):
        raise urllib.error.HTTPError(url, 404, "Not Found", {}, None)

    monkeypatch.setattr(updater, "_http_get", not_found)
    result = updater.check(force=True)
    # No published release yet is benign — up to date, no error toast.
    assert result["error"] == ""
    assert result["has_update"] is False
    assert result["latest"] == "0.14.0"
