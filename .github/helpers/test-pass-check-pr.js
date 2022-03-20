const { default: axios } = require("axios");

const readmeInstrsLink = "https://github.com/supertokens/supertokens-core/blob/master/CONTRIBUTING.md#using-github-actions";
let thisRunId = process.env.RUN_ID;

// this is an auto generated token for this action
// using which the API rate limit is 5000 requests / hour
let gitHubToken = process.env.GITHUB_TOKEN;
console.log(gitHubToken);
thisRunId = thisRunId.trim();

function doJob() {
    console.log("Checking job status...");
    axios.get(`https://api.github.com/repos/${process.env.REPO}/actions/runs?branch=${process.env.BRANCH}`, {
        headers: {
            'Authorization': `token ${gitHubToken}`
        }
    }).then(async result => {
        let data = result.data;
        let currentSHA = undefined;

        data.workflow_runs.forEach(run => {
            if ((run.id + "") === thisRunId) {
                currentSHA = run.head_sha;
            }
        });

        let foundActiveJob = false;
        let success = false;
        let failed = false;
        if (currentSHA !== undefined) {
            for (let i = 0; i < data.workflow_runs.length; i++) {
                let run = data.workflow_runs[i];
                if (run.head_sha === currentSHA) {
                    if (run.name === "Run tests") {
                        if (run.conclusion === "success") {
                            success = true;
                        } else if (run.conclusion === "failure") {
                            failed = true;
                        }
                        if (run.status === "in_progress") {
                            foundActiveJob = true;
                        }
                    }
                }
            }
        }

        if (success) {
            console.log("Success!");
            process.exit(0);
            return;
        }

        if (foundActiveJob) {
            console.log("Waiting for job to finish...");
            return setTimeout(doJob, 30000) // try again after 30 seconds.
        }

        if (failed) {
            console.log("Test job failed. Exiting... Please rerun this job manually when you run the test job again...");
            process.exit(1);
            return;
        }

        console.log("You need to trigger the \"Run tests\" github action and make that succeed.\nSee " + readmeInstrsLink);
        setTimeout(doJob, 30000);
    }).catch((e) => {
        console.log(e);
        console.log("Error thrown.. waiting for 1 min and trying again.");
        setTimeout(doJob, 60000) // try again after 1 min.
    })
}

doJob();