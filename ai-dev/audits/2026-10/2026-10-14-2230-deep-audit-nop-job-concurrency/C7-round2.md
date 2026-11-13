# C7 长时运行稳定性维度 — nop-job 第 2 轮深挖（盲审）

- 审计单元：`nop-job` 聚合器（12 jar 子模块），src/main/java 手写代码 164 files / 17,664 lines
- 仓库：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation）
- 审计日期：2026-10-15
- 盲审纪律：未读取 ai-dev/audits（除本维度第 1 轮文件 C7-long-running.md，属任务指定的必要输入）、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何文件；只审当前 live code；`_` 前缀生成文件不作为发现对象
- 第 1 轮输入：C7-001~008（P1×1 + P2×6 + P3×1）及其正面结论清单；本轮只输出新发现（C7-009 起），不重复登记第 1 轮已立案形态（RpcPollTaskManager 的条目泄漏/WARN 放大/deadline=0 均归 C7-001 族，不重复立案）

---

## 1. 本轮发现

### [nop-job-C7-009] 触发器计算持久失败无隔离：completion 侧 5s 无退避重试环 + fire 永久滞留 RUNNING；planner 侧 60s 重试环 + 调度静默停摆（F3 兄弟清扫——C7-001 同族第二例）

- **严重度**：P2
- **缺陷族**：F3（无退避无上限无隔离的重试环）+ F4 伴随（stuck RUNNING fire 账本滞留）+ F5 伴随（fixed-delay 停摆不可见）
- **位置**：
  - nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCompletionProcessorImpl.java:110-117（scanBatch catch 后仅 WARN，fire 留 RUNNING）、199-201（fixed-delay 完成时构建触发器）、339-349（calculateFixedDelayNextFireTime）
  - nop-job/nop-job-dao/src/main/java/io/nop/job/dao/helper/TriggerSpecHelper.java:33、81-86（toTriggerSpec → parsePauseCalendars → JsonTool.parseBeanFromText，坏 JSON 抛 NopException）
  - nop-job/nop-job-core/src/main/java/io/nop/job/core/trigger/TriggerBuilder.java:29-72（buildTrigger 对坏 cron/坏 calendar 配置直接抛错）
  - 对照面：nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java:127-133（plan-schedule-failed 捕获环）、217-229（planner 侧同样每次构建触发器）
  - nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java:73-81（fetchRunningFires 按 fireStatus=RUNNING 捞取——stuck fire 每周期重新入队）
- **证据片段**（JobCompletionProcessorImpl.java:110-117、199-201）：
```java
            } catch (Exception e) {
                // plan 340 §2.7 (P2-6): include scheduleId so commit-time conflicts ... are correlatable
                LOG.warn("nop.job.completion.fire-complete-failed:fireId={},scheduleId={}",
                        fire.getJobFireId(), fire.getJobScheduleId(), e);
            }
...
            } else if (isFixedDelay(schedule)) {
                schedule.setNextFireTime(calculateFixedDelayNextFireTime(schedule, fireEndTime));
            }
```
```java
    // TriggerSpecHelper.java:81-86 —— pauseCalendarSpec 是裸 JSON 字符串列，坏 JSON 在每次触发器构建时抛错
    private static List<CalendarSpec> parsePauseCalendars(String json) {
        if (json == null || json.isEmpty())
            return Collections.emptyList();
        return JsonTool.parseBeanFromText(json,
                JavaGenericTypeBuilder.buildListType(CalendarSpec.class));
    }
```
- **增长/劣化链**：
  1. 前提（持久失败源）：`pauseCalendarSpec`/`resultPayload` 等裸 JSON 列损坏（通用 GraphQL update 路径不经 `recalculateNextFireTime` 校验——它只在 enable/resume 时跑；直接 DB 写/Delta 亦无校验），或 calendar 配置非法（如 `DailyCalendarSpec` 越界时刻，`CalendarBuilder` 抛错）。全链无 quarantine/最大尝试次数/死信机制。
  2. **completion 侧（5s 全速环）**：`completeSingleFire`（REQUIRES_NEW）在 `calculateFixedDelayNextFireTime`/`resolveCompletionDecision` 处抛错 → 事务回滚、fire 未终态化 → fire 留 RUNNING → 下一 5s 扫描周期 `fetchRunningFires` 重新捞取 → 再次抛错。每个 stuck fire 每 5s 消耗 1 个事务 + ≥3 次 DB 读（getFire/findTasksByFireId/tryLoadSchedule，fire 的全部 task 行全量加载）+ 1 条带堆栈 WARN（17,280 条/天/fire），无任何退避或升级。注意 FIXED_DELAY 的触发器构建**只在完成时发生**（planner 侧 `calculateNextFireTime` 对 FIXED_DELAY 提前短路返回 null，JobPlannerScannerImpl.java:218-221）——配置损坏可以穿过 planning，首次暴露即在完成路径。
  3. **planner 侧（60s 有界环，对照项）**：CRON schedule 配置损坏 → `planSchedule` 抛错被 scanBatch 捕获（127-133）→ 但 `tryLockSchedulesForPlan` 已在独立 REQUIRES_NEW 事务中把 `nextFireTime` 写为锁 deadline（now+60s）→ 60s 后重新 due → 再失败。隐式 60s 节流 + ERROR 1440 条/天/schedule，**该 schedule 从此不再产出任何 fire（作业静默停摆）**，唯一痕迹是周期 ERROR。
  4. 后果：stuck fire 永久滞留 RUNNING（无任何墙钟回收覆盖 RUNNING fire——dispatch-timeout 只覆盖 DISPATCHING）→ `activeFireCount` 恒 >0 → DISCARD 调度被 `shouldDiscard` 永久卡死（与 C7-005 的停摆形态同流）；FIXED_DELAY 调度 `nextFireTime=null` 永不 due，零日志静默死亡。时间尺度：配置损坏发生即刻触发，**无限期持续**（直到人工修数）。
