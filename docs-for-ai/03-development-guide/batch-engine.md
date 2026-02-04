# NopBatch 批处理引擎使用指南

## 概述

NopBatch 是一个基于可逆计算原理设计的下一代批处理引擎，类似于 Spring Batch + XXL-Job 的组合。它支持多种数据加载器、处理器和消费者，通过配置文件定义批处理任务，无需编写大量代码。

**核心特性**:
- 配置驱动：通过 XDef 模型定义批处理任务
- 多种数据源：支持文件（CSV/Excel/定长）、ORM、JDBC、生成器等
- 灵活处理：支持处理器链、分区处理、并行处理
- 状态管理：支持任务状态保存和历史记录
- 重试和跳过：内置重试策略和跳过策略
- 事务支持：灵活的事务范围配置

**源码位置**:
- XDef 模型：`/nop/schema/task/batch.xdef`
- 核心实现：`io.nop.batch.core.IBatchTask`
- DSL 管理：`io.nop.batch.dsl.manager.IBatchTaskManager`

## 核心概念

### Batch 任务模型

批处理任务由以下核心组件组成：

```
BatchTask
├── Loader（数据加载器）：负责加载数据
├── Processor（处理器）：负责处理每条记录
├── Tagger（标签器）：为记录打标签
└── Consumer（消费者）：根据标签消费记录
```

### 执行流程

```
1. 加载数据（Loader）
   ↓
2. 分批处理（按 batchSize 分成多个 Chunk）
   ↓
3. 处理记录（Processor 可选）
   ↓
4. 打标签（Tagger 可选）
   ↓
5. 消费记录（Consumer）
   ↓
6. 保存状态（saveState）
```

## Batch XDef 模型

### 基本结构

```xml
<batch taskName="string"              # 任务名称
       taskVersion="long"             # 任务版本
       batchSize="int"                # 批次大小
       concurrency="int=0"            # 并发数（0 表示串行）
       saveState="boolean"            # 是否保存状态
       transactionScope="enum"         # 事务范围
       executor="bean-name"           # 线程池
       x:schema="/nop/schema/task/batch.xdef">

    <!-- 任务键表达式，用于区分同一任务的不同实例 -->
    <taskKeyExpr>xpl-fn:(batchTaskCtx)=>string</taskKeyExpr>

    <!-- 数据加载器 -->
    <loader>
        <!-- loader 配置 -->
    </loader>

    <!-- 处理器（可选，多个） -->
    <processor name="!var-name" order="int=0">
        <!-- processor 配置 -->
    </processor>

    <!-- 标签器（可选） -->
    <tagger>
        <!-- tagger 配置 -->
    </tagger>

    <!-- 消费者（可选，多个） -->
    <consumer name="!var-name" order="int=0" forTag="string">
        <!-- consumer 配置 -->
    </consumer>

</batch>
```

### 核心属性说明

| 属性 | 类型 | 说明 |
|------|------|------|
| `taskName` | string | 任务名称（必需） |
| `taskVersion` | long | 任务版本号 |
| `batchSize` | int | 每批次处理的记录数 |
| `concurrency` | int | 并发线程数，0 表示串行 |
| `retryOneByOne` | boolean | 重试时是否逐条重试，否则整批重试 |
| `singleMode` | boolean | 批量读取后逐条处理、消费 |
| `singleSession` | boolean | 是否使用单一会话 |
| `saveState` | boolean | 是否保存执行状态 |
| `transactionScope` | enum | 事务范围（none/chunk/process/consume） |
| `rateLimit` | double | 每秒处理的最大记录数 |
| `jitterRatio` | double | 批次大小随机化比例（避免多线程同步） |
| `allowStartIfComplete` | boolean | 允许重复执行已完成任务 |
| `startLimit` | int | 最大启动次数，0 表示不限制 |
| `executor` | bean-name | 线程池名称 |
| `asyncProcessor` | boolean | 异步处理器模式 |
| `asyncProcessTimeout` | duration | 异步处理超时时间 |
| `useBatchRequestGenerator` | boolean | 使用批量请求生成器 |
| `snapshotBuilder` | bean-name | 快照构建器 |

