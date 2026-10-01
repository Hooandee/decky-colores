# Optional bundled Python runtime

`npm run runtime` downloads a CPython 3.13 ARM64 standalone build and verifies
the SHA-256 digest advertised by its GitHub release. You can instead place a
runtime at `python/bin/python3` before packaging for reproducible/offline
builds. Development builds fall back to `python3` from `PATH`.
