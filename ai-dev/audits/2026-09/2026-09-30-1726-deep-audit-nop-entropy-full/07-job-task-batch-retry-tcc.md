# G7: nop-job/task/batch/retry/tcc 深度审计（首轮）

- **审计日期**: 2026-09-30
- **审计人**: 首轮审计子代理（维度 04/07/09/14/16）
- **仓库基线**: live code（HEAD=dd4cbda344 工作区，G7 五模块无未提交改动；近期提交 dd4cbda344、bcac549f47 涉及 BatchTaskBuilder / DaoBatchRecordHistoryStore / TestHistoryTxnPromotion，已纳入审计）

## 审计范围

模块：`nop-job/`（15 子模块）、`nop-task/`（11 子模块）、`nop-batch/`（16 子模块）、`nop-retry/`（9 子模块）、`nop-tcc/`（10 子模块），约 641 个 main Java 文件 + 194 个 test 文件（均已排除 `_gen/`、`target/`）。

深读过的关键文件（非穷举）：

- **nop-batch-core**: `BatchTaskBuilder`、`BatchTask`、`BatchChunkProcessor`、`WithHistoryBatchConsumer`、`InvokerBatchConsumer`、`AddCompletedBatchConsumer`、`SingleModeBatchConsumer`、`RateLimitConsumer`、`AbstractRetryBatchConsumer`/`RetryAllBatchConsumer`、`PartitionDispatchQueue`、`AsyncFetchPartitionDispatchLoaderProvider`、`BatchChunkContextImpl`/`BatchTaskContextImpl`
- **nop-batch-dao/orm/dsl**: `DaoBatchStateStore`、`DaoBatchRecordHistoryStore`、`OrmQueryBatchLoaderProvider`、`ModelBasedBatchTaskBuilderFactory`、`model/nop-batch.orm.xml`
- **nop-job**: `JobPlannerScannerImpl`、`JobDispatcherScannerImpl`、`JobScheduleStoreImpl`、`LocalJobScheduler`、`model/nop-job.orm.xml`（UK/索引齐备）
- **nop-task-core**: `TaskRuntimeImpl`、`ParallelTaskStep`（抽样；该模块近期经 plan 263/349/364 大量硬化）
- **nop-retry-engine**: `RetryEngineImpl`、`RetryTaskImpl`、`RetryRecordStoreImpl`、`RetryScannerImpl`、`model/nop-retry.orm.xml`
- **nop-tcc**: `TccEngine`、`TccTransaction`、`TccRecordStore.fetchExpiredRecords`
- 各模块 service 层 BizModel（`NopBatchTaskBizModel`、`NopBatchTaskStateBizModel`、`NopRetryRecordBizModel`、`NopTaskInstanceBizModel` 等）及 `_service.beans.xml`/`_app.orm.xml` 生成物一致性抽查

### 各维度结论概览

| 维度 | 结论 |
|------|------|
| 04 ORM 模型与实体设计 | 1 条发现（[G7-04-01]）；job/retry/tcc/task 模型索引与唯一键齐备，为正面对照 |
| 07 BizModel 规范遵循 | 1 条发现（[G7-07-01]）；其余 BizModel 均符合 CrudBizModel 标准模式，NopTaskInstanceBizModel 显式禁用 copyForNew 属合规硬化 |
| 09 错误处理与错误码 | 2 条发现（[G7-09-01]/[G7-09-02]，均 P3）；主链路已广泛使用 ErrorCode + .param()；机械基线的裸异常其余命中为平台核心 `ExecutionContextImpl` 同款镜像模式（BatchTaskContextImpl）与测试代码，不报 |
| 14 异步与事务模式 | 4 条发现（[G7-14-01]~[G7-14-04]）；nop-job 调度（planner 乐观锁/dispatcher 租约/backoff）与 nop-tcc 补偿（confirm 守卫/终态聚合/补偿超时上限）经近期 plan 339/340 硬化，本轮未发现新问题 |
| 16 测试覆盖与质量 | 1 条发现（[G7-16-01]）；nop-task-core(42)/nop-job-coordinator(20)/nop-batch-core(17) 覆盖良好 |

机械基线核对结果：main 代码中 `System.out/printStackTrace` 仅 `RangeSplitUtils`（死代码）1 处，其余全部在 test 代码（按口径不报）；裸异常 main 命中为 `BatchTaskContextImpl`（镜像平台核心 `ExecutionContextImpl.java:86` 的既有模式，非模块违规）、`RangeSplitUtils`、`BatchGenState`（后两者已报）；`@Inject private` 全仓 0 处，属实。

## 发现

### [G7-04-01] NopBatchTask 的 taskKey 声明为业务唯一键，但 ORM 模型无 unique-key/索引，DaoBatchStateStore 用 check-then-act 防重，存在并发双实例竞态

