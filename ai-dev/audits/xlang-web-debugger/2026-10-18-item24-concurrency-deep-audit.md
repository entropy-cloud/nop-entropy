# XLang 在线调试 并发与会话隔离深度审计（roadmap item 24）

**日期**：2026-10-18
**审计人**：独立深审 subagent（非实现者；只读审计，本文档为唯一产出文件）
**基线**：branch `feat-xlang-web-debugger`，HEAD `84fea0df6c`
**范围**：`nop-dev-tools/nop-xlang-debugger`（引擎 + session 包）、`nop-dev-tools/nop-xlang-debug-web`（WS 网关）的全部 main 源码；出站适配器（nop-quarkus-web / nop-spring-web-starter）；并发测试证据面；设计一致性对照 D2（02-lease-lifecycle）/ D3（03-sessionization）/ D1（01-ws-protocol）。
**方法**：不变式闭环审计（每条不变式 → 找执行点 → 验证强制 → 找旁路）+ 对每个疑似缺陷构造具体交错序列（线程 A 做 X 时线程 B 做 Y），再回到实际锁/同步代码（monitorLock / binder·manager 监视器锁 / volatile / AtomicBoolean / ConcurrentHashMap）逐行 CONFIRM 或 REFUTE。共立项 ~15 个候选，13 项入档（1 项 REFUTED，其余 CONFIRMED 分级）。

---

## 一、锁序全景（审计底图）

实测锁获取序（全部单向，无回边）：

```
binder 监视器锁 B（DebugSessionBinder 全 public synchronized）
  → manager 监视器锁 M（SessionDebugManager synchronized(this)）
    → 引擎 monitorLock L（XLangDebugger.monitorLock / resumeCondition）
      → pipe 锁 P（DebugLogPipe.synchronized(lock)）
        → 出站 session 监视器 S（synchronized(IWebSocketSession wrapper)）

引擎线程（脚本/请求线程）：仅触 L（monitorWait）与 P（notifyLog）与 S（notifier 出站），不进 B/M。
定时器线程（单线程 timer）：grace 任务走 B→M→L→S；keepalive/flush 走 S 或 P→S。
```

- **死锁判定：REFUTED（见 N-13）**。S（wrapper 对象监视器）只被本代码在最内层使用，容器不感知该对象；未发现 S→B/M 的回边。
- **stall（无限期阻塞）判定：CONFIRMED，见 C-1**——阻塞≠死锁，但持 B/M 阻塞等价于网关级冻结。

---

## 二、不变式清单表

