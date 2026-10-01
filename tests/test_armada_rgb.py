import json
import threading
import time

import pytest

import armada_rgb
from armada_rgb import (
    ArmadaProfileError,
    ArmadaRgbDevice,
    catalog_for_root,
    layout_for_profile,
    profile_for_model,
    validate_catalog,
)


def _make_node(root, name, *, multicolor=False, order="red green blue", maximum=255):
    node = root / name
    node.mkdir(parents=True)
    (node / "brightness").write_text("0")
    (node / "max_brightness").write_text(str(maximum))
    if multicolor:
        (node / "multi_index").write_text(order)
        (node / "multi_intensity").write_text("0 0 0")
    return node


def _channel_backend(zone_count=1):
    targets = []
    for channel in ("red", "green", "blue"):
        targets.extend(f"{channel}={channel}:indicator-{index}" for index in range(zone_count))
    return {"type": "channels", "targets": targets}


def test_bundled_catalog_covers_all_current_armada_models():
    expected = {
        "AYN Odin 2 Portal",
        "AYN Odin 2",
        "AYN Odin 3",
        "AYN Thor",
        "AYN Thor Lite",
        "Retroid Pocket 5",
        "Retroid Pocket 5 Visionox",
        "Retroid Pocket Flip2",
        "Retroid Pocket Flip2 Visionox",
        "Retroid Pocket 6",
        "Retroid Pocket 6 TOP-DPAD",
        "Retroid Pocket Nova",
        "KONKR Pocket FIT Elite",
        "MANGMI Air Y Pro",
        "MANGMI Pocket Max",
    }
    catalog = catalog_for_root("/path/that/does/not/exist")
    actual = {model for profile in catalog["profiles"] for model in profile["models"]}
    assert actual == expected


def test_system_catalog_takes_precedence(tmp_path):
    path = tmp_path / "usr/share/armada-rgb/profiles.json"
    path.parent.mkdir(parents=True)
    path.write_text(json.dumps({
        "version": 1,
        "profiles": [{
            "models": ["Future Handheld"],
            "backend": {"type": "multicolor", "targets": ["future:rgb"]},
        }],
    }))

    assert profile_for_model("Future Handheld", str(tmp_path))["models"] == ["Future Handheld"]
    assert profile_for_model("AYN Odin 3", str(tmp_path)) is None


def test_invalid_system_catalog_falls_back_to_bundled(tmp_path, caplog):
    path = tmp_path / "usr/share/armada-rgb/profiles.json"
    path.parent.mkdir(parents=True)
    path.write_text('{"version": 2, "profiles": []}')

    assert profile_for_model("AYN Odin 3", str(tmp_path)) is not None
    assert "Ignoring invalid Armada RGB catalog" in caplog.text


@pytest.mark.parametrize(
    "catalog",
    [
        {"version": 2, "profiles": []},
        {"version": True, "profiles": []},
        {"version": 1, "profiles": [], "extra": True},
        {
            "version": 1,
            "profiles": [{
                "models": ["Unsafe"],
                "backend": {"type": "multicolor", "targets": ["../brightness"]},
            }],
        },
        {
            "version": 1,
            "profiles": [{
                "models": ["Uneven"],
                "backend": {
                    "type": "channels",
                    "targets": ["red=r:1", "green=g:1", "blue=b:1", "red=r:2"],
                },
            }],
        },
        {
            "version": 1,
            "profiles": [
                {"models": ["Duplicate"], "backend": {"type": "multicolor", "targets": ["rgb:a"]}},
                {"models": ["Duplicate"], "backend": {"type": "multicolor", "targets": ["rgb:b"]}},
            ],
        },
    ],
)
def test_catalog_validation_rejects_unsafe_or_ambiguous_data(catalog):
    with pytest.raises(ArmadaProfileError):
        validate_catalog(catalog)


def test_channel_backend_matches_armada_gamma_and_brightness_vector(tmp_path):
    backend = _channel_backend()
    for target in backend["targets"]:
        _make_node(tmp_path, target.split("=", 1)[1])
    device = ArmadaRgbDevice(str(tmp_path), backend)

    assert device.available is True
    assert device.apply_solid((255, 128, 0), 25, True) is True
    assert (tmp_path / "red:indicator-0/brightness").read_text().strip() == "64"
    assert (tmp_path / "green:indicator-0/brightness").read_text().strip() == "14"
    assert (tmp_path / "blue:indicator-0/brightness").read_text().strip() == "0"


def test_channel_backend_preserves_independent_zones(tmp_path):
    backend = _channel_backend(zone_count=2)
    for target in backend["targets"]:
        _make_node(tmp_path, target.split("=", 1)[1])
    device = ArmadaRgbDevice(str(tmp_path), backend)

    assert device.apply_zones([(255, 0, 0), (0, 255, 0)], 100, True) is True
    assert (tmp_path / "red:indicator-0/brightness").read_text().strip() == "255"
    assert (tmp_path / "green:indicator-0/brightness").read_text().strip() == "0"
    assert (tmp_path / "red:indicator-1/brightness").read_text().strip() == "0"
    assert (tmp_path / "green:indicator-1/brightness").read_text().strip() == "255"


