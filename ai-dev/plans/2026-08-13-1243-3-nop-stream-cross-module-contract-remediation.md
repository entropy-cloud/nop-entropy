# 3 跨模块契约修复：Debezium offset 注册表陈旧续跑（P1）+ checkpoint/README 文档漂移（P1×3）+ RocksDB 命名空间迁移（P1）

> Plan Status: completed
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

Status: completed
Targets: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/NopStreamOffsetBackingStore.java`（REGISTRY :56-57、clearConnector :92、configure :104-115、ensureBound :202-208）、`nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java`（initializeState :232-236、resolveConnectorName :260-266）、`TestDebeziumCdcCheckpoint.java`（含 MockCdcMessageSource mock 引擎 harness :236-242/:373-416）

- Item Types: `Decision | Fix | Fix | Fix | Fix`

- [x] Decision: 裁定 `_default_` 桶语义——**采纳 fail-fast 为默认**（audit 首选建议，与 No-Silent-No-Op Rule #24 一致）：未配置 name 时抛异常并附 connector 配置指引；共享桶选项（Option B）显式拒绝并留痕理由（同 JVM 并发未命名 pipeline 静默互相覆盖 offset 无法可靠检测）。**注意：`NopStreamOffsetBackingStore.configure()`（:104-115）与 `ensureBound()`（:202-208）存在独立 `_default_` 回退，fail-fast 必须覆盖这两处**，否则引擎反射实例化仍可静默绑定 `_default_` 绕过源函数门。**异常类型裁定：`NopStreamOffsetBackingStore` 位于 nop-message-debezium 模块（pom 无 nop-stream-core 依赖），不可抛 `StreamException(ERR_STREAM_CONFIG_ERROR)`——store 侧用模块本地异常（`NopException` + 模块自有 ErrorCode 或等效模块异常类），源函数侧（connector-debezium，有 core 依赖）可用 `StreamException`**
- [x] Fix: 首跑路径（**两个 fresh 分支都要清**：state==null 于 `:232-236`，以及 state 非 null 但 `CDC_OFFSETS_KEY` 缺失的 raw==null 于 `:238-244`——后者同样 `forConnector(resolveConnectorName())` 且同样继承陈旧 offset）先清理再绑定；**采用"首跑显式清理"变体而非改 `forConnector` fresh 语义**——`forConnector` 的"同连接器共享 backing map"契约是公开 API（javadoc :70-77），改 fresh 语义会破坏 `TestNopStreamOffsetBackingStore.testConnectorNameRegistrySharesDataAcrossInstances`（:97-108）与 `testConfigureWithWorkerConfigBindsToRegistry`（:126-143）
- [x] Fix: 未命名连接器 fail-fast（三处回退点：`DebeziumCdcSourceFunction.resolveConnectorName` + `NopStreamOffsetBackingStore.configure` + `ensureBound`）
- [x] Fix: 新增回归测试——同 JVM 内先跑连接器 A 产生 offset → 首跑场景下新作业从 snapshot/起点开始（断言不续跑，先红后绿，**两个 fresh 分支各一用例**）；未命名连接器 fail-fast 断言（三处回退点参数化）；**新增 `TestNopStreamOffsetBackingStore` 回归覆盖**（store 级用例不被破坏）
- [x] Fix: `clearConnector` 的调用语义文档化（不再仅测试使用）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 首跑不继承陈旧 offset 的测试全绿（先红后绿证据在案；state==null 与 raw==null 两 fresh 分支各一用例）
- [x] 未命名连接器 fail-fast 测试全绿（覆盖 `resolveConnectorName` + `configure` + `ensureBound` 三处回退点；store 侧断言模块本地异常类型）
- [x] `TestDebeziumCdcCheckpoint` 与 `TestNopStreamOffsetBackingStore` 既有用例回归绿（含恢复路径与 mock 引擎用例、共享 map 契约用例；既有用例均显式设置连接器名，fail-fast 不破坏）
- [x] **端到端验证**：CDC 源作业重启 E2E——**采用现有 mock 引擎 harness（`MockCdcMessageSource` + `TestDebeziumCdcCheckpoint` 模式，:236-242/:373-416，引擎级 `env.addSource → checkpoint → restart`）**实现 run1 消费产生 offset → 清理/首跑 → run2 从起点重新 snapshot（不跳过数据）；**注意：run1/run2 必须使用独立 `emittedRecordKeys` 集合断言**（run2 重发会使共享集合产生"重复"误判——现 `testCdcCheckpointKillRecoverNoDuplicates` 共享集合是正确断言；新测试断言 run2 首条发射 index==0）；若需真实 MySQL/Postgres，采用 `@EnabledIfSystemProperty` 门控模式（镜像 `TestDataPlaneKafkaBackendE2E`）并在 plan 日志记录
- [x] **接线验证**：首跑清理调用确实在 `initializeState` 生产路径执行（代码审查或测试断言；两 fresh 分支均核实）
- [x] 无静默跳过：清理/fail-fast 路径不吞异常
- [x] `ai-dev/design/nop-stream/connector-design.md` §5.4（:363 起，§5.4.2 D1 + 接线裁定）**必须更新**——描述静态注册表机制的生命周期变更（首跑清理 + fail-fast），不得留旧语义
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 文档契约同步（P1-DOC-01/02/03）

Status: completed
Targets: `ai-dev/design/nop-stream/checkpoint-design.md`（§2.4 :101、§2.5 :177）、`nop-stream/README.md`（:7）

- Item Types: `Fix | Fix | Fix`

- [x] Fix: `checkpoint-design.md` §2.4 ALIGNING 行：删除"重叠 barrier 抛 `ERR_STREAM_CHECKPOINT_ABORTED`"，改为"重叠 barrier id 按 in-flight 集合逐 id 对齐，迟到/被 abort 的 barrier 被丢弃（§2.8.1 D1）"（引用 `InputGate.java` Stage 45 行为）
- [x] Fix: `checkpoint-design.md` §2.5 watermark state 行标注"spec-only / 未实现（对应 [P2-INV-7]）"；同步 §2.5 表格内其他行状态标注一致性核对
- [x] Fix: `nop-stream/README.md:7` 更新为"LOCAL 经 GraphExecutionPlan + TaskExecutor；DISTRIBUTED 经 IStreamExecutionDispatcher（EmbeddedDistributedExecutor / RpcDistributedExecutor，含 Stage 39-42 跨 JVM 能力）"

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 三处文档与 live code 行为一致（核对 `InputGate.java:87-89`、`TaskEpochSnapshot` 无 watermark 字段、两个 executor 类存在）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] 文档修订记录进 `ai-dev/logs/` 对应日期条目
- [x] No code change required for this phase（纯文档）

### Phase 3 - RocksDB 命名空间迁移（P1-01-01，结构性重构）——取消（scope 变更，人工确认门未通过）

Status: cancelled（**执行门：人工确认**——mission Cross-Cutting「结构性重构（公共 API、模块边界、Operator 接口变更）执行前人工确认」。本 Phase 自 plan draft 起即 `blocked`；截至收口（2026-08-13，含第 1/2 次复跑与本次独立 closure audit 复核）全仓（logs / plans / git）仍无用户/owner 批准记录 → 按本 Phase 预设路径「**取消 + 记录 scope 变更 + successor plan**」处置，不得静默搁置）
Targets: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/**` → `io.nop.stream.rocksdb.*`（迁移工作移交 successor，本次未执行，零代码变更）

