# 2507 调试子系统测试时间确定化（nop-xlang-debugger / nop-xlang-debug-web）

> Plan Status: completed
> Last Reviewed: 2026-11-06
> Source: 用户 2026-11-06 指令「所有测试必须不依赖敏感的时间等待」；`ai-dev/logs/2026/11-06.md`（同日以放宽预算处理 `TestReplService` / `TestDebugGatewayE2E` 偶发失败，与该指令相悖，由本 plan 重做）
> Related: `ai-dev/backlog/test-timing-determinism-roadmap.md`（WI-1）
> 编号说明：沿用仓库当前 4 位序列（最近为 2506）。
> Review: R1 对抗审查（独立子 agent）1 Blocker + 2 Major + 5 Minor，本稿逐条修订（F1 补齐墙钟 deadline 用例并把各 Phase 退出条件对齐 Goals 四项；F2 时间戳纳入 Phase 2；F3 规定按线程计数的可推进时钟作为进入循环的观测手段；F4 Phase 6 证据改为 grep 逐处清单；F5 补调度器待执行任务可见与异常收容、时钟初值；F6 冻结时钟风险入规则；F7 写明负载命令；F8 模板补齐）。R2 复审：F1–F8 均确认解决；新增 N1（验证命令补项目本地仓库参数）与 N2–N6（工作线程化、读取阈值依据、规则措辞、单一推进入口、负载进程回收）已修订；另补 `TestLeaseLifecycle` 两个靠真实租约到期的用例。R3 确认：N1–N6 全部解决、新增两条机制成立，无 Blocker / Major，判定可执行（同一独立子 agent 三轮，作者与审查者达成共识）。
> 验证环境：本 plan 所有 mvn 调用均带 `-Dmaven.repo.local=$PWD/.m2-repo`（与 `ai-dev/logs/2026/11-06.md` 的基线一致；`.m2-repo/` 不入库，仓库无 `.mvn/maven.config` 默认值），下文命令中以 `<repo>` 代指该参数。

## Purpose

把 nop-xlang-debugger 与 nop-xlang-debug-web 两个模块的测试，从「靠真实时间流逝与固定等待」改为「测试显式推进虚拟时间、跨线程交接显式同步」，使结果与机器负载无关；同时把「测试不得依赖敏感时间等待」写进平台测试规范。

## Current Baseline

- 平台已有可控时钟：`CoreMetrics.currentTimeMillis()` 读 `CoreMetrics.registerClock(IClock)` 注册的时钟（`JsDate` 已经它取时）；`nop-sys-dao`、`nop-biz-auth-core` 测试源各有一个模块内 `MockClock implements IClock` 先例（初值取注册时的真实当前时间）。平台没有可手动推进的 `ScheduledExecutorService` 测试实现。`CoreMetrics` 时钟是 JVM 全局静态量：注册冻结时钟后，凡超时基于 CoreMetrics 的代码（如 `FutureHelper.waitUntil`）都失去超时能力。
- 根 pom surefire 为 `parallel=classes`、`threadCount=1`、`reuseForks=true`：同一 JVM 内一次只跑一个测试类；两模块都不依赖 `nop-autotest-junit`。
- 两模块生产代码中决定行为的时间读取直接用 `System.currentTimeMillis()`，不经 CoreMetrics：
  - `XLangDebugger`：`renewLease`、`expireLease`、`leaseExpired`（挂起线程按 `monitorWaitInterval` 唤醒复查该谓词）。
  - `ThreadLocalReplDeadlineChannel.checkDeadline`（每个融合执行器入口读一次时钟）。
  - `ReplService`：编译 deadline、执行 deadline。
  - `DebugWebSocketHandler`：watch 求值 deadline。
  - `DebugSessionBinder.renew`：续期限频。
  - `GatewayNotifierBridge`：日志条目 timestamp（被 `TestDebugLogPipe` 断言）。
