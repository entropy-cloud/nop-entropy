# nop-network：HTTP 客户端文件传输与 MQTT 消息

> 受众：使用 `IHttpClient` 做文件上传/下载、或使用 `nop-vertx-mqtt-server` 接入 MQTT 设备的开发者。
> 本文只保留使用规范；协议细节以源码 `io.nop.http.api.utils.FileTransferHelper` 的 javadoc 与错误码为准。

## IHttpClient 文件上传

`uploadAsync(request, inputFile, options, cancelToken)` 由 Apache/JDK/OkHttp 三个实现共同支持，行为等价（相同请求在三个实现上产生相同线上协议）。

### BINARY 模式（默认）

```java
UploadOptions options = new UploadOptions(); // mode 默认 BINARY
IHttpResponse res = client.uploadAsync(
        HttpRequest.put("https://server/api/file"), // url/method 以 options.httpMethod 为准
        new DefaultHttpInputFile(localFile), options, null)
    .toCompletableFuture().get(30, TimeUnit.SECONDS);
```

- 线上协议：`PUT`（可配 `options.setHttpMethod("POST")`）+ `application/octet-stream` 原始字节流。
- 元数据头：`x-file-name`（URL 编码）、`x-file-length`、`x-file-mode: binary`、`x-file-sha256`（`options.isComputeSha256()` 默认 true，客户端本地预计算全文件摘要，服务端据此校验）。
- 要求 `IHttpInputFile.toFile() != null`（文件背书流式发送）；进度经 `options.setProgressListener(...)` 回调。
- 自建服务端按 header 还原文件名/长度并校验 sha256（协议契约见设计文档）。

### BASE64_FORM 模式

```java
UploadOptions options = new UploadOptions();
options.setMode(UploadMode.BASE64_FORM);
// 字段名默认 file/filename，可用 setFieldName/setFileNameField 调整
```

- 线上协议：标准 `multipart/form-data`，文件内容整体 base64 后作为**普通文本字段**提交——用于只收文本表单字段的网关/遗留服务。
- base64 文本整体构入请求体（内存约为文件 1.33 倍），仅适用于中小文件。
- **流关闭契约（C6-005）**：BASE64_FORM 读取文件流必须以 try-with-resources 包裹 `IoHelper.readBytes(inputFile.getInputStream())`——`readBytes` 不关闭输入流（`readAllBytes` 语义，关闭责任在调用方），三实现（JDK/OkHttp/Apache）统一。`IoHelper.readBytes` 行为零变更。

## IHttpClient 文件下载

`downloadAsync(request, targetFile, options, cancelToken)`：

- **断点续传**（`options.isResume()` 默认 true）：下载写入目标旁的 `{name}.part`；中断后再次调用自动带 `Range: bytes=N-` 续传（206 追加）；服务端不支持 Range（回 200）自动整体重传；`416` 自动删除 `.part` 重下。**原子 rename 无条件强制（C3-005，错误以成功交付族）**：`verifyAndRename` 对校验来源缺失（expected == null）仅豁免校验、不豁免落盘收尾——.part 必须改名为目标文件，仅校验失败分支删除 .part；下载完成信号与产物落地原子（三实现行为一致）。
- **future 终结契约（C3-001，R21 future-永不完成族）**：`onResponse` 回调体内任何同步异常（如 body 读取中断连）必须 `completeExceptionally` 终结返回 future——禁依赖上层超时兜底；OkHttp 回调返回的 stage 被框架丢弃，未终结即调用方永久挂起。`.part` 布局跨实现一致，可换实现续传。
- **完整性校验**（优先级：显式 > 响应头 > sidecar）：
  1. `options.setExpectedSha256(hex)` / `setExpectedSha1(hex)`
  2. 响应头 `x-content-sha256`（hex；兼容 `x-amz-checksum-sha256` base64）
  3. sidecar：自动 `GET {url}.sha256` → `{url}.sha1`（Maven/Node 风格 `<hex>  <filename>`），`options.isFetchSidecarChecksum()` 默认 true，探测失败不阻断
