# Colores ✨

Control de luces RGB para PCs portátiles, como plugin de [Decky Loader](https://github.com/SteamDeckHomebrew/decky-loader).

Español · [English](README.en.md)

Colores detecta tu consola al arrancar y te muestra solo lo que tu máquina puede hacer de verdad. Sin configuración, sin tocar archivos, sin elegir tu modelo en una lista. Lo abres desde el menú de acceso rápido y ya está.

## Dispositivos compatibles

| Marca | Modelos | Detalles |
| --- | --- | --- |
| ASUS ROG | Ally, Ally X, Xbox Ally, Xbox Ally X | 4 zonas RGB en los anillos de los joysticks |
| Lenovo Legion | Go, Go 2, Go S | Color por mando, botón de reconexión (ver más abajo) |
| MSI | Claw, Claw 8 AI+ | 9 zonas |

¿Tu consola no está en la lista? Colores intenta usarla igualmente leyendo los LEDs que el sistema expone. Verás esas funciones marcadas como experimentales: puedes probarlas, pero puede que no respondan bien hasta que tenga esa máquina entre manos para calibrarla. Si ni siquiera hay LEDs que controlar, el plugin te lo dice y no se queda colgado.

## Qué puedes hacer

- **Color fijo.** Elige un tono y una saturación. Lo más simple y lo que casi todo el mundo quiere.
- **Brillo.** En las consolas que lo permiten.
- **Degradados.** Un editor con presets (Atardecer, Océano, Aurora, Lava, Galaxia y más), ajuste color a color por zona, una paleta automática y un botón de "Sorpréndeme" para cuando no te decidas. Puedes guardar tus degradados favoritos y reutilizarlos.
- **Efectos.** Respiración, arcoíris, onda, ciclo y espiral. Los efectos de color usan tu color fijo, y varios pueden correr sobre tu degradado personalizado si activas esa opción.
- **Ambilight.** Las luces siguen lo que pasa en pantalla, tomando el color de la zona cercana a cada joystick. Tiene controles de intensidad, suavizado y tasa de captura.
- **Español e inglés.** Cambias de idioma con las banderas, arriba a la derecha. El plugin arranca en español.

Todo se adapta a tu consola. Si tu máquina solo tiene un color global, no te muestro un editor de zonas que no va a hacer nada.

## Cosas a tener en cuenta

- **Ambilight solo funciona en Modo Juego con un juego abierto.** Necesita leer la imagen que compone Modo Juego. En Escritorio o en Big Picture no hay nada que capturar, y el plugin te lo avisa con un mensaje en pantalla en lugar de fallar en silencio.

- **Legion Go: los mandos se desconectan al suspender.** Cuando la consola se duerme y vuelve, los mandos a veces pierden el canal por el que reciben los colores. Por eso hay un botón **Reconectar mandos**: si las luces dejan de responder después de suspender, púlsalo y vuelven a la vida. No es un fallo del plugin, es cómo se comportan esos mandos.

- **Legion Go: un solo color por mando.** El firmware de estas consolas no permite varios colores a la vez en un mismo mando, así que el degradado se ve como un fundido suave entre colores en lugar de zonas separadas. La espiral, además, es el efecto giratorio propio del firmware ("Espiral GO").

- **Consolas no listadas.** Las funciones experimentales son justo eso, experimentales. Si tienes una máquina que no aparece arriba y quieres ayudar a darle soporte, abre un issue.

## Instalación

Desde la tienda de plugins de Decky es la forma más cómoda cuando esté disponible. Para instalar a mano:

1. Descarga `Colores.zip` desde la [última release](https://github.com/Hooandee/decky-colores/releases/latest).
2. En Decky, activa el modo desarrollador e instala el zip desde la opción de instalar desde archivo.

Colores necesita permisos de root para escribir en los LEDs del sistema. Decky te lo pedirá al instalar.

## Saca más partido a tu consola

Hago vídeos sobre PCs portátiles, trucos, ajustes y cómo exprimir estas máquinas. Si Colores te ha gustado, pásate por el canal: [youtube.com/@Hooandee](https://www.youtube.com/@Hooandee).

## Agradecimientos

El motor de control de luces por dispositivo está adaptado de [HueSync](https://github.com/honjow/HueSync), de honjow, publicado bajo licencia BSD 3-Clause. Su trabajo en los drivers de cada marca es lo que hace posible que Colores hable con tantas consolas distintas, y conservo su aviso de copyright como pide la licencia. Gracias de verdad por compartirlo.

Colores también se apoya en la [plantilla de plugins de Decky](https://github.com/SteamDeckHomebrew/decky-plugin-template) de Steam Deck Homebrew.

Hecho con todo el cariño por las portátiles.

## Desarrollo

```bash
pnpm install && pnpm build   # genera dist/index.js
python -m pytest             # tests del backend
```

## Licencia

BSD 3-Clause. Ver [LICENSE](LICENSE).
