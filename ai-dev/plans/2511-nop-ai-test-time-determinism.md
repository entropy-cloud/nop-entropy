# 2511 nop-ai 测试时间确定化（agent / core / gateway / shell / toolkit）

> Plan Status: completed
> Last Reviewed: 2026-11-07
> Source: `ai-dev/backlog/test-timing-determinism-roadmap.md` WI-5；口径沿用 plan 2507 Phase 1 入册规则与 plan 2510 的执行范式（范本 + 分批实现子 agent + 变异探针 + 满载证明）
> Related: `ai-dev/plans/2510-nop-stream-test-time-determinism.md`（范式先例，completed）；`ai-dev/plans/2508-shared-deterministic-time-test-tools.md`（ManualClock/ManualScheduler owner）；`ai-dev/plans/2509-test-time-waits-guard.md`（守卫与基线消号纪律）
> 编号说明：沿用仓库当前 4 位序列（最近为 2510）。
> Review: R1 对抗审查（独立子 agent，现场复跑守卫与逐行核对）判定 READY，7 Minor 本稿全改（FutureHelper 归属 nop-api-core、seenInputKeys 消费测试更名 TestSeenInputKeysRoundTrip、3 处 SIM 均非中断形态须全改、shell 无真实子进程措辞收窄、breaker 修复出处锚点、探针数一致性确认、<repo> 展开显式化）。
> 验证环境：所有 mvn 命令带 `-Dmaven.repo.local=$PWD/.m2-repo`，下文以 `<repo>` 代指。
> 跨平台约束（用户 2026-11-07 指令）：全部改造为纯 JDK / JUnit 构造；sandbox 相关测试不得引入 Docker/OS 专有假设的新依赖（既有 gated 面保持既有形态）。
> 范围注记：roadmap WI-5 内容列名为「nop-ai（agent / core / gateway）」；本 plan 按子系统口径把同属 nop-ai 的 shell / toolkit 一并纳入（同族范式、同批消号更省），WI-8 的剩余面相应缩小——此扩入在 roadmap WI-5 行的 Plan 列以本 plan 记录。

## Purpose

把 nop-ai 子系统 5 个模块（agent / core / gateway / shell / toolkit）的单元测试从「靠真实时间流逝与固定等待」改为「测试显式驱动虚拟时间、跨线程交接显式同步」，并把守卫基线的 nop-ai 切面消号至具名裁定残余。

## Current Baseline

- 守卫基线（`ai-dev/tools/test-time-waits-baseline.json`，plan 2510 收口后）nop-ai 切面：**109 处 / 5 模块**——agent 66（SLEEP_THEN_ASSERT 17 / POLL_LOOP 15 / SIMULATED_WORK 2 / OTHER 32）、core 15（STA 13 / OTHER 2）、shell 12（POLL 7 / OTHER 4 / SIM 1）、gateway 11（STA 5 / OTHER 5 / POLL 1）、toolkit 5（POLL 3 / OTHER 2）。最大文件：TestThresholdBreaker 11（core）、TestTakeoverLockHeartbeatRenewal 7（agent）、TestDBMessageService 6、ShellConcurrencyEdgeCaseTest 7（shell）、TestActorMailboxConsumption 4。
- 生产时间读取面（main 源 `System.currentTimeMillis/nanoTime` 共 7 文件；另有 57 文件已走 CoreMetrics）：
  - **迁移候选（判定类）**：`AgentSession:63`（seenInputKeys 去重时间戳，直接消费测试 = `TestSeenInputKeysRoundTrip`；变异探针须打在该测试而非 TestAgentSession）；`JqToolExecutor:64`（工具调用 deadline 判定）；`DockerBashSandbox:303` / `HostBashSandbox:170`（进度发布节流，shell 测试消费进度流）。
  - **保留（裁定）**：`DBSessionStore:398`（SQL 列时间戳，数据值）；`DefaultWaitCoordinator` / `ProviderFailoverQueue`（仅 javadoc 提及反模式，实现已走 CoreMetrics / 可注入 seam，合规）。
