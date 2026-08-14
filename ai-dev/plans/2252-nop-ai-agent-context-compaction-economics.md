# 2252 Nop AI Agent 上下文压缩与计价增强（KV 前缀保持 / 阴影计价 / Spill）

> Plan Status: completed
> Last Reviewed: 2026-08-14
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-context-compaction-economics.md`（active）；外部调研见 `ai-dev/analysis/agent-survey/2026-08-13-deepseek-harness-analysis.md`
> Related: `2026-08-02-0900-1-nop-ai-agent-reference-compaction-dual-track.md`、`2026-08-02-0900-2-nop-ai-agent-compaction-snapshot-archive.md`
> Draft Review: 已通过独立对抗性审查（agent ses_0029c659dffeo16uJmTG3GpJs8，三轮：Blocker→Major→approve，2026-08-13）

## Purpose

把设计文档 `nop-ai-agent-context-compaction-economics.md` 的三项决策落地为可验证的实现：KV 前缀保持的摘要调用、阴影 token 计价（shadowedTokenCount + COMPACTION 事件）、Spill 溢出存储。收口到"三机制全部实现 + 各自 focused tests + 端到端验证通过"。

## Current Baseline

- `Layer3FullSummaryStrategy.summarize`（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/compact/Layer3FullSummaryStrategy.java:131-144`）构造摘要请求只有 `SUMMARIZATION_SYSTEM_PROMPT` + user prompt 两条消息，对话 head 未参与请求——摘要调用与真实对话请求无共享前缀。`incrementalUpdatePassesPreviousSummary` 经 `request.getLastUserPrompt()` 断言 `<previous-summary>`。
- `AgentCompactionCoordinator`（`.../engine/AgentCompactionCoordinator.java:151-172`）已写 COMPACTION checkpoint，compactSummary 含 `tokensBefore/tokensAfter/snapshotId`；`CompactionResult` 与 `CompactConfig` 均在 **session 包**（`.../session/CompactionResult.java`），已有 `originalSize/compactedSize` final 字段与 5/6/8 参构造器先例（context-model §8.3 裁定 D）。
- `AgentEventType`（`.../engine/AgentEventType.java`）现有 20 个枚举常量，无 COMPACTION；事件负载在 `AgentEvent` 的 Map（Javadoc 描述负载，如 SESSION_RESUMED）。
- `AgentToolDispatcher`（`.../engine/AgentToolDispatcher.java:311-314`）成功工具结果直接走 `ToolResultTruncator.truncateIfAllowed`（HEAD 6000/TAIL 1000/marker，`.../compact/ToolResultTruncator.java:8-28`）；`NON_TRUNCATABLE_TOOLS`（ask-oracle/ask-human，ToolResultTruncator.java:11-13）绕过截断全文内联。
- **包依赖事实**：session 包已依赖 compact 包（`AgentSession` 持有 `InSessionCompactionArchive`，`.../session/AgentSession.java`），spill store 挂 AgentSession 不引入新依赖方向；tool 包经 `AgentToolExecuteContext.getSession()` 访问 session 有先例（`.../tool/ReadMemoryExecutor.java` 等）。
- `ReferenceCompactionStrategy`（shortRef/read-ref 引用式压缩）已落地；`PipelineCompactor` 四层升级（Truncator→Micro→Layer2→Layer3）已存在。
- 测试基线：`nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/compact/`（TestLayer3FullSummaryStrategy、TestPipelineCompactor、TestToolResultTruncator 等）+ `engine/`（TestCompactionIntegration、TestCompactionInReActLoop、TestAgentEventPublisher）。

## Goals

1. Layer3 摘要请求与压缩后的真实对话请求共享 head 连续前缀（KV 前缀缓存连续性），head 消息级裁剪 + 摘要请求预算配置。
2. `shadowedTokenCount` 作为 `tokensBefore − tokensAfter` 的派生便捷字段进 `CompactionResult`/COMPACTION checkpoint，新增 `COMPACTION` 事件类型（发射点：POST_COMPACT 之后、checkpoint 之后）。
3. Spill 溢出存储：`ISpillStore`（compact 包）+ InMemorySpillStore + read-spill 工具，`AgentToolDispatcher` 成功路径 spill 优先于截断，NON_TRUNCATABLE_TOOLS 豁免，降级截断必须 LOG.warn。

