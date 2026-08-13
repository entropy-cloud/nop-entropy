# 2 AI Invariant Loop I4 — 修复执行

> Plan Status: completed
> Mission: nop-ai-invariant-loop
> Work Item: Cycle 1 / I4. 修复执行
> Last Reviewed: 2026-08-12
> Source: `ai-dev/backlog/nop-ai-invariant-loop-roadmap.md` §I4；`ai-dev/audits/nop-ai-invariants/adjudication-2026-08.md`（I3 裁决表，唯一修复契约面）
> Related: `ai-dev/plans/2026-08-12-1411-1-ai-invariant-i3-adjudication.md`（I3，前置）；`2026-08-12-1411-3-ai-invariant-i5-full-verification.md`（I5，消费本 plan 修复产物）

## Purpose

按 I3 裁决表执行 Cycle 1 全部 fix-I4 决策：门禁覆盖缺口补齐（33 个 Default* 类补 `@SecureDefault`、timeout 声明类修复、ToolExecutor 安全边界接线类修复、门禁⑤精度修复）+ 类别清扫（修任一族必穷举该族全部实例）+ test-first（每个修复配回归测试）+ known-gaps 条目移除（移除后门禁恢复实判，自校验）。交付后 gate-gaps 仅剩裁决为 watch/not-applicable 的条目。

## Current Baseline

（以下事实均于 2026-08-12 live 核实；I4 开工前须确认 I3 裁决表已完成——见下方「前置条件」）

- **前置条件（尚未落地，开工时 fail-fast 校验）**：I3 裁决表 `ai-dev/audits/nop-ai-invariants/adjudication-2026-08.md` 44/44 零悬挂，且**每条 fix-I4 决策含机制级 I4 动作清单**（文件/机制/deadline 来源/测试放置/门禁测试扩展要求）——无「建议…或…」二选一残留。裁决表不存在、未定稿、或含二义条目 → 停并回 I3 补裁，不自行重裁、不自行发明机制。
- 声明机制就位：`@SecureDefault` 注解已存在于 `nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/secure/SecureDefault.java`（marker 纯声明，`@Target(TYPE)` + `@Retention(RUNTIME)`）；门禁①测试 `TestInvariantGate1SecureDefault`（agent 31 类）+ `TestInvariantGate1SecureDefaultShell`（shell 2 类）断言注解存在性 + 表完备性。
- 门禁②③测试：`TestInvariantGate2OrchestrationTimeout`（**TABLE 硬编码 14 条，verdict 字段如 `missing` 为静态值；判定逻辑 = verdict missing 且未在清单 → violation**；`deriveEntryIds()` 机械派生只覆盖 IAgentEngine 公共方法 + engine 包 execute/executeAllowedCalls + CallAgentExecutor——不覆盖 team/flow、gateway、plan/runtime 包）；`TestInvariantGate3EntryPointCleanup`（8 行成对面，零缺口）。
- 门禁④⑤（mjs）：`check-ai-tool-executor-boundary.mjs`（30 实例内嵌表）；`check-fix-commit-diff.mjs`（`--grep=fix(nop-ai)` 匹配 subject+body——R-5-1 精度缺陷本体，subject-only 修复方案见 Phase 5）。
- known-gaps 现状：gate-1 33（missing-declaration）+ gate-2 5（4 N/A + 1 missing）+ gate-4 1（N/A）；gate-3 空。移除规则（catalog §3.5）：清单移除 = I4 修复后，移除后门禁恢复实判（未真修复即 fail）。**I3 开工后清单将变为 39 + I3 新增登记数**（R-2-1/R-2-3 若裁决补表 → +2 等），I4 开工基线以 live 清单为准。
- 关键修复点（live 行号）：
  - `SingleTurnExecutor.java:29` execute 签名 / `:41` `chatService.call(request, null)`（无 timeout 机制引用）。
  - `MemberFanOutDispatcher.java:305` `agentEngine.execute(request)`（**dispatch 为 static 方法，agentEngine 是参数，全类无 timeout/config 字段**）；`TeamTaskFlowOrchestrator.java:513` `executeAsync`（全类无 timeout 机制、无 config 字段）——**R-2-3 的 deadline 来源不存在现成配置，必须由 I3 裁决表给出机制级决策**。
  - `SsrfGuardDnsResolver.java:34`（实现 IDnsResolver，零接线）。**事实核查（I4 开工必核，供 R-4-1 接线判断）**：nop-ai 默认 HTTP client 为 `JdkHttpClient`（`nop-http-client-jdk` 模块），**不消费 `HttpClientConfig.dnsResolver`**；支持 dnsResolver 的 `ApacheHttpClientHelper`（`nop-http-client-apache`）**不是任何 nop-ai 模块的依赖**——「装配到默认 client」按现状会静默 no-op，接线点选择须以 I3 裁决（基于该事实）为准。
  - `ChannelMessageServiceImpl.java:208` `dispatchInbound`（mode-1 fanOutToListeners :230-233 / mode-2 messageService.send）。
  - **R-2-1 测试基建约束（事实核查，影响裁决分支）**：`InvariantGateSupport`（nop-ai-agent 测试基建）硬编码 `src/main/java/io/nop/ai/agent` 包前缀读取源文件（`InvariantGateSupport.java:35-37`）——若 R-2-1 裁决「补入 agent 模块 gate-2 测试的 TABLE + 派生逻辑」，读取 `io/nop/ai/gateway/channel/...` 源码会 `IllegalStateException` → 门禁红；因此 R-2-1 的补表测试必须走「gateway 模块新建独立 gate 测试类」或「mjs 形式扩展」分支，**不得走 agent 模块 TABLE 扩展分支**（除非同时重构 InvariantGateSupport 的包前缀假设——超出本 plan scope，须回 I3 裁决）。
  - plan/runtime 包（`PlanExecutor.java:108` / `TaskRunner.java:18` / `PlanScheduler` / `PlanRunner` / `StagnationDetector` 等，生产零接线——接线状态与处置以 I3 裁决为准）。
