# nop-ai 不变式裁决表 — 2026-08（Cycle 1 / I3 产物）

> 状态: I3 裁决产物，2026-08-12 生成，交 I4 修复执行（唯一修复契约面）
> 来源: `ai-dev/audits/nop-ai-invariants/red-list-2026-08.md`（I2 产物，44 条 finding）；本表逐条裁决
> 消费方: I4（`ai-dev/plans/2026-08-12-1411-2-ai-invariant-i4-fix-execution.md`，只消费本表，不自行重裁）；I5 验证
> 零悬挂规则: 每条 finding 唯一 P 级 + 唯一决策路径（fix-I4 / watch / not-applicable-confirmed），无「待定/由 I4 自行决定」
> 授权边界（mission `nop-ai-invariant-loop.json` + I3 plan 显式裁定）: P0/P1/P2 的 fix-I4 决策（含 gate-1 33 类补注解、R-2-1/R-2-3 timeout 机制、R-5-1 门禁修复等）均在预授权范围内；**仅跨模块公共 API 触碰项须「人工确认」**——本表经逐条核对，44 条裁决均不触碰跨模块公共 API（nop-ai-api 面；SingleTurnExecutor 构造/MemberFanOutDispatcher 签名的变更为 nop-ai-agent 模块内契约，调用点全在本模块内），故**零「人工确认」标记**。

## 1. 裁决汇总

**计数**：门禁 finding 39 条（gate-1 33 + gate-2 5 + gate-4 1）+ 探查 finding 5 条（R-2-1/2/3、R-4-1、R-5-1）= **44 条**，全部裁决，零悬挂。

| 决策路径 | 数量 | 明细 |
|---|---|---|
| fix-I4 | 37 | gate-1 33 类 + `SingleTurnExecutor.execute` + R-2-1 + R-2-3（2 入口）+ R-5-1 |
| watch | 1 | R-2-2（plan/runtime，生产零接线 = 非入口，触发条件已登记） |
| not-applicable-confirmed | 6 | gate-2 4 条（forkSession/cancelSession/close/getSessionStatus）+ gate-4 `AskOracleExecutor` + R-4-1 |

**交叉核对**：本表 44 == red-list 44（门禁 39 + 探查 5）；gate-gaps 更新后 39 + 3（R-2-1 + R-2-3 ×2 登记）= 42 条（R-4-1 非 ToolExecutor 实例不登记、R-2-2 watch 不登记、not-applicable 保持既有登记）。

## 2. 门禁 finding 裁决（39 条）

### 2.1 INV-1 / gate-1：33/33 Default* 类补 `@SecureDefault` — **P2 + fix-I4（批量）**

- **实例**：`gate-gaps.yaml` gate-1-default-secure 全部 33 条（FQCN 逐条一致；`find nop-ai -name "Default*.java" -not -path "*/target/*" -not -path "*/test/*" -not -path "*/_gen/*" | sort` = 33 行 == catalog §3.1 表 33 条 == 清单 33 条，2026-08-12 live 复核通过）。
- **证据引用**：red-list §2 INV-1（33/33 无注解）；`@SecureDefault` 注解已存在（`nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/secure/SecureDefault.java`，marker 纯声明）。
- **P 级**：P2（声明契约缺口，行为语义未受损——I2 接线抽查未发现新增 Default* 类缺构造期兜底）。
- **决策**：**fix-I4**（批量，33 类统一处理）。
- **依据**：声明与门禁①契约同步；行为语义验证已由 I2 接线抽查承接（red-list §4），本表不重复验证。
- **I4 动作清单（机制级）**：
  - 文件：33 个 Default* 类（FQCN 见 gate-gaps.yaml；agent 22 + core 8 + shell 2 + toolkit 1）。
  - 机制：每类类级加 `@SecureDefault` 注解 + `io.nop.ai.api.secure` import（纯声明 marker，无行为）。
  - 类别清扫：补注解前用 catalog §4 复现命令 grep 全量确认 33 类无遗漏、无新增。
  - 测试放置：既有门禁①测试即回归测试（`TestInvariantGate1SecureDefault` agent 31 类 + `TestInvariantGate1SecureDefaultShell` shell 2 类，断言注解存在性 + 表完备性），无需新测试类。
  - 门禁测试扩展：无（表完备性已机械反查接线）。
  - known-gaps：33 条 gate-1 条目修复后移除（自校验：移除后门禁实判——有注解 → 绿）。
  - 反例验证：临时去掉任一已补注解类 → 门禁红 → 恢复 → 绿（棘轮验证）。
