/**
 * REVIEW-007: Account linking behavioral equivalence tests.
 *
 * Black-box HTTP-level tests verifying that account linking APIs behave
 * identically across all migration modes. Each test creates users via the
 * SDK, performs linking operations, and asserts on the resulting user state.
 *
 * These tests are mode-agnostic: same assertions apply whether the core is
 * running in LEGACY, DUAL_WRITE_READ_OLD, DUAL_WRITE_READ_NEW, or MIGRATED.
 */

import SuperTokens from 'supertokens-node';
import AccountLinking from 'supertokens-node/recipe/accountlinking';
import EmailPassword from 'supertokens-node/recipe/emailpassword';

import {
  test,
  assert,
  assertEqual,
  assertNotNull,
  createEpUser,
  createTpUser,
  makePrimary,
  linkUsers,
  unlinkUser,
  getUser,
  deleteUser,
  uniqueEmail,
} from './setup';

// ── Test: createPrimaryUser ─────────────────────────────────────────────────

test('createPrimaryUser returns OK and sets isPrimaryUser', async () => {
  const { userId } = await createEpUser();
  const resp = await AccountLinking.createPrimaryUser(
    SuperTokens.convertToRecipeUserId(userId)
  );

  assertEqual(resp.status, 'OK', 'createPrimaryUser status');
  if (resp.status !== 'OK') throw new Error('unreachable');
  assert(resp.user.isPrimaryUser, 'user should be primary');
  assertEqual(resp.user.id, userId, 'primary user id should match');
  assertEqual(resp.user.loginMethods.length, 1, 'should have 1 login method');
});

test('createPrimaryUser on already-primary user returns OK or wasAlreadyAPrimaryUser', async () => {
  const { userId } = await createEpUser();
  await makePrimary(userId);

  const resp = await AccountLinking.createPrimaryUser(
    SuperTokens.convertToRecipeUserId(userId)
  );
  // Second call should succeed (wasAlreadyAPrimaryUser=true) or indicate already linked
  assert(
    resp.status === 'OK' ||
      resp.status === 'RECIPE_USER_ID_ALREADY_LINKED_WITH_PRIMARY_USER_ID_ERROR',
    `Expected OK or ALREADY_LINKED, got ${resp.status}`
  );
});

test('createPrimaryUser fails when email conflicts with existing primary', async () => {
  const email = uniqueEmail('conflict');
  const { userId: user1 } = await createEpUser(email);
  await makePrimary(user1);

  // Create a TP user with the same email — allowed because it's a different
  // recipe; createPrimaryUser must reject the conflict.
  const { userId: user2 } = await createTpUser('google', email);

  const resp = await AccountLinking.createPrimaryUser(
    SuperTokens.convertToRecipeUserId(user2)
  );
  assertEqual(
    resp.status,
    'ACCOUNT_INFO_ALREADY_ASSOCIATED_WITH_ANOTHER_PRIMARY_USER_ID_ERROR',
    'should fail with email conflict'
  );
});

// ── Test: linkAccounts ──────────────────────────────────────────────────────

test('linkAccounts links recipe user to primary user', async () => {
  const { userId: primaryId } = await createEpUser();
  await makePrimary(primaryId);

  const { userId: recipeId } = await createEpUser();
  const resp = await AccountLinking.linkAccounts(
    SuperTokens.convertToRecipeUserId(recipeId),
    primaryId
  );

  assertEqual(resp.status, 'OK', 'linkAccounts status');
  if (resp.status !== 'OK') throw new Error('unreachable');
  assert(resp.user.isPrimaryUser, 'result should be primary');
  assertEqual(resp.user.loginMethods.length, 2, 'primary should have 2 login methods');

  // Verify via getUserById
  const user = await getUser(primaryId);
  assertNotNull(user, 'primary user should exist');
  assertEqual(user!.loginMethods.length, 2, 'getUserById should show 2 login methods');
});

