# XDBG-26 前端集成审计（xlang-web-debugger roadmap item 26）

**日期**：2026-10-18
**审计人**：独立 deep auditor（非实现方）
**状态**：complete
**审计对象**：
- 前端（flux 仓，下称 FE）：`packages/flux-xlang-debug`（state/client/extension/assembly/panel 全部 src）+ `apps/playground/src/pages/xlang-debug-page.tsx`、`apps/playground/src/env/socket-impl.ts` + `tests/e2e/xlang-debug.spec.ts`、`tests/e2e/xlang-debug-mock-server.mjs`
- 后端（entropy 仓，下称 BE）：`nop-dev-tools/nop-xlang-debug-web`（DebugWebSocketHandler / DebugSessionBinder / GatewayNotifierBridge / DebugAuthenticator / DebugGatewayRuntime）+ 端点薄壳（nop-quarkus-web / nop-spring-web-starter 的 `XLangDebugWebSocketEndpoint`）
- 协议仲裁基准（D1）：`ai-dev/design/xlang-web-debugger/01-ws-protocol.md`；租约语义（D2）：`02-lease-lifecycle.md`；前端架构参照：`05-frontend-architecture.md`

**路径约定**（矩阵与发现中的 file:line 前缀）：
- `FE` = `/Users/abc/app/nop-chaos-flux-wt/nop-chaos-flux-master/`
- `BE` = `/Users/abc/app/nop-entropy-wt/nop-entropy-feat-xlang-web-debugger/`
- D1/D2 = `BE/ai-dev/design/xlang-web-debugger/` 下的两份文档，行号以 § 段落定位

**方法**：① 以 D1 为仲裁基准构建**全量**协议一致性矩阵（客户端发出的每个 method 逐一三方核对；服务端每个 `evt.*` 逐一三方核对，不抽样）；② 生命周期审计：逐一追踪 client/assembly/extensions 中创建的每个 subscription/listener/interval/WS，验证 dispose 链可从 React unmount（含 StrictMode 双挂载）到达；③ 重连仿真：按 server-drop → reconnect → rebind 序列逐行走查 debug-client.ts，核对状态恢复完备性（断点回放/挂起态/租约时钟/muted）。

---

## 一、结论摘要

| 级别 | 数量 | 概览 |
| --- | --- | --- |
| P0 | 0 | —— |
| P1 | 3 | `evt.sessionClosed` 无会话失效处理；rebind 被拒重建路径残留旧会话挂起态；`expandExprValue` keys 参数误用（懒展开对真实后端必然失效） |
| P2 | 5 | 陈旧 socket 回调无守卫；session.open 非版本类错误挂死在 connecting；`evt.completed` 无 UI 消费方；多标签页事件单落点；muted 态不随 rebind 恢复（含 D1 schema 缺口） |
| P3 | 11 | 断连不清 pending、退避不复位、runToCursor 语义失真、错误条不清除、4400 终态标签失真、pong 缺席、exceptions 无界、beforeunload 缺位、lease 只写不读、playground openSocket 身份不稳、mock 服务器协议放松 |

三项 P1 中两项是**状态机类缺口**（重连/销毁路径的状态收敛），一项是**协议语义误用**（keys 导航语义）。协议矩阵本身的形状一致性非常好：FE 发出的全部 16 种 method 与 D1 §3.3/§3.4 及 BE handler 逐字段一致，BE 发出的 8 类 `evt.*` 形状与 FE 消费一致；**分歧全部在语义层与生命周期层，不在帧形状层**。e2e 绿灯（plan 2476 mock-server 架构）对 P1-3 类失真不设防——mock 对未知语义一律回固定结果，见 §五.M-19。

---

## 二、协议一致性矩阵（全量，D1 仲裁）

### 2.1 客户端 → 服务端 method（FE 发出的全部 16 种）

