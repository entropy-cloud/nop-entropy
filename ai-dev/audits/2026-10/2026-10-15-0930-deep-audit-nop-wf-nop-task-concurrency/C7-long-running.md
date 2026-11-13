# C7 长时运行稳定性维度初审（盲审）— nop-wf + nop-task

- 审计单元：`nop-wf`（10 jar 子模块）+ `nop-task`（10 jar 子模块）
- 审计人：并发审计子代理（C7 维度，第 1 轮盲审）
- 日期：2026-10-15
- 仓库根：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation）
- 盲审纪律：未读取 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何文件；仅依据 live code + owner docs（docs-for-ai/03-modules/nop-wf.md、nop-task.md、02-core-guides/workflow-configuration.md）+ grep 基线 `_tmp/cra-baseline-nop-wf-nop-task.txt`。

## 检查范围说明

C7 维度六个检查项：

1. 单调增长盘点（持久化账本 / 内存历史累积 / 日志放大）
2. 挂死可检测性 F5（永久阻塞点健康可见性、滞留 RUNNING/SUSPENDED 的看门狗覆盖面）
3. 重试风暴（wf `<retry>` / task retry 的退避+上限；重试放大下游故障）
4. 自愈语义 F2/F10（重启恢复路径的状态清理；多节点 fencing：WfTaskScanner 双实例、TaskFlowManagerImpl 双实例）
5. 时钟与期限（dueTime/timeout 的时钟敏感性；epoch/generation 溢出）
6. 每次调用都增长的小对象

**重点通读文件**（live code，排除 `_` 前缀生成文件）：

- nop-wf：`WfTaskScanner`、`WorkflowEngineImpl`（1949 行全读）、`WorkflowImpl`、`WorkflowManagerImpl`、`WorkflowCoordinatorImpl`、`DefaultWorkflowExecutor`、`WfRuntime`、`WorkflowServiceImpl`、`DaoWorkflowStore`、`app-scheduler.beans.xml`、`wf-due-task-scan.job.yaml`、`wf-remind-task-scan.job.yaml`
- nop-task：`TaskFlowManagerImpl`、`TaskRuntimeImpl`、`TaskStepRuntimeImpl`、`TaskImpl`、`TaskStepExecution`、`TaskStepHelper`（timeout/retry 核心）、`DaoTaskStateStore`（1004 行全读）、`TaskExceptionRegistry`、`TaskConfigs`、`RetryPolicy`（nop-commons，设施使用面）、`LoopTaskStep`、`ParallelTaskStep`、`AbstractForkTaskStep`、`SleepTaskStep`、`DelayTaskStep`、`SuspendTaskStep`、`RetryTaskStepWrapper`、`TimeoutTaskStepWrapper`、`RateLimitTaskStepWrapper`、`ThrottleTaskStepWrapper`、`TaskFlowMetricsImpl`、`RateLimitTaskStepDecorator`、`NopTaskInstanceBizModel`
- 平台设施（DefaultRateLimiter/DefaultSemaphore/CoreMetrics/FutureHelper）只审使用方式。

**13 字段格式说明**：每个发现条目含：①发现 ID ②严重度 ③缺陷族/维度项 ④位置 ⑤证据片段 ⑥增长/劣化链（时间尺度×增长源×后果） ⑦影响 ⑧判定依据 ⑨误报筛查 ⑩已声明边界对照 ⑪修复方向 ⑫检测可见性 ⑬覆盖备注。

---

## 单调增长源清单 + 挂死点清单

### 单调增长源清单

| # | 增长源 | 位置 | 类型 | 边界状态 |
|---|--------|------|------|----------|
| G1 | `nop_wf_instance` / `nop_wf_step_instance` / `nop_wf_action` / `nop_wf_status_history` / `nop_wf_log` 行只增不删（引擎无任何 purge/TTL/归档路径） | `DaoWorkflowStore`（全部写方法）、`NopWfInstance.addStatusHistory`（`nop-wf-dao/.../entity/NopWfInstance.java:40-49`） | DB 账本（F4） | DB 约束，设计即审计历史；无配套清理 job（见 C7-007） |
| G2 | `nop_task_instance` / `nop_task_step_instance` 行只增不删（每任务执行 1 行 task + 每 stepPath 1 行 step，errorBeanData/errorStack/stateBeanData 诊断列长期留存） | `DaoTaskStateStore.newTaskState:132-143`、`saveStepState:355-374` | DB 账本（F4） | DB 约束；owner doc 声明错误诊断列持久化为既定设计（见 C7-007） |
| G3 | `globalRateLimiters` / `globalSemaphores` 强引用注册表，永不驱逐；key=`taskName:keyExpr求值值`，动态 key 基数不受模型静态结构约束 | `TaskFlowManagerImpl.java:63-64,175-207` | 内存 Map | **已声明边界**（plan 364 [05-04]，owner doc 同文）；告警为一次性 WARN（见 C7-008） |
| G4 | 到期扫描失败步骤滞留 ACTIVATED：每扫描周期被重新加载、重新执行、重新 ERROR 记日志 | `WfTaskScanner.scanDueTasksInCurrentSession:78-120` + `DaoWorkflowStore.findDueActivatedSteps:181-186` | 扫描周期级放大（日志+DB） | 无失败计数/无 dead-letter/无抑制（见 C7-002） |
| G5 | metrics label 基数：`TaskFlowMetricsImpl` 仅 task.name/task.version/status 三维，**stepPath 刻意不入标签** | `TaskFlowMetricsImpl.java:28-48,71-73` | Micrometer 注册表 | **有界**（任务名×版本×2状态×2 meter），正向确认，非发现 |
| G6 | `WorkflowImpl.steps` HashMap（stepId→impl 惰性累积）与 `WfRuntime`/`TaskRuntimeImpl.attrs` 等每次调用对象 | `WorkflowImpl.java:41,190-198`、`TaskRuntimeImpl.java:36` | 每调用生命周期 | WorkflowImpl 每次经 `WorkflowManagerImpl.getWorkflow` 新建（`WorkflowManagerImpl.java:81-88`），调用结束即可 GC；**有界**，非发现 |
| G7 | task 级指标 `Timer.Sample`（挂起时不关闭 meter） | `TaskImpl.java:153-161`（注释明示"挂起任务 meter 遗留量级有限，可接受"） | 每挂起任务 1 个 sample | 挂起任务被 resume/终态驱动时释放；量级=并存挂起任务数，可接受，非独立发现 |

### 挂死点清单

| # | 挂死/滞留点 | 位置 | 可检测性 |
|---|------------|------|----------|
| H1 | dueAction source 同步阻塞（syncGet 无超时，扫描线程滞留） | `WfTaskScanner.java:96`（`FutureHelper.syncGet(...)`，实际同步执行于 job 线程） | 不可见（无扫描心跳/线程堆栈健康项）；pool-size 4 共享调度器可级联（见 C7-004） |
| H2 | 步骤 ACTIVATED 且 dueTime 已过但无 dueAction / 步骤名不在模型中 | `WfTaskScanner.java:85-89`（LOG.debug 跳过） | 默认日志级别不可见；扫描 job 默认 `enabled:false` 时连此路径都不运行（见 C7-003） |
| H3 | 同步 step body 无视 cancel token 无限循环：timeout 只取消 token/完成兜底 promise，不能中断线程 | `TaskStepHelper.timeout:184-244` + `TimeoutTaskStepWrapper.java:20-29` | task 滞留 ACTIVE(30)，无 watchdog（见 C7-011） |
| H4 | `<sleep>` 同步阻塞调用线程（FutureHelper.waitUntil 100ms 轮询） | `SleepTaskStep.java:38` | 线程存活可见（非挂死），但 sleep 时长无构建期上限校验（见 C7-012） |
| H5 | JVM 崩溃/重启后持久化任务滞留 ACTIVE：无恢复扫描、workerId 从不赋值 | `DaoTaskStateStore`（workerId 仅 round-trip:452,505，全仓无生产赋值点） | DB 可查但引擎无看门狗（见 C7-009） |
| H6 | SUSPENDED 任务（含 resume-when 条件已满足者）不自动续跑，只能显式 resume | `SuspendTaskStep.java:28-43`（resumeWhen 仅在 resume 调用链上求值） | "等人工"为声明语义；无监控区分"待人工"与"遗弃"（见 C7-010） |
| H7 | wf join 步骤滞留 WAITING（上游步骤被 skip/异常退出未驱动 join 检查） | `WorkflowEngineImpl.checkWaitingJoinStep:491-520`（仅在上游迁移/signal 时被 delayExecute 触发） | 无扫描兜底；流程级无 dueTime 即无扫描面。登记为残留边界（未立案：触发需要模型配置缺陷组合，未能具体化稳定链） |

---

## 发现条目

### [nop-wf-nop-task-C7-001] WfTaskScanner 多节点部署无 fencing：dueAction 与 remind 双实例重复触发

