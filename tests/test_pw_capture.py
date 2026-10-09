import struct

from py_modules import pw_capture


def test_enum_format_pod_is_an_aligned_format_object():
    pod = pw_capture.enum_format_pod()
    size, type_ = struct.unpack_from("<II", pod)
    assert type_ == pw_capture.SPA_TYPE_OBJECT
    assert len(pod) == size + 8
    assert len(pod) % 8 == 0
    assert struct.unpack_from("<II", pod, 8) == (
        pw_capture.SPA_TYPE_OBJECT_FORMAT, pw_capture.SPA_PARAM_ENUM_FORMAT,
    )


def test_parse_format_reads_choice_defaults():
    assert pw_capture.parse_format(pw_capture.enum_format_pod()) == (8, 64, 36)


def _negotiated(fmt, width, height):
    props = b"".join((
        pw_capture._prop(pw_capture.SPA_FORMAT_VIDEO_FORMAT,
                         pw_capture._pod(pw_capture.SPA_TYPE_ID, struct.pack("<I", fmt))),
        pw_capture._prop(pw_capture.SPA_FORMAT_VIDEO_SIZE,
                         pw_capture._pod(pw_capture.SPA_TYPE_RECTANGLE, struct.pack("<II", width, height))),
    ))
    return pw_capture._pod(
        pw_capture.SPA_TYPE_OBJECT,
        struct.pack("<II", pw_capture.SPA_TYPE_OBJECT_FORMAT, pw_capture.SPA_PARAM_FORMAT) + props,
    )


def test_parse_format_reads_fixed_values():
    assert pw_capture.parse_format(_negotiated(7, 1920, 1080)) == (7, 1920, 1080)


def test_downsample_averages_bgrx_with_stride_padding():
    width, height, stride = 4, 2, 20
    frame = bytearray(stride * height)
    for y in range(height):
        for x in range(width):
            p = y * stride + x * 4
            frame[p:p + 4] = bytes((10, 20, 200, 0)) if x < 2 else bytes((0, 0, 0, 0))
    plan = pw_capture.sample_plan(stride, width, height, 2, 1, samples=1)
    out = pw_capture.downsample(memoryview(bytes(frame)), plan, pw_capture.PIXEL_LAYOUTS[8])
    assert out == bytes((200, 20, 10, 0, 0, 0))


def test_a_failing_callback_stops_the_helper_instead_of_spamming(monkeypatch):
    import ctypes

    class FakeLib:
        def __getattr__(self, name):
            fn = lambda *a: 0  # noqa: E731
            if name == "pw_stream_dequeue_buffer":
                fn = lambda *a: (_ for _ in ()).throw(RuntimeError("boom"))  # noqa: E731
            if name == "pw_main_loop_run":
                def run(loop):
                    events = captured["events"]
                    events.process(None)
                    events.process(None)
                fn = run
            if name == "pw_stream_new_simple":
                def new(loop, name, props, events, data):
                    captured["events"] = ctypes.cast(events, ctypes.POINTER(pw_capture._StreamEvents)).contents
                    return 1
                fn = new
            obj = type("F", (), {"__call__": lambda self, *a: fn(*a)})()
            setattr(self, name, obj)
            return obj

    captured = {}
    quits = []
    lib = FakeLib()
    monkeypatch.setattr(pw_capture, "_load", lambda: lib)
    monkeypatch.setattr(pw_capture, "CONNECT_TIMEOUT", 60)
    lib.pw_main_loop_quit = type("Q", (), {"__call__": lambda self, *a: quits.append(1)})()
    assert pw_capture.capture("gamescope", 2, 1, 10, out=open("/dev/null", "wb")) == 2
    assert quits == [1]
