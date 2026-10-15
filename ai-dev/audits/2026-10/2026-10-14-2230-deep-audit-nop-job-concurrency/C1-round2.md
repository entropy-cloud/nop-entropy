# [nop-job] C1 共享可变状态与可见性 — 第 2 轮深挖（盲审）

- 审计单元：`nop-job` 聚合器（12 个 jar 子模块，src/main/java 164 files / 17,664 lines，不含 `_` 前缀生成文件与 target/）
- 仓库：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation）
- Owner doc：docs-for-ai/03-modules/nop-job.md（已全文读取）
- grep 基线：_tmp/cra-baseline-nop-job.txt（已全文读取）
- 审计日期：2026-10-14；维度：C1 共享可变状态与可见性（第 2 轮深挖）；审计人：并发审计子代理（盲审，未读 ai-dev/audits|plans|bugs|lessons 下除本维度第 1 轮文件外的任何文件）
- 前序输入：C1-shared-mutable-state.md（第 1 轮，C1-001~006 + 18 项共享状态清单），已完整读取
- vendored 边界声明：与第 1 轮一致（CronExpression/ICronExpression/BaseCalendar 及 Terracotta 版权的 Weekly/Monthly 等 calendar 实现只审集成边界）

## 1. 本轮发现

**无。本轮五个深挖方向（C1-003 失效面扩展、C1-004 兄弟站点、发布安全专项、builder/POJO 抽验、第 1 轮残留补全）均未产生新的高价值问题。** 全部候选点经证据核查后归入第 2 节排除项/定向核查清单，第 1 轮 6 项发现（C1-001~006）无一被推翻或需升级/降级。

未发现新的高价值问题。C1 维度深挖终止。

## 2. 排除项/定向核查

### 2.1 C1-003 失效面扩展：6 个 scanner 子类的扫描期字段面（定向核查，结论：第 1 轮单点声明成立，无需扩展）

**方法**：对 6 个 scanner 子类（JobPlannerScannerImpl、JobDispatcherScannerImpl、JobCompletionProcessorImpl、JobTimeoutCheckerImpl、JobScheduleCounterReconciler、JobWorkerScannerImpl）逐一 grep 全部实例字段声明 + 全部字段赋值语句，核对每个写点所属方法。

**证据**（以 JobWorkerScannerImpl 为例，其余 5 个同构）：
```java
// JobWorkerScannerImpl.java:40-50 —— 全部实例字段
private IJobTaskStore taskStore;          // 仅 setTaskStore（@Inject，init 期）写
private int maxConcurrency = 0;           // 仅 setMaxConcurrency（@InjectValue，init 期）写
private boolean enforceAttribution = false; // 仅 setEnforceAttribution（init 期）写
private IntRangeSet assignedPartitions;   // 仅 setAssignedPartitions（init 期）写
```
6 个子类合计约 40 个实例字段，**全部赋值点位于构造器/setter（IoC init 期）**；scanBatch/scanOnce 循环体内对 `this.<field>` 的写点为零（循环内命中的 `=` 全为局部变量，如 JobWorkerScannerImpl:147 `effectiveBatchSize`、:196 `myRemaining`）。扫描期可变状态唯一载体仍是基类 `AbstractBatchScanner` 的 Cursor 机制（`Cursor.time/id/drained` 三元组），受同一"scheduleWithFixedDelay 单线程"声明契约约束——多 Cursor 子类（JobTimeoutCheckerImpl 持 3 个 Cursor：taskCursor/fireCursor/waitingCursor，153-155 行）与单 Cursor 子类共享同一契约与同一缺口，第 1 轮"基类 1 处 × 6 暴露实例"的枚举已完整覆盖，无第二缺口点。

另核实 `@InjectValue` setter（setScanIntervalMs/setBatchSize 等）为加载期一次性求值（owner doc 明示 `@cfg:` 非 reactive、改配置需重启），无运行期热更写点。

**排除理由**：C1-003 的失效面声明经逐子类字段面核查后确认精确，扩展核查为阴性。

### 2.2 C1-004 兄弟站点：全模块"复合注册 + 异步回调"形态族谱（定向核查，结论：无新实例）

**方法**：全模块 grep `whenComplete|thenApply|thenAccept|thenRun|handle(|exceptionally`，得 10 个站点，逐一核对"回调动作内是否含可抛异常的注册/清理副作用 + 异常是否有传播通道"。

