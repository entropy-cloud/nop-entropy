# C1 共享可变状态与可见性 — nop-network-integration（WI 4.3）

## 审计范围与口径

**批次覆盖**：本文件为 WI 4.3 CRA 的 **批次 1/2**，覆盖基线 `_tmp/cra-baseline-nop-network-integration.txt` 26 叶分节中 C1a/C1b/C1c 圈定的 11 个源码根（均为 src/main 手写口径）：

- `nop-network/nop-http/`：nop-http-api（46f）、nop-http-client-apache（10f）、nop-http-client-jdk（8f）、nop-http-client-oauth（6f）、nop-http-client-okhttp（3f）、nop-http-server-filters（1f）
- `nop-network/nop-rpc/`：nop-rpc-api（18f）、nop-rpc-core（30f）、nop-rpc-http（3f）、nop-rpc-model（16f 手写 + 8 个 `_gen` 文件按纪律跳过）、nop-rpc-simple（3f，WI 2.1 CL-024 原始站点，实审）

批次 2（其余 15 叶：nop-netty / nop-socket / nop-vertx / nop-codec / nop-integration-*）由另一初审员覆盖；`nop-socket/SocketClient`、`SocketServer`、`ClientConfig` 等被 nop-rpc-simple 调用的类不在本批次，相关交互只在证据中引用其公开方法签名，不在本批次立案。

**Protected Area 声明**：`nop-http-api`、`nop-rpc-api` 属跨模块公共 API（`nop-*-api`）。本审计为叶级只读审读，未做任何代码修改；发现条目仅陈述现状与修复方向。

**口径纪律**：
1. 全部发现基于 live code 逐文件实读，数量结论人工复现，不直接引用基线 grep 计数（基线 C1a 节命中多为 import/static 方法/static final 常量噪声，经人工复筛后本批次真实 static 可变载体仅 2 处非 final 单例字段，见清单表）。
2. `_` 前缀生成文件（`_gen/*`）不作为发现对象。
3. 降档口径：本模块为"请求触达 + 长驻运行期面"（HTTP 客户端连接池、RPC 通道均为运行期对象），**启动期降档不适用**的判断按逐条发现认定；`@PostConstruct` 内一次性写入、IoC 生命周期内发布、此后只读的字段按误报校准第 1/3 条不立案（见清单表 OK 项）。
4. 盲审纪律：未读 `ai-dev/audits|plans|bugs|lessons|backlog/**`（本目标文件除外），未查 git log；发现现状一律标"未复核"。

**线程语境**（代入 Nop 并发约定）：HTTP 客户端实现为 singleton bean，`fetchAsync` 等被多个请求线程/回调线程并发调用；SSE 读循环跑在客户端 executor 或 Apache IO reactor 线程；RPC 通道（`RpcChannelState`）的 `prepareSend`/`onReceive` 分别跑在调用方线程与消息接收线程。

**批次 2 覆盖声明（C1 收官）**：以下为 WI 4.3 CRA C1 维度**批次 2/2** 覆盖的其余 15 个源码根（均为 src/main 手写口径，逐文件实读）：

- `nop-network/nop-netty/src/main/java`（31f：NettyChannelHelper/NettyHelper/channel + handlers 9 类 + tcp/NettyTcpClient、NettyTcpServer、TcpChannelInfo + http/ssl/config；NettyHttpClient/NettyHttpServer/NettyHttpHelper 为空占位类）
- `nop-network/nop-socket/src/main/java`（11f：SocketClient、SocketServer、BinaryCommand、ByteBufferHelper、configs、接口与错误码）
- `nop-network/nop-vertx/nop-vertx-commons`（3f：NopVertx、VertxHelper、错误码）+ `nop-vertx-mqtt-server`（13f：VertxMqttServer、MqttConnection、MqttSessionManager、MqttServerMessageService、SimpleMqttAuthChecker、MqttTopicMatcher、message beans）
- `nop-network/nop-codec/src/main/java`（6f：IBinaryCodec/IPacketCodec/AbstractByteBufCodec/GZip/Deflate/ByteBufHelper——全部无实例状态，编解码器跨消息复用面为 PacketCodecHandler 的 per-channel 实例，见清单表）
- `nop-integration/` 10 叶：api、feishu（FeishuClient/JdkStreamTransport/JdkFeishuHttpApi/FeishuBindProvider/codec 全读）、email-java、email-tencent、file-local、oss、sftp、sms-tencent、sms-yunpian、zxing

批次 1 文中"批次 2（其余 15 叶…）由另一初审员覆盖"的移交声明由本节替代：批次 2 与批次 1 由不同初审员盲审完成后合并于本文件。**C1 维度两批次至此收官**：批次 1 = C1-001..C1-007 + CL-001..004（nop-http/nop-rpc 11 根）；批次 2 = C1-008..C1-017 + CL-005..010（本批次 15 根）。

**批次 2 口径纪律**（与批次 1 相同，差异点注明）：① 数量结论人工复现——基线 C1a 节命中经逐条复筛，批次 2 真实 static 非 final 可变载体为 6 字段/4 类（TcpChannelInfo.ATTR_KEY、CloseOnErrorHandler.INSTANCE、TestFragmentChannelHandler.INSTANCE、NopVertx._instance、SmsMessage.TYPE_DEFAULT/TYPE_PROMOTION），另含 2 处 static final 可变集合经 getter 泄漏（MqttServerMessageService 的两个 EnumSet）与 1 处共享空数组常量（BinaryCommand.EMPTY_BYTES），全量清单见 C1-017；基线其余命中均为 import/static 方法/static final 常量噪声。② ThreadLocal：基线 C1c 节 15 根全部为空，人工复现一致——批次 2 无 ThreadLocal 声明。③ 降档口径：本批次为"请求触达 + 长驻运行期面"（TCP/MQTT/WS 长连接、心跳/重连循环均为运行期对象），启动期降档不适用；`@PostConstruct` 单发装配、IoC 生命周期发布后只读的字段按误报校准第 1/3 条不立案（见清单表 OK 项）。④ 盲审纪律：未读 `ai-dev/audits|plans|bugs|lessons|backlog/**`（本目标文件既有内容除外），未查 git log；发现现状一律标"未复核"。⑤ 批次 2 证据中引用的 `SimpleRpcClient`（批次 1 立案站点）仅作消费方佐证，不在批次 2 重复立案。

**批次 2 线程语境**：Netty handler 全部运行在 channel 的 event loop 线程（入站事件与经 pipeline 调度的出站写）；Vert.x MQTT 的 endpoint handler 运行在连接的 event loop context；FeishuClient 的 WS 回调运行在 JDK HttpClient 的调用执行线程，重连/心跳运行在单线程 scheduler；集成发送器（email/sms）为 singleton bean 逐次建连，请求线程并发进入。

## 共享可变状态清单表（批次 2：字段 × 写者 × 读者 × 同步机制 × 判定）

| 载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|
| `RpcMessageHandler.futures`（普通 HashMap）+ `timer` | channelRead/write/checkTimeout/channelInactive——全部经 executor 归位到 IO 线程 | 同左 | 线程 confinement（L35 注释声明 + 实读全部写点核验：send() 非 event loop 时 executor.execute 投递；handler 逐管道 new，无 @Sharable） | OK（不变量成立；`executor/channel` 为 volatile，跨线程读安全） |
| `PacketCodecHandler.decodeErrorCount/encodeErrorCount`（普通 int） | encode/decode（IO 线程） | 同左 | per-channel 实例（无 @Sharable）+ IO 线程 confinement | OK；但 encode/decode 失败吞异常语义 → **CL-008（C3/F9）** |
| `TcpChannelInfo.sendBytes/recvBytes/sendCount/recvCount`（volatile long） | ConnectionInfoHandler/MessageCounterHandler 的 RMW（event loop 线程；出站 write 由 Netty 调度至 event loop） | getChannelInfos（管理线程） | volatile + 写者 event loop confinement（RMW 单写者成立） | OK；`ATTR_KEY` 非 final static → **C1-017** |
| `NettyChannelGroupHandler.group`（@Sharable 共享） | channelActive/Inactive（多 event loop） | NettyTcpServer.getChannelById/getAnyChannel 遍历 | ChannelGroup（DefaultChannelGroup 内部并发容器） | OK |
| `NettyTcpClient.connectFuture`（volatile） | doConnect（caller/event loop 线程） | getConnectFuture/sendAsync 任意线程 | volatile | OK；doStart 写 `bootstrap/retryPolicy` 非 volatile——生命周期管理面单线程，OK；stop 后重连守卫 → **C1-013** |
| `NettyTcpServer.channelGroup/globalTrafficShapingHandler/bootstrap/port` | doStart（写）→ bind 提交任务建立 HB → initChannel（event loop）读；doStop 置 null | 同左 | 生命周期 HB（bind sync） | OK；doStop 置 null 与在途 initChannel 的理论竞态（管理面低频，登记不立案）；`sendToChannel` 无 handler 时 NPE（健壮性，登记不立案） |
| `SslConfig.clientSslContext/serverSslContext`（普通字段） | NettySslEngineFactory.getSslContext 检查-置位（event loop 线程，每连接） | 同左 | 无 | **缺陷 → C1-014** |
| `SocketClient.headerBuf`（共享 ByteBuffer）+ socket 流 | recv 读循环 / call() / ping | 同左 | 无（单读线程约定仅存于消费方 SimpleRpcClient 注释） | **缺陷 → C1-016** |
| `SocketClient.closed`（volatile）/`lastWriteTime`（volatile）/LongAdder×4/connectFailCount | send/heartbeat/recv 循环 | 同左 + 管理读 | volatile/LongAdder | OK；写侧 synchronized(os) 帧同步正确；`onChannelOpen/onChannelClose` 非 volatile——与批次 1 C1-005 同族（setter 晚设无 HB），批次 1 已立案该家族，此处登记不另立案；`connectFailCount` 从未递增（死字段，非并发问题） |
| `SocketServer.stopped`（volatile）/`connections`(CHM)/`connectionCount`(AtomicInteger) | doStart/doStop；accept 线程；processCommand 线程 finally | 同左 + broadcast/sendTo | volatile + CHM（remove(k,v) 条件删）+ LongAdder | OK；doStop closeAllConnections 后由 reader 线程 finally decrement——收敛正确（异步但最终一致）；`handler/onChannelOpen/onChannelClose/config` 启动期写 → executor.execute 提交建立 HB → OK |
| `NopVertx._instance`（private static 非 final 非 volatile） | registerInstance（仓库内当前无调用点） | instance()（VertxMqttServer.doStart） | 无 | 类初始化/登记先于消费时 OK；理论可见性缺口 → **C1-017** |
| `VertxMqttServer.sessionManager`(final)/`publishListeners`(COW) | 构造/addPublishListener | event loop 分发 | final + CopyOnWriteArrayList | OK（对照正确实现）；异步 authChecker 下的注册线程问题 → **CL-010** |
| `MqttConnection.lastPingTime`（普通 long）/`closed`(volatile) | ping()（event loop） | isAlive()（任意线程，如会话巡检） | 无（写者 event loop 单线程；读仅 advisory——陈旧值只影响保活判定余量） | OK（登记）；`closed` 检查-置位在单 event loop 串行（disconnect/close handler 同 endpoint 同 loop）→ OK |
| `SimpleMqttAuthChecker.users`（普通 Map） | setUsers（启动期） | checkAuthAsync（event loop） | 无（启动后只读） | OK（运行期热更无 HB，防御性备注） |
| `MqttSessionManager.sessions/subscriptions`（CHM） | addConnection/removeConnection/subscribe/unsubscribe（多 event loop） | findConnections | CHM 原语 + **复合操作无原子性** | **缺陷 → C1-008**（removeConnection 无条件清订阅） |
| `MqttServerMessageService.subscribers`（CHM→ArrayList 值）+ dispatcher 挂载 | subscribe/cancel（应用线程） | dispatch（event loop） | CHM 仅护 map 结构 | **缺陷 → C1-012** |
| `FeishuClient.started/connected`（volatile） | start/stop（synchronized on this）/回调线程 | sendMessage/sendHeartbeat/scheduleReconnect | volatile + 生命周期锁 | start/stop 幂等守卫原子 → OK；`credentials/handler/endpoint` 非 volatile 跨线程读写 → **C1-010**；stop→start 与 scheduler 释放 → **C1-011**；onClose/onError 双调度 → **C1-009** |
| `FeishuClient.cachedToken/tokenExpireAtMs`（volatile） | ensureToken（任意发送线程） | 同左 | volatile 单字段；复合（检查-取-置）非原子 | 并发首刷重复 fetch token（有界 stampede，幂等值）——登记不立案（对照批次 1 C1-002 锁内 IO 的反面形态） |
| `JdkStreamTransport.webSocket/listener`（volatile） | connect/close | WS 回调线程/send | volatile | OK；connect 无重复连接守卫 → C1-009；send 无超时 → **CL-005** |
| `JdkStreamTransport` 匿名 Listener.`buffer`（ByteArrayOutputStream） | onBinary（WS 投递线程） | 同左 | JDK WebSocket 单连接回调不并发保证 | OK |
| `FeishuBindProvider.tickets`（CHM） | createBindTicket/onChannelScanCallback/purgeExpired | 同左 | CHM + removeIf（弱一致安全） | **单消费 TOCTOU → C1-015**；`requireHttpApi` 惰性初始化双构建（无状态实例，登记不立案） |
| `SftpClient.jsch/channel/session`（单实例） | connect/close + 全部操作方法 | 同 | 无（无守卫无线程安全声明） | 同族契约缺陷，与 C1-016 合并登记（现消费方 newClient 逐次新建规避 → 登记不另立案）；`close()` 与在途操作竞态 → fail-fast 异常，无泄漏 |
| `OssFileServiceClient.client`（共享 AmazonS3，SDK 线程安全契约）/无实例状态 | init（@PostConstruct）构造 → IoC HB | 请求线程 | final + SDK 契约 | OK；`makeBucket` 检查-创建竞态（同 bucket 首发并发一次性失败，自愈）→ P3 登记不立案 |
| `JavaEmailSender.session` | getSession（synchronized 惰性） | createMimeMessage/connectTransport | synchronized | OK；Transport 逐次建连 → 无共享连接态 |
| Tencent/Yunpian SMS、ZxingQrcodeService、CredentialResolutionSupport、FeishuPbCodec/FeishuJsons、MqttTopicMatcher、ByteBufferHelper/ByteBufHelper/BinaryCommand 静态方法 | — | — | 无状态（逐调用局部变量） | OK |
| `FileServiceResource.status` | getFileStatus（synchronized 惰性取数） | length/lastModified/exists | synchronized(this)（内层 synchronized 冗余但无害） | OK；getInputStream 的 FilterInputStream close 联动关 client → F8 配对正确 |
| `LocalFileServiceClientFactory`（newClient()=this 共享） | — | — | 无共享可变状态（委托 resourceStore 只读引用） | OK |
| static final 可变集合：`MqttServerMessageService.CAPABILITIES/WARN_ONCE`（EnumSet）、`BinaryCommand.EMPTY_BYTES` | 类初始化 | getCapabilities 返回引用外泄 / getData() 外泄 | 无防御性拷贝 | **缺陷（防御性）→ C1-017** |
| 各模块 config beans（NettyBaseConfig/SslConfig 基本字段/SocketClient/SslConfig 配置字段/MailConfig/SftpConfig/OssConfig/FeishuCredentials） | IoC 装配（启动期） | 运行期只读 | IoC 生命周期 HB | OK（误报校准第 3 条） |

