# nop-ai-gateway — AI 网关与透明账号切换（failover）

## 功能概览

`nop-ai-gateway` 提供 **AI 网关能力**：AI 请求的格式转换（`AiDialectBackendMessageConverter`）、网关形态的路由拦截，以及 **透明账号切换（account failover）**——访问 AI 后端失败时（限额/限流 429、连接中断/超时、余额不足 402、key 失效 401）自动切换到下一个可用账号（可同时切换模型与协议风格），对前端透明。

透明切换（failover）能力是 W1-W8 生产化收口后的完整能力，包含：

- **两种部署形态共享同一套能力**：nop-gateway 独立网关（拦截器链形态）与本地 `IChatService` 适配器（伪装本机接口形态）。账号链/熔断/错误分类/并发限流/模型类路由/选择策略/指标全部复用同一套机制。
- **主动 + 被动触发**：请求发出前检查账号并发上限（`concurrencyLimit`）；失败后按错误分类决定切换/重试/失败。
- **流式 failover**：首段缓冲窗口（前 N 元素 / T 毫秒）内失败 → 从头重订阅到新账号；已转发数据后失败 → 断流报错（无法保证透明）。
- **模型类路由组 + 动态选择策略**：模型按"级别/类"组织，类内候选集按可插拔策略选择（默认健康度 + 并发感知 + 声明序；可选规则策略）。
- **可观测性指标**：micrometer 指标族 `nop.ai.gateway.failover.*`。

> 需求规格（权威）：设计文档 02-account-failover-requirement.md（§3 行为契约、§3.6 指标契约）——位于 ai-dev/design/nop-ai-gateway/ 目录（platform-dev 文档，非 docs-for-ai 路由）。

## 两种部署形态

| 形态 | 载体 | 适用场景 |
|------|------|---------|
| 形态 A：网关拦截器 | `AiGatewayFailoverInterceptor`（`IGatewayInterceptor`）+ nop-gateway 缓冲层（`BufferedStreamingPublisher`） | 已有/需要 nop-gateway 独立网关，走拦截器链处理 HTTP 请求 |
| 形态 B：本地适配器 | `ChatServiceFailoverAdapter`（`IChatService` 实现，包装 `nopChatService`） | 无真实网关，业务代码直接调用 `IChatService` |

两种形态共享进程级单例 bean：`nopFailoverCircuitBreaker`（`ThresholdBreaker`，熔断三态 + 冷却 + 探活，粒度 `provider:model`）与 `nopFailoverConcurrencyRegistry`（每账号进程内并发计数）。两形态的熔断/并发状态互相累计（同一进程共享）。

**复用优先**：账号链 `<accounts>`、熔断、双源错误分类、凭证链（`accountKey > credentialId > resolveApiKey`）全部复用 nop-ai-core 既有机制，failover 只新增缺口（流式重订阅、两种形态、并发限流、模型类路由、动态选择、指标）。

## 形态 B：本地 `IChatService` 适配器

bean 已在 `nop-ai-gateway` 的 `ai-gateway-defaults.beans.xml` 注册（模块自动装配），无需额外注册：

```xml
<!-- ai-gateway-defaults.beans.xml（模块内置，无需手写） -->
<bean id="nopChatServiceFailoverAdapter"
      class="io.nop.ai.gateway.failover.ChatServiceFailoverAdapter"
      ioc:type="io.nop.ai.api.chat.IChatService">
    <property name="delegate"><ref bean="nopChatService"/></property>
    <property name="breaker"><ref bean="nopFailoverCircuitBreaker"/></property>
    <property name="registry"><ref bean="nopFailoverConcurrencyRegistry"/></property>
    <property name="metrics"><ref bean="nopAiFailoverMetrics"/></property>
    <!-- strategy 省略 = null = DefaultSelectionStrategy；需规则策略时注入 nopAiRuleBasedSelectionStrategy -->
    <property name="bufferSize"  value="@cfg:nop.ai.gateway.failover.buffer-size|10"/>
    <property name="bufferTimeMs" value="@cfg:nop.ai.gateway.failover.buffer-time-ms|1000"/>
    <property name="retryBudget" value="@cfg:nop.ai.gateway.failover.retry-budget|2"/>
</bean>
```

