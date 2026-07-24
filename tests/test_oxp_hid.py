from py_modules.oxp_hid import (
    CMD_ID,
    buf,
    brightness_cmd,
    solid_cmd,
    mode_cmd,
    level_code,
    mode_value,
)


def test_buf_frames_and_pads_to_64():
    packet = buf([0xFE, 0x00])
    assert packet[:3] == bytes([CMD_ID, 0xFF, 0xFE])
    assert len(packet) == 64
    assert packet[4:] == bytes(60)


def test_brightness_cmd_enable_high():
    packet = brightness_cmd(True, 0x04)
    assert packet[:6] == bytes([CMD_ID, 0xFF, 0xFD, 0x01, 0x05, 0x04])
    assert len(packet) == 64


def test_brightness_cmd_disable():
    packet = brightness_cmd(False, 0x04)
    assert packet[:6] == bytes([CMD_ID, 0xFF, 0xFD, 0x00, 0x05, 0x04])


def test_solid_cmd_repeats_triple_20_times():
    packet = solid_cmd(255, 0, 0)
    assert packet[:3] == bytes([CMD_ID, 0xFF, 0xFE])
    payload = packet[3 : 3 + 60]
    triples = [tuple(payload[i : i + 3]) for i in range(0, 60, 3)]
    assert triples == [(255, 0, 0)] * 20
    assert packet[63] == 0x00


def test_solid_cmd_clamps_channels():
    packet = solid_cmd(300, -5, 128)
    assert tuple(packet[3:6]) == (255, 0, 128)


def test_level_code_quantization():
    assert level_code(0) == 0x01
    assert level_code(20) == 0x01
    assert level_code(50) == 0x03
    assert level_code(100) == 0x04


def test_mode_value_mapping_defaults_to_aurora():
    assert mode_value("aurora") == 0x01
    assert mode_value("flowing") == 0x03
    assert mode_value("neon") == 0x05
    assert mode_value("unknown-effect") == 0x01


def test_mode_cmd_bytes():
    packet = mode_cmd(0x03)
    assert packet[:3] == bytes([CMD_ID, 0xFF, 0x03])
    assert len(packet) == 64
