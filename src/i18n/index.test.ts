import { afterEach, describe, expect, it, vi } from "vitest";
import { createElement, type ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";

type DropdownOption = {
  data: string;
  label: ReactNode;
};

type DropdownProps = {
  rgOptions: DropdownOption[];
  selectedOption: string;
  menuLabel: string;
  onChange: (option: DropdownOption) => void;
};

const { renderedDropdowns } = vi.hoisted(() => ({
  renderedDropdowns: [] as DropdownProps[],
}));

vi.mock("@decky/ui", async () => {
  const React = await import("react");
  return {
    Dropdown: (props: DropdownProps) => {
      renderedDropdowns.push(props);
      return React.createElement("div", { "aria-label": props.menuLabel });
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

function brazilianPortugueseCatalog(): Record<string, string> {
  return i18n.DICTS["pt-BR"];
}

afterEach(() => {
  renderedDropdowns.length = 0;
  vi.unstubAllGlobals();
});

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
      "experimental.description": "Diese Funktionen wurden auf diesem Gerät noch nicht geprüft. Du kannst sie ausprobieren, möglicherweise funktionieren sie aber noch nicht richtig. Ich arbeite noch an der Unterstützung für dieses Gerät.",
    });
  });

  it("looks up German strings and interpolates their parameters", () => {
    expect(i18n.translate("de", "profiles.game", { name: "Hades" })).toBe(
      "Spiel: Hades",
    );
  });
});

describe("Brazilian Portuguese catalog", () => {
  it("keeps product terms and key journeys natural in Brazilian Portuguese", () => {
    const portuguese = brazilianPortugueseCatalog();

    expect(portuguese).toMatchObject({
      "settings.language": "Idioma",
      "startup.remember.hint": "Ao definir uma cor, o Colores a salva e a aplica na inicialização. Desative esta opção para devolver o controle da barra ao SteamOS após reiniciar.",
      "forceControl.hint": "O Colores retoma o controle das luzes sempre que você o abre.",
      "performance.hint": "As luzes se preenchem como uma barra de acordo com o uso da GPU, do verde ao vermelho.",
      "experimental.description": "Estes recursos ainda não foram verificados neste dispositivo. Você pode testá-los, mas talvez ainda não funcionem corretamente. Estou trabalhando para oferecer suporte.",
    });
    expect(portuguese["forceControl.notice"]).toContain("RGB");
    expect(portuguese["startup.remember.hint"]).toContain("SteamOS");
    expect(portuguese["performance.hint"]).toContain("GPU");
    expect(i18n.translate("pt-BR", "profiles.game", { name: "Hades" })).toBe(
      "Jogo: Hades",
    );
  });
});

describe("Language persistence", () => {
  it.each([
    ["Italian", "it"],
    ["German", "de"],
    ["Brazilian Portuguese", "pt-BR"],
  ] as const)("restores a persisted %s selection", (_name, lang) => {
    vi.stubGlobal("localStorage", {
      getItem: vi.fn(() => lang),
      setItem: vi.fn(),
    });

    expect(i18n.readInitialLang()).toBe(lang);
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

describe("Language selector", () => {
  it("uses one compact dropdown and persists every language selection", () => {
    const setItem = vi.fn();
    vi.stubGlobal("localStorage", {
      getItem: vi.fn(() => "es"),
      setItem,
    });

    renderToStaticMarkup(
      createElement(i18n.I18nProvider, null, createElement(i18n.LanguageSelector)),
    );

    expect(renderedDropdowns).toHaveLength(1);
    const selector = renderedDropdowns[0];
    expect(selector.menuLabel).toBe("Idioma");
    expect(selector.selectedOption).toBe("es");
    expect(selector.rgOptions.map((option) => option.data)).toEqual([
      "es",
      "en",
      "it",
      "de",
      "pt-BR",
    ]);
    const renderedLabels = selector.rgOptions.map((option) =>
      renderToStaticMarkup(option.label),
    );
    expect(renderedLabels).toEqual([
      expect.stringContaining("Español"),
      expect.stringContaining("English"),
      expect.stringContaining("Italiano"),
      expect.stringContaining("Deutsch"),
      expect.stringContaining("Português (Brasil)"),
    ]);
    expect(
      renderedLabels.every((label, index) =>
        label.includes(`data-language-flag="${selector.rgOptions[index].data}"`),
      ),
    ).toBe(true);

    selector.rgOptions.forEach(selector.onChange);

    expect(setItem.mock.calls).toEqual([
      ["colores-lang", "es"],
      ["colores-lang", "en"],
      ["colores-lang", "it"],
      ["colores-lang", "de"],
      ["colores-lang", "pt-BR"],
    ]);
  });
});