- `DebugGatewayRuntime` 构造器已支持注入 `ScheduledExecutorService`；宽限销毁（`DebugSessionBinder`）、keepalive（`DebugWebSocketHandler`）、日志定时 flush（`DebugLogPipe`）都经它调度，三者回调最终都落到非阻塞的 `AsyncOutbound.offer`。`AsyncOutbound` 用自己的出站线程投递帧，不经该调度器。测试基座 `AbstractDebugGatewayTest` 目前用默认真实定时器。
- 依赖真实时间的测试（2026-11-06 按行核对，含 R1 审查补充）：
  - nop-xlang-debugger
    - `TestLeaseLifecycle`：两次 `sleep(250)` 判 300ms 租约内仍挂起（:133/:136）；`testLeaseExpiryReleasesThreadAndFiresEventOnce` 与 `testDualSessionLeaseIsolation` 续租 200ms 后靠真实时间到期放行（:155/:408）；`sleep(200)` 等租约过期（:303）；租约截止时间与墙钟比较（:486）。
    - `TestReplDeadline`：`testWhileTrueInterruptedByDeadline`、`testCancelErrorPiercesScriptCatch`、`testDestroyedBranchStillCancels` 均 `begin(墙钟 + 300)` 后跑 `while(true){}`，靠真实时间到达 deadline；取消用例 `sleep(200)` 后 cancel（:112），worker 若尚未 `begin` 则旗标被复位、死循环不退出。
    - `TestRemoteDebugger` / `TestXLangDebugger` 的 `assertBreakAt`：未挂起则 `sleep(100)` / `sleep(1000)` 后断言。
    - `TestSessionIsolation`：`sleep(50)` 作创建线程与关停之间的同步（:205）。
  - nop-xlang-debug-web
    - `TestReplService`：`testExecuteDeadlineInterruptsInfiniteLoop` 用 300ms 墙钟 deadline 打断死循环并断言耗时 < 10s；`testSuspendTimeCountsTowardDeadline` 用 2000ms 墙钟 deadline + 按挂起时刻睡 2200ms。
    - `TestDebugGatewayE2E`：`testFullDebugFlowSequence` 以租约截止时间与墙钟比较（:82）；真实 200ms 宽限（宽限销毁、停滞客户端两用例）；宽限 2000ms + `sleep(300)`（多标签页）；真实 50ms keepalive；宽限 2000ms + `sleep(150)` / `sleep(2200)`（宽限内 rebind）；宽限 120ms + `sleep(200)`（宽限销毁 churn）；以耗时 < 2s 判别事件出站是否异步。
    - `TestDebugLogPipe`：真实 50ms 定时 flush；时间戳按墙钟区间断言（:64）。
- 已观测的不稳定：`TestDebugGatewayE2E.testMultiTabLastConnectionTriggersGrace` 无负载单跑 3 次挂 1 次；`TestReplService.testSuspendTimeCountsTowardDeadline` 在 `-T 1C` 全量下失败、单跑通过。
- 全 reactor 基线：`./mvnw <repo> test -T 1C -fae` 26447 用例 0 失败（2026-11-06 17:25）。

## Goals

- 两模块测试中不再出现以下四类（下称「四类依赖」）：(1) 固定 sleep 用作同步或等待时间流逝；(2) 墙钟 deadline / TTL 作被测条件；(3) 真实毫秒定时器作被测条件；(4) 以耗时阈值判定通过与否。
- 时间驱动的行为（租约、deadline、宽限、keepalive、定时 flush、续期限频、日志时间戳）在测试中由可推进时钟与手动调度器驱动。
- 跨线程交接改为显式同步（latch / join / 按线程的时钟读取计数）或仅作挂死保护的条件等待。
- `docs-for-ai/02-core-guides/testing.md` 写明规则与允许形态，修正其中把 sleep 当同步手段的旧条目。

## Non-Goals

- 不改两模块生产行为语义：默认时钟与默认定时器不变，只把时间读取改为经 CoreMetrics。
- 不处理其他模块（roadmap 后续 WI 承接）。
- 不抽共享测试工具模块（本 plan 在模块测试源内落地；归并见 roadmap WI-2）。
- 不改生产实现内部的轮询（如挂起线程按 `monitorWaitInterval` 复查谓词），测试侧以条件等待观测其结果。

## Scope

### In Scope

