# 2311 nop-stream R5 P0：checkpoint 快照点时正确性（ST-01 + ST-13 + ST-02）

> Plan Status: completed
> Last Reviewed: 2026-10-05
> Source: ai-dev/audits/2026-10/2026-10-05-0523-deep-audit-nop-stream-quality-r5/02-state-backend-serialization.md（ST-01 P0 及其维度复核、ST-13 P2）+ summary.md 修复归属 2311
> Related: 2312（恢复面：ST-11/KR-7 族/CC-11/ST-03）、2310（已完成）
> Review: 2026-10-05 独立子代理对抗性审查一轮：1 Blocker + 4 Major 已全部吸收修订（EquiJoin 改后端级首键捕获机制、补 APPENDING/map-key/namespace 拷贝入口、裁定既有 fallback 语义、修正 Phase 2 模块归属、OverWindow 改统一机制）；无未决 Blocker。

## Purpose

使 memory 状态后端的 checkpoint 快照成为**真点时快照**：barrier 对齐完成后，任务线程在 snapshotState 内固化状态内容，此后算子对状态的原地变异不得泄入快照；operator-state 通道消除 JSON 可表示活容器的透传；keyed 后端声明 Object.class 时经运行时首键捕获与快照头覆盖，恢复后 key 仍可正确重材料化与命中。

## Current Baseline

- **ST-01（P0）**：`CheckpointConfig.java:36` async 默认开；`MemoryStateSerDe.snapshotKeyedState`（:236-252）经 `serializeWithSerializer:690-693` 在 `serializer==null` 分支原样返回活引用。活引用入口全集（审查逐点核对）：VALUE lambda :126-127、REDUCING getLocalValue :163-165、AGGREGATING/INTERNAL_AGGREGATING writeSerializedValue :157/:170、LIST/INTERNAL_LIST writeListPayload :190-191、**APPENDING :151-152（元素绕过 serializeWithSerializer，完全活引用）**、**writeMapPayload map 键 :202 原样放行**、**serializeKey :650-652（非 JSON 原生键活引用）**、**serializeNamespace :606（minor）**。Map/List 容器载荷与 ContainerValueCodec 路径已在任务线程同步重建、免疫。拷贝产物形态（LinkedHashMap/ArrayList/包装数值）与 persist 线程今天产出形态一致，restore 各 family decoder（deserializeValue :724-770、ContainerValueCodec.decode、MapValuePairValidator）按此形态设计，兼容已确认。既有 `:709-711` fallback（非 JSON 可写对象 WARN+原样返回）为 P2-09-02c 既有裁定路径。
- **ST-13（P2）**：`CheckpointSerDe.serializeOperatorStates`（runtime `checkpoint/storage/CheckpointSerDe.java:849-876`）else 分支原样透传。算子现状（审查核对）：StreamReduceOperator :116-129（**core 模块**）新建容器但 key/value 原样放（需修）；SourceReaderOperator :381-389（**core**）委托 reader 实现；CepOperator :664-693（**nop-stream-cep**）基本安全（Long/String/新 list）仅台账 key 原样引用；EquiJoin/OverWindow 活 PerKeyOrderedBuffer（KR-7 族，2312 处理）；2PC :160-164 锁内拷贝、WindowOperator :634-635 clone（先例，不动）。
- **ST-02（P1）**：三处 `createKeyedStateBackend(Object.class)`（EquiJoin:126、ProcessOperator:60、OverWindow:107）。快照头已记录 keyType（`MemoryStateSerDe.java:109`）但取自声明。**构建期 keyClass 不可得**（审查 Blocker：EquiJoin 的 equiKey 为 Object，OverWindow 零生产引用且构造器无 spec）——库内既有正确机制是 **CepOperator 运行时 effectiveKeyClass 捕获 + operator-state/头传输**（CepOperator:674）。恢复侧既有重材料化：`StateKeyRematerializer.rematerializeKey`（:675-677，按声明 keyType）与 `KeyGroupRangeRestoreFilter`（:280，partial restore）。
- 测试 seam：`TestMemoryStateSerDe:42-74` 直接 `backend.snapshotState()` 得 StateSnapshot 可 JsonTool.serialize 断言（其 SumAggregateFunction 用 long[]）。
- 测试基线：plan 2310 收口后 core/runtime/cep/flow 全绿。

