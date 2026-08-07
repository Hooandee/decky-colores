import { ProfileScope } from "../types";

export function nextProfileScope(
  current: ProfileScope,
  runningApp: { key: string } | null,
): ProfileScope {
  return current === "game" && runningApp === null ? "global" : current;
}
