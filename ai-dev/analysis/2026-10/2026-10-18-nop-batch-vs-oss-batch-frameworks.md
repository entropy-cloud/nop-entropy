# nop-batch 设计实现剖析与开源批处理框架对比

> Status: open
> Date: 2026-10-18
> Scope: nop-batch 全模块；Spring Batch 6.1.0-SNAPSHOT（`~/sources/spring-batch` 浅克隆）；JBeret/Jakarta Batch 2.1（`~/sources/jsr352`）；snail-job 2.0.0（`~/sources/snail-job`）；PowerJob 5.1.2（`~/sources/PowerJob`）。P0/P1/P2 建议已收敛为补全路线图：`ai-dev/backlog/nop-etl-platform-completion-roadmap.md`（WI0–WI16，与 ETL 平台对比共用）
> Conclusion: nop-batch 在 chunk 抽象（批量 Loader + flatMap Processor + Collection Consumer）、事务范围可配置、Provider/Hooks 式扩展、DSL 森林集成四个维度上结构性优于 Spring Batch 与 JSR-352 族（两者均为单条 readItem）；相对 snail-job/PowerJob 的优势在 chunk/记录级断点续传与声明式数据管道，弱项在运维闭环。DB 源行级续传经用户裁定撤回（状态驱动任务重跑即天然过滤）。**运行控制经用户追问后裁定：接入 nop-job 已有控制面（cancelFire/rerunFire/cancelAsync），不自建第二套 API**——反向钩子 `BeanMethodJobInvoker.cancelAsync` 现成，只缺 `BatchTaskRunner.cancel` + 运行中 context 注册表 + 状态关联列。建议按 P0（接线三断点）/P1（metrics 自动接线 + reduce 聚合步骤）/P2（遗留物清理）分级增强，不建议引入 Spring Batch 式 Job/Step 编排或 StepScope。

## Context

- 问题：nop-batch 的设计与实现质量如何？与主流开源批处理软件（Spring Batch、snail-job、PowerJob）相比有哪些结构性优势与缺口？是否有需要改进或增强的地方？
- 方法：nop-batch 逐模块源码精读 + 三套开源参考源码定向取证（均落在 `~/sources/`，clone 用 `_clone.sh` 重试脚本）；已吸收 `ai-dev/analysis/2026-08/2026-08-16-nop-batch-design-and-concurrency-review.md` 的缺陷审查结论并核查其修复状态；调度域对比（nop-job vs PowerJob vs snail-job）已有专文 `ai-dev/analysis/2026-08/2026-08-04-nop-job-vs-powerjob-vs-snail-job-deep-analysis.md`，本文不重复，只覆盖批处理语义域。
- 设计哲学基线：`docs/theory/why-springbatch-is-bad.md`（Nop 官方对 Spring Batch 的批判与 NopBatch 设计动机），本文在其之上做源码级验证与增量对比。

---

## 一、nop-batch 设计与实现剖析

### 1.1 模块结构与依赖哲学

nop-batch 拆为 14 个子模块（core/dsl/orm/jdbc/dao/service/gen/exp/biz/api/meta/sys/app/web/codegen），核心引擎 `nop-batch-core` 的 pom **只依赖 `nop-xlang` 一个传递根**——ORM/JDBC/文件/DAO 全部是外挂模块，经 `Provider.setup(context)` 接入（`nop-batch-core/pom.xml`；`IBatchLoaderProvider.java:9-36`）。这与 Nop 平台"核心只留抽象、能力按需 Delta 叠加"的总体风格一致。

代码规模：`nop-batch` 主源码约 252 个 Java 文件；核心执行器 `BatchTask.java` 仅 368 行，`BatchTaskBuilder.java` 456 行。对照组：Spring Batch master 的 `spring-batch-core` 单模块 410 个 Java 文件（不含 infrastructure/integration/test）。

### 1.2 核心 SPI：Provider/Setup 两段式（Hooks 模型）

三大 SPI 均为"工厂 + 内层接口"两段式：

```java
// IBatchLoaderProvider.java:9-36
public interface IBatchLoaderProvider<S> {
    IBatchLoader<S> setup(IBatchTaskContext context);
    interface IBatchLoader<S> {
        List<S> load(int batchSize, IBatchChunkContext context); // 批量、线程安全契约显式声明
    }
}
```

- `IBatchProcessor.process(S item, Consumer<R> consumer, IBatchChunkContext)` 是 **flatMap 语义**：一次输入可产生 0..N 输出，输出即产即消（`why-springbatch-is-bad.md:197-220`）。
- `IBatchConsumer.consume(Collection<R> items, IBatchChunkContext)` 直接接收集合，与 Loader 返回类型对偶。
- 三者共享同一个 `IBatchChunkContext`，通过 `onChunkEnd`/`onAfterComplete` 等回调注册生命周期动作（`ResourceRecordLoaderProvider.java` 的 `batchChunkCtx.onAfterComplete(...)` 模式），Provider 保持 IoC 单例，无需 Spring Batch 的 StepScope 动态 bean 重定义。

### 1.3 执行引擎：并发 chunk 循环 + fail-fast

`BatchTask.executeAsync`（`BatchTask.java:91-150`）：

