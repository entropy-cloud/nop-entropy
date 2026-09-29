# 快速上手

> 本页源文件基准（相对于 `deepwiki/nop-batch/`，链接先退到 wiki 根再按仓库相对路径解析，逐条验证存在）：
>
> - [../nop-batch/pom.xml](../../nop-batch/pom.xml)
> - [../nop-batch/nop-batch-core/pom.xml](../../nop-batch/nop-batch-core/pom.xml)
> - [../nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java](../../nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java)
> - [../nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/IBatchTask.java](../../nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/IBatchTask.java)
> - [../nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/IBatchLoaderProvider.java](../../nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/IBatchLoaderProvider.java)
> - [../nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/IBatchProcessorProvider.java](../../nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/IBatchProcessorProvider.java)
> - [../nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/IBatchConsumerProvider.java](../../nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/IBatchConsumerProvider.java)
> - [../nop-batch/nop-batch-core/src/test/java/io/nop/batch/core/TestBatchTask.java](../../nop-batch/nop-batch-core/src/test/java/io/nop/batch/core/TestBatchTask.java)
> - [../nop-batch/nop-batch-dsl/src/test/resources/_vfs/test/batch/test-runner-simple.batch.xml](../../nop-batch/nop-batch-dsl/src/test/resources/_vfs/test/batch/test-runner-simple.batch.xml)
> - [../nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef](../../nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef)

本页覆盖 nop-batch 的构建入口、测试资产清单和两种最小批任务写法（编程式 + DSL）。引擎装配与执行语义的展开见[总览](./overview.md)与[阅读指南](./reading-guide.md)；本文引用的责任链细节在[核心引擎](./modules/batch-core.md)、DSL 生成链在[DSL 模型](./modules/batch-dsl.md)。

## 1. 构建：模块坐标与依赖

聚合模块坐标为 `io.github.entropy-cloud:nop-batch:2.0.0-SNAPSHOT`（parent 为 `nop-entropy:2.0.0-SNAPSHOT`），packaging 为 pom，下挂 15 个子模块（`nop-batch/pom.xml:7-15,19-35`）：nop-batch-core、nop-batch-dao、nop-batch-gen、nop-batch-orm、nop-batch-codegen、nop-batch-api、nop-batch-service、nop-batch-web、nop-batch-app、nop-batch-jdbc、nop-batch-meta、nop-batch-dsl、nop-batch-exp、nop-batch-biz、nop-batch-sys。

引擎本体是 nop-batch-core，坐标 `io.github.entropy-cloud:nop-batch-core`（版本继承 parent），依赖仅两项（`nop-batch/nop-batch-core/pom.xml:12-25`）：

| 依赖 | scope | 用途 |
|---|---|---|
| `nop-xlang` | compile | XPL 表达式与平台基础类型（BatchTaskBuilder 直接 import `IEvalFunction`、`IRetryPolicy` 等，`BatchTaskBuilder.java:36-38`） |
| `junit-jupiter` | test | 测试 |

只依赖 nop-xlang 意味着 core 可以脱离 IoC 容器单独编程式使用——第 3.1 节的示例不需要任何 beans.xml。DSL 能力（batch.xdef 解析、bean 引用、batch.xlib）在 nop-batch-dsl，运行它需要容器环境。

常用构建命令：

```bash
./mvnw test -pl nop-batch/nop-batch-core          # 只跑 core 测试（上游依赖需已 install 到本地仓库）
./mvnw test -pl nop-batch/nop-batch-core -am      # 连带构建上游依赖（首次或上游有改动时）
./mvnw test -pl nop-batch/nop-batch-dsl -am       # 跑 DSL 模块测试（含 AutoTest 容器用例）
./mvnw install -pl nop-batch -am                  # 安装整个 nop-batch 聚合模块
```

## 2. 测试：类清单与数量

以下清单核对自当前工作区（快照日期 2026-09-26），计数方式为对 `src/test/java` 逐文件 grep `@Test`，未运行测试。全 nop-batch 合计：38 个含 @Test 的测试类、74 个 @Test。

### nop-batch-core：15 个测试类，37 个 @Test

