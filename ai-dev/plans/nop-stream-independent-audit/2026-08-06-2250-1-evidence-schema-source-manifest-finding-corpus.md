# 1 Evidence Schema, Source Manifest & Finding Corpus (nop-stream Independent Audit)

> Plan Status: active
> Last Reviewed: 2026-08-06
> Source: `ai-dev/backlog/nop-stream-independent-audit-roadmap.md` (Stage 4); `ai-dev/analysis/2026-08/2026-08-06-nop-stream-audit-baseline-and-roadmap-analysis.md`; live repo baseline of `nop-stream/` (10 submodules) + `ai-dev/audits/nop-stream-production/`
> Mission: nop-stream-independent-audit
> Work Item: 4. Audit evidence schema, source manifest and finding corpus
> Related: First successor audit plan of this mission. Hard prerequisite for roadmap Stages 5-23. Depends on the three active production remediation plans (Stages 1-3) only to the extent that their confirmed-defect set must be reflected in the frozen finding corpus; this plan does not block on their execution completion because the corpus captures their planned scope, not their closure state.

## Purpose

在做出任何能力声明（capability claim）之前，冻结 nop-stream 独立深度审计的"度量衡"：一份**有界的 source manifest**（带可复现的选择命令与期望分母）、一份**冻结的 finding corpus**（按 Stages 18-22 分片）、一套**证据行 schema**（含分类词汇表）和一个**校验器**（拒绝缺字段/未知字段/分母不匹配）。后续 Stages 5-23 只能在本计划冻结的度量衡内取证与分类，不得各自发明格式或口径。

## Current Baseline

经 2026-08-06 live repo 核对：

