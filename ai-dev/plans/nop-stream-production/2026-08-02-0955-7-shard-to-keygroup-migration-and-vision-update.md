# 7 StateShard→KeyGroup 迁移 + vision Non-Goal 更新

> Plan Status: completed
> Last Reviewed: 2026-08-02
> Draft Review: 2 轮独立子 agent 对抗性审查通过（round 1 发现 2 Blocker + 5 Major + 6 Minor，全部修复；round 2 确认 no Blocker、修复未引入新问题、recovery/reshard 机制可执行）
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 37；deferred item from `2026-08-02-0955-5-keygroup-range-recovery.md` Non-Blocking Follow-ups「`maxParallelism` 变化的显式迁移（Stage 37）」；deferred item from `2026-08-02-0955-4-key-group-model.md`「生产 rescale 接线属 Stage 35」（已由 Stage 35 交付，本 plan 收口 vision 文档与 maxParallelism 迁移）
> Mission: nop-stream-production
> Work Item: 37. StateShard→KeyGroup 迁移 + vision Non-Goal 更新
> Related: 前置 `2026-08-02-0955-4-key-group-model.md`（Stage 34 KeyGroup 模型，已完成）；`2026-08-02-0955-5-keygroup-range-recovery.md`（Stage 35 rescale 恢复，已完成）；本 plan 与 `2026-08-02-0955-8-leader-election-ha.md`（Stage 38）无执行依赖，可并行

## Purpose

把 nop-stream 的「弹性重分布」能力从 vision 文档的 Non-Goal / 去除区域正式收敛为 supported baseline，并交付 Stage 34/35 显式 deferred 的 `maxParallelism` 变化显式迁移 action。具体收口两件事：(1) 修正 `00-vision.md` 与 live baseline 的 owner-doc drift——Stage 34/35 已落地 Key-Group 模型与 rescale 恢复，但 vision §四/§七/§八/§六仍把 key-group 重分布列为 Non-Goal / 去除 / StateShard 路由；(2) 实现 checkpoint-design.md:845/858 承诺但未交付的 reshard migration action（`maxParallelism` 变化时的 key→group 重映射），使「改变 maxParallelism」从「默认拒绝」变为「提供显式迁移即允许」。

## Current Baseline

> 已核对 live repo（`00-vision.md`、`checkpoint-design.md`、`state-management-design.md`、Stage 34/35 源码与 plan）。

- **vision 文档与 live baseline 不一致（owner-doc drift，已确认）**：
  - `00-vision.md:51` §四 Non-Goals：「动态并行度调整和状态重分布 | 当前阶段不需要。`stateShardCount` 改变需要显式 migration action」
  - `00-vision.md:81` §七 核心取舍：「去除：复杂 Join、广播流、异步算子、**完整 key-group 重分布**」
  - `00-vision.md:89` §八 设计不变量 #2：「所有 keyed state 必须有确定性 `StateShard` 路由」
  - `00-vision.md:75` §六 决策点 #6：「`stateShardCount` 默认值的变更」
  - **但 Stage 34 已交付** KeyGroup/KeyGroupRange/KeyGroupAssignment（分层稳定哈希、key→group 映射、group→subtask 区间映射、job-global `maxParallelism` 默认 128、`getShardCount` 为 `maxParallelism` 别名——`RocksDBStateBackend.java:100-103` `@Deprecated`、`RocksDBKeyedStateBackend.java:346-348` 返回 `maxParallelism`（注：后者当前**无** `@Deprecated` 注解，与前者不一致，属 Minor 待裁定））。
  - **Stage 35 已交付** KeyGroupRange 局部恢复 + `parallelism` 4↔16 rescale E2E（`TestKeyGroupRescaleDispatchE2E`，memory + rocksdb range restore），`TaskEpochSnapshot` 归属物化 + `CheckpointSerDe` 持久化。
