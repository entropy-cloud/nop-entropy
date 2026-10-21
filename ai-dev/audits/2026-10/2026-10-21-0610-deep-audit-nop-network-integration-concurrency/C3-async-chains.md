# C3 异步链路正确性 — nop-network-integration（WI 4.3）

- 审计员：C3 维度盲审初审员（批次 1/2）
- 日期：2026-10-21
- 方法论：`ai-dev/skills/concurrency-resource-audit-prompt.md`（F1–F11 + C3 维度正文 + 附录 A 13 字段）
- 基线对照：`_tmp/cra-baseline-nop-network-integration.txt` C3a/C3b 节（仅用于圈定重点，全部结论基于 live code 人工复现）

## 审计范围与口径

**批次 1/2 覆盖声明**：本批次覆盖 `nop-network/nop-http/` 全 6 叶（nop-http-api、nop-http-client-apache、nop-http-client-jdk、nop-http-client-oauth、nop-http-client-okhttp、nop-http-server-filters）与 `nop-network/nop-rpc/` 全 5 叶（nop-rpc-api、nop-rpc-core、nop-rpc-http、nop-rpc-model、nop-rpc-simple）全部 main 源码（逐文件审读；nop-rpc-model 以模型/加载器为主，无异步入口，经并发构造扫描确认零命中）。批次 2 将覆盖 nop-network 其余叶（nop-netty、nop-vertx、nop-socket、nop-codec）与 nop-integration。

**orTimeout 零命中声明**：`grep -rn "orTimeout|completeOnTimeout"` 在本批次 11 叶 main 源码零命中（已验证）。超时面改按以下口径审计：(1) 显式 timeout 参数的传播链（HttpRequest.timeout → 各实现 RequestConfig/builder.timeout/忽略）；(2) RpcChannelState 的 timer 超时；(3) readLine/InputStream 阻塞读的无超时面。

**数量口径**：发现 14 条（P1×5、P2×7、P3×2），每条均经 file:line 逐一人工复现，未沿用基线计数。异步入口清单：HTTP 侧 3 个客户端实现（fetch/download/upload/SSE Publisher 共 12 个异步入口）+ enhancer 链 + 服务端 filter 链；RPC 侧 IRpcService.callAsync 全部实现（Http/Message/Reflective/Multi/RpcMessageSubscriber/Simple/Cancellable/Polling 共 8 个）+ 4 个拦截器 + RpcChannelState/RpcTaskMonitor。

**批次 2/2 覆盖声明（C3 收官）**：本批次逐文件审读 15 叶全部 main 源码：`nop-netty`（31f；NettyHttpClient/NettyHttpServer 为空壳类，无审计对象）、`nop-socket`（11f）、`nop-vertx-commons`（3f）、`nop-vertx-mqtt-server`（13f；test 目录不在审读面）、`nop-codec`（6f——纯转换无异步入口，异常路径 buffer 经 catch→release 对称验证，零发现）、`nop-integration` 10 叶（api 24f 接口/bean/无状态支持——零发现；feishu 22f；email-java 2f；email-tencent 1f；file-local 3f；oss 3f；sftp 4f；sms-tencent 1f；sms-yunpian 1f；zxing 1f 无状态——零发现）。批次 2 发现 18 条（P1×3、P2×9、P3×6），另集外线索 CL-016…020。**C3 两批次收官**：26 叶全覆盖（批次 1 = nop-http 6 叶 + nop-rpc 5 叶；批次 2 = 上述 15 叶），累计发现 32 条（批次 1 P1×5/P2×7/P3×2 + 批次 2 P1×3/P2×9/P3×6），CL 累计 020 条（CL-001…010 C1 占用、CL-011…015 批次 1、CL-016…020 批次 2）。vertx 版本事实基线：Vert.x 5（`VertxMqttServer.doStop` 注释自证；`MqttEndpointImpl` 语义经本地仓库 5.1.8 jar 字节码核实）。批次 2 异步入口清单（执行步骤 1 交付物·续）：netty 侧 RpcMessageHandler（event loop 寄存 futures + scheduleWithFixedDelay 超时轮）、NettyTcpClient（connect listener 链 / sendAsync / sendOneway / 重连调度）、NettyTcpServer（sendToChannel 族）；socket 侧 SocketClient（heartbeat 定时 + recv 重连链 + call 阻塞读）、SocketServer（accept 循环 + per-connection 处理任务）；vertx 侧 MqttConnection 全部 endpoint 回调 + sendAsync、VertxMqttServer.handleEndpoint 异步 auth、MqttServerMessageService sendAsync/subscribe/dispatch；feishu 侧 FeishuClient streamListener 四回调 + heartbeat 定时 + 重连链、JdkStreamTransport connect/send/close；sftp/oss/email/sms 为同步客户端调用面（无 future/回调，按调用链超时与取消口径审读）。

---

## 发现

### [C3-001] OkHttp onResponse 内抛 IOException 被 OkHttp 吞掉，future 永不完成

- **文件**: `nop-network/nop-http/nop-http-client-okhttp/src/main/java/io/nop/http/client/okhttp/OkHttpClientImpl.java:66-88`（fetchAsync）、`:330-350`（uploadAsync）
- **证据片段**:
  ```java
  public void onResponse(Call call, Response response) throws IOException {
      int status = response.code();
      String text = response.body() == null ? "" : response.body().string();  // 中途断连时抛 IOException
      ...
      future.complete(res);   // string() 抛出时永不执行
  }
  ```
- **严重程度**: P1
- **并发场景**: OkHttp dispatcher 回调线程与调用 `fetch()/syncGet()` 的业务线程。
- **触发时序**: T1 业务线程调用 `fetch()`（= `FutureHelper.syncGet(fetchAsync(...))`）阻塞等待 future → T2 OkHttp 回调线程执行 `onResponse`，`response.body().string()` 读 body 中途服务端断连抛 IOException → OkHttp `AsyncCall.execute()` 的 catch(IOException) 中 `signalledCallback==true` 分支仅记 "Callback failure for ..." 日志（okhttp 3.x/4.x 同构）→ `future.complete/res` 均未执行 → T1 永久阻塞（OkHttp 只在 IOException 时才走 onFailure，而 onResponse 自身抛出不再路由 onFailure）。
- **现状**: fetchAsync 与 uploadAsync 的 onResponse 均无 try/catch 兜底；doDownloadAsync 的 onResponse（L165-226）有完整 try/catch → completeExceptionally，未中招。
- **风险**: 所有走 OkHttp 的同步 fetch（含 oauth token 获取、RPC over HTTP）在"响应头已到、body 传输中断"这一常见故障下无限挂起；上游若无线程池隔离将累积挂起线程。
- **建议**: onResponse 内 try/catch Throwable → `future.completeExceptionally(e)`（对齐 doDownloadAsync 已有写法）。
- **失效面枚举**: OkHttpClientImpl 共 3 个 enqueue 站点：fetchAsync L66-88（中招）、uploadAsync L330-350（中招）、doDownloadAsync L158-228（已保护）。JdkHttpClient/ApacheHttpClient 的响应处理均在显式 try/catch 内（未中招）。
- **信心水平**: 确定（okhttp AsyncCall 的 signalledCallback 吞异常语义，3.x/4.x 一致）
- **误报排除**: 非"原语使用误用"：缺陷在复合层——回调接口声明 throws IOException 而完成 future 的路径只有一条。
- **复核状态**: 未复核
- 复核状态: 已保留(P1)｜新发现——三 enqueue 站点逐核成立（fetchAsync L73-87 无兜底／uploadAsync L337-349 try-with-resources 无 catch，IOException 仍逃逸／doDownloadAsync L165-227 已保护），OkHttp signalledCallback 吞回调异常语义核验成立；跨单元同族第 4 例（future 永不完成族：WI 1.6 C8-012／WI 3.2 C1-001／WI 4.2 C3-001，零同站点重复单列）；OkHttp 实现为 opt-in 装配条件

### [C3-002] JDK 客户端下载 body 阶段取消失效：取消后幽灵下载继续占满带宽/磁盘

- **文件**: `nop-network/nop-http/nop-http-client-jdk/src/main/java/io/nop/http/client/jdk/JdkHttpClient.java:378-421`
- **证据片段**:
  ```java
  CompletableFuture<HttpResponse<InputStream>> future =
          client.sendAsync(toJdkHttpRequest(request), HttpResponse.BodyHandlers.ofInputStream());
  if (cancelToken != null)
      cancelToken.appendOnCancelTask(() -> future.cancel(false));   // headers 到达后 future 已完成，cancel 无效
  ...
  executor().execute(() -> {
      try (InputStream in = response.body()) {
          promise.complete(processDownloadResponse(...));   // while ((n = in.read(buf)) > 0) 无取消响应点
  ```
- **严重程度**: P1
- **并发场景**: 调用方线程（cancelToken.cancel）与 client executor 线程（body 读循环）。
- **触发时序**: T1 发起大文件下载，headers 到达、sendAsync future 完成，executor 任务进入 `in.read` 阻塞循环 → T2 调用方 `cancelToken.cancel()` → `future.cancel(false)` 作用于已完成的 future 返回 false，无中断 → T2 读循环继续至 EOF，完整写盘 + 生成 .part/改名。取消语义整体失效。
- **现状**: cancelToken 只绑定到 sendAsync future（headers 阶段生效）；body 阶段的阻塞读循环无 isCancelled 检查、无中断响应点。
- **风险**: 大文件下载取消后持续消耗带宽与磁盘直至完成；上层"已取消"的判断与实际网络行为背离；并发批量取消场景带宽被幽灵下载挤占。
- **建议**: 读循环每次迭代检查 `cancelToken.isCancelled()`（抛 CancellationException 并关闭 sink/in），或将 `cancelToken` 传入 `processDownloadResponse`。
- **失效面枚举**: JdkHttpClient.doDownloadAsync（中招）；Jdk ServerEventPublisher.parseEvents（同族，见 C3-003，中招）；ApacheHttpClient.doDownloadAsync（cancel 走 apache `Future.cancel` → abort 交换，body 阶段生效，未中招）；OkHttpClientImpl.doDownloadAsync（`call.cancel()` 中断阻塞 read → 抛 IOException → catch → sink.close，未中招）；Apache ServerEventPublisher（cleanup → apache future.cancel 生效，未中招）。
- **信心水平**: 确定（JDK `sendAsync`+`ofInputStream` 契约：future 在 headers 时完成，body 手工读）
- **误报排除**: 非传输库自身行为——body 读取是本类手工实现，取消挂接点选错是本模块代码缺陷。
- **复核状态**: 未复核
- 复核状态: 已保留(P1)｜新发现——ofInputStream future 完成于 headers 契约 + 读循环无取消响应点回码成立；JdkHttpClient 为 http-api.beans.xml 默认 raw client（live 默认装配）；幽灵任务取消失效与 WI 3.1 MA-08 幽灵进程 P1 判级先例对齐；与 WI 2.3 CL-025 断连观测平台原语为修复联动（非重复）

### [C3-003] JDK SSE 订阅取消后 readLine 阻塞读不终止：executor 线程按次泄漏

- **文件**: `nop-network/nop-http/nop-http-client-jdk/src/main/java/io/nop/http/client/jdk/ServerEventPublisher.java:69-93, 119-145`
- **证据片段**:
  ```java
  CompletableFuture<HttpResponse<InputStream>> future = client.sendAsync(request, ...ofInputStream());
  ...
  FutureHelper.bindCancelToken(cancelToken, this::cleanup, promise);  // cleanup → future.cancel(true)，流式阶段已无效
  ...
  executor().execute(() -> { ... parseEvents(reader); ... });
  protected void parseEvents(BufferedReader reader) throws IOException {
      String line;
      while ((line = reader.readLine()) != null) { ... }   // 无超时、无取消响应点
  ```
- **严重程度**: P1
- **并发场景**: 调用方取消线程与执行 parseEvents 的 client executor 线程（executor 未配置时为 `GlobalExecutors.globalWorker`）。
- **触发时序**: T1 SSE 流建立、headers 完成、executor 任务阻塞在 `reader.readLine()`（SSE 常见静默间隔 > 30s，request timeout 只约束 headers 接收）→ T2 调用方取消 → cleanup 将 `isCancelled` 置位并 `future.cancel(true)`（对已完成 future 无效）→ T2 持续阻塞在 readLine，直到服务端推下一行或断连；静默连接上无限期泄漏。每次取消泄漏一个线程；SSE 客户端反复订阅/取消（AI 网关前端断连场景）线性累积，有界线程池（threadPoolSize 配置时）被占满后全部 SSE 解析停摆。
- **现状**: parseEvents 仅在行间检查 isCancelled；行间阻塞点无超时兜底。
- **风险**: 长驻运行期线程泄漏（F5 变体：挂死读取对健康检测不可见）；池化 executor 饥饿导致同池其他任务（下载 body、错误体读取）排队。
- **建议**: 取消时关闭底层 InputStream/HttpResponse（JDK HttpClient cancel 语义之外的显式流关闭），或 readLine 前后检查 + 以 `SoTimeout` 等价的 idle 超时（可包一层可中断的行读取器）。
- **失效面枚举**: Jdk ServerEventPublisher（中招）；Apache ServerEventPublisher（cleanup → apache future.cancel → abort 交换触发 consumer.failed → onError/onComplete 路径，读取终止，未中招）；OkHttpClientImpl 无 SSE Publisher 实现（不支持 fetchServerEventFlow，默认抛 UnsupportedOperationException，不适用）。
- **信心水平**: 确定
- **误报排除**: 非平台基础设施问题——bindCancelToken 本身正确（promise 完成时移除监听），失效在 cleanup 绑定的目标 future 生命周期与实际阻塞点不重合。
- **复核状态**: 未复核
- 复核状态: 已保留(P1)｜新发现——cleanup→future.cancel(true) 对 headers 已完成的 future 无效 + readLine 无超时、仅行间查 isCancelled 回码成立；取消路径线程泄漏直击 P1 校准（live 默认装配）

### [C3-004] sidecar 校验和探测的同步 get(30s) 运行在 HTTP 回调线程内：reactor/executor 自阻塞

- **文件**: `nop-network/nop-http/nop-http-client-apache/src/main/java/io/nop/http/apache/ApacheHttpClient.java:330-332, 370-383`；同族 `JdkHttpClient.java:455-458, 475-487`；`OkHttpClientImpl.java:212-215, 239-252`
- **证据片段**:
  ```java
  // handleDownloadResult 由 FutureCallback.completed() 在 apache I/O reactor 线程调用
  if (options.isFetchSidecarChecksum() && !hasHeaderOrExplicitChecksum(options, result)) {
      sidecarText = fetchSidecarChecksum(request.getUrl(), cancelToken);
  ...
  private String fetchSidecarChecksum(String url, ICancelToken cancelToken) {
      for (String algorithm : new String[]{FileTransferHelper.SHA256, FileTransferHelper.SHA1}) {
          IHttpResponse res = fetchAsync(HttpRequest.get(...), cancelToken)
                  .toCompletableFuture().get(30, TimeUnit.SECONDS);   // 在回调线程内同步阻塞
  ```
