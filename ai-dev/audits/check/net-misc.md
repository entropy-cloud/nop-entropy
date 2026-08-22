# net-misc 实现代码检查报告

- 检查日期: 2026-08-21
- 模块路径: nop-network/{nop-netty,nop-codec,nop-socket,nop-vertx}
- 文件数: 约 65（src/main/java，实际清点 62 个：nop-netty 31、nop-codec 6、nop-socket 11、nop-vertx 14，其中 3 个为空壳类）
- 覆盖范围声明: 62 个 src/main/java 文件全部逐文件深读（含 3 个仅含类声明空体 的空壳类）。测试代码、target/、`_` 前缀生成文件、_vfs 资源不在范围内。为验证跨模块契约，只读参考了以下范围外代码（未修改）：`nop-format/nop-record-netty` 中 handler 装配顺序（验证 ConnectionInfoHandler 误报）、`nop-kernel/nop-api-core` 的 IMessageConsumeContext/IMessageService 接口、`nop-kernel/nop-commons` 的 RetryHelper，以及本地 m2 仓库中 vertx-mqtt 4.5.26 的 MqttServerConnection/MqttEndpointImpl 字节码（验证 endpoint.accept 语义）。nop-vertx 按任务口径合并 nop-vertx-commons（3）与 nop-vertx-mqtt-server（11）。mqtt-server 与 compress codec 在全仓库内无生产调用方、无测试，发现中已如实注明"可触达性依赖模块被下游使用"。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 3 |
| P1 | 7 |
| P2 | 7 |
| P3 | 7 |

---

## 发现列表

### [P0] GZip/Deflate 压缩 codec 的 decode 从原始压缩流拷贝，解压功能完全失效

- **文件**: `nop-network/nop-codec/src/main/java/io/nop/codec/compress/GZipCompressCodec.java:40-52`、`nop-network/nop-codec/src/main/java/io/nop/codec/compress/DeflateCompressCodec.java:42-54`
- **维度**: D1
- **证据**:
```java
// GZipCompressCodec.decodeBuf（DeflateCompressCodec 同构）
ByteBufOutputStream output = new ByteBufOutputStream(allocator.buffer());
ByteBufInputStream input = new ByteBufInputStream(data);
try {
    GZIPInputStream zip = new GZIPInputStream(input);
    IoHelper.copy(input, output);   // <-- 拷贝源是 input，不是 zip
    zip.close();
    return output.buffer();
```
- **现状**: 构造了解压流 `zip` 但从未从它读取，`IoHelper.copy` 的源是未解压的 `input`。
- **风险**: `decodeBuf`/`decodeBytes` 返回原始压缩字节而非解压数据，解压功能 100% 失效；`encode→decode` 往返不对称，任何调用即返回错误数据（数据错误类 bug，非崩溃）。
- **建议**: 改为 `IoHelper.copy(zip, output)`。
- **误报排除**: 全仓库无该两个类的调用方与测试，不存在"依赖此错误行为"的锁定；decode 语义为解压无歧义。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 确认成立，已修复。两个 codec 的 decodeBuf 将 IoHelper.copy 的拷贝源从原始压缩流 input 改为解压流 zip；nop-codec 新增 junit-jupiter 测试依赖。测试：`nop-network/nop-codec` `TestCompressCodec#testGZipRoundTrip`/`#testDeflateRoundTrip`（修复前 encode→decode 返回原始压缩字节，无法还原原文）。

### [P0] SocketServer 连接注册/移除 key 不一致，每次客户端断开泄漏 connections 条目

