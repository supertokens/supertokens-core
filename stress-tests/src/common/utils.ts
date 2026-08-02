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
    const formattedMeasurements = this.measurements.map((measurement) => ({
      title: measurement.title,
      ms: measurement.timeMs,
      formatted: formatTime(measurement.timeMs),
      budgetMs: measurement.budgetMs,
      budgetFormatted: formatTime(measurement.budgetMs),
      overBudget: measurement.timeMs > measurement.budgetMs,
      status: measurement.timeMs > measurement.budgetMs ? 'OVER BUDGET' : 'OK',
    }));

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
  console.log(`    ${title} took ${formatTime(timeMs)} (budget ${formatTime(budgetMs)})${flag}`);
  StatsCollector.getInstance().addMeasurement(title, timeMs);
  return result;
};
