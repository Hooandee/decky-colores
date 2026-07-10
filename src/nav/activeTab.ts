const KEY = "colores:activeTab";

export function readActiveTab(): string | null {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

export function writeActiveTab(id: string): void {
  try {
    localStorage.setItem(KEY, id);
  } catch {
    void 0;
  }
}
