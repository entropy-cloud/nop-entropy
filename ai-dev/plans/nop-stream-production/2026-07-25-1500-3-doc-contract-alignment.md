# 文档合同对齐与 source-anchors 补全（D69—D73, Doc）

> Plan Status: active
> Last Reviewed: 2026-07-25
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 22；`ai-dev/analysis/nop-stream/08-gap-analysis.md` D69—D73；`ai-dev/audits/nop-stream-flink-comparison/2026-07-24-2227-multi-audit-nop-stream-flink-comparison.md`
> Mission: nop-stream-production
> Work Item: 22
> Related: Plan 2026-07-25-0800-3（Deferred "Pre-existing broken doc links" routed to Stage 22）；`ai-dev/design/nop-stream/01-architecture-baseline.md`、`checkpoint-design.md`、`graph-model-design.md`、`cep-design.md`

## Purpose

消除 nop-stream 文档与 live 代码的落差，补全 `source-anchors.md` 的 nop-stream 运行时/执行锚点，记录 nop-stream 独有的设计契约（D69—D73）并**协调现有文档间的层数/语义矛盾**，纠正 `cep-design.md` 过时表述，并收口 Plan 16 deferred 的文档链接健康度问题。**纯文档计划，无代码变更。**

## Current Baseline

> 所有引用均为 live repo 核对结果。

### source-anchors 现状
- `docs-for-ai/04-reference/source-anchors.md` 已有 nop-stream 段（179-202，STRM-001..020），但偏重 model/graph/state-serialization/timer，**缺失运行时执行与 checkpoint 关键类**：`InputGate`、`CheckpointBarrierTracker`、`StreamTaskInvokable`、`GraphModelCheckpointExecutor`、`TaskExecutor`、`SubtaskTask`、`ResultPartition`、`RecordWriter`、`AbstractStreamOperator`、`OperatorChain`、`TimestampsAndWatermarksOperator`、`CepOperator`、`BarrierAligner`（`@Deprecated` reference）。注意其中多数（InputGate/CheckpointBarrierTracker/StreamTaskInvokable/TaskExecutor/SubtaskTask/ResultPartition/RecordWriter）位于 `nop-stream-core/.../execution/`（非 runtime）。这些是近期 plan（14/15/16）反复核对的核心类，缺锚点迫使后续 AI 直读源码。

### D69—D73 现状（部分已成文，非"尚未成文"）
- **D69 EFFECTIVELY_ONCE / D70 Epoch-based recovery 已部分成文**于 `checkpoint-design.md`：§4.1 四保证表含 `EFFECTIVELY_ONCE`、§4.3 配置映射、§13.3 缓解；§2.1"Epoch 是一致性中心"+ §8"故障恢复模型"（全局 epoch 恢复）。**真实 gap 不是"未写"，而是未以"vs Flink 的有意设计差异"框架呈现**。
- D71 四层图模型、D72 IMessageService 数据面、D73 ClusterRegistry JDBC durability 为有意设计差异，待成文。
- **D71 存在三处层数口径冲突（本 plan 须先协调，不得盲写"四层"）**：
  - `graph-model-design.md:20` §1.1 选"Flink 风格两层图模型（StreamGraph → JobGraph）"，并设"为什么不是三层"段落拒绝 Flink `ExecutionGraph`；§8(288) 比较表"图层数 2 层"。
  - `01-architecture-baseline.md:93` §四"**五层执行管线**"（StreamModel → StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan → RuntimeTopology，实为 6 名）。
  - `07-distributed-comparison.md:70` 比较表把 nop-stream 列为"4 layers: StreamGraph→JobGraph→PartitionedPlan→DeploymentPlan"；line 590 D20 又称"Four-layer graph model"。另该文件 line 70 称 Flink 为"Two-layer: StreamGraph→ExecutionGraph"（把 JobGraph 当序列化格式），与 graph-model-design §1.1"为什么不是三层（Flink 有 ExecutionGraph）"口径不一致——Flink 规范为 StreamGraph→JobGraph→ExecutionGraph 三层。

### §四 与 cep-design 现状
- `01-architecture-baseline.md` §四标题实为"**四、执行模型**"（非"执行管线"），当前内容是管线层职责表。`checkpoint-design.md` §2.2 已详述 `handleBarrierNonRecursive`——§四若补充执行细节须界定与 §2.2 的分工边界，避免重复。
- `cep-design.md`（line 237、298 等）仍有过时 `SimpleKeyedStateStore` 表述；live CEP 已统一 `IKeyedStateBackend`（`CepOperator.java:209`）。

