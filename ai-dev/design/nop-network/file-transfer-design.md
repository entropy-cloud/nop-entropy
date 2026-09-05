# HTTP 文件传输设计（上传双模式 / 断点续传下载 / 内容校验）

**日期**：2026-09-05
**范围**：`nop-http-api`（选项模型 + 协议公共逻辑）、`nop-http-client-apache`、`nop-http-client-jdk`、`nop-http-client-okhttp`（`uploadAsync`/`downloadAsync` 实现）
**状态**：active

---

## 一、设计结论

1. **上传两种模式由 `UploadOptions.mode` 显式选择**：`BINARY`（默认，自定义协议 + header 标记 + 二进制流）与 `BASE64_FORM`（base64 编码走标准 multipart 表单）。不存在 AUTO 自动协商——服务端能力无法从客户端探测，静默选错模式的失败比显式配置更难排查。
2. **BINARY 模式是自约定协议**：`PUT`（可配 `POST`）+ `application/octet-stream` 体 + `x-file-*` 元数据头。服务端按 header 还原文件名/长度，可选校验 `x-file-sha256`。
3. **下载续传遵循 RFC 9110 Range 语义**：本地 `.part` 临时文件即续传状态；`Range: bytes=N-` 请求，`206` 追加、`200` 整体重传；完成后**原子 rename** 到目标路径。
4. **内容校验按来源优先级解析**：调用方显式指定 > 响应头（`x-content-sha256`，兼容 `x-amz-checksum-sha256`）> 同目录 sidecar 校验文件（`{url}.sha256` / `.sha1`）。校验流式进行（写入时同步计算摘要），续传时先重读本地 `.part` 预热摘要。校验失败视为数据损坏：删除 `.part`、整个调用失败。
5. **协议公共逻辑收敛在 `nop-http-api`**（校验解析、Range 状态管理、sidecar 获取），三个客户端实现只负责把字节搬到线上——保证跨实现的**行为等价**（线上协议、错误码、`.part` 布局完全一致）。

## 二、背景与动机

`IHttpClient.uploadAsync`/`downloadAsync` 在三个实现中长期是抛 `UnsupportedOperationException` 的占位（2026-09 缺陷审查将其从"返回 null 致调用方 NPE"修正为显式抛出，见 `ai-dev/analysis/2026-09-05c-nop-network-deep-bug-review.md`）。业务侧需要：

- 与只接受文本表单字段的网关/遗留服务对接的上传（base64 表单）；
- 与自家服务端约定的高效直传（二进制流，元数据走 header，避免 multipart 编解码开销与 base64 膨胀）；
- 大文件下载的断点续传（网络中断后不重头下载）与完成时的完整性验证（防截断、防中间人篡改）。

## 三、核心设计

### 3.1 分层与职责

```mermaid
flowchart LR
    subgraph api["nop-http-api（协议层，纯 JDK 依赖）")
        OPT[UploadOptions / DownloadOptions]
        FT[FileTransferHelper<br/>校验解析 / .part 状态 / Range 决策]
    end
    subgraph impl["客户端实现层（IO 执行）"]
        A[ApacheHttpClient]
        J[JdkHttpClient]
        O[OkHttpClientImpl]
    end
    OPT --> FT
    FT -.被三个实现共同复用.-> A & J & O
```

- `FileTransferHelper` 不做任何网络 IO（sidecar 校验文件的下载由实现层发起，解析在 helper）。
- `nop-http-api` 保持只依赖 `nop-api-core`；摘要计算直接用 JDK `MessageDigest`，hex 用 JDK `HexFormat`，不引入新依赖。

### 3.2 上传协议

#### BINARY 模式（自约定协议）

请求形态（默认）：

```
PUT {url}
Content-Type: application/octet-stream
Content-Length: {fileLength}
x-file-name: {URL编码后的原始文件名}
x-file-length: {fileLength}
x-file-sha256: {hex}          ; computeSha256=true 时携带（默认 true）
x-file-mode: binary           ; 标记传输模式，服务端可据此分派

{文件原始字节流}
```

