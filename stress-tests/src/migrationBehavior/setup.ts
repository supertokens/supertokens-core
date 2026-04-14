/**
 * REVIEW-007: Migration behavior test setup and helpers.
 *
 * Provides core connection setup, SDK initialization, migration mode detection,
 * and test runner infrastructure for black-box API-level tests.
 */

import SuperTokens from 'supertokens-node';
import EmailPassword from 'supertokens-node/recipe/emailpassword';
import Passwordless from 'supertokens-node/recipe/passwordless';
import ThirdParty from 'supertokens-node/recipe/thirdparty';
import AccountLinking from 'supertokens-node/recipe/accountlinking';
import Session from 'supertokens-node/recipe/session';
import { setupLicense, LICENSE_FOR_TEST } from '../common/utils';

// ── Config ──────────────────────────────────────────────────────────────────

export const CORE_URL = process.env.CORE_URL ?? 'http://localhost:3567';
export const API_KEY = process.env.API_KEY ?? 'qwertyuiopasdfghjklzxcvbnm';
export const MIGRATION_MODE = process.env.MIGRATION_MODE ?? 'MIGRATED';
export const CONCURRENCY = parseInt(process.env.CONCURRENCY ?? '10', 10);

// ── Types ───────────────────────────────────────────────────────────────────

export type MigrationMode = 'LEGACY' | 'DUAL_WRITE_READ_OLD' | 'DUAL_WRITE_READ_NEW' | 'MIGRATED';

export interface TestResult {
  name: string;
  passed: boolean;
  error?: string;
  durationMs: number;
}

export interface TestSuiteResult {
  mode: MigrationMode;
  total: number;
  passed: number;
  failed: number;
  tests: TestResult[];
}

// ── SDK Init ────────────────────────────────────────────────────────────────

let initialized = false;

export function initSdk(coreUrl: string = CORE_URL, apiKey: string = API_KEY) {
  if (initialized) {
    // supertokens-node doesn't support re-init cleanly, but for tests
    // against the same core it's fine to skip.
    return;
  }

  SuperTokens.init({
    appInfo: {
      appName: 'MigrationBehaviorTests',
      apiDomain: 'http://localhost:3001',
      websiteDomain: 'http://localhost:3000',
      apiBasePath: '/auth',
      websiteBasePath: '/auth',
    },
    supertokens: {
      connectionURI: coreUrl,
      apiKey: apiKey,
    },
    recipeList: [
      EmailPassword.init(),
      Passwordless.init({
        contactMethod: 'EMAIL_OR_PHONE',
        flowType: 'USER_INPUT_CODE',
      }),
      ThirdParty.init({
        signInAndUpFeature: {
          providers: [{ config: { thirdPartyId: 'google' } }],
        },
      }),
      AccountLinking.init({
        shouldDoAutomaticAccountLinking: async () => ({
          shouldAutomaticallyLink: false,
          shouldRequireVerification: false,
        }),
      }),
      Session.init(),
    ],
  });
  initialized = true;
}

// ── Test Runner ─────────────────────────────────────────────────────────────

type TestFn = () => Promise<void>;

interface TestRegistration {
  name: string;
  fn: TestFn;
}

const registeredTests: TestRegistration[] = [];

/**
 * Register a test. Tests are collected and run by `runRegisteredTests`.
 */
export function test(name: string, fn: TestFn) {
  registeredTests.push({ name, fn });
}

/**
 * Run all registered tests and return results.
 */
export async function runRegisteredTests(mode: MigrationMode): Promise<TestSuiteResult> {
  const results: TestResult[] = [];

  for (const t of registeredTests) {
    const start = Date.now();
    try {
      await t.fn();
      results.push({ name: t.name, passed: true, durationMs: Date.now() - start });
    } catch (err: any) {
      results.push({
        name: t.name,
        passed: false,
        error: err.message ?? String(err),
        durationMs: Date.now() - start,
      });
    }
  }

  const passed = results.filter((r) => r.passed).length;
  return { mode, total: results.length, passed, failed: results.length - passed, tests: results };
}