- **successor**：I4 Phase 2。

### 2.2 INV-2 / gate-2：5 条（4 not-applicable-confirmed + 1 fix-I4）

| finding-ID | 实例 | P 级 | 决策 | 依据（live） | I4 动作清单 |
|---|---|---|---|---|---|
| gate-2/forkSession | `IAgentEngine.forkSession` | N/A（非缺陷） | **not-applicable-confirmed** | `DefaultAgentEngine.java:622` forkSession 实现，仅 `sessionStore.forkSession` 注册子会话（:647，校验 parent 存在 → 注册），不启动 agent 执行路径、无异步等待面 | 保持登记；无修复动作 |
| gate-2/cancelSession | `IAgentEngine.cancelSession` | N/A（非缺陷） | **not-applicable-confirmed** | 取消原语（取消即终结等待），无异步执行等待面 | 保持登记；无修复动作 |
| gate-2/close | `IAgentEngine.close` | N/A（非缺陷） | **not-applicable-confirmed** | 生命周期终止入口（AR-09 AutoCloseable），无异步执行等待面 | 保持登记；无修复动作 |
| gate-2/getSessionStatus | `IAgentEngine.getSessionStatus` | N/A（非缺陷） | **not-applicable-confirmed** | 同步状态查询入口（`IAgentEngine.java:42` default），非异步编排面 | 保持登记；无修复动作 |
| gate-2/SingleTurnExecutor.execute | `engine/SingleTurnExecutor.java:29` execute（:41 `chatService.call(request, null)`） | **P2** | **fix-I4** | 单轮执行器 execute 内 `chatService.call` 为阻塞式默认方法（`IChatService.java:19-20` default `FutureHelper.syncGet(callAsync(...))`），LLM 挂起可无限阻塞调用线程；无任何 timeout 机制标记/配置引用 | **机制级动作清单**：(1) 文件 = `SingleTurnExecutor.java`；(2) 机制 = 构造参数新增 `long llmTimeoutMs` + `Executor timeoutExecutor`（复用 ReAct 同款模式——`AgentExecutorResolver.java:195-202` 已为 ReAct 注入 `llmTimeoutMs` :200 + `timeoutExecutor` :202，同一配置源），execute 内以 timeoutExecutor 提交 `chatService.call` 并 `future.get(llmTimeoutMs, TimeUnit.MILLISECONDS)`，超时抛 TimeoutException → 异常完成/失败路径生效；(3) deadline 来源 = `DefaultAgentEngineConfig.llmTimeoutMs`（:146 =120000，setter 拒绝非正数，已有非零默认）；(4) 测试放置 = nop-ai-agent engine 单测（mock `IChatService` 挂起 → 断言 execute 超时后异常/失败完成，行为级验证）；(5) 门禁测试扩展 = `TestInvariantGate2OrchestrationTimeout` TABLE 中该条目 verdict `missing` → `declared` + evidenceFile/marker 与修复后机制一致（`orTimeout`/`get(...,TimeUnit` 级标记），`deriveEntryIds()` 无需扩展（engine 包已覆盖）；(6) known-gaps = gate-2 族该条目修复后移除；(7) 公共 API = 构造签名变更（`AgentExecutorResolver.java:207` 为唯一构造点，nop-ai-agent 模块内契约，**不触碰跨模块公共 API，无需人工确认**） |

**类别清扫约束（gate-2 全量）**：I4 Phase 3 修复时穷举 catalog §3.2 全部入口（14 + 本表补表条目 R-2-1/R-2-3），确认无遗漏同类缺口。

### 2.3 INV-4 / gate-4：1 条 not-applicable-confirmed

| finding-ID | 实例 | P 级 | 决策 | 依据（live） | 触发条件 |
|---|---|---|---|---|---|
| gate-4/AskOracleExecutor | `nop-ai-toolkit/tools/AskOracleExecutor.java:14` | N/A（非缺陷；严重度候选 P2 仅对「client 落地后」成立） | **not-applicable-confirmed** | 2026-08-12 live 复核：`AskOracleExecutor.doExecute` 中 ORACLE_ENDPOINT 缺失 → errorResult（P2-MA1-011 fail-fast）；client 未实现 → errorResult（"not implemented yet"）；**无实际网络 I/O**（无 HttpClient/URL 构造），SSRF 校验入口不适用 | **oracle client 落地时补 SSRF 校验入口**（触发条件登记，I6/Cycle 复触发） |

