# 2325 nop-stream R6 P1：fail-closed 错误路径与测试保护族（AR-01/CON-01/ST-03/TE-01）

> Plan Status: completed
> Last Reviewed: 2026-10-06
> Source: ai-dev/audits/2026-10/2026-10-06-0522-adversarial-review-nop-stream/01-open-findings.md（AR-01 复核 P1）、03-connectors-security.md（CON-01 复核 P1，R5 CON-08 已登记未修复）、02-state-backend-serialization.md（ST-03 复核 P1）、06-test-effectiveness.md（TE-01 复核 P1）
> Related: ai-dev/plans/2315-nop-stream-r5-connector-security.md（CON-04/08 半回退背景）、2322-nop-stream-r6-p0-e2e-red-baseline.md

## Adjudication（计划审查后的关键裁定）

- **CON-01 机制选方案 (b)：pending 条目来源判别**。jobId 命名空间不构成默认路径的运行代际边界（LOCAL 缺省 "job-0"，重提交同名 jobId 是合法运维流——命名空间方案会产出"测试用不同 jobId 假绿、默认路径原样敞开"的空修；隔离写入方案还牵连 final 文件名盘上契约，JDBC ledger 类比不完备）。修复 = manifest 撞键守卫按 pending 条目来源判别：经恢复入口 `setPendingCommits` 进入的条目 = recover-safe（保持 WARN 跳过，现行为）；本 run saveState 新建的条目 = 撞键 → typed throw（不再静默丢弃）。逻辑内聚 sink、键形状与盘上契约不变。
- **MAJOR-2 工作量入计划**：既有 3 个测试以裸 `getPendingCommits().put(...)` 模拟恢复（testIdempotentCommitNoDuplicateRename:204、testCommitFinalExistsManifestMissingRepairsManifest:225、testFileSinkKillRecoverExactlyOnce:290）——来源判别守卫会把裸 put 条目视为本 run 新建而抛错。这 3 个测试必须迁移到诚实恢复入口 `setPendingCommits`（本身是测试质量改进）。
- **ST-03 方向=写侧同类校验前置**（审计选项 a）：恢复侧放行需 provenance 元数据扩攻击面、系统属性逃生口属新配置面，均违背 Non-Goal；写侧前置把"checkpoint 成功→恢复必败"前移为写侧响亮失败。校验字段集与恢复侧读取的 header 严格同集（valueType/mapKeyType/keyType/accumulatorType/aggregateFunctionType）。**Scope 补 RocksDB 面**（R6 审计 ST-03 明确含 RocksDBSnapshotSerDe:666-680 写侧 :84/:182/:280）——只修 Memory 则 ST-03 对 RocksDB 未闭合。ST-07（恢复侧 keyType 裸 Class.forName 绕过，P2）为邻接面，写侧校验落点须注明避免半修冲突。
- **AR-01 修复形态**：交付判定先看 snapshotError（等价形态任选），钉子断言面钉在 tracker/callback 的 `hasError()` 边界；破坏面已验证为零（全仓无任何测试 sink 的 saveState 抛异常；故障注入全部在 commit 阶段，与本修复正交）。
- 编号消歧：Non-Goals 的 CON-04 指 **R5 CON-04（=REG-07 的截断提交面）**，非 R6 03 报告的 CON-04（temp 残留，P3 归治理）。

## Purpose

闭合四个独立 P1：(a) AR-01——StreamSinkOperator.processBarrier 中 snapshotState 成功后 saveState/prepareCommit 失败被当成功 ACK（错误优先级颠倒，exactly-once 恢复期静默丢数据破口）；(b) CON-01——文件 2PC sink manifest 守卫无运行代际命名空间，fresh run 复用输出目录即静默丢弃新 epoch 数据（默认配置重启即触发）；(c) ST-03——ClassNameValidator 恢复侧白名单拒绝写侧已接受的用户类（checkpoint 成功变不可恢复资产）；(d) TE-01——executor 路径 DRAIN/SUSPEND→TERMINAL_SAVEPOINT 终止派发零测试保护（变异推演成立）。

