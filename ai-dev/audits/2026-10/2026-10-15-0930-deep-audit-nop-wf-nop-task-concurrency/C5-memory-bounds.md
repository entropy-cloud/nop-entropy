# C5 内存有界性与大数据量 — nop-wf + nop-task 第 1 轮初审（盲审）

- 审计单元：`nop-wf`（10 jar 子模块）+ `nop-task`（10 jar 子模块）
- 仓库：nop-entropy-concurrency-audit，分支 concurrency-audit-remediation（be0cbbb2a3）
- 审计维度：C5 内存有界性与大数据量（F4/F7/F11 定向对照）
- 日期：2026-10-15；审计人：并发审计子代理（盲审，未读 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何既有文件）
- 证据基线：`_tmp/cra-baseline-nop-wf-nop-task.txt` + live code 逐文件核读

---

## 检查范围说明

**模块清单**（src/main，排除 `target/`、`_gen/`、`_` 前缀生成物）：

- nop-wf：nop-wf-api / nop-wf-core（engine、impl、model、store、support）/ nop-wf-dao（store、dataobject、entity 非生成部分）/ nop-wf-service（entity、actor、designer）/ nop-wf-scheduler / nop-wf-ai / nop-wf-app / nop-wf-web / nop-wf-meta / nop-wf-codegen（后四者为资源型或仅 Application 入口，无独立 Java 逻辑）
- nop-task：nop-task-api / nop-task-core（impl、step 全部 26 个 step/wrapper、state、builder、metrics）/ nop-task-dao（DaoTaskStateStore、TaskExceptionRegistry）/ nop-task-service / nop-task-ext（dao/orm/ratelimit/retry/timeout decorator）/ nop-task-queue / nop-task-web / nop-task-meta / nop-task-codegen / nop-task-app

**C5 五项检查点的落实**：

1. **随输入增长容器盘点**：逐个核对了 TaskFlowManagerImpl 全局注册表、TaskRuntimeImpl.attrs、TaskStepRuntimeImpl（ATTR_FIRST_INSTANTIATED_STEP_PATHS）、TaskStateBean/TaskStepStateBean（taskVars/stateBean）、全部 step 实现（Loop/LoopN/Fork/ForkN/Parallel/Graph/Sequential/Selector/Choose/If/Delay/Sleep/Suspend 及全部 wrapper）、WorkflowImpl.steps、WfRuntime 字段、WfActorAssignSupport、DaoWorkflowStore/AbstractWorkflowStore 全部集合路径、DaoTaskStateStore、TaskExceptionRegistry、TaskFlowMetricsImpl、DaoWfActorResolver。逐项判定见下节清单。
2. **无界 API 使用点**：grep `findAllByQuery|findAll(|findFirstByQuery` 全单元扫描（命中集：DaoWorkflowStore×3、DaoWorkflowModelLoader×1、DaoTaskStateStore×1），并对照平台 `OrmEntityDao.findAllByQuery`（`orm().findAll(sql)`，**无隐式 LIMIT**；QueryBean.limit 缺省 0 且该路径不消费 limit）逐点核实。
3. **物化 vs 流式**：DaoTaskStateStore 序列化锚点（taskInputs/remark/stateBeanData/errorBeanData）、Loop/Fork items 物化、designer GraphDocument 编解码。
4. **缓存淘汰**：全局注册表强引用 [05-04] 已声明边界——按其声明的评估口径（**键空间有界性**）专项评估（C5-003）；ResourceComponentManager 模型缓存属平台设施，按模型数键空间有界，不在本单元立案。
5. **峰值叠加**：在每条发现的"峰值叠加"字段给出 单请求有界 × 并发 × 键基数 推演。

**规模包络**：两份 owner doc（`docs-for-ai/03-modules/nop-wf.md`、`nop-task.md`）均未声明数值化数据规模包络，仅声明并发语义/注册表容量告警阈值（`nop.task.max-global-semaphores|max-global-rate-limiters` 缺省 10000，**仅为 WARN 阈值非强制上限**）。故本报告以"单 wf/单 task 实例自身历史规模"与"调度周期积压规模"为参照包络，超出常用包络的 OOM 风险在对应条目单独标注。

**13 字段格式说明**：每条发现含 ①编号与标题 ②严重度 ③缺陷族 ④位置 ⑤证据片段 ⑥驻留结构与增长源 ⑦触发链（输入规模×路径×驻留） ⑧声明上界与保证者 ⑨规模包络对照 ⑩峰值叠加 ⑪误报校准 ⑫修复方向 ⑬覆盖度备注。

---

## 无界风险清单（路径 × 增长源 × 声明上界 × 判定）

| # | 路径（容器/查询） | 增长源 | 声明上界与保证者 | 判定 |
|---|---|---|---|---|
| 1 | `DaoWorkflowStore.findDueActivatedSteps/findRemindActivatedSteps`（stepDao().findAllByQuery 无 LIMIT） | 激活且到期的步骤实例数（跨全部 wf；失败步骤 dueTime 不清除 → 永久滞留） | 无。QueryBean 未设 limit；平台 findAllByQuery 无隐式上限 | **无界，立案 C5-001（P1）** |
| 2 | `DaoWorkflowStore.findActivatedStepsByOwner` + `WorkflowServiceImpl.transferActorsAsync` 批量转办 | 单 fromUserId 的激活步骤数 | 无 LIMIT；结果按 owner 有界但批量端点无条数上限 | **有界键/无界批，立案 C5-002（P2）** |
| 3 | `TaskFlowManagerImpl.globalRateLimiters/globalSemaphores`（强引用 ConcurrentHashMap，永不驱逐 [05-04]） | 键 = `taskName + ":" + keyExpr 求值结果`；keyExpr 为动态 EL（task.xdef `keyExpr="expr"`） | 已声明边界 [05-04]：强引用为正确性裁定，容量配置仅为 WARN-once 阈值；**边界自身要求键空间有界**——静态 key 有界（模型数×节点数），动态 key 无界 | **条件无界，立案 C5-003（P1）** |
| 4 | `ForkNTaskStep.promises`（`new ArrayList<>(count)` 预分配）+ 聚合期全部分支结果驻留 | countExpr 求值整数（可引用 task input） | 无上限；LoopTaskStep 有 maxCount、ForkN 无任何 cap | **无界（单请求），立案 C5-004（P2）** |
| 5 | `LoopTaskStep.LoopStateBean.items` / `ForkTaskStep.ForkStateBean.items`（`CollectionHelper.toList(itemsExpr)` 全量物化） | itemsExpr 求值集合大小；items 驻留整个循环期，且每迭代 saveState 全量重序列化 | 无 items 规模上限；持久化侧 4000 字符降级（DaoTaskStateStore）仅约束 DB 列不约束内存 | **无界（单请求），立案 C5-005（P2）** |
| 6 | `AbstractWorkflowStore` 全部读路径经 `DaoWorkflowStore.getSteps` → `NopWfInstance.getSteps()`（IOrmEntitySet 全量懒加载，无 SQL 侧过滤/分页）；`getLatestStepRecordByName` 全集排序；`runAutoTransitions` 每轮全集扫描 | 单 wf 实例的全部步骤实例行（历史含在内；allowStepLoop 回退环下每请求最多新建 10k 步骤行，跨请求累积） | DefaultWorkflowExecutor 限单请求 10,000 轮（硬编码）；跨请求/单 wf 生命周期无界（F4 DB 侧账本，有 cascade-delete 随 wf 删除） | **读路径全集物化，立案 C5-006（P2）** |
| 7 | `LoopNTaskStep` 迭代计数（begin/end/step 均为表达式整数） | (end-begin)/step 可达 2^31 量级；每迭代一次 findStepEntity+update 落盘 | 无 cap（对照 LoopTaskStep.maxCount）；内存 O(1) 但 DB 写放大与扫描时长无界 | **迭代数无界，立案 C5-007（P2）** |
| 8 | `DaoTaskStateStore.serializeStepStateData/serializeErrorBeanData`（JsonTool.serialize 全量物化后再判长）+ `filterSecretProps`（再 parse+serialize 一次） | state.getResultValue()/stateBean/outputs 载荷大小 | 列宽上限（4000/200/500）+ 超长降级；但**判定前**的瞬态物化无预算检查 | **瞬态 2-3× 物化，立案 C5-008（P3，F11）** |
| 9 | `DaoWfActorResolver` resolveRole/resolveGroup/resolveDept（`getRelatedUserList()/getDeptUsers()` ORM 关系全量） | 角色/部门/组成员用户数（org 数据） | org 注册表规模（部署内固有有界，缓增） | **包络内有界但按 org 全量物化，立案 C5-009（P3）** |
| 10 | `TaskFlowManagerImpl.getGlobalSemaphoreStats/getGlobalRateLimiterStats`（全量 HashMap 拷贝） | 注册表键基数 | 与 C5-003 同源；静态 key 下有界 | **随动放大，立案 C5-010（P3）** |