| 包 | 测试类（@Test 数） |
|---|---|
| core 根包（15 类，37） | TestBatchBugVerification(8)、TestHistoryTxnPromotion(4)、TestListBatchLoader(4)、TestRateLimitConsumer(3)、TestResourceRecordConsumerRecovery(3)、TestRetryConsumerExceptions(3)、TestAsyncFetchLoaderFailure(2)、TestPartitionDispatchLoader(2)、TestWithHistoryBatchConsumer(2)、TestBatchTask(1)、TestBatchTaskDispatchLoadRetry(1)、TestPartitionDispatchQueueFinish(1)、TestRecordLoader(1)、TestResourceRecordConsumerConcurrentWrite(1)、TestResourceRecordLoaderCallbackLeak(1) |

core 测试全部是纯 JUnit：不依赖 IoC 容器与数据库，用内存 mock 实现三个 Provider（见 3.1 节），可直接作为最小用法的参考实现。测试目录中另有 2 个 0 @Test 的调试夹具，不计入测试类：`DebugResourceLocator`、`DebugResourceRecordIO`（实现 `IResourceLocator`/`IResourceRecordIO` 的调试桩，`DebugResourceLocator.java:7`、`DebugResourceRecordIO.java:10`）。

### 其他子模块

| 模块 | 测试类（@Test 数） | 测试基座 | 说明 |
|---|---|---|---|
| nop-batch-dsl | 7 类，13 @Test | 混合 | TestBatchTaskForTagValidation(3)、TestBatchTaskDsl(2)、TestBatchTaskRunner(2)、TestBatchLoaderHelper(1)、TestFileBatchSupportLocator(1)、TestLimitHash(1)、TestBatchTaskRunnerPartition(3)；其中 TestBatchTaskDsl/TestBatchTaskRunner 继承 `JunitAutoTestCase` 且 `@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)`，需要 AutoTest 容器与本地数据库（`TestBatchTaskDsl.java:30-31`、`TestBatchTaskRunner.java:14-15`）。`BatchTaskModelGen` 是 0 @Test 的代码生成入口（main 方法），非测试 |
| nop-batch-exp | 4 类，7 @Test | 混合 | TestDbToolConfigMasking(3)、TestEtlTaskStateStoreAtomicWrite(2)、TestExportDbTool(1)、TestImportDbTool(1)；后两者需要容器。注意 TestImportDbTool 的包名是 `io.nop.dbtoo.exp`（目录即如此，`nop-batch-exp/src/test/java/io/nop/dbtoo/exp/`） |
| nop-batch-sys | 2 类，5 @Test | Nop 基座 | TestSysEventBatchPartitionE2E(3)、TestSysEventBatchTrigger(2)，继承 `JunitBaseTestCase`（`TestSysEventBatchTrigger.java:31`） |
| nop-batch-biz | 3 类，3 @Test | 纯 JUnit | TestBizExportTaskBuilderFormat(1)、TestBizExportTaskBuilderLocator(1)、TestDefaultBizEntityImporter(1) |
| nop-batch-jdbc | 3 类，3 @Test | 纯 JUnit | TestJdbcBatchConsumerProviderSharedField(1)、TestJdbcBatchLoaderClosedState(1)、TestJdbcPageBatchLoaderRetry(1) |
| nop-batch-dao | 1 类，3 @Test | 纯 JUnit | TestDaoBatchRecordHistoryStore(3) |
| nop-batch-gen | 2 类，2 @Test | 纯 JUnit | TestBatchGenModel(1)、TestBatchGenModelMergeMap(1) |
| nop-batch-web | 1 类，1 @Test | Nop 基座 | NopBatchWebPagesTest(1)；`NopBatchWebCodeGen` 是 0 @Test 的代码生成入口 |
| nop-batch-codegen | 0 类 | — | 仅 `NopBatchCodeGen` 代码生成入口（0 @Test） |
| nop-batch-api / app / meta / orm / service | 无 src/test 目录 | — | 纯模型/契约/配置模块 |

单类运行示例：

```bash
./mvnw test -pl nop-batch/nop-batch-core -Dtest=TestBatchTask          # 责任链与 retry 语义的最小闭环
./mvnw test -pl nop-batch/nop-batch-dsl -Dtest=TestBatchTaskRunner -am # DSL 加载与执行（需本地数据库）
```

## 3. 最小用法示例

### 3.1 编程式构建：BatchTaskBuilder 链式 API

