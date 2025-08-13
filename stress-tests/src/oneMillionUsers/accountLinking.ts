import SuperTokens from 'supertokens-node';
import AccountLinking from 'supertokens-node/recipe/accountlinking';
import { measureTime, workInBatches } from '../common/utils';

export const doAccountLinking = async (
  users: { recipeUserId: string; email?: string; phoneNumber?: string }[][]
) => {
  console.log('\n\n2. Linking accounts');

  await measureTime('Linking accounts', async () => {
    await workInBatches(users.length, 8, async (idx) => {
      const userSet = users[idx]!;
      await AccountLinking.createPrimaryUser(
        SuperTokens.convertToRecipeUserId(userSet[0].recipeUserId)
      );
      for (const user of userSet.slice(1)) {
        await AccountLinking.linkAccounts(
          SuperTokens.convertToRecipeUserId(user.recipeUserId),
          userSet[0].recipeUserId
        );
      }
    });
  });
};