### 文档链接健康度（Plan 16 deferred 项；baseline 已变化）
- Plan 16 记录"92 条 HEAD 预有 broken links"——**该数字已过期**（Plan 16 closure 时与 HEAD parity 为 92；此后仓库演进，当前 `check-doc-links --strict` 计数已不同）。本 plan 须在执行起点**重新测量**当前 HEAD 计数作为 parity 基线。
- 主要成分仍存：(a) nop-stream 自有 roadmap/design 中 `Module / area:` 把目录路径当 markdown 链接（跨两个 roadmap约 50+ 处）；(b) 缺失 analysis 文件引用（01-flink-source-audit.md、02-nopstream-live-audit.md）。
- **关键约束**：01-flink-source-audit.md/02-nopstream-live-audit.md 是**活跃 plan 316 / 317 的交付物**（plan 316:76、317:78 明列），并被活跃对比 plan（318、`2026-07-24-1000-*` 系列、`08-gap-analysis`、`06-cep` 等）引用。这些是**待交付物，非悬空孤儿引用**——**本 plan 不得移除或改指它们**，否则破坏 sibling 活跃 plan 的契约。

## Goals

- `source-anchors.md` 补全 nop-stream 运行时/执行/checkpoint 锚点（STRM-021+），路径与 live 一致。
- **协调 D71 层数口径**：区分"图模型（2 层：StreamGraph→JobGraph）"、"执行管线（5 层：StreamModel→…→RuntimeTopology）"、"部署计划分层（PartitionedPlan→DeploymentPlan）"三种视角，使三份文档口径自洽，并记录 D71 为有意设计（不盲写"四层"）。
- D69/D70 以"vs Flink 有意设计差异"框架补充进既有 `checkpoint-design.md` 段落（非重复造）；D72/D73 成文。
- `01-architecture-baseline.md` §四（执行模型）修订至 live，并界定与 `checkpoint-design.md` §2.2 分工。
- `cep-design.md` 纠正 `SimpleKeyedStateStore` 说法至 live。
- 收口 Plan 16 deferred 链接健康度：规范化 nop-stream 自有 `Module / area:` 模式；**不动活跃 plan 316/317 的待交付引用**；repo-wide 剩余 adjudicate。

## Non-Goals

- 任何代码变更（roadmap Stage 22 明示 Out of scope: 代码变更）。
- 处理活跃 plan 316/317（及其引用方）的 01-flink-source-audit.md/02-nopstream-live-audit.md 待交付引用——归 316/317 交付。
- analysis 文件实质内容撰写。
- 其他模块的 broken doc links。
- 改写已完成历史 plan 文本（Minimum Rule 20）。

## Scope

### In Scope

- `source-anchors.md` 补全运行时/执行/checkpoint 锚点。
- D71 层数口径协调（跨 graph-model-design / architecture-baseline / 07-dist）。
- `01-architecture-baseline.md` §四（执行模型）修订 + 与 §2.2 分工界定。
- D69/D70 以 vs-Flink 差异框架补充；D72/D73 成文。
- `cep-design.md` SimpleKeyedStateStore 纠正。
- 规范化 nop-stream 自有 `Module / area:` 模式；重新测量 parity 基线；活跃 plan 待交付引用显式排除。

### Out Of Scope

- 活跃 plan 316/317 的待交付 analysis 引用处理。
- analysis 文件实质撰写。
- 其他模块 broken links。
- 代码变更与历史 plan 回写。

## Execution Plan

### Phase 1 — source-anchors 补全 + §四修订 + D71 层数协调

Status: planned
Targets:
- `docs-for-ai/04-reference/source-anchors.md`（179-202）
- `ai-dev/design/nop-stream/01-architecture-baseline.md`（§四 执行模型）
- `ai-dev/design/nop-stream/graph-model-design.md`（§1.1/§8 层数口径）
- `ai-dev/analysis/nop-stream/07-distributed-comparison.md`（line 70/590 Flink 层数口径，仅文档校正）

- Item Types: `Fix | Decision`

