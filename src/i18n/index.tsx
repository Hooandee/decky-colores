import { FC, ReactNode, createContext, useCallback, useContext, useMemo, useState } from "react";
import { Focusable } from "@decky/ui";

export type Lang = "es" | "en";

const STORAGE_KEY = "colores-lang";

const es: Record<string, string> = {
  "load.error": "No se pudo cargar el estado del plugin. Vuelve a intentarlo en un momento.",
  "load.retry": "Reintentar",

  "device.noLeds": "No se detectaron LEDs controlables en este dispositivo.",
  "device.preview.rings": "Anillos del joystick",
  "device.preview.off": "Apagado",
  "device.preview.ambient": "Siguiendo la pantalla",

  "power.label": "Encendido",
  "reconnect.label": "Reconectar mandos",
  "reconnect.hint": "¿Las luces no responden tras suspender? Reinicia la conexión con los mandos.",

  "mode.solid": "Fijo",
  "mode.gradient": "Degradado",
  "mode.effect": "Efecto",
  "mode.ambient": "Ambilight",

  "color.hue": "Tono",
  "color.saturation": "Saturación",

  "gradient.edit": "Editar degradado",
  "gradient.title": "Crear degradado",
  "gradient.tab.presets": "Presets",
  "gradient.tab.tune": "Ajustar",
  "gradient.tab.save": "Guardar",
  "gradient.zonesCaption": "{n} zonas",
  "gradient.surprise": "Sorpréndeme",
  "gradient.autoPalette": "Auto-paleta",
  "gradient.apply": "Aplicar",
  "gradient.cancel": "Cancelar",
  "gradient.colorN": "Color {n}",
  "gradient.colorStart": "Color inicio",
  "gradient.colorEnd": "Color fin",
  "gradient.defaultGroup": "Luces",
  "gradient.speed": "Velocidad",
  "layout.leftStick": "Stick izquierdo",
  "layout.rightStick": "Stick derecho",
  "layout.lights": "Luces",

  "gradient.preset.sunset": "Atardecer",
  "gradient.preset.ocean": "Océano",
  "gradient.preset.aurora": "Aurora",
  "gradient.preset.neon": "Neón",
  "gradient.preset.lava": "Lava",
  "gradient.preset.mint": "Menta",
  "gradient.preset.vaporwave": "Vaporwave",
  "gradient.preset.forest": "Bosque",
  "gradient.preset.galaxy": "Galaxia",
  "gradient.preset.ember": "Brasa",
  "gradient.preset.ice": "Hielo",
  "gradient.preset.candy": "Caramelo",

  "saved.sectionTitle": "Mis degradados",
  "saved.delete": "Borrar",
  "saved.namePlaceholder": "Ponle un nombre",
  "saved.suggest": "Otro nombre",
  "saved.confirm": "Guardar",

  "effect.speed": "Velocidad",
  "effect.useGradient": "Usar degradado personalizado",
  "effect.spectrumNote": "Este efecto usa todo el espectro de color.",
  "effect.usesColor": "Usa tu color. Edítalo en la pestaña Fijo.",
  "effect.usesGradient": "Usa tu degradado. Edítalo en la pestaña Degradado.",

  "effect.breathing.label": "Respiración",
  "effect.rainbow.label": "Arcoíris",
  "effect.wave.label": "Onda",
  "effect.cycle.label": "Ciclo",
  "effect.spiral.label": "Espiral",
  "effect.spiral.legion.label": "Espiral GO",
  "effect.spiral.firmwareNote": "Efecto giratorio propio del firmware de tu Legion Go.",

  "ambient.gameModeBanner": "Aún no hay pantalla que leer. Ambilight funciona en Modo Juego con un juego abierto (no en Escritorio ni Big Picture).",
  "ambient.stickHint": "Las luces siguen la pantalla cerca de cada joystick. La izquierda desde arriba a la izquierda, la derecha desde el centro a la derecha.",
  "ambient.vividness": "Intensidad",
  "ambient.smoothing": "Suavizado",
  "ambient.captureRate": "Tasa de captura",

  "brightness.label": "Brillo",

  "about.title": "Acerca de",
  "about.version": "Versión {v}",
  "about.madeBy": "Hecho por {name}",
  "about.basedOn": "Motor de luces basado en HueSync.",

  "lang.spanish": "Español",
  "lang.english": "Inglés",

  "experimental.title": "Funciones experimentales",
  "experimental.description": "Estas funciones no han sido verificadas en este dispositivo. Puedes probarlas, pero puede que no funcionen bien. Estoy trabajando para darle soporte.",
  "experimental.feature.color": "Color",
  "experimental.feature.brightness": "Brillo",
  "experimental.feature.effects": "Efectos",
  "experimental.feature.ambilight": "Ambilight",

  "powerLed.section": "Luz del botón de encendido (experimental)",
  "powerLed.label": "Apagar luz del botón de encendido",
  "powerLed.warning": "Apaga el LED del botón de encendido.",
};

