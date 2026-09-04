import { Client } from 'pg';
import { randomUUID } from 'crypto';

import OAuth2Provider from 'supertokens-node/recipe/oauth2provider';

import {
  measureRepeated,
  measureTime,
  runStep,
  getCheckpoint,
  FailureTracker,
  CheckpointSize,
} from '../common/utils';

// ---------------------------------------------------------------------------
// OAuth phase.
//
// The 1M-user suite seeds no OAuth data, so the OAuth half of the feature-flag
// stats and every oauth_sessions / oauth_m2m_tokens query path is invisible to
// the budgets, ratios and pg_stat_statements machinery. This module seeds that
// data — OAuth clients + M2M-token stats volume + oauth_sessions — and measures
// the OAuth-dependent paths, so they land in the same summary table / ratio /
// spill machinery as everything else.
//
// Seeding follows the suite's convention: SDK/API where possible (the few
// hundred real clients, the real M2M token issuances that pin the burst
// assertion), direct SQL where the volume demands it (the ~2M M2M-stat rows and
// ~1M oauth_sessions rows — issuing those through the provider would take
// hours). The direct-SQL paths are the one place this module is coupled to the
// plugin schema; that coupling is unavoidable for the volume and is contained
// here.
//
// Coordination note: the burst-accuracy assertion and the O(1)-class bounds on
// the stats/revoke steps require the plugin-side fixes to be in the image under
// test (supertokens-postgresql-plugin#357 M2M rollup + the revoke-index work).
// A run against an older image is expected red on exactly those steps — that is
// informative, not a workaround target.
// ---------------------------------------------------------------------------

// Connection URI to the stress-test Postgres — same default as pgStatStatements.
const PG_CONNECTION_URI =
  process.env.STRESS_TEST_PG_CONNECTION_URI ??
  'postgresql://supertokens:supertokens@localhost:5432/supertokens';

// Seed sizes (env-overridable so smoke runs shrink). Defaults match the issue:
// a few hundred clients, ~2M M2M issuances, ~1M oauth_sessions.
const OAUTH_CLIENTS = Number(process.env.STRESS_TEST_OAUTH_CLIENTS ?? '300') || 300;
const OAUTH_M2M_TOKENS = Number(process.env.STRESS_TEST_OAUTH_M2M_TOKENS ?? '2000000') || 2000000;
const OAUTH_SESSIONS = Number(process.env.STRESS_TEST_OAUTH_SESSIONS ?? '1000000') || 1000000;
// Tokens issued (for real) inside one second for the burst-accuracy assertion.
const OAUTH_BURST = Number(process.env.STRESS_TEST_OAUTH_BURST ?? '500') || 500;
// Fraction of each target seeded at the 100k ("small") checkpoint; the rest is
// added before the 1M ("large") pass, so the OAuth data grows between the two
// passes and the two-size scaling ratios are meaningful. Mirrors the ~10% user
// checkpoint (SMALL_CHECKPOINT_FILES).
const OAUTH_SMALL_FRACTION = Number(process.env.STRESS_TEST_OAUTH_SMALL_FRACTION ?? '0.1') || 0.1;

const SECONDS_PER_HOUR = 3600;
const DAYS_30_SECONDS = 30 * 24 * SECONDS_PER_HOUR;
const RETENTION_SECONDS = 31 * 24 * SECONDS_PER_HOUR;
const SCOPE = 'stress.read stress.write offline_access openid';
const REQUEST_SCOPE = ['stress.read'];

const nowSeconds = (): number => Math.floor(Date.now() / 1000);

export interface SeededClient {
  clientId: string;
  clientSecret: string;
  isCcOnly: boolean;
}

/**
 * State shared between the seeding steps and the measurement steps: the set of
 * clients (so tokens can be minted / revoked against a real client), a sample
 * seeded oauth_sessions handle (for revoke-by-handle) and which of the two
 * M2M-stats table shapes the running image uses.
 */
export interface OAuthStore {
  clients: SeededClient[];
  ccClients: SeededClient[];
  sampleSessionHandle?: string;
  statsTable: 'rollup' | 'legacy' | 'unknown';
  seededSessions: number;
  seededM2M: number;
}

export const newOAuthStore = (): OAuthStore => ({
  clients: [],
  ccClients: [],
  statsTable: 'unknown',
  seededSessions: 0,
  seededM2M: 0,
});

// ---------------------------------------------------------------------------
// Low-level helpers
// ---------------------------------------------------------------------------

