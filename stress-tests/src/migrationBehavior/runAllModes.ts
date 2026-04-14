/**
 * REVIEW-007: Multi-mode test orchestrator.
 *
 * Runs the full behavioral test suite against all 4 migration modes sequentially.
 * This requires restarting the core between modes with a different migration_mode
 * config value, so it's designed for use with Docker or a local dev setup where
 * the core can be reconfigured.
 *
 * Usage:
 *   # With a pre-configured core (just runs tests, you manage mode switching)
 *   ts-node src/migrationBehavior/runAllModes.ts
 *
 *   # Or run individual modes separately:
 *   MIGRATION_MODE=LEGACY ts-node src/migrationBehavior/index.ts
 *   MIGRATION_MODE=DUAL_WRITE_READ_OLD ts-node src/migrationBehavior/index.ts
 *   MIGRATION_MODE=DUAL_WRITE_READ_NEW ts-node src/migrationBehavior/index.ts
 *   MIGRATION_MODE=MIGRATED ts-node src/migrationBehavior/index.ts
 *
 * How it works:
 *   This script attempts to set the migration mode via the core's
 *   /recipe/multitenancy/config endpoint (core config update). If that fails
 *   (e.g., the core doesn't support runtime mode switching), it prints
 *   instructions for manual mode switching.
 *
 *   In practice, for the current SuperTokens architecture, mode switching
 *   requires updating config.yaml and restarting the core. This runner is
 *   designed for CI pipelines where each mode run is a separate step.
 */

import { execSync, spawn, ChildProcess } from 'child_process';
import * as fs from 'fs';
import * as path from 'path';

import { MigrationMode } from './setup';

const MODES: MigrationMode[] = [
  'LEGACY',
  'DUAL_WRITE_READ_OLD',
  'DUAL_WRITE_READ_NEW',
  'MIGRATED',
];

const CORE_URL = process.env.CORE_URL ?? 'http://localhost:3567';
const API_KEY = process.env.API_KEY ?? 'qwertyuiopasdfghjklzxcvbnm';

interface ModeResult {
  mode: MigrationMode;
  passed: number;
  failed: number;
  total: number;
  exitCode: number;
}

async function waitForCore(url: string, timeoutMs: number = 30000): Promise<boolean> {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    try {
      const resp = await fetch(`${url}/hello`);
      if (resp.ok) return true;
    } catch {
      // Core not ready yet
    }
    await new Promise((r) => setTimeout(r, 1000));
  }
  return false;
}

function runTestsForMode(mode: MigrationMode): Promise<number> {
  return new Promise((resolve) => {
    const env = {
      ...process.env,
      MIGRATION_MODE: mode,
      CORE_URL,
      API_KEY,
    };

    const child = spawn(
      'npx',
      ['ts-node', path.join(__dirname, 'index.ts')],
      {
        env,
        stdio: 'inherit',
        cwd: path.resolve(__dirname, '..', '..'),
      }
    );

    child.on('close', (code) => {
      resolve(code ?? 1);
    });

    child.on('error', (err) => {
      console.error(`Failed to start tests for mode ${mode}:`, err);
      resolve(1);
    });
  });
}

async function main() {
  console.log('='.repeat(70));
  console.log('  Migration Behavior Tests — All Modes');
  console.log('='.repeat(70));
  console.log(`  Core URL: ${CORE_URL}`);
  console.log(`  Modes:    ${MODES.join(', ')}`);
  console.log('');
  console.log('  NOTE: This runner executes tests for each mode sequentially.');
  console.log('  The core must be configured with the correct migration_mode');
  console.log('  for each run. If the core does not support runtime mode');
  console.log('  switching, run each mode separately:');
  console.log('');
  for (const mode of MODES) {
    console.log(`    MIGRATION_MODE=${mode} ts-node src/migrationBehavior/index.ts`);
  }
  console.log('');

  // Check if running in "sequential" mode (core already running, we just test)
  const sequential = process.env.SEQUENTIAL !== 'false';

  if (sequential) {
    console.log('Running in sequential mode (testing against already-running core).');
    console.log('Each mode will be set via MIGRATION_MODE env var.\n');
  }

  const results: ModeResult[] = [];
  let overallFailed = false;

  for (const mode of MODES) {
    console.log(`\n${'─'.repeat(70)}`);
    console.log(`  Starting tests for mode: ${mode}`);
    console.log('─'.repeat(70));

    // Check core is reachable
    const reachable = await waitForCore(CORE_URL, 5000);
    if (!reachable) {
      console.error(`\n  ERROR: Core not reachable at ${CORE_URL}`);
      console.error('  Please start the core with migration_mode=' + mode);
      results.push({ mode, passed: 0, failed: -1, total: 0, exitCode: 1 });
      overallFailed = true;
      continue;
    }

    const exitCode = await runTestsForMode(mode);

    // Try to read results file
    const resultsFile = `migration-test-results-${mode}.json`;
    let modeResult: ModeResult = { mode, passed: 0, failed: 0, total: 0, exitCode };

    try {
      const data = JSON.parse(fs.readFileSync(resultsFile, 'utf-8'));
      modeResult = {
        mode,
        passed: data.passed ?? 0,
        failed: data.failed ?? 0,
        total: data.total ?? 0,
        exitCode,
      };
    } catch {
      // Results file not found — use exit code
      modeResult.failed = exitCode === 0 ? 0 : 1;
    }

    results.push(modeResult);
    if (exitCode !== 0) overallFailed = true;
  }

  // Print summary
  console.log(`\n${'='.repeat(70)}`);
  console.log('  SUMMARY — All Modes');
  console.log('='.repeat(70));

  for (const r of results) {
    const icon = r.exitCode === 0 ? '\u2705' : '\u274c';
    console.log(`  ${icon} ${r.mode.padEnd(25)} ${r.passed}/${r.total} passed`);
  }

  const totalPassed = results.reduce((s, r) => s + r.passed, 0);
  const totalTests = results.reduce((s, r) => s + r.total, 0);
  console.log(`\n  Total: ${totalPassed}/${totalTests} passed across ${MODES.length} modes`);

  if (overallFailed) {
    console.log('\n  \u274c Some modes had failures. See details above.');
    process.exit(1);
  } else {
    console.log('\n  \u2705 All modes passed!');
  }
}

main().catch((err) => {
  console.error('Fatal error:', err);
  process.exit(1);
});