---

## 共享可变状态清单表（字段 × 写者 × 读者 × 同步机制 × 判定）

| 载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|
| `SimpleRpcClientFactory.timer/executor/ownTimer/ownExecutor/interceptors/destroyed`（L32-38，全非 final/volatile） | `init()`（**每次 `newInstance()` 都执行**）、`destroy()` | `newInstance()`、所创建的 RpcInvocationHandler（按引用共享 interceptors 列表） | 无 | **缺陷 → C1-001** |
| `SimpleRpcServer.interceptors/executor/socketServer/handler` | `init()`/`start()`（生命周期单发） | handleCommandSync（executor 线程） | `Guard.checkState`（非原子） | **缺陷（低危）→ C1-007** |
| `AddAccessTokenHttpClientEnhancer.authTokens`（CHM）+ `authConfigs`（普通字段） | `getAccessTokenFromProvider` 双检 + `synchronized(authTokens)`；setter（启动期） | onFetchBegin 请求线程 | CHM + monitor | **缺陷（锁内 IO）→ C1-002** |
| `RpcChannelState.waitFutures`(CHM)/`closed`(volatile)/`maxWaitingRequests`/`hook` | prepareSend/onReceive/onChannelClose；setter 启动期 | 同左 + timer 超时任务 | CHM + volatile；容量守卫非原子 | **缺陷 → C1-003**；hook/maxWaitingRequests 运行期只读（setter 仅启动期，OK） |
| `RpcTask.checkFailCount`（普通 int） | RpcTaskMonitor 定时轮询的 whenComplete 回调线程 + `updateTaskStatus` 外部线程 | handleTaskStatus / statusStorage | 无 | **缺陷 → C1-004** |
| `RpcTaskMonitor.activeTasks`(CHM)/`timerFuture`/`cancellable` | addTask/removeTask 任意线程；doStart/doStop 生命周期；checkTaskStatus（timer 线程） | 同左 | CHM；timerFuture/cancellable 无 volatile | 部分缺陷：cancellable 跨线程读写无 HB，后果仅"stop 后多跑一轮"（P3 级，登记不立案） |
| `SimpleRpcClient.onChannelOpen/onChannelClose`（普通字段） | 工厂 newClient()（写到 SocketClient 槽位）→ 构造器覆盖同槽位；setter（可后置） | SocketClient IO/定时线程回调 lambda | 无 | **缺陷 → C1-005** |
| `AbstractServerEventSubscription.demand/future`（monitor 内）、`isCancelled/completed`(volatile)、`httpStatus/headers/id/event/data`（SSE 解析态） | request/cleanup（订阅者线程）；processLine/onStart/onComplete（读流线程） | 同左 | synchronized(this) + volatile + **线程 confinement 约定**（onStart→newServerEvent 同线程） | OK，但依赖"完成信号全部同线程串行"的隐式不变量（Apache 路径 3 处 onComplete 同一 reactor 线程串行；JDK 路径仅 1 处）；无具体并发交错可立案，登记为脆弱不变量 |
| `JdkHttpClient.client/executor`、`ApacheHttpClient.client/defaultRequestConfig`、`OkHttpClientProvider.client`、`DefaultHttpClientFactory.enhancedHttpClient`、`RpcServiceProxyFactoryBean.serviceBean` | `@PostConstruct`/doStart（启动期） | 全部请求线程 | IoC 生命周期（容器 start 完成后才暴露 bean/受理请求） | OK（误报校准第 3 条：启动路径粗粒度发布合理） |
| `HttpClientConfig`（全字段可变 @ConfigBean） | 启动期 IoC 装配 | 各客户端实现运行期只读快照（apache/jdk 在 start() 中消费） | 无（但写读窗口不重叠） | OK；注意 `maxRequestsPerHost` 默认 0 → **CL-003（C5）** |
| `CompositeX509TrustManager.ignoreSSLCert`（普通 boolean） | 启动期（SSLContext.init 前） | TLS 握手线程 | 无（配置后只读） | OK（同上）；公开 setter 若运行期翻转则无 HB，登记为防御性备注 |
| `RpcInvocationHandler.methods`(CHM)、`interceptors`(final 引用) | computeIfAbsent（任意调用线程） | invoke（任意线程） | CHM；列表内容共享自工厂 | CHM OK；列表内容变更面归入 C1-001 |
| `AopRpcServiceInvocation.index` | proceed/proceedAsync | 同实例 | 单请求单实例（invocation-per-call） | OK |
| `PollingRpcClient.PollTask.errorCount/timerFuture` | 轮询回调链（前轮 whenComplete → schedule 串行链）；cancel 线程 | 同左 | timerFuture volatile；errorCount 串行链 | OK；`schedule()` 的 `isDone` 检查与 cancel 交错至多多余一轮轮询（P3，登记不立案） |
| `RpcMessageSubscriptions.subscriptions`(CHM)、`Subscription.suspended`(volatile) | register/putIfAbsent、suspend/resume | callAsync 任意线程 | CHM + volatile | OK |
| `RpcInvocationHandler`/`ReflectiveRpcService`/`MultiRpcService`/`RpcMessageSender`/`CancellableRpcClient`/`HttpRpcService`/`DefaultRpcUrlBuilder` | 全 final 字段，构造后只读 | — | final 发布 | OK |
| static 可变单例：`HttpRpcMessageTransformer.INSTANCE`、`DefaultApiResponseNormalizer.INSTANCE`（均 public static **非 final**） | 类初始化；理论上任意代码可重赋值 | 全平台 | 类初始化 HB | **缺陷（防御性）→ C1-006** |
| static final 单例：`LogRpcServiceInterceptor.INSTANCE`、`DefaultRpcMessageTransformer.INSTANCE`、`DefaultRpcMessageAdapter.INSTANCE`、`ContentType.CONTENT_TYPE_MAP`（static 块 + unmodifiableMap）、各 Logger | 类初始化 | 全平台 | final + 不可变容器 | OK |
| ThreadLocal | 本批次 11 根无直接 `ThreadLocal` 声明（基线 C1c 节全部为空，人工复现一致）；`ContextHttpServerFilter` 经 ContextProvider/`context.setContext(ctx)` 挂请求级 context，`ctx.close()` 在 whenComplete 兜底，MDC put/remove 成对（L47/58） | — | 平台 ThreadLocal 设施 | OK（本批次无 ThreadLocal 清理类发现） |

---

## 发现

### [C1-001] SimpleRpcClientFactory.init() 非幂等：每次 newInstance 重复前插拦截器（共享列表原地变更）+ timer/executor 检查-创建竞态

- **文件**: `nop-network/nop-rpc/nop-rpc-simple/src/main/java/io/nop/rpc/simple/SimpleRpcClientFactory.java:112-172`（核心 L114、L121-128、L156-171）
- **证据片段**:
  ```java
  // L112-128  init() 由 newInstance() 每次调用
  void init() {
      if (clientConfig.isLogBody()) {
          this.interceptors = CollectionHelper.prepend(interceptors, LogRpcServiceInterceptor.INSTANCE);
      }
      ...
      if (timer == null) {
          timer = DefaultScheduledExecutor.newSingleThreadTimer("simple-rpc-client-timer-" + clientName);
          ownTimer = true;
      }
      if (executor == null) { ... ownExecutor = true; }
  }
  // L156-171  newInstance() 每次都触发 init()，并把共享 interceptors 列表按引用交给新 handler
  public T newInstance() {
      init();
      ...
      new RpcInvocationHandler(serviceName, rpcService, interceptors, transformer));
  ```
  `CollectionHelper.prepend`（nop-kernel/nop-commons `CollectionHelper.java:1106-1112`）是 `list.add(0, item)` **原地变更并返回同一列表**，不是拷贝。
- **严重程度**: P1
- **并发场景**: 长驻进程内同一工厂多次/并发调用 `newInstance()`（每次建立客户端连接即触发）；`timer` 单线程定时器线程、连接 IO 线程与调用方线程共享 `interceptors` ArrayList 与 `timer/executor/ownTimer/ownExecutor/destroyed` 字段。
- **触发时序**: (a) 累积：`clientConfig.isLogBody()==true` 时，T1 第 k 次 `newInstance()` 执行到 L114 → `interceptors` 增至 k 个 `LogRpcServiceInterceptor` → 此后每次 RPC 调用经 `AopRpcServiceInvocation` 链执行 k 次日志拦截器（时延随连接次数线性增长，无界增长面）。(b) 竞态：T1、T2 同时进入 `newInstance()` → 两者在 L121 同读 `timer==null` → 各自创建一个单线程 ScheduledExecutor → 后写者覆盖 `timer`/`ownTimer`，先建的那个定时器线程永远无人 destroy（线程泄漏）；executor 同理。T3（destroy）与 T4（newInstance）交错：L137 `destroyed=true` 先于 L139 `executor.destroy()`，T4 此时通过 init 检查并在已销毁的 executor 上 `executeRecv` → 任务被拒绝或静默丢弃。
- **现状**: `init()` 把"一次性装配"逻辑放在每次连接都会执行的方法里，且对共享列表做原地结构性变更；字段全部非 volatile，检查-创建无原子守卫（F10 形态）。
- **风险**: 当前仓库内消费方为 `nop-dev-tools/nop-idea-plugin/.../XLangDebugConnector`（未开 logBody、单线程 IDE 使用），故实际暴露面小；但该工厂是公开运行期 API（WI 2.1 已在此站点登记 CL-024），任何服务端复用（per-session 建连 + logBody）都会命中累积与泄漏路径。
- **建议**: (1) `init()` 结果缓存：以 `volatile boolean inited` + synchronized（或 `initialized` AtomicBoolean compareAndSet）守卫，二次调用直接返回；(2) 拦截器装配移到构造器或首次 init 并 `Collections.unmodifiableList` 快照后再交给 handler，禁止共享可变列表；(3) timer/executor 创建用平台 `IAtomicLong`/`AtomicBoolean` 惰性初始化惯用法；(4) `destroyed` 加 volatile。
- **失效面枚举**（F3 同族清扫）: ① `SimpleRpcServer.java:125` —— 同一 `CollectionHelper.prepend` 模式，但仅在 `start()` 内执行一次，且 L144 有 `Guard.checkState(socketServer == null)` 守卫 → 见 C1-007（低危同族）；② `DefaultHttpClientFactory.init()`（nop-http-api support/DefaultHttpClientFactory.java:25-36）—— enhancer 链装配在 `@PostConstruct` 单发，幂等且列表只读遍历 → OK；③ `RpcServiceProxyFactoryBean.init()`（RpcServiceProxyFactoryBean.java:87-99，`@PostConstruct` 单发）→ OK；④ `AddAccessTokenHttpClientEnhancer` 无同类前插 → OK；⑤ `CollectionHelper.prepend` 其他调用点均不在本批次 11 根内（批次 2 范围移交）。
- **信心水平**: 确定
- **误报排除**: 不属于"无状态单例并发调用"（工厂持有可变字段且被运行期写）；不属于"低频启动路径粗粒度锁"（init 挂在每次请求触达的 newInstance 上）。
- **复核状态**: 未复核
- 复核状态: 已保留(P1)｜新发现——回码核验成立（prepend 原地变更 CollectionHelper.java:1106-1112；timer/executor 双建竞态；destroy 后可用）；同案：C6-014 并入本条(a)；站点邻接 WI 2.1 CL-024（池累计面，移交 nop-cluster）非同缺陷