- **文件**: `nop-batch/model/nop-batch.orm.xml:39-127`（实体全节，无 `<unique-keys>`/`<indexes>`）；`nop-batch/nop-batch-dao/src/main/java/io/nop/batch/dao/store/DaoBatchStateStore.java:128-145, 73-79, 105`
- **证据片段**:
  ```xml
  <!-- nop-batch.orm.xml:49-51 —— 注释声称唯一键语义 -->
  <column code="TASK_KEY" comment="业务上区分任务是否已存在的唯一键" displayName="唯一Key" mandatory="true" name="taskKey"
          precision="100" propId="3" stdDataType="string" stdSqlType="VARCHAR"
          i18n-en:displayName="Task Key"/>
  ```
  ```java
  // DaoBatchStateStore.java:128-142 —— 读后判，无锁、无条件更新
  protected NopBatchTask loadExistingTask(IEntityDao<NopBatchTask> dao, IBatchTaskContext context) {
      ...
      QueryBean query = new QueryBean();
      query.addFilter(FilterBeans.eq(PROP_NAME_taskName, taskName));
      query.addFilter(FilterBeans.eq(PROP_NAME_taskKey, taskKey));
      ...
  }
  // DaoBatchStateStore.java:73-79 —— 状态检查与置 RUNNING 非原子
  if (task.getTaskStatus() <= NopBatchDaoConstants.TASK_STATUS_RUNNING) {
      throw new NopException(ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_EXIST_RUNNING_INSTANCE)...
  ```
- **严重程度**: P1
- **现状**: 文档（`docs-for-ai/03-modules/nop-batch.md` "同 taskName+taskKey 只允许一个活跃实例"）与列注释均承诺唯一实例语义，但模型未定义 (taskName, taskKey) 唯一键（对照 nop-job 的 `UK_NOP_JOB_SCHEDULE_NS_GROUP_NAME`、nop-retry 的 `UK_RETRY_IDEMPOTENT_ID` 均有 DB 级约束），运行时仅靠 `loadTaskState0` 的 read-check-update 防重。提交 bcac549f47 曾将该问题裁定"暂缓（需唯一键 orm 模型变更+存量迁移设计）"，live code 仍未闭合。
- **风险**: 两个节点/两个触发源并发启动同名任务时双双通过状态检查、双双置 RUNNING（`updateEntityDirectly` 无版本条件），同一批数据被两个实例并发处理——断点续传的 `completedIndex`、记录级幂等历史会被交错写坏，at-least-once 退化为并发重复处理；同时 `loadExistingTask` 按 taskName+taskKey 查询无索引，任务表增长后全表扫描。
- **建议**: 在 `model/nop-batch.orm.xml` 为 NopBatchTask 增加 (taskName, taskKey) 唯一键（存量迁移按 execCount 降序保留最新一条），`loadTaskState0` 改为条件更新（`update ... set taskStatus=RUNNING where sid=? and taskStatus>=终态`，affected rows=0 视为冲突），参照 `JobScheduleStoreImpl.tryLockSchedulesForPlan` + `tryUpdateManyWithVersionCheck` 的既有模式。
- **信心水平**: 确定（竞态机制由代码结构直接可证；该问题已被上游提交自认暂缓，属已知未修）
- **误报排除**: 不是"约定大于配置"的宽松防重：文档与列注释明示唯一实例是产品语义；且同仓 nop-job/nop-retry 对同类语义均落了 DB 唯一键，证明平台惯例要求 DB 级约束。
- **复核状态**: 未复核

### [G7-14-01] nop-retry 幂等键生命周期错配：全局唯一键覆盖所有状态，查重却只查 PENDING/RETRYING——COMPLETED/SUSPENDED 记录永久占用幂等键，重提交触发裸 DB 唯一键冲突

- **文件**: `nop-retry/model/nop-retry.orm.xml:264-268`；`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/store/RetryRecordStoreImpl.java:40-43, 213-227`；`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:167-179, 437-445`
- **证据片段**:
  ```xml
  <!-- nop-retry.orm.xml:264-268 —— 唯一键不含状态列，覆盖所有记录 -->
  <index name="UK_RETRY_IDEMPOTENT_ID" unique="true">
      <column name="namespaceId"/>
      <column name="groupId"/>
      <column name="idempotentId"/>
  </index>
  ```
  ```java
  // RetryRecordStoreImpl.java:40-43 + 213-227 —— 查重仅限 PENDING/RETRYING
  private static final List<Integer> PENDING_STATUSES = Arrays.asList(
          NopRetryConstants.RETRY_RECORD_STATUS_PENDING,
          NopRetryConstants.RETRY_RECORD_STATUS_RETRYING);
  ...
  query.addFilter(FilterBeans.in(PROP_NAME_status, PENDING_STATUSES));
  return getRecordDao().findFirstByQuery(query);
  ```
  ```java
  // RetryEngineImpl.java:167-178 —— 查不到即插入，无唯一键冲突处理
  NopRetryRecord existingRecord = recordStore.findPendingRecordByIdempotentId(...);
  if (existingRecord != null) {
      return handleBlockStrategy(existingRecord, policy, request, cancelToken, task);
  }
  NopRetryRecord record = recordStore.newRecord(task, request);
  recordStore.saveRecord(record);
  ```
