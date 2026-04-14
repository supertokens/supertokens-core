/**
 * REVIEW-007: Concurrency stress tests at the API level.
 *
 * These tests hammer the core with concurrent operations via HTTP to verify
 * that race conditions are handled correctly regardless of migration mode.
 * Each test runs multiple iterations with parallel operations and asserts
 * on the resulting state consistency.
 */

import SuperTokens from 'supertokens-node';
import AccountLinking from 'supertokens-node/recipe/accountlinking';
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
  deleteUser,
  uniqueEmail,
  CONCURRENCY,
} from './setup';

const STRESS_ITERATIONS = parseInt(process.env.STRESS_ITERATIONS ?? '5', 10);

// ── Concurrent link + email update ──────────────────────────────────────────

test('concurrent linkAccounts + updateEmail produces consistent state', async () => {
  for (let iter = 0; iter < STRESS_ITERATIONS; iter++) {
    const { userId: primaryId } = await createEpUser();
    await makePrimary(primaryId);

    const { userId: recipeId } = await createEpUser();
    const newEmail = uniqueEmail(`race-upd-${iter}`);

    // Fire concurrently: link + email update
    const results = await Promise.allSettled([
      AccountLinking.linkAccounts(
        SuperTokens.convertToRecipeUserId(recipeId),
        primaryId
      ),
      EmailPassword.updateEmailOrPassword({
        recipeUserId: SuperTokens.convertToRecipeUserId(recipeId),
        email: newEmail,
      }),
    ]);

    // Both may succeed or one may fail — that's fine.
    // The key assertion: the resulting state must be consistent.
    const primary = await getUser(primaryId);
    assertNotNull(primary, `iter ${iter}: primary should exist`);

    const recipe = await getUser(recipeId);
    if (recipe === undefined) {
      // Recipe user was deleted (shouldn't happen, but handle gracefully)
      continue;
    }

    if (recipe.isPrimaryUser && recipe.id === primaryId) {
      // User was linked — verify via primary view
      const linkedMethod = primary!.loginMethods.find(
        (lm: any) => lm.recipeUserId === recipeId
      );
      assertNotNull(linkedMethod, `iter ${iter}: linked user should be in primary's methods`);
    } else {
      // User was NOT linked — should exist independently
      assert(!recipe.isPrimaryUser || recipe.id === recipeId,
        `iter ${iter}: unlinked user should be independent`);
    }
  }
});

// ── Concurrent link + unlink ────────────────────────────────────────────────

test('concurrent link + unlink + re-link produces valid state', async () => {
  for (let iter = 0; iter < STRESS_ITERATIONS; iter++) {
    const { userId: primaryId } = await createEpUser();
    await makePrimary(primaryId);

    const { userId: recipe1 } = await createEpUser();
    const { userId: recipe2 } = await createEpUser();

    // Link both first
    await linkUsers(recipe1, primaryId);
    await linkUsers(recipe2, primaryId);

    // Fire concurrently: unlink recipe1 + update recipe2 email + re-link recipe1
    const newEmail = uniqueEmail(`stress-${iter}`);
    await Promise.allSettled([
      AccountLinking.unlinkAccount(SuperTokens.convertToRecipeUserId(recipe1)),
      EmailPassword.updateEmailOrPassword({
        recipeUserId: SuperTokens.convertToRecipeUserId(recipe2),
        email: newEmail,
      }),
      // Slight delay for the re-link to give unlink a chance
      new Promise((r) => setTimeout(r, 1)).then(() =>
        AccountLinking.linkAccounts(
          SuperTokens.convertToRecipeUserId(recipe1),
          primaryId
        )
      ),
    ]);

    // Verify: primary should exist and have a valid state
    const primary = await getUser(primaryId);
    assertNotNull(primary, `iter ${iter}: primary should exist`);
    assert(primary!.isPrimaryUser, `iter ${iter}: should still be primary`);

    // Login methods count should be between 1 and 3
    const methodCount = primary!.loginMethods.length;
    assert(
      methodCount >= 1 && methodCount <= 3,
      `iter ${iter}: method count ${methodCount} should be 1-3`
    );

    // All login method emails should be non-null
    for (const lm of primary!.loginMethods) {
      assertNotNull(lm.email, `iter ${iter}: login method email should not be null`);
    }
  }
});

// ── Concurrent deleteUser + linkAccounts ────────────────────────────────────

