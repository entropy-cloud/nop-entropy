# nop-retry — 分布式重试引擎

## 功能概览

分布式重试引擎，支持可配置的退避策略。

- 可配置退避策略（固定间隔、指数退避）
- 重试策略管理
- 幂等键支持（死信后同幂等键可重新提交）
- 命名空间/分组隔离
- 回调触发（policy 驱动）
- 重试尝试追踪（每次执行尝试一条 NopRetryAttempt）

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopRetryPolicy | `nop_retry_policy` | 重试策略 |
| NopRetryRecord | `nop_retry_record` | 重试记录 |
| NopRetryAttempt | `nop_retry_attempt` | 重试尝试 |
| NopRetryDeadLetter | `nop_retry_dead_letter` | 重试死信 |

## 退避策略

- **固定间隔**：每次重试间隔固定毫秒数
- **指数退避**：间隔随重试次数指数增长（`jitterRatio` 抖动）

## 阻塞策略

- **丢弃**（DISCARD）：同命名空间、同分组、同幂等键已有未完成记录时丢弃新任务
- **覆盖**（OVERWRITE）：以原子替换包络删除已有未完成记录并以新请求替换（store 层 `replaceRecord` 单事务 delete+save，失败整体回滚、旧记录存活并死信化全量快照，任务零蒸发）；替换成功时递增该记录的进程内代际号，旧链（在途回调/已调度续跑定时器）每次回调/续跑/回写前校验「代际一致 ∧ DB 行仍在」，任一不满足即短路中止（INFO 留痕）；旧链回写命中已删行（0 行）同样显式短路——覆盖重提后幽灵旧链双执行不可达
- **查重范围**：提交去重覆盖全部生命周期状态——已有 PENDING/RETRYING/SUSPENDED 记录按上述策略阻塞（SUSPENDED 是运维暂停，重复提交不偷偷恢复执行）；已有 COMPLETED 记录时重提交按幂等重放直接返回成功，不重新执行副作用、不撞唯一键
- **并发首提兜底**：两方同时以同一幂等键首发时由 `UK_RETRY_IDEMPOTENT_ID` 唯一键裁决，输家按 blockStrategy 处理，不以裸约束冲突暴露
- **OVERWRITE 输家分流（plan 2543 W-01）**：原子替换失败后按「异常类型 × 重读行身份」四分支处置——(a) UK 冲突且重读行 sid 不同（键已被对端合法替换）→ 让步返回成功、**不造死信**；(b) UK 冲突且重读为同一行（事务已回滚、旧行真实存活）→ 死信快照 + `ERR_RETRY_RECORD_OVERWRITTEN`；(c) UK 冲突且重读行为空（键位被并发删除）→ `ERR_RETRY_OVERWRITE_RACE_LOST`、不造死信；(d) **非 UK 异常**（瞬时 DB 故障/锁超时）→ 纯失败传播、**不造死信也不删除旧行**——此类异常下无法判定是否存在并发赢家，而死信含删除动作，禁止以猜测驱动破坏性操作

## 回调

由 policy 驱动（`callbackEnabled` + `callbackTriggerType` + `callbackPolicyId`），仅在 `callbackEnabled=true` 且存在 `callbackPolicyId` 时触发。当前实现仍回调原任务 service/method，payload 含 `recordId`/`idempotentId`/`success`/`retryCount`/`errorCode`/`errorMessage`；回调任务 idempotentId 追加 `_callback` 后缀。

**回调链环护栏（plan 2543 W-09）**：回调记录的策略是 `callbackPolicyId`，其终态又在同一策略下孵化下一条回调记录、idempotentId 逐环追加 `_callback`——若策略链成环（A→A 或 A→B→A），每环真实调用一次业务且记录数无界增长。孵化前统计 idempotentId **尾部连续 `_callback` 后缀**的重复次数作为环深，达到 `nop.retry.callback.max-depth`（缺省 3，`<=0` = 显式不限制）即**拒绝孵化**：ERROR 留痕（`nop.retry.callback-depth-exceeded`）+ 计数（`getCallbackDepthExceededCount()`）+ 不创建回调记录、不写死信、不改主记录结论（主业务结果已定，回调是旁路）。装载期策略环校验需 policy-save hook（属 `nop-retry-service`），不在本引擎范围。

