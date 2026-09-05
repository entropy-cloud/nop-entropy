# 349 nop-task 分析报告 P0-P3 缺陷全量修复（红测试→修复→绿）

> Plan Status: completed
> Last Reviewed: 2026-09-05
> Source: `ai-dev/analysis/2026-09/2026-09-05-nop-task-module-analysis.md`（AI 分析报告，用户指示"对于所有问题，编写测试用例验证错误存在，然后修复问题"，含 task.xdef 拼写错误修正授权）
> Related: `ai-dev/plans/344-check-audit-p1-p2-p3-remediation.md`（check 轮已收口）、`ai-dev/plans/346-check2-audit-p0-p3-remediation.md`（check2 nop-task.md 条目由本计划一并处置后回写标注）

## Purpose

修复分析报告确认的全部可修复缺陷：每条先写红测试验证错误存在，再最小修复至绿。工作区已有的两处未提交修复（TaskImpl metrics 判空、GraphTaskStep runningCount 级联顺序）补红验证测试后一并提交验证。

## Current Baseline

- nop-task-core 引擎单机执行链路完整（plan 252-266 遗产），check 轮 14 条已处置（commit c3dc34df53/e6010dc6eb）。
- 工作区含未提交修复：`TaskImpl.java` metrics null 守卫、`GraphTaskStep.java` runningCount 先级联后减计数（经复核均正确，本计划补红验证）。
- check2 的 19 条发现基本存活；分析报告另确认 8 处 SUSPEND 破坏点、状态码双体系错位、图模式 waitError 成功路径挂死等新发现。
- `_NopTaskCoreConstants`（生成）与 ORM 字典（`nop-task/model/nop-task.orm.xml` → task-status/task-step-status dict）为 40/50/60/70 体系；手写 `TaskConstants` 为 30/40/50/60 体系，两者错位。
- task.xdef 属性名拼写错误 `persisVars` 已生成到 `_TaskExecutableModel._persisVars`；xgen 官方生成链路 `precompile/gen-task-xdsl.xgen`（exec-maven-plugin generate-sources 阶段）可用，禁止手改 `_gen`。

## Goals

- SUSPEND 成为引擎级契约：值判断 + 全部容器/包装器/task 层正确传播或短路，挂起任务进入 SUSPENDED 状态且可恢复。
- 引擎状态常量与 ORM 字典/生成常量数值一致，并有一致性守卫测试。
- 图模式错误边在成功/失败两个方向语义正确，无挂死路径；构建期校验重复步骤名。
- wrapper（executor/timeout/retry/sleep）遵守"取消/超时必须使返回 promise 在有限时间内终结"不变量。
- 恢复完整性最小闭环：stateBean/outputs/nextStepName/persistVars 经 stateBeanData 版本化 wrapper 持久化，continuation-skip 重放输出。
- task.xdef 拼写/注释修正并官方再生；persistVars 模型→运行时接线。

## Non-Goals

- 不改 ORM 表结构（不加唯一索引/新列）——fork 分支同 stepPath 行竞争、(taskInstanceId,stepPath) 唯一约束属"DB 断点续跑完整性"设计主题，需 ORM 变更，维持 check/check2 暂缓裁定。
- 不做 TransactionalTaskStepWrapper 的"副作用提交+终态保存同事务"改造（需事务跨回调设计，暂缓裁定维持）。
- 不迁移 deprecated 语法（`<xpl>`/`<custom>`）、不改 input `role`→`roles`（无消费者的装饰属性，重命名纯 churn；在 xdef 注释标注 status）。
- 不实现 service 层生命周期 API / 可视化（产品化路线，另行立项）。
- 不修改 nop-task-service/web/meta/queue 的空壳问题（本计划只修复行为缺陷）。

## Scope

### In Scope