**核对为有界/无需立案的容器**（证据备查）：

- `TaskRuntimeImpl.attrs`（ConcurrentHashMap）与 `ATTR_FIRST_INSTANTIATED_STEP_PATHS`：task 执行级，随 runtime 生命周期回收；后者键空间 = 模型 stepPath 集合（TaskStepRuntimeImpl.java:36,172-178）。
- `GraphTaskStep.STEP_RESULTS`（makeResults → ConcurrentHashMap，GraphTaskStep.java:428-438）：键 = 图节点名，模型有界。
- `ParallelTaskStep.promises`（ParallelTaskStep.java:64）：模型子步骤数有界。
- `WorkflowImpl.steps`（HashMap，WorkflowImpl.java:41）+ `commandQueue`：**每请求新建**（WorkflowManagerImpl.getWorkflow 每次返回新 WorkflowImpl，WorkflowManagerImpl.java:81-88），无跨请求驻留注册表；规模受 DefaultWorkflowExecutor 10k 轮上限间接约束。
- `WorkflowEngineImpl`：无 static 集合；auto-transition 死循环已有 10,000 轮硬上限（DefaultWorkflowExecutor.java:51-59）。
- `DaoTaskStateStore.ROW_LOCKS`：固定 64 槽静态数组（DaoTaskStateStore.java:339-352）。
- `TaskExceptionRegistry`：4 个编译期/反射 FQCN + reflectiveMissing 缓存，键空间封闭（TaskExceptionRegistry.java:43-71）。
- `TaskFlowMetricsImpl` → GlobalMeterRegistry：meter 按 (task.name, task.version, status) 内聚，键空间 = 模型数×版本数（TaskFlowMetricsImpl.java:28-48）。
- timeout/Delay 定时器：TaskStepHelper.timeout 同步/异步/异常三出口均 `timer.cancel(false)`（TaskStepHelper.java:197-240），TimeoutTaskStepWrapper 恢复外层 token——无定时器滞留。
- `WfExecGroupType.textMap` 等静态 Map：枚举值域封闭。
- `WorkflowCoordinatorImpl`、`WfRuntime`、`WfModelAnalyzer`、`WfGraphDocumentCodec`、`WorkflowDesignerService`：请求级/模型级有界。
- 平台 CRUD 端点（NopWfInstanceBizModel 等纯 CrudBizModel）：分页由平台 QueryBean 强制，非本单元缺陷。

---

## 发现条目

### [nop-wf-nop-task-C5-001] WfTaskScanner 到期/提醒扫描无 LIMIT 全量载入，失败到期步骤永久滞留扫描集
1. **编号与标题**：C5-001 到期任务扫描 `findAllByQuery` 无 LIMIT 全量物化 + 失败重扫无退避。
2. **严重度**：P1（长时运行扫描集无界增长；包络内高概率内存/时长失控）。
3. **缺陷族**：F7（无 LIMIT 查询全量载入；OOM 同族）。次生 F4 形态（失败条目不清 dueTime，只增不扫出）。
4. **位置**：
   - `nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/store/DaoWorkflowStore.java:180-186`（findDueActivatedSteps）、`:188-194`（findRemindActivatedSteps）
   - `nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java:64-143`
   - `nop-wf/nop-wf-scheduler/src/main/resources/_vfs/nop/job/conf/wf-due-task-scan.job.yaml:4-9`（cron `0 0/1 * * * ?` 每分钟，`enabled|false` 缺省关闭）
5. **证据片段**：
   ```java
   // DaoWorkflowStore.java:181-186
   public List<? extends IWorkflowStepRecord> findDueActivatedSteps() {
       QueryBean query = new QueryBean();
       query.addFilter(FilterBeans.eq(NopWfStepInstance.PROP_NAME_status, NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED));
       query.addFilter(FilterBeans.le(NopWfStepInstance.PROP_NAME_dueTime, CoreMetrics.currentTimestamp()));
       return stepDao().findAllByQuery(query);     // 无 limit；平台侧 orm().findAll(sql) 无隐式上限
   }
   ```
   平台证据：`nop-persistence/nop-orm/src/main/java/io/nop/orm/dao/OrmEntityDao.java:674-686` — `findAllByQuery` 直接 `orm().findAll(sql)`，不消费 `query.getLimit()`；`QueryBean.limit` 缺省 0。
6. **驻留结构与增长源**：scanDueTasksInCurrentSession 在 `ormTemplate.runInNewSession` 单会话内 foreach 全量列表（WfTaskScanner.java:78-119）——整表结果 + 每条触发的 model/entity 加载全程驻留会话持久化上下文直至扫描结束。增长源 = `status=ACTIVATED AND dueTime<=now` 的行数；**失败路径不清 dueTime**（扫描仅成功 transit 后状态离开 ACTIVATED；NopException 分支只 LOG，WfTaskScanner.java:112-118），持续失败的到期步骤每分钟重新入集且永不退出。
7. **触发链**：启用 `nop.job.wf-due-task-scan.enabled=true` 后，部署 N 个含 dueAction 的审批步骤（如 5 万行积压，或 1 千个因 action 配置错误持续失败的"毒"步骤）× 每分钟调度 × 每轮 `findAllByQuery` 全量载入 5 万 NopWfStepInstance 实体并驻留整个扫描会话；毒步骤使集合永不收敛。宕机恢复/节假日积压场景集合同步跳增。
8. **声明上界与保证者**：无。QueryBean 未设 limit，平台 findAllByQuery 不补偿；job 配置无批大小参数。
9. **规模包络对照**：owner doc 未声明包络；5 万级激活步骤在多租户审批场景属常规包络内取值，超出该包络（百万级积压）为额外 OOM 风险放大，不改变定级。
10. **峰值叠加**：单轮驻留 O(due 集合) × 调度周期 1/min；与在线请求 ORM 会话无共享，但扫描会话内存峰值叠加上一轮未完成时串行重叠（FutureHelper.syncGet 串行执行使单实例内不并发叠加，多实例部署下每实例各一份）。
11. **误报校准**：非低频路径（启用后每分钟）；非平台设施自身；非测试代码。job 缺省 disabled 降半级考量，但该 feature 的声明用法即常开调度，维持 P1。
12. **修复方向**：QueryBean 设 limit + orderBy(dueTime) 分页游标推进（对照 nop-job C5-002 先例修法）；失败条目计入 retryCount 并按退避推迟 dueTime（或隔离表），使扫描集可收敛。
13. **覆盖度备注**：findRemindActivatedSteps 同构同修；与 C5-002 共享查询族修复面。
- **复核状态**：已保留（P1。从严复核属实：QueryBean 无 limit（DaoWorkflowStore.java:181-186/:189-194 引文与 live 一致）；平台侧 `findAllByQuery`→`queryToSelectObjectSql`（DaoQueryHelper.java:78-93）确不消费 limit，无隐式上限；job cron `0 0/1 * * * ?` 每分钟（wf-due-task-scan.job.yaml:5）核实；失败路径仅 LOG（WfTaskScanner.java:112-118），且全引擎唯一 `setDueTime` 在步骤激活时（WorkflowEngineImpl.java:389-394，且要求模型同时声明 dueTimeExpr+dueAction），失败步骤 dueTime 永不清、状态停留 ACTIVATED——毒步骤子集随时间单调不收敛，符合 P1"长时运行必然无界增长"口径。与 C8-001 同点（C8 尚未复核，本判定未读 C8 文件），按扫描族 P1 自洽、可归并。精度备注：remindTime 引擎侧无写入点（grep 仅接口/bean setter 与 scanner 清 null），remind 半区当前恒空集（与历史 P2-3.1-004"remind 链三重死"一致），P1 由 due 半区独立支撑，不影响判级。现状：新发现——同点位历史发现均为其他维度（P1-3.1-002 身份通道已修复 f1fa5cc888、P2-3.1-001 bean 失配、P2-3.1-004 remind 链死），扫描无界/毒步骤滞留无先前登记）

