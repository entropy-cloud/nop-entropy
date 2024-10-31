# 为什么SpringBatch是一个不好的设计？

SpringBatch是目前Java生态中最常用的批处理框架，银行业务中经常使用SpringBatch来实现日终结算和报表输出等功能。但是SpringBatch的设计在今天看来已经存在严重0的设计问题，对于性能优化、代码复用都极为不友好。
本文将分析SpringBatch的设计问题，并结合NopBatch这一新的批处理框架的实现方案来介绍下一代批处理框架的设计思想。

## SpringBatch简介

以下是智谱清言AI对于SpringBatch的简介：

Spring Batch 是一个用于批量处理的轻量级、全面的框架。它旨在支持开发对企业级系统的批量处理需求，如大规模数据导入、转换和迁移。Spring Batch 建立在 Spring 框架之上，允许开发者利用 Spring 的核心特性，如依赖注入和声明式事务管理，来创建健壮的批处理应用程序。

以下是 Spring Batch 的一些关键特性：

1. **可重用性**：Spring Batch 提供了许多可重用的组件，如作业读取器（ItemReaders）、作业写入器（ItemWriters）和处理者（ItemProcessors），这些组件可以很容易地被定制和扩展。

2. **可扩展性**：框架设计考虑到了扩展性，允许开发者通过插件机制添加自定义的组件。

3. **健壮性**：Spring Batch 支持事务管理，确保批量作业的原子性和一致性。它还提供了跳过和重试机制，以处理数据错误和故障。

4. **声明式开发**：通过配置和少量的代码，开发者可以定义复杂的批处理作业流程。

5. **并行处理**：Spring Batch 支持并行处理，可以通过分区来加速大量数据的处理。

6. **启动和调度**：Spring Batch 作业可以很容易地被集成到 Spring 的任务调度框架（如 Quartz）中，或者通过命令行启动。

7. **监控和管理**：框架提供了监控和管理作业执行的工具，包括作业状态追踪和统计信息收集。

主要组件包括：

- **JobRepository**：用于存储作业执行期间的数据，包括作业的执行状态。
- **JobLauncher**：用于启动作业的运行。
- **Job**：表示一个完整的批处理作业，由一系列的步骤（Step）组成。
- **Step**：作业中的单个步骤，可以包含读、处理、写等操作。
- **ItemReader**：负责读取数据。
- **ItemProcessor**：负责处理读取到的数据。
- **ItemWriter**：负责将处理后的数据写入到目的地。

Spring Batch 的使用场景非常广泛，包括但不限于：

- 数据同步
- 财务和报表生成
- 数据转换和迁移
- 文件处理

通过使用 Spring Batch，企业可以有效地处理批量操作，提高数据处理效率，同时确保系统的稳定性和数据的准确性。
==========智谱清言AI创作完毕=====================

### SpringBatch的核心接口

SpringBatch内置的核心逻辑是标准的读取-处理-写出三个步骤，对应的接口如下：

```java
interface ItemReader<T> {
    T read();
}

interface ItemProcessor<I, O> {
    O process(@NonNull I item);
}

interface ItemWriter<T> {
    void write(Chunk<? extends T> chunk);
}

```

为了控制处理过程中的资源消耗，SpringBatch引入了Chunk的概念，Chunk是一次性处理的数据量，可以通过配置commit-interval来控制。
比如以下的配置表示每100条数据作为一个chunk来处理，每个chunk都对应一个read-process-write的过程，

```xml
<batch:job id="firstBatchJob">
  <batch:step id="step1">
    <batch:tasklet>
      <batch:chunk reader="itemReader" writer="itemWriter"
                   processor="itemProcessor" commit-interval="100">
      </batch:chunk>
    </batch:tasklet>
  </batch:step>
</batch:job>
```

### Chunk的处理逻辑

Chunk的处理逻辑用伪代码表示，大致上是逐个读取并处理，然后收集所有返回结果，一次性写入。

````
doInTransaction:
  beforeChunk() // 在事务内执行
  repeat:
      item = reader.read();
      result = processor.process(item);
      if(result != null)
         outputs.add(result);

  try{
    beforeWrite()
    writer.write(outputs);
    afterWrite()
  }catch(e){
    onWriteError(e,outputs);
  }

afterChunk()  // 在事务外执行
````

Writer负责写入一组对象在架构层面便于实现写入优化，比如使用JDBC的batch insert比单条insert要快很多。

## SpringBatch的设计问题

### 1. ItemReader不应该只返回一条记录
SpringBatch的设计中ItemReader的read调用每次只返回一条记录，这样的设计导致了难以进行批量读取优化。大量的reader内部实现时是按照某个pageSize批量读取，然后再逐条返回。

常见的实现方式如下：
```java
class JdbcPagingItemReader<T> implements ItemReader<T> {
    public T read() {
        if (results == null || current >= pageSize) {
            results = doReadPage();
            page++;
            if (current >= pageSize) {
                current = 0;
            }
        }

        int next = current++;
        if (next < results.size()) {
            return results.get(next);
        } else {
            return null;
        }
    }
}
```

如果Reader读取的不是简单的平面结构记录，而是一个复杂的业务对象。那么如果要实现属性的批量加载，就必须修改Reader的实现代码，导致了代码的耦合性增加。

NopBatch中使用IBatchLoader接口来实现批量加载，可以更好的支持批量读取优化。

```java
public interface IBatchLoader<S> {
    /**
     * 加载数据
     *
     * @param batchSize 最多装载多少条数据
     * @return 返回空集合表示所有数据已经加载完毕
     */
    List<S> load(int batchSize, IBatchChunkContext context);
}
```

* 增加了batchSize参数，明确告知加载器当前需要多少数据，便于底层实现优化
* 将context作为参数传递。SpringBatch中要获取context需要实现`ChunkListener.beforeChunk(ChunkContext context)` 这种接口函数， 将context保存为类的成员变量，然后才可以在load函数中使用，这样的设计过于繁琐。
* 一次性返回一个Chunk所需的数据，而不是逐条返回，这样Reader内部就不需要维护复杂的状态变量

基于Loader返回的列表数据，我们可以使用很自然、很简单的方式批量加载相关数据
```javascript
List<T> data = loader.load(batchSize, context);
batchLoadRelatedData(data, context);
```

当处理数据需要获取互斥锁的时候，SpringBatch的设计就显得非常不友好。因为SpringBatch的ItemReader是逐条读取的，导致获取锁的时候无法进行批量优化，并且获取锁的顺序也难以控制，存在死锁风险。
而NopBatch的设计可以先按照某种规则对记录进行排序（不要求reader读取时整体排序），然后一次性获取所有需要的锁，这样就可以避免死锁风险。

### 2. ItemProcessor不应该只返回一条记录