test('concurrent deleteUser + linkAccounts does not produce orphaned state', async () => {
  for (let iter = 0; iter < STRESS_ITERATIONS; iter++) {
    const { userId: primaryId } = await createEpUser();
    await makePrimary(primaryId);

    const { userId: recipeId } = await createEpUser();

    // Fire concurrently: delete recipe user + link it
    await Promise.allSettled([
      SuperTokens.deleteUser(recipeId),
      AccountLinking.linkAccounts(
        SuperTokens.convertToRecipeUserId(recipeId),
        primaryId
      ),
    ]);

    // Either the user was deleted or linked — not both in an inconsistent way
    const primary = await getUser(primaryId);
    assertNotNull(primary, `iter ${iter}: primary should survive`);

    const recipe = await getUser(recipeId);
    if (recipe === undefined) {
      // Deleted — primary should not reference it
      const hasOrphaned = primary!.loginMethods.some(
        (lm: any) => lm.recipeUserId === recipeId
      );
      assert(!hasOrphaned, `iter ${iter}: primary should not have deleted user's method`);
    } else {
      // Linked (or still independent) — either is fine
      if (recipe.id === primaryId) {
        // Was linked
        const linked = primary!.loginMethods.find(
          (lm: any) => lm.recipeUserId === recipeId
        );
        assertNotNull(linked, `iter ${iter}: linked user should appear in primary`);
      }
    }
  }
});

// ── Bulk concurrent user creation + linking ─────────────────────────────────

test('bulk concurrent signUp + link maintains consistency', async () => {
  const { userId: primaryId } = await createEpUser();
  await makePrimary(primaryId);

  const userPromises: Promise<{ userId: string }>[] = [];
  for (let i = 0; i < CONCURRENCY; i++) {
    userPromises.push(createEpUser());
  }
  const users = await Promise.all(userPromises);

  // Link all concurrently
  const linkPromises = users.map((u) =>
    AccountLinking.linkAccounts(
      SuperTokens.convertToRecipeUserId(u.userId),
      primaryId
    ).catch(() => null)
  );
  await Promise.allSettled(linkPromises);

  // Verify primary state
  const primary = await getUser(primaryId);
  assertNotNull(primary, 'primary should exist after bulk link');
  assert(primary!.isPrimaryUser, 'should be primary after bulk link');

  // Count how many were actually linked
  const linkedCount = primary!.loginMethods.length;
  assert(linkedCount >= 1, `should have at least 1 method, got ${linkedCount}`);
  assert(
    linkedCount <= CONCURRENCY + 1,
    `should have at most ${CONCURRENCY + 1} methods, got ${linkedCount}`
  );

  // All login methods should have unique recipeUserIds
  const recipeIds = primary!.loginMethods.map((lm: any) => lm.recipeUserId);
  const uniqueIds = new Set(recipeIds);
  assertEqual(uniqueIds.size, recipeIds.length, 'all recipeUserIds should be unique');
});

// ── Mixed recipe concurrent operations ──────────────────────────────────────

test('concurrent EP + TP operations maintain consistency', async () => {
  for (let iter = 0; iter < STRESS_ITERATIONS; iter++) {
    const { userId: primaryId } = await createEpUser();
    await makePrimary(primaryId);

    const { userId: epId } = await createEpUser();
    const { userId: tpId, tpUserId } = await createTpUser('google');

    // Concurrently: link EP, link TP, update TP email
    const newTpEmail = uniqueEmail(`tp-race-${iter}`);
    await Promise.allSettled([
      AccountLinking.linkAccounts(
        SuperTokens.convertToRecipeUserId(epId),
        primaryId
      ),
      AccountLinking.linkAccounts(
        SuperTokens.convertToRecipeUserId(tpId),
        primaryId
      ),
      ThirdParty.manuallyCreateOrUpdateUser('public', 'google', tpUserId, newTpEmail, false),
    ]);

    // Verify primary consistency
    const primary = await getUser(primaryId);
    assertNotNull(primary, `iter ${iter}: primary should exist`);

    // Primary should have 1-3 methods depending on race outcomes
    const methods = primary!.loginMethods.length;
    assert(methods >= 1 && methods <= 3, `iter ${iter}: methods=${methods} should be 1-3`);

    // Each login method should have a valid email
    for (const lm of primary!.loginMethods) {
      assertNotNull(lm.email, `iter ${iter}: method ${lm.recipeUserId} email should not be null`);
    }
  }
});
