# C8 深挖第 2 轮 — nop-ai-gateway（WI 3.3）

- **审计日期**: 2026-10-20
- **审计员**: C8 维度深挖第 2 轮盲审员（盲审纪律同第 1 轮；编号续接 C8-004 起）
- **方法**: `ai-dev/skills/concurrency-resource-audit-prompt.md` L26–118 + L289–307（C8 正文）+ L351–377（附录 A）

## 深挖范围与方法

按派单深挖面逐一执行：

1. **并发限流的饱和行为（ConcurrencyRegistry 满时语义）**：谓词闭环验算——选择期健康视图 `CandidateHealth.isConcurrencySaturated`（`current >= limit`）与 acquire 后复查 `FailoverProbeSupport.isConcurrencySaturated`/`ChatServiceFailoverAdapter.isOverConcurrencyLimit`（`count > limit`，count 为 acquire 后值）：复查放行的最大并发恰为 L（第 L 个 acquire 使 count==L，`L > L` 假 → 放行），健康视图在 count==L 起标饱和阻止第 L+1 个进入选择——两谓词组合实现"最大并发 = L"的一致语义，**边界一致，负验证**。饱和重选递归：三处（invokeNonStreamingAttempt/callAsyncAttempt/startAttemptWithOptions）超限路径 release 后重选，attempted 集合单调增长保证终止（第 1 轮 C3-005 已登记其副作用），递归深度 ≤ 候选池 + provider 链规模——复核维持。`limit == null/≤0` = 不限制为候选解析期已解析声明。CACHE_STATE_LOST 原地重发的超限分支（ChatServiceFailoverAdapter L304-310）release 后 `completeNonStreamFailure` 干净失败——无泄漏无递归，负验证。
2. **分段发送的流控**：`deliverSegmented`/`splitIntoSegments`（FeishuConnector L439-500）——段数无上限（CL-017 第 1 轮已登记）、无出站准入（C8-003 已立案）、失败续发为声明的 no-silent-drop 语义——本轮定向补充验证：**限流拒绝路径的回复自身无准入**（L303 对每条被拒消息同步回一条出站），放大关系见下 CL-023；无新独立站点。
3. **上游 429/533 传播路径**：全链追踪——非流式异常路径：InvokeProcessor 对 429/5xx 抛异常 → `classifyStreamError`（ChatServiceFailoverAdapter L498-529）unwrap cause 链找 `NopException.ARG_HTTP_STATUS` → dialect.parseErrorResponse 恢复响应级分类（429 → RATE_LIMITED）；非流式响应路径：`classifyResponseStatus`（AiGatewayFailoverInterceptor L360-378，429 → RATE_LIMITED、401/403 → AUTH_INVALID、5xx → TRANSIENT、其余 4xx → NON_TRANSIENT）；流式：onError B-17 分类契约。**"533"全仓 grep 零命中**（含 nop-integration-feishu）——任务简报所载 533 无在库引用，留痕。发现 C8-004 立案 headers 信号链断裂面。

## 新增发现

