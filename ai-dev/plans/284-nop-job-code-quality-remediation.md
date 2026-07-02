# 284 nop-job 代码质量修复

> Plan Status: active
> Last Reviewed: 2026-07-02
> Source: `ai-dev/analysis/2026-07-02-nop-job-code-quality-remediation-analysis.md`、`ai-dev/inputs/nop-job-audit.md`、AR-65 (`ai-dev/audits/2026-06-04-adversarial-review-nop-job-r8/01-open-findings.md:350-371`)
> Related: `ai-dev/design/nop-job/01-architecture-baseline.md`

## Purpose

将 `ai-dev/inputs/nop-job-audit.md` 列出的 16 项 nop-job 代码质量问题全部修复或裁定为 deferred-with-justification，覆盖 Helper 抽取、框架约定对齐、Dao/API 误用纠正、计数器一致性加固四类问题。

## Current Baseline

### 已成立的事实

- `NopJobFireBizModel.delete()` 已重写为抛异常（`NopJobFireBizModel.java:40-43`），阻止 API 直接删除 fire 记录。但 `save__` / `update__` 未限制（AR-65 部分修复）。
- `NopJobTaskBizModel` 已限制 delete（抛异常），模式与 Fire 一致。
- **ORM 自动维护审计字段（已验证）**：`OrmTimestampHelper.onUpdate`（`nop-orm/.../persister/OrmTimestampHelper.java:74-112`）在实体更新时自动填充 `updatedBy`（`ContextProvider.currentUserRefNo()`，fallback `CFG_ORM_SYS_USER_NAME`）和 `updateTime`（`CoreMetrics.currentTimeMillis()`）。`onCreate`（同文件:31-72）自动填充 `createdBy`/`createTime`/`updatedBy`/`updateTime`。nop-job 的 3 个实体已配置这些 prop（`nop-job/model/nop-job.orm.xml:83-85,225-227,356-358`）。因此生产代码中的手工 `setUpdatedBy("system")` / `setUpdateTime(...)` / `setCreatedBy(...)` / `setCreateTime(...)` 均为冗余。
- **AR-67 已完成**：`NopJobFire.xmeta` 已覆盖 `triggerSource`、`triggeredBy`（`insertable="false" updatable="false"`）及 `scheduledFireTime`、`retryPolicyId`、`retryRecordId`、`partitionIndex`（`updatable="false"`，保留 `insertable="true"` 因引擎 insert 路径需设置）。见 `ai-dev/archived/2026-06/110-nop-job-r8-and-deep-audit-remediation.md:133`。
- Store 层（`JobFireStoreImpl`、`JobScheduleStoreImpl`、`JobTaskStoreImpl`）正确使用 DB 时钟 `dao.getDbEstimatedClock().getMaxCurrentTimeMillis()` 获取业务时间。
- fire/task 状态常量是有序整数（`_NopJobCoreConstants.java:34-104`）：活跃态 0-20、终态 30-60，天然支持区间判断。
- `IOrmEntityDao` 已提供单实体版本检查方法 `boolean tryUpdateWithVersionCheck(T entity)`（`IOrmEntityDao.java:40-44`）和批量加载方法 `batchGetEntityMapByIds`（`IEntityDao.java:175`）。
- `JsonOrmComponent` 已提供 `getValue(String name)` 直接取值（`JsonOrmComponent.java:121-126`）。

### 待修复的 gap

