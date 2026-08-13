# 1 AI Invariant Loop — P2 引擎拆分残留代码卫生（AR-5/AR-6/AR-7）

> Plan Status: completed
> Mission: nop-ai-invariant-loop
> Work Item: Cycle 2 / P2 — 引擎拆分残留（import 重复 AR-5 + logger 挂名 AR-6 + 超时 setter 校验不对称 AR-7）
> Last Reviewed: 2026-08-12
> Source: `ai-dev/audits/2026-08-12-1119-open-audit-nop-ai-invariant-loop.md` P2 [AR-5]/[AR-6]/[AR-7]；roadmap `ai-dev/backlog/nop-ai-invariant-loop-roadmap.md` `## Follow-up Backlog`
> Related: `2026-08-12-2050-1-ai-invariant-resolver-contract-fix.md`（同批拆分残留 AR-1 修复）；`2026-08-12-1411-2-ai-invariant-i4-fix-execution.md`（MA4.2-05 拆分本体）
> Review: 两轮独立子 agent 对抗性审查（fresh sessions ses_00975fc6cffe / ses_0096ea224ffe）——R1 三 Blocker（import-order 归零不可达 / 12 文件重复 import 扩 scope / 5 耦合测试类 appender 目标）+ M1-M3 全修；R2 复核 Blocker 清零、Major 全修，共识 = executable（3 Minor 措辞项已吸收）→ draft → active

## Purpose

收口 MA4.2-05 引擎拆分（commit 2f3251589）遗留的三类代码卫生残留：nop-ai-agent 全部重复 import 块（AR-5 为已知实例，live 反查共 12 文件）、8 个拆分类 Logger 挂名 `DefaultAgentEngine`（AR-6）、`DefaultAgentEngineConfig` 超时 setter 校验不对称（AR-7，含 catalog 契约裁定）。

## Current Baseline

（2026-08-12 23:11 live 核实）

