# Contract Drift Reconciliation, Config Convention & Test Integrity

> Plan Status: completed
> Last Reviewed: 2026-08-07
> Draft Review: independent sub-agent review passed (no Blockers; 2 Majors test-mock/container-strategy + Minors addressed; verdict YES). Session ses_036114ff9ffeJB69tHEx8arZB2.
> Source: `ai-dev/audits/nop-stream-production/2026-08-02-2107-multi-audit-nop-stream-production.md` (P0 TestTaskManagerDaemon vacuous; P1 IOperatorStateStore/KeyedStateStore/StateDescriptor SPI drift; P1 Operator State doc drift; P1 missing `_module` marker; P1 TestTaskExecutorDaemonThreads / TestSinkTransformation hollow tests)
> Related: Execution order `{3}` of 3 — 最低风险，可与 {1}/{2} 并行推进；不阻塞其他 plan。

## Purpose

收口 nop-stream 的文档/契约诚实性、平台约定符合度与测试完整性：四处 SPI/文档 drift（design 文档与生产代码矛盾）、缺失 `_module` 平台 IoC 发现标记（静默 bean-wiring gap）、三个空心测试（零 bug-catching power / 虚假覆盖）。这些缺陷共同使项目的契约（文档、SPI、约定、测试）与生产实际脱节。本 plan 统一裁定每个 drift 的收敛方向（文档对齐代码 或 代码对齐文档），补齐平台约定，恢复测试的真实保护力。

## Current Baseline

经 2026-08-04 live repo 核对（引用与 multi-audit 一致，已二次确认）：

**SPI / 文档 drift（四处，design 文档 marked active 与生产代码矛盾）：**
- **Operator State "尚未实现" drift**：`state-management-design.md:407`（§10.4）、`:419`（§11.9）、`core-design.md:419`（§7.3）均称 "Operator State 尚未实现 / Phase 0.3"。实际：`MemoryOperatorStateBackend.java:25 implements IOperatorStateBackend`（snapshotState + 4-way restoreState redistribution）、`DefaultOperatorStateStore.java:15 implements IOperatorStateStore`、`TestE2EOperatorStateCheckpoint.java`/`TestE2EOperatorStateRedistribution.java` 存在。`00-vision.md:99` 自身已矛盾（"已落地 operator state 重分布"）。
- **IOperatorStateStore 1-vs-3 方法 drift**：`IOperatorStateStore.java:12` 仅 `<T> ListState<T> getListState(...)`。design §10.1（`:373`）规定 3 方法（含 `getUnionListState`/`getBroadcastState`）。实际 mode 由 backend 在 restore 时外部设定，用户无注册时选择。
- **KeyedStateStore 5-vs-2 方法 drift**：`KeyedStateStore.java:66-109` 暴露 5 accessor（Value/List/Reducing/Aggregating/Map）。design §5.1 称"只暴露 getState/getMapState"、§2.1 称"ListState 不通过 KeyedStateStore 暴露"。两后端（`MemoryKeyedStateBackend`、`RocksDBKeyedStateBackend`）均实现全 5。
- **StateDescriptor 携带 TypeSerializer drift**：`StateDescriptor.java:22`（`private TypeSerializer<T> serializer`）、`:62`（`getSerializer()`）、`:66`（`setSerializer()`）。design §6.1 不变量 #2 明确"StateDescriptor 不携带 serializer 引用；IStreamSerializer 接口不向上暴露"。`MemoryStateSerDe` 生产路径按 `instanceof IStreamSerializer` 分支调用 serialize/deserialize。

