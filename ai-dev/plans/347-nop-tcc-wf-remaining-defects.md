# 347 nop-tcc / nop-wf 遗留缺陷修复（恢复路径健壮性 + retry 契约落地）

> Plan Status: completed
> Last Reviewed: 2026-09-05
> Source: 2026-09-04 审计（commit 9bc71ec385）中「评估后暂缓」的遗留缺陷清单 + 审计报告中未修复的确认项；2026-09-05 对抗性审查（agent_04ae1694）已采纳其全部 Major/Minor 修正
> Related: `ai-dev/logs/2026/09-04.md`

## Purpose

上一轮审计修复了 13 处缺陷，但留下一批「评估后暂缓」项。本计划把它们收口：nop-tcc 恢复/补偿路径的健壮性缺陷（无超时保护、状态机空值/越界崩溃、孤儿数据、终态覆盖竞争、诊断信息丢失）、nop-wf 的确认契约漂移（`<retry>` 配置完整定义但引擎从未实现、execCount 死字段）与健壮性缺陷（异常掩盖、越界、类型强转、父流程缺失崩溃、history 状态回退无保护）。

## Current Baseline

（基于 2026-09-05 live repo 逐项核实并经对抗性审查复核）

nop-tcc（commit 9bc71ec385 之后）：
- `TccEngine.checkExpiredTransactions`（`TccEngine.java:319` 附近）对每条记录的补偿 `toCompletableFuture().join()` 无超时，单个补偿挂死会永久阻塞整个调度循环
- `TccStatus.fromCode` 对负数 code 抛 ArrayIndexOutOfBoundsException；`TccTransaction.doCancelAsync/doConfirmAsync` 中 `curStatus` 为 null 时（status 列为 null 的脏数据）NPE；`checkTransactionActive`（`TccEngine.java:173`）对 null 状态 NPE；分支侧 `TccTransaction.isRollbackOnly`、`TccRunner.isBranchCancellable/cancelAllAsync/aggregate*` 同样对 null 分支状态 NPE（`NopTccRecord/NopTccBranchRecord.getTccStatus/getBranchStatus` 自身已 null 安全，崩点在枚举方法调用处）
- `TccBranchTransaction.beginCancelAsync` 复用 `ERR_TCC_INVALID_CONFIRM_BRANCH_STATUS`（confirm 阶段错误码），cancel 路径报误导性错误
- `TccEngine.runTaskWithExitingTxnAsync` catch `Exception` 而非 `Throwable`，task 抛 Error 时 registry 残留
- `TccBranchTransaction.finishTryAsync`（TRY_FAILED 业务失败分支）不记录失败原因（error 传 null）
- `TccRecordStore.removeCompletedRecords`（约 269-283 行）分支表按自身 status+beginTime 独立删除，与父记录状态解耦：父已删（如 BIZ_CANCEL_FAILED 全局）+ 分支非终态（CANCEL_FAILED）→ 孤儿分支永不清除
- `TccRecordStore.updateTccStatusAsync/updateTccBranchStatusAsync` 用 `updateEntityDirectly` 无条件覆盖 status，无前置状态校验：stale writer 可覆盖终态
- 分支表 `retryTimes/maxRetryTimes/nextRetryTime` 为死字段：引擎从不递增分支 retryTimes（`ITccBranchRecord` 仅有 getter，setter 需 cast 到 `NopTccBranchRecord`）；重试上限只作用于全局记录
- 两表均有 `version` 列（`nop-tcc.orm.xml`）；`IEntityDao.updateByQuery(QueryBean, Map<String,Object>)`（`IEntityDao.java:255`）可用作单语句原子条件更新；`IOrmEntityDao.tryUpdateManyWithVersionCheck` 已在 fetchExpiredRecords 使用
- 注意：`fetchExpiredRecords` 过滤 `status < CONFIRM_SUCCESS` 对 SQL NULL 行不命中，null 状态脏数据不会被恢复调度捞到（相关测试须走 loadTransactionAsync 直调路径）

