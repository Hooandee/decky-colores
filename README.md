# Colores

Beautiful RGB LED control for handheld gaming PCs, as a Decky Loader plugin.

Control color, brightness, hardware effects, and software-driven gradients —
gated per device by what each machine can actually do.

## Supported devices

Device support grows over time. Detection is based on DMI board/product names.
First targets: Steam Deck, ASUS ROG Ally / Ally X, Lenovo Legion Go family.

## Development

```bash
pnpm install
pnpm build        # builds dist/index.js
python -m pytest  # backend unit tests
```

## Releases

Push a `vX.Y.Z` tag to build and publish the plugin zip as a GitHub Release.

## Acknowledgements

The per-device LED control logic is adapted from
[HueSync](https://github.com/honjow/HueSync) by honjow (BSD 3-Clause), itself
building on the [Decky plugin template](https://github.com/SteamDeckHomebrew/decky-plugin-template)
by Steam Deck Homebrew. We stand on their work — thank you.
