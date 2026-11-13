# C6 资源生命周期与泄漏 — nop-network-integration（WI 4.3）

- **维度**: C6（资源生命周期与泄漏）
- **批次**: 批次 1/2（nop-http 6 叶 + nop-rpc 5 叶）+ 批次 2/2（nop-netty/nop-socket/nop-vertx/nop-codec + nop-integration 10 叶）
- **审计员**: C6 盲审初审员
- **日期**: 2026-10-21
- **状态**: 初审完成（两批次收官，C6-001…027 + CL-021…032），未复核

## 审计范围与口径

### 批次覆盖声明

本批次逐文件审读以下 11 个源码根（`src/main/java` 全部叶子文件，`_gen/` 与 `_` 前缀生成物按纪律排除）：

| 模块叶 | 重点资源面 |
|---|---|
| `nop-network/nop-http/nop-http-api` | FileDownloadSink/.part 状态机、FileTransferHelper、SSE 订阅基类、DefaultHttpInputFile/OutputFile、HttpFetchTask |
| `nop-network/nop-http/nop-http-client-apache` | ApacheHttpClient 连接池生命周期（PoolingAsyncClientConnectionManager/GRACEFUL close）、DownloadResponseConsumer/AbstractStreamResponseConsumer/LineAsyncDataConsumer、ProgressFileEntityProducer、SSE ServerEventPublisher |
| `nop-network/nop-http/nop-http-client-jdk` | JdkHttpClient（sendAsync/ofInputStream 下载、stop）、ServerEventPublisher、FileDownloadSubscriber、BodyPublisher 族 |
| `nop-network/nop-http/nop-http-client-okhttp` | OkHttpClientImpl/OkHttpClientProvider（连接池 evictAll、dispatcher）、下载/SSE |
| `nop-network/nop-http/nop-http-client-oauth` | AddAccessTokenHttpClientEnhancer token 缓存生命周期 |
| `nop-network/nop-http/nop-http-server-filters` | CoreInitializationGuardFilter（无资源面，核对通过） |
| `nop-network/nop-rpc/nop-rpc-api` | ApiContextInterceptor/ContextBinder 绑定-关闭对称性 |
| `nop-network/nop-rpc/nop-rpc-core` | RpcChannelState pending 配对、MessageRpcClient/Server 订阅对称性、PollingRpcClient/CancellableRpcClient 定时任务、RpcTaskMonitor 账本、RpcMessageSubscriptions |
| `nop-network/nop-rpc/nop-rpc-http` | HttpRpcService（无自有资源，委托 IHttpClient，核对通过） |
| `nop-network/nop-rpc/nop-rpc-model` | ProtoFileParser/ProtoFileGenerator（无文件 IO 泄漏，核对通过） |
| `nop-network/nop-rpc/nop-rpc-simple` | SimpleRpcClientFactory/SimpleRpcClient/SimpleRpcServer 通道生命周期 |

nop-socket（SocketClient/SocketServer）与 nop-netty 属批次 2 范围，本批次仅作为 SimpleRpc* 的下游资源引用核对 close 配对（`SimpleRpcClient.close → SocketClient.close` 存在），不在本批次立案。

### 批次 2 覆盖声明（C6 两批次收官）

批次 2 逐文件审读以下 15 个源码根（`src/main/java` 全部叶子文件，`_gen/` 与 `_` 前缀生成物、`src/test` 按纪律排除；测试文件仅作行为佐证）：

| 模块叶 | 重点资源面 | 裁定摘要 |
|---|---|---|
| `nop-network/nop-netty`（31f/2,313） | ByteBuf 释放面、channel close、event loop shutdownGracefully、handler 资源（RpcMessageHandler futures/timer、PacketCodecHandler、HeartbeatHandler、ConnectionInfoHandler/MessageCounterHandler、NettyChannelGroupHandler、TestFragmentChannelHandler）、NettySslEngineFactory keystore 流 | **C6-017（bind 失败泄漏事件循环组，P1）**、C6-022（stop×重连竞态，P2）；handler 释放配对通过 |
| `nop-network/nop-socket`（11f/1,203） | SocketClient socket/流关闭配对、heartbeatFuture 取消、SocketServer accept 循环与 per-connection 清理 | **C6-027（onChannelOpen 异常路径 socket 滞留，P2）**；connect 失败清理（L121-126）、processCommand finally 对称清理、broadcast/processCommand `synchronized(os)` 帧交错防护通过；CL-028/CL-030 |
| `nop-network/nop-vertx/nop-vertx-commons`（3f/89） | 全局 Vertx 实例、VertxHelper | 通过（register-only 全局实例，设计意图） |
| `nop-network/nop-vertx/nop-vertx-mqtt-server`（13f/1,054） | Vertx 关闭、MQTT 会话表、endpoint handler 注册、publish listener 配对 | **C6-020（会话接管订阅整组误删，P1）**、C6-023（listen 失败吞掉，P2）、C6-026（dispatcher 配对破损，P2）；MqttConnection complete() 关闭路径、MqttMessageBuilder payload 拷贝通过 |
| `nop-network/nop-codec`（6f/294） | GZip/Deflate ByteBuf 释放、AbstractByteBufCodec、IPacketCodec | 通过（**经 netty-buffer 4.1.128 字节码核实**：单参 `ByteBufInputStream` 构造 releaseOnClose=false、`ByteBufOutputStream.close` 不释放 buffer——encodeBuf/decodeBuf 成功/异常路径的 `ReferenceCountUtil.release` 配对正确）；`ByteBufHelper.writeFully` 无生产调用方 |
| `nop-integration/nop-integration-feishu`（22f/2,045） | WS 连接关闭、heartbeatFuture 取消、重连后旧资源、scheduler 释放 | **C6-021（stop×在途重连孤儿 WS，P1）**、C6-024（send/close 无超时阻塞，P2）、C6-025（HttpClient 停止期不关闭，P2）；heartbeatFuture 生命周期（connect 后常驻、stop 取消）通过 |
| `nop-integration/nop-integration-api`（24f/1,399） | IFileServiceClient AutoCloseable 契约、credential/channel 纯数据类 | 通过（无自有资源面） |
| `nop-integration/nop-integration-sftp`（4f/554） | jsch Session/ChannelSftp 断开配对、异常路径 | **C6-018（构造期部分连接泄漏，P1）**；uploadResource/close() finally 配对通过 |
| `nop-integration/nop-integration-oss`（3f/442） | S3Object 流关闭、AmazonS3 生命周期 | 通过（downloadToStream finally 关闭 S3Object；AmazonS3 由 factory `@PreDestroy shutdown` 持有，client 空 close 正确；CL-032 单页截断） |
| `nop-integration/nop-integration-email-java`（3f/592） | Transport connect/close 配对 | 通过（withTransport finally 关闭 + 异常重抛） |
| `nop-integration/nop-integration-email-tencent`（3f/592 中 1f） | SesClient 生命周期 | **C6-019（SesClient 每发必建从不 shutdown，P1）**、CL-029（发送失败吞异常） |
| `nop-integration/nop-integration-file-local`（3f/320） | FileServiceResource 延迟关闭包装、factory==client 自引用 | 通过（FilterInputStream close→client.close 配对；工厂自身 close 空实现正确） |
| `nop-integration/nop-integration-sms-tencent`（2f/368） | SmsSingleSender 生命周期 | 通过（qcloudsms 无 close API、无连接池，每次请求 URLConnection 由 GC 回收——与 C6-019 的对照见该条失效面枚举） |
| `nop-integration/nop-integration-sms-yunpian`（2f/368 中 1f） | YunpianClient close 配对 | 通过（sendMessage/sendMultiMessage 均 finally close） |
| `nop-integration/nop-integration-zxing`（1f/61） | 纯内存编解码 | 通过（ByteArrayOutputStream 无需关闭） |

**收官说明**：批次 1（C6-001…016）覆盖 nop-http 6 叶 + nop-rpc 5 叶；批次 2（C6-017…027）覆盖上述 15 叶。C6 维度对该 26 个源码根的逐文件审读到此收官；C6 全维度累计 27 条发现（P0×1 / P1×10 / P2×13 / P3×3，其中批次 2：P1×5 / P2×6）+ 集外线索 CL-021…032 共 12 条。

#### 批次 2 资源获取点配对表（C6 执行步骤 1–6 汇总）

| 获取点 | 释放点 | 成功路径 | 失败/异常路径 | 取消/停机路径 | 裁定 |
|---|---|---|---|---|---|
| NettyTcpServer boss/worker group（doStart L135-141） | doStop shutdownGracefully | ✅ | **✗ bind 失败不关闭** | ✅ | **C6-017（P1）** |
| NettyTcpServer globalTrafficShapingHandler | doStop release() | ✅ | ✗（随 bind 失败泄漏） | ✅ | 随 C6-017 |
| NettyTcpClient workerGroup + channel | doStop safeClose + shutdownGracefully | ✅ | ✅（connect 失败由 scheduleReconnect 管控） | **stop×在途重连竞态** | **C6-022（P2）** |
| RpcMessageHandler futures/timer | channelInactive cancel+clear、checkTimeout 超时清理 | ✅ | ✅ | ✅（cancel(false) 全清理） | 通过（单 event loop 串行 + channel==null 守卫） |
| PacketCodecHandler 编解码失败计数 | decodeErrorCount/encodeErrorCount 达阈值 rethrow → CloseOnErrorHandler 关闭 | ✅ | ✅ | — | 通过 |
| NettySslEngineFactory keystore/truststore 流 | finally `IoHelper.safeCloseObject` / TWR | ✅ | ✅ | — | 通过 |
| SocketClient socket/is/os/heartbeatFuture | cleanup()（close→closed+cleanup） | ✅ | ✅（connect 失败 L121-126 关半开 socket） | ✅ | onChannelOpen 回调异常**✗**：**C6-027（P2）** |
| SocketServer ServerSocket + connections 表 | doStop closeAll + safeClose | ✅ | ✅（bind 失败 L131 关闭） | ✅（accept 异常 stopped→break；dispatch 失败 removeSocket） | 通过 |
| MqttSessionManager sessions/subscriptions | removeConnection（onCloseCallback）+ addConnection 接管 | ✅ | — | ✅ | **接管/移除×subscribe 交错误删：C6-020（P1）** |
| VertxMqttServer mqttServer | doStop mqttServer.close() | ✅（异步不等待） | **listen 失败无观察者** | ✅ | **C6-023（P2）** |
| MqttServerMessageService dispatcher（publishListeners） | cancel→removePublishListener | **并发首发重复 add** | — | **remove 分支不可达（空列表滞留）** | **C6-026（P2）** |
| FeishuClient WS transport + heartbeatFuture + scheduler | stop()：cancel+transport.close+shutdownNow | ✅ | — | **stop×在途 attemptReconnect 竞态** | **C6-021（P1）** |
| JdkStreamTransport webSocket 字段 | close()（仅 stop 调用）；connect() **覆盖不关闭旧连接** | ⚠️ | — | send/close 无超时阻塞 | **C6-024（P2）** |
| FeishuClient 两个 JDK HttpClient（WS transport + httpApi） | 无（无 close 链） | — | — | stop() 不触达 | **C6-025（P2）** |
| SftpClient jsch Session/ChannelSftp | close()：channel.exit + finally session.disconnect | ✅（调用方 close 时） | **构造期 channel.connect 失败：session 已连接无人回收** | — | **C6-018（P1）** |
| OssFileServiceClient S3Object 流 | downloadToStream finally close / getInputStream 交调用方 | ✅ | ✅ | — | 通过 |
| SesClient（TencentEmailSender，每发送新建） | 无（从不 shutdown） | **✗** | — | — | **C6-019（P1）** |
| JavaEmailSender Transport | withTransport finally transport.close | ✅ | ✅（重抛 + 关闭） | — | 通过 |
| YunpianSmsSender YunpianClient | finally client.close | ✅ | ✅ | — | 通过 |
| FileServiceResource 延迟关闭 client | FilterInputStream.close→IoHelper.safeClose(client)；失败路径 finally | ✅ | ✅ | — | 通过 |
| GZip/Deflate encodeBuf/decodeBuf 输出 buffer | 异常 ReferenceCountUtil.release；成功交调用方 | ✅ | ✅（zip 流 finally 关闭释放 native 内存） | — | 通过（字节码核实 input buffer releaseOnClose=false） |

**降档口径（批次 2 重申）**：泄漏与残留形态一律不适用启动期单线程降档——C6-017/018/019/021 均按运行期资源生命周期判级；竞态类发现（C6-020/021/022/026）均给出可具体化交错序列；数量结论为人工复现，基线 C6a/C6b 节（L1448-1784）仅用于圈定获取点。

### Protected Area 声明

本批次仅做只读审计，未修改任何代码或生成文件。`nop-network/**` 不在 AGENTS.md Protected Area 表内；审计产物仅写入本文件。

### 口径纪律声明

- 数量结论均为人工复现（逐文件审读 + 定向 grep 兄弟入口），非基线文本摘抄；基线 `_tmp/cra-baseline-nop-network-integration.txt` 的 C6a（L1448–1685）/C6b（L1686–1784）节用于圈定获取点与 close 层级重点。
- **降档口径**：泄漏与残留形态一律不适用启动期单线程降档——本批全部发现按运行期资源生命周期判级；并发类发现按可具体化交错判级，写不出具体时序的已降 P2 并标注。
- 现状标注（新发现/已知未修复）按盲审纪律留待复核阶段。
- 参照 owner 级设计契约 `ai-dev/design/nop-network/file-transfer-design.md`（FileDownloadSink javadoc 指向的协议契约，design/ 不在禁读清单）区分「已声明边界」与缺陷：416 删除 .part 后**整体重传**（不带 Range）是设计明文（§3.3）；非文件输出直写是设计明文；**file 目标 + resume=false 直写最终路径**未声明；成功路径的流关闭语义无任何声明。

### 资源获取点配对表（C6 执行步骤 1–6 汇总）

| 获取点 | 释放点 | 成功路径 | 失败/异常路径 | 取消路径 | 裁定 |
|---|---|---|---|---|---|
| Apache 连接池（`createConnectionManager`） | `client.close(GRACEFUL)`（@PreDestroy stop） | ✅ | ✅（HC5 内部） | ✅（evictIdleConnections 已配） | 通过（maxConn 配置面见 CL-023） |
| HC5 响应实体 consumer | `AbstractStreamResponseConsumer.releaseResources`（success/failed/cancel 均调，`getAndSet(null)` 幂等） | ✅ | ✅ | ✅ | 通过 |
| `ProgressFileEntityProducer` FileChannel | `releaseResources()`（HC5 对 producer 亦保证调用） | ✅ | ✅（`failed()` 空实现但 releaseResources 兜底） | ✅ | 通过 |
| FileDownloadSink `out`（.part FileOutputStream / 直连输出） | `close()` 唯一释放点 | **Apache ✅（releaseResources）/ JDK ✗ / OkHttp ✗** | ✅（catch → sink.close） | JDK: cancel 在读循环中无效（CL-026） | **C6-001（P0）** |
| `.part` 临时文件 | 校验失败/416 删除、成功 move | ✅（Apache/JDK/OkHttp 均删/move） | 保留（设计意图：续传基底） | 保留（设计意图） | 通过 |
| 下载响应体流（JDK ofInputStream / OkHttp source） | TWR / `try(Response)` | ✅ | ✅ | **416 重试分支与 SSE cancel-at-entry 遗弃** | **C6-002（P1）** |
| BASE64 上传输入流（`DefaultHttpInputFile.getInputStream`） | 无（`IoHelper.readBytes` 不关闭） | **✗ 三实现同漏** | — | — | **C6-005（P1）** |
| SSE 订阅（AbstractServerEventSubscription.future） | `cleanup → future.cancel(true)` | ✅（onComplete/failed） | ✅ | ✅（幂等，isCancelled 守卫） | 结构通过；承载线程阻塞面 **C6-004（P1）** |
| oauth token 缓存条目（authTokens） | 惰性替换，无显式清理 | ⚠️（有界于 provider 数） | 刷新失败条目保留（可接受） | — | **C6-008（P2）** |
| RpcChannelState `waitFutures` | whenComplete remove + timer 超时 remove + onChannelClose clear | ✅ | ✅（SimpleRpcClient send 失败 completeExceptionally 触发 remove） | ✅（MessageRpcClient onFailure） | 通过（maxWaitingRequests=10000 有界 fail-fast） |
| MessageRpcClient/Server 订阅（doStart/doStop） | `subscription.cancel()` | ✅ | — | ✅ | 通过（RpcMessageSubscriptions.putIfAbsent/cancel-remove 配对） |
| RpcTaskMonitor `activeTasks` | 仅 statusMethod 非空且 storage 判 STOP 时 remove | ⚠️ | — | — | **C6-011（P2）** |
| PollingRpcClient 定时轮询任务 | 仅 task.future 完成 / cancelToken | ⚠️（无总时长上限） | errorCount 上限兜底 | 需调用方传入 cancelToken | **C6-012（P2）** |
| SimpleRpcClientFactory timer/executor | destroy() | ✅ | — | — | 创建的 SocketClient 无回收：**C6-010（P2）** |
| ApiContextInterceptor ContextBinder（ThreadLocal attach） | `binder.close()`（BaseContext.close 清 ThreadLocal 有 `context==this` 守卫） | async ✅ / sync ✅ | **async 同步抛出路径 ✗**（sync 有 finally，async 无） | — | **C6-009（P2）** |
| ContextHttpServerFilter ctx | whenComplete + 同步抛 catch 双路径 | ✅ | ✅（L53–56 显式关闭） | — | 通过 |

