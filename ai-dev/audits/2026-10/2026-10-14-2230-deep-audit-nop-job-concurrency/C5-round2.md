# C5 内存有界性与大数据量维度审计 — nop-job（第 2 轮深挖）

- 审计单元：`nop-job` 聚合器（12 个 jar 子模块），承接第 1 轮（C5-memory-bounds.md，C5-001~004 + R1-R14 盘点）
- 仓库根：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 concurrency-audit-remediation）
- Owner doc：`docs-for-ai/03-modules/nop-job.md`（第 1 轮已全文读取）；grep 基线：`_tmp/cra-baseline-nop-job.txt`（本轮已读取）
- 盲审纪律：本轮仅读取第 1 轮 C5 文件（指定必要输入）；未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下其他任何文件；C4-009 仅以深挖指令给出的摘要（worker 侧 promise 无超时 → sumReservedCost 永久扣减）为线索、以当前 live code 独立验证
- 审计日期：2026-10-14

---

## 1. 本轮发现

### [nop-job-C5-005] C5-001 失效面扩展（F11）：PollEntry 经实体 JSON 组件长期驻留完整解析后的参数对象图——单条目驻留成本 O(job 参数体积) 而非 KB 级，泄漏速率 × 参数体积叠加验算；并核对与 C4-009（DB reserved 永久账本）的同族峰值叠加

1. **严重度**：P2（C5-001 的影响放大器，非独立无界机制——单独不产生增长，修复 C5-001 任一方向即同步闭合；但使 C5-001 的 OOM 触发门槛从"万级条目×月级累积"下移至"千级条目×大参数 job"量级）
2. **缺陷族**：F11 中间物化/大参数驻留（本轮主攻方向）+ F7 同族叠加验算；宿主缺陷为 C5-001（F4 无界账本）
3. **位置**：
   - `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcPollTaskManager.java` L107-109（PollEntry 捕获 schedule/fire/task 三实体入注册表）、L195（每 poll tick 携带实体发起 getJobStatus）
   - `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/DefaultRpcPollTaskClient.java` L183-192（`resolveJobParams` 每 tick 重建参数 Map）、L255-260（`injectPollTimeoutHeader`）
   - `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/entity/NopJobTask.java` L20-33（`getEffectiveParams` 每次 new LinkedHashMap 全量合并 fire 快照 + task payload）
   - `nop-persistence/nop-orm/src/main/java/io/nop/orm/component/JsonOrmComponent.java` L32、L55-62（解析结果 `jsonValue` 缓存于组件实例，随实体生命周期驻留——平台设施，此处仅审集成边界）
   - 叠加面：`nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java` L295-303（scanner 侧 whenComplete 无超时兜底）；`JobTimeoutCheckerImpl.java` L489-501（无 timeoutSeconds 且 executionTimeoutMs≤0 时直接 return，RUNNING 行仅靠 liveness 链回收）；`RpcJobInvoker.java` L128-134（HEADER_TIMEOUT 仅 timeoutSeconds>0 时注入）
4. **证据片段**：
   ```java
   // RpcPollTaskManager L107-109：条目捕获三实体（含各自 JSON 组件）
   PollEntry entry = new PollEntry(task.getJobTaskId(), schedule, fire, task, cancelToken, future,
           computeDeadline(schedule));
   entries.put(entry.getTaskId(), entry);
   // DefaultRpcPollTaskClient L183-192：每 poll tick（默认 5s）全量重建参数 Map
   private static Map<String, Object> resolveJobParams(NopJobSchedule schedule, NopJobFire fire, NopJobTask task) {
       Map<String, Object> jobParams = task.getEffectiveParams(fire);   // new LinkedHashMap 全量合并
       if (jobParams.isEmpty() && schedule != null) {
           Map<String, Object> scheduleParams = schedule.getJobParamsComponent().get_jsonMap();
   // JsonOrmComponent L55-62：解析一次后 jsonValue 随实体永久驻留（对象图 ≈ JSON 文本 2-5 倍堆）
   public Object get_jsonValue() {
       Object value = jsonValue;
       if (value == NOT_INITED) {
           String text = get_jsonText();
           value = jsonValue = JsonTool.parseBeanFromText(text, Object.class);
   ```
