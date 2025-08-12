import SuperTokens from 'supertokens-node';
import Session from 'supertokens-node/recipe/session';
import { measureTime, workInBatches } from '../common/utils';

export const createSessions = async (
  users: { recipeUserId: string; email?: string; phoneNumber?: string }[]
) => {
  console.log('\n\n5. Creating sessions');

  await measureTime('Creating sessions', async () => {
    await workInBatches(users.length, 8, async (idx) => {
      const user = users[idx]!;
      await Session.createNewSessionWithoutRequestResponse(
        'public',
        SuperTokens.convertToRecipeUserId(user.recipeUserId),
      );
    });
  });
};