- **严重程度**: P1（apache 面）；JDK/OkHttp 面同族降 P2
- **并发场景**: apache I/O reactor 调度线程（或 JDK client executor 线程 / OkHttp dispatcher 线程）内嵌套发起对同一 client 的请求并同步等待。
- **触发时序**: T1 下载完成的 FutureCallback 在 reactor 线程 R1 执行 handleDownloadResult → 发起 sidecar GET（复用同一 CloseableHttpAsyncClient）并 `get(30s)` 阻塞 R1 → sidecar 响应需要 reactor 线程投递：ioThreadCount=1（小部署常见）时 R1 即唯一线程，sidecar 永远无法完成 → 必然等满 30s 超时（SHA1 探测再等 30s）→ 期间该 reactor 上全部连接（所有 fetch/download/SSE）停摆；多线程 reactor 时阻塞 R1 上分片的所有连接 30–60s。
- **现状**: 三实现的下载完成路径都内联执行"嵌套 fetch + 同步 get"，未投递到独立执行器；fetchSidecarChecksum 默认开启（DownloadOptions.fetchSidecarChecksum=true）。
- **风险**: 下载收尾在事件循环线程执行重活（嵌套网络往返 + 校验和 + 文件改名），负载上升必然造成事件循环停摆级联；与"负载上升必然触发的死锁/队列溢出"同级。
- **建议**: 将 handleDownloadResult 的校验/改名/sidecar 探测整体投递到独立 executor（如 `GlobalExecutors.globalWorker` 或专用池），回调线程只做 promise 完成。
- **失效面枚举**: ApacheHttpClient.fetchSidecarChecksum L370-383（reactor 线程，最重）；JdkHttpClient.fetchSidecarChecksum L475-487（client executor 线程，嵌套响应投递同池 → 有界池饥饿面）；OkHttpClientImpl.fetchSidecarChecksum L239-252（dispatcher 线程，cached 池，风险最低）。三处实现互为复制粘贴（F3 同族），修复需三处同改。
- **信心水平**: 确定（阻塞事实确定；1 线程 reactor 下自死锁至超时为必然）
- **误报排除**: 非粗粒度同步的优雅性问题——阻塞点位于事件循环/回调线程，属异步链时序违约。
- **复核状态**: 未复核
- 复核状态: 已保留(P1 apache 面/P2 jdk+okhttp 面)｜新发现——三实现互为复制的嵌套 get(30s) 回码成立；复核加重事实：HC5 IOReactorConfig 默认 ioThreadCount 即 1（非“小部署常见”而是默认值），单 reactor 线程自阻塞至超时为默认配置的必然形态；apache 面 opt-in 装配条件

### [C3-005] 无校验源时 .part 不改名：下载报成功但目标文件不产生（F2 半个成功）

- **文件**: `nop-network/nop-http/nop-http-api/src/main/java/io/nop/http/api/utils/FileDownloadSink.java:162-175`；消费方 `JdkHttpClient.java:464`、`OkHttpClientImpl.java:221`；对照 `ApacheHttpClient.java:341-353`
- **证据片段**:
  ```java
  public void verifyAndRename(FileTransferHelper.ExpectedChecksum expected) throws IOException {
      if (expected == null)
          return;                       // 跳过校验的同时跳过了改名
      ...
      if (partFile != null) {
          Files.move(partFile.toPath(), targetFile.toFile().toPath(), REPLACE_EXISTING);
      }
  }
  ```
- **严重程度**: P1
- **并发场景**: 单请求异步完成路径（无跨线程交错，属异步链完成语义缺陷）；默认 DownloadOptions（resume=true, requireChecksum=false）。
- **触发时序**: T1 下载 body 完成 → sink.finish() → 无显式 checksum、响应头无 x-content-sha256/x-amz-checksum-sha256、sidecar 探测失败 → `resolveExpectedChecksum` 返回 null → `requireChecksum=false` 不抛 → `verifyAndRename(null)` 直接 return → promise.complete(成功响应)，而 `{name}.part` 从未改名为目标文件。后续重试永远从 .part 续传，目标文件永不出现。
- **现状**: rename 只在 expected!=null 分支执行；注释声明"校验并把 .part 原子改名为目标文件"，null 分支漏掉 rename 步骤。
- **风险**: 默认配置 + 无校验头的普通服务端（大多数静态文件服务）即触发；调用方拿到成功响应但产物缺失，属"半个成功"无恢复语义。
- **建议**: null 分支也执行 `Files.move`（跳过校验但不跳过落盘收尾），仅校验失败分支删除 .part。
- **失效面枚举**: JdkHttpClient（走 sink.verifyAndRename，中招）；OkHttpClientImpl（走 sink.verifyAndRename，中招）；ApacheHttpClient.handleDownloadResult L350-353（自行无条件 `Files.move`，未中招——三实现行为分歧的实证）。FileDownloadSink 是唯一共享实现，修一处即覆盖两个中招站点。
- **信心水平**: 确定
- **误报排除**: 非已声明边界——设计注释明示 verifyAndRename 承担改名职责，无"无校验源则保留 .part"的声明。
- **复核状态**: 未复核
- 复核状态: 已保留(P1)｜新发现——expected==null 分支跳过 rename 回码成立；设计契约 file-transfer-design.md §3.3（校验后原子 rename 属完成语义）+ Apache 无条件 move 对照 + DownloadOptions 默认值（resume=true/fetchSidecarChecksum=true/requireChecksum=false）三件复核证实默认路径触发；跨单元同族第 4 例（错误以成功交付族：WI 3.1 C7-002／WI 3.3 C3-001／WI 3.4 C3-005，零同站点重复单列）；同站点资源面 C6-001(P0) 另案已立

### [C3-006] ApiContextInterceptor 异步版无同步抛保护：proceedAsync 同步抛出时 binder 永不 close

- **文件**: `nop-network/nop-rpc/nop-rpc-api/src/main/java/io/nop/rpc/api/ApiContextInterceptor.java:22-33`
- **证据片段**:
  ```java
  public CompletionStage<ApiResponse<?>> interceptAsync(IRpcServiceInvocation inv) {
      ApiRequest<?> request = inv.getRequest();
      ContextBinder binder = new ContextBinder(request);   // 已向 ThreadLocal 挂接新 context
      return inv.proceedAsync().whenComplete(
              (ret, err) -> { binder.close(); });          // proceedAsync 同步抛出时此行不执行
  }
  ```
- **严重程度**: P2
- **并发场景**: 调用线程（binder 挂接 ThreadLocal）与后续异步完成线程。
- **触发时序**: T1 进入 interceptAsync，ContextBinder 构造将新 context attach 到 T1 的 ThreadLocal → T1 调用 `inv.proceedAsync()`，链上任一环节同步抛出（如 ReflectiveRpcService L55/61 的 ERR_RPC_NO_HANDLER、ClientContextRpcServiceInterceptor L131 的 ERR_API_TIMEOUT_EXPIRED）→ whenComplete 未挂上，异常直接向调用方传播 → binder.close() 永不执行 → T1 ThreadLocal 残留已创建 context（下一请求在其上复用/串号，见 C3-007）。
- **现状**: 同类问题在两个兄弟站点已修：同步版 `intercept` L42-46 用 try/finally；`ContextHttpServerFilter.filterAsync` L53-56 显式 catch 并注释"next.get() 同步抛出时 whenComplete 不会挂上"。本站点为 F3 漏修兄弟。
- **风险**: 使用方把该拦截器接入 RPC 链后，每次同步抛出泄漏一个 context；context 持有 attributes/propagateHeaders 引用，累积滞留。
- **建议**: 仿照 ContextHttpServerFilter：`CompletionStage ret; try { ret = inv.proceedAsync(); } catch (Exception e) { binder.close(); throw e; } return ret.whenComplete(...)`.
- **失效面枚举**: ApiContextInterceptor.interceptAsync（中招）；ApiContextInterceptor.intercept（try/finally，未中招）；ContextHttpServerFilter.filterAsync（catch，未中招）；ContextBinder 自身无静态注册表（无其他泄漏面）。注：该拦截器平台内无默认注册点（grep 全库仅定义处），属"接入即触发"的公共 API 契约缺陷，故定 P2 而非 P1。
- **信心水平**: 确定
- **误报排除**: 非 error-handling 维度问题——关注点是资源（ThreadLocal 挂接）的清理不对称（F8），非异常消息/错误码。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——interceptAsync 无同步抛保护 + 同类两兄弟站点已修（intercept try/finally／ContextHttpServerFilter 显式 catch）对照回码成立；平台无默认注册点（grep 全库仅定义处）维持 P2；同案 C6-009 已并入本条（主登记），其失效面③④为修复批连带核对项

### [C3-007] binder.close() 在异步完成线程执行：发起线程 ThreadLocal 不清除，池化线程复用已关闭 context 串号

- **文件**: `nop-network/nop-rpc/nop-rpc-api/src/main/java/io/nop/rpc/api/ApiContextInterceptor.java:29-32`；根因 `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/context/BaseContext.java:315-327`、`BaseContextProvider.java:26-56`、`ContextBinder.java:25-45`
- **证据片段**:
  ```java
  // BaseContext.close()（在完成线程 T2 上执行）：
  IContext context = BaseContextProvider.contextHolder().get();  // 读 T2 的 ThreadLocal
  if (context == this) { BaseContextProvider.clear(); }          // T2 上不是 this → 不清除
  // ContextBinder 构造（在下一次请求的 T1 上执行）：
  IContext context = ContextProvider.currentContext();  // 命中 T1 残留的旧 context
  if (context == null) { ... created = true; } else { created = false; }  // 不替换、不刷新
  ```
- **严重程度**: P2（若下游将 tenant 外发或用户态数据依赖该 context，可升级 P0）
- **并发场景**: 发起线程 T1（池化 worker/HTTP 线程）创建并 attach context；HTTP 传输回调线程 T2 完成 future 并执行 close。
- **触发时序**: T1 outbound 调用（ApiContextInterceptor 在链上）创建 context 并 attach 到 T1 → 请求经 HttpRpcService→fetchAsync，future 在 T2（client 回调线程）完成 → T2 执行 `binder.close()`：T2 的 ThreadLocal 非 this，仅置 `closed=true` → T1 的 ThreadLocal 仍持有该 context（已关闭）→ T1 被线程池复用处理下一请求 → `new ContextBinder()` 命中旧 closed context，`created=false`，不初始化 → 上一请求的 tenantId/traceId/locale 经 `ClientContextRpcServiceInterceptor.transfer` 继续外发（跨请求上下文串号），且 context 上 `checkClosed()` 的操作（execute/executeBlocking）开始抛错。
- **现状**: close 语义只在"close 与 attach 同线程"时清 ThreadLocal；异步完成跨线程是该拦截器的常态而非例外（outbound HTTP 必然跨线程）。注意 `ExecutorHelper` 提交的任务会在 finally 中 detachContext（ExecutorHelper.java:143/167/192），但 T1 是调用发起线程，不经该包装。
- **风险**: 池化线程复用后串号是跨租户数据泄漏路径；同时 closed context 导致后续调用随机失败。
- **建议**: interceptAsync 在创建 binder 的线程上登记"归我清理"标记，whenComplete 中仅置关闭位、由发起线程侧（或 via `ContextProvider.thenOnContext`）执行 detach；或 ContextBinder 构造时对 `currentContext().isClosed()` 的残留强制替换（getOrCreateContext 已有 isClosed 检查而 currentContext 没有——补齐该守卫可止血）。
- **失效面枚举**: ApiContextInterceptor（跨线程 close，中招）；ContextHttpServerFilter（服务端 filter 每次 newContext(true) 覆盖式 attach，下一请求自愈，未中招）；ContextBinder 全部构造路径（`ContextBinder()` 与 `ContextBinder(ApiRequest)`，均中招——命中残留是共同入口）。
- **信心水平**: 很可能（代码路径确定；实际触发依赖使用方装配 ApiContextInterceptor，平台内当前无默认注册点）
- **误报排除**: 非"无状态单例并发调用"误报——缺陷是 ThreadLocal 生命周期与异步完成线程的错配，有具体交错序列。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——三层回码成立：BaseContext.close 跨线程仅置 closed 不清发起线程 ThreadLocal、tenantId/traceId/locale 为字段不被 attributes.clear() 清除（串号外发路径成立）、ContextBinder 构造命中残留不复初始化；止血建议核验：BaseContextProvider.getOrCreateContext 有 isClosed 检查（L38）而 currentContext（L22-24）无；“很可能”信心成立（装配前置）；平台根因面已由 CL-011 移交 C2

### [C3-008] oauth token 往返在 enhancer 锁内同步 HTTP：异步链阻塞面 + SSE 路径绕过增强

- **文件**: `nop-network/nop-http/nop-http-client-oauth/src/main/java/io/nop/http/client/oauth/enhancer/AddAccessTokenHttpClientEnhancer.java:110-132`（锁内 fetch）、`:44-69`（仅覆盖 onFetchBegin）；对照 `DelegateHttpClient.java:39-43`（fetchServerEventFlow 走 onRequestBegin）
- **证据片段**:
  ```java
  synchronized (authTokens) {
      ...
      Oauth2TokenResponseBean response = fetchToken(client, providerConfig);  // 锁内同步 HTTP 往返
      authTokens.put(providerName, response);
  }
  protected Oauth2TokenResponseBean fetchToken(...) {
      ...
      return client.fetch(request, null).getBodyAsBean(Oauth2TokenResponseBean.class);  // syncGet
  ```
- **严重程度**: P2
- **并发场景**: 多个业务线程并发走同一 enhancer；token 端点为远端 HTTP 服务。
- **触发时序**: T1 token 过期，进入 `synchronized(authTokens)` 并发起同步 token 请求（无 request.timeout，受全局 readTimeout 30s 兜底）→ T2..Tn 所有经过该 enhancer 的 `fetchAsync`（含异步调用，onFetchBegin 在调用线程同步执行）在锁上排队，每个最多阻塞一个 token 往返时长；token 端点抖动时全量外呼串行停摆。次要：`EnhancedClient` 仅覆盖 `onFetchBegin`，而 SSE 流入口 `fetchServerEventFlow` 在 DelegateHttpClient 中走 `onRequestBegin` → `fetchStreamAsync`（默认实现）经 `this.fetchServerEventFlow` 分派，完全绕过 token 注入 → SSE 请求 401。
- **现状**: 双检锁模式本身正确（get-check、sync、re-check、put）；缺陷在临界区含阻塞 I/O 与增强覆盖面缺口。
- **风险**: token 刷新风暴（服务返回 5xx 反复重试）时，锁串行放大为全局外呼停摆；SSE 场景认证静默缺失。
- **建议**: 临界区内只做"取旧 token + 登记刷新中"标记，HTTP 往返移出锁外后回填（per-provider 锁或 computeIfAbsent 异步化）；EnhancedClient 补覆盖 `fetchServerEventFlow` 或在 `onRequestBegin` 中复用 token 注入逻辑。
- **失效面枚举**: onFetchBegin（fetchAsync/downloadAsync/uploadAsync 三入口经此，均有 token）；fetchServerEventFlow（onRequestBegin，无 token，中招）。锁面：单锁 `authTokens` 覆盖全部 provider（跨 provider 互相阻塞）。
- **信心水平**: 确定
- **误报排除**: 非低频路径粗锁——token 刷新在运行期随每个过期周期发生，阻塞面在主调用线程。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——锁内同步 token HTTP 往返 + 单锁跨 provider 回码成立；失效面以 round2 扩展为准（EnhancedClient 仅覆盖 onFetchBegin，download/upload/SSE 三入口完全绕过 token 注入与 propagateHeaders，仅 fetchAsync 有 token——凭据配置 oauth provider 的部署下载/上传一律 401）；同案 C6-008 已并入本条（锁内 IO 面）

### [C3-009] SimpleRpcClient 发送失败时完成的是派生 future：waitFutures 条目滞留至超时（timeout<=0 时永久泄漏）