- `nop-task/nop-task-core`：TaskStepReturn/TaskImpl/TaskStepExecution/TaskStepHelper/各 step 与 wrapper/TaskFlowAnalyzer/GraphStepAnalyzer/TaskRuntimeImpl/TaskStepRuntimeImpl/TaskFlowManagerImpl/DefaultTaskStateStore/TaskConstants/TaskErrors。
- `nop-task/nop-task-dao`：DaoTaskStateStore 序列化 wrapper + 残留列清理 + 条件 bean 注册。
- `nop-kernel/nop-xdefs`：task.xdef（persisVars 拼写、retry 注释、persist 注释）；`nop-task/nop-task-core` `_gen` 经 xgen 官方再生。
- 测试：红验证（stash/还原法）+ 回归测试进 `nop-task-core`/`nop-task-ext` 测试套件。
- owner doc：`docs-for-ai/03-modules/nop-task.md` 补 SUSPEND/状态码/图错误边语义。

### Out Of Scope

- 见 Non-Goals；check2 报告中已被 check 轮修复覆盖的条目（回写"复查已修复"标注即可）。

## Execution Plan

> 每条流程：红测试（先于修复验证失败形态）→ 最小修复 → 绿 → 阶段末模块测试。红验证统一用"临时还原修复处代码"法确认测试确实变红。

### Phase 1 - SUSPEND 契约化（P0）

Status: completed
Targets: `TaskStepReturn`、`TaskImpl`、`BuildOutputTaskStepWrapper`、`TaskStepHelper.retry`、`SequentialTaskStep`、`SelectorTaskStep`、`AbstractForkTaskStep`/`ForkTaskStep`/`ForkNTaskStep`/`ParallelTaskStep`、`GraphTaskStep`、`TaskStepExecution`、`TaskErrors`

- Item Types: `Fix | Proof`

- [x] 1.1 `isSuspend()` 改值判断 `STEP_NAME_SUSPEND.equals(nextStepName)`（与 isEnd/isExit 对偶；红验证：BuildOutput 包裹的挂起步骤在 HEAD 下抛 ERR_TASK_UNKNOWN_NEXT_STEP）
- [x] 1.2 `BuildOutputTaskStepWrapper` lambda 开头 `if (res.isSuspend()) return res;`（避免挂起点提前求值 output 表达式）
- [x] 1.3 `TaskImpl` thenCompose 出口增加 SUSPEND 分支：置 `TASK_STATUS_SUSPENDED` + saveTaskState + 不 runCleanup/不 endTask（task 非终态，meter 不关闭；注释裁定）；红验证：HEAD 下挂起任务被置 COMPLETED
- [x] 1.4 `TaskStepHelper.retry`/`doRetry` 对 SUSPEND 跳过 `state.succeed`；catch 中真取消（CancellationException/NopTaskCancelledException）直接 rethrow 不计次不落 FAILED
- [x] 1.5 `SequentialTaskStep`/`SelectorTaskStep` 异步续延回调补 `isSuspend` 检查
- [x] 1.6 fork/parallel 挂起传播：`AbstractForkTaskStep.buildAggResult` 与 `ParallelTaskStep` 聚合前扫描已完成分支，命中 SUSPEND 即向上传播（同时删除 `executeFork` 死代码双 return）
- [x] 1.7 `GraphTaskStep.runStep` 成功分支挂起检查：不级联后继、complete(SUSPEND)、cancel 兄弟分支
- [x] 1.8 `TaskStepExecution:252` suspend 出口 metrics 判空（check2 P0-1）
- [x] 1.9 红验证：check2 P1 TaskImpl metrics 判空（工作区已有修复，还原后确认红）

Exit Criteria:

- [x] 新增测试类 `TestSuspendContract`（或分散至既有测试类）覆盖：值判断契约、TaskImpl 挂起驱动 SUSPENDED 且 resume 可续跑、BuildOutput+挂起、retry+挂起、Sequential/Selector 异步挂起、fork/parallel/graph 分支挂起传播、suspend 步骤默认 metrics 不 NPE；每条有红验证记录
- [x] `./mvnw test -pl nop-task/nop-task-core` 全绿
- [x] Owner doc：SUSPEND 语义变更写入 `docs-for-ai/03-modules/nop-task.md`

