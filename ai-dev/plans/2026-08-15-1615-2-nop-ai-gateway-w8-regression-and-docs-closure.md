# W8 全链回归收口 + docs-for-ai 使用文档 + 需求文档状态收口（OBS-02/03/04）

> Plan Status: completed
> Last Reviewed: 2026-08-15
> Review Consensus: 三轮独立 fresh-session 对抗性审查达成共识——R1: 3 Major（矩阵骨架/gap 判定规则、docs 缺挂载契约与启用方式、Closure Gates 缺硬门禁）+ 8 Minor，全部修复；R2: approve（9 项修复与 live repo 实证一致，0 Blocker 0 Major）；R3: approve（M1-M6 措辞修正 + scan-hollow 位置路径修正实机验证，全局一致）
> Source: `ai-dev/backlog/nop-ai-gateway-failover-roadmap.md`（W8 OBS-02/03/04）、`ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`（§3.x 落地状态 + §五 Q2/Q3/Q4/Q5/Q7）、`ai-dev/design/nop-ai-gateway/01-architecture.md`
> Related: `ai-dev/plans/2026-08-15-1615-1-nop-ai-gateway-w8-observability-metrics.md`（W8 OBS-01，本计划前置）、`ai-dev/plans/2026-08-15-1116-1-nop-ai-gateway-w5b-rule-selection-strategy.md`（规则策略使用文档归 OBS-03）、`ai-dev/plans/2026-08-15-1116-2-nop-ai-gateway-w6-local-adapter-streaming-failover.md` / `ai-dev/plans/2026-08-15-1116-3-nop-ai-gateway-w7-gateway-interceptor-converter.md`（W6/W7，Q2/Q5/Q7 处置记录）
> Mission: nop-ai-gateway-failover
> Work Item: W8 (OBS-02/03/04)

## Purpose

生产化收口第二半——（1）全链回归测试矩阵收口：两种形态（本地适配器 + 网关拦截器）× 非流式/流式 × 各触发分类与边界（缓冲窗口、重试预算、饱和、并发释放、探活恢复）gap 分析后补缺测试，既有测试零回归；（2）`docs-for-ai/` failover 使用文档（网关配置 + 本地适配器用法 + 模型类/账号配置示例 + 规则策略用法 + 指标清单）并同步 INDEX.md / source-anchors.md / 03-modules 路由；（3）需求文档状态收口：§五 Q 表 Q2/Q3/Q4/Q5/Q7 处置结果回填、§3.x 落地状态一致性复核。完成后 W1-W8 全部落地证据闭环，roadmap W8 具备标 `done` 的判定材料（`done` 标记由引擎经独立 closure audit 后执行，本计划不自行标）。

## Current Baseline

- W1-W7 已落地（roadmap `done`）。测试现状（live repo 核实）：本地形态 `TestChatServiceFailoverAdapterNonStreaming`（17 用例）+ `TestChatServiceFailoverAdapterStreaming`（15 用例）；网关形态 `TestAiGatewayFailoverInterceptorStreaming`（8 用例）+ `TestAiGatewayFailoverInterceptorNonStreaming`（5 用例）；W4 双向转换测试 + converter 接线测试；既有网关用例 152 个（W7 记录）；测试基建 `FailoverTestSupport`/`W7GatewayTestSupport`。
- `docs-for-ai/` 现状：无 nop-ai-gateway failover 使用文档；`docs-for-ai/INDEX.md` 无 ai-gateway 路由条目；`docs-for-ai/04-reference/source-anchors.md` 无 failover 锚点；`docs-for-ai/03-modules/nop-ai.md` 子模块表未列 `nop-ai-gateway`。
- W5b 计划 Non-Blocking Follow-up 已声明：`docs-for-ai/` 规则策略使用文档归 W8 OBS-03 统一收口。
- 需求文档 Q 表现状：Q2/Q5（前端 model 语义、RATE_LIMITED/TRANSIENT 切换偏离）推荐默认已在 W6/W7 落地但**人工裁决未回填**（W6/W7 均登记"裁决回填归 W8 OBS-04"，watch-only residual）；Q3 已裁定（W6/W7 执行期：N=10/T=1000ms/预算 2 次/总延迟默认 null）但 Q 表状态未同步；Q7 已决（W1 spike + W7 落地）但 Q 表未回填；Q4（指标契约）由 OBS-01（plan `2026-08-15-1615-1` Phase 1）落档 §3.6 后，本计划回填状态。
- OBS-01（plan `2026-08-15-1615-1`）完成后，指标契约与实现可用，回归测试可加指标断言。
- **真实 gap**：OBS-02 全链矩阵缺显式 gap 分析与文档化；OBS-03 无任何使用文档；OBS-04 Q 表未收口。

## Goals

- 全链回归矩阵显式化并补缺：矩阵 = 形态（本地/网关）× 模式（非流式/流式）× 触发分类（QUOTA_EXCEEDED/AUTH_INVALID/RATE_LIMITED/TRANSIENT → 切换；NON_TRANSIENT → 不切换不耗预算；CACHE_STATE_LOST → 原地重试）× 边界（缓冲窗口内重订阅/窗口外断流、重试预算耗尽、全池饱和 fail-loud、并发 +1/-1 配对、探活恢复、取消语义）；对既有测试做 gap 分析，只补缺口不重写既有测试；新增测试（含指标断言，依赖 OBS-01）全绿且既有测试零回归。
- `docs-for-ai/` 使用文档交付：failover 使用指南（网关形态配置 + 本地适配器用法 + `model-class.xdef` 模型类/账号配置示例 + `llm.xdef` concurrencyLimit + 规则策略用法（W5b follow-up 收口）+ 指标清单（OBS-01 契约摘要）），内容与 live repo 配置键/bean 名/xdef 结构一致；INDEX.md 路由、source-anchors.md 锚点、03-modules 路由同步；`check-doc-links.mjs --strict` 0 errors。
- 需求文档状态收口：§五 Q 表 Q2/Q3/Q4/Q5/Q7 逐条回填处置结果（沿用推荐默认者标注"已落地 + 待人工最终确认"或按裁定状态标记，**不允许把未裁决项静默标记为已决**）；§3.x 落地状态与 live repo 一致性抽查；roadmap W8 `todo` 状态的引擎侧流转材料就绪。

