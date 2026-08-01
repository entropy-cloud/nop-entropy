# 2 压缩前 snapshot 归档与压缩比度量（Compaction Snapshot Archive & Ratio）

> Plan Status: active
> Last Reviewed: 2026-08-02
> Draft Consensus: 2 轮独立子 agent 对抗性审查通过（r1 修 3 Major+5 Minor；r2 CONSENSUS yes，4 项非阻断 condition 已并入）
> Source: `ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md` W4-2；`ai-dev/analysis/agent-survey/2026-08-01-beads-versioned-graph-memory-analysis.md`；`ai-dev/design/nop-ai-agent/nop-ai-agent-context-model.md` §8.3
> Mission: nop-ai-agent-harness-evolution
> Work Item: W4-2
> Related: `2026-08-02-0900-1-nop-ai-agent-reference-compaction-dual-track.md`（前置，引用式压缩）；`nop-ai-agent-reliability.md` §7、§5.4（checkpoint / COMPACTION 触发点）

## Purpose

为上下文压缩管线补上"可逆性 + 可度量性"：压缩前对消息历史做 snapshot 归档（保留原文可回溯），记录压缩比（`originalSize`/`compactedSize`），并在压缩失败时显式保留原文不覆写。使压缩从"单向有损黑盒"变为"可回溯、可度量、失败安全"的管线。本计划覆盖摘要式与引用式（W4-1）两类压缩，是管线级的横切安全/可观测增强。

## Current Baseline

基于 live repo（`nop-ai/nop-ai-agent/`）核对：

- **压缩结果（已有，部分可度量）**：`session/CompactionResult(sessionId, tokensBefore, tokensAfter, retainedMessageCount, snapshotId, compactedMessages)`。`tokensBefore`/`tokensAfter` 已存在，**压缩比可由 token 维度计算**（`tokensAfter/tokensBefore`）。缺**消息条数维度**的 `originalSize`/`compactedSize` 显式度量与上报。
- **`snapshotId` 字段（已有，未接线）**：`CompactionResult.snapshotId` 存在（字段 `final`，构造后不可变），但 `PipelineCompactor` 全程传 `null`。注意 `PipelineCompactor.compact()` 实际有 **4 条** CompactionResult 构造路径：`:63`（messages 空）、`:69`（strategies 空→走 NoOpContextCompactor，间接构造）、`:122-123`（成功路径）、`:125-126`（未减 token 路径）。`:122-126` 是 pipeline 完整跑完后的最终结果构造点（两条分支都 new 结果），是注入 snapshotId 的天然单点；`:63/:69` edge 路径（dev/test 空配置）按裁定 G 保持 NoOp 语义返回 null（与 archive 的 snapshotId 不进同一结果对象——coordinator LOG 仍可观测）。
- **COMPACTION checkpoint（已有）**：`engine/AgentCompactionCoordinator.performCompaction` 在真实压缩成功后（消息替换 + token 调整后）发射 `COMPACTION` 类型 checkpoint（plan 187 落地），`compactSummary`（coordinator `:114-116`）= "token before→after + 单个 retainedMessageCount"，**非两维度完整度量**。这是事后基线标记，不是压缩前原文归档。
- **失败路径分两层（核对厘清）**：
  - **策略层**：`PipelineCompactor` 对单 strategy 异常 try-catch + `LOG.warn` + `continue` 跳过该层（`:96-100`，**已有日志**）。
  - **coordinator 层**：`AgentCompactionCoordinator.performCompaction` 在 `result.getCompactedMessages() != null` 为 false（全部 strategy 未产出）时**静默跳过替换、无任何 LOG**（`:94` false 分支）——这才是"显式记录失败"要补的**真实洞**。另外 coordinator 对 `contextCompactor.compact()` 调用**无 try-catch**（`:90`），非 PipelineCompactor 的自定义 compactor 抛异常会冒泡。本计划失败语义主要补 coordinator 层（Phase 1 裁定具体层级）。