## 3. 探查 finding 裁决（5 条）

### R-2-1 [INV-2] gateway 派发入口无 timeout — **P2 + fix-I4（补入 gate-2 表）**

- **实例**：`nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelMessageServiceImpl.java:208` `dispatchInbound`（mode-1 同步 `fanOutToListeners` :230、mode-2 `messageService.send` :222）。
- **证据引用**：red-list §3 R-2-1。2026-08-12 live 复核：`dispatchInbound` :208 存在；mode-1 fan-out :230 无 timeout 包装；mode-2 `messageService.send` :222；全类零 `timeout|Timeout` 标记（grep 0 命中）。复核刷新：该类首 commit 实为 2026-08-09（`7d084027e` plan-2026-08-08-1837 W2），red-list 记 2026-08-08 为 plan 日期，证据列注明。**mode-2 语义复核（I3 独立审查修订）**：`IMessageSender.send` default = `FutureHelper.syncGet(sendAsync(...))`（`IMessageSender.java:26-28`）——**是阻塞等待投递 future 的调用，非 fire-and-forget**；`DBMessageService` 仅实现 `sendAsync`（:229，persist-before-return :43-44 = at-least-once 持久化投递），未 override `send` → `ChannelMessageServiceImpl:222` 实际同步阻塞至 DB INSERT 完成；DB 挂起时传输线程同样可无限阻塞。故 mode-2 的等待面 = 「sendAsync future 完成（= 持久化完成）」，需与 mode-1 同等有界化。
- **P 级**：**P2**（mode-1 下挂起 listener 阻塞传输线程；mode-2 下 DB 挂起阻塞传输线程，at-least-once 声明缺失）。
- **决策**：**fix-I4 — 补入 gate-2 表 + timeout/at-least-once 声明**（选 (a)，否决「同步 fan-out 由 connector 负责」——dispatchInbound 是 gateway 传输层的统一派发入口，调用方 connector 无法为未知 listener 集提供 timeout 保证，fan-out 自身的等待面必须自持）。
- **依据**：gateway 包在门禁②表外（§3.2 判定标准仅覆盖 IAgentEngine + engine 包 + CallAgentExecutor）→ 判定标准面扩展由 I4 实施；mode-1 是真实同步等待面（listener 挂起），mode-2 是持久化等待面（send 阻塞 syncGet）。
- **I4 动作清单（机制级，单一机制无「或」）**：
  - (a) **catalog §3.2 表新增条目**：`ChannelMessageServiceImpl.dispatchInbound`（gateway 包，判定标准扩展为「channel 派发入口」）。
  - (b) **机制（唯一指定）**：
    - mode-1：`fanOutToListeners` 内对每个 listener 调用包装为 `CompletableFuture.runAsync(() -> listener.onInbound(message), fanOutExecutor).orTimeout(dispatchTimeoutMs, TimeUnit.MILLISECONDS)`，超时/异常 fail-loud（LOG.error 记录，不静默吞掉）。
    - mode-2：`messageService.send`（阻塞 syncGet）**改为 `messageService.sendAsync(topic, message)`** + `.orTimeout(dispatchTimeoutMs, TimeUnit.MILLISECONDS)`，超时 fail-loud 记录——at-least-once 语义由 DBMessageService persist-before-return 承担（超时仅停止等待，投递可能已持久化 → 消费侧幂等承担重复投递，与 DBMessageService 既有契约一致）。
  - (c) **deadline + executor 来源**：新增配置旋钮 `nop.ai.gateway.channel.dispatchTimeoutMs`（默认 30000，setter 拒绝非正数——对齐 `DefaultAgentEngineConfig` 模式）经 setter 注入；新增 `fanOutExecutor`（`java.util.concurrent.Executor` setter 注入，默认懒创建 daemon 线程池，`close()` 时 shutdown——对齐 `TeamTaskFlowOrchestrator.ownedSpawnExecutor` 模式）。
  - (d) **测试放置（跨模块约束裁定）**：nop-ai-gateway 依赖 nop-ai-agent（反向不成立，pom 复核通过），gate-2 测试类在 agent 模块无法引用 gateway 类；**`InvariantGateSupport` 硬编码 `src/main/java/io/nop/ai/agent` 包前缀读取源文件（:35-37），agent 模块 TABLE 扩展分支不可行** → **gateway 模块新建独立 gate 测试类**（如 `TestInvariantGate2GatewayTimeout`，自包含 TABLE + 派生逻辑，不依赖 InvariantGateSupport，机械反查 gateway 包派发入口）。
  - (e) **门禁测试扩展要求（防 inert 登记）**：`TestInvariantGate2OrchestrationTimeout` 的 TABLE **不**加 gateway 条目（读不了 gateway 源文件）；gateway 模块新测试类承担该条目的 TABLE/派生/表完备性职责，该条目 verdict=`declared` + evidenceFile=`gateway/channel/ChannelMessageServiceImpl.java` + **marker=`orTimeout`**（mode-1 runAsync().orTimeout 与 mode-2 sendAsync().orTimeout 均含该标记，单一 marker 无二义）。
  - (f) **known-gaps**：本表 Phase 3 登记 gate-2 族条目（附裁决证据）；I4 修复后移除。
