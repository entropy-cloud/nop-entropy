# Transaction Processing Examples

## 概述

本文档提供Nop Platform事务处理的示例，展示如何使用声明式事务、编程式事务、事务传播级别、事务监听器等特性。

## 声明式事务

### 1. 基础事务

使用@Transactional注解开启事务：

```java
@BizModel("Account")
public class AccountBizModel extends CrudBizModel<NopAccount> {

    @BizMutation
    @Transactional
    public NopAccount transfer(@Name("fromUserId") String fromUserId,
                             @Name("toUserId") String toUserId,
                             @Name("amount") BigDecimal amount) {
        // 1. 获取转出账户
        NopAccount fromAccount = dao().requireEntityById(fromUserId);

        // 2. 验证余额
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new NopException(ERR_INSUFFICIENT_BALANCE)
                .param("userId", fromUserId)
                .param("balance", fromAccount.getBalance())
                .param("amount", amount);
        }

        // 3. 扣款
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        dao().saveEntity(fromAccount);

        // 4. 收款
        NopAccount toAccount = dao().requireEntityById(toUserId);
        toAccount.setBalance(toAccount.getBalance().add(amount));
        dao().saveEntity(toAccount);

        logTransfer(fromAccount, toAccount, amount);
        return fromAccount;
    }
}
```

### 2. 只读事务

使用readOnly指定只读事务：

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<NopOrder> {

    @BizQuery
    @Transactional(readOnly = true)
    public PageBean<NopOrder> findOrders(QueryBean query, int pageNo, int pageSize) {
        // 只读事务：允许事务但不会提交
        return findPage(query, pageNo, pageSize);
    }

    @BizQuery
    public NopOrder getOrder(String orderId) {
        // 没有@Transactional注解：非事务方式查询
        return dao().getEntityById(orderId);
    }
}
```

### 3. 事务超时

设置事务超时时间：

```java
@BizModel("Payment")
public class PaymentBizModel {

    @BizMutation
    @Transactional(timeout = 30) // 30秒超时
    public PaymentResult processPayment(@Name("orderId") String orderId) {
        // 1. 查询订单
        NopOrder order = dao().requireEntityById(orderId);

        // 2. 调用支付网关
        PaymentResult result = paymentGateway.process(order);

        // 3. 更新订单状态
        order.setStatus(result.isSuccess() ? "PAID" : "FAILED");
        dao().saveEntity(order);

        return result;
    }
}
```

## 编程式事务

### 1. 基础事务模板

使用ITransactionTemplate进行事务操作：

```java
@BizModel("Inventory")
public class InventoryBizModel {

    @Inject
    private ITransactionTemplate transactionTemplate;

    @BizMutation
    public void batchUpdateInventory(List<InventoryUpdate> updates) {
        // 使用编程式事务
        transactionTemplate.runInTransaction(() -> {
            for (InventoryUpdate update : updates) {
                Inventory inventory = inventoryDao.requireEntityById(update.getProductId());
                inventory.setQuantity(inventory.getQuantity() + update.getDelta());
                inventory.setUpdateTime(new Date());
                inventoryDao.saveEntity(inventory);
            }
        });
    }
}
```

### 2. 指定事务组

指定事务组名称：

```java
@BizModel("MultiService")
public class MultiServiceBizModel {

    @Inject
    private ITransactionTemplate transactionTemplate;

    @BizMutation
    public void complexOperation(String userId) {
        // 使用指定事务组
        transactionTemplate.runInTransaction("userTxnGroup", () -> {
            // 操作1：更新用户信息
            User user = userDao.requireEntityById(userId);
            user.setLastLoginTime(new Date());
            userDao.saveEntity(user);

            // 操作2：更新用户积分
            Point point = pointDao.findByUserId(userId);
            point.setBalance(point.getBalance() + 10);
            pointDao.saveEntity(point);

            // 操作3：记录登录日志
            LoginLog log = new LoginLog();
            log.setUserId(userId);
            log.setLoginTime(new Date());
            loginLogDao.saveEntity(log);
        });
    }
}
```

### 3. 事务结果处理

处理事务结果：

```java
@BizMutation
public Object complexOperationWithResult() {
    ITransactionAction<Object> action = () -> {
        // 事务操作
        return performOperation();
    };

    TransactionResult<Object> result = transactionTemplate.runInTransaction(action);

    if (result.isSuccess()) {
        return result.getData();
    } else {
        throw new NopException(ERR_TRANSACTION_FAILED)
            .description(result.getErrorMessage());
    }
}
```

## 事务传播级别

### 1. REQUIRED（默认）

必须在事务中运行，如果当前没有事务则创建新事务：

```java
@BizModel("ServiceA")
public class ServiceABizModel {

