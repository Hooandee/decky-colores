from py_modules.saved_gradients import MAX_SAVED, remove_gradient, upsert_gradient


def test_upsert_appends_new():
    result = upsert_gradient([], "Sunset", [(255, 0, 0), (0, 0, 255)])
    assert result == [{"name": "Sunset", "stops": [[255, 0, 0], [0, 0, 255]]}]


def test_upsert_appends_multiple():
    saved = upsert_gradient([], "A", [(1, 1, 1)])
    saved = upsert_gradient(saved, "B", [(2, 2, 2)])
    assert [g["name"] for g in saved] == ["A", "B"]


def test_upsert_overwrites_same_name():
    saved = upsert_gradient([], "A", [(1, 1, 1)])
    saved = upsert_gradient(saved, "A", [(9, 9, 9)])
    assert len(saved) == 1
    assert saved[0]["stops"] == [[9, 9, 9]]


def test_upsert_accepts_lists_and_tuples():
    result = upsert_gradient([], "A", [[10, 20, 30]])
    assert result[0]["stops"] == [[10, 20, 30]]


def test_remove_removes_by_name():
    saved = upsert_gradient([], "A", [(1, 1, 1)])
    saved = upsert_gradient(saved, "B", [(2, 2, 2)])
    saved = remove_gradient(saved, "A")
    assert [g["name"] for g in saved] == ["B"]


def test_remove_missing_is_noop():
    saved = upsert_gradient([], "A", [(1, 1, 1)])
    assert remove_gradient(saved, "Z") == saved


def test_cap_drops_oldest():
    saved = []
    for i in range(MAX_SAVED + 5):
        saved = upsert_gradient(saved, f"g{i}", [(i % 256, 0, 0)])
    assert len(saved) == MAX_SAVED
    assert saved[0]["name"] == "g5"
    assert saved[-1]["name"] == f"g{MAX_SAVED + 4}"
