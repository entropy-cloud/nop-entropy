# null
```

### Batch Processing Task Model

The batch processing model includes configurations for `loader`, `processor`, and `consumer`.


## Configuration Parameters

- **taskName**: Specifies the name of the task. 
  - Default: `test.loadData`
  
- **batchSize**: Specifies the number of items to process in each batch.
  - Default: `100`

- **saveState**: Determines whether the state is saved between batches.
  - Default: `true`

- **concurrency**: Specifies the number of parallel threads for processing.
  - Default: `5`

- **executor**: Specifies the thread pool to use for execution.
  - Default: `nop-global-worker`
  - Corresponds to a thread pool registered in `GlobalWorkers` or managed by `BeanContainer`.



- **taskKeyExpr**: Unique identifier for the task key.
  - Each task key can only have one active instance running.



- A batch task typically includes:
  - One `loader`
  - Multiple `processors`
  - Multiple `consumers`



- If `file-reader` is not configured for `fileModelPath`, the default behavior reads files in CSV format.



- **processor1**: 
  ```xml
  <source>
    consume(item);
  </source>
  ```

- **processor2**:
  ```xml
  <!--
  <source>
    <task:Execute taskModelPath="process-item.task.xml" inputs="${{item,consume,batchChunkCtx}}" xpl:lib="/nop/task/xlib/task.xlib"/>
  </source>
  -->
  ```



- **all consumer**:
  ```xml
  <file-writer filePath="dev:/target/output/${bizDate}-all.dat" fileModelPath="SimpleFile"/>
  ```

- **selected consumer**:
  ```xml
  <filter>
    return item.quantity > 500;
  </filter>
  ```
  ```xml
  <file-writer filePath="dev:/target/output/${bizDate}-selected.dat" fileModelPath="simple.record-file.xml"/>
  ```



- If `dispatcher` is configured for the `loader`, `PartitionDispatchLoaderProvider` will be used to enable partition loading.

  
  1. Start multiple internal threads to invoke the internal `IBatchLoader` for data loading, continuously adding data to the partitioned task queue managed by `PartitionDispatchQueue`.
  2. Use `loadBatchSize` to control the amount of data loaded each time.
  3. Specify a partition field using `partitionIndexField`, where records with identical values will be assigned to the same task queue and managed centrally via `PartitionDispatchQueue`.
  4. The `BatchTask` runs by invoking the `loader` from `PartitionDispatchQueue`.

  ## File Reading and Writing
  
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

  * Through `fileModelPath`, data can be parsed and generated based on the `Record` model.
  * If `fileModelPath` is not specified, the default format will be CSV for reading and writing.

  ## ORM Reading and Writing
  
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
      <!-- Insert data into database after reading from files -->
      <orm-writer entityName="DemoIncomingTxn" allowInsert="true" allowUpdate="false">
        <keyFields>cardNumber, txnAmount, txnTime</keyFields>
      </orm-writer>
    </consumer>
  </batch>
  ```

  * `query`: Can be used to specify query conditions during reading.
  * `keyFields`: If configured, unique keys will be checked during insertion to avoid duplicates.
  * `allowInsert`: Allows inserting new data if not exists, default is true.
  * `allowUpdate`: Prevents updating existing records, default is false.

  ## Parameter Transmission
  
  1. Both `IBatchTaskContext` and `IBatchChunkContext` provide `setAttribute/getAttribute` methods for sharing data within the batch processing environment and individual chunk contexts.
  
  ```xml
  <processor>
    <source>
      const myVar = batchChunkCtx.taskContext.getAttribute('myVar');
    </source>
  </processor>
  ```
  
  2. When `saveState` attribute is enabled in `BatchTask`, data can be persisted using `IBatchTaskContext.getPersistVar/setPersistVar`.
  
  ## Expansion

  
  * `loader/consumer` and similar components support bean configuration. You can directly specify beans from the IOC container to implement loaders and consumers.
  * `file-reader` and `file-writer` components support resourceLocator configuration, allowing you to map resource paths to IResource objects. By default, a ZipResourceLocator is used, which automatically identifies entries like `/a.zip!/entryNameInZip.txt`. This allows loading specific files from zip or jar archives.
  * On Windows systems, compressed zip files are often encoded using GBK by default, while on Linux systems, they are typically encoded using UTF-8. Therefore, if a zip file generated on Windows contains Chinese filenames, it may cause reading errors on Linux systems. To resolve this, you can explicitly specify the encoding when accessing such files: `/a.zip!/entryNameInZip.txt?encoding=GBK`.
  
  ## AsyncProcessor
  By default, processors operate synchronously. If the root node in `batch.xml` has `asyncProcessor="true"`, asynchronous processing is enabled, requiring the processor to call `batchChunkCtx.countDown()` upon completion.
  The `asyncProcessTimeout` property allows you to configure the waiting time for asynchronous execution, with a default value of 10 minutes.
  
  ## Common Issues
  
  ### 1. Mapping Columns in Data Files
  Suppose your data file has two columns named "a" and "b". You want to import this file into database tables T1 and T2, mapping column "a" to field "c1" in T1 and column "b" to field "c1" in T2. How should the mapping relationships be handled?
  
  If using an ORM for persistence, you can directly use the `dao.xlib` component's `SaveEntity` tag in your processor. This will automatically delay database commits, performing only a call to `ormSession.save`.
  
  ### Example XML Configuration:
  ```xml
  <processor name="saveCustomer">
    <source>
      <dao:SaveEntity entityName="DemoCustomer" data="${
        firstName: item.customer.firstName,
        lastName: item.customer.familyName,
        gender: item.customer.gender,
        customerNumber: item.customerNumber,
        idCard: item.customer.idCard,
        partitionIndex: item.customerNumber.$shortHash()
      }"
        xpl:lib="/nop/orm/xlib/dao.xlib"/>
    </source>
  </processor>
  
  ```
  This configuration allows the processor to call `consume(result1)` and `consume(result2)` multiple times, outputting different results. You can then filter these results in a writer component.
  
  ### Data Filtering Example:
  ```xml
  <batch>
    ...
    <processor name="process">
      <source>
        consume(result1);
        consume(result2);
      </source>
    </processor>

    <consumer name="saveResult1">
      <filter>
        return item.name == 'result1';
      </filter>

      <file-writer filePath="result.csv"/>
    </consumer>
  </batch>