- **文件**: `nop-network/nop-socket/src/main/java/io/nop/socket/SocketServer.java:206-209（注册）`、`SocketServer.java:229-240（移除）`、`SocketServer.java:242-246`
- **维度**: D1/D2
- **证据**:
```java
// run() 中注册：getHostAddress() 形如 "127.0.0.1"
String ip = client.getInetAddress().getHostAddress();
String addr = getConnectionKey(ip, port);      // "127.0.0.1:5678"
connections.put(addr, client);

// removeSocket() 中移除：InetAddress.toString() 形如 "/127.0.0.1"
String ip = client.getInetAddress().toString();
String addr = ip + ':' + port;                 // "/127.0.0.1:5678"
connections.remove(addr, client);              // key 不匹配，remove 永远 miss
```
- **现状**: 注册用 `getHostAddress()`，移除用 `InetAddress.toString()`（带 `/` 前缀），两个 key 永不相等。
- **风险**: 每个客户端断开后 Socket 永久残留在 `connections` map（内存泄漏、连接对象无法 GC）；`broadcast()` 持续向已关闭 socket 写入并逐条记错误日志；长运行服务在客户端反复连接/断开（移动网络闪断、恶意连接）下内存耗尽。客户端断开是必然事件，触发路径现实。附带：public `getConnectionKey(Socket)` 用 toString 形式，外部拿它查 map 也必然 miss。
- **建议**: 统一使用 `getHostAddress()`；在测试中补充断连后 `connections` 为空的回归断言。
- **误报排除**: 唯一测试 `TestSocketServer` 未覆盖断连清理；`ConcurrentMap.remove(key,value)` 在 key 不匹配时静默返回 false，无异常暴露此问题。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 确认成立，已修复。removeSocket 与 getConnectionKey(Socket) 的 key 构造统一改用 InetAddress.getHostAddress()，与 run() 注册路径一致。测试：`nop-network/nop-socket` `TestSocketServer#testDisconnectRemovesConnection`（修复前客户端断开后 connections 条目永久残留，getConnection 轮询 10s 后仍返回已关闭 socket；getConnectionKey(Socket) 返回 "/127.0.0.1:port" 形式与注册 key 不一致）。

### [P0] MqttConnection 从不调用 endpoint.accept()，MQTT 服务端无法与任何客户端完成连接

- **文件**: `nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/impl/MqttConnection.java:59-115（init）`、`VertxMqttServer.java:64-66（handleEndpoint）`
- **维度**: D1/D8
- **证据**:
```java
// MqttConnection.init(): 注册了一堆 handler，但全文无 endpoint.accept()
void init() {
    this.endpoint
        .disconnectHandler(ignore -> this.complete())
        .closeHandler(ignore -> this.complete())
        ...
}
// VertxMqttServer.handleEndpoint(): 直接入会话表，也无 accept()
private void handleEndpoint(MqttEndpoint endpoint) {
    sessionManager.addConnection(new MqttConnection(endpoint, mqttHandler));
}
```
- **现状**: 通过反编译本地 m2 的 vertx-mqtt 4.5.26 `MqttServerConnection.handleConnect` 字节码确认：该版本在触发 endpointHandler 前不发送 CONNACK，CONNACK 仅由 `MqttEndpointImpl.accept()` 发出（内部含 "Connection already accepted"/"Need to use the 'accept' method" 状态检查）。本模块两个入口都未调用 accept。`MqttConnection` 中 `volatile boolean accepted = false` 是从未读写的残留字段，表明 refactor 时丢失了 accept 逻辑。
- **风险**: MQTT 客户端发出 CONNECT 后永远收不到 CONNACK，直到客户端侧超时断开；整个 MQTT 接入功能不可用。会话表中同时残留握手未完成的连接（配合下条 removeConnection 缺失进一步泄漏）。
- **建议**: 在 handleEndpoint 中（认证通过后，见下条）调用 `endpoint.accept()`，或至少实现 `endpoint.reject(...)` 拒绝路径。
- **误报排除**: 已 grep 全模块确认无任何 `.accept(` 调用；MqttServerOptions 无 autoAccept 选项（javap 确认字段列表）。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 确认成立，已修复。MqttConnection.init() 在注册完全部断连/消息 handler 后调用 endpoint.accept() 发送 CONNACK（认证分流 authChecker 属 P1 另行处置）；顺带删除从未读写的 accepted 残留字段；模块新增 junit-jupiter 测试依赖。测试：`nop-network/nop-vertx/nop-vertx-mqtt-server` `TestMqttConnection#testEndpointAcceptedOnConnect`（修复前 endpoint.accept() 从不被调用，客户端收不到 CONNACK）；`#testCloseHandlerNotifiesMqttHandlerOnce` 守护既有断连订阅逻辑不回退。

### [P1] RpcMessageHandler 在 channelInactive 后 send 触发 NPE，请求 future 永不完成