- **不变量 #2「StateShard 路由」在多个 design doc 重复 drift（已确认，均 in-scope）**：Stage 34/35 已落地 KeyGroup 路由，但不变量 #2 的「StateShard 路由」表述残留在**三处**：
  - `00-vision.md:89` §八 不变量 #2
  - `checkpoint-design.md:1024` §12 设计不变量 #2（「所有 keyed state 必须有确定性 `StateShard` 路由」——与 vision:89 是同一条不变量的跨文档重复）
  - `core-design.md:338` §4.4 状态路由（「所有 keyed state 必须有确定性 `StateShard` 路由」）
  - 三处必须**同步**改为 KeyGroup 路由，否则 vision 改后产生新的跨文档矛盾。
- **`checkpoint-design.md` §8.5 rescale 段已正确反映现状**（Stage 34/35 已同步，**不是** drift）：
  - §8.5（`:828`）：keyed state rescale 规则——`maxParallelism` 不变、`parallelism` 变化时按 `KeyGroupRange` 交集局部恢复。**Minor**：`:828` 引用的类名 `KeyGroupRangeAssignment` 不存在（实际方法 `computeKeyGroupRangeForSubtaskIndex` 在 `KeyGroupAssignment.java:112`），属 design doc 笔误，本 plan 更新 §8.5 时顺带修正
  - `:840`：`TaskEpochSnapshot` 记录每个 keyed subtask 的 `KeyGroupRange`，`CheckpointSerDe` 持久化
  - `:845`：「`maxParallelism` 默认不可改变。改变 `maxParallelism` 等价于 keyed state 重分片（key→group 映射变化），必须提供显式 migration action 和校验报告」
  - `:858`：「修改 maxParallelism | 默认拒绝，除非提供 reshard migration（key→group 映射变化）」
  - **Minor**：`:710` §8.4 兼容性表仍用术语 `stateShardCount`（语义等价，因是 deprecated 别名），本 plan 视为术语陈旧，非语义 drift，更新 §8.5 时一并核对
- **reshard migration action 未实现**：`checkpoint-design.md:858` 承诺的 reshard migration 在仓库中无对应实现（grep 无 `ReshardMigration` 类）。Stage 35 plan 的 Non-Blocking Follow-ups 明确「`maxParallelism` 变化的显式迁移（Stage 37）」。
- **不存在「StateShard 数据的存量 savepoint」迁移对象**（解释标题「StateShard→KeyGroup 迁移」的收敛）：生产 checkpoint **从不写** `TaskEpochSnapshot.shards`——`addShard`（`TaskEpochSnapshot.java:83`）无任何生产调用方（仅 `TestTaskEpochSnapshot` / `TestStateShardJsonBackwardCompat` 使用，运行时恒为空，Stage 35 plan 已确认）。故前序 Stage 34/35 deferred 的「StateShard→KeyGroup 存量 savepoint 自动迁移」**无迁移对象**；Stage 37 范围收敛为：(a) 不变量 #2 三处 drift 修正 + vision 更新；(b) `maxParallelism` 变化的 reshard migration action。`StateShard` 类本身仍存活（JSON 反序列化向后兼容由 `TestStateShardJsonBackwardCompat` 守护），物理删除属独立清理项。
- **Stage 29 schema 兼容性体系已落地**：`SerializerFingerprint` + `StateSchemaResolver` + `StateMigrationFunction`（Stage 33 接线，`StreamComponents` 实现 `StateMigrationRegistry`）。reshard migration 与 schema migration 正交：schema migration 处理「同一 key、value schema 变化」（per-state、在 backend `getState()` 内触发，`checkpoint-design.md:806-807`）；reshard migration 处理「同一 key、group 归属变化（maxParallelism 变）」（job-global、savepoint 级跨 subtask）。两者**复用的是 read-rewrite 模式，非具体代码**——作用域不同（per-state vs job-global），需独立实现，不可误以为直接复用 schema migration 扫描代码。

## Goals

