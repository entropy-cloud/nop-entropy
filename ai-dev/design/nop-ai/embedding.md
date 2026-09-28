# Embedding 客户端设计（K1）

**日期**：2026-09-28
**范围**：`nop-ai/nop-ai-core`（`EmbeddingServiceImpl` + `EmbeddingOptions.provider` + 配置面）
**状态**：active（K1 已落地）
**来源**：`ai-dev/backlog/knowledge-rag-roadmap.md` K1；执行计划 `ai-dev/plans/knowledge-rag/01-k1-embedding-api-client.md`
**相关裁定**：P1-MA5-003（`IEmbeddingModel` SPI 契约）、P3-MA3-003（`04-rag-module-position.md` 落点裁定）、P2 round-4（reserved 契约族）

---

## 一、设计结论

**裁定 = 在 nop-ai-core 落地 OpenAI 兼容 embedding 客户端 `EmbeddingServiceImpl`，作为 `IEmbeddingModel` 的首个平台生产实现；与 `ChatServiceImpl` 同构复用 llm.xml 配置面与可靠性范式。**

1. **落点 = nop-ai-core**（roadmap K1 落点列 + `ChatServiceImpl` 同构先例）。`04-rag-module-position.md` 的"第一个真实消费方触发"条件由 K1 达成；该模块预期的 `IVectorStore`（K2）与 RAG 管线（K3）实现仍落 `nop-ai-rag`，本裁定不改变其定位。
2. **协议 = OpenAI 兼容 v1 唯一方言**：POST `{baseUrl}{embedUrl}`，请求体 `{"model": ..., "input": [...]}`，响应解析 `data[].embedding`（按 `index` 排序还原输入顺序）。`apiStyle != openai` 的 provider fail-loud（`ERR_AI_EMBEDDING_UNSUPPORTED_API_STYLE`），不做隐式形状猜测。方言扩展点：出现真实需求时按需引入 embedding 方言层（另立 plan）。
3. **provider 路由**：`EmbeddingOptions.provider`（additive 字段）> `nop.ai.embedding.default-llm` 配置 > fail-loud（`ERR_AI_EMBEDDING_NO_PROVIDER`，无隐式 provider）。模型名：`options.model` > llm.xml `defaultModel` > fail-loud。
4. **bean 装配**：`nopAiEmbeddingModel`（`ioc:default="true"` + `ioc:type=IEmbeddingModel`，镜像 `nopChatService`）。未配置 provider 时 bean 可创建、首次调用 fail-loud——消费方 wiring 期 fail-fast 语义保持可观察。

## 二、可靠性语义（与 chat 侧对齐）

| 面 | 语义 | 配置 |
|----|------|------|
| 限流 | `LlmModel.rateLimit` + `IRateLimiter.tryAcquire(1, timeout)`，超时抛 `ERR_AI_RATE_LIMITED`（httpStatus=429） | `nop.ai.service.rate-limit-acquire-timeout` |
| 重试 | `IRetryPolicy`（默认 `StandardRetryPolicy` 3 次尝试；字段默认 + 普通 setter，不经 IoC）；TRANSIENT/RATE_LIMITED 退避重试，Retry-After 作下限；延迟经可注入 `ScheduledExecutorService` 延迟链，不在 IO 完成线程阻塞 | 无（策略级） |
| 账号链 failover | QUOTA_EXCEEDED/AUTH_INVALID → `<accounts>` 依次切换（apiKey/baseUrl 覆盖）；链耗尽抛 `ERR_AI_EMBEDDING_ACCOUNT_CHAIN_EXHAUSTED`；FALLBACK 步数上限 16 | llm.xml `<accounts>` |
| 错误分类双通道 | 响应级非 200 经 `ILlmDialect.parseErrorResponse`（应用 `<errorMappings>` 配置面，QUOTA/AUTH 由此可达）；传输级异常经 `LlmErrorClassifier.classify(Throwable)`。与 `LlmCallCoordinator` 架构逐点同构 | llm.xml `<errorMappings>`/`<errorResponse>` |
| baseUrl/apiKey 链 | baseUrl：账号覆盖 > `nop.ai.llm.{provider}.base-url` > `config.getBaseUrl()` > `ERR_AI_SERVICE_NO_BASE_URL`；apiKey：账号 > `IAiModelCredentialResolver`（可选注入）> `resolveApiKey`（config 变量/secret 文件） | 同 chat |

## 三、使用契约

```xml
<!-- /nop/ai/llm/{provider}.llm.xml -->
<llm apiStyle="openai" defaultModel="text-embedding-3-small">
    <baseUrl>https://api.example.com/v1</baseUrl>
    <embedUrl>/embeddings</embedUrl>
    <accounts>
        <account id="backup-1" apiKey="..." baseUrl="https://backup.example.com/v1"/>
    </accounts>
</llm>
```

```yaml
# application 配置
nop.ai.embedding.default-llm: my-embed-provider
nop.ai.llm.my-embed-provider.api-key: sk-...
```

- `embedAll` 单请求批量（`input: [...]`）；空列表不经网络直接返回空。
- 失败路径全部 fail-loud（异常上抛），无静默降级；`embedUrl` 缺失抛 `ERR_AI_EMBEDDING_NO_EMBED_URL`。
- 维度/usage 记录于 `VectorData` metadata（key `model`）。

## 四、拒绝的替代方案

- **落点 nop-ai-rag**：roadmap K1 落点列明 nop-ai-core，且 `ChatServiceImpl`（同为带 HTTP 客户端的实现类）已在 nop-ai-core——同构优先；nop-ai-rag 保持 K2/K3 实现落点。
- **自建 HTTP 栈**：复用 `IHttpClient` 抽象（chat 同款），不引入新客户端依赖。
- **自建重试/账号链逻辑**：复用 reliability 包（`StandardRetryPolicy`/`AccountChain`），不复制语义。
- **非 openai 方言静默按 openai 发送**：形状错误难诊断，fail-loud 显式拒绝。

## 五、与消费方的关系

- **N4.2（nop-code）**：`ITextEmbedding` 生产实现将委托本客户端（另立 plan，见 `ai-dev/plans/nop-code/23-*`）。
- **K2/K3**：向量库驱动与 RAG 管线的 embedding 来源即本实现；`IVectorStore` 实现落 `nop-ai-rag`。
- **`EmbeddingModelBasedClassifier`**：既有构造注入消费路径不变（编译 + 既有测试绿）。