- **文件**: `nop-network/nop-rpc/nop-rpc-simple/src/main/java/io/nop/rpc/simple/SimpleRpcClient.java:118-139`；根因 `RpcChannelState.java:91-107, 110-150`
- **证据片段**:
  ```java
  CompletableFuture<ApiResponse<?>> future = channelState.prepareSend(id, request, timeout);
  try {
      client.send(cmd, true);
  } catch (Exception e) {
      future.completeExceptionally(e);   // future 是 scheduleTimeout 返回的 whenComplete 派生 future
  }
  // RpcChannelState.waitFutures 中登记的是派生前的原始 future
  ```
- **严重程度**: P2
- **并发场景**: 调用线程与 channel timer 线程（超时清理）、通道接收线程（onReceive）。
- **触发时序**: T1 callAsync 注册原始 future 到 waitFutures 并取得派生 future → `client.send` 同步抛出（连接已断）→ T1 完成的是派生 future，调用方立即收到异常 → 原始 future 仍滞留 waitFutures：timer 到期时 `waitFutures.remove(id, future)` 成功并 completeExceptionally 原始 future（派生已完成后无效果）——条目在 [0, timeout) 区间占用 maxWaitingRequests 名额；`ClientConfig.getResponseTimeout()<=0`（scheduleTimeout 走 timeout<=0 分支）时无 timer，条目与闭包永久滞留（F4 无界账本）。同时原始 future 的 whenComplete hook（whenError）不触发，观测链缺一次 onError。
- **现状**: 同一基建的正确用法在 MessageRpcClient.callAsync L110-120：发送失败调 `channelState.onFailure(id, e)`（remove + completeExceptionally 原始 future），未中招——F3 兄弟对照实证。
- **风险**: 连接频繁失败的部署下 inflight 名额被滞留条目占用，`ERR_RPC_TOO_MANY_INFLIGHT_MESSAGES` 提前触发；timeout<=0 配置下内存缓慢泄漏。
- **建议**: 对齐 MessageRpcClient：catch 中改调 `channelState.onFailure(id, e)`。
- **失效面枚举**: SimpleRpcClient.callAsync（中招）；MessageRpcClient.callAsync（onFailure 正确，未中招）；SimpleRpcClient.callAsync 的 buildRequest 失败分支 L126-128（发生在 prepareSend 之前，无登记，未中招）。RpcChannelState.prepareSend 自身无法从外部区分"完成派生 future"，需在使用方修。
- **信心水平**: 确定
- **误报排除**: 非"CompletableFuture 使用即报"——复合语义错位（包装层与登记层指向不同 future 实例）有具体后果链。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——prepareSend 返回 scheduleTimeout 派生 future + 发送失败完成的是派生 future，原始条目滞留 [0,timeout) 占用 inflight 名额回码成立；timeout<=0 无 timer 永久滞留（F4）成立；MessageRpcClient onFailure 正确对照成立；仓内消费面 nop-idea-plugin/nop-xlang-debugger（dev-tools，包络小）

### [C3-010] RpcTaskMonitor 异步回调跨轮次并发：checkFailCount 丢失更新、cancellable 非 volatile

- **文件**: `nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/monitor/RpcTaskMonitor.java:40-41, 90-123`
- **证据片段**:
  ```java
  private ICancellable cancellable;                      // 非 volatile，timer 线程写 / doStop 线程读
  private void checkTaskStatus() {
      Cancellable cancellable = new Cancellable();
      this.cancellable = cancellable;
      for (RpcTask task : activeTasks.values()) {
          service.callAsync(statusMethod, task.getRequest(), cancellable).whenComplete((ret, err) -> {
              ...
              handleTaskStatus(task, res, err);          // 完成线程上执行
  ```
- **严重程度**: P2
- **并发场景**: timer 轮询线程发起；各任务的 whenComplete 回调在 RPC 传输完成线程上并发执行，可跨轮次交叠（scheduleWithFixedDelay 只约束轮询方法返回间隔，不等待上一轮回调）。
- **触发时序**: 轮 N 的 callAsync 慢（超时边界）→ 轮 N+1 启动并对同一 task 再次 callAsync → 轮 N 回调与轮 N+1 回调并发执行 `task.setCheckFailCount(task.getCheckFailCount() + 1)` → 两次读改写交叠丢失一次递增 → fail-count 低于实际，`statusStorage.saveTaskStatus` 返回 STOP 的判定被推迟；次级：doStop 与 timer 线程对 `cancellable` 字段无同步，stop 可能漏取消最新一轮的在途调用。
- **现状**: 无 per-task 互斥；activeTasks 是 ConcurrentHashMap（容器安全，非复合操作安全）。
- **风险**: 长时任务监护精度失真（fail-count 是唯一自愈/停表依据）；影响随任务数与轮询频率线性放大。
- **建议**: fail-count 迁移到 RpcTask 内的 AtomicInteger.incrementAndGet；cancellable 加 volatile 或 AtomicReference；saveTaskStatus 对同一 task 加 per-task 串行化（如按 taskId 的条带锁）。
- **失效面枚举**: RpcTaskMonitor.checkTaskStatus（中招）；RpcTaskMonitor.cancelTask（独立入口，无共享可变状态，未中招）；updateTaskStatus（与回调并发调 handleTaskStatus，同族中招面）；RpcTask.setCheckFailCount 实现为普通字段读写（伴随中招）。
- **信心水平**: 很可能（交叠可具体化；后果限于计数/状态存储精度）
- **误报排除**: 非 ConcurrentHashMap 原语误用问题——是回调跨轮交叠的复合操作竞态。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——跨轮回调 checkFailCount 非原子 RMW + cancellable 非 volatile 回码成立；同案 C1-004 已并入本条（主登记：checkFailCount RMW 与 updateTaskStatus 面均含于本条失效面，防重复计数）；RpcTaskMonitor 全仓零装配注记（面向嵌入方公开 API）

### [C3-011] callAsync"返回 CompletionStage"契约的同步抛出逃逸族

- **文件**: 抛出方：`ReflectiveRpcService.java:54-63`、`RpcChannelState.java:80-87, 101-105`、`ClientContextRpcServiceInterceptor.java:126-134`、`RpcMessageSender.java:56-64`、`AddAccessTokenHttpClientEnhancer.java:88-97`；未防护调用方：`CancellableRpcClient.java:43`、`PollingRpcClient.java:58`、`RpcInvocationHandler.java:83`
- **证据片段**:
  ```java
  // ReflectiveRpcService.callAsync：try 块之外同步 throw
  if (methods == null) {
      throw new NopException(ERR_RPC_NO_HANDLER)...;
  }
  // CancellableRpcClient.callAsync：无防护
  CompletionStage<ApiResponse<?>> future = rpcService.callAsync(serviceMethod, request, cancelToken);
  future.whenComplete((r, e) -> done.set(true));   // callAsync 同步抛时此行不执行，异常改走调用栈
  ```
- **严重程度**: P2
- **并发场景**: 异步链组合方（thenCompose/whenComplete 挂接点）与同步抛出点在不同后续挂接之间。
- **触发时序**: T1 `pollingClient.callAsync(...)` → 内部 `rpcService.callAsync(startMethod, ...)` 同步抛（如 enhancer 的 ERR_HTTP_OAUTH_NO_USER_CONTEXT）→ 异常未经 future 传播，直接从 callAsync 调用栈抛出 → `thenCompose` 未建立，调用方若按异步契约写 `callAsync(...).whenComplete(...)` 则异常在挂接前逃逸：thenApply/whenComplete 后的清理逻辑（如 PollTask 的 removeOnCancel）不执行；RPC 动态代理（RpcInvocationHandler L83）同理——`methodModel.isAsync()` 的方法返回值契约是 CompletableFuture，调用方拿到的是同步异常。
- **现状**: 部分调用方已有防护：MessageRpcServer.processRequest L85-102（try/catch Throwable）、RpcTaskMonitor.checkTaskStatus L98-108（catch Exception）、PollingRpcClient.PollTask.call L132-135（catch Throwable）——但只防护了轮询内的 statusMethod 调用，首跳 thenCompose 前的同步抛未防。
- **风险**: 防护矩阵不一致导致同一故障（通道关闭/流控配置错/无 handler）在不同调用路径下表现迥异（future 异常 vs 栈上异常），上层取消/清理逻辑随机失效。
- **建议**: 统一约定：公共 IRpcService 实现的 callAsync 不得同步抛（用 `FutureHelper.reject` 包装），或在 AopRpcService/RpcInvocationHandler 出口统一 catch 归一为 failed future。
- **失效面枚举**: 抛出方 5 站点：ReflectiveRpcService（NO_HANDLER/OVERLOAD）、RpcChannelState.prepareSend（CLOSED/TOO_MANY）、ClientContextRpcServiceInterceptor.transfer（TIMEOUT_EXPIRED）、RpcMessageSender.getRpcService（INVALID_TOPIC）、oauth enhancer getAccessToken（NO_USER_CONTEXT/UNKNOWN_PROVIDER）。防护方矩阵：MessageRpcServer（已防）、RpcTaskMonitor（已防）、PollingRpcClient.PollTask.call（已防）、CancellableRpcClient（未防）、PollingRpcClient.callAsync 首跳（未防）、RpcInvocationHandler（未防）、RpcMessageSubscriber.callAsync（handleMethod 内 catch，未中招）。
- **信心水平**: 确定
- **误报排除**: 非 error-handling 维度——关注异常传播通道（future vs 栈）对异步组合子的破坏，属 C3 异常传播检查项。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——5 抛出站点 + 防护矩阵逐点回码成立（CancellableRpcClient/PollingRpcClient 首跳/RpcInvocationHandler.isAsync 未防；MessageRpcServer/RpcTaskMonitor/PollTask.call 已防）；round2 扩展站点 MqttServerMessageService.sendAsync L75-77 确认

### [C3-012] cancelToken 取消监听器只增不删：长生命周期 token 上无界累积（F4）

- **文件**: JdkHttpClient 4 站点：`JdkHttpClient.java:168-172, 380-381, 541-542, 578-579`；OkHttpClientImpl 3 站点：`OkHttpClientImpl.java:63-64, 154-155, 326-327`；对照正确实现 `FutureHelper.java:528-535`（ApacheHttpClient 4 站点均经此）
- **证据片段**:
  ```java
  // JdkHttpClient.fetchAsync
  if (cancelTokens != null) {
      cancelTokens.appendOnCancel(reason -> { future.cancel(false); });   // 无 removeOnCancel
  }
  // 对照 ApacheHttpClient.fetchAsync（正确）：
  FutureHelper.bindCancelToken(cancelToken, reason -> future.cancel(false), promise);
  //   bindCancelToken 内部：promise.whenComplete → cancelToken.removeOnCancel(cleanup)
  ```
- **严重程度**: P2
- **并发场景**: 共享/长生命周期 ICancelToken（会话级、per-user 级）跨成千上万次请求复用。
- **触发时序**: T1 每次调用 fetchAsync/downloadAsync/uploadAsync 向同一 cancelToken append 一个监听器（lambda 持有 future→response→body 引用链）→ 请求完成后监听器不移除 → token 的监听器集合随请求次数线性增长，且每个条目钉住整个响应对象图 → 长驻进程内存无界增长；token 最终取消时还要遍历执行全部失效监听（O(n) 卡顿）。
- **现状**: JDK 实现全部 4 个绑定点、OkHttp 全部 3 个绑定点均未清理；Apache 4 个绑定点统一走 `FutureHelper.bindCancelToken`（append + 完成时 remove），是平台已有的正确设施。
- **风险**: 与"长时运行必然触发的无界增长"同类；泄漏量级 = 请求数 × (future + response body) 引用。
- **建议**: JDK/OkHttp 全部站点改用 `FutureHelper.bindCancelToken(cancelToken, consumer, future)`。
- **失效面枚举**: JdkHttpClient.fetchAsync/doDownloadAsync/uploadByBinary/uploadByBase64Form（4/4 中招）；OkHttpClientImpl.fetchAsync/doDownloadAsync/uploadAsync（3/3 中招）；ApacheHttpClient.fetchAsync/doDownloadAsync/uploadByBinary/uploadByBase64Form（0/4，经 bindCancelToken）；Jdk ServerEventPublisher（经 bindCancelToken，未中招）；Apache ServerEventPublisher（经 bindCancelToken，未中招）。
- **信心水平**: 确定（触发依赖长生命周期 token 的使用方式，绑定侧缺陷本身确定）
- **误报排除**: 非"有界声明存在"的误报场景——监听器集合无任何上限/清理声明。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——全 scope appendOnCancel 站点穷举复核一致（main 源码 10 处：jdk×4／okhttp×3／MessageRpcClient／CancellableRpcClient 中招；PollingRpcClient L82-84 手动 remove OK；Apache×4+双 SSE Publisher 经 bindCancelToken OK）；round2 扩展 2 站点确认；平台修复入口 FutureHelper.bindCancelToken 单点收敛成立

### [C3-013] 服务端 filter 的 MDC trace 在异步延续线程丢失

- **文件**: `nop-network/nop-http/nop-http-api/src/main/java/io/nop/http/api/server/ContextHttpServerFilter.java:47-59`
- **证据片段**:
  ```java
  MDC.put(ApiConstants.MDC_NOP_TRACE, ctx.getTraceId());
  try {
      CompletionStage<Void> ret = next.get();
      return ret.whenComplete((r, e) -> { ctx.close(); });
  } finally {
      MDC.remove(ApiConstants.MDC_NOP_TRACE);   // 同步段返回即移除；异步延续线程从未持有
  }
  ```
- **严重程度**: P3
- **并发场景**: HTTP worker 线程（同步段，MDC 有值）与异步延续线程（无 MDC）。
- **触发时序**: T1 filter 同步段设置 MDC → next.get() 返回未完成 future → finally 移除本线程 MDC（正确，防池化污染）→ 异步延续在 T2 执行业务后续/日志输出 → T2 无 traceId，跨线程日志链断裂。此为记录性缺陷（日志聚合降级），非状态不一致。
- **现状**: ctx 本身经 `context.setContext(ctx)` 传递正确；仅 MDC 面缺失。平台有 `NamedThreadLocal`/context 包装设施未被用于 MDC。
- **风险**: 分布式追踪与日志排查效率下降；不影响正确性。
- **建议**: 若需跨异步延续的 MDC，用平台 context 的 execute/thenOnContext 通道传播，或在延续点按 ctx.getTraceId() 重挂。
- **失效面枚举**: ContextHttpServerFilter（唯一服务端 context filter 实现，单站点）；客户端侧无需 MDC 传播面。
- **信心水平**: 确定
- **误报排除**: 非"低频路径粗锁"类误报；明确标注为记录性缺陷并定 P3，不与正确性发现混排。
- **复核状态**: 未复核
- 复核状态: 已保留(P3)｜新发现——MDC 同步段移除、异步延续线程从未持有回码成立；ContextHttpServerFilter 经 nop-biz biz-defaults.beans.xml 默认装配（live）；记录性缺陷定 P3 恰当

### [C3-014] OkHttp 实现忽略 request.getTimeout()：跨实现超时语义分歧

