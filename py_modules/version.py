import json
import pathlib

_PACKAGE_JSON = pathlib.Path(__file__).resolve().parent.parent / "package.json"


def read_version() -> str:
    return json.loads(_PACKAGE_JSON.read_text())["version"]
