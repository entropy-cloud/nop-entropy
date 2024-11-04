# 为什么SpringBatch是一个不好的设计？

SpringBatch是目前Java生态中最常用的批处理框架，银行业务中经常使用SpringBatch来实现日终结算和报表输出等功能。SpringBatch的起源是2006年埃森哲（Accenture）将自己的私有批处理框架开源，与SpringSource（Spring Framework 的背后公司）合作发布了Spring Batch 1.0。
后续SpringBatch的设计也经过多次重构，但是在今天看来已经存在严重的设计问题，对于性能优化、代码复用都极为不友好。本文将分析SpringBatch的设计问题，并结合NopBatch这一新的批处理框架的实现方案来介绍下一代批处理框架的设计思想。

## 一. SpringBatch简介

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

```
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
```

Writer负责写入一组对象在架构层面便于实现写入优化，比如使用JDBC的batch insert比单条insert要快很多。

## 二. SpringBatch的设计问题

### 2.1 Reader的每次调用不应该只返回一条记录

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
public interface IBatchLoader<S,C> {
    /**
     * 加载数据
     *
     * @param batchSize 最多装载多少条数据
     * @return 返回空集合表示所有数据已经加载完毕
     */
    List<S> load(int batchSize, C context);
}
```

* 增加了batchSize参数，明确告知加载器当前需要多少数据，便于底层实现优化
* 将context作为参数传递。SpringBatch中要获取context需要实现`ChunkListener.beforeChunk(ChunkContext context)` 这种接口函数， 将context保存为类的成员变量，然后才可以在load函数中使用，这样的设计过于繁琐。而IBatchLoader接口函数的参数信息完整，便于直接通过lambda函数来实现。
* 一次性返回一个Chunk所需的数据，而不是逐条返回，这样Reader内部就不需要维护复杂的状态变量

基于Loader返回的列表数据，我们可以使用很自然、很简单的方式批量加载相关数据

```javascript
List<T> data = loader.load(batchSize, context);
batchLoadRelatedData(data, context);
```

当处理数据需要获取互斥锁的时候，SpringBatch的设计就显得非常不友好。因为SpringBatch的ItemReader是逐条读取的，导致获取锁的时候无法进行批量优化，并且获取锁的顺序也难以控制，存在死锁风险。
而NopBatch的设计可以先按照某种规则对记录进行排序（不要求reader读取时整体排序），然后一次性获取所有需要的锁，这样就可以避免死锁风险。

### 2.2 Processor的每次调用不应该只返回一条记录

SpringBatch的处理逻辑类似于函数式编程中的map函数，`data.map(a->b)`，对每一条输入记录进行处理，返回一个输出记录。  这里很自然的就会产生一个疑问，为什么一次处理最多只会产生一个输出？不能一次处理产生多个输出吗？

现代的流处理框架的语义更接近于函数式编程中的flatMap函数, `data.flatMap(a->[b])`。也就是说，一次处理，可以有三种结果：A. 没有输出 B. 产生一个输出 C. 产生多个输出。如果一次处理可以产生多个输出，那么能不能每产生一个输出就交给下游进行处理，不用等待当前所有输出都产生之后再传递到下游？

NopBatch仿照流处理框架，定义了如下处理接口

```java
public interface IBatchProcessor<S, R, C> {
    /**
     * 执行类似flatMap的操作
     *
     * @param item     输入数据对象
     * @param consumer 接收返回结果，可能为一条或者多条。也可能不产生数据导致consumer不会被调用
     * @param context  上下文信息
     */
    void process(S item, Consumer<R> consumer, C context);

    /**
     * 两个processor合成为一个processor
     *
     * @param processor
     * @param <T>
     * @return
     */
    default <T> IBatchProcessor<S, T, C> then(IBatchProcessor<R, T, C> processor) {
        return new CompositeBatchProcessor<>(this, processor);
    }
}
```

* IBatchProcessor采用回调函数consumer来接收处理结果，内部产生输出元素之后就可以立刻消费

* IBatchProcessor接口还提供了一个then函数，可以将两个IBatchProcessor组合为一个整体的Processor，形成一种链式调用。这其实是类似函数式编程中Monad概念的一种应用。



### 2.3 Writer不应该只处理结果数据

SpringBatch中的ItemWriter一般固定用于消费Processor产生的结果数据，这样就导致固化了Read-Process-Write的处理流程。即使我们不需要处理过程或者写入过程，也不得不配置一个空的Processor或者Writer。

NopBatch引入了通用的BatchConsumer概念，使得BatchConsumer和BatchLoader构成一对对偶的接口，BatchLoader加载的数据直接传递给BatchConsumer进行消费。

```java
public interface IBatchConsumer<R, C> {
    /**
     * @param items   待处理的对象集合
     * @param context 上下文对象
     */
    void consume(List<R> items, C context);
}
```

Chunk的处理流程变得非常简单

```
List<T> items = loader.load(batchSize,context);
if(items == null || items.isEmpty())
   return STOP;
