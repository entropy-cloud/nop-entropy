# Checkpoint 恢复身份与完整性收口（AR-1[P0] + F-01/F-02/F-03/F-06/AR-10/AR-11）

> Plan Status: completed
> Mission: nop-stream-productization
> Last Reviewed: 2026-09-04
> Source: `ai-dev/audits/nop-stream-productization/2026-09-03-1951-open-audit-nop-stream-productization.md`（AR-1 [P0]、AR-10、AR-11）+ `ai-dev/audits/nop-stream-productization/2026-09-03-1951-multi-audit-nop-stream-productization.md`（F-01、F-02、F-03、F-06）
> Related: `2026-09-04-1326-2-dataplane-window-barrier-lifecycle.md`（{2}，本 plan 之后执行）、`2026-09-04-1326-3-connectors-trust-verification-surface.md`（{3}）
> Execution Order: {1}（含唯一 P0；F-06 修复恢复 checkpoint 执行路径、AR-1 修复作业身份隔离，是 {2}/{3} 恢复类测试可信基线的前置）
> Review: 三轮独立对抗性审查（fresh sessions `ses_f951920b1ffeXUwcKU9B1BSsRu` / `ses_f9509a13effeqYjCIi2iBvn3Jo` / `ses_f950110d4ffeWojS7dvuJjxhvo`）——首轮 1 Blocker（D4 键类通道缺失）+ 4 Major + 4 Minor 全修复；第二轮 9/9 RESOLVED + 新发现 1 Major（M-R2-1 合法恢复保全约束）+ 6 Minor 全修复；第三轮 M-R2-1/N1-N6 全 RESOLVED、无新 Blocker/Major，判定可 active。共识达成。

## Purpose

把 checkpoint/restore 的「恢复身份 = jobId + 键类 + future 语义 + 物理完整性」收口为正确且可信：跨作业状态污染被消除（AR-1，P0）、persist 失败不再悬挂 future（F-01）、文档化的 checkpoint 开关不再静默失效（F-06）、RocksDB 增量链路磁盘有界且物理损坏 fail-fast（F-02/F-03）、CEP 恢复后键可寻址且 timer 真实发射（AR-10/AR-11）。

## Current Baseline

（anchor 格式 `文件:行号`，核对日期 2026-09-04；全部来自两份 open 审计并经本轮 live 路径存在性复核）

**AR-1 [P0] 跨作业 checkpoint 状态污染（实跑复现在案）：**

- `env.execute("my-job")` 的作业名被忽略：`JobGraphGenerator.java:123` 硬编码 `new JobGraph("stream-job")`（注释宣称“来自 streamGraph”，与实现相悖）；`PartitionedPlanGenerator.java:64` 以该名作 jobId。
- 默认存储落机器级全局目录：`GraphModelCheckpointExecutor.java:1036-1040` 在无 path 配置时落 `${java.io.tmpdir}/nop-stream-checkpoints`；`restoreFromCheckpoint`（`:1043-1093`）发现存量产物即尝试恢复。**恢复守卫现状（精确）**：epoch manifest 路径 `:1061` 已有 `validateFingerprintCompatibility`（不同拓扑被 typed 拒绝 `:1119-1121`——quickstart 实跑「fingerprint componentCount 2 vs 7」失败即该守卫产物）；真正缺口 = manifest 无 fingerprint 时 skip（`:1104`）、CompletedCheckpoint 路径无校验（`:1083-1092`）、以及**作业身份本身**（指纹相同的无关作业静默继承——AR-1 修复目标是补作业身份层，不是重复实现指纹拒绝）。
- quickstart 模板 `checkpoint storageType="local"` 无 path（`quickstart/template/src/main/resources/_vfs/quickstart/topology2-window-aggregation.stream.xml:20-21`），官方教材直接继承该默认。
- 实跑证据（open 审计 2026-09-04）：multi-audit 基线运行残留的全局目录使 quickstart verify.sh 3 测试挂 2（`ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED`，fingerprint componentCount 2 vs 7）；清目录后 3/3 全绿。失败/被杀作业产物永久残留继续污染无关作业。
- 次级不一致：分布式执行器默认目录 `nop-stream-checkpoint`（单数，`EmbeddedDistributedExecutor.java:179`、`RpcDistributedExecutor.java:240`）与本地路径（复数）不一致。

**F-01 persist 失败悬挂 future：**

