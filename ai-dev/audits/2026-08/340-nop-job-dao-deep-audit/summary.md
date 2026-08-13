# nop-job-dao 深度审计 summary

**审计日期**：2026-08-12
**审计范围**：`nop-job/nop-job-dao` 全模块（store 层 + FSM/helper 层 + entity/跨模块生命周期）
**审计方式**：三路独立子 agent 并行深度审计后合并去重
**修复 plan**：`ai-dev/plans/340-nop-job-dao-audit-remediation.md`

## 总体结论

整体设计稳健——VERSION 乐观锁 + 状态机 helper + per-fire 隔离挡住了最危险的 double-claim / lost-update / duplicate-execution。但发现 **1×P1（生产可触发的长任务误杀）+ 9×P2（游标分页方向反转、计数漂移、超时语义错配、契约分歧等）+ ~15×P3**。其中最关键的一类 bug（P2-1 游标分页）正被 pre-existing H2 测试环境故障遮蔽，本该捕获它的集成测试从未执行。

状态机/helper 层语义迁移**零漂移**（9 处迁移逐一核对一致），FSM 内部自洽。生命周期 21 条迁移路径 19 条有测试覆盖。

## 合并去重后 findings（按严重度）

详细证据见同目录 `store-layer.md` / `fsm-helpers.md` / `entity-lifecycle.md`。

| ID | Sev | 位置 | 问题 | 修复归属 |
|----|-----|------|------|----------|
| P1-1 | P1 | `JobTimeoutCheckerImpl.java:452-453` | RUNNING task 执行超时回退到 `dispatchTimeoutMs`（默认 5min），未配超时的长任务被误杀 | plan 340 §2.1 |
| P2-1 | P2 | `JobTaskStoreImpl.java:110-111,173-174`；`JobFireStoreImpl.java:255-256` | 游标分页方向反转（ASC+lt 谓词），每周期只处理最老 ~batchSize 行 | plan 340 §2.2 |
| P2-2 | P2 | `JobScheduleStoreImpl.java:207-210` | recovery 不可恢复时不推进 nextFireTime，5s 紧循环无退避 | plan 340 §2.3 |
| P2-3 | P2 | `JobFireStoreImpl.java:291` | `failFireWithoutSchedule` 丢弃版本检查返回值，冲突时 fire 静默留 DISPATCHING | plan 340 §2.4 |
| P2-4 | P2 | `JobFireStoreImpl.java:139,205` | `activeFireCount` 计数漂移（@SingleSession 下不可路径内重试） | plan 340 §2.5（对账器） |
| P2-5 | P2 | `JobFireStoreImpl.cancelFire:146` vs `JobScheduleStoreImpl.cancelFire:417` | 两个同名 cancelFire 副作用分歧 | plan 340 §2.6（rename `markFireCanceledOnly`，拒绝 merge） |
| P2-6 | P2 | `JobCompletionProcessorImpl.java:110,122` | 完成路径 dirty-flush 冲突被通用 catch 吞掉，不可观测 | plan 340 §2.7 |
| P2-7 | P2 | `JobScheduleStoreImpl.java:196-204` | recovery 死代码（mutate failedFire 后 reload freshFire，前者从不持久化） | plan 340 §2.8 |
| P2-8 | P2 | `JobScheduleStoreImpl.java:363`；`JobFireStoreImpl.java:306`；`JobTaskStoreImpl.java:116` | 无 LIMIT 查询，overlay/广播风暴下无界 | plan 340 §2.9 |
| P2-9 | P2 | `TriggerSpecHelper.java:46-48`（+消费方） | fixed-rate 首次相位锚到 epoch 0，skip 后网格漂移 | plan 340 §2.10 |

## P3 汇总（维护性/效率/完备性）

- **死列**：fire `taskCostCpu/taskCostMemory`/`retryRecordId` + 索引 `IX_NOP_JOB_FIRE_RETRY`；task `workerAddress`/`progress`/`progressMessage`（均 written-never-read 或 never-written）
- **死方法**：`JobFireStateMachine.canRerun`/`isWaiting/isRunning/isFailed`、`JobTaskStateMachine.isWaiting/isFailed/isCanceled`、`JobFireStoreImpl.updateRetryRecordId`（无生产调用方）
- **索引不匹配**：`fetchRunningTasks`/`fetchDispatchingFires` 按 `startTime` 排序但索引第三键是 `scheduledFireTime`/`createTime` → filesort
- **`hasWaitingFire` vs UK 语义分歧**：UK 跨全部状态，pre-check 只查 WAITING，同 ms 重触发会撞约束
- **`findTasksByFireId` 重复实现**（fire-store 私有 + task-store 公共）
- **`enforceAttribution && workerInstanceId==null` 静默跳过归因**
- **CANCELED fire 落入失败分支**触发重试/告警（helper 预存在 P3）
- **`resolveFinalStatus(空list)=SUCCESS`**（生产由早返回保护，API 复用风险）
- **多租户隔离仅靠 partition**（未文档化）
- **实体是空壳**（无 domain 方法封装 FSM，调用方可裸 setStatus）

以上 P3 中：纯代码低风险项随 plan 340 §2.11 处理；ORM 模型结构变更（死列清理 + 索引）受 Protected Area 约束移入 plan 340 `Deferred`，由 successor ORM-cleanup plan 处理。

## 关键元问题：H2 测试环境故障遮蔽 bug

`TestJobStoreImpl`(20) / `TestJobFireStoreRace`(11) / `TestJobWorkerScanner`(15) / `TestJobCoordinatorScanner`(21) / `TestJobConcurrency`(6) 全部因 pre-existing H2 schema 错误失败——证据 `ai-dev/logs/2026/08-11.md:121`：`Table "NOP_JOB_SCHEDULE" not found (this database is empty)`，即**空库初始化问题**（AutoTest/H2 在单模块隔离运行下 schema 未初始化），**非列缺失/列漂移**。本该捕获 P2-1 的 `testFetchRunningTasksCursorPaginatesStrictly` 从未执行。该环境问题与本审计改动无关，已 git stash 在 master 基线复现。修复移入 successor plan（plan 340 不修，仅记录诊断）。
