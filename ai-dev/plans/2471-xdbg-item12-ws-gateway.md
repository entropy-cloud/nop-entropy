# 2471 XDBG item 12 WebSocket 调试网关（roadmap item 12）

> Plan Status: active
> Last Reviewed: 2026-10-16
> Source: roadmap Work Item 12；设计权威 `01-ws-protocol.md`（协议全量）+ `00-overview` §3.5（新子模块）/`02-lease-lifecycle` §3.3/§3.7（网关职责）/`03-sessionization` §3.2（标记契约；网关不设插入点——D3 §3.2③）/`07-repl-security`（鉴权接缝）
> Related: plans/2469/2470（item 10/11 done）

## Purpose

落地 WS 调试网关：新 Maven 子模块 `nop-dev-tools/nop-xlang-debug-web`——协议编解码（D1 envelope）、会话-连接绑定（session.open/rebind/close）、`debugger.*` 22 方法映射、租约 renew（限流）、事件出站（SessionListener 桥→evt.*）、鉴权（nopDebugToken 查询参数→Authorization 映射复用 `IUserContextExtractor` 契约）、D1 §3.1 强制约束、4401/4403/4400/4409 关闭码。核心 handler 容器无关（纯 Java、测试用内存 WS 会话桩）；quarkus/spring 端点薄壳照 `/ws/jsonrpc` 两份先例落地于各自宿主模块。

## Current Baseline

- WS 基座形态（已核验）：`io.nop.graphql.core.ws.IWebSocketHandler`（onMessage/onClose）+ `IWebSocketSession`（sendMessage/close）通用接口，容器无关；`JsonRpcWebSocketHandler` 为 GraphQL 专用实现（依赖 IGraphQLEngine，D0 §3.4 裁定复用其**模式**而非类）。
- 端点先例（已核验）：quarkus `JsonRpcWebSocketEndpoint`（`@ServerEndpoint("/ws/jsonrpc")` + `Endpoint`，从 EndpointConfig user properties 取握手 headers，`BeanContainer.getBeanByType(IUserContextExtractor.class)` 注入）；spring `JsonRpcWebSocketEndpoint`（jakarta `@ServerEndpoint` + `ServerEndpointExporter`，`nop.spring.jsonrpc-websocket.enabled` 门控）。**两份均为 jakarta @ServerEndpoint 模式；全仓无 TextWebSocketHandler**。
- 会话面（item 8/9）：`SessionDebugManager`（init/createSession 双形态/getSession/destroySession=abort 收尾/shutdownAll/SessionListener 锁外回调）；`DebugSessionHandle.getDebugger()` **包私有**——跨包访问调试器实例的唯一通道是 `SessionListener` 回调参数；`destroySession` 为 abort 语义，**无 graceful dispose 入口**（宽限路径缺口，见 Goals B1 修复项）。
- 引擎事件面（item 10）：`IDebugNotifier.notifyLeaseExpired/notifyResumed/notifySuspend/notifyStepInto/notifyStepOver/notifyStepOut/notifyBreakAt/notifyWarn/notifyLog`；`XLangDebugger.renewLease/releaseAll/releaseThread/getSuspendedThreadIds/getLeaseDeadlineMillis/isClosed/ERR_XLANG_DEBUGGER_THREAD_NOT_SUSPENDED`（模块内 public static，待迁 XLangErrors）。
- 错误码：D1 §3.7 九码表；`ERR_XLANG_DEBUGGER_ALREADY_CLOSED` 已在 `XLangErrors`（nop-xlang），其余 additive 登记归 item 12（本 plan 落地；debug-web 依赖 nop-xlang ✓）。
- 依赖可行性（已核验）：nop-graphql-core 传递依赖无 quarkus 污染；`ILoginService extends IUserContextExtractor`（`extractFromHeaders` 即接口方法），`IUserContextExtractor` 在 nop-api-core——网关对鉴权仅依赖该接口 + 容器注入实现（照端点先例 `BeanContainer.getBeanByType`）。
- 配置（item 8 已注册）：`nop.xlang.debug-web.enabled`（web=true 蕴含 enabled=true fail-fast 已在 Initializer）；D2 租约参数键 `nop.xlang.debug-web.lease.*`（ttl/renew-interval/grace）归本 plan 定义常量。

