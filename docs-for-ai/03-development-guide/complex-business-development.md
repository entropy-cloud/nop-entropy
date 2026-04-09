# 复杂业务开发指南

本文档说明在 Nop 平台中处理复杂业务时，应该优先选择哪些机制，以及如何保持 AI 生成代码仍然符合默认最佳实践。

默认原则：

1. 普通查询/修改仍从 BizModel 开始
2. 复杂流程优先拆成 Processor
3. 需要声明式状态流转时优先状态机 / 工作流 / 规则引擎
4. 不要因为业务复杂，就退回到“一个大方法 + 原始 DAO + 重复事务注解”的写法

---

## 一、复杂业务的默认分层

| 复杂度 | 默认位置 |
|--------|---------|
| 单实体状态判断 | Entity |
| 单个业务接口、几十行内逻辑 | BizModel |
| 多步骤流程编排 | Processor |
| 可配置状态流转 | XMeta 状态机 |
| 长流程、人工任务、异步节点 | Workflow |
| 可配置决策逻辑 | Rule / XLang |

---

## 二、复杂业务仍然从安全 BizModel 开始

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> {

    @Inject
    protected OrderSubmitProcessor orderSubmitProcessor;

    @BizMutation
    public SubmitOrderResult submitOrder(@RequestBean SubmitOrderRequest request,
                                         IServiceContext context) {
        return orderSubmitProcessor.process(request, context);
    }
}
```

重点：

- 用 `@BizMutation` 暴露入口
- 用 Processor 承接复杂流程
- 不要在入口方法上再叠加 `@Transactional`

---

## 三、Processor 的职责

Processor 适合处理：

1. 多步骤流程
2. 多个聚合之间的协调
3. 需要拆分为多个步骤才能复用/测试的逻辑

```java
public class OrderSubmitProcessor {
    public SubmitOrderResult process(SubmitOrderRequest request, IServiceContext context) {
        validateRequest(request, context);
        lockInventory(request, context);
        Order order = createOrder(request, context);
        return buildResult(order, context);
    }
}
```

---

## 四、什么时候用状态机 / 工作流 / 规则引擎

### 1. 状态机

适合：

- 有明确状态和转换图
- 需要对不同状态转换做约束

### 2. Workflow

适合：

- 跨多个节点的长流程
- 人工审批
- 异步任务链路

### 3. Rule / XLang

适合：

- 决策逻辑频繁变化
- 希望规则配置化

---

## 五、复杂业务中仍应避免的写法

1. `@BizMutation @Transactional`
2. `@Inject private Foo foo;`
3. 在普通 BizModel 中直接 `dao().getEntityById()` / `dao().saveEntity()`
4. 把所有流程堆进一个 BizModel 方法
5. 还没形成稳定复用就过早抽象 Step

---

## 六、事务与副作用

复杂业务的副作用默认仍然遵循：

```java
txn().afterCommit(null, () -> {
    sendNotification(order);
});
```

只有在 infra/store 层，或需要 `REQUIRES_NEW` 时，才显式使用更底层事务控制。

---

## 七、相关文档

- `./bizmodel-guide.md`
- `./processor-development.md`
- `../12-tasks/choose-entity-vs-bizmodel-vs-processor.md`
- `../12-tasks/transaction-boundaries.md`
- `../02-architecture/workflow.md`