const en: Record<string, string> = {
  "load.error": "Couldn't load the plugin state. Please try again in a moment.",
  "load.retry": "Retry",

  "device.noLeds": "No controllable LEDs detected on this device.",
  "device.preview.rings": "Joystick rings",
  "device.preview.off": "Off",
  "device.preview.ambient": "Reacting to screen",

  "power.label": "Power",
  "reconnect.label": "Reconnect controllers",
  "reconnect.hint": "Lights not responding after sleep? Restart the connection to the controllers.",

  "mode.solid": "Solid",
  "mode.gradient": "Gradient",
  "mode.effect": "Effect",
  "mode.ambient": "Ambient",

  "color.hue": "Hue",
  "color.saturation": "Saturation",

  "gradient.edit": "Edit gradient",
  "gradient.title": "Create gradient",
  "gradient.tab.presets": "Presets",
  "gradient.tab.tune": "Tune",
  "gradient.tab.save": "Save",
  "gradient.zonesCaption": "{n} zones",
  "gradient.surprise": "Surprise me",
  "gradient.autoPalette": "Auto palette",
  "gradient.apply": "Apply",
  "gradient.cancel": "Cancel",
  "gradient.colorN": "Color {n}",
  "gradient.colorStart": "Start color",
  "gradient.colorEnd": "End color",
  "gradient.defaultGroup": "Lights",
  "gradient.speed": "Speed",
  "layout.leftStick": "Left stick",
  "layout.rightStick": "Right stick",
  "layout.lights": "Lights",

  "gradient.preset.sunset": "Sunset",
  "gradient.preset.ocean": "Ocean",
  "gradient.preset.aurora": "Aurora",
  "gradient.preset.neon": "Neon",
  "gradient.preset.lava": "Lava",
  "gradient.preset.mint": "Mint",
  "gradient.preset.vaporwave": "Vaporwave",
  "gradient.preset.forest": "Forest",
  "gradient.preset.galaxy": "Galaxy",
  "gradient.preset.ember": "Ember",
  "gradient.preset.ice": "Ice",
  "gradient.preset.candy": "Candy",

  "saved.sectionTitle": "My gradients",
  "saved.delete": "Delete",
  "saved.namePlaceholder": "Give it a name",
  "saved.suggest": "Another name",
  "saved.confirm": "Save",

  "effect.speed": "Speed",
  "effect.useGradient": "Use custom gradient",
  "effect.spectrumNote": "This effect uses the full color spectrum.",
  "effect.usesColor": "Uses your color. Edit it in the Solid tab.",
  "effect.usesGradient": "Uses your gradient. Edit it in the Gradient tab.",

  "effect.breathing.label": "Breathing",
  "effect.rainbow.label": "Rainbow",
  "effect.wave.label": "Wave",
  "effect.cycle.label": "Cycle",
  "effect.spiral.label": "Spiral",
  "effect.spiral.legion.label": "Spiral GO",
  "effect.spiral.firmwareNote": "Your Legion Go's built-in rotating firmware effect.",

  "ambient.gameModeBanner": "No screen to read yet. Ambient works in Game Mode with a game running (not in Desktop or Big Picture).",
  "ambient.stickHint": "Lights follow the screen near each stick. Left from the top-left, right from the mid-right.",
  "ambient.vividness": "Vividness",
  "ambient.smoothing": "Smoothing",
  "ambient.captureRate": "Capture rate",

  "brightness.label": "Brightness",

  "about.title": "About",
  "about.version": "Version {v}",
  "about.madeBy": "Made by {name}",
  "about.basedOn": "Lighting engine based on HueSync.",

  "lang.spanish": "Spanish",
  "lang.english": "English",

  "experimental.title": "Experimental features",
  "experimental.description": "These features have not been verified on this device. You can try them, but they may not work correctly. I'm working on support.",
  "experimental.feature.color": "Color",
  "experimental.feature.brightness": "Brightness",
  "experimental.feature.effects": "Effects",
  "experimental.feature.ambilight": "Ambilight",

  "powerLed.section": "Power button light (experimental)",
  "powerLed.label": "Turn off power button light",
  "powerLed.warning": "Turns off the power button LED.",
};