- **successor**：I4 Phase 3。

### R-2-2 [INV-2 + Anti-Hollow] plan/runtime 编排面 — **P2 + watch（生产零接线 = 非入口）**

- **实例**：`nop-ai-agent/plan/runtime/`（PlanExecutor.java:108 execute、TaskRunner.java:18 接口、PlanScheduler、PlanRunner、StagnationDetector 等 20 文件）。
- **证据引用**：red-list §3 R-2-2。2026-08-12 live 复核：全包零 timeout 机制标记（grep 0 命中）；`import io.nop.ai.agent.plan.runtime` 在 src/main 仅 1 命中（`AgentPlan.java:4` → AgentPlanValidator）；`TaskRunner` 零产品实现（grep 零命中）；生产零接线成立。
- **P 级**：**P2**（若接线则为编排入口面 timeout 契约缺口 + hollow 风险；当前非入口）。
- **决策**：**watch**（选 (b)）——裁定「生产零接线 = 非入口」：plan/runtime 当前无运行时执行路径（引擎不调用），不构成 INV-2 编排入口，不补 timeout 契约、不入表登记。
- **依据**：INV-2 针对「启动 agent 执行路径」的编排入口；零接线的组件不存在运行时等待面。Anti-Hollow 风险以 watch 记录（`PlanReplanner.java:272` pre-existing hollow 基线已在 I1 closure 记录，非本 plan 引入）。
- **接线状态（显式记录）**：plan/runtime 包（20 文件）生产零接线；唯一 src/main 引用 = `AgentPlan.java` → `AgentPlanValidator`（模型校验用）；`TaskRunner` 仅测试构造。
- **触发条件（watch 复触发）**：plan/runtime 接线时（出现 `TaskRunner` 产品实现 / 引擎引用 `PlanExecutor.execute`）须补 timeout 契约 + gate-2 表登记（missing-declaration）→ 复触发 I2/I5。
- **I4 动作清单**：仅 catalog 记录接线状态说明（I4 Phase 3，不动代码）。
- **successor**：I4 Phase 3（仅记录）；触发后 → 后续 Cycle。

### R-2-3 [INV-2 + owner-doc drift] team-flow fan-out — **P1 + fix-I4（机制级）**