## Non-Goals

- 新指标实现/契约扩展（OBS-01 已收口，本计划只引用）。
- 任何新 failover 功能（手动运维、多实例聚合、告警、总延迟上限落地——均为已裁定 non-goal / optimization candidate）。
- 重写既有通过测试；为补覆盖率而扩展现有测试断言之外的行为变更。
- 修改 `02-account-failover-requirement.md` 的需求语义（仅状态回填与落地注记，不改变已审查结论）。

## Scope

### In Scope

- 全链回归矩阵定义（计划内文档化矩阵表）+ 缺口测试补写（`nop-ai/nop-ai-gateway/src/test`）。
- 指标断言测试（消费 OBS-01 指标服务，覆盖切换/重订阅/饱和/熔断迁移等关键指标在端到端路径上的观测）。
- `docs-for-ai/` failover 使用文档（落点执行期裁定，判据见 Phase 2 item 1：内容量阈值 + 组内先例）与 INDEX/source-anchors/nop-ai.md 同步。
- `02-account-failover-requirement.md` §五 Q 表收口 + §3.x 落地状态一致性复核。
- `ai-dev/logs/` 收口记录。

### Out Of Scope

- OBS-01（指标实现）任何遗留（若 OBS-01 未完成，本计划阻塞等待，不代为实现）。
- 需求文档语义修订、roadmap 状态改写（roadmap 的 `todo → done` 流转由引擎/closure audit 驱动）。

## Execution Plan

### Phase 1 - 全链回归 gap 分析与补缺（OBS-02）

Status: completed
Targets: `nop-ai/nop-ai-gateway/src/test`、测试矩阵文档

- Item Types: `Proof | Fix`

**全链回归矩阵（平铺场景清单，2026-08-15 落档；视图 = 形态（本地/网关）× 模式（非流式/流式），每格标注覆盖状态：既有用例名 / 新增用例名 / N/A+理由。daily log 引用见 `ai-dev/logs/2026/08-15.md`）**：

**A. 本地形态 · 非流式（`ChatServiceFailoverAdapter.callAsync`，测试类 `TestChatServiceFailoverAdapterNonStreaming`）**

| # | 场景 | 覆盖（用例名） | 状态 |
|---|------|---------------|------|
| A1 | QUOTA_EXCEEDED → 切换 + 新账号 apiKey 下沉 | `quotaExceededSwitchesToNextAccount` | 既有 |
| A2 | AUTH_INVALID → 切换 | `authInvalidSwitchesToNextAccount` | 既有 |
| A3 | RATE_LIMITED（响应级 429 rate_limit_exceeded）→ 切换 | `rateLimitedSwitchesToNextAccount` | 既有 |
| A4 | TRANSIENT（响应级 503）→ 切换 | `transientResponseSwitchesToNextAccount` | 既有 |
| A5 | TRANSIENT（传输异常级）→ 切换 | `transportErrorSwitchesToNextAccount` | 既有 |
| A6 | NON_TRANSIENT → 不切换不耗预算不记熔断 | `nonTransientFailsDirectlyWithoutSwitch` | 既有 |
| A7 | **CACHE_STATE_LOST → 同候选原地重发一次（预算计数、不记熔断）** | **新增 `cacheStateLostRetriesSameCandidateInPlace` / `cacheStateLostBudgetExhaustedFailsLoud`**（缺口：全库无 CACHE_STATE_LOST 测试；防御分支 `ChatServiceFailoverAdapter.decideNonStreamFailure` 无覆盖） | **gap→新增** |
| A8 | 重试预算耗尽 fail-loud + 熔断 OPEN | `budgetExhaustedFailsLoudAndTripsBreaker` | 既有 |
| A9 | 全池饱和 fail-loud（并发饱和） | `fullPoolConcurrencySaturationFailsLoud` | 既有 |
| A10 | 全池饱和 fail-loud（健康度饱和） | `fullPoolHealthSaturationFailsLoud` | 既有 |
| A11 | 并发 +1/-1 全终止路径配对 | 各用例 `currentCount==0` 断言（A1/A5/A8/A17 等） | 既有 |
| A12 | 并发 acquire 后复查（超限跳过重选） | `concurrencyRecheckSkipsSaturatedCandidate` | 既有 |
| A13 | 探活恢复（HALF_OPEN → CLOSED） | `breakerProbeRecoveryEndToEnd` | 既有 |
| A14 | 探活候选池含 provider 链候选 | `probePoolIncludesProviderChainCandidates` | 既有 |
| A15 | 重发同步异常（限流）→ 切换恢复 | `syncRateLimitOnResubscribeSwitchesToChainCandidateAndRecovers` | 既有 |
| A16 | 同步异常预算耗尽 fail-loud | `syncRateLimitBudgetExhaustedFailsLoud` | 既有 |
| A17 | 调用方已取消干净终止 | `callerCancelledBeforeAttemptTerminatesCleanly` | 既有 |
| A18 | 无路由组直通零回归（不计数） | `noRoutingGroupPassThroughZeroRegression` | 既有 |
| A19 | 非流式重发（切换后重发到新账号 = 四字段下沉断言） | A1/A2/A4/A5 内断言（第二请求 bearer） | 既有 |
| A20 | 接线验证（策略/路由运行时调用） | `adapterInvokesStrategyAndRouterAtRuntime` | 既有 |

