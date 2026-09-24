# unreal-agent vs nop-ai-agent 内在设计对比报告

> Status: resolved
> Date: 2026-09-25
> Scope: nop-ai-agent 与 unreal-agent（Unreal Labs 的 Go 语言 async-first agent harness）在 agent 内在设计上的单方比对；覆盖与既有 dsh/pi 对比同构的 D1–D10 十维度，重点是 unreal 独有、且 dsh/pi 对比未覆盖的增量
> Conclusion: 10 维裁定 nop 领先 6（D2/D3/D4/D5/D8/D10）、unreal 领先 1（D6 前缀缓存）、等价或不可比 3（D1/D7/D9 范式正交）。unreal 的工程价值集中在四件 dsh/pi 对比中未出现的设计：①异步工具调用占位协议（占位 tool result + 结果原位替换 + 心跳即输入）；②steering 打断-重建语义；③输入记账不变量 + redelivery 幂等；④工具执行耐久性纪律（翻译决策持久化 + 相位检查点状态机 + "结果不可判定即失败"恢复纪律）。外加 per-provider 缓存键放置三形态，强化既有 D6 结论。
> 基线: nop=`571412f0b2`（本仓库，2026-09-25 spot check）；unreal=`1b9f778453f411c029b39b85102aaefb95e7e48d`（`~/ai/unreal-agent`，2026-09-23）

## Context

- 既有三方对比（`ai-dev/analysis/compare-agent-design/`，28 份产物）只覆盖 deepseek-harness 与 pi；用户要求重构 roadmap 综合考虑 dsh 与 unreal-agent 的比对结果，故先补齐 unreal 比对。
- 本报告由两个独立研究子代理对 `~/ai/unreal-agent` 全量实读源码后汇总（执行面 + 工具/上下文/LLM 面各一份），行为语义一律以代码为准，README 仅作导航。
- 与既有对比的关系：本报告不重复 dsh/pi 报告已覆盖的结论，只在维度裁定表中对齐口径，增量部分聚焦 unreal 独有设计。

## unreal-agent 总体形态

单线程 coordinator 事件循环（`harness/coordinator/loop.go`，外部仓库路径，下同）驱动"LLM turn → 工具同步翻译 → 可序列化 operation → actor 化 operation manager → 结果回流下一 turn"的异步循环；持久层是 append-only JSONL session log。没有 ReAct 式同步迭代计数、没有 hook 点、没有内置故障转移——循环推进与终止由持久化不变量（未交付输入数、operation 终态集合）表达。

## 维度裁定表（D1–D10）

| 维度 | 裁定 | 一句话依据 |
|---|---|---|
| D1 内部 agent loop | 不可比（范式正交） | unreal 输入记账不变量驱动、无迭代上限、StopWhenIdle/StopHard 终止；nop 预算计数 + 判定器。服务场景不同（长时无人值守 vs 治理自主执行），互不可直译 |
| D2 扩展点 | nop 领先 | unreal 仅 2 个同步回调（sessionstore.Observer + responsesapi Trace）+ 六接口注入，无 hook/middleware/事件扩展体系；nop 12 生命周期点 + 4 执行点 + filter chain |
| D3 事件类型与触发 | nop 领先 | unreal 无事件总线——观测即持久化事实流（session items 逐条同步回调）+ Trace 窥视孔；nop 有类型化事件枚举 + 发布器 + 异步信封（但类型化程度仍低于 dsh/pi，该缺口由既有报告覆盖） |
| D4 容错性 | nop 领先 | unreal 单 provider 启动时绑定、无 failover/熔断/账号链；nop 多通道切换（重试→熔断→账号链→provider 链→tier 路由）。unreal 增量仅在重试细节（见 ⑤-9） |
| D5 自动切换 | nop 领先 | unreal 零内置（grep circuit/breaker/failover 非测试零命中；codex 客户端显式拒绝刷新过期 token） |
| D6 前缀缓存利用 | unreal 领先 | unreal 三件套：committed-prefix/staged-suffix 追加式上下文 + 确定性 JSON 序列化 + per-provider 缓存键放置（prompt_cache_key 字段 / x-session-id 会话亲和 header / openrouter cache_control TTL 断点），并有 CachedInputTokens/CacheWriteInputTokens 归一化观测字段；nop 仅解析资产在位（见 ③-6） |
| D7 工具系统 | 等价（互补） | unreal 领先在执行耐久性（翻译决策持久化、相位检查点状态机、保守恢复、输出预算 schema 内建）；nop 领先在安全纵深（7-checkpoint + 沙箱 fail-closed + 修复链 + DSL 声明）。安全面 unreal 完全外包给部署容器 |
| D8 上下文工程与压缩 | nop 领先 | unreal 零实现——contextbuilder 预留 Change/Report（omitted/truncated/compacted）申报契约但 Build 恒返空 Report，无 token 预算，压缩仅 TurnCompaction 类型占位；nop 三层压缩 + 引用式 + spill store |
| D9 会话持久化与恢复 | 等价（范式正交） | unreal append-only JSONL 单文件 + 确定性重放重建（无上下文快照）+ 撕裂写自愈 + 格式版本硬拒绝 + fork 剥离 operation；nop 快照 + journal + 接管锁 + 发散检测。双方各有独有恢复语义 |
| D10 多代理与子代理 | nop 领先 | unreal 零内置（无 subagent 派生；最接近的是 remote job operation 通道且生产二进制未注入 handler）；nop team.flow 编排全栈 |