1. `stateStore.loadTaskState` 恢复状态 → `loaderProvider.setup` / `chunkProcessorProvider.setup` → `fireTaskBegin`；
2. 启动 `concurrency` 个 chunk 循环线程，共享同一个 Loader/Processor/Consumer（要求线程安全，`BatchTask.java:142` 注释显式声明）；
3. `primaryErrorRef`（AtomicReference + CAS，`BatchTask.java:45-48`）保留首个非取消异常，防止 cancel 异常掩蔽根因；
4. 2026-08 审查缺陷 4 已修复：chunk 失败时先 `completeExceptionally(future)` 再 `cancel(STOP)`，兄弟线程 fail-fast（`BatchTask.java` executeChunkLoop，见 2026-08-16 分析"八、修复记录"）。

### 1.4 Chunk 处理链与可靠性语义

消费链包装顺序（`BatchTaskBuilder.buildChunkProcessor`）：Skip 最外 → Retry 在内 → 可选 history → 业务 consumer；事务 scope 经 `InvokerBatchConsumer` 按 `transactionScope ∈ {none, chunk, process, consume}` 插入（`BatchTaskBuilder.java:384-422`；`BatchTransactionScope.java`）。

可靠性语义（已文档化，`docs-for-ai/03-modules/nop-batch.md:10-11`）：

- **at-least-once**：chunk 提交才推进 checkpoint；失败 chunk 的行不标记 done（缺陷 6 修复后），resume 重投。
- **记录级幂等**：`historyStore` → `DaoBatchRecordHistoryStore.saveProcessed` 与业务 consume **同事务**写 `NopBatchRecordResult`，`filterProcessed` 按 recordKey 跳过（2026-08 缺陷 7 已修复——`saveProcessed` 现有真实写路径，`DaoBatchRecordHistoryStore.java:78-87`）。
- 重试支持 chunk 级 + 逐条降级（`RetryAllBatchConsumer`/`RetryOneByOneBatchConsumer`），配 `snapshotBuilder="orm-entity"` 恢复 ORM 实体态（`docs-for-ai/03-modules/nop-batch.md:13`）。

### 1.5 分区并行：共享 Loader 的微队列模型

与 Spring Batch"每分区独立 Reader"不同，nop-batch 的 `dispatcher` 在**一个共享 Loader 之后**按 `partitionIndexField` 投递到最多 32768 个微队列（`PartitionDispatchQueue`），N 个消费线程按队列租借保证"同分区顺序处理"（2026-08 缺陷 9 修复后 fetch+入队串行化保证页序）。集群场景经可选注入 `nopJobPartitionResolver` → `partitionRange` → orm-reader 自动追加分区过滤（`BatchTaskRunner.java:24,42-47`、`OrmQueryBatchLoaderProvider.java:116-121`）。

### 1.6 状态持久化与断点续传

| 能力 | 实现 | 证据 |
|------|------|------|
| 任务状态 | `IBatchStateStore` SPI（`loadTaskState`/`saveTaskState`） | `IBatchStateStore.java:13-17` |
| DB 状态存储 | `DaoBatchStateStore`：条件 UPDATE 原子抢占启动（防双实例）+ 乐观锁 | `DaoBatchStateStore.java:139-195`；`model/nop-batch.orm.xml:128-141` 已有 `UK_NOP_BATCH_TASK_NAME_KEY` 唯一键（2026-08 缺陷 8 已修复） |
| 文件 loader 续传 | `ResourceRecordLoaderProvider` 按 rowNumber completedIndex 压实 + resume skip | `ResourceRecordLoaderProvider.java:134-295` |
| **ORM/JDBC loader 续传** | 无行级续传——对状态驱动任务（处理时改实体状态）是正确设计：重跑同 filter 天然排除已处理行；historyStore 仅兜底 | `nop-batch-orm` 无 completedIndex |

### 1.7 DSL 与平台集成

- 声明式 `*.batch.xml`（schema：`nop-kernel/nop-xdefs/.../nop/schema/task/batch.xdef`）与 Java API `BatchTaskBuilder.create()` 汇合到同一条装配链（`BatchTaskManagerImpl.java:89-124`、`ModelBasedBatchTaskBuilderFactory.java`）。
- `batch.xlib` 的 `Execute` 宏标签在**编译期**把 batch DSL 固化进 nop-task AST（`batch.xlib:10-58`），运行期零解析开销——batch 可作为工作流 step 的原子步骤。
- 记录格式复用通用 NopRecord 模型（`record:file-model`），文件解析能力独立于批处理可复用。
- 生命周期回调：DSL 可配 onTaskBegin/End、onChunkBegin/End 等 12 种监听（`docs-for-ai/03-modules/nop-batch.md:16`）。

### 1.8 当前遗留缺口（截至本文核查）

