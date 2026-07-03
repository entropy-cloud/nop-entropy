# 实现复杂业务流程

## 适用场景

- 一个业务动作已经不止是简单 CRUD。
- 需要多个步骤、多个聚合、外部系统或提交后副作用。
- 你已经知道要写 Java，但还不确定该怎么拆。

## 默认路径

1. 入口仍放在 BizModel。
2. 复杂流程默认拆到 Processor（`*Processor` 后缀）。
3. 只有跨多个 Processor 稳定复用的单一动作才抽 Step（`*Step` 后缀）。
4. 状态流转、长流程、可配置规则再考虑状态机 / Workflow / Rule。
5. 不创建 `*Service` / `*Controller` 类，Nop 用 BizModel / I*Biz 承接这些职责。

## 最小结构

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> {
    @Inject
    protected OrderSubmitProcessor orderSubmitProcessor;

    @BizMutation
    public SubmitOrderResult submitOrder(@RequestBean SubmitOrderRequest request,
                                         IServiceContext context) {
        SubmitOrderResult result = orderSubmitProcessor.process(request, context);

        txn().afterCommit(null, () -> notifySubmitted(result.getOrderId()));
        return result;
    }
}
```

```java
public class OrderSubmitProcessor {
    @Inject
    protected IOrderBiz orderBiz;

    @Inject
    protected InventoryDeductStep inventoryDeductStep;

    public SubmitOrderResult process(SubmitOrderRequest request,
                                     IServiceContext context) {
        validateRequest(request, context);

        Order order = orderBiz.newEntity();
        fillOrder(order, request, context);
        inventoryDeductStep.execute(order, request, context);
        orderBiz.saveEntity(order, context);

        return buildResult(order);
    }

    protected void validateRequest(SubmitOrderRequest request,
                                   IServiceContext context) {
        // ...
    }
}
```

## 配置余地：派生 bean 覆盖

拓扑稳定的多步流程，虽然步骤顺序不改，但**单个 step 的实现**可能需要按客户/行业覆盖。做法：

1. **每个 step 标记为 `protected`**（不是 `private`）——下游可逐个覆盖，不改流程骨架。
2. **每个 step 以 `IServiceContext` 为末参**——透传到下游 `I*Biz` 调用，不丢用户身份和数据权限。
3. **派生类覆盖 + 同名 bean 注册**——下游写一个继承基线 Processor 的派生类，重载目标 `protected` 方法，然后在 Delta 的 beans.xml 中以**同名 bean id** 注册：

```java
// 下游派生类
public class PharmaOrderSubmitProcessor extends OrderSubmitProcessor {

    @Override
    protected void validateRequest(SubmitOrderRequest request,
                                   IServiceContext context) {
        super.validateRequest(request, context);
        // 医药行业加 GSP 合规校验
    }
}
```

```xml
<!-- Delta: _vfs/_delta/pharma/xxx/beans/app-service.beans.xml -->
<beans x:extends="super">
    <bean id="与基线同名的beanId"
          class="com.pharma.processor.PharmaOrderSubmitProcessor"/>