### [nop-wf-nop-task-C5-002] transferActorsAsync 批量转办端点无条数上限，逐条全量执行+failedItems 全量返回
1. **编号与标题**：C5-002 批量转办端点无上限批量物化（nop-job C5-002 先例同族）。
2. **严重度**：P2（边界配置缺陷；owner 域有界故非 P1）。
3. **缺陷族**：F7（批量端点无上限）。
4. **位置**：
   - `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/service/impl/WorkflowServiceImpl.java:213-255`
   - `nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/store/DaoWorkflowStore.java:170-178`
5. **证据片段**：
   ```java
   // WorkflowServiceImpl.java:224-227
   List<? extends IWorkflowStepRecord> stepRecords = workflowStore.findActivatedStepsByOwner(
           request.getFromUserId(), request.getWfIds());
   for (IWorkflowStepRecord stepRecord : stepRecords) {
       ...
       FutureHelper.syncGet(workflowExecutor.execute(wfRef, ctx, wf -> { ... }));   // 每条一次完整 executor 执行
   ...
   failedItems.add(buildFailedItem(stepRecord, e));    // 全量失败清单驻留并返回
   ```
   ```java
   // DaoWorkflowStore.java:170-178 —— 无 limit
   QueryBean query = new QueryBean();
   query.addFilter(FilterBeans.eq(NopWfStepInstance.PROP_NAME_ownerId, ownerId));
   ...
   return stepDao().findAllByQuery(query);
   ```
6. **驻留结构与增长源**：stepRecords 全量列表驻留整个事务（每条再 new WorkflowImpl + ORM 加载该 wf 全量记录集合，见 C5-006 叠加）；failedItems 无界累积。
7. **触发链**：fromUserId 为系统/共享账号（如部门邮箱、自动化账号）持有 1 万条激活步骤 × 单次 `transferActors(from,to)` 请求 → 1 万次完整 workflow 执行（每次含步骤全集懒加载）在同一 GraphQL mutation 事务内串行完成；单请求时长与内存 O(N)。
8. **声明上界与保证者**：无条数上限；唯一天然约束 = owner 域（单用户激活步骤数），无保证者。
9. **规模包络对照**：普通人工账号（几十~几百条）包络内安全；共享/系统账号超包络即退化，标注为包络外放大。
10. **峰值叠加**：单请求 O(N) × 转办请求并发数（无防重入锁，同一 fromUser 并发转办各自物化一份全集）。
11. **误报校准**：per-user 键有界、人工低频操作——故降 P2（与 C5-001 全局调度扫描区分）。
12. **修复方向**：findActivatedStepsByOwner 设 limit/分页 + 请求级 maxBatch 配置（对照 nop-job C5-002 先例）；超限显式报错或分页续转。
13. **覆盖度备注**：与 C5-001 同查询族；修复应一次覆盖三个查询点。
- **复核状态**：已保留（P2。复核属实：findActivatedStepsByOwner 无 limit（DaoWorkflowStore.java:170-178）+ transferActorsAsync 逐条完整 executor 执行、failedItems 无界累积（WorkflowServiceImpl.java:221-249 引文一致）、无防重入锁核实。per-user 域有界 + 人工低频操作支持 P2，与 C5-001 全局调度扫描的分级区分成立。现状：新发现——security-audit 2026-09-19 WF-01 仅涉同端点转办鉴权维度，非批量物化面，无同点登记）

### [nop-wf-nop-task-C5-003] 全局限流/信号量强引用注册表 × 动态 keyExpr → 键空间无界（已声明边界的键空间评估不通过）
1. **编号与标题**：C5-003 global throttle/rate-limit 注册表动态键无界增长。
2. **严重度**：P1（采用动态 key 的模型在长时运行下必然无界增长；静态 key 模型不受影响）。
3. **缺陷族**：F7（无界缓存/注册表）；对已声明边界 [05-04] 的**键空间有界性**专项评估（按审计纪律不按"无界缓存"泛化立案）。
4. **位置**：
   - `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:59-66`（强引用注册表与 WARN-once 阈值）、`:174-207`（getRateLimiter/getSemaphore computeIfAbsent）
   - `nop-task/nop-task-core/src/main/java/io/nop/task/step/ThrottleTaskStepWrapper.java:36-65`、`RateLimitTaskStepWrapper.java:34-57`（`getKey` 求值动态表达式）
   - `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef:115,121`（`keyExpr="expr"`）
5. **证据片段**：
   ```java
   // TaskFlowManagerImpl.java:63-66 —— plan 364 [05-04] 声明边界：强引用、永不驱逐、WARN-once
   private final ConcurrentHashMap<String, IRateLimiter> globalRateLimiters = new ConcurrentHashMap<>();
   private final ConcurrentHashMap<String, ISemaphore> globalSemaphores = new ConcurrentHashMap<>();
   ```
   ```java
   // ThrottleTaskStepWrapper.java:59-64 —— key 为每次执行求值的动态表达式，无长度/基数约束
   key = ConvertHelper.toString(keyExpr.invoke(stepRt));
   if (StringHelper.isEmpty(key)) key = stepRt.getStepPath();
   ```
   ```xml
   <!-- task.xdef:115 -->
   <throttle maxConcurrency="!int" maxWait="!int" keyExpr="expr" global="!boolean=false" .../>
   ```
6. **驻留结构与增长源**：`globalRateLimiters/globalSemaphores` 永不驱逐（plan 364 [05-04] 裁定：驱逐会分裂 permit 池，正确性优先）。键 = `taskName + ":" + keyExpr 求值值`。**键空间评估**：keyExpr 缺省（null）→ stepPath（模型有界，通过）；常量/低基数表达式（如租户 id）→ 有界；**高基数动态表达式（`${userId}`、`${entityId}`、`${requestId}`）→ 键基数 = 历史取值域，无界**。每 entry 为 DefaultRateLimiter/DefaultSemaphore（数十至数百字节 + 内部计数结构）。WARN-once（warnIfOverCap，TaskFlowManagerImpl.java:209-215）只提示一次，不构成上界。
7. **触发链**：任一 task 模型配置 `<throttle global="true" keyExpr="${userId}">`（按用户限流是该属性的存在动机）× 平台运行 1 年累计 10^7 独立用户/实体键 → 注册表常驻数百 MB 且只增不删；配合 `getGlobalSemaphoreStats` 全量导出（C5-010）二次放大。
8. **声明上界与保证者**：[05-04] 声明的评估口径即"键空间有界性"——静态键由模型数×节点数保证；动态键**无保证者**，容量配置转告警后无人闭环。
9. **规模包络对照**：高基数动态 key 超出"按任务模型配置有界"的隐含包络；无声明包络可依，按"长时运行必然无界增长"定 P1。
10. **峰值叠加**：注册表常驻（非请求级）× task 模型数 × 每模型动态键基数；多机部署每实例各一份全量键集。
11. **误报校准**：已声明边界条目——本条不否定强引用裁定本身，仅指出该裁定的可持续性前提（键空间有界）在动态 keyExpr 下不成立，且现有缓解（WARN-once）不可操作。若 owner 认定动态 key 为不支持用法，应在构建期拒绝（见修复方向 a），届时本条降级为文档声明缺口。
12. **修复方向**：(a) 构建期/执行期约束：global=true 时要求 keyExpr 解析为声明式低基数键（或直接禁止动态 key，回落 stepPath）；(b) 键空间治理：注册键加 lastAccess 时间戳 + 空闲淘汰（仅淘汰 permits==0 且无在用持有者的实例，不破坏 permit 池完整性）；(c) WARN 升级为周期性指标（暴露 registry.size gauge）替代 once-flag。
13. **覆盖度备注**：非全局（global=false）路径走 `taskRt.computeAttributeIfAbsent`（TaskFlowManagerImpl.java:191-193,204-206），随 runtime 回收，有界，不在本条范围。
- **复核状态**：已保留（P1。复核属实：强引用注册表 + WARN-once（TaskFlowManagerImpl.java:63-66/:178-206/:209-215 引文一致，代码注释即载 plan 364 [05-04] 裁定与 "never evicted" 字样）、keyExpr 每次执行求值（ThrottleTaskStepWrapper.java:55-65）、xdef keyExpr 属性（task.xdef:115,121）、非 global 路径随 runtime 回收均核实；round2 输入直通面亦核实——TaskStepExecution.initInputs（TaskStepExecution.java:493-507）将调用方 inputs 写入 stepRt 作用域，`keyExpr="${input.*}"` 使请求字段直通永久注册表键。分级自洽性：与 C5-004（P2）的区分线为"进程生命周期持久单调增长 vs 请求级瞬态分配"；动态 key 系 global throttle 特性的主流用法而非滥用，输入派生键域真无界，P1 成立。现状：已知未修复（plan 364 [05-04] 声明边界明确接受 key 基数增长风险、仅 WARN-once 缓解、无上界保证者——本发现即指出该前提在动态 keyExpr 下不成立）

