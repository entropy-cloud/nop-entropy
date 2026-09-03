# 状态 SerDe 与状态后端重复代码收敛 + 恢复路径防御性校验（roadmap items 21+24+30 联动）

> Plan Status: active
> Mission: nop-stream-productization
> Work Item: items 21（core 重复代码收敛第二轮）+ 24（状态恢复路径防御性校验补全）+ 30（rocksdb SerDe 克隆家族收敛）——三项审计来源显式要求联动执行避免双倍改动，本 plan 为三项的共同载体
> Last Reviewed: 2026-09-03
> Source: `ai-dev/analysis/2026-09/2026-09-01-nop-stream-core-module-audit.md` §2.3（D-1..D-4 结构性重复锚点 + W-4 恢复守卫缺口——item 21/24 依据）；`ai-dev/analysis/2026-09/2026-09-01-nop-stream-rocksdb-flow-fraud-example-audit.md` §2.5（rocksdb 重复代码清单 + 状态类族 4 对克隆——item 30 依据）
> Related: `2026-09-01-0938-2-core-module-audit.md`（item 7 plan，S-12 legacy 回退漂移已就地修复——本 plan 保留其语义）；`2026-09-01-1457-3-rocksdb-flow-fraud-example-audit.md`（item 11 plan，RK-3 同族对齐修复已落地）；item 25（manifest 版本化）不属本 plan（EpochManifest 字段是 checkpoint 产物契约，非 SerDe 结构收敛）

## Purpose

把 core 与 rocksdb 两侧状态序列化/状态后端的同族重复代码（审计 D-1..D-4 与 rocksdb 镜像清单）收敛到单一实现，并在收敛后的单一咽喉点上补齐 item 24 的恢复路径防御性校验——**不改变任何快照/恢复的字节格式与可观察行为**（legacy 快照兼容性、错误码语义、TTL 语义全部保持；防御性校验错误语义除外，见 Goals），以固化在先的独立回归面保护这次纯结构重构。

## Current Baseline

（锚点来自两份审计报告 2026-09-01 live 核对，本轮起草复核存在性；**具体行数/克隆对计数已随后续修复漂移（如 RocksDBSnapshotSerDe 839→~848、MemoryStateSerDe 919→~993），一律以 Phase 1 复算为准**，本节仅保留位点级锚点）

- **core 侧（core 审计 §2.3 D-1..D-4）**：
  - D-1（high）：`MemoryStateSerDe` restore*/snapshot* 成对克隆（restoreListState vs restoreInternalListState 28/30 行相同、aggregating 对 96%、snapshot 对 90%）；类名回退模板 5 处复制——漂移缺口（Reducing/Aggregating 缺 valueTypeName/accumulatorTypeName 回退）已由 item 7 的 S-12 就地补齐，**结构重复仍在**；entry-loop key 模板 8 处复制（MemoryStateSerDe.java:280-342,396-453,624-763,172-318）。
  - D-2（high）：memory 状态类家族成对克隆 4 对（MemoryListState vs MemoryInternalListState 等，MemoryListState.java:74-133 等四对）+ `applyMigration` 四处逐字复制 + 无意义同分支 if/else 化石四处。
  - D-3（high）：`MemoryKeyedStateBackend` 8 个 getXxxState 重载 85% 同构 + rebindStateBackends 8 路 instanceof 阶梯（TtlAware 标记接口已存在，MemoryKeyedStateBackend.java:177-316,460-481）。
  - D-4（medium）：windowing assigner/trigger 家族克隆——SETW vs SPTW ~85%（构造器 17/17 行逐字相同）、CETT vs CPTT ~80% 已漂移（审计 §2.1② 推演两处差异语义等价：CPTT 的 MAX_VALUE 时刻必先命中 maxTimestamp 短路分支，fireTimestamp 守卫不可达）、窗口溢出守卫 4 处复制、StreamGraphGenerator 节点+边创建样板 4 处（transformSource vs transformSourceApi ~85%）。