- **实例**：`MemberFanOutDispatcher.java:305` `agentEngine.execute(request)`（`dispatch` :152 为 static 方法，agentEngine 是参数；`executeBoundMember` :296 无 orTimeout 包装）、`TeamTaskFlowOrchestrator.java:513` `executeAsync`（无 timeout 标记；:384 awaitTermination 为 close 路径关闭等待非编排 timeout）。
- **证据引用**：red-list §3 R-2-3。2026-08-12 live 复核：:305 `agentEngine.execute(request)` 存在（static 方法、全类无 timeout/config 字段）；:513 `executeAsync` 存在（全类无 timeout 机制、无 config 字段——**deadline 来源不存在现成配置**，须本表给出机制级决策）。
- **P 级**：**P1**（成员 agent 挂起时团队任务无限等待；AUDIT-14-01 同族在新增面上复发；owner-doc drift 已确认）。
- **决策**：**fix-I4**（P1，mission 预授权范围）。
- **依据**：`agentEngine.execute` 直接启动 agent 执行路径 → 属 INV-2 编排入口面；I0 catalog §3.2 排除理由「team-flow 内部方法——不启动 agent 执行路径」与 live 矛盾（owner-doc drift 成立）。
- **I4 动作清单（机制级，不得写「或」）**：
  - **(1) deadline 来源（唯一，两条装配路径同源）**：新增 `DefaultAgentEngineConfig.memberExecTimeoutMs`（long，默认 120000，setter 拒绝非正数——与 `callAgentTimeoutMs/llmTimeoutMs/toolTimeoutMs` 同族）作为唯一配置源；`TeamTaskFlowOrchestrator` 新增同名字段（构造参数可选 + setter，flow 路径装配时从 config 派生注入）；**daemon/scheduler 路径**：`TeamTaskSchedulerDaemon`（:81 构造，`dispatchCoordinator = new TaskDispatchCoordinator(agentEngine, taskStore, daemonSessionId)` :137）→ `TaskDispatchCoordinator`（:40-44 字段，:196 调用 dispatch）构造链各增 `long memberExecTimeoutMs` 形参，由装配方从同一 `DefaultAgentEngineConfig.memberExecTimeoutMs` 派生传入（**两路径同源，不得各自发明默认值**）。`MemberFanOutDispatcher` 为 static 无法挂字段，deadline 经 `dispatch`/`executeBoundMember` 形参传递。
  - **(2) 机制**：`executeBoundMember` 内对 `agentEngine.execute(request)` future 加 `.orTimeout(memberExecTimeoutMs, TimeUnit.MILLISECONDS)`（per-member）；超时路径按 AUDIT-14-01 语义取消子会话（`engine.cancelSession(childSessionId, ...)`，对齐 `CallAgentExecutor.java:473` 超时取消语义）+ `MemberExecOutcome` 失败态（诚实失败，任务保持 CLAIMED 由调度层处置）；`TeamTaskFlowOrchestrator.executeAsync` 对 result future 加 daemon 级 `.orTimeout(memberExecTimeoutMs, ...)` 整体 deadline 兜底（同旋钮派生）。
  - **(3) API 触碰裁定**：`MemberFanOutDispatcher.dispatch`（public static :152）新增形参 `long memberExecTimeoutMs`——调用方全在 nop-ai-agent 模块内（`TaskDispatchCoordinator.java:196` + `SpawnMemberFanOutStep` :176 + `BoundMemberFanOutStep` :163 + `MixedMemberFanOutStep` :141），属**模块内契约变更，不触碰跨模块公共 API，无需人工确认**（同步更新 4 个调用点；fan-out step 由 orchestrator 构造 :761/:766/:771 传入字段值，coordinator 由 daemon 构造传入）；`TeamTaskFlowOrchestrator.executeAsync` 签名不变。
  - **(4) 测试放置**：nop-ai-agent team/flow 单测（member 挂起 → 超时 → 取消/失败处置生效 + 团队任务不无限等待，行为级）；gate-2 TABLE 新增两条目（`MemberFanOutDispatcher.dispatch` / `TeamTaskFlowOrchestrator.executeAsync`，verdict `declared`，marker = `orTimeout` 精确标记——不得用宽泛 `TimeUnit` 词以免误判）。
  - **(5) 门禁测试扩展（防 inert 登记）**：(a) catalog §3.2 表新增 2 条目 + 判定标准扩展（team/flow 包 dispatch/executeAsync 入口）；(b) `TestInvariantGate2OrchestrationTimeout` TABLE 更新；(c) **`deriveEntryIds()` 机械派生逻辑扩展**（当前只覆盖 IAgentEngine + engine 包 + CallAgentExecutor，不覆盖 team/flow 包——不扩派生则表完备性测试必红）。team/flow 包在 agent 模块内，InvariantGateSupport 可读，**agent 模块 TABLE 分支可行**（与 R-2-1 的 gateway 分支相反）。
  - **(6) owner-doc drift 更正（catalog §3.2 排除理由）**：删除「team-flow 内部方法（`TeamTaskFlowOrchestrator.executeAsync`、`MemberFanOutDispatcher.dispatch`）——不启动 agent 执行路径、无 timeout 契约面」排除理由 → 改为 live 事实「fan-out 经 `MemberFanOutDispatcher.java:305 agentEngine.execute` 启动 agent 执行路径，属编排入口面，须声明 timeout」；同步判定标准原文（I4 Phase 3 执行）。
  - **(7) known-gaps**：本表 Phase 3 登记 gate-2 族 2 条目（附裁决证据）；I4 修复后移除。
- **successor**：I4 Phase 3。

### R-4-1 [INV-4] SsrfGuardDnsResolver 生产零接线 — **P2 + not-applicable-confirmed**

