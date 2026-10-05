# WI1 吸收候选基线复核与划界裁定表（refactor roadmap × autonomous roadmap）

> Status: resolved
> Date: 2026-10-02
> Scope: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` M1–M7 全部吸收候选 × `ai-dev/backlog/nop-ai-agent-autonomous-execution-improvement-roadmap.md` 20 项去重裁定
> Conclusion: 23 项候选（WI2–WI23 + WI25）全部维持本 roadmap 承接（无一项已落地、无一项移交 autonomous、无一项裁定移出）；三项必裁——① pi-D4 建议2 已结构性满足登记不做；② autonomous WI8 移交 refactor WI20 合并执行；③ WI21 与 autonomous WI3 正交分层（解析面/消费面）各自保留。

## Context

refactor roadmap WI1 要求在 M1 动工前完成两件事：对 M1–M7 全部吸收候选按执行时 HEAD 复核（roadmap 基线快照 2026-09-25），与 autonomous roadmap 20 项逐项去重裁定。裁定结论四分类沿用 roadmap WI1 原枚举：**本 roadmap 吸收 / 已落地无需做 / 移交 autonomous / 裁定移出**。本文档是后续各 WI plan 的 Current Baseline 引用锚。

## 基线复核（执行时 HEAD `86ad2c389d`，2026-10-02）

- 2026-09-25 之后 nop-ai 侧落地仅 RAG/向量相关（K1 `c05c2c7600` 在 `nop-ai-core` embedding 面；K2 `97be7d367a` 跨 `nop-ai-core` vectorstore 面 + 新建 `nop-ai-rag` 模块）与 nop-stream/nop-jq/nop-metadata，未触碰 `nop-ai-agent`、`nop-ai-api`（ChatUsage/ChatMessage/ILlmDialect）、`nop-ai-shell` 执行模型面。roadmap 基线快照仍然有效，无需修订任何 WI 的前提。
- autonomous roadmap 20 项全部未勾选（`audit-rounds: 0`），无任何一项已落地或已移交。

## 三项必裁

### 必裁① pi-D4 建议2 "重试前摘除失败响应"——已结构性满足，登记不做

pi 建议：若失败响应已入上下文，重试前应摘除。live 复核结论：**nop 的 LLM 失败响应从不入上下文**，无需摘除动作。

- retry 循环完全在 `LlmCallCoordinator.runRetryLoop` 内部（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/LlmCallCoordinator.java:206`），失败响应只存在于 `RetryState.attemptResponse`，重试耗尽走 `fallbackExhausted` 异常路径（`:170-172`）抛 `NopAiAgentException`；
- LLM 成功响应产生的 assistant 消息在 `ReActAgentExecutor.recordLlmTurn`（`ReActAgentExecutor.java:959` `ctx.addMessage(assistantMsg)`）追加，唯一调用点 `:711` 前置 `:707` `isSuccess()` 守卫；
- `:842` 另有 input guardrail block 合成 assistant 消息路径，属 guardrail 拦截语义（非 LLM 失败响应），与本裁定无关。

登记：**已满足（satisfied-by-design）**。后续 WI3/WI5 改造消息追加语义时不得引入"失败响应入 ctx"的新路径。

### 必裁② WI20（refactor）× autonomous WI8 同靶——合并归属 refactor WI20

两候选均改 `AnthropicDialect` 请求侧 cache_control：

- autonomous WI8 = 位置纠错 Fix：`convertMessage` 中 cache_control 从 message map 顶层改 content block 级（现状 `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/AnthropicDialect.java:603-604` 仅顶层 put）。
- refactor WI20 = per-provider 缓存键放置框架：字段/会话亲和 header/TTL 断点 + 请求侧断点引擎构造，替换 providerHints 手工透传。

裁定：**合并执行，归属 refactor WI20**。理由：WI20 的断点引擎天然要求正确的 content-block 级放置，顶层→块级纠错是其子集；分离执行会两次触碰同一代码区域且先行的纠错会被框架重建。refactor roadmap 先行执行，WI20 落地时顺带完成放置纠错语义并在交付中显式覆盖 autonomous WI8 的验收点（content block 级放置 + 测试）。若 autonomous roadmap 在 WI20 之前动工 WI8，则按 refactor roadmap Cross-Cutting 协同规则由 WI20 复核吸收其落地形态。

