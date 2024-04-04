# Add Users For Bulk Import Script

The `/bulk-import/users` POST API endpoint in SuperTokens Core allows to add users to the database to bulk import the users. However, the API only allows importing 10,000 users at once. This script can take a JSON file containing a large number of users and call the API in batches of 10,000.

## How to Run

1. Ensure you have Node.js (V16 or higher) installed on your system.
2. Open a terminal window and navigate to the directory where the script is located.
3. Run the following command:

   ```
   node index.js <CoreAPIURL> <InputFileName>
   ```

   Replace `<CoreAPIURL>` with the URL of the core API endpoint and `<InputFileName>` with the path to the input JSON file containing user data.

## Format of Input File

The input file should be a JSON file with the same format as requested by the `/bulk-import/users` POST API endpoint.

## Expected Outputs

- Upon successful execution, the script will output a summary message indicating the number of users processed, any remaining users, and any users with invalid schema.
- If there are remaining users to be processed, a file named `remainingUsers.json` will be generated, containing the details of remaining users.
- If there are users with invalid schema, a file named `usersHavingInvalidSchema.json` will be generated, containing the details of users with invalid schema.

## Note

The script would re-write the `remainingUsers.json` and `usersHavingInvalidSchema.json` files on each run. Ensure to back up these files if needed.