# C1 深挖第 2 轮 — nop-network-integration（WI 4.3）

## 深挖范围与方法

按派发任务对第 1 轮 17 条（C1-001…017，两批次收官）之外的邻接面定向深挖，全部基于 live code 逐文件实读：

1. **netty handler per-channel 状态**：`RpcMessageHandler`（futures/timer/executor/channel 全字段的写点复扫）、`HeartbeatHandler`/`MessageCounterHandler`/`ConnectionInfoHandler`/`NettyChannelGroupHandler`/`CloseOnErrorHandler`/`TestFragmentChannelHandler`（实例状态、@Sharable、channel attr 生命周期）、`NettyChannelHelper.addCommonChannelHandler` 装配序、`NettyTcpServer`/`NettyTcpClient` 全文复核。
2. **vertx MQTT server 会话状态**：`VertxMqttServer`（acceptEndpoint 装配序、publishListeners、doStop）、`MqttConnection`（complete()/onCloseCallback/ping 状态机）、`MqttSessionManager`、`MqttServerMessageService` 全文复核。
3. **socket 契约外状态**：`SocketClient`（headerBuf 之外全部字段：heartbeatFuture/lastWriteTime/LongAdder/closed 生命周期与回调挂载）、`SocketServer`（stopped 标志、accept 循环、connections 账本、doStart/doStop 重启路径）全文复核。
4. **oauth enhancer 链其他竞态**：`AddAccessTokenHttpClientEnhancer`（EnhancedClient 内部类生命周期、getAccessToken 同步抛出、fetchToken）、`DelegateHttpClient` 全部 onXxxBegin 分派面、`DefaultHttpClientFactory`/`OkHttpClientProvider` 工厂状态、`HttpClientAuthConfigs` 消费面。
5. **基线外隐藏站点**：`ConcatenatedBodyPublisher`/`ProgressBodyPublisher`/`MultipartBodyPublisher`、`HttpFetchTask`、`HttpServerHelper`/`CoreInitializationGuardFilter`（server-filters 面）、`AopRpcServiceInvocation`/`FlowControlRpcServiceInterceptor`/`DefaultFlowControlRunner`、三个 StreamingEventAggregator、`NopVertx`/`VertxHelper`。

**盲审纪律**：未读 `ai-dev/audits|plans|bugs|lessons|backlog/**`（第 1 轮 C1/C3/C6 三文件除外），未查 git log。与第 1 轮同根的形态一律不立案；第 1 轮"登记不立案"的裁定（如 NettyTcpServer doStop×initChannel 理论竞态、sendToChannel 无 handler NPE、MqttConnection.lastPingTime）不翻案。

**批次 1 两处判定的勘误（影响本 轮口径）**：① 第 1 轮 C1 清单表对 `SocketServer` 的判定"stop 后不重启 → OK"与代码矛盾——`doStart` L142-143 注释明示"支持重启：清停止标志后重新进入 accept 循环"，该假设不成立，由此得出 **C1-019**；② 第 1 轮对 vertx 会话面聚焦 `removeConnection` 语义，未覆盖 `acceptEndpoint` 的回调挂载时序，由此得出 **C1-018**（与 CL-010/CL-018 同触发条件、不同缺陷本体，见该条误报排除）。

## 新增发现

### [C1-018] VertxMqttServer.acceptEndpoint 回调挂载时序：异步 auth 下 complete() 先于 setOnCloseCallback 执行，close 回调永久丢失 → 死连接驻留会话表污染下行路由

- **文件**: `nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/impl/VertxMqttServer.java:116-125`（acceptEndpoint 装配序）；`.../impl/MqttConnection.java:64-70`（构造器内 `endpoint.accept()`）、`:207-209`（setOnCloseCallback）、`:211-224`（complete() 读回调）；后果链 `.../impl/MqttSessionManager.java:33-40` + `.../bus/MqttServerMessageService.java:79-87` + `.../impl/MqttConnection.java:229-231`
- **证据片段**:
  ```java
  // acceptEndpoint（运行在 authChecker 完成线程，CL-010/CL-018 已证非 event loop）：
  MqttConnection connection = new MqttConnection(endpoint, wrapHandler(mqttHandler), sessionManager);
  //   ↑ 构造器 → init()：注册全部 handler 并 endpoint.accept() —— CONNACK 已发出，closeHandler 已武装
  connection.setOnCloseCallback(() -> sessionManager.removeConnection(connection));  // L119：此刻才挂回调
  IMqttConnection old = sessionManager.addConnection(connection);                    // L121：死连接照样入表
  // MqttConnection.complete()（event loop，closeHandler/disconnectHandler 触发）：
  Runnable callback = this.onCloseCallback;   // L219：可能仍是 null
  if (callback != null) { callback.run(); }   // L221：null → removeConnection 永不被调用
  ```
