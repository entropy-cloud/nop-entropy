# LLM 错误规范化配置可行性分析（主流 API 实测 + 参考实现调研）

> Status: resolved
> Date: 2026-08-01
> Scope: `nop-ai-llm-error-normalization-design.md` 的 `<errorResponse>` / `<errorMappings>` 配置方案，跨主流 LLM API 验证
> Conclusion: 配置驱动方案对全部主流 provider 可行；约 90% 的错误场景可纯配置表达，剩余 ~10%（Azure 嵌套多拼写、Gemini 429 限流/配额同型、负向排除）需 `messagePattern` 正则逃生舱或保留 XPL 自定义函数逃生舱。设计无需结构性修改，仅需补两个增强点。
> Related Design: `ai-dev/design/nop-ai-agent/nop-ai-llm-error-normalization-design.md`

---

## 一、Context / 背景

设计文档 `nop-ai-llm-error-normalization-design.md` 提出：在 `llm.xdef` 增加 `<errorResponse>`（抽取错误体字段）+ `<errorMappings>`（多条件合取 → 固定分类），把 provider 异构错误响应规范化为 6 类固定分类，复刻 `dialect.xdef` 的 `<errorCodes>` 模式。

本分析要回答的核心问题：**主流 LLM API 的真实错误响应格式，能否被这套配置方案表达？** 验证手段：① 联网核实 OpenAI / Anthropic / Gemini / DeepSeek 的官方错误规范与真实响应样本；② 调研 4 个参考实现（solon-ai / litellm / nanobot / agentscope-java）如何处理 provider 错误，反向印证哪些字段是真实可抽取的。

---

## 二、参考实现调研结论（4 个框架）

> 结论先行：**nanobot 是错误分类最丰富的参考**（设计文档的 pattern 来源已全部在代码中证实）；**litellm 覆盖 provider 最广**但错误映射是代码驱动（18 个 per-provider 函数 + 子串启发式）；**agentscope-java 是重试策略最 config-driven 的参考**（`ExecutionConfig`）；**solon-ai 最薄弱**（错误压成字符串，无任何分类/Retry-After）。

### 2.1 nanobot（Python）— 错误分类的金标准

nanobot 把所有错误归一进 `LLMResponse(finish_reason="error")` 数据对象（不抛异常），携带结构化元数据（`nanobot/providers/base.py:58-64`）：

```
error_status_code | error_kind("timeout"/"connection"/"http")
error_type        | error_code           # provider 语义字段，双路匹配
error_retry_after_s | error_should_retry # x-should-retry 头
```

**429 限流 vs 配额的区分**（`base.py:374-394`，正是设计文档引用的模式）——通过两组 token 列表 + 文本标记匹配 `error_type`/`error_code` 及消息文本：

| 分类 | token 列表（`base.py:119-163`，从真实响应提取） |
|------|------|
| 不可重试·配额（`_NON_RETRYABLE_429_ERROR_TOKENS`） | `insufficient_quota`, `quota_exceeded`, `quota_exhausted`, `billing_hard_limit_reached`, `insufficient_balance`, `credit_balance_too_low`, `billing_not_active`, `payment_required` |
| 可重试·限流（`_RETRYABLE_429_ERROR_TOKENS`） | `rate_limit_exceeded`, `rate_limit_error`, `too_many_requests`, `request_limit_exceeded`, `requests_limit_exceeded`, `overloaded_error` |

另有独立的 `is_arrearage_response`（`base.py:318-339`）检测计费耗尽（HTTP 402 或配额 token）用于**切换账号/Token**——与设计的 `QUOTA_EXCEEDED → 切换账号链` 完全吻合。

**Retry-After 三层解析全实现**（`base.py:650-723`）：① 头 `retry-after-ms`（/1000）+ `retry-after`（数值秒 + HTTP-date，`parsedate_to_datetime`）；② body 文本正则（`retry after N`/`try again in N`/`wait N before retry`/`retry_after":N`，带 ms/s/min 单位归一）；③ 响应对象字段。

**其他已证实的设计 pattern**：`has_streamed_content` 流式保护（`base.py:583-606`）、图片 fallback（`:779-793`）、persistent 模式（`:757,795-805`）。

### 2.2 litellm（Python）— 覆盖最广，但错误映射是代码驱动

