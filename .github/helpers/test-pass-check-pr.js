const { default: axios } = require("axios");

const readmeInstrsLink = "https://github.com/supertokens/supertokens-core/blob/master/CONTRIBUTING.md#using-github-actions";
let thisRunId = process.env.RUN_ID;
thisRunId = thisRunId.trim();

function doJob() {
    console.log("Trying to check test job status...");
    axios.get(`https://api.github.com/repos/${process.env.REPO}/actions/runs?branch=${process.env.BRANCH}`).then(async result => {
        let data = result.data;
        let currentSHA = undefined;

        data.workflow_runs.forEach(run => {
            if ((run.id + "") === thisRunId) {
                currentSHA = run.head_sha;
            }
        });

        let foundJob = false;
        if (currentSHA !== undefined) {
            for (let i = 0; i < data.workflow_runs.length; i++) {
                let run = data.workflow_runs[i];
                if (run.head_sha === currentSHA) {
                    // here we have all the jobs that have run on this commit.
                    let workflow_id = run.workflow_id;
                    let workflow = await axios.get(`https://api.github.com/repos/${process.env.REPO}/actions/workflows/${workflow_id}`);
                    let workflowData = workflow.data;
                    if (workflowData.path === ".github/workflows/tests.yml") {
                        foundJob = true;
                        if (run.conclusion === "success") {
                            console.log("Success!");
                            process.exit(0);
                            break;
                        } else if (run.conclusion === "failure") {
                            console.log("Test job failed... exiting");
                            process.exit(1);
                        }
                    }
                }
            }
        }

        if (!foundJob) {
            console.log("You need to trigger the \"Run tests\" github action and make that succeed.\nSee " + readmeInstrsLink);
        } else {
            console.log("Waiting for test job to finish...");
        }
        setTimeout(doJob, 30000) // try again after 30 seconds.
    }).catch(() => {
        console.log("Error thrown.. waiting for 1 min and trying again.");
        setTimeout(doJob, 60000) // try again after 1 min.
    })
}

doJob();