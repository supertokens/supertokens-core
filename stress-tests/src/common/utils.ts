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
  // Sub-second values must not all collapse to "0s". Most read-path steps in
  // the 1M suite land in the tens-to-hundreds of milliseconds, so flooring to
  // whole seconds made both the summary table and the run-to-run comparison
  // unreadable: a real 63ms -> 998ms scaling failure rendered as "0s -> 1s",
  // and the comparison printed deltas like "0s vs 0s (+200.0%)". Ratios and
  // budgets are computed from the raw ms either way; this only affects display.
  if (ms < 1000) {
    return `${Math.round(ms)}ms`;
  }
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
  // Session read paths: token/user-addressed point reads, so the budgets are
  // the same 60s as the other O(1) steps. The ratio bound, not the budget, is
  // what actually guards these.
  'Session verify (access token)': 60_000,
  'Session refresh (refresh token)': 60_000,
  'Session handles for user': 60_000,
  'Session handles for user (miss)': 60_000,
  // OAuth phase: seeding (clients via the provider + bulk M2M-stat/session SQL)
  // and the measured OAuth-dependent read paths.
  'Seeding OAuth data (100k checkpoint)': 1_800_000,
  'Seeding OAuth data (remaining to full)': 1_800_000,
  'OAuth M2M token issuance (client_credentials)': 60_000,
  'OAuth token introspection': 60_000,
  'OAuth revoke by session handle': 60_000,
  'OAuth revoke by client id': 120_000,
  'OAuth M2M created-since burst accuracy': 600_000,
  'OAuth cleanup cron sweep': 300_000,
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

interface Measurement {
  title: string;
  timeMs: number;
  budgetMs: number;
  timedOut?: boolean;
  failed?: boolean;
  failureReason?: string;
  // True if at least one earlier step had already failed/timed out when this
  // step ran. A failed mid-way step (e.g. a destructive mutation) can taint the
  // numbers of everything measured after it, so the summary flags those rows.
  afterFailure?: boolean;
}

/** Per-step repeat metadata for steps measured with measureRepeated. */
export interface RepeatInfo {
  iterations: number;
  totalMs: number;
  p95Ms: number;
}

export class StatsCollector {
  private static instance: StatsCollector;
  private measurements: Measurement[] = [];

  private constructor() {}

  public static getInstance(): StatsCollector {
    if (!StatsCollector.instance) {
      StatsCollector.instance = new StatsCollector();
    }
    return StatsCollector.instance;
  }

  private anyPriorFailure(): boolean {
    return this.measurements.some((m) => m.failed);
  }

  private repeatInfo: Map<string, RepeatInfo> = new Map();

  /** Attach repeat metadata for a step measured with measureRepeated. */
  public addRepeatInfo(title: string, info: RepeatInfo) {
    this.repeatInfo.set(title, info);
  }

  public addMeasurement(title: string, timeMs: number) {
    this.measurements.push({
      title,
      timeMs,
      budgetMs: getStepBudgetMs(title),
      afterFailure: this.anyPriorFailure() || undefined,
    });
  }

  /**
   * Record a step that failed (threw or timed out) so it still appears in the
   * summary table with its failure reason instead of vanishing from the run.
   * Recorded regardless of the ambient checkpoint — a failure anywhere must be
   * visible in the (large-run) summary table.
   */
  public addFailure(title: string, timeMs: number, timedOut: boolean, reason: string) {
    this.measurements.push({
      title,
      timeMs,
      budgetMs: getStepBudgetMs(title),
      timedOut,
      failed: true,
      failureReason: reason,
      afterFailure: this.anyPriorFailure() || undefined,
    });
  }

  public getStats() {
    return this.measurements;
  }

  /** Repeat metadata for a step, or undefined if it was a single sample. */
  public getRepeatInfo(title: string): RepeatInfo | undefined {
    return this.repeatInfo.get(title);
  }

