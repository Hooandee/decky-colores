import { FC, ReactNode, createContext, useCallback, useContext, useMemo, useState } from "react";
import { Focusable } from "@decky/ui";

export type Lang = "es" | "en";

const STORAGE_KEY = "colores-lang";

const es: Record<string, string> = {
  "device.noLeds": "No se detectaron LEDs controlables en este dispositivo.",
  "device.preview.rings": "Anillos del joystick",
  "device.preview.off": "Apagado",
  "device.preview.ambient": "Siguiendo la pantalla",

  "power.label": "Encendido",

  "mode.solid": "Fijo",
  "mode.gradient": "Degradado",
  "mode.effect": "Efecto",
  "mode.ambient": "Ambilight",

  "color.hue": "Tono",
  "color.saturation": "Saturación",

  "gradient.edit": "Editar degradado",
  "gradient.title": "Crear degradado",
  "gradient.subtitle": "Dos colores por joystick. Empieza con un preset o ajústalos a mano.",
  "gradient.presets": "Presets",
  "gradient.wizard": "Asistente",
  "gradient.surprise": "Sorpréndeme",
  "gradient.autoPalette": "Auto-paleta",
  "gradient.apply": "Aplicar",
  "gradient.cancel": "Cancelar",
  "gradient.colorN": "Color {n}",
  "gradient.color": "Color",
  "gradient.defaultGroup": "Luces",

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
  "saved.save": "Guardar degradado",
  "saved.delete": "Borrar",
  "saved.namePlaceholder": "Ponle un nombre",
  "saved.suggest": "Otro nombre",
  "saved.confirm": "Guardar",

  "effect.speed": "Velocidad",
  "effect.useGradient": "Usar degradado personalizado",
  "effect.spectrumNote": "Este efecto usa todo el espectro de color.",

  "effect.breathing.label": "Respiración",
  "effect.breathing.desc": "Un solo color se desvanece suavemente.",
  "effect.rainbow.label": "Arcoíris",
  "effect.rainbow.desc": "Recorre todo el espectro de color sin cortes.",
  "effect.wave.label": "Onda",
  "effect.wave.desc": "Los colores fluyen por el anillo como una ola.",
  "effect.cycle.label": "Ciclo",
  "effect.cycle.desc": "Pasa por una secuencia de colores vivos.",

  "ambient.gameModeBanner": "Aún no hay pantalla que leer. Ambilight funciona en Modo Juego con un juego abierto (no en Escritorio ni Big Picture).",
  "ambient.stickHint": "Las luces siguen la pantalla cerca de cada joystick. La izquierda desde arriba a la izquierda, la derecha desde el centro a la derecha.",
  "ambient.vividness": "Intensidad",
  "ambient.smoothing": "Suavizado",
  "ambient.captureRate": "Tasa de captura",

  "brightness.label": "Brillo",

  "lang.spanish": "Español",
  "lang.english": "Inglés",
};

const en: Record<string, string> = {
  "device.noLeds": "No controllable LEDs detected on this device.",
  "device.preview.rings": "Joystick rings",
  "device.preview.off": "Off",
  "device.preview.ambient": "Reacting to screen",

  "power.label": "Power",

  "mode.solid": "Solid",
  "mode.gradient": "Gradient",
  "mode.effect": "Effect",
  "mode.ambient": "Ambient",

  "color.hue": "Hue",
  "color.saturation": "Saturation",

  "gradient.edit": "Edit gradient",
  "gradient.title": "Create gradient",
  "gradient.subtitle": "Two colors per stick. Start from a preset or tune them by hand.",
  "gradient.presets": "Presets",
  "gradient.wizard": "Wizard",
  "gradient.surprise": "Surprise me",
  "gradient.autoPalette": "Auto palette",
  "gradient.apply": "Apply",
  "gradient.cancel": "Cancel",
  "gradient.colorN": "Color {n}",
  "gradient.color": "Color",
  "gradient.defaultGroup": "Lights",

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
  "saved.save": "Save gradient",
  "saved.delete": "Delete",
  "saved.namePlaceholder": "Give it a name",
  "saved.suggest": "Another name",
  "saved.confirm": "Save",

  "effect.speed": "Speed",
  "effect.useGradient": "Use custom gradient",
  "effect.spectrumNote": "This effect uses the full color spectrum.",

  "effect.breathing.label": "Breathing",
  "effect.breathing.desc": "A single color gently fades in and out.",
  "effect.rainbow.label": "Rainbow",
  "effect.rainbow.desc": "Smoothly cycles through the full color spectrum.",
  "effect.wave.label": "Wave",
  "effect.wave.desc": "Colors ripple across the ring like a moving wave.",
  "effect.cycle.label": "Cycle",
  "effect.cycle.desc": "Steps through a sequence of vivid solid colors.",

  "ambient.gameModeBanner": "No screen to read yet. Ambient works in Game Mode with a game running (not in Desktop or Big Picture).",
  "ambient.stickHint": "Lights follow the screen near each stick. Left from the top-left, right from the mid-right.",
  "ambient.vividness": "Vividness",
  "ambient.smoothing": "Smoothing",
  "ambient.captureRate": "Capture rate",

  "brightness.label": "Brightness",

  "lang.spanish": "Spanish",
  "lang.english": "English",
};

const DICTS: Record<Lang, Record<string, string>> = { es, en };

interface I18nValue {
  lang: Lang;
  setLang: (lang: Lang) => void;
  t: (key: string) => string;
}

const I18nContext = createContext<I18nValue | null>(null);

function readInitialLang(): Lang {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === "es" || stored === "en") return stored;
  } catch {
    return "es";
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
      t: (key: string) => DICTS[lang][key] ?? key,
    }),
    [lang, setLang],
  );

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
};

export function useI18n(): I18nValue {
  const ctx = useContext(I18nContext);
  if (!ctx) return { lang: "es", setLang: () => {}, t: (key: string) => DICTS.es[key] ?? key };
  return ctx;
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
