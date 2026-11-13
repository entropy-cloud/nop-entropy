# C6 深挖第 2 轮 — nop-network-integration（WI 4.3）

## 深挖范围与方法

按派发任务对第 1 轮 27 条（C6-001…027，两批次收官，配对表已建）之外的邻接面定向深挖，全部基于 live code 逐文件实读：

1. **oauth token 缓存清理**：`AddAccessTokenHttpClientEnhancer.authTokens` 的写入/读取/过期/移除全路径复扫——第 1 轮 C6-008 已立案"条目无配置移除清理"，本轮复核无新面（无其他持有点、`Oauth2TokenResponseBean` put 后只读）。
2. **vertx 关闭序**：`VertxMqttServer.doStart/doStop`（listenFuture 生命周期、`mqttServer.close()` 的等待/观察语义、`sessionManager` 跨停机状态）、`NopVertx`/`VertxHelper`、`MqttConnection` 关闭回调在停机期的执行面 → **C6-028**。
3. **socket 关闭序**：`SocketClient.cleanup`（socket→is/os→heartbeatFuture→onChannelClose 的顺序与重入性）、`SocketServer.doStop/closeAllConnections/removeSocket` 账本收敛、call/ping 无 recv 循环场景的关闭语义 → 核对通过（restart 面的标志竞态归 C1 文件 C1-019，不重复立案）。
4. **netty timer/future 资源**：`RpcMessageHandler`（channelRegistered 调度 / channelInactive 取消）、`GlobalChannelTrafficShapingHandler` release、`ReadTimeoutHandler`/`WriteTimeoutHandler`、`HeartbeatHandler`、`NettyHelper`、`NettyTcpServer/Client` doStop 清理清单复核 → 成功路径全通过；`channelRegistered` 调度点的平台语义缺口登记为 **CL-034**（线索，需 netty close 语义核实）。
5. **oss/sms/email 客户端其他资源**：`OssFileServiceClientFactory`（@PostConstruct/@PreDestroy 对称复核）、`OssFileServiceClient`（downloadToStream/getInputStream/listFiles）、`TencentSmsSender`（SmsSingleSender 无 close API 的对照成立）、`YunpianSmsSender`（finally close）、`JavaEmailSender`（withTransport finally）、`ZxingQrcodeService` → 全部通过，无第 1 轮配对表之外的新获取点。
6. **server-filters 面**：`CoreInitializationGuardFilter` 全文——无资源获取/释放面（503 分支 `sendResponse` + completedFuture，不持任何可关闭对象），核对通过；`isSuspended()/isInitializerRunning()` 检查与 `next.get()` 之间的 TOCTOU 属 C1 正确性面（守卫通过后挂起可立即开始），非资源生命周期缺陷，登记不立案。
7. **隐藏站点（lambda 内资源捕获）**：`ProgressBodyPublisher`/`ConcatenatedBodyPublisher`/`MultipartBodyPublisher`（jdk body publisher 链）、`HttpFetchTask`、`MqttMessageBuilder`、`FeishuBindProvider`、`FileServiceResource` 包装流 → 除 C1 文件已立案的 `ConcatenatedBodyPublisher`（C1-020）外无资源捕获缺口。

**盲审纪律**：未读 `ai-dev/audits|plans|bugs|lessons|backlog/**`（第 1 轮三文件除外），未查 git log。与第 1 轮同根不立案，扩展以「扩展 C6-{nnn}/扩展 CL-{nnn}」并入。

## 新增发现

### [C6-028] VertxMqttServer.doStop 异步 close 不等待且不清理会话表：重启窗口 bind 冲突被静默吞掉、死会话跨停机周期驻留污染下行路由

- **文件**: `nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/impl/VertxMqttServer.java:173-187`（doStop：L177 `mqttServer.close()` 异步仅记日志、L184-185 置 null）、`:67-75`（doStart 直接 `MqttServer.create(...).listen()`）、`:47`（`sessionManager` final 字段，停机不复位）；后果链 `.../impl/MqttSessionManager.java:68-84`（findConnections 不滤活）、`.../impl/MqttConnection.java:229-231`（对死连接 sendAsync 同步抛）、`.../bus/MqttServerMessageService.java:84-87`（扇出循环）
- **证据片段**:
  ```java
  protected void doStop() {
      if (mqttServer != null) {
          // Vert.x 5:close 回调重载移除,改 Future API(plan 2431 WI5)
          mqttServer.close().onComplete(res -> {           // L177：异步 close，不等待完成
              if (res.failed()) { LOG.error(...); }
              else { LOG.info("nop.vertx.mqtt.server.stopped:port={}", mqttServer.actualPort()); }
          });
          mqttServer = null;                               // L184：doStop 返回时端口多半仍在监听
          listenFuture = null;
      }                                                    // sessionManager（sessions/subscriptions）不清理
  }
  ```