1. **发现 ID**：nop-wf-nop-task-C7-001
2. **严重度**：P1（罕见但可具体化竞态）
3. **缺陷族/维度项**：C7-4 自愈语义/fencing（F2 半个成功近邻）
4. **位置**：`nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java:78-143`；job 定义 `nop-wf/nop-wf-scheduler/src/main/resources/_vfs/nop/job/conf/wf-due-task-scan.job.yaml`、`wf-remind-task-scan.job.yaml`；调度器 `nop-job/.../app-local-scheduler.beans.xml`（LocalJobScheduler，节点本地、无集群锁）
5. **证据片段**：
```java
// WfTaskScanner.java:78-96
void scanDueTasksInCurrentSession() {
    for (IWorkflowStepRecord stepRecord : workflowStore.findDueActivatedSteps()) {
        ...
        FutureHelper.syncGet(workflowService.invokeActionAsync(request, null, newSchedulerContext()));
```
```java
// WfTaskScanner.java:122-135（remind：listener 副作用 + remindCount read-modify-write）
IWorkflowStep step = workflowManager.getWorkflow(...).getStepById(...);
reminderListeners.forEach(listener -> listener.onRemind(step));
Integer remindCount = stepRecord.getRemindCount();
...
stepRecord.setRemindCount(remindCount + 1);
stepRecord.setRemindTime(null);
workflowStore.saveStepRecord(stepRecord);
```
6. **增长/劣化链**：部署形态（每节点运行同一份 enabled job yaml；LocalJobScheduler 为节点本地调度、本单元内无 leader 选举/分布式锁接入）× 触发窗口（cron `0 0/1 * * * ?` 双节点同秒触发）× 后果：两节点同时读同一步骤 ACTIVATED 记录 → 各自 `invokeAction` → checkAllowedAction 状态检查（无行锁）双双通过 → **action source 在两节点各执行一次**（escalation/通知类副作用翻倍）；随后 doTransition 落库时后到者才被状态守卫（ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS）或乐观锁拒绝——守卫发生在副作用之后。remind 路径：listener（如发提醒）双发 + remindCount 丢失更新（双会话各读 0 各写 1）。时间尺度：每次到期触发即可能发生；每周级运行下必然遇到并发窗口。
7. **影响**：超时升级/提醒类动作重复执行（重复外部调用、重复通知）；remindCount 统计失真；错误侧仅表现为每分钟一条 ERROR 日志，无聚合告警。
8. **判定依据**：scanDueTasksInCurrentSession 对"读到 ACTIVATED → 执行动作 → 状态迁移"三段无原子化（无 SELECT FOR UPDATE、无条件更新抢占、无节点身份标识）； due 扫描与 remind 扫描均无节点过滤条件；`newSchedulerContext()` 无 nodeId。
9. **误报筛查**：已排除——status 竞态 skip（:98-103）只证明"后到者会被拒绝"，不阻止 source 双跑；nop-cluster 模块存在但 nop-wf-scheduler 的 beans/job yaml 未接入任何集群锁；本条不涉低频粗粒度锁豁免。
10. **已声明边界对照**：workflow-configuration.md 仅声明 dueAction 开启前置（job 装配 + dueAction 配置），未声明多节点扫描语义；owner docs 无 fencing 声明。**未被声明覆盖**。
11. **修复方向**：扫描抢占（条件 UPDATE 把 due 步骤置为"处理中+owner+期限"）或接入分布式调度锁；或 dueAction/remind 触发统一经幂等键（wfId+stepId+dueTime）去重。
12. **检测可见性**：失败侧（后到者）每周期 ERROR 可见；成功侧双跑**完全静默**（业务副作用层面不可见）。
13. **覆盖备注**：与本文件 C7-003（job 默认 disabled）互补：单节点部署不触发；`enabled:true` + 多节点即进入本条暴露面。
14. **复核状态**： 已保留（P1——从严验证成立：①部署形态，`app-scheduler.beans.xml:7` 引入节点本地 LocalJobScheduler，nop-wf 全部 pom 无 nop-cluster 依赖，两个扫描 job yaml 无节点过滤/分布式锁/leader 选举入口，owner docs 无"单节点排程"声明；②副作用先于守卫——`WorkflowEngineImpl.doInvokeAction` :1255 `runXpl(actionModel.getSource())` 先于 :1280 `doTransition`，`checkAllowedAction`（:1740-1801）为对已加载记录的纯内存判定（`isActivated()`），无行锁/无条件更新抢占，双节点同秒 cron 双双通过后 source 各执行一次，后到者仅在迁移落库时被拒；③remindCount 读-改-写丢失更新（:128-135）证实。现状：新发现。cross-ref：同单元 C1-005 为同一 WfTaskScanner 扫描面的共享状态视角（P2，C1 复核已保留）——同面判级差异（业务副作用视角 P1 vs RMW 视角 P2）留 7.1 归并）

### [nop-wf-nop-task-C7-002] due/remind 扫描对持续失败项无抑制：每周期重放 ERROR + 无界重扫滞留 ACTIVATED 账本

1. **发现 ID**：nop-wf-nop-task-C7-002
2. **严重度**：P2（日志放大 + 监控盲区；DB 滞留受 DB 约束单独标注）
3. **缺陷族/维度项**：C7-1 日志放大 / F9 近邻（错误可见但无收敛）/ F4 滞留账本
4. **位置**：`nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java:96-118`（due 失败分支）、`:136-141`（remind 失败分支注释"下一轮扫描可重试"）；`nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/store/DaoWorkflowStore.java:181-186`（findDueActivatedSteps 无 limit/分页）
5. **证据片段**：
```java
// WfTaskScanner.java:112-118
} catch (NopException e) {
    ...
    LOG.error("nop.wf.scheduler.due-action-failed:wfId={},stepId={},dueAction={}",
            stepRecord.getWfId(), stepRecord.getStepId(), dueAction, e);
} catch (Exception e) {
    // 非NopException的运行时异常同样只影响单条任务
    LOG.error("nop.wf.scheduler.due-action-failed:...", ..., e);
}
```
```java
// DaoWorkflowStore.java:181-186
public List<? extends IWorkflowStepRecord> findDueActivatedSteps() {
    QueryBean query = new QueryBean();
    query.addFilter(FilterBeans.eq(...PROP_NAME_status, WF_STEP_STATUS_ACTIVATED));
    query.addFilter(FilterBeans.le(...PROP_NAME_dueTime, CoreMetrics.currentTimestamp()));
    return stepDao().findAllByQuery(query);   // 无 limit/分页/失败计数过滤
}
```
6. **增长/劣化链**：增长源=确定性失败的 due 步骤（dueAction XPL 引用已删除 bean、目标服务持续 5xx、模型与实例版本漂移）× 时间尺度=每扫描周期（due job 缺省 cron 1 分钟）× 后果：(a) 每周期对每个滞留步骤输出一条带完整堆栈的 ERROR——日志量 = 滞留步骤数 × 1440 次/天，无抑制、无失败计数升级、无 dead-letter；(b) 滞留步骤永远留在 `findDueActivatedSteps` 结果集中，扫描耗时随滞留数线性增长（查询无 limit）；(c) 周期内重复执行失败 action 的前置副作用（source 执行到一半抛错的部分写库在事务内回滚，但外部副作用不回滚——每周期重放）。remind 失败同理（remindTime 未清空 → 每 5 分钟周期重试+ERROR）。
7. **影响**：天/周级运行下日志噪声淹没真实错误（log rotation 压力）；扫描 job 耗时漂移；重复外部副作用。
8. **判定依据**：代码中失败后不改步骤状态、不记失败次数、不加退避；查询无上限；无抑制性元数据（如 nextAttemptTime）。
9. **误报筛查**：单条任务失败隔离本身是正确设计（不中断整批）；本条针对"失败后无收敛机制"，非批评隔离本身。不属信息性日志豁免（ERROR+堆栈 × 周期 × 无界时长）。
10. **已声明边界对照**：workflow-configuration.md 声明了 dueAction 的开启前置，未声明失败重试/抑制语义。未覆盖。
11. **修复方向**：步骤记录增加失败计数/下次重试时间列，指数退避重扫；连续 N 次失败后升级为终态或告警态；扫描查询加 limit 分页。
12. **检测可见性**：错误本身可见（ERROR），但无聚合指标（滞留 due 步骤数、失败次数）——运维只能靠翻日志发现累积。
13. **覆盖备注**：F4 维度：滞留 ACTIVATED 行属 DB 侧账本，受 DB 约束，此处标注不另立 F4 条目。
14. **复核状态**： 已保留（P2——失败分支仅 LOG.error、不改步骤状态、无失败计数/退避/dead-letter（WfTaskScanner:97-118，remind 侧 :136-141 注释自认"下一轮扫描可重试"）；`findDueActivatedSteps` 无 limit/分页（DaoWorkflowStore:181-186）逐行证实；due cron 缺省 1 分钟、remind 5 分钟（job yaml）放大链成立。现状：新发现）

### [nop-wf-nop-task-C7-003] 滞留 ACTIVATED 步骤的看门狗双重失效：dueAction 缺失 DEBUG 跳过 + 扫描 job 默认 disabled

