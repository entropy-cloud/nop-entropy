# 从可逆计算看JavaScript的编程范式

## 引言：JavaScript的成功与一个问题

根据 Stack Overflow 最新发布的 2019 年开发者年度调查报告，JavaScript 已经连续第 7 年蝉联最常用的编程语言。JavaScript 无疑是目前受众最广的编程语言，但它是否是世界第一（最好）的编程语言？

百姓网的架构师贺师俊（网名 hax）的回答是“是”，并且针对这个问题做过好几次专题报告，阐述了许多理由。我基本同意 hax 的论断，不过我想从一个稍微不同的角度重新解读一下所谓 Modern JavaScript 的技术形式。

**我的观点是：JavaScript 相比于其他编程语言，它的核心技术方案更接近可逆计算的技术原理，可以将 JavaScript 的最佳实践看作是可逆计算的一种比较原始的实现形式。**

## 理论框架：可逆计算

可逆计算是笔者所提出的一种新的软件构造原理，它明确提出应该将“差量”提升为第一性的概念，将全量看作是差量的一种特例（`全量 = 单位元 + 全量`），并围绕差量概念去建立整个领域概念体系。

在具体的技术形式上，可逆计算可以用如下公式来概括：

```
App = Delta x-extends Generator<DSL>
```

也就是说，采用领域特定语言（DSL）来描述领域信息，通过生成器（Generator）对领域信息进行二次加工产生新的结构，然后再通过一个差量（Delta）对生成结果进行补充修正。

如果从数学角度来解读，它的本质其实就是：

```
Y = F(X) + Δ
```

## 一、原生 JavaScript 的计算模式

现代 JavaScript 是一种内置嵌套对象字面量的、支持模块化的、动态弱类型的程序语言。这三点结合在一起，就很自然的可以实现可逆计算所要求的 `Y = F(X) + Δ` 计算模式。

下面来看一个简单的例子：

**a.js**

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

**b.js**

```javascript
import A from "./a.js"

var MixinB = {
  b: 3,
  f1() {
    alert("b=" + this.b);
  }
}

var B = Object.assign({}, A, MixinB)

B.f1();  // MixinB 中的 f1 方法覆盖了 A 中的 f1
B.f2();  // a.js 中动态生成的方法

export default B;
```

在上面的例子中，最终我们所得到的对象 B 由导入的 A 和一个 Mixin 合并得到。这种计算模式在 JS 的日常使用中可以说是司空见惯。它有三个明显特征：

1.  **使用对象字面量直接表达很复杂的嵌套结构。** 对象字面量对于程序语言的内在表达能力而言至关重要。JSON 正是利用了这一点，成功取代 XML 成为了服务数据交换的事实标准。一般的 JS 模块中都存在着大量类似对象 A 和 MixinB 的声明式结构。
2.  **模块对象通过动态构建的方式得到。** JS 导入模块时并不是一种类似 Java 的静态符号链接过程，而是隐含的会执行模块代码，这相当于是某种元编程（MetaProgramming）的能力，使得模块可以被动态构建出来。a.js 中导出的模块 A，它的一部分信息是直接声明得到的，但也有一部分信息是通过代码生成动态构建出来的。
3.  **使用类似 merge 的操作对从各个模块得到的信息进行综合。** 所有的信息都通过对象结构来表达，复杂的表达必然需要拆解为多个切片，而在动态弱类型语言中，我们可以定义一种通用的合成机制来实现多个切片的综合。因此，在 JS 中我们会大量使用 `Object.assign`, `deepMerge`, `JQuery.extend` 等合并算法。

总结起来，原生 JS 的计算模式可以表达为如下公式：

```
结果 = 合并(模块A, 模块B, ...)
```

## 二、Vue 的计算模式

相比于原生 JS，实现了数据驱动组件模型的 Vue 框架对于可逆计算的支持明显要更加细致一些。我们还是从三个方面来考察一下 Vue 框架的情况：

### 1. 领域信息表达

Vue 组件对 prop, data, method, watch 等概念进行了区分，相当于是把组件对象的构成进行了领域特定的分解。

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

### 2. 动态构建

