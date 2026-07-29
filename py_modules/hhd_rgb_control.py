from __future__ import annotations

import json
import logging
import urllib.error
import urllib.request

logger = logging.getLogger(__name__)


class RejectRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        url = request.full_url if hasattr(request, "full_url") else ""
        raise urllib.error.HTTPError(url, code, msg, headers, fp)


class HhdRgbControl:
    def __init__(
        self,
        token_path: str = "/etc/hhd/.token",
        base_url: str = "http://127.0.0.1:5335/api/v1",
        timeout: float = 8.0,
        open_request=None,
    ):
        self._token_path = token_path
        self._base_url = base_url.rstrip("/")
        self._timeout = timeout
        self._open_request = open_request or urllib.request.build_opener(
            urllib.request.ProxyHandler({}),
            RejectRedirects(),
        ).open
        self._last_failure = None

    def read_rgb(self) -> bool | None:
        state = self._request("GET")
        return self._rgb_from_state(state)

    def set_rgb(self, enabled: bool) -> bool | None:
        state = self._request(
            "POST",
            {"hhd": {"settings": {"rgb": bool(enabled)}}},
        )
        echoed = self._rgb_from_state(state)
        if echoed is None:
            return None
        return echoed is bool(enabled)

    def _request(self, method: str, payload: dict | None = None) -> dict | None:
        try:
            with open(self._token_path, encoding="utf-8") as handle:
                token = handle.read().strip()
            if not token:
                self._warn_once("empty_token", "HHD RGB control unavailable: empty API token")
                return None
            body = None if payload is None else json.dumps(payload).encode("utf-8")
            request = urllib.request.Request(
                f"{self._base_url}/state",
                data=body,
                method=method,
                headers={
                    "Authorization": f"Bearer {token}",
                    "Content-Type": "application/json",
                },
            )
            with self._open_request(request, timeout=self._timeout) as response:
                decoded = json.loads(response.read().decode("utf-8"))
            self._last_failure = None
            return decoded if isinstance(decoded, dict) else None
        except (OSError, ValueError, urllib.error.URLError) as exc:
            self._warn_once(
                type(exc).__name__,
                "HHD RGB control request failed: %s",
                type(exc).__name__,
            )
            return None

    def _warn_once(self, key, message, *args):
        if self._last_failure != key:
            logger.warning(message, *args)
        self._last_failure = key

    @staticmethod
    def _rgb_from_state(state: dict | None) -> bool | None:
        if not isinstance(state, dict):
            return None
        value = state.get("hhd", {}).get("settings", {}).get("rgb")
        return value if isinstance(value, bool) else None