- **文件**: `nop-network/nop-netty/src/main/java/io/nop/netty/handlers/RpcMessageHandler.java:101-116（channelInactive 置 channel=null、cancel timer）`、`119-158（send/write）`
- **维度**: D1/D4
- **证据**:
```java
public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    ...
    this.channel = null;                 // 断连后置空
    if (this.timer != null) { this.timer.cancel(false); this.timer = null; }
}

private void write(ResponseFuture future, Object msg) {
    ...
    ResponseFuture old = futures.put(msgId, future);   // 已入表
    channel.writeAndFlush(msg);                        // channel == null → NPE
```
- **现状**: `send()` 只判 `executor == null`（channelRegistered 后永不为 null，channelInactive 不清 executor），断连后 send 会走到 `write` 中 `channel.writeAndFlush` 抛 NPE；NPE 发生在 IO 线程任务内被 Netty 吞掉，已 put 进 `futures` 的请求 future 无人完成，而超时清理 timer 已被 cancel。
- **风险**: 典型路径：NettyTcpClient 自动重连窗口期调用 `sendAsync`——`getConnectFuture()` 返回旧的已成功 future，`doSend` 取旧 channel 的 handler 调 send → 调用方 future 永不完成，`send()`（syncGet）线程永久阻塞，且 futures 表条目泄漏。
- **建议**: `write` 前检查 `channel == null || !channel.isActive()`，直接 `ret.completeExceptionally(new NopException(ERR_CHANNEL_NOT_ACTIVE))`；或 channelInactive 时同时清空 executor。
- **误报排除**: 逐行确认 executor/channel 的赋值/清空时机；确认 channelInactive 与 write 均在同一 EventLoop 串行执行，NPE 源于时序而非并发本身。

### [P1] SocketServer.run() accept 一次异常即退出整个 accept 循环，服务器静默停止服务

- **文件**: `nop-network/nop-socket/src/main/java/io/nop/socket/SocketServer.java:194-227`
- **维度**: D1
- **证据**:
```java
void run() {
    do {
        try {
            Socket client = socket.accept();
            ...
        } catch (Exception e) {
            if (stopped)
                break;
            throw new NopException(ERR_SOCKET_ACCEPT_FAIL, e);   // 非 stopped 一律抛出
        }
    } while (!stopped);
}
```
- **现状**: accept 抛出任何一次非停止期异常（文件句柄耗尽 EMFILE、瞬时网络错误等）都直接 throw，run() 退出；该任务跑在 cachedThreadPool 中，异常仅成为 uncaught 异常，无重启机制，LifeCycle 仍处于 started 状态。
- **风险**: 服务器静默停止接受新连接且无告警、无自愈；存量连接继续工作掩盖故障，直到业务侧发现新连接全部失败。
- **建议**: 对 accept 异常做分类重试（记日志 + 短暂退避后 continue），仅在 stopped 时退出。
- **误报排除**: 确认 executor 为 cachedThreadPool（无 RejectedExecutionHandler 兜底重启）；无其他调用方重启 run()。

### [P1] SocketServer 并发写同一连接无互斥，broadcast/sendTo 与响应写交错损坏报文

- **文件**: `nop-network/nop-socket/src/main/java/io/nop/socket/SocketServer.java:158-184（broadcast/sendTo）`、`256-301（processCommand 响应写）`
- **维度**: D3
- **证据**:
```java
// broadcast（任意调用线程）：
OutputStream os = socket.getOutputStream();
BinaryCommand.writePacketToStream(command, os);   // 无锁
os.flush();

// processCommand（executor 线程）：
BinaryCommand response = handler.onCommand(addr, request);
BinaryCommand.writePacketToStream(response, os);  // 同样无锁
```
- **现状**: 对照 `SocketClient.send` 有 `synchronized (os)` 保护，服务端所有写路径（broadcast、sendTo、processCommand 响应）都没有对同一 socket 的 OutputStream 加锁。`writePacketToStream` 是"写头 + 写体"多次 write，两个线程交错时字节流交叠。
- **风险**: 任意线程调 broadcast/sendTo 与连接处理线程回写并发时，单连接收流字节错乱、后续帧全部解析失败（长度域被污染），触发条件现实（推送场景天然并发）。
- **建议**: 按连接维度对 OutputStream 加锁（如在 connections 中存包装对象），与 SocketClient 对齐。
- **误报排除**: 已确认 broadcast/sendTo 无外部串行化约束，且 SocketClient 侧同构代码有锁，证明作者本意需要互斥。

### [P1] MqttSessionManager.removeConnection 全仓库无调用方，会话表只增不减