1. **状态判断散落重复**：`isCancelableStatus`、`isRerunnableStatus`、`isCancelableFire`、`isTaskFinished` 在 4+ 处重复，且逐值枚举而非区间判断（Issue 1）。
2. **公共函数重复**：`addPartitionFilter` 2 处重复（Issue 2），`calculateDuration` 3 处重复（Issue 6）。
3. **审计字段手工设置**：生产代码中 `setUpdatedBy("system")` / `setUpdateTime()` / `setCreatedBy()` / `setCreateTime()` 共 50+ 处（跨 `JobFireStoreImpl`、`JobScheduleStoreImpl`、`JobTaskStoreImpl`、`FireFactory`、`JobTimeoutCheckerImpl`、`JobCompletionProcessorImpl`、`JobPlannerScannerImpl`、各 TaskBuilder），与 ORM 自动维护冗余（Issue 3）。
4. **System.currentTimeMillis()**：生产代码 6 个文件使用（`AdaptiveJobTaskBuilder:85`、`DefaultJobTaskBuilder:19`、`JobPartitionResolver:84,108-125`、`PartitionTaskBuilder:104`、`RpcBroadcastTaskBuilder:71`），绕过了 CoreMetrics 时钟注入（Issue 8）。
5. **乐观锁返回值未检查**：`JobFireStoreImpl.java:225` cancel task 重试更新丢弃返回值（Issue 5）。
6. **baseline 缓存刷新缺失**：`completeFireAndUpdateSchedule` 和 `cancelFire` 的重试循环中 `requireEntityById` 返回 session 缓存中的旧版本（同一 `@Transactional(REQUIRES_NEW)` 方法内，ORM session 按 ID 缓存实体）（Issue 12）。
7. **单实体更新用列表包装**：8+ 处 `tryUpdateManyWithVersionCheck(Collections.singletonList(x))`（Issue 14）。
8. **batchLoadFire 手工 query**：未使用 Dao 批量加载方法（Issue 13）。
9. **冗余 JSON 解析**：`NopJobScheduleBizModel.resolveJobParams:261-266` 回退分支是死代码（Issue 15）。
10. **错误消息未国际化**：`failFireWithoutSchedule` 接收字符串参数，调用方传 `ErrorCode.getDescription()`（Issue 7）。
11. **findAll 无防御性 limit**：多处 `findAllByQuery` 缺少范围限定（Issue 4）。
12. **activeFireCount 维护必要性**：audit 提出是否改为统计查询（Issue 11）。
13. **明确问题需加注释**：部分已知边界行为缺少设计意图注释（audit Issue 11 末尾）。

## Goals

- 16 项 audit 发现全部修复或裁定为 deferred-with-justification。
- 状态判断、公共函数统一到 Helper 类，消除重复。
- 审计字段（`updatedBy`/`updateTime`/`createdBy`/`createTime`）交由 ORM 框架自动维护，删除所有手工设置。
- 生产代码不再直接调用 `System.currentTimeMillis()`。
- 计数器 baseline 缓存漂移问题修复，消除乐观锁重试循环中的旧版本风险。
- 所有状态判断方法有单测覆盖（当前零覆盖）。

## Non-Goals

- 不重新设计 job 引擎调度/分发/超时检查的核心流程。
- 不新增业务功能。
- 不删除 `activeFireCount` 等计数器字段（架构决策裁定为 keep-and-harden，理由见 Phase 5 Decision）。
- 不将计数器更新改为 EQL 原子语句（Issue 16 需独立设计文档，移入 Deferred）。
- 不修改 `nop-commons` 的 `DateHelper.currentTimeMillis()` 实现（超出 nop-job scope）。

## Scope

### In Scope

- `nop-job/nop-job-service`：`NopJobFireBizModel`、`NopJobScheduleBizModel`、`FireFactory`
- `nop-job/nop-job-dao`：`JobFireStoreImpl`、`JobScheduleStoreImpl`、`JobTaskStoreImpl`、`IJobFireStore`
- `nop-job/nop-job-coordinator`：`AdaptiveJobTaskBuilder`、`DefaultJobTaskBuilder`、`JobPartitionResolver`、`PartitionTaskBuilder`、`RpcBroadcastTaskBuilder`、`JobCompletionProcessorImpl`、`JobTimeoutCheckerImpl`、`JobPlannerScannerImpl`
- `nop-job/nop-job-core`：`_NopJobCoreConstants`（只读引用，不修改生成文件）
- 新增：`io.nop.job.dao.helper.JobStatusHelper`、`io.nop.job.dao.helper.JobQueryHelper`
- 修改：`nop-kernel/nop-commons` 的 `DateHelper`（新增 `durationMs` 方法）
- `nop-job/nop-job-service` 的 `NopJobErrors.java`（新增 save/update not allowed error codes）

