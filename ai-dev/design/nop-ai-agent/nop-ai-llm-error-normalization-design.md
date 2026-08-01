# LLM 错误规范化与配额感知恢复设计

**日期**：2026-08-01（R1 审查修订）
**范围**：`nop-ai-core`（`llm.xdef` + `ChatServiceImpl`）+ `nop-ai-agent`（`LlmErrorClassifier` / `IRetryPolicy` / `IModelRouter`）+ `nop-http`（`ServerEventPublisher` 头传递）
**状态**：草案（待 plan 落地）
**前置文档**：`nop-ai-agent-llm-layer.md`（§6.5 回退错误分类、§7 重试策略、§7.6 Retry-After 多源解析均为本篇补齐的显式 Non-Goal）
**可行性验证**：`ai-dev/analysis/2026-08/2026-08-01-llm-error-mapping-feasibility-analysis.md`（主流 API 真实错误格式 + 4 个参考实现调研，证实配置方案 ~90% 可达）

---

## 一、设计结论

1. **把 provider 的异构错误响应规范化为少数固定分类**，规范化规则是**配置驱动**的，写在 `llm.xdef` 对应的 `{provider}.llm.xml` 里——直接复刻 `dialect.xdef` 的 `<errorCodes>` 模式（厂商码 → 标准码），而非每个 provider 写一段 Java。

2. **规范化在 `ChatServiceImpl` 内经 dialect 完成，结果放 `ChatResponse`**。ChatServiceImpl 的职责就是经 `ILlmDialect` 规范化输入输出——成功响应已这么做了（`parseResponse` 用 `contentPath`/`statusPath` 抽字段）。错误响应是另一种输出，同构处理：新增 dialect 错误解析（消费 `<errorMappings>`），把规范化分类填到 `ChatResponse` 新字段 `errorClassification`/`retryAfterMs`。**不用装饰器、不用新异常类型**——错误即输出，走同一通道。经审查核实：`callAsync` 默认 `stream=true`，流式路径 `ServerEventPublisher` 在非 2xx 已把响应体放进异常（`ARG_BODY`，`ServerEventPublisher.java:94-97`）；`ChatResponse` 已有 `error`/`errorCode`/`isSuccess()`/`ChatResponse.error()` 模式。详见 §3.4。

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

### 3.4 错误规范化在 `ChatServiceImpl` 内经 dialect 完成（与成功响应同构）

**架构裁定（用户定调）**：`ChatServiceImpl` 的职责就是**规范化输入输出**——它已经用 `ILlmDialect` 把 provider 特定的成功响应 JSON 规范化成统一的 `ChatResponse`（`parseResponse` 用 `contentPath`/`statusPath`/`errorPath` 等配置抽取字段）。错误响应是**另一种输出**，理应同构处理：经 dialect + `<errorMappings>` 配置规范化后，结果放在 `ChatResponse` 上。**不引入装饰器、不引入新的异常类型**。

```
ChatServiceImpl（基础 IChatService 实现，职责 = 经 dialect 规范化 I/O）
  ├─ 成功响应：dialect.parseResponse(body)  → ChatResponse(message, usage, ...)
  └─ 错误响应：dialect.parseErrorResponse(body,status,headers,config)
                                          → ChatResponse(error, errorCode, errorClassification, retryAfterMs, ...)
```

**为何放在 ChatServiceImpl 而非装饰器**（推翻前一版装饰器方案）：
1. **dialect 在 ChatServiceImpl 手里**——`parseResponse` 已持有 `LlmModel config`（含 `<errorMappings>`/`<errorResponse>`）。错误规范化需要的 provider 配置（`errorTypePath`/`errorMappings`）与成功解析同源，拆到装饰器反而要重新注入 dialect/config，凭空多一层。
2. **职责内聚**——ChatServiceImpl 既是"provider 差异屏蔽层"（`nop-ai-agent-llm-layer.md` §二定位），错误差异屏蔽正是其本职，不是"塞入额外逻辑"。
3. **复用既有模式**——`ChatResponse` 已有 `error`/`errorCode`/`isSuccess()`/`ChatResponse.error(...)` 工厂；`OpenAiDialect.parseResponse` 已对空 body 返回 `ChatResponse.error("NULL_RESPONSE",...)`；`<response errorPath>` 已能从 200 body 抽 error。错误即输出，沿用同一通道最自然。

