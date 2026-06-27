# Changelog

## [0.6.0](https://github.com/Hooandee/decky-colores/compare/decky-colores-v0.5.0...decky-colores-v0.6.0) (2026-06-27)


### Features

* **legion:** elegant gradient crossfade, gradient-aware effects, resume recovery ([962d9e5](https://github.com/Hooandee/decky-colores/commit/962d9e5abd68f91f6bff51b748b025f4407b0a66))


### Bug Fixes

* **ux:** show Ambilight only as its own tab, not duplicated as experimental ([6ed1cb6](https://github.com/Hooandee/decky-colores/commit/6ed1cb67799376691da3b3c5b6d0676bf3c2a418))

## [0.5.0](https://github.com/Hooandee/decky-colores/compare/decky-colores-v0.4.0...decky-colores-v0.5.0) (2026-06-27)


### Features

* add build_device factory wiring profiles to writers ([e40905a](https://github.com/Hooandee/decky-colores/commit/e40905ac15584b00a4cb02ad8e24c52c046bf09b))
* add experimental capabilities UI with per-device effect filtering and perZone gradient gating ([ec3353c](https://github.com/Hooandee/decky-colores/commit/ec3353ce2e705cbb07e6f5459b10643381e02530))
* add LedDevice seam with SysfsRgbDevice (hex + color order) ([b713998](https://github.com/Hooandee/decky-colores/commit/b7139982b332e4f83d28d6291fd3ae8b6ba6ec7b))
* add per-device profile registry with resolve_profile ([d1c0165](https://github.com/Hooandee/decky-colores/commit/d1c0165f368dfb5aacc6a2dbcc9d369793e04e82))
* add read_zone_format and capability-state builder ([90efb11](https://github.com/Hooandee/decky-colores/commit/90efb1135941d72dc51ba1dbd0f4b20b2ff10c9f))
* **asus:** per-channel output color correction at the sysfs write ([00533c3](https://github.com/Hooandee/decky-colores/commit/00533c3ba5b9a479e927962f86fe9a408c0a8c0c))
* extend Capabilities type with experimental fields and add setExperiment API ([9d079d6](https://github.com/Hooandee/decky-colores/commit/9d079d6824aa4b9c3a4bf76059c3babf986f29c1))
* HID adapters bridging vendored transports to LedDevice ([0dc4857](https://github.com/Hooandee/decky-colores/commit/0dc4857942498abcbf9624157178375492f108f9))
* **legion:** expose rainbow and spiral hardware effects ([707d5db](https://github.com/Hooandee/decky-colores/commit/707d5db97ebd9c5615e21df4fd89679c9eb6156c))
* **legion:** per-controller gradient for the Legion tablet ([470f687](https://github.com/Hooandee/decky-colores/commit/470f687f6438dd61ea7e89cb8705626b0160945a))
* **msi:** swap left/right sticks in the preview layout ([9a795bd](https://github.com/Hooandee/decky-colores/commit/9a795bd92a52009252eff47a41442f97c6181bc5))
* multi-device LED support (Legion HID, MSI HID, capability model) ([8773270](https://github.com/Hooandee/decky-colores/commit/87732709aeaed8c822a042ab5cbe170a4c564e76))
* route non-per-zone HID devices through hardware effects in _apply ([53ddb89](https://github.com/Hooandee/decky-colores/commit/53ddb89631e2ed547947e4e7e1330ce7a3fc70cd))
* vendor HueSync HID transport layer (BSD-3) ([05da6c5](https://github.com/Hooandee/decky-colores/commit/05da6c584e52a0dadbe06062191a300b41225162))
* wire HID drivers into build_device ([29bc0d6](https://github.com/Hooandee/decky-colores/commit/29bc0d6e60fcf27c012cdabf5ab47d3b497e6aa8))
* wire main.py to build_device with experiment opt-in ([84a540c](https://github.com/Hooandee/decky-colores/commit/84a540c59efab81967fe980a48f6d3bbbc583b6d))


### Bug Fixes

* **apply:** handle ambient mode on HID devices ([d65c4db](https://github.com/Hooandee/decky-colores/commit/d65c4db99b6c86875f26baacb5faa05719046ab9))
* **device:** stop sharing experimental list across profile lookups ([8deae28](https://github.com/Hooandee/decky-colores/commit/8deae289413ba76874abf0ae080c7e7817ebe741))
* **hid:** honor brightness on Legion Go S solid ([9b3cd90](https://github.com/Hooandee/decky-colores/commit/9b3cd90d16bcbe543eb1052217010a264eaeb2f1))
* **routing:** only route HID devices without per-zone to hardware path ([3488f4e](https://github.com/Hooandee/decky-colores/commit/3488f4e93561bdc998e8e35ff42b62419677cc1e))