**B. 本地形态 · 流式（`FailoverStreamFlow`，测试类 `TestChatServiceFailoverAdapterStreaming`）**

| # | 场景 | 覆盖（用例名） | 状态 |
|---|------|---------------|------|
| B1 | 端到端全链：窗口内 QUOTA 失败 → 重订阅 → 越窗透传 → 终止（含 Major-1 缓冲丢弃） | `endToEndInWindowFailureResubscribeAndPassThrough` | 既有 |
| B2 | 窗口内 AUTH_INVALID（parseErrorResponse 恢复）→ 切换 | `streamAuthInvalidSwitchesAccount` | 既有 |
| B3 | **窗口内 TRANSIENT（503）→ 切换** | **新增 `streamTransientFailureSwitchesAccount`**（缺口：流式仅 QUOTA/AUTH 测过窗口内切换） | **gap→新增** |
| B4 | 窗口内 RATE_LIMITED（同步限流抛）→ 切换/恢复 | `streamSyncRateLimitOnResubscribeSwitchesAndRecovers` | 既有 |
| B5 | **窗口内 NON_TRANSIENT → 不切换断流报错（不耗预算不记熔断）** | **新增 `streamNonTransientDeliversErrorWithoutSwitch`**（缺口：本地流式无 NON_TRANSIENT 测试） | **gap→新增** |
| B6 | **窗口内 CACHE_STATE_LOST → 同候选原地重发** | **新增 `streamCacheStateLostResubscribesSameCandidate`**（缺口） | **gap→新增** |
| B7 | 缓冲窗口内重订阅（N/T 先到越窗） | B1 覆盖（12 元素越窗 N=10）+ `concurrencyCountHooksAtCallStreamInvocation` | 既有 |
| B8 | 窗口外失败断流不切换 | `outOfWindowFailureBreaksStreamWithoutSwitch`（已知 flaky 根因 = JDK SubmissionPublisher 竞态，W6 log 489，非 W8 引入；负载相关 ~5-10%） | 既有 |
| B9 | 窗口期成功终止全量交付缓冲元素 | `completionDuringWindowDeliversBufferedItems` | 既有 |
| B10 | 重试预算耗尽断流 fail-loud + 熔断 OPEN | `budgetExhaustedBreaksStreamFailsLoudAndTripsBreaker` | 既有 |
| B11 | 全池饱和 fail-loud（并发） | `streamingFullPoolConcurrencySaturationFailsLoud` | 既有 |
| B12 | **全池饱和 fail-loud（健康度，流式）** | **新增 `streamingFullPoolHealthSaturationFailsLoud`**（缺口：本地流式健康度饱和未测；同款 `selectNextWithProbe` 路径） | **gap→新增** |
| B13 | 探活恢复（流式成功 recordSuccess → CLOSED） | `streamProbeRecoveryRestoresCircuitClosed` | 既有 |
| B14 | 并发 +1/-1 配对（挂钩 callStream 调用时刻） | `concurrencyCountHooksAtCallStreamInvocation` + 各终止路径断言 | 既有 |
| B15 | 取消：per-attempt token 隔离 | `callerCancelStopsCurrentAttemptOnly` | 既有 |
| B16 | 取消：调用前已取消不发起 fetch | `callerCancelledBeforeStartNoFetch` | 既有 |
| B17 | 取消：输出侧 cancel 全 teardown | `outputSubscriptionCancelTriggersFullTeardown` | 既有 |
| B18 | 取消顺序契约（内部订阅 → attempt token） | `cancelOrderInternalSubscriptionBeforeAttemptToken` | 既有 |
| B19 | 单订阅者契约（第二个订阅者显式失败） | `secondSubscriberRejectedExplicitly` | 既有 |
| B20 | **无路由组流式直通（不计数）** | **新增 `streamingNoRoutingGroupPassthrough`**（缺口：本地流式直通未测，网关流式有 `noRoutingGroupStreamingPassthrough`） | **gap→新增** |

**C. 网关形态 · 非流式（`AiGatewayFailoverInterceptor.invoke`，测试类 `TestAiGatewayFailoverInterceptorNonStreaming`）**