### 事务范围（BatchTransactionScope）

- `CHUNK`: 每个 Chunk 开启一个事务
- `BATCH`: 整个批处理任务开启一个事务
- `NONE`: 不开启事务

## Loader（数据加载器）

Loader 负责从数据源加载数据，支持多种类型：

### 1. 文件加载器（File Reader）

#### CSV 文件

```xml
<loader>
    <file-reader filePath="/path/to/file.csv"
                encoding="UTF-8"
                csvFormat="DEFAULT">
        <headers>name,product,price,quantity</headers>
        <filter>
            return item.price > 1000;
        </filter>
    </file-reader>
</loader>
```

#### 定长文件（使用 Record File 模型）

```xml
<loader>
    <file-reader filePath="/path/to/data.dat"
                fileModelPath="/nop/schema/record/simple.record-file.xml"
                binary="true"/>
</loader>
```

对应的 Record File 模型：

```xml
<file x:schema="/nop/schema/record/record-file.xdef"
      xmlns:x="/nop/schema/xdsl.xdef" binary="true">
    <body>
        <fields>
            <field name="name" type="String" length="10" codec="FLS"/>
            <field name="product" type="String" length="5" codec="FLS"/>
            <field name="price" type="double" codec="f8be"/>
            <field name="quantity" type="int" codec="s4be"/>
        </fields>
    </body>
</file>
```

#### Excel 文件

```xml
<loader>
    <excel-reader filePath="/path/to/data.xlsx"
                 templatePath="/path/to/template.xlsx"
                 dataSheetName="Data"
                 headerRowCount="1">
        <filter>
            return item.quantity > 0;
        </filter>
    </excel-reader>
</loader>
```

### 2. ORM 加载器（ORM Reader）

```xml
<loader>
    <orm-reader entityName="DemoIncomingTxn"
                batchLoadProps="id,name,amount">
        <eql>
            select * from DemoIncomingTxn
            where status = 1
            order by createTime
        </eql>
    </orm-reader>
</loader>
```

### 3. JDBC 加载器（JDBC Reader）

```xml
<loader>
    <jdbc-reader querySpace="default"
                fetchSize="1000"
                streaming="false">
        <sql>
            SELECT id, name, amount
            FROM txn_table
            WHERE status = :status
        </sql>
    </jdbc-reader>
</loader>
```

### 4. 生成器加载器（Generator）

```xml
<loader>
    <generator genModelPath="create-card.batch-gen.xlsx"
               totalCountExpr="totalCount"/>
</loader>
```

### 5. 自定义源（Source）

```xml
<loader>
    <source>
        // 自定义逻辑，返回 List
        var items = [];
        for(var i=0; i<100; i++){
            items.push({id: i, name: "Item"+i});
        }
        return items;
    </source>
</loader>
```

### 分区加载器（Partition Dispatcher）

用于多线程环境下分区加载数据：

```xml
<loader>
    <orm-reader entityName="DemoIncomingTxn"
               partitionIndexField="_t.partitionIndex">
        <eql>
            select * from DemoIncomingTxn
            where status = 1
        </eql>
    </orm-reader>

    <dispatcher loadBatchSize="100"
               fetchThreadCount="4"
               partitionIndexField="_t.partitionIndex">
        <!-- 动态计算分区索引 -->
        <partitionFn>
            return item.id % 4;
        </partitionFn>
    </dispatcher>
</loader>
```

## Processor（处理器）

Processor 对每条记录进行处理，支持多个处理器链式调用。

### 简单处理器

```xml
<processor name="processor1" order="10">
    <filter>
        return item.quantity > 500;
    </filter>
    <source>
        item.price = item.price * 1.1;
        item.totalPrice = item.price * item.quantity;
        consume(item);
    </source>
</processor>
```

### 使用 Task 处理器

```xml
<processor name="processor2"
          task:taskModelPath="process-item.task.xml">
    <!-- process-item.task.xml 会接收 item, consume, batchChunkCtx 三个参数 -->
</processor>
```

对应的 process-item.task.xml：

