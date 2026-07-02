from py_modules.asus_ally_hid import (
    buf,
    brightness_cmd,
    zone_cmd,
    init_cmds,
    set_apply_cmds,
    pct_to_level,
    speed_to_code,
    mode_code,
    MODE_SOLID,
)


def test_buf_pads_to_64():
    assert buf([0x5D, 0xB4]) == bytes([0x5D, 0xB4]) + bytes(62)
    assert len(buf([0x01])) == 64


def test_brightness_cmd_levels():
    assert brightness_cmd(3) == bytes.fromhex("5abac5c403") + bytes(59)
    assert brightness_cmd(0)[:5] == bytes.fromhex("5abac5c400")


def test_pct_to_level_quantization():
    assert pct_to_level(0) == 0
    assert pct_to_level(20) == 1
    assert pct_to_level(50) == 2
    assert pct_to_level(80) == 3
    assert pct_to_level(100) == 3


def test_speed_to_code():
    assert speed_to_code(10) == 0xE1
    assert speed_to_code(50) == 0xEB
    assert speed_to_code(90) == 0xF5


def test_mode_code_mapping():
    assert mode_code("solid") == 0
    assert mode_code("breathing") == 1
    assert mode_code("rainbow") == 2
    assert mode_code("cycle") == 2
    assert mode_code("wave") == 3
    assert mode_code("spiral") == 3


def test_zone_cmd_solid_red():
    assert zone_cmd(1, MODE_SOLID, 255, 0, 0) == (
        bytes.fromhex("5db30100ff0000000000000000") + bytes(51)
    )


def test_init_cmds_is_asus_handshake():
    (init,) = init_cmds()
    assert init[:15] == bytes([0x5D]) + b"ASUS Tech.Inc."
    assert len(init) == 64


def test_set_apply_order():
    reps = set_apply_cmds()
    assert reps[0][:2] == bytes([0x5D, 0xB5])  # SET
    assert reps[1][:2] == bytes([0x5D, 0xB4])  # APPLY
