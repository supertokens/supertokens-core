#!/usr/bin/env python3
"""Pick the branches of the other repos for a CI run in a -security mirror.

Runs only in supertokens/supertokens-core-security (in place of get-core-dependencies-action,
which only knows the public repos) and supertokens/supertokens-postgresql-plugin-security.
Core needs plugin-interface and postgresql-plugin; postgresql-plugin needs plugin-interface and
core. For each dependency, from its private -security mirror:
  1. the branch with the same name as this run's branch, if there is one (a fix that spans
     repos uses the same branch name in each), otherwise
  2. the only branch whose name matches once version numbers are ignored (e.g. core
     integration/12.3-security-fixes and plugin-interface integration/10.1-security-fixes); if
     several match, none of them is used, otherwise
  3. the version branch: plugin-interface X.Y from pluginInterfaceSupported.json, and the newest
     X.Y branch of postgresql-plugin or core that supports that plugin interface version.
X.Y branches (pushes to release branches) always take step 3.

Env: BRANCH, GITHUB_REPOSITORY, GH_TOKEN (read access to the mirrors), GITHUB_OUTPUT.
Writes the outputs `branches` and `repos` (JSON; for core keyed like get-core-dependencies-action).
"""
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

PI_MIRROR = "supertokens/supertokens-plugin-interface-security"
# this repo -> (output key, mirror) of the dependency matched by plugin interface version
OTHER = {
    "supertokens/supertokens-core-security": ("postgresql", "supertokens/supertokens-postgresql-plugin-security"),
    "supertokens/supertokens-postgresql-plugin-security": ("core", "supertokens/supertokens-core-security"),
}
XY = re.compile(r"^\d+\.\d+$")
VERSION = re.compile(r"\d+\.\d+(\.\d+)?")


def api(path, raw=False):
    req = urllib.request.Request(
        "https://api.github.com/" + path,
        headers={"Authorization": "Bearer " + os.environ["GH_TOKEN"],
                 "Accept": "application/vnd.github.raw" if raw else "application/vnd.github+json"})
    with urllib.request.urlopen(req) as r:
        body = r.read()
    return body.decode() if raw else json.loads(body)


def branch_exists(repo, branch):
    try:
        api(f"repos/{repo}/branches/{urllib.parse.quote(branch, safe='')}")
        return True
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return False
        raise


def all_branches(repo):
    names, page = [], 1
    while True:
        batch = api(f"repos/{repo}/branches?per_page=100&page={page}")
        names += [b["name"] for b in batch]
        if len(batch) < 100:
            break
        page += 1
    return names


def xy_branches(repo):
    names = [n for n in all_branches(repo) if XY.match(n)]
    return sorted(names, key=lambda n: tuple(map(int, n.split("."))), reverse=True)


def same_name_branch(repo, branch):
    """Steps 1 and 2: the branch named like this run's branch, or None."""
    if branch_exists(repo, branch):
        return branch
    if not VERSION.search(branch):
        return None
    pattern = VERSION.sub("#", branch)
    matches = [n for n in all_branches(repo) if not XY.match(n) and VERSION.sub("#", n) == pattern]
    if len(matches) == 1:
        return matches[0]
    if matches:
        print(f"::warning::{repo}: several branches match '{branch}' ignoring versions "
              f"({', '.join(sorted(matches))}); using the version branch")
    return None


def supported_pi_version(repo, branch):
    try:
        return json.loads(api(f"repos/{repo}/contents/pluginInterfaceSupported.json?ref={branch}", raw=True))["versions"][0]
    except (urllib.error.HTTPError, ValueError, KeyError, IndexError):
        return None


def fail(message):
    print(f"::error::{message}")
    sys.exit(1)


def main():
    branch = os.environ["BRANCH"]
    key, other_mirror = OTHER[os.environ["GITHUB_REPOSITORY"]]
    with open("pluginInterfaceSupported.json") as f:
        pi_version = json.load(f)["versions"][0]
    same_name = not XY.match(branch)

    try:
        pi_branch = same_name_branch(PI_MIRROR, branch) if same_name else None
        if not pi_branch:
            if not branch_exists(PI_MIRROR, pi_version):
                fail(f"{PI_MIRROR} has neither branch '{branch}' nor version branch '{pi_version}'")
            pi_branch = pi_version

        other_branch = same_name_branch(other_mirror, branch) if same_name else None
        if not other_branch:
            other_branch = next(
                (b for b in xy_branches(other_mirror) if supported_pi_version(other_mirror, b) == pi_version), None)
            if other_branch is None:
                fail(f"{other_mirror} has no branch '{branch}' and no version branch for plugin interface "
                     f"{pi_version}")
    except urllib.error.HTTPError as e:
        fail(f"GitHub API {e.code} on {e.url}. Is EMBARGO_REPO_TOKEN set, with read access to the mirrors?")

    branches = {"plugin-interface": pi_branch, key: other_branch}
    repos = {"plugin-interface": PI_MIRROR, key: other_mirror}
    print(f"{branch} (plugin interface {pi_version}): {PI_MIRROR}@{pi_branch}, {other_mirror}@{other_branch}")
    with open(os.environ["GITHUB_OUTPUT"], "a") as out:
        out.write(f"branches={json.dumps(branches)}\nrepos={json.dumps(repos)}\n")


if __name__ == "__main__":
    main()
