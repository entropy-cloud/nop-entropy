# Introduction

[https://zhuanlan.zhihu.com/p/64004026](https://zhuanlan.zhihu.com/p/64004026)

The implicit assumption of component technology is “identical can be reused,” but the common part of A and B is smaller than both A and B, which makes component technology theoretically ill-suited to address coarse-grained software reuse.
To solve system-level software reuse, we need new developments in software construction theory.

In recent years, technologies based on the Delta concept—such as Docker, React, and k8s’s Kustomize—have emerged in abundance. Behind all these Ad Hoc practices lies a unified law of software construction.
Reversible Computation proposes a software construction formula based on reversible Delta operations:

```
App = Delta x-extends Generator<DSL>
```

It can provide a unified theoretical explanation for various practices and indicate directions for further development. For example,

```
DockerApp = DockerBuilder<DockerFile> overlay-fs BaseImage
```

To better demonstrate the concrete technical content of Reversible Computation, I have open-sourced a low-code platform for DSL development, Nop Platform 2.0. Its goal is similar to JetBrains’ MPS product: to build a Domain Language Workbench for rapid development and extension of DSLs. However, its specific implementation is fundamentally different from MPS.
https://github.com/entropy-cloud/nop-entropy

The Nop platform now includes a demo software production line that can automatically generate GraphQL services and front-end pages from data models in Excel format. Based on the generated code, we can then make manual adjustments. The hand-written Delta code is isolated from the auto-generated code and does not interfere with it.
![delta-pipeline](../tutorial/delta-pipeline.png)

In fact, software products developed on the Nop platform all support the Delta customization mechanism. Application-layer code requires no special design (such as pre-abstracted extension interfaces) to achieve fully incremental customization capabilities (the customized Delta code is completely independent of the base product code; customizing the base product or the Nop platform’s functionality requires no modification to the original code

[How to achieve customized development without modifying the source code of the base product](https://zhuanlan.zhihu.com/p/628770810)

nop-ide-plugin provides breakpoint debugging and syntax hints for all DSLs
![xlang-debugger](../tutorial/xlang-debugger.png)

Design data models via Excel
![excel-data-model](../tutorial/excel-model.png)

Define business rules with Excel
![decision-matrix](../dev-guide/rule/decision-matrix.png)

![decision-tree](../dev-guide/rule/decision-tree.png)

<!-- SOURCE_MD5:4dbfe2ded88db5cbfcd42b263ccde36d-->