**配置约定缺口：**
- **`_module` 标记缺失**：`nop-stream-runtime/src/main/resources/_vfs/nop/stream/` 仅有 `beans/` 子目录，无 `_module` 文件。两个 beans.xml 声明 `ioc:default="true"` bean（`streamMessageService`→`LocalMessageService`、`streamDataPlaneWireCodec`→`IdentityWireCodec`）。平台约定要求 `_vfs/<root>/<module>/_module` 才能全局 IoC 发现；`nop-auth`/`nop-file`/`nop-demo` 均有此标记。当前 `TestStreamControlRpcBootstrap` 经 `BeanContainerBuilder.addResource(...)` 显式加载，绕过发现。**注**：`TestStreamControlRpcBootstrap.java:47-52` 注释说明其刻意停在 `INITIALIZER_PRIORITY_IOC - 1` 以避免 nop-dao datasource 初始化；故 Phase 2 的发现测试须用 **scoped `BeanContainerBuilder`** 从 `_vfs/<root>/` 遍历（经 `_module` 自动发现），而非 `CoreInitialization.initializeTo(INITIALIZER_PRIORITY_IOC)`，以免触发 datasource 配置依赖。

**空心测试（三处，零 bug-catching power）：**
- **TestTaskManagerDaemon [P0]**：`nop-stream-runtime/.../TestTaskManagerDaemon.java:11-39`。声称测 daemon-thread 不变量，但从不调用 `tm.start()` 也不提交任务，`tm-task-*` 线程零存在；断言在 `if (name.startsWith("tm-task-"))` 分支内（永不执行）。`foundDaemonTaskThread` 置位但从不断言。移除 daemon 标记的回归无法被捕获。**修复约束**：`TaskManager.start()`（`:148`）内部调 `clusterRegistry.registerNode(...)`（`:154`），现有测试构造时传 `null` clusterRegistry/messageService，故 `start()` 会 NPE；`tm-heartbeat-*` 线程由 `start()` 的 `heartbeatExecutor.scheduleAtFixedRate`（`:157`）创建，但 `tm-task-*` 线程由 `newFixedThreadPool`（`:127`）**懒创建**于首次 `submit`。故修复须提供非 null 的最小 mock（`IClusterRegistry`/`IMessageService`）才能 `start()`，且 `tm-task-*` 须 `start()` 后再 `submit` 一个 dummy task 才生成。
- **TestTaskExecutorDaemonThreads [P1]**：`nop-stream-core/.../TestTaskExecutorDaemonThreads.java:13-37`。测试体内自定义 `ThreadFactory`（`t.setDaemon(true)`），完全脱离生产类。验证的是 Java 语言恒真式，非生产行为。
- **TestSinkTransformation [P1]**：`nop-stream-core/.../TestSinkTransformation.java:26-334`（17 个 `@Test`，397 行）。全部构造 data-holder 类后调 getter 断言构造参数，无业务逻辑可测（`TestOneInputTransformation.java` 同型 18 方法/439 行）。

## Goals

- 四处 SPI/文档 drift 每处裁定收敛方向（文档对齐代码 或 代码对齐文档）并落地，使 design 文档与生产代码不再矛盾。
- 补齐 `_vfs/nop/stream/_module` 标记，并验证全局 IoC 容器能发现 `ioc:default` bean。
- 三个空心测试：恢复真实保护力（指向生产类）或删除/打标，消除虚假覆盖。

## Non-Goals

- 控制面/运行时恢复竞态（Plan {1}）。
- 检查点/状态后端/CEP 状态正确性 live defect（Plan {2}）。
- 全部 P2 项（error-handling 二层违规、javadoc rot、低价值测试尾部 — 归 backlog）。
- 不在 drift 收敛中改变已落地生产行为（除非裁定代码对齐文档，须显式记录风险）。

## Scope

### In Scope

- `ai-dev/design/nop-stream/state-management-design.md`（§2.1、§5.1、§6.1、§10.1、§10.4、§11.9）、`ai-dev/design/nop-stream/core-design.md`（§7.3）：drift 收敛。
- `IOperatorStateStore.java`/`KeyedStateStore.java`/`StateDescriptor.java`：仅在裁定为"代码对齐文档"时修改（须显式风险记录）。
- `nop-stream-runtime/src/main/resources/_vfs/nop/stream/_module`：新增空标记文件 + 全局容器发现测试。
- `TestTaskManagerDaemon.java`/`TestTaskExecutorDaemonThreads.java`/`TestSinkTransformation.java`（及同型 `TestOneInputTransformation.java`）：修复指向生产类 或 删除/打标。