| 缺口 | 2026-08 审查状态 | 2026-10-18 核查 |
|------|------------------|-----------------|
| historyStore 写路径（缺陷 7） | P1 未修 | **已修复**（`DaoBatchRecordHistoryStore.java:78-87` 真实写路径 + 同事务注释） |
| 启动互斥唯一键（缺陷 8） | P1 未修 | **已修复**（`nop-batch.orm.xml:128-135` UK_NOP_BATCH_TASK_NAME_KEY） |
| fetch 线程取消泄漏（缺陷 10） | P2 未修 | **已修复**：`PartitionDispatchQueue.acquirePermits` 改 `tryAcquire(permits, 500ms)` 循环 + `finished` 检查，队列结束后丢弃批次并退出（`PartitionDispatchQueue.java:332-350`，注释明确引用泄漏场景） |
| 运行时 kill/cancel API | 缺失 | 仍缺失：只有 KILLED 状态检查（`DaoBatchStateStore.java:108-109`）与 `taskContext.cancel` 标志位，无管理端入口 |
| DB loader 断点续传 | 缺失 | 仍缺失（见 §1.6） |
| `NopBatchTaskState` 实体 | 疑似遗留物 | 仍存在：entity + BizModel 有，但 `nop-batch.orm.xml` 无对应定义 |
| metrics 自动接线 | 有接口无默认落地 | 部分改善：`BatchTaskMetricsImpl` 已是 Micrometer 实现（`BatchTaskMetricsImpl.java:21-41`），但 DSL/`BatchTaskRunner` 无自动注入点 |

---

## 二、Spring Batch 对比（源码级）

参考：`~/sources/spring-batch`，master @ `e7db361`，版本 **6.1.0-SNAPSHOT**。

### 2.1 核心抽象对照

| 概念 | Spring Batch | nop-batch | 结构性差异 |
|------|--------------|-----------|------------|
| 读 | `ItemReader<T>.read()` 返回单条 + null 结束（`spring-batch-infrastructure/.../ItemReader.java:41-52`；javadoc 明言实现有状态、非线程安全） | `IBatchLoader.load(batchSize, ctx)` 批量返回 List（`IBatchLoaderProvider.java:24-36`） | 批量优化、锁排序、关联数据预载在 Loader 层自然完成；SB 的 paging reader 必须自持游标状态 |
| 处理 | `ItemProcessor.process(I)→O` 严格 1:1 map | `IBatchProcessor.process(item, consumer, ctx)` flatMap | 一次处理 0..N 输出、即产即消 |
| 写 | `ItemWriter.write(Chunk<? extends T>)`（`ItemWriter.java:39-51`） | `IBatchConsumer.consume(Collection<R>, ctx)` | 语义接近；SB 的 Chunk 类型还携带 skips/errors/userData/end/busy 状态，Reader/Processor 不可见，徒增复杂度 |
| 编排 | Job/Step/Tasklet/Flow/Split/Decision 一体内建 | 刻意不做——编排交给通用 nop-task（`why-springbatch-is-bad.md:636-763`） | 职责分离：batch 只管 chunk，编排引擎可复用于非批处理场景 |
| 扩展 | 实现接口 + StepExecutionListener 等 18 种 listener 元数据（`core/listener/StepListenerMetaData.java:55-82`），包装后需手工 `<streams>` 补挂 | Provider.setup 返回的内层对象经 context 动态注册回调；包装链自动透传 | SB 的 Class-Based Listener 类比 Vue Class Component；nop-batch 是 Hooks/setup 模型（`why-springbatch-is-bad.md:410-634`） |
| bean 生命周期 | StepScope 代理 + `scopedTarget.` 前缀（`core/scope/StepScope.java:67-69`、`BatchScopeSupport.java:150-158`），历史上要求 `allow-bean-definition-overriding` | Provider 单例 + setup 闭包捕获 per-run 状态 | nop-batch 无动态 Scope，IoC 约束干净 |

### 2.2 事务与容错

- **事务边界**：SB 强制"每 chunk 一个事务"——`ChunkOrientedStep.doExecute` 中 `transactionTemplate.executeWithoutResult(...)` 包住单 chunk 的 read-process-write + saveState（`ChunkOrientedStep.java:446-476`）。nop-batch 的 `transactionScope` 四档（none/chunk/process/consume）允许 process 在事务外跑、仅 consume 开事务，配合 Nop ORM 乐观锁缩小事务窗口（`why-springbatch-is-bad.md:314-339`）。
- **skip/retry**：SB 由 `FaultTolerantChunkProcessor`（`step/item/FaultTolerantChunkProcessor.java:61`）+ `SkipPolicy`/`RetryPolicy` 实现，重试锚点在**单条 item 的 process/read**；nop-batch 重试锚点在 **chunk 级 consume 链**（可降级逐条），更适合"写入是 JDBC batch、错误在提交时暴露"的场景（`why-springbatch-is-bad.md:341-406`）。
- **状态与重启**：SB 元数据模型完整——BATCH_JOB_INSTANCE / JOB_EXECUTION / STEP_EXECUTION / EXECUTION_CONTEXT 四级表（`schema-*.sql:3-82`），ExecutionContext 经 Jackson 序列化存库（`Jackson2ExecutionContextStringSerializer.java`），重启按 Step 级 commit point 恢复。nop-batch 只有任务级一行状态（`nop_batch_task.completedIndex`）+ 可选记录级 history 表；**无 Step 级执行历史谱系**。这是 SB 的真正强项：审计与"同一 JobParameters 不允许重复实例"的治理模型。
- **记录级幂等**：SB **没有**内建记录幂等——重启后 reader 从上次 commit 的流位置继续，但已 commit 的写与未写之间依赖事务原子性，跨系统（写文件+写库）无两阶段。nop-batch 的 historyStore 把幂等做成一等公民，这是对银行"过账兜底"类场景的直接回应。

### 2.3 并行模型