### [nop-wf-nop-task-C5-004] ForkNTaskStep count 无上限且 ArrayList 预分配，单请求即时大分配
1. **编号与标题**：C5-004 fork-n 分支数无上限 + 容量预分配。
2. **严重度**：P2（边界配置缺陷；countExpr 引入外部输入时为单请求 OOM 通道，标注包络外放大）。
3. **缺陷族**：F7（无界集合/批量端点无上限，对照 nop-job C5-002 先例形态）。
4. **位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/step/ForkNTaskStep.java:36-58`
5. **证据片段**：
   ```java
   // ForkNTaskStep.java:36-42
   Integer count = stepRt.getStateBean(Integer.class);
   if (count == null) {
       count = TaskStepHelper.castInt(countExpr.invoke(stepRt), getLocation(), stepRt);
       stepRt.setStateBean(count);
   }
   List<CompletionStage<TaskStepReturn>> promises = new ArrayList<>(Math.max(count, 0));  // 按 count 即时预分配
   ```
6. **驻留结构与增长源**：promises 列表容量 = count（引用数组即时分配）；聚合期 `buildAggResult`（AbstractForkTaskStep.java:107-143）遍历全部 promise 并物化 MultiStepResultBean 全集。增长源 = countExpr 求值整数，无任何上限校验（对照 TaskStepEnhancer.java:130-134 对 timeout 负值的硬失败，count 无同款约束）。
7. **触发链**：task 模型 `<fork-n count="${input.shardCount}">`（countExpr 引用 task input，模型作者可写）× 调用方传 `shardCount=2^30` → `new ArrayList<>(2^30)` 即时请求 ~4-8GB 连续数组 → 单请求 OOMError；即便 OOM 前成功，2^30 个内联串行分支（缺省无 executor）使请求永不结束。
8. **声明上界与保证者**：无。xdef 对 count 无最大值约束；TaskStepEnhancer 构建期校验清单（timeout/retry/rate-limit）未覆盖 count。
9. **规模包络对照**：模型静态 count（如 8/16 分片）包络内有界；**外部输入驱动 count 超出任何合理包络，属包络外 OOM 通道，单独标注**。
10. **峰值叠加**：单请求峰值 = O(count)（预分配）+ O(完成分支结果集)；并发 N 个此类请求线性叠加。
11. **误报校准**：非低频路径（fork-n 为一等控制结构）；非平台设施；非测试代码。模型作者可控使其常态有界 → P2 而非 P1。
12. **修复方向**：构建期对 fork-n 增加 count 上限校验（对照 [02-06] 硬失败先例，如 `count<=0 || count>MAX` 报 ERR_TASK_STEP_CONFIG_INVALID）；或将预分配改为增量 ArrayList 消除即时大分配。
13. **覆盖度备注**：ForkTaskStep（集合驱动，C5-005）与 ParallelTaskStep（模型子步骤数有界）不属本条。
- **复核状态**：已保留（P2。复核属实：count 自 countExpr 无上限校验 + `new ArrayList<>(Math.max(count, 0))` 即时预分配（ForkNTaskStep.java:36-42 引文一致）；对照先例核实——TaskStepEnhancer.java:130-134 对 timeout 负值硬失败（plan 364 [维度02-06]）而 count 无同款约束；count<=0 空 fork 守卫（plan 349）不约束上界。瞬态单请求分配、模型作者可控，P2 正确且与 C5-003 分级线自洽。现状：新发现）

### [nop-wf-nop-task-C5-005] Loop/Fork items 全量物化驻留整个循环期，且每迭代全量重序列化 stateBean
1. **编号与标题**：C5-005 loop/fork items 全量物化 + 每迭代 saveState 全量重序列化（O(N) 驻留 / O(N²) 序列化量）。
2. **严重度**：P2（单请求有界于输入规模，但输入规模无上界且驻留跨整个循环周期）。
3. **缺陷族**：F7（全量载入 vs 流式）+ F11（每迭代重复物化放大）。
4. **位置**：
   - `nop-task/nop-task-core/src/main/java/io/nop/task/step/LoopTaskStep.java:121-128`（items 物化入 stateBean）、`:155-171`（每迭代 saveState）
   - `nop-task/nop-task-core/src/main/java/io/nop/task/step/ForkTaskStep.java:51-63`
   - `nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java:560-599`（serializeStepStateData 将 stateBean 整体纳入 wrapper）
5. **证据片段**：
   ```java
   // LoopTaskStep.java:124-127
   List<Object> items = CollectionHelper.toList(itemsExpr.invoke(stepRt));  // 全量物化
   stateBean.setItems(items);
   stateBean.setIndex(0);
   stepRt.setStateBean(stateBean);      // items 进入 stateBean → 参与后续每次 saveState 序列化
   ```
   ```java
   // LoopTaskStep.java:155-157 —— 每迭代一次
   stateBean.incIndex();
   stepRt.setBodyStepIndex(0);
   stepRt.saveState();                  // → DaoTaskStateStore.serializeStepStateData 全量 wrapper 序列化
   ```
   ```java
   // DaoTaskStateStore.java:571-579 —— wrapper 含完整 stateBean（即 items 列表）
   wrapper.put("stateBean", state.getStateBean(Object.class));
   ...
   String json = filterSecretProps(JsonTool.serialize(wrapper, false), secretProps);
   if (json != null && json.length() <= 4000) return json;   // 判长在物化之后
   ```
6. **驻留结构与增长源**：items 列表驻留 LoopStateBean/ForkStateBean 整个循环/聚合期（ suspend 后经进程内 resume 继续驻留）；每迭代对含全量 items 的 wrapper 做一次 JSON 序列化（瞬态 String，O(items 序列化长度)），完成后因 >4000 丢弃——内存每迭代瞬时 O(N)，累计序列化字节 O(N²)。
7. **触发链**：`<loop items="${largeList}">`（largeList 为调用方传入的 10 万行集合）× 每迭代一次 saveState → 10 万次全量重序列化（每次 ~数十 MB 瞬态字符串，GC 可回收但堆峰值与 CPU 随 N² 增长）；items 本身驻留至循环结束。
8. **声明上界与保证者**：items 规模无上界（maxCount 只截断迭代次数不截断物化）；持久化侧 4000 字符降级（degrade=drop，有 WARN）只保 DB 列，不保内存。
9. **规模包络对照**：千级 items 包络内无害；10 万级超出常用任务编排包络，标注包络外放大。
10. **峰值叠加**：单请求 O(N) 驻留 + O(N) 瞬态；与并发任务数线性叠加。
11. **误报校准**：低频路径校准不适用（loop 为高频结构），但单请求内存有上界（=输入规模）故按纪律不判 P1；降 P2 并给出具体链。
12. **修复方向**：(a) stateBean 持久化视角剥离大体积 items（仅持久化 index/游标 + 可重算的 itemsExpr，恢复时重算）；(b) itemsExpr 返回 Iterable 时走游标消费而非 toList；(c) 短期缓解：LoopStateBean 序列化白名单（index/当前项），对齐已有的 degrade=resultValueOnly 思路。
13. **覆盖度备注**：ForkTaskStep 同构（`producer.invoke` → toList → stateBean → 首次 saveState 即全量序列化一次，ForkTaskStep.java:54-59）。
- **复核状态**：已保留（P2。复核属实：items 全量物化入 stateBean（LoopTaskStep.java:121-128 引文一致）、每迭代 incIndex+saveState（:155-157 同步路径/:169-171 异步路径）、Fork 同构（ForkTaskStep.java:51-59）、serializeStepStateData 先全量物化后判 4000（DaoTaskStateStore.java:564-599）均核实。round2 核实的 stateBean 写回缓解（TaskStepStateBean.java:212-227）仅消除进程内 resume 重解析，跨 saveState DB 序列化路径本条仍成立；单请求驻留有界于输入规模，P2 正确。现状：新发现）

### [nop-wf-nop-task-C5-006] wf 步骤记录读路径经 ORM 全集物化（无 SQL 侧过滤/分页），单 wf 历史规模无上界
1. **编号与标题**：C5-006 步骤实例读路径全集物化 + 单 wf 步骤账本跨请求无界增长（F4 DB 侧联动）。
2. **严重度**：P2（单请求有界于该 wf 自身历史；单 wf 历史跨请求无界增长且读路径按全集放大）。
3. **缺陷族**：F7（全量载入 vs 过滤下推）；F4（DB 侧只增账本，有 cascade-delete 边界，单独标注）。
4. **位置**：
   - `nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/store/DaoWorkflowStore.java:305-307`（getSteps 直返 ORM 集合）
   - `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/store/AbstractWorkflowStore.java:51-76`（getStepRecords 内存过滤）、`:108-133`（getJoinWaitStepRecords 全集扫描）、`:183-197`（getNextJoinStepRecord 全集扫描）
   - `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/DefaultWorkflowExecutor.java:51-59`（单请求 10k 轮上限）
   - `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:811-818`（runAutoTransitions 每轮 wf.getActivatedSteps()）
5. **证据片段**：
   ```java
   // DaoWorkflowStore.java:305-307 —— 返回 IOrmEntitySet 全量懒加载集合（首触加载该 wf 全部步骤行，无 SQL 过滤）
   protected Collection<? extends IWorkflowStepRecord> getSteps(IWorkflowRecord wfRecord) {
       return ((NopWfInstance) wfRecord).getSteps();
   }
   ```
   ```java
   // AbstractWorkflowStore.java:60-69 —— includeHistory/filter 均在内存流上过滤
   if (includeHistory && filter == null) {
       return getSteps(wfRecord);
   }
   return getSteps(wfRecord).stream().filter(step -> { ... }).collect(Collectors.toList());
   ```
6. **驻留结构与增长源**：任一 `getStepsByName/getSteps/getActivatedSteps/getWaitingSteps/getLatestStepRecordByName/getJoinWaitStepRecords` 调用触发 NopWfInstance.steps 关系全集载入（含全部历史行）；ORM 会话内该集合驻留至会话结束。增长源 = 单 wf 实例累计步骤行数：allowStepLoop 回退环 + 恒真迁移条件下每请求最多新增 10,000 步骤行（DefaultWorkflowExecutor 上限），跨请求无限累积；action/statusHistory/log 各表同源增长。
7. **触发链**：长生命周期审批流（运行 1 年、经 200 次回退迭代累计 2 万步骤行）× 每次任意 action 调用（invokeAction → runAutoTransitionsWithLimit → 每轮 getActivatedSteps 全集扫描）→ 每请求 2 万行实体载入 + O(N) 内存过滤，且 auto-transition 多轮时 O(轮数×N) 比较。
8. **声明上界与保证者**：单请求 10k 轮（DefaultWorkflowExecutor 硬编码，有 ERR_WF_AUTO_TRANSITION_EXCEED_LIMIT 兜底）；单 wf 生命周期步骤总数**无上界、无保证者**（无历史归档/裁剪机制）；steps 关系有 `ref-cascade-delete`（nop-wf/model/nop-wf.orm.xml:486），wf 删除时可整体回收——DB 侧账本受"wf 删除"约束，单独标注为 F4-DB 受控子案。
9. **规模包络对照**：普通审批流（几十步骤）包络内无感；回退环型流程超包络后读放大显著。
10. **峰值叠加**：单请求 O(该 wf 全历史) × 在飞同 wf 并发请求数（WorkflowImpl 无共享缓存，每请求各载一份全集）。
11. **误报校准**：非低频路径；但读集合受单 wf 历史约束（部署内通常有限）→ P2。
12. **修复方向**：(a) 高频谓词（isActivated/isWaiting/stepName/status<_historyBound）下推为 QueryBean SQL 过滤（stepDao 已可按 wfId+status 查）；(b) getLatestStepRecordByName 改 SQL orderBy(createTime desc) limit 1；(c) 单 wf 步骤数治理：超阈值告警/归档策略（F4-DB 主题，与 owner 已声明的跨进程断点边界分开跟踪）。
13. **覆盖度备注**：transferActorsAsync（C5-002）与 WfTaskScanner 扫描动作均叠加本条放大；WorkflowImpl.steps 缓存为请求级（WorkflowImpl.java:41），无跨请求驻留，不另立案。
- **复核状态**：已保留（P2。复核属实：getSteps 直返 ORM 全集（DaoWorkflowStore.java:305-307）、getLatestStepRecordByName 全集排序（AbstractWorkflowStore.java:51-55）、getJoinWaitStepRecords/getNextJoinStepRecord 全集扫描（:108-133/:183-197）、10k 轮上限 + ERR_WF_AUTO_TRANSITION_EXCEED_LIMIT（DefaultWorkflowExecutor.java:51-59）、runAutoTransitions 每轮 getActivatedSteps 全集（WorkflowEngineImpl.java:811-818）、steps 关系 cascadeDelete="true"（nop-wf/model/nop-wf.orm.xml steps to-many）均与引文一致；WorkflowImpl 每请求新建（WorkflowManagerImpl.java:81-88）核实。现状：新发现——其中 10k 上限本身系 plan 344 既有修复（075561f891），本条立案的是其上的全集读放大与单 wf 账本无归档面）

### [nop-wf-nop-task-C5-007] LoopNTaskStep 迭代数无上限（对照 LoopTaskStep.maxCount），每迭代 DB 落盘放大
1. **编号与标题**：C5-007 loop-n 迭代计数无上限边界。
2. **严重度**：P2（边界配置缺陷；内存 O(1) 但扫描/DB 写无界）。
3. **缺陷族**：F7 变体（无界循环资源放大；非纯内存项，按"边界配置缺陷"立案）。
4. **位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/step/LoopNTaskStep.java:153-232`（对照 `LoopTaskStep.java:36-42,192-209` 的 maxCount 机制）
5. **证据片段**：
   ```java
   // LoopNTaskStep.java:159-170 —— begin/end/step 均为表达式整数，无上限校验
   int begin = ConvertHelper.toPrimitiveInt(beginExpr.invoke(stepRt), NopException::new);
   int end = ConvertHelper.toPrimitiveInt(endExpr.invoke(stepRt), NopException::new);
   int step = stepExpr == null ? 1 : ConvertHelper.toPrimitiveInt(stepExpr.invoke(stepRt), NopException::new);
   ...
   stateBean.setCurrent(begin); stateBean.setEnd(end); stateBean.setStep(step);
   ```
   ```java
   // LoopNTaskStep.java:192-195 —— 每迭代落盘一次
   stateBean.incStep();
   stepRt.setBodyStepIndex(0);
   stepRt.saveState();
   ```
