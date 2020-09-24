# Contributing

We're so excited you're interested in helping with Supertokens! We are happy to help you get started, even if you don't have any previous open-source experience :blush:

## New to Open Source?
1. Take a look at [How to Contribute to an Open Source Project on GitHub](https://egghead.io/courses/how-to-contribute-to-an-open-source-project-on-github)
2. Familiarize yourself with the [Supertokens Code of Conduct](https://github.com/supertokens/supertokens-core/blob/master/CODE_OF_CONDUCT.md)

## Where to get ask Questions?
1. Check the [Github repo](https://github.com/supertokens/supertokens-core) to see if someone has already answered your question.  
2. Join our community on [Discord](https://discord.com/invite/4WXseq7) and feel free to ask us your questions  
  
  
:pray: As you gain experience with Supertokens, please help answer other peoples questions!



## Development Setup

### Prerequisites
- OS: Linux or macOS
- JDK: openjdk 12.0.2 for [Linux](https://download.java.net/java/GA/jdk12.0.2/e482c34c86bd4bf8b56c0b35558996b9/10/GPL/openjdk-12.0.2_osx-x64_bin.tar.gz) or [Mac](https://download.java.net/java/GA/jdk12.0.2/e482c34c86bd4bf8b56c0b35558996b9/10/GPL/openjdk-12.0.2_linux-x64_bin.tar.gz)
- IDE:[IntelliJ](https://www.jetbrains.com/idea/download/)(recommended) or equivalent IDE

### Project Setup
1. Fork the [supertokens-core](https://github.com/supertokens/supertokens-core) repository
2. `git clone https://github.com/supertokens/supertokens-root.git`
3. `cd supertokens-root`
4. Open the modules.txt file in an editor:
    - By default the master branch is used but you can change the branch depending on which version you want to modify 
      - [core](https://github.com/supertokens/supertokens-core)
      - [plugin-interface](https://github.com/supertokens/supertokens-plugin-interface)
      - [sqlite-plugin](https://github.com/supertokens/supertokens-sqlite-plugin)
      - Check repository branches by clicking on the links listed above, click the branch tab and check for all the available versions 
    - Add your github username separated by a ',' after core,master in  modules.txt
    - If, for example, your github username is "helloworld" then modules.txt should look like...

      ```
      // put module name like module name,branch name,github username(if contributing with a forked repository) and then call ./loadModules script        
      core,master,helloworld  
      plugin-interface,master        
      sqlite-plugin,master
      ```
	
5. Run loadModules to clone the required repositories  
`./loadModules`
6. Create a directory sqlite_db(directory required to run tests with sqlite-plugin)  
`mkdir sqlite_db`
7. Run all tests   
`./startTestingEnv`
8. Open the project in your IDE
9. After gradle has imported all the dependencies you can start modifying the code

### Pull Request
1. Before submitting a pull request make sure all tests are passing
    - In the project root run all the tests
      - `./startTestingEnv`
    - The terminal output should display that all tests have passed 
      - core tests:  
      ![core tests passing](https://github.com/jscyo/supertokens-logo/blob/master/images/core-tests-passing.png)  
      - plugin tests:  
      ![plugin tests passing](https://github.com/jscyo/supertokens-logo/blob/master/images/plugin-tests-passing.png)
2. Reference relevant issue or pull requests and give a clear description of changes/features added when submitting a pull request

## Supertokens Community 
Supertokens is made possible by a passionate team and a strong community of developers. If you have any questions or would like to get more involved in the Supertokens community you can check out:  
  - [Discord](https://discord.com/invite/4WXseq7)
  - [Twitter](https://twitter.com/supertokensio)
  - or [email us](mailto:team@supertokens.io)
  
Additional resources you might find useful:
  - [Supertokens Docs](https://supertokens.io/docs/community/getting-started/installation)
  - [Blog Posts](https://supertokens.io/blog/)




