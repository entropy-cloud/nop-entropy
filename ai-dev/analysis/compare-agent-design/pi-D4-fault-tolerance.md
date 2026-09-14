# pi-D4 容错性对比

> Status: resolved
> Date: 2026-09-12
> 基线: nop=800baf32da（nop-ai 模块与 c585459f83 diff 为空）、pi=c49906ec7（分析当日实测）
> 锚点重钉: 2026-09-14，HEAD 4582e780dad4（plan 355 重构+M5/M6 修复后逐锚点核对；仅行号更新，结论不变）
> 引用: 00-dimension-matrix.md（WI2，D4 子机制 D4-1..D4-5）、02-terminology-map.md（T9/T10/T16）; 机制事实引用 03 §2.3（P13/P14 恢复循环）、06（④.1 abort×retry 裁决）
> Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`

## ① 结论摘要

- 总裁定：**等价（精确性 vs 纵深互补）**。pi 强在错误分类的精确性与重试分层（溢出独立第三类绕过重试直达压缩、SDK 镜像传输层+应用层双层重试）；nop 强在恢复纵深（三级升级/治理 pause/崩溃恢复守护/发散检测）。
- 关键差异 1（D4-1）：pi 三表正则分类（可重试表/不可重试配额表/溢出表，先查排除表）+ 静默溢出检测（usage.input>contextWindow）；nop 枚举分类+cause 链解包。
- 关键差异 2（D4-5）：pi 的溢出恢复是**一次性门闩**（_overflowRecoveryAttempted，成功响应或新 user 消息才复位）——防压缩-重试死循环；nop 对位是 veto cap/bail cap 计数器族。
- 关键差异 3（D4-4）：pi 的 session.abort() 先 abortRetry 再 agent.abort()（有序取消，06 ④.1 裁定"continue 时已 abort 不可达"）；nop cancel 在治理序首位。
- 可吸收增量建议一句话：nop 可吸收 pi 的"溢出独立分类+一次性恢复门闩"对（与 maxIterations 语义互补，见 ⑥）。

## ② nop 侧机制与锚点

nop 事实基线与 dsh-D4 报告 ② 节共享（02 T9/T10、03 §2.1）：ErrorClassification 6 值+cause 链解包（`nop-ai-core/.../reliability/LlmErrorClassifier.java`）；StandardRetryPolicy 指数退避全抖动+Retry-After floor、maxAttempts=3（`StandardRetryPolicy.java:113-154`）；重试内存态；批内隔离+DENY 配对注入；治理恢复三路（pause/resume、wait/wake、restore）+发散检测+60s 扫描+接管锁（`AgentSessionLifecycle.java:222-801`、`ScheduledRecoveryManager.java:404-`）。

## ③ pi 侧机制与锚点

按 02 T9/T10 与 03 §2.3（P7/P13/P14），对比增量：

- **错误分类**（D4-1）：三表正则——`NON_RETRYABLE_PROVIDER_LIMIT_ERROR_PATTERN`（配额/账单，先查即排除）→`RETRYABLE_PROVIDER_ERROR_PATTERN`（~40 条：overloaded/rate-limit/429/5xx/网络，`packages/ai/src/utils/retry.ts:7,26,223-228`）；**溢出独立第三类** `isContextOverflow`（27 条 provider 溢出正则+静默溢出 usage.input>contextWindow+length-零输出，`overflow.ts:37,74,134-163`），先于重试判断（`_isRetryableError` 显式排除，`agent-session.ts:2770-2774`）。
- **重试与退避**（D4-2）：双层——传输层 `retryProviderRequest`（显式复刻 OpenAI/Anthropic SDK 策略：x-should-retry 头/408/409/429/5xx/retry-after 上限 maxRetryDelayMs，SDK 须 maxRetries:0，`provider-retry.ts:22-35,105-125`）+应用层 `_prepareRetry`（settings.retry.maxRetries=3，指数退避 baseDelayMs·2ⁿ，**摘除 error assistant 消息**在退避 sleep 之前，`agent-session.ts:2811-2861`）；重试对象=整条 assistant 消息重生成。
- **部分失败**（D4-3）：工具 throw→`createErrorToolResult`+isError 正常进 transcript（`agent-loop.ts:701-707,760-765`）；stopReason=length→整批工具调用判废回填（"arguments may be truncated... Re-issue"，:381-406）；afterToolCall 抛错→结果整体替换为 error（:747-750）。
- **abort/cancel**（D4-4）：`session.abort()` 先 abortRetry 再 agent.abort()（:1561-1565）；退避 sleep 用独立 `_retryAbortController`；abort 期间被取消的 retry 落入 `_checkCompaction` 后 threshold 压缩仍可能执行（willRetry=false 边缘行为，06 ④.1）；pi-ai `retry.ts` 的"退避中 abort 归一为 aborted"只用于摘要调用不用于主 turn。
- **降级与恢复**（D4-5）：溢出恢复环——摘 error 消息→`_runAutoCompaction("overflow")`（session_before_compact 可 cancel/自备结果）→`buildSessionContext` 重建→`agent.continue()` 一次（**门闩** `_overflowRecoveryAttempted`，成功响应/新 user 消息复位，`agent-session.ts:2050-2154,334`）；无治理 pause/崩溃扫描对位。

## ④ 子机制逐项对照表

| 子机制 | nop 机制 | pi 机制 | 裁定 | 证据 |
|---|---|---|---|---|
| D4-1 错误分类体系 | ErrorClassification 6 值枚举+cause 链解包（结构化优先） | 三表正则+静默溢出检测；**溢出独立于重试分类**（直达压缩） | 等价 | nop `LlmErrorClassifier.java`；pi `retry.ts:7-26`、`overflow.ts:37-163`——nop 分类语义更细（CACHE_STATE_LOST），pi 的溢出独立分类更精确（防止对溢出做无谓重试）；路径不同目标同 |
| D4-2 重试策略与退避 | 单层引擎内重试（IRetryPolicy，maxAttempts=3，全抖动+Retry-After floor）；重试透明于主循环 | 双层（传输层 SDK 镜像+应用层消息重生成）；摘除失败消息+退避可取消；per-call getApiKey | 等价 | nop `StandardRetryPolicy.java:113-154`、`LlmCallCoordinator.java:160-418`；pi `provider-retry.ts:22-125`、`agent-session.ts:2811-2861`——pi 分层精细、nop 一体化简单；双方重试状态都不持久化（dsh 独有持久化，见 dsh-D4） |
| D4-3 部分失败与中断语义 | 批内隔离+DENY 配对 error response+checkpoint 落账 | 工具 throw→error result；length 整批判废（截断参数提示重发）；afterToolCall 抛错→结果替换 | 等价 | nop `AgentToolDispatcher.java:247-256`；pi `agent-loop.ts:381-406,701-750`——双方都维持"调用-结果配对"；pi 的 length 判废提示（"Re-issue"）比 nop 对位（无 length 特判，截断结果原样回填）更精细，单点优势不翻权衡 |
| D4-4 abort/cancel | 单一取消+治理序首位（cancel>pause>wait>force-stop>goal） | 有序取消（abortRetry 先于 agent.abort）+独立 retry controller+退避中 abort 归一化 | 等价 | nop `ReActAgentExecutor.java:731-734`；pi `agent-session.ts:1561-1565`、06 ④.1——nop 治理序 vs pi 取消序；双方都把"取消与恢复机制的竞争"显式裁决 |
| D4-5 降级与恢复路径 | 三级失败升级+治理 pause/resume+wait/wake+崩溃恢复守护（扫描/发散检测/接管锁） | 溢出压缩恢复环（一次性门闩）+auto_retry 预算+分类器硬互斥（溢出不重试） | nop 领先 | nop `AgentSessionLifecycle.java:222-801`、`ScheduledRecoveryManager.java:404-`；pi `agent-session.ts:2050-2154`——nop 恢复面（治理+崩溃+跨进程）显著更宽；pi 的门闩防死循环设计与 nop cap 族同构但作用域更聚焦 |

## ⑤ 语义差异与取舍

- **重试的单元**（T9 注记）：pi 重试=整条 assistant 消息重生成（摘除旧消息）——语义上是"重新问"；nop 重试=同一 HTTP 请求重发（请求不变）——语义上是"再试一次"。对 provider 计费与缓存命中（D6 交叉）影响不同：pi 的重生成必然 cache 未命中前缀尾部，nop 的重发可命中。词同义异警示：两方"retry"不可直译。
- **溢出的地位**：nop 把溢出当 force-stop 治理事件（0.9 阈值预判+硬停）+压缩触发条件之一；pi 把溢出当独立错误类（分类器级隔离）。pi 的分类器级隔离更早介入（错误发生即分类），nop 的阈值预判更早预防（错误发生前）——预防 vs 恢复的取舍。
- **门闩 vs cap**：pi `_overflowRecoveryAttempted` 布尔门闩（每 user 消息复位）vs nop cap 计数器（3 次上限）——门闩防的是"同一溢出反复恢复"，cap 防的是"同类 veto 反复发生"；pi 门闩粒度更贴溢出场景。
- **不可比项**：pi 的 provider 正则表内容（40 条文案匹配）是生态经验数据，nop 枚举分类无对位细节——登记不硬比。

## ⑥ 裁定 + 可吸收增量建议

**维度总裁定：等价**——D4-1/2/3/4 双方各有精细点（分类/分层/判废/取消序），D4-5 nop 领先（恢复纵深）；pi 的"精确分类+聚焦恢复"与 nop 的"宽恢复面+治理纵深"互补，无全面领先。

可吸收增量建议（仅记录，不实施）：

1. 【来源 pi；针对 nop 溢出处理】为 LlmErrorClassifier 增加 CONTEXT_OVERFLOW 独立分类值（当前 CACHE_STATE_LOST 已有先例），并在 LlmCallCoordinator 决策中路由到"压缩后重试"而非普通 RETRY——配合 dsh-D8 建议 1（压缩后重试环）形成完整溢出恢复。
2. 【来源 pi；针对 nop 重试重入】评估"重试前摘除失败响应"语义（pi 式消息重生成）作为 IRetryPolicy 的可选模式——当前 nop 重发原请求，对确定性错误（如 context 超限）重发无意义。
3. 【来源 pi；针对 nop 静默溢出】CalibratedTokenEstimator 增加"静默溢出检测"（provider usage input>contextWindow 时校正估算器）——pi 的静默溢出检测可反哺 nop EMA 校准的收敛速度。
4. 【来源 nop；反向记录】治理 pause/resume 与发散检测恢复（nop 独有）是长生命周期自主 agent 的关键容错面，pi/dsh 场景（交互式）暂无对应需求——记录适用前提。

## References

- `ai-dev/analysis/compare-agent-design/00-dimension-matrix.md`（D4 定义）、`02-terminology-map.md`（T9/T10/T16）、`03-flow-agent-loop.md`（§2.3）、`06-extension-composition.md`、`dsh-D4-fault-tolerance.md`（nop 基线共享）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`（Owner doc）
- nop：`nop-ai/nop-ai-core/.../reliability/`、`nop-ai/nop-ai-agent/.../engine/LlmCallCoordinator.java`
- pi：`packages/ai/src/utils/{retry,provider-retry,overflow}.ts`、`packages/coding-agent/src/core/agent-session.ts`（`~/ai/pi` @ c49906ec7）