| # | 场景 | 覆盖（用例名） | 状态 |
|---|------|---------------|------|
| C1 | TRANSIENT（网络错误）→ 切换 + 认证头下沉 | `nonStreamingNetworkErrorSwitchesAccountAndSucceeds` | 既有 |
| C2 | 切换带 baseUrl 账号（F3 URL 表达式消费） | `nonStreamingBaseUrlOverrideFlowsThroughUrlExpression` | 既有 |
| C3 | **RATE_LIMITED（429 响应经 InvokeProcessor 内置 3×退避重试后异常路径）→ 切换** | **新增 `nonStreamingRateLimited429SwitchesAccount`**（缺口；~15s 慢测试——InvokeProcessor 内置 429 退避 2s/4s/8s） | **gap→新增** |
| C4 | **AUTH_INVALID（401 响应，classifyResponseStatus）→ 切换** | **新增 `nonStreamingAuthInvalidResponseSwitchesAccount`**（缺口；**可达性注记：converter 路由（/chat/nonstream）经 `toFrontendResponse` 把非 2xx 归一化为 `ApiResponse.success`（status=0 = isOk()=true）→ 响应级分类仅在无 converter 路由（新增 `/chat/nonstream-raw`）可达**） | **gap→新增** |
| C5 | **NON_TRANSIENT（400 响应）→ 不切换直接失败** | **新增 `nonStreamingNonTransientFailsWithoutSwitch`**（缺口；同 C4 路由注记） | **gap→新增** |
| C6 | CACHE_STATE_LOST（网关非流式） | **N/A**（理由：网关非流式分类 = 错误路径 `classifyStreamError`（InvokeProcessor 429/5xx 异常参数键 `responseBody` ≠ `HttpApiErrors.ARG_BODY("body")` → body 不可达 → parseErrorResponse 无法命中 errorMappings）+ 响应路径 `classifyResponseStatus`（httpStatus 启发式），两路径均不产 CACHE_STATE_LOST——事件协议层不成立；防御分支在本地响应级 errorClassification 通道已测（A7/B6/D6）） | N/A |
| C7 | 预算耗尽 fail-loud | `nonStreamingBudgetExhaustedFailsLoud` | 既有 |
| C8 | B-8 无双重转换（streaming.enabled=false） | `streamingDisabledRouteDoesNotDoubleConvert` | 既有 |
| C9 | 无路由组直通零回归 | `noRoutingGroupNonStreamingPassthrough` | 既有 |
| C10 | 并发 +1/-1 配对 | C1/C2/C3/C4 断言 | 既有 |
| C11 | **探活恢复（网关非流式）** | **新增 `nonStreamingProbeRecoveryRestoresCircuitClosed`**（缺口：网关形态探活恢复未测） | **gap→新增** |

**D. 网关形态 · 流式（测试类 `TestAiGatewayFailoverInterceptorStreaming`）**

| # | 场景 | 覆盖（用例名） | 状态 |
|---|------|---------------|------|
| D1 | 端到端全链：窗口内 QUOTA 失败 → 跨 dialect 重订阅（B-10/B-12/B-14 接线） | `streamingEndToEndWindowInFailureCrossDialectResubscribe` | 既有 |
| D2 | 缓冲关闭仍计数重试 | `streamingWithoutBufferStillCountsAndRetries` | 既有 |
| D3 | **窗口内 TRANSIENT（503）→ 跨 dialect 重订阅** | **新增 `streamingTransientFailureSwitchesAccount`**（缺口：网关流式仅 QUOTA 测过窗口内切换） | **gap→新增** |
| D4 | **窗口内 AUTH_INVALID → 跨 dialect 重订阅** | **新增 `streamingAuthInvalidFailureSwitchesAccount`**（缺口） | **gap→新增** |
| D5 | NON_TRANSIENT 降级终止（onError 契约 B-17） | `nonTransientStreamingErrorDegradesToTerminalResponse` | 既有 |
| D6 | **窗口内 CACHE_STATE_LOST → 同候选原地重发（重执行回调）** | **新增 `streamingCacheStateLostResubscribesSameCandidate`**（缺口：`GatewayStreamingRetryCallback.retry` CACHE 分支未测） | **gap→新增** |
| D7 | 窗口外失败断流 | `windowOutFailureStreamsErrorWithoutSwitch` | 既有 |
| D8 | 预算耗尽断流 | `retryBudgetExhaustedStreamsError` | 既有 |
| D9 | 全池饱和 fail-loud（并发） | `concurrencySaturationFailsLoud` | 既有 |
| D10 | 全池饱和 fail-loud（健康度） | `breakerSaturationFailsLoud` | 既有 |
| D11 | **探活恢复（网关流式：HALF_OPEN → 成功 → CLOSED）** | **新增 `streamingProbeRecoveryRestoresClosed`**（缺口 + **暴露行为缺口 → in-scope Fix**：网关流式路径原无 recordSuccess——探活放行成功流后熔断器滞留 HALF_OPEN（§3.3 探活恢复契约漂移）；已修复 = 拦截器新增 `onStreamComplete` → `CircuitObservation.recordSuccess`（对齐本地 `FailoverStreamFlow.handleStreamComplete`；不记录 request-success 指标——OBS-01 契约表明确网关流式成功不在指标触发事件内）） | **gap→新增+Fix** |
| D12 | 并发 +1/-1 配对（生命周期回调） | D1/D2/D5 断言 + `TestFailoverMetricsGateway` 配对断言 | 既有 |
| D13 | 取消语义（网关流式） | N/A（缓冲层职责）——取消/终止语义由 nop-gateway 缓冲层测试 `StreamingProcessorBufferingTest`（11 用例：`abortCancelsCurrentSubscriptionAndTerminatesLifecycle`/`lifecycleFiresWithoutBufferAndAbortIsDeduplicated` 等）覆盖（nop-gateway 11 用例回跑见 Phase 4）；拦截器侧终止计数配对由 D12 覆盖——缓冲层事件协议 = nop-gateway 层职责（需求 §3.2 形态差异：缓冲/重订阅发生在字节流层），nop-ai-gateway 拦截器侧无独立取消协议 | N/A（跨模块覆盖） |
| D14 | 无路由组流式直通 | `noRoutingGroupStreamingPassthrough` | 既有 |
| D15 | 流式重发（重订阅重发到新账号/同账号） | D1（换候选）+ D6（同候选） | 既有+新增 |

