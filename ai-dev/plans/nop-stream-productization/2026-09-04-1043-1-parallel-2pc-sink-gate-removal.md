# 并行 2PC sink 收口（CONN-01 successor：门禁解除 + 端到端证明）

> Plan Status: completed
> Mission: nop-stream-productization
> Work Item: roadmap item 35（并行 2PC sink / CONN-01 successor）
> Last Reviewed: 2026-09-04
> Source: plans `2026-09-01-2217-2`/`2026-09-01-2217-3` Deferred 区 CONN-01 显式路由（Successor Required: yes）+ `ai-dev/design/nop-stream/checkpoint-design.md` §6.4.1 后继能力记录 + `ai-dev/design/nop-stream/composite-scenario-design.md` A.0-5/C2 形态路由
> Related: `2026-09-04-0233-1-flow-dsl-compiler-productization.md`（item 29，per-transform parallelism 消费——本 plan 的声明面前置，已完成）
> Review: 两轮独立对抗性审查（fresh sessions `ses_f95b0b5c7ffeOUqVrtmvVw7tFn` / `ses_f95a1faf0ffeM3e05o8L4ezH6l`）——首轮 2 Major（F1 D1 证据基础、F2 Phase 2 验证范围）+ 4 Minor 全修复；复审 F1—F6 全 PASS、无 Blocker/Major 遗留、判定可 active，2 项非阻塞建议（N1 D1 检测点覆盖非 keyed、N2 多 JVM sink 族覆盖矩阵）已采纳入文。共识达成。

## Purpose

把 2PC sink 的 exactly-once 公开契约从「parallelism=1（已证明）或 fail-fast」收口为「parallelism=N 已证明（LOCAL + 真实多 JVM DISTRIBUTED）」：解除规划期并行度门禁、翻转连接器能力声明、完成被复合场景 C2 显式路由的 keyed-P>1+2PC 形态端到端证明，并把 §6.4.1 的后继能力记录改写为已落地设计。这是 roadmap 全部 34 项 done 后唯一再触发able 的 `Successor Required: yes` deferred 项（其声明面前置 item 29 已于 2026-09-04 完成）。

## Current Baseline

（以下均为 live repo 已核实事实，anchor 格式 `文件:行号`，核对日期 2026-09-04）

**已落地（commit `2de622fb6a` 起，无需重建）：**

- **per-subtask UDF 隔离（原 §6.4.1 B2 已解）**：`TwoPhaseCommitSinkFunction.copyForSubtask(int)` 存在且默认 fail-fast（subtask>0 抛 `UnsupportedOperationException`，No-Silent-No-Op，`TwoPhaseCommitSinkFunction.java:125-134`）；`StreamSinkOperator.copyForSubtask(int)` 对 2PC UDF 路由独立拷贝（`StreamSinkOperator.java:66-74`）；`OperatorChain.deepCopy(int)` 按索引分发（`OperatorChain.java:252`）。
- **执行模式接线（双模式均已通）**：LOCAL `SupervisionLoop.java:632`、DISTRIBUTED remote-deploy `RemoteGraphExecutionPlanBuilder.java:211`、单 JVM 图执行 `GraphExecutionPlan.java:379` 均以真实 taskIndex 调 `deepCopy(taskIndex)`。
- **JDBC 台账 composite 键（原 B3 的 DDL 侧已解）**：ledger DDL 主键 `(epoch_id, subtask_id)`（`JdbcTwoPhaseCommitSink.java:367-380`）；幂等提交守卫按 (epoch, subtask) 查询（`:409-417`）；拷贝构造器携带 subtaskIndex（`:123-162`）。
- **File sink per-subtask 输出隔离**：subtask>0 的 per-epoch 临时/终文件与 manifest 键带 `.sK` 后缀，subtask 0 保持 legacy 无后缀名（`FileTwoPhaseCommitSink.java:63-67, :169-171`）。
- **组件级测试已存在**：`TestJdbcTwoPhaseCommitSinkParallelIsolation`（拷贝独立性 + 台账键隔离）、`TestFileTwoPhaseCommitSink`（subtask 拷贝路由 + 输出隔离）、`TestOperatorSubtaskIsolation`（契约面）。
- **声明面前置已就绪（item 29，2026-09-04 done）**：`DataStream.setParallelism`/`sink(fn,parallelism)` + XDSL `<transform parallelism="N">` 全链消费（`stream-dsl-design.md` §5.1-5.3）；fraud-example 场景节流 sink（`ThrottledScenarioSinks`）已 override `copyForSubtask(int)`。

