# C10 测试保护力 — nop-network-integration（WI 4.3）

## 审计范围与口径

- **审计对象**：本单元（2026-10-21-0610-deep-audit-nop-network-integration-concurrency）C1/C3/C6 三维度复核后最终保留的 **74 条发现**（去重后口径：P0×1 / P1×21 / P2×39 / P3×13；其中 7 条同案并入条目标注主登记——C6-008 并入 C1-002/C3-008、C6-009 并入 C3-006、C6-014 并入 C1-001、C1-004 并入 C3-010、C3-019 并入 C6-023、C3-020 并入 C1-012、C3-031 并入 C1-015）。
- **审计问题**：对每条保留发现做变异推演——"把缺陷正确修复后，现有哪个测试会红？"与反向问题"缺陷重新引入（或从未修复）时，现有哪个测试会红？"——据此判为 **会红·有保护 / 假保护 / 无测试** 三档。
- **判定口径**：
  - **会红·有保护**：存在触达缺陷本体的测试，缺陷 reintroduce 即红。
  - **假保护**：现有测试触达该站点或其邻接面（甚至钉死了部分健康行为），但对缺陷本体不可判定——缺陷存在与修复后测试同为绿（或反向：钉死了缺陷行为，修复即红）。
  - **无测试**：无任何测试触达缺陷本体路径（站点邻接面有测试的注明）。
- **测试盘点基线**：`nop-network/`（nop-codec、nop-http×6、nop-netty、nop-rpc×5、nop-socket、nop-vertx×2）与 `nop-integration/`（10 叶）全部 `src/test/java` 共 **96 个测试文件**逐一枚举方法名；对矩阵判定关键文件（TestFileTransfer×3、TestServerEvent*×4、TestFeishuClient、TestFeishuBindProvider、TestFeishuCredentialResolution、TestMqttMessageRouting、TestMqttServerFix、TestSocketServerRestart、TestNettyTcpClientReconnect、TestSimpleRpcClient、TestAddAccessTokenEnhancerFix、TestRpcTaskMonitorFix、TestPollingRpcClientFix、TestMessageRpcClientFix、TestRpcMessageHandlerFix、TestBodyPublisherResourceFix、TestJavaEmailSenderErrorPropagation 等）实读全文。源码只读，未做任何修改。
- **方法依据**：`ai-dev/skills/concurrency-resource-audit-prompt.md` C10 正文（执行步骤 1–6）+ 高频缺陷族画像（F1–F11 的"修复不回灌/测试不钉死"面）+ 附录 A 格式。
- **严重度口径**：缺保护登记一律 **P2**（与缺陷本体分开计数，C10 正文第 2 条）；矩阵内注明对应缺陷本体的严重度以排序修复优先级。

---

## 保护力矩阵（74 行）

> 判定列：**会红·有保护 / 假保护 / 无测试**。★ = 同案并入条目的主登记。矩阵按 C1→C3→C6 排列。

### C1 共享可变状态（19 条）

| # | 发现 ID（严重度） | 判定 | 证据（变异推演要点） |
|---|---|---|---|
| 1 | C1-001（P1）★主登记←C6-014 | **无测试** | `TestSimpleRpcClient` 仅测 `DefaultRpcMessageTransformer.fromResponse` 异常包装（testException，L25-42），未触达 `SimpleRpcClientFactory.init()/newInstance()`。init 非幂等 prepend、timer/executor 双建竞态、destroy 后可用三面均无测试可红；修复后亦无红（无回归风险面被钉）。 |
| 2 | C1-002（P2）★主登记←C6-008 | **假保护** | `TestAddAccessTokenEnhancerFix` 钉死 onFetchBegin 令牌注入值语义（testFirstTokenFetchNoNpe/testExpiredTokenRefreshed）——但 `StubHttpClient.fetchAsync` 立即返回、单线程顺序执行，锁持有时长与并发排队不可观测；锁内 HTTP 往返、全 provider 单监视器两面对缺陷存在与修复均绿。 |
| 3 | C1-003（P2） | **无测试** | `RpcChannelState.prepareSend` 容量守卫无任何直接测试；`TestMessageRpcClientFix` 走 MessageRpcClient 正常路径，从不逼近 `maxWaitingRequests` 边界，TOCTOU 越界面无测试。 |
| 4 | C1-005（P2） | **无测试** | 工厂回调槽位双写覆盖（`SimpleRpcClient` 构造器覆盖工厂回调）无测试；`TestSimpleRpcClient` 不经工厂构造客户端。XLangDebugConnector 实仓消费面（回调确定性丢失）在仓内无任何测试断言。 |
| 5 | C1-006（P3） | **无测试** | 非 final `HttpRpcMessageTransformer.INSTANCE`/`DefaultApiResponseNormalizer.INSTANCE` 无专测；`TestHttpRpcMessageTransformerFix` 测转换行为不测字段 final 性。理论风险面，无测试可红（合理）。 |
| 6 | C1-007（P3） | **无测试** | `SimpleRpcServer.start()` 无任何测试（双 start 端口泄漏面）；nop-rpc-simple 仅 1 个测试文件且不触达 server。 |
| 7 | C1-008（P1） | **无测试** | `TestMqttServerFix.testSessionTakeoverClosesOldConnection`（L151-164）只断言接管时 `old.close()` 被调；`testSessionRemovedOnConnectionClose`（L182-201）只断言 sessions 移除。`removeConnection` 的 `subscriptions.remove(clientId)` 无条件清除面——无测试断言"旧连接迟到 close 后新连接订阅仍在"；修复（条件清）与现状（无条件清）测试同绿。 |
| 8 | C1-009（P1） | **假保护** | `TestFeishuCredentialResolution.reconnectUsesCapturedValues`（L248-263）经 `transport.fireClose()` 钉死"onClose→恰好调度 1 次重连"的单发路径——但 onClose/onError 双入口双调度、stop→start 窗口陈旧回调、双活连接三面均无断言（该测试单发 fireClose 且 NoopScheduler 捕获后手工执行，无连接数上界检查）。 |
| 9 | C1-010（P2） | **无测试** | stop 置 null 三字段与 WS 回调线程的僵尸投递/NPE 链：`FakeStreamTransport.deliver` 同步投递且测试从不并发 stop+deliver；tearDown 的 stop() 无投递断言。 |
| 10 | C1-011（P2） | **无测试**（假体主动掩蔽） | `TestFeishuClient.NoopScheduler.shutdownNow()` 只置 flag 不抛 RejectedExecutionException、`scheduleAtFixedRate` 返回 null 不校验——stop→start 重启的 REE 半启动死态在测试假体下**不可构造**；`doubleStartIsIdempotent` 只测 start×2，不测 stop→start。 |
| 11 | C1-012（P2）★主登记←C3-020 | **假保护** | `TestMqttMessageRouting.testMessageServiceSubscribeDispatchesIncoming`（L206-239）顺序钉死 subscribe→dispatch→cancel 停止分发——值容器并发变更 (a)(b) 与边沿挂载竞态需要并发 subscribe×dispatch 交错，顺序测试不可达；修复为 CopyOnWriteArrayList 后该测试同绿。 |
| 12 | C1-013（P2） | **无测试** | `TestNettyTcpClientReconnect.testReconnectStopsAfterMaxRetryCount` 钉死重试次数上限（健康面）；stop 后已调度 doConnect 复活连接面（isStopping() 谓词错位）无测试——无"stop 期间断连→断言无新 channel"用例。 |
| 13 | C1-014（P2）★主登记（CL-027 闭环） | **无测试** | nop-netty 测试目录无任何 SSL 用例（TestNettyTcpClient/ServerFix 均明文管线）；`NettySslEngineFactory.getSslContext` 检查-置位竞态零覆盖。 |
| 14 | C1-015（P2）★主登记←C3-031 | **假保护** | `TestFeishuBindProvider.consumedTicketCannotBeReused`（L239-258）**顺序**钉死单次性（用后复用抛错）——TOCTOU 双消费需两线程同 ticket 并发 `onChannelScanCallback`，顺序测试不可达；修复（原子 remove 先行）后该测试同绿。 |
| 15 | C1-016（P2） | **无测试**（写侧有正面对照） | `TestSocketServerRestart.testBroadcastDoesNotCorruptConcurrentResponses`（L106-170）以真线程钉死**服务端写侧**帧同步（正面保护，防回归价值真实）；但缺陷本体是 **SocketClient 双读入口**（call+recv 并发读流/headerBuf），无测试混用两入口。 |
| 16 | C1-017（P3） | **无测试** | static 非 final 载体清单（TcpChannelInfo.ATTR_KEY 等 6 字段/4 类 + 2 EnumSet 外泄）无专测；理论风险面。 |
| 17 | C1-018（P2） | **无测试** | `TestMqttServerFix` 三个 auth 用例全部用 `CompletableFuture.completedFuture`（同步完成）——异步 checker 完成线程与 event loop 的交错前提在测试中不可构造；`testSessionRemovedOnConnectionClose` 在 handleEndpoint **返回后**手工触发 closeHandler，恰好绕开回调挂载窗口。 |
| 18 | C1-019（P2） | **假保护** | `TestSocketServerRestart.testServerRestartableAfterStop`（L37-64）钉死"stop 后可重启并服务"的功能面（与 C1-019 的前提互证：重启是受支持场景）——但每轮重启泄漏一个常驻 accept 线程的面无断言（无线程计数/线程名断言），缺陷存在时测试绿。 |
| 19 | C1-020（P3） | **假保护** | `TestConcatenatedBodyPublisherFix.testSingleOnSubscribe` 单线程钉死 onSubscribe 恰一次——`currentSubscription` 非 volatile 的 JMM 可见性缺口单线程不可判定；修复加 volatile 后同绿。 |

### C3 异步链（30 条）

