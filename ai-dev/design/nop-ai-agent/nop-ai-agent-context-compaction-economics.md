# Nop AI Agent 上下文压缩与计价增强设计（KV 前缀保持 / 阴影计价 / Spill 溢出存储）

**日期**：2026-08-13
**范围**：nop-ai-agent 的 compact 包（PipelineCompactor / Layer3FullSummaryStrategy / ToolResultTruncator）、engine 包（AgentCompactionCoordinator / AgentToolDispatcher / AgentEventType）、usage-and-billing 链路
**状态**：active
**灵感来源**：DeepSeek Harness（v0.1.0-rc.5）调研——其 compaction 的 surface 遮蔽 + shadowedTokenCount 计价、KV 前缀保持的摘要调用、spill 溢出存储三个机制，经与本项目既有实现逐项对照后转化为本设计

---

## 一、设计结论

1. **摘要调用必须保持 KV 前缀缓存连续**：`Layer3FullSummaryStrategy` 的 LLM 摘要请求改为"原对话 head 消息原样前置 + 摘要指令 + 中段待概括内容"的结构，使其与压缩后的真实对话请求共享尽可能长的连续前缀。DeepSeek 官方定价中 cache hit 与 miss 存在数量级差异（约 10x），这是**成本级差异**，非优化细节。
2. **压缩必须记录阴影 token 计价**：compaction 结果增加被遮蔽（模型实际未再消费）token 的估算字段，随 COMPACTION checkpoint 持久化，供审计与计费链路量化"压缩省的输入成本"，并与既有两维度压缩比（消息条数、token）并列。
3. **超长工具输出必须 spill 而非纯截断**：工具结果在进入上下文前先走溢出存储（全文落 per-session spill store），上下文内只保留 head/tail 预览 + 定位提示；截断从默认行为降级为 spill 不可用时的 fallback。与既有引用式压缩（压缩时 shortRef）互补：spill 是**入口侧**防膨胀，引用式是**压缩侧**去膨胀。

## 二、背景与动机

### 2.1 现状（代码核实）

- `Layer3FullSummaryStrategy.summarize`（Layer3FullSummaryStrategy.java:131-133）构造摘要请求时只有 `SUMMARIZATION_SYSTEM_PROMPT` + user prompt 两条消息，**对话 head 未参与请求**——摘要调用与后续真实对话请求无共享前缀，KV 缓存完全不连续。
- `AgentCompactionCoordinator`（AgentCompactionCoordinator.java:151-172）已把 `tokensBefore/tokensAfter` + `snapshotId` 写入 COMPACTION checkpoint 的 compactSummary（两维度压缩比，context-model §8.3 裁定 D）；但**无"被遮蔽 token"维度**，且 `AgentEventType` 无 compaction 相关事件（20 个事件类型中无 COMPACTION）。
- `AgentToolDispatcher`（AgentToolDispatcher.java:311-314）成功工具结果直接走 `ToolResultTruncator.truncateIfAllowed`（HEAD 6000 + TAIL 1000 + 截断 marker，ToolResultTruncator.java:8-28）——**超长内容纯截断即丢失**，无 spill 路径。`ReferenceCompactionStrategy`（压缩侧 shortRef + read-ref 读回）已存在，但只在压缩阶段触发，入口侧无对应保真路径。

### 2.2 动机

1. **成本**：DeepSeek 前缀缓存命中与未命中存在数量级价差。压缩是高频路径（token 压力触发），摘要调用本身也是一次 LLM 调用——当前实现摘要调用全价未命中，且压缩打断对话前缀连续性（缓存被逐出后需重建）。
2. **可审计性**：压缩省了多少输入成本没有量化记录。usage-and-billing 链路（`NopAiChatResponse` 写入）目前记录的是模型返回的 usage，压缩被遮蔽的输入 token 无账可查。
3. **信息保真**：工具结果截断即丢。长文件 diff、长 SQL、长 JSON 被截断后模型只能看到 marker 内的 head/tail，需要时无法取回全文——spill 提供"不丢 + 不膨胀"的第三态。

## 三、核心设计

### 3.1 决策 1：KV 前缀保持的摘要调用