test('linkAccounts with EP + TP users works', async () => {
  const { userId: primaryId } = await createEpUser();
  await makePrimary(primaryId);

  const { userId: tpId } = await createTpUser();
  const resp = await AccountLinking.linkAccounts(
    SuperTokens.convertToRecipeUserId(tpId),
    primaryId
  );

  assertEqual(resp.status, 'OK', 'linkAccounts EP+TP status');
  if (resp.status !== 'OK') throw new Error('unreachable');
  assertEqual(resp.user.loginMethods.length, 2, 'should have 2 login methods (EP+TP)');

  // Verify each login method type
  const methods = resp.user.loginMethods.map((lm) => lm.recipeId);
  assert(methods.includes('emailpassword'), 'should have EP login method');
  assert(methods.includes('thirdparty'), 'should have TP login method');
});

test('linkAccounts fails when recipe user is already linked elsewhere', async () => {
  const { userId: primary1 } = await createEpUser();
  await makePrimary(primary1);
  const { userId: primary2 } = await createEpUser();
  await makePrimary(primary2);

  const { userId: recipeId } = await createEpUser();
  await linkUsers(recipeId, primary1);

  // Try to link same recipe user to a different primary
  const resp = await AccountLinking.linkAccounts(
    SuperTokens.convertToRecipeUserId(recipeId),
    primary2
  );
  assertEqual(
    resp.status,
    'RECIPE_USER_ID_ALREADY_LINKED_WITH_ANOTHER_PRIMARY_USER_ID_ERROR',
    'should fail: already linked'
  );
});

test('linkAccounts fails when email conflicts with another primary', async () => {
  const { userId: primary1, email: email1 } = await createEpUser();
  await makePrimary(primary1);

  const { userId: primary2 } = await createEpUser();
  await makePrimary(primary2);

  // TP user with email1 — allowed (different recipe), but linking to primary2
  // must fail because email1 is already claimed by primary1's group.
  const { userId: recipeId } = await createTpUser('google', email1);
  const resp = await AccountLinking.linkAccounts(
    SuperTokens.convertToRecipeUserId(recipeId),
    primary2
  );

  assert(
    resp.status === 'ACCOUNT_INFO_ALREADY_ASSOCIATED_WITH_ANOTHER_PRIMARY_USER_ID_ERROR' ||
      resp.status === 'RECIPE_USER_ID_ALREADY_LINKED_WITH_ANOTHER_PRIMARY_USER_ID_ERROR',
    `Expected conflict error, got ${resp.status}`
  );
});

// ── Test: unlinkAccounts ────────────────────────────────────────────────────

test('unlinkAccounts removes user from linked group', async () => {
  const { userId: primaryId } = await createEpUser();
  await makePrimary(primaryId);

  const { userId: recipeId } = await createEpUser();
  await linkUsers(recipeId, primaryId);

  // Verify linked
  let primary = await getUser(primaryId);
  assertNotNull(primary, 'primary should exist before unlink');
  assertEqual(primary!.loginMethods.length, 2, 'should have 2 methods before unlink');

  // Unlink
  const unlinkResp = await AccountLinking.unlinkAccount(
    SuperTokens.convertToRecipeUserId(recipeId)
  );
  assertEqual(unlinkResp.status, 'OK', 'unlink status');

  // Verify primary now has 1 method
  primary = await getUser(primaryId);
  assertNotNull(primary, 'primary should exist after unlink');
  assertEqual(primary!.loginMethods.length, 1, 'primary should have 1 method after unlink');

  // Verify recipe user exists independently
  const recipe = await getUser(recipeId);
  assertNotNull(recipe, 'unlinked user should still exist');
  assert(!recipe!.isPrimaryUser, 'unlinked user should not be primary');
});

test('unlinkAccounts on non-linked user is a no-op', async () => {
  const { userId } = await createEpUser();

  const resp = await AccountLinking.unlinkAccount(
    SuperTokens.convertToRecipeUserId(userId)
  );
  assertEqual(resp.status, 'OK', 'unlink non-linked should succeed');

  const user = await getUser(userId);
  assertNotNull(user, 'user should still exist');
});

// ── Test: canLinkAccounts ───────────────────────────────────────────────────

