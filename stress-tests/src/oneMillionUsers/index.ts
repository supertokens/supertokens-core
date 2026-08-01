import {
  createStInstanceForTest,
  deleteStInstance,
  setupLicense,
  StatsCollector,
  FailureTracker,
  RatioCollector,
  measureTime,
} from '../common/utils';
import { runReadPaths } from './readPaths';
import { capturePgStats, PgStatsCollector } from './pgStatStatements';

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
import {
  createBulkImportRoles,
  listUserFiles,
  postBulkImportFiles,
  waitForBulkImport,
} from './importMillionUsers';

// How many generated user-JSON files (10k users each) make up the first "small"
// checkpoint tranche. Default 10 files ≈ 100k users; the remainder is imported
// to reach 1M. Env-overridable so smoke runs can shrink it. A meaningful ratio
// needs strictly more total files than this.
const SMALL_CHECKPOINT_FILES = Number(process.env.STRESS_TEST_SMALL_CHECKPOINT_FILES ?? '10') || 10;

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

    // 0. Import users in two tranches so the read-path steps can be measured at
    // two dataset sizes (see runReadPaths / RatioCollector). First the ~100k
    // checkpoint, then the remainder to 1M.
    console.log('\n\n0. Importing users (two-size run)');
    await createBulkImportRoles(deployment);
    const allFiles = listUserFiles();
    const smallFileCount = Math.max(1, Math.min(SMALL_CHECKPOINT_FILES, allFiles.length));
    const smallFiles = allFiles.slice(0, smallFileCount);
    const restFiles = allFiles.slice(smallFileCount);
    console.log(
      `    ${allFiles.length} file(s) total; checkpoint tranche = ${smallFiles.length}, remainder = ${restFiles.length}`
    );
    if (restFiles.length === 0) {
      console.warn(
        '    WARNING: no files left for the 1M tranche — scaling ratios will be ~1 (both passes at the same size).'
      );
    }

    await measureTime('Loading users for bulk import (100k checkpoint)', () =>
      postBulkImportFiles(deployment, smallFiles)
    );
    await measureTime('Waiting for import (100k checkpoint)', () => waitForBulkImport(deployment));

    // Small read-path pass at the ~100k checkpoint (records into the ratio
    // harness only; does not touch the 1M summary/budget table).
    await runReadPaths(deployment, 'small');

    // Grow the dataset to the full 1M.
    await measureTime('Loading users for bulk import (remaining to 1M)', () =>
      postBulkImportFiles(deployment, restFiles)
    );
    await measureTime('Waiting for import (remaining to 1M)', () => waitForBulkImport(deployment));

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

    // Seeding is done: snapshot the ingest query profile from
    // pg_stat_statements, then reset the counters so the read/query phase below
    // is measured from a clean slate.
    await capturePgStats('seed', { reset: true });

    // Large read-path pass at the full 1M. This is the pass whose measurements
    // feed the existing summary/budget table (steps 6, 7 and 8), and it also
    // supplies the "large" side of every two-size scaling ratio.
    await runReadPaths(deployment, 'large');

    // Snapshot the steady-state read/query query profile now that the measured
    // paths above have run against the reset counters.
    await capturePgStats('read');

    // Write stats to file (duration measurements enriched with the two-size
    // scaling ratios + both pg_stat_statements phase snapshots), then the
    // human-readable pg summary for the step summary.
    StatsCollector.getInstance().writeToFile({
      pgStatStatements: PgStatsCollector.getInstance().toJSON(),
      scalingRatios: RatioCollector.getInstance().toJSON(),
    });
    PgStatsCollector.getInstance().writeSummaryFile();
    console.log('\nStats written to stats.json and pg-stats-summary.md');

    // Fail the run if any seeding step produced non-OK results, so silently
    // errored steps don't leave the run looking green with untrustworthy
    // measurements.
    FailureTracker.getInstance().throwIfAnyFailures();

    // Fail the run if any measured step blew past its duration budget (an
    // order-of-magnitude query regression). Runs last so every step still
    // appears in stats.json and the summary table.
    StatsCollector.getInstance().throwIfOverBudget();

    // Fail the run if any read-phase statement spilled more than the temp-block
    // threshold (a hardware-independent hash/sort-spill regression signal).
    PgStatsCollector.getInstance().throwIfTempSpillExceeded();

    // Fail the run if any step's cost grew faster than its scaling class allows
    // between the 100k checkpoint and the full 1M (the superlinear-growth
    // regression this suite exists to catch). Runs last, after all stats are
    // written, so the small/large/ratio columns are visible regardless.
    RatioCollector.getInstance().throwIfRatioExceeded();
  } catch (error) {
    console.error('An error occurred during execution:', error);
    throw error;
  } finally {
    await deleteStInstance(deployment.deployment_id);
  }
}

main();
