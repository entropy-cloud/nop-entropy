# LLM 错误规范化与配额感知恢复设计

**日期**：2026-08-01（R1 审查修订）
**范围**：`nop-ai-core`（`llm.xdef` + `ChatServiceImpl`）+ `nop-ai-agent`（`LlmErrorClassifier` / `IRetryPolicy` / `IModelRouter`）+ `nop-http`（`ServerEventPublisher` 头传递）
**状态**：草案（待 plan 落地）
**前置文档**：`nop-ai-agent-llm-layer.md`（§6.5 回退错误分类、§7 重试策略、§7.6 Retry-After 多源解析均为本篇补齐的显式 Non-Goal）
**可行性验证**：`ai-dev/analysis/2026-08/2026-08-01-llm-error-mapping-feasibility-analysis.md`（主流 API 真实错误格式 + 4 个参考实现调研，证实配置方案 ~90% 可达）

---

## 一、设计结论

1. **把 provider 的异构错误响应规范化为少数固定分类**，规范化规则是**配置驱动**的，写在 `llm.xdef` 对应的 `{provider}.llm.xml` 里——直接复刻 `dialect.xdef` 的 `<errorCodes>` 模式（厂商码 → 标准码），而非每个 provider 写一段 Java。

2. **规范化发生在底层 `ChatServiceImpl`，且必须覆盖默认（流式）路径**。经 R1 审查核实：`callAsync` 默认 `stream=true`（`ChatServiceImpl.java:91`），默认路径走 `aggregateStreamToResponse → callStream`。关键事实是——流式路径的 `ServerEventPublisher` 在非 2xx 时**已经把响应体放进异常**（`ARG_BODY`，`ServerEventPublisher.java:94-97`）。因此根因不是"体被丢弃"，而是"体已在异常里但无人规范化它，且 `Retry-After` 头三条错误发射点都丢了"。规范化点落在 `callStream.onError`（覆盖直接流式 + 聚合流式两条），非流式路径单独处理，详见 §3.4。

3. **固定分类驱动两种恢复动作**：等待（`RATE_LIMITED` 按 `Retry-After` 重试同一账号）或切换账号（`QUOTA_EXCEEDED` / `AUTH_INVALID` 走账号回退链）。分类是事实判断，恢复是策略判断，两者解耦。

4. **账号回退链 ≠ 模型 tier 回退链**，且二者必须由不同的 `RetryDecision` 通道区分**。现有 `FALLBACK` 已被 `IModelRouter.getFallback`（模型 tier）占用；`QUOTA_EXCEEDED`/`AUTH_INVALID` 切账号若复用 `FALLBACK`，会触发错误的恢复（把好模型降级）。详见 §3.6 的通道区分。

5. **`ErrorClassification` 上移到 `nop-ai-core`**：规范化配置引用它，底层产出它，故它必须落在 `llm.xdef` 的 bean-package 所在层（Layer 1）。该枚举必须是**纯词汇**（不 import 任何 Layer-3 类型），以保持依赖方向（L3 → L1）。

---

## 二、背景与动机

### 2.1 现状：运行到一半报错没有经费

无人值守长任务跑到中途，模型订阅额度耗尽，provider 返回错误。应用层无法区分这是"等几秒就好"还是"这个账号彻底没钱了，立刻换账号"，只能笼统失败或盲目重试——盲目重试一个已耗尽额度的账号只会把整条链路堵死。

### 2.2 根因（R1 审查核实后的精确版）

经核查两条调用路径，根因不是单点"体被丢弃"，而是分路径的两个事实：

**默认（流式）路径**（`stream` 默认 `true`，`ChatServiceImpl.java:91`）：
- `ServerEventPublisher.processResponse` 在非 2xx 时抛 `NopException(ERR_HTTP_RESPONSE_ERROR)`，**已携带 `ARG_BODY`（响应体文本）和 `ARG_HTTP_STATUS`**（`ServerEventPublisher.java:92-98`）。体**没有**被丢弃。
- 但：①`LlmErrorClassifier` 只读 `ARG_HTTP_STATUS`，**忽略 `ARG_BODY`**——区分信息就在体里却没人读；②HTTP 头（含 `Retry-After`）被读进局部变量（`:87`）却**从未挂到异常**——`Retry-After` 在此路径被丢。

