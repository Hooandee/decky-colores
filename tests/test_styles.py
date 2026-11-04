import json
from pathlib import Path

import pytest

from styles import (
    FORMAT,
    VERSION,
    export_document,
    from_profile,
    import_document,
    merge_styles,
    parse_style,
    remove_style,
    to_profile_changes,
)

PROFILE = {
    "brightness": 70,
    "mode": "solid",
    "color": [255, 128, 0],
    "gradient": [[0, 196, 255], [136, 86, 255]],
    "gradient_speed": 30,
    "effect": {"id": "breathing", "speed": 40, "use_gradient": True},
    "ambilight": {"vividness": 27, "smoothing": 75, "fps": 10, "sampling": "columns", "audio": True},
    "battery_breathe": False,
    "temperature_breathe": True,
}


def test_profile_round_trips_through_the_neutral_style():
    style = from_profile("Atardecer", PROFILE)
    assert style["mode"] == "color"
    assert style["colors"] == [{"r": 255, "g": 128, "b": 0}]
    changes = to_profile_changes(style)
    assert changes["mode"] == "solid"
    assert changes["color"] == [255, 128, 0]
    assert changes["gradient"] == [[0, 196, 255], [136, 86, 255]]
    assert changes["effect"] == {"id": "breathing", "speed": 40, "use_gradient": True}
    assert changes["ambilight"] == {"vividness": 27, "smoothing": 75, "audio": True}
    assert changes["brightness"] == 70
    assert changes["battery_breathe"] is False


@pytest.mark.parametrize("decky,neutral", [("solid", "color"), ("vu", "audio"), ("ambient", "ambilight"), ("clock", "clock")])
def test_modes_use_neutral_names(decky, neutral):
    style = from_profile("x", {**PROFILE, "mode": decky})
    assert style["mode"] == neutral
    assert to_profile_changes(style)["mode"] == decky


def test_android_style_with_per_zone_colors_uses_the_first():
    style = parse_style({"name": "Dos", "mode": "color", "colors": [{"r": 1, "g": 2, "b": 3}, {"r": 4, "g": 5, "b": 6}]})
    assert to_profile_changes(style)["color"] == [1, 2, 3]


def test_partial_style_only_changes_what_it_carries():
    changes = to_profile_changes(parse_style({"name": "Solo modo", "mode": "effect"}))
    assert changes == {"mode": "effect"}


@pytest.mark.parametrize(
    "raw",
    [
        {},
        {"name": "   "},
        {"name": "x" * 41},
        {"name": "ok", "mode": "disco"},
        {"name": "ok", "colors": [{"r": 300, "g": 0, "b": 0}]},
        {"name": "ok", "brightness": "high"},
        "nope",
    ],
)
def test_invalid_styles_are_rejected(raw):
    with pytest.raises(ValueError):
        parse_style(raw)


def test_merge_replaces_by_name_ignoring_case_and_appends_new():
    current = [parse_style({"name": "Rojo", "mode": "color"}), parse_style({"name": "Azul", "mode": "color"})]
    merged, added, replaced = merge_styles(current, [parse_style({"name": "rojo", "mode": "effect"}), parse_style({"name": "Verde"})])
    assert [style["name"] for style in merged] == ["rojo", "Azul", "Verde"]
    assert merged[0]["mode"] == "effect"
    assert (added, replaced) == (1, 1)


def test_remove_style_ignores_case():
    current = [parse_style({"name": "Rojo"}), parse_style({"name": "Azul"})]
    assert [style["name"] for style in remove_style(current, "AZUL")] == ["Rojo"]


def test_export_and_import_document_round_trip():
    styles = [from_profile("Atardecer", PROFILE)]
    document = export_document(styles)
    assert document["format"] == FORMAT and document["version"] == VERSION
    imported, skipped = import_document(json.loads(json.dumps(document)))
    assert imported == styles and skipped == 0


def test_import_skips_invalid_entries_and_rejects_foreign_files():
    imported, skipped = import_document({"format": FORMAT, "version": 1, "styles": [{"name": "Bien"}, {"name": ""}]})
    assert [style["name"] for style in imported] == ["Bien"] and skipped == 1
    with pytest.raises(ValueError):
        import_document({"format": "other", "styles": []})
    with pytest.raises(ValueError):
        import_document({"format": FORMAT, "version": 99, "styles": []})


def test_shared_example_document_is_valid():
    example = json.loads((Path(__file__).parents[1] / "shared" / "golden" / "styles.json").read_text())
    imported, skipped = import_document(example)
    assert skipped == 0 and len(imported) >= 2
