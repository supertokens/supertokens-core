import * as fs from 'fs';

export const LICENSE_FOR_TEST =
  'E1yITHflaFS4BPm7n0bnfFCjP4sJoTERmP0J=kXQ5YONtALeGnfOOe2rf2QZ0mfOh0aO3pBqfF-S0jb0ABpat6pySluTpJO6jieD6tzUOR1HrGjJO=50Ob3mHi21tQH1';

export const createStInstanceForTest = async () => {
  return {
    deployment_id: '1234567890',
    core_url: 'http://localhost:3567',
    api_key: 'qwertyuiopasdfghjklzxcvbnm',
  };
};

export const deleteStInstance = async (deploymentId: string) => {
  // noop
};

export const formatTime = (ms: number): string => {
  const seconds = Math.floor(ms / 1000);
  if (seconds < 60) {
    return `${seconds}s`;
  }
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  return `${minutes}m ${remainingSeconds}s`;
};

export const workInBatches = async <T>(
  count: number,
  numberOfBatches: number,
  work: (idx: number) => Promise<T>
): Promise<T[]> => {
  const batchSize = Math.ceil(count / numberOfBatches);
  const batches = [];
  let workCount = 0;

  const st = Date.now();
  let done = numberOfBatches;

  for (let b = 0; b < numberOfBatches; b++) {
    batches.push(
      (async () => {
        const startIndex = b * batchSize;
        const endIndex = Math.min(startIndex + batchSize, count);
        const batchResults: T[] = [];
        for (let i = startIndex; i < endIndex; i++) {
          batchResults.push(await work(i));
          workCount++;
        }
        done--;
        return batchResults;
      })()
    );
  }

  batches.push(
    (async () => {
      while (done > 0) {
        await new Promise((resolve) => setTimeout(resolve, 5000));
        const en = Date.now();
        console.log(
          `        Progress: Time=${formatTime(en - st)}, Completed=${workCount}, Throughput=${Math.round((workCount / (en - st)) * 10000) / 10}/s`
        );
      }
      return [];
    })()
  );

  const results = await Promise.all(batches);
  return results.flat();
};

export const setupLicense = async (coreUrl: string, apiKey: string) => {
  try {
    const response = await fetch(`${coreUrl}/ee/license`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'api-key': apiKey,
      },
      body: JSON.stringify({
        licenseKey: LICENSE_FOR_TEST,
      }),
    });

    if (!response.ok) {
      throw new Error(`Failed with status: ${response.status}`);
    }
    const responseText = await response.text();
    console.log('License response:', responseText);

    console.log('License key set successfully');
  } catch (error) {
    console.error('Failed to set license key:', error);
    throw error;
  }
};

/**
 * Per-step duration budgets in milliseconds. A step whose measured duration
 * exceeds its budget fails the run (see StatsCollector.throwIfOverBudget).
 *
 * These are deliberately generous order-of-magnitude tripwires — they exist to
 * catch a query path that has gone from milliseconds to minutes (a plan
 * regression / missing index), NOT to gate on CI noise. The defaults below are
 * placeholders that MUST be calibrated to ~5x a recorded baseline run once one
 * exists (PLAN-006 rollout checklist). Steps without an explicit budget fall
 * back to DEFAULT_BUDGET_MS.
 *
 * Everything here is env-overridable:
 *   STRESS_TEST_STEP_BUDGETS_MS - JSON object merged over the defaults, e.g.
 *       STRESS_TEST_STEP_BUDGETS_MS='{"Pagination full walk (newest first)":2400000}'
 *   STRESS_TEST_BUDGET_MULTIPLIER - scales every budget (default 1), e.g. "2"
 *   STRESS_TEST_ENFORCE_BUDGETS - "false" to measure + report without failing
 */
export const DEFAULT_BUDGET_MS = 120_000;

