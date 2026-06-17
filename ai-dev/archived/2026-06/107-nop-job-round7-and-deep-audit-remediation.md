# 107 nop-job Round 7 Adversarial Review + Deep Audit Remediation

> Plan Status: completed
> Last Reviewed: 2026-06-04
> Reviewed By: Independent sub-agent adversarial review (session ses_17187675fffeCaEmWYg4vh2KHz) — Verdict: Ready (after B1 resolution)
> Source: `ai-dev/audits/2026-06-04-adversarial-review-nop-job/01-open-findings.md` (Round 7: 6 new findings: AR-48~AR-53), `ai-dev/audits/2026-06-04-deep-audit-nop-job/summary.md` (7-dimension deep audit: 26 findings)
> Related: `ai-dev/plans/106-nop-job-r6-adversarial-review-remediation.md` (completed, covered AR-36~AR-47)

## Purpose

修复 2026-06-04 对抗性审查第 7 轮发现的 6 项新问题 + 2026-06-04 深度审计 7 维度发现的 26 项问题 + 先前 6 轮遗留的仍然存活的缺陷。将 nop-job 模块从"良好但存在系统性遗漏"提升到"审计收敛、乐观锁全覆盖、测试保护力完整"。

## Current Baseline

- Plan 106 已完成（AR-36~AR-47 中 10/12 已修复，剩余 AR-37 RPC null timeout 仍存在、AR-27 CronCalendar 无最大迭代部分修复）
- 对抗性审查累计 47 项（AR-1~AR-47），其中 40+ 已修复
- 深度审计 7 维度执行完成（01 依赖图、04 ORM、05 生成管线、07 BizModel、09 错误处理、11 XMeta、16 测试覆盖）
- `./mvnw clean test -pl nop-job -am` 当前基线为 BUILD SUCCESS
- **仍然存活的先前缺陷**（来自 Plan 106 的 Deferred But Adjudicated）：
  - AR-6 (P3): setActiveFireCount(0) dead write
  - AR-7 (P3): maxFailedCount hardcoded to 0
  - AR-14 (P3): copyMap 返回原始引用
  - AR-15 (P3): findFirstErrorTask 优先级不一致
  - AR-16 (P3): RpcBroadcastTaskBuilder 无 taskPayload
  - AR-24/F9 (P1→P2): retryRecordId always null
  - AR-32 (P3): System.currentTimeMillis 而非 DB clock
  - AR-37 (P1): RPC invokeAsync null timeout（injectTimeoutHeader 已添加但默认 60s）
  - AR-45 (P3): CronExpression.equals 忽略 timeZone
  - AR-47 (P3): emptyIfNull 死代码

## Goals

- 修复全部 5 项新 P2（AR-48~AR-50, AR-52, AR-53）和 1 项新 P3（AR-51）
- 修复深度审计中全部 12 项 P2
- 修复深度审计中高价值 P3 项（Store 常量重复、test mock 空操作等）
- 验证并收口 AR-37 (P1, RPC timeout — 已确认 `injectTimeoutHeader` 已处理超时传播，降级为验证项)
- 所有代码修复有对应测试覆盖
- 清理 4 个已废弃实体的孤立视图文件
- 清理 3 个无效编译范围依赖

## Non-Goals

- 不修复 Prior AR-6/7/14/15/16/32/45/47（全部 P3，已在先前计划中 adjudicated）
- 不重新设计 nop-retry 集成接口（AR-24/F9 的根本解决方案需独立设计）
- 不修复 AR-27 残留（CronCalendar 无最大迭代，部分修复已落地）
- 不重新设计调度器架构、ORM 模型结构或跨模块 API
- 不执行深度审计未覆盖的维度（02/03/06/08/10/12~15/17~21）

## Scope

### In Scope