| # | D1 定义（spec 段） | params → result（D1 §） | FE 发送点（file:line） | FE 实发 params | BE 解析（file:line） | 判定 |
|---|---|---|---|---|---|---|
| 1 | `session.open` §3.4 | `{clientInfo?, protoVersion}` → `{sessionId, protoVersion, lease:{ttlMs,renewIntervalMs}}` | FE:packages/flux-xlang-debug/src/client/debug-client.ts:426 | `{protoVersion:'1.0'}`（clientInfo 省略，可选项合规） | BE:nop-dev-tools/nop-xlang-debug-web/src/main/java/io/nop/xlang/debug/web/DebugWebSocketHandler.java:258-277（protoVersion 校验 :262-267，lease 组装 :272-275） | ✅ 一致；result 三字段与 D1 逐项一致 |
| 2 | `session.rebind` §3.4 | `{sessionId}` → `{sessionId, breakpoints, suspendedThreads, lease:{expiresAtMs,suspendedThreadIds}}` | FE:src/client/debug-client.ts:440 | `{sessionId:<上次会话>}` | BE:.../DebugWebSocketHandler.java:224-230；BE:.../DebugSessionBinder.java:157-204（RebindResult.toMap :471-478） | ✅ 一致（快照消费见 §四 R-2） |
| 3 | `session.close` §3.4 | `{}` → `{}`，随后服务端推 `evt.sessionClosed` | FE:src/client/debug-client.ts:512-518（dispose 内 fire-and-forget，不等响应即断连） | `{}`（带 id） | BE:.../DebugWebSocketHandler.java:231-236（先回响应再 closeSession，D1 §3.9 顺序成立） | ✅ 一致；D2 §3.5 best-effort 定位吻合 |
| 4 | `lease.renew` §3.4 | `{}` → `{lease: LeaseState}` | FE:src/client/debug-client.ts:209 | `{}` | BE:.../DebugWebSocketHandler.java:242-247；限流 BE:.../DebugSessionBinder.java:210-227（0.8×renewInterval :213-218，rebind 首豁免 :183,219） | ✅ 一致；FE 只消费 `lease.expiresAtMs`（:213-216），`suspendedThreadIds` 忽略（合规） |
| 5 | `debugger.addBreakpoint` §3.3 | `{bp:{sourcePath,line}}` → `{}` | FE:src/client/debug-client.ts:269-271 | `{bp:{sourcePath,line}}` | BE:.../DebugWebSocketHandler.java:323-331（Breakpoint bean 反序列化 :538-544；上限 500 :325-327） | ✅ 一致（condition/logExpr FE 恒缺省，字段可省） |
| 6 | `debugger.removeBreakpoint` §3.3 | `{bp}` → `{}` | FE:src/client/debug-client.ts:274-276 | 同上 | BE:.../DebugWebSocketHandler.java:332-335 | ✅ 一致 |
| 7 | `debugger.setBreakpoints` §3.3 | `{bps}` → `{}` | FE:src/client/debug-client.ts:373（仅 rebind 被拒重建路径） | `{bps:[本地镜像]}` | BE:.../DebugWebSocketHandler.java:336-344 | ✅ 一致 |
| 8 | `debugger.stepInto` §3.3 | `{}` → `{}` | FE:src/client/debug-client.ts:289-290 | `{}` | BE:.../DebugWebSocketHandler.java:365-368 | ✅ 一致 |
| 9 | `debugger.stepOver` §3.3 | 同上 | FE:src/client/debug-client.ts:289-290 | `{}` | BE:.../DebugWebSocketHandler.java:369-372 | ✅ 一致 |
| 10 | `debugger.stepOut` §3.3 | 同上 | FE:src/client/debug-client.ts:289-290 | `{}` | BE:.../DebugWebSocketHandler.java:373-376 | ✅ 一致 |
| 11 | `debugger.resume` §3.3 | `{}` → `{}`（会话内全局放行） | FE:src/client/debug-client.ts:292-294 | `{}` | BE:.../DebugWebSocketHandler.java:381-384 | ✅ 一致 |
| 12 | `debugger.suspend` §3.3 | `{}` → `{}` | FE:src/client/debug-client.ts:295-297 | `{}` | BE:.../DebugWebSocketHandler.java:377-380 | ✅ 一致 |
| 13 | `debugger.runToPosition` §3.3 | `{bp}` → `{}` | FE:src/client/debug-client.ts:298-300 | `{bp:<breakpoints[0]>}`（语义失真见 §三 F-12） | BE:.../DebugWebSocketHandler.java:419-422 | ✅ 形状一致，语义 P3 |
| 14 | `debugger.muteBreakpoints` §3.3 | `{muted}` → `{}` | FE:src/client/debug-client.ts:301-303 | `{muted:<bool>}` | BE:.../DebugWebSocketHandler.java:385-389 | ✅ 一致（muted 态恢复缺口见 F-8） |
| 15 | `debugger.getExprValue` §3.3 | `{threadId,frameIndex,expr}` → `{v}` | FE:src/client/debug-client.ts:304-310（transport，结果弃置）+ FE:src/panel/eval-input.tsx:27-31（query 直查） | 三字段齐全 | BE:.../DebugWebSocketHandler.java:423-434（expr ≤8KB :530-536；requireSuspended :577-581） | ✅ 一致 |
| 16 | `debugger.getStackInfo` §3.3 | `{threadId}` → `{stackInfo}` | FE:src/client/debug-client.ts:348-361（rebind 补取）+ FE:src/panel/call-stack-panel.tsx:31-45 | `{threadId}` | BE:.../DebugWebSocketHandler.java:455-464 | ✅ 一致；FE 读 `stackInfo.stackTrace[0]`，与 D1 §3.1 "topElement/topLocation 不出站" 注记吻合（未依赖被忽略字段） |
| 17 | `debugger.getFrameVariables` §3.3 | `{threadId,frameIndex}` → `{vars, truncated?}` | FE:src/panel/variables-tree.tsx:43-58 | 两字段齐全 | BE:.../DebugWebSocketHandler.java:473-480（截断 1000+truncated :583-593） | ✅ 一致；FE 消费 `truncated`（variables-tree.tsx:48,87-89） |
| 18 | `debugger.expandExprValue` §3.3 | `{threadId,frameIndex,expr,keys: DebugValueKey[]}` → `{vars}` | FE:src/panel/variables-tree.tsx:77-82 | `keys:[{name:<节点自身名>}]` | BE:.../DebugWebSocketHandler.java:435-454（keys ≤50 :441-444）→ 引擎 BE:nop-dev-tools/nop-xlang-debugger/src/main/java/io/nop/xlang/debugger/XLangDebugger.java:420-424 → **keys = 从 expr 值出发的导航步**（BE:.../debugger/DebugValueHelper.java:31-63，getNextValue 按 name/index 逐层下钻后再 expandValue） | ❌ **语义不符（P1-3）**：FE 把"展开该节点"表达成 `expr:node.expr, keys:[{name:node.name}]`，引擎语义是先求值 expr 再**沿 keys 下钻**——等价于取 `<node.expr>.<node.name>` 字段，恒空/错位。正确形态为 `keys:[]`（展开节点自身）。mock 服务器对该 method 无条件回固定子树（FE:tests/e2e/xlang-debug-mock-server.mjs:147-149），e2e 无法暴露 |

**FE 未使用的 D1 method（9 个）**：`getBreakpoints` / `clearBreakpoints` / `getBreakpointAt` / `updateBreakpoints` / `isSuspended` / `isBreakpointsMuted` / `getSuspendedThreads` / `getScopeVariables`（FE 无调用点）。BE 全部实现（DebugWebSocketHandler.java:345-481），FE 不用不构成不一致；但其中 `isBreakpointsMuted` 的缺席直接导致 F-8。

**D1 明确不暴露的 2 个**：`waitSuspended`/`close` —— FE 无调用 ✅，BE 无分支 ✅。

**保留方法**：

| method | D1 §3.3 | FE | BE | 判定 |
|---|---|---|---|---|
| `ping`（服务端→客户端 notification，10s） | §3.3 | FE:src/client/debug-client.ts:165-167 静默忽略 | BE:.../DebugWebSocketHandler.java:682-689（keepaliveIntervalMillis=10s，BE:.../DebugGatewayRuntime.java:41） | ✅ 接收合规；**FE 从不回 `pong`**（见 F-15） |
| `pong`（客户端→服务端豁免 notification） | §3.3 | FE 无发送点 | BE:.../DebugWebSocketHandler.java:159-160 静默 no-op | ✅ 兼容（BE 不追踪 pong，不回也不违规）；语义缺口记 P3 |
| `tokenRefresh` | §3.3 `{accessToken}` → `{refreshed}` | **FE 无调用**（重连走 per-attempt `getToken` 重新握手，FE:src/client/debug-client.ts:44-45,402） | BE:.../DebugWebSocketHandler.java:211-213,279-306（Authorization 映射校验链 BE:.../DebugAuthenticator.java:39-52） | ✅ 合规取舍：长会话 token 旋转依赖重连重握手而非刷新；代价是 4401 终态即丢会话（对比 tokenRefresh 可保会话），登记 P3 备注 |
| `unsubscribe` | §3.3 不采纳 → -32601 | FE 无发送 | BE:.../DebugWebSocketHandler.java:215-218 | ✅ 双方一致 |

