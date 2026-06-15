# 196 nop-ai-agent 模块异常类纳入框架异常体系（NopAiAgentException extends NopException）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: AUDIT-09-01
> Last Reviewed: 2026-06-15
> Source: carry-over from `ai-dev/plans/193-nop-ai-agent-secure-by-default.md`（Non-Goals 引用 `[09-1]（NopAiAgentException 基类）... 独立 work item`）；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §5b（`AUDIT-09-01 | P1 | ❌ 未修复`，roadmap 第 275 行）；`ai-dev/audits/2026-06-15-1146-deep-audit-nop-ai-agent/09-error-handling.md` 发现 [维度09-1]（P1，复核维持）；平台规范 `docs-for-ai/02-core-guides/error-handling.md`（"模式二：模块异常类" + 反模式表明确文点名 `extends RuntimeException`）
> Related: `193`（把本项裁定为 successor）、`194`（同为 carry-over 链上的 P1 安全收敛，已 completed）

## Follow-up handled by 198-nop-ai-agent-exception-consistency-cleanup.md

本计划的 Non-Goals [维度09-2]（49 处 `IllegalArgumentException` 迁移）与 [维度09-3]（1 处 `IllegalStateException` 迁移）已由 `ai-dev/plans/198-nop-ai-agent-exception-consistency-cleanup.md` 接管。

## Purpose

把 `NopAiAgentException` 从 `extends RuntimeException` 改为 `extends NopException`，补齐 `serialVersionUID` 与 `(ErrorCode)` / `(ErrorCode, Throwable)` 构造器，使模块异常纳入框架统一异常体系（结构化错误响应、`.param(...)` 链式上下文、`getErrorCode()`、i18n 钩子）。本计划只负责这一件事：让模块异常类与平台规范对齐，不再脱离框架异常体系。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-15）：

- **`engine/NopAiAgentException.java:3`**：`public class NopAiAgentException extends RuntimeException`。仅两个构造器 `(String message)`、`(String message, Throwable cause)`，均直接 `super(...)`。无 `serialVersionUID`。
- **使用面**：该类是模块内唯一自定义异常类，被约 100+ 处抛出引用（审计统计 118 处），分布在 `engine`/`session`/`reliability`/`security`/`skill`/`message` 等包。所有引用均通过 `(String)` 或 `(String, Throwable)` 构造器调用——**两个构造器签名在本计划中保留不变**。
- **`catch` 面**：模块内共 4 处 `catch (NopAiAgentException e)`（`DefaultAgentEngine.java:1434`、`FileBackedSessionStore.java:225`、`DBSessionStore.java:177`、`AgentMessageEnvelopeJson.java:93`）。无任何代码依赖"`NopAiAgentException` **不是** `NopException`"这一性质。改为 `extends NopException` 后，`catch (NopAiAgentException)`、`catch (RuntimeException)`、`catch (NopException)`（新增）仍命中——只增不减。
- **`NopException` 可用性**：`nop-ai-agent` 依赖 `nop-ai-core`（`pom.xml:25`），`nop-ai-core` 传递依赖 `nop-api-core`（`NopException` 所在）。审计已确认 `NopException` 在 classpath 上，技术上可立即修正。
- **`NopException` 构造器**（`nop-kernel/nop-api-core/.../NopException.java`）：提供 `(ErrorCode)`、`(ErrorCode, Throwable)`、`(String errorCode, Throwable cause, boolean, boolean)` 三个构造器；**没有** `(String)` 单参构造器。因此字符串构造器必须委派 `super(message, null, true, true)`——与 `docs-for-ai/02-core-guides/error-handling.md` "模式二" 模板（lines 84-109）完全一致。
- **`getMessage()` 语义变化（关键风险评估）**：`NopException` 覆写 `getMessage()` 返回结构化字符串 `NopAiAgentException[seq=N,errorCode=<原消息>,params={}]`；原消息文本作为 `errorCode` 子串嵌入。已逐条核对测试断言：
  - 所有 `assertThrows(NopAiAgentException.class, ...)` → 仍通过（类仍存在、仍可抛）。
  - 所有 `ex.getMessage().contains("...")` → 仍通过（原文本是 `getMessage()` 的子串）。已核对的站点包括 `TestSessionIdValidation`、`TestAgentNameValidation`、`TestRestoreSession`、`TestModeDispatch`、`TestDefaultAgentEngineCancel/Fork`、`TestReActAgentExecutorBuilder`、`TestAgentMessageEnvelopeJson` 等约 20 处 `.contains()`。
  - **唯一** `assertEquals(plainString, ex.getMessage())` 站点（`TestISessionStoreDefaultMethods:19/27/34/41`）抛的是 `UnsupportedOperationException`，**不是** `NopAiAgentException`——不受本计划影响。
  - 结论：预期全量测试在**零测试改动**下通过，但必须在 Phase 3 用 `./mvnw test` 实证验证（见 Exit Criteria）。
