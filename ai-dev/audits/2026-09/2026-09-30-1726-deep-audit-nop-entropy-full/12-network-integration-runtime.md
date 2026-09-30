# G12: network/integration/runner/spring/quarkus/autotest 深度审计（首轮）

- **审计日期**: 2026-09-30
- **审计子代理**: G12（网络与运行时外围模块组）
- **执行维度**: 09 错误处理与错误码 / 13 安全与权限模型 / 14 异步与事务模式 / 16 测试覆盖与质量
- **审计基线**: live code（HEAD），纯静态审计，未运行 mvn/test

## 审计范围

| 模块组 | 子模块 | 手写 Java 文件数 | 深读重点 |
|--------|--------|----------------|---------|
| nop-network/ | nop-codec, nop-http（api/apache/jdk/okhttp/oauth）, nop-netty, nop-rpc（api/model/core/http/simple）, nop-socket, nop-vertx | 261 | HTTP 客户端 TLS/代理/凭证、SSE 订阅背压、RPC 响应契约、SSRF 主机规范化 |
| nop-integration/ | api, feishu, sftp, oss, email-java/email-tencent, sms-tencent/sms-yunpian, file-local, zxing | 77 | 飞书 token/secret 处理与日志泄漏、SFTP 主机密钥校验、凭证解析 fail-closed |
| nop-runner/ | nop-cli, nop-cli-core, nop-cli-jdk11, nop-tool | 50 | CLI 命令注入面、参数校验异常形态 |
| nop-spring/ | 全部 8 个子模块 | 30 | spring-proxy 代理日志、文件服务 |
| nop-quarkus/ | 全部 7 个子模块 | 25 | 文件上传、WebSocket JSON-RPC 端点 |
| nop-autotest/ | core, dbtool, junit | 46 | 快照机制健壮性、资源清理、自身测试覆盖 |

**维度 13 标准检查项执行情况**：
- **SSRF 主机解析规范化**：本组内未发现任何带主机白名单/内网黑名单的 URL 校验逻辑（nop-http 客户端不做主机过滤，目标 URL 全部来自配置或上层调用方；`JdkFeishuHttpApi.baseUrl` / `FeishuBindProvider.feishuOpenHost` 为配置注入的服务端常量）。故该项在本组无命中面，不存在"extractHost 旁路"类问题。
- **TLS 校验是否可被关闭**：发现 [G12-13-01]（OkHttp 路径默认 trust-all）；Apache/JDK 路径的 `CompositeX509TrustManager.setIgnoreSSLCert` 已带显著 WARN（F-N1-3 修复，有回归测试 `TestIgnoreSslCertsWarnSite`），按"不重复报告已收敛的问题"不列入发现。
- **代理凭证处理**：`HttpClientConfig` 仅有 httpProxy/httpsProxy/noProxy 字符串配置，无凭证字段的日志输出路径；未发现泄漏。
- **白名单双向断言**：本组无名单类校验逻辑，无命中。
- **nop-runner 命令注入面**：全组 grep `Runtime.getRuntime`/`ProcessBuilder`/`exec(` 零命中——CLI 不派生子进程，XPL/工具执行是 CLI 的设计目的（本地信任域），未构成注入发现。

**机械基线核实**（区分 main/test）：
- main 代码 `throw new RuntimeException`：6 组模块 **0 处**。裸 `IllegalArgumentException`/`IllegalStateException` 共 23 处，逐个判级：CLI 参数校验（CliFileCommand/CliSplitCommand/CliGenFileCommand 等，本地信任域、英文明晰消息）与 `ChunkIterator` eof 哨兵属可接受形态；违规形态收敛为 [G12-09-01]。
- `System.out/printStackTrace`：nop-runner main 28 处均为 CLI stdout/stderr 用户输出（含 `CliValidateCommand` 中 `--verbose` 门控的 `printStackTrace`），按任务口径属合理形态，不算发现；nop-network main 12 处全部位于 `src/test`（核实为测试代码），不计。
- `@Inject private`：全组 0 处（与主 agent 基线一致）。

**误报排除（检视后不列为发现的高频误报模式）**：
- `JdkHttpClient.toHttpResponse` 将 503 转为 `NopConnectException`（`JdkHttpClient.java:321-323`）：代码注释明确为特定 MAC/JDK 版本连接失败返回 503 的规避措施，属有意为之。
- `AutoTestVars` 全局静态变量与 surefire parallel=classes 的兼容性：`docs-for-ai/02-core-guides/testing.md` 已明文记载"容器测试模块需 reuseForks=false 或关闭 parallel"，属已文档化的已知约束。
- `HttpApiErrors` 中文描述：nop-http 属存量中文模块，符合 error-handling.md 的存量延续规则。
- `FeishuPbCodec`：手写 protobuf 解码带完整长度上界、wire-type 校验与截断拒绝，加固良好，非发现。
- `CredentialResolutionSupport` + 各 sender 的 fail-closed 凭证解析：设计一致、实现干净，非发现。
- `FeishuClient.ensureToken()` 无锁并发双取 token：良性竞态（两次均为有效 token），不值得报告。

---

## 发现

### [G12-13-01] OkHttp 客户端 useSsl=true 时默认安装完全放行的 TrustManager/HostnameVerifier，且无任何告警

- **文件**: `nop-network/nop-http/nop-http-client-okhttp/src/main/java/io/nop/http/client/okhttp/OkHttpClientProvider.java:150-168`
- **证据片段**:
  ```java
  protected void addSSLConfig(OkHttpClient.Builder builder) {
      if (config.isUseSsl()) {
          try {
              X509TrustManager trustManager = this.trustManager != null ? this.trustManager
                      : new DisableValidationTrustManager(); //NOSONAR
              ...
              HostnameVerifier verifier = this.hostnameVerifier != null ? this.hostnameVerifier
                      : new TrustAllHostnames(); //NOSONAR
              builder.hostnameVerifier(verifier);
          } catch (Exception e) {
              LOG.warn("nop.err.okhttp.setSocketFactory-fail", e);
          }
      }
  }
  ```