export const DEFAULT_STEP_BUDGETS_MS: Record<string, number> = {
  // Existing seeding steps (kept generous; seeding dominates the run).
  'Loading users for bulk import': 1_800_000,
  'Waiting for users to be imported': 3_600_000,
  // Two-size run: the bulk import is split into a first ~100k checkpoint tranche
  // and the remainder to 1M (see the scaling-ratio harness in index.ts).
  'Loading users for bulk import (100k checkpoint)': 600_000,
  'Waiting for import (100k checkpoint)': 1_800_000,
  'Loading users for bulk import (remaining to 1M)': 1_800_000,
  'Waiting for import (remaining to 1M)': 3_600_000,
  'Emailpassword users creation': 1_800_000,
  'Passwordless users (with email) creation': 1_800_000,
  'Passwordless users (with phone) creation': 1_800_000,
  'ThirdParty users (google) creation': 1_800_000,
  'ThirdParty users (facebook) creation': 1_800_000,
  'Linking accounts': 1_800_000,
  'Create user id mappings': 1_800_000,
  'Adding roles': 1_800_000,
  'Creating sessions': 1_800_000,
  // Scale-sensitive read/query paths measured against the 1M-user state.
  'Pagination first page (newest first)': 60_000,
  'Pagination full walk (newest first)': 1_800_000,
  'Pagination first page (oldest first)': 60_000,
  'Pagination full walk (oldest first)': 1_800_000,
  'User count (tenant: public)': 120_000,
  'User count (all tenants)': 120_000,
  'Dashboard search by email prefix': 120_000,
  'Dashboard search by provider': 120_000,
  'Dashboard search by email + provider': 120_000,
  'Third-party sign-in for existing user': 60_000,
  'Email update (linked user)': 60_000,
  'Phone update (linked user)': 60_000,
  'Associate linked user to tenant': 120_000,
  'Disassociate linked user from tenant': 120_000,
  'canLinkAccounts precheck': 60_000,
  'Unlink account': 60_000,
  'Delete user (full, linked)': 120_000,
  'Active users count': 300_000,
  'Active users count (with more-than-one-login-method window)': 300_000,
  'Feature-flag usage stats aggregate': 600_000,
  'List users for role (large share)': 300_000,
  'Delete role (large share)': 300_000,
  'TOTP verify (user with many used codes)': 60_000,
  'Email-verification status update (mapped user)': 60_000,
  'Delete user with userid mapping': 120_000,
};

let cachedBudgets: Record<string, number> | undefined;

const resolveBudgets = (): Record<string, number> => {
  if (cachedBudgets) return cachedBudgets;
  let overrides: Record<string, number> = {};
  const raw = process.env.STRESS_TEST_STEP_BUDGETS_MS;
  if (raw) {
    try {
      overrides = JSON.parse(raw);
    } catch (e) {
      console.warn(`    Ignoring invalid STRESS_TEST_STEP_BUDGETS_MS: ${(e as Error).message}`);
    }
  }
  const multiplier = Number(process.env.STRESS_TEST_BUDGET_MULTIPLIER ?? '1') || 1;
  const merged: Record<string, number> = {};
  for (const [k, v] of Object.entries({ ...DEFAULT_STEP_BUDGETS_MS, ...overrides })) {
    merged[k] = Math.round(v * multiplier);
  }
  cachedBudgets = merged;
  return merged;
};

export const getStepBudgetMs = (title: string): number => {
  const budgets = resolveBudgets();
  return (
    budgets[title] ??
    Math.round(DEFAULT_BUDGET_MS * (Number(process.env.STRESS_TEST_BUDGET_MULTIPLIER ?? '1') || 1))
  );
};

export const budgetsEnforced = (): boolean =>
  (process.env.STRESS_TEST_ENFORCE_BUDGETS ?? 'true').toLowerCase() !== 'false';

export class StatsCollector {
  private static instance: StatsCollector;
  private measurements: { title: string; timeMs: number; budgetMs: number }[] = [];

  private constructor() {}

  public static getInstance(): StatsCollector {
    if (!StatsCollector.instance) {
      StatsCollector.instance = new StatsCollector();
    }
    return StatsCollector.instance;
  }

  public addMeasurement(title: string, timeMs: number) {
    this.measurements.push({ title, timeMs, budgetMs: getStepBudgetMs(title) });
  }

  public getStats() {
    return this.measurements;
  }

  public writeToFile(extra: Record<string, unknown> = {}) {
    const ratios = RatioCollector.getInstance();
    const formattedMeasurements = this.measurements.map((measurement) => {
      const base = {
        title: measurement.title,
        ms: measurement.timeMs,
        formatted: formatTime(measurement.timeMs),
        budgetMs: measurement.budgetMs,
        budgetFormatted: formatTime(measurement.budgetMs),
        overBudget: measurement.timeMs > measurement.budgetMs,
        status: measurement.timeMs > measurement.budgetMs ? 'OVER BUDGET' : 'OK',
      };
      // Merge in the two-size scaling ratio for steps measured at both sizes so
      // the summary table can show small/large/ratio columns per step.
      const r = ratios.resultFor(measurement.title);
      if (!r) return base;
      const ratio = Math.round(r.ratio * 100) / 100;
      return {
        ...base,
        scaleClass: r.scaleClass,
        smallMs: r.smallMs,
        smallFormatted: formatTime(r.smallMs),
        ratio,
        ratioBound: r.bound,
        ratioOverBound: r.overBound,
        ratioStatus: r.overBound ? 'OVER RATIO' : 'OK',
        ratioText: `${ratio.toFixed(1)}× (≤ ${r.bound}) ${r.overBound ? '⚠️' : '✅'}`,
      };
    });

    const stats = {
      measurements: formattedMeasurements,
      timestamp: new Date().toISOString(),
      ...extra,
    };
    fs.writeFileSync('stats.json', JSON.stringify(stats, null, 2));
  }

