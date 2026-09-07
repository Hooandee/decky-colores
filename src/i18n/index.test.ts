import { afterEach, describe, expect, it, vi } from "vitest";
import { createElement, type ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";

type FocusableProps = {
  children?: ReactNode;
  onActivate?: () => void;
  onClick?: () => void;
  "aria-label"?: string;
};

const { renderedFocusables } = vi.hoisted(() => ({
  renderedFocusables: [] as FocusableProps[],
}));

vi.mock("@decky/ui", async () => {
  const React = await import("react");
  return {
    Focusable: ({ children, onActivate, onClick, ...props }: FocusableProps) => {
      renderedFocusables.push({ children, onActivate, onClick, ...props });
      return React.createElement("div", props, children);
    },
  };
});

import * as i18n from "./index";

const CATALOGS = i18n.SUPPORTED_LANGUAGES.map(
  (lang) => [lang, i18n.DICTS[lang]] as const,
);

function placeholders(value: string): string[] {
  return [...value.matchAll(/\{(\w+)\}/g)].map((match) => match[1]).sort();
}

function italianCatalog(): Record<string, string> {
  return i18n.DICTS.it;
}

function germanCatalog(): Record<string, string> {
  return i18n.DICTS.de;
}

describe("Every supported translation catalog", () => {
  it.each(CATALOGS)("%s has exactly the Spanish keys", (_lang, catalog) => {
    expect(Object.keys(catalog).sort()).toEqual(Object.keys(i18n.DICTS.es).sort());
  });

  it.each(CATALOGS)("%s preserves every interpolation placeholder", (_lang, catalog) => {
    for (const key of Object.keys(i18n.DICTS.es)) {
      expect(placeholders(catalog[key] ?? ""), key)
        .toEqual(placeholders(i18n.DICTS.es[key]));
    }
  });

  it.each(CATALOGS)("%s avoids em dashes in interface copy", (_lang, catalog) => {
    expect(Object.values(catalog).some((value) => value.includes("—"))).toBe(false);
  });

  it("keeps reviewed wording natural in every language", () => {
    expect(i18n.DICTS).toMatchObject({
      es: {
        "forceControl.hint": "Recupera el control de las luces al abrir Colores.",
        "performance.hint": "Las luces se llenan como una barra según la carga de la GPU, de verde a rojo.",
        "experimental.description": "Estas funciones no se han verificado en este dispositivo. Puedes probarlas, pero puede que aún no funcionen bien. Estoy trabajando para añadir compatibilidad.",
      },
      en: {
        "forceControl.hint": "Reclaims the lights whenever you open Colores.",
        "performance.hint": "The lights fill like a bar with GPU load, from green to red.",
        "experimental.description": "These features have not been verified on this device. You can try them, but they may not work correctly yet. I'm still working on support for this device.",
      },
      it: {
        "forceControl.hint": "Colores riprende il controllo delle luci ogni volta che lo apri.",
        "performance.hint": "Le luci si riempiono come una barra in base al carico della GPU, dal verde al rosso.",
        "experimental.description": "Queste funzioni non sono state verificate su questo dispositivo. Puoi provarle, ma potrebbero non funzionare correttamente. Sto lavorando per aggiungere il supporto.",
      },
      de: {
        "forceControl.hint": "Colores übernimmt beim Öffnen erneut die Kontrolle über die Beleuchtung.",
        "performance.hint": "Die Beleuchtung füllt sich je nach GPU-Auslastung wie eine Leiste von Grün bis Rot.",
        "experimental.description": "Diese Funktionen wurden auf diesem Gerät noch nicht geprüft. Du kannst sie ausprobieren, möglicherweise funktionieren sie aber noch nicht richtig. Ich arbeite noch an der Unterstützung für dieses Gerät.",
      },
    });
  });
});

describe("Italian catalog", () => {
  it("keeps the product name and established technical terms unchanged", () => {
    const italian = italianCatalog();
    for (const lang of ["es", "en", "it"] as const) {
      expect(i18n.DICTS[lang]["forceControl.label"]).toContain("Colores");
      expect(i18n.DICTS[lang]["forceControl.hint"]).toContain("Colores");
    }
    expect(italian["forceControl.notice"]).toContain("RGB");
    expect(italian["startup.remember.hint"]).toContain("SteamOS");
    expect(italian["performance.hint"]).toContain("GPU");
  });

  it("hands startup control back to SteamOS in natural Italian", () => {
    expect(italianCatalog()["startup.remember.hint"]).toBe(
      "Quando imposti un colore, Colores lo salva e lo usa all'avvio. Disattiva questa opzione per restituire a SteamOS il controllo della barra al riavvio.",
    );
  });

  it("calls the accent the primary color", () => {
    expect(italianCatalog()["customize.accent"]).toBe("Colore principale");
  });

  it("uses a natural prompt for naming a gradient", () => {
    expect(italianCatalog()["saved.namePlaceholder"]).toBe("Assegna un nome");
  });

  it("describes Ambilight capture in natural Italian", () => {
    expect(italianCatalog()["ambient.gameModeBanner"]).toBe(
      "Non c’è ancora una schermata da acquisire. Ambilight funziona in modalità Gioco quando è aperto un gioco, non in modalità Desktop né in Big Picture.",
    );
  });

  it("looks up Italian strings and interpolates their parameters", () => {
    expect(i18n.translate("it", "profiles.game", { name: "Hades" })).toBe(
      "Gioco: Hades",
    );
  });

  it("preserves the existing fallback and unresolved-placeholder behavior", () => {
    expect(i18n.translate("it", "missing.key")).toBe("missing.key");
    expect(i18n.translate("it", "profiles.game")).toBe("Gioco: {name}");
  });
});

describe("German catalog", () => {
  it("keeps product and technical terms unchanged", () => {
    const german = germanCatalog();
    expect(german["forceControl.label"]).toContain("Colores");
    expect(german["forceControl.notice"]).toContain("RGB");
    expect(german["startup.remember.hint"]).toContain("SteamOS");
    expect(german["performance.hint"]).toContain("GPU");
  });

  it("uses natural German wording on key user journeys", () => {
    expect(germanCatalog()).toMatchObject({
      "startup.remember.hint": "Wenn du eine Farbe festlegst, speichert Colores sie und verwendet sie beim Start. Deaktiviere diese Option, damit SteamOS nach einem Neustart wieder die Beleuchtung steuert.",
      "customize.accent": "Primärfarbe",
      "saved.namePlaceholder": "Gib einen Namen ein",
      "ambient.gameModeBanner": "Noch ist kein Bild zum Erfassen vorhanden. Ambilight funktioniert im Spielemodus, sobald ein Spiel läuft, nicht im Desktopmodus oder in Big Picture.",
      "effect.breathing.label": "Pulsieren",
      "effect.spiral.firmwareNote": "Der in deiner Legion Go integrierte Dreheffekt der Firmware.",
      "battery.breathe.label": "Beim Laden pulsieren",
      "lang.german": "Deutsch",
      "experimental.description": "Diese Funktionen wurden auf diesem Gerät noch nicht geprüft. Du kannst sie ausprobieren, möglicherweise funktionieren sie aber noch nicht richtig. Ich arbeite noch an der Unterstützung für dieses Gerät.",
    });
  });

  it("looks up German strings and interpolates their parameters", () => {
    expect(i18n.translate("de", "profiles.game", { name: "Hades" })).toBe(
      "Spiel: Hades",
    );
  });
});

describe("Italian persistence", () => {
  afterEach(() => {
    renderedFocusables.length = 0;
    vi.unstubAllGlobals();
  });

  it("restores a persisted Italian selection", () => {
    vi.stubGlobal("localStorage", {
      getItem: vi.fn(() => "it"),
      setItem: vi.fn(),
    });

    expect(i18n.readInitialLang()).toBe("it");
  });

  it("offers an Italian selector for controller and pointer activation", () => {
    const setItem = vi.fn();
    vi.stubGlobal("localStorage", {
      getItem: vi.fn(() => "es"),
      setItem,
    });

    renderToStaticMarkup(
      createElement(i18n.I18nProvider, null, createElement(i18n.LangToggle)),
    );
    const italianButton = renderedFocusables.find(
      (props) => props["aria-label"] === "Italiano",
    );

    expect(italianButton).toBeDefined();
    italianButton?.onActivate?.();
    italianButton?.onClick?.();
    expect(setItem).toHaveBeenCalledTimes(2);
    expect(setItem).toHaveBeenNthCalledWith(1, "colores-lang", "it");
    expect(setItem).toHaveBeenNthCalledWith(2, "colores-lang", "it");
  });

  it("keeps Spanish as the safe fallback", () => {
    vi.stubGlobal("localStorage", {
      getItem: vi.fn(() => "unsupported"),
      setItem: vi.fn(),
    });
    expect(i18n.readInitialLang()).toBe("es");

    vi.stubGlobal("localStorage", {
      getItem: vi.fn(() => {
        throw new Error("storage unavailable");
      }),
      setItem: vi.fn(),
    });
    expect(i18n.readInitialLang()).toBe("es");
  });
});

describe("German persistence", () => {
  afterEach(() => {
    renderedFocusables.length = 0;
    vi.unstubAllGlobals();
  });

  it("restores a persisted German selection", () => {
    vi.stubGlobal("localStorage", {
      getItem: vi.fn(() => "de"),
      setItem: vi.fn(),
    });

    expect(i18n.readInitialLang()).toBe("de");
  });

  it("offers a German selector for controller and pointer activation", () => {
    const setItem = vi.fn();
    vi.stubGlobal("localStorage", {
      getItem: vi.fn(() => "es"),
      setItem,
    });

    renderToStaticMarkup(
      createElement(i18n.I18nProvider, null, createElement(i18n.LangToggle)),
    );
    const germanButton = renderedFocusables.find(
      (props) => props["aria-label"] === "Alemán",
    );

    expect(germanButton).toBeDefined();
    germanButton?.onActivate?.();
    germanButton?.onClick?.();
    expect(setItem).toHaveBeenNthCalledWith(1, "colores-lang", "de");
    expect(setItem).toHaveBeenNthCalledWith(2, "colores-lang", "de");
  });
});