- **文件**: `nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/impl/MqttSessionManager.java:22-24`、`VertxMqttServer.java:64-66`
- **维度**: D2
- **证据**:
```java
public void removeConnection(IMqttConnection conn) {
    sessions.remove(conn.getClientId(), conn);   // 定义后从未被调用
}
// 唯一使用点：
private void handleEndpoint(MqttEndpoint endpoint) {
    sessionManager.addConnection(new MqttConnection(endpoint, mqttHandler));
}
```
- **现状**: grep 全仓库确认 `addConnection` 仅在 handleEndpoint 调用，`removeConnection` 零调用。连接断开时 MqttConnection.complete() 只回调外部 `IMqttHandler.onClose`，sessionManager 不感知。
- **风险**: 每个断开的 MQTT 连接在 sessions map 永久残留（内存泄漏）；同 clientId 重连时旧死连接被覆盖属侥幸，但不同 clientId 场景无限累积。
- **建议**: 在 VertxMqttServer 中订阅连接关闭（handler.onClose 回调或自行包装）调用 removeConnection。
- **误报排除**: grep 已覆盖 src/main 与 src/test 全部目录；模块内无其他引用。

### [P1] VertxMqttServer.authChecker 是死字段，MQTT 认证完全未接入

- **文件**: `nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/impl/VertxMqttServer.java:33-46`
- **维度**: D5
- **证据**:
```java
private IMqttAuthChecker authChecker;

@Inject
public void setAuthChecker(IMqttAuthChecker authChecker) {
    this.authChecker = authChecker;   // 仅赋值，全类无任何读取
}

private void handleEndpoint(MqttEndpoint endpoint) {
    sessionManager.addConnection(new MqttConnection(endpoint, mqttHandler)); // 无认证检查
}
```
- **现状**: 提供了 `IMqttAuthChecker` 接口与 `SimpleMqttAuthChecker` 实现（用户名/口令校验），并声明了注入点，但 handleEndpoint 建立连接前从不调用 `checkAuthAsync`，也不读取 `endpoint.auth()`，无 accept/reject 分流。
- **风险**: 一旦 accept 流程被补上（修复前述 P0），当前代码即为认证旁路：配置了 authChecker 也不会生效，任意凭据客户端可接入。属"安全控制存在但未接线"的典型缺陷。另注：SimpleMqttAuthChecker 用 `Objects.equals` 明文比对，属可接受的最小实现。
- **建议**: handleEndpoint 中先 `authChecker.checkAuthAsync(endpoint.auth()...)`，通过后 accept + addConnection，失败 `endpoint.reject(NOT_AUTHORIZED)`。
- **误报排除**: grep 全仓库确认 `checkAuthAsync` 无任何调用点；`accepted` 死字段与 jetlinks 原实现对照进一步佐证 refactor 丢失。

### [P1] MqttConnection.sendAsync 返回 null，违背 IMessageConsumeContext 契约

- **文件**: `nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/impl/MqttConnection.java:188-191`
- **维度**: D8
- **证据**:
```java
@Override
public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
    return null;   // IMessageConsumeContext.sendAsync 是抽象方法
}
```
- **现状**: `IMqttConnection extends IMessageConsumeContext`，`sendAsync` 为无默认实现的抽象方法；实现返回 null 而不是抛 UnsupportedOperationException 或返回失败 future。
- **风险**: 消息框架把 IMqttConnection 作为消费上下文传递，消费方回调内向来源连接回发消息（`ctx.sendAsync(...).thenAccept(...)`）即 NPE。
- **建议**: 要么实现（endpoint.publish + ack），要么显式抛 UnsupportedOperationException。
- **误报排除**: 已读 `nop-kernel/nop-api-core` 的 IMessageConsumeContext 确认无 default 实现。

### [P1] NettySslEngineFactory 忽略 trustStorePath/trustStorePassword，信任管理器用私钥 keystore 初始化