约定：
- 方法默认 `PUT`（幂等语义），可经 `UploadOptions.httpMethod` 改为 `POST`。
- 请求体为文件原始字节，不做任何编码；三个实现必须以**流式**方式发送（文件背书，不在内存整体缓冲）。
- `x-file-name` 值经 URL 编码，避免非 ASCII 文件名破坏 header。
- `x-file-sha256` 是全文件 SHA-256 hex（小写），服务端校验失败应返回 4xx/5xx。
- 进度：`UploadOptions.progressListener` 以已发送字节数/总长回调。

#### BASE64_FORM 模式（标准 multipart 表单）

请求形态：

```
POST {url}
Content-Type: multipart/form-data; boundary={boundary}

--{boundary}
Content-Disposition: form-data; name="filename"

{原始文件名}
--{boundary}
Content-Disposition: form-data; name="file"

{文件内容的 base64 文本}
--{boundary}--
```

约定：
- 字段名默认 `file`（内容）与 `filename`（文件名），经 `UploadOptions.fieldName`/`fileNameField` 可配。
- base64 内容是**普通文本表单字段**，不是文件 part——这正是该模式对接"只收文本字段"服务端的意义；服务端按文本读出后 base64 解码落盘。
- 该模式需要把 base64 文本整体构入请求体，内存占用约为文件 1.33 倍，仅适用于中小文件（文档明示，不设硬上限）。

### 3.3 下载断点续传协议

状态机（伪代码）：

```
target = outputFile.toFile()          ; 非文件输出（toFile()==null）跳过 .part 逻辑，直接流写
partFile = target + ".part"

下载开始:
  offset = resume 且 partFile 存在 ? partFile.length() : options.initialOffset
  若 offset > 0: 请求带 Range: bytes={offset}-
  否则: 普通请求

收到响应:
  status == 206:
    校验 Content-Range: bytes {start}-…/{total} 的 start == offset，不符则按损坏处理
    以追加模式打开 partFile，从 offset 续写
  status == 200:                        ; 服务端不支持 Range 或资源已变更
    截断 partFile，从 0 重写            ; 正确行为是整体重传，不是报错
  status == 416:                        ; offset 越界（本地 .part 比远端新）
    删除 partFile 后整体重传

写满(total 字节或流结束) 且 Content-Range/Content-Length 与已写字节数一致:
  校验摘要（见 3.4）→ 通过后 partFile 原子 rename 为 target
```

- `.part` 文件名固定为 `{目标文件名}.part`，与目标同目录——这是**跨实现一致的公开布局**：调用方可安全地在中断后用另一实现续传。
- 续传成功的判定基准是**字节数对账**（`Content-Range` 的 total / `Content-Length` + offset），最终一致性由 3.4 的摘要校验兜底。
- `DownloadOptions.initialOffset` 语义：无 `.part` 状态时的起始偏移（如调用方自持进度）。`.part` 存在时以 `.part` 为准。
- 进度：`progressListener` 以 `offset + 已接收字节数 / total` 回调。

### 3.4 内容校验协议

期望摘要的解析优先级（实现层按序探测，取第一个非空）：

```
1. options.expectedSha256 / expectedSha1   ; 调用方显式指定
2. 响应头 x-content-sha256 / x-amz-checksum-sha256   ; hex（后者兼容 base64）
3. sidecar：GET {url}.sha256（失败则 {url}.sha1）     ; 同目录校验文件
   内容格式：<hex> [ *|空格 ] <filename>?（兼容 Maven/Node 风格，取首个 token）
```

- 校验算法随来源：sha256 优先；仅有 `.sha1` sidecar 时降级 SHA-1（MD5 不支持——已不安全）。
- sidecar 请求失败（404/网络错）**不阻断**下载：无校验来源即不校验（尽力而为语义），但 `options.requireChecksum=true` 时转为失败。
- 摘要计算与写入同流（`DigestOutputStream` 包裹目标输出），单遍完成；续传场景先顺序重读本地 `.part` 预热 `MessageDigest` 再续写——不做摘要状态序列化（不可移植，见"拒绝了什么"）。
- 校验失败 → 删除 `.part`、整个 `downloadAsync` 以 `ERR_HTTP_DOWNLOAD_CHECKSUM_MISMATCH` 失败。损坏数据不能作为续传基底。