- **严重程度**: P1
- **现状**: `useSsl` 配置位在本组三种 HTTP 客户端实现中语义相反——`JdkHttpClient.start()`（`JdkHttpClient.java:107-109`）中 `useSsl` 表示"安装自定义 SSLContext"（内含 `CompositeX509TrustManager`，默认仍做完整证书+主机名校验，`ignoreSslCerts` 另行控制且开启时输出显著 WARN）；而 `OkHttpClientProvider` 中 `useSsl=true` 且未注入显式 trustManager/hostnameVerifier 时，**默认值**就是 `DisableValidationTrustManager`（空实现）+ `TrustAllHostnames`（恒 true），即证书链与主机名校验全部关闭。该路径运行时零 WARN，仅靠 `@SuppressWarnings("java:S5527")` 静态压制。
- **风险**: 应用从默认 Jdk 客户端切换到 OkHttp 客户端（`nop-http-client-okhttp` 被 nop-auth-sso、nop-cli-core 等模块依赖）而保留 `nop.http.client.use-ssl=true` 配置时，所有出站 HTTPS 的 TLS 校验静默失效，MITM 可截获 Authorization 头与业务数据。这与 F-N1-3 已修复的"ignore-ssl-certs 逃生门必须带 WARN"属同类缺陷，但 OkHttp 路径漏改且形态更严重（是默认值而非显式开关）。
- **建议**: 对齐 F-N1-3 基线：(1) `DisableValidationTrustManager`/`TrustAllHostnames` 被实际安装时输出与 `CompositeX509TrustManager` 同等强度的 WARN；(2) 长期看将 `useSsl` 语义统一为"安装自定义 SSL 配置"，放行行为改由独立 `ignoreSslCerts` 开关控制。
- **信心水平**: 确定
- **误报排除**: 不是"测试工具类可接受放行"——`OkHttpClientProvider` 是通用生产客户端工厂（无任何 @TestOnly 标记），且 `useSsl` 在兄弟实现中是"启用自定义 SSL"的正常语义；也不是已收敛问题的重复报告——F-N1-3 只修了 `CompositeX509TrustManager`，此文件未在修复范围内。
- **复核状态**: 未复核

### [G12-13-02] SftpClient 硬编码关闭 SSH 主机密钥校验（StrictHostKeyChecking=no），无配置项、无告警

- **文件**: `nop-integration/nop-integration-sftp/src/main/java/io/nop/integration/sftp/SftpClient.java:131-150`
- **证据片段**:
  ```java
  protected void openConnection(ResolvedCredential credential) throws Exception {
      String keyPath = config.getKeyPath();
      if (keyPath != null) {
          jsch.addIdentity(keyPath, credential.passphrase);
      }
      session = jsch.getSession(credential.username, config.getHost(), config.getPort());
      //disable known hosts checking
      // jsch.setKnownHosts("path to known hosts file");
      Properties props = new Properties();
      props.put("StrictHostKeyChecking", "no");
      session.setConfig(props);
  ```
- **严重程度**: P2
- **现状**: 框架级 SFTP 客户端对所有连接无条件禁用主机密钥校验（TOFU 都没有），且 `SftpConfig` 未提供 known-hosts 配置面，注释表明是有意禁用。
- **风险**: 任何位于网络路径上的攻击者可对 SFTP 连接做 MITM——截获/篡改传输的文件内容，或钓鱼获取 password/passphrase 形态的凭证。凭证轮换等治理机制（W16 系列）防不了传输层被劫持。
- **建议**: 在 `SftpConfig` 增加 `strictHostKeyChecking`（默认 true）与 `knownHostsPath` 配置；默认路径校验失败时 fail-closed，显式关闭时输出与 ignore-ssl-certs 同级的 WARN。
- **信心水平**: 确定
- **误报排除**: 不是"内网工具可容忍"——`IFileServiceClient` 是跨模块公共文件抽象（nop-file 体系的标准 SPI），目标主机由部署配置任意指定；也不是 JSch 库的默认行为（JSch 默认 ask/yes-no），此处是显式覆盖。
- **复核状态**: 未复核

### [G12-13-03] FeishuBindProvider 扫码回调接受未经验证的客户端自声明 open_id，扫码身份绑定缺服务端验证

- **文件**: `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/bind/FeishuBindProvider.java:113-154`
- **证据片段**:
  ```java
  public ChannelBindResult onChannelScanCallback(ChannelScanCallback callback) {
      ...
      String openId = stringField(payload, "open_id");
      if (openId == null || openId.isEmpty()) {
          throw new NopFeishuException(
                  "FeishuBindProvider.onChannelScanCallback: rawPayload missing required field 'open_id'");
      }
      ...
      // consume the ticket (single-use binding flow)
      tickets.remove(ticketId);

      ChannelBindResult result = new ChannelBindResult();
      result.setExtId(openId);
      result.setPlatformUserId(entry.platformUserId);
  ```
- **严重程度**: P2
- **现状**: 绑定结果 `extId` 直接取自回调 payload 中的 `open_id` 字符串，未向飞书侧做任何验证（未走 OAuth `code` 换取 open_id，也未校验飞书回调签名/加密 payload——类 Javadoc 自述 Option A 应"resolves the Feishu open_id from a scan callback"，并提到可选的"the OAuth code for the provider to exchange"，实现选择了直接信任 payload）。绑定唯一的防线是 ticket 的不可猜测性（UUID，5 分钟 TTL，单次消费）。
- **风险**: 凡能看到二维码（肩窥/截图/日志记录 state）或截获回调请求的攻击者，可在 TTL 窗口内以任意 open_id（包括自己的飞书号）抢先消费该 ticket，把自己的飞书身份绑定到受害者平台账号——后续扫码登录（`ChannelLoginScanProcessor` → MFA/登录链）即以受害者身份通过。上游回调端点的鉴权情况在 nop-ai-gateway（范围外），本类作为绑定信任判定点自身不设防。
- **建议**: 改为校验飞书回调签名/encryptKey 解密后取 open_id，或实现 Javadoc 中提及的 OAuth code→open_id 服务端交换；至少在 `ChannelScanCallback` 契约中强制携带可验证的凭据字段。
- **信心水平**: 很可能（绑定判定逻辑确定缺验证；实际可利用性取决于范围外回调端点的鉴权强度）
- **误报排除**: 不是"ticket 已防重放/防猜测所以足够"——ticket 防的是陌生攻击者，防不了"看到二维码的人"，而扫码绑定的威胁模型恰恰包含该场景；ticket 的 UUID 加固注释（P0 hardening D5）本身说明该链路被认为是身份门禁，门禁的另一半（身份来源真实性）缺失。
- **复核状态**: 未复核

### [G12-14-01] ApacheHttpClient 下载收尾在 I/O reactor 回调线程内阻塞等待 sidecar 校验文件下载（.get 最长 60 秒）

- **文件**: `nop-network/nop-http/nop-http-client-apache/src/main/java/io/nop/http/apache/ApacheHttpClient.java:330-338, 370-383`
- **证据片段**:
  ```java
  // handleDownloadResult: 在 FutureCallback.completed 内被调用（HC5 I/O 分派线程）
  if (options.isFetchSidecarChecksum() && !hasHeaderOrExplicitChecksum(options, result)) {
      sidecarText = fetchSidecarChecksum(request.getUrl(), cancelToken);
  }
  ...
  private String fetchSidecarChecksum(String url, ICancelToken cancelToken) {
      for (String algorithm : new String[]{FileTransferHelper.SHA256, FileTransferHelper.SHA1}) {
          try {
              IHttpResponse res = fetchAsync(HttpRequest.get(FileTransferHelper.sidecarUrl(url, algorithm)), cancelToken)
                      .toCompletableFuture().get(30, TimeUnit.SECONDS);
  ```
