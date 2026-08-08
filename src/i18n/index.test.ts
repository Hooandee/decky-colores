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

function placeholders(value: string): string[] {
  return [...value.matchAll(/\{(\w+)\}/g)].map((match) => match[1]).sort();
}

function italianCatalog(): Record<string, string> {
  return i18n.DICTS.it;
}

describe("Italian catalog", () => {
  it("has exactly the same keys as Spanish", () => {
    const spanish = i18n.DICTS.es;
    expect(Object.keys(italianCatalog()).sort()).toEqual(Object.keys(spanish).sort());
  });

  it("preserves every interpolation placeholder", () => {
    const spanish = i18n.DICTS.es;
    const italian = italianCatalog();

    for (const key of Object.keys(spanish)) {
      expect(placeholders(italian[key] ?? ""), key).toEqual(placeholders(spanish[key]));
    }
  });

  it("does not contain em dashes", () => {
    expect(Object.values(italianCatalog()).some((value) => value.includes("—"))).toBe(false);
  });

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
      "Quando imposti un colore, Colores lo salva e lo usa all'avvio. Disattivalo per restituire a SteamOS il controllo della barra al riavvio.",
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
      "Non c’è ancora una schermata da acquisire. Ambilight funziona in GameMode quando è aperto un gioco, non in modalità Desktop né in Big Picture.",
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