- **文件**: `nop-network/nop-netty/src/main/java/io/nop/netty/ssl/NettySslEngineFactory.java:35-78`
- **维度**: D5/D8
- **证据**:
```java
KeyStore ks = KeyStore.getInstance(sslKeyStoreType);
in = new FileInputStream(sslKeyStore);          // 只加载 keyStore
ks.load(in, passChs);
...
TrustManagerFactory tmf = TrustManagerFactory.getInstance(sslAlgorithm);
tmf.init(ks);                                   // 用 keyStore（含私钥）当 trust store
// SslConfig 中 trustStorePath/trustStorePassword 全文未被读取
```
- **现状**: `SslConfig` 暴露 `trustStorePath`/`trustStorePassword` 两个配置项，但工厂从不加载 trustStore，TrustManagerFactory 直接用 keyStore 初始化。
- **风险**: 用户按配置语义配置独立 trustStore 时配置静默失效：客户端模式下若 keyStore 内无受信 CA 则握手失败（fail-closed）；用户被迫把 CA 塞进含私钥的 keystore（密钥管理上错误做法）。配置契约与实现不匹配且涉及 TLS 信任边界。
- **建议**: 存在 trustStorePath 时加载 trustStore 并 `tmf.init(trustStore)`，否则回退系统默认 `tmf.init((KeyStore) null)`；缺失口令/文件时用 NopException 报错码而非裸 IllegalArgumentException。
- **误报排除**: 已通读 SslConfig 全部字段并 grep trustStore 引用，确认仅 getter/setter 存在。

---

### [P2] AbstractByteBufCodec/IPacketCodec 字节入口未 release 编解码产出的 ByteBuf

- **文件**: `nop-network/nop-codec/src/main/java/io/nop/codec/support/AbstractByteBufCodec.java:19-29`、`nop-network/nop-codec/src/main/java/io/nop/codec/IPacketCodec.java:41-45`
- **维度**: D2
- **证据**:
```java
public byte[] encodeBytes(byte[] data) {
    ByteBuf buf = encodeBuf(Unpooled.wrappedBuffer(data), UnpooledByteBufAllocator.DEFAULT);
    byte[] bytes = ByteBufUtil.getBytes(buf);   // getBytes 不消耗引用
    return bytes;                                // buf（refCnt=1）无 release
}
// IPacketCodec.encodeToBytes 同构：
ByteBuf buf = UnpooledByteBufAllocator.DEFAULT.buffer();
encodeToBuf(message, buf);
return ByteBufUtil.getBytes(buf);               // 未 release
```
- **现状**: `encodeBuf/decodeBuf` 的契约是返回新分配、调用方负责释放的 buffer（压缩实现异常路径自己 release，正常路径移交调用方），但 bytes 入口拿到后只 getBytes 不 release。
- **风险**: 引用计数泄漏。当前硬编码 UnpooledByteBufAllocator（heap、GC 兜底），实际内存影响有限，但开启 Netty leak detector 会刷泄漏告警，且一旦换用池化分配器即成为真实泄漏——这正是 Netty 最常见的 bug 形态，与平台"ByteBuf 引用计数管理"重点直接冲突。
- **建议**: try/finally 中 `buf.release()`（getBytes 后释放）；`Unpooled.wrappedBuffer(data)` 输入同样处理。
- **误报排除**: 已核对压缩实现（allocator.buffer() 分配、refCnt=1）与 ByteBufUtil.getBytes 语义（纯读取）。

### [P2] SocketServer.doStart 抛 bare RuntimeException，违反平台错误处理规范

- **文件**: `nop-network/nop-socket/src/main/java/io/nop/socket/SocketServer.java:126-129`
- **维度**: D7
- **证据**:
```java
} catch (IOException e) {
    LOG.info("nop.socket.start-server-fail:host={},port={}", ...);  // 失败仅 info 级
    throw new RuntimeException("nop.socket.start-server-fail:" + config.getPort(), e);
}
```
- **现状**: 同模块定义了 `SocketErrors.ERR_SOCKET_CONNECT_FAIL` 等错误码且其余路径均用 NopException，此处用裸 RuntimeException 且 message 拼接中文语义码；失败日志还是 info 级。
- **风险**: 违反平台"禁止 bare RuntimeException / 框架层用 NopException + ErrorCode"两档策略；上层无法按 ErrorCode 归类处理。
- **建议**: 改为 `throw new NopException(ERR_SOCKET_ACCEPT_FAIL, e).param(ARG_HOST,...).param(ARG_PORT,...)`，日志升为 error。
- **误报排除**: grep 全模块唯一一处 bare RuntimeException；SocketErrors 常量可直接复用。

### [P2] SocketClient.heartbeat 失败仅 LOG.trace，连接故障不可见且不触发恢复