### 必裁③ WI21（refactor）× autonomous WI3 分界——正交分层，各自保留

- 解析面（refactor WI21 承接）：全 dialect 缓存 usage 解析接线——OpenAI/Ollama/Gemini 默认 path 配置化（`LlmResponseModel.promptCacheHitTokensPath/promptCacheCreationTokensPath` 生成类在位 `_LlmResponseModel.java:42-52`，仓库内无 provider 默认值，开箱不生效）、`ResponsesDialect.parseUsageFromResponses:465` 补 cache 字段、Usage Raw 原始保留。
- 消费面（autonomous WI3 保留）：`UsageRecord` 扩展 cache 字段（现状 `UsageRecord.java:30-41` 无 cache 字段）+ `IUsageRecorder`/事件 payload 暴露缓存命中统计。

裁定：**无重复，正交互补**。共享契约点为 `ChatUsage.cacheHitTokens/cacheCreationTokens/cacheMissTokens`（`nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/messages/ChatUsage.java:38-48`）：WI21 保证该字段在各 dialect 下被填充，WI3 保证其被消费。Anthropic 响应侧解析已在位（`AnthropicDialect.java:369-381`），故 WI3 不被 WI21 阻塞；WI21 不做任何消费面扩展。两 WI 执行时互不吸收、互不阻塞。

## 裁定表：M1–M7 候选 × autonomous 20 项

> 分类：吸收 = 本 roadmap 承接；已落地 = 无需做；移交 = 归 autonomous；移出 = 不做。所有候选经 HEAD 复核无一"已落地"。

| Refactor WI | 主题 | 裁定 | 相关 autonomous WI | 去重说明 |
|---|---|---|---|---|
| WI2 | 异步占位模式设计裁定 | 吸收 | 无重叠 | autonomous 20 项无异步工具执行/长时等待主题 |
| WI3 | 占位 tool result 协议 | 吸收 | 无重叠 | 同上；消息追加语义改造时遵守必裁①约束 |
| WI4 | 心跳即输入 | 吸收 | 无重叠 | autonomous 无心跳/进度证据主题 |
| WI5 | steering 双相位唤醒（Phase A） | 吸收 | 无重叠 | autonomous 定位明确排除 steering 交互场景 |
| WI6 | 长时工具相位检查点+保守恢复 | 吸收 | WI10（接触） | 互补分层：WI10=恢复时孤儿工具合成"结果未知"响应；WI6=工具执行相位检查点+按相位续跑。同在 reliability 崩溃恢复面，后动者复核前者 TOOL_EXECUTION 消费形态 |
| WI7 | 外部输入 redelivery 幂等 | 吸收 | 无重叠 | autonomous 无 mailbox/幂等主题 |
| WI8 | 注入持久化+语义三分+队列事件化 | 吸收 | 无重叠 | autonomous 定位排除 steering 持久化 |
| WI9 | AgentEvent payload 类型化 | 吸收 | WI3（接触） | 分层不冲突：WI9=事件 payload 契约面；autonomous WI3=缓存统计消费（挂新 payload 数据项）。若 WI9 先落地，WI3 的事件部分按类型化契约落地 |
| WI10 | 订阅者失败上报通道 | 吸收 | 无重叠 | autonomous 无事件订阅面主题 |
| WI11 | veto 死配置收敛 | 吸收 | 无重叠 | autonomous 无扩展面治理主题 |
| WI12 | 修复前后差异审计 | 吸收 | 无重叠 | autonomous 无工具修复链主题 |
| WI13 | hook 注册白名单类型化 | 吸收 | 无重叠 | autonomous 无 hook 注册面主题 |
| WI14 | session 快照写路径增量化+条目版本化 | 吸收 | WI9（无重叠接触） | autonomous WI9 在 checkpoint journal 面（RETRY_PLAN 条目），WI14 显式排除 journal（已 append-only）；WI14 内建"收益不成立则登记移出"裁定出口 |
| WI15 | 压缩产物条目化 | 吸收 | WI16/WI20（无重叠接触） | autonomous WI16=摘要算法、WI20=压缩计量事件化；WI15=压缩产物在 session 历史的存储形态，不同层 |
| WI16 | 工具输出预算 schema 内建 | 吸收 | WI1（交互登记） | autonomous WI1 工具定义排序针对前缀稳定；WI16 改变工具定义面，plan 必须评估前缀影响（roadmap 已标注波及面提示），非重复 |
| WI17 | 并发安全标记+路径串行 | 吸收 | 无重叠 | autonomous 无工具并发主题 |
| WI18 | deferred 工具引入机制裁定 | 吸收 | 无重叠 | 纯裁定项 |
| WI19 | continuable 子代理+传输抽象裁定 | 吸收 | 无重叠 | 纯裁定项；D10 为 nop 领先域 |
| WI25 | 工具级后台模式 runInBackground | 吸收 | 无重叠 | autonomous 无后台任务对象化主题 |
| WI20 | per-provider 缓存键放置框架 | 吸收 | WI8（同靶） | 必裁②：合并归属 refactor WI20，见上 |
| WI21 | 缓存观测全 dialect 接线 | 吸收 | WI3（正交） | 必裁③：解析面/消费面分层，见上 |
| WI22 | Retry-After 双源+长退避+正则归类器 | 吸收 | WI4（接触） | 互补：autonomous WI4=CONTEXT_OVERFLOW 枚举值+直路由压缩；WI22=正则归类机制+退避曲线。同改 LlmErrorClassifier 面，后动者复核前者，WI22 不得重写 WI4 直路由 |
| WI23 | 流式管线可行性评估 | 吸收 | 无重叠 | autonomous 定位排除流式；本项纯评估不实施 |

