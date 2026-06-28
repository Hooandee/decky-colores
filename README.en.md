# Colores ✨

RGB lighting control for handheld PCs, as a [Decky Loader](https://github.com/SteamDeckHomebrew/decky-loader) plugin.

[Español](README.md) · English

Colores detects your handheld at startup and shows you only what your machine can actually do. No config, no editing files, no picking your model from a list. Open it from the quick access menu and you're set.

## Supported devices

| Brand | Models | Notes |
| --- | --- | --- |
| ASUS ROG | Ally, Ally X, Xbox Ally, Xbox Ally X | 4 RGB zones around the joystick rings |
| Lenovo Legion | Go, Go 2, Go S | Per-controller color, reconnect button (see below) |
| MSI | Claw, Claw 8 AI+ | 9 zones |

Not on the list? Colores still tries to use your device by reading whatever LEDs the system exposes. Those features show up marked as experimental: you can try them, but they may not behave well until I get that machine in hand to calibrate it. If there are no controllable LEDs at all, the plugin tells you instead of hanging.

## What you can do

- **Solid color.** Pick a hue and saturation. The simplest option, and what most people want.
- **Brightness.** On devices that support it.
- **Gradients.** An editor with presets (Sunset, Ocean, Aurora, Lava, Galaxy and more), per-zone color tuning, an auto palette, and a "Surprise me" button for when you can't decide. You can save your favorite gradients and reuse them.
- **Effects.** Breathing, rainbow, wave, cycle and spiral. Color effects use your solid color, and several can run over your custom gradient if you turn that on.
- **Ambient (Ambilight).** The lights follow what's on screen, taking color from the region near each joystick. It has vividness, smoothing and capture-rate controls.
- **Spanish and English.** Switch language with the flags, top right. The plugin starts in Spanish.

Everything adapts to your handheld. If your machine only has a single global color, I won't show you a zone editor that wouldn't do anything.

## Things to keep in mind

- **Ambient only works in Game Mode with a game running.** It needs to read the image Game Mode composites. In Desktop or Big Picture there's nothing to capture, and the plugin warns you on screen instead of failing silently.

- **Legion Go: controllers disconnect after sleep.** When the handheld sleeps and wakes, the controllers sometimes lose the channel they receive colors on. That's why there's a **Reconnect controllers** button: if the lights stop responding after sleep, press it and they come back. It's not a plugin bug, it's how those controllers behave.

- **Legion Go: one color per controller.** The firmware on these machines doesn't allow multiple colors at once on a single controller, so a gradient shows as a smooth crossfade between colors rather than separate zones. The spiral is also the controller's own firmware rotating effect ("Spiral GO").

- **Unlisted devices.** Experimental features are exactly that, experimental. If you have a machine that isn't above and want to help add support, open an issue and I will take a look 🙌.

## Installation

The Decky plugin store is the easiest route when available. To install manually:

1. Download `Colores.zip` from the [latest release](https://github.com/Hooandee/decky-colores/releases/latest).
2. In Decky, enable developer mode and install the zip from the install-from-file option.

Colores needs root to write to the system LEDs. Decky will prompt you for it at install.

## Get more out of your handheld

I make videos about handheld PCs, tips, tweaks and how to squeeze the most out of these machines. If you like Colores, swing by the channel: [youtube.com/@Hooandee](https://www.youtube.com/@Hooandee).

## Acknowledgements

The per-device lighting engine is adapted from [HueSync](https://github.com/honjow/HueSync) by honjow, released under the BSD 3-Clause license. Their work on the per-brand drivers is what lets Colores talk to so many different handhelds, and I keep their copyright notice as the license requires. Truly, thank you for sharing it.

Colores also builds on the [Decky plugin template](https://github.com/SteamDeckHomebrew/decky-plugin-template) by Steam Deck Homebrew.

Made with a lot of love for handhelds.

## Development

```bash
pnpm install && pnpm build   # builds dist/index.js
python -m pytest             # backend tests
```

## License

BSD 3-Clause. See [LICENSE](LICENSE).
