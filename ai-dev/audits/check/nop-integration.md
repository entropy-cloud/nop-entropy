# nop-integration 实现代码检查报告

- 检查日期: 2026-08-21
- 模块路径: nop-integration
- 文件数: 67（src/main/java，排除 `_` 前缀与 `_gen/` 生成物、target/；与任务给定的约 75 一致，差额为 package-info 等非实质文件口径差异）
- 覆盖范围声明: 10 个子模块（api / email-java / email-tencent / feishu / file-local / oss / sftp / sms-tencent / sms-yunpian / zxing）的**全部 67 个主代码 Java 文件均已读或按职责skim**；其中 feishu（client/codec/bind）、sftp、oss、file-local、email-java、email-tencent、sms-tencent、sms-yunpian、zxing 的实现类逐行深读；api 子模块的接口/DTO 全部过目。同时核对了 feishu/oss/zxing 的 beans.xml 装配。为验证发现真实性，额外查证了：Tencent SDK 异常类字节码（errorCode 可为 null）、已编译 class 的 `setReplyToAddresses(String)` 签名、仓库内调用方（nop-auth 的 `LoginServiceImpl.sendSms` / `NopAuthUserBizModel.sendEmailForBinding`）与 `IResourceStore.getResource(path, boolean)` 参考实现（SimpleResourceStore / InMemoryResourceStore）。测试代码、`docs/`、生成文件不在范围。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 2 |
| P1 | 4 |
| P2 | 7 |
| P3 | 5 |

---

## 发现列表

### [P0] TencentSmsSender 对未设置 areaCode 的短信必现 NPE（平台自身发码链路触发即崩溃）

- **文件**: `nop-integration/nop-integration-sms-tencent/src/main/java/io/nop/integration/sms/TencentSmsSender.java:147-150`
- **维度**: D1
- **证据**:
```java
private void sendMessage(SmsSingleSender sender, String sign, SmsMessage message) {
    String areaCode = message.getAreaCode();
    if (areaCode.startsWith("+"))
        areaCode = areaCode.substring(1);
```
- **现状**: `SmsMessage.areaCode`（`nop-integration-api/.../sms/SmsMessage.java:20`）无默认值。`sendMessage` 在 try 块之外直接解引用 `areaCode`，未设置时抛裸 NPE（无 NopException 包装、无 mobile 参数）。
- **风险**: 平台主链路调用方 `nop-auth/nop-auth-service/.../login/LoginServiceImpl.java:960-971` 的 `sendSms` 只设置 `mobile/templateCode/params`，**从不设置 areaCode**（国内短信常态）。部署 TencentSmsSender 时，每次短信验证码发送（登录/MFA 绑定）都在入口 NPE 崩溃，短信功能完全不可用，且调用方拿到的是无法定位的裸 NPE。
- **建议**: `SmsMessage.getAreaCode()` 返回 null 时回退到默认区号（如 "86"），或在 `sendMessage` 入口显式校验并抛 `NopException(ERR_SEND_SMS_FAIL).param(ARG_MOBILE, ...)`；同步给 `SmsMessage.areaCode` 设默认值。
- **误报排除**: 已核实调用方主代码（LoginServiceImpl）确实不设 areaCode；非模板分支（`sender.send(...)`）不使用 areaCode 却同样在解引用处崩溃，崩溃不依赖模板配置。

### [P0] JavaEmailSender 吞掉全部发送/连接异常，sendEmail 永不报错（MFA 邮件发码链误报成功）