- **严重程度**: P2
- **现状**: `handleDownloadResult` 由异步客户端的完成回调调用（HC5 async 的回调在 I/O reactor 分派线程上执行），其中 `fetchSidecarChecksum` 对同一 client 发起新请求并以 `get(30s)` 同步阻塞等待，SHA256/SHA1 两次探测最长阻塞 60 秒。对照：`JdkHttpClient` 的同款逻辑运行在 `executor()` 工作线程（`JdkHttpClient.java:410-418`），不占 I/O 线程。
- **风险**: `ioThreadCount` 较小（尤其显式配 1）时，一次启用 sidecar 探测的下载会冻结整个共享客户端的 I/O 线程：同 reactor 上所有其他在途请求停止收发、超时检测停摆；sidecar 请求本身也依赖该 I/O 线程推进，必然等满 30s 超时，形成每个下载 60s 的确定性停顿。
- **建议**: 将收尾逻辑（sidecar 探测、校验、改名）移出回调线程——用 `Promise.complete` 后转投工作 executor 执行，或把 sidecar 探测改为异步链（`fetchAsync(...).thenCompose(...)`）后统一 complete。
- **信心水平**: 很可能（阻塞调用与回调线程归属可从 HC5 结构确定；具体停顿幅度取决于 ioThreadCount 配置）
- **误报排除**: 不是"options 未启用就不会触发"——`fetchSidecarChecksum` 是 `DownloadOptions` 的公开能力（`isFetchSidecarChecksum()`），启用即命中；也不是 Jdk/OkHttp 同类实现的共性问题（两者分别在 worker/dispatcher 线程执行，仅 Apache 在 reactor 线程）。
- **复核状态**: 未复核

### [G12-14-02] SSE 订阅背压以阻塞 HTTP 客户端投递线程的方式实现，慢消费者可冻结共享客户端

- **文件**: `nop-network/nop-http/nop-http-api/src/main/java/io/nop/http/api/client/AbstractServerEventSubscription.java:107-122, 143-153`
- **证据片段**:
  ```java
  private void dispatchEvent() {
      if (data != null) {
          if (!isCancelled) {
              waitForDemand();
              subscriber.onNext(newServerEvent(id, event, data));
          }
          ...
  protected synchronized void waitForDemand() {
      while (demand <= 0 && !isCancelled) {
          try {
              wait();
          } catch (InterruptedException e) { ... }
      }
      demand--;
  }
  ```
- **严重程度**: P2
- **现状**: `processLine → dispatchEvent → waitForDemand` 在 HTTP 客户端的事件投递线程上同步 `wait()`。Apache 路径（`ApacheHttpClient.ServerEventPublisher`）中 `LineAsyncDataConsumer.onLine` 运行在 HC5 I/O reactor 分派线程——订阅者未及时 `request(n)` 时，该 reactor 线程被冻结。
- **风险**: 一个慢订阅者（如 UI 停滞的 LLM 会话）即可冻结共享 `CloseableHttpAsyncClient` 的一个 I/O 线程：同线程上多路复用的其他 SSE/普通请求全部停摆，且 reactor 自身的连接超时检测同样失效；多个慢订阅者可逐步耗尽全部 I/O 线程，表现为"整个 HTTP 客户端假死"且无异常日志。
- **建议**: 改为有界缓冲 + 投递线程不阻塞：demand 不足时暂停向 consumer 请求更多数据（HC5 支持 `HttpDataConsumer` 的 flow 控制）或将事件先入队、由独立投递线程按 demand 派发。
- **信心水平**: 确定（阻塞位置与线程归属可静态确定）
- **误报排除**: 不是 Flow 规范要求的背压形态——`Flow.Publisher` 契约要求生产侧尊重 demand 但不得阻塞其他订阅者共享的事件循环线程；本组其他 Publisher 实现未采用"阻塞 reactor"方式。
- **复核状态**: 未复核

### [G12-14-03] JdkStreamTransport.send 无超时阻塞等待，可永久挂死飞书心跳调度线程导致客户端静默失联

- **文件**: `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/client/JdkStreamTransport.java:97-111`
- **证据片段**:
  ```java
  @Override
  public void send(byte[] frameBytes) {
      ...
      try {
          ws.sendBinary(ByteBuffer.wrap(frameBytes), true).get();
      } catch (Exception e) {
          throw new NopFeishuException("JdkStreamTransport.send failed", e);
      }
  }
  ```
- **严重程度**: P2
- **现状**: `sendBinary(...).get()` 无超时参数。`FeishuClient.sendHeartbeat`（`FeishuClient.java:268-281`）经 owned 单线程 `ScheduledExecutorService` 周期调用该 send；`FeishuClient.onOpen` 握手（`FeishuClient.java:312-328`）同样经此路径。
- **风险**: TCP 连接停滞（对端不读、半开连接）时 `sendBinary` 的 CompletionStage 可能长期不完成，心跳线程被永久挂死：后续心跳任务全部积压不再执行，连接因无心跳被服务端断开，而 `onClose → scheduleReconnect` 依赖的也是这个已被挂死的单线程调度器——整个客户端静默失联，仅心跳内部 catch 到的 trace 级日志（`FeishuClient.java:279` 用 LOG.debug）可循。
- **建议**: `sendBinary(...).get(sendTimeout, TimeUnit.SECONDS)`（如 10s），超时后主动 `transport.close()` 触发既有重连链路。
- **信心水平**: 很可能（无超时阻塞确定存在；"永久"取决于 JDK WebSocket 实现对停滞写的行为）
- **误报排除**: 不是"WebSocket 库自身有写超时"——`java.net.http.WebSocket.sendBinary` 返回的 CompletionStage 规范上不承诺超时完成，必须调用方设界；对照 `connect` 路径已设 15s connectTimeout，send 路径是遗漏。
- **复核状态**: 未复核

### [G12-14-04] HttpRpcService 非 200 响应体可解析为 ApiResponse 时不强制失败状态，下游错误被静默吞为成功

- **文件**: `nop-network/nop-rpc/nop-rpc-http/src/main/java/io/nop/rpc/http/HttpRpcService.java:81-122`
- **证据片段**:
  ```java
  protected ApiResponse<?> toApiResponse(IHttpResponse response, ApiRequest<?> request) {
      int status = response.getHttpStatus();
      ...
      } else {
          ret = (ApiResponse<?>) JSON.parseToBean(null, text, ApiResponse.class, true, false);
      }
      ...
      if (ret == null) {
          ret = ApiResponse.success(null);
      }
      if(status != 200)
          ret.setHttpStatus(status);
      return ret;
  }
  ```