### [C1-002] OAuth enhancer 在 synchronized(authTokens) 监视器内执行 token HTTP 往返：跨 provider 全局串行化

- **文件**: `nop-network/nop-http/nop-http-client-oauth/src/main/java/io/nop/http/client/oauth/enhancer/AddAccessTokenHttpClientEnhancer.java:110-132`
- **证据片段**:
  ```java
  protected String getAccessTokenFromProvider(IHttpClient client, ...) {
      Oauth2TokenResponseBean token = authTokens.get(providerName);
      if (token != null && !token.isExpired(gap))
          return token.getAccessToken();
      synchronized (authTokens) {              // L115
          token = authTokens.get(providerName);
          if (token != null && !token.isExpired(gap))
              return token.getAccessToken();
          Oauth2TokenResponseBean response = fetchToken(client, providerConfig);  // L120：锁内 HTTP 往返
          authTokens.put(providerName, response);
          ...
      }
  ```
- **严重程度**: P2
- **并发场景**: 所有业务线程的出站请求（`onFetchBegin` → `getAccessToken`）共享 enhancer 单例上的同一 `authTokens` 监视器；token 过期时刻的并发请求 + 单线程执行 `fetchToken`（阻塞 `client.fetch`，读超时默认 30s）。
- **触发时序**: token 到期瞬间，T1（业务线程 A，请求 provider-X）进入 synchronized 块执行 `fetchToken`（网络往返，最坏 30s）；T2..Tn（业务线程 B..N，请求 provider-Y，与 X 无关）在 L115 阻塞等锁 → 全部出站请求停等一个无关 provider 的 token 往返；token endpoint 网络劣化时停等放大至 readTimeout。
- **现状**: 双检模式本身正确（不会 thundering herd），但单例锁粒度为"所有 provider 共用一个监视器"，且锁内做阻塞 IO——可证明的争用热点 + 队头阻塞。
- **风险**: 负载上升 + token 恰好过期时，全应用经此 enhancer 的外呼出现秒级停等；token 服务故障时停等=客户端 readTimeout（默认 30s）量级，形成周期性停顿（每个 expireGap 一轮）。
- **建议**: 按 providerName 分离锁（`ConcurrentHashMap<String, Object> locks` + per-provider double-check），或改用平台 `io.nop.commons.concurrent.lock.IResourceLockManager`；进一步可用 `CompletableFuture<Oauth2TokenResponseBean>` 作为在途刷新单飞（single-flight），锁内只做 map put。
- **失效面枚举**: ① 同类"synchronized + 锁内 IO"扫描：本批次 11 根内 grep `synchronized` 共 5 处——`AbstractServerEventSubscription`（wait/cleanup，锁内无 IO，demand 等待可中断 → OK）、`ConcatenatedBodyPublisher`（demand 记账，锁内无 IO → OK）、`SimpleRpcClientFactory`（无锁，见 C1-001）、`SimpleRpcServer`（无锁）、本站点 → 仅本站点命中"锁内阻塞 IO"；② 无 token 缓存但 provider 集合来自有限配置，`authTokens` 无无界增长问题 → 非 F4。
- **信心水平**: 确定
- **误报排除**: 不是"原语使用本身"（synchronized 用法正确性无问题），立案点为复合语义：锁持有期跨越阻塞 IO 且锁对象为全 provider 单例——属于严重度校准中"可证明的争用热点"。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——锁内 HTTP 往返 + 全 provider 单监视器回码成立；同案：C3-008、C6-008（三登记一工单，C6-008 已裁并入）

### [C1-003] RpcChannelState.prepareSend 容量守卫非原子（check-size-then-put TOCTOU）

- **文件**: `nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/message/RpcChannelState.java:80-108`
- **证据片段**:
  ```java
  public CompletableFuture<R> prepareSend(Object id, S request, long timeout) {
      if (closed) { throw ...; }
      if (waitFutures.size() >= maxWaitingRequests) {   // L85：检查
          throw newError(ERR_RPC_TOO_MANY_INFLIGHT_MESSAGES)...;
      }
      whenSend(id, request, timeout);
      CompletableFuture<R> future = new CompletableFuture<>();
      CompletableFuture<R> oldFuture = waitFutures.put(id, future);   // L92：放入
  ```
- **严重程度**: P2
- **并发场景**: N 个业务调用线程并发 `prepareSend`（HTTP/RPC worker 线程池），`waitFutures` 为共享 CHM；接收线程并发 `onReceive` 做 remove。
- **触发时序**: `waitFutures.size()==maxWaitingRequests-1` 时，T1..Tk（k 个调用线程）先后执行到 L85，各自读到 size<max（彼此的 put 尚未发生）→ 全部通过检查 → k 个 future 全部 put → 在途量越过 `maxWaitingRequests`（默认 10000）上界，越过量=并发调用线程数。
- **现状**: `maxWaitingRequests` 有界但边界守卫非原子（F10）；`closed` 检查在 put 后有二次复查（L101-105，写法正确），唯容量守卫漏配原子性。
- **风险**: 背压阈值在突发并发下失真，超限在途请求不再被 fail-fast 拒绝；由于越界量受并发线程数约束（非无界），不升级 P1。
- **建议**: 以 `waitFutures.mappingCount()` 不可行时，用平台 `ISemaphore`/`HighWatermarkSemaphore`（io.nop.commons.concurrent.semaphore）做 tryAcquire/release 配对守卫；或将 CHM 换为按 size 原子判定的有界结构。
- **失效面枚举**: 本批次内"检查-然后-行动"复合操作全扫：① `AbstractServerEventSubscription.completed` 检查-置位（L63-66）——volatile 检查非原子，但 Apache/JDK 两条路径的全部完成信号均线程内串行（Apache：同一 reactor 线程顺序经过 processLine/onStreamComplete/回调链；JDK：executor 任务内单点），无线程交错序列可写 → 按口径不立案，登记为脆弱不变量（若未来引入异步完成源需补 AtomicBoolean）；② `PollingRpcClient.PollTask.schedule()` 的 `!future.isDone()` 检查（L96）——与 cancel 交错至多多余一轮轮询，无资源后果 → P3 不立案；③ `CancellableRpcClient.done` 检查（L50/L56）——`if (!done.get())` 后发取消请求，最坏对已完成任务多发一次 cancelMethod，服务端幂等语义可承受 → 不立案；④ `SimpleRpcServer.start()` 守卫 → C1-007；⑤ `RpcMessageSubscriptions.register` 用 `putIfAbsent` → 原子，OK；⑥ `RpcInvocationHandler.methods.computeIfAbsent` → 原子，OK。
- **信心水平**: 确定
- **误报排除**: 不是"CHM 本身的使用"（map 原语无问题），而是 size 检查与 put 两步复合缺少原子性——恰为校准条款允许报告的"复合操作误用"。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——size 检查×put 两步复合无原子性回码成立（RpcChannelState.java:80-92）；越界受并发线程数约束，P2 恰当

### [C1-004] RpcTask.checkFailCount 跨线程非原子读改写：定时轮询回调与外部更新丢失递增

- **文件**: `nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/monitor/RpcTask.java:19-27`、`nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/monitor/RpcTaskMonitor.java:113-134`
- **证据片段**:
  ```java
  // RpcTaskMonitor.handleTaskStatus（回调线程 / 任意调用 updateTaskStatus 的线程）
  void handleTaskStatus(RpcTask task, ApiResponse<TaskStatusBean> res, Throwable err) {
      if (res != null && res.isOk()) {
          task.setCheckFailCount(0);
      } else {
          task.setCheckFailCount(task.getCheckFailCount() + 1);   // L117：非原子 RMW
      }
      ProcessResult result = statusStorage.saveTaskStatus(task, res, err);
  // RpcTask.checkFailCount 为普通 int（RpcTask.java:19），无 volatile/atomic
  ```
- **严重程度**: P2
- **并发场景**: ① timer 线程（scheduleWithFixedDelay 每 10s 一轮 `checkTaskStatus`）派发的 `callAsync(...).whenComplete` 回调线程（RPC 响应完成线程，轮次间可滞留）；② 外部嵌入方调用公开 API `updateTaskStatus`（任意线程）；`activeTasks` 为 CHM，多任务并发，同一 task 的回调与外部更新并发。
- **触发时序**: 第 N 轮对任务 X 的状态 RPC 响应迟滞：T1 = 第 N 轮 whenComplete 在 t=10.5s 执行 `getCheckFailCount()` 读得 2；T2 = 第 N+1 轮 whenComplete 在 t=20s 读得 2、写 3；T1 随后写 3（本应写 3 的那一轮失败计数被覆盖为同值或丢失一次递增）→ `checkFailCount` 失真，`statusStorage.saveTaskStatus` 基于错误计数做 STOP/继续判定。
- **现状**: 失败计数是纯粹的共享可变 int，无任何同步；`scheduleWithFixedDelay` 只保证 checkTaskStatus 启动不重叠，不保证前轮异步回调已结束。
- **风险**: 监控语义失真——连续失败阈值判定滞后/跳变；不丢数据、不崩溃，故 P2。
- **建议**: `checkFailCount` 改 `AtomicInteger`（incrementAndGet/reset），或在 `RpcTask` 上以 synchronized 块封装计数变更；`handleTaskStatus` 与 `updateTaskStatus` 共用同一把 per-task 锁。
- **失效面枚举**: 同族"普通 int 计数器跨线程 RMW"扫描：① `PollingRpcClient.PollTask.errorCount`（L75/109/140）——写者全部在同一条轮询 whenComplete 串行链上，无并发写者 → OK；② `StatRpcServiceInterceptor` 计数走平台 `RpcClientStat`（nop-core stat，本领域不重审平台设施）→ OK；③ `RpcChannelState` 无计数器；④ 本批次无其他裸 int 跨线程计数（C7a 计数递增扫描的 C1 交集仅此一处）。
- **信心水平**: 确定（竞态存在确定；实际触发频率取决于嵌入方是否使用 updateTaskStatus——仓库内当前无调用点，公开 API 面向嵌入方）
- **误报排除**: 非静态分析式"看到共享字段就报"——已给出两线程具体交错序列（轮次回调滞留跨过 fixed-delay 边界是常态事件）。
- **复核状态**: 未复核
- 复核状态: 已驳回·同案并入C3-010(P2)｜新发现——缺陷本体回码成立，但与 C3-010 同站点同根（checkFailCount RMW + updateTaskStatus 面均含于 C3-010 失效面），按合并裁定防重复计数

### [C1-005] SimpleRpcClient 回调槽位双写者覆盖：工厂配置的 onChannelOpen/onChannelClose 被构造器静默丢弃，且晚设置字段对 IO 线程无可见性保障

- **文件**: `nop-network/nop-rpc/nop-rpc-simple/src/main/java/io/nop/rpc/simple/SimpleRpcClient.java:45-66`、`nop-network/nop-rpc/nop-rpc-simple/src/main/java/io/nop/rpc/simple/SimpleRpcClientFactory.java:174-182`
- **证据片段**:
  ```java
  // SimpleRpcClientFactory.newClient()：先把工厂回调写到 SocketClient 槽位
  client.setOnChannelOpen(onChannelOpen);     // L176
  client.setOnChannelClose(onChannelClose);
  // SimpleRpcClient 构造器（随后执行）：用自身 lambda 覆盖同一槽位
  client.setOnChannelOpen(() -> {
      channelState.onChannelOpen();
      if (onChannelOpen != null)      // 读 SimpleRpcClient 自己的字段——工厂从未向其传播
          onChannelOpen.run();
  });                                  // L55-59
  ```
