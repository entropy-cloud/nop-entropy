# C4 线程池与执行器治理 — 初审报告（盲审）

- **审计单元**: nop-rule（全 72f/5,221 行）+ nop-metadata（全 242f/29,131 行），合并单批
- **维度**: C4 线程池与异步执行（concurrency-resource-audit-prompt.md C4 正文 + 本单元维度适配口径）
- **盲审声明**: 初审阶段未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 下任何已落盘文件；结论全部基于 live code（commit 81a7c228e7 基线快照）与 `_tmp/cra-baseline-nop-rule-metadata.txt` 主 agent 预注。只读审计，未修改任何产品文件。
- **CL 前缀**: `nop-rule-metadata-CL-0NN`（本维度立案线索）；跨维度强信号以 `CL-201` 起附记
- **批次**: 全单元单批（72f + 242f，`_gen`/`target` 排除；src/test 不审，仅在 C10 交叉面引用存在性）

---

## 0. 基线预注核实（计数口径纪律）

| 预注 | 复现口径 | 初审结果 |
|---|---|---|
| C4a main 面 JDK 并发原语 0 命中 | `grep -rn -E "new Thread\(\|ExecutorService\|Executors\.\|newFixedThreadPool\|newCachedThreadPool\|IThreadPool\|ScheduledExecutorService\|ThreadPoolExecutor"` 过 `/main/` 且排 `_gen` | **0 命中，预注成立，无需订正**（test 面 3 文件命中，全部 src/test，不审） |
| C4a 8 命中全部 src/test | 同上口径不过滤 | 8 命中收敛为 3 文件（TestCheckpointActionDispatcherConcurrency / TestNopMetaQualityCheckpointBizModel / TestNopMetaEntityConcurrentNullSchemaUpsert；多文件内多命中），全部 src/test —— 与预注方向一致 |
| C3 overlay 9 命中 | `grep -E "CompletableFuture\|thenApply\|thenCompose\|supplyAsync\|runAsync\|orTimeout"` | 9 命中全部位于 src/test mock（MockMessageService / MockHttpClient），main 面 0 —— 与"main 面无异步链"判定互证 |
| scheduleAtFixedRate / Timer / parallelStream / commonPool / ForkJoin | 同上口径 | main 面 0 命中 |
| **基线细化口径缺口（订正记录）** | 附录 C 细化口径为「static 非 final + 容器类型（Map/List/Set/Date/Format/Random/容器）」，**未覆盖 primitive 静态可变字段** | `nop-metadata/.../query/CrossDbConfigHolder.java:5` 存在 `public static int maxCrossDbRows = 10000`（public static 非 final 可变字段，main 面），两次基线细化均未命中。main 面无运行时写入点（仅读），故不构成活动数据竞争，但「main 面 static 可变字段≈0」的结论口径上不严密，见 CL-204 |

**预注订正结论：C4a 预注（0 命中）成立、不被订正——本单元 main 面确无自建线程池/裸线程；全部调度走平台 job 机制（nop-job-local `LocalJobScheduler`），全部执行同步。**

---

## 1. 执行模型清单表（C4 本单元主审面：平台 job 执行模型下的并发纪律）

单元内唯一调度注册点：`MetaQualityCheckpointScheduler`（grep `addJob|fireNow|IJobScheduler|removeJob` 全单元 main 面，仅此 1 文件命中）。平台侧依赖（nop-job-local / nop-commons executor）按口径"只审使用方式"，为验证使用假设读了平台实现。

| # | 执行模型面 | 实现位置 | 线程/池 | 判定 | 判定依据 |
|---|---|---|---|---|---|
| 1 | 自建线程/线程池/裸线程 | 全单元 main 面 | 无 | **平台机制已确认**（预注成立） | C4a grep 0 命中；无 commonPool/并行流/ForkJoin 间接使用 |
| 2 | 平台 job 池（共享面） | `app-local-scheduler.beans.xml:7-17` → `DefaultScheduledExecutor`(JDK `ScheduledThreadPoolExecutor`) | `nop-job-local-scheduler`，core=max=4（`nop.job.local-scheduler.pool-size` 可配），`NamedThreadFactory` 命名可观测，`destroy-method="destroy"`（shutdownNow） | **配置合格** | 有界声明存在（池大小可配、delayed 队列每 job 至多 1 个 pending fire，无 per-tick 堆积）；关闭钩子已注册（F8 对称）；共享面影响见 C4-002 |
| 3a | 同一 checkpoint cron 重叠触发（cron 短于执行时长） | `LocalJobScheduler.scheduleNext/executeJob/handleResult`（nop-job-local:266-380） | 单 job 严格串行：fire→execute→`handleResult`→`scheduleNext`（完成后再算下次触发时间），`job.running` + `InternalState.RUNNING` 占位 | **平台机制已防护** | 下一次触发在本次执行完成后才排程，同 job 不可能并行两发；`fireNow` 在 `synchronized(job)` 内检查 `running != null` 即拒绝（fireNow 手动补触发同样防重叠）。执行慢于 cron 时 tick 被跳过（无补偿/catch-up）——对幂等快照型 checkpoint（每 run 独立 runId、结果行追加）语义无害，属已核实的平台边界 |
| 3b | cron×手动交叉重入 | `NopMetaQualityCheckpointBizModel.executeCheckpoint:203-206`（`checkpointRunLocks.putIfAbsent`）+ `MetaQualityCheckpointScheduler.executeScheduledCheckpoint:221-226`（`ERR_CHECKPOINT_ALREADY_RUNNING` 降级 WARN） | 进程内 per-checkpointId 非阻塞运行标记，fail-fast | **平台守卫 + 单元级互补防护成立** | 平台按 jobName 防 cron 自重叠；本标记补手动路径与手动×cron 交叉面；标记在 finally 释放（仅持有者 remove，fail-fast 下安全）。跨实例（多节点部署）不在 LocalJobScheduler 声明包络内（单机嵌入式，类 javadoc 明示）——已声明边界 |
| 3c | job 永久 FAILED 风险（异常逃逸 → `JobFireResult.ERROR`） | `MetaQualityCheckpointScheduler.executeScheduledCheckpoint:204-234`（catch-all 返回 `buildErrorResult`） | job 存活按 cron 继续触发 | **防护到位** | checkpoint 级 Exception 全部在 try 边界内转换（MA7.5-01 设计）；`Error`/`StackOverflowError` 类 Throwable 仍会经 `BeanMethodJobInvoker.invokeAsync` 的 `exceptionally` 转 ERROR 使 job 永久 FAILED——属 JVM 级故障场景，不立案 |
| 4 | jobParams→beanMethod 反查链线程语义 | `BeanMethodJobInvoker.invokeAsync`（nop-job-local:57-73）→ `invokeReflective` → `toCompletionStage`（同步方法返回值直接 `completedFuture`） | **bean 方法同步执行在调度池线程上**（`nop-job-local-scheduler` 4 线程之一），无二次派发 | **语义已核实，共享面影响立案 C4-002** | `executeScheduledCheckpoint` 返回 DTO 非 CompletionStage → 整条 checkpoint 编排（规则循环×外部库查询 + autoScore + webhook/notify 投递）全部占住池线程直至完成；`DisabledEvalScope.INSTANCE` 与 null context 传入一致（无 ThreadLocal 上下文依赖跨线程断裂问题——本链路根本无第二线程） |
| 5 | CheckpointActionDispatcher 分发执行模型 | `CheckpointActionDispatcher.dispatch:150-204`；`dispatchWebhook:224-281`；`dispatchActions`（BizModel:509-522，`runWithoutTransaction` 包裹） | **同步**，在调用线程（job 池线程或 web worker）上逐 action 执行；per-action try/catch 隔离；`request.setTimeout(webhookTimeoutSeconds*1000L)` 显式超时（默认 30s）；无重试（无重试风暴面）；重入由 #3b 运行标记覆盖（dispatch 在 try 块内、finally 释放之前） | **防护到位，不立案** | 超时/隔离/无重试三要素齐备；事务外执行不占 store 连接（既有裁定注释）；"webhook 先于 commit 发出"为注释显式登记的已接受残余，非本维度缺陷 |
| 6 | ReconciliationExecutor 重入 | `NopMetaReconciliationConfigBizModel.executeReconciliation:106-162` | **纯同步 @BizMutation**（仅 web worker 线程，无 job 注册点——grep 全单元确认 reconciliation 无任何 `addJob`）；执行器无状态（`ReconciliationExecutor` 纯组件，BizModel 字段 `@PostConstruct` 一次性赋值后不变） | **无共享面，设计上无需重入保护** | 同 config 并发触发两次 = 两个独立事务各写一行 `NopMetaReconciliationResult`，无共享可变状态、无 UK 冲突契约（结果行本为追加型历史记录），语义自洽；无调度触发面故无 cron 重叠问题 |
| 7 | nop-rule 执行面异步边界 | `RuleServiceImpl.executeRule:58-84` / `RuleManager` / `RuleRuntime` / `RuleOutputAction` / `DaoRuleModelLoader` | **全同步**：每请求新建 `RuleRuntime`（请求内单线程消费），规则模型经平台 `ResourceComponentManager` 缓存，编译 compile-once 由 `synchronized(ruleModel)` 守卫（RuleManager:55-65） | **无异步边界** | main 面 0 CF/异步原语；`saveLogMessages` 同步调用；`RuleModel.executableRule` 非 volatile 但读写全部在 `RuleManager.getExecutableRule` 的 `synchronized(ruleModel)` 内（monitor 内 happens-before 成立；全单元无绕过守卫的裸读调用点——grep 证实） |
| 8 | QualityAlertWorkflowProcessor（工作流触发面） | `createAlertWorkflow:75-91`（`wfManager.newWorkflow + wf.start` 同步）`reJudgeFailClosed:139-152` | 同步，调用线程执行 | **无异步边界** | `wf.start` 同步启动（平台语义）；`reJudgeFailClosed` 由 xwf c:script 调用，同步；无规则执行→告警流→再执行规则的递归环（reJudge 只更新既有 result 行，不触发新告警） |
| 9 | MetaModelChangedEventPublisher（事件面） | `publishEventWithSnapshots:123-141` | 同步 DB 行写入（主路径刻意不经 IMessageService，类 javadoc 明示"首版无订阅者"） | **无异步边界**；无界增长面见 CL-203 | 事件 = 同步 append 行，无消息监听器/回调 |

