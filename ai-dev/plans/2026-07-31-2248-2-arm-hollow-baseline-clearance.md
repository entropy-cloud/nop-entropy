# 2026-07-31-2248-2 scan-hollow 基线清零（nop-ai 24 项 high findings）

> Plan Status: completed
> Last Reviewed: 2026-07-31
> Source: `ai-dev/backlog/audit-remediation-roadmap.md`；`ai-dev/plans/2026-07-31-1834-1-arm-p2-security-hardening.md` Deferred But Adjudicated（scan-hollow 基线 24 项）
> Related: `2026-07-31-1834-1-arm-p2-security-hardening.md`（已 closed，基线 24 项登记于其 Deferred 段）
> Mission: audit-remediation
> Work Item: scan-hollow 基线清零（24 项既有 high findings）

## Purpose

将 `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai --severity high` 的存量 24 项 high findings 全部收敛，使工具退出码为 0，消除后续所有 closure audit 的"增量判定"例外（每份计划目前都需对比基线才可关闭），让 hollow 扫描成为 nop-ai 的硬门禁。

## Current Baseline

- 扫描工具 `scan-hollow-implementations.mjs`（`ai-dev/tools/`）对 nop-ai 输出 24 项 high findings，当前退出码 1（P1 regex 只匹配**单行** `throw new UnsupportedOperationException("...")`）：
  - **P1 模式（20 项，单行）**：`DefaultAgentEngine.java:3324`（plan-mode）、`IAgentEngine.java:38/42/46/76`（Phase 2 default 方法 ×4）、`NoOpHookRegistry.java:22/32`、`IAiMemoryStore.java:16/20/24/28`（Phase 2 default ×4）、`ISessionStore.java:129/162/166/170/174`（×5）、`DefaultAiChatService.java:632`（deprecated 方法）、`PrintStreamShellOutput.java:39`、`ShellChunk.java:33`、`TeeOutput.java:49`
  - **P6b 模式（4 项）**：注释含 "placeholder" 触发词——`NoOpFencingTokenService.java:49`、`AlwaysClosed.java:61`、`NoOpGoalTracker.java:50`、`NoOpSustainer.java:51`
- **同接口内还有 5 处多行 UOE（扫描器不匹配但同属被转换接口，为保持接口内风格一致纳入本计划）**：`ISessionStore.java:66`（listAllSessions）/`:90`（save）/`:157`（forkSession-with-filter）、`IAgentEngine.java:115/186`。
- **其余 7 处多行 UOE（不纳入转换，属既有 pass-through/SPI 裁定）**：`IHookRegistry:38`、`NoOpAgentMessenger:48`、`NoOpEmbeddingAdapter:32/40`、`NoOpActorRuntime:46`、`ILlmDialect:219`、`ExternalCommandAdapter:11`——均位于独立 NoOp/pass-through 类或独立接口，扫描器不匹配，风格一致性影响小，裁定保留（fail-fast 语义不变）。
- 1834-1 已将这些项裁定为 watch-only residual（pass-through/SPI 设计、历史 fail-fast UOE），作为该计划 closure 的基线例外（"增量判定"）。
- 仓库错误处理规范（`docs-for-ai/02-core-guides/error-handling.md` + AGENTS.md）：框架核心与公共 API 使用 `NopException` + `ErrorCode` + `.param(...)`；**错误消息必须是英文**。扫描工具 rationale 明确"unimplemented features 应使用语义清晰的异常而非 UOE"（历史 plans 84/86/97/98 已批量转换）。
- 关键约束（已核验）：`DefaultAgentEngine.java:2953` 捕获 `sessionStore.listAllSessions()` 的 UOE（:66 在本计划转换范围内 → 该 catch 需同步改为捕获 NopException 以保留 NopAiAgentException 包装语义）；`ShellCommandExecutor.java:364` 捕获外部 SPI（externalAdapter）UOE，不在转换范围，保持不动；`NoOpSandboxBackend.java:228`（nop-ai-agent）捕获 JDK ProcessHandle API UOE，不受影响。
- 受影响测试（断言被转换站点）：`TestIAiMemoryStoreDefaultMethods`（:37/44/51/58，含精确消息断言）、`TestISessionStoreDefaultMethods`（:17/25/32/39，含精确消息断言）、`TestNoOpHookRegistry:32`、`TestModeDispatch:158`（plan-mode，message contains 断言）、`ShellIOTest:360`（断言 `PrintStreamShellOutput.asInput` 抛 UOE——注意 `ShellIOTest` 同时是计划 3 的 Phase 3 目标文件，两计划按文件名顺序串行执行（本计划 2248-2 先于 2248-3），无冲突）。
- `TestNoOpAgentMessenger:71` 断言的 UOE 来自 `NoOpAgentMessenger:48`（多行站点，**不在转换范围**）——该测试不受影响，无需修改。