### 2.2 服务端 → 客户端 evt.*（D1 §3.5 全部 8 个）

| # | evt（spec 段） | params schema（D1 §3.5） | BE 发射点（file:line） | BE 实发 params | FE 消费（file:line） | 判定 |
|---|---|---|---|---|---|---|
| 1 | `evt.suspended` | `{reason: suspend\|stepInto\|stepOver\|stepOut\|breakAt, threadId, stackInfo, location:{sourcePath,line}}` | BE:.../GatewayNotifierBridge.java:48-87（5 回调合一，reason 枚举逐字对齐） | 含 `location` 可缺省（loc==null 时不放 :55-60） | FE:src/client/debug-client.ts:118-126 → store.addSuspended；reason/stackInfo 有意忽略（前端只要位置） | ✅ 形状一致；FE 缺 location 时落 `{sourcePath:'',line:0}` 占位，sync 桥按 sourcePath 过滤不会产生错误高亮 ✅ |
| 2 | `evt.log` | `{level, message, logger?, timestamp, sourcePath?, line?, threadId?}` | BE:.../GatewayNotifierBridge.java:91-125（notifyWarn 归并 level=warn ✅ D1 裁定；timestamp 必带 :109；logger 恒不发射——可选字段合规） | — | FE:src/client/debug-client.ts:142-154 → onLog → FE:src/panel/log-panel.tsx:21-30（500 条滚动上限 :26） | ✅ 一致 |
| 3 | `evt.logs` | `{entries: evt.log[]}` | BE:.../GatewayNotifierBridge.java:128-132（DebugLogPipe 攒批 flush 调用，BE:.../DebugLogPipe.java:128） | — | FE:src/client/debug-client.ts:155-159（逐条拆给 onLog） | ✅ 一致 |
| 4 | `evt.exception` | `{sourcePath?, line?, message, errorCode?}` | BE:.../GatewayNotifierBridge.java:136-146（line>0 才带位置 :141-144） | — | FE:src/client/debug-client.ts:131-138 → store.addException → LogPanel 文本行 + sync 桥波浪线（FE:src/extension/sync.ts:35-37，`line>0` 过滤与 BE 发射条件互恰 ✅） | ✅ 一致 |
| 5 | `evt.resumed` | `{threadIds: long[], cause:"user"}` | BE:.../GatewayNotifierBridge.java:156-161 | — | FE:src/client/debug-client.ts:127-130 → removeSuspended | ✅ 一致（cause FE 不消费，合规） |
| 6 | `evt.leaseExpired` | `{threadIds: long[]}` | BE:.../GatewayNotifierBridge.java:149-153 | — | FE:src/client/debug-client.ts:127-130（与 evt.resumed 同一 handler，仅清挂起行） | ✅ 形状一致；D2 §3.7 item 17 职责"挂起行标注清除 + '已放行'提示"只兑现了前半（无区分提示），记 P3（F-10 附注） |
| 7 | `evt.completed` | `{ok, result?, error?:{message,errorCode?}}` | BE:.../GatewayNotifierBridge.java:164-177（!ok 时 message 落在 error.message ✅） | — | FE:src/client/debug-client.ts:160-164 → completedSubscribers；**全 UI 零消费**（grep 全包仅 log-panel 用 onLog）→ **F-6** | ❌ 形状一致但消费链断裂（P2） |
| 8 | `evt.sessionClosed` | `{reason: userClosed\|graceExpired\|serverShutdown\|protocolError}` | BE:.../GatewayNotifierBridge.java:180-184（四值枚举登记 BE:.../DebugSessionBinder.java:53-61） | — | FE:src/client/debug-client.ts:139-141：**reason 四值折叠为 'closed'** + detail=原始 JSON，仅回调装配层弹错误条；**无任何会话状态失效处理** → **F-1** | ❌ 形状一致，语义处理缺失（P1） |

未知事件 FE 忽略（FE:src/client/debug-client.ts:168-169）✅ D1 §3.6；未知 method BE -32601（:255,595-597）✅。

### 2.3 envelope / 鉴权 / 错误 / 关闭码

| 项 | D1 § | FE | BE | 判定 |
|---|---|---|---|---|
| id 线上 string | §3.1/§3.3 | FE 全部 `String(++nextId)`（debug-client.ts:102,514） | BE `String.valueOf(idObj)` 回显（DebugWebSocketHandler.java:155-156）；响应按 `String(msg.id)` 匹配（FE:181-183） | ✅ |
| 无 id 入站 → id:null 回 -32600（pong 豁免） | §3.7 | FE 不发 notification（无违规面） | BE:159-164,610-619 | ✅ |
| 业务错误承载 `error.errorCode` | §3.7 | FE 读 `error.errorCode ?? error.data?.errorCode`（FE:186-190）；三个点分码字符串比对 `nop.err.xlang.debugger.rate-limited`（:220）/`session-bound-elsewhere`（:447）/`protocol-version-unsupported`（:460） | BE 字符串码序列化已由协议测试锁定（TestDebugGatewayProtocol.java:58,126 等）；登记点 BE:nop-kernel/nop-xlang/src/main/java/io/nop/xlang/XLangErrors.java:1025-1057 | ✅ 三码逐字一致 |
| protoVersion 协商 | §3.6 | FE 发 `'1.0'`（:22,426） | BE 主版本 "1" 前缀匹配（DebugWebSocketHandler.java:603-606）；不支持 → -32000 + 4400（:262-267） | ✅ |
| 握手鉴权 `?nopDebugToken=` → 4401 | §3.2 | FE 拼 URL（:402-408；空 token 不拼参 B4 ✅）；playground 在 openSocket 包装层注入（FE:apps/playground/src/pages/xlang-debug-page.tsx:72-75，与 FE client 空参互恰，无双参） | BE 端点抽参首值（BE:nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/ws/XLangDebugWebSocketEndpoint.java:43-60,31）→ Bearer 映射 → extractFromHeaders（DebugAuthenticator.java:39-44）；失败 4401（DebugWebSocketHandler.java:97-106） | ✅ 与 D1 §3.2 裁定逐项一致 |
| 关闭码 4401/4403/4400/4409 | §3.8 | FE onclose 分派（FE:471-491）：4401/4403 → 终态 authFailed；4400 → 终态 protocolUnsupported；4409 与其他码 → 重连 | BE 4401 :104；4403 :300；4400 :146,266,618；4409 仅 SESSION_ALREADY_OPEN（DebugSessionBinder.java:113-115） | ✅；4400 语义标签失真见 F-14 |
| 帧约束（256KB/8KB/500bp/50keys/1MB/1000+truncated） | §3.1 | FE 无发送侧超限面（expr 输入无长度预检——8KB 上限由 BE -32602 兜底） | BE 全部落地（DebugWebSocketHandler.java:63-70,530-536,325-343,441-444,649-668,583-593） | ✅ |

