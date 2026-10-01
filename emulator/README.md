# Emulador de Colores

El emulador de Colores carga un ZIP real de una versión del plugin y ejecuta su
frontend de Decky y su backend de Python sobre hardware RGB Armada simulado. No
escribe en el `/sys` del equipo ni en la instalación de Decky.

Español · [English](README.en.md)

## Qué emula

- Los modelos y nodos RGB se leen de `py_modules/armada_rgb_profiles.json`,
  incluido en el ZIP.
- Usa el descubrimiento, la corrección gamma y de color, los efectos y los
  ajustes del propio plugin sobre un árbol `sysfs` temporal.
- Proporciona el API de Decky y los controles comunes de `@decky/ui`.
- Los videos locales y los patrones integrados alimentan Ambilight.
- El audio local y la pista CC0 incluida alimentan el cálculo VU del plugin.
- Las actualizaciones, los reportes, los reinicios del loader, HHD y la
  integración con la suspensión están desactivados.

Carga únicamente ZIPs de confianza: un plugin contiene código ejecutable de
Python y JavaScript.

## Desarrollo

```sh
cd emulator
npm install
npm test
npm start
```

Durante el desarrollo se usa `python3` desde `PATH`. El empaquetado descarga la
versión más reciente de CPython 3.13 standalone para ARM64, verifica su SHA-256
con los metadatos del release de GitHub y la incluye en el AppImage. Si ya existe
`runtime/python/bin/python3`, se reutiliza para permitir builds reproducibles o
sin conexión.

Construye el AppImage ARM64 con:

```sh
npm run package
```

La versión del emulador comparte el número de versión del plugin y el plasmoide.
El resultado se escribe en `emulator/release/`.

La música incluida por defecto es “Hella Bumps”, de The Cynic Project /
pixelsphere.org, publicada bajo CC0:
https://opengameart.org/content/hella-bumps-menu-music