| # | 不变式 | 强制点（file:line） | 判定 |
|---|--------|--------------------|------|
| I1 | 挂起线程的租约到期必须放行而非永久阻塞（D2 §3.1） | `XLangDebugger.monitorWait` 循环谓词 `!closed && !localReleased && !leaseExpired()`，200ms 轮询（XLangDebugger.java:704-708） | **部分成立**：挂起等待窗口内成立（TestLeaseLifecycle 组2 钉住）；**挂起授予前的 notifier 发送阶段无此保障（C-1）**，条件求值阶段亦无（N-12） |
| I2 | 已销毁/销毁中/租约过期的新命中不挂起直接继续（D2 §3.1①②） | checkBreakpoint 前置判定（XLangDebugger.java:469-475）；路由层 destroyed 位（SessionRoutingExecutor.java:58-59）双层分工（D3 §3.4） | 成立（TestLeaseLifecycle 组5、TestSessionRoutingExecutor）；TOCTOU 边缘（grant① 过后 close）落 makeSuspendedThread 抛 ALREADY_CLOSED（XLangDebugger.java:746-747），属 abort 语义合规 |
| I3 | 出口判定顺序：局部放行/租约到期 优先于 closed（D3 §3.5） | monitorWait 出口（XLangDebugger.java:719-723） | 成立（TestLeaseLifecycle testResumeThenCloseIsNormalReturn / testStepReleaseThenCloseIsNormalReturn 钉住） |
| I4 | evt.resumed 与 evt.leaseExpired per-thread 互斥、恰一次 | releaseAllThreads 以 `isSuspended()` 为闸门且与 monitorWait 的 `setSuspended(false)` 同锁 L 串行化（XLangDebugger.java:264-282, 713-717）；per-thread 自放行事件（724-730） | 成立（TestLeaseLifecycle 组2/组4） |
| I5 | stepX 仅定向释放目标线程（D3 §3.6） | releaseTarget 置目标线程局部标志（XLangDebugger.java:189-204）；其余线程重查谓词继续等待 | **部分成立**：定向性成立（TestLeaseLifecycle 组6 钉住）；闸门检查在 L 外，留 C-4 竞态 |
| I6 | 会话标识透传：嵌套 action/函数调用路由不变（D3 §3.2） | ThreadLocal 标记（DebugSessionScope.java:74-85 save-restore）+ 函数边界重读全局外壳（ExecutableFunction.java:158 `XLang.getExecutor()` → XLang.java:52-53 → SessionRoutingExecutor.execute） | 成立；代价是条件/logpoint 求值重入 checkBreakpoint（C-7，D3 §3.7 预登记的审计点） |
| I7 | 已销毁句柄执行直通且保留取消保护（D3 §3.4 plainExecute 修订） | 路由 destroyed 分支 → DestroyedSessionExecutor 每入口 checkDeadline（DestroyedSessionExecutor.java:38-47） | 成立 |
| I8 | 多标签页共享租约：任一绑定连接 renew 推进全会话时钟（D2 §3.4） | binder.renew（按连接限流）→ debugger.renewLease 会话级 volatile deadline（DebugSessionBinder.java:210-227, XLangDebugger.java:304-306） | 租约面成立（TestDebugGatewayE2E testRenewRateLimitAndRebindExemption）；**事件通道违背多标签页对等性（C-3）** |
| I9 | 宽限销毁 = resume 语义：不置 closed、不抛错（D2 §3.3） | gracefulDispose 先 expireLease 后 releaseAll、不调 close（SessionDebugManager.java:208-225，顺序注释 200-207） | 成立（TestLeaseLifecycle 组4）；残留 C-2（cleanupThread 不关） |
| I10 | 断连/宽限/rebind 时序原子性（D2 §3.3/§3.5） | binder 全 public 方法 synchronized（DebugSessionBinder.java:46-49 声明）；disposeByGrace 空连接复查（270-272）；rebind 取消宽限任务（194） | 成立（TestDebugGatewayE2E testMultiTabLastConnectionTriggersGrace / testRebindWithinGraceWindowKeepsSession）；**直连 API 面留 C-8 残窗** |
| I11 | 会话注册表 create vs shutdown 无泄漏（D3 §3.5） | manager 同监视器锁 + ensureNotShutdown（SessionDebugManager.java:97-118, 231-264） | 成立（TestSessionIsolation.testCreateVsShutdownConcurrencyNoLeak 钉住） |
| I12 | REPL 取消保护 per-entry（while(true) 可取消，D6 §3.2） | 融合执行器每入口 checkDeadline（DebugExpressionExecutor.java:38-46）+ DestroyedSessionExecutor 同构 | 单执行内成立；**跨执行取消隔离不成立（C-6）** |
| I13 | evt.logs 先于 evt.sessionClosed；销毁后入队丢弃 | onSessionDestroyed 中 destroyFlush 前置（DebugSessionBinder.java:340-344）；pipe destroyed 位（DebugLogPipe.java:67-71, 97-108） | 成立（TestDebugLogPipe） |
| I14 | close 语义只中止"当时仍在挂起等待"的线程（D3 §3.5） | 出口判定（XLangDebugger.java:721-723）+ makeSuspendedThread closed 检查（746-747） | 成立 |
| I15 | 断点表并发操作按到达顺序后写覆盖（D2 §3.4"天然如此"前提） | BreakpointManagerImpl.setBreakpoints = clear + 逐条 add，**无原子性**（BreakpointManagerImpl.java:46-53） | **不成立（C-9）** |

---

## 三、发现清单

> 每条 = 严重级 + 判定 + 具体交错 + 证据 + 影响面 + 修复方向。路径缩写：`XDBG` = `nop-dev-tools/nop-xlang-debugger/src/main/java/io/nop/xlang/debugger`，`WEB` = `nop-dev-tools/nop-xlang-debug-web/src/main/java/io/nop/xlang/debug/web`。

---

### C-1 [P0] CONFIRMED — 引擎线程在挂起授予前的 notifier 同步 WS 发送无超时：慢/死客户端可长期占用请求线程，且发送可持有 manager/binder 锁与共享定时器，单客户端拖垮整个调试网关

**强制点缺失位置**：D2 红线"前端消失后端不永久阻塞"只在挂起等待循环内被强制（I1）。但引擎在进入 `monitorWait` **之前**于锁外同步调用 notifier：

- `XDBG/XLangDebugger.java:550-553`（checkSuspend：`notifier.notifySuspend` → `doSuspend`；checkTempBreakpoint 539-542、checkHitBreakpoint 621-624、checkStepInto/Over/Out 同构）；
- `WEB/GatewayNotifierBridge.java:186-203`（sendNotification：`synchronized(session)` → `session.sendMessage(json)`，同步阻塞）；
- 适配器无发送超时：`nop-quarkus-web/.../JakartaWebSocketSession.java:23-26` 与 `nop-spring-web-starter/.../SpringWebSocketSession.java` 均为 `session.getBasicRemote().sendText(message)`（JSR-356 blocking basic remote；全仓未调用任何 send-timeout / async API）。

