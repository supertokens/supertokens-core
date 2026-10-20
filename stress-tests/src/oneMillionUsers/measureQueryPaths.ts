import SuperTokens from 'supertokens-node';
import EmailPassword from 'supertokens-node/recipe/emailpassword';
import Passwordless from 'supertokens-node/recipe/passwordless';
import ThirdParty from 'supertokens-node/recipe/thirdparty';
import AccountLinking from 'supertokens-node/recipe/accountlinking';
import Multitenancy from 'supertokens-node/recipe/multitenancy';
import UserRoles from 'supertokens-node/recipe/userroles';

import { measureRepeated, measureTime, getCheckpoint, runStep } from '../common/utils';
import { generateBase32Secret, totpForCounter, currentCounter } from '../common/totp';

// Number of used TOTP codes to seed for the dedicated TOTP user before the
// measured verification (env-overridable to keep smoke runs fast). Each code is
// a distinct, self-minted valid code for a past time-step within the device
// skew window, so it lands as a distinct row in the used-codes table.
const TOTP_USED_CODES_TO_SEED = Number(process.env.STRESS_TEST_TOTP_USED_CODES ?? '3000') || 3000;

// Distinct third-party fixtures the repeated sign-in step rotates over, so the
// measurement is a lookup rather than a hot single row.
const TP_FIXTURE_POOL = 10;

const randomString = (len: number, chars = 'abcdefghijklmnopqrstuvwxyz'): string =>
  Array(len)
    .fill(0)
    .map(() => chars.charAt(Math.floor(Math.random() * chars.length)))
    .join('');

const randomEmail = (): string => `${randomString(48)}@stress.example.com`;
const randomPhone = (): string => `+1${randomString(10, '0123456789')}`;

// ---------------------------------------------------------------------------
// Raw core HTTP helper — used for paths not exposed by supertokens-node
// (active-user counts, EE usage stats, TOTP device import / verify, email
// verification by user id). The suite already talks to the core directly for
// bulk import, so this mirrors that convention.
// ---------------------------------------------------------------------------

let cachedCdiVersion: string | undefined;

const cmpSemVer = (a: string, b: string): number => {
  const pa = a.split('.').map((n) => parseInt(n, 10) || 0);
  const pb = b.split('.').map((n) => parseInt(n, 10) || 0);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] ?? 0) - (pb[i] ?? 0);
    if (d !== 0) return d;
  }
  return 0;
};

const getCdiVersion = async (deployment: any): Promise<string> => {
  if (cachedCdiVersion) return cachedCdiVersion;
  try {
    const res = await fetch(`${deployment.core_url}/apiversion`, {
      headers: { 'Api-Key': deployment.api_key },
    });
    const body: any = await res.json();
    const versions: string[] = Array.isArray(body?.versions) ? body.versions : [];
    cachedCdiVersion = versions.sort(cmpSemVer).pop();
  } catch (e) {
    console.warn(`    Could not fetch /apiversion: ${(e as Error).message}`);
  }
  return (cachedCdiVersion = cachedCdiVersion ?? '5.4');
};

const coreFetch = async (
  deployment: any,
  path: string,
  opts: { method?: string; body?: any; query?: Record<string, string> } = {}
): Promise<any> => {
  const cdiVersion = await getCdiVersion(deployment);
  const qs = opts.query ? '?' + new URLSearchParams(opts.query).toString() : '';
  const res = await fetch(`${deployment.core_url}${path}${qs}`, {
    method: opts.method ?? 'GET',
    headers: {
      'Content-Type': 'application/json',
      'Api-Key': deployment.api_key,
      'cdi-version': cdiVersion,
    },
    body: opts.body ? JSON.stringify(opts.body) : undefined,
  });
  const text = await res.text();
  try {
    return JSON.parse(text);
  } catch {
    return { _status: res.status, _raw: text };
  }
};

const warnIfNotOk = (label: string, result: { status?: string }): void => {
  if (result?.status !== undefined && result.status !== 'OK') {
    console.warn(`    ${label}: unexpected status ${result.status}`);
  }
};

// ---------------------------------------------------------------------------
// Fixture creation (small, deterministic; each pays the app-wide scan cost of
// the query being measured because the 1M-user dataset already exists).
// ---------------------------------------------------------------------------