const withPg = async <T>(fn: (client: Client) => Promise<T>): Promise<T> => {
  const client = new Client({ connectionString: PG_CONNECTION_URI });
  await client.connect();
  try {
    return await fn(client);
  } finally {
    await client.end().catch(() => {});
  }
};

/**
 * Which M2M-stats table the running image reads. Post-#357 it is the bucketed
 * rollup (oauth_m2m_token_stats); pre-#357 it is the per-token oauth_m2m_tokens.
 * The stats feature-flag queries only ever read the one the image has, so the
 * bulk seed has to target that one for the created-since/alive counts to reflect
 * the seeded volume.
 */
const detectStatsTable = async (client: Client): Promise<'rollup' | 'legacy' | 'unknown'> => {
  const res = await client.query(
    `SELECT table_name FROM information_schema.tables
       WHERE table_name IN ('oauth_m2m_token_stats', 'oauth_m2m_tokens')`
  );
  const names = new Set(res.rows.map((r) => r.table_name));
  if (names.has('oauth_m2m_token_stats')) return 'rollup';
  if (names.has('oauth_m2m_tokens')) return 'legacy';
  return 'unknown';
};

// Insert `rows` (arrays of column values) into `table` in batches, one
// multi-row INSERT per batch. `columns` names the columns; `conflict` is an
// optional trailing "ON CONFLICT …" clause.
const batchInsert = async (
  client: Client,
  table: string,
  columns: string[],
  rows: unknown[][],
  conflict = '',
  batchSize = 1000
): Promise<void> => {
  for (let start = 0; start < rows.length; start += batchSize) {
    const batch = rows.slice(start, start + batchSize);
    const values: unknown[] = [];
    const tuples = batch.map((row, i) => {
      const base = i * columns.length;
      const placeholders = row.map((_, j) => `$${base + j + 1}`);
      values.push(...row);
      return `(${placeholders.join(', ')})`;
    });
    await client.query(
      `INSERT INTO ${table} (${columns.join(', ')}) VALUES ${tuples.join(', ')} ${conflict}`,
      values
    );
  }
};

// ---------------------------------------------------------------------------
// Seeding
// ---------------------------------------------------------------------------

/**
 * Create `count` OAuth clients through the SDK (a real provider round-trip
 * each), a mix of client-credentials-only and regular clients so both
 * feature-flag client counters have realistic values. Appends to the store.
 */
const seedClients = async (store: OAuthStore, count: number): Promise<void> => {
  let created = 0;
  for (let i = 0; i < count; i++) {
    const isCcOnly = i % 2 === 0;
    const grantTypes = isCcOnly
      ? ['client_credentials']
      : ['authorization_code', 'refresh_token', 'client_credentials'];
    try {
      const res: any = await OAuth2Provider.createOAuth2Client({
        clientName: `stress-${isCcOnly ? 'cc' : 'reg'}-${store.clients.length}`,
        grantTypes,
        scope: SCOPE,
        responseTypes: isCcOnly ? [] : ['code', 'id_token'],
        redirectUris: isCcOnly ? [] : ['http://localhost:3001/auth/callback'],
        // createTokenForClientCredentials passes the secret in the request body
        // (client_secret_post), so register the client to accept that method.
        tokenEndpointAuthMethod: 'client_secret_post',
        audience: [],
      });
      if (res.status !== 'OK' || !res.client) {
        FailureTracker.getInstance().recordFailure('OAuth client seeding', res.status ?? 'ERROR');
        continue;
      }
      const rec: SeededClient = {
        clientId: res.client.clientId,
        clientSecret: res.client.clientSecret,
        isCcOnly,
      };
      store.clients.push(rec);
      if (isCcOnly) store.ccClients.push(rec);
      created++;
    } catch (e) {
      FailureTracker.getInstance().recordFailure('OAuth client seeding', 'EXCEPTION');
      // Keep going; a partial client set is still usable for the rest of the phase.
      console.warn(`    OAuth client create failed: ${(e as Error).message}`);
    }
  }
  console.log(`    Seeded ${created}/${count} OAuth clients (total ${store.clients.length})`);
};

/**
 * Seed `count` M2M-token issuances' worth of stats, spread over the last 30
 * days with two TTLs, into whichever stats table the image uses.
 *
 * Rollup image: a handful of hourly (iat_bucket, exp_bucket) counter rows whose
 * counts SUM to `count` — this is exactly the shape addOAuthM2MTokenForStats
 * produces, so the feature-flag SUM queries see the full volume from ~thousands
 * of rows. Legacy image: `count` per-token rows (the volume the pre-rollup
 * per-token queries were meant to be measured against — deliberately heavy).
 */