- SB partition：`Partitioner` 切出静态 name→ExecutionContext 图（`Partitioner.java:35`），`PartitionHandler` 派发给 slave Step，master 聚合 `StepExecution`（`TaskExecutorPartitionHandler` 等）。分区数≈执行器数，负载不均时其他线程无法支援（`why-springbatch-is-bad.md:898`）。
- nop-batch：共享 Loader + 微队列（32768）动态借还，粒度远细于线程数；需要步骤级并行时用 nop-task `fork`/`fork-n`（`why-springbatch-is-bad.md:996-1015`）。
- 有趣演进：SB 6.0 已将 `ChunkOrientedTasklet` 标记 `@Deprecated`，由 `ChunkOrientedStep` 取代并引入 scan-mode checkpoint（`ChunkOrientedTasklet.java:36-41`、`ChunkOrientedStep.java:496-509`）——即 SB 自己也在把 chunk 逻辑从 Tasklet 通用机制里抽出来、做更细的提交点管理，方向上印证了"chunk 处理不该绑死在通用重复框架上"。

### 2.4 运维 API

SB 提供 `JobOperator`（`launch/JobOperator.java:44+`）：start/restart/stop/getRunningExecutions/获取状态等，是程序化运维的标准入口；无自带 UI。nop-batch 相对缺位（见 §七 P0-1）。

### 2.5 小结

nop-batch 在**编程模型**（批量读、flatMap、事务档位、Hooks 扩展）上对 SB 是代际差；SB 在**执行谱系治理**（四级元数据表、JobParameters 幂等实例、restart 语义标准化、JobOperator）与**生态成熟度**上仍领先。两者编排哲学根本不同：SB 把 job 流程塞进批处理框架，nop-batch 把流程留给通用编排——后者长期演进成本更低。

---

## 三、JSR-352 / JBeret 对比

参考：`~/sources/jsr352`（JBeret 3.2.x，Jakarta Batch 2.1 参考实现，WildFly 内置，2026 年仍活跃维护）。JSR-352 是 Java EE/Jakarta 的**批处理标准**，与 Spring Batch 同族思想（规范本身参考了 SB 的 chunk 模型）但是独立规范实现，全仓零 Spring 依赖。

### 3.1 核心抽象对照

| 概念 | JSR-352 / JBeret | nop-batch |
|------|------------------|-----------|
| 读 | `ItemReader.readItem()` **单条** + null 结束（`ChunkRunner.java:385` 逐条循环） | `IBatchLoader.load(batchSize)` 批量 |
| 处理 | `ItemProcessor.processItem` 1:1，null=过滤（`ChunkRunner.java:444-450`） | flatMap 0..N |
| 写 | `ItemWriter.writeItems(List)`（`ChunkRunner.java:563`） | `IBatchConsumer.consume(Collection)` |
| checkpoint | `checkpoint-policy=item/custom`；reader/writer 各自 `checkpointInfo()` 序列化为 BLOB 存 JobRepository，`open(Serializable)` 恢复（`ChunkRunner.java:574-586`、`jberet.ddl:47-48`） | 任务级 completedIndex + 可选 historyStore 记录幂等；文件源行号续传 |
| 声明式 | JSL XML（规范 XSD `jobXML_1_0.xsd`），artifact 用 `ref` 字符串 + properties | `*.batch.xml` XDSL，可 Delta 定制、可嵌套 xpl |
| 运维 | 规范 `JobOperator` 12 方法：start/restart/stop/abandon + 层级查询（`AbstractJobOperator.java`）；stop 在 checkpoint 边界优雅停 | 无对等 API（P0 建议） |
| 编排 | job/flow/split/decision/partition 内建 | 委托 nop-task |

### 3.2 关键差异

1. **与 SB 同样的 item-oriented 局限**：`readItem()` 单条读在规范层被钉死——nop-batch 的批量 Loader 优势对 JSR-352 族同样成立。
2. **checkpoint 契约更显式**：JBeret 要求 Reader/Writer 实现 `checkpointInfo()/open(checkpoint)` 双向序列化，续传是**用户代码责任**；nop-batch 的文件 loader 内建行号续传，用户零编码。但 JBeret 的 BLOB checkpoint 能存任意 reader 状态（含 DB 游标/自定义偏移），通用性更强——nop-batch 的 ORM loader 要对齐这一点，键集分页是自然方案（见 §八 P1-3）。
3. **JobOperator 是规范级运维面**：stop/restart/abandon/查询全齐，印证 nop-batch 补 kill/restart API 的必要性——这不是 SB 特色，而是批处理领域的行业底线能力。
4. **体积**：jberet-core 约 129 文件 / 2.5 万行，比 spring-batch-core 小但仍是"完整批处理平台"体量；nop-batch-core 更接近"引擎内核"。

### 3.3 小结

JBeret 证实：**单条 readItem + 1:1 processItem 是 Java 批处理标准层的共同结构**，nop-batch 的批量/flatMap 接口不是孤芳自赏，而是相对整个标准族的代际差异。JBeret 的 checkpointInfo 序列化契约与 JobOperator 运维面是值得抄作业的两点；JSL 编排（flow/split/decision）则不应抄——那是 nop-task 的职责。

---

## 四、snail-job 对比（批处理语义部分）