- **严重程度**: P2
- **现状**: 非 2xx 响应只要 body 能反序列化为 `ApiResponse`（`status` 字段缺省即 0=成功），返回值 `isOk()` 即为 true；`httpStatus` 虽被设置（`ApiResponse.java:33,147`），但主判定入口 `isOk()` 只看 `status==0`。对比同方法内 GraphQL 分支显式做了 `ret.setStatus(-1)` 兜底（`HttpRpcService.java:98-101`），普通 ApiResponse 分支无此兜底。
- **风险**: 网关/非 Nop 下游返回 500/502/503 且携带任意可解析 JSON（如 `{"message":"upstream error"}`，未知字段被忽略、status 缺省 0）时，RPC 调用方按成功路径继续执行，`data=null` 引发下游 NPE 或业务数据静默缺失；故障被推迟到与根因无关的位置。
- **建议**: 解析成功且 `status != 2xx` 时，若 `ret.getStatus()==0` 则强制置失败（如 `ret.setStatus(-status)` 并带 code/msg），与 GraphQL 分支的兜底对齐。
- **信心水平**: 很可能
- **误报排除**: 不是"Nop 服务端总返回 200 所以无影响"——该客户端同样用于网关、第三方兼容端点与错误页返回 JSON 的场景（代码自身对"响应不是 JSON"和 GraphQL errors 都做了防御，唯独漏掉这条中间形态）；也不是解析失败路径（该路径已正确转为失败）。
- **复核状态**: 未复核

### [G12-16-01] JunitAutoTestCase 的 snapshotTest=NOT_USE 配置（文档推荐用法）必然在 setup 期 NPE

- **文件**: `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/AutoTestCase.java:133-135, 159-168`（配合 `nop-autotest/nop-autotest-junit/src/main/java/io/nop/autotest/junit/JunitAutoTestCase.java:101-108, 34-46`）
- **证据片段**:
  ```java
  // AutoTestCase.initCaseDataDir: useSnapshot=false 时 caseData 保持 null
  public void initCaseDataDir(File caseDataDir) {
      if(!useSnapshot)
          return;
      caseData = new AutoTestCaseData(caseDataDir, valueResolverRegistry);
  ...
  // AutoTestCase.initDao: 无条件解引用 caseData
  public void initDao() {
      daoProvider = initDaoProvider();
      jdbcTemplate = initJdbcTemplate();
      sessionFactory = initSessionFactory();

      Map<String, Object> vars = caseData.getInitVars();   // <-- NPE
  ```
- **严重程度**: P2
- **现状**: `configExecutionMode` 对 `SnapshotTest.NOT_USE` 设置 `setUseSnapshot(false)`（`JunitAutoTestCase.java:102-108`），`init()` 随后无条件调用 `initDao()`（`JunitAutoTestCase.java:44`），后者在 `AutoTestCase.java:164` 解引用尚未初始化的 `caseData`，必然 NPE。
- **风险**: `docs-for-ai/02-core-guides/testing.md` 第 130 行明确推荐"全局禁用快照 → `@NopTestConfig(snapshotTest = SnapshotTest.NOT_USE)`"；任何按文档使用该配置的 `JunitAutoTestCase` 子类全部测试在 setup 期崩溃，开发者只能靠猜测绕过（改用 JunitBaseTestCase 或全局 system property），损害测试基建可信度。
- **建议**: `initDao()` 中对 `useSnapshot==false || caseData==null` 跳过 init-vars 段（`AutoTestVars.clear()` + 直接注册收集器）；或 `initCaseDataDir` 在 NOT_USE 时仍构造空 `AutoTestCaseData`。
- **信心水平**: 确定（调用序与空值传播均为静态可判定事实）
- **误报排除**: 不是"NOT_USE 本就不该配 JunitAutoTestCase"——该枚举值由 `JunitAutoTestCase.configExecutionMode` 显式处理，且为 owner 文档记载的正式用法；`CFG_AUTOTEST_DISABLE_SNAPSHOT=true` 路径不受影响（disable 标志不置 useSnapshot=false），证明此分支缺乏实际测试覆盖（另见 [G12-16-02]）。
- **复核状态**: 未复核

### [G12-16-02] nop-autotest 作为平台测试基建本体，核心机制（变量/@var、快照保存/校验、diff、junit 集成）几乎零自测

- **文件**: `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/`（main 代码 24 个类）；测试盘点：core 仅 `util/TestTestClockAnchoredSim.java` 1 个测试，dbtool 4 个测试，`nop-autotest-junit` **0 个测试**
- **证据片段**:
  ```text
  nop-autotest-core/src/test/    → TestTestClockAnchoredSim（仅覆盖 TestClock）
  nop-autotest-dbtool/src/test/  → TestDataBaseUpgradeInitializer / TestOrmDbDiffer /
                                    TestOrmModelDiffer / TestJdbcMetaDiscovery（仅 dbtool）
  nop-autotest-junit/src/test/   → （目录不存在）
  无任何测试覆盖的 main 类：AutoTestCase、AutoTestCaseData、AutoTestVars、
  TagVarCollector、AutoTestMatchChecker、CsvDataDiffer、JsonDataDiffer、
  AutoTestCaseDataSaver、AutoTestCaseDataBaseInitializer、AutoTestCaseResultChecker、
  TestCaseJsonDataSplitter、migration/operations/*（Rename/Delete/Transform）
  ```
- **严重程度**: P2
- **现状**: 平台快照测试的全部核心机制——`AutoTestVars.VarsMap.addVar` 的同名后缀演算（`_1`/`_2` 递推含 `last.length() < 4 && isAllDigit` 分支）、`TagVarCollector.addRefVars` 外键变量传递、CSV/JSON diff、`AutoTestMatchChecker`、录制/校验生命周期——没有任何直接单元测试；`JunitAutoTestCase`/`NopJunitExtension`/`VariantsArgumentProvider` 亦零测试。核心逻辑仅被下游业务模块的快照测试间接执行（成功路径），失败路径与边界从未被独立验证。[G12-16-01] 的 NOT_USE NPE 正是这种覆盖缺口的直接后果。
- **风险**: 快照机制是全平台测试的地基；对其行为的任何回归（如变量替换规则、diff 归一化）不会在本模块被捕获，而是以"其他模块快照批量爆红"或更糟的"静默漏检"形式出现，排查成本被放大到每个消费模块。`addVar` 后缀演算、`JsonDataDiffer` 归一化这类纯函数极易测而未测。
- **建议**: 优先为纯函数补测：`AutoTestVars.addVar` 后缀演算（含 `id_0001` 类边界）、`TagVarCollector`、`CsvDataDiffer`/`JsonDataDiffer`、`AutoTestMatchChecker`；为 `configExecutionMode` 的四分支（CHECKING/RECORDING/NOT_USE/@EnableSnapshot）补 JUnit 级状态断言。
- **信心水平**: 确定（盘点可复现：`find nop-autotest -path "*src/test/*" -name "*.java"` 共 5 个文件）
- **误报排除**: 不适用"AutoTest 快照机制是标准测试模式，不需要对快照文件本身审计"的豁免——该豁免针对快照数据文件；此处审计的是机制实现代码自身的单元测试缺失，属维度 16 的正当对象。测试基建模块（附录 C"测试基础设施模块"优先维度含 16）不以"被下游使用"替代自测。
- **复核状态**: 未复核

