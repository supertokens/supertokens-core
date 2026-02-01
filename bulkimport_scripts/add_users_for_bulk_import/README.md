# Add Users For Bulk Import Script

The `/bulk-import/users` POST API endpoint in SuperTokens Core allows to add users to the database to bulk import the users. However, the API only allows importing 10,000 users at once. This script can take a JSON file containing a large number of users and call the API in batches of 10,000.

## How to Run

1. Ensure you have Node.js (v16 or higher) installed on your system.
2. Open a terminal window and navigate to the directory where the script is located.
3. Run `npm install` to install necessary dependencies.
4. Run the script using the following command:

    ```
    node index.js --core-endpoint <CoreAPIURL> --input-file <InputFileName> [--invalid-schema-file <InvalidSchemaFile>] [--remaining-users-file <RemainingUsersFile>]
    ```

    - Replace `<CoreAPIURL>` with the URL of the core API endpoint.
    - Replace `<InputFileName>` with the path to the input JSON file containing user data.
    - Optionally, you can specify the paths for the output files:
        - `--invalid-schema-file <InvalidSchemaFile>` specifies the path to the file storing users with invalid schema (default is `./usersHavingInvalidSchema.json`).
        - `--remaining-users-file <RemainingUsersFile>` specifies the path to the file storing remaining users (default is `./remainingUsers.json`).

## Format of Input File

The input file should be a JSON file with the same format as requested by the `/bulk-import/users` POST API endpoint. An example file named `example_input_file.json` is provided in the same directory.

## Expected Outputs

- Upon successful execution, the script will output a summary message indicating the number of users processed, any remaining users, and any users with invalid schema.
- If there are remaining users to be processed, the file specified by `--remaining-users-file` (default `remainingUsers.json`) will be generated, containing the details of remaining users.
- If there are users with invalid schema, the file specified by `--invalid-schema-file` (default `usersHavingInvalidSchema.json`) will be generated, containing the details of users with invalid schema.

## Note

The script would re-write the files specified by `--remaining-users-file` and `--invalid-schema-file` options on each run. Ensure to back up these files if needed.