- 平台既有事实：nop-ai-agent 依赖 nop-commons（传递）；测试以 plain JUnit 5 为主；`CoreMetrics.registerClock` + `ManualClock`（owner test-jar `io.nop.commons.test.time`）消费方式同 plan 2508；TestAwait 在 nop-ai 无既有副本，条件等待直接用 core canonical（`io.nop.stream.core.testsupport.TestAwait` 属 nop-stream 面——**nop-ai 不依赖 nop-stream**，不得引入该依赖；nop-ai 条件等待用 `FutureHelper.waitUntil` / 自写小工具 / latch，见改造手册）。
- 全 reactor 基线：plan 2510 收口时 456 模块 BUILD SUCCESS 0 失败 0 错误。
- 守卫消号纪律：本 plan 收口对 nop-ai 切面 `--update` 同批减基线。

## Goals

- nop-ai 5 模块测试源四类禁止写法清零（残余逐处具名裁定，总降幅 ≥ 90%，即 109 → ≤ 11）。
- 判定性 main 时间读取迁移 CoreMetrics（默认时钟行为不变）。
- 跨线程交接显式同步；「仍未发生」断言先取重新求值证据。
- 全 reactor 测试保持 0 失败 0 错误。

## Non-Goals

- 不改 nop-ai 生产行为语义（默认时钟/定时器不变，仅判定性读取迁移 CoreMetrics）。
- 不处理 nop-ai 之外模块（WI-6 ~ WI-8 承接）。
- 不重构 nop-ai 架构（agent 引擎、可靠性组件等结构性治理不在本 plan）。

## 改造手册（沿用 plan 2510 判定表，nop-ai 适配）

- 异步完成等待 → `CountDownLatch` / `Future.get(timeout)` / `FutureHelper.waitUntil`（**nop-api-core** 的 `io.nop.api.core.util.FutureHelper`，非 nop-commons；nop-ai-core/toolkit 直依、agent 经 core 传递，无需加 pom）/ 简洁条件轮询辅助（模块内测试源私有，超时仅挂死保护）。
- deadline / TTL / 冷却 / 心跳 / 限频语义 → 生产读取经 `CoreMetrics`；测试注册 `ManualClock`（`@BeforeEach` 注册、`@AfterEach` try/finally 恢复 `CoreMetrics.defaultClock()`），`advance()` 越过截止点；时间戳一律取虚拟时钟线（`peekMillis()` 相对量）。
- 「仍未发生」→ 读取计数（`clock.totalReads()`/`readsBy(thread)`）或消息/计数证据先行。
- 模拟耗时（SIMULATED_WORK）→ 仅测试主动中断形态允许；否则 latch。现有 3 处 SIM（TestMultiMemberFanOutEndToEnd supplyAsync 内 sleep(80) 正常完成、TestBackgroundRegistryJobSink、ShellIOTest:359 sleep(50) 后另线程 sendEof）**均非中断收尾形态，全部改 latch/条件等待，不得保留**。
- **不得引入 nop-stream 依赖**；条件等待工具就地最小化（nop-ai 测试源内私有 helper 或 FutureHelper）。
- `<repo>` = `-Dmaven.repo.local=$PWD/.m2-repo`（执行时逐命令展开，不得省略）。
- 外部进程面（shell sandbox、Docker gated）→ 既有形态保持；等待改条件等待；真实时长语义具名裁定。

## Execution Plan

### Phase 1 - 生产判定性时间读取收敛

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main`（AgentSession）、`nop-ai/nop-ai-toolkit/src/main`（JqToolExecutor、DockerBashSandbox、HostBashSandbox）

- Item Types: `Fix`

- [x] `AgentSession:63` / `JqToolExecutor:64` / `DockerBashSandbox:303` / `HostBashSandbox:170` → `CoreMetrics`（默认时钟行为不变）；`DBSessionStore:398` 保留并记录。
- [x] 消费 ManualClock 的模块 pom 加 `nop-commons` test-jar 测试依赖（按 Phase 2–4 实际消费面，硬前置）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 迁移点默认时钟行为不变（模块既有测试全绿）；裁定表写入 logs。
- [x] `./mvnw <repo> test -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-toolkit -am` 全绿。
- [x] `ai-dev/logs/2026/11-07.md` 已更新。

### Phase 2 - nop-ai-agent 测试改造（66 处）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test`（TestThresholdBreaker 11 在 core——不含；本模块最大 TestTakeoverLockHeartbeatRenewal 7、TestDBMessageService 6、TestActorMailboxConsumption 4、TestSteeringInjection 3、TestDbSessionTakeoverLock 3、TestAgentSession 3 等）