## Non-Goals

- call-agent capabilities 契约、goal 持久轮次驱动（设计文档"后续专题"，另行立计划）。
- spill 内容持久化进存储层（session-and-storage §16.4 审计对齐，后继专题）。
- KV 前缀缓存收益的运行时实测（依赖真实 provider 计价与缓存行为，属评测体系外）。
- 摘要请求 head 之外的前缀结构变更（真实对话消息结构不变）。

## Scope

### In Scope

- `compact/` 包：`Layer3FullSummaryStrategy` 摘要请求改造、`ISpillStore` + `InMemorySpillStore`。
- `session/` 包：`CompactConfig` 预算配置、`CompactionResult` 新字段、`AgentSession` spill store 宿主（lazy init 字段）。
- `engine/` 包：`AgentCompactionCoordinator`（compactSummary 追加 + COMPACTION 事件发射）、`AgentEventType` 新常量、`AgentToolDispatcher` spill 接入。
- `tool/` 包：新工具 `read-spill`。
- 对应测试与文档（02-execution-model.md 事件条目、03-extension-matrix.md 闭合度登记）。

### Out Of Scope

- 压缩触发阈值/水位调整（`AgentCompactionCoordinator.java:55-61` 不动）。
- `ReActAgentExecutor.java:733` 的 LLM 输出摘要截断（非工具结果，不接 spill）。
- provider 侧 KV 缓存行为验证。

## Execution Plan