test('canLinkAccounts returns OK when linkable', async () => {
  const { userId: primaryId } = await createEpUser();
  await makePrimary(primaryId);

  const { userId: recipeId } = await createEpUser();
  const resp = await AccountLinking.canLinkAccounts(
    SuperTokens.convertToRecipeUserId(recipeId),
    primaryId
  );
  assertEqual(resp.status, 'OK', 'canLinkAccounts should return OK');
});

test('canLinkAccounts detects email conflict', async () => {
  const { userId: primary1, email } = await createEpUser();
  await makePrimary(primary1);

  const { userId: primary2 } = await createEpUser();
  await makePrimary(primary2);

  // TP user with primary1's email — creation allowed, canLink must detect conflict.
  const { userId: recipeId } = await createTpUser('google', email);
  const resp = await AccountLinking.canLinkAccounts(
    SuperTokens.convertToRecipeUserId(recipeId),
    primary2
  );

  assert(
    resp.status === 'ACCOUNT_INFO_ALREADY_ASSOCIATED_WITH_ANOTHER_PRIMARY_USER_ID_ERROR' ||
      resp.status === 'RECIPE_USER_ID_ALREADY_LINKED_WITH_ANOTHER_PRIMARY_USER_ID_ERROR',
    `canLink should detect conflict, got ${resp.status}`
  );
});

// ── Test: deleteUser interaction with linking ───────────────────────────────

test('deleteUser removes linked user from group', async () => {
  const { userId: primaryId } = await createEpUser();
  await makePrimary(primaryId);

  const { userId: recipeId } = await createEpUser();
  await linkUsers(recipeId, primaryId);

  // Delete the recipe user
  await deleteUser(recipeId);

  // Primary should still exist with 1 login method
  const primary = await getUser(primaryId);
  assertNotNull(primary, 'primary should survive linked user deletion');
  assertEqual(primary!.loginMethods.length, 1, 'primary should have 1 method after linked deleted');
});

test('deleteUser on primary removes entire group when removeAllLinkedAccounts', async () => {
  const { userId: primaryId } = await createEpUser();
  await makePrimary(primaryId);

  const { userId: recipeId } = await createEpUser();
  await linkUsers(recipeId, primaryId);

  // Delete the primary user (removes all)
  await SuperTokens.deleteUser(primaryId, true);

  const primary = await getUser(primaryId);
  const recipe = await getUser(recipeId);
  assert(primary === undefined, 'primary should be deleted');
  assert(recipe === undefined, 'linked recipe user should also be deleted');
});

// ── Test: multiple links ────────────────────────────────────────────────────

test('linking 3 users creates a group with 3 login methods', async () => {
  const { userId: primaryId } = await createEpUser();
  await makePrimary(primaryId);

  const { userId: recipe1 } = await createEpUser();
  const { userId: recipe2 } = await createTpUser();

  await linkUsers(recipe1, primaryId);
  await linkUsers(recipe2, primaryId);

  const primary = await getUser(primaryId);
  assertNotNull(primary, 'primary should exist');
  assertEqual(primary!.loginMethods.length, 3, 'should have 3 login methods');
  assert(primary!.isPrimaryUser, 'should be primary');

  // All three users should resolve to the same primary
  const r1 = await getUser(recipe1);
  const r2 = await getUser(recipe2);
  assertNotNull(r1, 'recipe1 should exist');
  assertNotNull(r2, 'recipe2 should exist');
  assertEqual(r1!.id, primaryId, 'recipe1 should resolve to primary');
  assertEqual(r2!.id, primaryId, 'recipe2 should resolve to primary');
});

test('unlink + re-link produces correct state', async () => {
  const { userId: primaryId } = await createEpUser();
  await makePrimary(primaryId);

  const { userId: recipeId } = await createEpUser();
  await linkUsers(recipeId, primaryId);

  // Unlink
  await unlinkUser(recipeId);
  let primary = await getUser(primaryId);
  assertEqual(primary!.loginMethods.length, 1, 'should have 1 after unlink');

  // Re-link
  await linkUsers(recipeId, primaryId);
  primary = await getUser(primaryId);
  assertEqual(primary!.loginMethods.length, 2, 'should have 2 after re-link');
});
