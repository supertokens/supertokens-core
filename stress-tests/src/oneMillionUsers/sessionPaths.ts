import SuperTokens from 'supertokens-node';
import Session from 'supertokens-node/recipe/session';
import { randomUUID } from 'crypto';

import { measureRepeated, runStep } from '../common/utils';

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
 *
 * All four are measured with `measureRepeated`: one call lands in the tens of
 * milliseconds, where the recorded number is mostly runner noise. Each step
 * varies its input per iteration — verify and the handle lookup rotate over a
 * fixture pool, refresh chains its own rotated token forward, and the miss path
 * draws a fresh random id — so the repeat measures the path rather than one
 * warm row.
 */
export const measureSessionPaths = async (): Promise<void> => {
  console.log('\n10. Measuring session read paths');

  // Pick real users from the current dataset to own the fixture sessions.
  // Outside any measured step: this is setup, not a measured path.
  //
  // A pool rather than a single user, because the repeated steps rotate over it
  // — measuring the same row a thousand times measures a warm cache. The pool
  // is capped so fixture creation stays a rounding error next to the run.
  const POOL_SIZE = 20;
  const primaryUserIds: string[] = [];
  const recipeUserIds: string[] = [];
  await runStep(async () => {
    const page = await SuperTokens.getUsersNewestFirst({ tenantId: 'public', limit: POOL_SIZE });
    for (const user of page.users) {
      primaryUserIds.push(user.id);
      recipeUserIds.push(user.loginMethods[0]!.recipeUserId.getAsString());
    }
    if (recipeUserIds.length === 0) {
      throw new Error('no users in the public tenant to build session fixtures from');
    }
    console.log(`    Session fixture users: ${recipeUserIds.length}`);
  });
  if (recipeUserIds.length === 0) {
    // The fixture step already recorded itself as failed; skip the rest of the
    // section rather than reporting four more failures for the same cause.
    return;
  }

  // Anti-CSRF is disabled on the fixtures so verify/refresh need no extra token
  // and measure the storage path rather than header plumbing.
  const newSession = (i: number) =>
    Session.createNewSessionWithoutRequestResponse(
      'public',
      SuperTokens.convertToRecipeUserId(recipeUserIds[i % recipeUserIds.length]!),
      undefined,
      undefined,
      true
    );

  // One access token per fixture user, so verify rotates over distinct sessions.
  const accessTokens: string[] = [];
  await runStep(async () => {
    for (let i = 0; i < recipeUserIds.length; i++) {
      accessTokens.push((await newSession(i)).getAccessToken());
    }
  });
  if (accessTokens.length > 0) {
    await runStep(() =>
      measureRepeated('Session verify (access token)', async (i) => {
        await Session.getSessionWithoutRequestResponse(
          accessTokens[Math.abs(i) % accessTokens.length]!,
          undefined,
          { sessionRequired: true }
        );
      })
    );
  }

  // Refresh consumes and rotates its refresh token, so it gets its own session
  // and each iteration feeds the rotated token into the next one. That is what
  // refresh actually does in production — a chain, not a replay — and it means
  // the repeat needs no fixture pool of its own.
  let refreshToken: string | undefined;
  await runStep(async () => {
    refreshToken = (await newSession(0)).getAllSessionTokensDangerously().refreshToken;
  });
  if (refreshToken) {
    await runStep(() =>
      measureRepeated('Session refresh (refresh token)', async () => {
        const refreshed = await Session.refreshSessionWithoutRequestResponse(refreshToken!, true);
        refreshToken = refreshed.getAllSessionTokensDangerously().refreshToken;
      })
    );
  }

  // Handle lookup, hit path: every fixture user owns at least its seeded session
  // plus the one created above.
  await runStep(() =>
    measureRepeated('Session handles for user', async (i) => {
      const handles = await Session.getAllSessionHandlesForUser(
        primaryUserIds[Math.abs(i) % primaryUserIds.length]!
      );
      if (handles.length === 0) {
        throw new Error('expected at least one handle for a fixture user');
      }
    })
  );

  // Handle lookup, miss path — the C2 shape. A lookup for a user id that owns
  // no sessions must still be an indexed point read; if this one starts scaling
  // with the dataset while the hit path stays flat, the miss path is scanning.
  await runStep(() =>
    measureRepeated('Session handles for user (miss)', async () => {
      const handles = await Session.getAllSessionHandlesForUser(randomUUID());
      if (handles.length !== 0) {
        throw new Error(`expected no handles for a random user id, got ${handles.length}`);
      }
    })
  );
};
