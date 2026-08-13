# 3 跨模块契约修复：Debezium offset 注册表陈旧续跑（P1）+ checkpoint/README 文档漂移（P1×3）+ RocksDB 命名空间迁移（P1）

> Plan Status: active
> Last Reviewed: 2026-08-13
> Source: `ai-dev/audits/2026-08-13-0805-open-audit-nop-stream-invariant-loop.md`（AR-03）+ `ai-dev/audits/2026-08-13-0805-multi-audit-nop-stream-invariant-loop.md`（P1-DOC-01/02/03、P1-01-01）
> Related: `2026-08-13-1243-1`（运行时引擎）、`2026-08-13-1243-2`（flow DSL）

## Purpose

把 nop-stream 跨模块的四处契约问题收口：Debezium offset 静态注册表永不清洗导致同 JVM 复用连接器名即从陈旧 offset 续跑、未命名连接器共享 `_default_` 桶互相污染（AR-03 P1，静默事件丢失）；checkpoint-design §2.4/§2.5 与 nop-stream README 三处文档契约漂移（P1-DOC-01/02/03）；nop-stream-rocksdb 类驻留 core 命名空间跨 jar split-package（P1-01-01，结构性重构，需 plan-first + 人工确认门）。

## Current Baseline

（live repo 2026-08-13 复核）

- `NopStreamOffsetBackingStore` 静态 `REGISTRY`（`nop-message/nop-message-debezium/.../NopStreamOffsetBackingStore.java:56-57` 声明，仅 `computeIfAbsent` 永不移除）只进不出；`clearConnector`（`:92`）仅测试调用（`TestDebeziumCdcCheckpoint.java:54`）。`DebeziumCdcSourceFunction.initializeState` 首跑路径（state==null）`forConnector(resolveConnectorName())`（`DebeziumCdcSourceFunction.java:232-236`）不清理已有 offset；`resolveConnectorName()` 未配置 name 时返回 `"_default_"`（`:260-266`）→ 同 JVM 先前跑过同连接器名（崩溃未 checkpoint、redeploy、不同 pipeline 复用名）即从陈旧 offset 续跑，两未命名连接器共享 `_default_` 桶互相覆盖。**`NopStreamOffsetBackingStore.configure()`（:104-115）与 `ensureBound()`（:202-208）另有独立 `_default_` 回退。**
- `checkpoint-design.md:101`（§2.4 ALIGNING 行）声称"重叠 barrier 抛 `ERR_STREAM_CHECKPOINT_ABORTED`"，与 live `InputGate.java:87-89`（Stage 45：重叠 barrier 不再抛错，迟到/aborted barrier 被丢弃，§2.8.1 D1）及文档自身 D1 三方矛盾；`ERR_STREAM_CHECKPOINT_ABORTED` 在 InputGate 仅 import 无抛出点。
- `checkpoint-design.md:177`（§2.5 Snapshot 内容表）声明"watermark state = 输入 channel watermark 和 idle 标记"，但 `TaskEpochSnapshot`/`TaskStateSnapshot`/`EpochManifest` 无 watermark/idle 字段；checkpoint 两包对 watermark 零引用；唯一持久化点是 `HeapInternalTimerService.java:238` 的 task 级 currentWatermark（语义不符）。
- `nop-stream/README.md:7` 声称"DISTRIBUTED 模式规划通过 IStreamExecutionDispatcher 调度"，实际 `EmbeddedDistributedExecutor`/`RpcDistributedExecutor` 均实现 `IStreamExecutionDispatcher`，控制面 RPC（Stage 39）、数据面后端（Stage 40）、remote-deploy 多 JVM（Stage 42）全部落地含 E2E。
- `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/**`（17+ 类）驻留 `io.nop.stream.core.*` 命名空间；其余 9 个 nop-stream 子模块均遵守 `io.nop.<module>.*` 约定。依赖方向正确（rocksdb 依赖 core），违规点是实现类放置于 core 命名空间。

## Goals

- Debezium 首跑（无 checkpoint）不继承上次运行的静态 offset；未命名连接器不再共享 `_default_` 桶（fail-fast 或强制唯一 name）；补充"首跑不继承上一运行 offset"测试。
- checkpoint-design §2.4/§2.5 与 README 的文档契约与 live 行为一致（读者不会被误导）。
- nop-stream-rocksdb 类迁移到 `io.nop.stream.rocksdb.*` 命名空间，消除 split-package（结构性重构——需人工确认门通过后执行）。