**非流式路径**（显式 `stream=false`，`ChatServiceImpl.java:121-127`）：
- 在 `thenApply` 里只读 `getHttpStatus()` 抛 `ERR_AI_SERVICE_HTTP_ERROR`，**体和头全丢**。

**统一后果**：`LlmErrorClassifier` 只能做最粗的 HTTP 状态码映射（429→RATE_LIMITED，5xx→TRANSIENT，4xx→NON_TRANSIENT），把"解析 body / 读 Retry-After / 区分 429 子类型"全部列为显式 Non-Goal（`LlmErrorClassifier.java:33-43`）。`QUOTA_EXCEEDED` 枚举值**从未被任何代码生产过**。而多家 provider 把 `rate_limit_exceeded`（可重试）与 `insufficient_quota`（不可重试）放在同一个 429 里，区别只在 body：

| Provider | 同一个 429，体里的区别 |
|----------|----------------------|
| OpenAI | `error.type` = `rate_limit_exceeded` vs `insufficient_quota` |
| Anthropic | `error.type` = `rate_limit_error` vs `overloaded_error` |
| 各厂商 | HTTP 状态几乎都是 429，区分信息全在 body |

### 2.3 对比 dialect：同类问题早有成熟模式

`nop-dao` 的 `dialect.xdef` 用 `<errorCodes>` 把厂商错误码/SQLState/消息正则映射到一组**少数固定**的标准 `ErrorCode`。`DialectSQLExceptionTranslator` 按"厂商码 → SQLState → 消息正则 → 异常类型兜底 → UNCATEGORIZED"优先级翻译。LLM 层面对同一类问题（异构 provider、少数固定语义），却缺了这层配置驱动的翻译。本篇就是把 dialect 的成熟模式搬到 LLM 层。

> 诚实对照：dialect 的消息正则层其实也是**在 HashMap 上 first-match**（`DialectSQLExceptionTranslator.java:164-174`），并非纯扁平查表。所以本篇选有序规则表的真正理由不是"dialect 是纯 map"，而是 LLM 错误是**多维度合取**（status ∧ type ∧ code）无法压成单 key 查表——这一点与 dialect 的单维度（厂商码）查表有本质不同。

---

## 三、核心设计

### 3.1 两个正交关注点：规范化 vs 恢复

```
Provider 异构响应 ──规范化(事实)──▶ 固定分类 ──恢复策略(决策)──▶ 动作(等待/切换/终止)
  llm.xdef 配置驱动           ErrorClassification        IRetryPolicy + 账号链
  发生在底层 ChatService      底层产出，上层消费          发生在上层可靠性层
```

**规范化是纯事实判断**（这条响应是什么），**恢复是策略判断**（拿这个事实怎么办）。两者必须解耦——强行揉在一起会导致：换恢复策略就要改规范化逻辑，或换 provider 就要改重试逻辑。

### 3.2 固定分类（少数固定情况）

规范化目标是封闭的固定分类。在现有 `ErrorClassification`（4 类）基础上补齐 2 类：

| 分类 | 语义 | 同账号可重试? | 恢复动作 | 触发通道 |
|------|------|:---:|------|------|
| `TRANSIENT` | 5xx、超时、连接重置 | 是 | RETRY（退避） | RETRY |
| `RATE_LIMITED` | 429 每分/每秒限流（"太快了"） | 是（等待后） | RETRY（按 `Retry-After`，见 §3.7） | RETRY |
| `QUOTA_EXCEEDED` | 额度耗尽 / 计费上限 / 没钱了 | 否 | FALLBACK（切换账号） | SWITCH_ACCOUNT |
| `AUTH_INVALID` | 401/403 key 无效/过期/无权限 | 否 | FALLBACK（切换账号） | SWITCH_ACCOUNT |
| `NON_TRANSIENT` | 400 请求错误 / prompt 过长 / 内容过滤 | 否 | STOP | STOP |
| `CACHE_STATE_LOST` | 409 本地推理服务器缓存丢失（TRANSIENT 族） | 是（回放） | RETRY（原样重发） | RETRY |

