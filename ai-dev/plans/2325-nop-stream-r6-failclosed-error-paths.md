# 2325 nop-stream R6 P1：fail-closed 错误路径与测试保护族（AR-01/CON-01/ST-03/TE-01）

> Plan Status: draft
> Last Reviewed: 2026-10-06
> Source: ai-dev/audits/2026-10/2026-10-06-0522-adversarial-review-nop-stream/01-open-findings.md（AR-01 复核 P1）、03-connectors-security.md（CON-01 复核 P1，R5 CON-08 已登记未修复）、02-state-backend-serialization.md（ST-03 复核 P1）、06-test-effectiveness.md（TE-01 复核 P1）
> Related: ai-dev/plans/2315-nop-stream-r5-connector-security.md（CON-04/08 半回退背景）、2322-nop-stream-r6-p0-e2e-red-baseline.md

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
- CON-04 截断提交守卫（REG-07 残留面，P2）归治理批次。
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

Status: planned
Targets: StreamSinkOperator、ClassNameValidator

- Item Types: `Fix`、`Proof`

- [ ] StreamSinkOperator.processBarrier：saveState/prepareCommit 异常并入 tagged failure 路径（不再按成功 ACK）
- [ ] 钉子：saveState 抛异常 → 协调器收到 failure、checkpoint 不落盘（变异探针回退必红）
- [ ] ClassNameValidator：写读对称（选定方向：写侧同类校验前置，或恢复侧放行写侧已接受类——实施前按最小破坏原则裁定并记录）
- [ ] ST-03 钉子：DataBean 用户类 checkpoint→恢复往返成功
- [ ] `./mvnw test -pl nop-stream/nop-stream-core` 全绿

Exit Criteria:

- [ ] AR-01 钉子变异探针回退必红；恢复期不再有"两头落空"窗口（代码追踪记录）
- [ ] ST-03 往返钉子通过
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - CON-01 运行代际 + TE-01 终止派发测试

Status: planned
Targets: FileTwoPhaseCommitSink、TestGraphModelCheckpointExecutor

- Item Types: `Fix`、`Proof`

- [ ] FileTwoPhaseCommitSink：manifest 键引入运行代际（job/namespace 维度，对齐 JDBC 先例）；fresh run 撞旧代际 fail-fast（不再 WARN+丢弃）
- [ ] 跨运行代际 E2E：run1 完成后 fresh run 复用输出目录 → 显式报错（或安全隔离），新 epoch 数据不丢失
- [ ] TestGraphModelCheckpointExecutor：三个 config 往返虚测替换为真实 DRAIN/SUSPEND 驱动 executeWithCheckpoint 的终止派发验证（变异探针：删 triggerTerminalSavepoint 调用必红）；TE-02 自触发测试诚实化改名
- [ ] `./mvnw test -pl nop-stream/nop-stream-connector,nop-stream/nop-stream-runtime` 全绿

Exit Criteria:

- [ ] 跨代际 E2E 绿（fresh run 撞旧 manifest 的行为断言：fail-fast 或隔离，无静默丢弃）
- [ ] 终止派发测试变异探针回退必红（移除 executor 调用点测试必红）
- [ ] owner doc：connectors 文档中文件 sink 恢复语义小节与 live 行为一致
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] AR-01/CON-01/ST-03/TE-01 四缺陷闭合（变异探针证据）
- [ ] `./mvnw test -pl <受影响模块>` 全绿
- [ ] R6 summary 对应条目补记修复归属
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：failure 路径在真实 barrier 管线中被验证；代际守卫在跨运行场景中被验证
- [ ] owner doc 同步（或显式 No owner-doc update required + 理由）
- [ ] 代码规范核对通过

## Deferred But Adjudicated

### CON-04 截断提交守卫（REG-07 残留面）等连接器长尾

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: P2 局部缺陷，不阻塞四个 P1 闭合
- Successor Required: `yes`
- Successor Path: 后续治理批次

## Closure

Status Note: （收口时填写）
Completed: （收口时填写）

Closure Audit Evidence: （收口时由独立子 agent 填写）

Follow-up:

- （收口时填写）