**`ChatResponse` 增加规范化错误字段**（沿用既有 `error`/`errorCode`，新增分类信号）：
- `errorClassification: ErrorClassification`（核心——驱动恢复决策）
- `retryAfterMs: Long`（Retry-After 归一值）
- `httpStatus: Integer`（原始 HTTP 状态，诊断用）
- 既有 `error`/`errorCode` 填 provider 错误消息/code（`<errorResponse errorMessagePath/errorCodePath>` 抽取）

**改造点**（ChatServiceImpl，仍是基础调用的 I/O 规范化，不含重试/回退策略）：
- **非流式路径**：今天 `httpStatus != 200` 直接抛 `ERR_AI_SERVICE_HTTP_ERROR` 丢体（`ChatServiceImpl.java:121-127`）。改为读 `response.getBodyAsString()` + 头 → `dialect.parseErrorResponse(...)` → **返回**带 `errorClassification` 的错误 `ChatResponse`（不抛）。
- **流式路径**：`aggregateStreamToResponse` 的 `onError`（`ChatServiceImpl.java:306-308`）今天 `completeExceptionally`。改为解析 `ServerEventPublisher` 已放进异常的 `ARG_BODY`+`ARG_HTTP_STATUS`（`ServerEventPublisher.java:94-97`）→ `dialect.parseErrorResponse(...)` → **complete** 一个错误 `ChatResponse`（不 exceptionally）。
- **dialect 新增 `parseErrorResponse`**（或共享 helper）：按 `config.getErrorMappings()` 规则表 first-match 规范化（§3.3），未命中走默认启发式。这是纯输出规范化，与 `parseResponse` 对称。

**返回错误 ChatResponse 而非抛异常的收益**：统一了今天分裂的两条错误路径——`LlmCallCoordinator` 今天对"抛异常"走重试（`:122-174`）、对"`!isSuccess()`"走终止不重试（`:179-187`）。错误规范化进 ChatResponse 后，非 200 错误也成为带分类的 `ChatResponse`，重试循环可在 `!isSuccess()` 时读 `errorClassification` 做重试/回退决策（§3.5），不再因"是返回值"而放弃重试。

**契约约束**：
- 默认启发式与今日一致（429→RATE_LIMITED，5xx→TRANSIENT，4xx→NON_TRANSIENT），未配置 `<errorMappings>` 的 provider 零回归。
- **传输层错误仍抛异常**（连不上、超时——无 HTTP 响应无法构造 ChatResponse），由 `LlmErrorClassifier` 启发式处理。即：响应级错误（拿到 HTTP 响应）→ ChatResponse；传输级错误（没拿到响应）→ 异常。两者泾渭分明。
- 流式保护不变（§7.4）：已流出内容后的错误不重试。`parseErrorResponse` 只打分类，是否重试由策略层 + `hasStreamedContent` 决定。
- **`Retry-After` 头的前提条件**：流式路径需 `ServerEventPublisher`（nop-http）把响应头挂到异常（当前只读进局部变量 `:87` 未挂）。跨模块前置改动；未完成则流式仅支持 body 级 Retry-After（`<errorResponse retryAfterPath>`），头级为已知缺口。
- **HTTP 200 带 error body**（R2 审查 N2）：本方案天然更易覆盖——`<response errorPath>` 已抽 200-body 的 error，`parseResponse` 可在 `errorPath` 非空时也调规范化逻辑填充 `errorClassification`（比装饰器/异常方案更顺，因为 200 本就走 `parseResponse`）。列为 successor 增强。

### 3.5 上层消费：重试循环读 `ChatResponse.errorClassification`

