export interface RefreshQueue {
  request: () => void;
  block: () => void;
  release: () => void;
}

export function createRefreshQueue(run: () => Promise<void>): RefreshQueue {
  let blocked = false;
  let inFlight = false;
  let queued = false;

  const drain = async () => {
    if (blocked || inFlight || !queued) return;
    queued = false;
    inFlight = true;
    try {
      await run();
    } finally {
      inFlight = false;
      if (!blocked && queued) void drain();
    }
  };

  return {
    request() {
      queued = true;
      void drain();
    },
    block() {
      blocked = true;
      if (inFlight) queued = true;
    },
    release() {
      blocked = false;
      void drain();
    },
  };
}