- **严重程度**: P2
- **并发场景**: `SimpleRpcClientFactory.newInstance()` 调用线程（写者 1：工厂回调 → SocketClient 槽位）、`SimpleRpcClient` 构造（写者 2：覆盖槽位）、SocketClient IO/定时线程（读者，触发回调）、潜在的使用方 setter 调用线程（写者 3：`setOnChannelOpen` 于构造后）。
- **触发时序**: 时序固定（非竞态但为共享槽位的写序违约）：newClient 写工厂回调（T1-L176）→ SimpleRpcClient 构造器覆盖（T1-L55）→ 通道打开时 IO 线程只执行覆盖后的 lambda，其引用的 `onChannelOpen` 字段为 null → 工厂配置的回调永不执行。实仓消费方 `XLangDebugConnector`（nop-idea-plugin）将重连通知经 `clientFactory.setOnChannelOpen(onChannelOpen)`（XLangDebugConnector.java:52）注入，该通知确定性地丢失。并发角度：若使用方在构造后经 setter 补设回调，IO 线程 lambda 对该非 volatile 字段的读无 happens-before，可能永远读到 null。
- **现状**: 同一回调槽位存在两个写者且无写序契约（last-write-wins 取决于构造顺序这一隐式约定）；`SimpleRpcClient.onChannelOpen/onChannelClose` 字段非 volatile 且在构造器注册的回调中被跨线程读取。
- **风险**: 功能性：依赖工厂回调的重连感知逻辑静默失效（已实仓命中）；并发性：晚设置回调在 IO 线程上可见性无保障。
- **建议**: SimpleRpcClient 构造器不覆盖而是链式组合（先查工厂回调再执行自身逻辑），或工厂把回调经构造参数传播给 SimpleRpcClient；回调字段加 volatile 或在 connect() 前冻结为 final 快照。
- **失效面枚举**: ① `SocketClient.setOnChannelOpen/Close` 本体在 nop-socket（批次 2），本批次只登记 nop-rpc-simple 侧的覆盖写；② `SocketServer` 侧（`SimpleRpcServer.newServer` L159-160）只写一次、无覆盖 → OK；③ `SimpleRpcClientFactory.onChannelOpen` 字段本身亦非 volatile，setter（主线程）→ newClient 读（可能另一线程）无 HB，与上同一修复收敛；④ 其余批次 1 文件无"回调槽位双写者"形态。
- **信心水平**: 确定（覆盖丢失为确定性缺陷，已对照实仓消费方）；可见性部分为很可能
- **误报排除**: 不以"风格"立案——槽位覆盖导致实仓回调确定性丢失（功能违约），且叠加跨线程可见性缺口，属 C1 写者/读者契约问题。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——槽位双写覆盖序回码成立；实仓消费面命中（XLangDebugConnector.java:52 工厂回调确定性丢失）

### [C1-006] 非常量 public static 单例字段：HttpRpcMessageTransformer.INSTANCE 与 DefaultApiResponseNormalizer.INSTANCE 非 final

- **文件**: `nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/reflect/HttpRpcMessageTransformer.java:36`、`nop-network/nop-rpc/nop-rpc-api/src/main/java/io/nop/rpc/api/DefaultApiResponseNormalizer.java:15`
- **证据片段**:
  ```java
  public class HttpRpcMessageTransformer extends DefaultRpcMessageTransformer {
      public static HttpRpcMessageTransformer INSTANCE = new HttpRpcMessageTransformer();   // 非 final
  ...
  public static DefaultApiResponseNormalizer INSTANCE = new DefaultApiResponseNormalizer();  // 非 final
  ```
- **严重程度**: P3
- **并发场景**: 全平台任意线程读取；类初始化建立发布 HB，正常路径安全。风险仅在"任何代码（含用户 Delta 定制代码）运行期重赋值 INSTANCE"时，部分线程读到旧实例、部分读到新实例（无 volatile），出现同进程内 transformer/normalizer 行为分裂。
- **触发时序**: 理论风险，未具体化（仓库内无重赋值点）——按口径纪律第 4 条维持 P3 登记。
- **现状**: 与同包 `LogRpcServiceInterceptor.INSTANCE`、`DefaultRpcMessageTransformer.INSTANCE`、`DefaultRpcMessageAdapter.INSTANCE`（均 static final）风格不一致。
- **风险**: 防御性缺口；被运行期替换时引入不可复现的行为分叉。
- **建议**: 补 final（保留可替换性则改为 getInstance() + volatile 字段）。
- **失效面枚举**: 本批次 11 根 `public static` 非 final 字段全扫（人工复现基线 C1a 噪声后）仅此 2 处；其余单例均 final。
- **信心水平**: 确定（字段形态确定；危害为理论）
- **误报排除**: 非误报校准条款涵盖项；按"static 可变字段"载体清单义务登记，降 P3。
- **复核状态**: 未复核
- 复核状态: 已保留(P3)｜新发现（防御性）——两处非 final public static INSTANCE 与同包 final 单例不一致回码成立；危害理论

### [C1-007] SimpleRpcServer.start() 非原子守卫 + init() 同族 prepend（C1-001 同族，单发语境低危）

- **文件**: `nop-network/nop-rpc/nop-rpc-simple/src/main/java/io/nop/rpc/simple/SimpleRpcServer.java:123-152`
- **证据片段**:
  ```java
  public void start() {
      Guard.checkState(socketServer == null, "server already inited");   // L144：检查-赋值非原子
      init();                                                            // L125：prepend 同族
      this.socketServer = newServer();
      this.handler = buildHandler();
      this.socketServer.start();
  }
  ```
- **严重程度**: P3
- **并发场景**: 管理面线程并发调用 start()（服务器启动操作，低频）。
- **触发时序**: T1、T2 同时执行到 L144，均读到 `socketServer==null` → 各建一个 SocketServer 并 start → T2 的赋值覆盖 `this.socketServer`，T1 创建的 server 无人持有、无人 stop（监听端口泄漏，且 broadcast/sendTo 走 T2 的实例）。理论风险，未具体化（生命周期管理操作通常单线程）。
- **现状**: F10 形态守卫缺失；`init()` 的 `CollectionHelper.prepend`（L125）在双 start 下执行两次（叠加 C1-001 同族问题），单发语境下无累积。
- **风险**: 双 start 端口泄漏 + 回调指向错位；概率低。
- **建议**: start() 加 synchronized 或 AtomicBoolean compareAndSet 守卫；与 C1-001 一并收敛 prepend 模式。
- **失效面枚举**: 与 C1-001 共用同族清单（见 C1-001 失效面枚举 ①-⑤）；本条为该家族在服务端站点的登记。
- **信心水平**: 确定（形态）；触发为理论
- **误报排除**: 启动管理路径粗粒度同步本可接受（校准第 3 条），立案理由是其后果为资源泄漏（端口）而非仅优雅性，且与 C1-001 构成同族修复单元；已按理论风险降 P3。
- **复核状态**: 未复核
- 复核状态: 已保留(P3)｜新发现——Guard.checkState 非原子回码成立；生命周期管理面理论竞态，P3 恰当

### [C1-008] MqttSessionManager.removeConnection 会话接管窗口无条件清空新会话订阅表：下行路由静默失效

- **文件**: `nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/impl/MqttSessionManager.java:42-45`（关键 L44）、`:33-40`；触发链 `.../impl/VertxMqttServer.java:116-125`（onCloseCallback 注册 + addConnection）、`.../impl/MqttConnection.java:211-224`（complete → onCloseCallback）
- **证据片段**:
  ```java
  // MqttSessionManager.removeConnection —— 两步复合，第二步无条件
  public void removeConnection(IMqttConnection conn) {
      sessions.remove(conn.getClientId(), conn);   // L43：条件删（仅当当前值==conn）
      subscriptions.remove(conn.getClientId());    // L44：无条件清空订阅
  }
  // addConnection（接管语义）：put 新连接 → 清订阅 → old.close()（异步触发上面 L43/L44）
  ```
- **严重程度**: P1
- **并发场景**: 同一 clientId 的旧连接 X（event loop-A）与新连接 B（event loop-B，MQTT 掉线重连的常态路径）；X 的 TCP FIN 处理与 B 的 SUBSCRIBE 处理分属两个 event loop，仅经 CHM 协调，无会话级互斥。
- **触发时序**: T1（B 的 loop）：`acceptEndpoint` → `addConnection(B)`（L34 put(B)；L36 清除 X 的订阅——接管语义正确面；L37 `old.close()` 异步）。B 随后发 SUBSCRIBE → `subscribeHandler` → `sessionManager.subscribe(clientId, filters)`（订阅表恢复为 B 的过滤器）。T2（X 的 loop）：FIN 到达 → `closeHandler` → `complete()`（closed 守卫对 X 自身放行）→ `onCloseCallback` → `removeConnection(X)`：L43 `sessions.remove(clientId, X)` 因当前值是 B 而 no-op，**但 L44 无条件 `subscriptions.remove(clientId)` 把 B 刚登记的订阅抹掉**。此后 `findConnections(topic)` 不再命中 B → 服务端向该设备的下行消息静默丢失（客户端自认仍订阅，不会重发 SUBSCRIBE；QoS0 下无任何错误可见，直至设备重连/重订阅）。
- **现状**: L43 已按"条件删"写法防御接管竞态，L44 却是跨会话的无条件清除——两步复合操作未与 `sessions` 的当前值绑定，检查-行动缺口（F10）。
- **风险**: 设备重连风暴（网络抖动、服务重启）期间反复触发；下行指令（IoT 控制面）静默丢失且无日志可见。
- **建议**: `removeConnection` 改为原子条件清：`sessions.compute(clientId, (k, cur) -> cur == conn ? null : cur)` 仅当移除生效时同步清 `subscriptions`（或 `subscriptions.compute(clientId, (k, v) -> sessions.get(k) == null ? null : v)`）。
- **失效面枚举**: ① `subscriptions.remove` 全文件仅 2 处——L36（addConnection 内，put 后同步执行，先于 close 回调，语义正确）与本条 L44；② `findConnections` 遍历 CHM 弱一致 → 线程安全 OK；③ `MqttServerMessageService.subscribers` 的移除竞态归 C1-012（不同载体）；④ `MqttConnection.closed` 检查-置位单 endpoint 单 event loop 串行 → OK。
- **信心水平**: 确定
- **误报排除**: 非"CHM 原语本身使用"问题——CHM 用法正确，立案点是 remove 两步复合的原子性与跨会话语义（恰为校准条款允许的复合操作误用）；已给出两 event loop 具体交错。
- **复核状态**: 未复核
- 复核状态: 已保留(P1)｜新发现——L43 条件删 + L44 无条件清复合缺口回码成立；同案：C3-018、C6-020（三登记一工单）；同族前案 check/nosql-cdc.md 2026-08-19（Lettuce PubSub，非同站点）

### [C1-009] FeishuClient 重连调度无单飞/世代守卫：onClose/onError 双入口双调度 + 重启窗口陈旧回调 → 双活 WebSocket

- **文件**: `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/client/FeishuClient.java:283-310`（scheduleReconnect/attemptReconnect）、`:370-385`（onClose 与 onError 双入口）、`:200-224`（stop）、`.../client/JdkStreamTransport.java:37-95`（connect 无重复连接守卫）
- **证据片段**:
  ```java
  // 两个回调入口都直达同一调度，无去重
  public void onClose()  { connected = false; scheduleReconnect(); }
  public void onError(Throwable error) { connected = false; ...; scheduleReconnect(); }
  private void scheduleReconnect() {
      if (!started) { return; }                    // 唯一守卫：started 跨代复用
      long attempt = reconnectAttempt.incrementAndGet();
      scheduler.schedule(this::attemptReconnect, delay, MILLISECONDS);
  }
  private void attemptReconnect() {
      if (!started) { return; }
      ... transport.connect(URI.create(ep.getUri()), streamListener);  // 不 close 旧连接
  ```