- `CheckpointCoordinator.java:935-953` 的 `onCompletePersistFailure` 只 `pending.getStatus().set(Status.FAILED)`（:948），不调 `PendingCheckpoint.fail(...)`（`PendingCheckpoint.java:175-186`，唯一 `completeExceptionally` 点，生产代码零调用方）；超时兜底 `abortPendingCheckpoint` 的 CAS `RUNNING→ABORTED`（:974）因状态已 FAILED 提前返回 → future 永不完成。
- 三个阻塞调用方（savepoint/DRAIN/SUSPEND/EXPORT，`GraphModelCheckpointExecutor.java:317-318,449-450`、`JobCoordinator.java:2131-2132`）均 `future.get(checkpointTimeout)`（默认 600000ms）→ 存储故障时阻塞满 10 分钟后抛误导性 TimeoutException。`TestAsyncSnapshotPipeline.java:382-392` 注释自证绕过该缺口。

**F-02/F-03 RocksDB 增量链路磁盘无界 + 物理完整性失守：**

- 每次快照新建 `{dbPath}-checkpoints/cp-{N}/`（`RocksDBIncrementalSnapshotStrategy.java:42-44,60-112`）；non-SST 文件每 checkpoint 完整拷贝（:98-102）；全仓无任何回收路径（coordinator GC 只清共享 `shared-state/`；javadoc :42-44 自认“lifecycle owned by caller/coordinator”但两侧均未实现）。restore 依赖本地 `cp-N/non-sst` 目录（`RocksDBIncrementalRestore.java:144`）→ 简单补删除不可行。
- restore 仅 `Files.exists`（:98）后直接拷贝，不复验内容 hash（写入侧已算 `SstFileChecksum.sha256Hex`，验证廉价）；`RocksDB.openReadOnly`（:164）未包 typed 包装。`LocalFileSegmentStore.storeSegment`（`nop-stream-core/.../checkpoint/storage/LocalFileSegmentStore.java:38-49`）非原子直写终名 `{hash}.sst` + coordinator `segmentExists(hash)` 短路（`CheckpointCoordinator.java:743-745`）→ 崩溃半写留永久截断文件。rocksdb 全部 17 个测试文件零物理损坏注入。

**F-06 `enableCheckpointing` 静默失效 + 死 services 配置：**

- `StreamExecutionEnvironment.java:296-301` 门控 `isCheckpointEnabled() && checkpointExecutorFactory != null` 静默落入无 checkpoint LOCAL 执行，无日志无异常；`META-INF/services/io.nop.stream.core.execution.ICheckpointExecutorFactory` 存在但全仓零 `ServiceLoader.load` 消费（死配置）。同文件 savepoint API（:380-424）对 null factory 均 fail-fast，唯独此门静默。用户指南快速起步（`docs-for-ai/03-modules/nop-stream-user-guide.md:22-31`）直接教 `enableCheckpointing(60_000)` 且零字提及工厂 → 用户以为有 exactly-once，实际零 checkpoint。

**AR-10/AR-11 CEP 恢复键类漂移：**

- `CepOperator.java:269,280` 以 `Object.class` 自建 keyed 后端（无任何生产调用方注入键类型），旁路平台 AR-01/AR-22 键重物化修复（`MemoryStateSerDe.java:717` 守卫 `keyType != Object.class` 才生效）→ 非 String 键（含默认非 keyed 路径的 `Byte` 键）恢复后 `nfaState/eventQueues/SharedBuffer` 落类漂移键下，运行时访问永远 miss，CEP 状态静默清零。对照正确范式 `WindowOperator.java:421,471`（`setKeyType(keyClass)`）。
- `CepOperator.java:470-481`（snapshot 裸存 key 对象经 JSON）→ `:501-509`（restore 原样 `computeIfAbsent`，小数值键回来变 `Integer`）→ `:536-543`（排水以漂移键跑 `onEventTime`）→ `:727-736`（真实键 timer 被删但从未发射）。值层面已有 `Integer/Long` 归一（:503 注释、`addAllTimerTimestamps` :655-664），键层面没有。

## Goals

- 两个不同拓扑的本地作业在同一台机器上先后运行，checkpoint 状态互不可见、互不污染（脏机器上 quickstart verify.sh 3/3 绿）。
- 同一 jobId 换拓扑重启时：要么干净失败（typed 错误），要么明确隔离——不允许静默继承旧状态。
- persist 失败时 savepoint/DRAIN/SUSPEND/EXPORT 的 future 在短时间内 exceptional 完成，错误即真实根因。
- 用户指南快速起步路径按文档执行即产生真实 checkpoint（不再依赖未披露的工厂静态 setter）。
- RocksDB 增量作业 task 本地目录有界；损坏 SST/MANIFEST/截断 segment 在恢复期 typed fail-fast。
- CEP 作业（含默认非 keyed `Byte` 键与 `Long` 键）checkpoint→restore 往返后：状态可寻址、pending timer 真实发射、无静默清零。

