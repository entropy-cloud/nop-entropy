# 2472 XDBG item 13 日志流与事件管道（roadmap item 13）

> Plan Status: draft
> Last Reviewed: 2026-10-16
> Source: roadmap Work Item 13；设计权威 `01-ws-protocol.md` §3.5（evt.logs/evt.exception schema 登记）+ `00-overview` M3 验收"日志/事件经网关按会话定向出站且顺序正确（攒批不乱序）"+ `02-lease-lifecycle` §3.6（断连窗口日志不可补齐限制）
> Related: plans/2471（item 12 done——bridge/出站锁/会话绑定面）

## Purpose

落地会话定向的日志攒批管道与异常事件出站：`notifyLog`/`notifyWarn` 单条直发改造为每会话攒批（`evt.logs{entries}` 按条数/时间阈值 flush，销毁前 flush 保序）；`IDebugNotifier` 受控扩展 `notifyException`（engine-ext，D1 §3.5 登记的 evt.exception 产生源），在会话路由执行器最外层入口捕获被调试脚本的未捕获异常（定位信息 + errorCode）直发出站。M3 验收项"日志/事件经网关按会话定向出站且顺序正确（攒批不乱序）"由本 plan 达成。

## Current Baseline

- item 12 已收口（plan 2471，roadmap item 12 done）：`GatewayNotifierBridge` 每会话出站桥——notifyLog 直通单条 evt.log（level=info）、notifyWarn 归并 level=warn、`synchronized(session)` 出站锁、session==null 测试桩形态静默；`fireCompleted`/`fireSessionClosed` 预留/终态入口已就位。
- 日志产生源盘点（已核验）：**脚本级日志唯一产生源 = logpoint 的 `notifyLog`**（XLangDebugger.logBreakpoint:668-680，logExpr 求值后发 sourcePath/line/message）。XPL 无脚本级 log 标签/函数（stdlib xlib 全量 grep 无 log tag、GlobalFunctions 无 log/print），SLF4J 无平台中立 per-session appender SPI——"log appender"源无挂点，roadmap"Appender + 攒批"中的 Appender 形态在 Nop 语境落为 engine notifier 源（magic-api 模式借鉴中的 ThreadLocal 会话路由已由 DebugSessionScope 承担，攒批照做）。
- 出站帧构建：bridge.sendNotification 私有（jsonrpc envelope + synchronized(session) + isClosed 检查）。
- 会话生命周期挂钩：`SessionDebugManager` SessionListener（onCreated/onDestroyed，锁外触发）——item 12 由 `DebugSessionBinder` 消费；onCreated 接线出站桥、onDestroyed 推 evt.sessionClosed。
- 会话路由执行器：`SessionRoutingExecutor.execute`（web 开启时的全局 executor 外壳）——三条路由（无标记→默认会话/存活→本会话/销毁→DestroyedSessionExecutor），**无异常捕获**，异常直接传播到调用方。
- evt.log 现状 schema 缺口：D1 §3.5 登记 `{level, message, logger?, timestamp, sourcePath?, line?, threadId?}`——现 bridge 出站**无 timestamp/logger 字段**（item 13 补齐 timestamp；logger 一期无来源，留空不发射）。
- 测试基座：MockWsSession（出站捕获）+ AbstractDebugGatewayTest（注入型 runtime，keepalive/TTL 可注入）+ 内存 WS 桩模式。

## Goals