| # | 发现 ID（严重度） | 判定 | 证据（变异推演要点） |
|---|---|---|---|
| 20 | C3-001（P1） | **无测试** | OkHttp 三个 enqueue 站点中招两处（fetchAsync/uploadAsync 吞回调异常→future 永不完成）：`TestOkHttpClientImplFix` 仅测请求形态与 null 拒绝；`TestOkHttpClientImpl TestFileTransfer` 全部 happy-path。"body 传输中断→future 必须 completeExceptionally"无用例（连超时护栏断言都没有）。 |
| 21 | C3-002（P1） | **无测试** | JDK 下载 body 阶段取消失效：JDK `TestFileTransfer` 全部用例 cancelToken 传 null，无"下载中途 cancel→读循环终止/任务异常完成"用例；Apache 的对应正确行为同样无测试（矩阵整体缺失，非单实现缺口）。 |
| 22 | C3-003（P1） | **假保护** | `TestServerEventPublisherStreamFix.testNoEventsAfterCancel`（L84-149）断言取消后事件停止派发（isCancelled 行间闸门有效）——但服务端每 20ms 持续发事件，readLine 从不长时间阻塞；线程泄漏本体（静默连接上 readLine 永挂）不可达，且无承载线程终止断言。Thread.sleep(200/600) 时序敏感。 |
| 23 | C3-004（P1 apache/P2 jdk,okhttp） | **无测试** | sidecar 探测嵌套 get(30s)：三个 FileTransfer 测试的 sidecar 端点（`/file.sha256`）均即时应答，阻塞面（悬挂 sidecar、ioThreadCount=1 自锁）无测试；无下载总时长上界断言。 |
| 24 | C3-005（P1） | **无测试**（邻接面有正面保护） | expected==null 分支跳过 rename：JDK/OkHttp `TestFileTransfer` 所有下载用例均有校验源（sidecar `/file.sha256` 已注册或显式 expectedSha256）——null 分支从未被测试触达。**存在即红的先行失败测试缺失**：无"无校验源下载成功且目标文件存在"用例（Apache 的 `testDownloadServerWithoutRangeSupport` 行为等价用例未移植到 JDK/OkHttp）。校验失败清理面（testDownloadChecksumMismatch 断言 target/part 不存在）对校验路径 rename 有正面保护。 |
| 25 | C3-006（P2）★主登记←C6-009 | **无测试** | `ApiContextInterceptor` 全平台零测试（nop-rpc-api 仅 TestRpcMethodReferenceFix）；interceptAsync 同步抛→binder 不关→ThreadLocal 滞留链无覆盖。同文件 sync 版 try/finally 亦无测试对照。 |
| 26 | C3-007（P2） | **无测试** | `TestContextHttpServerFilterFix` 仅测超时头语义（testTimeoutHeaderTreatedAsRemainingDuration）——ContextBinder 跨线程 close 串号链（BaseContext.close 跨线程不清 ThreadLocal）零覆盖，无跨线程 context 断言。 |
| 27 | C3-008（P2）★主登记（C6-008 并入同工单） | **假保护** | `TestAddAccessTokenEnhancerFix` 钉死 fetchAsync 面令牌注入；**downloadAsync/uploadAsync/fetchServerEventFlow 三入口绕过 token** 的中招面（round2 扩展）无断言——EnhancedClient 的三个覆盖缺口入口在测试中返回 null/从未调用；修复（onRequestBegin 统一注入）后无测试变红，也无先行失败测试存在。 |
| 28 | C3-009（P2） | **无测试** | `TestMessageRpcClientFix.testSendFailureFailsPromise` 钉死**正确兄弟** MessageRpcClient 的发送失败传播；SimpleRpcClient 的派生 future 错位（waitFutures 条目滞留/timeout<=0 永久泄漏）无测试——`TestSimpleRpcClient` 不触达 send 失败路径，更无 waitFutures 状态断言。 |
| 29 | C3-010（P2）★主登记←C1-004 | **无测试**（邻接面有保护） | `TestRpcTaskMonitorFix.testCheckTaskStatusSurvivesSyncException` 钉死单任务同步异常不杀监控循环（健康面，正面价值真实）；跨轮回调 checkFailCount 丢失更新与 cancellable 非 volatile 面无测试（无跨轮回调交错的用例）。 |
| 30 | C3-011（P2） | **假保护**（家族 1/8 站点被钉） | `TestPollingRpcClientFix.testSyncThrowInPollCompletesCallerFuture` 钉死 PollTask.call 的 catch-Throwable（该站点真保护）；但家族未防面——CancellableRpcClient.callAsync、PollingRpcClient 首跳 thenCompose 前、RpcInvocationHandler.isAsync 出口——三个站点无任何测试（`TestCancellableRpcClient` 四用例全走正常完成路径）。 |
| 31 | C3-012（P2） | **无测试** | 全模块 HTTP 测试 cancelToken 一律传 null——JdkHttpClient×4/OkHttp×3 的 appendOnCancel 不移除（监听器无界累积）面无任何测试可触达；`TestMessageRpcClientFix`/`TestCancellableRpcClient` 亦无监听器集合状态断言。 |
| 32 | C3-013（P3） | **无测试** | MDC 异步断链：`TestContextHttpServerFilterFix` 无 MDC 断言（记录性缺陷，无测试可红，符合预期）。 |
| 33 | C3-014（P3） | **无测试** | OkHttp per-request timeout 丢弃：无任何 timeout 生效断言（三实现超时语义等价性测试缺失）。 |
| 34 | C3-015（P1） | **无测试**（假体掩蔽） | `FakeStreamTransport.send` 立即返回不入阻塞——sendBinary(...).get() 无超时阻塞单线程 scheduler 的死锁面在假体下不可构造；`heartbeatSendsControlFrameAndIsWired` 只断言帧内容与调度次数。 |
| 35 | C3-016（P2） | **无测试** | stop×在途 connect 僵尸连接：测试无 stop 与 connect 并发/交错的脚本化用例；`FakeStreamTransport.connect` 同步完成使 15s join 窗口不可构造。 |
| 36 | C3-017（P2） | **假保护** | 同 C1-009 证据：`reconnectUsesCapturedValues` 钉死单发重连的值捕获（重连不复解析凭证——正面保护真实）；attemptReconnect 无 connected/代际守卫的双活连接面无断言（无 conn0 迟到 close→第二条连接的脚本）。 |
| 37 | C3-018（P1） | **无测试** | 与 C1-008 同站点同证据（三登记一工单的 C3 侧登记）：迟到 close 回调清空新会话订阅面无测试。 |
| 38 | C3-021（P2） | **假保护·反向钉死** | **两处测试把缺陷行为钉为契约**：`TestMqttMessageRouting.testSendAsyncOnClosedConnectionFails`（L154-161）与 `TestMqttServerFix.testSendAsyncFailsOnClosedConnection`（L117-129）均 `assertThrows(NopException)` + 错误码相等断言——C3-021 的修复方向（改 `failedFuture`）会使两测试**变红**；PUBACK 注释失实面无测试（QoS1 确认语义无从断言）。 |
| 39 | C3-022（P2） | **无测试** | `testConnectionSendAsyncPublishesQoS1` 单线程顺序发布——跨线程 publish 的 nextMessageId 碰撞面无并发用例。 |
| 40 | C3-023（P2） | **假保护**（邻接面有保护） | `TestRpcMessageHandlerFix.testSendAfterChannelInactiveCompletesExceptionally` 钉死 channelInactive 兜底取消（正面）；encode/decode 容错分支吞帧→write promise 正常完成→调用方等满超时的中招面无测试（无坏编码消息用例）。 |
| 41 | C3-024（P1） | **无测试** | `TestSftpClientCredential`/`TestSftpHostKeyChecking` 用 recording-jsch + **离线即拒**（connect 立即失败）钉死 ERR_SFTP_CONNECT_FAIL 包装与 host-key 配置——jsch timeout=0 无限挂起面在离线假体下不可构造；SftpConfig 无 timeout 字段的配置面缺口无测试表达。 |
| 42 | C3-025（P2） | **无测试** | `TestTencentEmailSenderCredential` 仅测凭证解析；吞异常正常返回面无测试。**正确兄弟被钉**：`TestJavaEmailSenderErrorPropagation`（4 用例）钉死 JavaEmailSender 的重抛契约——同接口两实现的失败可见性分裂恰好只保护了对的那一半。 |
| 43 | C3-026（P2） | **无测试** | SocketClient recv 重连链耗尽静默死亡：`TestSocketProtocolFix.testConnectFailureIsRetryable` 钉死可重试面（正面）；重试耗尽→completeExceptionally 无观察者→僵尸客户端面无测试（无耗尽后终态通知断言）。 |
| 44 | C3-027（P3） | **无测试** | checkTimeout 共享异常实例：`TestRpcMessageHandlerFix` 单用例不触达超时清理路径。 |
| 45 | C3-028（P3） | **假保护** | `TestNettyTcpClientReconnect.testSendOnewayFlushesMessage` 钉死成功路径投递；失败连接上 writeAndFlush 静默丢弃面无测试（无连接失败后 oneway 的日志/可观测断言）。 |
| 46 | C3-029（P3） | **无测试** | `NettyTcpServer.sendToChannel` handler==null NPE：`TestNettyTcpServerFix` 三用例（traffic shaping/随机端口/stop 关连接）均装配完整管线，纯推送型管线（无 IRpcMessageHandler）未测。 |
| 47 | C3-030（P3） | **无测试** | TencentSmsSender.sendMultiMessage fail-fast：`TestTencentSmsSenderAreaCode/Credential` 不触达批量发送。**正确兄弟被钉**：`TestJavaEmailSenderErrorPropagation.sendMultiEmailFailsFastOnFirstFailure` 钉死 email 侧的 fail-fast 契约。 |
| 48 | C3-032（P3） | **无测试** | OssFileServiceClient.makeBucket check-then-act：`TestOssFileServiceClientFactoryCredential`/`TestOssConfigCredentialBinding` 仅测凭证装配；409 容忍缺失面无测试（也无 makeBucket 任何测试）。 |
| 49 | C3-033（P2） | **假保护** | `TestServerEventSubscriptionFix` 钉死三个健康面：[DONE]→恰好一次 onComplete（testDoneDispatchesPendingAndCompletesOnce）、cancel 后不派发（testNoEventsAfterCancel，钉 isCancelled 闸门）、pending 数据终态时派发（testPendingDataDispatchedOnComplete）。C3-033 三中招面全部无断言：[DONE] 后迟到 data 仍派发（无 late-data-after-DONE 用例）、终态后 waitForDemand 永久 park（无用例）、jdk 取消被路由为 onError（两集成测试的 onError 均空实现，从不断言）。 |

### C6 资源生命周期（25 条）