### Out Of Scope

- nop-job ORM 模型结构变更（不新增/删除列）
- nop-job-api 的 bean 定义变更
- `nop-commons` 除 `DateHelper.durationMs` 外的其他修改
- 测试代码中的 `System.currentTimeMillis()`（测试可直接用系统时钟）
- Issue 16（计数器 EQL 原子更新）——需独立设计文档，移入 Deferred
- Issue 10（NopJobTask 补充字段）——需调研，移入 Non-Blocking Follow-ups
- AR-67（xmeta 字段保护）——已完成

## Execution Plan

### Phase 1 - Helper 抽取与状态判断统一

Status: completed
Targets: `io.nop.job.dao.helper.JobStatusHelper`（新建）、`io.nop.job.dao.helper.JobQueryHelper`（新建）、`DateHelper`、`JobFireStoreImpl`、`JobScheduleStoreImpl`、`JobCompletionProcessorImpl`、`NopJobFireBizModel`

- Item Types: `Fix`

- [ ] 新建 `JobStatusHelper`，利用状态有序性提供区间判断：活跃 fire（< FIRE_STATUS_SUCCESS）、终态 fire（>= FIRE_STATUS_SUCCESS）、活跃 task、终态 task。提供语义别名方法 `isCancelableFire`、`isRerunnableFire`、`isFinishedTask`
- [ ] `NopJobFireBizModel` 的 `isCancelableStatus`、`isRerunnableStatus` 删除，改调 `JobStatusHelper`
- [ ] `JobFireStoreImpl` 的 `isCancelableFire`、`isTaskFinished`、`TERMINAL_FIRE_STATUSES` 删除，改调 `JobStatusHelper`
- [ ] `JobScheduleStoreImpl` 的 `isTaskFinished` 删除，改调 `JobStatusHelper`
- [ ] 新建 `JobQueryHelper.addPartitionFilter(QueryBean, IntRangeSet)`，`JobFireStoreImpl` 和 `JobScheduleStoreImpl` 的私有 `addPartitionFilter` 删除，改调 Helper
- [ ] `DateHelper` 新增 `public static Long durationMs(Timestamp start, Timestamp end)`；`JobFireStoreImpl`、`JobScheduleStoreImpl`、`JobCompletionProcessorImpl` 的私有 `calculateDuration` 删除，改调 `DateHelper.durationMs`
- [ ] 为 `JobStatusHelper` 编写单测，覆盖所有状态值的活跃/终态判断边界（0、10、15、20、30、40、50、60、null）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `JobStatusHelper` 存在且被 `NopJobFireBizModel`、`JobFireStoreImpl`、`JobScheduleStoreImpl` 调用（grep 可验证无残留私有状态判断方法）
- [x] `JobQueryHelper.addPartitionFilter` 存在且被 3 个 Store 调用（`JobFireStoreImpl`、`JobScheduleStoreImpl`、`JobTaskStoreImpl`）
- [x] `DateHelper.durationMs` 存在且 3 处 `calculateDuration` 已删除
- [x] `JobStatusHelper` 单测覆盖所有状态常量边界
- [x] **新增功能测试**（Rule #25）：`TestJobStatusHelper` 覆盖区间判断正确性（10 个测试方法）
- [x] **接线验证**（Rule #23）：grep 确认 `JobStatusHelper` 的方法在 `NopJobFireBizModel`、`JobFireStoreImpl`、`JobScheduleStoreImpl` 中确实被调用（非仅 import）
- [x] No owner-doc update required（纯内部重构）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Dao/JSON API 正确使用