- **实例**：`nop-ai-toolkit/tools/ssrf/SsrfGuardDnsResolver.java:34`（实现 `IDnsResolver`，resolve + resolveCanonicalHostname + fail-closed）。
- **证据引用**：red-list §3 R-4-1。2026-08-12 live 复核（关键依赖面）：**nop-ai 默认 HTTP client = `JdkHttpClient`（`nop-http-client-jdk` 模块，`nop-ai-core` pom 可选依赖），`grep dnsResolver` 在 `nop-http-client-jdk/src/main` 零命中——不消费 `HttpClientConfig.dnsResolver`**；消费 dnsResolver 的 `ApacheHttpClientHelper`（:100-109 `clientConfig.getDnsResolver()` → `builder.setDnsResolver`）位于 `nop-network/nop-http/nop-http-client-apache` 模块，**非任何 nop-ai 模块的依赖**（nop-ai-toolkit pom 仅依赖 nop-http-api）；`HttpRequestExecutor` 经 `IHttpClient` 接口注入（:33-36），client 实现由装配决定。`SsrfGuardDnsResolver` 全仓零 bean 配置、零 setDnsResolver 调用点。
- **P 级**：**P2**（降级自 P1/P2 候选——host 级主防线已接线，残差为纵深防御层）。
- **决策**：**not-applicable-confirmed**（选 (b)）——裁定 DNS 层防护当前非必需。
- **依据（威胁面分析）**：(1) 接线现实：默认 client `JdkHttpClient` 不消费 dnsResolver，消费点（Apache 族）不在 nop-ai 依赖面——「装配到默认 client」会静默 no-op，接线 = 换 client 实现（升级依赖 + 行为变更），成本 > 残差威胁面；(2) 主防线已接线：host 级 `SsrfAddressGuard.validateHost` 在 `HttpRequestExecutor.java:84`、`GraphqlQueryExecutor.java:71` 调用点 live 确认（URL 校验 + 内网/loopback/cloud-metadata 黑名单 + 编码 IP 归一化）；(3) DNS-rebinding 残差威胁面 = validate 与连接之间的 TOCTOU + 攻击者控制 DNS 的场景——属纵深防御层缺口，非主防线缺口，且 host 级校验已覆盖常见 SSRF 向量（metadata、内网直连、编码 IP）。
- **处置（I4 动作清单）**：(1) 在 catalog 标注 SsrfGuardDnsResolver 为 dead code（生产未接线 + 接线路径说明：装配至消费 dnsResolver 的 client 实现时启用）；(2) 触发条件登记 = 若引入消费 dnsResolver 的 HTTP client（Apache 族装配 / JDK 扩展支持点）→ 接线 `SsrfGuardDnsResolver`（复触发 I4/I5）；(3) gate-gaps **无变更**（`SsrfGuardDnsResolver` 非 ToolExecutor 实例，不在门禁④表 catalog §3.3）。
- **successor**：I4 Phase 4（仅记录 + dead-code 标注）；触发后 → 接线。

### R-5-1 [INV-5] 门禁⑤匹配精度 — **P2 + fix-I4（subject-only 收窄）**

- **实例**：`ai-dev/tools/check-fix-commit-diff.mjs`（`--grep=fix(nop-ai)` 匹配 subject+body）。
- **证据引用**：red-list §3 R-5-1。2026-08-12 live 复核：mjs:97 `--grep=fix(nop-ai)` + `--format=%H %s`（**subject 已由 `%s` 输出**——JS 侧可直接做前缀判定）；本次扫描 7 个匹配 commit 中 2 个非 fix commit（`5ebad065e` chore(ci) body 含字样 diff=23、`c1362dc77` feat(ai) body 含字样 diff=616），均有实质 diff 未误报违规，但 body 提及而 diff 为零的 chore/feat commit 会被误报。
- **P 级**：**P2**（门禁精度缺陷，非真实违规；误报仅成本无漏报）。
- **决策**：**fix-I4 — 收窄为 subject-only 匹配**。
- **依据**：`--grep='^fix(nop-ai)'` 锚定行首仍会误匹配 body 首行以该字样开头的 commit（`git log --grep` 逐行匹配 body）；首选 `--format='%s'` 前缀判断——subject 已在输出中，JS 侧 `subject.startsWith('fix(nop-ai)')` 判定，彻底消除 body 误报面。
- **I4 动作清单（机制级）**：(1) 修复方式（单一主路径）= `collectFixCommits` 中**移除 `--grep=fix(nop-ai)`**，改为 `git log --no-merges --format=%H %s <range>` 全量取 subject，JS 侧 `subject.startsWith('fix(nop-ai)')` 前缀判定为**唯一候选过滤**（subject 已由 `%s` 输出，无需 `--grep='^fix(nop-ai)'`——锚定行首仍会误匹配 body 首行以该字样开头的 commit）；(2) self-test 增补反例：构造「subject 非 fix(nop-ai) 但 body 首行以 'fix(nop-ai)' 开头」的 commit → 断言其被排除出候选集；(3) 同步更新现有 self-test 的 commit 计数断言（新增 fixture 后计数按实际调整）；(4) 既有正反例保持通过；(5) 修复后实跑 `pnpm check:fix-commit-diff` 确认 `5ebad065e` / `c1362dc77` 不再被误报为候选。
- **successor**：I4 Phase 5。

