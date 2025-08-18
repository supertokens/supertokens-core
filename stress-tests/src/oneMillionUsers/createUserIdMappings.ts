import { measureTime, workInBatches } from '../common/utils';
import SuperTokens from 'supertokens-node';

export const createUserIdMappings = async (
  users: { recipeUserId: string; email?: string; phoneNumber?: string }[]
) => {
  console.log('\n\n3. Create user id mappings');

  await measureTime('Create user id mappings', async () => {
    await workInBatches(users.length, 8, async (idx) => {
      const user = users[idx]!;
      if (Math.random() < 0.5) {
        const newUserId = Array(64)
          .fill(0)
          .map(() => String.fromCharCode(97 + Math.floor(Math.random() * 26)))
          .join('');
        await SuperTokens.createUserIdMapping({
          superTokensUserId: user.recipeUserId,
          externalUserId: newUserId,
        });
        user.recipeUserId = newUserId;
      }
    });
  });
};