## 配置边界（plan 2543 W-12/W-14）

扫描器参数在**装配期 fail-fast**（越界抛 `ERR_RETRY_SCANNER_CONFIG_INVALID`，不静默回落缺省）：

| 配置键 | 缺省 | 合法域 / 语义 |
|--------|------|--------------|
| `nop.retry.scanner.batch-size` | 100 | `[1, 1000]`。`0` 会使每轮 fetch 恒空（扫描器「运行中」但永不处理记录，与「无积压」不可区分）；负值透传 SQL 致每轮报错；巨值单批物化无上界 |
| `nop.retry.scanner.scan-interval-ms` | 5000 | `>= 100`。负/零使调度退化为零间隔紧循环（上一轮返回后立即重扫），DB 满频敲击 |
| `nop.retry.scanner.retrying-timeout-ms` | 600000 | `> 0`。非正使 tryLock 写出的锁过期时间落在过去，记录被立即重拾（租约语义失效） |
| `nop.retry.scanner.max-batches-per-round` | 10 | `>= 1`。单次 `doScan` 最多处理的**非空批**数（锁竞争的 `continue` 轮不耗预算） |
| `nop.retry.scanner.max-round-duration-ms` | 60000 | `>= 1`。单次 `doScan` 时长上界（每批开始前检查） |
| `nop.retry.callback.max-depth` | 3 | `<=0` = 不限制；见「回调链环护栏」 |

仓储层另有防御：`fetchPendingRecords(limit<=0, …)` 直接抛 `ERR_RETRY_SCANNER_CONFIG_INVALID`，不透传查询。

**升级注记**：既有部署若配置了 `0`/负值/超上限的扫描参数，升级后**启动失败**（fail-fast 而非静默降级）——这是有意的契约变更，须在升级前校正配置。

## 关键字段

**NopRetryPolicy**:
- `backoffStrategy`：退避策略（固定间隔/指数退避）
- `maxRetryCount`：最大重试次数
- `initialIntervalMs` / `maxIntervalMs`：初始/最大间隔毫秒数
- `jitterRatio`：抖动比例
- `immediateRetryCount` / `immediateRetryIntervalMs`：立即重试次数/间隔
- `deadlineTimeoutMs`：总截止超时
- `retryingTimeoutMs`：执行中锁定超时
- `blockStrategy`：阻塞策略
- `callbackEnabled` / `callbackTriggerType` / `callbackPolicyId`：回调配置

**NopRetryRecord**:
- `namespaceId`：命名空间
- `groupId`：分组
- `executorName`：执行器名称
- `idempotentId`：幂等键
- `status`：状态（PENDING/RETRYING/COMPLETED/SUSPENDED）
- `nextTriggerTime`：下次触发时间
- `partitionIndex`：分区索引（集群扫描分区）
- `retryCount` / `maxRetryCount`：重试计数

**NopRetryAttempt**:
- `recordId` + `attemptNo`：归属记录与尝试序号（唯一）
- `status`：WAITING/RUNNING/SUCCESS/FAILED/STOPPED/CANCELED
- `startTime` / `endTime` / `durationMs`：耗时
- `errorCode` / `errorMessage` / `errorStack`：失败信息
- `requestPayloadSnapshot`：请求快照

**NopRetryDeadLetter**:
- `recordId`：原记录 ID（原记录在死信化时删除，死信保留全量快照）
- `idempotentId`：幂等键（非唯一，多次失败周期可产生多条死信）
- `requestPayload` / `failureCode` / `failureMessage` / `errorStack`：失败快照

## 执行语义