- `DebugLogPipe`（每会话日志攒批管道，nop-xlang-debug-web）：
  - **归属与接线（审计 F2 修订）**：pipe 由 `DebugSessionBinder` 按 sessionId 创建并持有（onCreated 接线），**不随 bridge 生命周期**；bridge 持 pipe 引用做 enqueue，pipe 持 bridge 引用做 flush 出站。`session.rebind` 路径（binder 直接 new bridge 绕过 onCreated）必须显式重接：flush 旧 pipe 残余（经旧 bridge 尽力送达）→ `pipe.attachSink(newBridge)`。
  - enqueue（bridge notifyLog/notifyWarn 路由至此）：buffer 保序追加；条数达 `batch-size` → 立即 flush；否则若未排程 → 定时器排程 `flush-interval-ms` 后 flush。
  - flush：`evt.logs{entries:[...]}` 经 bridge 出站（synchronized(session) 共享出站锁——与 evt.suspended 等事件全序一致）；entries 元素 = D1 §3.5 evt.log 形状（补 timestamp，Long epoch ms）。
  - 会话销毁 flush：onDestroyed 时先 flush 残余 entries 再推 evt.sessionClosed（销毁窗口日志尽力送达，D2 §3.6 不可补齐限制不变——只保已入 buffer 的）。**suppress（rebind 过渡会话静默注销）路径 flush 前置于早退**（审计 F5：随旧 bridge 尽力送达，与"只保已入 buffer"口径一致）。
  - 并发纪律：pipe 内部锁只覆盖 buffer/排程状态；出站走 bridge 的 session 锁；flush 由"入队线程/定时器线程/销毁线程"三方触发，以 pipe 锁内摘除 buffer（swap）→ 锁外出站（防持 pipe 锁发送）。swap 后入队的新 entry 归下一代 buffer（审计 F6：destroyFlush 场景下 swap 后入队丢失视为尽力送达边界，非缺陷）。
- `GatewayNotifierBridge` 改造：notifyLog/notifyWarn → entry 组装（补 timestamp）→ pipe.enqueue（pipe 为 null 时保持现状直发单条 evt.log——向后兼容与测试桩形态）；新增 `sendLogs(List<Map>)` 包内出站（evt.logs 帧）。
- `evt.exception` 产生源（engine-ext，两域改动）：
  - `IDebugNotifier` 新增 **default 方法** `notifyException(String sourcePath, int line, String message, String errorCode)`（受控扩展，D1 §3.5 登记项；形态对齐 item 10 的 notifyLeaseExpired/notifyResumed default 先例——接口变更零破坏，Phase 1 不触 debug-web 模块）。default 体 = 空实现；覆写方：`XLangDebuggerInitializer.DebugNotifier`（TCP 通道 LOG 记录，行为对齐 notifyLeaseExpired 现状）、测试 CollectNotifier（记录用）、Phase 2 的 GatewayNotifierBridge（覆写 default 直发 evt.exception，不攒批——低频高价值）。
  - 产生点 = `SessionRoutingExecutor.execute` 最外层入口（web 激活时唯一执行边界）：ThreadLocal depth 守卫（嵌套重入只报最外层——函数/标签调用边界重入全局槽位时不重复报）+ try/catch-log-rethrow（异常传播行为零改变）。**三分支路由（default/存活/destroyed）统一包报**：经 `handle.getDebugger().getNotifier()` 触达并调用 notifyException（handle/notifier/debugger 为 null 时守卫跳过——destroyed 哨兵句柄 debugger=null；default 会话经 `manager.getSession("default")` 触达，shutdown 后为 null 同守卫）；发送层行为由各 notifier 自决（TCP=LOG、bridge=evt.exception 出站、destroyed 会话 notifier 仍指向 stale bridge 对象，发送时 isClosed 丢弃）。
  - 字段承载：NopException → sourcePath/line = errorLocation（**line < 0 或 loc 为 null 时二字段均不发射**，D1 §3.5 sourcePath?/line? 可选）、message = description 优先（空则 getErrorCode）、errorCode = getErrorCode；非 NopException → message = toString、无定位无 errorCode。
- 配置常量（`XLangDebuggerConfigs`，nop-xlang-debugger 模块内，零保护区）：`nop.xlang.debug-web.log.batch-size`（默认 64）、`nop.xlang.debug-web.log.flush-interval-ms`（默认 300）；`DebugGatewayRuntime` 读取并注入 pipe 工厂（可注入 short 周期供测试）。
- 测试（两域）：
  - debug-web：攒批顺序（N 条 entry 单帧有序、timestamp 单调）、阈值 flush、定时 flush（短周期注入）、会话隔离（A/B 会话各自 pipe 互不可见）、销毁前 flush（evt.logs 先于 evt.sessionClosed）、evt.exception 直发出站（sourcePath/line/errorCode 承载）、evt.logs 与 evt.suspended 共享出站锁全序。
  - debugger：嵌套重入只报一次（最外层）、异常传播行为不变（rethrow 保原异常）、default 路由分支报 LOG 不抛、destroyed 分支不报。