### Phase 2 - 状态码对齐 + 终态守卫（P0/P1）

Status: completed
Targets: `TaskConstants`、`TaskImpl.driveTask*`、`TaskStepStateBean.succeed`、`DefaultTaskStateStore.newMainStepState`、`ITaskState`

- Item Types: `Fix | Proof`

- [x] 2.1 `TaskConstants` 状态段对齐 ORM 字典（SUSPENDED=10、ACTIVE/ACTIVATED=30、COMPLETED=40、TIMEOUT/EXPIRED=50、FAILED=60、KILLED=70；step ACTIVE→ACTIVATED=30），注释标明字典映射
- [x] 2.2 新增一致性守卫测试：TaskConstants 与 `_NopTaskCoreConstants` 逐项相等、且等于字典定义值（红验证：HEAD 下 COMPLETED 30≠40）
- [x] 2.3 终态守卫：`TaskImpl.driveTask*` 先查 `taskState.isTerminal()`；`TaskStepStateBean.succeed`/`setStepStatus` 不覆写已终态
- [x] 2.4 `DefaultTaskStateStore.newMainStepState` 补 taskInstanceId/stepInstanceId
- [x] 2.5 `TASK_STATUS_HISTORY_BOUND` 死常量处置（确认无使用后删除）

Exit Criteria:

- [x] 一致性测试 + 终态覆写防护测试 + newMainStepState 测试绿；红验证记录
- [x] 既有测试（TestTaskKilledTimeoutResumeE2E 等）零回归（其断言引用常量自动跟随）
- [x] `./mvnw test -pl nop-task/nop-task-core` 全绿

### Phase 3 - 图模式可靠性（P1/P2）

Status: completed
Targets: `GraphTaskStep`、`GraphStepAnalyzer`、`TaskFlowAnalyzer`、`TaskErrors`

- Item Types: `Fix | Proof`

- [x] 3.1 waitError 成功语义：被等待步骤成功时等待按"跳过"完成（本节点不执行 body、stepFuture 正常完成级联后继）——修复 waitFuture 永久挂死
- [x] 3.2 错误边级联（check2 P1）：节点失败先 `stepFuture.completeExceptionally` 让 waitError/waitComplete 消费者触发；仅当该节点无任何错误消费者时才 fail-fast 整图；waitSuccess 等待者对上游失败按跳过传播（不炸整图）；保留 runningCount==0 兜底
- [x] 3.3 图内挂起传播（Phase 1.7 一并实现则勾选）
- [x] 3.4 `GraphStepAnalyzer` 构建期校验：重复步骤名抛新错误码 ERR_TASK_DUPLICATE_STEP_IN_GRAPH（i18n zh-CN/en 同步）
- [x] 3.5 `TaskFlowAnalyzer.forEachStep` if/then/else 改递归（孙级 normalize/checkStepRef/嵌套图分析可达；红验证：嵌套在 then 内的 graph 引用不存在的 wait 步骤在 HEAD 下不报错）
- [x] 3.6 新增图模式测试：错误边失败触发、错误边成功跳过、混合 wait、重复步骤名拒绝、嵌套 if 内图校验
- [x] 3.7 工作区未提交的 GraphTaskStep runningCount 修复补红验证记录（还原后确认 ERR_TASK_GRAPH_NO_ACTIVE_STEP 误报）

Exit Criteria:

- [x] 上述测试绿且红验证记录在案；`./mvnw test -pl nop-task/nop-task-core` 全绿
- [x] Owner doc：图模式错误边/跳过语义更新

### Phase 4 - wrapper 可靠性（P1）

Status: completed
Targets: `ExecutorTaskStepWrapper`、`TimeoutTaskStepWrapper`、`TaskStepHelper.timeout/retry`、`SleepTaskStep`、`TaskStepRuntimeImpl`

- Item Types: `Fix | Proof`