- [ ] `Fix` 在 `source-anchors.md` 补全 nop-stream 运行时/执行/checkpoint 锚点（STRM-021+）：至少覆盖 `InputGate`、`CheckpointBarrierTracker`、`StreamTaskInvokable`、`GraphModelCheckpointExecutor`、`TaskExecutor`、`SubtaskTask`、`ResultPartition`、`RecordWriter`、`AbstractStreamOperator`、`OperatorChain`、`TimestampsAndWatermarksOperator`、`CepOperator`、`BarrierAligner`（注明 `@Deprecated` reference）。**逐条核对 live 路径**（多数在 `nop-stream-core/.../execution/`，`GraphModelCheckpointExecutor` 在 runtime），锚点说明标注实际模块位置。
- [ ] `Decision` **协调 D71 层数口径**：先审计三处口径冲突（graph-model-design §1.1/§8 的"2 层图模型"、architecture-baseline §四 标签"五层执行管线"但表格实列 **6 项** StreamModel→…→RuntimeTopology、07-dist 的"4 layers/DeploymentPlan"），再裁定统一框架——区分(a)图模型 **2 层**（StreamGraph→JobGraph）；(b)执行管线（层数**经审计裁定**：标签与 6 项表格不一致须在审计中修正——改标签或调表格）；(c)部署计划分层（PartitionedPlan→DeploymentPlan）。据此把 D71 记录为有意设计，**不盲写"四层"**。**07-dist:70 比较行口径裁定**：采用"图模型层数"口径（Flink **3 层** StreamGraph→JobGraph→ExecutionGraph vs nop-stream **2 层**），与 graph-model-design §1.1"为什么不是三层"叙事一致；nop-stream 的 PartitionedPlan/DeploymentPlan 作为独立的"部署抽象"维度记录，**不计入图模型层数**（避免与 §1.1 矛盾）。校正 07-dist line 70/590 对 Flink 的层数表述（Flink 为 3 层，非 2 层）。
- [ ] `Fix` 修订 `01-architecture-baseline.md` §四（**执行模型**）：反映 InputGate 对齐、`StreamTaskInvokable` 主循环、checkpoint 触发链等 live 事实；**界定与 `checkpoint-design.md` §2.2 的分工边界**（§四给管线层职责与执行模型概览，§2.2 保留 barrier 协议细节），避免重复。

Exit Criteria:

- [ ] `source-anchors.md` 新增锚点对应 live 文件路径全部存在（逐条核对，含正确模块标注）
- [ ] D71 层数口径在三处文档自洽（图模型 2 层 / 执行管线层数经审计修正标签或表格 / 部署分层独立维度），不出现互斥的层数断言；07-dist 比较行采用图模型层数口径（Flink 3 / nop-stream 2）
- [ ] 07-dist 对 Flink 层数表述与 graph-model-design §1.1 一致
- [ ] `01-architecture-baseline.md` §四与 `checkpoint-design.md` §2.2 分工明确（无重复矛盾）
- [ ] **文档可观测性**：每条新锚点可在仓库定位到文件；§四类名/方法名与源码一致
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — nop-stream 独有语义与平台复用设计差异（D69/D70/D72/D73 + cep 纠正）

Status: planned
Targets:
- `ai-dev/design/nop-stream/checkpoint-design.md`（§4.1/§2.1/§8 补 vs-Flink 差异框架）
- `ai-dev/design/nop-stream/01-architecture-baseline.md`（§五 IMessageService、ClusterRegistry 段落补有意设计）
- `ai-dev/design/nop-stream/cep-design.md`（line 237/298 等 SimpleKeyedStateStore）

- Item Types: `Fix | Decision`

- [ ] `Decision` D69/D70：在 `checkpoint-design.md` 既有段落（§4.1 EFFECTIVELY_ONCE、§2.1/§8 epoch 恢复）**补充 vs Flink 的有意设计差异框架**（不重复既有内容，增补"与 Flink 差异点 + 为何如此设计"）。
- [ ] `Decision` D72：数据面 remote transport 复用 `IMessageService` 为有意设计（入 architecture-baseline §五 或对应段落），与 Stage 40 未来接线对齐，含有意设计理由。
- [ ] `Decision` D73：`ClusterRegistry` JDBC durability 为有意简化（入相关段落），说明取舍（与 Stage 41 决策点 D7 关联）。
- [ ] `Fix` 纠正 `cep-design.md` 的 `SimpleKeyedStateStore` 说法（line 237/298 等）：CEP 已统一 `IKeyedStateBackend`（`CepOperator.java:209`），移除/改正过时表述。

Exit Criteria:

- [ ] D69/D70/D72/D73 四条设计差异均以"有意设计差异"框架成文，含有意设计理由（非 Proposed/Current 对比、非历史叙事，符合 Minimum Rule 14）
- [ ] D69/D70 不与既有 checkpoint-design 段落重复（增补差异框架而非重写）
- [ ] `cep-design.md` 不再含 `SimpleKeyedStateStore` 过时说法，与 live 一致
- [ ] **文档可观测性**：每条设计差异引用的 live 类/机制在源码可定位
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — 文档链接健康度收口（Plan 16 deferred successor）

