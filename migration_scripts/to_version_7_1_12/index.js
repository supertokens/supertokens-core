/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

const libphonenumber = require('libphonenumber-js/max');

// Update the following credentials before running the script
const DB_HOST = "";
const DB_USER = "";
const DB_PASSWORD = "";
const DB_NAME = "";
const CLIENT = ""; // Use "pg" for PostgreSQL and "mysql2" for MySQL DB

if (!DB_HOST || !CLIENT) {
  console.error('Please update the DB_HOST, DB_USER, DB_PASSWORD, DB_DATABASE and CLIENT variables before running the script.');
  return;
}

const knex = require('knex')({
  client: CLIENT,
  connection: {
    host: DB_HOST,
    user: DB_USER,
    password: DB_PASSWORD,
    database: DB_NAME,
  },
  pool: { min: 0, max: 5 }
});

function getUpdatePromise(table, entry, normalizedPhoneNumber) {
  if (table === 'passwordless_devices') {
    return knex.raw(`UPDATE ${table} SET phone_number = ? WHERE app_id = ? AND tenant_id = ? AND device_id_hash = ?`, [normalizedPhoneNumber, entry.app_id, entry.tenant_id, entry.device_id_hash]);
  } else if (table === 'passwordless_users') {
    // Since passwordless_users and passwordless_user_to_tenant are consistent. We can update both tables at the same time. For consistency, we will use a transaction.
    return knex.transaction(async trx => {
      await trx.raw(`UPDATE passwordless_users SET phone_number = ? WHERE app_id = ? AND user_id = ?`, [normalizedPhoneNumber, entry.app_id, entry.user_id]);
      await trx.raw(`UPDATE passwordless_user_to_tenant SET phone_number = ? WHERE app_id = ? AND user_id = ?`, [normalizedPhoneNumber, entry.app_id, entry.user_id]);
    });
  }
}

function getNormalizedPhoneNumber(phoneNumber) {
  try {
    return libphonenumber.parsePhoneNumber(phoneNumber, { extract: false }).format('E.164');
  } catch (error) {
    return null;
  }
}

async function updatePhoneNumbers(table) {
  const batchSize = 1000;
  let offset = 0;

  try {
    while (true) {
      const entries = await knex.raw(`SELECT * FROM ${table} WHERE phone_number is NOT NULL LIMIT ${batchSize} OFFSET ${offset}`);
      const rows = entries.rows ? entries.rows : entries[0];

      const batchUpdates = [];

      for (const entry of rows) {
        const currentPhoneNumber = entry.phone_number;
        const normalizedPhoneNumber = getNormalizedPhoneNumber(currentPhoneNumber);

        if (normalizedPhoneNumber && normalizedPhoneNumber !== currentPhoneNumber) {
          const updatePromise = getUpdatePromise(table, entry, normalizedPhoneNumber);
          batchUpdates.push(updatePromise);
        }
      }

      await Promise.all(batchUpdates);

      offset += rows.length;

      console.log(`Processed ${offset} rows for table ${table}`);

      if (rows.length < batchSize) {
        break;
      }
    }
  } catch (error) {
    console.error(`Error normalising phone numbers for table ${table}:`, error.message);
  }
}

async function runScript() {
  const tables = ['passwordless_users', 'passwordless_devices', 'passwordless_user_to_tenant'];

  for (const table of tables) {
    await updatePhoneNumbers(table);
  }

  console.log('Finished normalising phone numbers!');

  knex.destroy();
}

runScript();