## Non-Goals

- 不改 checkpoint 协议语义（两阶段提交/aligned-unaligned/abort 语义零变更；AR-5/AR-6/AR-7/AR-8 归 {2}）。
- 不做 `.checkpoint` body checksum/MAC 与 JEP 290 反序列化 filter（F-10/F-09/F-08 归 {3}）。
- 不做 manifest 级 `stateFormatVersion`/`checksum` 字段（既有 roadmap item 25 归属，不重复）。
- 不引入 checkpoint 存储后端新实现（JDBC/Local 之外不加存储类型）。
- 不做多租户/命名空间级隔离产品化（只做作业身份正确性所需的最小隔离）。

## Scope

### In Scope

- `nop-stream-core`：`JobGraphGenerator` 作业名接线；`InputGate`/执行链无关；`JavaStreamSerializer` 不动。
- `nop-stream-runtime`：`GraphModelCheckpointExecutor` 默认路径与恢复守卫；`EmbeddedDistributedExecutor`/`RpcDistributedExecutor` 默认目录统一；`CheckpointCoordinator` persist 失败路径与 `segmentExists` 短路；`PendingCheckpoint` future 完成语义；`StreamExecutionEnvironment` checkpoint 门控；`META-INF/services` 消费或门控 fail-fast。
- `nop-stream-rocksdb`：增量快照本地目录回收（含 non-sst 持久化前置）；restore 期 segment hash 复验与 typed 包装。
- `nop-stream-core`：`LocalFileSegmentStore` 原子写。
- `nop-stream-cep`：`CepOperator` 键类接线与 timer 台账键类型化持久化；`PatternStreamBuilder`/`CEP.pattern` 入口（D4 通道）；如 D4 涉及 `KeyedStream` 键类通道则含 `nop-stream-core` datastream（跨模块公共 API 变更按 Protected Area 规则在裁定记录中体现）。
- `nop-stream/quickstart`：模板/脚本 per-run 临时目录与 freshness（AR-28 的 freshness 缺陷是 AR-1 的掩盖因素，仅修与污染相关的最小面）。
- owner docs：`docs-for-ai/03-modules/nop-stream.md`、`nop-stream-user-guide.md`、`ai-dev/design/nop-stream/checkpoint-design.md`（如裁定改变契约面）。

### Out Of Scope

- `docs-for-ai/INDEX.md` 行腐、BOM 卫生、测试卫生等 P2 项（已入 roadmap Follow-up Backlog）。
- RocksDB 增量模式的 toggle 化/生产接线推广（现状仅测试启用，blast radius 受限）。
- quickstart 跨平台脚本修缮（AR-27，P2 backlog）。

## Execution Plan

### Phase 1 - 作业身份与 checkpoint 存储隔离（AR-1，P0）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/jobgraph/JobGraphGenerator.java`、`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/graph/PartitionedPlanGenerator.java`、`EmbeddedDistributedExecutor.java`、`RpcDistributedExecutor.java`、`nop-stream/quickstart/`

- Item Types: `Fix | Decision | Proof`