**具体交错**：
1. 客户端 C 停止读 TCP（恶意不读 / 网线拔出半开连接）。脚本线程 T 命中断点 → `notifySuspend` → 持 S 阻塞在 `sendText`。此时 **T 尚未进入 monitorWait，租约 deadline 未开始裁决**（I1 的谓词在 doSuspend 之后才生效）→ T 的占用时长 = TCP 写超时（分钟级；半开连接下无界）。请求线程泄漏窗口成立，D2 §二"挂起原语已保证可中断不永久阻塞"的前提在发送阶段不成立。
2. 放大 1（持 M 阻塞）：宽限期满，timer 线程 `disposeByGrace`（WEB/DebugSessionBinder.java:268-280，持 B）→ `SessionDebugManager.gracefulDispose`（XDBG/session/SessionDebugManager.java:211-216，持 M）→ `releaseAll()` → `notifyLeaseExpired` 出站（XLangDebugger.java:255-262）→ 若该会话客户端同样停滞 → **持 M 阻塞** → 全部 `createSession/destroySession/shutdownAll/init` 冻结；`closeSession`（持 B 等 M）连锁冻结全部连接的 dispatch（renew/step/resume 全停）。
3. 放大 2（共享单线程定时器冻结）：`DebugGatewayRuntime.defaultTimer` = `ScheduledThreadPoolExecutor(1)`（WEB/DebugGatewayRuntime.java:77-79），同一 timer 承载 keepalive（DebugWebSocketHandler.java:109-113）、宽限任务、日志 flush。交错 1 中 T 持 S 阻塞 → timer 的 `sendKeepAlive`（DebugWebSocketHandler.java:682-689 → sendText synchronized(session)）在 S 上阻塞 → **所有连接的 ping、所有会话的宽限销毁、所有日志 flush 全部停摆**；或 timer 直接对停滞客户端 sendText 自身阻塞，效果相同。

**影响面**：P0（请求线程无限期占用 = 线程泄漏级；网关级 liveness 崩塌；不丢数据、无死锁环）。阻塞时长容器相关（部分容器对 blocking send 有默认上限，如 Tomcat ~20s；半开 TCP + 无应用层超时时无界）。
**修复方向**：① 出站改异步/有界队列 + 满则丢（D1 §3.5 事件本就是 best-effort 不重放，丢弃合规）；或 `getAsyncRemote` + `setSendTimeout`。② notifier 出站移出引擎线程（每会话发送队列 + 专职 sender）。③ **任何锁（B/M/L/P）内不得直接做 WS IO**：`gracefulDispose` 的 `releaseAll` 出站帧、`onSessionDestroyed` 的 `fireSessionClosed` 帧应先收集、锁外发送。④ timer 拆分（keepalive/宽限/flush 分线程）或至少保证 timer 上无直接 IO。

---

### C-2 [P1] CONFIRMED — 宽限销毁路径不关闭 `cleanupThread`：每个经 gracefulDispose 销毁的会话永久泄漏一个空闲调度线程（按会话churn无界累积）

**证据**：`XDBG/XLangDebugger.java:92-93` 每实例创建 `Executors.newSingleThreadScheduledExecutor(...)`（core=1，未 allowCoreThreadTimeOut）；唯一 `shutdown()` 在 `close()`（:106）。`SessionDebugManager.gracefulDispose`（:208-225）**有意不调 close**（D2/D3 红线：graceful 收尾不得置 closed），但也没有任何其他路径关闭该池；`reset()`（:284-290）同样不关。

**交错**：会话 churn（open → 断连 → 15s 宽限满 → gracefulDispose）× N → N 个 `xlang-debugger-cleanup` 空闲 daemon 线程永久驻留。`shutdownAll`/`destroySession`（close 路径）不泄漏，仅宽限路径泄漏。

**影响面**：按 P0 定义的字面（"线程永久泄漏"）本条可升 P0；因单条为空闲 daemon、不持锁不占资源、增长速率 = 会话销毁速率、JVM 退出无碍，实际风险评级取 **P1**。长寿命服务（网关场景预期形态）持续累积。
**修复方向**：引擎增加 `dispose()`（不置 closed，仅 `cleanupThread.shutdownNow()` + 可选 compileCache 清理），gracefulDispose 与 reset 调用之。

---

### C-3 [P1] CONFIRMED — 多标签页共享会话下引擎 notifier 单槽被 rebind 覆盖：原属主页签失去全部推送事件；evt.sessionClosed 亦仅送达一个连接

**证据**：引擎 notifier 为单槽 volatile（`XDBG/XLangDebugger.java:90`，D3 §3.5"专属 IDebugNotifier"落为单值）；`WEB/DebugSessionBinder.java:169-174` rebind 时 `debugger.setNotifier(newBridge)` 直接覆盖；`bridges` map 为 sessionId → 单 bridge（:76, :188）；销毁时 `fireSessionClosed` 只发 `bridges.get(sessionId)` 一个落点（:337, :353-354）。