**清单小结**：执行模型盘点 9 项；7 项判定为「平台机制已防护 / 无异步边界 / 已声明边界」（判定依据见上表），2 项立案（C4-001、C4-002），0 项 P0/P1。

---

## 2. 发现条目

### [C4-001] 单例 BizModel/处理器惰性初始化缝的 check-then-act 竞态（非 volatile 字段 + 双线程可见性缺口）

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaQualityCheckpointBizModel.java:531-555`（`ensureActionDispatcher`/`ensureTableRefExecutor`/`ensureCheckpointExecutor`，字段声明 154/157/160 行）；同族：`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaQualityRuleBizModel.java:118`（`tableRefExecutor` + `ensureTableRefExecutor`）；`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/QualityAlertWorkflowProcessor.java:55`（`tableRefExecutor` + `ensureTableRefExecutor`，见文末 173-177 行）
- **证据片段**:

  ```java
  // NopMetaQualityCheckpointBizModel.java:531-538（字段 private CheckpointActionDispatcher actionDispatcher; L160，非 volatile）
  private CheckpointActionDispatcher ensureActionDispatcher() {
      if (actionDispatcher == null) {
          actionDispatcher = new CheckpointActionDispatcher(httpClient, messageService);
          actionDispatcher.configureWebhookSsrf(webhookAllowedHostCsv, webhookTimeoutSeconds);
          actionDispatcher.configureRedirectPolicy(httpClientFollowRedirects);
      }
      return actionDispatcher;
  }
  ```

- **严重程度**: P2
- **并发场景**: 平台 job 调度池线程（cron 触发的 `executeScheduledCheckpoint`）与 HTTP web worker 线程（GraphQL 手动 `executeCheckpoint`/`executeQualityRule`）并发进入同一单例 BizModel；R4.3 运行标记仅按 checkpointId 互斥，**不同 checkpoint 的并发执行、以及手动与 cron 的不同 checkpoint 交叉**都会并发命中这些未同步的惰性初始化缝。
- **触发时序**: T1（job 池线程）执行 checkpoint-A 进入 `ensureActionDispatcher`，L532 判空通过，L533 完成赋值但 L534-535 的 `configureWebhookSsrf/configureRedirectPolicy` 尚未执行 → T2（web 线程）执行 checkpoint-B 进入同方法，L532 读到 `actionDispatcher != null`（普通字段读，无 happens-before，可能读到 T1 赋值后的中间态）→ T2 使用**未配置 SSRF allowlist/超时/重定向策略**的 dispatcher 执行 webhook。同型交错适用于三处 ensure* 缝（重复构造：T1/T2 同时判空 → 各自 new 一个 executor/dispatcher，后写覆盖先写）。
- **现状**: 三类共 5 处惰性初始化缝均为「检查-然后-行动」+ 赋值后二次 configure 的复合序列，字段全部非 volatile、无同步。
- **风险**: (a) 重复构造无状态 executor/dispatcher——本身无害但浪费且语义混乱；(b) 真实风险是**不安全发布**：另一个线程可能拿到"构造完成但 configure 未执行"的 dispatcher。后果方向经核实为**fail-closed 兜底**（`webhookAllowedHostsCsv` 默认 ""=拒绝全部内网、`followRedirectsEnabled` 默认 false=拒绝跟随、`webhookTimeoutSeconds` 经 `configureWebhookSsrf` 的 `>0` 判断兜底 30s），故安全态势不劣化，主要是"配置了 allowlist 却偶发按未配置行为拒绝"的可用性毛刺与可见性理论风险。此即本条判 P2 而非 P1 的原因。
- **建议**: 三选一（优先 a）：(a) 平台惯例对齐——将三字段改为 `volatile` 并把 configure 合并进构造参数（`new CheckpointActionDispatcher(httpClient, messageService, ssrfConfig, redirectPolicy)`），消除"赋值后二次可变配置"序列；(b) `synchronized` 方法级守卫（低频初始化路径，粗锁可接受）；(c) IoC 构造期装配（把 dispatcher/executor 改为独立 bean 注入），彻底消除惰性缝。注意 QualityAlertWorkflowProcessor 与 NopMetaQualityRuleBizModel 是 F3 同族兄弟，必须一并修。
- **失效面枚举**（grep `ensure` + 非 volatile 惰性字段全单元清扫）: ① `NopMetaQualityCheckpointBizModel.actionDispatcher`（L160）——本条立案；② `NopMetaQualityCheckpointBizModel.tableRefExecutor`（L154）——同缝，本条覆盖；③ `NopMetaQualityCheckpointBizModel.checkpointExecutor`（L157）——同缝（无 configure 后置步，仅重复构造+可见性），本条覆盖；④ `NopMetaQualityRuleBizModel.tableRefExecutor`（L118）——同缝，本条覆盖；⑤ `QualityAlertWorkflowProcessor.tableRefExecutor`（L55）——同缝，本条覆盖。`NopMetaReconciliationConfigBizModel.reconciliationExecutor`（L85）经 `@PostConstruct` 一次性赋值，IoC 初始化完成先于 bean 发布，**不在失效面内**。未发现第 6 处同族。
- **信心水平**: 确定（机制）；后果轻微（fail-closed 兜底已逐项核实）
- **误报排除**: 不属于"无状态单例被并发调用"校准项——问题不在并发调用本身，而在惰性初始化复合操作无同步；也不属于"低频路径粗锁合理"校准项——此处恰恰没有任何锁；触发时序具体（不同 checkpoint 的 cron×手动交叉是真实存在的入口组合，非纯理论）。
- **复核状态**: 已复核（2026-10-22 C4 独立复核）：**保留 P2**，同族合并归 Phase 6（主登记 = C1-102，与 C1 维度复核裁定一致）。证据逐行核实成立；行号订正：兄弟载体字段实际位于 `NopMetaQualityRuleBizModel.java:109`（原文 :118）、`QualityAlertWorkflowProcessor.java:56`（原文 :55），ensure 方法在 `:303-305`/`:167-172`，不影响实体与语义。

### [C4-002] checkpoint 全编排（含外部库查询与 webhook HTTP）同步占住共享平台 job 调度池——跨业务任务隔离缺失

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/MetaQualityCheckpointScheduler.java:204-234`（同步包装入口）+ `nop-job/nop-job-local/src/main/resources/_vfs/nop/job/beans/app-local-scheduler.beans.xml:13-14`（core=max=4 共享池）
- **证据片段**:

  ```java
  // MetaQualityCheckpointScheduler.java:215（整条编排在本调用内同步完成，运行于调度池线程）
  return checkpointBizModel.executeCheckpoint(checkpointId, null, null);
  // BeanMethodJobInvoker.java:126（同步返回值直接包 completedFuture，无二次派发）
  return CompletableFuture.completedFuture(result);
  ```

