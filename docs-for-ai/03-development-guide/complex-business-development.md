# 复杂业务开发指南

## 概述

Nop平台提供多种机制处理复杂业务逻辑，包括状态机、工作流引擎、规则引擎、事件总线等，通过声明式配置和可逆计算实现业务逻辑的可视化设计和动态执行。

## 核心概念

### 1. BizModel业务模型
BizModel是Nop平台的业务模型层，负责封装业务逻辑和状态管理，提供GraphQL/REST API。

### 2. XMeta元数据
XMeta是Nop平台的标准化对象元数据定义，用于描述业务对象的结构、验证规则、权限配置等。

### 3. XLang脚本语言
XLang是Nop平台的脚本语言，用于编写动态业务逻辑，支持表达式、函数、控制流等。

## 核心机制

### 1. 状态机

#### 状态定义
在XMeta中定义状态和转换规则：

```xml
<meta x:schema="/nop/schema/xmeta.xdef">
    <entityName>Order</entityName>
    
    <!-- 状态属性 -->
    <prop name="status" bizObjName="Order" stdDomain="string" dict="core/order-status">
        <enum>
            <value name="CREATED">待处理</enum>
            <value name="PAID">已付款</enum>
            <value name="SHIPPED">已发货</enum>
            <value name="COMPLETED">已完成</enum>
            <value name="CANCELLED">已取消</enum>
        </enum>
        
        <!-- 状态转换规则 -->
        <x:transitions>
            <x:transition from="CREATED" to="PAID">
                <x:condition test="order.isPaid()"/>
            </x:transition>
            <x:transition from="PAID" to="SHIPPED">
                <x:action>!orderService.shipOrder(#entity)</x:action>
            </x:transition>
            <x:transition from="SHIPPED" to="COMPLETED">
                <x:action>!orderService.completeOrder(#entity)</x:action>
            </x:transition>
            <x:transition from="CREATED" to="CANCELLED"/>
            <x:transition from="PAID" to="CANCELLED"/>
            <x:transition from="SHIPPED" to="CANCELLED"/>
        </x:transitions>
    </prop>
</meta>
```

#### 状态机使用

