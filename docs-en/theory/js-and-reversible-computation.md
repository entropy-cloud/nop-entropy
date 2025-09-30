# JavaScript Programming Paradigms Through the Lens of Reversible Computation

## Introduction: JavaScript’s Success and a Question

According to Stack Overflow’s latest 2019 Developer Survey, JavaScript has been the most commonly used programming language for seven consecutive years. JavaScript is undoubtedly the most widely adopted programming language today, but is it the world’s number one (best) programming language?

He Shijun (alias hax), an architect at Baixing, answered “yes” and has delivered several talks on this topic, presenting many reasons. I largely agree with hax’s conclusion, but I’d like to reinterpret the technical form of Modern JavaScript from a slightly different angle.

My view is: Compared with other programming languages, JavaScript’s core technical approach is closer to the principles of Reversible Computation. JavaScript best practices can be regarded as a rather primitive implementation of Reversible Computation.

## Theoretical Framework: Reversible Computation

Reversible Computation is a new software construction principle proposed by the author. It explicitly elevates “Delta” to a first-class concept, and treats totality as a special case of Delta (`Totality = Identity element + Totality`), building the entire conceptual system around the Delta concept.

In concrete technical form, Reversible Computation can be summarized by the following formula:

```
App = Delta x-extends Generator<DSL>
```

That is, use a domain-specific language (DSL) to describe domain information, perform secondary processing on that domain information via a Generator to produce new structures, and then apply a Delta to supplement and correct the generated result.

From a mathematical perspective, its essence is:

```
Y = F(X) + Δ
```

## I. The Computation Model of Native JavaScript

Modern JavaScript is a dynamically weakly typed programming language with built-in nested object literals and support for modularization. Taken together, these naturally enable the `Y = F(X) + Δ` computation pattern required by Reversible Computation.

Consider a simple example:

a.js

```javascript
var A = {
  a: 1,
  b: 2,
  c: {
    d: 3,
    children: [],
  },

  f1() {
    alert("f1 in A")
  }
}

var names = ["f2", "f3"]
names.forEach(name => {
  A[name] = function() {
    alert(name + " in A")
  }
})

export default A;
```

b.js

```javascript
import A from "./a.js"

var MixinB = {
  b: 3,
  f1() {
    alert("b=" + this.b);
  }
}

var B = Object.assign({}, A, MixinB)

B.f1();  // The f1 method in MixinB overrides the one in A
B.f2();  // Method dynamically generated in a.js

export default B;
```

In the example above, the final object B is obtained by merging the imported A with a Mixin. This computation pattern is ubiquitous in daily JS usage. It has three clear characteristics:

1.  Use object literals to directly express very complex nested structures. Object literals are crucial to a programming language’s internal expressive power. JSON leveraged this to successfully replace XML as the de facto standard for service data exchange. Typical JS modules contain a large number of declarative structures like object A and MixinB.
2.  Module objects are obtained via dynamic construction. Importing a module in JS is not a static symbol linking process like Java; instead, it implicitly executes the module code, which amounts to a form of metaprogramming, allowing modules to be constructed dynamically. In a.js, part of the exported module A is declared directly, while another part is dynamically constructed via code generation.
3.  Use merge-like operations to synthesize information obtained from various modules. All information is expressed via object structures. Complex expressions inevitably need to be split into multiple slices; in a dynamically weakly typed language, we can define a general composition mechanism to synthesize multiple slices. Consequently, in JS we frequently use merging algorithms such as `Object.assign`, `deepMerge`, and `JQuery.extend`.

To summarize, the native JS computation model can be expressed as:

```
Result = merge(ModuleA, ModuleB, ...)
```

## II. The Computation Model of Vue

Compared with native JS, Vue—by implementing a data-driven component model—supports Reversible Computation in a more fine-grained way. Let’s examine Vue from three perspectives:

### 1. Domain Information Expression

Vue components distinguish concepts such as prop, data, method, and watch, effectively decomposing the component object into domain-specific parts.

```javascript
export default {
  extends: Base,
  mixins: [A, B],

  props: {
    size: Number
  },

  computed: {
    normalizedSize: function() {
      return this.size.trim().toLowerCase()
    }
  },

  methods: {
    hello: function() {
      console.log('hello!')
    }
  },

  watch: {
    size: function() {
      console.log("size changed");
    }
  }
}
```

### 2. Dynamic Construction

A Vue component is just a normal JS object, so it can be generated in various ways. A particularly notable approach is using the Vuex framework.

```javascript
const store = new Vuex.Store({
  state: {
    count: 0
  },
  mutations: {
    increment(state) {
      state.count++
    }
  }
})

import { mapState } from 'vuex'

export default {
  name: 'test',
  store: store,
  data() {
    return {}
  },
  computed: {
    ...mapState([
      "count" // Map this.count to this.$store.state.count
    ])
  },
  methods: {
    ...mapActions([
      // Map `this.increment()` to `this.$store.dispatch('increment')`
      'increment',
      // Map `this.incrementBy(amount)` to `this.$store.dispatch('incrementBy', amount)`
      'incrementBy'
    ])
  }
}
```