```xml
<task x:schema="/nop/schema/task/task.xdef"
      xmlns:x="/nop/schema/xdsl.xdef"
      x:extends="/nop/task/lib/common.task.xml,/nop/task/lib/batch-common.task.xml">
    <input name="consume"/>
    <input name="item"/>
    <input name="batchChunkCtx"/>

    <steps>
        <xpl name="test" useParentScope="true">
            <source><![CDATA[
                // 自定义处理逻辑
                item.processed = true;
                consume(item);
            ]]></source>
        </xpl>
    </steps>
</task>
```

### Bean 处理器

```xml
<processor name="processor3" bean="myBatchProcessor">
    <!-- 使用 IoC 容器中名为 myBatchProcessor 的 Bean -->
</processor>
```

## Tagger（标签器）

Tagger 为每条记录打标签，Consumer 可以根据标签选择消费哪些记录。

```xml
<tagger bean="myTagger">
    <source>
        // 返回标签列表
        if(item.type === 'A'){
            return ['typeA'];
        } else {
            return ['typeB', 'special'];
        }
    </source>
</tagger>
```

## Consumer（消费者）

Consumer 负责处理记录并写入目标。

### 文件写入器（File Writer）

```xml
<consumer name="all">
    <file-writer filePath="/path/to/output.dat"
                fileModelPath="simple.record-file.xml"/>
</consumer>
```

### Excel 写入器

```xml
<consumer name="selected">
    <filter>
        return item.quantity > 500;
    </filter>
    <excel-writer filePath="/path/to/output.xlsx"
                 templatePath="/path/to/template.xlsx"
                 dataSheetName="Output"/>
</consumer>
```

### ORM 写入器

```xml
<consumer name="saveToDb">
    <transformer>
        // 转换实体结构
        var entity = {id: item.id, name: item.name};
        entity.amount = item.price * item.quantity;
        return entity;
    </transformer>
    <orm-writer entityName="DemoIncomingTxn"
                allowInsert="true"
                allowUpdate="false"/>
</consumer>
```

### JDBC 写入器

```xml
<consumer name="jdbcSave">
    <jdbc-writer querySpace="default"
                 tableName="txn_table"
                 allowInsert="true"
                 allowUpdate="true">
        <keyFields>id</keyFields>
        <fields>
            <field name="id" from="id" stdSqlType="VARCHAR"/>
            <field name="name" from="name" stdSqlType="VARCHAR"/>
            <field name="amount" from="amount" stdSqlType="DECIMAL"/>
        </fields>
    </jdbc-writer>
</consumer>
```

### 自定义源写入器

```xml
<consumer name="custom">
    <filter>
        return item.status === 'SUCCESS';
    </filter>
    <source>
        // 自定义写入逻辑
        items.forEach(item => {
            logInfo("Saving item: {}", item);
            // 执行写入操作
        });
    </source>
</consumer>
```

## 高级配置

### 历史记录（History Store）

记录任务执行历史和每条记录的处理状态。

```xml
<historyStore bean="myHistoryStore"
             onlySaveLastError="true">
    <recordKeyExpr>
        // 生成记录唯一标识
        return item.id + "_" + item.type;
    </recordKeyExpr>
    <recordInfoExpr>
        // 生成记录附加信息
        return {name: item.name, timestamp: now()};
    </recordInfoExpr>
</historyStore>
```

### 重试策略（Retry Policy）

```xml
<retryPolicy maxRetryCount="3"
            retryDelay="1000"
            maxRetryDelay="10000"
            exponentialDelay="true"
            jitterRatio="0.3">
    <exceptionFilter>
        // 只重试特定异常
        return err.isTimeout() || err.isNetworkError();
    </exceptionFilter>
</retryPolicy>
```

### 跳过策略（Skip Policy）

```xml
<skipPolicy maxSkipCount="10">
    <exceptionFilter>
        // 只跳过特定异常
        return err.isValidationError();
    </exceptionFilter>
</skipPolicy>
```

### 监听器（Listeners）

#### 任务级别监听器