- [x] 4.1 ExecutorTaskStepWrapper：future 被取消时兜底 `ret.completeExceptionally(NopTaskCancelledException)`（红验证：排队期取消 HEAD 下任务挂死）
- [x] 4.2 timeout 竞速：超时触发时若 body promise 未完成则以 TIMEOUT 取消异常强制终结返回值（同步完成路径不受影响）；红验证：不检查 token 的异步 body 在 HEAD 下超时后仍挂死
- [x] 4.3 TimeoutTaskStepWrapper 完成后恢复外层 cancelToken（对照 withCancellable 模式）
- [x] 4.4 SleepTaskStep 取消后抛 NopTaskCancelledException（不再静默 CONTINUE）
- [x] 4.5 `TaskStepRuntimeImpl.cancelToken` 加 volatile
- [x] 4.6 retry 取消不计次（Phase 1.4 一并实现则勾选，此处补红验证记录）

Exit Criteria:

- [x] TestExecutorTaskStepWrapperAsyncBranch 扩展 + 新增 timeout/sleep 测试绿，红验证记录在案
- [x] `./mvnw test -pl nop-task/nop-task-core` 全绿

### Phase 5 - 边界守卫与运行时修复（P2/P3）

Status: completed
Targets: `ForkTaskStep`/`ForkNTaskStep`/`LoopNTaskStep`/`ParallelTaskStep`、`MultiStepResultBean`、`TaskStepReturn.of`、`TaskStepExecution`（nextOnError reader）、`TaskFlowManagerImpl`、`TaskRuntimeImpl.newChildRuntime`、`TaskStepHelper`

- Item Types: `Fix | Proof`

- [x] 5.1 ForkTaskStep producer null → 空集合（不 NPE）；ForkNTaskStep count<=0 → 空聚合（不抛 IAE）
- [x] 5.2 LoopNTaskStep 同步/异步 until 语义统一为 CONTINUE（异步 END 为错）；exit 分支保留 outputs（对齐 LoopTaskStep）
- [x] 5.3 ParallelTaskStep 聚合对未完成分支补 CANCELLED 占位（对齐 fork 族）
- [x] 5.4 MultiStepResultBean.setStepResultBeanMap 改替换语义
- [x] 5.5 `TaskStepReturn.of`：显式 nextStepName 非 null 时优先生效（确认调用面仅 End/Exit 哨兵后实施）
- [x] 5.6 continuation-skip 失败重抛改造：配置了 nextOnError 的步骤 resume 命中 FAILED 终态时返回 buildErrorResult（错误分支可再入），未配置的维持重抛
- [x] 5.7 `TaskFlowManagerImpl.getTaskRuntime`（resume 路径）初始化 TaskFlowMetricsImpl（check2 P2）
- [x] 5.8 `TaskRuntimeImpl.newChildRuntime` 取消传播自引用改 `taskRt::cancel`（红验证：svcCtx=null 时父取消不传播）
- [x] 5.9 `resetGlobalStats` 补 globalRateLimiters；全局限流器参数不一致时 LOG.warn
- [x] 5.10 nextOnError 红验证：HEAD 下含 nextOnError 的失败步骤 resume 被重抛拦截

Exit Criteria:

- [x] 各项测试绿 + 红验证记录；`./mvnw test -pl nop-task/nop-task-core` 全绿

### Phase 6 - xdef 修正 + 恢复完整性持久化（P0/P1/P2）

Status: completed
Targets: `task.xdef`、`nop-task-core` `_gen`（xgen 再生）、`TaskStepBuilder`、`DaoTaskStateStore`、`ITaskStepState`/`TaskStepStateBean`、`nop-task-dao` beans、nop-task-ext round-trip 测试

- Item Types: `Fix | Proof | Decision`

