/**
 * REVIEW-007: Blue-green migration mode transition tests.
 *
 * Tests the full CUD-based migration lifecycle via the HTTP API:
 *   LEGACY → DUAL_WRITE_READ_OLD → DUAL_WRITE_READ_NEW → MIGRATED
 *
 * Each test creates a dedicated app via the multitenancy API with a
 * specific `migration_mode` coreConfig. Mode switches are done by
 * calling PUT /recipe/multitenancy/app/v2 again with the new mode.
 * This mirrors a real production blue-green deployment where the
 * migration_mode is a per-app (or per-CUD) config value.
 *
 * All operations use direct HTTP calls scoped to /appid-<id>/ so they
 * hit the correct app context and its associated migration_mode config.
 */

import {
  test,
  assert,
  assertEqual,
  assertNotNull,
  uniqueEmail,
  coreApiRequest,
  Mode,
  createOrUpdateApp,
  appScopedRequest,
  appSignUp,
  appTpSignInUp,
  appMakePrimary,
  appLinkAccounts,
  appUnlinkAccount,
  appGetUser,
  appDeleteUser,
  appUpdateEmail,
  appTpUpdateEmail,
  appSetupLicense,
} from './setup';

let appCounter = 0;

function nextAppId(): string {
  return `mig-test-${Date.now()}-${++appCounter}`;
}

// ── Full blue-green migration lifecycle ─────────────────────────────────────

