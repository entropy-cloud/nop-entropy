# nop-batch C7 维度审计报告：长时运行稳定性（第 1 轮盲审初审）

- **审计单元**: nop-batch 聚合器（11 个含 Java 的 jar 子模块：api/app/biz/core/dao/dsl/exp/gen/jdbc/orm/service；218 files / 15,698 行手写 src/main，不含 `_` 前缀生成物与 target/）
- **仓库**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **维度定义 C7——长时运行稳定性**: 单调增长源（只增不删的账本/日志/累积结构）；挂死点（同步等待/锁内 I/O/不产出也不报错）；周期任务退化；时钟敏感性；重试风暴；状态机死区。
- **盲审纪律声明**: 本轮为盲审轮次。审计期间未读取 `ai-dev/audits/`（本输出目录除外）、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件。仅阅读了方法论文件 `ai-dev/skills/concurrency-resource-audit-prompt.md`（附录 A/F1–F11/附录 C）、owner doc `docs-for-ai/03-modules/nop-batch.md`、基线 `_tmp/cra-baseline-nop-batch.txt` C7/C4 节，以及 live code。文内对 WI 1.5/WI 1.6 的"同族对照"仅为任务下发文本中的口径提示，未读取对应文件，现状标注（新发现/已知）留待复核阶段。
- **基线**: `_tmp/cra-baseline-nop-batch.txt`（C7 supplement: scheduled/timer/metrics/ledger/history 节；C4: thread creation and pools / scheduled 节）。基线反虚构预注经实测确认：nop-batch 本体无 `Timer`/`ScheduledExecutorService` 自建周期任务；唯一定时点为 `DefaultBizEntityExporter` 对平台 `GlobalExecutors.globalTimer()` 的一次性 schedule（见 CL-018）；`GlobalExecutors` 均为平台设施调用（使用方式在范围内，实现不在）。

---

## 一、单调增长源清单（C7 执行步骤 1）

| # | 增长源 | 位置 | 增长条件 | 上界/清理 | 判定 |
|---|---|---|---|---|---|
| 1 | `nop_batch_task` 表 | `DaoBatchStateStore.tryInsertNewTask/newTask`（saveState=true 时每执行插一行） | saveState=true 且 taskKeyExpr 缺省（每执行 UUID 新行）；或 taskKeyExpr 每 Trigger 产出新值 | 无任何删除路径（全模块 grep `deleteByQuery|purge|TTL|retention` 零命中） | **无界**（C7-003） |
| 2 | `nop_batch_record_result` 表 | `DaoBatchRecordHistoryStore.saveProcessed`（每成功记录 insert 一行，`dao.saveEntity` 无 upsert） | 配置 historyStore 的任务每处理一条成功记录 | 无删除路径；无按 taskId/时间的 TTL | **无界**（C7-003） |
| 3 | `nop_batch_task_var` / `nop_batch_file` 表 | 实体存在（nop-batch-dao），引擎主链路无写者亦无删除者 | 经通用 CRUD 写入后 | 无自动清理；`NopBatchTaskBizModel` 为裸 `CrudBizModel`，无批量清理 action | 无界（附属于 C7-003 一并登记） |
| 4 | 每 chunk 双条 INFO 日志 | `BatchTask.processChunk` `:301/:351` | 每 chunk 2 条 INFO | 无抑制/采样 | 日志放大（C7-010） |
| 5 | 重试失败 ERROR 日志 | `RetryConsumeHelper:55`、`RetryBatchLoader:67`、`AbstractRetryBatchConsumer:42` | 每次重试失败 1 条 ERROR（含全栈） | 无抑制 | 日志放大（C7-010） |
| 6 | 任务级 AtomicLong 计数器 ×11 | `BatchTaskContextImpl:55-65` | 每记录递增 | long 上界；随任务上下文消亡 | 有界，不立案 |
| 7 | Micrometer 指标 | `BatchTaskMetricsImpl`（tag=job.name+task.name） | —— | **实测无装配者**：全仓 grep `new BatchTaskMetricsImpl|setMetrics` 在 nop-batch main 内零命中（对照 nop-task-core 有装配），DSL 路径 metrics 恒 null | 未接线，不立案（复核阶段确认是否为缺陷形态） |
| 8 | `ResourceRecordLoaderProvider.processingItems` TreeMap | `:335` | 每读入一行登记 | `maxProcessingItems=50000` + `waitUntilUnderLimit` 超限报错（`:311/:339`） | 有界且 fail-fast，不立案 |
| 9 | `SplitBatchConsumer.activeConsumers` ConcurrentHashMap | `:44/:68` | 每个新 tag 缓存一个 consumer | DSL 路径 tag 空间=DSL 声明的 forTag 集合（有界）；自定义 splitter+provider 理论无界 | DSL 路径有界；自定义路径理论风险 |
| 10 | ETL 状态文件 | `EtlTaskStateStore.saveStateFile` | 每 chunk 全量 JSON 重写（不追加） | 表数量级 | 有界，不立案 |
| 11 | 导出临时文件 | `DefaultBizEntityExporter.cleanup` | 每次导出 1 个 | 成功后 10 分钟由 globalTimer 删除；**进程重启则孤儿** | CL-018（C6 形态） |
| 12 | `BatchTaskContextImpl.getCache()` MapCache | `:104-113` | 任务期内任意键 | task 上下文生命周期，随任务消亡 | 有界，不立案 |

## 二、挂死点清单（C7 执行步骤 2，对照 F5）

| # | 挂死点 | 位置 | 超时/可检测性 | 判定 |
|---|---|---|---|---|
| 1 | chunk future 永不完成：`executor.execute` 被拒绝（自定义受限 executor 拒绝策略）或 chunk lambda 内抛 `Error`（`catch (Exception e)` 接不住） | `BatchTask.java:226/:243` | 无。allOf 永不结算 → `executeAsync` future 永不完成 → 任务行永久 RUNNING，无任何 batch 侧日志 | **C7-002** |
| 2 | 任务行永久 RUNNING 后每周期触发被闸门拒绝：不产出也不自愈，仅周期性异常 | `DaoBatchStateStore.java:123-129` + cron 触发链 | 无 stale-lease/fencing/看门狗 | **C7-001** |
| 3 | 负 maxRetryCount 下的无限重试循环（sleep≤5min + ERROR 日志） | `RetryPolicy.isExceedRetryCount`（`max<0 → false`）+ `RetryConsumeHelper.retryConsume` do-while(true) | 无总时限；每次循环有 ERROR 日志（可检测但无自愈） | **C7-004** |
| 4 | `BlockingSinkBatchConsumer.consume → sink.sendMulti` 无超时阻塞 | `BlockingSinkBatchConsumer.java:38` | 取消依赖 IBlockingSink 实现的 interrupt 响应（平台设施）；batch 侧无超时兜底 | 登记不立案（平台契约依赖，建议 owner doc 声明） |
| 5 | `BlockingSourceBatchLoader` 无限 poll 循环 | `BlockingSourceBatchLoader.java:60-87` | 每轮回检查 cancel（CANCEL_REASON_STOP 返回空→STOP）；设计为长驻消费源 | 设计意图，可取消，不立案 |
| 6 | `GraphQLFetchResultBatchLoader.load → FutureHelper.syncGet` 无超时等待 | `GraphQLFetchResultBatchLoader.java:29` | 依赖 GraphQL 引擎 future 必然结算的平台契约 | 登记不立案（同 4 类，平台契约依赖） |
| 7 | `BatchProcessorConsumer` async latch await | `BatchProcessorConsumer.java:91` | 有超时（asyncProcessTimeout / CFG_BATCH_ASYNC_PROCESS_TIMEOUT），超时抛 NopTimeoutException | 有界，不立案 |
| 8 | `RateLimitConsumer` tryAcquire | `RateLimitConsumer.java:17,31` | 有界（20 分钟），超时 fail-fast 抛错 | 有界，不立案 |
| 9 | `PartitionDispatchQueue.takeBatch` 条件等待 | `:204/:229` | `notEmpty.await(500ms)` 有界轮询 + fetch 线程结束后 noMoreData 退出 + 异常经 `exception` AtomicReference 传播（`:89-99`） | 有界且异常可见，不立案 |
| 10 | `ResourceRecordLoaderProvider.waitUntilUnderLimit` `state.wait(remaining)` | `:383` | deadline 有界（overLimitWaitMillis，默认 60s），超时抛 ERR_BATCH_TOO_MANY_PROCESSING_ITEMS | 有界，不立案 |
| 11 | 保存终态时 `saveTaskState(true,err)` 自身抛错 → 终态永不落库 | `BatchTask.onTaskComplete:204-213` | future 仍以持久化异常结算（调用方可见），但行状态停留 RUNNING | 并入 **C7-001**（触发面之二） |

---

## 三、发现条目

### [nop-batch-C7-001] 任务行永久 RUNNING：终态保存失败/进程崩溃后单活跃闸门退化为永久死区 + 周期触发错误风暴