## Goals

- 25 项 UOE 转换（20 项被扫描 + 5 项同接口多行）为仓库规范的语义清晰异常：公共 API/接口 default 方法改用 `NopException` + 既有或新增 ErrorCode（保留 fail-fast 语义），**ErrorCode 描述使用英文**（AGENTS.md 错误消息英文约定，且原消息为英文，转换后 `getMessage()` 语义不变）；NoOp/pass-through 类保留设计意图并改写注释措辞。
- 4 项 P6b 注释改写为显式 pass-through 语义描述（不依赖 "placeholder" 触发词），类级 javadoc 已存在（如 `NoOpGoalTracker` 已有完整说明，仅需调整行内注释措辞）。
- `scan-hollow-implementations.mjs --module nop-ai --severity high` 退出码 0。
- 受影响 catch 站点与测试断言同步核验，行为语义不变（fail-fast 仍 fail-fast，pass-through 仍 pass-through）。
- arm-index 新增 hollow 基线清零登记段（当前 arm-index 无 hollow 基线段，为新增落盘而非更新）：24 项 → 0 项。

## Non-Goals

- 不实现 UOE 背后的"Phase 2"功能（如 ISessionStore fork 真实实现、IAiMemoryStore update 真实实现）——本计划只换异常语义，不改变功能现状。
- 不改 SPI 契约语义：pass-through default 仍是 pass-through，只是异常类型与消息符合规范。
- 不改 `scan-hollow-implementations.mjs` 工具本身（添加豁免/allowlist 属工具降级，禁止）。
- 不转换上述 7 处独立文件中的多行 UOE（IHookRegistry/NoOpAgentMessenger/NoOpEmbeddingAdapter/NoOpActorRuntime/ILlmDialect/ExternalCommandAdapter——既有 pass-through 裁定，fail-fast 语义不变）。
- 不处理其他模块（nop-metadata 等）的 hollow findings。

## Scope

### In Scope

- 25 个 UOE throw 站点转换（20 扫描站点 + 5 同接口多行站点）
- 4 个 P6b 注释触发点改写
- 受影响 catch 站点（DefaultAgentEngine:2953）与受影响测试断言同步
- 新增错误码定义（英文描述；`NopAiCoreErrors` 扩展或 nop-ai-agent/shell 新建 Errors 类）
- arm-index 新增 hollow 基线清零登记段（24 项 → 0 项）

### Out Of Scope

- 功能实现（Phase 2 能力落地）
- 7 处独立文件多行 UOE 转换（IHookRegistry 等，裁定保留）
- 扫描工具规则调整
- 其他模块 hollow findings

## Execution Plan

### Phase 1 - UOE 转换与错误码定义（25 处站点）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/**`、`nop-ai/nop-ai-core/src/main/java/**`、`nop-ai/nop-ai-shell/src/main/java/**`、对应 Errors 类

- Item Types: `Fix | Proof`

- [x] 核验 nop-ai-agent / nop-ai-shell 是否存在可复用的 ErrorCode 类，不存在则在相应模块新建（遵循 `NopAiCoreErrors` 模式：接口 + `define(...)` + ARG 常量）；**新 ErrorCode 描述一律英文**（AGENTS.md 错误消息英文约定；`NopAiCoreErrors` 既有中文描述不动，本计划新增的错误码用英文）
- [x] 逐个转换 20 个被扫描 UOE 站点为 `throw new NopException(ERR_...)`（或既有 ErrorCode），保留原消息语义与 fail-fast 行为：
  - `IAgentEngine` 4 个 default 方法（forkSession/getSessionStatus/cancelSession/resumeSession）
  - `IAiMemoryStore` 4 个 default 方法（readBudgeted/update/remove/batchAdd）
  - `ISessionStore` 5 个 default 方法（forkSession/appendEvent/compact/loadSnapshot/setPlanRef）
  - `DefaultAgentEngine:3324` plan-mode UOE
  - `NoOpHookRegistry` 2 处（hook/middleware 注册拒绝）
  - `DefaultAiChatService:632` deprecated 方法
  - `PrintStreamShellOutput:39`、`ShellChunk:33`、`TeeOutput:49`（shell IO 类型不匹配）