- **rocksdb 侧（rocksdb 审计 §2.5 重复代码行）**：`RocksDBSnapshotSerDe`（839 行 package-private）8 snapshot + 8 restore 分支同构（与 D-1 同族）；`RocksDBKeyedStateBackend` 8 个 getXxxState 重载 85% 同构（与 D-3 同族）；状态类族 4 对克隆（List/InternalList、Appending/Internal×2、Aggregating 两态、Map——与 D-2 同族）。
- **item 24 缺口（core 审计 §2.3 W-4）**：`TaskEpochSnapshot.getKeyGroupRange` 不校验 start/end 一致性（部分写坏 legacy 数据读取期远端抛裸 IAE，TaskEpochSnapshot.java:173-178）；restore 路径 mapValue 对/namespace 反序列化无逐项类型校验（CCE 无上下文，MemoryStateSerDe.java:224-229,810-817）；namespace 反序列化缺 TimeWindow 字段守卫。
- **既有回归面**：rocksdb——`TestRocksDBSnapshotRestore` 21 用例 round-trip、增量族 4 测试（真实 SST 物化→range restore）、`TestRocksDBIncrementalRestoreFailFast`；core/场景——memory SerDe 与状态类既有单测、`TestStateMigration`/`TestRocksDBStateMigration`/`TestStateMigrationEndToEnd`、`reshard/TestMaxParallelismReshardMigrationE2E`（同版本迁移机制）；S1/S2 复合场景 LOCAL 测试在默认套件。**缺口**：状态族 × 双后端的 round-trip 矩阵未系统固化；无「重构前快照产物作为 fixture」的跨重构格式稳定性证明。
- **rocksdb 守卫对等性背景**：rocksdb 审计曾将恢复路径同类守卫缺口裁定为 watch-only（W-R1），理由锚在「与 core 容忍基线一致」——本 plan 收紧 core 后该理由失效，Phase 1 必须重新裁定对等性（见执行项）。
- **已修复且必须保持的语义**：S-12（core）+ RK-3（rocksdb）legacy typeName 回退；RK-4 TTL 时间戳保持；RK-8 损坏增量 marker fail-fast；错误码体系（`ERR_STREAM_STATE_ERROR`/`ERR_STREAM_STATE_SCHEMA_MISMATCH` 等）。
- schemaVersion 恒 1（SerializerFingerprint 预留分支，D-GAP §1.1 ④）——本 plan **不得**递增任何版本号或改变序列化信封（格式变更属 item 25 的 manifest 字段范畴与本 plan 无关）。

## Goals

- D-1..D-4 与 rocksdb 镜像清单的同族重复收敛为单一实现（或模板/参数化单点）；确有语义差异不可安全收敛的位点允许「裁定保留 + 差异论证」（两态计数，见 Phase 1 位点级出口），重复行数与克隆位点显著消除（Phase 1 复算基线，closure 时对照复算）。
- item 24 三项防御性校验落在收敛后的单一咽喉点：mapValue 逐对类型校验、namespace 反序列化 TimeWindow 字段守卫、TaskEpochSnapshot KeyGroupRange start/end 一致性校验——错误为 typed `StreamException`（含定位参数），不再裸 IAE/CCE。
- 独立回归面先于重构固化：状态族 × 双后端 round-trip 矩阵 + 重构前快照 fixture（证明跨重构字节格式稳定）。
- 全部既有测试（含 S1/S2 场景、gated 分布式默认态）全绿；行为零变更（防御性校验错误语义除外——坏数据路径从裸 IAE/CCE 变为 typed 错误是本 plan 声明内唯一行为增量）。

## Non-Goals

- 任何快照/checkpoint/状态格式的变更（新字段、版本递增、键布局调整、序列化信封改动——manifest 级 `stateFormatVersion`/`checksum` 是 item 25）。
- item 23（core execution 根包重组——同为 core 变更但不属重复代码收敛，不捆绑以控制回归面）。
- items 25/26/27/28（runtime 侧 checkpoint 协调器/fencing/数据面治理）。
- W-6 深度不可变化（core 审计已裁定 watch-only residual，维持——见 Deferred）。
- 性能优化（除消除重复本身的间接收益）；新功能面（无新公共 API，内部结构收敛 + 防御校验错误语义为唯一行为增量）。

## Scope

### In Scope

- core：MemoryStateSerDe 收敛（D-1）+ item 24 守卫同点落地；memory 状态类 4 对 + applyMigration 单点（D-2）；MemoryKeyedStateBackend 重载/instanceof 阶梯收敛（D-3）；windowing 家族与 StreamGraphGenerator 样板收敛（D-4）。
- rocksdb：RocksDBSnapshotSerDe 8+8 分支、backend 8 重载、状态类族 4 对（item 30 清单）。
- 回归面固化：round-trip 矩阵测试 + 重构前快照 fixture。
- core 与 rocksdb 同族结构的**实现形态对齐**（两侧收敛后共享/镜像的结构由 Phase 1 裁定——是否抽公共模板、抽到哪个模块）。

### Out Of Scope

- 上列 Non-Goals 全部。

## Execution Plan

### Phase 1 - 基线复算与回归面固化