def test_multicolor_backend_uses_node_order_maximum_and_per_zone_color(tmp_path):
    _make_node(tmp_path, "rgb:left", multicolor=True, order="blue green red", maximum=100)
    _make_node(tmp_path, "rgb:right", multicolor=True, order="red green blue", maximum=200)
    backend = {"type": "multicolor", "targets": ["rgb:left", "rgb:right"]}
    device = ArmadaRgbDevice(str(tmp_path), backend)

    assert device.apply_zones([(255, 0, 0), (0, 255, 0)], 50, True) is True
    assert (tmp_path / "rgb:left/multi_intensity").read_text().strip() == "0 0 100"
    assert (tmp_path / "rgb:left/brightness").read_text().strip() == "50"
    assert (tmp_path / "rgb:right/multi_intensity").read_text().strip() == "0 200 0"
    assert (tmp_path / "rgb:right/brightness").read_text().strip() == "100"


def test_konkr_correction_only_triggers_when_red_is_present(tmp_path):
    _make_node(tmp_path, "konkr:rgb:joysticks", multicolor=True)
    profile = profile_for_model("KONKR Pocket FIT Elite", str(tmp_path))
    device = ArmadaRgbDevice(str(tmp_path), profile["backend"], profile["correction"])

    assert device.apply_solid((255, 255, 255), 100, True) is True
    assert (tmp_path / "konkr:rgb:joysticks/multi_intensity").read_text().strip() == "255 154 154"
    assert device.apply_solid((0, 255, 255), 100, True) is True
    assert (tmp_path / "konkr:rgb:joysticks/multi_intensity").read_text().strip() == "0 255 255"


def test_partial_write_failure_blanks_every_multicolor_target(tmp_path, monkeypatch):
    for name in ("rgb:left", "rgb:right"):
        node = _make_node(tmp_path, name, multicolor=True)
        (node / "brightness").write_text("255")
    device = ArmadaRgbDevice(
        str(tmp_path),
        {"type": "multicolor", "targets": ["rgb:left", "rgb:right"]},
    )
    real_write = armada_rgb._write_open_file
    failed = False

    def fail_once(handle, value):
        nonlocal failed
        if value == "0 255 0" and not failed:
            failed = True
            raise OSError("simulated sysfs failure")
        real_write(handle, value)

    monkeypatch.setattr(armada_rgb, "_write_open_file", fail_once)

    assert device.apply_zones([(255, 0, 0), (0, 255, 0)], 100, True) is False
    assert failed is True
    assert all((tmp_path / name / "brightness").read_text().strip() == "0" for name in ("rgb:left", "rgb:right"))


def test_power_off_blanks_channel_targets_without_touching_unrelated_leds(tmp_path):
    backend = _channel_backend()
    for target in backend["targets"]:
        node = _make_node(tmp_path, target.split("=", 1)[1])
        (node / "brightness").write_text("255")
    power_led = _make_node(tmp_path, "power-led")
    (power_led / "brightness").write_text("1")
    device = ArmadaRgbDevice(str(tmp_path), backend)

    assert device.apply_solid((255, 255, 255), 100, False) is True
    for target in backend["targets"]:
        name = target.split("=", 1)[1]
        assert (tmp_path / name / "brightness").read_text().strip() == "0"
    assert (power_led / "brightness").read_text() == "1"


def test_known_side_layouts_share_ambilight_by_side():
    profile = profile_for_model("AYN Odin 3")
    layout = layout_for_profile("AYN Odin 3", profile, 8)

    assert layout[0]["zones"] == [0, 1, 2, 3]
    assert layout[1]["zones"] == [4, 5, 6, 7]
    assert {group["kind"] for group in layout} == {"shared-edge"}


def test_odin3_eight_zone_channel_frames_are_serialized(tmp_path, monkeypatch):
    profile = profile_for_model("AYN Odin 3")
    for target in profile["backend"]["targets"]:
        _make_node(tmp_path, target.split("=", 1)[1])
    device = ArmadaRgbDevice(str(tmp_path), profile["backend"])
    second_device = ArmadaRgbDevice(str(tmp_path), profile["backend"])
    assert device.zone_count == 8
    assert len(profile["backend"]["targets"]) == 24
    original_write = armada_rgb._write_open_file
    guard = threading.Lock()
    active = 0
    maximum_active = 0

    def tracked_write(handle, value):
        nonlocal active, maximum_active
        with guard:
            active += 1
            maximum_active = max(maximum_active, active)
        time.sleep(0.001)
        try:
            original_write(handle, value)
        finally:
            with guard:
                active -= 1

    monkeypatch.setattr(armada_rgb, "_write_open_file", tracked_write)
    frame_a = [(255, 0, 0)] * 8
    frame_b = [(0, 0, 255)] * 8
    results = []
    workers = [
        threading.Thread(target=lambda frame=frame: results.append(
        (device if frame is frame_a else second_device).apply_zones(frame, 100, True)
        ))
        for frame in (frame_a, frame_b)
    ]
    for worker in workers:
        worker.start()
    for worker in workers:
        worker.join()

    assert results == [True, True]
    assert maximum_active == 1


def test_unknown_geometry_uses_safe_global_ambilight_layout():
    profile = profile_for_model("MANGMI Pocket Max")
    layout = layout_for_profile("MANGMI Pocket Max", profile, 16)

    assert layout == [{
        "name": "Lights",
        "region": [0.0, 0.0, 1.0, 1.0],
        "zones": list(range(16)),
        "kind": "shared-full",
    }]
