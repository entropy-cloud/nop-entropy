# nop-job 状态机逻辑集中化分析：按实体收敛为状态机 helper

> Status: resolved（2026-08-04 已按 Option B 实施，见下）
> Date: 2026-08-04
> Scope: nop-job（nop-job-core / nop-job-dao / nop-job-coordinator / nop-job-worker / nop-job-service）
> Conclusion: 已实施：按实体拆分 3 个状态机类（JobTaskStateMachine / JobFireStateMachine / JobScheduleStateMachine，nop-job-dao/.../helper/），收敛谓词与转换合法性判断；带事务的写入动作保留在 store 层；旧 JobStatusHelper 已删除。

## Context

- 问题：nop-job 中 schedule / fire / task 三种状态的状态判断与转换逻辑散落在 10+ 个类中（BizModel、coordinator scanner、worker、store），同一语义（"是否终态"、"是否 active"）以不同形式重复出现，无法一眼看出整体状态机是什么。
- 决策点：能否收敛进一个（或少数几个）helper，让"状态机总体判断"集中在一起？
- 硬约束（用户明确）：**不同实体的状态判断必须分开**（Schedule / Fire / Task 各自独立），不要混杂成一个杂烩类。
- 现状起点：`JobStatusHelper`（nop-job-dao/.../helper/JobStatusHelper.java）已存在，集中了 5 个 fire/task 谓词，但覆盖面不全、且 fire 与 task 已混杂在一类中。

## Analysis

### 1. 三实体状态机全景

| 实体 | 状态值 | 转换事件 | 写入点（文件:行） |
|------|--------|---------|------------------|
| NopJobSchedule | DISABLED(0) / ENABLED(10) / PAUSED(20) / COMPLETED(30) / ARCHIVED(40) | enable / disable / pause / resume / archive | NopJobScheduleBizModel.java:62-146（5 个 BizMutation，含 validateScheduleStatus 校验 :182-203） |
| | | fire 完成且 result.completed（仅 ENABLED 下）→ COMPLETED | JobCompletionProcessorImpl.java:210-212（**无校验直接 set**） |
| NopJobFire | WAITING(0) / DISPATCHING(10) / RUNNING(20) / SUCCESS(30) / FAILED(40) / TIMEOUT(50) / CANCELED(60) | 创建 → WAITING | JobPlannerScannerImpl.java:194-217；JobScheduleStoreImpl.java:88-112, 114-153, 156-234, 236-295；NopJobFireBizModel.java:105-113（rerun） |
| | | WAITING → DISPATCHING | JobFireStoreImpl.java:84-99（tryLockFiresForDispatch） |
| | | DISPATCHING → RUNNING | JobFireStoreImpl.java:102-119（insertTasksAndMarkFireDispatching） |
| | | DISPATCHING → WAITING（无适配 worker 退避） | JobFireStoreImpl.java:247-258；JobDispatcherScannerImpl.java:187 |
| | | RUNNING → 聚合终态（SUCCESS/FAILED/TIMEOUT/CANCELED） | JobCompletionProcessorImpl.java:185-208（resolveFinalFireStatus :312-358）；schedule 已删除 → FAILED :165-171 |
| | | DISPATCHING → TIMEOUT（派发超时） | JobTimeoutCheckerImpl.java:324-337 |
| | | active → CANCELED | JobFireStoreImpl.java:144-207（cancelFire）；JobScheduleStoreImpl.java:418-439（overlay/recovery 内取消）；NopJobFireBizModel.java:77-92 |
| | | FAILED/TIMEOUT → WAITING（recovery 复用） | JobScheduleStoreImpl.java:196-233 |
| | | 任意 → FAILED（schedule 丢失） | JobFireStoreImpl.java:268-278 |
| NopJobTask | WAITING(0) / CLAIMED(10) / SUSPICIOUS(15) / RUNNING(20) / SUCCESS(30) / FAILED(40) / TIMEOUT(50) / CANCELED(60) | 创建 → WAITING | DefaultJobTaskBuilder.java:29；AdaptiveJobTaskBuilder.java:114；RpcBroadcastTaskBuilder；PartitionTaskBuilder |
| | | WAITING → CLAIMED | JobTaskStoreImpl.java:79-89（tryLockTasksForExecute） |
| | | CLAIMED → RUNNING（CAS 校验） | JobWorkerScannerImpl.java:252-265 |
| | | RUNNING/CLAIMED → SUSPICIOUS（worker 失联） | JobTimeoutCheckerImpl.java:243-261 |
| | | SUSPICIOUS → TIMEOUT | JobTimeoutCheckerImpl.java:403-431 |
| | | RUNNING → TIMEOUT | JobTimeoutCheckerImpl.java:433-501 |
| | | RUNNING → SUCCESS/FAILED（结果驱动） | JobWorkerScannerImpl.java:281-378（handleExecutionResult）；380-397（invoker 解析失败 → FAILED） |
| | | 未终态 → CANCELED | JobFireStoreImpl.java:164-187；JobScheduleStoreImpl.java:441-460；JobTimeoutCheckerImpl.java:349-376 |
| | | FAILED/TIMEOUT/CANCELED/SUSPICIOUS → WAITING（recovery 重置） | JobScheduleStoreImpl.java:393-412（resetFailedTasks） |
| | | WAITING → WAITING（清 worker 归因重派发） | JobTaskStoreImpl.java:143-168（resetStaleWaitingTasks） |