- **判级理由**：P2——触发前提是边界配置缺陷（JSON 列损坏/非法 calendar 配置可绕过保存校验进入 DB），非包络内必然；但一旦触发即为 5s 全速、无退避、无上限、无隔离的重试环 + fire 账本永久滞留，且 FIXED_DELAY 形态的调度死亡完全静默。不判 P1：触发前提非常态（对照 C7-001 的"远端失联长时运行近乎必然"），且 stuck fire 数量受损坏时刻在途 fire 数约束（cron 型会被 planner 环先拦截、fixed-delay 型停在 1 个），无单调累积。
- **边界声明对照**：owner doc 未声明"触发器计算失败的调度生命周期语义"（无 quarantine/停摆声明）；check2 注释（plan 340 §2.3 P2-2）只对 recovery-skip 分支补了 nextFireTime 推进防紧循环，completion/planner 的持久失败分支无对应防护。
- **误报校准对照**：非低频路径粗锁（completion 扫描每 5s 高频执行）；非有界声明结构（无任何上限声明）；非平台设施自身实现（JobCompletionProcessorImpl/TriggerSpecHelper 均为 nop-job 模块代码）。
- **修复方向**：(a) completion/planner 捕获 `NopException(ERR_JOB_TRIGGER_*)` 等配置类错误时终态化 fire（FAILED + 错误码）或把 schedule 置 PAUSED + 告警，阻断重试环；(b) 对同一 fire 的连续 fire-complete-failed 计数，超过 N 次后升级 ERROR + 指标暴露；(c) 保存/更新路径对 `pauseCalendarSpec` 做一次触发器试构建校验（对齐 enable/resume 已有先例）。
- **置信度**：高（代码路径确凿；触发前提依赖配置损坏进入路径，已核实通用 update 不做触发器校验）。
- **影响面**：所有分布式部署（cron 型表现为 planner 环 + 停摆，fixed-delay 型表现为 completion 环 + 静默死亡）；本地模式不涉及（无 completion 回路，且 C7-006 已覆盖其失败终局语义）。
- **残留问题**：`rerunFire`/retry bridge 重跑路径若遇到同类配置错误是否终局，需结合 nop-retry 策略配置确认（平台设施边界，未展开）。
- **复核状态**: 已保留（P2 维持。全链逐点核实：completion catch 仅 WARN（JobCompletionProcessorImpl L110-117）+ completeSingleFire REQUIRES_NEW 回滚致 fire 留 RUNNING + fetchRunningFires 每周期重捞（JobFireStoreImpl L72-81）；FIXED_DELAY 触发器仅完成时构建（planner L217-221 对 FIXED_DELAY 短路返回 null、completion L199-201/L339-349）——"首次暴露即在完成路径"成立；planner 侧锁写（REQUIRES_NEW 独立提交 nextFireTime=now+60s，JobScheduleStoreImpl L66-82）先于 planSchedule 抛错（L127-133 捕获）→ 60s 隐式节流 + ERROR 1440 条/天；TriggerSpecHelper.parsePauseCalendars L81-86 坏 JSON 抛错、TriggerBuilder 坏 cron/calendar 抛错核实；触发器校验仅 enable/resume 存在（NopJobScheduleBizModel L68/L107），通用 GraphQL update 路径无校验；RUNNING fire 无墙钟回收（dispatch-timeout 仅覆盖 DISPATCHING，JobTimeoutCheckerImpl L166-174）。stuck fire 数量有界、无累积，不判 P1 的论证成立。现状：新发现）