**仍然阻塞/未证明（本 plan 的真实 gap）：**

- **规划期门禁仍 active**：`StreamGraphGenerator.transformSink` 对任何 `TwoPhaseCommitSinkFunction` 有效并行度 >1 抛 `ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED`（`StreamGraphGenerator.java:320-328`；错误码定义 `NopStreamErrors.java:486`）——并行 2PC 端到端不可达。
- **能力声明面钉在门禁值**：两内建工厂声明 `ConnectorParallelism.PLANNING_GATE_PARALLELISM_1`（`JdbcTwoPhaseCommitSinkConnectorFactory.java:39`、`FileTwoPhaseCommitSinkConnectorFactory.java:35`）；枚举契约「iff 2PC 必须声明门禁值」（`ConnectorParallelism.java:13-16`）；`StreamConnectorCatalog` 一致性校验绑定该不变式（`StreamConnectorCatalog.java:210`）。
- **零端到端证明**：无任何测试让 2PC sink 以 P>1 走完 `env.execute()` → 输出；复合场景 C2 的 keyed-P>1+2PC 形态在 plans 2217-2/2217-3 中被显式路由到本 successor，从未执行；多 JVM 形态同。
- **rescale 语义未裁定**：2PC sink P>1 的跨并行度恢复（P1→Pn / Pn→Pm）无裁定。live 机制（审查核verified）：跨并行度重分布只发生在 keyed-rescale 分支（`buildRescaledTaskState`，`GraphModelCheckpointExecutor.java:1563`）——keyed state 按 KeyGroupRange 再路由，而 operator state（pendingCommits 经 `participant-pending-commits` 键，`StreamSinkOperator.java:174`）按 index 严格 1:1 取旧 subtask（`:1571-1580`），**不存在 k'≠k 的 pendingCommits 移交路径**。真实风险：(1) **scale-down 静默丢弃**——旧 subtask（index ≥ newP）的 operator state 不进任何新 subtask，若含 durable-未提交 pending commits 则静默丢数据（违反 `checkpoint-design.md` §6.4「durable but not committed 必须 re-commit」不变量，恰是门禁要防的方向）；(2) **非 keyed 顶点 scale-up**——stateLookup 按新 taskLocation 查不到 → generic `ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED`（响亮但无 parallelism-mismatch 语义）。既有裁定基线：`checkpoint-design.md:1130-1135` 已有 rescale 语义表（sink pending transaction 不允许跨 subtask 静默迁移；non-keyed operator state 必须声明 redistribution policy 否则拒绝）——本 plan D1 是其增量裁定，非从零裁定。`restoreOperatorsFromState`（`:1802`）的 mappings 参数仅承载算子内 index→state-key 映射，与跨 subtask 重分布无关。
- **台账遗留 schema 未裁定**：`CREATE TABLE IF NOT EXISTS` 对 `2de622fb6a` 之前创建的单列 ledger 是 no-op（新插入按复合列写会列不匹配——响亮失败而非静默，但无正式裁定）。
- **文档钉在 defer 态**：`checkpoint-design.md` §6.4.1（:788-798，B1/B2 叙述已滞后于 live code——sink 身份注入经 copyForSubtask(int) 已存在）、`comparison.md:404`（「仅 parallelism=1 已证明」）、`docs-for-ai/03-modules/nop-stream-connectors.md:87-94`（硬门禁节）+ `:81`（矩阵行「规划期门禁 P=1」）、`nop-stream-user-guide.md:112/:149`、`distributed-runbook.md:156`、`composite-scenario-design.md:319/:333` 路由注记、`TestS2RecoveryAndRescaleE2E.java:49` 等测试 javadoc。

## Goals

