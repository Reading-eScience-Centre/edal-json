System.config({
  "baseURL": "/",
  "transpiler": "babel",
  "babelOptions": {
    "optional": [
      "runtime"
    ]
  },
  "paths": {
    "*": "*.js",
    "github:*": "jspm_packages/github/*.js",
    "npm:*": "jspm_packages/npm/*.js"
  },
  "bundles": {
    "build": [
      "npm:core-js@0.9.18/library/modules/$.fw",
      "npm:core-js@0.9.18/library/modules/$.def",
      "npm:core-js@0.9.18/library/modules/$.get-names",
      "github:components/jquery@2.1.4/jquery",
      "github:acigna/jquery-ajax-native@1.0.1/src/jquery-ajax-native",
      "npm:iota-array@1.0.0/iota",
      "npm:base64-js@0.0.8/lib/b64",
      "npm:ieee754@1.1.6/index",
      "npm:is-array@1.0.1/index",
      "npm:uniq@1.0.1/uniq",
      "npm:process@0.10.1/browser",
      "github:creationix/msgpack-js-browser@0.1.4/msgpack",
      "github:Leaflet/Leaflet@0.7.3/dist/leaflet-src",
      "github:leaflet-extras/leaflet-providers@1.1.1/leaflet-providers",
      "app/interpolation",
      "app/palettes",
      "app/utils",
      "npm:core-js@0.9.18/library/fn/object/create",
      "npm:core-js@0.9.18/library/fn/object/get-own-property-descriptor",
      "npm:core-js@0.9.18/library/fn/object/define-property",
      "npm:babel-runtime@5.6.7/helpers/class-call-check",
      "npm:core-js@0.9.18/library/modules/es6.math",
      "github:systemjs/plugin-css@0.1.13/css",
      "npm:core-js@0.9.18/library/modules/$",
      "github:components/jquery@2.1.4",
      "github:acigna/jquery-ajax-native@1.0.1",
      "npm:iota-array@1.0.0",
      "npm:base64-js@0.0.8",
      "npm:ieee754@1.1.6",
      "npm:is-array@1.0.1",
      "npm:uniq@1.0.1",
      "npm:process@0.10.1",
      "github:creationix/msgpack-js-browser@0.1.4",
      "github:Leaflet/Leaflet@0.7.3",
      "github:leaflet-extras/leaflet-providers@1.1.1",
      "app/controls",
      "npm:babel-runtime@5.6.7/core-js/object/create",
      "npm:babel-runtime@5.6.7/core-js/object/get-own-property-descriptor",
      "npm:babel-runtime@5.6.7/core-js/object/define-property",
      "npm:core-js@0.9.18/library/fn/math/trunc",
      "github:systemjs/plugin-css@0.1.13",
      "npm:core-js@0.9.18/library/modules/es6.object.statics-accept-primitives",
      "npm:buffer@3.2.2/index",
      "github:jspm/nodelibs-process@0.1.1/index",
      "npm:babel-runtime@5.6.7/helpers/inherits",
      "npm:babel-runtime@5.6.7/helpers/get",
      "npm:babel-runtime@5.6.7/helpers/create-class",
      "npm:babel-runtime@5.6.7/core-js/math/trunc",
      "npm:core-js@0.9.18/library/fn/object/keys",
      "npm:buffer@3.2.2",
      "github:jspm/nodelibs-process@0.1.1",
      "app/TileLayer.Coverage",
      "npm:babel-runtime@5.6.7/core-js/object/keys",
      "github:jspm/nodelibs-buffer@0.1.0/index",
      "npm:cwise-compiler@1.1.0/lib/compile",
      "github:jspm/nodelibs-buffer@0.1.0",
      "npm:cwise-compiler@1.1.0/lib/thunk",
      "npm:is-buffer@1.0.2/index",
      "npm:cwise-compiler@1.1.0/compiler",
      "npm:is-buffer@1.0.2",
      "npm:cwise-compiler@1.1.0",
      "npm:ndarray@1.0.18/ndarray",
      "app/ndarray-ops-null",
      "npm:ndarray@1.0.18",
      "app/main"
    ]
  },
  "buildCSS": false
});

System.config({
  "map": {
    "babel": "npm:babel-core@5.6.7",
    "babel-runtime": "npm:babel-runtime@5.6.7",
    "core-js": "npm:core-js@0.9.18",
    "css": "github:systemjs/plugin-css@0.1.13",
    "cwise-compiler": "npm:cwise-compiler@1.1.0",
    "jquery": "github:components/jquery@2.1.4",
    "jquery-ajax-native": "github:acigna/jquery-ajax-native@1.0.1",
    "leaflet": "github:Leaflet/Leaflet@0.7.3",
    "leaflet-providers": "github:leaflet-extras/leaflet-providers@1.1.1",
    "msgpack": "github:creationix/msgpack-js-browser@0.1.4",
    "ndarray": "npm:ndarray@1.0.18",
    "ndarray-ops": "npm:ndarray-ops@1.2.2",
    "github:jspm/nodelibs-buffer@0.1.0": {
      "buffer": "npm:buffer@3.2.2"
    },
    "github:jspm/nodelibs-process@0.1.1": {
      "process": "npm:process@0.10.1"
    },
    "npm:babel-runtime@5.6.5": {
      "process": "github:jspm/nodelibs-process@0.1.1"
    },
    "npm:babel-runtime@5.6.7": {
      "process": "github:jspm/nodelibs-process@0.1.1"
    },
    "npm:buffer@3.2.2": {
      "base64-js": "npm:base64-js@0.0.8",
      "ieee754": "npm:ieee754@1.1.6",
      "is-array": "npm:is-array@1.0.1"
    },
    "npm:core-js@0.9.18": {
      "fs": "github:jspm/nodelibs-fs@0.1.2",
      "process": "github:jspm/nodelibs-process@0.1.1",
      "systemjs-json": "github:systemjs/plugin-json@0.1.0"
    },
    "npm:cwise-compiler@1.1.0": {
      "process": "github:jspm/nodelibs-process@0.1.1",
      "uniq": "npm:uniq@1.0.1"
    },
    "npm:is-buffer@1.0.2": {
      "buffer": "github:jspm/nodelibs-buffer@0.1.0"
    },
    "npm:ndarray-ops@1.2.2": {
      "cwise-compiler": "npm:cwise-compiler@1.1.0"
    },
    "npm:ndarray@1.0.18": {
      "iota-array": "npm:iota-array@1.0.0",
      "is-buffer": "npm:is-buffer@1.0.2"
    }
  }
});

