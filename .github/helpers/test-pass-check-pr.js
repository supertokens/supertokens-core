const { default: axios } = require("axios");

let thisRunId = process.env.RUN_ID;
thisRunId = thisRunId.trim();
console.log("Hi from node!!", thisRunId);
const githubURL = `https://api.github.com/repos/${process.env.REPO}/actions/runs?branch=${process.env.BRANCH}`

axios.get(githubURL).then(result => {
    let data = result.data;
    let passed = false;
    let currentSHA = "";

    data.workflow_runs.forEach(run => {
        if ((run.id + "") === thisRunId) {
            currentSHA = run.head_sha;
            console.log("MATCHED!!!", run);
        } else {
            console.log("not matched", run.id);
        }
    });

    console.log(currentSHA);

    process.exit(passed ? 0 : 1);
})