- **严重程度**: P3
- **并发场景**: 全宿主所有 checkpoint job + `scheduler.yaml` 注册的宿主本地 job 共享 `nop-job-local-scheduler` 4 线程池；单个 checkpoint 运行 = N 条规则×外部数据源 JDBC 查询（`TableReferenceExecutor` 同步取连接）+ 逐表 autoScore + 每 webhook action 至多 30s HTTP（`CheckpointActionDispatcher:249`）。
- **触发时序**: T1-T4（4 个池线程）分别执行 4 个 cron 对齐（如同为整点触发）的长 checkpoint（大量规则 + 慢外部库 + 多个 30s webhook）→ 池满 → 宿主在 `scheduler.yaml` 注册的第 5 个 job 的到期 fire 无线程可用，延迟至某 checkpoint 完成后才执行。后果仅为**触发延迟**（不堆积：LocalJobScheduler 每 job 至多 1 个 pending fire，完成后再排程），无数据风险、无死锁。
- **现状**: 平台池配置合格（有界、命名、destroy 钩子齐全），但单元把长阻塞 I/O 编排放上共享调度池，未声明隔离策略。
- **风险**: 多 checkpoint 整点对齐 + 外部源变慢时，宿主其他定时任务（与元数据质量无关的业务 job）被系统性延迟；延迟量正比于最慢 checkpoint 时长，运维不可见（无池深度/延迟指标挂接）。
- **建议**: 沿平台设施给 checkpoint 编排配独立执行器（`io.nop.commons.concurrent.executor.IThreadPoolExecutor` + `ThreadPoolConfig` 命名池），`BeanMethodJobInvoker` 链路中由 bean 方法内部 `submit` 到专用池后返回 `CompletionStage`（invoker 已原生支持 CompletionStage 返回值，`toCompletionStage` 分支）；或至少在 owner doc 声明"checkpoint 与宿主 job 共享 4 线程池、慢外部源会延迟其他 job"的包络。量化口径：每 cron tick 占用 = 规则数×(JDBC 时延+判定) + webhook 数×30s 上限。
- **失效面枚举**: 共享池消费者全集 = ① checkpoint job（本条）；② 宿主 scheduler.yaml job（`LocalJobConfigLoader`，宿主域）；③ 无其他——grep 全单元 main 面无第二个 `addJob` 调用点、无其他 `IScheduledExecutor` 消费。nop-rule 不消费调度池。同族覆盖完整。
- **信心水平**: 确定（机制与时序）；影响评级保守（当前声明场景为单机嵌入式 + checkpoint 数量受元数据规模约束）
- **误报排除**: 不属于"有界声明存在的池"校准项——池本身有界，问题不是 OOM 而是任务隔离（C4 清单第 4 项"一个慢业务饿死其他业务"的定向检查命中）；也不属于平台设施自身缺陷——平台池配置无问题，立案对象是单元的使用方式未做隔离且无声明。
- **复核状态**: 已复核（2026-10-22 C4 独立复核）：**保留 P3**。同步返回链（`MetaQualityCheckpointScheduler.java:215` → `BeanMethodJobInvoker` completedFuture）与共享池 core=max=4（`app-local-scheduler.beans.xml:13-14`）均重演确认，判级与失效面枚举维持。

---

## 3. 维度集外强信号附记（CL 线索，供主 agent 分派对应维度复核；不占本维度发现计数）

### [nop-rule-metadata-CL-201]（C2 形态·平台 nop-job）job 监视器内执行整个 bean 方法——锁扩展至分钟级

- **文件**: `nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:209-224（fireNow）、301-311（scheduleNext 的定时回调）、313-346（executeJob）`
- **证据片段**: `synchronized (job) { ... executeJob(job); }`（fireNow L215-223 与定时回调 L302-307 同型）；`executeJob` 内 `job.invoker.invokeAsync(ctx)` 对 `BeanMethodJobInvoker` 同步方法形态即在持锁状态下跑完整个 checkpoint 编排。
- **信号**: 持 `job` 监视器执行分钟级业务（外部 JDBC + 30s×N webhook）→ 其他线程的 `suspendJob`/`cancelJob`/`removeJob`/`getJobState`/`awaitIdle` 全部阻塞至编排结束。无死锁（单锁无环），属锁扩展/可观测性问题。平台 nop-job 域，建议纳入 nop-job 审计批次；本单元使用方式（checkpoint 慢编排）是其放大器。
- **信心水平**: 确定（机制）；建议处置：watch，移交 nop-job 批次

### [nop-rule-metadata-CL-202]（C1 形态·nop-rule）RuleRuntime 多线程注释与实现不符 + RuleModel.executableRule 守卫外的公开读

