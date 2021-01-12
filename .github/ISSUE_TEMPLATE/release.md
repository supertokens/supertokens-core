---
name: 📅 Release
about: Release checklist
labels: 'feature' 'documentation'
---
## Notes

TODO before creating this issue:
 - Fill in "Feature" section.
 - Remove irrelevant checkboxes.
 - Replace X.Y version in following sections.
 - Remove the current (Notes) section.

## Feature

Description of the new feature


## 📅 Release checklist

### 🤖 Repositories impacted by the release

- [ ] [supertokens-core:X.Y](https://github.com/supertokens/supertokens-core/tree/X.Y)

  **Tracking issues**
    - [ ] [Issue 1](https://github.com/supertokens/supertokens-core/issues/XXX)
    - [ ] [Issue 2](https://github.com/supertokens/supertokens-core/issues/XXX)

  **Status**
    - [ ] In progress
    - [ ] Done


 - [ ] [supertokens-node:X.Y](https://github.com/supertokens/supertokens-node/tree/X.Y)
   
   **Tracking issues**
     - [ ] [Issue 1](https://github.com/supertokens/supertokens-node/issues/XXX)
     - [ ] [Issue 2](https://github.com/supertokens/supertokens-node/issues/XXX)
   
   **Status**
     - [ ] In progress
     - [ ] Done

 - [supertokens-website:X.Y](https://github.com/supertokens/supertokens-website/tree/X.Y)
   
   **Tracking issues**
     - [ ] [Issue 1](https://github.com/supertokens/supertokens-website/issues/XXX)
     - [ ] [Issue 2](https://github.com/supertokens/supertokens-website/issues/XXX)

   **Status**
     - [ ] In progress
     - [ ] Done

 - [supertokens-auth-react:X.Y](https://github.com/supertokens/supertokens-auth-react/tree/X.Y)
   
   **Tracking issues**
      - [ ] [Issue 1](https://github.com/supertokens/supertokens-auth-react/issues/XXX)
      - [ ] [Issue 2](https://github.com/supertokens/supertokens-auth-react/issues/XXX)

   **Status**
     - [ ] In progress
     - [ ] Done

 - [supertokens-react-themes:X.Y](https://github.com/supertokens/supertokens-react-themes/tree/X.Y)
   
   **Status**
     - [ ] In progress
     - [ ] [PR]() Ready using supertokens/supertokens-auth-react#X.Y and supertokens/supertokens-node#X.Y github repositories in package.json
     - [ ] [PR]() updated using npm registry.
     - [ ] Release [theme demo app](https://supertokens-react-themes.surge.sh) on surge using `npm run surge`

 - [supertokens-nextjs-demo:master](https://github.com/supertokens/supertokens-nextjs-demo/tree/master)
   
   **Status**
     - [ ] In progress
     - [ ] [PR]() Ready using supertokens/supertokens-auth-react#X.Y and supertokens/supertokens-node#X.Y github repositories in package.json
     - [ ] [PR]() updated using npm registry.
     - [ ] Merged to master


 - [supertokens-demo-react:master](https://github.com/supertokens/supertokens-nextjs-demo/tree/master)
   
    **Status**
     - [ ] In progress
     - [ ] [PR]() Ready using supertokens/supertokens-auth-react#X.Y and supertokens/supertokens-node#X.Y github repositories in package.json
     - [ ] [PR]() updated using npm registry.
     - [ ] Deployed



### 💻 NPM and core release


 - [ ] [supertokens-core:X.Y](https://github.com/supertokens/supertokens-core/tree/X.Y)
   - [ ] Dev Tag
   - [ ] Published

 - [ ] [supertokens-node:X.Y](https://github.com/supertokens/supertokens-node/tree/X.Y)
   - [ ] Dev Tag
   - [ ] Published

 - [supertokens-website:X.Y](https://github.com/supertokens/supertokens-website#X.Y)
   - [ ] Dev Tag
   - [ ] Published

 - [supertokens-auth-react:X.Y](https://github.com/supertokens/supertokens-auth-react/tree/X.Y)
   - [ ] Dev Tag
   - [ ] Published

 - [supertokens-react-themes:X.Y](https://github.com/supertokens/supertokens-react-themes/tree/X.Y)
   - [ ] [PR]() ready using supertokens/supertokens-auth-react#X.Y
   - [ ] [PR]() with new supertokens-auth-react  
   - [ ] Published

### 📚 Documentation

- [ ] EmailPassword main documentation update
   - [ ] In progress [PR]()
   - [ ] Deployed on [test site](https://test.supertokens.io/docs/emailpassword/introduction)
   - [ ] Deployed on [production site](https://supertokens.io/docs/emailpassword/introduction)

- [ ] nodejs documentation update
   - [ ] In progress [PR]()
   - [ ] Deployed on [test site](https://test.supertokens.io/docs/nodejs/installation)
   - [ ] Deployed on [production site](https://supertokens.io/docs/nodejs/installation)

- [ ] auth-react documentation update
   - [ ] In progress [PR]()
   - [ ] Deployed on [test site](https://test.supertokens.io/docs/auth-react/introduction)
   - [ ] Deployed on [production site](https://supertokens.io/docs/auth-react/installation)