> `CACHE_STATE_LOST` 恢复动作等同 `TRANSIENT`，单列因其重试语义特殊（原样回放而非退避），见 `nop-ai-agent-llm-layer.md` §8.5。暂不区分可与 `TRANSIENT` 合并，配置层留扩展位。

**为何拆出 `AUTH_INVALID`**：现有默认启发式把 401/403 归入 `NON_TRANSIENT`（直接 STOP）。但典型诉求是"key 失效就换备用账号"，与"400 请求体错误"（换了账号也没用）的恢复动作相反，必须分开。

### 3.3 `llm.xdef` 扩展：配置驱动的错误映射

直接复刻 `dialect.xdef` 的 `<errorCodes>` 结构。dialect 是"厂商码列表 → 标准码"，LLM 是"多条件合取 → 固定分类"。新增两个配置节：

**`<errorResponse>` — 告诉底层从错误体抽哪些字段**（对应现有 `<response>` 抽成功体字段）。沿用 `prop-path`，不引入新表达式类型：

```
errorResponse:
  errorTypePath:    prop-path    # 如 OpenAI "error.type"
  errorCodePath:    prop-path    # 如 "error.code"
  errorMessagePath: prop-path    # 如 "error.message"
  retryAfterPath:   prop-path    # body 级 retry-after（部分 provider 放体里）
```

**`<errorMappings>` — 把字段值 + HTTP 状态映射到固定分类**。**有序规则表，首条匹配胜出**：

```
<errorMappings xdef:body-type="list">     <!-- ⚠️ 故意不带 xdef:key-attr -->
  <errorMapping
      classification="!enum:io.nop.ai.core.model.ErrorClassification"
      httpStatus="csv-set"        # 可选限定，如 "429" 或 "401,403"
      errorTypes="csv-set"        # 体里 error.type 的值集合
      errorCodes="csv-set"        # 体里 error.code 的值集合
      messagePattern="string"     # 消息正则（dialect 消息匹配同款，空白替下划线、. 跨行、大小写无关）
      retryAfterPath="prop-path"  # 该映射专用 retry-after 路径覆盖
  />
</errorMappings>

<!-- 可选全局逃生舱：覆盖配置表无法表达的硬场景（见 §3.3 末） -->
<classifyError>xpl-fn:(httpStatus, bodyMap, headers) => ErrorClassification</classifyError>
```

**`messagePattern` 定位为必需而非可选**（实测裁定，见 `ai-dev/analysis/2026-08/2026-08-01-llm-error-mapping-feasibility-analysis.md`）：Gemini 的 429 `RESOURCE_EXHAUSTED` 限流与配额同型，只有消息文本能区分；OpenAI 上下文超长等场景也靠文案。复刻 `dialect.xdef` 消息正则规则（空白替下划线、`.` 跨行、大小写无关，`DialectSQLExceptionTranslator.java:164-174`）。

**`<classifyError>` XPL 逃生舱**（覆盖配置表无法表达的 ~10% 硬场景）：配置 `<errorMappings>` 优先，命中即用；未命中再走 `<classifyError>`；都未命中走默认启发式。用于 Azure 嵌套多拼写（`error.inner_error.code` vs `error.innererror.code` 跨版本）、负向排除（匹配 X 但排除 Y 同时出现）等。风格与 `llm.xdef` 现有 `<buildHttpRequest>`/`<parseHttpResponse>` 的 `xpl-fn` 节点一致。

