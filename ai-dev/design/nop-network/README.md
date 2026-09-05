# nop-network 子系统设计

> 本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织。

## 定位

`nop-network/` 承载网络 IO 外围模块（`nop-codec`、`nop-http`、`nop-netty`、`nop-rpc`、`nop-socket`、`nop-vertx`）的架构决策与使用契约。这些模块自底向上为平台提供：字节编解码、HTTP 客户端抽象（`IHttpClient`）、TCP/RPC 通信、MQTT 接入。

## 文档结构与阅读顺序

| 文档 | 层级 | 职责 |
|------|------|------|
| `file-transfer-design.md` | Architecture Baseline（文件传输专题） | HTTP 文件上传双模式协议、下载断点续传与内容校验协议、选项模型契约 |
| `mqtt-messaging-design.md` | Architecture Baseline（MQTT 消息专题） | MQTT 下行推送、订阅路由与消息总线桥接（`IMessageService`）契约 |

## Vision 层要点（本目录简述）

- `IHttpClient` 是跨实现（Apache/JDK/OkHttp）的**行为等价契约**：同一 `HttpRequest` + `UploadOptions`/`DownloadOptions` 在三个实现上必须产生相同的线上协议（方法、头、体格式）与相同的失败语义（错误码、部分文件处理）。实现间允许存在的能力差异必须显式抛 `UnsupportedOperationException`，不允许静默降级。
- 传输协议约定（自定义头、校验文件布局）一旦发布即为**跨服务契约**，修改需走破坏性变更流程。
- 模块依赖方向：`nop-http-api` 只依赖 `nop-api-core`（零重依赖），协议公共逻辑（校验、续传状态）放在 api 层，IO 执行放在各客户端实现层。

## 相关文档

- `docs-for-ai/02-core-guides/rpc-and-distributed-rpc.md` — RPC 分层调用模型（`/px/` 代理、token 转发）
- `ai-dev/analysis/2026-09-05c-nop-network-deep-bug-review.md` — 2026-09 全模块缺陷审查历史（文件传输与 MQTT 能力缺口的出处）
