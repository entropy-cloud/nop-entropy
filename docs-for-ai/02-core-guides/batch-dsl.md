# Batch DSL 配置参考

## 结构

```
batch
  ├── param[] / taskKeyExpr / historyStore
  ├── retryPolicy / loadRetryPolicy / skipPolicy
  ├── inputSorter / listeners
  │
  ├── loader
  │    ├── file-reader / excel-reader / orm-reader / jdbc-reader / generator
  │    ├── source / provider / dispatcher / afterLoad / adapter
  │
  ├── processor[] (按 order)
  │    └── filter / source / provider / adapter
  │
  ├── tagger
  │
  └── consumer[] (按 name)
       ├── file-writer / excel-writer / orm-writer / jdbc-writer
       ├── source / provider / transformer / filter / adapter
       └── forTag / aggregator / metaProvider
```

## 权威参考

所有属性、元素、函数参数名的完整定义见 `../04-reference/xdefs/batch.xdef`。

函数体中的参数名由 xdef 的函数签名确定。例如 `xpl-fn:(item,batchChunkCtx)=>boolean` 表示 body 中可直接使用 `item` 和 `batchChunkCtx`。

## 要点说明

### concurrency + executor

`concurrency>0` 时需配合 `executor`（线程池 Bean）才会并行，否则使用 `SyncExecutor` 串行。

### transactionScope

枚举值：`none` / `chunk`（默认） / `process` / `consume`。

### taskKeyExpr

同一 `taskName + taskKey` 只允许一个活跃实例。例如 `<taskKeyExpr>bizDate</taskKeyExpr>`。

### 函数体写法

xdef 中 `xpl-fn:(params)=>type` 表示在 XML 中直接写 body，参数按 xdef 定义的名称使用：

```xml
<!-- 正确：没有 (item,ctx)=> 前缀，参数名来自 xdef -->
<filter>return item.quantity > 500;</filter>
<source>consume(item);</source>
<transformer>return { id: item.id, name: item.name.toUpperCase() };</transformer>
```

复杂逻辑用 `<![CDATA[ ... ]]>`：

```xml
<source><![CDATA[
    import io.nop.core.resource.ResourceHelper;
    import io.nop.xlang.xdsl.DslModelHelper;
    import io.nop.batch.dsl.utils.BatchLoaderHelper;

    function loadAll(taskCtx){
        let resource = ResourceHelper.resolveRelativeResource(xdslPath);
        let authModel = DslModelHelper.loadDslModel(resource);
        // ...
        return allItems;
    }
    return BatchLoaderHelper.batchLoadWithFullList(batchSize, batchChunkCtx, loadAll);
]]></source>
```

### BatchLoaderHelper

`io.nop.batch.dsl.utils.BatchLoaderHelper.batchLoadWithFullList(batchSize, batchChunkCtx, loadAll)` 是批量加载的常用工具函数。

### tagger + forTag

`tagger.source` 返回 tag 字符串列表。consumer 通过 `forTag` 匹配。未设置 `forTag` 的 consumer 总是消费。

### processor 委托子 task

`<processor name="p" task:taskModelPath="process-item.task.xml"/>` 可将处理逻辑委托给 nop-task。

### 成功/失败语义

`nop-batch` 的正确性边界是“记录处理成功后再推进 batch 状态”。运行时既支持按 chunk 组织一次加载/处理/提交，也支持在 chunk 内保留逐条记录的处理结果；不同配置会改变失败后的重试粒度、是否允许跳过，以及事务何时提交。

#### retryPolicy

`retryPolicy` 控制 processor / consumer 抛出异常后的重试策略。

- 未配置时，异常会直接让当前 chunk 失败。
- 配置后，运行时会在同一批记录上按策略重试；是否整批重试还是逐条重试，取决于 `retryOneByOne`。
- `retryPolicy` 解决的是“失败后要不要再试一次”，不改变成功确认边界；真正的成功确认仍要等对应记录处理成功并落入状态存储。