- 校验失败 → 删除 `.part`、以 `nop.err.http.download-checksum-mismatch` 失败；`options.isRequireChecksum()` 要求必须有校验来源，否则 `nop.err.http.download-no-checksum`。
- 进度：`options.setProgressListener(...)` 按 `offset+received / total` 回调。

## MQTT 服务端消息

`nop-vertx-mqtt-server`：

- **下行**：`IMqttConnection.sendAsync(topic, message, options)` 向该连接 publish（QoS1）。payload 支持 String（UTF-8）/ byte[]，其余对象 JSON 序列化；连接断开抛 `nop.err.mqtt.not-connected`。
- **路由下发**：`MqttServerMessageService`（bean）实现 `IMessageService`。`sendAsync(topic, msg)` 会向所有订阅匹配（MQTT `+`/`#` 通配）的连接下发；无匹配连接时空广播正常完成。
- **上行订阅**：`MqttServerMessageService.subscribe("cmd/#", listener)` 注册进程内监听；设备上行 publish 按同一通配语义分发，`data` 为 payload 的 UTF-8 文本；二进制场景直接实现 `IMqttHandler`。
- 会话接管（同 clientId 新连接踢旧连接）、断连清理（连接与订阅登记）由 `MqttSessionManager` 维护。

## SMS areaCode 与 Feishu ACK 契约（P1-6.8-002/004，plan 2392）

- **SMS areaCode 语义（Yunpian，Tencent parity）**：areaCode null/空（平台主链路 MfaCodeSender 不设置——国内短信常态）时仅用裸 mobile 号；非空时剥离 "+" 前缀后拼接。杜绝 null 拼接出 "null138…" 数据损坏形态。
- **Feishu Stream DATA→ACK 契约**：DATA 帧处理后回发 ACK 帧（headers 原样复制——携带 message_id/log_id 等回执定位信息；requestId 沿用）；FeishuInboundMessage.headers 透传帧头供下游回执。零 ACK 回发会导致服务端重复投递/反复断连。

## Feishu Stream 通道生命周期并发契约（nop-integration-feishu）

- **send 有界预算（C3-015）**：`FeishuClient` 的 send（心跳/握手/ACK 三调用点）统一有界——`JdkStreamTransport.send` 以 `sendTimeoutMillis`（缺省 15s，`FeishuClient.setSendTimeoutMillis` 部署可调）执行有界 get；半开连接上无界 get 会占死心跳+重连唯一 scheduler 线程（整体死锁）。send 失败经失败处理单点收敛：`connected=false` + close（尽力而为，5s 预算与 send 解耦）+ warn（含站点维度）+ 重连调度。connect 阻塞段经 `IStreamTransport.connectAsync` 移出 scheduler（包私有 seam 的 default 方法级扩展，假体经 default 委托同步 connect）。
- **重连单飞与世代守卫（C1-009/C6-021）**：`onClose`/`onError` 双终态经 CAS 单飞去重为恰一在飞重连；`lifecycleGeneration` 在 start（connect 调用前）与 stop 各递增一次，重连/完成回调/`onOpen` 比对连接前记录的世代快照——过期世代回调丢弃、stop 后完成的建连立即关闭（孤儿消除，`connectCount - closeCount ≤ 1` 不变式）、晚到 `onOpen` 不置 connected 不发握手。重连前先 close 旧连接（fd 不依赖对端断开）。
- **stop 停机语义**：stop 递增世代 + cancel 心跳 + close transport；stop 返回后任何旧世代在途建连/回调确定性失效（不对新世代 promise/连接产生影响）。`stop→start` 重启重置世代并重建连接。

## SFTP 客户端超时契约（nop-integration-sftp，C3-024）

- `SftpConfig.timeoutMillis`（缺省 15s，0=jsch 无限——禁用）单值承载三段接线：`session.setTimeout(...)`（socket SO_TIMEOUT，per-read 读段包络——活动传输每次读重置计时，长传输无总时长预算为 by design）+ `session.connect(ms)`（建连上界，banner 黑洞等）+ `channel.connect(ms)`（channel 开启上界）。超时异常经既有 `ERR_SFTP_CONNECT_FAIL` 包装外抛。
- **边界注记**：构造期「session 已连、channel 未开」失败回收面归 R2-20（本批 connect(ms) 抛超时路径即其触发前提）。
- 无连接级 keep-alive/重连语义——`SftpClientFactory.newClient` 每操作新建客户端（构造即建连）。