1. **发现 ID**：nop-wf-nop-task-C7-003
2. **严重度**：P2（监控盲区/边界配置缺陷）
3. **缺陷族/维度项**：C7-2 挂死可检测性（F5：滞留状态不产出也不报错）
4. **位置**：`nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java:83-89`；`nop-wf/nop-wf-scheduler/src/main/resources/_vfs/nop/job/conf/wf-due-task-scan.job.yaml:2`（`enabled: "@cfg:nop.job.wf-due-task-scan.enabled|false"`）
5. **证据片段**：
```java
// WfTaskScanner.java:83-89
String dueAction = null;
try {
    dueAction = getDueAction(stepRecord);
    if (dueAction == null || dueAction.isBlank()) {
        LOG.debug("nop.wf.scheduler.skip-due-task-without-due-action:wfId={},stepId={}",
                stepRecord.getWfId(), stepRecord.getStepId());
        continue;
    }
```
```yaml
# wf-due-task-scan.job.yaml
jobName: wf-due-task-scan
enabled: "@cfg:nop.job.wf-due-task-scan.enabled|false"
```
6. **增长/劣化链**：步骤模型配置了 `dueTimeExpr` 但未配 `dueAction`（或流程实例引用的模型版本中该步骤已更名 → `getDueAction` 返回 null）× 时间尺度=步骤到期后无限期 × 后果：该步骤每周期被查出→DEBUG 跳过→仍留 ACTIVATED，流程实例永不停转；INFO/WARN 级零痕迹。叠加第二重失效：两个扫描 job 缺省 `enabled:false`（workflow-configuration.md:474 声明 job 需 host app 装配），缺省部署下超时升级/提醒机制整体静默不运行——滞留面连 DEBUG 日志都没有，仅 DB 中 `status=ACTIVATED AND dueTime<now` 行持续累积。
7. **影响**：审批流"到期升级"承诺在两类配置缺陷下退化为永久滞留；无健康指标暴露 due-backlog 规模（无 gauge/meter）。
8. **判定依据**：skip 分支日志级别为 DEBUG（生产常规级别不可见）；全模块无任何"滞留 due 步骤计数"指标或状态上报；xdef/文档未强制 dueTime 与 dueAction 成对出现。
9. **误报筛查**：不属"已声明边界"豁免——文档声明的是"job 需装配"这一开启前置，未声明"dueTime 无 dueAction 时静默滞留"这一行为后果；亦不属平台设施自身实现。
10. **已声明边界对照**：部分声明（job 装配前置已文档化）；"无 dueAction 的 dueTime 滞留"未声明。
11. **修复方向**：①skip 分支升 WARN（或按 stepId 去重的 WARN）；②提供 due-backlog gauge；③模型加载期校验 dueTimeExpr 与 dueAction 成对。
12. **检测可见性**：DB 层可人工查询；引擎运行时零可见。
13. **覆盖备注**：与 C7-002 同一扫描面；本条聚焦"零升级路径"，C7-002 聚焦"失败无抑制"。
14. **复核状态**： 已保留（P2——DEBUG 跳过（:85-89）与双 job 缺省 `enabled:false`（yaml:2）证实。复核补充一处机制精化：纯缺 dueAction 的步骤因 WorkflowEngineImpl:389 门控（`dueAction != null` 才落 dueTime）根本不入 `findDueActivatedSteps` 结果集（`le(dueTime,…)` 对 NULL 不匹配）——该子案的盲区比初审描述更彻底（连 DEBUG 都没有），不削弱结论；初审并列的"模型漂移"子案（dueTime 已落库、`getDueAction` 返回 null）机制逐字成立。现状：新发现）

### [nop-wf-nop-task-C7-004] 看门狗自身无超时防护：dueAction 同步阻塞可挂死扫描线程并级联耗尽共享调度池

1. **发现 ID**：nop-wf-nop-task-C7-004
2. **严重度**：P2（F5：挂死不可见；需特定 action 配置触发）
3. **缺陷族/维度项**：C7-2 挂死可检测性（sync 等待、锁外 I/O 于看门狗线程）
4. **位置**：`nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java:96`；`nop-job/.../app-local-scheduler.beans.xml`（`nopJobScheduledExecutor` pool-size 缺省 4，供全部本地 job 共享）
5. **证据片段**：
```java
// WfTaskScanner.java:96 —— 同步等待 action 完成；DefaultWorkflowExecutor.execute 为同步执行，
// syncGet 实际等价于在 job 线程内联调用 action source
FutureHelper.syncGet(workflowService.invokeActionAsync(request, null, newSchedulerContext()));
```
6. **增长/劣化链**：dueAction source 内联调用阻塞型外部端点（HTTP 客户端未配超时 / DB 锁等待）× 时间尺度=该调用阻塞的时长（可无限）× 后果：wf-due-task-scan 的 job 线程永久滞留；下一轮 cron 触发继续占用新线程；调度器 pool-size=4 为 nop-job 全部本地 job 共享——4 个滞留的 due action 即冻结整个本地调度器（含 remind 扫描与 host 侧其他 job），且无任何"扫描已停止/线程阻塞于 X"的健康上报。
7. **影响**：超时升级面整体静默失效（回到 C7-003 的滞留世界），且拖垮同调度器上的无关 job；重启前不可自愈。
8. **判定依据**：scan 循环对单步动作无超时包装（对比：nop-task 的 `TaskStepHelper.timeout` 有兜底 promise；wf 侧无对应物）；`FutureHelper.syncGet` 无 deadline 参数调用。
9. **误报筛查**：不属平台设施自身实现缺陷（平台提供了 timeout 设施，wf 扫描面未使用）；"低频路径粗粒度锁"豁免不适用（这是无界阻塞，非有界锁竞争）。
10. **已声明边界对照**：未声明。
11. **修复方向**：dueAction 调用加整体 deadline（如 `syncGet` 换带超时版本）；或扫描任务改异步投递+队列；扫描线程加"单轮最大时长"熔断日志。
12. **检测可见性**：完全不可见（无扫描心跳；线程 dump 是唯一手段）。
13. **覆盖备注**：触发前提是 action source 存在无界阻塞调用——平台多数 HTTP/GraphQL 客户端可配超时，但引擎不强制，属引擎面缺口而非确定性缺陷，故 P2。
14. **复核状态**： 已保留（P2——:96 `FutureHelper.syncGet` 单参调用无 deadline；`invokeActionAsync` 返回的 stage 在 `DefaultWorkflowExecutor.execute` 内已同步完成，动作确在 job 线程内联执行；`nopJobScheduledExecutor` pool-size 缺省 4（app-local-scheduler.beans.xml:13-14）供全部本地 job 共享、级联耗尽链成立；wf 扫描面对平台 timeout 设施零使用（对照 task 侧 TaskStepHelper.timeout 存在）。现状：新发现）

### [nop-wf-nop-task-C7-005] wf `<retry>` 以 Thread.sleep 阻塞调用线程并持有事务；exponential 退避缺省封顶为 0（不封顶）

1. **发现 ID**：nop-wf-nop-task-C7-005
2. **严重度**：P2（有界放大但同步阻塞；配置敏感）
3. **缺陷族/维度项**：C7-3 重试风暴（退避+上限核查）
4. **位置**：`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:876-905`（runSourceWithRetry）、`:922-933`（computeRetryDelay）；触发面 `DefaultWorkflowExecutor.java:46-59`（每次工作流变更后 runAutoTransitionsWithLimit → runStepAutoTransition → runSourceWithRetry）
5. **证据片段**：
```java
// WorkflowEngineImpl.java:888-899
if (attempt >= maxRetryCount || !isRetryableException(e, retry, wfRt))
    throw e;
long delay = computeRetryDelay(retry, attempt);
if (delay > 0) {
    try {
        Thread.sleep(delay);          // 同步阻塞：持有请求线程 + 当前事务
    } catch (InterruptedException ie) { ... }
```
```java
// WorkflowEngineImpl.java:926-931 —— maxRetryDelay=0 时不封顶
if (retry.isExponentialDelay()) {
    delay = delay * (1L << Math.min(attempt, 30));
    int maxRetryDelay = retry.getMaxRetryDelay();
    if (maxRetryDelay > 0)
        delay = Math.min(delay, maxRetryDelay);
}
```
6. **增长/劣化链**：步骤 source 抛可重试异常 × 每次退避 Thread.sleep 在调用线程内联 × 触发面广（任意 step action / dueAction / autoTransition 都会执行 activated 步骤的 source+retry）× 后果：`retryDelay=10s, maxRetryCount=5, maxRetryDelay=0`（xdef 允许）时单次调用内累计阻塞 10+20+40+80+160s≈5 分钟，期间数据库事务悬挂（连接池占用、行锁滞留、undo 增长）；并发 N 个此类步骤 → N 个事务同时长挂。与 nop-task 的 RetryTaskStepWrapper 对照：task 侧延迟经 `scheduledExecutor.schedule`（`TaskStepHelper.java:262-285`）异步等待不占线程，wf 侧为同步睡眠。
7. **影响**：请求线程 + DB 连接 + 事务资源被退避时间占用；下游故障（如 DB 抖动）时重试退避反而延长事务持有时间，放大故障。
8. **判定依据**：上限存在（maxRetryCount 构建期校验非负、attempt 封顶 shift 30），无重试风暴失控；但 backoff 策略为同步 sleep，且 maxRetryDelay=0 语义为"不封顶"（代码注释自认），指数无默认上限。
9. **误报筛查**：非无界（重试次数有限），故不判 P1；非测试专用路径（autoTransition 是每次 action 的标准后置）。
10. **已声明边界对照**：xdef 注释（javadoc :871-875）声明了 maxRetryDelay=0 不封顶语义——半声明；但其与"事务内 sleep"的叠加后果未声明。
11. **修复方向**：与 task 引擎对齐，改定时器异步退避或要求 maxRetryDelay 必配上限；至少在事务内路径禁止 sleep（检测到活动事务时降级为不重试）。
12. **检测可见性**：事务时长可由 DB 侧观测；引擎无重试计数指标。
13. **覆盖备注**：nop-task 侧 retry（`RetryPolicy` + `TaskStepHelper.retry`）经核查上限完备（maxRetryCount 缺省 0、负值构建期硬失败、delay 封顶、jitter），**无发现**；本条仅 wf 侧。
14. **复核状态**： 已保留（P2——`Thread.sleep`（WorkflowEngineImpl:893）内联于调用线程、`computeRetryDelay` maxRetryDelay=0 不封顶（:926-931）、javadoc :871-875 半声明语义证实；触发面 DefaultWorkflowExecutor:46 → runAutoTransitions:811 → runStepAutoTransition:825 → runSourceWithRetry:835 逐点证实；对照 task 侧 `TaskStepHelper.retry:262-285` 经 scheduledExecutor 异步退避不占线程成立。现状：新发现）