</beans>
```

覆盖单元是**单个 protected 方法**，流程骨架不被改写。升级基线时派生类自动继承新增步骤。

> **SPI 优先于派生覆盖**：如果扩展需求是"按类型路由到不同实现"（如不同行业的成本计算规则），优先用 SPI 接口 + 注册中心（`@Inject List<IXxxProvider>` + Map 路由），而非派生覆盖整个 Processor。SPI 更细粒度、多实现共存；派生覆盖是单点替换整个 step。两者可组合。

## 拆分判断

| 问题 | 默认做法 |
|------|---------|
| 只是一个几十行内的普通动作 | 先留在 BizModel |
| 明显是多步骤流程，且**拓扑稳定**（不会随客户/上线后调整） | 拆 Processor |
| 明显是多步骤流程，且**拓扑需要可变**（产品化、不同客户不同流程、上线后要调步骤顺序/增删步骤） | 用 task flow 编排（见 `03-modules/nop-task.md`），拓扑通过 VFS 动态更新，不重发版 |
| 多步骤流程需要断点重启 / 并行 / 限流 / 挂起等人工 | task flow（这些是 Processor 不具备的能力） |
| 同一单步会被多个流程复用 | 抽 Step（task flow 的 `<simple>`/`<call-step>`，或 Processor 的 protected 方法） |
| 需要明确状态图和转换约束 | 状态机 |
| 有审批、异步、人机协作、长时运行 | Workflow |
| 决策逻辑频繁变化且希望配置化 | Rule / XLang |

**Processor 与 task flow 的关键区别**：两者都是 BizModel 内部的编排手段，事务上都默认跟随外层 `@BizMutation`。区别在编排是否可脱离代码动态变化——Processor 是 Java，改拓扑要重发版；task flow 是 VFS 上的模型，改拓扑失效缓存即生效。因此"拓扑可变"几乎总是指向 task flow。无论用哪种，**事务入口、状态真相源写回、post-commit 事件这三件事都钉在 BizModel，不下放到编排层**；编排层内部的 step / Processor 方法都只调 `I*Biz` / `CrudBizModel` 安全能力，不直接 `dao()`，也不直接写状态字段。

## 复杂流程里的默认规则

1. BizModel 负责 API 入口、参数类型、事务入口和 afterCommit 副作用。
2. Processor 负责 orchestration，不负责发明新的持久化旁路。
3. Processor 内部优先调用 `I*Biz` 或 `CrudBizModel` 暴露的安全能力，不要默认直接 `dao()`。
4. Step 保持单一职责；如果暂时没有复用，留在 Processor 的 `protected` 方法里更好。
5. **事务入口钉在 BizModel 的 `@BizMutation`，不下放到 Processor**。`@SingleSession` 钉在 Processor 的编排方法上，使 ORM Session 刷新时点与编排边界对齐，异常稳定落入调用方 `try/catch`。两者是不同的关注点：`@Transactional` 管事务边界，`@SingleSession` 管 Session 刷新作用域。
6. **跨域调用方注入 `I*Biz` 接口，不注入 Processor 具体类**。直接注入编排具体类会绕过 `I*Biz` 管道，丢失数据权限并耦合实现细节。

## 什么时候不要继续加 Java 复杂度

以下场景先考虑平台机制，而不是继续堆 Java 类：

1. 只是状态流转规则变化。
2. 只是页面 / 元数据 / 字段可见性变化。
3. 只是 Delta 就能覆盖现有 Biz action。
4. 只是少量局部逻辑，还没形成稳定复用边界。

## 常见坑

1. 在 `@BizMutation` 上再叠加 `@Transactional`。
2. 复杂流程里直接退回到原始 DAO。
3. 一个动作刚变复杂就拆出很多没有复用价值的 Step。
4. 在提交前就发送通知、发消息或调用依赖最终提交结果的外部副作用。
5. 把所有校验、构建、持久化、通知都塞回同一个 BizModel 方法。
6. Processor 所有 helper 是 `private`——下游无法派生覆盖，产品化零配置余地。可覆盖步骤用 `protected`。
7. 跨域注入 `*Processor` 具体类——绕过 `I*Biz` 契约面，丢失数据权限管道。应注入 `I*Biz`。
8. 为不复现的扩展需求过早拆 Step / 过度派生设计——先留在 Processor 的 `protected` 方法，复用边界稳定后再抽。
9. 把稳定流程骨架放进可配 task.xml 只为"留余地"——稳定约束被误改。骨架锁在 Java，task.xml 只用于拓扑可变场景。

## 相关文档

- `../02-core-guides/domain-logic-and-ddd.md`
- `../02-core-guides/service-layer.md`
- `./choose-entity-bizmodel-processor.md`
- `./transaction-boundaries.md`
- `../04-reference/source-anchors.md`
