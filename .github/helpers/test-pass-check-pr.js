console.log("Hi from node!");
const currentCommitHash = process.env.COMMIT_HASH;
const githubURL = `https://api.github.com/repos/${process.env.REPO}/actions/runs?branch=${process.env.BRANCH}`

console.log(githubURL);