# dsh-D8 上下文工程与压缩对比

> Status: resolved
> Date: 2026-09-12
> 基线: nop=800baf32da（nop-ai 模块与 c585459f83 diff 为空）、dsh=c291e7961a（分析当日实测）
> 引用: 00-dimension-matrix.md（WI2，D8 子机制 D8-1..D8-5）、02-terminology-map.md（T14：dsh 与 pi"追加式"词同义异警示）; 机制事实引用 03（P7/P13 压缩链）、06（overflow×retry 委托链）
> Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-context-compaction-economics.md`

## ① 结论摘要

- 总裁定：**等价（强项互补）**。nop 强在策略分层与引用式保真（Pipeline Layer1→2→3 显式升级、ReferenceCompactionStrategy 内容寻址、快照归档可恢复）；dsh 强在动态阈值与溢出恢复环（contextWindow 折算阈值、overflow→压缩→retry 闭环、影子价协议）。
- 关键差异 1（D8-2）：dsh 有"压缩后重试"错误恢复环（CONTEXT_WINDOW_EXCEEDED→压缩→continue，上限 maxOverflowRetries）；nop 的溢出面是 force-stop 硬停+best-effort 压缩（0.9×maxContextTokens），无压缩后重试。
- 关键差异 2（D8-4）：nop 的 ReferenceCompactionStrategy（内容寻址引用式压缩 + read-spill 工具取回）是两方独有；dsh 的 spill 挂工具结果面（post-execute），不压缩历史本身。
- 关键差异 3（D8-5）：双方都是"底账不破坏+视图替换"——nop snapshotArchive+COMPACTION checkpoint vs dsh append-only 日志+SurfaceOp replace；dsh 的影子价协议（replace 紧邻计量事件定价）是独特记账设计。
- 可吸收增量建议一句话：nop 可吸收 dsh 的"溢出→压缩→重试"闭环与模型动态阈值（见 ⑥）。

## ② nop 侧机制与锚点

按 02 T14 与 03 §2.1（P7/P6 闸门），对比增量：

- **预算与压力**（D8-1）：`ITokenEstimator` + `CalibratedTokenEstimator`（EMA 校准：估算随 provider usage 反馈收敛，`engine/CalibratedTokenEstimator.java`）；触发判据 `tokensUsed > 80%×maxContextTokens 或 messages>30`（`engine/ReActAgentExecutor.java:132-133`、`engine/AgentCompactionCoordinator.java:57-71`，maxContextTokens 取 ChatOptions.maxTokens 否则 128000 默认）。
- **触发**（D8-2）：三处——①iteration 开头集中检查（P7，PRE_COMPACT 洋葱→快照归档→Pipeline→POST_COMPACT→COMPACTION checkpoint→COMPACTION 事件，`AgentCompactionCoordinator.java:72-218`）；②force-stop 兜底压缩（预调用估算>0.9×maxContextTokens→handleForcedStop 做 best-effort 压缩+FORCED_STOP 事件硬停，`engine/AgentLoopGuard.java:89-122`）；③无溢出错误驱动重试环。
- **分层**（D8-3）：Pipeline 显式三层升级——Layer1 ToolResultTruncator 截断/微压缩→Layer2 TurnPruning（head 锚点+尾窗口）→Layer3 FullSummary（7 段 prompt 独立摘要请求，预算超限整体降级 Layer2，`compact/Layer3FullSummaryStrategy.java:155-159`）；压缩失败保留原文（`AgentCompactionCoordinator.java:114-122`）。
- **spill/引用式**（D8-4）：`ReferenceCompactionStrategy` 内容寻址引用式压缩（`compact/ReferenceCompactionStrategy.java`）+ `ISpillStore`/InMemorySpillStore + read-spill 工具（工具结果超限也走 spill，`AgentToolDispatcher.java:481-511`）。
- **产物形态**（D8-5）：就地替换（clear+addAll）但**快照归档可恢复**（snapshotArchive.put :93-95、compactionArchive 字段）+ COMPACTION checkpoint 分录（恢复链有据）+ COMPACTION 事件（payload tokensBefore/After/shadowedTokenCount/snapshotId，`engine/AgentEventType.java:127`）。

## ③ 对方侧机制与锚点

按 02 T14 与 03 §2.2（P7/P13），对比增量：

- **预算与压力**（D8-1）：`TokenMeter`（启发式估算 + provider usage 锚点 header 匹配复用，`packages/llm/token-meter/src/index.ts:74,113-150`）；pressure 判据 `totalTokens >= thresholdTokens`（按路由模型 contextWindow 折算，`compaction-basic/src/index.ts:293-304`）——阈值随模型动态。
- **触发**（D8-2）：pressure（agent/pre-step 自动检查，step 间执行内嵌 open turn）+ context-overflow（agent/request-error 上 CONTEXT_WINDOW_EXCEEDED→压缩→返回 retry，maxOverflowRetries 默认 1，成功响应/idle 清零，:147-223）+ 手动 compactNow（要求真空闲 runMaintenance，:368-420）——**溢出→压缩→重试闭环**。
- **分层**（D8-3）：region 区间选择（selectCompactableRange，切点过 tool-pairing balance 不切在 tool 对中间，`packages/compaction/compaction/src/tool-pairing.ts:110,122`）→可选免费 prune（ToolResultPruner）→摘要；retainTokens/retainRatio 尾部保留（config.ts）。
- **spill**（D8-4）：`SpillStore.saveText` verbatim 全量→SpillRef{locator,bytes,retrievalHint}（`packages/spill/spill/src/index.ts:45-60`）；spill-policy 挂 tools/post-execute（prepend 钉位，超限文本→头尾预览+取回指引）——只 spill 工具结果，不 spill 历史。
- **产物形态**（D8-5）：追加式遮蔽——append-only 日志不动，`SurfaceOp{op:'replace',start,end}` 新节点遮蔽 surface 区间（只许改 content，`session/src/types.ts:368-378`、`surface.ts:117-134,287-318`）；**影子价协议**：replace 紧邻 compaction/summary-prune 计量事件陈述被遮蔽区间的 token 价（`compaction/src/types.ts:23-88`），纯消费者免逐节点计价。

## ④ 子机制逐项对照表

| 子机制 | nop 机制 | dsh 机制 | 裁定 | 证据 |
|---|---|---|---|---|
| D8-1 token 预算与压力检测 | CalibratedTokenEstimator EMA 校准（估算随 usage 收敛）；固定比例触发（80%/30 条） | TokenMeter 双源（启发式+usage 锚点）；阈值按路由模型 contextWindow 动态折算 | 等价 | nop `CalibratedTokenEstimator.java`、`AgentCompactionCoordinator.java:57-71`；dsh `token-meter/src/index.ts:74`、`compaction-basic:293-304`——nop 估算校准强、dsh 阈值自适应强；组合优于单独 |
| D8-2 触发条件 | iteration 开头集中检查 + force-stop 兜底压缩（硬停）；无溢出重试环 | pressure（pre-step）+ context-overflow（错误驱动压缩后 retry，有上限）+ 手动（要求真空闲） | 对方领先 | nop `AgentLoopGuard.java:89-122`；dsh `compaction-basic:147-223,368-420`——dsh 的溢出恢复环把"上下文溢出"从终态变为可恢复事件；nop force-stop 是硬停 |
| D8-3 策略分层 | 显式三层 Pipeline（截断→turn 剪枝→全文总结）+ 降级路径（Layer3 预算超限回落 Layer2）+ 失败保留原文 | region 区间选择（tool-pairing 平衡切点）+ 免费 prune→摘要；无显式层级名 | nop 领先 | nop `compact/`（Pipeline 4 类）+ `Layer3FullSummaryStrategy.java:155-159`；dsh `region.ts`+`tool-pairing.ts:110,122`——nop 分级升级语义显式可配置；dsh 的切点平衡约束（不切 tool 对）是 nop Layer2 的对位约束但实现更形式化，综合 nop 分层胜出 |
| D8-4 spill 与引用式保真 | ReferenceCompactionStrategy 内容寻址引用式（历史本体压缩为引用+read-spill 取回）+ 工具结果 spill | SpillStore verbatim+SpillRef（仅工具结果面，spill-policy post-execute）；历史压缩无引用式 | nop 领先 | nop `ReferenceCompactionStrategy.java`、`AgentToolDispatcher.java:481-511`；dsh `spill/src/index.ts:45-60`——nop 把引用式用于历史压缩（dsh 用 surface replace 摘要替代），保真语义更强；dsh spill 面（工具结果）双方趋同 |
| D8-5 产物形态 | 就地替换+快照归档（可恢复）+COMPACTION checkpoint+事件含 snapshotId | 追加式遮蔽（日志原样保留+SurfaceOp replace 只许改 content）+影子价协议 | 等价 | nop `AgentCompactionCoordinator.java:93-203`；dsh `session/src/types.ts:368-378`、`compaction/src/types.ts:23-88`——底账级双方都非破坏；dsh 影子价协议是记账创新，nop snapshotId 归档是恢复创新；02 T14 警示：两方"追加式"对象不同（dsh 日志不破坏但 surface 被遮蔽 vs nop 消息列表被替换但快照归档） |

## ⑤ 语义差异与取舍

- **压缩的失败语义相反**：nop 压缩器抛异常→保留原文继续（压缩是尽力优化，`AgentCompactionCoordinator.java:114-122`）；dsh 压缩失败→compaction/end 带错误、overflow 场景不返回 retry（错误继续传播）。nop"压缩永不使情况更糟"vs dsh"压缩是恢复环的必经步骤"。
- **compaction 词同义异**（02 T14）：dsh 的 compaction=start/summary/end 事件事务（持锁+影子价）；nop 的 COMPACTION 是 checkpoint 类型+事件。两方都有"压缩事务化"意识，载体不同（事件日志 vs checkpoint+快照）。
- **阈值哲学**：nop 固定比例（80%/30 条）简单可预测；dsh 按模型 contextWindow 折算（不同模型自动适配）。多模型路由场景（nop SmartModelRouter 换模型）下 nop 的固定 maxContextTokens 可能与新模型不匹配——这是 D5×D8 交叉的潜在缺口（路由换模后阈值不重算，记录为 nop 潜在改进点）。
- **不可比项**：dsh 的 region 选择作用于 surface 投影树（依赖其 Surface 数据结构），nop 无 surface 概念，region 算法不可直接移植——机制目标（不切 tool 对）可比，实现不可比。

## ⑥ 裁定 + 可吸收增量建议

**维度总裁定：等价**——五个子机制双方各两子项领先（nop：分层/引用式；dsh：溢出环/影子价），核心能力（压力检测、分层压缩、spill、非破坏底账）双方齐备且强项互补。

可吸收增量建议（仅记录，不实施）：

1. 【来源 dsh；针对 nop 溢出硬停】为 force-stop 增加"压缩后重试"路径：0.9 阈值触发时先执行兜底压缩并重算预算，压缩后仍超限才 FORCED_STOP（当前直接硬停+best-effort 压缩，长任务损失执行进度）。
2. 【来源 dsh；针对 nop 固定阈值】maxContextTokens 随 SmartModelRouter 路由切换动态刷新（当前执行开始时定格，换模后阈值可能与新模型 contextWindow 失配——D5×D8 交叉缺口）。
3. 【来源 dsh；针对 nop 压缩计价】引入压缩前后 token 计量的事件化记录（nop COMPACTION 事件已有 tokensBefore/After——进一步把被压缩段落的逐段计价落 journal，对位影子价协议的可审计性）。
4. 【来源 nop；反向记录供 dsh 参考】ReferenceCompactionStrategy 的内容寻址引用式压缩可移植为 dsh 的 spill 扩展（历史段落 spill+引用，比摘要保真更强）。

## References

- `ai-dev/analysis/compare-agent-design/00-dimension-matrix.md`（D8 定义）、`02-terminology-map.md`（T14）、`03-flow-agent-loop.md`、`06-extension-composition.md`（overflow×retry）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-context-compaction-economics.md`（Owner doc）
- nop：`nop-ai/nop-ai-agent/.../compact/`（PipelineCompactor/Layer2/Layer3/Reference/ToolResultTruncator/ISpillStore）、`engine/AgentCompactionCoordinator.java`、`engine/AgentLoopGuard.java`
- dsh：`packages/compaction/{compaction,compaction-basic}/src/`、`packages/compaction/compaction-tool-result-pruner/`、`packages/llm/token-meter/src/index.ts`、`packages/spill/`（`~/ai/deepseek-harness` @ c291e7961a）
