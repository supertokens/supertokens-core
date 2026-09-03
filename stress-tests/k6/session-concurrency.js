// Session concurrency probe — a PR-sized saturation test, not a capacity test.
//
// The 1M-user suite next door measures how per-request cost grows with the
// DATASET. It cannot see anything that only appears when requests contend for
// the same connection pool, and that is where production keeps hurting:
// sustained 5.0s Hikari `connectionTimeout` 500s on the session/refresh path,
// root-caused to one logical operation holding two connections at once.
//
// So this probe fixes the dataset at ~nothing and varies the ONE thing that
// matters: concurrency relative to `postgresql_connection_pool_size`. It runs
// against the same docker-compose as the stress suite, with the pool pinned
// small (POOL_SIZE, default 3) so saturation is reachable in seconds rather
// than needing a million users and 75 minutes.
//
// The gate that earns its keep is the refresh wall. Hikari's connectionTimeout
// is 5000ms; with a healthy core, 12 concurrent flows against a pool of 3 queue
// for single-digit milliseconds, three orders of magnitude below that. A
// request that actually reaches 5s did not queue — it parked, which is the
// deadlock/starvation signature. The margin is deliberately enormous so runner
// noise cannot trip it.
//
// No user seeding: POST /recipe/session accepts an arbitrary userId, so the
// whole probe is self-contained.
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const CORE_URL = __ENV.CORE_URL || 'http://localhost:3567';
const API_KEY = __ENV.API_KEY || 'qwertyuiopasdfghjklzxcvbnm';
const CDI = __ENV.CDI_VERSION || '5.4';
const POOL = parseInt(__ENV.POOL_SIZE || '3', 10);

// Hikari's connectionTimeout in ConnectionPool.java. A request that reaches it
// waited out the entire pool timeout.
const POOL_TIMEOUT_MS = 5000;

const PARAMS = {
  headers: { 'content-type': 'application/json', 'api-key': API_KEY, 'cdi-version': CDI },
  timeout: '30s',
};

// Counts 5xx separately from the pass/fail gates. The core has no admission
// control or shed path today (no 503 anywhere in the codebase), so overload
// surfaces as a 500 after a 5s pool wait rather than a typed rejection. That is
// a known gap, not a regression, so it is reported and not gated — if it ever
// starts returning 503 + Retry-After this counter is where you will see it.
const untypedShed = new Counter('untyped_shed_5xx');

export const options = {
  // Three phases: comfortably under the pool, just over it, and well over.
  // Sequential (via startTime) so each phase's requests carry their own tag and
  // the thresholds can speak about one regime at a time.
  scenarios: {
    under: { executor: 'constant-vus', vus: Math.max(1, POOL - 1), duration: '30s', startTime: '0s', tags: { phase: 'under' } },
    at:    { executor: 'constant-vus', vus: POOL + 1,              duration: '45s', startTime: '35s', tags: { phase: 'at' } },
    over:  { executor: 'constant-vus', vus: POOL * 4,              duration: '45s', startTime: '85s', tags: { phase: 'over' } },
  },
  thresholds: {
    // Below the pool size there is no contention to speak of; anything failing
    // here is a plain bug, not saturation.
    'http_req_failed{phase:under}': ['rate<0.01'],
    // THE GATE. See the header note on the margin.
    'http_req_duration{op:refresh}': [`max<${POOL_TIMEOUT_MS}`],
    'http_req_duration{op:verify}': [`max<${POOL_TIMEOUT_MS}`],
    // Under no contention the whole flow should be quick; a soft sanity bound.
    'http_req_duration{phase:under}': ['p(99)<2000'],
    // Every flow must produce well-formed tokens in every phase — saturation
    // may slow things down, it must not corrupt the contract.
    'checks{kind:contract}': ['rate>0.99'],
  },
};

function post(path, body, op) {
  const params = { ...PARAMS, tags: { ...PARAMS.tags, op } };
  const res = http.post(`${CORE_URL}${path}`, JSON.stringify(body), params);
  if (res.status >= 500) {
    untypedShed.add(1, { op });
  }
  return res;
}

export default function () {
  // 1. Create — an arbitrary userId, so no dataset is required.
  const created = post(
    '/recipe/session',
    {
      userId: `k6-concurrency-${__VU}-${__ITER}`,
      userDataInJWT: {},
      userDataInDatabase: {},
      enableAntiCsrf: false,
    },
    'create'
  );
  let session;
  try {
    session = created.json();
  } catch (e) {
    session = null;
  }
  const created_ok = check(
    session,
    { 'create returned access + refresh tokens': (s) => !!(s && s.accessToken && s.accessToken.token && s.refreshToken && s.refreshToken.token) },
    { kind: 'contract' }
  );
  if (!created_ok) {
    return;
  }

  // 2. Verify with checkDatabase: true. Without it, verification is pure JWT
  // signature checking and never touches the pool at all — which would make
  // this probe measure nothing.
  const verified = post(
    '/recipe/session/verify',
    {
      accessToken: session.accessToken.token,
      doAntiCsrfCheck: false,
      enableAntiCsrf: false,
      checkDatabase: true,
    },
    'verify'
  );
  check(
    verified,
    { 'verify returned OK': (r) => r.status === 200 && r.json('status') === 'OK' },
    { kind: 'contract' }
  );

  // 3. Refresh — the path production keeps starving on.
  const refreshed = post(
    '/recipe/session/refresh',
    { refreshToken: session.refreshToken.token, enableAntiCsrf: false },
    'refresh'
  );
  check(
    refreshed,
    { 'refresh rotated the session': (r) => r.status === 200 && r.json('status') === 'OK' },
    { kind: 'contract' }
  );
}

export function handleSummary(data) {
  const m = data.metrics;
  const g = (name, field) => (m[name] && m[name].values ? m[name].values[field] : undefined);
  const fmt = (v) => (v === undefined ? '-' : `${Math.round(v)}ms`);
  const lines = [
    `## Session concurrency probe (pool size ${POOL})`,
    '',
    `Hikari \`connectionTimeout\` is ${POOL_TIMEOUT_MS}ms — any request reaching it parked rather than queued.`,
    '',
    '| Metric | Value |',
    '|--------|-------|',
    `| refresh max | ${fmt(g('http_req_duration', 'max'))} |`,
    `| overall p95 | ${fmt(g('http_req_duration', 'p(95)'))} |`,
    `| request failure rate | ${((g('http_req_failed', 'rate') || 0) * 100).toFixed(2)}% |`,
    `| contract checks passed | ${((g('checks', 'rate') || 0) * 100).toFixed(2)}% |`,
    `| untyped 5xx (no shed path — informational) | ${g('untyped_shed_5xx', 'count') || 0} |`,
    '',
  ];
  return {
    'k6-session-concurrency-summary.json': JSON.stringify(data),
    'k6-session-concurrency-summary.md': lines.join('\n'),
    stdout: lines.join('\n') + '\n',
  };
}
