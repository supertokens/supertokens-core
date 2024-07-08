[![SuperTokens banner](https://raw.githubusercontent.com/supertokens/supertokens-logo/master/images/Artboard%20%E2%80%93%2027%402x.png)](https://supertokens.com/)

# Open-Source auth provider

<a href="https://supertokens.io/discord">
<img src="https://img.shields.io/discord/603466164219281420.svg?logo=discord"
    alt="chat on Discord"></a>
<span><img src="https://img.shields.io/docker/pulls/supertokens/supertokens-postgresql.svg" alt="Docker pull stats"/></span>

Add **secure login and session management** to your apps. [SDKs available](https://supertokens.com/docs/community/sdks)
for popular languages and front-end frameworks e.g. Node.js, Go, Python, React.js, React Native, Vanilla JS, etc.

![Architecture Diagram](https://supertokens.com/img/architecture/self_hosted_generic.png)
Supertokens architecture is optimized to add secure authentication for your users without compromising on user and
developer experience

**Three building blocks of SuperTokens architecture**

1. Frontend SDK: Manages session tokens and renders login UI widgets
2. Backend SDK: Provides APIs for sign-up, sign-in, signout, session refreshing, etc. Your Frontend will talk to these
   APIs
3. SuperTokens Core: The HTTP service for the core auth logic and database operations. This service is used by the
   Backend SDK

## Features

[![Click here to get started](.github/click-here-to-get-started.png)](https://supertokens.com/docs/guides)

* Passwordless Login
* Social Login
* Email Password Login
* Phone Password Login
* Session Management
* Multi-Factor Authentication
* Multi Tenancy / Organization Support (Enterprise SSO)
* User Roles
* Microservice Authentication

## Learn more

- [🚀 What is SuperTokens?](https://github.com/supertokens/supertokens-core#-what-is-supertokens)
    - [Philosophy](https://github.com/supertokens/supertokens-core#philosophy)
    - [Features + Demo app](https://github.com/supertokens/supertokens-core#features---click-here-to-see-the-demo-app)
    - [Documentation](https://github.com/supertokens/supertokens-core#documentation)
- [🏗️ Architecture](https://github.com/supertokens/supertokens-core#%EF%B8%8F-architecture)
- [☕ Why Java?](https://github.com/supertokens/supertokens-core#-why-java)
- [⌨️ User Management Dashboard](https://github.com/supertokens/supertokens-core#-user-management-dashboard)
- [🔥 SuperTokens vs Others](https://github.com/supertokens/supertokens-core#-supertokens-vs-others)
- [🛠️ Building from source](https://github.com/supertokens/supertokens-core#%EF%B8%8F-building-from-source)
- [👥 Community](https://github.com/supertokens/supertokens-core#-community)
    - [Contributors](https://github.com/supertokens/supertokens-core#contributors-across-all-supertokens-repositories)
- [👩‍💻 Contributing](https://github.com/supertokens/supertokens-core#-contributing)
- [📝 License](https://github.com/supertokens/supertokens-core#-license)

### If you like our project, please :star2: this repository! For feedback, feel free to join our [Discord](https://supertokens.io/discord), or create an issue on this repo

## 🚀 What is SuperTokens?

SuperTokens is an open-core alternative to proprietary login providers like Auth0 or AWS Cognito. We are
different because we offer:

- Open source: SuperTokens can be used for free, forever, with no limits on the number of users.
- An on-premises deployment so that you control 100% of your user data, using your own database.
- An end-to-end solution with login, sign-ups, user and session management, without all the complexities of OAuth
  protocols.
- Ease of implementation and higher security.
- Extensibility: Anyone can contribute and make SuperTokens better!

### Philosophy

Authentication directly affects the UX, dev experience, and security of any app. We believe that
current solutions cannot optimize for all three "pillars", leading to many
applications hand-rolling their own auth. This not only leads to security issues but is also a massive
time drain.

We want to change that - we believe the only way is to provide a solution that has the right level of
abstraction gives you maximum control, is secure, and is simple to use - just like if you build it yourself,
from scratch (minus the time to learn, build, and maintain).

We also believe in the principle of least vendor lock-in. Your having full control of your user's data means that you
can switch away from SuperTokens without forcing your existing users to logout, reset their passwords, or in the worst
case, sign up again.

### [Click here](https://thirdpartyemailpassword.demo.supertokens.io/) to see the demo app.

- Please visit [our website](https://supertokens.io/pricing) to see the list of features.
- We want to make features as decoupled as possible. This means you can use SuperTokens for just login, or just session
  management, or both. In fact, we also offer session management integrations with other login providers like Auth0.

### Documentation

The docs can be seen [on our website](https://supertokens.io/docs/community/introduction).

There is more information about SuperTokens on
the [GitHub wiki section](https://github.com/supertokens/supertokens-core/wiki).

## 🏗️ Architecture

Please find an [architecture diagram here](https://supertokens.io/docs/community/architecture)

**For more information, please visit
our [GitHub wiki section](https://github.com/supertokens/supertokens-core/wiki/SuperTokens-Architecture).**

## ☕ Why Java?

- ✅ Whilst running Java can seem difficult, we provide the JDK along with the binary/docker image when distributing it.
  This makes running SuperTokens just like running any other HTTP microservice.
- ✅ Java has a very mature ecosystem. This implies that third-party libraries have been battle-tested.
- ✅ Java's strong type system ensures fewer bugs and easier maintainability. This is especially important when many
  people are expected to work on the same project.
- ✅ Our team is most comfortable with Java and hiring great Java developers is relatively easy as well.
- ✅ One of the biggest criticisms of Java is memory usage. We have three solutions to this:
    - The most frequent auth-related operation is session verification - this happens within the backend SDK (node,
      python, Go) without contacting the Java core. Therefore, a single instance of the core can handle several 10s of
      thousands of users fairly easily.
    - We have carefully chosen our dependencies. For eg: we use an embedded tomcat server instead of a higher-level web
      framework.
    - We also plan on using [GraalVM](https://www.graalvm.org/) in the future and this can reduce memory usage by 95%!
- ✅ If you require any modifications to the auth APIs, those would need to be done on the backend SDK level (for example
  Node, Golang, Python..). So you’d rarely need to directly modify/work with the Java code in this repo.

## ⌨️ User Management Dashboard

Oversee your users with the [SuperTokens User Management Dashboard](https://supertokens.com/docs/userdashboard/about)

### List users

List all the users who have signed up to your application.

![List SuperTokens users](.github/list-user.png)

### Manage users

Manage users by modifying or deleting their sessions, metadata, roles and account info.

![Manage users](.github/user-info.png)

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
<td align="center"><a href="https://github.com/heracek"><img src="https://avatars.githubusercontent.com/u/7502?s=460&v=4" width="100px;" alt=""/><br /><sub><b>Tomáš Horáček</b></sub></a></td>
  </tr>
  <tr>
<td align="center"><a href="https://github.com/sbauch"><img src="https://avatars.githubusercontent.com/u/923033?s=460&u=db9bb41f9b279750c74afc1be0ab51db05539593&v=4" width="100px;" alt=""/><br /><sub><b>Sam Bauch</b></sub></a></td>
<td align="center"><a href="https://github.com/mirrorrim"><img src="https://avatars.githubusercontent.com/u/9555251?v=4" width="100px;" alt=""/><br /><sub><b>Alexey Tylindus</b></sub></a></td>
<td align="center"><a href="https://github.com/gusfune"><img src="https://avatars.githubusercontent.com/u/1147240?v=4" width="100px;" alt=""/><br /><sub><b>Gus Fune</b></sub></a></td>
<td align="center"><a href="https://github.com/chenkaiC4"><img src="https://avatars.githubusercontent.com/u/7543145?v=4" width="100px;" alt=""/><br /><sub><b>chenkaiC4</b></sub></a></td>
  </tr>
  <tr>
<td align="center"><a href="https://github.com/dulowski-marek"><img src="https://avatars.githubusercontent.com/u/17051704?v=4" width="100px;" alt=""/><br /><sub><b>Marek Dulowski</b></sub></a></td>
<td align="center"><a href="https://github.com/Piyushhbhutoria"><img src="https://avatars.githubusercontent.com/u/20777594?v=4" width="100px;" alt=""/><br /><sub><b>Piyushh Bhutoria</b></sub></a></td>
<td align="center"><a href="https://github.com/aldeed"><img src="https://avatars.githubusercontent.com/u/3012067?v=4" width="100px;" alt=""/><br /><sub><b>Eric Dobbertin</b></sub></a></td>
<td align="center"><a href="https://github.com/seniorquico"><img src="https://avatars.githubusercontent.com/u/415806?v=4" width="100px;" alt=""/><br /><sub><b>Kyle Dodson</b></sub></a></td>
  </tr>
  <tr>
<td align="center"><a href="https://github.com/taijuten"><img src="https://avatars.githubusercontent.com/u/4288526?v=4" width="100px;" alt=""/><br /><sub><b>Ralph Lawrence</b></sub></a></td>
<td align="center"><a href="https://github.com/christopher-kapic"><img src="https://avatars.githubusercontent.com/u/59740769?v=4" width="100px;" alt=""/><br /><sub><b>Christopher Kapic</b></sub></a></td>
<td align="center"><a href="https://github.com/Hanzyusuf"><img src="https://avatars.githubusercontent.com/u/22171112?v=4" width="100px;" alt=""/><br /><sub><b>Hanzyusuf</b></sub></a></td>
<td align="center"><a href="https://github.com/porcellus"><img src="https://avatars.githubusercontent.com/u/1129990?v=4" width="100px;" alt=""/><br /><sub><b>Mihály Lengyel</b></sub></a></td>
  </tr>
  <tr>
<td align="center"><a href="https://github.com/cerino-ligutom"><img src="https://avatars.githubusercontent.com/u/6721822?v=4" width="100px;" alt=""/><br /><sub><b>Cerino O. Ligutom III</b></sub></a></td>
<td align="center"><a href="https://github.com/nadilas"><img src="https://avatars.githubusercontent.com/u/5324856?v=4" width="100px;" alt=""/><br /><sub><b>nadilas</b></sub></a></td>
<td align="center"><a href="https://github.com/vasica38"><img src="https://avatars.githubusercontent.com/u/26538079?v=4" width="100px;" alt=""/><br /><sub><b>Vasile Catana</b></sub></a></td>
<td align="center"><a href="https://github.com/rossoskull"><img src="https://avatars.githubusercontent.com/u/27884543?v=4" width="100px;" alt=""/><br /><sub><b>Jay Mistry</b></sub></a></td>
  </tr> 
  <tr>
<td align="center"><a href="https://github.com/jacobhq"><img src="https://avatars.githubusercontent.com/u/29145479?v=4" width="100px;" alt=""/><br /><sub><b>Jacob Marshall</b></sub></a></td>
<td align="center"><a href="https://github.com/miketromba"><img src="https://avatars.githubusercontent.com/u/25141252?v=4" width="100px;" alt=""/><br /><sub><b>miketromba</b></sub></a></td>
<td align="center"><a href="https://github.com/olhapi"><img src="https://avatars.githubusercontent.com/u/4780263?v=4" width="100px;" alt=""/><br /><sub><b>Oleg Vdovenko</b></sub></a></td>
<td align="center"><a href="https://github.com/siddharthmudgal"><img src="https://avatars.githubusercontent.com/u/9314217?v=4" width="100px;" alt=""/><br /><sub><b>Siddharth</b></sub></a></td>
  </tr>
  <tr>
<td align="center"><a href="https://github.com/xuatz"><img src="https://avatars.githubusercontent.com/u/9292261?v=4" width="100px;" alt=""/><br /><sub><b>xuatz</b></sub></a></td>
<td align="center"><a href="https://github.com/yowayb"><img src="https://avatars.githubusercontent.com/u/603829?v=4" width="100px;" alt=""/><br /><sub><b>Yoway Buorn</b></sub></a></td>
<td align="center"><a href="https://github.com/rtpa25"><img src="https://avatars.githubusercontent.com/u/72537293?v=4" width="100px;" alt=""/><br /><sub><b>Ronit Panda</b></sub></a></td>
<td align="center"><a href="https://github.com/anugrahsinghal"><img src="https://avatars.githubusercontent.com/u/18058884?v=4" width="100px;" alt=""/><br /><sub><b>Anugrah Singhal</b></sub></a></td>
  </tr>
  <tr>
<td align="center"><a href="https://github.com/JeremyEastham"><img src="https://avatars.githubusercontent.com/u/34139712?v=4" width="100px;" alt=""/><br /><sub><b>Jeremy Eastham</b></sub></a></td>
<td align="center"><a href="https://github.com/assafushy"><img src="https://avatars.githubusercontent.com/u/7502687?v=4" width="100px;" alt=""/><br /><sub><b>Assaf Yacobi</b></sub></a></td>
<td align="center"><a href="https://github.com/sattvikc"><img src="https://avatars.githubusercontent.com/u/650429?v=4" width="100px;" alt=""/><br /><sub><b>Sattvik Chakravarthy</b></sub></a></td>
<td align="center"><a href="https://github.com/opichon"><img src="https://avatars.githubusercontent.com/u/203745?v=4" width="100px;" alt=""/><br /><sub><b>Olivier Pichon</b></sub></a></td>
  </tr>
  <tr>
<td align="center"><a href="https://github.com/FuzzySid"><img src="https://avatars.githubusercontent.com/u/40206571?v=4" width="100px;" alt=""/><br /><sub><b>Siddhant Varma</b></sub></a></td>
<td align="center"><a href="https://github.com/renyijiu"><img src="https://avatars.githubusercontent.com/u/8318266?v=4" width="100px;" alt=""/><br /><sub><b>renyijiu</b></sub></a></td>
<td align="center"><a href="https://github.com/ITenthusiasm"><img src="https://avatars.githubusercontent.com/u/47364027?v=4" width="100px;" alt=""/><br /><sub><b>Isaiah Thomason</b></sub></a></td>
<td align="center"><a href="https://github.com/utsavdotpro"><img src="https://avatars.githubusercontent.com/u/38961422?v=4" width="100px;" alt=""/><br /><sub><b>Utsav Barnwal</b></sub></a></td>
  </tr>
  <tr>
<td align="center"><a href="https://github.com/saurabhghatnekar"><img src="https://avatars.githubusercontent.com/u/20884782?v=4" width="100px;" alt=""/><br /><sub><b>Saurabh Ghatnekar</b></sub></a></td>
<td align="center"><a href="https://github.com/alisher-aituarov"><img src="https://avatars.githubusercontent.com/u/63276190?v=4" width="100px;" alt=""/><br /><sub><b>Alisher Aituarov</b></sub></a></td>
<td align="center"><a href="https://github.com/wallstromsimon"><img src="https://avatars.githubusercontent.com/u/3397398?v=4" width="100px;" alt=""/><br /><sub><b>Simon Kihlberg Wallstrom</b></sub></a></td>
<td align="center"><a href="https://github.com/AreebKhan619"><img src="https://avatars.githubusercontent.com/u/39133435?v=4" width="100px;" alt=""/><br /><sub><b>Areeb Khan</b></sub></a></td>
  </tr>
  <tr>
<td align="center"><a href="https://github.com/sublimator"><img src="https://avatars.githubusercontent.com/u/525211?v=4" width="100px;" alt=""/><br /><sub><b>Nicholas Dudfield</b></sub></a></td>
<td align="center"><a href="https://github.com/Qdea"><img src="https://avatars.githubusercontent.com/u/58660439?v=4" width="100px;" alt=""/><br /><sub><b>Qdea</b></sub></a></td>
<td align="center"><a href="https://github.com/LukasKnuth"><img src="https://avatars.githubusercontent.com/u/692211?v=4" width="100px;" alt=""/><br /><sub><b>Lukas Knuth</b></sub></a></td>
<td align="center"><a href="https://github.com/melvynhills"><img src="https://avatars.githubusercontent.com/u/417315?v=4" width="100px;" alt=""/><br /><sub><b>Melvyn Hills</b></sub></a></td>
  <tr>
<td align="center"><a href="https://github.com/mattanimation"><img src="https://avatars.githubusercontent.com/u/1426997?v=4" width="100px;" alt=""/><br /><sub><b>Matt Murray</b></sub></a></td>
<td align="center"><a href="https://github.com/constantoine"><img src="https://avatars.githubusercontent.com/u/13930958?v=4" width="100px;" alt=""/><br /><sub><b>Cléo Rebert</b></sub></a></td>
<td align="center"><a href="https://github.com/daniil-borovoy"><img src="https://avatars.githubusercontent.com/u/74528634?v=4" width="100px;" alt=""/><br /><sub><b>Daniil Borovoy</b></sub></a></td>
<td align="center"><a href="https://github.com/kriskw1999"><img src="https://avatars.githubusercontent.com/u/71312948?v=4" width="100px;" alt=""/><br /><sub><b>Krzysztof Witkowski</b></sub></a></td>
  </tr>
  <tr>
<td align="center"><a href="https://github.com/Lehoczky"><img src="https://avatars.githubusercontent.com/u/31937175?v=4" width="100px;" alt=""/><br /><sub><b>Lehoczky Zoltán</b></sub></a></td>
<td align="center"><a href="https://github.com/virajkanwade"><img src="https://avatars.githubusercontent.com/u/316111?v=4" width="100px;" alt=""/><br /><sub><b>Viraj Kanwade</b></sub></a></td>
<td align="center"><a href="https://github.com/anuragmerndev"><img src="https://avatars.githubusercontent.com/u/144275260?v=4" width="100px;" alt=""/><br /><sub><b>Anurag Srivastava</b></sub></a></td>
  </tr>
</table>

## 👩‍💻 Contributing

Please see the [CONTRIBUTING.md](https://github.com/supertokens/supertokens-core/blob/master/CONTRIBUTING.md) file for
instructions.

## 📝 License

&copy; 2020-2023 SuperTokens Inc and its contributors. All rights reserved.

Portions of this software are licensed as follows:

* All content that resides under the "ee/" directory of this repository, if that directory exists, is licensed under the
  license defined in "ee/LICENSE.md".
* All third-party components incorporated into the SuperTokens Software are licensed under the original license provided
  by the owner of the applicable component.
* Content outside of the above-mentioned directories or restrictions above is available under the "Apache 2.0"
  license as defined in the level "LICENSE.md" file
