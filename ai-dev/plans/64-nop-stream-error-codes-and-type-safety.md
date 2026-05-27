# 64 nop-stream Error Codes, Exception Unification & TypeSerializer Dedup

> Plan Status: completed
> Last Reviewed: 2026-05-27
> Source: `ai-dev/audits/2026-05-27-deep-audit-nop-stream/` (维度 09, 15, 02)
> Related: Plan 57 (Code Cleanup, draft), Plan 60 (Exception Hierarchy, completed)

## Purpose

将 nop-stream 的错误处理从硬编码字符串异常迁移到 ErrorCode 模式，统一异常类型，消除重复 TypeSerializer 接口，升级关键操作的日志级别。这是 2026-05-27 深度审计中最大的 P1 集中区。

## Current Baseline

- `StreamException extends StreamRuntimeException extends NopException` — 层次正确
- 149 处 `new StreamException(` 调用，其中约 124 处使用硬编码字符串无 ErrorCode（排除带 cause 参数的包装调用约 25 处）
- 3 处生产代码使用 `RuntimeException`（MemoryKeyedStateBackend:936, EmbeddedDistributedExecutor:191, PendingCheckpoint:140-141）
- NFA/SkipToElementStrategy 混用 `StreamRuntimeException` 而非 `StreamException`
- NopCepErrors 已有 3 个 ErrorCode 定义（仅 CEP 模块使用）
- `typeinfo.TypeSerializer` 和 `typeutils.TypeSerializer` 同名接口共存（typeinfo 版仅被 BasicTypeInfo 和 SimpleTypeSerializer 内部使用）
- `GraphModelCheckpointExecutor` 中 7 处 LOG.warn（L323, L339, L471, L488, L505, L666, L794），其中 4 处为关键操作（checkpoint 触发/恢复/barrier 注入/source 停止）应升级为 LOG.error
- 构建/测试全部通过（300 tests, 0 failures）

## Goals

1. 创建 `NopStreamErrors` 错误码定义类，覆盖高频异常场景（参数校验、状态不一致、配置错误）
2. 迁移核心模块（core、runtime）中的硬编码异常到 ErrorCode（至少 30 处）
3. 统一所有生产代码的异常类型为 `StreamException`（消除 RuntimeException 和 StreamRuntimeException 混用）
4. 重命名 `typeinfo.TypeSerializer` 消除与 `typeutils.TypeSerializer` 的歧义
5. 升级 `GraphModelCheckpointExecutor` 中 4 处关键操作失败的 LOG.warn 为 LOG.error

## Non-Goals

- 不迁移 CEP 模块的全部硬编码异常（CEP 已有 NopCepErrors，后续可独立完善）
- 不拆分超大文件（MemoryKeyedStateBackend, NFACompiler 等，属于 P2 优化）
- 不修改 import 排序（属于 Plan 65 的 scope）
- 不修改测试代码中的 RuntimeException 使用

## Scope

### In Scope

- `nop-stream-core`: NopStreamErrors 定义、高频异常迁移、TypeSerializer 重命名
- `nop-stream-runtime`: RuntimeException 替换、PendingCheckpoint 异常修正、GraphModelCheckpointExecutor 日志升级
- `nop-stream-cep`: NFA/SkipToElementStrategy 异常类型统一

### Out Of Scope

- 测试代码中的 RuntimeException（测试中使用是合理的）
- CEP 模块的全面 ErrorCode 迁移
- import 排序修复
- 文件拆分/重构

## Execution Plan

### Phase 1 - 创建 NopStreamErrors + TypeSerializer 重命名

Status: completed
Targets: `nop-stream-core/.../exceptions/NopStreamErrors.java`, `nop-stream-core/.../typeinfo/`

- Item Types: `Fix`

- [x] 创建 `NopStreamErrors.java` 错误码定义类，包含以下高频错误码：
  - `ERR_STREAM_NULL_ARG` — 参数为 null
  - `ERR_STREAM_INVALID_STATE` — 状态不一致
  - `ERR_STREAM_CONFIG_ERROR` — 配置错误
  - `ERR_STREAM_UNSUPPORTED` — 不支持的操作
  - `ERR_STREAM_SERIALIZATION` — 序列化失败
  - `ERR_STREAM_OPERATOR_ERROR` — 算子执行错误
  - `ERR_STREAM_CHECKPOINT_ERROR` — 检查点错误
- [x] 重命名 `typeinfo.TypeSerializer` 为 `SimpleSerializer`（仅被 BasicTypeInfo 和 SimpleTypeSerializer 内部使用）
- [x] 更新 BasicTypeInfo 和 SimpleTypeSerializer 中对重命名接口的引用

Exit Criteria:

- [x] `NopStreamErrors.java` 存在且包含 10 个 ErrorCode 定义
- [x] `typeinfo.TypeSerializer` 已重命名为 `SimpleSerializer`，无编译错误
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过（300 tests, 0 failures）
- [x] No owner-doc update required（内部 API 变更）
- [x] No ai-dev/logs update required for this phase（logs updated at plan completion）

### Phase 2 - 迁移 core 模块硬编码异常 + 替换 RuntimeException