test('blue-green: full LEGACY → DW_READ_OLD → DW_READ_NEW → MIGRATED lifecycle', async () => {
  const appId = nextAppId();

  // ─── Phase 1: LEGACY ───────────────────────────────────────────────
  await createOrUpdateApp(appId, 'LEGACY');
  await appSetupLicense(appId);

  // Create users in LEGACY mode
  const { userId: legacyUser1 } = await appSignUp(appId);
  const { userId: legacyUser2 } = await appSignUp(appId);
  const { userId: legacyTpUser, tpUserId: legacyTpId } = await appTpSignInUp(appId, 'google');

  // Make legacyUser1 primary and link legacyUser2
  await appMakePrimary(appId, legacyUser1);
  await appLinkAccounts(appId, legacyUser2, legacyUser1);

  let primary = await appGetUser(appId, legacyUser1);
  assertNotNull(primary, 'legacy primary should exist');
  assertEqual(primary.loginMethods.length, 2, 'legacy primary should have 2 methods');

  // ─── Phase 2: DUAL_WRITE_READ_OLD ─────────────────────────────────
  await createOrUpdateApp(appId, 'DUAL_WRITE_READ_OLD');

  // Legacy users should still be readable (reads from old tables)
  primary = await appGetUser(appId, legacyUser1);
  assertNotNull(primary, 'legacy primary readable in DW_READ_OLD');
  assertEqual(primary.loginMethods.length, 2, 'legacy primary still has 2 methods');

  // Create new users — dual-written to both old and new tables
  const { userId: dwUser1, email: dwEmail1 } = await appSignUp(appId);
  const { userId: dwUser2 } = await appSignUp(appId);
  const { userId: dwTpUser, tpUserId: dwTpId } = await appTpSignInUp(appId, 'google');

  await appMakePrimary(appId, dwUser1);
  await appLinkAccounts(appId, dwUser2, dwUser1);
  await appLinkAccounts(appId, dwTpUser, dwUser1);

  let dwPrimary = await appGetUser(appId, dwUser1);
  assertNotNull(dwPrimary, 'DW primary should exist');
  assertEqual(dwPrimary.loginMethods.length, 3, 'DW primary should have 3 methods');

  // Update email in DUAL_WRITE mode
  const updatedEmail = uniqueEmail('legacy-updated');
  await appUpdateEmail(appId, legacyUser2, updatedEmail);

  primary = await appGetUser(appId, legacyUser1);
  const updatedMethod = primary.loginMethods.find(
    (lm: any) => lm.recipeUserId === legacyUser2
  );
  assertNotNull(updatedMethod, 'updated method should be in primary');
  assertEqual(updatedMethod.email, updatedEmail, 'email should be updated in DW_READ_OLD');

  // Link the legacy TP user to the legacy primary (now dual-writing)
  await appLinkAccounts(appId, legacyTpUser, legacyUser1);
  primary = await appGetUser(appId, legacyUser1);
  assertEqual(primary.loginMethods.length, 3, 'legacy primary should now have 3 methods');

  // ─── Phase 3: DUAL_WRITE_READ_NEW ─────────────────────────────────
  await createOrUpdateApp(appId, 'DUAL_WRITE_READ_NEW');

  // DW users should be readable (reads now from new tables)
  dwPrimary = await appGetUser(appId, dwUser1);
  assertNotNull(dwPrimary, 'DW primary readable in DW_READ_NEW');
  assertEqual(dwPrimary.loginMethods.length, 3, 'DW primary still has 3 methods in READ_NEW');

  // Create user in READ_NEW mode and link
  const { userId: rnUser } = await appSignUp(appId);
  await appLinkAccounts(appId, rnUser, dwUser1);
  dwPrimary = await appGetUser(appId, dwUser1);
  assertEqual(dwPrimary.loginMethods.length, 4, 'DW primary has 4 methods after READ_NEW link');

  // Unlink one user
  await appUnlinkAccount(appId, dwUser2);
  dwPrimary = await appGetUser(appId, dwUser1);
  assertEqual(dwPrimary.loginMethods.length, 3, 'DW primary has 3 methods after unlink');

  const unlinked = await appGetUser(appId, dwUser2);
  assertNotNull(unlinked, 'unlinked user should still exist');
  assert(!unlinked.isPrimaryUser, 'unlinked user should not be primary');

  // ─── Phase 4: MIGRATED ────────────────────────────────────────────
  await createOrUpdateApp(appId, 'MIGRATED');

  // DW users should be readable in MIGRATED mode
  dwPrimary = await appGetUser(appId, dwUser1);
  assertNotNull(dwPrimary, 'DW primary readable in MIGRATED');
  assertEqual(dwPrimary.loginMethods.length, 3, 'DW primary has 3 methods in MIGRATED');

  // Create and link in MIGRATED mode (only writes to new tables)
  const { userId: migUser } = await appSignUp(appId);
  await appLinkAccounts(appId, migUser, dwUser1);
  dwPrimary = await appGetUser(appId, dwUser1);
  assertEqual(dwPrimary.loginMethods.length, 4, 'DW primary has 4 methods after MIGRATED link');

  // Update TP email in MIGRATED mode
  const newTpEmail = uniqueEmail('tp-migrated');
  await appTpUpdateEmail(appId, 'google', dwTpId, newTpEmail);
  dwPrimary = await appGetUser(appId, dwUser1);
  const tpMethod = dwPrimary.loginMethods.find(
    (lm: any) => lm.recipeUserId === dwTpUser
  );
  assertNotNull(tpMethod, 'TP method should be in primary');
  assertEqual(tpMethod.email, newTpEmail, 'TP email should be updated in MIGRATED');

  // Delete in MIGRATED mode
  await appDeleteUser(appId, migUser);
  dwPrimary = await appGetUser(appId, dwUser1);
  assertEqual(dwPrimary.loginMethods.length, 3, 'DW primary has 3 methods after MIGRATED delete');
});

// ── Concurrent operations during mode switch ────────────────────────────────

test('blue-green: concurrent operations survive mode switch', async () => {
  const appId = nextAppId();
  await createOrUpdateApp(appId, 'DUAL_WRITE_READ_OLD');
  await appSetupLicense(appId);

  // Create a primary with linked users
  const { userId: primaryId } = await appSignUp(appId);
  await appMakePrimary(appId, primaryId);

  const { userId: linked1 } = await appSignUp(appId);
  const { userId: linked2 } = await appSignUp(appId);
  await appLinkAccounts(appId, linked1, primaryId);
  await appLinkAccounts(appId, linked2, primaryId);

  // Fire concurrent operations while switching mode
  await Promise.allSettled([
    appUpdateEmail(appId, linked1, uniqueEmail('during-switch')),
    appUnlinkAccount(appId, linked2),
    createOrUpdateApp(appId, 'DUAL_WRITE_READ_NEW'),
    appSignUp(appId),
  ]);

  // Verify primary is in a valid state
  const primary = await appGetUser(appId, primaryId);
  assertNotNull(primary, 'primary should survive concurrent mode switch');
  assert(primary.isPrimaryUser, 'should still be primary');

  const count = primary.loginMethods.length;
  assert(count >= 1 && count <= 3, `method count ${count} should be 1-3`);
});

