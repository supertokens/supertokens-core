/**
 * Focused unit checks for the stress-harness robustness guards added for
 * issue #1343 — hard per-step timeouts and pagination-walk completeness guards.
 *
 * The full 1M-user suite is a heavy, core-dependent CI job; these checks
 * exercise the pure logic in isolation (fake page sources, hung/throwing
 * step functions) so the two harness gaps the issue describes are covered by a
 * fast, local test. Run with: npm run test:harness
 */
import * as assert from 'assert';

// Budgets must be set before the first getStepBudgetMs call (they are cached).
process.env.STRESS_TEST_STEP_BUDGETS_MS = JSON.stringify({
  'timeout-step': 60,
  'error-step': 60_000,
  'ok-step': 60_000,
  'enforce-off-step': 40,
});

import { measureTime, StatsCollector, StepTimeoutError, budgetsEnforced } from '../common/utils';
import { walkAllUsers, GetPage, UserPage } from '../oneMillionUsers/pagination';

const tests: { name: string; fn: () => Promise<void> }[] = [];
const test = (name: string, fn: () => Promise<void>) => tests.push({ name, fn });

const delay = (ms: number) => new Promise((r) => setTimeout(r, ms));

/**
 * Fake paginated user source over `total` synthetic users at `pageSize` per
 * page. Options inject the failure modes the guards must catch.
 */
const makeSource = (
  total: number,
  pageSize: number,
  opts: {
    truncateAfterUsers?: number; // stop (nextToken=undefined) once this many returned
    repeatToken?: boolean; // always hand back the same nextToken
    runaway?: boolean; // after page 1, emit a fresh unique token forever
  } = {}
): GetPage => {
  let runawayCounter = 0;
  let servedFirstPage = false;
  return async (token?: string): Promise<UserPage> => {
    const offset = token === undefined ? 0 : Number(token);

    if (opts.runaway && servedFirstPage) {
      // Never terminates: one user and a fresh unique token every page, so the
      // page-count guard (not the token-repeat guard) must be what fires.
      runawayCounter++;
      return { users: [{ loginMethods: [{}] }], nextPaginationToken: `runaway-${runawayCounter}` };
    }
    servedFirstPage = true;

    const end = Math.min(offset + pageSize, total);
    const users = Array.from({ length: end - offset }, () => ({ loginMethods: [{}] }));

    if (opts.repeatToken) {
      return { users, nextPaginationToken: 'same-token' };
    }
    const nextOffset = end;
    const done =
      nextOffset >= total ||
      (opts.truncateAfterUsers !== undefined && nextOffset >= opts.truncateAfterUsers);
    return { users, nextPaginationToken: done ? undefined : String(nextOffset) };
  };
};

const stat = (title: string) =>
  StatsCollector.getInstance()
    .getStats()
    .find((m) => m.title === title);

// --- pagination-walk completeness / non-termination guards -----------------

test('full walk visits the whole dataset and passes completeness', async () => {
  const result = await walkAllUsers('newest first', makeSource(250, 100), 250);
  assert.strictEqual(result.userCount, 250);
  assert.strictEqual(result.pages, 3);
});

test('silent truncation fails the completeness assertion', async () => {
  await assert.rejects(
    () => walkAllUsers('newest first', makeSource(250, 50, { truncateAfterUsers: 100 }), 250),
    /visited 100 users but the tenant reports 250/
  );
});

test('a repeated pagination token aborts the walk', async () => {
  await assert.rejects(
    () => walkAllUsers('oldest first', makeSource(250, 50, { repeatToken: true }), 250),
    /repeated pagination token/
  );
});

test('a runaway page count aborts the walk', async () => {
  await assert.rejects(
    () => walkAllUsers('newest first', makeSource(250, 100, { runaway: true }), 250),
    /exceeded \d+ pages/
  );
});

// --- hard per-step timeouts -------------------------------------------------

test('a hung step fails at its budget with the step name, recorded as timed_out', async () => {
  const started = Date.now();
  await assert.rejects(
    () => measureTime('timeout-step', () => new Promise<void>(() => {})), // never resolves
    (err: unknown) => err instanceof StepTimeoutError && /timeout-step/.test((err as Error).message)
  );
  // Died at ~the budget, not after some long hang.
  assert.ok(Date.now() - started < 5_000, 'timed out promptly at its budget');
  const m = stat('timeout-step');
  assert.ok(m && m.timedOut === true && m.failed === true, 'recorded as timed_out in stats');
});

test('a throwing step is recorded as failed with its reason', async () => {
  await assert.rejects(() =>
    measureTime('error-step', async () => {
      throw new Error('boom');
    })
  );
  const m = stat('error-step');
  assert.ok(m && m.failed === true && m.timedOut !== true, 'recorded as failed');
  assert.strictEqual(m!.failureReason, 'boom');
});

test('a fast step resolves normally and is recorded OK', async () => {
  const value = await measureTime('ok-step', async () => {
    await delay(5);
    return 42;
  });
  assert.strictEqual(value, 42);
  const m = stat('ok-step');
  assert.ok(m && !m.failed && !m.timedOut, 'recorded without failure');
});

test('STRESS_TEST_ENFORCE_BUDGETS=false disables the hard timeout', async () => {
  process.env.STRESS_TEST_ENFORCE_BUDGETS = 'false';
  try {
    assert.strictEqual(budgetsEnforced(), false);
    // Budget is 40ms but the step takes ~120ms; with enforcement off it must
    // still complete instead of being aborted.
    const value = await measureTime('enforce-off-step', async () => {
      await delay(120);
      return 'done';
    });
    assert.strictEqual(value, 'done');
  } finally {
    delete process.env.STRESS_TEST_ENFORCE_BUDGETS;
  }
});

(async () => {
  let failed = 0;
  for (const t of tests) {
    try {
      await t.fn();
      console.log(`  ok   - ${t.name}`);
    } catch (err) {
      failed++;
      console.error(`  FAIL - ${t.name}`);
      console.error(`         ${(err as Error).message}`);
    }
  }
  console.log(`\n${tests.length - failed}/${tests.length} passed`);
  process.exit(failed === 0 ? 0 : 1);
})();
