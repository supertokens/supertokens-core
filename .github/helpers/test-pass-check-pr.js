const { default: axios } = require("axios");

console.log("Hi from node!!", process.env.COMMIT_HASH);
const thisJobId = process.env.JOB_ID;
const githubURL = `https://api.github.com/repos/${process.env.REPO}/actions/runs?branch=${process.env.BRANCH}`

axios.get(githubURL).then(result => {
    let data = result.data;
    let passed = false;
    let currentSHA = "";

    data.workflow_runs.forEach(run => {
        if (run.id === thisJobId) {
            console.log(run);
            currentSHA = run.head_sha;
        }
    });

    console.log(currentSHA);

    process.exitCode(passed ? 0 : 1);
})