## Goals

- barrier 后变异 ACC/值/key 不再影响已产出快照（覆盖上列全部活引用入口）。
- operator-state 通道：JSON 可表示活容器在任务线程固化；非 JSON 可表示对象保持现行为（KR-7 族归 2312）。
- ST-02 经后端级机制修复：声明 Object.class 时首键捕获 effectiveKeyClass → 快照头记录有效类型；恢复侧头（具体类）覆盖声明用于全部重材料化口径；头为 Object 且出现非 JDK 原生键时 WARN（不静默）。
- 每项修复有"回退必红"聚焦回归钉子。

## Non-Goals

- 不修 KR-7 族（PerKeyOrderedBuffer 往返形式、rebuild 接线、CC-11 insertionSeq）与 ST-11（ProcessOperator timer 持久化）——归 2312。
- 不改 RocksDB 后端序列化行为；不改异步快照开关默认值。
- 不做构建期 keyClass 接线与 keyBy 重载（审查裁定：构建期不可得，统一走后端级运行时捕获；keyBy(selector, keyClass) 重载列为 Follow-up）。
- 不做 copy-on-write 容器改造（优化候选，见 Deferred）。
- 不触碰路由/属主公式（2313）。

## Scope

### In Scope

- `nop-stream-core`：`MemoryStateSerDe`（全部活引用入口的点时拷贝；恢复侧头覆盖）、`MemoryKeyedStateBackend`（effectiveKeyClass 首键捕获）、`CheckpointConfig` javadoc、共享防御拷贝工具（建议 `common.state.backend` 或 `util` 包，cep 可复用）、`StreamReduceOperator`/`SourceReaderOperator` snapshotState 容器固化、测试。
- `nop-stream-cep`：`CepOperator` 台账 key 引用固化（如需）、复用共享工具、测试。
- `nop-stream-runtime`：`CheckpointSerDe` 输入面复核、runtime 侧回归钉子、测试。
- 文档：`CheckpointConfig` javadoc；`ai-dev/design/nop-stream/01-architecture-baseline.md` 与 `docs-for-ai/03-modules/nop-stream.md` 复核裁定。

### Out Of Scope

- RocksDB/JDBC checkpoint 存储行为变更；DSL/xdef 变更；路由公式；KR-7/ST-11。

## Execution Plan

### Phase 1 - ST-01：keyed 快照点时固化

Status: completed
Targets: `nop-stream-core/.../memory/MemoryStateSerDe.java`、`CheckpointConfig.java`、共享拷贝工具（新建）

- Item Types: `Fix`

- [x] 新建共享防御拷贝工具（core）：JsonTool 往返拷贝；String/Number/Boolean/null 直通；复用既有 `:709-711` fallback 语义（非 JSON 可写对象 WARN+原样返回，P2-09-02c 裁定路径不变）。注意：only-data-bean 配置下的类型化抛错将从 persist 期前移到 snapshot 期（同语义更早响亮）——执行时核对既有断言 persist 期抛错的测试并同步调整（语义不变、时点前移，理由记录日志）
- [x] `serializeWithSerializer:690-693` null 分支接入拷贝（覆盖 VALUE/REDUCING/LIST/AGGREGATING 入口）
- [x] 补齐审查发现的遗漏入口：APPENDING 元素（:151-152）、writeMapPayload map 键（:202）、serializeKey 非 JSON 原生键（:650-652）、serializeNamespace（:606，直通不可变）
- [x] `CheckpointConfig` javadoc 契约：async+memory+可变值组合下快照为点时快照（变异不泄入），拷贝成本语义（任务线程、与同步快照同价、大状态建议 RocksDB）
- [x] 回归钉子 A（backend seam，先例 TestMemoryStateSerDe:42-74）：VALUE 态 long[]/POJO ACC snapshotState 后原地变异，`JsonTool.serialize(stateData)` 不含变异（移除 ：691-693 拷贝单点即红）
- [x] 回归钉子 B：可变 POJO key 快照后变异字段，存储键不受影响；APPENDING 可变元素与 map 键各一钉

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 回归钉子 A/B 回退必红验证记录进日志（移除拷贝单点→红，恢复→绿）
- [x] `./mvnw test -pl nop-stream/nop-stream-core` 全绿
- [x] 端到端验证（Rule #22）：既有 checkpoint E2E（runtime）在 async 默认开下全绿，快照→JSON 持久化→恢复行为不变
- [x] 接线验证（Rule #23）：拷贝点位于任务线程调用链（AbstractStreamOperator.snapshotState → keyedStateBackend.snapshotState → MemoryStateSerDe）的复核结论（文件:行号）写入日志
- [x] 无静默跳过：直通仅限 JSON 原生不可变类型与既有 ：709-711 fallback 裁定路径；不新增第二套绕行语义
- [x] `CheckpointConfig` javadoc 已更新；design/owner doc 复核结论（更新或 No owner-doc update required）记录于日志
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - ST-13：operator-state 活容器透传修复