Status: planned
Targets:
- `ai-dev/backlog/nop-stream-production-roadmap.md`、`ai-dev/backlog/nop-stream-flink-comparison-roadmap.md`（`Module / area:` 模式）
- `ai-dev/design/nop-stream/*.md`（nop-stream 自有 broken links）

- Item Types: `Fix | Decision | Proof`

- [ ] `Proof` **重新测量 parity 基线**：执行起点运行 `node ai-dev/tools/check-doc-links.mjs --strict` 记录当前 HEAD 总 broken-link 数（Plan 16 的"92"已过期），作为本 plan parity 基线。
- [ ] `Fix` 规范化 nop-stream 自有文档的 `Module / area:` 目录路径模式：统一改为非链接形式（去反引号或改为纯文本标签），消除该类 broken link（统一一种规范化方式，避免不一致）。
- [ ] `Decision` **显式排除活跃 plan 待交付引用**：01-flink-source-audit.md/02-nopstream-live-audit.md 为 plan 316/317 交付物，及其在 318/`2026-07-24-1000-*`/`08-gap-analysis`/`06-cep` 等活跃 plan/analysis 中的引用——**本 plan 不动**，记录为"待 316/317 交付后自愈"。仅处理真正与 nop-stream 自有文档绑定的孤儿悬空引用（若有）。
- [ ] `Proof` 验证 nop-stream 自有文档 `check-doc-links --strict` **不引入新 broken link**（与执行起点基线 parity：nop-stream 自有文档 broken-link 数 ≤ 基线）。本 plan 文件自身的 analysis 文件提及已用纯文本（非反引号链接），不产生新 broken link。

Exit Criteria:

- [ ] parity 基线已在执行起点记录（当前 HEAD 计数，非过期"92"）
- [ ] nop-stream 自有文档 `Module / area:` 模式已规范化（该类 broken link 清零或归 nop-stream 自有部分清零）
- [ ] 活跃 plan 316/317 待交付引用未被触碰（显式排除，记录自愈路径）
- [ ] nop-stream 自有文档无新增 broken link（parity 验证）
- [ ] repo-wide 剩余 broken link（其他模块、316/317 待交付）显式 adjudicate 为 out-of-scope/待交付并记录
- [ ] **文档可观测性**：`check-doc-links --strict` 输出可复现 parity 结论
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 本 plan 为纯文档计划（无代码变更），按 guide 规定移除 `./mvnw test`/`./mvnw lint` 构建验证条目。

- [ ] `source-anchors.md` 补全的运行时锚点全部指向 live 文件（含正确模块标注）
- [ ] D71 层数口径在三处文档自洽；D69—D73 全部以有意设计差异框架成文
- [ ] `01-architecture-baseline.md` §四与 live 一致且与 `checkpoint-design.md` §2.2 分工明确
- [ ] `cep-design.md` SimpleKeyedStateStore 说法已纠正
- [ ] nop-stream 自有文档无新增 broken link（基于执行起点重测基线的 parity）；活跃 plan 待交付引用未被破坏
- [ ] 无 in-scope doc drift 被静默降级
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 的 nop-stream 自有部分 parity 成立（基于重测基线，证据写入）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <this-plan-file> --strict` 退出码 0
- [ ] 独立子 agent closure-audit 完成并写入证据（抽查文档与 live repo 代码一致性）

## Deferred But Adjudicated

### 活跃 plan 316/317 的 analysis 待交付引用

- Classification: `out-of-scope (owned by sibling active plans)`
- Why Not Blocking Closure: 01-flink-source-audit.md/02-nopstream-live-audit.md 是活跃 plan 316/317 的交付物，被多个活跃对比 plan 引用；移除或改指会破坏 sibling plan 契约。待 316/317 交付后这些引用自愈。
- Successor Required: `no`（由 316/317 交付解决）

### Repo-wide 非 nop-stream broken doc links

- Classification: `out-of-scope repo-wide doc hygiene`
- Why Not Blocking Closure: 其他模块 broken links 与本 mission 无关，属 repo-wide 治理。
- Successor Required: `no`

## Non-Blocking Follow-ups

- `source-anchors.md` 可随 Stage 推进持续补全（如 Stage 17 mailbox 落地后补 mailbox 锚点）。
- 316/317 交付后，相关 analysis 引用自愈，可在后续 doc 维护中清理冗余表述。

## Closure

Status Note: <<完成时填写>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Audit Session: <<session id>>
- Evidence: <<每条 Exit Criterion/Closure Gate 的 PASS/FAIL + live doc path / check-doc-links parity 输出>>