- **文件**: `nop-network/nop-http/nop-http-client-okhttp/src/main/java/io/nop/http/client/okhttp/OkHttpClientImpl.java:101-128`（newCall 未应用 request.getTimeout）；对照 `ApacheHttpClient.java:191-196`（RequestConfig.setResponseTimeout）、`JdkHttpClient.java:218-222`（builder.timeout）
- **证据片段**:
  ```java
  protected Call newCall(HttpRequest request) {
      ...
      requestBuilder.method(method, body);
      // request.getTimeout() 未被读取；OkHttpTimeoutInterceptor 仅按 HEADER_TIMEOUT 头收紧 connectTimeout
      return client.newCall(requestBuilder.build());
  }
  ```
- **严重程度**: P3
- **并发场景**: 单请求超时控制面；调用方按 IHttpClient 契约设置 per-request 超时。
- **触发时序**: 调用方设置 `request.setTimeout(5000)` 并走 OkHttp 实现 → 超时被静默忽略，实际受全局 readTimeout（默认 30s）约束 → 与 Apache/JDK 实现（5s 生效）行为分歧；反向影响：SSE 流式请求在 OkHttp 下受 readTimeout 作用于"两次读间隔"，30s 静默即断流，而 Apache 的 responseTimeout 仅约束首响应——同一接口三实现三种超时语义。
- **现状**: OkHttpTimeoutInterceptor 只依据 HEADER_TIMEOUT 头收紧 connectTimeout，不处理 readTimeout 也不读 HttpRequest 对象字段。
- **风险**: 上层超时治理（请求级 deadline）在 OkHttp 通道失效；流式请求意外断连。
- **建议**: `client.newBuilder()` 派生 per-call client 并 setReadTimeout(request.getTimeout())（OkHttp 的标准 per-call 超时手法），并在 OkHttpTimeoutInterceptor 补 readTimeout 收紧逻辑。
- **失效面枚举**: OkHttpClientImpl.newCall（fetch/download/upload 三入口共用，全部中招）；OkHttpClientImpl 无 SSE 流实现（不适用）；Apache/JDK 全站点已应用 per-request timeout（未中招）。
- **信心水平**: 确定
- **误报排除**: 非"声明了上限即可"场景——per-request 参数在契约接口上存在但单一实现静默丢弃。
- **复核状态**: 未复核
- 复核状态: 已保留(P3)｜新发现——newCall 不读 request.getTimeout() 回码成立；Apache setResponseTimeout／Jdk builder.timeout 对照成立；全局 readTimeout 30s 兜底有界，P3 恰当；OkHttp opt-in 装配条件

### [C3-015] Feishu WS send 无超时阻塞单线程 scheduler：半开连接下心跳/重连机制整体死锁（F5）

- **文件**: `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/client/JdkStreamTransport.java:98-111`（send 无界 get）；消费方 `FeishuClient.java:64-65, 100-104`（单线程 scheduler 承载 heartbeat+reconnect）、`:258-281`（scheduleHeartbeat/sendHeartbeat）、`:283-295`（scheduleReconnect）
- **证据片段**:
  ```java
  // JdkStreamTransport.send —— 由 sendHeartbeat 在唯一调度线程上调用
  ws.sendBinary(ByteBuffer.wrap(frameBytes), true).get();   // 无超时；JDK WebSocket 无内建写超时
  // FeishuClient 构造：
  this.scheduler = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory("feishu-client"));
  ```
- **严重程度**: P1
- **并发场景**: FeishuClient 自有单线程 scheduler（heartbeat 定时 + reconnect 定时共用）与 JDK HttpClient 的 WS 回调线程；transport.send 的阻塞点跨这两类线程。
- **触发时序**: T1 连接进入半开态（NAT/LB 静默丢会话，无 RST，TCP 零窗）→ T2 scheduler 线程执行 sendHeartbeat（connected=true）→ `sendBinary(...).get()` 阻塞等待"已写出"——对端不读、本端无写超时，future 永不完成 → scheduler 唯一线程永久占用 → T3 连接死亡事件到达（onClose/onError 在 HttpClient 线程）→ `scheduleReconnect` 的 `scheduler.schedule(...)` 排队但永不执行 → 重连与心跳全部停摆，`connected` 恒为 true，无任何错误面暴露（F5：不抛异常也不产出的挂死对健康检测不可见）。
- **现状**: send 用无参 `get()`；`sendHeartbeat` 失败仅 LOG.debug（日志级别还低于 warn）；HTTP 侧同模块（JdkFeishuHttpApi）三个调用点均设 `.timeout(15s)`——正确写法在同类已存在。
- **风险**: 长驻进程在一次静默网络故障后 Feishu 消息通道（收发+重连）永久瘫痪且无告警；上层 FeishuConnector（nop-ai-gateway）依赖此 client 的收发与 isConnected。
- **建议**: send 改 `get(timeout)`（对齐 HTTP 面 15s）或异步发送+超时即 `transport.close()` 触发 onClose 重连路径；heartbeat 发送失败升级为 warn。
- **失效面枚举**: transport.send 共 3 个调用点：sendHeartbeat L277（scheduler 线程，中招——阻塞唯一调度线程，致命）、onOpen handshake `FeishuClient.java:324`（HttpClient 线程，中招——阻塞 WS 回调线程，拖停后续帧投递）、onBinary ACK `FeishuClient.java:353`（HttpClient 线程，中招同前）。对照：JdkFeishuHttpApi postJson/postJsonWithAuth/getJsonWithAuth 均设 15s request timeout（未中招）。
- **信心水平**: 确定（阻塞点确定；"永不完成"在静默丢包故障模型下成立，RST 类故障可自恢复）
- **误报排除**: 非库自身缺陷——无超时的阻塞等待是本模块对异步链的组合选择，且同模块 HTTP 面已示范带超时写法，非统一边界声明。
- **复核状态**: 未复核
- 复核状态: 已保留(P1)｜新发现——send 无参 get() 无超时 + 单线程 scheduler 承载心跳/重连 + 3 调用点（L277 scheduler 线程／L324 onOpen／L353 ACK）+ sendHeartbeat 失败仅 LOG.debug + JdkFeishuHttpApi 15s×3 正确对照，回码全部成立；同案 C6-024 升格 P1 对齐关系核验一致；休眠面注记：生产激活唯一路径 ChannelConnectorManager.startAll 全仓非测试零调用（WI 3.3 渠道栈休眠裁定延伸），激活回升条款建议 7.1 对 C3-015/C6-024 统一适用

### [C3-016] FeishuClient stop() 无法关闭在途 connect：停机竞态遗留僵尸 WS 连接

- **文件**: `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/client/FeishuClient.java:200-224`（stop）、`:297-310`（attemptReconnect）；`JdkStreamTransport.java:37-41`（connect 先覆盖 listener/webSocket）、`:114-125`（close）
- **证据片段**:
  ```java
  // JdkStreamTransport.connect：先改共享字段，再阻塞握手（≤15s join）
  this.listener = listener;
  ...
  this.webSocket = httpClient.newWebSocketBuilder()...buildAsync(uri, wsListener).join();
  // FeishuClient.stop：close 的是"当时"的 webSocket，之后完成的 connect 会覆盖回来
  started = false; ... transport.close(); scheduler.shutdownNow(); this.handler = null;
  ```
- **严重程度**: P2
- **并发场景**: scheduler 线程（attemptReconnect 内的 connect 阻塞段，不持 FeishuClient monitor）与业务线程（synchronized stop）。
- **触发时序**: T1 scheduler 线程 attemptReconnect 通过 `started` 检查、进入 `transport.connect`（join 阻塞，≤15s）→ T2 业务线程 stop()：started=false、transport.close()（关闭旧 webSocket、listener 置 null）、shutdownNow、handler=null → T1 connect 完成：`webSocket`=新连接、`listener`=重新赋值（connect 内部第一行）→ 僵尸连接存活：onBinary 到达 → `handler.onMessage` 于 handler=null → NPE 被 catch(Throwable) 捕获 → `handler.onError(t)` 二次 NPE；连接无心跳、无人关闭，滞留至 Feishu 服务端超时断开。
- **现状**: stop 无与在途 connect 的互斥或代际校验；transport.close 后可被同一 connect 调用"复活"（字段覆盖）。
- **风险**: 停服/重建 bean 场景下连接与回调线程滞留；shutdown 期资源不释放，旧实例继续接收消息。
- **建议**: FeishuClient 维护 generation 计数（stop 递增），connect 返回后校验代际不符即 `transport.close()`；或 transport.connect 返回 WS 句柄由 client 持有、close 按句柄实例关闭。
- **失效面枚举**: JdkStreamTransport（唯一 transport 实现，中招）；attemptReconnect（唯一异步 connect 入口，中招）；start() 同步 connect（持 monitor 且 stop 与 start 天然互斥，未中招）；close() 自身逻辑（正确，中招根源在使用方竞态）。
- **信心水平**: 确定
- **误报排除**: 非单例并发调用误报——生命周期竞态有具体交错序列与滞留后果。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——stop 与在途 connect 竞态（CompletableFuture.join 不可中断 + connect 先覆盖 listener/webSocket 字段可“复活”已关 transport + handler=null 二次 NPE）回码成立；与 C6-021(P1) 同站点判级分歧留 7.1——C3 侧意见：缺陷本体与修复点（代际校验）无分歧，纯判级校准分歧（停机窗口资源滞留 P2 vs 取消路径泄漏+停机语义违约 P1），建议 7.1 合并单工单裁定

### [C3-017] FeishuClient attemptReconnect 无 connected 守卫：交错重连建立并行活连接，消息双份消费

- **文件**: `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/client/FeishuClient.java:283-310`
- **证据片段**:
  ```java
  private void attemptReconnect() {
      if (!started) {          // 只查 started，不查 connected/连接代际
          return;
      }
      ... transport.connect(URI.create(ep.getUri()), streamListener);
      reconnectAttempt.set(0); // 成功后重置，不回收任何既有连接
  ```
- **严重程度**: P2
- **并发场景**: 同一 scheduler 上的重连任务与 WS 回调线程的 onClose 事件交错（跨连接代际）。
- **触发时序**: conn0 断开 → onClose → scheduleReconnect P1（attempt=1, 1s）→ P1 触发 attemptReconnect，connect 建立 conn1，onOpen 后 connected=true → conn0 迟到的 close 事件此后到达（close 帧/FIN 网络延迟超过重连间隔）→ 再次 scheduleReconnect P2 → P2 触发 attemptReconnect：无 connected 守卫 → 再次 getStreamEndpoint+connect 建立 conn2 → conn1 未被关闭 → 双活连接同时投递 onBinary → handler.onMessage 每消息双次消费、ACK 双发；后续每代际交错重复放大。
- **现状**: attemptReconnect 仅 `started` 检查；connect 成功不关闭任何既有 webSocket（JdkStreamTransport.connect 只覆盖字段）。
- **风险**: 消息重复消费与重复 ACK；Feishu 服务端对同 app 多 stream 连接的互踢行为可能引发重连风暴循环。
- **建议**: attemptReconnect 入口加 `if (connected) return;`（配合代际校验）；connect 成功后显式关闭上一 webSocket。
- **失效面枚举**: attemptReconnect 唯一重连实现（中招）；start() 首连（同步单次、幂等守卫 started，未中招）；onClose/onError 双回调（JDK WS 契约互斥，单代不重复，不适用）。
- **信心水平**: 很可能（守卫缺口确定；触发依赖旧代 close 事件迟到 > 重连间隔的时序，可具体化）
- **误报排除**: 非理论竞态降档对象——完整交错链可写出且后果（双活连接）持续。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——attemptReconnect 仅 started 检查、connect 成功不关闭既有 webSocket 回码成立；与 C1-009(P1) 同站点判级分歧留 7.1——C3 侧意见：C1-009 触发(a)（onError 后跟 onClose 双调度）依赖双终态回调序列，JDK WebSocket 契约中 onClose/onError 为互斥终态信号，该前提需 C1 侧复核；C1-009 触发(b)（stop→start 重启窗口陈旧回调）独立扎实可支撑合并 P1，倾向 7.1 以 C1-009 为主登记合并（C3-017 迟到 close 触发链自洽但窗口较窄）

### [C3-018] MqttSessionManager.removeConnection 无条件清订阅表：会话接管 + 迟到 close 回调清掉新连接订阅，下行路由静默瘫痪

- **文件**: `nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/impl/MqttSessionManager.java:42-45`（无条件 `subscriptions.remove(clientId)`）；装配点 `VertxMqttServer.java:116-125`；回调链 `MqttConnection.java:211-224`（complete → onCloseCallback）
- **证据片段**:
  ```java
  public void removeConnection(IMqttConnection conn) {
      sessions.remove(conn.getClientId(), conn);   // 条件删除：只删自己
      subscriptions.remove(conn.getClientId());    // 无条件删除：把当前会话的订阅一并清掉
  }
  ```
- **严重程度**: P1
- **并发场景**: 同一 clientId 的两个连接分属不同 vert.x 事件循环（EL1/EL2）；旧连接 closeHandler 异步迟到。
- **触发时序**: T1(EL1) 连接 A 接受、`sessions.put(clientId,A)`，A SUBSCRIBE X → `subs[clientId]={X}` → T2(EL2) 连接 B 接受（同 clientId，设备闪断重连）→ 接管：`put` 返回 old=A → `subscriptions.remove(clientId)` → `A.close()` 异步 → T3(EL2) B SUBSCRIBE Y → `subs[clientId]={Y}`（B 已收 SUBACK，协议层认为订阅生效）→ T4(EL1) A 的 closeHandler 迟到触发 → complete → onCloseCallback → removeConnection(A)：sessions 条件删除安全跳过，但 `subscriptions.remove(clientId)` 无条件执行 → B 的 {Y} 被清 → `findConnections(topic)` 对 B 永失配，B 不会重发 SUBSCRIBE → 服务端下行推送静默丢失直至 B 重连。
- **现状**: 同一个方法里 sessions 用 (key, value) 条件删除、subscriptions 用无条件删除——两表清理语义不一致；接管路径 `addConnection` L36 的 `subscriptions.remove(old)` 是正确语义，缺陷仅在迟到 close 回调路径。
- **风险**: IoT 设备重连闪断（高频常态）后该设备下行通道静默瘫痪；无错误、无日志，故障不可自愈。
- **建议**: removeConnection 改为 `if (sessions.get(clientId) == conn) { subscriptions.remove(clientId); }`，或将订阅集合按连接实例组织（conn → filters）。
- **失效面枚举**: MqttSessionManager.removeConnection（唯一清理入口，中招）；MqttSessionManager.addConnection 接管路径 L36（接管时清旧订阅正确，未中招）；MqttServerMessageService.subscribers（进程内另一张表，与连接生命周期无关，不适用）。
- **信心水平**: 确定
- **误报排除**: 非容器原语误用——跨连接生命周期异步清理的时序错配，交错链完整且后果为静默功能丢失。
- **复核状态**: 未复核
- 复核状态: 已保留(P1)｜新发现——removeConnection 条件删 sessions/无条件删 subscriptions 不对称 + 迟到 close 回调链（acceptEndpoint→setOnCloseCallback→removeConnection）完整回码成立；同案 C1-008/C6-020 三登记一工单（与 C1 侧复核口径一致）；休眠面注记：nop-vertx-mqtt-server 全模块零装配（无 main/resources、无 beans.xml、全仓零外部代码引用，仅 tests/pom 依赖），激活回升条款建议 7.1 对合并工单统一适用

### [C3-019] VertxMqttServer.doStart 不观察 listenFuture：绑定失败时生命周期仍置 ACTIVE（F9 静默失败）

- **文件**: `nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/impl/VertxMqttServer.java:66-75`
- **证据片段**:
  ```java
  mqttServer.exceptionHandler(err -> LOG.error(...))       // 只覆盖 server 运行期异常
          .endpointHandler(this::handleEndpoint);
  listenFuture = mqttServer.listen();                       // Future<MqttServer> 无人观察
  ```
