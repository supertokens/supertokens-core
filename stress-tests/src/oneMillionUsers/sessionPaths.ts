import SuperTokens from 'supertokens-node';
import Session from 'supertokens-node/recipe/session';
import { randomUUID } from 'crypto';

import { measureTime, runStep } from '../common/utils';

/**
 * Session read paths at scale.
 *
 * The suite creates a million users and a session per user, and then never
 * exercised what those sessions are for: there was no verify, no refresh and no
 * session-handle lookup among the measured steps. Session verify and refresh
 * are the highest-volume endpoints in production, and both have production
 * evidence behind them —
 *
 *   - refresh is where the observed connection-pool starvation shows up
 *     (persistent 5.0s Hikari `connectionTimeout` 500s on one tenant), and
 *   - the session-handle lookup is the shape of the unbounded-scan regression
 *     that produced multi-second bursts on a miss path (C2).
 *
 * All four steps are O(1) by contract: a session is addressed by token or by
 * user id, so none of them may get slower as the user table grows from 100k to
 * 1M. That is the whole assertion — the absolute numbers are small and
 * uninteresting, the *ratio* is the regression guard, and an O(1) step that
 * starts scaling with n is exactly the defect class this file exists to catch.
 *
 * Fixtures are created fresh on each pass (see the contract in readPaths.ts):
 * refresh rotates its token, so verify and refresh each get their own session.
 * The miss-path lookup uses a random user id and needs no fixture at all.
 */
export const measureSessionPaths = async (): Promise<void> => {
  console.log('\n10. Measuring session read paths');

  // Pick a real user from the current dataset to own the fixture sessions.
  // Outside any measureTime: this is setup, not a measured path.
  let recipeUserId: string | undefined;
  let primaryUserId: string | undefined;
  await runStep(async () => {
    const page = await SuperTokens.getUsersNewestFirst({ tenantId: 'public' });
    const user = page.users[0];
    if (!user) {
      throw new Error('no users in the public tenant to build session fixtures from');
    }
    primaryUserId = user.id;
    recipeUserId = user.loginMethods[0]!.recipeUserId.getAsString();
    console.log(`    Session fixture user: ${primaryUserId}`);
  });
  if (!recipeUserId || !primaryUserId) {
    // The fixture step already recorded itself as failed; skip the rest of the
    // section rather than reporting four more failures for the same cause.
    return;
  }
  const rid = SuperTokens.convertToRecipeUserId(recipeUserId);

  // Anti-CSRF is disabled on the fixtures so verify/refresh need no extra token
  // and measure the storage path rather than header plumbing.
  const newSession = () =>
    Session.createNewSessionWithoutRequestResponse('public', rid, undefined, undefined, true);

  let accessToken: string | undefined;
  await runStep(async () => {
    accessToken = (await newSession()).getAccessToken();
  });
  if (accessToken) {
    await runStep(() =>
      measureTime('Session verify (access token)', async () => {
        const session = await Session.getSessionWithoutRequestResponse(accessToken!, undefined, {
          sessionRequired: true,
        });
        console.log(`    Verified session handle: ${session.getHandle()}`);
      })
    );
  }

  // Refresh consumes and rotates its refresh token, so it gets its own session.
  let refreshToken: string | undefined;
  await runStep(async () => {
    refreshToken = (await newSession()).getAllSessionTokensDangerously().refreshToken;
  });
  if (refreshToken) {
    await runStep(() =>
      measureTime('Session refresh (refresh token)', async () => {
        const refreshed = await Session.refreshSessionWithoutRequestResponse(refreshToken!, true);
        console.log(`    Refreshed session handle: ${refreshed.getHandle()}`);
      })
    );
  }

  // Handle lookup, hit path: the fixture user has at least the two sessions
  // created above plus its seeded one.
  await runStep(() =>
    measureTime('Session handles for user', async () => {
      const handles = await Session.getAllSessionHandlesForUser(primaryUserId!);
      console.log(`    Session handles for fixture user: ${handles.length}`);
    })
  );

  // Handle lookup, miss path — the C2 shape. A lookup for a user id that owns
  // no sessions must still be an indexed point read; if this one starts scaling
  // with the dataset while the hit path stays flat, the miss path is scanning.
  await runStep(() =>
    measureTime('Session handles for user (miss)', async () => {
      const handles = await Session.getAllSessionHandlesForUser(randomUUID());
      if (handles.length !== 0) {
        throw new Error(`expected no handles for a random user id, got ${handles.length}`);
      }
    })
  );
};