const signUpEp = async (): Promise<{ recipeUserId: string; email: string }> => {
  const email = randomEmail();
  const res = await EmailPassword.signUp('public', email, 'password123');
  if (res.status !== 'OK') throw new Error(`EmailPassword.signUp failed: ${res.status}`);
  return { recipeUserId: res.recipeUserId.getAsString(), email };
};

const signUpPlessPhone = async (): Promise<{ recipeUserId: string; phoneNumber: string }> => {
  const phoneNumber = randomPhone();
  const res = await Passwordless.signInUp({ tenantId: 'public', phoneNumber });
  if (res.status !== 'OK') throw new Error(`Passwordless.signInUp failed: ${res.status}`);
  return { recipeUserId: res.recipeUserId.getAsString(), phoneNumber };
};

// Build a linked primary user out of an emailpassword account and a
// passwordless-phone account, returning both recipe user ids and the primary id.
const makeLinkedUser = async (): Promise<{
  primaryUserId: string;
  epRecipeUserId: string;
  phoneRecipeUserId: string;
  email: string;
  phoneNumber: string;
}> => {
  const ep = await signUpEp();
  const phone = await signUpPlessPhone();
  const primary = await AccountLinking.createPrimaryUser(
    SuperTokens.convertToRecipeUserId(ep.recipeUserId)
  );
  if (primary.status !== 'OK') throw new Error(`createPrimaryUser failed: ${primary.status}`);
  const linked = await AccountLinking.linkAccounts(
    SuperTokens.convertToRecipeUserId(phone.recipeUserId),
    ep.recipeUserId
  );
  if (linked.status !== 'OK') throw new Error(`linkAccounts failed: ${linked.status}`);
  return {
    primaryUserId: ep.recipeUserId,
    epRecipeUserId: ep.recipeUserId,
    phoneRecipeUserId: phone.recipeUserId,
    email: ep.email,
    phoneNumber: phone.phoneNumber,
  };
};

/**
 * Measure the scale-sensitive query paths flagged in the query-scaling survey
 * against the seeded 1M-user state. Read paths run over the whole dataset;
 * write/lookup paths use small dedicated fixtures created here, but still pay
 * the app-wide scan cost because the large dataset already exists.
 */