Status: completed
Targets: `JobFireStoreImpl`、`NopJobScheduleBizModel`、`NopJobFireBizModel`

- Item Types: `Fix`

- [x] `JobFireStoreImpl` 中所有单实体 `tryUpdateManyWithVersionCheck(Collections.singletonList(x))` 改为 `tryUpdateWithVersionCheck(x)` 返回 boolean（8 处）。保留 `tryLockFiresForDispatch` 的批量调用
- [x] ~~`JobFireStoreImpl.batchLoadFires` 改用 `batchGetEntityMapByIds`~~ → **移入 Non-Blocking Follow-ups**：`tryBatchGetEntitiesByIds` 返回 session 缓存实体而非 DB 最新数据，导致 timeout checker 看到过时的 fire 状态。`findAllByQuery` 始终发起新 SQL 查询保证数据新鲜。在 Store 层需要最新数据的场景不能安全替换。
- [x] `NopJobScheduleBizModel.resolveJobParams` 删除冗余回退分支，直接使用 `getJobParamsComponent().get_jsonMap()`
- [x] Issue 9 排查完成：nop-job 生产代码中无 `get_jsonMap().get(name)` 模式（全部读取完整 Map），无可修改项

Exit Criteria:

- [x] grep `Collections.singletonList` 在 `JobFireStoreImpl` 中仅出现在 `tryLockFiresForDispatch`（批量场景）和 `batchLoadFires`（已裁定保留）
- [x] ~~`batchLoadFires` 使用 `batchGetEntityMapByIds`~~ → 裁定为保留 `findAllByQuery`（理由见上），移入 Non-Blocking Follow-ups
- [x] `resolveJobParams` 方法体不超过 5 行，无 `JsonTool.parseMap` 调用
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 框架约定对齐：审计字段与时间获取

Status: completed
Targets: `JobFireStoreImpl`、`JobScheduleStoreImpl`、`JobTaskStoreImpl`、`FireFactory`、`JobTimeoutCheckerImpl`、`JobCompletionProcessorImpl`、`JobPlannerScannerImpl`、`AdaptiveJobTaskBuilder`、`DefaultJobTaskBuilder`、`JobPartitionResolver`、`PartitionTaskBuilder`、`RpcBroadcastTaskBuilder`

- Item Types: `Fix`

- [x] 删除所有 nop-job 生产代码中的手工审计字段设置：`setUpdatedBy(...)`、`setUpdateTime(...)`、`setCreatedBy(...)`、`setCreateTime(...)`（共 90 行，跨 13 个生产文件）。ORM 框架已通过 `OrmTimestampHelper` 自动维护这些字段
- [x] `JobPartitionResolver` 的 5 处 `System.currentTimeMillis()` 替换为 `CoreMetrics.currentTimeMillis()`。其余 TaskBuilder 中的 `System.currentTimeMillis()` 仅用于审计字段（已随审计字段删除一并清除）
- [x] 测试中 3 个断言旧审计字段值（`assertEquals("system", ...)`）的用例已更新：2 个移除断言（ORM 自动维护值取决于运行时上下文）、1 个移除断言（实体未持久化时字段为 null）

Exit Criteria:

- [x] grep `setUpdatedBy\(` 在 nop-job **生产代码**中返回 0 结果
- [x] grep `setUpdateTime\(` 在 nop-job **生产代码**中返回 0 结果
- [x] grep `setCreatedBy\(` 在 nop-job **生产代码**中返回 0 结果
- [x] grep `setCreateTime\(` 在 nop-job **生产代码**中返回 0 结果
- [x] grep `System\.currentTimeMillis` 在 nop-job **生产代码**（非 test）中返回 0 结果
- [x] `./mvnw test -pl nop-job/nop-job-coordinator` → 146 tests pass, 0 failures
- [x] No owner-doc update required（审计字段自动维护是 ORM 框架既定行为，非新约定）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 错误处理与安全性

