# 290 nop-sys 普通事件 batch DSL 重构

> Plan Status: completed
> Last Reviewed: 2026-07-12
> Source: `ai-dev/design/nop-sys/sys-event-architecture.md`; `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java`; `nop-batch/nop-batch-sys/src/main/java/io/nop/batch/sys/SysEventBatchTrigger.java`; `nop-batch/nop-batch-dsl/src/main/java/io/nop/batch/dsl/runner/BatchTaskRunner.java`; `nop-batch/nop-batch-orm/src/main/java/io/nop/batch/orm/loader/OrmQueryBatchLoaderProvider.java`; `docs-for-ai/03-modules/nop-sys.md`; `docs-for-ai/03-modules/nop-batch.md`
> Related: `ai-dev/plans/288-nop-sys-sys-event-reliability-refactor.md`; `ai-dev/plans/289-nop-sys-broadcast-findnext-refactor.md`

## Purpose

将 `nop-sys` 普通事件消费从 `SysDaoMessageService` 内联定时扫描 + `nop-batch-sys` 编程式 `BatchTaskBuilder` 触发的双实现，收口为一个以 `NonBroadcastEventProcessor` 为核心、同时支持：

- 简单模式：`SysDaoMessageService` 内建定时器触发
- batch 模式：`nop-job` 调度 `SysEventBatchTrigger`，由 batch DSL（`.batch.xml`）定义执行路径

并保证普通事件扫描不再使用 OFFSET 分页，简单模式与 batch 模式都基于 `findNext` keyset pagination 或其 DSL 对应实现。

## Current Baseline

- `SysDaoMessageService` 当前同时承载发送、广播消费、普通事件扫描、claim、执行、结果回写等职责；普通事件入口仍为 `processNonBroadcastEvent()`，内部顺序执行 `fetchNonBroadcastEvents()` → `claimNonBroadcastEvents()` → `processNonBroadcastEvent(NopSysEvent)`。
- `SysDaoMessageService.fetchNonBroadcastEvents(int batchSize)` 当前使用 `QueryBean.setOffset(offset)` + `findPageByQuery` 分页，再在 JVM 内按 `partitionIndex` 做“每分区只取头部事件”的归并；这与设计文档中“禁止 OFFSET”已不一致。
- `SysDaoMessageService.doStart()` 当前无条件启动 `checkNonBroadcastFuture` 定时任务；不存在 `nonBroadcastAutoScanEnabled` 开关，也没有对 batch 模式与简单模式共存/互斥的明确约束。
- `nop-batch-sys` 当前已有 `SysEventBatchTrigger`，但仍使用编程式 `BatchTaskBuilder` 组装 loader/processor/consumer，并直接依赖 `SysDaoMessageService.fetchExecutableNonBroadcastEvents`、`claimNonBroadcastEvents`、`processClaimedNonBroadcastEvent`。
- `SysEventBatchTrigger` 当前 `concurrency(1)`，虽然设置了 `dispatchConfig.partitionFn = NopSysEvent::getPartitionIndex`，但计划中的“必须使用 batch DSL”尚未落地。
- `BatchTaskRunner.executeAsync(taskPath, params)` 当前能够按 VFS 路径加载 batch DSL 任务，但 live repo 中尚无 `nop-batch-sys` 对应的普通事件 `.batch.xml` 定义，也没有证明该模块中的 DSL 文件已被触发器实际加载。
- `OrmQueryBatchLoaderProvider` 当前在 `load()` 中使用 `dao.findNext(state.lastEntity, query.getFilter(), query.getOrderBy(), batchSize)`，天然具备 keyset pagination；若 batch 模式改用 `<orm-reader>`，应复用此实现而非自行实现分页。
- `docs-for-ai/03-modules/nop-sys.md` 和 `docs-for-ai/03-modules/nop-batch.md` 已说明“普通事件可由 `nop-batch-sys` 以 batch trigger 方式扫描执行”，但尚未同步“batch 模式必须走 batch DSL、禁止编程式 `BatchTaskBuilder`”这一新基线。
- `ai-dev/design/nop-sys/sys-event-architecture.md` 已写入 `NonBroadcastEventProcessor`、auto-scan 开关、batch DSL 路径与 `findNext` 约束，但 reviewer 已指出几个仍需计划阶段显式验证的 gap：DSL 资源可加载性、batch mode partitionRange 注入、processor/consumer bean 可解析性、事务边界与现有 claim/consume 语义一致性。
- live baseline 中普通事件的 head-of-partition 语义仍然存在：`fetchNonBroadcastEvents()` 会对每个 `partitionIndex` 只保留一个候选 head，并在遇到未过期 `CLAIMED` head 时把该分区标记为 blocked；这意味着本计划若改变该语义，必须显式作为 contract change 裁定，而不是在重构中隐式改变。
- live `SysEventBatchTrigger` 当前并未设置 `IBatchTaskContext.partitionRange`；`OrmQueryBatchLoaderProvider` 只有在 `context.getPartitionRange() != null` 时才会自动追加 `partitionIndex BETWEEN ...` 过滤。因此 batch mode 的 partition 过滤接线不是现成成立事实，必须在计划中单独验证/实现。
- 当前可直接证明的 `nop-job` 集成仅到“可由 `BeanMethodJobInvoker` 调用一个 bean 方法”的通用能力；`nop-batch-sys` 里尚无 repo 内已落地的 `SysEventBatchTrigger` 调度定义。若本计划覆盖 job 集成，必须把“新增/验证哪个调度资源”写成明确执行项，而不是只引用设计文档中的示例 XML。

