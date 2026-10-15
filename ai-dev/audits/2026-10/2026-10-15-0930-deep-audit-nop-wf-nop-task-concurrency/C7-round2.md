# C7 长时运行稳定性维度第 2 轮深挖（盲审）— nop-wf + nop-task

- 审计单元：`nop-wf`（10 jar）+ `nop-task`（10 jar）
- 审计人：并发审计子代理（C7 维度，第 2 轮深挖）
- 日期：2026-10-15
- 仓库根：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation）
- 盲审纪律：未读取 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何文件，唯一例外为审计派单指定的本维度第 1 轮文件 `C7-long-running.md`（必要输入，完整读取）；未读取输出目录中其他维度文件。
- 输入基线：第 1 轮发现 C7-001~013（P1×1 / P2×11 / P3×1）+ 挂死清单 H1~H7 + 增长源清单 G1~G7；grep 基线 `_tmp/cra-baseline-nop-wf-nop-task.txt`；owner docs（docs-for-ai/03-modules/nop-wf.md、nop-task.md）。

## 本轮深挖目标（按派单指令）

1. C7-001 失效面扩展：fencing 缺失的其他多节点敏感面（task 引擎多实例部署、DaoTaskStateStore 条带锁跨 JVM 无效性声明核对）。
2. 日志放大专项：循环/每扫描周期/每任务路径上的 WARN/ERROR 无抑制同族清扫（C7-002 同族）。
3. 自愈清理面：任务/流程终态后的旧运行时注册清理（TaskRuntimeImpl 运行时表移除、step wrapper 残留）。
4. C7-009 workerId 失效面同族扩展：其他「崩溃后状态不可恢复」的内存-DB 不一致面。
5. 第 1 轮 4 项暂缓主题（跨进程 resume、join 滞留 H7 等）中归属本维度的可达性评估。

---

## 1. 本轮发现

### [nop-wf-nop-task-C7-014] task 引擎无执行级 fencing：同一 taskInstanceId 并发 resume 双执行，进程内即触发（C7-001 同族扩展）