| # | 发现 ID（严重度） | 判定 | 证据（变异推演要点） |
|---|---|---|---|
| 50 | C6-001（P0） | **无测试**（门控主动掩蔽） | JDK/OkHttp 下载 happy-path 测试（resume/sidecar/checksum）在 fd 泄漏存在时**全绿**——无任何句柄数/sink 关闭断言（`assertFalse(part.exists())` 断言的是文件系统路径非 fd）。**反向掩蔽**：C6-001 唯一确定性失败的平台（Windows `Files.move` AccessDeniedException）恰好被 `HttpFileTransferTestCondition` 的 `!osName.contains("win")` 门控**整体跳过**（jdk/okhttp 两套测试同门控）——P0 的唯一可观测症状被测试基础设施排除在回归面之外。 |
| 51 | C6-002（P1） | **无测试** | 416 早退遗弃响应体：JDK/OkHttp `TestFileTransfer` **均无 416 用例**（Apache 有 `testDownload416RecoversByFullDownload`——钉的是正确实现）；SSE cancel-at-entry 遗弃流：`testNoEventsAfterCancel` 的 cancel 发生在事件已流动之后，入口即取消窗口无用例；无线程/socket 归还断言。 |
| 52 | C6-003（P2，已知未修复） | **无测试**（正确实现被钉） | Apache `testDownload416RecoversByFullDownload` 钉死正确实现的整体重传自愈；JDK/OkHttp 的陈旧 Range 头缺陷（416 重试必再失败）无对应用例——同一协议矩阵在三个实现上的覆盖不对称，恰好缺失的格子就是缺陷所在的格子。 |
| 53 | C6-004（P1） | **无测试** | 全部 SSE 测试订阅者 `request(Long.MAX_VALUE)`/`request(1)+续订`——demand 停滞后 waitForDemand 无限 wait 面无用例（无"订阅者停止 request→断言承载线程不被永久占用"测试；该测试需带超时，属 C10 步骤 5 形态缺失）。 |
| 54 | C6-005（P1） | **假保护** | `testBase64FormUpload`×3 实现——BASE64_FORM 路径被三套测试触达且钉死内容正确性；`IoHelper.readBytes` 不关流的 fd 泄漏面无断言（无句柄计数/流关闭检查），缺陷存在时测试绿。 |
| 55 | C6-006（P1） | **无测试** | 同 C3-004 证据：sidecar 阻塞/超时后内层 future 不取消——无悬挂端点测试、无"get 超时后连接归还"断言。 |
| 56 | C6-007（P2） | **假保护** | 所有 JdkHttpClient 测试的 tearDown 都调 `client.stop()`——stop() 被高频执行但从不校验 selector 线程/连接池终止；stop 不关 HttpClient 的累积面（启停周期）无断言，缺陷存在时测试绿（且测试自身每个用例泄漏一个 HttpClient，反而常态化了该行为）。 |
| 57 | C6-010（P2） | **无测试** | `SimpleRpcClientFactory` destroy/newInstance 生命周期零测试（与 C1-001 同文件同测试空白）。 |
| 58 | C6-011（P2） | **无测试** | 空 statusMethod 任务滞留 activeTasks：`TestRpcTaskMonitorFix` 两用例不触达账本清理路径；无 addTask→轮询→移除的全链路断言。 |
| 59 | C6-012（P2） | **假保护**（cancel 面有真保护） | `testPollingStopsAfterCancel` 钉死取消停止轮询（正面保护真实）；缺陷本体是"**不取消**+远端不终结→永久轮询"——无 maxPollCount/总时长断言，被遗弃轮询面无测试。 |
| 60 | C6-013（P2） | **无测试** | resume=false 直写最终路径失败残留：三实现测试均用默认 resume=true；`setResume(false)` 组合零用例，截断文件残留面（"文件存在即完整"违约）无断言。 |
| 61 | C6-015（P3） | **无测试** | SimpleRpcServer stop 后 Guard 拒绝重启：服务器类零测试（同 C1-007 空白）。 |
| 62 | C6-016（P3） | **假保护**（轻度反向耦合） | `TestBodyPublisherResourceFix`（3 用例）是 `FileDownloadSubscriber`/`HttpInputFileBodyPublisher` 的**唯一消费者**——死代码的资源关闭纪律被钉死（其正确性恰成 C6-001 的反讽对照），但"main 零生产引用"的漂移面无保护；删除死代码的修复会先红这 3 个测试（需先迁移测试用例）。 |
| 63 | C6-017（P1） | **无测试** | NettyTcpServer bind 失败泄漏 boss/worker 组：`TestNettyTcpServerFix` 全部成功启动（随机端口）；端口占用失败路径无用例，无线程数断言（对照面：SocketServer 的失败清理同样无专测，但其行为已闭合）。 |
| 64 | C6-018（P1） | **无测试** | SftpClient 构造期"session 已连、channel 未开"泄漏：测试假体的 offline connect 在 `session.connect()` 即失败——**恰好到不了** session 成功后 channel.connect() 失败的泄漏窗口；构造失败后无人 disconnect 无断言。 |
| 65 | C6-019（P1） | **无测试** | SesClient 每发新建不 shutdown：`TestTencentEmailSenderCredential` 不触达 sendEmail 发送路径，无 shutdown() 调用断言（对照面：YunpianSmsSender 的 finally close 亦无测试，但其实现已正确）。 |
| 66 | C6-020（P1） | **无测试** | 三登记工单 C6 侧登记（双窗口）：窗口①（CONNACK 后 SUBSCRIBE 先落表、addConnection 接管删除再清掉）与窗口②（迟到 close 清新订阅）均需跨 event loop 交错；`testSessionTakeoverClosesOldConnection` 单线程只断言 close 调用，subscriptions 表状态零断言。 |
| 67 | C6-021（P1） | **无测试** | 同 C3-016 证据（同站点判级分歧对）：stop×在途 attemptReconnect 孤儿 WS 无测试可构造。 |
| 68 | C6-022（P2） | **无测试** | NettyTcpClient connectFuture 停机后覆写：同 C1-013 证据——stop 与重连任务交错无用例。 |
| 69 | C6-023（P2）★主登记←C3-019 | **无测试** | VertxMqttServer.doStart/doStop 零测试（需 Vertx 实例；现有测试全部绕过生命周期方法经反射直调 handleEndpoint）——listenFuture 无观察者面、绑定失败静默面无测试可红。 |
| 70 | C6-024（P1，升格） | **无测试** | 同 C3-015 证据：`JdkStreamTransport` 无任何直接测试（TestJdkFeishuHttpApiOAuth 只测 HTTP API 面），send/close 无超时阻塞面零覆盖。 |
| 71 | C6-025（P2） | **无测试** | feishu 两个 HttpClient 停止期不关闭：同上，JdkStreamTransport/JdkFeishuHttpApi 的生命周期面零测试，无线程数断言。 |
| 72 | C6-026（P2） | **假保护** | `testMessageServiceSubscribeDispatchesIncoming` 顺序钉死订阅→分发→取消停止（健康面）；①并发首订双挂载 dispatcher→双份分发面（需并发 subscribe）与②摘除分支死码面（cancel 后 map 条目永存——顺序测试恰好**依赖**该缺陷面通过："取消后不再分发"在 dispatcher 仍挂载时也绿，因为 list 已空）均不可判定。 |
| 73 | C6-027（P2） | **假保护**（邻接面有保护） | `TestSocketProtocolFix.testConnectFailureIsRetryable` 钉死 IO 段失败可重试（正面，对应 connect 的已闭合 IO 段）；onChannelOpen 回调抛异常→socket 滞留+Guard 锁死面无用例（无回调抛异常脚本）。 |
| 74 | C6-028（P2） | **无测试** | VertxMqttServer.doStop 异步不等待+sessionManager 不清空：doStop 零测试（同 C6-023），重启 bind 冲突与死会话驻留两面无测试可红。 |

### 矩阵统计

| 判定 | 条数 | 占比 |
|---|---|---|
| 会红·有保护 | **0** | 0% |
| 假保护 | **20** | 27%（C1×6：001-002/009/012/015/019/020；C3×8：003/008/011/017/021/023/028/033；C6×6：005/007/012/016/026/027） |
| 无测试 | **54** | 73% |
| 合计 | 74 | 100% |

**核心结论：74 条保留发现中没有任何一条的缺陷本体被现有测试保护**——全部 21 条 P1 与唯一 P0（C6-001）的缺陷引入/存在均不使任何现有测试变红。假保护 20 条中，7 条存在"邻接健康面被正面钉死"的真实保护价值（写侧帧同步、PollTask 同步抛、cancel 停轮询、校验失败清理、channelInactive 兜底、重试上限、单发重连值捕获），修复批必须保持这些测试绿；其余 13 条属于"站点被触达但缺陷面不可判定"。

---

## 反向钉死检查

对任务点名的四类测试逐一核查"是否存在把缺陷行为钉为契约、或主动掩蔽缺陷症状的测试"：

### RP-1【断言级反向钉死】MQTT 下行同步抛契约（C3-021）——2 处命中

- `nop-network/nop-vertx/nop-vertx-mqtt-server/src/test/.../bus/TestMqttMessageRouting.java:154-161`（testSendAsyncOnClosedConnectionFails）与 `.../impl/TestMqttServerFix.java:117-129`（testSendAsyncFailsOnClosedConnection）：
  ```java
  NopException e = assertThrows(NopException.class, () -> connection.sendAsync("topic", "msg", null));
  assertEquals("nop.err.mqtt.not-connected", e.getErrorCode());
  ```
- C3-021 的缺陷本体是"`isConnected()==false` 时**同步 throw** 而非返回 failed future"。两测试把同步抛断言为期望行为——**按审计建议修复（`CompletableFuture.failedFuture(...)`）会使两测试编译/断言红**。修复批必须同步改写（`get(…)` 断言异常完成 + 错误码），且两处注释（"sendAsync 已实现：断开连接时显式失败"）需更正。这是全单元唯一的断言级钉死。

### RP-2【门控级掩蔽】Windows 测试门控排除 P0 唯一确定性症状（C6-001）

- `nop-http-client-jdk`/`nop-http-client-okhttp` 的 `HttpFileTransferTestCondition.isFileTransferReliableOnThisOS()`（`return !osName.contains("win")`）把三套 FileTransfer 矩阵（含 Apache）在 Windows 上**整体跳过**，注释自述"skip on Windows rather than try to chase a transient platform race"。
- C6-001（P0）在 Windows 上是**确定性失败**（FileOutputStream 未关闭 → `Files.move(REPLACE_EXISTING)` 抛 AccessDeniedException）——P0 唯一能被黑盒测试观测到的症状恰好被门控排除；Unix 上泄漏是静默 fd 增量，无断言可观测。默认 CI（linux runner）上矩阵运行但对该缺陷零检出力。
- 门控动机（Windows 服务端句柄释放竞态）合理，但应改为"Windows 上改跑句柄占用断言"而非整体跳过——见 C10-001。

### RP-3【假体级掩蔽】Feishu 测试假体使两类缺陷不可构造（C1-011、C3-015/C6-024）

- `TestFeishuClient.NoopScheduler`：`shutdownNow()` 只置 flag、`scheduleAtFixedRate` 返回 null 且**不抛 RejectedExecutionException**——C1-011 的"stop→start 重启后 REE 半启动死态"在假体下结构性不可构造；`FakeStreamTransport.send` 立即返回——C3-015/C6-024 的 `sendBinary(...).get()` 无超时阻塞唯一调度线程面不可构造；`connect` 同步完成——C3-016/C6-021 的 15s join 窗口不可构造。
- 假体保真度缺口使 FeishuClient 全部 8 条 P1/P2 发现（C1-009/010/011、C3-015/016/017、C6-021/024/025）中无一可被现有测试表达。

### RP-4【耦合级】死代码的唯一消费者是测试（C6-016）

- `TestBodyPublisherResourceFix` 三个用例是 `FileDownloadSubscriber`/`HttpInputFileBodyPublisher` 在全仓的唯一引用——C6-016 的合理修复（删除死代码）会先红这 3 个测试。轻度反向钉死：修复批需先迁移测试到生产路径或删除用例。

### RP-5【环境级假保护】零断言外网冒烟测试

- `TestApacheHttpClient.testHttps`（硬编码 `https://www.baidu.com`）、`testHttp2`（`localhost:8080`）、`testServerEvent`（`localhost:11434` Ollama）、`testDownload`：四个用例**零断言**（仅 System.out + 异常即败），其中 `testServerEvent` 的 `latch.await()` **无超时**。它们既不能检出任何已登记缺陷，又是沙箱/离线环境的挂起风险。`TestJdkHttpClient` 同型（testHttps/testDownload/testServerEvent）。

**反向钉死命中统计：断言级 2 处（同案 C3-021）；掩蔽/耦合级 3 处（RP-2 Windows 门控、RP-3 假体、RP-4 死代码耦合）；环境级假保护 1 组（RP-5）。**

---

## 缺保护登记（C10-001 起，每条 ≥10 行附录 A 格式）

> 按**同案保护面**合并登记；每条覆盖的缺陷本体 ID 在失效面枚举中列全。严重度按 C10 口径统一 **P2**（与缺陷本体分开计数）；条目内注明对应缺陷本体的最高严重度以定修复顺序。

### [C10-001] 下载主路径资源生命周期零检出：fd/句柄断言缺失 + Windows 门控掩蔽 P0

- **文件**: `nop-network/nop-http/nop-http-client-jdk/src/test/java/io/nop/http/client/jdk/TestFileTransfer.java`（全文件）、`.../nop-http-client-okhttp/.../TestFileTransfer.java`（全文件）、`.../HttpFileTransferTestCondition.java:25-28`
- **证据片段**:
  ```java
  // 唯一的"清理"断言是文件系统路径级，非资源级：
  assertFalse(target.exists());
  assertFalse(FileTransferHelper.partFileOf(target).exists());
  // Windows（P0 唯一确定性失败平台）整体跳过：
  static boolean isFileTransferReliableOnThisOS() {
      return !osName.contains("win");   // C6-001 的 AccessDeniedException 永不进入回归面
  }
  ```
