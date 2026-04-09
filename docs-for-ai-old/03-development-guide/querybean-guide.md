# QueryBean 使用规范

`QueryBean` 是 Nop 平台的通用结构化查询对象。

本文档只强调 AI 在普通开发中最需要掌握的部分：

1. 如何构造 `QueryBean`
2. 在 BizModel 内部和跨 BizModel / Processor 中分别怎么执行
3. 为什么不要在普通 BizModel 中直接 `dao().findAllByQuery(...)`

---

## 一、基本构造

```java
QueryBean query = new QueryBean();
query.addFilter(FilterBeans.eq("status", 1));
query.addOrderField("createTime", true);
query.setLimit(20);
```

常用部分：

- `filter`
- `orderBy`
- `offset`
- `limit`
- `leftJoinProps`

---

## 二、在不同层里的执行方式

### 1. BizModel 内部

```java
@BizQuery
public List<Order> getOrdersByUser(@Name("userId") String userId,
                                   FieldSelectionBean selection,
                                   IServiceContext context) {
    QueryBean query = new QueryBean();
    query.addFilter(FilterBeans.eq("userId", userId));
    return doFindList(query, selection, context);
}
```

### 2. BizModel 内部分页

```java
@BizQuery
public PageBean<Order> search(@Name("status") Integer status,
                              FieldSelectionBean selection,
                              IServiceContext context) {
    QueryBean query = new QueryBean();
    query.addFilter(FilterBeans.eq("status", status));
    return doFindPage(query, selection, context);
}
```

### 3. Processor / 跨 BizModel

```java
@Inject
protected IOrderBiz orderBiz;

public List<Order> loadOrders(String userId, IServiceContext context) {
    QueryBean query = new QueryBean();
    query.addFilter(FilterBeans.eq("userId", userId));
    return orderBiz.findList(query, null, context);
}
```

---

## 三、普通 BizModel 中不要这样执行

```java
dao().findAllByQuery(query)
dao().findPageByQuery(query)
```

原因：

- `doFindList()` / `doFindPage()` 会走 `CrudBizModel` 统一流程
- 会附加数据权限、逻辑删除处理、最大分页限制、排序补全等行为

源码锚点：

- `io.nop.biz.crud.CrudBizModel#prepareFindPageQuery`

---

## 四、常用 FilterBeans

```java
FilterBeans.eq("status", 1);
FilterBeans.in("id", ids);
FilterBeans.contains("name", keyword);
FilterBeans.and(filter1, filter2);
FilterBeans.or(filter1, filter2);
FilterBeans.isNull("deletedAt");
```

---

## 五、leftJoinProps 和选择字段

可以使用：

```java
query.setLeftJoinProps(Arrays.asList("roles", "departments"));
```

但要注意：

- 普通业务场景不要随意扩大 left join 范围
- `CrudBizModel` 会校验允许的 `leftJoinProps` 和数量上限

---

## 六、常见坑

1. 在 BizModel 内部直接 `dao().findAllByQuery(query)`
2. 不设置 limit，导致列表查询无边界
3. 手工拼接字符串条件，而不是用 `FilterBeans`
4. 在 Processor 中误用 `doFindList()` 这类受保护方法

---

## 七、相关文档

- `./bizmodel-guide.md`
- `./data-access.md`
- `../12-tasks/custom-query-with-querybean.md`
- `../13-reference/source-anchors.md`