### [nop-wf-nop-task-C7-006] wf dueTime/扫描基于墙钟：时钟回拨延迟升级、时钟前跳集中触发

1. **发现 ID**：nop-wf-nop-task-C7-006
2. **严重度**：P2（时钟敏感；后果依赖部署时钟管理）
3. **缺陷族/维度项**：C7-5 时钟与期限
4. **位置**：`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:389-394`（dueTime 一次性求值）；`nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/store/DaoWorkflowStore.java:184`（`le(dueTime, CoreMetrics.currentTimestamp())`）；时钟源 `nop-kernel/nop-api-core/.../time/CoreMetrics.java:23-24`（`System.currentTimeMillis()`）
5. **证据片段**：
```java
// WorkflowEngineImpl.java:389-394 —— dueTime 在步骤创建时一次性定值（墙钟）
if (stepModel.getDueTimeExpr() != null && stepModel.getDueAction() != null) {
    Timestamp dueTime = ConvertHelper.toTimestamp(stepModel.getDueTimeExpr().invoke(wfRt));
    if (dueTime != null) {
        stepRecord.setDueTime(dueTime);
    }
}
```
```java
// DaoWorkflowStore.java:184 —— 墙钟比较
query.addFilter(FilterBeans.le(NopWfStepInstance.PROP_NAME_dueTime, CoreMetrics.currentTimestamp()));
```
6. **增长/劣化链**：NTP 阶跃 × 方向二分 × 后果：回拨 → due 步骤晚触发（升级延迟，可接受劣化）；前跳（如 +1h 修正）→ 所有 dueTime 落入跳变窗口的步骤在同一个扫描周期集中触发（thundering herd），每条经 C7-001 的无 fencing 路径执行，双跑概率与集中度同增。`<retry>`/step 超时（nop-task `TaskStepHelper.timeout:199` 经 `ScheduledThreadPoolExecutor`，内部 nanoTime 单调）不受墙钟影响——task 侧期限机制对时钟回拨稳健，仅 wf due 面暴露。
7. **影响**：升级风暴/重复副作用窗口；无数据损坏。
8. **判定依据**：dueTime 比较链全程 `System.currentTimeMillis`；无单调钟或跳变防护（无最小间隔、无批量上限）。
9. **误报筛查**：不属平台设施自身实现（CoreMetrics 是平台设施，本条审其消费方式无跳变防护）。
10. **已声明边界对照**：未声明。
11. **修复方向**：扫描侧对单周期触发量设上限+告警；或 dueTime 记录基准钟并用单调钟校正。
12. **检测可见性**：风暴表现为 C7-002 的 ERROR 增多，无专门指标。
13. **覆盖备注**：epoch/generation 溢出核查完成：`TaskStateBean.newRunId` 为 int 自增（`:91-93`，每 task 实例独立、不跨重启累积），2^31 次重启内不可达，无溢出发现。
14. **复核状态**： 已保留（P2——dueTime 一次性墙钟求值（WorkflowEngineImpl:389-394）+ `le(dueTime, CoreMetrics.currentTimestamp())`（DaoWorkflowStore:184）+ CoreMetrics:23-24 `System.currentTimeMillis()` 全链证实；task 侧期限经 ScheduledThreadPoolExecutor（内部单调钟）不受墙钟影响的对照成立；无跳变防护（无最小间隔/批量上限）核实。现状：新发现）

### [nop-wf-nop-task-C7-007] 双引擎 DB 账本只增不删：无内置保留/归档路径（F4，DB 约束单列）

1. **发现 ID**：nop-wf-nop-task-C7-007
2. **严重度**：P2（已声明设计方向的运维缺口；DB 约束单独标注，不计入内存类 P0/P1）
3. **缺陷族/维度项**：C7-1 单调增长（F4 无界账本，DB 侧）
4. **位置**：写入口全集——`nop-wf/nop-wf-dao/.../store/DaoWorkflowStore.java`（saveWfRecord/saveStepRecord/saveActionRecord/logError/logMsg，`:130-299`）、`nop-wf/nop-wf-dao/.../entity/NopWfInstance.java:40-49`（每次状态迁移 append NopWfStatusHistory）；`nop-task/nop-task-dao/.../store/DaoTaskStateStore.java:132-143,355-374`（newTaskState/saveStepState 幂等 upsert）
5. **证据片段**：
```java
// NopWfInstance.java:40-49 —— 每次状态迁移追加一条历史
NopWfStatusHistory history = new NopWfStatusHistory();
history.setWfId(getWfId());
...
getStatusHistories().add(history);
```
```java
// DaoTaskStateStore.java:369-372 —— step 行按 (taskInstanceId, stepPath) upsert（行数有界/任务），
// 但 task 行 + step 行随任务数线性累积，且 errorBeanData/errorStack/stateBeanData 长期留存
if (isNew) stepDao().saveEntityDirectly(entity);
else stepDao().updateEntityDirectly(entity);
```
6. **增长/劣化链**：每笔业务流程/任务执行 × 行数（wf：1 instance + N step + N link + 每动作 1 action + 每状态迁移 1 history + 每错误 1 log；task：1 instance + 每 stepPath 1 行）× 时间尺度=天/周级连续运行 × 后果：五张 nop_wf_* 表与两张 nop_task_* 表随累计业务量线性膨胀；全仓 grep 确认引擎侧无任何 delete/ purge/ archive 作业（`removeWfRecord` 仅限显式 kill 后的人工 remove 动作且要求 isEnded）；终态行（COMPLETED/KILLED 等）与其大体积诊断列（errorBeanData≤4000 字符、errorStack≤4000 字符）永久留存。
7. **影响**：表膨胀 → 索引退化 → `findDueActivatedSteps`（全表 status+dueTime 过滤，见 C7-002）与任务查询变慢；存储成本线性增长。属 DB 约束问题（非堆内存），故按纪律单独标注。
8. **判定依据**：nop-wf/nop-task 全部 Java 源与 `_vfs` 配置中无 TTL/清理 job 定义；owner docs 将错误诊断列持久化声明为"既定设计（plan 265/266）"，但保留期策略未声明。
9. **误报筛查**：不作为内存泄漏计入；审计历史本身是审计类表的设计意图，本条针对"无保留策略机制"而非"存在历史表"。
10. **已声明边界对照**：owner doc（nop-task.md 残留风险清单）声明了诊断列的**读取面**风险与权限收敛建议，未声明**保留期/清理**归属。
11. **修复方向**：内置按终态时间戳的归档 job（可配保留期）或文档明确交付 DBA 侧保留策略。
12. **检测可见性**：表行数可查；引擎不提供账本规模指标。
13. **覆盖备注**：step 行按 stepPath upsert（loop 迭代不增行）已核实，增长粒度为"任务实例数"而非"步骤执行次数"。
14. **复核状态**： 已保留（P2——`NopWfInstance.transitToStatus` :36-51 每状态迁移追加 NopWfStatusHistory；nop-wf/nop-task 两 dao 模块 grep 无 delete/purge/TTL/archive 作业（唯一删除入口为显式 kill 后的 `removeWfRecord`）；`DaoTaskStateStore.newTaskState:131-143`/`saveStepState:354-374` 幂等 upsert、行数按任务实例线性累积证实；owner doc 仅声明诊断列读取面风险、未声明保留期归属。现状：新发现）

### [nop-wf-nop-task-C7-008] task 全局限流器/信号量注册表：动态 keyExpr 下无界增长 + 一次性告警 + 统计端点无生产消费者

