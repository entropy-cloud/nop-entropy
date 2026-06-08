# Plan 运行时契约与编写引导边界讨论

> Status: resolved
> Date: 2026-04-14

## 第 1 轮

### 用户原始表述

用户提出两个核心问题：

1. 当前的 `plan` 模型能否描述 `nop-chaos-flux/docs/plans/` 下的真实计划文档。
2. `plan` 是否应该被理解为 AI 与运行时引擎协作的机制，而不是完整的人类计划文档模型。

### AI 复述

问题的本质是：`agent-plan.xdef` 是否应该去覆盖完整的 repo 级计划文档形态，还是只表达运行时能校验、能消费、能据此阻止结束的结构化子集。

### AI 分析

`nop-chaos-flux/docs/plans/` 中实际混合了多种文档形态：

- 执行计划
- checklist
- closure report
- roadmap
- audit / fix plan
- authoring guide

因此，不适合让一个 runtime plan schema 去覆盖所有人类计划文档形态。

更合理的模型是：

- `plan` = AI 与运行时引擎协作的结构化协议
- 只有运行时能消费、能校验的部分进入强约束结构
- 其余叙述性内容留给 AI 与人类自由编写

### 待澄清问题

1. 哪些字段必须属于 hard contract？
2. 哪些字段应只作为 narrative guidance？
3. Markdown 模板应该单独存在，还是并入 authoring guide？

---

## 第 2 轮

### 用户回答

用户明确指出：

- `plan` 应该是 AI 与运行时引擎协作的机制
- 重要的是运行时能明确强校验的部分
- 其余部分可以让 AI 自由发挥
- 如果强校验不满足，运行时不应允许 plan 结束

### AI 整理

这明确了 plan 的边界：

- hard contract = 运行时能消费、能校验、能阻止结束的部分
- soft narrative = 给 AI 和人阅读的叙述性内容

同时也明确了：

- `plan` 不应该试图完整复制 repo 级人类计划文档模板

### 阶段性结论

plan 系统应该：

- 在 `agent-plan.xdef` 中保留 hard contract 字段
- 把编写指导移到 authoring guide
- 允许 AI 用英文自由编写其余 narrative 内容

---

## 第 3 轮

### 用户回答

用户追问：为什么同时存在 `goal` 和 `goals`？

### AI 整理

这暴露出命名层级冲突：

- `goal` 原意是总目标
- `goals` 看起来像第二层重复目标列表

### 阶段性结论

模型收敛为：

- 保留 `goal` 作为单个总目标
- 把 `goals` 改名为 `successCriteria`

从而形成更清晰的语义：

- `goal` = 总目标
- `successCriteria` = 完成判定结果列表

---

## 第 4 轮

### 用户回答

用户继续追问：`questions`、`decisions`、`errors`、`additionalNotes` 是否真的需要进入 schema，还是应该让 AI 自由发挥。

### AI 整理

这一轮进一步细化了 hard / soft 边界：

- `decisions` 和 `additionalNotes` 更像 narrative-only 内容
- `questions` 只有在运行时真要以“未回答问题阻止完成”时，才值得结构化
- `errors` 是例外，因为未解决 blocking error 确实可能阻止 plan 结束

### 阶段性结论

方向变成：

- 从 `agent-plan.xdef` 中移除 narrative-only 部分
- 保留 `errors`，但收敛为偏 runtime 的 blocking-aware 记录
- `Questions` / `Decisions` / `Additional Notes` 作为 Markdown 编写引导保留

---

## 第 5 轮

### 用户回答

用户建议：这些引导性内容应该写进编写指南，而不是单独放一个模板文档。

### AI 整理

这解决了另一个结构问题：

- 单独存在的 template 文件会形成重复真相
- 更合理的结构是：
  - 一份 authoring guide
  - 一份 runtime schema
  - 一份 DSL 说明文档

### 阶段性结论

独立 Markdown 模板被删除，其内容并入：

- `ai-dev/plans/00-plan-authoring-and-execution-guide.md`

authoring guide 负责：

- 英文编写规则
- required sections
- recommended sections
- canonical template
- closure / review 规则

---

## 第 6 轮

### 用户回答

用户进一步澄清：

- plan 是给 AI / 人 / 运行时三方共同阅读的
- plan 不是 checklist
- 局部线性待办应由 `update-todos` 工具负责

### AI 整理

这纠正了之前把 plan 过度简化为 checklist 的倾向。

正确分工应为：

- plan = 结构化执行文档，既有 narrative，也有 runtime-consumed fields
- todo list = 局部线性待办列表

### 阶段性结论

guide 与 DSL 文档都据此调整为：

- plan 不退化为 checklist
- runtime gate 明确
- todo 责任单独保留给 `update-todos`

---

## 第 7 轮

### 用户回答

用户询问：`C:\can\ai\OpenSpec` 中是否有值得借鉴的 plan / spec / task 组织方式。

### AI 整理

调研结论是：

- OpenSpec 值得借鉴的是写作风格和 blocker/advisory 分层
- 不适合直接照搬它的 proposal/spec/tasks 三件套结构
- 当前 plan guide 仍应保持面向 AI / 人 / 运行时三方可读，而不是退化成 tasks-only 模式

### 阶段性结论

只借鉴以下几点：

- 更短、更直接的写作风格
- 更清楚的 blocking / advisory 区分
- 更明确的 runtime-consumed fields 是 source of truth

---

## 第 8 轮

### 用户回答

用户指出：AI 写计划时，不需要知道 Markdown/XML 对齐细节；它真正需要知道的是 runtime 会消费哪些字段，哪些字段不会。

### AI 整理

这带来了最后一轮收敛：

- guide 不再以“格式转换”作为主表达
- guide 应直接告诉 AI：runtime 真正在意什么
- 同时 guide 仍应保持人类可读、AI 可用、运行时边界清楚

### 最终结论

plan guide 的中心表达调整为：

- runtime-consumed fields
- narrative guidance
- required sections
- recommended sections
- canonical authoring form

而不再强调序列化或格式转换细节。

---

## 总结

### 最终需求

最终接受的模型是：

- `agent-plan.xdef` 是结构化运行时协议基座
- `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 是标准编写与收口指南
- `nop-ai-agent-plan-dsl.md` 负责解释 schema 语义以及 hard / soft 边界

### 关键决策

- `plan` 是 AI 与运行时引擎协作机制，不是完整的人类计划文档模型
- 运行时只强校验自己能消费、并会影响 completion blocking 的字段
- narrative section 应继续保留给 AI / 人类，但不都应进入 hard contract schema
- `goal` 与 `successCriteria` 应明确区分
- `Questions` / `Decisions` / `Additional Notes` 属于 authoring guidance，而不是当前 runtime hard contract
- `errors` 需要保留在 schema 中，因为 unresolved blocking error 会影响 completion
- plan 与 todo 是两套机制，plan 不是线性 todo 列表
- guide 应告诉 AI runtime 会消费哪些字段，而不是强调格式转换细节

### 剩余细节

- `agent-plan.xdef` 仍是主要结构化协议载体，但其中不是每个字段都属于 completion-blocking gate
- `successCriteria` 目前位于 runtime-consumed structure 与 human/AI readability 的边界上；运行时最终 closure 仍应主要依赖 `exitCriteria`、`checks`、`validationChecklist`、blocking errors 和 `closure`

### 后续行动

- 继续使用当前的 guide / schema / DSL 三分结构作为有效方案
- 如果未来 runtime 开始更严格消费 narrative 字段，再决定是否把这些字段升级回 schema-level hard contract