- [x] **D1 隔离策略裁定（Decision）**：基于实跑证据裁定默认路径隔离机制：(a) 存储产物 manifest 记录作业身份 token（如 jobId + 拓扑指纹），恢复前校验身份不匹配即 typed 拒绝（无关作业静默继承被堵死）；(b) 默认目录按「执行实例」隔离（jobId + 运行 token 子目录），终态（成功完成）后清理自身产物。裁定需覆盖：失败/被杀作业残留产物对后续作业的影响面、与既有 `LocalFileCheckpointStorage` 布局兼容性、与 manifest 版本化既有机制（`CheckpointSerDe` canonical checksum，item 25 已落地）的衔接、**合法恢复保全约束（硬约束）：「同 jobId 同拓扑跨 run 自动恢复」的既有语义（fraud-example kill/recover 测试形态所依赖）必须保全，或其变更为显式裁定项单独落档——身份 token 不得引入启动时间戳这类逐 run 变化的成分来堵死合法恢复；「堵死」的对象是不同作业恰好同形的静默继承，不是自动恢复本身**。裁定 + 拒绝的替代方案落 `checkpoint-design.md`
- [x] **D1b 作业名→存储 jobId 映射裁定（Decision，D1 子项）**：作业名接线后真实作业名将进入存储路径，而 `LocalFileCheckpointStorage.validateId`（:325-330）强制 `[a-zA-Z0-9_-]+` 且 `env.execute()` 无参默认名是 `"Streaming Job"`（含空格，`StreamExecutionEnvironment.java:254-255`）——必须裁定映射语义：存储 jobId = 消毒后的作业名（非法字符映射规则）还是「作业名仅展示、存储 id 独立生成（如 run token）」；含 CJK/空格/点号作业名与 DSL 作业名全形态覆盖。裁定与 D1 同段落档
- [x] **Fix（作业名接线 + 名字映射）**：`JobGraphGenerator.java:123` 使用 streamGraph 真实作业名（消除注释与实现相悖）；`env.execute(name)` → JobGraph 名 → `PartitionedPlanGenerator` jobId 全链传递；同名 JobGraph 不再恒为 `"stream-job"`；按 D1b 落地存储 jobId 映射——无参 `execute()` 默认名与任意合法作业名在 Local/JDBC 存储下均不因 id 校验失败（`"Streaming Job"` 默认路径 checkpoint 不因本修复而炸）
- [x] **Fix（既有测试依赖盘点更新）**：作业名接线改变存储目录布局，3 个硬编码 `"stream-job"` 路径解析的 fraud-example 测试必须同轮更新：`TestS2OfflineReshardE2E.java:158,188`、`TestParallel2PcJdbcE2E.java:146`、`TestS1CdcRecoveryE2E.java:149`（现依赖「名字被忽略恒为 stream-job」才通过）——改为从作业名/配置推导路径；全库 `rg '"stream-job"'` 扫描确认测试与 main 代码零残留（生成路径与测试均清）
- [x] **Fix（默认路径隔离与恢复守卫）**：`GraphModelCheckpointExecutor.java:1036-1040` 默认路径按 D1 裁定实现隔离；`restoreFromCheckpoint`（:1043-1093）按 D1 补作业身份校验（缺口 = manifest 无 fingerprint 的 skip 路径、CompletedCheckpoint 路径、指纹相同的无关作业继承；**不重复实现已存在的指纹拒绝**）。**守卫场景区分**：默认路径（AR-1 污染场景，身份校验从严）与显式 path 配置（用户显式指定存储 = 显式恢复意图，如 fraud kill/recover 形态）的行为差异按 D1 裁定明确——收紧不得无条件波及显式恢复场景
- [x] **Fix（分布式默认目录统一）**：`EmbeddedDistributedExecutor.java:179`、`RpcDistributedExecutor.java:240` 的 `nop-stream-checkpoint`（单数）与本地 `nop-stream-checkpoints`（复数）统一为单一约定（含文档同步）
- [x] **Fix（quickstart per-run 隔离）**：quickstart 模板测试使用每次运行独立的 checkpoint 目录（`@TempDir` 或 jobId+时间戳），verify.sh 在脏机器（存在残留产物）下可重复全绿；verify.sh 的 jar freshness 判定从「存在即跳过」改为可强制重建（AR-28 掩盖因素，最小面修复）
- [x] **Proof（跨作业污染回归 + 合法恢复对偶）**：新测试钉死审计复现链——同机先后运行两个不同拓扑作业（默认配置），第二个作业不因第一个作业的残留而失败或继承状态；同 jobId 换拓扑重启按 D1 裁定干净失败或隔离；**对偶钉定项**：同 jobId **同拓扑**重启（含显式 path 场景）自动恢复正常（fraud kill/recover 形态不因本修复回归）。测试自带 finally 清理纪律（不留机器级全局目录污染，避免复现 AR-1 的测试残留问题）
- [x] **Proof（端到端）**：quickstart `verify.sh` 连续两次实跑（第二次在脏目录上）3/3 绿，输出留档 `_tmp/`

Exit Criteria:

- [x] 两个不同拓扑作业同机先后运行的隔离测试 + 同 jobId 同拓扑重启正常恢复的对偶测试存在且断言「无 `ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED` 污染失败 + 无状态继承 + 合法恢复不受损」（测试名含回归语义，可指认）
- [x] 同 jobId 换拓扑重启的行为有测试钉定（typed 拒绝或显式隔离，与 D1 裁定一致）
- [x] `rg -n '"stream-job"' nop-stream --type java` 全库零命中（生成路径硬编码与测试硬编码均消除）
- [x] 无参 `execute()` 默认名与含空格/CJK 作业名的 checkpoint 路径不因 id 校验失败（D1b 映射测试）
- [x] quickstart verify.sh 脏机器重跑 3/3 绿（执行记录落 daily log）
- [x] **无静默跳过**：恢复守卫拒绝路径抛 typed 错误（含指纹/身份参数），非静默忽略
- [x] owner docs 更新：`checkpoint-design.md`（D1 裁定）、`nop-stream.md`/`distributed-runbook`（默认目录约定）；`ai-dev/logs/` 已更新