- Item Types: `Fix`

- [x] 全部基线文件逐文件改造（接管锁/心跳 TTL → 虚拟时钟；DB 轮询/消息队列等待 → 条件等待；actor mailbox 消费 → latch/条件等待；模拟耗时仅中断收尾形态保留）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 守卫 `--module nop-ai/nop-ai-agent` 残余逐处具名裁定（目标 ≤ 3）。
- [x] `./mvnw <repo> test -pl nop-ai/nop-ai-agent -am` 全绿。
- [x] 变异探针 ≥ 2 组（接管锁 TTL、心跳续期）撤行为即红、恢复 grep 校验后复跑绿。
- [x] `ai-dev/logs/2026/11-07.md` 已更新（per-file 账本指针）。

### Phase 3 - nop-ai-core + gateway 测试改造（26 处）

Status: completed
Targets: `nop-ai/nop-ai-core/src/test`（TestThresholdBreaker 11 等）、`nop-ai/nop-ai-gateway/src/test`（TestChatServiceFailoverAdapterStreaming 3 等）

- Item Types: `Fix`

- [x] core：ThresholdBreaker 冷却/开断窗口由虚拟时钟驱动（生产 `ThresholdBreaker:173/:276` 已读 `CoreMetrics.currentTimeMillis()`——2026-09-12 审计修复，见类 javadoc 与 ProviderFailoverQueue javadoc 交叉引用）；其余文件逐文件改造。
- [x] gateway：failover 流式、channel 消息服务、session store 等待全部显式同步/虚拟时钟化。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 两模块守卫切面残余逐处具名裁定（目标合计 ≤ 3）。
- [x] `./mvnw <repo> test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-gateway -am` 全绿。
- [x] 变异探针 ≥ 1 组（breaker 冷却开断）撤行为即红、恢复即绿并读取校验。
- [x] `ai-dev/logs/2026/11-07.md` 已更新。

### Phase 4 - nop-ai-shell + toolkit 测试改造（17 处）

Status: completed
Targets: `nop-ai/nop-ai-shell/src/test`（ShellConcurrencyEdgeCaseTest 7、ShellCommandExecutorTest 4、ShellIOTest 1）、`nop-ai/nop-ai-toolkit/src/test`（TestToolPathQueue 3、ToolManagerImplParallelTest 1、TestBashExecutionProgress 1）

- Item Types: `Fix`

- [x] shell：等待改条件等待（实测 shell 测试为 in-JVM 注册命令，**无真实子进程**；跨平台重点仅在 toolkit 的 Docker/Host sandbox gated 面——JDK 标准 API，无 OS 专有假设）；进度流节流经 Phase 1 迁移后可虚拟化（如测试需要）。
- [x] toolkit：工具路径队列、并行工具管理等待改条件等待/虚拟时钟。
- [x] Docker/Host sandbox 的 gated 真实环境面保持既有形态，具名裁定。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 两模块守卫切面残余逐处具名裁定（目标合计 ≤ 2）。
- [x] `./mvnw <repo> test -pl nop-ai/nop-ai-shell,nop-ai/nop-ai-toolkit -am` 全绿。
- [x] `ai-dev/logs/2026/11-07.md` 已更新。

### Phase 5 - 守卫基线消号与全量门

Status: completed
Targets: 守卫基线、全 reactor

- Item Types: `Proof`