- [x] 6.1 task.xdef：`persisVars`→`persistVars`（红验证：HEAD 下模型字段为 _persisVars）；retry 注释"重试整个task"改为"重试本步骤"；input/output persist 默认值补注释说明；`./mvnw generate-sources -pl nop-task/nop-task-core` 官方再生 `_gen`（禁止手改）
- [x] 6.2 persistVars 接线：`TaskStepBuilder.initAbstractStep` 设置 `step.setPersistVars(stepModel.getPersistVars())`；声明 persistVars 的步骤在 saveState 时捕获 scope 变量、load 后回写（经 6.3 wrapper）
- [x] 6.3 恢复完整性（最小闭环，不动 ORM）：`DaoTaskStateStore` step 状态序列化改版本化 wrapper（resultValue/stateBean/outputs/nextStepName/persistVars 同列存储，旧格式裸 resultValue 向后兼容读回）；loop/fork/if/choose/suspend/call-task 的 stateBean 恢复生效；红验证：HEAD 下 loop 恢复从 index=0 重跑
- [x] 6.4 continuation-skip 重放 outputs：持久化 outputs map 经 `ITaskStepState` 新 default 访问器恢复，skip 路径调用 initOutputs 重放导出变量（check/344 暂缓项的最小闭环）
- [x] 6.5 DaoTaskStateStore 残留列清理：resultValue 为 null 时显式清空列
- [x] 6.6 DaoTaskStateStore 条件 bean 注册（nop-task-dao beans.xml，镜像 task-ext on-class 模式）——闭合 check2 P3"开箱即抛 ERR_TASK_NO_PERSIST_STATE_STORE"装配缺口
- [x] 6.7 task 级恢复（request/taskVars）经可用实体列持久化（若 NopTaskInstance 无可用列则裁定暂缓并记录，不新增 ORM 列）
- [x] 6.8 nop-task-ext round-trip 测试扩展（stateBean/outputs round-trip + 旧格式兼容）

Exit Criteria:

- [x] xgen 再生后 diff 仅为预期字段改名；全模块编译通过
- [x] 新增 round-trip/loop 恢复/persistVars 测试绿 + 红验证记录
- [x] `./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-dao,nop-task/nop-task-ext` 全绿
- [x] Owner doc 更新（恢复语义边界：fork 分支行竞争仍暂缓的说明）

### Phase 7 - 收口

Status: completed
Targets: 全量测试、check2 报告标注、daily log、doc link checker、closure audit

- Item Types: `Proof`

Exit Criteria:

- [x] 收口验证全部完成（见 Closure 段证据）

