# 105 nop-job Round 5 Adversarial Review & Deep Audit Remediation

> Plan Status: planned
> Last Reviewed: 2026-06-03
> Source: `ai-dev/audits/2026-06-03-adversarial-review-nop-job/01-open-findings.md` (Round 5: 11 new findings: AR-25~AR-35), `ai-dev/audits/2026-06-03-deep-audit-nop-job/summary.md` (21-dim: 47 findings), `ai-dev/audits/2026-06-03-deep-audit-nop-job-full/summary.md` (49 findings)
> Related: `ai-dev/plans/103-nop-job-adversarial-review-2026-06-03-remediation.md` (completed), `ai-dev/plans/104-nop-job-r4-adversarial-review-remediation.md` (completed)

## Purpose

修复 2026-06-03 对抗性审查第 5 轮发现的 11 项新问题，以及 2026-06-03 两轮深度审计（21 维度 + 完整版）中发现的高优先级问题。前三轮对抗性审查（AR-1~AR-24，79% 已修复，剩余均为 P3 或已知设计限制）已在 Plan 103/104 中处理完毕。

## Current Baseline

- Plan 103 已完成（AR-1~AR-18 中 13 项修复），Plan 104 已完成（AR-19~AR-24 中 6 项修复）
- R5 验证：AR-1~AR-24 中 19/24 已修复，未修复项均为 P3（AR-6, AR-7, AR-14, AR-15, AR-16）和已知的 F9/F14
- Round 5 新增 11 项发现（2×P1 + 8×P2 + 1×P3）：
  - **AR-25 (P1)**: `AnnualCalendar.excludeDays` 未初始化 → `isExcludedDay` NPE
  - **AR-26 (P2)**: `HolidayCalendar`/`AnnualCalendar.getNextIncludedTime` 排除所有未来日期时无限循环
  - **AR-27 (P2)**: `CronCalendar.getNextIncludedTime` 毫秒级扫描 — 长排除范围下性能极差
  - **AR-28 (P2)**: `RpcBroadcastTaskBuilder` 不按健康状态过滤服务实例
  - **AR-29 (P1)**: `resolveCompletionDecision` 信任未验证的 task result 将 schedule 标记为 COMPLETED
  - **AR-30 (P2)**: `LimitCountTrigger` 使用 `totalFireCount` 而非 `fireCount` — PARALLEL 策略可超出 `maxExecutionCount`
  - **AR-31 (P2)**: `handleExecutionResult` updateTask 失败后静默丢弃执行结果
  - **AR-32 (P3)**: Task builders 使用 `System.currentTimeMillis()` 而非 DB 时钟
  - **AR-33 (P2)**: `cancelFire` 中 tasks 使用 `updateEntityDirectly` — 可覆盖并发 timeout 状态
  - **AR-34 (P2)**: `JobScheduleStoreImpl` 5 个 schedule 更新路径全部使用 `updateEntityDirectly`
  - **AR-35 (P2)**: Schedule lock 使用 `nextFireTime` 作为隐式锁 — planner 崩溃后可能产生重复 fire
- 深度审计（21 维度）新增 P1 发现：
  - **08-01 (P1)**: `job-retry-adapter.beans.xml` 不匹配 IoC 自动发现模式 — `NopRetryJobRetryBridge` 永远不会被加载
- 深度审计（完整版）新增 P1 发现：
  - **09-01 (P1)**: `LocalJobScheduler.addJob` job 已存在时误用 `ERR_JOB_UNKNOWN_JOB` 错误码
  - **18-01 (P1)**: `concurrency-and-transactions.md` 文档示例使用 `updateEntityDirectly`，与实际代码不一致
- 深度审计 P2 发现中与对抗性审查不重叠的关键项：
  - **07-01 (P2)**: `NopJobFireBizModel` 的 `cancelFire`/`rerunFire` 通过 `store.loadFire()` 绕过 `requireEntity()` 数据权限校验
  - **02-03 (P2)**: DAO store 层包含阻塞策略等业务决策逻辑
  - **15-01 (P2)**: `JobWorkerScannerImpl` 中 `Integer == int` 自动拆箱，`getTaskStatus()` 为 null 时 NPE
  - **04-01 (P2)**: `cancelFire` 遗漏 `totalFireCount`/`failFireCount` 计数器更新
  - **05-01 (P2)**: 4 个陈旧 web 页面目录引用不存在的 xmeta
  - **09-01/02 (P2)**: `ErrorCode` description 硬编码中文（`JobApiErrors:17,19` 2 项 + `JobCoreErrors:40,43` 2 项，其余已在前序计划中修复为英文）
  - **09-05 (P2)**: `NopJobTaskBizModel.delete()` 缺少 `.param()` 上下文
  - **19-01 (P2)**: `IJobScheduler` 同一接口 suspend/pause 术语不一致