- **文件**: `nop-batch/nop-batch-dao/src/main/java/io/nop/batch/dao/store/DaoBatchStateStore.java:106-150,187-195,233-255`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTask.java:175-220,328-348`
- **证据片段**:
  ```java
  // DaoBatchStateStore.startExistingTask —— 常规路径快失败检查
  if (task.getTaskStatus() <= NopBatchDaoConstants.TASK_STATUS_RUNNING) {
      throw new NopException(ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_EXIST_RUNNING_INSTANCE) ...
  }
  // 启动闸门：仅允许从 taskStatus > RUNNING 原子迁移
  protected boolean tryMarkTaskRunning(IOrmSession session, NopBatchTask task) {
      SQL sql = SQL.begin().update(...)
              .where().eq(PROP_NAME_sid, task.getSid())
              .and().gt(PROP_NAME_taskStatus, NopBatchDaoConstants.TASK_STATUS_RUNNING) ...
  }
  ```
- **严重程度**: P1
- **并发场景**: 同一 taskName+taskKey 的两次执行（如 cron 每 5s 触发的 nop-job 调度线程 vs 上一次执行遗留的 DB 行状态）；涉及 nop-job 调度线程、BatchTask chunk 线程、DaoBatchStateStore 单例。
- **触发时序**: 时序 A（崩溃窗口，不需并发）：T1 执行 saveState=true 任务 → `loadTaskState0` 置行状态 RUNNING(10) → chunk 循环处理中进程被 kill -9 / 断电 / OOM-Exit（终态 `saveTaskState(true,...)` 永未执行）→ 行状态永远停留 RUNNING → 此后每次 cron 触发（如每 5s）在 `:123` 被拒绝，每 5s 产生一条 `ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_EXIST_RUNNING_INSTANCE` 异常，无限重复。时序 B（saveState 失败链）：T1 chunk 失败 → `processChunk:341` `saveTaskState(false,e)` 因 DB 瞬断抛出持久化异常（业务异常被替换）→ `executeChunkLoop` fail-fast → `onTaskComplete:208` `saveTaskState(true,err)` 再次抛出（DB 仍不可用）→ future 以持久化异常结算，行状态停留 RUNNING → 同时序 A 后果。两条时序均无自动恢复路径。
- **现状**: 守卫本身原子性成立（(taskName,taskKey) 唯一键 + `tryMarkTaskRunning` 条件更新，plan 2282 G7-04-01），但守卫只认 `taskStatus > RUNNING` 可重启，且**没有任何 stale-RUNNING 判定机制**（无 lease 时间戳比较、无 workerId 存活探测、无 fencing、无启动时回收）。全模块唯一的状态迁移写者是本类；`NopBatchTaskBizModel` 为裸 CrudBizModel，无 kill/reset 业务动作——恢复只能靠人工改库。
- **风险**: 长时运行下进程重启/DB 瞬断是必然事件；一旦落入，该 taskName+taskKey 的任务**永久无法再次启动**，且周期触发器（nop-job cron）按触发频率制造错误日志风暴（每 5s 一条，天级 ≈ 17,280 条/天/任务），占满日志与告警带宽。运维若无该表知识，故障面表现为"任务莫名不再执行"。
- **建议**: (1) 启动闸门增加 stale 判定：RUNNING 行且 `restartTime + leaseTimeout < now`（或 workerId 心跳超时）时允许条件更新接管（复用平台 `IResourceLockManager`/租约语义或在 nop_batch_task 增加 lease_expire_time 列）；(2) `tryMarkTaskRunning` 的 WHERE 从 `taskStatus > RUNNING` 扩展为 `taskStatus > RUNNING OR (taskStatus = RUNNING AND leaseExpired)`；(3) 提供运维 action（force-kill）封装状态迁移。修复面在 nop-batch-dao 单点（唯一状态机写者），同族扩散面小。
- **失效面枚举（F3）**: ① `onTaskComplete` 成功路径 `saveTaskState(true,null)` 失败（`:196`）→ err=e 走失败路径再保存，仍失败则 RUNNING 残留——同族命中；② `onTaskComplete` 失败路径 `saveTaskState(true,err)` 失败（`:208`）——同族命中；③ `processChunk` catch 内 `saveTaskState(false,e)` 失败（`:342`）→ 异常替换后走 ②——同族命中；④ `executeChunkLoop` 提交后进程崩溃——同族命中（时序 A）；⑤ chunk 循环全部正常结束但 `fireBeforeComplete` 抛错（`:192`）→ err 分支同 ②——同族命中；⑥ `PartitionDispatchQueue` 分区残留——**不受影响**（队列纯内存、任务级生命周期，任务终止即消亡，无持久化分区状态）；⑦ file loader `processingItems`——**不受影响**（内存态）；⑧ ETL 状态文件（EtlTaskStateStore）——**不受影响**（complete 标志写入独立文件，且 `saveTableState` 失败会向上抛、不存在半态；原子 rename 见 CL-019）。同族缺陷集中于 DaoBatchStateStore 单一状态机 + BatchTask 终态回调，修复一处闭环。
- **信心水平**: 确定
- **误报排除**: 不是"低频路径粗粒度锁"类误报：这是状态机死区（C7 定义项）而非并发原语误用；守卫存在且正确，缺的是 stale 恢复语义（F5"不产出也不报错"+ 自愈语义缺失）。也不是已声明边界：owner doc 仅声明"同 taskName+taskKey 只允许一个活跃实例"，未声明"实例崩溃后行永久不可重启"。
- **复核状态**: 未复核
- **复核裁定（C7 独立复核 2026-10-16）**: **保留，维持 P1**（表述校准）。逐子项亲验：a) `tryMarkTaskRunning`（DaoBatchStateStore.java:187-195）WHERE 仅 `sid=? AND taskStatus>10`，无 lease/心跳/workerId 比较（workerId 列 :158/:289 有写者但从不参与判定）——无 stale 判定成立；b) 5s cron 风暴：17,280 次/天算术恒真，G3 快失败路径（:123-129）零 batch 日志、每次触发 1 session+1 SELECT（loadExistingTask :205-222）亲验成立——但示例须锚定为「saveState=true 任务配 5s 级 cron」：sys-event 链无 stateStore（C7-011），本身不产生也不遭遇 RUNNING 死区，且 sys-event-batch-consumer.job.yaml `enabled|false` 缺省关闭；c) 恢复入口：`IBatchTaskManager`/`BatchTaskManagerImpl` 仅构造/加载方法、5 个 BizModel 裸 `CrudBizModel`、`INopBatchTaskBiz` 空接口亲验成立——「人工改库外无恢复入口」按 C6 轮复核同口径校准为「无专用运维端点；通用 CRUD 写路径理论可达但不在契约内」（C6 轮对同形态 C6-001 已裁定该校准不改变 P1，沿用）；d) 全引擎 taskStatus 写者仅 DaoBatchStateStore（:152/:250/:280）亲验成立。触发时序 A（崩溃窗口）/B（saveState 失败链）每步可信（onTaskComplete :192-213 双次保存失败路径实测），崩溃/DB 瞬断为长时运行必然事件。现状：新发现·未修复（基线 b3a9c76bed 起 nop-batch/ 零代码变更，git diff 实测）。同族对照（不同模块，新发现不变）：WI 1.6 C6-002（RUNNING attempt 永久残留/砖化，open）、WI 1.6 C8-001（停摆族）。

### [nop-batch-C7-002] chunk future 永不完成：executor 拒绝与非 Exception Throwable 逃逸 chunk 循环 → 任务静默悬挂

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTask.java:222-263`
- **证据片段**:
  ```java
  CompletableFuture<Void> executeChunkLoop(IBatchTaskContext context, int threadIndex, ...) {
      CompletableFuture<Void> future = new CompletableFuture<>();
      executor.execute(() -> {          // ← :226 拒绝时 future 无人完成
          ...
          try { ... } while (true) ...
              future.complete(null);
          } catch (Exception e) {      // ← :243 Error（如 OOM）逃逸，future 无人完成
              ...
              future.completeExceptionally(e);
  ```
- **严重程度**: P2（条件化竞态/条件化故障——判级注明：默认 `GlobalExecutors.cachedThreadPool()` 下 `execute` 拒绝不可达，触发需自定义受限 executor 或 Error；但一旦触发即为"不产出也不报错"的永久挂死，符合 C7 挂死点定义，具体化程度高于纯理论）
- **并发场景**: 提交线程（executeAsync 调用者：nop-job 调度线程/GraphQL 请求线程）与 chunk 执行线程（DSL `executor` 配置的自定义线程池，或 cachedThreadPool）。
- **触发时序**: 路径 A：DSL 配置 `executor="myPool"`（`ModelBasedBatchTaskBuilderFactory:167-168/207-214` 支持，指向有界队列+Abort 策略的池）→ 池被其他任务占满 → T1 `executeAsync` 执行 `futures[i] = executeChunkLoop(...)`，`executor.execute` 抛 RejectedExecutionException → 异常直接冲出 `executeAsync`（allOf 尚未建立，`:148`）→ 已成功提交的 futures[0..i-1] 变幽灵线程继续加载并消费数据（stateStore 每 chunk 继续推进 completedIndex）→ 任务行停留 RUNNING、无终态、无 cancel 通知。路径 B：chunk lambda 内抛 `OutOfMemoryError`/`StackOverflowError` → `catch (Exception e)` 不接 → 线程死亡（仅 executor 级 uncaughtExceptionHandler 可见）→ `future` 永不结算 → `allOf(futures).whenComplete` 永不执行 → `executeAsync` 返回的 future 永不完成 → 调用方（nop-job invoker 线程）在 CompletionStage 上永久等待（跨单元放大：占用 job worker）。
- **现状**: 提交点无 RejectedExecutionException 捕获；循环体只 catch `Exception`；future 的 completed 保证在上述两个入口断裂。
- **风险**: 长时运行系统中 OOM Error 与线程池饱和都是可预期事件；路径 B 的挂死完全静默（batch 层零日志），且占用调度线程资源；路径 A 产生幽灵任务与 RUNNING 死区（与 C7-001 叠加）。
- **建议**: (1) `executeChunkLoop` 包裹 `executor.execute` 于 try-catch，拒绝时 `future.completeExceptionally(rej)`；(2) lambda 的 catch 改为 `catch (Throwable e)`（对 Error 也 completeExceptionally 后 rethrow）；(3) 复用平台 `FutureHelper`/`StandardThreadPoolExecutor` 的拒绝策略声明，DSL 层校验 executor 池参数并在文档声明并发×任务数上限。
- **失效面枚举（F3）**: 全模块 `executor.execute|.submit` 共 4 处——① `BatchTask.java:226`（本条主体）；② `AsyncFetchPartitionDispatchLoaderProvider.java:116`（fetch 线程提交，在 `setup()` 内：拒绝异常冲出 setup → executeAsync 的 try 捕获 → onTaskComplete 失败终态，**失败可见，不命中**；但其内层 `catch (Exception e)`（`:133`）同样接不住 Error——Error 时 fetch 线程死亡、`exception` ref 未设置、`exitFetchThread()` 在 finally 中执行（`:140-142`）→ noMoreData → 消费方拿到空列表任务"成功"结束 0 条记录，**同族变体命中**）；③④ `ExportDbTool.java:176`/`ImportDbTool.java:252`（CLI submit + `FutureHelper.waitAll`，拒绝异常从 execute() 抛出且 finally destroy，可见，不命中）。
- **信心水平**: 确定（路径 B）/ 很可能（路径 A 依赖用户池配置）
- **误报排除**: 不是"原语使用不是问题"类——这里是复合保证（future completed 保证）在异常/拒绝路径断裂（F8 清理不对称的 future 版本）；也不是测试代码：BatchTask 是全平台任务执行主路径。
- **复核状态**: 未复核
- **复核裁定（C7 独立复核 2026-10-16）**: **保留，维持 P2**。`executor.execute`（BatchTask.java:226）拒绝时 future 无人完成、`catch (Exception e)`（:243）接不住 Error——两个断裂点亲验成立；allOf 建立于 :148（提交循环之后），拒绝异常冲出 executeAsync 时幽灵 futures[0..i-1] 已在跑——路径 A 机制成立（依赖自定义受限 executor，缺省 cachedThreadPool 不可达，判级注记恰当）；路径 B（Error 逃逸）完全具体。F3 子项②亲验：AsyncFetchPartitionDispatchLoaderProvider fetch 提交拒绝异常冲出 setup→onTaskComplete 失败终态（可见，不命中），内层 `catch (Exception)`（:133）接不住 Error→`exitFetchThread()` 在 finally（:140-142）→noMoreData→空任务"成功"——同族变体成立。现状：新发现·未修复。同族对照：WI 1.6 C8-001 停摆族（静默不产出）。

### [nop-batch-C7-003] 批处理三表零保留：nop_batch_task / nop_batch_record_result / nop_batch_task_var 无任何清理路径，长时运行单调增长

- **文件**: `nop-batch/nop-batch-dao/src/main/java/io/nop/batch/dao/store/DaoBatchStateStore.java:83-104,276-291`；`nop-batch/nop-batch-dao/src/main/java/io/nop/batch/dao/history/DaoBatchRecordHistoryStore.java:77-111`
- **证据片段**:
  ```java
  // DaoBatchRecordHistoryStore.saveProcessed —— 每条成功记录新插一行，无 upsert、无 TTL
  for (S record : filtered) {
      NopBatchRecordResult result = dao.newEntity();
      result.setBatchTaskId(taskId);
      result.setRecordKey(getRecordKey(record, keyFn, scope));
      result.setResultStatus(0);
      ...
      dao.saveEntity(result);
  }
  ```