  public writeToFile(extra: Record<string, unknown> = {}) {
    const ratios = RatioCollector.getInstance();
    const formattedMeasurements = this.measurements.map((measurement) => {
      const timedOut = measurement.timedOut === true;
      const failed = measurement.failed === true;
      const overBudget = !failed && measurement.timeMs > measurement.budgetMs;
      const status = timedOut ? 'TIMED OUT' : failed ? 'FAILED' : overBudget ? 'OVER BUDGET' : 'OK';
      const base = {
        title: measurement.title,
        ms: measurement.timeMs,
        formatted: formatTime(measurement.timeMs),
        budgetMs: measurement.budgetMs,
        budgetFormatted: formatTime(measurement.budgetMs),
        overBudget,
        timedOut,
        failed,
        failureReason: measurement.failureReason,
        afterFailure: measurement.afterFailure === true,
        status,
        // Present only for repeated steps: `ms` above is then the per-operation
        // mean over `iterations` samples, not a single observation.
        ...(this.repeatInfo.get(measurement.title) ?? {}),
      };
      // Merge in the two-size scaling ratio for steps measured at both sizes so
      // the summary table can show small/large/ratio columns per step.
      const r = ratios.resultFor(measurement.title);
      if (!r) {
        // If a step was measured at one size but failed/timed out at the other,
        // it has no meaningful ratio: report it as n/a rather than a pass or a
        // fail (a failed side can't be scaled against a good one).
        if (ratios.hasFailedSide(measurement.title)) {
          return { ...base, ratio: null, ratioStatus: 'N/A', ratioText: 'n/a' };
        }
        return base;
      }
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
      // Comparability metadata — see HARNESS_VERSION. The comparison step reads
      // these off each baseline and flags any that do not match this run.
      harnessVersion: HARNESS_VERSION,
      stepFingerprint: stepFingerprint(formattedMeasurements.map((m) => m.title)),
      // Which published image produced these numbers. The artifact name already
      // encodes it, but the artifact name is not inside the file, and a baseline
      // is read long after it was downloaded — so record it here too.
      imageTag: process.env.STRESS_TEST_IMAGE_TAG,
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

  /**
   * Fails the run if any measured read-path step failed or timed out. With
   * collect-and-continue (issue #1346) a guard violation, thrown error or budget
   * timeout in a read-path step is caught, recorded here and the run continues —
   * so this end-of-run aggregate is what actually turns the job red, listing
   * every failed step at once instead of dying at the first one. Runs after all
   * stats are written so every step still appears in the summary table.
   * (Seeding failures are handled separately and remain immediately fatal.)
   */
  public throwIfAnyStepFailed() {
    const failed = this.measurements.filter((m) => m.failed);
    if (failed.length === 0) {
      console.log('\nAll measured read-path steps completed without failing.');
      return;
    }
    console.error('\nMeasured read-path steps that failed or timed out:');
    for (const m of failed) {
      console.error(
        `  ${m.title}: ${m.timedOut ? 'TIMED OUT' : 'FAILED'} — ${m.failureReason ?? 'unknown reason'}`
      );
    }
    throw new Error(
      `${failed.length} measured read-path step(s) failed or timed out; see the Stress Test Results table.`
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
  // Sessions are addressed by token or by user id, so none of these may grow
  // with the user table. The miss-path handle lookup is held to the same bound
  // as the hit path on purpose: a miss that scales while the hit stays flat is
  // the unbounded-scan signature (C2).
  'Session verify (access token)': 'O(1)',
  'Session refresh (refresh token)': 'O(1)',
  'Session handles for user': 'O(1)',
  'Session handles for user (miss)': 'O(1)',
  // OAuth read paths. Issuance / introspection / revoke-by-handle are single-key
  // ops that must stay flat as the M2M-stats and oauth_sessions volume grows
  // (O(1)-class once plugin #357 + the revoke index land). Revoke-by-client
  // touches all of a client's sessions, whose count grows with the seed, so it
  // is left on the lenient O(n) bound.
  'OAuth M2M token issuance (client_credentials)': 'O(1)',
  'OAuth token introspection': 'O(1)',
  'OAuth revoke by session handle': 'O(1)',
  'OAuth revoke by client id': 'O(n)',
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

/**
 * Harness identity, written into stats.json and shown in the comparison table.
 *
 * `HARNESS_VERSION` is manual: bump it whenever a change alters what the
 * numbers MEAN even though the step titles are unchanged — switching a step
 * from a single call to a repeated mean is exactly that. `stepFingerprint` is
 * automatic: a hash of the measured step titles, so adding or removing a step
 * invalidates comparability without anyone having to remember.
 *
 * A baseline whose version or fingerprint differs from the current run is still
 * shown, but flagged: the deltas against it are not apples-to-apples.
 */
export const HARNESS_VERSION = 2;

export const stepFingerprint = (titles: string[]): string => {
  const canonical = [...titles].sort().join('\u0000');
  // djb2 — no crypto import needed and collisions do not matter here; this only
  // has to notice that the step set changed.
  let h = 5381;
  for (let i = 0; i < canonical.length; i++) h = ((h << 5) + h + canonical.charCodeAt(i)) >>> 0;
  return h.toString(16).padStart(8, '0');
};

/**
 * Repeated measurement. A step that issues one request lands in the tens of
 * milliseconds, where a single sample is mostly runner noise — which is how a
 * genuinely flat step came out at 21.6x against a bound of 15 on one run and
 * passed on the next. Repeating it until the measured window is ~10s turns the
 * recorded value into a mean over hundreds of samples, which is stable enough
 * to compare across runs and versions.
 *
 * Sized automatically from a short calibration burst, so the count adapts to
 * the step and to the runner instead of being hard-coded per step.
 */
export const REPEAT_TARGET_MS =
  Number(process.env.STRESS_TEST_REPEAT_TARGET_MS ?? '10000') || 10_000;
export const REPEAT_MAX_ITERATIONS =
  Number(process.env.STRESS_TEST_REPEAT_MAX_ITERATIONS ?? '20000') || 20_000;
const REPEAT_CALIBRATION_ITERATIONS = 3;
const REPEAT_MIN_ITERATIONS = 5;

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
  // Steps whose small and/or large pass failed or timed out. Such a step has no
  // meaningful scaling ratio — the failed side can't be scaled against a good
  // one — so it is reported as n/a rather than pass/fail (issue #1346).
  private failedSides: Map<string, Set<CheckpointSize>> = new Map();
  // Steps measured as a mean over many samples. The ratio floor exists to stop
  // a noisy single-millisecond reading producing an enormous ratio; a mean over
  // hundreds of samples does not need that protection, and applying the floor
  // to it actively distorts the result (flooring a real 5ms mean to 50ms makes
  // a flat step look like it improved 10x with scale).
  private repeated: Set<string> = new Set();

  private constructor() {}

  /** Mark a step as measured by repetition, so the ratio floor is not applied. */
  public markRepeated(title: string) {
    this.repeated.add(title);
  }

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

  /** Mark one size of a two-size step as failed, so its ratio is reported n/a. */
  public recordFailure(title: string, size: CheckpointSize) {
    const sides = this.failedSides.get(title) ?? new Set<CheckpointSize>();
    sides.add(size);
    this.failedSides.set(title, sides);
  }

  /** Whether either pass of this step failed/timed out. */
  public hasFailedSide(title: string): boolean {
    return this.failedSides.has(title);
  }

  /** Ratio result for a single step, or undefined if it lacks both measurements. */
  public resultFor(title: string): RatioResult | undefined {
    const entry = this.data.get(title);
    if (!entry || entry.small === undefined || entry.large === undefined) return undefined;
    const floorMs = this.repeated.has(title) ? 0 : ratioFloorMs();
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
      // Two-size steps that failed on at least one side — no ratio computable.
      naSteps: [...this.failedSides.entries()]
        .map(([title, sides]) => ({ title, failedSides: [...sides], status: 'N/A' }))
        .sort((a, b) => a.title.localeCompare(b.title)),
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

/**
 * Thrown when a measured step runs past its duration budget. The budget is no
 * longer a post-hoc report but a hard, enforced ceiling: a step that hangs
 * (e.g. a non-terminating pagination walk or a stuck request) is aborted at its
 * budget instead of surviving until the 6h job timeout.
 */
export class StepTimeoutError extends Error {
  constructor(
    public readonly step: string,
    public readonly budgetMs: number
  ) {
    super(
      `Step "${step}" exceeded its hard timeout of ${formatTime(budgetMs)} (${budgetMs}ms) and was aborted.`
    );
    this.name = 'StepTimeoutError';
  }
}

/**
 * Race a step against its duration budget. Resolves with the step's result if
 * it finishes in time, otherwise rejects with a StepTimeoutError. On timeout the
 * step's AbortSignal is aborted so a cooperative step (one that checks the
 * signal in its walk/poll loop) actually stops issuing work instead of running
 * on in the background and polluting the measurements that follow it — which
 * matters now that a timed-out step no longer ends the process (issue #1346).
 */
const raceStepAgainstBudget = async <T>(
  title: string,
  budgetMs: number,
  controller: AbortController,
  fn: (signal: AbortSignal) => Promise<T>
): Promise<T> => {
  let timer: ReturnType<typeof setTimeout> | undefined;
  const timeout = new Promise<never>((_, reject) => {
    timer = setTimeout(() => {
      controller.abort();
      reject(new StepTimeoutError(title, budgetMs));
    }, budgetMs);
  });
  try {
    return await Promise.race([fn(controller.signal), timeout]);
  } finally {
    if (timer) clearTimeout(timer);
  }
};

/**
 * Measure the duration of a step. `fn` receives an AbortSignal that is aborted
 * if the step exceeds its budget; long-running loops (e.g. the pagination walk)
 * should check it each iteration so a timed-out step stops cooperatively. Steps
 * that do a single request can ignore the signal.
 */
export const measureTime = async <T>(
  title: string,
  fn: (signal: AbortSignal) => Promise<T>,
  // Set by measureRepeated: the fn ran `iterations` operations, so the value
  // recorded for budgets, ratios and the summary is the per-operation mean
  // rather than the total elapsed.
  repeat?: { iterations: number; p95Ms: number }
): Promise<T> => {
  const st = Date.now();
  const budgetMs = getStepBudgetMs(title);
  const checkpoint = getCheckpoint();
  const checkpointTag = checkpoint ? ` [${checkpoint}]` : '';
  const controller = new AbortController();

  let result: T;
  try {
    // When budgets are enforced (the default) the budget is a hard ceiling: race
    // the step against it so a hung step fails the run at its budget with the
    // step name. STRESS_TEST_ENFORCE_BUDGETS=false is the calibration escape
    // hatch — let the step run to completion and only report over-budget later.
    result = budgetsEnforced()
      ? await raceStepAgainstBudget(title, budgetMs, controller, fn)
      : await fn(controller.signal);
  } catch (err) {
    const timedOut = err instanceof StepTimeoutError;
    // A timed-out step's true duration is unknown; record it at its budget.
    const timeMs = timedOut ? budgetMs : Date.now() - st;
    const reason = timedOut ? `timed out after ${formatTime(budgetMs)}` : (err as Error).message;
    console.error(
      `    ${title}${checkpointTag} ${timedOut ? 'TIMED OUT' : 'FAILED'} after ${formatTime(timeMs)}: ${reason}`
    );
    // Surface the failure in the summary table (records regardless of checkpoint).
    StatsCollector.getInstance().addFailure(title, timeMs, timedOut, reason);
    // Mark the two-size ratio side that failed so it is reported as n/a rather
    // than pass/fail (the failed side can't be scaled against a good one).
    if (checkpoint) {
      RatioCollector.getInstance().recordFailure(title, checkpoint);
    }
    throw err;
  }

  const et = Date.now();
  const elapsedMs = et - st;
  // For a repeated step the comparable quantity is the per-operation mean; the
  // total is kept alongside it so the summary can show how long the step ran.
  // `iterations` is read after fn returned, so a window cut short by the abort
  // signal divides by what it actually ran, not by what it planned to.
  const repeatOps = repeat ? Math.max(1, repeat.iterations) : 1;
  const timeMs = repeat ? elapsedMs / repeatOps : elapsedMs;
  const flag = timeMs > budgetMs ? ' [OVER BUDGET]' : '';
  if (repeat) {
    StatsCollector.getInstance().addRepeatInfo(title, {
      iterations: repeatOps,
      totalMs: elapsedMs,
      p95Ms: repeat.p95Ms,
    });
    RatioCollector.getInstance().markRepeated(title);
    console.log(
      `    ${title}${checkpointTag} ${formatTime(timeMs)}/op mean over ${repeatOps} ops ` +
        `(p95 ${formatTime(repeat.p95Ms)}, ${formatTime(elapsedMs)} total, budget ${formatTime(budgetMs)})${flag}`
    );
  } else {
    console.log(
      `    ${title}${checkpointTag} took ${formatTime(timeMs)} (budget ${formatTime(budgetMs)})${flag}`
    );
  }
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

/**
 * Measure a cheap operation by repeating it until the measured window is long
 * enough to be stable (~REPEAT_TARGET_MS), recording the per-operation mean.
 *
 * `work` receives the iteration index and must vary its own input by it —
 * hammering one row measures a warm cache, not the path we care about. The
 * budget still applies to the whole window and the signal is honoured between
 * iterations, so a step that becomes pathologically slow still aborts at its
 * budget rather than running for the full iteration count.
 *
 * Sizing is adaptive and cheap. One unmeasured calibration op runs first; only
 * if it comes back fast enough that a single sample would be noise are more
 * calibration ops run to refine the estimate. A step whose single op already
 * fills the target window gets `iterations = 1` and is recorded exactly as a
 * plain `measureTime` step would be — repetition is for the sub-second steps,
 * and an operation that takes seconds is already its own signal.
 *
 * The calibration ops are deliberately unmeasured: they both size the run and
 * warm JIT and page cache, so the measured window is steady-state.
 */
export const measureRepeated = async (
  title: string,
  work: (iteration: number, signal: AbortSignal) => Promise<unknown>
): Promise<void> => {
  const noAbort = new AbortController().signal;
  let calOps = 0;
  const calStart = Date.now();
  await work(-1, noAbort);
  calOps = 1;
  // Refine only when the first op was cheap enough that the extra ops are free
  // next to the measured window we are about to run anyway.
  if (Date.now() - calStart < REPEAT_TARGET_MS / 20) {
    for (let i = 1; i < REPEAT_CALIBRATION_ITERATIONS; i++) {
      await work(-1 - i, noAbort);
      calOps++;
    }
  }
  const perOpMs = Math.max((Date.now() - calStart) / calOps, 0.05);
  const iterations = Math.min(
    REPEAT_MAX_ITERATIONS,
    Math.max(1, Math.round(REPEAT_TARGET_MS / perOpMs))
  );

  // Single-sample fallback: nothing to average, so record it the plain way and
  // leave the ratio floor in place rather than claiming a stabilised mean.
  if (iterations === 1) {
    await measureTime(title, (signal) => work(0, signal));
    return;
  }

  // Filled in by the loop and read by measureTime *after* fn resolves — the
  // p95 does not exist until the window has run, and the iteration count is
  // whatever the window actually completed.
  const progress = { iterations: 0, p95Ms: 0 };
  const samples: number[] = [];
  await measureTime(
    title,
    async (signal) => {
      const windowStart = Date.now();
      for (let i = 0; i < iterations; i++) {
        if (signal.aborted) break;
        // Wall-clock cap. The iteration count comes from a 3-op calibration
        // burst, so a step that is much slower under load than it was during
        // calibration would otherwise run far past the target window and trip
        // its budget as a false timeout. Whichever limit comes first wins,
        // subject to a floor so a genuinely slow step still gets a few samples.
        if (i >= REPEAT_MIN_ITERATIONS && Date.now() - windowStart >= REPEAT_TARGET_MS) break;
        const t0 = Date.now();
        await work(i, signal);
        samples.push(Date.now() - t0);
        progress.iterations = samples.length;
      }
      progress.p95Ms = percentile(samples, 0.95);
    },
    progress
  );
};

const percentile = (values: number[], p: number): number => {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const idx = Math.min(sorted.length - 1, Math.max(0, Math.ceil(sorted.length * p) - 1));
  return sorted[idx]!;
};

/**
 * Run one measured read-path step, isolating its failure so a single broken
 * step does not abort the rest of the run (issue #1346, collect-and-continue).
 * The step's own measureTime call has already recorded the failure/timeout in
 * the summary table and (for two-size steps) marked its ratio n/a; the
 * end-of-run StatsCollector.throwIfAnyStepFailed() aggregates them and fails
 * the job. Any error the step throws outside measureTime (e.g. fixture setup)
 * is caught here too so it is logged and the next step still runs.
 *
 * Only read-path steps are wrapped in this. Seeding steps deliberately are not:
 * a seeding failure invalidates every downstream measurement, so it stays
 * immediately fatal.
 */
export const runStep = async (fn: () => Promise<void>): Promise<void> => {
  try {
    await fn();
  } catch (e) {
    console.error(`    Step error (continuing): ${(e as Error).stack ?? (e as Error).message}`);
  }
};
