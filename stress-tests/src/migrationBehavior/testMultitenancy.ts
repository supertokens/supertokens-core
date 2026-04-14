/**
 * REVIEW-007: Multitenancy behavioral equivalence tests.
 *
 * Verifies tenant association and email conflict detection across tenants
 * behaves identically in all migration modes.
 */

import SuperTokens from 'supertokens-node';
import AccountLinking from 'supertokens-node/recipe/accountlinking';
import EmailPassword from 'supertokens-node/recipe/emailpassword';
import Multitenancy from 'supertokens-node/recipe/multitenancy';

import {
  test,
  assert,
  assertEqual,
  assertNotNull,
  createEpUser,
  makePrimary,
  linkUsers,
  getUser,
  uniqueEmail,
  coreApiRequest,
} from './setup';

// ── Helper: create a tenant ─────────────────────────────────────────────────

async function ensureTenant(tenantId: string): Promise<void> {
  try {
    await Multitenancy.createOrUpdateTenant(tenantId, {
      firstFactors: ['emailpassword', 'thirdparty', 'otp-email', 'otp-phone'],
    });
  } catch {
    // Tenant may already exist
  }
}

// ── Tenant association tests ────────────────────────────────────────────────

test('user created in public tenant is associated with public', async () => {
  const { userId } = await createEpUser();
  const user = await getUser(userId);
  assertNotNull(user, 'user should exist');

  const tenantIds = user!.loginMethods[0]?.tenantIds ?? [];
  assert(tenantIds.includes('public'), 'user should be in public tenant');
});

test('user can be associated with a second tenant', async () => {
  await ensureTenant('tenant-assoc');

  const { userId } = await createEpUser();
  await Multitenancy.associateUserToTenant('tenant-assoc', SuperTokens.convertToRecipeUserId(userId));

  const user = await getUser(userId);
  assertNotNull(user, 'user should exist');

  const tenantIds = user!.loginMethods[0]?.tenantIds ?? [];
  assert(tenantIds.includes('public'), 'should still be in public');
  assert(tenantIds.includes('tenant-assoc'), 'should be in tenant-assoc');
});

test('associateUserToTenant rejects duplicate email in same tenant', async () => {
  await ensureTenant('tenant-dup');

  const email = uniqueEmail('dup-tenant');
  const { userId: user1 } = await createEpUser(email);
  await Multitenancy.associateUserToTenant(
    'tenant-dup',
    SuperTokens.convertToRecipeUserId(user1)
  );

  // Create second user with same email in public
  // (different user, same email — allowed in public but not in tenant-dup if user1 is there)
  const { userId: user2 } = await createEpUser(email + '2');

  // Update user2 email to match
  await EmailPassword.updateEmailOrPassword({
    recipeUserId: SuperTokens.convertToRecipeUserId(user2),
    email: email,
  });

  // Try to associate user2 to tenant-dup where user1 already has that email
  const resp = await Multitenancy.associateUserToTenant(
    'tenant-dup',
    SuperTokens.convertToRecipeUserId(user2)
  );

  // Should fail because email already exists in that tenant
  assert(
    resp.status === 'UNKNOWN_USER_ID_ERROR' ||
      resp.status === 'EMAIL_ALREADY_EXISTS_ERROR' ||
      resp.status === 'ASSOCIATION_NOT_ALLOWED_ERROR',
    `Expected tenant conflict, got ${resp.status}`
  );
});

// ── Linking + tenant interaction ────────────────────────────────────────────

test('linked users share tenant associations through primary', async () => {
  const { userId: primaryId } = await createEpUser();
  await makePrimary(primaryId);

  const { userId: recipeId } = await createEpUser();
  await linkUsers(recipeId, primaryId);

  // Get primary user — should see both login methods
  const primary = await getUser(primaryId);
  assertNotNull(primary, 'primary should exist');
  assertEqual(primary!.loginMethods.length, 2, 'should have 2 methods');

  // Both methods should be in public tenant
  for (const lm of primary!.loginMethods) {
    assert(
      (lm.tenantIds ?? []).includes('public'),
      `method ${lm.recipeUserId} should be in public tenant`
    );
  }
});

test('disassociateUserFromTenant removes tenant from specific login method', async () => {
  await ensureTenant('tenant-disassoc');

  const { userId } = await createEpUser();
  await Multitenancy.associateUserToTenant(
    'tenant-disassoc',
    SuperTokens.convertToRecipeUserId(userId)
  );

  // Verify association
  let user = await getUser(userId);
  let tenantIds = user!.loginMethods[0]?.tenantIds ?? [];
  assert(tenantIds.includes('tenant-disassoc'), 'should be in tenant-disassoc');

  // Disassociate
  await Multitenancy.disassociateUserFromTenant(
    'tenant-disassoc',
    SuperTokens.convertToRecipeUserId(userId)
  );

  user = await getUser(userId);
  assertNotNull(user, 'user should still exist');
  tenantIds = user!.loginMethods[0]?.tenantIds ?? [];
  assert(!tenantIds.includes('tenant-disassoc'), 'should no longer be in tenant-disassoc');
  assert(tenantIds.includes('public'), 'should still be in public');
});