| # | 站点 | 形态 | 判定 |
|---|------|------|------|
| 1 | RemoteJobInvoker.java:100-113 `startJob(...).whenComplete` 内调 `pollTaskManager.register(...)` | 复合注册 + 回调 | **即 C1-004 本体**（第 1 轮已立案） |
| 2 | RpcPollTaskManager.java:123 `future.whenComplete((r,e) -> cancelEntry(entry))` | 终结清理钩子 | 干净（见 2.2.a 交错闭合验证） |
| 3 | RpcPollTaskManager.java:196/248（poll/cancel 回调） | 纯日志 + CAS-complete | 干净 |
| 4 | JobWorkerScannerImpl.java:302 `promise.whenComplete(... handleExecutionResult)` | 结果写回回调 | 干净（见 2.2.b） |
| 5 | DefaultJobCancelHandler.java:38 `promise.whenComplete`（cancelRunningTask） | fire-and-forget 日志回调 | 干净（无注册、无挂起方，同步段在 try/catch 内，45-48 行） |
| 6 | LocalJobScheduler.java:335 `future.whenComplete`（executeJob→handleResult） | 调度回调写 state | C1-001 族（写方持 `synchronized(job)`，第 1 轮已立案） |
| 7 | BeanMethodJobInvoker.java:66/67/99 | thenApply/exceptionally/handle 映射 | 干净（第 1 轮全文读） |
| 8 | NopRetryJobRetryBridge.java:49-57 `task.callAsync(...).whenComplete` | fire-and-forget 提交日志 | 干净（callAsync 同步段在 try/catch，58-60 行；回调仅记日志） |
| 9 | RpcJobInvoker.java:64-65/93-94 `invokeAsync(...).thenApply` | 映射回调；同步校验异常向调用方同步传播 | 干净（同步 throw 由有守卫的调用方 JobWorkerScannerImpl.java:296-306 `try{invokeAsync}catch→handleExecutionResult` 兜底终态化，无静默吞） |
| 10 | JobLogReporter.java:69-80 `fetchAsync(...).handle` | best-effort 上报 | 干净（try/catch 包裹 + handle 吞错记 WARN，契约明示 best-effort） |

**2.2.a register() 非异常路径交错闭合验证**（对 C1-004 的边界补全，非新缺陷）：针对"cancelToken 已取消 → `appendOnCancel`（115 行）在注册线程上立即同步触发 onCancelToken → future 已终结"以及"future 在 `setPollHandle`（121 行）与 `whenComplete` 挂接（123 行）之间被其他线程终结"两类交错，逐一验证：`register` 的三步兜底（121 `setPollHandle` → 123 `whenComplete` → 125-127 `future.isDone()` 复查）构成闭区间覆盖——任何完成时刻要么先于 123（挂接即同步触发 cancelEntry，此时 handle 已设置）要么后于 123（回调异步触发，handle 已设置），125-127 覆盖"挂接前已终结"方向（javadoc 124 行明示含 appendOnCancel 立即触发路径）；`CompletableFuture.complete` CAS 保证多完成方（onCancelToken/pollOnce 超时/poll 终态）仅一方胜出，`cancelEntry` 幂等（handle null 检查 + `entries.remove(taskId, entry)` 两参形式）。唯一未闭合窗口即 C1-004 立案的"中间步骤抛异常"方向，无第二窗口。

**2.2.b JobWorkerScannerImpl 回调线程面**：`handleExecutionResult` 在 promise 完成线程（RPC 回调/用户线程池）运行，与下一轮扫描线程真并发，是本模块第 1 轮清单外唯一"不受 scan 单线程契约保护"的运行期并发面。核查其全部共享依赖：方法内全部状态为局部变量 + DB per-call 加载（311 行 loadTask、320 行 loadFire，每次回调独立加载，不同 task 间无共享可变对象）；`workerMetrics` 为唯一共享对象，见 2.4 排除项。同 task 双回调不可能（298-303 行 null/非 null 二选一）。

### 2.3 发布安全专项：构造器 this 逃逸 / new Thread / 构造期监听器注册（定向核查，结论：干净）

**方法与证据**（全模块 src/main grep，不含 `_gen`）：