- **严重程度**: P2
- **并发场景**: 异步 `IMqttAuthChecker` 实现（查 DB/远端的公开 SPI）的完成线程执行 acceptEndpoint，与 endpoint 所属 event loop 上的 closeHandler/disconnectHandler 交错；无任何同步或归位（`runOnContext`）屏障。
- **触发时序**: T1（auth 完成线程）执行 L117 构造 MqttConnection——构造器内 L139 `endpoint.accept()` 已发 CONNACK 且 closeHandler 已注册；T2（event loop）客户端收到 CONNACK 后立即断开（健康探活、端口扫描、秒连秒断的设备——常态事件）→ `complete()`：`closed` 检查通过并置位 → `handler.onClose` 执行 → L219 读 `onCloseCallback` **仍为 null**（T1 尚未执行到 L119）→ 回调丢失，`removeConnection` 永不执行；T1 继续 L119 设置回调（永不再被触发）、L121 `addConnection` 把**已关闭的连接**放入 sessions。此后 `findConnections(topic)` 持续命中该死连接，`MqttServerMessageService.sendAsync` 循环内在 `MqttConnection.sendAsync` L229-231 同步抛 `ERR_MQTT_NOT_CONNECTED`——整批下行被单个死会话反复打断（与 C3-021 叠加），直至该 clientId 重连接管。subscriptions 侧同理残留（本应随 removeConnection 清除的过滤器集合永存）。
- **现状**: 回调挂载（L119）与可触发点（构造器内的 accept + handler 注册）顺序倒置，且无"注册后再 accept"或"event loop 归位"的屏障；`closed` 守卫只保证 complete() 单次，不保证回调已挂载。
- **风险**: 每个秒连秒断的客户端泄漏一条 sessions/subscriptions 记录（跨接管前不可回收）；死连接作为路由目标反复触发下行扇出异常。触发前置条件是部署方装配异步 authChecker（SPI 公开面，设计上受支持）。
- **建议**: (1) 装配序修正：`setOnCloseCallback` 与 `addConnection` 移到 `endpoint.accept()` 之前（构造器拆分或工厂方法），保证"回调先武装、会话先入表、最后发 CONNACK"；(2) 叠加 CL-018 的归位方案：whenComplete 内经 `Vertx.currentContext().runOnContext` 把 acceptEndpoint 整体投回 endpoint 的 event loop，两条路径同治。
- **失效面枚举**（F3 同族清扫）: ① `VertxMqttServer.acceptEndpoint`（中招，本条）；② `MqttConnection` 构造器内 handler 注册→accept 序（构造器同步段，event loop 内自洽 → OK）；③ `SocketClient.connect` 的 onChannelOpen 挂载（同步于 connect 返回前完成，无外线程触发面 → 挂载时序 OK；其回调异常面已由第 1 轮 C6-027 立案）；④ `NettyChannelGroupHandler` channelActive/inactive 自注册自注销（同 event loop → OK）；⑤ `MqttServerMessageService` dispatcher 挂载/摘除（check-act 缺陷已被第 1 轮 C1-012/C3-020/C6-026 覆盖，不属"挂载落后于可触发点"形态）。
- **信心水平**: 确定（交错由线程归属直接可证；触发依赖异步 authChecker 装配）
- **误报排除**: 与第 1 轮 CL-010/CL-018 同**触发条件**（异步 auth 完成线程）但**缺陷本体不同**——两条 CL 指向 handler 注册/HB 与 event loop 归位缺口，本条是 `onCloseCallback` 挂载晚于 `complete()` 可触发点的**纯时序倒置**（即便所有字段可见性完美、且全部归位到同一线程以外的第三线程也会发生），第 1 轮未立案该面；非"CHM 原语误用"类误报。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现（条件面=异步 authChecker SPI）——构造器 accept 先于回调挂载的时序倒置回码成立；与 CL-010/C3-CL-018 同触发条件不同本体，互补保留

