const { default: axios } = require("axios");

let thisRunId = process.env.RUN_ID;
thisRunId = thisRunId.trim();

axios.get(`https://api.github.com/repos/${process.env.REPO}/actions/runs?branch=${process.env.BRANCH}`).then(async result => {
    let data = result.data;
    let passed = false;
    let currentSHA = undefined;

    data.workflow_runs.forEach(run => {
        if ((run.id + "") === thisRunId) {
            currentSHA = run.head_sha;
        }
    });

    if (currentSHA !== undefined) {
        for (let i = 0; i < data.workflow_runs.length; i++) {
            console.log("I!!!!!!", i);
            let run = data.workflow_runs[i];
            if (run.head_sha === currentSHA) {
                // here we have all the jobs that have run on this commit.
                let workflow_id = run.workflow_id;
                let workflow = await axios.get(`https://api.github.com/repos/${process.env.REPO}/actions/workflows?workflow_id=${workflow_id}`);
                let workflowData = workflow.data;
                console.log(workflowData);
            }
        }
    }

    process.exit(passed ? 0 : 1);
})