- 测试基建：`nop-ai-agent/src/test/java/io/nop/ai/agent/gate/` 已有门禁测试族；`nop-ai-toolkit` SSRF 测试（`TestSsrfAddressGuard`）先例存在；门禁⑤ self-test 用 `_tmp` 构造 git 仓库先例（I1 落地）。

## Goals

- 裁决表全部 fix-I4 决策落地，每个修复配 committed 回归测试（test-first），全模块构建绿。
- known-gaps 清单中已修复条目移除，门禁恢复实判后仍全绿（自校验）；清单剩余 = 仅 watch/not-applicable 裁决条目。
- 类别清扫执行：修任一 Default* 类必 grep 全部 33 类；修任一编排入口 timeout 必穷举全部入口；修任一 ToolExecutor 安全边界必穷举全部 30 实例。
- catalog §3.2 排除理由更正（R-2-3 owner-doc drift）与 gate 表扩展（I3 裁决的补表项）同步落地。
- I5 验证前置条件达成：门禁零命中（known-gaps 精确一致）。

## Non-Goals

- **不裁决**（I3 已关闭；I4 中发现裁决表未覆盖事项**或裁决条目含二义/机制缺失** → 停止并回 I3 补裁，不自行裁定、不自行发明机制）。
- **不运行 I5 式正式全量验证**（I5 职责）；Phase 6 的 `./mvnw test -pl nop-ai -am -T 1C` 仅为修复收尾自验（门禁绿确认），不做 I5 的 drift 核对/CI 验证。
- **不收口/稳态判定**（I6）。
- 不修 I3 裁决为 watch/not-applicable 的条目（如 AskOracleExecutor 保持 N/A 登记）。
- 不扩展门禁判定标准语义（判定标准属 I1 契约；修复必须按现有标准达成声明）。