- **尝试追踪**：每次执行尝试（含立即/延迟重试）写一条 `NopRetryAttempt`，attemptNo = retryCount + 1。
- **幂等隔离**：未完成记录的查重和唯一性都按 `(namespaceId, groupId, idempotentId)` 收口；不同 group 不互相阻塞。
- **死信化**：maxRetry 耗尽/bizFatal/deadline 超时 → 死信全量快照 + 删除原 record 行；此后同 `(namespaceId, groupId, idempotentId)` 可重新提交。
- **死信重放**：`retryFromDeadLetter` = 手动单次重放（fire-and-forget，不新建 record、不改死信状态）；它不是重新入队。
- **分区**：`partitionIndex = floorMod(hash(namespaceId:groupId:idempotentId), 16)`，集群扫描按分区取数。
- **回调**：`callbackEnabled` 是总开关；仅当开关打开且存在 `callbackPolicyId` 时才按 `callbackTriggerType`（ON_SUCCESS/ON_FAILURE/ALWAYS）触发。当前实现仍回调原 service/method，新任务 idempotentId 追加 `_callback` 后缀。

## 并发与租约契约（plan 2515 R2-12）

- **租约续租协议**：即时重试链每轮 attempt 发起前对 `nextTriggerTime` 做乐观锁版本 CAS 续租（推进一个租约周期，缺省租约 600s）。续租 0 行 = 租约被扫描器接管或他链抢持 → 本链**败方显式中止**（错误码 `nop.err.retry.lease-lost` + WARN），不静默续跑、不重抢。扫描器拾取维持「status∈{PENDING,RETRYING} ∧ nextTriggerTime<=now」双条件；单次 attempt 挂死窗由 **attempt 级超时上界**闭合：配置键 `nop.retry.attempt.timeout-ms`（缺省 300s = 默认租约的一半分数档），装配期 fail-fast 校验必须小于租约——挂死 attempt 在租约到期前被超时取消落账，接管读到的必是已终态化的 attempt。
- **记账分层（簿记失败不改判业务结果）**：attempt 层（`NopRetryAttempt` 落账）与 record 层（retryCount 推进/状态回写/死信化）簿记写各自独立 try/catch 隔离——簿记失败记 ERROR（`nop.retry.attempt.bookkeep-fail` / `nop.retry.record.bookkeep-fail`）+ 失败计数，**不改判业务结论、不以簿记异常完成业务 future**（簿记瞬时 DB 故障不得把已成功的业务调用改判失败并重试）。
- **future 拓扑单一化**：`executeImmediateRetry` 返回的 future 与回调挂接为**同一 promise**，每条链出口 Throwable 兜底 `completeExceptionally`——返回 future 必有终态（成功/失败/兜底异常），扫描器 `allOf().join()` 有界返回，业务级永久冻结形态不可再现（扫描链绝缘契约见下节，两层正交零回退）。
- **提交面失败交付契约（C8-017）**：`IRetryTask.callAsync` 受支持的调用形态包含 fire-and-forget——调用方**等待非必需**；提交面任何失败（策略/记录装载、OVERWRITE 替换等同步 DB 类故障）保证经返回的 future 异常终态交付（实现侧 try-catch(Throwable) → failedStage），同步抛 / future 异常 / 永不完成三通道收敛为 future 单通道。调用方不读取 future 即自担失败感知；读取 future 者必然感知一切提交面失败。
- **attempt 终态化与僵尸记录消除**：执行入口（含扫描器重拾与 OVERWRITE 新链）对 record 名下遗留 RUNNING attempt 先对账置 STOPPED（终态）+ endTime；新 attempt 的 attemptNo 取该 record 下 max(attemptNo)+1（`UK_RETRY_ATTEMPT_RECORD_NO` 碰撞从根消除），`saveAttempt` 另有 duplicate-key 防御兜底（关闭遗留行 + max+1 重插一次，触发即 WARN 不静默）——崩溃/挂死残留 RUNNING attempt 不再砖化记录。解析（payload 反序列化/策略装载）与 invoker 同步抛等异常路径**首败即死信化**（`moveToDeadLetter` 全量快照 + failedStage），不再产生每租约周期无限重拾的僵尸记录。
- **扫描 namespace 覆盖**：扫描器拾取覆盖**全部 namespace** 的 PENDING/RETRYING 记录（不按 store 装配的单值 namespaceId 过滤；该字段保留为提交缺省值用途）。租户隔离由分区键（`namespaceId:groupId:idempotentId → partitionIndex`）与幂等 UK 承担；依赖「扫描面按 namespace 隔离」的部署须以分区分配表达。

