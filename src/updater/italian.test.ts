import { describe, expect, it } from "vitest";
import type { Lang } from "../i18n";
import { getUpdaterStrings } from "./strings";

describe("Italian updater copy", () => {
  it("returns all updater strings from one catalog", () => {
    expect(getUpdaterStrings("it")).toEqual({
      panel: {
        version: "Versione",
        latest: "(più recente)",
        newPrefix: "disponibile",
        checking: "verifica in corso…",
        check: "Cerca aggiornamenti",
        update: "Scopri le novità e installa",
        error: "Verifica non riuscita. Controlla la connessione.",
      },
      modal: {
        title: "Novità",
        noNotes: "Nessuna nota per questa versione.",
        install: "Installa l'aggiornamento",
        installing: "Installazione in corso…",
        installed: "Aggiornamento installato.",
        restartNote: "Riavvia Decky per applicare l'aggiornamento.",
        restart: "Riavvia Decky",
        failed: "Installazione non riuscita. Riprova.",
      },
      availableTitle: "Aggiornamento disponibile",
    });
  });

  it("does not use em dashes on any Italian updater surface", () => {
    const { panel, modal, availableTitle } = getUpdaterStrings("it");
    const values = [...Object.values(panel), ...Object.values(modal), availableTitle];

    expect(values.some((value) => value.includes("—"))).toBe(false);
  });

  it("falls back to the English catalog for an invalid runtime language", () => {
    expect(getUpdaterStrings("unsupported" as Lang)).toEqual({
      panel: {
        version: "Version",
        latest: "(latest)",
        newPrefix: "new",
        checking: "checking…",
        check: "Check for updates",
        update: "See what's new & install",
        error: "Couldn't check. Check your connection.",
      },
      modal: {
        title: "What's new",
        noNotes: "No notes for this release.",
        install: "Install update",
        installing: "Installing…",
        installed: "Update installed.",
        restartNote: "Restart Decky to apply it.",
        restart: "Restart Decky",
        failed: "Install failed. Please try again.",
      },
      availableTitle: "Update available",
    });
  });
});
