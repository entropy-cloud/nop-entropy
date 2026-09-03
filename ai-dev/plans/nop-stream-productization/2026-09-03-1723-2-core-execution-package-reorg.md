# nop-stream-core execution 根包重组：Task 执行族下沉子包（roadmap item 23）

> Plan Status: active
> Mission: nop-stream-productization
> Work Item: item 23（[Follow-up，来源 item 7 plan `2026-09-01-0938-2`（core 审计报告 §1.2 #8：06-30 审计 §6.2 建议的 execution.runtime 拆分未做，Task 执行族下沉 = 跨模块 import 变更））
> Last Reviewed: 2026-09-03
> Source: `ai-dev/analysis/2026-09/2026-09-01-nop-stream-core-module-audit.md` §1.2 #8（根 31 文件 + 5 子包现状、认知负荷治理定性）；`ai-dev/analysis/2026-06-30-nop-stream-code-audit.md` §6.2（原始拆分建议）
> Related: item 22（通配符导入清理）先于本 plan 执行（roadmap 首 `todo` 顺位天然保证；排序价值 = import 面可计数，非正确性硬依赖——fallback 见 Current Baseline 前置排序条目）；`2026-09-03-0830-3`（item 21/30 SerDe 收敛，已保持 execution 包 untouched，无冲突）

## Purpose

把 `io.nop.stream.core.execution` 根包中的 Task 执行族类（`Task`/`SubtaskTask`/`TaskExecutor`/`StreamTaskInvokable` 及 Phase 1 裁定的紧耦合邻接类）下沉到新子包，消除根包认知负荷（06-30 审计 §6.2 + core 审计 §1.2 #8 的组织治理项），**零行为变更**——纯包结构移动 + 全量 import 更新，由全量测试（含 gated 分布式默认态）钉定。

## Current Baseline

（2026-09-03 live 复算）

- `nop-stream-core/src/main/java/io/nop/stream/core/execution/` 根包现状：**32 个 .java 文件**（含 `package-info.java`；roadmap 口径 31 个 + package-info）+ 5 个既有子包（`buffer/`、`flow/`、`materialization/`、`plan/`、`transport/`）。
- **移动主体（roadmap item 23 点名 4 类）**：
  - `Task.java`（`public class Task implements Runnable, Serializable`，`serialVersionUID=1L`——见 Phase 1 序列化影响裁定）
  - `SubtaskTask.java`（`implements Runnable`，重度引用 `Subtask`——17 行引用）
  - `TaskExecutor.java`
  - `StreamTaskInvokable.java`（`implements Invokable<Void>`，`Invokable extends Serializable` 且自有 `serialVersionUID`——**亦属 Serializable**）
- **紧耦合邻接类（Phase 1 裁定是否随迁）**：`TaskStateTransition`（Task/SubtaskTask 状态机，Task.java 内 7+ 处引用）、`Subtask`、`TaskMailbox`、`TaskProcessingTimeService`——这些类与 4 主体类同根包、互相无 import 引用，迁移后跨包引用需补 import（或随迁保持同包）；反向依赖同样存在：随迁类引用根包留守类（如 `StreamTaskInvokable` 引用 `RecordWriter`/`InputGate`/`MailboxExecutor`）迁移后需**新增** import。
- **跨模块引用面（live 复算，含 import 语句之外的三类盲区）**：
  - main import：8 个文件显式 import 4 主体类（core 2：`StreamExecutionEnvironment`/`JobGraphGenerator` + runtime 6：`RemoteTaskDeploySupport`/`GraphModelCheckpointExecutor`/`SupervisionLoop`/`TaskManager`/`RemoteGraphExecutionPlanBuilder`/`SubtaskPlanBuilder`）
  - test import：31 个文件显式 import 4 主体类（口径 = `rg -l "import io\.nop\.stream\.core\.execution\.(Task|SubtaskTask|TaskExecutor|StreamTaskInvokable);" <path> | rg '/src/test/'`，2026-09-03 live 复算；依赖 item 22 完成后数字可信——通配符导入会静默掩盖引用面，这正是 item 22 先行的理由；**注**：item 22 只消通配符盲区，下列盲区与 item 22 无关，须独立盘点）
  - **javadoc FQN**：≥3 个 main 文件以 `{@link io.nop.stream.core.execution.StreamTaskInvokable}` 内联引用（runtime `rpc/IStreamTaskRpcService.java` 与 `rpc/TaskDeploymentDescriptor.java`（`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/rpc/`）、core `operators/StreamSourceOperator.java`（`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/`））——编译器不校验
  - **test 内联 FQN**：≥5 个测试文件在代码体内用完整限定名（非 import），如 `TestTaskManager`、`TestIdlePeriodProcessingTimeWindowE2E`、`TestRemoteDeployCheckpointWiringE2E`、`TestSavepointVertexSetDifferential`、`TestSupervisionLoopCheckpointReconnectE2E`——编译器不报错、逻辑不失败但引用旧包
  - **反射字符串**：`TestOutputContractInvariant.java` 内 10+ 处字符串字面量（如 `"io.nop.stream.core.execution.StreamTaskInvokable$RecordWriterOutput"`）——仅运行时失败，编译器不可见
  - 同包免 import 引用：execution 根包内其余文件引用 4 主体类无需 import，迁移后需补 import——逐文件编译驱动补齐。
- **门禁注册表钉定（迁移必碰的硬设施，两份注册表 + 一份清单）**：①`ai-dev/audits/nop-stream-invariants/output-contract-registry.json` 以 `fqcn`/`file` 字段钉定 `StreamTaskInvokable$RecordWriterOutput`（含嵌套类，2 条）及 `.../core/execution/StreamTaskInvokable.java` 路径；②`ai-dev/audits/nop-stream-invariants/wiring-registry.json` 以 **wiringPoint `file:line`**（8 条，:529/:530/:259/:297/:316/:322/:493/:502——2026-09-03 item 16/17 期间刚因行号漂移重钉过一次）钉定同一文件；③`red-list.md` 以行号钉定发射点。`check-nop-stream-invariants.mjs` 的 scan-output-contract（`fqcnToFile` 存在性校验）与 scan-wiring（V3：wiringPoint `file:line` 必须在 main 代码存在；V5：类型消失即红）均为 CI fail-fast 硬门禁；`red-list.md` 为人工复核清单（工具不读它）。移动类 + 新增 import 致行号漂移 = 两注册表必红，**必须在 Phase 2 同步更新**。
- 06-30 审计 §6.2 建议的子包名为 `execution.runtime`；**与既有 `nop-stream-runtime` 模块名存在词面撞车风险**（`io.nop.stream.core.execution.runtime` vs `io.nop.stream.runtime.*`），Phase 1 必须裁定最终子包名。
- 根包 `package-info.java` 现有描述字面包含 "Contains Task and TaskExecutor for job execution"——迁移后该句失真，必须更新；新子包按需建 `package-info.java`。
- 序列化面 live 核查（起草时初查）：`TaskDeploymentDescriptor` 刻意承载 `JobGraph` 而非活体 invokable；checkpoint 产物序列化 `TaskStateSnapshot`（经 `CheckpointSerDe`），从未序列化 `Task` 族——Phase 1 仍须按清单复核并留证据。
- 定性：认知负荷治理项，**非缺陷**（core 审计 §1.2 #8 明确归类）。
- 前置排序：item 22 先行（roadmap 首 `todo` 顺位天然保证 22 → 23；排序价值 = 通配符盲区消除后 import 面**可计数**。注：通配符导入的文件在类移动后会大声编译失败（非静默），故 22 → 23 是可计数性偏好而非正确性硬依赖；若 item 22 延期/取消，本 plan 执行时须把引用面盘点中的通配符文件先行手工展开（bounded 增量），不得阻塞）。

## Goals

- Task 执行族（4 主体类 + Phase 1 裁定的随迁邻接类）迁移至新子包，execution 根包文件数相应下降（目标数字 = 32 − 随迁类数，Phase 1 裁定后固化）。
- 全部引用方（core/runtime main + 全部 test）import 更新，编译零错误、全量测试绿（含 gated 分布式默认态）。
- 零行为变更：类内容（除 package 声明与 javadoc 内包引用）零改动；`Serializable` 类的序列化兼容性经 Phase 1 裁定显式处理。
- 公共 API 位置变更同步 owner doc（若 design 文档锚定了这些类的旧 FQN）。

## Non-Goals

- execution 根包内**其他类**的进一步拆分/重组（`InputGate`/`ResultPartition`/`MailboxExecutor` 等数据面/调度面类的归属再平衡——06-30 建议未点名，保持最小 diff）。
- 任何行为变更、签名变更、访问修饰符调整。
- core `execution.transport` vs runtime `transport` 边界调整（core 审计 #15 已裁定 watch-only 维持）。
- items 25/26/27/28/29（runtime 侧治理项）。

## Scope

### In Scope

- 4 主体类 + 邻接类（`TaskStateTransition`/`Subtask`/`TaskMailbox`/`TaskProcessingTimeService`）的**随迁/留守两态裁定**（每类一条裁定 + 理由）。
- 新子包命名裁定（`execution.runtime` 撞车风险 vs 替代名如 `execution.task`）。
- `package-info.java` 若涉及包描述迁移，随之更新。
- 跨模块 import 更新（main + test）+ 同包引用补 import。
- `Serializable` 序列化影响裁定（`Task` 等）。
- owner doc / design 文档 FQN 引用同步。

### Out Of Scope

- 上列 Non-Goals 全部。
- fraud-example 场景 XDSL 或配置文件中若出现 FQN 引用的同步（Phase 2 核查后如有则属 In Scope 顺带，预期无——场景用 bean 注入非 FQN）。

## Execution Plan

### Phase 1 - 迁移集与命名裁定 + 序列化影响核查

Status: planned
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/`（裁定与核查，不动代码）

- Item Types: `Decision | Proof`

- [ ] 随迁集裁定：对 4 主体类 + 4 邻接类逐类裁定（随迁 / 留守），裁定标准 = 与 Task 执行族的内聚度（互相引用密度、概念归属）与最小 diff 原则；产出随迁类清单与根包剩余文件数目标
- [ ] 子包名裁定：`execution.runtime`（06-30 原建议，与 nop-stream-runtime 模块词面撞车）vs `execution.task`（或其它）——裁定 + 理由 + 拒绝方案
- [ ] 序列化影响核查：`Task`/`StreamTaskInvokable`（均 Serializable）——核查 live 代码是否存在按 FQN 持久化/跨 JVM 传输 Task 执行族（或随迁集中其他 Serializable 类）实例的路径（checkpoint 产物、RemotePipelineSpec、JSON 序列化面、Java 序列化面）；结论两态：①无持久化路径（纯内存对象，起草初查倾向此结论——`TaskDeploymentDescriptor` 承载 `JobGraph` 非 invokable）→ 移动安全；②存在路径 → 裁定兼容策略（本 plan 内禁止引入会破坏既有 checkpoint 恢复的移动；如存在则该类改留守或配迁移兼容）
- [ ] FQN 引用面盘点（**口径必须覆盖五类形态并固化 rg 模式供 closure 复用**）：①import 语句；②javadoc `{@link ...}` 内联 FQN；③代码体内联限定名（含测试）；④字符串字面量/反射（含嵌套类 `$` 形态）；⑤门禁注册表与 red-list（`ai-dev/audits/nop-stream-invariants/` 下 JSON/md）。盘点范围 = 全仓库代码 + `ai-dev/design/nop-stream/` 全部文档（live 计数为准，不预设 16 仓名义数）+ `docs-for-ai/`；把固化后的 rg 模式（含 `$` 转义）记入日志，Phase 2 处置与 closure 复算用同一模式

Exit Criteria:

- [ ] 裁定记录（随迁集 + 子包名 + 拒绝方案）与序列化影响核查结论（含证据锚点：核查过的序列化路径清单）落日志
- [ ] FQN 引用面清单存在且五类形态全覆盖（固化 rg 模式记录在案，closure 复算可复现）
- [ ] owner-doc 影响清单：`ai-dev/design/nop-stream/` 全部文档（live 计数）+ `docs-for-ai/03-modules/nop-stream*` 中旧 FQN 引用条目（Phase 2 同步依据）
- [ ] 本 phase 不动代码：`./mvnw compile -pl nop-stream/nop-stream-core -am` 维持现状通过即可（No new test required: 纯裁定与核查 phase——显式声明）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 类迁移 + 跨模块 import 更新

Status: planned
Targets: `nop-stream/nop-stream-core/`、`nop-stream/nop-stream-runtime/`（main + test）、随迁集涉及的其它模块

- Item Types: `Fix`

- [ ] 随迁集类文件移动至新子包（package 声明 + 位置变更；类体内容除 package 声明、新增 import（随迁类引用根包留守类，如 `StreamTaskInvokable`→`RecordWriter`/`InputGate` 等）与 javadoc 包引用外零改动）
- [ ] core execution 根包同包引用方补 import（编译驱动逐文件）
- [ ] runtime main 6 文件 import 更新 + test 31+ 文件 import 更新（item 22 完成后 import 面全显式、编译器可全量验证；**编译器不可见的三类引用面按 Phase 1 固化模式逐条处置**：javadoc `{@link}`、测试内联限定名、反射字符串字面量）
- [ ] **门禁注册表同步（必做，否则 invariants 硬门禁必红）**：①`output-contract-registry.json` 中随迁类（含 `StreamTaskInvokable$RecordWriterOutput` 嵌套类）的 `fqcn`/`file` 字段更新到新包路径；②`wiring-registry.json` 中 8 条 StreamTaskInvokable wiringPoint 的 `file` 路径与行号（新增 import 导致行号漂移，逐条重勘 re-pin——沿 2026-09-03 item 17 重钉先例）；③`red-list.md` 行号钉定重勘（人工清单，随 ①② 同步）
- [ ] FQN 字符串引用处置：Phase 1 清单逐条更新（代码注释/javadoc/design 文档/owner doc/test 资源两态：更新 or 裁定无需更新）
- [ ] `package-info.java` 双侧处置：根包描述句 "Contains Task and TaskExecutor..." 更新为留守职责；新子包建 `package-info.java` 描述 Task 执行族职责

Exit Criteria:

- [ ] execution 根包 .java 文件数 = 32 − 随迁数（closure 复算）；新子包文件数与 Phase 1 清单一致
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿（含 fraud-example 场景 LOCAL 测试 = 端到端验证：Task 执行族迁移后从 env.execute() 到 sink 的完整路径仍跑通；No new test required: 纯包移动零行为变更，行为面由全量既有测试含 S1/S2 场景覆盖——显式声明）
- [ ] gated 分布式默认态绿：默认套件即含分布式路径单测（迁移涉及 runtime TaskExecutor 部署面）；如需 gated 多 JVM 复核，跑 `TestS2RestoreRescaleMultiJvmE2E` 一条钉定（gated 启用态）
- [ ] **接线验证**（规则 23）：迁移后 `TaskExecutor`/`StreamTaskInvokable` 在 runtime 部署路径的 import 与调用连通性由编译 + 全量测试证明（类型系统层面）；运行时连通性由场景 e2e 证明
- [ ] owner-doc 同步：Phase 1 影响清单内条目逐条更新（预期 `state-management-design.md`/`distributed-runbook.md` 等如有 Task FQN 锚点）；无条目则显式 `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 随迁集全部落位新子包，根包文件数达成 Phase 1 固化目标（复算记录）
- [ ] 全仓库旧 FQN 引用零残留（以 Phase 1 固化 rg 模式复核——五类形态含 import/javadoc/内联/字符串/注册表；排除历史性文档 `ai-dev/analysis/`、`ai-dev/logs/`、已完成 plan 的历史记载——历史文档不改写）
- [ ] 零行为变更证明：全量测试绿 + gated 抽查绿；`./mvnw compile`（nop-stream 聚合）通过
- [ ] 独立子 agent closure-audit 已完成并记录证据（含 Anti-Hollow 不适用声明：纯移动无新组件，接线由编译器+测试钉定）
- [ ] 门禁工具按名核验：`node ai-dev/tools/check-nop-stream-invariants.mjs` exit 0（scan-output-contract/scan-wiring 对两注册表同步后的复核；red-list 为人工清单不在工具面内）+ `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` exit 0
- [ ] docs-for-ai 变更（如有）已跑 `node ai-dev/tools/check-doc-links.mjs --strict` exit 0

## Deferred But Adjudicated

（预留——Phase 1 裁定中若发现某邻接类随迁收益低于留守（如仅 1 处引用），允许「裁定留守」并在此记录，Classification: watch-only residual + 理由。）

## Non-Blocking Follow-ups

- execution 根包剩余文件（数据面 `InputGate`/`ResultPartition`、调度面 `MailboxExecutor` 等）的进一步归属再平衡（本 plan 只处理审计点名的 Task 执行族，其余维持现状）

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
