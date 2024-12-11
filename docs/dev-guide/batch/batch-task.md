# 批处理任务模型

批处理模型包含loader、processor、writer配置。

```xml

<batch taskName="test.loadData" batchSize="100" saveState="true" concurrency="5" executor="nop-global-worker">

  <taskKeyExpr>bizDate</taskKeyExpr>

  <loader>
    <file-reader filePath="dev:/target/input/${bizDate}.dat" fileModelPath="simple.record-file.xlsx"/>
  </loader>

  <processor name="processor1">
    <source>
      consume(item);
    </source>
  </processor>

  <processor name="processor2" task:taskModelPath="process-item.task.xml">
    <!--                    <source>-->
    <!--                        <task:Execute taskModelPath="process-item.task.xml" inputs="${{item,consume,batchChunkCtx}}" xpl:lib="/nop/task/xlib/task.xlib"/>-->
    <!--                    </source>-->
  </processor>

  <consumer name="all">
    <file-writer filePath="dev:/target/output/${bizDate}-all.dat" record:file-model="SimpleFile"/>
  </consumer>

  <consumer name="selected">
    <filter>
      return item.quantity > 500;
    </filter>

    <file-writer filePath="dev:/target/output/${bizDate}-selected.dat" fileModelPath="simple.record-file.xml"/>
  </consumer>

</batch>
```

* taskKeyExpr： 同样的taskName，不同的taskKeyExpr会生成不同的任务实例。每个taskKey只允许一个活跃的实例在运行。
* batchSize： 每次处理的数据量
* concurrency: 使用多少个线程并行处理。
* executor: 使用哪个线程池来执行任务，默认使用全局的nop-cached-thread-pool。executor的名称对应于GlobalWorkers中注册的线程池或者BeanContainer管理的某个Executor。
* 每个批处理任务包含一个loader, 多个processor，以及多个consumer。processor和consumer都是可选配置。

## 分区处理

如果配置了loader的dispatcher，则会使用PartitionDispatchLoaderProvider来实现分区加载。

```xml

<batch>
  <loader>
    <orm-reader entityName="DemoIncomingTxn">

    </orm-reader>

    <dispatcher loadBatchSize="100" partitionIndexField="_t.partitionIndex">
    </dispatcher>

    <!-- reader读取到items集合之后会调用afterLoad回调函数对结果进行加工 -->
    <afterLoad>
      ...
    </afterLoad>
  </loader>
</batch>
```

1. 启动多个内部线程调用内部的IBatchLoader来加载数据，不断将数据放入到分区任务队列中。
2. 加载时使用loadBatchSize来控制每次加载的数据量。
3. 使用partitionIndexField来指定分区字段，该字段值相同的记录会被存放到某一个任务队列中，由PartitionDispatchQueue统一管理。
4. BatchTask运行的时候会通过loader从PartitionDispatchQueue中读取。

## 文件读写

```xml

<batch>
  <loader>
    <file-reader filePath="dev:/target/input/${bizDate}.dat" fileModelPath="simple.record-file.xlsx"/>
  </loader>

  <consumer name="selected">
    <filter>
      return item.quantity > 500;
    </filter>

    <file-writer filePath="dev:/target/output/${bizDate}-selected.dat" fileModelPath="simple.record-file.xml"/>
  </consumer>
</batch>
```

* 通过fileModelPath可以根据Record模型来实现文件格式解析和生成。
* 如果不指定fileModelPath，则缺省情况下会按照csv格式进行读写。

## ORM读写

```xml

<batch>

  <orm-reader entityName="DemoIncomingTxn">
    <query>
      <filter>
        <eq name="status" value="1"/>
      </filter>
      <orderBy>
        <field name="id"/>
      </orderBy>
    </query>
  </orm-reader>

  <consumer name="all">
    <!-- 从文件读取数据之后插入到数据库中，插入时判断是否已经存在，如果存在则会忽略这条记录-->
    <orm-writer entityName="DemoIncomingTxn" allowInsert="true" allowUpdate="false">
      <keyFields>cardNumber,txnAmount,txnTime</keyFields>
    </orm-writer>
  </consumer>

</batch>
```

* query: 读取时可以使用query来指定查询条件。
* keyFields：如果配置了keyFields，则写入时会检查唯一键，判断数据是否已经存在。
* allowInsert：如果发现数据不存在，是否允许插入数据，缺省为true。
* allowUpdate：如果发现数据已经存在，是否允许更新数据，缺省为false。




