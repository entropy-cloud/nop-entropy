# 102 nop-stream Remaining Audit Findings Remediation

> Plan Status: in progress
> Last Reviewed: 2026-06-02
> Source: ai-dev/audits/2026-05-30-adversarial-review-nop-stream-r13/01-open-findings.md + r12/01-open-findings.md + 2026-05-28-deep-audit-nop-stream/09-error-handling.md，经 live repo baseline 验证
> Related: 86-nop-stream-uncovered-audit-findings-remediation (completed), 85-nop-stream-21-dim-deep-audit-remediation (completed), 100-nop-stream-core-wiring-and-feature-completion (completed)

## Purpose

修复 R12/R13 对抗性审查和 05-28 深度审计中经 live repo 验证仍然存在的 4 个未修复发现，将 nop-stream 审计发现收口到"仅剩低优先级残留"状态。

## Current Baseline

经独立子 agent baseline 验证（ses_177a796b5ffehRgBIrjtwMa7uG），R12 的 4 个发现和 R13 的 17 个发现中，18 个已修复，3 个仍然存在，1 个部分修复：

**仍存在的发现：**

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| R13-AR-8 | P2 | `WindowOperator.java:1130-1139` | `windowNamespace()` 非 TimeWindow 回退路径使用 `System.identityHashCode(window)`，跨 JVM 实例不确定 |
| R13-AR-12 | P2 | `GraphExecutionPlan.java:198-200` | `taskIndex==0` 复用 JobVertex 的 OperatorChain 引用，不 deepCopy，与 taskIndex>0 行为不一致 |
| R13-AR-17 | P2 | `SimpleCondition.java:46-58` | `of()` 创建匿名类捕获外部引用，不可序列化；已加 Javadoc 警告但代码未改 |

**部分修复：**

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 09-04 | P2 | `WindowOperatorBuilder.java` | nop-stream-runtime 大部分已迁移到 ErrorCode；`WindowOperatorBuilder` 仍有 3 处字符串构造器 `StreamException(e.getMessage(), e)` |

## Goals

- 修复 R13-AR-8：`windowNamespace()` 对非 TimeWindow 类型使用确定性 namespace 生成而非 `identityHashCode`
- 修复 R13-AR-12：`GraphExecutionPlan.build()` 对所有 taskIndex（包括 0）使用 `deepCopy()`
- 修复 R13-AR-17：`SimpleCondition.of()` 使用具名静态内部类替代匿名类，确保可序列化
- 修复 09-04 残余：`WindowOperatorBuilder` 的 3 处字符串构造器替换为 `ErrorCode` + `.param()` 模式
- 每个修复完成后 `./mvnw test -pl nop-stream -am` 全部通过

## Non-Goals

- 不实现 ClosureCleaner 等效机制（R13-AR-17 的替代方案之一）
- 不重构 WindowOperator 的 namespace 设计为结构化 key（只修复当前 fallback 路径）
- 不处理 fraud-example 端到端验证
- 不处理已被之前 plan 覆盖且已修复的发现

## Scope

### In Scope

- 4 个文件的代码修复
- 对应修复的单元测试补充

### Out Of Scope

- WindowAggregationOperator 与 WindowOperator 统一（架构级）
- CEP ClosureCleaner 机制（独立设计决策）
- 其他 nop-* 模块

## Execution Plan

### Phase 1 — WindowOperator.windowNamespace() 确定性 namespace 生成

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java`

- Item Types: `Fix`

- [x] 将 `windowNamespace()` 的非 TimeWindow 回退路径从 `window.getClass().getName() + "@" + System.identityHashCode(window)` 改为 `window.getClass().getName() + "#" + window.toString()`。注意：这不是"序列化"，而是生成确定性的 namespace 字符串——`toString()` 是确定性但非碰撞安全的，与 `identityHashCode`（跨 JVM 实例不确定）相比是改进。在 Javadoc 中要求自定义 Window 子类必须实现唯一且确定性的 `toString()`
- [x] 添加单元测试：验证自定义 Window 子类（非 TimeWindow）的 `windowNamespace()` 在两次调用返回相同结果（确定性）

Exit Criteria:

- [x] `WindowOperator.windowNamespace()` 对非 TimeWindow 类型不再使用 `System.identityHashCode`
- [x] 新增单元测试验证确定性
- [x] `./mvnw test -pl nop-stream -am` 通过
- [x] No owner-doc update required（内部实现细节变更，不影响公共 API 契约）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — GraphExecutionPlan OperatorChain 隔离

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/GraphExecutionPlan.java`