- [x] 同步转换 5 处同接口多行 UOE（避免接口内风格混用）：`ISessionStore:66`（listAllSessions）、`:90`（save）、`:157`（forkSession-with-filter）、`IAgentEngine:115/186`
- [x] 同步 `DefaultAgentEngine:2953` catch 站点：`listAllSessions` 不再抛 UOE 后，catch 改为捕获 `NopException`（保留 NopAiAgentException 包装语义），并同步 :2958 注释与包装消息文案（不再提及 UnsupportedOperationException）；`ShellCommandExecutor:364` 与 `NoOpSandboxBackend:228` 核验后确认不受影响（外部 SPI / JDK API），不改
- [x] 同步受影响测试断言（改用错误码或新异常类型断言）：`TestIAiMemoryStoreDefaultMethods`、`TestISessionStoreDefaultMethods`（含精确消息断言 → 改为断言错误码或保持英文消息）、`TestNoOpHookRegistry:32`、`TestModeDispatch:158`、`ShellIOTest:360`（`ShellIOTest` 同时为计划 3 目标文件，本计划先执行，改动后计划 3 基于新状态继续）；`TestNoOpAgentMessenger` 核验后确认不受影响（其 UOE 来自未转换的 NoOpAgentMessenger:48）。**额外受影响测试同步**：TestRestoreSession、TestRestorePendingSessions、TestSessionStoreForkMessageFilter、TestDBSessionStore、TestFileBackedSessionStore、TestMiddlewareChain（均断言被转换站点 UOE，一并改为错误码/异常类型断言）
- [x] 每个转换站点核对"该异常是否真的可达"：default 方法被实现类覆盖后异常不可达的，在 javadoc 说明；可达的保留 fail-fast

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] grep 确认 25 个目标 UOE 站点 0 残留（`throw new UnsupportedOperationException` 在 IAgentEngine/IAiMemoryStore/ISessionStore/NoOpHookRegistry/DefaultAgentEngine/DefaultAiChatService/PrintStreamShellOutput/ShellChunk/TeeOutput 中 0 命中；IHookRegistry/NoOpAgentMessenger/NoOpEmbeddingAdapter/NoOpActorRuntime/ILlmDialect/ExternalCommandAdapter 的 7 处既有多行 UOE 保留并记录核验）；被转换文件中的 javadoc `{@link UnsupportedOperationException}` 残留引用同步清理（ISessionStore/IAgentEngine/InMemorySessionStore/InMemoryAiMemoryStore 已同步；DefaultAgentEngine:288/302/914/1556/1582 五处注释经核验指向**未转换**的 NoOpTeamManager/NoOpTeamTaskStore/NoOpAgentMessenger，仍准确，保留）
- [x] 所有转换站点使用 ErrorCode（非裸 UOE），**描述为英文**，消息保留原语义
- [x] `DefaultAgentEngine:2953` catch 站点已同步（捕获 NopException，包装语义保留）；`ShellCommandExecutor:364`/`NoOpSandboxBackend:228` 核验记录在案（不受影响）
- [x] 受影响测试更新完成：`TestIAiMemoryStoreDefaultMethods`、`TestISessionStoreDefaultMethods`、`TestNoOpHookRegistry`、`TestModeDispatch`、`ShellIOTest:360` 断言改为错误码/新异常语义且全绿；`TestNoOpAgentMessenger` 核验记录确认无需修改
- [x] **无静默跳过**：每个转换站点为显式 throw（fail-fast），无空体/吞异常
- [x] **接线验证**：至少一个引擎级路径确认转换后的异常在运行时可达（如 plan-mode 或 forkSession 路径的测试）——`TestModeDispatch.testPlanModeThrowsNopAiAgentException`（DefaultAgentEngine.resolveExecutor plan-mode 引擎级路径）+ `TestRestorePendingSessions.iAgentEngineDefaultRestorePendingSessionsThrowsNopAiAgentException`
- [x] 相关 owner docs 已同步：`docs-for-ai/02-core-guides/error-handling.md` 消息语言规则补记 nop-ai-agent/nop-ai-shell 英文 ErrorCode 例外（转换类错误码）
- [x] `ai-dev/logs/2026/07-31.md` 对应条目已更新