## 4. UpdateTodosExecutor 表标注偏差核对结论（I2 委托承接）

- **核对内容**（red-list §4 末条委托）：catalog §3.3 #30 `UpdateTodosExecutor` 表标注「文件」安全敏感面 vs live 实现。
- **live 事实（2026-08-12 复核）**：`UpdateTodosExecutor.java:16` `implements IToolExecutor`，:19 `private static final Map<String, List<TodoItem>> todoLists = new ConcurrentHashMap<>()`——**纯内存实现，无文件 IO**。
- **核对结论**：偏差成立——表标注（文件面）与 live 实现（内存 ConcurrentHashMap 面）不一致。
- **裁定**：**更正表标注（文件面 → 内存/会话面）**——catalog §3.3 #30 安全敏感面列由「文件」更正为「内存/会话」；gate-4 判定不受影响（I1 已按 live 面判声明成立）。
- **归属（显式）**：该更正由 **I4 Phase 6 catalog 同步承接**（本表记录裁定，I4 执行修正；不在 44 条 finding 内，不丢失）。

## 5. 零悬挂核对（Proof）

**44/44 唯一归属核对**：

| 族 | finding-ID 集合 | 决策路径 | 计数 |
|---|---|---|---|
| INV-1 | gate-1/33 类（批量，每条唯一 ID） | fix-I4 | 33 |
| INV-2 | gate-2/forkSession, cancelSession, close, getSessionStatus | not-applicable-confirmed | 4 |
| INV-2 | gate-2/SingleTurnExecutor.execute | fix-I4 | 1 |
| INV-2 | R-2-1 | fix-I4 | 1 |
| INV-2 | R-2-2 | watch（触发条件已登记） | 1 |
| INV-2 | R-2-3（2 入口 = 1 finding） | fix-I4 | 1 |
| INV-4 | gate-4/AskOracleExecutor | not-applicable-confirmed | 1 |
| INV-4 | R-4-1 | not-applicable-confirmed（触发条件已登记） | 1 |
| INV-5 | R-5-1 | fix-I4 | 1 |
| **合计** | | | **44** |

- 无「未分类」游离项；每条 finding 唯一 P 级 + 唯一决策路径。
- 每条 fix-I4 决策含机制级 I4 动作清单（文件/机制/deadline 来源/测试放置/门禁测试扩展/known-gaps 处置），无「建议…或…」残留。
- watch 决策（R-2-2）与 not-applicable-confirmed 决策（AskOracleExecutor、R-4-1）均写明后续触发条件。
- 公共 API 触碰项：44 条裁决零「人工确认」标记（全部模块内契约变更或声明性改动，跨模块公共 API 面未触碰——详见各条 I4 动作清单）。
- 交叉核对：本表 44 == red-list §1 44（门禁 39 + 探查 5）；gate-gaps 更新后 = 39 + 3 登记 = 42 条，与 §1 汇总一致；I4 plan 可消费性核对通过（每族 fix-I4 动作可映射至 I4 plan Phase 2/3/4/5）。
- gate-2 门禁测试 TABLE 硬编码 14 条不因本表登记变红（登记表外实例无 red 逻辑，中间态安全——见 I3 plan Phase 3 条）。

## 6. 独立子 agent 审查记录