consumer.consume(items,context);
```

Processor可以看作是一种可选的Consumer实现方案

```java
public class BatchProcessorConsumer<S, R>
   implements IBatchConsumer<S, IBatchChunkContext> {
    @Override
    public void consume(List<S> items, IBatchChunkContext context) {
        List<R> collector = new ArrayList<>();
        for(S item: items){
            processor.process(item, collector::add, context);
        }
        consumer.consume(collector, context);
    }
}
```

### 2.4 事务处理机制不灵活

SpringBatch强制限定了一个Chunk的Read-Process-Write在一个事务中执行。但是在Nop平台中，业务实体一般都具有乐观锁版本字段，而且在OrmSession中会缓存所有实体对象，这使得我们可以选择仅在Write阶段打开事务，从而缩小事务影响范围，减少数据库连接池的占用时间。

比如说，Processor可以在事务之外运行，当业务处理失败时不会产生数据库层面的回滚，从而降低了数据库的压力，也减少了数据库层面的锁竞争。在`OrmSession.flush()`调用的时候才会实际将内存中的修改数据更新到数据库中，此时如果发现乐观锁版本发生变化，则可以触发数据库回滚，避免多线程并发访问同一个业务数据出现冲突。

在NopBatch中，我们根据transactionScope配置的不同，可以创建不同的支持事务处理的Consumer。

```javascript
 if (batchTransactionScope == BatchTransactionScope.consume
                && transactionalInvoker != null) {
    // 仅在consume阶段打开事务。process可以是纯逻辑处理过程，不涉及到修改数据库，而读数据一般不需要打开事务。
    consumer = new InvokerBatchConsumer(transactionalInvoker, consumer);
}

if (this.processor != null) {
    // 如果设置了processor,则先执行processor再调用consumer，否则直接调用consumer
    IBatchProcessor processor = this.processor;
    if (!this.processListeners.isEmpty()) {
        IBatchProcessListener processListener = new MultiBatchProcessListener(new ArrayList<>(this.processListeners));
        processor = new BatchProcessorWithListener<>(processor, processListener);
    }
    consumer = new BatchProcessorConsumer(processor, this.consumer);
}
// 在process和consume阶段打开事务
if (batchTransactionScope == BatchTransactionScope.process && transactionalInvoker != null) {
    consumer = new InvokerBatchConsumer(transactionalInvoker, consumer);
}
```

### 2.5 失败重试逻辑不灵活

SpringBatch内置了失败重试逻辑：当Processor执行失败时，可以自动按照RetryPolicy的设置重试多次。但是很多时候Processor内部并没有完成所有涉及到单条记录的业务逻辑。比如说，Processor中可能并没有实际保存数据，而是将保存延迟到整个chunk处理完毕，统一使用Jdbc Batch机制来保存。这种情况下，针对单条记录的Retry就无法起到作用。

在NopBatch中，我们提供了一种针对整个chunk的重试机制。当chunk执行失败时，我们会自动重试整个chunk，而且重试的时候可以选择逐条重试，也就是将每个条目作为单独的chunk去重试，这样虽然损失了批量保存的优化，但是可以隔离出那些有错误的单条记录。

```java
public class RetryBatchConsumer<R>
    implements IBatchConsumer<R, IBatchChunkContext> {

    public void consume(List<R> items, IBatchChunkContext context) {
        IBatchRecordSnapshotBuilder.ISnapshot<R> snapshot =
                snapshotBuilder.buildSnapshot(items);
        try {
            consumer.consume(items, context);
        } catch (BatchCancelException e) {
            throw e;
        } catch (Exception e) {
            // 有可能部分记录已经被处理，不需要被重试
            if (context.getCompletedItemCount() > 0) {
                items = new ArrayList<>(items);
                items.removeAll(context.getCompletedItems());
            }

            retryConsume(e, items, snapshot, context);
        }
    }

    RetryOnceResult retryConsumeOneByOne(int retryCount, List<R> items,
                                         IBatchChunkContext context) {
        context.setSingleMode(true);
        List<R> retryItems = new ArrayList<>();

        Throwable retryException = null;
        Throwable fatalError = null;

        for (R item : items) {
            List<R> single = Collections.singletonList(item);

            Throwable consumeError = null;

            try {
                // 将每条输入数据做成一个小的批次单独执行一次
                consumer.consume(single, context);
                context.addCompletedItem(item);
            } catch (BatchCancelException e) {
                consumeError = e;
                throw e;
            } catch (Exception e) {
                consumeError = e;

                if (retryPolicy.getRetryDelay(e, retryCount + 1, context) >= 0) {
                    // 如果item可重试
                    retryItems.add(item);
                    retryException = e;
                }
            }
        }
        ...
    }
}
```

* 之所以能够实现整个chunk的retry，是因为Loader可以一次性获取到一个Chunk的所有输入数据，所以只要把这些数据缓存下来，就可以多次调用Consumer。Processor的处理逻辑已经被封装到BatchProcessorConsumer中，因此重试时只需要重复consume就可以。

* 如果有些已经成功完成的记录不需要被重复处理，则可以在consumer中成功处理之后，将它们加入到BatchChunkContext上下文对象中的completedItems集合中。重试整个chunk时，已经被完成的记录会被自动跳过。

• Do not do things twice in a batch run