- **严重程度**: P1（长时运行必然触发的无界增长；按判级口径 P1）
- **并发场景**: 不涉及线程交错；增长主体是周期触发（nop-job cron）× 记录量。
- **触发时序**: 增长率（非交错时序，按"每 X 单位增长 Y"口径）：saveState=true 且 taskKeyExpr 缺省的任务（`BatchTask.initTaskKey:170-171` 空则 UUID）每次触发插入 1 行 nop_batch_task、1 组 nop_batch_task_var；每成功处理 1 条记录插入 1 行 nop_batch_record_result。以周期任务为例：每日 1,000 次触发 × 每次处理 10,000 条 = 每日 +1,000 行 task +10,000,000 行 record_result，年化 ≈ 36.5 亿行，无任何删除/归档路径。全 11 模块 grep `deleteByQuery|deleteEntity|delEntity|purge|TTL|retention|cleanup` 对这三表零命中；service 层唯一任务 BizModel 为裸 `CrudBizModel`（仅通用单条 CRUD，无批量清理 action）。
- **现状**: 引擎只写不删。`saveProcessed` 明确仅成功写（失败不写，注释 `:79`），行按 (batchTaskId, recordKey) 逻辑去重但物理逐次插入。historyStore 的幂等查询 `filterProcessed`（`DaoBatchRecordHistoryStore:40-46`）按 `batchTaskId + recordKey IN(...) + resultStatus=0` 查询——表膨胀后每 chunk 一次的 IN 查询随之退化（索引存在与否取决于 `nop-batch.orm.xml`，批处理本身无分区/归档配合）。
- **风险**: 天/周级运行后 record_result 成为最大表之一：存储成本单调上升、幂等过滤查询变慢（长任务每 chunk 都查）、且 UUID-key 任务历史行永无复用价值（completedIndex 恢复依赖同 taskKey 行，UUID 行写完即死数据）。
- **建议**: (1) 为 nop_batch_record_result 增加 retention 配置（按 completedTime TTL 批删，复用平台定时任务设施或提供运维 action）；(2) 对终态任务行（COMPLETED/FAILED/KILLED 且超期）提供清理 job；(3) `saveProcessed` 评估按 (batchTaskId,recordKey) 唯一键 upsert（该组合语义上应为唯一）；(4) owner doc 补充"history 表保留策略由使用方负责"的显式声明（若定位为声明边界）。
- **失效面枚举（F3）**: 同族账本全清单——① `nop_batch_record_result`（DaoBatchRecordHistoryStore.saveProcessed，本条主体）；② `nop_batch_task`（tryInsertNewTask/newTask，本条主体）；③ `nop_batch_task_var`/`nop_batch_file`（引擎内无写者无删除者，登记状态）；④ `JdbcKeyDuplicateFilter`（nop-batch-jdbc）——**不命中**：以目标业务表自身为去重账本，保留随业务表，无独立增长源；⑤ ETL 状态文件（EtlTaskStateStore）——**不命中**：覆盖写，大小=表数量级；⑥ 导出临时文件——**不命中**本条（10 分钟定时删除，孤儿场景另立 CL-018）。
- **信心水平**: 确定
- **误报排除**: 不是"有界声明存在的缓存"误报——三表无任何上限声明与清理声明（owner doc 未声明保留策略，grep 无 TTL 实现）；append-only 持久化账本正是 F4 定向检查形态。
- **复核状态**: 未复核
- **复核裁定（C7 独立复核 2026-10-16）**: **保留，维持 P1**。清理面独立 grep（`deleteBy|removeBy|DELETE FROM|cleanup|purge|expire|archive|ttl|retention|deleteEntity|delEntity|deleteDirectly|removeEntity`，Java/XML/YAML/SQL 全资产面）亲验：唯一命中 `DefaultBizEntityExporter.cleanup`（文件面）与 `non-broadcast-consumer.batch.xml:26` 的 `leaseExpireTime`（查询语义）——第 2 轮「DB 表清理代码完全不存在（连死代码都没有）」二态判定成立；beans.xml 装配面（app-batch-dao/batch-dsl/app-service）无任何清理/TTL/归档 bean 亲验成立。UUID 增长路径亲验（BatchTask.initTaskKey:170-171 空则 UUID + saveState=true 每执行 1 行 task）；`nop_batch_record_result` 复合主键（nop-batch.orm.xml:195-199 双 `primary="true"`）修正采信——第 1 轮「物理逐次插入静默累积」表述按第 2 轮修正为「重复 (taskId,recordKey) 必然撞主键响亮失败」，增长无界结论不变。触发尺度具体（周期任务×记录量，天/周级可见膨胀）→ P1 维持。现状：新发现·未修复。同族对照（不同模块，新发现不变）：WI 1.6 C7-001（retry 三表零保留，P1，open）、WI 1.5 MA-05（扫描无界族）。

### [nop-batch-C7-004] maxRetryCount 负值语义为"无限重试"且 DSL 无校验；附重试次数 off-by-one（实际尝试 = 声明 + 2 次）

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/retry/RetryPolicy.java:141-146`（平台设施，语义判定点）；`nop-batch/nop-batch-dsl/src/main/java/io/nop/batch/dsl/model/BatchRetryPolicyModel.java:20-37`（装配点，无校验）；`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef:41`（`maxRetryCount="int"` 无约束）；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/AbstractRetryBatchConsumer.java:58` + `RetryConsumeHelper.java:35-79`（尝试次数累计点）
- **证据片段**:
  ```java
  // RetryPolicy —— max<0 永不超限
  public boolean isExceedRetryCount(int retryTimes) {
      int max = this.getMaxRetryCount();
      if (max < 0) return false;          // 负值 → 无限重试
      return retryTimes > max;
  }
  // BatchRetryPolicyModel.buildRetryPolicy —— DSL 值直通，无非负校验
  if (getMaxRetryCount() != null) { policy.setMaxRetryCount(getMaxRetryCount()); }
  ```
- **严重程度**: P2（需显式配置负值——罕见但触发链完全具体；同族 off-by-one 无条件但影响轻，附注在本条）
- **并发场景**: 单 chunk 线程内的重试循环；但多 chunk 并发时每个线程独立进入无限循环。
- **触发时序**: 用户在 DSL 写 `<retryPolicy maxRetryCount="-1"/>`（在众多框架中 -1 惯用语义是"禁用重试"，此处恰为反面：无限重试）→ chunk 消费失败 → `RetryConsumeHelper.retryConsume` do-while(true)：每次失败 `checkRetry` 得 `delay≥0`（`isExceedRetryCount` 恒 false）→ `ThreadHelper.sleep(≤maxRetryDelay=5min)` → 再消费 → 永不退出。任务永不终态（叠加 C7-001 的 RUNNING 死区面），每轮一条 ERROR 全栈日志（`RetryConsumeHelper:55`），下游故障被同频重试放大（重试风暴）。**附 off-by-one 实测**：`maxRetryCount=2`（DSL 缺省）时实际消费尝试次数 = 初始 1 次（AbstractRetryBatchConsumer.consume:38）+ `checkRetry(e,0)`（:58，只 sleep 不消费却已消耗一次计数并 sleep 1s）+ retryConsume 内 3 次（retryCount 1→3）= **4 次尝试、3 次 sleep**，与 RetryPolicy 注释"重试两次总共 3 次"不符。
- **现状**: `RetryPolicy.setRetryDelay/setMaxRetryDelay` 均有 `Guard.nonNegativeInt`，唯独 `setMaxRetryCount` 无守卫；xdef schema 无 min 约束；`BatchRetryPolicyModel` 直通。
- **风险**: 毒记录（永久性失败如数据格式错误）+ 负配置 → chunk 线程永久自旋，任务永不结束；对下游是固定频率（≤1/5min/线程）的无限重试风暴；日志按 retry 轮数线性放大。
- **建议**: (1) `BatchRetryPolicyModel.buildRetryPolicy` 对 maxRetryCount<0 显式报错（或声明负值=无限并写入 owner doc——二选一，必须显式）；(2) `RetryPolicy.setMaxRetryCount` 补 Guard 与 setRetryDelay 对齐；(3) 修正 AbstractRetryBatchConsumer 与 RetryConsumeHelper 的双处计数叠加，使总尝试次数与声明一致。
- **失效面枚举（F3）**: retryPolicy 语义消费点全清单——① `RetryConsumeHelper.checkRetry/retryConsume`（消费重试，本条主体）；② `RetryBatchLoader.retryLoad`（加载重试，同语义同暴露，`RetryBatchLoader.java:48-70` do-while(true) + `delay<0` 判退出——同族命中）；③ `AbstractRetryBatchConsumer.checkRetry:58`（入口预检查，同族命中）；④ loadRetryPolicy（`BatchTaskBuilder.buildLoader0/1:363-371` 装配）——与 ② 同点；⑤ 平台其他 RetryPolicy 使用方（nop-commons 设施，按纪律不重审设施本身，仅登记 batch 侧两个使用面 ①②）。
- **信心水平**: 确定（语义实测：`getRetryDelay` 唯一退出通道是 `isExceedRetryCount` 与 exceptionFilter）
- **误报排除**: 不是"原语使用不是问题"——这是 DSL 契约与语义的偏差（配置陷阱 + 声明 off-by-one），属 C7 重试风暴定向检查项；负值路径有完整可执行触发链，非"看到共享字段就报"。
- **复核状态**: 未复核
- **复核裁定（C7 独立复核 2026-10-16）**: **保留，维持 P2**。`isExceedRetryCount` max<0 恒 false（RetryPolicy.java:141-146）、`setMaxRetryCount` 无 Guard（:78-80，对照 setRetryDelay/setMaxRetryDelay :92/:106 均有 nonNegativeInt）、batch.xdef `maxRetryCount="int"` 无约束（:40）逐点亲验成立。off-by-one 实测复核：消费链初始 1 + checkRetry(e,0)（AbstractRetryBatchConsumer:58，只 sleep 计数不消费）+ retryConsume 3 次（retryCount 1→3，M=2 时 :3 触发 delay=-1 抛出）= 4 次 3 sleep；加载链（RetryBatchLoader:40-70）= 3 次 2 sleep——双链计数不一致（M+2 vs M+1）成立，与第 2 轮 3.2 节表格一致。负值语义反转（常见惯例 -1=禁用重试，此处=无限）为具体配置陷阱 → P2 维持。现状：新发现·未修复。