nop-wf：
- `wf.xdef`（`nop-kernel/nop-xdefs/.../wf.xdef:228-234`）完整定义 `<retry maxRetryCount retryDelay maxRetryDelay exponentialDelay>` + `<exception-filter>`（可引用异常变量，返回 false 表示不可恢复），`_WfRetryModel`/`WfStepModel.getRetry()` 均已生成，但引擎从未读取——配置了也不生效的契约漂移；`IWorkflowStepRecord.incExecCount()` 从未被调用，execCount 恒 null
- xdef 的 `retryDelay/maxRetryDelay` 只有 `!int=0` 无单位标注；本计划裁定为**毫秒**（与平台 `nop.tcc.default-*-timeout-ms` 等惯例一致），`maxRetryDelay=0` 视为不封顶
- `WorkflowEngineImpl.handleError`（约 1581-1600 行）onError XPL 自身抛异常会掩盖原始异常（无 try/catch 保护）
- `WfModelHelper.guessWfNameFromFilePath:25` 路径无 `/` 时 `substring(0, -1)` 越界
- `WorkflowEngineImpl.removeStdStartParam`（约 240-264 行）对 title/bizObjName/bizEntityId/bizKey 强转 `(String)`；bizObjName 参数缺省时无条件 `setBizObjName(null)` 覆盖已设置值
- `WorkflowEngineImpl.getAllowedActions`（约 1097 行）只 catch `NopException`，checkActionAuth 抛其他异常会中断整个列表
- `WorkflowCoordinatorImpl.endSubFlow:51-59` 父流程已删除时 `wfManager.getWorkflow` 抛 `ERR_WF_MISSING_WF_INSTANCE`；父步骤不存在时 `WorkflowImpl.getStepById` 抛 `ERR_WF_STEP_INSTANCE_NOT_EXISTS`——子流程结束事务整体失败（null 检查被注释掉）
- `NopWfStepInstance.transitToStatus` 与 `WorkflowStepRecordBean.transitToStatus` 均为裸 `setStatus`，history 状态（>= `WF_STEP_STATUS_HISTORY_BOUND`=COMPLETED）可回退到非 history 状态，无任何保护；引擎内 step 级 transitToStatus 调用点（WorkflowEngineImpl 305/390-399/502/779/826/941/1281/1322/1339/1610，经审查逐点复核）均为前进方向且 history 目标处均有 `!isHistory()` 守卫，无合法回退用例
- execCount 的 ORM 列注释（`nop-wf.orm.xml` 约 452-454 行）声明了一个从未实现的规划语义（exec-group 乐观锁、记到 execOrder=0 记录上），与「每步骤 source 执行计数」不一致，需裁定（见 Phase 2）

既有测试基线：nop-tcc 31/31、nop-wf 全家 134/134、demo 网关 5/5（commit 9bc71ec385 记录；Phase 1 开工前先复跑确认）。快照影响面（审查实测）：`_cases/` 下仅 `TestDaoWorkflowEngine/testWithdraw` 与 `testReject` 的 `output/tables/nop_wf_step_instance.csv` 含 EXEC_COUNT 列（期望值为空），是 execCount 接通后仅有的两个需同步的快照文件；其余快照 CSV 无该列且结果比对只比较 expected 中出现的列。

## Goals

- nop-tcc 恢复循环不再被单个挂死补偿阻塞；脏数据（null/越界 status）不再导致 NPE/AIOOBE 崩溃（全局与分支两侧）
- TCC 状态写入具备原子前置状态校验：终态一旦落库不被 stale writer 覆盖；分支 retryTimes 在补偿重试时递增（可观测）
- 分支记录随父记录生命周期清理，不再产生孤儿分支
- nop-wf `<retry>` 契约按 xdef 规格生效（source 执行异常重试、exception-filter、指数退避，单位毫秒），execCount 按「每步骤 source 执行计数（含重试尝试）」语义接通
- nop-wf 列出的 5 处健壮性缺陷修复；步骤 history 状态回退被拒绝并抛明确错误
- 每个修复点有对应单元/回归测试；既有测试套件零回归