### [nop-job-C7-010] planner 乐观锁 fence 过期窗口：滞后 planner 可插入重复 fire 且 `hasWaitingFire`/唯一键均不拦——同一触发槽双执行（fencing 正面结论的边界抽验）

- **严重度**：P2
- **缺陷族**：竞态（fence TTL 过期后的后半程无保护）→ 半个成功的镜像形态（半个重复：一次触发两次执行）
- **位置**：
  - nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java:66-82（tryLockSchedulesForPlan：nextFireTime=锁 deadline 的版本 CAS，REQUIRES_NEW 独立提交）、94-99（hasWaitingFire 仅匹配**相同** scheduledFireTime）、101-114（saveEntityDirectly 无任何前置状态复查）
  - nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java:90-104（锁写与 planSchedule 之间的窗口）、200-214（insertFire 路径无 post-fence 复查）
  - 对照组（有后置复查的先例）：nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java:101-115（insertTasksAndMarkFireDispatching 先 requireEntityById 校验 DISPATCHING + 版本 CAS）
  - 唯一键前提：nop-job/model/nop-job.orm.xml:323-327（UK_NOP_JOB_FIRE_SCHEDULE_TIME_SOURCE = jobScheduleId,scheduledFireTime,triggerSource）
- **证据片段**（JobScheduleStoreImpl.java:76-81、94-101）：
```java
        long now = dao.getDbEstimatedClock().getMaxCurrentTimeMillis();
        Timestamp lockTime = new Timestamp(now + Math.max(lockTimeoutMs, 1));
        for (NopJobSchedule schedule : schedules) {
            schedule.setNextFireTime(lockTime);
        }
        return dao.tryUpdateManyWithVersionCheck(schedules);   // fence = 版本 CAS，TTL = lockTimeoutMs(默认60s)
...
        if (hasWaitingFire(schedule.getJobScheduleId(), fire.getScheduledFireTime(), fire.getTriggerSource())) {
            ... // 去重只匹配「相同 scheduledFireTime」——fence 过期后两次 plan 的时间戳不同，不命中
        }
        fireDao().saveEntityDirectly(fire);   // 无 DISPATCHING 式 post-fence 状态复查
```
- **增长/劣化链**：双 coordinator 部署。planner A 完成 lock CAS（v1→v2，nextFireTime=originalDue+60s）后在 planSchedule 内停顿 ≥60s（GC 风暴/VM 冻结/SIGSTOP/极端 DB 延迟）→ 锁 deadline 到期，planner B fetch 到该 schedule（snapshot = A 的 lockDeadline）→ B 的 lock CAS 成功（v2→v3）→ B 插入 fire2（scheduledFireTime=lockDeadline）并推进 nextFireTime → A 苏醒，以**过期实体**继续：`calculateNextFireTime`/`shouldDiscard` 全部基于陈旧快照（A 的 activeFireCount 可能仍为 0，DISCARD 判空通过），`hasWaitingFire(S, originalDue)` 查不到 B 的 fire2（scheduledFireTime 不同）→ **插入 fire1（scheduledFireTime=originalDue）**。数据库唯一键 UK(jobScheduleId,scheduledFireTime,triggerSource) 对两个不同时间戳的 fire 不构成约束 → 两笔 fire 各自派发执行 → **同一逻辑触发槽业务副作用执行两次**。A 的 schedule 计数更新经 `updateWithRetry`（IOrmEntityDao.orm_unload 重载重试，IOrmEntityDao.java:52-63/105-113）最终成功，账本最终一致（reconciler 兜底），无残留漂移——纯粹的重复执行形态。时间尺度：单次事件级，不累积；前提为 ≥lock-timeout-ms（默认 60s）的 planner 线程停顿 + 多 coordinator，周-月级运行中低概率但可具体化（发布期 GC/检查点暂停、DB failover 抖动叠加）。
- **判级理由**：P2——按 campaign 口径"罕见但可具体化竞态"可达 P1，但本条后果限单次触发槽双执行、无账本损伤、无增长（与第 1 轮 C7-003 的判级先例一致：罕见竞态 + 后果有界 → P2），故判 P2 并明确标注为第 1 轮正面结论 5（"双 coordinator 由 planner 乐观锁 + 版本检查防护"）的边界：版本检查只保护 lock 写本身，**lock TTL 过期后的 fire 插入半程无任何保护**，且与 dispatch 侧（insertTasksAndMarkFireDispatching 有 post-fence 状态复查）形成防护不对称。
- **边界声明对照**：owner doc"fire 计数自愈"节与第 1 轮正面结论均未声明 fence 的 TTL 语义与过期窗口；`lock-timeout-ms` 在 owner doc 中仅表述为"Planner 乐观锁超时"，未说明其作为租约的失效含义。
- **误报校准对照**：非低频路径粗锁（本条正是"锁过期"形态的教科书触发面）；非有界声明结构；非平台设施（planner 链路为 nop-job 模块代码；`updateWithRetry` 的重载语义已核实不构成额外保护）。
- **修复方向**：(a) 对齐 dispatch 先例：`insertFireAndAdvanceSchedule` 插入前 requireEntityById 复查 `nextFireTime` 仍等于本 planner 写入的 lockDeadline（值匹配 CAS），不等则放弃本槽；(b) 或插入后校验 schedule 版本仍为本 planner lock 写的版本，失败则删除/取消刚插入的 fire；(c) 文档声明 lock 的租约语义与 60s 过期窗口的重复执行残余风险。
- **置信度**：中高（竞态窗口与两条去重防线的缺口均由代码逐行核实；实际触发频率依赖 ≥60s planner 停顿的发生率）。
- **影响面**：多 coordinator 部署（单实例部署 A 即唯一 planner 线程，停顿期间无人接管，不触发）。
- **残留问题**：`insertManualFire` 的 triggerSource=MANUAL + scheduledFireTime=now 形态下同毫秒双触发撞 UK 抛错（ surfacing 为用户可见错误，非静默），不立案。
- **复核状态**: 已保留（P2 维持，从严验证通过。时序逐步重构成立：锁 = 版本 CAS 写 nextFireTime=lockDeadline（REQUIRES_NEW，JobScheduleStoreImpl L66-82，默认 60s）→ planner A 在 planSchedule 内停顿 ≥60s → B fetch（snapshot=A 锁 deadline，fetchDueSchedules 按 nextFireTime≤now）→ B CAS v2→v3 成功并插入 fire2（scheduledFireTime=A 锁 deadline）→ A 以过期实体续跑：hasWaitingFire 仅精确匹配同 scheduledFireTime（L360-370）查不到 fire2，saveEntityDirectly（L101）前无任何 post-fence 复查；UK_NOP_JOB_FIRE_SCHEDULE_TIME_SOURCE=(jobScheduleId,scheduledFireTime,triggerSource)（model/nop-job.orm.xml L322-326）对两个不同时间戳不构成约束——UK 不拦截确凿。A 的 fire1 插入与计数更新同事务，仅 updateWithRetry 5 次耗尽才连带回滚（需 B 在该瞬间并发写，非典型），orm_unload 重载重试不构成额外防护——重复 fire 存活链成立。正向补充：fetchDueSchedules 用 min 时钟而锁 deadline 用 max 时钟，实际过期窗口 ≥lock-timeout-ms，只会加宽。once 型经 isTriggerExhausted 陈旧检查天然豁免、周期型暴露，与本条影响面一致；dispatch 侧防护不对称（insertTasksAndMarkFireDispatching L101-115 有 requireEntityById+DISPATCHING 复查）核实。后果限单槽双执行、无账本损伤，与 C7-003 判级先例一致，P2 恰当。现状：新发现）