- **文件**: `nop-integration/nop-integration-email-java/src/main/java/io/nop/integration/email/java/JavaEmailSender.java:149-168`
- **维度**: D4
- **证据**:
```java
private void withTransport(Consumer<Transport> task) {
    ResolvedCredential credential = resolveCredential();
    Transport transport = null;
    try {
        transport = connectTransport(credential);
        task.accept(transport);
    } catch (Exception e) {
        LOG.error("nop.err.send-mail-fail", e);   // <-- 全部吞掉，不再抛出
    } finally { ... }
```
- **现状**: `doSend` 内部把异常包装为 `NopException` 抛出（184-186 行），但 `withTransport` 对整个发送过程 catch-all 后仅记日志。`sendEmail` / `sendMultiEmail` 对调用方呈现"永远成功"。
- **风险**: (1) SMTP 故障/认证失败/网络断开时调用方无从感知。关联调用方 `nop-auth/.../NopAuthUserBizModel.java:1590-1601` 调 `emailSender.sendEmail(msg)` 后在 `bindEmail`（约 388-392 行）无条件 `result.setEmailSent(true)` 并持久化 pending 绑定——邮件实际未发出，用户收不到验证码，MFA 绑定流程数据失真。这正是 150-151 行注释自己声明的红线（"否则 MFA 发码链会误信已发码"），凭证解析修复了，发送失败仍未修复。(2) `sendMultiEmail` 中第一封失败即中断循环，**剩余邮件静默放弃**，调用方完全无感知。
- **建议**: catch 块改为记录日志后重抛（`NopException.adapt`）；`sendMultiEmail` 需要定义部分失败语义（逐封收集失败再汇总抛出，或返回逐封结果）。
- **误报排除**: 已核实 `IEmailSender` 契约（api 模块）无"失败不抛错"约定；auth 调用方按"不抛即成功"编码；本模块自身其他 sender（sms 系）失败均抛 NopException，本实现属行为偏离。

### [P1] Feishu Stream 客户端收到 DATA 帧后从不回发 ACK，事件确认语义缺失

- **文件**: `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/client/FeishuClient.java:330-344`
- **维度**: D1
- **证据**:
```java
public void onBinary(byte[] data) {
    try {
        FeishuStreamFrame frame = FeishuPbCodec.decode(data);
        if (frame.getMethod() == FeishuFrameType.DATA.getMethod()) {
            FeishuInboundMessage msg = toInbound(frame);
            handler.onMessage(msg);          // 处理后无任何回发
        } else if (frame.getMethod() == FeishuFrameType.ACK.getMethod()) {
            LOG.debug("feishu-client received ACK frame");
```
- **现状**: 全模块 grep 证据：主代码中 `ACK(2)` 帧类型只出现在枚举定义与接收分支的 debug 日志，**不存在任何构造/发送 ACK 帧的代码路径**（客户端只发过 method=0 的 connect/heartbeat CONTROL 帧）。`toInbound`（362-373 行）还丢弃了 DATA 帧的 headers（log_id/message_id 等），下游也无从自行回执。
- **风险**: Feishu Stream（长连接）协议要求客户端对每条收到的事件回执确认；缺失 ACK 时服务端会视为投递失败，导致事件重复投递或网关断连——生产接入后消息将被重复消费（依赖消费端幂等）或连接反复重建。这是消息确认语义（D1 核心风险区）的整块缺失。
- **建议**: `onBinary` 处理 DATA 帧后按协议回发确认帧（携带原帧 headers 中的 log_id 等）；将 `frame.getHeaders()` 透传给 `FeishuInboundMessage` 以便错误处理路径也能回执。
- **误报排除**: 已 grep 全模块确认无 ACK 发送路径；`FeishuFrameType` javadoc 自述 wire 兼容性"verified at W6 E2E"（尚未验证），不影响"确认语义代码路径不存在"这一事实。

### [P1] SftpClient 硬编码禁用 SSH 主机密钥校验（StrictHostKeyChecking=no）

- **文件**: `nop-integration/nop-integration-sftp/src/main/java/io/nop/integration/sftp/SftpClient.java:137-141`
- **维度**: D5
- **证据**:
```java
//disable known hosts checking
// jsch.setKnownHosts("path to known hosts file");
Properties props = new Properties();
props.put("StrictHostKeyChecking", "no");
session.setConfig(props);
```
- **现状**: 无条件关闭主机密钥校验，且 `SftpConfig` 没有 knownHosts 或 strictHostKey 开关，部署方无法启用校验。
- **风险**: SSH 中间人攻击可劫持文件传输连接，截获用户名/密码认证凭证（`session.setPassword`）或私钥交互数据，并可篡改/窃取传输文件。SFTP 常用于跨网络边界传输，属现实安全暴露面。
- **建议**: `SftpConfig` 增加 `knownHostsFile` 与 `strictHostKeyChecking` 配置（默认安全值，如 `accept-new` 或显式 known_hosts 路径），兼容期可提供配置项但不应无出路地硬编码 no。
- **误报排除**: 已确认无其他配置途径覆盖该 Properties（`SftpConfig` 全文读过，无相关字段）。

### [P1] TencentEmailSender 吞掉发送异常；errorCode 为 null 的异常会导致错误处理路径 NPE

