# nop-ai 不变式审计 Red List — 2026-08（Cycle 1 / I2 产物）

> 状态: I2 门禁运行 + 对抗探查产物，2026-08-12 生成，交 I3 裁决（本文件不裁决 P 级、不修复）
> 来源: `ai-dev/plans/2026-08-12-1120-3-ai-invariant-i2-gate-driven-audit.md`（I2）；门禁输入 = I1 五族门禁（`2026-08-12-1120-2`）+ known-gaps 清单（`gate-gaps.yaml`）
> 消费方: I3 裁决（P0/P1/P2 分级、修复/观察裁定）；I4 修复；I5 验证
> 零悬挂规则: 每条 finding 归属唯一不变式族（INV-1~5）且指向唯一 successor 路径（I3）；无「未分类」游离项
> 证据要求: 每条 finding 带 live 代码证据（`文件:行`）；门禁来源 finding 附门禁输出记录（见 §5）

## 1. 汇总

| 族 | 不变式 | 门禁 finding 数（含清单外） | 探查 finding 数 | 来源 |
|---|---|---|---|---|
| gate-1 | INV-1 secure-default | 33（全部已登记清单，门禁 PASS） | 0 | 门禁① + 清单 |
| gate-2 | INV-2 timeout 声明 | 5（4 not-applicable + 1 missing-declaration，门禁 PASS） | 3（R-2-1 / R-2-2 / R-2-3） | 门禁② + 探查 |
| gate-3 | INV-3 清理对称性 | 0（8/8 成对，门禁 PASS） | 0 | 门禁③ |
| gate-4 | INV-4 ToolExecutor 安全边界 | 1（AskOracleExecutor not-applicable，门禁 PASS） | 1（R-4-1） | 门禁④ + 探查 |
| gate-5 | INV-5 fix-commit 实质 diff | 0 违规（7 commit 全实质 diff > 0） | 1（R-5-1 门禁精度） | 门禁⑤ + 探查 |

**计数核对**：门禁 finding（清单内 39 条 = 33 + 5 + 1，清单外零差异） + 探查 finding 5 条（R-2-1/2/3、R-4-1、R-5-1） = **44 条**；合并去重后 44 条全部唯一（探查 5 条与门禁清单无重叠——探查面向门禁表外新面）。合并前后计数一致，无遗漏。

## 2. 门禁缺口 finding（known-gaps 清单，I1 首跑登记，I2 交叉核对确认）

> 来源：门禁①-④ 首跑输出（`gate-gaps.yaml` 全量）。I2 Phase 1 交叉核对：清单 39 条全部回 live 代码确认存在性，清单外零新缺口（门禁 PASS = 清单与现状精确一致）。

### INV-1（gate-1）：33/33 Default* 类缺 `@SecureDefault` 声明

- **实例**：`gate-gaps.yaml` gate-1-default-secure 全部 33 条（FQCN 与 `find` 复现命令输出逐条一致，33 行 == §3.1 表 33 条）。
- **证据**：I1 首跑实判 33/33 无注解（声明形式 = `io.nop.ai.api.secure.SecureDefault`，2026-08-12 I1 Phase 1 裁定）；I2 复核 `find nop-ai -name "Default*.java" -not -path "*/target/*" -not -path "*/test/*" -not -path "*/_gen/*"` = 33 行，与清单/表零 diff。
- **建议修复面（I3 裁决）**：I4 逐类补 `@SecureDefault` 注解（声明与门禁契约同步）；补注期间清单条目移除 = 门禁恢复实判（自校验）。
- **严重度候选**：P2（声明契约缺口，行为语义未受损——I2 接线抽查未发现新增 Default* 类缺构造期兜底）。
- **边缘类**：`DefaultGuardrailGrader`（`nop-ai-agent/src/main/java/io/nop/ai/agent/guardrail/test/`，新增 2026-08-02）按 I0 裁定**排除**（guardrail 测试辅助 grader，非引擎/安全组件；复现命令 `-not -path "*/test/*"` 天然排除）——I2 live 确认存在，不悬置。

### INV-2（gate-2）：5 条（4 not-applicable + 1 missing-declaration）

