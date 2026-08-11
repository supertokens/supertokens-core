import EmailPassword from 'supertokens-node/recipe/emailpassword';
import Passwordless from 'supertokens-node/recipe/passwordless';
import ThirdParty from 'supertokens-node/recipe/thirdparty';

import { workInBatches, measureTime, FailureTracker } from '../common/utils';

const TOTAL_USERS = 10000;

const createEmailPasswordUsers = async () => {
  console.log(`  Creating EmailPassword users...`);

  return await workInBatches(Math.floor(TOTAL_USERS / 5), 4, async (idx) => {
    const email =
      Array(64)
        .fill(0)
        .map(() => String.fromCharCode(97 + Math.floor(Math.random() * 26)))
        .join('') + '@example.com';
    const createdUser = await EmailPassword.signUp('public', email, 'password');
    if (createdUser.status === 'OK') {
      return {
        recipeUserId: createdUser.recipeUserId.getAsString(),
        email: email,
      };
    }
    FailureTracker.getInstance().recordFailure('EmailPassword users creation', createdUser.status);
  });
};

const createPasswordlessUsersWithEmail = async () => {
  console.log(`  Creating Passwordless users (with email)...`);
  return await workInBatches(Math.floor(TOTAL_USERS / 5), 4, async (idx) => {
    const email =
      Array(64)
        .fill(0)
        .map(() => String.fromCharCode(97 + Math.floor(Math.random() * 26)))
        .join('') + '@example.com';
    const createdUser = await Passwordless.signInUp({
      tenantId: 'public',
      email,
    });
    if (createdUser.status === 'OK') {
      return {
        recipeUserId: createdUser.recipeUserId.getAsString(),
        email,
      };
    }
    FailureTracker.getInstance().recordFailure(
      'Passwordless users (with email) creation',
      createdUser.status
    );
  });
};

const createPasswordlessUsersWithPhone = async () => {
  console.log(`  Creating Passwordless users (with phone)...`);
  return await workInBatches(Math.floor(TOTAL_USERS / 5), 4, async (idx) => {
    const phoneNumber = `+1${Math.floor(Math.random() * 10000000000)}`;
    const createdUser = await Passwordless.signInUp({
      tenantId: 'public',
      phoneNumber,
    });
    if (createdUser.status === 'OK') {
      return {
        recipeUserId: createdUser.recipeUserId.getAsString(),
        phoneNumber,
      };
    }
    FailureTracker.getInstance().recordFailure(
      'Passwordless users (with phone) creation',
      createdUser.status
    );
  });
};

const createThirdPartyUsers = async (thirdPartyId: string) => {
  console.log(`  Creating ThirdParty (${thirdPartyId}) users...`);
  return await workInBatches(Math.floor(TOTAL_USERS / 5), 4, async (idx) => {
    const email =
      Array(64)
        .fill(0)
        .map(() => String.fromCharCode(97 + Math.floor(Math.random() * 26)))
        .join('') + '@example.com';
    const tpUserId = Array(64)
      .fill(0)
      .map(() => String.fromCharCode(97 + Math.floor(Math.random() * 26)))
      .join('');
    const createdUser = await ThirdParty.manuallyCreateOrUpdateUser(
      'public',
      thirdPartyId,
      tpUserId,
      email,
      true
    );
    if (createdUser.status === 'OK') {
      return {
        recipeUserId: createdUser.recipeUserId.getAsString(),
        email,
      };
    }
    FailureTracker.getInstance().recordFailure(
      `ThirdParty users (${thirdPartyId}) creation`,
      createdUser.status
    );
  });
};

export const createUsers = async () => {
  console.log('\n\n1. Create one million users');

  const epUsers = await measureTime('Emailpassword users creation', createEmailPasswordUsers);

  const plessEmailUsers = await measureTime(
    'Passwordless users (with email) creation',
    createPasswordlessUsersWithEmail
  );

  const plessPhoneUsers = await measureTime(
    'Passwordless users (with phone) creation',
    createPasswordlessUsersWithPhone
  );

  const tpUsers1 = await measureTime('ThirdParty users (google) creation', () =>
    createThirdPartyUsers('google')
  );

  const tpUsers2 = await measureTime('ThirdParty users (facebook) creation', () =>
    createThirdPartyUsers('facebook')
  );

  return {
    epUsers,
    plessEmailUsers,
    plessPhoneUsers,
    tpUsers1,
    tpUsers2,
  };
};