重试循环（`LlmCallCoordinator.doLlmCallWithRetry`）消费方式调整——错误分类现在在 `ChatResponse` 上而非异常里：

```
doLlmCallWithRetry:
  try {
    response = callChatWithTimeout(request)
    if (!response.isSuccess()):
       classification = response.getErrorClassification()   // 底层已规范化
       retryCtx = RetryContext(attempt, response, classification, hasStreamedContent, response.getRetryAfterMs())
       outcome = retryPolicy.shouldRetry(retryCtx)
       → RETRY / FALLBACK(账号链) / STOP  （按 §3.2 决策表）
  } catch (transport ex) {                                   // 网络/超时，无响应
     classification = LlmErrorClassifier.classify(ex)        // 启发式
     → 同上重试决策
  }
```

- 今天 `!isSuccess()` 是终止路径（`:179-187` 不重试）。改造后 `!isSuccess()` 进入重试决策——读 `errorClassification` 决定 `QUOTA_EXCEEDED`/`AUTH_INVALID` 切账号、`RATE_LIMITED` 按 `retryAfterMs` 等待重试。
- `RetryContext` 增加 `retryAfterMs`（从 `ChatResponse` 或异常取）；`StandardRetryPolicy` 的 `RATE_LIMITED` 用 Retry-After 作 floor（§3.7）。
- 传输层异常仍走 `LlmErrorClassifier.classify(ex)` 启发式（向后兼容）。

`RetryContext` 增加 `retryAfterMs`（从 `ChatResponse.getRetryAfterMs()` 取；今天 `RetryContext` 无此字段，是真实接口变更）。

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

底层（`ChatServiceImpl` 经 dialect）把 1~3 归一为 `retryAfterMs` 放进 `ChatResponse`，策略层只消费这一个值。

**`retryAfterMs` 与 full-jitter 的关系（必须显式裁定）**：`StandardRetryPolicy` 今天对**每个** RETRY 都加 full jitter（`computeBackoff`，`StandardRetryPolicy.java:144-166`）。若对 `RATE_LIMITED` 原样用 `retryAfterMs`，会丢掉 thundering-herd 抑制；若把 jitter 套在 `retryAfterMs` 上（如 `uniform(0, retryAfterMs)`），等待可能**低于服务器要求**，导致立即再被拒。

**裁定：`Retry-After` 作为下限（floor）**：`RATE_LIMITED` 的 `delay = retryAfterMs + uniform(0, jitterCap)`，**永不低于** `retryAfterMs`。这刻意让 `RATE_LIMITED` 偏离纯 full-jitter 公式——理由：遵守服务器明确要求的等待，重要性高于 herd 抑制（配额/限流信号下，服务器已告知精确等待，不应抢跑）。`TRANSIENT` 的退避仍用纯 full-jitter（无服务器提示）。两者策略不同须文档化。

### 3.8 分层归属与依赖方向

```
Layer 1 (nop-ai-core):  llm.xdef 配置 + ErrorClassification(纯词汇)
                        ChatServiceImpl（I/O 规范化：成功经 parseResponse，错误经 parseErrorResponse，结果都在 ChatResponse）
                        ILlmDialect（新增 parseErrorResponse，消费 <errorMappings>）
                                ↓ 返回带 errorClassification 的 ChatResponse
Layer 3 (nop-ai-agent): LlmCallCoordinator 重试循环读 ChatResponse.errorClassification（!isSuccess() 进入重试决策）
                        + IRetryPolicy(恢复决策) + 账号链消费；传输异常仍走 LlmErrorClassifier 启发式
Layer 0 (nop-http):     ServerEventPublisher 头传递（前置改动，见 §3.4）
```

`ErrorClassification` 已在 `io.nop.ai.core.model`（纯词汇）；agent 侧 `reliability.ErrorClassification` 已 `@Deprecated` 桥接。错误规范化逻辑（dialect.parseErrorResponse）归属 `nop-ai-core`（与成功解析同模块同层），依赖方向（agent → core）不变。`ChatResponse` 在 `nop-ai-api`，新增字段对 agent 层透明可见。