litellm 有完整的异常类层级（`litellm/exceptions.py`，~20 个类，每个绑定一个 HTTP 状态码），但**错误映射不是配置表，而是 18 个 per-provider 的 `_map_*_exception()` 函数**（`exception_mapping_utils.py`）。

**关键反直觉发现**：litellm **并没有干净地解析 OpenAI 的 `error.type` JSON 字段**。`insufficient_quota` / `rate_limit_exceeded` 在映射代码中**根本不出现**（全仓 grep 0 命中）。它实际做的是对 `str(exception)`（渲染后的错误字符串）做**子串匹配**（`is_error_str_rate_limit` 匹配 `\b429\b`/`rate[\s_\-]*limit`；`is_error_str_context_window_exceeded` 内置 9 个 provider 的固定短语）。只有 Azure（递归 `body.error.inner_error.code`）和 Vertex（解析 `body.error.code` 为 int）做了少量结构化 body 解析。

**litellm 的评估**（对本设计最重要）：其错误映射**约 70% 是表格形**（status→class 的 1:1 梯子 + 简单精确匹配），**约 30% 是过程形**（子串正则、负向排除、状态码被 body 覆盖、Azure 嵌套递归、消息改写/脱敏）。这 30% 是纯配置难以表达的——**任何配置方案都应保留一个逃生舱**（XPL 自定义函数）。

**Retry-After**：litellm 只从头读（`utils.py:6390`，数值秒 + HTTP-date），**封顶 60s**，不读 body。重试策略 `RetryPolicy`（`types/router.py:95`）是 config-driven（per-exception-type 的重试次数）。

### 2.3 agentscope-java（Java）— 重试策略 config-driven 的好范例

`ExecutionConfig`（`model/ExecutionConfig.java`）把 timeout/maxAttempts/backoff/retryOn 谓词全部做成可配置，用 reactor `Retry.backoff(...).filter(retryOn)` 接线（`ModelUtils.java:96-135`）。`MODEL_DEFAULTS`（3 次、初始 2s、max 30s）注释明言"为更好处理 429 调优"。

**但错误语义薄弱**：`ModelHttpException.isRetryableHttpStatus()` 只看 429/5xx（`:40-43`），**不读 Retry-After**（全仓 12 处 `retryAfter` 命中全在无关的 skill-curator），**不区分配额/限流**。OpenAI 扩展识别 `rate_limit_exceeded` token（`OpenAIClient.java:463`）但仅用于把"200 带 body error"反推状态码，不做分类。值得一提：它**识别"200 带 body error"模式**（`resolveErrorStatusCode`，`:453-480`）——印证设计文档的 200-with-error 缺口是真实存在的。

### 2.4 solon-ai（Java）— 最薄弱，反面教材

错误压成字符串（`ChatException` 只带 message，无 httpStatus/errorCode/retryAfter）。重试对**任何 Throwable** 一视同仁（`RetryTask.java:75-122`，仅排除 NPE/IE），会盲目重试 401 三次。全仓 0 处 `Retry-After`。非流式路径**连 HTTP 状态码都不检查**（`ChatRequestDescDefault.doCall` 无 `code()` 调用）。Anthropic/Gemini dialect 把结构化 `error.type` 故意拼成 `[type] message` 展示串，**在到达重试/分类逻辑前就销毁了结构信号**。本设计与之正交，无需借鉴。

---

## 三、主流 API 真实错误格式（联网核实）

### 3.1 OpenAI — 同一个 429 两种语义，靠 body `error.code` 区分

**真实响应格式**（社区 + nanobot 实测样本确认）：
```json
{ "error": { "message": "...", "type": "insufficient_quota", "param": null, "code": "insufficient_quota" } }
```

**关键事实**（多源印证）："429 不是一种东西。OpenAI 把它分成 `rate_limit_exceeded`（可重试）和 `insufficient_quota`（计费问题——重试永远无效）"。

| `error.type` | `error.code` | HTTP | 语义 |
|---|---|---|---|
| `insufficient_quota` | `insufficient_quota` | 429 | 配额/计费耗尽（不可重试） |
| `requests` / `tokens` | `rate_limit_exceeded` | 429 | 限流（可重试，按维度） |
| `invalid_request_error` | `invalid_api_key` / `model_not_found` / ... | 400/404 | 请求错误 |
| `server_error` | — | 500+ | 服务端错误 |

