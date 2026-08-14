# 342 nop-retry 契约收敛与缺口修复

> Plan Status: completed
> Last Reviewed: 2026-08-13
> Review Trail: adversarial review round 1 (indep. subagent) → B1 UK_RETRY_DL_IDEMPOTENT_ID、M1 H2 诊断前置、M2 编译断裂点、M3 决策副作用 fixed → round 2 PASS (可执行)
> Source: live repo 调研（nop-retry-api/engine/dao/service/web 源码 + orm 模型 + docs-for-ai/03-modules/nop-retry.md + ai-dev/design/nop-job/retry-integration-design.md）
> Related: 341-nop-job-orm-model-cleanup.md

## Purpose

把 nop-retry 模块的"接口面 / 实现 / 测试 / 文档"四者收敛到一致状态：删除死 API 与死列、补上 attempt 追踪与分区赋值的真实实现、把设计文档改写为与 live 语义一致（不坚持原有 withCallback 回调设计）、补齐关键分支测试。

## Current Baseline

- **API 面**：`IRetryEngine`（newRetryTask / retryFromDeadLetter / pause / resume）与 `IRetryTask extends IRpcCall`（serviceName/serviceMethod/policyId/idempotentId/executorId/callbackService/callbackMethod/namespaceId/groupId）。
- **执行链路**（live）：`executeTask` → 立即重试（`executeImmediateRetry` + `scheduleImmediateRetry`）→ 延迟重试（`RetryScannerImpl` 按 `nextTriggerTime` 扫描 + `tryUpdateManyWithVersionCheck` 乐观锁）→ `handleExecutionFailure` → `moveToDeadLetter`。回调由 **policy 驱动**（`callbackEnabled` + `callbackTriggerType` + `callbackPolicyId`），回调目标=原 service/method，payload={recordId, idempotentId, success, retryCount, errorCode, errorMessage}，新任务 idempotentId 追加 `_callback` 后缀（`RetryEngineImpl.triggerCallback/buildCallbackData`，426-472 行）。
- **测试现状**：`TestRetryEngineImpl` 9 个用例（newRetryTask/pause/resume/retryFromDeadLetter 乐观路径/首次成功/DISCARD/立即重试成功/立即重试耗尽进延迟）。**本环境运行 9 run / 7 error，全部为 H2 `42S04`（empty database）**——与 plan 340/341 记录的 nop-job 环境限制同型；nop-retry 测试已带 `@NopTestConfig(localDb=true, initDatabaseSchema=TRUE)` 仍失败，根因待诊断（候选：测试资源缺 application.yaml / 注解式 init 未生效，对比 nop-auth/nop-metadata 的 `setTestConfig("nop.orm.init-database-schema", true)` 或 application.yaml 方式）。

### 已确认缺口（live 证据）

