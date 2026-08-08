import { describe, expect, it } from "vitest";
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
});