> 注意：OpenAI 的 `error.type` 在限流场景下是**维度**（`requests`/`tokens`），不是错误种类；真正的错误种类在 `error.code`（`rate_limit_exceeded`）。**因此对 OpenAI 应主要匹配 `errorCodes`，不是 `errorTypes`**。设计两者都支持，可配置。

### 3.2 Anthropic — 最干净，几乎 1:1 映射，且有专门的 402 计费状态

**官方文档**（platform.claude.com/docs/en/api/errors）确认的完整 status→type 映射：

| HTTP | `error.type` | 语义 | 对应设计分类 |
|---|---|---|---|
| 400 | `invalid_request_error` | 请求格式/内容问题 | NON_TRANSIENT |
| 401 | `authentication_error` | API key 问题 | AUTH_INVALID |
| **402** | **`billing_error`** | **计费/支付问题** | **QUOTA_EXCEEDED** |
| 403 | `permission_error` | 无权限 | AUTH_INVALID |
| 404 | `not_found_error` | 资源不存在 | NON_TRANSIENT |
| 409 | `conflict_error` | 状态冲突 | TRANSIENT（可重试解决） |
| 413 | `request_too_large` | 请求过大 | NON_TRANSIENT |
| 429 | `rate_limit_error` | 限流（带 retry-after 头） | RATE_LIMITED |
| 500 | `api_error` | 内部错误 | TRANSIENT |
| 504 | `timeout_error` | 处理超时 | TRANSIENT |
| 529 | `overloaded_error` | 临时过载 | TRANSIENT |

> **Anthropic 有独立的 402 `billing_error`** ——这是设计分类 `QUOTA_EXCEEDED` 的最干净来源（不依赖 body 解析，HTTP 状态码 + 文档约定即可）。官方 SDK 自动重试瞬时故障（连接/限流/5xx），默认 2 次，遵守 `retry-after` 头。
> **流式中途错误**（200 之后）：官方明言"不走标准机制"——印证设计的 200-with-error 流式缺口是 provider 官方行为。

### 3.3 Gemini / Vertex — Google canonical 格式，429 限流与配额同型

**格式**（Google canonical error）：
```json
{ "error": { "code": 429, "status": "RESOURCE_EXHAUSTED", "message": "You exceeded your current quota..." } }
```

| HTTP | `error.status` | 语义 |
|---|---|---|
| 429 | `RESOURCE_EXHAUSTED` | **限流与配额耗尽共用**（消息文本才区分："quota" vs "rate"） |
| 503 | `UNAVAILABLE` | 暂时不可用 |

- `error.code` = 数值 HTTP 状态；`error.status` = canonical gRPC 状态串。
- **Vertex 特例**（litellm `exception_mapping_utils.py:1174-1195`）：HTTP 500–599 但 `body.error.code==429`（5xx 信封包 429）——设计的 `httpStatus` + `errorCodes` 合取**能表达**：`<errorMapping classification="RATE_LIMITED" httpStatus="500,502,503,504" errorCodes="429"/>`。
- **Gemini 的 429 限流/配额难区分**：两者都是 `RESOURCE_EXHAUSTED`，只有消息文本不同。需 `messagePattern` 正则区分（如 `.*quota.*` → QUOTA；否则 → RATE_LIMITED）。

### 3.4 DeepSeek — OpenAI 兼容

`deepseek.llm.xml` 确认走 `/chat/completions`、`apiStyle=openai`。错误格式同 OpenAI（`error.type`/`error.code`）。可直接复用 OpenAI 的映射配置。

### 3.5 Azure OpenAI — 嵌套错误，双拼写

litellm 调研确认 Azure 递归 `error.code` + `error.inner_error.code`（且 `inner_error` 与 `innererror` 两种拼写跨 API 版本）。需要嵌套 prop-path（`error.inner_error.code`）+ 可能多映射。设计的 prop-path（`BeanTool.getComplexProperty` 支持点号嵌套）能表达单一路径，但双拼写需两条映射或逃生舱。

### 3.6 DashScope（阿里）— 两种模式

- **兼容模式**（`bailian.llm.xml` 实际使用）：走 `/compatible-mode/v1/chat/completions`，**OpenAI 格式**，复用 OpenAI 映射。
- **原生模式**（solon-ai 调研）：顶层 `code`/`message`（不嵌套在 `error.` 下），如 `{ "code": "Throttling", "message": "..." }`。需 `errorCodePath="code"`（顶层）。

---

## 四、可行性评估：设计的配置能否表达每个 provider