### [nop-job-C7-011] CLAIMED 态任务对存活 worker 无任何回收路径：liveness 要求 worker 消失、执行超时被 `isRunning` 谓词排除（与 store javadoc 声明直接矛盾）、stale 重置只覆盖 WAITING——fire 永久 RUNNING + 并发/资源额度永久占用（F5 盲区）

- **严重度**：P2
- **缺陷族**：F5（不产出也不报错的滞留无看门狗）+ 自愈语义（宣称的回收链对子状态失效）
- **位置**：
  - nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java:470-472（`if (!isRunning(taskStatus)) return;`——CLAIMED(10) 被墙钟超时路径整体排除）
  - nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobTaskStoreImpl.java:89-91（认领 javadoc 声明"CLAIMED态的回收依赖worker存活链（SUSPICIOUS→TIMEOUT）**或执行超时配置**"——后半句与实现不符）、184-223（resetStaleWaitingTasks 仅 WAITING）
  - nop-job/nop-job-dao/src/main/java/io/nop/job/dao/helper/JobTaskStateMachine.java:62-64（isRunning 仅匹配 RUNNING(20)）、37-39（IN_FLIGHT 含 CLAIMED → maxConcurrency 占用）
  - nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java:212-227（每批最多 100 个 task CAS 认领后串行执行——crash 窗口）
  - nop-job/nop-job-core/src/main/java/io/nop/job/core/NopJobCoreConstants.java:40-44（RESERVED_TASK_STATUSES 含 CLAIMED → 资源预留占用）