**矩阵小结**：18 个出站 method 形状全绿；8 个入站事件形状全绿；分歧集中在 4 处语义/消费层（P1-3、P1-1、P2-evt.completed、P3-pong）。

---

## 三、发现清单（每条：级别 + 判定 + 三方定位 + 影响 + 修复方向）

### F-1 [P1] CONFIRMED — `evt.sessionClosed` 到达后客户端不失效会话状态

- **spec**：D1 §3.5 `evt.sessionClosed` = 会话销毁**终态**通知；D2 §3.3 状态机 Destroyed 后不可复用；D2 §3.7 item 17 职责含 UI 收敛。
- **客户端**：FE:packages/flux-xlang-debug/src/client/debug-client.ts:139-141 —— 仅转发回调（装配层弹错误条），`stopRenewHeartbeat`/`setTransport(null)`/清 `suspended`/失效 `currentSessionId`/触发重连**全部缺失**。
- **服务端**：BE:.../DebugSessionBinder.java:234-239（`session.close` 销毁会话但**其他标签页连接保持打开**）、:335-355（onDestroyed 推事件）。
- **影响**：多标签页场景（D1 §一.7/D2 §3.4 明确合法）任一页签 `session.close` 后：另一页签收到 `evt.sessionClosed` 但心跳继续——每次 `lease.renew` 得 `ERR_XLANG_DEBUGGER_NO_SESSION`（FE:218-227 静默吞掉，非 rate-limited 无动作），10s 一次永久空转；挂起行高亮残留、面板继续对死线程发 `getStackInfo`/`getFrameVariables` 撞 THREAD_NOT_SUSPENDED；UI 呈现"连接正常但一切操作失效"的僵尸态。
- **修复方向**：`handleNotification` 的 `evt.sessionClosed` 分支应执行与 `finish` 同级的会话失效：stopRenewHeartbeat、清 pending、`setSuspended([])`、`setLease(null)`、`setTransport(null)`、失效 `currentSessionId`，并按 reason 决定 `scheduleReconnect()`（graceExpired/serverShutdown → 自动重建新会话）或终态呈现；同时把 D1 四值 reason 透传而非折叠为 `'closed'`（FE:140 现状丢失 `graceExpired/serverShutdown` 语义，回调方无法区分可重试与终态）。

### F-2 [P1] CONFIRMED — rebind 被拒重建路径残留旧会话挂起态

- **spec**：D2 §3.5："唤醒后前端重连 + rebind：若会话已销毁 → `session.rebind` 返回 error `SESSION_BOUND_ELSEWHERE`，**前端按新会话重建**（断点表在客户端有副本，重新 setBreakpoints）"。
- **客户端**：FE:src/client/debug-client.ts:368-376 `rebuildOnNewSession` —— 重发断点 ✅、恢复心跳 ✅，但 **`suspended`/`lease`/`exceptions` 不复位**；对照 rebind 成功路径 `applyRebindSnapshot`（:328-365）会 `setSuspended(restored)` 全量重建。
- **服务端**：BE:.../DebugSessionBinder.java:157-162（目标会话不存在/已销毁一律 BOUND_ELSEWHERE，新过渡会话 `suspendedThreads=[]` 为服务端事实）。
- **影响**：断连超过宽限（15s）后重连（FE 重连退避累计 0.5+1+2+4+8+15s≈30s，超过宽限是常态路径）→ 旧会话挂起线程在 store 中残留幽灵条目：编辑器挂起行高亮错误保留、DebugPanel 以死 threadId 自动发 `getFrameVariables`/`getStackInfo`（THREAD_NOT_SUSPENDED 静默失败，面板呈现 loading/错误态）。
- **修复方向**：`rebuildOnNewSession` 内补 `setSuspended([])` + `setLease(null)`（exceptions 可保留为历史）；与 F-1 的失效逻辑合并为一个 `invalidateSession()` 复用。

### F-3 [P1] CONFIRMED — `expandExprValue` keys 参数误用，懒展开对真实引擎必然失效

- **spec**：D1 §3.3 `expandExprValue {threadId,frameIndex,expr,keys: DebugValueKey[]}`；字段语义源 = 引擎契约：keys 是**从 expr 求值结果出发的导航步序列**（BE:.../XLangDebugger.java:420-424 先 `eval(expr)`，BE:.../DebugValueHelper.java:31-38 `getExpandValue` 对每个 key 执行 `getNextValue` 下钻，再对终值 `expandValue` 列子项；:40-84 按 index 定位数组/列表、按 name 定位 Map/bean 字段）。
- **客户端**：FE:src/panel/variables-tree.tsx:77-82 —— `{threadId, frameIndex, expr: node.expr, keys: [{ name: node.name }]}`。根节点 `expr===name`（:109）时请求语义变成"取 `<name>` 这个值的 `name` 字段"；子节点（expr=`parent.child`，name=`child`）变成"取 `parent.child` 的 `child` 字段"。**"展开该节点"的正确请求是 `keys: []`**（下钻零步 → 对节点自身列子项）。
- **服务端**：BE:.../DebugWebSocketHandler.java:435-454 解析合规（形状无误），语义按引擎执行 → 返回空表（getNextValue 落 null）或同名巧合字段子树。
- **影响**：变量树懒展开——调试器核心能力——在真实后端联调（roadmap item 21/22）时必然返回空/错；mock 服务器对该 method 无条件回固定 `vars`（FE:tests/e2e/xlang-debug-mock-server.mjs:147-149），e2e 全绿假阴性。
- **修复方向**：`toggle()` 改发 `keys: []`；若要支持按 member 定位，再按 `expr: parentExpr, keys: [{name: nodeName}]` 建模子节点（与引擎导航语义对齐）。同时在 mock 服务器按引擎语义实现 keys（或至少回显校验），让 e2e 具备暴露此类失真的能力（见 §五.M-19）。

### F-4 [P2] CONFIRMED — 陈旧 socket 的 onopen/onclose/onmessage 无连接身份守卫