1. **发现 ID**：nop-wf-nop-task-C7-014
2. **严重度**：P2（可具体化竞态；进程内可达，无需多节点）
3. **缺陷族/维度项**：C7-4 自愈语义/fencing（C7-001 失效面向 task 引擎的扩展；F2 半个成功近邻）
4. **位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:121-134`（getTaskRuntime 每次新建 runtime，无实例级注册表/互斥）；`nop-task/nop-task-core/.../impl/TaskImpl.java:98`（resume 仅终态短路，非终态不设防）、`:186,202,237,252`（四个终态 driver 的 `synchronized(taskState)`）；`nop-task/nop-task-dao/.../store/DaoTaskStateStore.java:331-374`（条带锁注释自述仅进程内行写串行化）；子任务链 `nop-task/nop-task-core/.../step/CallTaskStep.java:87-89`
5. **证据片段**：
```java
// TaskFlowManagerImpl.java:121-128 —— 每次 resume 新建 runtime + 从 DB load 独立 TaskStateBean，
// 无任何 per-taskInstanceId 的在途标记或互斥
public ITaskRuntime getTaskRuntime(String taskInstanceId, IServiceContext svcCtx, IEvalScope scope) {
    ITaskStateStore stateStore = requirePersistStateState();
    TaskRuntimeImpl taskRt = new TaskRuntimeImpl(this, stateStore, svcCtx, scope, true);
    ITaskState taskState = stateStore.loadTaskState(taskInstanceId, taskRt);
```
```java
// TaskImpl.java:98 —— resume 唯一防线：仅终态短路；ACTIVE/SUSPENDED 实例并发 re-enter 双双放行
if (taskRt.isRecoverMode() && taskState.isTerminal()) { ... }
```
6. **增长/劣化链**：宿主经 `getTaskRuntime(taskInstanceId)` + `execute` resume 同一挂起实例（owner doc 声明的唯一恢复入口）× 双击重提交/重复投递/超时后重试触发两个并发请求 × 后果：两个 runtime 各持独立 TaskStateBean，`synchronized(taskState)`（TaskImpl 四个终态 driver 的唯一互斥）监视器不同 → **mainStep 从持久化位置并发重跑，业务步骤体双执行**（副作用翻倍）；两条执行线的 `saveStepState` 交叠（条带锁把同 JVM 行写串行化为 last-writer-wins，注释 :331-338 自认），step 行的 stepStatus/stateBeanData/bodyStepIndex 在两条执行线间翻转；两条线的终态 driver 均能通过各自的 `skipTerminalOverwrite` 判定，task 行最终归属取决于写序（DB version 列提供窄窗口的乐观锁失败 ERR_ORM_UPDATE_ENTITY_NOT_FOUND，或读-写窗口内静默 last-writer-wins）——终态与 step 行可能来自不同执行者的混合状态。多节点部署同面放大（C7-001 同构：跨 JVM 连条带锁都没有）。时间尺度：每次并发 resume 即可能发生；长挂起任务（C7-010 滞留面）上恢复窗口以天计，双触发概率随滞留时长累积。
7. **影响**：业务步骤副作用双跑（扣减/通知/外部调用）；DB 状态行混合一致（resume 后再崩溃时断点数据不可信）；一方终态驱动可能收到与任务语义无关的 ORM 异常，掩盖真实结果。
8. **判定依据**：全模块 grep 无任何 per-instance 在途注册表/锁（`synchronized` 清点仅 TaskImpl 终态 driver、TaskRuntimeImpl.cancel:97、条带锁、模型级 lazy-init）；TaskImpl 的 resume-skip 短路证明引擎预期 re-enter 场景，但只处理了顺序（终态后）re-enter，未处理并发 re-enter。
9. **误报筛查**：不属"宿主责任"豁免——getTaskRuntime 是 owner doc 声明的唯一恢复入口，文档未警示并发 resume；且 `saveStepState` 的条带锁与 plan 364 [05-02] 的"进程内并发已串行化"表述只覆盖**行写**原子性，易造成"进程内并发安全"的错觉。条带锁与乐观锁均为事后兜底，均不阻止双执行本身。
10. **已声明边界对照**：owner doc（nop-task.md「挂起与恢复」已知边界）声明的是**跨进程** fork+DB-resume 不可靠；「进程内同 stepPath 行的并发 saveStepState 已由条带锁串行化」声明的是**行写**粒度。**进程内执行级双跑未被声明覆盖**。
11. **修复方向**：TaskFlowManagerImpl 维护 per-taskInstanceId 在途标记（ConcurrentHashMap.putIfAbsent + runtime 终态时移除），并发第二者快速失败 ERR_TASK_INSTANCE_ALREADY_RUNNING；或 DB 条件 UPDATE 抢占执行权（status+SUSPENDED→ACTIVE 带 owner），与 C7-009 的 workerId 赋值修复同源。
12. **检测可见性**：双执行期间 step 行状态翻转可从 updateTime 高频变化间接察觉；无专门指标/日志；终态 ORM 异常在调用方可见但语义失真。
13. **覆盖备注**：第 1 轮 C7-001 只登记了 wf 扫描面（WfTaskScanner）的 fencing 缺失；本条补齐 task 引擎面，并指出其**进程内**暴露（比 C7-001 的多节点前置更宽）。CallTaskStep:87-89 的子任务 resume 路径使父任务重试/并发时同一子实例被二次 getTaskRuntime，是本条的传播面之一。
14. **复核状态**： 已保留（P2——双执行链逐环证实：`getTaskRuntime` 每次 new TaskRuntimeImpl + 独立 `loadTaskState`（TaskFlowManagerImpl:121-134，全模块无 per-taskInstanceId 在途标记/注册表）；resume 仅终态短路（TaskImpl:98，非终态 re-enter 双双放行）；四个终态 driver 的 `synchronized(taskState)` 监视器随各 runtime 独立 TaskStateBean 对象隔离失效（:186/202/237/252）；条带锁注释自认仅进程内行写串行化（DaoTaskStateStore:331-338）；CallTaskStep:87-89 子实例二次 getTaskRuntime 传播面成立。owner doc 声明的跨进程边界与行写粒度均不覆盖进程内执行级双跑，对照准确；平台自身无 GraphQL resume 入口（NopTaskInstanceBizModel 未暴露），触发面为宿主并发 resume（双击/重复投递），与初审前置描述一致。判级自洽：后果为单实例业务步骤双跑（有界、无账本增长），符合 campaign 校准线（罕见竞态后果无界者才入 P1，WI 1.4 复核先例）。现状：新发现）

### [nop-wf-nop-task-C7-015] global 限流器/信号量作用域实为 per-JVM：多节点部署下限额按节点数线性放大，语义未声明

1. **发现 ID**：nop-wf-nop-task-C7-015
2. **严重度**：P3（声明缺口/部署敏感；单节点部署无影响，无正确性数据损坏）
3. **缺陷族/维度项**：C7-4 fencing/多节点敏感面（C7-001 同族扩展——「global」资源的集群语义）；C7-1 近邻（保护失效型劣化）
4. **位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:63-64,175-207`（`globalRateLimiters`/`globalSemaphores` 为普通 ConcurrentHashMap 实例字段）；模块依赖 `nop-task/pom.xml`、`nop-task/nop-task-core/pom.xml`（无 nop-cluster 依赖，grep 核对）
5. **证据片段**：
```java
// TaskFlowManagerImpl.java:63-64 —— bean 单例字段，生命周期=JVM
private final ConcurrentHashMap<String, IRateLimiter> globalRateLimiters = new ConcurrentHashMap<>();
private final ConcurrentHashMap<String, ISemaphore> globalSemaphores = new ConcurrentHashMap<>();
// :198-203 —— global=true 走 JVM 内注册表；无任何跨节点协调入口
if (global)
    return globalSemaphores.computeIfAbsent(taskRt.getTaskName() + ":" + key,
            k -> { ...; return new DefaultSemaphore(maxPermits); });
```
6. **增长/劣化链**：任务模型配置 `global=true` 的 rate-limit/throttle（意图通常是保护下游系统：maxConcurrency=N / N rps）× 部署 k 个节点 × 后果：每节点各持一份独立 permit 池/速率配额，**下游实际承受的并发/速率上限 = k×N**；节点数随扩容增长，保护强度等比例稀释，且无任何配置告警或文档提示。与 C7-008 的注册表增长问题正交：本条是"global 的语义边界"，非内存问题。时间尺度：每次水平扩容即劣化一档，长期运行中随扩容次数累积。
7. **影响**：下游保护型配置（限流/限并发）在多节点部署下静默失效至按节点数放大；容量规划基于"global=全局"的字面语义会系统性低估下游压力。
8. **判定依据**：`DefaultRateLimiter`/`DefaultSemaphore` 为纯 JVM 内存实现；nop-task 无 nop-cluster 依赖，全模块无分布式协调调用；名称与文档（owner doc："以 taskName:key 为共享单元，刻意跨租户共享"）均只声明了**跨租户**维度，未声明**跨节点**边界。
9. **误报筛查**：不把"未内置分布式信号量"当缺陷（实现成本与依赖是合理取舍）；本条针对"global 命名+文档均未披露 per-JVM 边界，配置者无从得知语义"。单节点下行为正确，故不判 P2。
10. **已声明边界对照**：跨租户共享=已声明（刻意设计）；per-JVM 作用域与多节点放大=未声明。
11. **修复方向**：owner doc 补一句"global 为节点本地配额，多节点部署需按节点数折算配置值"；或接入 nop-cluster 提供可选的分布式实现（长期）。
12. **检测可见性**：完全静默——每节点各自统计（stats 端点亦为节点本地，见 C7-008 的"无生产消费者"），无聚合视图可发现限额被放大。
13. **覆盖备注**：与 C7-008（同注册表的内存增长/warn-once）合并构成 global 门面的完整审计面；两者修复可同批落地（文档+指标）。
14. **复核状态**： 已保留（P3——global 注册表为 bean 实例字段即 JVM 作用域（TaskFlowManagerImpl:63-64、:197-207）、DefaultRateLimiter/DefaultSemaphore 纯 JVM 内存实现、nop-task 全部 pom 无 nop-cluster 依赖（grep 证实）；owner doc（nop-task.md:33）仅声明"刻意跨租户共享"，per-JVM 边界与多节点限额按节点数放大未声明——命名误导与文档缺口成立；单节点行为正确、无数据损坏，P3 恰当。现状：新发现）

### [nop-wf-nop-task-C7-016] kill 与挂起转移竞态：SUSPEND 写入无 first-terminal-wins 守卫且与 cancel 的判定不互斥，kill 可静默丢失

1. **发现 ID**：nop-wf-nop-task-C7-016
2. **严重度**：P3（窄窗口竞态；可恢复但静默，无数据损坏）
3. **缺陷族/维度项**：C7-4 自愈语义（kill 幂等性/终态守卫不对称；与 C2/C4 状态机竞态近邻，因主体是 kill 语义而归属本维度）
4. **位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskImpl.java:153-161`（SUSPEND 写入裸露：无 synchronized、无 skipTerminalOverwrite）；`nop-task/nop-task-core/.../impl/TaskRuntimeImpl.java:94-105`（cancel 仅在 `status==SUSPENDED` 时驱动 KILLED）；对照 `:186,202,237,252`（四个终态 driver 均 `synchronized(taskState)`+守卫）；`nop-task/nop-task-core/.../step/SuspendTaskStep.java:30-35`（首入不检查 cancel token）
5. **证据片段**：
```java
// TaskImpl.java:154-160 —— SUSPEND 写入：无 synchronized(taskState)、无终态守卫
if (err == null && ret != null && ret.isSuspend()) {
    taskState.setTaskStatus(TaskConstants.TASK_STATUS_SUSPENDED);
    taskRt.saveTaskState();
    return ret;
}
```
```java
// TaskRuntimeImpl.java:97-99 —— kill 侧：读-判定-写三段仅在「已挂起」时收敛；
// status 仍为 ACTIVE 时仅设 cancel token 即返回
synchronized (state) {
    Integer status = state.getTaskStatus();
    if (status != null && status == TaskConstants.TASK_STATUS_SUSPENDED && !state.isTerminal()) {
```
6. **增长/劣化链**：kill 恰好落在「步骤边界 token 检查（TaskStepExecution:271）通过之后、SUSPEND 状态落库（TaskImpl:159-160）之前」的窗口 × 执行序：①executor 通过 token 检查 → ②kill 线程 cancel()：读 status=ACTIVE → 仅设 token 返回（**kill 调用无任何异常/告警**）→ ③executor 侧 SuspendTaskStep 首入返回 SUSPEND（自身不查 token）→ SUSPENDED 落库 × 后果：运维 kill 已"成功"返回，DB 终态为 SUSPENDED——kill 静默丢失，任务保持可 resume 态；无日志、无指标、KILLED 永不写入。时间尺度：窗口为毫秒级，但 kill 正是"任务看起来不对劲"时的操作，与挂起转移的相遇概率随挂起型任务的存量（C7-010 滞留面）上升。二次 kill 可收敛（此刻 status 已是 SUSPENDED，会驱动 KILLED），前提是运维发现 kill 未生效。
7. **影响**：终态收敛语义出现"已确认但未生效"的运维操作；审计线上 kill 动作与实际状态不一致；无数据损坏。
8. **判定依据**：kill 侧 check-then-act（读 status → 判定 ==SUSPENDED）与 suspend 侧无锁写入构成竞态对；四个终态 driver 均收敛到 `synchronized(taskState)`+`skipTerminalOverwrite` 原子序列（plan 364 [05-01/02-12]），唯独 SUSPEND 这一非终态但决定 kill 走向的写入是裸露的——守卫体系不对称。
9. **误报筛查**：非"cancel 语义未实现"（SUSPENDED 态的 kill 路径 plan 364 [05-03] 已正确实现，第 1 轮亦正面确认）；本条仅针对 ACTIVE→SUSPENDED 转移瞬间与 kill 的交错窗口。异步聚合路径中 SUSPEND 与 TIMEOUT 的竞速已被 TaskStepHelper.timeout 的 winner 互斥覆盖（先完成者独占），无双重驱动。
10. **已声明边界对照**：plan 364 [05-03] 声明了"SUSPENDED 直接驱动 KILLED；ACTIVE 走 token→step driver 路径"，未声明两态边界处的竞态窗口。未覆盖。
11. **修复方向**：SUSPEND 写入纳入 `synchronized(taskState)` 并在写入前复查 cancel token / isTerminal（与终态 driver 同型原子序列）；cancel() 侧则在设 token 后重查一次 status，若已转 SUSPENDED 再驱动 KILLED。
12. **检测可见性**：完全静默（两侧均无日志）；只能靠"kill 后状态仍非终态"的事后核对发现。
13. **覆盖备注**：第 1 轮挂死清单将"kill 对 SUSPENDED 直接终态驱动"列为正向确认（C7-010 覆盖备注）；本条登记其相邻的转移窗口缺陷，不推翻该确认。
14. **复核状态**： 已保留（P3，附同点判级对账——SUSPEND 写入裸露（TaskImpl:154-160，无 synchronized、无 skipTerminalOverwrite 守卫）× cancel 仅 `status==SUSPENDED` 时驱动 KILLED（TaskRuntimeImpl:97-105，ACTIVE 时仅设 token 返回）的竞态对逐行证实，kill 静默丢失、二次 kill 可收敛的链成立。cross-ref：与 C1-001（C1 复核保留 P1，含 TeamTaskFlowOrchestrator orTimeout 线程具体化）、C2-001（C2 复核保留 P1）为同一代码点（TaskImpl:159 × TaskRuntimeImpl:97-105）的维度视角；按复核指令，同点 P1 vs 本条 P3 的判级差异留 7.1 统一（归并应取各维最高级 P1，本条 P3 视角登记不构成覆盖缺口）。自洽性说明：按 WI 1.4 复核确立的校准线（罕见竞态后果无界者才入 P1、后果有界→P2），本条独立定级应为 P2 下限而非 P3，已列维度复核结论不确定项。现状：新发现）

### [nop-wf-nop-task-C7-017] global 限流器参数固化告警为每调用一条无抑制 WARN：配置漂移态永久持续直至重启（C7-002 日志放大同族）

1. **发现 ID**：nop-wf-nop-task-C7-017
2. **严重度**：P3（日志放大；触发需配置漂移这一前置，但一旦进入则无界）
3. **缺陷族/维度项**：C7-1 日志放大（C7-002 同族清扫第二例——告警机制本身成为放大源）
4. **位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:183-189`（frozen 告警）；对照 `:209-215`（warnIfOverCap 为每注册表一次）；对称面 `:197-207`（getSemaphore 无对应告警）
5. **证据片段**：
```java
// TaskFlowManagerImpl.java:185-188 —— 同 key 二次配置不同速率时，每次调用都 WARN
if (Math.abs(limiter.getPermitsPerSecond() - requestPerSecond) > 1e-9) {
    LOG.warn("nop.task.global-rate-limiter-config-frozen:cacheKey={},configuredPerSecond={},activePerSecond={}",
            cacheKey, requestPerSecond, limiter.getPermitsPerSecond());
}
```
6. **增长/劣化链**：管理员调低某 global 限流步骤的 requestPerSecond（模型已更新、注册表中旧 limiter 固化——plan 349 Phase 5 的强引用不可驱逐语义）× 之后该步骤的**每一次执行**都命中差值判定并输出一条 WARN × 时间尺度=每调用 × 后果：100 QPS 的步骤 ≈ 864 万条 WARN/天，直至进程重启注册表重建为止；日志量淹没该 key 的其他输出，log rotation 压力同 C7-002。根因是"漂移态永久持续"与"每调用告警"的组合：告警本意（plan 349：避免参数调优静默不生效）正确，但缺少抑制/升级机制使信号退化为噪声——而噪声恰恰掩盖"新速率从未生效"这一需要人工介入的事实。
7. **影响**：日志放大；真实生效参数与配置的偏离被海量重复告警钝化为背景噪声。
8. **判定依据**：告警路径无去重/限频/计数（对照同文件 warnIfOverCap 的 `compareAndSet` 一次性语义——同注册表内两套告警策略不一致）；漂移态无自愈路径（重启前新配置永不生效）。
9. **误报筛查**：不判缺陷级——告警内容本身是正确且必要的诊断信息（半声明：代码注释明示意图）；本条按 C7-002 同族口径登记其无抑制面。不属信息性日志豁免（WARN 级 × 每调用 × 无界时长）。
10. **已声明边界对照**：代码注释声明了告警示意，未声明其每调用触发特性与漂移态的持续时长。未覆盖。
11. **修复方向**：告警去重（每 cacheKey 首次 + 周期性重发，如每 N 次/每小时一次）；或提供注册表参数热更新入口使新配置可生效（根治漂移态）。
12. **检测可见性**：告警本身可见（过度可见）；无"漂移 key 数/持续时长"聚合指标。
13. **覆盖备注**：对称缺口：`getSemaphore`（:197-207）无 frozen 告警——并发数配置同样固化但**完全静默**（与 rate-limit 的过度告警恰成两极）；修复时应一并补齐。第 1 轮 C7-008 影响字段曾顺带提及此 WARN 可能刷屏，本条将其独立立案并补齐 semaphore 静默侧。
14. **复核状态**： 已保留（P3——frozen WARN 每调用触发、无去重/限频/计数（TaskFlowManagerImpl:185-188）、同注册表 warnIfOverCap 为 compareAndSet 一次性（:209-215）、getSemaphore 无 frozen 告警（:197-207）三点均证实；漂移态持续至重启+每调用 WARN 的放大链成立；触发需配置漂移前置，P3 恰当。现状：新发现）

---

## 2. 排除项/定向核查

### 2.1 定向核查（派单深挖点逐项结论）

| # | 核查点 | 结论 | 依据 |
|---|--------|------|------|
| K1 | **DaoTaskStateStore 条带锁跨 JVM 无效性声明核对** | **声明准确，无新发现**。条带锁为 JVM 内静态 Object[]（`:339-352`），跨 JVM 无效；注释（`:331-338`）与 owner doc（nop-task.md「已知边界」）均明示"跨进程并发写仍属 DB 断点续跑完整性主题（暂缓）"。DB 侧 `nop_task_instance`/`nop_task_step_instance` 均带 `versionProp="version"`（nop-task.orm.xml:159,262），`updateEntityDirectly`→`orm().updateDirectly`→persister `checkUpdateResult`（EntityPersisterImpl:508-521）对 0 行更新抛 ERR_ORM_UPDATE_ENTITY_NOT_FOUND——跨 JVM 并发写有乐观锁最后防线（响亮失败而非静默丢失）。行级之外的双执行面已由 C7-014 立案。 | DaoTaskStateStore.java:331-374；EntityPersisterImpl.java:508-521 |
| K2 | **task 引擎多实例部署敏感面** | C7-014（执行级 fencing）+ C7-015（global 门面 per-JVM）两案覆盖；无其他独立暴露面（task 引擎无自带的扫描/调度 job，无 WfTaskScanner 类的多节点重复触发面；nop-task-queue 的 ITaskQueue 仍为空壳接口，无调度实现——C7-009 结论复核成立）。 | grep nop-task pom 无 nop-cluster；ITaskQueue 单方法接口 |
| K3 | **日志放大同族清扫** | 全部 79 个 LOG.warn/error/info 站点清点完毕，新立案 1 例（C7-017），其余排除，见 2.2 表。 | grep 清单比对 |
| K4 | **终态后运行时注册清理（TaskRuntimeImpl/step wrapper 残留）** | **无发现（正向确认）**。①`TaskRuntimeImpl.attrs`/scope/cleanups 均为每调用生命周期，`runCleanup` 在 COMPLETED/FAILED/异步终态出口（TaskImpl:143,163）与 cancel 驱动路径被调用；②父→子 cancel 监听（newChildRuntime:167-175）在子任务 cleanup 时注销，挂起子任务保留监听属必要语义；③`addStepCleanup/runStepCleanups`（TaskStepRuntimeImpl:181-195）**全仓零生产调用方**（仅测试 fake 实现）——step wrapper 残留面不存在（死 API，登记为代码卫生备注，非稳定性问题）；④Throttle permit：`whenComplete` 对同步返回即时回调（TaskStepReturn:278-284）、异步返回挂接 promise 终结，suspend/失败/取消均释放，`tryAcquire(1,maxWait)` 有界等待；⑤Delay 步骤定时器经 `bindCancelToken` 取消联动，到期空任务自清理；⑥Timeout 定时器同步快路径与 winner 链双路取消（第 1 轮已核，本轮复核）；⑦wf 侧 WorkflowImpl/WorkflowManagerImpl 每调用新建（WorkflowManagerImpl:81-88），无运行时注册表，commandQueue 随实例回收。 | TaskImpl.java:143,163；TaskStepReturn.java:278-284；WorkflowManagerImpl.java:81-88 |
| K5 | **C7-009 同族：崩溃后内存-DB 不一致面** | 新立案 C7-016（kill 竞态属此族的状态收敛面）；另核查两项排除：①`TaskStateBean.nextRunId` 不持久化（`:26,91-93`），崩溃重启后 runId 从 0 重计与存量 step 行 runId 重叠——核对全部消费者后确认 runId/parentRunId 仅入日志与实体 round-trip，无任何决策/匹配逻辑引用（findStepEntity 按 taskInstanceId+stepPath 定位），纯诊断字段，**排除**；②call-task 子实例 INSERT（newTaskState 即时落盘）与父指针 saveState（CallTaskStep:85-86）之间崩溃 → 孤儿 ACTIVE 子实例——默认同事务部署下窗口随回滚关闭，无事务部署下被 owner doc 已声明边界（"步骤业务副作用与终态保存之间无事务原子性，步骤体必须幂等或用 TransactionTaskStepDecorator"）覆盖，且其"滞留无看门狗"根因即 C7-009，不另立。 | TaskStateBean.java:91-93；CallTaskStep.java:81-90；owner doc nop-task.md 已知边界段 |
| K6 | **第 1 轮暂缓主题可达性评估：H7 join 滞留** | **链已具体化，维持登记不立案**。触发链收紧为：and-join J 的**全部**上游分支经不产生后继步骤的出口退出（`_killStep`:1100-1102 / `exitStep`:1910-1927 / doTransition TO_EMPTY——三者均只调 `checkEnd`，不触发 `checkWaitingJoinStep`）→ J 的 wait 集 isHistory 满足但无人重查 → J 永久 WAITING、流程实例永不结束（checkEnd 的 auto-end 条件被 J 卡住）。重查仅由三入口驱动：新上游步骤进入 join 复用分支（:358,:421）、signal 变化（:752-757→:766）、手动 `triggerWaiting`（:1117，grep 确认无引擎内自动调用方）。维持不立案理由：①需 and-join+全上游无后继退出的配置组合，正常完成路径（transitionToStep→join 复用）会正确驱动重查；②后果为"流程不结束"的滞留可见型（DB/管理端可见 step WAITING），无日志/资源放大；③修复归属与 C7-003 的滞留看门狗方向重合（扫描面应一并覆盖 WAITING join）。若未来实施 due-backlog 看门狗，应将 `status=WAITING AND joinType!=null AND 全上游 history` 纳入扫描条件。 | WorkflowEngineImpl.java:491-520,752-766,1100-1128,1910-1927 |
| K7 | **第 1 轮暂缓主题：跨进程 resume 完整性** | 维持 owner doc 暂缓裁定；其检测盲区已由第 1 轮 C7-009 立案。本轮补充：进程内（非跨进程）的并发 resume 缺口不被该裁定覆盖，已独立为 C7-014。 | — |
| K8 | **LoopTaskStep 残余面** | 排除一项理论风险：迭代终态 saveState（:157）持久化 `index==size` 后、循环退出前崩溃 → resume 时 `:141 get(index)` 越界（:130 的 isEmpty 防线与 :186 shouldContinue 防线均不覆盖 index==size 的再进入）。窗口为毫秒级崩溃窗口，后果为该任务 FAILED（非挂死、无放大），按"写不出稳定链降级"登记为理论风险，不立案。 | LoopTaskStep.java:130-141,155-157,186 |

### 2.2 日志放大同族清扫明细（除 C7-017 外全部排除）

| 日志站点 | 级别 | 触发粒度 | 排除理由 |
|---|---|---|---|
| WfTaskScanner `due-action-failed` ERROR ×2 / `remind-task-failed` ERROR | ERROR | 每扫描周期×每滞留项 | 即 C7-002 本体（remind 侧第 1 轮已并入），非新发现 |
| WfTaskScanner `due-action-blocked-by-user-check` WARN | WARN | 每周期（潜在） | 代码注释明示"仅剩防御意义"（P1-3.1-002 后 scanner 携系统标记放行），仅竞态下触发；触发即说明存在 C7-001 面的竞态，低频防御分支豁免 |
| DaoWfActorResolver `user/role/group/dept-not-found` INFO ×4 | INFO | 每参与者解析失败 | 每业务动作至多一次，随数据修复收敛；信息性日志豁免 |
| TaskExceptionRegistry 7 处 WARN | WARN | 每持久化异常重建失败 | 仅 resume 载入时触发、有 generic 回退，低频路径豁免 |
| WorkflowCoordinatorImpl `skip-notify-subflow-end-since-parent-missing` WARN | WARN | 每子流程结束（父缺失时） | 每子流程至多一次，有界；信息合理 |
| TaskStepHelper `retry-attempt-persist-failed` ERROR ×2 | ERROR | 每重试轮次落盘失败 | 有界（重试次数封顶）且立即 rethrow 不静默，失败级联场景下非首要噪声源 |
| TaskStepExecution `step.run-fail` INFO ×2 | INFO | 每步骤失败（含每重试轮） | 有界（重试封顶）；失败可见性是必要信号 |
| TaskStepExecution `step-input/step-output` dump INFO | INFO | 每步骤（dump=true 时） | 显式 opt-in 调试配置，声明行为 |
| DaoTaskStateStore 序列化降级 WARN 族（state-data-too-long/serialize-failed 等） | WARN | 每次落盘降级 | 有数据丢失告警职责，且触发前提是单条数据超限，非周期性放大 |
| TaskImpl `terminal-state-overwrite-blocked` WARN | WARN | 每次竞态拦截 | 低频（first-terminal-wins 拦截本身罕见），且是正确性告警 |
| TaskFlowManagerImpl `global-gate-registry-over-cap` WARN | WARN | 每注册表一次 | warn-once 语义已由 C7-008 登记，非新发现 |

### 2.3 其他核查后排除项

| 项 | 结论 |
|---|---|
| TaskStepExecution SUSPEND 出口（:301-313） | 挂起状态保存路径同步单线程，无并发写面；saveState=false 步骤的挂起退化为重执行是声明语义（plan 364 [03-05]） |
| AbstractForkTaskStep 聚合 | buildAggResult 对未完成分支以 ERR_TASK_CANCELLED 占位（:128-132），无 promise 泄漏；分支共享 stepPath 行为已声明边界 |
| TaskStepReturn.whenComplete 同步语义 | :278-284 非异步返回即时回调——Throttle release 不漏（详见 K4-④） |
| WorkflowManagerImpl.removeModelCache | 缓存失效路径正确（先基路径后版本路径），与 C7 无关 |
| DaoWorkflowStore save* 族 | saveOrUpdateEntity 走 ORM version 乐观锁（nop-wf.orm.xml:181,352 versionProp），与 C7-001 第 1 轮"后到者被拒绝"结论一致，无新增 |
| findRemindActivatedSteps（:189-194） | remindTime 成功后置 NULL，`le` 谓词对 NULL 不匹配——成功提醒不重扫，无放大 |
| WorkflowImpl.delayExecute/executing 标志 | 非volatile `executing` 至多导致双线程并发 drain，`commandQueue.poll()` 原子性保证命令单次执行；实例每调用新建，无长尾 |
| 第 1 轮残留疑点#5（挂起任务保留 task 级 beanContainer） | 维持残留疑点定性（未发现持有线程资源的实证任务级 bean 配置），无新证据升级 |

---

## 3. 终止声明

本轮（第 2 轮）产出新增发现 4 项：**P2×1（C7-014 task 引擎执行级 fencing 缺失）+ P3×3（C7-015 global 门面 per-JVM 语义、C7-016 kill-挂起转移竞态、C7-017 frozen 告警无抑制）**。其中 P2 一项是第 1 轮 P1（C7-001）同族在 task 引擎的真实扩展且暴露面更宽（进程内可达）；3 项 P3 均为边际暴露面（配置漂移、窄窗口竞态、部署语义缺口）。

按派单深挖指令的五个方向逐一核查后：日志放大同族清扫已穷尽全部日志站点（79 处清点，仅 1 例新立案）；自愈清理面正向确认无运行时注册残留；条带锁跨 JVM 声明核对准确；C7-009 同族的崩溃不一致面经排查仅 kill 竞态一例值得立案；H7/跨进程 resume 等暂缓主题的可达性已评估并收紧（维持不立案，理由与修复归属已文档化于 K6/K7）。

**C7 维度主要暴露面已穷尽，深挖终止。**（本轮无 P0/P1 级新发现；若 C7-014/C7-009 的修复实施引入新的恢复/抢占路径，建议在修复验证时单独立项复审，而非开启第 3 轮盲挖。）

## 4. 覆盖声明

**本轮新深读文件**（live code，全读）：TaskFlowManagerImpl、TaskRuntimeImpl、TaskImpl、TaskStepRuntimeImpl、TaskStateBean、AbstractTaskStateCommon、TaskStepStateBean、DaoTaskStateStore（1004 行全读）、TaskStepExecution（528 行全读）、TaskStepHelper（407 行全读）、TaskStepReturn（关键段）、CallTaskStep、ThrottleTaskStepWrapper、RateLimitTaskStepWrapper、SuspendTaskStep、DelayTaskStep、LoopTaskStep（209 行全读）、AbstractForkTaskStep（全读）、TaskConfigs、NopTaskInstanceBizModel、WfTaskScanner（160 行全读）、WorkflowManagerImpl（全读）、WorkflowCoordinatorImpl（全读）、WorkflowImpl（delayExecute/命令队列段）。

**定向核查文件/配置**：OrmEntityDao/OrmSessionImpl/EntityPersisterImpl（updateDirectly 与乐观锁语义链）、nop-task.orm.xml/nop-wf.orm.xml（versionProp 核对）、nop-task 各 pom（nop-cluster 依赖排除）、DaoWorkflowStore（save*/findRemindActivatedSteps 段）、WorkflowEngineImpl（join/signal/exitStep/triggerWaiting 段，:300-430/:480-540/:720-775/:1100-1130/:1300-1330/:1520-1560/:1910-1949）、DefaultWorkflowExecutor、owner docs 两篇全文。

**第 1 轮结论复核状态**：C7-001~013 无一被推翻；C7-008 的 warn-once 与 stats 无消费者结论维持（C7-017 补充其 frozen WARN 每调用侧与 semaphore 静默侧）；C7-009 的 workerId 无赋值点、ITaskQueue 空壳结论复核成立；正面结论（misfire 无补偿风暴、RetryPolicy 上限完备、TaskFlowMetricsImpl 有界标签、终态 first-terminal-wins 原子序列）全部维持，其中 first-terminal-wins 的覆盖面由 C7-016 限定为"四个终态 driver"，SUSPEND 写入为其未覆盖的相邻面。

**残留边界（未深读/维持第 1 轮定性）**：GraphTaskStep 内部逐行审计（owner doc 已声明死端报错不挂死）、nop-wf-web/nop-task-web AMIS 页面、nop-task-ext 两个 decorator 的事务嵌套代价、nop-cluster/nop-job 集群装配形态下 C7-001/C7-004/C7-014/C7-015 暴露面的变化。
