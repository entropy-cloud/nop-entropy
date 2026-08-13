# nop-job Store 契约设计

**日期**：2026-08-12
**范围**：`nop-job-dao` store 层的游标分页、cancelFire 原子契约、无界查询防御、完成路径可观测性、归因隔离
**状态**：设计完成（plan 340 §1.3）

---

## 1. 游标分页契约（Decision，修正 P2-1）

### 1.1 契约

涉及"批量扫描 + 全量 drain"的 store 查询（`fetchRunningTasks`/`resetStaleWaitingTasks`/`fetchDispatchingFires`）统一采用：

- **排序**：按游标字段 **DESC**（`addOrderField(field, true)`），主字段 + 唯一 tie-breaker（如 `startTime DESC, jobTaskId DESC`）。
- **游标谓词**：`field < cursorTime OR (field == cursorTime AND id < cursorId)`（`lt` 语义）。
- **推进**：`drainBatch` 取每批最后一行（DESC 下即本批最小值）作为下一批游标；下一批谓词 `lt cursor` 返回更小的（即未见过的新行）；空批或 `size < batchSize` 时 `markDrained`。

### 1.2 为何不能 ASC

`ASC` 排序 + `lt` 谓词不兼容：ASC 首批返回最小值集合，游标=首批最大值；第二批谓词 `lt cursor` 返回首批全部行（均小于游标）→ 重复首批 → `drainBatch` 因 `size < batchSize` 误判完成 → 只处理了最老的 ~batchSize 行，大量行永远轮不到。这正是 P2-1 的 bug（`addOrderField(x, false)` 即 ASC）。

### 1.3 验证

- 代码核对：三处 `addOrderField` 第二参为 `true`。
- mock 级测试：在 `TestJobTimeoutChecker` 的 reverseOrder mock 基础上，新增用例断言批次 2 返回**未见过的新行**（非重复批次 1），drainBatch 跨多批推进。
- 真 DB 集成测试（`testFetchRunningTasksCursorPaginatesStrictly`）gated on H2 测试环境修复 successor plan。

---

## 2. cancelFire 契约（Decision，修正 P2-5）

### 2.1 裁定：改名消除碰撞（非 merge）

`IJobFireStore.cancelFire(jobFireId)`（public，`@Transactional(REQUIRES_NEW)`，一次取消单 fire 的 fire+tasks+schedule 计数）与原 `JobScheduleStoreImpl.cancelFire(fire, time)`（私有，仅标记 fire，不动 tasks/计数）**职责不同**。

**拒绝 merge（overlay 改调 public cancelFire）**：public 方法是 `@Transactional(REQUIRES_NEW)`，overlay 循环里逐个调它会产生 N 个嵌套新事务，破坏 overlay 操作的原子性；且 overlay 的"聚合一次 schedule 更新"模式比"逐 fire 更新 schedule"更高效（N 次 schedule 写 → 1 次）。merge 形态在本场景不合适。

**选定 rename**：私有方法改名 `markFireCanceledOnly(fire, time)`，明确表达"仅标记 fire、不动 tasks/计数"。overlay/manual 调用方继续组合 `markFireCanceledOnly` + `cancelTasks` + 聚合计数 math（在其自身事务内），但名称不再碰撞、契约清晰。

### 2.2 错误码归因

- `markFireCanceledOnly` 用 `ERR_JOB_OVERLAID`（overlay 归因）。
- `IJobFireStore.cancelFire(jobFireId)` 用 `ERR_JOB_CANCELED`（手动/业务取消归因）。

两路径的归因 error code 经方法名天然区分，无需 overload。

### 2.3 保持的聚合计数

overlay/manual 路径在 `updateScheduleWithRetry` 回调里基于 `cancelledCount` 做计数的**聚合**更新（`activeFireCount - cancelledCount + 1`、`totalFireCount + cancelledCount`、`failFireCount + cancelledCount`），这是**有意的**——一次 schedule 写覆盖整批取消，比逐 fire 写 schedule 高效。**不存在双重计数**（`markFireCanceledOnly` 不动 schedule 计数，只有回调动）。