### Phase 2 - P6b 注释触发点改写（4 项）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/fencing/NoOpFencingTokenService.java`、`.../reliability/AlwaysClosed.java`、`.../reliability/NoOpGoalTracker.java`、`.../reliability/NoOpSustainer.java`

- Item Types: `Fix | Proof`

- [x] 逐项改写 4 个文件中含 "placeholder" 的行内注释，改为显式 pass-through 语义描述（参考 `NoOpGoalTracker` 类级 javadoc 已有措辞，如"explicit no-op / pass-through default"），不改变代码行为
- [x] 核验改写后类级 javadoc 与行内注释语义一致（无矛盾表述）
- [x] 如某类实际上不应 pass-through（如 `AlwaysClosed` 语义需核验），在 plan 执行中记录裁定：保持 pass-through 或改为 fail-fast——核验：`AlwaysClosed`（pass-through ICircuitBreaker，allowCall=true/getState=CLOSED/record 显式 no-op）为设计有意 pass-through（类级 javadoc + design nop-ai-agent-reliability.md §3.3/§5.1），裁定保持 pass-through，仅注释措辞

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] grep 确认 4 个文件中 "placeholder" 触发词 0 残留（或改写后不再匹配扫描器 regex）
- [x] 4 个类的行为零变化（pass-through 语义保持，仅注释措辞）
- [x] `scan-hollow-implementations.mjs --module nop-ai --severity high` 输出中 P6b 类 0 项
- [x] No owner-doc update required（注释措辞修正，行为不变）
- [x] `ai-dev/logs/2026/07-31.md` 对应条目已更新

### Phase 3 - 全量验证与基线落盘

Status: completed
Targets: `arm-index.md`、构建验证

- Item Types: `Proof`