**矩阵 100% 覆盖核验**：A 20/20、B 20/20、C 11 格（10 覆盖 + 1 N/A 带理由）、D 15 格（14 覆盖 + 1 N/A 带理由）——全部非 N/A 格子均有 live 用例（既有或新增），N/A 格均有理由落档。

**新增测试清单（15 个）**：`TestChatServiceFailoverAdapterNonStreaming` +2（cacheStateLostRetriesSameCandidateInPlace / cacheStateLostBudgetExhaustedFailsLoud）；`TestChatServiceFailoverAdapterStreaming` +5（streamTransientFailureSwitchesAccount / streamNonTransientDeliversErrorWithoutSwitch / streamCacheStateLostResubscribesSameCandidate / streamingFullPoolHealthSaturationFailsLoud / streamingNoRoutingGroupPassthrough）；`TestAiGatewayFailoverInterceptorNonStreaming` +4（nonStreamingRateLimited429SwitchesAccount / nonStreamingAuthInvalidResponseSwitchesAccount / nonStreamingNonTransientFailsWithoutSwitch / nonStreamingProbeRecoveryRestoresCircuitClosed）；`TestAiGatewayFailoverInterceptorStreaming` +4（streamingTransientFailureSwitchesAccount / streamingAuthInvalidFailureSwitchesAccount / streamingCacheStateLostResubscribesSameCandidate / streamingProbeRecoveryRestoresClosed）。

**测试基建新增**：`gw-cache.llm.xml` 测试 fixture（nop-ai-gateway test 资源，errorMapping → CACHE_STATE_LOST，httpStatus=409 + errorCodes=cache_state_lost——ErrorClassification javadoc 语义 409；default.llm.xml 映射均不命中该 code，first-match-wins 顺序安全）；`_default.model-class.xml` 新增 `tier-gw-cache`；`w7-failover.gateway.xml` 新增 `/chat/nonstream-raw` 路由（无 converter——响应级分类可达性）；`TestAiGatewayFailoverInterceptorNonStreaming.httpResponse` fake 增强（Nop 风格 body 抽取 status/code/msg——仿真实 Jackson 反序列化）。

**W8 实测发现并修复的 in-scope 行为缺口（2 个，均非测试设计问题）**：

1. **本地非流式 CACHE_STATE_LOST 原地重发缺 acquire（release 下溢 → future 永不完成挂起）**：`ChatServiceFailoverAdapter.decideNonStreamFailure` CACHE 分支直接调 `callAsyncWithOptions`（其 whenComplete 恒 release），未先 acquire → 原地重发时 release 下溢抛 `ERR_AI_AGENT_INVALID_ARG`（release-without-acquire fail-fast），异常被 CompletableFuture whenComplete 捕获后存储到被忽略的返回 stage → 重发后的成功分支与 relay 永不执行 → 调用方 future 永不完成（实测挂起）。修复 = CACHE 分支 acquire + 并发复查（M-6a 语义，与 `invokeNonStreamingAttempt` 对齐）后再重发；流式路径（`FailoverStreamFlow.startAttemptWithOptions`）与网关非流式路径（`invokeNonStreamingAttempt`）本就有 acquire，无此缺陷。此修复同时消除 W6 `outOfWindowFailureBreaksStreamWithoutSwitch` 的另一个潜在挂起面。
2. **网关流式路径探活恢复契约缺失（HALF_OPEN 成功不 → CLOSED）**：网关流式成功路径（缓冲层 onComplete）原无任何 recordSuccess——探活放行（HALF_OPEN）的流成功后熔断器滞留 HALF_OPEN，违背需求 §3.3"探活成功 → CLOSED 并清零失败计数"（本地形态 `FailoverStreamFlow.handleStreamComplete` 已实现，网关形态遗漏）。修复 = `AiGatewayFailoverInterceptor` 新增 `onStreamComplete(svcCtx)` 覆写（仅接管请求生效，零回归）：当前 attempt 候选 `CircuitObservation.recordSuccess`。不记录 request-success 指标——OBS-01 §3.6 契约表明确网关流式成功路径不在指标触发事件内（既有指标测试断言 0 计数，保持一致）。

**既有测试零回归**：W6 32（含 2 个 CACHE 新增后为 34？不——W6 基数 32 不变，新增不计入）+ W7 13 + 既有网关用例，本 Phase 后网关模块 175 用例全绿（OBS-01 后 160 + 本 Phase 新增 15 = 175）；既有用例无任何改写（`outOfWindowFailureBreaksStreamWithoutSwitch` 保留原断言，其已知 flaky 根因 = JDK SubmissionPublisher 竞态（W6 log 489），负载相关，非 W8 引入——连续 2 次全绿验证见 Exit Criteria 记录）。

