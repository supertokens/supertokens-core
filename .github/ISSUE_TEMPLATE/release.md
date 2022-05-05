---
name: 📅 Release
about: Release checklist
labels:
---

# 📅 Checklist

## 🔶 Staging 

### Dev Tag
 - [supertokens-core:X.Y](https://github.com/supertokens/supertokens-core/tree/X.Y)
     - [ ] core
        - [ ] check CDI, plugin interface list
     - [ ] plugin-interface
        - [ ] check plugin interface list
     - [ ] mysql-plugin
         - [ ] check plugin interface list
     - [ ] postgresql-plugin
         - [ ] check plugin interface list
     - [ ] mongodb-plugin
         - [ ] check plugin interface list
 - [ ] [supertokens-node:X.Y](https://github.com/supertokens/supertokens-node/tree/X.Y)
   - [ ] check CDI, FDI list
 - [ ] [supertokens-golang:X.Y](https://github.com/supertokens/supertokens-golang/tree/X.Y)
   - [ ] check CDI, FDI list
 - [ ] [supertokens-python:X.Y](https://github.com/supertokens/supertokens-python/tree/X.Y)
   - [ ] check CDI, FDI list
 - [ ] [supertokens-website:X.Y](https://github.com/supertokens/supertokens-website/X.Y)
   - [ ] check FDI list
 - [ ] [supertokens-auth-react:X.Y](https://github.com/supertokens/supertokens-auth-react/tree/X.Y)
    - [ ] check FDI list
    - [ ] Updated dependencies to use supertokens-website from npm registry
    - [ ] Various browsers - Safari, Firefox, Chrome, Edge
    - [ ] Mobile responsiveness
    - [ ] Make sure using with-typescript example that types are correct for every new configs exposed to users
    - [ ] Make sure frontend login UI shows even if backend is not working.
 - [ ] [supertokens-react-native:X.Y](https://github.com/supertokens/supertokens-react-native/X.Y)
    - [ ] check FDI list

### Others

 - [ ] Examples apps in supertokens-auth-react
 - [ ] Examples apps in supertokens-react-native
 - [ ] Examples apps in supertokens-golang
 - [ ] Examples apps in supertokens-python
 - [ ] Examples apps in supertokens-node
 - [ ] [next.js:canary](https://github.com/supertokens/next.js/tree/canary/examples/with-supertokens)
 - [ ] RedwoodJS and playground-auth
 - [ ] Run on netlify (and hence AWS lambda) to check if it works fine there
 - [ ] SuperTokens Jackson SAML example update

### 📚 Documentation (test site)

- [ ] All recipe main documentation update

- [ ] community documentation update

- [ ] website changes (test.supertokens.io)
   - [ ] homepage
   - [ ] pricing page feature list
   - [ ] comparison chart in the pricing page
   - [ ] product roadmap page
   - [ ] Update API key code snippet in SaaS dashboard

## 🔥 Production 

### 💻 NPM and core release

 - core
    - [ ] [supertokens-core:X.Y](https://github.com/supertokens/supertokens-core/tree/X.Y)
    - [ ] plugin-interface
    - [ ] mysql-plugin
    - [ ] postgresql-plugin
    - [ ] mongodb-plugin
    - Docker update
       - [ ] MySQL
       - [ ] Postgres
       - [ ] MongoDB
    - [ ] try.supertokens.io
      ```
      docker rm try-supertokens -f
      docker rmi supertokens/supertokens-postgresql:<VERSION>
      nano ~/try-supertokens/start_container.sh (update version tag)
      ~/try-supertokens/start_container.sh
      ```
    - [ ] Update SaaS config
    - [ ] Update to tables checked for user count / or to know if a deployment is being used or not
    - [ ] Update logic for exporting csv file for registered users
    - [ ] Update SaaS instances to use the latest docker images.
 - [ ] [supertokens-node:X.Y](https://github.com/supertokens/supertokens-node/tree/X.Y)
 - [ ] [supertokens-golang:X.Y](https://github.com/supertokens/supertokens-golang/tree/X.Y)
 - [ ] [supertokens-website:X.Y](https://github.com/supertokens/supertokens-website/tree/X.Y)
 - [ ] [supertokens-auth-react:X.Y](https://github.com/supertokens/supertokens-auth-react/tree/X.Y)
 - [ ] [supertokens-python:X.Y](https://github.com/supertokens/supertokens-python/tree/X.Y)
 - [ ] [supertokens-react-native:X.Y](https://github.com/supertokens/supertokens-react-native/X.Y)


### 📚 Documentation

- [ ] Pushed to production
- [ ] Post message on discord about new update
- [ ] Updated swaggerhub FDI spec
- [ ] Update frontend-driver-interface repo
- [ ] Updated swaggerhub CDI spec
- [ ] Update core-driver-interface-repo
- [ ] Update internal contributing guide to move from previous core version to the latest one
- [ ] Algolia search update for docs
- [ ] robots.txt, sitemap.xml, noindex page update