## Scope

### In Scope

- 裁决表全部 fix-I4 动作（按 Phase 分组执行）。
- 对应 known-gaps 条目移除 + 门禁复判验证。
- 类别清扫 + test-first 回归测试。
- catalog §3.2 更正 / gate 表扩展 / 门禁⑤修复（mjs 改动）。

### Out Of Scope

- 裁决（I3）、全量验证（I5）、收口（I6）。
- watch/not-applicable 条目修复。

## Execution Plan

### Phase 1 - 裁决表消费与开工核对

Status: completed
Targets: `ai-dev/audits/nop-ai-invariants/adjudication-2026-08.md`、`gate-gaps.yaml`

- Item Types: `Fix | Proof`

- [x] `Fix` 前置校验（fail-fast）：I3 裁决表存在且 44/44 零悬挂；**逐条核对每条 fix-I4 决策含机制级动作清单**（文件/机制/deadline 来源/测试放置/门禁测试扩展），发现「建议…或…」二义条目即停并回 I3 补裁；汇总 fix-I4 动作清单（按族分组）并映射到本 plan 各 Phase；裁决表不存在/未定稿则停，不得自行重裁。
- [x] `Proof` 核对裁决表中标注「人工确认」的公共 API 触碰项（若有）：未获确认前跳过对应动作并记录，不得静默执行。
- [x] `Proof` 事实核查（影响接线类修复的裁决前提）：确认 nop-ai 默认 HTTP client 实现（`JdkHttpClient`）是否消费 `HttpClientConfig.dnsResolver`（预期：不消费，`nop-http-client-jdk` 零 dnsResolver 引用）——**若与 I3 裁决表接线假设矛盾，停止对应动作并回 I3 复裁**；核查 R-2-3 两入口（`MemberFanOutDispatcher.dispatch` / `TeamTaskFlowOrchestrator.executeAsync`）是否有裁决表声明的 deadline 来源可用；核查 R-2-1 裁决的测试放置分支是否受 `InvariantGateSupport` 包前缀约束影响（若裁决走 agent 模块 TABLE 扩展分支 → 违反 Constraint，回 I3 复裁）。
- [x] `Proof` 基线记录：开工前 `./mvnw compile -pl nop-ai -am -T 1C` + 门禁全绿基线（**live 清单**（39 + I3 新增登记数）下全绿，以实际清单为准），记录于 daily log。

Exit Criteria:

- [x] fix-I4 动作清单汇总完成（每族动作数 == 裁决表 fix-I4 计数）
- [x] 人工确认项状态明确（确认/挂起），挂起项有显式记录
- [x] 开工基线绿（compile + 门禁），记录于 daily log
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 门禁①修复：33 个 Default* 类补 @SecureDefault

Status: completed
Targets: `nop-ai/*/src/main/**` 33 个 Default* 类、`gate-gaps.yaml`

- Item Types: `Fix | Proof`

- [x] `Fix` 33/33 类补 `@SecureDefault` 注解（`io.nop.ai.api.secure` import + 类级注解；IoC 注入候选与非注入候选统一补——门禁①以类别属性为判定，不区分注入面）。**类别清扫**：补注解前用 §4 复现命令 grep 全部 Default* 类，确认 33 个清单无遗漏、无新增（新增未登记类 → 停止并记录，交 I3 复裁）。
- [x] `Fix` 每补一个/一批注解后运行门禁①测试（`TestInvariantGate1SecureDefault` + `TestInvariantGate1SecureDefaultShell`），确认声明检查通过。
- [x] `Fix` known-gaps 移除：33 条 gate-1 条目从 `gate-gaps.yaml` 移除（修复后移除，自校验：移除后门禁对 33 类实判——有注解 → 绿）。
- [x] `Proof` 反例验证门禁拦截力仍在：临时去掉任一已补注解类 → 门禁红 → 恢复 → 绿（棘轮验证，证明移除清单后门禁真在实判）。