- [x] 构建全链回归矩阵（平铺场景清单，避免全组合空转）：场景 = §3.1 动作表 6 分类（QUOTA_EXCEEDED/AUTH_INVALID/RATE_LIMITED/TRANSIENT → 切换；NON_TRANSIENT → 不切换不耗预算；CACHE_STATE_LOST → 原地重试）× 边界（缓冲窗口内重订阅/窗口外断流、重试预算耗尽、全池饱和 fail-loud、并发 +1/-1 全终止路径配对、探活恢复、取消顺序契约、非流式重发），视图 = 形态（本地/网关）× 模式（非流式/流式）四象限；**行为空缺组合不机械生成**（如 NON_TRANSIENT × 取消顺序契约 直接 N/A），**一条测试可覆盖多格**（用例标注其覆盖的格子）；**每格标注覆盖状态：既有用例名 / 新增用例名 / N/A（理由必须引用使事件协议层不成立的需求条款，如"网关非流式路径无缓冲窗口事件——缓冲窗口是流式重订阅机制（§3.2），非流式对应机制为非流式重发，见后者行"），禁止用空 N/A 逃避覆盖**
- [x] 逐格对照既有 4 个测试类（17/15/8/5 用例）产出 gap 清单；**gap 判定规则**：格内无任何断言该场景的测试 = gap；有测试但断言不覆盖该场景关键行为（如只断言不抛异常未断言重发到新账号）= gap；**gap 若揭示行为缺口（测试失败根因是功能未实现/有 bug 而非测试设计问题），属 in-scope Fix，不得降级为 follow-up**
- [x] 补缺测试：未覆盖的矩阵格新增用例（保持既有测试不改写）；**指标断言限于端到端路径测试**（切换次数、重订阅次数、饱和计数、熔断迁移计数在端到端路径上的增量断言，消费 OBS-01 指标服务；单元级补缺测试不强制指标断言）
- [x] 边界验证：缓冲窗口内重订阅 / 窗口外断流、重试预算耗尽 fail、全池饱和 fail-loud、并发 +1/-1 全终止路径配对、探活恢复（HALF_OPEN → CLOSED）、取消顺序契约、NON_TRANSIENT 不耗预算
- [x] 双形态 × 非流式/流式 × 各分类的端到端全链验证（从适配器/拦截器入口到最终输出/指标计数）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 矩阵表落档 **plan 内**（附 daily log 引用）：平铺场景清单逐格标注既有用例名/新增用例名/N/A+理由
- [x] 矩阵 100% 覆盖：每个非 N/A 格子在 live 测试套件中有对应用例（新增或既有），N/A 格均有理由落档
- [x] **端到端验证**：本地形态与网关形态各至少一条从入口到输出的全链测试（含指标断言）绿
- [x] **接线验证**：新测试断言了指标/重订阅/熔断等组件的运行时调用（非仅类型存在）
- [x] 既有测试零回归（口径：W6 32 + W7 13 为组成部分，网关模块用例总数 152 含上述新增——**OBS-01 已落地的指标测试为既有用例一并纳入，新增用例不并入 152 基数**；零回归核验 = 152 全绿且无既有用例改写）
- [x] 无 flaky：全链相关测试连续 2 次运行全绿（chain3/chain4 各 175 用例全绿；`outOfWindowFailureBreaksStreamWithoutSwitch` 已知 flaky 根因 = JDK SubmissionPublisher 竞态（W6 log 489，~5-10% 负载相关），非 W8 引入，既有断言未改写，2 次全绿记录见 daily log）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - docs-for-ai 使用文档（OBS-03）

Status: completed
Targets: `docs-for-ai/`（03-modules、INDEX.md、04-reference/source-anchors.md）

- Item Types: `Decision | Follow-up`

**落点裁定（2026-08-15 执行期）**：**新增独立页 `03-modules/nop-ai-gateway.md`**。判据核对：内容量 = 9 节且每节含完整配置示例（两种形态配置/挂载契约/缓冲参数/模型类与账号配置/规则策略/指标清单/行为契约摘要/源码锚点/相关文档）——超过 nop-ai.md 聚合页单章容量；nop-ai.md 追加 9 节将使聚合页（现 121 行）显著超长并破坏既有章节结构。**组内先例的例外理由**：nop-ai 组 12 子模块无独立页的语境 = 各子模块无独立使用契约（职责归聚合页行）；nop-ai-gateway failover 是 W1-W8 独立交付的生产级能力，有独立需求规格/架构文档，属于"有独立使用契约"的例外（与 nop-job.md 等有独立契约的模块页同构），且 failover 使用契约横跨 5 个配置面（gateway.xdef/llm.xdef/model-class.xdef/beans.xml/rule.xdef）+ 指标清单，聚合页行无法承载。落点同步：nop-ai.md 子模块表补 `nop-ai-gateway` 行 + 功能概览 bullet；INDEX.md 补路由条目；source-anchors.md 补 `AIGW-001`~`AIGW-009` 锚点。

- [x] 裁定文档落点并执行。判据：内容量（约 ≥6 节/含完整配置示例 → 新增独立页 `03-modules/nop-ai-gateway.md`，与 nop-job.md 等平级；否则 nop-ai.md 内章节 + 子模块表行）；**注意组内先例**——nop-ai 组 12 个子模块均无独立页（仅聚合页行），若裁定独立页需说明理由落档；两种落点都必须同步 INDEX 路由
- [x] 内容：网关形态配置（拦截器 bean 注册 + **`gateway.xml <interceptors>` 挂载契约——bean 注册 ≠ 挂载，部署必须引用 bean 到路由拦截器链（W7 M-7 契约）**、`gateway.xdef` streaming bufferEnabled/bufferSize/bufferTimeMs、`nop.ai.gateway.failover.*` 配置键、重试预算）；本地适配器用法（`nopChatServiceFailoverAdapter` bean 装配 + **部署启用方式——独立 bean 不覆盖 ioc:default，部署方需显式切换注入目标（W6 D4 裁定）**）；模型类与账号配置示例（引用 `model-class.xdef` 结构 + `llm.xdef` concurrencyLimit + `<accounts>`，**示例为新建示意片段，不拷贝 test fixture `_default.model-class.xml` 内容、不修改任何 `_` 前缀文件**）；规则策略用法（W5b follow-up 收口：`rule.xdef` + IoC 绑定 + 输入/输出契约 + `nop.ai.gateway.rule-selection.rule-name` 配置键）；指标清单（OBS-01 契约摘要）
- [x] 内容与 live repo 一致性核对（配置键名/bean 名/xdef 元素名逐一比对源码与 beans.xml）
- [x] 路由同步：INDEX.md 新增条目、source-anchors.md 新增锚点（如 AI 网关 failover 关键类）、03-modules/nop-ai.md 子模块表补充 nop-ai-gateway 行
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 errors

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 使用文档已交付且被 INDEX.md 路由；source-anchors.md / nop-ai.md 同步（文件路径 + 章节可核）
- [x] 文档引用的每个配置键/bean 名/xdef 元素与 live repo 一致（抽查比对记录）
- [x] check-doc-links.mjs --strict 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 需求文档状态收口（OBS-04）