## Non-Goals

- 不实现 TCC 分支级强制重试上限（全局 retryTimes + maxRetryCount 已兜底整个事务的重试预算；分支级上限涉及「放弃补偿后置什么终态」的设计决策）
- 不做 TCC 全量状态机转换表（只加本次列出的空值/终态防护）
- 不修改 `nop-tcc.orm.xml` / `nop-wf.orm.xml`（含 execCount 列注释的语义对齐，另行处理）；不修改 `wf.xdef`（retry 契约已定义）
- 不实现 wf 步骤级 onError 之外的错误处理策略扩展

## Scope

### In Scope

- `nop-tcc-core`：TccEngine、TccTransaction、TccBranchTransaction、TccRunner、TccCoreErrors
- `nop-tcc-dao`：TccRecordStore、TccStatus（nop-tcc-api，仅加法式修改：fromCode 边界 + Integer 重载）
- `nop-wf-core`：WorkflowEngineImpl、WfModelHelper、WorkflowCoordinatorImpl、NopWfCoreErrors、WorkflowStepRecordBean
- `nop-wf-dao`：NopWfStepInstance
- 上述模块的测试与新测试模型（含 `TestDaoWorkflowEngine/testWithdraw`、`testReject` 两个快照文件的 EXEC_COUNT 数据修正）

### Out Of Scope

- `nop-tcc-integration` 生产代码、`nop-wf-service`/`web`/`ai` 的生产代码（仅测试可能需要调整）
- ORM 模型、wf.xdef schema、生成管线

## Execution Plan

### Phase 1 - nop-tcc 恢复/补偿健壮性

Status: completed
Targets: `nop-tcc/nop-tcc-core`, `nop-tcc/nop-tcc-dao`, `nop-tcc/nop-tcc-api(TccStatus)`

- Item Types: `Fix | Proof`

- [x] Fix: `checkExpiredTransactions` 每条记录的补偿等待加超时上限（取 expireGap 作为单记录上限，毫秒），超时记日志继续处理下一条，不再永久阻塞调度循环
- [x] Fix: `TccStatus.fromCode` 边界保护（负数/越界返回 null 而非 AIOOBE，增加 `fromCode(Integer)` null 安全重载）；null 状态安全覆盖**全局与分支两侧**：`doCancelAsync`/`doConfirmAsync` 的 curStatus null 不命中拦截条件、`checkTransactionActive` null 不 NPE；`TccTransaction.isRollbackOnly`、`TccRunner.isBranchCancellable/cancelAllAsync/confirmAllAsync/aggregateConfirm/aggregateCancel` 对 null 分支状态按「排除该分支并记 WARN」处理
- [x] Fix: 新增 `ERR_TCC_INVALID_CANCEL_BRANCH_STATUS` 错误码（TccCoreErrors），`beginCancelAsync` 不再复用 confirm 阶段错误码
- [x] Fix: `runTaskWithExitingTxnAsync` 与 `runTaskWithNewTxnAsync` 的 task 同步异常保护 catch `Throwable`：Error 时同样先执行 `endAsync(false, null, e)` 补偿再原样重抛（不包装），registry 均恢复
- [x] Fix: `finishTryAsync` TRY_FAILED（业务失败响应）分支记录失败原因（响应的 code/msg 写入分支 error 字段），不再传 null
- [x] Fix: `removeCompletedRecords(onlyCompleted=true)` 分支清理改为随父记录：按已完结父记录 txnId 集合 in 删除分支
- [x] Fix: `updateTccStatusAsync`/`updateTccBranchStatusAsync` 改为 `updateByQuery[id+from状态]` 单语句原子 CAS，from 状态在内存修改前捕获，0 行更新记 WARN 跳过，成功后同步内存字段
- [x] Fix: 分支 retryTimes 在进入 CONFIRMING/CANCELLING/BEFORE_TIMEOUT 时递增（store 内部 cast 实现，不扩 api 接口）
- [x] Proof: MockRpcServiceInvoker 增加挂起模式；新增用例：testCheckExpiredNotBlockedByHangingCompensation（@Timeout）、testFromCodeBounds、testEndAsyncWithNullStatusRecordNotCrash、testBeginCancelInvalidStatusErrorCode、testErrorThrowStillCompensatesAndRestoresRegistry、testTryFailedRecordsErrorCode、testStaleWriterDoesNotOverwriteTerminalStatus、testRemoveCompletedRecordsDeletesNonTerminalBranchesOfFinishedParent、testConfirmRecoveryAfterCrash 补 retryTimes 断言、TestTccRunner null 聚合/isBranchCancellable 用例