- 2PC sink（jdbc-2pc 与 file 两内建）在有效并行度 >1 时可通过规划并正确执行：每 subtask 独立批次、独立台账键/输出路径，exactly-once 在 P>1 成立。
- 该能力有端到端证据：LOCAL e2e（keyed-P>1 + 2PC sink，含 kill/recover）+ MiniStreamCluster 真实多 JVM gated（kill TM → 恢复 → fencing 严格递增 → exactly-once 结果集）。
- rescale 语义、台账遗留 schema、第三方子类面三个裁定落档（Decision 记录进 owner design doc）。
- 全部 defer 态文档/测试注记 supersession 为已落地设计。

## Non-Goals

- 不改两阶段提交协议本身（beginTransaction/invoke/preCommit/commit/rollback/abort 语义、恢复时 durable-未提交重提交语义零变更）。
- 不做台账在线迁移工具链/跨版本兼容（无已发布版本，与 P-REQ-21 defer 同口径；遗留 schema 只做裁定，不做升级工具）。
- 不为第三方/用户自定义 2PC sink 子类提供自动并行化（基类 fail-fast 默认保持为契约）。
- 不做 message 族/batch 族 sink 的任何变更（非 2PC，无门禁）。
- 不把既有 S1/S2 场景脚手架迁移到 P>1（既有 P=1 测试零回归；新增变体测试承载证明）。

## Scope

### In Scope

- `nop-stream-core`：`StreamGraphGenerator` 门禁解除；`ConnectorParallelism` 枚举语义与 `StreamConnectorCatalog` 一致性校验改写；`NopStreamErrors` 错误码处置（移除或按裁定保留）。
- `nop-stream-connector` / `nop-stream-connector-jdbc`：两工厂能力声明翻转；（若 Phase 1 裁定需要）遗留台账 schema typed 探测。
- `nop-stream-runtime`：（若 Phase 1 裁定为 fail-fast）跨并行度恢复的 typed 拒绝接线。
- `nop-stream-fraud-example`：keyed-P>1+2PC 形态场景变体测试（LOCAL e2e）。
- gated 多 JVM 测试（runtime test-jar 既有消费模式）。
- owner docs supersession：`checkpoint-design.md`、`comparison.md`、`connector-design.md`（如涉及）、`composite-scenario-design.md` 注记、`distributed-runbook.md`、`docs-for-ai/03-modules/nop-stream-connectors.md`、`nop-stream-user-guide.md`、`nop-stream-migration-guide.md`（台账 schema 说明）。

### Out Of Scope

- OLAP/新连接器（item 19 已裁定需求门控）。
- K8s/YARN/HPA、Web 控制台、声明式异常策略、跨版本升级测试（P-REQ defer 维持项）。
- CEP/windowing/keyed state 语义变更。

## Execution Plan

### Phase 1 - 语义裁定（Decision）

Status: completed
Targets: `ai-dev/design/nop-stream/checkpoint-design.md`（裁定记录）、`ai-dev/logs/`

- Item Types: `Decision`

- [x] **D1 跨并行度恢复语义**：以 `checkpoint-design.md:1130-1135` 既有 rescale 语义表为基线，基于 Current Baseline 所列 live 机制（operator state 按 index 1:1、无 k'≠k 移交；scale-down 静默丢弃 durable pending commits；非 keyed scale-up generic 失败）裁定：(a) 2PC sink 顶点并行度与快照不一致时 typed fail-fast 拒绝（检测点在 oldParallelism≠newParallelism 判定处，**覆盖 keyed 与非 keyed 两种顶点形态**——live 的 `rescale` 布尔是 keyed-only 条件，窄读会漏非 keyed 路径；错误码+参数，消除 scale-down 静默丢弃与 scale-up generic 失败两个缺口），或 (b) 身份保持重分布（改 `buildRescaledTaskState` 的 operator-state 重分布使 pending 值按原始 subtask 身份重提交——须与 :1130「non-keyed operator state 必须声明 redistribution policy」既有裁定对齐，工作量大）。默认预期 (a)（与既有裁定一致且关闭两个真实缺口），除非证据支持 (b)。裁定 + 依据 + 拒绝面落 `checkpoint-design.md`（对 :1130-1135 表做增量而非重写）
- [x] **D2 台账遗留 schema 裁定**：对 `2de622fb6a` 前创建的单列 ledger 表（无已发布版本，仅存量测试/演练库）裁定处置：(a) typed 探测 + 明确错误指引重建，或 (b) 文档化「重建台账表」操作口径不加代码探测。裁定落 `checkpoint-design.md` + `nop-stream-migration-guide.md`
- [x] **D3 第三方 2PC 子类面裁定**：门禁解除后，未 override `copyForSubtask(int)` 的用户子类在 P>1 下的行为契约 = 运行时 fail-fast（基类默认已抛）vs 规划期 opt-in 声明。裁定落 `checkpoint-design.md` §6.4.1 改写稿
- [x] 三项裁定记录于 daily log（含证据引用）

