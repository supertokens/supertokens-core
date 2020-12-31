
![SuperTokens banner](https://raw.githubusercontent.com/supertokens/supertokens-logo/master/images/Artboard%20%E2%80%93%2027%402x.png)

# SuperTokens

<a href="https://supertokens.io/discord">
<img src="https://img.shields.io/discord/603466164219281420.svg?logo=discord"
    alt="chat on Discord"></a>

## Table of Contents
- [🚀 What is SuperTokens?](https://github.com/supertokens/supertokens-core#-what-is-supertokens)
    - [Philosophy](https://github.com/supertokens/supertokens-core#philosophy)
    - [Features + Demo app](https://github.com/supertokens/supertokens-core#features---click-here-to-see-the-demo-app)
    - [Documentation](https://github.com/supertokens/supertokens-core#documentation)
- [🏗️ Architecture](https://github.com/supertokens/supertokens-core#%EF%B8%8F-architecture)
- [☕ Why Java?](https://github.com/supertokens/supertokens-core#-why-java)
- [🔥 SuperTokens vs Others](https://github.com/supertokens/supertokens-core#-supertokens-vs-others)
- [🛠️ Building from source](https://github.com/supertokens/supertokens-core#%EF%B8%8F-building-from-source)
- [👥 Community](https://github.com/supertokens/supertokens-core#-community)
    - [Contributors](https://github.com/supertokens/supertokens-core#contributors-across-all-supertokens-repositories)
- [👩‍💻 Contributing](https://github.com/supertokens/supertokens-core#-contributing)
- [📝 License](https://github.com/supertokens/supertokens-core#-license)

If you like our project, please :star2: this repository! For feedback, feel free to join our [Discord](https://supertokens.io/discord), or create an issue on this repo

## 🚀 What is SuperTokens?
SuperTokens is an open core alternative to proprietary login providers like Auth0 or AWS Cognito. We are
 different because we offer:
- Open source: SuperTokens can be used for free, forever, with no limits on the number of users.
- An on-premises deployment so that you control 100% of your user data, using your own database.
- An end to end solution with login, sign ups, user and session management, without all the complexities of OAuth protocols.
- Ease of implementation and higher security.
- Extensibility: Anyone can contribute and make SuperTokens better!

### Philosophy
Authentication directly affects UX, dev experience and security of any app. We believe that
 current solutions are unable to optimise for all three "pillars", leading to a large number of
  applications hand rolling their own auth. This not only leads to security issues, but is also a massive
   time drain.
  
We want to change that - we believe the only way is to provide a solution that has the right level of
 abstraction, gives you maximum control, is secure, and is simple to use - just like if you build it yourself,
  from scratch (minus the time to learn, build and maintain).
  
We also believe in the principle of least vendor lockin. Your having full control of your user's data means that you can switch away from SuperTokens without forcing your existing users to logout, reset their passwords or in the worst case, sign up again. 

### Features - [Click here](https://emailpassword.demo.supertokens.io/) to see the demo app.
- Please visit [our website](https://supertokens.io/pricing) to see the list of features.
- We want to make features as decoupled as possible. This means, you can use SuperTokens for just login, or just session management, or both. In fact, we also offer session management integrations with other login providers like Auth0.


### Documentation
The docs can be seen [on our website](https://supertokens.io/docs/emailpassword/introduction).

There is more information about SuperTokens on the [GitHub wiki section](https://github.com/supertokens/supertokens-core/wiki).

## 🏗️ Architecture
<img width="700px" src="https://supertokens.io/docs/static/assets/emailpassword/architecture.png" />

- **Frontend SDK**: Responsible for rendering the login UI widgets and managing session tokens automatically.
- **Backend SDK**: Provides APIs for sign-up, sign-in, signout, session refreshing etc... You can also disable these default APIs and build your own using the provided functions from this SDK.
- **SuperTokens Core**: This is an HTTP service that contains the core logic for auth and sessions. It's also responsible for interfacing with the database.

**For more information, please visit our [GitHub wiki section](https://github.com/supertokens/supertokens-core/wiki/SuperTokens-Architecture).**

## ☕ Why Java?
- ✅ Whilst running Java can seem difficult, we provide the JDK along with the binary / docker image when distributing it. This makes running SuperTokens just like running any other http microservice.
- ✅ Java has a very mature ecosystem. This implies that third party libraries have been battled tested.
- ✅ Java's strong type system ensures fewer bugs and easier maintainability. This is especially important when many people are expected to work on the same project.
- ✅ Our team is most comfortable with Java and hiring for great Java developers is relatively easy as well.

## 🔥 SuperTokens vs others
Please find a detailed comparison chart [on our website](https://supertokens.io/pricing#comparison-chart)

## 🛠️ Building from source
Please see our [wiki](https://github.com/supertokens/supertokens-core/wiki/Building-from-source) for instructions.

## 👥 Community
- [Discord](https://supertokens.io/discord)
- [Email](mailto:team@supertokens.io)

If you think this is a project you could use in the future, please :star2: this repository!

### Contributors (across all SuperTokens repositories)
<table>
  <tr>
    <td align="center"><a href="https://github.com/rishabhpoddar"><img src="https://avatars1.githubusercontent.com/u/2976287?s=460&u=d0cf2463df96fbdf1138cf74f88d7cf41415b238&v=4" width="100px;" alt=""/><br /><sub><b>Rishabh Poddar</b></sub></a></td>
    <td align="center"><a href="https://twitter.com/Advait_Ruia"><img src="https://pbs.twimg.com/profile_images/1261970454685900800/ALVzsBQJ_400x400.jpg" width="100px;" alt=""/><br /><sub><b>Advait Ruia</b></sub></a></td>
    <td align="center"><a href="https://github.com/bhumilsarvaiya"><img src="https://avatars2.githubusercontent.com/u/21988812?s=460&u=c0bcde60a8bf1a99baafced55dd1a8d901fa7e4a&v=4" width="100px;" alt=""/><br /><sub><b>Bhumil Sarvaiya</b></sub></a></td>
    <td align="center"><a href="https://github.com/jscyo"><img src="https://i.stack.imgur.com/frlIf.png" width="100px;" alt=""/><br /><sub><b>Joel Coutinho</b></sub></a></td> 
  </tr>
  <tr>
   <td align="center"><a href="https://github.com/RakeshUP"><img src="https://avatars1.githubusercontent.com/u/20946466?s=400&u=01d7d6d701eedd8345e491172e3af04578d18113&v=4" width="100px;" alt=""/><br /><sub><b>Rakesh UP</b></sub></a></td>
   <td align="center"><a href="https://twitter.com/mufassirkazi"><img src="https://i.stack.imgur.com/frlIf.png" width="100px;" alt=""/><br /><sub><b>Mufassir Kazi</b></sub></a></td>
<td align="center"><a href="https://github.com/nkshah2"><img src="https://avatars2.githubusercontent.com/u/18233774?s=400&u=5befa41674cfcd6c6060103360ab323cdfa24dcb&v=4" width="100px;" alt=""/><br /><sub><b>Nemi Shah</b></sub></a></td>
<td align="center"><a href="https://github.com/irohitb"><img src="https://avatars3.githubusercontent.com/u/32276134?s=400&u=0b72f6c4e6cfa749229a8e69ed86acb720a384e7&v=4" width="100px;" alt=""/><br /><sub><b>Rohit Bhatia</b></sub></a></td>
  </tr>
  <tr>
<td align="center"><a href="https://github.com/mmaha"><img src="https://avatars3.githubusercontent.com/u/297517?s=400&u=8c41caf46c511ed2054c3d14c23193eda0d996af&v=4" width="100px;" alt=""/><br /><sub><b>Madhu Mahadevan</b></sub></a></td>
<td align="center"><a href="https://github.com/nugmanoff"><img src="https://avatars3.githubusercontent.com/u/20473743?s=460&u=2d33e10df1e8c3f38328e6e92d753363026f660f&v=4" width="100px;" alt=""/><br /><sub><b>Aidar Nugmanoff</b></sub></a></td>
<td align="center"><a href="https://github.com/arnxv0"><img src="https://avatars2.githubusercontent.com/u/57629464?s=460&u=5f0cca1aed9fabb38bea74df73ed99dfcfec2f26&v=4" width="100px;" alt=""/><br /><sub><b>Arnav Dewan</b></sub></a></td>
<td align="center"><a href="https://github.com/NkxxkN"><img src="https://avatars1.githubusercontent.com/u/5072452?s=460&u=eda6b25b674d20e3389bf19a0619d6e4c1e46670&v=4" width="100px;" alt=""/><br /><sub><b>NkxxkN</b></sub></a></td>
  </tr>
  <tr>
<td align="center"><a href="https://github.com/UbadahJ"><img src="https://avatars1.githubusercontent.com/u/26687928?s=460&u=ae1d3ae5fad6e4cfa71809f8ce4a99429321dcaf&v=4" width="100px;" alt=""/><br /><sub><b>LordChadiwala</b></sub></a></td>
<td align="center"><a href="https://github.com/LuizDoPc"><img src="https://avatars0.githubusercontent.com/u/20651653?s=460&u=d673e5357da83e446311831efe107e695d3ef875&v=4" width="100px;" alt=""/><br /><sub><b>Luiz Soares</b></sub></a></td>
<td align="center"><a href="https://github.com/sudiptog81"><img src="https://avatars0.githubusercontent.com/u/11232940?s=460&u=07b4989ae4c43e43f35730d7f8d59631f5ed933c&v=4" width="100px;" alt=""/><br /><sub><b>Sudipto Ghosh</b></sub></a></td>
<td align="center"><a href="https://github.com/Fabricio20"><img src="https://avatars1.githubusercontent.com/u/7545720?s=400&v=4" width="100px;" alt=""/><br /><sub><b>Fabricio20</b></sub></a></td>
  </tr>
  <tr>
<td align="center"><a href="https://github.com/metallicmonkey"><img src="https://avatars0.githubusercontent.com/u/10272154?s=460&u=b6f5daefe3f3ce49e9ed094043674a2c2718af73&v=4" width="100px;" alt=""/><br /><sub><b>metallicmonkey</b></sub></a></td>
<td align="center"><a href="https://github.com/vidu171"><img src="https://avatars1.githubusercontent.com/u/25363324?s=460&u=8d3ccde95f49579e893c8c12db22cdcd0fea36cb&v=4" width="100px;" alt=""/><br /><sub><b>Vidhyanshu Jain</b></sub></a></td>
<td align="center"><a href="https://github.com/dlion"><img src="https://avatars3.githubusercontent.com/u/2125236?s=460&u=801df23e89718386a099ba60e15b61a562fdf334&v=4" width="100px;" alt=""/><br /><sub><b>Domenico Luciani</b></sub></a></td>
<td align="center"><a href="https://github.com/EnzoBtv"><img src="https://avatars1.githubusercontent.com/u/40310156?s=460&u=f7c0e017293b0d57b8340dbfae36c078f0176e1a&v=4" width="100px;" alt=""/><br /><sub><b>Enzo Batrov</b></sub></a></td>
  </tr>
  <tr>
<td align="center"><a href="https://github.com/IsautierEloise"><img src="https://avatars2.githubusercontent.com/u/44578188?s=400&u=2bda597af317d871d6b1017193956b40a6fe0412&v=4" width="100px;" alt=""/><br /><sub><b>Eloïse Isautier</b></sub></a></td>
<td align="center"><a href="https://github.com/ocReaper"><img src="https://avatars2.githubusercontent.com/u/4038188?s=460&v=4" width="100px;" alt=""/><br /><sub><b>Ákos Resch</b></sub></a></td>
<td align="center"><a href="https://github.com/chotuchaudhary"><img src="https://avatars0.githubusercontent.com/u/14938108?s=460&v=4" width="100px;" alt=""/><br /><sub><b>Chotu Chaudhary</b></sub></a></td>
  </tr>
</table>

## 👩‍💻 Contributing
Please see the [CONTRIBUTING.md](https://github.com/supertokens/supertokens-core/blob/master/CONTRIBUTING.md) file for instructions.

## 📝 License
&copy; 2020 SuperTokens Inc and its contributors. All rights reserved.

Licensed under the Apache 2.0 license.