1. **发现 ID**：nop-wf-nop-task-C7-008
2. **严重度**：P2（注册表强引用为已声明边界；本条针对动态 key 基数与监控失效面）
3. **缺陷族/维度项**：C7-1 内存历史累积
4. **位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:63-66,175-215`；key 求值 `nop-task/nop-task-core/.../step/RateLimitTaskStepWrapper.java:47-57`、`ThrottleTaskStepWrapper.java:55-65`；容量配置 `TaskConfigs.java:11-18`
5. **证据片段**：
```java
// TaskFlowManagerImpl.java:63-66 —— 强引用、永不驱逐（声明边界）
private final ConcurrentHashMap<String, IRateLimiter> globalRateLimiters = new ConcurrentHashMap<>();
private final ConcurrentHashMap<String, ISemaphore> globalSemaphores = new ConcurrentHashMap<>();
private final AtomicBoolean rateLimiterCapWarned = new AtomicBoolean();
private final AtomicBoolean semaphoreCapWarned = new AtomicBoolean();
```
```java
// RateLimitTaskStepWrapper.java:50-55 —— key 可以是每次执行求值的动态值
key = ConvertHelper.toString(keyExpr.invoke(stepRt));
if (StringHelper.isEmpty(key)) key = stepRt.getStepPath();
// TaskFlowManagerImpl.java:177-178 —— 动态 key 直接成为永久注册表条目
String cacheKey = taskRt.getTaskName() + ":" + key;
IRateLimiter limiter = globalRateLimiters.computeIfAbsent(cacheKey, k -> { ... });
```
6. **增长/劣化链**：任务步骤配置 `global=true` + 动态 `keyExpr`（如按 customerId/订单号/日期分桶）× 每个新 key 值永久分配一个 DefaultRateLimiter/DefaultSemaphore（固定字段约数百字节，含 map entry 开销）× 时间尺度=每任务执行都可能新增 key × 后果：周/月级运行下注册表单调膨胀（10 万 key ≈ 数十 MB，百万 key ≈ 数百 MB 量级，全程不回收）；`warnIfOverCap` 经 `compareAndSet(false,true)` 每注册表**只告警一次**（`:210`），首次越线后再增长无任何日志；`getGlobalSemaphoreStats/getGlobalRateLimiterStats/resetGlobalStats`（`:217-245`）在生产代码中零调用方（grep 仅测试引用）——容量/基数无运行时可观测出口。
7. **影响**：慢速堆增长难定位；permit 池分裂风险为零（正确性优先正确），但内存劣化与"同 key 参数固化告警"（:185-188，同为每调用 WARN，动态 key 下高频路径反而可能刷 WARN——与本条一次性的越线告警不对称）。
8. **判定依据**：代码注释与 owner doc（nop-task.md "global=true 的限流器/信号量……注册表为强引用、按配置容量阈值告警（plan 364 [05-04]，在用实例永不驱逐）"）声明了强引用策略与"key 基数增长以 WARN 提示（每注册表一次）"——边界已声明，故不判 P1；但 warn-once 的监控语义与 stats 无消费者两点未声明。
9. **误报筛查**：非 Caffeine 驱逐缺陷（该问题已在 plan 364 修复，驱逐会分裂 permit 池——修复本身正确）；本条不推翻强引用裁定，仅登记其监控缺口与动态 key 维度。
10. **已声明边界对照**：强引用=已声明；warn-once 与 stats 无出口=未声明。
11. **修复方向**：越线告警改周期性（如每 N 次新增重发一次）；为 stats 暴露管理端点或 micrometer gauge（registry size + per-key stats）。
12. **检测可见性**：堆 dump 可见；运行时仅首次越线 WARN。
13. **覆盖备注**：非 global（taskRt attribute 作用域）限流器/信号量随 runtime 回收，已核实无泄漏（`TaskRuntimeImpl.attrs` 生命周期=runtime）。
14. **复核状态**： 已保留（P2——强引用注册表（TaskFlowManagerImpl:63-66）、warn-once `compareAndSet`（:209-215）、动态 key 直接成为永久条目（:177-178 + RateLimitTaskStepWrapper:47-57 keyExpr 逐次求值）、stats/reset 零生产调用方（grep 全仓仅接口声明/实现/测试命中）全部证实。cross-ref：内存增长主面已由同单元 C5-003 立案（P1，C5 复核保留），stats 全量拷贝面由 C5-006（P3，复核保留）登记——本条 P2 限缩为 warn-once 语义与监控出口缺口，与 C5 判级自洽，归并留 7.1。现状：新发现）

### [nop-wf-nop-task-C7-009] 崩溃后在途任务无恢复看门狗：ACTIVE 行永久滞留、workerId 从不赋值、无 fencing 基础

1. **发现 ID**：nop-wf-nop-task-C7-009
2. **严重度**：P2（跨进程恢复为已声明暂缓主题；本条登记其检测面缺口）
3. **缺陷族/维度项**：C7-4 自愈语义（F5：滞留 RUNNING 不可见）
4. **位置**：`nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java:452,505`（workerId 仅 round-trip）；全仓 grep 无 `setWorkerId` 生产赋值点；`nop-task/nop-task-core/.../impl/TaskImpl.java:94-117`（resume 终态短路——恢复唯一入口为显式 `getTaskRuntime` 调用）
5. **证据片段**：
```java
// DaoTaskStateStore.java:452 / 505 —— workerId 双向搬运，但无任何生产代码写入节点身份
state.setWorkerId(entity.getWorkerId());
...
entity.setWorkerId(state.getWorkerId());
```
```java
// TaskImpl.java:98 —— resume 短路读 DB snapshot；无 DB 行则无人知道任务曾存在
if (taskRt.isRecoverMode() && taskState.isTerminal()) { ... }
```
6. **增长/劣化链**：JVM 崩溃/OOM/发布重启时正在执行的持久化任务（DB 中 status=ACTIVE 30、step 行 status=ACTIVE）× 无任何扫描 reconcile 该状态 × workerId 列存在但永远为 null（无法识别属主节点、无法判断属主死亡）× 后果：滞留 ACTIVE 行随每次崩溃线性累积，永不收敛；超时机制不覆盖此场景（step timeout 定时器随进程死亡消失）；唯一恢复手段是人（或上游调用方）再次调用 resume，而 resume 前无人能区分"执行中""已死""该重跑"。多节点同时 resume 同一实例无 fencing（owner doc 声明跨进程并发写为已知边界）。
7. **影响**：运营面表现为"任务卡在执行中"；每次发布重启都遗留一批需人工甄别的 ACTIVE 行；无指标暴露滞留量。
8. **判定依据**：nop-task 全模块无 scheduled recover/reconcile 代码（grep recover/restart/worker 均无生产命中）；ITaskQueue（nop-task-queue）为空壳接口（仅 `void enqueueTask()`），无实现无消费者。
9. **误报筛查**：跨进程 resume 完整性已由 owner doc 声明为"DB 断点续跑完整性设计主题，暂缓实施"——本条不重立该裁定，只登记**检测盲区**（滞留不可见）这一未声明部分。
10. **已声明边界对照**：跨进程写入可靠性=已声明暂缓；滞留 ACTIVE 的可观测性缺口=未声明。
11. **修复方向**：workerId 写入节点身份+心跳时间戳；提供滞留 ACTIVE 扫描告警（先只读不自动恢复，与暂缓裁定兼容）。
12. **检测可见性**：仅 DB 人工查询；无 gauge、无日志。
13. **覆盖备注**：进程内 async 完成路径（promise 泄漏→任务悬空）已由 timeout 兜底 promise（TaskStepHelper.timeout:194-204）与 first-terminal-wins 覆盖，无发现。
14. **复核状态**： 已保留（P2——workerId 仅 :452/:505 entity↔state round-trip、全仓无生产赋值点（grep 仅生成类/接口/bean 搬运命中）；nop-task 无任何 recover/reconcile 扫描代码；`ITaskQueue` 为单方法空壳接口（无实现无消费者）；resume 终态短路（TaskImpl:98）为唯一恢复入口证实。登记口径恰当：不重立 owner doc"跨进程 DB 断点续跑暂缓"裁定，仅登记未声明的滞留可观测性缺口。现状：新发现）

### [nop-wf-nop-task-C7-010] SUSPENDED 任务（含 resume-when 已满足者）无自动续跑与滞留区分监控

1. **发现 ID**：nop-wf-nop-task-C7-010
2. **严重度**：P2（信息性/监控盲区；语义本身为声明设计）
3. **缺陷族/维度项**：C7-2 挂死可检测性（F5 变体：滞留 SUSPENDED）
4. **位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/step/SuspendTaskStep.java:28-43`；resume 入口 `TaskFlowManagerImpl.getTaskRuntime:121-134` + `TaskImpl.execute`
5. **证据片段**：
```java
// SuspendTaskStep.java:36-42 —— resumeWhen 仅在 resume 调用链上求值，无周期驱动
if (resumeWhen == null) {
    return TaskStepReturn.CONTINUE;
}
if (!resumeWhen.passConditions(stepRt))
    return TaskStepReturn.SUSPEND;
```
6. **增长/劣化链**：任务经 `<suspend>` 进入 SUSPENDED × resumeWhen 条件此后自满足（如外部状态就绪）但无人调用 resume × 时间尺度=无限期 × 后果：任务停留 SUSPENDED(10)；grep 全模块无任何 job 扫描 SUSPENDED 任务或轮询 resumeWhen；"awaiting-human"与"条件已满足但被遗忘"两类滞留在监控上不可区分。与 nop-wf 的 WfTaskScanner（due/remind 双扫描）对照，nop-task 侧完全没有对应物。
7. **影响**：挂起任务随业务累积无收敛机制；无超时升级路径（task 有 per-step timeout，但 SUSPENDED 等待本身无期限语义）。
8. **判定依据**：owner doc 将 suspend 定位为"挂起等人工"（人工 resume 是声明语义），故不判缺陷级；但"无滞留监控"是事实缺口。
9. **误报筛查**：不把"等人工"本身当缺陷（声明语义）；仅登记可观测性缺口，避免与已声明边界冲突。
10. **已声明边界对照**：resume 需显式调用=已声明；滞留无监控=未声明。
11. **修复方向**：提供 SUSPENDED 滞留时长指标或可选的 resume-when 轮询 job（默认关闭）。
12. **检测可见性**：SUSPENDED 状态可经 CRUD 查询；引擎无主动上报。
13. **覆盖备注**：kill 对 SUSPENDED 的直接终态驱动（TaskRuntimeImpl.cancel:94-105）已存在，取消侧无滞留。
14. **复核状态**： 已保留（P2——resumeWhen 仅在 resume 调用链求值（SuspendTaskStep:36-42，首入返回 SUSPEND 后无任何周期驱动）；nop-task 全模块无 job.yaml、无 SUSPENDED 扫描/轮询代码（grep 证实）；nop-wf 侧 WfTaskScanner 双扫描与 task 侧零对应物的对照成立。"等人工"声明语义与可观测性缺口的切分恰当，不与 owner doc 冲突。现状：新发现）

