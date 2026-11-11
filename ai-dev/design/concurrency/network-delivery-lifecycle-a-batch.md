# WI 4.3 交付与生命周期束 a（R4-26，plan 2560）设计裁定

> Status: active
> Last Reviewed: 2026-11-11
> Source: `ai-dev/audits/2026-10/2026-10-21-0610-deep-audit-nop-network-integration-concurrency/`（13 原样 + MA-12 已修 fresh-session 取证 @ 2026-11-11）；vertx-mqtt 休眠面注记 = §三.5#9（修复照常立项，不要求生产激活证明；激活回升条款 7.1）

## 〇、验收物口径（R1-F1 双口径区分）

**单元台账**：C10 勘误 R5 的 16 处即红标记覆盖整个 4.3 单元 58 工单——核减 3 处过期（C10-008①/C10-012①/C10-006①）后单元台账 13 处记账，**批外项归对应工单**（C10-001②/C10-002/C10-003②③/C10-004②③/C10-005①/C10-011②/C10-012③/C10-013①/C10-014① 等，属 R4-27 及后续工单）。

**本批 in-scope 验收基数（逐条枚举）**：C10-009②（MA-08 四入口注入即红）、C10-006②（MA-09 配对）、C10-007①（MA-14 doStart 抛出）、C10-013③（MA-11 并发恰好一次）、C10-005②/C1-011 restart×N（C1-010/011）、RP-1×2（MA-15）、C10-014②③（MA-15 调用方防御）、C10-004①④（MA-16 close）、MA-12 回归钉 + 各载体新造即红面（MA-10/MA-13/C1-003/C1-005）。

## 一、设计结论（十四条裁定 A-N）

1. **裁定 A（MA-08 token 注入收敛；R1-F3 形态勘正）**：`AddAccessTokenHttpClientEnhancer` ①锁粒度收敛——**分 provider 锁**（`ConcurrentHashMap<String,Object>` computeIfAbsent 锁对象），token fetch 留在 provider 锁内但移出 `authTokens` 监视器（保 per-provider singleflight 防并发击穿 token endpoint；纯 fetch-outside-then-put 会并发重复取 token——显式否决）；fetchToken 经 getHttpClient() raw 委托发起无递归（已核验）。②注入统一覆盖——**override 既有 `DelegateHttpClient.onRequestBegin` 统一钩子（四入口已全部经它：fetchAsync→onFetchBegin→onRequestBegin 等）**，EnhancedClient 的 override 从 onFetchBegin 改为 onRequestBegin（一行，无新增 SPI）；fetchServerEventFlow 直调 onRequestBegin 同受盖。③C6-008 随同 diff。护栏 C10-009②。

2. **裁定 B（MA-09 注册配对）**：`MqttServerMessageService`（:93-113）——AtomicBoolean CAS 注册标志（跨结构注册不再借用 subscribers 空判）+ 空列表 `remove(topic, list)` 两参真移除（computeIfPresent 死码摘除面消除；对照 RpcMessageSubscriptions putIfAbsent/cancel-remove 先例）。

3. **裁定 C（MA-10 stop 世代守卫）**：`NettyTcpClient` stop 世代守卫单点覆盖两类复活路径（已调度重连 :231 + 在途连接建立 :206/:214 connectFuture 覆写）——stop 递增 generation，重连调度与 connectFuture 完成回调校验世代（失配放弃）。护栏 C10-011。

4. **裁定 D（MA-11 bind ticket 原子消费；R1-F4 语义取舍裁定）**：`FeishuBindProvider`（:172-192）**显式取舍 = 选项 (b) remove → exchange → 失败且仍不存在时回插**——保住既有已文档化重试语义（:176-179 注释「failed exchange 可在同一 live ticket 上重试」）且并发恰一次消费成立（remove 原子摘除即胜出；exchange 失败回插以 `putIfAbsent` 防 ABA 覆盖他者写入；回插失败=已被并发消费 → 本次 typed 失败即终态）。选项 (a)（失败即消费，用户重扫码）被拒——删除已文档化契约。护栏 C10-013③。