- **严重程度**: P1
- **并发场景**: WS 传输回调线程（onClose/onError）与单线程 scheduler（attemptReconnect）；stop/start 生命周期线程。JDK WebSocket 读失败典型序列为 onError 后跟 onClose(1006)——两个入口相继触发是常态事件而非边角。
- **触发时序**: (a) 双调度：T1 WS 线程 `onError` → scheduleReconnect（attempt=1，delay 1s）；T2 WS 线程随后 `onClose` → scheduleReconnect（attempt=2，delay 2s）。两个任务先后执行 `attemptReconnect` → 各调 `httpApi.getStreamEndpoint` + `transport.connect` → **两条并存 WebSocket**：各自 onOpen 发 connect 握手 → 服务端视作双活会话，同一事件经两条连接重复投递（FeishuClient 无 message_id 去重状态，IMessageHandler 直接收重）；旧 socket 无任何 close 调用（泄漏一条 WS 连接及 JDK HttpClient 线程）。(b) 重启窗口：`stop()`（L211 `transport.close()`→sendClose.join，但不保证 listener 回调已投递完）→ `start()`（L194 建新连接、L195 `started=true`）→ 旧连接的 onClose 回调此后才由 JDK executor 投递 → `scheduleReconnect` 读 `started==true` 通过 → 1s 后 attemptReconnect 在新连接之外**再建第三条连接**。
- **现状**: `started` 一个布尔同时承担"客户端启停"与"重连代次"两个语义；重连无 AtomicBoolean 单飞、无世代号；`attemptReconnect` 不关闭前代连接。
- **风险**: 双活连接 → 消息重复投递 + ACK 双发（协议违约可致服务端断连循环）+ fd/线程泄漏；长时运行断连反复时累积。
- **建议**: `AtomicBoolean reconnectPending.compareAndSet(false,true)` 单飞（attemptReconnect 收尾清零）；attemptReconnect 先 `transport.close()` 旧连接；引入连接世代号（AtomicLong generation，回调闭包携带世代，过期回调直接丢弃）——与 C1-013 的 stop 世代修复同族。
- **失效面枚举**: ① onClose/onError 双入口（本条）；② `start()` 幂等由 synchronized(start)+started → OK；③ `heartbeatFuture` 仅在 synchronized 的 start/stop 内写 → OK；④ `JdkStreamTransport.connect` 无二次连接守卫（本条同治）；⑤ 批次 1 `PollingRpcClient`/`SimpleRpcClient` 重连链已核单线程串行 → OK；⑥ `NettyTcpClient.scheduleReconnect`（C1-013——同族"守卫谓词不足"不同形态）；⑦ `SocketClient.recv` 重连链（retryExecute→reconnect 串行于单 executor）→ OK。
- **信心水平**: 确定（双入口调度为代码事实；onError→onClose 相继发生为很可能）
- **误报排除**: 非 C3 异步异常传播问题——立案点是共享守卫状态的检查-行动缺陷（F10 家族）；不因"罕见"降 P2：断连重连是 Stream 长连接的主路径事件。
- **复核状态**: 未复核
- 复核状态: 已保留(P1)｜新发现——onClose/onError 双入口无单飞/世代守卫回码成立；同案：C3-017（同站点 P2，判级分歧留 7.1——本条 P1 校准：断连重连为 Stream 主路径 + 双活协议违约）

### [C1-010] FeishuClient.stop() 清空 credentials/handler/endpoint 与 WS 回调线程无 happens-before：停机窗口僵尸投递与 NPE 链

- **文件**: `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/client/FeishuClient.java:75-86`（字段声明）、`:200-224`（stop 置空）、`:238-256`（sendMessage/ensureToken）、`:312-385`（回调读取）
- **证据片段**:
  ```java
  private volatile boolean started = false;
  private volatile boolean connected = false;
  private FeishuCredentials credentials;      // 非 volatile
  private IMessageHandler handler;            // 非 volatile
  private StreamEndpoint endpoint;            // 非 volatile
  // stop()（持 this 锁）置三者为 null；WS 回调线程（不持锁）直接读
  public void onBinary(byte[] data) { ... handler.onMessage(msg); ... }
  ```
- **严重程度**: P2
- **并发场景**: stop/start 生命周期线程（持 this）与 JDK WS 回调线程（onOpen/onBinary/onClose/onError，不持锁）+ 任意调用 sendMessage 的业务线程。字段写读之间除 `started/connected`（volatile）外无任何 HB 边。
- **触发时序**: T1 WS 线程 `onBinary` 解码成功 → L339 读 `handler`（旧值非 null）→ T2 `stop()` 置 `handler=null/credentials=null` 并关闭 transport → T1 继续调用旧 `handler.onMessage(msg)`：**stop 返回后的僵尸投递**（下游 connector 已认为 client 停止，状态不一致）。变体 1：T1 读 `handler` 恰为 null → NPE 落入 L366 `catch(Throwable)` → L367 `handler.onError(t)` 再次 NPE → 异常穿透 `JdkStreamTransport.onBinary`（无捕获）进入 JDK WS onError 链（onError 入口有判空 → 转入 scheduleReconnect → started=false 拦截 → 仅日志噪声）。变体 2：业务线程 L239 `started` 检查通过后 T2 stop → L252 `ensureToken` 读 `credentials==null` → 裸 NPE（非 NopFeishuException）。
- **现状**: 生命周期字段对传输线程的"停机空窗"无契约：stop 以置 null 表达失效，但读侧无 volatile 快照、无判空本地化惯例。
- **风险**: 停机/重启窗口的 NPE 噪声、僵尸投递、静默握手失败（onOpen 读 credentials==null → NPE 被 catch 吞为 WARN → 无握手连接被服务端断开 → 触发多余重连）。
- **建议**: 引入不可变会话快照：`volatile SessionContext ctx`（聚合 credentials+handler+endpoint），start 构造、stop 置 null；回调入口 `var c = ctx; if (c == null) return;` 单点判空后全程使用局部引用；sendMessage/ensureToken 同模式。
- **失效面枚举**: ① onOpen（credentials/endpoint 读，catch 吞 NPE）② onBinary（handler 读）③ onError（handler 读，已判空 → OK）④ ensureToken/sendMessage（credentials 读）⑤ attemptReconnect（credentials 读——started 守卫先行，但 started 检查与 credentials 读之间同窗口存在，属本条同治）；同族对照：批次 1 C1-005（回调槽位双写者）——本条为"置空 vs 回调读"形态。
- **信心水平**: 确定（窗口存在）；僵尸投递/NPE 链为很可能
- **误报排除**: `started/connected` 已 volatile 不立案；立案点在其余三个生命周期字段——非"看到共享字段就报"，已给出 stop 与每类回调的具体交错及穿透路径。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——stop 置 null 三字段与 WS 回调线程无 HB 回码成立；僵尸投递/NPE 链推演成立

### [C1-011] FeishuClient stop→start 重启路径：owned scheduler 已 shutdown 后 scheduleAtFixedRate 拒绝 → started=true 的半启动死态

- **文件**: `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/client/FeishuClient.java:215-217`（shutdownNow）、`:258-265`（scheduleHeartbeat 无守卫）、`:290-294`（reconnect 调度仅 catch-WARN）
- **证据片段**:
  ```java
  // stop(): if (ownsScheduler) { scheduler.shutdownNow(); }
  // start(): ... transport.connect(...); this.started = true; scheduleHeartbeat();
  private void scheduleHeartbeat() {
      if (heartbeatIntervalSeconds <= 0) { return; }
      heartbeatFuture = scheduler.scheduleAtFixedRate(this::sendHeartbeat, ...);  // 未捕获 RejectedExecutionException
  ```
- **严重程度**: P2
- **并发场景**: stop→start 生命周期序列（同线程或不同线程均可，属时序确定性缺陷）；单线程 scheduler 的终态（shutdownNow 后永久拒绝任务）。
- **触发时序**: T1 `stop()`（ownsScheduler=true → scheduler.shutdownNow()）→ T1' `start()`（换凭证重启）：L194 connect 成功 → L195 `started=true` → L196 `scheduleHeartbeat` → L262 `scheduleAtFixedRate` 抛 RejectedExecutionException → `start()` 以未检查异常失败，**但 transport 已连接、started=true** → 无心跳 → 服务端保活超时断连 → onClose → scheduleReconnect → L291 `scheduler.schedule` 同样被拒（L290-294 仅 WARN）→ 客户端停在 `started=true/connected=false` 的死态，接口层 sendMessage 继续放行（started 检查通过）而长连接永不恢复。
- **现状**: stop 释放 scheduler 的"一次性"语义未传递给 start：重启路径在 ownsScheduler 形态下确定性产生"半个成功"（连接已建立、心跳与重连引擎已死）。
- **风险**: 运维侧"停改凭证再启"主路径失效且失败形态为静默（仅 WARN 日志）；上层 FeishuConnector 依赖 client.started 判定健康 → 误判存活。
- **建议**: start() 检测 `scheduler.isShutdown()` 时按 ownsScheduler 重建 executor（ownsScheduler 置 true）；或将 shutdownNow 下沉到 `dispose/destroy` 级生命周期，stop 仅取消任务不杀线程池（对照平台 LifeCycleSupport 的 stop/restart 两级语义）。
- **失效面枚举**: 全批次 stop→start 路径清扫：① `VertxMqttServer.doStop/doStart`——mqttServer 字段重建、无共享 executor 释放 → OK；② `NettyTcpClient.doStop`——shutdownGracefully 但 doStart 的 createBootstrap 每次重建 group（workerGroup==null 时）→ OK（restart 场景自愈）；③ `SocketServer.doStop/doStart`——executor 判空重建 → OK；④ `SocketClient.close/reconnect`——不杀 timer（外部注入）→ OK；⑤ `SftpClientFactory` 无生命周期 → 不适用。仅本站点命中。
- **信心水平**: 确定
- **误报排除**: 非"低频管理路径优雅性"校准项——后果是主链路静默失效而非日志/优雅性问题；RejectedExecutionException 未捕获属 F9 面。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——ownsScheduler shutdownNow 后重启 REE 半启动死态回码成立；实仓消费方 FeishuConnector（nop-ai-gateway）stop/start 真实可达

### [C1-012] MqttServerMessageService.subscribers 值容器并发变更 + dispatcher 首末订阅挂载检查-行动竞态：入站消息静默丢失

- **文件**: `nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/bus/MqttServerMessageService.java:91-134`（subscribe/cancel）、`:139-153`（dispatch）
- **证据片段**:
  ```java
  public IMessageSubscription subscribe(String topic, IMessageConsumer listener, ...) {
      if (mqttServer != null && subscribers.isEmpty()) {   // L93：空→非空边沿检查
          mqttServer.addPublishListener(dispatcher);
      }
      subscribers.computeIfAbsent(topic, k -> new ArrayList<>()).add(listener);  // L97：ArrayList 共享 add
  }
  private void dispatch(MqttPublishMessage msg) {
      for (... : subscribers.entrySet()) {
          ...
          for (IMessageConsumer consumer : new ArrayList<>(entry.getValue())) {  // L145：拷贝构造与并发 add/remove 竞态
  ```
- **严重程度**: P2
- **并发场景**: 应用线程（subscribe/cancel，IMessageService 公开 API 的正常动态订阅用法）与 MQTT event loop（入站 publish 分发）；CHM 仅保护 map 结构。
- **触发时序**: (a) T1 subscribe 与 T2 dispatch 并发：L145 `new ArrayList<>(entry.getValue())` 拷贝构造读取 elementData/size 与 T1 的 `list.add` 交错 → AIOOBE 或 null 元素 → 该 topic 本条消息分发中断（异常被 wrapHandler 的 per-listener try 记日志，消息丢失）。(b) T1、T2 并发对同一 topic subscribe → 并发 `ArrayList.add` size++ 竞态 → 丢失一个监听者注册（静默）。(c) 挂载竞态：T1 cancel 最后一个订阅——L107 `computeIfPresent` 移除后、L112 `removePublishListener` 执行前；T2 subscribe——L93 读 `subscribers.isEmpty()==true` → L95 `addPublishListener`；T1 继续 L112 摘除 dispatcher → **subscribers 非空但 dispatcher 已摘除**，后续入站 publish 永不分发（静默，直至订阅全清再重挂）。
- **现状**: 三处复合操作（list 值变更、拷贝遍历、空/非空边沿挂载）均无原子性；对照同文件依赖的 `VertxMqttServer.publishListeners` 已正确使用 CopyOnWriteArrayList。
- **风险**: 动态订阅/退订与设备消息并发时消息静默丢失；无错误可观察。
- **建议**: 值容器改 `CopyOnWriteArrayList`（`computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())`，add/remove 直接作用于 COW 列表，dispatch 可直接遍历免拷贝）；挂载/摘除改为 `subscribers.compute()` 内统一判定边沿（或维护 AtomicInteger 挂载引用计数，0→1 挂载、1→0 摘除）。
- **失效面枚举**: ① `VertxMqttServer.publishListeners`（COW，对照正确实现）；② `dispatcher` final 单例 → OK；③ `IMessageSubscription.cancelled` volatile 检查-置位——双 cancel 幂等（多执行一次空 remove）→ OK；④ `MqttSessionManager.subscriptions` 值为 `CHM.newKeySet()` 并发 add/remove 安全（对照正确实现）；⑤ `MqttTopicMatcher` 纯函数 → OK。
- **信心水平**: 确定
- **误报排除**: 非"CHM 本身使用"——map 原语无问题，立案点为值容器复合操作误用与边沿检查非原子（F10）。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现（子项勘误）——(a) 拷贝构造竞态、(b) 并发 add 丢失更新回码成立；子项(c) 不可达：其前提与 C6-026② 事实矛盾（computeIfPresent 恒返回列表→条目永存→isEmpty() 恒 false→L112 为死码），勘误不改判级