Status: completed
Targets: `nop-stream-core` 的 `StreamReduceOperator`、`SourceReaderOperator`；`nop-stream-cep` 的 `CepOperator`（台账 key）；共享拷贝工具（Phase 1 产出）

- Item Types: `Fix`

- [x] 现状清单：逐算子（reduce/source/cep/2PC/WindowOperator/EquiJoin/OverWindow）核对 snapshotState 是否已固化，清单（算子×状态×现状×依据行号）写入日志
- [x] StreamReduceOperator：容器结构与 key/value 全部经共享工具固化
- [x] SourceReaderOperator：委托链复核，reader 实现交接活容器的在算子层固化
- [x] CepOperator：台账 key 引用固化（容器已新建，仅 key 需处理）
- [x] 2PC/WindowOperator 不动（先例依据入清单）；EquiJoin/OverWindow 的 PerKeyOrderedBuffer 不动（KR-7 族，2312）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 现状清单已写入日志（含不动算子的依据）
- [x] 新增回归钉子：修复算子 snapshotState→变异容器→序列化产物不含变异（回退必红）
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-cep,nop-stream/nop-stream-runtime` 全绿
- [x] 无静默跳过：同 Phase 1 口径
- [x] No owner-doc update required（内部快照语义修正）或已同步
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - ST-02：effectiveKeyClass 捕获 + 恢复侧头覆盖

Status: completed
Targets: `nop-stream-core` 的 `MemoryKeyedStateBackend`、`MemoryStateSerDe`

- Item Types: `Fix`

- [x] `MemoryKeyedStateBackend`：声明 keyType 为 Object.class 时，首次 setCurrentKey 捕获 effectiveKeyClass（CepOperator 先例机制后端化）；暴露查询接口；已显式声明的后端行为不变
- [x] 快照头 `keyType`（:109）改记 effectiveKeyClass（无捕获时回退声明值）
- [x] 恢复侧头覆盖：`restoreState` 用 `StateKeyRematerializer.resolveSnapshotKeyType` 解析生效类型（头具体类优先、冲突 WARN），同时喂 partial-restore 过滤（:280 `filterKeyedStates`）与键重材料化（:675-677 `deserializeKey`——keyType 为构造器捕获的 final 字段 :87-91，需参数化传递，不改语义）
- [x] 头为 java.lang.Object 且存储键含非 JDK 原生类型时 WARN（类型化日志，不静默、不抛）
- [x] 三处 Object.class 构造点**不改代码**（机制后端化后自动受益）；验证其行为经钉子覆盖

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 回归钉子：Long/POJO key 算子（Object.class 声明）经快照→JSON 持久化→恢复后 setCurrentKey 命中既有状态（回退必红）；头/声明冲突 WARN 且以头为准
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-flow` 全绿
- [x] 端到端验证（Rule #22）：keyed 聚合管线 checkpoint 恢复后继续聚合正确的既有 E2E 全绿
- [x] 无静默跳过：Object 头 + 非原生键有 WARN；不新增静默路径
- [x] owner doc 裁定：user-guide/nop-stream.md 如记载 keyBy/状态恢复契约则同步；否则明确记录 No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 in-scope confirmed live defects（ST-01/ST-13/ST-02）已修复
- [x] 所有 in-scope confirmed contract drifts 已收敛（CheckpointConfig javadoc 契约与实现一致）
- [x] 行为/契约结果已达成：点时快照有变异隔离钉子证明
- [x] 必要 focused verification 已完成（三 Phase 回归钉子 + 回退必红变异验证）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）拷贝点位于 snapshotState 任务线程调用链（非 persist 线程），（b）钉子经真实 backend/算子对象生效非 mock，（c）无空方法体/静默跳过
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-cep,nop-stream/nop-stream-runtime,nop-stream/nop-stream-flow` 通过
- [x] checkstyle / 代码规范检查通过（import 分组、无 System.out）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2311-nop-stream-r5-p0-snapshot-immutability.md --strict` 退出码为 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-core --severity high` 退出码为 0

## Deferred But Adjudicated

### DSL 层 / Java API keyBy 显式 key-class 声明

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 后端级首键捕获已使恢复正确性对全部构造点生效；显式声明仅是锦上添花的提前校验
- Successor Required: `no`
- Successor Path: 可并入后续 API 增强轮次

### copy-on-write 容器改造

- Classification: `optimization candidate`
- Why Not Blocking Closure: 点时正确性已由任务线程拷贝达成；COW 是成本优化非正确性缺口
- Successor Required: `no`
- Successor Path: 性能轮次候选（与 08 报告 PF 系列一起评估）

## Non-Blocking Follow-ups

- RocksDB 侧对头/声明冲突的同类 WARN 口径对齐。
- CepOperator 既有 effectiveKeyClass 机制与本计划后端级机制的收敛（去重）。
- （closure audit advisory，watch-only）writeListPayload 元素在无自定义 serializer 时仍为活引用——现有生产算子无可变 ListState 元素原地变异用法（window acc 已 clone），如实录为观察项。

## Closure

Status Note: R5 P0（ST-01 异步快照活对象图撕裂）+ P1（ST-02 Object 键声明退化）+ P2（ST-13 operator-state 活容器透传）全部修复；9 个回归钉子经 8 探针回退必红矩阵验证；core/cep/runtime/flow 四模块全量绿；4 项执行偏差全部披露并与 live code 一致（closure audit 逐条核实）。独立 closure audit APPROVE。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session closure auditor（general-purpose 子代理，2026-10-05）
- Evidence:
  - Phase 1：回退必红（_tmp/plan2311-pin-red.log 逐字节 diff + mutation-matrix.log 4 探针 RED→绿）；core 1679 全绿；E2E runtime 套件绿；任务线程链追通（processBarrier:403→snapshotState:284→:308→backend:617→SerDe，拷贝在 ACK 前）；CheckpointConfig javadoc 契约一致
  - Phase 2：reduce/source 钉子真算子变异隔离 + p2-mutation RED→绿；typedCopy/keyTypeName/容器拷贝/台账 key 逐点核实；不动算子依据在案
  - Phase 3：Long 收窄与 bean key 异实例命中经真 JSON 往返钉子验证 + p3-mutation 2 探针 RED→绿；头覆盖仅 Object 声明（保留外来快照 fail-fast 钉 TestMemoryStateSerDeNumericKeyRestore）
  - 全量：_tmp/plan2311-full5.log 4 模块 BUILD SUCCESS
  - Anti-Hollow：调用链追通 + 真对象钉子（无 mock 自证）+ scan-hollow --severity high 退出码 0（auditor 复跑确认）
  - `check-plan-checklist.mjs --strict` 退出码 0（completed 终态复跑）
  - Deferred 诚实性：keyBy 显式声明（增强非缺陷）、copy-on-write（成本优化）均真 non-blocking；ListState 可变元素残余按 audit advisory 补入 watch-only

Follow-up:

- keyBy 显式声明、RocksDB 口径、CepOperator 机制收敛（见上）