- Item Types: `Fix`

- [x] 将 `taskIndex == 0` 分支从 `original.getOperatorChains().get(0)` 改为 `original.getOperatorChains().get(0).deepCopy()`，与 `taskIndex > 0` 行为一致
- [x] 验证已有 `GraphExecutionPlan` 相关测试不受影响

Exit Criteria:

- [x] `GraphExecutionPlan.build()` 的所有 taskIndex 路径均使用 `deepCopy()`
- [x] `./mvnw test -pl nop-stream -am` 通过
- [x] No owner-doc update required（内部实现隔离，不影响公共 API）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — SimpleCondition.of() 序列化安全性

Status: completed
Targets: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/pattern/conditions/SimpleCondition.java`

- Item Types: `Fix`

- [x] 将 `SimpleCondition.of(FilterFunction)` 的匿名类实现改为具名静态内部类 `FilterFunctionCondition<T>`，持有 `FilterFunction` 引用并实现 `Serializable`。**构造时不检查可序列化性**（避免破坏 168+ 现有测试中传递 lambda 的调用点），仅在序列化时（`writeReplace` 或让 Java 序列化自然失败并包装为描述性异常）暴露不可序列化问题
- [x] 添加测试：验证 `SimpleCondition.of(serializableFilter)` 可被 Java 序列化/反序列化
- [x] 添加测试：验证 `SimpleCondition.of(nonSerializableFilter)` 在序列化时抛出异常而非静默失败

Exit Criteria:

- [x] `SimpleCondition.of()` 不再创建匿名内部类
- [x] 可序列化的 filter 通过 `SimpleCondition.of()` 产生的对象可被 Java 序列化
- [x] 不可序列化的 filter 产生的对象在序列化时显式失败
- [x] `./mvnw test -pl nop-stream -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — WindowOperatorBuilder ErrorCode 合规

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperatorBuilder.java`

- Item Types: `Fix`

- [x] 将 3 处 `throw new StreamException(e.getMessage(), e)` 替换为 `StreamException(ERR_STREAM_STATE_ERROR, e).param(ARG_DETAIL, e.getMessage())`，使用 `NopStreamErrors.ERR_STREAM_STATE_ERROR`（`WindowOperatorBuilder` 位于 `nop-stream-runtime`，`NopStreamErrors` 在 `nop-stream-core` 中已可访问）
- [x] 验证错误消息语义不变

Exit Criteria:

- [x] `WindowOperatorBuilder` 中无字符串构造器 `StreamException` 调用
- [x] `./mvnw test -pl nop-stream -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] R13-AR-8 已修复：`windowNamespace()` 对非 TimeWindow 类型不再使用 `identityHashCode`
- [ ] R13-AR-12 已修复：`GraphExecutionPlan.build()` 对 taskIndex==0 使用 `deepCopy()`
- [ ] R13-AR-17 已修复：`SimpleCondition.of()` 不创建匿名类，序列化行为可预测
- [ ] 09-04 残余已修复：`WindowOperatorBuilder` 的 3 处字符串构造器已替换为 ErrorCode
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [ ] No owner-doc update required across all phases（4 个 phase 均为内部实现修复，不影响公共 API 契约）
- [ ] `./mvnw compile` 通过
- [ ] `./mvnw test -pl nop-stream -am` 通过
- [ ] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

（无 — 本 plan scope 内的全部 4 项均为 live defect，不可延期）

## Non-Blocking Follow-ups

- CEP ClosureCleaner 等效机制：R13-AR-17 的 Phase 3 方案是局部修复（替换匿名类），如果未来引入 Flink 风格的 ClosureCleaner 可进一步简化。当前方案可独立关闭
- WindowOperator namespace 结构化 key：当前方案在 toString() 层面防御，未来可改为 Tuple/JSON 结构化 key 完全消除碰撞风险

## Closure

Status Note: （执行完成后填写）

Closure Audit Evidence:

（由独立子 agent 填写）

Follow-up:

- CEP ClosureCleaner 等效机制（optimization candidate）
- WindowOperator namespace 结构化 key（optimization candidate）
