/**
 * REVIEW-007: Email update behavioral equivalence tests.
 *
 * Verifies that email/password updates via all recipe types behave
 * identically across migration modes, including interactions with
 * account linking (email conflict detection).
 */

import SuperTokens from 'supertokens-node';
import EmailPassword from 'supertokens-node/recipe/emailpassword';
import ThirdParty from 'supertokens-node/recipe/thirdparty';

import {
  test,
  assert,
  assertEqual,
  assertNotNull,
  createEpUser,
  createTpUser,
  makePrimary,
  linkUsers,
  getUser,
  uniqueEmail,
} from './setup';

// ── EmailPassword email updates ─────────────────────────────────────────────

test('EP updateEmail changes email for standalone user', async () => {
  const { userId } = await createEpUser();
  const newEmail = uniqueEmail('ep-updated');

  const resp = await EmailPassword.updateEmailOrPassword({
    recipeUserId: SuperTokens.convertToRecipeUserId(userId),
    email: newEmail,
  });
  assertEqual(resp.status, 'OK', 'updateEmail status');

  const user = await getUser(userId);
  assertNotNull(user, 'user should exist');
  const epMethod = user!.loginMethods.find((lm: any) => lm.recipeId === 'emailpassword');
  assertNotNull(epMethod, 'should have EP login method');
  assertEqual(epMethod!.email, newEmail, 'email should be updated');
});

test('EP updateEmail changes email for linked user', async () => {
  const { userId: primaryId } = await createEpUser();
  await makePrimary(primaryId);

  const { userId: linkedId } = await createEpUser();
  await linkUsers(linkedId, primaryId);

  const newEmail = uniqueEmail('linked-updated');
  const resp = await EmailPassword.updateEmailOrPassword({
    recipeUserId: SuperTokens.convertToRecipeUserId(linkedId),
    email: newEmail,
  });
  assertEqual(resp.status, 'OK', 'updateEmail on linked user');

  // Verify through primary user view
  const primary = await getUser(primaryId);
  assertNotNull(primary, 'primary should exist');
  const linkedMethod = primary!.loginMethods.find(
    (lm: any) => lm.recipeUserId.getAsString() === linkedId
  );
  assertNotNull(linkedMethod, 'linked method should be visible in primary');
  assertEqual(linkedMethod!.email, newEmail, 'linked email should be updated');
});

test('EP updateEmail rejects duplicate email within same tenant', async () => {
  const existingEmail = uniqueEmail('existing');
  await createEpUser(existingEmail);

  const { userId: user2 } = await createEpUser();

  const resp = await EmailPassword.updateEmailOrPassword({
    recipeUserId: SuperTokens.convertToRecipeUserId(user2),
    email: existingEmail,
  });
  assertEqual(resp.status, 'EMAIL_ALREADY_EXISTS_ERROR', 'should reject duplicate email');
});

test('EP updateEmail with email conflicting with another primary user', async () => {
  const { userId: primary1, email: email1 } = await createEpUser();
  await makePrimary(primary1);

  const { userId: primary2 } = await createEpUser();
  await makePrimary(primary2);

  const { userId: linkedToP2 } = await createEpUser();
  await linkUsers(linkedToP2, primary2);

  // Try to update linked user's email to conflict with primary1's email
  const resp = await EmailPassword.updateEmailOrPassword({
    recipeUserId: SuperTokens.convertToRecipeUserId(linkedToP2),
    email: email1,
  });

  // Should fail — email already associated with primary1. Core returns
  // EMAIL_CHANGE_NOT_ALLOWED_ERROR for linked users whose new email would
  // conflict across primary groups; EMAIL_ALREADY_EXISTS_ERROR for flat tenant
  // conflicts. Either is an acceptable rejection.
  assert(
    resp.status === 'EMAIL_ALREADY_EXISTS_ERROR' ||
      resp.status === 'EMAIL_CHANGE_NOT_ALLOWED_ERROR',
    `should reject conflicting email, got ${resp.status}`
  );
});

// ── ThirdParty email updates (via signInUp) ─────────────────────────────────

test('TP signInUp with new email updates the email for existing TP user', async () => {
  const { userId, tpUserId } = await createTpUser('google');

  const newEmail = uniqueEmail('tp-new');
  const resp = await ThirdParty.manuallyCreateOrUpdateUser(
    'public',
    'google',
    tpUserId,
    newEmail,
    false
  );

  assertEqual(resp.status, 'OK', 'TP signInUp status');
  if (resp.status !== 'OK') throw new Error('unreachable');
  assert(!resp.createdNewRecipeUser, 'should not create new user');

  const user = await getUser(userId);
  assertNotNull(user, 'user should exist');
  const tpMethod = user!.loginMethods.find((lm: any) => lm.recipeId === 'thirdparty');
  assertNotNull(tpMethod, 'should have TP login method');
  assertEqual(tpMethod!.email, newEmail, 'TP email should be updated');
});

test('TP signInUp on linked TP user updates email visible through primary', async () => {
  const { userId: primaryId } = await createEpUser();
  await makePrimary(primaryId);

  const { userId: tpId, tpUserId } = await createTpUser('google');
  await linkUsers(tpId, primaryId);

  const newEmail = uniqueEmail('tp-linked-new');
  await ThirdParty.manuallyCreateOrUpdateUser('public', 'google', tpUserId, newEmail, false);

  const primary = await getUser(primaryId);
  assertNotNull(primary, 'primary should exist');
  const tpMethod = primary!.loginMethods.find(
    (lm: any) => lm.recipeUserId.getAsString() === tpId
  );
  assertNotNull(tpMethod, 'TP method should be visible in primary');
  assertEqual(tpMethod!.email, newEmail, 'TP email should be updated in primary view');
});

// ── Password updates ────────────────────────────────────────────────────────

test('EP updatePassword works for standalone user', async () => {
  const { userId, email } = await createEpUser();

  const resp = await EmailPassword.updateEmailOrPassword({
    recipeUserId: SuperTokens.convertToRecipeUserId(userId),
    password: 'NewPassword456!',
  });
  assertEqual(resp.status, 'OK', 'updatePassword status');

  // Verify by signing in with new password
  const signInResp = await EmailPassword.signIn('public', email, 'NewPassword456!');
  assertEqual(signInResp.status, 'OK', 'signIn with new password');
});

test('EP updatePassword works for linked user', async () => {
  const { userId: primaryId } = await createEpUser();
  await makePrimary(primaryId);

  const linkedEmail = uniqueEmail('linked-pw');
  const { userId: linkedId } = await createEpUser(linkedEmail);
  await linkUsers(linkedId, primaryId);

  const resp = await EmailPassword.updateEmailOrPassword({
    recipeUserId: SuperTokens.convertToRecipeUserId(linkedId),
    password: 'UpdatedLinkedPw789!',
  });
  assertEqual(resp.status, 'OK', 'updatePassword on linked user');

  // Verify sign-in resolves to primary
  const signInResp = await EmailPassword.signIn('public', linkedEmail, 'UpdatedLinkedPw789!');
  assertEqual(signInResp.status, 'OK', 'signIn with updated linked password');
  if (signInResp.status !== 'OK') throw new Error('unreachable');
  assertEqual(signInResp.user.id, primaryId, 'signIn should resolve to primary');
});
