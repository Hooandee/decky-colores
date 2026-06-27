from enum import Enum
from typing import Tuple


class RGBMode(Enum):
    Disabled = "disabled"
    Solid = "solid"
    Rainbow = "rainbow"
    Pulse = "pulse"  # Breathing effect | 呼吸效果
    Spiral = "spiral"  # Rotating effect | 旋转效果
    Duality = "duality"  # Dual-color alternating pulse | 双色交替呼吸
    Gradient = "gradient"  # Dual-color gradient transition | 双色渐变过渡
    Battery = "battery"

    # OneXPlayer/AOKZOE preset modes
    # OneXPlayer/AOKZOE预设模式
    OXP_MONSTER_WOKE = "oxp_monster_woke"
    OXP_FLOWING = "oxp_flowing"
    OXP_SUNSET = "oxp_sunset"
    OXP_NEON = "oxp_neon"
    OXP_DREAMY = "oxp_dreamy"
    OXP_CYBERPUNK = "oxp_cyberpunk"
    OXP_COLORFUL = "oxp_colorful"
    OXP_AURORA = "oxp_aurora"
    OXP_SUN = "oxp_sun"
    OXP_CLASSIC = "oxp_classic"  # OXP Cherry Red (0xB7, 0x30, 0x00)

    # MSI specific preset modes
    # MSI 专属预设模式
    MSI_FROSTFIRE = "msi_frostfire"  # A Song of Ice and Fire | 冰火之歌

    # Zotac specific preset modes
    ZOTAC_STARS = "zotac_stars"
    ZOTAC_FLASH = "zotac_flash"
    ZOTAC_WINK = "zotac_wink"
    ZOTAC_RANDOM = "zotac_random"


class Color:
    """
    RGB color class with support for multiple initialization methods.
    支持多种初始化方法的 RGB 颜色类。
    """

    def __init__(self, r: int, g: int, b: int):
        self.R = self._validate_color_value(r)
        self.G = self._validate_color_value(g)
        self.B = self._validate_color_value(b)

    @classmethod
    def from_hex(cls, hex_str: str) -> 'Color':
        hex_str = hex_str.lstrip('#')
        r = int(hex_str[0:2], 16)
        g = int(hex_str[2:4], 16)
        b = int(hex_str[4:6], 16)
        return cls(r, g, b)

    @classmethod
    def from_hsv(cls, h: float, s: float, v: float) -> 'Color':
        h = h % 360
        c = v * s
        x = c * (1 - abs((h / 60) % 2 - 1))
        m = v - c

        if 0 <= h < 60:
            r, g, b = c, x, 0
        elif 60 <= h < 120:
            r, g, b = x, c, 0
        elif 120 <= h < 180:
            r, g, b = 0, c, x
        elif 180 <= h < 240:
            r, g, b = 0, x, c
        elif 240 <= h < 300:
            r, g, b = x, 0, c
        else:  # 300 <= h < 360
            r, g, b = c, 0, x

        r_int = int((r + m) * 255)
        g_int = int((g + m) * 255)
        b_int = int((b + m) * 255)

        return cls(r_int, g_int, b_int)

    def _validate_color_value(self, value: int) -> int:
        if 0 <= value <= 255:
            return value
        raise ValueError(f"Color values must be between 0 and 255, got {value}")

    def to_hex(self) -> str:
        return f"{self.R:02X}{self.G:02X}{self.B:02X}"

    def hex(self) -> str:
        return self.to_hex()

    def to_hsv(self) -> Tuple[float, float, float]:
        r, g, b = self.R / 255.0, self.G / 255.0, self.B / 255.0
        max_val = max(r, g, b)
        min_val = min(r, g, b)
        diff = max_val - min_val

        v = max_val
        s = 0 if max_val == 0 else diff / max_val

        if diff == 0:
            h = 0
        elif max_val == r:
            h = 60 * (((g - b) / diff) % 6)
        elif max_val == g:
            h = 60 * (((b - r) / diff) + 2)
        else:  # max_val == b
            h = 60 * (((r - g) / diff) + 4)

        return (h, s, v)

    def __str__(self) -> str:
        return f"Color(R={self.R}, G={self.G}, B={self.B})"

    def __repr__(self) -> str:
        return f"Color({self.R}, {self.G}, {self.B})"

    def __eq__(self, other) -> bool:
        if not isinstance(other, Color):
            return False
        return self.R == other.R and self.G == other.G and self.B == other.B