- **与 design §8.3 现存自相矛盾（须显式修正）**：设计源 `nop-ai-agent-context-model.md:207` 现写「与 checkpoint append-only 天然一致（**归档即 checkpoint 的 compaction 类型**）」——与本计划核心边界"归档 ≠ checkpoint snapshot.json"**直接冲突**。Phase 1 必须显式点名改正这句（见 Phase 1 裁定 + Exit Criteria）。
- **PRE_COMPACT / POST_COMPACT 钩子（已有）**：`performCompaction` 中 `PRE_COMPACT`（middleware，压缩前）+ `POST_COMPACT`（hook，压缩后）已是现成挂载点。
- **checkpoint snapshot.json（独立 successor）**：reliability §5.4 明确"compaction-triggered snapshot.json 文件生成仍是独立 successor"——本计划**不**等于 checkpoint snapshot.json 文件生成；本计划的归档是压缩管线内部、为可回溯/失败安全服务的原文副本，与 checkpoint 子系统的 resume-point snapshot.json 是两个关注点（边界在 Phase 1 裁定）。
- **真正剩余的 gap**：
  - 压缩前原文 snapshot 归档未实现（`snapshotId` 恒 null），且 snapshotId 数据流未接通（`CompactionResult` 字段 final、`PipelineCompactor` 不持归档、`CompactionContext` 不携 snapshotId）。
  - 消息条数维度的 `originalSize`/`compactedSize` 显式度量缺失（与既有 `retainedMessageCount` 语义重叠，须裁定去留）。
  - coordinator 层 `compactedMessages==null` 静默跳过无失败记录（策略层有 LOG、coordinator 层无）。
  - design §8.3:207 "归档即 checkpoint" 自相矛盾未修正。

## Goals

- 压缩前对当前消息历史做 snapshot 归档，产出非 null `snapshotId`（可回溯原文）。
- `CompactionResult` 显式携带 `originalSize`/`compactedSize`（消息条数维度）+ 复用既有 token 维度，使压缩比可在两个维度度量。
- 压缩失败时显式保留原文 archive + 记录失败结果（fail-loud 可观测，非静默"没改"）。
- 覆盖摘要式与引用式（W4-1）两类压缩（管线级横切，不绑定具体策略）。
- 零回归：`snapshotId` null 历史值仍被现有消费方接受（向后兼容）。

## Non-Goals

- **不做** checkpoint 子系统的 `snapshot.json` 文件生成（reliability §5.4 独立 successor，边界在 Phase 1 显式裁定）。
- **不做** snapshot 归档的持久化后端（DB / 文件）——首版 in-session / 内存归档，跨进程持久化是 successor。
- **不做**从 snapshot 归档**自动回滚**消息历史（"可回溯"= 原文可取回供审计/调试，非自动恢复；自动回滚是独立语义）。
- **不做**压缩比的可观测面板/指标上报系统（仅产出度量值；面板是 observability 增强）。
- **不改** `PipelineCompactor` 的 escalation/relief 触发逻辑（本计划只在外围加 archive + 度量）。
- **不依赖** W4-1 落地（本计划对现有摘要式管线独立成立；W4-1 落地后自然覆盖引用式）。
- **不复用** W4-1 的引用归档基础设施（W4-1 归档=hash 寻址 per-content；本计划归档=snapshotId 寻址 per-compaction-event 整段历史；两者独立）。注：`snapshotId` 术语已存在于 `CheckpointSnapshot`（reliability）与 `SessionSnapshot`（session），本计划引入第三个 compaction-archive snapshotId，design 须文档化三套命名空间。

## Scope

### In Scope

- 压缩前 snapshot 归档抽象 + 首版 in-session 内存实现（按 snapshotId 寻址取回原文消息列表）。
- `AgentCompactionCoordinator.performCompaction`：压缩前 archive 原文 → 压缩 → 把非 null `snapshotId` + `originalSize`/`compactedSize` 写入结果/记录。
- `CompactionResult` 扩展：`originalSize`/`compactedSize`（消息条数维度）+ `snapshotId` 非 null 化（向后兼容保留既有构造器/null 语义）。
- 失败路径：压缩异常/无产出时显式保留原文 archive + 记录失败可观测结果（不静默）。
- 与 checkpoint `COMPACTION` 触发点的边界裁定（归档 ≠ checkpoint）。
- design §8.3 回写为最终架构决策。