## 扫描链（RetryScanner）

`RetryScannerImpl` 经平台周期任务绝缘层驱动（`IScheduledExecutor.scheduleWithFixedDelay` 六参绝缘入口，taskName=`nop-retry-scanner`；绝缘语义契约 = `IScheduledExecutor` 周期方法 javadoc 成文契约）：

- **链存活**：任务体任何 Throwable（含 Error）由绝缘设施捕获、观测并重臂——单轮扫描失败（含 DB 瞬时异常/Error）不会永久停摆扫描器；Exception 级失败由站点自身 catch 兜底记 `nop.retry.scanner.scan-failed`，Error 级失败由设施守卫观测（`nop.err.periodic-task.fail` + `nop.retry.scanner.round-failed`）。
- **链终止**：仅两条路径——调用方 `stopScanning()`（cancel）或设施级提交拒绝（timer/worker 池关闭，`nop.retry.scanner.chain-rejected`）；拒绝后 `isRunning()` 真实化为 `false`，链不复活。
- **存活指标**：`getScanCount()` / `getLastScanTimeMillis()` / `getLastScanDurationMillis()`（每轮扫描结束刷新，含异常路径）与 `getScanFailureCount()` / `getLastScanFailureTimeMillis()`——`lastScanTimeMillis` 停止增长即扫描链停摆的可判别信号。
- **单轮上界（plan 2543 W-12）**：`doScan` 的排水式 `do-while` 原先无上界——单轮时长 = 批次数 × 最慢批耗时（下游批量超时时可达小时级），期间扫描心跳不推进、租约语义被间接破坏。现加**批次数与时长双上界**（先到先断）：达 `max-batches-per-round`（非空批口径）或 `max-round-duration-ms` 即结束本轮，余量交下一 fixedDelay 轮。上界只控本轮起点，**不打断在途批**（保 future 终态完备）。结束原因经 `getLastRoundEndReason()` 可观测（`BACKLOG_DRAINED`/`MAX_BATCHES`/`MAX_DURATION`/`STOPPED`/`FETCH_ERROR`）。
- **批内 per-record 隔离（plan 2543 W-11）**：批处理由惰性 `stream().map(...).toArray()` 改为显式循环 + 单条 try/catch——原形态下第 k 条记录同步抛出即中断整个 `toArray`，第 k+1..N 条的 future 根本不会创建，且这些记录已被 `tryLockRecordsForProcess` 置 RETRYING（租约 +10min），下周期同页排序不变（`nextTriggerTime asc + sid asc`）时**确定性顺延**（饿死）。现单条同步抛不再中断本批其余记录，失败显式 ERROR（`nop.retry.scanner.record-dispatch-failed`）+ 计数（`getScannerDispatchFailureCount()`），其租约到期后由扫描器重新拾取。
- **积压兜底**：扫描停摆的直接后果是 `nop_retry_record` 表积压；绝缘层消除「单次 Throwable 即永久停摆」后，表积压回归瞬态可自愈。

## 账本保留策略（1.6-C7-001，plan 2502）

三账本表（`nop_retry_record` / `nop_retry_attempt` / `nop_retry_dead_letter`）由引擎生命周期链周期清理（与扫描链同构的绝缘载体，taskName=`nop-retry-ledger-cleanup`）。参数语义 SSOT = 仓库 ai-dev/design/concurrency/ledger-retention-policy.md（R01 族级保留策略，平台内部文档——按 docs-for-ai 边界规则不直接链接），模块配置键：

| 配置键 | 缺省 | 语义 |
|--------|------|------|
| `nop.retry.ledger.retention-days` | 30 | 保留期（天）；`0`=显式禁用周期清理；负值启动期 fail-fast |
| `nop.retry.ledger.cleanup-interval` | 1h | 清理触发周期；下限 60s，低于下限启动期 fail-fast |
| `nop.retry.ledger.delete-batch-size` | 1000 | 单轮单表删除上限（有界拾取+IN 批删）；非正数启动期 fail-fast |