- **AR-5 实锤 + 规模**：`AgentCallDelegate.java` 含 3 份相同的 import 块（`org.slf4j.Logger/LoggerFactory` 与 `java.util.*` 各重复）；**live 机械反查（`grep -o '^import .*;' <file> | sort | uniq -d`）确认 nop-ai-agent 共 12 个文件含重复 import**：`AgentCallDelegate`(7)/`DefaultAgentEngine`(16)/`AgentSessionLifecycle`(10)/`AgentToolPlanResolver`(7)/`SessionLockRenewal`(6)/`AgentPromptAssembly`(5)/`AgentSecurityConsultation`(5)/`AgentSessionSupport`(5)/`AgentLoopGuard`(4)/`AgentTeamBinder`(4)/`AgentStartupWarnings`(2)/`ReActAgentExecutor`(1)——同批 copy-paste 残留的真实规模。
- **check-import-order 工具事实**：`node ai-dev/tools/check-import-order.mjs --module nop-ai` 实测 **176 errors / 168 files / exit 1**（pre-existing 基线，遍布 nop-ai-api/dao/maven/service/shell/skills 及 `_gen/` 生成文件）；该工具只查分组顺序、**不检测重复 import**；且其 `-g '!*/_gen/*'` 排除对深层路径静默失效（工具 bug）。因此「模块级归零」不可达且非本 plan 目标；本 plan 对**受处理文件**要求该工具零报错 + 重复 import 反查归零。
- **AR-6 实锤**：8 个拆分类（`AgentExecutorResolver:34`/`AgentCallDelegate:49`/`AgentSessionLifecycle:66`/`AgentTeamBinder:39`/`AgentStartupWarnings:49`/`SessionLockRenewal:34`/`AgentSessionSupport:39`/`DefaultAgentEngineConfig:102`）均为 `getLogger(DefaultAgentEngine.class)`。`TaskDispatchCoordinator.java:37` 为 `getLogger(TeamTaskSchedulerDaemon.class)`（audit live 复核确认为**误报项，不处理**）。
- **AR-6 测试耦合（live 核实）**：5 个测试类把 logback `ListAppender` 挂到 `LoggerFactory.getLogger(DefaultAgentEngine.class)` 并断言 `AgentStartupWarnings` 的告警（`warnIfInsecureDefaults` :67-138 / `warnIfNoOpUsageRecorder` :144-147 经 `AgentStartupWarnings.LOG` 发射）——现因 LOG 挂名 DefaultAgentEngine 才被 appender 收到；**logger 改挂 `AgentStartupWarnings.class` 后这些测试会红**，必须同步改测试的 appender 目标：`TestSecureByDefault:91`/`TestLayer23SecureDefaults:116`/`TestSecureDefaultsInfoAwareness:67`/`TestAuditLoggerDefault:105`/`TestUsageRecorderWiring:120,152`。
- **AR-7 实锤**：`DefaultAgentEngineConfig.java`——`setCallAgentTimeoutMs`（:943-947）与 `setMemberExecTimeoutMs`（:965-969）拒绝 `<= 0`；`setLlmTimeoutMs`（:950）与 `setToolTimeoutMs`（:952）无校验。catalog §2 INV-2（:43）声称「均非 0，setter 拒绝非正数」——live 仅 2/4，owner-doc 漂移。**0 值 live 语义（live 核实，比 audit 描述更分裂）**：`LlmCallCoordinator.callChatWithTimeout:606` `if (llmTimeoutMs <= 0 || timeoutExecutor == null) return chatService.call(...)` = 0 禁用超时（ReAct 生产主路径）；`SingleTurnExecutor.callWithTimeout:132` 无守卫 = 0 立时 `TimeoutException`；`AgentToolDispatcher:230-233` `>0` 守卫 = 0 静默禁用 tool 超时——同一配置值在三条路径上语义相反/静默逃生。
- **调用面事实**：`DefaultAgentEngine.java:279-280` Builder → setter（默认 120s/300s 正）；`:503-509` 透传 getter；`AgentExecutorResolver:200-201` 读取。全仓 `setLlmTimeoutMs/setToolTimeoutMs` 除 `DefaultAgentEngine`(:279-280/:503/:507) 与 `DefaultAgentEngineConfig` 外**零调用点**（无测试、无 beans.xml 传 0）。`AgentToolDispatcher`/`LlmCallCoordinator`/`SingleTurnExecutor` 的守卫可由直接构造绕过 setter 到达（如 `TestEngineConfigAndHelpers` 直接构造）——setter 拒绝后逃生口仍存在于直接构造路径，属防御性编码，登记 watch。
- 既有测试：`TestEngineConfigAndHelpers.java` 已断言三个默认值（:94-96）与 `configRejectsNonPositiveCallAgentTimeout`（:112-114）——AR-7 新增测试的自然落点。
- 授权基线：mission json「P2 自动修复授权为 plan 级裁定（I3 裁定 2026-08-12）」——本 plan 即该 plan 级裁定，三类项均为已确认 live defect / owner-doc drift，归类 `Fix`/`Decision`。

## Goals

- AR-5：nop-ai-agent 全部 12 个含重复 import 的文件去重（AgentCallDelegate 为首要实例）；受处理文件 `node ai-dev/tools/check-import-order.mjs` 零报错 + 全模块重复 import 反查归零。
- AR-6：8 个拆分类 Logger 改为 `getLogger(X.class)`；5 个耦合测试类 appender 目标同步到实际发射类（`AgentStartupWarnings.class` 等）；`TaskDispatchCoordinator` 保持不动；全模块 `getLogger(DefaultAgentEngine.class)` 仅剩 `DefaultAgentEngine` 自身。
- AR-7：四个超时 setter 统一拒绝 `<= 0`（裁定见 Phase 3），catalog INV-2 声明与 live 一致；参数化测试覆盖四个 setter。
- 明确记录：AR-6 logger 修正对 logback 测试可观测（非行为零变更）；AR-7 对 0 值从静默接受/语义分裂改为显式拒绝。

## Non-Goals

- **不处理** AR-8（gate-1 fixture 位置，plan `2026-08-12-2311-2`）、AR-9（gateway dispatchTimeoutMs 接线，plan `2026-08-12-2311-3`）。
- 不修改 `AgentToolDispatcher`/`LlmCallCoordinator`/`SingleTurnExecutor` 的 `>0`/`<=0` 防御守卫（直接构造路径的防御性编码保留，watch-only）。
- 不处理 check-import-order 模块级 176 个 pre-existing 顺序错误与 `_gen` 排除 bug（登记 Non-Blocking Follow-ups；本 plan 只保证受处理文件）。
- 不改变默认值（120s/120s/300s/DEFAULT_MEMBER_EXEC_TIMEOUT_MS）与公共 API 签名。
- 不评估五族门禁机制本身（归 I6 复触发登记）。

