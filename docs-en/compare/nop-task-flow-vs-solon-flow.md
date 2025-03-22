# Comparison of NopTaskFlow and SolonFlow Logic Engines

solon-flow is a domestically produced open-source development framework with an embedded foundational flow engine. It can be used for business rules, decision-making, orchestration, and process approval across various scenarios (where "flow processing" refers to workflow or business process management). The design of solon-flow is lightweight, with implemented functions being relatively basic. Its architecture shares some similarities with Nop's built-in logic orchestration engine, NopTaskFlow. This comparison provides an opportunity for analysis, allowing us to clearly see the fundamental differences between the Nop platform and traditional development platforms in terms of underlying design.

The Nop platform is based on reversible computation theory and has built a comprehensive technological infrastructure. This means that when developing any engine (such as ORM engines, rule engines, or orchestration engines), you don't need to design separate scalable mechanisms for each engine; instead, you can directly reuse the common scalable design provided by Nop. This significantly improves development efficiency and reduces development costs, enabling engines developed on the Nop platform to be more flexible in functionality extension and customization.

## 1. Simple Example: Order Discount Rules

The following example is based on a simple discount rule for an order. It draws from a brief guide provided by solon-flow (see [Drools Rule Engine vs. solon-flow: Which is Better?](https://zhuanlan.zhihu.com/p/20299193626)).

First, define the BookOrder entity:

```java
@DataBean
public class Order {
    private Double originalPrice; // Original price, i.e., pre-discount price
    private Double realPrice;   // Discounted price, i.e., post-discount price
}
```

Next, define a YAML workflow model file `bookDiscount.yaml`:

```yaml
id: "book_discount"
nodes:
  - type: "start"
  - id: "book_discount_1"
    when: "order.originalPrice < 100"
    task: |
      order.realPrice = order.originalPrice;
      System.out.println("No discount");
  - id: "book_discount_4"
    when: "order.originalPrice >= 300"
    task: |
      order.realPrice = order.originalPrice - 100;
      System.out.println("Discounted by 100");
  - id: "book_discount_2"
    when: "order.originalPrice < 200 && order.originalPrice > 100"
    task: |
      order.realPrice = order.originalPrice - 20;
      System.out.println("Discounted by 20");
  - type: "end"
```

Then, invoke the workflow using the following JavaScript test case:

```javascript
@Test
public void testDiscount() {
    FlowEngine flowEngine = FlowEngine.newInstance();
    flowEngine.load(Chain.parseByUri("classpath:flow/bookDiscount.yml"));

    BookOrder bookOrder = new BookOrder();
    bookOrder.setOriginalPrice(500);

    ChainContext ctx = new ChainContext();
    ctx.put("order", bookOrder);

    flowEngine.eval("book_discount", ctx);

    // Assert that the discount is applied correctly
    assert bookOrder.getRealPrice() == 400;
}
```

## Key Features of NopTaskFlow

While NopTaskFlow's core module, nop-task-core, consists of approximately 3,000 lines of code, it is a complete logic orchestration engine supporting asynchronous processing, timeout retries, and other advanced features. This makes it easy to implement the capabilities demonstrated by solon-flow.

  
```yaml
version: 1
steps:
  - type: xpl
    name: book_discount_1
    when: "order.getOriginalPrice() < 100"
    source: |
      order.setRealPrice(order.getOriginalPrice());
      logInfo("没有优惠");
  - type: xpl
    name: book_discount_4
    when: "order.getOriginalPrice() >= 300"
    source: |
      order.setRealPrice(order.getOriginalPrice() - 100);
      logInfo("优惠100元");
  - type: xpl
    name: book_discount_2
    when: "order.getOriginalPrice() >= 100 && order.getOriginalPrice() < 200"
    source: |
      order.setRealPrice(order.getOriginalPrice() - 20);
      logInfo("优惠20元");
outputs:
  - name: realPrice
    source: order.realPrice
```

除了属性名的差异之外，整个流程定义基本与solon-flow完全一致。
 
 | Feature      | Solon-Flow        | NopTaskFlow         |
| ----------- | ----------------- | ------------------ |
| **Overall Structure** | Uses `id` and `nodes` to define the process, distinguishing nodes with `id` and `type`. | Uses `version` and `steps` to define the process, distinguishing steps with `type` and `name`. |
| **Start/End Node** | Identifies the start and end of the process using `type: "start"` and `type: "end"`. | Determines the start and end of the process by default following the order of steps, or switches to graph mode requiring explicit next step specification. |
| **Step Definition** | Each step is defined by `id` and `when` conditions, with tasks specified via the `task` field. | Each step is defined by `type` and `name`, with tasks specified via the `source` field. |
| **Condition Judgement** | Uses the `when` field to define conditions, such as `order.getOriginalPrice() < 100`. | Uses the `when` field to define conditions, such as `order.getOriginalPrice() < 100`. |
| **Task Execution** | Tasks are defined by the `task` field and executed with code blocks like `order.setRealPrice(order.getOriginalPrice());`. | Tasks are defined by the `source` field and executed with code blocks like `order.setRealPrice(order.getOriginalPrice());`. |
| **Output Result** | No explicit output definition is specified, results are returned indirectly via task operations like `order.setRealPrice`. | Outputs are explicitly defined using the `outputs` field, such as `outputs: - name: realPrice source: order.realPrice`. |

NopTaskFlow's usage pattern is very similar to solon-flow:

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
| Feature          | Solon-Flow         | NopTaskFlow        |
|------------------|-------------------|------------------|
| **Engine Loading Method** | Uses `flowEngine.load(Chain.parseByUri)` to load workflow model files. | Uses `taskFlowManager.loadTaskFromPath` to load workflow model files. |
| **Task Loading Path** | File path in the classpath or system directory, e.g., `classpath:flow/bookDiscount.yml`. | File path in the virtual file system, e.g., `/nop/demo/task/discount-01.task.yaml`. |
| **Context Setting Method** | Uses `ChainContext.put` to set context variables. | Uses `ITaskRuntime.setInput` to set input parameters. |
| **Task Execution Method** | Synchronous execution via `flowEngine.eval`. | Asynchronous execution using `task.execute`. |
| **Task Execution** | Synchronous execution through object properties. | Supports asynchronous execution. Returns result variables in `outputs`, or via object properties. |


## II. NopTaskFlow's Built-in Extensibility

NopTaskFlow is highly flexible, supporting automatic asynchronous execution, graph execution, and stack-based execution modes. It also provides fine-grained control over variable scoping. For detailed documentation, see [The Next-Generation Logic Arrangement Engine NopTaskFlow](https://mp.weixin.qq.com/s/2mFC0nQon_l2M82tOlJVhg).

Any function that requires modification can be replaced with NopTaskFlow implementation, such as backend service functions which can directly use NopTaskFlow without requiring Java coding. For example, see [Implementing Backend Service Functions Using NopTaskFlow Logic Arrangement](https://mp.weixin.qq.com/s/CMBcV9Riehlf4_Ds_BmyEw).

In addition to domain-specific designs (such as transactions, asynchronous operations, and recovery), NopTaskFlow also inherits a series of extensible mechanisms from the Nop platform, including those provided by the XLang language.


### 2.1 Unified Model Loader

The solon-flow example demonstrates model loading via classpath, while NopTaskFlow uses `ResourceComponentManager.loadComponentModel` from the Nop platform to load models. It automatically identifies virtual file paths and supports storage in the classpath, system directories, or database tables. The model loading process caches parsed results and tracks dependencies, such as for files like `a.task.xml` which rely on `batch.xlib`. When `batch.xlib` is modified, the corresponding cached model (`a.task.xml`) becomes invalid.

The virtual file system's capabilities can be shared across other DSLs, such as ORM models. If we add Redis storage to the virtual file system, both ORM and NopTaskFlow models will gain support for it. However, solon-flow cannot share this underlying mechanism across multiple DSLs.


### 2.2 Customization (Delta)

In the Nop platform, DSL models managed by the unified model loader inherently support Delta customization. When modifying model content, you don't need to edit existing files directly; instead, you can place a corresponding file in the `_delta` directory. This will override the original file and take priority during loading. For example, `x:extends='super'` indicates inheritance from existing models.

### 2.3 Differential Merge

Nop platform's DSL models support `x:extends` and `x:gen-extends` syntax, allowing for the merging of complex models. For example:

```yaml
x:extends: "base.task.xml"
x:gen-extends: |
  <task-gen:GenAppWorkflow bizObjName="XXX"/>
```

The `x:extends` syntax can be used to indicate inheritance of existing model files. Multiple base files can be specified by separating them with commas, and they will be merged in order from first to last.

The `x:gen-extends` and `x:post-extends` syntax is used during compilation to generate the necessary code logic for generating inherited models dynamically.

During the merge process, the `x:override` mechanism can be employed to control specific node merging logic. For instance, `x:override: remove` indicates the removal of a particular node.

This approach allows for the introduction of delete semantics and inverse metadata concepts.

For detailed information, refer to [XDSL: General Domain-Specific Language Design](https://mp.weixin.qq.com/s/usInt7_odzvFzuiIUPw4iQ).

### 2.4 Multi-Representation

The reversible computation theory emphasizes that a single piece of information can have multiple representation forms (Representaion). Due to information equivalence, these representations can be freely converted into one another. For example, the visualization design can be considered as a form of DSL model's visualization presentation, while the DSL code serves as the textual representation of model information.

The ability to visualize and edit is rooted in the interconversion between visual representation and textual representation:
```
Visual Representation = Designer(textual representation),
Textual Representation = Serializer(visual representation)
```

In the Nop platform, mechanisms are built-in to handle field-level multi-representation, enabling automatic derivation of object-level multi-representation and subsequent visualization editing. For instance, for solon-flow workflows, developing a specialized visual editor requires specific syntax for solon-flow. However, in the Nop platform, we can leverage meta-modeling to automatically generate a dedicated visual editor tailored for logic compilation, without needing to manually design and implement the editor.

As a simple example, the Nop platform supports multiple representation forms such as XML, YAML, and others for model representations, enabling comprehensive business logic expression through various means.
```xml
<task version="1" x:schema="/nop/schema/task/task.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <steps>
        <xpl name="book_discount_1">
            <when><![CDATA[
                order.getOriginalPrice() < 100
            ]]></when>
            <source>
                order.setRealPrice(order.getOriginalPrice());
                logInfo("没有优惠");
            </source>
        </xpl>

        <xpl name="book_discount_4">
            <when><![CDATA[
                order.getOriginalPrice() >= 300
            ]]></when>
            <source>
                order.setRealPrice(order.getOriginalPrice() - 100);
                logInfo("优惠100元");
            </source>
        </xpl>

        <xpl name="book_discount_2">
            <when><![CDATA[
               order.getOriginalPrice() >= 100 && order.getOriginalPrice() < 200
            ]]></when>
            <source>
                order.setRealPrice(order.getOriginalPrice() - 20);
                logInfo("优惠20元");
            </source>
        </xpl>
    </steps>

    <output name="realPrice">
        <source>order.realPrice</source>
    </output>
</task>
```

The XML information presented here is identical to the information provided in the previous section, which was expressed using YAML formatting.

In the Nop platform, another form of modeling, Excel, exists. For any DSL model, there's no need to write Excel parsing or generating code. Instead, you can use Excel to configure complex domain models such as ORM models and API models. This is unlike using `orm.xml` and `api.xml` files for configuration. The Excel model can automatically generate corresponding XML model files.

## 3. Rule Model

In addition to NopTaskFlow logic arrangement, the Nop platform also provides a dedicated model called NopRule (NopRule) for complex logic description. It can be used to describe decision tables or decision trees. The key difference between NopRule and NopTaskFlow is that NopRule focuses on logic evaluation. Using tree structures and matrix structures allows for reusable conditions, simplifying configuration and improving performance. For example, in a decision tree, after evaluating the top-level nodes, the bottom-level nodes no longer need to be re-evaluated. Additionally, the rule model introduces weighted average concepts, which can be directly mapped to business scoring tools, making management more efficient than using logic arrangement.

Using NopRule for rule configuration is much simpler than using logic arrangement.
  
```xml
<rule ruleVersion="1" x:schema="/nop/schema/rule.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <input name="order" mandatory="true"/>
    <output name="discount" mandatory="true" type="Double"/>

    <decisionTree>
        <children>
            <child id="discount-1" label="Price less than 100">
                <predicate>
                    <lt name="order.originalPrice" value="100"/>
                </predicate>
                <output name="discount">
                    <valueExpr>0</valueExpr>
                </output>
            </child>

            <child id="discount-4" label="Price greater than 300">
                <predicate>
                    <ge name="order.originalPrice" value="300"/>
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

Except for XML format, we can also use Excel format to configure decision tables and trees.

![NopRule Decision Table](nop/nop-rule.png)

In the Nop platform, you can import Excel models into the database via the Web interface and perform online editing, rule adjustments, and debugging.

For detailed information about NopRule, please refer to [Open-Source Rule Engine NopRule](https://mp.weixin.qq.com/s/zJvovUG2f4mjB5CbrlX6RA).

## 4. Model Nesting

The fundamental difference between the Nop platform and all other platforms/frameworks is that it does not develop a standalone lower-level engine in isolation but abstracts and completes the technical architecture of all engines at once, ensuring that all engines share the same XLang language and reversible computation support. Using the DSL defined by XLang, you do not need to worry about extensibility (nor related syntax design), and you also do not need to consider how multiple DSLs can be seamlessly integrated into one another.

Previously, we discussed NopTaskFlow and NopRule, two DSL models defined using Xpl template language. By implementing a single Xpl label function (similar to Vue components), these two DSL models can be seamlessly combined for use.

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

In the above example, we use the `<rule:Execute>` tag function to call the `discount.rule.xlsx` rule model to calculate the discount. The result is stored in the `outputs` variable. This allows us to maintain business rules in Excel and directly invoke Excel-formatted business rules within the business code.

The implementation of `rule.xlib` is also straightforward:

```xml
<lib x:schema="/nop/schema/xlib.xdef" xmlns:x="/nop/schema/xdsl.xdef" 
    xmlns:c="c" xmlns:xpl="xpl">
    <tags>
        <Execute>
            <attr name="ruleModelPath" stdDomain="v-path" optional="true" />
            <attr name="ruleName" type="String" optional="true"/>
            <attr name="ruleVersion" type="Long" optional="true"/>
            <attr name="inputs" type="Map" optional="true" />

            <attr name="svcCtx" type="io.nop.core.context.IServiceContext" 
                  implicit="true" optional="true"/>

            <source><![CDATA[
                const ruleManager = inject('nopRuleManager');
                const rule = ruleModelPath ? ruleManager.loadRuleFromPath(ruleModelPath) :
                                      ruleManager.getRule(ruleName, ruleVersion);

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

You can either specify `ruleModelPath`, or specify `ruleName` and `ruleVersion`, and then dynamically determine the rule model object (loaded from virtual files within the system). If not found, it will attempt to load the rule from the database.

The Nop platform is not just a single functional DSL, but rather a series of DSLs that make up what is referred to as the DSL forest. For a detailed explanation of the DSL forest, please refer to [How does Nop overcome the limitations of DSL?](https://mp.weixin.qq.com/s/6TOVbqHFmiFIqoXxQrRkYg). More detailed examples are provided in [Why is SpringBatch a bad design?](https://mp.weixin.qq.com/s/1F2Mkz99ihiw3_juYXrTFw).


The NopPlatform, designed based on reversible computing theory as a low-code platform, has now been open-sourced:

- On Gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- On GitHub: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- On Gitcode: [canonical-entropy/nop-entropy](https://gitcode.com/canonical-entropy/nop-entropy)
- For development examples: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Reversible computing principles and Nop platform introduction on Bilibili](https://www.bilibili.com/video/BV14u411T715/)
- International website: [https://nop-platform.github.io/](https://nop-platform.github.io/)
- Community member Crazydan Studio's Nop development practice sharing site: [https://nop.crazydan.io/](https://nop.crazydan.io/)