### [nop-batch-C7-005] skipPolicy 缺省 maxSkipCount=0 语义为无限跳过；被跳过记录不入任何账本，周期任务对毒记录永久循环丢弃（超出 at-least-once）

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchSkipPolicy.java:42-51`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/SkipConsumeHelper.java:26-47`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java:436-439`
- **证据片段**:
  ```java
  // BatchSkipPolicy.shouldSkip —— 0/负值 = 不设上限
  public boolean shouldSkip(Throwable exception, long skipCount, IBatchChunkContext context) {
      if (maxSkipCount > 0 && skipCount >= maxSkipCount) {
          return false;
      }
      ...
      return !(exception instanceof Error);
  }
  ```
- **严重程度**: P2（需配置 skipPolicy 且不设 maxSkipCount——条件化但后果是静默数据丢失，且周期任务下具体化为永久循环）
- **并发场景**: 单 chunk 线程即可触发；多 chunk 并发时 `getSkipItemCount()` AtomicLong 计数共享，无竞态但计数与 maxSkipCount 比较点（`:34` 读-后-用）非原子，极端并发下可少量越过 maxSkipCount（轻微超跳，附注）。
- **触发时序**: 任务配置 `<skipPolicy/>`（或 exceptionFilter 宽松）而不设 maxSkipCount → 默认 0 → 永不饱和。毒记录 R 使 consume 每次失败：首轮 R 被跳过（`SkipConsumeHelper:34-44` 仅 LOG.info + `incSkipItemCount`）→ R 既不进 completedItems（AddCompletedBatchConsumer 只对成功 consume 的 items 追加）、也不进 historyStore（saveProcessed 只收成功 filtered 集）→ 若任务为周期触发（同一批输入每轮重读，如 orm-reader 待处理扫描）：**每轮触发 R 都失败一次、跳过一次、下轮再来**——永久循环，每次一条 INFO 日志 + 一次 skipError 指标；R 对应数据永久丢失且无任何持久化丢弃记录（超出 at-least-once 的"已处理行丢失"面，按本轮口径可立案）。saveState=false 的扫描型任务连 skipItemCount 累计都不持久化。
- **现状**: `maxSkipCount<=0` 被实现为"不限"，xdef `skipPolicy maxSkipCount="long"` 无 required/min 约束（batch.xdef:48），owner doc 配置表仅写"跳过策略（maxSkipCount/exceptionFilter）"未声明缺省语义。
- **风险**: 数据静默丢失无死信记录；周期任务退化为对毒记录的永久重复扫描-失败-跳过循环（每轮固定成本 + 日志/指标噪声）；运维无法从任何持久化面得知哪些记录被丢弃。
- **建议**: (1) `BatchSkipPolicy` 对 maxSkipCount<=0 显式报错或要求显式 `maxSkipCount=-1` 表示无限；(2) 跳过动作写死信记录（复用 historyStore 的 resultStatus 失败语义或新增 skip 账本），使丢弃可审计；(3) owner doc 补缺省语义声明。
- **失效面枚举（F3）**: skip 路径全清单——① `SkipBatchConsumer`（retryOneByOne=false 时装配，`BatchTaskBuilder:437-439`）→ `SkipConsumeHelper.consumeWithSkipPolicy`，本条主体；② `RetryOneByOneBatchConsumer:29-31`（retryOneByOne=true 时 skipPolicy 内嵌于逐条路径，同一 helper）——同族命中；③ maxSkipCount 饱和后的行为（>0 正常配置）——饱和即抛异常 → chunk 失败 → 任务 FAILED，行为正确，不命中；④ `shouldSkip` 默认放行非 Error 异常（`return !(exception instanceof Error)`）——无 filter 时 Error 之外的任意异常均可跳，与 ①② 叠加放大本条暴露面。
- **信心水平**: 确定（语义实测）
- **误报排除**: 不是"已声明边界"——owner doc 未声明 maxSkipCount 缺省=无限；且"跳过超出 at-least-once"是本轮明确可立案面。
- **复核状态**: 未复核
- **复核裁定（C7 独立复核 2026-10-16）**: **保留，维持 P2**。`shouldSkip` 的 `maxSkipCount > 0` 前置条件（BatchSkipPolicy:41-53）→ 缺省 0 = 饱和检查永不生效亲验成立；被跳过记录不入 completedItems（AddCompletedBatchConsumer 只收成功 consume 集）也不入 historyStore（saveProcessed 仅收成功 filtered 集）亲验成立；batch.xdef `maxSkipCount="long"` 无 required/min 约束亲验成立；周期任务对毒记录的永久重复扫描-失败-跳过循环具体。附注复核：多 chunk 下 `getSkipItemCount` 读-后-用的轻微超跳与主判定无关。P2 维持。现状：新发现·未修复。

### [nop-batch-C7-006] completedIndex 断点推进仅存在于 file/excel loader：orm/jdbc/gen 任务的 saveState=true 断点续传实际空转，重启全量重跑

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/ResourceRecordLoaderProvider.java:232-240,300-302`（唯一推进点）；`nop-batch/nop-batch-dsl/src/main/java/io/nop/batch/dsl/manager/ModelBasedBatchTaskBuilderFactory.java:431-449`（saveState 仅接给 file/excel）；`nop-batch/nop-batch-orm/src/main/java/io/nop/batch/orm/loader/OrmQueryBatchLoaderProvider.java:108-129`（cursor 不读 completedIndex）；`nop-batch/nop-batch-jdbc/src/main/java/io/nop/batch/jdbc/loader/JdbcPageBatchLoaderProvider.java:74-98`（同）
- **证据片段**:
  ```java
  // ModelBasedBatchTaskBuilderFactory.buildLoader0 —— 仅 file/excel 接收 saveState
  if (loaderModel.getFileReader() != null) {
      return newFileReader(loaderModel.getFileReader(), beanProvider, saveState, aggregator);
  } else if (loaderModel.getExcelReader() != null) { ...
  } else if (loaderModel.getJdbcReader() != null) {
      return newJdbcReader(loaderModel.getJdbcReader(), beanProvider, jdbcTemplate, sqlLibManager); // 无 saveState
  } else if (loaderModel.getOrmReader() != null) {
      return newOrmReader(loaderModel.getOrmReader(), daoProvider);                                 // 无 saveState
  ```
- **严重程度**: P2（语义缺口 + 长任务重启成本退化；无正确性损失——重复处理落在已声明的 at-least-once + historyStore 幂等契约内，但"断点续传"对多数 loader 是空承诺）
- **并发场景**: 不涉及竞态；场景是"长任务第 N 小时崩溃 → 重启"的单线程恢复路径。
- **触发时序**: orm-reader + saveState=true 长任务运行 10 小时崩溃（此时 nop_batch_task.completedIndex 恒为 -1——全引擎 grep 确认 `setCompletedIndex` 推进者仅 ResourceRecordLoaderProvider:301 与 EtlTaskStateStore:115，orm/jdbc/gen 路径无任何写者）→ 重启后 `startExistingTask:173` 恢复 completedIndex=-1、各计数器 → orm loader 从头建 cursor 全量重读：若配 historyStore，靠 `filterProcessed` 逐 chunk 过滤（全表重读 + 每 chunk IN 查询，读放大 = 全量）；若未配 historyStore，10 小时工作量的副作用全部重做。saveTaskState 每 chunk 持久化的 completedIndex（`DaoBatchStateStore:238`）在该场景持久化的是常量，无信息量。
- **现状**: owner doc 将"断点续传：通过 completedIndex 记录进度，中断后可恢复"列为引擎级特性，未声明其仅覆盖文件读取器。
- **风险**: 运维以为配置 saveState 即有断点；大表任务（本模块声明的大数据量场景）重启成本全额重付；completedIndex 字段的观测值（恒 -1）具有误导性。
- **建议**: (1) orm-reader/jdbc-reader 增加 cursor 恢复（orm-reader 可序列化 keyset 锚点实体主键到 task var；jdbc 分页可持久化 offset/range）；(2) 或在 DSL 构建期对"orm/jdbc/gen loader + saveState=true + 无 historyStore"组合报错/告警，声明该组合无续传能力（最小改动）；(3) owner doc 明确 completedIndex 的适用范围。
- **失效面枚举（F3）**: 断点消费/推进面全清单——① ResourceRecordLoaderProvider（file/excel，唯一有效推进者，**正常**）；② OrmQueryBatchLoaderProvider——命中（无恢复）；③ JdbcPageBatchLoaderProvider——命中（range 每次执行从 null 重建）；④ JdbcBatchLoaderProvider——命中（流式 cursor 无任何恢复且不支持 saveState）；⑤ BatchGenLoaderProvider——命中（genState 从头生成；gen 语义下重放或许幂等，登记）；⑥ EtlTaskStateStore（exp 自有 state store，经 completedIndex 恢复——**正常**，`:94-96`）；⑦ sys-event non-broadcast-consumer DSL——saveState 未配置，不在本条范围（见 C7-011）。
- **信心水平**: 确定（推进点全 grep 实证）
- **误报排除**: 不是"已声明边界"——owner doc 反向声明了该特性；不是 C5 内存问题——本条是自愈语义（C7 步骤 4）与周期任务退化（步骤 3 的重启特例）。
- **复核状态**: 未复核
- **复核裁定（C7 独立复核 2026-10-16）**: **保留，维持 P2**。completedIndex 写点全 grep 亲验：引擎内推进者仅 `ResourceRecordLoaderProvider:301`（file/excel）与 `EtlTaskStateStore:115`（exp 自有 store），DaoBatchStateStore 三处（:173/:238/:281）均为透传/初始化——orm/jdbc/gen 无推进者成立；DSL 装配亲验（ModelBasedBatchTaskBuilderFactory.buildLoader0 :431-449）：仅 fileReader/excelReader 接收 saveState 参数，jdbc/orm/generator 装配调用无该参数。owner doc 核对（docs-for-ai/03-modules/nop-batch.md:5/10/213）：「断点续传：通过 completedIndex 记录进度，中断后可恢复」为引擎级声明且 `saveState｜是否保存状态（断点续传）`，**未声明仅覆盖文件读取器**——契约缺口成立，无需校准。at-least-once 已声明语义覆盖正确性面（本条判级已据此不计数据丢失）→ P2 维持。现状：新发现·未修复。

### [nop-batch-C7-007] orm-reader keyset 锚点持有活实体：orderBy 列被本任务变更时游标漂移，剩余行被静默跳过（超出 at-least-once 的丢失面）

- **文件**: `nop-batch/nop-batch-orm/src/main/java/io/nop/batch/orm/loader/OrmQueryBatchLoaderProvider.java:42-46,131-145`
- **证据片段**:
  ```java
  static class LoaderState<S extends IDaoEntity> {
      S lastEntity;          // 锚点是活实体对象引用，不是排序键值快照
      QueryBean query;
      IEntityDao<S> dao;
  }
  ...
  // batchLoadProps有可能报错，这里将状态更新放到最后
  state.lastEntity = list.get(list.size() - 1);
  ```
