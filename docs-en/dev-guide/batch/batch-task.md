# Batch Task Model

The batch model includes configurations for loader, processor, and writer.

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

* taskKeyExpr: For the same taskName, different taskKeyExpr values will produce different task instances. Only one active instance is allowed to run per taskKey.
* batchSize: The amount of data processed each time.
* concurrency: How many threads to process in parallel.
* executor: Which thread pool to use to execute tasks. By default, the global nop-cached-thread-pool is used. The executor name corresponds to a thread pool registered in GlobalWorkers or an Executor managed by BeanContainer.
* Each batch task contains one loader, multiple processors, and multiple consumers. Both processor and consumer are optional.

### CSV File Read/Write

If file-reader does not configure fileModelPath, it will default to reading and writing in CSV format.

* csvFormat Configures the format of the CSV file, with DEFAULT as the default. Refer to the CSVFormat class for supported formats.
* headers Used to specify the field names obtained during parsing. By default, headers from the first line of the file are used. If specified, the header in the first line will be ignored, and the header specified here will take precedence.
* headerLabels If headerLabels is specified, only columns matching headerLabels will be retained; otherwise, they will be ignored. When columns are ignored, a log will be printed:
  `nop.csv.ignore-header:header={}, allowed={}`.

## Partitioned Processing

If the loader is configured with a dispatcher, PartitionDispatchLoaderProvider will be used to implement partitioned loading.

```xml

<batch>
  <loader>
    <orm-reader entityName="DemoIncomingTxn">

    </orm-reader>

    <dispatcher loadBatchSize="100" partitionIndexField="_t.partitionIndex">
    </dispatcher>

    <!-- After the reader reads the items collection, it will call the afterLoad callback to post-process the result -->
    <afterLoad>
      ...
    </afterLoad>
  </loader>
</batch>
```

1. Start multiple internal threads that invoke the internal IBatchLoader to load data, continuously placing the data into partition task queues.
2. Use loadBatchSize to control the amount of data loaded each time.
3. Use partitionIndexField to specify the partition field. Records with the same field value will be placed into a specific task queue, managed uniformly by PartitionDispatchQueue.
4. When running, BatchTask will read from PartitionDispatchQueue through the loader.

## File Read/Write

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

* Through fileModelPath, you can parse and generate file formats based on the Record model.
* If fileModelPath is not specified, it will default to reading and writing in CSV format.

## ORM Read/Write

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
    <!-- After reading data from the file, insert it into the database. When inserting, check whether it already exists; if it does, this record will be ignored -->
    <orm-writer entityName="DemoIncomingTxn" allowInsert="true" allowUpdate="false">
      <keyFields>cardNumber,txnAmount,txnTime</keyFields>
    </orm-writer>
  </consumer>

</batch>
```

* query: When reading, you can use query to specify query conditions.
* keyFields: If keyFields is configured, it will check the unique key upon writing to determine whether the data already exists.
* allowInsert: Whether to allow inserting data when it is not found; defaults to true.
* allowUpdate: Whether to allow updating data when it is found; defaults to false.

## Parameter Passing

1. IBatchTaskContext and IBatchChunkContext both provide setAttribute/getAttribute methods, which can be used to share data across the entire batch task environment and within a single chunk execution environment.

```
<processor>
  <source>
     const myVar = batchChunkCtx.taskContext.getAttribute('myVar’);
  </source>
</processor>
```

2. With BatchTask's saveState property enabled, you can persist variable information via `IBatchTaskContext.getPersistVar/setPersistVar`.

## Extensions

* `loader/consumer` etc. support bean configuration, allowing you to directly specify IoC-managed beans to implement loaders, processors, and so on.
* `file-reader` and `file-writer` support resourceLocator configuration, through which you can customize the mapping logic from resource paths to IResource objects. By default, ZipResourceLocator is used. It automatically recognizes paths in the form `/a.zip!/entryNameInZip.txt` and can load specified files from zip or jar archives.
* Zip files compressed on Windows default to GBK encoding, while on Linux they default to UTF-8. Therefore, zip files produced on Windows with Chinese filenames may fail to read on Linux. In such cases, you can specify the encoding via a URL like
`/a.zip!/entryNameInZip.txt?encoding=GBK`.

### Custom loader
loader/processor/consumer all support provider configuration, which is equivalent to implementing the corresponding IBatchLoaderProvider/IBatchProcessorProvider/IBatchConsumerProvider interfaces. Through this, you can customize loaders, processors, and consumers.

For example, `batch.xlib` provides a tag that imports Excel data according to an imp.xml model.

```xml

<loader>
  <provider>
    <batch:ImportFromExcelLoader impModelPath="test.imp.xml" filePath="test.xlsx" resultVar="data"
                                 xpl:lib="/nop/batch/xlib/batch.xlib"/>
  </provider>
</loader>
```

The loader's provider can return a List/IBatchLoader/IEvalFunction and other types of results; all of them will be automatically wrapped as an IBatchLoader interface.


## Asynchronous Processor
By default, processors execute synchronously. If `asyncProcessor=true` is configured on the root node of `batch.xml`, asynchronous processing is enabled, and the processor must call `batchChunkCtx.countDown()` internally to mark the completion of the current processor. You can configure asyncProcessTimeout to set the wait time for asynchronous execution, which defaults to 10 minutes.

## FAQs

### 1. The data file has two columns with attribute names a and b. I want to import this file into database tables T1 and T2, where attribute a maps to field c1 in table T1, and attribute b maps to field c1 in table T2. How should this mapping be handled?

If you use ORM to persist, directly call the `SaveEntity` tag in `dao.xlib` within the processor. It will automatically defer committing the database operation, only calling
`ormSession.save`.

```xml

<processor name="saveCustomer">
  <source>
    <dao:SaveEntity entityName="DemoCustomer" data="${{
        firstName: item.customer.firstName,
        lastName: item.customer.familyName,
        gender: item.customer.gender,
        customerNumber: item.customerNumber,
        idCard: item.customer.idCard,
        partitionIndex: item.customerNumber.$shortHash()
        }}" xpl:lib="/nop/orm/xlib/dao.xlib"/>
  </source>
</processor>

```

Additionally, the processor can call consume multiple times to emit multiple results. Then configure filters in the writer to selectively accept them.

```xml

<batch>
    ...
    <processor name="process">
        <source>
            ...
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
```



<!-- SOURCE_MD5:b6680ceeb43427e990e23e84cbb6a228-->