- **严重程度**: P2
- **并发场景**: 启动线程与 vert.x 内部 bind 完成线程（异步 listen）；无跨线程交错，属异步链完成语义缺陷。
- **触发时序**: 端口被占/网络栈拒绝 → `mqttServer.listen()` 返回 failed Future → 无 onFailure/whenComplete/await → doStart 正常返回 → LifeCycleSupport 将状态置 ACTIVE → 服务事实上未监听；唯一暴露面是下游 MQTT 客户端连接失败，日志无任何绑定失败痕迹（Vert.x 不自动记录未观察的 failed future）。
- **现状**: 同文件 doStop 对 `mqttServer.close()` 有 onComplete 记录（对照成立）；listenFuture 字段仅存储、置 null，从未消费。
- **风险**: "半个成功"——生命周期状态与事实背离；部署错误（端口冲突）被吞成静默功能缺失。
- **建议**: `listenFuture.onFailure(err -> { LOG.error(...); stop(); throw ... })` 或 await 后再返回 doStart（对齐 NettyTcpServer.bind().sync() 的同步失败面）。
- **失效面枚举**: VertxMqttServer.doStart 唯一监听点（中招）；VertxMqttServer.doStop close（有 onComplete，未中招）；对照 SocketServer.doStart（同步 bind 失败抛 NopException，未中招）；对照 NettyTcpServer.doStart（bind().sync() 失败抛出，未中招）。
- **信心水平**: 确定
- **误报排除**: 非 error-handling 维度——关注点是异步完成语义（失败未回流到调用方/生命周期状态）。
- **复核状态**: 未复核
- 复核状态: 已驳回·同案并入 C6-023(P2)｜新发现——缺陷本体回码成立，但与 C6-023 同站点同根同修复（VertxMqttServer.doStart listenFuture 无观察者；C6 侧复核已保留并登记本条同案），按合并裁定防重复计数

### [C3-020] MqttServerMessageService dispatcher 挂载 check-then-act：并发首订阅重复挂载 → 入站消息双份分发

- **文件**: `nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/bus/MqttServerMessageService.java:90-114`（subscribe/cancel）、`:104-106`（addPublishListener）
- **证据片段**:
  ```java
  public IMessageSubscription subscribe(String topic, IMessageConsumer listener, ...) {
      if (mqttServer != null && subscribers.isEmpty()) {   // check
          mqttServer.addPublishListener(dispatcher);       // act —— 非原子
      }
      subscribers.computeIfAbsent(topic, k -> new ArrayList<>()).add(listener);
  ```
- **严重程度**: P2
- **并发场景**: 两个业务线程并发执行首批 subscribe（不同 topic）。
- **触发时序**: T1 subscribe(topicA) 读 `subscribers.isEmpty()`=true → T2 subscribe(topicB) 同读 true（T1 尚未 computeIfAbsent）→ 两线程各执行 `addPublishListener(dispatcher)` → publishListeners=[d, d]（CopyOnWriteArrayList 允许同元素重复）→ 此后每条入站 publish 触发 dispatch 两次 → 每个消费者 onMessage 双次调用（重复消费无幂等假设）。次级：cancel 用 `computeIfPresent` 返回（可能为空的）list、键不删除 → subscribers 永非空 → dispatcher 在首个订阅后永不移除（有界残留：单 dispatcher + 空 list 键，F4-lite）。
- **现状**: 挂载/卸载是对"subscribers 是否为空"的 check-then-act，无引用计数或原子状态转移；包装层 wrapHandler 对监听器异常已防护（对照正确）。
- **风险**: 首批并发订阅窗口内引入持久性的重复分发（影响从窗口出现起持续整个进程生命周期）。
- **建议**: 挂载由 `computeIfAbsent` 的返回值驱动（仅"首次建键"线程挂载），卸载在 list 变空且 CAS 移除键成功后执行；或以 AtomicInteger 引用计数守卫 add/remove。
- **失效面枚举**: subscribe（中招，仅首批并发窗口）；cancel（次级：dispatcher 永不移除，有界）；dispatcher 实例唯一（全部订阅共享，重复挂载即全局翻倍）；VertxMqttServer.wrapHandler 的监听器异常防护（未中招）。
- **信心水平**: 确定（窗口窄但交错链完整、后果持续）
- **误报排除**: 非理论竞态降档——交错可写出且后果不随窗口关闭消失。
- **复核状态**: 未复核
- 复核状态: 已驳回·同案并入 C1-012(P2)｜新发现——同站点同根同修复（挂载/摘除边沿非原子）；并入说明：C1-012 复核将其子项(c)（摘除竞态）勘误为不可达死码后，本条正向首挂载双挂载子面（首批并发 subscribe 双读 isEmpty→双挂 dispatcher→全局持久双份分发）仍为该站点存活面，须并入 C1-012 修复批失效面；次级“cancel 键不删→dispatcher 永不移除”与 C1-012 勘误事实一致

### [C3-021] MQTT 下行扇出遇连接关闭同步抛逃逸：扇出中断 + CompletionStage 契约破坏（C3-011 家族新站点）；PUBACK 语义注释失实

- **文件**: `nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/impl/MqttConnection.java:226-239`（同步抛 + 注释失实）；消费方 `MqttServerMessageService.java:79-88`
- **证据片段**:
  ```java
  public CompletionStage<Void> sendAsync(String topic, Object message, ...) {
      if (!endpoint.isConnected()) {
          throw new NopException(MqttErrors.ERR_MQTT_NOT_CONNECTED)...;   // 同步抛，未走 failed future
      }
      // 注释："QoS1（AT_LEAST_ONCE），PUBACK 确认后 future 完成" —— 与实现不符（见现状）
  ```
- **严重程度**: P2
- **并发场景**: 消息总线发送线程与 MQTT 连接关闭事件（事件循环）交叠——findConnections 时刻连接活跃、循环内已断开。
- **触发时序**: T1 sendAsync → findConnections 返回 [connA, connB, connC]（均 isConnected）→ 循环 publish connA 成功 → connB 恰在此刻断开 → connB.sendAsync 同步 throw → for 循环中断：connC 未 publish、`FutureHelper.waitAll` 未构建 → 异常从"返回 CompletionStage"的 sendAsync 调用栈直接逃逸给调用方——同一消息部分送达（半个成功），且异常通道与契约返回类型背离。
- **现状**: 附加事实核查：vertx-mqtt 5.1.8 `MqttEndpointImpl.publish(...)` 字节码显示返回值为 `write(msg).map(packetId)`——socket 写完成即完成，**并非等待 PUBACK**；代码注释宣称的 QoS1 确认语义不成立，调用方会高估送达可靠性。
- **风险**: 设备群发场景下个别设备断连即中断整批下发；同步/异步异常双通道使上层重试与监控逻辑不一致。
- **建议**: MqttConnection.sendAsync 改 `CompletableFuture.failedFuture(...)`；扇出循环 per-conn try/catch 收集 failed future 后统一 waitAll；更正 PUBACK 注释（或真接 publishAcknowledgeHandler 关联确认）。
- **失效面枚举**: MqttConnection.sendAsync（唯一 IMqttConnection 实现，中招）；MqttServerMessageService.sendAsync 扇出循环（消费面，中招）；MqttConnection 其余 endpoint 交互均在事件循环回调内（不适用）；C3-011 已登记的 nop-rpc 站点（同族，另案）。
- **信心水平**: 确定（同步抛确定；PUBACK 语义经字节码核实）
- **误报排除**: 非 error-handling 维度——关注异常传播通道（future vs 栈）对扇出组合子的破坏。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——isConnected 同步抛 + 扇出循环中断 + waitAll 未构建（半个成功）回码成立；PUBACK 注释失实经本复核独立再证（vertx-mqtt 5.1.8 字节码：publish(5参)→publish(7参)→write(msg).map(packetId)，完成于 socket 写非 PUBACK 确认）；休眠面注记同 C3-018

### [C3-022] MqttConnection.sendAsync 从任意调用线程触发 vertx nextMessageId() 非原子自增：并发下行发布 packetId 碰撞

- **文件**: `nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/impl/MqttConnection.java:233-238`（caller 线程直接调 endpoint.publish）；外部证据：vertx-mqtt 5.1.8 `MqttEndpointImpl`（本地仓库 jar 字节码）——`nextMessageId()` 为普通字段 `messageIdCounter` 的读-改-写（非 volatile、非 synchronized、非 Atomic）
- **证据片段**:
  ```java
  CompletableFuture<Void> ret = new CompletableFuture<>();
  endpoint.publish(topic, buffer, MqttQoS.AT_LEAST_ONCE, false, false)   // 在调用方线程执行
          .onSuccess(id -> ret.complete(null))
  ```
- **严重程度**: P2
- **并发场景**: 多个业务线程并发经 MqttServerMessageService.sendAsync 下行推送至同一连接（topic 过滤器重叠即汇聚到同一 conn）。
- **触发时序**: T1、T2 同时进入同一 conn 的 sendAsync → 同时执行 vertx `publish(5参) → nextMessageId()` → 两个读-改-写交叠 → 生成相同 packetId 的两条 QoS1 PUBLISH 写向客户端 → 客户端回两个同 id PUBACK；MQTT 规范"在途报文 packetId 唯一"约定被破坏，客户端可能将第二条按重传处理，投递语义降级/丢消息。
- **现状**: 本模块把 publish 暴露到任意调用线程（`IMessageService.sendAsync` 的调用线程），未做 event loop 归位；vertx 端点的其余交互（init/各 handler）均在事件循环内，无此面。
- **风险**: 高并发下行推送时协议级 id 冲突；QoS1 送达保障在本层即不可靠（与 C3-021 的注释失实叠加，双重高估）。
- **建议**: sendAsync 经连接事件循环串行化发布（`NopVertx.instance().runOnContext` / endpoint context 内 publish），失败语义经 promise 回传。
- **失效面枚举**: MqttConnection.sendAsync（唯一下行发布点，中招）；MqttServerMessageService.sendAsync（并发触发面）；MqttConnection.handlePing/publishHandler 等（事件循环内，未中招）。
- **信心水平**: 很可能（nextMessageId 非原子经字节码确定；交叠窗口为指令级，高负载下可复现）
- **误报排除**: 非平台基础设施重审——缺陷触发方是本模块的跨线程调用方式，vertx 端点按约定应在事件循环驱动。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——caller 线程直接 endpoint.publish 回码成立；nextMessageId 非原子经本复核独立再证（5.1.8 字节码：messageIdCounter 普通 int 读-改-写，publish 与 nextMessageId 均 non-synchronized）；交叠窗口为指令级、“很可能”定级恰当；休眠面注记同 C3-018

### [C3-023] PacketCodecHandler 编解码失败静默吞帧：调用方等到超时而非即时错误（F9）

- **文件**: `nop-network/nop-netty/src/main/java/io/nop/netty/handlers/PacketCodecHandler.java:43-66`（encode 吞）、`:92-109`（decode 吞）
- **证据片段**:
  ```java
  } catch (RuntimeException e) {
      LOG.error("nop.netty.encode-msg-fail", e);
      encodeErrorCount++;
      if (encodeErrorCount > maxEncodeErrorCount) throw e;
      LOG.warn("nop.netty.warn.ignore-encode-msg-fail:errorCount={}", encodeErrorCount);
      out.resetWriterIndex();        // 消息被丢弃，但 write promise 将正常完成
  }
  ```
- **严重程度**: P2
- **并发场景**: 单连接事件循环内顺序执行（无跨线程交错），属异步链异常传播语义缺陷。
- **触发时序**: T1 业务线程 sendAsync 一个不可编码的消息 → event loop encode 抛 RuntimeException → 容错分支 resetWriterIndex、不 rethrow → write promise 正常完成（空载荷）→ RpcMessageHandler 已将 future 登记进 futures → 请求实际未上送，响应永不抵达 → 调用方等待整整 `timeout` 后才收到 NopTimeoutException（精确的编码错误被降级为超时，maxInFlightCount 名额同被占用至超时）。decode 同构：坏帧静默丢弃 → 对端响应丢失 → 同样超时。错误计数成功后清零，持续编码故障呈"每 N 条抛一次"的间歇关闭连接行为。
- **现状**: 容错上限（maxEncodeErrorCount/maxDecodeErrorCount）存在，但容错期内无任何向调用方的失败传播。
- **风险**: 故障定位困难（超时掩盖编码错误）；坏数据源场景下反复占用 in-flight 名额与超时窗。
- **建议**: encode 失败 fail 掉 write promise（经 ChannelPromise 传播给 writeAndFlush 调用方），超上限前的"容错"仅记 metric；decode 失败维持丢弃但暴露计数。
- **失效面枚举**: encode 容错分支（中招）；decode 容错分支（中招）；byte[] 直通分支 L46-47（无 codec 参与，不适用）；TestFragmentChannelHandler（测试切片注入，不适用）。
- **信心水平**: 确定
- **误报排除**: 非 error-handling 维度——关注点是异常未沿异步链（promise）传播导致调用方语义失真。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——encode/decode 容错分支吞异常致 write promise 正常完成（空载荷）→ 调用方等满超时回码成立；消费面经 nop-record-netty（嵌入方 SDK 形态，仓内无更深消费方）

### [C3-024] SftpClient 全链路无超时：网络故障下业务线程与 SSH 会话无限挂起（F5；失效面含 SMTP 次级同族）

- **文件**: `nop-integration/nop-integration-sftp/src/main/java/io/nop/integration/sftp/SftpClient.java:163-165`（`session.connect()`/`channel.connect()` 无参——jsch 默认 timeout=0 即无限）、`SftpConfig.java`（全文无任何 timeout 配置面）、`:295-304`（getInputStream 长传输读同样无界）
- **证据片段**:
  ```java
  session.connect();                                // jsch Session.connect() 默认 0 = 无限
  channel = (ChannelSftp) session.openChannel("sftp");
  channel.connect();                                // 同上
  // SftpConfig：host/port/credentialId/strictHostKeyChecking/knownHostsPath —— 无 timeout 字段
  ```
