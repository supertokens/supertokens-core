/**
 * Pagination full-walk driver with non-termination and completeness guards.
 *
 * The 1M-user suite walks the entire paginated user list in both directions to
 * measure the scale-sensitive read path. Two failure modes were observed in the
 * field and neither was caught by the harness (see issue #1343):
 *
 *   - a walk that never terminates (a pagination token that keeps pointing at
 *     the same slice), which previously only died at the 6h job timeout; and
 *   - a walk that silently returns a fraction of the dataset and stops, which
 *     was reported as a fast, successful measurement.
 *
 * This module keeps the walk logic free of any SuperTokens/network coupling so
 * it can be unit-tested with a fake page source, and turns both failure modes
 * into hard, immediate errors that name where and why the walk went wrong.
 */

/** Minimal structural shape of a page returned by the paginated user list. */
export interface UserPage {
  users: { loginMethods: unknown[] }[];
  nextPaginationToken?: string;
}

/** Fetches one page given the previous page's pagination token (undefined = first page). */
export type GetPage = (paginationToken?: string) => Promise<UserPage>;

export interface WalkResult {
  userCount: number;
  loginMethodCount: number;
  pages: number;
}

/** Visited count must be within this fraction of the expected population. */
export const WALK_COMPLETENESS_TOLERANCE = 0.01;
/** Log a progress line every this many pages. */
export const WALK_PROGRESS_EVERY_PAGES = 50;
/** Abort once pages exceed this multiple of the expected page count. */
export const WALK_MAX_PAGE_FACTOR = 2;

/**
 * Walk every page of a paginated user list, guarding against non-termination
 * and silent truncation.
 *
 * @param label   Human-readable walk label, e.g. "newest first" — used in logs
 *                and error messages so a failure names the exact walk.
 * @param getPage Page source; called with `undefined` for the first page and
 *                with the previous page's `nextPaginationToken` thereafter.
 * @param expectedUsers Population the walk is expected to visit (the tenant's
 *                current user count). The walk must visit within
 *                WALK_COMPLETENESS_TOLERANCE of this, and may not run for more
 *                than WALK_MAX_PAGE_FACTOR × the pages that many users implies.
 *
 * @throws if a pagination token repeats, if the page count runs away, or if the
 *         visited count is not within tolerance of `expectedUsers`.
 */
export const walkAllUsers = async (
  label: string,
  getPage: GetPage,
  expectedUsers: number
): Promise<WalkResult> => {
  let userCount = 0;
  let loginMethodCount = 0;
  let pages = 0;
  let paginationToken: string | undefined;
  let pageSize = 0;
  let maxPages = Number.POSITIVE_INFINITY;
  const seenTokens = new Set<string>();

  while (true) {
    const result = await getPage(paginationToken);
    pages++;
    for (const user of result.users) {
      userCount++;
      loginMethodCount += user.loginMethods.length;
    }

    // Derive the effective page size from the first page and, from it, the most
    // pages a complete walk could legitimately take.
    if (pages === 1) {
      pageSize = result.users.length || 1;
      maxPages = WALK_MAX_PAGE_FACTOR * Math.ceil(Math.max(expectedUsers, 1) / pageSize);
    }

    if (pages % WALK_PROGRESS_EVERY_PAGES === 0) {
      console.log(`    (${label}) progress: pages=${pages}, users=${userCount}`);
    }

    paginationToken = result.nextPaginationToken;
    if (paginationToken === undefined) break;

    // Non-termination guard 1: a repeated token means the walk is looping over a
    // slice it has already visited and will never reach the end.
    if (seenTokens.has(paginationToken)) {
      throw new Error(
        `Pagination full walk (${label}) repeated pagination token after ${pages} page(s) ` +
          `(users so far ${userCount}); the walk is looping and will not terminate.`
      );
    }
    seenTokens.add(paginationToken);

    // Non-termination guard 2: far more pages than the dataset can justify —
    // e.g. a token that advances by one user per page, or duplicate pages.
    if (pages >= maxPages) {
      throw new Error(
        `Pagination full walk (${label}) exceeded ${maxPages} pages ` +
          `(${WALK_MAX_PAGE_FACTOR}× the ~${Math.ceil(Math.max(expectedUsers, 1) / pageSize)} pages ` +
          `expected for ${expectedUsers} users at page size ${pageSize}); aborting non-terminating walk.`
      );
    }
  }

  console.log(
    `    (${label}) users=${userCount}, loginMethods=${loginMethodCount}, pages=${pages}`
  );

  // Completeness guard: the walk must have visited essentially the whole
  // dataset. Silent truncation (returning a fraction of the users and stopping)
  // becomes a hard failure here instead of a green run with a bogus fast number.
  // Compared against the tenant's current user count rather than a seed-derived
  // constant: pagination and the count endpoint must agree on the population,
  // and that comparison is robust to account-linking math.
  if (expectedUsers > 0) {
    const tolerated = Math.ceil(expectedUsers * WALK_COMPLETENESS_TOLERANCE);
    if (Math.abs(userCount - expectedUsers) > tolerated) {
      throw new Error(
        `Pagination full walk (${label}) visited ${userCount} users but the tenant reports ` +
          `${expectedUsers} (tolerance ±${tolerated}, ${WALK_COMPLETENESS_TOLERANCE * 100}%); ` +
          `the walk did not cover the whole dataset.`
      );
    }
  }

  return { userCount, loginMethodCount, pages };
};