- `./mvnw clean test -pl nop-job -am` 当前基线为 BUILD SUCCESS

## Goals

- 修复全部 4 项 P1：AR-25（Calendar NPE）、AR-29（result-driven completion trust boundary）、08-01（IoC bean 发现）、09-01（错误码语义）
- 修复对抗性审查 R5 的 8 项 P2：AR-26~AR-28（Calendar 防御性编码）、AR-30~AR-31（触发器语义/结果丢失）、AR-33~AR-35（乐观锁一致性）
- 修复深度审计中高价值 P2：07-01（数据权限绕过）、15-01（NPE 风险）、04-01（计数器漂移）、09-01/02（中文错误码）、05-01（陈旧 web 目录）
- 注：深度审计 10-01/11-01（xmeta 字段只读声明）经 reviewer 验证已在 repo 中修复，从 scope 移除
- 修复文档 P1：18-01（并发文档示例与实现不符）
- 所有代码修复有对应测试覆盖

## Non-Goals

- 不修复已 adjudicated 的 P3 项（AR-6 死代码、AR-7 maxFailedCount、AR-14 copyMap、AR-15 错误优先级、AR-16 广播 taskPayload、F14 CONTINUE 命名冲突）— 仍在前序计划的 Deferred But Adjudicated 中
- 不修复 AR-32 (P3)（时钟不一致）— 低优先级
- 不修复深度审计中的 P3 项（未使用依赖、域类型偏差、命名不一致、import 风格、测试覆盖不足等）
- 不重新设计调度器架构或 ORM 模型
- 不处理 nop-retry 接口重新设计（F9 的根本解决方案）
- 不处理 DAO→Core 依赖重构（独立计划）
- 不处理 Store 层边界渗透的重构（02-03，属于架构优化，非 bug）

## Scope

### In Scope

- AR-25: `AnnualCalendar` 字段初始化
- AR-26: `HolidayCalendar`/`AnnualCalendar` getNextIncludedTime 最大迭代次数
- AR-27: `CronCalendar` getNextIncludedTime 跳转优化
- AR-28: `RpcBroadcastTaskBuilder` 过滤健康实例
- AR-29: `resolveCompletionDecision` 添加 schedule 配置开关
- AR-30: `TriggerSpecHelper` getFireCount 使用 `fireCount`
- AR-31: `handleExecutionResult` updateTask 失败时添加 WARN 日志
- AR-33: `cancelFire` 中 tasks 改用 `tryUpdateManyWithVersionCheck`
- AR-34: `JobScheduleStoreImpl` schedule 更新逐步改用乐观锁
- AR-35: `insertFireAndAdvanceSchedule` 添加幂等性检查
- 08-01: `job-retry-adapter.beans.xml` 重命名
- 09-01(full): `LocalJobScheduler.addJob` 错误码修正
- 07-01: `NopJobFireBizModel` 使用 `requireEntity()` 做数据权限
- 15-01: `Integer == int` 自动拆箱 NPE 修复
- 04-01: `cancelFire` 补充 totalFireCount/failFireCount 更新
- 05-01: 清理陈旧 web 页面目录
- 09-01/02(deep): ErrorCode description 改英文（`JobApiErrors:17,19` + `JobCoreErrors:40,43`，共 4 项）
- 09-05: delete() 添加 .param() 上下文
- 18-01(full): 更新并发文档示例

Note: 19-01（IJobScheduler API 命名一致性）移至 Deferred But Adjudicated（公共 API 变更需向后兼容策略）。10-01/11-01（xmeta 字段限制）经 reviewer 验证已在 repo 中修复，从 scope 移除。

### Out Of Scope