- **严重程度**: P2（判级注明：可辩 P1——触发链完全具体且后果是静默丢数据；降 P2 因需要"orderBy 恰好配置在本任务会变更的列上"这一反模式配置，非常规装配）
- **并发场景**: 单线程即可触发（无需交错）；多 chunk 线程共享 LoaderState（`synchronized(state)` 串行化）不改变暴露面。
- **触发时序**: 查询模型声明 `<orderBy><field(name="status")/>`（status 为本任务 processor/consumer 要翻转的列）：T1 chunk N `load` 返回 [E1..E100]（status=WAITING），`state.lastEntity=E100`；T2 consumer/orm-writer 将 E100.status 改为 DONE（afterLoad claim 或业务写入同/异会话均可——若同 ORM 会话则活实体字段被就地变更）；T3 chunk N+1 `dao.findNext(E100,...)` 以 E100 的**当前**排序键值 (DONE) 为排他下界 seek → 所有仍为 WAITING 的行（排序上 < DONE）永远落在窗口之外 → 数据静默丢失。删除行的对照面：keyset 按键值 seek，锚点行被删不影响（安全）；排序键变更才是暴露面（`nop-persistence/nop-orm/.../OrmEntityDao.findNext:705` → queryToFindNextSql 按实体排序键值构造下界）。
- **现状**: `dao.findNext` 为平台 keyset 契约（隐含排序键在遍历期间不可变）；本类以**活实体引用**为锚，未冻结排序键值，也未在 DSL 校验 orderBy 列与任务写入列的交集。
- **风险**: "读待处理-改状态"是批处理最常见模式；一旦 orderBy 选了变更列，丢行是静默的（无日志、无错误、completedIndex/file 式账本均不知情），且 saveState 重启后用新锚点继续丢。
- **建议**: (1) 锚点改为排序键值快照（`lastEntity` → 持久化 orderBy 列值元组，或冻结实体的排序键字段）；(2) DSL 构建期校验 orderBy 列不得出现在 orm-writer 写列集合（最小改动告警）；(3) owner doc 增加"orderBy 列在任务运行期必须不可变"的显式契约。
- **失效面枚举（F3）**: keyset 消费面全清单——① OrmQueryBatchLoaderProvider（本条主体）；② `IEntityDao.findNext` 平台侧调用面（OrmEntityDao:617 findPage 内部复用——平台侧，不立案，登记）；③ sys-event non-broadcast-consumer.batch.xml 的 orm-reader——**当前配置无 orderBy**（走主键序，主键不可变，安全；但该 DSL 是用户可仿写的模板，契约缺失会复制扩散）；④ JdbcBatchLoaderProvider/JdbcPageBatchLoaderProvider——非 keyset（流式/offset，分别见 C7-009/C7-008），不命中本条。
- **信心水平**: 确定（机制）/ 很可能（实际命中配置的存在频率）
- **误报排除**: 不是"理论风险未具体化"——T1/T2/T3 是单线程内完整可执行序列；不是平台设施重审——缺陷点在 batch 侧锚点持有方式。
- **复核状态**: 未复核
- **复核裁定（C7 独立复核 2026-10-16）**: **保留，维持 P2**。`LoaderState.lastEntity` 活实体引用（OrmQueryBatchLoaderProvider:42-46）与锚点更新在 batchLoadProps 之后（:142-143 注释自证状态敏感）亲验成立；`load` 以 `dao.findNext(state.lastEntity,...)`（:133）按实体当前排序键值构造排他下界（DaoQueryHelper.appendCursorCmp 按锚点实体 orm_propValue 取值亲验）——活实体被就地变更时游标漂移机制成立；第 2 轮补充的「复合列安全（appendPkOrderBy 元组展开）/NULL eq 腿 is null」不修复用户列漂移的判断正确。触发为单线程完整序列、后果静默丢行，判级注记（反模式配置前提降 P2）合理 → P2 维持。现状：新发现·未修复。

### [nop-batch-C7-008] jdbc-reader 默认分页 loader 使用 offset 翻页：每 chunk 扫描代价线性增长（全任务 O(N²)）且并发写下方移导致跳行

- **文件**: `nop-batch/nop-batch-jdbc/src/main/java/io/nop/batch/jdbc/loader/JdbcPageBatchLoaderProvider.java:85-98`
- **证据片段**:
  ```java
  List<T> load(int batchSize, IBatchChunkContext context, LoaderState state) {
      LongRangeBean range = state.range;
      if (range == null) {
          range = LongRangeBean.longRange(0, batchSize);
      } else {
          range = LongRangeBean.longRange(range.getEnd(), batchSize);   // offset 随 chunk 线性推进
      }
      List<T> page = jdbcTemplate.findPage(state.sql, range.getOffset(), (int) range.getLimit(), rowMapper);
      // 分页查询成功后才提交游标。...
      state.range = range;
      return page;
  }
  ```
- **严重程度**: P2（有量化口径的退化 + 条件化丢失面；对应维度"大表长任务的翻页退化"定向项）
- **并发场景**: chunk 线程经 `synchronized(state)` 串行调用 load（无竞态）；退化与位移问题均不需要并发即可成立，"跳行"变体需要外部对源表的并发删除。
- **触发时序**: 增长口径：处理 N 行、batchSize=B 的任务执行 N/B 次 `findPage(offset=k*B)`，第 k 页数据库需扫描并丢弃 k*B 行 → 总扫描行数 ≈ N²/(2B)。N=1000 万、B=1000 时 ≈ 500 亿行扫描量，任务耗时随进度非线性恶化（对比同模块 orm-reader 的 keyset `findNext` 为每页 O(logN+B)）。位移时序：T1 任务翻到 offset=5000（已读完前 5000 行）；T2 外部进程删除源表前 100 行；T3 下一页 offset=5000 读到的行整体前移 100 位 → 第 5000~5099 行区间被跳过——静默丢失（超出 at-least-once）；反向插入则重复读（契约内）。`:94-96` 注释仅处理了"查询失败不推游标"的重试丢失面，未处理 offset 本身的快照不稳定性。
- **现状**: jdbc-reader 的 `<sql>`/`<query>` 两形态中，`JdbcPageBatchLoaderProvider`（按页模式）以 offset 实现；与 orm-reader 的 keyset 方案（C7-007 同族语境）不一致。
- **风险**: 大表导出/迁移任务随进度持续变慢直至运维判定挂死（C7 退化项）；活跃表上的跳行是静默数据完整性事件。
- **建议**: (1) 按 orderBy+keyset 重写分页（复用 orm 侧 keyset 思路或要求 SQL 携带可参数化游标条件）；(2) 短期在 owner doc 声明 jdbc-page loader 适用于"静态快照表"，活跃表应使用 orm-reader keyset 或 streaming（JdbcBatchLoaderProvider）；
- **失效面枚举（F3）**: jdbc 读取面全清单——① JdbcPageBatchLoaderProvider（本条主体）；② JdbcBatchLoaderProvider——流式游标（非 offset），退化面不同，见 C7-009，不命中本条；③ orm-reader——keyset（C7-007），不命中；④ ExportDbTool 导出（读侧走 ORM/DAO 全量流，非本类）——登记不命中。
- **信心水平**: 确定（机制与量化链）
- **误报排除**: 非热路径性能误报——这是长时运行退化（C7 定向项）与静默丢失（数据完整性），且给出了 N²/(2B) 量化口径。
- **复核状态**: 未复核
- **复核裁定（C7 独立复核 2026-10-16）**: **保留，维持 P2**。offset 翻页亲验（JdbcPageBatchLoaderProvider:85-98：`LongRangeBean.longRange(range.getEnd(), batchSize)` 线性推进 offset + `findPage(offset,...)`）；N²/(2B) 扫描量算术正确，与第 2 轮 4.2 节参数包络一致；并发删前移跳行（超出 at-least-once 的静默丢失）与插后移重复读（配 historyStore 时转化为 C7-015 主键冲突失败面）双面成立；:94-96 注释仅覆盖查询失败重试丢失面、未处理 offset 快照不稳定性的对比亲验成立 → P2 维持。现状：新发现·未修复。

### [nop-batch-C7-009] jdbc 流式 loader 整任务持有单条物理连接：长任务期间连接被服务端超时/网络瞬断杀死即任务失败，且该 loader 无断点恢复

- **文件**: `nop-batch/nop-batch-jdbc/src/main/java/io/nop/batch/jdbc/loader/JdbcBatchLoaderProvider.java:238-249,255-289`（newState 打开连接/语句/结果集）
- **证据片段**:
  ```java
  Connection connection = jdbcTemplate.currentConnection(querySpace);
  if (connection == null) {
      connection = jdbcTemplate.openConnection(querySpace);   // setup 时打开，任务级持有
      closeConnection = true;
  }
  ...
  context.onAfterComplete(err -> state.close());   // 仅任务结束时释放
  ```
- **严重程度**: P2
- **并发场景**: 单任务单连接（`synchronized(state)` 串行消费 ResultSet）；与 DB 服务端会话超时器（wait_timeout）、网络设备空闲回收的"异步对抗"。
- **触发时序**: 长任务（声明的大数据量场景下小时级）→ 连接被持有 T_total 期间：T1 消费端因 retryPolicy 退避 sleep（≤5min/轮，C7-004）或下游消费者慢导致 ResultSet 空闲超过 DB wait_timeout（MySQL 默认 8h，中间件常为分钟级）→ T2 服务端单方面断开 → T3 下一次 `readBatch` 抛 SQLException（连接失效）→ chunk 失败 → 任务 FAILED。由于 jdbc-reader 不支持 saveState（C7-006 ④），重启从零开始；若任务叠加 retryPolicy，重试阶段（`:43-46` → RetryBatchLoader 亦可包该 loader）对同一死连接重试注定失败，白白消耗退避时长后才终态失败。
- **现状**: 连接的获取/释放配对正确（onAfterComplete 关闭，异常路径 `newState:289` 翻译异常时关闭 own 连接）；缺陷是**持有时长与任务时长耦合**，长任务把"连接可用性"变成了任务可用性的单点。
- **风险**: 长任务对环境瞬断（DB 重启、failover、网络抖动）零容忍；失败后无恢复面（与 C7-006 叠加为全额重跑）；重试策略掩盖真实原因（重试一个已死的连接）。
- **建议**: (1) 短期：load 失败且为连接类 SQLException 时对 state 强制置 closed 并快速失败（避免无意义重试）；(2) 中期：提供按页+游标的长任务形态（与 C7-008 建议合流）；(3) owner doc 声明 streaming loader 的"任务时长 ≤ 连接最大空闲/存活时长"约束。
- **失效面枚举（F3）**: 长持有资源面全清单——① JdbcBatchLoaderProvider.connection（本条主体）；② 同类 PreparedStatement/ResultSet——随连接同生命周期，同族命中（同一 close 路径）；③ JdbcPageBatchLoaderProvider——**不命中**（每 chunk 独立查询、连接由 jdbcTemplate 池管理，短持有）；④ orm-reader——**不命中**（ORM 会话按 chunk/事务管理）；⑤ EtlTaskStateStore 状态文件句柄——**不命中**（写完即关）。
- **信心水平**: 确定（持有模型）；很可能（触发概率随任务时长上升）
- **误报排除**: 非资源泄漏（C6）——配对正确；本条是长时运行下的故障暴露面（C7），核心是"长持有 × 环境瞬断必然事件"。
- **复核状态**: 未复核
- **复核裁定（C7 独立复核 2026-10-16）**: **保留，维持 P2**。setup 时 `openConnection` 任务级持有 + `context.onAfterComplete(err -> state.close())` 唯一释放点亲验成立（JdbcBatchLoaderProvider setup ~:184-195 / newState 连接打开与异常路径 own 连接关闭 ~:255-289）；jdbc-reader 无 saveState（C7-006 ④）→ 失败后全额重跑叠加成立；RetryBatchLoader 包装对死连接重试注定失败的面成立（RetryBatchLoader:40-70 实测）。获取/释放配对正确（非 C6 泄漏）、缺陷为持有时长×环境瞬断暴露面——C7 定性恰当 → P2 维持。现状：新发现·未修复。

### [nop-batch-C7-010] 每 chunk 双条 INFO 与每轮重试 ERROR 全栈日志：长任务日志线性放大，毒记录场景无限放大

