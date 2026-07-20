# Datos compartidos de Colores

`shared/` contiene datos neutrales y vectores deterministas para mantener alineadas las implementaciones Decky y Android. No contiene código ejecutable. En F0 Android empaqueta estos ficheros como assets, pero todavía no los interpreta; Decky no cambia hasta F2.

## Tipos comunes

Un color RGB es un objeto con `r`, `g` y `b`. Cada canal es un entero entre 0 y 255.

Los identificadores usan minúsculas y guiones. Los nombres de claves JSON son estables y sensibles a mayúsculas.

## `devices.json`

La raíz es una lista de dispositivos. Cada entrada requiere:

- `id`: identificador estable.
- `friendlyName`: nombre mostrado al usuario.
- `android`: identidad y superficie de control Android, o `null`.
- `linux`: identidad Linux, o `null`.
- `capabilities`: booleanos `color`, `brightness` y `perZone`.

El bloque `android` puede declarar listas `model`, `device` y un objeto `led`. Para `settings_provider`, `led` requiere `driver`, `colorKey`, `colorFormat`, `brightnessKey`, `brightnessRange`, `enableKeys`, `zones`, `requiresPermission` y `vendorService`. `colorFormat` admite actualmente `argb_hex_csv`. No se debe inferir sysfs cuando no aparezca en el descriptor.

## `bands.json`

La raíz contiene `battery` y `temperature`. Cada grupo requiere una `unit` y una lista `bands` ordenada de mayor a menor. Cada banda tiene un umbral inclusivo `min` y un `color` RGB. El último umbral debe cubrir el valor mínimo esperado.

## `effects.json`

La raíz es una lista de presets. Cada preset requiere:

- `id`: identificador del motor.
- `label`: etiqueta de referencia del preset actual.
- `needs`: uno de `color`, `gradient` o `none`.
- `defaultSpeed`: entero entre 0 y 100.
- `description`: descripción de referencia del preset actual.
- `colors`: lista no vacía de colores RGB para su representación visual.

Las plataformas deben localizar las cadenas que muestran al usuario. Los valores de `id`, `needs`, `defaultSpeed` y `colors` forman parte del contrato compartido.

## `golden/gradient.json`

Un vector de gradiente requiere `id`, `operation: "interpolate_gradient"`, `input.stops`, `input.zones` y `expected.colors`. La salida contiene exactamente un color por zona y usa redondeo al entero más cercano igual que el backend actual.

## `golden/effect-frame.json`

Un vector de frame requiere `id`, `operation: "effect_frame"`, `input.effectId`, `input.timeSeconds`, `input.speed`, `input.base` y `expected.colors`. `base` y `expected.colors` contienen un color por zona. El vector debe ser determinista y no depender del reloj ni de hardware.