- AR-6 (P3): parallel path setActiveFireCount(0) 死代码
- AR-7 (P3): maxFailedCount 硬编码为 0
- AR-14 (P3): copyMap 返回原始引用
- AR-15 (P3): findFirstErrorTask 错误优先级
- AR-16 (P3): RpcBroadcastTaskBuilder 不设置 taskPayload
- AR-32 (P3): 时钟不一致
- F9 (P1): retryRecordId 返回 jobFireId（异步设计限制）
- F14 (P3): CONTINUE 命名冲突
- 02-03 (P2): Store 层业务逻辑渗透（架构重构）
- 所有 P3 项（未使用依赖、域类型偏差、i18n 不完整等）

## Execution Plan

### Phase 1 - P1 Calendar NPE + IoC Bean 发现 + 错误码语义（AR-25, 08-01, 09-01-full）

Status: completed
Targets: `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/AnnualCalendar.java`, `nop-job/nop-job-retry-adapter/src/main/resources/_vfs/nop/job/beans/`, `nop-job/nop-job-core/src/main/java/io/nop/job/core/LocalJobScheduler.java`, `nop-job/nop-job-api/src/main/java/io/nop/job/api/JobApiErrors.java`

- Item Types: `Fix`

- [x] AR-25: `AnnualCalendar` 中 `excludeDays` 字段初始化为 `Collections.emptyList()`（字段声明处直接赋值）
- [x] 08-01: 将 `job-retry-adapter.beans.xml` 重命名为 `app-retry-adapter.beans.xml`
- [x] 09-01(full): 在 `JobApiErrors` 中新增 `ERR_JOB_ALREADY_EXISTS` 错误码（描述英文），`LocalJobScheduler.addJob` 改用新错误码
- [x] 为 AR-25 添加测试：验证未设置 excludeDays 时 `isTimeIncluded`/`getNextIncludedTime` 不抛 NPE
- [x] 为 08-01 添加验证：确认重命名后 beans 文件被 IoC 容器加载（通过 beans.xml 存在性检查或启动日志）
- [x] 为 09-01(full) 添加测试：验证 `addJob` 重复注册时抛出 `ERR_JOB_ALREADY_EXISTS`

Exit Criteria:

- [x] `AnnualCalendar.excludeDays` 默认为 `Collections.emptyList()`，未调用 `setExcludeDays` 时不抛 NPE
- [x] `app-retry-adapter.beans.xml` 文件存在且匹配 NopIoC 自动发现模式
- [x] `LocalJobScheduler.addJob` 重复注册时抛出 `ERR_JOB_ALREADY_EXISTS`（非 `ERR_JOB_UNKNOWN_JOB`）
- [x] 新增测试覆盖，测试通过
- [x] `./mvnw compile -pl nop-job -am` 通过
- [x] No owner-doc update required（内部修复，不影响公共 API 契约）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - P1 result-driven completion 安全门（AR-29）

Status: completed
Targets: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCompletionProcessorImpl.java`

- Item Types: `Fix`

- [x] AR-29: 在 `resolveCompletionDecision` 中添加前置检查，从 schedule 的 `jobParams` 中读取 `allowResultCompletion` 标志。仅当值为 `true` 时才信任 `resultPayload.completed`。不修改 ORM 模型（避免 DDL 变更）。实现方式：在 `resolveCompletionDecision` 方法开头增加 `if (!Boolean.TRUE.equals(schedule.getJobParamsComponent().get_jsonMap() != null ? ((Map<String,Object>)schedule.getJobParamsComponent().get_jsonMap()).get("allowResultCompletion") : null)) return new FireCompletionDecision(false, null);`
- [x] 为 AR-29 添加测试：验证 schedule 的 jobParams 不含 `allowResultCompletion`（默认）时 task 返回 `{"completed": true}` 不终止 schedule；验证 `allowResultCompletion=true` 时行为保持不变

Exit Criteria:

- [x] `resolveCompletionDecision` 不再无条件信任 task result 的 `completed: true`，需 schedule 的 jobParams 中 `allowResultCompletion=true` 才启用
- [x] 默认行为变更：result-driven completion 从"始终启用"变为"默认禁用"
- [x] 新增测试覆盖，测试通过
- [x] `./mvnw compile -pl nop-job -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - P2 Calendar 防御性编码 + 广播健康过滤（AR-26, AR-27, AR-28）

Status: completed
Targets: `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/`, `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcBroadcastTaskBuilder.java`

- Item Types: `Fix`