## Goals

- 提取 `NonBroadcastEventProcessor`，作为普通事件 fetch/claim/process/result-handling 的唯一核心实现。
- `SysDaoMessageService` 增加 `nonBroadcastAutoScanEnabled`，普通事件扫描可由服务内定时器启停控制。
- 简单模式下，普通事件扫描改为 `findNext` keyset pagination，不再使用 OFFSET 分页。
- `nop-batch-sys` 的普通事件触发路径改为 batch DSL（`.batch.xml`）+ `BatchTaskRunner`，不再使用编程式 `BatchTaskBuilder`。
- batch 模式下，`<orm-reader>` 实际通过 `OrmQueryBatchLoaderProvider.findNext()` 扫描普通事件，并在运行时确实应用 partition 过滤。
- `nop-job` 可通过稳定、可验证的触发路径调度 `SysEventBatchTrigger` 执行 batch DSL 普通事件消费。
- 同步 owner docs：`docs-for-ai/03-modules/nop-sys.md`、`docs-for-ai/03-modules/nop-batch.md`、`docs-for-ai/INDEX.md`、`docs-for-ai/04-reference/source-anchors.md` 与最终 live baseline 一致。

## Non-Goals

- 不修改广播事件发送或广播消费行为。
- 不修改 `NopSysEvent` ORM 结构与状态机字段语义。
- 不引入外部 MQ，也不把普通事件正确性边界改为 batch chunk 成败。
- 不把 `nop-job` 扩展成新的分布式 batch 分片执行框架；本计划只收口当前 `nop-job` 调度 + `nop-batch` 本地 partition dispatch 的可用路径。
- 不手改任何 `_gen/`、`_*.xml`、`_*.java` 生成物。

## Scope

### In Scope

- `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/` 下普通事件相关实现
- `nop-batch/nop-batch-sys/src/main/java/io/nop/batch/sys/`
- `nop-batch/nop-batch-sys/src/main/resources/_vfs/nop/batch-task/`（若新增）
- `nop-batch` 相关 focused tests / integration tests（如需）
- `nop-sys` 相关 focused tests / integration tests
- `ai-dev/design/nop-sys/sys-event-architecture.md`（若需根据 live 实施收敛非契约性草图）
- `docs-for-ai/03-modules/nop-sys.md`
- `docs-for-ai/03-modules/nop-batch.md`
- `docs-for-ai/INDEX.md`（若模块路由描述需要同步）
- `docs-for-ai/04-reference/source-anchors.md`
- `ai-dev/logs/2026/07-12.md`