**⚠️ 关键 xdef 约束（R1 审查 G1）**：`<errorMappings>` 必须**省略 `xdef:key-attr`**。被模仿的 `<errorCodes xdef:key-attr="name">`（`dialect.xdef:137`）带 key-attr，会把 body 变成按 name 去重的 map（后写覆盖、不保序），与"首条匹配胜出"直接冲突——两条同 `classification` 不同优先级的规则会被合并、顺序丢失。省略 key-attr 才能让 list 保序、first-match-wins 成立。这是与 dialect 模式的**刻意偏离点**，必须文档化并有测试固化（断言两条同 classification 规则按位置先后分别命中）。

**匹配优先级**（规则之间的解析顺序，首条命中即定分类）：

```
对每条 errorMapping（按 XML 声明顺序）:
  若 (httpStatus 未设 或 实际状态 ∈ httpStatus)
     且 (errorTypes 未设 或 抽到的 error.type ∈ errorTypes)
     且 (errorCodes 未设 或 抽到的 error.code ∈ errorCodes)
     且 (messagePattern 未设 或 消息匹配正则)
     → 命中，取其 classification
全部未命中 → 默认启发式（按 HTTP 状态码，等价今日行为）
```

**为何是有序规则表而非 dialect 的扁平 map**：dialect 单维度（厂商码）可 O(1) 查表；LLM 错误是多维度合取（status ∧ type ∧ code）无法压成单 key，且需精确表达"429 + insufficient_quota → QUOTA_EXCEEDED"优先于"429 → RATE_LIMITED"——顺序即优先级，对配置者最直观。

**示例**（OpenAI 风格，仅示意配置形态）：

```xml
<errorResponse errorTypePath="error.type" errorCodePath="error.code"
               errorMessagePath="error.message"/>

<errorMappings>
  <errorMapping classification="QUOTA_EXCEEDED" httpStatus="429"
                errorTypes="insufficient_quota,billing_limit_reached"/>
  <errorMapping classification="RATE_LIMITED"  httpStatus="429"
                errorTypes="rate_limit_exceeded"/>
  <errorMapping classification="AUTH_INVALID"  httpStatus="401,403"/>
  <errorMapping classification="NON_TRANSIENT" httpStatus="400"
                errorTypes="context_length_exceeded,content_filter"/>
</errorMappings>
```

与 dialect 的对照：

| dialect `<errorCodes>` | LLM `<errorMappings>` |
|---|---|
| `<errorCode name="std-code" xdef:key-attr="name">1062</errorCode>` 单值、带 key | `<errorMapping classification="...">` 多条件合取、**不带 key** |
| 按 vendor code / SQLState 精确查表 | 按规则顺序首匹配 |
| 消息正则兜底（HashMap 上 first-match） | 同款 `messagePattern` 兜底 |
| 输出标准 `ErrorCode` | 输出固定 `ErrorClassification` |
| 缺省 `UNCATEGORIZED` | 缺省走 HTTP 状态启发式 |

### 3.4 底层 `ChatServiceImpl` 封装（覆盖两条路径）

底层职责：**在两条错误路径上都做"抽 body+status+头 → 按配置规范化 → 抛带分类的 `LlmProviderException`"**。规范化逻辑收敛为一个共享 helper（如 `LlmErrorNormalizer`），两个错误点都调它，避免分叉。