Exit Criteria:

- [x] 33/33 类带 `@SecureDefault`，门禁①（agent+shell）全绿
- [x] gate-gaps.yaml gate-1 族零条目，门禁恢复实判后绿（自校验通过）
- [x] 反例棘轮验证记录（红 → 回滚 → 绿）
- [x] 类别清扫记录：grep 全量 33 类无遗漏无新增（或差异已显式记录）
- [x] No owner-doc update required（注解为声明性 marker，不改行为语义，catalog 无需同步）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 门禁②修复：timeout 声明补齐

Status: completed
Targets: `SingleTurnExecutor.java`、team-flow fan-out（`MemberFanOutDispatcher.java` / `TeamTaskFlowOrchestrator.java`）、gateway 派发入口（按 I3 裁决）、`gate-gaps.yaml`、catalog §3.2

- Item Types: `Fix | Proof`

- [x] `Fix` `SingleTurnExecutor.execute` 修复（**按 I3 裁决表指定的机制执行，不得自行在「复用配置/复用 callChatWithTimeout」间二选一**；若 I3 裁为 not-applicable-confirmed 则跳过并在 daily log 记录）。**配行为级回归测试**（不允许「声明可观测性」代替行为验证）：mock `IChatService` 挂起 → 断言 execute 在超时后异常完成/取消路径生效（`chatService.call` 是阻塞式默认方法，行为测试可行）。
- [x] `Fix` **门禁②测试同步更新（B1，自校验机制必需）**：`TestInvariantGate2OrchestrationTimeout` 的 TABLE 中 `SingleTurnExecutor.execute` 条目 verdict 从 `missing` 改为 `declared` + 指定 evidenceFile/marker（与修复后的实际机制一致）；`deriveEntryIds()` 若裁决补表（R-2-1/R-2-3）须扩展机械派生逻辑（当前只覆盖 IAgentEngine + engine 包 + CallAgentExecutor）——**不更新测试则「移除清单 → 门禁红」自校验断裂或表完备性必红**；同步修正测试头注释「13 入口」与 javadoc「9/14 declared」计数（若与实际不符）。
- [x] `Fix` R-2-3 team-flow fan-out 修复（按 I3 裁决的**机制级决策**——deadline 来源、API 触碰点、测试放置、取消语义均由裁决表给出，I4 不得自行发明）：`MemberFanOutDispatcher.java:305` 的 `agentEngine.execute` 补裁决表指定的 timeout 机制；`TeamTaskFlowOrchestrator.executeAsync` 补 timeout 声明（**marker 用裁决表指定的具体机制标记**——注意 `:384` `awaitTermination(2, TimeUnit.SECONDS)` 是关闭等待非编排 timeout，若 marker 用宽泛词 `TimeUnit` 会误判 declared，须用 `orTimeout`/`TimeoutException` 级精确标记）；超时后子会话处置（取消/终止语义按裁决表，AUDIT-14-01 语义对齐）。配回归测试：member 挂起 → 超时 → 处置生效 + 团队任务不无限等待。
- [x] `Fix` R-2-1 gateway 派发入口修复（若 I3 裁决补表 + 修复）：`dispatchInbound` 补 timeout/at-least-once 声明（按裁决表机制）；gate-2 表扩展（catalog §3.2 新增条目 + 测试放置**按 I3 裁决的可行分支**——因 `InvariantGateSupport` 硬编码 agent 包前缀，agent 模块 TABLE 扩展分支不可行，须走 gateway 模块新建 gate 测试类或 mjs 扩展）。配回归测试。
- [x] `Fix` R-2-2 plan/runtime（按 I3 裁决）：若裁决「属 INV-2 入口」→ 补 timeout 契约 + 表登记 + 门禁测试扩展；若裁决「watch：生产零接线 = 非入口」→ 在 catalog 记录接线状态说明（接线时须补 timeout 契约），本 phase 不动代码。
- [x] `Fix` catalog §3.2 排除理由更正（R-2-3 owner-doc drift）：删除/改写「team-flow 内部方法——不启动 agent 执行路径」排除理由，改为 live 事实（fan-out 启动 agent 执行路径）；若 gate-2 表扩展则同步判定标准原文。
- [x] `Fix` known-gaps 移除：gate-2 族已修复条目移除（SingleTurnExecutor 若修复；R-2-1/R-2-3 补表登记条目在修复后移除）；not-applicable-confirmed 4 条保持登记（I3 已确认）。**移除顺序 = 先更新门禁测试 TABLE/派生逻辑使实判通过，再移除清单条目**（避免中间态红）。
- [x] `Proof` 类别清扫：修任一时 timeout 入口必穷举全部编排入口（catalog §3.2 表 14 + 表扩展条目），确认无遗漏同类缺口（I4 不得只修 red-list 点名实例）。

