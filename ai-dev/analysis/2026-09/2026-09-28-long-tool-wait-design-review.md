# nop-ai-agent 长时工具等待与唤醒机制设计评审（nop 现状 × dsh × unreal）

> Status: resolved
> Date: 2026-09-28
> Scope: 重构 roadmap `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` M1 执行前评审——长时工具等待、等待期 steering 合流、卡死智能判定、占位协议形态、session 是否事件化五个决策点；三方机制精读（nop 源码 + dsh 全量 + unreal 全量）
> Conclusion: M1 前提成立（数十分钟 mvn install 为真实负载，现状长任务"5 分钟被杀或全轮阻塞"两难）。设计取向钉定为**等待优先 + 唤醒源仅 steering/心跳**：保留 round 同步等待为默认路径，steering 到达时模型空闲、以瞬时占位请求处理新输入后回到等待；心跳携带进度证据（沙箱 capturedRef 输出尾）供模型判定死锁，机械超时降级为兜底。**session 不事件化**——占位是派生态不入史，三条不变量（派生态渲染 / 原位插入 / checkpoint 差集恢复判定）使 M1 完全落在 nop 快照+journal 范式内。否决 unreal 全量事件循环、session 事件溯源、纯机械超时三案。
> 基线: nop=工作区 HEAD（2026-09-28 实测）；dsh=`477b4f4205`（`~/ai/deepseek-harness`，注意晚于既有比对基线 c291e7961a）；unreal=`1b9f778453f4`（`~/ai/unreal-agent`）

## Context

- 用户确认负载前提：mvn install 类数十分钟工具是真实场景，M1（异步工具执行模型重构）的收益前提由假设变为事实。
- 评审问题：①现状对长任务的真实行为；②dsh 的实现可否参考；③unreal 的占位协议是否必须全量照搬；④用户提出的"等待 + steering 时临时占位 + 继续等待"形态是否成立；⑤dsh 式不可变唯一历史（事件溯源）是否是本特性的前置。

## 1. nop 现状：长任务两难（含代码锚点）

- 默认 `toolTimeoutMs = 300_000`（5 分钟，`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngineConfig.java:152`）；超时经 `orTimeout` 转错误 tool result（`AgentToolDispatcher.java:271-283`）——数十分钟 build 现状**跑不完**。
- 调大/关闭超时则 `awaitToolFutures` 的 `allOf().get()` 阻塞整轮（`AgentToolDispatcher.java:304-319`，plan 280 已改为可中断 get）——期间模型零推理、无进度可见性，steering 按既有语义等至 round 边界 drain（`ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md` §5.2）。
- 崩溃恢复：无相位级工具状态；开放 tool_call 依赖 `TOOL_EXECUTION` checkpoint（callId 幂等键唯一约束，`reliability/CheckpointType.java:26`）差集判定，孤儿合成收尾归 autonomous roadmap WI10。
- **进度证据钩子在位**：沙箱执行以独立线程增量 drain 输出到 `capturedRef`（`NoOpSandboxBackend.java:101-113`、`DockerSandboxBackend.java:255`）——心跳可携带输出尾，无需新增采集机制。

## 2. dsh 机制精读：与 nop 同范式，对本问题无解可借

- step 内同步等全部工具：`executeToolCalls` 被 step await（`packages/core/agent-loop/src/agent.ts:517-520`）；parallel 池 `Promise.race` 逐个收、排空才返回（`packages/core/agent-loop/src/tool-calls.ts:199-246`）。
- steering = next-step 队列边界消费：`steer()` 仅 splice 入 inbox（`agent.ts:154-169`），运行中不 latch 不打断（`wakeDriver` `agent.ts:214-224`），当前 step 全部工具跑完后 `preStep` 才 claim（`agent.ts:271`）——与 nop round 边界 drain 同类。
- 等待中模型可见性为零：无心跳机制（grep heartbeat 零命中）；卡死保护纯机械 `packages/guard/timeout-policy`。
- 可借鉴的是配套件而非主机制：abort 时"已启动提交真实结果 + 未启动合成 `aborted before dispatch` 结果"的有序排空纪律（`tool-calls.ts:238-260`）；`isConcurrencySafe`/exclusive 分组（对位 roadmap WI17）。

## 3. unreal 机制精读：唯一有等待中唤醒的一方，但占位入史有隐患

- 事件循环 select 四类唤醒源（`harness/coordinator/loop.go:118-160`）：①工具完成即唤醒（`finishToolCall` → `availableInputs++`，`loop.go:680-691`；1 秒 grace 批合）；②外部输入置 `callModel=true`（`loop.go:417-427`）；③心跳 timer（默认 10 分钟，`cmd/internal/agentrunner/run.go:174`；payload 仅运行清单 + elapsed，`loop.go:282-304`）；④steering 打断在飞 LLM（`interruptModel`）。
- 占位协议：工具调度即以 `ToolCallRunningPayload` 占位进 stagedSuffix（`scheduleToolCall` → `loop.go:803-806` → `addToolResultToLocalState:744-749`；payload 定义 `contextbuilder/builder.go:16`），完成后同 callId 从 stagedSuffix 删除再追加真实结果（`builder.go:103-123`）。
- **隐患（实测）**：占位随 Turn `Commit()` 进入 committedPrefix 后，真实结果只能追加到 stagedSuffix——后续请求中同 callId 的占位与真实结果**双份共存**（测试断言即如此：`harness/coordinator/submission_test.go:273` 占位 + `:290` 真实；`responsesapi/request.go` 转换无去重）。provider 对双份 tool_result 的容忍度未验证。
- unreal 心跳只有 elapsed + 清单，**无输出进度**——判"死循环"证据不足，nop 需超越而非照搬。