// ── Rollback scenario: forward then back ────────────────────────────────────

test('blue-green: rollback from DW_READ_NEW to DW_READ_OLD', async () => {
  const appId = nextAppId();
  await createOrUpdateApp(appId, 'DUAL_WRITE_READ_OLD');
  await appSetupLicense(appId);

  const { userId: primaryId } = await appSignUp(appId);
  await appMakePrimary(appId, primaryId);
  const { userId: linkedId } = await appSignUp(appId);
  await appLinkAccounts(appId, linkedId, primaryId);

  let primary = await appGetUser(appId, primaryId);
  assertEqual(primary.loginMethods.length, 2, 'should have 2 methods in READ_OLD');

  // Move forward to READ_NEW
  await createOrUpdateApp(appId, 'DUAL_WRITE_READ_NEW');

  const { userId: rnUser } = await appSignUp(appId);
  await appLinkAccounts(appId, rnUser, primaryId);
  primary = await appGetUser(appId, primaryId);
  assertEqual(primary.loginMethods.length, 3, 'should have 3 methods in READ_NEW');

  // ROLLBACK: switch back to READ_OLD
  await createOrUpdateApp(appId, 'DUAL_WRITE_READ_OLD');

  // Data created in DUAL_WRITE modes is in BOTH tables — visible either way
  primary = await appGetUser(appId, primaryId);
  assertNotNull(primary, 'primary readable after rollback');
  assertEqual(primary.loginMethods.length, 3, 'all 3 methods visible after rollback');

  // Operations still work after rollback
  const { userId: postRollbackUser } = await appSignUp(appId);
  await appLinkAccounts(appId, postRollbackUser, primaryId);
  primary = await appGetUser(appId, primaryId);
  assertEqual(primary.loginMethods.length, 4, 'link works after rollback');
});

// ── Email conflict detection across modes ───────────────────────────────────

test('blue-green: email conflict detection works after mode transition', async () => {
  const appId = nextAppId();
  await createOrUpdateApp(appId, 'DUAL_WRITE_READ_OLD');
  await appSetupLicense(appId);

  // Create primary with a specific email
  const conflictEmail = uniqueEmail('conflict-bg');
  const { userId: primary1 } = await appSignUp(appId, conflictEmail);
  await appMakePrimary(appId, primary1);

  // Switch to READ_NEW
  await createOrUpdateApp(appId, 'DUAL_WRITE_READ_NEW');

  // Another user tries to become primary with the same email
  const { userId: user2 } = await appSignUp(appId, conflictEmail + '2');
  await appUpdateEmail(appId, user2, conflictEmail);

  const resp = await appScopedRequest(appId, 'POST', '/recipe/accountlinking/user/primary', {
    recipeUserId: user2,
  });
  assertEqual(
    resp.body?.status,
    'ACCOUNT_INFO_ALREADY_ASSOCIATED_WITH_ANOTHER_PRIMARY_USER_ID_ERROR',
    'email conflict detected after mode switch to READ_NEW'
  );

  // Switch to MIGRATED — conflict should still be detected
  await createOrUpdateApp(appId, 'MIGRATED');

  const { userId: user3 } = await appSignUp(appId, conflictEmail + '3');
  await appUpdateEmail(appId, user3, conflictEmail);

  const resp2 = await appScopedRequest(appId, 'POST', '/recipe/accountlinking/user/primary', {
    recipeUserId: user3,
  });
  assertEqual(
    resp2.body?.status,
    'ACCOUNT_INFO_ALREADY_ASSOCIATED_WITH_ANOTHER_PRIMARY_USER_ID_ERROR',
    'email conflict detected in MIGRATED mode'
  );
});