### [C1-019] SocketServer stop→start 重启：共享 volatile stopped 的检查竞态使旧 accept 循环不退出——每次重启泄漏一个常驻 accept 线程并形成双循环争抢

- **文件**: `nop-network/nop-socket/src/main/java/io/nop/socket/SocketServer.java:121-145`（doStart：L143 `stopped=false` 复位、L144 提交 run）、`:148-155`（doStop：L151 `stopped=true`、L153 close socket）、`:208-243`（run 循环：L211 `socket.accept()`、L235 `if (stopped) break`、L239 sleep(1000)）
- **证据片段**:
  ```java
  protected void doStop() {
      stopped = true;                       // L151
      closeAllConnections();
      IoHelper.safeCloseObject(socket);     // L153：解除旧循环的 accept() 阻塞
      socket = null;
  }
  protected synchronized void doStart() {   // L121
      ...
      // 支持重启：清停止标志后重新进入 accept 循环   ← L142 注释与第 1 轮"stop 后不重启"判定矛盾
      stopped = false;                      // L143
      executor.execute(this::run);          // L144
  }
  void run() {
      do {
          try {
              Socket client = socket.accept();   // L211：字段每轮重读——重启后读到新 ServerSocket
              ...
          } catch (Exception e) {
              if (stopped)                       // L235：唯一退出判定——被重启复位后恒为 false
                  break;
              LOG.error("nop.socket.accept-fail", e);
              ThreadHelper.sleep(1000);          // L239：睡眠后回到 L211 继续 accept
          }
      } while (!stopped);
  ```
- **严重程度**: P2
- **并发场景**: executor 线程（阻塞在旧 ServerSocket 的 accept()）与生命周期管理线程（stop→start 顺序调用，典型同线程连续执行）；协调机制仅有共享 `volatile boolean stopped`，无世代号、无循环身份标识。
- **触发时序**: T2 管理线程 `stop()`：stopped=true、close 旧 socket → 旧 accept 抛 SocketException；T2 紧接 `start()`：L143 `stopped=false`、L144 提交新 run 循环。旧循环线程被唤醒（线程唤醒延迟毫秒级，几乎必然晚于 restart 的标志复位）→ L235 读 `stopped` 得 **false**（重启已复位）→ 记 error 日志 → sleep(1s) → 回到 L211 对**新** ServerSocket 调用 accept()——旧循环永不退出。此后新旧两个 accept 循环并发竞争同一 ServerSocket（内核仲裁下每个连接只被一个循环接受，连接账本仍一致），但每执行一轮 stop→start 净增一个常驻 accept 线程。
- **现状**: 退出信号用可被生命周期复位的单比特标志表达，"本轮循环该退出"与"服务已停止"两个语义混用（F10 守卫谓词错位家族，与第 1 轮 C1-013 的 isStopping() 同构但站点、机制均不同）。
- **风险**: 反复重启（配置热更、运维编排）下 accept 线程线性累积；双循环期间 accept 事件分发行为不可预期（如未来加入 per-循环状态时直接错乱）；旧循环持有的异常日志噪声每秒一条（对端无连接时的 accept 抛错路径）。
- **建议**: 引入循环世代号：doStart 递增 `AtomicLong acceptGeneration`，run 循环以局部 final 捕获本代值，异常分支与 while 条件改为"世代仍相等才继续"；或 doStop 不复位共享标志、doStart 以新标志对象/新线程身份启动循环。
- **失效面枚举**（F3 同族清扫）: ① `SocketServer.stopped`（中招，本条）；② `SocketClient.closed`（close 后不复位、无 restart 语义 → OK）；③ `NettyTcpClient` 的 `isStopping()` 谓词（第 1 轮 C1-013 已立案——stop 完成后谓词恒假，同族不同站点）；④ `FeishuClient.started`（第 1 轮 C1-009/C3-017 已立案）；⑤ `VertxMqttServer` 无 stopped 标志（doStop 置 null mqttServer，关闭序问题归本轮 C6 文件 C6-028）。
- **信心水平**: 确定（退出判定与标志复位的代码事实；"旧循环几乎必然存活"基于线程唤醒与重启指令的常规时序，非精确调度保证）
- **误报排除**: 非"低频管理路径粗锁可接受"校准项——后果是每次重启确定性累积常驻线程（资源面）而非启动路径优雅性；非第 1 轮已立案站点（第 1 轮对 SocketServer 的通过判定建立在"不重启"这一与代码注释相悖的前提上，属前提性遗漏而非重复）。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——stopped 复位×旧循环退出判定竞态回码成立；第 1 轮清单表「stop 后不重启」前提勘误成立（doStart「支持重启」注释实证）