- **文件**: `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/execute/RuleRuntime.java:68-69（"taskRt可能会被多线程访问，所以这里scope线程安全"注释）、41/43/45（logMessages ArrayList / outputLists HashMap / outputs LinkedHashMap 均非线程安全）`；`nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/model/RuleModel.java:28（executableRule 非 volatile，getter public）`
- **信号**: ① 注释声称 runtime 可能被多线程访问，但仅 scope 做了并发化，`logMessages`/`outputs` 容器仍是普通集合——若下游（如 nop-task taskRt 共享形态）真实多线程访问即竞态；当前 nop-rule main 面执行链为请求内单线程，未见实际多线程消费者，故不构成本单元活动缺陷。② `RuleModel.getExecutableRule()` 是 public 方法，守卫依赖调用方自觉进 `RuleManager.getExecutableRule` 的 `synchronized(ruleModel)`；当前全仓 main 面无绕过调用点（grep 证实），属守卫依赖调用方自觉的 F10 形态隐患。
- **信心水平**: 有趣的猜测（①，待 C1/C10 维度核实消费者面）；确定（② 的守卫结构事实）
- **建议处置**: 移交 C1 维度并入其共享状态清单；C10 核对 TestPerTransform/规则并发测试是否覆盖

### [nop-rule-metadata-CL-203]（C7 形态·nop-metadata）元数据变更事件表 append-only 无清理路径

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/event/MetaModelChangedEventPublisher.java:123-141（saveEntity 追加）`
- **信号**: `NopMetaModelChangedEvent` 每次关键 mutation/save/delete 追加一行（含 before/after 全实体 JSON 快照），全单元 grep 未见任何清理/TTL/归档路径——F4 无界账本形态（天/周级长时运行单调增长，快照 JSON 体积放大写放大）。首版设计显式选择 DB 行为事件主路径（javadoc D2），但未声明留存包络。
- **信心水平**: 很可能；建议处置：移交 C7 维度（长时运行稳定性）复核清理策略与留存上限声明

### [nop-rule-metadata-CL-204]（C1 形态·nop-metadata + 基线口径订正）public static 非 final 可变配置字段未被基线细化口径覆盖

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/CrossDbConfigHolder.java:5`
- **证据片段**:

  ```java
  public class CrossDbConfigHolder {
      /** 跨库拼接单侧结果集行数上限（防 OOM，超限显式失败）。 */
      public static int maxCrossDbRows = 10000;
  ```

- **信号**: main 面 public static 非 final 可变字段；附录 C 两次基线细化（容器类型过滤 + static 方法剔除）均未命中 primitive 形态。live 核实：main 面**无写入点**（仅 `MetaJoinExecutor:74/404/436` 与 `CrossDbJoinMerger` 读），当前为"事实上常量"，无活动数据竞争；但该字段是留给测试/宿主的全局可变缝，任何运行时写入方（如未来 config 装配）都将引入无同步的全局可变状态（int 写原子但无可见性保证，且各请求读取值可不同——上限语义漂移）。
- **信心水平**: 确定（字段存在与无写入点）；建议处置：移交 C1 维度；修复方向为改 `@InjectValue` 配置注入或 static final + 显式 config 对象
- **同族扫描**: `GranularityBucketing` 两个 static final HashMap（L30/32）经核实为 static 块内一次性填充后只读——不在失效面；`NopMetaDataSourceBizModel.EXTERNAL_TABLE_UPSERT_LOCKS`（L1053）为已裁定有界键空间 + 单实例 baseline（源码 javadoc 显式声明边界），按方法论"已声明边界非缺陷"不立案

---

## 4. 本维度未立案面汇总（复核阶段可直接引用判定依据）

| 面 | 未立案理由 |
|---|---|
| webhook 无重试 | 单次投递 + per-action 隔离 + 错误入摘要 errors——重试缺失是有意的最小语义，无重试风暴面；如需投递保证应走 post-commit + 持久化 outbox（已在 dispatcher 类注释登记为暂缓决策点） |
| "webhook 先于事务 commit 发出"残余 | 源码 javadoc 显式登记（BizModel:496-506 / Dispatcher:37-46），含 post-commit 改法与 R4.3 标记释放时序的联动冲突分析——已声明残余，非缺陷 |
| 运行标记 finally 无条件 remove | fail-fast 语义下仅持有者能到达 finally，无 ABA 窗口 |
| LocalJobScheduler 多实例不共享 | 类 javadoc 明示"单机嵌入式场景，分布式用 coordinator/worker"——已声明边界 |
| cron 快于执行时长的 tick 丢失无补偿 | 平台"完成后再排程"语义，checkpoint 幂等快照场景无害；补偿需求未在任何 owner 契约出现 |
| `executeQualityRulesForDataSource` 批量路径 | 与单规则路径同构（per-rule try/catch + flushSession/clearSession 隔离），同步执行，无独立执行模型面 |
| 平台池 destroy 用 `shutdownNow` | 在途 checkpoint 任务被中断时依赖 ORM 事务回滚兜底；JVM 退出场景，非长时运行缺陷面 |

---

## 5. 结论

- 本单元 C4 预注**成立未被订正**：main 面 0 自建线程/线程池/异步原语，全部调度收敛于平台 job 机制，全部执行同步。
- 立案 2 条（C4-001 P2、C4-002 P3），无 P0/P1——单元的执行模型纪律整体健康：同 checkpoint 重叠有平台串行 fire + 单元级运行标记双保险，job 永久 FAILED 风险已被 catch-all 包装消除，webhook 分发具备超时/隔离/无重试三要素，对账路径无共享面，nop-rule 全同步无异步边界。
- 附记 CL 线索 4 条（CL-201 平台锁扩展、CL-202 C1 形态、CL-203 C7 形态、CL-204 C1 形态+基线口径订正）。

---

# C4 深挖第 2 轮

- **轮次范围**：主 agent 下发的 5 个扩大面——①平台 job 机制单元内消费面全查；②异步回调隐藏形态（XPL 异步/GraphQL dataLoader/事件监听器线程语义）；③MetaQualityRuleExecutor/QualityResultWriter 执行链隐式并发；④CheckpointActionDispatcher 的 webhook 实现类（HTTP client 面：超时/连接池归属）；⑤QualityAlertWorkflowProcessor 推进线程语义。
- **方法声明**：逐项核查结论见文末清扫表（终止信号载体）。本轮读取了平台实现（nop-job-local、nop-http-api/client-apache/client-jdk、nop-dao SimpleDataSource、nop-biz BizModelToGraphQLDefinition）以验证使用假设，按口径"平台设施自身不重审、只审使用方式"；其中 SimpleDataSource/超时链的核实结论反过来支撑了单元级发现 C4-R2-001 的"无兜底"判定。
- **盲审声明**：本轮未读取 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 及其他维度文件；只读本维度文件（第 1 轮产物）。只读审计，未修改任何产品文件。
- **核实基线**：live code，与第 1 轮同快照（commit 81a7c228e7 基线）。

## 发现条目