### Phase 1 - KV 前缀保持的摘要调用

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/compact/Layer3FullSummaryStrategy.java`、`.../session/CompactConfig.java`、`TestLayer3FullSummaryStrategy.java`、`TestCompactionInReActLoop.java`、`TestForcedStop.java`（端到端实际落在 compact 包 `TestCompactionInReActLoop` + engine 包 `TestForcedStop`——后者因 KV 前缀改造导致 system 判别失效 + FixedEstimator 失真场景下预算降级，按新设计语义适配）

- Item Types: `Fix | Fix | Fix | Proof`

- [x] `Fix` Layer3 摘要请求改造：head 消息原样前置（复用消息对象不重建不改写 content），其后追加摘要指令 **user 角色**消息（含 previous-summary 内容）+ 中段内容 user 消息；head/中段按预算做消息级裁剪（从段尾部丢弃整条消息，不做字符级截断），预算按 head/中段 1:1 分配（默认，允许配置偏离；head 未用尽份额可让渡给中段）。
- [x] `Fix` `CompactConfig` 新增 `compressionPromptBudget` 配置项（哨兵 = -1 未配置）：未配置时运行时按公式计算默认 = 压缩触发水位的一半（水位 = `maxContextTokens × triggerPercent`；`triggerPercent` 取 `config.getTriggerTokenPercent()`，`maxContextTokens` 解析镜像 `PipelineCompactor.resolveMaxContextTokens`，execCtx 为 null 时 fallback 128000）；配置 ≥ 1 时使用显式值。
- [x] `Fix` 更新 `TestLayer3FullSummaryStrategy`：断言摘要请求消息序列 = head 原样 + 摘要指令（user 角色、含 previous-summary）+ 中段（head 消息对象复用、content 未改写——用对象同一性断言而非字节比对）；**断言机制裁定**：既有 `incrementalUpdatePassesPreviousSummary` 的 `<previous-summary>` 断言从 `request.getLastUserPrompt()`（新结构下返回中段消息）改为扫描 `request.getMessages()` 定位摘要指令消息再断言；新增预算裁剪测试（1:1 分配下 head 超支裁剪 head 段尾部整条、中段超支裁剪中段尾部；哨兵 -1 与显式值两路径）。
- [x] `Proof` 端到端：扩展 `TestCompactionInReActLoop`（`layer3SummaryRequestDistinctFromMainLoopRequests`，仅含 Layer3 的 PipelineCompactor 构造，mock IChatService 捕获请求，按摘要指令消息特征区分摘要请求与主循环请求，断言摘要请求 head 前缀存在且对象复用）；`TestForcedStop.endToEndPipelineEscalationThenForcedStop` 适配新预算语义（FixedEstimator 固定 950 在 maxTokens=1000 下预算 400 无法容纳指令+中段 → 按设计降级 Layer2；改用比例 estimator ×500 + maxTokens=10000 使 forced-stop 与预算充足同时成立，断言 Layer3 在 forced stop 中被调用）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 摘要请求消息序列与设计文档 §3.1 结构一致（head 原样 + user 指令含 previous-summary + 中段），head 消息对象复用（同一性断言成立）。
- [x] 预算裁剪为消息级（无字符级截断路径）；`compressionPromptBudget` 哨兵 -1 动态计算与显式值两路径均有测试；1:1 分配裁剪测试覆盖 head/中段两侧。
- [x] 既有 Layer2 降级路径（chatService null / 调用失败）行为不变，`TestLayer3FullSummaryStrategy` 既有用例除 `incrementalUpdatePassesPreviousSummary` 断言机制调整（getLastUserPrompt → 扫描 messages 定位指令消息）外全绿。
- [x] **端到端验证**：真实压缩流程中摘要请求 head 与回写消息 head 为同一批消息对象（测试断言成立）。
- [x] 设计文档已含全部相关裁定（user 角色、预算哨兵语义），实施与设计一致；`No docs-for-ai owner-doc update required`（内部行为，无公共契约变化）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 阴影 token 计价与 COMPACTION 事件

Status: completed
Targets: `.../session/CompactionResult.java`、`.../engine/AgentCompactionCoordinator.java`、`.../engine/AgentEventType.java`、`TestPipelineCompactor.java`、`TestCheckpointTriggersLLMTurnAndCompaction.java`、`TestAgentEventPublisher.java`、`ai-dev/design/nop-ai-agent/02-execution-model.md`

- Item Types: `Fix | Fix | Fix | Proof`

- [x] `Fix` `CompactionResult`（session 包）新增 `shadowedTokenCount` final 字段：新 9 参构造器（PipelineCompactor 唯一构造点经 8 参构造器 delegate 填正确差值），legacy 5/6/8 参构造器默认填 `max(0, tokensBefore − tokensAfter)`；`equals/hashCode/toString` 同步。
- [x] `Fix` `AgentCompactionCoordinator`：COMPACTION checkpoint compactSummary 追加 `shadowed=<n>`；`POST_COMPACT` hook 之后、checkpoint 写入之后经 `hookInvoker.publishEvent` 发射 `COMPACTION` 事件（负载 Map：tokensBefore/tokensAfter/shadowedTokenCount/snapshotId）。
- [x] `Fix` `AgentEventType` 新增 `COMPACTION` 枚举常量，负载约定按既有风格写入 Javadoc（参照 SESSION_RESUMED 的 Map 负载描述模式）。
- [x] `Proof` 测试：`TestPipelineCompactor` 断言 legacy/新构造器 shadowed 默认值与正确值（新增 `legacyConstructorDefaultsShadowedToTokenDifference`，`wiringVerificationOrchestratorInvokesComposedStrategy` 增 shadowed 断言）；`TestCheckpointTriggersLLMTurnAndCompaction.compactionFieldCompleteness` 增 checkpoint compactSummary 含 `shadowed=` 断言；`TestAgentEventPublisher` 新增 `testCompactionEventPublishedWithShadowedCount`（真实压缩路径 COMPACTION 事件存在 + 负载同源 + snapshotId null）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `CompactionResult` 新字段对既有消费方兼容（既有构造器不破坏，legacy 默认值 = max(0, before−after)）。
- [x] COMPACTION checkpoint 与事件均含 shadowed 值，事件发射顺序 = POST_COMPACT 之后、checkpoint 之后。
- [x] **接线验证**：`AgentCompactionCoordinator` 在真实压缩路径发布 COMPACTION 事件（`TestAgentEventPublisher` 断言事件存在）。
- [x] `02-execution-model.md` 事件模型章节已补充 COMPACTION 事件条目（该文档现无独立事件类型表，登记为该章节的事件清单条目）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - Spill 溢出存储

Status: completed
Targets: `.../compact/ISpillStore.java`（新）、`.../compact/InMemorySpillStore.java`（新）、`.../session/AgentSession.java`（宿主字段）、`.../engine/AgentToolDispatcher.java`、`.../tool/ReadSpillExecutor.java`（新）、`TestSpillStore.java`（新）、`TestAiAgentToolsIoC.java`（装配计数 11→12）

- Item Types: `Fix | Fix | Fix | Proof`

- [x] `Fix` `ISpillStore` 接口 + `InMemorySpillStore`（compact 包，put/get/delete）；`AgentSession` 新增 lazy init 宿主字段（参照 InSessionCompactionArchive 先例），默认装配 InMemorySpillStore（无 null-store 分支）。
- [x] `Fix` `AgentToolDispatcher` 成功结果路径接入：长度 > 内联阈值时先 spill（经既有模式 `sessionStore.get(ctx.getSessionId())` 取 session → 取/初始化 store → 全文入 store → 内联 head/tail 预览 + `[SPILL_REF id=...]` 定位提示）；`NON_TRUNCATABLE_TOOLS`（ask-oracle/ask-human）豁免 spill 保持全文内联；**仅 put 失败**（内存/容量异常）降级走既有截断且必须 LOG.warn（工具名 + 原因 + bytes）；**null-session**（session 未入 store，builder 直连测试等）时超阈值结果静默走既有截断、不 warn（未装配场景非故障，与 put 失败严格区分）。
- [x] `Fix` `ReadSpillExecutor`（tool 包，注册进 ai-agent-tools.beans.xml）：按 spillId 经 `AgentToolExecuteContext.getSession()` 取同一 store 实例读回全文；失效/不存在 spillId 返回显式错误（fail-loud）；读回结果与普通工具结果同路径（超阈值再次走 spill/截断判定，SPILL_REF 允许嵌套）。附加：`read-spill.tool.xml`（meta=true，系统级能力始终可见，set-active-tags 先例）。
- [x] `Proof` 测试：`TestSpillStore`（16 用例：put/get/delete/会话生命周期/put 失败降级 warn；dispatcher 写侧端到端——spill/内联/豁免/put 失败降级；null-session 静默截断；read-spill 读回/未知 id/无 session）；`TestAiAgentToolsIoC` 装配计数更新（read-spill 12 个）；`TestToolResultTruncator` 既有用例不破。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 超阈值工具结果：上下文内为预览 + SPILL_REF，全文可从 spill store 经 read-spill 读回（端到端）。
- [x] **端到端验证**：工具结果 → spill 落库 → 预览入消息 → read-spill 读回全文，完整路径单测成立（TestSpillStore.readSpillReturnsFullContent / oversizedToolResultSpilledToSessionStore）。
- [x] **接线验证**：`AgentToolDispatcher` 在运行时实际调用 `ISpillStore`（非仅 import 存在）；读侧经 `AgentToolExecuteContext.getSession()` 与写侧共享同一 store 实例（无双实例漂移——session 宿主单实例断言 + 端到端共享实例验证）。
- [x] **无静默跳过**：put 失败路径降级截断且 LOG.warn（测试断言 putFailureDegradesToTruncation）；read-spill 失效路径返回显式错误（测试断言 readSpillUnknownIdReturnsExplicitError）；无空方法体/continue 绕过；无**未经裁定**的静默路径（null-session 静默截断为已裁定语义，store 默认装配）。
- [x] 豁免工具行为不变（ask-oracle/ask-human 仍全文内联，`TestToolResultTruncator` 不破）。
- [x] `03-extension-matrix.md` 已登记 ISpillStore 接口闭合度（67 接口索引新增行 + 闭合状态标注，闭合 59→60）。
- [x] 设计文档已含全部相关裁定（宿主接线、降级语义、同路径语义、null-session），实施与设计一致；`No docs-for-ai owner-doc update required`（新工具属 agent 内部能力，无公共契约变化）。
- [x] `ai-dev/logs/` 对应日期条目已更新（08-13 Phase 3 条目）。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] 三项决策（KV 前缀保持 / shadowedTokenCount+COMPACTION 事件 / spill）已按设计文档语义落地，无 contract drift
- [x] 行为结果已达成：摘要请求共享 head 前缀；checkpoint 与事件携带 shadowed 值；超阈值工具结果 spill 而非纯截断
- [x] focused verification 已完成（每个 Phase 的测试项全部通过）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步（02-execution-model.md 事件条目、03-extension-matrix.md 闭合度登记；设计文档 active 状态确认）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）COMPACTION 事件发射、spill 调用链在运行时确实连通，（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw test -pl nop-ai-agent -am` 通过（实测 `./mvnw test -pl nop-ai/nop-ai-agent -am` 3460 用例 0 失败）
- [x] checkstyle / 代码规范检查通过（导入分组、命名、4 空格缩进；新文件清零，剩余均为模块 pre-existing 基线）