- **文件**: `nop-integration/nop-integration-email-tencent/src/main/java/io/nop/integration/email/tencent/TencentEmailSender.java:187-196`
- **维度**: D4
- **证据**:
```java
} catch (TencentCloudSDKException e) {
    String ignoreInfo = "EmailAddressIsNULL";
    if (e.getErrorCode().contains(ignoreInfo)) {      // getErrorCode() 可为 null
        return;
    } else if ("FailedOperation.FrequencyLimit".equals(e.getErrorCode())) {
        LOG.warn("nop.send-email-exceed-limit", e);
        return;
    }
    LOG.error("nop.err.send-email-fail", e);          // 其余失败同样不抛出
}
```
- **现状**: (1) 除两种白名单外，所有 SDK 失败（含认证错误、发件域未配置等）只记 LOG.error，调用方无感知——与上条 P0 同型（同为 `IEmailSender`，auth 的 `sendEmailForBinding` 同样会误信已发送）。(2) 网络类异常由 SDK 以单参构造器抛出（已用 javap 核实 `TencentCloudSDKException(String)` 存在且 errorCode 保持 null，`AbstractClient` 字节码确认网络错误走单参路径），此时 `e.getErrorCode().contains(...)` 抛 NPE，从 catch 块内逃逸为裸 NPE。
- **风险**: 邮件发送失败静默；超时/断网时错误处理本身崩溃为 NPE，行为不可预期（时而静默、时而裸 NPE）。
- **建议**: catch 内先判 `e.getErrorCode() != null`；白名单外的异常应包装为 `NopException` 抛出（与 sms 系实现一致）。
- **误报排除**: 已核实 SDK jar 字节码（tencentcloud-sdk-java-common 3.1.213）：单参构造器存在、errorCode 字段默认 null、AbstractClient 网络失败分支使用该构造器。

### [P1] YunpianSmsSender 将 null areaCode 字符串拼接进手机号（"null138..."）

- **文件**: `nop-integration/nop-integration-sms-yunpian/src/main/java/io/nop/integration/sms/yunpian/YunpianSmsSender.java:140-142`
- **维度**: D1
- **证据**:
```java
Map<String, String> param = client.newParam(2);
param.put(YunpianClient.MOBILE, message.getAreaCode() + message.getMobile());
param.put(YunpianClient.TEXT, message.getText());
```
- **现状**: Java 字符串拼接中 null 引用渲染为字面量 `"null"`。`areaCode` 未设置（与 P0 第 1 条同一调用方 `LoginServiceImpl.sendSms`）时，提交给云片的号码变为 `"null+手机号"`。
- **风险**: 每次未设区号的发送都会以损坏的号码调用云片 API，返回不可理解的错误（掩盖真实原因：缺区号默认值），排障成本高；与 TencentSmsSender 的 NPE 属同一根因（`SmsMessage.areaCode` 无默认值）的两种劣化表现。
- **建议**: 与 P0 第 1 条同修：`SmsMessage` 提供默认区号或解析侧统一 null 兜底。
- **误报排除**: 已确认调用方不设 areaCode；拼接语义（null→"null"）为 Java 语言确定行为。

### [P2] SftpClient 建连半途失败时 SSH Session 泄漏

- **文件**: `nop-integration/nop-integration-sftp/src/main/java/io/nop/integration/sftp/SftpClient.java:131-117`
- **维度**: D2
- **证据**:
```java
protected void openConnection(ResolvedCredential credential) throws Exception {
    ...
    session = jsch.getSession(credential.username, config.getHost(), config.getPort());
    ...
    session.connect();
    channel = (ChannelSftp) session.openChannel("sftp");
    channel.connect();               // <-- 此处失败则 session 已连接但无人关闭
}
// connect():
try {
    openConnection(credential);
} catch (Exception e) {
    throw new NopException(ERR_SFTP_CONNECT_FAIL, e)...   // 无清理
}
```
- **现状**: 构造器 → `connect()` → `openConnection()`。若 `session.connect()` 成功而 `openChannel`/`channel.connect()` 失败（如服务端 sftp 子系统异常），异常被包装抛出、构造失败、客户端对象不可达，已建立的 TCP+SSH 连接无人 disconnect。
- **风险**: 服务端 sftp 通道异常时按操作频率泄漏 SSH 连接（`SftpClientFactory.newClient` 每操作新建客户端），可耗尽服务端/本机连接与句柄。
- **建议**: `connect()` 的 catch 中对已建立的 session 执行 `session.disconnect()` 再抛出。
- **误报排除**: 已核对 `close()` 只会被拿到客户端对象的调用方触发；构造抛错路径调用方拿不到对象，无法补偿关闭。