- **证据片段**（JobTimeoutCheckerImpl.java:465-472 与 JobTaskStoreImpl.java:88-91）：
```java
    private void tryMarkTimeout(NopJobTask task, ...) {
        Integer taskStatus = task.getTaskStatus();
        ...
        if (JobTaskStateMachine.isSuspicious(taskStatus)) {
            markSuspiciousAsTimeout(task, fireMap, scheduleMap);
            return;
        }
        if (!JobTaskStateMachine.isRunning(taskStatus)) {
            return;          // CLAIMED(10)：连「配置了 timeoutSeconds/executionTimeoutMs」的部署也不回收
        }
```
```java
    /**
     * CAS认领（WAITING→CLAIMED + workerInstanceId）。...CLAIMED态的回收依赖
     * worker存活链（SUSPICIOUS→TIMEOUT）或执行超时配置，而非认领租约超时
     * （租约机制需设计引入，接口保留参数以兼容既有签名）。
```
- **增长/劣化链**：worker 在 `tryLockTasksForExecute`（批量 CAS 认领，单批 ≤100 行）返回后、逐 task 的 CLAIMED→RUNNING CAS（JobWorkerScannerImpl.java:287）之前崩溃/被杀（loadFire+loadSchedule+resolveInvoker+buildContext 的窗口，批内串行放大窗口宽度）→ 该批剩余 task 永久 CLAIMED 且 `workerInstanceId`=旧实例。此后四条回收链全部失效：(1) liveness 链要求 worker **从 alive 集消失**——进程以同 identity 重启快速重注册（hostId 持久化场景）或注册客户端缓存覆盖了 ≥1 个扫描周期（默认 5s）时，永不标记 SUSPICIOUS；(2) `namingService` 未接线（`@Inject @Nullable` 合法装配，WARN 一次即关闭链路）时 liveness 整体不可用，而本应兜底的执行超时**对 CLAIMED 不生效**（isRunning 门）；(3) AR-88 `resetStaleWaitingTasks` 只重置 WAITING；(4) 认领租约未实现（javadoc 自认）。后果：fire 永久 RUNNING（聚合永不完成）→ DISCARD 调度经 `activeFireCount>0` 永久卡死；`countInFlightTasks`（maxConcurrency>0 时）与 `sumReservedCost`（配置 cost 的 worker）中该批 CLAIMED 行**永久计入**，额度单调流失——反复 crash-loop 的发布（OOM kill 循环）每次流失最多一批（batch-size=100），最终 worker 满载拒绝一切新任务（唯一信号是 rejected counter，JobWorkerScannerImpl.java:143-145）。零观测：无 CLAIMED-存活时长 metric，超时扫描器明明每轮捞到这些行（RUNNING_LIKE_STATUSES 含 CLAIMED、startTime 认领时已写）却在谓词处静默丢弃。时间尺度：**永久**（直到人工干预或 row 手工修复）；额度流失按 crash 次数累积。
- **判级理由**：P2——触发前提是"crash 精确落在认领→运行窗口"与（或）"liveness 缺失/未被重启间隙观察到"的复合前提，非常态必然；但后果链（fire 停摆 + 额度永久占用 + 修复需手工修数）具体且不可自愈，且**模块自身 javadoc 宣称的第二条回收路径（执行超时配置）实际不存在**——声明与实现的落差正是本轮定向核查目标。不判 P1：单事故增量有界（≤batch-size），非无界账本增长。
- **边界声明对照**：owner doc"超时语义（三层独立）"节声明"未配任何执行超时的长任务……由 worker-liveness 链兜底"，未声明 CLAIMED 态被墙钟超时排除；JobTaskStoreImpl.java:89-91 的 javadoc **明确声称**执行超时覆盖 CLAIMED 回收——与 JobTimeoutCheckerImpl.java:470-472 直接矛盾（docs bug 性质：同仓库内 owner-adjacent 声明失真）。
- **误报校准对照**：非低频路径粗锁（超时扫描每 5s 高频、数据在手）；非有界声明结构；非平台设施（谓词与扫描器均为模块代码）。
- **修复方向**：(a) `tryMarkTimeout` 对 CLAIMED 同样应用墙钟（`startTime` 已在认领时写入，语义成立），或新增认领租约参数（接口签名已预留 `lockTimeoutMs`）；(b) 暴露 per-status in-flight 年龄 gauge（CLAIMED 最老存活时间）；(c) 修正 JobTaskStoreImpl 认领 javadoc 与 owner doc 超时小节。
- **置信度**：高（谓词、常量集、四条回收链的覆盖面均逐行核实）。
- **影响面**：所有分布式部署；`maxConcurrency>0` 或配置 cost 的 worker 额外承受额度流失；liveness 未接线的部署（合法装配形态）风险最大。
- **残留问题**：`AppConfig.hostId()` 缺省为进程内 UUID 并尝试回写 config provider——重启后 identity 是否稳定取决于平台 config provider 的持久化形态（平台设施边界）；若恒为新 UUID，场景 (1) 中旧行会经 liveness 回收，但场景 (2)（namingService 未接线）不受影响，结论主体不变。
- **复核状态**: 已保留（P2 维持。四条回收链逐一排除核实：(1) liveness 需 worker 从 alive 集消失（isInFlight 含 CLAIMED 成立，JobTaskStateMachine L93-97；但同 identity 快速重注册/注册缓存覆盖 5s 窗/namingService 未接线 @Inject @Nullable 合法装配（JobTimeoutCheckerImpl L87-90，WARN 仅一次 L271-275）任一形态即失效）；(2) 墙钟执行超时被 isRunning 门排除（JobTimeoutCheckerImpl L470-472；isRunning 仅匹配 RUNNING(20)，JobTaskStateMachine L62-64）——与 JobTaskStoreImpl L88-91 认领 javadoc"或执行超时配置"直接矛盾，逐字核实；(3) resetStaleWaitingTasks 仅 WAITING（JobTaskStoreImpl L189）；(4) 认领租约未实现（javadoc 自认，lockTimeoutMs 参数不参与 L102-113）。"数据在手却静默丢弃"核实：fetchRunningTasks 的 RUNNING_LIKE_STATUSES 含 CLAIMED + 认领时写 startTime（L107-112），CLAIMED 行每轮入批、在 tryMarkTimeout 谓词处 return。额度占用核实：IN_FLIGHT_STATUSES 含 CLAIMED（countInFlightTasks）+ RESERVED_TASK_STATUSES 含 CLAIMED（NopJobCoreConstants L40-44，sumReservedCost）。仓库内自证：JobWorkerScannerImpl L231-233 注释明写"任务会永久滞留CLAIMED（无任何回收路径覆盖该态——liveness链/超时检查/stale重置均不命中）"。历史对照：AR-88（2026-06-19）系 WAITING 反方向且已修（resetStaleWaitingTasks 即其修复），CLAIMED 面无历史审计登记编号。复合前提 + 单事故增量有界（≤batch-size），P2 恰当。现状：新发现——附注：in-code 注释（72b770b094 引入）已自认该缺口"属另一课题"，属代码内已知未修但无历史发现编号，按口径标新发现）