关键事实：**nop-job 没有"实例（instance）"实体**，状态机只覆盖上述 3 个 ORM 实体（`model/nop-job.orm.xml`）。`JobInstanceState`（nop-job-api）只是传给 IJobInvoker 的只读上下文快照，不参与状态机。

### 2. 分散度盘点：三类"分散"

#### 2.1 同一谓词语义的多重实现（最值得合并）

| 语义 | 已集中实现 | 内联重复实现（应替换） |
|------|-----------|----------------------|
| task 是否未终态（WAITING/CLAIMED/RUNNING 之外） | `JobStatusHelper.isFinishedTask` | JobTimeoutCheckerImpl.java:307-309、:353-355（三连 `!= WAITING && != CLAIMED && != RUNNING` 内联）；JobCompletionProcessorImpl.java:321-323 |
| task 是否并发终态（TIMEOUT/CANCELED/SUSPICIOUS） | `isConcurrentlyFinalizedTask`（worker 已用） | JobTimeoutCheckerImpl 未使用（SUSPICIOUS 分支自己判断） |
| task 是否可恢复 | `isRecoverableTask` | 无（唯一） |
| fire 是否 active（可取消） | `isActiveFire` | JobScheduleStoreImpl.java:365-366（`List.of(WAITING, DISPATCHING, RUNNING)` 内联枚举）；JobFireStoreImpl.java:302 |
| fire 是否终态（可 rerun） | `isTerminalFire` | JobWorkerScannerImpl.java:295-298（四值内联枚举）；JobCompletionProcessorImpl.java:204-208（内联 SUCCESS 判断） |
| fire 是否可恢复（FAILED/TIMEOUT） | 无 | JobScheduleStoreImpl.java:385-386（内联 `List.of(FAILED, TIMEOUT)`） |
| fire 状态聚合优先级（TIMEOUT>FAILED>CANCELED>SUCCESS） | 无 | JobCompletionProcessorImpl.java:312-358（私有方法） |
| schedule 是否 ENABLED / COMPLETED | 无 | DefaultJobCancelHandler.java:114-119、DefaultJobExecutionContextBuilder.java:98-103、JobCompletionProcessorImpl.java:174-175 各自内联 |

结论：`isFinishedTask` / `isActiveFire` / `isTerminalFire` 等谓词**名义上已集中，实际使用率不到一半**——一半调用点仍在写内联枚举。这是"看起来分散"的第一来源，且替换是纯机械的、零风险（有 `TestJobStatusHelper` 兜底）。

#### 2.2 转换动作分散（setStatus 散落 12+ 个方法）

`setScheduleStatus` / `setFireStatus` / `setTaskStatus` 的直接写入点：

- schedule：6 处（BizModel 5 + completion 1）
- fire：13 处（planner 创建、scheduleStore 4、fireStore 5、timeout 1、completion 1、bizModel 校验外）
- task：13 处（taskStore 2、worker 4、timeout 3、scheduleStore 2、fireStore 1）

每个写入点同时维护 3 类重复性代码：
1. **目标状态合法性预判**（如 cancel 前 `isCancelableFire`，JobFireStoreImpl.java:300-317 与 BizModel.java:79 双重校验，且后者更弱——只查 fire 状态，不查 task）
2. **终态附带字段**（endTime / durationMs / errorCode / errorMessage 的成组赋值，在 fireStore / scheduleStore / timeoutChecker / worker 四处各写一遍）
3. **并发冲突后处理**（`tryUpdateWithVersionCheck` 失败：JobFireStoreImpl.java:127-141 用 @SingleSession 语义放弃重试；JobScheduleStoreImpl.java:328-336 重试 5 次；JobWorkerScannerImpl.java:330-365 重新 load 再 CAS；JobTimeoutCheckerImpl 仅 warn）