### [nop-wf-nop-task-C7-011] step timeout 兜底不覆盖同步阻塞体：无视 token 的同步死循环使 timeout 失效且不可见

1. **发现 ID**：nop-wf-nop-task-C7-011
2. **严重度**：P2（覆盖面缺口；需步骤体自身存在无界同步路径）
3. **缺陷族/维度项**：C7-2 挂死可检测性（sync 等待对健康检测不可见）
4. **位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:184-244`（timeout 实现）、`nop-task/nop-task-core/.../step/TimeoutTaskStepWrapper.java:20-29`
5. **证据片段**：
```java
// TaskStepHelper.java:206-224 —— 同步体：调用线程在 task.apply 内，超时只能 cancel token，
// 不能中断线程；apply 不返回则整个 timeout() 不返回
TaskStepReturn result;
try {
    result = task.apply(cancellable);
} catch (Exception e) { ... }
if (!result.isAsync()) {
    ...
    return result;      // 只有 apply 返回后才可能走到
}
```
6. **增长/劣化链**：`<timeout>` 包裹的步骤体为同步实现且不轮询 cancel token（XPL 死循环/无超时外部调用/大计算）× 超时定时器到点后完成 timeoutGuard 兜底 promise，但**无消费者**——调用方仍阻塞在 `task.apply` 内 × 后果：线程钉死、task 滞留 ACTIVE（衔接 C7-009 无看门狗）、TIMEOUT(50) 终态 driver 永不被驱动；兜底 promise 的价值仅覆盖 async 体（其注释 :194-196 自述目标是"返回值在有限时间内终结"，对 sync 体该目标不成立）。
7. **影响**：超时承诺对一类常见步骤体（同步 script/同步 HTTP）失效；每发生一次即钉死一个线程，多个则线程池耗尽。
8. **判定依据**：cancel token 是协作式取消；同步 CPU 循环/无超时 I/O 不检查 token；`checkNotCancelled` 仅在步骤边界调用（TaskStepExecution:271）。
9. **误报筛查**：异步体与检查 token 的同步体已被正确覆盖（timer.cancel 于同步快路径 :218-223 与 winner.whenComplete :238-242）——本条只针对"不合作的同步体"这一残余面，非指 timeout 机制整体失效。
10. **已声明边界对照**：TaskStepHelper/TimeoutTaskStepWrapper 注释声明了异步兜底意图，未声明同步体不受保护。
11. **修复方向**：文档明确"同步体必须自行响应 cancel 或自带超时"；或对 XPL source 注入周期性 token 检查（loop 边界）。
12. **检测可见性**：线程 dump 可见；任务面滞留 ACTIVE 与 C7-009 相同不可见。
13. **覆盖备注**：`<sleep>`（C7-012）是本条的一个特例（waitUntil 轮询 token，属"合作"但阻塞）。
14. **复核状态**： 已保留（P2——同步体 `task.apply` 不返回则 `timeout()` 不返回（TaskStepHelper:206-224），超时定时器到点仅 cancel token+完成兜底 promise，同步路径对 timeoutGuard 零消费者；`checkNotCancelled` 仅步骤边界（TaskStepExecution:271）；异步体已被 winner 竞速正确覆盖（:226-243）、本条仅限不合作同步体，切分准确。现状：新发现）

### [nop-wf-nop-task-C7-012] SleepTaskStep 同步占用调用线程且时长无构建期上限校验

1. **发现 ID**：nop-wf-nop-task-C7-012
2. **严重度**：P2（边界校验缺陷）
3. **缺陷族/维度项**：C7-2 挂死可检测性 / C7-3 重试风暴近邻（资源占用型放大）
4. **位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/step/SleepTaskStep.java:31-45`；对比 `DelayTaskStep.java:32-50`（异步等价物）；构建期校验面 `TaskStepEnhancer.java:114-127,216-233`（timeout/retry 负值硬失败，sleep 无对应校验）
5. **证据片段**：
```java
// SleepTaskStep.java:32-38 —— 同步轮询睡眠，时长上不设限
Long sleep = ConvertHelper.toLong(sleepMillisExpr.invoke(stepRt));
if (sleep == null) sleep = -1L;
if (sleep <= 0) return TaskStepReturn.CONTINUE;
FutureHelper.waitUntil(stepRt::isCancelled, sleep);   // 100ms 轮询，占线程
```
6. **增长/劣化链**：任务流（尤其 xbiz action 绑定路径，owner doc 用法一）内 `<sleep sleep-millis-expr="...">` 配大值或表达式求值异常放大 × 每个并发调用钉死一个请求线程 × 后果：50 个并发 × 1h sleep = 50 线程被占用，服务线程池（含 DB 连接事务）随之耗尽，无关请求饿死；sleep 值为表达式求值结果，构建期无法预知，但平台对 timeout/retry/rate-limit 的数值边界均在构建期硬失败（owner doc plan 364 取值约束），sleep 是唯一无上限校验的时长类属性。
7. **影响**：线程饥饿型不可用；sleep 期间事务（若有外层 action 事务）同步悬挂，同 C7-005 的资源持有面。
8. **判定依据**：`FutureHelper.waitUntil(test, timeout, 100)` 为同步轮询（nop-api-core FutureHelper:496-500）；对照 DelayTaskStep 用 `scheduledExecutor.schedule` + ASYNC return 不占线程——同模块内已存在正确形态，Sleep 属遗留形态。
9. **误报筛查**：线程在轮询中属"活着"非挂死，但资源占用效应等同；非测试专用。
10. **已声明边界对照**：owner doc 的取值约束清单（timeout/retry/rate-limit 负值硬失败）未包含 sleep——校验面缺口未声明。
11. **修复方向**：构建期对 sleep 上限告警/硬失败；或改造为 DelayTaskStep 的异步形态；文档指引"需要等待时用 delay 而非 sleep"。
12. **检测可见性**：线程栈可见；无按步骤耗时的慢步骤指标（metrics 无 per-step 维度，见覆盖清单 G5——这是防基数爆炸的合理取舍，但也使慢 sleep 不可见）。
13. **覆盖备注**：xdef 中 sleep 无 `xdef:must-expr` 型上限约束（`task.xdef:297` 附近核实 suspend/resume-when；sleep-millis 为普通 expr）。
14. **复核状态**： 已保留（P2——`FutureHelper.waitUntil` 缺省 100ms 同步轮询（nop-api-core FutureHelper:496-500）占线程、时长为表达式求值结果无上限；对照 DelayTaskStep:38-50 异步 schedule+ASYNC 形态同模块存在；构建期校验面 timeout 负值硬失败（TaskStepEnhancer:127-130）与 retry 三参数负值硬失败（:216-233）而 sleep 无对应校验、owner doc 取值约束清单未含 sleep，均证实。现状：新发现）

### [nop-wf-nop-task-C7-013] 循环体每迭代 saveState 的 DB 写放大 + 每次 save 重复模型解析查找（优化候选）