- Round 7 新发现 AR-48~AR-53（6 项）
- 深度审计 P2 发现（12 项：01-01/02/03, 04-01/02, 05-01, 07-01, 09-03, 11-01, 16-02/05/06）
- 深度审计高价值 P3（04-02 Store 常量重复、16-04 mock 空操作、16-01 死代码）
- 先前遗留 AR-37 (P1) — 经独立审查确认：`RpcJobInvoker` 的 `null` 参数为 `ICancelToken`（非 timeout），超时已通过 `injectTimeoutHeader` 以 `HEADER_TIMEOUT` 方式传播（默认 60s）。降级为验证项：确认 timeout 传播链路完整性。
- 依赖清理（移除 nop-sys-dao, nop-rpc-cluster, nop-cluster-core）

### Out Of Scope

- 所有 P3 项（除 04-02, 16-04, 16-01），包括 01-04, 04-03/04, 07-02/03, 09-01/02/04, 11-02/03/04, 16-03/07
- AR-6/7/14/15/16/32/45/47（先前 adjudicated P3）
- AR-24/F9（retryRecordId 跨系统问题）
- 跨模块契约维度（深度审计维度 20）

## Execution Plan

### Phase 1 - 并发控制与乐观锁修复（AR-48, AR-49, AR-50, AR-51, AR-52）

