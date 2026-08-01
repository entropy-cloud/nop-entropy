# 1 arm-p3-error-handling-agent-shell — 错误处理规范收口：nop-ai-agent + nop-ai-shell 裸 IAE/RTE → NopException + ErrorCode（P2-MA3-1 + P3-MA3-1）

> Plan Status: completed
> Last Reviewed: 2026-08-01
> Source: `ai-dev/audits/2026-07-31-0423-arm-MA3.4-nop-ai-error-handling.md`（P2-MA3-1、P3-MA3-1）+ `docs-for-ai/02-core-guides/error-handling.md` + `ai-dev/audits/arm-index.md`
> Mission: audit-remediation
> Work Item: P2-MA3-1 + P3-MA3-1（第十二批 deferred successor 重开）
> Related: `2026-08-01-0936-2-arm-p3-error-handling-core-coder.md`、`2026-08-01-0936-3-arm-p3-error-handling-tools-toolkit.md`（同批次兄弟计划，模块面互不重叠可并行；登记顺序 = 执行顺序）

## Purpose

把 MA3.4 错误处理审计中审计点名但从未被单独重开（仅被 MR 批量裁定为 watch-only residual）的裸 `IllegalArgumentException` 集群在 nop-ai-agent 与 nop-ai-shell 两个模块收口：全部转换为模块级异常（`NopException` + 模块 ErrorCode，英文消息保持），使审计点名的 IAE 绕过框架异常体系问题在模块内清零。nop-ai-maven（P2-MA3-2）与 code-analyzer（P2-MA3-4）的同型转换已在批次 5（1834-3）完成，本计划是同一规范工作的 P3 残余收口。

## Current Baseline

- **MA3.4 审计结论**：P2-MA3-1（nop-ai-shell 13 处 IAE）被 audit 复核降级为 P3（"Actual count is 13 not 100+; P2 too severe"），但 audit 明确否定了"风格问题"定性：*"bare IllegalArgumentException is not NopException-compatible. This is not a style preference — it's a structural gap that prevents framework-level error handling"*（Confidence: Certain）；P3-MA3-1（nop-ai-agent ~50 IAE）audit 复核 Retain (P3)："nop-ai-agent 已定义 NopAiAgentException（(String) 与 (ErrorCode) 构造器）+ 200+ 处使用，62 个 IAE 残留是明显不一致"（Confidence: Likely）。
- **批次数**：1834-3（批次 5）Out Of Scope 段显式排除 "P3 项（P2-MA1-023~030、P2-MA3-1 等）"——被排除但**从未在后续批次（6-11）单独重开或裁定**；roadmap v12 尾部把 MA1-MA3 的 P2/P3 findings 批量裁定为 watch-only residual，仅把 P3-MA1-023~030/P3-MA1-039 显式声明为"不入 scope"，未单独覆盖本集群。批次 9 先例（"按严重度排序重开 watch-only 项"，P2-MA1-004/005 同型）支持本集群重开。
- **live 现状（grep 复核 2026-08-01）**：
  - `nop-ai-agent`：**62 处** `throw new IllegalArgumentException`（33 个文件），含 InMemoryVectorAdapter:107、DockerSandboxBackend:181、SandboxRequest:39、DefaultDenialLedger:52/64、SandboxConfig:71/74/77、DefaultSessionTimeoutHandler、DefaultTeamTaskRecoveryHandler、InMemoryActorRegistry、InMemoryActorRuntime、CallAgentRequestPayload、DeferredAckMailbox、MailboxEntry、CallAgentResponsePayload、InMemoryWriteIntentRegistry、DbTeamTaskStore 等；消息全部英文且质量良好（audit 确认）。
  - `nop-ai-shell`：**12 处**（5 个文件）：Redirect.java:48/172/177/197、GroupExpr.java:16、PipelineExpr.java:16、LogicalExpr.java:36、ShellCommandRegistry.java:35/40/54/58/62。
  - 异常基础设施在位：agent 有 `NopAiAgentException`（engine 包，String/ErrorCode/cause 构造器）+ `NopAiAgentErrors`（21 码，全部为 NOT_SUPPORTED 类，需新增参数校验类通用码）；shell 有 `NopAiShellErrors`（3 码：ERR_AI_SHELL_OUTPUT_NOT_INPUT/CHUNK_NOT_TEXT/TEE_NO_OUTPUT，批次 7 新增，需扩展校验类码）。
  - 测试断言影响面：nop-ai-agent **20 个测试文件**（50 处 `assertThrows(IllegalArgumentException)`，live grep 复核）、nop-ai-shell 3 个（RedirectTest/CommandModelTest/ShellCommandRegistryTest）——转换后断言需同步。
  - **catch IAE 调用面（live 复核）**：nop-ai-agent main 存在 5 处 `catch (IllegalArgumentException e)`（LLMCurator:286、FileSystemSkillProvider:275/284、AgentMessageEnvelopeJson:108、CheckpointJournalReader:177），逐一核验均只捕获 JDK 产物（`Enum.valueOf`：SkillQualityRating/AgentMessageKind/CheckpointType），**无一包住本计划 62 个转换站点**——影响面为 0，但 Phase 1 需逐处证明"JDK 契约来源"并留档。
  - **既有 ISE 集群（不属本计划 scope，裁定见 Deferred）**：nop-ai-agent 另有 9 个文件 12 处 `throw new IllegalStateException`（ThresholdBreaker "Unknown circuit state"、MemberExecOutcome "toException called on a COMPLETED outcome" 等）、nop-ai-shell 6 个 io 类 "output closed" 等——audit 未点名（scope 仅 IAE），本计划不处理，显式登记为 watch-only residual（见 Deferred But Adjudicated）。