## 发现

### [C6-001] JDK/OkHttp 下载成功路径不关闭 FileDownloadSink 输出流——每次成功下载泄漏一个文件句柄

- **文件**: `nop-network/nop-http/nop-http-client-jdk/src/main/java/io/nop/http/client/jdk/JdkHttpClient.java:407-415`；`nop-network/nop-http/nop-http-client-okhttp/src/main/java/io/nop/http/client/okhttp/OkHttpClientImpl.java:165-227`；根因载体 `nop-network/nop-http/nop-http-api/src/main/java/io/nop/http/api/utils/FileDownloadSink.java:120-129,177-185`
- **证据片段**:
  ```java
  // JdkHttpClient.doDownloadAsync —— sink.close() 只在 catch 分支
  executor().execute(() -> {
      try (InputStream in = response.body()) {
          promise.complete(processDownloadResponse(...)); // 成功：begin/write/finish/verifyAndRename
      } catch (Exception e) {
          sink.close();                                    // 仅异常路径关闭
          promise.completeExceptionally(wrapError0(e));
      }
  });
  // OkHttpClientImpl.onResponse 同构：try (Response resp) 只关 HTTP 体，sink.close() 仅在 catch(L224)
  ```
  `FileDownloadSink.begin()` 中 `out = new FileOutputStream(partFile, true)`（L120/122，resume=true 为 `DownloadOptions` 默认值）；`verifyAndRename()`（L162-175）成功时 `Files.move` 但从不调用 `out.close()`；`close()`（L177-185）是 `out` 的唯一释放点。JDK/OkHttp 两实现的成功路径均无人调用它。
- **严重程度**: P0（主路径资源泄漏——`resume` 默认 true，正常成功下载即泄漏）
- **并发场景**: 无需并发；单线程顺序下载即可累积。并发下载加速 fd 耗尽。
- **触发时序**: T1 调用 `downloadAsync`（默认 options）→ 200 响应 → `sink.begin` 打开 `.part` 的 FileOutputStream（被 BufferedOutputStream/DigestOutputStream 双层包裹）→ 读循环写完 → `sink.finish()`（仅 flush）→ `verifyAndRename` move 成功 → `promise.complete` → `out` 及其底层 fd 从未被 close，仅能依赖 FileDescriptor Cleaner 在 GC 后回收。每次成功下载净增一个 open fd；泄漏的 fd 还锚定已改名后的目标 inode，目标文件此后被 REPLACE_EXISTING 覆盖时旧 inode 空间延迟释放。Windows 上 `Files.move` 因句柄占用直接抛 AccessDeniedException——默认配置下下载功能在 Windows 不可用。
- **现状**: 成功路径零关闭；与设计契约（file-transfer-design.md）无对应声明，非已声明边界。
- **风险**: 长运行服务（模型文件/安装包定时下载）按下载次数线性泄漏 fd，`ulimit -n` 耗尽后全进程无法打开文件/连接；磁盘空间随旧 inode 滞留增长。
- **建议**: JDK/OkHttp 成功路径在 `verifyAndRename` 后、`promise.complete` 前调用 `sink.close()`（或改为 finally 关闭 + close 幂等化：`close()` 内 `out` 置 null）；更优做法是把关闭收敛进 `FileDownloadSink.verifyAndRename`/`finish` 终态，使三实现无需各自记得关闭。
- **失效面枚举（F3）**: ① `ApacheHttpClient`（经 `DownloadResponseConsumer.EntityConsumer.releaseResources → sink.close`，HC5 对 success/failed/cancel 均保证 releaseResources，且 `AbstractStreamResponseConsumer.releaseResources` 用 `getAndSet(null)` 幂等）——已闭合；② `JdkHttpClient.doDownloadAsync`——**泄漏**；③ `OkHttpClientImpl.doDownloadAsync`——**泄漏**。三实现共用 sink，修复须同时覆盖 ②③。
- **信心水平**: 确定
- **误报排除**: 非平台基础设施（nop-kernel）自身问题；`BufferedOutputStream` 无 finalizer，仅 FileDescriptor Cleaner 兜底，不构成确定性释放；非「有界声明缓存」豁免形态。
- **复核状态**: 未复核
- 复核状态: 已保留(P0)｜新发现——四子项复核全部通过（成功路径从不 close / resume 默认 true / Windows move 句柄占用 / Apache releaseResources 兜底，详见维度复核结论·C6-001 子项复核）；前轮 2026-09-22 3.9 曾将 FileDownloadSink 主路径列为确认关闭子域，本轮回码证伪

### [C6-002] JDK 下载 416 重试分支与 SSE cancel-at-entry 遗弃响应体流——连接与 socket 不归还

- **文件**: `nop-network/nop-http/nop-http-client-jdk/src/main/java/io/nop/http/client/jdk/JdkHttpClient.java:384-405`；`nop-network/nop-http/nop-http-client-jdk/src/main/java/io/nop/http/client/jdk/ServerEventPublisher.java:119-121`
- **证据片段**:
  ```java
  // JdkHttpClient.doDownloadAsync —— 416 分支在进入 TWR 之前 return
  if (response.statusCode() == 416 && offset > 0 && !retriedFromScratch) {
      ...
      doDownloadAsync(copyRequest(request), targetFile, options, cancelToken, true)
              .whenComplete(...);
      return;                       // response.body() InputStream 从未 close/消费
  }
  // jdk ServerEventPublisher.processResponse —— 取消竞态窗口内入口即弃
  executor().execute(() -> {
      if (isCancelled())
          return;                   // in/reader 未进入 try(reader)，从不关闭
  ```
- **严重程度**: P1（错误/取消路径资源泄漏）
- **并发场景**: 取消令牌线程与 JDK HttpClient 完成线程（whenComplete 回调）竞态；下载调用方与嵌套重试共用同一连接池。
- **触发时序**: （416）T1 resume 下载，本地 `.part` 长度大于远端资源（远端已替换为更小版本）→ 服务端返回 416 → `whenComplete` 进入 416 分支删除 .part 并发起重试 → `return` 跳过 `try (InputStream in = response.body())` → 416 响应体流未关闭未消费。JDK HttpClient 语义下该连接永久停留于「响应体未消费」状态，不归还池、不关闭 socket，只能等服务端单侧超时断开。（SSE）T1 `cancelToken.cancel()` 恰在响应头已到达、executor 任务尚未启动之间 → 任务入口 `isCancelled()` 为真 → 直接 return → `response.body()` 遗弃，同一后果。
- **现状**: 两处早退路径均无流关闭；OkHttp 同族分支由 `try (Response resp)` 覆盖（416 分支在 TWR 内），Apache 由 HC5 `releaseResources` 契约覆盖。
- **风险**: 每次触发净漏一条连接+socket；断点续传场景（远端文件被替换）可稳定复现，长运行累积至连接耗尽/句柄耗尽。
- **建议**: 416 分支与 SSE cancel 分支在 return 前 `IoHelper.safeClose(response.body())`；或将响应体处理统一收拢到单一 TWR 块，禁止分支绕过。
- **失效面枚举（F3）**: ① `JdkHttpClient` 416 分支——**泄漏**；② `jdk ServerEventPublisher` cancel-at-entry——**泄漏**；③ `jdk ServerEventPublisher` parse 中途取消（L128-129 位于 TWR 内）——已闭合；④ `OkHttpClientImpl` 416 分支（TWR 覆盖）——已闭合；⑤ `ApacheHttpClient` 全部早退（HC5 releaseResources 兜底）——已闭合。
- **信心水平**: 确定
- **误报排除**: JDK `HttpResponse.BodyHandlers.ofInputStream` 的流由调用方负责关闭是平台明文契约，非原语误用误报；不属「有界声明」豁免。
- **复核状态**: 未复核
- 复核状态: 已保留(P1)｜新发现——416 早退与 SSE cancel-at-entry 遗弃回码成立；同案：C3-002/C3-003（取消面）、C1 文件 CL-001（移交闭环并入本条②）

### [C6-003] JDK/OkHttp 416 整体重传携带陈旧 Range 头——恢复路径确定性失败，违反设计契约

- **文件**: `nop-network/nop-http/nop-http-client-jdk/src/main/java/io/nop/http/client/jdk/JdkHttpClient.java:374-376,390-404`；`nop-network/nop-http/nop-http-client-okhttp/src/main/java/io/nop/http/client/okhttp/OkHttpClientImpl.java:149-151,170-184`；正确对照 `nop-network/nop-http/nop-http-client-apache/src/main/java/io/nop/http/apache/ApacheHttpClient.java:256-262`
- **证据片段**:
  ```java
  // JdkHttpClient.doDownloadAsync —— 只在 offset>0 时设置，无 else 移除
  long offset = retriedFromScratch ? 0 : sink.resolveOffset();
  if (offset > 0)
      request.header("range", "bytes=" + offset + "-");
  //（Apache 正确实现：else request.removeHeader("range"); L261-262）
  ```
  `HttpRequest.removeHeader` 存在（HttpRequest.java:204）而 JDK/OkHttp 均未调用；`DISALLOWED_HEADERS`（HttpApiConstants.java:56-57）仅含 host/content-length/connection/expect/upgrade，`range` 会被原样透传。
- **严重程度**: P1（错误恢复路径「半个成功」语义断裂，F2）
- **并发场景**: 无需并发；416 触发的嵌套重试路径。
- **触发时序**: T1 首次请求带 `Range: bytes=N-`（offset=N>0）→ 服务端返回 416 → 删除 `.part` → T2 `doDownloadAsync(copyRequest(request), ..., retriedFromScratch=true)`：`copyRequest` 连同 headers 复制了陈旧 `Range: bytes=N-`，`offset=0` 分支既不设置也不移除 → 重传请求仍是 Range 请求。服务端再次 416（此时 `retriedFromScratch=true` 不再重试）→ `processDownloadResponse` 判非 2xx 返回错误响应；若服务端改为返回 206，`FileDownloadSink.begin` 校验 `range[0] != startOffset(0)` 抛「invalid content-range for resume」→ `promise.completeExceptionally`。两条分支均无法完成设计要求的「整体重传」。
- **现状**: 设计契约（file-transfer-design.md §3.3）明文「status == 416: 删除 partFile 后整体重传」，且 §3.6 声明恢复成功仅记 INFO（`download-range-not-satisfiable-recovered`，该日志三实现均未实现）；Apache 实现正确，JDK/OkHttp 偏离。
- **风险**: 「.part 比远端新」这一续传协议核心自愈场景在 JDK/OkHttp 客户端上必然失败，调用方收到下载异常而非自动恢复；跨实现行为等价承诺（设计 §一.5）被破坏。
- **建议**: 与 Apache 对齐：`doDownloadAsync` 中 `if (offset > 0) request.header("range", ...) else request.removeHeader("range")`；或重传时改用不含 range 的全新请求对象。
- **失效面枚举（F3）**: ① `ApacheHttpClient.doDownloadAsync`——正确（else removeHeader）；② `JdkHttpClient.doDownloadAsync`——**缺陷**；③ `OkHttpClientImpl.doDownloadAsync`——**缺陷**。三实现共用 `copyRequest`（均复制 headers）。
- **信心水平**: 确定
- **误报排除**: 设计文档明文规定整体重传语义，非「已声明边界」；非压测代码。
- **复核状态**: 未复核
- 复核状态: 已降级(P2)｜已知未修复——同站点前案 2026-09-22 3.9（P2 已复核保留，代码未动）；降级理由：后果为可观察下载失败而非静默损坏/泄漏，与前案判级对齐

### [C6-004] SSE 反压以无限期阻塞等待实现且无超时——阻塞承载线程（Apache 为 I/O reactor 线程）

- **文件**: `nop-network/nop-http/nop-http-api/src/main/java/io/nop/http/api/client/AbstractServerEventSubscription.java:117-127,143-161`；`nop-network/nop-http/nop-http-client-apache/src/main/java/io/nop/http/apache/ServerEventPublisher.java:107-109`；`nop-network/nop-http/nop-http-client-jdk/src/main/java/io/nop/http/client/jdk/ServerEventPublisher.java:119-145`
- **证据片段**:
  ```java
  // AbstractServerEventSubscription —— demand 等待无超时；onNext 前同步阻塞
  protected synchronized void waitForDemand() {
      while (demand <= 0 && !isCancelled) { wait(); }   // 仅 cancel 可唤醒
      demand--;
  }
  private void dispatchEvent() {
      if (data != null) { if (!isCancelled) { waitForDemand(); subscriber.onNext(...); } ... }
  // apache ServerEventSubscription.ServerEventConsumer.onLine → processLine → dispatchEvent
  //   —— 由 HC5 I/O dispatch 线程在 data() 回调中同步调用
  ```
- **严重程度**: P1（罕见但可具体化的资源滞留；F5「挂死生产者/消费者对健康检测不可见」）
- **并发场景**: HC5 I/O reactor 线程（Apache）/ JDK HttpClient 内部 executor 线程（JDK）与下游订阅者线程通过 `wait/notifyAll` 交互；reactor 线程为同 reactor 上所有连接共享。
- **触发时序**: T1 订阅者以有界 demand 消费 SSE（如 `request(1)` 逐条背压）并在业务侧停滞（不 request 也不 cancel）→ T2 上游事件到达，I/O 线程执行 `dispatchEvent → waitForDemand` 进入 `wait()` → 该 I/O reactor 线程被挂起，同一 reactor 线程承载的其他 HTTP 连接全部停摆，直至订阅者 request 或 cancel。无任何超时兜底。JDK 侧：parse 循环 `reader.readLine()` 在 body 中途停滞时不可中断（`cleanup → future.cancel(true)` 对已完成的 sendAsync future 是 no-op，不会中断执行 parse 的 executor 线程），`isCancelled` 只在行间检查——服务器停滞时 executor 线程永久滞留。
- **现状**: 反压用阻塞承载线程实现且两处等待点（demand 等待、readLine）均无超时；未被任何文档声明为边界。
- **风险**: 单个停滞订阅者即可拖死共享 I/O 线程（多路复用全部连接），或累积滞留 executor 线程；长运行下线程池耗尽、全客户端假死。
- **建议**: demand 等待加入带超时的 `wait(timeout)` 并在超时后走 `onError`/cleanup；Apache 侧改为投递到独立消费队列（如平台 `IBlockingQueue` + `QueueOverflowPolicy`）而非在 reactor 线程内 wait；JDK 侧 parse 循环改用可中断通道或在 cleanup 时关闭底层 `in`（顺带消除 C6-002②）。
- **失效面枚举（F3）**: ① `AbstractServerEventSubscription.waitForDemand`（共享基类）——无超时；② Apache `ServerEventPublisher`（onLine 直调 processLine 于 reactor 线程）——**最重**；③ jdk `ServerEventPublisher.parseEvents`（readLine 不可中断 + cancel 无效）——**滞留**；④ OkHttp 未实现 `fetchServerEventFlow`（无此面）。IHttpResponse 轮询/下载路径不在本条失效面内（另见 C6-006/CL-026）。
- **信心水平**: 确定（结构）；触发依赖订阅者停滞（有明确触发链）
- **误报排除**: 非 Reactive Streams 合规实现（onNext 必须非阻塞），阻塞 wait 是实现选择而非原语使用；不属「无状态单例」误报形态。
- **复核状态**: 未复核
- 复核状态: 已保留(P1)｜新发现——waitForDemand 无超时 + Apache reactor 内联 + JDK readLine 不可中断回码成立；本条失效面③即 WI 3.1 C7-001 消费面（P1）的 SSE readLine 本体落点，回写闭环；同案：C3-003