- Item Types: `Decision | Fix | Fix | Proof`（原 4 个执行项按取消路径全部处置，见下）

- [x] Decision: 人工确认门未通过（全仓无批准记录）→ 按预设路径裁定：本 Phase 取消 + 记录 scope 变更 + successor plan 登记（**不静默搁置**）。影响面清单已 grep 实测在案（rocksdb main 17+ 类 + test 13 类（9 root + 4 incremental）；nop-stream-runtime 以 test scope 依赖 rocksdb（pom :67-72）含 7 个引用测试类，验证须 `-pl nop-stream -am`；`WindowedStreamImpl.java:162-165` 反射类名 = core↔runtime 边界残留 P2-01-03 与本迁移无关不列入）——清单即 successor 触发输入
- [x] Fix: 命名空间迁移（原执行项）→ **移交 successor 所有权**：迁移方案 + 影响面清单已记录于本 plan 与 `ai-dev/logs/2026/08-13.md` + roadmap backlog（P1-01-01 条目），待用户/owner 批准后由 successor plan 执行；本次零代码变更（grep 实证 rocksdb 类仍驻留 `io.nop.stream.core.common.state.backend.rocksdb`）
- [x] Fix: 全仓引用点同步（原执行项）→ 并入 successor 范围（Decision 影响面清单即 successor 输入），本次未执行
- [x] Proof: 迁移后 grep 复核零残留（原执行项）→ 收口时点改证：`io.nop.stream.core.common.state.backend.rocksdb` 主目录仍存在（17+ 类），迁移确未发生，与「取消 + successor 登记」记录一致；零残留复核由 successor plan 在迁移后执行

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。取消的 Phase 按本 Phase 预设路径（取消 + scope 变更 + successor）验收。