Exit Criteria:

- [x] `checkpoint-design.md` 含 D1/D2/D3 三裁定记录（各含裁定值、live 证据依据、拒绝的替代方案及原因）
- [x] D1 若裁定为 (a)：新增 typed 拒绝的错误码语义已在设计文档描述（实现归 Phase 2）
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] No owner-doc update required 不适用（本 Phase 交付物即 owner doc）

### Phase 2 - 门禁解除与能力面翻转（Fix）

Status: completed
Targets: `nop-stream-core`（graph/connector.registry/exceptions）、`nop-stream-connector`、`nop-stream-connector-jdbc`

- Item Types: `Fix`

- [x] 移除 `StreamGraphGenerator.transformSink` 的 2PC 并行度门禁分支（`StreamGraphGenerator.java:320-328`）
- [x] 两内建工厂能力声明 `PLANNING_GATE_PARALLELISM_1` → `PARALLEL`；`ConnectorParallelism` 枚举值语义改写（门禁值移除或保留为 deprecated 语义说明，与 `StreamConnectorCatalog` 一致性校验逻辑同步改写——「iff 2PC」不变式按新契约重述）；`ConnectorCapabilityDescriptor.java:24` javadoc 的同一 iff 不变式陈述同步（枚举删除时 `{@link}` 不悬空）
- [x] `ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED` 错误码处置：门禁移除后 main 代码零引用则删除常量（含注册表/invariants 同步）；若 D1 裁定 (a) 复用/新设错误码则按裁定接线
- [x] D1 裁定为 (a) 时：跨并行度恢复 typed 拒绝实现 + focused 测试（拒绝错误码与参数断言）
- [x] 既有门禁测试更新：`TestStreamGraphGenerator` 两拒绝用例（:365/:406）改为 P=2 构建通过断言；`TestJdbcConnectorFactory:95`/`TestFileMessageConnectorFactories:90`/`TestStreamConnectorRegistryDiscovery:140,148,278` 声明断言翻转
- [x] planning 回归：非 2PC sink 并行路径零变更（既有测试不动）

Exit Criteria:

- [x] 2PC sink P=2/P=3 的 `StreamGraph`/`JobGraph` 构建通过（更新后的 `TestStreamGraphGenerator` 用例钉定，含 per-subtask 拆分结果断言）
- [x] `rg "PLANNING_GATE_PARALLELISM_1" nop-stream --type java` 结果与Phase 1 裁定后的新契约一致（枚举移除则零命中；保留则仅新语义注释命中）
- [x] 错误码处置与裁定一致：`rg "ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED" nop-stream/*/src/main` 零命中（或按 D1 裁定显式保留并注明新用途）
- [x] `./mvnw test -pl nop-stream-core,nop-stream-connector,nop-stream-connector-jdbc,nop-stream-runtime -am` 绿（含 runtime：`TestStreamConnectorRegistryDiscovery` 在 runtime src/test，D1(a) typed 拒绝实现亦在 runtime）
- [x] **无静默跳过**：门禁移除后未 override `copyForSubtask(int)` 的子类在 P>1 运行时仍响亮失败（基类默认行为回归用例钉定——`TestOperatorSubtaskIsolation` 既有用例保持绿）
- [x] Phase 1 裁定如改变 public contract（错误码增删/枚举语义）：`ai-dev/design/nop-stream/` 对应段落已按裁定更新；否则 `No owner-doc update required`（Phase 1 已覆盖则注明）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 端到端证明（Proof）

Status: completed
Targets: `nop-stream-fraud-example`（LOCAL e2e）、`nop-stream-runtime` gated 测试

- Item Types: `Proof`