## 关键机制对照（只列重构相关增量）

### 1. 异步工具调用占位协议（D1×D7，unreal 独有）

- 占位文案 `ToolCallRunningPayload`（`harness/contextbuilder/builder.go:16`）："Tool call is still running. Its result arrives in a later turn: continue with independent work, or end your turn to wait for it."——工具未完成时以占位 tool result 进入请求，模型被系统性引导并行发调用而非串行等待。
- `AddToolResult(callID, payload, running=true)` 注入占位、真实结果到达时**原位替换**（先删同 callID 旧占位，`builder.go:103-123`），同一 turn 多次唤醒不堆积。
- 工具完成使 `availableInputs++` 驱动新 turn（`loop.go:680-691`）；多个工具并行在飞时 1 秒宽限期做完成批合（`loop.go:28, 247-252`）。
- 对照 nop：`AgentToolDispatcher` 在 round 内同步/并行执行全部工具后统一回流（`ReActAgentExecutor` round 边界语义），无"运行中"状态可见性——长时工具阻塞整个 round，模型无法在等待期间继续推理。
- 吸收方向：为 nop 工具执行引入可选的异步占位模式（先落占位结果、完成事件唤醒下一轮、原位替换），需与 maxIterations 预算、7-checkpoint 安全链、W3 双层中间件裁定兼容。

### 2. steering 打断-重建（D1，unreal 独有）

- 新输入到达时 `callModel=true`（`loop.go:417-427`），下一次请求前 `interruptModel()` 取消在飞 LLM 请求（`loop.go:350, 306-311`）；迟到响应按 turnID 不匹配显式丢弃且不消耗记账（`loop.go:153-156`）。
- 打断时未完成工具调用以占位 tool result 编入替换请求（与机制 1 同一协议），新输入与部分完成的工具状态合流一致。
- 注入本身按 `ItemInput` 持久化，崩溃恢复后 steering 不丢失（重放重建 pending 记账）。
- 对照 nop：steering 为 round 边界 drain（2026-09-15 修复已将该语义写入 owner doc，mid-round 为已登记 successor；见 `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md` §5.1/§5.2）。本机制即该 successor 的参考实现：打断边界从 round 提前到在飞请求。
- 吸收方向：mailbox 注入升级为可打断在飞 LLM 调用（nop 已有 `DeferredAckMailbox`/`IMailbox` 通道与请求取消原语），合流协议复用机制 1 的占位替换。

### 3. 输入记账不变量 + redelivery 幂等（D9×D1，unreal 独有）

- 循环推进/终止归结为"未交付输入数"（`availableInputs - deliveredInputs`，`loop.go:320-322`）——正确性记账而非资源预算；工具完成、外部输入、心跳全部走同一记账。
- 外部输入带调用方全局唯一 ID；`Inbox` 是 session 级内存去重（`harness/inbox/local.go:72-77`），启动时用持久历史恢复的 ID 预填（`ResumeState.ExternalInputIDs`）。投递契约 at-least-once + store/inbox 双层幂等：输入先持久化再改状态（`loop.go:390-415`），崩溃后同 ID 重投恰好一次生效。
- 对照 nop：mailbox 体系（`io.nop.ai.agent.message.IMailbox` 等）有排队与延迟确认，但外部输入无显式幂等 ID 契约；channel 场景（飞书连接器等）消息重复投递会重复触发执行。
- 吸收方向：为外部注入通道引入输入幂等键（session 级去重 + 持久化前置），契约对齐 at-least-once。

