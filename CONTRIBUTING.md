# Contribuir a Colores

Gracias por querer echar una mano. Esta guía va al grano.
(English version below.)

## Montar el entorno

```bash
pnpm install && pnpm build   # genera dist/index.js
python -m pytest             # tests del backend
ruff check py_modules main.py tests
```

El backend es Python (`main.py` + `py_modules/`). El frontend es React/TypeScript
(`src/`), empaquetado con `@decky/rollup`.

## Cómo trabajamos

- **Commits con formato convencional** (`feat:`, `fix:`, `docs:`, `chore:`...).
  De ahí salen la versión y el changelog de forma automática, así que importa.
- Backend con tests en pytest. Si tocas lógica del backend, añade o ajusta tests.
- Sin comentarios en el código salvo que hagan falta de verdad.
- El plugin se adapta solo a cada consola: la interfaz muestra únicamente lo que
  el dispositivo soporta. Mantén esa idea al añadir cosas.

### Calidad de las traducciones

El español, el inglés, el italiano y el alemán son textos de producto, no destinos
de una traducción literal. Cada frase nueva o modificada debe sonar natural, usar
la jerga habitual de juegos y hardware, y conservar la intención y el tono. Evita
giros de traducción automática y tics editoriales como los guiones largos.
`pnpm test:i18n` comprueba claves, parámetros, puntuación prohibida y terminología
revisada, pero el sentido y la naturalidad siempre requieren revisión humana.

## Añadir soporte para una consola

La detección y las capacidades viven en `py_modules/`. Para una máquina nueva
necesito saber su identidad (DMI `board_name` / `product_name`), cuántas zonas
de LED tiene y cómo se controlan. Si tienes el hardware y quieres ayudar a
calibrarlo, abre un issue de tipo "soporte de dispositivo" con esos datos.

## Pull requests

Que pasen el CI (tests, typecheck y build) antes de pedir revisión. Describe qué
cambia y, si afecta a una consola concreta, en cuál lo has probado.

---

# Contributing to Colores

Thanks for helping out. This guide gets to the point.

## Setup

```bash
pnpm install && pnpm build   # builds dist/index.js
python -m pytest             # backend tests
ruff check py_modules main.py tests
```

The backend is Python (`main.py` + `py_modules/`). The frontend is React/TypeScript
(`src/`), bundled with `@decky/rollup`.

## How we work

- **Conventional commits** (`feat:`, `fix:`, `docs:`, `chore:`...). The version
  and changelog are generated from them, so it matters.
- Backend is covered by pytest. If you touch backend logic, add or update tests.
- No code comments unless they're genuinely needed.
- The plugin adapts to each device: the UI shows only what the hardware supports.
  Keep that in mind when adding features.

### Localization quality

Spanish, English, Italian, and German are product copy, not literal translation
targets. New or changed strings must read naturally to a native speaker, use the
established gaming and hardware terminology, and preserve the original intent and
tone. Avoid machine-translation phrasing and editorial tics such as em dashes.
`pnpm test:i18n` checks keys, placeholders, banned punctuation, and reviewed
terminology; a human content review is still required for meaning and naturalness.

## Adding a device

Detection and capabilities live in `py_modules/`. For a new machine I need its
identity (DMI `board_name` / `product_name`), how many LED zones it has, and how
they're driven. If you have the hardware and want to help calibrate it, open a
"device support" issue with those details.

## Pull requests

Make sure CI passes (tests, typecheck, build) before requesting review. Describe
what changes and, if it's device-specific, which handheld you tested it on.