清理谓词与保留语义：

- **COMPLETED record**：锚列 `updateTime`、谓词 `status=COMPLETED`，超期整行删除，其 attempt 行级联删除。
- **死信**：锚列 `createTime`，超期整行删除；其孤儿 attempt（recordId 指向已删除 record，含死信化与 OVERWRITE 重建两类来源）随死信保留期一并删除。
- **不清理**：PENDING/RETRYING/SUSPENDED（在途/可恢复）记录永不进入清理谓词。
- **死信重放通道随保留期关闭**：死信行超期清除后，`retryFromDeadLetter` 对该行不再可用——需要长期保留可重放死信的部署应调大 `nop.retry.ledger.retention-days`。
- 清理失败显式可见：失败轮 ERROR（`nop.retry.ledger.cleanup-fail`）+ 失败计数，周期链由绝缘层保障不断链。

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-retry-engine` | 重试引擎核心 |
| `nop-retry-dao` | ORM 实体与 DAO |
| `nop-retry-service` | 业务逻辑 |
| `nop-retry-web` | Web 层与 AMIS 页面 |

## 关系

- `nop-job` 可通过 `nop-job-retry-adapter` 集成 nop-retry 实现任务失败重试

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-retry/model/nop-retry.orm.xml` |
| 扫描链 | `nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/scanner/RetryScannerImpl.java` |
| 账本保留清理（1.6-C7-001） | `nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/store/RetryRecordStoreImpl.java`（`purgeExpiredLedgerRows` 三通道清理面；保留参数 `RetryEngineConfigs`，引擎生命周期链接线 `RetryEngineImpl`） |
| 并发契约（plan 2515 R2-12 + plan 2543 R4-10） | `nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java`（续租+被抢中止 `executeImmediateRetry` :457/:465、代际校验 `finishImmediateRetry` :488/:500、两层簿记隔离 `runRecordBookkeeping` :565/:665/:723、僵尸死信化 `executeRetryFromScanner` :355/:377-388、join 兜底 :923、**OVERWRITE 输家四分支** `handleOverwriteReplaceFailure` :353+、**回调深度护栏** `triggerCallback`/`callbackDepth` :849/:936+、**per-record 派发隔离** `dispatchScannerBatch` :1035+）；`RetryRecordStoreImpl`（store/）（原子替换 `replaceRecord` :339、续租 CAS `renewLease` :349、遗留 RUNNING 对账 `reconcileStaleRunningAttempts` :203、namespace 放宽 `fetchPendingRecords` :230-241、**limit 防御** :230）；`RetryScannerImpl`（scanner/）（**单轮双上界 + RoundEndReason + 时钟 seam** `doScan` :255+、**参数 fail-fast** setter :66-140）；`RetryTaskImpl`（impl/）（提交面 failedStage 契约 `callAsync` :101）；attempt 超时上界键 `nop.retry.attempt.timeout-ms` = `RetryEngineConfigs`；新增覆盖键 `nop.retry.callback.max-depth` / `nop.retry.scanner.max-batches-per-round` / `nop.retry.scanner.max-round-duration-ms` = `RetryEngineConfigs` / `RetryScannerImpl` setter |

## 时钟口径（plan 2566 R4-32，1.6-C7-011）

首提租约写值（`RetryRecordStoreImpl.newRecord` 的 `nextTriggerTime` 起点）、续租写值（`renewLease`）与诊断读值统一经 `getCurrentTime()` = DB 估计钟 **max 侧**（`getDbEstimatedClock().getMaxCurrentTimeMillis()`，plan 2543 W-08 双缝写侧口径；读侧 reclaim 用 min 侧）。`RetryScannerImpl` 的 `lastScanFailureTimeMillis`（:199/:207/:328）为对外可观测诊断时间戳（getter 展示），保留墙钟 epoch 语义，不入单调换源面。测试锚点：`TestCraR432RetryClockPins`（红⑧ 形态：stub getDbEstimatedClock 已知 min/max，断言写侧取 max；变异回退本地墙钟即红）。

## 相关文档

- `../nop-job.md`
- `../reusable-modules-overview.md`