- **事务回滚语义**：`NopException.shouldRollback(e)` 对 `NopException` 返回 `!isNotRollback()`（默认 `false` → 回滚），对非 NopException 返回 `true`（回滚）。两条路径默认都回滚——改为 `NopException` 后回滚行为**不变**，无事务回归。
- **roadmap §5b**（第 275 行）：`AUDIT-09-01 | P1 | ❌ 未修复 | NopAiAgentException extends RuntimeException 而非 NopException`。本计划关闭这一行。

## Goals

- `NopAiAgentException extends NopException`，补齐 `serialVersionUID`，提供 `(String)`、`(String, Throwable)`、`(ErrorCode)`、`(ErrorCode, Throwable)` 四构造器——与 `error-handling.md` "模式二" 模板一致。
- 现有约 100+ 处 `(String)` / `(String, Throwable)` 抛出站点**零改动**编译通过（构造器签名保留）。
- 模块异常现在可被框架统一 `ApiResponse` 错误响应机制识别，支持 `.param(...)` 链式上下文与 `(ErrorCode)` 构造。
- roadmap §5b `AUDIT-09-01` 行从 ❌ → ✅ 并标注本 plan。

## Non-Goals

- **[维度09-2]**（49 处 `IllegalArgumentException` → `NopAiAgentException`）：跨 24 文件的机械迁移，属独立大项，且**依赖本计划先落地**（09-1 修正为 `NopException` 后，09-2 的迁移才有结构化意义）。独立 successor plan。
- **[维度09-3]**（`Layer2TurnPruningStrategy` 的 1 处 `IllegalStateException`）：P2，单站点。建议与 09-2 合并入同一"异常一致性清理" successor plan。
- **[维度09-4]**（`CheckpointJournalReader` 使用 `java.util.logging`）：P3，日志框架切换，与异常体系无关。独立 work item。
- 新增 `ErrorCode` 定义：模块定位为内部 AI Agent 库，当前无需定义 `ErrorCode` 常量（审计 09 已确认 ErrorCode 定义数=0 合理）。本计划只提供 `(ErrorCode)` 构造器能力，不定义具体错误码。
- 覆写 `param(...)` / `description(...)` 返回协变类型 `NopAiAgentException`：`error-handling.md` 模板不覆写，且 `throw new NopAiAgentException(...).param(...)` 因 `.param()` 返回的 `NopException` IS-A `RuntimeException` 仍可抛出。本计划不覆写（保持与模板一致）。

## Scope

### In Scope

- `engine/NopAiAgentException.java`：基类切换 + `serialVersionUID` + 构造器调整 + import。
- 新增 focused 测试验证新能力（`instanceof NopException`、`getErrorCode()` 返回原文本、`.param(...)` 链式、`(ErrorCode)` 构造器、`getMessage().contains()` 兼容性）。
- `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §5b：`AUDIT-09-01` 状态同步。
- 验证既有测试零回归（`getMessage()` 语义变化不影响 `.contains()` 断言）。

### Out Of Scope

- 09-2（`IllegalArgumentException` 迁移）、09-3（`IllegalStateException`）、09-4（JUL 日志）、新 `ErrorCode` 常量定义、协变 `param()` 覆写（见 Non-Goals）。

## Execution Plan

### Phase 1 - 基类切换策略裁定与设计落档

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`（§5b 状态同步）、裁定记录

- Item Types: `Decision`

