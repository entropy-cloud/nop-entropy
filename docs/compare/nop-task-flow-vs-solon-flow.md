# 逻辑编排引擎NopTaskFlow与SolonFlow的设计对比

solon-flow 是国产开源开发框架solon内置的一个基础级的流处理引擎，可用于业务规则、决策处理、计算编排、流程审批等多种场景（这里的流处理指的应该是工作流或者业务流处理）。solon-flow的设计非常轻量级，目前已经实现的功能比较简单。它的设计与Nop平台内置的逻辑编排引擎NopTaskFlow有一定的相似之处，这为我们提供了一个对比分析的机会。通过对比可以清晰地看到 Nop 平台与传统开发平台之间的本质区别，即底层平台如何自动提供通用的可扩展设计。

Nop平台基于可逆计算理论，构建了一整套通用的基础技术设施，这意味着在开发任何引擎（如ORM引擎、规则引擎、流程编排引擎）时，无需为每个引擎单独设计可扩展机制，而是可以直接复用这套通用的可扩展设计**及其实现**。这大大提高了开发效率，降低了开发成本，使得在 Nop 平台上开发的引擎能够更加灵活地进行功能扩展和定制。

## 一. 简单示例：订单打折规则

> 示例来源于[drools 规则引擎和 solon-flow 哪个好？ solon-flow 简明教程](https://zhuanlan.zhihu.com/p/20299193626)

solon提供的简明教程中提供了一个使用solon-flow实现订单打折规则的演示。

首先定义实体类BookOrder

```java
@DataBean
public class Order {
    private Double originalPrice;//订单原始价格，即优惠前价格
    private Double realPrice;//订单真实价格，即优惠后价格
}
```

第二，定义一个yaml格式的流程模型文件`bookDiscount.yaml`

```yaml
id: "book_discount"
nodes:
  - type: "start"
  - id: "book_discount_1"
    when: "order.getOriginalPrice() < 100"
    task: |
      order.setRealPrice(order.getOriginalPrice());
      System.out.println("没有优惠");
  - id: "book_discount_4"
    when: "order.getOriginalPrice() >= 300"
    task: |
      order.setRealPrice(order.getOriginalPrice() - 100);
      System.out.println("优惠100元");
  - id: "book_discount_2"
    when: "order.getOriginalPrice() < 200 && order.getOriginalPrice() > 100"
    task: |
      order.setRealPrice(order.getOriginalPrice() - 20);
      System.out.println("优惠20元");
  - type: "end"
```

然后通过如下方式调用流程模型

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

    //价格变了，省了100块
    assert bookOrder.getRealPrice() == 400;
} 
```

NopTaskFlow的核心（nop-task-core模块）虽然只有3000行左右的代码，但是它是一个非常完整的逻辑编排引擎，支持异步处理、超时重试、断点重提等高级功能，所以用NopTaskFlow可以很容易的实现solon-flow所演示的功能。

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

| 特性          | Solon-Flow                                                                  | NopTaskFlow                                                                     |
| ----------- | --------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| **整体结构**    | 使用 `id` 和 `nodes` 定义流程，节点通过 `id` 和 `type` 区分。                               | 使用 `version` 和 `steps` 定义流程，步骤通过 `type` 和 `name` 区分。                            |
| **起始/结束节点** | 通过 `type: "start"` 和 `type: "end"` 明确标识流程的开始和结束。                            | 缺省按照顺序执行steps中的步骤，也可以设置图模式，此时需要显式指定下一个执行步骤。                                     |
| **步骤定义**    | 每个步骤通过 `id` 和 `when` 条件定义，任务通过 `task` 字段指定。                                 | 每个步骤通过 `type` 和 `name` 定义，任务通过 `source` 字段指定。                                   |
| **条件判断**    | 使用 `when` 字段定义条件，如 `order.getOriginalPrice() < 100`。                        | 使用 `when` 字段定义条件，如 `order.getOriginalPrice() < 100`。                            |
| **任务执行**    | 任务通过 `task` 字段定义，直接包含代码块，如 `order.setRealPrice(order.getOriginalPrice());`。 | 任务通过 `source` 字段定义，直接包含代码块，如 `order.setRealPrice(order.getOriginalPrice());`。   |
| **输出结果**    | 没有明确的输出结果定义，结果通过任务代码中的操作（如 `order.setRealPrice`）间接返回。                       | 通过 `outputs` 字段明确定义输出结果，如 `outputs: - name: realPrice source: order.realPrice`。 |

NopTaskFlow的调用方式与solon-flow也非常相似：

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

| 特性           | Solon-Flow                                                 | NopTaskFlow                                       |
| ------------ | ---------------------------------------------------------- | ------------------------------------------------- |
| **任务引擎加载方式** | 使用 `flowEngine.load(Chain.parseByUri)`  方法加载流程模型文件。        | 使用 `taskFlowManager.loadTaskFromPath` 方法加载流程模型文件。 |
| **任务加载路径**   | classpath下的文件或者操作系统目录下的文件`classpath:flow/bookDiscount.yml` | 虚拟文件系统中的路径`/nop/demo/task/discount-01.task.yaml`  |
| **上下文设置方式**  | 使用 `ChainContext.put` 设置上下文变量。                             | 使用 `ITaskRuntime.setInput` 设置输入参数。                |
| **任务执行方式**   | 使用 `flowEngine.eval` 方法执行任务。                               | 使用 `task.execute` 方法执行任务。                         |
| **任务执行**     | 同步执行，通过对象属性来返回结果                                           | 支持异步执行。任务会返回outputs结果变量集合，当然也可以通过对象属性来返回结果        |
