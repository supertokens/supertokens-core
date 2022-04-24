# Contributing

We're so excited you're interested in helping with SuperTokens! We are happy to help you get started, even if you don't have any previous open-source experience :blush:

## New to Open Source?
1. Take a look at [How to Contribute to an Open Source Project on GitHub][how-to-contribute-to-open-source]
2. Go through the [SuperTokens Code of Conduct][code-of-conduct]

## Where to Ask Questions?
1. Check our [Github Issues][issues] to see if someone has already answered your question.
2. Join our community on [Discord][discord] and feel free to ask us your questions

As you gain experience with SuperTokens, please help answer other people's questions! :pray:

## What to Work On?
You can get started by taking a look at our [Github Issues][issues]  
If you find one that looks interesting and no one else is already working on it, comment in the issue that you are going to work on it.

Please ask as many questions as you need, either directly in the issue or on [Discord][discord]. We're happy to help! :raised_hands:

### Contributions that are ALWAYS welcome
1. More tests
2. Contributing to discussions that can be found [here][issues-discussions]
3. Improved error messages
4. Educational content like blogs, videos, courses

## Development Setup

### With Gitpod
1. Navigate to the [supertokens-root][supertokens-root] repository
2. Click on the `Open in Gitpod` button

### Local Setup Prerequisites
- OS: Linux or macOS. Windows is experimentally supported with Git Bash (recommended, installed with [Git for Windows][git-for-windows]) or [WSL2][wsl-about].
- JDK: OpenJDK 15.0.1
    - Installation instructions for Mac and Linux can be found in [our wiki][wiki-openjdk-install-instructions-mac-linux]
    - To install for Windows, download and run the correct installer from the [AdoptOpenJDK Archive][adopt-openjdk-archive]
- IDE: [IntelliJ IDEA][intellij] (recommended) or equivalent IDE

### Familiarize Yourself with SuperTokens
1. [Architecture of SuperTokens][wiki-architecture]
2. [SuperTokens Code and File Structure Overview][wiki-overview]
3. [Versioning methodology][wiki-versioning]

### Experimental Windows Setup ([Git Bash][git-for-windows]) [Recommended]
1. Git Bash is automatically installed with [Git for Windows][git-for-windows]
2. Install the correct version of OpenJDK (see above) from [AdoptOpenJDK][adopt-openjdk-archive]
3. Open Command Prompt or PowerShell and run `sh` to open Git Bash
4. Run `echo "TERM=cygwin" >> .bashrc`
    - This ensures that the correct encoding is used in the console, especially when running `gradle` commands
5. Run `git config --global core.autocrlf false`
    - This ensures that line endings are not converted to Windows (CRLF) line endings on checkout
    - Scripts will fail if line endings are CRLF