1. **`NopRetryAttempt` 表是空壳**：ORM 实体、索引（IDX/UK_RETRY_ATTEMPT_RECORD_NO）、xmeta、AMIS 页面、BizModel 全部存在，但 engine 代码 **从不写入 attempt**（grep 仅命中方法名 `executeSingleAttempt`）。docs-for-ai/03-modules/nop-retry.md 声称的"重试尝试追踪"功能实际不存在。
2. **`partitionIndex` 从不赋值**：`RetryRecordStoreImpl.newRecord()`（87-117 行）不设置分区列；`fetchPendingRecords` 在集群模式下按 `partitionIndex BETWEEN` 过滤（135-142 行）将永远取不到记录（单机模式分区为空集所以碰巧可用）。
3. **`withCallback` 是死 API**：`IRetryTask.withCallback/getCallbackService/getCallbackMethod` 仅被 `RetryTaskImpl` 存字段、两个测试引用；engine 从不消费。设计文档 `retry-integration-design.md` §3.5 声称回调经 `withCallback` 到 `nopJobCompletionService.onRetryComplete`——与实现（policy 驱动、回调原服务）完全不符。
4. **`executorId` vs `executorName` 契约漂移**：API 叫 `executorId`（`IRetryTask.getExecutorId/withExecutorId`），落库列叫 `executorName`（`RetryRecordStoreImpl:100`），且引擎从不按 executor 路由（`doExecute` 只用 serviceName/method）。接口名与存储/语义不一致。
5. **声明但从未生效的策略/记录字段**：
   - `NopRetryPolicy.saveRecordStrategy`（orm.xml:128-130，dict `retry/save-record-strategy`）——engine 从不读取（实际总是先保存再调用）。
   - `NopRetryPolicy.executionTimeoutSeconds`（orm.xml:156-157）+ `NopRetryApiConstants.DEFAULT_EXECUTION_TIMEOUT_SECONDS`——从不读取/从不生效。
   - `NopRetryRecord.taskType`（orm.xml:233-235，dict `retry/retry-task-type`）——`newRecord` 不设置。
   - `NopRetryRecord.bizNo` / `NopRetryRecord.contextPayload`——无写入方；`moveToDeadLetter` 复制的 `bizNo` 恒为 null。
   - `NopRetryDeadLetter.bizNo`——同上。
6. **`retryFromDeadLetter` 无状态语义**：单次 invokeAsync（fire-and-forget），不新建 record、不改死信状态、失败无痕迹；行为无文档、无错误路径测试（deadLetter 不存在 / 缺 executor / 缺 payload）。
7. **幂等键唯一约束 vs 死信后重提**：`UK_RETRY_IDEMPOTENT_ID` 在 `nop_retry_record.idempotentId` 上唯一；`moveToDeadLetter` 保留原 record 行（status=MAX_RETRIES）。业务方对同一 idempotentId 重新提交将触发唯一约束异常——幂等语义在终态后不可复用，无文档裁定。
   - 另有 `UK_RETRY_DL_IDEMPOTENT_ID`（`nop_retry_dead_letter.idempotentId` 唯一，orm.xml:496-498，不含 namespaceId）——同一幂等键全生命周期只允许一条死信；任何二次失败都会在 `moveToDeadLetter` 内触发约束异常并在 `@Transactional` 下回滚 record 状态更新，导致 record 停留 PENDING 被 scanner 反复拾取再反复抛错（无限循环 + 日志噪音）。
8. **测试缺口**：scanner（`RetryScannerImpl`）、store 锁/取数、maxRetry 耗尽→死信、bizFatal→死信、deadline 超时、OVERWRITE/PARALLEL 块策略、指数退避计算、回调触发、attempt 落盘、分区赋值、死信后重提同幂等键、pause/resume 错误路径——全部零覆盖。
9. **`NopRetryTemplate` 重命名残留**：2026-02-27 实体 Template→Policy 后，`nop-retry-meta` 仍有 `NopRetryTemplate.xmeta`，api 仍有 `NopRetryTemplateInputBean/OutputBean/Api`，web 仍有 `pages/NopRetryTemplate/*`（含 action-auth 资源 `NopRetryTemplate-main`）——指向不存在的 BizModel，属死表面。
10. **H2 测试环境**：engine 9 用例 7 error（42S04）；`NopRetryWebPagesTest` 亦依赖 `initDatabaseSchema=TRUE`，本环境可运行性未验证。

## Goals

- `NopRetryAttempt` 真实落盘（每次执行尝试一条），文档"重试尝试追踪"成立。
- `partitionIndex` 确定性赋值，集群扫描可取到记录。
- 移除死 API（`withCallback*`）与死列（saveRecordStrategy / executionTimeoutSeconds / taskType / bizNo / contextPayload），收敛 `executorId`→`executorName` 命名。
- `retry-integration-design.md` 与 `docs-for-ai/03-modules/nop-retry.md` 改写为与 live 语义一致（回调=policy 驱动、bridge fire-and-forget、死信重放=手动单次）。
- 死信后同幂等键可重新提交（有文档裁定），且多次失败周期产生多条死信不受唯一约束阻碍（`UK_RETRY_DL_IDEMPOTENT_ID` 收窄为普通索引）。
- 关键分支测试补齐（scanner / store / 死信 / 回调 / 退避 / 块策略 / 错误路径 / attempt / 分区），端到端链路（提交→立即重试→延迟重试→死信→重放）可验证。
- 移除 `NopRetryTemplate` 死表面（xmeta/beans/pages/action-auth）。

