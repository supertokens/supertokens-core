import SuperTokens from 'supertokens-node';
import UserRoles from 'supertokens-node/recipe/userroles';
import { measureTime, workInBatches } from '../common/utils';

export const addRoles = async (
  users: { recipeUserId: string; email?: string; phoneNumber?: string }[]
) => {
  console.log('\n\n4. Adding roles');

  await measureTime('Adding roles', async () => {
    await UserRoles.createNewRoleOrAddPermissions('admin', ['p1', 'p2']);

    await workInBatches(users.length, 8, async (idx) => {
      const user = users[idx]!;
      await UserRoles.addRoleToUser('public', user.recipeUserId, 'admin');
    });
  });
};