- **文件**: `nop-network/nop-socket/src/main/java/io/nop/socket/SocketClient.java:141-151`
- **维度**: D4
- **证据**:
```java
private void heartbeat() {
    ...
    try {
        send(newHeartbeat(), true);
    } catch (Exception e) {
        LOG.trace("nop.socket.send-heart-fail", e);   // 心跳失败仅 trace
    }
}
```
- **现状**: 心跳写失败（连接已断的最主要信号）被吞成 trace 级日志；call 模式（未启动 recv 循环）下没有任何机制感知或触发重连。
- **风险**: 连接死亡后客户端长期"假活"：心跳静默失败、下一次业务 call 才以写失败暴露；默认 trace 关闭时完全不可见。
- **建议**: 至少升为 warn 并累计失败计数，连续失败达到阈值时主动 cleanup + 触发 autoReconnect。
- **误报排除**: 已确认 send 抛 NopException 会被此 catch 捕获；recv 循环模式的重连与此路径独立。

### [P2] MqttServerMessageService 实现 IMessageService 但 sendAsync/subscribe 返回 null

- **文件**: `nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/bus/MqttServerMessageService.java:28-36`
- **维度**: D8
- **证据**:
```java
public class MqttServerMessageService implements IMessageService {
    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        return null;
    }
    @Override
    public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
        return null;
    }
}
```
- **现状**: 类带 `@Inject` setter、按可注册消息服务 bean 的形态实现公共接口，但两个方法都返回 null。
- **风险**: 一旦被装配为 IMessageService 实现，任何消息收发调用即 NPE；对调用方而言失败模式是崩溃而非显式异常。
- **建议**: 未实现前抛 UnsupportedOperationException 并注明 TODO，或完成实现（借助 sessionManager 向订阅主题的连接 publish）。
- **误报排除**: 已确认 IMessageService 接口这两个方法无 default 实现。

### [P2] VertxMqttServer.doStop 异步回调读取已置 null 的 mqttServer 字段

- **文件**: `nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/impl/VertxMqttServer.java:68-81`
- **维度**: D1
- **证据**:
```java
protected void doStop() {
    if (mqttServer != null) {
        mqttServer.close(res -> {                 // 回调异步执行
            if (res.failed()) { LOG.error(...); }
            else { LOG.info("...port={}", mqttServer.actualPort()); }  // 读字段
        });
        mqttServer = null;                        // 注册回调后立刻置 null
        listenFuture = null;
    }
}
```
- **现状**: close 的回调在 event loop 线程异步执行，而 doStop 同步先把字段置 null；回调内两处引用 `mqttServer.actualPort()`。
- **风险**: 停服时回调必 NPE，成功/失败日志均丢失，NPE 被 vertx 吞掉，停服状态不可观测。
- **建议**: 回调外先取局部变量 `MqttServer s = mqttServer;` 再 `s.close(res -> ... s.actualPort())`。
- **误报排除**: 确认 lambda 捕获的是外部字段而非局部变量。

### [P2] BinaryCommand 对 len∈(4,8) 的畸形帧抛 BufferUnderflowException

- **文件**: `nop-network/nop-socket/src/main/java/io/nop/socket/BinaryCommand.java:146-163`
- **维度**: D1
- **证据**:
```java
int len = buf.getInt(0);
if (len < minLen) throw new IOException("...packet-is-too-small...");  // minDataLen 默认 0，拦不住 5..7
...
if (len > 4) {
    readFully(is, dataBuf.array(), 4, len - 4);   // len=5..7 时只补 1..3 字节
}
dataBuf.rewind();
return readFrom(dataBuf);   // remaining>0 时 getShort(cmd)+getShort(flags) → BufferUnderflowException
```
- **现状**: 协议帧结构要求 body（cmd+flags+data）长度要么 0（len=4）要么 >=4（len>=8）；minDataLen 默认 0 无法拦住 len=5,6,7，readFrom 对 remaining∈(0,4) 直接 getShort×2。
- **风险**: 不可信对端发送 len∈(4,8) 的帧触发 unchecked BufferUnderflowException：服务端被兜底 catch 断连（错误日志误导为处理失败）；客户端 recv 被包装为 ERR_SOCKET_READ_FAIL。属协议健壮性缺口而非崩溃级。
- **建议**: 读包时校验 `len == 4 || len >= 8`（或将 minDataLen 语义定为总包长且默认 8），不满足抛带上下文的 IOException。
- **误报排除**: 已核对 `getLength()`（空包=4，否则 >=8）证明合法取值集合不含 5..7；ClientConfig/ServerConfig 默认 minDataLen=0。