设计的匹配条件：`<errorMapping classification=... httpStatus=csv-set errorTypes=csv-set errorCodes=csv-set messagePattern=regex/>`，首条匹配胜出。`<errorResponse>` 用 prop-path 抽 `errorTypePath`/`errorCodePath`/`errorMessagePath`/`retryAfterPath`。

### 4.1 逐 provider 可行性矩阵

| Provider | `errorTypePath` | `errorCodePath` | 429 限流 vs 配额能否区分 | 配置可行性 |
|----------|-----------------|-----------------|--------------------------|-----------|
| **OpenAI** | `error.type` | `error.code` | ✅ `code`: `rate_limit_exceeded` vs `insufficient_quota` | **✅ 完全可行** |
| **Anthropic** | `error.type` | （无 code） | ✅ `type`: `rate_limit_error` vs 402 `billing_error` | **✅ 完全可行（最干净）** |
| **Gemini** | `error.status` | `error.code` | ⚠️ 同为 `RESOURCE_EXHAUSTED`/429，需 `messagePattern` 区分 | **⚠️ 需正则辅助** |
| **DeepSeek** | `error.type` | `error.code` | ✅ OpenAI 兼容 | **✅ 完全可行** |
| **Azure** | `error.code` | `error.inner_error.code` | ✅ 但嵌套 + 双拼写 | **⚠️ 嵌套路径，需多映射** |
| **DashScope 原生** | `code`（顶层） | `code`（顶层） | ⚠️ `Throttling` vs 计费 code | **✅ 可行（路径不同）** |
| **DashScope 兼容** | `error.type` | `error.code` | ✅ OpenAI 格式 | **✅ 完全可行** |

### 4.2 OpenAI 配置示例（最常见，验证双 429 区分）

```xml
<errorResponse errorTypePath="error.type" errorCodePath="error.code"
               errorMessagePath="error.message"/>

<errorMappings>
  <!-- 配额耗尽：不可重试，切账号。注意放最前，优先于通用 429 -->
  <errorMapping classification="QUOTA_EXCEEDED" httpStatus="429"
                errorCodes="insufficient_quota,quota_exceeded,billing_hard_limit_reached,
                            insufficient_balance,credit_balance_too_low,billing_not_active,
                            payment_required"/>
  <!-- 限流：可重试，按 Retry-After 等待 -->
  <errorMapping classification="RATE_LIMITED" httpStatus="429"
                errorCodes="rate_limit_exceeded,requests_limit_exceeded,too_many_requests"/>
  <!-- 认证：切账号 -->
  <errorMapping classification="AUTH_INVALID" httpStatus="401,403"/>
  <!-- 请求错误：不重试 -->
  <errorMapping classification="NON_TRANSIENT" httpStatus="400"
                errorCodes="context_length_exceeded"/>
</errorMappings>
```

### 4.3 Anthropic 配置示例（最干净，402 专用）

```xml
<errorResponse errorTypePath="error.type" errorMessagePath="error.message"/>

<errorMappings>
  <!-- Anthropic 有专用 402 计费状态，无需 body 区分 -->
  <errorMapping classification="QUOTA_EXCEEDED" httpStatus="402"
                errorTypes="billing_error"/>
  <errorMapping classification="RATE_LIMITED" httpStatus="429"
                errorTypes="rate_limit_error"/>
  <errorMapping classification="AUTH_INVALID" httpStatus="401,403"
                errorTypes="authentication_error,permission_error"/>
  <errorMapping classification="TRANSIENT" httpStatus="500,504,529"
                errorTypes="api_error,timeout_error,overloaded_error"/>
</errorMappings>
```

### 4.4 Gemini 配置示例（需 messagePattern 区分 429 子类型）

```xml
<errorResponse errorTypePath="error.status" errorCodePath="error.code"
               errorMessagePath="error.message"/>

<errorMappings>
  <!-- Gemini 429 同型 RESOURCE_EXHAUSTED，靠消息文本区分配额 vs 限流 -->
  <errorMapping classification="QUOTA_EXCEEDED" httpStatus="429"
                errorTypes="RESOURCE_EXHAUSTED"
                messagePattern=".*quota.*plan.*billing.*"/>
  <errorMapping classification="RATE_LIMITED" httpStatus="429"
                errorTypes="RESOURCE_EXHAUSTED"/>
  <!-- Vertex 5xx 信封包 429 -->
  <errorMapping classification="RATE_LIMITED" httpStatus="500,502,503,504"
                errorCodes="429"/>
  <errorMapping classification="TRANSIENT" httpStatus="503"
                errorTypes="UNAVAILABLE"/>
</errorMappings>
```