## Goals

- 新模块 `nop-dev-tools/nop-xlang-debug-web`（parent=nop-dev-tools）。
- 网关核心（容器无关，`io.nop.xlang.debug.web` 包）：
  - `DebugWebSocketHandler`：JSON-RPC envelope 编解码（平台 JSON 工具）、方法分发、`evt.*` 出站（无 id notification，**每连接出站锁**——JSR-356 basic remote 非并发安全）、关闭码语义（D1 §3.8）、入站无 id → id:null -32600 + 重复违规计数超阈值 4400、**D1 §3.1 强制约束**（入站 256KB/expr 8KB/断点 500/keys 50/出站 1MB+截断+1000 条）、keepalive ping 10s 出站定时器、protoVersion 校验。
  - `DebugAuthenticator`：**握手查询参数 `nopDebugToken` 由端点适配器抽取后传入**核心（核心不做容器 API 依赖）→ 构造 `Authorization: Bearer` headers map → `IUserContextExtractor.extractFromHeaders`（D1 §3.2；失败 4401）；tokenRefresh 同映射（否则刷新必 4401）。
  - `DebugSessionBinder`：连接↔会话 1:1 绑定、**onCreated 回调缓存 handle→debugger**（跨包访问通道，D3 §3.2 提权声明）、rebind（属主校验+过渡会话销毁）、宽限销毁走 **graceful dispose 新入口**（B1 修复，见下）、renew 限流（按连接计量+0.8 容差+rebind 首豁免）、销毁发起方记录（reason 映射 userClosed/graceExpired/serverShutdown）。
  - `XLangDebuggerSessionListener`（SessionListener 实现）：onCreated 接线 `GatewayNotifierBridge`（每会话 IDebugNotifier→出站 evt.*）；onDestroyed→出站 `evt.sessionClosed{reason: userClosed/graceExpired/serverShutdown/protocolError}`。
  - `GatewayNotifierBridge`（每会话 IDebugNotifier）：notifySuspend/StepInto/Over/Out/BreakAt→`evt.suspended{reason}` 5 合 1；notifyLeaseExpired→`evt.leaseExpired`；notifyResumed→`evt.resumed{cause:"user"}`；**notifyLog/notifyWarn→`evt.log` 直通单条**（攒批管道归 item 13）；notifyWarn→level=warn 归并。**evt.completed 不在本桥**（无产生源，发射入口预留接口供 item 14/21 调用）。
  - `DebugWebServerConfig`（ICoreInitializer）：web 开关 ⟹ enabled=true（fail-fast 已在 Initializer）+ listener 注册 + lease 配置装载；destroy=shutdownAll。
- **B1 修复项（nop-xlang-debugger 增强）**：`SessionDebugManager.gracefulDispose(handle)`——releaseAll（resume 语义放行，聚合 evt.leaseExpired 已由引擎发）→ 注销 → destroyed 位 → 回调（**不调 close 不置 closed**，D2 §3.3 红线）；`DebugSessionHandle.getDebugger()` 提升为 public（item 12 消费面）。
- quarkus/spring 端点薄壳（**落宿主模块**，B3 裁定）：`nop-quarkus-web` 与 `nop-spring-web-starter` 各增 `XLangDebugWebSocketEndpoint`（jakarta `@ServerEndpoint("/ws/xlang-debugger")`，照 JsonRpcWebSocketEndpoint 结构）——抽取握手 query 参数 `nopDebugToken` 传入核心 handler；两模块 pom 增 debug-web 依赖；spring 侧复用 `ServerEndpointExporter` 机制。
- 错误码登记：`XLangErrors` additive 八码（D1 §3.7 表；ALREADY_CLOSED 既有）+ `XLangDebugger.ERR_XLANG_DEBUGGER_THREAD_NOT_SUSPENDED` 迁移为引用 XLangErrors 常量（消双份定义）。
- 测试：内存 WS 会话桩（线程安全出站捕获）+ 全链路（open→bp.set→挂起→evt.suspended→renew→resume→evt.resumed→close→sessionClosed 顺序断言）、鉴权失败 4401、重复 open 4409+错误响应、rebind 属主校验/跨用户拒绝、renew 限流与 rebind 豁免、宽限 gracefulDispose（**脚本不抛错** + reason=graceExpired）、入站无 id -32600、帧超限、debugger 方法映射抽样（含 TOO_MANY_BPS）、keepalive ping、close 响应先于 sessionClosed 顺序。