## Current Baseline

- AR-01（对抗复核独立推导成立）：`StreamSinkOperator.processBarrier` 同一 try 块内 snapshotState(:114) 成功 → saveState(:117)/prepareCommit(:127) 失败 → :131 仅 LOG.error → :136-138 先判 snapshotResult 非空按成功 ACK → durable checkpoint 落盘无 pendingCommits；tagged failure 分支仅 snapshotState 本身失败可达；abort 通道已接线（TaskCheckpointWiring:155-156）但该路径永不送达；恢复 `restoreFromEpoch` 只重提交 eid≤N pending → N-1→N 记录两头落空。真实外部 I/O 失败源为 file sink writeLines+fsync。
- CON-01（=R5 CON-08 已登记未修复）：manifest 键 `epochId + .sN` 无 job/run 维度；fresh run epoch 从 0 起（CheckpointIDCounter 初始 0，默认 checkpoint 存储下 auto-restore 显式禁用固定 "Starting fresh"）；撞键分支仅 WARN + 移除 pending，temp 不 rename 不删，消费面永久看不到；目录预检/epoch 单调校验/启动清理均不存在；16 个现有测试全部单运行代际。
- ST-03：写侧无白名单校验、恢复侧 `loadClass/loadAccumulatorClass` 必校验；默认 `nop.core.json.serialize-only-data-bean=true` 使非 DataBean 用户类写侧响亮失败，静默"写成功/恢复必败"面收窄为 DataBean 用户类与用户 accumulator 类。
- TE-01：TestGraphModelCheckpointExecutor 三个虚测以 config getter 往返冒充终止派发验证；删除 handleJobTermination L495/L502 的 triggerTerminalSavepoint 调用后全部仍通过；JobCoordinator 路径有 TerminationMatrix 真保护（TE-02 交叉引用：CheckpointCoordinator 不读 jobTerminationMode）。

## Goals

- sink 侧错误优先级 fail-closed：saveState/prepareCommit 失败必须走 tagged failure 路径（不 ACK），checkpoint 不落盘。
- 文件 2PC manifest 引入运行代际命名空间（对齐 JDBC ledger sink_namespace 先例）：fresh run 复用目录不再静默丢弃；撞键 fail-fast。
- 写读类门对称：ClassNameValidator 校验语义写侧/恢复侧一致（或写侧同规则前置 fail-fast）。
- executor 终止派发获得真实驱动测试（变异探针回退必红）。

## Non-Goals

- 不改 2PC 端点协议（R5 已收敛）。
- R5 CON-04（=REG-07 的截断提交守卫面，P2）归治理批次。注意与 R6 03 报告的 CON-04（temp/锁文件残留，P3）编号撞号，两者不同缺陷。
- 不引入用户类白名单配置面扩张（保持现有 fail-closed 默认）。

## Scope

### In Scope

- `nop-stream-core` StreamSinkOperator（错误优先级）、ClassNameValidator（对称性）。
- `nop-stream-connector` FileTwoPhaseCommitSink（运行代际命名空间 + 守卫）。
- `nop-stream-runtime` TestGraphModelCheckpointExecutor 重写虚测 + executor 终止派发真驱动测试（TE-02 一并诚实化）。
- 各修复面回归钉子。

### Out Of Scope

- JDBC sink ledger（R5 已有命名空间）。
- CheckpointCoordinator 锁内 I/O（CC-01 归治理批次）。

## Execution Plan

### Phase 1 - AR-01 错误优先级 + ST-03 类门对称

Status: completed
Targets: StreamSinkOperator、ClassNameValidator

- Item Types: `Fix`、`Proof`