- `new Thread`：**0 处**（唯一命中为 RpcPollTaskManager.java:48 javadoc 文字）。模块不自建线程，统一经 `GlobalExecutors.globalTimer()/globalWorker()`（AbstractBatchScanner.java:135）与注入的 `IScheduledExecutor`（RpcPollTaskManager.java:119）。
- 构造器内 `this` 逃逸（`new X(this)` / 构造器内注册本对象监听器）：**0 处**。构造器体内的 `this.` 命中均为普通字段初始化（DTO 拷贝构造器：JobInstanceState:51-58、JobFireResult:29-31、ResourceVector:41-42 等，纯字段赋值不逃逸）。
- 监听器注册（appendOnCancel/addListener/registerListener）：唯一形态为 RpcPollTaskManager.register 的 `appendOnCancel`（115 行，C1-004 站点），非构造期，运行期 per-entry 挂接 + 终结期 `removeOnCancel` 配对摘除（145 行）。
- 生命周期注册：`AbstractBatchScanner.startScanning()`（58 行 synchronized）将扫描任务提交给 executor，发生于 IoC init 字段全部写完之后，发布 happens-before 按平台设施假定（第 1 轮残留边界 #2 口径不变）。

**排除理由**：发布安全三形态（this 逃逸、构造期注册、自建线程）在本模块零命中，无需立案。

### 2.4 builder/POJO 面：构建期写点与运行期读点归属抽验（定向核查，结论：归属清晰，无逃逸缺口）

**抽验对象与证据**：

- **CalendarBuilder → calendar 实现**（nop-job-core/calendar/CalendarBuilder.java:44-120）：全部 spec 值在构建期**逐值拷贝**进 calendar 自有结构——`MonthlyCalendar`/`WeeklyCalendar` 走 `for (int day : spec.getExcludes()) setDayExcluded(day, true)`（值拷入自有 boolean[]），`AnnualCalendar.setExcludeDays` 为 `this.excludeDays = new ArrayList<>(excludeDays)` + sort（拷贝+排序），HolidayCalendar 收集进新建 `ArrayList<LocalDate>`。**calendar 不持有 spec 数组/Map 引用**；spec 侧的 `int[] excludes`、`Map yearDays` 构建后再无运行期读点。`WeeklyCalendar.getDaysExcluded()` 已有防御拷贝（check2 [P3-9] 注释）。
- **TriggerSpec/JobSpec/LocalJobConfig/\*CalendarSpec**（约 60 文件）：纯 JavaBean，无集合字段初始化器（无 `= new HashMap()` 形态的实例字段默认值，grep 阴性）；写点 = 加载期（LocalJobConfigLoader/TriggerSpecHelper 每周期 per-call 新建）或用户注册前；运行期读点全部归属第 1 轮已立案路径——`SimpleExecutionContext.getJobParams()`（C1-002）、`ScheduledJob.toJobDetail`（C1-001）、`TriggerBuilder.buildTrigger`（构建期一次性）。
- **JobWorkerMetricsImpl**（worker 并发回调面唯一共享依赖）：6 个实例字段全部 `final Counter/Timer`（Micrometer 平台设施，自身线程安全），构造期定型后无变异点（JobWorkerMetricsImpl.java:11-28）。`onTaskSuccess/onTaskFailure/onTaskTimeout` 被 2.2.b 所述的多线程回调并发调用，判定安全。其余 metrics 实现（planner/dispatcher/completion）仅由单线程扫描调用，天然安全。

**排除理由**：构建期写点与运行期读点归属清晰，无新增逃逸路径（spec 可变 Map 逃逸已由 C1-002 覆盖，不重复立案）。

### 2.5 第 1 轮残留边界补全：JobScheduleStoreImpl 全文精读（结论：干净，残留边界关闭）

**证据**（JobScheduleStoreImpl.java，483 行全文）：唯一实例字段 `daoProvider`（44 行，@Inject init 期）；`ACTIVE_FIRES_QUERY_LIMIT` 为 static final。全部 13 个公开方法为 per-call `QueryBean` 构造 + DAO 实体操作；实体变异（如 tryLockSchedulesForPlan:78-81 的 `schedule.setNextFireTime(lockTime)`、updateScheduleWithRetry:338-346 的闭包式字段重设）均作用于**本调用链内新加载的实体**，配合 `@Transactional(REQUIRES_NEW)` + `tryUpdateManyWithVersionCheck`/`updateWithRetry`(5 次) 乐观锁——属第 1 轮清单 #18 的"DB 权威状态"（C4/C7 维度主体），进程内无共享可变缓存。第 1 轮残留边界 1（"仅字段清单核查"）就此关闭，其"残留漏报风险低"预判成立。