**契约**：Layer3 摘要请求的消息序列必须与"压缩后的真实对话请求"共享从第一条消息开始的最长连续前缀。

具体结构（请求消息序列）：

```
[原 head 消息逐条原样]          ← 与真实对话共享的前缀（压缩后 head 保留不变）
[摘要指令 user 消息]            ← "对以下中段内容做 7 段式总结"（含 previous-summary；角色裁定见约束 6）
[中段内容 user 消息]            ← middle 内容（与 head 共享摘要请求预算，见约束 2）
```

**约束**：

1. head 消息**原样复用**压缩上下文中的消息对象（不重建、不改写 content），保证与真实对话请求序列化字节一致——呼应 llm-layer 既有"前缀缓存设计（原则层序列化确定性）"，序列化确定性是命中前提。
2. head 过长时（极端长会话）允许按预算裁剪：裁剪必须是**消息级**（从段尾部丢弃整条消息），不得做字符级截断——字符级截断可能落在多字节字符/JSON 转义中间，破坏序列化字节前缀一致性。**预算作用于整个摘要请求**（head + 摘要指令 + 中段的总 token 上限，可配置 `compressionPromptBudget`，默认取压缩触发时 token 水位的一半）；head 与中段**共享**该预算总额，按 **1:1 分配**（默认，允许配置偏离；head 未用尽份额可让渡给中段）。"压缩触发时 token 水位" = `maxContextTokens × triggerPercent`（`triggerPercent` 取 `CompactConfig.getTriggerTokenPercent()`，`maxContextTokens` 解析镜像 `PipelineCompactor.resolveMaxContextTokens`，execCtx null → 128000），而非单一固定阈值。
3. 摘要响应回写格式不变（`SUMMARY_MARKER` 前缀，Layer3FullSummaryStrategy.java:155），真实对话前缀 = head + summary 消息 + tail 的结构不变——共享前缀只覆盖 head 段。
4. 本机制是**尽力而为的成本优化**：head 命中不保证（provider 缓存逐出、中间有非前缀调用都会失效），失败不影响正确性；压缩请求失败仍走既有 Layer 2 降级路径（Layer3FullSummaryStrategy.java:97-103 不改）。
5. 压缩模型与真实模型不一致时（`compressionModel` 配置），KV 缓存本就跨模型不共享，本机制收益归零但**不得关闭**——保持行为一致，成本由配置者判断。
6. **摘要指令归属（裁定）**：摘要指令用 **user 角色**消息（`[摘要指令 user 消息]`），**不用 system**——head 前置后若指令仍为 system，请求会出现"system→user→…→system"多 system 结构，多数 provider（OpenAI 系）拒绝该结构，导致摘要请求永远失败 → 静默降级 Layer2（机制在真实环境失效而单测全绿）。previous-summary（既有 `incrementalUpdatePassesPreviousSummary` 语义）并入摘要指令 user 消息内容。
7. **预算默认值语义（裁定）**：`compressionPromptBudget` 未配置时按公式动态计算——默认 = 压缩触发水位的一半；水位 = `maxContextTokens × triggerPercent`。**来源钉死**：`triggerPercent` 取 `CompactConfig.getTriggerTokenPercent()`（可配置，默认 0.8，`PipelineCompactor` 同源）；`maxContextTokens` 解析镜像 `PipelineCompactor.resolveMaxContextTokens`（execCtx 为 null 时 fallback 128000）。配置哨兵：未配置 = `-1`，运行时按公式计算；配置 ≥ 1 即显式预算（不再按水位推导）。

**拒绝项**：见"四、拒绝了什么"第 1 条。

### 3.2 决策 2：阴影 token 计价

**概念**：`shadowedTokenCount` = 压缩时被遮蔽（模型实际未再消费）的输入 token 估算。语义 = 压缩前后"模型实际读取的输入量"之差（即 `tokensBefore − tokensAfter`，同一估算器同一消息集）。它与既有 `tokensBefore/tokensAfter` 是**直接函数关系**——定位为**派生便捷字段**（避免 checkpoint/事件消费方重算），不是独立度量维度；压缩比维度的权威度量仍是 `tokensAfter/tokensBefore`（context-model §8.3 裁定 D）。