- 审查轮次：1 轮 fresh session 独立审查（general，task `ses_00b341407ffes2gvzsuUlZ12x2`，review-only 禁改文件）。
- 审查维度：零悬挂（44 条计数/决策分布逐条核对）、证据真实性（R-2-1/2/3、R-4-1、R-5-1、gate-1、UpdateTodosExecutor、SingleTurnExecutor 全部 live 抽查）、机制级可执行性（fix-I4 动作清单是否含「或/如」残留）、公共 API 触碰、gate-gaps 登记合规、I4 可消费性（Phase 映射）。
- 审查发现与修订（2 Blocker + 1 Major + 4 Minor，全部已修订，见下）：
  - Blocker-1（R-2-3 deadline 来源对 daemon/scheduler 路径无落地路径）：修订前动作清单仅点名「同步更新 4 个调用点」，未裁决 `TaskDispatchCoordinator:196` 路径（daemon 构造链 `TeamTaskSchedulerDaemon:137` → `TaskDispatchCoordinator:40-44`，无 config 可达来源）的 deadline 值来源——**已修订**：deadline 唯一来源 = 新增 `DefaultAgentEngineConfig.memberExecTimeoutMs`，flow 与 daemon 两装配路径同源派生（见 §3 R-2-3 动作 (1)）。
  - Blocker-2（R-2-1 机制动作含「或/如」残留且对 void listener 不可机械执行）：修订前「per-listener orTimeout 包装（或整体有界 join…）」「配置旋钮（如 …）经 setter/@InjectValue 注入」——`IInboundMessageListener.onInbound` 为 void 同步方法，`orTimeout` 无 future 可挂；**已修订**：唯一机制 = `CompletableFuture.runAsync(..., fanOutExecutor).orTimeout(dispatchTimeoutMs)`（mode-1）+ `sendAsync(...).orTimeout(dispatchTimeoutMs)`（mode-2），executor 来源 = 新增 `fanOutExecutor` setter（默认懒创建 daemon 线程池，close 时 shutdown），gateway 测试类 marker 锁定 = `orTimeout`（见 §3 R-2-1 动作 (b)(c)(e)）。
  - Major-1（R-2-1 mode-2 证据表述与 live 不符）：修订前称 `IMessageSender.send` 为「fire-and-forget default void 方法，无同步等待面」——live 实为 `FutureHelper.syncGet(sendAsync(...))`（`IMessageSender.java:26-28`）阻塞等待；DBMessageService 仅实现 sendAsync（persist-before-return at-least-once），未 override send → `ChannelMessageServiceImpl:222` 同步阻塞至 DB INSERT 完成，DB 挂起同样无限阻塞传输线程。**已修订**：证据列改写 + mode-2 裁决改为「send 改 sendAsync + orTimeout 有界化」（见 §3 R-2-1 证据/动作 (b)）。
  - Minor-1（R-2-3「从 flow 级配置派生」措辞自相矛盾 + 无 live 锚点）：orchestrator 现有 4 个构造（:205-268）均无 config 参数——已修订为「新增 `DefaultAgentEngineConfig.memberExecTimeoutMs` 唯一配置源 + orchestrator 字段/setter 派生」的具体描述。
  - Minor-2（两处行号 off-by-range）：forkSession 注册点实为 `:647`（原 :615-630 区间外）、AgentExecutorResolver timeoutExecutor 实为 :202（原 :195-200）——已修正。
  - Minor-3（R-5-1「移除/替换」双述）：已单一化主路径措辞（移除 `--grep`，JS subject 前缀为唯一过滤）。
  - Minor-4（§6 占位）：本记录即填补。
- 审查结论：`revised` → 修订后 44 条零悬挂、机制级无二义、证据全部 live 复核一致，可交 I4 直接执行。

## 7. 复核刷新记录（Phase 1）

- 2026-08-12 live 复核全部 44 条 finding 证据：33 条 gate-1（find 命令 33 行）+ 5 条 gate-2（4 N/A + SingleTurn :29/:41）+ 1 条 gate-4（AskOracleExecutor fail-fast）+ 5 条探查 finding 全部成立。
- 复核刷新 1 处：R-2-1 `ChannelMessageServiceImpl` 首 commit 日期 = 2026-08-09（`7d084027e`），red-list 记 2026-08-08 为 plan 日期——已在本表 R-2-1 证据列注明，red-list 文件保持只读未改。
- 关键依赖面核实：gateway→agent 依赖方向（pom 复核）、plan/runtime 接线面（import 计数 1）、dnsResolver 支持点（JdkHttpClient 零消费 / ApacheHttpClientHelper :100-109 消费但非 nop-ai 依赖）、R-2-3 两入口行号（:305 / :513）——全部与 I3 plan Phase 1 预期一致。
