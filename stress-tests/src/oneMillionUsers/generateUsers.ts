import * as fs from 'fs';
import { v4 as uuidv4 } from 'uuid';

const USERS_TO_GENERATE = 1000000;
const USERS_PER_JSON = 10000;

const n = Math.floor(USERS_TO_GENERATE / USERS_PER_JSON);

const generatedEmails = new Set<string>();
const generatedPhoneNumbers = new Set<string>();
const generatedUserIds = new Set<string>();

interface LoginMethod {
  tenantIds: string[];
  email: string;
  recipeId: string;
  passwordHash?: string;
  hashingAlgorithm?: string;
  thirdPartyId?: string;
  thirdPartyUserId?: string;
  phoneNumber?: string;
  isVerified: boolean;
  isPrimary: boolean;
  timeJoinedInMSSinceEpoch: number;
}

interface User {
  externalUserId: string;
  userRoles: Array<{
    role: string;
    tenantIds: string[];
  }>;
  loginMethods: LoginMethod[];
}

function createEmailLoginMethod(email: string, tenantIds: string[]): LoginMethod {
  return {
    tenantIds,
    email,
    recipeId: 'emailpassword',
    passwordHash: '$argon2d$v=19$m=12,t=3,p=1$aGI4enNvMmd0Zm0wMDAwMA$r6p7qbr6HD+8CD7sBi4HVw',
    hashingAlgorithm: 'argon2',
    isVerified: true,
    isPrimary: false,
    timeJoinedInMSSinceEpoch:
      Math.floor(Math.random() * (Date.now() - 3 * 365 * 24 * 60 * 60 * 1000)) +
      3 * 365 * 24 * 60 * 60 * 1000,
  };
}

function createThirdPartyLoginMethod(email: string, tenantIds: string[]): LoginMethod {
  return {
    tenantIds,
    recipeId: 'thirdparty',
    email,
    thirdPartyId: 'google',
    thirdPartyUserId: String(hashCode(email)),
    isVerified: true,
    isPrimary: false,
    timeJoinedInMSSinceEpoch:
      Math.floor(Math.random() * (Date.now() - 3 * 365 * 24 * 60 * 60 * 1000)) +
      3 * 365 * 24 * 60 * 60 * 1000,
  };
}

function createPasswordlessLoginMethod(
  email: string,
  tenantIds: string[],
  phoneNumber: string
): LoginMethod {
  return {
    tenantIds,
    email,
    recipeId: 'passwordless',
    phoneNumber,
    isVerified: true,
    isPrimary: false,
    timeJoinedInMSSinceEpoch:
      Math.floor(Math.random() * (Date.now() - 3 * 365 * 24 * 60 * 60 * 1000)) +
      3 * 365 * 24 * 60 * 60 * 1000,
  };
}

function hashCode(str: string): number {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash = hash & hash;
  }
  return hash;
}

function generateRandomString(length: number, chars: string): string {
  let result = '';
  for (let i = 0; i < length; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
}

function generateRandomEmail(): string {
  return `${generateRandomString(24, 'abcdefghijklmnopqrstuvwxyz')}@example.com`;
}

function generateRandomPhoneNumber(): string {
  return `+91${generateRandomString(10, '0123456789')}`;
}

function genUser(): User {
  const user: User = {
    externalUserId: '',
    userRoles: [
      { role: 'role1', tenantIds: ['public'] },
      { role: 'role2', tenantIds: ['public'] },
    ],
    loginMethods: [],
  };

  let userId = `e-${uuidv4()}`;
  while (generatedUserIds.has(userId)) {
    userId = `e-${uuidv4()}`;
  }
  generatedUserIds.add(userId);
  user.externalUserId = userId;

  const tenantIds = ['public'];

  let email = generateRandomEmail();
  while (generatedEmails.has(email)) {
    email = generateRandomEmail();
  }
  generatedEmails.add(email);

  const loginMethods: LoginMethod[] = [];

  // Always add email login method
  loginMethods.push(createEmailLoginMethod(email, tenantIds));

  // 50% chance to add third party login
  if (Math.random() < 0.5) {
    loginMethods.push(createThirdPartyLoginMethod(email, tenantIds));
  }

  // 50% chance to add passwordless login
  if (Math.random() < 0.5) {
    let phoneNumber = generateRandomPhoneNumber();
    while (generatedPhoneNumbers.has(phoneNumber)) {
      phoneNumber = generateRandomPhoneNumber();
    }
    generatedPhoneNumbers.add(phoneNumber);
    loginMethods.push(createPasswordlessLoginMethod(email, tenantIds, phoneNumber));
  }

  // If no methods were added, randomly add one
  if (loginMethods.length === 0) {
    const methodNumber = Math.floor(Math.random() * 3);
    if (methodNumber === 0) {
      loginMethods.push(createEmailLoginMethod(email, tenantIds));
    } else if (methodNumber === 1) {
      loginMethods.push(createThirdPartyLoginMethod(email, tenantIds));
    } else {
      let phoneNumber = generateRandomPhoneNumber();
      while (generatedPhoneNumbers.has(phoneNumber)) {
        phoneNumber = generateRandomPhoneNumber();
      }
      generatedPhoneNumbers.add(phoneNumber);
      loginMethods.push(createPasswordlessLoginMethod(email, tenantIds, phoneNumber));
    }
  }

  loginMethods[Math.floor(Math.random() * loginMethods.length)].isPrimary = true;

  user.loginMethods = loginMethods;
  return user;
}

// Create users directory if it doesn't exist
if (!fs.existsSync('users')) {
  fs.mkdirSync('users');
}

for (let i = 0; i < n; i++) {
  console.log(`Generating ${USERS_PER_JSON} users for ${i}`);
  const users: User[] = [];
  for (let j = 0; j < USERS_PER_JSON; j++) {
    users.push(genUser());
  }
  fs.writeFileSync(
    `users/users-${i.toString().padStart(4, '0')}.json`,
    JSON.stringify({ users }, null, 2)
  );
}
