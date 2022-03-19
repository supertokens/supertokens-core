const { default: axios } = require("axios");

console.log("Hi from node!", process.env.COMMIT_HASH);
const currentCommitHash = process.env.COMMIT_HASH;
const githubURL = `https://api.github.com/repos/${process.env.REPO}/actions/runs?branch=${process.env.BRANCH}`

axios.get(githubURL).then(result => {
    let data = result.data;
    let passed = false;

    data.workflow_runs.forEach(run => {
        if (run.head_sha === currentCommitHash) {
            console.log(run);
        } else {
            console.log("not matched", run.head_sha, currentCommitHash)
        }
    });

    process.exitCode(passed ? 0 : 1);
})