- **文件**: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTask.java:301-304,330-332,351-355`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/RetryConsumeHelper.java:55-57`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/AbstractRetryBatchConsumer.java:42-44`
- **证据片段**:
  ```java
  LOG.info("nop.batch.process-chunk-begin:taskName={},taskId={},taskKey={},threadIndex={},processCount={}...", ...);
  ...
  LOG.info("nop.batch.process-chunk-end:taskName={},...,historyCount={},completedIndex={}", ...);
  // RetryConsumeHelper —— 每轮重试一条全栈 ERROR
  LOG.error("nop.err.batch.retry-consume-fail:taskName={},taskId={},taskKey={},retryCount={}", ..., e);
  ```
- **严重程度**: P3（有量化口径的日志放大）
- **并发场景**: 所有 chunk 线程并行输出。
- **触发时序**: 增长口径：1 亿行、batchSize=100 的导出任务 → 100 万 chunk × 2 条 INFO = 200 万条日志（每条含 9-11 个字段插值）；叠加 C7-004 负重试时每失败轮 +1 条全栈 ERROR。周期任务（每 5s 空转一轮，单 chunk 空 loader 也产生 begin/end 两条）在天级 ≈ 34,560 条/任务。
- **现状**: INFO 双条含完整计数快照（排障有价值），但无采样/等级配置开关（`BatchConfigs` 无相关 cfg 项——grep 实测仅 CFG_BATCH_ASYNC_PROCESS_TIMEOUT）。
- **风险**: 长任务/周期任务贡献稳定日志洪流，稀释真正错误（如 C7-001 的周期异常）的信噪比；全栈 ERROR 在无限重试下放大。
- **建议**: chunk begin 降 DEBUG（保留 end INFO 或改为每 N chunk 汇总一条）；重试 ERROR 对重复异常（同 code 连续）做抑制计数；增加 `nop.batch.log.chunk-level` 配置项。
- **失效面枚举（F3）**: 日志放大点全清单——① `process-chunk-begin/end`（每 chunk 2 条 INFO，主体）；② `RetryConsumeHelper:55`（每重试轮 1 ERROR）；③ `AbstractRetryBatchConsumer:42`（每次首败 1 ERROR，与 ② 对同一次失败可能连续两条——同族命中）；④ `RetryBatchLoader:67`（每轮 load 重试 1 ERROR——同族命中）；⑤ `nop.batch.loader.chunk-end`（ResourceRecordLoaderProvider:294，每 chunk 1 条 INFO——同族命中）；⑥ `PartitionDispatchQueue` trace/debug 日志——有 isTraceEnabled/isDebugEnabled 守卫，**不命中**；⑦ `nop.batch.skip-error`（SkipConsumeHelper:36，每跳过一批 1 条 INFO——毒记录循环时并入 C7-005 面计数，同族命中）。
- **信心水平**: 确定
- **误报排除**: 非纯风格问题——给出量化频率并与 C7-001/C7-004 的异常风暴复合；C7 明确把"日志放大（循环内 error 日志无抑制）"列为检查项。
- **复核状态**: 未复核
- **复核裁定（C7 独立复核 2026-10-16）**: **保留，维持 P3**。process-chunk-begin/end 双 INFO（BatchTask.java:301/:351）、RetryConsumeHelper:55 / AbstractRetryBatchConsumer:42 / RetryBatchLoader:67 三处 ERROR、ResourceRecordLoaderProvider:294 loader INFO、BatchConfigs 仅 CFG_BATCH_ASYNC_PROCESS_TIMEOUT 亲验逐一成立；PartitionDispatchQueue 4 处 isTraceEnabled/isDebugEnabled 守卫（F3⑥ 不命中）亲验成立。量化口径与守卫缺失事实充分，P3 恰当 → 维持。现状：新发现·未修复。

### [nop-batch-C7-011] sys-event 触发链在 batch 侧无任何重叠防护：saveState 未配置 → 状态存储未接线，"同 taskName+taskKey 单活跃"守卫对该链结构性失效

- **文件**: `nop-batch/nop-batch-dsl/src/main/java/io/nop/batch/dsl/manager/ModelBasedBatchTaskBuilderFactory.java:191-192`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTask.java:165-173`；`nop-batch/nop-batch-dsl/src/main/java/io/nop/batch/dsl/runner/BatchTaskRunner.java:35-50`；触发配置 `nop-batch/nop-batch-sys/src/main/resources/_vfs/nop/job/conf/sys-event-batch-consumer.job.yaml:6` + `.../nop/batch-task/sys-event/non-broadcast-consumer.batch.xml`（被触发面，模块本体在 11 jar 之外，按本轮口径审 batch 侧守卫）
- **证据片段**:
  ```java
  // ModelBasedBatchTaskBuilderFactory —— saveState 非 true 即不接状态存储
  if (Boolean.TRUE.equals(batchTaskModel.getSaveState()))
      builder.stateStore(stateStore);
  // BatchTask.initTaskKey —— 无 taskKeyExpr 时每次执行生成 UUID
  if (StringHelper.isEmpty(taskKey)) taskKey = StringHelper.generateUUID();
  ```
- **严重程度**: P2（判级注明：守卫失效的后果依赖两个跨单元前提——nop-job 允许同 job 重叠执行、nop-sys claim 非原子；batch 侧可具体化的是"守卫结构性不覆盖"这一事实）
- **并发场景**: cron `0/5 * * * * ?` 的两次触发（job 调度线程 T1/T2）× 同一 DSL 任务；batch 侧无任何互斥体。
- **触发时序**: 前提链（实测）：non-broadcast-consumer.batch.xml 未声明 saveState → `:191` 条件不成立 → `BatchTask.stateStore=null` → `loadTaskState/saveTaskState` 全部短路（BatchTask.java:115/322）→ (taskName,taskKey) 唯一键与 `tryMarkTaskRunning` 闸门均不经过 → batch 层对重叠触发**零防护**。T1 触发 executeAsync → orm-reader 查出 WAITING 事件集 E → T2（重叠，若 nop-job 允许）触发第二次 executeAsync，其 loader 在 T1 的 claim 提交前执行同一查询 → 也读出 E → 双方各自 afterLoad `claimNonBroadcastEvents(E)` → 重复处理与否完全取决于 nop-sys claim 的条件更新原子性（nop-sys-dao 侧，跨单元不立案）。即使 nop-job 串行，job.yaml 的 cron/cfg 可配性（`@cfg:...cron-expr|0/5...`）使误配 <5s 周期或长任务（processor 慢于 5s）常态化重叠。
- **现状**: owner doc 声明"同 taskName+taskKey 只允许一个活跃实例"，但该守卫的生效前提（saveState=true）与触发链的实际装配（sys-event 消费任务无 saveState）不匹配；batch 侧亦无 JVM 内单实例互斥（BatchTaskRunner 单例无状态）。
- **风险**: 双执行下至少产生重复加载/重复 claim 尝试的资源浪费；若 nop-sys claim 条件更新覆盖不全（跨单元，未审），即双处理事件；长任务慢于 cron 时任务堆积。
- **建议**: (1) 对"扫描型周期任务"提供显式 `singleActive="true"` 声明，在 DSL/Runner 层用 DB 条件更新或平台 `IResourceLockManager` 实现与 saveState 解耦的单活跃守卫；(2) 短期在 non-broadcast-consumer.batch.xml 补 saveState+taskKeyExpr 或 owner doc 显式声明"重叠防护依赖 nop-job 调度语义 + nop-sys claim 原子性"（跨单元契约显式化）。
- **失效面枚举（F3）**: 单活跃守卫覆盖面全清单——① saveState=true + 稳定 taskKeyExpr 任务——守卫完整生效（C7-001 审其死区面）；② saveState=true + 无 taskKeyExpr（UUID）——守卫**结构性失效**（UUID 每次不同，唯一键永不冲突，`:170-171`），同族命中（本条同一根因的第二表现：周期触发堆叠执行互不知晓，且每执行一行死数据，并入 C7-003 增长面）；③ saveState=false（sys-event 链）——守卫完全不接线，本条主体；④ nop-batch-exp CLI 工具——进程级单实例，不命中；⑤ BizExport/Import（per-request 任务）——重叠是业务预期，不命中。
- **信心水平**: 确定（装配事实）；很可能（重叠后果依赖跨单元）
- **误报排除**: 非"平台设施不重审"误报——守卫装配逻辑（saveState 门控 + UUID 键）是 batch 本体代码；跨单元依赖已显式标注不立案范围。
- **复核状态**: 未复核
- **复核裁定（C7 独立复核 2026-10-16）**: **保留，维持 P2**。亲验成立：non-broadcast-consumer.batch.xml 无 saveState 属性 → ModelBasedBatchTaskBuilderFactory:190-191 门控不接 stateStore → BatchTask.loadTaskState/saveTaskState 全短路（stateStore=null 分支 :115-117/:322-324）→ (taskName,taskKey) 唯一键与启动闸门结构性不覆盖；UUID 绕过面（initTaskKey:170-171）成立；BatchTaskRunner 无状态单例、无 JVM 内互斥亲验成立。跨单元前提（nop-job 重叠语义、nop-sys claim 原子性）已显式划出不立案——batch 侧可立案面恰为「守卫结构性不覆盖」事实 → P2 维持。现状：新发现·未修复。备注：sys-event-batch-consumer.job.yaml `enabled|false` 缺省关闭，重叠面在启用该 job 或用户仿写配置时成立。

---

## 四、维度集外线索（CL，从 CL-016 起）

### [nop-batch-CL-016]（维度集外线索，不立案展开）
形态归属 C2/C9（锁与同步原语/并发高性能）。`DaoBatchStateStore.saveTaskState`（`DaoBatchStateStore.java:234`）为 singleton bean（`nop-batch-dao/.../app-batch-dao.beans.xml` `nopDaoBatchStateStore`）上的 `synchronized` 方法，锁内执行 `updateTask` DB I/O——JVM 全局串行化**所有任务所有 chunk** 的状态保存，长任务高并发下形成吞吐天花板与锁内 I/O 争用热点。严重度候选 P2。一句话证据：`public synchronized void saveTaskState(...)` + `updateTask(taskDao, task)` 同方法内。
- **复核处置（C7 独立复核 2026-10-16）**: **确认**。`synchronized saveTaskState`（DaoBatchStateStore.java:234）+ 锁内 `updateTask`（:254）+ singleton bean（app-batch-dao.beans.xml:9 `nopDaoBatchStateStore`）亲验成立。**与 CL-012（C6 轮登记）为同一站点同一事实的跨维度重复登记——按 C6 复核既定裁定合并处置，台账单列（CL-012=CL-016），不得双记**；P2 候选维持。

