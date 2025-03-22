# Introduction

[https://zhuanlan.zhihu.com/p/64004026](https://zhuanlan.zhihu.com/p/64004026)

The inherent assumption of component technology is that "the same can be reused," but the common part between A and B is smaller than either A or B, which makes it difficult to address coarse-grained software reuse from a theoretical perspective. To solve software reuse at the system level, new developments are needed in software construction theory.

In recent years' technical practices, Docker, React, k8s's Kustomize, and other technologies based on delta concepts have emerged in abundance. Behind all these ad hoc practices lies a unified software construction rule.

Reversible computing proposed a software construction formula based on reversible delta operations:

```
App = Delta x-extends Generator<DSL>
```

It provides a unified theoretical explanation for various practices and indicates their future development direction, such as:

```
DockerApp = DockerBuilder<DockerFile> overlay-fs BaseImage
```

To better demonstrate the content of reversible computing theory, I open-sourced a low-code platform called Nop Platform 2.0, which is similar in goals to JetBrains's MPS product but differs fundamentally in its implementation method. The platform is designed for DSL development.

The Nop platform now includes an example production line. It can generate GraphQL service and front-end pages from Excel data models and allows manual adjustments on the generated code, ensuring that manually written delta code and auto-generated code remain isolated and do not interfere with each other.

In essence, software products developed based on the Nop platform support Delta customization without requiring any special design (e.g., abstracting out expandable interfaces in advance). This enables completely incrementalized customized development, where custom increments are independent of base product code, whether it's for customizing the base product or Nop platform functionality, and no changes are needed to the original source code.

[How to implement customized development without modifying the base product source code](https://zhuanlan.zhihu.com/p/628770810)

The nop-ide-plugin provides debugging and code suggestion features for all DSLs.

Through Excel modeling:
![excel-data-model](../tutorial/excel-model.png)

Using Excel to define business rules:
![decision-matrix](../dev-guide/rule/decision-matrix.png)
![decision-tree](../dev-guide/rule/decision-tree.png)