- **严重程度**: P2（对应缺陷本体最高 P0：C6-001）
- **保护面场景**: 每次成功下载 sink 输出流不关闭（JDK/OkHttp）；无校验源 .part 不改名；resume=false 直写失败残留。全部为默认参数主路径。
- **触发时序（变异推演）**: 将 C6-001 修复（成功路径 close 或收敛进 sink 终态）回滚为现状——三套 TestFileTransfer 全绿；将 C3-005 修复（null 分支也 rename）回滚——同样全绿（所有用例均有校验源）。无先行失败测试存在。
- **现状**: 下载矩阵覆盖 resume/sidecar/checksum-mismatch 三类，但断言只到文件内容与路径存在性；fd 生命周期零断言；Windows 分支零覆盖。
- **风险**: C6-001 级 P0（每下载泄漏一 fd）可在测试全绿状态下回归；修复后无防回归屏障。
- **建议**: ① Windows 门控改为 Windows 专用断言（下载后尝试对 target 独占重开/删除，或经 OS 句柄计数）；② 增加无校验源用例（`setFetchSidecarChecksum(false)` + 无 expectedSha256 + 服务端无 sha 头 → 断言 target 存在且 `.part` 不存在，**当前即红，可作先行失败测试**）；③ sink 可注入或在 verifyAndRename 后断言 `out == null`（close 幂等化改造的锚点断言）。
- **失效面枚举**: C6-001（P0，JDK/OkHttp 成功路径不关 sink）、C3-005（P1，null 分支不 rename）、C6-013（P2，resume=false 残留）——三案同保护面；Apache 侧已闭合（releaseResources 契约）且有 416/no-range 富矩阵，仅作移植来源。
- **信心水平**: 确定
- **误报排除**: 非要求"真实大规模压测"——fd 断言属 C10 步骤 4 明示的"关闭后断言句柄数"钩子形态，小规模参数化即可。
- **复核状态**: 未复核

### [C10-002] 下载取消与 416 恢复面零覆盖：JDK/OkHttp 缺失恰为缺陷所在的矩阵格

- **文件**: `nop-network/nop-http/nop-http-client-jdk/src/test/java/io/nop/http/client/jdk/TestFileTransfer.java`（无 416/cancel 用例）；对照 `.../nop-http-client-apache/.../TestFileTransfer.java:264-276`（testDownload416RecoversByFullDownload 仅覆盖正确实现）
- **证据片段**:
  ```java
  // Apache（正确实现）有完整 416 自愈用例；JDK/OkHttp（缺陷实现）无对应格子：
  public void testDownload416RecoversByFullDownload() throws Exception {   // apache-only
      Files.write(FileTransferHelper.partFileOf(target).toPath(), CONTENT); // 比远端还"新"
      IHttpResponse response = client.downloadAsync(HttpRequest.get(url("/file-416")), ...);
      assertEquals(200, response.getHttpStatus());   // 自愈成功
  ```
- **严重程度**: P2（对应缺陷本体最高 P1：C3-002/C6-002）
- **保护面场景**: 下载中途取消（body 阶段 cancelToken 失效）、416 早退遗弃响应体、416 重试携带陈旧 Range 头。
- **触发时序（变异推演）**: C6-003 修复（else removeHeader）回滚——JDK/OkHttp 无测试变红（用例不存在）；Apache 同缺陷若引入则其用例红——同一协议矩阵三实现覆盖不对称，缺的格子恰是缺陷格子。
- **现状**: 三实现 javadoc 声明"行为等价"（JDK TestFileTransfer 类注释"与 Apache 实现行为等价（同协议矩阵）"），但等价矩阵不完整：416 恢复、no-range 服务端、require-checksum-without-source 三格仅在 Apache 存在。
- **风险**: 恢复路径（续传核心自愈场景）回归不可检出；取消语义回归不可检出。
- **建议**: 将 Apache 的 416/no-range/require-checksum 三用例移植到 JDK/OkHttp（**416 用例在 JDK/OkHttp 上当前即红，可作先行失败测试**）；增加慢速分块服务端 + cancelToken.cancel 中途用例，断言 future 在 T 内异常完成且连接归还（读循环停止）。
- **失效面枚举**: C3-002（P1，JDK body 阶段取消失效）、C6-002（P1，416 早退+SSE cancel-at-entry 遗弃）、C6-003（P2，陈旧 Range，已知未修复）；Apache/OkHttp-416-TWR 面已闭合不适用。
- **信心水平**: 确定
- **误报排除**: 非平台传输库行为——取消挂接与 Range 头管理均为本模块代码，属可测行为契约。
- **复核状态**: 未复核

### [C10-003] SSE 取消/终态隔离/背压三面断言缺失：现有测试钉死健康闸门但放行全部中招面

- **文件**: `nop-network/nop-http/nop-http-api/src/test/java/io/nop/http/api/client/TestServerEventSubscriptionFix.java`（全文）、`.../nop-http-client-jdk/.../TestServerEventPublisherStreamFix.java:84-149`（testNoEventsAfterCancel）
- **证据片段**:
  ```java
  // 现有最深断言——事件停止派发，且服务端每 20ms 持续供事件：
  Thread.sleep(200); subRef.get().cancel();
  int sizeAfterCancel = events.size(); Thread.sleep(600);
  assertTrue(events.size() <= sizeAfterCancel + 1, "events must stop after cancel");
  // 缺失的三类断言：线程终止、终态后隔离、取消≠错误
  ```
- **严重程度**: P2（对应缺陷本体最高 P1：C3-003/C6-004）
- **保护面场景**: SSE 订阅取消后承载线程泄漏（readLine 永挂）；[DONE] 终态后迟到事件越过 onComplete 派发 + 终态后 waitForDemand 永久 park；jdk 取消被确定性路由为 onError。
- **触发时序（变异推演）**: C3-033 修复（onComplete→cleanup + completed 守卫 + CancellationException 分支 return）回滚——现有 6 个 SSE 测试全绿（无 late-data-after-DONE 用例、onError 空实现从不断言）；C3-003/C6-004 同理（静默服务端不可达）。
- **现状**: 测试钉死 [DONE]-once、cancel 闸门、pending 派发三个健康面（修复批必须保持绿）；三个中招面无表达式。`Thread.sleep` 同步的时序敏感测试 2 处（600ms 宽限），平台性脆弱。
- **风险**: AI 网关流式链路的线程泄漏/Flow 违约回归不可检出；假 onError 污染监控不可检出。
- **建议**: ① 静默服务端（发头不发体）+ cancel 后断言承载线程在 T（如 2s）内存活线程数回落——覆盖 C3-003/C6-002②/C6-004③；② base 类单测补 `[DONE]` 后 feed 迟到 data，断言 onNext 不再调用且 `future.isCancelled()`——**当前即红，可作先行失败测试**；③ jdk 集成测试 onError 收集断言"取消不产生 onError"——**当前即红**；④ demand=1 停止续订 + 事件到达，带超时断言不永久 park（覆盖 C6-004①）。
- **失效面枚举**: C3-003（P1）、C6-004（P1，①基类/apache②/jdk③ 三面）、C3-033（P2，a/b/c 三路径）；TestServerEventPublisherErrorResponse 钉死的非 2xx→onError 面为健康面，保持绿。
- **信心水平**: 确定
- **误报排除**: 非要求 Flow 全规范一致性测试——仅覆盖已登记缺陷的最小可判定断言；Thread.sleep 敏感性按 C10 步骤 3 登记为平台性脆弱，随修复改 latch。
- **复核状态**: 未复核

### [C10-004] HTTP/SFTP/邮件客户端生命周期资源面零检出：无线程计数、无构造期失败回收、无超时断言

- **文件**: `nop-network/nop-http/nop-http-client-jdk/.../TestJdkHttpClient.java`（tearDown 仅 client.stop()）；`nop-integration/nop-integration-sftp/src/test/java/io/nop/integration/sftp/TestSftpClientCredential.java`（offline 假体）、`.../email-tencent/.../TestTencentEmailSenderCredential.java`（仅凭证面）
- **证据片段**:
  ```java
  // 所有生命周期收尾都只有一行，且无任何事后断言：
  @AfterEach void tearDown() { client.stop(); server.stop(0); }
  // sftp 假体：connect 离线即拒——恰好到不了 session 成功后 channel 失败的泄漏窗：
  public void setKnownHosts(String fov) throws com.jcraft.jsch.JSchException { ... } // recording-jsch
  ```
- **严重程度**: P2（对应缺陷本体最高 P1：C6-005/C6-006/C6-017/C6-018/C6-019）
- **保护面场景**: JdkHttpClient.stop 不关 HttpClient（C6-007）；feishu 双 HttpClient 无关闭链（C6-025）；sidecar 阻塞+内层不取消（C6-006/C3-004）；NettyTcpServer bind 失败不关组（C6-017）；SftpClient 构造期 session 泄漏（C6-018）；SesClient 不 shutdown（C6-019）；BASE64 读流不关（C6-005）。
- **触发时序（变异推演）**: 上述 7 案全部修复或全部保持现状——现有测试无任何差异（无线程数断言、无 shutdown 调用断言、无构造失败回收断言、无下载总时长上界）。
- **现状**: 全模块测试对"线程/连接池/句柄"三类资源零断言（C10 步骤 4 的泄漏检测钩子完全缺失）；sftp 测试假体的失败注入点（session.connect）恰好早于缺陷窗口（channel.connect）。
- **风险**: P1 级资源泄漏全部可在全绿测试下回归；启停周期累积与构造期泄漏无防回归屏障。
- **建议**: ① JdkHttpClient 启停 N=3 周期后断言线程名含 `HttpClient-` 的存活线程数≤1（JDK 25 close() 可用后的回归锚）；② recording-jsch 失败注入点上移到 `channel.connect()`，断言 `session.disconnect()` 被调——**当前即红，可作先行失败测试**；③ TencentEmailSender 断言 createClient 产物 shutdown 被调（同法先行红）；④ 悬挂 sidecar 端点 + 断言下载总时长上界（覆盖 C6-006/C3-004 阻塞面与"超时后内层取消"）；⑤ NettyTcpServer 占用端口预置再 start，失败后断言 event loop 线程组终止。
- **失效面枚举**: C6-005/C6-006/C6-007/C6-017/C6-018/C6-019/C6-025/C3-004/C3-024（超时配置面）——九案同保护面（资源生命周期可观测性）；C6-010/C6-015 归 C10-008。
- **信心水平**: 确定
- **误报排除**: 线程计数断言在 CI 的波动风险以"上限≤基线+常量"形态规避，非 flaky 设计；不要求真实远端。
- **复核状态**: 未复核

### [C10-005] FeishuClient 重连引擎与阻塞面不可构造：假体保真度不足以表达任何已登记缺陷

- **文件**: `nop-integration/nop-integration-feishu/src/test/java/io/nop/integration/feishu/client/TestFeishuClient.java`（FakeStreamTransport/NoopScheduler）、`.../TestFeishuCredentialResolution.java:248-263`（reconnectUsesCapturedValues）
- **证据片段**:
  ```java
  @Override public void send(byte[] frameBytes) { sentFrames.add(frameBytes); }   // 永不阻塞
  @Override public void connect(URI uri, IStreamListener listener) { connectCount++; listener.onOpen(); } // 同步完成
  @Override public List<Runnable> shutdownNow() { shutdown = true; return emptyList(); }  // 永不抛 REE
  ```