- Current Baseline 所列生产时间读取点（含 `GatewayNotifierBridge` 日志 timestamp）改经 `CoreMetrics.currentTimeMillis()`。
- 两模块测试源：可推进时钟、手动调度器及其自测；Current Baseline 所列全部测试的改造。
- `docs-for-ai/02-core-guides/testing.md` 规则条目。

### Out Of Scope

- 其他模块的测试；CI 守卫脚本（roadmap WI-3）。
- 审计耗时、`ReplService` 审计事件 timestamp、`AsyncOutbound` 告警限频与出站线程空闲退出的时间读取：只被值无关断言消费（如 `durationMs >= 0`、timestamp 非空），测试结论不取决于其具体取值；最后一项是出站线程的真实生命周期。

## Execution Plan

### Phase 1 - 规则裁定与入册

Status: completed
Targets: `docs-for-ai/02-core-guides/testing.md`

- Item Types: `Decision`, `Fix`

- [x] Decision：「敏感时间等待」口径——测试通过与否不得取决于真实时间流逝的长短，禁止 Goals 所列四类依赖。允许：只作挂死保护、不承载语义的超时上限（条件等待、join；其内部对墙钟的上限比较不属于被测条件）；被测试主动中断的模拟耗时；生产实现内部的轮询；对「在两次墙钟读数之间取得的值」做区间检查。除此之外，墙钟比较不得作为断言或被测条件。
- [x] Decision：冻结时钟使用约束——可推进时钟逐测试注册、`@AfterEach` 恢复 `CoreMetrics.defaultClock()`；冻结期间凡超时基于 CoreMetrics 的路径必须由测试显式推进，否则会退化为无限等待。
- [x] Fix：testing.md 写入上述规则与推荐写法（`CoreMetrics.registerClock` + 可推进时钟、注入调度器、latch / 条件等待 / 按线程时钟读取计数）；修正现有以短 sleep / 短上限自旋等待线程启动的条目（:490-491）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] testing.md 有独立条目陈述四类禁止项、允许形态与冻结时钟约束；全文不再推荐以 sleep 作同步。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 errors。
- [x] 本 Phase 即 owner-doc 更新（`docs-for-ai/02-core-guides/testing.md`）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 生产时间读取收敛到 CoreMetrics

Status: completed
Targets: `nop-dev-tools/nop-xlang-debugger`（`XLangDebugger`、`ThreadLocalReplDeadlineChannel`）、`nop-dev-tools/nop-xlang-debug-web`（`ReplService`、`DebugWebSocketHandler`、`DebugSessionBinder`、`GatewayNotifierBridge`）

- Item Types: `Fix`

- [x] Current Baseline 所列时间读取改经 `CoreMetrics.currentTimeMillis()`。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 两模块 main 源 `grep -rn "System.currentTimeMillis" <module>/src/main` 的剩余命中逐处列入 logs，且每处都属 Out Of Scope 所列类别。
- [x] 默认时钟下行为不变：两模块既有测试全绿。
- [x] No new test required：仅替换时钟来源，可控性由 Phase 4 / 5 改造后的测试覆盖。
- [x] No owner-doc update required：`ai-dev/design/xlang-web-debugger/02-lease-lifecycle.md` 约定的是「服务端 epoch ms」语义，不约定时间来源实现。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 确定性时间测试工具

Status: completed
Targets: 两模块 `src/test/java`

- Item Types: `Feature`

- [x] 可推进时钟：实现 `IClock`；初值为构造时的真实当前时间（对齐 `MockClock.now()` 先例），只在测试显式推进时前进；按线程统计读取次数（供测试确认某工作线程已经过 deadline 检查入口）。
- [x] 手动调度器：实现 `ScheduledExecutorService`，持有同一个可推进时钟实例（单一虚拟时间线）；推进入口只有一个——调度器的推进操作同时推进时钟、并在推进线程上触发到期任务（注入了调度器的测试一律经它推进；只用时钟的测试直接推进时钟）；一次性 / 固定延迟 / 固定速率任务按虚拟时间到期，按到期时刻、同刻按提交序执行；可查询待执行任务；任务异常按执行器契约收容进对应 future 并留存供断言，推进本身不因任务异常抛出；支持取消与关停；不支持的方法显式抛 `UnsupportedOperationException`。
- [x] 两工具各自的单元测试。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 自测覆盖并通过：时钟推进前不变、按线程读取计数；调度器推进前不执行、到期序与同刻提交序、固定延迟与固定速率的重排、取消后不执行、待执行任务可见、任务异常进入 future 且推进不抛、关停后拒绝提交、不支持方法显式失败。
- [x] 无静默跳过：不支持的方法抛异常（Minimum Rules #24）。
- [x] No owner-doc update required：测试源内工具。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - nop-xlang-debugger 测试改造