**路径 A — 流式（默认，含两个错误发射点）**：
- 今天 `ServerEventPublisher` 已把 `ARG_BODY` + `ARG_HTTP_STATUS` 放进异常（`ServerEventPublisher.java:94-97`）。
- **规范化点必须落在 `callStream.onError`**（`ChatServiceImpl.java:185-187`），而非 `aggregateStreamToResponse.onError`。理由（R2 审查 N1）：流式实际有**两个**错误发射点——①直接 `callStream` 的订阅者（`onError` → `publisher.closeExceptionally(throwable)`，`:185-187`），②`callAsync(stream=true)` 经 `aggregateStreamToResponse` 订阅 `callStream` 的 publisher（`:294-323`）。若 normalizer 放在 `aggregateStreamToResponse`，直接调 `callStream` 的订阅者拿到的是未规范化的原始异常。把 normalizer 下沉到 `callStream.onError`（在 `closeExceptionally` **之前**规范化），则两条流式路径都被一处 hook 覆盖（DRY）——`aggregateStreamToResponse` 订阅的就是已规范化的 publisher。
- **`Retry-After` 头的前提条件**：当前 `ServerEventPublisher` 把头读进局部变量（`:87`）但未挂异常。要让流式路径拿到 `Retry-After`，必须让 `ServerEventPublisher`（`nop-http`）把响应头也挂到抛出的异常上（新增如 `ARG_RESPONSE_HEADERS`）。这是**跨模块前置改动**，属 `nop-http` 范畴。若暂不改动 `nop-http`，则流式路径的 `Retry-After` 只能从 body 取（`<errorResponse retryAfterPath>`），头级 `Retry-After` 为已知缺口——必须文档化。

**路径 B — 非流式**：
- 今天 `callAsync` 在 `httpStatus != 200` 时只读状态码（`ChatServiceImpl.java:121-127`），体和头全丢。
- 规范化点：在 `thenApply` 里先读 `response.getBodyAsString()` + 响应头（`IHttpResponse extends IHttpHeaders`，`IHttpResponse.java:12`）→ 调 normalizer → 抛 `LlmProviderException`。此路径可拿到完整头，`Retry-After` 无缺口。

**`LlmProviderException` 契约**（`NopException` 子类）：
- 携带 `ARG_HTTP_STATUS`（向后兼容现有 `LlmErrorClassifier`）。
- 新增 `ARG_ERROR_CLASSIFICATION`、`ARG_RETRY_AFTER_MS`、`ARG_PROVIDER_ERROR_TYPE`、`ARG_PROVIDER_ERROR_CODE`、`ARG_PROVIDER_ERROR_MESSAGE`、`ARG_RESPONSE_BODY`。

**契约约束**：
- 默认启发式与今日 `LlmErrorClassifier` 一致（429→RATE_LIMITED，5xx→TRANSIENT，4xx→NON_TRANSIENT），保证未配置 `<errorMappings>` 的 provider 零回归。
- 流式保护不变（`nop-ai-agent-llm-layer.md` §7.4）：已流出内容后的错误不触发 RETRY/FALLBACK。规范化只负责给异常打分类，是否重试仍由策略层 + `hasStreamedContent` 决定。
- **HTTP 200 带 error body 是已知缺口**（R2 审查 N2）：`llm.xdef` 的 `<response errorPath>`（`:67`）表明部分 provider 可能返回 HTTP 200 但 body 里是错误。本篇的 `<errorMappings>` 只在非 2xx 异常路径上咨询；200-with-error 会经 `dialect.parseResponse` 当成功流过、不被分类。多数 provider 用非 2xx 报错，影响有限，列为已知缺口/后续 successor（可扩展 `<errorMappings>` 在 `errorPath` 非空时也匹配成功 body）。
- **`DefaultAiChatService` 范围说明**（R1 审查 A2）：`DefaultAiChatService.java:201-205` 有同样"抛 HTTP 错误、丢体/头"的缺陷。本篇首版仅规范化 `ChatServiceImpl`；`DefaultAiChatService` 列为已知未覆盖路径（§6 跟踪），建议后续提取共享 normalizer 复用。

### 3.5 上层消费：分类驱动恢复决策

`LlmErrorClassifier` 优先信任底层已做的规范化：

```
classify(error):
  若 error 是 LlmProviderException 且携带 classification
     → 直接返回其 classification（底层已按配置规范化，信任之）
  否则 → 走现有 HTTP 状态启发式
         （向后兼容：底层未升级 / 非 provider 异常 / DefaultAiChatService 路径）
```