## Non-Goals

- 不改变 Debezium engine 反射实例化机制（offset store 静态注册表是 2.4.0 约束下的桥接方案，保留机制，只修生命周期）。
- 不做 RocksDB 增量恢复的其他 P2 修复（AR-17/18/19/21，backlog）。
- 不处理 connector 族其余 P2（AR-04~09，backlog）。
- 不重写 checkpoint-design 全文；只修三处契约行。

## Scope

### In Scope

- AR-03：offset 注册表首跑清理 + 未命名连接器 fail-fast + 回归测试。
- P1-DOC-01：checkpoint-design §2.4 ALIGNING 行回写（Stage 45 行为）。
- P1-DOC-02：checkpoint-design §2.5 watermark state 行标注 spec-only / 未实现。
- P1-DOC-03：nop-stream/README DISTRIBUTED 状态回写。
- P1-01-01：rocksdb 命名空间迁移（人工确认门后执行）+ 编译/测试验证。

### Out Of Scope

- P2-INV-7（InputGate 通道水位进 checkpoint 的设计缺口）——与 DOC-02 同源但属功能实现，backlog。
- 平台级 bean/ioc 改动。

## Execution Plan

### Phase 1 - Debezium offset 注册表生命周期（AR-03，P1）

Status: planned
Targets: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/NopStreamOffsetBackingStore.java`（REGISTRY :56-57、clearConnector :92、configure :104-115、ensureBound :202-208）、`nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java`（initializeState :232-236、resolveConnectorName :260-266）、`TestDebeziumCdcCheckpoint.java`（含 MockCdcMessageSource mock 引擎 harness :236-242/:373-416）

- Item Types: `Decision | Fix | Fix | Fix | Fix`

- [ ] Decision: 裁定 `_default_` 桶语义——**采纳 fail-fast 为默认**（audit 首选建议，与 No-Silent-No-Op Rule #24 一致）：未配置 name 时抛异常并附 connector 配置指引；共享桶选项（Option B）显式拒绝并留痕理由（同 JVM 并发未命名 pipeline 静默互相覆盖 offset 无法可靠检测）。**注意：`NopStreamOffsetBackingStore.configure()`（:104-115）与 `ensureBound()`（:202-208）存在独立 `_default_` 回退，fail-fast 必须覆盖这两处**，否则引擎反射实例化仍可静默绑定 `_default_` 绕过源函数门。**异常类型裁定：`NopStreamOffsetBackingStore` 位于 nop-message-debezium 模块（pom 无 nop-stream-core 依赖），不可抛 `StreamException(ERR_STREAM_CONFIG_ERROR)`——store 侧用模块本地异常（`NopException` + 模块自有 ErrorCode 或等效模块异常类），源函数侧（connector-debezium，有 core 依赖）可用 `StreamException`**
- [ ] Fix: 首跑路径（**两个 fresh 分支都要清**：state==null 于 `:232-236`，以及 state 非 null 但 `CDC_OFFSETS_KEY` 缺失的 raw==null 于 `:238-244`——后者同样 `forConnector(resolveConnectorName())` 且同样继承陈旧 offset）先清理再绑定；**采用"首跑显式清理"变体而非改 `forConnector` fresh 语义**——`forConnector` 的"同连接器共享 backing map"契约是公开 API（javadoc :70-77），改 fresh 语义会破坏 `TestNopStreamOffsetBackingStore.testConnectorNameRegistrySharesDataAcrossInstances`（:97-108）与 `testConfigureWithWorkerConfigBindsToRegistry`（:126-143）
- [ ] Fix: 未命名连接器 fail-fast（三处回退点：`DebeziumCdcSourceFunction.resolveConnectorName` + `NopStreamOffsetBackingStore.configure` + `ensureBound`）
- [ ] Fix: 新增回归测试——同 JVM 内先跑连接器 A 产生 offset → 首跑场景下新作业从 snapshot/起点开始（断言不续跑，先红后绿，**两个 fresh 分支各一用例**）；未命名连接器 fail-fast 断言（三处回退点参数化）；**新增 `TestNopStreamOffsetBackingStore` 回归覆盖**（store 级用例不被破坏）
- [ ] Fix: `clearConnector` 的调用语义文档化（不再仅测试使用）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 首跑不继承陈旧 offset 的测试全绿（先红后绿证据在案；state==null 与 raw==null 两 fresh 分支各一用例）
- [ ] 未命名连接器 fail-fast 测试全绿（覆盖 `resolveConnectorName` + `configure` + `ensureBound` 三处回退点；store 侧断言模块本地异常类型）
- [ ] `TestDebeziumCdcCheckpoint` 与 `TestNopStreamOffsetBackingStore` 既有用例回归绿（含恢复路径与 mock 引擎用例、共享 map 契约用例；既有用例均显式设置连接器名，fail-fast 不破坏）
- [ ] **端到端验证**：CDC 源作业重启 E2E——**采用现有 mock 引擎 harness（`MockCdcMessageSource` + `TestDebeziumCdcCheckpoint` 模式，:236-242/:373-416，引擎级 `env.addSource → checkpoint → restart`）**实现 run1 消费产生 offset → 清理/首跑 → run2 从起点重新 snapshot（不跳过数据）；**注意：run1/run2 必须使用独立 `emittedRecordKeys` 集合断言**（run2 重发会使共享集合产生"重复"误判——现 `testCdcCheckpointKillRecoverNoDuplicates` 共享集合是正确断言；新测试断言 run2 首条发射 index==0）；若需真实 MySQL/Postgres，采用 `@EnabledIfSystemProperty` 门控模式（镜像 `TestDataPlaneKafkaBackendE2E`）并在 plan 日志记录
- [ ] **接线验证**：首跑清理调用确实在 `initializeState` 生产路径执行（代码审查或测试断言；两 fresh 分支均核实）
- [ ] 无静默跳过：清理/fail-fast 路径不吞异常
- [ ] `ai-dev/design/nop-stream/connector-design.md` §5.4（:363 起，§5.4.2 D1 + 接线裁定）**必须更新**——描述静态注册表机制的生命周期变更（首跑清理 + fail-fast），不得留旧语义
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 文档契约同步（P1-DOC-01/02/03）

Status: planned
Targets: `ai-dev/design/nop-stream/checkpoint-design.md`（§2.4 :101、§2.5 :177）、`nop-stream/README.md`（:7）

- Item Types: `Fix | Fix | Fix`

- [ ] Fix: `checkpoint-design.md` §2.4 ALIGNING 行：删除"重叠 barrier 抛 `ERR_STREAM_CHECKPOINT_ABORTED`"，改为"重叠 barrier id 按 in-flight 集合逐 id 对齐，迟到/被 abort 的 barrier 被丢弃（§2.8.1 D1）"（引用 `InputGate.java` Stage 45 行为）
- [ ] Fix: `checkpoint-design.md` §2.5 watermark state 行标注"spec-only / 未实现（对应 [P2-INV-7]）"；同步 §2.5 表格内其他行状态标注一致性核对
- [ ] Fix: `nop-stream/README.md:7` 更新为"LOCAL 经 GraphExecutionPlan + TaskExecutor；DISTRIBUTED 经 IStreamExecutionDispatcher（EmbeddedDistributedExecutor / RpcDistributedExecutor，含 Stage 39-42 跨 JVM 能力）"

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 三处文档与 live code 行为一致（核对 `InputGate.java:87-89`、`TaskEpochSnapshot` 无 watermark 字段、两个 executor 类存在）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [ ] 文档修订记录进 `ai-dev/logs/` 对应日期条目
- [ ] No code change required for this phase（纯文档）

### Phase 3 - RocksDB 命名空间迁移（P1-01-01，结构性重构）

Status: blocked（**执行门：人工确认**——mission Cross-Cutting「结构性重构（公共 API、模块边界、Operator 接口变更）执行前人工确认」。本 Phase 自 plan draft 起即标记 `blocked`，在获得用户/owner 明确批准前不得开始执行；未批准 → 按「取消 + 记录 scope 变更 + successor plan」路径处置，不得静默搁置）
Targets: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/**` → `io.nop.stream.rocksdb.*`