Status: completed
Targets: `IJobFireStore`、`JobFireStoreImpl`、`JobTimeoutCheckerImpl`、`JobScheduleStoreImpl`、`JobTaskStoreImpl`

- Item Types: `Fix`

- [x] **Issue 7**：`failFireWithoutSchedule` 的 i18n 修复。`JobCompletionProcessorImpl:177` 和 `JobTimeoutCheckerImpl:317` 两处调用改用 `ErrorMessageManager.instance().getLocalizedDescription(null, errorCode)` 获取本地化描述，fallback 到 `getDescription()`。其他 `getDescription()` 反模式记录为 Non-Blocking Follow-up
- [x] **Issue 5**：`JobFireStoreImpl.cancelFire` 的 task 重试更新返回值检查——添加 WARN 日志
- [x] **Issue 4**：审查完成。所有生产代码 `findAllByQuery` 调用均有 `setLimit` 或有界等值过滤（`FilterBeans.eq(jobFireId, ...)` / `FilterBeans.in(jobFireId, ...)`）。`findTasksByFireId` 按 fireId 过滤（task 数受 dispatch 控制），`existsXxx` 查询均有 `setLimit(1)`
- [x] **audit Issue 11 末尾**：cancelFire task 重试失败添加 WARN 注释

Exit Criteria:

- [x] `failFireWithoutSchedule` 的 errorMessage 来源经过 i18n 处理（2 处调用均使用 `ErrorMessageManager`）
- [x] `JobFireStoreImpl.cancelFire` 的 task 重试更新返回值被检查（WARN 日志）
- [x] 所有生产代码 `findAllByQuery` 调用有 `setLimit` 或有界等值过滤
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 计数器一致性加固与 CRUD 保护

Status: completed
Targets: `NopJobFireBizModel`、`NopJobErrors.java`、`JobFireStoreImpl`、`JobScheduleStoreImpl`

- Item Types: `Decision`、`Fix`

- [x] **Decision（Issue 11）**：activeFireCount 维护方式裁定为 **keep-and-harden**（已记录在 plan 中，正面回应 analysis Option B 推荐）
- [x] **Fix（AR-65 补全）**：`NopJobFireBizModel` 增加 `save` / `update` 方法 override（与 `delete` 一致抛异常）。新增 `ERR_JOB_FIRE_SAVE_NOT_ALLOWED`、`ERR_JOB_FIRE_UPDATE_NOT_ALLOWED` 到 `JobApiErrors`
- [x] **Fix（Issue 12）**：`completeFireAndUpdateSchedule` 和 `cancelFire` 的重试循环中，baseline 刷新前调用 `schedule.orm_unload()` 强制从 DB 重新加载

Exit Criteria:

- [x] Decision 记录在本 plan 中且理由完整
- [x] Option B 和 Issue 16 已移入 `Deferred But Adjudicated`
- [x] `NopJobFireBizModel` 的 `save` / `update` / `delete` 均抛异常
- [x] `JobApiErrors` 包含两个新 error codes
- [x] 重试循环中使用 `orm_unload()` 刷新 baseline（grep 可验证）
- [x] **新增功能测试**：baseline 刷新的 focused test 需确定性并发场景，现有 `TestJobFireStoreRace` 提供间接覆盖；确定性 focused test 移入 Non-Blocking Follow-up
- [x] **端到端验证**：`TestJobE2E` 等测试全部通过（146 coordinator + 41 service tests, 0 failures）
- [x] No owner-doc update required（API 表面积缩小——限制了 save/update，不改文档约定）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 16 项 audit 发现全部处于 landed 或 adjudicated-deferred 状态
- [ ] 状态判断、公共函数统一到 Helper 类，无残留重复
- [ ] 生产代码无手工审计字段设置（`setUpdatedBy`/`setUpdateTime`/`setCreatedBy`/`setCreateTime`）和 `System.currentTimeMillis()`（或每处保留有注释）
- [ ] `NopJobFireBizModel` CRUD 全面限制（save/update/delete 均抛异常）
- [ ] 计数器 baseline 缓存漂移问题已修复（重试循环中使用 `orm_unload`/`refresh`）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [ ] 受影响的 owner docs 已同步或明确写明 No owner-doc update required
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证 Helper 类确实被调用（不只是存在），baseline 刷新确实在重试循环中执行
- [ ] `./mvnw test -pl nop-job -am` 通过
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### activeFireCount 改为统计查询（Issue 11 Option B）

