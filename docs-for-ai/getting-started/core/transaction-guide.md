# 事务管理指南

## 概述

Nop平台提供了灵活的事务管理机制，支持声明式事务（注解）和编程式事务（模板）。事务管理基于`ITransactionTemplate`接口，支持多种传播级别和事务组。

**核心接口**：`io.nop.dao.txn.ITransactionTemplate`
**事务注解**：`io.nop.api.core.annotations.txn.Transactional`

## 事务传播级别

TransactionPropagation定义了事务的传播行为：

| 传播级别 | 描述 |
|---------|------|
| `REQUIRED` | 默认。如果当前存在事务，则加入；否则创建新事务 |
| `REQUIRES_NEW` | 总是创建新事务，如果当前存在事务，则挂起当前事务 |
| `MANDATORY` | 必须在已有事务中运行，否则抛出异常 |
| `SUPPORTS` | 如果存在事务则加入，否则以非事务方式运行 |
| `NOT_SUPPORTED` | 总是以非事务方式运行，如果存在事务则挂起 |
| `NEVER` | 从不以事务方式运行，如果存在事务则抛出异常 |
| `NESTED` | 如果存在事务，则创建嵌套事务；否则创建新事务 |

## 声明式事务（注解方式）

### @Transactional注解

```java
import io.nop.api.core.annotations.txn.Transactional;

@BizMutation
@Transactional
public void updateUser(String userId, String newName) {
    NopAuthUser user = dao().requireEntityById(userId);
    user.setName(newName);
    dao().saveEntity(user);
}
```

### 指定传播级别

```java
@Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
public void logAction(String userId, String action) {
    // 总是在新事务中执行
}
```

### 只读事务

```java
@Transactional(readOnly = true)
@BizQuery
public User getUserById(String userId) {
    return dao().getEntityById(userId);
}
```

### 指定事务组

```java
@Transactional(txnGroup = "other-datasource")
public void writeToOtherDs(Data data) {
    // 使用指定的数据源事务
}
```

### TCC事务

```java
@TccTransactional
public void tccOperation(String id) {
    // TCC模式的事务
}
```

## 编程式事务（模板方式）

### 使用ITransactionTemplate

```java
@Inject
protected ITransactionTemplate txnTemplate;

public void updateUserData(String userId, String newName) {
    txnTemplate.runInTransaction(txn -> {
        NopAuthUser user = dao().requireEntityById(userId);
        user.setName(newName);
        dao().saveEntity(user);
        // 事务提交前
        txn.beforeCommit(() -> {
            log.info("About to commit transaction");
        });
        // 事务提交后
        txn.afterCommit(() -> {
            log.info("Transaction committed successfully");
        });
    });
}
```

### 使用事务组

```java
public void multiDataSourceOperation(String userId) {
    // 主数据源事务
    txnTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn -> {
        NopAuthUser user = dao().requireEntityById(userId);
        // ...

        // 其他数据源事务
        txnTemplate.runInTransaction("other-ds", TransactionPropagation.REQUIRES_NEW, txn2 -> {
            // 在other-ds事务中执行
        });
    });
}
```

### 异步事务

```java
public CompletionStage<String> asyncOperation(String userId) {
    return txnTemplate.runInTransactionAsync(null, TransactionPropagation.REQUIRED, txn -> {
        String result = processUser(userId);
        return CompletableFuture.completedFuture(result);
    });
}
```

### 无事务执行

```java
public void nonTransactionalRead(String userId) {
    NopAuthUser user = txnTemplate.runWithoutTransaction(null, () -> {
        return dao().getEntityById(userId);
    });
}
```

## CrudBizModel中的事务

### txn() 方法

CrudBizModel提供了简化的`txn()`方法用于事务管理：

```java
@BizMutation
public void updateUserWithAudit(String userId, String newName) {
    txn(() -> {
        // 更新用户
        NopAuthUser user = dao().requireEntityById(userId);
        user.setName(newName);
        dao().saveEntity(user);

        // 记录审计日志
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setOldName(user.getOldName());
        log.setNewName(newName);
        logDao.saveEntity(log);
    });
}
```

### 事务中的多个操作

```java
@BizMutation
public void transferOrder(String fromOrderId, String toOrderId) {
    txn(() -> {
        // 查询订单
        Order fromOrder = dao().requireEntityById(fromOrderId);
        Order toOrder = dao().requireEntityById(toOrderId);

        // 更新状态
        fromOrder.setStatus("TRANSFERRED");
        toOrder.setStatus("PENDING");

        // 保存
        dao().saveEntity(fromOrder);
        dao().saveEntity(toOrder);

        // 记录转移记录
        TransferRecord record = new TransferRecord();
        record.setFromOrderId(fromOrderId);
        record.setToOrderId(toOrderId);
        recordDao.saveEntity(record);
    });
}
```

