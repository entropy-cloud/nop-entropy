# dsh-D4 容错性对比

> Status: resolved
> Date: 2026-09-12
> 基线: nop=800baf32da（nop-ai 模块与 c585459f83 diff 为空）、dsh=c291e7961a（分析当日实测）
> 引用: 00-dimension-matrix.md（WI2，D4 子机制 D4-1..D4-5）、02-terminology-map.md（T9/T10/T16）; 机制事实引用 03-flow-agent-loop.md（重试/恢复链路）、04/06（能力与协同），只写对比增量
> Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`

## ① 结论摘要

- 总裁定：**等价（强项互补）**。nop 强在恢复与治理面（三级失败升级、pause/resume、崩溃恢复守护、checkpoint 发散检测）；dsh 强在重试状态的持久化与确定性崩溃收尾。
- 关键差异 1（D4-2）：dsh 重试**先持久化再等待**（`llm/retry` 事件 + policyKey 重启恢复计数）——重试预算跨进程崩溃不丢；nop 重试计数是方法内局部变量（进程亡则亡）。
- 关键差异 2（D4-5）：nop 有显式治理恢复面（denial-pause → resumeSession、WAIT_FOR → wake、60s ScheduledRecoveryManager 扫描 orphan）；dsh 无治理暂停概念，崩溃恢复靠 `interruptedTurnClosers` 确定性合成收尾。
- 关键差异 3（D4-1）：双方都是结构化分类——nop ErrorClassification 6 值（cause 链解包）vs dsh 稳定错误码 + 正则归类器（"route on code, never parse message"）。
- 可吸收增量建议一句话：nop 可吸收 dsh 的"重试计划先持久化"模式（挂接 checkpoint journal），消融跨重启的重试预算丢失（见 ⑥）。

## ② nop 侧机制与锚点

分类、重试、恢复面按 02 T9/T10/T16 与 03 §2.1（P9/P14/P15X），对比增量：

- **错误分类**（D4-1）：`LlmErrorClassifier` → ErrorClassification 6 值：TRANSIENT/RATE_LIMITED/NON_TRANSIENT/QUOTA_EXCEEDED/AUTH_INVALID/CACHE_STATE_LOST，cause 链解包（`nop-ai-core/.../reliability/LlmErrorClassifier.java`）；框架错误走 NopException+ErrorCode 两级策略。
- **重试与退避**（D4-2）：`StandardRetryPolicy`——QUOTA/AUTH→FALLBACK、TRANSIENT/RATE_LIMITED→RETRY（maxAttempts=3，指数退避全抖动，Retry-After 为 RATE_LIMITED 下限）、其余 STOP（`CORE/reliability/StandardRetryPolicy.java:113-154`）；重试在 `LlmCallCoordinator.doLlmCallWithRetry` while 循环内（`engine/LlmCallCoordinator.java:156-401`），attempt 计数为方法局部变量。
- **部分失败**（D4-3）：批内隔离——单工具 veto/异常→该工具 error result、同批其他不受影响（`engine/AgentToolDispatcher.java:203-215,294-302`）；checkpoint DENY 注入 error response 保 tool_call_id 配对（`engine/AgentSecurityConsultation.java:160-162`）。
- **abort/cancel**（D4-4）：SESSION_CANCEL_REQUESTED/CANCELLED 事件 + reactLoop 顶部最高优先检查（`engine/ReActAgentExecutor.java:452-455`，注释明示 user-initiated 最高优先）；无 CancelCause 分型（单一取消语义 + pause/waiting/failed 区分靠终态机）。
- **降级与恢复**（D4-5）：三级失败升级（同账号 RETRY→账号链换 key→跨 provider failover→模型 tier，`LlmCallCoordinator.java:276-328`）；治理恢复：denial-pause→`resumeSession`（sticky，`engine/AgentSessionLifecycle.java:219-386`）、WAIT_FOR→wakeSession（:387-486）；崩溃恢复：`restoreSession`（:487-696，checkpoint journal 消费 + 幂等键发散检测 ：527-567，发散则降级 session 重放不阻断）+ `ScheduledRecoveryManager` 60s 扫描（过期锁清理→超时检测→orphan 判定→恢复/abort，`runtime/recovery/ScheduledRecoveryManager.java:109-`）。

## ③ 对方侧机制与锚点

按 02 T9/T10/T16 与 03 §2.2（P13/P20-21），对比增量：

- **错误分类**（D4-1）：`HarnessError` 稳定 code 机器路由（"route on code, never parse message"，`packages/llm/llm/src/error.ts:17-27`）；规范码 CONTEXT_WINDOW_EXCEEDED/QUOTA/EMPTY_RESPONSE/INVALID_CREDENTIAL/NO_ADAPTER…+ 默认可重试集（EMPTY_RESPONSE/RATE_LIMIT/SERVER/TIMEOUT/TRANSPORT，`retry-policy.ts:23-29`）；正则归类器把 provider 文案归类为规范码（`error.ts:70-115`）。
- **重试与退避**（D4-2）：策略归 provider 所有（PreparedLlmCall 冻结）、执行归 llm-retry 插件（`packages/llm/llm-retry/src/index.ts:123-259`）；normal（默认 5 次）|always（无限）；退避=指数+对称抖动，providerRetryAfterMs 有效则优先；**每次重试先持久化 `llm/retry`（含 policyKey/retryId/delayMs）再等待**（:188-190），恢复经 sessionProjections 事件折叠（:126-137：step/start|turn/end 清零、llm/retry 计入）——重试预算跨崩溃存活。
- **部分失败**（D4-3）：中止调用必须有结果对（provider 拒 dangling tool-call）：未派发→ABORTED_BEFORE_DISPATCH 合成 error（`packages/core/agent-loop/src/tool-calls.ts:250-260`）；崩溃孤儿→TOOL_NOT_STARTED/TOOL_OUTCOME_UNKNOWN + 谨慎重试指引文案（`packages/core/session/src/repair.ts:14-18,105-107`）。
- **abort/cancel**（D4-4）：TurnEndCancelCause 四分型（user/parent/hook/disposed，`packages/core/session/src/types.ts:187-195`）；abort 信号检查点密集分布（每 chunk 后等，06 ④.1）；中断流固化 interrupted:true 消息（`agent.ts:402-419`）。
- **降级与恢复**（D4-5）：无治理暂停概念；崩溃恢复=载入时 `interruptedTurnClosers` 确定性合成收尾（补 error result→step/end→turn/end{interrupted}，时间戳复用最后真实事件，合成收尾接线于 `packages/core/agent-loop/src/index.ts:892-893`（interruptedTurnClosers），合成器本体在 `packages/core/session/src/repair.ts:29`；coordinator.ts 已随 session-persistence 重写删除）；llm-retry 计数按 step/start 与 turn/end 清零（per-step 预算）。

## ④ 子机制逐项对照表

| 子机制 | nop 机制 | dsh 机制 | 裁定 | 证据 |
|---|---|---|---|---|
| D4-1 错误分类体系 | ErrorClassification 6 值枚举 + cause 链解包；框架 ErrorCode 两级策略 | 稳定错误码路由 + 正则归类器 + 默认可重试集声明 | 等价 | nop `LlmErrorClassifier.java`；dsh `error.ts:17-115`——同为结构化分类；nop 枚举语义更细（分出 CACHE_STATE_LOST），dsh 的可重试集是声明式配置（可含溢出码——06 ④.1 告警该配置风险） |
| D4-2 重试策略与退避 | 指数退避全抖动 + Retry-After floor；maxAttempts=3；计数在内存（局部变量） | 指数+对称抖动 + Retry-After 优先；normal 5/always 无限；**重试先持久化再等待 + policyKey 重启恢复** | 对方领先 | nop `StandardRetryPolicy.java:113-154`；dsh `llm-retry/src/index.ts:59-64,126-137,188-190`——持久化重试状态是 dsh 独有，长恢复场景（部署重启）nop 重试预算清零 |
| D4-3 部分失败与中断语义 | 批内隔离（单工具 error 不影响同批）；DENY 注入配对 error response | 中止调用必须有结果对（ABORTED_BEFORE_DISPATCH 合成）；崩溃孤儿 TOOL_OUTCOME_UNKNOWN + 模型可读指引 | 等价 | nop `AgentToolDispatcher.java:203-215`；dsh `tool-calls.ts:250-260`、`repair.ts:14-18`——两者都坚持"工具调用-结果配对不变式"；dsh 的崩溃合成文案（谨慎重试指引）更精细 |
| D4-4 abort/cancel | 单一取消语义 + 终态机区分；cancel 优先级最高（治理序首位） | TurnEndCancelCause 四分型（user/parent/hook/disposed）+ 密集 signal 检查点 + 中断内容固化 | 等价 | nop `ReActAgentExecutor.java:452-455`；dsh `types.ts:187-195`、`agent.ts:402-419`——dsh 取消原因分型支撑差异化善后（parent 取消 vs 用户取消），nop 靠事件与终态间接区分 |
| D4-5 降级与恢复路径 | 三级失败升级 + 治理恢复（pause/resume、wait/wake）+ 崩溃恢复守护（60s 扫描 + restoreSession + 发散检测降级） | 无治理面；interruptedTurnClosers 确定性合成收尾 + llm-retry policyKey 恢复 | nop 领先 | nop `LlmCallCoordinator.java:276-328`、`AgentSessionLifecycle.java:219-696`、`ScheduledRecoveryManager.java:109-`；dsh `agent-loop/src/index.ts:892-893`（interruptedTurnClosers 接线）、`core/session/src/repair.ts:29`（合成器本体）——nop 的恢复面（治理+崩溃+主动扫描）显著更宽；dsh 合成收尾的确定性设计（时间戳复用）更优雅但范围窄 |

## ⑤ 语义差异与取舍

- **重试归属**（T9）：nop 重试策略是引擎组件（IRetryPolicy 注入 Coordinator），重试对主循环透明；dsh 策略归 provider 注册所有、执行归可选插件（llm-retry 不装则失败终局）——nop"开箱即容错"vs dsh"容错是组合项"。词同义异警示：dsh `always` 模式是对路由上一切失败无限重试，非"always 重试同请求"。
- **恢复的真源不同**（T16）：nop 恢复真源=session 快照（消息历史）+ journal 校验（分录只存摘要与计数，不含消息内容——`Checkpoint.java:45-333`）；dsh 恢复真源=事件日志本身（合成 closers 是补事件）。两者"幂等"含义不同：nop 幂等键=工具调用指纹（sha256(toolName|callId|inputSummary)，`Checkpoint.computeIdempotencyKey:210-225`）防重复恢复；dsh policyKey=重试计数防重复计次。
- **治理暂停是 nop 独有语义**（T10）：nop 的 paused（可恢复治理态）与 escalated（terminal）四态互斥纪律在 dsh 无对位——dsh 的对应物只有 turn error（可重试）与 blocked（pre-step 拒绝）。
- **不可比项**：nop 的 WAIT_FOR 挂起/唤醒原语（外部条件等待）在 dsh 无对位（dsh 用 inbox 注入表达等待）；按 02 总则不硬比。

## ⑥ 裁定 + 可吸收增量建议

**维度总裁定：等价**——容错四要素（分类/重试/部分失败/取消）双方能力相当且取舍互补；nop 的恢复治理面（D4-5）与 dsh 的重试持久化（D4-2）分别是各自体系中最有价值的独有设计，均不构成全面领先。

可吸收增量建议（仅记录，不实施）：

1. 【来源 dsh；针对 nop 重试计数易失】将 LLM 重试计划挂接 checkpoint journal（新增 RETRY_PLAN 分录类型或复用 LLM_TURN payload 扩展），重启后经 restoreSession 恢复 attempt 计数——避免部署重启导致重试预算重置、对限流 provider 造成重复压力。
2. 【来源 dsh；针对 nop 崩溃孤儿工具】为崩溃时未完成的工具调用建立确定性合成收尾（对位 interruptedTurnClosers）：当前 nop 靠发散检测降级重放，可为 TOOL_EXECUTION checkpoint 后缺失结果的调用生成"结果未知，勿盲目重试"的 error response。
3. 【来源 nop 自身；记录 dsh 参考】dsh 的正则归类器（把 provider 非结构化文案归入规范码）可补充 nop ErrorClassification 的 cause 链解包，提高跨 provider 兼容率。
4. 【来源 dsh；针对 nop 无取消分型】为 SESSION_CANCEL_REQUESTED 增加 cause 字段（user/parent/system），支撑差异化善后与审计。

## References

- `ai-dev/analysis/compare-agent-design/00-dimension-matrix.md`（D4 定义）、`02-terminology-map.md`（T9/T10/T16）、`03-flow-agent-loop.md`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`（Owner doc）
- nop：`nop-ai/nop-ai-core/.../reliability/`、`nop-ai/nop-ai-agent/.../engine/LlmCallCoordinator.java`、`reliability/`、`runtime/recovery/`
- dsh：`packages/llm/llm/src/error.ts`、`packages/llm/llm-retry/src/index.ts`、`packages/core/session/src/repair.ts`、`packages/core/agent-loop/src/index.ts`（`packages/session/session-persistence/src/coordinator.ts` 已随该包重写删除，原合成收尾上移至 agent-loop）（`~/ai/deepseek-harness` @ c291e7961a）