Status: planned
Targets: `nop-stream/nop-stream-core/`、`nop-stream/nop-stream-rocksdb/`（测试与 fixture）

- Item Types: `Proof | Decision`

- [ ] 清单复算：对 D-1..D-4 + item 30 清单逐条 live 复算（文件/位点/克隆对数——审计后无第三方改动假设需验证），产出本 plan 的基线数字（重复位点计数口径固化，closure 复算用同口径）；如清单有漂移，修正后再执行后续 phase。**位点级出口**：复算/实现中发现某位点在「行为零变更」约束下不可安全收敛（存在真实语义差异）时，允许「裁定保留 + 差异论证」并记入复算记录——closure 复算按「收敛 / 裁定保留（附论证）」两态计数，不得为过门禁做形式收敛（差异硬塞布尔参数）
- [ ] 收敛实现形态裁定：core/rocksdb 两侧收敛后的结构形态（各自单点 vs 抽公共骨架到 core 由 rocksdb 复用——含模块依赖方向合规性）；CETT/CPTT 收敛方向裁定（守卫统一保留 vs 删除不可达分支——需附等价性论证引用审计 §2.1② 推演并**重新验证**：推演只直接覆盖「向 CPTT 加守卫」方向，「从 CETT 删守卫」方向的等价性需单独论证）；**rocksdb 恢复路径守卫对等性裁定**（core 收紧后 W-R1 watch-only 理由失效：对等落地守卫 + rocksdb 坏数据路径用例，或显式维持 watch-only 并重述理由——若裁定共享骨架，守卫随骨架流入 rocksdb 属声明内行为增量，须配用例）
- [ ] round-trip 矩阵测试固化：状态族（Value/List/Reducing/Aggregating/InternalAggregating/Appending/Map + TTL 变体 + namespace 变体按 live 支持面）× 双后端（memory/rocksdb）snapshot→restore round-trip 用例补齐（既有用例覆盖的引用不重写）
- [ ] 重构前快照 fixture（**双向证明**）：对代表性状态族在重构前生成快照产物固化为测试资源——①**捕获层裁定**（SerDe 产物经什么序列化固化为资源：测试内 JSON 序列化 vs CheckpointSerDe 真实信封，Phase 1 选定并记录；**序列化确定性（键序/字段序稳定）为选型硬标准**——写稳定比对依赖产物逐字节可复现）；②**读兼容**用例：fixture 可被当前代码恢复；③**写稳定**用例：重构后对同一状态图重新 snapshot 的产物与 fixture 逐字节/哈希比对相等（防止键序/字段序等写侧漂移逃逸）；④**范围声明**：增量 SST 二进制路径不经收敛中的 SerDe，显式排除在 fixture 外（由既有 4 个增量测试承载）；⑤legacy 键路径 fixture 至少一例（S-12/RK-3 语义）——构造方法可沿用既有先例（真实快照改写 legacy 拼写键，参照 `TestMemoryStateSerDeAuditFixes` 的 legacy 改写法），不手写格式

Exit Criteria:

- [ ] 基线复算记录（清单逐条 + 计数口径 + 位点级出口说明）落日志；形态裁定（含 CETT/CPTT 与 rocksdb 守卫对等性）含拒绝替代方案
- [ ] round-trip 矩阵与 fixture 双向用例（读兼容 + 写稳定）存在且在**未重构代码**上全绿（证明回归面本身正确）
- [ ] legacy 键路径 fixture 至少一例（S-12/RK-3 语义钉定）
- [ ] **新功能必有测试**：本 phase 新增测试属回归面固化（列出矩阵/fixture 用例名）；无生产行为变更（No production change in this phase，显式声明）
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [ ] owner-doc 裁定：`No owner-doc update required`（纯测试与裁定固化，无行为变更——Phase 2 落守卫时再评估 owner doc）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - core SerDe 收敛与恢复守卫（D-1 + item 24）

Status: planned
Targets: `nop-stream/nop-stream-core/`（MemoryStateSerDe 及同族）

- Item Types: `Fix`

- [ ] D-1 收敛：restore*/snapshot* 成对克隆、类名回退模板 5 处、entry-loop 模板 8 处 → 单一实现（按 Phase 1 形态裁定）；S-12 legacy 回退语义逐分支保持（fixture/矩阵用例验证）
- [ ] item 24 守卫同点落地：mapValue 逐对类型校验（typed 错误含 entry 定位参数，替代裸 CCE）；namespace 反序列化 TimeWindow 字段守卫；`TaskEpochSnapshot.getKeyGroupRange` start/end 一致性校验（typed 错误替代远端裸 IAE）——三项守卫错误信息含足够定位上下文（错误码 + 参数）
- [ ] 守卫行为测试：逐守卫至少一个「坏数据 → typed 错误（错误码+参数断言）」用例 + 一个「好数据不受影响」用例

