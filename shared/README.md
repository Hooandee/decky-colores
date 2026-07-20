# Datos compartidos de Colores

`shared/` contiene datos neutrales y vectores deterministas para mantener alineadas las implementaciones Decky y Android. No contiene código ejecutable. Android empaqueta y consume estos ficheros como assets; Decky no cambia hasta su fase de integración.

Cada nueva plataforma implementa su propio runtime, drivers y UI nativa. Windows podrá añadirse como otro consumidor sin convertir esta carpeta en una librería ejecutable.

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
- `previewProfile`: identificador opcional de `led-preview-profiles.json`.

El bloque `android` puede declarar listas `model`, `device` y un objeto `led`. Para `settings_provider`, `led` requiere `driver`, `transport`, `colorKey`, `colorFormat`, `brightnessKey`, `brightnessRange`, `enableKeys`, `zones`, `requiresPermission` y `vendorService`. `transport` admite `direct` y `pserver`; `requiresPermission` es `null` cuando el transporte no necesita una concesión del usuario. `colorFormat` admite actualmente `argb_hex_csv`. No se debe inferir sysfs cuando no aparezca en el descriptor.

El driver `htr3212` conserva esos campos para el estado oficial de brillo, encendido y color de respaldo, y añade un bloque `htr3212` con `leftBus`, `rightBus`, `address`, `leftOrder` y `rightOrder`. Las listas de orden traducen cada zona lógica del joystick, en orden arriba/izquierda/abajo/derecha, al grupo físico `0..3` del controlador. Deben ser permutaciones completas y calibradas en el dispositivo real.

Una entrada puede declarar `previewProfile` para aproximar en la interfaz la apariencia de sus LEDs físicos. Si la referencia falta o no se resuelve, la plataforma muestra el RGB exacto y no ofrece la vista calibrada.

## `led-preview-profiles.json`

La raíz es una lista de perfiles visuales reutilizables. Cada perfil requiere un `id` estable y un objeto `calibration`. Sus campos son `saturationScale` entre 0 y 1.5, `whiteMix` entre 0 y 1, `redGain`, `greenGain` y `blueGain` entre 0 y 2, `valueGamma` entre 0.1 y 3, `glowAlpha` entre 0 y 1 y `hueMap`, una lista de anclas `input` y `output` en grados que interpola la deriva de tono observada.

Un perfil puede ser referenciado por varias máquinas únicamente cuando la respuesta perceptual de sus LEDs se ha validado en hardware. Compartir fabricante, carcasa o disposición no basta. La calibración solo cambia lo dibujado y nunca el RGB enviado al dispositivo.

## `platform-support.json`

Registra las funciones del producto, los contratos compartidos que consumen y su estado en `decky`, `android` y `windows`. Es la fuente tracked para decidir el alcance de una feature antes de implementarla.

Los estados permitidos son `validated`, probado de extremo a extremo; `implemented`, terminado pero pendiente de validación final; `planned`, aprobado para una fase próxima; `deferred`, posible trabajo futuro sin compromiso actual; y `unsupported`, no disponible deliberadamente. El estado sigue condicionado por las capacidades y dispositivos validados de cada plataforma: `validated` no significa que todo el hardware soporte esa función.

Una modificación de comportamiento común actualiza el contrato o golden vector y cada plataforma consumidora. Un cambio de transporte, permisos, ciclo de vida o UI se implementa únicamente en el runtime nativo afectado. Un fichero de `shared/` nunca sustituye esa implementación.

## `gradients.json`

La raíz requiere `schemaVersion` y una lista `presets`. Cada preset contiene un `id` estable en minúsculas y una lista no vacía `colors` de colores RGB. El contrato no contiene nombres visibles: cada plataforma localiza los identificadores en su propia interfaz.

El orden de `colors` define las paradas del gradiente. Cada runtime interpola esas paradas al número real de zonas del dispositivo mediante `golden/gradient.json`.

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

Los ficheros de `golden/` son listas de vectores deterministas generados desde la implementación de referencia de Decky (`py_modules/effects.py`). Cada plataforma valida su motor contra los mismos vectores; así se bloquea la deriva entre Python y Kotlin. Ningún vector depende del reloj ni del hardware.

## `golden/gradient.json`

Lista de vectores. Cada vector requiere `id`, `operation: "interpolate_gradient"`, `input.stops`, `input.zones` y `expected.colors`. La salida contiene exactamente un color por zona y usa redondeo al entero más cercano con desempate al par, igual que el backend.

## `golden/effect-frame.json`

Lista de vectores. Cada vector requiere `id`, `operation: "effect_frame"`, `input.effectId`, `input.timeSeconds`, `input.speed`, `input.zones` y `expected.colors`. Según el efecto, el input añade `base` (paleta por zona para efectos que necesitan color) o `stops` (paradas del gradiente para `wave` y `spiral`). Los efectos que no necesitan color no llevan ninguno de los dos.

## `golden/clock.json`

Lista de vectores. Cada vector requiere `id`, `operation: "clock_color"`, `input.hour` (hora local en 0..24 con la fracción de minutos) y `expected.color`. Interpola linealmente los puntos horarios del reloj.

## `golden/meter.json`

Lista de vectores. Cada vector requiere `id`, `operation: "performance_meter"`, `input.value` (0..1), `input.zones` y `expected.colors`. Reproduce la barra de nivel verde a rojo del modo rendimiento.
