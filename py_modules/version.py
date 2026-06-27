import json
import pathlib
from functools import lru_cache

_PACKAGE_JSON = pathlib.Path(__file__).resolve().parent.parent / "package.json"


@lru_cache(maxsize=1)
def read_version() -> str:
    return json.loads(_PACKAGE_JSON.read_text())["version"]