**部署启用方式（W6 D4 裁定）**：`nopChatServiceFailoverAdapter` 是**非默认** bean——`ioc:default` 仍保留在 nop-ai-core 的 `nopChatService` 上，既有 by-type `IChatService` 消费者（如 nop-wf-ai `WfAiHelper`）保持零回归。**部署方需显式把注入目标切换到 `nopChatServiceFailoverAdapter`**（如在自己 app 的 beans.xml 中把 `IChatService` 类型的注入点 ref 到该 bean id），适配器不会自动覆盖默认注入。

**配置键**：

| 配置键 | 默认值 | 语义 |
|--------|--------|------|
| `nop.ai.gateway.failover.buffer-size` | `10` | 流式首段缓冲窗口元素数 N（>=1；先到者越窗） |
| `nop.ai.gateway.failover.buffer-time-ms` | `1000` | 流式首段缓冲窗口时长 T 毫秒（先到者越窗） |
| `nop.ai.gateway.failover.retry-budget` | `2` | 重试预算（初始 attempt 之后最多重试/重订阅次数）；总延迟上限默认不设（仅次数预算） |

## 形态 A：nop-gateway 拦截器

### bean 注册

```xml
<!-- ai-gateway-defaults.beans.xml（模块内置，无需手写） -->
<bean id="nopAiGatewayFailoverInterceptor"
      class="io.nop.ai.gateway.failover.AiGatewayFailoverInterceptor"
      ioc:type="io.nop.gateway.core.interceptor.IGatewayInterceptor">
    <property name="breaker"><ref bean="nopFailoverCircuitBreaker"/></property>
    <property name="registry"><ref bean="nopFailoverConcurrencyRegistry"/></property>
    <property name="converter"><ref bean="nopBackendMessageConverter_AI_DIALECT"/></property>
    <property name="metrics"><ref bean="nopAiFailoverMetrics"/></property>
    <property name="retryBudget" value="@cfg:nop.ai.gateway.failover.retry-budget|2"/>
    <property name="primaryProvider" value="@cfg:nop.ai.gateway.failover.primary-provider|"/>
</bean>
```

### 挂载契约（M-7）：bean 注册 ≠ 挂载

**仅在 beans.xml 注册 bean 不会让任何路由生效**。部署必须在自己的 `gateway.xml` 中把拦截器 bean **引用到路由拦截器链**（`GatewayInterceptorModel.getOrCreateInterceptor` 按 bean 名解析）：

```xml
<gateway x:schema="/nop/schema/gateway.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <interceptors>
        <interceptor id="ai-failover" bean="nopAiGatewayFailoverInterceptor">
            <match path="/{*path}"/>
        </interceptor>
    </interceptors>
    <routes>
        <route id="chat-stream" unwrapResponse="false">
            <match path="/chat/stream" httpMethod="POST"/>
            <invoke url="${'https://llm.example.com' + '/v1/chat/completions'}"/>
            <streaming enabled="true" contentType="text/event-stream"
                       bufferEnabled="true" bufferSize="10" bufferTimeMs="1000"/>
        </route>
    </routes>
</gateway>
```

流式 failover 路由前置条件：

- **F1**：流式路由不得配置 `requestMapping`/`onRequest` xpl（请求实例一致性）。
- **B-18**：流式路由不得配置会吞错的 route 级 `onError`（错误必须透传到拦截器链）。

### 缓冲窗口（gateway.xdef `streaming`）

| 属性 | 默认值 | 语义 |
|------|--------|------|
| `streaming.enabled` | — | 流式开关（非流式路由不需要缓冲配置） |
| `streaming.bufferEnabled` | `false` | 首段缓冲开关；缺省 false = 既有零缓冲直通（零回归）。**注意：缓冲关闭不解除 failover 接线**——生命周期计数/重订阅仍生效（计数与缓冲解耦） |
| `streaming.bufferSize` | `10` | 首段缓冲窗口元素数 N（>=1；先到者越窗） |
| `streaming.bufferTimeMs` | `1000` | 首段缓冲窗口时长 T 毫秒（先到者越窗） |