5. **触发链（输入规模 × 路径 × 驻留结构）**：executorKind=rpcPoll 且 schedule.timeoutSeconds 未配置的大参数 job（jobParams/taskPayload 携带 MB 级 data payload——rpcPoll 的设计场景即长任务参数透传，`DefaultRpcPollTaskClient.startJob` L109-117 将 `jobParams.data` 原样合并进请求）× worker 失联（C5-001 已立案链：getJobStatus 瞬态失败被忽略 + deadline=0 无兜底）× 驻留结构 = PollEntry → NopJobSchedule/NopJobFire/NopJobTask 三实体 → 各自 JsonOrmComponent 的缓存 `jsonValue`（**完整解析后的参数对象图**，非仅字符串）。第 1 轮对 C5-001 的单条目估算"约数 KB（三实体 + future 链 + 句柄）"漏算了组件缓存：实体一旦解析过参数（注册前 RemoteJobInvoker/任务构建路径必然触发），每个被泄漏条目即钉住 O(payload 对象图) 常驻堆。次级效应：每泄漏条目每 5s 一次 `getEffectiveParams` 全量浅拷贝（L20-33 new LinkedHashMap 复制全部 entry 槽位）+ 一次注定失败的 getJobStatus RPC（携带 jobName/fireId 等 header）——永久 GC 与网络/日志 churn，随条目数线性叠加。**叠加验算（单条目 × 累积速率 × 时间尺度）**：(a) coordinator entries 面——每 worker 失联事件泄漏 1 条目；单条目 ≈ 3 实体骨架（~数 KB）+ 解析对象图（1MB JSON ≈ 3-5MB 堆，含 Map/嵌套节点开销）+ 句柄/监听器；月级滚动发布场景 1000 次失联 × 1MB payload job ≈ 3-5GB 常驻（第 1 轮按 KB 级估算同场景仅数十 MB——OOM 门槛下移两个数量级）；(b) DB reserved 面（C4-009 同族，live code 复核）——worker 侧 promise 挂起变体（RpcJobInvoker 无 HEADER_TIMEOUT 依赖平台传输默认 + `JobTimeoutCheckerImpl.tryMarkTimeout` L496-501 无配置即 return + scanner 侧 L297-303 whenComplete 无超时兜底）使 task 行永久 RUNNING ∈ `RESERVED_TASK_STATUSES`（NopJobCoreConstants L40-45），`sumReservedCost`/`countInFlightTasks` 永久扣减；默认配置下（max-concurrency=0 旁路 count ceiling + capacity 未声明退化 MAX_VALUE，MetadataWorkerCapacityProvider/NopJobCoreConstants L20）两道闸门均不制动，DB RUNNING 行与平台传输层在途 future 账本随周期单调累积；dispatcher 侧 `DefaultWorkerLoadProvider.loadReservedByWorker` L116-125 每 scan 物化的行数 = 非 terminal task 的 distinct workerInstanceId（含死亡 worker，随部署历史慢增长——DB 侧，单独标注）。同一失联/挂起事件族在 coordinator 堆（条目×payload）与 DB 账本（RUNNING 行×cost）双站点同步累积，峰值 = 两站点之和。
6. **声明上界 / 由谁保证**：无条目驻留体积上界；owner doc 三层超时口径（schedule.timeoutSeconds > executionTimeoutMs > 不超时）在 `computeDeadline`（L259-265）与 DB 链均存在"第三层 = 无界"默认；jobParams/taskPayload 无任何模块级大小校验（`TriggerSpecHelper`/task builder 均不检查 payload 体积；reportTaskLog 侧同类缺口已由 C5-002 覆盖）。唯一保证者为 C5-001 的修复本身。
7. **判定**：C5-001 失效面扩展 + F11 大参数驻留缺口成立。非独立 P1：驻留钉住完全以 C5-001 的泄漏链为前提（正常在途条目数受在途任务约束，属 R4/C5-004 已盘点的峰值面）；作为放大器与叠加验算载体立案 P2，修复建议并入 C5-001 的 (a)/(b) 方向，另加 (d) 对 PollEntry 捕获的实体做参数裁剪（注册后轮询仅需 serviceName/headers，可在 entry 内保留精简视图而非三实体全量引用）。
8. **影响面**：coordinator 进程堆常驻随"失联事件数 × job 参数体积"乘性增长（大参数 job 场景 OOM 门槛显著低于第 1 轮估算）；每泄漏条目 5s 周期的参数 Map 浅拷贝 + 失败 RPC 造成永久 GC/网络/日志 churn；DB 侧 RUNNING 永久账本（C4-009 域）使 worker 资源闸门永久侵蚀、dispatcher 侧聚合行数随死亡 worker 慢增长。
9. **修复方向**：(a) 主修 C5-001（computeDeadline 纳入 executionTimeoutMs 或条目最大轮数/年龄兜底）；(b) PollEntry 精简视图：注册时仅拷贝轮询所需字段（taskId、serviceName、headers、targetHost、cancelToken），不持有三实体全量引用，使单条目驻留回到 KB 级并与 payload 解耦；(c) jobParams/taskPayload 增加模块级序列化体积上限（对齐 C5-002 的修复方向）；(d) `getEffectiveParams` 在 DefaultRpcPollTaskClient 轮询路径上避免每 tick 重建（仅 startJob 需要完整 payload，getJobStatus/cancelJob 只需 instanceId）。
10. **误报校准核查**：非平台设施自身实现（PollEntry 捕获策略与 per-tick 重建均为 nop-job 手写代码；JsonOrmComponent 缓存为平台行为，仅作为驻留事实引用）；非低频路径（worker 失联为分布式调度常规事件，大参数 rpcPoll job 属特性设计场景）；非有界声明结构（无任何体积上限声明）；与 C5-001 的关系为同链路影响放大而非重复立案（C5-001 立案对象是 entries 的无界性，本条立案对象是单条目驻留体积的参数耦合 + 双站点叠加验算）。
11. **置信度**：高（捕获链、组件缓存语义、per-tick 重建、RESERVED 状态集、容量默认值全部逐行核读；量级估算基于 JSON 对象图堆开销的通用倍率，未做运行时测量——已在残留边界注明）。
- **复核状态**: 已保留（P2 维持。钉住链完整核实：PollEntry 捕获三实体（RpcPollTaskManager L107-109）、DefaultRpcPollTaskClient L183-192 每 tick（getJobStatus L140 调用）经 NopJobTask L20-33 getEffectiveParams 全量重建、JsonOrmComponent L32/L55-62 jsonValue 解析缓存随实体驻留、startJob（L93）注册前必然触发解析；叠加面核实：RpcJobInvoker L128-136 仅 timeoutSeconds>0 注入 HEADER_TIMEOUT、JobTimeoutCheckerImpl L489-501 无配置即 return、scanner L297-303 promise 无超时兜底、RESERVED_TASK_STATUSES（NopJobCoreConstants.java L40-45）与双闸门默认旁路（L20 MAX_VALUE）成立。"OOM 门槛下移两个数量级"为基于 JSON 对象图 2-5x 通用倍率的推断（原条目已自声明未做运行时测量），方向成立、量级存不确定性——列入复核不确定项。"非独立无界机制"定性核实成立：正常条目数受在途任务约束，宿主缺陷为 C5-001。现状：新发现）