## Scope

### In Scope

- 12 个含重复 import 的 nop-ai-agent 文件去重（AR-5，机械反查驱动）。
- 8 个拆分类 Logger 类名修正 + 5 个耦合测试类 appender 目标修正（AR-6）。
- `DefaultAgentEngineConfig` 四个超时 setter 校验统一（AR-7，含 catalog §2 INV-2 全文及 §3.2 相关行核对）。
- `TestEngineConfigAndHelpers` 扩展：四个 setter 非正数拒绝 + 正值接受参数化测试。

### Out Of Scope

- AR-8 / AR-9（各自独立 plan）。
- check-import-order 工具 bug 修复与模块级顺序错误清理（follow-up）。
- 其他 P2 或历史 backlog 项。

## Execution Plan

### Phase 1 - AR-5 重复 import 全量去重（12 文件）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/`（12 文件：AgentCallDelegate/DefaultAgentEngine/AgentSessionLifecycle/AgentToolPlanResolver/SessionLockRenewal/AgentPromptAssembly/AgentSecurityConsultation/AgentSessionSupport/AgentLoopGuard/AgentTeamBinder/AgentStartupWarnings/ReActAgentExecutor）

- Item Types: `Fix | Proof`

- [x] `Fix` 以 `grep -o '^import .*;' <file> | sort | uniq -d` 为扫描驱动，对 nop-ai-agent 全部 `src/main` 文件去重：每个 import 语句保留 1 份，按 import 分组约定（io.nop.* → third-party → java.*，组间空行，静态导入最后）整理。
- [x] `Fix` `AgentCallDelegate.java` 为首要实例（3 份 import 块 → 1 份）；`DefaultAgentEngine.java`（16 条重复）等逐文件处理。
- [x] `Proof` 机械反查：上述扫描命令对 nop-ai-agent `src/main` 输出为空（零重复文件）；对 nop-ai 全模块组（含 api/core/app/coder/gateway 等）跑同一反查，发现 nop-ai-agent 之外的重复 import 文件则**当场纳入 scope 一并去重**（机械清理，无行为变更），或记录为残留在 daily log（须裁定 non-blocking 理由）。
- [x] `Proof` 受处理文件逐一跑 `node ai-dev/tools/check-import-order.mjs --module nop-ai`：**12 个受处理文件全部零报错**（含 `AgentCallDelegate.java`；实测其中 8 文件的 11 条报错全部由重复 import 引起，去重+分组整理后自然消失），其余文件仅剩 pre-existing 顺序错误（与 176-error 基线对比，不新增）。
- [x] `Proof` 行为零变更确认：`git diff` 仅 import 块文本变化。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] nop-ai-agent `src/main` 重复 import 反查归零（扫描命令输出为空）
- [x] 12 个已知文件每个 import 语句恰好 1 次；AgentCallDelegate 等受处理文件在 `check-import-order.mjs` 下零报错（相对 176-error pre-existing 基线不新增）
- [x] `git diff` 仅含 import 块文本变化（编译 + 测试行为不变由 Phase 1/2/3 测试共同验证）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] No new test required: import 文本清理，行为零变更，由机械反查 + 编译 + 全模块测试（Closure Gates）验证
- [x] No owner-doc update required（纯代码文本卫生）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - AR-6 拆分类 Logger 归属修正 + 耦合测试同步