### 4. 工具执行耐久性纪律（D4×D7，unreal 独有）

- 翻译决策即持久化事件：`CallStatus` 与其初始化的 operations 原子绑定落盘（`harness/sessionstore/localfile/state.go:224-263` 硬校验不变式）。
- operation 是版本化相位状态机：每阶段先写 checkpoint 再发原语 dispatch（`harness/operation/operation.go:53-57`），事件 Source/CorrelationID 不匹配即 fail（`shell.go:250-264`）；9 种可审计原语（io.create 双 fsync、io.read 前后大小校验、进程组全生命周期管理等）。
- 恢复纪律："结果不可判定即失败"——shell 恢复遇"进程已启动但无退出记录"直接判 failed 不重跑（`shell.go:194-202`），副作用不可假设幂等。
- 对照 nop：checkpoint 体系有 TOOL_EXECUTION 条目与幂等键、发散检测，但工具执行本身不可分相位恢复；崩溃孤儿工具的合成收尾在既有建议中已登记（dsh-D4 建议 2），本机制是其相位级深化。
- 吸收方向：长时/高副作用工具（bash、文件写）引入相位检查点与保守恢复判定，与既有 idempotency_key 协同。

### 5. 心跳即持久化输入（D1×D8，unreal 独有）

- 仅剩工具在跑且超过阈值（默认 10 分钟）时，coordinator 向自己的 inbox 投递 Heartbeat 控制输入，payload 含运行中工具清单，成为发给模型的 user message（`loop.go:282-304`、`builder.go:72-77`），并驱动新 turn。
- 对照 nop：长工具等待期间模型完全无感知、无干预通道；WAIT_FOR 原语是条件挂起，不含"告知模型等待进展"语义。
- 吸收方向：长工具等待超阈值时构造心跳消息入上下文（模型可决定继续等待/放弃/并行），作为机制 1 的配套。

### 6. 前缀缓存工程化（D6，unreal 领先域，强化既有结论）

- 追加式双段上下文（committedPrefix/stagedSuffix）保证逐 turn 请求前缀字节级稳定；请求体确定性序列化（`json.Marshal(..., json.Deterministic(true))`）。
- 缓存键 = session ID，按 provider 三形态放置：`prompt_cache_key` 字段（openai）/ `x-session-id` 会话亲和 header（openrouter/fireworks）/ `cache_control: ephemeral 1h TTL` 断点前移（openrouter 扩展字段），配置见 `harness/llm/clients/*`。
- 观测闭环：`llm.Usage{InputTokens, CachedInputTokens, CacheWriteInputTokens, ...}` 从 `input_tokens_details.cached_tokens / cache_write_tokens` 归一化（`harness/llm/responsesapi/response.go:186-197`），`Raw` 保留原始 usage JSON 供方言翻译。
- 对照 nop（2026-09-25 live 核对 @ `571412f0b2`）：
  - `ChatUsage.cacheHitTokens/cacheCreationTokens/cacheMissTokens` 字段已存在（`nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/messages/ChatUsage.java:38-48`）。
  - `AnthropicDialect` 仅响应侧解析（`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/AnthropicDialect.java:369-381`）；请求侧 `cache_control` 仅 providerHints 透传（`:603-604`），引擎不构造断点。
  - 开箱零缓存观测：`AbstractLlmDialect.parseUsage:411-418` 已有 promptCacheHitTokensPath/promptCacheCreationTokensPath 通用配置通道（OpenAI/Ollama/Gemini 的 usage 解析走此基类方法），但仓库内无任何 provider 配置设置该 path；`ResponsesDialect` 自有 `parseUsageFromResponses:465` 无缓存字段接入。
- 吸收方向：请求侧断点引擎构造 + 缓存键 per-provider 放置 + 全 dialect 观测接线。注意与 `ai-dev/backlog/nop-ai-agent-autonomous-execution-improvement-roadmap.md` M0/M2（工具排序、记忆移出 system、cacheHitTokens 消费、cache_control 位置纠错）的划界——该 roadmap 承接基础项，本报告增量是放置策略框架与观测面扩展。

### 7. 工具输出预算 schema 内建（D7×D8，unreal 独有）