- **spec**：非协议项；属 FE 连接管理正确性（D2 §3.5 断连重连流的实现质量面）。
- **客户端**：FE:src/client/debug-client.ts:425-495 —— 三个 handler 闭包引用**共享可变量** `connection/state`，无 `conn === connection` 守卫：
  - 路径 A（StrictMode 双挂载）：mount1 connect(conn1) → cleanup dispose 调 `conn1.close(1000)`（:532）→ mount2 revive connect(conn2)（:394-397 复活闩锁）→ conn1 的 onclose **晚到**（浏览器 close 握手是异步宏任务）→ handler 执行 `connection = null`（:472，此时 connection 已是 conn2）→ `state='disconnected'` + `scheduleReconnect()` → **conn3 在 conn2 健在时又开一条**。注意 revive 复位 `disposed=false` 发生在 conn1 onclose 之前，守卫 `disposed||stopped`（:475）拦不住。
  - 路径 B（rebind 超时/失败）：FE:451 `scheduleReconnect()` 在 rebind catch 中触发时连接**仍然存活** → connect() 直接开第二条 socket，旧 socket 永不关闭（泄漏为服务端过渡会话，直到宽限销毁）。
- **服务端**：每条连接各自 `session.open` 都创建真实会话（BE:.../DebugSessionBinder.java:111-139）→ 每次竞态多产生一个待宽限销毁的过渡会话。
- **影响**：重复连接/会话churn、共享状态互相践踏（新连接句柄被旧连接 onclose 置 null）、心跳状态机错乱。现有单测未覆盖：MockConnection.close() 不触发 onclose（FE:src/__tests__/debug-client.test.ts:20-22），e2e 以 `≥1` 帧计数容忍了重复帧（FE:tests/e2e/xlang-debug.spec.ts:68-69 注释自证）。
- **修复方向**：connect() 内以局部 `const conn = ...` 捕获，三个 handler 首行 `if (connection !== conn) return;`；rebind catch 分支改为"先 `connection?.close()` 再 scheduleReconnect"（或对存活连接复用而非新开）。

### F-5 [P2] CONFIRMED — `session.open` 非版本类失败使客户端永久卡在 connecting

- **spec**：D1 §3.4 `session.open` 错误族（SESSION_ALREADY_OPEN+4409、服务端内部 -32603 等）；D1 §3.8 关闭码仅覆盖部分场景。
- **客户端**：FE:src/client/debug-client.ts:459-464 —— open 的 catch 只处理 `protocol-version-unsupported`，注释称"其余 open 错误走 onclose 重连路径"，**但业务错误响应不会触发 onclose**（连接保持打开）。
- **服务端**：BE:.../DebugWebSocketHandler.java:166-181 —— -32000/-32602/-32603 错误**只回响应不关连接**（仅 DebugProtocolException 携非零 closeCode 时才 close）。
- **影响**：`createSession` 服务端异常等场景 → 客户端 state 停在 'connecting'、无心跳、transport 未注册、无重连、无终态提示——静默死会话，仅能手动刷新。
- **修复方向**：open 失败（非版本类）时主动 `conn.close()` 交回 onclose 重连矩阵，或直接 `scheduleReconnect()`；4409（SESSION_ALREADY_OPEN）按 D1 §3.8 属"冲突升级终态"，应 finish 而非无限重连（当前重连后新连接 id 不同不会复现，可接受但应显式登记）。

### F-6 [P2] CONFIRMED — `evt.completed` 全 UI 零消费

- **spec**：D1 §3.5/§3.9：`evt.completed` 是"被调试脚本执行完成"的独立事件（正常流最后一步）；D2 §3.6 明确断连窗口内的 completed **不可补齐**——即在线期间这是唯一感知执行完成的通道。FE:src/client/debug-client.ts:15-20 注释自称"UI 独立状态呈现"。
- **客户端**：FE:src/client/debug-client.ts:160-164 分发到 `completedSubscribers`、:553-557 暴露 `onCompleted` —— **grep 全包无任何组件调用 `client.onCompleted`**（仅 log-panel.tsx:22 用 onLog）。e2e 只 emit 不断言 UI（FE:tests/e2e/xlang-debug.spec.ts:121-123）。
- **服务端**：BE:.../GatewayNotifierBridge.java:164-177 正常发射。
- **影响**：脚本执行完成对用户不可见（无结果行/完成态）；D2 §3.6 限制（错过即不可恢复）进一步放大——在线时都不呈现，错过更无从谈起。
- **修复方向**：DebugPanel（或 LogPanel 顶部状态行）订阅 `onCompleted` 呈现 ok/result/error；`{ok:false}` 时与 exceptions 区分呈现。

### F-7 [P2] CONFIRMED（跨域）— 事件单落点：多标签页仅最后绑定连接收到 `evt.*`

- **spec**：D1 §一.7"多标签页打开同一会话是预期合法场景"；D2 §3.4 多标签页=共享租约（任一页签前台即维持租约）；D1 §3.5 事件 best-effort 未定义 fan-out 拓扑——**设计留白**。
- **服务端**：BE:.../DebugSessionBinder.java:136（`bridges.put(sessionId, wire.bridge)` 每 session 单 bridge）+ :172-188（rebind 以新连接 wsSession 重建 bridge 并整体替换）→ 引擎 notifier 永远只指向**最近 open/rebind 的那条连接**。
- **客户端**：FE 无法感知事件流已断（无 heartbeat-of-events 机制）；旧页签 renew 照常、UI 冻结。
- **影响**：双屏/双页签调试场景，非最新页签收不到 `evt.suspended/resumed/log/completed`，挂起高亮与面板全部失真——与 D2 §3.4 的多标签页语义预期冲突。
- **修复方向**：属 BE+D1 演进项：bridge 改为按 `sessionConnections` 广播（`evt.*` 逐连接投递，D1 additive 登记）；短期可在 D1/D2 文档显式登记"事件单落点、多标签页仅最新连接为活跃观察者"的限制，避免 FE 侧错误承诺。

### F-8 [P2] CONFIRMED — muted 状态不随 rebind 恢复（含 D1 rebind schema 缺口）