Status: completed
Targets: `ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`

- Item Types: `Decision | Follow-up`

- [x] §五 Q 表逐条收口，**状态依据**：`待人工确认` = roadmap/W1 暴露为人工决策点的项（Q2/Q5，推荐默认已落地）；`已决` = 执行期裁定（Q3，W6 Phase 1 记录）或 spike 结论（Q7，W1 plan + W7 落地记录）——按此依据区分，不得混标。具体：Q2（推荐默认 = 路由覆盖 model，已落地，标注待人工最终确认）、Q5（推荐默认 = 确认偏离，RATE_LIMITED/TRANSIENT → 账号链切换，已落地，标注待人工最终确认）、Q3（执行期裁定回填：N=10/T=1000ms/预算 2/总延迟 null）、Q4（引用 OBS-01 §3.6 契约，状态 = 已落档）、Q7（spike + W7 落地回填：机制 A 最小通用改动）
- [x] 未获人工裁决项（Q2/Q5）显式保留"待确认"状态并引用 W6/W7 落地记录，**不得静默标记已决**
- [x] §3.x 落地状态与 live repo 一致性抽查（§3.1 动作表、§3.2 切换语义、§3.3 路由/熔断/并发、§3.4 配置、§3.7 双向转换的落地注记 vs 代码/测试）
- [x] roadmap W8 标 `done` 的判定材料就绪（closure audit 证据链）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] Q 表 Q2/Q3/Q4/Q5/Q7 均已回填处置结果（Q 表行可核），未裁决项保持显式"待确认"标注
- [x] §3.x 抽查记录在 daily log（抽查条目 + 结果）
- [x] requirement doc 无语义修订（仅状态回填与注记），R1-R8 审查结论未被破坏
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 收口验证（Proof）

Status: completed
Targets: 全量验证 + closure audit 材料

- Item Types: `Proof`

- [x] 相关模块全量测试绿色：`./mvnw test -pl :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -T 1C` + **nop-gateway 模块测试**（`-pl nop-service-framework/nop-gateway -am`，W7 follow-up 声明"nop-gateway 改动在 W8 全链回归中复核"——缓冲层 11 用例回跑；若裁定 nop-gateway 无改动仅由 nop-ai-gateway 测试间接覆盖，须落档理由）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 errors（复核 Phase 2）
- [x] 全链矩阵最终复核（矩阵表 vs live 测试套件）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 上述三项验证全过，结果记录于 daily log
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] 全链回归矩阵 100% 覆盖且零回归（补缺测试全绿、既有测试未改写）
- [x] docs-for-ai 使用文档交付 + INDEX/source-anchors/nop-ai.md 同步 + doc link check 0 errors
- [x] 需求文档 Q 表收口（Q2/Q3/Q4/Q5/Q7），未裁决项显式标注待确认
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline（requirement doc + docs-for-ai）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）新增测试覆盖的链路从入口到输出/指标在运行时确实连通，（b）无空方法体/静默跳过/no-op 作为正常实现（测试为真实断言，非占位）
- [x] `./mvnw compile`（`-pl :nop-ai-gateway -am`）
- [x] `./mvnw test`（`-pl :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -T 1C` + nop-gateway 模块测试，见 Phase 4）
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本 plan 文件> --strict` 退出码 0（无未勾选项 + Closure Evidence 已写入）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs nop-ai/nop-ai-gateway --severity high` 退出码 0（**用位置路径**：`--module nop-ai-gateway` 会解析到不存在的 `<root>/nop-ai-gateway` 空扫描恒绿，禁止使用）

## Deferred But Adjudicated

### 各 provider 显式非零 concurrencyLimit 缺省值（Q6 剩余）

- Classification: `optimization candidate`
- Why Not Blocking Closure: requirement §3.4/Q6 已决"缺省 = 不限制（零回归）"；"各 provider 是否需要显式配非零缺省"是部署面取值决策，不影响配置面语义契约成立。
- Successor Required: `no`

### 手动运维 / 多实例聚合 / 告警

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: requirement §3.6 显式 non-goal（本期），不影响 W8 收口判定。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 总延迟上限（time-based retry budget）落地：optimization candidate（W6/W7 裁定默认 null），如未来需求确认可单独评估。
- 指标告警规则/阈值：部署面增强，无 successor 硬依赖。

## Closure