---

## 3. 无界查询防御性 LIMIT（Decision，修正 P2-8）

涉及"按 schedule 聚合活跃 fire"的查询加防御性 LIMIT；**`findTasksByFireId` 不加 LIMIT**：

| 方法 | LIMIT? | 理由 |
|------|--------|------|
| `JobScheduleStoreImpl.findActiveFires` | 500 | overlay/手动风暴下活跃 fire 数无界；overlay 取消是 drain 语义——每周期取最多 500 取消，余量下周期处理（非丢失，fire 仍 active）。REQUIRES_NEW 事务内逐行 CAS，长事务风险。 |
| `JobFireStoreImpl.findTasksByFireId` / `JobTaskStoreImpl.findTasksByFireId` | **不加** | 这两方法被 completion（需要全部 task 状态聚合 fire 终态）与 cancel（需要全部 task 取消）调用——加 LIMIT 会导致**不完整状态聚合 / 部分取消**，破坏正确性。单 fire 的 task 数在 dispatch 时已 bounded（broadcast=健康实例数、partition=分区数、single=1），不是"风暴"而是单 fire 扇出。 |

超限时 `LOG.warn("nop.job.query-limit-hit:method=findActiveFires,scheduleId={},limit={}")`。余量由后续周期处理（fire 仍 active，下轮 overlay 取消覆盖）。

**拒绝"给 findTasksByFireId 加 LIMIT"**：completion 与 cancel 路径要求看到 fire 的全部 task，capping 会静默产出错误终态或遗漏取消。

---

## 4. 完成路径 dirty-flush 冲突可观测（Decision，修正 P2-6）

### 4.1 保持 @SingleSession dirty-flush

`JobCompletionProcessorImpl.completeSingleFire` 依赖 `@SingleSession` dirty-flush（实体在 `@Transactional` commit 时刷盘）是 deliberate 设计（`:199-200` 注释）——避免完成热路径上的额外 roundtrip。**不切换**到 `completeFireAndUpdateSchedule`（那会违背 @SingleSession 缓存语义）。

### 4.2 使冲突可观测

原实现外层 `catch(Exception)` 把 commit 期的版本冲突（schedule 被并发 planner 改写）吞为通用 `fire-complete-failed` warn，导致 `activeFireCount` 漂移不可观测。

**裁定**：`tryUpdateWithVersionCheck` 返回 boolean（不抛异常），`@SingleSession` dirty-flush commit 期的冲突在 ORM 内部处理、其异常类型不可靠识别。因此 **2.7 不重构 catch 去匹配特定异常类**，而是：(a) 在现有 warn 中补充 `scheduleId` 使冲突可关联；(b) **以独立会话对账器（见 `timeout-and-recovery-design.md` §2）作为漂移收敛的 PRIMARY 机制**——它按 live fire 计数重算 `activeFireCount`，不依赖冲突是否可观测。完成路径 warn 的价值是运维可感知，对账器保证最终一致。

---

## 5. `enforceAttribution` 空值语义（Decision，修正 P3-e）

`JobTaskStoreImpl.fetchTasksForExecute`（worker 拉取）当 `enforceAttribution=true`（dedicated worker 池模式）但 `workerInstanceId==null`（worker 的 hostId 未设）时，**不得静默跳过归因 filter**（原实现 `if (enforceAttribution && workerInstanceId != null)` 使 null hostId 的 worker 看到全部 task，破坏 dedicated 池隔离）。

**裁定**：`enforceAttribution && workerInstanceId == null` 视为**配置错误**，抛 `NopException(ERR_JOB_WORKER_INSTANCE_ID_REQUIRED)`（新增错误码），快速失败。运维须确保 dedicated 模式下 worker hostId 已正确配置。

---

## 6. 关联文档

- 超时三层边界与对账器选型理由见 `timeout-and-recovery-design.md`。
- 状态机谓词与迁移约束见源码 `JobFireStateMachine`/`JobTaskStateMachine`/`JobScheduleStateMachine`（FSM 是状态语义的单一可信源，本 doc 不重复）。