- **模块布局**：`nop-stream/` 含 10 个 reactor 子模块 — `nop-stream-core`、`nop-stream-runtime`、`nop-stream-flow`、`nop-stream-cep`、`nop-stream-rocksdb`、`nop-stream-connector`、`nop-stream-connector-batch`、`nop-stream-connector-jdbc`、`nop-stream-connector-debezium`、`nop-stream-fraud-example`（`nop-stream/pom.xml` + 目录枚举）。`docs-for-ai/01-repo-map/module-groups.md` 的 nop-stream 子模块清单与 reactor 不同步（analysis 已记录为 doc drift）。
- **设计文档面**：`ai-dev/design/nop-stream/` 含 15 份文档（`00-vision.md`、`01-architecture-baseline.md`、`cep-design.md`、`checkpoint-design.md`、`comparison.md`、`component-roadmap.md`、`connector-design.md`、`core-design.md`、`failover-design.md`、`graph-model-design.md`、`mailbox-design.md`、`state-management-design.md`、`stream-dsl-design.md`、`time-model-design.md`、`window-design.md`、`README.md`）。
- **XDSL 入口 schema**：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef`。StreamModel Java 类在 `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/model/`（`StreamDefinitionModel`、`StreamSinkModel`、`StreamEdgeModel`、`StreamAggregateModel`、`StreamWindowModel`、`CoderModel`、`CheckpointConfigModel` 等）。
- **IoC/beans 面**：`nop-stream/nop-stream-runtime/src/main/resources/_vfs/nop/stream/beans/` 仅有 `stream-control-rpc.beans.xml`、`stream-data-plane.beans.xml`（各声明 `ioc:default` bean：`streamMessageService`→`LocalMessageService`、`streamDataPlaneWireCodec`→`IdentityWireCodec`）。**无 `_module` 标记**（Stage 3 计划正在补齐）。
- **Connector 工厂/配置面**：`MessageSourceFunction`/`MessageSinkFunction`（connector）、`FileSplitEnumerator`/`FileSourceReader`（connector file）、`BatchLoaderSourceFunction`/`BatchConsumerSinkFunction`（connector-batch）、`JdbcTwoPhaseCommitSinkBuilder`/`JdbcTwoPhaseCommitSink`（connector-jdbc）、`DebeziumCdcSourceFunction`（connector-debezium）。
- **示例/测试 lane**：示例 `_vfs/nop/stream/demo/fraud-detection.stream.xml`（fraud-example 模块）。测试 fixture 散布于各模块 `src/test/`（含 multi-JVM fixture 如 `MiniStreamCluster`、recovery/E2E fixture）。
- **Finding corpus 来源**：`ai-dev/audits/nop-stream-production/` 含 4 份审计报告 — `2026-07-25-1948-multi-audit-*.md`、`2026-07-25-1948-open-audit-*.md`、`2026-08-02-2107-multi-audit-*.md`、`2026-08-02-2107-open-audit-*.md`。最新 multi-audit 记录 P0×2 / P1×13 / P2×23，含 `AR-` 编号项；finding ID 形如 `[P0]`/`[P1]`/`[P2]`/`AR-N`。
- **现有工具**：`ai-dev/tools/` 含 `scan-hollow-implementations.mjs`、`check-plan-checklist.mjs`、`check-vfs-violations.mjs`、`code-stats.mjs` 等，但**无** nop-stream 专用的 manifest/corpus/evidence-row 校验器。
- **真实 gap**：(1) 没有冻结的、带可复现选择命令的有界 source manifest；(2) 没有 finding corpus 的冻结分片与 ID 登记；(3) 没有证据行 schema 与分类词汇表；(4) 没有拒绝缺字段/未知字段/分母不匹配的校验器；(5) 各历史审计与历史 plan 的 deferred/follow-up P2 项散落，未归并到统一 corpus。

## Goals

- 产出一份**有界的 source manifest**，对 Java 公共类型/方法、XDSL 节点、Delta 面、beans/SPI、connector 工厂/配置、示例、测试 lane 各给出：精确路径范围、可复现的选择命令（`rg`/`find`）、期望分母（denominator）、显式 include/exclude 规则（覆盖 generated `_`-前缀源、`@Internal` SPI、example 模块）。
- 产出一份**冻结的 finding corpus**，登记 4 份审计报告的每个 finding（ID、来源报告路径、anchor `file:line`、severity、所属技术域），并按 Stages 18-22 切分为 5 个有界分片，每个分片有合计与 ID 清单。
- 产出一套**证据行 schema**，字段至少含：inventory ID、source/anchor、declared guarantee、implementation anchor、runtime wiring、positive proof、rejection proof、environment class、required-lane flag、finding ID、disposition；并冻结分类词汇表 `e2e-proved | component-only | unverified | fail-fast | non-goal | residual-risk | blocked`。
- 产出一个**校验器** `ai-dev/tools/check-nop-stream-audit-manifest.mjs`：拒绝缺字段/未知字段/分类词表外值；对 manifest 每条选择命令实际执行并比对期望分母；对 corpus 校验 ID 唯一、分片合计与登记一致；对 evidence-row 文件校验字段完整与分类合法。
- 校验器带**阳性对照**（喂入一个已知坏行，确认被拒绝），满足 roadmap "Tool validity: 静态扫描器在零结果被采信前需有阳性对照"。

## Non-Goals

- **枚举所有 capability 行**（那是 Stages 6-16 各域审计的工作；本计划只提供度量衡与空网格）。
- 任何 nop-stream 产品代码变更（本计划产出为数据文件 + 校验器脚本，不改生产代码）。
- 生产就绪（readiness）结论（Stage 23）。
- 对历史 P2 finding 逐条做 live 复验与 disposition（Stages 21-22）；本计划只冻结其 ID/anchor 并归入分片。
- 修正 `docs-for-ai/01-repo-map/module-groups.md` 的子模块清单 drift（该 drift 由相关 owner doc plan 收敛；本计划只在 manifest 中记录 live 10-模块布局作为事实基准）。

## Scope

### In Scope

- `ai-dev/audits/nop-stream-independent-audit/source-manifest.md`（或等价机器可读格式）。
- `ai-dev/audits/nop-stream-independent-audit/finding-corpus.md`（含 18-22 五个分片）。
- `ai-dev/audits/nop-stream-independent-audit/evidence-schema.md`（字段定义 + 分类词汇表 + include/exclude 规则）。
- `ai-dev/tools/check-nop-stream-audit-manifest.mjs`（校验器 + 阳性对照测试输入）。

### Out Of Scope

- 任何单条 finding 的 live 复验或 disposition 判定（Stages 18-22）。
- 任何域审计的 capability 行枚举（Stages 6-16）。
- 环境资格认定（Stage 5）——但本计划的 evidence-row schema 预留 `environment class` 与 `required-lane flag` 字段供其消费。
- 生成代码（`_gen/`、`_*.java`、`_*.xml`）的内容审计——manifest 显式 exclude 这些路径。

## Execution Plan

### Phase 1 - Source Manifest 冻结

Status: planned
Targets: `ai-dev/audits/nop-stream-independent-audit/source-manifest.md`

- Item Types: `Proof`

- [ ] 为每个 manifest 域登记：精确路径范围 + 可复现选择命令 + 期望分母。覆盖域：(a) Java 公共类型/方法（`public` 类型，排除 `_`-前缀 generated 与 `@Internal` SPI，明确 example 模块处理）；(b) XDSL 节点（来自 `stream.xdef` 的节点枚举）；(c) Delta 面（`stream.xml` overlay 表面）；(d) beans/SPI（`_vfs/nop/stream/beans/*.beans.xml` + `ioc:default` bean 清单）；(e) connector 工厂/配置（5 个 connector 模块的工厂类与配置）；(f) 示例（`fraud-detection.stream.xml`）；(g) 测试 lane（按 unit / in-process integration / multi-JVM 分类）。
- [ ] 每条选择命令必须能在当前 HEAD 上独立复现，且登记的期望分母与命令执行结果一致（manifest 写入前已实际跑过）。
- [ ] 显式记录 include/exclude 规则文本：generated `_`-前缀源如何识别与排除、`@Internal` SPI 是否计入公共面、example 模块是否计入 capability 面（不计入生产 capability，但计入 fail-fast/语义面）。

Exit Criteria:

- [ ] manifest 文件存在于仓库，含全部 7 个域，每域有 ≥1 条带选择命令 + 期望分母的登记
- [ ] manifest 中每条选择命令的"期望分母"与在当前 HEAD 实际执行结果一致（校验器 Phase 3 会自动复核；Phase 1 至少手工跑通并记录分母）
- [ ] include/exclude 规则在 `evidence-schema.md` 中有显式文本（不依赖口头约定）
- [ ] **接线验证**：manifest 命令是"活"的——校验器（Phase 3）能直接运行这些命令；不允许只写路径列表而无选择命令
- [ ] `No owner-doc update required`（manifest 是审计基础设施，不改 `docs-for-ai/`；子模块清单 drift 由独立 owner doc 收敛，本计划只以 manifest 记录 live 事实）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Finding Corpus 冻结与分片

Status: planned
Targets: `ai-dev/audits/nop-stream-independent-audit/finding-corpus.md`

- Item Types: `Proof`

- [ ] 登记 4 份审计报告（`2026-07-25-1948-multi/open`、`2026-08-02-2107-multi/open`）的每个 finding：稳定 ID（报告前缀 + 序号，如 `08-02-multi-P0-1`）、来源报告路径、anchor `file:line`、severity（P0/P1/P2/AR）、所属技术域（coordinator/runtime、checkpoint/state、CEP、connector、contract/test）。
- [ ] 将全部 finding 按 Stages 18-22 切为 5 个有界分片：分片 18（current production，取最新两份 08-02 报告）、分片 19（hist P0/P1 checkpoint/state/window）、分片 20（hist P0/P1 CEP/connector/runtime）、分片 21（hist P2 core/state/window）、分片 22（hist P2 CEP/connector/runtime）。同一 finding 不得跨分片重复；跨分片的归属规则在文件顶部显式声明。
- [ ] 归并 3 份 active production remediation plan 的 deferred/follow-up P2 项（Plan 1: `setTasksToAcknowledge`/`assignTasks` mid-iteration/`failJob`；Plan 2: native handle leak/增量 native 目录/段 hash/JDBC retained-manifest/CEP Serializable；Plan 3: javadoc rot/二层错误处理/低价值测试尾部）到对应 P2 分片，标注其 deferred 来源与 owner plan 路径。
- [ ] 每个分片给出 finding 合计与 ID 清单；文件顶部给出全 corpus 合计。

Exit Criteria:

- [ ] corpus 文件存在于仓库，含 5 个分片，每分片有合计 + ID 清单，顶部有全 corpus 合计
- [ ] 每个 finding 有稳定 ID 且 corpus 内 ID 唯一
- [ ] finding 的 severity/域与来源审计报告一致（抽样核对：最新 multi-audit 的 P0×2/P1×13/P2×23 在分片 18/19/20/21/22 中的分布可追溯）
- [ ] 3 份 active plan 的 deferred P2 项已归并并标注来源
- [ ] 跨分片归属规则在文件顶部显式声明（同一 finding 不重复登记）
- [ ] **无静默跳过**：任何无法归类到 18-22 的 finding 显式标注"待裁决"而非丢弃（Rule #24）
- [ ] `No owner-doc update required`（corpus 是审计基础设施）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Evidence Schema 与校验器

Status: planned
Targets: `ai-dev/audits/nop-stream-independent-audit/evidence-schema.md`, `ai-dev/tools/check-nop-stream-audit-manifest.mjs`

- Item Types: `Decision | Proof`

- [ ] 在 `evidence-schema.md` 冻结证据行字段（inventory ID、source/anchor、declared guarantee、implementation anchor、runtime wiring、positive proof、rejection proof、environment class、required-lane flag、finding ID、disposition）与分类词汇表（`e2e-proved | component-only | unverified | fail-fast | non-goal | residual-risk | blocked`），并声明各字段的允许值与必填性。
- [ ] 实现校验器 `ai-dev/tools/check-nop-stream-audit-manifest.mjs`，至少支持 3 个子命令：(a) `manifest`——逐条执行 manifest 选择命令并比对期望分母，分母不匹配则失败；(b) `corpus`——校验 finding ID 唯一、各分片合计与登记一致、severity/域词表合法；(c) `evidence`——校验 evidence-row 文件字段完整、分类值在词表内、未知/缺字段被拒绝。
- [ ] 校验器**阳性对照**：提供一份已知坏输入（缺字段行、词表外分类值、分母故意写错的 manifest 条目），确认校验器对其报错并以非零退出码失败。

Exit Criteria:

- [ ] `evidence-schema.md` 存在且字段/词表完整，必填性与允许值有显式声明
- [ ] 校验器脚本存在于 `ai-dev/tools/`，3 个子命令均可执行
- [ ] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs manifest` 对 Phase 1 的 manifest 退出码为 0（分母全部匹配）
- [ ] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs corpus` 对 Phase 2 的 corpus 退出码为 0（ID 唯一、分片合计一致）
- [ ] **阳性对照**：对已知坏输入，3 个子命令均以非零退出码失败并打印具体错误（证明校验器非空壳、非静默放行）
- [ ] **无静默跳过**：校验器遇到缺字段/未知字段/词表外值时显式报错退出，不静默修复或忽略（Rule #24）
- [ ] **接线验证**：`check-plan-checklist.mjs` 风格——校验器可被后续 plan 的 Closure Gates 直接调用（有 `--strict` 等价行为）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **纯数据/工具计划**：本计划不改 nop-stream 生产代码（仅新增审计数据文件 + 一个 `ai-dev/tools/` 校验器脚本）。`./mvnw test`/`./mvnw compile` 不强制；改为以校验器自身退出码为 closure 证据。校验器脚本须通过 `node --check` 语法校验。

- [ ] source manifest（7 域、带选择命令、期望分母匹配）已冻结
- [ ] finding corpus（5 分片、ID 唯一、合计一致、deferred P2 已归并）已冻结
- [ ] evidence schema（字段 + 分类词表）已冻结
- [ ] 校验器存在，3 子命令对正式输入退出码为 0、对阳性对照坏输入退出码非 0
- [ ] 不存在被静默降级到 deferred 的 in-scope 度量衡项
- [ ] `No owner-doc update required`（manifest 只以 live 事实记录 10-模块布局，不修 `docs-for-ai/` drift）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 验证校验器非空壳（阳性对照确有拒绝行为）、manifest 命令确为"活"选择命令（非纯路径列表）、corpus 分片无静默丢弃
- [ ] `node --check ai-dev/tools/check-nop-stream-audit-manifest.mjs` 通过

## Deferred But Adjudicated

（执行中如出现延期项，须写明 Classification / Why Not Blocking Closure / Successor Required。本计划预期无延期——度量衡必须完整冻结才能解锁 Stages 5-23。）

## Non-Blocking Follow-ups

- `docs-for-ai/01-repo-map/module-groups.md` 的 nop-stream 子模块清单 drift 收敛由独立 owner-doc plan 负责；本计划只在 manifest 中以 live 10-模块布局作为事实基准，不阻塞 closure。
- 若 manifest 域在后续域审计中发现遗漏面（如某 SPI 未登记），由对应域审计 plan 触发 manifest 增补（successor），不阻塞本计划 closure——前提是 Phase 1 已覆盖 7 个声明域。

## Closure

Status Note: （关闭时填写）
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: （关闭时填写）
- Evidence: （关闭时填写，含每条 Exit Criterion / Closure Gate 验证结果、校验器 3 子命令退出码、阳性对照结果、check-plan-checklist 退出码）

Follow-up:

- （关闭时填写；confirmed live defect 不得出现在这里）