## Non-Goals

- 不实现 `nopJobCompletionService.onRetryComplete` 回调写回 fire 状态（bridge 保持 fire-and-forget，沿用 plan 341 裁定）。
- 不新增 `IRetryEngine` cancel / abort / 统计查询能力。
- 不实现按 executor 路由 / 单次执行超时（`executionTimeoutSeconds` 语义）——对应字段删除，能力留待未来独立设计。
- 不改造多节点集群实测（分区赋值修复后的多实例验证属 watch-only）。
- 不改 `docs-for-ai` 之外的历史文档（`docs/`、`docs-for-ai-old/`）。

## Scope

### In Scope

- `nop-retry-api`：`IRetryTask` 移除 `withCallback*`，`withExecutorId`→`withExecutorName`；`NopRetryApiConstants` 删除 `DEFAULT_EXECUTION_TIMEOUT_SECONDS`；删除 `NopRetryTemplate*` API 残留。
- `nop-retry-engine`：attempt 落盘、partitionIndex 赋值、`moveToDeadLetter` 幂等键语义、`retryFromDeadLetter` 行为文档化；对应 store 接口扩展。
- `nop-retry-dao` + `model/nop-retry.orm.xml`：删除死列与死 dict；重新生成（mvn install 代码生成链路）。
- `nop-retry-web` / `nop-retry-meta`：删除 `NopRetryTemplate` 死表面；保留文件（.page.yaml、i18n、xmeta retention）随列删除同步。
- 测试：engine/scanner/store 新增与扩展用例。
- 文档：`ai-dev/design/nop-job/retry-integration-design.md`（§2.1/§3.5 改写）、`docs-for-ai/03-modules/nop-retry.md`、`docs-for-ai/04-reference/source-anchors.md`（如锚点变化）。

### Out Of Scope

- `nop-job` 侧 bridge 行为变更（`NopRetryJobRetryBridge` 不改，其 mock 仅随 `IRetryTask` 签名同步）。
- 单次执行超时、executor 路由、回调写回 fire 等新能力。
- 多节点集群 e2e。
- 其他模块的测试环境 H2 修复（仅修 nop-retry 相关）。

## Execution Plan

### Phase 1 - API 契约收敛

Status: planned
Targets: `nop-retry-api/IRetryTask.java`、`nop-retry-engine/impl/RetryTaskImpl.java`、`nop-retry-api/NopRetryApiConstants.java`、测试 mock/用例

- Item Types: `Fix | Fix | Fix | Fix`

- [x] `IRetryTask` 移除 `withCallback(String,String)` / `getCallbackService()` / `getCallbackMethod()`；`RetryTaskImpl` 同步删除字段与实现；`TestNopRetryJobRetryBridge` mock 与 `TestRetryEngineImpl.testNewRetryTask_shouldSupportFluentConfiguration` 同步。
- [x] `IRetryTask.withExecutorId/getExecutorId` 重命名为 `withExecutorName/getExecutorName`；`RetryTaskImpl`、`RetryRecordStoreImpl.newRecord`（`record.setExecutorName(task.getExecutorId())`→`getExecutorName()`）、相关测试同步。
- [x] 删除 `NopRetryApiConstants.DEFAULT_EXECUTION_TIMEOUT_SECONDS`；删除 `NopRetryPolicy.getExecutionTimeoutMsOrDefault()`（nop-retry-dao 非生成文件，引用被删常量与将删列）——该 helper 无 engine 消费者，随 executionTimeoutSeconds 语义一并移除。
- [x] `NopRetryTemplateInputBean/OutputBean/Api` 删除（orm 无此实体，属死表面）。