### Phase 2 - persist 失败 future 收口 + checkpoint 门控诚实化（F-01、F-06）

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java`、`PendingCheckpoint.java`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/environment/StreamExecutionEnvironment.java`、`docs-for-ai/03-modules/nop-stream-user-guide.md`

- Item Types: `Fix | Decision | Proof`

- [x] **Fix（F-01 future 完成）**：`onCompletePersistFailure`（`CheckpointCoordinator.java:935-953`）使 persist 失败真实完成 future（exceptional）。**状态机事实（执行须知）**：persist 失败发生时 `completePendingCheckpoint` 已先 CAS `RUNNING→COMPLETED`（:538），此刻调 `PendingCheckpoint.fail(...)` 是非法转移（`isValidTransition`（:47-55）对 COMPLETED→FAILED 为 false，`checkValidTransition`（:57-62）抛 typed `StreamException`，且异常会在 :949-952 的 remove/decrement/notify 簿记清理**之前**炸出）——审计原文「改用 pending.fail」按字面实现比现状更糟。正确修法须新增强制失败路径（如 COMPLETED→FAILED 的显式 force-fail 转移，或放宽转移规则 + 保证 future 完成与簿记清理顺序：先簿记后完成）；future 完成即解除全部等待方（`future.get` 阻塞调用方与任何已注册回调）；超时兜底与该路径不冲突（FAILED 终态下 future 已完成）
- [x] **Proof（F-01 回归）**：测试断言 persist 失败（注入存储故障）后 savepoint/terminate future 在秒级（非 600s）exceptionally 完成，异常信息含真实根因；`TestAsyncSnapshotPipeline.java:382-392` 的绕过注释改写为真实断言
- [x] **D2 门控策略裁定（Decision）**：`enableCheckpointing` 工厂未接线时的行为——(a) 实现 `ServiceLoader.load` 消费既有 `META-INF/services/io.nop.stream.core.execution.ICheckpointExecutorFactory`（死配置复活，classpath 即插即用，与平台 beans 发现哲学对照记录取舍）；(b) 门控处 fail-fast（声明了 checkpoint 却无工厂 = 配置错误）。裁定依据含：quickstart 脚手架静态 setter 现路径、用户指南主路径、false-positive 风险。裁定落 `ai-dev/design/nop-stream/checkpoint-design.md` 或 owner doc
- [x] **Fix（F-06 按 D2 实施）**：消除静默跳过——按裁定 (a) 实现 ServiceLoader 发现（有 services 文件即自动接线）或 (b) `StreamExecutionEnvironment.java:296-301` 门控 fail-fast。语义统一口径：(a) 下用户指南快速起步路径按文档执行产生真实 checkpoint；(b) 下裸按现文档执行快速失败，且用户指南同轮补工厂接线说明（quickstart 脚手架的静态 setter 现路径文档化）——补齐说明后按指南执行同样产生真实 checkpoint。两种裁定收敛到同一验收：**用户指南快速起步路径不再有「以为有 checkpoint 实际没有」的形态**
- [x] **Proof（F-06 端到端）**：新测试模拟用户指南快速起步形态（无静态 setter、实现 jar 在 classpath），断言 checkpoint 真实发生（storage 产物存在/计数 >0）或按裁定快速失败

Exit Criteria:

- [x] F-01 回归测试存在且断言 future 短时 exceptional 完成 + 根因可见
- [x] F-06 裁定记录落档（含拒绝方案）；按裁定实施后用户指南主路径无「静默零 checkpoint」形态的端到端测试存在（(a) 断言产物/计数 >0；(b) 断言裸路径 typed 快速失败 + 补说明后产物存在）
- [x] **无静默跳过**：`enableCheckpointing` 声明后无任何路径静默落入无 checkpoint 执行（rg 门控点确认 fail-fast 或自动接线二选一）
- [x] owner docs 更新：user guide 快速起步（如需补工厂说明）、checkpoint-design（D2 裁定）；`ai-dev/logs/` 已更新