`RetryContext` 增加 `retryAfterMs`（从 `LlmProviderException` 取；今天 `RetryContext` 无此字段，是真实接口变更）。

### 3.6 备用账号链与 FALLBACK 通道区分（R1 审查 G2）

`RetryDecision` 今天只有 `RETRY/STOP/FALLBACK`，而 `FALLBACK` **已被** `IModelRouter.getFallback`（模型 tier）占用（`IRetryPolicy.java:16-20`、`SmartModelRouter.java:122-152`）。若把 `QUOTA_EXCEEDED`/`AUTH_INVALID` 也映射到 `FALLBACK`，重试循环会调 `getFallback` 做**模型 tier 降级**——这是错误恢复（模型没问题，是 key/额度没了）。

**必须区分两个回退通道**。三选一（推荐 b）：

| 方案 | 改动 | 取舍 |
|------|------|------|
| a. 新增 `RetryDecision.SWITCH_ACCOUNT` | 枚举 +1，重试循环多一个分支 | 最显式，但 `RetryDecision` 膨胀 |
| b. 重试循环按 `errorClassification` 路由 | 无枚举改动；循环判分类选账号链 vs `getFallback` | 最小接口改动；循环与分类耦合（可接受，循环本就持有 `RetryContext`） |
| c. `RetryOutcome` 携带回退目标类型 | `RetryOutcome` 扩字段 | 接口改动中等 |

**推荐 b**：重试循环在收到"需回退"信号时，先看 `RetryContext` 的 `errorClassification`——`QUOTA_EXCEEDED`/`AUTH_INVALID` 走账号链，`TRANSIENT`（模型 tier 回退场景）走 `IModelRouter.getFallback`。无需扩 `RetryDecision`，且循环本就持有 `RetryContext`，耦合可控。

**账号链与模型 tier 回退是两个独立维度**：

| 维度 | 触发分类 | 实体 | 入口 |
|------|---------|------|------|
| 模型 tier 回退 | `TRANSIENT`（5xx/超时，现有已落地） | 另一个模型 | `IModelRouter.getFallback` |
| 账号回退 | `QUOTA_EXCEEDED` / `AUTH_INVALID` | 同模型另一套 key/额度 | 账号链（本篇新增） |

**账号链配置**（provider 级，写在 `{provider}.llm.xml` 或独立账号清单）：provider 声明有序备用账号（每个 = `apiKey` 引用 + 可选独立 `baseUrl` + 额度元数据）。命中账号链则切到下一个账号重试（attempt 重置、usage 归属新账号），链耗尽则 fail-loud（Minimum Rules #24，不静默跳过）。

**为何不复用 `IModelRouter` 管账号**：router 是前置路由（按复杂度选模型，请求发出前），账号回退是后置恢复（按错误切 key，请求失败后）。两者输入时机与数据不同，合并会让 router 同时背"前置选模型"和"后置选账号"两个不相关职责。

> 账号链的持久化形态（是否落库、额度元数据结构）属实现细节，留给 plan。

### 3.7 `Retry-After` 多源解析与 jitter 关系（R1 审查 G3）

补齐 `nop-ai-agent-llm-layer.md` §7.6：

```
优先级:
  1. HTTP 头 retry-after-ms（毫秒）
  2. HTTP 头 retry-after（秒或 HTTP-date）
  3. body 字段（<errorResponse retryAfterPath> 或 <errorMapping retryAfterPath>）
  4. 缺省退避：min(baseDelay * 2^attempt, maxDelay) + full jitter
```

底层把 1~3 归一为 `retryAfterMs` 放进 `LlmProviderException`，策略层只消费这一个值。

**`retryAfterMs` 与 full-jitter 的关系（必须显式裁定）**：`StandardRetryPolicy` 今天对**每个** RETRY 都加 full jitter（`computeBackoff`，`StandardRetryPolicy.java:144-166`）。若对 `RATE_LIMITED` 原样用 `retryAfterMs`，会丢掉 thundering-herd 抑制；若把 jitter 套在 `retryAfterMs` 上（如 `uniform(0, retryAfterMs)`），等待可能**低于服务器要求**，导致立即再被拒。