### [P2] ByteBufHelper.writeBuf 非 heap 分支忽略 start/length 参数

- **文件**: `nop-network/nop-codec/src/main/java/io/nop/codec/util/ByteBufHelper.java:42-54`
- **维度**: D1
- **证据**:
```java
public static void writeBuf(OutputStream os, ByteBuf buf, int start, int length) throws IOException {
    ...
    if (buf.hasArray()) {
        int baseOffset = buf.arrayOffset() + start;
        os.write(bytes, baseOffset, length);          // 尊重 start/length
    } else {
        byte[] bytes = ByteBufUtil.getBytes(buf);     // 恒取 readerIndex 起的全部可读字节
        os.write(bytes);                              // start/length 被忽略
    }
}
```
- **现状**: direct buffer 时输出内容与 heap 分支不一致：无论调用方指定什么区间都写整个可读区间。
- **风险**: 当前仓库内调用方（GZip/Deflate 压缩）恰好都用全量参数（readerIndex/readableBytes）故未暴露；任何后续调用者传部分区间 + direct buffer 即写入错误数据。heap 分支用 capacity 做边界检查也与 readerIndex 语义混杂。
- **建议**: 非 heap 分支改为 `ByteBufUtil.getBytes(buf, start, length)`，边界检查基于 readableBytes。
- **误报排除**: 已 grep 确认现有调用方均走全量参数，故未列更高严重度。

---

### [P3] 多处死代码/空壳类：NettyHttpClient、NettyHttpServer、NettyHttpHelper 及无调用方的工具

- **文件**: `nop-network/nop-netty/src/main/java/io/nop/netty/http/NettyHttpClient.java`、`NettyHttpServer.java`、`nop-network/nop-netty/src/main/java/io/nop/netty/utils/NettyHttpHelper.java`、`nop-network/nop-netty/src/main/java/io/nop/netty/handlers/HeartbeatHandler.java`、`nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/message/MqttMessageBuilder.java`
- **维度**: D6/维护性
- **证据**: NettyHttpClient/NettyHttpServer/NettyHttpHelper 为仅含空类体的空壳；HeartbeatHandler、MqttMessageBuilder 全仓库无调用方；`NettyHelper.writeFully` 与 `ByteBufHelper.writeFully` 是完全重复实现且均无调用方（其非阻塞 channel 忙等缺陷因此暂不可达）。
- **现状**: 占位/残留代码随版本发布。
- **风险**: 误导使用者（以为有 Netty HTTP 客户端实现）；重复实现漂移风险。
- **建议**: 删除或补全实现；若保留 writeFully 需处理非阻塞 channel `write()==0` 的忙等。
- **误报排除**: 均经全仓库 grep 确认零调用。

### [P3] BinaryMqttMessageBean 遮蔽父类同名字段 mqttProperties

- **文件**: `nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/message/BinaryMqttMessageBean.java`、`MqttMessageBean.java`
- **维度**: D8/维护性
- **证据**: 父类 `MqttMessageBean` 声明 `private MqttProperties mqttProperties;`（无 @JsonIgnore），子类 `BinaryMqttMessageBean` 再声明同名 `private MqttProperties mqttProperties;` 并覆盖 getter（@JsonIgnore）。
- **现状**: 子类字段遮蔽父类字段，父类字段恒为 null；JSON 序列化按属性名合并时父类声明缺少 @JsonIgnore，行为依赖 Jackson 版本细节。
- **风险**: 反射/序列化工具读写到不同字段导致数据丢失或序列化 netty MqttProperties 失败。
- **建议**: 字段只保留在父类（或只保留在子类），统一 @JsonIgnore。

### [P3] NopVertx._instance 为可变静态字段且非 volatile

- **文件**: `nop-network/nop-vertx/nop-vertx-commons/src/main/java/io/nop/vertx/commons/NopVertx.java:18-30`
- **维度**: D3
- **证据**: `private static Vertx _instance;` 由 `registerInstance` 写入、`instance()` 读取，无 volatile/锁。
- **现状**: 启动线程注册后，工作线程按 JMM 不保证立即可见（无 happens-before）。
- **风险**: 理论数据竞争；实际 JVM 上静态引用通常最终可见，表现为偶发 ERR_VERTX_NOT_INITIALIZED 的窗口。
- **建议**: 改为 `private static volatile Vertx _instance;`。

