# nop-wf 作为平台可扩展机制案例

## 这篇文档的定位

这篇文档不是说“nop-wf 自己发明了一套独特扩展机制”。

它要表达的是：**nop-wf 很适合拿来观察 Nop 平台通用可扩展机制如何落地。**

如果你还没读平台级文档，先看：

- `./platform-extensibility-mechanism.md`

## 为什么 `nop-wf` 是一个好案例

`nop-wf` 同时展示了以下几层机制：

- XDef 定义 workflow DSL
- `.xwf` 作为 XDSL 模型
- `x:extends` 继承基础模板
- `oa.xwf` 把审批共性语义沉淀为模板
- `oa.xlib` / `wf-actor.xlib` 把规则外置为标签库
- 设计器和转换器可以在编译期把高层配置展开为底层模型

所以 `nop-wf` 不是“只有 workflow 才能这样扩展”，而是“workflow 把平台通用机制用得非常完整”。

## `oa.xwf` 的正确理解

`oa.xwf` 不是简单示例，也不是 workflow 引擎私有魔法。

它是平台通用机制在审批领域的一次具体应用：

- 用 base `.xwf` 抽象领域共性
- 用公共 action 形成默认语义
- 用扩展属性表达步骤差异
- 用 `<when>` 标签限制不同动作的适用范围

这说明：

- 很多审批规则不必写死在引擎核心
- 可以先放进模板和标签库
- 业务项目继续通过 `x:extends` 叠加自己的审批方言

## 在 `nop-wf` 中能看到的通用模式

## 1. 基础 DSL 足够小，领域语义通过组合叠加

workflow 内核保留的是较稳定的原语：

- `step`
- `transition`
- `join`
- `flow`

而审批领域里的很多变化被表达为：

- `specialType`
- `execGroupType`
- actor 属性
- 标签库逻辑
- 模板继承

这正是平台“把变化放在模型组合层，而不是全塞进引擎”的典型做法。

## 2. 领域规则先放模板，不先放引擎

例如：

- 抄送步骤和普通审批步骤动作不同
- 某些公共 action 所有步骤都可见
- 某些动作只能在特定步骤类型执行

这些首先表现为：

- `oa.xwf`
- `oa.xlib`
- `<when>` 条件

而不是先在 Java 主循环里加一串 if/else。

## 3. 动态能力优先落在标签库层

审批人解析是最典型的例子。

`wf-actor:*` 标签说明：

- 动态规则不是只能靠 Java SPI
- 也可以先变成可声明调用的 DSL 标签
- 这样业务模型仍然留在 `.xwf` 层，而不是散到服务代码里

## 4. 更高层 DSL 可以继续编译成 `.xwf`

workflow 设计器、简化审批 DSL、行业审批方言，本质上都可以做成：

- 更友好的 DSLx
- 编译期展开
- 最终仍回到标准 `.xwf`

这就是平台级元编程机制在 workflow 上的自然用法。

## 一个常见误解

错误理解：

- `nop-wf` 可扩展，是因为 workflow 引擎自己设计得特别灵活

更准确的理解：

- `nop-wf` 可扩展，是因为它建立在 Nop 平台通用扩展基础设施上
- workflow 模块只是把这套基础设施应用到了审批/BPM 领域

所以当你总结 `nop-wf` 的优势时，应该强调：

- 它证明了平台级机制足以支撑复杂领域抽象
- 而不是把它写成一个只属于 workflow 的孤立技巧

## 对文档写作的默认要求

如果以后再写 `nop-wf` 的“可扩展性”文档，默认要保持这个顺序：

1. 先指向平台级机制
2. 再说明 workflow 怎么用这套机制
3. 不把平台通用机制误写成 workflow 私有设计

## 相关文档

- `./platform-extensibility-mechanism.md`
- `../03-modules/nop-wf.md`
- `../02-core-guides/workflow-configuration.md`
- `../03-runbooks/build-approval-flow.md`
