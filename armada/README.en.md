# Armada OS ✨ — Plasma widget

RGB lighting and performance profile controls for Armada OS, combined in a
single Plasma 6 widget.

[Español](README.md) · English

The icon reflects the actual lighting state: color when the lights are on, stop
when they are off, and a warning when they are unavailable. Opening the panel
shows the RGB switch first and, below a separator, the Eco, Balanced, and
Performance options together with the active profile.

The interface automatically follows the language configured in Plasma. It
includes English, Spanish, Italian, German, and Brazilian Portuguese; any other
language falls back to English.

## What you can do

- **Turn all RGB lights on or off.** It preserves the previous brightness of
  every zone and never changes their colors.
- **Change the performance profile.** Choose Eco, Balanced, or Performance and
  see which profile is active.
- **Keep everything in sync.** State refreshes every two seconds to reflect
  changes made from Steam Big Picture, Armada Control, or Colores.

## Requirements

- Armada OS exposing the `left-side`, `left-joystick`, `right-side`, and
  `right-joystick` zones under `/sys/class/leds`.
- `/usr/bin/armada-power` and the Armada OS SteamOSManager service.
- Plasma 6 with `plasma5support` and `kpackagetool6`.
- Decky Loader with the **Colores** plugin installed and running.
- Python 3.
- GNU gettext (`msgfmt`) only when building the package from source.

## Installation

1. Download `Armada-OS.plasmoid` from the corresponding release.
2. Open Plasma's **Add Widgets** dialog.
3. Choose **Get New Widgets → Install Widget From Local File** and select the
   downloaded file.
4. Search for **Armada OS** and add it to the panel or desktop.

Installation stays inside the user profile and does not use `sudo` or execute
installation scripts.

## Things to keep in mind

- Colores controls the lights. If Decky or Colores is unavailable, the widget
  keeps showing the physical state, disables the switch, and reports the issue.
- Performance changes go through SteamOSManager so Steam does not later restore
  a cached profile.
- **QAM may show an older profile.** The Steam client has a known issue where
  its quick access menu does not always refresh when an external application
  changes the profile. The actual Armada OS profile still changes, and this
  widget displays that real state. If you change it again from QAM, the widget
  detects the new value automatically.
- The widget can coexist with the original individual widgets.

## Development

```sh
python3 -m unittest discover -s tests -v
python3 -m json.tool package/metadata.json >/dev/null
./scripts/package_widget.sh
```

After installation, it can be opened in a standalone window for testing:

```sh
plasmawindowed com.armada.armadaos
```

## Uninstallation

Open Plasma's widget picker, find **Armada OS**, and choose **Uninstall**. This
does not touch Colores or the original individual widgets.

## License

MIT. See [LICENSE](LICENSE).
