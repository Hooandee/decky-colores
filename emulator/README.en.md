# Colores Emulator

Colores Emulator loads an actual Colores release ZIP and runs its Decky
frontend and Python backend against synthetic Armada RGB hardware. No writes
are made to the host's `/sys` or Decky installation.

[Español](README.md) · English

## What is emulated

- Models and RGB nodes come from the ZIP's bundled
  `py_modules/armada_rgb_profiles.json`.
- The plugin's own discovery, gamma, color correction, effects and settings are
  used against a temporary `sysfs` tree.
- The Decky API and common `@decky/ui` controls are provided by the host.
- Local video and deterministic patterns feed the plugin's Ambilight code.
- Local audio and the bundled CC0 music track feed its real VU calculation.
- Updates, reports, loader restarts, HHD and suspend integration are disabled.

Only load ZIPs you trust. A plugin contains executable Python and JavaScript.

## Development

```sh
cd emulator
npm install
npm test
npm start
```

The app uses `python3` from `PATH` while developing. Packaging downloads the
latest CPython 3.13 ARM64 standalone runtime, verifies its SHA-256 digest from
the GitHub release metadata, and bundles it in the AppImage. An existing
`runtime/python/bin/python3` is reused for reproducible/offline builds.

Build the ARM64 AppImage with:

```sh
npm run package
```

The emulator shares its version number with the plugin and Plasmoid. The result
is written to `emulator/release/`.

The bundled default music is “Hella Bumps” by The Cynic Project / pixelsphere.org,
released as CC0:
https://opengameart.org/content/hella-bumps-menu-music
