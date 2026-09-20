import { ProfileScope, ProfileState } from "../types";

interface ProfileScopeApi {
  getProfileState: (
    scope: ProfileScope,
    appKey: string | null,
  ) => Promise<ProfileState>;
  setProfileFollowGlobal: (
    appKey: string,
    follow: boolean,
  ) => Promise<ProfileState>;
}

export function profileScopeFor(
  appKey: string | null,
  followsGlobal: boolean,
): ProfileScope {
  return appKey !== null && !followsGlobal ? "game" : "global";
}

export async function selectProfileScope(
  scope: ProfileScope,
  appKey: string | null,
  api: ProfileScopeApi,
): Promise<ProfileState | null> {
  if (appKey === null) {
    return scope === "global" ? api.getProfileState("global", null) : null;
  }
  if (scope === "game") return api.setProfileFollowGlobal(appKey, false);
  const globalState = await api.getProfileState("global", null);
  await api.setProfileFollowGlobal(appKey, true);
  return globalState;
}

export async function profileStateForApp(
  appKey: string | null,
  api: Pick<ProfileScopeApi, "getProfileState">,
): Promise<ProfileState> {
  if (appKey === null) return api.getProfileState("global", null);
  const gameState = await api.getProfileState("game", appKey);
  return gameState.followsGlobal
    ? api.getProfileState("global", null)
    : gameState;
}