Status: completed
Targets: `TestLeaseLifecycle`、`TestReplDeadline`、`TestRemoteDebugger`、`TestXLangDebugger`、`TestSessionIsolation`

- Item Types: `Fix`

- [x] `TestLeaseLifecycle`：租约窗口与过期（含 `testLeaseExpiryReleasesThreadAndFiresEventOnce`、`testDualSessionLeaseIsolation`）由可推进时钟驱动；「仍挂起」类断言须在挂起线程于推进后的时刻重新读过时钟（按线程读取计数增长）之后再做，否则断言不证明任何事；租约截止断言与可推进时钟比较。
- [x] `TestReplDeadline` 三个死循环用例：deadline 在主线程按可推进时钟算好再交给工作线程；等工作线程读取计数达到远大于编译期入口检查次数的阈值（死循环每轮回边读一次时钟，计数会持续增长；阈值取 10000 量级），证明推进发生在循环内，再推进时钟越过 deadline。
- [x] `TestReplDeadline` 取消用例：工作线程读取计数 ≥ 1（其首个动作是 `begin`，有读取即证明 `begin` 已执行、旗标不会再被复位）后再 cancel，去掉 sleep。
- [x] `assertBreakAt`（两处）：改为条件等待挂起。
- [x] `TestSessionIsolation`：创建线程确认已创建会话后再关停，去掉 sleep。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 本模块测试源无四类依赖；`grep -rnE "Thread\.sleep|currentTimeMillis|nanoTime" nop-dev-tools/nop-xlang-debugger/src/test` 剩余命中逐处列入 logs 并注明所属允许形态。
- [x] `./mvnw <repo> test -pl nop-dev-tools/nop-xlang-debugger` 全绿。
- [x] 变异探针：撤掉「续期推进租约」与「deadline / cancel 打断执行」两处被测行为，对应用例变红；恢复后变绿，且以读取校验确认被恢复的可执行行确实存在（guide「变异探针纪律」）。
- [x] No owner-doc update required。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 5 - nop-xlang-debug-web 测试改造

Status: completed
Targets: `AbstractDebugGatewayTest`、`TestReplService`、`TestDebugGatewayE2E`、`TestDebugLogPipe`

- Item Types: `Fix`

- [x] 测试基座注入手动调度器；宽限 / keepalive / 定时 flush 用例由测试推进虚拟时间触发。
- [x] `TestReplService` 死循环超时用例：`execute` 移到工作线程（它在调用线程上阻塞于循环，测试线程无法观测自身读取计数）；主线程等工作线程读取计数达到 Phase 4 同一阈值后推进时钟越过 deadline；去掉耗时阈值断言。
- [x] `TestReplService` 挂起计入 deadline 用例：挂起期间推进可推进时钟越过 deadline，去掉墙钟预算与 sleep。
- [x] `testFullDebugFlowSequence`：租约截止断言与可推进时钟比较。
- [x] 出站异步判别用例：以「停滞桩未放行时已挂起」的结构性判据替代耗时阈值。
- [x] 部分断连、宽限内 rebind、宽限销毁 churn 用例：以调度器中宽限任务的有无与推进后的会话状态判定，替代 sleep 观察窗。
- [x] `TestDebugLogPipe`：定时 flush 由调度器推进触发；日志时间戳断言为等于可推进时钟的当前值。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 本模块测试源无四类依赖；`grep -rnE "Thread\.sleep|currentTimeMillis|nanoTime" nop-dev-tools/nop-xlang-debug-web/src/test` 剩余命中逐处列入 logs 并注明所属允许形态。
- [x] `./mvnw <repo> test -pl nop-dev-tools/nop-xlang-debugger,nop-dev-tools/nop-xlang-debug-web` 全绿。
- [x] 接线验证：推进手动调度器即触发宽限销毁 / keepalive / 定时 flush；推进可推进时钟即触发 REPL 超时——确认注入点确实被生产路径调用，而非真实时间碰巧流逝。
- [x] 变异探针：撤掉「宽限到期销毁」「宽限内 rebind 取消销毁任务」「挂起计入 deadline」三处被测行为，对应用例变红；恢复后变绿并读取校验。
- [x] No owner-doc update required。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 6 - 稳定性证明与全量门