`BatchTaskBuilder.create()` 静态工厂入口（`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java:122-124`），全部配置项都是返回自身的链式 setter（`@PropertySetter` 标注，`:126-323`）；`loader`/`consumer`/`processor` 三个核心槽位各带 `Guard.checkState` 防重复设置（`:280,287,295,320`）。两个已核实的装配语义（与[核心引擎](./modules/batch-core.md)一致）：

- **装配延迟到 setup 时刻**：`buildTask()` 不装配管线，只 new 一个 `BatchTask`，把 `this::buildLoader`、`this::buildChunkProcessor` 以方法引用传入构造器（`BatchTaskBuilder.java:325-330`）；真正的 Provider 装配发生在任务执行、`IBatchTask.executeAsync(IBatchTaskContext)` 触发 setup 之后（接口签名 `IBatchTask.java:18-23`）。
- **消费者责任链 12 个固定包装位**：`buildChunkProcessor` 从内到外按固定顺序叠加（`BatchTaskBuilder.java:376-456`）——① `EmptyBatchConsumer` 缺省（:377-379）→ ② `InvokerBatchConsumer` consume 级事务（:391-394）→ ③ `BatchProcessorConsumer` 接入 processor（:396-403）→ ④ `WithHistoryBatchConsumer` 去重历史（:405-407）→ ⑤ `InvokerBatchConsumer` process 级事务（:409-412）→ ⑥ `AddCompletedBatchConsumer` 标记完成（:414-416）→ ⑦ `RateLimitConsumer` 限速（:419-420）→ ⑧ `SingleModeBatchConsumer` 逐条消费（:423-424）→ ⑨ `RetryOneByOne/RetryAllBatchConsumer`（:427-434）→ ⑩ `SkipBatchConsumer`（:437-439）；外层再包 ⑪ chunk 级事务（:444-447）与 ⑫ singleSession（:451-453）两个 `InvokerBatchChunkProcessor`。写代码时只需记住：retry 位于事务之外、skip 检查在 retry 之后（`:426,436-437` 注释）；配置了 `historyStore` + 事务时 scope 会被自动从 consume 提升为 process 以保证历史原子性（`:381-389`）。

三件套的最小实现直接对应三个内嵌接口（签名出处：`IBatchLoaderProvider.java:9-31`、`IBatchProcessorProvider.java:24-32`、`IBatchConsumerProvider.java:7-28`），下面的骨架逐行对齐 core 测试 `TestBatchTask` 的 mock 写法（`nop-batch/nop-batch-core/src/test/java/io/nop/batch/core/TestBatchTask.java:33-118`）：

```java
public class DemoBatch {

    // 读：每次 load 返回一批；返回空列表表示数据读完
    static class ListLoader implements IBatchLoaderProvider.IBatchLoader<String>, IBatchLoaderProvider<String> {
        int count = 0;

        @Override public IBatchLoader<String> setup(IBatchTaskContext context) { return this; }

        @Override public List<String> load(int batchSize, IBatchChunkContext context) {
            List<String> ret = new ArrayList<>();
            for (int i = 0; i < batchSize && count < 100; i++, count++)
                ret.add("item-" + count);
            return ret;
        }
    }

    // 加工：逐条调用；不调用 consumer.accept 即丢弃该条
    static class UpperProcessor implements IBatchProcessorProvider.IBatchProcessor<String, String>,
            IBatchProcessorProvider<String, String> {
        @Override public IBatchProcessor<String, String> setup(IBatchTaskContext taskContext) { return this; }

        @Override public void process(String item, Consumer<String> consumer, IBatchChunkContext context) {
            consumer.accept(item.toUpperCase());
        }
    }

    // 写：按 chunk 整批到达
    static class LogConsumer implements IBatchConsumerProvider.IBatchConsumer<String>, IBatchConsumerProvider<String> {
        @Override public IBatchConsumer<String> setup(IBatchTaskContext context) { return this; }

        @Override public void consume(Collection<String> items, IBatchChunkContext context) {
            System.out.println("chunk: " + items);
        }
    }

    public static void main(String[] args) {
        BatchTaskBuilder<String, String> builder = new BatchTaskBuilder<>();
        builder.taskName("demo")
               .loader(new ListLoader())
               .processor(new UpperProcessor())
               .consumer(new LogConsumer())
               .batchSize(100)                                   // 缺省即 100（BatchTaskBuilder.java:62）
               .concurrency(1).executor(GlobalExecutors.cachedThreadPool()); // 并行必须同时给 executor

        IBatchTask task = builder.buildTask();

        BatchTaskContextImpl context = new BatchTaskContextImpl();
        context.setTaskName("demo");
        CompletableFuture<Void> future = new CompletableFuture<>();
        context.onAfterComplete(err -> FutureHelper.complete(future, null, err)); // 完成信号

        task.executeAsync(context);
        FutureHelper.syncGet(future);
    }
}
```