  /**
   * Fails the run if any measured step blew past its duration budget. Called at
   * the very end so every step still appears in stats.json and the summary
   * table before the run is failed. Honors STRESS_TEST_ENFORCE_BUDGETS=false,
   * which reports over-budget steps without failing.
   */
  public throwIfOverBudget() {
    const over = this.measurements.filter((m) => m.timeMs > m.budgetMs);
    if (over.length === 0) {
      console.log('\nAll measured steps completed within their duration budgets.');
      return;
    }
    console.error('\nSteps exceeding their duration budget:');
    for (const m of over) {
      console.error(
        `  ${m.title}: ${formatTime(m.timeMs)} > budget ${formatTime(m.budgetMs)} (${m.timeMs}ms > ${m.budgetMs}ms)`
      );
    }
    if (!budgetsEnforced()) {
      console.error('\nSTRESS_TEST_ENFORCE_BUDGETS=false — reporting only, not failing the run.');
      return;
    }
    throw new Error(
      `${over.length} step(s) exceeded their duration budget; likely an order-of-magnitude query regression.`
    );
  }
}

// ---------------------------------------------------------------------------
// Two-size scaling-ratio harness.
//
// Absolute duration budgets catch big regressions but can't tell a slow runner
// apart from pathological scaling. The read-path steps are therefore measured
// at two dataset sizes — a ~100k-user checkpoint mid-seed ("small") and the
// full 1M ("large") — and the per-step cost ratio time(1M)/time(100k) is
// asserted against a per-class bound. That ratio is hardware-independent: a
// step that is meant to be O(1) in total user count but grows with it shows a
// ratio well above 1, which is exactly the regression class this suite exists
// to catch.
//
// measureTime consults the ambient checkpoint (set by runReadPaths): during the
// small pass it records only into the RatioCollector; during the large pass it
// records into both the StatsCollector (so the existing summary/budget table is
// unchanged) and the RatioCollector; outside any checkpoint (seeding steps) it
// records only into the StatsCollector, as before.
// ---------------------------------------------------------------------------

export type CheckpointSize = 'small' | 'large';

let currentCheckpoint: CheckpointSize | undefined;

export const setCheckpoint = (size: CheckpointSize | undefined): void => {
  currentCheckpoint = size;
};

export const getCheckpoint = (): CheckpointSize | undefined => currentCheckpoint;

export type ScaleClass = 'O(1)' | 'O(n)';

/**
 * Expected scaling class of each measured read-path step in total user count.
 * O(1) steps (single-user lookups/writes, sign-in, first-page pagination, TOTP
 * verify) must stay roughly flat as the dataset grows 10x; O(n) steps (counts,
 * full pagination walk, analytics aggregates, large-share role listing) may
 * grow with the data. Steps not listed default to the lenient O(n) bound.
 */
export const STEP_SCALE_CLASS: Record<string, ScaleClass> = {
  'Pagination first page (newest first)': 'O(1)',
  'Pagination first page (oldest first)': 'O(1)',
  'Pagination full walk (newest first)': 'O(n)',
  'Pagination full walk (oldest first)': 'O(n)',
  'User count (all tenants)': 'O(n)',
  'User count (tenant: public)': 'O(n)',
  'Dashboard search by email prefix': 'O(n)',
  'Dashboard search by provider': 'O(n)',
  'Dashboard search by email + provider': 'O(n)',
  'Third-party sign-in for existing user': 'O(1)',
  'Email update (linked user)': 'O(1)',
  'Phone update (linked user)': 'O(1)',
  'Associate linked user to tenant': 'O(1)',
  'Disassociate linked user from tenant': 'O(1)',
  'canLinkAccounts precheck': 'O(1)',
  'Unlink account': 'O(1)',
  'Delete user (full, linked)': 'O(1)',
  'Active users count': 'O(n)',
  'Active users count (with more-than-one-login-method window)': 'O(n)',
  'Feature-flag usage stats aggregate': 'O(n)',
  'List users for role (large share)': 'O(n)',
  'TOTP verify (user with many used codes)': 'O(1)',
  'Email-verification status update (mapped user)': 'O(1)',
  'Delete user with userid mapping': 'O(1)',
};