1. **发现 ID**：nop-wf-nop-task-C7-013
2. **严重度**：P3（优化候选，无正确性影响）
3. **缺陷族/维度项**：C7-1/C7-6（每次调用都增长的小对象→DB 往返放大）
4. **位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/step/LoopTaskStep.java:152-157`（每迭代 `stepRt.saveState()`）；`nop-task/nop-task-dao/.../store/DaoTaskStateStore.java:355-374`（saveStepState=1 SELECT findStepEntity + 1 UPDATE）+ `:366-367`（每次 save 调 `collectSecretProps` → `loadComponentModel`，`:930-942`）
5. **证据片段**：
```java
// LoopTaskStep.java:152-157 —— 每次迭代一次断点写
stateBean.incIndex();
stepRt.setBodyStepIndex(0);
stepRt.saveState();
```
```java
// DaoTaskStateStore.java:357-358, 366-368 —— 每次落盘：行锁→SELECT→模型查找→序列化→UPDATE
synchronized (rowLock(state.getTaskInstanceId(), state.getStepPath())) {
    NopTaskStepInstance entity = findStepEntity(state.getTaskInstanceId(), state.getStepPath());
    ...
    Set<String> secretProps = collectSecretProps(taskRt.getTaskName(), taskRt.getTaskVersion());
```
6. **增长/劣化链**：`<loop>` 遍历大集合（itemsExpr 返回 10 万条）× 每迭代 1 SELECT + 1 UPDATE（外加 retry/fork 路径同型落盘）× 后果：单任务执行产生 20 万级 SQL；大 items 列表同时整份序列化进 stateBeanData（>4000 字符时降级丢弃并 WARN——行为正确但每迭代重复序列化-丢弃循环）。`collectSecretProps` 虽有 ResourceComponentManager 缓存兜底（非泄漏），但每次 save 走一遍 map 查找+secret 集合构建，10 万迭代即 10 万次。
7. **影响**：长循环任务耗时随迭代数线性放大（每次落盘 ≈ 1-5ms → 10 万迭代 ≈ 分钟级纯落盘开销）；对 DB 形成稳定写压力。无状态损坏。
8. **判定依据**：代码路径直读；无批处理/去抖。
9. **误报筛查**：不判缺陷——断点续跑契约要求迭代级持久点（plan 349/364 设计），本条仅登记成本形态与优化空间。
10. **已声明边界对照**：迭代级 saveState 是声明契约的一部分；成本未声明。
11. **修复方向**：secret props 按 taskName/version 会话内缓存；或迭代落盘改每 N 次/时间窗去抖（契约层面需权衡断点粒度）。
12. **检测可见性**：task meter 有 step 级 timer（无 stepPath 维度），可看到循环步骤整体耗时异常。
13. **覆盖备注**：行锁条带 64 槽（DaoTaskStateStore:339-352）为有界结构，非发现。
14. **复核状态**： 已保留（P3——每迭代 `stepRt.saveState()`（LoopTaskStep:155-157）×每 save 1 SELECT（findStepEntity）+1 UPDATE（DaoTaskStateStore:355-374）+每次 `collectSecretProps`→`loadComponentModel`（:930-942，有 ResourceComponentManager 缓存但逐次 map 查找+集合构建）全部证实；迭代级持久点为断点续跑契约（plan 349/364）声明行为，成本登记 P3 恰当。现状：新发现）

---

## 维度集外线索（C6/C9 形态 P0/P1）

本轮未发现需登记的维度集外 P0/P1 线索。说明：C7-005（事务内 Thread.sleep）涉及时长但属有界退避且已在本维度立案；C7-008 强引用注册表的内存增长本质是本维度第 1 项（内存历史累积）的正常登记范围，不构成 C5 集外 P0/P1（增长速率慢、每实例开销小、边界已声明）。

## 覆盖度与残留边界

**已覆盖**：
- nop-wf 扫描面（WfTaskScanner 全文 + job yaml + beans 装配链 + LocalJobScheduler pool 配置）；wf 引擎主路径（WorkflowEngineImpl 全文：start/suspend/resume/kill/transition/retry/dueTime/join/signal）；store 层（DaoWorkflowStore 全文，含五表写入口与三个 REQUIRES_NEW 日志方法）；服务层（WorkflowServiceImpl 全文，transferActors 逐项失败隔离为半个成功反面样本——已按设计确认）；执行器上限防护（DefaultWorkflowExecutor 10,000 环上限——自愈类正向确认）。
- nop-task 核心面（TaskFlowManagerImpl/TaskRuntimeImpl/TaskStepRuntimeImpl/TaskImpl/TaskStepExecution/TaskStepHelper 全文；DaoTaskStateStore 全文；Loop/Fork/Parallel/Suspend/Sleep/Delay 步骤；retry/timeout/rate-limit/throttle wrapper 与 ext 装饰器的配置校验路径；TaskFlowMetricsImpl 基数；TaskExceptionRegistry 有界性；TaskConfigs 容量配置；TaskImpl 五个终态 driver 的 first-terminal-wins——自愈类正向确认）。
- 重试风暴面：wf `runSourceWithRetry`（同步 sleep，C7-005）与 task `RetryPolicy`/`TaskStepHelper.retry`（上限完备、异步退避、SUSPEND 不重试、真取消不计次——正向确认）双侧对照完成。
- 时钟与溢出面：CoreMetrics 墙钟（C7-006）、ScheduledThreadPoolExecutor 单调钟（timeout/delay 稳健）、`TaskStateBean.newRunId` int 自增无现实溢出路径。

**残留边界（未深读/未立案）**：
1. `GraphTaskStep`（453 行）、`ForkNTaskStep`、`LoopNTaskStep`、`SequentialTaskStep`、`SelectorTaskStep` 仅核查了与聚合/挂起传播相关的片段，未逐行审计其内部死循环面；owner doc 已声明 graph 死端以 `ERR_TASK_GRAPH_NO_ACTIVE_STEP` 报错不挂死（声明边界）。
2. nop-wf-web 的 AMIS 页面与 WfDesignerConfigBuilder（设计器导出）未审计——无运行时长尾语义。
3. nop-task-ext 的 TransactionTaskStepDecorator/OrmSessionTaskStepDecorator 仅核查配置校验，未审计其事务嵌套与长循环组合的代价（与 C7-013 同型，成本问题不另立）。
4. 挂死清单 H7（wf join 步骤滞留 WAITING 无扫描兜底）未立案：触发需"上游步骤被 skip/异常退出且无 dueTime"的配置组合，未能构造稳定的必然链，按纪律不具体化不立案，登记于挂死清单备查。
5. TaskImpl 挂起路径保留 task 级 beanContainer（`TaskImpl.java:154-161` 注释声明保留给进程内 resume）：若容器内 bean 持有线程类资源且任务永不 resume，存在理论泄漏窗口；因未发现任务级 `<bean>` 定义持有线程的实证配置，按"写不出具体链降级"原则登记为残留疑点而非发现条目。
6. nop-cluster 与 nop-job 的集群模式（若部署接入分布式调度器）会改变 C7-001/C7-004 的暴露面——本单元按 nop-wf-scheduler 自带的 local 装配审计。
7. 误报校准应用记录：`WorkflowImpl.steps`/`WfRuntime`/`TaskRuntimeImpl.attrs`（每调用生命周期，G6）；`TaskFlowMetricsImpl` 有界标签（G5）；`ROW_LOCKS` 64 槽（C7-013 内注明）；扫描单条失败隔离与信息性 LOG.debug 主体（C7-003 仅针对其滞留后果）。

---

## 维度复核结论（独立复核子代理）

- 复核子代理：独立复核子代理（与初审 C7 维度 agent_d9f69db1 / 第 2 轮 agent_348c76d9 不同角色，职责为逐条验证、保留/降级/驳回与现状标注）
- 日期：2026-10-15
- 仓库：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation，HEAD be0cbbb2a3）
- 复核方法：17 条发现逐条重读 live code 验证（WfTaskScanner 全文、两 job yaml、app-scheduler.beans.xml、app-local-scheduler.beans.xml、DaoWorkflowStore 关键段、WorkflowEngineImpl invokeAction/doInvokeAction/runSourceWithRetry/computeRetryDelay/dueTime/checkAllowedAction 段、DefaultWorkflowExecutor 全文、CoreMetrics、NopWfInstance.transitToStatus、TaskFlowManagerImpl 全文、TaskImpl 全文、TaskRuntimeImpl.cancel 段、DaoTaskStateStore newTaskState/saveStepState/rowLock/copyStepStateToEntity/collectSecretProps 段、SuspendTaskStep/SleepTaskStep/DelayTaskStep/LoopTaskStep/CallTaskStep、TaskStepHelper.timeout、TaskStepExecution 步骤边界、TaskStepEnhancer 校验面、RateLimit/Throttle wrapper、TaskConfigs、NopTaskInstanceBizModel 全文）；独立 grep（nop-cluster 依赖、purge/delete/TTL、setWorkerId 生产赋值点、stats 调用方、SUSPENDED 扫描 job、`*.job.yaml` 清单）；现状核对仅限 cra-index.md、同 campaign WI 1.1–1.4 审计目录结论/复核节、同单元 C1/C2/C5 已复核条目的复核状态行、git log（未读取 ai-dev/plans、ai-dev/bugs、ai-dev/lessons）。

### 逐条判定表

| 发现编号 | 原判级 | 判定 | 一句话理由 | 现状标注 |
|---|---|---|---|---|
| C7-001 | P1 | 保留 | 多节点无 fencing 链逐环证实：LocalJobScheduler 节点本地无集群锁、source 执行（:1255）先于状态守卫（:1280）、checkAllowedAction 纯内存判定，多节点下 due source 双跑接近确定性而非罕见边沿，副作用静默翻倍 | 新发现（cross-ref C1-005 同扫描面 P2，判级差异留 7.1） |
| C7-002 | P2 | 保留 | 失败分支无收敛（不改状态/无计数/无退避）+ 查询无 limit，逐行证实 | 新发现 |
| C7-003 | P2 | 保留 | DEBUG 跳过 + job 缺省 disabled 证实；复核精化：纯缺 dueAction 子案因 :389 门控连扫描结果集都不入，盲区更彻底（不削弱结论） | 新发现 |
| C7-004 | P2 | 保留 | syncGet 无 deadline、动作在 job 线程内联执行、pool-size=4 共享调度池逐点证实 | 新发现 |
| C7-005 | P2 | 保留 | 事务路径 Thread.sleep 内联 + maxRetryDelay=0 不封顶 + 触发面（execute→autoTransition→retry）逐点证实 | 新发现 |
| C7-006 | P2 | 保留 | dueTime 求值→落库→扫描比较全链墙钟（:389-394→:184→CoreMetrics:23-24）证实 | 新发现 |
| C7-007 | P2 | 保留 | 两 dao 模块 grep 无任何 delete/purge/TTL/archive 路径，账本线性累积证实 | 新发现 |
| C7-008 | P2 | 保留 | warn-once（:209-215）+ stats/reset 零生产调用方（grep 证实）；增长主面 cross-ref C5-003（P1 保留），本条限缩为监控出口缺口，判级自洽 | 新发现（cross-ref C5-003/C5-006，归并留 7.1） |
| C7-009 | P2 | 保留 | workerId 零生产赋值 + 无 recover/reconcile 扫描 + ITaskQueue 空壳接口，逐项证实 | 新发现 |
| C7-010 | P2 | 保留 | resumeWhen 无周期驱动、nop-task 无任何 job.yaml/扫描器，对照 wf 侧双扫描成立 | 新发现 |
| C7-011 | P2 | 保留 | 同步体不返回则 timeout() 不返回、timeoutGuard 同步路径零消费者、token 检查仅步骤边界，逐点证实 | 新发现 |
| C7-012 | P2 | 保留 | sleep 同步轮询占线程无上限 + 构建期校验面（timeout/retry 有、sleep 无）+ owner doc 约束清单缺口，证实 | 新发现 |
| C7-013 | P3 | 保留 | 每迭代 saveState×(SELECT+UPDATE)+逐次模型查找成本证实；断点契约内优化候选，P3 恰当 | 新发现 |
| C7-014 | P2 | 保留 | 双执行链逐环证实（独立 runtime × 监视器跨实例失效 × 条带锁仅行写 × CallTaskStep 传播面）；后果有界，符合 campaign 校准线 | 新发现 |
| C7-015 | P3 | 保留 | per-JVM 作用域（实例字段+无 nop-cluster）+ owner doc 仅声明跨租户维度，证实；单节点无影响，P3 恰当 | 新发现 |
| C7-016 | P3 | 保留（附同点判级对账注记） | 竞态对（裸写 SUSPEND × 监视器内 kill 判定）证实；同点 C1-001/C2-001 均 P1 保留，差异按指令留 7.1；按 WI 1.4 校准线独立定级应为 P2 下限（列不确定项） | 新发现（cross-ref C1-001/C2-001） |
| C7-017 | P3 | 保留 | frozen WARN 每调用无抑制 + 同注册表 warn-once 对照 + semaphore 侧对称静默，三点证实 | 新发现 |

**判定计数**：保留 17（P1×1、P2×11、P3×5，C7-016 计 P3）/ 降级 0 / 驳回 0。全部 17 条现状均为「新发现」（无任何一条在本 campaign 之前的审计或 plan 中立案；无修复提交——git log 中 nop-wf/nop-task 最近的变更均为审计前合入的 plan 2379/2380/364 修复，绿色基线之前）。

### 不确定项（显式列出）

1. **C7-016 判级张力**：本条 P3 与同点 C1-001/C2-001（均独立复核保留 P1）存在维度视角差；且按 WI 1.4 复核确立的校准线（"罕见但可具体化竞态中后果无界者才入 P1"、罕见竞态+后果有界→P2），本条独立定级应为 P2 下限而非 P3。复核指令三态限定（保留/降级/驳回）不含升级，故保留 P3 并留 7.1 归并；归并时应取同点最高级（P1），本条不构成覆盖缺口。
2. **C7-001 与 C1-005 的同面判级差**：C1-005（P2 保留）与 C7-001（P1）为同一 WfTaskScanner 扫描面的两个视角（C1 强调 remindCount RMW/共享状态，C7 强调 due action source 双跑的业务副作用）。C7-001 的 P1 论证更强：多节点部署下双节点同秒 cron 命中同一 ACTIVATED 记录、source 双跑的窗口为整个 action 执行时长，接近确定性而非罕见竞态。7.1 归并时需统一。
3. **C7-003 机制精化**："配置 dueTimeExpr 但缺 dueAction"子案的初审机制表述不精确（WorkflowEngineImpl:389 要求 dueAction != null 才落 dueTime，该子案步骤根本不入 `findDueActivatedSteps` 结果集，连 DEBUG 日志都无）；"模型漂移致 getDueAction 返回 null"子案机制逐字成立。盲区结论反而更强，不构成降级/驳回理由。
4. **C7-014 的"静默 last-writer-wins"分支**：跨线写碰撞时 DB version 乐观锁（nop-task.orm.xml 两表 versionProp；EntityPersisterImpl 对 0 行更新抛 ERR_ORM_UPDATE_ENTITY_NOT_FOUND）使静默覆盖分支窄于初审表述（多数交错以 ORM 异常响亮失败），但主链（业务步骤体双执行、副作用先于任何 DB 冲突检测）不受影响，P2 维持。

### 与 campaign 其他维度/单元的自洽性

- 判级校准线引用：WI 1.4（nop-job）C7 复核确立"罕见竞态 + 后果有界（可自愈、无账本增长）→ P2，后果无界者才入 P1"。按此线：C7-001（每周期近乎确定性双跑、副作用无界累积、完全静默）P1 成立；C7-014（单实例双跑、有界）P2 成立；C7-016（毫秒窗、单次 kill 可重试收敛）按线为 P2 下限（见不确定项 1）；C7-017（需配置漂移前置）P3 成立。
- 同单元 cross-ref 汇总（7.1 归并输入）：C7-001↔C1-005（同一扫描面）；C7-008↔C5-003（P1）/C5-006（P3）与 C8-004~008（同注册表背压面）；C7-016↔C1-001/C2-001（同一代码点，双 P1）；C7-014 与 C1-006（saveTaskState 无条带锁）为相邻面不同缺陷，无需归并。
- 正向结论复核维持：wf 自动迁移 10,000 环上限、task RetryPolicy 上限完备、TaskFlowMetricsImpl 有界标签、四个终态 driver first-terminal-wins 原子序列、ROW_LOCKS 64 槽有界——重验均成立。
- 无 P0 遗漏：17 条中无"包络内必然 OOM/主路径泄漏/可达死锁"形态，与初审 P0=0 一致。

## 最终保留项

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状 |
|---|---|---|---|---|
| C7-001 | P1 | nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java | 多节点部署 dueAction/remind 扫描无 fencing：action source 双跑副作用先于状态守卫、remindCount 丢失更新（cross-ref C1-005，留 7.1 归并） | 新发现 |
| C7-002 | P2 | nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java | due/remind 扫描对持续失败项无抑制：每周期重放 ERROR、滞留 ACTIVATED 无界重扫、查询无 limit | 新发现 |
| C7-003 | P2 | nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java | 滞留 ACTIVATED 看门狗双重失效：无 dueAction 时 DEBUG 级跳过 + 扫描 job 缺省 disabled | 新发现 |
| C7-004 | P2 | nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java | dueAction 同步阻塞可挂死扫描线程并级联耗尽 pool-size=4 共享本地调度池，无健康上报 | 新发现 |
| C7-005 | P2 | nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java | wf `<retry>` 以 Thread.sleep 内联阻塞调用线程并持有事务；exponential 退避缺省不封顶 | 新发现 |
| C7-006 | P2 | nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java | dueTime/到期扫描全链墙钟：时钟回拨延迟升级、前跳集中触发无防护 | 新发现 |
| C7-007 | P2 | nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/store/DaoWorkflowStore.java | 双引擎 DB 账本只增不删：无内置保留/归档/清理路径，诊断列永久留存 | 新发现 |
| C7-008 | P2 | nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java | global 闸门注册表 warn-once 不可操作 + stats 端点零生产消费者（增长主面 cross-ref C5-003 P1） | 新发现 |
| C7-009 | P2 | nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java | 崩溃后在途任务无恢复看门狗：ACTIVE 行永久滞留、workerId 从不赋值、无 fencing 基础 | 新发现 |
| C7-010 | P2 | nop-task/nop-task-core/src/main/java/io/nop/task/step/SuspendTaskStep.java | SUSPENDED 任务（含 resume-when 已满足者）无自动续跑与滞留区分监控 | 新发现 |
| C7-011 | P2 | nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java | step timeout 兜底不覆盖不合作同步体：无视 token 的同步死循环使 timeout 失效且不可见 | 新发现 |
| C7-012 | P2 | nop-task/nop-task-core/src/main/java/io/nop/task/step/SleepTaskStep.java | sleep 同步占用调用线程且时长无构建期上限校验（同模块 DelayTaskStep 为正确异步形态） | 新发现 |
| C7-013 | P3 | nop-task/nop-task-core/src/main/java/io/nop/task/step/LoopTaskStep.java | 循环体每迭代 saveState 的 DB 写放大 + 每次 save 重复模型解析查找（优化候选） | 新发现 |
| C7-014 | P2 | nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java | task 引擎无执行级 fencing：同一 taskInstanceId 并发 resume 双执行（进程内可达，监视器随实例对象失效） | 新发现 |
| C7-015 | P3 | nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java | global 限流器/信号量实为 per-JVM：多节点部署限额按节点数线性放大，语义未声明 | 新发现 |
| C7-016 | P3（同点 C1-001/C2-001 均 P1，留 7.1 统一） | nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskImpl.java | SUSPEND 写入游离于 taskState 监视器外，与 cancel 的 SUSPENDED 判定竞态可致 kill 静默丢失 | 新发现 |
| C7-017 | P3 | nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java | global 限流器 frozen 告警每调用一条无抑制 WARN（漂移态持续至重启）；semaphore 侧对称静默 | 新发现 |

需子项复核的高风险项：无（唯一 P1 即 C7-001，已按从严口径完成多节点部署形态/扫描互斥缺失/副作用先于守卫三环节的逐项验证；C7-014 双执行链亦逐环实证；其余均为监控面/边界配置/成本类 P2/P3，无待深挖项）。