- **严重程度**: P1
- **现状**: `handleExecutionSuccess`（RetryEngineImpl.java:437-445）只把记录置 COMPLETED、**不删行**（对照 `moveToDeadLetter` 显式删行以释放幂等键，plan 342 裁定）。于是：(a) 业务用同一 idempotentId 再次提交（成功后的重放/对账重驱是最常见场景）→ 查重返回 null → INSERT 撞 `UK_RETRY_IDEMPOTENT_ID` → 调用方收到裸数据库约束冲突，blockStrategy（DISCARD/OVERWRITE）从未被咨询；(b) 记录被 `pause()` 置 SUSPENDED 后同样对查重不可见，再提交同样撞键；(c) 并发首次提交（TOCTOU）也绕过 blockStrategy 直接以约束冲突暴露。
- **风险**: 幂等重驱这个 retry 引擎的核心场景在"上一次已成功/已暂停"时必然报内部错误而非幂等返回；错误以 DB 异常形态暴露，无 ErrorCode、无参数上下文，运维无法区分"重复提交"与"真实故障"。
- **建议**: 三选一并对齐模型：① 查重去掉状态过滤，命中 COMPLETED 记录按 blockStrategy 返回既有结果或 success；② `handleExecutionSuccess` 完结后迁入历史表/删行（对齐死信路径 plan 342 的做法）；③ 唯一键加状态列并接受多行（需同步调整查重语义）。同时 `saveRecord` 捕获唯一键冲突转 `handleBlockStrategy`，兜住 TOCTOU 窗口。
- **信心水平**: 确定（机制三处代码互证：模型 UK、查重过滤、成功路径不删行）
- **误报排除**: 不是"idempotentId 一次性使用"的有意设计：blockStrategy 的存在本身表明"已存在记录时的提交"是设计内输入；死信路径特意删行释放键（注释明说"使同一 idempotentId 可以重新提交"）证明键可复用是既定契约，仅 COMPLETED/SUSPENDED 路径漏配。
- **复核状态**: 未复核

### [G7-14-02] historyStore 自动把 consume 事务提升为 process 级：chunk 事务现在覆盖整个 processor 执行，processor 调远程 I*Biz 的既有模式演变为长事务

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java:381-412`；`nop-batch/nop-batch-dsl/src/main/java/io/nop/batch/dsl/manager/ModelBasedBatchTaskBuilderFactory.java:184-185`
- **证据片段**:
  ```java
  // BatchTaskBuilder.java:384-394 —— consume scope 静默提升为 process
  BatchTransactionScope scope = batchTransactionScope;
  if (scope == BatchTransactionScope.consume && historyStore != null && transactionalInvoker != null) {
      LOG.info("nop.batch.history-txn-promoted:...");
      scope = BatchTransactionScope.process;
  }
  if (scope == BatchTransactionScope.consume && transactionalInvoker != null) { ... } // 被跳过
  ...
  // BatchTaskBuilder.java:410-412 —— process 级包装在 WithHistory 外侧，覆盖 processor
  if (scope == BatchTransactionScope.process && transactionalInvoker != null) {
      consumer = new InvokerBatchConsumer<>(transactionalInvoker, consumer);
  }
  ```
  ```java
  // ModelBasedBatchTaskBuilderFactory.java:184-185 —— DSL 恒定注入事务 invoker
  if (transactionTemplate != null)
      builder.transactionalInvoker(new TransactionalFunctionInvoker(transactionTemplate));
  ```
- **严重程度**: P2
- **现状**: dd4cbda344 为修复"业务已提交、history 未写"崩溃窗口，把配置了 historyStore 的 consume scope 整体提升为 process 事务。副作用：事务边界从"仅业务 consume"扩大到"processor（逐条处理）+ consume + saveProcessed"。而 `docs-for-ai/03-modules/nop-batch.md` 明确把 "processor 中调用 I*Biz / 远程服务" 列为推荐模式（"过账兜底扫描（processor 调用 I*Biz）"），文档只说明了提升事实，未提示长事务风险。
- **风险**: batchSize 条记录 × 每条一次远程调用的处理器，DB 事务持锁时间 = 整个 chunk 的远程交互时长（可达分钟级）；连接池占用、锁竞争、undo 膨胀，且事务内远程超时会放大为整 chunk 回滚重试。
- **建议**: 改为嵌套内层事务方案——保留 consume scope 语义，把「业务 consumer + WithHistory.saveProcessed」包进同一个**内层**事务（REQUIRES_NEW 于 consume 点），processor 留在事务外；至少在 `nop-batch.md`/`batch-dsl.md` 的 transactionScope 一节补"historyStore 提升后事务覆盖 processor，processor 含远程调用时应缩小 batchSize 或改用 process 显式确认"的告示。
- **信心水平**: 很可能（包装次序与提升逻辑确定；实际影响取决于用户 processor 是否含远程调用，而该模式是文档推荐用法）
- **误报排除**: 不是对 dd4cbda344 修复本身的否定——saveProcessed 必须与 consume 同事务的目标正确；问题在实现选择了"扩大到 process"而非"内层收窄"，引入了事务边界语义的静默变更。
- **复核状态**: 未复核

### [G7-14-03] AsyncFetch 分区加载的 fetch 线程不传播 IContext（租户/用户），与 chunk 处理线程的上下文传播行为不一致

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/AsyncFetchPartitionDispatchLoaderProvider.java:114-146`；对照 `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTask.java:210-241`
- **证据片段**:
  ```java
  // AsyncFetchPartitionDispatchLoaderProvider.java:114-131 —— 裸 executor.execute，无上下文包装
  for (int i = 0; i < fetchThreadCount; i++) {
      final int threadIndex = i;
      executor.execute(() -> {
          try {
              while (!context.isCancelled() && !queue.isFinished()) {
                  IBatchChunkContext ctx = context.newChunkContext();
                  ...
                  synchronized (fetchMutex) {
                      List<S> list = loader.load(loadBatchSize, ctx);  // 此线程无 IContext
  ```
  ```java
  // BatchTask.java:210-215 —— chunk 线程专门做了上下文传播（提交 50adea67ea）
  executor.execute(() -> {
      LOG.info("nop.batch.run-chunk-loop:...");
      ContextProvider.runWithContext(ctx -> {
          propagateContext(ctx, context);   // 传播租户/用户/locale
  ```
