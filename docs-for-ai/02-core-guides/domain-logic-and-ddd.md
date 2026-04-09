# 领域逻辑与 DDD 落位

Nop 不照搬传统“把所有行为都塞进聚合根”的 DDD。当前仓库更实用的默认分层是：Entity 承担稳定领域语言，BizModel 暴露对外动作，Processor 承接流程编排，Step 只在跨多个 Processor 稳定复用时再抽。

## 默认落位

| 需求 | 默认位置 |
|------|---------|
| 稳定领域事实、只读计算、状态判断 | Entity |
| 对外查询 / 修改动作、事务入口、安全 API | BizModel |
| 多步骤编排、跨聚合协作、外部系统调用 | Processor |
| 多个 Processor 共用的单一动作 | Step |
| 可配置状态流转、长流程、人工节点、规则化决策 | 状态机 / Workflow / Rule |

## Entity 放什么

适合放在 Entity 的通常是这些方法：

- `isXxx()`
- `canXxx()`
- `calculateXxx()`
- 基于已有字段和关联的只读 helper

这些逻辑应同时满足：

1. 只读，不写库。
2. 不调用外部系统。
3. 不依赖易变的业务策略。
4. 能表达稳定的领域事实。

## Entity 不要放什么

默认不要在 Entity 中做这些事：

1. `dao()`、`I*Biz`、远程调用、消息发送。
2. 长流程编排。
3. 容易因租户或业务场景变化而改变的规则。
4. 直接承担事务和持久化入口。

如果一段逻辑需要 `IServiceContext`、需要安全 API、需要跨多个聚合协作，默认就不该继续留在 Entity。

## BizModel 放什么

BizModel 是默认业务入口。这里负责：

1. 暴露 `@BizQuery` / `@BizMutation`。
2. 使用 `CrudBizModel` 的安全 API，例如 `requireEntity()`、`doFindList()`、`updateEntity()`。
3. 组织请求 DTO、返回 DTO、权限与事务边界。
4. 在需要时把复杂流程转交给 Processor。

默认模式不是“BizModel 什么都不做”，而是“BizModel 保持清晰入口，不把复杂 orchestration 全堆在一个方法里”。

## 什么时候拆 Processor

出现以下信号时，优先拆 Processor：

1. 一个动作已经明显是多步骤流程。
2. 需要协调多个 BizModel、多个聚合或外部系统。
3. 同一流程会被多个入口复用。
4. 单个 BizModel 方法已经难以阅读、测试或定制。

Processor 的角色是编排，不是绕开 Biz 层去直接写原始 DAO。

## 什么时候抽 Step

Step 只在下面两种情况同时满足时再抽：

1. 这是一个边界清晰的单一动作。
2. 它会被多个 Processor 稳定复用。

如果只是某个 Processor 内部的局部子步骤，优先保留为 `protected` 方法，不要过早抽 Step。

## 什么时候新增 Java 类

| 场景 | 默认选择 |
|------|---------|
| 单个对外业务动作已经变成复杂流程 | 新增 Processor |
| 多个 Processor 共享同一个单步能力 | 新增 Step |
| 跨模块暴露稳定业务能力 | 新增 `I*Biz` 接口 + BizModel |
| 纯计算 / 纯格式转换 / 无上下文 helper | 普通 helper 或私有方法 |
| 配置化状态流转或长流程 | 状态机 / Workflow / Rule |

如果模型、Delta、已有 BizModel 扩展点已经能解决问题，就不要为了“更像 DDD”再额外加 Java 类。

## DDD 在 Nop 里的实用结论

1. 聚合根依然重要，但主要承担稳定领域语义和关联导航。
2. 可变业务动作默认放 BizModel 或 Processor，而不是塞进实体方法。
3. 不要因为业务复杂，就退回到 MyBatis 式“大方法 + 原始 DAO + 手工事务”开发。
4. 业务状态值如果需要配置化或租户定制，优先字典 / Constants，而不是把易变业务状态固化成 enum。

本页和相关 runbook 一起定义当前项目默认的 DDD 落位规则。

## 常见坑

1. 在 Entity 中写持久化或外部调用。
2. BizModel 一个方法塞满全部业务流程。
3. 还没出现复用就先抽 Step。
4. 为了“架构好看”新增很多没有稳定职责的 Java 类。

## 相关文档

- `./service-layer.md`
- `../03-runbooks/choose-entity-bizmodel-processor.md`
- `../03-runbooks/implement-complex-business-flow.md`
- `../03-runbooks/add-dict-and-constants.md`
- `../04-reference/source-anchors.md`