| 实例 | reason-type | 证据（live） |
|---|---|---|
| `IAgentEngine.forkSession` | not-applicable | `DefaultAgentEngine.java:622` 仅 `sessionStore.forkSession` 注册子会话，不启动执行路径 |
| `IAgentEngine.cancelSession` | not-applicable | 取消原语（取消即终结等待） |
| `IAgentEngine.close` | not-applicable | 生命周期终止（AR-09 AutoCloseable） |
| `IAgentEngine.getSessionStatus` | not-applicable | 同步状态查询（I1 表完备性发现 I0 漏计；live `IAgentEngine.java:42`） |
| `SingleTurnExecutor.execute` | **missing-declaration** | `SingleTurnExecutor.java:29` execute 内 `chatService.call(request, null)`（调用点 :41），无 orTimeout/get-with-timeout/配置引用 |

- **建议修复面**：`SingleTurnExecutor.execute` = 真实缺口，I4 补 timeout 声明（签名参数或机制标记）或 I3 裁定 not-applicable；4 条 not-applicable 由 I3 确认理由成立。
- **严重度候选**：SingleTurnExecutor P2（单轮执行器，LLM 挂起可无限等待）；4 条 not-applicable 非缺陷。

### INV-4（gate-4）：1 条 not-applicable

- **实例**：`AskOracleExecutor`（`nop-ai-toolkit/tools/AskOracleExecutor.java:14`）。
- **证据**：oracle client 未实现（P2-MA1-011 fail-fast），live 确认无实际网络 I/O（ORACLE_ENDPOINT 缺失或 client 未实现即 errorResult）。
- **建议修复面**：client 落地时补 SSRF 校验入口（I3/I4）。
- **严重度候选**：P2。

## 3. 对抗探查 finding（I2 新增，面向门禁表外新面）

> 来源：探查（git 时域 diff + 兄弟路径 + 接线抽查）。每条带 live 证据。

### R-2-1 [INV-2] `ChannelMessageServiceImpl.dispatchInbound` 新派发入口无 timeout 声明

- **实例**：`nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelMessageServiceImpl.java:208`（新增 2026-08-08，plan-2026-08-08-1837 W2）。
- **证据**：`dispatchInbound` mode-1 同步 `fanOutToListeners`（:230-233）无 timeout 包装、mode-2 `messageService.send` 无超时契约；全类零 `timeout|Timeout` 标记（grep 零命中）。属 timeout/at-least-once 族直系兄弟（门禁②表外——§3.2 判定标准仅覆盖 IAgentEngine + engine 包 + CallAgentExecutor）。
- **建议修复面（I3 裁决）**：a) 将 dispatchInbound 登记入门禁②表（含 orTimeout/at-least-once 声明判定）；b) 或裁定「同步 fan-out 由调用方（connector）负责 timeout」记录理由。gate-2 表完备性标准是否需要扩展至 gateway 包 = I3 裁定。
- **严重度候选**：P2（mode-1 下挂起 listener 阻塞传输线程）。

### R-2-2 [INV-2 + Anti-Hollow] plan/runtime 编排面无 timeout 标记且生产零接线

- **实例**：`nop-ai-agent/plan/runtime/PlanExecutor.java:108`（execute）、`TaskRunner.java:18`（接口）、`PlanScheduler`、`PlanRunner`、`StagnationDetector` 等（新增 2026-08-01 系列）。
- **证据**：全包零 timeout **机制**标记（`grep -rn "orTimeout|TimeoutException|get(.*TimeUnit"` 零命中；`FailureType.java:40` 仅 javadoc 措辞提及 "timeout"，非机制）；`grep -rln "import io.nop.ai.agent.plan.runtime" nop-ai-agent/src/main` 仅 1 命中（`AgentPlan.java`，引用 AgentPlanValidator）；`TaskRunner` 无产品实现（仅测试构造 `PlanExecutor`）——**生产零接线**（Anti-Hollow：组件存在 + 单测通过 ≠ 系统级可用；I1 closure 已记录 `PlanReplanner.java:272` pre-existing hollow 基线，本 finding 扩大至整个 plan/runtime 面）。
- **建议修复面（I3 裁决）**：a) 裁定 plan/runtime 是否属 INV-2 编排入口（若接线入引擎，须补 timeout 契约 + 表登记）；b) 接线状态（未接线即测试面）显式记录，防后续接线时漏 timeout。
- **严重度候选**：P2（编排入口面 timeout 契约缺口 + hollow 风险）。

### R-2-3 [INV-2 + owner-doc drift] team-flow fan-out 启动 agent 执行路径但无 timeout，I0 排除理由被 live 证据推翻

