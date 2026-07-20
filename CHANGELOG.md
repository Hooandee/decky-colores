# Changelog

## [0.20.1](https://github.com/Hooandee/decky-colores/compare/decky-colores-v0.20.0...decky-colores-v0.20.1) (2026-07-20)


### Reverts

* keep Decky release configuration unchanged ([9f0950c](https://github.com/Hooandee/decky-colores/commit/9f0950cdee8c3ee25ca1a7a1eed7cc4230cc4d28))

## [0.20.0](https://github.com/Hooandee/decky-colores/compare/decky-colores-v0.19.0...decky-colores-v0.20.0) (2026-07-19)


### Features / Novedades

* Full controller navigation: a visible accent ring now highlights whatever button or control the cursor is on, all across the panel and every dialog, so you can use the plugin end to end with just the controller. You can also pick your own accent color from a palette under Customize — it recolors the focus ring and the tab highlights instantly, and it's remembered. ([#58](https://github.com/Hooandee/decky-colores/pull/58)) ([a748e2c](https://github.com/Hooandee/decky-colores/commit/a748e2c0562a065e79b42ce14896159aa82436bb))
* **ES:** Navegación total con el mando: ahora un anillo de acento resalta el botón o control donde está el cursor, por todo el panel y en cada ventana, para que uses el plugin de principio a fin solo con el mando. Además puedes elegir tu propio color de acento desde una paleta en Personalización: recolorea al instante el anillo de foco y el resaltado de las pestañas, y se recuerda. ([#58](https://github.com/Hooandee/decky-colores/pull/58)) ([a748e2c](https://github.com/Hooandee/decky-colores/commit/a748e2c0562a065e79b42ce14896159aa82436bb))

## [0.19.0](https://github.com/Hooandee/decky-colores/compare/decky-colores-v0.18.0...decky-colores-v0.19.0) (2026-07-18)


### Features

* ambilight sampling selector (columns / bottom edge) ([631a578](https://github.com/Hooandee/decky-colores/commit/631a57823af89a52d550d2c5a735d7c5fc27698b))
* audio VU mode (bar reacts to system sound) ([d597dd0](https://github.com/Hooandee/decky-colores/commit/d597dd0e6ed05ab121d76fbba7c646d513d2f925))
* clock ambient mode (bar color follows time of day) ([2f1c5ab](https://github.com/Hooandee/decky-colores/commit/2f1c5ab0b60047e87f29b25ebf27b94b69cbfd83))
* indicator LED control and persistent startup color ([7451d3e](https://github.com/Hooandee/decky-colores/commit/7451d3e30a481d471d86623178aaeea9528b381d))
* performance meter mode (GPU/CPU load as a fill bar) ([8d51d62](https://github.com/Hooandee/decky-colores/commit/8d51d62c1aa6a335737047c6e1456d625f69d989))
* performance meter UI under the Sensors tab ([59eca97](https://github.com/Hooandee/decky-colores/commit/59eca976bf87a0c1b8326e9668517098cb61f747))
* remember-at-startup toggle for the Steam Machine bar ([b958898](https://github.com/Hooandee/decky-colores/commit/b95889850f3564261c8ca19894e38bffc1578bf9))
* Steam Machine (Valve Fremont) LED bar support ([f9000d2](https://github.com/Hooandee/decky-colores/commit/f9000d20bbf9c4f18805af66db7c55bbab95ad81))
* Steam Machine (Valve Fremont) LED bar support ([7397426](https://github.com/Hooandee/decky-colores/commit/739742618e5c918516353ec680a5bfd06b346fb6))


### Bug Fixes

* debounce startup-color flash writes to protect the EC flash ([4387a24](https://github.com/Hooandee/decky-colores/commit/4387a24c34809069d0eecb5a535e3959593555b3))
* remove indicator LED control (status:white drives no visible LED) ([8e648ca](https://github.com/Hooandee/decky-colores/commit/8e648ca7871315caaf016fc42e88a13870a87a30))

## [0.18.0](https://github.com/Hooandee/decky-colores/compare/decky-colores-v0.17.1...decky-colores-v0.18.0) (2026-07-10)


### Features / Novedades

* Sensors tab: the Battery tab is now **Sensors** and adds a new **Temperature** mode that colors your lights by your handheld's processor temperature, from cool blue when idle through to red when it runs hot. It uses the same smooth color bands as Battery and can gently pulse as a warning when things get very hot. It shows up automatically on any handheld with a temperature sensor, no setup needed. ([#41](https://github.com/Hooandee/decky-colores/pull/41)) ([0070768](https://github.com/Hooandee/decky-colores/commit/0070768f5bf4656596e9049d5ab791329c59f7ec))
* **ES:** Pestaña Sensores: la pestaña Batería ahora es **Sensores** y suma un nuevo modo **Temperatura** que colorea tus luces según la temperatura del procesador de tu consola, del azul en reposo al rojo cuando se calienta. Usa las mismas bandas de color suaves que Batería y puede latir con suavidad como aviso cuando la cosa se pone muy caliente. Aparece sola en cualquier consola con sensor de temperatura, sin configurar nada. ([#41](https://github.com/Hooandee/decky-colores/pull/41)) ([0070768](https://github.com/Hooandee/decky-colores/commit/0070768f5bf4656596e9049d5ab791329c59f7ec))

## [0.17.1](https://github.com/Hooandee/decky-colores/compare/decky-colores-v0.17.0...decky-colores-v0.17.1) (2026-07-10)


### Bug Fixes / Correcciones

* Polish for the new tabs and Settings: the Settings tab no longer repeats the Power, Charger-only and Brightness controls (those live on the mode tabs), Customize now comes before Report a problem, the spacing between sections was loosened up, and a couple of doubled separator lines on the Battery and Ambilight tabs are gone. ([#39](https://github.com/Hooandee/decky-colores/pull/39)) ([de3b376](https://github.com/Hooandee/decky-colores/commit/de3b3766c6eb56e6870af4258e8768af0fc73186))
* **ES:** Pulido de las nuevas pestañas y de Ajustes: la pestaña Ajustes ya no repite los controles de Encendido, Solo con cargador y Brillo (viven en las pestañas de modo), Personalizar interfaz ahora va antes de Reportar un problema, se soltó un poco el espaciado entre secciones, y desaparecieron un par de líneas separadoras duplicadas en las pestañas Batería y Ambilight. ([#39](https://github.com/Hooandee/decky-colores/pull/39)) ([de3b376](https://github.com/Hooandee/decky-colores/commit/de3b3766c6eb56e6870af4258e8768af0fc73186))

## [0.17.0](https://github.com/Hooandee/decky-colores/compare/decky-colores-v0.16.0...decky-colores-v0.17.0) (2026-07-10)


### Features / Novedades

* Customizable tabs: your lights now live in tabs you can reorder and hide, with a new Settings tab that brings language, updates, reporting a problem and the advanced options together in one place. Long tab names gently scroll instead of getting cut off, and each handheld shows only the tabs it supports. ([#37](https://github.com/Hooandee/decky-colores/pull/37)) ([f6e4939](https://github.com/Hooandee/decky-colores/commit/f6e4939f44fe463108366d283acb11e242195b49))
* **ES:** Pestañas personalizables: tus luces ahora se organizan en pestañas que puedes reordenar u ocultar, con una nueva pestaña de Ajustes que reúne el idioma, las actualizaciones, reportar un problema y las opciones avanzadas en un solo sitio. Los nombres largos de pestaña se desplazan suavemente en vez de cortarse, y cada consola muestra solo las pestañas que admite. ([#37](https://github.com/Hooandee/decky-colores/pull/37)) ([f6e4939](https://github.com/Hooandee/decky-colores/commit/f6e4939f44fe463108366d283acb11e242195b49))

## [0.16.0](https://github.com/Hooandee/decky-colores/compare/decky-colores-v0.15.5...decky-colores-v0.16.0) (2026-07-09)


### Features / Novedades

* Report a problem: a new option under About lets you send me a bug report in one tap. It automatically gathers the technical details about your lights and handheld — kept private and encrypted — and gives you a code to follow it up. ([#35](https://github.com/Hooandee/decky-colores/pull/35)) ([895e4f5](https://github.com/Hooandee/decky-colores/commit/895e4f5b9e18a4a3d6a7f2bc66c934cfac028f8c))
* **ES:** Reportar un problema: una nueva opción en Acerca de te permite enviarme un reporte de fallo con un toque. Recoge automáticamente la info técnica de tus luces y tu equipo —privada y cifrada— y te da un código para seguirlo. ([#35](https://github.com/Hooandee/decky-colores/pull/35)) ([895e4f5](https://github.com/Hooandee/decky-colores/commit/895e4f5b9e18a4a3d6a7f2bc66c934cfac028f8c))

## [0.15.5](https://github.com/Hooandee/decky-colores/compare/decky-colores-v0.15.4...decky-colores-v0.15.5) (2026-07-04)


### Bug Fixes

* recupera los LEDs del ROG Ally/Ally X sin nodo sysfs vía HID | recover ROG Ally/Ally X LEDs with no sysfs node via HID ([8f748a3](https://github.com/Hooandee/decky-colores/commit/8f748a35cf6cee0bb40a761c9b6a112d65dc9b00))
* recupera los LEDs del ROG Ally/Ally X sin nodo sysfs vía HID | recover ROG Ally/Ally X LEDs with no sysfs node via HID ([6e683d8](https://github.com/Hooandee/decky-colores/commit/6e683d84a55000899dea7af653fb44c9fa5cff5c))

## [0.15.4](https://github.com/Hooandee/decky-colores/compare/decky-colores-v0.15.3...decky-colores-v0.15.4) (2026-07-02)


### Bug Fixes

* minor fixes ([72f4472](https://github.com/Hooandee/decky-colores/commit/72f44727aba574efe5d0f6196395abb44ae914bc))

## [0.15.3](https://github.com/Hooandee/decky-colores/compare/decky-colores-v0.15.2...decky-colores-v0.15.3) (2026-07-02)


### Bug Fixes

* minor fixes ([6e6993c](https://github.com/Hooandee/decky-colores/commit/6e6993ceb62c24fd234c8b4a814e3b28d14e9db0))

## [0.15.2](https://github.com/Hooandee/decky-colores/compare/decky-colores-v0.15.1...decky-colores-v0.15.2) (2026-07-02)


### Bug Fixes

* show the changelog in a formatted modal instead of raw inline text ([ad5cf01](https://github.com/Hooandee/decky-colores/commit/ad5cf012536dd7825d280374924138a0f75cbe04))

## [0.15.1](https://github.com/Hooandee/decky-colores/compare/decky-colores-v0.15.0...decky-colores-v0.15.1) (2026-07-02)


### Bug Fixes

* match GitHub's dotted release asset name ([afd7cbd](https://github.com/Hooandee/decky-colores/commit/afd7cbd80a651cbb28dad3dabbe405c239e9bda2))

## [0.15.0](https://github.com/Hooandee/decky-colores/compare/decky-colores-v0.14.0...decky-colores-v0.15.0) (2026-07-02)


### Features

* add in-plugin self-updater ([1128390](https://github.com/Hooandee/decky-colores/commit/11283900aa5a5d85d8a1d00f31e727aa34b26c91))

## [0.14.0](https://github.com/Hooandee/decky-colores/compare/decky-colores-v0.13.0...decky-colores-v0.14.0) (2026-07-02)


### Features

* add battery mode that colors LEDs by charge level ([225a5c2](https://github.com/Hooandee/decky-colores/commit/225a5c2fdf641d1bb164057411b5347af633f52d))

## [0.13.0](https://github.com/Hooandee/decky-colores/compare/decky-colores-v0.12.0...decky-colores-v0.13.0) (2026-07-02)


### Features

* keep Prioritize-Colores holding via a background re-assert ([4331003](https://github.com/Hooandee/decky-colores/commit/4331003b3c5f00379224cb8b3b3df00c14dd5947))
* keep Prioritize-Colores holding via a background re-assert ([b8e4dd3](https://github.com/Hooandee/decky-colores/commit/b8e4dd3d00c9fad56e1ae9d913cfa4b9a2def9bc))


### Bug Fixes

* gentle periodic re-assert to avoid a 2s LED flicker ([ed8813c](https://github.com/Hooandee/decky-colores/commit/ed8813c7f10dd332848a330844ff14fec4e66349))

## [0.12.0](https://github.com/Hooandee/decky-colores/compare/decky-colores-v0.11.0...decky-colores-v0.12.0) (2026-07-02)


### Features

* add AsusAllyHidDevice Aura HID adapter ([a984584](https://github.com/Hooandee/decky-colores/commit/a98458467cce872e5e975a79288f1203e26594a5))
* add force_control setting and reassert RPC ([5f100a6](https://github.com/Hooandee/decky-colores/commit/5f100a62e5ee5637fe6718bda2b39777aff9de59))
* add ROG Ally Aura RGB protocol builders ([95eb35d](https://github.com/Hooandee/decky-colores/commit/95eb35d040aa5a75684e779728a84d8f215cfeff))
* expose conflictsWithSystemRgb and implement hid_asus_ally ([edad75e](https://github.com/Hooandee/decky-colores/commit/edad75eaa2e42336349dfecee8ee30009719e9f1))
* force-control toggle and HHD conflict notice for the Ally ([cbbb3b7](https://github.com/Hooandee/decky-colores/commit/cbbb3b765f776c507b3e5b776e57fd80e481a6b7))
* map ROG Ally RC71L to the Aura HID driver ([1511e72](https://github.com/Hooandee/decky-colores/commit/1511e72d5b1ccabc4bce9e9a8b2430fb4e6b9f5b))
* ROG Ally (RC71L) RGB support via Aura HID + Prioritize-Colores toggle ([ae89cb4](https://github.com/Hooandee/decky-colores/commit/ae89cb4fe8786dcd53e3a97cac17f1b6e79883c5))


### Bug Fixes

* hide Legion-only spiral effect on the ROG Ally ([ea77df0](https://github.com/Hooandee/decky-colores/commit/ea77df01d867bfa058870665a3bb066ade74e872))
* re-poll state while no LEDs so a late-enumerating node self-heals the UI ([f7575d6](https://github.com/Hooandee/decky-colores/commit/f7575d6bf23d1fbc1d5ff794ea072b3759c1a94f))
* reclaim via reconnect so force-control re-inits the Aura device ([123b2e1](https://github.com/Hooandee/decky-colores/commit/123b2e19cd40749bb5de56e9427fd18ddf6c509c))

## [0.11.0](https://github.com/Hooandee/decky-colores/compare/decky-colores-v0.10.1...decky-colores-v0.11.0) (2026-06-29)


### Features

* encender LEDs solo con el cargador conectado ([d65998d](https://github.com/Hooandee/decky-colores/commit/d65998def3b17ac5a4cb3de057c6c0f39887b8f7))
* gate LEDs on charger connection (charger-only mode) ([b06650c](https://github.com/Hooandee/decky-colores/commit/b06650cc1b60d322b4226da76f93a8732e9da0b0))

## [0.10.1](https://github.com/Hooandee/decky-colores/compare/decky-colores-v0.10.0...decky-colores-v0.10.1) (2026-06-29)


### Bug Fixes

* **EN:** recover LED config after a cold boot: ambient now reconnects to the gamescope capture source (showing the last solid color as a fallback while there is no source) instead of giving up, plus a defensive re-assert and better diagnostics for static modes ([696e81e](https://github.com/Hooandee/decky-colores/commit/696e81e658771a95858e92ea92bb64ccb96d296d))
* **ES:** recuperar la configuración de LEDs tras un arranque en frío: ambient ahora se reconecta a la fuente de captura de gamescope (mostrando el último color sólido como respaldo mientras no hay fuente) en lugar de rendirse, con una reafirmación defensiva y mejor diagnóstico para los modos estáticos ([696e81e](https://github.com/Hooandee/decky-colores/commit/696e81e658771a95858e92ea92bb64ccb96d296d))

## [0.10.0](https://github.com/Hooandee/decky-colores/compare/decky-colores-v0.9.0...decky-colores-v0.10.0) (2026-06-29)


### Features

* add experimental power-button LED toggle for Legion Go ([807a40b](https://github.com/Hooandee/decky-colores/commit/807a40b54735b7fef3b6158e0ecbd848bfb388d5))
* experimental power-button LED toggle for Legion Go ([b77ff2f](https://github.com/Hooandee/decky-colores/commit/b77ff2f915c73ce391067ba35307faf1ce9d985c))

## [0.9.0](https://github.com/Hooandee/decky-colores/compare/decky-colores-v0.8.0...decky-colores-v0.9.0) (2026-06-28)


### Features

* **ux:** replace plugin icon with a color wheel ([98b3360](https://github.com/Hooandee/decky-colores/commit/98b3360c1f0e56ae38daf010b3579b50c2b345b4))

## [0.8.0](https://github.com/Hooandee/decky-colores/compare/decky-colores-v0.7.0...decky-colores-v0.8.0) (2026-06-27)


### Features

* **ux:** redesign gradient editor with tabs and horizontal zone grid ([c4793a1](https://github.com/Hooandee/decky-colores/commit/c4793a1bb5849c413bc416624f2dea65c6047cb5))
* **ux:** redesign gradient editor with tabs and horizontal zone grid ([662538b](https://github.com/Hooandee/decky-colores/commit/662538b3e509fa00fcd3cf163365b8bdd5ed749b))

## [0.7.0](https://github.com/Hooandee/decky-colores/compare/decky-colores-v0.6.0...decky-colores-v0.7.0) (2026-06-27)


### Features

* **effects:** add Spiral effect — firmware "Spiral GO" on Legion, software on Ally ([5682a32](https://github.com/Hooandee/decky-colores/commit/5682a3294a32ac2f92fa01bd991a511da481bdeb))
* **resume:** restore user lighting after suspend on SteamOS ([5c44e9c](https://github.com/Hooandee/decky-colores/commit/5c44e9c5170fd5191bad90f9bee7f275f24f5c5e))


### Bug Fixes

* **legion:** coherent wave effect on single-color-zone devices ([8388a17](https://github.com/Hooandee/decky-colores/commit/8388a17ba8e2cb9d662338423351d5b444516243))
* **resume:** warn when SteamClient is unavailable at load ([08b943c](https://github.com/Hooandee/decky-colores/commit/08b943c21e02284a038b362cfd6a2b16ca978848))
* **ux:** consistent disabled states, effect-setter desync, load-error recovery ([bf3ece6](https://github.com/Hooandee/decky-colores/commit/bf3ece6231a7e8381128487aebd720d281f8a86b))
* **ux:** show the gradient section on single-color devices (Legion Go S) ([7fe3eca](https://github.com/Hooandee/decky-colores/commit/7fe3ecaee75a2c7aa33da6521e6a1b0b53e76a7c))

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