    @Inject
    private IServiceB serviceB;

    @BizMutation
    @Transactional(propagation = Propagation.REQUIRED)
    public void methodA() {
        // methodA在事务中运行
        doSomething();

        // methodB也使用REQUIRED，加入当前事务
        serviceB.methodB();
    }
}

@BizModel("ServiceB")
public class ServiceBBizModel {

    @BizMutation
    @Transactional(propagation = Propagation.REQUIRED)
    public void methodB() {
        // 加入methodA的事务
        doSomething();
    }
}
```

### 2. REQUIRES_NEW

创建新事务，挂起当前事务：

```java
@BizModel("Order")
public class OrderBizModel {

    @Inject
    private IInventoryBizModel inventoryBizModel;

    @Inject
    private ILogBizModel logBizModel;

    @BizMutation
    @Transactional(propagation = Propagation.REQUIRED)
    public void createOrder(NopOrder order) {
        // 1. 创建订单（当前事务）
        dao().saveEntity(order);

        // 2. 记录日志（新事务，独立提交）
        // 即使创建订单失败，日志也会成功记录
        logBizModel.recordOrderLog(order);
    }
}

@BizModel("Log")
public class LogBizModel {

    @BizMutation
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOrderLog(NopOrder order) {
        // 在新事务中记录日志
        Log log = new Log();
        log.setOrderId(order.getOrderId());
        log.setCreateTime(new Date());
        dao().saveEntity(log);
    }
}
```

### 3. MANDATORY

必须在现有事务中运行：

```java
@BizModel("Order")
public class OrderBizModel {

    @Inject
    private IInventoryBizModel inventoryBizModel;

    @BizMutation
    @Transactional(propagation = Propagation.REQUIRED)
    public void createOrder(NopOrder order) {
        // 1. 创建订单
        dao().saveEntity(order);

        // 2. 扣减库存（在相同事务中）
        inventoryBizModel.updateInventory(order.getProductId(), -order.getQuantity());
    }
}

@BizModel("Inventory")
public class InventoryBizModel {

    @BizMutation
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateInventory(String productId, int delta) {
        // MANDATORY：必须有事务存在
        Inventory inventory = inventoryDao.requireEntityById(productId);
        inventory.setQuantity(inventory.getQuantity() + delta);
        inventoryDao.saveEntity(inventory);
    }

    // ❌ 错误：MANDATORY方法在没有事务的环境调用会抛出异常
    @BizQuery
    public Inventory getInventory(String productId) {
        // 这个方法没有事务，如果被MANDATORY方法调用会失败
        return inventoryDao.getEntityById(productId);
    }
}
```

### 4. SUPPORTS

支持当前事务，如果没有则以非事务方式运行：

```java
@BizModel("Cache")
public class CacheBizModel {

    @Inject
    private IOrderBizModel orderBizModel;

    @BizQuery
    @Transactional(propagation = Propagation.SUPPORTS)
    public Order getOrder(String orderId) {
        // 如果调用的方法有事务，则加入该事务
        // 否则以非事务方式运行
        return orderBizModel.getOrder(orderId);
    }
}
```

### 5. NOT_SUPPORTED

不支持事务，以非事务方式运行：

```java
@BizModel("ExternalService")
public class ExternalServiceBizModel {

    @Inject
    private IOrderBizModel orderBizModel;