export function clearTests() {
  registeredTests.length = 0;
}

// ── Assertions ──────────────────────────────────────────────────────────────

export function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(`Assertion failed: ${message}`);
  }
}

export function assertEqual<T>(actual: T, expected: T, label: string) {
  if (actual !== expected) {
    throw new Error(`${label}: expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
  }
}

export function assertNotNull<T>(value: T | null | undefined, label: string): asserts value is T {
  if (value === null || value === undefined) {
    throw new Error(`${label}: expected non-null value`);
  }
}

export function assertIncludes<T>(arr: T[], item: T, label: string) {
  if (!arr.includes(item)) {
    throw new Error(`${label}: expected array to include ${JSON.stringify(item)}`);
  }
}

// ── User Helpers ────────────────────────────────────────────────────────────

let userCounter = 0;

export function uniqueEmail(prefix: string = 'user'): string {
  return `${prefix}-${Date.now()}-${++userCounter}@test.com`;
}

export async function createEpUser(email?: string): Promise<{
  userId: string;
  email: string;
}> {
  const e = email ?? uniqueEmail('ep');
  const resp = await EmailPassword.signUp('public', e, 'Password123!');
  if (resp.status !== 'OK') {
    throw new Error(`signUp failed: ${resp.status}`);
  }
  return { userId: resp.user.id, email: e };
}

export async function createTpUser(
  thirdPartyId: string = 'google',
  email?: string
): Promise<{ userId: string; email: string; tpUserId: string }> {
  const e = email ?? uniqueEmail('tp');
  const tpUserId = `tp-${Date.now()}-${++userCounter}`;
  const resp = await ThirdParty.manuallyCreateOrUpdateUser(
    'public',
    thirdPartyId,
    tpUserId,
    e,
    false
  );
  if (resp.status !== 'OK') {
    throw new Error(`TP signInUp failed: ${resp.status}`);
  }
  return { userId: resp.user.id, email: e, tpUserId };
}

export async function makePrimary(userId: string): Promise<void> {
  const resp = await AccountLinking.createPrimaryUser(
    SuperTokens.convertToRecipeUserId(userId)
  );
  if (resp.status !== 'OK') {
    throw new Error(`createPrimaryUser failed: ${resp.status}`);
  }
}

export async function linkUsers(recipeUserId: string, primaryUserId: string): Promise<boolean> {
  const resp = await AccountLinking.linkAccounts(
    SuperTokens.convertToRecipeUserId(recipeUserId),
    primaryUserId
  );
  return resp.status === 'OK';
}

export async function unlinkUser(userId: string): Promise<boolean> {
  const resp = await AccountLinking.unlinkAccount(
    SuperTokens.convertToRecipeUserId(userId)
  );
  return resp.status === 'OK';
}

export async function getUser(userId: string) {
  return SuperTokens.getUser(userId);
}

export async function deleteUser(
  userId: string,
  removeAllLinkedAccounts: boolean = false
): Promise<void> {
  await SuperTokens.deleteUser(userId, removeAllLinkedAccounts);
}

// ── Core API Helpers (direct HTTP) ──────────────────────────────────────────

export async function coreApiRequest(
  method: string,
  path: string,
  body?: any
): Promise<{ status: number; body: any }> {
  const resp = await fetch(`${CORE_URL}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      'api-key': API_KEY,
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });

  const text = await resp.text();
  let parsed: any;
  try {
    parsed = JSON.parse(text);
  } catch {
    parsed = text;
  }

  return { status: resp.status, body: parsed };
}

// ── App-scoped helpers (direct HTTP for blue-green tests) ───────────────────
//
// These make raw HTTP calls to the core with an /appid-<id>/ path prefix,
// bypassing the SDK (which is bound to the default app at init time).
// Used by testModeTransition.ts to test against a dedicated app whose
// migration_mode is controlled via the multitenancy coreConfig API.

