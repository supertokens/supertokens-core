import {
  createStInstanceForTest,
  deleteStInstance,
  setupLicense,
  StatsCollector,
} from '../common/utils';

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

    // 6. List all users
    console.log('\n\n6. Listing all users');
    let userCount = 0;
    let paginationToken: string | undefined;
    while (true) {
      const result = await SuperTokens.getUsersNewestFirst({
        tenantId: 'public',
        paginationToken,
      });

      for (const user of result.users) {
        userCount++;
      }

      paginationToken = result.nextPaginationToken;

      if (result.nextPaginationToken === undefined) break;
    }
    console.log(`Users count: ${userCount}`);

    // 7. Count users
    console.log('\n\n7. Count users');
    const total = await SuperTokens.getUserCount();
    console.log(`Users count: ${total}`);

    // Write stats to file
    StatsCollector.getInstance().writeToFile();
    console.log('\nStats written to stats.json');
  } catch (error) {
    console.error('An error occurred during execution:', error);
    throw error;
  } finally {
    await deleteStInstance(deployment.deployment_id);
  }
}

main();