## Non-Goals

- SLF4J/logback appender 源（平台无中立 appender SPI，脚本无会话日志 API——见 Current Baseline 盘点；如 item 18/22 需要更多日志源走 additive 演进）；evt.log 的 logger 字段来源（一期无来源）；断连窗口日志补齐（D2 §3.6 显式不可补齐）；REPL 日志（item 14 自有通道）；前端消费（item 17/18）。

## Scope

### In Scope

- `nop-dev-tools/nop-xlang-debug-web`：`DebugLogPipe`（新）、`GatewayNotifierBridge`（pipe 接线 + sendLogs + notifyException）、`DebugSessionBinder`（onCreated 接线 pipe）、`DebugGatewayRuntime`（配置注入）
- `nop-dev-tools/nop-xlang-debugger`：`IDebugNotifier.notifyException`（+两实现方）、`SessionRoutingExecutor`（异常捕获外壳）、`XLangDebuggerConfigs`（两配置键）
- 测试（两域）+ owner-doc 同步（D1 §3.5 evt.log timestamp 补齐注记随实现核对）

### Out Of Scope

- item 14 REPL、item 17/18 前端、nop-graphql-core/nop-api-core、IDEA 插件行为变更（TCP 通道仅 LOG 记录，无协议变更）

## Execution Plan

### Phase 1 - 引擎侧：notifyException 扩展 + 路由执行器异常捕获

Status: completed
Targets: `nop-xlang-debugger`（IDebugNotifier/SessionRoutingExecutor/XLangDebuggerConfigs/CollectNotifier）+ `XLangDebuggerInitializer.DebugNotifier`

- Item Types: `Feature`

- [ ] `IDebugNotifier.notifyException(sourcePath, line, message, errorCode)` 新增；`XLangDebuggerInitializer.DebugNotifier` 实现（LOG 记录，对齐 notifyLeaseExpired 形态）
- [x] `CollectNotifier`（测试基座）补默认实现；既有测试记录器按需补齐
- [x] `SessionRoutingExecutor.execute` 异常捕获外壳：ThreadLocal depth 守卫（只报最外层）+ try/catch-log-rethrow；NopException 提取 errorLocation→sourcePath/line、getErrorCode、description；三分支路由（default/存活/destroyed）统一包裹
- [x] `XLangDebuggerConfigs` 增 `CFG_XLANG_DEBUG_WEB_LOG_BATCH_SIZE`（64）/`CFG_XLANG_DEBUG_WEB_LOG_FLUSH_INTERVAL`（300ms）
- [x] 测试：嵌套重入单次上报（depth 守卫）、rethrow 保原异常引用、default 分支上报行为、destroyed 分支 null 守卫不抛错

Exit Criteria:

- [x] 异常捕获外壳测试绿（单次上报 + 传播不变 + 分支行为）——testExceptionReportedOnceAtOutermost/testNopExceptionLocationExtracted/testDefaultSessionExceptionReported/testDestroyedBranchReportAndNullGuard
- [x] 既有 debugger 模块回归绿（IDebugNotifier 接口变更零破坏——default 方法）
- [x] `./mvnw test -pl nop-dev-tools/nop-xlang-debugger` 全绿（62/62）
- [x] owner-doc：No owner-doc update required（D1 §3.5 已登记 evt.exception；default 接口扩展与模块内配置键为实现面自身）
- [x] `ai-dev/logs/` 执行当日条目已更新

### Phase 2 - 网关侧：攒批管道 + evt.exception 出站

Status: completed
Targets: `nop-xlang-debug-web`（DebugLogPipe/GatewayNotifierBridge/DebugSessionBinder/DebugGatewayRuntime）

- Item Types: `Feature`