- **严重程度**: P2（对应缺陷本体最高 P1：C1-009/C3-015/C6-021/C6-024）
- **保护面场景**: onClose/onError 双调度双活连接（C1-009/C3-017）；stop×在途 connect 僵尸 WS（C3-016/C6-021）；send 无超时死锁单线程 scheduler（C3-015/C6-024）；stop 置 null 僵尸投递（C1-010）；重启 REE 半启动（C1-011）；HttpClient 停止期泄漏（C6-025 归 C10-004）。
- **触发时序（变异推演）**: 给 attemptReconnect 加/去 connected 守卫、加/去代际校验、send 加/去超时——`reconnectUsesCapturedValues` 断言"恰好调度 1 次 + 重连复用捕获值"在两种实现下同绿（单发路径钉死，守卫语义不可判定）。
- **现状**: 唯一的重连测试（fireClose 单发）钉死值捕获契约（修复批保持绿）；8 条缺陷面无一可被现有假体表达。
- **风险**: feishu 渠道全部 P1（4 条）与 P2（4 条）回归不可检出；实仓消费方 FeishuConnector 的长连接可靠性无回归屏障。
- **建议**: 假体升级三件套：① `FakeStreamTransport.send/connect` 可编程挂起（CountDownLatch 门）；② `NoopScheduler.shutdownNow` 后 `schedule` 抛 REE（覆盖 C1-011，**当前若补 stop→start 用例即红**）；③ 连接计数断言（任意序列后 `connectCount - closeCount ≤ 1`，覆盖双活/僵尸面）。
- **失效面枚举**: C1-009/C1-010/C1-011/C3-015/C3-016/C3-017/C6-021/C6-024——八案同保护面；C1-015/C3-031（bind TOCTOU）归 C10-013；TestFeishuCredentialResolution 的值捕获断言为健康面保持绿。
- **信心水平**: 确定
- **误报排除**: 非要求真实 WS 集成测试——假体保真度提升后全部缺陷面可脚本化交错（deterministic），符合"小规模参数化模拟"口径。
- **复核状态**: 未复核

### [C10-006] MQTT 会话/订阅复合操作零交错覆盖：单线程测试结构性不可达全部竞态窗口

- **文件**: `nop-network/nop-vertx/nop-vertx-mqtt-server/src/test/java/io/nop/vertx/mqtt/server/impl/TestMqttServerFix.java`（L151-201）、`.../bus/TestMqttMessageRouting.java`（L163-239）
- **证据片段**:
  ```java
  // 接管测试只断言 close 被调——subscriptions 表状态零断言：
  manager.addConnection(first); manager.addConnection(second);
  assertTrue(firstRecorder.invoked("close"), "old connection must be closed on session takeover");
  // 全部 auth 用例同步完成——异步 checker 完成线程交错不可构造：
  server.setAuthChecker((u, p, c) -> CompletableFuture.completedFuture(true));
  ```
- **严重程度**: P2（对应缺陷本体最高 P1：C1-008/C3-018/C6-020）
- **保护面场景**: 迟到 close 清空新会话订阅（C1-008/C3-018/C6-020②）；接管窗口清掉新连接已登记订阅（C6-020①）；异步 auth 下回调挂载晚于 complete（C1-018）；订阅表并发变更/边沿挂载（C1-012/C3-020/C6-026①②）；跨线程 packetId 碰撞（C3-022）。
- **触发时序（变异推演）**: `removeConnection` 修复为条件清（或现状）——`testSessionTakeoverClosesOldConnection`/`testSessionRemovedOnConnectionClose` 同绿（subscriptions 断言不存在）。**先行失败测试可零并发构造**：单线程按序调用 `addConnection(B)`→`subscribe(B,filter)`→`removeConnection(A)`→断言 B 订阅仍在——**当前即红**。
- **现状**: MQTT 测试全部经代理 endpoint + 反射 handleEndpoint 单线程驱动，无任何真实 event loop 交错；已登记的 7 条 MQTT 发现中 0 条可被现有测试判定。
- **风险**: IoT 下行路由静默失效族（三登记 P1 工单）回归不可检出；dispatcher 双挂载双份分发不可检出。
- **建议**: ① 上述单线程交错脚本（覆盖 C1-008/C3-018/C6-020 双窗口，无需真并发）；② 并发 subscribe×N 不同 topic 后断言 `publishListeners.count(dispatcher)==1`（覆盖 C3-020/C6-026①，当前即红）；③ 异步 checker（手工 CompletableFuture）在 complete 回调后再触发 closeHandler，断言 sessions/subscriptions 收敛（覆盖 C1-018）。
- **失效面枚举**: C1-008/C3-018/C6-020（三登记工单）、C1-012/C3-020/C6-026（一 diff 修复单元）、C1-018、C3-022——八案同保护面。
- **信心水平**: 确定
- **误报排除**: 交错脚本属"以确定序列模拟并发交错"的参数化小规模形态，非真实压测要求；单线程脚本依赖的调用序列即审计发现记录的触发时序本身。
- **复核状态**: 未复核

### [C10-007] VertxMqttServer 生命周期（doStart/doStop）零测试：listen 失败静默与停机不收敛不可检出

- **文件**: `nop-network/nop-vertx/nop-vertx-mqtt-server/src/test/java/io/nop/vertx/mqtt/server/impl/`（无任何 doStart/doStop 用例；TestMqttFailClosedNoChecker 仅 auth 面且也经反射绕过生命周期）
- **证据片段**:
  ```java
  // 现有测试的到达方式——绕过生命周期：
  static void handleEndpoint(VertxMqttServer server, MqttEndpoint endpoint) throws Exception {
      java.lang.reflect.Method m = VertxMqttServer.class.getDeclaredMethod("handleEndpoint", MqttEndpoint.class);
      m.setAccessible(true); m.invoke(server, endpoint);   // doStart/doStop 从不执行
  ```
- **严重程度**: P2（对应缺陷本体 P2：C6-023 主登记←C3-019、C6-028）
- **保护面场景**: listenFuture 无观察者（绑定失败静默 ACTIVE）；doStop 异步 close 不等待 + sessionManager 停机不清空（重启 bind 冲突、死会话驻留）。
- **触发时序（变异推演）**: doStart 补 listenFuture 观察/await、doStop 补同步等待+清表——现有测试全绿（它们根本不调这两个方法）；缺陷引入/修复不可区分。
- **现状**: 生命周期两方法的测试覆盖为 0；模块已有 RecordingEndpoint 代理基建可复用（MqttServer.create 不可 mock，需 NopVertx 注入缝或对 doStart 逻辑做保护性重构后测）。
- **风险**: 部署编排（等端口就绪）失效回归不可检出；容器重启编排的静默失效不可检出。
- **建议**: 为 `NopVertx.instance()` 提供注入缝（setVertx）后以代理 MqttServer 断言：① listen 返回 failed future → doStart 抛出或触发 stop（**当前即红，可作先行失败测试**）；② doStop 后 sessionManager 收敛为空 + close 完成前 doStart 二次绑定被拒绝。
- **失效面枚举**: C6-023（主登记←C3-019）、C6-028——两案同保护面；对照兄弟（NettyTcpServer sync+isSuccess、SocketServer 同步 bind）已闭合且无专测（其正确行为同样无回归锚，随本条一并补齐覆盖）。
- **信心水平**: 确定
- **误报排除**: 注入缝改造属测试可达性最小变更，不改产品行为；非要求真实端口绑定测试。
- **复核状态**: 未复核

### [C10-008] nop-rpc-simple 工厂/服务器生命周期零测试：init 非幂等、回调覆盖、waitFutures 滞留、destroy 后可用全不可检出

- **文件**: `nop-network/nop-rpc/nop-rpc-simple/src/test/java/io/nop/rpc/simple/TestSimpleRpcClient.java`（唯一测试文件，25-42 行仅测 transformer 异常包装）
- **证据片段**:
  ```java
  // 模块全部测试内容——与工厂/客户端生命周期无关：
  future = future.thenApply(ret -> DefaultRpcMessageTransformer.INSTANCE.fromResponse("a", fn, res));
  future.whenComplete((ret, err) -> { ref.set(err); });
  assertTrue(ref.get().getClass() == CompletionException.class);
  ```
- **严重程度**: P2（对应缺陷本体最高 P1：C1-001 主登记←C6-014）
- **保护面场景**: init() 每连接重复 prepend + timer/executor 双建（C1-001）；工厂回调槽位被构造器覆盖（C1-005）；发送失败完成派生 future→waitFutures 滞留（C3-009）；destroy 无登记无兜底（C6-010）；start 守卫非原子（C1-007）；stop 后不可重启（C6-015）。
- **触发时序（变异推演）**: `init()` 加/去幂等守卫、`newInstance` 加/去 destroyed 检查——测试同绿（根本不触达工厂）。对照：`TestMessageRpcClientFix.testSendFailureFailsPromise` 已为正确兄弟钉死该契约。
- **现状**: 整个 nop-rpc-simple 的测试只有一个 transformer 单测；SimpleRpcServer 零测试。
- **风险**: dev-tools（XLangDebugConnector）消费面的六案回归不可检出；跨单元已登记 CL-024 站点的复发性无屏障。
- **建议**: ① `newInstance()×N` 后断言 interceptors 长度恒定 + timer 实例唯一（reflection 读字段）——**当前即红，可作先行失败测试**；② send 失败脚本后断言 `channelState.waitFutures` 不含该 id（对齐 MessageRpcClient 契约）；③ destroy 后 newInstance 断言 fail-fast。
- **失效面枚举**: C1-001（主登记）/C1-005/C1-007/C3-009/C6-010/C6-015——六案同保护面（同一工厂/服务器生命周期面）。
- **信心水平**: 确定
- **误报排除**: 非要求真实 socket 集成——SocketClient/SocketServer 已有 localhost 测试基建（TestSocketServerRestart）可复用。
- **复核状态**: 未复核

### [C10-009] oauth enhancer 锁行为与增强覆盖面：值语义被钉死，锁与三入口绕过不可判定

- **文件**: `nop-network/nop-http/nop-http-client-oauth/src/test/java/io/nop/http/client/oauth/enhancer/TestAddAccessTokenEnhancerFix.java`（96-121 行两用例）
- **证据片段**:
  ```java
  // 两个用例都只走 onFetchBegin（fetchAsync 面）：
  assertDoesNotThrow(() -> client.onFetchBegin(request, null));
  assertEquals("Bearer token-1", request.getHeader("authorization"));
  // downloadAsync/uploadAsync/fetchServerEventFlow 三入口在 StubHttpClient 中返回 null——从未被调用
  ```
- **严重程度**: P2（对应缺陷本体 P2：C1-002 主登记←C6-008、C3-008 主登记）
- **保护面场景**: 锁内同步 token HTTP 往返 + 全 provider 单监视器（C1-002/C6-008）；EnhancedClient 仅覆盖 onFetchBegin→download/upload/SSE 三入口完全绕过 token 注入（C3-008 round2 扩展面）。
- **触发时序（变异推演）**: 锁内 IO 移出锁外/分 provider 锁——两用例同绿（StubHttpClient 即时返回，锁时长不可观测）；补齐 onRequestBegin 注入——同样无红无先行失败用例。
- **现状**: 值语义（首次获取/过期刷新）被正面钉死（修复批保持绿）；锁与覆盖面两维不可判定。
- **风险**: token endpoint 抖动放大为全局停摆、凭据配置 oauth 的部署下载/上传一律 401——两者回归均不可检出。
- **建议**: ① SlowStubHttpClient（token 往返挂 200ms）+ N=8 并发线程断言总时长显著低于串行上界（覆盖 C1-002，非 flaky：8×200ms=1.6s 串行 vs 并行 <400ms，阈值 3 倍裕度）；② EnhancedClient.downloadAsync/fetchServerEventFlow 断言 authorization 头存在——**当前即红，可作先行失败测试**（覆盖 C3-008）。
- **失效面枚举**: C1-002（主登记←C6-008）、C3-008（主登记，含 download/upload/SSE 三绕过入口）——同保护面一工单。
- **信心水平**: 确定
- **误报排除**: 并发时长断言以宽松阈值规避 CI 抖动；非"锁粒度优雅性"审查——针对的是已定案缺陷的回归锚。
- **复核状态**: 未复核