### [nop-job-C7-012] 循环路径无采样/无抑制 WARN 站点矩阵残留（C7-001 同族第 2/3 例，量级受限于扫描频率或业务速率）

- **严重度**：P3
- **缺陷族**：F4 伴随（日志放大，第 1 轮未系统盘点的专项收尾）
- **位置**：
  - nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java:291-294（resolveAliveWorkerIds 捕获后每批 WARN）
  - nop-job/nop-job-api/src/main/java/io/nop/job/api/log/JobLogReporter.java:70-84（每次上报失败 WARN，速率=业务日志速率）
  - 对照（已立案/排除面）：RpcPollTaskManager.java:199/205（per-entry/5s——C7-001 已立案）；JobWorkerScannerImpl.java:155/205（每周期 1 条，AR-93 设计信号，排除）
- **证据片段**（JobTimeoutCheckerImpl.java:278-294）：
```java
        try {
            String svcName = StringHelper.isEmpty(workerServiceName) ? AppConfig.appName() : workerServiceName;
            List<ServiceInstance> instances = namingService.getInstances(svcName);
            ...
        } catch (Exception e) {
            LOG.warn("nop.job.timeout.resolve-workers-failed", e);   // 注册中心持续不可用时：每批/每 5s 一条带堆栈 WARN，无采样无抑制
            return null;
        }
```
- **增长/劣化链**：(a) 注册中心故障持续 D 小时 + 存在 ≥1 个 RUNNING/CLAIMED task（批非空才触发）→ 17,280×D 条带堆栈 WARN/天/节点；(b) `nop.job.log.report-url` 配置的目标端宕机 + 业务高频调用 `reportAsync` → WARN 速率 = 业务日志产生速率，无上限无采样（默认关闭，显式启用后暴露）。两者均无结构性增长（内存/账本不变），纯日志洪泛 + 排障信号淹没（真实告警被同型 WARN 埋没）。
- **判级理由**：P3——监控盲区/优化候选；速率上界明确（扫描频率/业务速率），无内存或账本后果。
- **边界声明对照**：未声明；owner doc 对 JobLogReporter 声明了"上报失败 best-effort（异步 + WARN 日志）"——行为已声明，未声明的只是无采样放大量级。
- **误报校准对照**：按"信息性日志/平台设施边界"口径不升级：resolve-workers-failed 的触发前提是外部注册中心故障（自身行为正确），JobLogReporter 属可选功能且调用方为业务代码。
- **修复方向**：(a) 两站点加"同因连续失败"节流（如每 N 次或每 T 采样 1 条 + 计数器）；(b) 或暴露 `resolve-alive-failed` counter 代替逐条 WARN。
- **置信度**：高。
- **影响面**：全部部署（(a) 仅 liveness 已接线且有在途任务时；(b) 仅显式启用日志上报时）。
- **残留问题**：无。
- **复核状态**: 已保留（P3 维持。resolve-workers-failed 每批 WARN 无采样（JobTimeoutCheckerImpl L291-294，catch→warn→return null）、JobLogReporter report-failed/report-http-failed 按业务日志速率 WARN 无上限（L70-84）核实；两站点速率上界（扫描频率/业务速率）明确、无内存/账本后果；JobLogReporter best-effort 行为已声明、仅放大量级未声明的校准准确，P3 恰当。现状：新发现）

