import http.client
import json
import time
import os
import sys


REPO = "supertokens/supertokens-core"
SHA = os.environ.get("GITHUB_SHA")
NAME = os.environ.get("WORKFLOW_NAME", "Publish Dev Docker Image")

st = time.time()

def get_latest_actions():
    conn = http.client.HTTPSConnection("api.github.com")
    url = f"/repos/{REPO}/actions/runs"
    headers = {"User-Agent": "Python-http.client"}
    conn.request("GET", url, headers=headers)
    response = conn.getresponse()

    if response.status == 200:
        data = response.read()
        runs = json.loads(data)['workflow_runs']
        found = False
        for run in runs:
            if run['head_sha'] == SHA and run['name'] == NAME:
                found = True
                break

        if not found:
            print("No matching workflow run found.")
            sys.exit(1)

        if run["status"] == "completed":
            if run["conclusion"] == "success":
                print("Workflow completed successfully.")
                return True
            else:
                print(f"Workflow failed with conclusion: {run['conclusion']}")
                sys.exit(1)
    else:
        print(f"Failed to fetch workflow runs: {response.status} {response.reason}")
        sys.exit(1)

    return False

time.sleep(30)  # Wait for 30 seconds before checking


while not get_latest_actions():
    print("Waiting for the latest actions to complete...")
    time.sleep(10)
    if time.time() - st > 600:
        print("Timed out waiting for the latest actions.")
        sys.exit(1)
