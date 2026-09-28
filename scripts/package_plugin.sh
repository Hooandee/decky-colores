#!/bin/sh

set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
package_tmp=$(mktemp -d /tmp/colores-package.XXXXXX)
staging="$package_tmp/Colores"
archive="$package_tmp/Colores.zip"

cleanup() {
    rm -rf -- "$package_tmp"
}
trap cleanup EXIT HUP INT TERM

cd "$repo_root"
if command -v pnpm >/dev/null 2>&1; then
    pnpm build
elif command -v corepack >/dev/null 2>&1; then
    corepack pnpm build
else
    echo "pnpm or corepack is required to build Colores" >&2
    exit 1
fi

mkdir "$staging"
cp -rL dist main.py plugin.json package.json README.md LICENSE py_modules assets "$staging"

(
    cd "$package_tmp"
    zip -qr "$archive" Colores -x '*.map' '*.pyc' '*/__pycache__/*'
)

unzip -t "$archive" >/dev/null
if unzip -Z1 "$archive" | grep -Eq '\.map$|\.pyc$|(^|/)__pycache__/'; then
    echo "package contains excluded build or Python cache files" >&2
    exit 1
fi

for required in Colores/plugin.json Colores/main.py Colores/dist/index.js; do
    if ! unzip -Z1 "$archive" | grep -Fxq "$required"; then
        echo "package is missing $required" >&2
        exit 1
    fi
done

mv "$archive" "$repo_root/Colores.zip"
unzip -l "$repo_root/Colores.zip"
