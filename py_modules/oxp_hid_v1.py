# OneXPlayer HID V1 protocol behavior adapted from HueSync/HHD.
# Copyright (c) 2024, honjow; original copyright (c) 2022-2024,
# Steam Deck Homebrew. BSD-3-Clause terms are in huesync/LICENSE.

CMD_ID = 0xB8


def _clamp8(value):
    return max(0, min(255, int(value)))


def command(command_id, payload, index=0x01, size=64):
    base = bytes([command_id, 0x3F, index, *payload])
    return base + bytes(size - len(base) - 2) + bytes([0x3F, command_id])


def brightness_cmd(enabled, code, side=0x00):
    return command(CMD_ID, [0xFD, side, 0x02, int(bool(enabled)), 0x05, code])


def solid_cmd(r, g, b, side=0x00):
    color = [_clamp8(r), _clamp8(g), _clamp8(b)]
    return command(CMD_ID, [0xFE, side, 0x02, *color * 18, color[0], color[1]])


def intercept_cmd(enabled):
    return command(0xB2, [0x03 if enabled else 0x00, 0x01, 0x02])


INITIALIZE = (
    command(
        0xB4,
        bytes.fromhex(
            "0238020101010101000000020102000000030103000000040104000000050105000000"
            "060106000000070107000000080108000000090109000000"
        ),
    ),
    command(
        0xB4,
        bytes.fromhex(
            "02380202010a010a0000000b010b0000000c010c0000000d010d0000000e010e000000"
            "0f010f000000100110000000220200000000230200000000"
        ),
    ),
    intercept_cmd(False),
)