### Phase 3 - RocksDB 增量生命周期与物理完整性（F-02、F-03）

Status: completed
Targets: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/incremental/RocksDBIncrementalSnapshotStrategy.java`、`RocksDBIncrementalRestore.java`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/storage/LocalFileSegmentStore.java`、`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java`

- Item Types: `Fix | Decision | Proof`

- [x] **D3 回收策略裁定（Decision）**：task 本地 `cp-N/` 回收方案——(a) durable 持久化成功后回调 task 侧清理对应 cp 目录（前置：non-sst 伴生物先入共享存储，因 restore 依赖本地 non-sst 目录 `RocksDBIncrementalRestore.java:144`；**另须先验证 coordinator→task/backend 的清理通知通道存在——现有 `notifyCheckpointAborted` 通知的是 `listeners`（CheckpointListener，经 `GraphModelCheckpointExecutor:689-695` 注册的算子/UDF，`CheckpointCoordinator:1471-1479` 派发），task/backend 侧无清理通知，方案 (a) 裁定前必须确认通道可实现或定义新回调，不可裁定一个无法落地的方案**）；(b) backend 内保留最近 K 个本地目录滚动清理（自洽可回退）。裁定含 restore 正确性论证（所选方案下增量恢复全链仍成立）。裁定落 `checkpoint-design.md`
- [x] **Fix（F-02 按 D3）**：实现本地目录回收；长运行增量作业 task 本地磁盘占用有界
- [x] **Proof（F-02 有界性）**：测试跑 N>K 次（或 N 次以上）增量 checkpoint 后断言本地 cp 目录数量/总大小有界（不随 N 线性增长）
- [x] **Fix（F-03a restore 复验）**：`RocksDBIncrementalRestore.java:94-117` 恢复期对 segment 重算内容 hash 与写入侧 `SstFileChecksum.sha256Hex` 比对，不符即 typed `StreamException`（错误码 + 文件名/期望/实际参数）；`RocksDB.openReadOnly`（:164）native 异常包 typed 包装（含根因）
- [x] **Fix（F-03b 原子写）**：`LocalFileSegmentStore.storeSegment`（:38-49）改 temp 文件 + atomic move；coordinator `segmentExists(hash)` 短路（`CheckpointCoordinator.java:743-745`）不再把「同名文件存在」当「完整 segment 存在」（如先写后 move 则存在即完整；短路保留时须论证）
- [x] **Proof（F-03 损坏注入）**：新故障注入测试三例——截断 MANIFEST、翻转 SST 字节、半写 segment（模拟崩溃残留）——均 typed fail-fast（错误码断言，非 native/untyped 异常，非静默错数据）

Exit Criteria:

- [x] F-02 有界性测试与 F-03 三例损坏注入测试存在且全绿（测试类可指认）
- [x] D3 裁定落档且 restore 正确性论证成立（增量恢复 e2e 既有测试零回归）
- [x] **无静默跳过**：损坏输入在恢复期抛 typed 错误；`storeSegment` 无直写终名路径
- [x] `./mvnw test -pl nop-stream/nop-stream-rocksdb -am` 全绿
- [x] owner docs 更新：`checkpoint-design.md`（D3 + 完整性守卫语义）；`ai-dev/logs/` 已更新

### Phase 4 - CEP 恢复键类与 timer 台账（AR-10、AR-11）

