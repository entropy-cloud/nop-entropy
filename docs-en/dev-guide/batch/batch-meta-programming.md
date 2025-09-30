# Fusion of Multiple DSLs via Meta-Models

In the Nop platform, we define a Record model specialized for parsing and generating data message formats. It is not designed specifically for batch file parsing; instead, it can be used wherever message parsing and generation are required. It is a general declarative development mechanism and far more powerful than the FlatFile configuration in SpringBatch.

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
        <step name="test" customType="batch:Execute" useParentScope="true"
                xmlns:batch="/nop/batch/xlib/batch.xlib">
            <batch:task taskName="test.loadData" batchSize="100" saveState="true">

                <taskKeyExpr>bizDate</taskKeyExpr>

                <loader>
                    <file-reader filePath="dev:/target/input/${bizDate}.dat"
                        fileModelPath="simple.record-file.xlsx" />
                </loader>

                <!-- Multiple processors can be defined; they execute in order -->
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

                <!-- Multiple consumers can be defined, and the filter section controls consuming only a subset of the output data -->
                <consumer name="selected">
                    <filter>
                        return item.quantity > 500;
                    </filter>

                    <file-writer filePath="dev:/target/output/${bizDate}-selected.dat"
                       fileModelPath="simple.record-file.xml"/>
                </consumer>

            </batch:task>
        </step>
    </steps>
</task>
```

The example above demonstrates how to seamlessly embed the Batch processing model and the Record message format definition within NopTaskFlow.

1. The NopTaskFlow orchestration engine was designed without any knowledge of batch tasks and without a built-in Record model.
2. Extending NopTaskFlow does not require implementing any internal extension interfaces of the NopTaskFlow engine, nor registering extension steps via its internal registration mechanisms.
3. You only need to review the `task.xdef` meta-model to understand the node structure of the NopTaskFlow orchestration model, then use XLang’s built-in metaprogramming mechanisms to implement extensions.
4. `x:extends="/nop/task/lib/common.task.xml,/nop/task/lib/batch-common.task.xml"` introduces base model support; these base models transform the current model at the XNode structural layer via metaprogramming mechanisms such as `x:post-extends`.
5. `<step name="test" customType="batch:Execute" xmlns:batch="xxx.xlib">` The customType of the extension node is automatically recognized as an Xpl tag function, and the custom node is transformed into a call to the `<batch:Execute>` tag function.

```xml
<step customType="ns:TagName" xmlns:ns="libPath" ns:argName="argValue">
  <ns:slotName>...</ns:slotName>
</step>
will be automatically transformed into

<xpl>
    <source>
        <ns:TagName xpl:lib="libPath" argName="argValue">
            <slotName>...</ns:slotName>
        </ns:TagName>
    </source>
</xpl>
```

In other words, customType is a namespace-qualified tag function name. All attributes and child nodes sharing the same namespace are treated as that tag’s attributes and child nodes.

7. The `<batch:Execute>` tag parses its task node at compile time to construct an IBatchTaskBuilder; at runtime it can directly access compile-time variables without re-parsing.
8. All XDSLs automatically support extension attributes and nodes; by default, namespaced attributes and nodes are excluded from XDef meta-model validation. Therefore, under the task node you can introduce a custom `<record:file-model>` model definition, which will be automatically parsed by the metaprogramming processor introduced via `batch-common.task.xml` into a RecordFileMeta model object and stored as a compile-time variable.
9. The `record:file-model` attribute on the `file-reader` and `file-writer` nodes will be recognized and automatically transformed.

```xml
<file-writer record:file-model="SimpleFile">
</file-writer>
will be transformed into

<file-writer>
    <newRecordOutputProvider>
      <!-- In the Xpl template language, #{xx} refers to accessing a compile-time defined variable -->
       <batch-record:BuildRecordOutputProviderFromFileModel fileModel="#{SimpleFile}"
                xpl:lib="/nop/batch/xlib/batch-record.xlib"/>
    </newRecordOutputProvider>
</file-writer>
```

10. NopTaskFlow invokes a BatchTask in a given step, and within the BatchTask’s Processor we can use the same approach to invoke NopTaskFlow to implement per-record processing logic.

```xml
<processor name="processor2" task:taskModelPath="process-item.task.xml">
</processor>

will be transformed into
<processor name="processor2">
    <source>
        <task:Execute taskModelPath="process-item.task.xml"
                 inputs="${{item,consume,batchChunkCtx}}"
                 xpl:lib="/nop/task/xlib/task.xlib"/>
    </source>
</processor>
```

11. For database access, NopORM provides comprehensive ORM model support with built-in multi-tenancy, logical deletion, field encryption/decryption, flexible transaction handling, relational querying, batch loading, and batch save optimizations—delivering a robust data access layer. Database reads and writes can be achieved via orm-reader and orm-writer.

```xml
<batch>
    <loader>
        <orm-reader entityName="DemoIncomingTxn">
        </orm-reader>
    </loader>

    <consumer name="saveToDb">
        <orm-writer entityName="DemoTransaction">
        </orm-writer>
    </consumer>
</batch>
```

**By combining multiple domain models such as NopTaskFlow, NopBatch, NopRecord, and NopORM, the Nop platform enables batch tasks to be implemented entirely declaratively in typical business development, without writing any Java code.**
<!-- SOURCE_MD5:544d53e56691e6a304e172183eff7147-->