### [C4-R2-001] 质量执行链外部 JDBC 查询阶段 socket 读无超时上限——外部源"已连接后挂起"将无限期占住共享调度池线程并卡死该 job 的控制面，且无任何层兜底

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java:199-212`（withConnection 未注入任何 socket 级超时）；`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/datasource/SimpleDataSource.java:41-68`（DriverManager 直连、无池）、`:81-83`（`setLoginTimeout` 为 no-op 空实现）；`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java:124,140-153`（`DEFAULT_LOGIN_TIMEOUT_SECONDS=5` 仅经 `DriverManager.setLoginTimeout` 界定**建连**阶段）
- **证据片段**:

  ```java
  // MetaDataSourceConnectionProcessor.java:199-212（唯一外部连接获取点，finally 只关连接、不设读超时）
  public void withConnection(String datasourceType, String connectionConfig,
                             BiConsumer<Connection, DatabaseMetaData> action) {
      DataSource dataSource = buildDataSource(datasourceType, connectionConfig);
      Connection conn = null;
      try {
          conn = dataSource.getConnection();
          DatabaseMetaData metaData = conn.getMetaData();   // ← 第一轮 socket 读，无超时
          action.accept(conn, metaData);
      ... finally { IoHelper.safeCloseObject(conn); }
  // SimpleDataSource.getConnection → DriverManager.getConnection(url, props)：socketTimeout 走驱动默认 0=无限
  ```

- **严重程度**: P2（判级边界说明见"风险"末句，复核可裁量上调）
- **并发场景**: 三类执行上下文共享同一无界 socket 读面——(a) `nop-job-local-scheduler` 共享调度池线程（cron checkpoint，`MetaQualityCheckpointExecutor.execute:129-186` 的 for 循环内逐规则 `TableReferenceExecutor.execute` → `withConnection`）；(b) HTTP web worker 线程（手动 `executeQualityRule`/`executeQualityRulesForDataSource`/query/join 族）；(c) HTTP web worker 线程 + 工作流 approve store 事务内（xwf verify 步 `reJudgeFailClosed`，见 CL-206）。
- **触发时序**: T0 外部数据源接受 TCP 连接后停止响应（典型：DB 宿主 VM 暂停/恢复无 RST、LB 静默丢弃半开连接、驱动登录阶段后服务端挂起——注意"建连被防火墙 DROP"子场景已被 `DriverManager.setLoginTimeout(5)` 界定为 5s 失败，**本发现的触发子场景是"TCP 已建立后的 socket 读"**，驱动默认 socketTimeout=0）→ T1 job 池线程进入 `conn.getMetaData()` 或 `MetaQualityRuleExecutor.queryLong/queryTimestamp/querySingleValue` 的 `executeQuery()`，永久阻塞在 socket read → T2 该 job 的 `LocalJobScheduler.scheduleNext` 回调持有 `synchronized(job)`（`LocalJobScheduler.java:302-307`，同步方法形态下 `invokeAsync` 返回 completedFuture、整个编排含无界读都在监视器内执行）→ T3 运维对该 job 的 `suspendJob/cancelJob/removeJob/getJobState` 全部阻塞在监视器上，控制面不可干预；T4 同类挂起重复 4 次后 `nop-job-local-scheduler` 4 线程全满，宿主所有定时任务永久停摆；T5 无任何 watchdog/超时层能打破（见"现状"）。
- **现状**: 三层均无兜底——(1) JDBC 层：`buildDataSource` 只透传 url/username/password/driverClassName（`:289-296`），不设 socketTimeout/connectTimeout 属性；唯一的超时是构造器副作用 `DriverManager.setLoginTimeout(5)`（`:141,148-152`），JDBC 语义只作用于**建连登录阶段**，不覆盖建连后的 socket 读；(2) 连接配置层：`connectionConfig` 若未在 jdbcUrl 查询参数里显式带驱动级 `socketTimeout`（MySQL/PG 默认 0=无限），即无界，且 owner doc（`docs-for-ai/03-modules/nop-metadata.md`）无任何连接超时/挂起包络声明；(3) 调度层：nop-job owner doc 明示 LOCAL 模式"不支持 Misfire 补偿、阻塞策略、超时控制、分片——这些是分布式模式的能力"（`docs-for-ai/03-modules/nop-job.md:213`），`MetaQualityCheckpointScheduler.executeScheduledCheckpoint` 也无时间预算包装。
- **风险**: 外部源故障（生产环境常见的网络分区/宿主冻结形态之一）→ 挂起不可检测、不可取消、随故障扩大累积至全调度池停摆——对照 F5（挂死对健康检测不可见）与 F8（错误路径资源占用）。同时与同单元 webhook 通道形成不对称：`CheckpointActionDispatcher:249` 为 webhook 显式设 30s 超时（`request.setTimeout(webhookTimeoutSeconds*1000L)`），而作为 checkpoint **主 I/O** 的外部 JDBC 查询无任何预算。判 P2 而非 P1 的理由：触发依赖外部源故障行为而非主路径输入、后果为可用性损失（可经进程重启恢复）而非数据损坏、建连子阶段已有 5s 界；若部署环境将跨网络外部源故障视为常态事件，复核可按"错误路径资源永久占用 + 控制面锁死"上调 P1。
- **建议**: 三选一（优先 a）：(a) 单元级默认预算——`withConnection` 内对建连后的连接设 `conn.setNetworkTimeout(...)`（JDBC 标准 API，覆盖所有后续 socket 读；超时值可走 `@InjectValue` 配置如 `nop.metadata.datasource.network-timeout-ms`，默认参照 webhook 的 30s 惯例）；(b) `MetaQualityCheckpointScheduler.executeScheduledCheckpoint` 包装执行时间预算（超时中断不能解除 socket read 阻塞，需配合 a 或改用连接级方案，故 b 单独不充分）；(c) 至少在 owner doc 声明"外部源查询 socket 读无超时上限、依赖驱动 URL 参数自救"的包络。注意修复属 F3 同族面：全部 13 处 `withConnection` 消费点 + `testConnect` 共享同一获取点，单点收口于 (a) 即全覆盖。
- **失效面枚举**（grep `withConnection(` 全单元 main 面 + 共享获取点）: ① `NopMetaQualityRuleBizModel:245`（单规则执行）② `MetaQualityCheckpointExecutor→TableReferenceExecutor:128`（checkpoint 编排，本条主场景）③ `QualityAlertWorkflowProcessor.reJudge→TableReferenceExecutor:128`（xwf verify，叠加 CL-206 事务内占用）④ `NopMetaEntityQueryAction:108,130`（实体查询预览）⑤ `MetaJoinExecutor:344,433`（跨库 join）⑥ `NopMetaDataSourceBizModel:646,758`（collectCatalog/sync）⑦ `SqlViewFieldTypeInferrer:122`（SQL 视图类型推断）⑧ `MixedSameDbJoinAggregationProcessor:121,176` ⑨ `ExternalExternalJoinAggregationProcessor:88` ⑩ `ExternalAggregationProcessor:67` ⑪ `testConnect`（连接测试）。全部经同一 `withConnection`/`buildDataSource` 获取点，(a) 方案单点覆盖。nop-rule 单元无外部 JDBC 面（其规则执行走平台内存模型），不在失效面内。
- **信心水平**: 确定（三层无兜底与触发时序逐层核实）；严重程度判级处在 P1/P2 边界，已显式标注裁量依据
- **误报排除**: 不属于"平台设施自身不重审"豁免——立案对象是**单元的使用方式**：单元为外部 I/O 编排选择了零预算执行模型，且同单元 webhook 通道自证了 30s 预算是本单元已建立的惯例标准；也不属于"已声明边界"——owner doc（nop-metadata.md/nop-job.md）明示的只是"LOCAL 模式无 job 级超时"这一平台边界，没有任何文档声明"外部 JDBC 查询可以无界挂起"；更不属于"有界声明存在"校准项——本处恰恰没有任何界。
- **复核状态**: 已复核（2026-10-22 C4 独立复核）：**保留 P2，不上调**。按任务书专项核实平台连接获取链：外部 `DataSource.getConnection()` 在 nop-metadata main 面仅 `MetaDataSourceConnectionProcessor.java:204`（withConnection）与 `:239`（testConnect）两处、同经 `buildDataSource` 收口；平台 main 面 `setNetworkTimeout(` grep = 0 命中（nop-persistence/nop-metadata/nop-core/nop-core-framework），`SimpleDataSource.setLoginTimeout` 为空实现（`SimpleDataSource.java:81-83`），`DriverManager.setLoginTimeout(5)`（`MetaDataSourceConnectionProcessor.java:148-153`）按 JDBC 语义仅界建连登录阶段——**「无任何层兜底」成立，不降级**；监视器内占池与控制面锁死经 `LocalJobScheduler.java:302-307/:215-223/:143/:155/:184/:435` 重演确认。触发依赖外部源故障、后果可重启恢复，初审判级边界说明成立，不上调 P1。详见文末「维度复核结论」。

### [C4-R2-002] 外部连接零复用：每规则每次执行新建 SimpleDataSource + 物理连接——C4-002 池占用的乘数放大器

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java:269-297`（`buildDataSource` 每次调用 new SimpleDataSource）、`:199-212`（withConnection 用完即关）；`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/MetaQualityCheckpointExecutor.java:129-186`（for 循环逐规则触发）
- **证据片段**:

  ```java
  // MetaDataSourceConnectionProcessor.java:289-296（每次 withConnection/testConnect 都新建）
  SimpleDataSource ds = new SimpleDataSource();
  ds.setUrl(jdbcUrl);
  ds.setUsername(username);
  ds.setPassword(password);
  if (driverClassName != null && !driverClassName.isEmpty()) {
      ds.setDriverClassName(driverClassName);   // ← 每次 Class.forName 驱动加载检查
  }
  return ds;
  // SimpleDataSource.getConnection → DriverManager.getConnection：无池，物理建连+认证握手
  ```

- **严重程度**: P3
- **并发场景**: checkpoint 编排在单个调度池线程内**串行**执行 N 条规则；同数据源的 N 条规则 = N 次（配置解析 + AR-02 URL 校验 + 凭证解析 + `Class.forName` + TCP 建连 + 认证握手 + 断开），无任何复用。
- **触发时序**: 单次 checkpoint 含同外部源 50 条规则、单次握手 200ms（同机房）→ 仅建连开销即 10s 串行占住 1 个池线程（未计查询本身）；握手 2s（跨机房/慢认证）→ 100s。每次建连窗口同时是 C4-R2-001"accept-后-挂起"的暴露窗口——建连次数与挂起概率线性正相关。
- **现状**: 连接生命周期为 per-action 短连接（finally 关闭，无泄漏），但零复用；owner doc 无"短连接为有意设计"的声明（nop-metadata.md 仅描述 14 处消费点结构）。
- **风险**: 放大 C4-002 已立案的共享池任务隔离问题（池占用 = 规则数×(握手+查询)+webhook 数×30s，其中握手项本可为零）；手动批量路径（web worker）同乘数占用请求线程。
- **建议**: 按数据源标识（dataSourceId 或 config 摘要）缓存 `DataSource` 实例（平台 `ICache` + `CacheConfig`，键空间=元数据源数量天然有界），或引入池化 DataSource；注意凭证轮换/配置变更时的缓存失效配对（F8：更新 NopMetaDataSource 配置须 evict 对应条目）。量化口径：节省 = 规则数×握手时延；建议以 nop-benchmark 形态验证（登记基准缺口）。
- **失效面枚举**: 与 C4-R2-001 同一 14 处消费点全集（①-⑪，见上条），全部经 `buildDataSource` 单点受益。
- **信心水平**: 确定（机制与乘数可复现）；性能收益量化依赖部署握手时延，标注为估算链
- **误报排除**: 不属于"热路径分配浪费按 C9 报告"的维度越界——本条的立案轴是 C4-002 的执行模型治理（共享池占用乘数），连接复用是执行器治理的组成部分；也不属于"池化是普适最佳实践"的泛化建议——同源多规则串行复用同一连接是可量化的结构性收益，非风格偏好。本条与 C4-R2-001 根因不同（一个是有界资源的浪费、一个是无界等待），不合并且不互替。
- **复核状态**: 已复核（2026-10-22 C4 独立复核）：**保留 P3**。`buildDataSource` 每调用 new `SimpleDataSource`（仅透传 url/username/password/driverClassName）、withConnection finally 关闭、`MetaQualityCheckpointExecutor.java:129-186` 单线程 for 循环逐规则建连——乘数机制重演确认，判级维持。

## 维度集外强信号附记（本轮新增）

### [nop-rule-metadata-CL-205]（C1 形态）bean 构造器写 JVM 全局静态：DriverManager.setLoginTimeout 副作用

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java:140-153`
- **证据片段**: `public MetaDataSourceConnectionProcessor() { setGlobalLoginTimeout(); }` → `DriverManager.setLoginTimeout(DEFAULT_LOGIN_TIMEOUT_SECONDS); // =5`
- **信号**: `DriverManager.loginTimeout` 是**进程级全局**静态可变状态。本单元 bean 构造器无条件改写它，影响宿主内**所有** DriverManager 路径连接（不止本单元）；反向面：任何其他组件（或未来重构移除本构造器调用）把它改回 0，本单元 C4-R2-001 的建连 5s 界同时失效——双向漂移均无守卫、无同步。建议处置：移交 C1 维度（全局可变状态清单）；修复方向为显式在连接属性/连接级设超时而非全局静态（与 C4-R2-001 建议 (a) `setNetworkTimeout` 收口同工）。
- **信心水平**: 确定（机制）

### [nop-rule-metadata-CL-206]（C14/C3 形态）xwf verify 步在 approve 请求的 store 事务内执行外部 JDBC

- **文件**: `nop-metadata/nop-metadata-service/src/main/resources/_vfs/nop/wf/qualityBreachApproval/v1.xwf:44-63`（verify 步 c:script 同步调 `reJudgeFailClosed`）；`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/QualityAlertWorkflowProcessor.java:105-154`
- **证据片段**: verify 步 transition 链：owner-investigate 的 agree 动作 → 进入 verify → `<c:script> const passed = svc.reJudgeFailClosed(ruleId, resultId); </c:script>` → reJudge 经 `TableReferenceExecutor` 建外部连接执行判定 SQL。
- **信号**: verify 步推进同步发生在 owner-investigate 的 approve 动作请求线程上（无异步推进、无定时推进——本轮核实），外部 JDBC 查询（可能慢源/受 C4-R2-001 无界 socket 读影响）全程持有 approve 动作的 store 事务连接 = 长事务形态（维度 14"事务中包含远程调用"定向项命中）。外部连接自身不占 store 连接（withConnection 自建），但 store 事务连接被占用时长 = 外部查询时长（含无界挂起情形）。
- **信心水平**: 确定（事务占用机制）；建议处置：移交维度 14（异步与事务模式）复核长事务包络声明与 fail-closed 路径

## 本轮清扫表（扩大面逐项核查结论——终止信号载体）

| # | 扩大面检查项 | 核查口径（grep/读码） | 结论 |
|---|---|---|---|
| 1a | job 注册面全查：还有哪些类向 LocalJobScheduler/JobManager 注册 | grep `addJob\|removeJob\|fireNow\|IJobScheduler\|IJobManager\|JobManager` 全单元 main 面 | **无新发现**。唯一注册点仍为 `MetaQualityCheckpointScheduler`（第 1 轮判定维持）；无 IJobManager/JobManager 消费者 |
| 1b | 一次性 job / 延时 job | 读 `buildJobSpec:349-365`：`TriggerSpec` 仅设 cronExpr；grep 单元无 simple/one-time/delay trigger 形态 | **无新发现**。全部注册为 cron 触发；`fireNow` 仅测试经 `getScheduler()` 使用（第 1 轮 3a 已覆盖其防重叠） |
| 1c | 运行时增量注册（checkpoint save → addJob）与调度线程在途 fire 的交叉 | `MetaQualityCheckpointScheduler:160-313`（web 线程 addJob/removeJob）× `LocalJobScheduler.java:94-122`（addJob 对 existing/raced job 的 `synchronized` 处理）+ 全类 20 处 per-job monitor、`ConcurrentHashMap jobs`、`volatile active` | **平台机制已防护，无发现**。注册/注销与 fire 串行化于同一 per-job 监视器；`allowUpdate=true` 对 RUNNING job 的语义平台已有注释（第 1 轮 3c 引用） |
| 2a | XPL/xlib/xbiz/xwf 模板中的异步调用 | grep `async\|Async` over `*.xlib/*.xpl/*.xwf/*.xbiz`（非 _gen） | **0 命中，无发现**。模板面全同步 |
| 2b | GraphQL dataLoader 线程语义 | 单元唯一 `@BizLoader` = `NopRuleNodeBizModel.predicateLabel:39-49`；平台装配 `BizModelToGraphQLDefinition.buildFetcher:144-178`：非集合型 ContextSource → `EvalActionDataFetcher`（同步 DataFetcher，请求线程串行执行）；`context.getCache()` 为 IServiceContext 请求域，无跨请求共享 | **无发现**。批量形态（集合型 ContextSource → BeanMethodBatchFetcher）单元未使用；nop-graphql-orm 的 OrmBatchLoader 属平台设施，单元仅间接受益，不审 |
| 2c | 事件监听器线程语义 | `MetaModelChangedEventPublisher` 同步 DB 写（第 1 轮 #9 已判）；nop-rule main 面 grep `Listener/listener/subscribe/observe` = 0；xwf `onDisagree` listener 在 approve 请求线程同步执行 | **无发现**。全部事件面同步、调用线程执行 |
| 3 | MetaQualityRuleExecutor / QualityResultWriter 隐式并发 | 两类均无实例可变状态（executor 全 static 工具方法 + static final 常量；writer 无字段）；grep `parallelStream\|.parallel(\|commonPool\|ForkJoin` 单元 main 面 = 0；`MetaQualityCheckpointExecutor.execute:129-186` 单线程 for 循环 + per-rule try/catch，迭代中唯一回调 `resultWriter.append` 为同线程同步调用；链尾 `withConnection` finally 关连接（无泄漏） | **无新发现**。执行链单线程、无迭代中异步回调、无共享可变状态 |
| 4 | webhook HTTP client 面：超时/连接池归属 | 单元使用：`CheckpointActionDispatcher:249` 显式 per-request timeout。平台三实现核实：Apache `toSimpleRequest:186-191`（request.timeout → `setResponseTimeout`）+ 连接池 `PoolingAsyncClientConnectionManager`（`createConnectionManager:112-128`，归属 `ApacheHttpClient` bean，`@PreDestroy close(GRACEFUL)` 生命周期对称）；JDK 实现同兑现（`JdkHttpClient:218-221`）；`IHttpResponse`（nop-http-api `:12-26`）为全缓冲抽象（Apache 走 `SimpleHttpResponse` 内存 body），**无连接/流句柄泄漏面**，不关闭安全；connect 超时平台默认 10s 有界（`HttpClientConfig:37`） | **使用方式合格，无发现**。dispatcher 不自建池/线程，超时三要素（response 30s + connect 10s + 全缓冲响应）齐备；建连后的 JDBC 读无界问题不在本面（见 C4-R2-001） |
| 5 | QualityAlertWorkflowProcessor 推进线程语义 | `createAlertWorkflow:67-98` 同步于调用线程（唯一生产调用点 `NopMetaQualityRuleBizModel:170-177`，异常 catch 隔离）；`wf.start(vars, ctx)` 同步启动；xwf 推进全同步（verify 步在 approve 请求线程内执行 source 脚本）；无定时/后台推进器、无 escalation timer（xwf 无 timeout 节点）；`reJudgeFailClosed` 同步 catch-all 转 reject（fail-closed，第 1 轮 #8 维持） | **线程语义无发现**；事务占用强信号立案 CL-206（移交维度 14）。`ensureTableRefExecutor` 惰性缝已属 C4-001 失效面 ⑤，不重复立案 |
| 6 | （面 1-5 衍生）外部 JDBC 超时/池化归属 | 见 C4-R2-001 / C4-R2-002 证据链 | **立案 2 条**（P2 ×1、P3 ×1）+ CL-205/CL-206 附记 |
| 7 | 第 1 轮遗留面回查：`MetaQualityCheckpointScheduler` scanner 启动注册 | `@PostConstruct` 路径单线程 bean 初始化期执行（IoC 容器串行 init），无并发窗口 | **无发现** |

**本轮终止**：5 个扩大面 + 2 个衍生面全部核查完毕；新发现 2 条（C4-R2-001 P2、C4-R2-002 P3）+ 附记 CL-205/CL-206；未发现 P0/P1（C4-R2-001 判级边界已显式标注供复核裁量）。无未尽检查项。

---

# C4 维度复核结论（CRA WI 5.5 独立复核）

- **复核人/日期**: C4 维度独立复核子代理，2026-10-22
- **复核基线**: live code commit 81a7c228e7（与初审/深挖同快照，工作区 nop-entropy-concurrency-audit）
- **复核方法**: 逐条重读引用源码 file:line；C4-R2-001 按任务书专项核实平台连接获取链兜底；执行模型盘点 9 项中抽 3 项「已防护」重演验证（3a/3b/#5）；10 源码根覆盖核验；CL-201~206 逐条裁定。只读复核，未修改其他维度文件/cra-index/产品代码。

## 逐条判定

| 条目 | 初审判级 | 复核判定 | 判定理由（file:line 证据） |
|---|---|---|---|
| C4-001 | P2 | **保留 P2**（同族合并归 Phase 6，主登记 = C1-102） | 证据成立：`NopMetaQualityCheckpointBizModel.java:154/157/160` 非 volatile 字段 + `:531-535` check-then-act 且赋值后二次 configure；运行标记仅按 checkpointId 分键（`:203`），不同 checkpoint 的 cron×手动交叉可并发命中 ensure*。P2 判级独立成立：后果方向 fail-closed 经独立核实（`CheckpointActionDispatcher.java:74` 默认 30s、`:111` `>0` 兜底、redirect 默认 false）。行号订正：`NopMetaQualityRuleBizModel.java:109`（原文 :118）、`QualityAlertWorkflowProcessor.java:56`（原文 :55）。同族关系：与 C1-001/C1-102 同根因，C4「5 处」为质量链子集，C1-102 家族 11 字段×8 载体更全（抽查 `NopMetaEntityBizModel.java:108/431-432`、`NopMetaDataSourceBizModel.java:155/871-872` 同型确认）——合并无损，以 C1-102 失效面为准。 |
| C4-002 | P3 | **保留 P3** | 证据成立：`MetaQualityCheckpointScheduler.java:215` 同步返回 DTO → `BeanMethodJobInvoker` 直接包 completedFuture（整条编排占调度池线程）；`app-local-scheduler.beans.xml:13-14` core=max=4 共享池、destroy 钩子在（`:7`）。后果仅触发延迟、不堆积（scheduleNext 完成后再排程），P3 判级恰当。 |
| C4-R2-001 | P2 | **保留 P2，不上调不下调** | 专项核实三点：①获取点收口——nop-metadata main 面外部 `DataSource.getConnection()` 仅 `MetaDataSourceConnectionProcessor.java:204`（withConnection）与 `:239`（testConnect）两处，同经 `buildDataSource`（仅透传 url/username/password/driverClassName），13 处消费点 grep 复核一致；TableReferenceExecutor ENTITY 路径走平台事务连接（`TableReferenceExecutor.java:79`），不在外部无界面内。②平台无兜底——`setNetworkTimeout(` 在 nop-persistence/nop-metadata/nop-core/nop-core-framework main 面 grep = 0 命中；`SimpleDataSource.setLoginTimeout` 空 no-op（`SimpleDataSource.java:81-83`）；`DynamicDataSource:92-93`/`ReadWriteRoutingDataSource:192-193` 仅转发；`DriverManager.setLoginTimeout(5)`（`MetaDataSourceConnectionProcessor.java:148-153`）按 JDBC 语义仅界建连登录阶段——**「无 networkTimeout/无线程中断路径」在获取链上成立，不降级**。③监视器内同步占池——`LocalJobScheduler.java:302-307` 定时回调与 `:215-223` fireNow 均在 `synchronized(job)` 内 executeJob，同步方法形态 whenComplete 同线程持锁完成；控制面 `removeJob:143`/`getJobState:155`/`suspendJob:184`/`awaitIdle:435` 同锁阻塞。判级：触发依赖外部源故障、后果为可重启恢复的可用性损失、建连子阶段已有 5s 界——初审判级边界说明成立，不上调 P1。 |
| C4-R2-002 | P3 | **保留 P3** | 证据成立：`MetaDataSourceConnectionProcessor.java:289-296` 每调用 new SimpleDataSource、withConnection finally 关闭（`:211-212`）、`MetaQualityCheckpointExecutor.java:129-186` 单线程 for 循环逐规则建连（per-rule try/catch + clearSession 隔离）。零复用乘数机制确认，P3（性能/隔离乘数、无正确性风险）恰当；与 C4-R2-001 根因不同、不合并不互替的裁定维持。 |

## 执行模型盘点表抽查（3/9 项重演）

| 抽查项 | 重演证据 | 结果 |
|---|---|---|
| 3a cron 自重叠串行 | `LocalJobScheduler.java:302-307` 定时回调 `synchronized(job)` 内 executeJob；`handleResult`→`scheduleNext` 仅在执行完成后排下次触发；`fireNow :215-223` 检查 `running != null` 即拒绝 | **防护成立，维持** |
| 3b checkpointRunLocks fail-fast | `NopMetaQualityCheckpointBizModel.java:203-206` putIfAbsent + ERR_CHECKPOINT_ALREADY_RUNNING、`:268` finally 释放；调度侧 `isConcurrentRunRejection` 降级 WARN（`MetaQualityCheckpointScheduler.java:232-236`） | **防护成立，维持** |
| #5 webhook 30s 超时 | `CheckpointActionDispatcher.java:249` `request.setTimeout(webhookTimeoutSeconds*1000L)`，默认 30s（`:74`）；dispatch 循环 per-action try/catch 隔离（`:196-202`）；无重试 | **防护成立，维持** |

抽查 3/3 通过，未发现「实际无防护」的错判，无升级项。

## 覆盖核验（nop-rule 5 源码根 + nop-metadata 5 源码根，全单元单批）

| 源码根 | 核查痕迹 | 粒度 |
|---|---|---|
| nop-rule-core | 清单 #7（RuleServiceImpl/RuleManager/RuleRuntime/RuleOutputAction）、CL-202 | 类级具名 |
| nop-rule-dao | 清单 #7（DaoRuleModelLoader） | 类级具名 |
| nop-rule-service | 清扫表 2b（NopRuleNodeBizModel @BizLoader 唯一命中） | 类级具名 |
| nop-rule-api / nop-rule-app | 报告 §0 单元级 main 面清扫（C4a grep 0 命中、C3 overlay 9 命中全 src/test、Timer/parallelStream/commonPool/ForkJoin 0 命中，口径「过 /main/ 且排 _gen」） | 清扫粒度 |
| nop-metadata-service | 主证据面（清单 #2-#9、C4-001/002、R2 两条、CL-203/205/206） | 类级具名 |
| nop-metadata-api / nop-metadata-app / nop-metadata-core / nop-metadata-dao | 同上单元级 main 面清扫（该 4 根为 DTO/引导类/常量/实体与 biz 接口，无 C4 执行模型面） | 清扫粒度 |

**结论：10/10 源码根有核查痕迹（4 根类级具名 + 6 根清扫粒度，后者无执行模型面，粒度已满足 C4 维度要求），无缺失根需登记，不触发补审。**

## CL-201~206 逐条裁定

| CL | 裁定 | 依据（复核重演） |
|---|---|---|
| CL-201 | **确认** | `LocalJobScheduler.java:302-307/:215-223` synchronized(job) 覆盖 executeJob 全程；控制面 `:143/:155/:184/:435` 同锁——锁扩展机制成立；处置维持 watch、移交 nop-job 批次 |
| CL-202 | **确认** | `RuleRuntime.java:68` 注释「taskRt可能会被多线程访问」与容器普通集合形态不符（①）；`RuleModel.java:28` executableRule 非 volatile + public getter `:45`，grep 全 main 面唯一外部调用点在守卫内（`RuleManager.java:55-65`，守卫结构事实②成立）；处置维持移交 C1 |
| CL-203 | **确认** | `MetaModelChangedEventPublisher.java:123-141` 纯 saveEntity 追加，全文件无清理/TTL/归档路径；处置维持移交 C7 |
| CL-204 | **确认** | `CrossDbConfigHolder.java:5` public static 非 final；main 面全部引用为读（`MetaJoinExecutor.java:74/404/436`、`CrossDbJoinMerger.java:29/33` 构造期拷贝）；处置维持移交 C1 |
| CL-205 | **确认** | `MetaDataSourceConnectionProcessor.java:142-144` 构造器 → `:148-153` `DriverManager.setLoginTimeout(5)` JVM 全局静态副作用成立；修复方向与 C4-R2-001 建议 (a) 收口同工；处置维持移交 C1 |
| CL-206 | **确认** | `v1.xwf` verify 步 c:script 同步调 `svc.reJudgeFailClosed`（qualityBreachApproval/v1.xwf:44-63）→ `QualityAlertWorkflowProcessor.java:139-152` fail-closed 包装内经 TableReferenceExecutor 建外部连接执行判定 SQL，全程占用 approve 请求 store 事务（verify 步在 approve 动作请求线程内同步推进）；处置维持移交维度 14 |

## 最终保留项

| # | 条目 | 判级 | 复核处置 | 现状 |
|---|---|---|---|---|
| 1 | C4-001 单例 BizModel ensure* 惰性初始化缝 | P2 | 保留；同族 C1-001/C1-102，合并归 Phase 6（主登记 = C1-102） | 未修复 |
| 2 | C4-002 checkpoint 全编排同步占共享平台 job 调度池 | P3 | 保留 | 未修复 |
| 3 | C4-R2-001 外部 JDBC socket 读无超时 + 监视器内同步占池 | P2 | 保留（平台无兜底经独立核实；不上调不下调） | 未修复 |
| 4 | C4-R2-002 外部连接零复用（C4-002 乘数放大器） | P3 | 保留 | 未修复 |

**最终保留计数：P0×0 / P1×0 / P2×2 / P3×2**；CL 线索 6 条全部确认（201→nop-job 批次、202/204/205→C1、203→C7、206→维度 14）。执行模型盘点 9 项判定全部维持。
