# 2479 xdbg item 27 并发域 remediation（item 24 审计 C-1~C-9 消化）

> Plan Status: active
> Last Reviewed: 2026-10-18
> Source: roadmap item 27（ai-dev/backlog/xlang-web-debugger-roadmap.md）；审计报告 ai-dev/audits/xlang-web-debugger/2026-10-18-item24-concurrency-deep-audit.md；设计契约 02-lease-lifecycle.md（D2）/03-sessionization.md（D3）/01-ws-protocol.md（D1）
> Related: plans/2471（item 12 WS 网关）、2472（item 13 日志管道）、2478（安全域 remediation，已完成 0ef850c1da）；flux 前端 remediation plan（另立）
> 文件行号以审计基线 84fea0df6c 标注（HEAD 经 2478 有小幅漂移，以类名+方法名定位为准）
> Draft Review: R1 agent_66dff92f（2B/7M 全消化后 active——B1 队列摘除延后到 onSessionDestroyed+drain 按 isClosed 丢帧 / B2 共享适配器不动改 per-connection 专职 drain / M1 targets 用 Set 去重 / M2 sink 函数化缺省直发保测试直构+断言反转入清单 / M3 awaitUntil 化显式清单 / M4 补 getScopeVariables+引擎直调判别 / M5 C-4 验收如实降级 / M6 C-8 listener 摘除法 / M7 MockWsSession latch 桩；F/O 项随执行落实）

## Purpose

消化 item 24 并发与会话隔离深度审计的 P0（C-1 出站活性：慢/死客户端可持锁冻结网关）与 P1（C-2 宽限销毁线程泄漏、C-3 多标签页事件单槽），并顺带消化同审计域的锁内复核类 P2（C-4/C-5/C-8/C-9）。收口后：任何客户端停滞不再阻塞引擎线程/共享定时器/网关锁；会话销毁无调度线程泄漏；多标签页事件对等送达；stepX/求值/断点表闸门与 D3/D2 定义语义对齐。

## Current Baseline

- 审计基线：@ 84fea0df6c。plan 2478 已改沙箱/门控/审计路径（XLangDebugger.eval 入口与 handler 调试方法区有小幅行漂移，与本 plan 并发面无语义交集）。
- **C-1（P0）**：引擎线程挂起授予前在锁外同步调 notifier（XLangDebugger checkSuspend/checkTempBreakpoint/checkHitBreakpoint/checkStepInto/Over/Out 5 处）→ `GatewayNotifierBridge.sendNotification` `synchronized(session)` + `session.sendMessage` 同步阻塞；适配器 `getBasicRemote().sendText` 无发送超时。交错：①停滞客户端使请求线程在 monitorWait **之前**阻塞（租约谓词未生效）；②宽限期满 `disposeByGrace`（持 B）→ `gracefulDispose`（持 M）→ `releaseAll` → `notifyLeaseExpired` 出站 → 持 M 阻塞 → 全网关冻结；③共享单线程 timer 的 keepalive `sendKeepAlive` 直接同步发送 → timer 冻结。
- **C-2（P1）**：`XLangDebugger` 每实例单线程调度器（cleanupThread，一次性 30s cleanup 任务）唯一 shutdown 在 `close()`；`gracefulDispose` 有意不调 close（D2 红线）且无其他路径关池 → 宽限销毁 churn 泄漏空闲 daemon 线程；`reset()` 同样不关。
- **C-3（P1）**：引擎 notifier 单槽；rebind `setNotifier(newBridge)` 覆盖；`bridges` 每 session 单 bridge；`fireSessionClosed` 单落点 → 多标签页原页签失联全部推送（前端审计 F-7 同源）。
- **C-4（P2）**：`releaseTarget` 的 `lastSuspendThread` 快照读与 `isSuspended()` 闸门均在 monitorLock 之外 → 快照-加锁窗口 stepX 静默丢失/陈旧 step 模式跨执行污染。
- **C-5（P2）**：`eval/getScopeVariables/getStackInfo/getFrameVariables` 在 `synchronized(thread)` 内不复核 `isSuspended()` → 放行瞬态撕裂读；网关"良性竞态"注释失真。
- **C-8（P2）**：`binder.renew/closeSession` 缺 `handle.isDestroyed()` 复核 → 直连 API dispose 与 WS renew 交错可 zombie 复活租约（shipped 装配锁串行不可达，直连 API 面 CONFIRMED）。
- **C-9（P2）**：`BreakpointManagerImpl.setBreakpoints` = clear + 逐条 add 无原子性 → 并发双页签产生并集，违背 D2 §3.4"后写覆盖"；`getBreakpoints` 跨 per-path map 不一致快照。
- **共享适配器约束（R1 B-2 裁定）**：`IWebSocketSession`（nop-graphql-core）与 quarkus/spring 适配器是 GraphQL WS 订阅通道共享基建——**本 plan 不改共享适配器**（慢订阅消费者 5s 断连属计划外产品行为变更）。debug 出站隔离全部在 debug-web 内闭环。
- **宽限路径事件可达性（R1 B-1 裁定）**：evt.leaseExpired/evt.logs/evt.sessionClosed 在 `handler.onClose` 之后发射（grace 到期 → gracefulDispose）——既有 pinned E2E（testGraceExpiryReleasesScriptAndDestroysSession、testMultiTabLastConnectionTriggersGrace）依赖"断连后出站仍可达"。**队列生命周期裁定：onClose 不摘队列；drain 时按 `session.isClosed()` 丢帧；队列在 `onSessionDestroyed`（fireSessionClosed 之后）移除**——保住 D2 §3.6 序列证据。
- Deferred（本 plan 不做）：C-6（共享取消旗标跨执行错杀——shipped 无 cancel 调用方）、C-7（条件断点重入嵌套挂起）、C-10（默认会话编译期挂起无兜底）、N-11/N-12（P3 观测性）。
- 既有测试面：debug-web 55 tests（Protocol 17/E2E 10/LogPipe 9/Repl 18/TaskFullChain 1）+ nop-xlang-debugger TestLeaseLifecycle/TestSessionIsolation 等。**注意**：Queue 化后部分同步时序断言需 awaitUntil 化改造（显式清单见 Phase 1）——语义保持、断言方式适配，不算语义回归。