## Non-Goals

- evt.log 攒批管道（item 13 直通单条即可）、REPL HTTP 端点（item 14）、quarkus/spring 容器级集成测试、前端客户端、多标签页租约策略细节（D2 已裁定共享租约，binder 只承载）。

## Scope

### In Scope

- 新模块 `nop-dev-tools/nop-xlang-debug-web`（main + test）
- `nop-dev-tools/pom.xml`（modules）、`nop-quarkus-web`（端点薄壳 + pom）、`nop-spring-web-starter`（端点薄壳 + pom）
- `nop-kernel/nop-xlang`：`XLangErrors`（additive 八码）；`nop-dev-tools/nop-xlang-debugger`：`SessionDebugManager.gracefulDispose` + `DebugSessionHandle.getDebugger` 提权 + `XLangDebugger` 错误码常量迁移
- `XLangDebuggerConfigs`（lease 配置键常量）
- 测试（两域）

### Out Of Scope

- item 13/14 功能面；nop-graphql-core/nop-api-core 改动；前端；容器级集成测试。

## Continuation Note（2026-10-16 会话切换点）

Phase 1 已实现并测试绿（57/57）。Phase 2 网关核心进度：
- 已编译通过：`DebugAuthenticator`（nopDebugToken→Bearer→extractFromHeaders）、`GatewayNotifierBridge`（evt.* 出站桥，synchronized(session) 出站锁）、模块 pom（deps: debugger+graphql-core）、nop-dev-tools/pom 注册
- 待实现：`DebugSessionBinder`（连接↔会话绑定/SessionListener 桥/宽限定时器/renew 限流——上轮评审 M1 指出 getDebugger 提权后 binder 可直接持有 handle.getDebugger()，**会话创建键 = "userId:connectionId"** 供属主校验）、`DebugWebSocketHandler`（envelope 编解码+方法分发 22 条+关闭码+D1 §3.1 五桶约束+keepalive 可注入 scheduler）、`DebugWebServerConfig`（ICoreInitializer）、端点薄壳（quarkus/spring）、全部测试
- 注意：nop-xlang 已 install 到本地仓（XLangErrors 八码含 THREAD_NOT_SUSPENDED），debug-web 可直接编译引用；跨模块类变更需 install 或 -am

## Execution Plan

### Phase 1 - 引擎侧配套（宽限 dispose + 错误码 + getDebugger 提权）

Status: completed
Targets: `nop-xlang-debugger` + `XLangErrors`

- Item Types: `Feature`

- [ ] `SessionDebugManager.gracefulDispose(DebugSessionHandle)`：releaseAll 语义放行（引擎 releaseAll 聚合 leaseExpired 已发）→ 注销 → destroyed 位 → onDestroyed 回调；**不调 close 不置 closed 不抛错**（D2 §3.3 红线）
- [ ] `DebugSessionHandle.getDebugger()` 提升为 public（item 12 消费面，D3 §3.2 M4 修复的通道声明同步）
- [ ] `XLangDebugger` 错误码常量迁移：`ERR_XLANG_DEBUGGER_THREAD_NOT_SUSPENDED` 改为引用 `XLangErrors` 新登记常量（消双份定义）
- [ ] `XLangErrors` additive 八码登记（D1 §3.7 表：THREAD_NOT_SUSPENDED/TOO_MANY_BPS/RESULT_TOO_LARGE/NO_SESSION/SESSION_ALREADY_OPEN/SESSION_BOUND_ELSEWHERE/PROTOCOL_VERSION_UNSUPPORTED/RATE_LIMITED）
- [ ] `XLangDebuggerConfigs` 增 lease 配置键常量（ttl-ms=30000/renew-interval-ms=10000/grace-ms=15000，D2 §3.2 参数表）
- [ ] 测试：gracefulDispose 后挂起线程正常返回（不抛错）、destroyed 位路由、closed=false；既有回归绿