- **严重程度**: P2
- **现状**: `BatchTask.executeChunkLoop` 对每个并发 chunk 线程用 `ContextProvider.runWithContext + propagateContext` 传播租户/用户信息（提交 50adea67ea 的专项修复），但 dispatchConfig 配置 `fetchThreadCount > 0` 时，实际执行 `loader.load()` 的 fetch 线程直接跑在裸 executor 上，`ContextProvider.getOrCreateContext()` 会得到 tenantId=null 的空上下文。同步版 `PartitionDispatchLoaderProvider`（fetcher 在 chunk 线程内执行）不受影响，两条路径行为分叉。
- **风险**: 多租户部署下，orm-reader/jdbc-reader 的租户过滤依赖线程上下文：fetch 线程按空租户过滤，轻则查不到数据（任务空转"成功"），重则绕过租户隔离读到跨租户数据喂给消费侧；ORM 审计列（createdBy）也会落空值。仅 async-fetch 模式触发，问题隐蔽。
- **建议**: fetch 线程任务体包一层 `ContextProvider.runWithContext` + 复用 `BatchTask.propagateContext` 同款逻辑（将该方法提为可复用的静态工具），与 chunk 线程对齐。
- **信心水平**: 很可能（代码结构确定无传播；实际后果取决于使用方是否启用多租户与 async fetch 的组合）
- **误报排除**: 不是"框架自动传播"的误判：chunk 线程需要手工 propagateContext 正说明本仓线程池不自动继承上下文，fetch 线程是同性质的独立线程，无任何等价处理。
- **复核状态**: 未复核

### [G7-07-01] NopBatchTaskStateBizModel 是无 ORM 注册、无 xmeta、无 xbiz、无 bean 注册的伪 BizModel，挂在一条残留实体链上，下次 codegen 清理 _gen 时将编译断裂

- **文件**: `nop-batch/nop-batch-service/src/main/java/io/nop/batch/service/entity/NopBatchTaskStateBizModel.java:13-19`；`nop-batch/nop-batch-dao/src/main/java/io/nop/batch/dao/entity/NopBatchTaskState.java:14-17`
- **证据片段**:
  ```java
  // NopBatchTaskStateBizModel.java:13-19
  @BizModel("NopBatchTaskState")
  public class NopBatchTaskStateBizModel extends CrudBizModel<NopBatchTaskState>{
      public NopBatchTaskStateBizModel(){
          setEntityName(NopBatchTaskState.class.getName());
      }
  }
  ```
  ```
  // 交叉验证（grep 计数，口径：文件名精确匹配，排除 target/_gen）
  // nop-batch/model/nop-batch.orm.xml        → 0 命中（源模型无此实体）
  // _vfs/nop/batch/orm/_app.orm.xml           → 0 命中（运行时 ORM 未注册）
  // _vfs/nop/batch/model/（xmeta 目录）       → 无 NopBatchTaskState 目录
  // _vfs/nop/batch/biz/_service.beans.xml     → 无注册（仅 4 个 BizModel）
  // nop-batch-dao/.../entity/_gen/            → _NopBatchTaskState.java、NopBatchTaskStatePkBuilder.java 仍残留
  ```