---

## 2. 排除项/定向核查

**第 1 轮「有界判定」抽查复核（独立验证保证者在位，全部通过）**：

| 抽查项 | 第 1 轮编号 | 独立验证结果 |
|---|---|---|
| scanner 批查询 LIMIT | R9 | `JobTaskStoreImpl` L73/L121/L188、`JobFireStoreImpl` L56/L75/L246、`JobScheduleStoreImpl` L57/L368/L404 全部 `query.setLimit(...)` 在位；`ACTIVE_FIRES_QUERY_LIMIT=500`（L42）+ 超限 warn（L379-386）在位 |
| `findTasksByFireId` 无 LIMIT 声明边界 | R8 | `JobFireStoreImpl` L309-318 注释原文在位："No LIMIT: completion/cancel need ALL tasks of a fire... Task count per fire is bounded at dispatch time (broadcast = healthy instances, partition = partition count, single = 1)"——声明边界成立 |
| 触发器/日历迭代硬上限 | R10 | `PauseCalendarTrigger` L22 `MAX_TRY_COUNT=10000`、`AnchorRepeatTrigger` L25 `MAX_SCAN=36600`、`HolidayCalendar` L23 `MAX_DAY_SCAN=366*5` 全部在位且循环内计数检查在位 |
| LocalJobScheduler.jobs（加测） | R5 | L48 ConcurrentHashMap + L135 removeJob + deactivate 双路径清理 + putIfAbsent 竞态防护（L94-126）在位 |
| DefaultWorkerLoadProvider.scanCache（加测） | R6 | L40 ThreadLocal + L44 beginScan clear + L52-55 endScan `remove()`（check2 P3-6 修复注释在位）；缓存键 serviceName，值按 discovery 实例数——有界 |