参考：`~/sources/snail-job`，2.0.0。定位是"分布式重试 + 任务调度平台"，批处理是 Job 任务类型之一（`JobTaskTypeEnum`：CLUSTER/BROADCAST/SHARDING/MAP/MAP_REDUCE，`enums/JobTaskTypeEnum.java:16-20`）。

| 维度 | snail-job | nop-batch |
|------|-----------|-----------|
| 编程模型 | 单方法执行器 `IJobExecutor.jobExecute(JobContext)` + 注解；无 reader-processor-writer 分层 | 声明式 Loader/Processor/Consumer 管道 |
| 分片 | 服务端预生成 N 个 JobTask，`index % 存活节点数` 轮转（`ShardingTaskGenerator.java:72-90`）；客户端拿 shardingIndex/shardingTotal 自行切数据 | 共享 Loader + partitionIndex 微队列；集群 partitionRange 下推 SQL 过滤 |
| MapReduce | MAP→REDUCE→MERGE_REDUCE 三阶段；**shuffle 中介是服务端 DB**（map 结果写 `sj_job_task.result_message`，reduce 输入由服务端 SQL 重组，`MapReduceTaskGenerator.java:109-163`）；官方自限 map 分片 ≤200/500（`MapHandler.java:15-19`） | 无内建 reduce；可用 nop-task fork/join 或 consumer 汇总组合，无 DB shuffle 瓶颈 |
| 断点续传 | **仅任务/阶段级**：RECOVERY 阻塞策略重跑批次内失败任务；无 chunk/item checkpoint | 文件源有行号级续传 + historyStore 记录级幂等；DB 源无行级续传 |
| 运维 | 强：批次 stop/retry API（`JobBatchController.java:43-54`）、树形任务查询、日志上报、5 渠道告警、OpenAPI、工作流 DAG | 弱：CRUD BizModel 为主，无 kill/retry 管理 API、无告警 |
| 重试治理 | 双栈：job 级重试 + 独立分布式重试子系统（`@Retryable`、死信表、场景配置） | chunk/retryPolicy 在引擎内；跨系统异步重试归 nop-retry |

**结论**：snail-job 是"调度+重试平台捎带批处理"，数据面能力（管道抽象、chunk 语义）远弱于 nop-batch；控制面（运维、告警、多租户）远强于 nop-batch。两者是互补而非替代——理想集成是 nop-batch 提供数据面、nop-job/nop-batch-web 补齐控制面（对照 2026-08-04 调度域分析的平台矩阵结论）。

---

## 五、PowerJob 对比（批处理语义部分）

参考：`~/sources/PowerJob`，v5.1.2。定位"分布式计算 + 任务调度框架"。

| 维度 | PowerJob | nop-batch |
|------|----------|-----------|
| 编程模型 | `BasicProcessor`/`MapProcessor`/`MapReduceProcessor`（`worker/core/processor/sdk/`）；子任务是任意 Java 对象，嵌套 map 支持任意级 MR，taskId 层次命名 `0.1.2` | 声明式数据管道；MR 需组合 nop-task |
| 并行 | TaskTracker（13s 状态检查 + 500ms 派发器）+ ProcessorTracker 线程池；宕机节点任务重派（`CommonTaskTracker.java:296-306`）；SWAP 机制：活跃任务 >10 万溢出到文件系统（`SwapTaskPersistenceService.java:31-64`） | 单机内 concurrency 线程 + 微队列；集群靠 nop-job partitionRange 分摊到多进程，无跨节点任务重派 |
| 状态粒度 | **子任务级** `task_info`（worker 内嵌 H2）；无 chunk/记录 checkpoint；容错=超时重派 + 重试计数 | chunk 级 checkpoint + 文件行号续传 + 记录幂等 |
| 编排 | 内建 DAG（Decision 节点 Groovy、wfContext 传递、嵌套工作流） | 委托 nop-task（能力更强：30+ step、跨重启断点续跑） |
| 运维 | 控制台（AntV G6 DAG 可视化）、钉钉/邮件/WebHook 告警、OmsLogger 在线日志、FatJar 容器分发（Git+Maven 构建） | 见 §三 同类缺口 |

**结论**：PowerJob 解决"把一个任务分发到集群算力"，子任务内部的批处理语义完全交给用户代码；nop-batch 解决"把一个数据流安全地过一遍管道"。PowerJob 的 SWAP/宕机重派是分布式派发可靠性的深度工程，nop-batch 若未来做多机分片执行可借鉴其"派发超时→重派"与"活跃集溢出文件"思路，但当前单进程 chunk 引擎不需要这层。

---

## 六、横向对比矩阵