- **spec**：D1 §3.4 rebind result = `{sessionId, breakpoints, suspendedThreads, lease}`——**无 muted 字段**；D2 §3.6 快照可补齐清单同样未列 muted。
- **客户端**：FE:src/panel/debug-panel.tsx:31 `muted` 本地 state 默认 false；rebind 成功路径（FE:441-452）不查询 `debugger.isBreakpointsMuted`（FE 有该 method 可用而未用，见 §2.1 未使用清单）。
- **服务端**：muted 是会话级状态（BE:.../DebugWebSocketHandler.java:385-389），跨断连存活。
- **影响**：断连前 mute 过（或多标签页他页签 mute）→ 重连后 UI 显示"未静音"，首次点击发 `muted=true`（服务端已是 true）→ 本地切换序列与服务端真值错位一拍，单步命中行为与 UI 预期不符。
- **修复方向**：短期 FE 在 rebind 成功后 `query('debugger.isBreakpointsMuted')` 回填本地态；中期 D1 additive 演进把 `muted` 纳入 rebind 快照（一次往返）。

### F-9 [P2] CONFIRMED — store.exceptions 无界增长且 `clearExceptions` 零调用

- **spec**：非协议项；FE 资源纪律（对照 LogPanel 对 logs 的 500 条上限，FE:src/panel/log-panel.tsx:26）。
- **客户端**：FE:src/state/debug-store.ts:181-187 `addException` 无界 append；`clearExceptions`（:80,185-187）grep 全包**无生产调用方**。
- **影响**：长会话反复异常（如循环内 logpoint 异常）→ 内存与 DOM 双无界（LogPanel 渲染全部 exceptions，log-panel.tsx:40-45）。
- **修复方向**：`addException` 设上限（对齐 logs 的 500）或提供 UI 清除入口并在 session 失效（F-1 的 invalidateSession）时清空。

### F-10 [P3] CONFIRMED — 断连不清 pending，在途请求挂满 30s 超时

- 客户端 FE:src/client/debug-client.ts:471-491（onclose 不拒绝 pending；只有 dispose :519-523 与 30s 超时 :104-107 清理）。影响：断连瞬间面板 query 显示 loading 长达 `REQUEST_TIMEOUT_MS`。修复：onclose 中复用 dispose 的 pending 清空逻辑（reject 'connection closed'）。

### F-11 [P3] CONFIRMED — RATE_LIMITED 退避后倍率不复位（interval 期）

- 客户端 FE:src/client/debug-client.ts:218-227：成功回执仅 `renewBackoff=1`，运行中 interval 保持 2x/3x 周期（20-30s）直到下次 visibility 变化/重连才重建。backoff=3 时 renew 周期 30s ≈ TTL 30s，网络抖动可致活跃使用中租约意外过期。修复：`.then` 中检测 `renewBackoff===1 && 当前 interval 已放大` 时重启心跳（或以变量记录当前生效 backoff 再决定）。

### F-12 [P3] CONFIRMED — "runToCursor" 实发 breakpoints[0]

- 客户端 FE:src/panel/control-buttons.tsx:35-39：`runToPosition(firstBreakpoint)`——目标是**第一个断点**而非光标位置；D5 §3.4 定义 `runToPosition(bp)` 映射同名 method，形状合规但按钮语义失真。修复：接 EditorView selection 行号构造 bp，或改按钮文案。

### F-13 [P3] CONFIRMED — 错误条一旦显示不再清除

- 客户端 FE:src/assembly/xlang-debug-editor.tsx:84-88（onSessionClosed → setConnectError，无复位点）；rebind 被拒重建（FE:src/client/debug-client.ts:375 主动回调 'rebindRejected'）成功恢复后错误条仍常驻。修复：连接成功（session.open 完成）时 `setConnectError(null)`。

### F-14 [P3] CONFIRMED — 4400 一律终态化为 'protocolUnsupported'，标签失真

- 客户端 FE:src/client/debug-client.ts:482-484。D1 §3.8：4400 覆盖解析失败/违规超阈值/版本不支持三因；后两者才该终态。解析失败致 4400 时客户端放弃重连且 reason 误导。修复：以 open 错误 errorCode（protocol-version-unsupported）为终态判据，onclose 4400 走有限重试（防风暴可设上限）或区分 reason 文案。

### F-15 [P3] CONFIRMED — 客户端从不回 pong（基座语义缺口，当前无功能损害）

- spec D1 §3.3：`pong` 是客户端→服务端豁免 notification（基座 ping 的应答面）；D2 §3.3"半开连接由 keepalive ping 探测"。
- 客户端 FE:src/client/debug-client.ts:165-167 静默；服务端 BE:.../DebugWebSocketHandler.java:682-689 发 ping 不追踪 pong——半开探测退化为"写失败才知道"。
- 修复（二选一）：FE 收 ping 回 pong（一行，恢复基座对称性）；或 BE/D2 登记调试点为单向 keepalive、探测靠发送失败。

### F-16 [P3] CONFIRMED — beforeunload best-effort close 未实现（D2 item 17 职责清单差额）

- spec D2 §3.7：item 17 职责含"beforeunload best-effort close"；§一.7 明确服务端不得依赖。FE 全包 grep 无 beforeunload——杀页签走宽限兜底（合规但少一层优化，挂起线程多阻塞 ≤15s）。修复：装配层注册 `beforeunload` → `sendRequest('session.close')` fire-and-forget；登记为可选项。

### F-17 [P3] CONFIRMED — store.lease 只写不读（写死状态）

- 客户端 FE:src/client/debug-client.ts:215,336 写入；grep 全包无消费方（无租约倒计时 UI、不参与续期排程——排程用 `renewIntervalMs`，:384,434）。D5 §3.4 称 lease"供续期排程"，实际排程不依赖它。修复：要么提供租约剩余时间展示（对用户有意义），要么从公共契约摘除以免误导后续开发者。

### F-18 [P3] CONFIRMED — playground openSocket/env 身份不稳 → client 重建 → 重连风暴隐患

- FE:apps/playground/src/pages/xlang-debug-page.tsx:36,58-76（env 与 openSocket 每次 render 新建）× FE:src/assembly/xlang-debug-editor.tsx:74-91,134-143（client useMemo 依赖 `openSocket`；effect 依赖 `[client, openSocket]`，身份变化 = dispose+reconnect）。当前演示页连接后无 state 变更故未触发；任何后续新增页面级 state（如 token 刷新 UI）都会让每次 render 拆一次 WS。修复：page 侧 `useMemo/useCallback` 固化 openSocket，或 editor 侧以 ref 收敛 openSocket（同 getToken 模式，:69-72）。

### F-19 [P3] CONFIRMED（测试架构）— mock 服务器协议放松使 e2e 对语义失真不设防