- **实例**：`MemberFanOutDispatcher.dispatch`（`nop-ai-agent/team/flow/MemberFanOutDispatcher.java:305` `agentEngine.execute(request)`）、`TeamTaskFlowOrchestrator.executeAsync`（`TeamTaskFlowOrchestrator.java:513`）。
- **证据**：**I0 catalog §3.2 排除理由「team-flow 内部方法——不启动 agent 执行路径、无 timeout 契约面」与 live 代码矛盾**——`MemberFanOutDispatcher.java:305` 直接调 `agentEngine.execute(request)`（启动 agent 执行路径），`executeBoundMember` 无 orTimeout 包装；`TeamTaskFlowOrchestrator.executeAsync` 无 timeout 标记。两入口均未入门禁②表。
- **建议修复面（I3 裁决）**：a) 将 team-flow fan-out 补入门禁②表（missing-declaration 登记，I4 补 timeout——如 per-member orTimeout 或 daemon 级 deadline 契约）；b) catalog §3.2 排除理由更正（owner-doc drift 修复）。
- **严重度候选**：**P1 候选**（成员 agent 挂起时团队任务无限等待；AUDIT-14-01 同族在新增面上复发模式）。

### R-4-1 [INV-4] `SsrfGuardDnsResolver` 声明 + 单测存在但生产零接线

- **实例**：`nop-ai-toolkit/tools/ssrf/SsrfGuardDnsResolver.java:34`（新增 2026-08-08，plan-336）。
- **证据**：实现 `IDnsResolver`（resolve + resolveCanonicalHostname + fail-closed 判定），但生产代码零接线——无 IoC bean 配置（`grep -rn "SsrfGuardDnsResolver" --include="*.xml"` 全仓零命中）、无 `setDnsResolver`/`HttpClientConfig.dnsResolver` 赋值点（`ApacheHttpClientHelper.java:100-109` 支持该配置但 nop-ai 侧无装配）；除自身 + 单测 + 2 处 javadoc 提及（`SsrfAddressGuard.java:12/:47`，非接线）外零引用。**声明 ≠ 接线（Lesson 08 模式）在新增面上复发**。
- **建议修复面（I3 裁决）**：a) 接线：将 resolver 装配到默认 HTTP client bean（`ai-tools-defaults.beans.xml` 或应用层 `HttpClientConfig.dnsResolver`）；b) 或裁定 DNS 层防护非必需（host 级 `SsrfAddressGuard.validateHost` 已接线）记录理由并标注 dead code。
- **严重度候选**：P1/P2 候选（DNS-rebinding 层防护未生效；host 级校验已接线故降级空间存在——I3 定）。

### R-5-1 [INV-5] 门禁⑤匹配精度：commit body 含 `fix(nop-ai)` 字样被纳入扫描

- **实例**：`ai-dev/tools/check-fix-commit-diff.mjs`（`--grep=fix(nop-ai)`，`git log --grep` 匹配 subject + body）。
- **证据**：本次扫描 7 个匹配 commit 中 2 个非 `fix(nop-ai)` commit——`5ebad065e`（chore(ci)，body 含 "fix(nop-ai) commit" 字样，diff=23）、`c1362dc77`（feat(ai)，body 含字样，diff=616）——两 commit 均有实质 diff 故未误报违规，但**body 提及 fix(nop-ai) 而产品 diff 为零的 chore/feat commit 会被误报**。
- **建议修复面（I3 裁决）**：收窄为 subject 匹配（`--grep='^fix(nop-ai)'` 或 `--format='%s'` 前缀判断）或接受现状（误报仅成本无漏报）。
- **严重度候选**：P2（门禁精度缺陷，非真实违规）。

## 4. 探查排除项（核对过，无问题）

