export function shouldReportApp(
  target: string | null,
  committed: string | null,
  inFlight: string | null | undefined,
): boolean {
  return inFlight === undefined && target !== committed;
}