### Out Of Scope

- snapshot 归档持久化（DB/文件/跨进程）。
- checkpoint `snapshot.json` 文件生成（§5.4 successor）。
- 自动回滚消息历史到 snapshot。
- 压缩比指标的上报/面板/告警系统。

## Execution Plan

### Phase 1 - 设计裁定与边界划分

Status: planned
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-context-model.md` §8.3（含修正 :207 自相矛盾）；与 `nop-ai-agent-reliability.md` §5.4 的边界

- Item Types: `Decision`

- [ ] **裁定 A — snapshotId 数据流**（解决"CompactionResult 字段 final + PipelineCompactor 不持归档 + CompactionContext 不携 snapshotId"）：**采纳** coordinator 在 `compact()` **前** archive 原文 → 产出 snapshotId → 经 **`CompactionContext` 新增 snapshotId 字段**（`PipelineCompactor.rebuildContext` 须传播该字段）传入 → `PipelineCompactor` 在最终结果唯一构造点（`:121-126`）把 `ctx.getSnapshotId()` + `originalSize`/`compactedSize` 填入 CompactionResult。**不动 strategy 内部的 CompactionResult 构造**（strategy 产中间结果，PipelineCompactor 在 `:121-126` 重新构造最终结果为唯一权威）。**拒绝**让 strategy 各自读归档（爆炸半径大）、拒绝 coordinator 在 compact() 后改 final 字段（不可变）
- [ ] **裁定 B — 归档存储边界**：归档键（`snapshotId` 寻址）、首版 in-session 内存实现、生命周期（会话级释放）、**与 checkpoint `snapshot.json` 的明确边界**（归档=压缩管线内部原文副本供可回溯/失败安全；checkpoint snapshot.json=resume-point 持久化缓存，§5.4 独立 successor）
- [ ] **裁定 C — 显式修正 design §8.3:207 自相矛盾**：现文「归档即 checkpoint 的 compaction 类型」与本计划核心边界冲突，**必须改正**为"归档 ≠ checkpoint snapshot.json（§5.4 successor），二者是两个关注点"
- [ ] **裁定 D — 度量维度与字段去重**：`originalSize`（压缩前消息条数）/`compactedSize`（压缩后消息条数）作为新维度；与既有 `retainedMessageCount`（成功路径=压缩后条数、失败路径=原始条数，语义含混）**裁定关系**——是否废弃 `retainedMessageCount` 改用 `compactedSize`，或并存（须明确权威性，避免冗余）。压缩比定义两维度各一（消息条数 + token）。**新增字段须同步更新 `CompactionResult.equals/hashCode`**（现不含新字段，`CompactionResult.java:57-73`），避免语义不一致
- [ ] **裁定 E — 归档时机**：在 `PRE_COMPACT` 之后、`contextCompactor.compact()` 之前 archive 原文（保证压缩失败时原文副本已存在）。**构造顺序**：coordinator 须在 archive **之后** new `CompactionContext`（把 snapshotId 传入），即移动现有 `AgentCompactionCoordinator:79-86` 的 CompactionContext 构造到 archive 之后（CompactionContext 字段 final，不可后置 setter）
- [ ] **裁定 F — 失败记录层级**（厘清两层）：**coordinator 层**补两处静默：(1) `:94` false 分支（compactedMessages==null）+ (2) `:98 else` 分支（compactedMessages 非 null 但 `tokensAfter >= tokensBefore`，即"压缩尝试但未减 token"）——两处均补 `LOG.warn`（含 snapshotId + 原因）；区分"未触发压缩"（不 archive、不记录失败）与"archive 后压缩未产出/未减"（archive 保留 + 显式失败记录）。策略层 `:96-100` 已有 LOG 不动。coordinator 对 `compact()` 加 try-catch（捕获→保留 archive+LOG.warn 含 snapshotId，不冒泡中断 agent）
- [ ] **裁定 G — 向后兼容**：`snapshotId`/`originalSize`/`compactedSize` 新字段对既有 `CompactionResult` 消费方（`AgentCompactionCoordinator`、测试）保持兼容；既有 5 参/6 参构造器不破坏；`NoOpContextCompactor` 仍返回 snapshotId=null（`TestNoOpContextCompactor` 断言 null 不破）

Exit Criteria:

- [ ] design §8.3 重写为最终架构决策（含 A–G 七条裁定结论 + 拒绝的替代方案），**并显式改正 :207 "归档即 checkpoint" 为边界声明**
- [ ] design 在 reliability §5.4 交叉引用处明确"归档 ≠ checkpoint snapshot.json"边界
- [ ] design 描述的 snapshotId 数据流（coordinator→CompactionContext→PipelineCompactor:121-126）、归档键、度量维度、失败层级可在 Phase 2/3 直接落地（无臆想空间，无 item 间矛盾）
- [ ] **无静默跳过**：design 明确 coordinator 层压缩失败须显式 LOG.warn（含 snapshotId），不静默吞（见 Minimum Rules #24）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - snapshot 归档与结果扩展

Status: planned
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/compact/`（含 `CompactionResult` 在 session 包、归档抽象在 session 包）；`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentCompactionCoordinator.java`