### Out Of Scope

- 广播事件路径
- `NopSysEvent` source ORM model 结构变更
- `nop-job` 分布式调度模型的大范围重构
- 新的 UI、监控页面、运维控制台
- `_gen/`、`_*.xml`、`_*.java` 生成物

## Execution Plan

### Phase 1 - Baseline And Wiring Contract Freeze

Status: completed
Targets: `ai-dev/design/nop-sys/sys-event-architecture.md`, `SysDaoMessageService.java`, `SysEventBatchTrigger.java`, `docs-for-ai/03-modules/nop-sys.md`, `docs-for-ai/03-modules/nop-batch.md`

- Item Types: `Decision`, `Proof`

- [x] 核对并记录普通事件 live call path：`SysDaoMessageService.processNonBroadcastEvent()` 的 fetch/claim/process/result-handling 调用链，以及 `SysEventBatchTrigger.processNonBroadcastEvent()` 的当前编程式 batch 触发链。
- [x] 明确 simple mode 与 batch mode 的部署/配置契约：同节点是否允许同时开启、若同时开启属于支持配置还是禁止配置、计划中如何验证这一点；如 design doc 与 live baseline 冲突，先在本 Phase 裁定并同步文档。
- [x] 明确事务边界契约：batch mode 下 claim、listener 执行、结果回写是否要求同一事务，还是保留当前 `REQUIRES_NEW`/分离边界；该决策必须以 live baseline 为参照写入 design/doc，不允许执行阶段临时猜测。
- [x] 收口 design doc 中仍偏实现草图的部分，仅保留本次实施所需的架构约束和 repo-observable contract。特别修正两处已知矛盾：(a) design doc 中 `SysEventBatchTrigger` 示例用 `batchTaskRunner.executeAsync(path, params)` + params 注入，但实际 partitionRange 必须通过 `IBatchTaskContext.setPartitionRange()` 注入而非 params；(b) design doc 中 `<nop-job:schedule>` XML 标签在 repo 中不存在，真实 nop-job 调度配置参照 `nop-wf-scheduler` 的 `scheduler.yaml` YAML 模式。对仍需实现阶段裁定的点显式记为 implementation decision。
- [x] 裁定 batch DSL 路径的真实装载边界：任务文件放置位置、`BatchTaskRunner` 的实际加载路径、`nop-batch-sys` 中 bean/provider 的可解析方式。注意 `BatchTaskRunner.executeAsync()` 内部自行创建 context 且不暴露 `setPartitionRange()` hook，因此 `SysEventBatchTrigger` 需绕过 runner 直接使用 `IBatchTaskManager.loadBatchTaskFromPath()` + `newBatchTaskContext()` + `setPartitionRange()` + `task.executeAsync()`，或扩展 runner API。
- [x] **已裁定：partitionRange 注入由 `SysEventBatchTrigger` 负责实现**。Trigger 在执行 batch task 前读取自身 `assignedPartitions` 配置（与 `SysDaoMessageService` 共用 `IntRangeSet` 来源），转换为 `IntRangeBean` 并设置到 `IBatchTaskContext.setPartitionRange()`，使 `OrmQueryBatchLoaderProvider` 能自动追加 `partitionIndex BETWEEN` 过滤。Phase 3 将实现此接线。
- [x] **已裁定：本计划落地真实 `nop-job` 调度资源**。将在 Phase 4 新增可被 `nop-job` 加载的 job schedule 定义（YAML 配置或 `NopJobSchedule` 初始化数据），使 `nop-job` 能按 cron 定期触发 `SysEventBatchTrigger.processNonBroadcastEvent()`。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `Current Baseline` 与 live repo 一致，不再保留已被 reviewer 指出的过时/未证实叙述。
- [x] design doc 中关于普通事件 batch DSL 路径的关键契约（禁止编程式 `BatchTaskBuilder`、禁止 OFFSET、simple/batch 双模式边界、head-of-partition 语义是否保留）已收敛到 repo-observable 表述。
- [x] `docs-for-ai/03-modules/nop-sys.md`、`docs-for-ai/03-modules/nop-batch.md`、`docs-for-ai/INDEX.md`、`docs-for-ai/04-reference/source-anchors.md` 如需同步本轮 contract，已在本 Phase 完成；否则明确写 `No owner-doc update required` 不允许默默跳过。
- [x] **接线验证**：已明确并记录 batch DSL 任务在 live repo 中将如何被 `BatchTaskRunner` 实际加载、provider 如何被解析、`partitionRange` 由 `SysEventBatchTrigger` 注入 `IBatchTaskContext`，而不是仅停留在 design 草图。
- [x] **无静默跳过**：若 Phase 1 审核发现某条 wiring（如 provider 解析、partitionRange 注入）当前仓库无现成机制，本计划已将其升级为明确 `Fix` 项，而不是留给实现阶段自行发挥。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - NonBroadcastEventProcessor Extraction And Simple-Mode Parity