- FE:tests/e2e/xlang-debug-mock-server.mjs:175-176 `default: reply({})` 对一切未知/拼错 method 回成功；:147-149 expandExprValue 无视 keys 语义（直接掩盖 F-3）；不校验 nopDebugToken/protoVersion。已核实 mock-server 架构本身仍为现行方案（FE:playwright.config.ts:87-90 webServer 拉起；routeWebSocket shim 弃用注释仍在 spec 头部——plan 2476 已知项仍然成立）。修复方向：mock 对 `debugger.*` 未知 method 回 -32601、expandExprValue 按引擎导航语义实现最小版，保住 e2e 的协议回归价值。

### F-20 [P3] REFUTED（两项候选不成立，记录排除理由）

- **候选："store→extension sync 桥泄漏"**：FE:src/extension/sync.ts:47-56 单层 unsubscribe + disposed 幂等 + `dom.isConnected` 守卫，dispose 由 effect cleanup 可达（FE:src/assembly/xlang-debug-editor.tsx:123-128）；StrictMode 双挂载下旧 view destroy → isConnected=false → 桥 apply no-op。**REFUTED，无泄漏**（617 B2 勘正后的现状核实通过）。
- **候选："gutter/Effect/StateField dispose 链缺失"**：三族 Effect 与 StateField 均为模块级无状态单例（FE:src/extension/effects.ts 全文件、fields.ts:9-20、decorations.ts:42-79），随 EditorView.destroy 由 CM 统一析构（FE:packages/flux-code-editor/src/use-code-mirror.ts:97-101）；gutter 走 `gutter()` 扩展随 view 析构；无独立 listeners。**REFUTED**。附注（非缺陷）：use-code-mirror.ts:37 compartments 为模块级单例，Compartment 状态按 EditorState 槽位隔离，单页多编辑器不互扰，但同页多实例共享身份属脆弱点，登记知悉。

---

## 四、生命周期追踪表（每个订阅/定时器/WS 的 dispose 可达性）

| # | 资源 | 创建点（FE file:line） | dispose 路径 | StrictMode 双挂载 | 判定 |
|---|---|---|---|---|---|
| 1 | WS connection | debug-client.ts:411-423 | dispose→close(1000)（:531-536）← editor effect cleanup（xlang-debug-editor.tsx:140-142） | revive 复活（:394-397，plan 621 B3 核实仍成立）；但陈旧 onclose 无守卫 → F-4 | ⚠️ |
| 2 | renewTimer（setInterval） | debug-client.ts:236-240 | stopRenewHeartbeat ← onclose/finish/dispose（:474,500,524） | stop→start 序列安全（先 clear 再 set） | ✅ |
| 3 | visibilitychange listener | debug-client.ts:241-243 | stopRenewHeartbeat removeEventListener（:260-262）；同函数引用 add 幂等 | ✅ | ✅ |
| 4 | reconnectTimer | debug-client.ts:322-324 | finish/dispose clear（:501-504,525-528） | ✅ | ✅ |
| 5 | pending 请求超时器 | debug-client.ts:104-108 | dispose 全清（:519-523）；**onclose 不清** → F-10 | ✅（dispose 路径） | ⚠️ |
| 6 | store.subscribe（sync 桥） | extension/sync.ts:47 | 返回 unsubscribe ← editor effect cleanup（xlang-debug-editor.tsx:123-128）；disposed 幂等（sync.ts:49-56） | 旧 view isConnected 守卫兜底 | ✅ |
| 7 | client.onLog 订阅 | panel/log-panel.tsx:21-30 | effect cleanup 退订 | ✅ | ✅ |
| 8 | client.onCompleted 订阅 | debug-client.ts:553-557 | 接口完备，**无消费者** → F-6 | — | ⚠️ |
| 9 | logSubscribers/completedSubscribers | debug-client.ts:82-83 | 组件级退订；client revive（同实例）时订阅保留——语义正确 | ✅ | ✅ |
| 10 | CodeMirror EditorView | use-code-mirror.ts:89-95 | cleanup destroy（:97-101） | 双挂载安全（destroy→新建） | ✅ |
| 11 | gutter/StateField/Effect/decoration | 模块级单例（见 F-20） | 随 view 析构 | ✅ | ✅ |
| 12 | Compartment 重配置 | use-code-mirror.ts:126-133（extensions 身份变化→`extensions.reconfigure` 全量替换） | StateField 为模块单例故重配置**不丢状态**（breakpointsField 等，xlang-debug-editor.tsx:99-104）；sourcePath 变更 → facet 值替换 ✅ | ✅ | ✅ |
| 13 | languageRegistered 渲染期副作用 | xlang-debug-editor.tsx:22-29,58 | 幂等闩锁，无资源 | 知悉（render 期副作用，可接受） | ✅ |
| 14 | 服务端 keepaliveTask（对照项） | DebugWebSocketHandler.java:109-113 | onClose cancel（:191-192） | — | ✅ |

**结论**：dispose 链在"正常 unmount"维度全部可达、无经典泄漏；缺口集中在**连接状态机**（F-1/F-2/F-4/F-5/F-10）而非资源析构。

---

## 五、重连矩阵仿真（server-drop → reconnect → rebind 逐行走查）

记号：TTL=30s / renewInterval=10s / grace=15s（D2 §3.2 基线；FE 从 `session.open` 回执读取 renewIntervalMs，debug-client.ts:434）。