export const measureQueryPaths = async (deployment: any): Promise<void> => {
  console.log('\n\n8. Measuring scale-sensitive query paths');

  const extraTenantId = 'stressextra';

  // --- Dashboard-style user search (survey G1: leading-wildcard ILIKE search) ---
  // The three dashboard searches are repeated rather than sampled once: each is
  // well under a second, and the query is held identical across iterations
  // because here the query shape is the subject of the measurement.
  await runStep(() =>
    measureRepeated('Dashboard search by email prefix', async () => {
      await SuperTokens.getUsersNewestFirst({
        tenantId: 'public',
        limit: 100,
        query: { email: 'a' },
      });
    })
  );
  await runStep(() =>
    measureRepeated('Dashboard search by provider', async () => {
      await SuperTokens.getUsersNewestFirst({
        tenantId: 'public',
        limit: 100,
        query: { provider: 'google' },
      });
    })
  );
  await runStep(() =>
    measureRepeated('Dashboard search by email + provider', async () => {
      await SuperTokens.getUsersNewestFirst({
        tenantId: 'public',
        limit: 100,
        query: { email: 'a', provider: 'google' },
      });
    })
  );

  // --- Third-party sign-in for an existing user (survey A1 / T1a: lookup by
  // thirdPartyId + thirdPartyUserId with no value index) ---
  //
  // A pool of fixtures rather than one, because the measured call is a point
  // lookup: repeating it against a single row would measure a hot row rather
  // than the lookup path.
  await runStep(async () => {
    const fixtures: Array<{ id: string; email: string }> = [];
    for (let i = 0; i < TP_FIXTURE_POOL; i++) {
      const fixture = { id: randomString(32), email: randomEmail() };
      const created = await ThirdParty.manuallyCreateOrUpdateUser(
        'public',
        'google',
        fixture.id,
        fixture.email,
        true
      );
      warnIfNotOk('third-party fixture create', created);
      fixtures.push(fixture);
    }
    await measureRepeated('Third-party sign-in for existing user', async (i) => {
      const fixture = fixtures[Math.abs(i) % fixtures.length]!;
      const res = await ThirdParty.manuallyCreateOrUpdateUser(
        'public',
        'google',
        fixture.id,
        fixture.email,
        true
      );
      warnIfNotOk('third-party sign-in', res);
    });
  });

  // --- Email + phone update of a user in a linked group (survey A5 / P2:
  // update subqueries omit app_id -> whole-table scans) ---
  await runStep(async () => {
    const linked = await makeLinkedUser();
    await measureTime('Email update (linked user)', async () => {
      const res = await EmailPassword.updateEmailOrPassword({
        recipeUserId: SuperTokens.convertToRecipeUserId(linked.epRecipeUserId),
        email: randomEmail(),
      });
      warnIfNotOk('email update', res);
    });
    await measureTime('Phone update (linked user)', async () => {
      const res = await Passwordless.updateUser({
        recipeUserId: SuperTokens.convertToRecipeUserId(linked.phoneRecipeUserId),
        phoneNumber: randomPhone(),
      });
      warnIfNotOk('phone update', res);
    });

    // --- Tenant association / disassociation for a linked user (survey A2 / A3:
    // ruai.primary_user_id filter unindexed -> app-wide scan) ---
    const tenant = await Multitenancy.createOrUpdateTenant(extraTenantId, { firstFactors: null });
    warnIfNotOk('create extra tenant', tenant);
    await measureTime('Associate linked user to tenant', async () => {
      const res = await Multitenancy.associateUserToTenant(
        extraTenantId,
        SuperTokens.convertToRecipeUserId(linked.epRecipeUserId)
      );
      warnIfNotOk('associate to tenant', res);
    });
    await measureTime('Disassociate linked user from tenant', async () => {
      const res = await Multitenancy.disassociateUserFromTenant(
        extraTenantId,
        SuperTokens.convertToRecipeUserId(linked.epRecipeUserId)
      );
      warnIfNotOk('disassociate from tenant', res);
    });
  });

  // --- canLinkAccounts precheck (survey A6: row-constructor IN defeats PK) ---
  await runStep(async () => {
    const primary = await makeLinkedUser();
    const standalone = await signUpEp();
    // Read-only precheck, so it repeats against the same pair — the pair is
    // the point of the step (a row-constructor IN against the PK).
    await measureRepeated('canLinkAccounts precheck', async () => {
      const res = await AccountLinking.canLinkAccounts(
        SuperTokens.convertToRecipeUserId(standalone.recipeUserId),
        primary.primaryUserId
      );
      warnIfNotOk('canLinkAccounts', res);
    });
  });

  // --- Unlink account (survey A4: nested subqueries on unindexed
  // ruai.primary_user_id) ---
  await runStep(async () => {
    const linked = await makeLinkedUser();
    await measureTime('Unlink account', async () => {
      const res = await AccountLinking.unlinkAccount(
        SuperTokens.convertToRecipeUserId(linked.phoneRecipeUserId)
      );
      warnIfNotOk('unlink', res);
    });
  });

  // --- Full delete of a linked user (survey A4: unlink/delete reservation
  // cleanup) ---
  await runStep(async () => {
    const linked = await makeLinkedUser();
    await measureTime('Delete user (full, linked)', async () => {
      await SuperTokens.deleteUser(linked.primaryUserId, true);
    });
  });

  // --- Active-users counts (survey U3 / U4: MAU aggregate). since=0 counts all
  // active users; a recent window exercises the index-range variant. ---
  await runStep(() =>
    measureRepeated('Active users count', async () => {
      const res = await coreFetch(deployment, '/users/count/active', { query: { since: '0' } });
      warnIfNotOk('active users count (all)', res);
    })
  );
  await runStep(() =>
    measureRepeated('Active users count (with more-than-one-login-method window)', async () => {
      const since = String(Date.now() - 30 * 24 * 60 * 60 * 1000);
      const res = await coreFetch(deployment, '/users/count/active', { query: { since } });
      warnIfNotOk('active users count (30d)', res);
    })
  );

  // --- EE usage-stats aggregate (survey G5 usesAccountLinking, U3/U4 MAU by
  // day, K1 tenant counts, O2 oauth counts) — the "dashboard analytics"
  // aggregate the managed dashboard shows. Needs the EE license (set earlier). ---
  await runStep(() =>
    measureTime('Feature-flag usage stats aggregate', async () => {
      await coreFetch(deployment, '/ee/featureflag');
    })
  );

  // --- List users for a role assigned to a large share of users, then delete a
  // large role (survey R1: unpaginated materialized list; unbounded delete).
  // Every bulk-imported user carries role1 and role2. ---
  await runStep(() =>
    // The step that flapped at 21.6x against a bound of 15 on one run and
    // passed on the next: exactly the noise a repeated mean is meant to settle.
    measureRepeated('List users for role (large share)', async () => {
      const res = await UserRoles.getUsersThatHaveRole('public', 'role1');
      warnIfNotOk('getUsersThatHaveRole', res);
    })
  );
  // Deleting role2 (assigned to every bulk-imported user) is globally
  // destructive and cannot be repeated cleanly, so it runs only on the large
  // pass (never at the 100k checkpoint). It therefore has no two-size ratio.
  if (getCheckpoint() !== 'small') {
    await runStep(() =>
      measureTime('Delete role (large share)', async () => {
        const res = await UserRoles.deleteRole('role2');
        warnIfNotOk('deleteRole', res);
      })
    );
  }

  // --- TOTP verify for a user with many accumulated used codes (survey TO1:
  // getAllUsedCodesDescOrder has no LIMIT). We import a device with a known
  // secret and a large skew, then mint distinct valid codes for past time-steps
  // to seed the used-codes table before the measured verification. ---
  await runStep(async () => {
    const totpUser = await signUpEp();
    const secret = generateBase32Secret();
    const period = 1;
    // skew must cover every past counter we seed so each code validates.
    const skew = Math.max(TOTP_USED_CODES_TO_SEED + 5, 5);
    const importRes = await coreFetch(deployment, '/recipe/totp/device/import', {
      method: 'POST',
      body: {
        userId: totpUser.recipeUserId,
        deviceName: 'stress',
        skew,
        period,
        secretKey: secret,
      },
    });
    warnIfNotOk('totp device import', importRes);

    const base = currentCounter(period);
    let seeded = 0;
    for (let k = 1; k <= TOTP_USED_CODES_TO_SEED; k++) {
      const code = totpForCounter(secret, base - k);
      const res = await coreFetch(deployment, '/recipe/totp/verify', {
        method: 'POST',
        body: { userId: totpUser.recipeUserId, totp: code },
      });
      if (res?.status === 'OK') seeded++;
    }
    console.log(`    Seeded ${seeded}/${TOTP_USED_CODES_TO_SEED} used TOTP codes`);

    await measureTime('TOTP verify (user with many used codes)', async () => {
      const code = totpForCounter(secret, base);
      const res = await coreFetch(deployment, '/recipe/totp/verify', {
        method: 'POST',
        body: { userId: totpUser.recipeUserId, totp: code },
      });
      warnIfNotOk('totp verify', res);
    });
  });

  // --- Email-verification status update + delete for a user with a userid
  // mapping (survey E1: emailverification by-user_id ops with no (app_id,
  // user_id) index; delete path). ---
  await runStep(async () => {
    const user = await signUpEp();
    const externalId = `ext-${randomString(24)}`;
    const mapping = await SuperTokens.createUserIdMapping({
      superTokensUserId: user.recipeUserId,
      externalUserId: externalId,
    });
    warnIfNotOk('create userid mapping', mapping);

    await measureTime('Email-verification status update (mapped user)', async () => {
      const tokenRes = await coreFetch(deployment, '/recipe/user/email/verify/token', {
        method: 'POST',
        body: { userId: externalId, email: user.email },
      });
      if (tokenRes?.status === 'OK' && tokenRes.token) {
        const verifyRes = await coreFetch(deployment, '/recipe/user/email/verify', {
          method: 'POST',
          body: { method: 'token', token: tokenRes.token },
        });
        warnIfNotOk('email verify', verifyRes);
      } else {
        warnIfNotOk('email verify token', tokenRes);
      }
    });

    await measureTime('Delete user with userid mapping', async () => {
      await SuperTokens.deleteUser(externalId, true);
    });
  });
};
