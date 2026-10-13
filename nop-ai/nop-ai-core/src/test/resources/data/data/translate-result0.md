Here is the translation of the given Chinese technical document:

<TRANSLATE_SOURCE>

# Comparison of Design between NopTaskFlow and SolonFlow

Solon-flow is a basic-level flow processing engine built into the Solon open-source development framework, which can be used in various scenarios such as business rules, decision-making, computation composition, and workflow approval (here, flow processing refers to workflow or business process handling). The design of solon-flow is very lightweight, and the implemented functions are relatively simple. Its design has some similarities with Nop platform's built-in logic composition engine NopTaskFlow, providing an opportunity for comparative analysis. Through comparison, we can clearly see the fundamental differences between Nop platform and traditional development platforms, i.e., how the underlying platform automatically provides common extensible designs.

Nop platform is based on reversible computation theory and has built a set of general foundation technologies, meaning that when developing any engine (such as ORM engines, rule engines, or workflow composition engines), no separate design of extensibility mechanisms is required for each engine. Instead, the reusable common extensible design and its implementation can be directly reused. This greatly improves development efficiency, reduces development costs, making the engines developed on Nop platform more flexible in extending and customizing functions.

## 1. Simple Example: Discount Rule for Order

> Reference from [drools rule engine vs solon-flow which is better? Solon-flow simple tutorial](https://zhuanlan.zhihu.com/p/20299193626)

Solon provides a simple tutorial on using solon-flow to implement an order discount rule.

First, define the entity class `Order`:

```java
@DataBean
public class Order {
    private Double originalPrice; // Order original price
    private Double realPrice; // Order actual price
}
```

Second, define a YAML file for workflow modeling: `bookDiscount.yaml`

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
      System.out.println("Discounted by 100");
  - id: "book_discount_2"
    when: "order.getOriginalPrice() < 200 && order.getOriginalPrice() > 100"
    task: |
      order.setRealPrice(order.getOriginalPrice() - 20);
      System.out.println("Discounted by 20");
  - type: "end"
```

Then, call the workflow model using the following code:

```javascript
@Test
public void testDiscount() {
    FlowEngine flowEngine = FlowEngine.newInstance();
    flowEngine.load(Chain.parseByUri("classpath:flow/bookDiscount.yml"));

    Order bookOrder = new Order();
    bookOrder.setOriginalPrice(500);

    ChainContext ctx = new ChainContext();
    ctx.put("order", bookOrder);

    flowEngine.eval("book_discount", ctx);

    // Price changed, saved 100
    assert bookOrder.getRealPrice() == 400;
}
```

NopTaskFlow is the core module (nop-task-core) of Nop platform, which has around 3000 lines of code. However, it is a very complete and powerful logic composition engine, supporting asynchronous processing, timeout retries, and breakpoint resumption etc.

</TRANSLATE_SOURCE>
翻译结果为
<TRANSLATE_RESULT>
# Comparison of Design between NopTaskFlow and SolonFlow

Solon-flow is a basic-level flow processing engine built into the Solon open-source development framework, which can be used in various scenarios such as business rules, decision-making, computation composition, and workflow approval (here, flow processing refers to workflow or business process handling). The design of solon-flow is very lightweight, and the implemented functions are relatively simple. Its design has some similarities with Nop platform's built-in logic composition engine NopTaskFlow, providing an opportunity for comparative analysis. Through comparison, we can clearly see the fundamental differences between Nop platform and traditional development platforms, i.e., how the underlying platform automatically provides common extensible designs.

Nop platform is based on reversible computation theory and has built a set of general foundation technologies, meaning that when developing any engine (such as ORM engines, rule engines, or workflow composition engines), no separate design of extensibility mechanisms is required for each engine. Instead, the reusable common extensible design and its implementation can be directly reused. This greatly improves development efficiency, reduces development costs, making the engines developed on Nop platform more flexible in extending and customizing functions.

## 1. Simple Example: Discount Rule for Order

> Reference from [drools rule engine vs solon-flow which is better? Solon-flow simple tutorial](https://zhuanlan.zhihu.com/p/20299193626)

Solon provides a simple tutorial on using solon-flow to implement an order discount rule.

First, define the entity class `Order`:

```java
@DataBean
public class Order {
    private Double originalPrice; // Order original price
    private Double realPrice; // Order actual price
}
```

Second, define a YAML file for workflow modeling: `bookDiscount.yaml`

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
      System.out.println("Discounted by 100");
  - id: "book_discount_2"
    when: "order.getOriginalPrice() < 200 && order.getOriginalPrice() > 100"
    task: |
      order.setRealPrice(order.getOriginalPrice() - 20);
      System.out.println("Discounted by 20");
  - type: "end"
```

Then, call the workflow model using the following code:

```javascript
@Test
public void testDiscount() {
    FlowEngine flowEngine = FlowEngine.newInstance();
    flowEngine.load(Chain.parseByUri("classpath:flow/bookDiscount.yml"));

    Order bookOrder = new Order();
    bookOrder.setOriginalPrice(500);

    ChainContext ctx = new ChainContext();
    ctx.put("order", bookOrder);

    flowEngine.eval("book_discount", ctx);

    // Price changed, saved 100
    assert bookOrder.getRealPrice() == 400;
}
```

NopTaskFlow is the core module (nop-task-core) of Nop platform, which has around 3000 lines of code. However, it is a very complete and powerful logic composition engine, supporting asynchronous processing, timeout retries, and breakpoint resumption etc.

</TRANSLATE_RESULT>
