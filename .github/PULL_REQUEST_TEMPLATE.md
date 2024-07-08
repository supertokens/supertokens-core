## Summary of change

(A few sentences about this PR)

## Related issues

- Link to issue1 here
- Link to issue1 here

## Test Plan

(Write your test plan here. If you changed any code, please provide us with clear instructions on how you verified your
changes work. Bonus points for screenshots and videos!)

## Documentation changes

(If relevant, please create a PR in our [docs repo](https://github.com/supertokens/docs), or create a checklist here
highlighting the necessary changes)

## Checklist for important updates

- [ ] Changelog has been updated
    - [ ] If there are any db schema changes, mention those changes clearly
- [ ] `coreDriverInterfaceSupported.json` file has been updated (if needed)
- [ ] `pluginInterfaceSupported.json` file has been updated (if needed)
- [ ] Changes to the version if needed
    - In `build.gradle`
- [ ] If added a new paid feature, edit the `getPaidFeatureStats` function in FeatureFlag.java file
- [ ] Had installed and ran the pre-commit hook
- [ ] If there are new dependencies that have been added in `build.gradle`, please make sure to add them
  in `implementationDependencies.json`.
- [ ] Update function `getValidFields` in `io/supertokens/config/CoreConfig.java` if new aliases were added for any core
  config (similar to the `access_token_signing_key_update_interval` config alias).
- [ ] Issue this PR against the latest non released version branch.
    - To know which one it is, run find the latest released tag (`git tag`) in the format `vX.Y.Z`, and then find the
      latest branch (`git branch --all`) whose `X.Y` is greater than the latest released tag.
    - If no such branch exists, then create one from the latest released branch.
- [ ] If added a foreign key constraint on `app_id_to_user_id` table, make sure to delete from this table when deleting
  the user as well if `deleteUserIdMappingToo` is false.

## Remaining TODOs for this PR

- [ ] Item1
- [ ] Item2