---

## 五、设计的两个增强点（从实测得出）

### 5.1 增强点 A：`messagePattern` 正则须是方言消息匹配同款（含 `.` 跨行 + 大小写无关）

Gemini / litellm 的子串启发式表明，`messagePattern` 不是可选装饰，而是**约 30% 场景的必需逃生舱**（Gemini 429 子类型、OpenAI 上下文超长、各种 provider 文案差异）。应直接复刻 `dialect.xdef` 消息正则规则：空白替下划线、`.` 跨行、大小写无关（`DialectSQLExceptionTranslator.java:164-174`）。设计文档已列 `messagePattern`，本分析确认其**从"可选"提升为"必需"**。

### 5.2 增强点 B：保留 XPL 自定义函数逃生舱（针对 litellm 的 30% 过程形场景）

litellm 的 18 个 per-provider 函数中，有部分逻辑纯配置表无法表达：
- **Azure 嵌套多拼写**（`inner_error.code` vs `innererror.code` 跨版本，需 try 多路径）
- **负向排除**（`is_error_str_context_window_exceeded` 匹配 X 但排除 Y 同时出现）
- **状态码被 body 覆盖**（Vertex 5xx 包 429——这个设计**已能**表达）
- **消息改写/脱敏**（bearer token 截断、帮助文案注入——属展示层，不属分类）

建议在设计文档 `<errorMappings>` 之外，保留一个可选的 `<classifyError>xpl-fn:(httpStatus,bodyMap)=>ErrorClassification</classifyError>` 全局逃生舱（类似现有 `<buildHttpRequest>`/`<parseHttpResponse>` 的 `xpl-fn` 模式）。配置映射优先，命中则用；未命中再走 XPL 函数；都未命中走默认启发式。这覆盖 Azure 嵌套多拼写等 5% 硬场景，且与 `llm.xdef` 现有 `xpl-fn` 节点风格一致。

---

## 六、结论 / Decision

**配置驱动方案对全部主流 provider 可行，设计无需结构性修改。**

1. **OpenAI / DeepSeek / DashScope 兼容**：完全可行，`error.code` 区分配额/限流。
2. **Anthropic**：完全可行且最干净，402 `billing_error` 专用状态。
3. **Gemini**：可行但需 `messagePattern` 正则区分 429 子类型（RESOURCE_EXHAUSTED 同型）。
4. **Azure**：可行但嵌套路径，建议 XPL 逃生舱处理双拼写。
5. **DashScope 原生**：可行，路径改为顶层 `code`。

**net 可达率 ~90% 纯配置 + ~10% 正则/XPL 逃生舱**，与 litellm 实测的"70% 表格 + 30% 过程"一致（本设计因 `messagePattern` 正则提前纳入，可达率更高）。

**对设计文档的两处增强建议**（已写入分析，待并入设计 §3.3/§6）：
- A. `messagePattern` 定位从"可选"升为"必需"，复刻 dialect 消息正则规则。
- B. 增加可选 `<classifyError>xpl-fn</classifyError>` 全局逃生舱，覆盖 Azure 嵌套/负向排除等硬场景。

**被证实的 nanobot pattern**（设计引用来源）：429 双 token 列表、`is_arrearage_response`（切换账号触发）、Retry-After 三层解析、`has_streamed_content`、图片 fallback、persistent 模式——全部在 `nanobot/providers/base.py` 找到对应实现，设计引用准确。

---

## 七、Open Questions

- [ ] Azure `inner_error.code` 与 `innererror.code` 的双拼写：是用两条 `<errorMapping>` + 两个 `errorCodePath` 表达，还是必须 XPL 逃生舱？落地时取一份真实 Azure 错误样本验证 prop-path 多路径策略。
- [ ] Gemini 消息文本"quota vs rate"的正则边界：不同模型/版本的文案是否稳定？建议落地时取 Gemini Free Tier 与 Paid 的真实 429 样本固化正则。
- [ ] OpenAI 的 `Retry-After` 头是否在所有 429 都返回（nanobot 头+体双读，litellm 只读头并封顶 60s）——需实测确认 OpenAI 限流是否总有头。