---

## 2. 排除项/定向核查

本轮按深挖指令完成的定向核查与排除判定（均为复核，不立案）：

1. **F3 兄弟清扫矩阵（退避缺失面全量盘点）**：以全量 grep（LOG.warn/error × 调用环位置）+ 精读核对，站点判定如下——(a) planner no-fitting-worker revert：revert 后 30s backoff 窗口（`fetchWaitingFires` 过滤 startTime），有界，**合格**；(b) dispatcher per-fire dispatch 失败：fire 留 DISPATCHING、由 5min dispatch-timeout 终态回收，重试 ≤60 次/fire 有界，**合格**；(c) timeout checker 任务级更新冲突 WARN：版本冲突本质瞬态（对端推进即终止），**合格**；(d) reconciler 版本冲突 WARN：窗口内有界，**合格**；(e) completion/planner 触发器计算失败环：**立案 → C7-009**；(f) rpcPoll poll-failed 5s 环：C7-001 已立案（其 `applyStatus` default 分支对未知状态码静默续轮询，为 C7-001 族 deadline=0 形态的注记，不另立案）；(g) JobLogReporter/resolve-workers-failed：**立案（P3）→ C7-012**。
2. **日志放大专项（第 1 轮未系统盘点的方向）**：全量 87 个 LOG.warn/error 站点逐一定位调用环（扫描周期内/终态一次性/关闭期）。一次性与终态路径（cancel-failed、alarm-failed、scan-failed、生命周期 stop-component-failed 等）天然有界，排除；周期性站点结论见上矩阵。`worker-liveness-disabled` WARN 复核确认为 `AtomicBoolean` 仅一次（JobTimeoutCheckerImpl.java:51、271），第 1 轮正面结论维持。
3. **自愈清理面（运行期对应关闭语义）**：(a) `LocalJobScheduler.removeJob/deactivate`：pending timer future 经 `cancelScheduledFire` 取消 + 触发体双重守卫（`!active || internal != WAITING`，LocalJobScheduler.java:301-310、382-388），在途 promise 由 handleResult 的 `jobs.containsKey` 守卫自然落地不重排程（371-374）——**干净**；(b) 扫描器 `stopScanning` 取消 scanFuture（AbstractBatchScanner.java:67-73）；JobCoordinator.doStop 逐组件 try/catch 隔离——**干净**；(c) Cursor 每周期 reset（scanOnce 入口，88-92）+ null-sort-key 防御性 markDrained（240-248，不推进但终止子扫描，不产生重扫环）——**干净**；(d) worker 无内存级在途注册表（并发经 DB count），无额外可泄漏结构；在途 promise 不终结面属 C7-002 已立案；(e) RpcPollTaskManager cancelEntry 的句柄取消 + cancelToken 监听器摘除自洽（135-151），DB 终态联动缺失属 C7-001 已立案。
4. **fencing 正面结论的 DB 兜底前提复核**：4 张表均有 `version` BIGINT 列且实体声明 versionProp（model/nop-job.orm.xml:85/228/360/486）——乐观锁前提**成立**；UK_NOP_JOB_FIRE_SCHEDULE_TIME_SOURCE 与 UK_NOP_JOB_TASK_FIRE_NO 唯一键存在，精确去重前提**成立**；worker 认领为单步版本 CAS、AR-88 resetStaleWaitingTasks 版本 CAS、dispatch 有 post-fence 状态复查——均**成立**；**planner lock TTL 过期窗口不设防 → C7-010**。
5. **CronExpression（vendored，按集成边界口径抽验）**：入口无自备时钟（由调用方注入 `scheduleStore.getCurrentTime()`）；年内回退环有 `MAX_YEAR=2099` 硬上界（CronExpression.java:77）；DST 重叠日回退环有 `count++ < max` 上界（:285）； BitSet 年域构造限定 MIN_YEAR..MAX_YEAR（:365、451-453）。未逐分支审计时区/DST 语义（维持第 1 轮残留边界口径），无新的无界环/挂死点。
6. **metrics 标签基数**（第 1 轮 G6 复核）：JobPlannerMetricsImpl/JobWorkerMetricsImpl 等全部为构造期固定名 counter/timer，零标签——**维持合格**。
7. **retry bridge 边界复核**：NopRetryJobRetryBridge 幂等 id=fireId、仅 SCHEDULE 来源触发、重试节奏委托 nop-retry 策略引擎（平台设施）——第 1 轮正面结论维持。
8. **resetStaleWaitingTasks 与认领 CAS 的竞态**：双向均为版本 CAS（先到者赢、后到者版本冲突跳过），无重复认领/覆盖窗口——**干净**。
9. **AppConfig.hostId**：缺省 UUID + 回写 config provider（nop-api-core AppConfig.java:46-56），重启 identity 稳定性依赖平台配置提供者形态——作为 C7-011 的前提注记，不单独立案（平台设施边界）。