    @BizMutation
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void callExternalAPI(String orderId) {
        // 挂起当前事务（如果有）
        ExternalAPIResult result = externalAPI.call(orderId);

        // 以非事务方式更新订单状态
        orderBizModel.updateOrderStatus(orderId, result.getStatus());
    }
}
```

## 事务隔离级别

### 1. READ_COMMITTED

读已提交的数据：

```java
@BizMutation
@Transactional(isolation = IsolationLevel.READ_COMMITTED)
public void createOrder(NopOrder order) {
    dao().saveEntity(order);
}
```

### 2. REPEATABLE_READ

可重复读：

```java
@BizMutation
@Transactional(isolation = IsolationLevel.REPEATABLE_READ)
public void updateAccount(String userId, BigDecimal amount) {
    // 在事务中多次读取同一数据，结果一致
    Account account = dao().requireEntityById(userId);
    BigDecimal balance = account.getBalance();

    account.setBalance(balance.add(amount));
    dao().saveEntity(account);
}
```

### 3. SERIALIZABLE

串行化隔离：

```java
@BizMutation
@Transactional(isolation = IsolationLevel.SERIALIZABLE)
public void transferWithHighConsistency(String fromId, String toId, BigDecimal amount) {
    // 最高隔离级别，完全串行化
    // 适用于对数据一致性要求极高的场景
    Account from = dao().requireEntityById(fromId);
    Account to = dao().requireEntityById(toId);

    from.setBalance(from.getBalance().subtract(amount));
    to.setBalance(to.getBalance().add(amount));

    dao().saveEntity(from);
    dao().saveEntity(to);
}
```

## 事务回滚

### 1. 异常自动回滚

发生异常时自动回滚：

```java
@BizMutation
@Transactional
public void transfer(String fromId, String toId, BigDecimal amount) {
    Account from = dao().requireEntityById(fromId);
    Account to = dao().requireEntityById(toId);

    from.setBalance(from.getBalance().subtract(amount));
    dao().saveEntity(from);

    // 发生异常，事务自动回滚
    to.setBalance(to.getBalance().add(amount));
    dao().saveEntity(to);
}
```

### 2. 不回滚指定异常

使用setNotRollback控制回滚：

```java
@BizModel("Log")
public class LogBizModel {

    @BizMutation
    @Transactional
    public void recordLog(String message) {
        try {
            // 记录日志
            Log log = new Log();
            log.setMessage(message);
            log.setCreateTime(new Date());
            dao().saveEntity(log);
        } catch (Exception e) {
            // 即使日志记录失败也不回滚主事务
            throw new NopException(ERR_LOG_SAVE_FAILED, e)
                .setNotRollback(true);
        }
    }
}
```

### 3. 手动回滚

使用setRollbackOnly标记只回滚：

```java
@BizModel("Validator")
public class ValidatorBizModel {

    @Inject
    private IOrderBizModel orderBizModel;

    @BizMutation
    @Transactional
    public void validateAndCreateOrder(OrderData data) {
        try {
            // 验证数据
            ValidationResult result = validateOrderData(data);

            if (!result.isValid()) {
                // 验证失败，只回滚不抛出异常
                throw new NopException(ERR_VALIDATION_FAILED, result.getErrorMessage())
                    .setRollbackOnly(true);
            }
        } catch (NopException e) {
            if (e.isRollbackOnly()) {
                // 只回滚，不重新抛出异常
                log.error("Validation failed, rolling back", e);
                return;
            }
            throw e;
        }

        // 验证通过，创建订单
        orderBizModel.createOrder(data);
    }
}
```

## 事务监听器

### 1. 事务提交后操作

在事务提交后执行操作：

```java
public class TransactionListener {

    @AfterTransactionCommit
    public void afterCommit(CommitEvent event) {
        // 事务提交后发送通知
        if (event.containsChange("order")) {
            Order order = (Order) event.getChangedEntity("order");
            sendOrderNotification(order);
        }

        // 事务提交后清除缓存
        if (event.containsChange("user")) {
            String userId = (String) event.getChangedEntityId("user");
            cacheManager.remove("userCache", userId);
        }
    }

    private void sendOrderNotification(Order order) {
        // 发送邮件、短信等通知
        EmailMessage email = new EmailMessage();
        email.setTo(order.getUserEmail());
        email.setSubject("订单创建通知");
        email.setContent("您的订单" + order.getOrderNo() + "已创建");
        emailService.send(email);
    }
}
```

### 2. 事务回滚后操作

在事务回滚后执行操作：

```java
public class RollbackListener {

    @AfterTransactionRollback
    public void afterRollback(RollbackEvent event) {
        // 记录回滚原因
        log.error("Transaction rolled back: cause={}", event.getCause());

        // 发送告警通知
        sendRollbackAlert(event);
    }

    private void sendRollbackAlert(RollbackEvent event) {
        // 发送邮件给管理员
        EmailMessage email = new EmailMessage();
        email.setTo("admin@example.com");
        email.setSubject("事务回滚告警");
        email.setContent("事务回滚：" + event.getCause().getMessage());
        emailService.send(email);
    }
}
```

## 分布式事务

### 1. 两阶段提交

使用TCC模式实现分布式事务：

```java
@BizModel("Order")
public class OrderBizModel {