### [C8-004] 上游限流/降载信号链断裂：ARG_RESPONSE_HEADERS 已恢复进分类入口却在编排层被全量丢弃——Retry-After/限流头零消费（平台 RetryContext.retryAfterMs 归一设施未被本模块触及），provider 级 429 下即时切换对兄弟账号形成无间隔加压

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/ChatServiceFailoverAdapter.java:498-529`（headers 恢复后仅透传 parseErrorResponse）、`:458-464`（isSwitchable 即切）；`nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/AiGatewayFailoverInterceptor.java:360-378`（classifyResponseStatus 完全不读 headers）；平台设施证据 `nop-ai/nop-ai-core/.../RetryContext.java:32-79`（retryAfterMs 归一字段，本模块零引用——grep 举证）
- **证据片段**:
  ```java
  // classifyStreamError——headers 已恢复为局部变量：
  Object headersObj = ex.getParam(HttpApiErrors.ARG_RESPONSE_HEADERS);   // L508
  Map<String, String> headers = headersObj instanceof Map ? ... : null;  // L510-511
  ChatResponse err = dialect.parseErrorResponse(body, httpStatus, headers, config); // L517 —— 仅用于分类判定
  // 分类完成后：isSwitchable(RATE_LIMITED)==true → 立即重选/重订阅（无任何等待）
  CircuitObservation.recordFailure(...);                                 // L325
  callAsyncAttempt(request, router, cancelToken, attempt + 1);           // L332 —— 同步立即发起
  ```
- **严重程度**: P3（降档依据："重试无退避为即时切换语义"是第 1 轮已核实的 owner doc 声明语义；本条立案面收窄为**信号链的可用而未用**，非行为本身）
- **并发场景**: provider 返回 429（或 dialect 映射的过载类错误，典型头 `Retry-After: N`）时，多条并发请求的失败决策线程各自立即发起新 attempt。
- **触发时序**: T1…Tn 并发请求命中同一 provider 的账号级限流（429）→ 各自 whenComplete/onError 分类为 RATE_LIMITED（可切换）→ 即时重选：`DefaultSelectionStrategy` 跳过已尝试候选 → 同 provider 兄弟账号（或 provider 链扩展候选）**零间隔**承接全部重试流量 → 兄弟账号在 provider 侧共享限流配额时同样 429 → 再切换（预算 2 内）→ 交叠后果：单账号限流在毫秒级被放大为全账号链的连环探测；熔断记账（3 连败 OPEN）是唯一减速器，但连续失败计数被任意成功穿插即清零——混合负载下减速器常态失效。
- **现状**: 平台 `LlmErrorClassifier`/`RetryContext` 已定义 `retryAfterMs` 归一契约（nop-ai-core），本模块编排对 `Retry-After`/`retryAfterMs` 零消费（grep 全根零命中）；`classifyStreamError` 恢复的 headers Map 在分类完成后即弃，`classifyResponseStatus` 连读都不读。
- **风险**: 有界（budget=2 封顶每请求放大 ×3），但并发基数放大（N 并发 ×3）在 provider 侧表现为限流风暴，延长整体冷却时间；对按请求计费的兄弟账号产生本可避免的额外消耗。无内存/资源泄漏面。
- **建议**: 编排层消费限流信号：分类为 RATE_LIMITED 时从 headers 解析 `Retry-After`（或复用 `RetryContext.retryAfterMs` 归一值），对被限流的 **provider 维度**记录"静默至"时间戳（per-provider `ConcurrentHashMap<String,Long>` 即可，非新增重试策略设施），重选时跳过未到期的 provider——与"不消费 IRetryPolicy"的声明边界不冲突（这是请求间信号记忆，非每请求重试策略）。
- **失效面枚举（F3）**: 限流信号消费点全量清单——①`classifyStreamError` headers 恢复（**断裂点 1**：恢复后仅透传分类）；②`classifyResponseStatus`（**断裂点 2**：完全不读 headers）；③`isSwitchable` 后的即时切换（ChatServiceFailoverAdapter L332 / AiGatewayFailoverInterceptor L350 / FailoverStreamFlow L462 / GatewayStreamingRetryCallback L95 四形态一致无退避，**声明语义**，不立案仅指认）；④nop-ai-core `RetryContext.retryAfterMs`（平台设施，本模块零引用，证据）；⑤Feishu 入站限流（FeishuConnector 滚动窗，方向相反且有准入，豁免）。
- **信心水平**: 确定（headers 恢复与丢弃、Retry-After 零消费均为 grep/代码实证；放大后果的并发量级为推算）
- **误报排除**: 非"重试风暴面无发现"（C7 第 1 轮）的翻案——该结论核验的是**重试收敛性**（预算/递归终止），本条是**重试时机信号**的缺失，不同失效面；非已声明边界——声明的是"无退避的即时切换语义"，未声明"限流响应头可用而全量丢弃"；判 P3 与声明语义的覆盖范围一致。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现（cra-index WI 1.1–3.2 台账零同站点）。逐子项回码证实：①headers 恢复后仅透传分类——L508 `ARG_RESPONSE_HEADERS` 恢复、L510-511 Map 化、L517 仅入 `parseErrorResponse` 后即弃（方法返回 classification，headers 无第二消费点）；②`classifyResponseStatus` L360-377 仅读 httpStatus，连读不读 headers；③限流信号零消费 grep 复跑成立（`retryAfterMs|Retry-After|RETRY_AFTER` 于 nop-ai-gateway 全根 0 命中）——平台设施真实性反证：nop-ai-core `RetryContext`（reliability/RetryContext.java，class L42）的 `retryAfterMs` 被 `StandardRetryPolicy` L144-153 作为 RATE_LIMITED 退避 floor 实际消费，唯独本模块编排层不触及；④四形态即时切换一致回码（adapter L332 / interceptor L350 / FailoverStreamFlow L462 / GatewayStreamingRetryCallback retry 体）；⑤减速器穿插失效证实——`ThresholdBreaker.recordSuccess` L232-245 对 CLOSED 条目 `consecutiveFailures = 0`，任意成功穿插即清零。判级校准：行为面（无退避即时切换）为 owner doc 声明语义，立案面收窄为信号可用而未用、放大有界（budget=2 封顶 ×3）、无资源面——P3 恰当（与 C7-005 P2 的判级差异成因：彼为容量单调流失不可自愈，本条有界且负载回落后自然恢复）。live 面（failover 请求主链）P3 维持

## 维度集外线索（overlay）

### [nop-ai-gateway-CL-023] BufferedStreamingPublisher（nop-gateway 侧）存在与 C8-001 同构的越窗零需求无界缓冲——第 1 轮 F3 清扫只登记了本地形态站点，修复须同批覆盖孪生站点

`BufferedStreamingPublisher.handleItem`（nop-service-framework/nop-gateway/.../BufferedStreamingPublisher.java:260-266）：`passed==true` 后 `demand==0` 分支 `state.buffer.add(item)` 无容量检查，且 `AttemptSubscriber.onSubscribe` 无 `request(MAX)`（映射链自驱动 request(1)）——上游节流形态与本地不同，但下游零 demand + 上游持续产出时同样无界积压。第 1 轮 CL-001/005/012/014/C8-001 的"越窗后缓冲无上界"全部锚定 `FailoverStreamFlow` 站点，网关侧孪生站点未登记（锚点在 nop-gateway 模块根，归该模块批次或本模块修复联动）。建议：C8-001 修复（越窗缓冲硬上限 + 溢出策略）时将 `AttemptState.buffer` 抽象为共享的有界缓冲组件，两站点同批替换，防 F3 同族漏修。

—— 本维度无其他集外线索。

深挖面负验证留痕（不立案）：①饱和谓词两形态边界验算一致（最大并发 = L）；②CACHE_STATE_LOST 超限分支干净失败；③533 全仓零引用（简报锚点无在库对象）；④分段流控三面（段数上限 CL-017/出站准入 C8-003/失败续发声明语义）维持第 1 轮登记；⑤模式 2 sendAsync persist-before-return 声明语义维持。

## 继续判定

新增 1 条（P3×1，无 P0/P1/P2）。判定：**终止信号**——①第 1 轮 2×P1（C8-001 越窗缓冲/C8-002 超时锚定）的主站点第 2 轮定向复核未再产出新面，唯一实质产出是 C8-001 的**孪生站点补登记**（CL-023）与一个 P3 信号链缺口；②三个定向深挖面（饱和语义/分段流控/429 传播）均闭合，其中饱和语义为完整负验证；③生产-消费边界清单（第 1 轮 B1–B6）复核后无新增边界。建议 C8 维度不再派第 3 轮，转入 Phase 4 复核合并。

---
*审计员：C8 深挖第 2 轮盲审。发现计数：P0=0，P1=0，P2=0，P3=1；CL 追加=1（CL-023）。*

## 复核结论（WI 3.3 独立复核）

- **C8-004**：保留(P3)——五子项复核全部回码证实（状态行见上方条目）；grep 零消费复跑成立，平台 `RetryContext.retryAfterMs` 的真实性由 `StandardRetryPolicy` 消费点反证；声明语义边界（无退避即时切换）尊重，立案面收窄恰当，P3 维持。
- **CL-023 复核裁定：站点同构事实成立，但属重复登记——按 campaign cross-reference 规则关闭新立案**。孪生站点回码证实（`BufferedStreamingPublisher.handleItem` 越窗后 `demand==0` 分支 `state.buffer.add(item)` 无容量检查，L250-266；"映射链自驱动 request(1)、缓冲层不追加请求" L378 注释证实，与本地形态上游节流形态差异的表述准确）；但初审"网关侧孪生站点未登记"为**登记勘误**——该站点已由 WI 2.3 立案为 `nop-service-framework-hotpath-C3-003`（P1，open 待 7.1，cra-index P1 台账"BufferedStreamingPublisher 背压断裂：映射链自驱动 request(1) 越窗后元素进无界 ArrayDeque"）。CL-023 不作为本单元新线索计入台账，改为 cross-reference 关闭；其"两站点同批修复/抽象共享有界缓冲组件"建议转为 C8-001 工单的修复联动指令保留（F3 防同族漏修价值成立）。
- **第 2 轮终止信号判定维持**：三个定向深挖面复核抽验无翻案——①饱和谓词负验证与 C8-004 不冲突（彼为并发计数边界，本条为重试时机信号）；③533 全仓零引用复跑成立；④分段流控三面（CL-017/C8-003/失败续发声明语义）维持第 1 轮登记，其中 C8-003 降档 P3 条件化（判定表见 C8-backpressure.md「维度复核结论」）。
- 深挖后 C8 维度合计判定：**保留×2（C8-001 P1、C8-004 P3）+ 降级×2（C8-002 P1→P2 条件化、C8-003 P2→P3 条件化）+ 驳回×0**；CL 处置：CL-001/005/012/014 并入 C8-001 工单（同站点同面 F3 重复登记收敛）、CL-023 cross-ref 至 WI 2.3 C3-003 关闭。维度判定表与最终保留项见 C8-backpressure.md「维度复核结论」。