## 事务监听器

### 监听事务事件

```java
@Inject
protected ITransactionTemplate txnTemplate;

public void operationWithListeners(String userId) {
    txnTemplate.runInTransaction(txn -> {
        // 业务逻辑
        NopAuthUser user = dao().requireEntityById(userId);
        // ...

        // 提交前回调
        txn.beforeCommit(() -> {
            log.info("Before commit: userId=" + userId);
        });

        // 提交后回调
        txn.afterCommit(() -> {
            log.info("After commit: userId=" + userId);
            // 发送通知等
            notificationService.send("User updated", userId);
        });

        // 完成后回调
        txn.afterCompletion((status, exception) -> {
            if (exception == null) {
                log.info("Transaction completed successfully");
            } else {
                log.error("Transaction failed", exception);
            }
        });
    });
}
```

### 添加和移除监听器

```java
// 添加监听器
txnTemplate.addTransactionListener(null, new ITransactionListener() {
    @Override
    public void onBeforeCommit(ITransaction txn) {
        // 提交前
    }

    @Override
    public void onAfterCommit(ITransaction txn) {
        // 提交后
    }

    @Override
    public void onAfterCompletion(ITransaction txn, CompleteStatus status, Throwable exception) {
        // 完成后
    }
});

// 移除监听器
txnTemplate.removeTransactionListener(null, listener);
```

## 事务回滚

### 自动回滚

当抛出`RuntimeException`或`NopException`时，事务会自动回滚：

```java
@BizMutation
@Transactional
public void updateWithAutoRollback(String userId, String newName) {
    NopAuthUser user = dao().requireEntityById(userId);

    // 检查约束
    if (isInvalidName(newName)) {
        throw new NopException(Errors.ERR_INVALID_USER_NAME)
            .param("name", newName);
    }

    user.setName(newName);
    dao().saveEntity(user);
    // 如果抛出异常，事务自动回滚
}
```

### 手动回滚

```java
public void manualRollbackExample(String userId) {
    txnTemplate.runInTransaction(txn -> {
        try {
            // 执行操作
            NopAuthUser user = dao().requireEntityById(userId);
            // ...

            // 遇到错误时手动回滚
            if (hasError()) {
                txn.markRollbackOnly();
                return;
            }

        } catch (Exception e) {
            // 也可以通过抛出异常来回滚
            throw e;
        }
    });
}
```

## 嵌套事务

### REQUIRED行为

```java
@Transactional
public void outerMethod(String userId) {
    // 开启事务A
    innerMethod(userId);
    // innerMethod加入事务A
}

@Transactional(propagation = TransactionPropagation.REQUIRED)
public void innerMethod(String userId) {
    // 加入外部事务
    NopAuthUser user = dao().requireEntityById(userId);
    dao().saveEntity(user);
}
```

### REQUIRES_NEW行为

```java
@Transactional
public void outerMethod(String userId) {
    // 开启事务A
    innerMethod(userId);
    // innerMethod开启独立的事务B，事务A被挂起
}

@Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
public void innerMethod(String userId) {
    // 在新事务B中执行
    NopAuthUser user = dao().requireEntityById(userId);
    dao().saveEntity(user);
    // 事务B提交，事务A恢复
}
```

## 实际应用示例

