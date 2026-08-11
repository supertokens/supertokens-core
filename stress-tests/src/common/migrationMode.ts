// ---------------------------------------------------------------------------
// Migration-mode labelling + confirmation for the 1M-user suite (issue #1351).
//
// The suite deploys a fresh core and, until now, always ran it in the default
// LEGACY migration mode — so every measured read path exercised the legacy
// all_auth_recipe_users code path, never the migrated-schema paths
// (app_id_to_user_id / recipe_user_tenants) that large migrated deployments
// actually run. The stress-tests workflow now runs one matrix leg per mode; a
// leg tells the suite which mode the core was deployed in via
// STRESS_TEST_MIGRATION_MODE so its stats.json, summary table and comparison
// baseline are tagged by mode and the two legs never cross-compare.
//
// This module owns the pure resolution of that label and a startup guard that
// confirms the core is really in the expected mode — without the guard a leg
// whose SUPERTOKENS_MIGRATION_MODE never took effect would silently re-run the
// LEGACY paths and still report itself as the MIGRATED leg.
// ---------------------------------------------------------------------------

export const MIGRATION_MODES = [
  'LEGACY',
  'DUAL_WRITE_READ_OLD',
  'DUAL_WRITE_READ_NEW',
  'MIGRATED',
] as const;

export type MigrationMode = (typeof MIGRATION_MODES)[number];

export const DEFAULT_MIGRATION_MODE: MigrationMode = 'LEGACY';

export const isMigrationMode = (v: string): v is MigrationMode =>
  (MIGRATION_MODES as readonly string[]).includes(v);

/**
 * Resolve the migration-mode label for this run from an env value (defaults to
 * STRESS_TEST_MIGRATION_MODE). Case-insensitive; an absent or unrecognized
 * value falls back to LEGACY so a local run with nothing set behaves exactly
 * as it did before this feature.
 */
export const resolveMigrationMode = (
  raw: string | undefined = process.env.STRESS_TEST_MIGRATION_MODE
): MigrationMode => {
  if (raw === undefined || raw.trim() === '') return DEFAULT_MIGRATION_MODE;
  const upper = raw.trim().toUpperCase();
  if (isMigrationMode(upper)) return upper;
  console.warn(
    `    Unrecognized STRESS_TEST_MIGRATION_MODE=${JSON.stringify(raw)}; ` +
      `defaulting to ${DEFAULT_MIGRATION_MODE}. Valid: ${MIGRATION_MODES.join(', ')}.`
  );
  return DEFAULT_MIGRATION_MODE;
};

// GET /migration/mode is a versioned WebserverAPI, so it needs a cdi-version
// header just like the read-path direct HTTP calls. Fetch the newest version
// the core advertises; fall back to a recent one if /apiversion is unreachable.
const cmpSemVer = (a: string, b: string): number => {
  const pa = a.split('.').map((n) => parseInt(n, 10) || 0);
  const pb = b.split('.').map((n) => parseInt(n, 10) || 0);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] ?? 0) - (pb[i] ?? 0);
    if (d !== 0) return d;
  }
  return 0;
};

const getCdiVersion = async (coreUrl: string, apiKey: string): Promise<string> => {
  try {
    const res = await fetch(`${coreUrl}/apiversion`, { headers: { 'Api-Key': apiKey } });
    const body: any = await res.json();
    const versions: string[] = Array.isArray(body?.versions) ? body.versions : [];
    const max = versions.sort(cmpSemVer).pop();
    if (max) return max;
  } catch (e) {
    console.warn(`    Could not fetch /apiversion for the mode check: ${(e as Error).message}`);
  }
  return '5.4';
};

/**
 * Read the migration mode the core is actually running the base tenant in via
 * GET /migration/mode. Returns undefined when the endpoint is absent
 * (FEATURE_NOT_SUPPORTED_ERROR or 404) — i.e. the image predates the migration
 * feature — so the caller can decide how to treat that per expected mode.
 */
export const fetchCoreMigrationMode = async (
  coreUrl: string,
  apiKey: string
): Promise<string | undefined> => {
  const cdiVersion = await getCdiVersion(coreUrl, apiKey);
  const res = await fetch(`${coreUrl}/migration/mode`, {
    headers: { 'Api-Key': apiKey, 'cdi-version': cdiVersion },
  });
  if (res.status === 404) return undefined;
  const text = await res.text();
  let body: any;
  try {
    body = JSON.parse(text);
  } catch {
    throw new Error(
      `GET /migration/mode returned non-JSON (HTTP ${res.status}): ${text.slice(0, 200)}`
    );
  }
  if (body?.status === 'FEATURE_NOT_SUPPORTED_ERROR') return undefined;
  if (body?.status !== 'OK') {
    throw new Error(`GET /migration/mode failed: HTTP ${res.status} body=${JSON.stringify(body)}`);
  }
  // A non-root CUD returns { status, mode }. The suite's base tenant is the
  // root CUD, which returns { status, cuds: [{ connectionUriDomain, mode }] }.
  if (typeof body.mode === 'string') return body.mode;
  if (Array.isArray(body.cuds)) {
    const base = body.cuds.find((c: any) => (c?.connectionUriDomain ?? '') === '') ?? body.cuds[0];
    if (base && typeof base.mode === 'string') return base.mode;
  }
  return undefined;
};

/**
 * Confirm the deployed core is really running in `expected` mode before the
 * suite spends an hour measuring it. A MIGRATED leg whose config never took
 * effect would otherwise silently re-measure the LEGACY paths, so a mismatch —
 * or an unavailable endpoint when MIGRATED was expected — is fatal. When LEGACY
 * is expected an unavailable endpoint is tolerated: that is the pre-migration
 * image behaving exactly as this suite always assumed.
 */
export const assertCoreMigrationMode = async (
  coreUrl: string,
  apiKey: string,
  expected: MigrationMode
): Promise<void> => {
  const actual = await fetchCoreMigrationMode(coreUrl, apiKey);
  if (actual === undefined) {
    if (expected === DEFAULT_MIGRATION_MODE) {
      console.log(
        `Migration mode: /migration/mode unavailable; assuming pre-migration ${DEFAULT_MIGRATION_MODE}.`
      );
      return;
    }
    throw new Error(
      `Expected the core in ${expected} mode but GET /migration/mode is unavailable — ` +
        `the image may predate the migration feature or SUPERTOKENS_MIGRATION_MODE did not take effect.`
    );
  }
  if (actual.toUpperCase() !== expected) {
    throw new Error(
      `Core migration mode mismatch: expected ${expected}, core reports ${actual}. ` +
        `SUPERTOKENS_MIGRATION_MODE did not take effect on the deployed core.`
    );
  }
  console.log(`Confirmed core migration mode: ${actual}.`);
};