### [C1-013] NettyTcpClient 停机后重连：isStopping() 谓词在 stop 完成后为假 → 已停客户端静默重连、外部 workerGroup 下幽灵连接泄漏

- **文件**: `nop-network/nop-netty/src/main/java/io/nop/netty/tcp/NettyTcpClient.java:230-256`（scheduleReconnect）、`:205-228`（doConnect）、`:262-276`（doStop）；守卫谓词来源 `nop-kernel/nop-commons/src/main/java/io/nop/commons/service/LifeCycleSupport.java:99-117`
- **证据片段**:
  ```java
  protected void scheduleReconnect(EventLoopGroup eventLoop, Throwable e) {
      if (isStopping())          // L231：唯一生命周期守卫
          return;
      if (config.isAutoReconnect()) {   // 默认 true
          ...
          eventLoop.schedule(this::doConnect, delay, TimeUnit.MILLISECONDS);
  // LifeCycleSupport.stop(): finally { endStatus = isAllowRestart() ? CREATED : STOPPED; ... }
  // isStopping(): status >= STOPPING.ordinal()  → stop 完成回到 CREATED 后为 false
  ```
- **严重程度**: P2
- **并发场景**: event loop 线程（channelInactive/connect-fail listener 调度延迟重连任务）与生命周期管理线程（stop）；重连延迟（connectRetryDelay 起、至多 maxConnectRetryDelay=3s）使"已调度任务"天然跨过 stop 时点。
- **触发时序**: 连接断开（典型停因：服务端下线）→ channelInactive → scheduleReconnect → `eventLoop.schedule(doConnect, delay)`。管理线程 `stop()`：beginStop 置 STOPPING → doStop（`shutdownGracefully` **异步**，默认 quiescence 2s）→ finally status 回 **CREATED**（allowRestart 默认 true，NettyTcpClient 从未 setAllowRestart(false)）→ stop 返回后 `isStopping()==false`。已调度的 doConnect 触发：L206 守卫放行 → `bootstrap.connect`。**外部注入 workerGroup**（setWorkerGroup 共享连接池形态）时 doStop 不关闭组 → connect 成功 → "已 stop"客户端持有活连接；该连接再次断开又继续重连——无任何生命周期约束的幽灵连接 + 持续外呼。内部组场景：connect 在 quiescence 窗口内可能成功后随组终止被关，或组终止后被拒（RejectedExecutionException 在 netty listener 内被吞为日志，F9 面）。
- **现状**: 守卫谓词选型缺陷——`isStopping()` 仅在 STOPPING 瞬时窗口为真，对"停机后到达的延迟任务"（稳态 STOPPED/CREATED）恒为假，不构成守卫（F10 变体：守卫存在但覆盖错误的状态集）。
- **风险**: 幽灵连接泄漏 + 停机后资源活动；共享 workerGroup 部署下影响同组其他组件的容量核算。
- **建议**: 以独立 `volatile boolean reconnectStopped`（doStop 置位、doStart 复位）替代 isStopping() 作为重连守卫；或重连任务捕获 start 世代号（`AtomicLong startGeneration`，doStart 递增，任务执行时比对不一致即放弃）——与 C1-009 的世代修复同族收敛。
- **失效面枚举**: ① `NettyTcpServer` 无重连循环 → 不适用；② `SocketServer.run` 以 volatile stopped 判定、stop 后不重启 → OK；③ `SocketClient.recv` 重连链以 closed 判定且 close 后不复位 → OK；④ `FeishuClient`（C1-009——同族世代缺陷另一形态）；⑤ 平台 `LifeCycleSupport` 本体属 nop-commons，不重审（本条立案点是使用侧谓词选择）。
- **信心水平**: 确定（谓词语义与生命周期转换均已对照实码）
- **误报排除**: 非平台设施重审，也非"低频启动路径粗粒度同步"校准项——立案点是运行期延迟任务与生命周期的检查-行动缺口；外部组场景后果为资源泄漏（达 P1 下限），因触发需"停机时恰有断连"的时序组合，维持 P2。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——isStopping() 于 stop 完成回 CREATED 后恒假回码成立（allowRestart 默认 true、全仓无 setAllowRestart(false)）；同案：C6-022（同根不同交错，同批修复）

### [C1-014] NettySslEngineFactory 对 SslConfig 缓存字段的检查-置位无同步：并发首连重复构建 keystore + 非 volatile 不安全发布

- **文件**: `nop-network/nop-netty/src/main/java/io/nop/netty/ssl/NettySslEngineFactory.java:27-43`、`nop-network/nop-netty/src/main/java/io/nop/netty/config/SslConfig.java:66-78`（非 volatile 缓存字段）、消费点 `.../utils/NettyChannelHelper.java:29-31`（每连接调用）
- **证据片段**:
  ```java
  public SslContext getSslContext(SslConfig sslConfig, boolean clientMode) {
      if (clientMode) {
          if (sslConfig.getClientSslContext() != null)   // 检查
              return sslConfig.getClientSslContext();
          SslContext sslContext = buildSslContext(sslConfig, clientMode);  // keystore 文件 IO + Factories 构建
          sslConfig.setClientSslContext(sslContext);     // 置位（非 volatile 字段）
          return sslContext;
  ```
- **严重程度**: P2
- **并发场景**: 首批并发建连：多个 channel 的 event loop 线程各自经 `addCommonChannelHandler` → `getSslContext`；client/server 双路径对称。
- **触发时序**: T1、T2（不同 event loop）同读 `getClientSslContext()==null` → 各自 `buildSslContext`（各做一次 keystore 文件 IO + KeyManagerFactory/TrustManagerFactory 构建）→ 各自 set → 后写胜出（双构建值等价，正确性无损）。读侧：其他线程经普通字段读到 T1 写入的引用时无 HB——SslContext 内部大量 final 字段受 final freeze 兜底，但非 final 惰性缓存字段存在不可见窗口（理论面）。
- **现状**: 平台前缀明示的"检查-然后-放入"缓存竞态形态；无同步、无 volatile；结果幂等因此不产生数据损坏。
- **风险**: 首批 SSL 并发连接的重复 keystore IO/构建开销（证书库大时可感知）；极端情况下半初始化非 final 状态的不可见窗口。
- **建议**: SslConfig 两个缓存字段加 volatile 并以 synchronized 惰性块封装双路径，或改单飞字段（`volatile CompletableFuture<SslContext>`）。
- **失效面枚举**: ① client/server 双路径对称同型（一处修复两处同改）；② 全批次 grep 无其他 SslContext 缓存点；③ 同族对照批次 1：`AddAccessTokenHttpClientEnhancer.authTokens`（C1-002，锁内 IO——本条是其无锁反面形态）。
- **信心水平**: 确定（竞态形态确定）；后果大概率仅性能级
- **误报排除**: 不按"幂等值竞态即误报"处理——平台共享前缀将此类缓存路径列为高频定向检查项，重复 keystore IO 有具体触发时序与可量化浪费；如复核裁定降级，P3 下限保留。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——检查-置位无同步回码成立；C6 文件 CL-027 与本条同站点（线索），本条主登记闭环

### [C1-015] FeishuBindProvider 绑定 ticket 单消费 TOCTOU：get → 双次 HTTP 往返 → remove 非原子，同一 ticket 可并发消费两次

- **文件**: `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/bind/FeishuBindProvider.java:172-196`（关键 L172/L186/L189）
- **证据片段**:
  ```java
  TicketEntry entry = tickets.get(ticketId);        // L172：检查
  if (entry == null) { throw ... unknown or expired ... }
  if (entry.isExpired()) { tickets.remove(ticketId); throw ...; }
  // 注释明示：consume BEFORE resolution 便于失败重试
  String openId = resolveOpenIdServerSide(code);    // L186：两次 HTTP 往返的长窗口
  tickets.remove(ticketId);                         // L189：消费（无原子性绑定检查）
  ```
- **严重程度**: P2
- **并发场景**: 同一 ticketId 的回调并发提交（用户双击授权回调、平台回调重试、重放——ticketId 经 QR 的 `state=` 公开可见）；HTTP worker 线程并发进入 `onChannelScanCallback`。
- **触发时序**: T1、T2 同时执行 L172 `get` 均得非 null 且未过期 → 各自 `resolveOpenIdServerSide`（可携带不同 code，同一账号两次授权）→ 各自 L189 `remove`（CHM 幂等）→ **两次都返回 BINDING_COMPLETED**——"single-use binding flow"契约被并发击穿（两次成功绑定结果；若下游按回调次数写审计/发通知则重复，绑定本体因同 platformUserId 幂等而危害有限）。
- **现状**: 单次性由"get 后置 remove"表达，检查与消费之间隔两次网络往返（秒级窗口），无原子摘除或 in-flight 标记。
- **风险**: 5 分钟 TTL 窗口内可稳定重放；单次性违约 + 下游重复副作用。
- **建议**: 原子摘除 + 失败补偿：`TicketEntry entry = tickets.remove(ticketId); if (entry == null || entry.isExpired()) throw;` 先消费再 resolve，resolve 失败路径重新 `tickets.put(ticketId, entry)`（仅当仍无同 id 条目时）；或 `tickets.putIfAbsent(ticketId+"_inflight", marker)` 单飞标记。
- **失效面枚举**: ① `purgeExpired` 的 CHM removeIf（逐项原子、弱一致）→ OK；② `requireHttpApi` 惰性初始化双构建——无状态实例、值等价 → 登记不立案（对照 C1-014 家族，此处无 IO 重复后果）；③ `CredentialResolutionSupport` 无状态 → OK；④ `createBindTicket` 的 UUID 随机票号（P0 hardening 注释）→ 已防猜解，OK。
- **信心水平**: 确定
- **误报排除**: 立案点是并发单次性违约（复合操作原子性），非安全审计口径的身份伪造——服务端换码（G12-13-03）语义不受影响；已给出具体并发时序。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——TOCTOU 回码成立；勘误：证据对源码注释转述有误（实际为 resolve 先于 consume 以便失败重试），代码顺序描述正确，本体不受影响；同案：C3-031

### [C1-016] SocketClient 单读线程契约无守卫无声明：共享 headerBuf 与 InputStream 可被 call()/recv() 双入口并发消费 → 帧流交织损坏

- **文件**: `nop-network/nop-socket/src/main/java/io/nop/socket/SocketClient.java:44-47`（共享字段）、`:164-197`（recv）、`:235-238`（call）、`:279-294`（cleanup）；契约仅存于消费方 `nop-network/nop-rpc/nop-rpc-simple/src/main/java/io/nop/rpc/simple/SimpleRpcClient.java:104`（"这里假定了单线程调用"注释）
- **证据片段**:
  ```java
  private ByteBuffer headerBuf = ByteBuffer.allocate(8);   // 实例级共享，无同步
  public BinaryCommand recv() {
      InputStream is = getInputStream();                   // synchronized 仅保护引用获取
      BinaryCommand cmd = BinaryCommand.readPacketFromStream(is, ..., headerBuf);  // buf.rewind/limit/position 状态变更
  ...
  public BinaryCommand call(BinaryCommand request) {       // 公开 API 第二读入口
      send(request, true);
      return recv();                                       // 与 recv 循环并发即双读
  ```