### [C6-005] BASE64_FORM 上传输入流泄漏——三实现共漏（IoHelper.readBytes 不关闭）

- **文件**: `nop-network/nop-http/nop-http-client-apache/src/main/java/io/nop/http/apache/ApacheHttpClient.java:487`；`nop-network/nop-http/nop-http-client-jdk/src/main/java/io/nop/http/client/jdk/JdkHttpClient.java:554`；`nop-network/nop-http/nop-http-client-okhttp/src/main/java/io/nop/http/client/okhttp/OkHttpClientImpl.java:289-290`；根因载体 `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/IoHelper.java:246-253` 与 `nop-network/nop-http/nop-http-api/src/main/java/io/nop/http/api/support/DefaultHttpInputFile.java:35-41`
- **证据片段**:
  ```java
  // 三处同型调用（Apache L487 / Jdk L554 / OkHttp L289-290）
  base64 = java.util.Base64.getEncoder().encodeToString(IoHelper.readBytes(inputFile.getInputStream()));
  // IoHelper.readBytes(InputStream) —— 只读不关
  public static byte[] readBytes(InputStream is) throws IOException {
      if (PlatformEnv.javaVersion() >= 9) { return is.readAllBytes(); }   // readAllBytes 不关闭
  // DefaultHttpInputFile.getInputStream —— 每次调用新建 FileInputStream
  ```
- **严重程度**: P1（opt-in 模式的正常路径 fd 泄漏；F8 资源清理不对称）
- **并发场景**: 无需并发；每次 BASE64_FORM 上传确定性泄漏。并发上传加速耗尽。
- **触发时序**: T1 调用 `uploadAsync` 且 `options.getMode() == UploadMode.BASE64_FORM` → `inputFile.getInputStream()` 新建 `FileInputStream` → `IoHelper.readBytes` 读全量后返回、流未关闭 → fd 遗留，仅 FileDescriptor Cleaner 在 GC 后可回收。上传大文件高频场景下 GC 滞后于 fd 增速，`Too many open files` 先于 GC 到来。
- **现状**: 设计文档声明了 BASE64_FORM 的内存代价（1.33 倍，「仅适用中小文件」）但未声明流关闭责任；`HttpInputFileBodyPublisher`（死代码，见 C6-016）反而在完成/异常/取消三路径都 close，形成同族内正确实现被弃用、错误实现上线的反差。
- **风险**: 以 BASE64_FORM 对接网关的服务按上传次数泄漏 fd；`ulimit` 耗尽波及全进程。
- **建议**: 三处调用改为 try-with-resources 包裹 `inputFile.getInputStream()`；同时给 `IoHelper.readBytes(InputStream)` 补 javadoc 声明「不关闭流」契约，防止下一个调用方重蹈。
- **失效面枚举（F3）**: ① `ApacheHttpClient.uploadByBase64Form:487`——**泄漏**；② `JdkHttpClient.uploadByBase64Form:554`——**泄漏**；③ `OkHttpClientImpl.uploadAsync BASE64_FORM 分支:289`——**泄漏**；④ `ProgressFileEntityProducer`（BINARY 模式，FileChannel 由 releaseResources 关闭）——已闭合；⑤ jdk BINARY（`BodyPublishers.ofFile`，JDK 内部管理）——已闭合；⑥ OkHttp BINARY（`RequestBody.create(File)`，OkHttp 内部管理）——已闭合。
- **信心水平**: 确定
- **误报排除**: `readAllBytes` 的不关闭语义是 JDK 明文契约；本条报的是调用方未配对关闭，非「原语使用本身」。
- **复核状态**: 未复核
- 复核状态: 已保留(P1)｜新发现——三实现 readBytes 不关流入栈回码成立；同案：C3 文件 CL-012（内存面互补，fd 泄漏面本条独有）

### [C6-006] sidecar 探测在下载完成回调内同步阻塞 get(30s)×2——Apache 阻塞 I/O reactor 线程；超时后内层 future 不取消

- **文件**: `nop-network/nop-http/nop-http-client-apache/src/main/java/io/nop/http/apache/ApacheHttpClient.java:266-274,300-383`；`nop-network/nop-http/nop-http-client-jdk/src/main/java/io/nop/http/client/jdk/JdkHttpClient.java:454-458,475-487`；`nop-network/nop-http/nop-http-client-okhttp/src/main/java/io/nop/http/client/okhttp/OkHttpClientImpl.java:212-215,239-252`
- **证据片段**:
  ```java
  // ApacheHttpClient.fetchSidecarChecksum —— completed() 回调链上同步阻塞
  IHttpResponse res = fetchAsync(HttpRequest.get(FileTransferHelper.sidecarUrl(url, algorithm)), cancelToken)
          .toCompletableFuture().get(30, TimeUnit.SECONDS);   // ×2 个算法
  ...
  } catch (Exception e) {
      LOG.debug("nop.http.download.sidecar-check-fail:url={}", url, e);  // get 超时后内层 fetch 未 cancel
  }
  // 调用链：FutureCallback.completed → handleDownloadResult → fetchSidecarChecksum（HC5 I/O 线程上执行）
  ```
- **严重程度**: P1（错误路径资源滞留 + 共享承载线程阻塞；`DownloadOptions.fetchSidecarChecksum` 默认 true，所有无校验头的下载都走此路径）
- **并发场景**: Apache：HC5 I/O dispatch 线程内阻塞（该线程服务多个连接）；JDK：HttpClient 内部 executor 线程；OkHttp：Dispatcher 线程。嵌套 `fetchAsync` 与宿主下载共享同一客户端连接池。
- **触发时序**: T1 下载完成回调在 I/O 线程执行 `handleDownloadResult` → 无校验头且 `fetchSidecarChecksum=true` → `fetchSidecarChecksum` 发起 sidecar GET 并 `get(30s)` 阻塞 → T2 期间该 I/O 线程不处理任何其他连接事件（Apache 形态），其余请求全部排队；若 sidecar 服务端挂起（接受连接不响应），阻塞持续 30s×2；`get` 超时后内层 future **未取消**，该在途请求继续占用池内连接直至自身 readTimeout（默认 30s）。
- **现状**: 三实现同型；「探测失败不阻断下载」的注释只覆盖异常分支，未覆盖「阻塞承载线程」与「迟到请求」两面。
- **风险**: 单个悬挂的 `{url}.sha256` 请求即可让 Apache 客户端吞吐雪崩（reactor 线程 60s 不可用）；并发下载时 JDK/OkHttp 侧额外占用 dispatcher/executor 线程与连接池槽位。
- **建议**: sidecar 探测移出回调线程（投递到平台 `IThreadPoolExecutor`，或改异步 `whenCompose` 链）；`get` 超时/failure 分支补 `future.cancel(false)`；为 sidecar GET 单独设短超时（如 3–5s）。
- **失效面枚举（F3）**: ① `ApacheHttpClient.fetchSidecarChecksum`（reactor 线程阻塞，最重）——**缺陷**；② `JdkHttpClient.fetchSidecarChecksum`（executor 线程阻塞）——**缺陷**；③ `OkHttpClientImpl.fetchSidecarChecksum`（dispatcher 线程阻塞）——**缺陷**。三实现 get 超时分支均不取消内层 future。
- **信心水平**: 确定（结构）；悬挂触发需服务端不响应（明确触发链）
- **误报排除**: 阻塞点位于事件回调线程属复合误用而非 `CompletableFuture.get` 原语本身；`fetchSidecarChecksum=true` 是默认配置，非低频管理路径。
- **复核状态**: 未复核
- 复核状态: 已保留(P1)｜新发现——三实现回调线程 get(30s)×2 + 超时不取消内层 future 回码成立；同案：C3-004、C1 文件 CL-002（移交闭环）

### [C6-007] JdkHttpClient.stop() 不关闭 java.net.http.HttpClient——selector 线程与连接池跨启停周期累积

- **文件**: `nop-network/nop-http/nop-http-client-jdk/src/main/java/io/nop/http/client/jdk/JdkHttpClient.java:94-116,154-160`；对照 `nop-network/nop-http/nop-http-client-okhttp/src/main/java/io/nop/http/client/okhttp/OkHttpClientImpl.java:92-99` 与 `ApacheHttpClient.java:93-97`
- **证据片段**:
  ```java
  @PreDestroy
  public void stop() {
      if (executor != null) {
          executor.destroy();      // 只销毁自建 executor
          executor = null;
      }
  }                                // 无 client.close() —— HttpClient 自 Java 21 起 AutoCloseable
  ```
- **严重程度**: P2（长运行/热更新启停周期资源滞留）
- **并发场景**: bean 生命周期线程（stop）与 JDK HttpClient 内部 selector 线程、连接池清洁线程并存。
- **触发时序**: T1 `stop()` 销毁自建 executor → HttpClient 本体（连接池、selector manager 线程、SSL 上下文）保持存活；T2 再次 `start()` 构造**新的** HttpClient → 旧实例无任何引用与关闭路径，仅能等 GC 清理（连接池中 keep-alive 连接依赖服务端超时单侧断开）。每个 start/stop 周期净增一套 selector 线程+连接池。
- **现状**: `stop()` 未覆盖 `client` 字段；`refreshConfig()` 亦为空实现（配置刷新不会触发重建，尚不构成额外泄漏面）。
- **风险**: 配置热更新/测试上下文反复启停时线程与 socket 缓慢累积；停机后残留连接延迟进程退出（非 daemon 语义变化视 JDK 版本）。
- **建议**: `stop()` 中按 JDK 版本调用 `client.close()`（Java 21+ AutoCloseable），或至少 `client.shutdownNow()` 语义等价物；`OkHttpClientImpl.stop()` 中被注释的 `dispatcher().executorService().shutdown()` 建议恢复为可配置的 drain 后关闭（现仅 evictAll + cache close，在途调用不等待，dispatcher 线程靠 60s keepalive 自灭）。
- **失效面枚举（F3）**: ① `ApacheHttpClient.stop()`——正确（`close(CloseMode.GRACEFUL)`，连接池随 client 关闭）；② `JdkHttpClient.stop()`——**缺陷**；③ `OkHttpClientImpl.stop()`——部分（evictAll+cache 关闭，dispatcher 不 shutdown、在途不 drain）。`DefaultHttpClientFactory` 无生命周期方法（依赖底层 bean 的 @PreDestroy）。
- **信心水平**: 确定（代码面）；后果程度依 JDK 版本 daemon 语义
- **误报排除**: HttpClient 的 selector 线程与池是真实进程级资源，非「无状态单例并发调用」误报形态；本项目 Java 25，close() 可用。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——stop 不关 HttpClient 回码成立（OkHttp dispatcher 注释态同证）

### [C6-008] oauth token 缓存刷新持全局监视器做同步 HTTP 往返——跨 provider 串行化；条目无配置移除清理

- **文件**: `nop-network/nop-http/nop-http-client-oauth/src/main/java/io/nop/http/client/oauth/enhancer/AddAccessTokenHttpClientEnhancer.java:33,110-132`
- **证据片段**:
  ```java
  protected String getAccessTokenFromProvider(IHttpClient client, String providerName,
                                              OauthProviderConfig providerConfig, long gap) {
      Oauth2TokenResponseBean token = authTokens.get(providerName);
      if (token != null && !token.isExpired(gap)) return token.getAccessToken();
      synchronized (authTokens) {                       // 全部 provider 共享一把锁
          ...
          Oauth2TokenResponseBean response = fetchToken(client, providerConfig);  // 锁内同步 HTTP
          authTokens.put(providerName, response);
  ```
- **严重程度**: P2（锁粒度过粗 + 过期条目生命周期缺口）
- **并发场景**: 多业务线程并发调用 `onFetchBegin`；token 过期瞬间一线程持 `authTokens` 监视器执行 token endpoint 的完整 HTTP 往返，其余线程（含其他 provider 的过期刷新）全部排队。
- **触发时序**: T1 provider-A 的 token 过期，线程-1 进入 `synchronized(authTokens)` 发起 fetchToken（token endpoint 慢，如 5s）→ T2 同期 provider-B 的 token 也过期，线程-2 到达 `synchronized(authTokens)` 被阻塞 5s → 线程-2 所属业务请求的超时预算被无关 provider 的刷新吃掉；token endpoint 故障时所有 provider 的令牌获取在单把锁上串行超时。
- **现状**: 缓存键为 provider 名（有界于配置），无 OOM 面；但 (a) 锁内做阻塞 IO，(b) provider 从配置移除后 `authTokens` 条目永不删除（惰性替换-only），生命周期无对账点。
- **风险**: token endpoint 抖动被锁放大为全客户端请求排队；动态 provider 配置下缓存条目与配置漂移（陈旧 token 仍可命中直至过期）。
- **建议**: 按 providerName 分锁（`ConcurrentHashMap<String,Object>` 监视器或 `computeIfAbsent` + per-key future 合并刷新，参考平台 `ICache`/`GlobalCacheRegistry`）；配置重载时对 `authTokens` 做 keySet 对账移除。
- **失效面枚举（F3）**: 本模块唯一缓存入口即 `getAccessTokenFromProvider`；`useContextAccessToken` 分支与 `processPropagateHeaders` 分支无缓存无锁，不受影响。
- **信心水平**: 确定（结构）；排队后果在多 provider + token endpoint 慢时必然发生
- **误报排除**: 问题不在 double-checked 模式本身（写法正确），在「持锁做阻塞 IO」的复合误用；非低频启动路径。
- **复核状态**: 未复核
- 复核状态: 已驳回·同案并入C1-002/C3-008(P2)｜新发现——锁内 IO 面与 C1-002/C3-008 完全重叠；独有「条目无对账移除」子面在启动期装配、无配置热移除路径前提下为 P3 防御性，随 C1-002 修复批处理

### [C6-009] ApiContextInterceptor.interceptAsync 无同步异常保护——proceedAsync 同步抛出时 context 永不关闭并滞留 ThreadLocal

- **文件**: `nop-network/nop-rpc/nop-rpc-api/src/main/java/io/nop/rpc/api/ApiContextInterceptor.java:22-47`；平台语义载体 `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/context/BaseContextProvider.java:45-53`、`BaseContext.java:315-325`
- **证据片段**:
  ```java
  public CompletionStage<ApiResponse<?>> interceptAsync(IRpcServiceInvocation inv) {
      ApiRequest<?> request = inv.getRequest();
      ContextBinder binder = new ContextBinder(request);   // newContext(true) attach 到当前线程
      return inv.proceedAsync().whenComplete((ret, err) -> binder.close());
      // proceedAsync 同步抛出 → whenComplete 未挂上 → close 无人调用
  }
  // 对照同文件 sync 版：try { return inv.proceed(); } finally { binder.close(); }  —— 有 finally
  ```