#### retryOneByOne

`retryOneByOne="true"` 表示整批处理失败后，把当前 chunk 拆成单条记录逐个重试。

- 典型用途：批量调用时先追求吞吐，失败后再定位到具体坏数据。
- 语义上等价于“先按 chunk 尝试一次，失败后降级为 record 级重试”。
- 对需要逐条状态转移的场景要谨慎：如果前半段成功、后半段单条失败，运行时可以只重试未完成记录；不要把它误解成“整个 chunk 只能一起成功或一起失败”。

#### skipPolicy

`skipPolicy` 决定某条记录在达到失败条件后是否允许跳过并继续后续记录。

- 未配置或返回不允许跳过时，失败会让 chunk/task 进入失败路径。
- 允许跳过时，该条记录会被标记为 skipped/failed result，batch 继续处理后续记录。
- 因此 `skipPolicy` 明确表达的是“容忍坏记录并继续”，而不是“把失败当成功”。

如果业务要求“同一 partition 头部失败时后续不能越过”，就不应使用会跨过失败记录的 skip 策略。

#### transactionScope

`transactionScope` 决定事务包裹范围，也直接影响失败回滚边界：

- `none`：不自动开启事务。每个底层操作按自身资源语义提交。
- `chunk`：一个 chunk 一次事务。chunk 内任一步失败会回滚该 chunk 的持久化副作用。
- `process`：按 processor 处理阶段控制事务，常用于“处理逻辑需要独立提交”。
- `consume`：按 consumer 写出阶段控制事务，常用于“读取/处理与最终写出分离”。

默认 `chunk` 最接近传统批处理模型。它可以作为批量 flush / 批量提交的优化单位，但最终完成结果仍应落到 batch 记录状态，而不是只剩一个粗粒度的“整批成功/失败”标志。

#### dispatcher

loader/processor 侧如果使用 partition dispatcher，则运行时先按 `partitionIndex` 分派，再在 worker 内保证同一 partition 的顺序执行。

- `dispatcher` 解决的是“并发执行模型”。
- `retryPolicy` / `skipPolicy` 解决的是“失败后如何处理”。
- dispatcher 只保证同一 partition 不会被两个线程同时处理，不额外规定失败后是否阻塞后续记录；这类约束应由具体业务处理逻辑决定。

#### 设计边界

`nop-batch` 可以做 windowed fetch、partition dispatch、chunk 事务与批量重试，但它的核心语义仍是批处理运行时，而不是消息队列协议。

- chunk completion 表示“这一批 batch 记录的阶段性完成情况”，同时允许在 chunk 内形成逐条记录结果并统一提交。
- 如果把 batch runtime 复用到事件系统，可以复用 chunk flush / 批量提交能力，但事件确认边界仍必须由 event row / cursor 语义自己定义，不能退化成“只看 chunk 成败”。

### 调度方式

批任务本身只定义“怎么加载、处理、消费”，真正的周期调度通常有两种接法。

#### 方式一：通过 nop-job 调度

推荐把周期性 batch 挂到 `nop-job`。

- `nop-job` 负责 cron / fixed delay 调度、集群内 worker 分配、分区切分与故障恢复。
- `nop-batch` 负责一次执行周期内的数据加载、处理和写出。
- 当任务需要分布式部署、worker ownership、分区范围切分时，应优先选这种方式。

#### 方式二：通过 IScheduledExecutor 调度

如果只是单机内定期触发一个 batch，也可以直接使用 `IScheduledExecutor`：

- `schedule()`：延迟一次执行。
- `scheduleAtFixedRate()`：按固定频率触发，不等待上次执行自然结束后再计时。
- `scheduleWithFixedDelay()`：上次执行结束后等待固定 delay 再启动下一次。

对 batch 来说，通常 `scheduleWithFixedDelay()` 更安全，因为它天然避免同一个调度器把尚未结束的上一次执行再次并发拉起。

#### 如何选择

