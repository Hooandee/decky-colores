BAR_LEVEL = 24
BAR_SHARE_PERCENT = 90
MAX_CROP_PERCENT = 30
CHROMA_WEIGHT = 4
ADOPT_FRAMES = 3
RELEASE_FRAMES = 20


def _peak(frame, index):
    return max(frame[index], frame[index + 1], frame[index + 2])


def _is_bar(frame, indices):
    dark = sum(1 for index in indices if _peak(frame, index) <= BAR_LEVEL)
    return dark * 100 >= len(indices) * BAR_SHARE_PERCENT


def content_box(frame, width, height):
    def row(y):
        return [(y * width + x) * 3 for x in range(width)]

    def column(x, y0, y1):
        return [(y * width + x) * 3 for y in range(y0, y1)]

    if all(_is_bar(frame, row(y)) for y in range(height)):
        return (0, 0, width, height)
    top, bottom = _symmetric_bars(lambda y: _is_bar(frame, row(y)), height)
    left, right = _symmetric_bars(lambda x: _is_bar(frame, column(x, top, bottom)), width)
    return (left, top, right, bottom)


def _symmetric_bars(is_bar, size):
    limit = size * MAX_CROP_PERCENT // 100
    start = 0
    while start < limit and is_bar(start):
        start += 1
    end = 0
    while end < limit and is_bar(size - 1 - end):
        end += 1
    if start == 0 or end == 0 or abs(start - end) > 1:
        return 0, size
    return start, size - end


def region_color(frame, width, x0, y0, x1, y1):
    plain = [0, 0, 0]
    weighted = [0, 0, 0]
    count = 0
    total_weight = 0
    for y in range(y0, y1):
        for x in range(x0, x1):
            index = (y * width + x) * 3
            pixel = (frame[index], frame[index + 1], frame[index + 2])
            weight = 255 + CHROMA_WEIGHT * (max(pixel) - min(pixel))
            for channel in range(3):
                plain[channel] += pixel[channel]
                weighted[channel] += pixel[channel] * weight
            count += 1
            total_weight += weight
    if count == 0:
        return (0, 0, 0)
    mean_peak = max(value // count for value in plain)
    color = [value // total_weight for value in weighted]
    color_peak = max(color)
    if color_peak == 0:
        return (0, 0, 0)
    return tuple(min(255, (value * mean_peak + color_peak // 2) // color_peak) for value in color)


def region_pixels(box, region):
    left, top, right, bottom = box
    x0, y0, x1, y1 = region
    width = right - left
    height = bottom - top
    px0 = left + int(x0 * width)
    py0 = top + int(y0 * height)
    px1 = max(px0 + 1, min(right, left + int(x1 * width)))
    py1 = max(py0 + 1, min(bottom, top + int(y1 * height)))
    return (min(px0, right - 1), min(py0, bottom - 1), px1, py1)


class ContentBoxTracker:
    def __init__(self):
        self.box = None
        self._candidate = None
        self._streak = 0
        self._growth = 0

    def update(self, detected):
        if self.box is None:
            self.box = detected
        if detected == self.box:
            self._candidate = None
            self._streak = 0
            self._growth = 0
            return self.box
        if _area(detected) > _area(self.box):
            self._candidate = None
            self._streak = 0
            self._growth += 1
            if self._growth >= RELEASE_FRAMES:
                self.box = detected
                self._growth = 0
            return self.box
        self._growth = 0
        if detected != self._candidate:
            self._candidate = detected
            self._streak = 0
        self._streak += 1
        if self._streak >= ADOPT_FRAMES:
            self.box = detected
            self._candidate = None
            self._streak = 0
        return self.box


def _area(box):
    return (box[2] - box[0]) * (box[3] - box[1])