```xml
<onTaskBegin>
    logInfo("Task begin: {}", batchTaskCtx.getTaskName());
</onTaskBegin>

<onBeforeTaskEnd>
    logInfo("Task about to end, status: {}", batchTaskCtx.getStatus());
</onBeforeTaskEnd>

<onTaskEnd>
    if(err){
        logError("Task failed", err);
    } else {
        logInfo("Task completed successfully");
    }
</onTaskEnd>
```

#### Chunk 级别监听器

```xml
<onChunkBegin>
    logInfo("Chunk begin: items={}", batchChunkCtx.getItems().size());
</onChunkBegin>

<onChunkEnd>
    if(err){
        logError("Chunk failed", err);
    } else {
        logInfo("Chunk completed");
    }
</onChunkEnd>
```

#### Load 和 Consume 监听器

```xml
<onLoadBegin>
    logInfo("Loading {} items", batchSize);
</onLoadBegin>

<onLoadEnd>
    if(err){
        logError("Load failed", err);
    } else {
        logInfo("Load completed");
    }
</onLoadEnd>

<onConsumeBegin>
    logInfo("Consuming {} items", items.size());
</onConsumeBegin>

<onConsumeEnd>
    if(err){
        logError("Consume failed", err);
    } else {
        logInfo("Consume completed");
    }
</onConsumeEnd>
```

## 完整示例

### 示例 1：CSV 文件处理

```xml
<batch taskName="processSalesData"
       batchSize="100"
       concurrency="4"
       executor="nop-global-worker"
       saveState="true"
       x:schema="/nop/schema/task/batch.xdef"
       xmlns:x="/nop/schema/xdsl.xdef">

    <taskKeyExpr>${bizDate}</taskKeyExpr>

    <loader>
        <file-reader filePath="/data/sales/${bizDate}.csv"
                    encoding="UTF-8"
                    csvFormat="DEFAULT">
            <filter>
                return item.quantity > 0;
            </filter>
        </file-reader>
    </loader>

    <processor name="calculateTotal" order="10">
        <source>
            item.totalAmount = item.price * item.quantity;
            consume(item);
        </source>
    </processor>

    <consumer name="saveToDb">
        <filter>
            return item.totalAmount > 10000;
        </filter>
        <transformer>
            var entity = {};
            entity.id = item.id;
            entity.productId = item.productId;
            entity.quantity = item.quantity;
            entity.amount = item.totalAmount;
            entity.bizDate = ${bizDate};
            return entity;
        </transformer>
        <orm-writer entityName="SalesRecord"
                    allowInsert="true"
                    allowUpdate="false"/>
    </consumer>

    <consumer name="saveToFile">
        <file-writer filePath="/output/summary/${bizDate}-summary.csv"/>
    </consumer>

</batch>
```

### 示例 2：数据库到文件

```xml
<batch taskName="exportUserData"
       batchSize="500"
       transactionScope="chunk"
       x:schema="/nop/schema/task/batch.xdef"
       xmlns:x="/nop/schema/xdsl.xdef">

    <loader>
        <orm-reader entityName="SysUser"
                   batchLoadProps="id,userName,realName,email,departmentId">
            <eql>
                select * from sys_user
                where status = 1
                order by id
            </eql>
        </orm-reader>
    </loader>

    <processor name="formatData" order="10">
        <source>
            item.departmentName = item.department ? item.department.name : '';
            item.createDate = formatDate(item.createTime, 'yyyy-MM-dd');
            consume(item);
        </source>
    </processor>

    <consumer name="exportCsv">
        <file-writer filePath="/export/users_${date}.csv"
                    encoding="UTF-8"
                    csvFormat="EXCEL">
            <headers>用户ID,用户名,真实姓名,邮箱,部门,创建日期</headers>
        </file-writer>
    </consumer>

</batch>
```

### 示例 3：分区并行处理

