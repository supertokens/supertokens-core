# Contributing

We're so excited you're interested in helping with Supertokens! We are happy to help you get started, even if you don't have any previous open-source experience :blush:

## New to Open Source?
1. Take a look at [How to Contribute to an Open Source Project on GitHub](https://egghead.io/courses/how-to-contribute-to-an-open-source-project-on-github)
2. Go thorugh the [SuperTokens Code of Conduct](https://github.com/supertokens/supertokens-core/blob/master/CODE_OF_CONDUCT.md)

## Where to ask Questions?
1. Check our [Github Issues](https://github.com/supertokens/supertokens-core/issues) to see if someone has already answered your question.  
2. Join our community on [Discord](https://supertokens.io/discord) and feel free to ask us your questions  

As you gain experience with SuperTokens, please help answer other people's questions! :pray: 

## What to Work On?
You can get started by taking a look at our [Github issues](https://github.com/supertokens/supertokens-core/issues)  
If you find one that looks interesting and no one else is already working on it, comment in the issue that you are going to work on it.  

Please ask as many questions as you need, either directly in the issue or on [Discord](https://supertokens.io/discord). We're happy to help!:raised_hands:

### Contributions that are ALWAYS welcome 

1. More tests
2. Contributing to discussions that can be found [here](https://github.com/supertokens/supertokens-core/issues?q=is%3Aissue+is%3Aopen+label%3Adiscussions)
3. Improved error messages
4. Educational content like blogs, videos, courses


## Development Setup

### Prerequisites
- OS: Linux or macOS
- JDK: openjdk 12.0.2 for [Linux](https://linuxhint.com/install_jdk12_ubuntu_1904/) or [Mac](https://java.tutorials24x7.com/blog/how-to-install-openjdk-12-on-macos)
- IDE: [IntelliJ](https://www.jetbrains.com/idea/download/)(recommended) or equivalent IDE

### Familiarize yourself with SuperTokens
1. [Architechture of SuperTokens](https://github.com/supertokens/supertokens-core/wiki/Code-and-file-structure-overview)
2. [SuperTokens code and file structure overview](https://github.com/supertokens/supertokens-core/wiki/Code-and-file-structure-overview)
3. [Versioning methodology](https://github.com/supertokens/supertokens-core/wiki/Versioning,-git-and-releases)


### Project Setup
1. Fork the [supertokens-core](https://github.com/supertokens/supertokens-core) repository
2. `git clone https://github.com/supertokens/supertokens-root.git`
3. `cd supertokens-root`
4. Open the `modules.txt` file in an editor:
    - The `modules.txt` file contains the core, plugin-interface, the type of plugin and their branches(versions) 
    - By default the `master` branch is used but you can change the branch depending on which version you want to modify 
    - The `sqlite-plugin` is used as the default plugin as it is an in-memory database and requires no setup
      - [core](https://github.com/supertokens/supertokens-core)
      - [plugin-interface](https://github.com/supertokens/supertokens-plugin-interface)
      - [sqlite-plugin](https://github.com/supertokens/supertokens-sqlite-plugin)
      - Check the repository branches by clicking on the links listed above, click the branch tab and check for all the available versions 
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
6. Create a directory called sqlite_db, this directory is required to run tests with the sqlite-plugin  
`mkdir sqlite_db`


## Modifying code
1. Open `supetokens-root` in your IDE
2. After gradle has imported all the dependencies you can start modifying the code  

## Testing  
1. Navigate to the `supertokens-root` repository  
2. Run all tests   
`./startTestingEnv`  
3. If all tests pass the terminal should display  
- core tests:  
![core tests passing](https://github.com/supertokens/supertokens-logo/blob/master/images/core-tests-passing.png)  
- plugin tests:  
![plugin tests passing](https://github.com/supertokens/supertokens-logo/blob/master/images/plugin-tests-passing.png)

## Pull Request
1. Before submitting a pull request make sure all tests have passed  
2. Reference the relevant issue or pull request and give a clear description of changes/features added when submitting a pull request

## SuperTokens Community 
SuperTokens is made possible by a passionate team and a strong community of developers. If you have any questions or would like to get more involved in the SuperTokens community you can check out:  
  - [Github Issues](https://github.com/supertokens/supertokens-core/issues)
  - [Discord](https://supertokens.io/discord)
  - [Twitter](https://twitter.com/supertokensio)
  - or [email us](mailto:team@supertokens.io)
  
Additional resources you might find useful:
  - [SuperTokens Docs](https://supertokens.io/docs/community/getting-started/installation)
  - [Blog Posts](https://supertokens.io/blog/)