## Kafka 消费生命周期语义（P1-4.4-M01..M03，plan 2396）

三条契约：①**suspend/resume = 期望态标志**（线程安全 setter）——消费循环每轮对当前分配分区应用 pause/resume，rebalance listener 对新分配分区按标志补应用（调用线程不再直操 consumer，rebalance 后暂停不失效）；②**seek = onPartitionsAssigned 驱动**——ALWAYS_SEEK_TO_END 每次 assigned 执行，INIT_SEEK_TO_BEGIN/END 与 seekToTime 仅首次 assigned 执行（ALWAYS 语义下 seekToTime 随 ALWAYS 每次），seekToMessage fail-loud；③**cancel 顺序 = 先停线程后 close**——task.stop()（wakeup）→ executor.shutdown/awaitTermination → consumer.close()（消费线程退出后才关闭，杜绝 CME 吞异常导致的 consumer 泄漏）。

## nop-cluster 语义（P1-4.4-C1..C4，plan 2395）

四条契约：①leader 选举租约配置绑定使用 IoC 认知的 `@cfg:` 前缀（`@cfg;` 笔误会使装配即启动失败）；②LeastActive 负载均衡窗口回绕取模（`(startIndex+currentCursor)%n`，多实例回绕场景不再越界）；③SentinelFlowControlRunner.runAsync 的 `ContextUtil.enter/exit` 无条件配对（成功路径不再泄漏发起线程 context 栈；entry 的 exit 在 whenComplete/失败分支独立配对）；④Nacos 服务发现 serviceName 无组名（不含 `@@`）时原样保留、groupName 为 null（对齐 getNormalizedServiceName 守卫）。

## nop-search 引擎语义（P1-4.4-S-02..06，plan 2394）

LuceneSearchEngine（先修批后）五条契约：①空 query（`SearchRequest.query` 省略）= match all，可与 tags/filter 组合浏览；②filter `not` 组合条件为补集语义（NOT 补 MatchAllDocs 锚点）；③写入失败回滚时自动清理 writer/SearcherManager 缓存——单条坏文档不再永久打死 topic（下次访问重建）；④`ISearchEngine.getDoc(topic, docId)`（**API 签名变更**：原 `getDoc(docId)` 全量遍历取首命中=跨 topic 串读）——限定单 topic 查找；⑤`SearchEngine__indexDir` fail-closed：需要 admin 角色（`nop.err.search.no-permission`），直解析路径受 `nop.search.index-dir-roots` 白名单约束（未配置=仅工作目录相对路径；resourceLocator 解析豁免）。pattern 正则回溯限制为 admin 自伤面，随拆分计划处理。

## JDK client 503 响应语义（P1-check-netrpc-24，plan 2391）

JdkHttpClient.toHttpResponse 不再把 HTTP 503 映射为 NopConnectException——503 按正常响应送达（status/body/headers 完整），与 Apache/OkHttp 实现对齐（此前仅 JDK 把 503 误判为连接失败且丢 body）。旧「某些 MAC/JDK 连接失败表现为 503」workaround 已移除：现代 JDK 连接失败正确抛 ConnectException（经 wrapException 归一化为 NopConnectException），调用方需自行处理 503 状态（重试/降级语义属调用方域）。

## 网关 HTTP 过滤语义（P1-check-gw-49/86，plan 2390）

GatewayHttpFilter 两条契约：①**路由预匹配后置**——path 级路由预匹配（`GatewayHandler.hasRouteFor`）无命中时请求体零消费直接放行下游；请求体解析按 content-type 分流，仅 JSON（或未声明）content-type 走严格解析（非法 body fail-fast 5xx），multipart/表单等不再炸滤器（body 不向下游透传，data=null）；异步段异常经 `exceptionally` 兜底写 5xx，响应已提交（streaming 中途失败）时只记日志不二次写出。②**OAuth redirect 凭证下发**——code+redirect_uri 分支在 sendRedirect 前初始化用户上下文并下发 auth cookie（新会话凭证；redirect 提交后 header/cookie 必丢），消除 SSO 回调登录循环；redirect 后下游对已提交响应的写入为无害噪音（现状既有行为）。