/**
 * Default per-class ratio bounds. O(1) steps get ~3x headroom over a perfectly
 * flat 1.0; O(n) steps get ~15x (10x data plus headroom). Everything is
 * env-overridable:
 *   STRESS_TEST_RATIO_O1_BOUND / STRESS_TEST_RATIO_ON_BOUND - per-class bounds
 *   STRESS_TEST_RATIO_BOUNDS - JSON object of per-step overrides (by title)
 *   STRESS_TEST_RATIO_FLOOR_MS - clamp floor before dividing (default 50ms), so
 *       sub-noise measurements can't manufacture a false ratio
 *   STRESS_TEST_ENFORCE_RATIOS - "false" to measure + report without failing
 */
export const DEFAULT_RATIO_BOUNDS: Record<ScaleClass, number> = { 'O(1)': 3, 'O(n)': 15 };
export const DEFAULT_RATIO_FLOOR_MS = 50;

const ratioFloorMs = (): number =>
  Number(process.env.STRESS_TEST_RATIO_FLOOR_MS ?? String(DEFAULT_RATIO_FLOOR_MS)) ||
  DEFAULT_RATIO_FLOOR_MS;

const scaleClassFor = (title: string): ScaleClass => STEP_SCALE_CLASS[title] ?? 'O(n)';

const classBound = (cls: ScaleClass): number => {
  const envKey = cls === 'O(1)' ? 'STRESS_TEST_RATIO_O1_BOUND' : 'STRESS_TEST_RATIO_ON_BOUND';
  return (
    Number(process.env[envKey] ?? String(DEFAULT_RATIO_BOUNDS[cls])) || DEFAULT_RATIO_BOUNDS[cls]
  );
};

let cachedRatioOverrides: Record<string, number> | undefined;

const ratioOverrides = (): Record<string, number> => {
  if (cachedRatioOverrides) return cachedRatioOverrides;
  let overrides: Record<string, number> = {};
  const raw = process.env.STRESS_TEST_RATIO_BOUNDS;
  if (raw) {
    try {
      overrides = JSON.parse(raw);
    } catch (e) {
      console.warn(`    Ignoring invalid STRESS_TEST_RATIO_BOUNDS: ${(e as Error).message}`);
    }
  }
  return (cachedRatioOverrides = overrides);
};

const boundFor = (title: string): number => {
  const override = ratioOverrides()[title];
  if (override !== undefined) return override;
  return classBound(scaleClassFor(title));
};

const ratiosEnforced = (): boolean =>
  (process.env.STRESS_TEST_ENFORCE_RATIOS ?? 'true').toLowerCase() !== 'false';

export interface RatioResult {
  title: string;
  scaleClass: ScaleClass;
  smallMs: number;
  largeMs: number;
  floorMs: number;
  ratio: number;
  bound: number;
  overBound: boolean;
}

export class RatioCollector {
  private static instance: RatioCollector;
  private data: Map<string, { small?: number; large?: number }> = new Map();

  private constructor() {}

  public static getInstance(): RatioCollector {
    if (!RatioCollector.instance) {
      RatioCollector.instance = new RatioCollector();
    }
    return RatioCollector.instance;
  }

  public record(title: string, size: CheckpointSize, timeMs: number) {
    const entry = this.data.get(title) ?? {};
    entry[size] = timeMs;
    this.data.set(title, entry);
  }

  /** Ratio result for a single step, or undefined if it lacks both measurements. */
  public resultFor(title: string): RatioResult | undefined {
    const entry = this.data.get(title);
    if (!entry || entry.small === undefined || entry.large === undefined) return undefined;
    const floorMs = ratioFloorMs();
    const small = Math.max(entry.small, floorMs);
    const large = Math.max(entry.large, floorMs);
    const bound = boundFor(title);
    const ratio = large / small;
    return {
      title,
      scaleClass: scaleClassFor(title),
      smallMs: entry.small,
      largeMs: entry.large,
      floorMs,
      ratio,
      bound,
      overBound: ratio > bound,
    };
  }

  /** Every step that has both a small and a large measurement, sorted by title. */
  public results(): RatioResult[] {
    return [...this.data.keys()]
      .map((title) => this.resultFor(title))
      .filter((r): r is RatioResult => r !== undefined)
      .sort((a, b) => a.title.localeCompare(b.title));
  }

