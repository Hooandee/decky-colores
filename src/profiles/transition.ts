import { ProfileScope, ProfileState } from "../types";
import { profileStateForApp } from "./scope";

export interface ProfileTarget {
  scope: ProfileScope;
  appKey: string | null;
}

interface ProfileTransitionApi {
  getProfileState: (
    scope: ProfileScope,
    appKey: string | null,
  ) => Promise<ProfileState>;
}

interface ProfileTransitionHandlers {
  onStart: (appKey: string | null) => void;
  onAccept: (appKey: string | null, state: ProfileState) => void;
  onError: (appKey: string | null, error: unknown) => void;
}

export interface ProfileTransitionRunner {
  run: (
    appKey: string | null,
    operation: () => Promise<ProfileState>,
  ) => Promise<void>;
  isActiveFor: (appKey: string | null) => boolean;
}

export function createProfileTransitionRunner(
  api: ProfileTransitionApi,
  handlers: ProfileTransitionHandlers,
): ProfileTransitionRunner {
  let epoch = 0;
  let activeAppKey: string | null | undefined;

  return {
    async run(appKey, operation) {
      const requestEpoch = ++epoch;
      activeAppKey = appKey;
      handlers.onStart(appKey);

      let profileState: ProfileState;
      try {
        profileState = await operation();
      } catch {
        if (requestEpoch !== epoch) return;
        try {
          profileState = await profileStateForApp(appKey, api);
        } catch (error) {
          if (requestEpoch === epoch) handlers.onError(appKey, error);
          return;
        }
      }

      if (requestEpoch !== epoch) return;
      activeAppKey = undefined;
      handlers.onAccept(appKey, profileState);
    },
    isActiveFor(appKey) {
      return activeAppKey === appKey;
    },
  };
}

export function snapshotProfileTarget(target: ProfileTarget): ProfileTarget {
  return { ...target };
}

export interface StateSnapshotGate {
  begin: () => number;
  invalidate: () => void;
  isCurrent: (token: number) => boolean;
}

export function createStateSnapshotGate(): StateSnapshotGate {
  let epoch = 0;
  return {
    begin() {
      return ++epoch;
    },
    invalidate() {
      epoch += 1;
    },
    isCurrent(token) {
      return token === epoch;
    },
  };
}
