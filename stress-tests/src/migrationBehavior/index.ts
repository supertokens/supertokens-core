/**
 * REVIEW-007: Migration behavior test runner.
 *
 * Runs all behavioral equivalence tests against the currently running
 * SuperTokens core instance. The migration mode is read from the
 * MIGRATION_MODE environment variable (defaults to MIGRATED).
 *
 * Usage:
 *   # Run against default mode (MIGRATED)
 *   ts-node src/migrationBehavior/index.ts
 *
 *   # Run against a specific mode
 *   MIGRATION_MODE=LEGACY ts-node src/migrationBehavior/index.ts
 *
 *   # Run with custom core URL
 *   CORE_URL=http://localhost:3567 MIGRATION_MODE=DUAL_WRITE_READ_OLD ts-node src/migrationBehavior/index.ts
 *
 *   # Run with more stress iterations
 *   STRESS_ITERATIONS=20 ts-node src/migrationBehavior/index.ts
 *
 * The runner loads all test modules, which register tests via the `test()` function
 * from setup.ts, then executes them sequentially and prints results.
 */

import {
  initSdk,
  CORE_URL,
  API_KEY,
  MIGRATION_MODE,
  MigrationMode,
  runRegisteredTests,
  clearTests,
  printResults,
} from './setup';
import { setupLicense } from '../common/utils';

async function main() {
  const mode = MIGRATION_MODE as MigrationMode;
  console.log(`\nMigration Behavior Tests`);
  console.log(`  Core URL: ${CORE_URL}`);
  console.log(`  Mode:     ${mode}`);
  console.log(`  Stress:   ${process.env.STRESS_ITERATIONS ?? '5'} iterations`);
  console.log(`  Concurrency: ${process.env.CONCURRENCY ?? '10'}\n`);

  // Initialize SDK
  initSdk();

  // Set up license for account linking
  try {
    await setupLicense(CORE_URL, API_KEY);
  } catch (err) {
    console.warn('Warning: Could not set license (may already be set):', (err as Error).message);
  }

  // Verify core is reachable
  try {
    const resp = await fetch(`${CORE_URL}/hello`);
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    console.log('Core is reachable.\n');
  } catch (err) {
    console.error(`ERROR: Cannot reach core at ${CORE_URL}`);
    console.error('Make sure the SuperTokens core is running.');
    process.exit(1);
  }

  // Clear any previously registered tests
  clearTests();

  // Import test modules — each module registers tests via test()
  await import('./testLinkAccounts');
  await import('./testEmailUpdates');
  await import('./testMultitenancy');
  await import('./testConcurrency');

  // Mode transition (blue-green) tests create dedicated apps via the
  // multitenancy API with per-app migration_mode coreConfig overrides.
  // Enable with INCLUDE_TRANSITION_TESTS=true.
  if (process.env.INCLUDE_TRANSITION_TESTS === 'true') {
    console.log('Loading blue-green mode transition tests.\n');
    await import('./testModeTransition');
  }

  // Run all tests
  const result = await runRegisteredTests(mode);

  // Print results
  printResults(result);

  // Write results to file
  const fs = await import('fs');
  const outputPath = `migration-test-results-${mode}.json`;
  fs.writeFileSync(
    outputPath,
    JSON.stringify(
      {
        ...result,
        timestamp: new Date().toISOString(),
        coreUrl: CORE_URL,
        stressIterations: process.env.STRESS_ITERATIONS ?? '5',
        concurrency: process.env.CONCURRENCY ?? '10',
      },
      null,
      2
    )
  );
  console.log(`\nResults written to ${outputPath}`);

  // Exit with non-zero if any tests failed
  if (result.failed > 0) {
    process.exit(1);
  }
}

main().catch((err) => {
  console.error('Fatal error:', err);
  process.exit(1);
});