- **严重程度**: P2（F8 清理不对称 + F9 静默状态不一致）
- **并发场景**: 拦截器运行于 RPC 调用线程（如 HTTP worker/IoC 调度线程）；滞留的 open context 影响同线程后续请求。
- **触发时序**: T1 线程-X 执行 interceptAsync，`new ContextBinder(request)` 创建新 context 并 attach 到线程-X ThreadLocal → `inv.proceedAsync()` 在返回 CompletionStage 之前同步抛出（拦截器链前序/请求校验同步失败）→ `binder.close()` 未注册也未执行 → 线程-X 的 ThreadLocal 留有**未关闭**的 context（内含本次请求的 tenantId/trace/locale）。T2 线程-X 处理下一个无上下文请求：`ContextBinder()` 里 `ContextProvider.currentContext()` 返回该残留 context，`created=false` → `init` 跳过 → 请求携带**上一个请求**的租户与 trace 执行。`BaseContext.close` 的 `context == this` 守卫只保证不误删他人 context，不提供此路径的清理。
- **现状**: 同文件 sync 路径有 finally，async 路径无同步异常防护（同族不对称，F3 在同一文件内即成立）。
- **风险**: 跨请求租户/trace 串号（静默，无错误码）；context 滞留至线程复用覆盖或线程退出。
- **建议**: async 版对齐 sync 版：`try { CompletionStage<?> f = inv.proceedAsync(); f.whenComplete(...); return f; } catch (Throwable e) { binder.close(); throw e; }`。
- **失效面枚举（F3）**: ① `ApiContextInterceptor.interceptAsync`——**缺陷**；② `ApiContextInterceptor.intercept`——已闭合（finally）；③ `ContextHttpServerFilter.filterAsync`——已闭合（同步抛 catch L53-56 显式 close，whenComplete 兜底异步路径）；④ `ContextBinder` 自身 close（幂等：created 才 close，BaseContext.close 二次调用安全）——通过。
- **信心水平**: 很可能（同步抛出路径存在取决于调用链具体实现，但防护缺失是确定的）
- **误报排除**: 非平台 IContext 语义误读——`close()` 不清 ThreadLocal 的场景恰恰是本条要报的滞留来源；非「无状态 bean」豁免。
- **复核状态**: 未复核
- 复核状态: 已驳回·同案并入C3-006(P2)｜新发现——与 C3-006 同站点同根同修复；本条失效面③④（ContextHttpServerFilter/ContextBinder 幂等）为修复批连带核对项

### [C6-010] SimpleRpcClientFactory 创建的通道无回收路径：destroy 不关闭已建 SocketClient，destroy 后仍可 newInstance

- **文件**: `nop-network/nop-rpc/nop-rpc-simple/src/main/java/io/nop/rpc/simple/SimpleRpcClientFactory.java:112-146,156-182`
- **证据片段**:
  ```java
  public void destroy() {
      destroyed = true;
      if (ownExecutor && executor != null) { executor.destroy(); }
      if (ownTimer && timer != null) { timer.destroy(); }
  }                          // 无已创建 client 的登记与关闭
  public T newInstance() {
      init();                // destroyed 标志不检查
      ICommandClient client = newClient();
      ...
      client.connect();
      rpcService.executeRecv(executor);   // destroy 后销毁的 executor 上启动接收循环
  ```
- **严重程度**: P2（socket/线程孤儿 + 销毁后可用）
- **并发场景**: 生命周期线程 destroy 与业务线程 newInstance/调用并发；`destroyed` 为普通 boolean 无同步。
- **触发时序**: T1 业务侧 `newInstance()` 建立连接 A 并启动 `executeRecv` → T2 容器 `destroy()` 仅销毁 factory 自有的 timer/executor → 连接 A 的 `SocketClient` 无任何关闭调用（proxy 侧须显式 `proxy_destroy` 才触发 `SimpleRpcClient.close`，调用方不销毁代理即泄漏）；若 T3 在 destroy 后调用 `newInstance()`，`init()` 不再补建 executor（引用已 destroy 的实例），recv 循环跑在已销毁线程池上——任务拒绝或静默不调度。
- **现状**: 代理确有 `IRpcProxy.proxy_destroy → DestroyHelper.safeDestroy(rpcService) → client.close()` 的释放通道（RpcInvocationHandler L61-64），但 factory 层无登记、无兜底关闭、无 destroyed 检查；生命周期契约未声明「调用方必须销毁每个代理」。
- **风险**: 集成调试场景（XLang IDE 插件为声明用途）反复创建代理而只销毁 factory → socket 与接收任务累积。
- **建议**: factory 维护 `ConcurrentHashMap<SimpleRpcClient,?>` 登记创建实例，`destroy()` 逆序关闭全部存活 client；`newInstance()` 入口对 `destroyed` 做 fail-fast（抛 ERR 或重建 executor）。
- **失效面枚举（F3）**: ① `SimpleRpcClientFactory.newInstance` 通道——**无回收登记**；② `SimpleRpcServer.stop`（socketServer.stop + ownExecutor.destroy，顺序正确）——通过；③ proxy 侧 `proxy_destroy`——通过（依赖调用方自觉）。
- **信心水平**: 确定（结构）
- **误报排除**: 非平台池化设施（IPool）的使用，属自建生命周期管理；「destroy 后可用」非已声明边界。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——destroy 无登记无兜底、destroy 后 newInstance 可用回码成立；与 C1-001 同文件互补面

### [C6-011] RpcTaskMonitor.activeTasks：statusMethod 为空的任务进入不可移除状态——条件性只增不删账本（F4）

- **文件**: `nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/monitor/RpcTaskMonitor.java:69-76,90-127`
- **证据片段**:
  ```java
  for (RpcTask task : activeTasks.values()) {
      String statusMethod = task.getStatusMethod();
      if (!StringHelper.isEmpty(statusMethod)) {   // 空 statusMethod → 整个分支跳过
          ... service.callAsync(statusMethod, ...).whenComplete(... handleTaskStatus ...);
      }
  }
  void handleTaskStatus(...) { ... if (result == ProcessResult.STOP) removeTask(task.getTaskId()); }
  ```
- **严重程度**: P2（有界但依赖外部清理方的账本；F4 形态的条件实例）
- **并发场景**: `scheduleWithFixedDelay` 监视线程与 `addTask/removeTask` 外部调用线程并发于 `ConcurrentHashMap`（map 本身线程安全，问题在清理路径覆盖率）。
- **触发时序**: T1 `addTask` 注册一个 `statusMethod` 为空（仅可 cancel、无状态查询）的任务 → 监视循环每轮跳过它 → `handleTaskStatus` 永不执行 → `removeTask` 只能依赖模块外部显式调用；T2 无外部调用方（或调用方遗漏）→ 条目（持 request/rpcService 强引用）长驻；T3 重启后 `doStart → statusStorage.fetchActiveTasks` 将持久化任务**重新载入** activeTasks，滞留跨进程延续。
- **现状**: `addTask` 强制 `cancelMethod` 非空（L140）但允许 `statusMethod` 为空；`removeTask` 为 public，是否存在外部清理方超出本批次范围（nop-task 等），本条按「模块内无清理路径」立案并标注条件性。
- **风险**: 无 statusMethod 的长时任务在 monitor 内成为永久条目并跨重启复活；持有 service/request 引用阻止回收。
- **建议**: 对空 statusMethod 任务提供独立的清理判定（如 cancel 结果回写 storage 判 STOP，或文档强制「此类任务必须由创建方显式 removeTask」并在 addTask javadoc 声明契约）。
- **失效面枚举（F3）**: ① 监视循环移除路径（statusMethod 非空 + STOP）——已闭合；② 空 statusMethod 任务——**无模块内清理**；③ `updateTaskStatus` 推送路径 → handleTaskStatus → 同①判定——依赖外部推送。RpcChannelState.waitFutures（同模块另一账本）有超时+close 双清理——对照通过。
- **信心水平**: 很可能（模块内确无清理；外部清理方存在与否待复核）
- **误报排除**: 账本有 `maxWaitingRequests` 类硬上限者可豁免——activeTasks **无上限配置**，不落入「有界声明」豁免。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现（条件性）——复核补强：cancelTask 亦不移除，模块内移除仅剩 handleTaskStatus→STOP；全仓无生产消费方，嵌入方面维持 P2

### [C6-012] PollingRpcClient 轮询任务无总时长/次数上限——远端任务不完成且调用方放弃时永久轮询

- **文件**: `nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/composite/PollingRpcClient.java:54-99,101-148`
- **证据片段**:
  ```java
  private class PollTask implements Callable<Void> {
      ...
      public void schedule() {
          if (!future.isDone()) {
              timerFuture = timer.schedule(this, pollInterval, TimeUnit.MILLISECONDS);
          }
      }
      // call(): 轮询成功且 !isCompleted(res) → schedule() —— 无最大轮询次数/总时长上限
  ```
- **严重程度**: P2（长运行资源滞留；F4/CL-026 同族的定时任务版）
- **并发场景**: `IScheduledExecutor` timer 线程调度轮询；调用方线程等待 `task.future`；cancel 仅经 `cancelToken.appendOnCancel` 挂接。
- **触发时序**: T1 `callAsync` 触发 start 方法返回未完成态 → PollTask 开始按 `pollInterval` 轮询 statusMethod → T2 远端任务永远停在非完成态且轮询调用**成功返回**（errorCount 永不增长）→ 轮询无限继续。若调用方以自己的超时放弃等待（未传入 cancelToken 或未对返回 token 执行 cancel——构造器允许 `cancelToken == null`），无任何机制终止轮询：timer 任务与对远端的 RPC 流量永久持续。
- **现状**: 错误路径有 `maxErrorCount` 上限；成功但未完成路径无对应上限；cancel 依赖调用方纪律，无 owner 级声明兜底。
- **风险**: 每个被遗弃的未完成任务贡献一个永久 timer 任务 + 周期性 RPC；任务积压后 timer 队列与远端负载缓慢恶化。
- **建议**: 增加 `maxPollCount`/`maxPollDuration`（对齐 errorCount 风格），超限后 `future.completeExceptionally(RpcTimeoutException)`；或要求调用方必传 cancelToken 并在 javadoc 声明。
- **失效面枚举（F3）**: ① `PollingRpcClient.PollTask`——**无总量上限**；② `CancellableRpcClient`（同包组合客户端）——无自持定时资源，cancel 钩子在 future 完成后成 no-op（监听器随 token 生命周期回收）——通过；③ `RpcTaskMonitor` 定时监视（timerFuture 在 doStop cancel）——生命周期配对通过（账本问题另立案 C6-011）。
- **信心水平**: 确定（结构）
- **误报排除**: 轮询到完成态是设计目的，本条仅针对「调用方已放弃+远端不终结」的资源滞留面；非「有界声明」豁免（无上限声明）。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——成功未完成路径无总量上限、cancel 依赖调用方纪律回码成立

### [C6-013] file 目标 + resume=false 下载直写最终路径——失败/校验失败在目标位置残留截断文件（F2）

- **文件**: `nop-network/nop-http/nop-http-api/src/main/java/io/nop/http/api/utils/FileDownloadSink.java:67-69,110-128,162-175`（三实现共同依赖）
- **证据片段**:
  ```java
  public boolean isFileMode() {
      return targetFile.toFile() != null && options.isResume();   // resume=false → 非文件模式
  }
  ...
  } else {
      // 直连输出（无文件形态或未启用续传）：恒为整体写入
      startOffset = 0;
      out = targetFile.getOutputStream();      // 直接打开最终目标路径
  }
  // verifyAndRename: 校验失败只删 partFile（此时为 null）；直写路径无任何回滚/清理
  ```
- **严重程度**: P2（错误路径残留；触发需显式 `setResume(false)` 或非文件目标，故不入 P1）
- **并发场景**: 无需并发；单次失败下载即残留。
- **触发时序**: T1 调用方 `DownloadOptions.setResume(false)`（或 targetFile 非文件形态）下载 → `sink.begin` 打开**最终目标文件** → T2 传输中途断连 / 服务端 200 但字节数对账失败（`finish` 抛 size mismatch）/ 校验失败（`verifyChecksum` 抛）→ catch 仅 `sink.close()` → 目标路径留下**无标记的截断文件**。后续读取方（按目标路径存在的即完整语义）读到损坏数据。
- **现状**: 设计 §3.3 仅声明「非文件输出（toFile()==null）跳过 .part 直写」；**file 目标 + resume=false 直写最终路径**未在设计中声明，且失败残留无清理——非已声明边界。
- **风险**: 消费方以「文件存在即完整」为前提时产生静默数据损坏；与 resume 路径「损坏数据不能作为续传基底」的严谨性形成反差。
- **建议**: 直写路径改用同目录临时名 + 成功后 move（复用 .part 机制，resume=false 只是不跨调用续传、不影响单次落盘原子性）；至少在 javadoc/设计文档声明该残留语义。
- **失效面枚举（F3）**: ① `FileDownloadSink.begin` 直连输出分支（resume=false 文件目标）——**缺陷**；② 同函数非文件目标分支（toFile()==null，如内存/流输出）——设计明文允许，残留由目标语义决定，不立案；③ resume=true 文件路径（.part + move + 校验失败删除）——已闭合。
- **信心水平**: 确定
- **误报排除**: 已对照设计文档 §3.3/§四 确认未声明；非「已声明边界误报」。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——直写最终路径失败残留回码成立；设计契约对照：§3.3 仅声明非文件目标直写，file+resume=false 未声明

### [C6-014] SimpleRpcClientFactory.init() 每次 newInstance 重复 prepend 日志拦截器——列表随调用次数无界增长

- **文件**: `nop-network/nop-rpc/nop-rpc-simple/src/main/java/io/nop/rpc/simple/SimpleRpcClientFactory.java:112-129`
- **证据片段**:
  ```java
  void init() {
      if (clientConfig.isLogBody()) {
          this.interceptors = CollectionHelper.prepend(interceptors, LogRpcServiceInterceptor.INSTANCE);
      }     // newInstance() 每次调用 init() → 每次多一个 Log 拦截器
  ```
- **严重程度**: P3（有界缓慢增长 + 每调用重复日志的性能退化；init 非幂等）
- **并发场景**: 多线程并发 newInstance 时 `this.interceptors` 为普通字段引用替换，存在丢失更新（并发面为理论风险，未具体化，故按口径不上调）。
- **触发时序**: T1 首次 `newInstance()` → interceptors=[Log]；T2 第二次 `newInstance()`（同一 factory 多代理场景）→ interceptors=[Log,Log] → 每个 RPC 调用经过 N 个重复日志拦截器，列表长度随 newInstance 次数线性增长。
- **现状**: init 承担「每工厂一次」的装配却挂在「每实例」入口；`LogRpcServiceInterceptor.INSTANCE` 为无状态单例，无内存放大，仅列表增长与重复日志。
- **风险**: 长运行频繁创建代理的集成场景日志量与调用开销线性放大。
- **建议**: init 拆分：拦截器装配移入构造/首次 init（幂等守卫），newInstance 仅做实例创建。
- **失效面枚举（F3）**: ① `SimpleRpcClientFactory.init`——**非幂等**；② `SimpleRpcServer.init`（同类装配，start 前置且 Guard 防重复 start）——通过。
- **信心水平**: 确定
- **误报排除**: 无状态单例拦截器的重复注册是可观察的行为缺陷，非「原语使用」误报。
- **复核状态**: 未复核
- 复核状态: 已驳回·同案并入C1-001(P3)｜新发现——与 C1-001(a) 同站点同缺陷本体（init 非幂等 prepend），C1-001 主登记已计

### [C6-015] SimpleRpcServer.stop() 后无法重启：socketServer 未复位，start() 的 Guard 拒绝

