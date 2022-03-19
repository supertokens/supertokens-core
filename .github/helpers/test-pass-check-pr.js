const { default: axios } = require("axios");

let thisRunId = process.env.RUN_ID;
thisRunId = thisRunId.trim();
console.log("Hi from node!!", thisRunId);
const githubURL = `https://api.github.com/repos/${process.env.REPO}/actions/runs?branch=${process.env.BRANCH}`

axios.get(githubURL).then(result => {
    let data = result.data;
    let passed = false;
    let currentSHA = undefined;

    data.workflow_runs.forEach(run => {
        if ((run.id + "") === thisRunId) {
            currentSHA = run.head_sha;
        }
    });

    if (currentSHA !== undefined) {
        data.workflow_runs.forEach(run => {
            if (run.head_sha === currentSHA) {
                console.log(run.id);
            }
        });
    }

    process.exit(passed ? 0 : 1);
})