- Item Types: `Decision | Fix | Fix | Proof`

- [ ] Decision: 向用户/owner 提交命名空间迁移方案（迁移范围、包名映射、影响面）并取得明确批准（人工确认门）。影响面清单须基于 grep 实测：`io.nop.stream.core.common.state.backend.rocksdb` 全仓引用点（已知含 nop-stream-runtime 7 个测试类——nop-stream-runtime 以 test scope 依赖 rocksdb（pom :67-72），`-am` 从 rocksdb 模块不覆盖 runtime，**必须用 `-pl nop-stream -am` 验证**；nop-stream-rocksdb 自身 13 个测试类（9 root + 4 incremental））。**注意：`WindowedStreamImpl.java:162-165` 的反射类名是 core↔runtime 边界残留（P2-01-03），与本迁移无关，不列入影响面**；未批准则本 Phase 保持 blocked
- [ ] Fix: 迁移 `nop-stream-rocksdb` 全部类到 `io.nop.stream.rocksdb.*`（实现 core 接口无需同包；import/包声明/资源引用同步）
- [ ] Fix: 全仓引用点同步（按 Decision 影响面清单：其他模块 import、测试类包名）
- [ ] Proof: 迁移后 grep 复核 `io.nop.stream.core.common.state.backend.rocksdb` 全仓零残留（main + test）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 人工确认门通过（记录批准证据与影响面清单；未通过 → 本 Phase 按 blocked 处置记录，plan 不关闭）
- [ ] 迁移后 `nop-stream-rocksdb` 模块全部类在 `io.nop.stream.rocksdb.*` 下（grep 复核）
- [ ] 全仓编译通过（`./mvnw clean install -pl nop-stream -am -DskipTests`——**必须覆盖 nop-stream-runtime 的 test scope 依赖**）
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿（含 nop-stream-runtime 7 个 rocksdb 引用测试类 + rocksdb 模块 13 个测试类，覆盖 RocksDB 恢复/增量路径）
- [ ] **端到端验证**：RocksDB 后端作业 checkpoint 恢复 E2E（`TestRocksDBStateBackendE2E` / `TestE2EWindowAggregateRestore` / `TestStateMigrationEndToEnd` 等既有引擎级恢复 E2E）迁移后仍全绿
- [ ] No new test required: 纯包重定位（行为零变化；既有测试即验证），迁移后全量回归覆盖
- [ ] 无静默跳过：迁移不改变任何行为语义（纯包重定位；若发现行为差异立即记录）
- [ ] `docs-for-ai/01-repo-map/module-groups.md`（或 source-anchors）模块归属说明同步
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] AR-03（P1）已修复：首跑不继承陈旧 offset + 未命名连接器 fail-fast + 测试证据在案
- [ ] P1-DOC-01/02/03 已修复：三处文档与 live 行为一致 + doc-link-checker exit 0
- [ ] P1-01-01 已修复：人工确认门通过 + 命名空间迁移完成 + 测试证据在案（若人工确认未通过，本 gate 明确记录为 blocked 且 plan 不关闭）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）offset 清理/fail-fast 在运行时调用链连通，（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw test -pl nop-stream -am -T 1C`
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-connector-debezium --severity high` exit 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs 2026-08-13-1243-3-nop-stream-cross-module-contract-remediation.md --strict` exit 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0

## Deferred But Adjudicated

### InputGate 通道水位进 checkpoint（P2-INV-7，与 DOC-02 同源）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 已裁定 P2 backlog；本 plan 只把 §2.5 契约行标注为 spec-only/未实现，使文档诚实；功能落地不阻塞当前契约修复
- Successor Required: `no`

### RocksDB 增量恢复其他 P2（AR-17/18/19/21）

- Classification: `watch-only residual`（backlog 条目）
- Why Not Blocking Closure: P2 不驱动本 plan；命名空间迁移不改变这些路径的行为，维持 backlog 触发条件
- Successor Required: `no`

## Non-Blocking Follow-ups

- 命名空间迁移完成后评估"实现类归属 jar"可发现性（source-anchors / module-groups 同步）
- `resolveConnectorName` 的 `_default_` 桶最终语义（共享 vs 禁用）若选择 fail-fast，遗留对旧配置的迁移说明登记 backlog

## Closure

Status Note: 完成或关闭时填写
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
- 或明确写 no remaining plan-owned work