**具体交错**：页签 1 `session.open` → 会话 S（bridge1 接线）。页签 2 `session.open`（过渡会话 S2）+ `session.rebind(S)` → `setNotifier(bridge2)`。此后 S 的全部 `evt.suspended/evt.leaseExpired/evt.exception/evt.logs` 只到页签 2；页签 1 仍绑定（`sessionConnections={conn1,conn2}`）、仍正常续租（限流按连接、共享租约 I8 成立）、其 debugger.* 请求/响应方法全部可用，但 **推送通道静默失联**——断点命中不再显示，直到用户手动刷新/rebind。会话销毁时页签 1 收不到 `evt.sessionClosed`。

**设计一致性**：D2 §3.4 明确背书多标签页共享会话（"双显示器两页签"）且**拒绝"单归属 + 抢占"**（租约面）；D1:18 宣布"多标签页打开同一会话是预期合法场景"。实现把 D2 拒绝的"抢占"移植到了事件通道（last-rebind-wins 单 sink）。D1 §3.5 未明文规定 fan-out，属实现缺口而非违宪，但与设计意图相悖且无限制登记。
**影响面**：P1（被支持场景下的确定性功能失效，非竞态窗口）。
**修复方向**：① bridge 持 `Set<IWebSocketSession>` 做会话内 fan-out（binder 按 sessionConnections 迭代）；或 ② 在 D1/D2 显式登记"事件单 sink、最后 rebind 者持有"为限制并让前端 rebind 时感知（当前未登记）。

---

### C-4 [P2] CONFIRMED — `releaseTarget`（stepX）闸门检查在 monitorLock 之外：交错下 stepX 静默丢失，或对已放行线程留下陈旧 step 模式（可跨执行污染后续断点判定）

**证据**：`XDBG/XLangDebugger.java:189-204`：`target = lastSuspendThread; if (target == null || !target.isSuspended()) throw` 在 **L 外**（:190-194），随后才 `monitorLock.lock()` 置 `threadStepMode` + `localReleased`（:196-203）。对照 `releaseThread`（:292-294）与 `releaseAllThreads`（:268-276）的 `isSuspended()` 闸门**都在锁内**。item 10 审计 F4 的修复（monitorWait 入口复位与放行原语同锁串行化，:628-631 注释、:699-701）只覆盖了后两者。

**具体交错 A（step 静默丢失）**：
1. 线程 T 挂起于断点；stepX：快照 T、`isSuspended()=true` 通过（L 外）。
2. T 的租约到期自放行（200ms 轮询窗口，`setSuspended(false)` 于 L 内完成）。
3. stepX 获得 L，对已不挂起的 T 置 `STEP_OVER + localReleased=true`，正常返回成功。
4. T 命中下一断点重入 `monitorWait`：入口于 L 内复位 `localReleased=false`（:699-701）→ **stepX 的放行被抹掉**。用户按了 step、收到成功响应、线程纹丝不动。

**具体交错 B（陈旧 step 模式跨执行污染）**：
1. 同上 1-3，T 已带 `threadStepMode=STEP_OVER` 运行。
2. T 此后每次命中都走 `checkBreakpoint` 分派 switch（:484-510）：`case STEP_OVER` → **跳过 `checkHitBreakpoint`**（:496 的 default 分支才查断点）。租约过期期间 grant② 先行短路（:472-474），step 永不"落地"复位。
3. 请求线程回池复用（同 threadId），`makeSuspendedThread` 返回**旧 map 条目**（:749-754，不复位 stepMode；条目仅按 `!isAlive` 30s 清理 :121-130）→ 新脚本的断点命中被当作陈旧 step-over 判定：漏断点，或以陈旧 `lastBreakFrameIndex` 为基线幽灵挂起一次（checkStepOver :556-577）。

**设计一致性**：D3 §3.6.5 定义"处理时刻快照判定：无已挂起目标即报错（含已释放未再挂起瞬态）"——实现按快照**判**了，却按**事后**状态**施**了（快照后不复核），偏离定义语义。
**影响面**：P2（窗口为快照与加锁之间的微秒级；但交错 B 的残留效应跨请求存续，且"step 后藏页签→租约过期"是 D2 明文支持路径，前置可达）。
**修复方向**：releaseTarget 在 L 内复核 `target.isSuspended()`（对齐 releaseThread，一行）；可选加固：monitorWait 出口/入口将 `threadStepMode` 复位为 RESUME。

---

### C-5 [P2] CONFIRMED — "requireSuspended 预检 → 取变量/求值"与租约到期/放行竞态：对已恢复线程的活跃 EvalRuntime 无保护求值，网关注释宣称的"良性竞态由引擎空结果兜底"不成立