- **严重程度**: P1
- **并发场景**: 文件操作线程（FileServiceResource.getInputStream/writeToStream 等经 `SftpClientFactory.newClient()` 逐次建连，每次一个独立 SSH 会话）与静默故障的远端。
- **触发时序**: T1 业务线程（含 HTTP 请求线程：FileServiceResourceStore/FileServiceResource 均在请求路径内 newClient）执行 download → 传输中途 SFTP 服务端静默失联（无 RST 的丢包/分区）→ jsch socket 读无限阻塞 → T1 永不返回；N 个并发故障操作 = N 个挂起线程 + N 个半开会话，等待点无任何健康检测覆盖；连接阶段同理（防火墙 drop 场景 connect 无限阻塞）。
- **现状**: 配置面提供了 strictHostKeyChecking 等精细开关，唯独缺超时维度；部署方无任何手段配置（与 SMTP 面不同——见失效面）。
- **风险**: 请求线程池被无限挂起逐步耗尽；SSH 会话在服务端累积；文件服务整体不可用且不可自愈。
- **建议**: `session.setTimeout(ms)`（socket 读超时）+ `session.connect(ms)`/`channel.connect(ms)` 显式传值；SftpConfig 增加 timeout 配置项（默认对齐 OSS SDK 的 10s/50s 量级）。
- **失效面枚举**: SftpClient 全部 channel 操作 listFiles/getFileStatus/uploadFile/uploadResource/downloadFile/downloadToStream/getInputStream/deleteFile（8/8 中招——同一 channel 同一无超时面）+ 构造器 connect（中招）。同族对照：JavaEmailSender.connectTransport L213-226（SMTP `mail.smtp.connectiontimeout/timeout` 默认无限，但 MailConfig.properties 可由部署方显式设置——次级同族，配置义务未文档化）、OssFileServiceClient（AmazonS3 SDK 默认 10s/50s，未中招）、JdkFeishuHttpApi（15s，未中招）、Tencent SMS/Email SDK（默认有界，未中招）。
- **信心水平**: 确定（jsch 默认 timeout=0=无限）
- **误报排除**: 非低频路径——文件操作是运行期常规路径；且超时缺失是配置面缺口而非已声明边界。
- **复核状态**: 未复核
- 复核状态: 已保留(P1)｜新发现——session.connect()/channel.connect() 无参（jsch 默认 0=无限）+ SftpConfig 全文无 timeout 配置面回码成立；可达性经本复核证实（初审 FileServiceResource 主张成立）：SftpClient/SftpClientFactory 实现 IFileServiceClient/IFileServiceClientFactory 平台文件服务后端接口，FileServiceResource 请求路径经 factory.newClient() 可达（部署选择 sftp 后端即触发，为其设计用途）；SMTP 次级同族对照成立；F5 长驻运行期面不适用启动期降档

### [C3-025] TencentEmailSender.sendEmail 吞掉全部发送异常正常返回："不抛即成功"契约下上游误信已发送（F9 + F3 漏修兄弟）

- **文件**: `nop-integration/nop-integration-email-tencent/src/main/java/io/nop/integration/email/tencent/TencentEmailSender.java:166-197`
- **证据片段**:
  ```java
  } catch (TencentCloudSDKException e) {
      String ignoreInfo = "EmailAddressIsNULL";
      if (e.getErrorCode().contains(ignoreInfo)) {
          return;                                  // 显式忽略（有理由）
      } else if ("FailedOperation.FrequencyLimit".equals(e.getErrorCode())) {
          LOG.warn("nop.send-email-exceed-limit", e);
          return;                                  // 显式忽略（有理由）
      }
      LOG.error("nop.err.send-email-fail", e);     // 其余错误：仅记日志 → 正常 return
  }
  ```
- **严重程度**: P2
- **并发场景**: 无跨线程交错；属发送链完成语义缺陷（同步调用面）。
- **触发时序**: 发送失败（InternalError/凭证失效/参数错误等任意未知错误码）→ LOG.error → 正常 return → 消费方契约"不抛即成功"（同模块 JavaEmailSender javadoc 自证：nop-auth bindEmail 无条件 `setEmailSent(true)`）→ 上游状态"邮件已发送"与事实背离 → MFA/验证码链路用户收不到邮件且无重试信号。
- **现状**: 仅两个错误码的显式忽略可辩护；通用 catch 分支吞掉一切。家族对照：TencentSmsSender.sendMessage（throw ERR_SEND_SMS_FAIL）、JavaEmailSender.withTransport（注释明示"记录日志后必须重抛"并给出同一消费契约理由）、FeishuClient.sendMessage（throw）——本类为 F3 漏修兄弟。
- **风险**: 邮件验证码/MFA 静默失败；错误只存在于日志，监控与重试均无触发面。
- **建议**: 未知错误统一 `throw NopException.adapt(e)`，保留两个显式忽略分支并注释各自边界。
- **失效面枚举**: sendEmail（本类唯一发送入口，中招）；同接口 JavaEmailSender.sendEmail/sendMultiEmail（重抛，未中招）；TencentSmsSender.sendMessage/sendMultiMessage（重抛，未中招）；YunpianSmsSender（同步抛，未中招）。
- **信心水平**: 确定
- **误报排除**: 非 error-handling 纯消息/错误码问题——吞异常直接造成上游状态机不一致（F9 明示在范围内）；交叉标注：建议 error-handling 维度复核时一并定性。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——未知错误码吞掉正常 return 回码成立；家族对照（TencentSmsSender/JavaEmailSender/FeishuClient 均重抛）与消费方契约自证（UserMfaSelfService L345 setEmailSent(true) 无条件置位）复核成立；部署装配条件注记（tencent 模块无 beans.xml，IEmailSender bean 由部署声明——MFA 链路为其设计用途）

### [C3-026] SocketClient.recv 重连链耗尽后静默死亡：接收循环僵尸化，客户端"活着但收不到任何消息"（F9）

- **文件**: `nop-network/nop-socket/src/main/java/io/nop/socket/SocketClient.java:197-222`（recv 重连链）、`:224-233`（doRecv）、`:235-238`（call）、`:240-251`（send 的静默丢弃分支）；根因协作 `nop-kernel/nop-commons/.../retry/RetryHelper._retryExecute`（耗尽 → `promise.completeExceptionally` 无观察者）
- **证据片段**:
  ```java
  RetryHelper.retryExecute(() -> { ... reconnect(); ... }, config.getReconnectPolicy(), timer, null)
          .thenRun(() -> recv(executor, handler));   // 耗尽时 completeExceptionally：无 exceptionally，无人观察
  ```
- **严重程度**: P2
- **并发场景**: executor 线程（接收循环）、timer 线程（重试调度）与业务线程（call/send）。
- **触发时序**: 连接异常（服务器重启/网络断）→ 重连按 policy 重试至耗尽 → retryExecute future completeExceptionally → thenRun 不执行、无 exceptionally/whenComplete → 接收循环静默终止（无日志、无回调）；此后 `call()`：send 走 `os==null` 分支 warn 后静默丢弃 → recv 返回 null → 调用方把"连接已死"误读为"无消息"；客户端变僵尸直至进程重启。次级：`handler.accept` 抛出（SimpleRpcClient.executeRecv 内 JSON 解析失败）与 I/O 异常混同走重连路径——一条坏消息即触发整连接重连。
- **现状**: onChannelClose 回调只在 cleanup 路径触发；重连链终结无任何终态通知。
- **风险**: 依赖 notice 推送的链路（SimpleRpcClient CMD_NOTICE）静默断供；僵尸客户端无告警。
- **建议**: 重连链挂终态处理器（exceptionally → LOG.error + onChannelClose 回调 + closed 置位）；doRecv 内对 handler.accept 单独 catch 分流（消息级错误不触发重连）。
- **失效面枚举**: recv(Executor, Consumer)（唯一订阅入口，中招；生产消费者 SimpleRpcClient.executeRecv ← SimpleRpcClientFactory.newInstance，触发面）；同步 recv()/call() 直用路径（不经重连链，不适用）。
- **信心水平**: 确定
- **误报排除**: 非已声明边界——无任何文档声明"重连耗尽即静默放弃"；终结无观察者是 F9 直接命中。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——重连链耗尽 completeExceptionally 无观察者 + thenRun 不执行回码成立；handler.accept 异常混同 I/O 异常走重连、send os==null 静默丢弃两个次级面成立；消费面 nop-idea-plugin/nop-xlang-debugger（dev-tools，CL-015 同面）

### [C3-027] RpcMessageHandler.checkTimeout 以单个异常实例完成多个 future：可变 NopException 跨等待者串扰

- **文件**: `nop-network/nop-netty/src/main/java/io/nop/netty/handlers/RpcMessageHandler.java:76-99`
- **证据片段**:
  ```java
  if (exp == null)
      exp = new NopTimeoutException();       // 整轮共用一个实例
  future.future.completeExceptionally(exp);  // 多个 future → 同一 Throwable
  ```
- **严重程度**: P3
- **并发场景**: event loop 定时线程超时清理 vs 多个等待方线程消费各自 future。
- **触发时序**: 同一轮超时清理将同一 NopTimeoutException 完成给 N 个 future → 任一消费方对异常 `.param(...)`（NopException 可变 params）或 `addSuppressed` 即对全部等待者可见；所有等待者共享同一份堆栈（堆栈采集于清理时刻，误导定位到 checkTimeout 而非各自发送点）。
- **现状**: 每轮清理仅首个过期条目建实例（`if (exp == null)`），其余复用。
- **风险**: 诊断失真 + 参数串扰；无功能损坏。
- **建议**: 每 future 独立建异常（或轻量工厂），实例共享仅保留给不可变异常。
- **失效面枚举**: checkTimeout（唯一超时清理点，中招）；channelInactive 的 `future.future.cancel(false)`（CancellationException 每 future 独立，未中招）；RpcChannelState 超时路径（nop-rpc，每条独立建异常，未中招——对照成立）。
- **信心水平**: 确定
- **误报排除**: 非原语使用误报——可变异常跨 future 共享是复合层缺陷，且平台已有正确对照。
- **复核状态**: 未复核
- 复核状态: 已保留(P3)｜新发现——单轮共用一个 NopTimeoutException 实例完成多 future 回码成立；诊断失真+可变异常参数串扰无功能损坏，P3 恰当

### [C3-028] NettyTcpClient.sendOneway 完成监听不校验连接结果：失败连接上静默写丢弃

- **文件**: `nop-network/nop-netty/src/main/java/io/nop/netty/tcp/NettyTcpClient.java:296-311`
- **证据片段**:
  ```java
  channelFuture.addListener(new GenericFutureListener<>() {
      public void operationComplete(Future<? super Void> future) {
          channelFuture.channel().writeAndFlush(msg);   // 未校验 future.isSuccess()
          channelFuture.removeListener(this);
      }
  });
  ```
- **严重程度**: P3
- **并发场景**: 调用线程挂接 listener，连接完成线程执行写。
- **触发时序**: sendOneway 在连接建立前调用 → 连接失败完成 → listener 仍执行 → 对失败 channel writeAndFlush → 写失败且 ChannelFuture 未监听 → 消息静默丢弃、无任何日志（对照 doSend L342-347 的 sendAsync 路径有 ERR_TCP_CONNECT_FAIL 显式失败）。成功路径 writeAndFlush 返回值同样未监听（出站高水位/连接关闭时静默丢）。
- **现状**: oneway 契约为尽力投递，但"失败即无痕丢弃"连观测面都没有。
- **建议**: listener 内 isSuccess 分支失败时 LOG.warn；writeAndFlush 后挂 `ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE` 或日志监听（CloseOnErrorHandler.write 已有该手法，对照成立）。
- **失效面枚举**: sendOneway（中招）；sendAsync→doSend（isSuccess 分支完整，未中招）；HeartbeatHandler.writeAndFlush L31（同族未监听返回值，中招-lite）。
- **信心水平**: 确定
- **误报排除**: 非"oneway 即可静默"的已声明边界——无文档声明；缺陷在观测缺失而非投递语义。
- **复核状态**: 未复核
- 复核状态: 已保留(P3)｜新发现——listener 不校验 isSuccess + writeAndFlush 返回值未监听回码成立；复核补充：isDone 分支对失败 channelFuture 同样直接 writeAndFlush，同属未观测写（与 listener 分支同批修复）

### [C3-029] NettyTcpServer.sendToChannel 无 IRpcMessageHandler 时 NPE 同步抛：CompletionStage 契约破坏 + 与客户端侧处理不一致（F3）

- **文件**: `nop-network/nop-netty/src/main/java/io/nop/netty/tcp/NettyTcpServer.java:280-285`；对照 `NettyTcpClient.java:332-348`
- **证据片段**:
  ```java
  private CompletableFuture<Object> sendToChannel(Channel channel, Object msg, int timeout) {
      CompletableFuture<Object> ret = new CompletableFuture<>();
      IRpcMessageHandler handler = channel.pipeline().get(IRpcMessageHandler.class);
      handler.send(msg, timeout, ret);     // handler == null → NPE 同步抛出
      return ret;
  }
  ```
- **严重程度**: P3
- **并发场景**: 服务端向已连接 channel 下发消息（管理/推送线程）；纯推送型管线（无 RpcMessageHandler）即可稳定触发。
- **触发时序**: pipeline 未装配 IRpcMessageHandler（推送型业务）→ sendToChannel/sendToAnyChannel/sendToFirstChannel → `handler.send` NPE 同步抛——返回类型 `CompletableFuture<Object>` 契约破坏（C3-011 同族新站点）；对照客户端侧 doSend L335-340 已显式处理为 ERR_NETTY_NO_RPC_HANDLER failed future——F3 漏修兄弟。channel 恰好断开的场景由 RpcMessageHandler.send 的 ERR_CHANNEL_NOT_ACTIVE 兜住（不适用）。
- **现状**: 客户端/服务端同一抽象（IRpcMessageHandler）的缺失处理不对称。
- **建议**: handler==null 时 `ret.completeExceptionally(new NopException(ERR_NETTY_NO_RPC_HANDLER)...)` 对齐客户端。
- **失效面枚举**: sendToChannel（中招）；sendToChannel(String)/sendToAnyChannel/sendToFirstChannel（均经此，中招）；getChannelById/getFirstChannel/getAnyChannel 的 NO_AVAILABLE_CHANNEL 分支（显式同步异常、future 契约未涉，不适用）。
- **信心水平**: 确定
- **误报排除**: 非低频优雅性问题——纯推送管线是合法装配形态，缺失处理直接 NPE。
- **复核状态**: 未复核
- 复核状态: 已保留(P3)｜新发现——sendToChannel handler==null NPE 同步抛回码成立；客户端 doSend ERR_NETTY_NO_RPC_HANDLER 对照成立（F3 漏修兄弟）

### [C3-030] TencentSmsSender.sendMultiMessage 批量 fail-fast 无逐条结果：半个成功无恢复语义（F2；对照 sendMultiEmail 已声明）

- **文件**: `nop-integration/nop-integration-sms-tencent/src/main/java/io/nop/integration/sms/TencentSmsSender.java:193-199`
- **证据片段**:
  ```java
  public void sendMultiMessage(List<SmsMessage> messages) {
      ResolvedCredential credential = resolveCredential();
      SmsSingleSender sender = createSender(credential);
      for (SmsMessage message : messages) {
          sendMessage(sender, credential.sign, message);   // 第 k 条失败抛出，1..k-1 已发出，无逐条结果
      }
  ```
- **严重程度**: P3
- **并发场景**: 无跨线程交错；批量提交完成语义缺陷。
- **触发时序**: 5 条短信批量发送 → 第 3 条命中无效模板/黑名单 → ERR_SEND_SMS_FAIL 抛出 → 调用方只知"失败"，前 2 条已实际发出、后 2 条未尝试 → 整批重试将重复发送前 2 条（短信幂等无保障），不重试则后 2 条永久丢失。
- **现状**: 同族 JavaEmailSender.sendMultiEmail L141-145 javadoc 明示 fail-fast 为契约边界（声明后不算缺陷，规则 6）；本类无对应声明，属未声明边界。
- **风险**: 批量通知场景的重复发送/漏发二义。
- **建议**: 逐条捕获并返回 per-item 结果；或补 javadoc 声明对齐 sendMultiEmail。
- **失效面枚举**: sendMultiMessage（唯一批量入口，中招）；sendMessage 单发（完整异常传播，未中招）；YunpianSmsSender（无批量入口，不适用）。
- **信心水平**: 确定
- **误报排除**: 非 error-handling 维度——关注多目标提交的部分成功语义（F2）。
- **复核状态**: 未复核
- 复核状态: 已保留(P3)｜新发现——批量 fail-fast 无逐条结果回码成立；JavaEmailSender.sendMultiEmail L141-145 fail-fast javadoc 已声明边界对照成立（规则 6），未声明差距定 P3 恰当