## 4. 三方对比表

| | nop 现状 | dsh（477b4f4205） | unreal（1b9f778453f4） |
|---|---|---|---|
| 工具等待 | round `allOf().get()` 阻塞 | step 内池排空（同类） | 事件循环不阻塞 |
| 等待中 steering | round 边界 drain | next-step 边界 claim（不打断） | 打断在飞 LLM + 占位重建 |
| 等待中模型可见性 | 无 | 无 | 心跳 10min + 完成即唤醒 |
| 卡死判定 | toolTimeoutMs 5min 机械 | timeout-policy 机械 | 心跳交模型判（但无进度证据） |
| 占位协议 | 无 | 无 | 有（入史 + staged 内替换，commit 后有双份隐患） |

## 5. 设计裁定：等待优先 + 唤醒源仅 steering/心跳（用户确认）

- **保留 round 同步等待为默认路径**——工具自然完成走既有 round 边界回填，不采用 unreal 的"单工具完成即唤醒"（避免 round 语义解体与 turn 膨胀）。
- **steering 到达时模型是空闲的**（阻塞在工具上、无在飞 LLM 请求）——不需要 unreal 的 `interruptModel` 取消机制；构造含运行中工具占位的**瞬时请求**处理新输入，处理后回到等待，迟到结果照常回填。这是最小改造面，即用户提出的形态。
- **心跳带进度证据**：运行清单 + elapsed + 沙箱 capturedRef 输出尾——模型看到"40 分钟无新输出"才能判死锁；心跳判断为主路径，`toolTimeoutMs` 按工具类型分层降为兜底。
- 等待实现不需 event loop 重写：等待期保持 mailbox 可见 + 心跳 timer 即可（Java 侧等待可中断化或 CompletableFuture 组合，归 WI2 裁定）。

## 6. 事件化裁定：非必须，也非最简

- dsh 事件化是其"log 即真相、消息是投影"范式（`agent.ts:654` `deriveMessages()`）的自洽产物：占位/替换在投影层消解。代价是投影/重放机器、claim 即删除（steer 消息消费即消失）、写放大。99 总报告 D9 已裁定三方真相模型正交、nop 恢复纵深领先——不构成迁移理由。
- 本特性三样持久物在 nop 现有范式内全有位置：steering/心跳消息是**事实**（append 消息即可）；"哪些 tool_call 还开着"是**派生态**（消息列表"有 tool_call 无 tool_result"即 running，占位由请求构造期渲染，天然不落盘）；崩溃恢复开放集合 = 快照消息 × `TOOL_EXECUTION` checkpoint 差集（`CheckpointType.java:26`），已有机制。
- **三条不变量**（WI3 交付物必须满足）：①占位仅在请求构造期存在，不入 session 历史；②真实结果按 callId 原位插入 tool_call 之后（非尾部追加），规避 unreal 双份形态；③开放 tool_call 集合可由快照 × checkpoint 差集判定。
- 事件化真正有用的场景是重放保真/多投影（dsh 流式整流记录）——归 WI23 流式评估；WI14（快照写放大）与本特性正交，不捆绑。

## Conclusion

- **采纳**：等待优先 + steering/心跳双唤醒源 + 派生态占位 + 原位插入 + 心跳带进度 + 超时分层兜底。M1 改造完全落在 nop 快照+journal 范式内，不触碰真相模型。
- **否决 1**：unreal 全量事件循环（含完成即唤醒、输入记账不变量）——改造面大，且等待期唤醒场景下模型空闲，取消机制用不上。
- **否决 2**：session 事件溯源——本特性无需重放保真；迁移成本覆盖恢复/发散检测/接管锁全链。
- **否决 3**：纯机械超时（现状/dsh 路线）——对数十分钟真实负载直接不可用，且无法判死锁。
- **后续工作**：裁定细化归 roadmap WI2（provider 契约实测等四项必裁），交付归 WI3/WI4/WI5（已按本评审修订），均指回 `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md`。

## Open Questions

- [ ] provider 契约实测：Anthropic"tool_result 必须紧跟 tool_use"约束下，steering 插队请求的合法构造形态；以及（若误用 unreal 式追加）同 callId 双份 tool_result 的实际容忍度——归 roadmap WI2 必裁①
- [ ] 等待期心跳间隔默认值（unreal 10min 对 mvn 场景偏长？）与分层超时的参数面——归 WI2 必裁②

## References

- `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md`（M1 编排，已按本评审修订 WI2-WI5）
- `ai-dev/analysis/2026-09/2026-09-25-unreal-agent-vs-nop-ai-agent-comparison.md`（unreal 比对基报告）
- `ai-dev/analysis/compare-agent-design/99-overall-comparison.md`（三方总报告，D9 真相模型裁定）
- `ai-dev/analysis/compare-agent-design/dsh-D1-agent-loop.md`（dsh 注入语义基线）
- nop：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentToolDispatcher.java`、`engine/DefaultAgentEngineConfig.java`、`engine/AgentExecutionContext.java`（`messages` 可变列表 :26/:88）、`security/NoOpSandboxBackend.java`、`security/DockerSandboxBackend.java`、`reliability/CheckpointType.java`
- dsh：`~/ai/deepseek-harness` @ `477b4f4205`——`packages/core/agent-loop/src/agent.ts`、`tool-calls.ts`、`packages/guard/timeout-policy`
- unreal：`~/ai/unreal-agent` @ `1b9f778453f4`——`harness/coordinator/loop.go`、`submission_test.go`、`harness/contextbuilder/builder.go`、`harness/llm/responsesapi/request.go`、`cmd/internal/agentrunner/run.go`
