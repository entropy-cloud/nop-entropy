# pi-D6 前缀缓存利用对比

> Status: resolved
> Date: 2026-09-12
> 基线: nop=800baf32da（nop-ai 模块与 c585459f83 diff 为空）、pi=c49906ec7（分析当日实测）
> 引用: 00-dimension-matrix.md（WI2，D6 子机制 D6-1..D6-5）、02-terminology-map.md（T12）; nop 侧现状核查结论引用 dsh-D6 报告 ② 节（M2 已兑现矩阵 Open Question）；roadmap 初步假设"pi 拥有三方中最显式 prefix-cache 工程化设计"当日复核**成立**
> Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-context-compaction-economics.md`

## ① 结论摘要

- 总裁定：**对方领先**（三方中最强）。pi 是唯一具备"断点+保留策略+路由+观测"四旋钮正交设计的实现；nop 五项中四项不存在/空置。
- 关键差异 1（D6-1）：pi 显式管理 Anthropic cache_control 三断点（system 块/最后一个工具定义/最后一条 user 消息最后块）+ cacheRetention 三档（none/short/long→ttl 1h）+ sessionId 缓存路由——四个正交旋钮；nop 通道空置。
- 关键差异 2（D6-2）：pi 工具集变化才重建 system prompt + 工具注册表 Map 插入序稳定；nop 记忆进 system+工具 HashSet 序两个不稳定源。
- 关键差异 3（D6-4）：pi 摘要类一次性调用强制 `cacheRetention:"none"`+全新 uuidv7 sessionId——**禁写缓存防污染**（与 dsh 的"前缀对齐复用"是正交的两条辅助调用策略：dsh 让辅助调用蹭缓存，pi 隔离辅助调用护缓存）。
- 可吸收增量建议一句话：nop 的 D6 改造可直接以 pi 为蓝图（四旋钮+禁写策略+观测闭环，见 ⑥）。

## ② nop 侧机制与锚点

nop 现状核查结论与 dsh-D6 报告 ② 节共享（M2 已兑现）：断点=通道空置（`ChatMessage.java:40-42` hints+`AnthropicDialect.java:603-605` 透传位置不正确、无生产写入方）；前缀稳定=部分（记忆进 system `AgentSessionLifecycle.java:153-162`+工具 HashSet 序 `AgentToolPlanResolver.java:57-132`）；压缩交互/一次性标记=不存在；观测=解析层完整未消费（`ChatUsage.java:18-129`）。

## ③ pi 侧机制与锚点

按 02 T12 与 03 §2.3，对比增量：

- **断点控制**（D6-1）：Anthropic 适配器把 `{type:"ephemeral"}` 放三处——system 块（`anthropic-messages.ts:1002-1022`）、**最后一个工具定义**（convertTools 仅 index===tools.length-1，:1343-1361）、最后一条 user 消息最后块（:1295-1320）；统一走 `getCacheControl`（:57-71）；`cacheRetention: none|short|long`（默认 short，env PI_CACHE_RETENTION=long 兼容，long→`ttl:"1h"` 需模型 supportsLongCacheRetention，none→不下断点且不传 sessionId，:50-71,548-549）。
- **前缀稳定**（D6-2）：system prompt 仅在活动工具集变化时重建（`setActiveToolsByName`→`_rebuildSystemPrompt`，`agent-session.ts:938-955,1034-1067`）；工具注册表 Map 插入序（内置在前、扩展按注册序在后）+每轮 `prepareNextTurnWithContext` 重注（:2587-2680,541-560）。
- **压缩交互与一次性调用**（D6-3/D6-4）：摘要类独立请求强制 `cacheRetention:"none"`+全新 `uuidv7()` sessionId（`packages/agent/src/harness/compaction/compaction.ts:110-115`；coding-agent 版 `completeSummarization` 同款 `packages/coding-agent/src/core/compaction/compaction.ts:568-580`）——避免污染主会话缓存；sessionId 转发 provider 做会话粘性/缓存后端路由（`types.ts:207-211`）。
- **观测**（D6-5）：`CacheMiss{missedTokens,missedCost,idleMs,modelChanged}`——1024 token 噪声地板+5min TTL（CACHE_TTL_MS）启发式归因；`computeCacheWaste` 汇总；TUI 可选 miss 通告（`packages/coding-agent/src/core/cache-stats.ts:7-11,56-71,138`、`interactive-mode.ts:3685,3808`）。

## ④ 子机制逐项对照表

| 子机制 | nop 机制 | pi 机制 | 裁定 | 证据 |
|---|---|---|---|---|
| D6-1 缓存断点控制 | 通道空置（hints 透传位置不正确、无写入方） | 三断点放置+cacheRetention 三档+sessionId 路由——四正交旋钮 | 对方领先 | nop `ChatMessage.java:40-42`；pi `anthropic-messages.ts:50-71,1295-1361`——pi 是三方唯一显式断点管理（dsh 刻意不用、nop 未建设） |
| D6-2 前缀稳定性构造 | 执行内 append-only+两个不稳定源（记忆进 system/工具 HashSet 序） | 工具集变化才重建 system prompt+Map 插入序稳定 | 对方领先 | nop `AgentSessionLifecycle.java:153-162`；pi `agent-session.ts:938-955`——"按需重建"比"一次构造+意外漂移"可控 |
| D6-3 压缩与缓存的交互 | 不存在（压缩作废前缀） | 摘要请求禁写缓存+新 sessionId（防污染主缓存）——交互策略为"隔离"而非"复用" | 对方领先 | nop `AgentCompactionCoordinator.java:130-132`；pi `compaction.ts:110-115`——⚠️ 词同义异：dsh 压缩复用主对话缓存（对齐前缀），pi 压缩隔离于主缓存（禁写）——两条正交策略，nop 两者皆无 |
| D6-4 一次性调用是否写缓存 | 不存在 | 强制 retention:none+全新 sessionId | 对方领先 | 同 D6-3 证据——pi 把"辅助调用不污染主缓存"显式化为协议 |
| D6-5 缓存命中观测 | 解析层完整（ChatUsage 三字段+dialect 解析）但 agent 层无消费无出口 | CacheMiss 完整闭环（噪声地板+TTL 归因+waste 汇总+TUI 通告） | 对方领先 | nop `ChatUsage.java:18-129`；pi `cache-stats.ts:7-138`——pi 闭环 vs nop 半成品；nop 的解析资产是改造起点 |

## ⑤ 语义差异与取舍

- **三方缓存策略全景**（合 dsh-D6 ⑤）：dsh 构造性稳定（不用缓存 API，结构保证命中）→ pi 断点管理（显式 API+隔离策略+观测闭环）→ nop 无主动机制。dsh 与 pi 的辅助调用策略正交（dsh 复用/pi 隔离）——"让辅助调用蹭主缓存"（省钱但可能干扰）vs"辅助调用隔离"（干净但全价）——nop 若建设需先裁定辅助调用策略。
- **sessionId 的双重身份**：pi 的 sessionId 既是缓存路由键（provider 会话粘性）又是隔离工具（none 时置 undefined）——一个字段两个语义；nop 无对位概念。
- **观测的归因哲学**：pi CacheMiss 带 idleMs/modelChanged 归因（区分"闲置失效"与"换模失效"）——观测不只统计还解释；nop ChatUsage 只有原始计数。
- **不可比项**：pi 的 supportsLongCacheRetention 模型能力协商、env 覆盖（PI_CACHE_RETENTION）属产品化细节，登记不硬比。

## ⑥ 裁定 + 可吸收增量建议

**维度总裁定：对方领先**——五项全对方领先（pi 四旋钮正交设计+观测闭环），nop 仅解析资产可作改造起点；roadmap 假设③成立且幅度最大（本维是 pi 领先最显著的维度）。

可吸收增量建议（仅记录，不实施）：

1. 【来源 pi；针对 nop 断点空置】以 pi 为蓝图建设 nop 断点管理：ChatOptions 增加 cacheRetention 档位+dialect 层按三断点位置放置 cache_control（先修 AnthropicDialect 顶层放置错误——dsh-D6 建议 5），并引入 session 级缓存路由标识。
2. 【来源 pi；针对 nop 辅助调用】LlmCompletionJudge/Layer3 摘要调用默认禁写缓存（retention:none 对位）——裁定 nop 辅助调用策略时以 pi 隔离派为默认（安全），dsh 复用派为可选优化。
3. 【来源 pi；针对 nop 观测半成品】ChatUsage 的 cacheHitTokens 消费+噪声地板归因（idleMs/modelChanged）——把"解析"变"观测"（与 dsh-D6 建议 2 合并：nop 只差消费侧+归因侧两步）。
4. 【来源 pi；针对 nop 工具序】工具集变化才重建 prompt 的按需重建模式（与 dsh-D6 建议 1 的显式排序互补：排序保证稳定，按需重建避免无关变更破坏前缀）。

## References

- `ai-dev/analysis/compare-agent-design/00-dimension-matrix.md`（D6 定义）、`02-terminology-map.md`（T12）、`dsh-D6-prefix-cache.md`（nop 现状共享）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-context-compaction-economics.md`（Owner doc）
- nop：`nop-ai/nop-ai-api/.../chat/ChatUsage.java`、`nop-ai/nop-ai-core/.../dialect/AnthropicDialect.java`
- pi：`packages/ai/src/api/anthropic-messages.ts`、`packages/coding-agent/src/core/{agent-session,cache-stats}.ts`、`packages/agent/src/harness/compaction/compaction.ts`（`~/ai/pi` @ c49906ec7）