- [x] AR-26: `HolidayCalendar.getNextIncludedTime` 和 `AnnualCalendar.getNextIncludedTime` 中 while 循环添加最大迭代次数（`MAX_DAY_SCAN = 366 * 5`），超出时 break 并返回 `baseCalendar.getNextIncludedTime(timeStamp)` 或 `timeStamp`
- [x] AR-27: `CronCalendar.getNextIncludedTime` 中 else 分支改为跳转到 `baseCalendar.getNextIncludedTime(nextIncludedTime)` 而非逐毫秒递增
- [x] AR-28: `RpcBroadcastTaskBuilder` 中在 `instances` 列表上过滤 `instance.isHealthy() && instance.isEnabled()`，过滤后为空则 fallback
- [x] 为 AR-26 添加测试：验证大量排除日期时 `getNextIncludedTime` 不无限循环
- [x] 为 AR-27 添加测试：验证 CronCalendar 在 baseCalendar 长排除范围下不退化为毫秒扫描
- [x] 为 AR-28 添加测试：验证不健康实例被过滤，空列表时触发 fallback

Exit Criteria:

- [x] `HolidayCalendar`/`AnnualCalendar` 的 while 循环有最大迭代次数保护
- [x] `CronCalendar` 不再逐毫秒递增，改用跳转到 baseCalendar 结果
- [x] `RpcBroadcastTaskBuilder` 过滤不健康实例
- [x] 新增测试覆盖，测试通过
- [x] `./mvnw compile -pl nop-job -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - P2 乐观锁一致性（AR-33, AR-34）

Status: planned
Targets: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java`, `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java`

- Item Types: `Fix`

- [ ] AR-33: `cancelFire` 中 tasks 更新从 `taskDao().updateEntityDirectly(task)` 改为 `taskDao().tryUpdateManyWithVersionCheck`，版本冲突时重新加载 task 状态，若已为终态（TIMEOUT/CANCELED/SUSPICIOUS）则跳过
- [ ] AR-34: `JobScheduleStoreImpl` 中全部 5 个 schedule 更新路径从 `scheduleDao().updateEntityDirectly(schedule)` 改为 `tryUpdateManyWithVersionCheck` + 重试（最多 5 次）。5 个路径为：(1) `advanceScheduleAfterSkip:98`、(2) `insertFireAndAdvanceSchedule:114`、(3) `overlayFireAndAdvanceSchedule:153`、(4) `recoveryFireAndAdvanceSchedule:190,215`（两个分支）、(5) `insertManualFire:256`。重试时从 fresh schedule 重新计算差值字段（fireCount、activeFireCount 等），lastFireStatus/lastEndTime/nextFireTime 等覆盖字段直接设置
- [ ] 为 AR-33 添加测试：模拟 cancel 与 timeout 并发，验证 task 终态不被覆盖
- [ ] 为 AR-34 添加测试：模拟 planner 与 BizModel 并发更新 schedule，验证计数器不丢失

Exit Criteria:

- [ ] `cancelFire` 中 tasks 使用乐观锁，并发 timeout 状态不被覆盖
- [ ] `JobScheduleStoreImpl` 中所有 schedule 更新路径使用 `tryUpdateManyWithVersionCheck` + 重试
- [ ] 新增测试覆盖并发场景，测试通过
- [ ] `./mvnw compile -pl nop-job -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - P2 触发器语义 + 结果丢失 + 幂等性（AR-30, AR-31, AR-35）

Status: planned
Targets: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/helper/TriggerSpecHelper.java`, `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java`, `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java`

- Item Types: `Fix`

- [ ] AR-30: `TriggerSpecHelper.toEvalContext` 中 `getFireCount()` 改为 `schedule.getFireCount()`（已调度计数，包含 active）而非 `schedule.getTotalFireCount()`（仅已完成计数）
- [ ] AR-31: `JobWorkerScannerImpl.handleExecutionResult` 中 updateTask 版本冲突且 task 仍为 RUNNING/CLAIMED 时，记录 WARN 日志（包含 jobTaskId、taskStatus、执行结果摘要），而非静默丢弃
- [ ] AR-35: `insertFireAndAdvanceSchedule` 在创建新 fire 前查询是否已存在相同 `scheduledFireTime` 的 WAITING fire（按 `jobScheduleId + scheduledFireTime + fireStatus=WAITING` 查询），若存在则跳过创建
- [ ] 为 AR-30 添加测试：验证 PARALLEL 策略下 `LimitCountTrigger` 使用 `fireCount` 正确限制执行次数
- [ ] 为 AR-31 添加测试：验证 updateTask 失败时产生 WARN 日志
- [ ] 为 AR-35 添加测试：验证 planner 崩溃恢复后不产生重复 fire