- [x] LOCAL e2e（JDBC）：keyed-P>1 + `JdbcTwoPhaseCommitSink` P>1 复合形态（C2 路由形态——可扩展 S1 变体或新场景测试）：运行至完成，结果集 multiset exactly-once 断言（无重复无丢失）；kill/recover（同 P）后恢复续算断言；台账按 (epoch, subtask) 逐 subtask 行断言
- [x] LOCAL e2e（File）：`FileTwoPhaseCommitSink` P>1：per-subtask `.sK` 输出文件与 manifest 键共存断言、精确输出文件集断言（无互相覆盖）、恢复后无重复
- [x] **接线验证（Minimum Rules #23）**：e2e 中断言运行时每 subtask UDF 拷贝真实独立（行为级代理 = 台账逐 subtask 行 / 输出逐 subtask 文件；至少一处直接断言 subtask 身份进入提交键）
- [x] DISTRIBUTED gated 多 JVM（`@EnabledIfSystemProperty` 既有模式）：MiniStreamCluster 真实多 JVM，2PC sink P>1：kill TM → 恢复 → fencing epoch 严格递增断言 + exactly-once 结果集断言（沿 `TestS2RestoreRescaleMultiJvmE2E`/CHAOS 断言面先例）。**sink 族选型口径**：JDBC 族优先（沿 S1 模式——MiniStreamCluster H2 AUTO_SERVER 共享库承载跨 JVM 台账/数据表）；若 File 族多 JVM 形态成本低可一并覆盖。最终覆盖矩阵（sink 族 × 执行形态）与选型理由记录在 plan/测试留档——至少一个族有真实多 JVM 证明，LOCAL-only 的族显式标注
- [x] D1 裁定的跨并行度行为在 e2e 层有对应验证（裁定 (a) = 拒绝路径断言；裁定 (b) = 重分布后 exactly-once 断言）
- [x] 既有 S1/S2 场景测试与 legacy 套件零回归

Exit Criteria:

- [x] **端到端验证（Minimum Rules #22）**：从用户入口点（DataStream 构建/XDSL 场景）到最终输出（JDBC 表行/文件集），2PC sink P>1 完整跑通且 exactly-once 成立——LOCAL 与 DISTRIBUTED 双形态各有留档测试，且 sink 族 × 执行形态覆盖矩阵在 plan/测试留档中显式记录（至少一个族有真实多 JVM 证明，LOCAL-only 的族显式标注）
- [x] kill/recover 恢复路径：恢复后 durable-未提交重提交幂等（台账守卫命中）+ 非 durable abort，P>1 形态下断言通过
- [x] fencing 断言：多 JVM 恢复轮 fencing epoch 严格递增（gated 测试内日志/行为级断言）
- [x] 新增测试清单与验证行为一一对应（每条新断言注明验证哪个新行为）
- [x] 默认态 `./mvnw test -pl nop-stream -am -T 1C` 全绿；gated 套件启用态执行记录在案
- [x] 场景/引擎若发现新缺陷：就地修复配回归（大缺陷按 roadmap Rules 路由 Follow-up，不静默）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - owner docs supersession 与写回（Fix / Follow-up）

Status: completed
Targets: `ai-dev/design/nop-stream/*`、`docs-for-ai/03-modules/*`、roadmap、测试 javadoc

- Item Types: `Fix`、`Follow-up`