- INV-1：`DefaultWaitCoordinator`（新增 2026-08-01）已在表 #7 + 清单登记；行为级确认含 timeout 调度（`DefaultWaitCoordinator.java:78-82` TIMEOUT 条件经 scheduler 调度 deliverWake）；shipped default = `NoOpWaitCoordinator`（WAIT_FOR opt-in 设计，非缺口）。
- INV-2 种子接线：`LlmCallCoordinator.callChatWithTimeout`（:605，调用点 :218）、`AgentToolDispatcher` per-tool orTimeout（:232）、`CallAgentExecutor` resolveTimeoutMs + dispatch 链（:211-285）——已修复实例接线成立。
- INV-3：gate-3 8/8 成对；新面 start/stop 对称核对通过——`TeamTaskSchedulerDaemon`（schedule cancel + shutdownOwnSpawnExecutor）、`DBMessageService.close`（poller shutdownNow :203-215）、`TeamTaskFlowOrchestrator.close`（ownedSpawnExecutor.shutdownNow :380）、`InMemoryActorRuntime`（executor shutdown + shutdownTimeoutMs）。
- INV-4 接线抽查通过：`LocalToolFileSystem.isPathAllowed`→`resolveFile`（:53-54）、`HttpRequestExecutor.validateUrl`→`SsrfAddressGuard.validateHost`（:63/:84）、`GraphqlQueryExecutor.validateUrl`（:71）、`BashExecutor.validateCommand`（:103）+ plan-335 sandbox fail-closed（无 backend 拒绝 :91-93）、`ReadRefExecutor` 有界抽象 `ICompactionArchiveReader` + hash 校验 + fail-loud。
- INV-5：7 个 `fix(nop-ai)` commit 全部实质 diff > 0，零违规。
- 新增 ToolExecutor 面：`ReadRefExecutor` 已在门禁④表 #25，声明成立。
- `UpdateTodosExecutor` 表文件面/live 内存面偏差（I1 note）——I2 复核：live 为 `ConcurrentHashMap` 实现，按 live 面声明成立，偏差记录交 I3 核对表标注。

## 5. 门禁输出归档（Phase 1 记录）

- 门禁①②③（JUnit/ArchUnit）：`./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS 03:39；surefire：`TestInvariantGate1SecureDefault` 5/5、`TestInvariantGate1SecureDefaultShell` 3/3、`TestInvariantGate2OrchestrationTimeout` 3/3、`TestInvariantGate3EntryPointCleanup` 3/3，全 0 fail。
- 门禁④（`pnpm check:ai-tool-boundary`）：exit 0，PASS — 30 实例声明成立 + 表完备性一致 + LocalToolFileSystem 接线 OK。
- 门禁⑤（`pnpm check:fix-commit-diff`）：exit 0，PASS — 7 commit 全实质 diff > 0（明细见 R-5-1 上下文）。
- known-gaps 清单 diff：零差异（清单外零新缺口；清单内 39 条全 live 确认）。

## 6. 独立子 agent 审查记录

- 审查轮次：1 轮 fresh session 独立审查（general，task `ses_00b7fab7fffeoYnqMNCn4behLA`，review-only 禁改文件）。
- 审查维度：零悬挂（44 条 = 门禁 39 + 探查 5，计数/归属逐条 live 核对，零游离项）、证据真实性（R-2-1/2/3、R-4-1、R-5-1 全 live 核对）、格式可裁决性、与 plan Phase 1/2 执行记录一致性。
- 审查发现与修订（1 Blocker + 1 Major + 4 Minor，全部已修订，见下）：
  - Blocker-1（§6 声明无据）：修订前 §6 声明「审查已执行，结论见 plan §Closure 与 daily log」但两处引用为空——已通过本文件 §6 记录 + plan Phase 3/Closure + daily log 补证解决（本修订链）。
  - Major-1（R-2-2 证据命令不可复现）：`grep -rln "io.nop.ai.agent.plan.runtime"` 字面匹配包声明（21 命中）——已改为 `grep -rln "import io.nop.ai.agent.plan.runtime"`（实测 1 命中）+ 明确排除包声明。
  - Minor-1（R-2-2「零 timeout 命中」字面不实）：`FailureType.java:40` javadoc 措辞含 "timeout"——已改为「零 timeout 机制标记」+ 显式排除 javadoc。
  - Minor-2（gate-2 行号偏移）：`SingleTurnExecutor.java:29` 为 execute 签名行，`chatService.call` 调用点实为 :41——已修正。
  - Minor-3（R-4-1 日期 off-by-one）：`SsrfGuardDnsResolver` 新增 commit 为 2026-08-08（plan-336），非 2026-08-07——已修正。
  - Minor-4（R-4-1「仅单测引用」不精确）：另有 `SsrfAddressGuard.java:12/:47` 两处 javadoc 提及——已注明（非接线，不影响结论）。
- 审查结论：`revised` → 修订后 `approved`（核心 finding 面 R-2-1/2/3、R-4-1、R-5-1 实质 + 39 条门禁缺口 + 44 计数 + 零悬挂全部 live 成立；修订仅涉及措辞与引用精确性，不影响 I3 可裁决性）。