| # | 场景 | FE 实际行为（行号） | 状态恢复核对 | 判定 |
|---|---|---|---|---|
| R-1 | 服务端掉线（1006），宽限期内重连 | onclose→stop 心跳（:471-474）→退避重连（:315-325，0.5s×2^n×jitter，上限 15s）→open 过渡会话（:426）→rebind 旧会话（:439-444） | 断点：`setBreakpoints(服务端全量)` ✅（:335）；租约：`setLease(expiresAtMs)` ✅（:336）且立即 renew（首豁免，:378-382 ↔ BE:DebugSessionBinder.java:183,219）；挂起：本地已知 threadId 保留位置、未知线程占位 + 异步 `getStackInfo` 补取 ✅（:338-364，符合 D2 §3.6） | ✅（除 F-8 muted） |
| R-2 | 重连后旧会话已销毁（宽限耗尽） | rebind 得 BOUND_ELSEWHERE → rebuildOnNewSession（:446-449） | 断点重发 ✅、心跳 ✅；**suspended 残留** ❌ → F-2；lease 置空缺失（依赖 renew 回执，可接受） | ❌ F-2 |
| R-3 | 会话被他页签 close / serverShutdown（连接未断） | 收 `evt.sessionClosed` → 仅错误条（:139-141） | **无任何失效/恢复动作** → F-1；后续 renew 永久 NO_SESSION 空转 | ❌ F-1 |
| R-4 | 心跳与页面可见性 | 隐藏期 renew 跳过（:236-240）；恢复可见立即 renew + 相位重置（:246-253）——与 D2 §3.1/§3.2 逐条吻合 | RATE_LIMITED 退避（:220-227）符合 D2 §3.1"退避重试、不停循环"；退避不复位 → F-11 | ✅（+F-11） |
| R-5 | 终态关闭码 | 4401/4403→authFailed、4400→protocolUnsupported，stopped 闩锁阻止复活（:476-489,498-506；测试 debug-client.test.ts:273-302,491-502） | ✅（4400 标签失真 F-14） | ✅（+F-14） |
| R-6 | 断连窗口事件丢失 | 不重放、靠 rebind 快照补齐（:328-365）；evt.log/exception 历史与 completed 一次性结果不补——与 D1 §3.5/D2 §3.6"不可补齐"登记一致 | ✅（completed 在线也不呈现 → F-6） | ✅（+F-6） |
| R-7 | 断连放行触发 | 断连即停 renew → 服务端租约自然到期放行（D2 §3.3 ReleasedByLease）或宽限销毁放行；FE 收 `evt.leaseExpired` 清挂起行（:127-130）——但断连窗口内事件本就收不到，恢复呈现依赖 R-1/R-2 快照 | 路径闭合 ✅；"已放行"提示缺失（D2 §3.7 前半已做） | ✅（P3 附注） |
| R-8 | 重连期间用户操作 | transport 在 session.open 后即注册（:435），rebind 往返窗口内断点点击发往**过渡会话**，rebind 成功后过渡会话被服务端销毁（BE:DebugSessionBinder.java:164-167）→ 该断点静默丢失，随后 `setBreakpoints(快照)` 把 UI 也回滚 | 低概率、自愈（UI 与服务端最终一致），用户可感知"点了没生效" | P3 知悉 |
| R-9 | rebind 往返挂死 | 30s 请求超时（:104-107）→ catch → scheduleReconnect（:451）**此时连接仍活** → 双连接竞态 → F-4 路径 B | ❌（归并 F-4） | ❌ F-4 |

**租约时钟核对**：FE 全程不拿 `expiresAtMs` 做本地过期判定（无该逻辑），排程只依据服务端授予的 `renewIntervalMs` —— 符合 D2 §3.1"客户端时钟不可信，一切判定以服务端为准"；`expiresAtMs` 仅入 store（F-17 只写不读）。

---

## 六、已知项核验（audit 委托方列出的 accepted items）

| 已知项 | 核验结果 |
|---|---|
| routeWebSocket shim 闪断 → mock-server 架构（plan 2476） | **仍成立**：FE:tests/e2e/xlang-debug.spec.ts:6-10 弃用注释在；FE:playwright.config.ts:87-90 webServer 拉起 mock（端口 4180）；vite 代理 `/ws/xlang-debugger`+`/r/nop-xlang-debug`（FE:apps/playground/vite.config.ts:17-22） |
| dispose-revive（plan 621 B3） | **仍成立**：debug-client.ts:388-397 复活闩锁 + 测试 debug-client.test.ts:475-489；但 revive 与陈旧 onclose 的交互存在 F-4 缺口 |
| 语句级步进粒度（plan 2475） | 后端引擎议题，FE 侧无对应假设（UI 直接透传 stepX + reason 忽略），**FE 不受影响** |
| StrictMode 双连 discarded-connection 语义 | **部分成立**：demo 页启用 StrictMode（FE:apps/playground/src/main.tsx:21）；设计上接受双连（spec:68-69 按 ≥1 断言），但"旧连接 onclose 晚到践踏新连接状态"超出 accepted 范围 → 升格为 F-4 |

---

## 七、总结与 remediation 建议

### 总结

1. **协议形状层质量高**：D1 全量 24 method（22 暴露 + 2 保留不暴露）与 8 evt 的三方矩阵（§二）无一处帧形状错位；envelope/id/errorCode/鉴权锚点/关闭码分派全部对齐仲裁文档，三个点分错误码逐字一致。前后端背对背实现 + 文档仲裁的协作方式在"形状"维度是成功的。
2. **分歧集中在三个语义层缺口（P1）**：会话终态收敛（F-1）、重建路径状态复位（F-2）、expandExprValue 导航语义（F-3）。共同模式：**e2e mock 无法暴露、单测不覆盖跨层交互**——三项均在真实后端联调（item 21/22）时才会爆发，且 F-3 是核心功能级失效。
3. **连接状态机是薄弱面**（F-4/F-5）：handler 无连接身份守卫 + open 错误路径假设"必有 onclose"不成立。资源析构层（sync 桥/gutter/StateField/Effect/定时器）经逐项追踪**无泄漏**（F-20 两项候选均 REFUTED），617/621 两轮审计的修复仍健在。
4. **D1/D2 文档侧待登记项**：事件 fan-out 拓扑留白（F-7）、rebind 快照缺 muted（F-8）、keepalive pong 对称性（F-15）、beforeunload 职责差额（F-16）。

### remediation 建议（供 item 27 收口拆 plan）

- **P1（建议一个 FE plan 收口"会话状态机收敛"）**：
  1. 抽 `invalidateSession(reason)`：evt.sessionClosed 分支 + rebuildOnNewSession 共用（F-1、F-2），含 reason 四值透传。
  2. `variables-tree.tsx` keys 改 `[]`（F-3），mock 服务器按引擎导航语义重写 expandExprValue + 未知 method 回 -32601（F-19 一并）。
- **P2**：
  3. connect() handler 连接身份守卫 + rebind 失败先关旧连接（F-4）；open 失败兜底重连/终态（F-5）。
  4. DebugPanel 订阅 onCompleted 呈现执行结果（F-6）。
  5. exceptions 上限/清除入口（F-9）；rebind 后回查 isBreakpointsMuted（F-8 FE 侧）；F-7 提交 D1 additive 登记 + BE bridge 广播改造（BE plan）。
- **P3（低带宽批量）**：F-10/11/12/13/14/15/17/18 按上表逐条小改；F-16、F-17 可合并进 F-1 的会话状态机 plan。
- **回归面**：修复后必须补三类测试——①MockConnection 支持"陈旧 onclose 晚到"用例（F-4）；②mock 服务器语义化后的变量树 e2e（F-3）；③多标签页/他端 close 的 evt.sessionClosed 状态收敛单测（F-1）。真实后端联调前，F-3 与 F-4 为**门禁级**修复项。
