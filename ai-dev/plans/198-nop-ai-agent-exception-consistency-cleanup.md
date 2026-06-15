# 198 nop-ai-agent 异常一致性清理（IllegalArgumentException / IllegalStateException → NopAiAgentException）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: AUDIT-09-02（吸收 AUDIT-09-03）
> Last Reviewed: 2026-06-15
> Source: carry-over from `ai-dev/plans/196-nop-ai-agent-exception-base-class.md`（Non-Goals [维度09-2] + [维度09-3]，均标 `successor plan required`）；`ai-dev/audits/2026-06-15-1146-deep-audit-nop-ai-agent/09-error-handling.md` 发现 [维度09-2]（P2，复核维持）与 [维度09-3]（P2，复核维持）；平台规范 `docs-for-ai/02-core-guides/error-handling.md`（"模式二：模块异常类" + 反模式表）
> Related: `196`（交付 `NopAiAgentException extends NopException` 基类，本计划的前置依赖，已 completed）

## Purpose

将 nop-ai-agent 模块内 49 处 `throw new IllegalArgumentException`（跨 24 个 main 文件）和 1 处 `throw new IllegalStateException`（`Layer2TurnPruningStrategy:199`）统一迁移为 `throw new NopAiAgentException`，使全模块抛出风格收敛到单一模块异常类。同时迁移受影响的测试断言（`assertThrows(IllegalArgumentException.class, ...)` → `assertThrows(NopAiAgentException.class, ...)`），保证全量测试零回归。本计划把 [09-2] + [09-3] 收口为"模块内无游离的 IllegalArgumentException / IllegalStateException throw 站点"这一单一结果面。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main` 与 `src/test`，2026-06-15）：

- **`NopAiAgentException` 基类已就位**（plan 196 交付）：`engine/NopAiAgentException.java:6` `extends NopException`，含 `serialVersionUID` 与四构造器 `(String)` / `(String, Throwable)` / `(ErrorCode)` / `(ErrorCode, Throwable)`。前两个签名与原 `IllegalArgumentException` 消息用法兼容（`new NopAiAgentException("msg")` / `new NopAiAgentException("msg", cause)`）。
- **49 处 `throw new IllegalArgumentException`** 站点确证存在（grep `throw new IllegalArgumentException` → 49 matches），分布在 24 个 main 文件，按包分布：security（12 处）、message（11 处）、completion（8 处）、reliability（8 处）、skill（5 处）、memory（4 处）、engine（1 处），合计 49 处。完整文件清单见 Scope § In Scope。
- **1 处 `throw new IllegalStateException`**：`compact/Layer2TurnPruningStrategy.java:199`（Layer 2 边界完整性自检，私有调用链）。
- **5 处 `catch (IllegalArgumentException)` in main code**（关键裁定项）：`FileSystemSkillProvider.java:275,284`、`LLMCurator.java:285`、`AgentMessageEnvelopeJson.java:108`、`CheckpointJournalReader.java:177`。逐一核对：**全部捕获的是 JDK `Enum.valueOf()` 抛出的 `IllegalArgumentException`**（`SkillTopPattern.valueOf` / `SkillResourceScope.valueOf` / `SkillQualityRating.valueOf` / `AgentMessageKind.valueOf` / `CheckpointType.valueOf`），**不是**本模块 `throw new IllegalArgumentException` 站点。这些 catch 块已将 enum 解析异常翻译为 `NopAiAgentException` 或返回 null。**迁移 throw 站点不影响这些 catch 块——它们必须保持不变。**
- **0 处 `catch (IllegalStateException)` in main code**（grep 确认 exit 1）。
- **~57 处测试断言依赖 `IllegalArgumentException` 类型**（关键影响项）：分布在 14 个 test 文件，包括 `assertThrows(IllegalArgumentException.class, ...)`（~51 处）与 `catch (IllegalArgumentException expected)`（~6 处）。`NopAiAgentException extends NopException extends RuntimeException`，**不是** `IllegalArgumentException` 的子类，因此迁移后这些断言**必须同步更新**为 `assertThrows(NopAiAgentException.class, ...)` / `catch (NopAiAgentException expected)`，否则测试全部失败。受影响 test 文件清单见 Scope § In Scope。
- **`LocalAgentMessenger.java:101-102`** 使用 `previous.completeExceptionally(new IllegalStateException(...))`——这是 `CompletableFuture.completeExceptionally` 模式（非 `throw new`），且 audit [09-3] 未标记此站点。不在本计划 scope（见 Non-Goals）。
- **测试中的 `IllegalStateException`**：`TestNopAiAgentExceptionBaseClass.java:215` 仅将其作为 cause 对象构造（`new IllegalStateException("underlying failure")`），非断言类型依赖——不受影响。
- **roadmap §5b**：当前仅有 `AUDIT-09-01` 行（第 275 行，已 ✅）。[09-2] / [09-3] 尚未有独立 roadmap 行（audit findings 仅有 audit 文档记录）。

## Goals

- 全模块 main code 中 `throw new IllegalArgumentException` 站点数从 49 → 0（全部迁移为 `NopAiAgentException`）。
- `compact/Layer2TurnPruningStrategy.java:199` 的 `throw new IllegalStateException` 迁移为 `NopAiAgentException`。
- 全部受影响测试断言（`assertThrows(IllegalArgumentException.class, ...)` 与 `catch (IllegalArgumentException expected)`）同步迁移为 `NopAiAgentException`，测试全绿。
- 5 处捕获 JDK `Enum.valueOf()` 异常的 `catch (IllegalArgumentException)` 块**保持不变**（它们不是本模块 throw 站点的消费者）。
- roadmap §5b 新增 `AUDIT-09-02` 行并标注 ✅ + 本 plan。

## Non-Goals

- **`LocalAgentMessenger.java:101-102` 的 `completeExceptionally(new IllegalStateException(...))`**：audit [09-3] 未标记此站点，且它是 `CompletableFuture` 异步完成模式而非 `throw` 语句，语义不同。Classification: watch-only residual。如需处理，独立 work item。
- **5 处 `catch (IllegalArgumentException)` 块（Enum.valueOf 翻译层）**：它们捕获的是 JDK 抛出的异常，不是本模块代码。迁移它们会破坏 enum 解析错误捕获。保持不变。
- **[维度09-4] `CheckpointJournalReader` 的 `java.util.logging` → SLF4J**：P3，日志框架切换，与异常体系无关。独立 work item（optimization candidate）。
- **新增 `ErrorCode` 定义**：模块定位为内部 AI Agent 库，当前无 ErrorCode 常量需求（audit 09 已确认合理）。迁移后的 `NopAiAgentException` 使用 `(String)` 构造器，与现有 100+ 站点风格一致。
- **错误消息内容改写**：本计划是类型迁移（`IllegalArgumentException` → `NopAiAgentException`），不重写消息文本。现有英文消息保持不变。

## Scope

### In Scope

**Main code — 24 文件（49 处 IllegalArgumentException throw 站点）：**

- `compact/Layer2TurnPruningStrategy.java`（1 处 IllegalStateException:199 + 无 IllegalArgumentException）
- `completion/CompletionRuleConfig.java`
- `completion/LlmCompletionJudge.java`
- `completion/LlmJudgeConfig.java`
- `completion/RuleBasedCompletionJudge.java`
- `engine/CalibratedTokenEstimator.java`
- `memory/InMemoryAiMemoryStore.java`
- `memory/InMemoryMemoryStoreProvider.java`
- `message/AgentMessageEnvelope.java`
- `message/AgentMessageTopics.java`
- `message/DBMessageService.java`
- `message/LocalAgentMessenger.java`（仅 IllegalArgumentException throw 站点；`completeExceptionally(IllegalStateException)` 不在 scope）
- `reliability/Checkpoint.java`
- `reliability/CheckpointSnapshot.java`
- `reliability/CompactionAwareTruncation.java`
- `security/DBDenialLedger.java`
- `security/DenialRecord.java`
- `security/DenialRecordOutcome.java`
- `security/DenialResult.java`
- `security/ParentConstrainedPathAccessChecker.java`
- `security/ParentConstrainedToolAccessChecker.java`
- `security/ParentPermissionConstraint.java`
- `security/RuleBasedPathAccessChecker.java`
- `skill/CuratorConfig.java`
- `skill/LLMCurator.java`

**Test code — ~14 文件（~57 处断言/捕获迁移）：**

- `memory/TestInMemoryMemoryStoreProvider.java`
- `skill/TestLLMCurator.java`
- `security/TestDenialResult.java`
- `security/TestDBDenialLedger.java`
- `security/TestParentConstrainedPathAccessChecker.java`
- `security/TestNoOpDenialLedger.java`
- `security/TestParentConstrainedToolAccessChecker.java`
- `security/TestRuleBasedPathAccessChecker.java`
- `completion/TestLlmCompletionJudge.java`
- `completion/TestRuleBasedCompletionJudge.java`
- `message/TestAgentMessageEnvelope.java`
- `message/TestAgentMessageTopics.java`
- `message/TestLocalAgentMessenger.java`
- `reliability/TestNoOpCheckpointAndValue.java`

**Docs:**

- `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §5b（新增 AUDIT-09-02 行）