- **同规范先例（方法学锚点）**：1834-3（P2-MA3-2：nop-ai-maven 13 处裸 IAE/RTE → `NopException` + 新 `NopAiMavenErrors`，IO 失败保留 cause；P2-MA3-4：FileLanguageStats RTE → NopException + ERR_STATS_IO_FAILED）；2248-2（批次 7：25 处 UOE → NopException + ErrorCode，新增 NopAiAgentErrors 21 码 / NopAiShellErrors 3 码 / NopAiCoreErrors 1 码，测试断言同步改错误码/异常类型）。
- **绿色基线**：`ai-dev/logs/2026/08-01.md` 记录全量 `./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（2907+ tests 0 failures）；`scan-hollow-implementations.mjs --module nop-ai --severity high` exit 0（批次 7 清零后无新增）。

## Goals

- nop-ai-agent 62 处 + nop-ai-shell 12 处裸 IAE 全部转换为 `NopException` + 模块 ErrorCode（英文消息逐字保持，cause 链保持），转换后 grep 对应模块 main 目录 `throw new IllegalArgumentException|RuntimeException` = 0 命中
- 为转换新建的 ErrorCode 全部落在既有模块 Errors 接口内（`NopAiAgentErrors` / `NopAiShellErrors`），英文描述，遵循 `nop.err.ai.*` 前缀约定
- 受影响测试断言同步（agent 20 + shell 3 个测试文件），新增 focused 测试断言转换后的异常类型/错误码，测试数量不减少
- 零行为变更（消息文本、触发条件、cause 均保持；仅异常类型/错误码变化），arm-index 登记第十二批

## Non-Goals

- 不处理 P3-MA3-4（JavaMethodReplacer System.out——audit 已裁定 informational："main() is demo entry, not production runtime"）
- 不处理 nop-ai-core/nop-ai-coder/nop-ai-tools/nop-ai-toolkit 的 IAE（兄弟计划 0936-2 / 0936-3 承接）
- 不重开 P3-MA1-023~030、P3-MA1-039（roadmap v12 显式"不入 scope"）
- 不处理 MA4.2-01/MA4.2-14（批量格式化 optimization candidate，多次裁定不重开）
- 不做异常消息内容优化或错误码 i18n 化（超出本计划范围）

## Scope

### In Scope

- `nop-ai/nop-ai-agent/src/main/java/` 下全部 62 处裸 IAE（33 文件，执行时以 Phase 1 重新 grep 盘点为准）
- `nop-ai/nop-ai-shell/src/main/java/` 下全部 12 处裸 IAE（5 文件）
- 相关测试断言更新 + 新增 focused 测试
- `NopAiAgentErrors` / `NopAiShellErrors` 新增 ErrorCode + arm-index/roadmap 登记

### Out Of Scope

- 非 nop-ai 模块代码；nop-ai-maven/code-analyzer（已修）；core/coder/tools/toolkit（兄弟计划）
- 既有 catch IAE 的调用点改造（grep 确认调用面，若无 IAE 特定 catch 则无此项）

## Execution Plan

### Phase 1 - 盘点与逐处裁定（design 前置）

Status: completed
Targets: 两模块 main 目录 grep + `NopAiAgentErrors.java` + `NopAiShellErrors.java`

- Item Types: `Decision | Proof`
- [x] grep 全量盘点 nop-ai-agent（预期 62 处/33 文件）与 nop-ai-shell（预期 12 处/5 文件）裸 IAE 清单（文件:行号:消息），与 audit 明细对照，输出逐处处置表（转换目标 ErrorCode）落盘 daily log
- [x] 逐处裁定分类：(a) 参数/状态校验类 → 转换；存在极少数实现 JDK 接口契约必须抛 IAE 的情形（如实现 JDK 函数式接口）→ 显式裁定保留并记录理由；裁定结果进入逐处处置表
- [x] 规划新增 ErrorCode（英文描述）：agent 侧如 `ERR_AI_AGENT_INVALID_ARG`（通用参数校验，带 `ARG_*` param 可选）+ 少量场景码；shell 侧如 `ERR_AI_SHELL_INVALID_REDIRECT`/`ERR_AI_SHELL_EMPTY_COMMAND`/`ERR_AI_SHELL_UNKNOWN_SYMBOL`/`ERR_AI_SHELL_COMMAND_NOT_FOUND` 等；码名与描述执行时按处置表最终确定，遵循 `nop.err.ai.*` 前缀
- [x] 盘点调用面：grep 两模块 main+test 是否存在 `catch (IllegalArgumentException` 依赖 IAE 类型捕获的调用点——已知 agent main 5 处（LLMCurator:286、FileSystemSkillProvider:275/284、AgentMessageEnvelopeJson:108、CheckpointJournalReader:177），逐处核验其捕获来源（预期均为 JDK `Enum.valueOf` 产物），输出影响面清单（预期结论：与 62 个转换站点零交集，影响 0）

Exit Criteria:

- [x] 逐处处置表落盘 daily log（文件:行号:消息:处置:目标 ErrorCode），两模块 IAE 总数与审计/live grep 一致
- [x] 新增 ErrorCode 清单（名称+英文描述）已确定且未重复既有码
- [x] `catch (IllegalArgumentException` 调用面清单完成（5 处逐一核验来源并记录，预期零交集）
- [x] No owner-doc update required（转换沿既有 error-handling.md 约定，不改变规范本身）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - nop-ai-agent 转换（62 处）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/`（33 文件）+ `NopAiAgentErrors.java`

- Item Types: `Fix`
- [x] 按处置表将 62 处 `throw new IllegalArgumentException("msg")` 转换为 `throw new NopException(ErrorCode).param(...)` 或 `throw new NopAiAgentException(ErrorCode)`（消息经 ErrorCode 英文描述承载，动态值用 `.param()`；对既有 `NopAiAgentException(String)` 使用面保持不扰动——仅新增 throw 走 ErrorCode 路径），消息语义逐字保持，cause 保留
- [x] `NopAiAgentErrors` 新增规划 ErrorCode（英文描述，`nop.err.ai.agent.*` 前缀与既有 21 码一致）
- [x] 更新 agent 测试目录 20 个引用 IAE 的测试文件断言（assertThrows(IllegalArgumentException) → 对应异常类型/ErrorCode；若断言仅验证"抛异常"语义，改用 NopAiAgentException 类型）
- [x] 为转换后的代表性路径新增 focused 测试（值级断言：错误码、消息、param），覆盖每类新 ErrorCode 至少 1 条

Exit Criteria:

- [x] `grep -rn "throw new IllegalArgumentException\|throw new RuntimeException" nop-ai/nop-ai-agent/src/main/java` = 0 命中（JDK 契约保留项若有则显式列出，且总数与处置表一致）
- [x] 新增 ErrorCode 全部在 `NopAiAgentErrors` 内且英文描述；`check-import-order.mjs` 0 新违规
- [x] focused 测试通过且为值级断言；测试数量不减少（`@Test` 计数对比）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` BUILD SUCCESS
- [x] No owner-doc update required（沿既有约定；如需记录 ErrorCode 使用模式则补 `ai-dev/logs/`）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - nop-ai-shell 转换（12 处）

Status: completed
Targets: `nop-ai/nop-ai-shell/src/main/java/`（5 文件）+ `NopAiShellErrors.java`

- Item Types: `Fix`
- [x] 按处置表将 12 处裸 IAE 转换为 `NopException` + 新 `NopAiShellErrors` 码（Redirect/GroupExpr/PipelineExpr/LogicalExpr 语法校验 + ShellCommandRegistry 命令/别名校验），消息语义逐字保持，cause 保留
- [x] 更新 shell 3 个测试文件断言（RedirectTest/CommandModelTest/ShellCommandRegistryTest）+ 新增 focused 测试（值级断言错误码/消息）

Exit Criteria:

- [x] `grep -rn "throw new IllegalArgumentException\|throw new RuntimeException" nop-ai/nop-ai-shell/src/main/java` = 0 命中
- [x] 新增 ErrorCode 全部在 `NopAiShellErrors` 内且英文描述；focused 测试值级断言通过；测试数量不减少
- [x] `./mvnw test -pl nop-ai/nop-ai-shell -am -T 1C` BUILD SUCCESS
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 全量验证 + 登记

Status: completed
Targets: `nop-ai` 全模块 + `arm-index.md` + `ai-dev/backlog/audit-remediation-roadmap.md`

- Item Types: `Proof`
- [x] `./mvnw clean install -DskipTests -pl nop-ai -am -T 1C` BUILD SUCCESS + `./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（全量回归，0 failures）
- [x] `scan-hollow-implementations.mjs --module nop-ai --severity high` exit 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] arm-index 新增「P3 追踪（第十二批 — 错误处理规范）」小节登记（P2-MA3-1、P3-MA3-1 → fixed，含转换计数与证据）；roadmap 登记第十二批

Exit Criteria:

- [x] 全量 build + 全量 test 绿
- [x] scan-hollow exit 0；check-doc-links exit 0
- [x] arm-index 与 roadmap 登记完成
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] agent 62 处 + shell 12 处裸 IAE 全部转换或显式裁定保留（处置表齐全），两模块 main grep 0 残留
- [x] 新增 ErrorCode 英文描述、前缀规范、无重复；消息语义与 cause 零变更
- [x] 受影响测试断言全部同步且新增 focused 测试（值级断言），测试数量零减少
- [x] 不存在被静默降级的 in-scope live defect（本集群为审计确认的规范漂移，全部落定）
- [x] arm-index 与 roadmap 已登记
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证转换后的异常在运行时可达（抽查 3-5 处 throw 路径 + focused 测试断言），无空壳
- [x] `./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（Phase 4 已执行，此处为 closure 复核）

## Deferred But Adjudicated

### nop-ai-agent / nop-ai-shell 既有 IllegalStateException 集群

- Classification: `watch-only residual`
- Why Not Blocking Closure: audit（MA3.4）scope 仅点名 IAE（P2-MA3-1/P3-MA3-1），ISE 未被审计点名；ISE 语义上多表示"状态机/生命周期非法调用"（ThresholdBreaker "Unknown circuit state"、MemberExecOutcome、shell io "output closed" 等），与参数校验 IAE 属不同类别，且无 audit finding 归属。登记为 watch-only，后续如需规范收口另行规划，不阻塞本计划 closure。
- Successor Required: `no`

### P3-MA3-4 JavaMethodReplacer System.out

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: audit 已裁定 informational（"main() is demo entry, not production runtime"），无运行时行为面。
- Successor Required: `no`

### 极少数 JDK 接口契约 IAE（若 Phase 1 发现）

- Classification: `watch-only residual`（若存在）
- Why Not Blocking Closure: 实现 JDK 函数式接口（如 `java.util.function.*`）时契约强制 IAE；此类保留不构成规范漂移。
- Successor Required: `no`

## Non-Blocking Follow-ups

- nop-ai-core/nop-ai-coder IAE 由兄弟计划 `2026-08-01-0936-2-arm-p3-error-handling-core-coder.md` 承接；nop-ai-tools/nop-ai-toolkit 由 `2026-08-01-0936-3-arm-p3-error-handling-tools-toolkit.md` 承接（模块面不重叠，可与本计划并行；登记顺序即执行顺序）

## Closure

Status Note: 全部 4 Phase completed、Closure Gates 全勾选、独立子 agent closure audit APPROVE（12/12 PASS）、文本一致性核对通过（Plan Status/Phase Status/Exit Criteria/Closure Gates/daily log 五处一致）——MA3.4 审计点名的裸 IAE 集群在 nop-ai-agent（62 处）与 nop-ai-shell（12 处）收口完成，全部转换为 NopException + 模块 ErrorCode，消息逐字零漂移。
Completed: 2026-08-01

Closure Audit Evidence:

- Reviewer / Agent: independent subagent（fresh session，task `ses_044d2f34affew4vz6nF0eaT15V`）
- Audit Session: `ses_044d2f34affew4vz6nF0eaT15V`
- Evidence:
  - 每条 Exit Criterion 的验证结果（PASS/FAIL + 对应的 live code path 或 test name）：12/12 PASS——(1) agent/shell main `throw new IllegalArgumentException|RuntimeException` grep 双 0；(2) diff 文件集 = 33+5 转换文件 + 2 Errors 接口，纯 throw 替换无杂质；(3) `ERR_AI_AGENT_INVALID_ARG`（nop.err.ai.agent.invalid-arg，invalid argument: {msg}）+ shell 5 新码英文描述前缀合规、`uniq -d` 无重复；(4) ThresholdBreaker/InMemoryVectorAdapter/StandardRetryPolicy/ShellCommandRegistry/Redirect 等 ≥5 站点消息逐字比对 HEAD 一致，cause 零新增 try/catch；(5) 5 处 catch IAE 全部 JDK `Enum.valueOf` 产物且文件不在改动集（零交集）；(6) agent/shell 测试文件 IAE 引用 0，3 个 focused 值级测试（TestThresholdBreaker.validationFailureCarriesErrorCodeAndVerbatimMessage / ShellCommandRegistryTest.validationFailuresCarryErrorCodeAndVerbatimMessage / CommandModelTest.expressionValidationFailuresCarryErrorCodeAndVerbatimMessage）断言 errorCode+param+getMessage；(7) @Test 372→375（+3，零减少）；(8) daily log Phase 4 记录 clean install + 全量 test BUILD SUCCESS（audit 采用日志证据，未重跑 ~10min 构建）；(9) Anti-Hollow：diff 无逻辑删除/空体/no-op，throw 站点全在构造器/公开方法守卫路径且被 focused+既有测试执行，NopAiAgentException(ErrorCode) 构造器在位；(10) arm-index:488 第十二批小节 + roadmap:264 第十二批行 ✅；(11) Deferred 三项分类诚实（ISE watch-only / P3-MA3-4 out-of-scope / JDK 契约保留数 0）；(12) plan 文本一致性（34 项 in-scope 全 [x]，4 Phase completed）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0（确认无未勾选项 + Closure Evidence 已写入）
  - Anti-Hollow 检查结果：throw 路径运行时可达（构造器校验 + 公开方法守卫 + 3 focused 测试实际执行断言）；`scan-hollow-implementations.mjs --module nop-ai --severity high` 退出码为 0
  - Deferred 项分类检查：无 in-scope live defect 被降级（三类均非 blocking，理由齐备）

Follow-up:

- nop-ai-core/nop-ai-coder / nop-ai-tools/nop-ai-toolkit IAE 由兄弟计划 `2026-08-01-0936-2` / `2026-08-01-0936-3` 承接（并行中，模块面不重叠）
- nop-ai-agent/shell 既有 ISE 集群与 P3-MA3-4 System.out 保持 watch-only residual（见 Deferred But Adjudicated）
- 其余：no remaining plan-owned work

## Optional Sections

## Risks And Rollback

- 异常类型变化可能影响 catch IAE 的调用点——Phase 1 已盘点调用面，预计 0；如有则同步修改。回滚 = 单 commit revert。
- 消息语义漂移风险——转换必须逐字保持消息文本（经 ErrorCode 描述或 `.param()` 承载），closure audit 抽查。