### [G12-14-05] FeishuClient.stop() 关闭 owned 调度器后无法再次 start()，重启进入半启动故障态

- **文件**: `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/client/FeishuClient.java:200-224, 258-265`
- **证据片段**:
  ```java
  public synchronized void stop() {
      ...
      if (ownsScheduler) {
          scheduler.shutdownNow();          // 调度器仅此一次创建，stop 后不可复用
      }
  ...
  private void scheduleHeartbeat() {
      if (heartbeatIntervalSeconds <= 0) {
          return;
      }
      heartbeatFuture = scheduler.scheduleAtFixedRate(   // 关闭后的调度器 → RejectedExecutionException
  ```
- **严重程度**: P3
- **现状**: 生产构造器 `FeishuClient()` 创建 owned 单线程调度器；`stop()` 将其 `shutdownNow()` 且无重建路径。再次 `start()` 时 `started=true` 已先行置位（`FeishuClient.java:195`），随后 `scheduleHeartbeat()` 抛 `RejectedExecutionException`：连接已建立、started=true，但无心跳；后续断线时 `scheduleReconnect` 内 `scheduler.schedule` 同样被拒（仅 LOG.warn，`FeishuClient.java:290-294`），客户端永久失联。而 `start()` 的 Javadoc（`FeishuClient.java:120-124`）自述凭证"轮换可见性 = connector 重启"，表明 stop/start 生命周期是被预期的使用方式。
- **风险**: 按文档路径做凭证轮换（stop→start 复用同一 bean 实例）后，飞书渠道进入"看似已启动、实际无心跳无重连"的静默故障态。
- **建议**: `start()` 时若 `ownsScheduler && scheduler.isShutdown()` 重建调度器；或将 `started=true` 移到心跳调度成功之后，保证失败不留下半启动状态。
- **信心水平**: 很可能（缺陷路径确定；实际触发取决于 connector 侧是复用实例还是重建 bean）
- **误报排除**: 不是"stop 只在 @PreDestroy 调用所以无所谓"——`stop()` 是 public synchronized 方法且 Javadoc 明示重启为凭证轮换机制；若轮换必须重建 bean，应显式禁止 stop 后 start（start 检查 scheduler 状态并 fail-fast），当前行为是第三种最坏的"半启动"。
- **复核状态**: 未复核

### [G12-14-06] OkHttpClientImpl 与兄弟实现行为漂移：忽略 per-request 超时、不过滤 DISALLOWED_HEADERS、sidecar 探测异常零日志吞没

- **文件**: `nop-network/nop-http/nop-http-client-okhttp/src/main/java/io/nop/http/client/okhttp/OkHttpClientImpl.java:57-90, 101-114, 239-251`
- **证据片段**:
  ```java
  // fetchAsync/newCall：不读 request.getTimeout()（Jdk/Apache 均支持 per-request 超时）
  protected Call newCall(HttpRequest request) {
      final Request.Builder requestBuilder = new Request.Builder();
      requestBuilder.url(request.getUrlWithParams());
      ...
      if (request.getHeaders() != null) {
          for (Map.Entry<String, Object> entry : request.getHeaders().entrySet()) {
              requestBuilder.header(entry.getKey(), String.valueOf(entry.getValue()));  // 无 DISALLOWED_HEADERS 过滤
          }
      }
  ...
  } catch (Exception e) {
      // sidecar 探测失败不阻断下载       // 三实现中唯一没有 LOG 的
  }
  ```
- **严重程度**: P3
- **现状**: 同一 `IHttpClient` 契约下，OkHttp 实现与 Apache/JDK 实现存在三处漂移：(1) `request.getTimeout()` 完全被忽略（`OkHttpTimeoutInterceptor` 只处理协议头且只收紧 connectTimeout）；(2) 请求头不过 `HttpApiConstants.DISALLOWED_HEADERS` 过滤（Jdk/Apache 均过滤）；(3) `fetchSidecarChecksum` 的 catch 块无任何日志——error-handling.md 要求丢弃 throwable 前 LOG.info 及以上留证，Apache/Jdk 同位代码均有 `LOG.debug(..., e)`。
- **风险**: 使用 OkHttp 实现的部署中，调用方设置的单请求超时静默失效（挂死风险回到调用方）；被禁投递的头（如 hop-by-hop 头）可能被透传；sidecar 探测失败完全不可观测，`requireChecksum` 场景下只剩一个无上下文的 checksum-missing 错误。
- **建议**: 三点对齐：per-request 超时经 `chain.withReadTimeout/writeTimeout` 实现、复用 DISALLOWED_HEADERS 过滤、catch 块补 `LOG.debug("nop.http.download.sidecar-check-fail:url={}", url, e)`。
- **信心水平**: 确定
- **误报排除**: 不是"OkHttp 能力限制"——OkHttp 支持 per-call 超时（OkHttpClient.newBuilder() 或 interceptor 链），DISALLOWED 过滤是纯应用层逻辑；也不是纯风格问题——超时失效是行为缺陷，仅在风格维度容忍度内因伴随真实行为漂移而报告。
- **复核状态**: 未复核

### [G12-13-04] SpringWebProxy 以 INFO 级别完整记录上游响应体（含 LLM 会话内容）

- **文件**: `nop-spring/nop-spring-proxy/src/main/java/io/nop/spring/proxy/SpringWebProxy.java:99, 104-126`
- **证据片段**:
  ```java
  .bodyToMono(String.class) // 单响应体转换为Mono
  .doOnSuccess(text -> LOG.info("nop.rest.recv: {}", text)) // 成功时记录响应
  ...
  .bodyToFlux(String.class)
  .doOnEach(signal -> {
      String text = signal.get();
      LOG.info("nop.recv:{}", text);
  })
  ```
