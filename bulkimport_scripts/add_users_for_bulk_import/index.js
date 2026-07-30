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
const yargs = require('yargs');
const process = require('process');

const BATCH_SIZE = 10000;

async function parseInputArgs() {
  const argv = await yargs
      .option('core-endpoint', {
          alias: 'c',
          type: 'string',
          describe: 'Core API URL endpoint',
          demandOption: true,
      })
      .option('input-file', {
          alias: 'i',
          type: 'string',
          describe: 'Path to the input file',
          demandOption: true,
      })
      .option('invalid-schema-file', {
          alias: 's',
          type: 'string',
          describe: 'Path to the file storing users with invalid schema',
          default: './usersHavingInvalidSchema.json',
      })
      .option('remaining-users-file', {
          alias: 'r',
          type: 'string',
          describe: 'Path to the file storing remaining users',
          default: './remainingUsers.json',
      })
      .argv;

  return {
      coreAPIUrl: argv['core-endpoint'],
      inputFileName: argv['input-file'],
      usersHavingInvalidSchemaFileName: argv['invalid-schema-file'],
      remainingUsersFileName: argv['remaining-users-file'],
  };
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

async function deleteUsersHavingInvalidSchemaFileIfExists({ usersHavingInvalidSchemaFileName }) {
  try {
    await fs.rm(usersHavingInvalidSchemaFileName);
  } catch (error) {
    if (error.code !== 'ENOENT') {
      console.error(`Failed to delete ${usersHavingInvalidSchemaFileName}:`, error.message);
    }
  }
}

async function addInvalidSchemaUsersToFile({ errors, users, usersHavingInvalidSchemaFileName }) {
  let parsedData = null;
  try {
    const existingData = await fs.readFile(usersHavingInvalidSchemaFileName, 'utf8');
    parsedData = JSON.parse(existingData);
  } catch (error) {
    if (error.code === 'ENOENT') {
      parsedData = { users: [] };
    } else {
      console.error(`Failed to read output file. Error: ${error.message}`);
      throw error;
    }
  }

  parsedData.users.push(...errors.map((err) => ({ user: users[err.index], errors: err.errors })));

  await fs.writeFile(usersHavingInvalidSchemaFileName, JSON.stringify(parsedData, null, 2));

  return users.filter((_, index) => !errors.some(err => err.index === index));
}

async function updateRemainingUsersFile({ users, index, remainingUsersFileName }) {
  const remainingUsers = users.slice(index + 1);
  await fs.writeFile(remainingUsersFileName, JSON.stringify({ users: remainingUsers }, null, 2));
}

async function removeRemainingUsersFile({ remainingUsersFileName }) {
  try {
    await fs.rm(remainingUsersFileName);
  } catch (error) {
    if (error.code !== 'ENOENT') {
      console.error(`Failed to delete ${remainingUsersFileName}:`, error.message);
    }
  }
}

async function main() {
  const { coreAPIUrl, inputFileName, usersHavingInvalidSchemaFileName, remainingUsersFileName } = await parseInputArgs();

  const users = await getUsersFromInputFile({ inputFileName });

  await deleteUsersHavingInvalidSchemaFileIfExists({ usersHavingInvalidSchemaFileName });
  await updateRemainingUsersFile({ users,  index: 0, remainingUsersFileName });

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
        usersToProcessInBatch = await addInvalidSchemaUsersToFile({ errors: errors.users, users: usersToProcessInBatch, usersHavingInvalidSchemaFileName });
      } else {
        await updateRemainingUsersFile({ users, index: i, remainingUsersFileName });
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
    ...(users.length - i > 0 && { remainingUsersFileName }),
    ...(usersHavingInvalidSchemaCount > 0 && { usersHavingInvalidSchemaFileName }),
  };

  if (i < users.length) {
    const message = usersHavingInvalidSchemaCount > 0 ?
      `We processed ${i} users and ${usersHavingInvalidSchemaCount} users have invalid schema! Remaining users can be processed again by processing the ${remainingUsersFileName} file and users having invalid schema needs to be fixed and processed again by processing the ${usersHavingInvalidSchemaFileName} file.`
      : `We processed ${i} users and ${users.length - i} users are remaining to be processed! Remaining users can be processed again by processing the ${remainingUsersFileName} file.`;
    console.log({ message, ...result });
  } else {
    await removeRemainingUsersFile({ remainingUsersFileName });
    const message = usersHavingInvalidSchemaCount > 0 ?
      `All users processed but ${usersHavingInvalidSchemaCount} users have invalid schema! Users having invalid schema needs to be fixed and processed again by processing the ${usersHavingInvalidSchemaFileName} file.` : `All users processed successfully!`;
    console.log({ message, ...result }); ``
  }
}

main()