窗口内（尚未向客户端转发任何数据）失败 → 分类 → 切换/重订阅；窗口外（已转发）失败 → 断流报错（无法保证透明）。

### 配置键

| 配置键 | 默认值 | 语义 |
|--------|--------|------|
| `nop.ai.gateway.failover.retry-budget` | `2` | 重试预算（初始之后最多重订阅/重发次数） |
| `nop.ai.gateway.failover.primary-provider` | 空（回退 `nop.ai.service.default-llm`） | provider 链扩展的 primary 键 |

## 模型类与账号配置

### 模型类路由组（`model-class.xdef`）

模型按"级别/类"组织：每个模型类 = 逻辑路由组 + 候选集（备选模型 + 账号组合）。请求携带的 model 归属某类后，在该类候选集内游走。**opt-in**：配置文件 `_default.model-class.xml`（VFS 路径 nop/ai/llm/ 下，见 `model-class.xdef` 注释）；缺省（无文件）= 无路由组 = 零回归（沿用既有单 provider 行为）。请求 model 未归属任何类同样解析为"无路由组"（零回归）。

示例（示意片段）：

```xml
<modelClass x:schema="/nop/schema/ai/model-class.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <modelClasses>
        <!-- members = 归属本类的 model 名清单（csv，全局匹配，首个声明命中） -->
        <modelClass id="tier-deepseek" members="deepseek-v4,deepseek-v4-lite">
            <candidates>
                <!-- provider 必填；model 省略 = provider defaultModel；
                     accountRef 省略 = 主账号 + 该 provider 有序账号链展开 -->
                <candidate provider="deepseek" model="deepseek-v4"/>
                <candidate provider="volcengine" model="doubao-pro"/>
                <candidate provider="deepseek" model="deepseek-v4" accountRef="backup-1"/>
            </candidates>
        </modelClass>
    </modelClasses>
</modelClass>
```

候选展开语义（`LlmConfigHelper`）：`accountRef` 未配置 → "主账号 + 该 provider 有序账号链"（主账号在前）；`accountRef` 配置 → 只展开该账号（未知 id 解析期 fail-fast）；未知 provider 解析期 fail-fast（不静默吞）。

### 账号与并发上限（`llm.xdef`）

账号沿用 `{provider}.llm.xml` 的 `<accounts>` 结构（`id`/`apiKey`/`baseUrl`/`quotaLimit`/`renewAt`/`concurrencyLimit`），主账号 = `nop.ai.llm.{provider}.api-key` 配置变量（或 secret 文件，或 `credentialId` 凭证库，凭证链 `accountKey > credentialId > resolveApiKey`）。备用账号 `apiKey` 直接下沉为 `accountKey`（凭证链不作用于备用账号）。

`concurrencyLimit`（in-flight 并发上限）层级语义：账号级未配置 → 回退 provider 级缺省（`<llm concurrencyLimit="...">` 根属性）；均未配置 = 不限制；显式 0/负数 = 显式不限制（不回退）。**注意与 `rateLimit` 的区别**：`rateLimit` 是每秒 QPS（排队语义），`concurrencyLimit` 是 in-flight 计数（**跳过**语义——超限换账号），两者并存不互斥。

示例（示意片段）：

```xml
<llm x:schema="/nop/schema/ai/llm.xdef" xmlns:x="/nop/schema/xdsl.xdef"
     x:extends="default.llm.xml" apiStyle="openai"
     defaultModel="deepseek-v4" concurrencyLimit="8">
    <baseUrl>https://api.deepseek.com</baseUrl>
    <chatUrl>/v1/chat/completions</chatUrl>
    <accounts>
        <account id="backup-1" apiKey="@sec:..." concurrencyLimit="4"/>
        <account id="backup-2" apiKey="@sec:..." baseUrl="https://proxy.example.com"
                 concurrencyLimit="2"/>
    </accounts>
</llm>
```