#### 2.3 转换合法性校验缺失/不一致

- schedule 操作转换有完整校验（BizModel `validateScheduleStatus`），但 coordinator 的 ENABLED→COMPLETED（JobCompletionProcessorImpl.java:210-212）没有对应校验——它不是用户操作，是 fire 结果驱动，语义上应视为"事件驱动转换"而非"操作转换"。
- fire cancel 的合法性存在**两套**规则：BizModel 只查 `isActiveFire`，store 层 `isCancelableFire` 额外要求 RUNNING 且 task 全终态时不可取消。两处必须一致，否则前端提示与后端行为脱节。
- "事件 → 允许转换表"从未被显式建模：enable/disable/pause/resume 各自的 allowedStatuses 是逐方法手写数组（BizModel.java:64, 79-81, 95, 105, 137-141），只能通过读代码拼出状态机。

### 3. 为什么"分散"有结构性原因（不能全部收进一个纯 helper）

1. **事务边界**：fire/task 的状态变更必须与其归属 schedule 的计数器（activeFireCount/failFireCount/lastFireStatus）在同一事务内，否则崩溃后状态与计数不一致。写入点必然留在带 `@Transactional` 的 store 方法里（JobFireStoreImpl.java:83, 121, 143；JobScheduleStoreImpl 全部）。纯 helper 无法替代这一点。
2. **乐观锁 CAS 语义**：每次写入伴随版本检查 + 失败处理，且 @SingleSession 下重试无效（JobFireStoreImpl.java:124-141 的注释就是踩坑记录）。把写入动作集中到一个"统一 transition 方法"会破坏各调用点的 CAS 策略差异。
3. **跨实体联动**：fire 终态由 task 状态聚合而来（JobCompletionProcessorImpl.resolveFinalFireStatus），schedule 终态由 fire 结果驱动，cancel 要同时翻转 fire + task + schedule 计数。这些是"转换脚本"而非"单一实体转换"。
4. **分层职责**：BizModel 负责用户操作校验（错误码 + 提示），scanner 负责后台推进，worker 负责结果落库。谓词判断被复制是因为各层都需要做同一道防线（如 worker 拒绝覆盖已终态的 task）。

### 4. 方案对比

#### Option A：纯谓词收敛（现状 JobStatusHelper 的延伸）

- 核心思路：保留 JobStatusHelper 但不扩展成"按实体分节"，或拆成 `JobScheduleStatuses` / `JobFireStatuses` / `JobTaskStatuses` 三个小类；把所有内联枚举替换为 helper 调用；补齐缺失谓词（isRecoverableFire、isCompletedSchedule 等）。
- 优点：改动机械、风险极低、立刻消除重复；与既有 `TestJobStatusHelper` 测试模式一致。
- 缺点：只覆盖"判断"，不覆盖"转换"——validateScheduleStatus 的 allowedStatuses 数组、isCancelableFire 复合规则、resolveFinalFireStatus 聚合仍散落原位，"状态机总体判断"没有形成单一视图。
- 适用场景：作为任何更大方案的第一步。

#### Option B：每实体一个状态机类（推荐方向）

- 核心思路：按实体拆 3 个类（位于 nop-job-dao helper 包或 nop-job-core）：
  - `JobScheduleStateMachine`：谓词（isEnabled/isPaused/isArchived/isActive）+ 操作-源状态表（canEnable/canDisable/canPause/canResume/canArchive/canTriggerNow，替代 BizModel 手写数组）+ COMPLETED 事件规则。
  - `JobFireStateMachine`：谓词（isActive/isTerminal/isRecoverable）+ canCancel/canRerun 复合规则（吸收 isCancelableFire）+ `resolveFinalStatus(tasks)` 聚合函数（从 JobCompletionProcessorImpl 迁入，内部只依赖 Task 状态机谓词）。
  - `JobTaskStateMachine`：谓词（isFinished/isRecoverable/isConcurrentlyFinalized/isPending）+ canClaim/canComplete（防止覆盖并发终态）。
- 优点：状态机总体判断形成单一视图；BizModel 校验、store 预判、completion 聚合都从同一张表取值，杜绝两套 cancel 规则这类不一致；仍符合"按实体分开、不混杂"的约束。
- 缺点：改动面大于 A；复合/联动转换脚本（fire+task+counter 同事务）仍留在 store，helper 只负责"判断"，不负责"执行"。
- 适用场景：当"总体判断"是主要诉求、且接受渐进式迁移时。

#### Option C：转换动作也收拢（统一 transition 入口）