### [C10-010] cancelToken 监听器生命周期零覆盖：全模块 HTTP 测试 cancelToken 一律 null

- **文件**: `nop-network/nop-http/*`（三实现全部测试用例 cancelToken 参数恒为 null）；`nop-network/nop-rpc/nop-rpc-core/src/test/.../TestCancellableRpcClient.java`（正常完成路径）
- **证据片段**:
  ```java
  // 生产缺陷站点（JdkHttpClient.fetchAsync 等 10 处 main 站点）的测试触达形态：
  IHttpResponse response = client.downloadAsync(HttpRequest.get(url("/file")),
          DefaultHttpOutputFile.create(target), null, null)   // ← cancelToken == null
  ```
- **严重程度**: P2（对应缺陷本体 P2：C3-012）
- **保护面场景**: 长生命周期 token 上 appendOnCancel 监听器只增不删（JDK×4/OkHttp×3/MessageRpcClient/CancellableRpcClient），每条目钉住 future→response→body 引用图。
- **触发时序（变异推演）**: 全部站点改用 `FutureHelper.bindCancelToken`（修复）或维持现状——无任何测试可区分（token 恒 null，绑定代码从不执行）。
- **现状**: Apache 路径的正确行为（bindCancelToken）同样无测试——平台设施契约在模块内零锚定。
- **风险**: 长驻进程无界增长（F4）回归不可检出；修复后 Apache/JDK/OkHttp 行为等价无回归屏障。
- **建议**: 共享 `MutableCancelToken` 上发起 N=50 次请求，完成 N 次后反射断言 token 监听器集合 size 回落到 0——对正确实现绿、对缺陷实现红（Apache 上先验证该测试形态可行）。
- **失效面枚举**: C3-012 全部 10 站点（jdk×4/okhttp×3/MessageRpcClient/CancellableRpcClient 中招；Apache×4+双 SSE Publisher+PollingRpcClient 已闭合）——一保护面覆盖全家族。
- **信心水平**: 确定
- **误报排除**: 反射断言私有集合属变异测试锚定惯例（仓内已有 failCountOf 先例，TestNettyTcpClientReconnect L31-36）；非泛泛内存泄漏测试。
- **复核状态**: 未复核

### [C10-011] netty/socket 生命周期竞态与失败路径面：重连×停机、重启线程泄漏、重连耗尽终态、回调异常恢复全不可检出

- **文件**: `nop-network/nop-netty/src/test/java/io/nop/netty/tcp/TestNettyTcpClientReconnect.java`（testReconnectStopsAfterMaxRetryCount/testSendOnewayFlushesMessage）、`nop-network/nop-socket/src/test/java/io/nop/socket/TestSocketServerRestart.java`（testServerRestartableAfterStop）、`.../TestSocketProtocolFix.java`（testConnectFailureIsRetryable）
- **证据片段**:
  ```java
  // 重连测试钉死"重试上限后停止"，不触达"stop 后复活"：
  assertTrue(failCountOf(client) > 2, ...); Thread.sleep(500);
  assertEquals(failCount, failCountOf(client), "reconnect attempts must stop after max retry count");
  // 重启测试钉死"可重启"，不断言线程账本：
  assertDoesNotThrow(server::start);
  ```
- **严重程度**: P2（对应缺陷本体最高 P2，全部为本条覆盖面）
- **保护面场景**: stop 后已调度 doConnect 复活连接（C1-013/C6-022）；stop 完成回 CREATED 后 isStopping() 恒假（同前）；restart 每轮泄漏 accept 线程（C1-019）；重连链耗尽静默死亡（C3-026）；onChannelOpen 回调异常锁死状态机（C6-027）；encode/decode 吞帧等超时（C3-023）；共享超时异常（C3-027）；sendOneway 静默丢弃（C3-028）；sendToChannel NPE（C3-029）；SocketClient 双读无守卫（C1-016）；SslConfig 缓存竞态（C1-014）。
- **触发时序（变异推演）**: C1-019 修复（世代号）回滚——testServerRestartableAfterStop 绿（无线程计数）；C1-013/C6-022 修复回滚——重连测试绿（无 stop×reconnect 序列）；C6-027 修复回滚——connect 测试绿（无回调抛异常脚本）。
- **现状**: 该保护面的测试只钉死健康路径（重试上限、可重启、可重试、oneway 成功、inactive 兜底）；全部 11 条缺陷面无表达式。
- **风险**: 停机语义/资源累积/僵尸客户端三类长驻缺陷回归不可检出。
- **建议**: ① stop() 前人为断连（kill 服务端）+ stop 完成后静置 2×retryDelay，断言无新连接建立（C1-013/C6-022）；② restart×N 后断言存活 accept 线程数（线程名过滤）≤N——**当前即红（C1-019），可作先行失败测试**；③ 重试耗尽脚本断言 onChannelClose 回调触发（C3-026）；④ onChannelOpen 抛异常脚本后断言 connect 可重试（C6-027）；⑤ sendOneway 于连接失败 channel 后断言 warn 日志/异常回调（C3-028）。
- **失效面枚举**: C1-013/C6-022/C1-019/C3-026/C6-027/C3-023/C3-027/C3-028/C3-029/C1-016/C1-014——十一案同保护面（netty/socket 运行期与生命周期失败路径）。
- **信心水平**: 确定
- **误报排除**: 线程计数与日志断言（Logback list appender 仓内已有先例：TestSftpHostKeyChecking 的 warns 断言）均为既有测试惯例；非真实网络故障注入要求。
- **复核状态**: 未复核

### [C10-012] RPC 上下文传播与账本清理面：拦截器/上下文类零测试基础

- **文件**: `nop-network/nop-rpc/nop-rpc-api/src/test/`（仅 TestRpcMethodReferenceFix）；`nop-network/nop-http/nop-http-api/src/test/java/io/nop/http/api/server/TestContextHttpServerFilterFix.java`（仅超时头语义）；`nop-network/nop-rpc/nop-rpc-core/src/test/.../TestRpcTaskMonitorFix.java`（无账本用例）
- **证据片段**:
  ```java
  // nop-rpc-api 全部测试内容：
  public class TestRpcMethodReferenceFix { ... }   // 与 ApiContextInterceptor/ContextBinder 无关
  // ContextHttpServerFilter 唯一用例只测 call-expire 头解析：
  assertFalse(ctx.isCallExpired()); assertTrue(ctx.getCallExpireTime() > CoreMetrics.currentTimeMillis());
  ```
- **严重程度**: P2（对应缺陷本体最高 P2：C3-006 主登记←C6-009）
- **保护面场景**: interceptAsync 同步抛 binder 不关（C3-006）；跨线程 close ThreadLocal 串号（C3-007）；RpcTaskMonitor 跨轮 RMW（C3-010 主登记←C1-004）与空 statusMethod 滞留（C6-011）；PollingRpcClient 无总量上限（C6-012 的遗弃面）；MDC 断链（C3-013）。
- **触发时序（变异推演）**: C3-006 修复（catch+close+rethrow）回滚——无红（该类零测试）；C3-010 修复（AtomicInteger）回滚——无红（无跨轮交错用例）。
- **现状**: 上下文传播族在模块内无任何直接测试（TestContextHttpServerFilterFix 的命名暗示曾有 context 断言意图，现仅剩超时头用例）。
- **风险**: 跨租户串号路径（C3-007 标注可升 P0 的潜在面）无回归屏障；监控账本滞留不可检出。
- **建议**: ① proceedAsync 同步抛脚本 + reflection 断言 ThreadLocal 已清——**修复后才有绿，当前该测试对现状也绿（现状即缺陷），补测时直接断言正确行为即红→先行失败测试**；② 跨线程完成脚本断言发起线程无残留 context；③ PollTask 遗弃脚本（不 cancel + 永不完成远端）带超时断言轮询次数有界（当前红，覆盖 C6-012）。
- **失效面枚举**: C3-006（主登记）/C3-007/C3-010（主登记）/C6-011/C6-012/C3-013——六案同保护面（RPC 上下文/账本卫生）。
- **信心水平**: 确定
- **误报排除**: ContextBinder/ThreadLocal 断言经 ContextProvider.currentContext() 公开 API 即可读取，无需反射；非重审平台 BaseContext 本体（CL-011 移交面）。
- **复核状态**: 未复核

### [C10-013] 集成发送器失败语义与绑定单次性：正确兄弟被钉、缺陷实现零覆盖

- **文件**: `nop-integration/nop-integration-email-tencent/src/test/.../TestTencentEmailSenderCredential.java`（仅凭证面）；`nop-integration/nop-integration-sms-tencent/src/test/.../TestTencentSmsSenderAreaCode.java`；对照 `nop-integration/nop-integration-email-java/src/test/.../TestJavaEmailSenderErrorPropagation.java`（4 用例钉死正确实现）
- **证据片段**:
  ```java
  // 正确实现的失败语义被完整钉死（JavaEmailSender）：
  public void sendFailurePropagatesToCaller() { ... }
  public void connectFailurePropagatesToCaller() { ... }
  public void sendMultiEmailFailsFastOnFirstFailure() { ... }
  // 缺陷实现（TencentEmailSender/TencentSmsSender 批量）只有凭证/区号用例：
  public class TestTencentEmailSenderCredential { ... }   // 无 sendFailure 用例
  ```
- **严重程度**: P2（对应缺陷本体最高 P2：C3-025；含 C1-015 主登记←C3-031 的并发面）
- **保护面场景**: TencentEmailSender 吞异常"不抛即成功"（C3-025）；sendMultiMessage fail-fast 无逐条结果（C3-030）；makeBucket 无 409 容忍（C3-032）；FeishuBindProvider 并发双消费 TOCTOU（C1-015/C3-031）。
- **触发时序（变异推演）**: C3-025 修复（未知错误重抛）回滚——无红（无用例）；C1-015 修复（原子 remove）回滚——`consumedTicketCannotBeReused` 绿（顺序复用面在两种实现下行为相同）。
- **现状**: 失败可见性契约的测试只存在于正确实现侧——同接口两实现的语义分裂（审计认定的 F3 漏修形态）在测试层被原样复制。
- **风险**: MFA 验证码静默失败、批量短信重发二义、绑定流并发击穿回归不可检出。
- **建议**: ① 将 TestJavaEmailSenderErrorPropagation 的 4 用例形态移植到 TencentEmailSender（**sendFailurePropagatesToCaller 当前即红，可作先行失败测试**）；② sendMultiMessage 逐条结果断言；③ 并发 onChannelScanCallback（两线程 latch 对齐同 ticket）断言恰好一次 BINDING_COMPLETED——**当前即红**。
- **失效面枚举**: C3-025/C3-030/C3-032/C1-015（主登记←C3-031）——四案同保护面（集成发送/绑定失败语义）；YunpianSmsSender/JavaEmailSender/TencentSmsSender.sendMessage 正确面已闭合（其中两个有测试锚）。
- **信心水平**: 确定
- **误报排除**: 腾讯 SDK 可经本地 HttpServer mock（仓内 TestJdkFeishuHttpApiOAuth 已有同法先例）；非要求真实云服务。
- **复核状态**: 未复核

