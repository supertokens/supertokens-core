import SuperTokens from 'supertokens-node';

import { measureTime, runStep, setCheckpoint, CheckpointSize } from '../common/utils';
import { measureQueryPaths } from './measureQueryPaths';
import { measureOAuthPaths, OAuthStore } from './oauthPaths';
import { measureSessionPaths } from './sessionPaths';
import { walkAllUsers } from './pagination';

/**
 * Run the full set of measured read-path steps against the current dataset,
 * tagging every measurement with the given checkpoint size. Called twice by the
 * two-size run: once at the ~100k checkpoint ('small') and once at the full 1M
 * ('large'). The ambient checkpoint (set here) routes each measureTime call to
 * the RatioCollector — and, for the large pass, also to the StatsCollector that
 * feeds the existing summary/budget table.
 *
 * The steps here are all repeatable across passes: read-only queries, or writes
 * that create their own fresh fixtures each pass. The single globally
 * destructive step (deleting a role assigned to a large share of users) runs
 * only on the large pass — see the guard in measureQueryPaths.
 *
 * Every measured step is wrapped in runStep so a guard violation, timeout or
 * thrown error records that step as failed and the pass moves on to the next
 * step instead of aborting the whole run (issue #1346, collect-and-continue).
 * The end-of-run throwIf* stages then fail the job listing every failed step.
 */
export const runReadPaths = async (
  deployment: any,
  size: CheckpointSize,
  oauthStore?: OAuthStore
): Promise<void> => {
  console.log(`\n\n===== Read-path measurement pass: ${size} =====`);
  setCheckpoint(size);
  try {
    // 6. List all users — measure the first page and the full pagination walk
    // separately, in both newest-first and oldest-first order (the paginated
    // read path scales with total user count).
    console.log('\n6. Listing all users (paginated)');
    const getPage = (order: 'newest' | 'oldest', paginationToken?: string) =>
      order === 'newest'
        ? SuperTokens.getUsersNewestFirst({ tenantId: 'public', paginationToken })
        : SuperTokens.getUsersOldestFirst({ tenantId: 'public', paginationToken });

    // Population the full walk is expected to visit — the public tenant's user
    // count. Captured outside any measureTime so it doesn't affect the measured
    // walk durations, and reused as the completeness target for both walks (the
    // walk and the count endpoint must agree on how many users exist). Isolated
    // like the measured steps: if it fails, the walks still run (with the
    // completeness guard disabled, expectedUsers = 0) rather than aborting.
    let expectedUsers = 0;
    await runStep(async () => {
      expectedUsers = await SuperTokens.getUserCount(undefined, 'public');
      console.log(
        `    Expected users at ${size} checkpoint (public tenant count): ${expectedUsers}`
      );
    });

    for (const order of ['newest', 'oldest'] as const) {
      await runStep(() =>
        measureTime(`Pagination first page (${order} first)`, async () => {
          await getPage(order);
        })
      );

      await runStep(() =>
        measureTime(`Pagination full walk (${order} first)`, async (signal) => {
          await walkAllUsers(
            `${order} first`,
            (token) => getPage(order, token),
            expectedUsers,
            signal
          );
        })
      );
    }

    // 7. Count users — measure both the app-wide (all-tenants) and the
    // per-tenant (public) count variants.
    console.log('\n7. Counting users');
    await runStep(() =>
      measureTime('User count (all tenants)', async () => {
        const total = await SuperTokens.getUserCount();
        console.log(`    Users count (all tenants): ${total}`);
      })
    );
    await runStep(() =>
      measureTime('User count (tenant: public)', async () => {
        const total = await SuperTokens.getUserCount(undefined, 'public');
        console.log(`    Users count (public tenant): ${total}`);
      })
    );

    // 8. Measure the remaining scale-sensitive query paths (dashboard search,
    // third-party sign-in, linked-user updates, tenant assoc, unlink/delete,
    // analytics counts, role listing/delete, TOTP verify, email verification).
    await measureQueryPaths(deployment);

    // 9. Measure the OAuth-dependent paths (M2M issuance, introspection, revoke
    // by handle / client, plus the large-pass-only burst-accuracy assertion and
    // cleanup sweep) against the seeded OAuth data.
    if (oauthStore) {
      await measureOAuthPaths(deployment, oauthStore);
    }

    // 10. Measure the session read paths (verify, refresh, handle lookup on
    // both the hit and the miss path) against the current dataset.
    await measureSessionPaths();
  } finally {
    // Always clear the checkpoint so any measurement outside a pass (or a later
    // pass) is routed correctly.
    setCheckpoint(undefined);
  }
};