- **严重程度**: P3
- **现状**: 通用 REST/SSE 代理（从 `/rest/{provider}` 端点与 "Chat session completed" 日志看，主要用于 LLM API 代理）把上游完整响应体/每个 SSE chunk 以 INFO 无条件落日志，无开关、无脱敏。
- **风险**: 用户与 LLM 的完整会话内容、上游返回的凭证类字段（token 刷新响应若经 restProxy 透传）进入生产日志，构成合规/隐私暴露面；SSE 流式场景日志量亦显著放大。
- **建议**: 降为 DEBUG 并加开关；或仅记录长度/事件计数。对照 `ApacheHttpClient.logRequest` 已有的 `CFG_HTTP_LOG_PRINT_ALL_HEADERS` + secret 头掩码模式收敛。
- **信心水平**: 确定
- **误报排除**: 不是"调试便利可接受"——该日志无级别门控、无配置开关，INFO 在生产默认输出；也不是一次性请求而是代理主路径的每请求/每 chunk 必然执行。
- **复核状态**: 未复核

### [G12-13-05] DefaultClientIpFetcher 无条件信任 X-Forwarded-For/Forwarded 头，客户端 IP 可被任意伪造

- **文件**: `nop-network/nop-http/nop-http-api/src/main/java/io/nop/http/api/server/DefaultClientIpFetcher.java:8-24, 37-46`
- **证据片段**:
  ```java
  // 只信任这两个最标准的头，避免潜在的安全问题
  private static final String X_FORWARDED_FOR = "X-Forwarded-For";
  ...
  // X-Forwarded-For: client, proxy1, proxy2 → 取第一个 IP
  String[] ips = headerValue.split(",");
  for (String ip : ips) {
      String trimmedIp = ip.trim();
      if (isValidIp(trimmedIp)) {
          return trimmedIp;
      }
  }
  ```
- **严重程度**: P3
- **现状**: 取 XFF 第一项（即最客户端侧、完全由请求方可控的条目），无"信任代理跳数/可信代理网段"配置；`isValidIp` 仅排除 null/空/“unknown”。该 bean 以 `ioc:default="true"` 注册（`http-api.beans.xml:8`），并被 `JsonRpcWebSocketEndpoint` 用作 `HEADER_CLIENT_ADDR` 来源。
- **风险**: 未部署改写 XFF 的可信反代直接暴露时，任何客户端可伪造来源 IP——污染审计日志、限流/风控按错误 IP 维度失效。
- **建议**: 增加可信代理配置（网段/跳数），仅当直接对端 IP 落在可信网段时才采信 XFF（从右往左取），否则回退 `remoteAddr`；或在文档中强制声明该 bean 仅可在可信反代后启用。
- **信心水平**: 确定（伪造性确定；实际影响取决于部署形态）
- **误报排除**: 不是重复报告"已收敛问题"——注释自称"避免潜在的安全问题"但只实现了头选择而非信任判定，属安全意图与实现不匹配；典型高频误报是"拿 XFF 当事实"的文档示例，此处是框架默认 bean 的实际安全行为。
- **复核状态**: 未复核

### [G12-13-06] JdkFeishuHttpApi/FeishuClient 以字符串拼接手工构造 JSON 请求体，字段值未转义

- **文件**: `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/client/JdkFeishuHttpApi.java:40-50, 64-79`（配合 `FeishuClient.java:314-328`）
- **证据片段**:
  ```java
  public int sendMessage(String accessToken, String receiveIdType, String receiveId,
                         String msgType, String content) {
      String body = "{\"receive_id_type\":\"" + receiveIdType + "\","
              + "\"receive_id\":\"" + receiveId + "\","
              + "\"msg_type\":\"" + msgType + "\","
              + "\"content\":" + content + "}";
      String resp = postJsonWithAuth(baseUrl + "/open-apis/im/v1/messages?receive_id_type=" + receiveIdType,
  ```