### [C10-014] 异步完成契约面：OkHttp 吞异常与同步抛逃逸族的未防站点零覆盖

- **文件**: `nop-network/nop-http/nop-http-client-okhttp/src/test/java/io/nop/http/client/okhttp/`（无 future 完成语义用例）；`nop-network/nop-rpc/nop-rpc-core/src/test/.../TestCancellableRpcClient.java`（76-131 行四用例全正常完成）
- **证据片段**:
  ```java
  // TestCancellableRpcClient 的 rpcService 假体只返回正常 future——同步抛路径零触达：
  public CompletableFuture<ApiResponse<?>> callAsync(String m, ApiRequest<?> r, ICancelToken t) {
      return CompletableFuture.completedFuture(success());   // 从不 throw
  }
  ```
- **严重程度**: P2（对应缺陷本体最高 P1：C3-001）
- **保护面场景**: OkHttp onResponse 内抛 IOException→future 永不完成（C3-001）；callAsync 同步抛逃逸族的三个未防站点：CancellableRpcClient/PollingRpcClient 首跳/RpcInvocationHandler（C3-011）；sendToChannel handler 缺失 NPE 契约破坏（C3-029）。
- **触发时序（变异推演）**: C3-001 修复（try/catch→completeExceptionally）回滚——无红（无 body 中断用例）；C3-011 未防站点补防护回滚——无红（假体从不抛）。
- **现状**: `TestHttpRpcServiceFix.testGraphqlRequestErrorsNotDropped` 与 `TestPollingRpcClientFix.testSyncThrowInPollCompletesCallerFuture` 钉死了家族中两个已防站点（正面）；"返回 CompletionStage 不得同步抛、回调内异常必须 completeExceptionally"的平台契约在缺陷站点上零锚定。
- **风险**: 同步 fetch 永久挂起（C3-001 校准为 P1 主路径）回归不可检出；上层清理逻辑随机失效（C3-011）不可检出。
- **建议**: ① OkHttp 本地慢服务端中途断连（半写 body 后 close）+ `future.get(2s)` 断言异常完成——**当前即红，可作先行失败测试**；② CancellableRpcClient 假体改可抛（同步 throw 分支）断言返回 failed future；③ NettyTcpServer 纯推送管线断言 sendToChannel 返回异常完成 future。
- **失效面枚举**: C3-001（fetchAsync/uploadAsync 两中招站点；doDownloadAsync 已保护面）、C3-011 未防三站点（已防三站点有测试锚）、C3-029——同保护面（异步完成契约）。
- **信心水平**: 确定
- **误报排除**: 半写 body 后断连可用 com.sun.net.httpserver 手工控制输出流实现（仓内 SSE 测试已用同 server）；非传输库行为测试。
- **复核状态**: 未复核

### [C10-015] P3 防御性/理论面与死代码：登记为 watch-only，不立项测试

- **文件**: `nop-network/nop-rpc/nop-rpc-core/.../HttpRpcMessageTransformer.java:36`、`nop-integration/nop-integration-api/.../SmsMessage.java:16-17` 等（C1-006/C1-017 载体）；`nop-network/nop-http/nop-http-client-okhttp/.../OkHttpClientImpl.java:101-128`（C3-014）
- **证据片段**:
  ```java
  public static HttpRpcMessageTransformer INSTANCE = new HttpRpcMessageTransformer(); // 非 final，仓库内无重赋值点
  public static int TYPE_DEFAULT = 0;  // 非 final 常量，理论风险
  ```
- **严重程度**: P2→**watch-only**（对应缺陷本体全部 P3：C1-006/C1-017/C1-020/C3-013/C3-014/C6-016）
- **保护面场景**: 非 final static 单例/常量被运行期重赋值（仓库内无触发点）、MDC 记录性缺陷、OkHttp per-request timeout 丢弃、死代码漂移。
- **触发时序（变异推演）**: 理论风险无具体触发时序（审计原判 P3 的理由即在此）；为其立测试违背"不为理论风险立 P2 工单"的校准。
- **现状**: C3-014（OkHttp 忽略 per-request timeout）是本组唯一有真实行为后果的面——建议随 C3-014 修复批补一个 timeout 传递断言（低优先级），其余 watch-only。
- **风险**: 不修复的后果为优雅性/理论面；无 P1 级后果。
- **建议**: watch-only 登记；C6-016 的死代码在删除修复时同步迁移 TestBodyPublisherResourceFix（见 RP-4）；C3-014 若修，补 per-call client 派生的 readTimeout 断言。
- **失效面枚举**: C1-006/C1-017/C1-020/C3-013/C3-014/C6-016——六案全列，均维持不立项裁定；与误报校准第 3 条（低频/理论面不立案测试）一致。
- **信心水平**: 确定
- **误报排除**: 本条自身即为"不立案"的显式登记，防止后续轮次重复评估同一理论面。
- **复核状态**: 未复核

**缺保护登记统计：15 条（C10-001…C10-015），全部按 C10 口径 P2；其中 watch-only 1 条（C10-015）。覆盖全部 P0×1 与 P1×21 的保护面（C10-001…005、008 部分等），P2/P3 缺陷按同保护面搭车覆盖。先行失败测试（对现状即红的用例）共 11 处，已在各条建议中标注。**

---

## 系统性观察

1. **测试栈结构性单线程化：全部 74 条并发缺陷中 0 条可被现有测试表达，根因不是用例数量而是测试基建形态。** 26 个触达并发原语的测试文件里，几乎全部经 `CompletableFuture.completedFuture` 同步假体或反射单线程驱动（Feishu 的 NoopScheduler/FakeStreamTransport、MQTT 的 RecordingEndpoint+反射 handleEndpoint、oauth 的即时 StubHttpClient）；仅有的真线程测试是 TestSocketServerRestart.testBroadcastDoesNotCorruptConcurrentResponses（写侧帧同步）与三处 sleep 时序测试（SSE cancel、netty 重试上限、polling cancel）。**两处假体不仅不覆盖缺陷，还主动掩蔽**：NoopScheduler 永不抛 REE（C1-011 不可构造）、FakeStreamTransport 永不阻塞（C3-015/C6-024 不可构造）。后果与提示词 F10（并发守卫缺失）形成闭环——生产侧无运行时守卫 + 测试侧无并发交错，缺陷从两个方向都不可见。修复批若只补生产守卫不升级假体保真度，守卫本身也将无回归锚。

2. **断言只到"功能结果"，从未到"资源与线程"：P0 在全绿测试下存活是必然而非疏漏。** 全模块 96 个测试文件中，对 fd/句柄数、线程数、监听器集合大小、连接池状态、executor 终止的断言为 **0 处**——C10 步骤 4 要求的"泄漏检测钩子"完全缺失。唯一 P0（C6-001）每个成功下载泄漏一个 fd，而三套 FileTransfer 测试的断言终点是文件内容与 `.part` 路径不存在；更甚者 P0 唯一的黑盒可观测症状（Windows AccessDeniedException）被 `HttpFileTransferTestCondition` 门控整体排除。这是 F8（资源清理不对称）在测试层的镜像：生产代码不对称，测试账本也不对称。建议将"资源账本断言"（线程计数、句柄占用、集合 size 回落）列为后续 WI 的统一测试基建项，而非逐缺陷补丁。

3. **跨实现/跨兄弟的"行为等价矩阵"只钉死了正确的一半——且唯一一处断言级"保护"保护的是错误行为。** 三实现 HTTP 客户端的等价测试声明（JDK TestFileTransfer javadoc"与 Apache 实现行为等价"）只移植了 happy-path 格子：416 恢复、no-range、require-checksum、cancel 四格仅 Apache 有，恰好 JDK/OkHttp 的缺陷格（C6-002/C6-003/C3-005/C3-002）全部落空；同型不对称出现在 MessageRpcClient（有 testSendFailureFailsPromise）vs SimpleRpcClient（无）、JavaEmailSender（有 4 用例失败语义）vs TencentEmailSender（无）、RpcMessageSubscriptions（配对被钉）vs MqttServerMessageService（配对破损）。而全单元唯一的 assertThrows 断言级反向钉死（C3-021 的两处测试）钉的恰是**缺陷契约**（同步抛）——即现有测试对已登记缺陷的净保护力为 0，且存在 2 处负保护。修复路线图应把"等价矩阵补格 + 正确契约先行失败测试（本轮已标 11 处即红点）"作为与代码修复同批的验收物，防止 F3（修实例不修类别）在测试层复发。

---

## 终止判定

**C10 维度收官，无需第 2 轮。**

- 保护力矩阵 74 行闭合：会红·有保护 **0** / 假保护 **20** / 无测试 **54**。
- 反向钉死检查命中：断言级 **2**（C3-021，两测试钉死同步抛契约）；掩蔽/耦合级 **3**（Windows 门控掩蔽 P0 症状、Feishu 假体保真度缺口、死代码测试耦合）；环境级假保护 **1 组**（Apache/JDK 零断言外网冒烟 + 无超时 latch）。
- 缺保护登记 **15 条**（C10-001…015，P2 口径；watch-only 1 条），覆盖全部 P0/P1 缺陷保护面，标注先行失败测试 11 处。
- 收敛依据：① 74 条发现的变异推演均给出确定性判定，每条标注了依据的测试文件与推演结论，无"无法判定"残留；② 测试盘点为 nop-network+nop-integration 全部 26 模块 src/test 全集枚举（96 文件），矩阵关键文件全文实读，盘点完备；③ 三个系统性观察均可由矩阵行聚合复现，无单条孤证。剩余不确定项仅一处已显式登记：C10-007 指出的 VertxMqttServer 生命周期测试需注入缝改造（测试可达性前提），已在该条建议中声明前置条件。

---

## C10 复核结论（WI 4.3 独立复核，2026-10-20）

> 复核员：C10 维度独立复核员。方法=74 条清单与矩阵逐行对账 + 抽样回测试源码验证判定（假保护 7 行 / 无测试及邻接面 9 行，均实读测试文件全文或关键段）+ 反向钉死 5 处断言原文逐字核对 + 登记条目格式与覆盖闭合程序化检查。原审计内容未改动，勘误与归属修正以本节为准。

### 一、对账结果（闭合，差异 0）

- **74 ↔ 74 闭合**：矩阵 C1×19（#1–19）+ C3×30（#20–49）+ C6×25（#50–74）与三维度「最终保留项」逐条对上，无缺行、无多行、无重复。已驳回并入的 7 条（C1-004、C3-019/020/031、C6-008/009/014）正确地不在矩阵中，并入归属注记（★主登记 7 处 + C1-014←CL-027 线索闭环）与三个维度文件的驳回指针全部一致。
- **严重度对账**：矩阵行标注 P0×1 / P1×21 / P2×39 / P3×13，与三维度最终保留分布（C1 3/12/4 + C3 8/15/7 + C6 1/10/12/2）精确一致；C3-004 双判级（P1 apache/P2 jdk,okhttp）、C6-024 升格 P1、C6-003 已知未修复标注均与复核后口径一致。
- **判定分布自洽**：会红 0 / 假保护 20（C1×6 + C3×8 + C6×6）/ 无测试 54，按行清点与矩阵统计表一致。