### [P2] OssFileServiceClient.getBucketName 对无斜杠的 bucket 前缀路径抛 StringIndexOutOfBoundsException，且前缀从不剥离对象键

- **文件**: `nop-integration/nop-integration-oss/src/main/java/io/nop/integration/oss/OssFileServiceClient.java:74-80`
- **维度**: D1
- **证据**:
```java
protected String getBucketName(String remotePath) {
    if (remotePath.startsWith(BUCKET_PREFIX)) {
        int pos = remotePath.indexOf('/', 1);
        return remotePath.substring(BUCKET_PREFIX.length(), pos);  // pos=-1 时 substring(4,-1) 崩溃
    }
    return ossConfig.getDefaultBucketName();
}
```
- **现状**: 路径形如 `"bkt_mybucket"`（无后续 `/key`）时 `indexOf('/',1)` 返回 -1，`substring(4,-1)` 抛越界异常。另外 bucket 段从不从 key 中剥离：`listFiles("bkt_a/f")` 实际以 key=`"bkt_a/f"` 调用 `listObjects("a","bkt_a/f")`——对象键恒带 `"bkt_<bucket>/"` 前缀（内部自洽但与 `BUCKET_PREFIX` 的寻址语义相悖，跨客户端/控制台访问同桶对象时路径不一致）。
- **风险**: 通过 `FileServiceResourceStore`（`getResource`/`getChildren` 透传路径）访问 bucket 根（不带斜杠）即崩溃且异常信息不可读；前缀语义漂移造成数据布局混乱。
- **建议**: 无 `/` 时回退默认桶或抛带 remotePath 参数的 `NopException`；统一"前缀只用于选桶、不进入对象键"的语义并在 `getBucketName` 后裁掉前缀段。
- **误报排除**: 已核对所有 5 个调用点（listFiles/deleteFile/getFileStatus/uploadFile/downloadFile/uploadResource/downloadToStream/getInputStream）均先走 `getBucketName` 再 `normalizePath`（仅去头斜杠），无其他剥离逻辑。

### [P2] FileServiceResourceStore 不遵守 IResourceStore.getResource(path, true) 的 returnNullIfNotExists 契约

- **文件**: `nop-integration/nop-integration-file-local/src/main/java/io/nop/integration/file/local/FileServiceResourceStore.java:38-45`
- **维度**: D8
- **证据**:
```java
public IResource getResource(String path, boolean returnNullIfNotExists) {
    IResource resource = newResource(path, null);
    if (returnNullIfNotExists && !resource.exists()) {   // exists() 对远端不存在路径会抛异常
        return null;
    }
    return resource;
}
```
- **现状**: `FileServiceResource.exists()` → `getFileStatus()` → `client.getFileStatus(remotePath)`；SFTP 实现对不存在路径抛 `NopException(ERR_SFTP_LIST_FILE_FAIL)`（SftpClient.java:193-201，lstat 失败包装抛出），OSS 实现抛 `AmazonS3Exception`（getObjectMetadata 404）。`exists()`（FileServiceResource.java:110-112）依赖"返回空 status"的兜底，但两个客户端实现都改为抛异常，兜底不可达。
- **风险**: 调用方以 `getResource(path, true)` 探测资源存在性时（参考实现 `SimpleResourceStore`/`InMemoryResourceStore` 均按契约返回 null），远端 store 抛异常中断流程。`oss-defaults.beans.xml` 将本 store 注册为 `nopOssResourceStore`，属可装配的公共组件。
- **建议**: `exists()` 捕获"不存在"类异常（SftpException ssh NoSuch file / S3 404）返回 false；或客户端提供 `getFileStatusOrNull` 语义。
- **误报排除**: 已读参考实现确认契约语义（返回 null 而非抛错）；已确认 SFTP/OSS 客户端无"不存在→null"分支。

### [P2] FeishuClient stop() 与传输回调/业务线程的生命周期竞态（credentials/handler 置 null）