- **文件**: `nop-network/nop-rpc/nop-rpc-simple/src/main/java/io/nop/rpc/simple/SimpleRpcServer.java:142-152,168-178`
- **证据片段**:
  ```java
  public void start() {
      Guard.checkState(socketServer == null, "server already inited");
      ...
  }
  public void stop() {
      if (socketServer != null) { socketServer.stop(); }   // 未置 null
      if (ownExecutor) { ... executor.destroy(); }
  }
  ```
- **严重程度**: P3（生命周期单向：stop→start 必抛 IllegalStateException；ILifeCycle 契约下重启是否支持未声明）
- **并发场景**: 无并发要求；顺序生命周期。
- **触发时序**: T1 `start()` → `stop()` → T2 再次 `start()`：`socketServer` 仍非 null → Guard.checkState 抛「server already inited」。同实例重建通道被永久关闭。
- **现状**: 停止后字段未复位；若重启属支持场景则缺陷，若不支持则至少 `isActive()`/`broadcast()` 在 stop 后访问 `socketServer` 的语义未声明（stop 后 `broadcast` 直接 NPE）。
- **风险**: 运维编排「停-改-启」流程对该 bean 失效；错误信息误导（报 already inited 而非 stopped）。
- **建议**: `stop()` 末尾置 `socketServer = null`（并复位 handler），或显式声明本 bean 不支持重启并在 stop 后对 broadcast/sendTo 做 fail-fast 错误。
- **失效面枚举（F3）**: ① `SimpleRpcServer` start/stop——**不可重启**；② `SimpleRpcClientFactory`（destroy 后 newInstance 见 C6-010）；③ `OkHttpClientProvider`（LifeCycleSupport doStart/doStop 可重入，doStop 后 getClient 返回已 evict 的 client——行为陈旧但可重启）——部分。
- **信心水平**: 确定
- **误报排除**: Guard 本身是 fail-fast 正确用法，问题在状态未复位导致契约面缺口。
- **复核状态**: 未复核
- 复核状态: 已保留(P3)｜新发现——stop 未复位 socketServer、重启必抛回码成立

### [C6-016] FileDownloadSubscriber / HttpInputFileBodyPublisher 主源码无生产调用方——两套并行的资源管理实现漂移

- **文件**: `nop-network/nop-http/nop-http-client-jdk/src/main/java/io/nop/http/client/jdk/FileDownloadSubscriber.java:13-79`；`nop-network/nop-http/nop-http-client-jdk/src/main/java/io/nop/http/client/jdk/HttpInputFileBodyPublisher.java:11-72`
- **证据片段**:
  ```java
  // 全仓 grep：二者的 main 源码引用仅自身定义与 MultipartBodyPublisher→ConcatenatedBodyPublisher 链，
  // FileDownloadSubscriber/HttpInputFileBodyPublisher 的使用点全部位于 src/test（TestBodyPublisherResourceFix 等）
  ```
- **严重程度**: P3（死代码维护风险；其流关闭逻辑反而完备——完成/异常/取消三路径均 close）
- **并发场景**: 不适用（未被生产路径调用）。
- **触发时序**: 不适用（非运行期缺陷；风险在后续演进中主路径修复不回灌死代码，形成 C6-001 类缺陷的孪生温床）。
- **现状**: jdk 包内存在两套下载/上传资源管理实现：生产用 `FileDownloadSink` 路径（有 C6-001 缺陷），测试引用的 Flow 订阅者实现（资源关闭正确但无人使用）。
- **风险**: 双实现漂移；死代码中正确的关闭模式（如 cancel 路径 close）未反哺生产路径。
- **建议**: 删除或在 javadoc 标注保留原因；若保留，将 FileDownloadSubscriber 的关闭纪律对照修复 C6-001。
- **失效面枚举（F3）**: ① `FileDownloadSubscriber`（测试-only）；② `HttpInputFileBodyPublisher`（测试-only）；③ `ConcatenatedBodyPublisher`（被 `MultipartBodyPublisher` 生产引用，cancel 转发当前内部订阅——已闭合）。
- **信心水平**: 确定（grep 复现：main 引用为空）
- **误报排除**: 按「为压测写的测试专用代码不按生产标准报 P1」口径仅列 P3 维护项，不报其内部并发问题。
- **复核状态**: 未复核
- 复核状态: 已保留(P3)｜新发现（维护面）——main 零生产引用 grep 复现成立

### [C6-017] NettyTcpServer.doStart bind 失败不关闭已创建的 boss/worker 事件循环组——每次启动失败泄漏整组 I/O 线程

- **文件**: `nop-network/nop-netty/src/main/java/io/nop/netty/tcp/NettyTcpServer.java:135-141,165-171,175-196`
- **证据片段**:
  ```java
  EventLoopGroup bossGroup = NettyHelper.newEventGroup(config.getBossGroupSize(), ...);   // L135 先创建
  EventLoopGroup workerGroup = this.workerGroup;
  if (workerGroup == null)
      workerGroup = NettyHelper.newEventGroup(config.getWorkerGroupSize(), ...);          // L139 再创建
  ...
  if (shapingConfig != null && !shapingConfig.isNoLimit()) {
      globalTrafficShapingHandler = new GlobalChannelTrafficShapingHandler(workerGroup, ...); // L168
  }
  ...
  } catch (Exception e) {
      LOG.error("nop.netty.server.bind-port-fail", e);
      throw NopException.adapt(e);                                                        // L189 直接抛出，无任何清理
  }
  ```
  doStart 先创建 bossGroup/workerGroup（NioEventLoopGroup 默认线程数 = CPU×2，非 daemon），再 bind；bind 失败（端口占用/权限）时 `throw NopException.adapt(e)` 直接传播，两个 group 与 globalTrafficShapingHandler 均未 shutdown/release。doStop 是唯一清理点，但 bean 启动失败时不会走到 doStop。
- **严重程度**: P1（错误路径资源泄漏；泄漏与残留形态不适用启动期单线程降档）
- **并发场景**: 启动/重启线程（调用 start() 的任意线程）与已创建 NioEventLoopGroup 的内部线程。
- **触发时序**: T1 start() → L135/139 创建两组 → L177 bind 失败（端口已被占用，运维常见事件）→ L189 抛出 → boss+worker 两组的全部 event loop 线程滞留存活。每次「启动失败→再启动」循环净增 2×CPU 个非 daemon 线程；泄漏线程还持有 NIO selector 与打开的文件描述符。
- **现状**: bind 失败路径零清理；无任何已声明边界覆盖此形态。
- **风险**: 容器化环境的端口冲突重试循环（restart backoff）下线程数线性增长，最终触发 `OutOfMemoryError: unable to create native thread`；非 daemon 线程同时阻止 JVM 正常退出。
- **建议**: bind 失败 catch 块中补 `bossGroup.shutdownGracefully()`（外部注入 workerGroup 时只关 boss）+ `globalTrafficShapingHandler.release()`；参照同文件 doStop（L310-329）的清理清单逆序执行。
- **失效面枚举（F3）**: ① `NettyTcpServer.doStart`——**泄漏（本条）**；② `NettyTcpClient.doStart`——`bootstrap.connect` 同步不抛（返回 future，失败走 scheduleReconnect），无此路径；③ `SocketServer.doStart`——bind 失败 catch 内 `IoHelper.safeCloseObject(socket)`（L130-135）已闭合；④ `VertxMqttServer.doStart`——无自建 group，缺陷形态不同（见 C6-023）；⑤ `SimpleRpcServer`——批次 1 已审（C6-015）。修复须仅覆盖 ①。
- **信心水平**: 确定
- **误报排除**: 非「启动期单线程」降档对象——泄漏物是常驻 I/O 线程组且随每次失败重启累积；非 nop-kernel 平台基础设施自身（NettyHelper 仅被使用）。
- **复核状态**: 未复核
- 复核状态: 已保留(P1)｜新发现——bind 失败两抛出点零清理回码成立；doStop 清理清单对照成立

### [C6-018] SftpClient 构造期「session 已连、channel 未开」失败——SSH 会话无人回收，调用方因构造抛错拿不到对象

- **文件**: `nop-integration/nop-integration-sftp/src/main/java/io/nop/integration/sftp/SftpClient.java:131-166（openConnection）,61-69（构造函数）`；调用方 `SftpClientFactory.java:72-75`、`nop-integration-file-local/src/main/java/io/nop/integration/file/local/FileServiceResource.java:38-56`
- **证据片段**:
  ```java
  // SftpClient 构造函数：connect() 抛错 → 对象半构造，外部无引用可 close
  SftpClient(SftpConfig config, ICredentialProvider credentialProvider) {
      ...
      this.connect();                       // L68
  }
  // openConnection：L163 与 L165 两步之间失败即泄漏
  session.connect();                        // L163 —— TCP+SSH 已建立
  channel = (ChannelSftp) session.openChannel("sftp");
  channel.connect();                        // L165 —— sftp 子系统不可用/被禁时抛错
  // close() 是唯一回收点（L309-319），但构造失败时永远不会被调用
  ```
  `openConnection` 中 `session.connect()` 成功后、`channel.connect()` 失败（典型：服务器未启用 sftp 子系统、channel 超时）时，异常包装为 `ERR_SFTP_CONNECT_FAIL` 从构造函数抛出。调用方（`SftpClientFactory.newClient`、`FileServiceResource.getFileStatus` 的 `factory.newClient()`）因构造抛错拿不到实例，其 finally 中的 `IoHelper.safeCloseObject(client)` 对象引用为 null，无从清理。
- **严重程度**: P1（错误路径资源泄漏，可重复触发）
- **并发场景**: 不需要并发；每次 newClient() 逐次失败即逐次泄漏。
- **触发时序**: T1 调用方 `factory.newClient()` → 构造 → `session.connect()` 成功（服务端 SSH 正常）→ `channel.connect()` 失败（sftp 子系统缺失）→ NopException 传播出构造函数 → 已连接的 SSH session 及其 socket 无人 disconnect。服务端每次重试操作（如定时文件同步轮询）都新增一条挂起的 SSH 连接，直至服务端 MaxSessions/连接上限触发或对端 keepalive 超时。
- **现状**: 失败路径零清理；「逐次操作期解析、每次新建」的工厂语义放大了泄漏频率。
- **风险**: 目标主机 sshd 连接数耗尽（默认 MaxSessions 10）；本地 fd 泄漏；服务端审计日志出现大量未正常退出的会话。
- **建议**: `openConnection` 用 try/catch 包裹 `session.connect()` 之后的步骤，失败时 `session.disconnect()` 再抛；或把 `close()` 逻辑提取为 `disconnectQuietly()` 并在 `connect()` 的 catch 中调用。
- **失效面枚举（F3）**: ① `SftpClient.connect`——**泄漏（本条）**；② `SocketClient.connect`——catch 内 `IoHelper.safeCloseObject(socket)`（L121-126）已闭合；③ `SocketServer.doStart`——bind 失败关闭（L130-135）已闭合；④ `JavaEmailSender.connectTransport`——transport 建连失败由 `withTransport` finally `transport.close()`（L168-172）兜底已闭合；⑤ `FeishuClient.start`——`transport.connect` 失败时 WS 未建成（buildAsync 抛错），无残留。同族仅 ① 缺陷。
- **信心水平**: 确定
- **误报排除**: 非调用方责任——`FileServiceResource` 等 finally 写法正确，是构造函数抛错导致引用不可得；jsch Session 无 finalizer 兜底，不构成确定性释放。
- **复核状态**: 未复核
- 复核状态: 已保留(P1)｜新发现——session.connect 成功后 channel.connect 失败无人回收、构造抛错致引用不可得回码成立；同案：C3-024（同文件不同面：本条构造期泄漏 vs 挂死面）

### [C6-019] TencentEmailSender 每次 sendEmail 新建 SesClient 从不 shutdown——HTTP 连接池随发送次数无界累积

- **文件**: `nop-integration/nop-integration-email-tencent/src/main/java/io/nop/integration/email/tencent/TencentEmailSender.java:167-198`
- **证据片段**:
  ```java
  public void sendEmail(EmailMessage mail) {
      ResolvedCredential credential = resolveCredential();
      SesClient client = createClient(credential);   // L169 —— 每次发送新建
      try {
          ...
          client.SendEmail(req);                     // L186
      } catch (TencentCloudSDKException e) {
          ...
      }                                              // 无 finally、无 client.shutdown()
  }
  ```
  Tencent Cloud SDK 的 `SesClient`（经 `BaseClient`）内部各自持有 HTTP 连接池与底层 Apache HttpClient 资源，官方契约要求用毕 `shutdown()`。本类每次 `sendEmail` 调用 `createClient` 新建实例且从不关闭——对照同批次 `YunpianSmsSender.sendMessage/sendMultiMessage` 均 `finally client.close()`（L125-144）。
- **严重程度**: P1（长时运行必然触发的无界增长；F8 资源清理不对称）
- **并发场景**: 任意业务线程（MFA 发码、通知发送）各自调用 sendEmail，无需并发即可累积。
- **触发时序**: T1 第 1 次 sendEmail → 新建 SesClient（连接池 P1 + keepalive 空闲连接）→ 发送成功 → client 脱离作用域但池内 ESTABLISHED socket 仍存活 → T2 第 N 次发送 → 累积 N 套池。SesClient 无 finalizer 关闭连接池，socket 仅能依赖 TCP keepalive/对端超时或 GC 后的弱清理，fd 与内存按发送次数线性增长。
- **现状**: 成功与失败路径均零关闭；与 yunpian 短信兄弟实现的 close 纪律形成同族偏差。
- **风险**: MFA/邮件通知是高频长驻场景，`ulimit -n` 耗尽后进程整体不可用；每套池附带的后台线程进一步放大 native 线程压力。
- **建议**: `try { ... } finally { client.shutdown(); }`；或参照 `OssFileServiceClientFactory` 模式把 SesClient 提为 bean 级单例（`@PostConstruct` 创建 + `@PreDestroy shutdown`），凭证轮换可见性变化需在 design 层另行裁定（当前注释明示「逐次解析、每次新建」语义，故保守修复为 finally shutdown）。
- **失效面枚举（F3）**: ① `TencentEmailSender.sendEmail`——**泄漏（本条）**；② `TencentSmsSender.sendMessage/sendMultiMessage`（L149-151/196）——qcloudsms `SmsSingleSender` 无 close/shutdown API 且无池化（每请求独立 URLConnection，请求完即 GC 可回收），非同缺陷；③ `YunpianSmsSender.sendMessage/sendMultiMessage`——finally close 已闭合；④ `JavaEmailSender.withTransport`——finally transport.close 已闭合；⑤ `JdkFeishuHttpApi/JdkStreamTransport`——HttpClient 停止期不关闭，属 C6-025 另案。修复须覆盖 ①。
- **信心水平**: 确定（关闭点缺失为静态事实；SDK 池资源规模按官方客户端模型判定）
- **误报排除**: 非「有界声明缓存」——无任何上限配置面；非低频启动路径——sendEmail 是运行期业务热路径。
- **复核状态**: 未复核
- 复核状态: 已保留(P1)｜新发现——SesClient 每发新建从不 shutdown 回码成立；YunpianSmsSender finally close 对照成立

### [C6-020] MqttSessionManager 会话接管/移除与 subscribe 注册交错——新连接的订阅登记被旧连接清理路径整组删除，推送静默丢失