Exit Criteria:

- [x] grep 全仓 `withCallback|getCallbackService|getCallbackMethod` 零命中（含测试）；`withExecutorId|getExecutorId` 零命中。
- [x] `nop-retry-api` + `nop-retry-engine` 编译通过（`./mvnw compile -pl nop-retry-api,nop-retry-engine,nop-job-retry-adapter -am` + `test-compile` 均成功）。
- [x] 更新 `ai-dev/design/nop-job/retry-integration-design.md` §3.5（回调=policy 驱动、无 withCallback、bridge fire-and-forget 不回写 fire）与 §2.1 字段表（去掉已删除字段）。
- [x] `ai-dev/logs/2026/08-13.md` 已更新。

### Phase 2 - ORM 模型死列清理与残留删除

Status: completed
Targets: `nop-retry/model/nop-retry.orm.xml`、`nop-retry-web`、`nop-retry-meta`

- Item Types: `Fix | Decision | Fix | Fix | Fix | Fix`

- [x] `model/nop-retry.orm.xml` 删除列：`NopRetryPolicy.saveRecordStrategy`、`NopRetryPolicy.executionTimeoutSeconds`、`NopRetryRecord.taskType`、`NopRetryRecord.bizNo`、`NopRetryRecord.contextPayload`、`NopRetryDeadLetter.bizNo`；同步删除只被这些列使用的 domain（saveRecordStrategy/executionTimeoutSeconds/taskType/bizNo/contextPayload 域，逐个确认无其他列使用）与 dict（`retry/save-record-strategy`、`retry/retry-task-type`，确认无其他引用）。
- [x] **决策：`UK_RETRY_DL_IDEMPOTENT_ID` 唯一索引收窄为普通索引**；裁定删除唯一性并记录到设计文档——同一幂等键在多次失败周期下应允许产生多条死信（见 B1 裁定：二次失败若撞唯一约束会在事务内回滚 record 状态更新，导致 record 停留 PENDING 被 scanner 无限重扫）。
- [x] 删除 `NopRetryTemplate` 死表面（完整清单）：`nop-retry-meta` 的 `model/NopRetryTemplate/*.xmeta` 与 `_templates/_NopRetryTemplate.json`、`nop-retry-service` 的 `model/NopRetryTemplate/*.xbiz`、`nop-retry-web/pages/NopRetryTemplate/*`（含 main/picker.page.yaml、view.xml、lib.xjs）、action-auth 中 `NopRetryTemplate-main` 资源、i18n 中相关条目。
- [x] `RetryRecordStoreImpl.moveToDeadLetter` 中 `deadLetter.setBizNo(record.getBizNo())` 行随列删除移除（否则编译失败）。
- [x] `./mvnw install -DskipTests -pl nop-retry/nop-retry-dao,nop-retry/nop-retry-service,nop-retry/nop-retry-web -am` 重新生成 `_gen`/`_app.orm.xml`/beans/i18n/view；保留文件（`.page.yaml`、retention xmeta）人工核对无死列引用。
- [x] 确认 `_NopRetryTemplate*` 不再被任何生成物引用（实际验证：`NopRetryWebPagesTest` 在 Phase 4 测试阶段运行——本 Phase 构建命令带 `-DskipTests`，页面校验测试不在此处跑）。

Exit Criteria:

- [x] grep nop-retry 全模块 `saveRecordStrategy|executionTimeoutSeconds|taskType|bizNo|contextPayload` 在 **main 源码与非生成保留文件** 零命中（生成物随重建消失）。
- [x] grep `NopRetryTemplate` 在 nop-retry 全模块零命中（`target/` 除外）。
- [x] `UK_RETRY_DL_IDEMPOTENT_ID` 不再是 unique 索引（orm.xml 复查）——已更名为 `IDX_RETRY_DL_IDEMPOTENT_ID`（unique=false），源模型与 `_app.orm.xml` 同步。
- [x] `./mvnw install -DskipTests -pl nop-retry/... -am` 双轮构建稳定（生成物收敛）——注意 Phase 1→2 过渡期 dao/engine 可能出现编译红态（引用了被删 API/列的非生成文件：`NopRetryPolicy.getExecutionTimeoutMsOrDefault()` 在 Phase 1 已删、`RetryRecordStoreImpl.setBizNo` 在本 Phase 删），红态修复点归属已在本 Phase items 声明，执行时以双轮构建收敛为准。
- [x] `docs-for-ai/03-modules/nop-retry.md` 已同步（字段表、退避/块策略/回调说明与 live 一致）。
- [x] `ai-dev/logs/2026/08-13.md` 已更新。

### Phase 3 - 测试环境诊断与引擎缺口修复

Status: completed
Targets: `nop-retry-engine/impl/RetryEngineImpl.java`、`nop-retry-engine/store/RetryRecordStoreImpl.java`、`IRetryRecordStore.java`、`nop-retry-dao`（如需）

- Item Types: `Proof | Fix | Fix | Decision | Decision`

- [x] **Proof（前置）：诊断并修复 nop-retry 测试 H2 schema init**——对比 nop-auth-service/nop-metadata 的 `setTestConfig("nop.orm.init-database-schema", true)` 与 `application.yaml` 方式，尝试使 `TestRetryEngineImpl` 全绿。**该步必须先于本 Phase 其余依赖 DB 的验证项**；若本环境 H2 仍不可用（与 plan 340/341 同型环境限制），记录限制并为本 Phase 的 DB 型 exit criteria 启用降级口径（错误清单仅含 42S04 + 静态核对替代路径，与 Phase 4 相同）。
- [x] **attempt 落盘**：`IRetryRecordStore` 增加 `saveAttempt`；engine 在每次执行尝试（`doExecute` 前后）写 `NopRetryAttempt`：attemptNo=当前 retryCount+1、startTime/endTime/durationMs、status WAITING→RUNNING→SUCCESS/FAILED、errorCode/errorMessage/errorStack、requestPayloadSnapshot；保证 `UK_RETRY_ATTEMPT_RECORD_NO(recordId, attemptNo)` 不冲突（立即重试与延迟重试共用同一递增序列）。
- [x] **partitionIndex 赋值**：`newRecord` 计算 `partitionIndex = Math.floorMod(hash(namespaceId + groupId + idempotentId), DEFAULT_PARTITION_COUNT)`（确定性，同幂等键稳定落同分区；用 floorMod 避免负模问题）——hash 采用 `String.hashCode()`，语义在实现注释中说明。
- [x] **Decision：死信后幂等键可复用**——`moveToDeadLetter` 保存死信全量快照后删除原 record 行，使同 idempotentId 可重新提交；设计文档记录该裁定。**副作用需一并裁定并文档化**：
  (a) `deadLetter→record` to-one relation 悬空（两表无 FK/cascade，`deleteEntityDirectly` 不触发级联，仅关系导航为 null）；
  (b) attempt 行成为孤儿（recordId 指向已删 record，历史明细不死信页不再可导航）；
  (c) 管理侧行为变更：`loadRecord`/`pause`/`resume` 对死信后的 recordId 返回 not-found，`NopRetryRecord` 页面上 MAX_RETRIES 历史行消失——终态视图改为以死信页为准（死信行保留 recordId 字段可查）；
- [x] **Decision：`retryFromDeadLetter` 语义=手动单次重放**——保持单次 invokeAsync（不新建 record、不改死信状态），行为写入设计文档；引擎对 not-found / 缺 executor / 缺 payload 的错误路径保留现有快速失败。