- [x] 裁定并落档构造器集合：采用 `error-handling.md` "模式二" 模板四构造器——`(String)` → `super(message, null, true, true)`、`(String, Throwable)` → `super(message, cause, true, true)`、`(ErrorCode)` → `super(errorCode)`、`(ErrorCode, Throwable)` → `super(errorCode, cause)`。前两个签名与现有完全一致（保证 100+ 站点零改动）。明确 `NopException` 无 `(String)` 单参构造器（已核对 `NopException.java`，仅有 `(ErrorCode)` / `(ErrorCode, Throwable)` / `(String, Throwable, boolean, boolean)` 三构造器），故字符串构造器必须走 4 参委派。
- [x] 裁定并落档包位置不变：类留在 `io.nop.ai.agent.engine` 包（迁移包会破坏 100+ import，无收益）。
- [x] 裁定并落档事务回滚无回归：`NopException.shouldRollback` 对 `NopException` 返回 `!isNotRollback()`（默认 `false` → 回滚），对非 NopException 返回 `true`（回滚）。两条路径默认都回滚——改为 `NopException` 后回滚行为不变，不需额外 `notRollback` 标记。
- [x] 裁定并落档 `getMessage()` 语义变化的影响评估结论：`NopException.getMessage()` 返回结构化字符串 `NopAiAgentException[seq=N,errorCode=<原文本>,params={}]`；原文本作为 `errorCode` 子串嵌入。已逐条核对：约 20 处 `ex.getMessage().contains("...")` 断言仍通过（原文本是 `getMessage()` 的子串）；唯一 `assertEquals(plainString, ex.getMessage())` 站点（`TestISessionStoreDefaultMethods`）抛的是 `UnsupportedOperationException`，不受本计划影响；不存在对 `NopAiAgentException` 的 `.equals(plainString)` 断言。该结论须在 Phase 3 由全量 `./mvnw test` 实证。
- [x] 裁定并落档不覆写 `param()`/`description()` 协变返回：遵循 `error-handling.md` 模板（`NopAiException` 范例未覆写），`throw new NopAiAgentException(...).param(...)` 因 `.param()` 返回的 `NopException` IS-A `RuntimeException` 仍可抛。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 上述 5 条裁定已写入本 plan（决策与契约，不含类签名/伪代码，遵循 Minimum Rules #14）。
- [x] No owner-doc update required for `docs-for-ai/`：本变更使模块**符合**已有平台规范 `docs-for-ai/02-core-guides/error-handling.md`（"模式二"模板 + 反模式表 line 163 明文点名 `extends RuntimeException`），该 owner doc 无需修改；模块 design docs 仅以 "throws NopAiAgentException" 引用该类（契约不变），无需修改。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 实现基类切换

Status: completed
Targets: `engine/NopAiAgentException.java`

- Item Types: `Fix`