Status: completed
Targets: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java`、`nop-stream/nop-stream-cep/.../PatternStreamBuilder.java` 与 `CEP.pattern` 入口、`nop-stream-core/.../datastream/KeyedStream*.java`（键类通道）、`nop-stream-core/.../MemoryStateSerDe.java`（如需守卫扩展）

- Item Types: `Fix | Decision | Proof`

- [x] **D4 键类通道选型（Decision，先于 AR-10 实施）**：live 事实：`KeyedStream`/`KeyedStreamImpl` 只携带 `KeySelector` 无 `Class<KEY>` 通道，`CepOperator` 构造器无 keyClass 参数，`PatternStreamBuilder.build()` 无从获得键类；被引为范式的 `WindowOperator` 在生产路径拿到的也是 `Object.class`（`WindowedStreamImpl` 四路径均传 `Object.class`——F-05 在 {2} 修）——即**键类通道在代码库中不存在，必须先设计**。裁定方案：(a) API 扩展（`CEP.pattern`/`KeyedStream` 携带可选键类，DSL `stream.xdef` 增加 keyType 属性）；(b) 运行时首键捕获（首个键到达时物化类并写入后端，空键作业回落显式默认）；(c) 仅 CEP 侧显式 setter + DSL 声明。裁定维度：公共 API 面（nop-stream-core 变更）、DSL 契约、非 keyed 默认路径（`NullByteKeySelector` → `Byte.class`）覆盖、恢复期后端重建时序（键类须在 restore 前可用——(b) 需论证首键早于状态恢复）。裁定 + 拒绝方案落 `ai-dev/design/nop-stream/cep-design.md`（如存在）或 `checkpoint-design.md`；DSL 路径（`AdvancedTransforms.buildCep` 经 `CEP.pattern`）是否纳入本轮一并裁定
- [x] **Fix（AR-10 键类接线，按 D4）**：`CepOperator.java:269,280` 的 keyed 后端创建携带真实键类（非 keyed 全局路径回落 `Byte.class`），通道按 D4 落地；使 `MemoryStateSerDe` 键重物化守卫（:717）对 CEP 生效
- [x] **Fix（AR-11 台账键类型化；依赖 AR-10 先落地——后端键类正确是台账键归一的前提，同 Phase 内按序执行）**：timer 台账（`CepOperator.java:470-481` snapshot / `:501-509` restore）的键以类型化形式持久化（类名 + 规范表示，恢复时重物化）；「恢复时与运行时活键归一」选项仅在 AR-10 已落地后可用（否则恢复出的"活键"本身是漂移键，循环依赖）；排水（:536-543）只对后端中真实存在的键执行（存在性以后端键查询为准），漂移键不再消费真实键的 timer
- [x] **Proof（AR-10 恢复可寻址）**：checkpoint→restore 往返测试（`Byte` 键默认路径 + `Long` 键 keyed 路径）：恢复后 `nfaState/eventQueues` 以原始键类型可寻址，部分匹配不静默清零
- [x] **Proof（AR-11 timer 真实发射）**：恢复后水位排水触发 pending timer 的 `onEventTime`（断言匹配产出/台账清空），排队事件不滞留
- [x] **Proof（端到端）**：CEP 模式作业（quickstart topology3 形态或等价 keyed CEP）带 kill/restore 的 e2e：恢复后继续正确匹配（无重复、无丢失、无静默重置）

Exit Criteria:

- [x] AR-10/AR-11 各有往返/排水测试且全绿；CEP kill/restore e2e 存在且断言恢复后匹配连续性
- [x] D4 键类通道裁定落档（含拒绝方案与 DSL 路径裁定）；`CepOperator` 后端创建无 `Object.class` 键（rg 验证）；台账键持久化格式含类型信息（代码可指认）
- [x] **接线验证**：`CepOperator` 键类确实经 `createKeyedStateBackend` 传至 `MemoryStateSerDe` 重物化守卫路径（测试断言恢复键 `equals` 原始键且 `getClass()` 一致）
- [x] `./mvnw test -pl nop-stream/nop-stream-cep -am` 全绿
- [x] owner docs：`ai-dev/design/nop-stream/cep-design.md`（如存在）或 `checkpoint-design.md` 补 CEP 键类恢复语义；`ai-dev/logs/` 已更新

## Closure Gates

- [x] AR-1（P0）：跨作业污染回归测试 + 脏机器 quickstart 3/3 绿留档；作业名硬编码消除
- [x] F-01：persist 失败 future 短时 exceptional 完成回归测试
- [x] F-02：本地目录有界性测试
- [x] F-03：三例损坏注入 typed fail-fast 测试 + storeSegment 原子化
- [x] F-06：门控裁定落档 + 用户指南主路径产生真实 checkpoint 的端到端测试
- [x] AR-10/AR-11：CEP 恢复可寻址 + timer 发射测试 + kill/restore e2e
- [x] 三项裁定（D1/D2/D3）+ D1b/D4 均落档（D1/D1b/D2/D3 落 `checkpoint-design.md`；D4 落 `checkpoint-design.md` 或 `cep-design.md` 之一，与 Phase 4 裁定一致；均含拒绝方案）
- [x] 无任何 P0/P1 发现被降级为 follow-up
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [x] checkstyle 通过
- [x] 独立子 agent closure audit 完成 + Anti-Hollow 检查（调用链连通 + 无空壳）+ evidence 写入

## Deferred But Adjudicated

（无——本 plan 无 deferred 项；全部 7 项发现均为 in-scope Fix。）

## Non-Blocking Follow-ups

- RocksDB 增量 `RocksDBException` 一律解释为「仅默认列族」的留证缺口（F-18，P2，backlog 登记）
- restore 期「损坏段长期潜伏才爆发」的可观测性增强（告警/metrics 维度）——非阻塞，随 item 25 manifest 校验和一并考虑

## Closure

Status Note: 四 Phase 全部执行完毕且 Exit Criteria 逐条成立——AR-1（P0）作业身份/存储隔离（作业名全链接线 + StorageJobIds 单射映射 + 默认目录禁用自动恢复 + 默认基目录可覆写；脏机器 quickstart 双跑 3/3 绿）；F-01 persist 失败 future 短时 exceptional 完成（forceFail 显式转移）；F-02/F-03 RocksDB task 本地目录滚动有界 + 恢复期 hash 复验/native typed 包装/原子写 + 三例损坏注入 typed fail-fast；F-06 门控诚实化（ServiceLoader 复活 + 声明后 fail-fast，朴素作业 LOCAL 回落保留）；AR-10/AR-11 CEP 键类三通道（Byte 钉死/首 key 捕获/checkpoint 携带）+ 台账类型化（timer 真实发射）。五项裁定 D1/D1b/D2/D3/D4 落档含拒绝方案。无 P0/P1 降级；Deferred 区为空。两份源审计（open/multi）的 P0/P1 分属 plans {1}/{2}/{3}，本 plan 仅收口 {1} 的 7 项——审计整体 closure 由 {2}/{3} 完成后统一处理。
Completed: 2026-09-04

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent fresh session `ses_f93ee52c3ffeMFyblR59FX8sIQ`（task_id），实现者 session 之外的单次 closure-audit pass
- Evidence:
  - 26/26 checkpoints PASS（Phase 1 ×7 / Phase 2 ×5 / Phase 3 ×5 / Phase 4 ×5 / Closure Gates ×4），逐条 live file:line 或测试结果锚点（如 `StreamExecutionEnvironment.java:366` 作业名接线、`GraphModelCheckpointExecutor.java:237-246/:1080-1083` 默认路径守卫、`PendingCheckpoint.java:205-223` forceFail、`CepOperator.java:756-767` 键类解析、`LocalFileSegmentStore.java:56-68` 原子写、`RocksDBIncrementalRestore.java:102-126` hash 复验）
  - verdict: **CLOSURE-AUDIT: APPROVED**（0 Blocker / 0 Major / 3 Info——Info 1 = 收口仪式本身；Info 2/3 = 已在 §8.1.6/§9 裁定记录的 fallback，非缺陷）
  - audit 现场复跑：`TestCheckpointJobIdentityIsolationE2E` 4/4、`TestStreamExecutionEnvironmentCheckpointGateFailFast` 3/3、`TestCheckpointGateServiceLoaderE2E` 1/1、`TestAsyncSnapshotPipeline` 12/12、`TestRocksDBIncrementalLifecycleAndIntegrity` 6/6、rocksdb 全模块 114/114、`TestCepKeyClassRecovery` 3/3 + cep 全模块全绿、`check-nop-stream-invariants`/`check-doc-links --strict`/`scan-hollow-implementations`（runtime+cep）全 exit 0
  - Anti-Hollow：三条调用链 live 追踪连通——execute→requireCheckpointExecutorFactory→factory.executeWithCheckpoint；CepOperator.restoreState 读 KEY_CLASS_STATE_NAME 先于 open() 建后端（顺序由测试钉定）；onCompletePersistFailure→forceFail→completeExceptionally
  - `rg -n '"stream-job"' nop-stream --type java` 零命中（audit 现场执行）
  - quickstart 双跑日志留档 `_tmp/quickstart-verify-run{1,2}.log`（3/3 绿 ×2，audit 现场读取确认）
  - 实现者侧终态验证：`./mvnw clean install -pl nop-stream -am -T 1C -DskipTests`（checkstyle 随构建）+ `./mvnw test -pl nop-stream -am -T 1C` 双 BUILD SUCCESS

Follow-up:

- RocksDB 增量 `RocksDBException` 一律解释为「仅默认列族」的留证缺口（F-18，P2，已在 roadmap Follow-up Backlog 登记）
- restore 期「损坏段长期潜伏才爆发」的可观测性增强（告警/metrics 维度）——随 item 25 manifest 校验和一并考虑（non-blocking）
- plans {2}/{3}（`2026-09-04-1326-2-dataplane-window-barrier-lifecycle.md` / `2026-09-04-1326-3-connectors-trust-verification-surface.md`）承接两份源审计的其余 P0/P1 项，非本 plan 义务