Vuex further abstracts Vue components by separating UI-independent core business logic into an independent Store object. In other words, compared with ordinary Vue components, for describing business logic the Store object is a better domain description language, and functions like `mapState`/`mapActions` provide a dynamic construction mechanism to generate Vue components based on the Store description.

### 3. Merge Strategies

Vue components can be merged via extend, mixin, and other methods (though their internal implementations are essentially the same).

```javascript
// Define a mixin object
var myMixin = {
  created: function() {
    this.hello()
  },
  methods: {
    hello: function() {
      console.log('hello from mixin!')
    }
  }
}

// Define a component that uses the mixin
var Component = Vue.extend({
  mixins: [myMixin],
  methods: {
    hello2: function() {}
  }
})
```

Vue.mixin differs from Object.assign in that it does not simply override by name. Instead, it applies different merge strategies to different parts of a Vue component. These strategies are centrally registered in `Vue.config.optionMergeStrategies`, and you can even extend and implement custom merge strategies.

```javascript
// Vue framework source code
// Option overwriting strategies are functions that handle
// how to merge a parent option value and a child option
// value into the final value.
var strats = config.optionMergeStrategies;
// ...
strats.props =
strats.methods =
strats.inject =
strats.computed = function(
  parentVal,
  childVal,
  vm,
  key
) {
  if (childVal && "development" !== 'production') {
    assertObjectType(key, childVal, vm);
  }
  if (!parentVal) {
    return childVal
  }
  var ret = Object.create(null);
  extend(ret, parentVal);
  if (childVal) {
    extend(ret, childVal);
  }
  return ret
};
strats.provide = mergeDataOrFn;

// The default merge strategy is direct overwrite
var defaultStrat = function(parentVal, childVal) {
  return childVal === undefined ?
    parentVal :
    childVal
};
```

Vue’s computation model can be summarized with:

```
Component = mergeStrategy(BaseClass, MixinA, MixinB, ...)
```

## III. The Computation Model of Webpack

Webpack is an indispensable heavyweight module bundler in modern front-end development. As a large tool system, its design philosophy can also be analyzed and understood through Reversible Computation theory. In fact, analysis shows that Webpack’s support for Reversible Computation is even deeper and more comprehensive than that of the smaller Vue framework.

Before Webpack, front-end “workflow” tools mainly included Gulp/Grunt. These tools abstract the automated task execution flow in front-end development, enabling automatic page refresh, JS transpilation, Less compilation, and other automated operations to reduce manual steps. Webpack, by contrast, is a module bundler. It treats all resource files (css, js, image, vue, etc.) uniformly as modules (Everything is Module), then introduces the concept of Loader to load modules, analyze dependencies among modules, and finally uses Plugins to perform various synthesis processes.