| 能力 | nop-batch | Spring Batch 6 | JSR-352/JBeret | snail-job 2.0 | PowerJob 5.1.2 |
|------|-----------|----------------|----------------|---------------|----------------|
| 批量读接口 | ✅ `load(batchSize)` | ❌ 单条 `read()` | ❌ 单条 `readItem()` | N/A（无管道） | N/A（任意对象子任务） |
| flatMap 处理 | ✅ | ❌ 1:1 map | ❌ 1:1 processItem | N/A | map 可产子任务（另一层抽象） |
| 事务范围可配 | ✅ 4 档 | ❌ 固定 chunk 事务 | ❌ 固定 chunk 事务 | 任务级 | 子任务级 |
| chunk 断点续传 | ✅（文件源）；⚠️ DB 源无 | ✅（流位置 checkpoint） | ✅（checkpointInfo BLOB，用户实现） | ❌ | ❌ |
| 记录级幂等 | ✅ historyStore | ❌ | ❌ | ⚠️ 幂等 ID 在重试子系统 | ❌ |
| 步骤级编排 | ✅ 委托 nop-task | ✅ 内建 Job/Step | ✅ 内建 job/flow/split | ✅ 工作流 DAG | ✅ 工作流 DAG |
| 运行时 kill API | ❌ | ✅ JobOperator.stop | ✅ JobOperator.stop | ✅ batch stop | ✅ 实例停止 |
| 重启/重跑 API | ⚠️ 重新 execute（stateStore 恢复） | ✅ restart | ✅ JobOperator.restart | ✅ batch retry | ✅ instance retry |
| 告警 | ❌（靠平台） | ❌ | ❌ | ✅ 5 渠道 | ✅ 3 渠道 |
| Micrometer metrics | ⚠️ 实现有，无自动接线 | ✅ batch observability | ⚠️ StepMetrics | ❌ | ❌ |
| 声明式 DSL | ✅ XDSL（可 Delta 定制） | ⚠️ Java config/XML bean 组装 | ✅ JSL 规范 XSD（继承为 JBeret 扩展） | ⚠️ 控制台表单 | ⚠️ 控制台表单 |
| 审计谱系 | ⚠️ 单任务行 + 可选 record 表 | ✅ 4 级元数据表 | ✅ instance/execution/step/partition | ✅ batch+task 两级 | ✅ instance+task |
| 集群分片执行 | ✅ partitionRange（无任务重派） | ✅ partition（无重派） | ✅ partition（mapper/collector） | ✅ 预分配+重试换节点 | ✅ 派发超时重派 |
| 多语言客户端 | ❌ JVM | ❌ JVM | ❌ JVM | ✅ Java/Python/Go | ⚠️ Shell/Python 脚本任务 |

### 未纳入对照的项目与理由

| 项目 | 本地是否有 | 不纳入主对比的原因 |
|------|-----------|-------------------|
| Apache Flink / Spark / SeaTunnel | ✅ `~/sources/` 有 | 数据并行大数据引擎（分区算子、shuffle、容错快照），解决的问题域是分布式计算而非"企业 chunk ETL"；对比维度正交，混入会模糊结论。若未来 nop-batch 要做多机执行可另开专文 |
| Quartz / XXL-JOB / ElasticJob | 部分 | 调度器/分片作业框架，批处理语义弱；调度域对比已有 `2026-08-04-nop-job-vs-powerjob-vs-snail-job-deep-analysis.md` |
| Apache Beam | 有目录 | 统一批流 SDK+Runner 模型，依赖特定 runner 生态，与嵌入式 Java 引擎不同类 |
| Kettle/PDI、Talend、Airflow、dbt | ✅ 已下载 `~/sources/` | ETL/编排工具链（UI 驱动、Python/图形编排），非嵌入式 Java 批处理引擎；**平台级对比已另开专文** `ai-dev/analysis/2026-10/2026-10-18-nop-platform-vs-etl-toolchains.md` |
| Camel/Mule | ❌ | EIP 集成总线，路由为主，chunk 语义非核心 |

## 七、nop-batch 的结构性优势（护城河）

1. **接口签名即架构**：`load→List` / `process→flatMap` / `consume→Collection` + 共享 chunkCtx，函数类型直接表达数据流组织（`why-springbatch-is-bad.md:312`）；Spring Batch 的 Item-oriented 遗迹（单条 read、状态化 reader、Chunk 大对象）在源码中仍然成立（`ItemReader.java:24-32` javadoc 自认 stateful/non-thread-safe）。
2. **事务档位 + ORM 乐观锁协同**：process 出事务、consume 才开事务、flush 时乐观锁兜底，是只有"自带 ORM 的平台框架"才能做出的优化。
3. **Provider/Hooks vs StepScope**：单例 Provider + setup 闭包，无 bean 重定义开关、无 scoped proxy 魔法（`BatchScopeSupport.java:150-158` 仍在 SB 源码里解决历史包袱）。
4. **DSL 森林**：最小 batch 模型 + 通用 Record/Task/ORM 无缝嵌套，编排零重复建设；SB 的 FlatFileItemReader 式专用配置无法在框架外复用（`why-springbatch-is-bad.md:1051-1093`）。
5. **at-least-once + 记录幂等显式化**：把分布式批处理的真实语义写进文档与引擎（historyStore 同事务），而不是让用户从 SB 的 commit-interval 文档里自行推导。
6. **体积**：核心引擎几百行级状态机 + 清晰 SPI，定制与审计成本远低于 spring-batch-core。

## 八、改进建议（按优先级）

### P0 — 运维闭环（集成 nop-job 控制面，不自建第二套）

> 2026-10-18 用户追问"批任务运行控制不能集成 nop-job 吗"后复核：**能，且 nop-job 已有全套控制面**——`cancelFire`（级联 task 置 CANCELED + best-effort 通知）、`rerunFire`（RECOVERY fire + sourceFireId）、`pauseSchedule`/`resumeSchedule`、`IJobInvoker.cancelAsync`（`IJobInvoker.java:30`）。batch 已被 job 启动：`*.job.yaml → LocalJobConfigLoader → BeanMethodJobInvoker.invokeAsync → BatchTaskRunner.executeAsync`（`sys-event-batch-consumer.job.yaml:7-12`）。反向取消钩子也是现成的：`BeanMethodJobInvoker.cancelAsync` 按方法名反射调用（默认 `cancel`，`BeanMethodJobInvoker.java:52,89`）。因此原"在 IBatchTaskManager 加 stop"方向**已修订为接线而非新建**。