export type Mode = 'LEGACY' | 'DUAL_WRITE_READ_OLD' | 'DUAL_WRITE_READ_NEW' | 'MIGRATED';

/**
 * Create or update an app with a specific migration_mode via the multitenancy API.
 * PUT /recipe/multitenancy/app/v2  { appId, coreConfig: { migration_mode } }
 */
export async function createOrUpdateApp(appId: string, migrationMode: Mode): Promise<void> {
  const resp = await coreApiRequest('PUT', '/recipe/multitenancy/app/v2', {
    appId,
    coreConfig: { migration_mode: migrationMode },
  });
  if (resp.body?.status !== 'OK' && resp.body?.status !== 'TENANT_ALREADY_EXISTS') {
    throw new Error(
      `Failed to create/update app ${appId} with mode ${migrationMode}: ${JSON.stringify(resp.body)}`
    );
  }
}

/**
 * Make a core API request scoped to a specific app.
 * Prefixes the path with /appid-<appId>/.
 */
export async function appScopedRequest(
  appId: string,
  method: string,
  path: string,
  body?: any,
  extraHeaders?: Record<string, string>
): Promise<{ status: number; body: any }> {
  const url = `${CORE_URL}/appid-${appId}${path}`;
  const resp = await fetch(url, {
    method,
    headers: {
      'Content-Type': 'application/json',
      'api-key': API_KEY,
      ...extraHeaders,
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });

  const text = await resp.text();
  let parsed: any;
  try {
    parsed = JSON.parse(text);
  } catch {
    parsed = text;
  }

  return { status: resp.status, body: parsed };
}

/**
 * Sign up an emailpassword user in a specific app via direct HTTP.
 */
export async function appSignUp(
  appId: string,
  email?: string,
  password: string = 'Password123!'
): Promise<{ userId: string; email: string }> {
  const e = email ?? uniqueEmail('app-ep');
  const resp = await appScopedRequest(appId, 'POST', '/recipe/signup', {
    email: e,
    password,
  });
  if (resp.body?.status !== 'OK') {
    throw new Error(`appSignUp failed: ${JSON.stringify(resp.body)}`);
  }
  return { userId: resp.body.user.id, email: e };
}

/**
 * Create a third-party user in a specific app via direct HTTP.
 */
export async function appTpSignInUp(
  appId: string,
  thirdPartyId: string = 'google',
  email?: string
): Promise<{ userId: string; email: string; tpUserId: string }> {
  const e = email ?? uniqueEmail('app-tp');
  const tpUserId = `tp-${Date.now()}-${++userCounter}`;
  const resp = await appScopedRequest(appId, 'POST', '/recipe/signinup', {
    thirdPartyId,
    thirdPartyUserId: tpUserId,
    email: { id: e, isVerified: false },
  });
  if (resp.body?.status !== 'OK') {
    throw new Error(`appTpSignInUp failed: ${JSON.stringify(resp.body)}`);
  }
  return { userId: resp.body.user.id, email: e, tpUserId };
}

/**
 * Create a primary user in a specific app via direct HTTP.
 */
export async function appMakePrimary(appId: string, recipeUserId: string): Promise<void> {
  const resp = await appScopedRequest(appId, 'POST', '/recipe/accountlinking/user/primary', {
    recipeUserId,
  });
  if (resp.body?.status !== 'OK') {
    throw new Error(`appMakePrimary failed: ${JSON.stringify(resp.body)}`);
  }
}

/**
 * Link accounts in a specific app via direct HTTP.
 */
export async function appLinkAccounts(
  appId: string,
  recipeUserId: string,
  primaryUserId: string
): Promise<boolean> {
  const resp = await appScopedRequest(appId, 'POST', '/recipe/accountlinking/user/link', {
    recipeUserId,
    primaryUserId,
  });
  return resp.body?.status === 'OK';
}

/**
 * Unlink accounts in a specific app via direct HTTP.
 */
export async function appUnlinkAccount(appId: string, recipeUserId: string): Promise<boolean> {
  const resp = await appScopedRequest(appId, 'POST', '/recipe/accountlinking/user/unlink', {
    recipeUserId,
  });
  return resp.body?.status === 'OK';
}

/**
 * Get user by ID in a specific app via direct HTTP. Throws if the call fails
 * with anything other than UNKNOWN_USER_ID_ERROR (in which case it returns
 * undefined). This makes it easy to distinguish "user truly doesn't exist"
 * from "request failed for some other reason" in tests.
 */
export async function appGetUser(
  appId: string,
  userId: string
): Promise<any | undefined> {
  const resp = await appScopedRequest(appId, 'GET', `/user/id?userId=${userId}`);
  if (resp.body?.status === 'OK') {
    return resp.body.user;
  }
  if (resp.body?.status === 'UNKNOWN_USER_ID_ERROR') {
    return undefined;
  }
  throw new Error(
    `appGetUser(${appId}, ${userId}) failed: HTTP ${resp.status} body=${JSON.stringify(resp.body)}`
  );
}

/**
 * Delete user in a specific app via direct HTTP. Defaults to removing only
 * the specified recipe user (not the whole linked group). Pass
 * `removeAllLinkedAccounts=true` to match the core's default behaviour and
 * delete every linked login method along with the primary.
 */
export async function appDeleteUser(
  appId: string,
  userId: string,
  removeAllLinkedAccounts: boolean = false
): Promise<void> {
  await appScopedRequest(appId, 'POST', '/user/remove', {
    userId,
    removeAllLinkedAccounts,
  });
}

/**
 * Update emailpassword user email in a specific app via direct HTTP.
 */
export async function appUpdateEmail(
  appId: string,
  recipeUserId: string,
  newEmail: string
): Promise<{ status: string }> {
  const resp = await appScopedRequest(appId, 'PUT', '/recipe/user', {
    recipeUserId,
    email: newEmail,
  });
  return { status: resp.body?.status ?? 'UNKNOWN' };
}

/**
 * Update third-party user email via signInUp in a specific app.
 */
export async function appTpUpdateEmail(
  appId: string,
  thirdPartyId: string,
  thirdPartyUserId: string,
  newEmail: string
): Promise<void> {
  const resp = await appScopedRequest(appId, 'POST', '/recipe/signinup', {
    thirdPartyId,
    thirdPartyUserId,
    email: { id: newEmail, isVerified: false },
  });
  if (resp.body?.status !== 'OK') {
    throw new Error(`appTpUpdateEmail failed: ${JSON.stringify(resp.body)}`);
  }
}

/**
 * Set up license for a specific app.
 */
export async function appSetupLicense(appId: string): Promise<void> {
  await appScopedRequest(appId, 'PUT', '/ee/license', {
    licenseKey: LICENSE_FOR_TEST,
  });
}

// ── Reporting ───────────────────────────────────────────────────────────────

export function printResults(result: TestSuiteResult) {
  console.log(`\n${'='.repeat(70)}`);
  console.log(`  Mode: ${result.mode}  |  ${result.passed}/${result.total} passed`);
  console.log('='.repeat(70));

  for (const t of result.tests) {
    const icon = t.passed ? '\u2705' : '\u274c';
    const time = `(${t.durationMs}ms)`;
    console.log(`  ${icon} ${t.name} ${time}`);
    if (!t.passed && t.error) {
      console.log(`     \u2514\u2500 ${t.error}`);
    }
  }

  if (result.failed > 0) {
    console.log(`\n  \u274c ${result.failed} test(s) FAILED`);
  } else {
    console.log(`\n  \u2705 All tests passed`);
  }
}