Exit Criteria:

- [ ] D-1 清单位点的克隆消除（对照 Phase 1 基线数字复算验证）；MemoryStateSerDe 无成对 restore*/snapshot* 克隆残留（复算口径记录）
- [ ] item 24 三守卫用例存在且断言错误码与参数（新行为 = 防御校验，测试必答）；既有 round-trip 矩阵/fixture 全绿（legacy 兼容与格式稳定证明）
- [ ] **无静默跳过**：守卫拒绝路径显式抛 typed 异常，无吞异常/静默跳过分支（对照 guide 规则 24 自查）
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [ ] owner-doc 裁定：错误码/守卫语义若构成契约面（state-management-design.md 或 checkpoint-design.md 相关承诺）则同步；纯内部结构收敛部分记录 `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - core 状态类与后端收敛（D-2 + D-3）

Status: planned
Targets: `nop-stream/nop-stream-core/`（memory 状态类族 + MemoryKeyedStateBackend）

- Item Types: `Fix`

- [ ] D-2 收敛：4 对状态类克隆 → 继承/组合单一实现（按 Phase 1 形态裁定）；`applyMigration` 四处逐字复制 → 单点；同分支 if/else 化石四处清除
- [ ] D-3 收敛：8 个 getXxxState 重载同构部分 → 参数化单点；rebindStateBackends instanceof 阶梯 → 基于 TtlAware 标记的统一路径（标记接口语义不变）
- [ ] 回归：矩阵/fixture + 既有 backend 用例全绿；TTL 行为用例（RK-4 core 对应面）全绿

Exit Criteria:

- [ ] D-2/D-3 清单位点复算消除记录；`applyMigration` 单点化验证
- [ ] rebindStateBackends 路径行为等价（TtlAware 判定结果对照重构前后一致——用例或复算记录）
- [ ] **新功能必有测试**：No new test required: 纯结构重构（行为由 Phase 1 固化的矩阵/fixture + 既有 backend/TTL 用例覆盖）——显式声明
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [ ] owner-doc 裁定：`No owner-doc update required`（纯结构重构，Phase 2 已裁定守卫语义的 owner doc；本 phase 无契约面变更）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - rocksdb 同族收敛（item 30）

Status: planned
Targets: `nop-stream/nop-stream-rocksdb/`（RocksDBSnapshotSerDe + RocksDBKeyedStateBackend + 状态类族）

- Item Types: `Fix`

- [ ] `RocksDBSnapshotSerDe` 8 snapshot + 8 restore 分支收敛（与 Phase 2 core 侧形态对齐——按 Phase 1 裁定共享骨架或镜像单点）；RK-3 legacy typeName 回退语义保持（fixture 验证）
- [ ] `RocksDBKeyedStateBackend` 8 重载 + rebind 阶梯收敛（与 Phase 3 对齐）
- [ ] 状态类族 4 对克隆收敛（List/InternalList、Appending/Internal×2、Aggregating 两态、Map）
- [ ] **条件执行项**（Phase 1 守卫对等性裁定的执行宿主）：若裁定「对等落地守卫」或「共享骨架守卫流入 rocksdb」——本 phase 落 rocksdb 侧守卫 + 坏数据路径 typed 错误用例（守卫语义与 core 侧一致，含定位参数）；若裁定「维持 watch-only」——本项勾选时附重述理由引用（无执行增量）
- [ ] 回归：rocksdb 全量（TestRocksDBSnapshotRestore 21 用例 + 增量族 4 + fail-fast 族）+ 矩阵/fixture 全绿

Exit Criteria:

- [ ] item 30 三清单位点复算消除记录（8+8 分支/8 重载/4 对）
- [ ] **端到端验证**：真实 SST 物化 → range restore 增量路径用例（既有 4 测试）全绿——rocksdb 收敛不破坏增量快照恢复主路径
- [ ] **新功能必有测试**：若 Phase 1 裁定守卫对等落地/共享骨架流入——列出 rocksdb 坏数据路径守卫用例名；否则 No new test required: 纯结构重构（同 Phase 3 理由）——按裁定结果条件式声明
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [ ] owner-doc 裁定：state-management-design.md 若描述了状态类族结构则核对同步；否则 `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - windowing 与图生成样板收敛（D-4）+ 总复算