- **严重程度**: P2
- **并发场景**: 生命周期管理线程（stop→start 顺序调用，运维重启/配置热更路径）与 Vert.x 内部 close 完成线程；无任何 await/屏障把"端口已释放、endpoint 已关闭"传递给后续 doStart。
- **触发时序**: (a) **重启 bind 冲突**：T1 `stop()` → L177 提交异步 close → doStop 返回，LifeCycleSupport 置 stopped；T1' `start()` → doStart L74 `listen()` 绑定同一端口——旧 server 的 close 尚未完成（Vert.x close 涉及逐 endpoint 关闭与 acceptor 退出，毫秒~十毫秒级）→ bind 失败 → 该 `listenFuture` 无人观察（第 1 轮 C6-023 已立案 listen 失败吞掉的通用面），**重启以"bean ACTIVE + 无监听"告终且无任何日志**——失败被 C6-023 的无观察者机制二次放大为静默；若 close 恰在 listen 失败之后才完成，旧端口释放后无人重试，服务永久缺失。(b) **死会话跨停机驻留**：`sessionManager` 是 final 字段，doStop 不清空；`mqttServer.close()` 关闭 endpoint 时虽会触发 closeHandler → `removeConnection`，但该清理依赖每条连接的回调送达（异步、可丢失——对照第 1 轮 C1-018 的回调丢失形态）；凡回调未达的连接在 sessions/subscriptions 中残留**指向已死 endpoint 的连接**，重启后 `findConnections` 直接命中 → 下行 `sendAsync` 在 L229-231 同步抛 `ERR_MQTT_NOT_CONNECTED`，单个残留会话持续打断整批下发（与 C3-021 的扇出中断叠加），直至该 clientId 重连接管。
- **现状**: doStop 是"发射后不管"形态——既不等待 close 完成（无 `toCompletionStage().toCompletableFuture().get(timeout)`），也不对会话表做停机对账；对照同模块 `NettyTcpServer.doStop`（`channelGroup.close().awaitUninterruptibly()` 后再关组）与 `SocketServer.doStop`（同步 close + closeAllConnections）的收敛写法，本站点是三兄弟中唯一不等待且不清理的。
- **风险**: 容器重启编排（stop→start 为标准动作）下 MQTT 接入以确定概率静默失效；残留死会话使重启后的下行推送出现"设备在线却持续失败"的诡异态，排障只能靠客户端侧反推。
- **建议**: doStop 内同步等待：`mqttServer.close().toCompletionStage().toCompletableFuture().get(closeTimeout, SECONDS)`（失败降级日志后继续），成功后 `sessionManager` 走一套 `clear()`（对 sessions 残留逐条 removeConnection 或直接清表——重启后旧 endpoint 无存活语义）；与 C6-023 的 listenFuture 观察修复同一提交收敛。
- **失效面枚举**（F3 关闭序清扫）: ① `VertxMqttServer.doStop`（中招，本条）；② `NettyTcpServer.doStop`（close().awaitUninterruptibly() 后关组 → 对照正确）；③ `SocketServer.doStop`（同步 close + closeAllConnections → OK；其 stopped 标志 restart 竞态归 C1-019，不属本条关闭序面）；④ `NettyTcpClient.doStop`（shutdownGracefully 不等待——停机后残留 channel 面，第 1 轮 C6-022 已立案同族）；⑤ `SocketClient.close`（同步 cleanup → OK）；⑥ `FeishuClient.stop`（关闭序完整但与在途 connect 的竞态已由第 1 轮 C6-021/C6-025 立案）。
- **信心水平**: 确定（close 异步不等待、会话表不清理为静态事实；bind 冲突窗口为 stop→start 的常态时序）
- **误报排除**: 与第 1 轮 C6-023（listen 失败无观察者）同文件但**缺陷本体不同**——C6-023 是启动面失败语义，本条是停机面关闭序（不等待 + 不清理），且后果链（重启静默失效 + 死会话驻留）不依赖 C6-023 的修复而独立存在；非"异步 close 由 Vert.x 保证最终完成即可"的已声明边界——问题恰在完成前后的窗口与状态对账。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——doStop 异步不等待 + sessionManager 不清空回码成立；关联前案 2026-09-22 3.9 still-live P2-15（同方法 doStop 回调 NPE 相邻面，未修复）；同族对照 nosql-cdc 停机滞留（非同站点）