- Item Types: `Proof`

- [ ] 新增压缩前 snapshot 归档抽象（`put(messages)→snapshotId` + `get(snapshotId)→messages`）+ 首版 in-session 内存实现
- [ ] `CompactionResult` 扩展 `originalSize`/`compactedSize`（消息条数维度，按裁定 D 处理与 `retainedMessageCount` 的关系）+ 既有 `snapshotId` 非 null 化；保留既有 5 参/6 参构造器向后兼容（新字段为可选/默认）
- [ ] `CompactionContext` 新增 `snapshotId` 字段（裁定 A），`PipelineCompactor.rebuildContext` 传播该字段
- [ ] `PipelineCompactor` 最终结果构造点（`:121-126`）不再硬编码 `snapshotId=null`：从 `ctx.getSnapshotId()` 取 + 填 `originalSize`(messages.size())/`compactedSize`(current.size())（裁定 A；不动 strategy 内部构造）
- [ ] `AgentCompactionCoordinator.performCompaction`：`PRE_COMPACT` 后、`compact()` **前** archive 原文 → 把 snapshotId 经 CompactionContext 传入管线（裁定 A + E）
- [ ] **失败记录补 coordinator 层**（裁定 F）：`compact()` 加 try-catch（捕获→保留 archive+LOG.warn 含 snapshotId，不冒泡）；`:94` false 分支（compactedMessages==null）补 LOG.warn（含 snapshotId + 原因），区分"未触发压缩"（不 archive）与"archive 后未产出"（archive 保留+记录）。策略层 `:96-100` 已有 LOG 不动

Exit Criteria:

- [ ] 真实压缩发生后 `CompactionResult.snapshotId` 非 null，且 `get(snapshotId)` 取回的消息列表与压缩前原文一致
- [ ] `originalSize`/`compactedSize` 正确反映压缩前后消息条数；与 `retainedMessageCount` 关系按裁定 D 落地（无冗余/权威性明确）；token 维度 `tokensBefore`/`tokensAfter` 行为不变
- [ ] **数据流接通验证**：snapshotId 从 coordinator 的 archive → CompactionContext → PipelineCompactor `:121-126` → CompactionResult 全链路可追溯（单测断言每一跳，见 Anti-Hollow）
- [ ] 压缩失败时：原文 archive 已保留（`snapshotId` 可取回原文）+ coordinator 层有显式 LOG.warn 记录（含 snapshotId）
- [ ] **向后兼容**：既有 compact 测试全过（`TestNoOpContextCompactor` 断言 snapshotId==null 不破；新字段不破坏既有构造器/null 语义）
- [ ] **接线验证**：`AgentCompactionCoordinator` 确在 `compact()` 前调用归档 + snapshotId 经 CompactionContext 流入 PipelineCompactor 最终结果（单测断言，见 Minimum Rules #23）
- [ ] **无静默跳过**：coordinator `:94` false 分支不再静默；归档/取回失败显式异常或显式失败记录，不空体/吞异常（见 Minimum Rules #24）
- [ ] 新增归档/失败路径有单测：真实压缩（archive 产出 + 可取回）/ 压缩失败（archive 保留 + coordinator 显式 LOG）/ 未触发压缩（不 archive）三条（见 Minimum Rules #25）
- [ ] design §8.3 与落地一致（含 :207 已修正）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 压缩比度量与端到端验证