- **严重程度**: P2
- **并发场景**: T1 = `recv(executor, handler)` 派生的常驻读循环线程（SimpleRpcClient 唯一在仓消费路径）；T2 = 直接调用公开 API `call()`/`recv()`/`ping()` 的业务线程。两入口无任何互斥或守卫。
- **触发时序**: T1 在 L171 `readPacketFromStream(is,...,headerBuf)` 读流途中；T2 经 `call()` 进入 `recv()`：同一 socket InputStream 的读指针被两线程竞争（半帧被双方各读一部分），`headerBuf` 的 rewind/limit/position 被交错变更 → 双方均解析出错帧/串帧 → 响应错配（T2 收到 T1 请求的响应）与协议流损坏（后续字节流整体错位，难排查的随机解析失败）。写侧无此问题（send 以 synchronized(os) 帧同步）。
- **现状**: 单读约定仅存在于消费方注释，SocketClient 自身无 javadoc 声明、无运行时 fail-fast；`call()` 公开签名诱导同步用法。close/reconnect 与在途读并发为 fail-fast（is 置 null 有锁、已取局部引用读到 -1/异常退出循环）→ 无泄漏，不入案。
- **风险**: 嵌入方混用 call 与 recv 循环 → 流式协议损坏；诊断成本高。
- **建议**: `AtomicBoolean readerActive` 读入口守卫（第二读者 fail-fast 抛错并注明契约）；或 `headerBuf` 降为 recv 局部变量（消除共享态）+ call() 文档标注与 recv 循环互斥。
- **失效面枚举**（同族"单连接/单会话实例多入口共享"清扫）: ① `SftpClient`——单 ChannelSftp（jsch 非线程安全）+ `getInputStream` 流式读与其他操作并发即协议交织，同样无守卫无声明；现仓库消费方 `SftpClientFactory.newClient()` 逐次新建规避 → 同族登记，不另立案（修复时一并补契约声明/守卫）；② `OssFileServiceClient`——AmazonS3 SDK 线程安全契约 → OK；③ `LocalFileServiceClientFactory`（newClient()=this）——无状态委托 → OK；④ `SocketServer` 写侧 broadcast/sendTo/processCommand 三入口 synchronized(os) 对齐 → OK（正面对照：同模块写侧已正确帧同步）；⑤ `JdkStreamTransport.send` 多线程（心跳/握手/ACK）——JDK WebSocket sendBinary 内部串行化 → OK。
- **信心水平**: 确定（契约缺失确定；损坏时序在混用前提下确定）
- **误报排除**: 非"声明的边界"——校准条款要求守卫缺失/文档未声明才立案：此处约定未在 SocketClient 声明亦未守卫，仅存于第三方类注释；已给出具体双读交错。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——call()/recv() 双读入口无守卫、契约仅存消费方注释（SimpleRpcClient.java:104）回码成立

### [C1-017] static 可变载体批次 2 清扫：非 final 单例/常量字段 + EnumSet/共享数组经公开访问外泄（C1-006 同族）

- **文件**: `nop-network/nop-netty/src/main/java/io/nop/netty/tcp/TcpChannelInfo.java:8`、`.../handlers/CloseOnErrorHandler.java:19`、`.../handlers/TestFragmentChannelHandler.java:14`、`nop-network/nop-vertx/nop-vertx-commons/src/main/java/io/nop/vertx/commons/NopVertx.java:18`、`nop-integration/nop-integration-api/src/main/java/io/nop/integration/api/sms/SmsMessage.java:16-17`、`nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/bus/MqttServerMessageService.java:44-55`
- **证据片段**:
  ```java
  public static AttributeKey<TcpChannelInfo> ATTR_KEY = AttributeKey.valueOf("channelInfo"); // 非 final
  public static CloseOnErrorHandler INSTANCE = new CloseOnErrorHandler();                    // 非 final
  public static TestFragmentChannelHandler INSTANCE = new TestFragmentChannelHandler();      // 非 final
  private static Vertx _instance;                                                             // 非 final 非 volatile
  public static int TYPE_DEFAULT = 0;  public static int TYPE_PROMOTION = 1;                  // 非 final 常量
  private static final Set<MessageCapability> CAPABILITIES = EnumSet.of(...);                 // 可变集合
  public Set<MessageCapability> getCapabilities() { return CAPABILITIES; }                    // 引用外泄
  ```
- **严重程度**: P3
- **并发场景**: 全平台任意线程；类初始化建立发布 HB，正常路径安全。风险仅在运行期重赋值（非 final 字段）或调用方修改外泄集合（`getCapabilities()` 返回 EnumSet 可 add/remove——`MessageOptionsValidator` 内部只读使用，外泄面为公开 getter）。
- **触发时序**: 理论风险，未具体化（仓库内无重赋值点/无集合修改点）——按口径纪律登记。
- **现状**: 与批次 1 C1-006（HttpRpcMessageTransformer.INSTANCE 等）同族；`NopVertx._instance` 附加非 volatile 缺口（registerInstance 当前无仓库内调用点，`instance()` 为 fail-fast 读取，实际暴露面小）。`BinaryCommand.EMPTY_BYTES` 经 `getData()` 外泄（对空命令返回共享数组，调用方可变长度内容——理论面）。
- **风险**: 防御性缺口；被运行期替换/修改时引入同进程行为分叉或全局常量污染。
- **建议**: 非 final 单例/常量统一补 final；`NopVertx._instance` 加 volatile；`getCapabilities()` 返回 `Collections.unmodifiableSet`；`EMPTY_BYTES` 文档标注只读契约。
- **失效面枚举**: 批次 2 全部 `public/private static` 非 final 字段人工全扫（复现基线 C1a 后）：上述 6 字段/4 类即全集；static final 可变容器全集 = 2 个 EnumSet（本条）+ `BinaryCommand.EMPTY_BYTES`；同族对照批次 1 C1-006（2 处）；`MathHelper.s_randomImpl` 等属 nop-commons 平台设施不重审。
- **信心水平**: 确定（字段形态确定；危害为理论）
- **误报排除**: 按 C1 载体清单义务登记（static 可变字段 + 全局注册表两类），非风格类误报；已按理论风险降 P3。
- **复核状态**: 未复核
- 复核状态: 已保留(P3)｜新发现（防御性）——6 字段/4 类 + 2 EnumSet + EMPTY_BYTES 清单逐项回码成立；危害理论

---

## 维度集外线索（overlay C2/C4/C5/C7/C8/C9）

### [nop-network-integration-CL-001] [C6/C8] JDK SSE 取消竞态窗口：cleanup 赢得调度时 reader/InputStream 永不关闭，连接泄漏（F8）

- **文件**: `nop-network/nop-http/nop-http-client-jdk/src/main/java/io/nop/http/client/jdk/ServerEventPublisher.java:74-135`
- **线索**: `processResponse` 在 sendAsync 完成线程拿到 `response.body()`（InputStream）后，仅当执行体进入 `try (reader)` 才有关闭路径；L120 `if (isCancelled()) return;` 在 try-with-resources 之前直接返回。时序：T1 sendAsync 完成 → whenComplete 调度读循环任务；T2 cancelToken 取消（cleanup → `future.cancel(true)` 对已完成 future 无效）→ T1 executor 任务启动即见 isCancelled → 返回，reader 从未 close → HTTP 连接无法归还（BodyHandlers.ofInputStream 语义下连接被流占用）。SSE 长驻场景下每次"取消恰逢响应到达窗口"漏一条连接，长时运行累积。
- **移交**: C6（资源获取释放对称）/C8（长时运行）复核；建议该维度确认 `cleanup` 与 executor 任务的全部交错路径并补"cancel 路径也 close reader"。

### [nop-network-integration-CL-002] [C4/C2] fetchSidecarChecksum 在 HTTP 回调线程上阻塞 get(30s)：reactor/worker 停等至多 60s，存在自等待有界死等窗口

- **文件**: `nop-network/nop-http/nop-http-client-apache/src/main/java/io/nop/http/apache/ApacheHttpClient.java:330-332,370-383`、`nop-network/nop-http/nop-http-client-jdk/src/main/java/io/nop/http/client/jdk/JdkHttpClient.java:454-458,475-487`
- **线索**: 下载完成回调（Apache：IO reactor 派发线程；JDK：客户端 executor worker）内同步 `.get(30, TimeUnit.SECONDS)` 发起同客户端的 sidecar 探测（2 个算法串行，最坏 60s）。Apache 侧阻塞的是共享 reactor 线程——该线程上的所有其他连接头阻塞；若 ioThreadCount 小，被阻塞线程恰是 sidecar 请求所需的派发线程时形成自等待（有 30s 超时兜底，非永久死锁）。JDK 侧占用下载/SSE 共享池线程（与 CL-004 叠加）。
- **移交**: C4（线程池/调度）与 C2（锁/阻塞点）复核；建议改为非阻塞链式（whenCompose）或将 sidecar 探测移出回调线程。

### [nop-network-integration-CL-003] [C5] Apache 连接池 maxConnPerRoute 无 >0 守卫：HttpClientConfig.maxRequestsPerHost 默认 0 被直接下推

- **文件**: `nop-network/nop-http/nop-http-api/src/main/java/io/nop/http/api/client/HttpClientConfig.java:27`（默认 0，无初始化器）、`nop-network/nop-http/nop-http-client-apache/src/main/java/io/nop/http/apache/ApacheHttpClientHelper.java:127-128`
- **线索**: `builder.setMaxConnPerRoute(clientConfig.getMaxRequestsPerHost())` 无条件下推；默认配置下即 `setMaxConnPerRoute(0)`。对照同批次 OkHttp 提供方对同一参数有守卫（`OkHttpClientProvider.newBuilder` L95-97 `if (config.getMaxRequestsPerHost() > 0)`），且 `ioThreadCount` 亦走 `>0` 守卫（ApacheHttpClientHelper L107），不对称性指向遗漏。已查 httpclient5 5.4.4 `PoolingAsyncClientConnectionManagerBuilder.setMaxConnPerRoute` 字节码：setter 无校验（直接 putfield）；0 值在 lease 期的实际后果（build 期 fail-fast 或路由级 0 许可饿死）需 C5 按依赖版本行为裁定。
- **移交**: C5（池/buffer 配置）复核；确认 0 值语义并补默认值或守卫。

### [nop-network-integration-CL-004] [C4] JdkHttpClient 单一小池同时承载 SSE 长驻读循环与下载泵任务：threadQueueSize 默认 10，长驻任务占满后队列拒绝

- **文件**: `nop-network/nop-http/nop-http-client-jdk/src/main/java/io/nop/http/client/jdk/JdkHttpClient.java:108-116`（建池）、`.../ServerEventPublisher.java:119-135`（SSE 读循环占线程直至流结束）、`.../JdkHttpClient.java:407-415`（下载 body 泵占线程直至流读完）
- **线索**: `config.getThreadPoolSize()>0` 时以 `DefaultThreadPoolExecutor.newExecutor(threadName, threadPoolSize, threadQueueSize)`（queue 默认 10）统一作为 `client.executor()`；SSE 读循环（`parseEvents` 阻塞 readLine 至流结束）与下载泵（`processDownloadResponse` 阻塞读完整个 body）都是长驻占线程任务。并发长流数量 > threadPoolSize 时新任务入队，队列满后提交被拒（下载失败/SSE 无法启动），或长期滞留队头造成响应停等。默认 threadPoolSize=0 时走 JDK 内部线程不受影响——仅显式配置小池的部署命中。
- **移交**: C4（线程池配置与拒绝策略）复核；确认拒绝策略与队列参数在"长驻任务"负载下的适配性（对照 QueueOverflowPolicy 平台设施）。

### [nop-network-integration-CL-005] [C2/C8/F5] FeishuClient 心跳/ACK/握手共用 JdkStreamTransport.send 无超时阻塞 get()：单线程 scheduler 可被永久挂死，且心跳失败不喂重连

- **文件**: `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/client/JdkStreamTransport.java:106-110`（`ws.sendBinary(...).get()` 无超时）、`.../client/FeishuClient.java:268-281`（sendHeartbeat 吞异常）、`:347-359`（ACK 发送）
- **线索**: 全部 WS 写（心跳、connect 握手、DATA ACK）经 `transport.send` → `sendBinary(...).get()` **无超时阻塞**。对端停止读取/连接半开时发送背压积压 → `get()` 永久阻塞 → 单线程 scheduler 被心跳任务挂死：后续心跳、scheduleReconnect 的定时任务全部排队不再执行 → 长连接静默死亡（F5："不抛异常也不产出"的挂死对重连机制不可见）。叠加：`sendHeartbeat` catch(Exception) 仅 debug 日志——心跳发送失败**不触发 onClose/onError**，不喂重连逻辑；WS 若无 close/error 事件（半开）则 `connected` 恒为 true，客户端停在假活状态。
- **移交**: C2（等待点超时全覆盖）/C8（长时运行假活）复核；建议 send 加超时（`get(N, SECONDS)` + 失败时主动 close 并回调 listener.onError），心跳失败计入连续失败计数并驱动重连。

### [nop-network-integration-CL-006] [C4] FeishuClient 单线程 scheduler 上 attemptReconnect 同步 connect().join() 阻塞至 15s：重连窗口内心跳/重试全部停摆

