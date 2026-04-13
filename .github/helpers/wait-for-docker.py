import http.client
import json
import time
import os
import sys

REPO = "supertokens/supertokens-core"
# COMMIT_SHA is set explicitly by the workflow step: for PR events it is the
# branch HEAD SHA (github.event.pull_request.head.sha); for push events it
# equals GITHUB_SHA.  We fall back to GITHUB_SHA so the script stays usable
# when invoked outside of that workflow step.
SHA = os.environ.get("COMMIT_SHA") or os.environ.get("GITHUB_SHA")
NAME = os.environ.get("WORKFLOW_NAME", "Publish Dev Docker Image")
TOKEN = os.environ.get("GITHUB_TOKEN", "")

MAX_TRIES = 10
POLL_INTERVAL = 15


def fetch_run_for_sha():
    """Return the first workflow run matching SHA+NAME, or None if not found yet."""
    conn = http.client.HTTPSConnection("api.github.com")
    url = f"/repos/{REPO}/actions/runs?per_page=100"
    headers = {"User-Agent": "Python-http.client"}
    if TOKEN:
        headers["Authorization"] = f"Bearer {TOKEN}"
    conn.request("GET", url, headers=headers)
    response = conn.getresponse()

    if response.status != 200:
        print(f"Failed to fetch workflow runs: {response.status} {response.reason}")
        return None

    runs = json.loads(response.read()).get("workflow_runs", [])
    for run in runs:
        if run["head_sha"] == SHA and run["name"] == NAME:
            return run
    return None


print(f"Waiting for workflow '{NAME}' triggered by commit {SHA} ...")

# Give GitHub time to create the run before the first poll.
time.sleep(30)

for attempt in range(1, MAX_TRIES + 1):
    run = fetch_run_for_sha()

    if run is None:
        print(f"No matching workflow run found (attempt {attempt}/{MAX_TRIES}).")
        if attempt == MAX_TRIES:
            print("Giving up.")
            sys.exit(1)
        time.sleep(POLL_INTERVAL)
        continue

    status = run["status"]
    conclusion = run.get("conclusion")

    if status == "completed":
        if conclusion == "success":
            print("Workflow completed successfully.")
            sys.exit(0)
        else:
            print(f"Workflow failed with conclusion: {conclusion}")
            sys.exit(1)

    print(f"Workflow status: '{status}' (attempt {attempt}/{MAX_TRIES}), polling again in {POLL_INTERVAL}s ...")
    if attempt == MAX_TRIES:
        print("Giving up.")
        sys.exit(1)
    time.sleep(POLL_INTERVAL)
