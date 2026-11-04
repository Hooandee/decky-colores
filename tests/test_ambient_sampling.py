import json
from pathlib import Path

import pytest

from ambient_sampling import (
    ADOPT_FRAMES,
    RELEASE_FRAMES,
    ContentBoxTracker,
    content_box,
    region_color,
    region_pixels,
)

GOLDEN = json.loads(
    (Path(__file__).parents[1] / "shared" / "golden" / "ambient-sampling.json").read_text()
)


def _frame(vector):
    return bytes(channel for pixel in vector["input"]["pixels"] for channel in pixel)


@pytest.mark.parametrize("vector", GOLDEN, ids=[vector["id"] for vector in GOLDEN])
def test_matches_shared_golden_vectors(vector):
    width = vector["input"]["width"]
    height = vector["input"]["height"]
    frame = _frame(vector)
    if vector["operation"] == "content_box":
        expected = vector["expected"]
        assert content_box(frame, width, height) == (
            expected["left"],
            expected["top"],
            expected["right"],
            expected["bottom"],
        )
    else:
        color = vector["expected"]["color"]
        assert region_color(frame, width, 0, 0, width, height) == (color["r"], color["g"], color["b"])


def test_tracker_crops_quickly_and_restores_slowly():
    tracker = ContentBoxTracker()
    cropped = (0, 2, 10, 8)
    tracker.update((0, 0, 10, 10))
    for _ in range(ADOPT_FRAMES - 1):
        assert tracker.update(cropped) == (0, 0, 10, 10)
    assert tracker.update(cropped) == cropped
    for _ in range(RELEASE_FRAMES - 1):
        assert tracker.update((0, 0, 10, 10)) == cropped
    assert tracker.update((0, 0, 10, 10)) == (0, 0, 10, 10)


def test_tracker_ignores_a_flickering_box():
    tracker = ContentBoxTracker()
    tracker.update((0, 0, 10, 10))
    for _ in range(10):
        tracker.update((0, 2, 10, 8))
        tracker.update((0, 1, 10, 9))
    assert tracker.box == (0, 0, 10, 10)


def test_region_pixels_maps_normalized_regions_into_the_content_box():
    box = (2, 1, 12, 9)
    assert region_pixels(box, (0.0, 0.0, 0.5, 1.0)) == (2, 1, 7, 9)
    assert region_pixels(box, (0.5, 0.72, 1.0, 1.0)) == (7, 6, 12, 9)
    assert region_pixels(box, (0.99, 0.99, 1.0, 1.0)) == (11, 8, 12, 9)


def test_tracker_grows_back_even_when_the_bigger_detections_flicker():
    tracker = ContentBoxTracker()
    tracker.update((0, 2, 10, 8))
    for index in range(RELEASE_FRAMES):
        tracker.update((0, 0, 10, 10) if index % 2 == 0 else (0, 1, 10, 9))
    assert tracker.box != (0, 2, 10, 8)
