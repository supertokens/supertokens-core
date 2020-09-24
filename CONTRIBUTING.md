# Contributing

Please note we have a code of conduct, please follow it in all your interactions with the project.

## Setup

### Prerequisites
- OS: Linux or macOS
- JDK: openjdk 12.0.2
- IDE:IntelliJ(recommended) or equivalent IDE
- Valid github account

### Project Setup
1. Fork the [supertokens-root](https://github.com/supertokens/supertokens-root.git) and [supertokens-core](https://github.com/supertokens/supertokens-core) repositories
2. Navigate to the forked supertokens-root repository and clone it in your local environment 
  - Open your terminal
  - git clone https://github.com/your_github_username/supertokens-root.git
3. cd supertokens-root
4. Open the modules.txt file in an editor:
  - By default the master branch is used but you can change the branch depending on which version you want to modify 
    - [core](https://github.com/supertokens/supertokens-core)
    - [plugin-interface](https://github.com/supertokens/supertokens-plugin-interface)
    - [sqlite-plugin](https://github.com/supertokens/supertokens-sqlite-plugin)
    - Check repositories branches by clicking on the links listed above, click the branch tab and check for all the available versions 
  - Add your github username separated by a comma after core,master in  modules.txt
  - Final modules.txt should look like
    - Example  
      // put module name like <module name>,<branch name>,<github username>(if contributing with a forked repository) and then call ./loadModules script  
	core,master,your_github_username  
	plugin-interface,master  
	sqlite-plugin,master
	
5. run ./loadModules
6. mkdir sqlite_db (directory required to run tests with sqlite-plugin)
7. ./startTestingEnv (runs all the tests)
8. Open the project in your IDE(we recommend using Intellij)
9. After gradle has imported all the dependencies you can start modifying the code

### Pull Request
1. Before submitting a pull request make sure all tests are passing
  - In the project root run all the tests with command ./startTestingEnv
  - The terminal output should display that all tests have passed
![Successful test screenshot](/relative/path/to/img.jpg?raw=true "Successful test screenshot")  
2.Reference relevant issue or pull requests and give a clear description of changes made/features added before submitting a pull request


## Code of Conduct

### Our Pledge

In the interest of fostering an open and welcoming environment, we as
contributors and maintainers pledge to making participation in our project and
our community a harassment-free experience for everyone, regardless of age, body
size, disability, ethnicity, gender identity and expression, level of experience,
nationality, personal appearance, race, religion, or sexual identity and
orientation.

### Our Standards

Examples of behavior that contributes to creating a positive environment
include:

* Using welcoming and inclusive language
* Being respectful of differing viewpoints and experiences
* Gracefully accepting constructive criticism
* Focusing on what is best for the community
* Showing empathy towards other community members

Examples of unacceptable behavior by participants include:

* The use of sexualized language or imagery and unwelcome sexual attention or
advances
* Trolling, insulting/derogatory comments, and personal or political attacks
* Public or private harassment
* Publishing others' private information, such as a physical or electronic
  address, without explicit permission
* Other conduct which could reasonably be considered inappropriate in a
  professional setting

### Our Responsibilities

Project maintainers are responsible for clarifying the standards of acceptable
behavior and are expected to take appropriate and fair corrective action in
response to any instances of unacceptable behavior.

Project maintainers have the right and responsibility to remove, edit, or
reject comments, commits, code, wiki edits, issues, and other contributions
that are not aligned to this Code of Conduct, or to ban temporarily or
permanently any contributor for other behaviors that they deem inappropriate,
threatening, offensive, or harmful.

### Scope

This Code of Conduct applies both within project spaces and in public spaces
when an individual is representing the project or its community. Examples of
representing a project or community include using an official project e-mail
address, posting via an official social media account, or acting as an appointed
representative at an online or offline event. Representation of a project may be
further defined and clarified by project maintainers.

### Enforcement

Instances of abusive, harassing, or otherwise unacceptable behavior may be
reported by contacting the project [team](mailto:team@supertokens.io). All
complaints will be reviewed and investigated and will result in a response that
is deemed necessary and appropriate to the circumstances. The project team is
obligated to maintain confidentiality with regard to the reporter of an incident.
Further details of specific enforcement policies may be posted separately.

Project maintainers who do not follow or enforce the Code of Conduct in good
faith may face temporary or permanent repercussions as determined by other
members of the project's leadership.