### Out Of Scope

- `LocalAgentMessenger.java:101-102` 的 `completeExceptionally(IllegalStateException)`（见 Non-Goals）
- 5 处 `catch (IllegalArgumentException)` Enum.valueOf 翻译块（见 Non-Goals）
- [维度09-4] JUL 日志迁移
- 新增 ErrorCode 定义、错误消息改写、协变 `param()` 覆写

## Execution Plan

### Phase 1 - 迁移策略裁定与影响面落档

Status: completed
Targets: 本 plan 文件（裁定记录）、`ai-dev/logs/`

- Item Types: `Decision`

- [x] 裁定并落档：迁移映射规则为 `throw new IllegalArgumentException(msg)` → `throw new NopAiAgentException(msg)`，`throw new IllegalArgumentException(msg, cause)` → `throw new NopAiAgentException(msg, cause)`，`throw new IllegalStateException(msg)` → `throw new NopAiAgentException(msg)`。消息文本不变。
- [x] 裁定并落档：5 处 `catch (IllegalArgumentException)`（`FileSystemSkillProvider:275,284`、`LLMCurator:285`、`AgentMessageEnvelopeJson:108`、`CheckpointJournalReader:177`）**不迁移**——它们捕获 JDK `Enum.valueOf()` 异常。已在 Current Baseline 逐一核对确认。
- [x] 裁定并落档：测试断言迁移规则为 `assertThrows(IllegalArgumentException.class, ...)` → `assertThrows(NopAiAgentException.class, ...)`，`catch (IllegalArgumentException expected)` → `catch (NopAiAgentException expected)`。需补充 `import io.nop.ai.agent.engine.NopAiAgentException` 的 test 文件一并处理。
- [x] 裁定并落档：`LocalAgentMessenger.java:101-102` 的 `completeExceptionally(new IllegalStateException(...))` 不在本计划 scope——audit 未标记，且语义为异步完成而非同步 throw。归入 Non-Blocking Follow-ups。
- [x] 裁定并落档：import 策略——各 main 文件如已 import `NopAiAgentException` 则无需新增；否则补 `import io.nop.ai.agent.engine.NopAiAgentException`，按 import 分组规范（java→jakarta→第三方→io.nop.*）插入 io.nop.* 段。移除不再使用的 `java.lang.IllegalArgumentException` 无需操作（java.lang 隐式导入）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 上述 5 条裁定已写入本 plan（决策与契约，不含类签名/伪代码，遵循 Minimum Rules #14）。
- [x] No owner-doc update required for `docs-for-ai/`：本变更使模块**符合**已有平台规范 `docs-for-ai/02-core-guides/error-handling.md`（"模式二"模块异常类 + 反模式表 line 161 明文点名 `throw new RuntimeException`），该 owner doc 无需修改。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 执行迁移（main code + test code）