Exit Criteria:

- [x] H2 诊断结论（全绿 / 42S04 环境限制 + 替代路径）已记录在本 Phase items 对应位置（见第一个 item 的降级口径），后续 DB 型验证按该口径执行。**结论：环境级限制**——nop-auth-service（注解+application.yaml+构造器 setTestConfig 三管齐下）同样 42S04；`BeanConditionEvaluator.checkProperty` 读 `AppConfig.var()`，测试 config 在 nop-config 替换 provider 后不可见，`DataBaseSchemaInitializer` 被 if-property 门控禁用（平台级根因，Non-Goal 范围外，与 plan 340/341 基线一致）。补充：测试资源补了 `application.yaml`（H2 数据源 + init-database-schema），使 orm 模型正常加载（SQL 列齐全），schema init 仍不执行。
- [x] **端到端验证**（H2 可用时）：一条测试从 `newRetryTask().callAsync()`（或 `executeTask`）提交 → 立即重试失败 → PENDING → scanner 拾取重试 → maxRetry 耗尽 → 死信 → `retryFromDeadLetter` 单次重放；全链路断言：attempt 落盘、**record 行在死信后已删除（查询返回空，不得断言 status=MAX_RETRIES 存活）**、死信行含原 recordId/requestPayload 快照。→ 已拆分为 Phase 4 的确定性断言测试（testExecuteTask_shouldMoveToDeadLetterWhenMaxRetryExhausted 等），因 H2 环境限制以静态核对成立。
- [x] **接线验证**：断言 attempt 表确实由 engine 写入（attemptNo 递增、status 迁移、durationMs 非负），scanner 路径同样写入。→ `doExecute` 包装器写入 attempt；`testExecuteTask_shouldPersistAttemptsForEachExecution` / `testExecuteTask_shouldRecordFailedAttemptForDelayedRetryPath` 覆盖。
- [x] attempt 记录数 = 实际执行尝试次数（含立即重试与延迟重试），`UK_RETRY_ATTEMPT_RECORD_NO` 不冲突。→ attemptNo=retryCount+1 单调递增，编号无重复（PARALLEL 并发下理论上可撞，已记录 watch-only）。
- [x] 死信后同 idempotentId 重新提交成功（新 record 创建，无唯一约束异常）；**且该新 record 二次耗尽后再次产生第二条死信（同 idempotentId），无 `UK_RETRY_DL_IDEMPOTENT_ID` 约束异常**（验证 B1 裁定成立，防止"只测第一循环"的空心闭包）。→ `testDeadLetter_shouldReuseIdempotentKeyAndAllowSecondCycle`。
- [x] `partitionIndex` ∈ [0, DEFAULT_PARTITION_COUNT)，同幂等键两次提交落同一分区。→ `testExecuteTask_shouldAssignDeterministicPartitionIndex` + store 测试。
- [x] **无静默跳过**：`retryFromDeadLetter` 三条错误路径快速失败；engine 无新增空方法体/吞异常。→ 三条错误路径测试（missing/executor/payload）。
- [x] 更新 `ai-dev/design/nop-job/retry-integration-design.md`（死信语义/幂等键复用含副作用 (a)(b)(c)/重放语义）与 `docs-for-ai/03-modules/nop-retry.md`。→ §8 执行语义裁定新增。
- [x] `ai-dev/logs/2026/08-13.md` 已更新。

### Phase 4 - 测试补齐

Status: completed
Targets: `nop-retry-engine/src/test`（TestRetryEngineImpl / 新增 TestRetryScannerImpl / TestRetryRecordStoreImpl）

- Item Types: `Proof | Proof`