const seedM2MStats = async (client: Client, store: OAuthStore, count: number): Promise<void> => {
  if (count <= 0) return;
  const now = nowSeconds();

  if (store.statsTable === 'rollup') {
    // Hourly iat buckets across the last 30 days; each split over two TTLs (1h
    // and 24h) so some buckets are alive now (exp_bucket > now-hour) and the
    // alive counter is non-trivial. Distribute `count` as evenly as possible.
    const nowBucket = Math.floor(now / SECONDS_PER_HOUR);
    const startBucket = Math.floor((now - DAYS_30_SECONDS) / SECONDS_PER_HOUR);
    const ttlHours = [1, 24];
    const buckets: [number, number][] = [];
    for (let iat = startBucket; iat <= nowBucket; iat++) {
      for (const ttl of ttlHours) buckets.push([iat, iat + ttl]);
    }
    const perRow = Math.max(1, Math.floor(count / buckets.length));
    let remaining = count;
    const rows: unknown[][] = [];
    for (const [iatB, expB] of buckets) {
      const c = Math.min(perRow, remaining);
      if (c <= 0) break;
      rows.push(['public', iatB, expB, c]);
      remaining -= c;
    }
    // Any rounding remainder lands on the most-recent (alive) bucket.
    if (remaining > 0 && rows.length > 0) {
      (rows[rows.length - 1][3] as number) += remaining;
    }
    await batchInsert(
      client,
      'oauth_m2m_token_stats',
      ['app_id', 'iat_bucket', 'exp_bucket', 'count'],
      rows,
      // Accumulate across the two seeding passes, mirroring the rollup upsert.
      'ON CONFLICT (app_id, iat_bucket, exp_bucket) DO UPDATE SET count = oauth_m2m_token_stats.count + EXCLUDED.count'
    );
    store.seededM2M += count;
    console.log(`    Seeded ${count} M2M issuances into rollup (${rows.length} bucket rows)`);
    return;
  }

  if (store.statsTable === 'legacy') {
    if (store.clients.length === 0) {
      FailureTracker.getInstance().recordFailure('OAuth M2M stats seeding', 'NO_CLIENTS');
      return;
    }
    // One row per issuance: (app_id, client_id, iat) is the PK, so give each row
    // a distinct (client, iat-second). iat spread over the last 30 days; exp =
    // iat + one of the two TTLs. ON CONFLICT DO NOTHING covers any collision.
    const rows: unknown[][] = [];
    const nClients = store.clients.length;
    const startSec = now - DAYS_30_SECONDS;
    for (let i = 0; i < count; i++) {
      const clientId = store.clients[i % nClients].clientId;
      // Stride so successive rows for the same client land on distinct seconds.
      const iat = startSec + (Math.floor(i / nClients) % DAYS_30_SECONDS);
      const ttl = i % 2 === 0 ? SECONDS_PER_HOUR : 24 * SECONDS_PER_HOUR;
      rows.push(['public', clientId, iat, iat + ttl]);
    }
    await batchInsert(
      client,
      'oauth_m2m_tokens',
      ['app_id', 'client_id', 'iat', 'exp'],
      rows,
      'ON CONFLICT DO NOTHING'
    );
    store.seededM2M += count;
    console.log(`    Seeded ${count} M2M per-token rows into legacy oauth_m2m_tokens`);
    return;
  }

  FailureTracker.getInstance().recordFailure('OAuth M2M stats seeding', 'NO_STATS_TABLE');
};

/**
 * Seed `count` oauth_sessions rows referencing the seeded clients. A slice is
 * given an already-expired exp (older than the 31-day retention window) so the
 * cleanup-cron sweep has rows to delete; the rest are alive. Records one sample
 * session handle for the revoke-by-handle measurement.
 */