Status: completed
Targets: 两模块测试、全 reactor

- Item Types: `Proof`

- [x] 负载下连续 10 轮：每核一个忙循环进程制造满载并记录 PID（`pids=""; for i in $(seq $(sysctl -n hw.ncpu)); do sh -c 'while :; do :; done' & pids="$pids $!"; done`），期间 `./mvnw <repo> -o test -pl nop-dev-tools/nop-xlang-debugger,nop-dev-tools/nop-xlang-debug-web` 连续 10 轮全绿，结束后 `kill $pids` 并确认无残留。
- [x] 全 reactor `./mvnw <repo> test -T 1C -fae` 0 失败 0 错误。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 两项证据（命令、轮数、结果）写入 `ai-dev/logs/`。
- [x] No owner-doc update required：Proof 项。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] Current Baseline 所列依赖真实时间的测试全部改造，或按 Phase 1 口径逐处裁定为允许形态并记录
- [x] 两模块测试源无四类依赖（Phase 4 / 5 的 grep 清单）
- [x] 生产行为默认不变（两模块既有测试全绿）
- [x] 变异探针证据已记录
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项
- [x] testing.md 规则已入册
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] **Anti-Hollow Check**：注入的时钟 / 调度器在生产路径上确实被调用；无空方法体或静默跳过
- [x] `./mvnw <repo> compile`（由 test 生命周期传递满足）
- [x] `./mvnw <repo> test`（全 reactor）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2507-debug-subsystem-test-time-determinism.md --strict` 退出 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-dev-tools/nop-xlang-debug-web --severity high` 与 `--module nop-dev-tools/nop-xlang-debugger` 退出 0

## Deferred But Adjudicated

### 其余模块的时间敏感测试

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本 plan 收口面是调试子系统；其余模块由 roadmap WI-4 ~ WI-8 逐模块承接，口径沿用 Phase 1 入册的规则。
- Successor Required: yes
- Successor Path: `ai-dev/backlog/test-timing-determinism-roadmap.md`

## Non-Blocking Follow-ups

- 共享确定性时间测试工具的归属与归并（roadmap WI-2）。
- 新增违规守卫（roadmap WI-3）。

## Closure