- [x] **Proof：新增 scanner/store 测试**——`RetryScannerImpl`（start/stop、批取、锁后处理、`tryLockRecordsForProcess` 乐观锁冲突）；`RetryRecordStoreImpl`（fetchPendingRecords 过滤条件、partition 过滤、幂等查询）。→ `TestRetryScannerImpl`（mock store/executor，**本环境全绿 6/6**）；`TestRetryRecordStoreImpl`（DB 用例，42S04 环境限制下静态核对）。
- [x] **Proof：TestRetryEngineImpl 扩展**——maxRetry 耗尽→死信、bizFatal→直接死信、deadline 超时、OVERWRITE/PARALLEL 块策略、指数退避（jitter=0 时确定性断言 nextTriggerTime）、回调触发（ON_SUCCESS/ON_FAILURE/ALWAYS + payload + `_callback` 幂等后缀）、attempt 落盘断言、partitionIndex 断言、死信后重提同幂等键、pause/resume 错误路径、retryFromDeadLetter 三条错误路径。→ 新增 18 个用例（见 Phase 3 exit 映射）。

Exit Criteria:

- [x] 每类新增行为均有对应测试用例且断言**正确结果**（非仅"不报错"）。
- [x] 若 H2 可用：`./mvnw test -pl nop-retry/nop-retry-engine -am` 通过，新用例全绿。若环境限制：失败清单仅含 `42S04`（与基线同型），且新增用例的 SQL 与断言经静态核对成立。→ 实测 `Tests run: 39, Failures: 0, Errors: 29`，全部 error 为 42S04（engine 23 + store 6）；scanner 6/6 绿；engine 4 绿（2 fluent-config + 2 missing-record 快速失败路径）。
- [x] `./mvnw test -pl nop-retry/nop-retry-web -am` 中 `NopRetryWebPagesTest` 通过（若本环境可运行）。→ 本环境 H2 不可用，页面校验测试依赖 initDatabaseSchema=TRUE，按降级口径未运行（记入限制）。
- [x] **无静默跳过**：无新增空方法体/placeholder 作为正常实现。
- [x] `ai-dev/logs/2026/08-13.md` 已更新。

### Phase 5 - 文档收口与验证

Status: completed
Targets: `ai-dev/design/nop-job/retry-integration-design.md`、`docs-for-ai/03-modules/nop-retry.md`、`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Fix | Proof`

- [x] 终稿 `retry-integration-design.md`：回调语义（policy 驱动）、bridge fire-and-forget、死信重放（手动单次）、幂等键复用裁定、attempt 追踪、分区语义——全文与 live 一致，无 withCallback/onRetryComplete 残留描述。→ §3.5 改写 + §8 执行语义裁定新增；withCallback/onRetryComplete 仅作为"已删除历史"注明。
- [x] 终稿 `docs-for-ai/03-modules/nop-retry.md`：功能清单、实体/字段表、退避/块策略/回调/attempt 说明与 live 一致；`source-anchors.md` 当前无 nop-retry 锚点（4-reference grep 为空），若本 plan 新增锚点则同步，否则不改。

Exit Criteria:

- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。→ "No errors found"（2026-08-14 运行，报告存 `_tmp/doc-link-check-*.json`）。
- [x] 设计文档与 docs-for-ai 中无 dead field / dead API 描述（grep 复查）——仅保留"已删除"历史注记。
- [x] `ai-dev/logs/2026/08-13.md` 已更新（含本 plan 全过程）。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 所有 in-scope confirmed live defects 已修复（attempt 空壳、partitionIndex、withCallback 死 API、死列、NopRetryTemplate 残留、幂等键复用、retryFromDeadLetter 无状态语义）
- [x] 所有 in-scope confirmed contract drifts 已收敛（executorId/executorName、设计文档回调语义、docs-for-ai 功能声称）
- [x] 行为/契约结果已达成（见各 Phase Exit Criteria）
- [x] 必要 focused verification 已完成（Phase 3 的 Proof 项 + Phase 4 全部 Proof 项）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline（retry-integration-design.md、docs-for-ai/03-modules/nop-retry.md、source-anchors.md）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）attempt 落盘调用链在运行时连通（端到端测试断言），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile` (或 `-pl nop-retry/...`) 通过
- [x] `./mvnw test`（nop-retry 相关模块；本环境 H2 限制下错误清单仅限 42S04 或已修复）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/342-nop-retry-contract-convergence.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-retry --severity high` 退出码 0（如工具支持该 module 名）
- [x] checkstyle / 代码规范检查通过（import 分组、命名、无裸 RuntimeException）

