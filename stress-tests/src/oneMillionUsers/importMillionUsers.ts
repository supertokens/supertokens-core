import fs from 'fs';
import { formatTime } from '../common/utils';

// Create the two roles every bulk-imported user carries. Split out from the
// import itself so the caller can create roles once and then import users in
// tranches (see the two-size run in index.ts).
export const createBulkImportRoles = async (deployment: any) => {
  await fetch(`${deployment.core_url}/recipe/role`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'Api-Key': deployment.api_key,
    },
    body: JSON.stringify({
      role: 'role1',
      permissions: ['p1', 'p2'],
    }),
  });

  await fetch(`${deployment.core_url}/recipe/role`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'Api-Key': deployment.api_key,
    },
    body: JSON.stringify({
      role: 'role2',
      permissions: ['p3', 'p2'],
    }),
  });
};

// The generated user JSON files, sorted so tranche boundaries are deterministic.
export const listUserFiles = (): string[] =>
  fs
    .readdirSync('users')
    .filter((file) => file.endsWith('.json'))
    .sort();

// POST the given generated user JSON files to the bulk-import endpoint. Returns
// immediately after enqueuing; use waitForBulkImport to drain the queue.
export const postBulkImportFiles = async (deployment: any, files: string[]) => {
  for (const file of files) {
    const fileData = fs.readFileSync(`users/${file}`, 'utf8');
    const data = JSON.parse(fileData);

    await fetch(`${deployment.core_url}/bulk-import/users`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Api-Key': deployment.api_key,
      },
      body: JSON.stringify(data),
    });
  }
};

// Poll the bulk-import queue until every enqueued (non-failed) user has been
// processed. Tranche-agnostic: it drains whatever is currently queued, so it
// works for both the 100k checkpoint tranche and the remainder to 1M.
export const waitForBulkImport = async (deployment: any) => {
  let lastCount = Number.POSITIVE_INFINITY;
  let st = Date.now();
  let lastTime = st;
  while (true) {
    await new Promise((resolve) => setTimeout(resolve, 5000));
    let response;
    try {
      response = await fetch(`${deployment.core_url}/bulk-import/users/count`, {
        headers: {
          'Api-Key': deployment.api_key,
        },
      });
    } catch (error) {
      // Ignoring any error from this fetch request
      console.log('    Error fetching user count, continuing anyway...');
      response = { json: async () => ({ count: lastCount }) };
    }

    let failedCountResponse;
    try {
      failedCountResponse = await fetch(
        `${deployment.core_url}/bulk-import/users/count?status=FAILED`,
        {
          headers: {
            'Api-Key': deployment.api_key,
          },
        }
      );

      const count: any = await response.json();
      const failedCount: any = await failedCountResponse.json();
      const rate =
        lastCount === Number.POSITIVE_INFINITY
          ? 0
          : ((lastCount - count.count) * 1000) / (Date.now() - lastTime);
      console.log(
        `    Progress: Time=${formatTime(Date.now() - st)}, UsersLeft=${count.count}, Rate=${rate.toFixed(1)}, Failed=${failedCount.count}`
      );

      if (count.count - failedCount.count === 0) {
        break;
      }

      lastCount = count.count;
      lastTime = Date.now();
    } catch (error) {
      // Ignoring any error from this fetch request
      console.log('    Error fetching user count, continuing anyway...');
      failedCountResponse = { json: async () => ({ count: 0 }) };
    }
  }
};