**证据**：`WEB/DebugWebSocketHandler.java:572-581`（注释 + `requireSuspended` 预检）；随后 `debugger.getFrameVariables/getExprValue/getStackInfo`（DebugWebSocketHandler.java:423-481）→ `XDBG/XLangDebugger.java:404-417`（`eval` 仅判 `thread == null`）、:449-463、:429-436。map 条目放行后仍存留（cleanup 仅清死线程 :121-130）；脚本线程运行期**从不**获取 `synchronized(thread)`；`SuspendedThread.rt/lastBreakLocation` 的读取未同步（SuspendedThread.java:82-98，仅 `update/getStackInfo` synchronized :86-90, :104-110）。

**具体交错**：
1. T 挂起于断点；FE 发 `getScopeVariables` → 预检 `getSuspendedThreadIds().contains(T)` 通过。
2. 租约到期（≤200ms 轮询窗口）或另一页签 resume → T 恢复执行，推进/弹出 EvalFrame、改写 scope。
3. 网关线程此刻进入 `synchronized(thread)` 开始遍历 `rt.getScope()` 链与帧栈 → 与 T 的变异并发：撕裂读/ConcurrentModification（scope 实现相关）/返回陈旧垃圾变量值。

**影响面**：P2（只读面：异常以 error 响应呈现、变量面板脏数据；不腐蚀被调试状态）。注释的"良性"论断仅覆盖 `thread==null` 分支，对"已放行但条目仍在"分支失真。
**修复方向**：`eval/getStackInfo/getFrameVariables` 在 `synchronized(thread)` 内复核 `thread.isSuspended()`，不满足改抛 THREAD_NOT_SUSPENDED（引擎侧兜底取代注释性豁免）；彻底解法需挂起期间读快照（帧变量在挂起点物化），属 additive 演进。

---

### C-6 [P2] CONFIRMED — `ThreadLocalReplDeadlineChannel` 全局共享 `cancelled` 旗标：跨会话/跨执行取消互相错杀与抹除

**证据**：单例通道被全部会话共享（`XDBG/session/SessionDebugManager.java:59,63,141`）+ ReplService compile/execute 共用（`WEB/ReplService.java:108,182`）；`cancelled` 为**一个全局 AtomicBoolean**（`XDBG/session/ThreadLocalReplDeadlineChannel.java:36`）；`begin()` 复位旗标（:43）、`end()` 复位（:55）。

**具体交错**：
1. 错杀：会话 A 的 REPL#1 与会话 B 的 REPL#2 同时在飞（全局并发上限 4，`ReplService.java:170-177` 允许）。管理面/测试面 `cancel()`（:48-50，无目标参数）→ REPL#1 的下一个融合执行器入口抛 REPL_CANCELLED——取消本意针对 #2。
2. 抹除：`cancel()` 置 true 后，任一其他 REPL `begin()`（:43）先将其复位 → 目标执行的下一次 `checkDeadline` 查不到旗标 → **取消静默丢失**。两个并发 compile+execute 对今天就已在 begin/end/cancelled 上互踩。

**影响面**：P2（当前"一期无 HTTP 取消端点"（:24 注释）——生产面暂无 cancel 调用方，属带电潜伏缺陷；D6 §3.3 已声明共享旗标是有意裁定，但其"爆炸半径收口"仅收口到"REPL 窗口内"，未收口到"单次执行"）。
**修复方向**：`begin()` 返回/注册 per-execution 取消句柄（对象内含自身 AtomicBoolean），ThreadLocal 只存句柄；`cancel(handle)` 定向翻转。保持"跨线程可取消"的同时获得执行粒度隔离。

---

### C-7 [P2] CONFIRMED — 条件断点/logpoint 求值经函数调用边界重入会话 debugExecutor → 同线程嵌套挂起：一次命中双重挂起、SuspendedThread 状态跨层覆写、自递归条件无深度防线（D3 §3.7 预登记审计点）

**证据**：`matchCondition/logBreakpoint` 求值走 `XDBG/XLangDebugger.java:758-774`（`DefaultExpressionExecutor.INSTANCE` 直调）——但表达式内**函数调用边界**重读全局槽位：`nop-xlang/.../exec/ExecutableFunction.java:158`（`XLang.getExecutor()`）→ `XLang.java:52-53`（`EvalExprProvider.getGlobalExecutor()`）→ `SessionRoutingExecutor.execute`（SessionRoutingExecutor.java:51-61）按线程标记回同一会话 → `DebugExpressionExecutor.execute`（DebugExpressionExecutor.java:44）→ **重入 `checkBreakpoint`**（D3 §3.7："per-thread 化后的重入挂起语义列入并发深审清单"）。

**具体交错**：
1. 线程 T 命中 L1 条件断点，`matchCondition` 在**挂起前**求值 `f(x)`；`f` 体内 L2 也有断点（或同名行）→ 嵌套 `doSuspend` → T 在嵌套层 `monitorWait`（不持任何引擎锁——**无死锁**，已核）。
2. 用户 resume → 嵌套层放行 → `matchCondition` 完成 → **外层继续 `doSuspend` 于 L1** → 一次逻辑命中产生 2 次挂起 + 2 次 `evt.suspended`。
3. 同一 `SuspendedThread` 条目被逐层 `update` 覆写（:549/:620 → SuspendedThread.java:86-90）：嵌套挂起期间栈/位置显示内层（尚可接受），嵌套放行后外层覆写回 L1——事件与状态对用户呈"resume 了一次又停了"。
4. 条件函数自递归触发同断点：每层嵌套挂起消耗一次 resume 才退一层；若用户不 resume 而是反复 resume-retrigger，eval 栈持续增长 → StackOverflowError 杀死脚本（无深度防线）。