Status: completed
Targets: `nop-stream-core/.../state/backend/memory/MemoryKeyedStateBackend.java`, 其他 core 文件

- Item Types: `Fix`

- [x] 替换 `MemoryKeyedStateBackend.java:936` 的 `RuntimeException` 为 `StreamException`
- [x] 迁移 `nop-stream-core` 中 93 处硬编码 `StreamException` 到 `NopStreamErrors` 的 ErrorCode 模式
- [x] 优先迁移参数校验类异常（null 检查、参数范围检查）— 覆盖 34 个文件

Exit Criteria:

- [x] `MemoryKeyedStateBackend` 无 `RuntimeException`
- [x] 93 处硬编码异常已迁移到 ErrorCode
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过（300 tests, 0 failures）
- [x] No owner-doc update required
- [x] No ai-dev/logs update required for this phase

### Phase 3 - 统一 runtime/cep 异常类型 + 日志升级

Status: completed
Targets: `nop-stream-runtime/.../execution/EmbeddedDistributedExecutor.java`, `nop-stream-runtime/.../checkpoint/PendingCheckpoint.java`, `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java`, `nop-stream-cep/.../nfa/NFA.java`, `nop-stream-cep/.../nfa/aftermatch/SkipToElementStrategy.java`

- Item Types: `Fix`

- [x] 替换 `EmbeddedDistributedExecutor.java:191` 的 `RuntimeException` 为 `StreamException`
- [x] 替换 `PendingCheckpoint.java:140-141` 的 `RuntimeException` 为 `StreamException`
- [x] 统一 `NFA.java` 中的 `StreamRuntimeException` 为 `StreamException`（5 处）
- [x] 统一 `SkipToElementStrategy.java` 中的 `StreamRuntimeException` 为 `StreamException`（2 处）
- [x] 升级 `GraphModelCheckpointExecutor.java` 中 4 处关键操作 LOG.warn 为 LOG.error（L323, L339, L471, L505），其余 3 处保持不变

Exit Criteria:

- [x] 生产代码无 `new RuntimeException`
- [x] NFA/SkipToElementStrategy 使用 `StreamException`
- [x] `GraphModelCheckpointExecutor` 4 处关键操作失败日志级别为 ERROR
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过（300 tests, 0 failures）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 in-scope P1 错误处理问题已修复（D09-01 迁移 93 处, D09-02, D09-04, D09-06, D09-07, D09-08）
- [x] TypeSerializer 重复接口已消除（D02-07, D15-03）— 重命名为 SimpleSerializer
- [x] 生产代码无 `new RuntimeException`（D09-02）
- [x] NFA/SkipToElementStrategy 异常类型统一（D09-04, D09-08）
- [x] `GraphModelCheckpointExecutor` 关键操作日志级别为 ERROR（D09-06）
- [x] `./mvnw clean install -pl nop-stream -am -T 1C` 通过（300 tests, 0 failures）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过（300 tests, 0 failures）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `ai-dev/logs/` 对应日期条目已更新（ai-dev/logs/2026/05-27.md）

## Deferred But Adjudicated

### MemoryKeyedStateBackend 文件拆分 (D02-01)

- Classification: `optimization candidate`
- Why Not Blocking Closure: 文件虽大（1251行），但职责边界清晰（状态后端 + 内部 State 实现），当前无功能缺陷
- Successor Required: yes
- Successor Path: 后续优化 plan

### NFACompiler 文件拆分 (D02-02)

- Classification: `optimization candidate`
- Why Not Blocking Closure: 来自 Apache Flink 移植代码，复杂度是领域固有的
- Successor Required: yes
- Successor Path: 后续优化 plan

### CEP 模块全面 ErrorCode 迁移

- Classification: `optimization candidate`
- Why Not Blocking Closure: CEP 已有 NopCepErrors，3 个 ErrorCode 覆盖了最关键的错误场景
- Successor Required: no

## Non-Blocking Follow-ups

- WindowOperator 文件拆分（D02-03，职责合理但文件大）
- ChainingOutput 异常包装优化（D09-03，保留了 cause 但丢失类型信息）
- GraphModelCheckpointExecutor 拆分（D02-04，恢复逻辑独立为 CheckpointRestorer）

## Closure

Status Note: 所有 3 个 Phase 已完成，93 处异常迁移到 ErrorCode，3 处 RuntimeException 替换，TypeSerializer 重命名，NFA 异常统一，GraphModelCheckpointExecutor 日志升级。Closure audit 通过，无 blocking findings。

Closure Audit Evidence:
- Reviewer/Agent: Feynman (独立 closure audit 子 agent, session 019e699b-4134-7390-ab25-fc40918f31a4)
- NopStreamErrors.java: 10 个 ErrorCode 定义 ✅
- SimpleSerializer 重命名: 无编译错误 ✅
- 生产代码无 RuntimeException: ✅
- NFA/SkipToElementStrategy 使用 StreamException: ✅
- GraphModelCheckpointExecutor 4 处 LOG.error: ✅
- 300 tests, 0 failures: ✅
- 所有 Phase checklist 已勾选: ✅
- 日志条目 ai-dev/logs/2026/05-27.md: ✅
- Advisory: 清理了 NFA/SkipToElementStrategy 的未使用 StreamRuntimeException import
