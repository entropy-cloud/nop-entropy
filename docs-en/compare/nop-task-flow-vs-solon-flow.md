
# Design Comparison Between the NopTaskFlow and SolonFlow Logical Orchestration Engines

solon-flow is a foundational stream-processing engine built into the domestic open-source development framework Solon, which can be used in various scenarios such as business rules, decision processing, compute orchestration, and workflow approvals (here, stream processing should refer to workflow or business flow processing). The design of solon-flow is very lightweight, and the functionality implemented so far is relatively simple. Its design bears certain similarities to the logical orchestration engine NopTaskFlow built into the Nop Platform, which gives us an opportunity for comparative analysis. Through comparison, we can clearly see the essential difference between the Nop Platform and traditional development platforms—namely, how the underlying platform automatically provides a general, extensible design.

The Nop Platform is built on reversible computing theory and constructs a comprehensive set of general foundational technical infrastructure. This means that when developing any engine (such as an ORM engine, a rules engine, or a process orchestration engine), there is no need to design an extensibility mechanism for each engine separately; instead, you can directly reuse this general extensible design and its implementation. This greatly improves development efficiency, reduces development costs, and enables engines developed on the Nop Platform to be more flexible in functional extension and customization.

## 1. Simple Example: Order Discount Rules

> Example source: [Which is better, the drools rules engine or solon-flow? solon-flow concise tutorial](https://zhuanlan.zhihu.com/p/20299193626)

Solon’s concise tutorial provides a demonstration of implementing an order discount rule using solon-flow.

First, define the entity class BookOrder:

```java
@DataBean
public class Order {
    private Double originalPrice; // Order’s original price, i.e., price before discount
    private Double realPrice;     // Order’s actual price, i.e., price after discount
}
```

Second, define a YAML-format flow model file `bookDiscount.yaml`:

```yaml
id: "book_discount"
nodes:
  - type: "start"
  - id: "book_discount_1"
    when: "order.getOriginalPrice() < 100"
    task: |
      order.setRealPrice(order.getOriginalPrice());
      System.out.println("No discount");
  - id: "book_discount_4"
    when: "order.getOriginalPrice() >= 300"
    task: |
      order.setRealPrice(order.getOriginalPrice() - 100);
      System.out.println("Discount 100 yuan");
  - id: "book_discount_2"
    when: "order.getOriginalPrice() < 200 && order.getOriginalPrice() > 100"
    task: |
      order.setRealPrice(order.getOriginalPrice() - 20);
      System.out.println("Discount 20 yuan");
  - type: "end"
```

Then invoke the flow model as follows:

```javascript
 @Test
 public void testDiscount()
    FlowEngine flowEngine = FlowEngine.newInstance();
    flowEngine.load(Chain.parseByUri("classpath:flow/bookDiscount.yml"));

    BookOrder bookOrder = new BookOrder();
    bookOrder.setOriginalPrice(500);

    ChainContext ctx = new ChainContext();
    ctx.put("order", bookOrder);

    flowEngine.eval("book_discount", ctx);

    // Price changed, saved 100 yuan
    assert bookOrder.getRealPrice() == 400;
}
```

**Although the core of NopTaskFlow (the nop-task-core module) has only about 3,000 lines of code, it is a very complete logical orchestration engine, supporting advanced features such as asynchronous execution, timeout retries, and resume-from-breakpoint.** Therefore, using NopTaskFlow, it is straightforward to implement the functionality demonstrated by solon-flow.

```yaml
version: 1
steps:
  - type: xpl
    name: book_discount_1
    when: "order.getOriginalPrice() < 100"
    source: |
      order.setRealPrice(order.getOriginalPrice());
      logInfo("No discount");
  - type: xpl
    name: book_discount_4
    when: "order.getOriginalPrice() >= 300"
    source: |
      order.setRealPrice(order.getOriginalPrice() - 100);
      logInfo("Discount 100 yuan");
  - type: xpl
    name: book_discount_2
    when: "order.getOriginalPrice() >= 100 && order.getOriginalPrice() < 200"
    source: |
      order.setRealPrice(order.getOriginalPrice() - 20);
      logInfo("Discount 20 yuan");
outputs:
  - name: realPrice
    source: order.realPrice
```

Aside from differences in property names, the entire flow definition is essentially identical to solon-flow.

| Feature        | Solon-Flow                                                                  | NopTaskFlow                                                                     |
| -------------- | ---------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| Overall structure | Defines the flow using `id` and `nodes`, with nodes distinguished by `id` and `type`. | Defines the flow using `version` and `steps`, with steps distinguished by `type` and `name`. |
| Start/end nodes  | Explicitly marks the start and end of the flow with `type: "start"` and `type: "end"`. | By default, executes the steps in order; graph mode can also be enabled, in which case the next step must be explicitly specified. |
| Step definition  | Each step is defined by `id` and a `when` condition; the task is specified via the `task` field. | Each step is defined by `type` and `name`; the task is specified via the `source` field. |
| Conditional logic | Uses the `when` field to define conditions, e.g., `order.getOriginalPrice() < 100`. | Uses the `when` field to define conditions, e.g., `order.getOriginalPrice() < 100`. |
| Task execution   | Tasks are defined via the `task` field and directly embed code blocks, e.g., `order.setRealPrice(order.getOriginalPrice());`. | Tasks are defined via the `source` field and directly embed code blocks, e.g., `order.setRealPrice(order.getOriginalPrice());`. |
| Output results   | No explicit output result definition; results are indirectly returned through operations within the task code (such as `order.setRealPrice`). | Explicitly defines output results via the `outputs` field, e.g., `outputs: - name: realPrice source: order.realPrice`. |

The way to invoke NopTaskFlow is also very similar to solon-flow:

```javascript
@Test
public void testDiscount01ForYaml() {
  ITask task = taskFlowManager.loadTaskFromPath("/nop/demo/task/discount-01.task.yaml");
  ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);

  BookOrder bookOrder = new BookOrder();
  bookOrder.setOriginalPrice(500.0);

  taskRt.setInput("order", bookOrder);

  Map<String, Object> outputs = task.execute(taskRt).syncGetOutputs();
  assertEquals(400.0, outputs.get("realPrice"));

  assertEquals(400.0, bookOrder.getRealPrice());
}
```

| Feature            | Solon-Flow                                                  | NopTaskFlow                                         |
| ------------------ | ----------------------------------------------------------- | --------------------------------------------------- |
| Engine loading     | Loads the flow model file using `flowEngine.load(Chain.parseByUri)`. | Loads the flow model file using `taskFlowManager.loadTaskFromPath`. |
| Model load path    | Files in the classpath or OS directories, e.g., `classpath:flow/bookDiscount.yml` | Paths in a virtual file system, e.g., `/nop/demo/task/discount-01.task.yaml` |
| Context setup      | Sets context variables using `ChainContext.put`.            | Sets input parameters using `ITaskRuntime.setInput`. |
| Task execution     | Executes the task via `flowEngine.eval`.                    | Executes the task via `task.execute`.               |
| Execution behavior | Synchronous execution; returns results via object properties. | Supports asynchronous execution. Tasks return a collection of output variables, and of course results can also be returned via object properties. |

## 2. Built-in Extensibility Capabilities of NopTaskFlow

NopTaskFlow is very powerful: it automatically supports asynchronous execution; multiple execution modes including graph mode and stack mode; and very fine-grained variable scope control. For a detailed introduction, see [The next-generation logical orchestration engine NopTaskFlow written from scratch](https://mp.weixin.qq.com/s/2mFC0nQon_l2M82tOlJVhg). Anywhere a function is needed can be replaced with NopTaskFlow; for example, backend service functions can directly call NopTaskFlow for implementation without coding in Java. See [Implement backend service functions via NopTaskFlow logical orchestration](https://mp.weixin.qq.com/s/CMBcV9Riehlf4_Ds_BmyEw).

Beyond the domain-specific designs mentioned above (such as transactions, async, state recovery, etc.), NopTaskFlow also automatically inherits a series of extensibility mechanisms that come with the XLang language from the Nop Platform.

### 2.1 Unified Model Loader

The solon-flow example demonstrates loading model files from the classpath, whereas NopTaskFlow loads model files using the Nop Platform’s unified ResourceComponentManager.loadComponentModel. It automatically recognizes virtual file paths, and the actual storage location of virtual files can be the classpath, a directory in the operating system, or a model table in a database. The model loading process automatically caches parsed results and automatically tracks model file dependencies. For example, if `a.task.xml` uses the xpl tag library `batch.xlib`, then when `batch.xlib` is modified, the corresponding model cache for `a.task.xml` will automatically be invalidated.

The virtual file system’s capabilities can be shared by other DSLs. For instance, ORM model files are loaded based on the same mechanism. If we add Redis storage support for the virtual file system, both ORM models and NopTaskFlow models immediately gain that support. In solon-flow’s approach, such underlying mechanisms cannot be shared across multiple DSL models.

### 2.2 Delta Customization

DSL models managed by the unified model loader in the Nop Platform automatically support Delta customization. When we need to modify model content, we do not need to directly change the original model file. Instead, we can add a model file with the same name under the `_delta` directory, which will override the original model file, and files in the delta directory are loaded with priority. In the customization file, you can use `x:extends='super'` to indicate inheritance from an existing model file.

Delta customization is similar to Docker’s layered filesystem, where files in upper layers automatically override files with the same name in lower layers. However, with the XLang language’s `x:extends` syntax, model files in the Nop Platform can implement merging of the internal structures of two model files, rather than only overriding the whole file by filename as Docker does.

For a detailed introduction, see [How to implement customized development without modifying the base product’s source code](https://mp.weixin.qq.com/s/JopDTYBIw0_Pmp0ZsTuMpA)

### 2.3 Delta Merging

All DSL models in the Nop Platform support the `x:extends` and `x:gen-extends` syntax, which can be used to implement decomposition and merging of complex models. For example:

```yaml
x:extends: "base.task.xml"
x:gen-extends: |
   <task-gen:GenAppWorkflow bizObjName="XXX"/>

steps:
   - type: step
     name: step2
     x:override: remove
```

`x:extends` indicates inheritance from existing model files; multiple base files can be specified, separated by commas, and they will be merged in order from first to last.

You can use `x:gen-extends` and `x:post-extends` to run compile-time code generation logic that dynamically produces base models to inherit from.

During the merge process, you can control the merge logic for specific nodes using `x:override`. For example, `x:override: remove` indicates deleting that node. In this way, deletion semantics and the notion of inverse elements can be introduced.

For a detailed introduction, see [XDSL: A general design for domain-specific languages](https://mp.weixin.qq.com/s/usInt7_odzvFzuiIUPw4iQ)

### 2.4 Multiple Representations

Reversible computing theory emphasizes that the same information can have multiple forms of representation, and because these forms are information-equivalent, they can be freely and reversibly transformed. For example, what is called “visual design” can be seen as a visual representation of a DSL model, whereas DSL code text is a textual representation of the model. The reason visual design is possible is precisely because visual and textual representations can be transformed into each other.

```
Visual representation = Designer(textual representation),   Textual representation = Serializer(visual representation)
```

Using the Nop Platform’s built-in mechanisms and the composability of reversibility, multiple representations at the field level can automatically infer multiple representations at the object level, thereby automatically enabling visual editing of model objects. For instance, for solon-flow, if you want to develop a visual designer, you need to specifically design and implement the designer tailored to solon-flow’s syntax. In the Nop Platform, however, we can automatically generate a visual designer dedicated to logical orchestration based on meta-model definitions, without having to write a designer for the orchestration engine itself.

As a simple example, all models in the Nop Platform automatically support multiple syntactic representations, such as XML and YAML, which can express the same business logic in different forms.

```xml
<task version="1" x:schema="/nop/schema/task/task.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <steps>
        <xpl name="book_discount_1">
            <when><![CDATA[
                order.getOriginalPrice() < 100
            ]]></when>
            <source>
                order.setRealPrice(order.getOriginalPrice());
                logInfo("No discount");
            </source>
        </xpl>

        <xpl name="book_discount_4">
            <when><![CDATA[
                order.getOriginalPrice() >= 300
            ]]></when>
            <source>
                order.setRealPrice(order.getOriginalPrice() - 100);
                logInfo("Discount 100 yuan");
            </source>
        </xpl>

        <xpl name="book_discount_2">
            <when><![CDATA[
               order.getOriginalPrice() >= 100 && order.getOriginalPrice() < 200
            ]]></when>
            <source>
                order.setRealPrice(order.getOriginalPrice() - 20);
                logInfo("Discount 20 yuan");
            </source>
        </xpl>
    </steps>

    <output name="realPrice">
        <source>order.realPrice</source>
    </output>
</task>
```

The information expressed in this XML is exactly the same as the information expressed in YAML format in the previous section.

In the Nop Platform, another representation available out-of-the-box is Excel. For any DSL model, without writing Excel parsing and generation code, you can configure complex domain model objects via Excel. For example, ORM and API models are typically managed via Excel rather than via `orm.xml` and `api.xml` model files. The Excel models can be automatically converted into the corresponding XML model files.

## 3. Rules Model

In addition to NopTaskFlow logical orchestration, the Nop Platform also provides a rules model (NopRule) dedicated to describing complex decision logic, which can be used to define decision tables or decision trees. Compared to NopTaskFlow, NopRule focuses on logical predicates; through tree and matrix structures, it can reuse conditions, thereby simplifying configuration and optimizing performance. For example, in a decision tree, after conditions are evaluated at upper-level nodes, those conditions do not need to be repeated at lower-level nodes. The rules model also introduces concepts such as weighted averages, which can directly map to business-level tools like scorecards, making it simpler than using orchestration directly.

Configuring the same discount rules using NopRule:

```xml
<rule ruleVersion="1" x:schema="/nop/schema/rule.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <input name="order" mandatory="true"/>
    <output name="discount" mandatory="true" type="Double"/>

    <decisionTree>
        <children>
            <child id="discount-1" label="Price less than 100">
                <predicate>
                    <lt name="order.originalPrice" value="100" />
                </predicate>
                <output name="discount">
                    <valueExpr>0</valueExpr>
                </output>
            </child>

            <child id="discount-4" label="Price greater than 300">
                <predicate>
                    <ge name="order.originalPrice" value="300" />
                </predicate>

                <output name="discount">
                    <valueExpr>100</valueExpr>
                </output>
            </child>

            <child id="discount-2">
                <output name="discount">
                    <valueExpr>20</valueExpr>
                </output>
            </child>
        </children>
    </decisionTree>
</rule>
```

Besides XML, we can also use Excel format to configure decision tables and decision trees:
![](nop/nop-rule.png)

In the Nop Platform, we can also import Excel models into the database, edit and adjust rules online via a web page, and debug online.

For a detailed introduction to NopRule, see [NopRule: An open-source rules engine that uses Excel as a visual designer](https://mp.weixin.qq.com/s/zJvovUG2f4mjB5CbrlX6RA)

## 4. Model Nesting

An essential difference between the Nop Platform and all other platforms and frameworks is that it does not develop some underlying engine in isolation; instead, it abstracts the underlying technical architecture for all engines in one go. All engines share the same XLang language and support for reversible computing. DSLs defined using XLang do not need to consider extensibility issues themselves (nor design related syntax), and they also do not need to worry about how multiple DSLs are seamlessly integrated and used together.

As introduced earlier, NopTaskFlow and NopRule are two DSL models. Through the xpl template language, we can seamlessly integrate and use the two DSL models together by simply implementing an xpl tag function (similar to a Vue component).

```xml
<task version="1" x:schema="/nop/schema/task/task.xdef" xmlns:x="/nop/schema/xdsl.xdef"
      xmlns:rule="rule" xmlns:xpl="xpl" xmlns:c="c">
    <steps>
        <xpl name="calcDiscount">
            <source>
               <rule:Execute ruleModelPath="/nop/demo/rule/discount.rule.xlsx"
                             inputs="${{order}}" xpl:return="outputs"
                             xpl:lib="/nop/rule/xlib/rule.xlib" />
               <c:script>
                    order.setRealPrice(order.originalPrice - outputs.discount);
               </c:script>
            </source>
        </xpl>
    </steps>

    <output name="realPrice">
        <source>order.realPrice</source>
    </output>
</task>
```

In the example above, we call the `discount.rule.xlsx` rules model via the `<rule:Execute>` tag function to calculate the discount, and store the result in the `outputs` variable. In this way, we can maintain business rules in Excel and invoke Excel-formatted business rules directly in business code.

The implementation of `rule.xlib` is also very simple:

```xml
<lib x:schema="/nop/schema/xlib.xdef" xmlns:x="/nop/schema/xdsl.xdef" xmlns:c="c" xmlns:xpl="xpl"
>

    <tags>

        <Execute>
            <attr name="ruleModelPath" stdDomain="v-path" optional="true" />
            <attr name="ruleName" type="String" optional="true"/>
            <attr name="ruleVersion" type="Long" optional="true"/>
            <attr name="inputs" type="Map" optional="true" />

            <attr name="svcCtx" type="io.nop.core.context.IServiceContext" implicit="true" optional="true"/>

            <source><![CDATA[
                const ruleManager = inject('nopRuleManager');
                const rule = ruleModelPath? ruleManager.loadRuleFromPath(ruleModelPath) :
                            ruleManager.getRule(ruleName,ruleVersion);

                const ruleRt = ruleManager.newRuleRuntime(svcCtx, $scope);
                if(inputs != null){
                    ruleRt.setInputs(inputs);
                }
                return rule.executeForOutputs(ruleRt);
            ]]></source>
        </Execute>

    </tags>
</lib>
```

You can directly specify `ruleModelPath`, or specify `ruleName` and `ruleVersion` to dynamically determine the rules model object (load from the virtual file system, and if not found, attempt to load from the database).

The Nop Platform does not offer a single-function DSL, but rather a collection of DSLs—a so-called DSL forest. For a detailed introduction to the DSL forest, see [How does Nop overcome the limitation that DSLs can only apply to specific domains?](https://mp.weixin.qq.com/s/6TOVbqHFmiFIqoXxQrRkYg), and for more detailed examples, see [Why is SpringBatch a poor design?](https://mp.weixin.qq.com/s/1F2Mkz99ihiw3_juYXrTFw).

The low-code platform NopPlatform, designed based on reversible computing theory, is open source:

- gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- gitcode: [canonical-entropy/nop-entropy](https://gitcode.com/canonical-entropy/nop-entropy)
- Development examples: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Principles of Reversible Computing and Nop Platform Introduction & Q&A\_bilibili](https://www.bilibili.com/video/BV14u411T715/)
- International site: [https://nop-platform.github.io/](https://nop-platform.github.io/)
- Nop development practice sharing site by Crazydan Studio: [https://nop.crazydan.io/](https://nop.crazydan.io/)

<!-- SOURCE_MD5:9b58b32ebe0e6e4d85fae1b7e8033154-->
