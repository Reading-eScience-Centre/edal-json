## Installation 
(Note: prerequisites = [Node.js](https://nodejs.org/download/)):

#### Step 1: Install jspm CLI globally

```
npm install jspm/jspm-cli -g
```

#### Step 2: From a terminal in the root of the html/ directoy run:

```
npm install
```

and, then:

```
jspm install
```

## Serve It
(i.e. starts browser-sync and serves the index.html in src directory to localhost:3000):

```
npm run serveit
```

## Switch between build mode (manual bundle creation) and dev mode

```
npm run buildMode
```
(needs to be run again if any code is changed)

```
npm run devMode
```
(code can be changed and a browser refresh is enough to see the changes)