- **严重程度**: P3
- **现状**: `receiveId`/`msgType`/`receiveIdType`/`appId`/`appSecret` 均未转义直接拼入 JSON（`content` 为有意传入的预序列化 JSON）。同模式亦见于 `getStreamEndpoint`/`getTenantAccessToken` 的凭证请求体（`JdkFeishuHttpApi.java:41,54`）与 `FeishuClient.onOpen` 握手 payload（`FeishuClient.java:320-321`）。`receiveIdType` 还被未编码地拼入 URL query。
- **风险**: 任一字段含 `"`/`\` 或换行即产生畸形请求体，飞书侧报难以定位的解析错误；若 `receiveId` 类字段未来接入用户可控输入（如渠道回复场景携带外部 id），则构成 JSON 结构注入。`FeishuJsons`（解析侧）与 `FeishuPbCodec`（编解码侧）都做了严谨处理，唯独请求序列化侧是裸拼接，属同一链路上的质量洼地。
- **建议**: 统一经 `JsonTool.stringify`/Map 序列化构造请求体（模块对 `io.nop.api.core.json.JSON` 的规避仅针对 `FeishuJsons` 的依赖隔离理由，HTTP API 层可正常使用平台 JSON 工具）；query 参数经 `ApiStringHelper.encodeURL`。
- **信心水平**: 很可能（未转义确定；当前入参多为服务端受控 id，实际注入需特定数据流）
- **误报排除**: 不是"FeishuJsons 已有先例所以可接受"——`FeishuJsons` 是刻意设计的只读解析器并在 Javadoc 声明局限；请求侧拼接没有任何结构性理由，且 `content` 参数的存在证明调用方已承担"预序列化"心智负担，其余字段本可一并安全序列化。
- **复核状态**: 未复核

### [G12-09-01] 以 IllegalArgumentException/IllegalStateException 承载 "nop.err.*" 错误码字符串，绕开 ErrorCode 体系

- **文件**: `nop-network/nop-netty/src/main/java/io/nop/netty/ssl/NettySslEngineFactory.java:38-52`；`nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/split/TestCaseJsonDataSplitter.java:123, 138, 179, 229`
- **证据片段**:
  ```java
  // NettySslEngineFactory.buildSslContext
  String sslKeyStoreType = sslConfig.getKeyStoreType();
  if (sslKeyStoreType == null) {
      throw new IllegalArgumentException("nop.err.ssl.key-store-type-not-set");
  }
  ...
  // TestCaseJsonDataSplitter
  throw new IllegalArgumentException("nop.err.autotest.invalid-dao:" + name);
  ```
- **严重程度**: P3
- **现状**: 两处共 7 个抛出点使用 `nop.err.ssl.*` / `nop.err.autotest.*` 形态的错误码字符串，但承载容器是 `IllegalArgumentException`/`IllegalStateException` 而非 `NopException` + `ErrorCode.define(...)`。这些 ID 不会进入错误码/i18n/结构化响应机制，日志里呈现为"裸消息带着像错误码的文本"，且 `nop.err.*` 命名空间被未注册的码占用（i18n 文件按 errorCode 字符串查 key 的机制查不到它们）。
- **风险**: 误导排障（看似有错误码实为无参数、无 i18n 的普通消息）；后续若有人为这些 ID 补 i18n 条目将永远不生效；`TestCaseJsonDataSplitter` 还把动态 name 拼在错误码冒号后，破坏 `nop.err.{模块}.{错误}` 的 ID 语义。
- **建议**: nop-netty 为 SSL 配置错误定义 `NopNettyErrors.ERR_SSL_KEY_STORE_TYPE_NOT_SET` 等码（配 `.param`）；nop-autotest 为 `invalid-dao`/`invalid-table` 定义 `AutoTestErrors` 码，`ARG_DAO`/`ARG_TABLE` 作参数。
- **信心水平**: 确定
- **误报排除**: 不是"模块内部可用英文字符串"的合规形态——error-handling.md 两档策略允许的是**不带错误码形态**的英文消息；这些字符串刻意使用了 `nop.err.*` 错误码命名空间，属于"想用 ErrorCode 但没用对容器"的中间态，且 `nop.err.ssl.*` 未在任何 `ErrorCode.define` 中注册（全仓 grep 核实）。
- **复核状态**: 未复核

### [G12-09-02] 短信发送链路以未掩码手机号写入日志与异常参数，违反 error-handling.md 掩码规则

- **文件**: `nop-integration/nop-integration-sms-yunpian/src/main/java/io/nop/integration/sms/yunpian/YunpianSmsSender.java:137-151`；`nop-integration/nop-integration-sms-tencent/src/main/java/io/nop/integration/sms/TencentSmsSender.java:170-182`
- **证据片段**:
  ```java
  // YunpianSmsSender
  LOG.info("nop.send-sms:areaCode={},mobile={}", message.getAreaCode(), message.getMobile());
  ...
  throw new NopException(ERR_SEND_SMS_FAIL, result.getThrowable())
          .param(ARG_ERROR_CODE, result.getCode())
          .param(ARG_MSG, result.getMsg())
          .param(ARG_MOBILE, message.getMobile());
  ```
- **严重程度**: P3
- **现状**: 两个 SMS sender 均将完整手机号写入 INFO 日志与 `NopException.param(ARG_MOBILE, ...)`。`docs-for-ai/02-core-guides/error-handling.md:64` 明确规定："敏感信息（手机号、身份证等）不要直接传入参数；如果业务必须包含，先掩码：`StringHelper.maskMiddle(mobile, 3, 4)`"。
- **风险**: PII（手机号）随日志与异常消息（`NopException.getMessage()` 展开 params）扩散到日志系统/APM/告警管道，属文档-代码直接冲突的合规缺口；平台已提供掩码工具却未使用。
- **建议**: 日志与 `.param(ARG_MOBILE, ...)` 统一经 `StringHelper.maskMiddle(mobile, 3, 4)`；若排障确需完整号码，以 debug 级别+开关承载。
- **信心水平**: 确定
- **误报排除**: 不是"上游代码已脱敏"——调用链（nop-auth LoginServiceImpl 等，文档注释自述）传入的是真实用户手机号；也不是 owner 文档过期——error-handling.md 现行有效且明确以手机号举例。
- **复核状态**: 未复核

### [G12-14-07] SftpClient.getInputStream 复制粘贴缺陷：读取操作记 delete 日志、失败抛 delete 错误码

- **文件**: `nop-integration/nop-integration-sftp/src/main/java/io/nop/integration/sftp/SftpClient.java:279-288`
- **证据片段**:
  ```java
  @Override
  public InputStream getInputStream(String remotePath) {
      LOG.info("nop.sftp.delete:remotePath={}", remotePath);
      try {
          return channel.get(remotePath);
      } catch (Exception e) {
          throw new NopException(ERR_SFTP_DELETE_FILE_FAIL)
                  .param(ARG_REMOTE_PATH, remotePath);
      }
  }
  ```
- **严重程度**: P3
- **现状**: `getInputStream` 是读取操作，但成功路径记 `nop.sftp.delete` 审计日志、失败路径抛 `ERR_SFTP_DELETE_FILE_FAIL`，且 `NopException(ERR_SFTP_DELETE_FILE_FAIL)` 丢失了 cause（相邻方法均带 `e`）。与同类 `downloadToStream`（用 `ERR_SFTP_DOWNLOAD_FILE_FAIL`）对照可确认是复制粘贴残留。
- **风险**: 运维按 `nop.sftp.delete` 日志审计删除操作时，所有读操作都会被计入，审计口径失真；读取失败被归因为"删除失败"，排障方向错误；丢 cause 使根因（权限/网络/路径）不可见。
- **建议**: 改为 `LOG.info("nop.sftp.get:remotePath={}", remotePath)`（或 debug）、`ERR_SFTP_DOWNLOAD_FILE_FAIL` 并保留 `e`。
- **信心水平**: 确定
- **误报排除**: 不是"日志事件名有意复用"——同文件 `deleteFile` 与 `getInputStream` 记录同一事件名但执行相反语义的操作，且错误码选择在同文件内有正确先例可对照。
- **复核状态**: 未复核

---

## 深挖覆盖度说明（零发现维度/区域）

- **维度 09（除上述 2 条 P3）**：6 组模块 main 代码零 `RuntimeException`；nop-integration 全族错误处理统一走 `IntegrationErrors`/`NopFeishuException`/`SftpErrors` 且 fail-closed 语义一致；nop-http 全部走 `HttpApiErrors` + `.param()`；异常链保留（catch-rethrow with cause）抽查（ApacheHttpClientHelper.wrapException、JdkFeishuHttpApi.postJson、JavaEmailSender.withTransport）均合规。
- **nop-runner 命令注入面（维度 13）**：无子进程派生、无 shell 拼接；XPL/tool 执行为 CLI 设计目的（本地信任域），无发现。
- **nop-spring/nop-quarkus（维度 14/09）**：`SpringFileService`/`QuarkusFileService`/`JsonRpcWebSocketEndpoint` 资源与异步边界整洁；`ContextHttpServerFilter` 的 context 生命周期（含同步抛出分支显式 close）实现正确。
- **nop-quarkus-grpc**：仅测试代码（GreeterService 等 demo），main 无手写代码，无可审对象。
- **维度 13 SSRF 主机解析规范化**：本组无白名单/黑名单类主机校验逻辑，标准检查项无命中面（详见审计范围节）。

## 发现统计

| 严重程度 | 数量 | 编号 |
|---------|------|------|
| P0 | 0 | — |
| P1 | 1 | G12-13-01 |
| P2 | 8 | G12-13-02, G12-13-03, G12-14-01, G12-14-02, G12-14-03, G12-14-04, G12-16-01, G12-16-02 |
| P3 | 8 | G12-13-04, G12-13-05, G12-13-06, G12-14-05, G12-14-06, G12-14-07, G12-09-01, G12-09-02 |

（合计 17 条；纯风格类 0 条）

## 子项复核结论

复核人：独立复核代理 R2（2026-09-30）

| 发现编号 | 判定 | 复核说明 |
|---|---|---|
| [G12-13-01] | 降级（P1 → P2） | 代码缺陷本身全部核实：`OkHttpClientProvider.java:150-168` 确认 useSsl=true 且未注入时默认安装 `DisableValidationTrustManager`（:188-201，checkClientTrusted/checkServerTrusted 空实现）+ `TrustAllHostnames`（:178-185，恒 true），路径内零 WARN；对照 `JdkHttpClient.java:107-109`（useSsl→newSSLContext，:130-134 处叠加 JDK 默认 TrustManager 即默认完整校验）与 `CompositeX509TrustManager.java:45-48`（ignoreSSLCert 启用时显著 WARN）确认语义相反属实。**但报告承重风险前提失实**："nop-http-client-okhttp 被 nop-auth-sso、nop-cli-core 等模块依赖" 为假——全仓 pom 检索仅 nop-bom（dependencyManagement）/nop-http 聚合 pom/tests pom 引用，两个被点名模块的 pom 均无 okhttp；全仓 Java/XML/properties 检索 `OkHttpClientProvider|OkHttpClientImpl` 零命中，且该模块没有任何 beans.xml——平台自身运行时（默认 `nopRawHttpClient`=JdkHttpClient，`http-client-jdk.beans.xml`）永不激活 OkHttp 路径。暴露需下游显式采用一个仓内完全未接线的模块 + 设置 use-ssl=true，属"已发布公共面真实缺陷但无仓内激活路径"，按判级基准降为 P2（修复建议维持：安装放行组件时对齐 F-N1-3 WARN 基线，长期统一 useSsl 语义）。 |
| [G12-13-02] | 保留（维持 P2） | 重开 `SftpClient.java:131-150`：`props.put("StrictHostKeyChecking", "no")` 硬编码 + 注释自证有意禁用属实；`SftpConfig.java` grep knownHosts/strictHost 零命中确认无配置面。框架级 SFTP SPI 对所有连接无条件跳过主机密钥校验，P2 恰当（未升 P1 因该集成模块为可选组件、MITM 前提是网络路径存在攻击者）。 |
| [G12-13-03] | 保留（维持 P2） | 重开 `FeishuBindProvider.java:113-154`：extId 直接取 `stringField(payload, "open_id")`，绑定判定仅有 ticket 存在性+TTL+单次消费校验，无签名验证/OAuth code 交换属实；类内对 null/空字段全部 fail-fast 唯独身份来源不设防的对照成立。绑定信任点缺服务端验证，P2 恰当（可利用性取决于范围外回调端点鉴权，与首轮"很可能"信心一致）。 |
| [G12-14-01] | 保留（维持 P2） | 重开 `ApacheHttpClient.java`：`FutureCallback.completed`（:266-270）内联调用 handleDownloadResult（:300），其中 `fetchSidecarChecksum`（:370-383）以 `.get(30, SECONDS)` 同步等待、SHA256/SHA1 两次探测最长 60s；客户端为 `CloseableHttpAsyncClient`（:34,68），HC5 回调默认运行于 I/O reactor 分派线程。阻塞位置与线程归属结构上成立，幅度取决于 ioThreadCount（与首轮"很可能"一致），P2 恰当。 |
| [G12-14-02] | 保留（维持 P2） | 重开 `AbstractServerEventSubscription.java:107-130`：`dispatchEvent → waitForDemand → wait()` 位于事件投递同步路径，demand 不足时投递线程冻结属实。Apache 路径 `LineAsyncDataConsumer.onLine` 于 reactor 线程执行的归属判断与 [G12-14-01] 同源成立。慢消费者冻结共享客户端，P2 恰当。 |
| [G12-14-03] | 保留（维持 P2） | 重开 `JdkStreamTransport.java:97-111`：`ws.sendBinary(...).get()` 确无超时；`FeishuClient.java:97-102` owned 单线程 scheduler、`:262-263` scheduleAtFixedRate(sendHeartbeat) 确认心跳/重连共用该线程，挂死即整机失联的传导链成立。P2 恰当。 |
| [G12-14-04] | 保留（维持 P2） | 重开 `HttpRpcService.java:81-122`：非 GraphQL 分支解析成功即返回，仅 `if(status != 200) ret.setHttpStatus(status)`；`ApiResponse.java:23-35`（nop-kernel/nop-api-core）确认 status 缺省 0=成功、isOk() 只看 status；同方法 GraphQL 分支确有 `ret.setStatus(-1)` 兜底且带说明注释——作者已知该缺陷类别但只修了一个分支。500+任意可解析 JSON 被当成功，P2 恰当。 |
| [G12-16-01] | 保留（维持 P2） | 静态重推完整 NPE 链：`JunitAutoTestCase.java:41-46` init() 依序 configExecutionMode → initCaseDataDir → initDao；`configExecutionMode`（:104-111）对 NOT_USE 置 setUseSnapshot(false)；`AutoTestCase.initCaseDataDir`（:133-135）`if(!useSnapshot) return` 使 caseData 保持 null；`initDao`（:162-164）无条件 `caseData.getInitVars()` → 必然 NPE。`docs-for-ai/02-core-guides/testing.md:130` 确以 NOT_USE 为文档推荐用法。注：该缺陷位于 P1/P2 边界——文档用法 100% 崩溃但失败响亮、仅涉测试代码、易绕过，维持首轮 P2。 |
| [G12-16-02] | 保留（维持 P2） | 独立盘点 `find nop-autotest -path "*/src/test/*"`：共 5 个文件（core 1 个 TestTestClockAnchoredSim、dbtool 4 个、junit 0 个），与报告一致；AutoTestCase/AutoTestCaseVars/differ/collector 等 24 个 main 类无直接单测属实；[G12-16-01] 的 NOT_USE NPE 即覆盖缺口的直接后果。测试基建本体零自测，P2 恰当。 |

**R2 统计**：复核 9 条（P1×1、P2×8）——保留 8、降级 1（[G12-13-01] P1→P2：代码缺陷属实，但"被 nop-auth-sso/nop-cli-core 依赖"的承重前提失实，模块仓内零接线零调用方，暴露路径为假设性下游采用）、驳回 0。

### 复核中发现的附带线索

- 核对 [G12-13-01] 时发现 nop-http-client-okhttp 整个模块处于"已发布（nop-bom 托管）但仓内零接线"状态：无 beans.xml、无任何调用方——与 G11 报告 [G11-03-02]（cluster 分片子系统休眠公开面）同型。若平台统一处理"休眠特性"，建议将 okhttp 客户端的装配决策（接线并对齐 F-N1-3 WARN 基线，或明确标记为仅限下游手动装配）一并纳入，不另立项。