## 扩展第 1 轮发现（并入，不另立案）

- **扩展 CL-005（socket 侧同族佐证）**: `SocketClient.heartbeat`（nop-socket/.../SocketClient.java:144-154）与 CL-005（Feishu 心跳）同族：心跳发送失败 `catch (Exception)` 仅 `LOG.trace`（级别低于 warn）、不置位任何状态、不喂重连；recv 循环路径可由读失败自愈，但 call/ping-only 用法下心跳静默停摆无观察面。登记佐证，不另立案（CL-005 的修复方案——发送失败升级告警并驱动重连——可同族覆盖）。

## 维度集外线索（overlay）

### [nop-network-integration-CL-034] (C7/C6) RpcMessageHandler 超时轮依赖 channelInactive 兜底取消——connect 失败路径是否触发 channelInactive 取决于 netty close 语义，若不触发则每次失败连接泄漏一个 100ms 周期任务

- `nop-network/nop-netty/src/main/java/io/nop/netty/handlers/RpcMessageHandler.java:68-74`（channelRegistered 即 `scheduleWithFixedDelay(checkTimeout, 100, 100ms)`）与 `:101-116`（唯一取消点为 channelInactive）；调度入口 `NettyTcpClient.java:157-162`（ChannelInitializer 在 channel **注册时**即 initChannel——先于 connect 结果）。netty 4.1 对"已注册但从未 active 的 channel"执行 close 时是否派发 channelInactive 受 `wasActive` 门控（需按依赖版本字节码核实，同 C6 卷对 vertx/HC5 的核实口径）：若不派发，则服务端不可达 + autoReconnect 场景下**每次失败连接泄漏一个常驻周期任务**（挂在该 channel 的 event loop 上，外部注入共享 workerGroup 时不随 server 关闭回收）；若派发，则仅余 100ms 空转窗口，无缺陷。交 C7 按"失败路径资源残留"口径核实裁定；防御性修复方向：调度下沉到 channelActive，或 channelUnregistered 兜底取消。

## 继续判定

**终止信号。** 本轮新增 1 条：P2×1（C6-028），无 P0/P1；扩展 1 项（CL-005 同族佐证）、线索 1 条（CL-034，待 C7 核实，非立案发现）。收敛证据：(1) 严重度剖面自第 1 轮 P0×1/P1×10/P2×13/P3×3 单调下移至单条 P2，且该条位于第 1 轮配对表已建但仅核对了"成功路径清理"的 vertx 关闭序面，属该表的自然补全而非新资源类；(2) 第 1 轮资源获取点配对表（批次 1+2 共 28 行）本轮逐行复核成功/失败/取消/停机四路径，除上表所列外无新缺口；(3) 深挖任务书六个方向中，oauth 清理（C6-008 覆盖，无新面）、vertx 关闭序（C6-028）、socket 关闭序（核对通过）、netty timer/future（成功路径通过 + CL-034 线索）、oss/sms/email（全部通过）、server-filters（无资源面，核对通过）全部闭合。判定：C6 维度收官，C6-028 并入修复基线；CL-034 作为待核实线索移交 C7 维度（长时运行）复核，不影响本维度收敛，无需第 3 轮。

## 复核结论（WI 4.3 合并批复核）

- C6-028 已保留(P2)｜新发现——doStop 异步 close 不等待 + sessionManager 停机不清空回码成立；关联前案 2026-09-22 3.9 still-live P2-15（同方法 doStop 成功回调读已置 null 的 mqttServer 字段 NPE——相邻面，未修复，修复同批收敛）；同族对照 check/nosql-cdc.md 停机状态滞留面（非同站点）。
- 判定汇总：1 条保留（P2），无降级/驳回；CL-034 维持移交 C7 的线索定位（非立案发现，不计入本批复核）。其「与 C6-023 缺陷本体不同」的并案排除理由成立（C6-023 启动面失败语义 vs 本条停机面关闭序）。逐条判定表、最终保留项与跨单元回写核对见 C6-resource-lifecycle.md「维度复核结论」。