## 响应体阶段取消与读超时契约（4.3-C3-002 族 / 4.3-C6-004，plan 2505）

JDK 客户端的 `sendAsync(..., BodyHandlers.ofInputStream())` future 在**响应头到达即完成**——cancelToken 只绑 headers 阶段时，body 阶段的取消/静默均无响应点。修复后的契约（下载与 SSE body 阶段，jdk 叶内）：

- **body 阶段取消响应**：下载读循环每迭代检查 `cancelToken.isCancelled()`，取消即 `CancellationException` 终态并关闭 sink/in（幽灵下载消除）；SSE cleanup 显式关闭登记的底层 body 流——阻塞中的 `readLine` 抛 IOException 解除（`future.cancel(true)` 对 headers 后已完成的 future 是 no-op，显式关流是唯一出口）。取消监听经 `FutureHelper.bindCancelToken` 收敛，且 **promise 生命周期 = body 阶段终态**（非 headers 到达）——监听器覆盖整个 body 阶段、按 promise 配对移除（无累积契约保持）。
- **body idle 读超时**：`nop.http.client.jdk.body-idle-timeout`（`JdkHttpConfigs.CFG_HTTP_BODY_IDLE_TIMEOUT`，缺省 `120000` ms，独立于只覆盖 headers 的 request timeout）——`BodyIdleWatchdog`（globalTimer 短任务巡检，任务体仅「进度/取消判定 + 关流」）超阈或取消时从独立线程关闭底层流，阻塞 read/readLine 抛 IOException 进入既有错误路径（SSE 静默 >30s 为常态，预算独立于 headers 语义）。`<= 0` 关闭看门狗（不推荐）。
- **缺省 120s 边界声明**：无 keepalive 且单次静默间隔合法超过 120s 的 SSE 流，在缺省配置下将由「读取滞留后续恢复」变为 **120s 关流错误**（IOException → onError）——这是有界终止换取的显式取舍；合法长静默流的缓解面 = 调高 `nop.http.client.jdk.body-idle-timeout`，或置 `<= 0` 关闭看门狗回到滞留形态（此时上层聚合层预算兜底，见 `nop-ai.md` 流式契约）。SSE demand 等待预算同理：`AbstractServerEventSubscription` 基类缺省 120s，停滞订阅者以 `ERR_HTTP_SSE_DEMAND_TIMEOUT` 显式终态化（可经 `demandWaitTimeoutMillis()` 钩子收紧）。
- **三出口统一关流**：取消出口（cleanup）、416 重试分支、SSE cancel-at-entry 分支——三处 return/终态前均关闭遗弃的响应体（连接/socket 归还，对端可观测 EOF）。
- **反压不可中断面收敛**：demand 等待有界化（基类 `waitForDemand` 预算 + 停滞终态）；apache SSE 事件投递经有界队列 + 专用 `nop-sse-dispatch` daemon 消费线程（HC5 I/O reactor 线程不再内联 wait）；jdk parse 循环可关闭化；残留长阻塞承载线程为 daemon 形态（挂死不阻断 JVM 退出）。
- 测试锚点：`TestBodyPhaseCancel_C3_002`（6 条，raw socket/HttpServer 真实装配链）、`TestServerEventSubscriptionDemandTimeout_C6_004`、`TestApacheSseReactorDecouple_C6_004`、`TestJdkHttpClientCancelListenerCleanup_C3_012`（2499 注册面零回退）；消费面全链（聚合层 + 传输层双层预算）端到端见 `nop-ai.md` 流式契约节与 `TestStreamFullChainEndToEnd_C3_001`。

## 相关文档

- `../02-core-guides/rpc-and-distributed-rpc.md`（RPC 分层调用模型，`/px/` 代理）
- `./nop-credential.md`（出站 OAuth 客户端引擎使用 `IHttpClient`）