Status: completed
Targets: 生产：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/`（8 类：AgentExecutorResolver/AgentCallDelegate/AgentSessionLifecycle/AgentTeamBinder/AgentStartupWarnings/SessionLockRenewal/AgentSessionSupport/DefaultAgentEngineConfig）；测试：`TestSecureByDefault`/`TestLayer23SecureDefaults`/`TestSecureDefaultsInfoAwareness`/`TestAuditLoggerDefault`/`TestUsageRecorderWiring`

- Item Types: `Fix | Proof`

- [x] `Fix` 8 个类各自 `LoggerFactory.getLogger(DefaultAgentEngine.class)` 改为 `LoggerFactory.getLogger(<本类>.class)`；`TaskDispatchCoordinator` 不得改动。
- [x] `Fix` 5 个耦合测试类：appender 挂接目标从 `DefaultAgentEngine.class` 改为实际发射类——`AgentStartupWarnings.class`（告警类；`TestAuditLoggerDefault` 另有 `Slf4jAuditLogger` 目标不动），断言逻辑不变。
- [x] `Proof` 机械反查：`grep -rn "getLogger(DefaultAgentEngine.class)" nop-ai/nop-ai-agent/src/main/java` 仅剩 `DefaultAgentEngine.java` 自身；`src/test` 零命中（耦合测试已改目标）。
- [x] `Proof` 日志路由验证：改名后 5 个测试类仍绿（appender 收到 `AgentStartupWarnings` 告警），日志归属漂移收敛。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 8 个类 logger 行均为 `getLogger(<本类>.class)`；全模块 `getLogger(DefaultAgentEngine.class)` 仅剩 `DefaultAgentEngine.java`
- [x] `TaskDispatchCoordinator.java:37` 未改动（git diff 确认）
- [x] 5 个耦合测试类 appender 目标已同步且断言仍绿（`./mvnw test -pl nop-ai/nop-ai-agent -am -Dtest=TestSecureByDefault,TestLayer23SecureDefaults,TestSecureDefaultsInfoAwareness,TestAuditLoggerDefault,TestUsageRecorderWiring -Dsurefire.failIfNoSpecifiedTests=false` 通过）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] No new test required: 既有 5 个测试类即日志路由的 focused verification（改目标 = 保持断言有效，非新增行为测试）；生产行为不变
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - AR-7 超时 setter 校验统一（契约裁定 + catalog 同步 + 测试）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngineConfig.java`、`nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/TestEngineConfigAndHelpers.java`、`ai-dev/audits/nop-ai-invariants/invariant-catalog.md` §2 INV-2 / §3.2

- Item Types: `Decision | Fix | Proof`