- **文件**: `nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/impl/MqttSessionManager.java:33-45`；注册顺序 `impl/VertxMqttServer.java:126-135（acceptEndpoint）` 与 `impl/MqttConnection.java:72-140（init 内 subscribeHandler → sessionManager.subscribe）`
- **证据片段**:
  ```java
  public IMqttConnection addConnection(IMqttConnection conn) {
      IMqttConnection old = sessions.put(conn.getClientId(), conn);
      if (old != null && old != conn) {
          subscriptions.remove(old.getClientId());   // L36 —— 接管：按 clientId 整组删
          old.close();
      }
      return old;
  }
  public void removeConnection(IMqttConnection conn) {
      sessions.remove(conn.getClientId(), conn);      // L43 —— sessions 有两参幂等守卫
      subscriptions.remove(conn.getClientId());       // L44 —— subscriptions 无守卫，无条件整组删
  }
  ```
  两个交错窗口都能删掉**当前活跃连接**的订阅：① 接管窗口——`VertxMqttServer.acceptEndpoint` 先 `new MqttConnection(...)`（构造内 `endpoint.accept()` 已发 CONNACK，L139）**再**调 `addConnection`；新客户端在 CONNACK 后立即发 SUBSCRIBE，`subscribeHandler` → `sessionManager.subscribe(clientId)` 先落表，随后 `addConnection` 的 `subscriptions.remove(old.getClientId())` 把 clientId 相同的新连接订阅一并删除。② 迟到关闭窗口——接管后旧连接 A1 的 closeHandler 异步触发 `onCloseCallback → removeConnection(A1)`，`sessions.remove` 的两参形式正确跳过新连接，但 L44 无条件删除该 clientId 的 subscriptions（此时已属于新连接 A2）。
- **严重程度**: P1（罕见但可具体化的竞态，有明确触发链；后果为推送静默丢失）
- **并发场景**: MQTT 设备重连（同一 clientId）：新连接的 event loop context 与旧连接 close 回调 context、应用线程（sendAsync 调用方）三方交错。
- **触发时序**: T1 设备断网重连 → A2 CONNACK → 设备发 SUBSCRIBE `t/#` → `subscriptions[cid]={t/#}`；T2 `addConnection(A2)` 执行 `subscriptions.remove(cid)`（窗口①）→ A2 订阅登记被清空；或 T2′ A1 close 回调晚于 A2 的 SUBSCRIBE 到达 → `removeConnection(A1)` L44 清空（窗口②）。后果：`MqttServerMessageService.sendAsync` 的 `findConnections(topic)` 因 `subscriptions.get(cid)==null` 不再返回 A2，L80-81 `connections.isEmpty()` 返回 `success(null)`——**消息未送达却报告成功**。
- **现状**: 清理粒度（按 clientId 整组）与所有权（per-connection 登记）不匹配；`sessions` 的两参 remove 表明作者已意识到接管竞态，但 subscriptions 漏改。
- **风险**: 设备在线却收不到下行指令（IoT 控制面静默失效），且无任何错误可观测——正是 F2「半个成功」在推送面的形态。
- **建议**: `removeConnection` 改为订阅登记与连接同键绑定：`subscriptions` 值改为持有所属 `IMqttConnection` 引用的记录，删除时校验属主（与 `sessions.remove(key, value)` 同法）；`addConnection` 的接管删除只删 `old` 名下的登记。同时把 `acceptEndpoint` 调整为先注册会话再 `endpoint.accept()`，消除窗口①。
- **失效面枚举（F3）**: ① `MqttSessionManager.removeConnection`（迟到关闭窗口）——**缺陷**；② `MqttSessionManager.addConnection` 接管删除（CONNACK 先于注册的顺序窗口）——**缺陷**；③ `MqttConnection.complete → onCloseCallback`——回调本身幂等（closed 守卫）非缺陷源；④ `MqttServerMessageService` subscribers 表——独立生命周期，不在本条（见 C6-026）。修复须同时覆盖 ①②。
- **信心水平**: 很可能（窗口①②均为真实代码顺序，交错概率取决于客户端 SUBSCRIBE 时机与旧连接 close 回调时序；后果链完整到 `success(null)`）
- **误报排除**: 非误报校准中任一形态——`ConcurrentHashMap` 原语使用正确，缺陷在复合操作（删 key 与属主校验缺失）；非「已声明边界」——mqtt-messaging-design.md 未声明接管期订阅丢失语义。
- **复核状态**: 未复核
- 复核状态: 已保留(P1)｜新发现——双窗口回码成立；同案并入 C1-008/C3-018 修复单元（窗口②同根）；独有窗口①成立——异步 auth 下接管删除可清掉 CONNACK 后已登记的新连接订阅（C1-008 失效面①「L36 语义正确」在该前提下被证伪，随同 diff 修正）；同族前案 nosql-cdc（非同站点）

### [C6-021] FeishuClient.stop() 与在途 attemptReconnect 竞态——stop 之后新建的 WS 孤儿连接无人关闭，listener 复活 connected 状态

- **文件**: `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/client/FeishuClient.java:200-224（stop）,297-310（attemptReconnect）`；`client/JdkStreamTransport.java:37-113（connect）,115-125（close）`
- **证据片段**:
  ```java
  // attemptReconnect：不在 FeishuClient 锁内，仅靠 started 易失标志
  private void attemptReconnect() {
      if (!started) { return; }                       // L298 —— 检查点
      StreamEndpoint ep = httpApi.getStreamEndpoint(...);  // L302 —— HTTP 往返，最长 15s
      this.endpoint = ep;
      transport.connect(URI.create(ep.getUri()), streamListener);  // L304 —— 重建 WS
      ...
  }
  // JdkStreamTransport.close（stop 路径）：只关当时字段指向的旧 WS
  public void close() {
      WebSocket ws = this.webSocket; ... ws.sendClose(...).join(); this.webSocket = null;
      this.listener = null;
  }
  // JdkStreamTransport.connect：重新武装 listener + 覆盖 webSocket 字段
  this.listener = listener;                            // L41 —— 无条件复活
  this.webSocket = ...buildAsync(uri, wsListener).join();  // L88
  ```
  stop()（synchronized）依次置 started=false → transport.close()（关旧 WS、置 listener=null）→ shutdownNow。但 `attemptReconnect` 运行在 scheduler 线程且不持 FeishuClient 锁：它通过 started 检查后即离开守护，其后 stop() 全部执行完毕，L302/L304 照常执行——connect 内部 `this.listener = listener` 把已被置空的 listener 重新武装，`this.webSocket = ...` 挂上**新 WS**。此后无人调用 transport.close（stop 已过），该 WS 保持打开。
- **严重程度**: P1（取消/重试路径的资源泄漏；重连期停机是现实场景）
- **并发场景**: FeishuClient 单线程 scheduler（attemptReconnect/scheduleReconnect/heartbeat）与外部调用 stop() 的生命周期线程。
- **触发时序**: T1 网络闪断 → onClose → scheduleReconnect → 任务在 t+1s 执行 attemptReconnect，通过 started 检查（L298），进入 15s 级的 getStreamEndpoint HTTP 往返（L302）；T2 运维/容器停止调用 stop()：started=false、旧 WS 关闭、scheduler.shutdownNow（不中断已运行任务）→ T1 继续：transport.connect 成功 → 新 WS 建立并 onOpen → `connected=true` 复活、connect 握手帧发出。该 WS 之后无 heartbeat（已 cancel）、无任何 close 调用方——连接与服务端会话滞留直至对端超时。
- **现状**: stop 语义只覆盖「当时已建立的连接」，未覆盖「检查后建立」的窗口；无世代（generation）令牌使 listener 回调与 stop 校验。
- **风险**: 停机后残留对外长连接（优雅停机语义违约）；若应用随后重启同 appId 客户端，服务端同 ticket 双连接行为未定义；测试/CI 环境泄漏的 WS 拖慢进程退出。
- **建议**: stop() 后递增世代计数，connect/onOpen 回调携带世代校验（过期即 `webSocket.sendClose` 后返回）；或 attemptReconnect 全程置于 `synchronized(this)` 并在 connect 返回后复查 `started`，不成立则立即 `transport.close()`。平台参照物：`LifeCycleSupport` 状态机内做二次校验。
- **失效面枚举（F3）**: ① `FeishuClient.stop × attemptReconnect`——**泄漏（本条）**；② `NettyTcpClient.doStop × scheduleReconnect/doConnect`——同构竞态，登记为 **C6-022（P2）**；③ `SocketClient.close × recv 重试链`——`closed` 标志在 retryExecute 闭包内复查（L207-215）+ reconnect 在 executor 上重入 `reconnect()` 先 cleanup，已闭合；④ `SimpleRpcServer.stop`（批次 1 C6-015）——单向生命周期，形态不同。修复须覆盖 ①②。
- **信心水平**: 很可能（交错窗口 = attemptReconnect 的 HTTP 往返期，秒级宽度，停机期重连挂起即可命中）
- **误报排除**: 非原语误用——是 stop 协议缺「检查后建立」复核；非「已声明边界」——javadoc 明言 stop 为 "Gracefully stop ... release scheduler"，未声明停机后残留连接。
- **复核状态**: 未复核
- 复核状态: 已保留(P1)｜新发现——stop×在途 attemptReconnect 孤儿 WS 窗口回码成立；同案：C3-016（同站点 P2，判级分歧留 7.1——本条 P1 校准：取消路径泄漏 + 停机语义违约）

### [C6-022] NettyTcpClient stop 与在途重连竞态——stop 之后重建的 channel 无人关闭，connectFuture 字段被停机后覆写

- **文件**: `nop-network/nop-netty/src/main/java/io/nop/netty/tcp/NettyTcpClient.java:205-228（doConnect）,230-256（scheduleReconnect）,262-276（doStop）`
- **证据片段**:
  ```java
  private void doConnect() {
      if (isStopping()) { return; }                    // L206 —— 唯一守卫（非同步）
      ...
      ChannelFuture future = bootstrap.connect(...);
      this.connectFuture = future;                     // L214 —— 停机后仍可覆写
      ...
  }
  protected void doStop() {
      ChannelFuture f = this.connectFuture;            // L264 —— 只读一次当时值
      if (f != null) { NettyHelper.safeClose(f.channel()); }
      if (this.workerGroup == null) {
          bootstrap.config().group().shutdownGracefully();  // L273 —— 不等待完成
      }
  }
  ```
  event loop 上排定的 `this::doConnect`（L251）与外部线程的 doStop 之间无同步：T1 的 doConnect 通过 isStopping 检查后，T2 的 stop() 读取并关闭**旧的** connectFuture、发起 shutdownGracefully，随后 T1 的 `this.connectFuture = future` 覆写字段、新 connect 提交到正在关闭的 group——若 connect 恰在 shutdown 生效前完成，新 channel 存活且无人关闭（stop 已读完 connectFuture）。
- **严重程度**: P2（窄窗口竞态但可具体化；后果为停机后残留连接 + 字段语义违约）
- **并发场景**: 外部生命周期线程（stop）与 client 自身 event loop（scheduleReconnect→doConnect）。
- **触发时序**: T1 event loop 执行排定的 doConnect，通过 isStopping()（false）→ 进入 bootstrap.connect；T2 stop()：`connectFuture` 读到旧值并关闭 → shutdownGracefully（异步）→ T1 `this.connectFuture = future` 覆写 → 新 channel 在 group 完全关闭前建立成功。结果：stop 返回后 client 仍持有一条活连接，getConnectFuture() 返回的是停机后新建的 future。
- **现状**: 守卫只有非同步的 isStopping 检查；与 C6-021 同构（「检查后建立」无复核），但后果较轻（channel 随 group 关闭大概率夭折，仅窄窗口存活）。
- **风险**: 停机语义不确定：偶发残留连接占用服务端会话；重启复用同一 client bean 时状态混淆（connectFuture 指向已停机周期的连接）。
- **建议**: doStop 先置停止标志再 `shutdownGracefully().awaitUninterruptibly(超时)` 之后再读 connectFuture 收尾关闭；或 doConnect 在 future 完成回调内复查 isStopping，不成立即 `f.channel().close()`（与 C6-021 的世代校验方案统一）。
- **失效面枚举（F3）**: ① `NettyTcpClient.doStop × doConnect`——**缺陷（本条）**；② `NettyTcpServer.doStop`——channelGroup.close().awaitUninterruptibly() 后才关 group，且服务端无自重连路径，已闭合；③ `SocketServer.doStop`——stopped 标志 + run 循环内 `if (stopped) break`，accept 循环退出已闭合；④ FeishuClient 同构缺陷见 C6-021。
- **信心水平**: 很可能（窗口窄但每一步均为真实可达代码）
- **误报排除**: 写不出时序才降 P2——本条已给出具体交错；非「低频启动路径粗锁」豁免形态——缺陷恰是**缺锁**。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——connectFuture 停机后覆写窗口回码成立；与 C1-013 同根不同交错，同批修复

### [C6-023] VertxMqttServer.doStart 不等待也不观察 listenFuture——绑定失败被完全吞掉，bean「启动成功」但端口未监听（F9）

- **文件**: `nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/impl/VertxMqttServer.java:67-75`
- **证据片段**:
  ```java
  @Override
  protected void doStart() {
      Guard.notNull(serverOptions, "serverOptions");
      mqttServer = MqttServer.create(NopVertx.instance(), serverOptions);
      mqttServer.exceptionHandler(err -> {
          LOG.error("nop.vertx.mqtt.server.start-fail", err);
      }).endpointHandler(this::handleEndpoint);
      listenFuture = mqttServer.listen();     // L74 —— 返回值无人 await/.addListener
  }
  ```
  `listenFuture` 字段被赋值后再无任何消费者（全文件 grep 仅 L73/L74 两处）；`mqttServer.exceptionHandler` 只覆盖连接期异常，listen 绑定失败（端口占用）经 Future 的失败分支传播，无人观察即静默丢弃。LifeCycleSupport.start() 正常返回。
- **严重程度**: P2（F9 异步链失败静默吞掉 → 生命周期状态与实际不一致）
- **并发场景**: 启动线程与 Vert.x 内部 accept 线程（listen 的异步完成回调）。
- **触发时序**: T1 start() → listen() 提交 → doStart 返回，bean 标记 started；T2 Vert.x 绑定失败（端口已被占用/权限）→ listenFuture 异常完成 → 无 listener → 任何日志都没有 → 调用方与治理面板均认为 MQTT 服务在线，实际无监听。
- **现状**: 与批次 1 `SimpleRpcServer`（socketServer 同步 bind，失败会抛）和批次 2 `NettyTcpServer.doStart`（`.sync()` + isSuccess 双检，L177-196）形成三兄弟两比一差——本条是唯一吞失败的。
- **风险**: 部署编排（等端口就绪再放流量）全部失效；设备侧表现为连接被拒，排障只能靠客户端报错反推；叠加重启编排可能长期处于「看似健康」的死服务状态。
- **建议**: 对齐 NettyTcpServer：`listenFuture = mqttServer.listen()` 后 `.toCompletionStage().toCompletableFuture().get(timeout)`（或 `FutureHelper.syncGet`）并失败即抛 NopException——doStart 内同步等待绑定结果；至少补 `listenFuture.onFailure(err -> { LOG.error(...); stop(); })` 兜底。
- **失效面枚举（F3）**: ① `VertxMqttServer.doStart`——**吞失败（本条）**；② `NettyTcpServer.doStart`——sync+isSuccess 双检已闭合（但失败清理缺陷见 C6-017）；③ `SocketServer.doStart`——同步 bind + 抛 NopException 已闭合；④ `SimpleRpcServer.start`——批次 1 已审同步路径。
- **信心水平**: 确定（listenFuture 无消费者的静态事实 + Vert.x Future 无人观察即丢弃的语义）
- **误报排除**: 非「exceptionHandler 已兜底」——该 handler 挂在 MqttServer 的连接异常面，listen 绑定失败不经它；非低频理论路径——端口冲突是部署期常态。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——listenFuture 无消费者回码成立；同案：C3-019（同站点）

### [C6-024] JdkStreamTransport.send/close 无超时阻塞——心跳占用唯一调度线程，重连永不执行且 stop() 可无限期挂起（F5）

