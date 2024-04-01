# 前端架构设计

整体设计思想是在前端建立微内核架构，提供一组公共的插件注册机制，可以支持amis、opentiny等多种前端低代码引擎技术。

## 一. 整体结构

1. packages目录下提供基本的sdk实现，建立基本的微内核架构

2. plugins通过插件机制提供各类低代码技术的集成，amis作为plugin引入

3. shells提供整体程序框架，包括前端菜单、框架页面、安全认证等。

4. apps集成shells、plugins、packages的功能构建完整应用。因为程序整体已经采用微内核技术，所以apps仅起粘结作用，功能并不复杂。nop-site提供一个示例性的粘结应用。

## 二. 核心模块功能

1. nop-core中定义基本的Adapter接口和Registry接口，它的实现与vue和react框架均无关

2. nop-vue-core、nop-react-core、nop-vue-react提供vue和react的集成支持，并提供在vue和react混合调用的帮助函数

3. nop-graph-designer提供抽象的图形设计器，使用react实现，内部具体的流程图和属性编辑均作为插件引入。

## 插件设计和动态加载

```
Plugin = Module + PluginFunctions
```

单个页面依赖的模块可以事前加载

1. 在通用的模块管理机制之上建立插件管理机制。每个插件对应一个模块入口。目前使用SystemJs来加载模块。
2. 模块的动态更新可以使用HMR（Hot Module Replacement），但是目前Nop平台前端比较简单，一般整体更新即可，暂时还不需要HMR
3. 动态加载的基本单元是Module。一个模块可以包含多个组件

## Store

```
Store = State + Methods + Scope
```

在zustand基础上增加统一的架构支持。

Store的作用将替代Event处理。真正需要原生Event的逻辑都属于底层交互抽象层，在已经建立好抽象的业务层不会使用UI专有的Event概念，只会使用业务直接相关的
状态数据和处理函数，也就是Store对象。

1. Store也是一个Module。Module中包含createStore函数用于创建Store。
2. 引入StoreModule的节点会自动创建Store，然后存放在Scope上下文对象中，实现类似于词法作用域的变量查找链。框架层面使用useContext机制来提供Store Scope。store的传递不再需要逐级通过props传递，可以直接作为implicit context传递。
3. 构造函数主要可以传入从父Store继承的部分，以及初始化的数据集
4. Store采用不可变数据集实现比较简单，它的信息衍生方向可预测性更强。应该可以为zustand增加Vue3响应式适配。
5. 子节点可以看到父节点提供的Store，并直接触发store中的函数。一般情况下应该不需要兄弟节点之间的通信。如果有共享信息部分，都可以考虑直接提升到父节点。
6. 配置时可以使用属性绑定表达式语法

## Schema

```
 component = compile(schema);
```

json格式的schema是面向编辑的表达。运行前可以经由compile函数编译得到component

RenderContext提供低代码运行时支持，包含两个函数:

1. render： 直接将schema转换为虚拟DOM
2. invokeApi: 执行Api对象。 Api不仅仅是远程调用，各类前台触发函数都可以被转换为Api配置格式。

## Render

```
render(name, schema, options, {props, store})
```

render函数的作用就是生成虚拟DOM树。

1. type属性通过resolve函数映射到组件定义。
2. resolve总是返回Component定义，对于异步加载，可以返回一个AsyncWrapperComponent

## 框架支持

1. xui:schema-type
2. xui:import
3. xui:store-lib
4. xui:store-init-data
5. xui:store-inherit