---

## 四、拒绝了什么

### 4.0 拒绝：装饰器 / 新异常类型承载错误规范化

**方案**：`ErrorNormalizingChatService implements IChatService` 装饰器包装 `ChatServiceImpl`，捕获原始 HTTP 错误 → 规范化 → 抛 `LlmProviderException`。

**拒绝理由**：① 错误规范化需要的 `ILlmDialect` + `LlmModel config`（含 `<errorMappings>`）已在 `ChatServiceImpl` 手里（`parseResponse` 已持有），拆到装饰器要重新注入 dialect/config，凭空多一层；② ChatServiceImpl 的职责本就是"经 dialect 规范化 I/O"（`nop-ai-agent-llm-layer.md` §二），错误差异屏蔽是其本职；③ `ChatResponse` 已有 `error`/`errorCode`/`isSuccess()` 模式，`parseResponse` 已对空 body 返回 `ChatResponse.error(...)`，错误即输出走同一通道最自然，无需新异常类型。错误规范化留在 ChatServiceImpl 经 dialect 完成、结果放 ChatResponse，比装饰器/异常方案更内聚。

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

2. **`ErrorClassification` 已上移**（已完成）：core 中的枚举是纯词汇（`io.nop.ai.core.model.ErrorClassification`，不 import Layer-3 类型）；agent 层 `reliability.ErrorClassification` 已降级为 `@Deprecated` 桥接。后续接线全量回归 `reliability` 包测试。

3. **`<errorMappings>` 顺序与 first-match-wins**：实现采用 `xdef:key-attr="id"`（与 `dialect.xdef` `<errorCodes key-attr="name">` 同款），id 用于 `x:extends` 合并时按 id 区分条目（子配置 replaceChild 在原位置替换、新增 id 追加末尾），合并后顺序保持，故 first-match-wins 仍成立。须有测试断言两条同 `classification` 规则按合并后位置先后分别命中。

4. **流式保护不被破坏**：已流出内容后的错误不触发 RETRY/FALLBACK（§7.4 不变）。装饰器只打分类，重试与否仍由策略层 + `hasStreamedContent` 决定。

5. **流式路径 `Retry-After` 头缺口**：依赖 `nop-http` `ServerEventPublisher` 挂响应头到异常的前置改动。若该改动未完成，装饰器在流式路径仅支持 body 级 `Retry-After`，须文档化为已知缺口。

6. **装饰器可复用覆盖 `DefaultAiChatService`**：`ErrorNormalizingChatService` 包装任意 `IChatService` 实现，`ChatServiceImpl` 与 `DefaultAiChatService` 套同一装饰器即得规范化，无需重复逻辑（装饰器方案相对"改 `ChatServiceImpl` 内部"的额外收益）。

7. **`<errorResponse>` / `<errorMappings>` 是可选配置节**，不破坏现有 `{provider}.llm.xml` 加载（已落地：default/claude/gemini/azure/ollama 均已配置）。

8. **`StandardRetryPolicy` 行为变更（R2 审查 N3）**：今天 `StandardRetryPolicy.shouldRetry` 对 `QUOTA_EXCEEDED`/`AUTH_INVALID` 返回 STOP（`StandardRetryPolicy.java:119-122`，因这两类不在 `TRANSIENT`/`RATE_LIMITED` 白名单内），且标准策略从不产生 `FALLBACK`——故 §3.6 的"按分类路由账号链"今日**不可达**。落地时 `StandardRetryPolicy` 必须改为：`QUOTA_EXCEEDED`/`AUTH_INVALID` → 返回 FALLBACK（交由重试循环按 §3.6 方案 b 路由账号链），`TRANSIENT` 保持 RETRY。此策略变更是 §3.6 路由的前置条件，须有测试固化且验证不破坏零回归红线（依赖 §6.1 两个不变量）。

9. **账号链 fail-loud**（Minimum Rules #24）：链耗尽抛异常不静默，须有测试固化。