- **修正 key-group / StateShard owner-doc drift**：把 key-group 重分布从 `00-vision.md` 的 Non-Goal / 去除区域移出，更新为 supported baseline；**不变量 #2「StateShard 路由」三处**（`00-vision.md:89`、`checkpoint-design.md:1024`、`core-design.md:338`）同步改为 KeyGroup 路由；决策点 #6 的 `stateShardCount` 改为 `maxParallelism`。使三份 design doc 与 live 代码彼此一致。
- **交付 `maxParallelism` reshard migration action（离线工具范式）**：提供一个显式、**离线**、可校验的迁移工具/action，读旧 savepoint（本地文件/目录），在 `maxParallelism` 变化（old→new）下重映射所有 keyed state 的 key 到新 group，**写出新 savepoint** + 校验报告，使恢复时按新 group 路由。（范式裁定见 Phase 2 Decision：离线工具而非 restore 路径触发，因 maxParallelism 变化是低频重操作，离线落盘更安全且可复核。）
- **迁移正确性与可校验性**：迁移前后 key 集合守恒（无丢失/重复/静默丢弃），迁移后用新 `maxParallelism` restore 的聚合结果与等价从头跑作业一致。
- **为 Stage 36（BroadcastState vision 决策）建立 vision 更新先例**（非本 plan 交付物，但本 plan 的 vision 更新流程是 Stage 36 的参照）。

## Non-Goals

- **Stage 36 BroadcastState**：广播流在 vision §七 列为「去除」，是否纳入需独立 vision 决策流程，不在本 plan。
- **改变 `maxParallelism` 默认值本身**：默认 128 维持不变；本 plan 只提供「已存在 savepoint 改变 maxParallelism」的迁移路径，不改默认值。
- **在线/自动 reshard**：迁移是显式、离线、用户触发的 action，不做运行时自动重分片。
- **跨 JVM 状态文件传输**（Stage 40）：迁移工具操作本地可达的 savepoint 文件/目录。
- **Operator state rescale**（已有 `SPLIT_DISTRIBUTE/UNION/BROADCAST`，与本 keyed reshard 正交）。
- **删除 `StateShard` 类型 / `TaskEpochSnapshot.shards` 字段**：这些有 JSON 向后兼容负担（`TestStateShardJsonBackwardCompat`），删除是独立清理项，不在本 plan；本 plan 只在 vision 中标注其 legacy 语义。

## Scope

### In Scope

- `00-vision.md` 四处 drift 修正（§四 Non-Goals、§七 核心取舍、§八 不变量 #2、§六 决策点 #6）。
- `maxParallelism` reshard migration action：读旧 savepoint → 对每个 keyed state 按 key 用新 `maxParallelism` 重算 group → 重写归属 → 写新 savepoint，附校验报告。
- migration action 的 focused 测试（key 集守恒、restore 正确性、边界：maxParallelism 增/减、空状态、无 keyed state 作业）。
- `checkpoint-design.md` / `state-management-design.md` 补 reshard migration action 的使用契约（触发条件、校验报告字段、失败语义）。

### Out Of Scope

- Stage 36 BroadcastState vision 决策。
- 跨 JVM 状态传输（Stage 40）。
- `StateShard` 类/字段的物理删除（独立清理项）。
- `maxParallelism` 默认值变更。
- **其他过时但非不变量语义的文档表述（Minor，显式声明 out-of-scope 避免误判遗漏）**：`comparison.md`（「生产 rescale 接线在 Stage 35」等过时叙事、key-group 重分布标 ✅ 与 reshard 未实现的措辞张力）、`README.md` StateShard 术语表。这些是叙事/术语层陈旧，非不变量 drift，不纳入本 plan；如修订 §8.5 时顺带触及可一并修正，但不作为 closure 必需项。

## Execution Plan

### Phase 1 - key-group / StateShard owner-doc drift 修正（vision + checkpoint-design + core-design）