### 示例1：订单处理

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> {

    @Inject
    protected InventoryBizModel inventoryBizModel;

    @Inject
    protected PaymentBizModel paymentBizModel;

    @BizMutation
    @Transactional
    public Order createOrder(Order order, List<OrderItem> items) {
        txn(() -> {
            // 1. 保存订单
            Order savedOrder = dao().saveEntity(order);

            // 2. 扣减库存（使用REQUIRES_NEW确保独立事务）
            for (OrderItem item : items) {
                inventoryBizModel.reduceStock(
                    item.getProductId(),
                    item.getQuantity()
                );
                item.setOrderId(savedOrder.getId());
            }

            // 3. 保存订单项
            for (OrderItem item : items) {
                dao().saveEntity(item);
            }

            // 4. 创建支付记录
            Payment payment = new Payment();
            payment.setOrderId(savedOrder.getId());
            payment.setAmount(savedOrder.getTotalAmount());
            payment.setStatus("PENDING");
            paymentBizModel.createPayment(payment);

            return savedOrder;
        });
    }
}
```

### 示例2：批量操作

```java
@BizMutation
@Transactional
public void batchUpdateStatus(List<String> userIds, Integer newStatus) {
    txn(() -> {
        // 批量获取
        List<NopAuthUser> users = dao().batchGetEntitiesByIds(userIds);

        // 批量更新
        for (NopAuthUser user : users) {
            user.setStatus(newStatus);
        }

        // 批量保存
        dao().batchSaveEntities(users);

        // 记录批量操作日志
        BatchOperationLog log = new BatchOperationLog();
        log.setOperation("BATCH_UPDATE_STATUS");
        log.setCount(users.size());
        batchLogDao.saveEntity(log);
    });
}
```

### 示例3：带监听器的事务

```java
@BizMutation
@Transactional
public void updateWithNotification(String userId, String newName) {
    txnTemplate.runInTransaction(txn -> {
        NopAuthUser user = dao().requireEntityById(userId);
        String oldName = user.getName();

        // 更新用户
        user.setName(newName);
        dao().saveEntity(user);

        // 提交后发送通知
        txn.afterCommit(() -> {
            // 发送邮件通知
            emailService.sendUserChanged(userId, oldName, newName);

            // 发送站内消息
            messageService.send(userId, "用户名已更新");

            // 触发事件
            eventPublisher.publish(new UserChangedEvent(userId, oldName, newName));
        });
    });
}
```

### 示例4：补偿事务

```java
@BizMutation
@Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
public void compensatingOperation(String businessId) {
    try {
        // 尝试执行业务操作
        doBusinessOperation(businessId);

    } catch (Exception e) {
        // 执行补偿逻辑
        compensate(businessId);

        // 重新抛出异常
        throw e;
    }
}

private void compensate(String businessId) {
    // 补偿逻辑，如回滚库存、退款等
    // 这里需要独立事务
    txnTemplate.runInTransaction("compensation", TransactionPropagation.REQUIRES_NEW, txn -> {
        // 补偿操作
    });
}
```

## 最佳实践

### 1. 事务边界要小

```java
// 推荐：事务只包含必要的数据库操作
@Transactional
public void updateStatus(String id, Integer status) {
    NopAuthUser user = dao().requireEntityById(id);
    user.setStatus(status);
    dao().saveEntity(user);
}

// 避免：在事务中执行耗时操作
@Transactional
public void updateStatusWithSlowOperation(String id, Integer status) {
    NopAuthUser user = dao().requireEntityById(id);
    user.setStatus(status);
    dao().saveEntity(user);

    // 不要在事务中调用外部服务
    externalService.callSlowApi(); // ❌
}
```

### 2. 正确处理异常

```java
// 推荐：抛出业务异常，事务自动回滚
@Transactional
public void updateWithValidation(String id, String newName) {
    if (!isValidName(newName)) {
        throw new NopException(Errors.ERR_INVALID_NAME)
            .param("name", newName);
    }

    NopAuthUser user = dao().requireEntityById(id);
    user.setName(newName);
    dao().saveEntity(user);
}

// 避免：吞掉异常
@Transactional
public void updateWithSwallowedException(String id, String newName) {
    try {
        NopAuthUser user = dao().requireEntityById(id);
        user.setName(newName);
        dao().saveEntity(user);
    } catch (Exception e) {
        log.error("Error", e);
        // ❌ 不应该吞掉异常
    }
}
```

### 3. 合理使用传播级别

```java
// 推荐：使用REQUIRED（默认）
@Transactional
public void defaultMethod() {
    // 加入已有事务或创建新事务
}

// 推荐：需要独立事务时使用REQUIRES_NEW
@Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
public void independentMethod() {
    // 总是新事务
}

// 推荐：只读操作使用readOnly
@Transactional(readOnly = true)
@BizQuery
public User findById(String id) {
    return dao().getEntityById(id);
}
```

### 4. 避免在事务中执行IO操作

```java
// 推荐：在事务后执行IO操作
@Transactional
public void updateAndNotify(String id, String newName) {
    NopAuthUser user = dao().requireEntityById(id);
    user.setName(newName);
    dao().saveEntity(user);

    // 不要在事务中执行IO操作
    // fileService.writeFile(user); // ❌
}

// 使用事务监听器
@Transactional
public void updateWithAfterCommit(String id, String newName) {
    txnTemplate.runInTransaction(txn -> {
        NopAuthUser user = dao().requireEntityById(id);
        user.setName(newName);
        dao().saveEntity(user);

        // 在提交后执行IO
        txn.afterCommit(() -> {
            fileService.writeFile(user); // ✅
        });
    });
}
```

### 5. 批量操作使用批量方法

```java
@Transactional
public void batchUpdate(List<String> ids, Integer newStatus) {
    txn(() -> {
        List<NopAuthUser> users = dao().batchGetEntitiesByIds(ids);

        // 批量更新
        for (NopAuthUser user : users) {
            user.setStatus(newStatus);
        }

        // 使用批量保存
        dao().batchSaveEntities(users); // ✅

        // 避免循环保存
        // for (NopAuthUser user : users) {
        //     dao().saveEntity(user); // ❌
        // }
    });
}
```

## 常见问题

### Q1: 事务中的异常为什么没有回滚？

**A**: 检查以下几点：
1. 确保异常是`RuntimeException`或`NopException`
2. 确保方法上有`@Transactional`注解或在`txn()`中执行
3. 检查是否被try-catch吞掉了异常

```java
// 错误：异常被吞掉
@Transactional
public void wrongMethod() {
    try {
        dao().saveEntity(entity);
    } catch (Exception e) {
        log.error(e);
        // ❌ 异常被吞掉，事务不会回滚
    }
}

