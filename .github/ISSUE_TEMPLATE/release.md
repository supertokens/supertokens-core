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
     - [ ] plugin-interface
     - [ ] mysql-plugin
     - [ ] postgresql-plugin
     - [ ] mongodb-plugin
 - [ ] [supertokens-node:X.Y](https://github.com/supertokens/supertokens-node/tree/X.Y)
 - [ ] [supertokens-website:X.Y](https://github.com/supertokens/supertokens-website/X.Y)
 - [ ] [supertokens-auth-react:X.Y](https://github.com/supertokens/supertokens-auth-react/tree/X.Y)
    - [ ] Updated dependencies to use supertokens-website from npm registry
    - [ ] Various browsers - Safari, Firefox, Chrome, Edge
    - [ ] Mobile responsiveness
    - [ ] Make sure using with-typescript example that types are correct for every new configs exposed to users

### Others

 - [supertokens-demo-react](https://github.com/supertokens/supertokens-demo-react/tree/master)
     - [ ] Run and test all the demo apps
 - [ ] Run on NextJS to test that it works fine there
 - [ ] Run on netlify (and hence AWS lambda) to check if it works fine there

### 📚 Documentation (test site)

- [ ] All recipe main documentation update

- [ ] nodejs documentation update

- [ ] auth-react documentation update

- [ ] supertokens-website documentation update

- [ ] community documentation update

- [ ] website changes (test.supertokens.io)
   - [ ] homepage
   - [ ] pricing page feature list
   - [ ] comparison chart in the pricing page

- [ ] Checked for broken links


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
    - [ ] Update SaaS config
    - [ ] Update to tables checked for user count / or to know if a deployment is being used or not
 - [ ] [supertokens-node:X.Y](https://github.com/supertokens/supertokens-node/tree/X.Y)
 - [ ] [supertokens-website:X.Y](https://github.com/supertokens/supertokens-website/tree/X.Y)
 - [ ] [supertokens-auth-react:X.Y](https://github.com/supertokens/supertokens-auth-react/tree/X.Y)

### 🔀 Others

- [supertokens-demo-react:master](https://github.com/supertokens/supertokens-demo-react/tree/master)
   - [ ] Deployed to actual demo sites

- [supertokens-auth-react:master](https://github.com/supertokens/supertokens-auth-react/tree/master)
   - [ ] Update examples to use latest `supertokens-node` and `supertokens-auth-react`

- [next.js:canary](https://github.com/supertokens/next.js/tree/canary/examples/with-supertokens)
   - [ ] Update `with-supertokens` examples to use latest `supertokens-node` and `supertokens-auth-react`

- RedwoodJS
   - [ ] Update [redwood core](https://github.com/supertokens/redwood/tree/main/packages/auth) to use latest `supertokens-auth-react`
   - [ ] Update [playground-auth](https://github.com/supertokens/playground-auth/tree/main) to use latest `supertokens-node` and `supertokens-auth-react`

### 📚 Documentation

- [ ] Pushed to production
- [ ] Post message on discord about new update