Status: completed
	Targets: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/`

- Item Types: `Fix`

- [x] **AR-48 (P2)**: `JobTaskStoreImpl.countRunningTasks` 扩展计数范围包含 CLAIMED 状态（`FilterBeans.in(taskStatus, RUNNING, CLAIMED)`）。**注意**：直接使用 `_NopJobCoreConstants.TASK_STATUS_CLAIMED`（而非新增本地常量），与 Phase 3 Dim04-02 的常量消除保持一致。重命名为 `countInFlightTasks`。添加测试验证 CLAIMED 任务被计入并发限制。
- [x] **AR-49 (P2)**: `JobFireStoreImpl.cancelFire` 的 schedule 更新路径，5 次重试后改为抛出异常（与 `completeFireAndUpdateSchedule` 保持一致），移除 `updateEntityDirectly` 降级。
- [x] **AR-50 (P2)**: `JobScheduleStoreImpl.overlayFireAndAdvanceSchedule` 和 `insertManualFire` overlay 路径引入 `actualCancelledCount`，仅在 `cancelFire` 成功时递增。用实际计数更新 `totalFireCount` 和 `failFireCount`。
- [x] **AR-51 (P3)**: `JobScheduleStoreImpl` 所有 7 个 schedule 更新路径，5 次重试后改为抛出异常（与 `completeFireAndUpdateSchedule` 保持一致）。建议复用 `ERR_JOB_FIRE_STATUS_CONFLICT` 或新增 `ERR_JOB_SCHEDULE_UPDATE_CONFLICT` 错误码。这些操作在 `REQUIRES_NEW` 事务中，失败后下次扫描会重试。
- [x] **AR-52 (P2)**: `JobScheduleStoreImpl.recoveryFireAndAdvanceSchedule` reuse-failed 路径，将 `fireDao().updateEntityDirectly(failedFire)` 改为 `tryUpdateManyWithVersionCheck`。版本冲突时检查 DB 状态，fire 已不在 FAILED/TIMEOUT 则跳过恢复。

Exit Criteria:

- [x] `countRunningTasks` 查询包含 CLAIMED 状态，有对应单元测试
- [x] `cancelFire` schedule 更新在重试耗尽后抛出异常而非降级为 `updateEntityDirectly`
- [x] overlay 取消循环使用 `actualCancelledCount`，有测试验证部分取消失败场景的计数准确性
- [x] `JobScheduleStoreImpl` 全部 7 个路径重试耗尽后抛出异常
- [x] recovery reuse-failed 使用 `tryUpdateManyWithVersionCheck`，有测试验证版本冲突时跳过恢复
- [x] `./mvnw test -pl nop-job -am` 全过
- [x] No owner-doc update required（纯内部行为修复）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - SUSPICIOUS 语义修复与 RPC 超时验证（AR-53, AR-37）

Status: completed
Targets: `nop-job/nop-job-coordinator/`, `nop-job/nop-job-service/src/main/java/io/nop/job/service/executor/RpcJobInvoker.java`

- Item Types: `Fix | Proof`

- [x] **AR-53 (P2)**: `JobCompletionProcessorImpl.resolveFinalFireStatus` 中，当所有 task 都不在 WAITING/CLAIMED/RUNNING 状态（只有 SUSPICIOUS + 终态 task）时，不应将 SUSPICIOUS 视为 pending。修改逻辑：如果只有 SUSPICIOUS 和终态 task，将 SUSPICIOUS 视为等同于 TIMEOUT（因 worker 已不可达）。添加混合场景测试。**注意**：`resolveFinalFireStatus` 是 private 方法，需通过 `JobCompletionProcessorImpl` 的公开 API 间接测试。
- [x] **AR-37 (P1→验证)**: 验证 `RpcJobInvoker` 的超时传播链路完整性。**经独立审查确认**：第 63/92 行的 `null` 参数为 `ICancelToken cancelToken`（非 timeout），超时已通过 `injectTimeoutHeader()` 以 `ApiConstants.HEADER_TIMEOUT` 方式传播（默认 60,000ms，可从 `timeoutSeconds` 配置覆盖）。确认无需代码修改。添加测试验证 `injectTimeoutHeader` 正确设置 timeout header。

Exit Criteria:

- [x] SUSPICIOUS + 终态混合场景下 fire 能正常完成，不再无限等待，有测试验证
- [x] `injectTimeoutHeader` 测试验证：有 timeoutSeconds 时使用配置值，无时默认 60,000ms
- [x] `./mvnw test -pl nop-job -am` 全过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 深度审计：依赖清理、ORM 索引与生成管线（Dim 01-01/02/03, 04-01/02, 05-01）

Status: completed
Targets: `nop-job/nop-job-service/pom.xml`, `nop-job/nop-job-dao/pom.xml`, `nop-job/model/nop-job.orm.xml`, `nop-job/nop-job-web/`

- Item Types: `Fix`

- [x] **Dim01-01 (P2)**: 从 `nop-job-service/pom.xml` 移除未使用的 `nop-sys-dao` 依赖
- [x] **Dim01-02 (P2)**: 从 `nop-job-service/pom.xml` 移除 `nop-rpc-cluster` 依赖（运行时由 app 层传递引入）
- [x] **Dim01-03 (P2)**: 从 `nop-job-dao/pom.xml` 移除未使用的 `nop-cluster-core` 依赖
- [x] **Dim04-01 (P2)**: 在 `nop-job.orm.xml` 的 NopJobTask 实体上新增索引 `(taskStatus, workerInstanceId)`
- [x] **Dim04-02 (P2)**: 消除三个 Store 中的硬编码常量副本，统一使用 `_NopJobCoreConstants`
- [x] **Dim05-01 (P2)**: 删除 4 个已废弃实体目录下的全部孤立视图文件（NopJobDefinition, NopJobInstance, NopJobInstanceHis, NopJobAssignment）

Exit Criteria:

- [x] `nop-job-service` 和 `nop-job-dao` 的 pom.xml 无多余依赖，`./mvnw compile -pl nop-job -am` 成功
- [x] ORM 模型新增 `(taskStatus, workerInstanceId)` 索引
- [x] 三个 Store 文件中无本地状态常量定义，全部引用 `_NopJobCoreConstants`
- [x] `nop-job-web` 中无 NopJobDefinition/NopJobInstance/NopJobInstanceHis/NopJobAssignment 目录
- [x] `./mvnw test -pl nop-job -am` 全过
- [x] No owner-doc update required（依赖和索引为内部变更）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 深度审计：BizModel 规范、错误码、XMeta 修复（Dim 07-01, 09-03, 11-01）

Status: completed
Targets: `nop-job/nop-job-service/src/main/java/io/nop/job/service/`, `nop-job/nop-job-core/src/main/java/io/nop/job/core/JobCoreErrors.java`, `nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobSchedule/NopJobSchedule.xmeta`

- Item Types: `Fix`

- [x] **Dim07-01 (P2)**: 提取 `resolveTriggeredBy` 到共享工具类（包：`io.nop.job.service`，类名：`JobContextHelper.resolveTriggeredBy`），NopJobScheduleBizModel 和 NopJobFireBizModel 引用共享方法
- [x] **Dim09-03 (P2)**: 将 `JobCoreErrors` 中 `ERR_JOB_INVOKER_NOT_FOUND` 和 `ERR_JOB_EXECUTION_FAILED` 的错误码字符串改为 `nop.err.job.invoker-not-found` 和 `nop.err.job.execution-failed` 格式。**注意**：修改错误码字符串会破坏现有引用这些字符串的测试断言，需同步更新。
- [x] **Dim11-01 (P2)**: 在 delta xmeta 文件 `nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobSchedule/NopJobSchedule.xmeta`（非生成文件，可安全编辑）中为 `scheduleStatus` prop 添加 `defaultValue="0"`。或覆写 `NopJobScheduleBizModel.defaultPrepareSave()` 在创建时设置初始状态。确保 CRUD save 创建 Schedule 不再触发 DB NOT NULL 错误。**禁止编辑** `_NopJobSchedule.xmeta`（生成文件）。添加测试验证通过 GraphQL save 创建 Schedule 成功。

Exit Criteria:

- [x] `resolveTriggeredBy` 只在一个工具类中定义，两个 BizModel 引用它
- [x] `JobCoreErrors` 中被 throw 的错误码使用 `nop.err.job.*` 格式
- [x] 通过标准 CRUD save 创建 NopJobSchedule 不再触发 DB 约束错误
- [x] `./mvnw test -pl nop-job -am` 全过
- [x] 若 XMeta defaultValue 改变影响前端行为，更新 `docs-for-ai/02-core-guides/api-and-graphql.md`；否则写 No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 深度审计：测试覆盖补充（Dim 16-02, 16-05, 16-06）+ 高价值清理（16-01, 16-04）

Status: completed
Targets: `nop-job/nop-job-coordinator/src/test/`, `nop-job/nop-job-service/src/test/`

- Item Types: `Fix | Proof`

- [x] **Dim16-02 (P2)**: 添加 `TestNopJobTaskBizModel` 测试（位置：`nop-job/nop-job-service/src/test/java/io/nop/job/service/entity/TestNopJobTaskBizModel.java`），验证调用 `delete()` 时抛出 `ERR_JOB_TASK_DELETE_NOT_ALLOWED`
- [x] **Dim16-05 (P2)**: 扩展 `TestRpcBroadcastTaskBuilder`：添加多实例 broadcast 语义验证、null discoveryClient 边界、serviceName 缺失场景
- [x] **Dim16-06 (P2)**: 添加 `resolveFinalFireStatus` 的 CANCELED 优先级独立测试 + SUSPICIOUS pending 测试 + 混合场景（TIMEOUT+CANCELED+SUCCESS）测试
- [x] **Dim16-01 (P3)**: 清理多个测试文件中 `setExecutorKind` 连续调用两次的死代码
- [x] **Dim16-04 (P2→P3)**: 在 TestJobCompletionProcessor 和 TestJobE2E 的 mock store 中，为 `completeFireAndUpdateSchedule` 添加调用验证（如 Mockito verify 或 AtomicBoolean 标志）

Exit Criteria:

- [x] `TestNopJobTaskBizModel.delete_throws_notAllowed` 测试通过
- [x] `TestRpcBroadcastTaskBuilder` 覆盖 ≥4 个场景（健康过滤、多实例 broadcast、null client、fallback）
- [x] `resolveFinalFireStatus` 的 CANCELED、SUSPICIOUS 和混合场景测试通过
- [x] 测试文件中无 `setExecutorKind` 连续调用两次的死代码
- [x] Mock store 的 `completeFireAndUpdateSchedule` 有调用验证
- [x] `./mvnw test -pl nop-job -am` 全过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全部 Round 7 发现（AR-48~AR-53）已修复并有测试覆盖
- [x] 全部深度审计 P2 发现（12 项）已修复
- [x] AR-37 (P1) 已验证：`injectTimeoutHeader` 超时传播链路完整，有测试覆盖
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] Anti-Hollow Check: closure audit 已验证（a）组件间调用链在运行时确实连通，（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-job -am` 成功
- [x] `./mvnw test -pl nop-job -am` 全过
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### Prior P3 Items (from Plans 103-106)