// 正确
@Transactional
public void correctMethod() {
    dao().saveEntity(entity);
    // ✅ 异常会自动传播，事务回滚
}
```

### Q2: 如何在不同的事务中执行多个操作？

**A**: 使用`REQUIRES_NEW`传播级别或指定不同的事务组。

```java
// 方式1：REQUIRES_NEW
@Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
public void independentMethod() {
    // 总是新事务
}

// 方式2：不同事务组
@Transactional(txnGroup = "ds1")
public void method1() {
    // ds1事务
}

@Transactional(txnGroup = "ds2")
public void method2() {
    // ds2事务
}
```

### Q3: 如何在事务提交后执行操作？

**A**: 使用事务监听器。

```java
@Transactional
public void withAfterCommit(String id) {
    txnTemplate.runInTransaction(txn -> {
        // 业务逻辑
        dao().saveEntity(entity);

        // 提交后回调
        txn.afterCommit(() -> {
            // 发送通知、清理缓存等
            notificationService.send("Operation completed");
        });
    });
}
```

### Q4: 嵌套事务的行为是什么？

**A**: 取决于传播级别：
- `REQUIRED`: 加入外部事务
- `REQUIRES_NEW`: 创建新事务，外部事务挂起
- `NESTED`: 创建嵌套事务（如果支持）

```java
@Transactional
public void outer() {
    inner(); // REQUIRED时加入outer的事务
}

@Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
public void inner() {
    // REQUIRES_NEW时创建独立事务
}
```

### Q5: 只读事务有什么用？

**A**: 只读事务可以：
1. 提示数据库优化（如使用只读快照）
2. 防止意外的写操作
3. 在某些数据库中提高性能

```java
@Transactional(readOnly = true)
@BizQuery
public User findById(String id) {
    return dao().getEntityById(id);
}
```

## 性能优化建议

### 1. 合理设置事务超时

```java
@Transactional(timeout = 30) // 30秒超时
public void longRunningOperation(String id) {
    // ...
}
```

### 2. 避免大事务

```java
// 推荐：分批处理
@Transactional
public void processBatch(List<String> ids) {
    List<List<String>> batches = Lists.partition(ids, 100);
    for (List<String> batch : batches) {
        processBatchInTransaction(batch);
    }
}

@Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
public void processBatchInTransaction(List<String> batch) {
    // 处理单个批次
}
```

### 3. 使用批量操作

```java
// 推荐：批量操作
@Transactional
public void batchUpdate(List<Entity> entities) {
    dao().batchSaveEntities(entities);
}
```

## 事务隔离级别

Nop平台使用数据库默认的隔离级别，可以通过配置调整：

```yaml
nop:
  dao:
    jdbc:
      isolation-level: READ_COMMITTED
```

常见的隔离级别：
- `READ_UNCOMMITTED`: 读未提交
- `READ_COMMITTED`: 读已提交（默认）
- `REPEATABLE_READ`: 可重复读
- `SERIALIZABLE`: 串行化

## 相关文档

- [异常处理指南](./exception-guide.md) - 异常处理完整指南
- [IoC容器指南](./ioc-guide.md) - 依赖注入容器使用
- [IEntityDao使用指南](../dao/entitydao-usage.md) - 数据访问接口详解
- [服务层开发指南](../service/service-layer-development.md) - BizModel开发详解
- [GraphQL服务开发指南](../api/graphql-guide.md) - GraphQL API开发

## 总结

Nop平台的事务管理提供了：

1. **灵活的事务控制**：声明式和编程式两种方式
2. **多种传播级别**：满足不同的事务场景需求
3. **事务监听器**：支持事务生命周期钩子
4. **嵌套事务支持**：支持复杂的事务场景
5. **自动回滚**：异常时自动回滚

在实际开发中：
- 简单场景使用`@Transactional`注解
- 复杂场景使用编程式事务
- 批量操作使用批量方法
- 事务边界要尽可能小
- 合理使用传播级别和只读事务