---

## 3. 终止声明

C7 维度第 2 轮深挖在五个追加方向（F3 兄弟清扫、日志放大专项、自愈清理面、fencing 边界抽验、CronExpression 边界抽验）上已完成定向覆盖，新增 P2×3（C7-009/010/011）+ P3×1（C7-012），均为第 1 轮未覆盖的独立缺陷面而非已有发现的复读。剩余未审面（nop-job-app 示例、src/test、ORM 索引设计对 C7-004 的影响、平台 executor/namingService 自身实现）均已在第 1 轮残留边界中声明且不属于本单元 src/main 手写代码的 C7 缺陷面。**未发现新的高价值问题。C7 维度深挖终止。**

---

## 4. 覆盖声明

**本轮新增精读/复核**：JobCompletionProcessorImpl（全文）、JobPlannerScannerImpl（全文）、JobTimeoutCheckerImpl（全文）、JobScheduleStoreImpl（全文）、JobTaskStoreImpl（全文）、JobFireStoreImpl（全文）、JobWorkerScannerImpl（全文）、LocalJobScheduler（全文）、AbstractBatchScanner（全文）、RpcPollTaskManager（全文，C7-001 交叉核对）、DefaultRpcPollTaskClient（全文）、JobCoordinator（全文）、TriggerBuilder（全文）、TriggerSpecHelper（全文）、JobTaskStateMachine（全文）、NopJobScheduleBizModel（全文）、NopRetryJobRetryBridge（全文）、JobLogReporter（全文）、JobPlannerMetricsImpl/JobWorkerMetricsImpl（全文）、model/nop-job.orm.xml（版本列/唯一键/索引节）、IOrmEntityDao（updateWithRetry/tryUpdateWithVersionCheck 契约）、AppConfig.hostId、CronExpression（环上界定向抽验）、JobDispatcherScannerImpl（scanBatch 节，退避复核）。

**grep 全量**：LOG.warn/LOG.error 87 站点逐一定位调用环；scheduled/scheduledWithFixedDelay/ThreadLocal/computeIfAbsent/synchronized 站点对照基线文件 `_tmp/cra-baseline-nop-job.txt` 复核；delete/purge/cleanup/retention 路径复核（维持 C7-004 结论）。

**未审面（与前轮一致并继承其边界声明）**：nop-job-app 示例、src/test、`_` 前缀生成物、平台设施自身实现（GlobalExecutors/IScheduledExecutor/INamingService/IRpcServiceInvoker/JDBC 超时/config provider 持久化形态——分别归 C4/平台单元）、ORM 索引设计（交叉项归 ORM 模型维度）。