- **文件**: `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/client/JdkStreamTransport.java:107（send .get()）,118（close .join()）`；阻塞后果载体 `FeishuClient.java:101（单线程 scheduler）,258-265（scheduleHeartbeat）`
- **证据片段**:
  ```java
  public void send(byte[] frameBytes) {
      ...
      ws.sendBinary(ByteBuffer.wrap(frameBytes), true).get();   // L107 —— 无超时阻塞
  }
  public void close() {
      ...
      ws.sendClose(WebSocket.NORMAL_CLOSURE, "client shutdown").join();  // L118 —— 无超时
  }
  ```
  FeishuClient 的 scheduler 是 `newSingleThreadScheduledExecutor`（L101），heartbeat（scheduleAtFixedRate）与全部 reconnect 任务共用它。sendBinary 的 future 在输出通道停滞（对端不读、NAT 静默丢弃）时无限期 pending：心跳任务卡死在 L107 → 调度线程被永久占用 → scheduleReconnect 排队的 attemptReconnect 永不执行；同时心跳发送失败的 catch 只 LOG.debug（L279），不影响 connected 标志——连接已死但 `isConnected()==true`。stop() 路径同理可挂在 L118。
- **严重程度**: P2（F5「挂死生产者对健康检测不可见」；负载/网络劣化下的必然滞留形态）
- **并发场景**: FeishuClient 单线程 scheduler（heartbeat+reconnect）、JDK WS reader 线程（onBinary 内的 ACK send 也走同一 send() 阻塞点）、外部 stop 线程。
- **触发时序**: T1 心跳到期 → sendHeartbeat → transport.send → sendBinary future 因对端停滞不完成 → L107 永久阻塞；T2 期间任何断连事件排定的 attemptReconnect 在队列中永不调度 → connected 恒为 true，`isConnected()` 健康检查失真，消息发送面（sendMessage 走独立 HTTP）表面正常。
- **现状**: 两处无超时等待 + 心跳失败仅 trace 级日志，构成「卡死且不可见」组合；JDK WS 未设置底层 soTimeout 语义（连接为 WS，无法套用 CL-026 的 readTimeout 结论）。
- **风险**: 长驻渠道连接在弱网/对端半开场景下永久失联且无告警；FeishuConnector 的渠道健康面（isConnected）持续误报在线。
- **建议**: `send` 改 `future.get(sendTimeout, TimeUnit.SECONDS)`（超时即抛并由调用方触发 reconnect/onError 路径）；`close` 改 `sendClose(...).timeout(Duration)` 或 `get(超时)` + 失败降级 `ws.abort()`；心跳发送失败升级为 WARN 并联动 scheduleReconnect。
- **失效面枚举（F3）**: ① `JdkStreamTransport.send/close`——**无超时（本条）**；② `JdkFeishuHttpApi.postJson*`——15s request timeout 已闭合；③ `FeishuClient.ensureToken→httpApi`——同 ② 已闭合；④ nop-http 批次 1 的下载读循环无超时同族——CL-026 已登记（JDK 下载路径）。修复须覆盖 ①。
- **信心水平**: 确定（阻塞点与调度结构为静态事实；触发依赖对端停滞，网络半开下必然出现）
- **误报排除**: 非「原语使用即问题」——缺陷是 get()/join() 未配超时且复用单线程调度器；非压测代码。
- **复核状态**: 未复核
- 复核状态: 已保留(P1，升格)｜新发现——send/close 无超时回码成立；升格校准：与同站点 C3-015（P1）对齐——半开挂死不可见（F5）+ 单线程 scheduler 全死 + 实仓消费方 FeishuConnector；同案：C1 文件 CL-005/C3 文件 CL-006（移交闭环）

### [C6-025] feishu 渠道两个 JDK HttpClient 停止期不关闭——selector 线程与连接池跨启停周期累积（C6-007 同族 F3）

- **文件**: `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/client/JdkStreamTransport.java:33`；`client/JdkFeishuHttpApi.java:32-35`；停止路径 `client/FeishuClient.java:200-224`
- **证据片段**:
  ```java
  // JdkStreamTransport 构造
  this.httpClient = HttpClient.newHttpClient();          // L33 —— WS 专用实例
  // JdkFeishuHttpApi 构造
  this.httpClient = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(15)).build();  // L32-35 —— Open API 实例
  // FeishuClient.stop()：只经 transport.close() 关 WS，两个 HttpClient 无任何关闭链
  ```
  每个 `FeishuClient` 生产构造持有两个 `java.net.http.HttpClient` 实例；JDK HttpClient 的 selector 线程与连接池随实例存活，`HttpClient` 无显式 close（JDK 21+ 提供 `shutdown()/shutdownNow()`，当前 JDK 25 目标下可用）。stop() 不触达任何一处；若部署以「stop→new FeishuClient→start」做凭证轮换或渠道重启，每周期泄漏 2 个 selector 线程 + 池化连接。
- **严重程度**: P2（长运行/热更新启停周期资源滞留；批次 1 C6-007 的同族兄弟）
- **并发场景**: 不需要并发；启停周期即累积。scheduler 经 shutdownNow 释放，是本类唯一被释放的资源。
- **触发时序**: T1 stop() → transport.close（WS 关）+ shutdownNow（scheduler 关）→ 两个 HttpClient 原样存活；T2 new FeishuClient().start() → 新建 2 个 HttpClient（+2 selector 线程）。第 N 次轮换后 2N 个 HttpClient 滞留，旧实例仅 GC 可达性丧失后由 JDK 内部清理（时机不保证）。
- **现状**: 与批次 1 C6-007（`JdkHttpClient.stop()` 不关 HttpClient）同一缺陷族在 feishu 渠道的两个新实例化点；F3 要求登记后统一修。
- **风险**: 渠道热更新/凭证轮换频繁的部署下线程数与 fd 缓慢爬升；监控面以「feishu-* 线程数」呈现无界增长。
- **建议**: IStreamTransport/IFeishuHttpApi seam 增加 close 契约（JdkStreamTransport.close 内补 `httpClient.shutdown()`，JdkFeishuHttpApi 同理），FeishuClient.stop() 顺次调用；与 C6-007 的修复统一为一个 owner doc 条目。
- **失效面枚举（F3）**: ① `JdkStreamTransport.httpClient`——**泄漏（本条）**；② `JdkFeishuHttpApi.httpClient`——**泄漏（本条）**；③ 批次 1 `JdkHttpClient`——C6-007 已登记；④ `JdkStreamTransport` 每次新建的匿名 WebSocket.Listener 与 per-connect ByteArrayOutputStream——随 WS 对象回收，无独立资源，通过。
- **信心水平**: 确定（关闭链缺失为静态事实；JDK 25 下 shutdown API 可用）
- **误报排除**: 非「GC 会兜底」——HttpClient selector 线程是强可达 GC root，实例不显式 shutdown 即存活；非单例设计意图——类注释明示按渠道实例化、stop 后可重建。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——两个 HttpClient 无关闭链回码成立；C6-007 同族 F3 成立

### [C6-026] MqttServerMessageService dispatcher 注册配对破损——并发首发重复 add 导致消息双重分发，cancel 清理分支不可达（F8/F10）

- **文件**: `nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/bus/MqttServerMessageService.java:90-134`
- **证据片段**:
  ```java
  public IMessageSubscription subscribe(String topic, IMessageConsumer listener, ...) {
      if (mqttServer != null && subscribers.isEmpty()) {   // L93 —— check-then-act，无同步
          mqttServer.addPublishListener(dispatcher);        // L95 —— 可重入叠加
      }
      subscribers.computeIfAbsent(topic, k -> new ArrayList<>()).add(listener);
      ...
      public void cancel() {
          ...
          subscribers.computeIfPresent(topic, (k, list) -> {
              list.remove(listener);
              return list;                                  // L107-109 —— 空列表仍返回，条目永不移除
          });
          if (subscribers.isEmpty() && mqttServer != null) { // L111 —— 恒 false
              mqttServer.removePublishListener(dispatcher);  // L112 —— 不可达
          }
      }
  }
  ```
  两个方向都破损：① **add 侧竞态**——两个线程同时通过 L93 的 `subscribers.isEmpty()` 检查（首个订阅并发到达），`addPublishListener(dispatcher)` 执行两次，CopyOnWriteArrayList 允许重复元素 → 每条入站 publish 被 dispatch 两遍（consumer 非幂等时产生双份副作用）。② **remove 侧死码**——computeIfPresent 恒返回列表（含空列表），map 条目首次 subscribe 后永不消失 → L111 `isEmpty()` 恒 false → dispatcher 永不摘除（后续功能依赖「登记仍在」侥幸正确，但注册表对称性已破，且每条 publish 白遍历空列表）。
- **严重程度**: P2（F8 清理不对称 + F10 守卫缺失；双分发的触发链可具体化）
- **并发场景**: 应用启动期多 bean 并发订阅消息总线（首个订阅的并发窗口）；运行期订阅/取消交错。
- **触发时序**: T1 线程 A `subscribe("t1", cA)` 读 isEmpty=true → 将 add；T2 线程 B `subscribe("t2", cB)` 读 isEmpty=true → 将 add；T3 两次 addPublishListener 均执行 → dispatcher 在 publishListeners 中出现两份 → 设备 publish 一条消息，wrapHandler 对每个 listener 各调一次 → `dispatch` 执行两次 → cA/cB 各收到两遍。
- **现状**: 用「map 是否为空」兼作跨结构注册标志是根因；CopyOnWriteArrayList 的 add/remove 均不查重。
- **风险**: 双重分发对 MFA 发码/通知类 consumer 即双重扣减/双份外发；长期看 dispatcher 滞留使「全部取消后零开销」契约失效。
- **建议**: 注册标志改为 `AtomicBoolean dispatcherRegistered`（CAS 成功才 addPublishListener，最后一个取消时 compareAndSet 摘除）；cancel 中列表为空时 `subscribers.remove(topic, list)` 真正移除条目。
- **失效面枚举（F3）**: ① `MqttServerMessageService.subscribe/cancel`——**配对破损（本条）**；② `VertxMqttServer.addPublishListener/removePublishListener`——载体本身 add/remove 对称（CopyOnWriteArrayList 直操作），非缺陷源；③ 批次 1 `RpcMessageSubscriptions`（putIfAbsent/cancel-remove 配对）——已闭合，可作修复参照。
- **信心水平**: 确定（两个窗口均为静态可达代码顺序）
- **误报排除**: 非原语误用类误报——ConcurrentHashMap/COW 列表各自线程安全，缺陷在跨结构的 check-then-act；首发并发窗口窄但消息总线订阅是多 bean 启动期常态。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——①并发首订重复挂载（与 C3-020 同站点同根，并入）与②摘除分支死码（computeIfPresent 恒返回列表→条目永存→isEmpty 恒 false，独立成立）回码均成立；与 C1-012(a)(b) 同方法互补面，一 diff 修复

### [C6-027] SocketClient.connect 的 onChannelOpen 回调异常——socket 留开且状态机被 Guard 锁死，仅 reconnect() 可解

- **文件**: `nop-network/nop-socket/src/main/java/io/nop/socket/SocketClient.java:110-137（connect）,274-294（close/cleanup）`
- **证据片段**:
  ```java
  public synchronized void connect() {
      Guard.checkState(socket == null);                 // L111 —— 重入被锁死的前提
      Socket socket = new Socket();
      try {
          ...
          this.socket = socket;                          // L120 —— 状态先落地
      } catch (IOException e) { IoHelper.safeCloseObject(socket); throw ...; }  // 仅覆盖 IO 段
      if (onChannelOpen != null)
          onChannelOpen.run();                           // L128-129 —— 无 try/finally
      if (config.getHeartbeatInterval() > 1) { ... heartbeatFuture = ...; }     // L131-136
  }
  ```
  `this.socket = socket` 成功后进入用户回调段：`onChannelOpen.run()` 抛异常时，connect() 以异常结束，但 socket 仍打开、is/os 已赋值、`closed=false`、heartbeat 未调度。此后调用方重试 `connect()` 被 L111 `Guard.checkState(socket == null)` 拒绝（IllegalStateException）——正常重试入口永久失效；唯一恢复路径是 `reconnect()`（cleanup 后重连），而调用方通常只持 connect 语义。
- **严重程度**: P2（回调异常路径的资源滞留 + 状态机不可自愈；F8 清理不对称）
- **并发场景**: 不需要并发；单线程 connect 失败重试即可触发。回调内若另起线程操作 client 则放大。
- **触发时序**: T1 connect() → 三步握手成功、L120 状态落地 → onChannelOpen 回调（连接建立通知/鉴权注册等用户逻辑）抛 RuntimeException → connect() 异常返回 → socket 仍 ESTABLISHED；T2 调用方捕获后重试 connect() → L111 Guard 抛 IllegalStateException → 重试循环持续失败，泄漏 fd + 服务端半死会话。
- **现状**: IO 段失败已正确清理（L121-126 注释明示「连接失败必须清理半开 socket」），回调段是同一意图下的遗漏窗口。
- **风险**: fd 与对端会话按重试次数累积；心跳缺位使该半死连接无保活，行为不确定；上层（如 SimpleRpcClientFactory 的 connect 包装）把 IllegalStateException 误判为编程错误而非可重试故障。
- **建议**: 回调段与 heartbeat 调度段包入 try/catch，失败时 `IoHelper.safeCloseObject(socket)` + 字段复位（复用 cleanup()）后再抛；或把 `onChannelOpen.run()` 移到状态落地前的独立校验阶段。
- **失效面枚举（F3）**: ① `SocketClient.connect` 回调段——**缺陷（本条）**；② `SocketClient.connect` IO 段——已闭合（L121-126）；③ `SocketClient.reconnect`——cleanup→connect 串联，cleanup 自身无抛出点（IoHelper.safeCloseObject 吞关闭异常），已闭合；④ `SocketServer.doStart`——无用户回调段，已闭合。
- **信心水平**: 确定（回调抛异常即复现；回调是否抛取决于装配方，接口契约未声明不得抛）
- **误报排除**: 非平台基础设施自身问题（onChannelOpen 是本模块暴露的扩展点，守卫责任在本模块）；非「低频启动路径粗锁」——connect 是运行期可重试入口。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——onChannelOpen 回调异常 socket 滞留 + Guard 锁死回码成立

## 维度集外线索（overlay C2/C4/C5/C7/C8/C9）

（CL-001…020 已被其他文件占用；批次 1 登记 CL-021…026，批次 2 续接 CL-027…032）

### [CL-021]（C4/C2）sidecar 嵌套请求与宿主下载同池 + ioThreadCount=1 可自死锁
- `ApacheHttpClientHelper.createReactorConfig`（ApacheHttpClientHelper.java:102-110）允许 `ioThreadCount=1`；此时 C6-006 的回调内 `get(30s)` 使唯一 I/O 线程等待一个永远无法被派发的嵌套请求 → 必然等满 30s×2 超时（有界死锁）。默认 ioThreadCount=0（HC5 取 CPU 数）不死锁，但配置面允许击穿。证据：ApacheHttpClient.java:371-374。

### [CL-022]（C5）fetchAsync 响应体全量内存缓冲，无大小上限
- Apache `SimpleRequestProducer/SimpleResponseConsumer`（ApacheHttpClient.java:109/287/472/515）、JDK `BodyHandlers.ofByteArray`（JdkHttpClient.java:166/539/576）、OkHttp `body().string()`（OkHttpClientImpl.java:75/341）——三类 fetch 全部整体缓冲，配置无 maxBodySize 面；对大响应属 OOM 族（F7）。与下载流式路径并存，按调用方选择分叉。

### [CL-023]（C8）HttpClientConfig.maxRequestsPerHost 默认 0 → Apache 每路由回落 HC5 内置默认 5
- HttpClientConfig.java:28 默认 0；`createConnectionManager`（ApacheHttpClientHelper.java:128）无条件 `setMaxConnPerRoute(0)`；经字节码核实 HC5 5.6.4 `PoolingAsyncClientConnectionManagerBuilder.build()` 对 ≤0 跳过设置（`ifle` 守卫）→ 每路由并发 5、`maxConnTotal=2000` 形同名义值。配置默认值与容量声明矛盾（OkHttp 侧同字段语义为 dispatcher.setMaxRequestsPerHost，0 亦跳过 → OkHttp 默认 64）。非泄漏，配置面缺陷。

