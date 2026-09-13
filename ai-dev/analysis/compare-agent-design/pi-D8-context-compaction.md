# pi-D8 上下文工程与压缩对比

> Status: resolved
> Date: 2026-09-12
> 基线: nop=800baf32da（nop-ai 模块与 c585459f83 diff 为空）、pi=c49906ec7（分析当日实测）
> 引用: 00-dimension-matrix.md（WI2，D8 子机制 D8-1..D8-5）、02-terminology-map.md（T14：pi"追加式"词同义异警示）; 机制事实引用 03 §2.3（P13 恢复循环）、06（session_before_compact 协同）
> Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-context-compaction-economics.md`

## ① 结论摘要

- 总裁定：**等价（预算模型 vs 策略分层互补）**。pi 强在双预算参数模型（reserveTokens/keepRecentTokens）与溢出恢复环（一次性门闩+continue）；nop 强在显式三层策略与引用式保真（Layer1→2→3、ReferenceCompactionStrategy）。
- 关键差异 1（D8-1）：pi 的预算是**双参数正向声明**（给响应留 reserveTokens=16384、给最近上下文留 keepRecentTokens=20000）；nop 是**单阈值反向检测**（80% 触发）。
- 关键差异 2（D8-5）：pi 的压缩产物是会话树上的追加式 `CompactionEntry{summary,retainedTail}`，重建算法="最近一条 compaction+其后全部 entry"（惰性、多次压缩自然级联）；nop 是就地替换+快照归档。
- 关键差异 3：pi 独有 branch_summary（会话树导航离开分支时生成）——压缩与会话树导航耦合，nop 无树概念。
- 可吸收增量建议一句话：nop 可吸收 pi 的双预算参数与"切点合法边界"约束（见 ⑥）。

## ② nop 侧机制与锚点

nop 事实基线与 dsh-D8 报告 ② 节共享：CalibratedTokenEstimator EMA 校准+80%/30 触发（`AgentCompactionCoordinator.java:57-71`）；Pipeline Layer1 截断→Layer2 turn 剪枝→Layer3 全文摘要（7 段 prompt，`compact/Layer3FullSummaryStrategy.java:155-178`）；ReferenceCompactionStrategy 内容寻址+ISpillStore；就地替换+快照归档+COMPACTION checkpoint（:93-203）。

## ③ pi 侧机制与锚点

按 02 T14 与 03 §2.3（P13），对比增量：

- **预算与压力**（D8-1）：`CompactionSettings{enabled, reserveTokens=16384, keepRecentTokens=20000}`（`packages/agent/src/harness/compaction/compaction.ts:148-162`）；判据 `shouldCompact = tokens > contextWindow - reserveTokens`（:247-250）——reserve 是给模型响应留的空间，keepRecent 是切点保留预算。
- **触发**（D8-2）：三档 `reason: manual|threshold|overflow`（/compact、阈值、溢出恢复，`agent-session.ts:156,1831`）；溢出恢复一次性门闩 `_overflowRecoveryAttempted`（成功响应/新 user 消息复位）+session_before_compact 可 cancel/自备结果（06 ④.1）。
- **切点与摘要**（D8-3）：`findCutPoint` 按 keepRecentTokens 从尾累计，切点只允许落在 user/bashExecution/branch_summary 等合法边界（`compaction.ts:312-422,689-702`）；**迭代式摘要**——有旧 compaction 时旧 retainedTail 虚拟展开参与累计，`UPDATE_SUMMARIZATION_PROMPT` 合并旧摘要（:461-498,624-646）；摘要调用禁写缓存（`completeSummarization`，:568-580，D6 交叉）。
- **产物与重建**（D8-5）：会话树**追加** `CompactionEntry{summary,retainedTail,tokensBefore,details,usage}`，原始条目不删（`harness/session/types.ts:44-51`）；重建 `buildSessionContext`：从 leaf 沿 parentId 回溯→**从后往前找最近一条 compaction，上下文=[该 compaction]+其后全部 entry**（此前丢弃，不递归拼接，`harness/session/context.ts:45-100`）。
- **branch_summary**（pi 独有）：树导航离开分支时从 oldLeaf 走到公共祖先收集 entry 生成 `BranchSummaryEntry`（`packages/coding-agent/src/core/compaction/branch-summarization.ts:34-58,96-120`）。

## ④ 子机制逐项对照表

| 子机制 | nop 机制 | pi 机制 | 裁定 | 证据 |
|---|---|---|---|---|
| D8-1 token 预算与压力检测 | EMA 校准估算（精度资产）+固定比例触发（80%/30 条） | 双参数正向预算（reserve 给响应/keepRecent 给切点）+contextWindow 动态基 | 等价 | nop `CalibratedTokenEstimator.java`、`AgentCompactionCoordinator.java:57-71`；pi `compaction.ts:148-162,247-250`——nop 精确估算、pi 语义清晰预算；理想组合（与 dsh-D8 D8-1 同结论三方互异） |
| D8-2 触发条件 | iteration 集中检查+force-stop 兜底（硬停） | threshold/overflow/manual 三档+溢出恢复一次性门闩+continue | 对方领先 | nop `AgentLoopGuard.java:89-122`；pi `agent-session.ts:2050-2154`——pi 有溢出恢复环（与 dsh 同），nop 溢出即硬停（dsh-D8 D8-2 同结论） |
| D8-3 策略分层 | 显式三层 Pipeline 升级（截断→剪枝→全文摘要）+降级路径 | 切点合法边界约束+迭代式摘要（旧 retainedTail 虚拟展开+合并 prompt）；无显式层级 | nop 领先 | nop `compact/`+`Layer3FullSummaryStrategy.java:155-159`；pi `compaction.ts:312-702`——nop 分级升级语义显式；pi 的迭代式摘要（合并旧摘要而非重新全量）是好的增量策略但非分层 |
| D8-4 spill 与引用式保真 | ReferenceCompactionStrategy 内容寻址引用式+ISpillStore+read-spill | 无 spill 概念；保真靠 retainedTail 原文尾段 | nop 领先 | nop `compact/ReferenceCompactionStrategy.java`；pi `types.ts:44-51`——内容寻址引用（中段可取回原文）pi 无对位；retainedTail 只保尾段 |
| D8-5 产物形态 | 就地替换+快照归档（可恢复）+COMPACTION checkpoint | 追加式 CompactionEntry+惰性重建（最近一条 compaction 截断，级联自然发生） | 等价 | nop `AgentCompactionCoordinator.java:93-203`；pi `context.ts:45-100`——⚠️ 02 T14 警示：pi"追加式"指树条目不删但**重建只取最近一条之后**（旧历史不参与上下文），nop"替换"有快照归档——两者都非破坏，对象不同；pi 的惰性重建无需运行时替换动作（更简），nop 的快照归档可回滚（更强） |

## ⑤ 语义差异与取舍

- **压缩即树节点**（pi 独有架构事实）：pi 的 compaction/branch_summary 都是会话树的普通 entry——压缩没有特权地位（可 fork 一个未压缩分支继续跑）；nop 的压缩改变执行上下文（有 checkpoint+归档）。前者历史多宇宙（树各分支各自压缩状态），后者单线演进+快照。
- **"最近一条截断"的级联语义**：pi 多次压缩后上下文=最近摘要+其后 entry——旧摘要不递归拼接（信息单调收敛）；nop Layer3 摘要时合并全部中层窗口（`buildMiddleContent` 打平）——每次压缩都重新看全量（信息保留更全但成本高）。迭代式摘要（pi）是两者的中间态。
- **reserveTokens 的正向性**：pi 的 reserve 是"给响应留多少"（防压缩后响应又溢出的死循环）——nop 的 80% 阈值隐含同类意图但未参数化。多模型路由（nop SmartModelRouter）下 reserve 应随模型 contextWindow 变化（与 dsh-D8 建议 2 同源的 D5×D8 缺口）。
- **branch_summary 不可比**：依赖 pi 会话树（D9 交叉），nop 无树结构——登记为 pi 独有概念（02 已登记），不硬比。

## ⑥ 裁定 + 可吸收增量建议

**维度总裁定：等价**——D8-2/D8-4 各一方领先（pi 溢出环/nop 引用式）、D8-3 nop 领先、D8-1/D8-5 等价；pi 的预算模型与树级压缩、nop 的分层与引用式保真分属两种上下文经济学。

可吸收增量建议（仅记录，不实施）：

1. 【来源 pi；针对 nop 触发单阈值】把 80% 阈值参数化为双预算（reserveTokens 给响应+保留预算给切点），随 SmartModelRouter 路由动态刷新（与 dsh-D8 建议 2 合并实施面）。
2. 【来源 pi；针对 nop 压缩成本】迭代式摘要（Layer3 摘要时引用上一次压缩结果合并，而非重新处理全量中层）——长会话多次压缩的成本从 O(n²) 降 O(n)。
3. 【来源 pi；针对 nop 切点安全】Layer2 turn 剪枝的切点增加"合法边界"约束校验（对位 pi findCutPoint 的边界白名单+tool-pairing 平衡），防止剪枝切在工具调用对中间。
4. 【来源 nop；反向记录供 pi 参考】ReferenceCompactionStrategy 的内容寻址引用可作为 pi 树 entry 的可选压缩级别（比纯摘要保真强，与 spill 组合）。

## References

- `ai-dev/analysis/compare-agent-design/00-dimension-matrix.md`（D8 定义）、`02-terminology-map.md`（T14）、`03-flow-agent-loop.md`（§2.3）、`06-extension-composition.md`、`dsh-D8-context-compaction.md`（nop 基线共享）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-context-compaction-economics.md`（Owner doc）
- nop：`nop-ai/nop-ai-agent/.../compact/`、`engine/AgentCompactionCoordinator.java`
- pi：`packages/agent/src/harness/compaction/compaction.ts`、`harness/session/{types,context}.ts`、`packages/coding-agent/src/core/compaction/branch-summarization.ts`（`~/ai/pi` @ c49906ec7）
