  # Why Spring Batch is a Bad Design?
  
  Explained in the video: [https://www.bilibili.com/video/BV1TgBEYYETK/](https://www.bilibili.com/video/BV1TgBEYYETK/)
  
  Spring Batch is currently one of the most commonly used batch processing frameworks in the Java ecosystem. It is frequently employed in banking applications to handle end-of-day settlement and report generation, among other functionalities. The origins of Spring Batch date back to 2006, when Accenture (a major IT services company) open-sourced its proprietary batch processing framework and collaborated with SpringSource (the company behind the Spring Framework) to release Spring Batch 1.0.
  
  Subsequent updates to Spring Batch have undergone numerous refactorings, but as it stands today, the framework is plagued by significant design flaws, particularly in terms of performance optimization and code reuse. This article will analyze the design issues of Spring Batch and introduce the implementation approach of NopBatch, a new batch processing framework, to discuss the design principles of the next-generation batch processing framework.
  
  ## 1. Introduction to Spring Batch
  
  Here is the summary provided by智谱清言AI regarding Spring Batch:
  
  Spring Batch is a lightweight and comprehensive batch processing framework designed to support enterprise-level bulk data operations such as large-scale data import, transformation, and migration. Built on top of the Spring Framework, it allows developers to leverage core Spring features like dependency injection and declarative transaction management to create robust batch processing applications.
  
  Key characteristics of Spring Batch include:
  
  1. **Reusability**: The framework provides numerous reusable components such as item readers (ItemReaders), writers (ItemWriters), and processors (ItemProcessors) that can be easily customized and extended.
  
  2. **Extensibility**: The framework's design is structured to allow for extensibility, enabling developers to add custom components through plug-in mechanisms.
  
  3. **Robustness**: Spring Batch supports transaction management to ensure the atomicity and consistency of batch jobs. It also provides mechanisms for skipping and retrying items to handle data errors and failures.
  
  4. **Declarative Development**: Through configuration and minimal code, developers can define complex batch processing workflows.
  
  5. **Parallel Processing**: Spring Batch supports parallel processing, which can be optimized using partitions.
  
  6. **Launch and Scheduling**: Batch jobs can be easily integrated with Spring's task scheduling framework (e.g., Quartz) or launched via command line.
  
  7. **Monitoring and Management**: The framework provides tools for monitoring and managing the execution of batch jobs, including tracking job states and collecting statistical information.
  
  Major components include:
  
  - **JobRepository**: Used to store data during job execution, including the job's execution state.
  - **JobLauncher**: Responsible for launching the execution of jobs.
  - **Job**: Represents a complete batch job consisting of a series of steps (Steps).
  - **Step**: A single step within a job, which can include read, process, and write operations.
  - **ItemReader**: Manages the reading of data items.
  - **ItemProcessor**: Manages the processing of read data items.
  - **ItemWriter**: Manages the writing of processed data to the target destination.
  
  ![spring-batch.png](https://i.gyazo.com/630b8c1a6f74e69d565a76dffe935233.png)
  
  Spring Batch is applicable in a wide range of scenarios, including but not limited to:
  
  - Data synchronization
  - Financial and reporting generation
  - Data transformation and migration
  - File processing
  
  By leveraging Spring Batch, organizations can effectively handle bulk operations, enhancing data processing efficiency while maintaining system stability and data accuracy.
  
  ==================== 智谱清言AI 创作完毕 ======================
  
  ### Core Interfaces of SpringBatch
  
  SpringBatch's built-in core logic follows the standard read-process-write workflow, with corresponding interfaces as follows:
  
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
  
  To manage resource consumption during processing, SpringBatch introduces the concept of Chunk, which represents a batch of data to be processed. The commit-interval configuration determines how many items are processed before committing.
  
  For example:
  
  ```java
  <property name="commitInterval">100</property>
  ```
  
  This configuration means that every 100 items will be processed as a single Chunk, with each Chunk corresponding to a read-process-write cycle.

```xml
<batch:job id="firstBatchJob">
  <batch:step id="step1">
    <batch:tasklet>
      <batch:chunk reader="itemReader" processor="itemProcessor"
                   writer="itemWriter" commit-interval="100">
      </batch:chunk>
    </batch:tasklet>
  </batch:step>
</batch:job>
```

### Chunk Processing Logic

The processing logic of the Chunk is implemented using pseudocode and involves reading and processing items one by one, collecting all results, and writing them in a single batch. The logic is as follows:

```pseudocode
doInTransaction:
  beforeChunk() // Executed within a transaction
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

afterChunk()  // Executed outside of a transaction
```

The Writer is responsible for writing a batch of objects in a way that's optimized at the architectural level. For example, using JDBC's `batch insert` can be significantly faster than inserting individual records.

## II. Design Issues with SpringBatch

### 2.1 Reader Should Not Return One Record Per Call

In SpringBatch's design, each call to `ItemReader.read()` returns only one record. This design makes it difficult to optimize bulk reading. Most implementations achieve this by reading in batches (e.g., using a `pageSize`) and then returning individual records.

A common implementation example is:

```java
class JdbcPagingItemReader<T> implements ItemReader<T> {
    public T read() {
        if (this.results == null || current >= pageSize) {
            this.results = doReadPage();
            page++;
            if (current >= pageSize) {
                current = 0;
            }
        }

        int next = this.current++;
        if (next < results.size()) {
            return results.get(next);
        } else {
            return null;
        }
    }
}
```

This design not only requires the Reader to maintain temporary state variables but also complicates bulk optimization. If the Reader reads complex business objects instead of simple flat records, optimizing attribute loading would require modifying the Reader's implementation, increasing code coupling.

NopBatch uses the `IBatchLoader` interface for batch loading, which better supports bulk reading optimization.

```java
public interface IBatchLoader<S> {
    /**
     * Loads data
     *
     * @param batchSize The maximum number of data items to load
     * @return Returns an empty collection if all data has been loaded
     */
    List<S> load(int batchSize, IBatchChunkContext context);
}
```

* Provides `batchSize` as a parameter to inform the loader how many data items it needs to load, enabling optimization at the lower level.
* Passes `context` as a parameter. In SpringBatch, obtaining `context` requires implementing `ChunkListener.beforeChunk(ChunkContext context)` and saving it as a class member variable before using it in the `load` function. This design is overly cumbersome. The `IBatchLoader` interface's parameters are comprehensive, allowing direct implementation via lambda functions.
* Instead of returning data one record at a time, we return all the necessary data for a Chunk in one go, which means the Reader doesn't need to maintain complex state variables.

Using the list returned by the Loader, we can naturally and simply batch load related data.

```javascript
List<T> data = loader.load(batchSize, context);

// Batch load other related data. The loaded data can be placed in the context or added as an extension field within the data elements.
batchLoadRelatedData(data, context);
```

When processing data that requires mutual exclusion locks, SpringBatch's design seems particularly unfavorable. This is because SpringBatch's ItemReader reads data one record at a time, preventing batch optimization when acquiring locks and making it difficult to control the order of lock acquisition, which can lead to deadlocks.
 
In contrast, NopBatch's design first sorts records according to certain rules (without requiring the Reader to perform sorting during read operations) and then acquires all necessary locks in one go. This avoids the risk of deadlocks.

In summary, SpringBatch's design reflects its legacy as an Item-oriented framework, making Chunk-level processing unnatural.

> The concept of **Chunk** was introduced in Spring 2.0. Initially, SpringBatch only had the Item concept.

The interface definition for `ItemWriter` in SpringBatch 1.0 is as follows:

```java
public interface ItemWriter {

    public void write(Object item) throws Exception;

    public void flush() throws FlushFailedException;

    public void clear() throws ClearFailedException;
}
```

### 2.2 Processor's processing should not only return a single record each time

In SpringBatch, the processing logic of `Processor` resembles function-style programming with map functions, such as `data.map(a->b)`, where each input record is processed and a single output record is returned. This naturally leads to a question: why can't a single processing step produce multiple outputs?

Modern stream processing frameworks align more closely with the `flatMap` function in functional programming, `data.flatMap(a->[b])`. This means that for each input, there are three possible outcomes: A. No output B. One output C. Multiple outputs.

The stream-style processing pattern suggests that each generated output should be passed immediately to the downstream without waiting for all outputs to be produced before transmission.
 
NopBatch mimics this behavior by defining the following processing interface:

```java
public interface IBatchProcessor<S, R> {
    /**
     * Executes an operation similar to `flatMap`.
     *
     * @param item      The input data object
     * @param consumer  A consumer that can receive one or multiple results. It may not be called if no data is produced.
     * @param context   Context information
     */
    void process(S item, Consumer<R> consumer, IBatchChunkContext context);

    /**
     * Combines two processors into one processor.
     *
     * @param processor The other processor
     * @param <T>      The type parameter for the combined processor
     * @return A new IBatchProcessor that combines this processor with the given one
     */
    default <T> IBatchProcessor<S, T> then(IBatchProcessor<R, T> processor) {
        return new CompositeBatchProcessor<>(this, processor);
    }
}
```

* **IBatchProcessor** uses a callback function (`consumer`) to receive processed results. Once output elements are generated, they can be immediately consumed.

* The `IBatchProcessor` interface also provides a `then()` method to combine two processors into one, creating a chain-like invocation similar to functional programming's Monad concept.

### 2.3 Writer can accept Collection-type data

First, the naming of ItemWriter in SpringBatch is somewhat inappropriate. From a naming perspective, ItemWriter appears to be designed for consuming data produced by Processor, which has concretized the Read-Process-Write processing flow at the conceptual level. However, there are many scenarios where we do not need to write out results; we only require consumption of input data.

NopBatch introduced a generic BatchConsumer concept, enabling BatchConsumer and BatchLoader to form a pair of dual interfaces. The data loaded by BatchLoader is directly passed to BatchConsumer for consumption.

```java
public interface IBatchConsumer<R> {
    /**
     * @param items   Collection of objects to be processed
     * @param context Context object
     */
    void consume(Collection<R> items, IBatchChunkContext context);
}
```

Based on the Consumer interface, the processing flow for a Chunk becomes very straightforward:

```javascript
List<T> items = loader.load(batchSize, context);
if (items == null || items.isEmpty()) {
   return ProcessingResult.STOP;
}
consumer.consume(items, context);
```

Processor can be considered as an optional implementation of the Consumer interface:

```java
public class BatchProcessorConsumer<S, R>
    implements IBatchConsumer<S> {
    @Override
    public void consume(Collection<S> items, IBatchChunkContext context) {
        Collection<R> outputs = new ArrayList<>();
        for (S item : items) {
            processor.process(item, outputs::add, context);
        }
        consumer.consume(outputs, context);
    }
}

> When Processor is asynchronously executed, a ConcurrentLinkedQueue is used to store outputs.
```

In contrast to NopBatch's Consumer, which directly receives Collection-type data, SpringBatch's Writer accepts Chunk-type data. Its structure is defined as follows:

```java
class Chunk<W> implements Iterable<W>, Serializable {

    private List<W> items = new ArrayList<>();

    private List<SkipWrapper<W>> skips = new ArrayList<>();

    private final List<Exception> errors = new ArrayList<>();

    private Object userData;

    private boolean end;

    private boolean busy;
}
```

The Chunk structure contains various pieces of information, but in Processor and Reader implementations, it cannot be directly accessed, leading to unnecessary complexity.

In the NopBatch architecture, Loader/Processor/Consumer interfaces all accept the same IBatchChunkContext parameter, enabling mutual coordination through it. Additionally, within the IBatchConsumer interface, items are passed as a Collection type without requiring explicit List-type usage.
```java
interface IBatchLoader<S> {
    List<S> load(int batchSize, IBatchChunkContext chunkCtx);
}

interface IBatchProcessor<S, R> {
    void process(S item, Consumer<R> consumer,
          IBatchChunkContext chunkCtx);
}

interface IBatchConsumer<R> {
     void consume(Collection<R> items, IBatchChunkContext chunkCtx);
}
```

Clearly, NopBatch's three core interfaces are more intuitive. The type loaded by the loader can directly match the input type of the consumer, and all three interfaces share the IBatchChunkContext context environment, allowing for coordination through it.

**A good architectural design should be able to reveal its internal organization through its function signatures (type definitions).**

### 2.4 Lack of Flexible Transaction Handling

SpringBatch enforces that a Chunk's Read-Process-Write operations must be executed within a single transaction. However, in the Nop platform, business entities generally have an optimistic lock version field, and all entity objects are cached within an OrmSession. This allows us to choose to open a transaction only during the Write phase, thereby reducing the scope of transaction impact and minimizing database connection pool usage.

For example, the Processor can operate outside of transactions. If business processing fails, it does not result in rollbacks at the database level, thereby reducing database pressure and minimizing lock contention at the database level. When `OrmSession.flush()` is called, data modifications stored in memory are actually written to the database at this point. If an optimistic lock version conflict is detected, a database rollback can be triggered to prevent conflicts from occurring due to concurrent access of the same business data.

In NopBatch, we can create consumers that support different transaction scopes based on `transactionScope` configuration.

```javascript
 if (batchTransactionScope == BatchTransactionScope.consume
                && transactionalInvoker != null) {
    // Only open a transaction during the consume phase. The process stage is purely logical processing without involving database modifications, while reading data typically does not require opening a transaction.
    consumer = new InvokerBatchConsumer(transactionalInvoker, consumer);
}

if (this.processor != null) {
    // If a processor is set, execute it first before calling the consumer; otherwise, directly call the consumer.
    IBatchProcessor<S, R> processor = this.processor.setup(context);
    consumer = new BatchProcessorConsumer<>(processor, (IBatchConsumer<R>) consumer);
}

// Open a transaction during both the process and consume phases
if (batchTransactionScope == BatchTransactionScope.process && transactionalInvoker != null) {
    consumer = new InvokerBatchConsumer(transactionalInvoker, consumer);
}
```

### 2.5 Inflexibility of Failure Retry Logic

SpringBatch provides built-in failure retry logic: if a Processor fails, it can automatically retry multiple times according to the RetryPolicy settings. However, in many cases, the Processor may not contain all necessary business logic for individual records. For example, the Processor might defer data saving until the entire chunk is processed and then use Jdbc Batch to save all records at once. In such cases, retries for individual records are ineffective.

In NopBatch, we provide a retry mechanism that operates on the entire chunk level. If a chunk fails, we automatically retry it as a whole. Additionally, we support per-item retries, where each item is treated as an independent chunk for retrying. This approach sacrifices the optimization of batch saves but isolates errors in individual items, making it easier to debug and fix specific issues.
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

* The ability to implement the entire chunk's retry is due to the Loader being able to fetch all input data for a Chunk in one go. Once this data is cached, the Consumer can be called multiple times. The processing logic of the Processor has been encapsulated within the BatchProcessorConsumer, so during retries, only the consume method needs to be invoked again.

* If some records have already been successfully processed and do not need to be retried, they can be added to the IBatchChunkContext context object's completedItems collection after being successfully consumed by the Consumer. When retrying the entire chunk, these already completed records will be automatically skipped.
## 3. NopBatch Architecture Changes

### 3.1 Using Context to Dynamically Register Listeners

In SpringBatch, if a reader/writer/processor needs to listen for step start, end, etc., the standard approach is to implement the `StepExecutionListener` interface.

```java
class MyProcessor implements ItemProcessor, StepExecutionListener {

    @Override
    public void beforeStep(StepExecution stepExecution) {
        System.out.println("Before Step: " + stepExecution.getStepName());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        System.out.println("After Step: " + stepExecution.getStepName());
        return stepExecution.getExitStatus();
    }
    ....
}
```

This approach presents two problems:

1. If using Spring container to manage these beans, consider concurrency scenarios, the beans need to be scoped as `step` rather than global Singleton. The implementation of SpringBatch's StepScope is highly complex, requiring a global flag `spring.main.allow-bean-definition-overriding` to be enabled. However, Spring defaults disallow bean redefinition and strongly recommends disabling this flag. For reference, see [Issue: StepScope not working when XML namespace activated](https://github.com/spring-projects/spring-batch/issues/3936).

2. If we wrap Reader/Processor/Writer, the listeners cannot be automatically detected by the SpringBatch framework. Additional registration of listeners is required. Ideally, the listener should be registered automatically when a Writer is defined without needing extra configuration in XML files.

```xml
    <step id="step1">
        <tasklet>
            <chunk reader="itemReader" writer="compositeWriter" commit-interval="2">
                <streams>
                    <stream ref="fileItemWriter1"/>
                    <stream ref="fileItemWriter2"/>
                </streams>
            </chunk>
        </tasklet>
    </step>
    <beans:bean id="compositeWriter"
                class="org.springframework.batch.item.support.CompositeItemWriter">
        <beans:property name="delegates">
            <beans:list>
                <beans:ref bean="fileItemWriter1" />
                <beans:ref bean="fileItemWriter2" />
            </beans:list>
        </beans:property>
    </beans:bean>
```

For example, in the configuration above, a CompositeWriter is used which delegates to two Writers. However, SpringBatch does not recognize this setup as the compositeWriter does not implement the `ItemStream` callback interface. To enable proper callbacks, additional streams configuration is needed to specify which Writers should be called at appropriate times.
If we compare the evolution of front-end frameworks, an interesting observation can be made: SpringBatch's approach resembles traditional Class Components in a way that's strikingly similar.

```javascript
class MyComponent extends Vue {
    // Logic executed after the component is mounted
    mounted() {
        console.log('Component mounted');
    }

    // Logic executed after the component is updated
    updated() {
        console.log('Component updated');
    }

    // Logic executed before the component is destroyed
    beforeDestroy() {
        console.log('Component will be destroyed');
    }

    // Rendering function
    render(h) {
        return (
            <div>
                {/* Rendering logic for the component */}
                Hello, Vue Class Component!
            </div>
        );
    }
}
```

The core design philosophy revolves around implementing lifecycle hooks as functions within components. The framework registers corresponding event listeners when these components are created and uses the component's object properties to manage communication and organization between multiple callback functions.

A revolutionary advancement in the front-end field introduced what is known as the Hooks mechanism, moving away from the traditional Class-Based component approach. See my WeChat public article [Understanding React's Essence through React Hooks](https://mp.weixin.qq.com/s/-n5On67e3_46zH6ppPlkTA)

Under the Hooks system, front-end components are reduced to a single render function that considers the one-time initialization process. Vue opts to abstract components into render function constructors.

```javascript
defineComponent({
    setup() {
        onMounted(() => {
            console.log('Component mounted');
        });

        onUpdated(() => {
            console.log('Component updated');
        });

        onBeforeUnmount(() => {
            console.log('Component will be destroyed');
        });

        return () => (
            <div>
                {/* Rendering logic for the component */}
                Hello, Vue Composition API!
            </div>
        );
    }
})
```

The Hooks mechanism offers several advantages over traditional Class Components:

1. Event listener functions can be defined independently of the class structure and easily encapsulated into reusable `useXXX` functions, such as wrapping `onMounted` and `onUpdated` into a reusable function.

2. Multiple event listener functions can communicate via closures without relying on the `this` pointer to navigate through object properties.

3. Event listeners can be dynamically registered based on input parameters.

The key architectural change here is the introduction of a global, dynamic event registration mechanism instead of binding event listeners to specific object properties as members of an object.

Similar to the Hooks system, NopBatch abstracts the core functionality from `IBatchLoader` (a component runner) to `IBatchLoaderProvider` (a factory component), which provides a `setup` method to create `IBatchLoader`.

```java
public interface IBatchLoaderProvider<S> {
    IBatchLoader<S> setup(IBatchTaskContext context);

    interface IBatchLoader<S> {
        List<S> load(int batchSize, IBatchChunkContext context);
    }
}
```

The `setup` function returns a loader akin to Vue's component `setup` function returning a renderer. In Vue, the component calls the returned renderer once and then repeatedly invokes it.
The same applies to the `setup` method of `IBatchLoaderProvider`, which is called once to return `IBatchLoader`, which is then invoked multiple times.

The context object provides methods for registering callback functions such as `onTaskBegin` and `onTaskEnd`.

```java
class ResourceRecordLoaderProvider<S> extends AbstractBatchResourceHandler
        implements IBatchLoaderProvider<S> {

    public IBatchLoader<S> setup(IBatchTaskContext context) {
        LoaderState<S> state = newLoaderState(context);
        return (batchSize, batchChunkCtx) ->{
            // Execute callback function after each chunk is completed
            batchChunkCtx.onAfterComplete(err -> onChunkEnd(err, batchChunkCtx, state));
            return load(batchSize, state);
        };
    }

    LoaderState<S> newLoaderState(IBatchTaskContext context) {
        LoaderState<S> state = new LoaderState<>();
        state.context = context;
        IResource resource = getResource(context);
        IRecordInput<S> input = recordIO.openInput(resource, encoding);

        if (recordRowNumber) {
            input = new RowNumberRecordInput<>(input);
        }

        state.input = input;

        // Register callback function to close resources after task completion
        context.onAfterComplete(err -> {
            IoHelper.safeCloseObject(state.input);
        });

        return state;
    }
}
```

In the above example, we explicitly pass the context object to register callback functions like `onAfterComplete`. If further encapsulation is performed using a `ThreadLocal` to store the context object, the invocation pattern can resemble hooks more closely.

```java
public class BatchTaskGlobals {
    static final ThreadLocal<IBatchTaskContext> s_taskContext = new NamedThreadLocal<>("batch-task-context");

    public static IBatchTaskContext useTaskContext() {
        return s_taskContext.get();
    }

    public static void provideTaskContext(IBatchTaskContext taskContext) {
        s_taskContext.set(taskContext);
    }

    public static void onTaskEnd(BiConsumer<IBatchTaskContext, Throwable> action) {
        IBatchTaskContext ctx = useTaskContext();
        ctx.onAfterComplete(error -> action.accept(ctx, error));
    }

    public static void onBeforeTaskEnd(Consumer<IBatchTaskContext> action) {
        IBatchTaskContext ctx = useTaskContext();
        ctx.onBeforeComplete(()-> action(ctx));
    }
}
Importing static methods from BatchTaskGlobals allows the following usage format:

```java
IBatchLoader setup(ITaskContext context){
    init();
    ...
}
```

void init(){
    onBeforeTaskEnd(taskCtx ->{
        ...
    });

    onChunkBegin(batchChunkCtx ->{
        ...
    });
}

Similarly, IBatchProcessor and IBatchConsumer objects are changed to return the result of the setup function for IBatchProcessorProvider and IBatchConsumerProvider respectively.

The Provider is now a singleton object, which can be configured using an IoC container without requiring dynamic Scope support. Additionally, regardless of how many layers of encapsulation are used, the IBatchTaskContext object can be directly accessed, allowing dynamic registration of various event listener functions through it.

> Interestingly, while Hooks were invented by React, Vue chooses to divide logic into setup and render phases as a more natural approach. Otherwise, it would be necessary to distinguish at every detail whether an initialization action needs to be performed on the first call.
> This is highly unfavorable for performance optimization and can easily introduce confusion at the conceptual level.

### 3.2 Organizing Logic Flow with General TaskFlow

SpringBatch provides a simple logic flow model that can be configured in XML, defining multiple steps and their transition relationships, while also supporting parallel execution and conditional transitions.

For example, the following code generated by Wuli Qianshui AI demonstrates how it starts two parallel sub-flows using split, then serializes the steps within each sub-flow:

```xml
<job id="exampleJob" xmlns="http://www.springframework.org/schema/batch">
    <split id="split1" task-executor="taskExecutor">
        <flow>
            <step id="step1">
                <tasklet ref="tasklet1" />
                <next on="COMPLETED" to="step2" />
                <next on="FAILED" to="step4" />
            </step>
            <step id="step2">
                <tasklet ref="tasklet2" />
                <next on="COMPLETED" to="step4" />
            </step>
        </flow>
        <flow>
            <step id="step3">
                <tasklet ref="tasklet3" />
                <next on="COMPLETED" to="step4" />
            </step>
        </flow>
    </split>
    <step id="step4">
        <tasklet ref="tasklet4" />
    </step>
</job>
```

In SpringBatch, the task units correspond to the Tasklet interface, with chunk processing being a specific implementation of Tasklet.
```java
public class ChunkOrientedTasklet<I> implements Tasklet {
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

        Chunk<I> inputs = (Chunk<I>) chunkContext.getAttribute(INPUTS_KEY);
        if (inputs == null) {
            inputs = chunkProvider.provide(contribution);
            if (buffering) {
                chunkContext.setAttribute(INPUTS_KEY, inputs);
            }
        }

        chunkProcessor.process(contribution, inputs);
        chunkProvider.postProcess(contribution, inputs);

        chunkContext.removeAttribute(INPUTS_KEY);
        chunkContext.setComplete();
        return RepeatStatus.continueIf(!inputs.isEnd());
    }
}
```

Interestingly, Spring Batch's early design lacked a general-purpose Tasklet interface, which reflects the inherent limitations in its abstract level of design.

> The Tasklet interface was introduced in Spring Batch 2.0, see [Spring Batch 2.0 Highlights](https://docs.spring.io/spring-batch/docs/2.2.x/migration/2.0-highlights.html)

```java
public interface Tasklet {

    /**
     * Given the current context in the form of a step contribution, do whatever is
     * necessary to process this unit inside a transaction. Implementations return
     * {@link RepeatStatus#FINISHED} if finished. If not they return
     * {@link RepeatStatus#CONTINUABLE}. On failure throws an exception.
     * @param contribution mutable state to be passed back to update the current step
     * execution
     * @param chunkContext attributes shared between invocations but not between restarts
     * @return an {@link RepeatStatus} indicating whether processing is continuable.
     * Returning {@code null} is interpreted as {@link RepeatStatus#FINISHED}
     * @throws Exception thrown if error occurs during execution.
     */
    RepeatStatus execute(StepContribution contribution,
           ChunkContext chunkContext) throws Exception;

}
```

The Tasklet interface essentially serves as a general-purpose function interface, with its primary purpose being to support retry capabilities on failure through the use of StepContribution for persistent storage.
The key characteristics of SpringBatch emphasize reusability and scalability, but in practice, both are significantly lacking. The core interfaces and workflow orchestration provided by SpringBatch are specific to its own implementation and cannot be used across broader scenarios. For example, if we extend SpringBatch's built-in FlatFileItemReader to support parsing of a specific data file format, the resulting extension can only be used in the context of SpringBatch's batch processing for that specific scenario and must be utilized through the SpringBatch framework. Any attempt to reuse or leverage SpringBatch-related components outside of the framework becomes highly cumbersome.

SpringBatch's job configuration can be viewed as a simplistic and non-generic workflow orchestration mechanism, capable only of orchestrating batch tasks and not serving as a generic orchestration engine for broader use cases. In the NopBatch framework, we explicitly isolate workflow orchestration from the batch processing engine by using a generic NopTaskFlow for logic workflows, while NopBatch is responsible only for handling the Chunk processing step in a workflow. This simplifies the design of both NopTaskFlow and NopBatch, making their implementation significantly more straightforward (with only a few thousand lines of code) and highly scalable. The work done by NopTaskFlow and NopBatch can be applied to more general scenarios.

NopTaskFlow is built from scratch based on the reversible computation principle, representing the next-generation logic workflow framework. Its core abstraction centers around supporting RichFunction with decorators and persistent states. It exhibits high performance and lightweight characteristics (with approximately 3,000 lines of core code), making it suitable for use in any scenario requiring function configuration-based decomposition. Detailed information can be found in [A Next-Generation Logic Engine Built from Scratch: NopTaskFlow](https://mp.weixin.qq.com/s/2mFC0nQon_l2M82tOlJVhg).

Implementation of equivalent configurations to SpringBatch's Job in NopTaskFlow is illustrated below:

```xml
<task x:schema="/nop/schema/task/task.xdef" xmlns:x="/nop/schema/xdsl.xdef">
  <steps>
    <parallel nextOnError="step4">
      <steps>
        <sequential timeout="3000">
          <steps>
            <simple name="step1" bean="tasklet1"/>
            <simple name="step2" bean="tasklet2"/>
          </steps>
        </sequential>

        <simple name="step3" bean="tasklet3"/>
      </steps>
    </parallel>

    <simple name="step4" bean="tasklet4"/>
  </steps>
</task>
```

NopTaskFlow provides rich step types such as parallel, sequential, loop, choose, and fork, each supporting configurations like timeout, retry, decorator, catch, when, and validator. For instance, the following configuration indicates that if execution exceeds 3 seconds, a timeout exception is thrown; otherwise, if it fails, it is retried up to 5 times, with each attempt executed within a transaction.

```xml
 <sequential timeout="3000">
    <retry maxRetryCount="5" />
    <decorator name="transaction" />

    <steps>
       <simple name="step1" bean="tasklet1" />
       <simple name="step2" bean="tasklet2" />
    </steps>
 </sequential>
```

The 'sequential' step executes tasks in order, eliminating the need to explicitly define the flow between steps. Once a task completes without errors, it automatically proceeds to the next one. This execution model closely resembles typical programming languages, making it easier to map to programmatic logic. Notably, unlike SpringBatch where all steps share a global variable space and each step has its own persistent variable space, NopTaskFlow allows nested function calls that create a stack-like structure for variable scoping. Variables in the parent scope are accessible within nested functions.

NopTaskFlow also supports direct embedding of Xpl template language and XScript scripts.
```xml
<xpl name="step1">
  <input name="a" type="int">
    <source>x + 1</source>
  </input>
  <input name="b" type="int">
    <source>y + 2</source>
  </input>
  <output name="RESULT" type="int"/>
  <source>
    return a + b
  </source>
</xpl>

The above XML code is equivalent to the following function call:

```javascript
function step1(a: int, b: int) {
   return { RESULT: a + b };
}

const { RESULT } = step1(x + 1, y + 2);
```

### 3.3 Supporting Distributed Parallel Execution

Spring Batch provides a mechanism to split data into multiple partitions and assign them to multiple slave steps (slave steps) for parallel execution. The main steps and components involved in distributed parallel processing are as follows:

1. **Defining the Partitioner**:
   - The partitioner is responsible for dividing the data into multiple partitions. Each partition contains a portion of the data, which is then stored in the `ExecutionContext`.
2. **Master Step Configuration**:

   - The master step is responsible for managing partitions and allocating tasks. It generates partitions using a partitioner and assigns each partition to an associated slave step for processing.

3. **Slave Step Configuration**:

   - The slave step is responsible for handling the data assigned to it. Each slave step can execute concurrently, improving processing efficiency.

4. **Task Executor**:

   - The task executor is used to perform parallel execution of slave steps. It can be configured with different types of executors, such as `SimpleAsyncTaskExecutor` or `ThreadPoolTaskExecutor`, to achieve parallel processing.

Through these steps, Spring Batch effectively decomposes large tasks into multiple smaller tasks for parallel processing, thereby improving efficiency and performance.

```xml
<batch:job id="partitionedJob">
   <batch:step id="masterStep">
      <batch:partition step="slaveStep" partitioner="rangePartitioner">
         <batch:handler grid-size="4" task-executor="taskExecutor"/>
      </batch:partition>
   </batch:step>
</batch:job>

<!-- Slave step definition -->
<batch:step id="slaveStep">
   <batch:tasklet>
     <batch:chunk reader="itemReader" processor="itemProcessor"
                  writer="itemWriter" commit-interval="10"/>
   </batch:tasklet>
</batch:step>
```

SpringBatch's partitioning parallel design essentially implements partitioned reading from the Reader, with each slave step using its own dedicated Reader to read data before processing. If a particular partition has an exceptionally large amount of data, other partitions' threads will become idle once all available threads are occupied and cannot assist in handling it.
 
In real-world business scenarios, there often exists a need for finer-grained partitioning. For example, in banking applications, it is typically sufficient to ensure that individual account data is processed in order, while data for different accounts can be handled concurrently. NopBatch provides a more flexible partitioning and parallel processing strategy.

Firstly, NopBatch's `BatchTask` has a `concurrency` parameter that allows specifying the number of parallel threads to use for processing. Additionally, the `IBatchChunkContext` stores both the `concurrency` parameter and the current thread index parameter, enabling direct determination during processing of how many handling threads are available and which thread is currently executing, facilitating internal partitioning operations.
```java
interface IBatchChunkContext {
    int getConcurrency();
    int getThreadIndex();
    ...
}

class BatchTask implements IBatchTask {
    public CompletableFuture<Void> executeAsync(IBatchTaskContext context) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        context.fireTaskBegin();

        // Multiple threads can be executed concurrently. loader/processor/consumer all need to be thread-safe
        CompletableFuture<?>[] futures = new CompletableFuture[concurrency];
        for (int i = 0; i < concurrency; i++) {
            futures[i] = executeChunkLoop(context, i);
        }

        CompletableFuture.allOf(futures).whenComplete((ret, err) -> {
            onTaskComplete(future, meter, err, context);
        });

        return future;
    }

    CompletableFuture<Void> executeChunkLoop(IBatchTaskContext context,
                                             int threadIndex) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        executor.execute(() -> {
            BatchTaskGlobals.provideTaskContext(context);
            try {
                do {
                    if (context.isCancelled())
                        throw new BatchCancelException(ERR_BATCH_CANCEL_PROCESS);

                    IBatchChunkContext chunkContext = context.newChunkContext();
                    chunkContext.setConcurrency(concurrency);
                    chunkContext.setThreadIndex(threadIndex);

                    if (processChunk(chunkContext) != ProcessResult.CONTINUE)
                        break;
                } while (true);

                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            } finally {
                BatchTaskGlobals.removeTaskContext();
            }
        });
        return future;
    }
}

The step-level parallel processing in NopBatch differs from Spring Batch's grid partition. In NopBatch, the loader, processor, and consumer are shared during step-level parallel execution, but it achieves this by passing concurrency and threadIndex parameters through IBatchChunkContext.
NopBatch has built-in **PartitionDispatchLoaderProvider**, which provides flexible partition loading capabilities. When setting up, this provider starts several loading threads to actually load the data. It then generates a hash value between 0 and 32767 in memory using a hashing function based on business critical information. Each hash value corresponds to a micro-queue, and each record in a queue must be processed in order. All micro-queues are managed centrally in **PartitionDispatchQueue**.

When loading chunk data, each processing thread can retrieve data from the micro-queue within the **PartitionDispatchQueue**. Once data is retrieved, it marks the corresponding micro-queue as used, preventing other threads from processing the same micro-queue. After chunk processing is complete, the micro-queue is released in the `onChunkEnd` callback function.

```xml
<batch>
    <loader>
        <!-- First, use OrmReader to read data and then call dispatcher to dispatch to partition task queues, with each partitionIndex corresponding to one queue -->
        <orm-reader entityName="DemoIncomingTxn">

        </orm-reader>

        <!-- After loading items into a collection, the afterLoad callback function is called to process the results -->
        <afterLoad>
            for(let item of items){
                // Dynamically compute partitionIndex and assign it to the item
                item.make_t().partitionIndex = ...;
            }
        </afterLoad>

        <!-- The partitionIndex is dynamically computed in afterLoad, as it does not exist in the original data -->
        <!-- Therefore, SpringBatch's grid configuration cannot handle this scenario -->
       <dispatcher loadBatchSize="100" partitionIndexField="_t.partitionIndex">
       </dispatcher>
    </loader>
</batch>
```

> In the Nop platform, all entities provide a `make_t()` method that returns a Map for storing temporary custom attributes. This design aligns with the philosophy of enabling extensible capabilities for each local computation.

The above configuration is an example of a NopBatch DSL configuration. It uses OrmReader to read data from the **DemoIncomingTxn** table and sends it to different queues based on `"_t.partitionIndex"` as defined in the entity.

In SpringBatch, each thread corresponds to a partition, with the number of partitions equaling the number of threads. However, in NopBatch, the maximum number of partitions is 32768, which is much greater than the number of parallel processing threads in typical batch jobs while still being far less than the actual number of business entities. This ensures balanced partitioning without requiring excessive memory to maintain multiple queues.

If you indeed need similar step-level parallel processing capabilities like SpringBatch, you can directly use **NopTaskFlow**'s `fork` or `fork-n` step configuration.

```xml
<fork name="processFile" var="fileName" aggregateVarName="results"
      executor="nop-global-worker">
     <producer>
       return ["a.dat","b.dat"]
     </producer>

     <steps>
        <!-- The context environment contains a variable named fileName -->
     </steps>

     <aggregator>
       <!-- This can be an optional aggregation action to perform after all fork steps are executed -->
     </aggregator>
</fork>
```

The producer in the `fork` step can dynamically compute a list and start a separate step instance for each element in the list.

Both **OrmReader** and **JdbcReader** in NopBatch DSL support `partitionIndexField` configuration. If this field is specified along with the `partitionRange` parameter, it will automatically generate partition filtering conditions.
```xml
<batch>
    <loader>
        <orm-reader entityName="MyEntity" partitionIndexField="partitionIndex">
           <filter>
              <eq name="status" value="1" />
           </filter>
        </orm-reader>
    </loader>
</batch>
```

When invoking a batch task, the `partitionRange` configuration is passed.

```javascript
batchTaskContext.setPartitionRange(IntRangeBean.of(1000, 100));
```

The above configuration will automatically append partition filter conditions during execution, generating the following SQL statement:

```sql
select o from MyEntity o
where o.status = '1'
and o.partitionIndex between 1000 and (1000 + 100 - 1)
```

## Three. DSL Forest: NopTaskFlow + NopBatch + NopRecord + NopORM

While Spring Batch is touted as a declarative development approach, its declarative model relies heavily on the limited capabilities of Spring IoC for bean assembly, leaving much of the business-related logic still embedded within Java code. It does not establish a comprehensive framework for fine-grained declarative batch processing. On the other hand, if Spring Batch were to introduce a domain-specific model specifically for batch processing, it might lack flexibility and could restrict its applicability.

NopBatch's solution represents a highly distinctive approach characteristic of the Nop platform: the use of a "DSL Forest" to resolve issues by reusing a set of seamlessly nested DSLs tailored for different domain layers, rather than relying on a single monolithic DSL designed specifically for batch processing. For batch processing, we establish a minimalistic NopBatch model that abstracts batch-specific chunk processing logic and provides a series of auxiliary implementation classes, such as `PartitionDispatcherQueue`. At a higher level of task orchestration, we leverage existing `NopTaskFlow`, which is entirely unrelated to batch processing. It requires no internal engine modifications for integration with `NopBatch` and achieves seamless fusion through meta-programming, smoothing over any gaps between the two.

For example, in the file parsing layer, Spring Batch provides a `FlatFileItemReader` that enables configuration for parsing simple data files with structured formats.
```xml
<bean id="flatFileItemReader" class="org.springframework.batch.item.file.FlatFileItemReader">
    <property name="resource" value="classpath:data/input.dat" />
    <property name="lineMapper">
        <bean class="org.springframework.batch.item.file.mapping.DefaultLineMapper">
            <property name="lineTokenizer">
                <bean class="org.springframework.batch.item.file.transform.FixedLengthTokenizer">
                    <property name="names" value="length,name,price,quantity" />
                    <property name="columns">
                        <list>
                            <bean class="org.springframework.batch.item.file.transform.Range">
                                <constructor-arg value="1" />
                                <constructor-arg value="4" />
                            </bean>
                            <bean class="org.springframework.batch.item.file.transform.Range">
                                <constructor-arg value="5" />
                                <constructor-arg value="24" />
                            </bean>
                            <bean class="org.springframework.batch.item.file.transform.Range">
                                <constructor-arg value="25" />
                                <constructor-arg value="30" />
                            </bean>
                            <bean class="org.springframework.batch.item.file.transform.Range">
                                <constructor-arg value="31" />
                                <constructor-arg value="36" />
                            </bean>
                        </list>
                    </property>
                </bean>
            </property>
            <property name="fieldSetMapper">
                <bean class="org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper">
                    <property name="targetType" value="com.example.MyRecord" />
                </bean>
            </property>
        </bean>
    </property>
</bean>
Clearly, this configuration is highly inefficient and is specifically designed for SpringBatch's fileReader. Outside of SpringBatch, it is generally difficult to directly reuse the same configuration information for parsing similar data files.

In the Nop platform, we define a specialized model called Record, which is intended for data message format analysis and generation. However, this model is not specifically tailored for batch processing file parsing; instead, it can be used in any context where message parsing and generation are required. It represents a general-purpose declarative development mechanism and offers significantly greater capabilities compared to SpringBatch's FlatFile configuration.
```xml
<task x:schema="/nop/schema/task/task.xdef" xmlns:x="/nop/schema/xdsl.xdef"
      x:extends="/nop/task/lib/common.task.xml,/nop/task/lib/batch-common.task.xml"
      xmlns:record="record" xmlns:task="task" x:dump="true">

    <input name="bizDate" type="LocalDate" />

    <record:file-model name="SimpleFile" binary="true">
        <body>
            <fields>
                <field name="name" type="String" length="10" codec="FLS"/>
                <field name="product" type="String" length="5" codec="FLS"/>
                <field name="price" type="double" codec="f8be"/>
                <field name="quantity" type="int" codec="s4be"/>
            </fields>
        </body>
    </record:file-model>

    <steps>
        <custom name="test" customType="batch:Execute" useParentScope="true"
                xmlns:batch="/nop/batch/xlib/batch.xlib">
            <batch:task taskName="test.loadData" batchSize="100" saveState="true">

                <taskKeyExpr>bizDate</taskKeyExpr>

                <loader>
                    <file-reader filePath="dev:/target/input/${bizDate}.dat"
                        fileModelPath="simple.record-file.xlsx" />
                </loader>

                <!-- Can define multiple processors to be executed in sequence -->
                <processor name="processor1">
                    <source>
                        consume(item);
                    </source>
                </processor>

                <processor name="processor2" task:taskModelPath="process-item.task.xml">
                </processor>

                <consumer name="all">
                    <file-writer filePath="dev:/target/output/${bizDate}-all.dat"
                      record:file-model="SimpleFile"/>
                </consumer>

                <!-- Can define multiple consumers, then use filter to control consumption of a subset of output data -->
                <consumer name="selected">
                    <filter>
                        return item.quantity > 500;
                    </filter>

                    <file-writer filePath="dev:/target/output/${bizDate}-selected.dat"
                       fileModelPath="simple.record-file.xml"/>
                </consumer>

            </batch:task>
        </custom>
    </steps>
</task>
```
  
In the above example, it demonstrates how to seamlessly integrate a Batch bulk processing model and Record message format definition within NopTaskFlow.

1. The NopTaskFlow logic orchestration engine was designed without any knowledge of batch processing tasks or built-in Record models.
2. **Extending NopTaskFlow does not require implementing any specific extension interfaces of the NopTaskFlow engine nor using an internal registration mechanism to register extended steps**. This contrasts sharply with typical framework designs.
3. You only need to examine the `task.xdef` meta-model to understand the node structure of the NopTaskFlow logic orchestration model and then use XLang's built-in metaprogramming mechanisms to implement extensions.
4. The line `x:extends="/nop/task/lib/common.task.xml,/nop/task/lib/batch-common.task.xml"` introduces basic models, which are processed through meta-programming mechanisms like `x:post-extends` into the XNode structure of the current model. **We can optionally introduce compile-time structural transformation rules**.
5. The `<custom name="test" customType="batch:Execute" xmlns:batch="xxx.xlib">` element's `customType` will be automatically recognized as an Xpl tag function and transform the `custom` node into a call to the `<batch:Execute>` tag function.

```xml
<custom customType="ns:TagName" xmlns:ns="libPath" ns:argName="argValue">
  <ns:slotName>...</ns:slotName>
</custom>
will be automatically transformed into

<xpl>
    <source>
        <ns:TagName xpl:lib="libPath" argName="argValue">
            <slotName>...</ns:slotName>
        </ns:TagName>
    </source>
</xpl>
```

This means that `customType` is a tag function name with a namespace. All attributes and child nodes sharing the same namespace will be treated as properties and child nodes of this tag function. While directly using Xpl steps is not very complex, metaprogramming transformations based on `customType` can further reduce information expression complexity, ensuring that only the minimal amount of information is expressed, with all derivable information automatically deduced.

7. The `<batch:Execute>` tag will be parsed at compile time to resolve its task node and construct an IBatchTask object, which can then be directly accessed at runtime without re-parsing.
8. All XDSL are automatically capable of handling extension attributes and extension nodes. By default, attributes and nodes with namespaces do not participate in XDef meta-model checks. Therefore, custom `<record:file-model>` models can be introduced within task nodes, and they will be automatically parsed by the `batch-common.task.xml` metaprogramming processor into RecordFileMeta objects and stored as compile-time variables.
9. The `file-reader` and `file-writer` nodes' `record:file-model` attributes will be recognized and automatically transformed.

```xml
<file-writer record:file-model="SimpleFile">
</file-writer>
will be transformed into

<file-writer>
    <newRecordOutputProvider>
      <!-- Xpl模板语言中#{xx}表示访问编译期定义的变量 -->
       <batch-record:BuildRecordOutputProviderFromFileModel
              fileModel="#{SimpleFile}"
                xpl:lib="/nop/batch/xlib/batch-record.xlib"/>
    </newRecordOutputProvider>
</file-writer>
```

* `#{}` is the syntax defined in XLang for compile-time expressions, allowing access to compile-time variables.
10. In a specific step of NopTaskFlow, BatchTask is called, and within the Processor of BatchTask, the same method can be used to call NopTaskFlow to implement per-record processing logic.

```xml
<processor name="processor2">
    <source>
        <task:Execute taskModelPath="process-item.task.xml"
                 inputs="${{item,consume,batchChunkCtx}}"
                 xpl:lib="/nop/task/xlib/task.xlib"/>
    </source>
</processor>

* The task model can be embedded with the `customType="batch:Execute"` attribute. In the batch processor configuration, another task model can be embedded using the `task:taskModelPath` attribute.
11. Regarding database access, NopORM provides a complete ORM model supporting multi-tenanting, logical deletion, field encryption and decryption, flexible transaction handling, associated query optimization, batch loading, and batch saving. The orm-reader and orm-writer components enable database reading and writing.

```xml
<batch>
    <loader>
        <orm-reader entityName="DemoIncomingTxn">
          <query>...query conditions...</query>
        </orm-reader>
    </loader>

    <consumer name="saveToDb">
        <orm-writer entityName="DemoTransaction" allowUpdate="false">
          <keyFields>unique key fields</keyFields>
        </orm-writer>
    </consumer>
</batch>
```

**By combining NopTaskFlow, NopBatch, NopRecord, and NopORM with multiple domain models, the Nop platform can achieve complete task orchestration through declarative programming during general business development, without requiring Java code writing.**

You can stop here to carefully think about it: in a single DSL, how is it possible to simultaneously include definitions for task types, batch tasks, and record types, while ensuring seamless integration between them? It appears that the platform provides a complete unified DSL.

1. How would you implement this without the Nop platform?
2. Can the ability to define multiple DSLs and combine them be abstracted into a general capability?
3. Does this abstracted capability impact runtime performance?
4. How is mixed DSL debugging handled? Can errors be accurately pinpointed to their DSL source code origins?
5. How can a rapid visual designer be developed for this mixed DSL? Is it possible to use Excel for DSL configuration?

### Command Line Execution

The nop-cli tool provided by the Nop platform allows direct execution of orchestration tasks.

1. In the _vfs directory, introduce files like `app.orm.xml` and `batch-demo.task.xml`, and the nop-cli tool will automatically load all model files from the virtual file system within the working directory.
2. Execute orchestration tasks using the command: `java -Dnop.config.location=application.yaml -jar nop-cli.jar run-task v:/batch/batch-demo.task.xml -i="{bizDate:'2024-12-08'}"`.
* The first argument to `run-task` specifies the path to the orchestration model file. The `v:` indicates it is within the `_vfs` virtual file system. You can also specify a direct file path.
* The `-i` parameter provides input parameters for the orchestration task in JSON format. Alternatively, you can use `-if=filePath` to specify an input data file containing a JSON object.
* Use `-Dnop.config.location` to specify configuration files, which can include database connection passwords.

The TestNopCli test suite provides functions like `testBatchGenDemo`, allowing you to familiarize yourself with the NopBatch engine through debugging these test cases.

## Four. Multiple Facets of DSL

A fundamental difference between Nop platform and other platforms is that Nop not only includes some common DSLs but also provides a comprehensive Language-Oriented Programming (LOP) infrastructure. This infrastructure is designed for rapid development or extension of existing DSLs. Each DSL does not merely have a unique expression form but can be represented in multiple forms, including Excel, visual editing, JSON, and XML, with seamless conversion between these formats.


In the `record-file.register-model.xml` file, multiple loaders are defined for the message model.

```xml
<model x:schema="/nop/schema/register-model.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       name="record-file">

    <loaders>
        <xdsl-loader fileType="record-file.xml" schemaPath="/nop/schema/record/record-file.xdef"/>
        <xlsx-loader fileType="record-file.xlsx" impPath="/nop/record/imp/record-file.imp.xml" />
    </loaders>

</model>
```

The configuration above indicates that files of type `xxx.record-file.xml` can be parsed into a `RecordFileMeta` object, following the `record-file.xdef` meta-model for parsing. Simultaneously, files of type `xxx.record-file.xlsx` require an `XlsxObjectLoader` to parse them, adhering to the structure defined in `record-file.imp.xml`.

In `txn.record-file.xlsx`, a fixed-length data file format is defined, comprising file header, body, and trailer.

![](batch/record-file.png)

This configuration is equivalent to:

```xml
<file x:schema="/nop/schema/record/record-file.xdef" xmlns:x="/nop/schema/xdsl.xdef" binary="true">
    <header typeRef="HeaderOObject"/>
    <body typeRef="BodyObject" />
    <trailer typeRef="TrailerObject" />
    <aggregates>
        <!-- Aggregates the `txnAmount` property across each record into a `totalAmount` variable, used in the trailer -->
        <aggregate name="totalAmount" aggFunc="sum" prop="txnAmount" />
    </aggregates>

    <types>
        <type name="HeaderObject">
           <fields>...</fields>
        </type>
        <type name="BodyObject">
           <fields>...</fields>
        </type>
        <type name="TrailerObject">
            <fields>...</fields>
        </type>
    </types>
</file>
```


### ORM Model

In the `/nop-cli/demo/_vfs/app/demo/orm` directory, a demonstration `app.orm.xml` model file is provided, illustrating an interesting NopORM model configuration.

```xml
<orm x:schema="/nop/schema/orm/orm.xdef" xmlns:x="/nop/schema/xdsl.xdef"
     orm:forceDynamicEntity="true" x:dump="true"
     xmlns:orm-gen="orm-gen" xmlns:xpl="xpl" xmlns:orm="orm">
    <x:gen-extends>
        <!-- Generates NopORM model from Excel files (`demo.orm.xlsx` and `demo-delta.orm.xlsx`) -->
        <orm-gen:GenModelFromExcel path="demo.orm.xlsx" xpl:lib="/nop/orm/xlib/orm-gen.xlib"/>
        <orm-gen:GenModelFromExcel path="demo-delta.orm.xlsx" xpl:lib="/nop/orm/xlib/orm-gen.xlib" />
    </x:gen-extends>
</orm>
```

![](batch/orm-model.png)
![](batch/demo-orm-model.png)

1. Here, `orm:forceDynamicEntity=true` is set to indicate that no entity class code needs to be generated; instead, dynamic entities are used for ORM mapping directly. This allows for direct use of ORM entity relationships in batch models without prior code generation. In the XLang language, dynamic entities can just like regular Java classes utilize property access syntax, making them fully compatible with standard entities.

2. The second interesting aspect is demonstrated by the `x:gen-extends` section, which shows an implementation for model generation and difference model merging. This approach is a completely generalizable method requiring no additional effort and is part of the underlying capabilities of the Nop platform.
   * The `orm-gen:GenModelFromExcel` tag reads from the Excel format model file (`orm.xlsx`) and parses it into an OrmModel object based on the import configuration specified in `orm.imp.xml`. It then converts this OrmModel object into an XNode node using the meta-model definition provided by `orm.xdef`.
   * The difference model is defined in `demo-delta.orm.xlsx`. When customizing the demo.orm.xlsx model file, you can avoid modifying the original file by simply adding a new Delta model file that has the exact same structure as the original (with the full model being a special case of the delta, requiring no separate design for delta-specific expressions).
   * The two `orm-gen:GenModelFromExcel` tags generate two XNode nodes. According to the XLang language specifications, multiple nodes generated by the `x:gen-extends` section are automatically merged into a single complete XNode node through difference merging. The `x:dump=true` setting causes the detailed merging process and final result to be printed in the log file during runtime.

With the above configuration, Excel can be used to define data models and model differences. Any changes made to an Excel model are immediately reflected in the system. In online systems, refreshing the page after modifying the Excel model will reload the ORM model. This is because the Nop platform implements resource file dependency tracking at the bottom layer, ensuring that model cache invalidation occurs whenever any of the referenced resource files change during compilation.

Here's also a detailed consideration: if not using the Nop platform, how would one implement similar visual model design and difference-based model definition?
* The use of Excel for visualization is not a necessity. For example, tools like [PowerDesigner](https://www.oracle.com/technologies/products/pd-doorstep/) or [PDManer](https://my.oschina.net/skymozn/blog/10858773) can be used to design ORM models. All that's needed is a tag function to implement the model conversion. The Nop platform natively supports `pdman.xlib` and PdmModelParser for adapting these two types of models.