![Webpack Concept Map](https://via.placeholder.com/400x200?text=Webpack+Concept+Map)
*(Diagram: Everything is Module -> Loader -> Plugin)*

Conceptually, Webpack and Gulp/Grunt are not direct replacements; in theory, each has its own space. However, in practice, once you adopt Webpack, Gulp/Grunt rarely come into play. The reason is simple: Gulp/Grunt aim to automate and optimize front-end workflows, while Webpack provides a novel abstraction mechanism, establishing a domain-specific declarative solution directly for single-page application bundling. The domain model provided by Webpack is undoubtedly simpler and more straightforward than general-purpose procedural flow handling, and many global optimizations can be guided by the domain model, thereby overcoming numerous issues that Gulp/Grunt face when bundling large SPAs.

Let’s analyze Webpack’s computation model from three aspects:

### 1. Domain Information Expression

Webpack completely transcends the limitations of JS object representations by expressing domain descriptions directly via domain-specific languages (DSLs). scss/less extend CSS and can be seen as DSLs for styles, while vue is a DSL for single-page components. Even JS files themselves are treated as a new kind of language file with the latest ECMAScript syntactic sugar, which can be transformed into browser-recognizable ES5 syntax via babel-loader. We can write our own Babel plugins to insert custom syntax rules.

### 2. Dynamic Construction

Webpack’s Loader mechanism extends the semantics of JS’s built-in import syntax. An import now effectively returns the result of executing a Loader.

```javascript
import App from "./a.vue"
// Will be converted to require("./a.vue"),
// then, via webpack's loader mechanism, vue-loader is invoked to load the .vue file
// and transform it into the corresponding JS code
```

### 3. Merge Strategies

All synthesis logic in Webpack is implemented by various Plugins. Plugins can uniformly analyze the loading results of all Loaders to perform splitting/merging/optimization and more.

The entire operation of Webpack can be expressed as follows (the symbol ⊕ denotes the synthesizing effect of Plugins):

```
Bundling result = Plugin⊕( Loader₁(Resource₁), Loader₂(Resource₂), ... )
```

Beyond the external bundling model, Webpack’s own configuration management also conforms to the principles of Reversible Computation.

1.  Express WebpackConfig via a very complex JS object

    ```javascript
    const webpackConfig = {
      mode: 'production',
      output: {
        path: path.resolve(process.cwd(), './lib'),
        publicPath: '/dist/',
        filename: '[name].js',
        chunkFilename: '[id].js',
        libraryTarget: 'commonjs2'
      },
      module: {
        rules: [{
          test: /\.vue$/,
          loader: 'vue-loader',
          options: {
            compilerOptions: {
              preserveWhitespace: false
            }
          }
        }, ]
      },
      plugins: [
        new ProgressBarPlugin(),
        new VueLoaderPlugin()
      ]
    };
    module.exports = webpackConfig;
    ```

    Implementing configurations of similar complexity in Java would require heavyweight tools such as IoC containers, XML object mapping, expression engines, etc. In contrast, in JS a simple `require("webpack.common.js")` gets it all done.

2.  Generate WebpackConfig via code

    For example, in the build scripts of the element-ui component library, config.js dynamically generates Webpack’s externals configuration.

    ```javascript
    var Components = require('../components.json');
    var externals = {};
    
    Object.keys(Components).forEach(function(key) {
      externals[`element-ui/packages/${key}`] = `element-ui/lib/${key}`;
    });
    
    exports.externals = externals;
    ```

3.  Merge Strategies

    Webpack internally provides the webpack-merge module, which enables “smart” merges among multiple configuration objects. Compared with Vue’s merge strategies, webpackMerge is more customizable, flexible, and powerful.

## IV. Reversible Starts from “Nothing”

In my view, the future development of software will inevitably bring the Delta concept to the forefront. Reflected in programming languages, this demands that languages directly express Delta and provide built-in operations related to Delta. The first step in this transformation is to break away from historical mental models: we must express not only “presence,” but also “absence.” In this regard, JS still holds many advantages over other languages.

Modern programming increasingly emphasizes declarative approaches—separating expression from execution—so the order and content of expressions are no longer constrained by the actual order and content of machine instructions. For example, in JS, a Promise represents a value that can be obtained in the future; it can be passed around in the program as a parameter, but only when we explicitly call await and wait for it to return do we actually get the value. Under traditional programming concepts, a function’s return indicates completion of execution; if execution is asynchronous, you can only receive notifications via callbacks, and at the conceptual level you cannot directly define and use a “future value.”

```javascript
async function asyncValue() {
  return new Promise((resolve, reject) => {
    setTimeout(() => resolve('a'), 1000)
  });
}

var result = asyncValue();
doSomething(result);

async function doSomething(input) {
  console.log('begin');
  var result = await input;
  console.log('1 second later: result=' + result);
}
```

A future value may not exist yet, but at least it is expected to arrive. What if we don’t even know whether the future will arrive—can we still assign it an expressive form? Vue 2.0 has a rather awkward `$set` function: whenever we add a new property to the data object, we must call `this.$set("x", xx)` rather than directly using `this.x = xx`. The root cause is that under ES5 syntax, when a property does not yet exist, no information can be expressed about it, so Vue.js cannot capture the operation of adding a new property and enhance it to a reactive variable. ES6’s Proxy provides a solution to this problem.

```javascript
var obj = new Proxy({}, {
  get: function(target, key, receiver) {
    console.log(`getting ${key}!`);
    return Reflect.get(target, key, receiver);
  },
  set: function(target, key, value, receiver) {
    console.log(`setting ${key}!`);
    return Reflect.set(target, key, value, receiver);
  },
  deleteProperty: function(target, key) {
    console.log(`deleting ${key}!`);
    return Reflect.deleteProperty(target, key);
  }
});

obj.x = 3;
console.log(obj.x);
delete obj.x;
```

Going further, we might want not only to express “it doesn’t exist now but will exist in the future” or “it doesn’t exist now and may exist in the future,” but to express pure “absence.” JS provides a designated representation for this: `undefined`.

```javascript
var o = { a: 1, b: 2 };
var patch = { a: undefined, b: 3, c: null };
var result = Object.assign(o, patch);
console.log(JSON.stringify(result)); // result is {b:3, c:null}
```

## Conclusion and Practice

The low-code platform NopPlatform, designed based on Reversible Computation theory, is open source:

*   Gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
*   GitHub: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
*   Tutorial: [tutorial](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)

<!-- SOURCE_MD5:1ee13aff3ea0656c779dd7c8dfd40f61-->