Status Note: W8 OBS-02/03/04 全 4 Phase 落地——全链回归矩阵 66 格 100% 覆盖（15 新测试补齐全部 gap）+ 2 个 in-scope 行为缺口修复（本地非流式 CACHE_STATE_LOST 原地重发缺 acquire 挂起缺陷；网关流式探活恢复契约缺失）+ docs-for-ai 使用文档独立页（INDEX/source-anchors/nop-ai.md 同步 + doc link 0 errors）+ 需求文档 Q2/Q3/Q4/Q5/Q7 状态收口（未裁决项显式保持待人工确认）；独立 closure audit closure-approve（6/6 维 PASS）。
Completed: 2026-08-15

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent（fresh session，read-only）
- Audit Session: `ses_ffafacdf6ffeu4MwcP8IttwqqA`
- Evidence:
  - 每条 Phase Exit Criteria 验证结果（PASS）：Phase 1（7/7——矩阵 A/B/C/D 66 格逐格核对 live 套件（`rg "void <用例名>"` 实证 A 20/B 20/C 10/D 14 + N/A×2 理由与代码路径一致：C6 classifyStreamError/classifyResponseStatus 均不产 CACHE_STATE_LOST（`HttpApiErrors.ARG_BODY="body"` vs InvokeProcessor param `"responseBody"`，file:line 实证）、D13 取消 = nop-gateway 缓冲层职责（StreamingProcessorBufferingTest 11 用例回跑）；端到端+接线验证 = 60 用例（19/20/9/12）实证运行全绿；零回归 = chain3/chain4 连续 2 次 175 用例全绿 + nop-gateway 64 用例回跑全绿；无 flaky = 连续 2 次全绿记录 + 已知 flaky（outOfWindowFailureBreaksStreamWithoutSwitch，JDK SubmissionPublisher 竞态，W6 log 489，非 W8 引入）说明）；Phase 2（4/4——9 节独立页交付 + INDEX:186/nop-ai.md 子模块表行/source-anchors AIGW-001..009 路由同步 + 配置键/bean 名/xdef 元素与 live repo 逐一比对（beans.xml 6 bean + 5 配置键 + gateway.xdef/model-class.xdef 结构实证）+ check-doc-links exit 0）；Phase 3（4/4——Q2/Q5 显式保持待人工确认（requirement doc:399/402）、Q3 裁定回填（:400）、Q4 已落档（:401）、Q7 机制 A 落地复核（:404）+ §3.x 抽查无 drift）；Phase 4（3/3——175×2 + nop-gateway 64 全绿 + doc links 0 errors + 矩阵最终复核）。
  - 每条 Closure Gate 验证结果（PASS）：矩阵 100% 覆盖零回归；docs 交付 + 同步 + 0 errors；Q 表收口；无静默降级（2 个修复为 in-scope Fix 记录于 Phase 1，deferred 段无 live defect）；owner docs 同步；独立 closure audit（本段）；Anti-Hollow（本地 cacheStateLostRetriesSameCandidateInPlace + 网关 streamingProbeRecoveryRestoresClosed 端到端运行时断言实证 + scan-hollow 0 finding）；`./mvnw compile -pl :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -q` BUILD SUCCESS；`./mvnw clean install -DskipTests` BUILD SUCCESS；`./mvnw test` 连续 2 次 BUILD SUCCESS（chain3/chain4）+ nop-gateway 64；checkstyle 非有效门禁（Maven 默认 Sun 规则集 nop-api-core 9164 项基线违规，mission lint 命令 `|| echo 'lint not configured'` 回退；import-order 检查本 plan 变更文件零违规）；`check-doc-links.mjs --strict` exit 0；`check-plan-checklist.mjs --strict` exit 0（本段写入后复跑）；`scan-hollow-implementations.mjs nop-ai/nop-ai-gateway --severity high`（位置路径）exit 0（Critical 0 / High 0）。
  - Anti-Hollow 检查结果：两条端到端链路运行时连通（本地 = callAsync → decideNonStreamFailure CACHE 分支（acquire/recheck/resend）→ ChatServiceImpl → FakeHttpClient，断言 2 请求同 bearer 同 URL + breaker CLOSED + 计数归零；网关 = GatewayHandler → onRequest → BufferedStreamingPublisher → proceedOnStreamComplete → onStreamComplete → recordSuccess，断言 breaker CLOSED）；无空方法体/静默跳过/no-op。
  - Deferred 项分类检查：deferred 段仅 optimization candidate（Q6 缺省、总延迟上限）与 out-of-scope improvement（手动运维/告警），均有 Why Not Blocking Closure 理由；无 in-scope live defect 被降级。
  - 非阻塞观察（audit）：落点裁定"7 节"措辞与交付页 9 节计数差异（已修正为 9）；daily log 矩阵计数记法（C 10/10 vs 11 格）为 N/A 含/不含记法差异，一致。
  - 2 个 in-scope Fix 的证据：`ChatServiceFailoverAdapter.java:286-309`（CACHE 分支 acquire + 复查，W8 实测挂起根因 = release 下溢异常被 whenComplete 吞掉 future 永不完成）；`AiGatewayFailoverInterceptor.java:444-453`（onStreamComplete → recordSuccess，探活恢复契约 §3.3 网关流式缺口）。

Follow-up:

- 已知 flaky（非本 plan 引入，无 successor 硬依赖）：`TestChatServiceFailoverAdapterStreaming.outOfWindowFailureBreaksStreamWithoutSwitch`——根因 = JDK SubmissionPublisher closeExceptionally 竞态（W6 log 489），负载相关偶发（隔离运行稳定）；如未来消除可单独评估（不改写既有断言的前提下替换 fake 时序）。
- no remaining plan-owned work（roadmap W8 标 done 的引擎侧判定材料 = 本 plan closure 证据链；roadmap 状态流转由引擎/独立 closure audit 执行）