- Classification: `watch-only residual`
- Why Not Blocking Closure: 全部为 P3，不影响数据完整性或状态机正确性。已在 6 轮审查中确认无升级趋势。
- Successor Required: no
- Items:
  - AR-6 (P3): setActiveFireCount(0) dead write — 无功能影响
  - AR-7 (P3): maxFailedCount hardcoded to 0 — 无 ORM column，功能未暴露
  - AR-14 (P3): copyMap 返回原始引用 — ORM flush 保护，实际无数据污染
  - AR-15 (P3): findFirstErrorTask 优先级不一致 — 仅影响 errorCode 展示
  - AR-16 (P3): RpcBroadcastTaskBuilder 无 taskPayload — 执行路径不依赖 payload
  - AR-32 (P3): System.currentTimeMillis 而非 DB clock — 分布式部署下影响有限
  - AR-45 (P3): CronExpression.equals 忽略 timeZone — 未用作集合 key
  - AR-47 (P3): emptyIfNull 死代码 — 无功能影响

### AR-24/F9 retryRecordId

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 跨系统问题，需重新设计 nop-retry 异步 API 回调机制。当前返回 null（不写入误导数据），已有独立设计文档。
- Successor Required: yes
- Successor Path: 需要独立的 nop-retry 集成重新设计 plan