- **严重程度**: P2
- **现状**: `NopBatchTaskState` 实体已从源模型移除（模型只剩 NopBatchTask/TaskVar/RecordResult/File 四实体，与 `docs-for-ai/03-modules/nop-batch.md` 一致），但手写保留层实体、`_gen` 生成物和该 BizModel 三件套残留。BizModel 未注册 bean，当前不产生运行时错误，纯死代码。
- **风险**: ① 违反 service-layer.md "每个 @BizModel 必须对应有 xmeta 的实体（聚合根），不允许伪 BizModel"——一旦有人把 bean 注册回去，GraphQL 即报"未定义的对象"；② `setEntityName` 指向运行时未注册实体，`CrudBizModel` 初始化路径会失败；③ `_gen/_NopBatchTaskState.java` 不在源模型中，下次清理式再生成（codegen 删除孤儿产物）时 `NopBatchTaskState.java` 与 BizModel 将编译失败，属埋雷。
- **建议**: 删除三件套：`NopBatchTaskStateBizModel.java`、`entity/NopBatchTaskState.java`、`entity/_gen/_NopBatchTaskState.java` + `NopBatchTaskStatePkBuilder.java`（后者属生成物，确认重新生成不会重建后一并删除）。
- **信心水平**: 确定
- **误报排除**: 不是"手写保留层可有业务扩展"的合法场景：该实体在源模型与运行时 ORM 中都不存在，`@BizModel` 指向的是平台已抛弃的聚合根；docs-for-ai 的核心实体表也不含它。
- **复核状态**: 未复核

### [G7-14-04] RetryEngine 回调目标复用原服务方法并以合成 payload 调用，模型缺回调服务字段，回调机制对常规服务必然失败

- **文件**: `nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:479-512`；`nop-retry/model/nop-retry.orm.xml:147-153`
- **证据片段**:
  ```java
  // RetryEngineImpl.java:485-501 —— 回调目标 = 原记录的服务/方法，data 为合成 Map
  String callbackService = record.getServiceName();
  String callbackMethod = record.getServiceMethod();
  ...
  IRetryTask callbackTask = newRetryTask(callbackService, callbackMethod)
          .withPolicyId(policy.getCallbackPolicyId())
          .withIdempotentId(record.getIdempotentId() + "_callback");
  ApiRequest<Object> callbackRequest = new ApiRequest<>();
  callbackRequest.setData(buildCallbackData(record, success, error)); // {recordId,idempotentId,success,retryCount,...}
  executeTask(callbackTask, callbackRequest, null)
  ```
  ```xml
  <!-- nop-retry.orm.xml:147-153 —— 策略只有 enabled/triggerType/policyId，无回调服务名/方法列 -->
  <column code="CALLBACK_ENABLED" ... />
  <column code="CALLBACK_TRIGGER_TYPE" ... />
  <column code="CALLBACK_POLICY_ID" ... />
  ```
- **严重程度**: P2
- **现状**: 策略模型没有任何"回调目标服务"字段，实现退而复用**原目标服务方法**，把 `{recordId, idempotentId, success, retryCount, errorCode, errorMessage}` 合成 Map 当作请求体再次调用。原方法期待的请求形状（原始业务请求体）与回调 payload 形状不同，除非服务作者特意实现双形状入口，回调调用将解析失败/执行错误，仅留一条 `nop.retry.callback-failed` error 日志。回调失败也无重试放大（idempotentId+"_callback" 走独立记录，尚可）。
- **风险**: "启用回调"这一配置项对绝大多数服务等于静默无效（失败只进日志），用户以为收到完成通知实际没有；合成的 Map 还会作为原始方法的入参参与反序列化，可能触发难排查的 4xx。
- **建议**: 策略模型补 `callbackServiceName`/`callbackServiceMethod`（或复用 dead-letter 通知类字段），`triggerCallback` 改为调用显式配置的回调目标；未配置时 fail-fast 记 warn 而非拿原方法顶替。
- **信心水平**: 很可能（复用原方法+合成 payload 是代码事实；"是否有服务真的实现了双形状入口"未知，若有文档化约定请复核时驳回）
- **误报排除**: 不是"通知原服务就是设计意图"的误判：若意图是通知原服务，payload 应复用 `record.getRequestPayload()` 或走独立通知接口；当前实现既丢了原请求又换了形状，两头不沾。
- **复核状态**: 未复核

### [G7-16-01] 断点续传核心 DaoBatchStateStore 零测试覆盖；nop-retry 幂等键重放场景（COMPLETED/SUSPENDED 后重提交）无测试——恰是两个 P1 的漏网原因

- **文件**: `nop-batch/nop-batch-dao/src/test/java/io/nop/batch/dao/history/TestDaoBatchRecordHistoryStore.java`（nop-batch-dao 全部测试）；`nop-retry/nop-retry-engine/src/test/java/io/nop/retry/engine/impl/TestRetryEngineImpl.java:504-505`
- **证据片段**:
  ```
  // 测试文件计数（find src/test -name '*.java'，排除 target）
  // nop-batch-dao:    1 个测试文件（仅 historyStore；DaoBatchStateStore 无任何直接测试）
  // 对照: nop-batch-core 17 / nop-job-dao 6 / nop-job-coordinator 20 / nop-task-core 42
  ```
  ```java
  // TestRetryEngineImpl.java:504-505 —— 只测了死信释放幂等键的循环
  @Test
  void testDeadLetter_shouldReuseIdempotentKeyAndAllowSecondCycle() throws Exception {
  // 无 "completed 记录占用幂等键后重提交"、"SUSPENDED 记录重提交"、"并发提交" 用例
  ```