### 行为契约摘要（错误分类 → 动作）

失败分类复用双源归一化（响应级 `parseErrorResponse` + `<errorMappings>`；传输异常级 `LlmErrorClassifier`）：

| 分类 | 典型来源 | 动作 |
|------|---------|------|
| QUOTA_EXCEEDED | 429 insufficient_quota / 402 billing | 账号链切换 + 熔断记账 + 消耗预算 |
| AUTH_INVALID | 401/403 key 失效 | 账号链切换 + 熔断记账 + 消耗预算 |
| RATE_LIMITED | 429 rate_limit_exceeded | 账号链切换（语义扩展，见下） |
| TRANSIENT | 5xx / 连接中断 / 超时 | 账号链切换（语义扩展，见下） |
| NON_TRANSIENT | 400 等 | **不切换**直接失败（不消耗预算） |
| CACHE_STATE_LOST | 经 `<errorMappings>` 配置可达（如 409） | **原地重发同一候选一次**（不切换账号，消耗预算，不记熔断） |

> **语义扩展（产品决策 Q5）**：RATE_LIMITED/TRANSIENT 在网关/适配器语义下同样触发账号切换（与 agent 引擎 `LlmCallCoordinator` 仅 QUOTA/AUTH 走账号链不同）。

**并发语义**：请求发出前检查 + acquire 后复查，保证单进程内任一账号不超并发；**主动切换（并发饱和跳过）不记熔断、不消耗重试预算**；类内全部候选并发/健康饱和 → **fail-loud**（`ERR_AI_MODEL_CLASS_SATURATED`，不排队不无限等待）。熔断粒度 `provider:model`，同模型类内多账号失败跨账号累计。

**透明边界（§3.5）**：前端只可能感知（1）延迟增加（重试耗时）、（2）流式首段缓冲的首包延迟、（3）全部账号失败/流已转发后失败的最终错误或流中断。不承诺 token 级幂等/请求语义变换/失败补偿。

## 规则选择策略（可选）

选择策略 = `ISelectionStrategy` 接口（nop-ai-core `io.nop.ai.core.routing`）。默认策略 = `DefaultSelectionStrategy`（健康度 + 并发感知 + 声明序），无需配置。规则策略 `RuleBasedSelectionStrategy` 为可插拔替换实现（XLang 规则 DSL）。

### IoC 绑定

```xml
<!-- ai-gateway-defaults.beans.xml（模块内置） -->
<bean id="nopAiRuleBasedSelectionStrategy"
      class="io.nop.ai.core.routing.RuleBasedSelectionStrategy"
      ioc:type="io.nop.ai.core.routing.ISelectionStrategy">
    <property name="ruleManager">
        <ref bean="nopRuleManager" ioc:optional="true"/>
    </property>
    <!-- 规则身份（规则文件位于 /nop/rule 下，见下）；空 = 未配置 → 首用 fail-fast -->
    <property name="ruleName" value="@cfg:nop.ai.gateway.rule-selection.rule-name|"/>
    <!-- ruleVersion 省略 = null = 该规则最新版本 -->
</bean>
```

- **部署 opt-in**：`ioc:default` 仍归默认策略——部署方把 `ChatServiceFailoverAdapter`/`AiGatewayFailoverInterceptor` 的 `strategy` 属性 ref 到 `nopAiRuleBasedSelectionStrategy` 即启用。
- `ruleManager` ref 为 `ioc:optional`：未部署 nop-rule 的容器可启动，首用 fail-fast（`ERR_AI_AGENT_INVALID_ARG`，不静默回退）。
- 规则文件路径约定：`/nop/rule/rule-selection/{ruleName}/v{version}.rule.xml`（`resolve-rule` 版本化解析；version 省略 = 最新）。

### 规则输入/输出契约