## Goals

- **C-1（P0）出站队列化 + per-connection 专职 drain（R1 裁定形态）**：
  - 新增 `AsyncOutbound`（debug-web）：每连接一个——有界队列（256）+ **专职 daemon drain 线程**（首次 enqueue 惰性启动；空闲 30s 自退出、再 enqueue 再启动；close 后 drain 尽弃余帧）。**per-connection 线程隔离**：一个停滞客户端只阻塞自己的 drain 线程，无共享池饥饿问题（R1 B-2/M-7.2 连带消解——共享适配器与 socket 级超时均不需要）。
  - `GatewayNotifierBridge`：出站 sink 函数化——构造接收 `OutboundSink`（`Consumer<String>`；binder 装配 = 经 runtime `enqueueOutbound(session, json)`；**测试直构缺省 = 同步直发**，保持 TestDebugLogPipe 直构形态语义）+ 目标集合化（`Set<IWebSocketSession>` 去重——R1 M-1，自 rebind 不双发）。
  - `DebugWebSocketHandler.sendKeepAlive` → enqueueOutbound；**onClose 不摘队列**（B-1 裁定）；`binder.onSessionDestroyed` cleanup 末尾 removeOutbound。
  - 队列满丢帧 warn **按连接限频**（每连接每 5s 窗口最多 1 条——R1 O-3）；enqueueOutbound/removeOutbound 以 session 实例为键（R1 O-5）。
  - **残余如实登记**：drain 线程对半死 TCP 的 send 仍无 socket 级超时（共享适配器不可动）——停滞连接占用其专属 daemon 线程直至容器写超时/连接消亡，有界于连接数、不持任何网关锁；D2 §3.7 登记（audit P0 三交错——引擎线程/持 M/共享 timer——全部消除）。