**载体**（按优先级，均不动公共 API）：

1. `CompactionResult` 新增 `shadowedTokenCount` final 字段。**构造器默认语义**：新 9 参构造器（既有 8 参已存在，`PipelineCompactor` 唯一构造点）填正确差值；legacy 5/6 参构造器默认填 `max(0, tokensBefore − tokensAfter)`（可从既有参数直接推导，避免"便捷字段恒 0 的静默错值"）；`equals/hashCode/toString` 同步。参照 context-model §8.3 裁定 D 的构造器兼容先例。
2. `AgentCompactionCoordinator` 的 COMPACTION checkpoint compactSummary 追加 `shadowed=<n>`（AgentCompactionCoordinator.java:151-172 现成拼接点）。
3. `AgentEventType` 新增 `COMPACTION` 事件类型（当前 20 个事件类型无 compaction——事件缺失是审计断点，本设计补齐）。事件负载含 `tokensBefore/tokensAfter/shadowedTokenCount/snapshotId`（shadowed 为便捷字段，消费方亦可由前两者推导）。**发射点**：`POST_COMPACT` hook 之后、COMPACTION checkpoint 写入之后（AgentCompactionCoordinator.java:159-172 的 checkpoint 拼接点之后立即发射）——保证事件携带的度量值与 checkpoint 持久化值同源一致。**登记**：同步在 `02-execution-model.md` 的事件模型章节补充 COMPACTION 事件条目（该文档无独立事件类型表，登记为该章节的事件清单条目）；ISpillStore 的 `03-extension-matrix.md` 闭合度登记随其接口落地执行。

**计价口径**：

- shadowed 用**估算器**（`CalibratedTokenEstimator`，EMA 校准）计算，不与 provider 计费精确对齐——它是成本工程的内部账目，文档化口径（估算值，非账单值）。
- 与既有 token 维度压缩比（`tokensAfter/tokensBefore`）的关系：压缩比度量"上下文瘦身程度"（权威维度），shadowed 是其派生便捷字段，度量"输入计费节省"的同一事实的两个视角——**不是独立维度**，两者同源同估。
- **不扩展** usage-and-billing 的 `NopAiChatResponse` 写入（外部计费账单仍以模型返回 usage 为准）；阴影计价只在 agent 内部账目（checkpoint/事件）呈现。此边界防止"内部估算值污染计费账单"。

### 3.3 决策 3：Spill 溢出存储

**契约**：工具结果进入上下文之前，超长输出先入 spill store，上下文内只放预览。

```
工具结果产出
  → 长度 ≤ maxInlineBytes：原样内联（现状不变）
  → 长度 > maxInlineBytes：
      1. 全文 put 到 per-session spill store → 返回 spillId
      2. 内联 head/tail 预览 + [SPILL_REF id=<spillId> type=<toolName> bytes=<n>] 定位提示
      3. spill 不可用（存储失败）：降级走既有 ToolResultTruncator 截断，且必须 LOG.warn（含工具名 + 失败原因 + bytes 数）——降级不静默（Minimum Rules #24）
      4. LLM 需全文时经 read-spill 工具按 spillId 读回
```

**例外工具**：`ToolResultTruncator` 现有 `NON_TRUNCATABLE_TOOLS`（`ask-oracle`/`ask-human`，ToolResultTruncator.java:11-13）不截断、全文内联。**spill 同样豁免这两类工具**——人机交互输出语义要求全文可达，spill 引入的失效风险不可接受。例外集与截断例外集保持同一配置源，避免两套例外清单漂移。

**关键裁定**：