Status Note: 两模块测试已不再依赖敏感时间等待：判定用时间读取收敛到 `CoreMetrics`，测试经可推进时钟与手动调度器显式驱动虚拟时间，跨线程交接改为读取计数 / latch / 条件等待；规则已入册 `docs-for-ai/02-core-guides/testing.md`，其余模块按 roadmap 逐模块承接。独立 closure audit 复核全部 Exit Criteria 与 Closure Gate 后 APPROVED，其提出的 F1（一条断言名不副实）已修订并用变异探针验证有效。
Completed: 2026-11-06

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（fresh session，与执行阶段不同 session；未参与实现）
- Evidence:
  - Phase 1：testing.md:503-536 新规则条目（四类禁止 / 允许形态 / 冻结时钟约束），全文仅 5 处 `sleep` 命中且无一处推荐其作同步（:490 限定为模拟耗时、:491 已改显式同步）；`check-doc-links.mjs --strict` 重跑 0 errors。
  - Phase 2：`git diff ba02ac4310 -- <两模块 src/main>` 恰为 9 处 `CoreMetrics` 替换 + 6 处 import（XLangDebugger:320/330/350、ThreadLocalReplDeadlineChannel:73、ReplService:131/233、DebugWebSocketHandler:638、DebugSessionBinder:220、GatewayNotifierBridge:146）；剩余 main 源命中与日志 Phase 2 条目逐条一致，全属 Out Of Scope 类别。
  - Phase 3：ManualClock（仅推进前进、按线程计数、peek 不计数）与 ManualScheduler（单线推进、到期序 / 同刻提交序、固定率与固定延迟重排、取消、待执行可见、异常入 future、关停拒绝、不支持方法显式抛）自测 4+4+12 全绿；`awaitTermination` 的惰性语义已在 javadoc 写明且生产侧无调用方。
  - Phase 4：TestLeaseLifecycle（虚拟时钟租约窗口 / 到期 / 双会话隔离 / 精确截止断言 + 推进后复查计数门）、TestReplDeadline（3 个死循环用例 LOOP_READS=10000 后推进；cancel 用例读数 ≥1 后 cancel）、assertBreakAt ×2 条件等待、TestSessionIsolation latch 化；本模块剩余命中全属允许形态并计入日志。
  - Phase 5：基座注入 ManualScheduler，生产三处调用点（DebugSessionBinder:281 宽限、DebugWebSocketHandler:119 keepalive、DebugLogPipe:76 定时 flush）均经 `runtime.timer()`；宽限 / 多标签页 / rebind / churn / keepalive / 定时 flush / 停滞客户端 / 挂起计入 deadline 全部改为推进驱动或结构性判据；接线四面对均有「推进前 / 推进后」断言。
  - Phase 6：16 核满载 10 轮全绿（`_tmp/p6-load-rounds.out`：rounds 1-10 exit=0，burners left 0）；F1 修订后全 reactor 复跑 `./mvnw <repo> test -T 1C -fae` → **26467 用例 0 失败 0 错误，456 模块全 SUCCESS 0 FAILURE 0 SKIPPED**（`_tmp/full-test-2026-11-06-run5.log`，22:32 完成）。
  - 变异探针 5 组均「撤掉被测行为即红、恢复即绿」并经日志留存：`probe1-mutated`（续期推进租约）、`probe2-mutated`（deadline / cancel 分支）、`probe3-mutated`（宽限到期销毁）、`probe4-mutated`（宽限内 rebind 取消销毁任务）、`probe5-mutated`（挂起计入 deadline）。全仓 `MUTATION-PROBE` 标记 0 命中，两模块 main 源相对提交版零差异。
  - Anti-Hollow：注入的调度器被生产三处调用点消费；9 处时钟读取均在判定路径上；`scan-hollow-implementations.mjs --severity high` 两模块均 exit 0（0 critical / high）。
  - 审计发现处理：F1（Minor，`testFullDebugFlowSequence` 未调 `useManualClock()`，租约截止断言名不副实）已修订——用例开头 `useManualClock()`、renew 前 `clock.advance(5000)`、断言改为精确等于「推进后虚拟时刻 + 策略 ttl」，并以变异探针验证（`renewLease` 改回真实时钟即红，差值 4837ms）；F2（`awaitTermination` javadoc 明确惰性语义）、F3（补全 debugger 侧剩余命中清单）、F4（Out Of Scope 措辞改为「只被值无关断言消费」）同批处理。
  - 文本一致性（Guide #19）：Plan Status、6 个 Phase Status、各自 Exit Criteria、Closure Gates 与本日志条目一致。
  - `check-plan-checklist.mjs --strict` 退出 0（关闭后复核）。
- 说明：审计在 F1 修订前给出 APPROVED-with-fix；F1 为测试断言力度问题（非产品缺陷），修订后两个模块与全 reactor 均复跑全绿，故 closure 成立。

Follow-up:

- 其余模块的时间敏感测试按 `ai-dev/backlog/test-timing-determinism-roadmap.md` WI-4 ~ WI-8 承接（不在本 plan scope）。
- WI-2（工具归并）、WI-3（新增违规守卫）为非阻塞治理项。
- 无剩余 plan-owned work。