    @Inject
    protected IPaymentService paymentService;
    @Inject
    private IInventoryService inventoryService;

    @BizMutation
    @TccTransaction
    public OrderResult createOrder(OrderData data) {
        // Try阶段：准备资源
        Order order = new Order();
        BeanTool.copyProperties(order, data);
        dao().saveEntity(order);

        // 扣减库存
        inventoryService.decreaseInventory(data.getProductId(), data.getQuantity());

        // 如果准备阶段失败，回滚所有操作
        if (data.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            throw new NopException(ERR_AMOUNT_TOO_LARGE);
        }

        // Confirm阶段：确认事务
        return OrderResult.success(order);
    }

    @TccCancel
    public void cancelOrder(String orderId) {
        // Cancel阶段：取消事务
        Order order = dao().requireEntityById(orderId);

        // 恢复库存
        inventoryService.increaseInventory(order.getProductId(), order.getQuantity());

        // 删除订单
        dao().deleteEntity(order);
    }
}

@BizModel("Inventory")
public class InventoryBizModel {

    @Inject
    private IInventoryDao inventoryDao;

    @Override
    public void decreaseInventory(String productId, int quantity) {
        Inventory inventory = inventoryDao.requireEntityById(productId);
        if (inventory.getQuantity() < quantity) {
            throw new NopException(ERR_INSUFFICIENT_INVENTORY);
        }
        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventoryDao.saveEntity(inventory);
    }

    @Override
    public void increaseInventory(String productId, int quantity) {
        Inventory inventory = inventoryDao.requireEntityById(productId);
        inventory.setQuantity(inventory.getQuantity() + quantity);
        inventoryDao.saveEntity(inventory);
    }
}
```

### 2. Saga模式

使用Saga模式实现长事务：

```java
@BizModel("Travel")
public class TravelBizModel {

    @Inject
    private IFlightBizModel flightBizModel;
    @Inject
    private IHotelBizModel hotelBizModel;
    @Inject
    private IPaymentBizModel paymentBizModel;

    @BizMutation
    @SagaTransaction
    public TravelResult bookTravel(String userId, TravelRequest request) {
        // Step 1: 预订航班
        FlightBooking flight = flightBizModel.bookFlight(request.getFlight());
        try {
            // Step 2: 预订酒店
            HotelBooking hotel = hotelBizModel.bookHotel(request.getHotel());
            try {
                // Step 3: 支付
                Payment payment = paymentBizModel.pay(request.getPayment());
                return TravelResult.success(flight, hotel, payment);
            } catch (PaymentException e) {
                // 支付失败，取消酒店预订
                hotelBizModel.cancelHotel(hotel.getBookingId());
                throw e;
            }
        } catch (HotelException e) {
            // 酒店预订失败，取消航班预订
            flightBizModel.cancelFlight(flight.getBookingId());
            throw e;
        }
    }
}
```

## 嵌套事务

### 1. 嵌套调用

事务可以嵌套调用：

```java
@BizModel("Order")
public class OrderBizModel {

    @Inject
    private IInventoryBizModel inventoryBizModel;

    @BizMutation
    @Transactional
    public void createOrderWithItems(OrderData data) {
        // 1. 创建订单
        Order order = new Order();
        BeanTool.copyProperties(order, data);
        dao().saveEntity(order);

        // 2. 调用其他BizModel方法（同一事务）
        for (OrderItemData itemData : data.getItems()) {
            // 这个方法也使用@Transactional，会加入当前事务
            inventoryBizModel.decreaseInventory(itemData.getProductId(), itemData.getQuantity());
        }
    }
}

@BizModel("Inventory")
public class InventoryBizModel {

    @BizMutation
    @Transactional
    public void decreaseInventory(String productId, int quantity) {
        Inventory inventory = inventoryDao.requireEntityById(productId);
        if (inventory.getQuantity() < quantity) {
            throw new NopException(ERR_INSUFFICIENT_INVENTORY);
        }
        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventory.setUpdateTime(new Date());
        inventoryDao.saveEntity(inventory);
    }
}
```

### 2. 内部事务

在事务中启动独立事务：

```java
@BizModel("Report")
public class ReportBizModel {

    @Inject
    private ITransactionTemplate transactionTemplate;