### 2.6 其他定向排除点

- **MonthlyCalendar.getDaysExcluded() 返回内部数组裸引用**（MonthlyCalendar.java:77，对照 WeeklyCalendar.java:72 的 clone 防御拷贝，check2 [P3-9] 修复未同步至此）：全模块 grep `getDaysExcluded` 仅两个定义零调用方；`ITrigger` 接口不暴露 calendar，trigger 包装类无 `getCalendar()` 访问器——模块内**不可达**，外部可达性需先取得 CalendarBuilder 私有产物。vendored（Terracotta 版权）+ 无集成暴露路径 + 纯卫生级 → 不立案，记录为 vendored 已知边界（与 C1-006 同级但缺集成触发路径，弱于 C1-006）。
- **JobWorkerScannerImpl:297 promise 永不完成（用户 invoker 缺陷/远程挂起）→ task 行停留 RUNNING**：依赖 DB 权威 + 超时链兜底（第 1 轮清单 #18、owner doc 超时语义三层声明），属 C7 维度与已声明边界，非 C1 进程内共享状态缺陷。
- **JobLogReporter.java:67 `HttpRequest.post(reportUrl)` 位于 try 块外**：HttpRequest.post(String) 为值对象工厂实际不抛；即便抛出也是同步传播给显式调用方（best-effort 契约由 javadoc 明示），C3/C6 形态且写不出触发时序，非 C1，不立案。
- **MetadataWorkerCapacityProvider `synchronized(this)` DCL**（78 行）：第 1 轮清单 #5 已判定（DCL 模式正确），本轮复核无变化。

## 3. 终止声明

第 2 轮按深挖指令完成全部五个方向后，未发现新的高价值问题：无新 P0/P1/P2/P3 立案。第 1 轮 C1-001~006 经第 2 轮定向复核（C1-003 失效面逐子类字段核查、C1-004 非异常交错闭合验证、C1-004 兄弟站点 10 站点族谱枚举）全部维持原判。**C1 共享可变状态与可见性维度对 nop-job 审计单元的深挖到此终止**；后续轮次如需重启，应仅由代码变更（分支 concurrency-audit-remediation 的整改提交）或新形态线索触发。

## 4. 覆盖声明

**本轮新增覆盖**（第 1 轮未逐一覆盖或以清单方式核查的面）：

| 面 | 方式 | 结果 |
|---|------|------|
| 6 scanner 子类实例字段全部写点 | grep 字段声明 + 赋值语句 × 6 文件逐一核对 | 全为 init 期写，C1-003 单点声明确认 |
| 全模块异步回调站点 | grep whenComplete/thenApply/thenAccept/thenRun/handle/exceptionally → 10 站点逐一定性 | 族谱闭合，C1-004 为唯一复合注册缺陷实例 |
| RpcPollTaskManager.register 非异常交错 | 手工交错推演（appendOnCancel 立即触发 / setPollHandle-whenComplete 窗口） | 三步兜底闭区间覆盖，无第二窗口 |
| 发布安全三形态 | grep new Thread / 构造器 this 逃逸 / 构造期监听器 | 全部零命中 |
| CalendarBuilder 拷贝语义 + calendar getter 面 | 全文读 + AnnualCalendar/Weekly/Monthly 抽验 | 构建期值拷贝，无 spec 引用持有 |
| JobWorkerMetricsImpl 并发回调面 | 全文读 | final Micrometer meter，安全 |
| JobScheduleStoreImpl（第 1 轮残留） | 483 行全文读 | 干净，残留边界关闭 |
| JobWorkerScannerImpl.handleExecutionResult 并发面 | 全文读 + 共享依赖核查 | per-task 状态隔离 + metrics 线程安全，干净 |
| DefaultJobCancelHandler/RpcJobInvoker 回调与错误通道 | 精读 | fire-and-forget 日志 / 同步异常有守卫调用方，干净 |

**沿用第 1 轮的边界（不重复声明）**：vendored 算法内部（CronExpression/ICronExpression/Terracotta calendar 算法）、NopIoC 容器发布 HB、GlobalExecutors/Micrometer/ICancelToken 平台设施内部、src/test。

**序号接续**：本轮零立案，下一轮次（若有）发现编号自 **C1-007** 起。

**总量统计（两轮累计）**：P0 = 0；P1 = 0；P2 = 5（C1-001/002/003/004/005）；P3 = 1（C1-006）；第 2 轮新增 = 0。