Exit Criteria:

- [ ] `getFireCount()` 返回 `schedule.getFireCount()`（已调度计数），PARALLEL + maxExecutionCount 组合不再超限
- [ ] updateTask 失败且 task 非 TIMEOUT/CANCELED 时记录 WARN 日志
- [ ] `insertFireAndAdvanceSchedule` 有幂等性检查，防止 planner 崩溃恢复后产生重复 fire
- [ ] 新增测试覆盖，测试通过
- [ ] `./mvnw compile -pl nop-job -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - 深度审计 P2 关键修复（07-01, 15-01, 04-01, 09-01/02-deep, 05-01, 09-05）

Status: planned
Targets: `nop-job/nop-job-service/`, `nop-job/nop-job-coordinator/`, `nop-job/nop-job-worker/`, `nop-job/nop-job-dao/`, `nop-job/nop-job-core/`, `nop-job/nop-job-web/`, `nop-job/nop-job-api/`

- Item Types: `Fix`

- [ ] 07-01: `NopJobFireBizModel` 的 `cancelFire`/`rerunFire` 先调用 `requireEntity()` 做数据权限校验，再传给 store 执行
- [ ] 15-01: `JobWorkerScannerImpl` 中 `Integer == int` 比较改为安全方式（`Objects.equals(taskStatus, TASK_STATUS_RUNNING)` 或先做 null 检查）
- [ ] 04-01: `cancelFire` 方法中补充 `totalFireCount` 和 `failFireCount` 的递增（与 overlay 路径一致）
- [ ] 09-01/02(deep): 仅修复 4 个仍为中文的 ErrorCode description：`JobApiErrors` 第 17、19 行 + `JobCoreErrors` 第 40、43 行（`ERR_JOB_EXECUTOR_REF_EMPTY`、`ERR_JOB_EXECUTOR_KIND_EMPTY`）。其余已在前序计划中修复为英文
- [ ] 05-01: 删除 4 个陈旧 web 页面目录（`nop-job/nop-job-web/src/main/resources/_vfs/nop/job/pages/` 下引用不存在 xmeta 的 NopJob* 目录）。执行时逐一验证哪些页面目录引用的 xmeta 不存在，仅删除确认陈旧的
- [ ] 09-05: `NopJobTaskBizModel.delete()` 添加 `.param("jobTaskId", id)`
- [ ] 为 07-01 添加测试：验证 `cancelFire`/`rerunFire` 通过 `requireEntity` 校验数据权限
- [ ] 为 15-01 添加测试：验证 `getTaskStatus()` 为 null 时不抛 NPE
- [ ] 为 04-01 添加测试：验证 cancel 后 `totalFireCount`/`failFireCount` 正确递增

Exit Criteria:

- [ ] `cancelFire`/`rerunFire` 通过 `requireEntity()` 校验数据权限后再操作
- [ ] `Integer == int` 比较不再因 null 自动拆箱导致 NPE
- [ ] `cancelFire` 路径正确更新 `totalFireCount`/`failFireCount`
- [ ] `JobApiErrors:17,19` 和 `JobCoreErrors:40,43` 的 description 为英文
- [ ] 陈旧 web 页面目录已删除
- [ ] delete() 错误消息包含 jobTaskId
- [ ] 新增测试覆盖，测试通过
- [ ] `./mvnw compile -pl nop-job -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 7 - P1 文档修复（18-01-full）

Status: planned
Targets: `docs-for-ai/` 下 nop-job 相关文档（执行时 glob 确认具体路径）

- Item Types: `Fix`

- [ ] 18-01(full): 找到 `concurrency-and-transactions.md`（或含并发/事务代码示例的文档），将代码示例中的 `updateEntityDirectly` 替换为实际的 `tryUpdateManyWithVersionCheck` + 乐观锁重检实现
- [ ] 找到 `architecture-principles.md`（如存在）：修正 `@BizAction` 错误引用为 `IJobInvoker` 注册机制；修正 `NopJobScheduleBizModel` 聚合根分类
- [ ] 找到 `where-things-live.md`（如存在）：补充遗漏的 8 个 nop-job 关键子模块