    @BizMutation
    @Transactional
    public void generateReport(String userId) {
        // 外部事务
        generateUserReport(userId);

        // 启动独立事务生成统计报告
        transactionTemplate.runInTransaction(Propagation.REQUIRES_NEW, () -> {
            generateStatisticsReport();
        });

        // 继续外部事务
        generateSummaryReport(userId);
    }

    private void generateUserReport(String userId) {
        User user = userDao.requireEntityById(userId);
        // 生成用户报告...
    }

    private void generateStatisticsReport() {
        // 生成统计报告（独立事务）...
    }

    private void generateSummaryReport(String userId) {
        // 生成汇总报告（外部事务）...
    }
}
```

## 事务性能优化

### 1. 缩小事务边界

事务尽可能小：

```java
// ✅ 推荐：小事务
@BizMutation
@Transactional
public void updateUser(String userId, String name) {
    User user = dao().requireEntityById(userId);
    user.setName(name);
    dao().saveEntity(user);
}

// ❌ 不推荐：大事务
@BizMutation
@Transactional
public void processOrder(Order order) {
    // 1. 创建订单
    dao().saveEntity(order);

    // 2. 更新库存
    updateInventory(order);

    // 3. 发送邮件
    sendEmail(order);

    // 4. 记录日志
    logOrder(order);

    // 5. 调用外部服务
    callExternalAPI(order);

    // 事务时间过长
}
```

### 2. 避免事务中IO操作

不要在事务中执行IO操作：

```java
@BizMutation
@Transactional
public void createUser(User user) {
    dao().saveEntity(user);
}

@AfterTransactionCommit
public void afterCreateUser(User user) {
    // 事务提交后执行IO操作
    sendWelcomeEmail(user);
}
```

### 3. 使用批量操作

使用批量方法减少数据库交互：

```java
@BizMutation
@Transactional
public void batchCreateUsers(List<User> users) {
    // ✅ 推荐：批量操作
    dao().batchSaveEntities(users);

    // ❌ 不推荐：循环操作
    for (User user : users) {
        dao().saveEntity(user);
    }
}
```

## 事务调试

docs-for-ai 不提供基于 Spring 组件模型/注解的“事务事件监听/监控/AOP”示例（例如 `@Component`、`@Around` 等），因为这类写法很容易与 Nop 平台实际机制不一致。

如果需要补充事务日志/监控能力，请以仓库真实实现为准，按以下流程处理：

1. 先在仓库源码中搜索确认可用的扩展点（相关注解/接口/事件）。
2. 只给出能在仓库中找到依据的示例（类名/包名/注解均可验证）。

参考：

- `docs-for-ai/getting-started/core/transaction-guide.md`
- `docs-for-ai/getting-started/nop-vs-traditional-frameworks.md`

## 常见问题

### Q1: 事务没有生效？

A: 检查以下内容：
1. 方法是否被外部调用（绕过代理）
2. 方法是否为public
3. 是否使用了final
4. 同一个类内部调用没有经过代理

### Q2: 事务超时如何设置？

A: 使用timeout参数：
```java
@Transactional(timeout = 30)
```

### Q3: 如何回滚部分操作？

A: 使用setRollbackOnly：
```java
throw new NopException(ERR_ERROR)
    .setRollbackOnly(true);
```

### Q4: 嵌套事务如何控制？

A: 使用不同的传播级别：
- REQUIRED：加入当前事务
- REQUIRES_NEW：创建新事务
- MANDATORY：必须在事务中

## 相关文档

- [事务管理指南](../getting-started/core/transaction-guide.md)
- [服务层开发指南](../getting-started/service/service-layer-development.md)
- [异常处理指南](../getting-started/core/exception-guide.md)
- [完整CRUD示例](./complete-crud-example.md)

## 总结

Nop Platform提供了强大而灵活的事务管理能力：

1. **声明式事务**: 使用@Transactional注解简单配置
2. **编程式事务**: 使用ITransactionTemplate精确控制
3. **传播级别**: REQUIRED、REQUIRES_NEW、MANDATORY等
4. **隔离级别**: READ_COMMITTED、REPEATABLE_READ、SERIALIZABLE
5. **事务监听器**: 事务提交后、回滚后执行操作
6. **分布式事务**: TCC、Saga模式
7. **性能优化**: 缩小事务边界、避免IO操作、使用批量方法
8. **事务调试**: 日志记录、性能监控

通过合理使用这些事务特性，可以构建数据一致性高、性能良好的应用系统。

---

**文档版本**: 1.0
**最后更新**: 2025-01-09
**作者**: AI Assistant (Sisyphus)