- **A. 接口与宿主**：`ISpillStore` 接口（put/get/delete + per-session 生命周期）与首版实现 `InMemorySpillStore` 均定义在 **nop-ai-agent 的 compact 包**。**实例宿主 = `AgentSession`**（lazy init，首次超阈值才创建）：session→compact 依赖边**已存在**（`AgentSession` 已持有 compact 包的 `InSessionCompactionArchive`，context-model §8.2 裁定 G 的宿主先例），不引入新依赖方向。**双端接线**：写侧 `AgentToolDispatcher` 经既有模式 `sessionStore.get(ctx.getSessionId())` 取 session（AgentToolDispatcher.java:145-147/388-394 同款，`AgentExecutionContext` 无 session 访问器、只有 sessionId）；读侧 read-spill 工具经 `AgentToolExecuteContext.getSession()`（tool 包既有先例：`SetActiveTagsExecutor`/`ReadMemoryExecutor` 同模式）取同一 session → 同一 store 实例。两端共享同一实例，无双实例漂移。**null-session 路径**（session 未入 store，builder 直连测试等场景）：超阈值结果**静默走既有截断、不 warn**——与"put 失败降级 warn"严格区分（null-session 是未装配场景，非故障）。
- **A'. 降级语义（两类，只保留一类 warn）**：store **始终存在**（默认装配 InMemorySpillStore，lazy init，无 null-store 分支——存量部署无需处理"未接线"分支）；只有 **put 失败**（内存/容量异常）走降级：截断 + LOG.warn（工具名 + 原因 + bytes）。避免"每会话首次超阈值都 warn"的日志污染与 null 分支语义分裂。
- **B. 与引用式压缩的关系**：spill 与 `ReferenceCompactionStrategy` 不互转（首版）。spill 预览是普通文本（含定位提示），引用式 shortRef 只针对文件/搜索类长内容。**拒绝**把 spill 预览改造成 shortRef 格式（两个寻址模型——spillId vs content hash——职责不同，互转引入歧义）。允许后续演进：引用式压缩可将 spill 预览的定位提示纳入候选（shortRef 化），但本设计不承诺。
- **C. 与 checkpoint/恢复的关系**：spill store 是**内存态**（会话级），不随 checkpoint 持久化；崩溃恢复后 spill 引用失效——read-spill 对失效 spillId 返回显式错误（"内容已失效，请重新执行工具"），fail-loud 不静默。spill 全文**无界**，不入 checkpoint journal（journal 只记有界 payload——既有 TOOL_EXECUTION checkpoint 的 tool response 受入口截断 ≤8000 字符约束（截断例外的 ask-oracle/ask-human 除外，AgentToolDispatcher.java:363-376）；spill 全文无此上界约束）。**read-spill 读回结果与普通工具结果同路径**（超阈值再次走 spill/截断判定，SPILL_REF 允许嵌套，不特殊豁免）。
- **D. 阈值**：`maxInlineBytes` 默认与截断阈值一致（ToolResultTruncator.DEFAULT_TRUNCATION_THRESHOLD_CHARS），语义为"超过内联阈值即 spill 而非截断"。
- **E. 接入点**：`AgentToolDispatcher.java:311-314`（成功结果路径）——spill 决策先于 truncate；`ReActAgentExecutor.java:733` 的 LLM 输出摘要截断**不接入** spill（那是模型输出不是工具结果，长度受模型输出上限约束）。

## 四、拒绝了什么

1. **摘要请求把 tail 也前置**（完整复用"head+tail"前缀）：head 在压缩后原样保留于真实对话且长度可控（压缩触发时 head 占比小），tail 可能含工具响应等大 payload——前置 tail 使摘要请求 token 成本翻倍而缓存共享收益与 head 相同。head 已是真实对话的最长共享前缀，tail 不参与摘要请求。
2. **摘要调用与真实对话同批进行**（一次请求完成压缩与总结）：真实对话语义依赖压缩结果，无法并行；复杂度收益不成立。
3. **shadowedTokenCount 用 provider 精确 usage 差值计算**：压缩前没有真实请求，无 usage 可对；估算器是唯一可行口径，且内部账目无需账单精度。
4. **给 `ChatMessage`/`nop-ai-api` 加 origin/content-type 字段做 spill 判定**：与 context-model §8.2 裁定 A 同理由——跨模块公共 API 扩展（Protected Area plan-first），工具名 + 长度阈值已足够。
5. **spill 全文进 checkpoint/DB 持久化**：大 payload 进 journal 破坏 append-only journal 的轻量语义；内存态 + fail-loud 失效提示已覆盖恢复语义。
6. **`ISpillStore` 放 nop-ai-toolkit**：无 toolkit 消费方（read-spill 工具 nop-ai-agent 内部注册），放 toolkit 是过早抽象；遵循"消费方所在模块定义接口"原则（context-model §8.2 裁定 B 同款逻辑）。
7. **spill 与引用式归档合一**：spillId（执行时生成、会话态）与 content hash（压缩时生成、内容寻址）寻址模型不同，合一造成双重语义；本设计保持两套独立。

