# Armada OS ✨ — widget para Plasma

Control de luces RGB y perfiles de rendimiento para Armada OS, en un único
widget para Plasma 6.

Español · [English](README.en.md)

El icono muestra el estado real de las luces: color cuando están encendidas,
stop cuando están apagadas y advertencia si no están disponibles. Al abrir el
panel aparecen primero el interruptor RGB y, debajo de un separador, los perfiles
Eco, Balanced y Performance con el perfil activo.

La interfaz sigue automáticamente el idioma configurado en Plasma. Incluye
inglés, español, italiano, alemán y portugués de Brasil; para cualquier otro
idioma utiliza inglés.

## Qué puedes hacer

- **Encender y apagar todas las luces RGB.** Conserva el brillo anterior de cada
  zona y no modifica sus colores.
- **Cambiar el perfil de rendimiento.** Permite elegir Eco, Balanced o
  Performance y muestra cuál está activo.
- **Mantener todo sincronizado.** Los estados se actualizan cada dos segundos
  para reflejar cambios hechos desde Steam Big Picture, Armada Control o Colores.

## Requisitos

- Armada OS con las zonas `left-side`, `left-joystick`, `right-side` y
  `right-joystick` expuestas en `/sys/class/leds`.
- `/usr/bin/armada-power` y el servicio SteamOSManager de Armada OS.
- Plasma 6 con `plasma5support` y `kpackagetool6`.
- Decky Loader con el plugin **Colores** instalado y activo.
- Python 3.
- GNU gettext (`msgfmt`) solo para construir el paquete desde el código fuente.

## Instalación

1. Descarga `Armada-OS.plasmoid` desde la release correspondiente.
2. Abre **Añadir elementos gráficos** en Plasma.
3. Entra en **Obtener nuevos elementos gráficos → Instalar elemento gráfico
   desde archivo local** y selecciona el archivo descargado.
4. Busca **Armada OS** y añádelo al panel o al escritorio.

La instalación se realiza dentro del perfil del usuario y no utiliza `sudo` ni
ejecuta scripts de instalación.

## Cosas a tener en cuenta

- Colores es el controlador de las luces. Si Decky o Colores no están
  disponibles, el widget conserva la lectura del estado físico, deshabilita el
  interruptor y muestra el problema.
- El cambio de rendimiento pasa por SteamOSManager para que Steam no restaure
  después un perfil que tenía guardado.
- **QAM puede mostrar un perfil anterior.** El cliente Steam tiene un problema
  conocido por el que su menú de acceso rápido no siempre refresca el valor
  cuando el cambio viene de una aplicación externa. El perfil real de Armada OS
  sí cambia y este widget muestra ese estado real. Si vuelves a cambiarlo desde
  QAM, el widget lo detecta automáticamente.
- El widget puede convivir con los widgets individuales originales.

## Desarrollo

```sh
python3 -m unittest discover -s tests -v
python3 -m json.tool package/metadata.json >/dev/null
./scripts/package_widget.sh
```

Tras instalarlo, se puede abrir en una ventana para probarlo:

```sh
plasmawindowed com.armada.armadaos
```

## Desinstalación

Abre el selector de elementos gráficos de Plasma, busca **Armada OS** y elige
**Desinstalar**. Esto no toca Colores ni los widgets individuales originales.

## Licencia

MIT. Ver [LICENSE](LICENSE).