- **C-2（P1）**：引擎 `dispose()` 原语（`cleanupThread.shutdownNow()` 幂等、不置 closed；cleanup 为一次性任务、dispose 后残留条目随实例 GC——R1 已核）；`gracefulDispose` 与 `reset()` 接线。
- **C-3（P1）**：fan-out 实现归 Phase 1（bridge 目标集合化 + rebind 复用既有 bridge 追加目标——logPipe 属 session 不属连接，复用无损（R1 已核）；`fireSessionClosed` 随 fan-out 广播）；Phase 2 仅承载验收测试；D2 additive 登记"多标签页对等送达，废除 last-rebind-wins"。
- **C-4（P2）**：`releaseTarget` 的快照读与闸门复核移入 monitorLock（**验收形态如实降级（R1 M-5）**：稳态负向语义对齐 + 结构断言（检查位于锁块内）+ TestLeaseLifecycle 组6 零回归；快照-加锁微秒窗口无确定性黑盒判别测试，不做 stress 假判别）。
- **C-5（P2）**：`eval/getScopeVariables/getStackInfo/getFrameVariables` 四处（R1 M-4 补 getScopeVariables）在 `synchronized(thread)` 内复核 `isSuspended()` → 抛 THREAD_NOT_SUSPENDED；判别测试 = **引擎直调**（resume 后条目仍在 map，直调 getFrameVariables：旧码返回脏数据/新码抛——网关预检路径修复前后同绿不判别）。
- **C-8（P2）**：`binder.renew/closeSession` 锁内 `handle.isDestroyed()` → NO_SESSION；判别测试 = **listener 摘除法**（`sessionManager.setSessionListener(null)` → destroySession（binding 不清、handle destroyed）→ renew：旧码 zombie 续租成功/新码 NO_SESSION——常规路径 requireBinding 先挡不判别（R1 M-6））。
- **C-9（P2）**：BreakpointManagerImpl 独立监视器锁；`breakpoints` 字段去 final、锁内整引用替换（读侧单引用即一致快照——R1 O-4 推荐形态）；add/removeBreakpoint 同锁（收口并发 add 交叠）。
- owner docs：D2 §3.7（出站拓扑改述：队列化 + per-connection drain + fan-out + 残余登记 + 响应/事件到达次序注记（R1 O-2））、D1 §3.5（队列上限丢弃语义 + 限频 warn）、D3（dispose 生命周期边界）。

## Non-Goals

- 共享 WS 适配器（quarkus JakartaWebSocketSession/spring SpringWebSocketSession）任何改动（R1 B-2 裁定；socket 级发送超时随之出 scope，残余登记 D2）。
- C-6/C-7/C-10 与 N-11/N-12（Deferred）；集群转发/事件重放；flux 前端。

## Scope

### In Scope

- `nop-dev-tools/nop-xlang-debug-web`：AsyncOutbound（新增）、GatewayNotifierBridge（sink 函数化 + 目标集合化）、DebugWebSocketHandler（keepalive enqueue；onClose 不摘队列）、DebugSessionBinder（rebind 复用 bridge/enqueue 接线/onSessionDestroyed removeOutbound/renew+closeSession destroyed 复核）、DebugGatewayRuntime（enqueueOutbound/removeOutbound + 限频 warn）。
- `nop-dev-tools/nop-xlang-debugger`：XLangDebugger（dispose/releaseTarget 锁内/四处 isSuspended 复核）、BreakpointManagerImpl（原子替换）、SessionDebugManager（gracefulDispose/reset 接线 dispose）。
- 测试：MockWsSession 扩桩（latch 可阻塞可中断 sendMessage + 计数观测——R1 M-7）；停滞客户端三场景；fan-out 对等；churn 相对计数守恒（settle 基线 → churn → 轮询回落基线）；C-4 结构断言 + 语义对齐；C-5 引擎直调；C-8 listener 摘除法；C-9 并发交叉。既有测试 awaitUntil 化改造（显式清单）。
- 文档：D2/D1/D3。

### Out Of Scope

- quarkus/spring 模块全部文件（含适配器）；flux 仓；nop-kernel/nop-xlang；C-6/C-7/C-10；N-11/N-12。

## Execution Plan

### Phase 1 - C-1 队列化 + C-3 fan-out 实现

Status: planned
Targets: `nop-dev-tools/nop-xlang-debug-web`（AsyncOutbound/runtime/bridge/handler/binder）

- Item Types: `Fix`（C-1 P0 live defect；C-3 P1 live defect 实现半程）

