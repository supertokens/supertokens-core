/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This software is licensed under the Apache License, Version 2.0 (the
 *    "License") as published by the Apache Software Foundation.
 *
 *    You may not use this file except in compliance with the License. You may
 *    obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

const fs = require('fs/promises');
const process = require('process');

const BATCH_SIZE = 10000;
const USERS_HAVING_INVALID_SCHEMA_FILE = './usersHavingInvalidSchema.json';
const REMAINING_USERS_FILE = './remainingUsers.json';

function parseInputArgs() {
  if (process.argv.length !== 4) {
    console.error('Usage: node index.js <CoreAPIURL> <InputFileName>');
    process.exit(1);
  }

  return { coreAPIUrl: process.argv[2], inputFileName: process.argv[3] };
}


async function getUsersFromInputFile({ inputFileName }) {
  try {
    const inputFileDataString = await fs.readFile(inputFileName, 'utf8');
    const inputFileData = JSON.parse(inputFileDataString);

    if (!inputFileData.users || !Array.isArray(inputFileData.users) || inputFileData.users.length === 0) {
      throw new Error('Expected users array in the input file.');
    }

    return inputFileData.users;
  } catch (error) {
    console.error('Error reading or parsing input file:', error.message);
    process.exit(1);
  }
}

async function deleteFailedToAddUsersFileIfExists() {
  try {
    await fs.rm(USERS_HAVING_INVALID_SCHEMA_FILE);
  } catch (error) {
    if (error.code !== 'ENOENT') {
      console.error(`Failed to delete ${USERS_HAVING_INVALID_SCHEMA_FILE}:`, error.message);
    }
  }
}

async function addInvalidSchemaUsersToFile({ errors, users }) {
  let parsedData = null;
  try {
    const existingData = await fs.readFile(USERS_HAVING_INVALID_SCHEMA_FILE, 'utf8');
    parsedData = JSON.parse(existingData);
  } catch (error) {
    if (error.code === 'ENOENT') {
      parsedData = { users: [] };
    } else {
      console.error(`Failed to read output file. Error: ${error.message}`);
      throw error;
    }
  }

  parsedData.users.push(...errors.map((err, index) => ({ user: users[index], errors: err.errors })));

  await fs.writeFile(USERS_HAVING_INVALID_SCHEMA_FILE, JSON.stringify(parsedData, null, 2));

  return users.filter((_, index) => !errors.some(err => err.index === index));
}

async function updateRemainingUsersFile({ users, index }) {
  const remainingUsers = users.slice(index + 1);
  await fs.writeFile(REMAINING_USERS_FILE, JSON.stringify({ users: remainingUsers }, null, 2));
}

async function removeRemainingUsersFile() {
  try {
    await fs.rm(REMAINING_USERS_FILE);
  } catch (error) {
    if (error.code !== 'ENOENT') {
      console.error(`Failed to delete ${REMAINING_USERS_FILE}:`, error.message);
    }
  }
}

async function main() {
  const { coreAPIUrl, inputFileName } = parseInputArgs();

  const users = await getUsersFromInputFile({ inputFileName });

  await deleteFailedToAddUsersFileIfExists();
  await updateRemainingUsersFile({ users, index: 0 });

  let usersToProcessInBatch = [];
  let usersHavingInvalidSchemaCount = 0;
  let i = 0;

  try {
    while (i < users.length || usersToProcessInBatch.length > 0) {
      let remainingBatchSize = usersToProcessInBatch.length > BATCH_SIZE ? 0 : BATCH_SIZE - usersToProcessInBatch.length;
      remainingBatchSize = Math.min(remainingBatchSize, users.length - i);

      usersToProcessInBatch.push(...users.slice(i, i + remainingBatchSize));

      const res = await fetch(`${coreAPIUrl}/bulk-import/users`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ users: usersToProcessInBatch }),
      });

      if (!res.ok && res.status !== 400) {
        const text = await res.text();
        console.error(`Failed to add users. API response - status: ${res.status} body: ${text}`);
        break;
      }

      if (res.status === 400) {
        const errors = await res.json();
        usersHavingInvalidSchemaCount += errors.users.length;
        usersToProcessInBatch = await addInvalidSchemaUsersToFile({ errors: errors.users, users: usersToProcessInBatch });
      } else {
        await updateRemainingUsersFile({ users, index: i });
        usersToProcessInBatch = [];
      }

      i += remainingBatchSize;
    }
  } catch (error) {
    console.log(`Got an unexpected Error: `, error);
  }

  const result = {
    totalUsers: users.length,
    processedUsers: i,
    remainingUsers: users.length - i,
    usersHavingInvalidSchema: usersHavingInvalidSchemaCount,
    ...(users.length - i > 0 && { remainingUsersFileName: REMAINING_USERS_FILE }),
    ...(usersHavingInvalidSchemaCount > 0 && { usersHavingInvalidSchemaFileName: USERS_HAVING_INVALID_SCHEMA_FILE }),
  };

  if (i < users.length) {
    const message = usersHavingInvalidSchemaCount > 0 ?
      `We processed ${i} users and ${usersHavingInvalidSchemaCount} users have invalid schema! Remaining users can be processed again by processing the ${REMAINING_USERS_FILE} file and users having invalid schema needs to be fixed and processed again by processing the ${USERS_HAVING_INVALID_SCHEMA_FILE} file.`
      : `We processed ${i} users and ${users.length - i} users are remaining to be processed! Remaining users can be processed again by processing the ${REMAINING_USERS_FILE} file.`;
    console.log({ message, ...result });
  } else {
    await removeRemainingUsersFile();
    const message = usersHavingInvalidSchemaCount > 0 ?
      `All users processed but ${usersHavingInvalidSchemaCount} users have invalid schema! Users having invalid schema needs to be fixed and processed again by processing the ${USERS_HAVING_INVALID_SCHEMA_FILE} file.` : `All users processed successfully!`;
    console.log({ message, ...result }); ``
  }
}


main()