  /** Structured payload merged into stats.json alongside the duration measurements. */
  public toJSON() {
    return {
      floorMs: ratioFloorMs(),
      bounds: {
        'O(1)': classBound('O(1)'),
        'O(n)': classBound('O(n)'),
      },
      results: this.results().map((r) => ({
        ...r,
        smallFormatted: formatTime(r.smallMs),
        largeFormatted: formatTime(r.largeMs),
        ratio: Math.round(r.ratio * 100) / 100,
        status: r.overBound ? 'OVER RATIO' : 'OK',
      })),
    };
  }

  /**
   * Fails the run if any step's large/small duration ratio exceeded its bound —
   * i.e. its per-request cost grew with the database size beyond what its
   * scaling class allows. Called at the very end so every step still appears in
   * stats.json and the summary table. Honors STRESS_TEST_ENFORCE_RATIOS=false.
   */
  public throwIfRatioExceeded() {
    const results = this.results();
    if (results.length === 0) {
      console.log('\nNo two-size measurements captured; skipping scaling-ratio check.');
      return;
    }
    const over = results.filter((r) => r.overBound);
    if (over.length === 0) {
      console.log('\nAll measured steps scaled within their per-class ratio bounds.');
      return;
    }
    console.error('\nSteps exceeding their scaling-ratio bound:');
    for (const r of over) {
      console.error(
        `  ${r.title} [${r.scaleClass}]: ratio ${r.ratio.toFixed(2)} > bound ${r.bound} ` +
          `(${formatTime(r.smallMs)} -> ${formatTime(r.largeMs)}, floor ${r.floorMs}ms)`
      );
    }
    if (!ratiosEnforced()) {
      console.error('\nSTRESS_TEST_ENFORCE_RATIOS=false — reporting only, not failing the run.');
      return;
    }
    throw new Error(
      `${over.length} step(s) exceeded their scaling-ratio bound; per-request cost is growing with database size.`
    );
  }
}

/**
 * Tracks non-OK results produced by the seeding steps. A seeding step that
 * silently errors would otherwise still "pass" and invalidate every
 * measurement downstream of it, so we count the non-OK results per step and
 * fail the run at the end if any step had failures.
 */
export class FailureTracker {
  private static instance: FailureTracker;
  private failures: Map<string, { count: number; statuses: Record<string, number> }> = new Map();

  private constructor() {}

  public static getInstance(): FailureTracker {
    if (!FailureTracker.instance) {
      FailureTracker.instance = new FailureTracker();
    }
    return FailureTracker.instance;
  }

  public recordFailure(step: string, status: string) {
    const entry = this.failures.get(step) ?? { count: 0, statuses: {} };
    entry.count++;
    entry.statuses[status] = (entry.statuses[status] ?? 0) + 1;
    this.failures.set(step, entry);
  }

  public hasFailures(): boolean {
    return this.failures.size > 0;
  }

  /**
   * Prints a per-step summary of any non-OK results and throws if there were
   * any, so the run fails at the end without aborting the seeding mid-way.
   */
  public throwIfAnyFailures() {
    if (!this.hasFailures()) {
      console.log('\nAll seeding steps completed with no non-OK results.');
      return;
    }
    console.error('\nSeeding step failures detected (non-OK results):');
    let total = 0;
    for (const [step, info] of this.failures) {
      total += info.count;
      console.error(`  ${step}: ${info.count} non-OK result(s) — ${JSON.stringify(info.statuses)}`);
    }
    throw new Error(
      `${total} non-OK result(s) across ${this.failures.size} seeding step(s); measurements are not trustworthy.`
    );
  }
}

export const measureTime = async <T>(title: string, fn: () => Promise<T>): Promise<T> => {
  const st = Date.now();
  const result = await fn();
  const et = Date.now();
  const timeMs = et - st;
  const budgetMs = getStepBudgetMs(title);
  const flag = timeMs > budgetMs ? ' [OVER BUDGET]' : '';
  const checkpoint = getCheckpoint();
  const checkpointTag = checkpoint ? ` [${checkpoint}]` : '';
  console.log(
    `    ${title}${checkpointTag} took ${formatTime(timeMs)} (budget ${formatTime(budgetMs)})${flag}`
  );
  // Small checkpoint pass: record only into the ratio harness, so the 100k
  // measurements neither pollute the 1M summary/budget table nor trip 1M
  // budgets. Large pass and un-checkpointed seeding steps record into the
  // StatsCollector as before; the large pass additionally feeds the ratio.
  if (checkpoint === 'small') {
    RatioCollector.getInstance().record(title, 'small', timeMs);
  } else {
    StatsCollector.getInstance().addMeasurement(title, timeMs);
    if (checkpoint === 'large') {
      RatioCollector.getInstance().record(title, 'large', timeMs);
    }
  }
  return result;
};