- **严重程度**: P2
- **现状**: 批处理断点续传/防重复启动的状态存储（本轮 [G7-04-01] 的竞态所在类）没有一行直接测试；retry 引擎 16 个用例覆盖了即时/延迟重试、死信、分区，但幂等键的三个边界（成功后重放、暂停后重提交、并发提交）缺失——测试用 mock store，DB 唯一键行为天然测不到。
- **风险**: 两个 P1 正是从这些缺口漏过：DaoBatchStateStore 的并发语义无回归保护（修复后可能再回归），retry 幂等键生命周期改动（如修复 [G7-14-01]）没有红/绿基准。
- **建议**: ① 为 `DaoBatchStateStore.loadTaskState/saveTaskState` 补 NopAutoTest 集成测试（含同名任务重启、KILLED 拒启、状态机转移），并发用例可先以 mock 时序固化预期行为；② `TestRetryEngineImpl` 补 completed/suspended 记录重提交与并发提交用例（用真实 H2 + 唯一键，或把 store 的冲突处理抽为可 mock 的协作点）。
- **信心水平**: 确定（计数与用例清单可直接复现）
- **误报排除**: 不是"数量少即差"的机械判断：nop-batch-dao 恰恰承载断点续传这一最高风险职责而覆盖为 0，缺口与已确认缺陷一一对应。
- **复核状态**: 未复核

### [G7-09-01] BatchGenState 抛裸 IllegalStateException，消息是错误码形态字符串但未走 ErrorCode 体系

- **文件**: `nop-batch/nop-batch-gen/src/main/java/io/nop/batch/gen/generator/BatchGenState.java:64-76`
- **证据片段**:
  ```java
  // BatchGenState.java:64-66, 73-76
  public Object next(IBatchGenContext context, boolean produce) {
      if (!hasNext())
          throw new IllegalStateException("nop.err.batch.generator-already-finished");
      ...
          if (!subHasNext()) {
              throw new IllegalStateException("nop.err.batch.generator-already-finished");
          }
  ```
- **严重程度**: P3
- **现状**: 消息写成 `nop.err.batch.generator-already-finished` 的错误码形态，但用裸 `IllegalStateException` 抛出——既没有进 `BatchErrors` 的 ErrorCode 定义（该文件近期刚迁移过 4 处裸异常），也无法被 `.param()`/i18n/结构化错误响应机制识别。上层 `NopException.adapt` 虽能兜住转为 NopException，但错误码语义丢失。
- **风险**: 前端/调用方无法按错误码分支处理；与 error-handling.md 两档策略的任何一档都不符。
- **建议**: 在 `BatchErrors` 定义 `ERR_BATCH_GENERATOR_ALREADY_FINISHED`（ErrorCode.define + i18n），替换两处裸抛。
- **信心水平**: 确定
- **误报排除**: 与 `BatchTaskContextImpl` 的同款 ISE 不同：后者逐字镜像平台核心 `ExecutionContextImpl.java:86` 的既有平台模式（跟随基类，不属本模块违规）；BatchGenState 无此基类对照，属模块自有代码未迁移。
- **复核状态**: 未复核

