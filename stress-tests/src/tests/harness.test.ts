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
  'walk-timeout-step': 60,
  'repeat-step': 60_000,
  'repeat-index-step': 60_000,
  'repeat-slow-step': 60_000,
  'repeat-abort-step': 300,
});
// Keep the repeat windows short enough for a unit test — the production target
// is 10s per step, which is not something a test suite should sit through.
process.env.STRESS_TEST_REPEAT_TARGET_MS = '200';

import {
  measureRepeated,
  measureTime,
  runStep,
  StatsCollector,
  StepTimeoutError,
  budgetsEnforced,
} from '../common/utils';
import { walkAllUsers, GetPage, UserPage } from '../oneMillionUsers/pagination';
import { resolveMigrationMode, DEFAULT_MIGRATION_MODE } from '../common/migrationMode';

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
    // Include the pagination-cursor fields (timeJoined, id) so the completeness
    // diagnostics that name the terminal-page boundary can be asserted.
    const users = Array.from({ length: end - offset }, (_, i) => {
      const idx = offset + i;
      return { loginMethods: [{}], timeJoined: 1000 + idx, id: `user-${idx}` };
    });

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

const repeatInfo = (title: string) => StatsCollector.getInstance().getRepeatInfo(title);

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

// --- repeated measurement ---------------------------------------------------

test('measureRepeated records a per-operation mean, not the total window', async () => {
  let calls = 0;
  await measureRepeated('repeat-step', async () => {
    calls++;
    await delay(2);
  });
  const m = stat('repeat-step');
  const info = repeatInfo('repeat-step');
  assert.ok(m, 'recorded a measurement');
  assert.ok(info && info.iterations > 1, `repeated more than once (got ${info?.iterations})`);
  // The whole point: the recorded time is per-op, so it stays near the 2ms
  // operation even though the window ran for ~200ms.
  assert.ok(m!.timeMs < 50, `per-op mean stayed per-op (got ${m!.timeMs}ms)`);
  assert.ok(info!.totalMs >= 100, `total window was recorded (got ${info!.totalMs}ms)`);
  assert.ok(info!.p95Ms > 0, 'p95 is computed after the window ran, not before it');
  // Calibration ops are unmeasured but real, so the work ran more often than
  // the recorded iteration count.
  assert.ok(calls > info!.iterations, 'calibration ops ran and were not counted');
});

test('measureRepeated passes a distinct iteration index to the work function', async () => {
  const seen: number[] = [];
  await measureRepeated('repeat-index-step', async (i) => {
    seen.push(i);
    await delay(2);
  });
  const measured = seen.filter((i) => i >= 0);
  assert.deepStrictEqual(
    measured,
    measured.map((_, n) => n),
    'measured iterations are indexed 0..n-1 so work can vary its input'
  );
  assert.ok(
    seen.some((i) => i < 0),
    'calibration ops are handed negative indices so they are distinguishable'
  );
});

test('measureRepeated falls back to a single sample for an operation that is already slow', async () => {
  await measureRepeated('repeat-slow-step', async () => {
    await delay(250); // longer than the 200ms target window
  });
  const m = stat('repeat-slow-step');
  assert.ok(m, 'recorded a measurement');
  assert.strictEqual(repeatInfo('repeat-slow-step'), undefined, 'not marked as repeated');
  assert.ok(m!.timeMs >= 200, 'recorded the single observation, not a mean');
});

test('a repeated step that blows its budget mid-window still fails at the budget', async () => {
  // 300ms budget, ~10ms per op after a fast calibration, then the op becomes
  // pathologically slow: the window must be cut off by the budget rather than
  // running out its planned iteration count.
  let n = 0;
  await assert.rejects(
    () =>
      measureRepeated('repeat-abort-step', async () => {
        n++;
        await delay(n > 5 ? 400 : 1);
      }),
    (err: unknown) => err instanceof StepTimeoutError
  );
  const m = stat('repeat-abort-step');
  assert.ok(m && m.timedOut === true, 'recorded as timed out at its budget');
});

// --- richer completeness diagnostics (issue #1346) -------------------------

