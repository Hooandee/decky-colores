import json
import urllib.error

import pytest

from hhd_rgb_control import HhdRgbControl, RejectRedirects


class Response:
    def __init__(self, body):
        self._body = body if isinstance(body, bytes) else json.dumps(body).encode()

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False

    def read(self):
        return self._body


@pytest.fixture
def control_factory(tmp_path):
    token = tmp_path / "hhd.token"
    token.write_text("secret\n")

    def make(body, **options):
        seen = {}

        def open_request(request, timeout):
            seen.update(request=request, timeout=timeout)
            if isinstance(body, BaseException):
                raise body
            return Response(body)

        control = HhdRgbControl(
            token_path=str(token),
            open_request=open_request,
            **options,
        )
        return control, seen

    return make


def test_read_rgb_returns_current_boolean(control_factory):
    control, seen = control_factory(
        {"hhd": {"settings": {"rgb": True}}},
        timeout=1.5,
    )

    assert control.read_rgb() is True
    assert seen["request"].full_url == "http://127.0.0.1:5335/api/v1/state"
    assert seen["request"].get_method() == "GET"
    assert seen["request"].get_header("Authorization") == "Bearer secret"
    assert seen["timeout"] == 1.5


def test_set_rgb_posts_partial_state_and_confirms_echo(control_factory):
    control, seen = control_factory({"hhd": {"settings": {"rgb": False}}})

    assert control.set_rgb(False) is True
    assert {
        "body": json.loads(seen["request"].data),
        "method": seen["request"].get_method(),
    } == {
        "body": {"hhd": {"settings": {"rgb": False}}},
        "method": "POST",
    }


def test_set_rgb_rejects_mismatched_echo(control_factory):
    control, _ = control_factory({"hhd": {"settings": {"rgb": True}}})

    assert control.set_rgb(False) is False


def test_unavailable_api_returns_none_without_exposing_token(control_factory, caplog):
    control, _ = control_factory(urllib.error.URLError("connection refused"))

    assert control.read_rgb() is None
    assert control.read_rgb() is None
    assert "secret" not in caplog.text
    assert caplog.text.count("HHD RGB control request failed") == 1


def test_missing_token_returns_none(monkeypatch):
    monkeypatch.setattr("builtins.open", lambda *_args, **_kwargs: (_ for _ in ()).throw(OSError()))

    assert HhdRgbControl(token_path="/missing").read_rgb() is None


def test_invalid_response_returns_none(control_factory):
    control, _ = control_factory(b"not-json")

    assert control.read_rgb() is None


def test_redirects_are_rejected_before_authorization_can_leave_localhost():
    handler = RejectRedirects()
    request = object()

    with pytest.raises(urllib.error.HTTPError) as error:
        handler.redirect_request(
            request,
            None,
            302,
            "Found",
            {},
            "https://example.com/steal-token",
        )

    assert error.value.code == 302


def test_default_opener_disables_environment_proxies(tmp_path, monkeypatch):
    monkeypatch.setenv("http_proxy", "http://proxy.example:8080")
    token = tmp_path / "hhd.token"
    token.write_text("secret")

    control = HhdRgbControl(token_path=str(token))
    opener = control._open_request.__self__
    proxies = [
        handler.proxies
        for handler in opener.handlers
        if isinstance(handler, urllib.request.ProxyHandler)
    ]

    assert proxies == []