Vue 组件就是一个普通的 JS 对象，因此可以使用各种方法来生成。一个比较特别的方法是使用 Vuex 框架。

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
      "count" // 将 this.count 映射为 this.$store.state.count
    ])
  },
  methods: {
    ...mapActions([
      // 将 `this.increment()` 映射为 `this.$store.dispatch('increment')`
      'increment',
      // 将 `this.incrementBy(amount)` 映射为 `this.$store.dispatch('incrementBy', amount)`
      'incrementBy'
    ])
  }
}
```

Vuex 相当于是在 Vue 组件的基础上进一步进行抽象，将 Vue 组件中与界面无关的核心业务逻辑剥离到独立的 Store 对象中。或者说，相比于普通的 Vue 组件，对于描述业务逻辑而言，Store 对象是更好的一种领域描述语言，而 `mapState`/`mapActions` 等函数所提供的是根据 Store 描述来动态生成 Vue 组件的一种动态构建机制。

### 3. 合并策略

Vue 组件可以通过 extend, mixin 等多种方法实现合并（但其实其内部实现都是一样的）。

```javascript
// 定义一个混入对象
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

// 定义一个使用混入对象的组件
var Component = Vue.extend({
  mixins: [myMixin],
  methods: {
    hello2: function() {}
  }
})
```

Vue.mixin 机制与 Object.assign 不同，它不是简单的按名称覆盖，而是对 Vue 组件的不同组分使用不同的合并策略。这些合并策略统一注册在 `Vue.config.optionMergeStrategies` 中，甚至我们还可以扩展实现自定义的合并策略。

```javascript
// Vue框架源码
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