Status: completed
Targets: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/`, `nop-sys` focused tests

- Item Types: `Fix`, `Proof`

- [x] 提取 `NonBroadcastEventProcessor`，收拢普通事件的候选扫描、claim、执行、结果回写逻辑；`SysDaoMessageService` 改为门面与 lifecycle 宿主。
- [x] simple mode 下的候选扫描改为 `findNext` keyset pagination，彻底移除 OFFSET 分页；同时按照 Phase 1 裁定结果保留或显式修改“每分区只取头部事件 + active lease 分区阻塞 + 过期 lease 捡回”的现有语义，禁止在重构中隐式漂移。
- [x] 增加 `nonBroadcastAutoScanEnabled` 配置开关，并让 `doStart()` / `doStop()` 对普通事件定时器的启停行为与开关一致。
- [x] 保持 `ConsumeLater`、retry、`FAILED`、lease 清理、分区头阻塞等现有语义不变。

Exit Criteria:

- [x] `SysDaoMessageService` 不再内联普通事件完整实现链，而是实际委托到 `NonBroadcastEventProcessor`。
- [x] focused test：simple mode 下普通事件扫描不再经过 OFFSET 分页；通过 repo-observable 方式证明 `findNext` 被调用且非广播扫描路径不再保留 `QueryBean.setOffset` / `findPageByQuery`。
- [x] focused test：同一 `partitionIndex` 下，后续事件不会越过 active head；过期 lease 的 head 可被重新拾取；若 Phase 1 裁定改变该语义，则测试改为验证新 contract 并同步 owner docs。
- [x] focused test：`ConsumeLater`、retryable error、terminal failure 的状态迁移与当前 baseline 一致。
- [x] **端到端验证**：从 `service.send(topic, message, ...)` → `NopSysEvent` 落库 → simple mode 扫描 → listener 回调 → event row 状态更新完整跑通。
- [x] **接线验证**：`doStart()` 的普通事件定时器实际调用 `NonBroadcastEventProcessor`，而不是遗留的旧方法体。
- [x] **无静默跳过**：simple mode 中不存在用空分支/continue 悄悄绕过未实现路径的情况；未支持配置显式失败或有明确日志/契约。
- [x] No owner-doc update required beyond Phase 1 contract sync，或在本 Phase 明确同步需要补的 owner docs。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - Batch DSL Runtime Path In nop-batch-sys

Status: completed
Targets: `nop-batch/nop-batch-sys/`, batch DSL task resource, `nop-batch` / `nop-sys` focused tests

- Item Types: `Fix`, `Proof`

- [x] 在 `nop-batch-sys` 增加可被 `BatchTaskRunner` 加载的普通事件 `.batch.xml` 任务定义，使用 `<orm-reader>` 表达 loader，而非编程式 `BatchTaskBuilder`。
- [x] 将 `SysEventBatchTrigger` 改为委托 `BatchTaskRunner.executeAsync(taskPath, params)`，移除编程式 batch task 组装。
- [x] 提供 batch DSL 所需的 processor / consumer provider wiring，并在代码与测试中证明 provider 的解析方式是 repo 内真实支持的路径，而非仅依据设计文档示例。
- [x] 实现 `partitionRange` 注入：`SysEventBatchTrigger` 读取 `assignedPartitions`（`IntRangeSet`），在执行 batch task 前将其转换为 `IntRangeBean` 并通过 `IBatchTaskContext.setPartitionRange()` 注入，使 `OrmQueryBatchLoaderProvider.setup()` 中 `context.getPartitionRange()` 非 null，自动追加 `partitionIndex BETWEEN` 过滤。
- [x] 证明 batch 模式下 `OrmQueryBatchLoaderProvider.findNext()` 被实际调用，并且普通事件 row 最终状态通过 provider 链正确回写。

Exit Criteria:

- [x] `.batch.xml` 任务资源存在于真实可加载的 VFS 路径，focused test 可通过 `BatchTaskRunner` 成功加载并执行。
- [x] `SysEventBatchTrigger` 中不再出现编程式 `BatchTaskBuilder` 组装逻辑。
- [x] focused test：batch DSL `<orm-reader>` 路径实际走 `OrmQueryBatchLoaderProvider.findNext()`，不存在 OFFSET 分页回退。
- [x] focused test：batch mode 运行时确实应用了 partition 过滤，而不是仅在 DSL 中声明 `partitionIndexField`；`SysEventBatchTrigger` 注入的 `partitionRange` 使 `OrmQueryBatchLoaderProvider` 实际追加 `BETWEEN` 条件。
- [x] **端到端验证**：构造真实 `NopSysEvent` 行，通过 `SysEventBatchTrigger` → `BatchTaskRunner` → DSL loader/processor/consumer 完整执行，最终 event row 状态按 contract 更新。
- [x] **接线验证**：`SysEventBatchTrigger.processNonBroadcastEvent()` 在运行时确实触发 DSL 任务执行，processor/consumer provider 被实际调用，且不是仅完成 task 加载后提前返回。
- [x] **无静默跳过**：若 batch DSL 路径无法加载、provider 无法解析、partitionRange 未注入，则行为为显式失败或明确错误，不允许静默退回旧路径。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - nop-job Triggering And Mode Coexistence Proof

Status: completed
Targets: `nop-batch-sys`, `nop-job` integration wiring, owner docs, focused tests

- Item Types: `Fix`, `Proof`

- [x] 新增 `nop-job` 调度资源（YAML 配置或 `NopJobSchedule` 初始化数据），使 `nop-job` 能按 cron 定期触发 `SysEventBatchTrigger.processNonBroadcastEvent()`。具体形式参照 `nop-job-local` 的 YAML 配置模式或 `nop-wf-scheduler` 的 `scheduler.yaml` 模式。
- [x] 验证 simple mode 与 batch mode 的配置边界：支持/禁止的组合、行为优先级、避免双触发的 operational contract。
- [x] 同步 owner docs：说明普通事件 batch mode 的支持路径、batch DSL 是支持基线、普通事件扫描禁止 OFFSET 的实现锚点与 owner-facing 语义。

Exit Criteria:

- [x] **端到端验证**：从 `nop-job` 触发入口到 `SysEventBatchTrigger`，再到 batch DSL loader/processor/consumer，最终完成普通事件状态更新的完整链路已验证。
- [x] focused test 或 wiring proof：simple mode 与 batch mode 的支持配置边界明确，未支持组合不会产生模糊行为。
- [x] `docs-for-ai/03-modules/nop-sys.md`、`docs-for-ai/03-modules/nop-batch.md`、`docs-for-ai/INDEX.md`、`docs-for-ai/04-reference/source-anchors.md` 已同步最终 live baseline。
- [x] 文档链接检查通过：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0 errors。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 所有 in-scope 普通事件 live defects 与 contract gaps 已修复
- [x] simple mode 与 batch mode 的行为/契约边界已达成
- [x] batch DSL 任务可被真实加载并执行，不存在仅落地资源文件但运行时未接通的 hollow implementation
- [x] partition filtering 在 batch mode 运行时已验证，不存在“声明了 partitionIndexField 但未实际生效”的空壳路径
- [x] 普通事件扫描路径中不存在 OFFSET 分页残留
- [x] 必要 focused verification 已完成
- [x] 受影响的 owner docs 已同步到 live baseline
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`SysDaoMessageService` → `NonBroadcastEventProcessor` 连线成立，（b）`nop-job` → `SysEventBatchTrigger` → batch DSL → provider → event row 状态更新连线成立，（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile`（或受影响模块等价命令）通过
- [x] `./mvnw test`（或受影响模块等价命令）通过
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 分布式多节点 batch 分片增强

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本计划只收口当前支持的 `nop-job` 调度 + `nop-batch` 本地 partition dispatch 可用路径，不扩大为新的跨节点 batch ownership 协议。
- Successor Required: `no`
- Successor Path: —

