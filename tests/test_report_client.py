import base64
import gzip
import json
import os

from report.client import encode_payload, parse_response, save_local


def test_encode_payload_round_trips():
    bundle = {"app": "colores", "schema": 1, "text": "no enciende", "logs": ["x" * 1000]}
    env = encode_payload(bundle)
    assert env["app"] == "colores" and env["enc"] == "gzip" and env["schema"] == 1
    raw = gzip.decompress(base64.b64decode(env["payload"])).decode("utf-8")
    assert json.loads(raw) == bundle


def test_encode_payload_compresses():
    bundle = {"app": "colores", "logs": ["A" * 20000]}
    env = encode_payload(bundle)
    assert len(env["payload"]) < 2000


def test_parse_response_success():
    body = json.dumps({"ok": True, "code": "COL-7QK2", "issueUrl": "u"}).encode()
    assert parse_response(200, body) == {"ok": True, "code": "COL-7QK2", "issue_url": "u"}


def test_parse_response_2xx_without_code_is_failure():
    assert parse_response(200, json.dumps({"ok": True}).encode())["ok"] is False


def test_parse_response_http_error_surfaces_message():
    out = parse_response(429, json.dumps({"error": "rate limited"}).encode())
    assert out == {"ok": False, "error": "rate limited"}


def test_parse_response_garbage_body():
    out = parse_response(500, b"<html>oops")
    assert out["ok"] is False and "500" in out["error"]


def test_save_local_writes_json(tmp_path):
    path = save_local(str(tmp_path), {"a": 1}, code="COL-XX")
    assert path and path.endswith("report-COL-XX.json")
    with open(path) as f:
        assert json.load(f) == {"a": 1}


def test_save_local_offline_name(tmp_path):
    path = save_local(str(tmp_path), {"a": 1})
    assert os.path.basename(path) == "report-offline.json"