**影响面**：P2（机制继承自全局 executor 现状、会话化不放大跨会话面——D3 §3.7 的判断成立；但 per-thread 局部化使其成为可稳定复现的双挂起语义，且与 D2 §3.3"释放-再挂起不发事件"的防双跳变意图在嵌套面上冲突）。
**修复方向**：checkBreakpoint 增加"条件/logpoint 求值中"重入守卫（ThreadLocal 深度标志：matchCondition/logBreakpoint 求值窗口内置位，窗口内命中一律跳过挂起、仅记 log）——语义对齐 IDEA；登记为 engine-ext additive 变更。

---

### C-8 [P2] CONFIRMED（潜伏；即 plan 2476 N2 watch-item 的正式裁定）— binder.renew / closeSession 缺 `handle.isDestroyed()` 复核：直连 API dispose 与 WS renew 的交错可为 zombie 会话复活租约至 TTL；shipped 装配内不可达

**裁定 N2**：**shipped 装配（DebugWebServerConfig 装配 binder + listener）内 REFUTED**——`disposeByGrace/renew/rebind/closeSession` 全部在 binder 监视器锁内串行（DebugSessionBinder.java:268-280, 210-227, 157-204, 234-239），dispose 的 listener 清理（`cleanupSession` :375-386 移除 bindings）与 renew 原子先后，无交错；rebind 对已销毁目标复核 `isDestroyed`（:160）。**直连 API 面 CONFIRMED**：`SessionDebugManager.gracefulDispose/destroySession` 可被绕过 binder 直接调用（manager 本就是 public 单例），其 listener 回调在调用者线程锁外运行（SessionDebugManager.java:222-224；`onSessionDestroyed` 未 synchronized，DebugSessionBinder.java:335）。

**具体交错（直连 API 面）**：
1. 线程 A（管理/测试代码）：`manager.gracefulDispose(handle)` 完成 `sessions.remove + handle.markDestroyed`。
2. 线程 B（WS 容器）：`lease.renew` → `binder.renew(conn)`（持 B）→ `requireBinding` 仍命中（A 的 `cleanupSession` 尚未跑）→ `requireDebugger` **只判 null 不判 destroyed**（DebugSessionBinder.java:408-413）→ `debugger.renewLease(ttl)`（:222）→ **zombie 会话租约复活**。
3. A 的 cleanup 随后移除 binding；此后该连接报 NO_SESSION，但 zombie debugger 的 deadline 已指向未来：仍在飞行中、携带该会话标记的脚本线程可再次挂起至多一个 TTL（notifier 已拆线，事件丢弃），最终凭租约谓词自放行（I1 兜底，无永久阻塞）。

**影响面**：P2（有界自愈、仅 shipped 装配外可达；但 renew 是租约唯一推进口，缺 destroyed 复核属防御缺口，任何未来新增的直连管理端点都会踩中）。
**修复方向**：`binder.renew`（与 `closeSession`）在 B 内加 `binding.handle.isDestroyed()` 拒绝（一行），使 destroyed 位成为一切租约变异的权威闸门。

---

### C-9 [P2] CONFIRMED — `setBreakpoints` 非原子（clear + 逐条 add）：并发双页签 setBreakpoints 产生**并集**，违背 D2 §3.4"后写覆盖"的定义语义

**证据**：`XDBG/BreakpointManagerImpl.java:46-53`（`clearBreakpoints()` 后循环 `addBreakpoint`，无 manager 级锁；`breakpoints` 为 CHM，条目级 `synchronized(map)` 只保住单行 put/remove）。binder 只串行化**单连接**消息（DebugWebSocketHandler dispatch 按连接），两个连接的 dispatch 线程并发进入引擎。

**具体交错**：页签 A `setBreakpoints([a])` 与页签 B `setBreakpoints([b])` 并发：A.clear → B.clear → A.add(a) → B.add(b) → 结果 `{a,b}`——非任一方的完整意图。次生：`rebind` 快照 `getBreakpoints()`（DebugSessionBinder.java:199）落在 clear 与 add 之间时返回**空表**，重连方据此清空本地断点副本。
**设计一致性**：D2 §3.4："断点表操作按到达顺序生效（后写覆盖，`setBreakpoints` 语义天然如此）"——"天然如此"的前提在并发下不成立，属设计与实现的偏差。
**影响面**：P2（多标签页是支持场景；表现 = 用户删掉的断点复活/重连后断点表丢失，可复现、低危但违背钉死语义）。
**修复方向**：引擎内以独立监视器锁包 `setBreakpoints/clearBreakpoints` 的整体替换（或 CHM.replaceAll 语义的整表原子换入）；`getBreakpoints` 同锁取一致快照。