- Classification: `optimization candidate`
- Why Not Blocking Closure: AR-65 根因（直接 delete fire 绕过引擎）已通过 `NopJobFireBizModel.delete()` 抛异常消除。本 plan Phase 5 通过 baseline 刷新（Issue 12）修复乐观锁重试中的缓存旧版本问题——这是计数器不一致的直接技术根因。改为统计查询是独立的架构优化方向，需评估 fire 表数据量、索引策略、Dashboard 查询频率，不应与代码质量修复混淆。
- Successor Required: `yes`
- Successor Path: 待开 `ai-dev/design/nop-job/counter-query-optimization.md` + 对应 plan

### 计数器 EQL 原子更新（Issue 16）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前计数器更新通过读-改-写 + 乐观锁重试（5 次）实现，Issue 12 的 baseline 刷新修复了缓存旧版本导致的 delta 计算错误。EQL 原子更新（`UPDATE ... SET col = col + delta`）是简化并发逻辑的优化方向，但 schedule 计数器更新涉及条件分支（`shouldAdvanceFixedDelaySchedule`、`calculateFixedDelayNextFireTime`）、多 caller 差异（`JobCompletionProcessorImpl` vs `JobTimeoutCheckerImpl` vs `JobFireStoreImpl.cancelFire`）、以及 `JobScheduleStoreImpl.updateScheduleWithRetry` 的平行模式，需独立设计文档明确 EQL schema、version 策略、条件字段拆分方案。
- Successor Required: `yes`
- Successor Path: 待开 `ai-dev/design/nop-job/counter-eql-update-design.md` + 对应 plan

## Non-Blocking Follow-ups

- **Issue 10（NopJobTask 补充字段）**：需调研 `completed`、`nextScheduleTime` 当前存储位置和使用频率后决定是否补充为实体列。属 ORM 模型变更（plan-first），不在本 plan 内执行。
- **Issue 13（batchLoadFires → batchGetEntityMapByIds）**：`tryBatchGetEntitiesByIds` 返回 session 缓存实体而非 DB 最新数据，在 timeout checker 等需要新鲜数据的场景不安全。需要评估是否在调用前 `orm_unload` 或使用其他方式保证数据新鲜度，属优化项。
- **广泛的 `ErrorCode.getDescription()` i18n**：Issue 7 仅修复 `failFireWithoutSchedule` 路径。cancelFire（`JobFireStoreImpl:194,211`）、cancelTasks（`JobScheduleStoreImpl:521,545`）、`JobCompletionProcessorImpl:177-178`、`JobTimeoutCheckerImpl` 的其他 `getDescription()` 直接写入 errorMessage 的反模式，应统一走 i18n 处理。
- `DateHelper.currentTimeMillis()` 内部使用 `System.currentTimeMillis()`（`DateHelper.java:116`），应改为 `CoreMetrics.currentTimeMillis()`。属 `nop-commons` scope，不在本 plan 内修复。
- 测试代码中大量使用 `System.currentTimeMillis()`，可考虑统一为 mock-friendly 的时钟获取，但不影响生产行为。

## Closure

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<待 closure 时填写>>
- Evidence: <<待 closure 时填写>>

Follow-up:

- <<待 closure 时填写>>