### [G7-09-02] RangeSplitUtils 死代码：裸 IllegalArgumentException + 中文错误消息（违反"Error messages must be in English"），全仓无调用方

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/utils/RangeSplitUtils.java:61-66, 113, 122, 194`
- **证据片段**:
  ```java
  // RangeSplitUtils.java:122, 194
  throw new IllegalArgumentException("参数 bigInteger 不能为空.");
  ...
  throw new IllegalArgumentException(String.format("根据字符串进行切分时仅支持 ASCII 字符串，而字符串:[%s]非 ASCII 字符串.", aString));
  ```
  ```
  // 调用方验证：grep -rn "RangeSplitUtils" --include='*.java' 全仓（排除 target/_tmp/.m2-repo）
  // 命中仅 RangeSplitUtils.java 自身 → 0 个调用方
  ```
- **严重程度**: P3
- **现状**: DataX 移植的区间切分工具，全仓无调用方（提交 bcac549f47 曾裁定"不修复"，但文件仍留在 main 源码树）。6 处裸 `IllegalArgumentException` + 2 处中文消息，双重违反错误处理与消息语言规范。
- **风险**: 死代码本身无运行时风险，但留在 main 树会持续污染裸异常基线计数（本轮机械基线 batch 31 处中的 17 处来自它），且给 AI/新人"中文消息可用"的错误示范。
- **建议**: 直接删除该文件（及若有对应测试）；若判定保留，至少将消息英文化并迁移 ErrorCode。
- **信心水平**: 确定
- **误报排除**: 不是"工具类预留"场景：无调用方、无测试引用、未在任何 xdef/模板中以反射或字符串方式引用（grep 类名零命中）。
- **复核状态**: 未复核

## 零发现维度说明（核对过的正面结论）

- **nop-job 调度并发/分布式锁（维度14 抽样深读）**: planner 用 `nextFireTime` 覆写 + `tryUpdateManyWithVersionCheck` 作乐观锁（`JobScheduleStoreImpl.tryLockSchedulesForPlan`），fire 有 `UK_NOP_JOB_FIRE_SCHEDULE_TIME_SOURCE` 唯一键防重，dispatcher 对 no-fitting-worker 做 revert+backoff，schedule 计数器带 `Math.max` 下限防负值（plan 340 痕迹）。未发现新问题。
- **nop-tcc 补偿（维度14）**: `TccTransaction.endAsync` 的 confirm/cancel 双向守卫、`TccEngine.checkExpiredTransactions` 的单记录超时上限、`fetchExpiredRecords` 的 retryTimes 递增（循环有界）均正确；近期修复注释齐全。未发现新问题。
- **nop-task-core 执行流（维度14 抽样）**: `ParallelTaskStep` 挂起传播/未完成分支占位、`TaskRuntimeImpl` cancel 驱动 SUSPENDED→KILLED、父子 runtime 取消传播均已按 plan 349/364 硬化。本轮未投入全量深挖（42 个测试文件、近期多轮审计覆盖），列为盲区。
- **维度07 其余 BizModel**: batch/retry/task/job 的实体 BizModel 均为标准 `CrudBizModel<T> + setEntityName` 模式并 `implements I*Biz`，无违规。
- **机械基线**: `@Inject private` 0 处属实；`System.out` main 侧仅死代码 1 处；其余裸异常为平台核心镜像模式或测试代码，按口径不报。

## 盲区自评

- nop-task-core 仅抽样（ParallelTaskStep/TaskRuntimeImpl/关键 wrapper），Graph/Loop/Fork 全家与 nop-task-queue 未逐行；nop-batch-jdbc/文件 reader-writer、nop-batch-sys(SysEventBatchTrigger)、nop-job-worker/RPC 链路未深读。
- 未运行任何构建/测试（纯静态审计），事务传播（TransactionalFunctionInvoker 与 ORM session 的实际交互）按接口契约推断。
- nop-tcc 的 `TccRunner.confirmAllAsync/cancelAllAsync` 分支聚合细节未逐行复核。

## 子项复核结论

复核人：独立复核代理 R1（2026-09-30）

| 发现编号 | 判定 | 复核说明 |
|---|---|---|
| [G7-04-01] | 保留（维持 P1）| 打开 `nop-batch/model/nop-batch.orm.xml`：`grep unique-key\|<index` 全文件零命中（NopBatchTask 实体 39-127 行仅有 columns/relations），TASK_KEY 列注释「业务上区分任务是否已存在的唯一键」在 49-51 行与引文逐字一致；对照 `nop-job/model/nop-job.orm.xml` 实测 3 组 `UK_NOP_JOB_*`（207/321/450 行），平台惯例对照成立。`DaoBatchStateStore.java` 73-79 行状态检查、89-95 行置 RUNNING、105 行 `updateEntityDirectly`、128-145 行 `loadExistingTask` 读后判，均与引文一致。**关键深挖**：为排除「乐观锁兜底」的误报可能，追踪到底层——`OrmEntityDao.updateEntityDirectly` → `OrmSessionImpl.updateDirectly`（522-536 行）→ `flushUpdate` → `EntityPersisterImpl.update` → `GenSqlHelper.genUpdateSql`（290-325 行）：UPDATE 的 WHERE 子句仅由 `genEntityFilter` 生成（id 等值 + fixed filter + tenant filter），version 只出现在 SET 子句（`version = version + 1`），**无版本条件、无 affected-rows 乐观检查**——check-then-act 竞态确实无任何 DB 层兜底。`docs-for-ai/03-modules/nop-batch.md` 29/211 行「同 taskName+taskKey 只允许一个活跃实例」承诺属实。P1 成立。 |
| [G7-14-01] | 保留（维持 P1）| 三处机制互证全部实读核实：(1) `nop-retry/model/nop-retry.orm.xml` 中 `UK_RETRY_IDEMPOTENT_ID` 唯一索引列为 (namespaceId, groupId, idempotentId)，不含 status；(2) `RetryRecordStoreImpl.java` 40-43 行 `PENDING_STATUSES = [PENDING, RETRYING]` + 213-227 行 `findPendingRecordByIdempotentId` 带 `status IN (PENDING_STATUSES)` 过滤；`saveRecord`（259-261 行）为裸 `saveEntityDirectly` 无唯一键冲突处理；(3) `RetryEngineImpl.executeTask`（167-179 行）查不到即 `newRecord + saveRecord`；`handleExecutionSuccess`（437-445 行）仅置 COMPLETED + `updateRecord`，不删行；`pause`（131 行）置 SUSPENDED 同样不删行。对照路径 `moveToDeadLetter`（RetryRecordStoreImpl 268-293 行）显式 `deleteEntityDirectly(record)` 且注释明写「死信保存成功后删除原 record 行，使同一 idempotentId 可以重新提交（plan 342 裁定）」——幂等键终态可复用是平台自证契约，COMPLETED/SUSPENDED 路径漏配成立。重放/暂停后重提交必然撞 UK 的推演成立。P1（retry 引擎核心幂等契约断裂）成立。 |
| [G7-14-02] | 保留（维持 P2）| 打开 `BatchTaskBuilder.java` 375-415 行：historyStore 存在时 consume→process 静默提升（384-389 行，含 `nop.batch.history-txn-promoted` 日志）与包装次序逐行核实——process 级 `InvokerBatchConsumer`（409-412 行）在 `BatchProcessorConsumer`（含 processor）与 `WithHistoryBatchConsumer` **外侧**，事务确实覆盖整个 processor 执行。`ModelBasedBatchTaskBuilderFactory.java` 184-185 行确认 DSL 路径恒注入 `TransactionalFunctionInvoker`（transactionTemplate != null 时）。`nop-batch.md` 90 行「processor 中调用 I*Biz」推荐模式实读确认；文档 11/219 行只陈述了提升事实、无长事务告示。风险推演（batchSize 条 × 每条远程调用的持锁时长）成立，P2 合理。 |
| [G7-14-03] | 保留（维持 P2）| 打开 `AsyncFetchPartitionDispatchLoaderProvider.java` 108-148 行：fetch 线程为裸 `executor.execute(...)`，`loader.load(loadBatchSize, ctx)`（125 行）前无任何 `ContextProvider.runWithContext` 包装，属实。对照 `BatchTask.java` 209-241 行：chunk 线程用 `ContextProvider.runWithContext(ctx -> { propagateContext(ctx, context); ... })`，`propagateContext`（245 行起）传播 tenantId/userId/locale 等；`git log` 确认提交 50adea67ea「批处理任务并行执行时自动传播IContext中的租户和用户id信息」存在——同一任务两类线程行为分叉属实。P2（仅 async-fetch × 多租户组合触发，隐蔽）成立。 |
| [G7-07-01] | 保留（维持 P2）| 五项交叉验证全部复现：`NopBatchTaskStateBizModel.java` 实读为 `@BizModel("NopBatchTaskState") extends CrudBizModel<NopBatchTaskState>`；`grep -c NopBatchTaskState` 于 `model/nop-batch.orm.xml` = 0、于运行时 `_app.orm.xml` = 0；xmeta 目录实测仅 NopBatchFile/NopBatchRecordResult/NopBatchTask/NopBatchTaskVar 四个；beans.xml grep 零注册；残留三件套（手写 `entity/NopBatchTaskState.java`、`_gen/_NopBatchTaskState.java`、`_gen/NopBatchTaskStatePkBuilder.java`，另有 `_templates/_NopBatchTaskState.json`）find 列举确认。伪 BizModel + 孤儿实体 + codegen 再生成编译断裂的埋雷评估成立，P2 合理。 |
| [G7-14-04] | 保留（维持 P2，附证据补充）| 打开 `RetryEngineImpl.triggerCallback`（479-512 行）：`callbackService = record.getServiceName()`、`callbackMethod = record.getServiceMethod()` 复用原目标，`withIdempotentId(record.getIdempotentId() + "_callback")`，payload 为合成 Map（`buildCallbackData` 514 行起：recordId/idempotentId/success/retryCount/...），失败仅 `LOG.error("nop.retry.callback-failed", ...)`——代码事实全部属实。`nop-retry.orm.xml` 回调配置实测仅 CALLBACK_ENABLED/CALLBACK_TRIGGER_TYPE/CALLBACK_POLICY_ID 三列（~147-153 行），无回调目标服务字段，属实。**证据补充**：`docs-for-ai/03-modules/nop-retry.md` 35/79 行已明文记载「当前实现仍回调原任务 service/method，payload 含 recordId/idempotentId/...」——属已知局限的文档化陈述而非隐藏行为，缓解了「静默无效」的表述，但文档未约定原服务需实现双形状入口，payload 形状失配导致回调对常规服务失败的功能缺陷依然成立。维持 P2。 |
| [G7-16-01] | 保留（维持 P2）| 逐项复现：`find nop-batch-dao/src/test -name '*.java'` 实测仅 1 个文件（TestDaoBatchRecordHistoryStore）；全 batch 测试树 grep `DaoBatchStateStore` 仅命中 `nop-batch-core/TestBatchBugVerification.java` 的 defect5——实读确认其中是匿名内存 `IBatchStateStore` 桩（224-232 行 new IBatchStateStore 覆写空方法），真实 `DaoBatchStateStore` 无任何直接测试，报告断言成立。测试计数实测 batch-core 17 / job-coordinator 20 / task-core 42 / job-dao 6，与报告一致。`TestRetryEngineImpl` 实读：505 行 `testDeadLetter_shouldReuseIdempotentKeyAndAllowSecondCycle` 存在；27 个用例清单逐一核对，无 completed/suspended 记录重提交、无并发提交用例（现有 blockStrategy 用例的既有记录均为 PENDING 可查）。一处小误：「16 个用例」与实测 27 个 @Test 方法不符，不影响核心结论。P2 成立。 |
