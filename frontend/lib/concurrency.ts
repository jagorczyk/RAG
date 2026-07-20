/**
 * Bounded parallel map: runs at most `concurrency` async workers at a time.
 * All items are attempted; individual failures settle as rejected results
 * without aborting siblings.
 */
export async function mapWithConcurrency<T, R>(
  items: readonly T[],
  concurrency: number,
  mapper: (item: T, index: number) => Promise<R>
): Promise<PromiseSettledResult<R>[]> {
  const total = items.length;
  if (total === 0) {
    return [];
  }

  const limit = Math.max(1, Math.min(total, Math.floor(concurrency) || 1));
  const results: PromiseSettledResult<R>[] = new Array(total);
  let nextIndex = 0;

  async function worker(): Promise<void> {
    while (true) {
      const index = nextIndex++;
      if (index >= total) {
        return;
      }
      try {
        const value = await mapper(items[index], index);
        results[index] = { status: "fulfilled", value };
      } catch (reason) {
        results[index] = { status: "rejected", reason };
      }
    }
  }

  await Promise.all(Array.from({ length: limit }, () => worker()));
  return results;
}

/** Default client-side multi-file upload concurrency (bounded pool). */
export const DEFAULT_UPLOAD_CONCURRENCY = 3;