- [x] 人工确认门未通过 → 已按预设路径处置（Phase 取消 + scope 变更记录 + successor 登记），非静默搁置；未获得批准前迁移不执行（与「未批准 → 取消」预设路径一致，无静默降级）
- [x] 迁移未执行且证据在案：grep 实证 rocksdb 类仍驻留 core 命名空间（零代码变更），与取消裁定一致
- [x] successor 登记完成：roadmap backlog 新增 P1-01-01 条目（含触发条件 = 用户/owner 批准迁移方案）+ `ai-dev/logs/2026/08-13.md` 记录在案（plan / roadmap / daily log 三处一致）
- [x] 无静默跳过：取消理由、scope 变更、successor 归属全部书面记录；P1-01-01 未降级为 non-blocking follow-up
- [x] No owner-doc update required: 未执行迁移，`docs-for-ai/01-repo-map/module-groups.md` / source-anchors 无需同步（由 successor plan 迁移后同步）
- [x] `ai-dev/logs/` 对应日期条目已更新（2026-08-13：Phase 3 取消 + successor 登记）

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] AR-03（P1）已修复：首跑不继承陈旧 offset（两 fresh 分支）+ 未命名连接器 fail-fast（三处回退点）+ 回归/E2E 测试证据在案
- [x] P1-DOC-01/02/03 已修复：三处文档与 live 行为一致 + doc-link-checker exit 0
- [x] P1-01-01 已按本 Phase 预设路径处置：人工确认门未通过（全仓无批准记录）→ Phase 取消 + scope 变更记录 + successor 登记（roadmap backlog P1-01-01 条目），非静默搁置；迁移由 successor plan 在用户/owner 批准后执行
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（P1-01-01 走显式 scope 变更 + successor 所有权，非降级；P2-INV-7 / AR-17/18/19/21 为既存 backlog 条目维持 watch-only）
- [x] 受影响的 owner docs 已同步到 live baseline（Phase 1: `connector-design.md` §5.4；Phase 2: `checkpoint-design.md` §2.4/§2.5 + `nop-stream/README.md`；Phase 3 明确 No owner-doc update required——未执行迁移）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（见 `## Closure` 段）
- [x] **Anti-Hollow Check**：closure audit 已验证（a）offset 清理/fail-fast 在运行时调用链连通（`initializeState` 两 fresh 分支 → `newFreshOffsetStore` → `clearConnector`/`resolveConnectorName`；store `configure`/`ensureBound` 无 `_default_` 回退），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw test -pl nop-stream,nop-message/nop-message-debezium -am -T 1C`（2026-08-13 执行 + 第 2 次复跑复核：BUILD SUCCESS 0 failures/0 errors）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-connector-debezium --severity high` exit 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs 2026-08-13-1243-3-nop-stream-cross-module-contract-remediation.md --strict` exit 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0

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

Status Note: Phases 1-2 全部落地并经第 2 次复跑复核 full-green（AR-03 首跑清理 + 三处 fail-fast + 回归/E2E 测试；P1-DOC-01/02/03 文档契约回写 + doc-link-checker exit 0）；Phase 3（P1-01-01 rocksdb 命名空间迁移）人工确认门未通过（全仓 logs/plans/git 均无批准记录，第 1/2 次复跑与本次审计三次确认）→ 按本 Phase 预设路径「取消 + 记录 scope 变更 + successor plan」处置并登记，非静默搁置。三 Phase 全部落定（completed × 2 + cancelled × 1），plan 可关闭。
Completed: 2026-08-13

Closure Audit Evidence:

- Reviewer / Agent: mission-driver closure auditor（独立 closure-audit pass，fresh session，prompt MISSION_DRIVER:2026-08-13-080516-mission-driver）
- Audit Session: mission-driver plan-execution subflow CLOSURE_AUDIT（CLOSURE_SCRIPT_CHECK FAIL 后独立复检）
- Evidence:
  - **Phase 1（PASS）**：live 代码核对——`NopStreamOffsetBackingStore.java` `clearConnector` :101 + `configure` :123-126 抛 `NopException(ERR_DEBEZIUM_CONNECTOR_NAME_REQUIRED)`（`_default_` 回退已移除）+ `ensureBound` :214-221 同抛；`DebeziumCdcSourceFunction.java` `newFreshOffsetStore` :273-276（resolveConnectorName + clearConnector 两 fresh 分支 :241/:250 均调用）+ `resolveConnectorName` :279-286 抛 `StreamException(ERR_STREAM_CONFIG_ERROR)`；测试名 grep 命中 7/7（`TestDebeziumCdcCheckpoint` 5 新用例含 E2E `testFirstRunRestartStartsFromBeginning` + `TestNopStreamOffsetBackingStore` 2 新用例）；构建证据 = daily log `ai-dev/logs/2026/08-13.md`（`./mvnw test -pl nop-stream,nop-message/nop-message-debezium -am -T 1C` BUILD SUCCESS 0 failures，`TestDebeziumCdcCheckpoint` 12/0 + `TestNopStreamOffsetBackingStore` 7/0；第 2 次复跑含 TOCTOU 竞态修复后 24/0 + 全量绿）
  - **Phase 2（PASS）**：live 文档核对——`checkpoint-design.md:101`（§2.4 ALIGNING 行 = 重叠 barrier 逐 id 对齐 + 迟到/aborted 丢弃，`ERR_STREAM_CHECKPOINT_ABORTED` 不再由 InputGate 抛出）+ `checkpoint-design.md:179`（§2.5 watermark state 行 = spec-only/未实现 + [P2-INV-7] 引用）+ `nop-stream/README.md:7`（LOCAL/DISTRIBUTED 经 `IStreamExecutionDispatcher`）；`check-doc-links.mjs --strict` exit 0（daily log 记录）
  - **Phase 3（cancelled，PASS 按预设路径验收）**：grep 实证 rocksdb 类仍驻留 `io.nop.stream.core.common.state.backend.rocksdb`（17+ 类，主目录存在）→ 迁移未执行 = 与取消裁定一致（零代码变更）；roadmap backlog 新增 P1-01-01 条目（触发条件 = 用户/owner 批准迁移方案）+ daily log 记录在案
  - **Closure Gates 11/11**：逐条核对（见上节），其中 check-plan-checklist --strict exit 0（本 run 复跑）、doc-link-checker exit 0、scan-hollow `--module nop-stream-connector-debezium --severity high` exit 0（daily log 记录）
  - **Anti-Hollow Check**：调用链追踪 = `DebeziumCdcSourceFunction.initializeState`（:241/:250 两 fresh 分支）→ `newFreshOffsetStore()`（:273-276）→ `NopStreamOffsetBackingStore.clearConnector(name)` + `forConnector(resolveConnectorName())`；未命名路径在源函数（:286）、store `configure`（:125）、`ensureBound`（:220）三处显式抛异常，无 `_default_` 静默绑定残留；无空方法体/continue/吞异常作为正常实现
  - **Deferred 项分类检查**：P1-01-01 = 显式 scope 变更 + successor 所有权（非 deferred 非 follow-up 降级）；Deferred But Adjudicated 仅含 P2-INV-7（watch-only residual）与 AR-17/18/19/21（backlog 条目），均为既存 P2 条目，non-blocking 理由在案

Follow-up:

- P1-01-01 rocksdb 命名空间迁移 = **successor 所有权**（roadmap backlog 条目，触发条件 = 用户/owner 批准迁移方案；批准后由新 plan 执行，影响面清单已在 Phase 3 Decision 记录）
- `resolveConnectorName` 的 `_default_` 桶旧配置迁移说明登记 backlog（fail-fast 落地后既有未命名配置需显式改名）
- no remaining plan-owned work（本 plan 范围内全部落定：landed × 2 Phase + cancelled × 1 Phase 记录在案）