## 五、与已有设计的关系

| 既有文档/实现 | 关系 |
|---|---|
| `nop-ai-agent-context-model.md` §8.2 引用式压缩双轨（shortRef/read-ref/内容 hash 校验） | spill 与其互补（入口侧 vs 压缩侧），寻址模型分离；§8.2 裁定 A 的"不改公共 API"先例被本设计 3.2/3.3 沿用 |
| `nop-ai-agent-context-model.md` §8.3 snapshot 归档与压缩比（originalSize/compactedSize/tokensBefore/tokensAfter/snapshotId，裁定 A-G） | shadowedTokenCount 作为 token 维度的**派生便捷字段**挂靠既有 checkpoint 链路（同 §3.2 定位，非独立维度）；构造器兼容先例（裁定 D 的 5/6/8 参）被 3.2 沿用；三套 snapshotId 命名空间不新增 |
| `nop-ai-agent-reliability.md`（CheckpointType.COMPACTION / checkpoint journal） | 阴影计价随既有 COMPACTION checkpoint 持久化，不新增 checkpoint 类型；spill 内容不入 journal（3.3 裁定 C） |
| `nop-ai-agent-llm-layer.md` 前缀缓存设计（序列化确定性 / 缓存状态丢失恢复 409 / 缓存流量双侧记账） | 决策 1 依赖其序列化确定性原则；双侧记账（`cache_read`/`cache_write`）记录的是**实际发送**给 provider 的 token 流量，与 shadowed 记账（**从未发送**的被遮蔽估算）互补而非同域——shadowed 是"省了多少发送"，cache_read 是"发送中命中多少" |
| `nop-ai-agent-session-and-storage.md` §16.4（Tool output 完整保留） | **边界声明**：§16.4 裁定"Tool output 完整存入存储层 CLOB 不做截断"——spill 后上下文只有预览，会话持久化链路（AgentToolDispatcher.java:388-392 的 replaceMessages+save）与未来存储层都只有预览。本设计**首版不承诺**存储层全文归档（spill store 内存态、会话级释放）；"spill 内容持久化进存储层"列为后继专题，与 §16.4 的审计意图对齐由该专题负责 |
| `nop-ai-agent-usage-and-billing.md`（IUsageRecorder / NopAiChatResponse 写入） | 阴影计价只进 agent 内部账目（checkpoint/事件），不污染外部计费账单（3.2 边界声明） |
| `03-extension-matrix.md`（66 接口闭合度索引） | ISpillStore 新增接口需在实现后登记闭合度 |

## 后续专题（不在本文范围）

dsh 调研另有两条机制建议，与上下文成本主题正交，另行立文档：

- **call-agent capabilities 契约**（outputSchema/depthLimit/toolFilter/persona 声明 + 委托前校验 fail-loud）——子代理契约层。
- **goal 持久轮次驱动**（持久目标对象 + idle 自动续轮 + maxGoalRounds 上限）——目标执行层。

## References（锚点）

- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/compact/Layer3FullSummaryStrategy.java:131-155`（摘要请求构造现状）
- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/compact/ToolResultTruncator.java:8-28`（截断现状，NON_TRUNCATABLE_TOOLS 在 11-13）
- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentCompactionCoordinator.java:151-172`（checkpoint 记录现状）
- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentToolDispatcher.java:311-314`（工具结果截断接入点）
- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentEventType.java`（20 事件类型，无 COMPACTION）
- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/compact/ReferenceCompactionStrategy.java`（引用式压缩现状）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-context-model.md` §8.2/§8.3、`nop-ai-agent-llm-layer.md`、`nop-ai-agent-reliability.md`、`nop-ai-agent-usage-and-billing.md`、`nop-ai-agent-session-and-storage.md` §16.4