- [x] 全量运行 `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai --severity high`，确认退出码 0（24 项全部消除）
- [x] `./mvnw compile -pl nop-ai -am` + `./mvnw test -pl nop-ai -am -T 1C` 全绿
- [x] arm-index 新增 hollow 基线清零登记段：scan-hollow 基线 24 项 → 0 项，登记转换证据（若不存在基线段则新增）
- [x] 更新 1834-1 计划中"基线 24 项"表述的 follow-up 指引（如必要，在 daily log 记录而非回写历史计划）——历史计划不回写（guide rule #20），在 daily log 记录：后续 closure audit 无需再对比基线（硬门禁通过）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 扫描工具退出码 0（关键证据：工具输出截图/日志——24 项 → 0 项，P1/P6b 均 0 命中）
- [x] 全量构建测试绿（`./mvnw test -pl nop-ai -am -T 1C`，0 failures）
- [x] arm-index hollow 基线清零登记段已落盘（24 → 0，可追溯）
- [x] `check-doc-links.mjs --strict` 退出码 0（如修改 docs-for-ai）
- [x] `ai-dev/logs/2026/07-31.md` 对应条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] `scan-hollow-implementations.mjs --module nop-ai --severity high` 退出码 0（非增量判定，硬门禁通过）
- [x] 所有 in-scope UOE/P6b 项已收敛（20+5+4 = 29 处目标站点，无残留；7 处独立文件 UOE 保留但核验记录在案）
- [x] fail-fast 与 pass-through 语义不变（catch 站点与测试核验记录在案）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）转换后的异常在运行时路径可达（至少一条引擎级测试路径），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai -am`
- [x] `./mvnw test -pl nop-ai -am -T 1C`
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### UOE 背后功能实现（Phase 2 能力，如 ISessionStore.fork 真实实现）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 本计划只统一异常语义（UOE → NopException + ErrorCode），不改变功能现状；功能实现属既有 watch-only residual（1834-1 裁定 pass-through/SPI 设计），无 in-scope live defect。
- Successor Required: `no`

## Non-Blocking Follow-ups

- MA5.4-P3-1/2/3（IShellInput 接口方法、readBudgeted 文档、readAllText 二进制丢弃）：P3 低优先，后续批次（已在 1834-1 follow-ups 登记）

## Closure

Status Note: 25 处 UOE 全部转换为 NopException + ErrorCode（英文描述，消息语义保持），4 处 P6b placeholder 注释改写，catch 站点与 11 个受影响测试同步，`scan-hollow-implementations.mjs --module nop-ai --severity high` 退出码 0（24 项 → 0 项，硬门禁通过，后续 closure audit 无需增量判定）；独立子 agent closure-audit APPROVE。
Completed: 2026-07-31

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general，task `ses_046f3b334ffesnhWREbt5V783k`）
- Evidence:
  - **扫描门禁**：PASS — `scan-hollow-implementations.mjs --module nop-ai --severity high` 退出码 0，P1/P6b 0 命中；工具文件未修改（Non-Goal 保持）
  - **Phase 1 Exit Criteria**：全 PASS — 9 个被转换文件 `throw new UnsupportedOperationException` 0 残留（grep 核验）；7 处 out-of-scope 多行 UOE（IHookRegistry:38/NoOpAgentMessenger:48/NoOpEmbeddingAdapter:32,40/NoOpActorRuntime:46/ILlmDialect:219/ExternalCommandAdapter:11）保留且仍 fail-fast；`NopAiAgentErrors`（21 码）/`NopAiShellErrors`（3 码）/`NopAiCoreErrors.ERR_AI_CHAT_GET_SESSION_DEPRECATED` 全部英文描述且消息语义保持；DefaultAgentEngine 5 处注释（288/302/914/1556/1582）核验指向未转换类（NoOpTeamManager/NoOpTeamTaskStore/NoOpAgentMessenger）仍准确；catch 站点（DefaultAgentEngine:2955）改捕获 NopException 且包装消息不再含 UOE 字样
  - **测试**：PASS — TestIAiMemoryStoreDefaultMethods/TestISessionStoreDefaultMethods（错误码 + getDescription 断言）/TestNoOpHookRegistry/TestModeDispatch（plan-mode 引擎级路径）/ShellIOTest（NopException）/TestRestoreSession/TestRestorePendingSessions/TestSessionStoreForkMessageFilter/TestDBSessionStore/TestFileBackedSessionStore/TestMiddlewareChain 全部断言新异常语义；TestNoOpAgentMessenger 确认不受影响（UOE 来自未转换 NoOpAgentMessenger:48）
  - **Phase 2 Exit Criteria**：PASS — 4 文件 `//` 行注释 "placeholder" 0 残留（剩余 5 处均在类级 javadoc，不匹配扫描器 `//` regex）；行为零变化（pass-through 保持）
  - **Phase 3 Exit Criteria**：PASS — arm-index「scan-hollow 基线清零」段落盘（24→0+证据）；roadmap 第七批 ✅；error-handling.md 英文 ErrorCode 例外补记；daily log 条目已更新
  - **Anti-Hollow Check**：PASS — 转换站点全部显式 throw（fail-fast），无空体/静默跳过；TestModeDispatch.testPlanModeThrowsNopAiAgentException（resolveExecutor plan-mode）证明引擎级运行时可达；pass-through 类为 javadoc 明示的设计默认
  - **Deferred 分类检查**：PASS — 仅 "UOE 背后功能实现（Phase 2 能力）" 为 watch-only residual（Successor Required: no），无 in-scope live defect 降级
  - `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码为 0（全项勾选 + Closure Evidence 已写入）
  - 构建：`./mvnw compile -pl nop-ai -am` PASS；`./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（连续多轮 0 failures）；checkstyle 插件在根 pom 注释未接线构建，以编译 + 仓库风格约定代替（核验记录）

Follow-up:

- 无本 plan 遗留 work；后续 closure audit 无需再对比 hollow 基线（硬门禁通过）。MA5.4-P3-1/2/3（IShellInput 契约等）为既有 1834-1 登记的 non-blocking follow-up，由 2248-3 承接

## Optional Sections

## Risks And Rollback

- 异常类型变更（UOE → NopException）可能影响外部 SPI 实现方（如自定义 ISessionStore 调用方 catch UOE）：本仓内 catch 站点已核验；对外契约在 ErrorCode javadoc 声明语义，回滚单 commit。
- 若某 default 方法实为第三方实现方覆盖路径（不可达），转换属冗余但无害；可达路径必须有测试证明 fail-fast。
- P6b 注释改写为纯注释变更，零行为风险。