- [ ] 新增 AsyncOutbound：有界队列 256 + 专职 daemon drain（惰性启动/空闲 30s 自退出/close 尽弃）+ 满则丢帧 + 限频 warn（每连接 5s 窗口 1 条）
- [ ] DebugGatewayRuntime：`enqueueOutbound/removeOutbound`（session 实例为键）；`OutboundSink` 函数接口
- [ ] GatewayNotifierBridge：构造接收 OutboundSink（缺省同步直发——测试直构形态）；`Set<IWebSocketSession> targets` 去重 + add/removeTarget；sendNotification 构帧一次 → 对全部目标 sink
- [ ] DebugSessionBinder：open/rebind 统一装配队列 sink；rebind 复用既有 bridge addTarget（去重）；unbind removeTarget；`onSessionDestroyed` cleanup 末尾 removeOutbound；`fireSessionClosed` fan-out 广播
- [ ] DebugWebSocketHandler：sendKeepAlive → enqueueOutbound；onClose 不摘队列（B-1 裁定，注释登记裁定）
- [ ] MockWsSession 扩桩：可编程 latch 阻塞 sendMessage（**可中断**——tearDown shutdownNow 能解除）+ 阻塞计数观测
- [ ] 既有测试 awaitUntil 化改造（语义保持、显式清单）：TestDebugGatewayE2E.testFullDebugFlowSequence（close 后 sessionClosed 断言）、testServerShutdownBroadcastsReason、testMultiTabLastConnectionTriggersGrace（轻）；TestDebugLogPipe.testDestroyFlushBeforeSessionClosed、testLogsAndSuspendedShareOutboundOrder
- [ ] 既有断言语义反转（C-3 预期行为变更）：TestDebugLogPipe.testRebindRewiresPipeSink"旧连接不应再收"→ 双页签同收（fan-out 语义）
- [ ] 新增测试：停滞客户端三场景（①断点命中 notifySuspend 即返不阻塞请求线程（awaitUntil isSuspended 判定——R1 已核确定性）；②gracefulDispose 持 M 路径不冻结其他会话操作；③停滞连接不冻结他连接出站——健康连接 ping/事件照达）；fan-out 对等（双连接同收 evt.suspended + sessionClosed 广播）；移除接线变异探针（bridge 改回直发 → 停滞测试红；恢复后 grep 断言目标行）
- [ ] **判别力证据**：上述变异红 + 恢复绿记录入 plan

Exit Criteria:

- [ ] 停滞客户端三场景测试绿 + 变异探针红→绿证据入档
- [ ] **端到端验证**：断点命中 → evt.suspended 经队列 → 双 MockWsSession 收帧（含断连后 grace 事件可达——pinned 序列保持）
- [ ] 语义保持：改造清单 5 处 + 反转 1 处全绿；debug-web 全套绿（`-pl` 即可，本 Phase 未动 nop-xlang）
- [ ] **无静默跳过**：队列满丢弃有限频 warn（可观测）
- [ ] `ai-dev/logs/` 条目已更新；owner docs 归 Phase 3

### Phase 2 - C-2 dispose + 闸门复核（C-4/C-5/C-8/C-9）

Status: planned
Targets: `nop-dev-tools/nop-xlang-debugger`（XLangDebugger/BreakpointManagerImpl/SessionDebugManager）、`nop-dev-tools/nop-xlang-debug-web`（DebugSessionBinder）

- Item Types: `Fix`（C-2/C-3 P1 live defect 收口；C-4/5/8/9 P2 加固）

- [ ] XLangDebugger.`dispose()`：cleanupThread.shutdownNow() 幂等、不置 closed；`gracefulDispose` 与 `reset()` 接线
- [ ] C-4：releaseTarget 快照读 + isSuspended 复核移入 monitorLock（语义对齐 releaseThread；**验收 = 结构断言 + TestLeaseLifecycle 组6 零回归**——R1 M-5 降级裁定，不做假判别 stress）
- [ ] C-5：eval/getScopeVariables/getStackInfo/getFrameVariables 四处锁内 isSuspended 复核 → THREAD_NOT_SUSPENDED；勘正网关"良性竞态"注释为引擎兜底描述
- [ ] C-8：binder.renew/closeSession 锁内 handle.isDestroyed() → NO_SESSION
- [ ] C-9：BreakpointManagerImpl 独立锁；breakpoints 去 final 整引用替换；add/removeBreakpoint 同锁
- [ ] 回归测试：churn 相对计数守恒（settle 基线 → N 轮 gracefulDispose → 轮询回落基线）；dispose 接线变异探针（移除 → churn 红，恢复 grep 断言）；fan-out 验收（双连接 evt.suspended/sessionClosed 对等——C-3 收口验收）；C-5 引擎直调判别（resume 后直调 getFrameVariables：旧码脏数据/新码抛）；C-8 listener 摘除判别（destroyed+binding 在场 → renew 旧码成功/新码 NO_SESSION）；C-9 并发双线程 setBreakpoints 交叉 → 终态为任一完整意图（非并集）；TestLeaseLifecycle/TestSessionIsolation 零回归
- [ ] 全部新增公共行为有测试（Test-Mandated）

Exit Criteria:

- [ ] C-2 churn 守恒 + 变异证据；C-3 fan-out 对等验收绿
- [ ] C-4 结构断言 + 组6 零回归；C-5/C-8/C-9 判别测试绿（含变异/构造法说明）
- [ ] **接线验证**：dispose 被 gracefulDispose 真实调用（变异探针）
- [ ] `./mvnw test -pl nop-dev-tools/nop-xlang-debug-web -am` 绿（含 task 全链）
- [ ] owner docs 更新归 Phase 3（dispose 语义/THREAD_NOT_SUSPENDED 面涉 D3/D1——R1 F-2 补登记）
- [ ] `ai-dev/logs/` 条目已更新

### Phase 3 - owner docs 同步

Status: planned
Targets: `ai-dev/design/xlang-web-debugger/02-lease-lifecycle.md`、`01-ws-protocol.md`、`03-sessionization.md`

- Item Types: `Fix`（owner-doc 与 live baseline 对齐）

- [ ] D2 §3.7：出站拓扑改述（三写者直发 → per-connection 队列化 + 专职 drain + fan-out）；**残余登记**（半死 TCP 占用专属 drain 线程直至容器写超时——不持网关锁、有界于连接数）；响应/事件到达次序注记（响应同步直发 happens-after enqueue，D1 §3.9 close 响应先于 sessionClosed 序列不受影响——R1 O-2）；多标签页对等送达裁定（废除 last-rebind-wins，C-3/F-7 销案）
- [ ] D1 §3.5：best-effort 明示队列上限 256 + 限频 warn；dispose 不置 closed 的生命周期边界核对 D3
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 0 errors
- [ ] `ai-dev/logs/` 条目已更新

Exit Criteria:

- [ ] 文档与 live 一致（fan-out/队列上限/残余逐字核对）
- [ ] doc-links --strict exit 0

## Closure Gates

- [ ] C-1（P0）停滞客户端三场景 + 变异判别证据入档
- [ ] C-2/C-3（P1）落地且有守恒/对等测试
- [ ] C-4/5/8/9（P2）落地且验收形态如实（结构断言/直调/listener 摘除法）
- [ ] 既有测试面全绿：`./mvnw test -pl nop-dev-tools/nop-xlang-debug-web -am`（含 task 全链）
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect（C-6/C-7/C-10/N-11/12 按 Deferred But Adjudicated 登记 successor）
- [ ] owner docs（D2/D1/D3）同步且 doc-links --strict 0 errors
- [ ] 独立子 agent closure-audit 完成并记录证据（含 Anti-Hollow：AsyncOutbound 真实被 bridge/keepalive 接线、dispose 真实被 gracefulDispose 接线、drain 线程上无网关锁获取）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-dev-tools/nop-xlang-debug-web --severity high` exit 0

## Deferred But Adjudicated

### C-6：ThreadLocalReplDeadlineChannel 共享取消旗标跨执行错杀/抹除

- Classification: `watch-only residual`
- Why Not Blocking Closure: 一期无 HTTP cancel 端点——shipped 装配无生产 cancel 调用方；D6 §3.3 已声明共享旗标为有意裁定；修复需 per-execution 取消句柄 + D6 裁定修订
- Successor Required: `yes`
- Successor Path: follow-up"per-execution 取消句柄"（与 REPL cancel API 设计合并；开放 cancel API 则升级前置必修）

### C-7：条件断点/logpoint 求值经函数边界重入嵌套挂起

- Classification: `watch-only residual`
- Why Not Blocking Closure: 继承全局 executor 现状、会话化不放大（审计原话）；死锁已 REFUTED；修复为 engine-ext additive 需 D1/D3 语义登记
- Successor Required: `yes`
- Successor Path: follow-up"条件求值重入守卫"（engine-ext additive + D3 §3.7 登记）

### C-10：默认会话 deadline=0 编译期挂起无租约兜底

- Classification: `watch-only residual`
- Why Not Blocking Closure: 前置 = IDEA 断点与 Web compile 编译期求值面相交（窄场景）；IDEA 侧可见可 resume（审计缓解）；修复需语义裁定 + D3 §3.2 修订
- Successor Required: `yes`
- Successor Path: follow-up（与 C-6/C-7 同属 REPL 演进批）

### N-11/N-12（P3 观测性）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 审计定性"不涉正确性/登记备查"
- Successor Required: `no`
- Successor Path: 观测性 backlog

## Non-Blocking Follow-ups

- socket 级发送超时（需 debug 专属适配器或共享适配器裁定变更——若未来 GraphQL 侧接受超时语义可重启）
- 队列容量/drain 空闲超时配置化（当前设计常量，裁决登记于 D2/D1）
- 事件聚合（N-11 单元素 leaseExpired 合帧）随观测性 backlog
