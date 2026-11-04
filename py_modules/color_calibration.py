import math

STEPS = ("yellow", "orange", "white")
STEP_LIMITS = {"yellow": 60, "orange": 100, "white": 60}
NEUTRAL = {step: 0 for step in STEPS}
ORANGE_GAMMA_BASE = 2.5


def parse_steps(raw):
    if not isinstance(raw, dict):
        raise ValueError("invalid color calibration")
    steps = {}
    for step in STEPS:
        value = raw.get(step, 0)
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
            raise ValueError("invalid color calibration step")
        limit = STEP_LIMITS[step]
        steps[step] = max(-limit, min(limit, int(round(value))))
    return steps


def channel_curves(steps):
    yellow, orange, white = (steps[step] for step in STEPS)
    gains = [100.0 - max(0, yellow), 100.0 + min(0, yellow), 100.0]
    if white < 0:
        gains[2] += white
    else:
        gains[0] *= (100 - white) / 100
        gains[1] *= (100 - white) / 100
    gammas = [1.0, ORANGE_GAMMA_BASE ** (-orange / 100), 1.0]
    return gains, gammas


def _table(gain, gamma):
    return tuple(
        math.floor(255 * gain / 100 * (value / 255) ** gamma + 0.5) for value in range(256)
    )


class Calibration:
    def __init__(self, steps):
        self.steps = parse_steps(steps)
        self.neutral = self.steps == NEUTRAL
        gains, gammas = channel_curves(self.steps)
        self._tables = tuple(_table(gain, gamma) for gain, gamma in zip(gains, gammas))

    def apply(self, color):
        if self.neutral:
            return tuple(color)
        return tuple(
            table[max(0, min(255, int(channel)))] for table, channel in zip(self._tables, color)
        )

    def apply_all(self, colors):
        if self.neutral:
            return colors
        return [self.apply(color) for color in colors]