### [nop-batch-CL-017]（维度集外线索，不立案展开）
形态归属 C3（异步链正确性——幽灵任务）。`BatchProcessorConsumer.consume`（`BatchProcessorConsumer.java:85-99`）async 超时后 `batchChunkCtx.cancel + NopTimeoutException`，但已提交的异步 processor 协程仍在运行（取消为协作式），其输出追加到已无人消费的 outputs 队列后被丢弃——超时未终止在途处理，任务以失败结算而后台处理继续产生副作用。严重度候选 P2。一句话证据：latch await 超时路径仅 cancel+抛错，无对在途 processor 的汇合/中断。
- **复核处置（C7 独立复核 2026-10-16）**: **确认**（与 CL-013 合并）。机制亲验成立（BatchProcessorConsumer 超时仅 cancel+抛 NopTimeoutException，已提交异步 processor 无 future 跟踪无取消）。**与 CL-013（C6 轮登记）为同一事实——按 C6 复核既定裁定合并处置、候选判级对齐 P2（默认装配不可达 + asyncProcessor 为 opt-in 且全仓 XML 零使用），台账单列（CL-013=CL-017），不得双记**。

### [nop-batch-CL-018]（维度集外线索，不立案展开）
形态归属 C6（资源生命周期）。`DefaultBizEntityExporter.cleanup`（`DefaultBizEntityExporter.java:67-77`）成功导出的临时文件删除依赖 `GlobalExecutors.globalTimer().schedule(10min)` 一次性任务——进程在延迟窗口内重启则临时文件成为孤儿，且无启动清扫钩子。严重度候选 P3。一句话证据：`globalTimer().schedule(() -> tempResource.delete(), keepTempFileMinutes, MINUTES)`。
- **复核处置（C7 独立复核 2026-10-16）**: **确认**。cleanup 延迟删除 + 无启动清扫钩子亲验成立（DefaultBizEntityExporter.cleanup :67-79）。P3 候选维持（孤儿文件为临时导出物，影响有界）。

### [nop-batch-CL-019]（维度集外线索，不立案展开）
形态归属 C6/F6（落盘持久化屏障）。`EtlTaskStateStore.saveStateFile`（`EtlTaskStateStore.java:129-144`）用 tmp 写 + `ATOMIC_MOVE` 原子替换，注释声明的故障模型是"进程写入中途被 kill"（rename 语义可覆盖）；但 `FileHelper.writeText` 无 fsync，断电/内核崩溃场景下目标文件内容持久性无屏障（F6: flush ≠ fsync）。声明模型内成立，模型外（掉电）存疑。严重度候选 P3。一句话证据：`writeText(tmpFile...) + Files.move(ATOMIC_MOVE)` 全程无 force/fsync。
- **复核处置（C7 独立复核 2026-10-16）**: **确认**。注释声明的故障模型亲验属实（EtlTaskStateStore.saveStateFile 注释原文「进程在写入中途被kill……保证目标文件要么是旧内容要么是完整新内容」——即声明模型覆盖进程 kill，未声明掉电/内核崩溃）；`FileHelper.writeText` + `Files.move(ATOMIC_MOVE)` 全程无 fsync/force 亲验成立。声明模型内成立、模型外存疑的定性准确，P3 候选维持；处置出口二选一：owner doc/注释补声明「掉电场景状态文件不保证持久」，或 writeText 后补 fsync。

### [nop-batch-CL-020]（维度集外线索，不立案展开）
形态归属 C2（锁内 I/O，设计约束类）。四处 per-execution loader 的 `synchronized(state) { load... }`——`OrmQueryBatchLoaderProvider.java:102`、`JdbcPageBatchLoaderProvider.java:79`、`JdbcBatchLoaderProvider.java:187`、`BatchGenLoaderProvider.java:59`——均为游标正确性所需（keyset/游标必须串行推进），但持锁做同步 DB I/O，concurrency>1 时消费线程在 load 点完全串行，实际并行度上限=loader 串行吞吐（owner doc 的 concurrency 承诺对含 dispatcher 的路径才成立——见 `ModelBasedBatchTaskBuilderFactory.safeLoad:452-455` 的注释声明）。严重度候选 P3（属声明过的设计权衡）。
- **复核处置（C7 独立复核 2026-10-16）**: **确认**。四处 `synchronized(state)` 亲验成立（OrmQueryBatchLoaderProvider:102 / JdbcPageBatchLoaderProvider:79 / JdbcBatchLoaderProvider:187 / BatchGenLoaderProvider:59）；`safeLoad` 注释声明（ModelBasedBatchTaskBuilderFactory ~:452-455「source函数如果非线程安全，需配置dispatcher使fetch串行化」）与 loader 侧游标正确性需求（keyset/游标必须串行推进）均实测成立——游标正确性所需 + 声明过的设计权衡，P3 候选维持（锁内 I/O 族与 CL-012/016/036 合并修复面）。

### [nop-batch-CL-021]（维度集外线索，不立案展开）
形态归属 C4（线程池队列有界性）。`ExportDbTool.java:150` / `ImportDbTool.java:232`：`DefaultThreadPoolExecutor.newExecutor("export-db"/"import-db", threadCount, Integer.MAX_VALUE)` 第二参为无界队列容量；CLI 工具任务数=配置表数（有界），风险低。严重度候选 P3。
- **复核处置（C7 独立复核 2026-10-16）**: **确认**。`Integer.MAX_VALUE` 队列容量亲验成立（ExportDbTool.java:150 / ImportDbTool.java:232）；CLI 任务数有界、submit 后 waitAll 汇合，P3 候选维持。

---

## 五、已验证一致 / 不立案结论（复核阶段可直接引用）

1. **completedIndex 与 historyStore 双账本漂移**：不立案。history 写入与业务 consume 同事务（BatchTaskBuilder:381-411 自动提升 process scope，含回归测试名 TestHistoryTxnPromotion 的存在佐证接线），`saveTaskState` 在事务提交之后执行——唯一可能的漂移方向是 history 领先 completedIndex（事务提交后、saveState 前崩溃），该方向由 `filterProcessed` 幂等兜住，completedIndex 落后仅造成读侧重读（读放大），不丢失、不重复。
2. **PartitionDispatchQueue 分区部分失败残留**：不立案。队列纯内存、任务级生命周期（`AsyncFetchPartitionDispatchLoaderProvider:78` 每次 setup 新建，`context.onAfterComplete → queue.finish()`），失败路径 fetch 线程异常经 `exception` AtomicReference 在消费侧重抛（`:89-99`），无跨任务持久化分区状态。
3. **`acquirePermits` 完成后丢弃**：有注释声明的有意行为（`PartitionDispatchQueue.java:332-350`），带 500ms tryAcquire 循环避免永久阻塞，日志可观测（INFO discard-batch-when-finished），不立案。
4. **metrics label 基数**：BatchTaskMetricsImpl 标签为 job.name×task.name×status（配置枚举空间）；且实测 nop-batch 主链路无装配者（metrics 恒 null），基数爆炸与累积增长当前均不成立。
5. **时钟敏感站点**：batch 侧读时钟仅 4 类——chunk 耗时统计（BatchTask:300/350，仅日志）、ETL 状态时间戳（元数据）、`ResourceRecordLoaderProvider.waitUntilUnderLimit` deadline（墙钟，回拨仅使等待窗口拉长 ≤overLimitWaitMillis，有界无害）、DaoBatchStateStore start/restart/end 时间戳（纯元数据，无 lease 判定——这也正是 C7-001 缺 stale 判定的另一面）。sys-event 的 `leaseExpireTime/getEstimatedNow()` 时钟使用在 nop-sys（跨单元）与 batch DSL 配置文本内（声明面），本轮不立案。

## 六、总计

| 类别 | 数量 | 明细 |
|---|---|---|
| C7 发现 | 11 | P1×2（C7-001, C7-003）；P2×8（C7-002, C7-004, C7-005, C7-006, C7-007, C7-008, C7-009, C7-011）；P3×1（C7-010） |
| 维度集外线索 | 6 | CL-016 ~ CL-021 |
| 挂死点清单 | 11 项 | 第三节清单（3 项立为发现，2 项平台契约依赖登记，6 项有界/设计意图不立案） |
| 单调增长源 | 12 项 | 第一节清单（3 项无界立为 C7-003，其余有界/未接线） |

---

## 维度复核结论（独立复核子代理，2026-10-16）

- **复核人立场**: 与初审/深挖代理相互独立的复核轮；以怀疑姿态对全部 16 条发现 + 7 条 CL 线索逐条亲验 file:line（行号漂移以实际代码修正），历史对照面（cra-index、plan 2461~2467、WI 1.1–1.6 台账、owner doc、ai-dev/bugs、ai-dev/lessons）按复核阶段授权读取。
- **现状标注基线**: `git diff b3a9c76bed..HEAD -- nop-batch/` = **0 变更**、`git log b3a9c76bed..HEAD -- nop-batch/` = **0 提交**（2026-10-16 实测）——全部发现现状为**新发现·未修复**；nop-batch 直接历史并发审计 0 命中（plan 2467 Phase 1 实测），WI 1.1–1.6 单元（nop-stream/nop-job/nop-wf/nop-task/nop-retry/nop-tcc）与本单元零代码交集，同族不同模块一律标新发现并备注同族关联。
- **F3 完备性补全**: 复核独立 grep 未发现初审遗漏的立案面（清理面全资产 grep、taskStatus 写者 grep、completedIndex 写者 grep、getRetryDelay 消费点 grep 均与初审/深挖枚举一致或更收敛）；对 C7-015 补一处精化（filterProcessed 塌缩键命中时 map.remove 取最后一条，重放的是首条记录），不改判级。

### 逐条裁定汇总