### [C3-031] FeishuBindProvider 单次票据消费滞后于身份解析：并发回调双绑定（TOCTOU）

- **文件**: `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/bind/FeishuBindProvider.java:172-189`
- **证据片段**:
  ```java
  TicketEntry entry = tickets.get(ticketId);          // T1、T2 均命中（票据未消费）
  ...
  String openId = resolveOpenIdServerSide(code);      // 2 次 HTTP，秒级窗口
  tickets.remove(ticketId);                           // 之后才原子消费——两者都成功
  ```
- **严重程度**: P3
- **并发场景**: 两个回调请求线程持有同一 ticketId（票据 id 经 OAuth redirect `state` 参数流转，存在 Referer/共享终端泄露面）。
- **触发时序**: T1 合法用户回调命中 ticket → 服务端身份解析（秒级 HTTP 窗口）→ T2 攻击者/双击回调以同一 ticketId + 自己的 OAuth code 进入 → 同样命中（entry 未消费、未过期）→ 双双 remove 成功、双双返回 BINDING_COMPLETED（不同 openId）→ "单次绑定流"产出两个绑定结果，下游绑定记录语义未定义。
- **现状**: UUID 不可猜（L120-124 注释）缓解了猜测面；注释明示"消费延后到解析成功以便失败重试"是刻意取舍，但无 consuming 状态标记，取舍未覆盖并发面。
- **风险**: 身份绑定正确性受损（security-adjacent）；危害前置条件是 ticketId 泄露。
- **建议**: 先原子 `remove` 消费再解析（失败回填恢复）；或 entry 状态机 PENDING→CONSUMING（`ConcurrentHashMap.compute` CAS 转移）。
- **失效面枚举**: onChannelScanCallback（唯一消费入口，中招）；createBindTicket（纯创建，不适用）；purgeExpired（只清过期项，与并发消费无关，不适用）。
- **信心水平**: 确定（竞态可具体化；危害受 ticketId 保密性前置条件限制故定 P3）
- **误报排除**: 非"已有 UUID 防护"的已声明边界——注释只论证了不可猜测性，未覆盖持票者并发。
- **复核状态**: 未复核
- 复核状态: 已驳回·同案并入 C1-015(P2)｜新发现——同站点同根（绑定 ticket 单消费 TOCTOU：get→双次 HTTP→remove 非原子）；C1-015 证据更精确（其复核已做注释转述勘误）且判级 P2 高于本条 P3，按合并裁定防重复计数，修复随 C1-015

### [C3-032] OssFileServiceClient.makeBucket check-then-act：并发首建桶一端 409 失败

- **文件**: `nop-integration/nop-integration-oss/src/main/java/io/nop/integration/oss/OssFileServiceClient.java:44-56`
- **证据片段**:
  ```java
  try {
      boolean b = client.doesBucketExist(bucketName);   // 检查
      if (b) return;
  } catch (AmazonS3Exception e) { LOG.debug(...); }
  client.createBucket(bucketName);                       // 行动——并发下后者 409
  ```
- **严重程度**: P3
- **并发场景**: 两个业务线程首次并发上传至同一新桶（autoCreateBucket=true）。
- **触发时序**: T1、T2 同时 doesBucketExist=false → 双双 createBucket → 后者收 BucketAlreadyOwnedByYou（409）异常 → 该次上传整体失败（未捕获该特定错误码）；错误后重试自愈，但首建窗口的失败对调用方表现为随机上传失败。
- **现状**: 检查异常被降级 debug 后继续 createBucket，无 409 容忍分支。
- **风险**: 部署初期/测试环境的间歇性上传失败；无数据损坏。
- **建议**: createBucket 捕获 BucketAlreadyOwnedByYou/BucketAlreadyExists 视为成功（S3 幂等惯用法）。
- **失效面枚举**: makeBucket（uploadFile/uploadResource 两调用方共用，中招，仅首建窗口）；其余 OssFileServiceClient 操作无 check-then-act 面（不适用）。
- **信心水平**: 确定
- **误报排除**: 非低频粗锁误报——具体错误码级别的幂等容忍是标准做法缺失。
- **复核状态**: 未复核
- 复核状态: 已保留(P3)｜新发现——check-then-act 无 409 容忍回码成立；minio doesBucketExist 恒 true 注释佐证检查面不可靠；首建窗口错误后自愈，P3 恰当

---

## 维度集外线索（overlay C2/C4/C5/C7/C8/C9）

（编号续接 nop-network-integration-CL-011 起；CL-001…010 已被 C1 占用）

### [CL-011] (C2 上下文传播) BaseContext.close 跨线程不清 ThreadLocal 为平台级根因
C3-007 的根因在 nop-api-core：`BaseContext.close()`（BaseContext.java:315-327）仅当"close 执行线程的 ThreadLocal 恰为 this"时清理。任何"创建线程 A attach、完成线程 B close"的组合都滞留。建议 C2 维度从平台侧评估：close() 改为记录 attach 线程并在其上 detach，或提供显式 detach 归还协议。**以 C2 维度复核为准。**

### [CL-012] (C7/F7 OOM 同族) BASE64_FORM 上传整文件入内存，峰值 ≈ 3× 文件大小
`ApacheHttpClient.uploadByBase64Form:484-505`（readBytes + base64 + ByteArrayOutputStream 三份拷贝）、`JdkHttpClient.uploadByBase64Form:549-557`、`OkHttpClientImpl.uploadAsync:286-299` 三实现同族。无文件大小护栏、无包络声明。BINARY 模式为流式替代，BASE64 为显式 opt-in，故未列 C3 发现，交 C7 按规模包络口径裁定。

### [CL-013] (C7/F7 OOM 同族) 非 2xx 响应体在 SSE 消费器中无界累积
`LineAsyncDataConsumer.data:66-70`（`!success` 时把整个 error body 追加进无界 StringBuilder，最终进 ERR_HTTP_RESPONSE_ERROR 的 ARG_BODY）；同类：JdkHttpClient fetch 默认 `ofByteArray` 全量缓冲（常规，包络内）。恶意/异常服务端可返回 GB 级 error body。交 C7 裁定是否需要 maxErrorBodyLength 上限。

### [CL-014] (C5 线程池化) 下载/SSE 阻塞读回退 GlobalExecutors.globalWorker
`JdkHttpClient.executor():423-425` 与 Jdk `ServerEventPublisher.executor():56-58` 在 client 未配置 executor 时回退平台全局工作线程池，长阻塞下载读取/SSE readLine 占用全局池线程。与 C3-002/003 的幽灵任务叠加时泄漏面扩大到平台共享池。交 C5 评估隔离策略。

### [CL-015] (C5 线程池化) SimpleRpcServer 同步执行 handler.call 无超时 + 工厂并发 init 双 timer
`SimpleRpcServer.handleCommandSync:215-235` 在 socket executor 线程上同步 `handler.call(...)`（阻塞等待 future），服务实现返回永不完成的 future 时按 `serverConfig.getThreadPoolSize()` 逐线程耗尽，socket 服务整体停摆（IDE 调试插件场景，包络小）。另 `SimpleRpcClientFactory.newInstance:112-129` 每次调用执行 init()，并发调用 newInstance 可各建一个 timer（ownTimer 双写，先建者泄漏）。交 C5 一并评估。

### [CL-016] (C7 规模面) OssFileServiceClient.listFiles 无分页：>1000 对象静默截断
`OssFileServiceClient.listFiles:58-72` 用 `client.listObjects(bucketName, remotePath)` 单页拉取（S3 默认 1000 条），无 `isTruncated/getNextMarker` 循环 → 大目录静默只返回前 1000 条，不报错。下游清理/同步逻辑会误判目录完整性（如"目录已清空"）。交 C7 按规模包络口径裁定是否补分页循环。

### [CL-017] (C5 线程池化) SocketServer.processCommand 同步 handler 无超时 + 默认 cachedThreadPool
`SocketServer.processCommand:297` 在连接处理线程同步执行 `handler.onCommand(addr, request)`，无超时；executor 未配置时回退 `GlobalExecutors.cachedThreadPool()`（`doStart:139-141`）→ 慢 handler 下连接线程无界增长。与 CL-015（SimpleRpcServer 同步 handler 无超时）同族。交 C5 一并评估隔离与超时策略。

### [CL-018] (C2 上下文/线程约定) MQTT 异步 auth 完成线程未归位事件循环
`VertxMqttServer.handleEndpoint:82-93` 的 `checkAuthAsync(...).whenComplete` 在完成线程执行 `endpoint.reject` / `acceptEndpoint`（含 MqttConnection 构造、handler 注册与 `endpoint.accept()`）——vertx 端点按事件循环约定驱动。默认 `SimpleMqttAuthChecker` 返回已完成 future（同步路径，不触发）；自定义异步 checker（DB/HTTP 查询）即在完成线程操作端点，与事件循环上的后续帧处理存在交错面。建议 C2 评估以 `context.runOnContext` 包裹 accept/reject。**以 C2 维度复核为准。**

### [CL-019] (C7/F4) FeishuBindProvider 票据表过期清理只搭车于读写路径
`FeishuBindProvider.tickets:67` + `purgeExpired:239-242` 仅在 createBindTicket/onChannelScanCallback 时执行——领票突发后的静默期条目滞留内存（小对象、按创建率有界，非无界账本）。交 C7 判是否需要定时清理/TTL 扫描。

### [CL-020] (C8 清理对称) OssFileServiceClient.getInputStream 生命周期责任全部外移调用方
`OssFileServiceClient.getInputStream:170-177` 返回 S3ObjectContent 后即失去管理：调用方不 close 即滞留一条 HTTP 连接（S3 SDK 流与连接绑定至流关闭/EOF）。现有消费者 `FileServiceResource.getInputStream:56-75` 已正确用 FilterInputStream 包装双关；直用 `IFileServiceClient` 的其他消费者无此保护且接口 javadoc 未声明 close 义务。交 C8 评估契约注释/统一包装器。

---

## 附：异步入口清单（C3 执行步骤 1 交付物）

| 入口 | 类 | 组合子使用 | 异常传播 | 取消/超时 |
|---|---|---|---|---|
| fetchAsync | Apache/Jdk/OkHttp ×3 | 手工 promise + 回调 | Apache/Jdk 完整；OkHttp onResponse 豁口（C3-001） | Apache bindCancelToken 完整；Jdk/OkHttp 监听器不清理（C3-012）；OkHttp 忽略 per-request timeout（C3-014） |
| downloadAsync | ×3 | 416 重试 whenComplete 桥接 | 完整 | Jdk body 阶段失效（C3-002）；收尾重活入回调线程（C3-004） |
| uploadAsync | ×3 | — | OkHttp 豁口（C3-001） | 同 fetch |
| fetchServerEventFlow | Apache/Jdk | Flow.Publisher + AbstractServerEventSubscription | Jdk onError 路由完整；apache 依赖 reactor 异常派发 | Jdk readLine 取消失效（C3-003）；apache cleanup 生效 |
| RPC callAsync ×8 实现 | rpc-core/http/simple | thenCompose/whenComplete/channelState | 同步抛出逃逸族（C3-011）；SimpleRpcClient 派生 future 错位（C3-009） | Cancellable/Polling 桥接完整；RpcChannelState timer 超时完整 |
| 拦截器 ×4 | ApiContext/ClientContext/FlowControl/Stat/Log | whenComplete 挂 close/统计 | ApiContext 同步抛豁口（C3-006/007） | 不适用 |
| RpcTaskMonitor | 定时任务 | scheduleWithFixedDelay + whenComplete | 就地吞异常（正确） | 跨轮竞态（C3-010） |

---

## 维度复核结论（WI 4.3 C3 独立复核）

- 复核员：C3 维度独立复核员（CRA WI 4.3，与初审员不同 task）
- 日期：2026-10-20
- 方法：33 条发现（C3-001…033，含 round2 C3-033）逐条回码验证（live code 逐文件实读，含 vertx-mqtt 5.1.8 jar 字节码独立再证）+ 触发时序论证核验 + 严重度校准 + `ai-dev/audits/cra-index.md`（WI 1.1–4.2 台账）查重 + C1/C6 复核已定跨维裁定指针核对 + 休眠面/装配面 grep 取证
- 输入的已知跨维裁定核对结果：C1-004→C3-010 并入（本条为主登记，一致）；C6-008→C1-002/C3-008、C6-009→C3-006（C3 侧主登记维持，一致）；C6-014→C1-001（非 C3 面）；C6-024 升格 P1 与 C3-015 对齐（一致，休眠面新证据单列 7.1）；C1-009(P1)↔C3-017(P2)、C6-021(P1)↔C3-016(P2) 判级分歧留 7.1（C3 侧意见见同案清单）

### 判定统计

| 判定 | 数量 | 条目 |
|---|---|---|
| 保留 | 30 | C3-001…018、C3-021…030、C3-032、C3-033（详见逐条判定表） |
| 驳回（同案并入，非证伪） | 3 | C3-019→C6-023(P2)、C3-020→C1-012(P2)、C3-031→C1-015(P2) |
| 降档 / 升档 | 0 / 0 | — |

**最终保留严重度分布：P1×8 / P2×15 / P3×7。**

**现状标注分布（保留 30 条）**：默认装配 live×5（C3-002/003/005/013/033——JDK 为 http-api.beans.xml 默认 raw client、ContextHttpServerFilter 经 nop-biz 默认装配）；装配/消费面可达×19（部署装配后端、opt-in 实现、嵌入方 SDK、dev-tools 消费面）；激活休眠面×3（C3-015/016/017——feishu bean 已注册但生产激活唯一路径 `ChannelConnectorManager.startAll` 全仓非测试零调用，WI 3.3 渠道栈休眠裁定延伸）；零装配休眠面×3（C3-018/021/022——nop-vertx-mqtt-server 无 main/resources、无 beans.xml、全仓零外部代码引用）。

### 逐条判定表（C3-001…033）

