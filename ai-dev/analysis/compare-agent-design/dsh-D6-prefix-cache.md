# dsh-D6 前缀缓存利用对比

> Status: resolved
> Date: 2026-09-12
> 基线: nop=800baf32da（nop-ai 模块与 c585459f83 diff 为空）、dsh=c291e7961a（分析当日实测）
> 引用: 00-dimension-matrix.md（WI2，D6 子机制 D6-1..D6-5；其 Open Question"D6 nop 侧现状待核查"由本报告兑现）、02-terminology-map.md（T12）; 机制事实引用 03（链 1/P6、快照消息注入）
> Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-context-compaction-economics.md`

## ① 结论摘要

- 总裁定：**对方领先**。dsh 的"构造性前缀稳定"是完整的工程体系（section order 约定 + 动态走 user 快照 + 工具 canonical 排序 + EpochHeader 记账 + 压缩字节级重放）；nop 五项核查中四项不存在或仅部分存在。
- **nop 侧现状核查结论**（兑现 WI2 矩阵 Open Question）：①显式断点=通道存在无用户（providerHints 可透传 cache_control 但全仓无生产写入方，且 AnthropicDialect 放置位置与官方 block 级不一致）；②前缀稳定=部分存在（执行内 append-only，但记忆注入进 system 区域 + 工具定义 HashSet 序两个不稳定源）；③压缩×缓存交互=不存在（压缩 clear+addAll 作废前缀）；④一次性调用标记=不存在；⑤命中观测=模型/解析层完整但 agent 层未消费。
- dsh 关键差异：动态上下文"快照取代制"（新快照 replace 旧快照出 surface）保证 system 区域字节稳定；压缩摘要调用对齐主对话前缀使 KV cache 保持温热。
- 可吸收增量建议一句话：nop 最小改动路径是修两个不稳定源（工具序排序 + 记忆移出 system）并消费已有的 cacheHitTokens 观测（见 ⑥）。

## ② nop 侧机制与锚点（现状核查）

2026-09-12 逐项核查（02 T12 四个 Open Question 全部落地）：

- **D6-1 显式断点**：部分存在（通道空置）。`ChatMessage.providerHints` Javadoc 明示例举 `{"cache_control":{"type":"ephemeral"}}`（`nop-ai-api/.../chat/messages/ChatMessage.java:40-42`）；`AnthropicDialect.convertMessage` 透传该 hint（`nop-ai-core/.../dialect/AnthropicDialect.java:603-605`）——但放在消息 map **顶层**而非 content block 内（与 Anthropic 官方 block 级位置不一致，透传不等于正确实现）；全仓 grep 无任何生产代码调用 setProviderHints 写 cache_control。`ChatOptions` 无缓存字段（`ChatOptions.java:33-596`）。
- **D6-2 前缀稳定性**：部分存在。执行内 append-only：`buildBaseExecutionContext` 只在执行 setup 构造一次 [system prompt + 记忆 system message]，循环内 `new ChatRequest(new ArrayList<>(ctx.getMessages()))` 复用（`engine/AgentSessionLifecycle.java:135-169`、`engine/ReActAgentExecutor.java:626`）；steering 走尾部追加不破坏前缀（:912-932）。两个不稳定源：①记忆注入为第二条 ChatSystemMessage（:153-162）——记忆内容用户可变，跨 turn 前缀漂移；②工具定义顺序来自 `Set<String>`（HashSet 迭代序，`model/_gen/_AgentModel.java:208`）+ 过滤链无排序（`engine/AgentToolPlanResolver.java:57-132`）——跨 JVM 无保证。
- **D6-3 压缩与缓存交互**：不存在。压缩成功后 `clear()+addAll(compactedMessages)` 前缀彻底作废（`engine/AgentCompactionCoordinator.java:130-132`）；无任何 KV 级复用设计。唯一的消息级意识：Layer3FullSummary 的 head anchors（system+首条 user goal）"prepended verbatim and object-reused"以保留 KV 复用（`compact/Layer3FullSummaryStrategy.java:22-38,162-178`）——但摘要调用本身是独立请求，与主对话中段之后前缀无关。
- **D6-4 一次性调用标记**：不存在。LlmCompletionJudge/Layer3 摘要调用均无禁写/标记（`completion/LlmCompletionJudge.java:143-164`）；legacy `AiChatOptions.disableCache` 是本地响应缓存开关且 deprecated，与 provider 前缀缓存无关（`CORE/api/chat/AiChatOptions.java:63,445-450`）。
- **D6-5 命中观测**：模型/解析层完整——`ChatUsage.cacheHitTokens/cacheCreationTokens/cacheMissTokens` + `getCacheHitRate()`（`ChatUsage.java:18-129`）；AbstractLlmDialect 按模型配置路径解析 + AnthropicDialect 硬编码解析 cache_read/creation_input_tokens（`CORE/dialect/AbstractLlmDialect.java:410-421`、`AnthropicDialect.java:369-381,508-509`）。缺口：agent 执行层 usage 累计只加 prompt+completion（`LlmCompletionJudge.accumulateTokens:218-231`），cacheHitTokens 无消费无出口。

## ③ 对方侧机制与锚点

按 02 T12 与 03 §2.2（P6），对比增量：

- **构造性稳定**：`PromptSection` 数值 order 约定（-1000=harness 身份/0=persona/1000-2900=工具指引，同 scope 重名抛错，`packages/core/system-prompt/src/index.ts:53-75`）；动态上下文**不进 system prompt**——渲染成 user-role 快照消息（"This snapshot supersedes earlier…"，:77-85,236-240），`RuntimeContextProjection` 只保留 surface 上最后一份快照、新快照 replace 旧快照（`packages/core/agent-loop/src/runtime-context.ts:23-75`）；工具表 canonical 排序（toolOrder/字典序，system-prompt:197-232）。
- **请求记账**：`EpochHeader`（request/header 事件）持久化 config+system+tools 规范快照，headerEquals 判 initial/resume/change（`packages/core/session/src/types.ts:201-228`）；adapter 物化默认值从下次提案剥除防污染比较（`agent.ts:54-61`）。
- **压缩×缓存**：压缩摘要调用 `summarize()` 请求前缀=对话自己的 system+tools+消息前缀，压缩指令作最后一条 user 消息→**provider 前缀缓存保持温热**（字节级重放，`packages/compaction/compaction-basic/src/index.ts:226-246`、`summarizer.ts:27-28,112-116`）。
- **辅助调用标记**：`GenerateOptions.purpose: 'compaction'|'session-title'`（`packages/llm/llm/src/types.ts:371-377`）——辅助调用的规范分类，映射专用生成策略。
- **观测**：无显式 cache stats（headerEquals reason:change 间接记账）；TUI 无 miss 通告（pi 的 cache-stats 是三方独有）。

## ④ 子机制逐项对照表

| 子机制 | nop 机制 | dsh 机制 | 裁定 | 证据 |
|---|---|---|---|---|
| D6-1 缓存断点控制 | 通道存在无用户：providerHints 可透传 cache_control，但无生产写入方且放置位置不正确 | 无显式断点 API（刻意不用）；靠结构约定使前缀自然稳定 | 双方均无 | nop `ChatMessage.java:40-42`+`AnthropicDialect.java:603-605`（通道空置+位置错）；dsh 无断点 API——**有效断点管理双方都没有**（pi 才有，M3 对比）；dsh 的"不用"是设计选择，nop 的"没有"是未建设 |
| D6-2 前缀稳定性构造 | 执行内 append-only（setup 一次构造）；两个不稳定源：记忆进 system（跨 turn 漂移）+ 工具定义 HashSet 序（跨 JVM 不保证） | 完整工程化：section order 约定 + 动态走 user 快照取代制 + 工具 canonical 排序 + 同 scope 重名 fail-fast | 对方领先 | nop `AgentSessionLifecycle.java:153-162`、`AgentToolPlanResolver.java:57-132`、`_AgentModel.java:208`；dsh `system-prompt/src/index.ts:53-85,164-178`、`runtime-context.ts:23-75`——dsh 三条构造纪律 vs nop 两个缺口 |
| D6-3 压缩与缓存的交互 | 不存在（压缩 clear+addAll 作废前缀）；仅 Layer3 head anchors 消息级复用意识 | 压缩摘要调用字节级重放主对话前缀（KV cache 保持温热） | 对方领先 | nop `AgentCompactionCoordinator.java:130-132`、`Layer3FullSummaryStrategy.java:22-38`；dsh `compaction-basic/src/index.ts:226-246`——dsh 把"压缩破坏缓存"反转为"压缩复用缓存"，设计代差 |
| D6-4 一次性调用是否写缓存 | 不存在（辅助调用无任何标记；legacy disableCache 是本地缓存且 deprecated） | purpose 标记（compaction/session-title）+ 压缩调用前缀对齐主对话（利用而非禁写） | 对方领先 | nop `LlmCompletionJudge.java:143-164`；dsh `llm/src/types.ts:371-377`、`summarizer.ts:161`——dsh 方向是"辅助调用共享缓存"，nop 无机制 |
| D6-5 缓存命中观测 | 模型/解析层完整（ChatUsage 三字段+dialect 解析）但 agent 层未消费无出口 | 无显式 stats；EpochHeader reason:change 间接记账 | nop 领先 | nop `ChatUsage.java:18-129`、`AnthropicDialect.java:369-381`；dsh `types.ts:201-228`——nop 的解析资产已就位（比 dsh 强），差"最后一公里"消费 |

## ⑤ 语义差异与取舍

- **两种缓存哲学**：dsh 是"构造性稳定"（不碰 provider 缓存 API，让前缀天然可缓存）——适合多 provider（对不支持缓存的 provider 也获得稳定前缀的全部其他好处：可比较、可记账）；pi（M3 对比）是"断点式管理"（显式控制 TTL/位置）。nop 两者皆无。词同义异警示：dsh "runtime context" 不是 system prompt 的一部分（user 快照消息），nop 的记忆注入是第二条 system message——两者都在处理"动态上下文"，放置层完全不同，直接对比"动态内容位置"才会暴露差异。
- **快照取代制 vs 追加制**：dsh 新快照 replace 旧快照出 surface（模型只见最后一份）；nop steering 是纯追加（历史快照留在上下文中）。对前缀缓存：dsh 的 replace 需要缓存失效（它接受这点，换取上下文清洁）；nop 的追加保前缀但上下文膨胀。这是上下文工程与缓存利用的取舍轴（D8 交叉）。
- **观测即资产**：nop 的 ChatUsage 解析是"已建成的管道没有水"——cacheHitTokens 已按模型配置解析进 usage 对象，只差 agent 层累计与出口。这使 nop 的 D6 改造成本显著低于从零建设。
- **不可比项**：dsh 的 purpose 元数据向下映射到传输层隐藏字段（provider 特定），nop 无对位传输层契约——按 02 总则登记不硬比。

## ⑥ 裁定 + 可吸收增量建议

**维度总裁定：对方领先**——dsh 的构造性前缀工程（D6-2/3/4 三项领先）形成完整闭环；nop 仅观测解析层领先（D6-5）且断点通道空置（D6-1 双方均无）。roadmap 初步假设"nop 侧是否存在等价设计待 D6 核查"落地：**不存在系统性等价设计，仅部分构件**。

可吸收增量建议（仅记录，不实施）：

1. 【来源 nop 自身缺口；最小改动路径】修两个前缀不稳定源：①工具定义列表显式排序（声明序或字典序，对齐 dsh canonical 排序）；②记忆注入从 system 区域移到 user 快照消息（对齐 dsh 快照取代制），跨 turn 前缀即可稳定。
2. 【来源 nop 自身资产；一公里工程】在 agent usage 累计中纳入 cacheHitTokens/cacheCreationTokens 并在 usage 记录（IUsageRecorder）与事件 payload 暴露——管道已通，只加消费。
3. 【来源 dsh；针对压缩作废缓存】评估 Layer3 摘要调用对齐主对话前缀的完整方案（当前仅 head anchors 复用；dsh 式字节级重放可将压缩成本降低一个量级）。
4. 【来源 dsh；针对辅助调用】引入 purpose 式调用分类标记（compaction/judge/title），为后续按用途定制生成策略与缓存策略打基础。
5. 【来源 nop 自身；纠错优先】若启用 providerHints cache_control 透传，先修 AnthropicDialect 的放置位置（消息 map 顶层→content block 级），否则透传无效。

## References

- `ai-dev/analysis/compare-agent-design/00-dimension-matrix.md`（D6 定义 + Open Question 兑现）、`02-terminology-map.md`（T12）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-context-compaction-economics.md`（Owner doc）
- nop：`nop-ai/nop-ai-api/.../chat/{ChatOptions,ChatUsage,ChatMessage}.java`、`nop-ai/nop-ai-core/.../dialect/{AnthropicDialect,AbstractLlmDialect}.java`、`nop-ai/nop-ai-agent/.../engine/{AgentSessionLifecycle,AgentToolPlanResolver,AgentCompactionCoordinator}.java`、`compact/Layer3FullSummaryStrategy.java`
- dsh：`packages/core/system-prompt/src/index.ts`、`packages/core/agent-loop/src/runtime-context.ts`、`packages/compaction/compaction-basic/src/{index,summarizer}.ts`、`packages/llm/llm/src/types.ts`（`~/ai/deepseek-harness` @ c291e7961a）