Exit Criteria:

- [ ] gracefulDispose 语义测试（挂起线程正常返回不抛 + destroyed 路由 + 不置 closed）
- [ ] 八码登记与 D1 §3.7 表逐条一致
- [ ] `./mvnw test -pl nop-dev-tools/nop-xlang-debugger -am` 全绿
- [ ] owner-doc：XLangErrors 登记 + getDebugger 提权即实现面自身；D3 §3.8 通道声明引用修正（Source 行）随收口核对
- [ ] `ai-dev/logs/` 执行当日条目已更新

### Phase 2 - 网关核心与端点适配器

Status: in progress
Targets: `nop-xlang-debug-web` + 端点薄壳

- Item Types: `Feature`

- [ ] 模块骨架 + `DebugWebServerConfig`（ICoreInitializer：web 开关门 + listener 注册 + lease/grace 配置装载；destroy=shutdownAll）+ META-INF/services 注册
- [ ] `DebugWebSocketHandler`：envelope 编解码（平台 JSON 工具）、方法分发（保留集 ping/pong/tokenRefresh + session.* + lease.renew + debugger.* 22 条 + 未实现 -32601）、出站每连接锁、入站无 id -32600+计数、D1 §3.1 约束五条、protoVersion 校验、关闭码（4401 鉴权/4403 刷新用户不一致/4400 解析与违规超阈/4409 同连接重复绑定/1000 正常）
- [ ] `DebugSessionBinder`：绑定/rebind（属主校验 ERR_XLANG_DEBUGGER_SESSION_BOUND_ELSEWHERE + 过渡会话销毁）/解绑；宽限定时器→gracefulDispose + reason=graceExpired；renew 限流（0.8 容差 + rebind 首豁免）→ RATE_LIMITED；LeasePolicy 授予（open 时 renewLease(ttl)）与 LeaseState 返回
- [ ] `GatewayNotifierBridge`：事件出站（**§3.5 8 条中 5 条**：suspended/log/resumed/leaseExpired/sessionClosed；`evt.completed` 预留发射入口接口供 item 14/21 调用；`evt.logs`/`evt.exception` 归 item 13 不在本桥）+ evt.sessionClosed reason 映射（userClosed/graceExpired/serverShutdown/protocolError 四值）+ keepalive ping 10s 出站定时器（scheduler 可注入，测试禁用/缩短周期）
- [ ] `DebugAuthenticator`：nopDebugToken → Authorization 映射 → IUserContextExtractor.extractFromHeaders；tokenRefresh 同映射
- [ ] 端点薄壳：`nop-quarkus-web` XLangDebugWebSocketEndpoint（jakarta `@ServerEndpoint("/ws/xlang-debugger")`，**从 handshake query 参数抽 nopDebugToken 传入核心**，BeanContainer 取依赖）+ `nop-spring-web-starter` 同构（jakarta @ServerEndpoint + 既有 ServerEndpointExporter 机制，`@ConditionalOnProperty` 挂 debug-web 开关门——照 jsonrpc 先例换键名）；两模块 pom 增 debug-web 依赖；**禁用态语义**：开关关时端点不注册（spring）或 onOpen 直接 close（quarkus 容器扫描态）——两形态都写明；**装配**：DebugAuthenticator/依赖经 BeanContainer/静态单例惯例进容器（照端点先例，Nop IoC 无注解扫描）
- [ ] 测试（内存 WS 桩）：全链路顺序断言（D1 §3.9）、鉴权 4401、重复 open 错误响应+4409、rebind 属主/跨用户、限流+豁免、宽限 gracefulDispose+reason=graceExpired、入站无 id、帧超限、方法映射抽样（getStackInfo/getExprValue/updateBreakpoints/TOO_MANY_BPS）、keepalive ping、close 响应先于 sessionClosed