| 发现 | 初审 | 复核判定 | 核验要点 |
|---|---|---|---|
| C3-001 | P1 | 保留 P1 | 三 enqueue 站点逐核（uploadAsync try-with-resources 无 catch 仍逃逸）；future 永不完成族跨单元第 4 例；OkHttp opt-in |
| C3-002 | P1 | 保留 P1 | ofInputStream headers 完成契约成立；JDK 默认 client（live）；WI 3.1 MA-08 幽灵任务 P1 先例对齐 |
| C3-003 | P1 | 保留 P1 | cancel(true) 对已完成 future 无效 + readLine 无超时成立；取消路径线程泄漏直击 P1 校准 |
| C3-004 | P1/P2 | 保留（P1 apache 面/P2 jdk+okhttp 面） | 嵌套 get(30s) 成立；加重：HC5 默认 ioThreadCount=1（非小部署特例） |
| C3-005 | P1 | 保留 P1 | expected==null 跳过 rename 成立；设计契约 §3.3 + Apache 对照 + 默认 DownloadOptions 三件证实；错误以成功交付族第 4 例 |
| C3-006 | P2 | 保留 P2 | 同步抛豁口成立；无默认注册点维持 P2；C6-009 已并入 |
| C3-007 | P2 | 保留 P2 | 三层机制回码成立；tenantId/traceId 为字段不被 attributes.clear() 清除；止血点核验（getOrCreateContext 有 isClosed 检查） |
| C3-008 | P2 | 保留 P2 | 锁内 token HTTP 成立；失效面按 round2 扩展（仅 fetchAsync 有 token）；C6-008 已并入 |
| C3-009 | P2 | 保留 P2 | 派生 future 错位 + timeout<=0 永久滞留成立；MessageRpcClient 正确对照；dev-tools 消费面 |
| C3-010 | P2 | 保留 P2 | 跨轮 RMW + 非 volatile 成立；C1-004 已并入（本条主登记）；RpcTaskMonitor 零装配注记 |
| C3-011 | P2 | 保留 P2 | 5 抛出站点 + 防护矩阵逐点成立；round2 扩展站点确认 |
| C3-012 | P2 | 保留 P2 | 10 站点穷举复核一致；round2 扩展 2 站点确认；bindCancelToken 单点修复成立 |
| C3-013 | P3 | 保留 P3 | MDC 断链成立；nop-biz 默认装配（live） |
| C3-014 | P3 | 保留 P3 | per-request timeout 被 OkHttp 丢弃成立；全局 readTimeout 兜底有界 |
| C3-015 | P1 | 保留 P1 | 3 调用点 + 15s 对照成立；C6-024 对齐核验一致；休眠面注记留 7.1 统一适用 |
| C3-016 | P2 | 保留 P2 | stop×在途 connect 竞态成立；与 C6-021(P1) 判级分歧留 7.1（意见见同案清单） |
| C3-017 | P2 | 保留 P2 | 无 connected/代际守卫成立；与 C1-009(P1) 判级分歧留 7.1（意见见同案清单） |
| C3-018 | P1 | 保留 P1 | 清理不对称 + 迟到 close 链完整成立；三登记一工单（C1-008/C6-020）；休眠面注记留 7.1 |
| C3-019 | P2 | **驳回·并入 C6-023(P2)** | 本体成立，与 C6-023 同站点同根同修复（C6 复核已登记同案） |
| C3-020 | P2 | **驳回·并入 C1-012(P2)** | 本体成立；正向双挂载子面并入 C1-012 失效面（其子项(c) 已勘误为死码后本面仍存活） |
| C3-021 | P2 | 保留 P2 | 同步抛 + 半个成功成立；PUBACK 注释失实经字节码独立再证 |
| C3-022 | P2 | 保留 P2 | nextMessageId 非原子经字节码独立再证（且 publish/nextMessageId 均 non-synchronized） |
| C3-023 | P2 | 保留 P2 | 容错分支吞异常致 promise 正常完成成立 |
| C3-024 | P1 | 保留 P1 | jsch 无限超时 + 配置面缺口成立；可达性证实（IFileServiceClient sftp 后端）；F5 运行期面不适用启动期降档 |
| C3-025 | P2 | 保留 P2 | 吞异常正常返回成立；消费方契约自证（setEmailSent(true)）成立 |
| C3-026 | P2 | 保留 P2 | 重连链静默死亡成立；dev-tools 消费面 |
| C3-027 | P3 | 保留 P3 | 单实例多 future 成立 |
| C3-028 | P3 | 保留 P3 | listener 不校验 isSuccess 成立；复核补充 isDone 分支同面 |
| C3-029 | P3 | 保留 P3 | handler==null NPE 成立；客户端对照成立 |
| C3-030 | P3 | 保留 P3 | fail-fast 未声明成立；sendMultiEmail 已声明边界对照成立 |
| C3-031 | P3 | **驳回·并入 C1-015(P2)** | 本体成立；同站点同根，C1-015 证据更精确且判级更高 |
| C3-032 | P3 | 保留 P3 | check-then-act 无 409 容忍成立 |
| C3-033 | P2 | 保留 P2 | 三路径全部证实（判定与状态行见 C3-round2.md） |

### 高风险/不确定条目子项复核说明

- **C3-001**（family 风险）：3 个 enqueue 站点逐一核对——fetchAsync（L66-88，无 try/catch，中招）、uploadAsync（L330-352，try-with-resources 仅关闭 response、**无 catch 子句**，`body().string()` 的 IOException 照样逃出 onResponse，中招）、doDownloadAsync（L165-227，catch(Exception)→completeExceptionally，未中招）。OkHttp `AsyncCall.signalledCallback` 吞 onResponse 内抛异常的语义按 3.x/4.x 同构核验成立。
- **C3-004**：三实现嵌套阻塞点逐一核对（Apache L370-387 reactor 线程／Jdk L475-487 client executor 线程／OkHttp L239-252 dispatcher 线程）；`ApacheHttpClientHelper.createReactorConfig`（L102-110）证实 ioThreadCount 仅在配置 >0 时覆写——HC5 默认即 1，初审"小部署常见"实为**默认配置**，加重不降级。
- **C3-005**：设计契约对照（`ai-dev/design/nop-network/file-transfer-design.md` §3.3 将"校验后原子 rename"写入完成状态机、§3.4 的"无校验来源即不校验"仅豁免校验不豁免 rename）+ 三实现行为分歧（Apache `handleDownloadResult` 无条件 `Files.move`）+ 默认值（DownloadOptions L19/34/39）三点交叉证实；本站点成功路径流关闭缺口已由 C6-001（P0）另案覆盖，修复须同批。
- **C3-015**：transport.send 3 个调用点全部定位（FeishuClient L277 sendHeartbeat／L324 onOpen 握手／L353 ACK 回发）；对照面 JdkFeishuHttpApi 15s timeout×3（L115/135/156）核实；心跳失败 LOG.debug（L279）核实。
- **C3-018**：完整回调链核实（VertxMqttServer.acceptEndpoint → `connection.setOnCloseCallback(() -> sessionManager.removeConnection(connection))` → MqttConnection.complete L211-224）；`addConnection` L36 接管路径正确清旧订阅对照成立。
- **C3-021/022**（字节码独立再证）：vertx-mqtt 5.1.8 `MqttEndpointImpl`——publish(5参)→publish(7参) 返回 `write(msg).map(header.packetId())`（完成于 socket 写，**非 PUBACK**）；`nextMessageId()` 为 `messageIdCounter` 普通 int 读-改-写且 publish/nextMessageId 方法均 **non-synchronized**——初审字节码结论独立复核一致。
- **C3-024**：SftpClient L163/165 无参 connect（jsch timeout=0=无限）、SftpConfig 全部字段（host/port/username/password/keyPath/passphrase/credentialId/strictHostKeyChecking/knownHostsPath——无 timeout）核实；**可达性专门复核**：SftpClient implements IFileServiceClient、SftpClientFactory implements IFileServiceClientFactory，FileServiceResource（nop-integration-file-local）在请求路径经 `factory.newClient()` 使用——初审可达性主张成立（部署选择 sftp 后端即触发）。
- **C3-033**：三路径逐行证实（见 C3-round2.md 状态行）。

### 同案命中清单（含跨维指针 C3 侧意见）

| C3 条目 | 同案/同族 | 关系 | C3 侧意见 |
|---|---|---|---|
| C3-001 | future 永不完成族（WI 1.6 C8-012／WI 3.2 C1-001／WI 4.2 C3-001） | 跨单元同族第 4 例，**零同站点重复，单列登记** | 修复批与族内三案同批对齐（失败路径必须 completeExceptionally） |
| C3-005 | 错误以成功交付族（WI 3.1 C7-002／WI 3.3 C3-001／WI 3.4 C3-005） | 跨单元同族第 4 例，**零同站点重复，单列登记** | 修复批与族内三案同批对齐（成功信号与产物落地原子性） |
| C3-015 | C6-024（升格 P1 对齐） | 同站点互补面（C3=异步链阻塞／C6=send+close 资源面） | 对齐关系核验一致；新增休眠面证据（startAll 零调用）建议 7.1 对两条**统一**适用激活回升条款 |
| C3-016 | C6-021(P1) | 同站点同根，判级分歧留 7.1 | 本体与修复点（代际校验）无分歧，纯校准分歧；建议合并单工单 |
| C3-017 | C1-009(P1) | 同站点同根，判级分歧留 7.1 | C1-009 触发(a)依赖 onClose/onError 双终态回调序列，JDK WebSocket 契约两者互斥，该前提需 C1 侧复核；触发(b)（stop→start 重启窗口）独立扎实——倾向以 C1-009 为主登记合并 P1 |
| C3-018 | C1-008(P1)/C6-020(P1) | 三登记一工单 | 维持 P1 登记；休眠面 grep 证据（模块零装配）建议 7.1 对合并工单统一适用激活回升条款 |
| C3-019 | C6-023(P2) | 同站点同根同修复 | **驳回并入** |
| C3-020 | C1-012(P2) | 同站点同根同修复 | **驳回并入**；正向双挂载子面并入 C1-012 修复批失效面 |
| C3-031 | C1-015(P2) | 同站点同根 | **驳回并入** |
| C3-021 | C3-011 | 同族新站点（同步抛逃逸 CompletionStage 契约） | 已在 C3-021 失效面自注，修复同批 |

### 台账查重结论（cra-index WI 1.1–4.2）

- nop-network（nop-http/nop-rpc/nop-netty/nop-socket/nop-vertx/nop-codec）与 nop-integration 十叶在 WI 1.1–4.2 台账中**无任何既有审计单元覆盖**（已审计单元为 stream/job/wf/task/retry-tcc/batch/kernel/core/service-framework/persistence/ai/message/cluster）——33 条发现与 P1 台账逐条比对**零同站点重复立项**。
- 族关联 2 处单列（C3-001 future 永不完成族、C3-005 错误以成功交付族），均为跨单元同族非同站点。
- WI 2.3 CL-022（JDK Delayer orTimeout 11 站点）与本单元无交集（初审已证 orTimeout 零命中）；WI 2.3 CL-025（IHttpResponse 断连观测平台原语）与 C3-002/003 修复联动（非重复），修复时同步评估。
- 维度内跨文件同案 3 处已按合并裁定驳回并入（C3-019/020/031），驳回均附指针，不产生计数损失争议。

### 降档口径核对

- 长驻运行期面不适用启动期降档：C3-015（长驻 WS 心跳/重连）、C3-024（运行期文件操作）等运行期面均维持原判级，无启动期降档误用。
- 休眠面条件化降档（须 grep 证据）：本轮完成两组休眠面取证——(1) feishu 链生产激活唯一路径 `ChannelConnectorManager.startAll` 全仓非测试零调用（WI 3.3「渠道栈结构性休眠」裁定的延伸，涉及 C3-015/016/017）；(2) nop-vertx-mqtt-server 全模块零装配（涉及 C3-018/021/022，及已并入的 C3-019/020）。两处均以**激活回升条款注记**形式登记留 7.1 对合并工单统一适用；鉴于 C6-024 已裁定升格 P1 与 C3-015 对齐、C1-008/C6-020 以 P1 三登记，本复核不单方面改变已定跨维判级，仅登记证据供 7.1 统一裁定（与 WI 3.3/3.2 条件化工单先例一致）。
- 已声明边界豁免核对：C3-030 的 sendMultiEmail 对照（javadoc 明示 fail-fast）成立，本类无声明故立案；C3-005 无"保留 .part"声明、C3-026 无"重连耗尽即静默"声明——豁免均不适用。

---

## 最终保留项（30 条，P1×8/P2×15/P3×7）

| # | 发现 | 最终严重度 | 修复归属/指针 |
|---|---|---|---|
| 1 | C3-001 OkHttp onResponse 吞异常 future 永不完成 | P1 | future 永不完成族同批 |
| 2 | C3-002 JDK 下载 body 阶段取消失效（幽灵下载） | P1 | 与 C3-003/CL-025 同批 |
| 3 | C3-003 JDK SSE 订阅取消后 readLine 泄漏线程 | P1 | 与 C3-002 同批 |
| 4 | C3-004 sidecar 探测同步 get 跑在回调线程 | P1(apache)/P2(jdk,okhttp) | 三实现同 diff（F3） |
| 5 | C3-005 无校验源 .part 不改名（半个成功） | P1 | 错误以成功交付族同批；与 C6-001(P0) 同站点同批 |
| 6 | C3-015 Feishu WS send 无超时单线程 scheduler 死锁 | P1 | 与 C6-024 同站点合并工单；激活回升条款留 7.1 |
| 7 | C3-018 MqttSessionManager 迟到 close 清新连接订阅 | P1 | 三登记工单（C1-008/C6-020）；激活回升条款留 7.1 |
| 8 | C3-024 SftpClient 全链路无超时 | P1 | 与 C6-018（同文件构造期泄漏）同批 |
| 9 | C3-006 ApiContextInterceptor 同步抛豁口 | P2 | C6-009 已并入；修复含 ContextBinder 幂等连带核对 |
| 10 | C3-007 binder.close 跨线程 ThreadLocal 串号 | P2 | 与 CL-011（C2 平台根因）协同 |
| 11 | C3-008 oauth token 锁内 HTTP + 三入口绕过 token | P2 | C6-008 已并入；C1-002 同文件同批 |
| 12 | C3-009 SimpleRpcClient 派生 future 错位 | P2 | 对齐 MessageRpcClient onFailure |
| 13 | C3-010 RpcTaskMonitor 跨轮竞态 | P2 | C1-004 已并入（主登记） |
| 14 | C3-011 callAsync 同步抛逃逸族 | P2 | round2 扩展站点同 diff |
| 15 | C3-012 cancelToken 监听器只增不删 | P2 | 10 站点统一 bindCancelToken；WI 2.1 api-core 移交包协同点 |
| 16 | C3-016 FeishuClient stop×在途 connect 僵尸连接 | P2 | 与 C6-021 合并单工单（7.1 裁判级） |
| 17 | C3-017 attemptReconnect 无单飞/代际守卫 | P2 | 与 C1-009 合并（7.1 裁判级与主登记） |
| 18 | C3-021 MQTT 下行同步抛 + PUBACK 注释失实 | P2 | 与 C3-011 同批；激活回升条款留 7.1 |
| 19 | C3-022 MQTT 跨线程 publish packetId 碰撞 | P2 | 事件循环归位修复；激活回升条款留 7.1 |
| 20 | C3-023 PacketCodecHandler 吞编解码异常 | P2 | fail write promise |
| 21 | C3-025 TencentEmailSender 吞发送异常 | P2 | 对齐家族重抛范式 |
| 22 | C3-026 SocketClient 重连链静默死亡 | P2 | 终态处理器 + 消息级错误分流 |
| 23 | C3-033 SSE 终态无隔离（迟到事件/终态 park/假 onError） | P2 | 基类单点修复（C3-round2.md 主登记） |
| 24 | C3-013 服务端 filter MDC 异步断链 | P3 | 记录性 |
| 25 | C3-014 OkHttp 忽略 per-request timeout | P3 | 三实现语义对齐 |
| 26 | C3-027 checkTimeout 共享异常实例 | P3 | 每 future 独立异常 |
| 27 | C3-028 sendOneway 失败连接静默写丢弃 | P3 | isSuccess 分支 + FIRE_EXCEPTION_ON_FAILURE（含 isDone 分支） |
| 28 | C3-029 sendToChannel handler 缺失 NPE | P3 | 对齐客户端 ERR_NETTY_NO_RPC_HANDLER |
| 29 | C3-030 sendMultiMessage fail-fast 未声明 | P3 | 逐条结果或补 javadoc |
| 30 | C3-032 makeBucket 并发首建 409 | P3 | S3 幂等惯用法 |

> 已驳回 3 条（C3-019→C6-023、C3-020→C1-012、C3-031→C1-015）随各主登记工单修复，本维度不再单独立项。跨维判级分歧 2 处（C3-016↔C6-021、C3-017↔C1-009）与休眠面激活回升条款 2 组（feishu 链、vertx-mqtt 面）移交 7.1 终裁。