- **文件**: `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/client/FeishuClient.java:199-223, 311-344, 245-255`
- **维度**: D3
- **证据**:
```java
public synchronized void stop() {
    ...
    this.credentials = null;   // 非 volatile 字段
    this.handler = null;       // 回调线程可能正在使用
    ...
}
// onOpen(): payload 用到 credentials.getAppId() / endpoint.getTicket()  → NPE(被 catch)
// onBinary(): catch(Throwable t) 内 handler.onError(t) → handler 已为 null 时二次 NPE 逃逸到 WebSocket 线程
// ensureToken(): credentials.getAppId() → sendMessage 调用方直接收到裸 NPE
```
- **现状**: `credentials/handler/endpoint` 为普通字段，仅 `started/connected/cachedToken` volatile。`stop()` 与 WebSocket 回调线程（onOpen/onBinary）及业务线程（sendMessage→ensureToken）之间无同步，置 null 后并发读产生 NPE。
- **风险**: 停机窗口内 `sendMessage` 向业务方抛裸 NPE；`onBinary` 的 catch 块内二次 NPE 逃逸进 JDK WebSocket 监听线程（触发其 onError→transport listener 已 null，日志噪声）。单次影响有限但属确定性数据竞争。
- **建议**: 字段加 volatile 并在回调/ensureToken 内做 null 快照与降级（如 sendMessage 里复查 `credentials == null` 抛 NopFeishuException("client not started")）。
- **误报排除**: 已核对字段声明（77-79 行无 volatile）与三个读取点；确认 `sendMessage` 入口只检查 `started` 不复查 `credentials`。

### [P2] FeishuBindProvider 不支持 credentialId 解析，与 FeishuClient 的凭证库语义不一致

- **文件**: `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/bind/FeishuBindProvider.java:152-158`
- **维度**: D8
- **证据**:
```java
private String requireAppId() {
    if (credentials == null || credentials.getAppId() == null || credentials.getAppId().isEmpty()) {
        throw new NopFeishuException("FeishuBindProvider: FeishuCredentials.appId is not configured");
    }
    return credentials.getAppId();
}
```
- **现状**: beans.xml（feishu-defaults.beans.xml:32-35）将 `nopFeishuCredentials`（含 `credentialId` 配置键）直接注入 BindProvider；同模块 `FeishuClient.resolveEffectiveCredentials`（125-139 行）实现了 credentialId → 凭证库整组解析，BindProvider 只读静态 `appId`。
- **风险**: 部署按 W16 方式仅配置 `nop.integration.feishu.credentialId`（静态四键留空）时，消息通道正常启动，而扫码绑定流程在 `createBindTicket` 即抛 "appId is not configured"——同一凭证配置在两处行为分叉。
- **建议**: BindProvider 复用 `CredentialResolutionSupport` 与 `resolveEffectiveCredentials` 同款解析（构造/首用时解析一次）。
- **误报排除**: 已通读 BindProvider 全文确认无 credentialId 分支；已核对 beans.xml 注入路径。

### [P2] 入站消息解析假设扁平 JSON，与 Feishu 事件实际结构不符（核心字段将为 null）

- **文件**: `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/client/FeishuClient.java:362-373`（配合 `FeishuJsons.java:22-28`）
- **维度**: D1
- **证据**:
```java
private static FeishuInboundMessage toInbound(FeishuStreamFrame frame) {
    String json = ...;
    m.setReceiveIdType(FeishuJsons.extractString(json, "receive_id_type"));
    m.setReceiveId(FeishuJsons.extractString(json, "receive_id"));
    m.setMsgType(FeishuJsons.extractString(json, "msg_type"));
    ...
}
// FeishuJsons.extractString: 全文 indexOf("\"key\"") 子串匹配，不区分层级
```
- **现状**: 解析器对整个 payload JSON 做全文子串搜索取"第一个匹配键"。Feishu v2 事件 schema 为嵌套结构（header/event 两层，消息字段在 `event.message.*`，键名为 `message_type` 而非 `msg_type`），顶层并不存在 `receive_id_type/receive_id/msg_type` 等键。
- **风险**: 真实事件到达时 `receiveId/msgType` 等核心字段解析为 null（无法回复消息），`content` 可能命中嵌套键但其余字段缺失；同时全文匹配不区分层级，消息文本中恰好包含未转义的相同键名时存在错配可能。`rawPayload` 兜底存在但便捷字段名存实亡。
- **建议**: 用真实事件结构（v2 嵌套 schema）定位字段路径；或在未取到关键字段时显式抛错而非填充 null（fail-fast）。
- **误报排除**: 基于模块自述"W6 E2E 未做真实联调"与解析器代码事实（扁平假设、全文搜索）；不影响"扁平假设与嵌套结构冲突"的代码层结论。

