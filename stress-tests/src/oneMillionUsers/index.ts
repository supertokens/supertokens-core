import {
  createStInstanceForTest,
  deleteStInstance,
  setupLicense,
  StatsCollector,
  FailureTracker,
  measureTime,
} from '../common/utils';
import { measureQueryPaths } from './measureQueryPaths';

import SuperTokens from 'supertokens-node';
import EmailPassword from 'supertokens-node/recipe/emailpassword';
import Passwordless from 'supertokens-node/recipe/passwordless';
import ThirdParty from 'supertokens-node/recipe/thirdparty';
import UserRoles from 'supertokens-node/recipe/userroles';
import Session from 'supertokens-node/recipe/session';

import { createUsers } from './createUsers';
import { doAccountLinking } from './accountLinking';
import { createUserIdMappings } from './createUserIdMappings';
import { addRoles } from './addRoles';
import { createSessions } from './createSessions';
import { importMillionUsers } from './importMillionUsers';

function stInit(connectionURI: string, apiKey: string) {
  SuperTokens.init({
    appInfo: {
      appName: 'SuperTokens',
      apiDomain: 'http://localhost:3001',
      websiteDomain: 'http://localhost:3000',
      apiBasePath: '/auth',
      websiteBasePath: '/auth',
    },
    supertokens: {
      connectionURI: connectionURI,
      apiKey: apiKey,
      networkInterceptor: (request) => {
        return request;
      },
    },
    recipeList: [
      EmailPassword.init(),
      Passwordless.init({
        contactMethod: 'EMAIL_OR_PHONE',
        flowType: 'USER_INPUT_CODE',
      }),
      ThirdParty.init({
        signInAndUpFeature: {
          providers: [
            {
              config: { thirdPartyId: 'google' },
            },
            {
              config: { thirdPartyId: 'facebook' },
            },
          ],
        },
      }),
      UserRoles.init(),
      Session.init(),
    ],
  });
}

async function main() {
  const deployment = await createStInstanceForTest();
  console.log(`Deployment created: ${deployment.core_url}`);
  try {
    stInit(deployment.core_url, deployment.api_key);
    await setupLicense(deployment.core_url, deployment.api_key);
    // 0. Import one million users
    await importMillionUsers(deployment);

    // 1. Create one million users
    const users = await createUsers();

    // Randomly create groups of users for linking
    const allUsers: ({ recipeUserId: string; email?: string; phoneNumber?: string } | undefined)[] =
      [
        ...users.epUsers,
        ...users.plessEmailUsers,
        ...users.plessPhoneUsers,
        ...users.tpUsers1,
        ...users.tpUsers2,
      ];
    const usersToLink: { recipeUserId: string; email?: string; phoneNumber?: string }[][] = [];

    while (allUsers.length > 0) {
      const userSet: { recipeUserId: string; email?: string; phoneNumber?: string }[] = [];
      const numAccounts = Math.min(Math.floor(Math.random() * 5 + 1), allUsers.length);
      for (let i = 0; i < numAccounts; i++) {
        const randomIndex = Math.floor(Math.random() * allUsers.length);
        userSet.push(allUsers[randomIndex]!);
        allUsers.splice(randomIndex, 1);
      }
      usersToLink.push(userSet);
    }

    // 2. Link accounts
    await doAccountLinking(usersToLink);

    // 3. Create user id mappings
    const allUsersForMapping = [
      ...users.epUsers,
      ...users.plessEmailUsers,
      ...users.plessPhoneUsers,
      ...users.tpUsers1,
      ...users.tpUsers2,
    ].filter((user) => user !== undefined) as {
      recipeUserId: string;
      email?: string;
      phoneNumber?: string;
    }[];
    await createUserIdMappings(allUsersForMapping);

    // 4. Add roles
    await addRoles(allUsersForMapping);

    // 5. Create sessions
    await createSessions(allUsersForMapping);

    // 6. List all users — measure the first page and the full pagination walk
    // separately, in both newest-first and oldest-first order (the paginated
    // read path scales with total user count).
    console.log('\n\n6. Listing all users (paginated)');
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
    console.log('\n\n7. Counting users');
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

    // Write stats to file
    StatsCollector.getInstance().writeToFile();
    console.log('\nStats written to stats.json');

    // Fail the run if any seeding step produced non-OK results, so silently
    // errored steps don't leave the run looking green with untrustworthy
    // measurements.
    FailureTracker.getInstance().throwIfAnyFailures();

    // Fail the run if any measured step blew past its duration budget (an
    // order-of-magnitude query regression). Runs last so every step still
    // appears in stats.json and the summary table.
    StatsCollector.getInstance().throwIfOverBudget();
  } catch (error) {
    console.error('An error occurred during execution:', error);
    throw error;
  } finally {
    await deleteStInstance(deployment.deployment_id);
  }
}

main();