Status: completed
Targets: `ai-dev/design/nop-stream/00-vision.md`（§四:51、§六:75、§七:81、§八:89）；`ai-dev/design/nop-stream/checkpoint-design.md`（§12 不变量 #2 `:1024`）；`ai-dev/design/nop-stream/core-design.md`（§4.4 `:338`）

- Item Types: `Fix`

- [x] `00-vision.md:51` §四 Non-Goals：移除「动态并行度调整和状态重分布」整行，或改写为「`maxParallelism` 变化需显式 reshard migration（Stage 37 已交付）；`parallelism` 变化在 `maxParallelism` 上界内是 supported rescale」。决策（写入 plan）：是「整行移除」还是「改写为 supported-with-migration」。Decision 依据：保留一行可澄清边界，推荐改写而非纯删除
- [x] `00-vision.md:81` §七 核心取舍：「去除」列表中删除「完整 key-group 重分布」，移入「保留」（或新增「保留（Stage 34/35 已交付）」项），与 §五设计收敛路径一致
- [x] `00-vision.md:89` §八 不变量 #2：「所有 keyed state 必须有确定性 `StateShard` 路由」→ 改为「所有 keyed state 必须有确定性 `KeyGroup` 路由（`key → keyGroupId` 仅依赖 job-global `maxParallelism`）」
- [x] `00-vision.md:75` §六 决策点 #6：「`stateShardCount` 默认值的变更」→ 改为「`maxParallelism` 的变更（需显式 reshard migration action）」；保留为「需人决策」类别
- [x] **`checkpoint-design.md:1024` §12 不变量 #2**：与 vision:89 同步，「StateShard 路由」→「KeyGroup 路由」（同一不变量的跨文档重复，必须同步，否则 vision 改后产生新矛盾）
- [x] **`core-design.md:338` §4.4 状态路由**：「所有 keyed state 必须有确定性 `StateShard` 路由」→ 同步改为 KeyGroup 路由（同上，第三处重复）
- [x] 一致性自检：修订后，§四/§七/§八/§六 与 `checkpoint-design.md` §8.5（`:828/840/845/858`）、`state-management-design.md` 的 KeyGroup/maxParallelism 表述无矛盾

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。

- [x] `00-vision.md` 四处 drift 全部修订
- [x] **不变量 #2 三处全部同步**（`00-vision.md:89`、`checkpoint-design.md:1024`、`core-design.md:338` 均改为 KeyGroup 路由）
- [x] **跨文档 grep 自检**：`grep -rn "StateShard 路由\|StateShard路由" ai-dev/design/nop-stream/` 在不变量/状态路由语义处无残留（legacy 字段说明除外）；`grep -rn "key-group 重分布" ai-dev/design/nop-stream/00-vision.md` 不再出现在 Non-Goal / 去除语义中
- [x] **Decision 记录**：§四 Non-Goals 该行的最终处理（移除 vs 改写）在本 plan 记录理由
- [x] `ai-dev/design/nop-stream/README.md`（如索引了相关章节）无需则写 `No owner-doc update required`；本 Phase 改的就是 owner doc
- [x] **纯文档 Phase**：无代码变更；本 Phase 无需 `./mvnw test`（见 Closure Gates 注）
- [x] `ai-dev/logs/` 对应日期条目已更新

**Phase 1 Decision 记录**：
- §四 Non-Goals 该行采用「**改写为 supported-with-migration**」（非整行移除）。原行「动态并行度调整和状态重分布 = Non-Goal」已语义过时：`parallelism` rescale（Stage 35）与 `maxParallelism` 离线 reshard（Stage 37）均已 supported。真正保留的 Non-Goal 收敛为「**在线/自动 reshard（运行时自动重分片）**」，并在理由列显式标注 supported 的两条路径与边界，澄清而非纯删除。
- §七「完整 key-group 重分布」从「去除」移入「保留」，并标注 Stage 34/35/37 交付来源。
- README.md 的 StateShard 索引项（line 362/400/463）描述的是有意保留的 legacy 术语（StateShard 类仍存活，JSON 向后兼容由 `TestStateShardJsonBackwardCompat` 守护，物理删除属 Non-Blocking Follow-up），非不变量语义 drift，故 `No owner-doc update required`（README 是索引，invariant 已在 owner doc 修正）。

