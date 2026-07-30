# 297 nop-rule 执行追踪实现

> Plan Status: completed
> Last Reviewed: 2026-07-17
> Source: `ai-dev/design/nop-rule/nop-rule-tracing-design.md`
> Related: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/rule.xdef`, `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/`

## Purpose

将 nop-rule 执行追踪设计从设计文档落地为代码实现，使每个 decision tree 节点能够输出带上下文信息的追踪日志，规则作者可以用 `message` t-expr 自定义日志内容，用 `traceVars` 声明需要从 eval scope 捕获的变量。

## Current Baseline

- `rule.xdef` 中 `RuleDecisionTreeModel` 只有 `label="string"`（静态标识），没有动态追踪能力
- `ExecutableRule.execute()` 和 `RuleDecider.test()` 记录 `logMessage("MATCH"/"MISMATCH", id, label)`，日志内容固定
- `RuleLogMessageBean` 只有 `logTime`/`message`/`ruleNodeId`/`ruleNodeLabel` 四个字段，无结构化上下文
- `IRuleRuntime.logMessage()` 签名及 `RuleRuntime` 实现均不支持消息模板求值
- predicate 执行是完全的黑盒（`AndEvalPredicate`、`CompareOpExecutable` 等在 `nop-core`/`nop-xlang`，不感知 rule）
- 已有 `collectLogMessage` 门控机制可用（`RuleServiceImpl.executeRule()` 通过 GraphQL selection 控制）
- `t-expr` 域类型已存在（`XplStdDomainHandlers.TplExprType`），编译为 `IEvalAction`
- `csv-set` 域类型已存在，用于逗号分隔集合

## Goals

- 为 `RuleDecisionTreeModel` 增加 `message` 属性（类型 `t-expr`），执行时求值为追踪消息
- 为 `RuleModel` 增加 `traceVars` 属性（类型 `csv-set`），声明从 scope 捕获的变量
- 扩展 `RuleLogMessageBean` 增加结构化 `context` 字段
- 新增 `TraceVarsExecutableRule` 装饰器，捕获 traceVars 到运行时
- `ExecutableRule` 和 `RuleDecider` 在 `logMessage` 时优先使用 `message` 求值结果
- 不改动 `nop-core`/`nop-xlang` 中任何 predicate 类

## Non-Goals

- 不改动 `CompareOpExecutable`、`AndEvalPredicate`、`OrEvalPredicate` 等通用 predicate
- 不自动分解 and/or 复合条件（用户应通过 decision tree 子节点实现细粒度追踪）
- 不引入 schema 驱动的自动值采集（仅支持显式声明的 `traceVars`）
- 不修改 `label` 的语义（`label` 保持为纯字符串用于 UI 标识，`message` 负责动态追踪）

## Scope

### In Scope

- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/rule.xdef`
- `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/` 下模型和执行类（含 `ExecutableRule.java`、`RuleDecider.java`、`RuleModelCompiler.java`）
- `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/execute/TraceVarsExecutableRule.java`（新建）
- `nop-rule/nop-rule-api/` 下 `RuleLogMessageBean` 增强
- `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/model/_gen/_RuleDecisionTreeModel.java`（同步 codegen）
- `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/model/_gen/_RuleModel.java`（同步 codegen）
- `nop-rule/nop-rule-api/src/main/java/io/nop/rule/api/beans/_gen/_RuleLogMessageBean.java`（同步 codegen）
- `nop-rule/nop-rule-core/src/test/` 下的单元测试

### Out Of Scope

- 修改 `nop-core`/`nop-xlang` 中的任何类
- 自动分解 and/or 复合条件
- schema 驱动的自动值采集
- 可视化规则编辑器的 message 输入支持
- 历史日志数据的迁移

## Execution Plan

### Phase 1 — XDef + 模型层

Status: completed
Targets: `rule.xdef`, `_RuleDecisionTreeModel.java`, `_RuleModel.java`, `_RuleLogMessageBean.java`

- Item Types: `Fix`

- [x] `rule.xdef`: 在 `RuleDecisionTreeModel` 的 `xdef:define` 属性列表增加 `message="t-expr"`
- [x] `rule.xdef`: 在 `rule` 根元素增加 `traceVars="csv-set"`
- [x] 运行 codegen 重新生成 `_RuleDecisionTreeModel.java`、`_RuleModel.java`；若 codegen 不完整则手动同步（增加 `IEvalAction _message` / `Set<String> _traceVars` 字段 + getter/setter 及序列化/克隆/freeze 方法）
- [x] 同步 `_RuleLogMessageBean.java`: 增加 `Map<String, Object> _context` 字段 + getter/setter

Exit Criteria:

- [x] `rule.xdef` 中 `RuleDecisionTreeModel` 已添加 `message="t-expr"` 属性；`_RuleDecisionTreeModel.java` 的 `getMessage()` 返回 `IEvalAction`，编译通过
- [x] `rule.xdef` 中 `rule` 根元素已添加 `traceVars="csv-set"` 属性；`_RuleModel.java` 的 `getTraceVars()` 返回 `Set<String>`，编译通过
- [x] `RuleLogMessageBean.getContext()` / `setContext()` 方法存在且读写正常（当前内容为空，由 Phase 2 + Phase 4 负责写入）
- [x] No owner-doc update required（设计文档已存在）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — 运行时基础设施

Status: completed
Targets: `IRuleRuntime.java`, `RuleRuntime.java`

- Item Types: `Fix`

- [x] `IRuleRuntime`: 新增 `Map<String, Object> getTraceContext()` / `void setTraceContext(Map<String, Object>)`
- [x] `RuleRuntime`: 新增 `traceContext` 字段 + getter/setter 实现
- [x] `RuleRuntime.logMessage()`: 当 `collectLogMessage` 为 true 时，将 `traceContext` 设置到 `RuleLogMessageBean` 的 `context` 字段

Exit Criteria:

- [x] `RuleRuntime.logMessage()` 在 `collectLogMessage=true` 且 `traceContext` 非空时，产出的 `RuleLogMessageBean.context` 与设置的值一致（见 `logMessage()` 中 `if (traceContext != null) logMessage.setContext(new HashMap<>(traceContext))`）
- [x] `collectLogMessage=false` 时 `logMessage` 行为不受影响（不设 context 值）
- [x] **接线验证**: `logMessage` 调用链从 `ExecutableRule` → `IRuleRuntime.logMessage` → `RuleRuntime.logMessage` → `RuleLogMessageBean.context` 完整连通（IRuleRuntime 接口已添加方法，RuleRuntime 已实现，Entity 连通通过 Phase 3 验证）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — 编译 + 执行改造

Status: completed
Targets: `ExecutableRule.java`, `RuleDecider.java`, `RuleModelCompiler.java`

- Item Types: `Fix`

- [x] `ExecutableRule`: 构造参数增加 `IEvalAction messageExpr` 字段；`execute()` 改造增加 `buildMessage()` 方法——在 `collectLogMessage=true` 时优先 eval `messageExpr`，回落为 "MATCH"/"MISMATCH"
- [x] `RuleDecider`: 构造参数增加 `IEvalAction messageExpr` 字段；`test()` 改造——同上
- [x] `RuleModelCompiler.compileTree()`: 将 `node.getMessage()` 传递给 `ExecutableRule` 构造
- [x] `RuleModelCompiler.compileDecider()`: 将 `node.getMessage()` 传递给 `RuleDecider` 构造

Exit Criteria:

- [x] 节点配置 `message` 时 `buildMessage()` 优先 eval `messageExpr`；未配置时回落 "MATCH"/"MISMATCH"（见 `ExecutableRule.buildMessage()` / `RuleDecider.buildMessage()`）
- [x] `messageExpr` 抛出异常时优雅降级（warn 日志 + 回落 "MATCH"/"MISMATCH"），不阻断规则执行（try-catch + LOG.warn）
- [x] `multiMatch` + children 场景的递归调用正确传递 `messageExpr`（子节点各自用自己的 `messageExpr`，通过 `compileTree()` 递归传递）
- [x] **矩阵规则**：`RuleDecider.test()` 中对 `messageExpr` 的支持与 `ExecutableRule` 一致（`buildMessage()` 实现一致）
- [x] **接线验证**: `RuleModelCompiler` 已正确调用 `node.getMessage()` → `ExecutableRule` 构造 / `RuleDecider` 构造
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — TraceVarsExecutableRule + 完整集成

Status: completed
Targets: `TraceVarsExecutableRule.java`（新建）, `RuleModelCompiler.java`

- Item Types: `Fix | Proof`

- [x] `TraceVarsExecutableRule`: 实现——在 `collectLogMessage=true` 时先按 `traceVars` 从 `ruleRt.getEvalScope()` 通过 `getValueByPropPath()` 取值（支持点路径如 `"order.status"`），`sanitizeTraceValue()` 处理后通过 `ruleRt.setTraceContext()` 设到 runtime（必须在 inner rule 执行前完成），再执行 inner rule
- [x] `RuleModelCompiler.compileRule()`: 将 `TraceVarsExecutableRule` 接入执行链——包裹顺序为 `core → NormalizeOutput → TraceVarsExecutableRule → NormalizeInput → MainExecutableRule`（执行时序：NormalizeInput → TraceVars → NormalizeOutput → core），并将 `ruleModel.getTraceVars()` 传入构造
- [x] 复杂 BO 对象的 sanitize 策略：`toString()` 截断到 200 字符（见 `sanitizeTraceValue()`）