6. Follow the instructions for [Project Setup](#project-setup) below (using Git Bash)
7. If using IntelliJ IDEA:
    - Go to `File > Project Structure... > Project Settings > Project` and set `Project SDK` to the correct OpenJDK version
    - Go to `File > Settings... > Tools > Terminal` and set `Application Settings > Shell Path` to the location of Git Bash
        - For the default install directory this is `C:\Program Files\Git\bin\bash.exe`
        - This will allow you to open a shell script and click the play button to run it and create a template run configuration
8. If using a Git GUI Client (GitHub Desktop, SourceTree, GitKraken, etc):
    - Make sure that Git Bash is set as the default terminal
        - This ensures that any pre-commit hooks can be run
9. If NOT using a Git GUI Client:
    - Make sure to use Git from the Git Bash shell
        - This ensures that any pre-commit hooks can be run

### Experimental Windows Setup ([WSL2][wsl-about])
1. If WSL2 is not installed, follow [Microsoft's installation instructions][wsl-install]
2. Open Command Prompt, PowerShell, or the Ubuntu Profile in Windows Terminal
3. If not using Ubuntu Profile in Windows Terminal, run `wsl` to open bash
4. Follow Linux setup instructions for [installing OpenJDK][wiki-openjdk-install-instructions-mac-linux]
5. Follow the instructions for [Project Setup](#project-setup) below (using WSL Bash)
    - Note: If using IntelliJ IDEA Community, checkout into the WSL filesystem, not Windows
        - Running Windows files in WSL is only supported in IntelliJ IDEA Ultimate
6. If using IntelliJ IDEA:
    - Go to `File > Project Structure... > Project Settings > Project` and set `Project SDK` to the correct WSL OpenJDK version
7. If using a Git GUI Client (GitHub Desktop, SourceTree, GitKraken, etc):
    - Make sure that WSL Bash is set as the default terminal
        - **This option does not have widespread support**
        - This ensures that any pre-commit hooks can be run
8. If NOT using a Git GUI Client:
    - Make sure to use Git from the WSL Bash shell
        - This ensures that any pre-commit hooks can be run

### Project Setup
1. Fork the [supertokens-core][supertokens-core] repository (**Skip this step if you are NOT modifying supertokens-core**)
2. Run `git clone https://github.com/supertokens/supertokens-root.git`
3. Run `cd supertokens-root`
4. Open the `modules.txt` file in an editor (**Skip this step if you are NOT modifying supertokens-core**):
    - The `modules.txt` file contains the core, plugin-interface, the type of plugin and their branches(versions)
    - By default, the `master` branch is used, but you can change the branch depending on which version you want to modify
    - The `sqlite-plugin` is used as the default plugin as it is an in-memory database and requires no setup
        - [core][supertokens-core]
        - [plugin-interface][supertokens-plugin-interface]
        - Check the repository branches by clicking on the links listed above, click the branch tab to view all the available versions
    - Add your GitHub `username` separated by a ',' after `core,master` in  `modules.txt`
    - If, for example, your GitHub `username` is `helloworld` then modules.txt should look like:
        ```
        // put module name like module name,branch name,github username(if contributing with a forked repository) and then call ./loadModules script        
        core,master,helloworld
        plugin-interface,master
        sqlite-plugin,master
        ```
5. Run `./loadModules` to clone the required repositories
6. Open `supertokens-root` in your IDE ([IntelliJ IDEA][intellij] is recommended)
7. After gradle has imported all the dependencies you can start modifying the code

## Developing With IntelliJ IDEA

### Run Configurations
![run configurations menu][image-run-configurations]

Several Run Configurations are available to run `supertokens-root` scripts:
- **Lint:** `./gradlew spotlessApply`
    - Run this configuration before committing
- **Run Core:** `./runCore`
- **Run All Tests:** `./startTestEnv`
- **Start Testing Environment:** `./startTestEnv --wait`
    - Run this configuration when running individual tests
- **Load Modules:** `./loadModules`

### Running All Tests
1. Make sure that none of these are running:
    - The `Start Testing Environment` run configuration
    - The `Run All Tests` run configuration
    - The `./startTestEnv` script
2. Run the `Run All Tests` run configuration

### Running Single Tests
1. Run the `Start Testing Environment` run configuration
2. Wait for it to print `Test environment started!`
3. Leave this script running in the terminal
4. Click the Play button next to the test(s) that you want to run
5. Tests can be debugged with breakpoints

### Running the Core
1. Edit `supertokens-root/supertokens-core/devConfig.yaml`
2. Run the `Run Core` run configuration
3. Breakpoints are **not supported** at this time

## Developing Without IntelliJ IDEA

### Running All Tests (Manually)
1. Navigate to the `supertokens-root` repository
2. Run `./startTestEnv`
3. If all tests pass the terminal should display
   - Core Tests:  
     ![core tests passing][image-core-tests-passing]
   - Plugin Tests:  
     ![plugin tests passing][image-plugin-tests-passing]

### Running All Tests (GitHub Actions)
1. Go to the `supertokens-core` repo on GitHub (or your forked version of it)
2. Navigate to the Actions tab
3. Find the action named "Run tests" and navigate to it
4. Click on the "Run workflow" button
5. Set the config variables in the drop down:
    - **supertokens-plugin-interface repo owner name**: If you have forked the supertokens-plugin-interface repo, then set the value of this to your GitHub username
    - **supertokens-plugin-interface repos branch name**: If the core version you are working on is compatible with a plugin-interface version that is not in the master branch, then set the correct branch name in this value
6. Click on "Run workflow"

## Running the Core (Manually)
1. Run `startTestingEnv --wait` in a terminal, and keep it running
2. Then open `supertokens-root` in another terminal and run `cp ./temp/config.yaml .`
3. Then run `java -classpath "./supertokens-core/*:./supertokens-plugin-interface/*" io.supertokens.Main ./ DEV`. This will start the core to listen on `http://localhost:3567`

## Pull Requests
1. Before submitting a pull request make sure all tests have passed
2. Reference the relevant issue or pull request and give a clear description of changes/features added when submitting a pull request
3. Make sure the PR title follows [conventional commits][conventional-commits] specification

## SuperTokens Community
SuperTokens is made possible by a passionate team and a strong community of developers. If you have any questions or would like to get more involved in the SuperTokens community you can check out:
- [Github Issues][issues]
- [Discord][discord]
- [Twitter][twitter]
- or [Email Us][email]

### Additional Resources
- [SuperTokens Docs][docs]
- [Blog Posts][blog]
- [Development Guideline for Backend and Frontend Recipes][wiki-recipe-development-guide]

[how-to-contribute-to-open-source]: https://egghead.io/courses/how-to-contribute-to-an-open-source-project-on-github

[code-of-conduct]: https://github.com/supertokens/supertokens-core/blob/master/CODE_OF_CONDUCT.md

[supertokens-root]: https://github.com/supertokens/supertokens-root

[supertokens-core]: https://github.com/supertokens/supertokens-core

[supertokens-plugin-interface]: https://github.com/supertokens/supertokens-plugin-interface

[issues]: https://github.com/supertokens/supertokens-core/issues

[issues-discussions]: https://github.com/supertokens/supertokens-core/issues?q=is%3Aissue+is%3Aopen+label%3Adiscussions

[discord]: https://supertokens.io/discord

[twitter]: https://twitter.com/supertokensio

[email]: mailto:team@supertokens.io

[blog]: https://supertokens.io/blog/

[docs]: https://supertokens.io/docs/community/getting-started/installation

[wiki-recipe-development-guide]: https://github.com/supertokens/supertokens-core/wiki/Development-guideline-for-the-backend-and-frontend-recipes

[wiki-openjdk-install-instructions-mac-linux]: https://github.com/supertokens/supertokens-core/wiki/Installing-OpenJDK-for-Mac-and-Linux

[wiki-architecture]: https://github.com/supertokens/supertokens-core/wiki/SuperTokens-Architecture

[wiki-overview]: https://github.com/supertokens/supertokens-core/wiki/Code-and-file-structure-overview

[wiki-versioning]: https://github.com/supertokens/supertokens-core/wiki/Versioning,-git-and-releases

[git-for-windows]: https://gitforwindows.org/

[wsl-about]: https://docs.microsoft.com/en-us/windows/wsl/about

[wsl-install]: https://docs.microsoft.com/en-us/windows/wsl/install

[intellij]: https://www.jetbrains.com/idea/

[adopt-openjdk-archive]: https://adoptopenjdk.net/archive.html

[conventional-commits]: https://www.conventionalcommits.org/en/v1.0.0/

[image-core-tests-passing]: https://github.com/supertokens/supertokens-logo/blob/master/images/core-tests-passing.png

[image-plugin-tests-passing]: https://github.com/supertokens/supertokens-logo/blob/master/images/plugin-tests-passing.png

[image-run-configurations]: https://github.com/supertokens/supertokens-logo/blob/master/images/supertokens-run-configurations.png