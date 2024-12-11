# 通过元模型实现多个DSL的融合

在Nop平台中，我们定义了一种专用于数据消息格式解析和生成的Record模型，但它并不是为批处理文件解析专门设计，而是可以用于所有需要消息解析和生成的地方，是一种通用的声明式开发机制，而且能力远比SpringBatch中的FlatFile配置强大。

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

                <!-- 可以定义多个processor，它们按顺序执行 -->
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

                <!-- 可以定义多个consumer，然后通过filter段来控制只消费一部分输出数据 -->
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

在上面的示例中，演示了在NopTaskFlow中如何无缝嵌入Batch批处理模型和Record消息格式定义。

1. NopTaskFlow逻辑编排引擎在设计的时候并没有任何关于批处理任务的知识，也没有内置Record模型。
2. 扩展NopTaskFlow并不需要实现某个NopTaskFlow引擎内部的扩展接口，也不需要使用NopTaskFlow内部的某种注册机制注册扩展步骤。
3. 只需要查看`task.xdef`元模型，了解NopTaskFlow逻辑编排模型的节点结构，就可以使用XLang语言内置的元编程机制实现扩展。
4. `x:extends="/nop/task/lib/common.task.xml,/nop/task/lib/batch-common.task.xml"`引入了基础模型支持，这些基础模型通过`x:post-extends`等元编程机制在XNode结构层对当前模型进行结构变换。
5. `<custom name="test" customType="batch:Execute" xmlns:batch="xxx.xlib">` 扩展节点的customType会被自动识别为Xpl 标签函数，并将custom节点变换为对`<batch:Execute>`标签函数的调用。

```xml
<custom customType="ns:TagName" xmlns:ns="libPath" ns:argName="argValue">
  <ns:slotName>...</ns:slotName>
</custom>
会被自动变换为

<xpl>
    <source>
        <ns:TagName xpl:lib="libPath" argName="argValue">
            <slotName>...</ns:slotName>
        </ns:TagName>
    </source>
</xpl>
```

也就是说，customType是具有名字空间的标签函数名。所有具有相同名字空间的属性和子节点都会作为该标签的属性和子节点。

7. `<batch:Execute>`标签会在编译期解析自己的task节点，构造出IBatchTaskBuilder，在运行期可以直接获取到编译期变量，不用再重复解析。
8. 所有的XDSL都自动支持扩展属性和扩展节点，缺省情况下带名字空间的属性和节点不会参与XDef元模型检查。所以在task节点下可以引入自定义的`<record:file-model>`模型定义，它会被`batch-common.task.xml`引入的元编程处理器自动解析为RecordFileMeta模型对象，并保存为编译期的一个变量。
9. `file-reader`和 `file-writer`节点上的`record:file-model`属性会被识别，并自动转换。

```xml
<file-writer record:file-model="SimpleFile">
</file-writer>
被变换为

<file-writer>
    <newRecordOutputProvider>
      <!-- Xpl模板语言中#{xx}表示访问编译期定义的变量 -->
       <batch-record:BuildRecordOutputProviderFromFileModel fileModel="#{SimpleFile}"
                xpl:lib="/nop/batch/xlib/batch-record.xlib"/>
    </newRecordOutputProvider>
</file-writer>
```

10. NopTaskFlow在某个步骤中调用BatchTask，在BatchTask的Processor中我们可以使用同样的方式来调用NopTaskFlow来实现针对单条记录的处理逻辑。

```xml
<processor name="processor2" task:taskModelPath="process-item.task.xml">
</processor>

会被变换为
<processor name="processor2">
    <source>
        <task:Execute taskModelPath="process-item.task.xml"
                 inputs="${{item,consume,batchChunkCtx}}"
                 xpl:lib="/nop/task/xlib/task.xlib"/>
    </source>
</processor>
```

11. 在数据库存取方面，NopORM提供了完整的ORM模型支持，内置多租户、逻辑删除、字段加解密、柔性事务处理、数据关联查询、批量加载和批量保存优化等完善的数据访问层能力。通过orm-reader和orm-writer可以实现数据库读写。

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


**结合NopTaskFlow、NopBatch、NopRecord和NopORM等多个领域模型，Nop平台就可以做到在一般业务开发时完全通过声明式的方式实现批处理任务，而不需要编写Java代码**。
