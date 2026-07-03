# 平台可扩展性

本目录专门解释 **Nop 平台与具体业务领域无关的通用可扩展设计**。

这里回答的问题不是“某个模块怎么配”，而是：

- 为什么 Nop 的扩展机制和传统 Spring 风格框架不同
- 为什么很多能力可以不写进引擎核心，而转成 DSL、模板、Delta 和元编程
- 当你想扩展平台或平台模块时，应该优先落在哪一层

## 阅读顺序

1. `how-to-use-extensibility-in-business-implementation.md`
2. `platform-extensibility-mechanism.md`
3. `nop-wf-as-example.md`
4. 按需回到具体 guide / module / runbook

建议搭配阅读：

- `../02-core-guides/xdef-and-xdsl.md`
- `../02-core-guides/delta-customization.md`
- `../02-core-guides/xlang-and-xpl-basics.md`

尤其要注意：XDSL 节点默认可带名字空间扩展属性，很多元编程展开就是围绕这些扩展属性完成的。

## 本目录与其他目录的边界

- `02-core-guides/`：默认开发规则和 DSL/模型/服务写法
- `03-modules/`：某个模块能做什么、怎么用
- `03-runbooks/`：具体任务怎么做
- `06-extensibility/`：平台级可扩展设计的共性机制

如果你是 AI，在做业务实现前，先看 `how-to-use-extensibility-in-business-implementation.md`。

如果你要解释“为什么 Nop 能把很多传统框架内置能力变成外置可扩展层”，再看本目录其他文档。