## Deferred But Adjudicated

### spill 内容持久化进存储层（session-and-storage §16.4 审计对齐）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计文档 §五 已显式声明"首版不承诺存储层全文归档"，spill 内存态 + read-spill fail-loud 已覆盖恢复语义，审计对齐是 §16.4 的后继专题，不影响本计划三机制 baseline 成立。
- Successor Required: `yes`
- Successor Path: 待立（属存储层专题）

### KV 前缀缓存收益运行时实测

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 依赖真实 provider 计价与缓存行为，本计划验证的是机制正确性（前缀共享、事件、spill 语义），成本收益量化属评测体系外工作。
- Successor Required: `no`

## Non-Blocking Follow-ups

- call-agent capabilities 契约（设计文档"后续专题"）——不影响本计划。
- goal 持久轮次驱动（设计文档"后续专题"）——不影响本计划。

## Closure

Status Note: 三 Phase 全部完成并全绿（模块全量 3460 用例）；独立 closure audit APPROVE；无 contract drift；唯一 minor（head 侧正向裁剪测试未交付）记录为 non-blocking follow-up。
Completed: 2026-08-14

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general，非实施者）
- Evidence: task id `ses_001cee61fffegHMdPy66SJvyBx`；日志 `ai-dev/logs/2026/08-13.md`（Phase 1/2/3 条目）
- Findings 摘要: Anti-Hollow 三项全 PASS（COMPACTION 事件发射在真实压缩成功分支、spill 双端共享同一 store 实例、无空方法体/静默跳过）；Phase 1/2/3 Exit Criteria 全部 PASS 且有测试断言（head 对象复用 assertSame、预算 1:1 裁剪、tiny 降级、shadowed 派生默认值、checkpoint shadowed= 字段、豁免/put 失败降级 warn/null-session 静默/read-spill fail-loud）；`./mvnw test -pl nop-ai/nop-ai-agent -am` 3460 用例实测通过；02-execution-model.md 与 03-extension-matrix.md 同步到位。唯一 minor：head 侧 trim 循环在所有测试中未实际执行删除（fixture head 恒为单 turn-group），为测试覆盖不足而非行为缺陷。

Follow-up:

- head 侧预算裁剪的正向测试（构造多 turn-group head 使 head 侧 trim 实际执行并断言删除）——测试覆盖增强，不阻塞任何行为。
- spill 内容持久化进存储层（Deferred 段已登记，successor required）。
- KV 前缀缓存收益运行时实测（Deferred 段已登记，no successor）。