// 缺省合并策略为直接覆盖
var defaultStrat = function(parentVal, childVal) {
  return childVal === undefined ?
    parentVal :
    childVal
};
```

可以用一个公式来总结 Vue 的计算模式：

```
组件 = 合并策略(基类, 混入A, 混入B, ...)
```

## 三、Webpack 的计算模式

Webpack 是目前前端开发不可或缺的重量级模块打包工具。作为一个大型工具系统，它的设计思想同样可以基于可逆计算理论进行分析和理解，而且经过分析可以发现，Webpack 对于可逆计算的支持相比于小型的 Vue 框架要更加深入和全面。

在 Webpack 出现之前，前端开发所使用的所谓工作流工具主要是 Gulp/Grunt，这些工具对前端的自动化任务执行流程进行了抽象，可以实现自动刷新页面、转译 js、编译 less 等一系列自动化工作，减少手工操作过程。而 Webpack 是所谓的模块打包器，它将所有资源文件（css, js, image, vue 等）都统一看作是模块（Everything is Module），然后引入所谓 Loader 概念来装载模块，并分析模块之间的依赖关系，最后利用 Plugin 来实现各种综合处理过程。

![Webpack概念图](https://via.placeholder.com/400x200?text=Webpack+Concept+Map)
*(示意图：Everything is Module -> Loader -> Plugin)*

从概念层面上说，Webpack 与 Gulp/Grunt 并不是直接的替代关系，按理说应该是各有自己的生存空间。但是实际情况却是，自从用上 Webpack，就基本上没有 Gulp/Grunt 什么事了。这里面的原因其实很简单，Gulp/Grunt 致力于自动化和优化前端的工作流，而 Webpack 却是提供了一种新的抽象机制，直接对前端单页应用打包建立了一套领域特定的描述式方案。Webpack 所提供的领域模型，比起通用的编程式流程处理，无疑更加简单直接，而且很多全局优化可以在领域模型的指导下进行，从而克服了 Gulp/Grunt 打包大型单页应用时的诸多问题。

下面对于 Webpack，我们仍然从三个方面来分析一下它所体现的计算模式：

### 1. 领域信息表达

Webpack 彻底超越了 JS 对象表示形式的限制，直接通过领域特定语言(DSL)来实现领域描述。scss/less 是 css 的扩展，可以看作是样式领域的 DSL，而 vue 是单页面组件的 DSL，甚至 js 文件自身也被看作是添加了 ECMAScript 最新语法糖的一种新的语言文件，它可以通过 babel-loader 转化为浏览器可以识别的 ES5 语法，我们可以自己写 babel 插件来插入自定义的语法规则。

### 2. 动态构建

Webpack 的 Loader 机制扩展了 JS 内置的 import 语法的语义。现在的 import 实际返回的是 Loader 执行的结果。

```javascript
import App from "./a.vue"
// 将会被转换为 require("./a.vue"),
// 然后通过 webpack 的 loader 机制，调用 vue-loader 来加载 vue 文件，并转换生成对应的 js代码
```

### 3. 合并策略

Webpack 中所有的综合处理逻辑由各路 Plugin 负责实现。Plugin 可以对所有 Loader 的装载结果进行统一分析，实现拆分/合并/优化等工作。

整个 Webpack 的运作原理可以用如下公式来表达（符号 ⊕ 表示 Plugin 所起的综合作用）：

```
打包结果 = Plugin⊕( Loader₁(资源₁), Loader₂(资源₂), ... )
```

除了对外提供的打包模型之外，Webpack 自身的配置管理也是符合可逆计算原理的。

1.  **通过一个很复杂的 JS 对象来描述 WebpackConfig**

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

    如果是在 Java 中实现同样复杂度的配置，需要动用 IoC 容器、XML 对象映射、表达式引擎等一系列重型工具。相比较之下，JS 中仅仅需要一句 `require("webpack.common.js")` 就全部搞定了。

2.  **可以通过代码生成 WebpackConfig**

    例如 element-ui 控件库的构建脚本中，config.js 文件动态生成 webpack 的 externals 配置。

    ```javascript
    var Components = require('../components.json');
    var externals = {};
    
    Object.keys(Components).forEach(function(key) {
      externals[`element-ui/packages/${key}`] = `element-ui/lib/${key}`;
    });
    
    exports.externals = externals;
    ```

3.  **合并策略**

    Webpack 内部提供了 webpack-merge 模块，通过它可以实现多个配置对象之间的“智能”合并。与 Vue 的合并策略相比，webpackMerge 的可定制化程度更高，更灵活，更强大。

## 四、可逆从“无”开始

在我看来，未来软件世界的发展必然是一个差量概念逐渐走上前台的过程。反映到程序语言中，它必然要求语言可以直接表达差量，并且内置差量相关的运算操作。而这方面的第一步改造是要跳出历史上的思维定式，不仅仅要表达“有”，还要能够表达“无”。JS 在这方面，相对于其他语言还是存在着不少优势。

现代编程体系越来越强调所谓描述式，也就是表达与执行分离，我们表达的顺序和内容不再受限制于实际计算机指令的执行顺序和内容。比如 JS 中的 Promise 对象，它表示了未来可以获得的一个值，在程序中可以作为参数传来传去，但是只有当我们明确调用 await 指令，并等待 await 返回时，我们才实际获取到了值。而传统编程概念中，函数的返回就表示执行完毕，如果是异步执行，则只能通过回调函数获取通知，在概念层面上我们并无法直接定义和使用“未来的值”。

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

未来的值虽然现在未来，但毕竟未来可期。但如果根本不知道未来是否会来，那能否给它分配一个表达形式呢？Vue2.0 中有一个非常丑陋的 `$set` 函数，每当我们在 data 对象上新增属性时，都需要调用 `this.$set("x", xx)` 函数来设置一下，而不能直接使用 `this.x = xx` 这种属性赋值语法。归根结底，其原因在于按照 ES5 的语法，在一个属性尚不存在的时候，无法进行任何信息表达，所以 Vue.js 无法捕获新增属性这一操作，并把新增的属性增强为响应式变量。ES6 中的 Proxy 对这个问题提供了一个解决方案。

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

如果更进一步，我们不仅仅要表达“虽然现在没有，但是未来会有”，或者“现在没有，未来可能会有”，我们就是要表达“没有”，JS 中也存在一个专门的表示：`undefined`。

```javascript
var o = { a: 1, b: 2 };
var patch = { a: undefined, b: 3, c: null };
var result = Object.assign(o, patch);
console.log(JSON.stringify(result)); // 结果为 {b:3, c:null}
```

## 结语与实践

基于可逆计算理论设计的低代码平台 NopPlatform 已开源：

*   **Gitee**: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
*   **GitHub**: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
*   **开发示例**: [tutorial](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
