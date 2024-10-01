# Contributing

We're so excited you're interested in helping with SuperTokens! We are happy to help you get started, even if you don't
have any previous open-source experience :blush:

## New to Open Source?

1. Take a look
   at [How to Contribute to an Open Source Project on GitHub](https://egghead.io/courses/how-to-contribute-to-an-open-source-project-on-github)
2. Go through
   the [SuperTokens Code of Conduct](https://github.com/supertokens/supertokens-core/blob/master/CODE_OF_CONDUCT.md)

## Where to ask Questions?

1. Check our [Github Issues](https://github.com/supertokens/supertokens-core/issues) to see if someone has already
   answered your question.
2. Join our community on [Discord](https://supertokens.io/discord) and feel free to ask us your questions

As you gain experience with SuperTokens, please help answer other people's questions! :pray:

## What to Work On?

You can get started by taking a look at our [Github issues](https://github.com/supertokens/supertokens-core/issues)  
If you find one that looks interesting and no one else is already working on it, comment in the issue that you are going
to work on it.

Please ask as many questions as you need, either directly in the issue or on [Discord](https://supertokens.io/discord).
We're happy to help!:raised_hands:

### Contributions that are ALWAYS welcome

1. More tests
2. Contributing to discussions that can be
   found [here](https://github.com/supertokens/supertokens-core/issues?q=is%3Aissue+is%3Aopen+label%3Adiscussions)
3. Improved error messages
4. Educational content like blogs, videos, courses

## Development Setup

### With Gitpod

1. Navigate to the [supertokens-root](https://github.com/supertokens/supertokens-root) repository
2. Click on the `Open in Gitpod` button

### Local Setup Prerequisites

- OS: Linux or macOS. Or if using Windows, you need to use [wsl2](https://docs.microsoft.com/en-us/windows/wsl/about).
- JDK: openjdk 15.0.1. Installation instructions for Mac and Linux can be found
  in [our wiki](https://github.com/supertokens/supertokens-core/wiki/Installing-OpenJDK-for-Mac-and-Linux)
- IDE: [IntelliJ](https://www.jetbrains.com/idea/download/)(recommended) or equivalent IDE

### Familiarize yourself with SuperTokens

1. [Architecture of SuperTokens](https://github.com/supertokens/supertokens-core/wiki/SuperTokens-Architecture)
2. [SuperTokens code and file structure overview](https://github.com/supertokens/supertokens-core/wiki/Code-and-file-structure-overview)
3. [Versioning methodology](https://github.com/supertokens/supertokens-core/wiki/Versioning,-git-and-releases)

### Project Setup

1. Fork the [supertokens-core](https://github.com/supertokens/supertokens-core) repository (**Skip this step if you are
   NOT modifying supertokens-core**)
2. `git clone https://github.com/supertokens/supertokens-root.git`
3. `cd supertokens-root`
4. Open the `modules.txt` file in an editor (**Skip this step if you are NOT modifying supertokens-core**):
    - The `modules.txt` file contains the core, plugin-interface, the type of plugin and their branches(versions)
    - By default the `master` branch is used but you can change the branch depending on which version you want to modify
    - The `sqlite-plugin` is used as the default plugin as it is an in-memory database and requires no setup
        - [core](https://github.com/supertokens/supertokens-core)
        - [plugin-interface](https://github.com/supertokens/supertokens-plugin-interface)
        - Check the repository branches by clicking on the links listed above, click the branch tab and check for all
          the available versions
    - Add your github `username` separated by a ',' after `core,master` in  `modules.txt`
    - If, for example, your github `username` is `helloworld` then modules.txt should look like...
      ```
      // put module name like module name,branch name,github username(if contributing with a forked repository) and then call ./loadModules script        
      core,master,helloworld  
      plugin-interface,master        
      sqlite-plugin,master
      ```

5. Run loadModules to clone the required repositories  
   `./loadModules`

## Modifying code

1. Open `supetokens-root` in your IDE
2. After gradle has imported all the dependencies you can start modifying the code

## Testing

### On your local machine

1. Navigate to the `supertokens-root` repository
2. Run all tests   
   `./startTestEnv`
3. If all tests pass the terminal should display

- core tests:  
  ![core tests passing](https://github.com/supertokens/supertokens-logo/blob/master/images/core-tests-passing.png)
- plugin tests:  
  ![plugin tests passing](https://github.com/supertokens/supertokens-logo/blob/master/images/plugin-tests-passing.png)

### Using github actions

1. Go to the supertokens-core repo on github (or your forked version of it).
2. Navigate to the Actions tab.
3. Find the action named "Run tests" and navigate to it.
4. Click on the "Run workflow" button.
5. Set the config variables in the drop down:
    - **supertokens-plugin-interface repo owner name**: If you have forked the supertokens-plugin-interface repo, then
      set the value of this to your github username.
    - **supertokens-plugin-interface repos branch name**: If the core version you are working on is compatible with a
      plugin-interface version that is not in the master branch, then set the correct branch name in this value.
6. Click on "Run workflow".

## Running the core manually

1. Run `startTestEnv --wait` in a terminal, and keep it running
2. Then open `supertokens-root` in another terminal and run `cp ./temp/config.yaml .`
3. Then run `java -classpath "./core/*:./plugin-interface/*:./ee/*" io.supertokens.Main ./ DEV`. This will start the
   core to listen on `http://localhost:3567`

## Pull Request

1. Before submitting a pull request make sure all tests have passed
2. Reference the relevant issue or pull request and give a clear description of changes/features added when submitting a
   pull request
3. Make sure the PR title follows [conventional commits](https://www.conventionalcommits.org/en/v1.0.0/) specification

## Install the supertokens CLI manually

1. Setup test env and keep it running
2. In `supertokens-root`, run `cp temp/config.yaml .`
3. On a different terminal, go to `supertokens-root` folder and
   run `java -classpath "./cli/*" io.supertokens.cli.Main true install`

## SuperTokens Community

SuperTokens is made possible by a passionate team and a strong community of developers. If you have any questions or
would like to get more involved in the SuperTokens community you can check out:

- [Github Issues](https://github.com/supertokens/supertokens-core/issues)
- [Discord](https://supertokens.io/discord)
- [Twitter](https://twitter.com/supertokensio)
- or [email us](mailto:team@supertokens.io)

Additional resources you might find useful:

- [SuperTokens Docs](https://supertokens.io/docs/community/getting-started/installation)
- [Blog Posts](https://supertokens.io/blog/)
- [Development guideline for the backend and frontend recipes](https://github.com/supertokens/supertokens-core/wiki/Development-guideline-for-the-backend-and-frontend-recipes)