const seedSessions = async (client: Client, store: OAuthStore, count: number): Promise<void> => {
  if (count <= 0) return;
  if (store.clients.length === 0) {
    FailureTracker.getInstance().recordFailure('OAuth sessions seeding', 'NO_CLIENTS');
    return;
  }
  const now = nowSeconds();
  const nClients = store.clients.length;
  const rows: unknown[][] = [];
  const tag = randomUUID().slice(0, 8);
  for (let i = 0; i < count; i++) {
    const clientId = store.clients[i % nClients].clientId;
    const gid = `stress-gid-${tag}-${store.seededSessions + i}`;
    const handle = `stress-sh-${tag}-${store.seededSessions + i}`;
    // ~10% already expired past retention (swept by cleanup), the rest alive.
    const expired = i % 10 === 0;
    const exp = expired ? now - RETENTION_SECONDS - 3600 : now + 24 * SECONDS_PER_HOUR;
    rows.push([
      gid,
      'public',
      clientId,
      handle,
      `stress-ert-${tag}-${store.seededSessions + i}`,
      `stress-irt-${tag}-${store.seededSessions + i}`,
      randomUUID(),
      exp,
    ]);
    if (store.sampleSessionHandle === undefined && !expired) {
      store.sampleSessionHandle = handle;
    }
  }
  await batchInsert(
    client,
    'oauth_sessions',
    [
      'gid',
      'app_id',
      'client_id',
      'session_handle',
      'external_refresh_token',
      'internal_refresh_token',
      'jti',
      'exp',
    ],
    rows,
    'ON CONFLICT DO NOTHING'
  );
  store.seededSessions += count;
  console.log(`    Seeded ${count} oauth_sessions rows (total ${store.seededSessions})`);
};

/**
 * Seed the OAuth dataset for one checkpoint tranche. `fraction` is the share of
 * each target to reach by the end of this call (0.1 for the small checkpoint, 1
 * for the full run). Clients are all created up-front on the first (small)
 * tranche — a few hundred is cheap and both counts should be stable across
 * passes; the volume tables (M2M stats, sessions) grow between tranches.
 */
export const seedOAuthData = async (
  deployment: any,
  store: OAuthStore,
  size: CheckpointSize
): Promise<void> => {
  const fraction = size === 'small' ? OAUTH_SMALL_FRACTION : 1;
  const clamped = Math.max(0, Math.min(1, fraction));
  console.log(`\n\nSeeding OAuth data to ${Math.round(clamped * 100)}% of targets`);

  // Clients: create the full set on the first tranche.
  if (store.clients.length === 0) {
    await seedClients(store, OAUTH_CLIENTS);
  }
  if (store.clients.length === 0) {
    // Without any clients (feature not licensed / provider down) the rest of the
    // phase cannot run; the FailureTracker entry above turns the run red.
    console.warn('    No OAuth clients available — skipping M2M/session seeding.');
    return;
  }

  await withPg(async (client) => {
    if (store.statsTable === 'unknown') {
      store.statsTable = await detectStatsTable(client);
      console.log(`    M2M stats table shape: ${store.statsTable}`);
    }
    const targetM2M = Math.floor(OAUTH_M2M_TOKENS * clamped);
    const targetSessions = Math.floor(OAUTH_SESSIONS * clamped);
    await seedM2MStats(client, store, Math.max(0, targetM2M - store.seededM2M));
    await seedSessions(client, store, Math.max(0, targetSessions - store.seededSessions));
  });
};

// ---------------------------------------------------------------------------
// Measurement
// ---------------------------------------------------------------------------

// GET /ee/featureflag and pull the OAuth stats block (or undefined if OAuth is
// not licensed / present). Used both by the measured aggregate step and by the
// burst assertion, which reads numberOfM2MTokensCreated deltas from it.
const getOAuthFeatureStats = async (deployment: any): Promise<any | undefined> => {
  const res = await fetch(`${deployment.core_url}/ee/featureflag`, {
    headers: { 'Api-Key': deployment.api_key },
  });
  const body: any = await res.json();
  return body?.usageStats?.oauth;
};

const mintM2MToken = async (clientRec: SeededClient): Promise<string | undefined> => {
  const res: any = await OAuth2Provider.createTokenForClientCredentials(
    clientRec.clientId,
    clientRec.clientSecret,
    REQUEST_SCOPE
  );
  if (res?.status === 'OK') {
    return res.accessToken ?? res.access_token;
  }
  console.warn(`    M2M mint returned ${res?.status ?? 'unknown'}: ${res?.errorDescription ?? ''}`);
  return undefined;
};

/**
 * Measure the OAuth-dependent query paths. Called from runReadPaths for each
 * checkpoint, so measureTime routes the timings to the ratio harness (small) /
 * summary table (large) exactly like the other read paths. Steps are wrapped in
 * runStep so one failure doesn't abort the pass.
 */