## autonomous WI1–WI20 反向索引

| Autonomous WI | 主题 | 裁定覆盖 |
|---|---|---|
| WI1 | 工具定义显式排序 | 无对象（WI16 前缀影响评估时交互登记） |
| WI2 | 记忆移出 system 区域 | 无对象 |
| WI3 | cacheHitTokens 消费 | 被 WI21 裁定覆盖（分界：消费面保留 autonomous，见必裁③） |
| WI4 | CONTEXT_OVERFLOW 独立分类 | 被 WI22 裁定覆盖（互补分层保留 autonomous） |
| WI5 | 溢出→压缩→重试恢复环 | 无对象 |
| WI6 | 双预算参数化 | 无对象 |
| WI7 | maxContextTokens 动态刷新 | 无对象 |
| WI8 | Anthropic cache_control 位置纠错 | 被 WI20 裁定覆盖（合并归属 refactor WI20，见必裁②） |
| WI9 | 重试预算持久化（RETRY_PLAN） | 无对象（WI14 已显式排除 journal 面） |
| WI10 | 崩溃孤儿工具合成收尾 | 被 WI6 裁定覆盖（互补分层保留 autonomous） |
| WI11 | 辅助调用缓存隔离 | 无对象 |
| WI12 | purpose 调用分类标记 | 无对象 |
| WI13 | max-tokens 粘性语义 | 无对象 |
| WI14 | 取消原因分型 | 无对象 |
| WI15 | 静默溢出检测 | 无对象 |
| WI16 | 增量摘要 | 无对象（WI15 存储形态不同层） |
| WI17 | Layer2 切割点边界约束 | 无对象 |
| WI18 | Layer3 摘要前缀复用 | 无对象 |
| WI19 | Tool 结果可提议停止 | 无对象 |
| WI20 | 压缩 token 计量事件化 | 无对象（WI15 存储形态不同层） |

## Conclusion

- 23 项候选全部**本 roadmap 吸收**：基线复核无一已落地；无候选移交 autonomous；无候选裁定移出（WI14/WI18/WI19/WI23 的裁定出口与 WI5 Phase B 后置为工作项内建语义，非本次划界移出）。
- 必裁①登记已满足（不做）；必裁② autonomous WI8 移交 refactor WI20 合并执行（已加注 autonomous roadmap）；必裁③ WI21/WI3 正交分层各自保留。
- 协同登记四处（非重复）：WI6×aWI10、WI9×aWI3、WI22×aWI4、WI16×aWI1——两 roadmap 涉及同一文件/类面，后动者复核前者落地形态。

## References

- `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md`（WI1 委托方）
- `ai-dev/backlog/nop-ai-agent-autonomous-execution-improvement-roadmap.md`（划界对象）
- 锚点文件：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/LlmCallCoordinator.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/AnthropicDialect.java`、`nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/messages/ChatUsage.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/usage/UsageRecord.java`