Exit Criteria:

- [x] 裁决表 gate-2 相关 fix-I4 动作全部落地（或按裁决跳过并记录）
- [x] 每个修复有回归测试：超时语义（挂起 → 超时 → 取消/失败）可观测
- [x] gate-2 测试全绿；表扩展条目（若裁决补表）被门禁穷举覆盖（注意 `InvariantGateSupport` 包前缀约束——gateway 面走 gateway 模块测试或 mjs 扩展）
- [x] catalog §3.2 排除理由已更正为 live 事实（无与代码矛盾的排除理由）
- [x] known-gaps gate-2 族 = 仅裁决为 not-applicable-confirmed 的条目
- [x] 类别清扫记录（入口穷举核对无遗漏）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 门禁④修复：ToolExecutor 安全边界

Status: completed
Targets: `SsrfGuardDnsResolver.java`、HTTP client 装配面（按 I3 裁决的接线点）、`gate-gaps.yaml`

- Item Types: `Fix | Proof`

- [x] `Fix` R-4-1 修复（按 I3 裁决，**接线点以裁决表为准**——默认 `JdkHttpClient` 不消费 dnsResolver 的事实已由 I3 裁决时考虑）：若裁 (a) 接线——装配到裁决表指定的实际消费 dnsResolver 的 client 实现（如换用/装配 ApacheHttpClient 族或扩展 JDK client 支持点，按裁决表机制，不得假设默认 client 消费），配接线回归测试（DNS 解析经 resolver、fail-closed 行为）；若裁 (b) not-applicable——记录 dead-code 裁定到 catalog/裁决表并注明不接线理由（host 级 validateHost 已接线覆盖），本 phase 不动代码。
- [x] `Proof` 接线验证（若裁 (a)）：运行时调用链确认 HTTP 调用实际使用装配的 resolver（非仅 bean 存在）；接线测试断言 resolver 方法被调用（计数器/标志位或 mock verify）——**若接线测试发现 resolver 不被消费（no-op），停止并回 I3 复裁**，不得静默留下假接线。
- [x] `Proof` 类别清扫：ToolExecutor 面 30 实例（catalog §3.3）grep 反查，确认安全边界声明无新增缺口（修复 R-4-1 后 INV-4 族全绿）。
- [x] `Fix` gate-gaps：gate-4 族 `AskOracleExecutor` not-applicable-confirmed 条目保持；**SsrfGuardDnsResolver 非 ToolExecutor 实例（IDnsResolver），不在门禁④表（catalog §3.3），无需清单变更**——若 R-4-1 裁 (a) 接线完成，仅记录接线证据。

Exit Criteria:

- [x] R-4-1 按 I3 裁决落地（接线并有运行时调用证据 / 或 dead-code 裁定记录）
- [x] **接线验证**：装配的 resolver 在运行时被 HTTP 调用链实际调用（测试断言或代码追踪）
- [x] 门禁④ `pnpm check:ai-tool-boundary` 全绿（30 实例声明成立）
- [x] 类别清扫记录（catalog §3.3 全表核对无遗漏）
- [x] No owner-doc update required（catalog §3.3 如因接线无变更则无需同步；若 R-4-1 裁定影响目录「检测方法」列，由 Phase 6 统一同步）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 门禁⑤修复：fix-commit 匹配精度

Status: completed
Targets: `ai-dev/tools/check-fix-commit-diff.mjs`、`ai-dev/tools/package.json`

- Item Types: `Fix | Proof`

- [x] `Fix` R-5-1 修复（按 I3 裁决的修复方式，**首选 `--format='%s'` 前缀判断**——`--grep='^fix(nop-ai)'` 锚定行首，body 行以该字样开头仍会误匹配）：将 `--grep=fix(nop-ai)` 的 subject+body 匹配收窄为 subject-only 匹配，消除 body 提及字样的误报。
- [x] `Fix` self-test 增补反例：构造「subject 非 fix(nop-ai) 但 body 含字样（含 body 首行即以该字样开头）」的 commit → **必须被排除出候选集**（subject-only 过滤后不纳入扫描）；**同步更新现有 self-test 的 commit 计数断言**（现有断言如 `commits.length === 4` 需随新增 fixture 调整）；既有正反例保持通过。
- [x] `Proof` 修复后实跑：`pnpm check:fix-commit-diff` 对本仓库 `--since 2026-07-31` 运行，确认仍零违规且不再扫入 body 匹配 commit（`5ebad065e` / `c1362dc77` 两 commit 不再被误报为候选）。

Exit Criteria:

- [x] 门禁⑤ subject-only 匹配生效，body 匹配反例 self-test 通过
- [x] 实跑零违规且无 body 误报（记录扫描结果）
- [x] `pnpm check:ai-invariants` 全绿（④⑤聚合）
- [x] No owner-doc update required（门禁⑤为 ai-dev 工具改动，catalog 检测方法列同步归 Phase 6）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - 类别清扫复核 + 全绿收尾

Status: completed
Targets: `gate-gaps.yaml`、五族门禁

- Item Types: `Proof`

- [x] `Proof` 五族类别清扫终核：gate-1（33 类注解全量）、gate-2（入口 timeout 穷举）、gate-3（8 行对称无回归）、gate-4（30 实例声明）、gate-5（subject 匹配）——逐族复核无遗漏、无新缺口。
- [x] `Proof` known-gaps 终态核对：清单 = 仅裁决 watch/not-applicable 条目（预期 gate-2 4 条 N/A + gate-4 1 条 N/A）；零 missing-declaration 残留（除非 I3 裁决保留）。
- [x] `Proof` 局部全量验证：`./mvnw test -pl nop-ai -am -T 1C`（含门禁①②③）+ `pnpm check:ai-invariants`（④⑤）全绿；记录结果供 I5 消费。
- [x] `Fix` catalog 同步：catalog §3.2 更正、gate 表扩展（若裁决）、R-4-1 接线/裁定记录、门禁⑤修复说明——目录「检测方法」列与实际门禁产物一致。
- [x] `Proof` `node ai-dev/tools/check-doc-links.mjs --strict` 运行：零新增断链（20 个 pre-existing 错误为基线，非本 plan 引入）。

Exit Criteria:

- [x] 五族类别清扫终核记录（零遗漏零新增缺口）
- [x] known-gaps 终态 = 仅 watch/not-applicable-confirmed 条目，零 missing-declaration 残留
- [x] `./mvnw test -pl nop-ai -am -T 1C` + `pnpm check:ai-invariants` 全绿（结果记录）
- [x] catalog 与门禁产物一致（双向引用核对）；doc-links 零新增断链
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 裁决表全部 fix-I4 决策落地（或按裁决跳过且有记录），无遗漏
- [x] 每个修复有 committed 回归测试（test-first），验证的是正确行为而非仅无异常
- [x] known-gaps 清单终态 = 仅 watch/N/A 条目；移除后门禁恢复实判且全绿（自校验）
- [x] 类别清扫执行（修一族穷举一族），无只修点名实例的情况
- [x] catalog §3.2 排除理由更正 + gate 表扩展（若裁决）已同步 live 事实
- [x] 不存在被静默降级的 in-scope 项（修复未完成的条目不得进 follow-up）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）每个修复在运行时被实际调用（接线验证），（b）无空方法体/静默跳过/no-op 作为正常实现，（c）known-gaps 移除是真修复（门禁实判绿）而非删除条目
- [x] **端到端验证**：从「修复后的代码路径」到「门禁绿」的完整链路已验证（如 SingleTurnExecutor 超时 → 测试断言超时行为 → 门禁②绿）
- [x] **接线验证**：R-4-1（若接线）的运行时调用链已验证（裁决 not-applicable-confirmed，dead-code 记录 + 触发条件；非接线分支不适用）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（closure 时执行）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai --severity high` 退出码 0 或记录 pre-existing 基线（guide rule 5b）——**注意**：2 条 pre-existing high finding（`PlanReplanner.java:272` / `NoOpProviderFailoverQueue.java:34`）中 `PlanReplanner.java:272` 属 R-2-2 目标面（plan/runtime 包），若 I3 裁决对 R-2-2 动代码，该基线须在 closure 时重跑后重新记录（R-2-2 裁决 = watch，未动代码 → 基线重跑后仍为同 2 条，记录于 Closure Evidence）
- [x] `./mvnw compile -pl nop-ai -am -T 1C`
- [x] `./mvnw test -pl nop-ai -am -T 1C`
- [x] checkstyle / 代码规范检查通过（`check-import-order.mjs` 等既有门禁不回归；import 分组 io.nop.* → 第三方 → java.*）

## Deferred But Adjudicated

None（本 plan 全部动作来自 I3 裁决表；执行中发现裁决表未覆盖事项 → 停止回 I3，不在此处延期）

## Non-Blocking Follow-ups

- I5 全量验证（`2026-08-12-1411-3`）消费本 plan 修复产物。
- 门禁运行耗时优化（ArchUnit 扫描范围）——non-blocking optimization candidate（I1 遗留）。

## Closure

Status Note: 裁决表 37 条 fix-I4 决策全部落地（5 个 commit），known-gaps 终态 = 仅 5 条 not-applicable 条目（gate-2 4 条 + gate-4 1 条），零 missing-declaration 残留；五族门禁全绿（自校验通过）；独立 closure audit APPROVED（2 Minor 已收口）。
Completed: 2026-08-12

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general，task `ses_00af483cbffeRgJDmN6YFvYDJl`，review-only 零文件改动）
- Evidence:
  - Phase 1-6 Exit Criteria 逐条 PASS：Phase 2 `find` = 33 行 == catalog §3.1 == gate-1 清单空 + `@SecureDefault` 33/33（抽查 DefaultAgentEngine/DefaultCommandChecker/DefaultToolExecutorProvider import + 注解）；Phase 3 SingleTurnExecutor:128-149 `get(llmTimeoutMs, TimeUnit)` + TimeoutException cancel、MemberFanOutDispatcher:326 `.orTimeout` + cancelSession、TeamTaskFlowOrchestrator:618 `.orTimeout`（区别于 :443 close 路径 awaitTermination）、ChannelMessageServiceImpl:348/:373 双 mode `orTimeout`、DefaultAgentEngineConfig DEFAULT_MEMBER_EXEC_TIMEOUT_MS=120_000 + setter 拒绝非正数、gate-2 TABLE verdict=declared、catalog §3.2 17 行；Phase 4 R-4-1 dead-code 记录 catalog:231 + gate-4 30 实例 PASS；Phase 5 collectFixCommits 无 `--grep` + subject.startsWith 唯一过滤、self-test 含 body 反例全 PASS、实跑 7 commit 零违规且 5ebad065e/c1362dc77 不在候选；Phase 6 §3.3 #30 内存/会话 + §INV-5 R-5-1 记录 + doc-links 20 pre-existing 零新增 + daily log 四条目。
  - Closure Gates 逐条 PASS（审计时 gate 7 自身 = 本次 audit；gate 12 import-order：177 pre-existing error 与 I4 触碰 52 文件零重叠 → 零回归；审计 Minor-1 两处 slf4j import 顺序 → 已修复 commit e9b2bee76 并复跑 gate-2 3/3 + fan-out 2/2 绿）。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-08-12-1411-2-ai-invariant-i4-fix-execution.md --strict` 退出码 0（审计时 + closure 时复跑）。
  - Anti-Hollow：调用链追踪——SingleTurnExecutor.execute → callWithTimeout → supplyAsync(timeoutExecutor) + get(timeout)（AgentExecutorResolver:210-211 接线，非 stub）；fan-out :316-349 真 orTimeout + 超时 cancelSession + 诚实 engineFailed（无 continue/空 catch）；orchestrator :572-629 exceptionally 诚实 failed result；gateway fanOutToListeners :368-381 runAsync().orTimeout + fail-loud LOG.error。端到端：TestSingleTurnExecutor.testHangingChatServiceTimesOutAndFails（挂起 → 超时 → status=failed + elapsed<5s）→ 门禁②绿；TestMemberFanOutDispatchTimeout（挂起 → 有界返回 + cancelSession 恰 1 次 + CLAIMED 保持）。
  - scan-hollow：exit 1 = 2 条 pre-existing high 基线（PlanReplanner:272 / NoOpProviderFailoverQueue:34）——R-2-2 裁决 watch 未动代码，基线重跑后无变化，符合 gate 的「记录 pre-existing 基线」条款。
  - 构建：`./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（03:21，门禁 ①②③ 17 tests 全绿 + 回归测试 11/11）；`pnpm check:ai-invariants`（④⑤）exit 0；`./mvnw compile -pl nop-ai -am -T 1C` 随 test reactor 编译通过。
  - Deferred 分类检查：Deferred But Adjudicated = None；Non-Blocking Follow-ups 仅 I5 验证 + 门禁耗时优化（non-blocking）；无 in-scope live defect 被降级。

Follow-up:

- I5 全量验证（`2026-08-12-1411-3`）消费本 plan 修复产物。
- 门禁运行耗时优化（ArchUnit 扫描范围）——non-blocking optimization candidate（I1 遗留）。

## Draft Review Records

- Round 1（fresh session `ses_00b64259cffeEIRTEMYkw8OPJp`）：4 Blocker + 4 Major + 3 Minor，全部修订（门禁②测试 TABLE/派生逻辑更新、R-2-3 deadline 机制级决策要求、R-4-1 默认 client 事实、行为级测试强制、裁决含糊 fail-fast、前置条件区、known-gaps 计数、R-5-1 subject-only、scan-hollow 基线）。
- Round 2（fresh session `ses_00b559bb4ffeJTvp51vOH25FhZ`）：1 Major（F1 InvariantGateSupport 包前缀约束）+ 8 Minor，全部修订（F2-F9：机制二义文本清理、marker 精确性、self-test 断言、doc-links、R-2-3 取消语义条件化、指代澄清、catalog 前缀、rule 17）。
- Round 3（fresh session `ses_00b45560fffeC0xh9fHWPD2Z2B`）：verdict 可执行（零 Blocker 零 Major；2 Minor：Non-Goals 措辞 + commit 窗口前提——均已修订/注明）。共识达成 → Plan Status: active。