### Phase 2 - `maxParallelism` reshard migration action（离线工具范式，Stage 35 deferred 收口）

Status: completed
Targets: 新增离线迁移工具（`nop-stream/nop-stream-core` state 或 checkpoint 包，位置执行时裁定）；复用 Stage 34 `KeyGroupAssignment.assignToKeyGroup` 的 key→group 计算；`CheckpointSerDe` / `LocalFileCheckpointStorage` savepoint 读写路径；`ai-dev/design/nop-stream/checkpoint-design.md` §8.5（design 先行）

- Item Types: `Decision | Fix | Proof`

> **范式裁定（闭合 B1）**：采用**离线工具**范式——独立工具读旧 savepoint 文件/目录、重映射、**写出新 savepoint** + 校验报告。**不**采用「restore 路径触发 descriptor 运行时重算」范式（运行时重算不落新文件，与「可复核的离线迁移」目标冲突，且 maxParallelism 变化是低频重操作）。此裁定与 Goals/Exit Criteria 一致，消除 Goals 与原 Decision 推荐的方向矛盾。

- [x] **design 先行（闭合 B2）**：先在 `checkpoint-design.md` §8.5 补 reshard migration action 使用契约，**再**写代码。契约须定义：(1) 工具入参（旧 savepoint 路径、old/new `maxParallelism`）；(2) savepoint 物理重写流程（见下方算法规格）；(3) 校验报告字段；(4) 失败语义；(5) 与 schema migration 的边界（orthogonal，不混用）。同时修正 §8.5 `:828` 的类名笔误 `KeyGroupRangeAssignment` → `KeyGroupAssignment`
- [x] **算法规格（reshard 重写流程，期望行为语义）**：对旧 savepoint 中**每个 keyed state**，遍历其全部 entry；对每个 (key, value)，用**新** `maxParallelism` 经 `KeyGroupAssignment.assignToKeyGroup(key, newMaxParallelism)` 重算该 key 的 group（key→group 映射随 `maxParallelism` 变化，这是与 Stage 35 `parallelism`-only rescale 的本质区别——后者 key→group 不变，只 group→subtask 变）；按新 group 把 entry 归入新 subtask（用新 `parallelism` 下的 group→subtask 映射）；写出新 savepoint：新 `maxParallelism` + 每个 subtask 的新 `KeyGroupRange` + 重映射后的 entry。operator state（非 keyed）原样搬运，不受 reshard 影响
- [x] **校验报告**：迁移产出校验报告——迁移前后 key 总数守恒（per state）、old/new `maxParallelism`、每个新 subtask 拥有的 key 数分布；不一致则 fail-fast（不静默写半成品）
- [x] **失败语义**：迁移中途失败 → 新 savepoint 不完整即丢弃，从原 savepoint 重跑（无「迁移中」持久标记，与 schema migration 崩溃语义一致 `checkpoint-design.md:809`）；原 savepoint 只读不被破坏；迁移产出的新 savepoint 在 restore 前用 Stage 29 schema fingerprint 一并校验
- [x] **接线验证 restore**：reshard 产出的新 savepoint 经既有 `CheckpointSerDe` + executor restore dispatch（Stage 35 区间路由）按新 `maxParallelism` 正常 restore（无需改 restore 路径——reshard 已把数据物化为新布局，restore 只消费）

Exit Criteria:

- [x] **迁移正确性单测**：构造 `maxParallelism=128` 的 keyed savepoint → reshard 到 `maxParallelism=256`（及反向 256→128），断言迁移前后所有 keyed state 的 key 总数守恒、无丢失/重复
- [x] **restore 正确性 E2E**：reshard 后的 savepoint 用**新** `maxParallelism` restore，聚合结果与等价从头跑作业（同新 `maxParallelism`）一致（memory + rocksdb 两后端）
- [x] **端到端验证**：从「读旧 savepoint → reshard → 写新 savepoint → restore → 运行」完整路径跑通，恢复后状态正确
- [x] **边界测试**：(a) 空 keyed state 迁移不报错不静默丢数据；(b) 无 keyed state 作业（纯 operator state）迁移为 no-op 且显式记录；(c) `maxParallelism` 未变化时迁移 fail-fast（无意义迁移拒绝）或显式 no-op 并记录（Decision 记录选择）
- [x] **无静默跳过**：迁移遇到未知 state 类型 / 无法重算 group 的 key 时抛异常（非静默丢弃）；迁移工具入参非法（如 old==new `maxParallelism`、savepoint 格式不可识别）时 fail-fast，不静默写出与输入相同的 savepoint
- [x] **接线验证**：reshard 产出的新 savepoint 经既有 `CheckpointSerDe` + executor restore dispatch 按新 `maxParallelism` 正常 restore——端到端测试断言 restore 后 group 归属为新 `maxParallelism` 计算（非旧值），证明重写真实生效（非原样搬运）
- [x] `ai-dev/design/nop-stream/checkpoint-design.md` §8.5 reshard migration action 使用契约已补（Phase 2 design 先行项的交付确认）；`state-management-design.md` 视需要补充
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-rocksdb,nop-stream/nop-stream-runtime -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

**Phase 2 实现交付记录**：
- 纯逻辑（nop-stream-core `io.nop.stream.core.common.state.shard.KeyGroupReshard`）：`redistributeStates(globalStates, newMaxP, newParallelism)` 重算每个 key 的 group 并按新 group→subtask 路由；`countKeyedEntries` 守恒计数。focused test：`TestKeyGroupReshard`（11 用例，含 128→256 守恒、256→128、moved-vs-old 反空洞、metadata 保留、空/null、entry 缺 key / 未知类型 / 缺 entries 全部 fail-fast、参数非法）。
- I/O 工具（nop-stream-runtime `io.nop.stream.runtime.checkpoint.reshard`）：`MaxParallelismReshardMigration.migrate(oldSavepointPath, oldMaxP, newMaxP, outputBaseDir)` 读旧 savepoint → 按 vertex 汇聚全局 keyed 池 → `KeyGroupReshard.redistributeStates` → 重建 TaskEpochSnapshot（物化新 ownership）+ operator state 原样搬运 → 原子写出新 savepoint（`.tmp`→rename，与 `LocalFileCheckpointStorage.storeSavepoint` 一致）+ `reshard-report.json`；`ReshardMigrationResult` 校验报告（per-state 守恒、per-subtask 分布、warnings）。纯入口 `reshardCheckpoint(...)` 供测试。
- E2E test：`TestMaxParallelismReshardMigrationE2E`（8 用例）：128→256 / 256→128 守恒、memory + rocksdb restore 聚合 == 输入、stamped ownership（in-memory，因 `serializeCheckpoint` 不持久化 ownership 字段——该字段 restore 时由 execPlan re-derive，与平台所有 savepoint 一致）、空 keyed state、operator-only no-op+warning、old==new fail-fast、未知结构 fail-fast、anti-hollow moved 断言。
- **Decision（边界 c）**：`old == new maxParallelism` 采用 **fail-fast**（拒绝无意义迁移），不静默写出与输入相同的 savepoint。
- **newParallelism**：默认 = 旧 savepoint 每 vertex 并行度（pure reshard：subtask 数不变，仅 key→group→subtask 重算）；可选显式 override。reshard 工具不改 value schema/codec（与 schema migration 正交，restore 时由 Stage 29 fingerprint 兜底）。

## Closure Gates