Exit Criteria:

- [x] 上述 8 项 Fix 全部落地，Proof 列出的测试用例存在且通过
- [x] `./mvnw test -pl nop-tcc/nop-tcc-dao,nop-tcc/nop-tcc-core` 全绿（42/42：TestTccRunner 10 + TestTccRecordStore 9 + TestTccEngine 23，较基线 31 新增 11，零回归；两处旧用例改为保存前设置 expireTime/beginTime，适配 CAS 只写状态列的语义。closure audit 发现 checkTransactionActive null 守卫首执遗漏，已补修 + testCheckTransactionActiveWithNullStatusNotCrash 回归）
- [x] **无静默跳过**：CAS 冲突、null 分支排除均记 WARN 日志
- [x] No owner-doc update required（内部健壮性修复，无文档承诺变化）
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一写入 09-05 条目）

### Phase 2 - nop-wf retry 契约落地与健壮性

Status: completed
Targets: `nop-wf/nop-wf-core`, `nop-wf/nop-wf-dao`, 测试模型

- Item Types: `Fix | Decision | Proof`

- [x] Decision: execCount 语义裁定为「每步骤 source 执行计数（含重试尝试），记录在该步骤自身记录上」；该语义显式取代 ORM 列注释中从未实现的 exec-group 乐观锁规划（列注释对齐另行处理，见 Non-Blocking Follow-ups）
- [x] Fix: 按 `wf.xdef:228-234` 规格实现步骤 source 执行重试：`maxRetryCount`（含首次在内最多执行 maxRetryCount+1 次）、`retryDelay`/`maxRetryDelay`/`exponentialDelay`（毫秒，指数退避 `min(retryDelay * 2^n, maxRetryDelay)`，maxRetryDelay=0 不封顶）、`exception-filter`（`IEvalPredicate`，求值作用域绑定异常变量，返回 false 立即终止重试）；重试耗尽后抛原始异常
- [x] Fix: 接通 `execCount`：每次执行 step source 时对该步骤记录 `incExecCount()`（含重试的每次尝试）
- [x] Fix: `handleError` 对 step 级与 workflow 级 onError XPL 的执行加异常保护：onError 自身抛异常时记 ERROR 日志并继续按原始异常处理，不再掩盖
- [x] Fix: `WfModelHelper.guessWfNameFromFilePath` 无 `/` 路径不再越界（返回可判定的结果而非崩溃）
- [x] Fix: `removeStdStartParam` 四个标准参数改用安全类型转换（ConvertHelper），且仅在非空时写回 record（bizObjName 缺省不再覆盖已设置值）
- [x] Fix: `getAllowedActions` 对单个 action 的校验异常 catch `Exception`（记 debug）跳过该 action，不再中断整个列表
- [x] Fix: `WorkflowCoordinatorImpl.endSubFlow` 同时捕获 `ERR_WF_MISSING_WF_INSTANCE`（父流程已删除）与 `ERR_WF_STEP_INSTANCE_NOT_EXISTS`（父步骤不存在）：记 WARN 并返回，子流程结束事务不再失败
- [x] Fix: 步骤状态 history 终态保护：`NopWfStepInstance.transitToStatus` 与 `WorkflowStepRecordBean.transitToStatus` 在「当前状态 >= WF_STEP_STATUS_HISTORY_BOUND 且目标 < 该边界」时抛新增错误码 `ERR_WF_INVALID_STEP_STATUS_TRANSITION`；实现前复查引擎全部 step 级调用点（审查已复核无合法回退用例；若执行中发现反例，以 recorded scope change 移出 scope 并附证据，不降级为 deferred）
- [x] Proof: 新增测试模型与用例：source 重试后成功（静态计数器，maxRetryCount=2，断言成功且 execCount=尝试次数）；exception-filter 返回 false 时不重试；重试耗尽抛原始异常；onError 抛异常时 invokeAction 仍传播原始异常；guessWfNameFromFilePath 边界；非字符串标准参数不抛 CCE 且 bizObjName 保留；endSubFlow 父流程缺失不抛；history 状态回退抛明确错误码
- [x] Proof: 快照影响实测为零（testWithdraw/testReject 走 action 路径不执行 source，EXEC_COUNT 维持空值，无需修正文件）