- 需要集群调度、分区分派、故障转移：选 `nop-job`。
- 需要进程内轻量定时执行、没有集群协作诉求：可直接用 `IScheduledExecutor`。
- 如果 runnable 内部又会异步派发真正工作，要额外确认“调度返回”是否等价于“本轮 batch 已处理完成”；否则即使用 `scheduleWithFixedDelay()`，也可能出现轮次重叠。

---

## 完整示例

### 独立 batch.xml

```xml
<batch taskName="auth.importActionAuthJdbc" taskVersion="1" batchSize="100"
       x:schema="/nop/schema/task/batch.xdef" xmlns:x="/nop/schema/xdsl.xdef">

    <param name="xdslPath" mandatory="true"/>

    <loader>
        <source><![CDATA[
            import io.nop.core.resource.ResourceHelper;
            import io.nop.xlang.xdsl.DslModelHelper;
            import io.nop.batch.dsl.utils.BatchLoaderHelper;

            function loadAll(taskCtx){
                let resource = ResourceHelper.resolveRelativeResource(xdslPath);
                let authModel = DslModelHelper.loadDslModel(resource);
                let allItems = [];
                // ... 收集 item 并附加 _type 标签
                return allItems;
            }
            return BatchLoaderHelper.batchLoadWithFullList(batchSize, batchChunkCtx, loadAll);
        ]]></source>
    </loader>

    <tagger>
        <source>return [item._type];</source>
    </tagger>

    <consumer name="saveSites" forTag="site">
        <jdbc-writer tableName="nop_auth_site" allowUpdate="true" allowInsert="true">
            <keyFields>site_id</keyFields>
            <fields>
                <field name="site_id" stdSqlType="VARCHAR"/>
                <field name="display_name" stdSqlType="VARCHAR"/>
                <field name="order_no" stdSqlType="INTEGER"/>
            </fields>
        </jdbc-writer>
        <transformer><![CDATA[
            let site = item._data;
            return {
                site_id: site.id,
                display_name: site.displayName,
                order_no: item._orderNo,
                status: 1
            };
        ]]></transformer>
    </consumer>
</batch>
```

### nop-task 集成

```xml
<task x:extends="/nop/task/lib/batch-common.task.xml">
    <steps>
        <custom name="test" customType="batch:Execute"
                xmlns:batch="/nop/batch/xlib/batch.xlib">
            <batch:task taskName="test.loadData" batchSize="100" saveState="true">
                <taskKeyExpr>bizDate</taskKeyExpr>
                <loader>
                    <file-reader filePath="dev:/target/input/${bizDate}.dat"
                                 fileModelPath="simple.record-file.xlsx"/>
                </loader>
                <processor name="p">
                    <source>consume(item);</source>
                </processor>
                <consumer name="all">
                    <file-writer filePath="dev:/target/output/${bizDate}-all.dat"/>
                </consumer>
                <consumer name="selected">
                    <filter>return item.quantity > 500;</filter>
                    <file-writer filePath="dev:/target/output/${bizDate}-selected.dat"/>
                </consumer>
            </batch:task>
        </custom>
    </steps>
</task>
```

---

## 源码锚点

| 组件 | 路径 |
|------|------|
| DSL Schema | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef` |
| 本地副本 | `../04-reference/xdefs/batch.xdef` |
| 运行时标签库 | `nop-batch/nop-batch-dsl/src/main/resources/_vfs/nop/batch/xlib/batch.xlib` |
| DSL 模型类 | `nop-batch/nop-batch-dsl/src/main/java/io/nop/batch/dsl/model/` |
| 测试用例 | `nop-batch/nop-batch-dsl/src/test/java/io/nop/batch/dsl/TestBatchTaskDsl.java` |
| 真实示例 | `nop-runner/nop-cli/demo/_vfs/batch/import-action-auth-jdbc.batch.xml` |

## 相关文档

- `../03-modules/nop-batch.md` — 批处理引擎模块概览
- `./xlang-and-xpl-basics.md` — XPL 脚本基础
