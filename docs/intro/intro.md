# 介绍

[https://zhuanlan.zhihu.com/p/64004026](https://zhuanlan.zhihu.com/p/64004026)

组件技术的内在假定是“相同可以复用”，但是A和B的公共部分是比A和B都要小的，这使得组件技术在理论层面就难以解决粗粒度软件复用的问题。
要解决系统级别的软件复用，我们需要在软件构造理论方面做出新的发展。

在最近几年的技术实践中，Docker、React、k8s的Kustomize等基于差量概念的技术层出不穷，在所有这些Ad Hoc的实践背后存在统一的软件构造规律。
可逆计算提出了一个基于可逆差量运算的软件构造公式：

```
App = Delta x-extends Generator<DSL>
```

它可以为各种实践提供统一的理论解释，并为它们指明进一步发展的方向。比如

```
DockerApp = DockerBuilder<DockerFile> overlay-fs BaseImage
```

为了更好的展示可逆计算理论的具体技术内涵，我开源了一个面向DSL开发的低代码平台Nop Platform 2.0，它的目标类似于JetBrains公司的MPS产品，希望实现一个快速开发和扩展DSL的领域语言工作台(Domain Language Workbench )，但它的具体实现方式与MPS有本质不同。
https://github.com/entropy-cloud/nop-entropy

Nop平台现在内置了一个演示用的软件生产线，可以从Excel格式的数据模型自动生成GraphQL服务以及前端页面，然后在自动生成的代码基础上我们可以手工调整，手工编写的差量代码与自动生成的代码相互隔离，不会相互影响。
![delta-pipeline](../tutorial/delta-pipeline.png)

实际上，基于Nop平台开发的软件产品都支持Delta定制机制，应用层代码无需做出任何特殊的设计（比如预先抽象出扩展接口）即可获得完全增量式的定制化开发能力（定制的增量代码完全独立于基础产品代码，定制基础产品或者Nop平台的功能都无需修改原始代码

[如何在不修改基础产品源码的情况下实现定制化开发](https://zhuanlan.zhihu.com/p/628770810)

nop-ide-plugin为所有DSL提供断点调试、语法提示的功能
![xlang-debugger](../tutorial/xlang-debugger.png)

通过Excel来设计数据模型
![excel-data-model](../tutorial/excel-model.png)

使用Excel来定义业务规则
![decision-matrix](../dev-guide/rule/decision-matrix.png)

![decision-tree](../dev-guide/rule/decision-tree.png)