### [C1-020] ConcatenatedBodyPublisher.currentSubscription 跨线程非 volatile 读写：request 转发可读到 stale null 丢失背压请求，双重转发致 demand 记账漂移

- **文件**: `nop-network/nop-http/nop-http-client-jdk/src/main/java/io/nop/http/client/jdk/ConcatenatedBodyPublisher.java:44-47`（字段声明）、`:66-79`（内部 publisher onSubscribe：先赋值再快照 demand）、`:91-103`（request：synchronized 内加需求、锁外读 currentSubscription 并转发）
- **证据片段**:
  ```java
  private int currentPublisherIndex = 0;
  private Flow.Subscription currentSubscription;      // 非 volatile；onSubscribe 线程写、request 线程读，无锁两侧
  ...
  public void onSubscribe(Flow.Subscription subscription) {
      currentSubscription = subscription;              // L69：锁外写
      long toRequest;
      synchronized (Subscription.this) { toRequest = demand; }
      if (toRequest > 0) subscription.request(toRequest);
  }
  public void request(long n) {
      synchronized (this) { demand += n; }             // L97
      Flow.Subscription current = currentSubscription; // L100：锁外读——无 HB，可读到 stale null
      if (current != null) current.request(n);
  }
  ```
- **严重程度**: P3
- **并发场景**: 内部 publisher 切换线程（subscribeToNextPublisher 在前一个 onComplete 回调线程执行）与 JDK 客户端请求体泵线程（request 调用方）；仅当两者不同线程时构成 JMM 无 HB 读写。
- **触发时序**: 理论风险，未具体化为高频事件——T1（切换线程）执行 L69 `currentSubscription = subscription`；T2（泵线程）L97 `demand+=n` 后 L100 读 `currentSubscription` 因无 happens-before 读到 stale null → 本次 request(n) 不转发 → 内部 publisher 收不到需求；若 T1 的快照恰又读到 T2 已加的 n，则 T1 转发 + T2 后续转发形成**双重转发**，demand 被多减直至为负（对纯 body publisher 后果限于记账漂移）。当前唯一生产消费方是 JDK HttpClient，其 subscribe 与 request 大概率在同一线程序列化，故按口径纪律降 P3。
- **现状**: `currentSubscription` 的发布完全依赖"读写同线程"的未声明假设；`demand` 记账在锁内而转发在锁外，切换窗口的快照语义未闭合。
- **风险**: 被非 JDK 引擎复用（该类是自研 Flow 实现，无引擎绑定声明）时上传体饥饿悬挂（JDK 请求体无默认超时）；记账漂移使后续段 demand 失真。
- **建议**: `currentSubscription` 加 volatile；request 改为锁内读引用锁外转发（`synchronized(this){ current = currentSubscription; }` 后转发），转发幂等重复无害（Flow request 是累加语义，不会少供）。
- **失效面枚举**（F3 同族清扫）: ① `ConcatenatedBodyPublisher.currentSubscription`（中招，本条）；② `ProgressBodyPublisher`（纯转发包装、无跨线程可变字段 → OK）；③ `HttpInputFileBodyPublisher`/`FileDownloadSubscriber`（测试-only 死代码，第 1 轮 C6-016 已登记维护面）；④ 对照正确实现 `AbstractServerEventSubscription.demand/future`（全部读写收敛于 synchronized(this) → OK）。
- **信心水平**: 确定（JMM 缺口确定）；实际触发依赖跨线程切换，JDK 主路径未观察到
- **误报排除**: 非"CompletableFuture/Flow 原语使用即报"——立案点是自研 Subscription 的发布安全缺口；按第 2 轮任务"基线 grep 之外的隐藏站点"口径登记，理论风险已降档。
- **复核状态**: 未复核
- 复核状态: 已保留(P3)｜新发现（理论）——currentSubscription 非 volatile 回码成立；JDK 主路径同线程序列化，P3 恰当