| 发现 | 初审判级 | 复核裁定 | 终审判级 | 一句话依据 |
|---|---|---|---|---|
| C7-001 | P1 | 保留（表述校准） | **P1** | tryMarkTaskRunning 无 stale 判定/全引擎唯一状态写者/G3 零日志/17280 次/天算术均亲验；「无恢复入口」按 C6 复核同口径收敛为「无专用运维端点、通用 CRUD 理论可达不在契约内」（不改变 P1）；5s 风暴示例锚定 saveState=true 任务前提。 |
| C7-002 | P2 | 保留 | **P2** | future 完成保证双断裂点（:226 拒绝/:243 Error）亲验；F3② 幽灵变体亲验；判级注记（缺省池不可达）恰当。 |
| C7-003 | P1 | 保留 | **P1** | 清理面独立 grep 二态判定成立（DB 清理代码完全不存在）；UUID 增长路径、复合主键修正采信；同族对照 WI 1.6 C7-001（不同模块，新发现不变）。 |
| C7-004 | P2 | 保留 | **P2** | max<0 无限/无 Guard/xdef 无约束/双链计数 M+2 vs M+1 逐点亲验。 |
| C7-005 | P2 | 保留 | **P2** | shouldSkip 缺省无限/跳过不入任何账本/xdef 无约束亲验。 |
| C7-006 | P2 | 保留 | **P2** | setCompletedIndex 全 grep + DSL 装配亲验；owner doc 未声明仅覆盖 file/excel——契约缺口成立，无需校准。 |
| C7-007 | P2 | 保留 | **P2** | 活实体锚点 + findNext 按当前键值 seek 亲验；单线程完整触发序列。 |
| C7-008 | P2 | 保留 | **P2** | offset 翻页 + N²/(2B) 算术 + 快照不稳定双面亲验。 |
| C7-009 | P2 | 保留 | **P2** | 任务级连接持有 + 唯一释放点 + 无 saveState 叠加亲验；非泄漏定性正确。 |
| C7-010 | P3 | 保留 | **P3** | 全部日志站点 + BatchConfigs 单 cfg 项亲验。 |
| C7-011 | P2 | 保留 | **P2** | sys-event 链无 saveState → 守卫结构性不覆盖亲验；跨单元前提已正确划出；job enabled 缺省 false 备注。 |
| C7-012 | P2 | 保留（表述校准） | **P2** | KILLED 吸收态/缺省 cancel="kill"/xmeta updatable/version CAS 无 fencing 全亲验；「无 API 解除」按 C6 同口径校准为引擎层无专用端点。 |
| C7-013 | P2 | **降级 + 事实修正** | **P3** | 溢出推导有误：`(long)Math.pow(2,rt)` 在 rt≥63 饱和为 Long.MAX_VALUE（第 2 轮 3.1 节自证却未代入推导），零延迟窗口实为 **rt∈[64−k, 62]**（缺省 1000 时仅 {61,62} 两轮），rt≥63 负溢出被钳制回落 maxRetryDelay——「永久零延迟紧循环/风暴升级/4.6h 恶化点」不成立；残余缺陷（钳制不拦精确 0，≤k−1 轮有界突发）真实，降 P3 保留。 |
| C7-014 | P3 | 保留 | **P3** | appendCursorCmp NULL 抛错亲验；响亮/可规避/无丢失校准合理。 |
| C7-015 | P3 | 保留 | **P3** | 塌缩/last-win/复合主键冲突/全过滤静默跳过四步亲验；补一处精化不改判级。 |
| C7-016 | P3 | 保留 | **P3** | 双谓词不对称 + skip 外层装配亲验；bizFatal 唯一交集成立。 |
| CL-016 | P2 候选 | 确认（合并） | — | 与 CL-012（C6 轮）同一站点——合并处置，台账单列（CL-012=CL-016）。 |
| CL-017 | P2 候选 | 确认（合并） | — | 与 CL-013（C6 轮）同一事实——沿 C6 复核裁定合并、判级对齐 P2，台账单列（CL-013=CL-017）。 |
| CL-018 | P3 候选 | 确认 | — | timer 延迟删除 + 无启动清扫亲验。 |
| CL-019 | P3 候选 | 确认 | — | 注释声明故障模型=进程 kill（亲验原文）；fsync 缺失属实；声明模型内成立。 |
| CL-020 | P3 候选 | 确认 | — | 四处 synchronized(state) + safeLoad 声明注释亲验；设计权衡已声明。 |
| CL-021 | P3 候选 | 确认 | — | Integer.MAX_VALUE 队列亲验；CLI 有界低危。 |
| CL-036 | P3 候选 | 确认 | — | LOG.info 与 notifyAll 同处 synchronized(state) 亲验；并入锁内 I/O 族合并修复面。 |

### 特别复核指令执行记录

1. **C7-001 终裁**：P1 维持。a) 无 stale 判定事实成立（WHERE 仅 `taskStatus>10`，workerId 有值无判定）；b) 风暴量级算术恒真、batch 侧零日志成立，但示例前提校准（sys-event 链无 stateStore 不适用、job.yaml enabled 缺省 false）；c) 恢复入口 grep 亲验（IBatchTaskManager 6 个构造/加载方法、BizModel 裸 CrudBizModel、INopBatchTaskBiz 空接口、beans 装配仅 2 处）——按 C6 轮 C6-001 复核既定口径校准表述，P1 不变；d) 触发时序 A/B 每步可信（onTaskComplete 双次保存失败路径、崩溃窗口均为长时运行必然事件）。
2. **C7-003 终裁**：P1 维持。二态判定经独立全资产 grep 复核成立（「代码完全不存在」准确）；UUID 增长路径与增长速率口径具体；第 2 轮复合主键修正采信后第 1 轮两处表述（IN 查询常数退化、物理逐次静默插入）已按深挖修正，主体结论（零保留无界增长）不受影响；与 WI 1.6 C7-001（retry 三表零保留 P1）同族不同模块，新发现标注不变。
3. **C7-013 终裁**：降级 P2→P3 + 事实修正（见裁定表；这是本轮唯一判级变更，争议点详见下节）。
4. **C7-006 终裁**：P2 维持。orm/jdbc/gen loader 确无 completedIndex 推进（写点全 grep）；owner doc 将断点续传声明为引擎级特性且无适用范围限定——契约缺口成立，非已声明边界。
5. **CL 处置**：7 条全部确认、零驳回；CL-016 与 CL-012 合并、CL-017 与 CL-013 合并（沿 C6 复核既定裁定），台账单列不双记。

### 争议点（供 7.1 合并裁定参考）

1. **C7-013 溢出推导错误（本轮最重要修正）**：第 2 轮报告的信心水平标注「确定」的归零点推导（`tryDelay·2^rt mod 2^64` 在 rt+k≥64 恒 0 → rt≥61 永久归零）忽略了其在 3.1 节自己指出的 `(long)Math.pow(2,rt)` rt≥63 饱和为 Long.MAX_VALUE——乘数不再是 2^rt。复核以精确 Java 语义逐点模拟：缺省 retryDelay=1000 零窗口仅 {61,62} 两轮，rt≥63 回落 5min 钳制。修复建议 (1)（对 0 值钳制）仍然成立且值得做，但「随时间恶化的风暴升级」叙事必须废弃。
2. **C7-001 的 5s 风暴示例锚定**：sys-event 链（5s cron 的唯一仓内实例）无 stateStore，不产生也不遭遇 RUNNING 死区；风暴量级应锚定为「saveState=true 任务 × 5s 级 cron 触发」的通用前提。不改变 P1（时序 A 的崩溃窗口不需要 cron 前提）。
3. **恢复入口表述口径**（C7-001/C7-012）：两处「无恢复入口/无 API 解除」统一按 C6 轮复核口径收敛为「无专用运维端点；通用 CRUD 写路径理论可达但不在契约内（无转移校验、依赖 version CAS 随机冲突驱逐旧进程）」——C6 轮已裁定该口径不改变同形态 P1，本轮沿用。
4. **C7-011 适用前提备注**：sys-event-batch-consumer.job.yaml `enabled|false` 缺省关闭，重叠面在显式启用或用户仿写配置时成立（batch 侧守卫结构性缺失不依赖此前提）。

### 复核统计

| 项 | 数量 | 明细 |
|---|---|---|
| 发现复核 | 16 | 保留 15（其中 1 条降级：C7-013 P2→P3）、驳回 0 |
| 终审判级 | P1×2 / P2×9 / P3×5 | P1：C7-001、C7-003；P2：C7-002/004/005/006/007/008/009/011/012；P3：C7-010/013/014/015/016 |
| CL 线索 | 7 | 确认 7 / 驳回 0；跨维度合并 2 组（CL-012=CL-016、CL-013=CL-017），台账单列 |
| 现状标注 | 16+7 | 全部新发现·未修复（基线 b3a9c76bed 起 nop-batch/ 零代码变更，git 实测） |
| 同族对照 | 3 组 | C7-001↔WI 1.6 C6-002（RUNNING 残留砖化）+ WI 1.6 C8-001（停摆族）；C7-002↔WI 1.6 C8-001；C7-003↔WI 1.6 C7-001（三表零保留）+ WI 1.5 MA-05（扫描无界） |

## 最终保留项（C7 维度终裁清单）

| # | ID | 终审判级 | 一句话结论 |
|---|---|---|---|
| 1 | nop-batch-C7-001 | **P1** | 任务行永久 RUNNING 死区：终态保存失败/进程崩溃后启动闸门（无 stale 判定）永久拒绝重启 + 周期触发错误风暴；无专用运维恢复端点（通用 CRUD 理论可达不在契约内）。 |
| 2 | nop-batch-C7-003 | **P1** | 批处理三表（nop_batch_task / nop_batch_record_result / nop_batch_task_var、附 nop_batch_file）零保留机制：全资产面无任何清理代码与装配，长时运行单调增长无界。 |
| 3 | nop-batch-C7-002 | P2 | chunk future 完成保证双断裂（executor 拒绝 + 非 Exception Error 逃逸）→ 幽灵任务/静默永久挂死。 |
| 4 | nop-batch-C7-004 | P2 | maxRetryCount 负值=无限重试（惯例反转）无 DSL/Setter 校验；消费链与加载链尝试次数不一致（M+2 vs M+1）。 |
| 5 | nop-batch-C7-005 | P2 | skipPolicy 缺省 maxSkipCount=0 语义无限跳过；被跳过记录不入任何账本，周期任务对毒记录永久循环丢弃。 |
| 6 | nop-batch-C7-006 | P2 | completedIndex 断点推进仅 file/excel loader 生效：orm/jdbc/gen 的 saveState=true 断点续传空转，owner doc 未声明该限定（契约缺口）。 |
| 7 | nop-batch-C7-007 | P2 | orm-reader keyset 锚点持有活实体：orderBy 列被本任务变更时游标漂移，剩余行静默跳过。 |
| 8 | nop-batch-C7-008 | P2 | jdbc-reader 缺省分页 offset 翻页：O(N²/2B) 扫描退化 + 并发写下方移跳行（静默丢失）。 |
| 9 | nop-batch-C7-009 | P2 | jdbc 流式 loader 整任务持有单条物理连接：环境瞬断即任务失败，无断点恢复，重试策略掩盖真因。 |
| 10 | nop-batch-C7-011 | P2 | sys-event 触发链 saveState 未配置 → 单活跃守卫结构性不覆盖；UUID taskKey 任务同族绕过。 |
| 11 | nop-batch-C7-012 | P2 | KILLED 永久吸收态（缺省 cancel() 即可达）+ 恢复入口仅无校验通用 CRUD 且与 version CAS 无 fencing 交互。 |
| 12 | nop-batch-C7-010 | P3 | 每 chunk 双条 INFO + 每轮重试 ERROR 全栈：长任务日志线性放大，毒记录场景无限放大。 |
| 13 | nop-batch-C7-013 | P3 | exponentialDelay 溢出钳制不拦精确 0：retryDelay=o·2^k 时重试中段出现 ≤k−1 轮有界零延迟突发（缺省配置 rt∈{61,62}）——复核修正：非永久紧循环。 |
| 14 | nop-batch-C7-014 | P3 | orm-reader keyset 游标对 NULL 排序值确定性失败墙：任务永不完成，cron 下每轮全额重扫后失败。 |
| 15 | nop-batch-C7-015 | P3 | recordKeyExpr 空值塌缩 "default" + 同键重复记录复合主键冲突：确定性失败 + 独占 chunk 场景静默误跳。 |
| 16 | nop-batch-C7-016 | P3 | retry/skip 缺省异常谓词不对称：bizFatal 异常被重试侧判死后落入跳过侧缺省谓词被静默丢弃。 |

> CL 台账（不立案展开，全部确认）：CL-016（=CL-012 合并，锁内 DB I/O，P2 候选）、CL-017（=CL-013 合并，幽灵 processor，P2 候选）、CL-018（导出临时文件重启孤儿，P3 候选）、CL-019（ETL 状态文件无 fsync，声明模型内成立，P3 候选）、CL-020（per-execution loader 锁内 DB I/O 设计权衡，P3 候选）、CL-021（CLI 无界队列容量，P3 候选）、CL-036（锁内日志 I/O，P3 候选）；锁内 I/O 族（CL-012/016/020/036）合并修复面。