5. **裁定 E（MA-12 验证型工单）**：已修（plan 2499 commit 44e633da72，ApiContextInterceptor catch-close-rethrow :27-39 + finally-close）。本批 = **回归钉测试**（同步抛 → binder closed 断言）+ 2499 证据登记；C6-009 ThreadLocal 滞留子面移交 CL-025 不在本批。

6. **裁定 F（MA-13 monitor 并发）**：`RpcTaskMonitor` checkFailCount RMW（:115-117）→ AtomicInteger；`cancellable`（:41/:92）volatile。

7. **裁定 G（MA-14 + C6-028 VertxMqttServer 启停两面同提交；R1-F5 形态钉死）**：**注入缝 = VertxMqttServer bean 级 `setVertx(Vertx)`**（非 NopVertx.setVertx——`NopVertx.registerInstance` 已存在，勿重复造全局污染面）；doStart `vertx != null ? vertx : NopVertx.instance()` 缺省回退；doStart 观察/await `listenFuture`（:74），失败抛出（先关闭已创建的 MqttServer）并触发 stop；doStop（:179-192）等待 close 完成——**CountDownLatch + close().onComplete(countDown) + 带超时 await**（防永久挂起）再置空 + **`MqttSessionManager.clear()` 新增**（sessions+subscriptions 两表同清——现无 clear API，新增代码面）。护栏 C10-007①。

8. **裁定 H（MA-15 同步抛统一包络 + RP-1 先改；R1-F2 站点补全 + SSE 面裁定）**：「返回 CompletionStage 的方法不得同步抛」契约统一防护——站点全集：`RpcChannelState`（:80-87/:101-105）、`ClientContextRpcServiceInterceptor`（:131）、`RpcMessageSender`（:61）、`AddAccessTokenHttpClientEnhancer`（:91/96）、`MqttConnection`（:233-239）+ **`MqttServerMessageService.sendAsync:76`（ERR_MQTT_PUBLISH_FAIL 同步抛，R1-F2 遗漏站点 1）** + **`NettyTcpServer.getFirstChannel:279-284`（ERR_NETTY_NO_AVAILABLE_CHANNEL，遗漏站点 2）+ `NettyTcpServer:287-290` NPE 面**——统一 catch → failed future（或防御守卫）。**SSE/Publisher 面裁定**：`fetchServerEventFlow` 返回 `Flow.Publisher` 无 CompletionStage 可包——该入口异常交付走 **Publisher onError 信号**（onRequestBegin 内抛出的异常由 Subscriber onError 接收；包络契约对该入口以 onError 语义落地）。**RP-1 两处先改测试**：`TestMqttMessageRouting:159-160` 与 `TestMqttServerFix:122-126` assertThrows → failed-future 断言（先红后修）。C3-021 PUBACK 失实注释勘正。未防调用方（CancellableRpcClient/PollingRpcClient/RpcInvocationHandler）防御面随批。护栏 C10-014 主。

9. **裁定 I（MA-16 + C6-025 stop close 收敛）**：`JdkHttpClient.stop`（:155-161）补 `HttpClient.close()`（JDK 25 AutoCloseable）；feishu 渠道双 JDK HttpClient（`JdkStreamTransport:46`/`JdkFeishuHttpApi:28`）停止期 close（`FeishuClient.stop` 收敛清单补 httpApi）。护栏 C10-004①④ 部分面。

10. **裁定 J（C1-003 容量守卫原子化）**：`RpcChannelState.prepareSend`（:85-92）check-size-then-put → 监视器收敛或 CAS 守卫。与裁定 H 同文件同方法，单 diff。