- `max_output_length` 是工具参数的一部分、模型按调用自选（默认 40000、上限 1000000，`harness/tool/static.go:90-98`）；截断头尾各半保留 + 精确字节数 + 全文落盘路径指针（`harness/operation/output.go:13-37`）；shell 完整 stdout/stderr 永久落盘在 operation 目录。
- 对照 nop：工具输出截断散落在各执行器（如 boundOutput 类逻辑），非 schema 声明参数；spill store 有全文保留能力但未与模型可见预算参数打通。
- 吸收方向：tool.xdef 增加模型可调输出预算参数 + 统一截断协议（头尾保留 + 字节数 + spill 路径指针回填）。

### 8. 会话存储恢复语义（D9，范式正交记录）

- 撕裂写自愈：追加前 truncate 到 committed 尺寸、读取只认最后换行、初始发布 temp+rename+目录 fsync（`harness/sessionstore/localfile/store.go:386-433`、`codec.go:88-94`）；格式版本硬拒绝（v1 显式报错）。
- fork：turn 边界切历史，继承的 tool-call status 剥离 operation 引用（不可派发），"对话分叉"与"副作用历史"严格分离（`state.go:388-412`）；生产路径无调用方（能力在 store 层，CLI 未暴露）。
- 对照 nop：DB/FileBacked 会话存储已有原子写（2026-09-14 修复）与接管锁、发散检测；nop 无 session fork 消费场景。撕裂写自愈对 nop 的 FileBacked 后端有参照价值但优先级低。
- 裁定：记录为反向参考，不列吸收项（nop 存储后端形态不同，收益有限）。

### 9. 重试细节增量（D4，小项）

- Retry-After 双源解析：响应头（秒数或 HTTP 日期）+ 错误 message 正文正则 "try again in N s"（`harness/llm/responsesapi/retry.go:56-84`）。
- `server_is_overloaded/slow_down` 独立长退避曲线（10s→60s，注释明确为 unattended 场景与 Codex CLI 分岔）；指数退避 ±20% 抖动。
- 分类器 fail-open（黑名单式，注释 "intentionally fails open, favoring retries"）。
- 对照 nop：`StandardRetryPolicy` 已有 retryAfterMs floor（W2e 落地）；错误 message 正文解析与 overloaded 独立曲线不存在。
- 吸收方向：小项，正则归类器补充 + overloaded 长退避曲线。

## Conclusion

- **裁定汇总**：nop 领先 6 / unreal 领先 1 / 等价或不可比 3。unreal 与 dsh/pi 的领先域不重叠——dsh 领先在流式/事件/重试持久化，pi 领先在缓存旋钮/类型化/分层扩展，unreal 领先在前缀缓存放置与**执行模型的异步耐久性**。
- **对 nop 最有价值的增量**（与既有 20 报告建议去重后）：①异步工具调用占位协议（含心跳即输入、steering 打断-重建）；②外部输入 redelivery 幂等；③长时工具相位检查点 + 保守恢复纪律；④per-provider 缓存键放置 + 全 dialect 缓存观测；⑤工具输出预算 schema 内建；⑥Retry-After 双源解析 + overloaded 长退避。
- **被否决的吸收**：单协议多 provider 配置变体（nop dialect + 故障转移体系更强，D5 nop 领先）；撕裂写自愈（存储后端形态不同，nop 已有原子写）；fork 剥离 operation（nop 无 fork 消费场景）；无 hook 极简扩展面（nop 扩展矩阵是资产不是负债）。
- **后续工作**：指向 `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md`（重构编排，与 `ai-dev/backlog/nop-ai-agent-autonomous-execution-improvement-roadmap.md` 划界并行）。

## Open Questions

- [ ] 异步占位模式与 7-checkpoint 安全链的兼容形态（占位结果是否过 POST_CALL guardrail）——重构 roadmap M1 设计裁定项
- [ ] steering 打断-重建与 round 边界 drain 的过渡策略（全量切换 or 按工具类型可选）——重构 roadmap M1 设计裁定项

## References

- `~/ai/unreal-agent`（HEAD `1b9f778453f411c029b39b85102aaefb95e7e48d`，外部仓库，本文全部 unreal 锚点出处）
- `ai-dev/analysis/compare-agent-design/99-overall-comparison.md`（dsh/pi 三方总报告，维度口径与划界基准）
- `ai-dev/backlog/nop-ai-agent-autonomous-execution-improvement-roadmap.md`（在途承接 D1/D4/D6/D8 的 20 项改进）
- `ai-dev/design/nop-ai-agent/`（nop 侧 owner docs）