### 二、抽查结果（16 行实核，判定维持 16 / 修正 0）

**假保护抽查（7 行，全部维持）**：

| 行 | 发现 | 实核证据 | 结论 |
|---|---|---|---|
| 2/27 | C1-002/C3-008 | `StubHttpClient.fetchAsync` 即时 completedFuture（TestAddAccessTokenEnhancerFix L45-50）、downloadAsync/uploadAsync 返回 null 从未被调（L53-62）、两用例仅走 onFetchBegin（L103/115/121）——锁时长与三入口绕过面确实不可观测 | 假保护成立 |
| 8 | C1-009 | `reconnectUsesCapturedValues` 单发 fireClose + `assertEquals(1, scheduler.scheduledCommands.size())` + 手工执行（TestFeishuCredentialResolution），无连接数上界断言 | 假保护成立 |
| 11 | C1-012 | `testMessageServiceSubscribeDispatchesIncoming`（TestMqttMessageRouting L206-239）纯顺序：subscribe→handle→cancel→handle，无并发交错 | 假保护成立 |
| 14 | C1-015 | `consumedTicketCannotBeReused`（TestFeishuBindProvider L239-258）顺序两段式：首次成功→复用抛错；无双消费并发脚本 | 假保护成立 |
| 18 | C1-019 | `testServerRestartableAfterStop`（TestSocketServerRestart L37-64）仅 `assertDoesNotThrow(server::start)`，无线程计数/线程名断言 | 假保护成立 |
| 22 | C3-003 | `testNoEventsAfterCancel`（JDK TestServerEventPublisherStreamFix）服务端 200×20ms 持续供事件，`Thread.sleep(200/600)` + `events.size() <= sizeAfterCancel + 1`，无承载线程终止断言 | 假保护成立 |
| 56 | C6-007 | TestJdkHttpClient 三处 `client.stop()`（L44/60/110）无 selector 线程/连接池终止断言 | 假保护成立 |

**无测试/邻接面抽查（9 行，全部维持）**：

| 行 | 发现 | 实核证据 | 结论 |
|---|---|---|---|
| 1 | C1-001 | TestSimpleRpcClient 全文件仅 testException（transformer 异常包装，L24-40），不触达工厂 | 无测试成立 |
| 7 | C1-008 | `testSessionTakeoverClosesOldConnection`（TestMqttServerFix L151-164）只断言 `invoked("close")`；`testSessionRemovedOnConnectionClose`（L182-201）只断言 sessions 移除——subscriptions 表状态零断言 | 无测试成立 |
| 15 | C1-016 | 广播测试（L106-170）真线程钉死写侧帧同步（正面）；`SocketClient.send(cmd, flush)` 只写不读（SocketClient.java L240），主线程 send + 接收线程 recv 不构成 call+recv 双读入口混用 | 无测试（写侧正面对照）成立 |
| 17 | C1-018 | TestMqttServerFix/MessageRouting 全部 auth 用例 `CompletableFuture.completedFuture`（L122/214/239）；closeHandler 在 handleEndpoint 返回后手工触发（L198-199） | 无测试成立 |
| 21 | C3-002 | JDK TestFileTransfer 三处 downloadAsync cancelToken 恒 null（L177/207/226），无中途取消用例 | 无测试成立 |
| 25/26 | C3-006/C3-007 | nop-rpc-api 测试仅 TestRpcMethodReferenceFix；TestContextHttpServerFilterFix 仅 testTimeoutHeaderTreatedAsRemainingDuration 一用例 | 无测试成立 |
| 29/30 | C3-010/C3-011 | TestRpcTaskMonitorFix 两用例（同步异常不杀循环为邻接正面）；TestCancellableRpcClient 四用例假体只返回正常 future、从不 throw | 无测试（邻接面有保护）成立 |
| 50 | C6-001 | 三套 TestFileTransfer 无任何 fd/句柄断言；唯一清理断言是路径级 `assertFalse(part.exists())`（JDK L239-240）；`@EnabledIf(isFileTransferReliableOnThisOS)` 整类门控（JDK L42） | 无测试（门控掩蔽）成立 |
| 60/69/74 | C6-013/C6-023/C6-028 | 三实现无 `setResume(false)` 用例；MQTT 测试全部经反射 handleEndpoint 绕过 doStart/doStop（TestMqttMessageRouting L127-131、TestMqttServerFix L175-179） | 无测试成立 |

### 三、反向钉死 5 处核实（方向与断言原文全部一致；2 处事实勘误）

- **RP-1（断言级 ×2）**：两处 `assertThrows(NopException)` + `assertEquals("nop.err.mqtt.not-connected", …)` 逐字核实（TestMqttMessageRouting L159-160、TestMqttServerFix L124-126）；"sendAsync 已实现：断开连接时显式失败" 注释在 TestMqttServerFix L123 核实。修复改 failedFuture 将使两测试变红——方向正确。行号微偏差：TestMqttServerFix 引 L117-129，实为 L118-130（不影响判定）。
- **RP-2（门控掩蔽）**：`HttpFileTransferTestCondition` 三份拷贝（apache/jdk/okhttp），`return !osName.contains("win")` 逐字核实，注释自述"skip on Windows rather than try to chase a transient platform race"核实；JDK TestFileTransfer `@EnabledIf` 挂载核实。方向正确。
- **RP-3（假体掩蔽）**：`NoopScheduler.shutdownNow()` 只置 flag 返回空表、`scheduleAtFixedRate` 返回 null 不校验、`FakeStreamTransport.send` 只入 list 永不阻塞、`connect` 同步 `listener.onOpen()`——四处证据逐字核实；`doubleStartIsIdempotent` 只测 start×2 核实。方向正确。
- **RP-4（死代码耦合）**：全仓 grep 证实 `FileDownloadSubscriber`/`HttpInputFileBodyPublisher` 的非定义引用仅有 TestBodyPublisherResourceFix——唯一消费者成立。**勘误 R1**：该测试实际 **4 用例**（testInputFileClosedOnCompletion / testDownloadSubscriberClosesChannelOnCancel / testDownloadSubscriberClosesChannelOnWriteError / testUploadAsyncRejectsNullInputFile，L67/131/154/180），原文「3 用例/三个用例」计数错误，判定不变。
- **RP-5（环境级假保护）**：testHttps/testHttp2/testServerEvent（Apache）与 testHttps/testServerEvent（JDK）零断言核实；`latch.await()` 无超时核实（Apache L101、JDK L109）。**勘误 R2**：`testDownload` 并非零断言——Apache L117 / JDK L59 各有 `assertEquals(200, response.getHttpStatus())`；应表述为「3 用例零断言 + testDownload 仅状态码断言」。该断言对已登记缺陷零区分力，环境级假保护与离线挂起风险结论不变。

### 四、系统性观察核实

- **观察 1（测试栈结构性单线程化）成立**：`new Thread(` 在 nop-network+nop-integration 测试源码全集中仅命中 TestSocketServerRestart 一个文件；MQTT（RecordingEndpoint+反射）、oauth（即时 Stub）、Feishu（NoopScheduler/FakeStreamTransport）的同步假体驱动均经实读证实。微勘误 R3：「仅有的真线程测试」应含同文件 testAcceptLoopSurvivesExecutorRejection（真线程 + sleep，L67-103）；「26 个触达并发原语的测试文件」为口径相关计数（宽口径 grep 实测 34 文件），数量级一致，不构成误判。
- **观察 2（断言只到功能结果）成立**：C10 步骤 4 要求的句柄数/线程数/集合 size 断言在全模块测试中为 0 处（抽-read 六个关键测试文件均证实）。
- **观察 3（等价矩阵只钉正确一半）成立**：Apache TestFileTransfer 有 testDownload416RecoversByFullDownload（L264）/testDownloadServerWithoutRangeSupport（L248）/testDownloadRequireChecksumWithoutSource（L311），JDK/OkHttp 文件清单确认无对应格。

### 五、盘点基线勘误

**勘误 R4**：审计范围声称「96 个测试文件」，实测 nop-network+nop-integration `src/test/java` 全集为 **86 个 .java 文件**（本 worktree 与主仓两个工作副本一致）。计数差 10 个不影响矩阵有效性——全部矩阵行均给出文件级证据，抽查涉及的盘点性否定命题（"无 416 用例""无 SSL 用例""nop-rpc-api 仅 1 测试文件"等）经实查全部成立。

### 六、缺保护登记复核

- **格式**：C10-001…015 编号连续；15 条全部 ≥20 行（要求 ≥10）；12 个附录 A 字段（含领域适配的「保护面场景/触发时序（变异推演）/失效面枚举」）15/15 齐全。
- **先行失败测试计数勘误 R5**：建议字段中「当前即红」类标注实数 **16 处**（14 个建议块，其中 C10-003 与 C10-004、C10-013 各含 2 处），原文宣称「共 11 处」为低估；方向不变，按 16 处计。
- **覆盖闭合**：P0×1 + P1×21 全部 22 条均有唯一主归属（C10-001…005/006/008/014 承接），与原文声明一致。**修正 A1**：**C1-003（P2）** 此前仅存在于矩阵行 3，无任何登记归属——复核裁定主归属补登为 **C10-012**（RPC 账本/守卫卫生面：`RpcChannelState.prepareSend` 的 `maxWaitingRequests` 容量守卫与 waitFutures 账本同属 nop-rpc-core 测试空白面，与该条已列的 C3-010/C6-011 账本面同保护面）。**修正 A2**：**C3-021（P2）** 此前仅存在于矩阵行 38 与 RP-1，无登记条目承接其修复批的两处测试改写义务——复核裁定主归属补登为 **C10-006**（MQTT 复合操作保护面，失效面枚举已含 MQTT 8 案，C3-021 的 sendAsync 同步抛契约面同保护面；RP-1 的测试改写义务随该工单执行）。至此 74 条全部有唯一主归属。**主从声明 M1**：C3-013 双列（C10-012 立项 / C10-015 watch-only）主从可辨，维持；C3-029 双列（C10-011/C10-014）无主从声明，记录 primary=C10-011（矩阵行 46 主证据面），C10-014 为次级面。
- **登记条目三态判定**：15/15 **保留**（含 watch-only C10-015——其「显式不立项」登记价值经复核认可），0 驳回，0 降级。全部条目复核状态由「未复核」更新为「已保留（本节，2026-10-20）」。

### 七、判定统计与收官

| 项 | 结果 |
|---|---|
| 矩阵行三态 | 维持 74 / 修正 0 |
| 判定分布（复核确认） | 会红·有保护 0 / 假保护 20 / 无测试 54 |
| 反向钉死 | RP-1×2 断言级 + RP-2/3/4 掩蔽耦合级 + RP-5 环境级，全部方向核实成立（含勘误 R1/R2） |
| 缺保护登记 | 保留 15 / 驳回 0（立项 14 + watch-only 1）；**最终登记数 15** |
| 覆盖闭合 | 修正后 74/74 唯一主归属（A1：C1-003→C10-012；A2：C3-021→C10-006） |
| 事实勘误 | R1 用例数 3→4；R2 testDownload 含 1 断言；R3 真线程测试清单补 1；R4 测试文件 96→86；R5 先行失败测试 11→16 |
| 抽查误判 | 0 |

**C10 复核收官：原审计判定全部维持，修正均为事实性勘误与账面归属补登，不改变任何保护力结论、严重度与修复优先级排序。本维度无需第 2 轮。**