**裁定：`Retry-After` 作为下限（floor）**：`RATE_LIMITED` 的 `delay = retryAfterMs + uniform(0, jitterCap)`，**永不低于** `retryAfterMs`。这刻意让 `RATE_LIMITED` 偏离纯 full-jitter 公式——理由：遵守服务器明确要求的等待，重要性高于 herd 抑制（配额/限流信号下，服务器已告知精确等待，不应抢跑）。`TRANSIENT` 的退避仍用纯 full-jitter（无服务器提示）。两者策略不同须文档化。

### 3.8 分层归属与依赖方向

```
Layer 1 (nop-ai-core):  llm.xdef 配置 + ChatServiceImpl 规范化 + ErrorClassification 定义(纯词汇)
                          ↓ 产出 LlmProviderException(已分类)
Layer 3 (nop-ai-agent): LlmErrorClassifier(信任底层) + IRetryPolicy(恢复决策) + 账号链消费
Layer 0 (nop-http):     ServerEventPublisher 头传递（前置改动，见 §3.4 路径 A）
```

`ErrorClassification` 当前在 `nop-ai-agent.reliability`（Layer 3）。规范化配置（`llm.xdef`，`xdef:bean-package=io.nop.ai.core.model`）要引用它，故它**必须上移到 `io.nop.ai.core.model`**。约束：core 中的该枚举**不得 import 任何 Layer-3 类型**，是纯词汇枚举；agent 层原有引用改为引用 core 定义，依赖方向（agent → core）不变。涉及 `RetryContext`/`IRetryPolicy`/`StandardRetryPolicy`/`LlmErrorClassifier` 及其测试的全量回归。

---

## 四、拒绝了什么

### 4.1 拒绝：每个 provider 在 `ILlmDialect` 里写 Java 解析错误

**方案**：各 dialect 实现 `parseErrorResponse()`。

**拒绝理由**：与 dialect 既定哲学冲突——`dialect.xdef` 刻意把厂商差异做成**数据**（`<errorCodes>`），加一家库不改一行 Java。LLM 层已在 `llm.xdef` 把成功响应解析做成数据（`<response>` 的 prop-path），错误响应理应同构。写成 Java 会让"加一个错误类型映射"变成改代码+发版，且散落各 dialect 难以全局审视。

### 4.2 拒绝：把恢复策略塞进 `llm.xdef`

**方案**：在 `<errorMapping>` 上直接写 `action="retry|fallback|switch-account|stop"`。

**拒绝理由**：违反 §3.1 正交原则。规范化是事实（这条响应是 quota 超限），恢复是策略（该等还是该换——取决于运维配置、时段、是否有备用账号）。同一份错误映射应能服务多套恢复策略；把动作焊死在映射里，换策略就得改所有 provider 配置。

### 4.3 拒绝：用 HTTP 状态码做唯一判据（维持现状）

**方案**：保持 `LlmErrorClassifier` 只看状态码。

**拒绝理由**：多家 provider 对 `rate_limit` 与 `insufficient_quota` 用同一个 429，区别只在 body。状态码唯一判据永远生产不出 `QUOTA_EXCEEDED`，"切换备用账号"无从触发——这正是当前痛点。该方案即 Non-Goal 本身。

### 4.4 拒绝：账号回退复用 `IModelRouter` 或现有 `FALLBACK` 通道

**方案**：让 `IModelRouter.getFallback` 同时管模型 tier 和账号；或 `QUOTA_EXCEEDED` 直接复用 `FALLBACK`。

**拒绝理由**：见 §3.6。router 是前置路由（按复杂度选模型），账号回退是后置恢复（按错误切 key），输入时机与数据不同；且现有 `FALLBACK` 已被模型 tier 占用，复用会触发错误恢复（把好模型降级）。两通道必须区分。