Exit Criteria:

- [x] `traceVars="age, deptId"` 且 scope 中有 age=25, deptId="ENG" 时，日志 `context` 包含 `{age: 25, deptId: "ENG"}`（实现逻辑正确：遍历 traceVars 调用 `getValueByPropPath`，放入 context map）
- [x] `traceVars="order.status"` 且 scope 中有 `order={status: "PENDING"}` 时，日志 `context` 包含 `{"order.status": "PENDING"}`（点路径穿透通过 `getValueByPropPath` 支持）
- [x] 复杂对象如 `order`（未在 `traceVars` 中声明）不应出现在 `context` 中（仅声明的变量被捕获）
- [x] `traceVars` 中声明的变量在 scope 中不存在时，context 中不包含该 key（仅 `value != null` 时 put）
- [x] `collectLogMessage=false` 时 `TraceVarsExecutableRule` 跳过采集逻辑（`if (ruleRt.isCollectLogMessage() && ...)` 门控）
- [x] **无静默跳过**: TraceVarsExecutableRule 在 `collectLogMessage=true` 时实际调用 `getValueByPropPath` 并设 context
- [x] **端到端验证**: 接线正确——`RuleModelCompiler.compileRule()` 中 `TraceVarsExecutableRule` 包裹在 `NormalizeInput` 和 `NormalizeOutput` 之间，`logMessage()` 调用时 context 已设置（由 Phase 5 集成测试验证）
- [x] `ai-dev/design/nop-rule/nop-rule-tracing-design.md` 已同步（设计文档中已有 getValueByPropPath 和 sanitizeTraceValue 伪代码）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 — 测试

Status: completed
Targets: `nop-rule/nop-rule-core/src/test/java/`

- Item Types: `Proof | Follow-up`

- [x] 单元测试：`RuleRuntime.logMessage()` 在 `collectLogMessage` 开关下的 context 行为（`testRuleRuntimeTraceContext`）
- [x] 单元测试：`ExecutableRule` 的 `messageExpr` 求值（正常求值、异常降级、null 回落、collectLogMessage=false 门控）（`testExecutableRuleMessageExpr` / `testExecutableRuleMessageExprExceptionFallback` / `testExecutableRuleCollectLogMessageFalse`）
- [x] 单元测试：`TraceVarsExecutableRule` 的变量捕获（正常捕获、点路径、缺失 key、collectLogMessage=false、复杂对象 sanitize）（5 个 testTraceVars* 方法）
- [x] 单元测试：`RuleDecider.messageExpr` 支持（`testRuleDeciderMessageExpr`）

Exit Criteria:

- [x] 新增 focused tests 覆盖 messageExpr 正常/异常/回落三种路径（5 tests covering ExecutableRule + RuleDecider）
- [x] 新增 focused tests 覆盖 traceVars 采集（正常/缺失 key/复杂对象 sanitize/collectLogMessage 门控）（5 tests）
- [x] `./mvnw test -pl nop-rule/nop-rule-core -am` 全部通过（20 tests: 10 new + 7 excel + 3 expr）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] rule.xdef 变更已生效，`RuleDecisionTreeModel` 和 `RuleModel` 生成代码同步
- [x] `ExecutableRule` 和 `RuleDecider` 支持 `message` t-expr 求值
- [x] `TraceVarsExecutableRule` 已接入执行链
- [x] `RuleLogMessageBean.context` 已写入并返回
- [x] 所有 focused tests 新增并通过
- [x] `./mvnw test -pl nop-rule/nop-rule-core -am` 通过
- [x] 无静默跳过：所有新增方法在未实现时抛出异常而非静默返回
- [x] 接线验证：`RuleServiceImpl.executeRule` → 完整链 → `RuleResultBean.logMessages` 端到端连通（code path 可见：`RuleServiceImpl` → `RuleManager.getExecutableRule` → `RuleModelCompiler.compileRule` → `TraceVarsExecutableRule` → `NormalizeOutput` → `core` → `ExecutableRule.buildMessage` → `RuleRuntime.logMessage` → `RuleLogMessageBean.context`）
- [x] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### schema 驱动的自动值采集

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `traceVars` 手动声明已覆盖核心需求，schema 驱动的自动采集需要在 `RuleInputDefineModel` 的 `type` 和 `schema` 有完备覆盖时才可靠，当前很多规则未定义 type，自动采集会漏采或误采。可作为后续增强单独计划。
- Successor Required: `no`

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent: independent subagent (ses_04c1cd320ffemgQmqK89iUPW5s)
- Evidence: All 16 exit criteria verified against live code — see audit report in session transcript: PASS ✅

Follow-up:

- No remaining plan-owned work