要点：`concurrency` 缺省 0，且"设置了 concurrency 的情况下，还需要设置 executor 才会真正并行执行，否则会使用 SyncExecutor 在当前线程上串行执行"（`BatchTaskBuilder.java:64-67`、`batch.xdef:2`）；需要失败重试/跳过时追加 `.retryPolicy(RetryPolicy.retryNTimes(3)).retryOneByOne(true).skipPolicy(new BatchSkipPolicy().maxSkipCount(100))`（同款用法 `TestBatchTask.java:47-48`）。

### 3.2 DSL 方式：batch.xdef 任务 XML

DSL 根节点是 `<batch>`，由 `/nop/schema/task/batch.xdef` 约束（该文件物理位置在 nop-kernel 的 nop-xdefs 模块，生成模型类落在 `io.nop.batch.dsl.model` 包，`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef:9-14`）。必填/缺省属性：`batchSize="!int"` 必填、`startLimit="!int=0"`、`concurrency="!int=0"`、`useBatchRequestGenerator="!boolean=false"`，其余 taskName/transactionScope/rateLimit/jitterRatio/saveState 等可选（`batch.xdef:9-15`）。`<loader>` 是唯一强制子节点（`xdef:mandatory="true"`，`batch.xdef:76-77`）。

仓库内真实的最小样例是 dsl 测试资源 `test-runner-simple.batch.xml`（全文 16 行，`nop-batch/nop-batch-dsl/src/test/resources/_vfs/test/batch/test-runner-simple.batch.xml:1-16`）：

```xml
<batch taskName="test.simple" batchSize="10"
       x:schema="/nop/schema/task/batch.xdef" xmlns:x="/nop/schema/xdsl.xdef">

    <loader>
        <provider>
            return [1,2,3,4,5];
        </provider>
    </loader>

    <consumer name="all">
        <source>
            logInfo("items={}",items);
        </source>
    </consumer>

</batch>
```

可替换的节点面（均在 batch.xdef 中定义）：loader 侧 `provider`/`source`（内存产生数据，:143-145）、`file-reader`（:97-111）、`excel-reader`（:113-120）、`orm-reader`（:125-129）、`jdbc-reader`（:131-136）、`generator`（:138）、`dispatcher` 分区分发（:80-85）；processor 的 `source` 签名为 `xpl-fn:(item,consume,batchChunkCtx)=>void`（:154）；consumer 支持 `filter`、`file-writer`/`excel-writer`/`orm-writer`/`jdbc-writer` 四种内建写出口与 `source`/`transformer` 自定义（:167-213），`name` 唯一、可配 `forTag` 配合 `<tagger>` 做按记录路由（:160-166）；任务级与节点级监听器 `onTaskBegin`…`onConsumeEnd` 共 12 个回调（:54-71）。

两种运行入口（仓库测试即用例）：

```java
// 入口一：IBatchTaskRunner——按路径同步执行，可带参数（IBatchTaskRunner.java:11-25）
// 测试样例：nop-batch/nop-batch-dsl/src/test/java/io/nop/batch/dsl/runner/TestBatchTaskRunner.java:20-32
batchTaskRunner.execute("/test/batch/test-runner-simple.batch.xml");
batchTaskRunner.execute("/test/batch/test-runner-simple.batch.xml", params);

// 入口二：IBatchTaskManager——加载为 IBatchTask 后自行控制执行
// 测试样例：nop-batch/nop-batch-dsl/src/test/java/io/nop/batch/dsl/TestBatchTaskDsl.java:64-69
IBatchTask task = batchTaskManager.loadBatchTaskFromPath(path, BeanContainer.instance()); // IBatchTaskManager.java:27
task.execute(new BatchTaskContextImpl());
```

