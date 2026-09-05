# 1 复合场景设计文档（roadmap item 12）

> Plan Status: completed
> Last Reviewed: 2026-09-01
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` item 12（Phase S 第一项，critical path，deps: M2 done）；stage details「复合场景设计文档」
> Related: `ai-dev/analysis/2026-09/2026-09-01-nop-stream-design-productization-gap-analysis.md`（item 6 的 D-GAP 报告，其 §3.2 Phase S 场景约束 6 条 + S3 不派生裁定是本 plan 的硬边界）；`ai-dev/analysis/2026-09/2026-09-01-nop-stream-rocksdb-flow-fraud-example-audit.md`（item 11 报告，其 §2.4 fraud-example 完整度评估 + Gap A/B/C 缺口清单是本 plan 的直接输入，其 §3.2 显式路由「fraud 三缺口 + demo 重写 → items 12/13」）；`ai-dev/analysis/2026-09/2026-09-01-nop-stream-cep-module-audit.md` / `ai-dev/analysis/2026-09/2026-09-01-nop-stream-connectors-module-audit.md`（items 9/10 报告：CEP 声明式接线现状、连接器能力矩阵事实）
> Mission: nop-stream-productization
> Work Item: roadmap item 12

## Purpose

设计 2 个复杂复合使用场景（S1: CDC source → CEP → 窗口聚合 → 2PC JDBC sink；S2: 文件 source → keyBy 聚合 + Delta 定制拓扑 → exactly-once 文件 sink + rescale）并定义**可运行**的验收标准与分布式验证矩阵，落库为 `ai-dev/design/nop-stream/composite-scenario-design.md`；同时对 item 11 路由过来的 fraud-example Gap A/B/C 缺口做出映射裁定（并入 S1/S2 或独立交付面）。本 plan 是纯设计文档交付（无产品代码变更），为 items 13（单进程落地）/14（分布式落地）提供自包含输入。

## Current Baseline

（2026-09-01 live 核对）

- **场景约束已由 D-GAP §3.2 硬性给定**（`ai-dev/analysis/2026-09/2026-09-01-nop-stream-design-productization-gap-analysis.md` §3.2）：① 不得依赖声明式异常策略（P-REQ-17 defer；poison-record 类需求只记录为 revisit 证据）；② rescale 只能走 restore 时 parallelism rescale 或显式离线 reshard 工具（`MaxParallelismReshardMigration`，vision non-goal「在线/自动 reshard」）；③ 不得含 standby 热备/状态查询接口（P-REQ-18/19 exclude）；④ checkpoint 断言以「最新 durable epoch manifest 恢复 + exactly-once 结果」为准（manifest 级 checksum 字段落地前不得断言 manifest 完整性校验行为——item 25 未做）；⑤ 分布式验证矩阵以 `MiniStreamCluster` 真实多 JVM 为基线，不引入 K8s；⑥ **语义不降级**（S1 声明 STRICT_EXACTLY_ONCE 的前提 = CDC source 可重放（offset checkpoint）+ 2PC JDBC sink 严格提交；S2 文件 sink 走 `FileTwoPhaseCommitSink`）。另有一条**独立裁定**：S3 不派生，维持 S1/S2（已落定，本 plan 不重开）
- **fraud-example 现状**（item 11 报告 §2.4 盘点表；live 复核 2026-09-01：10 main / 6 test 类 29 @Test）：`FraudDetectionDemo` 直连 CEP 引擎内部 API（NFA/NFACompiler/SharedBuffer），未展示 DataStream API / XDSL 两扇正门；`fraud-detection.stream.xml` 为死文件（零引用，模块 pom 无 flow 依赖）；`UserTransactionHistory` 死代码；`DemoKeyedStateStore` demo 专用；pom 仅依赖 `nop-stream-cep`
- **Gap A/B/C 缺口清单**（item 11 报告 §2.4，自包含供本 plan 消费）：Gap A 可运行 XDSL 声明式欺诈管线（工作量 M）；Gap B keyBy + 窗口聚合 + keyed state 示例（DataStream API，素材 `UserTransactionHistory` + UnusualAmountPattern 去 stub，工作量 M）；Gap C checkpoint/恢复 + 状态后端切换 demo（kill-recover，工作量 L）
- **S1 组件 live 事实**：CDC source `DebeziumCdcSourceFunction`（`nop-stream-connector-debezium`，main 1 文件 + 4 test）；其测试以手工 `ChangeEvent` 经 `SourceFunction.SourceContext` 驱动，**无真实 Debezium engine/源库**——S1 的可运行形态（replayable 事件源 vs embedded engine + 真实库）是本 plan 必须裁定的决策点。2PC JDBC sink `JdbcTwoPhaseCommitSink`/`JdbcTwoPhaseCommitSinkBuilder`（`nop-stream-connector-jdbc`）
- **S2 组件 live 事实**：file source/sink 在 `nop-stream-connector`（含 `FileTwoPhaseCommitSink`）；flow XDSL E2E 模式已存在（`StreamModelDslBuilder` + testing helpers `CollectingSinkFunction`/`IntegerSourceFunction` 等 + fixtures `_vfs/nop/stream/test/test-*.stream.xml`）；Delta 定制三形态测试钉定（`test-delta-{base,extends,layered,config-*,failfast-*}.stream.xml`）；rescale 路径 = restore 时 parallelism rescale + 离线 `MaxParallelismReshardMigration`（runtime main）+ `TestMaxParallelismReshardMigrationE2E`
- **flow 声明面约束**（item 11 FL-1/FL-2 修复后 live）：source/sink 的 `params`/`outputType`/`inputType`/`maxParallelism`/非默认 `consistencyCapability` 与 per-transform `parallelism` 不匹配生效值时均 **build 期 fail-fast**——场景 XDSL 设计不得使用这些声明（实际消费属 Follow-up item 29）；W-F5：`AdvancedTransforms` 内置窗口 assigner 魔法目录（标注 test convenience）的「正式目录化」裁定路由到本 plan/plan 2
- **分布式基建 live 事实**：`MiniStreamCluster`（runtime test multijvm，572 行）+ `JobCoordinatorMain`/`TaskManagerMain` 独立进程入口 + gated 用例 `TestMultiJvmExactlyOnceRecovery`/`TestMultiJvmCoordinatorFailover`（`@EnabledIfSystemProperty(named = "nop.stream.test.multi-jvm.enabled", matches = "true")`）；item 8 R-14 先例：真实多 JVM（3 JVM：JC + TM 独立 spawn 进程 + 测试 JVM 扮 zombie）gated fencing 测试 7/7 绿
- **设计文档体系**：`ai-dev/design/nop-stream/` 现有 16 份（`00-vision.md` … `window-design.md`）；编写规范 `ai-dev/design/00-design-writing-guide.md`（决策+理由+拒绝替代方案；不写代码签名/实现叙事）；本 plan 新增文档命名定为 `composite-scenario-design.md`（与既有 `*-design.md` 命名一致，无冲突）
- **backpressure 演练归属**：roadmap stage details item 12 要求分布式验证矩阵含 kill/rescale/backpressure 组合——backpressure **行为**的稳定性演练属 item 15；本 plan 矩阵只定义场景验收所需的 backpressure 触发组合（如 bounded sink 限速），不定义长时 soak/chaos 矩阵

## Goals

- 场景设计文档 `ai-dev/design/nop-stream/composite-scenario-design.md` 落库，每个场景（S1/S2）自包含：拓扑与数据流、XDSL 声明式定义形态（bean/xpl 双函数形态取舍）、输入输出契约、**可运行的验收断言**（正确性 + exactly-once + 恢复语义，逐条 repo-observable）、分布式验证矩阵（kill/rescale/backpressure 组合 × 场景）
- S1 关键决策裁定并记录拒绝的替代方案：CDC source 的可运行驱动形态（replayable 事件源 vs embedded Debezium + 真实源库）；JDBC 2PC sink 的可运行目标（H2 等无外部依赖方案 vs 外部库）
- S2 关键决策裁定：Delta 定制拓扑的演示形态（在 base 场景上叠加什么 delta）；rescale 验证走 restore-time parallelism rescale 还是离线 reshard 工具（或两者）；exactly-once 文件 sink 断言方式
- Gap A/B/C 映射裁定：逐条裁定并入 S1/S2（作为场景的组成部分落地于 plan 2）还是独立交付面，路由结论显式记录（item 11 §3.2 路由的正式承接）
- 模块放置决策：场景落地模块（扩展 `nop-stream-fraud-example` vs 新建 demo/scenario 模块），依据依赖面（Gap A 需 flow、Gap C 需 runtime+rocksdb）与 roadmap「fraud-example 扩展」表述裁定，供 plan 2 直接消费
- W-F5 裁定：场景窗口定义是否目录化正式 builtin（还是经 bean 引用），一次裁定
- roadmap item 12 写回

## Non-Goals

- 场景实现与测试编写（items 13/14；本 plan 只交付设计）
- 多 JVM / DISTRIBUTED 模式验证执行（item 14）
- 长时 soak / chaos / backpressure 稳定性演练矩阵（item 15；本 plan 矩阵仅覆盖场景验收所需组合）
- flow 声明面的实际消费实现：per-transform parallelism 消费属 Follow-up item 29；`params`/`outputType`/`inputType`/`maxParallelism`/`consistencyCapability` 的消费实现按 item 11 §3.2 路由归 items 12/13（场景落地时按需）与 item 20 邻域（conf-validate 数据面）——本 plan 只在其 Non-Goals 边界内**裁定场景的表达形态**，不实施消费
- 新连接器开发 / OLAP 连接器集裁定（Follow-up item 19）
- 作业提交前校验（dry-run/conf-validate，Follow-up item 20；D-GAP 已裁定其为 S1 的可选提交前步骤，非场景组成部分）
- 00-vision/01-architecture 等 16 份既有设计文档的回写（D-DRIFT-1 修正属 item 17）
- P-REQ-25 快速起步脚手架的完整文档化（item 17；本 plan 只裁定拓扑缺口的技术形态）

## Scope

### In Scope

- 新增：`ai-dev/design/nop-stream/composite-scenario-design.md`（遵循 `ai-dev/design/00-design-writing-guide.md`）
- 修改：`ai-dev/design/nop-stream/README.md`（子系统结构索引追加新文档条目）
- 决策记录：S1/S2 全部关键设计决策 + Gap A/B/C 映射 + 模块放置 + W-F5 目录化（每条含拒绝替代方案及理由）
- 只读核对：live 组件契约锚点（connector-debezium/jdbc/file、flow fixtures、MiniStreamCluster、reshard 工具）——设计断言与 live 能力一致性
- 修改：`ai-dev/backlog/nop-stream-productization-roadmap.md`（item 12 closure 写回 + Last updated）
- 修改（条件性）：若设计过程发现 D-GAP §3.2 约束需要修正（如新证据表明某约束阻碍场景成立），按 roadmap Rules 以 Follow-up 追加或 stop-edit-restart 提请，**不在本 plan 内私改约束**

### Out Of Scope

- 任何 `nop-stream/` 产品代码/测试代码变更（纯文档 plan）
- `ai-dev/analysis/` 新报告（设计文档即交付物；可行性核对结论随 Phase 2 落库为设计文档附录、过程记录入 `ai-dev/logs/`，不另立 analysis 报告）
- 16 份既有设计文档的结构性重写

## Execution Plan

### Phase 1 - 可运行形态可行性核对

Status: completed
Targets: `nop-stream/nop-stream-connector-debezium|jdbc|connector/`、`nop-stream-flow`（testing helpers + fixtures）、`nop-stream-runtime`（multijvm + reshard）、设计文档素材

- Item Types: `Proof`

- [x] S1 组件契约核对：`DebeziumCdcSourceFunction` 的运行契约（DebeziumConfig 必填面、SourceContext 消费语义、offset checkpoint 路径）与现有测试驱动方式的差距；`JdbcTwoPhaseCommitSink` 对目标库的最小要求（DDL/事务语义，H2 可行性）；CEP 在 XDSL 中的声明式接线面（`stream.xdef` 的 cep patternRef → pattern 经何 registry/bean 解析——S1「XDSL 声明形态」决策的直接输入；该接线现状结论见 item 11 报告 §2.2 flow 消费端表）
- [x] S2 组件契约核对：file source 的可重放性（replay 语义与 offset/文件位点）、`FileTwoPhaseCommitSink` 的 exactly-once 语义与断言面；flow fixtures 中 bean/xpl 双函数形态与 Delta 三形态的可复用模式
- [x] rescale 路径核对：restore-time parallelism rescale 与 `MaxParallelismReshardMigration` 的输入要求（checkpoint 产物格式、key-group maxParallelism 约束），确定 S2 矩阵中 rescale 步骤的技术形态
- [x] backpressure 触发/观察代理核对：现有测试基建（item 16 metrics 未落地）下，矩阵 backpressure 格的可执行触发手段（如 bounded sink 限速）与可观察代理（如 checkpoint 推进 + 结果完整性）的 live 事实
- [x] 模块依赖面核对：Gap A/B/C 各自所需模块依赖（flow/runtime/rocksdb/connector-*）与 fraud-example 现依赖（仅 cep）的差距，为模块放置决策提供事实

Exit Criteria:

- [x] 每项核对**结论**已整理成文（含 live 类/fixture 名锚点，非过程记录），随 Phase 2 落库为设计文档附录「约束与可行性锚点」；核对过程记录写入 `ai-dev/logs/` 当日条目；无「未核对」项
- [x] S1 CDC 可运行形态的候选集 enumerated（至少：replayable ChangeEvent 事件源、embedded Debezium + 真实源库），每候选的依赖/确定性/与 offset checkpoint 契约的兼容性有事实记录
- [x] **纯文档阶段**：No product code change（若可行性核对发现 live defect，记录并按 roadmap Rules 路由，不顺手修）
- [x] No owner-doc update required: 纯核对阶段，文档裁定随 Phase 2 设计文档落库（含 `ai-dev/design/nop-stream/README.md` 索引更新）
- [x] No new test required: 纯核对/文档阶段
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 场景设计与裁定落库

Status: completed
Targets: `ai-dev/design/nop-stream/composite-scenario-design.md`

- Item Types: `Decision | Proof`

- [x] S1 设计：拓扑（CDC source → watermark/过滤 → CEP pattern → 窗口聚合 → 2PC JDBC sink）、XDSL 声明形态、数据流与输入输出契约、验收断言集（正确性：乱序/迟到处理结果；exactly-once：sink 端无重复无丢失；恢复：checkpoint 后中断重放的语义）
- [x] S2 设计：拓扑（file source → keyBy 聚合 + Delta 定制拓扑 → exactly-once 文件 sink + rescale）、Delta 演示形态、验收断言集（聚合正确性、Delta 生效证明、restore-rescale 前后结果一致性、exactly-once）
- [x] S1/S2 关键决策逐条裁定 + 拒绝替代方案记录（CDC 驱动形态 / JDBC sink 目标 / Delta 演示形态 / rescale 技术路径 / 模块放置 / W-F5 目录化 / 连接器参数表达形态（bean 引用 vs `params` 声明——后者 build 期 fail-fast 且消费未落地）/ 场景恢复与检查点测试的驱动形态（XDSL 入口直驱 checkpoint 执行 vs runtime harness 组合——现无 XDSL+checkpoint+恢复组合先例，plan 2 消费此裁定））
- [x] Gap A/B/C 映射裁定：逐条「并入 S1/S2（注明并入点）或独立交付面（注明 plan 2 交付序）」，与 item 11 §3.2 路由闭环
- [x] 分布式验证矩阵设计：场景 × 演练组合（kill TaskManager / kill 恢复 + fencing 断言 / restore-rescale / backpressure 触发）的矩阵表，每格定义可观察验收点；矩阵遵守 D-GAP §3.2 全部 6 条约束
- [x] 设计文档自包含性检查：plan 2/3（即 items 13/14）执行者无需回读 D-GAP/item 11 报告即可实施——所需约束与输入**内联**为设计文档独立章节/条目（design-writing-guide 禁止 design doc 引用 `ai-dev/analysis/` 报告；本项不含「精确引用」选项）
- [x] `ai-dev/design/nop-stream/README.md` 索引更新：新增文档入子系统结构索引（阅读路径/层级归属）
- [x] roadmap item 12 closure 写回（经 closure audit 后）

Exit Criteria:

- [x] `ai-dev/design/nop-stream/composite-scenario-design.md` 存在且含上述全部章节（S1/S2 设计 + 决策记录 + Gap A/B/C 映射 + 分布式验证矩阵 + 附录「约束与可行性锚点」）；每个验收断言均为 repo-observable 表述（可映射为 plan 2/3 的具体测试断言）
- [x] 全部关键决策有「选择 + 理由 + 拒绝的替代方案及原因」三要素（design-writing-guide 硬要求）
- [x] Gap A/B/C 三条映射裁定显式落库，与 item 11 §3.2 路由表逐条对应
- [x] 分布式验证矩阵无违反 D-GAP §3.2 六条约束的格子（逐条对照记录；S3 不派生为独立裁定行，不在六条之内）
- [x] 设计文档不包含实现代码/类签名展开/演进叙事（design-writing-guide 合规自查）
- [x] `ai-dev/design/nop-stream/README.md` 索引已含新文档条目
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（新文档 + README 索引 + roadmap 修改后）
- [x] 纯文档 plan：`./mvnw test` 等构建验证条目按 guide 模板「纯文档计划」规则豁免（显式声明，非静默跳过）
- [x] No new test required: 纯文档 phase
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 纯文档计划：按 guide 模板规则，`./mvnw compile`/`./mvnw test`/checkstyle 条目豁免（本 plan 无产品代码变更）。

- [x] 设计文档落库且章节齐备（S1/S2 设计 + 决策记录 + Gap A/B/C 映射 + 分布式验证矩阵 + 附录「约束与可行性锚点」）+ `ai-dev/design/nop-stream/README.md` 索引更新
- [x] S3 未派生（维持 D-GAP 独立裁定，设计文档显式记载（内联）不重开）
- [x] 全部决策含拒绝替代方案；无可「自行猜测」的留白（plan 2/3 执行者视角自查）
- [x] 不存在被静默降级的路由项（Gap A/B/C 逐条有归属；item 11 §3.2 路由闭环）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] 独立子 agent closure-audit 已完成并记录证据（重点：设计断言与 live 组件契约一致性抽查、约束合规、文档指南合规）
- [x] roadmap item 12 状态写回（closure audit 通过后）

## Deferred But Adjudicated

（执行时按需填写；预期类型：`out-of-scope improvement`——如设计过程中识别的示例增强想法，逐条附 Why Not Blocking Closure）

## Non-Blocking Follow-ups

- D-GAP §2.4 F-2（item 16 语义追加告警渠道闭环）维持 stop-edit-restart 建议，与本 plan 无关，仅备忘不重开。
- 设计过程中若出现 poison-record 类声明式异常策略需求（P-REQ-17 revisit 触发材料），记录为 revisit 证据，不在本 plan 实施。

## Closure

Status Note: 纯文档 plan 全部交付物落地：设计文档（S1/S2 设计 + 决策 D1—D9 + Gap A/B/C 映射 + 分布式验证矩阵 + 附录 live 锚点）+ README 索引 + roadmap item 12 写回。Phase 1 可行性核对与 Phase 2 设计裁定均经独立 closure audit 复核（设计断言与 live 组件契约 8 项抽查全 PASS）。场景实现归 items 13/14（设计文档 §3.6 自包含消费契约）。
Completed: 2026-09-01

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent（fresh session，closure audit 专用，非执行 session）
- Audit Session: `ses_fa286a702ffeBqX2daZlW0bdF8`
- Evidence:
  - **A.1 Phase 1 Exit Criteria — PASS**：附录 A 六节（A.1—A.6）live 锚点齐备（design doc §附录 A）；Phase 1 log 条目存在（`ai-dev/logs/2026/09-01.md`）；`git status --porcelain` 确认零产品代码变更（改动仅 ai-dev/ 文档：plan/design doc/README/log/roadmap）。
  - **A.2 Phase 2 Exit Criteria — PASS**：S1 §3.1.1—3.1.4 / S2 §3.2 + D4 / 决策 D1—D9（§3.4.1—3.4.9 各含选择+理由+拒绝替代）/ Gap A/B/C 表（§3.5）/ 矩阵 C0—C3（§3.3，六条约束逐条对照 + S3 独立裁定行 §3.0）/ 附录 A 全部在位；14 条验收断言（A1-1..A1-7、A2-1..A2-7）均为 repo-observable 表述。
  - **A.3 design-writing-guide 合规 — PASS**：`rg "analysis|discussions"` 零命中（无过程文档引用）；无实现代码块/类签名展开（仅 Mermaid 拓扑 + 接口名/路径锚点，属契约锚点）；无 Proposed-vs-Current/演进叙事。
  - **A.4 设计断言与 live 组件契约一致性抽查 — PASS（8/8）**：①`DebeziumCdcSourceFunction`（implements CheckpointedSourceFunction / `cdc-offsets` 键 :62 / protected `createMessageSource` :125 / name 必填 fail-fast :299）②`JdbcTwoPhaseCommitSink`（`stream_epoch_ledger` :74 / PK(epoch_id, subtask_id) :358 / `initializeLedgerTable()` :368 / H2 MODE=MySQL 测试先例）③`AdvancedTransforms`（buildCep KeyedStream 门禁 + patternRef + PatternProcessFunction bean :380-:405 / bean-first + 4 魔法 id :218-:237 / triggerId/allowedLateness/非 DISCARDING fail-fast :177-:191）④`StreamModelDslBuilder`（source 仅 SourceFunction :482-:486 / sink SinkFunction :585-:589 / `<keyBy keyExpr>`）⑤`FileTwoPhaseCommitSink`（temp+ATOMIC_MOVE+manifest :47-:76 / `N[.sK]`/`epoch-N[.sK].txt` subtask>0 后缀 :60-:64 / FileSource BOUNDED + per-split cursor）⑥`MiniStreamCluster`（AUTO_SERVER=TRUE :134 / kill/restart/spawn :186-:194 / gated 用例 `TestMultiJvmExactlyOnceRecovery` :67 / `MaxParallelismReshardMigration.migrate(String,int,int,String)` :79-:82）⑦`stream.xdef`（:167 cep patternRef / :223 pattern xdef:ref / :147 keyBy / :30-:36 checkpoint 13 attr 默认 STRICT_EXACTLY_ONCE / :124 watermarkStrategyBean）⑧fraud-example pom 唯一框架依赖 = nop-stream-cep。
  - **A.5 自包含性 — PASS**：六条约束 + S3 裁定 + Gap A/B/C 定义 + fail-fast 声明面全部内联；§3.6 列明 plan 2/3 消费契约（无需回读过程文档）。
  - **A.6 诚实性 — PASS**：唯一未勾选项为「roadmap 写回（经 closure audit 后）」（本 plan 显式排序，audit 后已勾选完成）；Deferred 区零追加；Non-Blocking Follow-ups 无 live defect。
  - **A.7 工具门禁 — PASS**：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（audit 时 30908 refs / 0 errors；roadmap 写回后复跑仍 0）。
  - **Anti-Hollow（纯文档计划口径）**：文档内容与 live repo 代码一致性抽查通过（A.4 8/8）；无空章节/占位符。
  - **Minor 处置**：audit 发现 2 处名字漂移（`JdbcFactory.newJdbcTemplateForDataSource` → `newJdbcTemplateFor(DataSource)`；`MaxParallelityReshardMigration` → `MaxParallelismReshardMigration`）已在设计文档/日志/plan 内就地修正（audit 后、本 evidence 写入前）。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（Closure Evidence 写入后终验，见下）。
  - Deferred 项分类检查：`Deferred But Adjudicated` 为空（无延期项）；Non-Blocking Follow-ups 两项均为备忘级（F-2 stop-edit-restart 建议、poison-record revisit 证据记录），无 in-scope live defect 被降级。

Follow-up:

- no remaining plan-owned work（场景实现归 items 13/14，消费契约见设计文档 §3.6；W-F5 已由 D7 关闭裁定；fraud-example 旧 demo main 去留与死文件删除由 item 13 按「与 S1/S2 叙事冲突时让位」原则处置）