- **文件**: `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/client/JdkStreamTransport.java:87-94`（`buildAsync(...).join()`）、`.../client/FeishuClient.java:101-103`（单线程 scheduler）、`:297-310`（attemptReconnect 同步调用）
- **线索**: `attemptReconnect` 跑在单线程 scheduler 上，`transport.connect` 内 `.join()` 同步等待 WS 握手（connectTimeout 15s）。服务端不可达时每轮重连阻塞该线程 15s：期间 `scheduleHeartbeat` 的新任务、并发 scheduleReconnect 的重试任务全部积压排队（`schedule` 本身不失败）；恢复后积压任务突发执行（多条过期心跳/重复重连尝试连发）。与 CL-005 叠加时（send 挂死在心跳任务上）scheduler 永久不可用。
- **移交**: C4（线程池/调度模型）复核；建议 connect 移出 scheduler（独立连接线程或异步链 `buildAsync(...).whenComplete` 回投），或 scheduler 池化。

### [nop-network-integration-CL-007] [C3/F2] MqttConnection.sendAsync 连接检查同步抛出：多连接下发"半个成功"无恢复语义

- **文件**: `nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/impl/MqttConnection.java:227-239`（`throw ERR_MQTT_NOT_CONNECTED`）、`.../bus/MqttServerMessageService.java:73-88`（findConnections → 逐连接 sendAsync → waitAll）
- **线索**: `MqttServerMessageService.sendAsync` 先 `findConnections` 快照再逐连接 `sendAsync`：连接可在快照与发送之间关闭，`MqttConnection.sendAsync` 对 `!isConnected()` **同步 throw**（不是 completeExceptionally）→ 异常直接冲出循环，后续连接不再发送，已发送的部分连接无整体失败语义（调用方只看到一个异常，无"哪些设备已收到"信息）；消息为 fire-and-forget 无重试/补偿路径（F2"半个成功"）。
- **移交**: C3（异步链/失败语义）复核；建议 `MqttConnection.sendAsync` 改返回 failed future（消灭同步抛出），批量路径 `FutureHelper.waitAll` 聚合部分失败并上报失败连接清单。

### [nop-network-integration-CL-008] [C3/F9] PacketCodecHandler 吞 encode/decode 异常：消息静默丢弃，仅靠超时兜底

- **文件**: `nop-network/nop-netty/src/main/java/io/nop/netty/handlers/PacketCodecHandler.java:43-66`（encode 吞 RuntimeException 至 maxEncodeErrorCount）、`:68-110`（decode 同型）
- **线索**: encode 失败在 `maxEncodeErrorCount` 内被吞（`resetWriterIndex` 后消息既不写出、写 promise 也无失败可达调用方）——RPC 场景 `RpcMessageHandler` 的在途 future 需等 `checkTimeout`（100ms 巡检 + responseTimeout）才异常完成，期间调用方阻塞；decode 失败被吞则该帧静默丢弃（对端无感知，应用层消息丢失）。error-count 后门本身是防雪崩设计（连续失败超阈值才抛），但单次失败的静默面未向调用方传播。
- **移交**: C3（异步失败传播）复核；建议 encode 失败时 `ctx.fireExceptionCaught` 或对关联 promise fail（可追溯 msgId），decode 失败至少累计指标并告警。

### [nop-network-integration-CL-009] [C9/F9] TencentEmailSender 吞 FrequencyLimit/EmailAddressIsNULL 异常：与 JavaEmailSender "必须重抛"裁定同族不对称

- **文件**: `nop-integration/nop-integration-email-tencent/src/main/java/io/nop/integration/email/tencent/TencentEmailSender.java`（sendEmail 尾部 catch：两错误码 `return`，其余仅 LOG.error 不重抛）
- **线索**: `IEmailSender` 消费契约"不抛即成功"（`JavaEmailSender.withTransport` 注释明示：吞掉异常会让 nop-auth MFA 发码链无条件 setEmailSent(true)）。`TencentEmailSender.sendEmail` 捕获 `TencentCloudSDKException` 后：`EmailAddressIsNULL`/`FailedOperation.FrequencyLimit` 静默 return，其余错误码也仅记日志**不重抛** → 频控/参数错/网络失败全部表现为"已发送"，MFA 验证码/通知邮件静默丢失且状态标记错误。与同模块 `JavaEmailSender` 的"记录日志后必须重抛"裁定（W16-impl-ext plan Phase 1 落点约束）直接不对称——F3 同族兄弟未修齐。
- **移交**: 错误处理/一致性维度复核；建议对齐 JavaEmailSender 语义：仅可明确"无需调用方感知"的错误才可吞且须留审计日志，FrequencyLimit 应抛出由上层退避重试。

### [nop-network-integration-CL-010] [C3/C1] VertxMqttServer 异步 IMqttAuthChecker 下 acceptEndpoint 在回调线程执行：handler 注册与 event loop 消息分发无 HB 保证

- **文件**: `nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/impl/VertxMqttServer.java:77-102`（checkAuthAsync.whenComplete → acceptEndpoint）、`:116-125`
- **线索**: `authChecker.checkAuthAsync(...).whenComplete(...)` 的完成线程 = 认证实现的选择：仓库内唯一实现 `SimpleMqttAuthChecker` 返回 immediate future → whenComplete 同线程（event loop）执行 → 无窗口。但 `IMqttAuthChecker` 是公开 SPI：异步实现（查 DB/远端）在自有线程 complete → `acceptEndpoint` → `new MqttConnection`（注册全部 endpoint handler + `endpoint.accept()` 发 CONNACK）在该线程执行，而后续 MQTT 报文回调在连接的 event loop 读 handler 字段——跨线程注册/消费无可见性契约（vertx-mqtt 内部 handler 字段非 volatile，依赖 Vert.x "线程安全"契约的覆盖范围不明）。CONNACK 先于 handler 可见时，客户端紧随的 SUBSCRIBE/PUBLISH 可能命中未发布完成的 handler。
- **移交**: C3（异步边界/上下文传播）复核；建议 `whenComplete` 中经 `context.runOnContext`（或 `Vertx.currentContext()` 捕获）将 acceptEndpoint 归位到 endpoint 的 event loop，并在 SPI javadoc 声明线程契约。

---

（C1 维度两批次收官：批次 1 = C1-001..007 + CL-001..004；批次 2 = C1-008..017 + CL-005..010。）

## 维度复核结论（WI 4.3 合并批复核：C1，独立复核员，2026-10-20）

> 复核范围：C1-001…020 全部 20 条（C1-018…020 条目本体在 C1-round2.md，状态行写各 round 文件，判定并入本表）。方法=逐条回码验证 + 时序论证核验 + 严重度校准 + 与 cra-index WI 1.1–4.2 台账、本单元 C3/CL 文件同案对照。原发现条目未改动，判定与现状标注见各条末尾追加的「复核状态」行。

### 逐条判定表

| ID | 初判 | 判定 | 最终严重度 | 同案命中 / 现状要点 |
|---|---|---|---|---|
| C1-001 | P1 | 保留 | P1 | C6-014 并入本条(a)；站点邻接 WI 2.1 CL-024 非同缺陷 |
| C1-002 | P2 | 保留 | P2 | ↔ C3-008、C6-008（三登记一工单） |
| C1-003 | P2 | 保留 | P2 | — |
| C1-004 | P2 | 驳回（并入） | — | ↔ C3-010 同站点同根；本体回码成立，防重复计数 |
| C1-005 | P2 | 保留 | P2 | 实仓消费面 XLangDebugConnector 命中 |
| C1-006 | P3 | 保留 | P3 | — |
| C1-007 | P3 | 保留 | P3 | — |
| C1-008 | P1 | 保留 | P1 | ↔ C3-018、C6-020（三登记一工单）；同族前案 nosql-cdc（非同站点） |
| C1-009 | P1 | 保留 | P1 | ↔ C3-017（P2，判级分歧留 7.1） |
| C1-010 | P2 | 保留 | P2 | — |
| C1-011 | P2 | 保留 | P2 | 实仓消费方 FeishuConnector（nop-ai-gateway）可达 |
| C1-012 | P2 | 保留（子项勘误） | P2 | 子项(c) 不可达勘误；(a)(b) 成立 |
| C1-013 | P2 | 保留 | P2 | ↔ C6-022（同根不同交错） |
| C1-014 | P2 | 保留 | P2 | ↔ C6 文件 CL-027（本条主登记闭环） |
| C1-015 | P2 | 保留 | P2 | ↔ C3-031；注释转述勘误不改本体 |
| C1-016 | P2 | 保留 | P2 | — |
| C1-017 | P3 | 保留 | P3 | — |
| C1-018 | P2 | 保留 | P2 | 条件面=异步 authChecker SPI；↔ CL-010/C3-CL-018 互补 |
| C1-019 | P2 | 保留 | P2 | 第 1 轮 SocketServer 判定前提勘误成立 |
| C1-020 | P3 | 保留 | P3 | 理论风险 |

### 复核统计

- **保留 19**（P1×3：C1-001/008/009；P2×12；P3×4）、**驳回（同案并入）1**（C1-004→C3-010）、**降级 0**、**证伪驳回 0**。
- 两条复核勘误（不改变判级）：① C1-012 子项(c)「cancel 末订阅→重订阅挂载竞态」不可达——其前提（cancel 后 `subscribers.isEmpty()==true`）与 C6-026② 的代码事实矛盾：`computeIfPresent` 恒返回列表（含空列表），map 条目首次订阅后永存，`isEmpty()` 恒 false，L112 摘除分支为死码（该死码面已由 C6-026② 独立立案）；(a) 拷贝构造竞态与 (b) 并发 add 丢失更新回码成立，维持 P2。② C1-015 证据片段对源码注释的转述有误——实际注释为「resolve 先于 consume 以便失败重试」（resolve 在 consume 之前），代码顺序（L186 resolve→L189 remove）描述正确，TOCTOU 本体不受影响。

## 最终保留项（C1 维度，19 条）

- **P1×3**：C1-001（并入 C6-014）、C1-008（并入 C3-018/C6-020 修复单元）、C1-009。
- **P2×12**：C1-002（并入 C3-008 修复单元）、C1-003、C1-005、C1-010、C1-011、C1-012、C1-013（与 C6-022 同批）、C1-014、C1-015（并入 C3-031 修复单元）、C1-016、C1-018、C1-019。
- **P3×4**：C1-006、C1-007、C1-017、C1-020。
- 现状标注：新发现×20（CRA 台账零前案；站点邻接引用不构成同缺陷）。

### 跨单元回写核对

1. **JdkHttpClient SSE readLine 无读超时（WI 3.1 C7-001 本体落点）**：已在本单元立案，**非复核遗漏**——本体面落在本单元 C6 维度 C6-004 失效面③ + C3 维度 C3-003（P1）；消费面 WI 3.1 C7-001 已保留 P1 并明注「nop-http 仅作证据引用，不作为发现对象」。回写闭环成立：修复批须两侧同批（聚合层期限 + 传输层读超时/可中断）。
2. **IHttpResponse 断连观测（WI 2.3 CL-025 落点）**：dispatch-log 已声明「本单元实现面落点」；实现面由 C3-002/C3-003（取消传播）与 C6-004③/CL-026（滞留面）承接；IHttpResponse 平台原语面（断连观测 API 缺失）仍归 WI 2.3 CL-025 平台轮并维持「C3-010/C4-003 修复先决」关系。C1 维度无补立义务。
3. **WI 2.1 CL-021 feishu 点名核实**：已核 WI 2.1 C3-round2 CL-021 原条目——明列「nop-integration-feishu 1 处」（raw `Executors.newSingleThreadScheduledExecutor`）。**FeishuClient raw 线程面确属点名移交范围**；本单元 C1/C6 未重复立案（正确防重复）；行为面缺陷（C1-011、C6-024、C1 文件 CL-005/CL-006）独立成立，修复注记须链接 WI 2.1 CL-021 载体轮（先平台设施后使用方）。

### 同案命中清单（C1 维度）

| 本单元条目 | 同案条目 | 关系 | 处置 |
|---|---|---|---|
| C1-002 | C3-008、C6-008 | 同站点同根 | 一工单；C6-008 驳回并入 |
| C1-004 | C3-010 | 同站点同根 | C1-004 驳回并入 C3-010 |
| C1-008 | C3-018、C6-020 | 同站点同根（窗口②） | 三登记一工单；C6-020 独有窗口①随同 diff |
| C1-009 | C3-017 | 同站点同根 | 一工单；判级分歧（P1/P2）留 7.1 |
| C1-013 | C6-022 | 同根不同交错 | 同批修复 |
| C1-014 | C6 文件 CL-027 | 同站点（线索 vs 发现） | C1-014 主登记，CL-027 闭环 |
| C1-015 | C3-031 | 同站点同根 | 一工单 |
| C1-018 | C1 文件 CL-010、C3 文件 CL-018 | 同触发条件不同本体 | 互补保留，修复批协同 |
| C1-001 | C6-014；C6-010/C6-015（同文件互补面） | 并入/互补 | C6-014 驳回并入；C6-010/015 独立面保留 |