1. **batch 接入 job 取消链路**（原"运行时 kill API"）：补三处断点——① `BatchTaskRunner` 增加 `cancel(Map params)` 方法（`BeanMethodJobInvoker` 反射调它；当前缺失会被 `:92-95` 吞掉并**静默假成功**返回 TRUE）；② **运行中 batch context 注册表**（`executeAsync` 创建的 `IBatchTaskContext` 即用即弃——`BatchTaskRunner.java:38-50`，现状仅 ThreadLocal `BatchTaskGlobals.java:9-21`），key 需可从 jobParams 定位（taskPath/taskKey 或 jobTaskId）；③ `NopBatchTask` 增加 `jobTaskId` 关联列（**ORM 变更，plan-first Protected Area**）+ 双状态 SSOT 裁定（`nop_job_task.taskStatus` vs `nop_batch_task.taskStatus` 谁是批任务运行状态权威；batch KILLED 永久禁启 `DaoBatchStateStore.java:108-113` 与 job `isRecoverable` 含 CANCELED 的语义冲突需消解）。
2. **resume/rerun 复用 job `rerunFire`**（原"失败任务重跑入口"）：job 侧已有 `rerunFire`（仅终态可重跑，新建 RECOVERY fire 带 `sourceFireId` 追溯，`NopJobFireBizModel.java:154-173`）+ UI/权限按钮（`NopJobFire.view.xml:69-80`）。batch 侧要补的是语义映射：job rerun → 新 fire → 新 batch 运行时，stateStore 是"同 taskName/taskKey 原地重启"还是"清 completedIndex 重跑"需明确；`allowStartIfComplete`（`DaoBatchStateStore.java:131-137`）已有开关。管理端入口直接复用 `nop-job-web`，无需 `nop-batch-web` 重建。
3. **补充**：`IJobExecutionContext.getCancelToken()` 默认 null（`IJobExecutionContext.java:22-24`），标准路径无人注入——若要"标记后在下个 chunk 边界停"的软取消（而非 cancelAsync 硬中断），需 scanner/runner 侧注入 token 或 runner 内轮询。长查询场景另需 `load` 前检查 cancel（沿用原建议）。

### P1 — 数据面能力补齐

3. ~~ORM/JDBC loader 键集分页断点续传~~ **已降级/撤回**（2026-10-18 用户裁定）：DB 源批处理的主流模式是**状态驱动**——处理链路中修改源实体状态（`status: NEW→DONE` 等），重跑时同一 filter 自动排除已处理行，天然等价于 checkpoint，无需行级续传；文件源因为文件本身不可变才需要行号 completedIndex。仍保留行级续传价值的**窄场景**仅：① 源表只读（export、纯分析扫描）且单次任务量极大、中断重跑全表扫描成本不可接受；② 状态字段不在 consume 事务内更新（先读后批、后置回写）。这两类场景用户可通过 `orderBy + 自定义 provider loader` 自行实现键集逻辑，**不建议引擎内建**。2026-08 审查 12e（filter 下行号错位）是文件 loader 的问题，与 DB 源无关。
4. **metrics 自动接线**：`BatchTaskRunner`/DSL builder 检测 IoC 中的 `MeterRegistry`，默认挂 `BatchTaskMetricsImpl`（实现已存在，`BatchTaskMetricsImpl.java:21`），标签 job.name/task.name 就绪——当前需要用户手工 `context.setMetrics`，观测性落不了地。
5. **reduce/aggregate 步骤**：DSL 增加 `aggregator`（任务结束时对全部 chunk 输出或 context 变量做汇总，可委托 xpl），补上 snail-job/PowerJob MapReduce 的"汇"半边；保持"数据面轻量、跨节点 shuffle 不做"的边界（DB shuffle 反例见 snail-job §四）。

### P2 — 清理与契约固化

6. **遗留物清理**：`NopBatchTaskState` 三件套（手写 entity + `_gen` 生成物 + BizModel）已从源模型移除但代码残留——`nop-batch.orm.xml` 仅 4 实体、无 xmeta、无 bean 注册，属纯死代码；已有独立审计裁定（`ai-dev/audits/2026-09/2026-09-30-1726-deep-audit-nop-entropy-full/07-job-task-batch-retry-tcc.md` G7-07-01，建议删除三件套 + `_templates` 残留，防止下次 codegen 清理时编译断裂）。`BatchDslConstants.FILE_TYPE_BATCH_TASK="batch-task.xml"` 与实际 `batch.xml` 注册的双命名并存问题统一。
7. **语义文档补强**：分区顺序语义**已文档化**（`docs-for-ai/02-core-guides/batch-dsl.md:144-149`：同分区源序、跨分区无序、fetch 入队已串行化）；剩余缺口是 **loader 线程安全契约**在模块文档中的显式强调——`IBatchLoader.load` javadoc 已声明"必须线程安全"（`IBatchLoaderProvider.java:26`），但自定义 DSL `provider` loader 的作者容易忽略；建议在 `batch-dsl.md` 的 provider 一节加警示框 + 指向 concurrency 模型说明。集群 partitionRange 无任务重派（宕机语义=整任务失败重启）也应写明。
8. **测试补强**（延续 2026-08 清单）：kill 后状态断言、DB loader 续传、metrics 自动挂载、多 fetch 线程顺序性回归。