export const measureOAuthPaths = async (deployment: any, store: OAuthStore): Promise<void> => {
  console.log('\n\n9. Measuring OAuth-dependent query paths');

  if (store.clients.length === 0) {
    console.warn('    No OAuth clients seeded — skipping OAuth measurements.');
    return;
  }
  const isLargePass = getCheckpoint() !== 'small';
  const ccClient = store.ccClients[0] ?? store.clients[0];

  // --- M2M token issuance (client_credentials) — real provider round-trip that
  // also exercises the stats-write path (survey O2). O(1) in issuance volume. ---
  let sampleToken: string | undefined;
  await runStep(() =>
    measureTime('OAuth M2M token issuance (client_credentials)', async () => {
      sampleToken = await mintM2MToken(ccClient);
    })
  );

  // --- Token introspection with the DB (revoked-token) check, against the
  // seeded oauth_sessions volume (survey O1). ---
  if (sampleToken) {
    await runStep(() =>
      // Repeated: a pure read against the seeded oauth_sessions volume, and
      // the one OAuth step that is both cheap and free of side effects.
      // Issuance and both revokes are left as single samples — repeating them
      // would mint or revoke hundreds of tokens and perturb the burst-accuracy
      // assertion that runs after them.
      measureRepeated('OAuth token introspection', async () => {
        await OAuth2Provider.validateOAuth2AccessToken(sampleToken!, undefined, true);
      })
    );
  }

  // --- Revoke by session handle: the single-handle lookup the new oauth_sessions
  // index is meant to serve (O(1)-class once that index lands). ---
  if (store.sampleSessionHandle) {
    await runStep(() =>
      measureTime('OAuth revoke by session handle', async () => {
        await OAuth2Provider.revokeTokensBySessionHandle(store.sampleSessionHandle!);
      })
    );
  }

  // --- Revoke by client id: marks the client's tokens revoked; the other path
  // the revoke indexes cover. ---
  await runStep(() =>
    measureTime('OAuth revoke by client id', async () => {
      await OAuth2Provider.revokeTokensByClientId(ccClient.clientId);
    })
  );

  // The remaining steps are single-shot correctness / cleanup measurements that
  // are not repeatable across passes, so they run only on the large pass (like
  // the destructive role-delete step) and therefore carry no two-size ratio.
  if (!isLargePass) return;

  // --- Burst-accuracy assertion (survey O2 / plugin #357): issue N tokens for
  // one client within one second and assert the feature-flag created-since
  // counter rises by exactly N. Fails against the pre-rollup per-token mechanism
  // (which records ~1 per client-second) — this is the step that pins the fix. ---
  await runStep(() =>
    measureTime('OAuth M2M created-since burst accuracy', async () => {
      const before = await getOAuthFeatureStats(deployment);
      const beforeCount = before?.numberOfM2MTokensCreated?.[0];
      if (typeof beforeCount !== 'number') {
        throw new Error('OAuth feature-flag stats unavailable (is the OAUTH feature licensed?)');
      }
      let issued = 0;
      const start = Date.now();
      for (let i = 0; i < OAUTH_BURST; i++) {
        const tok = await mintM2MToken(ccClient);
        if (tok) issued++;
      }
      const elapsed = Date.now() - start;
      const after = await getOAuthFeatureStats(deployment);
      const afterCount = after?.numberOfM2MTokensCreated?.[0] ?? 0;
      const delta = afterCount - beforeCount;
      console.log(
        `    Burst: issued ${issued}/${OAUTH_BURST} tokens in ${elapsed}ms; created-since delta = ${delta}`
      );
      if (delta !== issued) {
        throw new Error(
          `created-since rose by ${delta} but ${issued} tokens were issued — ` +
            `the M2M stats mechanism is undercounting (expected with the pre-#357 per-token rows).`
        );
      }
    })
  );

  // --- Cleanup-cron OAuth sweep: the two deletes CleanupOAuthSessionsAndChallenges
  // runs (expired sessions + expired M2M-stat rows), timed against the seeded
  // volume. Measured directly in SQL because the cron has no on-demand trigger.
  // This mutates seeded data, so it is the last OAuth step. ---
  await runStep(() =>
    measureTime('OAuth cleanup cron sweep', async (signal) => {
      await withPg(async (client) => {
        const cutoff = nowSeconds() - RETENTION_SECONDS;
        if (signal.aborted) return;
        await client.query('DELETE FROM oauth_sessions WHERE exp < $1', [cutoff]);
        if (store.statsTable === 'rollup') {
          await client.query('DELETE FROM oauth_m2m_token_stats WHERE exp_bucket < $1', [
            Math.floor(cutoff / SECONDS_PER_HOUR),
          ]);
        } else if (store.statsTable === 'legacy') {
          await client.query('DELETE FROM oauth_m2m_tokens WHERE exp < $1', [cutoff]);
        }
      });
    })
  );
};