### [P3] RpcMessageHandler.checkTimeout 与 channelInactive 存在 timer 竞态 NPE

- **文件**: `nop-network/nop-netty/src/main/java/io/nop/netty/handlers/RpcMessageHandler.java:76-116`
- **维度**: D3
- **证据**: `checkTimeout` 中 `LOG.debug(..., channel.remoteAddress(), ...)`；`channelInactive` 先 `this.channel = null` 再 `timer.cancel(false)`，cancel 不能撤回已在执行/已到期的任务。
- **现状**: 已提交的 checkTimeout 任务在 channel 置 null 后执行会 NPE，该 NPE 会使 scheduleWithFixedDelay 的后续调度终止。
- **风险**: 实际影响小（此时 timer 本应终止），NPE 由调度器记录 warning。
- **建议**: checkTimeout 内先取局部 `Channel ch = channel; if (ch == null) return;`。

### [P3] NettyTcpServer 未启用 useChannelGroup 时 send/getChannelById NPE

- **文件**: `nop-network/nop-netty/src/main/java/io/nop/netty/tcp/NettyTcpServer.java:240-304`
- **维度**: D8
- **证据**: `channelGroup` 仅在 `config.isUseChannelGroup()` 时创建；`getChannelById`/`sendToAnyChannel`/`getChannelInfos` 直接遍历该字段。
- **现状**: 未启用 channelGroup 时调用这些公开方法 NPE，而非"无可用连接"语义错误。
- **风险**: 配置组合错误时以 NPE 形式暴露，错误信息不可诊断。
- **建议**: 入口判空抛 `ERR_NETTY_NO_AVAILABLE_CHANNEL`。

### [P3] aggMaxMessageSize 默认值 1014*1024 疑似 1024*1024 笔误

- **文件**: `nop-network/nop-netty/src/main/java/io/nop/netty/config/NettyBaseConfig.java:28`
- **维度**: D1（轻微）
- **证据**: `private int aggMaxMessageSize = 1014 * 1024;`
- **现状/风险**: 与常见 1MB 意图相差 10KB，仅导致默认帧上限略小，无功能危害；疑为 typo。
- **建议**: 确认意图后改为 1024 * 1024 或注释说明。

### [P3] PacketCodecHandler 吞掉 encode 异常时，请求 future 悬挂至超时

- **文件**: `nop-network/nop-netty/src/main/java/io/nop/netty/handlers/PacketCodecHandler.java:44-66`
- **维度**: D4
- **证据**:
```java
} catch (RuntimeException e) {
    encodeErrorCount++;
    if (encodeErrorCount > maxEncodeErrorCount) throw e;
    LOG.warn("nop.netty.warn.ignore-encode-msg-fail:...");
    out.resetWriterIndex();          // 消息被静默丢弃
}
```
- **现状**: 未超阈值时编码失败被吞、消息不发出，但上游 RpcMessageHandler 已登记 msgId 等待响应。
- **风险**: 请求方只能等满 timeout 才收到失败，且无错误上下文（设计上有 allowedEncodeErrorCount 后门，属权衡但缺少对请求方的快速失败通知）。
- **建议**: 吞异常时同步让 promise 失败（out 挂到 ctx 写回失败或将 writeFuture 关联），避免悬挂。

---

## 附注

- 检查过程中对以下疑似点做了误报排除，未列入报告：ConnectionInfoHandler 的 `(ByteBuf) msg` 强转（nop-record-netty 中装配顺序保证其位于 PacketCodecHandler 之前，收到的一定是 ByteBuf）；MessageCounterHandler 的 null 检查缺失（同链路 attr 必已初始化）；TestFragmentChannelHandler 的 readRetainedSlice（retain/write 后由 Netty 释放，正确）；RpcMessageHandler futures 的 HashMap 无锁（读写均在单 EventLoop 线程，注释已声明）；NettyTcpClient 重连调度的 isStopping 双重检查；SocketClient.heartbeat 的 `interval/2` period（`>1` 门卫保证 period>=1）。
- mqtt-server 模块（P0-3、P1-4/5/6、P2-4、P3-2 关联）整体呈"refactor 未完成"状态：仓库内无生产调用方、无测试，四组缺陷（accept 缺失、认证未接线、会话泄漏、接口返回 null）互相印证为同一次半成品迁移的产物，建议作为一个整体修复项处理。
