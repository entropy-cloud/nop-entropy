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

## IHttpClient 文件下载

`downloadAsync(request, targetFile, options, cancelToken)`：

- **断点续传**（`options.isResume()` 默认 true）：下载写入目标旁的 `{name}.part`；中断后再次调用自动带 `Range: bytes=N-` 续传（206 追加）；服务端不支持 Range（回 200）自动整体重传；`416` 自动删除 `.part` 重下。完成并校验通过后原子 rename 到目标路径。`.part` 布局跨实现一致，可换实现续传。
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

## 相关文档

- `../02-core-guides/rpc-and-distributed-rpc.md`（RPC 分层调用模型，`/px/` 代理）
- `./nop-credential.md`（出站 OAuth 客户端引擎使用 `IHttpClient`）