- [x] `checkpoint-design.md` §6.4.1 改写为已落地终态：B1/B2 已解事实（sink 身份经 `copyForSubtask(int)` 注入、per-subtask 隔离）、门禁解除、D1/D2/D3 裁定、公开契约改为「P=N 已证明（LOCAL+多 JVM）」；:1135 pending transaction 行同步
- [x] `comparison.md:404` 事务身份行更新（P>1 已证明）
- [x] `stream-dsl-design.md:159` 活门禁陈述更新（「2PC sink 门禁读生效并行度，声明路径不放宽」句随门禁解除改写）
- [x] `connector-design.md:515`「描述符 ⟺ 端点 instanceof TwoPhaseCommitSinkFunction」不变式按新契约改写（Phase 2 改的就是该不变式，必涉）
- [x] `docs-for-ai/03-modules/nop-stream-connectors.md`：矩阵行 jdbc-2pc/file 2PC「并行度」列 → 支持并行（附 per-subtask 台账键/输出后缀机制）；「硬门禁：2PC sink 并行度」节移除或改写为能力说明
- [x] `nop-stream-user-guide.md:112/:149` 门禁句更新；`nop-stream-migration-guide.md` 增台账 schema 说明（复合主键 + D2 遗留口径）
- [x] `distributed-runbook.md:156` 门禁条目 supersession；`composite-scenario-design.md:319/:333` 路由注记 supersession（CONN-01 successor 已落地）
- [x] 测试 javadoc 中 CONN-01 defer 注记更新（`TestS2RecoveryAndRescaleE2E.java:49`、`TestS2RestoreRescaleMultiJvmE2E.java:47`、`ThrottledScenarioSinks.java:36`）
- [x] `docs-for-ai/INDEX.md` / `04-reference/source-anchors.md`：如路由/锚点变化则同步
- [x] roadmap item 35 写回 `done`（closure audit 通过后、Plan Status 置 `completed` 前）

Exit Criteria:

- [x] `rg "CONN-01|PARALLELISM_NOT_SUPPORTED|规划期门禁 P=1|仅 parallelism=1 已证明" docs-for-ai ai-dev/design nop-stream/*/src` 的命中全部为 supersession 注记/历史记录/新能力描述，无「当前仍门禁」的活陈述
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] 文档-代码一致性抽查：矩阵行/用户指南句与 live 行为一致（并行 2PC 可用 + 机制描述准确）

## Closure Gates