### [CL-024]（C8）DownloadOptions.fetchSidecarChecksum 默认 true 的流量/行为放大
- DownloadOptions.java:29 默认 true：所有无 `x-content-sha256` 头的下载自动追加 `{url}.sha256`/`.sha1` 两次 GET（404 也发）。对不支持 sidecar 的目标服务器是纯放大流量；与 C6-006 的线程占用面叠加。默认值合理性属配置维度裁定。

### [CL-025]（C2/C9）Context 异步跳线程后的 ThreadLocal 残留面
- `ContextHttpServerFilter`/`ApiContextInterceptor` 创建的 context attach 在发起线程，`close()` 在完成线程执行；`BaseContext.close` 的 `context==this` 守卫使发起线程 ThreadLocal 残留**已关闭** context（下次请求被 `newContext` 覆盖前，`currentContext()` 可见）。C6-009 已报「未关闭残留」子面；「已关闭残留可见性」属上下文传播（C9）/线程卫生（C2）维度，建议统一审计 ThreadLocal 生命周期（含 `NamedThreadLocal` 使用面）。

### [CL-026]（C7/F5）JDK 下载 body 读取无超时且 cancel 不可中断——服务端中途停滞时 worker 线程永久滞留
- JdkHttpClient.java:447-451 读循环 `in.read(buf)` 无 soTimeout 语义（JDK HttpClient 的 request timeout 只覆盖到响应头）；`cancelToken.appendOnCancelTask(() -> future.cancel(false))`（L381）在 future 已完成后是 no-op，不中断执行读循环的线程。挂起的服务端（发完头不发体）→ 线程+连接无限期滞留，健康检测不可见。OkHttp 同型（OkHttpClientImpl.java:202-209，readTimeout 覆盖 socket 读、相对可控；cancel 经 `call.cancel` 可中断 OkHttp 侧 IO）。建议列长运行维度专项。

### [CL-027]（C1/C2）SslConfig 的 SslContext 缓存 check-then-act 无同步——并发建连重复构建 keystore
- `NettySslEngineFactory.getSslContext`（NettySslEngineFactory.java:18-33）对 `sslConfig.getClientSslContext()/getServerSslContext()` 做「判空→构建→回写」，SslConfig 两字段（L22-23）非 volatile、无锁。不同 channel 的 initChannel 在不同 event loop 并发进入 → 双方各自 `buildSslContext`（各加载一次 keystore/truststore 文件），last-write-wins，两个合法 SslContext 并存（旧实例被在途连接引用）。功能无损，但启动高峰重复 IO+密钥解析；属 C1 缓存竞态面，资源开销有界，建议 volatile + 局部 double-check 或提前在 start 期预热。

### [CL-028]（C3/C9）SocketClient.recv 重连重试耗尽后循环静默死亡——无失败通知、无状态翻转
- SocketClient.java:197-222：recv 失败 → `RetryHelper.retryExecute(...)` 的返回 future 无任何 whenComplete/异常监听；reconnectPolicy 重试耗尽后 future 异常完成即被丢弃，recv 循环终结（仅 L232 一条 "socket.recv-exit" info），`closed` 仍为 false、onChannelClose 不触发、上层无从感知「客户端已死」。对照正常断连路径 onClose 通知存在。建议补 `.whenComplete((v,e) -> { if (e != null) { onChannelClose 回调 + closed 翻转/告警 } })`。

### [CL-029]（C9）TencentEmailSender 发送失败吞异常——违反 IEmailSender「不抛即成功」消费契约
- TencentEmailSender.java:187-197：catch TencentCloudSDKException 后，FrequencyLimit/EmailAddressIsNULL 静默 return，其余错误仅 LOG.error 后 return——不抛。JavaEmailSender.java:163-167 注释明示消费方契约：「IEmailSender 按"不抛即成功"消费（nop-auth bindEmail 无条件 setEmailSent(true)）」——同接口两实现对失败可见性语义相反，MFA 发码链在 tencent 实现下会误信已发送。属错误处理/行为一致性维度，建议按 JavaEmailSender 的「记录后重抛」口径对齐（FrequencyLimit 等白名单语义需 owner doc 裁定）。

### [CL-030]（C4）SocketServer 默认 executor=GlobalExecutors.cachedThreadPool——每连接一线程，上限仅由 maxConnections=10 隐式界定
- SocketServer.java:139-141 + ServerConfig.java:22：默认 cachedThreadPool + maxConnections=10 → 每个连接独占一个线程直至断开（processCommand 阻塞读循环）。上限非零即有界，但连接保持型业务（长连接设备）10 个即饱和，且线程模型与 IO 多路复用（nop-netty 路径）并存双轨。C4 维度裁定线程模型选型，资源面仅登记。

### [CL-031]（C2）MqttServerMessageService.dispatch 在 Vert.x event loop 内联执行 consumer——慢消费者阻塞全部 MQTT 连接
- MqttServerMessageService.java:139-153：dispatch 由 publishHandler → wrapHandler → listener 直调链在 Vert.x context 线程执行，`consumer.onMessage` 内联调用且无超时/队列隔离；单个慢消费者阻塞该 event loop 上所有连接的后续 publish。建议投递到平台 executor（io.nop.commons.concurrent.executor）或消息总线异步化。

### [CL-032]（C9）OssFileServiceClient.listFiles 仅取 S3 首页（1000 条）——静默截断，下游误判文件不存在
- OssFileServiceClient.java:52-65：`client.listObjects(bucketName, remotePath)` 单页调用，无 ObjectListing.isTruncated/getNextMarker 翻页循环。桶内前缀下对象 >1000 时静默漏列——`FileServiceResourceStore.getChildren` 及其上游「列表即全量」的消费者拿到不完整清单；对「先列后传」的同步逻辑是数据不完整面。正确性维度缺陷（非资源生命周期），建议补翻页或按包络声明裁剪。

## 维度复核结论（WI 4.3 合并批复核：C6，独立复核员，2026-10-20）

> 复核范围：C6-001…028 全部 28 条（C6-028 条目本体在 C6-round2.md，状态行写 round 文件，判定并入本表）。方法=逐条回码验证 + 时序论证核验 + 严重度校准 + `ai-dev/design/nop-network/file-transfer-design.md` 设计契约对照 + 与 cra-index WI 1.1–4.2 台账、2026-09-22 code-review-full 3.9 前案及本单元 C1/C3/CL 文件同案对照。原发现条目未改动。

### C6-001（本单元唯一 P0）子项复核

| 子项 | 结论 |
|---|---|
| 成功路径从不 close | **核验通过**。JdkHttpClient.doDownloadAsync：`sink.close()` 仅在 catch 分支，成功路径 processDownloadResponse 走 begin→write→finish→verifyAndRename 无任何 close；OkHttpClientImpl.onResponse 同构（`try (Response resp)` 只关 HTTP 体，sink.close 仅 catch）；FileDownloadSink.verifyAndRename（L162-175）不关 out，close()（L177-185）为唯一释放点。 |
| resume 默认值 | **核验通过**。DownloadOptions.java:19 `private boolean resume = true`——默认参数下正常成功下载即泄漏，满足「主路径资源泄漏」P0 定义。 |
| Windows Files.move 句柄占用 | **核验通过**。JDK 在 Windows 以不带 FILE_SHARE_DELETE 的共享模式打开文件；FileOutputStream 未关闭时 `Files.move(..., REPLACE_EXISTING)`（FileDownloadSink L173）对源 .part 抛 AccessDeniedException——Windows 默认配置下载确定性失败（异常落入 catch 关闭流），Unix 系静默泄漏 fd 且锚定已改名 inode。两分支均支撑 P0。 |
| Apache 兜底对照 | **核验通过**。DownloadResponseConsumer.EntityConsumer.releaseResources→sink.close（L125-127）；AbstractStreamResponseConsumer.releaseResources 用 getAndSet(null) 幂等（L126-131）、failed()→releaseResources（L121-123）；HC5 对 success/failed/cancel 三终态均保证 releaseResources——Apache 路径闭合，修复须仅覆盖 JDK/OkHttp 两实现。 |

- **P0 判级维持**：主路径公开 API（file-transfer-design.md active 契约）、默认参数触发、无需并发、每次成功下载净增一个 fd。
- **前案证伪注记**：2026-09-22 code-review-full 3.9-gateway-network 第 2 轮曾将「FileDownloadSink 文件模式主路径」列为确认关闭子域——本轮回码证伪该关闭结论，C6-001 为新发现。
- **连带修复注记**：verifyAndRename 的 `expected == null` 早退分支（无校验源时不改名，.part 滞留、目标不产生）已由本单元 C3-005（P1）立案；C6-001 的 close 收敛修复须与 C3-005 改名修复同批设计。

### 逐条判定表

| ID | 初判 | 判定 | 最终严重度 | 同案命中 / 现状要点 |
|---|---|---|---|---|
| C6-001 | P0 | 保留 | P0 | 子项复核全通过；前轮 3.9 误关闭证伪 |
| C6-002 | P1 | 保留 | P1 | ↔ C3-002/C3-003、C1 文件 CL-001（并入本条②，闭环） |
| C6-003 | P1 | 降级 | P2 | 已知未修复（前案 2026-09-22 3.9 同站点 P2 已复核保留）；后果为可观察失败，与前案判级对齐 |
| C6-004 | P1 | 保留 | P1 | ↔ C3-003；跨单元 WI 3.1 C7-001 本体落点闭环 |
| C6-005 | P1 | 保留 | P1 | ↔ C3 文件 CL-012（内存面互补） |
| C6-006 | P1 | 保留 | P1 | ↔ C3-004、C1 文件 CL-002（闭环） |
| C6-007 | P2 | 保留 | P2 | — |
| C6-008 | P2 | 驳回（并入） | — | ↔ C1-002/C3-008；独有子面 P3 随同 diff |
| C6-009 | P2 | 驳回（并入） | — | ↔ C3-006 同站点同根 |
| C6-010 | P2 | 保留 | P2 | 与 C1-001 同文件互补面 |
| C6-011 | P2 | 保留 | P2 | 复核补强：cancelTask 亦不移除；零生产消费方（嵌入方面） |
| C6-012 | P2 | 保留 | P2 | — |
| C6-013 | P2 | 保留 | P2 | 设计契约对照通过 |
| C6-014 | P3 | 驳回（并入） | — | ↔ C1-001(a) 同站点同本体 |
| C6-015 | P3 | 保留 | P3 | — |
| C6-016 | P3 | 保留 | P3 | 维护面 |
| C6-017 | P1 | 保留 | P1 | — |
| C6-018 | P1 | 保留 | P1 | ↔ C3-024（同文件不同面） |
| C6-019 | P1 | 保留 | P1 | — |
| C6-020 | P1 | 保留 | P1 | ↔ C1-008/C3-018 一工单；独有窗口①成立；同族前案 nosql-cdc（非同站点） |
| C6-021 | P1 | 保留 | P1 | ↔ C3-016（同站点 P2，判级分歧留 7.1） |
| C6-022 | P2 | 保留 | P2 | ↔ C1-013 同根不同交错 |
| C6-023 | P2 | 保留 | P2 | ↔ C3-019 |
| C6-024 | P2 | 保留（升格） | P1 | ↔ C3-015（同站点 P1）判级对齐；↔ C1 文件 CL-005/C3 文件 CL-006（闭环） |
| C6-025 | P2 | 保留 | P2 | ↔ C6-007 同族 |
| C6-026 | P2 | 保留 | P2 | ①↔ C3-020（并入）；②独立成立；↔ C1-012(a)(b) 互补面 |
| C6-027 | P2 | 保留 | P2 | — |
| C6-028 | P2 | 保留 | P2 | 关联前案 3.9 still-live P2-15（相邻面）；同族对照 nosql-cdc（非同站点） |

### 复核统计

- **复核保留 24 + 降级保留 1**（C6-003 P1→P2）= 25 条有效；其中升格校准 1 条（C6-024 P2→P1，与同站点 C3-015 对齐）；**驳回（同案并入）3**（C6-008→C1-002/C3-008、C6-009→C3-006、C6-014→C1-001）；证伪驳回 0。
- **最终保留严重度分布（25 条）**：P0×1（C6-001）、P1×10（C6-002/004/005/006/017/018/019/020/021/024）、P2×12（C6-003/007/010/011/012/013/022/023/025/026/027/028）、P3×2（C6-015/016）。
- 现状标注：新发现×27、已知未修复×1（C6-003）。

## 最终保留项（C6 维度，25 条）

- **P0×1**：C6-001（JDK/OkHttp 下载成功路径不关 sink；修复须同盖两实现并与 C3-005 同批设计）。
- **P1×10**：C6-002（并入 C1-CL-001②）、C6-004（WI 3.1 C7-001 本体落点）、C6-005、C6-006、C6-017、C6-018、C6-019、C6-020（并入 C1-008/C3-018 修复单元）、C6-021、C6-024（并入 C3-015 修复单元）。
- **P2×12**：C6-003、C6-007（与 C6-025 同族统一修复）、C6-010、C6-011、C6-012、C6-013、C6-022（与 C1-013 同批）、C6-023（并入 C3-019 修复单元）、C6-025、C6-026（并入 C3-020/C1-012 修复单元）、C6-027、C6-028。
- **P3×2**：C6-015、C6-016。

### 跨单元回写核对

1. **JdkHttpClient SSE readLine 无读超时（WI 3.1 C7-001 本体落点）**：已立案——C6-004 失效面③ + C3-003（P1）；消费面回写闭环成立。非复核遗漏，无需补立。
2. **IHttpResponse 断连观测（WI 2.3 CL-025 落点）**：本单元实现面已由 C3-002/C3-003 + C6-004③/CL-026 承接（dispatch-log 有落点声明）；平台原语面维持 WI 2.3 CL-025 平台轮归属及「C3-010/C4-003 修复先决」关系。C6 维度无补立义务。
3. **WI 4.1 nop-message 各后端面对照**：MqttServerMessageService 属 nop-vertx-mqtt-server（IMessageService 的 MQTT 桥实现），与 WI 4.1 nop-message 各后端（Debezium/Local 等）零同站点重复；同族关联单列——C6-026 ↔ WI 4.1 MA-05（DebeziumMessageSource 幽灵订阅）同属「订阅生命周期注册/注销配对」族，修复批对齐模式（参照 RpcMessageSubscriptions putIfAbsent/cancel-remove 正确先例）。
4. **WI 2.1 CL-021 feishu 点名核实**：FeishuClient raw 线程面确属 WI 2.1 CL-021 点名移交范围（原条目明列 nop-integration-feishu 1 处）；本单元未重复立案正确；C6-024/C1-011 为该载体上的行为面，修复注记链接载体轮。
5. **check/nosql-cdc.md 前案对照**：C1-008/C6-020（订阅竞态）与 C6-028（停机状态滞留）为该 2026-08-19 前案（Lettuce PubSub P2×2，未修复）在 MQTT 面的同族兄弟——家族关联单列，非同站点，修复批按族对齐。

### 同案命中清单（C6 维度）

同站点并入/合并 8 组：C6-008→C1-002/C3-008、C6-009→C3-006、C6-014→C1-001、C6-020⊂C1-008/C3-018、C6-026①→C3-020、C6-024↔C3-015、C6-023↔C3-019、C6-021↔C3-016（保留双 ID，判级分歧留 7.1）；同根同批 3 组：C6-022↔C1-013、C6-007↔C6-025、C6-002②↔C1 文件 CL-001；跨 campaign 前案 2 处：C6-003↔3.9 P2（已知未修复）、C6-028↔3.9 still-live P2-15（相邻面）；同族非同站点 2 处：nosql-cdc 前案对照、WI 4.1 MA-05 家族关联。