test('completeness failure names the terminal-page cursor boundary', async () => {
  // Truncate after 100 of 250 users at page size 50 -> terminal page is page 2,
  // fetched with token "50", covering users 50..99.
  await assert.rejects(
    () => walkAllUsers('newest first', makeSource(250, 50, { truncateAfterUsers: 100 }), 250),
    (err: unknown) => {
      const msg = (err as Error).message;
      return (
        /Terminal page: fetched with pagination token 50;/.test(msg) &&
        /first user \(timeJoined=1050, userId=user-50\)/.test(msg) &&
        /last user \(timeJoined=1099, userId=user-99\)/.test(msg)
      );
    }
  );
});

// --- cooperative cancellation of a walk (issue #1346) ----------------------

test('an aborted walk stops issuing pages and throws', async () => {
  const controller = new AbortController();
  let calls = 0;
  const src: GetPage = async (token) => {
    calls++;
    if (calls === 2) controller.abort();
    const offset = token === undefined ? 0 : Number(token);
    return { users: [{ loginMethods: [{}] }], nextPaginationToken: String(offset + 1) };
  };
  await assert.rejects(
    () => walkAllUsers('newest first', src, 1_000_000, controller.signal),
    /aborted after \d+ page\(s\)/
  );
  assert.ok(calls <= 3, `stopped issuing pages shortly after abort (calls=${calls})`);
});

test('a walk step over its budget is aborted and stops hitting the source', async () => {
  let calls = 0;
  const src: GetPage = async (token) => {
    calls++;
    await delay(10);
    const offset = token === undefined ? 0 : Number(token);
    return { users: [{ loginMethods: [{}] }], nextPaginationToken: String(offset + 1) };
  };
  await assert.rejects(
    () =>
      measureTime('walk-timeout-step', (signal) =>
        walkAllUsers('newest first', src, 1e9, signal).then(() => undefined)
      ),
    (err: unknown) => err instanceof StepTimeoutError
  );
  // After the budget timeout the walk must stop issuing further page requests
  // rather than running on in the background and polluting later measurements.
  const callsAtTimeout = calls;
  await delay(150);
  assert.strictEqual(calls, callsAtTimeout, 'walk stopped hitting the source after the abort');
});

// --- runStep isolation + end-of-run aggregate (issue #1346) ----------------

test('runStep swallows a thrown error so the next step still runs', async () => {
  let ranSecond = false;
  await runStep(async () => {
    throw new Error('isolated');
  });
  await runStep(async () => {
    ranSecond = true;
  });
  assert.ok(ranSecond, 'the step after a throwing one still ran');
});

test('throwIfAnyStepFailed fails the run when any measured step failed', async () => {
  // The measureTime tests above recorded a timed-out step and a thrown step;
  // the end-of-run aggregate must turn that into a non-zero exit.
  const failedTitles = StatsCollector.getInstance()
    .getStats()
    .filter((m) => m.failed)
    .map((m) => m.title);
  assert.ok(
    failedTitles.includes('timeout-step') && failedTitles.includes('error-step'),
    'both failure kinds are recorded in the summary'
  );
  assert.throws(
    () => StatsCollector.getInstance().throwIfAnyStepFailed(),
    /measured read-path step\(s\) failed or timed out/
  );
});

// --- migration-mode label resolution (issue #1351) -------------------------

test('resolveMigrationMode defaults to LEGACY when unset or blank', async () => {
  assert.strictEqual(resolveMigrationMode(undefined), 'LEGACY');
  assert.strictEqual(resolveMigrationMode(''), 'LEGACY');
  assert.strictEqual(resolveMigrationMode('   '), 'LEGACY');
  assert.strictEqual(DEFAULT_MIGRATION_MODE, 'LEGACY');
});

test('resolveMigrationMode accepts each valid mode, case-insensitively', async () => {
  assert.strictEqual(resolveMigrationMode('MIGRATED'), 'MIGRATED');
  assert.strictEqual(resolveMigrationMode('migrated'), 'MIGRATED');
  assert.strictEqual(resolveMigrationMode('  Legacy '), 'LEGACY');
  assert.strictEqual(resolveMigrationMode('dual_write_read_old'), 'DUAL_WRITE_READ_OLD');
  assert.strictEqual(resolveMigrationMode('DUAL_WRITE_READ_NEW'), 'DUAL_WRITE_READ_NEW');
});

test('resolveMigrationMode falls back to LEGACY for an unrecognized value', async () => {
  assert.strictEqual(resolveMigrationMode('MIGRATEDD'), 'LEGACY');
  assert.strictEqual(resolveMigrationMode('nonsense'), 'LEGACY');
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