- [x] `DebugLogPipe`：enqueue/阈值 flush/定时 flush/销毁 flush；buffer swap 锁外出站；entries 保序 + timestamp
- [x] `GatewayNotifierBridge`：notifyLog/notifyWarn → pipe.enqueue（null pipe 直发兜底）+ sendLogs（evt.logs 帧）+ notifyException 覆写（evt.exception 直发）
- [x] `DebugSessionBinder`：onCreated 创建 pipe（按 sessionId 持有）+ bridge 接线；**rebind 路径 flush 旧 pipe + attachSink 新 bridge**；onDestroyed/suppress：pipe.destroyFlush() 前置（先于 evt.sessionClosed / suppress 早退）
- [x] `DebugGatewayRuntime`：读配置注入 batch-size/flush-interval（测试可注入短周期）
- [x] 测试：攒批顺序/阈值/定时/会话隔离/销毁 flush 保序（evt.logs 先于 sessionClosed）/evt.exception 出站（NopException 定位+errorCode 承载）/pipe 与 evt.suspended 共享出站锁全序

Exit Criteria:

- [x] M3 验收达成：日志/事件经网关按会话定向出站且顺序正确（攒批不乱序）——testLogBatchingByThresholdOrdered（单帧 3 条有序）/testLogSessionIsolation/testDestroyFlushBeforeSessionClosed（logsIdx<closedIdx 索引断言）
- [x] D1 §3.5 schema 对账：evt.logs entries 元素含 timestamp（testLogBatchingByThresholdOrdered 断言）；evt.exception 四字段承载（testExceptionEventOutbound：定位/无定位/errorCode null 不发射三形态）
- [x] **接线验证**：notifyLog→pipe→flush→出站链路运行时连通（testLogpointEndToEnd 真实 logpoint 断点产生源端到端 + 阈值 flush）；notifyException→bridge→evt.exception 连通（testExceptionEventOutbound）
- [x] 无静默跳过：pipe 禁用态（null）行为 = 单条直发（非 no-op）——testDirectSendFallbackWithoutPipe
- [x] `./mvnw test -pl nop-dev-tools/nop-xlang-debug-web -am` 全绿（debug-web 34/34 + debugger 62/62 依赖链；注：宽限窗口/多标签两测试初版 sleep 边距对定时器过紧，负载下偶发绿失败——已改确定性边距 grace=2000/观测点 300ms，14+ 连跑绿）
- [x] `ai-dev/logs/` 执行当日条目已更新

### Phase 3 - 独立审计与收口

Status: planned
Targets: 全部、roadmap

- Item Types: `Proof`

- [ ] 独立子代理 closure audit：D1 §3.5 evt.logs/evt.exception schema 对账、攒批顺序/并发（三方 flush 竞态：入队/定时/销毁）、异常捕获外壳的传播不变性（变异探针：depth 守卫/rethrow）、会话隔离、M3 验收逐条对账、Anti-Hollow
- [ ] 修复审计发现
- [ ] roadmap item 13 → `done`（MVP milestone 仍依赖 item 14 不翻转）
- [ ] `ai-dev/logs/` 收口记录

Exit Criteria:

- [ ] audit 发现全修复
- [ ] 测试门全绿（两域）
- [ ] roadmap item 13 `done` 且 MVP milestone 未翻转
- [ ] `ai-dev/logs/` 收口记录

## Closure Gates

- [ ] 全部 in-scope 行为实现且有测试
- [ ] Anti-Hollow：notifyLog→pipe→flush→出站、notifyException→直发出站两链运行时连通（不只是类型存在）
- [ ] D1 §3.5 schema 对账无遗漏（evt.logs/evt.exception/evt.log timestamp 补齐）
- [ ] owner docs：D1 §3.5 若实现与登记有偏差（timestamp 补齐为 additive 实现）核对一致；D2 §3.6 限制不变；无其他 owner-doc 漂移
- [ ] 独立子代理 closure audit 完成并记录证据
- [ ] `./mvnw test -pl nop-dev-tools/nop-xlang-debug-web -am` 通过
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-dev-tools/nop-xlang-debug-web --severity high` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2472-xdbg-item13-log-event-pipe.md --strict` 退出码 0
- [ ] 改动面仅声明文件（debug-web 四文件 + debugger 四文件 + 测试）

## Non-Blocking Follow-ups

- evt.log logger 字段来源（脚本侧日志 API 缺失，若前端需要走 additive 演进）
- SLF4J appender 源（item 25 安全审计后按需评估）

## Closure

Status Note: （待收口后填写）
Completed: （待填写）

Closure Audit Evidence:

- Reviewer / Agent: （待填写）
- Evidence: （待填写）