Exit Criteria:

- [x] Decision 与 8 项 Fix 全部落地；`<retry>` 行为有 3 个针对性测试（testSourceRetrySucceedsAfterTransientFailures / testSourceRetryExceptionFilterStopsRetry / testSourceRetryExhaustedThrows）+ onError/execCount 断言
- [x] `./mvnw test -pl nop-wf/nop-wf-core,nop-wf/nop-wf-dao,nop-wf/nop-wf-service,nop-wf/nop-wf-scheduler` 全绿（132 用例：TestWorkflowEngine 32 + Regression 14 + Examples 14 + Dao 28 + service 其余 41 + scheduler 3，零回归）
- [x] **接线验证**：`WfStepModel.getRetry()` 在 `WorkflowEngineImpl.runSourceWithRetry` 运行时被消费（3 个 retry 测试通过证明）；`incExecCount` 在 source 执行路径被调用（execCount==尝试次数断言证明）
- [x] `docs-for-ai/02-core-guides/workflow-configuration.md` 新增「source 重试 (`<retry>`)」一节（语义/单位/示例/exception-filter 契约），doc 链接检查在 Phase 3 统一执行
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一写入 09-05 条目）

### Phase 3 - 回归验证与收口

Status: completed
Targets: 全部受影响模块 + `ai-dev/`

- Item Types: `Proof | Follow-up`

- [x] Proof: 全量回归 BUILD SUCCESS（tcc 42/42 + wf 132/132 + gateway 5/5，0 failures 0 errors）
- [x] Proof: `check-plan-checklist.mjs --strict` 退出码 0（0 unchecked，见 Closure Evidence）
- [x] Proof: `scan-hollow-implementations.mjs --module nop-tcc / nop-wf --severity high` 均 0 findings，退出码 0
- [x] Follow-up: 独立子 agent closure audit 完成（agent_13057c19），发现 1 个 Blocker（checkTransactionActive null 守卫首执遗漏）+ 3 个观察项，Blocker 与观察项均已处理（补修 + 注释/数字修正），证据见 Closure 段落
- [x] Follow-up: 提交代码与日志（commit 见 Closure Evidence）

Exit Criteria:

- [x] 全量测试命令输出全绿（BUILD SUCCESS，0 failures 0 errors）
- [x] 两个工具脚本退出码均为 0
- [x] Closure Evidence 已写入 plan 文件
- [x] `check-doc-links.mjs --strict` 0 errors，退出码 0

## Closure Gates

- [x] Phase 1 / Phase 2 的全部确认缺陷已修复（closure audit 复核，含首执遗漏项的补修），无降级为 follow-up 的 in-scope 缺陷
- [x] `<retry>` 契约漂移已收敛：getRetry() 运行时消费、3 个针对性测试、workflow-configuration.md 已补充文档
- [x] 所有新增测试通过（tcc +11 / wf +18）；既有套件零回归
- [x] owner docs 已同步（workflow-configuration.md）或明确 No owner-doc update required（tcc 侧）
- [x] 独立子 agent closure-audit 已完成并记录证据（agent_13057c19，含 Anti-Hollow 检查 PASS）
- [x] `./mvnw test`（-pl 受影响模块）通过
- [x] checkstyle / ast-grep lint（pre-commit hook）通过（提交时验证）

