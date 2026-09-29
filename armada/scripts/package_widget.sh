#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
package_tmp=$(mktemp -d /tmp/armada-plasmoid.XXXXXX)
staging="$package_tmp/package"
archive="$package_tmp/Armada-OS.plasmoid"

cleanup() {
    rm -rf -- "$package_tmp"
}
trap cleanup EXIT HUP INT TERM

mkdir "$staging"
cp -r "$repo_root/armada/package/." "$staging/"

catalog_domain="plasma_applet_com.armada.armadaos"
for catalog in "$repo_root"/armada/translate/*.po; do
    locale=$(basename "$catalog" .po)
    locale_dir="$staging/contents/locale/$locale/LC_MESSAGES"
    mkdir -p "$locale_dir"
    msgfmt --check --output-file="$locale_dir/$catalog_domain.mo" "$catalog"
done

python3 - "$repo_root/package.json" "$staging/metadata.json" <<'PY'
import json
import sys
from pathlib import Path

package_json = Path(sys.argv[1])
metadata_json = Path(sys.argv[2])
version = json.loads(package_json.read_text(encoding="utf-8"))["version"]
metadata = json.loads(metadata_json.read_text(encoding="utf-8"))
metadata["KPlugin"]["Version"] = version
metadata_json.write_text(
    json.dumps(metadata, ensure_ascii=False, indent=4) + "\n", encoding="utf-8"
)
PY

(
    cd "$staging"
    zip -qr "$archive" . -x '*.pyc' '*/__pycache__/*'
)

unzip -t "$archive" >/dev/null
if unzip -Z1 "$archive" | grep -Eq '\.pyc$|(^|/)__pycache__/'; then
    echo "El paquete contiene cachés de Python excluidas" >&2
    exit 1
fi

for required in \
    metadata.json \
    contents/ui/main.qml \
    contents/code/armada-ledctl.py \
    contents/doc/LICENSE \
    contents/locale/es/LC_MESSAGES/plasma_applet_com.armada.armadaos.mo \
    contents/locale/de/LC_MESSAGES/plasma_applet_com.armada.armadaos.mo \
    contents/locale/it/LC_MESSAGES/plasma_applet_com.armada.armadaos.mo \
    contents/locale/pt_BR/LC_MESSAGES/plasma_applet_com.armada.armadaos.mo; do
    if ! unzip -Z1 "$archive" | grep -Fxq "$required"; then
        echo "Falta $required en el paquete Plasma" >&2
        exit 1
    fi
done

python3 - "$repo_root/package.json" "$archive" <<'PY'
import json
import sys
import zipfile
from pathlib import Path

expected = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))["version"]
with zipfile.ZipFile(sys.argv[2]) as package:
    metadata = json.loads(package.read("metadata.json"))
actual = metadata["KPlugin"]["Version"]
if actual != expected:
    raise SystemExit(f"Versión Plasma {actual!r}; se esperaba {expected!r}")
PY

mv "$archive" "$repo_root/Armada-OS.plasmoid"
unzip -l "$repo_root/Armada-OS.plasmoid"
