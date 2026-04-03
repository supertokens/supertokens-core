import fs from 'fs';
import { formatTime, measureTime } from '../common/utils';

export const importMillionUsers = async (deployment: any) => {
  console.log('\n\n0. Importing one million users');

  // Create roles
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

  await measureTime('Loading users for bulk import', async () => {
    const files = fs.readdirSync('users').filter((file) => file.endsWith('.json'));
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
  });

  await measureTime('Waiting for users to be imported', async () => {
    let lastCount = 1000000;
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
        console.log(
          `    Progress: Time=${formatTime(Date.now() - st)}, UsersLeft=${count.count}, Rate=${(((lastCount - count.count) * 1000) / (Date.now() - lastTime)).toFixed(1)}, Failed=${failedCount.count}`
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
  });
};