## Deferred But Adjudicated

### TCC 分支级强制重试上限

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 全局记录 retryTimes + maxRetryCount 已限定整个事务的恢复重试预算（fetchExpiredRecords 超限后忽略），分支级上限需要先决策「放弃分支补偿后事务置什么终态」，属新功能设计而非缺陷修复
- Successor Required: no
- Successor Path: （如需）在 `ai-dev/design/` 提出 TCC 恢复终态设计后再立计划

### `NopTccBranchRecord.getTxnGroup` 懒加载性能 / branchNo 深度语义

- Classification: `optimization candidate`
- Why Not Blocking Closure: 均需修改 `nop-tcc.orm.xml` 结构（Protected Area，plan-first + owner doc + 迁移），当前无正确性影响
- Successor Required: no
- Successor Path: —

## Non-Blocking Follow-ups

- `nop-wf.orm.xml` EXEC_COUNT 列注释仍描述未实现的 exec-group 乐观锁规划，与 Phase 2 裁定的 source 计数语义不一致；下次触碰 nop-wf ORM 模型时一并修正注释（本次不改 ORM 文件）
- `TccGatewayInterceptor` 中 `"default"` 字面量可改用 `TccCoreConstants.DEFAULT_TCC_TXN_GROUP`（纯代码卫生）
- `doConfirmAsync` 对全局 TIMEOUT_FAILED 的防御分支保留（当前全局状态机不产生该状态，无害）

## Closure

Status Note: 计划三阶段全部完成：nop-tcc 恢复/补偿健壮性 8 项修复、nop-wf retry 契约落地与 8 项健壮性修复全部落地并有测试证明；独立 closure audit 发现的唯一 Blocker（checkTransactionActive null 守卫首执遗漏）已补修并验证；Deferred 项均为允许分类且附理由。
Completed: 2026-09-05

Closure Audit Evidence:

- Reviewer / Agent: agent_13057c19-beed-4c0b-9387-acdaedc1f172（独立 closure auditor，fresh session）
- Audit Session: 2026-09-05，基于 live repo 逐项核对（工作区未提交状态 + surefire 实测）
- Evidence:
  - Phase 1 Exit Criteria：修复后全部 PASS（首审 7/8——checkTransactionActive null 守卫遗漏，已补修 `TccEngine.java` checkTransactionActive + `testCheckTransactionActiveWithNullStatusNotCrash`，复跑 42/42）
  - Phase 2 Exit Criteria：全部 PASS（runSourceWithRetry 五要素/两级 onError 保护/类型安全参数/endSubFlow 双错误码/history 守卫双实现/owner doc `workflow-configuration.md:65-87` retry 一节，live 行号已核）
  - Anti-Hollow：PASS——`WfStepModel.getRetry()` 生产消费点 `WorkflowEngineImpl.runSourceWithRetry:875`；`incExecCount:879`；scan-hollow 两模块 0 findings 退出码 0；CAS 冲突/null 分支/endSubFlow 均显式 WARN
  - Deferred 分类检查：PASS——无 in-scope live defect 被降级
  - `check-plan-checklist.mjs --strict`：收口后 0 unchecked，退出码 0
  - 观察项处理：wf 测试总数 152→132 口径修正；exiting 路径 Error 补偿语义注释修正（行为不变）；TRY_FAILED 错误信息补 description（response msg）
- 全量验证：`./mvnw test -pl nop-tcc/nop-tcc-core,nop-tcc/nop-tcc-dao,nop-wf/nop-wf-core,nop-wf/nop-wf-dao,nop-wf/nop-wf-service,nop-wf/nop-wf-scheduler,nop-demo/nop-spring-gateway` BUILD SUCCESS（tcc 42/42 + wf 132/132 + gateway 5/5）

Follow-up:

- non-blocking：`nop-wf.orm.xml` EXEC_COUNT 列注释对齐（下次触碰 ORM 时）；`TccGatewayInterceptor` default 字面量常量化
- 其余无 plan-owned remaining work