`processor bean="..."`/`consumer bean="..."` 等 bean 引用经第二个入口的 `IBeanProvider` 参数解析，这也是 `loadBatchTaskFromPath` 必须传容器的原因。更复杂的复合形态是把 `<batch:task>` 嵌进 task.xdef 任务流的步骤里，配合 `record:file-model` 定长文件模型与多个命名 consumer（带 `filter` 分别写不同输出文件），真实样例见 `nop-batch/nop-batch-dsl/src/test/resources/_vfs/test/batch/test-batch.task.xml:1-56`；xlib 展开链路见[DSL 模型](./modules/batch-dsl.md)。

## Sources

- [nop-batch/pom.xml:7-35](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-batch/pom.xml#L7-L35)
- [nop-batch/nop-batch-core/pom.xml:12-25](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-batch/nop-batch-core/pom.xml#L12-L25)
- [nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java:48-130](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java#L48-L130)
- [nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java:122-124](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java#L122-L124)
- [nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java:279-298](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java#L279-L298)
- [nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java:318-330](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java#L318-L330)
- [nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java:376-456](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java#L376-L456)
- [nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/IBatchTask.java:18-23](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/IBatchTask.java#L18-L23)
- [nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/IBatchLoaderProvider.java:9-31](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/IBatchLoaderProvider.java#L9-L31)
- [nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/IBatchProcessorProvider.java:7-32](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/IBatchProcessorProvider.java#L7-L32)
- [nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/IBatchConsumerProvider.java:7-28](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/IBatchConsumerProvider.java#L7-L28)
- [nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/manager/IBatchTaskManager.java:11-27](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/manager/IBatchTaskManager.java#L11-L27)
- [nop-batch/nop-batch-core/src/test/java/io/nop/batch/core/TestBatchTask.java:29-118](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-batch/nop-batch-core/src/test/java/io/nop/batch/core/TestBatchTask.java#L29-L118)
- [nop-batch/nop-batch-core/src/test/java/io/nop/batch/core/DebugResourceLocator.java:7](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-batch/nop-batch-core/src/test/java/io/nop/batch/core/DebugResourceLocator.java#L7)
- [nop-batch/nop-batch-core/src/test/java/io/nop/batch/core/DebugResourceRecordIO.java:10](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-batch/nop-batch-core/src/test/java/io/nop/batch/core/DebugResourceRecordIO.java#L10)
- [nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef:2-19](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef#L2-L19)
- [nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef:54-71](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef#L54-L71)
- [nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef:76-148](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef#L76-L148)
- [nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef:151-213](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef#L151-L213)
- [nop-batch/nop-batch-dsl/src/test/resources/_vfs/test/batch/test-runner-simple.batch.xml:1-16](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-batch/nop-batch-dsl/src/test/resources/_vfs/test/batch/test-runner-simple.batch.xml#L1-L16)
- [nop-batch/nop-batch-dsl/src/test/resources/_vfs/test/batch/test-batch.task.xml:1-56](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-batch/nop-batch-dsl/src/test/resources/_vfs/test/batch/test-batch.task.xml#L1-L56)
- [nop-batch/nop-batch-dsl/src/main/java/io/nop/batch/dsl/runner/IBatchTaskRunner.java:11-25](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-batch/nop-batch-dsl/src/main/java/io/nop/batch/dsl/runner/IBatchTaskRunner.java#L11-L25)
- [nop-batch/nop-batch-dsl/src/test/java/io/nop/batch/dsl/runner/TestBatchTaskRunner.java:14-33](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-batch/nop-batch-dsl/src/test/java/io/nop/batch/dsl/runner/TestBatchTaskRunner.java#L14-L33)
- [nop-batch/nop-batch-dsl/src/test/java/io/nop/batch/dsl/TestBatchTaskDsl.java:30-69](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-batch/nop-batch-dsl/src/test/java/io/nop/batch/dsl/TestBatchTaskDsl.java#L30-L69)
- [nop-batch/nop-batch-sys/src/test/java/io/nop/batch/sys/TestSysEventBatchTrigger.java:31](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-batch/nop-batch-sys/src/test/java/io/nop/batch/sys/TestSysEventBatchTrigger.java#L31)

---

## On this page

- 1. 构建：模块坐标与依赖
- 2. 测试：类清单与数量
- 3. 最小用法示例