- 核心思路：在 store 层提供 `fireStore.transitionFire(fire, target, reason)` / `taskStore.transitionTask(task, target, reason)`，把所有 setStatus + 附带字段赋值收敛。
- 优点：写入点最少。
- 缺点：违背现状按场景拆分事务的设计（cancel 要联动 3 张表，与单任务终态写入不是一个事务粒度）；CAS 策略各场景不同，统一入口需要大量参数化；改动面最大，回归风险最高（worker/timeout/completion 的并发场景都有专门测试）。
- 结论：**不推荐**。

#### Comparison

| 维度 | A 纯谓词收敛 | B 按实体状态机类 | C 统一转换入口 |
|------|-------------|-----------------|---------------|
| 消除内联枚举 | 全量 | 全量 | 全量 |
| 形成状态机总体视图 | 部分（仅谓词） | 完整（谓词+转换表+聚合） | 完整 |
| 消除"两套 cancel 规则"类不一致 | 否 | 是 | 是 |
| 尊重事务边界 / CAS 差异 | 是 | 是 | 否（需打破） |
| 回归风险 | 低 | 中 | 高 |
| 改动量 | 小 | 中 | 大 |

### 5. "不要混杂"的边界设计

若实施 Option B，遵循以下边界：

1. **类维度按实体拆分**：`JobScheduleStateMachine` / `JobFireStateMachine` / `JobTaskStateMachine` 各一，不出现"三合一 helper"。
2. **跨实体引用只允许"状态机调用另一状态机谓词"**：如 `JobFireStateMachine.resolveFinalStatus(tasks)` 内部调 `JobTaskStateMachine.isTerminal(...)`，不允许实体状态字段互相 set。
3. **联动写入脚本不属于任何状态机**：cancel/overlay/recovery 这类同时改 3 张表的方法留在 store，状态机只提供其合法性判断（canCancel + 目标态常量）。
4. **现有 JobStatusHelper 的处置**：作为 Task/Fire 状态机方法的过渡壳保留（或直接迁移并删除，由测试文件 TestJobStatusHelper 改为测新类），避免双源。
5. **聚合规则（fire 终态优先级链）** 归 Fire 状态机，成为其公开 API，供 completion processor 与未来需要聚合的调用点共用。

## Conclusion

- 可以集中，但结论是**"集中成按实体的 3 个状态机 helper"，而不是一个全局大 helper**——这与用户的约束一致，也符合现有代码中 JobStatusHelper 的意图（只是它目前 fire/task 混杂且覆盖不全）。
- 推荐 Option B 为主、Option A 为前置步骤：先把内联枚举全部替换为已有/新增谓词（A，机械、低风险），再把转换表与聚合规则上收（B）。
- 否决 Option C：统一写入入口会破坏事务边界与 CAS 策略差异，回归风险不成比例。
- 不放入 helper 的部分（必须留在原位）：跨实体同事务写入、附带字段赋值脚本、CAS 失败处理、@SingleSession 语义相关逻辑。
- 后续工作（如获批）：指向 `ai-dev/design/` 状态机设计 + `ai-dev/plans/` 迁移计划；现有测试 `TestJobStatusHelper`、`TestJobStoreImpl`、`TestJobWorkerScanner`、`TestJobConcurrency`、`TestJobCompletionProcessor` 提供回归覆盖基线。

## Implementation Log（2026-08-04，Option B 已落地）

- **新增**（nop-job-dao/.../helper/）：`JobTaskStateMachine`（isPending / isFinished / isRecoverable / isConcurrentlyFinalized，isPending 与 isFinished 对非 null 严格互补；SUSPICIOUS 两边都返回 false）、`JobFireStateMachine`（isActive / isTerminal / isRecoverable / canRerun / canCancel(fireStatus, hasUnfinishedTask) / resolveFinalStatus(List\<Integer\>) + ACTIVE_STATUSES / RECOVERABLE_STATUSES 常量，List\<Integer\> 因 FilterBeans.in 需 Collection）、`JobScheduleStateMachine`（isDisabled / isEnabled / isPaused / isCompleted / isArchived + canEnable / canDisable / canPause / canResume / canArchive / canTriggerNow）。
- **迁移 9 个调用点**：`JobScheduleStoreImpl`、`JobFireStoreImpl`（isCancelableFire 委派 canCancel）、`NopJobFireBizModel`（validateRerunSchedule → canTriggerNow）、`JobWorkerScannerImpl`（fire 内联四值枚举 → isTerminal）、`JobTimeoutCheckerImpl`（两处内联三连 != → isFinished）、`JobCompletionProcessorImpl`（私有 resolveFinalFireStatus 删除，直调 resolveFinalStatus；scheduleEnabled → isEnabled）、`DefaultJobCancelHandler`、`DefaultJobExecutionContextBuilder`、`NopJobScheduleBizModel`（validateScheduleStatus 签名改 IntPredicate，删手写数组与 isScheduleStatus）。
- **删除**：`JobStatusHelper` + `TestJobStatusHelper`（迁移前 grep 确认零残留）。
- **验证**：nop-job-dao 68、nop-job-coordinator 162、nop-job-worker 36、nop-job-service 41 全绿；新增测试 TestJobTaskStateMachine(14) / TestJobFireStateMachine(18) / TestJobScheduleStateMachine(7)。
- **实施坑**：`List.of((Integer) null)` 抛 NPE（含 null 元素的测试用例须用 ArrayList）；service 测试首跑报 `duplicate-bean-definition:jobPartitionResolver` 系 `.m2` 陈旧 nop-job-coordinator jar 携带旧 app-engine 内联 bean 定义所致，重装该 jar 后消除（与本次代码改动无关）。