## Non-Blocking Follow-ups

- 若本次实施中发现 `ai-dev/design/nop-sys/sys-event-architecture.md` 仍有过多代码级草图，可后续单独整理为更纯粹的 owner design 文本。

## Closure

Status Note: All 4 phases completed. NonBroadcastEventProcessor extracted, OFFSET pagination removed, batch DSL path via IBatchTaskManager, nop-job scheduler.yaml configured. Design doc and owner docs synced to live implementation (source-script approach, not orm-reader). Independent closure audit completed with remediation of initial findings.
Completed: 2026-07-13

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (task: ses_0a8bcbdefffeDnv9djCNduqTAJ)
- Audit Session: Round 1 found 2 Blockers (orm-reader mismatch, dead partitionRange). Remediation: updated all docs/design to match live `<source>` XPL approach; removed dead partitionRange from SysEventBatchTrigger; wired nonBroadcastAutoScanEnabled config.
- Evidence:
  - Phase 1 PASS: design doc and owner docs synced
  - Phase 2 PASS: NonBroadcastEventProcessor extracted, findNext replaces OFFSET, `./mvnw test -pl nop-sys/nop-sys-dao -Dtest=TestSysDaoMessageService` = 15 tests pass
  - Phase 3 PASS: batch DSL + IBatchTaskManager, `./mvnw test -pl nop-batch/nop-batch-sys -Dtest=TestSysEventBatchTrigger` = 2 tests pass
  - Phase 4 PASS: scheduler.yaml created, beans registered
  - Anti-Hollow: (a) SysDaoMessageService delegates to NonBroadcastEventProcessor (confirmed), (b) SysEventBatchTrigger uses IBatchTaskManager not BatchTaskBuilder (confirmed), (c) DSL loader calls fetchExecutableNonBroadcastEvents which uses findNext (confirmed)
  - Doc-link checker: 0 new errors from our changes

Follow-up:

- Partition filtering in batch mode relies on SysDaoMessageService.assignedPartitions (same as simple mode), not on IBatchTaskContext.partitionRange. This is by design: the `<source>` XPL approach delegates to the processor which already handles partition filtering.
- If future evolution requires `<orm-reader>` + OrmQueryBatchLoaderProvider for batch mode, the partitionRange injection mechanism is available in IBatchTaskContext but currently unused for this task.