---

### C-10 [P2] CONFIRMED — D3 §3.2 宣称"编译期挂起的阻塞风险由 D2 租约兜底"对默认会话不成立：deadline=0 恒不过期，REPL compile 线程可被 IDEA 断点无限期占用

**证据**：默认会话租约禁用（`leaseDeadlineMillis=0`，`XDBG/XLangDebugger.java:71`；`leaseExpired()` 判 `deadline > 0` :333-336 → 永假）；REPL compile 无会话标记 → 编译期常量/宏求值路由**默认会话**（SessionDebugManager.java:56-57 注释自认）；ReplService.compile 的 deadline 通道只在执行器入口检查（DebugExpressionExecutor.java:39-40），**monitorWait 挂起期间不检查**。

**具体交错**：开发者经 IDEA 在某宏/编译期求值代码设断点（默认会话，租约无兜底）→ Web 用户提交 REPL compile → 编译期求值命中 → HTTP 线程 `monitorWait` 挂起，谓词仅 `closed/localReleased` 可解除 → **无 leaseExpired、无 REPL deadline 打断**，直到 IDEA 用户恰好 resume/close 或服务停机。D3 §3.2（03-sessionization.md:43）明言"编译期挂起的阻塞风险由 D2 租约兜底（无人续期即到期放行）"——对 deadline=0 的默认会话该兜底**不存在**，设计与实现不一致。
**影响面**：P2（前置需 IDEA 断点位置与 Web compile 求值面相交——同库开发场景真实存在；缓解：IDEA 侧默认会话可见该挂起并可 resume）。
**修复方向**：① 默认会话授予长 TTL 虚拟租约（如 5min 上限值），使"无人 resume 的编译期挂起"有界；或 ② ReplService.compile 以 destroyed 哨兵句柄包裹求值窗口（compile 面本无需断点语义，D3 §3.2 的"窗口覆盖 compile"可对 REPL compile 显式收窄并修订设计档）。

---

### N-11 [P3] CONFIRMED — 观测性缺口三则（不涉正确性）

1. **双线程同窗租约自放行产生两条单元素 `evt.leaseExpired`**（monitorWait per-thread 发射，XLangDebugger.java:724-730）而非一条聚合——协议合法（D1 §3.5 threadIds 数组），UI 噪声。
2. **stepX 释放后失联窗口**：stepX 零事件契约（D3 §3.6.3）+ 释放后租约过期 grant② 短路（:472-474）→ 线程不再重挂起且无任何后续事件 → FE 挂起行陈旧直至下次轮询/rebind。建议：grant② 跳过时若 `currentThreadStepMode() != RESUME` 补发一条 resumed/leaseExpired 消歧，或 FE 以 `isSuspended` 轮询兜底（当前未登记）。
3. **makeSuspendedThread 入口 TOCTOU 抛点**（grant① 通过后 close 插入，:746-747）与出口抛点（:721-723）错误码相同但语义位置不同——合规（abort 语义），仅登记以便排障时区分。

---

### N-12 [P3] NEEDS-PROBE — 两个继承性观察项（不占缺陷编号，登记备查）

1. **条件求值窗口内无任何 deadline/租约裁决**：`matchCondition`（:758-765）执行用户函数，若其内部阻塞（调外部服务），I1 的 200ms 轮询不适用——继承现状（全局 executor 同样如此），会话化不放大；若未来 REPL 支持条件断点需复查。
2. **`EvalExprProvider._executor` 非 volatile**（nop-core/.../EvalExprProvider.java:14, 51-54）：启动期一次性写入 + destroy 期写回，稳态读无竞争；初始化 happens-before 由类初始化与启动顺序保证。列于 P3 观察项。

### N-13 [—] REFUTED — 死锁（专项排查结论）

按 §一锁序全景逐一核查全部获取序：`B→M→L`（closeSession→destroySession→close / gracefulDispose→releaseAll→releaseAllThreads）、`M→S`（gracefulDispose 内 releaseAll 的 leaseExpired 出站）、`B→S`（closeSession/onSessionDestroyed 的 sessionClosed 出站）、`B→P`（attachLogPipe）、`P→S`（flush 出站，**锁外** :121-133）、引擎线程 `L/P/S` 独立获取。S 只被本代码在最内层 synchronized（wrapper 对象，容器不感知），无任何路径持 S 再取 B/M/L/P。**结论：无锁循环**。持锁阻塞（stall）风险已单列为 C-1。

---

## 四、测试证据面评估