- [x] 7.1 `./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-dao,nop-task/nop-task-ext,nop-task/nop-task-service` 全绿（126+1+117；closure audit 复跑 244+1/0 fail；audit 修正后 core+ext 复跑全绿）
- [x] 7.2 check2 `nop-task.md` 19 条逐条回写处置标注（已修复/复查已修复/暂缓/不修复）
- [x] 7.3 `ai-dev/logs/2026/09-05.md` 收口条目
- [x] 7.4 `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 7.5 独立子 agent closure audit（本会话外新 task）+ 证据写入本 plan Closure 段

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复或移入 Deferred But Adjudicated（附理由）
- [x] 每条修复有红验证记录（测试先红后绿）
- [x] `./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-dao,nop-task/nop-task-ext -am` 全绿
- [x] Owner docs（docs-for-ai/03-modules/nop-task.md）与 live baseline 一致
- [x] 独立 closure audit 完成并记录证据（含 Anti-Hollow 检查）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

### fork/forkN 分支同 stepPath 并发写同一 DB 行（check2 P1）

- Classification: `out-of-scope improvement`（需 ORM 唯一索引/行标识设计，属"DB 断点续跑完整性"主题）
- Why Not Blocking Closure: 需要 ORM 结构变更（保护区 plan-first）；单进程内并发由 store 语义兜底，跨进程 fork+resume 场景在 owner doc 明示不支持
- Successor Required: yes
- Successor Path: DB 断点续跑完整性 design（待立项）

### TransactionalTaskStepWrapper 副作用提交与终态保存非原子（分析报告 P1-N9）

- Classification: `watch-only residual`（需"终态 save 纳入业务事务"的跨层设计）
- Why Not Blocking Closure: 步骤体幂等可规避；文档明示硬契约
- Successor Required: yes
- Successor Path: DB 断点续跑完整性 design

### task 级 taskVars 持久化（plan 6.7 余量）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: NopTaskInstance 实体无 taskVars 对应列，持久化需新增 ORM 列（保护区 plan-first）；task 输入（request）已在本 plan 经既有 taskInputs 列持久化（closure audit 指正后实现），taskVars 余量留待恢复完整性立项一并设计
- Successor Required: yes
- Successor Path: DB 断点续跑完整性 design

### GraphStepAnalyzer 重名步骤校验的测试可达性（plan 3.4/3.6 余量）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 校验代码已落地（防御 ERR_TASK_DUPLICATE_STEP_IN_GRAPH），但 xdef key-attr 解析去重与 KeyedList.addAllUnique 使重复步骤名状态在两条构造路径（XML/程序化）下均不可达，红绿测试无法构造触发；保留校验作为绕过模型层去重的纵深防御
- Successor Required: no

### task.xdef 语法清理（role/roles 统一、deprecated 标签迁移、作用域速查表）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 无行为缺陷；role/roles 无消费者，重命名纯 churn；DSL 迁移需单独兼容策略
- Successor Required: no

### service 层生命周期 API / 可视化 / queue 空壳

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 产品化功能缺口，非行为缺陷；分析报告已给路线
- Successor Required: no

## Closure

Status Note: 分析报告确认的全部可修复缺陷（P0×4/P1×9/P2×13/P3×5 中除 3 项诚实暂缓外）已按"红测试→修复→绿"收口：SUSPEND 契约化（8 处破坏点）、状态码对齐字典、终态守卫、图模式错误边双向语义+挂死修复、wrapper 取消/超时终结不变量、恢复完整性最小闭环（stateBean/outputs/nextStepName/persistVars 经版本化 wrapper 持久化 + skip 重放 + taskInputs 列持久化）、xdef persistVars 拼写修正并官方再生。26+8 个新测试 stash 还原法 26/26 验红；四模块 126+1+117 全绿；check2 19 条全部标注；owner doc 三节更新。

Completed: 2026-09-05

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general-purpose，agentId: agent_b2831c96-c11a-43e3-9591-af85e690867a，全新会话未参与实现）
- Evidence:
  - Phase 1-6 共 40 个执行项：38 项 live code 逐条核对 PASS（文件:行号级证据见审计报告）；2 项（5.9 告警/6.7 输入持久化）经 audit 指正后当场补实现并复核
  - 测试断言判别性抽查（5 用例细读）：非恒真断言，注释均写明修复前失败形态
  - Anti-Hollow：(a) persistVars 端到端链路 xdef→_gen→builder→runtime→state→store→DB→load→restore 全环连通（文件:行号追踪）；(b) `scan-hollow-implementations.mjs --module nop-task --severity high` 退出码 0；(c) 新增/修改代码无空方法体/吞异常/TODO-as-done
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0；`check-doc-links.mjs --strict` 退出码 0
  - 测试：审计者实测 `./mvnw test -pl nop-task-core,nop-task-dao,nop-task-ext -am` 244/0 + service 1/0；audit 修正后主会话复跑 core+ext 全绿（27+41）
  - Deferred 分类复核：fork stepPath（需 ORM 索引）/事务原子性（需跨层设计）诚实；audit 指出 5.9/6.7 两处暂缓理由有误——已按指正实现（5.9 告警、6.7 taskInputs 持久化）并修正记录，余量（taskVars 无列）以真实理由移入 Deferred
  - Audit Minor 项处置：4 处过期状态码注释已修；suspend-parallel 专属 fixture 用例已补；3.6 重名校验测试不可达已诚实改写为本 Deferred 条目
  - 代码提交：nop-task 全模块 + nop-xdefs/task.xdef + _gen 再生 + docs/ai-dev 变更按工作区 Git Workflow 规则提交（见当日 git log）

Follow-up:

- nop-task-service 生命周期 API（start/resume/kill）与可视化（产品化路线，见分析报告第五节）
- 「DB 断点续跑完整性」design 立项：fork 分支行标识 + 唯一索引 + 事务原子性 + taskVars 列（本 plan Deferred 三项的统一承接）