### Deep Audit P3 Items (Dim 01-04, 04-03/04, 07-02/03, 09-01/02/04, 11-02/03/04, 16-03/07)

- Classification: `optimization candidate`
- Why Not Blocking Closure: 全部为 P3 代码规范/可维护性优化，不影响运行时正确性
- Successor Required: no

## Non-Blocking Follow-ups

- 考虑提取 `buildManualFire`/`buildRecoveryFire` 公共部分为 `buildBaseFire`（Dim 07-02）
- 考虑将 `insertManualFire` 重命名为 `insertAdHocFire`（Dim 07-03）
- 考虑将 Calendar 类的 `IllegalArgumentException` 替换为 `NopException`（Dim 09-01）
- 考虑为 `.param()` 名提取 ARG_* 常量（Dim 09-02）
- 考虑提取 3 套独立 Mock Store 为共享 mock 类（Dim 16-03）
- 考虑为 NopJobFire/NopJobTask 显式覆写 save()/update() 抛出业务异常（Dim 11-02/03）
- 考虑在 delta xmeta 中将 triggerSource/scheduledFireTime 等字段设为 updatable=false（Dim 11-04）
- 考虑将 remark 域定义 precision 改为 200（Dim 04-03）
- 考虑新增 `(taskStatus, startTime)` 索引覆盖 ORDER BY（Dim 04-04）

## Closure

Status Note: All 5 phases completed. AR-48~53 fixed, all 12 deep audit P2 findings remediated, AR-37 verified. Build and tests passing.

Closure Audit Evidence:

- Reviewer / Agent: Independent sub-agent closure audit (session ses_171569135ffeO29rLwcy1Dj2LO). Verdict: All code-level exit criteria met. 3 procedural blocking issues resolved (checkboxes, closure section, dev log).

Follow-up:

- Watch pre-existing flaky test TestJobConcurrency.testFullPipelineTwoCoordinators (test isolation issue)
- Consider Dim07-02/03/09-01/02/16-03 P3 items for future cleanup