Exit Criteria:

- [ ] 全链路集成测试绿（事件顺序 D1 §3.5/§3.9 一致；close 响应先于 sessionClosed）
- [ ] D1 §3.1 约束逐条有断言（五桶对应表行：①入站 256KB ②expr 8KB ③断点 500 ④keys 50 ⑤出站 1MB+1000 条截断；懒展开行为在执行器侧非网关强制点；renew 频率走限流条目）
- [ ] 版本化/protoVersion 不支持（ERR_PROTOCOL_VERSION_UNSUPPORTED+4400）、4403 tokenRefresh 用户不一致、NO_SESSION、RESULT_TOO_LARGE 断言补齐
- [ ] 关闭码/错误码/限流/属主校验逐条有断言
- [ ] `./mvnw test -pl nop-dev-tools/nop-xlang-debug-web -am` 全绿；quarkus/spring 模块编译通过
- [ ] 无静默跳过（未实现 method -32601；bridge 全方法有行为）
- [ ] `ai-dev/logs/` 执行当日条目已更新

### Phase 3 - 独立审计与收口

Status: planned
Targets: 全部、roadmap

- Item Types: `Proof`

- [ ] 独立子代理 closure audit：**D1 §3.1-§3.9 逐节对账**（含 §3.1 约束、§3.2 鉴权、§3.3 映射 24 条、§3.4 会话租约 schema、§3.5 事件、§3.6 版本化、§3.7 错误码、§3.8 关闭码、§3.9 时序）+ 变异探针（鉴权门禁/限流计数/宽限路径）+ Anti-Hollow + 线程安全审查（三写者出站锁）
- [ ] 修复审计发现
- [ ] roadmap item 12 → `done`
- [ ] `ai-dev/logs/` 收口记录

Exit Criteria:

- [ ] audit 发现全修复
- [ ] 测试门全绿
- [ ] roadmap item 12 `done` 且 MVP milestone 未翻转（依赖 13/14）
- [ ] `ai-dev/logs/` 收口记录

## Closure Gates

- [ ] 全部 in-scope 行为实现且有测试
- [ ] Anti-Hollow：WS 帧→分发→会话调试器→出站事件全链运行时连通
- [ ] D1 §3.1-§3.9 逐节对账无遗漏
- [ ] owner docs：XLangErrors additive 登记 + D1 §3.7 表与实现一致 + xlang-execution 基线（上轮已同步）+ module-groups（module-groups 明文 item 23 完整注册——本 plan 不动）
- [ ] 独立子代理 closure audit 完成并记录证据
- [ ] `./mvnw test -pl nop-dev-tools/nop-xlang-debug-web -am` + quarkus/spring 模块编译 通过
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2471-xdbg-item12-ws-gateway.md --strict` 退出码 0
- [ ] 改动面仅声明文件（新模块 + nop-dev-tools/pom + 两端点宿主模块薄壳 + XLangErrors + debugger 模块四文件[SessionDebugManager/DebugSessionHandle/XLangDebugger/XLangDebuggerConfigs] + IXLangDebugger + 测试）

## Deferred But Adjudicated

### 一次性调试票据

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: D1 §3.2 裁定一期以短 TTL access token 达标；签发/核销端点归 item 25 评估后的 remediation。
- Successor Required: `yes`
- Successor Path: item 25 安全审计。

## Non-Blocking Follow-ups

- quarkus/spring 容器级集成测试（@QuarkusTest）→ 宿主验证阶段。
- evt.log 攒批管道 → item 13。

## Closure

Status Note: （待收口后填写）
Completed: （待填写）

Closure Audit Evidence:

- Reviewer / Agent: （待填写）
- Evidence: （待填写）