## Open Questions

- [x] 是否引入显式"事件"建模转换（DISPATCH / CLAIM / COMPLETE / CANCEL / TIMEOUT / RECOVER...），还是维持"目标状态 + 操作名"的轻量表？→ **裁定（2026-08-04，用户）：不引入 Java `enum`，事件若建模只用 `int` 常量**（与 `_NopJobCoreConstants` 全仓库风格一致，加进同一常量接口）。结合 nop-job 实际：fire/task 的目标状态基本可唯一反推事件（目标 CANCELED 只有 CANCEL），事件抽象额外信息有限，故默认走轻量表（方法名即操作）；若实施中确需区分"同一目标态的多触发源"，用 `int` 事件常量 + 查表，不做全量可达性校验也收益足够。
- [x] `resolveFinalFireStatus` 的聚合优先级链（TIMEOUT>FAILED>CANCELED>SUCCESS、SUSPICIOUS 视为 TIMEOUT）是否应在 Fire 状态机中固化为显式常量优先级表？→ **实施裁定（2026-08-04）：不做独立常量优先级表**。优先级链直接固化在 `JobFireStateMachine.resolveFinalStatus(List<Integer>)` 的实现里（TIMEOUT→FAILED→CANCELED→SUCCESS 顺序判断），与调用点单点、有 18 个新测试覆盖；再抽一层常量表反而增加间接性，收益不抵成本。
- [x] BizModel 的 `validateScheduleStatus` 手写数组与状态机转换表并存期间，是否需要审计脚本检测两者漂移？→ **实施裁定（2026-08-04）：问题已消失**。`NopJobScheduleBizModel.validateScheduleStatus` 重构为 `(schedule, action, IntPredicate canTransition)`，五个操作全部改为传入 `JobScheduleStateMachine` 的 canXxx 方法引用，手写数组已删除，单一事实源不复存在。
- [x] JobStatusHelper 迁移后删除还是保留为兼容壳？→ **实施裁定（2026-08-04）：直接删除**（含 `TestJobStatusHelper`），迁移前 grep 确认 4 模块零残留引用；其 5 个谓词由新状态机类等价承接。

## References

- `nop-job/nop-job-dao/.../helper/JobStatusHelper.java`（现状谓词工具，含语义注释）
- `nop-job/nop-job-dao/.../helper/TestJobStatusHelper.java`（既有测试）
- `nop-job/nop-job-dao/.../store/JobScheduleStoreImpl.java` / `JobFireStoreImpl.java` / `JobTaskStoreImpl.java`
- `nop-job/nop-job-coordinator/.../engine/JobPlannerScannerImpl.java` / `JobDispatcherScannerImpl.java` / `JobCompletionProcessorImpl.java` / `JobTimeoutCheckerImpl.java` / `DefaultJobCancelHandler.java`
- `nop-job/nop-job-worker/.../engine/JobWorkerScannerImpl.java` / `DefaultJobExecutionContextBuilder.java`
- `nop-job/nop-job-service/.../entity/NopJobScheduleBizModel.java` / `NopJobFireBizModel.java`
- `nop-job/nop-job-core/.../_NopJobCoreConstants.java`（全部状态常量定义）
- 相关对比：`ai-dev/analysis/2026-07-02-nop-job-code-quality-remediation-analysis.md`、`ai-dev/analysis/2026-08-04-nop-job-vs-powerjob-vs-snail-job-deep-analysis.md`