**本轮新增排除项（确认干净）**：

1. **metrics/统计 Map 面（深挖指令第 4 项）**：`JobPlannerMetricsImpl`/`JobDispatcherMetricsImpl`/`JobCompletionMetricsImpl`/`JobWorkerMetricsImpl` 四类全部为构造期注册的**固定名** Micrometer Counter/Timer（如 `nop.job.worker.tasks-claimed`），无动态 tag、无按 jobName/taskId/workerId 的 Map 或维度注册——基数恒定，不随任务/schedule 数增长。基线 C7 supplement 节命中文件逐一核对，无其他归属本维度的动态注册面。
2. **JobCoordinator**：纯生命周期接线（doStart/doStop 逐组件启停），无任何实例级容器。
3. **JobScheduleCounterReconciler**：单批 `query.setLimit(batchSize)`（fetchSchedulesToReconcile），逐条对账异常隔离，无累积结构；non-@SingleSession 偏离有类注释声明。
4. **JobCompletionProcessorImpl**：字段级仅 scanner 配置与 metrics/retryBridge/alarmHandler；全部 List/Map 为 per-fire 方法局部（L133/L137/L264/L273），无跨周期驻留。
5. **AbstractBatchScanner.registeredCursors**：per-instance 固定 ArrayList（`newCursor()` 仅字段初始化期调用），Cursor 状态每周期 `scanOnce()` 入口统一 reset（L88-92）——无跨周期累积。
6. **JsonOrmComponent 缓存语义（F11 定向）**：平台设施（nop-orm），解析值随实体生命周期缓存、无逐次重解析泄漏；其集成后果（被 PollEntry 钉住）已并入 C5-005，组件自身不立案。
7. **NopRetryJobRetryBridge（R12 复核）**：fire-and-forget 提交（newRetryTask + callAsync + 仅日志的 whenComplete），本地无队列/账本；重试账本属 nop-retry 引擎（集外单元）。
8. **jobParams 快照创建路径（F11 定向）**：`JobPlannerScannerImpl.buildFire` L150 每 fire 一次 `copyMap`（L269-270 浅拷贝，瞬时）；快照随 fire 行持久化为 DB 侧增长（见下"DB 侧补充标注"）。
9. **Invoker promise 完成保证（C4-009 同族核对）**：`NopE2eTestJobInvoker` 立即完成；`RpcJobInvoker` promise 完成依赖平台 transport（HEADER_TIMEOUT 仅 timeoutSeconds>0 注入，L128-134——该缺口的核心定级属 C4 维度 C4-009，本维度仅在 C5-005 叠加验算中引用，不重复立案）；`BeanMethodJobInvoker` 的同步阻塞形态属 C6/C3 域，非本维度。