### Out Of Scope

- P2 javavadoc rot（`IStateBackend` Redis 引用、README 五层 vs 六阶段 — 归 backlog，部分与 doc drift 相邻但不阻塞）。
- P2 二层错误处理违规（`InputGate.blockConsumption`、`StreamControlRpcServer`、`LocalSourceCoordinator` 等 — 归 backlog）。
- P2 低价值测试尾部（`TestCountTrigger`、`TestMapStateDescriptor` 等 — 归 backlog）。

## Execution Plan

### Phase 1 - SPI / 文档 drift 收敛

Status: completed
Targets: `ai-dev/design/nop-stream/state-management-design.md`, `ai-dev/design/nop-stream/core-design.md`, `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/IOperatorStateStore.java`, `.../KeyedStateStore.java`, `.../StateDescriptor.java`

- Item Types: `Decision | Fix`

- [x] **裁定 Operator State "尚未实现" drift**（裁定：文档对齐代码）：更新 `state-management-design.md` §10.4/§11.9 与 `core-design.md` §7.3/§7.4 反映 live 状态（`IOperatorStateBackend` 4 方法含 4-mode redistribution + `IOperatorStateStore.getListState` + `DefaultOperatorStateStore` + `MemoryOperatorStateBackend`，以 E2E 测试 `TestE2EOperatorStateCheckpoint`/`TestE2EOperatorStateRedistribution` 为证）；`comparison.md` 两处 Operator State 行同步收敛；移除 "Phase 0.3" backlog 引用
- [x] **裁定 IOperatorStateStore 1-vs-3 方法 drift**（裁定：文档对齐代码）：更新 design §10.1/§10.2 描述实际 SPI（`IOperatorStateStore` 仅 `getListState`；4 种 `RedistributionMode` 在 restore 时由 backend 外部设定，用户侧 store 无注册时选择）
- [x] **裁定 KeyedStateStore 5-vs-2 方法 drift**（裁定：文档对齐代码，因广泛生产使用）：更新 design §2.1/§5.1 反映实际 5 accessor 面貌（getState/getListState/getReducingState/getAggregatingState/getMapState）
- [x] **裁定 StateDescriptor 携带 TypeSerializer drift**（裁定：更新文档记录可选 escape hatch）：更新 §6.1 文档化 `StateDescriptor` 持有 `TypeSerializer<T>` 字段（默认 `JsonToolSerializer`）+ `IStreamSerializer` SPI 作为显式 opt-in（`MemoryStateSerDe` 按 `instanceof IStreamSerializer` 分支调用）；§2.2 是正确锚点

Exit Criteria:

- [x] 四处 drift 每处在仓库中可观察到收敛落地（文档修订），且裁定方向有显式记录（plan Decision 标注 + design doc 备注）
- [x] 收敛后 design 文档与生产代码不再矛盾（抽查：design §10.1 `IOperatorStateStore` 方法集合 == 生产接口 `getListState` 单方法；§2.1/§5.1 KeyedStateStore 5 accessor == 生产接口 5 方法；§6.1 serializer 字段 == `StateDescriptor.serializer`）
- [x] 纯文档收敛：`No new test required: <doc reconciliation, 4 drifts all converged docs→code>`
- [x] `ai-dev/design/nop-stream/` 下文档为最终状态（无 "Proposed vs Current" 残留，符合 plan guide #14）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - `_module` IoC 发现标记补齐

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/resources/_vfs/nop/stream/_module`

- Item Types: `Fix | Proof`

- [x] 新增空文件 `_vfs/nop/stream/_module`（镜像 `nop-auth` 约定）
- [x] 新增测试 `TestStreamModuleDiscovery`：用 **scoped `BeanContainerBuilder`** 从 `_vfs/<root>/` 遍历（经 `_module` 自动发现 `_vfs/nop/stream/beans/*.beans.xml`，**不**经 `CoreInitialization.initializeTo(INITIALIZER_PRIORITY_IOC)` 以免触发 nop-dao datasource 配置依赖——见 `TestStreamControlRpcBootstrap.java:47-52` 刻意早停的原因），断言 `container.containsBean("streamMessageService")`（`IdentityWireCodec` 同理）

Exit Criteria:

- [x] `_vfs/nop/stream/_module` 文件存在于仓库
- [x] 新增发现测试：未显式 `addResource`（仅靠 `_module` 标记驱动 scoped 容器遍历）的情况下 `containsBean("streamMessageService")` 为 true
- [x] **接线验证**：`moduleMarkerEnablesDiscovery` 断言 `ModuleManager.getEnabledModuleMap` 含 `nop-stream`（`_module` 驱动 `discover()`），`scopedContainerMaterializesDefaultBeansViaModuleMarker` 经 `findModuleResourcesInModules` 遍历发现的 module 的 `beans/` 目录并断言 `streamMessageService`/`streamDataPlaneWireCodec` 物化——移除 `_module` 则 module 不可发现，测试 FAIL
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 空心测试恢复或删除

Status: completed
Targets: `TestTaskManagerDaemon.java`, `TestTaskExecutorDaemonThreads.java`, `TestSinkTransformation.java`, `TestOneInputTransformation.java`

- Item Types: `Fix`

- [x] **TestTaskManagerDaemon**：改为先提供非 null 最小 mock（`IClusterRegistry`/`IMessageService`）使 `tm.start()` 不 NPE，再 `submit` 至少一个 dummy task 使 `tm-task-*` 线程生成，扫描线程后 `assertTrue(foundDaemonTaskThread.get(), ...)`；`testHeartbeatThreadIsDaemon` 仅需 `start()` + 非 null `ClusterRegistry`（heartbeat 线程由 `start()` 直接创建）。若提供 mock 成本过高，**删除并记录原因**（P0 项优先修复，删除为兜底）
- [x] **TestTaskExecutorDaemonThreads**：改为实例化生产 `TaskManager`/`TaskExecutor` 并验证**其**线程为 daemon；或删除（零 bug-catching value）
- [x] **TestSinkTransformation**（及同型 `TestOneInputTransformation`）：删除 或 `@Tag("low-value")` 打标排除（项目已自标 18 个此类文件）

Exit Criteria:

- [x] TestTaskManagerDaemon 修复版在仓库中可观察到 `tm.start()` + 任务提交 + `foundDaemonTaskThread` 断言，且测试在 green 时确实能捕获"移除 daemon 标记"的回归（或已删除并记录原因）
- [x] TestTaskExecutorDaemonThreads 修复版指向生产 ThreadFactory（或已删除并记录原因）
- [x] TestSinkTransformation/TestOneInputTransformation 已删除或打标排除
- [x] **无静默跳过**：保留的测试有真实断言，删除的有记录原因
- [x] `No new test required: <test rewrite/deletion, no production behavior change>`（本 phase 不新增生产功能）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。
> **纯文档/测试计划**：本 plan 不涉及核心生产算法变更，但包含 SPI 可能的代码侧修改与 `_module` 配置。`./mvnw test` 仍须通过（测试改写 + 发现测试）。

- [x] 四处 SPI/文档 drift 均已裁定并落地收敛
- [x] `_module` 标记补齐且全局 IoC 发现测试通过
- [x] 三个空心测试已恢复真实保护力或删除/打标
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope drift/约定缺口
- [x] 受影响的 owner docs（design docs）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证修复后测试确有 bug-catching power（指向生产类），`_module` 发现路径运行时连通
- [x] `./mvnw compile -pl nop-stream -am -T 1C`
- [x] `./mvnw test -pl nop-stream -am -T 1C`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；执行中如出现延期项，须写明 Classification / Why Not Blocking Closure / Successor Required。）

## Non-Blocking Follow-ups

- 本 plan 的 P2 邻接项（`IStateBackend` javadoc Redis 引用、README 五层/六阶段、二层错误处理违规、低价值测试尾部）已归入 roadmap Follow-up Backlog，不阻塞 closure。

## Closure

Status Note: 三处空心测试已恢复真实保护力或删除/打标，`_module` IoC 发现标记补齐且发现测试通过，四处 SPI/文档 drift 已收敛（Phase 1 文档对齐代码）。本 plan 不涉及核心生产算法变更，仅文档/配置/测试收口。Phase 3 的代码工作在 commit `eeef4e13d` 中落地，本次执行完成 bookkeeping（勾选 + closure audit + roadmap 收口）。
Completed: 2026-08-07

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（fresh session，非实现 session 复用）
- Audit Session: ses_023b54eb7ffehrrD9PTvILHCRH
- Evidence:
  - **Phase 1 (SPI/doc drift)**：已完成（前序执行），四处 drift 均裁定为文档对齐代码，design 文档已修订。Exit Criteria 全 `[x]`。
  - **Phase 2 (`_module` 发现标记)**：已完成（前序执行）。`_vfs/nop/stream/_module` 文件存在；`TestStreamModuleDiscovery`（2 tests）通过 `_module` 驱动 scoped 容器遍历断言 `streamMessageService`/`streamDataPlaneWireCodec` 物化——`Tests run: 2, Failures: 0, Errors: 0`。
  - **Phase 3 (空心测试)** — 独立审计逐项核对 live code：
    - `TestTaskManagerDaemon`：PASS。重写版提供非 null `noopClusterRegistry()`/`noopMessageService()`，调 `tm.start()`（heartbeat 线程生成）+ `tm.receiveAssignment(...)`（`tm-task-*` 线程懒创建），真实断言 `assertTrue(t.isDaemon(), ...)`（TestTaskManagerDaemon.java:164/:193）。生产 `TaskManager.java:129/:134` 两处 `setDaemon(true)`——移除任一即测试 FAIL（regression power 成立）。`Tests run: 2, Failures: 0, Errors: 0`。
    - `TestTaskExecutorDaemonThreads`：PASS（已删除）。原版自定义 `ThreadFactory` 测 Java 语言恒真式，零生产耦合；在 commit `eeef4e13d` 删除，commit message 记录原因。
    - `TestSinkTransformation`/`TestOneInputTransformation`：PASS（打标）。类级 `@Tag("low-value")` + Javadoc 说明（仅构造 data-holder 后断言 getter，无业务逻辑可测）。项目共 20 文件用此约定。`Tests run: 17/18, Failures: 0, Errors: 0`。
  - **无静默跳过**：保留的测试有真实断言；删除的有记录原因。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（无未勾选项 + Closure Evidence 已写入）。
  - **Anti-Hollow 检查**：保留/重写的测试指向生产类（`TaskManager` 线程工厂、`SinkTransformation`/`OneInputTransformation`），`_module` 发现路径运行时连通（`TestStreamModuleDiscovery` 实证）。`scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 1，但 14 条 high findings 全在 `src/main` 且与本 plan 改动零重叠（git show eeef4e13d 实证）——均为既有的 rule-#24-compliant `UnsupportedOperationException` 守卫（抛清晰异常非静默跳过）、demo 模块守卫、与 comment-match 误报，属 roadmap Follow-up Backlog 的 P2 邻接项（本 plan Non-Goals 已声明 out-of-scope），不阻塞 closure。
  - `./mvnw compile -pl nop-stream -am -T 1C` BUILD SUCCESS；`./mvnw test -pl nop-stream -am -T 1C` BUILD SUCCESS（全部模块绿）。
  - Deferred 项分类检查：本 plan 无 deferred 项；P2 邻接项（IStateBackend javadoc、README 五层/六阶段、二层错误处理、低价值测试尾部）已在 Non-Blocking Follow-ups 声明，非 in-scope live defect。

Follow-up:

- no remaining plan-owned work（三处空心测试已收口，`_module` 已补齐，drift 已收敛）
- P2 邻接项（scan-hollow 14 条既存 src/main findings、IStateBackend javadoc、README、二层错误处理、低价值测试尾部）归 roadmap Follow-up Backlog，不阻塞 closure