Status: completed
Targets: Scope § In Scope 列出的 24 个 main 文件 + ~14 个 test 文件

- Item Types: `Fix`

- [x] 迁移 24 个 main 文件中的 49 处 `throw new IllegalArgumentException(...)` → `throw new NopAiAgentException(...)`（签名兼容，消息文本不变；按需补 import）。
- [x] 迁移 `compact/Layer2TurnPruningStrategy.java:199` 的 1 处 `throw new IllegalStateException(...)` → `throw new NopAiAgentException(...)`。
- [x] 迁移 ~14 个 test 文件中的 `assertThrows(IllegalArgumentException.class, ...)` → `assertThrows(NopAiAgentException.class, ...)` 与 `catch (IllegalArgumentException expected)` → `catch (NopAiAgentException expected)`（按需补 import）。
- [x] 确认 5 处 `catch (IllegalArgumentException)` Enum.valueOf 翻译块**未被改动**。
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am -T 1C` 通过（证明 main code 迁移编译通过）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] grep `throw new IllegalArgumentException` 在 `nop-ai/nop-ai-agent/src/main/java/` 返回 0 结果（49 → 0）。
- [x] grep `throw new IllegalStateException` 在 `nop-ai/nop-ai-agent/src/main/java/` 返回 0 结果（Layer2TurnPruningStrategy:199 已迁移）。注：`LocalAgentMessenger:101-102` 的 `completeExceptionally(new IllegalStateException(...))` 不含 `throw new`，不受此 grep 影响，保持不变（Non-Goals）。
- [x] grep `assertThrows(IllegalArgumentException.class` 在 `nop-ai/nop-ai-agent/src/test/java/` 返回 0 结果（全部迁移为 `NopAiAgentException.class`）。
- [x] 5 处 `catch (IllegalArgumentException)` 翻译块仍存在且未被改动（grep `catch (IllegalArgumentException` 在 main code 仍返回 5 结果）。
- [x] **无静默跳过（Minimum Rules #24）**：迁移后的 throw 站点均为真实 `throw new NopAiAgentException(...)`，无空方法体/吞异常。
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am -T 1C` 通过。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 全量验证与 roadmap 同步

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/**`、`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`

- Item Types: `Proof`、`Follow-up`

- [x] 运行全量 `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`，确认零回归（迁移后的断言全部通过；既有 NopAiAgentException 测试不受影响）。
- [x] roadmap §5b 新增 `AUDIT-09-02` 行：`✅ 已修复`，标注 plan 198，说明 49 处 IllegalArgumentException + 1 处 IllegalStateException 已收敛为 NopAiAgentException。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全绿（0 failures, 0 errors）。
- [x] **端到端验证（Minimum Rules #22）**：全量模块测试从公共入口点（构造器/工厂方法/公共 API）到异常抛出再到测试断言捕获的完整路径已验证——每个迁移站点对应至少一个 `assertThrows(NopAiAgentException.class, ...)` 测试（迁移前断言 `IllegalArgumentException`，迁移后断言 `NopAiAgentException`），证明异常类型迁移在运行时生效。
- [x] **接线验证（Minimum Rules #23）**：迁移后的 `throw new NopAiAgentException(...)` 被测试中 `assertThrows(NopAiAgentException.class, ...)` 实际捕获（非仅编译通过）——由全量 `./mvnw test` 零失败证明。
- [x] roadmap §5b `AUDIT-09-02` 行已新增为 ✅ 并指向本 plan。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：本 section 与每个 Phase 的 Exit Criteria 全部 `[x]` 后，方可将 `Plan Status` 改为 `completed`。关闭流程见 plan guide 的 `When Closing The Plan` 与 `Closure Audit Rule`。

- [x] 全模块 main code 中 `throw new IllegalArgumentException` 站点数为 0（grep 在 `src/main/java/` 返回 0）。
- [x] 全模块 main code 中 `throw new IllegalStateException` 站点数为 0（grep 在 `src/main/java/` 返回 0；`LocalAgentMessenger` 的 `completeExceptionally` 不计入）。
- [x] 全部测试断言已迁移（grep `assertThrows(IllegalArgumentException.class` 在 `src/test/java/` 返回 0）。
- [x] 5 处 `catch (IllegalArgumentException)` Enum.valueOf 翻译块未被改动（仍在原位捕获 JDK enum 异常）。
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect（`LocalAgentMessenger` `completeExceptionally(IllegalStateException)` 已显式归入 Non-Goals 并附 non-blocking 理由）。
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required（见 Phase 1：符合已有 `error-handling.md`，无需修改）。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：迁移后的 throw 站点为真实异常抛出（非空壳/静默跳过），由全量测试中 `assertThrows` 实际捕获证明。
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过。
- [x] checkstyle / 代码规范检查通过（imports 分组正确；无残留 `java.lang.IllegalArgumentException` import）。
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/198-nop-ai-agent-exception-consistency-cleanup.md --strict` 退出码为 0。

## Deferred But Adjudicated

（暂无；本计划范围窄，未发现需裁定的 residual。）

## Non-Blocking Follow-ups

- **`LocalAgentMessenger.java:101-102` 的 `completeExceptionally(new IllegalStateException(...))`**：Classification: watch-only residual。Why Not Blocking Closure: audit [09-3] 未标记此站点；它是 `CompletableFuture` 异步完成模式（非 `throw` 语句），语义不同于同步异常抛出；correlationId 碰撞场景下用 `IllegalStateException` 表达"状态非法"有一定合理性。Successor Required: no（如后续决定统一，可作为独立小项处理）。

## Closure

Status Note: nop-ai-agent 全模块异常抛出风格已收敛到单一模块异常类 `NopAiAgentException`——49 处 `throw new IllegalArgumentException` 与 1 处 `throw new IllegalStateException`（Layer2TurnPruningStrategy:200）全部迁移完成，受影响测试断言（49 处 `assertThrows` + 4 处 `catch`）同步迁移，5 处捕获 JDK `Enum.valueOf()` 异常的 `catch` 翻译块保持不变。`./mvnw test` 1547 tests 零回归。独立 closure audit PASS。
Completed: 2026-06-15

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit subagent（fresh session，task_id `ses_1341cae27ffeGM6XSzrMXIdpNR`）
- Audit Session: ses_1341cae27ffeGM6XSzrMXIdpNR（read-only，2026-06-15）
- Evidence:
  - **Exit Criterion 1**（grep `throw new IllegalArgumentException` in main → 0）: PASS（grep 无输出）
  - **Exit Criterion 2**（grep `throw new IllegalStateException` in main → 0）: PASS（grep 无输出）
  - **Exit Criterion 3**（grep `assertThrows(IllegalArgumentException.class` in test → 0）: PASS（grep 无输出）
  - **Exit Criterion 4**（grep `catch (IllegalArgumentException expected)` in test → 0）: PASS（grep 无输出）
  - **Closure Gate 5**（`catch (IllegalArgumentException)` in main → 5 unchanged）: PASS（FileSystemSkillProvider:275,284 / LLMCurator:286 / AgentMessageEnvelopeJson:108 / CheckpointJournalReader:177——5 处 Enum.valueOf 翻译块均原位保留；注：plan 文本写 LLMCurator:285，实际为 286，off-by-one 文档差异，不影响功能）
  - **LocalAgentMessenger residual untouched**: PASS（line 102-103 `completeExceptionally(new IllegalStateException(...))` 保持不变）
  - **Layer2TurnPruningStrategy migrated**: PASS（line 200 `throw new NopAiAgentException(`，无 IllegalStateException 残留）
  - **No stray `java.lang.IllegalArgumentException` imports**: PASS（grep in `src/` 无输出）
  - **roadmap §5b AUDIT-09-02 row ✅**: PASS（roadmap.md:276）
  - **Spot-check real throw sites**: PASS（DenialRecord:57 / CompletionRuleConfig:29,32 / Checkpoint:97,100,103,107 均为真实 `throw new NopAiAgentException(...)`，消息文本保留，构造器签名兼容）
  - **`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`**: PASS（1547 tests, 0 failures, 0 errors）
  - **`check-plan-checklist.mjs --strict`**: exit 0（无未勾选项 + Closure Evidence 已写入）
  - **Anti-Hollow 检查**: PASS——迁移站点为真实异常抛出（spot-check 确认），全量 `assertThrows(NopAiAgentException.class)` 实际捕获（1547 tests 零失败证明）；`scan-hollow-implementations.mjs --severity high` 报告的 15 个 UOE stubs 均为历史 pre-existing（plan 84/86/97/98 等），不在本 plan 迁移文件内，与本 plan 无关
  - **Deferred 项分类检查**: PASS——唯一 deferred 项 `LocalAgentMessenger completeExceptionally(IllegalStateException)` 已显式归入 Non-Goals + Non-Blocking Follow-ups 并附 non-blocking 理由，非 in-scope live defect 降级

Follow-up:

- `LocalAgentMessenger.java:102-103` 的 `completeExceptionally(new IllegalStateException(...))`（watch-only residual；如后续决定统一异步完成异常类型，可作为独立小项处理）
- 无其他 plan-owned remaining work