- [x] `Decision` 裁定：四个超时 setter 统一拒绝 `<= 0`（与 catalog INV-2 声明契约一致）。理由：(a) owner-doc 已声明该契约，live 是漂移侧；(b) 0 的 live 语义在三条路径分裂——`llmTimeoutMs=0`：`LlmCallCoordinator:606` 禁用超时 / `SingleTurnExecutor:132` 立时超时（**同一配置值语义相反**）；`toolTimeoutMs=0`：`AgentToolDispatcher:230` 静默禁用超时（No Silent No-Op 规则禁止把逃生口当正常实现）；(c) 统一契约防 per-setter 漂移复发。setter 拒绝后，三个守卫仍可由直接构造路径到达（防御性编码，watch-only 不删）。
- [x] `Fix` `setLlmTimeoutMs` / `setToolTimeoutMs` 增加与 `setCallAgentTimeoutMs`/`setMemberExecTimeoutMs` 相同的 `<= 0` 拒绝（`NopAiAgentException`，英文消息，镜像既有两 setter 风格）。
- [x] `Proof` 调用面核对：全仓 `setLlmTimeoutMs/setToolTimeoutMs` 调用点（grep 范围 = `nop-ai/` 源码与 resources，**排除 `ai-dev/` 证据目录**——`ai-dev/audits/evidence/ma4-2-05/baseline/` 含历史快照副本会误命中）无一传 0（live 已核实零调用点，执行时再 grep 确认）；确认新校验不破坏既有调用。
- [x] `Proof` 逃生口可达性记录：`LlmCallCoordinator:606`/`AgentToolDispatcher:230`/`SingleTurnExecutor:132` 守卫在 setter 拒绝后仍可通过直接构造到达——核对无生产装配路径传 0，逃生口仅为构造防御，登记 watch-only residual。
- [x] `Fix` 测试：`TestEngineConfigAndHelpers` 新增 `@ParameterizedTest`——四个 setter 对 `0`/`-1` 均抛 `NopAiAgentException`，对正值（如 1000）接受且 getter 回读相等；既有三个默认值断言（:94-96）保持。
- [x] `Proof` catalog 同步：核对 §2 INV-2 全文（:43 行锚点 `DefaultAgentEngineConfig.java:145-147` 已漂移——live 字段在 :147-150，更新为 live 行号）及 §3.2 表内 INV-2 相关行，确认「setter 拒绝非正数」表述在四个 setter 统一后与 live 完全一致。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `DefaultAgentEngineConfig.java` 四个 setter 均拒绝 `<= 0`（代码可读验证）
- [x] `@ParameterizedTest` 覆盖 4 setter × {0, -1} 拒绝 + 正值接受，全绿
- [x] 全仓调用点核对记录：无传 0 的合法调用；逃生口可达性结论落盘（daily log）
- [x] catalog §2 INV-2 全文及 §3.2 相关行与 live 一致（含行锚点修正）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -Dtest=TestEngineConfigAndHelpers -Dsurefire.failIfNoSpecifiedTests=false` 通过
- [x] `ai-dev/audits/nop-ai-invariants/invariant-catalog.md` 已同步
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] AR-5/AR-6/AR-7 三个 in-scope live defect / owner-doc drift 已修复（重复 import 反查归零；logger 归属唯一 + 耦合测试绿；四个 setter 校验统一 + catalog 一致）
- [x] 行为契约结果达成：`setLlmTimeoutMs(0)/setToolTimeoutMs(0)` 从静默接受/语义分裂改为显式拒绝，且无合法调用被破坏
- [x] 必要 focused verification 已完成（Phase 2 五测试 + Phase 3 参数化测试 + Phase 1 机械反查）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响 owner docs（catalog INV-2）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证无空方法体/静默跳过；AR-7 校验在 setter 层真实执行（测试断言抛异常）；AR-6 日志路由真实生效（appender 断言）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过（`./mvnw checkstyle:check -pl nop-ai -am`，按 I5/I6 既有口径记录 pre-existing 基线）

## Deferred But Adjudicated

### 三个超时守卫（`LlmCallCoordinator:606` / `AgentToolDispatcher:230` / `SingleTurnExecutor:132`）的 0 值逃生口

- Classification: `watch-only residual`
- Why Not Blocking Closure: setter 拒绝 `<= 0` 后，生产配置面（beans.xml / builder / setter 调用点）无法再传 0；守卫仅可由直接构造绕过 setter 的路径到达（测试/内部构造），属防御性编码，不改变配置契约；语义分裂已由本 plan 在契约层消除。
- Successor Required: `no`（复触发登记内，周期复探时复核）

## Non-Blocking Follow-ups

- Classification: `optimization candidate`（`check-import-order.mjs` 的 `_gen` 排除 bug + 模块级 176 个 pre-existing 顺序错误——工具修复 + 全模块清理独立于本 plan；修工具后模块级归零才可达）
  - Why Not Blocking Closure: 本 plan 只承诺受处理文件零报错；工具 bug 与历史错误不构成 AR-5 的 in-scope 缺陷（AR-5 的实质 = import 重复，由反查归零验证）
  - Successor Required: `no`

## Closure

Status Note: 三 Phase 全部 completed、Closure Gates 全勾、独立 closure audit APPROVED（0 Blocker/0 Major）——AR-5/6/7 三个 in-scope live defect / owner-doc drift 全部修复，无剩余 plan-owned work。
Completed: 2026-08-13

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general，review-only fresh session）
- Audit Session: `ses_009434f0effenc1Ku2MfoFoTuu`
- Evidence:
  - **Phase 1 Exit Criteria（8 条）全 PASS**：12 engine 文件 + 2 测试文件重复 import 反查零命中；14 个受处理文件 check-import-order 逐文件 0 报错（模块 164 errors ≤ pre-existing 176 基线，受处理文件均不在错误清单）；`git diff` nop-ai/ 仅 import 块文本变化；`./mvnw compile -pl nop-ai/nop-ai-agent -am` exit 0。
  - **Phase 2 Exit Criteria（7 条）全 PASS**：8 类 logger 行均为 `getLogger(<本类>.class)`（AgentExecutorResolver:34/AgentCallDelegate:31/AgentSessionLifecycle:43/AgentTeamBinder:28/AgentStartupWarnings:43/SessionLockRenewal:20/AgentSessionSupport:25/DefaultAgentEngineConfig:102）；全模块 `getLogger(DefaultAgentEngine.class)` 仅剩 `DefaultAgentEngine.java:101`；`TaskDispatchCoordinator.java:37` git diff 为空（未改动）；5 测试类 appender 目标全部 `AgentStartupWarnings.class`（TestAuditLoggerDefault 的 Slf4jAuditLogger 目标 :110 未动）；focused 5 类 34 测试 0 失败。
  - **Phase 3 Exit Criteria（7 条）全 PASS**：`DefaultAgentEngineConfig.java` 四个 setter（:943-947/:950-954/:957-961/:975-979）全部拒绝 `<=0`（`NopAiAgentException`，英文消息）；`TestEngineConfigAndHelpers` `configRejectsNonPositiveTimeoutSetter`（8 cases）+ `configAcceptsPositiveTimeoutSetter`（4 cases × 1000 + getter 回读）26/26 绿；调用面 grep（排除 `ai-dev/` 证据目录，baseline 快照已证含历史副本）零传 0 合法调用；逃生口三守卫（LlmCallCoordinator:606/AgentToolDispatcher:230/SingleTurnExecutor:132）watch-only residual 已登记；catalog §2 INV-2 :43 行锚点 `:145-147`→live `:147-150` 修正 + AR-7 四 setter 统一契约标注，§3.2 相关行核对无漂移。
  - **Closure Gates（10 条）全 PASS**：compile exit 0；focused 6 类 60 测试 0 失败；`./mvnw test -pl nop-ai/nop-ai-agent -am` 3437 tests 0 failures 0 errors（12 新增参数化 case）；`./mvnw test -pl nop-ai -am -T 1C` 8999 tests 0 failures；checkstyle `-Pqa` EXIT=0（默认-config 直跑 = 9164 条 pre-existing violation 全在未触碰上游 nop-api-core，I5/I6 同口径）；`check-import-order` 受处理文件零报错；`scan-hollow-implementations.mjs --module nop-ai --severity high` exit 1 = 2 条 pre-existing（PlanReplanner:272/NoOpProviderFailoverQueue:34）零新增（审计复核确认，同 daily log 基线）。
  - **Anti-Hollow**：AR-7 `assertThrows(NopAiAgentException.class, ...)` 逐 setter 逐值真实执行；AR-6 appender 断言真实检查捕获事件（TestSecureByDefault:312 零-warn-前置 / :322 ≥2 warns；TestUsageRecorderWiring:136/172 warnSeen）——路由空壳必红。无空方法体/静默跳过。
  - **check-plan-checklist.mjs --strict**：审计时点 exit 0（warnings only，plan 未 completed 态）；本 closure 更新后复跑 exit 0（0 unchecked + Closure Evidence 已写入）。
  - **Deferred 分类检查**：三个超时守卫 = `watch-only residual`（setter 拒绝后生产装配面无法传 0，守卫仅直接构造可达，构造防御不改变配置契约）——分类诚实，无 in-scope live defect 被降级；check-import-order `_gen` 排除 bug + 模块级 164 pre-existing = `optimization candidate`（本 plan 只承诺受处理文件，工具修复独立于本 plan）。

Follow-up:

- no remaining plan-owned work
- Non-blocking follow-ups（原样登记）：(1) `check-import-order.mjs` `_gen` 排除 bug + 模块级 164 条 pre-existing 顺序错误（optimization candidate，独立于本 plan）；(2) 三个超时守卫逃生口 = watch-only residual（复触发登记内，周期复探复核）。

## Optional Sections

### Risks And Rollback

- 风险：(1) AR-7 行为变更若存在外部调用方传 0（Phase 3 Proof 项先 grep 全仓核实，live 已确认零调用点），会在调用点抛异常——快速失败而非静默，方向正确，但需 daily log 记录；(2) Phase 2 若漏改任一耦合测试类，`./mvnw test -pl nop-ai/nop-ai-agent -am` 会红——Exit Criteria 已把 5 个测试类列名钉死；(3) Phase 1 若发现 nop-ai-agent 之外模块的重复 import 文件，当场扩 scope（机械清理）或裁定残留。
- Rollback：三个 Phase 均为小 diff 机械修改，`git revert` 可整体回滚；AR-7 测试独立于生产装配。