- **已钉住**（本次审计直接引用为反证资产）：租约续期/到期放行/事件互斥/出口判定顺序（TestLeaseLifecycle 组1-5）、stepX 定向双线程（组6）、isSuspended 维护与 releaseThread 幂等（组7）、双会话租约隔离（组8）、suspendOnce CAS（组9）、双会话四维隔离 + create-vs-shutdown 并发（TestSessionIsolation）、宽限销毁放行不抛错/多标签最后连接才宽限/宽限窗内 rebind 保活/renew 限流与豁免（TestDebugGatewayE2E）、协议面违规计数/4401/4409（TestDebugGatewayProtocol）。
- **证据缺口**（与上述发现一一对应，均无测试覆盖）：① 挂起前 notifier 发送阶段的阻塞/超时行为（C-1）；② gracefulDispose 后实例资源回收（C-2，现有测试只断言 closed 路径）；③ 多标签页事件送达对等性（C-3，testMultiTabLastConnectionTriggersGrace 只测租约面）；④ stepX 与放行/租约到期的竞态窗口（C-4，组6 是稳态编排）；⑤ 放行瞬态窗口内的变量读取（C-5，requireSuspended 竞态被注释性豁免而非测试钉住）；⑥ 并发 cancel / 跨执行旗标（C-6，TestReplDeadline 为单执行编排）；⑦ 条件断点函数调用嵌套挂起（C-7）；⑧ 并发 setBreakpoints（C-9）。

---

## 五、总结与修复拆分建议

**计数**：P0 × 1（C-1）；P1 × 2（C-2、C-3）；P2 × 7（C-4 ~ C-10）；P3 × 2（N-11、N-12）；REFUTED × 1（N-13 死锁）。合计入档 13 项（另 I1/I5/I15 三条不变式"部分成立/不成立"已归入对应缺陷）。总体判断：**会话隔离与租约状态机的核心语义（I2/I3/I4/I6/I7/I9/I10/I11/I13/I14）实现与 D2/D3 一致且测试钉住扎实**；缺陷集中在**出站通道的活性**（C-1/C-3）、**销毁路径的资源收尾**（C-2/C-8）与**锁外闸门/共享单例的竞态面**（C-4 ~ C-7、C-9），无跨会话状态污染级缺陷。

**建议的 remediation plan 拆分**（按可独立验收的切片，全部不触发保护区；引擎改动限于 nop-xlang-debugger）：

1. **plan A（P0，活性收口）**：出站异步化/限队列 + 锁内禁 IO 改造（C-1 的 ①③④），timer 拆分评估（C-1 ②）。验收：新增"停滞客户端不阻塞引擎线程/不冻结 timer/不持 M 发送"的并发回归测试；D1 §3.5 best-effort 语义复核（丢帧合规声明）。
2. **plan B（P1，销毁路径收尾）**：引擎 `dispose()` 原语 + gracefulDispose/reset 接线（C-2）；binder.renew/closeSession 加 destroyed 复核（C-8，与 N2 watch-item 一并销案）。验收：churn 压测线程数守恒断言 + 直连 API dispose-renew 竞态测试。
3. **plan C（P1，多标签页事件面）**：fan-out 或限制登记（C-3）。需先在 D1/D2 走一次语义裁定（additive：bridge 多 sink 或登记单 sink 限制），再实现 + 多连接事件测试。
4. **plan D（P2 批次一：引擎闸门）**：releaseTarget 锁内复核（C-4）+ 变量读取 isSuspended 复核（C-5）+ setBreakpoints 原子化（C-9）。三处均为小 diff、同属"锁内复核/原子替换"模式，可一个 plan 内联交付并各配竞态回归测试。
5. **plan E（P2 批次二：取消与重入）**：per-execution 取消句柄（C-6，D6 §3.3 裁定修订需在 design 档登记）+ 条件断点重入守卫（C-7，engine-ext additive，先在 D1/D3 登记语义）。验收：并发取消定向性测试、嵌套挂起单事件测试。
6. **plan F（P2/P3 收尾）**：默认会话编译期挂起兜底（C-10，需 D3 §3.2 文档同步修订）+ N-11 观测性三项按需跟进。

---

## 六、与既有裁定/登记的关系

- **plan 621 审计 B3（dispose-revive 语义）**：本次在 I9/I10 面复核，shipped 装配语义成立，未发现回归。
- **plan 2476 审计 N2（direct-API dispose(connected)→revive loop-rebind）**：本次正式裁定为 C-8——shipped 装配内 REFUTED（binder 锁串行），直连 API 面 CONFIRMED（renew 缺 destroyed 复核的残窗，有界自愈）；建议随 plan B 一行修复后销案。
- **plan 2475（statement-level stepping for task xpl）**：语句级粒度为既登记限制，本次 C-4/C-7 的 step 语义发现与其正交，不受其影响。
- **D3 §3.7 预登记的"条件/logpoint 重入挂起"审计点**：本次以 C-7 结案（机制确认、无死锁、双重挂起语义偏差 + 无深度防线）。