6. **驻留结构与增长源**：内存驻留 O(1)（LoopStateBean 仅计数器）；无界资源为迭代次数 × 每迭代一次 `findStepEntity`（SELECT）+ `updateEntityDirectly`（UPDATE）。step 为负时反向计数、step 整数下溢（current += step 越过 Integer.MIN_VALUE）语义未防护。
7. **触发链**：`<loop-n begin="${0}" end="${input.total}">`，调用方传 `total=10^9` → 引擎按每迭代 2 条 SQL 推进，等效永续 DB 写循环，占满 rowLock 条带（DaoTaskStateStore 64 槽， DaoTaskStateStore.java:339-352）并拖垮同槽其他任务的落盘串行化。
8. **声明上界与保证者**：无。LoopTaskStep 具备 maxCount 上限机制而 LoopN 无对应属性（xdef `loop-n` 节点无 max-count），非对称边界。
9. **规模包络对照**：常规分页步进（千级）包络内无害；10^9 级超包络，标注。
10. **峰值叠加**：单请求 DB QPS = 2/迭代 × 循环体耗时；并发多个大 loop-n 线性叠加 DB 与锁槽压力。
11. **误报校准**：非测试代码、模型作者可静态有界 → P2；本条主危害是资源时长而非驻留内存，若 owner 判定 loop-n 属"作者自负"原语，可降 P3 并文档声明。
12. **修复方向**：loop-n 增加 max-count（对齐 LoopTaskStep.maxCount），构建期校验 |end-begin|/|step| 超限硬失败（对齐 [02-06] 先例）；防 step 下溢。
13. **覆盖度备注**：与 C5-004 同属"计数类边界缺失"修复面，可合并一个构建期校验清单。
- **复核状态**：已保留（P2。复核属实：LoopNTaskStep 全字段核对无 maxCount（:30-41），begin/end/step 表达式整数（:159-161）、step==0 有守卫（:163-165）而负 step 反向计数与 `current += step` 整数回绕无防护（shouldContinue :102-106 语义下可达成 ~2^32 量级迭代）、每迭代 saveState（:193-195）均核实；与 LoopTaskStep.maxCount（LoopTaskStep.java:31,192-195）非对称成立。条目自留的"可降 P3"出口不启用——边界缺陷与 C5-004 同级一致。现状：新发现）