- **输入**（固定，不含账号敏感信息）：`model`、`provider`、`candidates`（候选列表，**不含 accountKey**——备用账号 apiKey 明文安全裁定）、`health`（健康视图，键 = 候选 index）、`attempted`（本轮已尝试候选 index 集）。
- **输出**：`selectedIndex`（int，**不得声明 mandatory**——未命中也校验输出；越界/命中已尝试 → `ERR_AI_AGENT_INVALID_ARG` fail-loud）；未命中/无输出 → null（调用方 fail-loud）。
- **谓词编写模式**：XML 规则文件一律用 **computed 输入**派生辅助变量做条件（`<expr>` filter op 在 XML 中不可用——body 文本不编译进 value attr，执行期实证）。
- 单例 stateless：每 select 新建 rule runtime，规则可热更新（规则文件按版本解析）。

## 指标清单（OBS-01 契约摘要）

指标经平台 micrometer 设施暴露，命名族 `nop.ai.gateway.failover.*`，缺省恒定启用、零行为影响（观测面独立于控制面）。bean `nopAiFailoverMetrics`（`ioc:default="true"`）恒注册——部署方不可移除（容器启动 fail-fast），可注册自定义 `IFailoverMetrics` 实现覆盖。完整契约（维度/单位/触发事件/近似语义）见需求文档 §3.6。

| 类别 | 指标名 |
|------|--------|
| 切换 | `nop.ai.gateway.failover.switch.total` |
| 重订阅 | `nop.ai.gateway.failover.resubscribe.total` |
| 熔断迁移 | `nop.ai.gateway.failover.circuit-transition.total`（**近似观测**——`ThresholdBreaker.getState` 无锁读，并发交错下可能重复计数/错误归因） |
| 冷却期 | `nop.ai.gateway.failover.cooldown.total`（type ∈ started/rejected/probe-rejected） |
| 成功率 | `nop.ai.gateway.failover.request-success.total` / `request-failure.total` |
| 延迟 | `nop.ai.gateway.failover.request.duration`（Timer，outcome ∈ success/failure） |
| 饱和 | `nop.ai.gateway.failover.saturation.total` |
| 并发配对 | `nop.ai.gateway.failover.concurrency-acquire.total` / `concurrency-release.total` |
| 接管 | `nop.ai.gateway.failover.takeover.total`（网关流式首次候选下沉） |
| 降级终止 | `nop.ai.gateway.failover.degraded.total`（流式 NON_TRANSIENT 降级响应） |
| 反向转换 | `nop.ai.gateway.failover.stream-element.total`（onStreamElement per-attempt 转换） |

维度约定：`provider`/`model`/`account`（主账号 accountKey null → 空串维度）/`model-class`/`to-state`/`type`/`outcome`。

## 源码锚点

| 组件 | 路径 |
|------|------|
| 本地适配器 | `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/ChatServiceFailoverAdapter.java` |
| 流式重订阅 | `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/FailoverStreamFlow.java` |
| 网关拦截器 | `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/AiGatewayFailoverInterceptor.java` |
| 重执行回调 / 生命周期监听器 | `GatewayStreamingRetryCallback.java` / `GatewayStreamingLifecycleListener.java` |
| 指标 | `IFailoverMetrics.java` / `FailoverMetricsImpl.java` |
| 缓冲/重订阅层（nop-gateway） | `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/BufferedStreamingPublisher.java` |
| 模型类路由组 / 选择策略（nop-ai-core） | `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/routing/ModelClassRouter.java` / `ISelectionStrategy.java` / `RuleBasedSelectionStrategy.java` |
| 熔断/错误分类（nop-ai-core） | `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/reliability/ThresholdBreaker.java` / `LlmErrorClassifier.java` |
| 配置面 xdef | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/model-class.xdef` / `llm.xdef` / `gateway.xdef` |

## 相关文档

- `../reusable-modules-overview.md`
- `../03-modules/nop-ai.md`
- 需求规格与架构：02-account-failover-requirement.md、01-architecture.md（ai-dev/design/nop-ai-gateway/ 目录，platform-dev 文档）
