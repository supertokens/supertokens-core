import SuperTokens from 'supertokens-node';

import { measureTime, setCheckpoint, CheckpointSize } from '../common/utils';
import { measureQueryPaths } from './measureQueryPaths';

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
 */
export const runReadPaths = async (deployment: any, size: CheckpointSize): Promise<void> => {
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

    for (const order of ['newest', 'oldest'] as const) {
      await measureTime(`Pagination first page (${order} first)`, async () => {
        await getPage(order);
      });

      await measureTime(`Pagination full walk (${order} first)`, async () => {
        let lmCount = 0;
        let userCount = 0;
        let paginationToken: string | undefined;
        while (true) {
          const result = await getPage(order, paginationToken);
          for (const user of result.users) {
            userCount++;
            lmCount += user.loginMethods.length;
          }
          paginationToken = result.nextPaginationToken;
          if (result.nextPaginationToken === undefined) break;
        }
        console.log(`    (${order} first) users=${userCount}, loginMethods=${lmCount}`);
      });
    }

    // 7. Count users — measure both the app-wide (all-tenants) and the
    // per-tenant (public) count variants.
    console.log('\n7. Counting users');
    await measureTime('User count (all tenants)', async () => {
      const total = await SuperTokens.getUserCount();
      console.log(`    Users count (all tenants): ${total}`);
    });
    await measureTime('User count (tenant: public)', async () => {
      const total = await SuperTokens.getUserCount(undefined, 'public');
      console.log(`    Users count (public tenant): ${total}`);
    });

    // 8. Measure the remaining scale-sensitive query paths (dashboard search,
    // third-party sign-in, linked-user updates, tenant assoc, unlink/delete,
    // analytics counts, role listing/delete, TOTP verify, email verification).
    await measureQueryPaths(deployment);
  } finally {
    // Always clear the checkpoint so any measurement outside a pass (or a later
    // pass) is routed correctly.
    setCheckpoint(undefined);
  }
};