- [x] 全仓 `--update` 消号（nop-ai 切面 109 → 具名残余）；全仓 check exit 0。
- [x] 满载稳定性证明：满载期间 `./mvnw <repo> -o test -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-core` 连续 2 轮全绿（burner 纯 Java 存 `_tmp/`，结束无残留）。
- [x] 全 reactor `./mvnw <repo> test -T 1C -fae` 0 失败 0 错误（第 3 轮 BUILD SUCCESS；前 2 轮暴露并修复 2 处先前批次遗留竞态：TestLeaseLifecycle、TestTaskManagerLivenessAndReporting）。
- [x] roadmap WI-5 → done；logs 收口条目。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 消号证据（前后计数）写入 logs；全仓 check exit 0。
- [x] 满载 2 轮证据写入 logs。
- [x] 全 reactor 证据写入 logs。
- [x] roadmap 状态块已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 5 模块测试源四类依赖清零（守卫残余 1 处具名裁定 + 4 处守卫不可见实时窗口诚实申报于 logs）
- [x] 判定性 main 时间读取已收敛 CoreMetrics（裁定表 + 默认时钟行为不变）
- [x] 守卫基线 nop-ai 切面消号完成（109→1，降幅 99.1%），全仓 check exit 0
- [x] 变异探针 ≥ 3 组证据齐全（撤行为即红、恢复 grep 校验、复跑绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] **Anti-Hollow Check**：虚拟时钟推进在生产判定路径被消费（探针即证明）；无空方法体/静默跳过；`scan-hollow-implementations.mjs --severity high` 在 nop-ai 有 4 处 **HEAD 既有**关键级发现（ReActAgentExecutor ×2 / PlanReplanner / NoOpProviderFailoverQueue——关键词误报与设计性 no-op，0 diff 未被本 plan 触碰），触碰文件**无新增**发现
- [x] `./mvnw <repo> compile`（由 test 生命周期传递满足）
- [x] `./mvnw <repo> test`（全 reactor）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2511-nop-ai-test-time-determinism.md --strict` 退出 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 errors
- [x] **跨平台核查**：改造面纯 JDK/JUnit；shell 子进程等待用 JDK 标准 API；sandbox gated 面保持既有形态；结论入 logs

## Deferred But Adjudicated

（本 plan 无 deferred 项。）

## Non-Blocking Follow-ups

- WI-6 ~ WI-8 各模块清欠（roadmap 承接）。

## Closure

Status Note: nop-ai 5 模块测试时间确定化完成：守卫切面 109→1（降幅 99.1%，唯一残余 SSE 发射延时具名裁定 + 另 4 处守卫不可见实时窗口诚实申报）；4 处生产判定读取迁移 CoreMetrics；3 组变异探针红→恢复→绿；满载 2 轮 + 全 reactor 456 模块 BUILD SUCCESS（期间修复 2 处先前批次遗留合跑竞态，断言均为加强而非削弱）。独立 closure audit APPROVED-WITH-FIX（1 Minor：hollow 门措辞与 live 证据不符——4 处 HEAD 既有误报被误写为 exit 0，已修正）。

Completed: 2026-11-07

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（fresh session，未参与实现）
- Evidence:
  - Phase 1：4 迁移文件 git diff 仅 CoreMetrics 迁移 + import；DBSessionStore:398 数据戳保留；5 pom test-jar 依赖齐备。
  - Phase 2–4：`--module nop-ai --json` 实测残余恰 1（TestResponsesDialectIntegration:382，in-code 注释具名裁定且实测直改即红）；抽查 3 测试（ThresholdBreaker 类级 ManualClock 同位 advance、TakeoverLockHeartbeatRenewal 全虚拟化精确虚拟时刻契约、ShellConcurrencyEdgeCaseTest latch + waitUntil 结构证据）断言无削弱。
  - 探针：3 组红（2/4/6 失败）→ 恢复 grep 零 PROBE 残留（现场确认仅域词 probe）→ 复跑绿（_tmp/wi5-probe{1,2,3}.log 10/6/26 全绿）。
  - Phase 5：基线数学精确（HEAD 282/109/173 → 174/1/173，非 nop-ai 零漂移）；满载 2 轮 3536 用例绿；全 reactor 第 3 轮 BUILD SUCCESS / 456 SUCCESS / 0 失败。
  - 遗留竞态修复复核：TestLeaseLifecycle 逐线程结果 + 按 id 判定（加强）；TestTaskManagerLivenessAndReporting 心跳等值条件重试（加强）。
  - 跨平台：nop-ai 测试 diff 零 ProcessBuilder/Runtime.exec/File.separator 新增；shell 为 in-JVM 命令。
  - 文本一致性：check-plan-checklist --strict exit 0；check-doc-links --strict 0 errors。
  - 审计发现处理：Minor（hollow 门措辞）已修正如上；2 Info（探针红相日志未留存——计数经绿相日志交叉一致；工作树未提交——本提交即收口提交）。
  - 审计判定：**APPROVED-WITH-FIX**（已修）。

Follow-up:

- WI-6 ~ WI-8 各模块清欠（roadmap 承接）。
- SSE 发射延时的生产 subscription-ready seam、orchestrator 总超时可注入 deadline 调度器 seam（out-of-scope improvement，后续 roadmap 评估）。
- 无剩余 plan-owned work。