- [x] `NopAiAgentException`：`extends RuntimeException` → `extends NopException`。
- [x] `NopAiAgentException`：新增 `private static final long serialVersionUID = 1L;`。
- [x] `NopAiAgentException`：现有 `(String)` 构造器改为 `super(message, null, true, true)`（签名不变，仅 super 委派改变）。
- [x] `NopAiAgentException`：现有 `(String, Throwable)` 构造器改为 `super(message, cause, true, true)`（签名不变）。
- [x] `NopAiAgentException`：新增 `(ErrorCode errorCode)` 与 `(ErrorCode errorCode, Throwable cause)` 构造器。
- [x] `NopAiAgentException`：新增 import `io.nop.api.core.exceptions.ErrorCode`、`io.nop.api.core.exceptions.NopException`。
- [x] 确认约 100+ 处既有抛出站点与 4 处 `catch (NopAiAgentException)` 站点**无源码改动**（构造器签名保留，`./mvnw compile -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `NopAiAgentException` 的 `extends` 子句为 `NopException`（`engine/NopAiAgentException.java:6` 确认，无残留 `RuntimeException` 直接继承）。
- [x] `NopAiAgentException` 含 `serialVersionUID` 字段（`engine/NopAiAgentException.java:7`）。
- [x] `NopAiAgentException` 提供四构造器 `(String)`、`(String, Throwable)`、`(ErrorCode)`、`(ErrorCode, Throwable)`。
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am -T 1C` 通过（证明既有 100+ 抛出站点与 4 处 catch 站点零改动编译通过）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 测试验证与 roadmap 同步

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/**`、`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`

- Item Types: `Proof`、`Follow-up`

- [x] 新增 focused 测试（Minimum Rules #25）`TestNopAiAgentExceptionBaseClass`（8 tests）覆盖：(1) `new NopAiAgentException("msg")` `instanceof NopException` 为 true 且 `instanceof RuntimeException` 仍为 true；(2) `getErrorCode()` 返回原消息文本；(3) `getMessage().contains("msg")` 为 true（兼容性断言，证明现有 `.contains()` 测试语义不变）；(4) `new NopAiAgentException("msg").param("k","v")` 可正常链式调用且可被 `throw` 并 catch；(5) `(ErrorCode)` 构造器构造的实例 `getErrorCode()` 返回 `ErrorCode.getErrorCode()`；(6) `(String, Throwable)` 构造器保留 cause 链（`getCause()` 非空）；外加 `(ErrorCode, Throwable)` ctor 与 `(String)` ctor 无 cause 两个 sanity tests。
- [x] 运行全量 `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`，确认现有 `.contains()` 消息断言（约 20 处）与全部 `assertThrows(NopAiAgentException.class)` 站点零回归。结果：**1542 tests, 0 failures, 0 errors**（1534 既有 + 8 新增，零回归）。
- [x] roadmap §5b（第 275 行）：`AUDIT-09-01` 行 ❌ → ✅，落地 plan 标注 196。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全绿（1542 tests, 0 failures；既有测试零回归 + 新增 8 个 focused 测试通过）。
- [x] 新增测试**显式列出**所验证的新行为（`instanceof NopException`、`getErrorCode` 保留原文本、`.param` 链式、`ErrorCode` 构造器、`getMessage().contains` 兼容、cause 链保留），不是"原有测试通过"。
- [x] **接线验证（Minimum Rules #23）**：`TestNopAiAgentExceptionBaseClass.stringCtorSupportsParamChainingAndIsThrowable` 实际调用 `(String)` 构造器 + `.param("agentName", ...)` + `.param("reason", ...)` 并 `throw` 后断言 `getParam(...)` 取回；`errorCodeCtorReturnsErrorCodeValue` / `errorCodeAndCauseCtorReturnsErrorCodeAndCause` 实际调用 `(ErrorCode)` 与 `(ErrorCode, Throwable)` 构造器并断言 `getErrorCode()` 取回 `ErrorCode.getErrorCode()`（非仅类型存在）。
- [x] **无静默跳过（Minimum Rules #24）**：新构造器均为真实实现（委派 `super(errorCode)` / `super(errorCode, cause)` / `super(message, null, true, true)` / `super(message, cause, true, true)`），无空方法体/吞异常。
- [x] roadmap §5b `AUDIT-09-01` 行已更新为 ✅ 并指向本 plan（`nop-ai-agent-roadmap.md:275`）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：本 section 与每个 Phase 的 Exit Criteria 全部 `[x]` 后，方可将 `Plan Status` 改为 `completed`。关闭流程见 plan guide 的 `When Closing The Plan` 与 `Closure Audit Rule`。

- [x] `NopAiAgentException extends NopException`（live behavior，非仅类型声明——由 focused `instanceof` 测试 `TestNopAiAgentExceptionBaseClass.stringCtorIsNopExceptionAndRuntimeException` 证明，同时断言仍 `instanceof RuntimeException`）。
- [x] 四构造器齐备（`NopAiAgentException.java:9-23`：`(String)`→`super(message, null, true, true)`、`(String, Throwable)`→`super(message, cause, true, true)`、`(ErrorCode)`→`super(errorCode)`、`(ErrorCode, Throwable)`→`super(errorCode, cause)`），既有 100+ 抛出站点与 4 处 catch 站点（`DefaultAgentEngine.java:1434`、`FileBackedSessionStore.java:225`、`DBSessionStore.java:177`、`AgentMessageEnvelopeJson.java:93`）零源码改动。
- [x] 模块异常可被框架统一异常体系识别（`catch (NopException)` 命中、`.param(...)` 可用——由 focused 测试 `stringCtorSupportsParamChainingAndIsThrowable` 实际调用 `.param("agentName",...).param("reason",...)` 并 `throw`/`catch`/`getParam` round-trip 取回证明）。
- [x] 既有测试零回归（`getMessage()` 结构化变化未破坏 `.contains()` 断言——由全量 `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 1542 tests / 0 failures 证明；`getMessageContainsOriginalTextForBackwardCompatibility` 显式断言结构化 `getMessage()` 把原文本作为 `errorCode=` 子串嵌入）。
- [x] 事务回滚行为无变化（裁定结论：`NopException.shouldRollback` 对 NopException 返回 `!isNotRollback()`=默认 `true`（回滚），对非 NopException 返回 `true`（回滚），两条路径默认都回滚——见 Phase 1 裁定；无事务相关测试回归）。
- [x] roadmap §5b `AUDIT-09-01` 同步为 ✅（`nop-ai-agent-roadmap.md:275`）。
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect（09-2/09-3/09-4 已显式移入 Non-Goals/Non-Blocking Follow-ups，属裁定移出而非隐藏，见 `Non-Blocking Follow-ups` 段）。
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required（见 Phase 1：符合已有 `error-handling.md` "模式二"模板 + 反模式表 line 163，无需修改）。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（独立 fresh-session subagent `ses_1349894fdffe3KIyq4TRxnEn6A`，见 Closure 段）。
- [x] **Anti-Hollow Check**：closure audit 验证新构造器在运行时确被调用——`errorCodeCtorReturnsErrorCodeValue` 实际调用 `(ErrorCode)` ctor 并断言 `getErrorCode()` 返回 `"nop.err.ai-agent.test-invalid-name"`；`stringCtorSupportsParamChainingAndIsThrowable` 实际调用 `.param(...)` 链并 round-trip 取回；`NopException.param`（`NopException.java:386-392`）真实执行 `params.put(...)`。无空方法体/静默 no-op（4 个 ctor 均委派真实 `super(...)` 调用）。
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过（Tests run: 1542, Failures: 0, Errors: 0, Skipped: 0 / BUILD SUCCESS）。
- [x] checkstyle / 代码规范检查通过（BUILD SUCCESS；imports 分组正确 java→jakarta→第三方→io.nop.*；4-space 缩进；无 bare RuntimeException）。
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/196-nop-ai-agent-exception-base-class.md --strict` 退出码为 0（"All plans passed checklist verification."）。

## Deferred But Adjudicated

（暂无；本计划范围窄，未发现需裁定的 residual。）

## Non-Blocking Follow-ups

- **[维度09-2]**：49 处 `IllegalArgumentException` → `NopAiAgentException` 迁移（跨 24 文件）。依赖本计划先落地。独立 successor plan。（Classification: successor plan required）
- **[维度09-3]**：`Layer2TurnPruningStrategy:198` 的 1 处 `IllegalStateException` → `NopAiAgentException`。建议与 09-2 合并。（Classification: successor plan required）
- **[维度09-4]**：`CheckpointJournalReader` 的 `java.util.logging` → SLF4J。独立 work item。（Classification: optimization candidate）

## Closure

Status Note: `NopAiAgentException` 从 `extends RuntimeException` 切换为 `extends NopException`，补齐 `serialVersionUID` 与 `(ErrorCode)` / `(ErrorCode, Throwable)` 构造器（字符串构造器签名保留，100+ 抛出站点与 4 处 catch 站点零改动）。模块异常现已纳入框架统一异常体系：`catch (NopException)` 命中、`.param(...)` 链式上下文可用、`getErrorCode()` 钩子可用、可被框架 `ApiResponse` 结构化错误响应识别。变更使模块**符合**已有平台规范 `docs-for-ai/02-core-guides/error-handling.md`（"模式二"模板 + 反模式表 line 163 明文点名 `extends RuntimeException`），无需修改 owner doc。既有 1542 个测试零回归（结构化 `getMessage()` 把原文本作为 `errorCode=` 子串嵌入，约 20 处 `.contains()` 断言全部兼容）。roadmap §5b `AUDIT-09-01` 已同步为 ✅。
Completed: 2026-06-15

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit subagent（fresh session），task ID `ses_1349894fdffe3KIyq4TRxnEn6A`（subagent_type=general，非实现 session）
- Audit Session: `ses_1349894fdffe3KIyq4TRxnEn6A`
- Evidence:
  - **Phase 1 Exit Criteria — 全 PASS**：5 条裁定写入 plan（lines 68-72）；No owner-doc update required 已落档（line 79，变更使模块符合已有 `error-handling.md` "模式二"模板）；`ai-dev/logs/2026/06-15.md` 已更新。
  - **Phase 2 Exit Criteria — 全 PASS**：`NopAiAgentException.java:6` `extends NopException`（无残留 `RuntimeException` 直接继承）；`:7` `serialVersionUID = 1L`；`:9-23` 四构造器齐备且 super 委派正确（`(String)`→`super(message, null, true, true)`、`(String, Throwable)`→`super(message, cause, true, true)`、`(ErrorCode)`→`super(errorCode)`、`(ErrorCode, Throwable)`→`super(errorCode, cause)`）；`:3-4` import `ErrorCode`/`NopException`；`./mvnw compile -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS（100+ 抛出站点 + 4 catch 站点零改动编译通过）。
  - **Phase 3 Exit Criteria — 全 PASS**：`TestNopAiAgentExceptionBaseClass` 8 tests 显式覆盖 6 行为点 + 2 sanity；接线验证（#23）`stringCtorSupportsParamChainingAndIsThrowable` / `errorCodeCtorReturnsErrorCodeValue` 实际运行时调用 `.param(...)` 与 `(ErrorCode)` ctor 并 round-trip 取回；无静默跳过（#24）4 ctor 均委派真实 `super(...)`。
  - **Closure Gates — 全 PASS**：见 plan Closure Gates 段每条的 live evidence。
  - **Anti-Hollow Check — PASS**：`NopException.param`（`NopException.java:386-392`）真实执行 `params.put(name, normalizeValue(value))`，`getParam`（`:375-377`）返回 `params.get(name)`，focused 测试 round-trip 证明；`NopException(ErrorCode)`（`:81-85`）调用 `super(errorCode.getErrorCode())`，`getErrorCode()`（`:269-271`）返回 `super.getMessage()`，focused 测试证明取回 `"nop.err.ai-agent.test-invalid-name"`；grep `TODO|FIXME|XXX|HACK` 在新文件无命中（exit 1）；4 ctor 均有真实 `super(...)` 委派，无空方法体。
  - **Test run**：`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → **Tests run: 1542, Failures: 0, Errors: 0, Skipped: 0** / BUILD SUCCESS（独立 audit subagent 复跑确认）。
  - **Checklist tool**：`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/196-nop-ai-agent-exception-base-class.md --strict` → exit 0（"All plans passed checklist verification."）。
  - **Deferred 项分类检查**：09-2（49 处 IllegalArgumentException 迁移）、09-3（1 处 IllegalStateException）、09-4（JUL 日志）已显式移入 Non-Goals + Non-Blocking Follow-ups，均带 successor-plan-required / optimization-candidate 分类与 non-blocking 理由，无 in-scope live defect 被降级。
  - **4 catch 站点**（closure audit 确认仍编译有效，因 `NopException extends RuntimeException` 故 `NopAiAgentException` IS-A `RuntimeException`）：`DefaultAgentEngine.java:1434`、`FileBackedSessionStore.java:225`、`DBSessionStore.java:177`、`AgentMessageEnvelopeJson.java:93`。
  - **Minor baseline 修正**：closure audit 发现 Current Baseline line 20 原写"3 处 catch"（实际 4 处），已在 closure 时修正为 4 处以保持文本一致性（Minimum Rules #19）。

Follow-up:

- [维度09-2] 49 处 `IllegalArgumentException` → `NopAiAgentException` 迁移（跨 24 文件，依赖本计划先落地——独立 successor plan required）。
- [维度09-3] `Layer2TurnPruningStrategy:198` 的 1 处 `IllegalStateException` → `NopAiAgentException`（建议与 09-2 合并——successor plan required）。
- [维度09-4] `CheckpointJournalReader` 的 `java.util.logging` → SLF4J（optimization candidate，与异常体系无关）。
- 无其他 plan-owned work 残留。