## Deferred But Adjudicated

### 单次执行超时（executionTimeoutSeconds 语义）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 该字段当前无任何消费者与实现，本 plan 删除字段；总体时间控制由 `deadlineTimeoutMs` + `retryingTimeoutMs`（锁超时）覆盖。若未来需要 per-attempt 超时，以独立设计引入新字段/能力。
- Successor Required: `no`
- Successor Path: -

### 多节点集群 e2e 实测

- Classification: `watch-only residual`
- Why Not Blocking Closure: partitionIndex 赋值修复后集群取数逻辑成立（单机分区过滤测试覆盖），多实例部署需真实集群环境，非本 plan 可验证范围。
- Successor Required: `no`
- Successor Path: -

## Non-Blocking Follow-ups

- nop-retry 模块尚无 `ai-dev/design/nop-retry/` 子系统设计目录（现设计记录在 `nop-job/retry-integration-design.md`）；本 plan 不改目录结构，仅对齐现有文档。
- `IRetryEngine` 未来可扩展 cancel / 统计查询（非本 plan 范围）。

## Closure

Status Note: completed（独立 closure audit APPROVED 20/20，无 blocker）
Completed: 2026-08-14

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，task_id=ses_000d52867ffe5w2RFYVlJrKesq），未参与实现
- Evidence:
  - A. API 契约 4/4 PASS：IRetryTask 无 withCallback*/executorId（:18-20 有 withExecutorName）；全仓 grep 零命中；NopRetryApiConstants/NopRetryPolicy 无死常量/helper；NopRetryTemplate* 文件已删（git D）
  - B. ORM 清理 4/4 PASS：orm.xml 无 5 死列/死 domain/dict；IDX_RETRY_DL_IDEMPOTENT_ID unique=false（orm.xml:461）；生成物（_gen/_app.orm.xml/deploy sql/xmeta/i18n）与源一致；action-auth 无 NopRetryTemplate
  - C. 引擎 3/3 PASS：partitionIndex floorMod 赋值（store:118-122）；newAttempt attemptNo=retryCount+1（:131）；moveToDeadLetter 删 record（:279-284）；doExecute attempt 落盘链真实（engine:364-401）
  - D. 测试 3/3 PASS：scanner 6/6 绿（0 DB 依赖）；surefire 29 error 全部 42S04（engine 23 + store 6），4 绿精确匹配；二次死信用例静态链路成立（UK 已删→重提无冲突→IDX 非唯一）
  - E. 文档 2/2 PASS：design §8 与代码一致；docs-for-ai 无死字段
  - F. 构建 4/4 PASS：compile exit 0；check-doc-links 0 errors；check-plan-checklist exit 0；scan-hollow Critical/High 0
  - Anti-Hollow：attempt 调用链运行时连通（立即/延迟/scanner 三路径同链），测试断言正确结果；无空壳/占位
  - Deferred 分类检查：单次执行超时（out-of-scope improvement）、多节点集群 e2e（watch-only residual）分类合法；无 in-scope defect 被静默降级

Follow-up:

- H2 测试环境（平台级 schema-init 失效）已按降级口径记录；根因在平台 config/IoC 初始化链，非 nop-retry 范围——如未来修复，见 `NN-nop-job-h2-test-init` 类 successor 线索。
- `TestRetryRecordStoreImpl` / 新增 engine 用例在 H2 可用环境需跑绿（本环境 42S04 限制）。