> **关闭条件**：本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 2PC sink P>1 exactly-once 公开契约成立且双形态（LOCAL + 真实多 JVM）端到端证明在档
- [x] D1/D2/D3 三裁定落档且实现与裁定一致
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs（design + docs-for-ai）同步到 live baseline
- [x] 独立子 agent closure-audit 已完成并记录证据（fresh session）
- [x] **Anti-Hollow Check**：closure audit 验证（a）per-subtask 拷贝在运行时被真实构建与调用（e2e 行为级证据），（b）无空方法体/静默跳过/no-op 作为正常实现（门禁移除不引入静默路径——未隔离子类仍响亮失败）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿（默认态）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module <affected-modules> --severity high` exit 0
- [x] `node ai-dev/tools/check-nop-stream-invariants.mjs` exit 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] roadmap item 35 写回 `done` + Last updated 同步

## Deferred But Adjudicated

（draft 阶段为空；执行中发现的可延期项按 Allowed Deferred Classifications 落此并附 Why Not Blocking Closure）

## Non-Blocking Follow-ups

- 命名规范治理观察（审查 F6）：本 mission 目录 20+ 份 plan 均用 `{YYYY-MM-DD-HHmm}-{N}-{slug}.md` 日期前缀命名（mission-driver 引擎约定），与 plan guide Rule #21 的 `NN-描述.md` 编号命名存在目录级偏差——既有惯例偏差已既成事实，留待 guide 层统一裁定（本 plan 沿用 mission 目录惯例，不阻塞）。

## Closure

Status Note: 四 Phase 全部完成：D1/D2/D3 三裁定落档（checkpoint-design §8.5.2/§6.4.2/§6.4.3）并按裁定实现（typed 拒绝/文档化重建/运行时 fail-fast）；规划期门禁移除 + 能力面翻转（枚举值删除、两工厂 PARALLEL、catalog 不变式重述）；双形态端到端证明在档（LOCAL JDBC/File + 真实多 JVM kill TM，覆盖矩阵显式记录）；owner docs 全量 supersession；独立 closure audit 两轮（首轮 REJECTED 于 1 Major docs 残留 → 修复后复审 APPROVED）。roadmap item 35 写回 done。
Completed: 2026-09-04

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent ×2（fresh sessions）
- Audit Sessions: 首轮 `ses_f9567e2d7ffeiQy2RngPXbR7c5`（CLOSURE-AUDIT: REJECTED——F1 Major `docs-for-ai/03-modules/nop-stream-connectors.md:33` 单一事实源段仍留活态旧不变式陈述 + F2/F3 Minor connector-design §8.4/§8.9 残段 + F4 Minor gated surefire 产物被默认态覆写）；复审 `ses_f955fb970ffeH2KwDSHFhWktIA`（**CLOSURE-AUDIT: APPROVED**——F1-F4 四项修复逐项 live 核验 PASS；PLANNING_GATE/error-code/门禁词三向 grep 扫描全部命中均为 supersession/历史表述；check-doc-links exit 0）
- Evidence:
  - Phase 1 Exit Criteria 4/4 PASS（D1 §8.5.2 :1219-1229 / D2 §6.4.2 :802-808 / D3 §6.4.3 :810-816，各含裁定值/live 证据/拒绝面；daily log Phase 1 条目）
  - Phase 2 Exit Criteria 8/8 PASS（`TestStreamGraphGenerator.java:368/:417` P=2/P=3 构建通过 + per-subtask 拆分断言；`rg PLANNING_GATE_PARALLELISM_1 nop-stream --type java` 零命中；`rg ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED nop-stream/*/src/main` 零命中；D1(a) 实现于 `GraphModelCheckpointExecutor.java:1396-1400`（keyed-rescale 分支前、不 gate 于 vertexKeyed，helper `:1524-1540`）；focused `TestTwoPhaseCommitSinkParallelismChangeRestoreE2E` 4/4（keyed 形态 + same-P 恢复含）；`TestOperatorSubtaskIsolation` 17/17 绿钉定基类 fail-fast；4 模块 mvn 绿）
  - Phase 3 Exit Criteria 7/7 PASS（`TestParallel2PcJdbcE2E`：multiset exactly-once 两 run + 恢复续算 epoch 严格递增 + 台账 ∃epoch 双 subtask 行（:153）+ D1 场景级拒绝 cause-chain 参数断言（:173-181）；`TestParallel2PcFileE2E`：shared-epoch `.s1` 共存（:127-145）+ 精确文件集 + manifest==文件集；`TestParallel2PcMultiJvmE2E` gated 1/1 绿（on-disk surefire 93.20s，kill tm-1 → fencing 严格递增 → exactly-once 收敛 + 共享台账跨 JVM per-subtask 行）；覆盖矩阵 javadoc 留档（JDBC 双形态 / File LOCAL-only 显式标注）；默认态 `./mvnw test -pl nop-stream -am -T 1C` BUILD SUCCESS ×2（一次 rocksdb 基准 flake 隔离复跑绿，零相关文件触碰））
  - Phase 4 Exit Criteria 4/4 PASS（rg 门禁模式命中全为 supersession/历史表述；check-doc-links --strict exit 0；矩阵/指南句与 live 行为抽查一致——含首轮 audit 发现的 F1-F3 残留修复）
  - Closure Gates 12/12 PASS（含下述工具门禁）
  - `check-plan-checklist.mjs --strict` exit 0（全 checklist [x] + Closure Evidence 已写入）
  - Anti-Hollow 检查：PASS——(a) 接线追踪 `GraphExecutionPlan.java:379`（另 `SupervisionLoop.java:632`/`RemoteGraphExecutionPlanBuilder.java:211`）→ `OperatorChain.deepCopy(int):252` → `StreamSinkOperator.copyForSubtask(int)` → `JdbcTwoPhaseCommitSink.copyForSubtask:160`/`FileTwoPhaseCommitSink:169`（真实 taskIndex、独立拷贝携带 subtask 身份），行为级证据 = 台账 (epoch, subtask) 双 subtask 行 + `.s1` 文件/manifest 键（e2e 断言）；(b) `transformSink`（`StreamGraphGenerator.java:308-331`）无 2PC 分支/空 if/死代码，基类默认 fail-fast 保持，D1 typed 抛出（3 参数），same-P 路径不受影响；`scan-hollow-implementations --module nop-stream-core,nop-stream-runtime,nop-stream-connector,nop-stream-connector-jdbc --severity high` exit 0（0 findings）
  - Deferred 项分类检查：`Deferred But Adjudicated` 空；Non-Blocking Follow-ups 仅命名规范治理观察（watch-only，非 in-scope defect）

Follow-up:

- no remaining plan-owned work（命名规范治理观察已记录于 Non-Blocking Follow-ups，留待 guide 层统一裁定）