const DICTS: Record<Lang, Record<string, string>> = { es, en };

type Params = Record<string, string | number>;

interface I18nValue {
  lang: Lang;
  setLang: (lang: Lang) => void;
  t: (key: string, params?: Params) => string;
}

function translate(lang: Lang, key: string, params?: Params): string {
  const raw = DICTS[lang][key] ?? key;
  if (!params) return raw;
  return raw.replace(/\{(\w+)\}/g, (match, token) =>
    token in params ? String(params[token]) : match,
  );
}

const FALLBACK_I18N: I18nValue = {
  lang: "es",
  setLang: () => {},
  t: (key, params) => translate("es", key, params),
};

const I18nContext = createContext<I18nValue | null>(null);

function readInitialLang(): Lang {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === "es" || stored === "en") return stored;
  } catch {
    void 0;
  }
  return "es";
}

export const I18nProvider: FC<{ children: ReactNode }> = ({ children }) => {
  const [lang, setLangState] = useState<Lang>(readInitialLang);

  const setLang = useCallback((next: Lang) => {
    setLangState(next);
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      void 0;
    }
  }, []);

  const value = useMemo<I18nValue>(
    () => ({
      lang,
      setLang,
      t: (key, params) => translate(lang, key, params),
    }),
    [lang, setLang],
  );

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
};

export function useI18n(): I18nValue {
  return useContext(I18nContext) ?? FALLBACK_I18N;
}

const FlagES: FC = () => (
  <svg width={20} height={14} viewBox="0 0 20 14" xmlns="http://www.w3.org/2000/svg">
    <rect width={20} height={14} fill="#c60b1e" />
    <rect y={3.5} width={20} height={7} fill="#ffc400" />
  </svg>
);

const FlagEN: FC = () => (
  <svg width={20} height={14} viewBox="0 0 60 42" xmlns="http://www.w3.org/2000/svg">
    <rect width={60} height={42} fill="#012169" />
    <path d="M0,0 60,42 M60,0 0,42" stroke="#fff" strokeWidth={8} />
    <path d="M0,0 60,42 M60,0 0,42" stroke="#c8102e" strokeWidth={4} />
    <path d="M30,0 V42 M0,21 H60" stroke="#fff" strokeWidth={12} />
    <path d="M30,0 V42 M0,21 H60" stroke="#c8102e" strokeWidth={7} />
  </svg>
);

export const LangToggle: FC = () => {
  const { lang, setLang, t } = useI18n();

  const buttonStyle = (active: boolean): React.CSSProperties => ({
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    width: 28,
    height: 20,
    borderRadius: 5,
    cursor: "pointer",
    opacity: active ? 1 : 0.4,
    boxShadow: active ? "0 0 0 1.5px rgba(255,255,255,0.85)" : "0 0 0 1px rgba(255,255,255,0.15)",
    transition: "opacity 120ms ease, box-shadow 120ms ease",
  });

  return (
    <Focusable style={{ display: "flex", gap: 6, justifyContent: "flex-end", padding: "2px 2px 0" }}>
      <Focusable
        onActivate={() => setLang("es")}
        onClick={() => setLang("es")}
        aria-label={t("lang.spanish")}
        style={buttonStyle(lang === "es")}
      >
        <FlagES />
      </Focusable>
      <Focusable
        onActivate={() => setLang("en")}
        onClick={() => setLang("en")}
        aria-label={t("lang.english")}
        style={buttonStyle(lang === "en")}
      >
        <FlagEN />
      </Focusable>
    </Focusable>
  );
};