```xml
<batch taskName="processLargeData"
       batchSize="200"
       concurrency="8"
       executor="batch-worker"
       saveState="true"
       x:schema="/nop/schema/task/batch.xdef"
       xmlns:x="/nop/schema/xdsl.xdef">

    <taskKeyExpr>${batchDate}</taskKeyExpr>

    <loader>
        <orm-reader entityName="LargeDataTable"
                   batchLoadProps="id,data"
                   partitionIndexField="partitionIndex">
            <eql>
                select *, MOD(id, 8) as partitionIndex
                from large_data_table
                where processDate = ${batchDate}
            </eql>
        </orm-reader>

        <dispatcher loadBatchSize="200"
                   fetchThreadCount="4"
                   partitionIndexField="partitionIndex">
        </dispatcher>
    </loader>

    <processor name="process" order="10">
        <source>
            // 处理逻辑
            item.processed = true;
            item.processTime = now();
            consume(item);
        </source>
    </processor>

    <consumer name="update">
        <orm-writer entityName="LargeDataTable"
                    allowInsert="false"
                    allowUpdate="true">
            <keyFields>id</keyFields>
        </orm-writer>
    </consumer>

    <retryPolicy maxRetryCount="3"
                retryDelay="2000"
                exponentialDelay="true"/>

    <historyStore onlySaveLastError="true">
        <recordKeyExpr>item.id</recordKeyExpr>
    </historyStore>

</batch>
```

## 编程方式调用

### 使用 BatchTaskManager

```java
@Inject
IBatchTaskManager batchTaskManager;

public void executeBatchTask() {
    IBatchTaskContext context = batchTaskManager.newBatchTaskContext(svcCtx, scope);
    context.setVar("bizDate", LocalDate.now());

    IBatchTask task = batchTaskManager.loadBatchTaskFromPath(
        "/nop/batch/my-batch.batch.xml",
        BeanContainer.instance()
    );

    BatchTaskResult result = task.execute(context);
}
```

### 在 TaskFlow 中调用

```xml
<task x:schema="/nop/schema/task/task.xdef"
      xmlns:x="/nop/schema/xdsl.xdef"
      x:extends="/nop/task/lib/common.task.xml,/nop/task/lib/batch-common.task.xml">

    <input name="bizDate" type="LocalDate"/>

    <steps>
        <custom name="executeBatch"
                customType="batch:Execute"
                useParentScope="true"
                xmlns:batch="/nop/batch/xlib/batch.xlib">
            <batch:task taskName="processSalesData" batchSize="100">
                <loader>
                    <file-reader filePath="/data/sales/${bizDate}.csv"/>
                </loader>

                <consumer name="save">
                    <orm-writer entityName="SalesRecord"/>
                </consumer>
            </batch:task>
        </custom>
    </steps>
</task>
```

## 最佳实践

### 1. 批次大小选择

- 小数据量（< 10万条）：batchSize = 500-1000
- 中等数据量（10万-100万条）：batchSize = 1000-5000
- 大数据量（> 100万条）：batchSize = 5000-10000

### 2. 并发配置

- CPU 密集型任务：concurrency = CPU 核心数
- IO 密集型任务：concurrency = CPU 核心数 * 2
- 数据库操作：concurrency = 数据库连接池大小

### 3. 事务范围

- 数据一致性要求高：使用 `chunk` 事务范围（Chunk 的整个处理阶段）
- 允许部分失败：使用 `process` 事务范围（处理和消费阶段）
- 只读操作：使用 `none` 事务范围（不开启事务）

### 4. 性能优化

- 使用 `batchLoadProps` 减少数据库查询字段
- 合理使用分区加载器避免数据倾斜
- 设置 `jitterRatio` 避免多线程同步
- 使用 `rateLimit` 控制处理速度

### 5. 错误处理

- 合理配置重试策略
- 使用跳过策略避免任务中断
- 配置历史记录便于问题排查
- 监听器中记录详细日志

### 6. 状态管理

- 长时间运行的任务：启用 `saveState`
- 需要幂等性的任务：配置 `recordKeyExpr`
- 需要追溯的任务：启用 `historyStore`

## 相关文档

- [数据处理和任务编排指南](./data-processing.md)
- [XScript 脚本语言](../05-xlang/xscript.md)
- [XPL 模板语言](../05-xlang/xpl.md)
- [服务层开发](./service-layer.md)
- [数据访问层](./data-access.md)