在BizModel中通过XMeta定义的状态机进行状态转换：

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> {
    
    @Inject
    private OrderService orderService;
    
    @BizMutation
    @Transactional
    public Order payOrder(@Name("orderId") String orderId) {
        Order order = dao().requireEntityById(orderId);
        
        // 检查订单状态
        String currentStatus = order.getStatus();
        if (!"CREATED".equals(currentStatus)) {
            throw new NopException(OrderErrors.ERR_ORDER_STATUS_INVALID)
                .param(OrderErrors.ARG_STATUS, currentStatus);
        }
        
        // 更新付款状态
        order.setStatus("PAID");
        order.setPaidTime(new Date());
        dao().saveEntity(order);
        
        return order;
    }
    
    @BizMutation
    @Transactional
    public Order shipOrder(@Name("orderId") String orderId) {
        Order order = dao().requireEntityById(orderId);
        
        // 检查订单状态
        String currentStatus = order.getStatus();
        if (!"PAID".equals(currentStatus)) {
            throw new NopException(OrderErrors.ERR_ORDER_STATUS_INVALID)
                .param(OrderErrors.ARG_STATUS, currentStatus);
        }
        
        // 更新发货状态
        order.setStatus("SHIPPED");
        order.setShippedTime(new Date());
        dao().saveEntity(order);
        
        return order;
    }
    
    @BizMutation
    public void cancelOrder(@Name("orderId") String orderId) {
        Order order = dao().requireEntityById(orderId);
        
        // 取消订单
        order.setStatus("CANCELLED");
        order.setCancelTime(new Date());
        dao().saveEntity(order);
    }
    
    @BizMutation
    @Transactional
    public Order completeOrder(@Name("orderId") String orderId) {
        Order order = dao().requireEntityById(orderId);
        
        // 完成订单
        order.setStatus("COMPLETED");
        order.setCompleteTime(new Date());
        dao().saveEntity(order);
        
        return order;
    }
}
```

### 2. 工作流引擎

#### 工作流定义
在XDef中定义工作流：

```xml
<wf:workflow xmlns:wf="http://nop-xlang.github.io/schema/workflow.xdef" 
              name="order-process" 
              xmlns:x="http://nop-xlang.github.io/schema/xscript.xdef">
    
    <wf:start id="start"/>
    
    <wf:task id="validate">
        <wf:action>!orderService.validateOrder(#entity)</wf:action>
    </wf:task>
    <wf:transition from="start" to="validate"/>
    
    <wf:task id="pay">
        <wf:condition test="order.totalAmount > 0 && order.balance >= order.totalAmount"/>
        <wf:action>!paymentService.processPayment(#entity)</wf:action>
    </wf:task>
    <wf:transition from="validate" to="pay" when="valid"/>
    
    <wf:task id="ship">
        <wf:action>!orderService.shipOrder(#entity)</wf:action>
        <wf:action>!inventoryService.reduceStock(#entity)</wf:action>
    </wf:task>
    <wf:transition from="pay" to="ship" when="paid"/>
    
    <wf:task id="complete">
        <wf:action>!orderService.completeOrder(#entity)</wf:action>
        <wf:action>!orderService.sendNotification(#entity)</wf:action>
    </wf:task>
    <wf:transition from="ship" to="complete" when="shipped"/>
    
    <wf:end id="end"/>
    <wf:transition from="complete" to="end"/>
</wf:workflow>
```

#### 工作流使用

在BizModel中启动工作流：

```java
@BizModel("OrderProcess")
public class OrderProcessBizModel extends AbstractBizModel {
    
    @Inject
    private IWorkflowEngine workflowEngine;
    
    @BizMutation
    public void startOrder(@Name("orderId") String orderId) {
        // 启动工作流
        workflowEngine.startWorkflow("order-process", orderId);
    }
    
    @BizQuery
    public String getOrderStatus(@Name("orderId") String orderId) {
        // 查询工作流状态
        return workflowEngine.getWorkflowStatus("order-process", orderId);
    }
}
```

### 3. 规则引擎

#### 规则定义
在XDef中定义规则：

```xml
<rule:rule xmlns:rule="http://nop-xlang.github.io/schema/rule.xdef">
    
    <rule:rule name="price-discount">
        <rule:when>
            <rule:or>
                <rule:condition test="user.isVip"/>
                <rule:condition test="order.amount > 1000"/>
            </rule:or>
        </rule:when>
        
        <rule:then>
            <rule:action>!priceService.applyDiscount(#entity, 0.9)</rule:action>
        </rule:then>
    </rule:rule>
    
    <rule:rule name="free-shipping">
        <rule:when>
            <rule:condition test="order.amount > 200"/>
        </rule:when>
        
        <rule:then>
            <rule:action>!shippingService.setFreeShipping(#entity)</rule:action>
        </rule:then>
    </rule:rule>
    
    <rule:rule name="stock-check">
        <rule:when>
            <rule:condition test="inventoryService.getStock(#entity.productId) <= 0"/>
        </rule:when>
        
        <rule:then>
            <rule:action>!orderService.setOutOfStock(#entity)</rule:action>
        </rule:then>
    </rule:rule>
</rule:rule>
```

#### 规则引擎使用

在BizModel中应用规则：

```java
@BizModel("OrderPricing")
public class OrderPricingBizModel extends AbstractBizModel {
    
    @Inject
    private IRuleEngine ruleEngine;
    
    @BizMutation
    public Order calculatePrice(@Name("orderId") String orderId) {
        Order order = dao().requireEntityById(orderId);
        
        // 应用所有规则
        ruleEngine.applyRules(order, "orderPricing");
        
        // 规则可能修改了以下属性：
        // - order.discount (折扣)
        // - order.shippingFee (运费)
        // - order.totalAmount (总金额）
        
        dao().saveEntity(order);
        return order;
    }
}
```

### 4. 事件总线

#### 事件定义
在XDef中定义事件：

```xml
<event:event xmlns:event="http://nop-xlang.github.io/schema/event.xdef">
    
    <event:event name="OrderCreated">
        <event:payload type="Order"/>
    </event:event>
    
    <event:event name="OrderPaid">
        <event:payload type="Order"/>
    </event:event>
    
    <event:event name="OrderShipped">
        <event:payload type="Order"/>
    </event:event>
    
    <event:event name="OrderCompleted">
        <event:payload type="Order"/>
    </event:event>
</event:event>
```

#### 事件监听

在BizModel中监听事件：

```java
@BizModel("OrderNotification")
public class OrderNotificationBizModel extends AbstractBizModel {
    
    @Inject
    private IEventBus eventBus;
    @Inject
    private INotificationService notificationService;
    
    public OrderNotificationBizModel() {
        // 注册事件监听器
        eventBus.addListener("OrderCreated", this::onOrderCreated);
        eventBus.addListener("OrderPaid", this::onOrderPaid);
        eventBus.addListener("OrderShipped", this::onOrderShipped);
        eventBus.addListener("OrderCompleted", this::onOrderCompleted);
    }
    
    private void onOrderCreated(Order order) {
        // 发送订单创建通知
        notificationService.sendOrderCreated(order);
    }
    
    private void onOrderPaid(Order order) {
        // 发送付款成功通知
        notificationService.sendOrderPaid(order);
    }
    
    private void onOrderShipped(Order order) {
        // 发送发货通知
        notificationService.sendOrderShipped(order);
    }
    
    private void onOrderCompleted(Order order) {
        // 发送订单完成通知
        notificationService.sendOrderCompleted(order);
    }
    
    @BizMutation
    public Order createOrder(@Name("data") Map<String, Object> data) {
        Order order = save(data);
        
        // 发布事件
        eventBus.publish("OrderCreated", order);
        
        return order;
    }
}
```

## 完整示例：订单处理流程

### 场景：订单从创建到完成的完整流程

#### 1. 创建订单

```java
@BizModel("OrderProcess")
public class OrderProcessBizModel extends CrudBizModel<Order> {
    
    @BizMutation
    @Transactional
    public Order createOrder(@Name("order") OrderData orderData) {
        Order order = new Order();
        BeanTool.copyProperties(order, orderData);
        
        order.setStatus("CREATED");
        order.setCreateTime(new Date());
        
        // 发布创建事件
        eventBus.publish("OrderCreated", order);
        
        return save(order);
    }
}
```

#### 2. 应用折扣规则

```java
@BizModel("OrderPricing")
public class OrderPricingBizModel extends CrudBizModel<Order> {
    
    @BizMutation
    public Order applyDiscount(@Name("orderId") String orderId) {
        Order order = dao().requireEntityById(orderId);
        
        // 应用价格规则
        ruleEngine.applyRules(order, "orderPricing");
        
        dao().saveEntity(order);
        return order;
    }
}
```

#### 3. 付款处理

```java
@BizModel("OrderPayment")
public class OrderPaymentBizModel extends CrudBizModel<Order> {
    
    @BizMutation
    @Transactional
    public Order payOrder(@Name("orderId") String orderId) {
        Order order = dao().requireEntityById(orderId);
        
        // 检查库存
        for (OrderItem item : order.getItems()) {
            int stock = inventoryService.getStock(item.getProductId());
            if (stock <= 0) {
                throw new NopException(OrderErrors.ERR_PRODUCT_OUT_OF_STOCK)
                    .param("productId", item.getProductId());
            }
        }
        
        // 创建支付记录
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setAmount(order.getTotalAmount());
        payment.setPaymentTime(new Date());
        
        paymentDao.saveEntity(payment);
        
        // 更新订单状态
        order.setStatus("PAID");
        order.setPaidTime(new Date());
        dao().saveEntity(order);
        
        // 发布付款事件
        eventBus.publish("OrderPaid", order);
        
        return order;
    }
}
```

#### 4. 发货处理

```java
@BizModel("OrderShipping")
public class OrderShippingBizModel extends CrudBizModel<Order> {
    
    @BizMutation
    @Transactional
    public Order shipOrder(@Name("orderId") String orderId) {
        Order order = dao().requireEntityById(orderId);
        
        // 检查支付状态
        if (!"PAID".equals(order.getStatus())) {
            throw new NopException(OrderErrors.ERR_ORDER_NOT_PAID);
        }
        
        // 检查库存并扣减
        for (OrderItem item : order.getItems()) {
            inventoryService.reduceStock(item.getProductId(), item.getQuantity());
        }
        
        // 创建物流记录
        Logistics logistics = new Logistics();
        logistics.setOrderId(orderId);
        logistics.setLogisticsCompany("顺丰快递");
        logistics.setTrackingNumber("SF" + System.currentTimeMillis());
        logistics.setShipTime(new Date());
        
        logisticsDao.saveEntity(logistics);
        
        // 更新订单状态
        order.setLogisticsId(logistics.getId());
        order.setStatus("SHIPPED");
        order.setShippedTime(new Date());
        dao().saveEntity(order);
        
        // 发布发货事件
        eventBus.publish("OrderShipped", order);
        
        return order;
    }
}
```

#### 5. 完成订单

```java
@BizModel("OrderComplete")
public class OrderCompleteBizModel extends CrudBizModel<Order> {
    
    @BizMutation
    @Transactional
    public Order completeOrder(@Name("orderId") String orderId) {
        Order order = dao().requireEntityById(orderId);
        
        // 检查发货状态
        if (!"SHIPPED".equals(order.getStatus())) {
            throw new NopException(OrderErrors.ERR_ORDER_NOT_SHIPPED);
        }
        
        // 更新订单状态
        order.setStatus("COMPLETED");
        order.setCompleteTime(new Date());
        dao().saveEntity(order);
        
        // 发布完成事件
        eventBus.publish("OrderCompleted", order);
        
        return order;
    }
}
```

## 最佳实践

### 1. 状态机设计
- 使用XMeta定义状态转换规则
- 保持状态简单明确，避免过多状态
- 确保每个状态都有明确的进入和退出条件
- 提供状态变更日志记录

### 2. 工作流设计
- 将复杂流程分解为多个任务
- 每个任务只做一件事
- 使用条件分支控制流程走向
- 提供流程监控和日志

### 3. 规则设计
- 规则应该简单明确
- 避免规则冲突
- 提供规则优先级
- 支持规则的动态更新

### 4. 事件驱动
- 事件应该描述清晰，命名规范
- 避免事件循环依赖
- 事件监听器应该快速执行
- 提供事件重试机制

### 5. 异常处理
- 使用NopException统一异常
- 提供清晰的错误码
- 记录详细的错误上下文
- 事件总线可以用于异步错误处理

## 高级特性

### 1. 并发处理
```java
// 并发安全的状态更新
@Transactional
@BizMutation
public void concurrentUpdate(@Name("orderId") String orderId) {
    // 使用悲观锁
    Order order = dao().requireEntityForUpdate(orderId);
    
    // 执行业务逻辑
    order.setStatus("PROCESSING");
    dao().saveEntity(order);
}
```

### 2. 补偿事务
```java
// TCC模式：try-confirm-cancel
@BizMutation
public void tccOperation(String orderId) {
    // try阶段：预留资源
    reserveStock(orderId);
    
    // confirm阶段：确认订单
    confirmOrder(orderId);
    
    // cancel阶段：取消订单（如果确认失败）
    cancelOrder(orderId);
}
```

### 3. 事件溯源
```java
// 记录事件历史
@BizQuery
public List<EventLog> getOrderEvents(@Name("orderId") String orderId) {
    return eventLogDao.findByEntityId(orderId);
}
```

## 常见问题

### Q1: 如何处理循环依赖？

A: 使用事件总线解耦：
```java
// 错误：直接依赖
orderService.updateInventory(order); // 循环依赖

// 正确：使用事件驱动
eventBus.publish("OrderCreated", order);  // 解耦
inventoryService.onOrderCreated(order);  // 异步处理
```

### Q2: 如何调试复杂业务流程？

A: 使用工作流监控：
```java
// 查询工作流执行状态
@BizQuery
public WorkflowStatus getWorkflowStatus(@Name("workflowId") String workflowId) {
    return workflowEngine.getWorkflowStatus(workflowId);
}

// 查询任务执行状态
@BizQuery
public List<TaskStatus> getTaskStatus(@Name("workflowId") String workflowId) {
    return workflowEngine.getTaskStatuses(workflowId);
}
```

### Q3: 如何热更新规则？

A: 使用规则引擎的动态加载：
```java
// 热加载规则配置
ruleEngine.loadRules("/rules/order-rules.xdef");

// 应用更新后的规则
ruleEngine.applyRules(entity, "orderPricing");
```

### Q4: 如何处理事件失败？

A: 使用事件重试机制：
```java
// 事件监听器配置
public class OrderListener {
    private static final int MAX_RETRY = 3;
    
    @EventListener("OrderPaid")
    public void onOrderPaid(Order order) {
        for (int i = 0; i < MAX_RETRY; i++) {
            try {
                sendNotification(order);
                break;
            } catch (Exception e) {
                log.warn("Failed to send notification, attempt {}", i + 1, e);
            }
        }
    }
}
```

## 相关文档

- [服务层开发指南](./service-layer.md) - BizModel开发详解
- [IEntityDao使用指南](../dao/entitydao-usage.md) - 数据访问接口详解
- [事务管理指南](../04-core-components/transaction.md) - 事务管理完整指南
- [CRUD开发指南](./crud-development.md) - CRUD功能开发
- [GraphQL服务开发指南](./api-development.md) - GraphQL API开发
- [异常处理指南](../04-core-components/exception-handling.md) - 异常处理完整指南

## 总结

Nop平台提供了完整的复杂业务处理机制：

1. **状态机**：清晰定义状态转换规则
2. **工作流引擎**：可视化流程定义和执行
3. **规则引擎**：灵活的业务规则处理
4. **事件总线**：事件驱动架构
5. **补偿事务**：支持TCC模式

通过合理使用这些机制，可以构建复杂、可维护的业务系统。
