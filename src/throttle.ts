export interface Throttled<A extends unknown[]> {
  run: (...args: A) => void;
  flush: () => Promise<void>;
  cancel: () => void;
}

export function createThrottle<A extends unknown[]>(
  fn: (...args: A) => void | Promise<unknown>,
  ms: number,
): Throttled<A> {
  let last: number | null = null;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let latest: A | undefined;
  let inFlight = Promise.resolve();

  const invoke = (args: A) => {
    latest = undefined;
    last = Date.now();
    const result = Promise.resolve(fn(...args));
    inFlight = Promise.all([inFlight, result]).then(
      () => undefined,
      () => undefined,
    );
  };

  const run = (...args: A) => {
    latest = args;
    const elapsed = last === null ? ms : Date.now() - last;
    if (elapsed >= ms) {
      if (timer !== undefined) clearTimeout(timer);
      timer = undefined;
      invoke(args);
    } else if (timer === undefined) {
      timer = setTimeout(() => {
        timer = undefined;
        if (latest) invoke(latest);
      }, ms - elapsed);
    }
  };

  return {
    run,
    async flush() {
      if (timer !== undefined) clearTimeout(timer);
      timer = undefined;
      if (latest) invoke(latest);
      await inFlight;
    },
    cancel() {
      if (timer !== undefined) clearTimeout(timer);
      timer = undefined;
      latest = undefined;
    },
  };
}