### 不建议方向

- ❌ 引入 Spring Batch 式 Job/Step/Decision 内建编排——与 nop-task 重复，违背 DSL 森林分工（`why-springbatch-is-bad.md:722`）。
- ❌ 引入 StepScope/动态 bean 重定义——Provider/setup 已覆盖需求。
- ❌ 做跨节点 MR shuffle（PowerJob/snail-job 模式）——吞吐上限与复杂度不匹配定位；需要集群加速时用 nop-job partitionRange 横向扩展同构任务即可。
- ❌ 兼容 Spring Batch API——抽象模型不兼容（单条 vs 批量、map vs flatMap），兼容层只会固化坏抽象。

---

## Open Questions

- [ ] ~~kill API 的实现位置~~ **已裁定方向**（2026-10-18）：接入 nop-job 控制面（cancelFire → BeanMethodJobInvoker.cancelAsync → BatchTaskRunner.cancel），不自建 IBatchTaskManager.stop。遗留细节：运行中 context 注册表的 key 选型（taskPath/taskKey vs jobTaskId）；本地 `.job.yaml` 路径（LocalJobScheduler，内存态、重启丢失）与分布式 `NopJobSchedule` DB 路径如何统一（`beanMethod` executorKind 不在 dict `test/rpc/rpcPoll` 中）。
- [ ] 双状态 SSOT：`nop_job_task.taskStatus` 与 `nop_batch_task.taskStatus` 谁是批任务运行状态权威？是否需要对账器（对照 job 侧 `JobScheduleCounterReconciler`）？
- [ ] DB loader 键集续传：是否强制要求 query 带稳定 orderBy？无主键排序键时退化策略？（已随用户裁定撤回引擎内建，此处仅遗留窄场景）
- [ ] resume/rerun 双语义是否值得在 DSL 层加 `restartPolicy` 配置，还是保持 API 参数？
- [ ] `NopBatchTaskState` 是否有未提交的使用场景（如 ETL 变量存储）需要保留？

## References

### 仓内

- `docs/theory/why-springbatch-is-bad.md` — NopBatch 设计动机与 Spring Batch 批判（官方理论文）
- `docs-for-ai/03-modules/nop-batch.md` — 模块权威文档
- `docs-for-ai/02-core-guides/batch-dsl.md` — DSL 指南（fail-fast/顺序语义已同步）
- `ai-dev/analysis/2026-08/2026-08-16-nop-batch-design-and-concurrency-review.md` — 缺陷审查与修复记录（本文 §1.8 核查基线）
- `ai-dev/analysis/2026-08/2026-08-04-nop-job-vs-powerjob-vs-snail-job-deep-analysis.md` — 调度域对比（本文不重复）
- 源码：`nop-batch/nop-batch-core/.../IBatchLoaderProvider.java`、`BatchTask.java`、`BatchTaskBuilder.java`、`IBatchStateStore.java`、`IBatchTaskMetrics.java`、`metrics/BatchTaskMetricsImpl.java`、`loader/ResourceRecordLoaderProvider.java`；`nop-batch-dao/.../DaoBatchStateStore.java`、`history/DaoBatchRecordHistoryStore.java`；`nop-batch/model/nop-batch.orm.xml`

### 外部参考（本地克隆）

- Spring Batch：`~/sources/spring-batch`（master 6.1.0-SNAPSHOT @ e7db361）
  - `spring-batch-infrastructure/.../item/{ItemReader,ItemWriter,ItemProcessor}.java`
  - `spring-batch-core/.../step/item/{ChunkOrientedStep,ChunkOrientedTasklet,SimpleChunkProcessor,FaultTolerantChunkProcessor}.java`
  - `spring-batch-core/.../launch/JobOperator.java`、`repository/JobRepository.java`
  - `spring-batch-core/.../listener/StepListenerMetaData.java`、`scope/{StepScope,BatchScopeSupport}.java`
  - `spring-batch-core/.../partition/{Partitioner,PartitionHandler}.java`、`schema-*.sql`
- JBeret / JSR-352：`~/sources/jsr352`（3.2.x，Jakarta Batch 2.1.1）
  - `jberet-core/.../runtime/runner/ChunkRunner.java`（readItem/writeItems/checkpoint 状态机）
  - `jberet-core/.../operations/{AbstractJobOperator,JobOperatorImpl}.java`
  - `jberet-core/src/main/resources/jobXML_1_0.xsd`、`sql/jberet.ddl`（READERCHECKPOINTINFO BLOB）
- snail-job：`~/sources/snail-job`（2.0.0）— `JobTaskTypeEnum.java`、`ShardingTaskGenerator.java`、`MapReduceTaskGenerator.java`、`MapHandler.java`、`JobBatchController.java`
- PowerJob：`~/sources/PowerJob`（v5.1.2）— `worker/core/processor/sdk/*`、`heavy/{HeavyTaskTracker,CommonTaskTracker}.java`、`persistence/SwapTaskPersistenceService.java`