## 扩展第 1 轮发现（并入，不另立案）

- **扩展 C1-013/C6-022（佐证）**: `NettyTcpServer.getChannelInfos`（NettyTcpServer.java:300-308）在管理线程遍历 channelGroup 并读 attr，与 event loop 上 `NettyChannelGroupHandler.channelActive`（L21-30 先入组）先于 `ConnectionInfoHandler.channelActive`（L11-20 后设 attr）的装配序交错时，返回含 **null 元素**的列表（调用方 NPE）。该 handler 链由消费方 INettyChannelInitializer 装配，仓库内无默认装配点，登记为健壮性备注，不立案。

## 维度集外线索（overlay）

### [nop-network-integration-CL-033] (C9/C2) NettyTcpServer.getChannelInfos 对未完成 channelActive 的 channel 返回 null 元素

- `NettyTcpServer.java:300-308`：`channel.attr(TcpChannelInfo.ATTR_KEY).get()` 无判空即 `infos.add(info)`。channel 入组（NettyChannelGroupHandler.channelActive）与 attr 赋值（ConnectionInfoHandler.channelActive）分属管线中两个 handler 的 channelActive 回调，按管线顺序先后执行——遍历线程（getChannelInfos/getAnyChannel 任意管理线程）可在两回调之间读到"已入组、无 attr"的 channel。管理面健壮性线索，交 C2/C9 复核遍历-发布交错口径；修复建议：null 过滤或 attr 赋值前移至 NettyChannelGroupHandler 之前。

## 继续判定

**终止信号。** 本轮新增 3 条：P2×2（C1-018、C1-019）、P3×1（C1-020），无 P0/P1。收敛证据：(1) 严重度剖面自第 1 轮 P1×3/P2×11/P3×3 显著下移，本轮无新的 P1 及以上共享状态载体；(2) 第 1 轮的五类载体清单（singleton 字段、static、全局注册表、ThreadLocal、逃逸发布）经两批次已全量盘点且本轮复核未发现遗漏载体，新增发现全部位于生命周期时序边（异步 SPI 完成线程、stop→start 重启路径），属第 1 轮判定的前提性遗漏而非新的状态面；(3) 深挖任务书列出的五个方向（netty handler 状态、vertx 会话、socket 双读契约外状态、oauth enhancer、HttpClient 工厂）中，除上述 3 条外其余核查点均收敛为 OK 或第 1 轮已立案（详见深挖范围）。判定：C1 维度收官，新增发现并入修复基线，无需第 3 轮。

## 复核结论（WI 4.3 合并批复核）

- C1-018 已保留(P2)｜新发现（条件面=异步 authChecker SPI）——装配序倒置回码成立；与 C1 文件 CL-010/C3 文件 CL-018 同触发条件、不同缺陷本体，互补保留成立。
- C1-019 已保留(P2)｜新发现——stopped 复位×旧循环退出判定竞态回码成立；第 1 轮清单表「SocketServer stop 后不重启」前提勘误成立。
- C1-020 已保留(P3)｜新发现（理论）——currentSubscription 非 volatile 回码成立；JDK 主路径同线程序列化，P3 恰当。
- 判定汇总：3 条全部保留（P2×2/P3×1），无降级/驳回。合并批同案命中：C1-019 与 C1-013/C6-022 同属「守卫谓词错位」族（站点与机制均不同，独立立案成立）。逐条判定表、最终保留项与跨单元回写核对见 C1-shared-mutable-state.md「维度复核结论」。