---

## 五、与已有设计的关系

- **`nop-ai-agent-llm-layer.md` §6.5 / §7 / §7.6**：本篇补齐其显式 Non-Goal——429 子类型区分、Retry-After 多源解析、body 解析。落地后把该文档对应行的 `deferred` 改为 `✅` 并指向本篇。
- **`nop-ai-agent-reliability.md`**：错误分类是可靠性层输入，本篇扩展了分类集合与恢复决策表，应在其错误分类章节同步引用。
- **`dialect.xdef` / `DialectSQLExceptionTranslator`**：本篇直接模式来源，结构对照见 §3.3 末尾表。
- **`IModelRouter.getFallback`（plan 209）**：模型 tier 回退已落地；本篇新增账号回退与之并行、通道不同，见 §3.6。
- **`nop-ai-agent-usage-and-billing.md`**：账号回退后 usage 应归属实际承接账号，需在该文档同步一句。
- **`nop-http` `ServerEventPublisher`**：流式路径取 `Retry-After` 头的前置改动落点，见 §3.4 路径 A。

---

## 六、落地约束（留给 plan）

1. **零回归红线及其两个支撑不变量（R1 审查 G4）**：未配置 `<errorMappings>` 的 provider 行为须与今日完全一致。这依赖两个不变量——① `QUOTA_EXCEEDED`/`AUTH_INVALID`/`CACHE_STATE_LOST` 今日不可达（无人生产）；②默认启发式把 401/403 映射为 `NON_TRANSIENT`（**不是** `AUTH_INVALID`），故 `AUTH_INVALID` 只能经配置后的 `<errorMappings>` 到达。**任何对默认启发式的未来改动都必须对照审计这两个不变量。**

2. **`ErrorClassification` 上移先于底层改造**：core 中的枚举是纯词汇（不 import Layer-3 类型）；agent 层原枚举改引用 core，全量回归 `reliability` 包测试。

3. **`<errorMappings>` 不带 `xdef:key-attr`**（R1 审查 G1）：必须保序以支持 first-match-wins。有测试断言两条同 `classification` 规则按位置先后分别命中。

4. **流式保护不被破坏**：已流出内容后的错误不触发 RETRY/FALLBACK（§7.4 不变）。规范化只打分类，重试与否仍由策略层 + `hasStreamedContent` 决定。

5. **流式路径 `Retry-After` 头缺口**：依赖 `nop-http` `ServerEventPublisher` 挂响应头到异常的前置改动。若该改动未完成，流式路径仅支持 body 级 `Retry-After`，须文档化为已知缺口。

6. **`DefaultAiChatService` 未覆盖**（R1 审查 A2）：首版仅规范化 `ChatServiceImpl`。`DefaultAiChatService` 列为已知未覆盖路径，后续提取共享 normalizer 复用。

7. **`<errorResponse>` / `<errorMappings>` 是可选配置节**，不破坏现有 `{provider}.llm.xml` 加载。

8. **`StandardRetryPolicy` 行为变更（R2 审查 N3）**：今天 `StandardRetryPolicy.shouldRetry` 对 `QUOTA_EXCEEDED`/`AUTH_INVALID` 返回 STOP（`StandardRetryPolicy.java:119-122`，因这两类不在 `TRANSIENT`/`RATE_LIMITED` 白名单内），且标准策略从不产生 `FALLBACK`——故 §3.6 的"按分类路由账号链"今日**不可达**。落地时 `StandardRetryPolicy` 必须改为：`QUOTA_EXCEEDED`/`AUTH_INVALID` → 返回 FALLBACK（交由重试循环按 §3.6 方案 b 路由账号链），`TRANSIENT` 保持 RETRY。此策略变更是 §3.6 路由的前置条件，须有测试固化且验证不破坏零回归红线（依赖 §6.1 两个不变量）。

9. **账号链 fail-loud**（Minimum Rules #24）：链耗尽抛异常不静默，须有测试固化。
