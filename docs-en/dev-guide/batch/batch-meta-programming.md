# Through Metamodel Implementing the Fusion of Multiple DSLs

In the Nop platform, we define a Record model specifically designed for data message format analysis and generation. However, this model is not exclusively tailored for batch file processing but can be utilized in any context where message analysis and generation are required. It represents a general declarative development mechanism, offering capabilities far surpassing those of SpringBatch's FlatFile configuration.


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

            <!-- Can define multiple processors in sequence -->
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

            <!-- Can define multiple consumers, and filter to consume only part of the data -->
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

Here is the translation of the Chinese technical document into English, preserving the original Markdown format, including headers, lists, and code blocks:

---

# Translating a Chinese Technical Document to English

The following English translation maintains the original Markdown format, including headers, lists, and code blocks.

---

## 1. Overview of NopTaskFlow Engine

In the example provided above:

- **NopTaskFlow** logic orchestration engine does not have any knowledge about batch processing tasks or built-in Record models during its design.
- **Extending NopTaskFlow** does not require implementing any specific extension interfaces within the NopTaskFlow engine nor using any internal registration mechanisms for extension steps.
- To extend **NopTaskFlow**, you only need to analyze the `task.xdef` meta-model to understand the structure of the NopTaskFlow logic orchestration model. This allows you to use the built-in meta-programming mechanism in XLang to implement extensions.

---

## 2. Extending with XML

- The extension is achieved using the following XML:

```xml
<x:extends="/nop/task/lib/common.task.xml,/nop/task/lib/batch-common.task.xml"/>
```

This introduces base models that can be transformed using meta-programming mechanisms like `<x:post-extends>` at the XNode level.

---

## 3. Custom Elements Translation

The custom element `customType` is translated into the tag name in the XPL language, and the corresponding element is converted to `<batch:Execute>`.

```xml
<custom name="test" customType="batch:Execute" xmlns:batch="xxx.xlib"/>
```

This translates to:

```xml
<xpl>
    <source>
        <batch:Execute xpl:lib="xxx.xlib">
            <slotName>...</slotName>
        </batch:Execute>
    </source>
</xpl>
```

---

## 4. Batch Processing Support

- The `<batch:Execute>` tag is parsed at compile time and converted into an `IBatchTaskBuilder` instance, which can directly access compile-time variables without further parsing.
- All XDSL elements support extension attributes and nodes. By default, namespace-aware attributes and nodes are not subject to XDef model validation.

---

## 5. Defining File Models

- The following XML defines a file model:

```xml
<file-writer record:file-model="SimpleFile">
</file-writer>
```

This translates to:

```xml
<file-writer>
    <newRecordOutputProvider>
        <!-- Accessing compile-time variables in XPL -->
        <batch-record:BuildRecordOutputProviderFromFileModel fileModel="#{SimpleFile}"
                xpl:lib="/nop/batch/xlib/batch-record.xlib"/>
    </newRecordOutputProvider>
</file-writer>
```

---

## 6. Processing Individual Records

- In a specific step, **NopTaskFlow** calls **BatchTask**, and the same mechanism can be used within **BatchTask's Processor** to call **NopTaskFlow** for processing individual records.

```xml
<processor name="processor2" task:taskModelPath="process-item.task.xml">
</processor>
```

This translates to:

```xml
<processor name="processor2">
    <source>
        <task:Execute taskModelPath="process-item.task.xml"
                inputs="${{item, consume, batchChunkCtx}}"
                xpl:lib="/nop/task/xlib/task.xlib"/>
    </source>
</processor>
```

---


11. In terms of database access, NopORM provides a complete ORM model with features such as multitenancy, logical deletion, field encryption, flexible transaction handling, associated data queries, batch loading, and batch saving optimization. These capabilities are achieved through the use of `orm-reader` for reading from the database and `orm-writer` for writing to it.

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

**Combining NopTaskFlow, NopBatch, NopRecord, and NopORM, the Nop platform allows for the implementation of batch processing tasks in general business development through declarative configuration. This eliminates the need to manually write Java code for these operations.**