### [nop-wf-nop-task-C5-008] 状态持久化序列化先全量物化再判长 + secret 过滤二次 parse/serialize（F11）
1. **编号与标题**：C5-008 序列化中间体积无预算检查的重复物化。
2. **严重度**：P3（优化候选；瞬态 2-3× 载荷，受调用方输入规模约束）。
3. **缺陷族**：F11（序列化中间体积膨胀）。
4. **位置**：
   - `nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java:564-599`（serializeStepStateData）
   - `:970-1002`（filterSecretProps → JsonTool.parse + stripSecretKeys 全量拷贝 + JsonTool.serialize）
   - `:182-200,205-224`（saveTaskState 的 taskInputs/remark 同构路径）
5. **证据片段**：
   ```java
   // DaoTaskStateStore.java:579-581 —— 全量 JSON 已物化为 String 后才判 4000
   String json = filterSecretProps(JsonTool.serialize(wrapper, false), secretProps);
   if (json != null && json.length() <= 4000) return json;
   ```
   ```java
   // DaoTaskStateStore.java:973-976 —— filterSecretProps 内部再 parse + 全量重建 + serialize
   Object parsed = JsonTool.parse(json);
   Object filtered = stripSecretKeys(parsed, secretProps);
   return JsonTool.serialize(filtered, false);
   ```
6. **驻留结构与增长源**：state.resultValue/stateBean（可达调用方输入全量，见 C5-005）→ wrapper JSON String（1×）→ secret 过滤时 parse 出全量对象图 + 重建拷贝（1-2×）+ 再序列化 String（1×）。瞬态峰值 ≈ 3× 载荷。
7. **触发链**：带 secret 声明的 task（plan 2380 契约启用后）每次 saveStepState/saveTaskState 对接近或超过 4000 字符的载荷做 3 次全量物化后才丢弃/降级——高频 saveState（loop/fork 每迭代）下 GC 压力显著。
8. **声明上界与保证者**：列宽上限 4000/200/500 + degrade 机制保证**持久化结果**有界（plan 364 [01-04] 已对齐列宽）；物化过程无预算检查，无保证者（固有瞬态）。
9. **规模包络对照**：包络内载荷（KB 级）无感；与 C5-005 叠加时放大其 O(N²) 项的常数。
10. **峰值叠加**：单请求 3× 载荷 × 并发任务数。
11. **误报校准**：非静默（超长有 WARN）、持久化侧有界、瞬态可回收 → P3。
12. **修复方向**：secret 过滤前移到对象图（序列化前 strip）消除二次 parse；或 wrapper 先按字段分级序列化（resultValue 优先，与既有 degrade=resultValueOnly 顺序一致）实现短路的长度预算。
13. **覆盖度备注**：本条不否定 4000 字符守卫族（plan 364 [01-04] 修复本身正确）；仅指物化顺序。
- **复核状态**：已保留（P3。复核属实：先物化后判长（DaoTaskStateStore.java:579-581）+ filterSecretProps parse/重建/再序列化（:970-981，空集短路 :971-972 与原文"3× 仅在 secret 声明 task 上成立"一致）+ taskInputs/remark 同构路径（:182-224）均与引文一致；持久化侧列宽守卫（plan 364 [01-04]）有效、瞬态可回收，P3 恰当。现状：新发现——系对既有修复的物化顺序补充，非否定守卫族）

### [nop-wf-nop-task-C5-009] 参与者解析按角色/部门/组全量物化用户集合
1. **编号与标题**：C5-009 wf 参与者分配的用户集合全量物化。
2. **严重度**：P3（org 注册表规模内固有有界；优化候选）。
3. **缺陷族**：F7 变体（关系全量载入 vs 按需分页）。
4. **位置**：`nop-wf/nop-wf-service/src/main/java/io/nop/wf/service/actor/DaoWfActorResolver.java:108-150`（getDeptUsers/getGroupUsers/getRoleUsers → ORM 关系全量）+ `WfActorAssignSupport`（engine/WfActorAssignSupport.java:100-154 全量遍历构造 WfActorWithWeight）
5. **证据片段**：
   ```java
   // DaoWfActorResolver.java:116-118
   protected Collection<NopAuthUser> getRoleUsers(NopAuthRole role) {
       return role.getRelatedUserList();      // ORM 关系全量懒加载，无分页
   }
   ```
   ```java
   // DaoWfActorResolver.java:132 —— 延迟加载器仍指向全量集合
   actor.setUsersLoader(() -> buildUserActors(getGroupUsers(group)));
   ```
6. **驻留结构与增长源**：角色/部门/组全部成员用户实体 + 对应 WfUserActorBean 转换副本（buildUserActors 再 Collectors.toList 一份）。增长源 = org 成员数。
7. **触发链**：审批流步骤 assignment 配置 `actorType=role`（如全员角色 5 万人）× 每次 startWorkflow/转办重分配触发 getActors → 5 万 NopAuthUser + 5 万 WfUserActorBean 单请求驻留；usersLoader 的 lazy 包装只在"触发 containsUser 判断"时延后而非避免。
8. **声明上界与保证者**：org 注册表部署规模（天然有界、缓增）；无应用侧上界/缓存。
9. **规模包络对照**：包络内（org 数万级）单请求几十 MB 瞬态可接受；超大组织或高频大角色分配超包络。
10. **峰值叠加**：单请求 O(角色成员数) × 并发审批请求数（无共享 actor 缓存，每请求重算）。
11. **误报校准**：有界外部数据源、平台惯例（ORM 关系即全量语义）→ P3。
12. **修复方向**：assign 判定只需 count/contains 场景改走存在性查询；大角色引入 actor id 列表缓存（有界缓存 + 失效钩子）。
13. **覆盖度备注**：WfActorBean.usersLoader 为惰性但不去重不缓存；与 nop-auth 侧用户模型正交，不在本单元修。
- **复核状态**：已保留（P3。复核属实：getRoleUsers/getGroupUsers 走 `getRelatedUserList()`、getDeptUsers 走关系全量（DaoWfActorResolver.java:108-118 引文一致）、usersLoader 惰性但指向全量集合（:132/:148）、getAssignmentActors 逐 actor 构造 WfActorWithWeight（WfActorAssignSupport.java:100-135）核实；org 注册表部署规模天然有界，P3 恰当。现状：新发现）