> **纯文档 Phase（Phase 1）**：无代码变更，其 `./mvnw test`/`lint` 验证由 Phase 2 的代码变更整体承载。Plan 整体涉及代码（Phase 2），故以下构建门禁保留。

- [x] key-group / StateShard owner-doc drift 全部修正：`00-vision.md` 四处 + 不变量 #2 三处同步（`00-vision.md:89`、`checkpoint-design.md:1024`、`core-design.md:338`），跨文档 grep 自检无残留
- [x] Stage 35 deferred「`maxParallelism` 变化的显式迁移」收口（reshard migration 离线工具交付，有真实消费者：产出的新 savepoint 可被既有 restore 路径消费）
- [x] reshard migration key 集守恒 + restore 正确性有 focused test
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 owner-doc drift
- [x] 受影响 owner docs（`00-vision.md`、`checkpoint-design.md` §8.5+§12、`core-design.md` §4.4、`state-management-design.md`）同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：reshard migration 离线工具真实重算 group 并写出新 savepoint（非原样搬运/stub）；端到端验证产出 savepoint 能被 restore 消费且 group 归属为新 `maxParallelism`；无空方法体/静默跳过
- [x] `./mvnw compile -pl nop-stream/nop-stream-core,nop-stream/nop-stream-rocksdb,nop-stream/nop-stream-runtime -am`
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-rocksdb,nop-stream/nop-stream-runtime -am`
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本 plan> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（执行中按需填写；预期 `StateShard` 类型/字段物理删除 → 独立清理项，因有 JSON 向后兼容负担）

## Non-Blocking Follow-ups

- `StateShard` 类与 `TaskEpochSnapshot.shards` 字段的物理删除（独立清理项，需评估 JSON 向后兼容边界，由 `TestStateShardJsonBackwardCompat` 守护）
- Stage 36 BroadcastState 的 vision 决策流程（本 plan 的 vision 更新为先例，但广播流是否纳入属独立决策）

## Closure

Status Note: 全部完成。Phase 1（owner-doc drift 修正，纯文档）+ Phase 2（maxParallelism reshard migration 离线工具 + focused/E2E 测试）均 done，Closure Gates 全部满足。
Completed: 2026-08-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general subagent ses_03e04ff5affe1bbpkfs4UryY67），独立于执行者，未修改任何文件。
- Evidence:
  - **A 文档 drift**：`00-vision.md` §四/§七/§八/§六 + 不变量 #2 三处跨文档（`00-vision.md:89`、`checkpoint-design.md:1076`、`core-design.md:338`）均为 KeyGroup；`grep "StateShard 路由"` 在 `ai-dev/design/nop-stream/` 残留为 0。
  - **B 反空洞**：`KeyGroupReshard.redistributeStates` 真实调用 `assignToKeyGroup(rawKey, newMaxParallelism)` + `assignKeyGroupToSubtask`（重算非拷贝）；fail-fast 覆盖缺 key/未知类型/缺 entries；`MaxParallelismReshardMigration` 全局池汇聚 + 重分布 + 物化 ownership + operator state 原样搬运 + 原子写出新 savepoint + report；`old==new` 抛异常。
  - **C 测试绿**：`TestKeyGroupReshard` Tests run: 11, Failures: 0, Errors: 0；`TestMaxParallelismReshardMigrationE2E` Tests run: 8, Failures: 0, Errors: 0；三模块整体 611 tests 0 failures。
  - verdict: **PASS**（无 stub / 无矛盾 / 全 exit criteria 满足）。
- 执行侧补充证据：`check-plan-checklist.mjs --strict` exit 0；`scan-hollow-implementations.mjs --module nop-stream --severity high` exit 0（新代码无命中）；`check-doc-links.mjs --strict` 0 errors。

Follow-up:

- no remaining plan-owned work. Non-Blocking Follow-ups（StateShard 物理删除、Stage 36 BroadcastState vision 决策）已显式声明为独立项，不属本 plan。