- [x] StreamSinkOperator.processBarrier：saveState/prepareCommit 异常并入 tagged failure 路径（交付判定先看 snapshotError，不再按成功 ACK）
- [x] 钉子（断言面=tracker/callback 的 hasError() 边界，core 模块）：saveState 抛异常 → callback result hasError()==true（变异探针：回退"先判 snapshotResult"→ 收到 success ACK → 红）；runtime 级补充钉（coordinator 收 failure、durable 无该 epoch checkpoint）
- [x] ClassNameValidator：写侧同类校验前置（MemoryStateSerDe snapshotOneState 写 header 前校验 valueType/mapKeyType/keyType/accumulatorType/aggregateFunctionType，与恢复侧 loadClass 同规则同集）
- [x] RocksDBSnapshotSerDe 写侧同规则校验（closure audit M2 整改后三入口全闭合：snapshotState keyType 头（补）+ snapshotMapState value/keyType + 通用 keyed funnel valueType/accumulator；新增 TestWriteSideClassValidationRocksDB 钉（变异探针实测：禁用校验→红，恢复→绿）+ rocksdb 模块 138/0）
- [x] ST-07 邻接注明：写侧 keyType 校验落点注释标明与恢复侧裸 Class.forName（:423-450，P2 ST-07）的关系，避免半修冲突
- [x] ST-03 钉子：DataBean 用户类 checkpoint→恢复往返成功；非白名单类写侧响亮失败（前移验证）
- [x] owner-doc 裁定：AR-01/ST-03 部分维持 No owner-doc update required（向既有契约收敛、允许集不变）；CON-01 部分按审计 m2 补同步——`docs-for-ai/03-modules/nop-stream-connectors.md` 文件 sink 行更新为来源判别双语义（recover-safe 跳过 / fresh-run 撞键 fail-fast）
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime` 全绿

Exit Criteria:

- [x] AR-01 钉子变异探针回退必红；恢复期不再有"两头落空"窗口（代码追踪记录）
- [x] ST-03 往返钉子通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - CON-01 运行代际 + TE-01 终止派发测试

Status: completed
Targets: FileTwoPhaseCommitSink、TestGraphModelCheckpointExecutor

- Item Types: `Fix`、`Proof`

- [x] FileTwoPhaseCommitSink：manifest 撞键守卫改按 pending 条目来源判别（Adjudication 方案 (b)）——经 `setPendingCommits` 恢复的条目撞键保持 recover-safe WARN 跳过；本 run saveState 新建的条目撞键 → typed throw（不再静默丢弃新 epoch 数据）
- [x] 既有裸 put 测试迁移到诚实恢复入口 `setPendingCommits`（closure audit M1 整改后共 3 处：testIdempotentCommitNoDuplicateRename、testCommitFinalExistsManifestMissingRepairsManifest、testFileSinkKillRecoverExactlyOnce——后者初版遗漏，已补迁移）
- [x] 跨运行代际 E2E：run1 完成后 fresh run（同 jobId 同目录）复用输出目录 → 本 run 新建条目撞旧 manifest → typed 报错（默认配置重启即触发的审计面闭合）；恢复启动（经 setPendingCommits）→ recover-safe 保持
- [x] TestGraphModelCheckpointExecutor：三个 config 往返虚测替换为真实 DRAIN/SUSPEND 驱动 executeWithCheckpoint 的终止派发验证（断言面=盘上 EpochManifest.checkpointType 含 TERMINAL_SAVEPOINT，按 type 判别非数文件；变异探针：删 triggerTerminalSavepoint 调用必红）；TE-02 两个方法诚实化改名（testDrainModeTriggersTerminalSavepoint/testSuspendModeTriggersSavepoint → 自触发语义）
- [x] `./mvnw test -pl nop-stream/nop-stream-connector,nop-stream/nop-stream-runtime` 全绿

Exit Criteria:

- [x] 跨代际 E2E 绿（同 jobId fresh run 撞旧 manifest → typed fail-fast，无静默丢弃；恢复启动路径 recover-safe 保持——两向断言）
- [x] 终止派发测试变异探针回退必红（移除 executor 调用点测试必红）
- [x] owner doc：connectors 文档中文件 sink 恢复语义小节与 live 行为一致
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] AR-01/CON-01/ST-03/TE-01 四缺陷闭合（变异探针证据）
- [x] `./mvnw test -pl <受影响模块>` 全绿
- [x] R6 summary 对应条目补记修复归属
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：failure 路径在真实 barrier 管线中被验证；代际守卫在跨运行场景中被验证
- [x] owner doc 同步（或显式 No owner-doc update required + 理由）
- [x] 代码规范核对通过

## Deferred But Adjudicated

### CON-04 截断提交守卫（REG-07 残留面）等连接器长尾

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: P2 局部缺陷，不阻塞四个 P1 闭合
- Successor Required: `yes`
- Successor Path: 后续治理批次

## Closure

Status Note: AR-01（错误优先级：saveState/prepareCommit 失败并入 tagged failure，钉子断言 tracker callback hasError 边界）、CON-01（来源判别守卫：恢复条目 recover-safe 保持、本 run 新建条目撞键 typed fail-fast，双面钉 + 2 个既有测试迁移诚实入口）、ST-03（Memory+RocksDB 写侧同规则校验，禁类名快照期响亮失败 + 允许类往返保持）、TE-01（DRAIN/SUSPEND 真驱动 + 盘上 TERMINAL_SAVEPOINT 断言，变异探针实测红/绿）四缺陷全部闭合。core 1695/0、runtime 1246/0、connector 81/0、rocksdb 137/0、fraud-example 120/0。

Completed: 2026-10-06

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_edd9dd23-5935-4d84-8dcf-3673e1381d93）
- Audit Session: agent_edd9dd23-5935-4d84-8dcf-3673e1381d93
- Evidence:
  - Phase 1 Exit Criteria 3 条 + Phase 2 Exit Criteria 4 条：PASS（ST-03 往返钉初判 PARTIAL——RocksDB 面无钉，M2 整改后补齐 TestWriteSideClassValidationRocksDB）
  - Closure Gates 7 条：全部 PASS
  - 变异探针：(a) AR-01 回退"先判 snapshotResult"→ hasError 断言红（推演确认）；(b) CON-01 守卫回退 WARN+skip → 双面撞键钉红、恢复向既有钉保持绿（推演确认）；(c) ST-03 移除校验 → disallowedValueType 红、允许类往返绿；RocksDB keyType 门禁用实测红/恢复绿（实测闭环）；(d) TE-01 主 agent 实测：注释 DRAIN triggerTerminalSavepoint → drainMode 测试红（盘上无 durable checkpoint），恢复→绿
  - Anti-Hollow：AR-01 消费链 processBarrier→snapshotCallback→tracker hasError 分支→TaskCheckpointWiring:155-156→coordinator.reportTaskCheckpointFailure→abortPendingCheckpoint 完整连通；CON-01 守卫触达 commit():292→doCommitLocked（finishCommit 恢复重提交双生产触点）；守卫抛错经 coordinator notifyParticipantsFinishCommit try/catch → retryFailedCommits（fail checkpoint 而非崩作业）
  - `node ai-dev/tools/check-plan-checklist.mjs --strict` 回填后主 agent 复跑 exit 0
  - Deferred 项分类检查：ST-07、R5 CON-04/REG-07 面均治理批次归属；无 in-scope live defect 被降级
  - 条件整改：M1（第三测试迁移）+ M2（RocksDB keyType 门 + 钉）完成；m2 connectors 文档补同步；m4 提交切分（OverWindowOperator 为 2324 常量迁移残差，随本提交注明归属）

Follow-up:

- ST-07（恢复侧 keyType 裸 Class.forName 绕过，P2）归治理批次（写侧门注释已标明邻接）
- R5 CON-04/REG-07 截断提交守卫面归治理批次

Follow-up:

- ST-07（恢复侧 keyType 裸 Class.forName 绕过，P2）为邻接面未闭合（写侧校验注释已标明），归治理批次
- R5 CON-04/REG-07 截断提交守卫面归治理批次
