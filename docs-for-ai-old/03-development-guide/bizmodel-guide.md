# BizModel 编写指南

BizModel 是 Nop 平台业务逻辑的主入口。

本文档是 AI 生成服务层代码时的 canonical doc。

默认规则：

1. 实体型服务优先 `@BizModel + extends CrudBizModel<T>`
2. 普通查询/取数优先 `requireEntity()`、`doFindList()`、`doFindPage()`
3. 写操作优先 `@BizMutation`
4. 跨 BizModel 调用优先 `I*Biz` 接口
5. 参数复杂时用 `@RequestBean`，多字段返回用 `@DataBean`

---

## 一、最小结构

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> implements IOrderBiz {

    public OrderBizModel() {
        setEntityName(Order.class.getName());
    }
}
```

---

## 二、方法规则

### 1. 注解

| 注解 | 用途 |
|------|------|
| `@BizQuery` | 查询 |
| `@BizMutation` | 修改，自动事务 |
| `@BizAction` | 内部动作或特殊动作 |

### 2. 参数

1. 业务参数使用 `@Name`
2. 最后一个参数是 `IServiceContext`
3. 参数多时用 `@RequestBean`

### 3. 返回值

1. 返回实体本身时可直接返回实体
2. 返回多个字段时定义 `@DataBean` DTO
3. 避免 `Map<String, Object>` 作为复杂返回类型

---

## 三、数据访问默认规则

### 获取实体

```java
Order order = requireEntity(orderId, "cancel", context);
```

### 列表查询

```java
QueryBean query = new QueryBean();
query.addFilter(FilterBeans.eq("userId", userId));
return doFindList(query, selection, context);
```

### 分页查询

```java
return doFindPage(query, selection, context);
```

### 更新实体

```java
order.setStatus(OrderConstants.CANCELLED);
updateEntity(order, "cancel", context);
```

### 前端 Map 数据

```java
return save(data, context);
return update(data, context);
```

---

## 四、普通 BizModel 中避免的写法

1. `dao().getEntityById(id)`
2. `dao().findAllByQuery(query)`
3. `dao().findPageByQuery(query)`
4. `dao().saveEntity(entity)`
5. `dao().updateEntity(entity)`
6. `dao().deleteEntity(entity)`
7. `@BizMutation @Transactional`
8. `@Inject private Foo foo;`

---

## 五、I*Biz 接口

### 什么时候需要

| 场景 | 是否需要 |
|------|----------|
| 被其他 BizModel 调用 | 需要 |
| 只通过 GraphQL/REST 暴露 | 可不建 |
| 需要 Delta 替换/扩展 | 建议建 |

### 典型写法

```java
public interface IOrderBiz extends ICrudBiz<Order> {
    @BizMutation("cancel")
    Order cancel(@Name("orderId") String orderId, IServiceContext context);
}
```

```java
@Inject
protected IOrderBiz orderBiz;
```

不要直接注入另一个 BizModel 实现类。

---

## 六、DTO 规则

### 输入 DTO

```java
@DataBean
public class SubmitOrderRequest implements Serializable {
    private List<String> cartIds;
    private String addressId;
}
```

```java
@BizMutation
public SubmitOrderResult submitOrder(@RequestBean SubmitOrderRequest request,
                                     IServiceContext context) {
    // ...
}
```

### 输出 DTO

```java
@DataBean
public class SubmitOrderResult implements Serializable {
    private String orderId;
    private BigDecimal totalPrice;
}
```

---

## 七、复杂逻辑拆分

默认顺序：

1. Entity：纯状态判断/计算
2. BizModel：单个业务接口入口
3. Processor：复杂流程 orchestration
4. Step：已经稳定复用的单一动作

---

## 八、事务与副作用

写操作默认只用：

```java
@BizMutation
```

提交后副作用默认用：

```java
txn().afterCommit(null, () -> {
    notifyPaid(order);
});
```

---

## 九、源码锚点

- `io.nop.biz.crud.CrudBizModel`
- `io.nop.orm.biz.ICrudBiz`
- `io.nop.biz.service.BizActionInvoker`
- `io.nop.job.biz.INopJobScheduleBiz`
- `io.nop.job.service.entity.NopJobScheduleBizModel`

## 十、相关文档

- `./crud-development.md`
- `./service-layer.md`
- `../12-tasks/write-bizmodel-method.md`
- `../12-tasks/add-cross-module-biz-interface.md`
- `../12-tasks/create-request-response-dto.md`
- `../13-reference/source-anchors.md`