Exit Criteria:

- [ ] 文档代码示例与 live repo 代码一致
- [ ] 无错误的 API 引用（`@BizAction` → `IJobInvoker`）
- [ ] 模块列表完整覆盖所有 nop-job 子模块
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 通过（如涉及 `docs-for-ai/`）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 全部 4 项 P1 已修复（AR-25, AR-29, 08-01, 09-01-full）
- [ ] 全部 8 项 P2 (R5) 已修复（AR-26~AR-28, AR-30~AR-31, AR-33~AR-35）
- [ ] 深度审计关键 P2 已修复（07-01, 15-01, 04-01, 09-01/02-deep, 05-01, 09-05）
- [ ] 文档 P1 已修复（18-01-full）
- [ ] 每项代码修复有对应测试覆盖
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] Anti-Hollow Check：closure audit 已验证组件间调用链在运行时确实连通，无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile -pl nop-job -am` 通过
- [ ] `./mvnw test -pl nop-job -am` 通过
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### AR-6: Planner parallel path setActiveFireCount(0) 死代码

- Classification: `watch-only residual`
- Why Not Blocking Closure: 仅误导后续开发者，不影响运行时行为。Plan 103/104 已 adjudicated。
- Successor Required: no

### AR-7: maxFailedCount 硬编码为 0

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需要 ORM 模型变更，属于 feature enhancement。Plan 103/104 已 adjudicated。
- Successor Required: yes

### AR-14: copyMap 返回原始引用

- Classification: `optimization candidate`
- Why Not Blocking Closure: 实际影响极低。Plan 103/104 已 adjudicated。
- Successor Required: no

### AR-15: findFirstErrorTask 错误优先级

- Classification: `optimization candidate`
- Why Not Blocking Closure: 仅影响广播场景 errorCode 准确性。Plan 103/104 已 adjudicated。
- Successor Required: no

### AR-16: RpcBroadcastTaskBuilder 不设置 taskPayload

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 运行时从 fire 的 jobParamsSnapshot 读取。Plan 103/104 已 adjudicated。
- Successor Required: no

### AR-32: Task builders 使用 System.currentTimeMillis()

- Classification: `optimization candidate`
- Why Not Blocking Closure: 分布式部署下时钟偏差通常在毫秒级，对 audit trail 影响极低。
- Successor Required: no

### F9: retryRecordId 返回 jobFireId

- Classification: `watch-only residual`
- Why Not Blocking Closure: 异步 retry API 设计限制。Plan 103/104 已 adjudicated。
- Successor Required: yes

### F14: CONTINUE 命名冲突

- Classification: `optimization candidate`
- Why Not Blocking Closure: 编译和运行时无歧义。Plan 103/104 已 adjudicated。
- Successor Required: no

### 02-03: Store 层业务逻辑渗透

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 属于架构重构，非 bug，影响范围大，独立规划。
- Successor Required: yes

### 19-01: IJobScheduler suspend/pause 命名不一致

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 属于公共 API 命名变更（`nop-job-api`），需向后兼容策略（deprecated + 新方法），独立规划更安全。当前 `suspendJob`/`pauseJobs`/`pauseAllJobs` 的调用关系在功能上无歧义。
- Successor Required: yes

## Non-Blocking Follow-ups

- 考虑为 `maxFailedCount` 添加 ORM 列和配置入口（AR-7）
- 考虑重新设计 nop-retry 回调接口以支持同步获取真实 retryRecordId（F9）
- 清理 parallel path 死代码（AR-6）
- 考虑 `copyMap` 重命名或改为真正深拷贝（AR-14）
- 考虑 `findFirstErrorTask` 优先级对齐（AR-15）
- 考虑广播 task 设置 taskPayload（AR-16）
- 考虑将 task builders 的时钟改为 DB clock provider（AR-32）
- 考虑 Store 层业务逻辑下沉到 coordinator 层（02-03）
- 考虑 `IJobScheduler` API 命名统一（19-01，需向后兼容策略）
- 深度审计 P3 项（未使用依赖、域类型、i18n、import 风格等）按优先级逐步处理

## Closure

Status Note: 

Closure Audit Evidence:

- Reviewer / Agent: 
- Evidence:

Follow-up:

- no remaining plan-owned work