**DB 侧补充标注（不与内存面混排）**：除第 1 轮已标注的 `nop_job_task_log` 外，本轮确认两处同类 DB 侧增长：(a) `nop_job_fire.jobParamsSnapshot` 每 fire 行冗余存储完整参数快照（L150）——大参数 schedule 的 fire 行体积 = O(payload)×fire 频率；(b) `nop_job_task` 的永久 RUNNING 行（C4-009 域）同时是 `sumReservedCostByWorker` 聚合行数随死亡 workerInstanceId 慢增长的来源。两者均受 DB 容量约束，属容量治理课题。

---

## 3. 终止声明

**C5 维度深挖终止。** 本轮仅产出 1 项新发现 C5-005（P2，C5-001 失效面扩展/F11 叠加验算），未发现新的独立无界机制或 P1 级问题。判定依据：(1) 第 1 轮 R1-R14 全量盘点的有界判定经 5 项抽查（R5/R6/R8/R9/R10）独立复核全部成立；(2) 深挖指令四方向逐一闭合——C4-009 同族叠加验算完成（C5-005 内）、有界判定抽查完成、jobParams/jobDetail 字符串面完成（组件缓存语义 + 快照路径 + 无独立新缺口，DB 侧单独标注）、metrics Map 面完成（固定基数，干净）；(3) 基线 C7 supplement 命中全部核对无遗漏。C5-005 的修复与 C5-001 同链路，建议在同一修复 PR 内处理。

---

## 4. 覆盖声明

**本轮覆盖**：以第 1 轮 164 files/17,664 lines 逐文件核读为基础，本轮定向深读/复核以下高价值面（全部 src/main/java 手写代码）：RpcPollTaskManager（全文件重读，确认与第 1 轮一致）、RemoteJobInvoker、DefaultRpcPollTaskClient、DefaultJobCancelHandler 关联链、JobTimeoutCheckerImpl（全文件）、JobWorkerScannerImpl（全文件）、DefaultJobExecutionContextBuilder、DefaultJobInvokerResolver、MetadataWorkerCapacityProvider、DefaultWorkerLoadProvider、JobCoordinator、JobScheduleCounterReconciler、JobCompletionProcessorImpl（容器面）、JobPlannerScannerImpl（buildFire/copyMap）、AbstractBatchScanner（全文件）、RpcJobInvoker、BeanMethodJobInvoker、NopRetryJobRetryBridge、NopJobTask/NopJobSchedule/NopJobFire 实体参数访问器、JsonOrmComponent（平台设施集成边界）、4 个 metrics 实现、LocalJobScheduler（注册表面）、JobTaskStoreImpl/JobFireStoreImpl/JobScheduleStoreImpl（LIMIT 抽查）、NopJobCoreConstants（状态集/容量默认值）。`_` 前缀生成文件（`_gen/*`）仅核对引用关系不作为发现对象。

**盲审纪律遵守声明**：本轮仅读取 `/ai-dev/audits/2026-10/2026-10-14-2230-deep-audit-nop-job-concurrency/C5-memory-bounds.md`（指定的前序轮次必要输入）；未读取任何其他审计文件、计划、bug 记录或 lessons；C4-009 的结论仅以深挖指令中的摘要为线索，全部关键事实（scanner 无超时兜底、超时检查链无配置即 return、RESERVED 状态集、容量默认 MAX_VALUE）均以 live code 独立验证。

**残留边界**：(1) C5-005 的驻留量级估算基于 JSON 对象图堆开销的通用倍率（2-5x）与 KB 级实体骨架推断，未做运行时堆测量——建议修复 PR 附带条目驻留体积断言（含大参数 job 用例）；(2) 平台 RPC transport 的在途 future 账本有界性属 nop-rpc/nop-cluster 设施自身实现（集外），C5-005 仅在叠加验算中引用其存在；(3) `LocalJobScheduler` 竞态、`RemoteJobInvoker` L115 NPE 等第 1 轮已评估的 C3 形态线索维持不立案口径不变；(4) 本轮未修改任何前轮文件。
