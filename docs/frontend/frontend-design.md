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

4. 