### [P2] MailConfig.port 无默认值，transport.connect 自动拆箱 NPE（叠加异常吞没后变为静默不发）

- **文件**: `nop-integration/nop-integration-email-java/src/main/java/io/nop/integration/email/java/JavaEmailSender.java:214-215`（配置：`MailConfig.java:35`）
- **维度**: D1
- **证据**:
```java
Transport transport = getSession().getTransport(config.getProtocol());
transport.connect(config.getHost(), config.getPort(), username, password);
// MailConfig: private Integer port;  // 无默认值
```
- **现状**: `connect(String, int, String, String)` 的 int 形参触发 `Integer` 自动拆箱；未配置 port（SMTP 常态是让 JavaMail 用协议默认端口）时抛 NPE。
- **风险**: 未显式配置 port 的部署全部邮件发送在连接前 NPE；再叠加 P0 的 catch-all 吞没，表现为"邮件静默不发"，极难排查。
- **建议**: `MailConfig.port` 默认 -1（JavaMail 语义：使用协议默认端口），或拆箱前判空回退。
- **误报排除**: 已核对 `MailConfig` 全文无默认值；JavaMail `Transport.connect(host, port, user, pwd)` 为 int 形参（javax.mail 标准签名）。

### [P3] SftpClient.getInputStream 复用"删除"错误码与日志文案

- **文件**: `nop-integration/nop-integration-sftp/src/main/java/io/nop/integration/sftp/SftpClient.java:280-288`
- **维度**: D4
- **证据**:
```java
public InputStream getInputStream(String remotePath) {
    LOG.info("nop.sftp.delete:remotePath={}", remotePath);   // 读取操作打出 delete 日志
    try {
        return channel.get(remotePath);
    } catch (Exception e) {
        throw new NopException(ERR_SFTP_DELETE_FILE_FAIL, e)  // 读取失败报"删除失败"
```
- **现状**: 复制 `deleteFile` 时未改错误码（`ERR_SFTP_DELETE_FILE_FAIL`，而 `SftpErrors` 已定义 `ERR_SFTP_DOWNLOAD_FAIL` 未被使用）与日志前缀。
- **风险**: 读文件失败时错误信息误导排障（提示删除失败）；info 日志文案与动作不符。
- **建议**: 改用 `ERR_SFTP_DOWNLOAD_FAIL`，日志前缀改为 `nop.sftp.read`。
- **误报排除**: 直接代码事实。

### [P3] SftpErrors 三个错误码消息模板使用 `{}` 而非命名占位符，参数不渲染

- **文件**: `nop-integration/nop-integration-sftp/src/main/java/io/nop/integration/sftp/SftpErrors.java:27-37`
- **维度**: D4
- **证据**:
```java
ErrorCode ERR_SFTP_UPLOAD_FILE_FAIL =
        define("nop.err.sftp.upload-file-fail", "上传文件失败:localPath={localPath},remotePath={}",
                ARG_LOCAL_PATH, ARG_REMOTE_PATH);
```
- **现状**: `ERR_SFTP_UPLOAD_FILE_FAIL` / `ERR_SFTP_DOWNLOAD_FILE_FAIL` / `ERR_SFTP_DOWNLOAD_FAIL` 的消息模板把 remotePath 写成 `{}`，与本文件其他码及 `IntegrationErrors` 的 `{paramName}` 命名占位风格不一致。
- **风险**: 错误消息中 remotePath 原样输出 `{}`（命名参数机制无法填充），丢失关键定位信息。
- **建议**: 统一为 `{remotePath}`。
- **误报排除**: 同文件 `ERR_SFTP_CONNECT_FAIL`/`ERR_SFTP_LIST_FILE_FAIL` 均用命名占位，对比明显。

### [P3] ZxingQrcodeService 输入校验失败抛裸 IllegalArgumentException