Status: planned
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentCompactionCoordinator.java`（度量记录）；端到端压缩→归档→度量

- Item Types: `Proof`

- [ ] `AgentCompactionCoordinator` 在真实压缩成功后记录压缩比（消息条数维度 `compactedSize/originalSize` + token 维度 `tokensAfter/tokensBefore`），写入 COMPACTION checkpoint 的 `compactSummary`（coordinator `:114-116`，扩展现有格式）或等价可观测位置
- [ ] COMPACTION checkpoint 的 `compactSummary` 含 snapshotId（可回溯关联），与既有 token/retainedMessageCount 合并为完整两维度度量
- [ ] 失败可观测：压缩失败的 LOG.warn 与成功压缩的压缩比记录在统一可检索位置（审计可区分成功/失败）

Exit Criteria:

- [ ] **端到端验证**（见 Minimum Rules #22）：从 `performCompaction` 触发 → 压缩前 archive 原文 → 管线压缩 → 结果携带非 null `snapshotId` + `originalSize`/`compactedSize` → COMPACTION checkpoint `outputSummary` 含 snapshotId + 两维度压缩比 → `get(snapshotId)` 取回原文一致，完整路径有一条集成测试
- [ ] **接线验证**：压缩比度量确被 `performCompaction` 在真实压缩成功路径记录（断言成功路径写入度量 / 失败路径写入失败记录，见 Minimum Rules #23）
- [ ] **无静默跳过**：成功/失败两路径都有显式可观测记录，无空方法体/吞异常（见 Minimum Rules #24）
- [ ] 新增度量/端到端有单测（见 Minimum Rules #25）
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [ ] design §8.3 / reliability §5.4 边界与落地一致
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 本计划涉及代码变更，构建验证条目必填。

- [ ] 压缩前 snapshot 归档已落地：`snapshotId` 非 null + 原文可取回
- [ ] 压缩比两维度度量（消息条数 + token）已落地并记录
- [ ] 失败路径显式保留原文 + 显式可观测记录（fail-loud，非静默）
- [ ] 端到端"压缩→归档→度量→取回"已验证（Anti-Hollow：运行时连通）
- [ ] 与 checkpoint `snapshot.json`（§5.4）边界明确，无 scope 越界
- [ ] 零回归：既有 compact/compaction 测试全过；`snapshotId` null 历史值向后兼容
- [ ] design §8.3 已回写为最终架构决策；reliability §5.4 边界已标注
- [ ] 受影响 owner docs 已同步
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：归档确被 `performCompaction` 压缩前调用 + snapshotId 流入结果 + 取回连通
- [ ] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### snapshot 归档的持久化后端（DB / 文件 / 跨进程）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 首版 in-session 内存归档即可使压缩可回溯 + 失败安全 + 压缩比度量端到端成立；跨进程/跨重启回溯是独立增强。
- Successor Required: yes
- Successor Path: snapshot 归档持久化 successor（与 session store / checkpoint 持久化同向）

### checkpoint `snapshot.json` 文件生成（reliability §5.4）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: §5.4 已明确为独立 successor；本计划归档是压缩管线内部原文副本，与 resume-point 持久化缓存是两个关注点（Phase 1 裁定边界）。
- Successor Required: yes
- Successor Path: reliability §5.4 compaction-triggered snapshot.json successor

### 从 snapshot 自动回滚消息历史

- Classification: `optimization candidate`
- Why Not Blocking Closure: 本计划"可回溯"=原文可取回供审计/调试；自动回滚是不同语义（改变消息历史），独立增强。
- Successor Required: no

## Non-Blocking Follow-ups

- 压缩比指标的上报/面板/告警——本计划仅产出度量值，面板是 observability 增强。
- snapshot 归档的过期清理策略（会话级释放之外）——长 session 内存占用优化项。

## Closure

Status Note: <<完成时填写>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