11. **裁定 K（C1-005 SimpleRpcClient 回调槽位）**：构造器覆写工厂回调（:50-64）→ 显式合并语义（工厂回调先行、构造器回调后行，或工厂回调不可覆盖时 WARN）；`onChannelOpen/onChannelClose`（:45-46）volatile。护栏 C10-008① 已过期（C1-001 已修）——本面为新增并发测试。

12. **裁定 L（C1-010 FeishuClient 停机快照；R1-F8 精度）**：stop 清空 → **credentials/handler/endpoint 三字段 volatile 化**（cachedToken/tokenExpireAtMs 已 volatile）+ WS 回调线程快照读取（停机窗口僵尸投递/NPE 链消除）。

13. **裁定 M（C1-011 stop→start 重建；R1-F8 补注）**：scheduler `final`（:66）→ volatile 可换引用 + start 重建路径（owned scheduler shutdownNow 后重建，半启动死态消除）；**ownsScheduler=false（外部注入）路径 stop 后 start 显式 fail-fast**（外部 scheduler 生命周期不归本类，documented 不支持）。restart×N 用例（即红面）。

14. **裁定 N（同文件接缝执行序；R1-F2 站点补正）**：六处接缝单 diff 或顺序约束——①VertxMqttServer（G 两面）②MqttServerMessageService（B + **H 站点 sendAsync:76** + 扇出 per-conn 防护面）③MqttConnection（H）④Enhancer（A + H :91/96）⑤RpcChannelState（J + H :80-105 同方法 prepareSend）⑥FeishuClient（I/L/M 三单同方法 stop）。MA-15 先改 RP-1 两测试后修站点（组 1 前置）。

## 二、载体表（14 工单）

| 工单 | 载体 | 模块叶 |
|---|---|---|
| MA-08 | `TestAddAccessTokenEnhancerFix` 扩展（四入口注入断言[先行失败 C10-009②] + 锁外 token fetch 断言） | nop-http-client-oauth |
| MA-09 | `TestMqttServerRegistrationPairing`（CAS 注册/两参真移除/双份分发消除） | nop-vertx-mqtt-server |
| MA-10 | `TestNettyTcpClientStopGeneration`（重连复活/connectFuture 覆写两路径世代失配放弃） | nop-netty |
| MA-11 | `TestFeishuBindProvider` 扩展（并发同 ticket 恰一消费[先行失败 C10-013③]） | nop-integration-feishu |
| MA-12 | `TestApiContextInterceptorSyncThrow`（回归钉：同步抛 → binder closed） | nop-rpc-api |
| MA-13 | `TestRpcTaskMonitorConcurrency`（跨轮 RMW + volatile 可见性） | nop-rpc-core |
| MA-14+C6-028 | `TestVertxMqttServerLifecycle`（listen failed → doStart 抛出[先行失败 C10-007①] + doStop 等待 close + sessionManager 清空；前置注入缝） | nop-vertx-mqtt-server |
| MA-15 | RP-1 两处改写 + 各站点 failed-future 断言（CancellableRpcClient/PollingRpcClient/RpcInvocationHandler 防御面） | 四叶 |
| MA-16+C6-025 | `TestJdkHttpClientStop`（close 调用断言 + 线程计数） | nop-http-client-jdk + feishu |
| C1-003 | `TestRpcChannelStateCapacity`（并发 prepareSend 不超容量） | nop-rpc-core |
| C1-005 | `TestSimpleRpcClientCallbacks`（工厂回调不丢弃 + 晚设置可见性） | nop-rpc-simple |
| C1-010/C1-011 | `TestFeishuClientLifecycleConcurrency` 扩展（停机窗口投递 + restart×N） | nop-integration-feishu |

## 三、回归面与接缝

- 回归基线：`./mvnw -o test -pl nop-network -am`（六叶内存形态）。
- 命名统一：注入缝 = VertxMqttServer bean 级 `setVertx`（勿动 NopVertx——registerInstance 已存在）。
- 硬接缝六处（裁定 N）；RP-1 两处先改测试（组 1 前置）；NopVertx 注入缝 Phase 0（裁定 G 前置）。