- **文件**: `nop-integration/nop-integration-zxing/src/main/java/io/nop/integration/qrcode/ZxingQrcodeService.java:29, 42-43, 60`
- **维度**: D4
- **证据**:
```java
hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.forBits(options.getErrorCorrection()));
...
} catch (WriterException | IOException e) {     // 不含 IllegalArgumentException
    throw NopException.adapt(e);
...
throw new IllegalArgumentException("nop.qrcode.unsupported-barcode-format:" + name);
```
- **现状**: `errorCorrection` 非 {0,1,2,3} 时 `forBits` 抛 IAE、宽高非法时 zxing `encode` 抛 IAE，均不在捕获范围内；`getFormat` 也直接抛裸 `IllegalArgumentException`。
- **风险**: 调用方收到未包装的运行时异常，违背平台两档错误处理约定（公共入口应以 NopException 呈现）。
- **建议**: 入口显式校验并抛 `NopException`，或 catch 范围加入 IllegalArgumentException 后 adapt。
- **误报排除**: `QrcodeOptions` 的 errorCorrection/width 均为外部可设原始值，非法输入现实可达。

### [P3] FeishuClient.sendMessage 契约声明与实现不符；JdkFeishuHttpApi.sendMessage 恒返回 200

- **文件**: `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/client/FeishuClient.java:229-243`、`client/JdkFeishuHttpApi.java:64-79`
- **维度**: D8
- **证据**:
```java
/**
 * @throws NopFeishuException if not started/connected, or the API call fails
 */
public void sendMessage(...) {
    if (!started) { ... }        // 只检查 started，未检查 connected
    ...
// JdkFeishuHttpApi.sendMessage: 一律 return 200（HTTP 实际状态可能是任意 2xx）
```
- **现状**: javadoc 声称未连接会抛错，实现不检查 `connected`（未连接时靠底层 send 失败间接暴露）；HTTP API 实现无论实际状态码一律返回常量 200，返回值无信息量。
- **风险**: 契约漂移：调用方据 javadoc 假设的失败前置检查不存在；接口返回值失去意义。
- **建议**: 实现补 `connected` 检查或修正 javadoc；`sendMessage` 返回真实状态码或改为 void。
- **误报排除**: 直接代码事实。

### [P3] SFTP 路径每次资源操作都新建完整 SSH 连接（无复用）

- **文件**: `nop-integration/nop-integration-sftp/src/main/java/io/nop/integration/sftp/SftpClientFactory.java:70-73`（配合 `file-local/FileServiceResourceStore.java:48-58`）
- **维度**: D6
- **证据**:
```java
@Override
public SftpClient newClient() {
    return new SftpClient(config, credentialProvider);   // 构造即 connect()
}
// FileServiceResourceStore.getChildren / saveResource / FileServiceResource 各操作:
//   client = factory.newClient(); try { ... } finally { IoHelper.safeCloseObject(client); }
```
- **现状**: `IFileServiceClientFactory` 语义为按操作建连；SFTP 每次操作都要完整 TCP+SSH 握手+认证（listFiles 中每项还各自 getFileStatus 补一次 lstat）。
- **风险**: 高频小文件/目录遍历场景延迟与服务器负载显著放大（对比 OSS 共享 AmazonS3 客户端的做法）。
- **建议**: 属设计取舍，可考虑 SftpClient 内部 channel 断线重建 + 短时限连接复用，或在 store 层做按线程/按批次复用。
- **误报排除**: 已核对 store 与 resource 的每次操作均 new + close，无复用路径。

---

## 检查方法与边界说明

1. 先 `ls` 子模块并 grep 可疑模式（空 catch、`new RuntimeException`、`printStackTrace`、凭证字段、连接创建/关闭、并发原语）——本模块无空 catch、无 printStackTrace、无 bare `new RuntimeException`（D4 问题集中在"吞日志不抛"与裸 IAE/NPE 两条变体）。
2. 深读顺序：feishu 连接管理/认证/收发主链路 → sftp → oss/file-local → email×2 → sms×2 → zxing → api 契约层；每个候选问题均 Read 上下文并交叉验证（调用方、SDK 字节码、参考实现）后才写入。
3. D7（Nop 平台规范）专查结论：无 private 字段注入（全部 setter 注入且 beans.xml 显式定义）；配置值注入使用 `@InjectValue`；错误处理总体符合两档策略（例外见 P3 zxing 条）；未发现手改 `_` 前缀生成文件迹象。
4. 已核实并排除的疑点：`TencentEmailSender.setReplyToAddresses(String)` 与 SDK 签名匹配（javap 字节码证据）；`FeishuPbCodec` 手写 protobuf wire 格式逐行核对无截断/负长度缺陷；`CredentialResolutionSupport` fail-closed 逻辑本身未见缺陷。
5. 未覆盖：测试代码、`target/`、运行时行为（未启动集成环境），feishu 真实联调行为以代码静态事实为准。