Status: planned
Targets: `nop-stream/nop-stream-core/`（windowing assigners/triggers + StreamGraphGenerator）

- Item Types: `Fix | Proof`

- [ ] SETW/SPTW 构造器与公共逻辑收敛；CETT/CPTT 按 Phase 1 裁定方向统一（等价性论证随裁定记录）；窗口溢出守卫 4 处 → 单点
- [ ] StreamGraphGenerator 节点+边创建样板 4 处收敛（transformSource vs transformSourceApi 等）
- [ ] 触发器行为回归：triggers/ 8 测试 + evictors/ 3 测试 + WindowOperator 集成用例（item 7 审计 §2.1② 行为级证据面）全绿
- [ ] 全清单总复算：D-1..D-4 + item 30 逐条对照 Phase 1 基线数字复算消除情况，记录复算命令与结果（closure audit 消费）

Exit Criteria:

- [ ] D-4 位点收敛 + 触发器行为证据面全绿（含 CETT/CPTT 裁定方向的针对性用例）
- [ ] StreamGraphGenerator 图生成等价（既有图生成测试 + S1/S2 场景回归承载）
- [ ] **端到端验证**：S1/S2 复合场景 LOCAL 测试（默认套件）全绿——窗口/状态收敛在真实管线上的端到端证明
- [ ] 总复算记录落日志（逐清单数字对照，含「裁定保留」位点及其差异论证）
- [ ] **新功能必有测试**：No new test required: 纯结构重构——显式声明（Phase 2 守卫测试除外，已在该 phase 交付）
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [ ] owner-doc 裁定：`No owner-doc update required`（触发器/图生成行为契约未变，由既有测试钉定；若 Phase 5 实现中发现契约面差异则回写）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] items 21/24/30 的审计清单位点逐条处置：按「收敛 / 裁定保留（附差异论证）」两态计数（Phase 1 基线 vs closure 复算数字对照，同口径）；裁定保留位点不得为无论证的形式收敛
- [ ] item 24 三守卫落地且 typed 错误语义经测试断言；rocksdb 守卫对等性按 Phase 1 裁定执行（对等落地有用例，或维持 watch-only 的重述理由落档）
- [ ] 快照/恢复字节格式零变更证明：**读兼容**（重构前 fixture 在最终代码上恢复成功）+ **写稳定**（最终代码对同一状态图重新 snapshot 与 fixture 逐字节/哈希相等）；schemaVersion 无递增；S-12/RK-3/RK-4/RK-8 语义钉定用例全绿
- [ ] 行为零变更证明：全量回归 + S1/S2 场景（LOCAL 默认态）全绿；**closure 时跑既有 gated 场景面**（S1/S2 DISTRIBUTED + C2/C3，item 16/17 已留绿基线，成本有界）——状态 SerDe/恢复路径属分布式 checkpoint/restore 数据面，gated 面为必跑项非可选
- [ ] 无空壳/静默跳过引入（守卫路径全部显式 typed 异常）
- [ ] 独立子 agent closure audit 已完成并记录证据（含 Anti-Hollow：收敛后单点实现确实被双后端消费路径调用）
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` exit 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0（如有文档更新）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0
- [ ] roadmap items 21、24、30 写回 `done`（closure audit 通过后，三项同写）

## Deferred But Adjudicated

### W-6 深度不可变化（EpochManifest 内部可变 Map 暴露等）

- Classification: `watch-only residual`
- Why Not Blocking Closure: core 审计已裁定（消费方均为快照后只读路径，无已证实并发写缺陷）；深度不可变化涉及 runtime 消费路径行为审计，超出本 plan 重复代码收敛范围
- Successor Required: `no`
- Successor Path: —

### memory 后端 snapshotState 空态返回 null 的逐调用点判空（W-6 关联项）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 行为语义（null 契约）被既有调用点钉定，改变返回契约属行为变更非结构收敛；收益为可读性
- Successor Required: `no`
- Successor Path: —

## Non-Blocking Follow-ups

- 收敛后单点实现的进一步模板化（若 Phase 1 裁定为「各自单点」形态，core/rocksdb 骨架统一属后续优化候选）。
- item 22（测试通配符导入清理）如在本文档改动文件上有交集，机械清理归 item 22 统一 sweep，本 plan 不顺手改无关导入。

## Closure

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent session/task id>>
- Evidence: <<逐条 Exit Criterion / Closure Gate 验证结果 + 基线/复算数字对照 + 工具退出码>>

Follow-up:

- <<只记录 non-blocking follow-up；或写 no remaining plan-owned work>>