### [nop-wf-nop-task-C5-010] 全局限流/信号量统计端点对无界注册表做全量快照拷贝
1. **编号与标题**：C5-010 getGlobalSemaphoreStats/getGlobalRateLimiterStats 全量拷贝随动放大。
2. **严重度**：P3（优化候选；依赖 C5-003 的键基数）。
3. **缺陷族**：F7 变体（导出端点无分页）。
4. **位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:217-233`
5. **证据片段**：
   ```java
   // TaskFlowManagerImpl.java:218-224
   public Map<String, ISemaphore.SemaphoreStats> getGlobalSemaphoreStats() {
       Map<String, ISemaphore.SemaphoreStats> ret = new HashMap<>();
       globalSemaphores.forEach((k, v) -> { ret.put(k, v.getStats()); });
       return ret;
   }
   ```
6. **驻留结构与增长源**：快照 HashMap 键值全集 + 每个 Stats 对象；增长源 = 注册表键基数（C5-003）。
7. **触发链**：静态 key 模型（≤ 数千键）下运维查询无感；动态 key 演化到 10^6+ 键后，一次 stats 拉取即物化百万级 map 并经 GraphQL 序列化再放大（F11 叠加）。
8. **声明上界与保证者**：无分页/无上限；与 C5-003 同源（键空间）。
9. **规模包络对照**：包络内（静态键）有界；随 C5-003 失控。
10. **峰值叠加**：单请求 O(注册表) × 并发监控拉取数。
11. **误报校准**：运维低频路径 → P3。
12. **修复方向**：分页/流式导出或聚合摘要（top-N + 总数）；随 C5-003 键空间治理一并收敛。
13. **覆盖度备注**：resetGlobalStats 仅遍历不物化，无此问题。
- **复核状态**：已保留（P3。复核属实：两 stats 方法全量 HashMap 拷贝（TaskFlowManagerImpl.java:218-233 引文一致）、键基数随 C5-003。复核补充事实：全仓库 grep 无生产调用者（仅接口声明/实现/测试命中，无 xbiz/GraphQL 暴露）——初审"经 GraphQL 序列化再放大"的触发链当前不可达，作为公共接口潜在隐患维持 P3 下限，修复优先级随 C5-003 键空间治理。现状：新发现）

---

## 覆盖度与残留边界

**已覆盖**（C5 五项检查点 100% 落到文件级证据）：

1. 随输入增长容器：nop-task-core 全部 26 个 step/wrapper、impl 3 个 runtime 类、state 2 个 bean、builder/metrics/utils；nop-wf-core engine/impl/store/support、nop-wf-dao store/dataobject、nop-wf-service actor/designer/entity、nop-wf-scheduler——每容器均给出上界判定（见清单表 + 有界备查段）。
2. 无界 API：全单元 `findAllByQuery/findAll` 命中 5 处逐一核实（3 处立案，2 处按主键/唯一键定位有界）。
3. 物化 vs 流式：DaoTaskStateStore 全部 5 个序列化锚点（taskInputs/remark/stateBeanData/errorBeanData/errorStack）核对，列宽守卫族（plan 364 [01-04]）确认有效，物化顺序问题立案 C5-008。
4. 缓存淘汰：全局注册表强引用 [05-04] 按声明口径评估键空间（C5-003）；ROW_LOCKS/TaskExceptionRegistry/meter 键空间核对有界。
5. 峰值叠加：逐条给出。

**缺陷族对照结论**：

- **F4（无界账本只增不删）**：DB 侧确认两处——(a) 单 wf 步骤/action/statusHistory/log 账本跨请求累积（C5-006 第 8 字段，受 wf 级 cascade-delete 约束，标注 F4-DB 受控子案；缺历史归档策略）；(b) WfTaskScanner 失败到期步骤 dueTime 不清除致扫描集不可收敛（并入 C5-001）。两者均为 DB 侧，受 DB 约束/可清理性已单独标注。内存侧未发现只增不删的全局账本（WorkflowImpl/runtime 均请求级）。
- **F7（无界集合/全量载入）**：C5-001/002（无 LIMIT 查询）、C5-004/005/006（全量物化）、C5-010（导出）。
- **F11（序列化中间体积膨胀）**：C5-008（三重物化）、C5-005（每迭代重序列化）；designer GraphDocumentCodec 与 xdef 模型编解码为模型级有界，不立案。

**维度集外线索（C6/C9 形态）核查结论**：**本轮未登记维度集外线索**。核查过的候选：(a) SUSPEND 路径跳过 runCleanup 使 task 级 beanContainer.stop 不执行（TaskImpl.java:154-161 注释声明的"保留给进程内 resume"裁定）——属已声明设计边界，且泄漏显性化依赖"task 模型使用 task-scoped bean 容器 + 容器内含线程类资源 + 挂起后进程内永不 resume"三重条件，未达 P0/P1 门槛，按 overlay 纪律（仅 P0/P1 登记）不立案，留作 C6 维度复核输入；(b) timeout/Delay 定时器三出口均显式 cancel，无泄漏；(c) LoopN 无界迭代（C5-007）的资源危害以 DB 写放大为主，已在本维度以边界缺陷立案，不重复登记 C9。

**残留边界（本轮未展开，转后续维度/轮次）**：

1. `NopWfInstance` 的 KvTableVarSet（globalVars/outputs）键空间 = 流程变量名集，理论上 listener/step 可写任意动态变量名——变量名有界性依赖模型作者纪律，未见构建期校验（候选下轮 C5 深化项，当前判 P3 级理论风险）。
2. nop-task-web / nop-wf-web 的 AMIS 页面与 GraphQL selection 投影大小未审计（平台 GraphQL 层职责）。
3. `ResourceComponentManager` 模型缓存容量与失效策略属平台设施（nop-core），本单元仅消费；其有界性由平台维度审计覆盖。
4. fork/fork-n 共享 stepPath 行的跨进程 resume 完整性为 owner 已声明边界（nop-task.md [已知边界]），与本维度正交，未重复评估。
5. `nop.task.max-global-*` 容量配置的 WARN-once 不可操作性在 C5-003 内提出修复方向，其告警通路有效性属可观测性维度。

**误报自我校准声明**：本报告 10 条发现均给出具体触发链或明确标注包络外放大；无"仅凭集合类型存在"式的泛化立案；已声明边界（强引用注册表 [05-04]、SUSPEND 不清理容器、fork 共享 stepPath）均按其声明口径评估而非直接立案。

---

## 维度复核结论（独立复核子代理）

- 复核人：C5 维度独立复核子代理（与第 1 轮 agent_260c53b9、第 2 轮 agent_2e028808 均非同一角色）
- 复核日期：2026-10-15；基线：concurrency-audit-remediation @ be0cbbb2a3（与初审同基线，受检文件无漂移）
- 复核方法：12 条发现逐条重读 live code 验证（文件/行号、引文、增长链/物化路径、平台侧行为、规模对照）；历史现状标注仅依据允许范围（ai-dev/audits/ 相关历史目录结论节、cra-index.md、git log）；未读 ai-dev/plans、bugs、lessons 及 C8 维度文件
- 判定总口径：P1/P2 分级线采用"进程生命周期持久单调增长 vs 请求级瞬态分配 vs 有界域放大"三级区分；每条以"被审计方可用什么证据反驳"作反驳性自检

### 逐条判定表

| 发现编号 | 原判级 | 判定 | 一句话理由 | 现状标注 |
|---|---|---|---|---|
| C5-001 | P1 | 保留 | 查询无 LIMIT + 平台 queryToSelectObjectSql 不消费 limit + 失败步骤 dueTime 全引擎无清除点（唯一 setDueTime 在激活时）均实证，毒步骤子集单调不收敛符合 P1 口径 | 新发现（同点位历史发现均为其他维度：P1-3.1-002 已修复 f1fa5cc888、P2-3.1-001/004 与扫描无界面无关） |
| C5-002 | P2 | 保留 | 端点无上限 + 逐条完整 executor 执行 + failedItems 无界累积实证；per-user 域有界、人工低频支持 P2 | 新发现（security-audit 2026-09-19 WF-01 仅涉同端点鉴权维度） |
| C5-003 | P1 | 保留 | 强引用永不驱逐 + WARN-once 不可操作 + keyExpr 逐次求值 + 输入直通键面均实证；动态 key 系特性主流用法且增长为进程生命周期持久单调，与 C5-004 瞬态分配的 P1/P2 分级线自洽 | 已知未修复（plan 364 [05-04] 声明边界明确接受 key 基数增长、仅 WARN 缓解、无上界保证者） |
| C5-004 | P2 | 保留 | count 无上限 + ArrayList 即时预分配实证；timeout 硬失败先例（TaskStepEnhancer:130-134）存在而 count 无同款；瞬态单请求 P2 正确 | 新发现 |
| C5-005 | P2 | 保留 | toList 物化入 stateBean + 每迭代 saveState 全量重序列化实证；round2 核实的写回缓解仅覆盖进程内 resume，跨 saveState 路径成立；单请求有界于输入支持 P2 | 新发现 |
| C5-006 | P2 | 保留 | 全集读路径族 + 10k 轮上限 + cascade-delete 边界全部实证；WorkflowImpl 每请求新建核实 | 新发现（10k 上限本身系 plan 344 既有修复 075561f891，本条立案其上的读放大面） |
| C5-007 | P2 | 保留 | LoopN 无 maxCount 与 Loop 非对称实证；负 step 反向计数与整数回绕无防护实证；条目自留"可降 P3"出口不启用（与 C5-004 同级一致） | 新发现 |
| C5-008 | P3 | 保留 | 先物化后判长 + secret 过滤二次 parse/serialize 实证（空集短路使 3× 仅在 secret 声明时成立，与原文一致）；持久化守卫有效、瞬态可回收，P3 恰当 | 新发现（对 plan 364 [01-04] 既有修复的物化顺序补充，非否定守卫族） |
| C5-009 | P3 | 保留 | 关系全量载入 + usersLoader 惰性不去重实证；org 域天然有界支持 P3 | 新发现 |
| C5-010 | P3 | 保留 | 全量 HashMap 拷贝实证；复核补充：全仓无生产调用者（仅接口/实现/测试），初审"GraphQL 导出放大"触发链当前不可达——作为公共接口潜在隐患维持 P3 下限 | 新发现 |
| C5-011 | P3 | 保留 | 站点无 limit + wf 级 N+1 + 无生产调用者均实证；站点计数勘误核实为 4 findAllByQuery + 2 findFirstByQuery = 6 站点（round2 分类措辞不精确但补站结论正确） | 新发现 |
| C5-012 | P3 | 保留 | LLM 键直写 + KV 全集懒加载 + 列宽定界实证；per-wf 域 + 双条件触发支持 P3 | 已知未修复（2026-09-22 code-review-full P2-3.1-011/AU-10 同点位安全维度未修复、无修复提交；C5 键空间面为新增维度） |

### 不确定项（显式列出）

1. **C5-001 remind 半区当前恒空集**：remindTime 引擎侧无写入点（grep 仅接口/bean setter 与 scanner 清 null），remind 扫描集当前恒为空（与历史 P2-3.1-004"remind 链三重死"一致）。P1 由 due 半区独立支撑，不影响判级；若日后修复 remind 链，同构风险即生效，"同构同修"修复方向维持。
2. **C5-003 的条件性 P1**：仅采用动态 keyExpr 的 global throttle/rate-limit 模型受影响；静态 key 模型不受影响。若 owner 裁定"动态 global key 为不支持用法"并落地构建期拒绝（修复方向 a），本条可降为文档声明缺口——条目自身已预告此出口，归并裁定阶段需 owner 表态。
3. **C5-010 触发链可达性**：初审未核查调用面，复核确认当前无生产调用者；维持 P3（公共接口潜在隐患）而非驳回（代码事实与接口面真实存在）。
4. **C8-001 归并对账**：C8 维度尚未复核，本复核按纪律未读 C8 文件；C5-001 按 P1 保留，若 C8-001 复核判级不同，应在 7.1 合并裁定阶段就扫描族统一定级，避免双计或分级冲突。

---

## 最终保留项

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状 |
|---|---|---|---|---|
| C5-001 | P1 | `nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/store/DaoWorkflowStore.java`（+ `nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java`） | 到期/提醒扫描 findAllByQuery 无 LIMIT 全量载入单会话，失败步骤 dueTime 永不清致毒步骤每分钟重入扫描集、集合不可收敛 | 新发现 |
| C5-002 | P2 | `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/service/impl/WorkflowServiceImpl.java`（+ `nop-wf/nop-wf-dao/.../DaoWorkflowStore.java:170-178`） | transferActorsAsync 批量转办无条数上限，逐条完整 executor 执行 + failedItems 全量驻留返回 | 新发现 |
| C5-003 | P1 | `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java`（+ `ThrottleTaskStepWrapper.java`/`RateLimitTaskStepWrapper.java`） | 全局限流/信号量强引用注册表永不驱逐 × 动态 keyExpr（含调用方输入直通）→ 进程生命周期键空间持久无界增长，WARN-once 不可操作 | 已知未修复（plan 364 [05-04] 声明边界） |
| C5-004 | P2 | `nop-task/nop-task-core/src/main/java/io/nop/task/step/ForkNTaskStep.java` | fork-n count 自表达式无上限校验且 ArrayList 按值即时预分配，外部输入驱动时为单请求 OOM 通道 | 新发现 |
| C5-005 | P2 | `nop-task/nop-task-core/src/main/java/io/nop/task/step/LoopTaskStep.java`（+ `ForkTaskStep.java`） | loop/fork items 全量物化驻留整个循环期，且每迭代 saveState 对含全量 items 的 wrapper 重序列化（累计 O(N²) 序列化量） | 新发现 |
| C5-006 | P2 | `nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/store/DaoWorkflowStore.java`（+ `nop-wf/nop-wf-core/.../AbstractWorkflowStore.java`） | wf 步骤记录读路径经 ORM 关系全集物化（无 SQL 侧过滤/下推），单 wf 步骤账本跨请求无归档无上界 | 新发现 |
| C5-007 | P2 | `nop-task/nop-task-core/src/main/java/io/nop/task/step/LoopNTaskStep.java` | loop-n 迭代计数无 maxCount 上界（与 Loop 非对称），负 step 反向计数/整数回绕未防护，每迭代 2 条 SQL 写放大 | 新发现 |
| C5-008 | P3 | `nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java` | 状态序列化先全量物化再判列宽 + secret 过滤二次 parse/serialize（secret 声明 task 上瞬态 3× 载荷） | 新发现 |
| C5-009 | P3 | `nop-wf/nop-wf-service/src/main/java/io/nop/wf/service/actor/DaoWfActorResolver.java` | 参与者解析按角色/部门/组 ORM 关系全量物化用户集合，usersLoader 惰性但不减量 | 新发现 |
| C5-010 | P3 | `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java` | getGlobalSemaphoreStats/getGlobalRateLimiterStats 对注册表做全量 HashMap 拷贝（当前无生产调用者，随 C5-003 键基数失控） | 新发现 |
| C5-011 | P3 | `nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/store/DaoWorkflowStore.java:209-213`（+ `nop-wf/nop-wf-core/.../IWorkflowStore.java:124-135`） | getActionRecords 单步无 LIMIT 全量载入，wf 级 default 方法对全集步骤 N+1 逐条查询（无生产调用者，模型脚本可达） | 新发现 |
| C5-012 | P3 | `nop-wf/nop-wf-ai/src/main/java/io/nop/wf/ai/WfAiHelper.java` | AI decide 将 LLM 输出任意键直写 wf 全局变量 KV 表，键空间无界且无 schema 门禁，读路径全集放大 | 已知未修复（2026-09-22 AU-10/P2-3.1-011 同点位安全面） |

**复核计数**：12 条全部保留（无降级、无驳回）；P1×2（C5-001、C5-003）、P2×5（C5-002/004/005/006/007）、P3×5（C5-008/009/010/011/012）。
