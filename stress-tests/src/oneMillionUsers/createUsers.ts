import EmailPassword from 'supertokens-node/recipe/emailpassword';
import Passwordless from 'supertokens-node/recipe/passwordless';
import ThirdParty from 'supertokens-node/recipe/thirdparty';

import { workInBatches, measureTime } from '../common/utils';

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
    // expect(createdUser.status).toBe("OK");
    if (createdUser.status === 'OK') {
      return {
        recipeUserId: createdUser.user.id,
        email: email,
      };
    }
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
    // expect(createdUser.status).toBe("OK");
    if (createdUser.status === 'OK') {
      return {
        recipeUserId: createdUser.user.id,
        email,
      };
    }
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
    // expect(createdUser.status).toBe("OK");
    if (createdUser.status === 'OK') {
      return {
        recipeUserId: createdUser.user.id,
        phoneNumber,
      };
    }
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
    // expect(createdUser.status).toBe("OK");
    if (createdUser.status === 'OK') {
      return {
        recipeUserId: createdUser.user.id,
        email,
      };
    }
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