### 3.5 选项模型（使用契约）

`UploadOptions` 新增（全部有默认值，既有构造用法不受影响）：

| 字段 | 类型 | 默认 | 含义 |
|------|------|------|------|
| `mode` | `UploadMode` | `BINARY` | 上传模式 |
| `httpMethod` | String | `PUT`（BINARY 模式） | BINARY 模式的 HTTP 方法 |
| `computeSha256` | boolean | `true` | BINARY 模式是否计算并携带 `x-file-sha256` |
| `fieldName` | String | `file` | BASE64_FORM 内容字段名 |
| `fileNameField` | String | `filename` | BASE64_FORM 文件名字段名 |

`DownloadOptions` 新增：

| 字段 | 类型 | 默认 | 含义 |
|------|------|------|------|
| `resume` | boolean | `true` | 是否启用 `.part` 断点续传 |
| `expectedSha256` / `expectedSha1` | String | null | 显式期望摘要（hex） |
| `fetchSidecarChecksum` | boolean | `true` | 无显式/响应头来源时是否探测 sidecar |
| `requireChecksum` | boolean | `false` | 无任何校验来源时是否报错 |

### 3.6 错误码

| 错误码 | 场景 |
|--------|------|
| `nop.err.http.download-checksum-mismatch` | 摘要校验失败（附 expected/actual） |
| `nop.err.http.download-no-checksum` | `requireChecksum=true` 且无任何来源 |
| `nop.err.http.download-range-not-satisfiable-recovered` | 416 后自动整体重传（INFO 级，不作失败） |
| `nop.err.http.upload-input-file-error` | 读取本地上传文件失败 |

## 四、拒绝了什么

- **Tus 可续传上传协议 / S3 分片上传**：两者都需要服务端实现配套状态机（offset 管理、part 编号）。BINARY 模式的目标是"自约定、零服务端状态"的单流直传；需要分片时由服务端另行立项。**续传只做下载侧**（Range 是 HTTP 通用能力，wget/curl/Nginx 默认支持）。
- **上传侧用 Range/Content-Offset 续传**：非标准（RFC 9110 的 Range 只定义在 GET），服务端支持罕见，协议成本高。
- **摘要状态序列化（跨进程续传免重读）**：`MessageDigest` 不可序列化/克隆不可靠。重读本地 `.part` 预热的代价只是一次本地顺序读（无网络），且代码无状态。
- **If-Range + ETag 持久化**：严格防"资源变更后续传"需要跨进程保存 ETag（状态文件）。替代方案：`Content-Range.start` 对账 + 最终摘要校验，已覆盖"续错数据"的两种来源（offset 错位、内容变更），无需持久化状态。
- **MD5 校验**：MD5 已不具备安全强度；sidecar 只接受 sha256/sha1。
- **下载预分配文件 + RandomAccess 写**：不是所有文件系统/`IHttpOutputFile` 实现都支持定长预分配；追加写 + 原子 rename 已满足原子性与续传。
- **三实现共享一个"通用文件传输客户端"**：共享逻辑已在 api 层（helper）；IO 执行与各客户端的请求构建/异步模型（HC5 consumer / JDK Flow / OkHttp call）强耦合，抽象成通用执行器会制造第二个平行框架。

## 五、与已有设计的关系

- 依赖 `IHttpClient.fetchAsync` 的既有语义（错误包装、header 传递）作为 sidecar 探测的执行通道。
- `.part` 布局与 `DownloadOptions.initialOffset`（既有字段）的关系见 3.3。
- MQTT 侧的 `sendAsync`/`subscribe` 实现见本目录 `mqtt-messaging-design.md`（独立的传输域，不共用本协议）。
