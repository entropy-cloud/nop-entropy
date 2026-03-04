# Nop入门：EQL集合操作符_some和_all

在关系型数据库查询中，我们经常需要根据关联集合来判断主表记录是否符合条件。例如：

- 查找"至少有一个角色是管理员的用户"
- 查找"所有订单都已完成的客户"
- 查找"没有任何未读消息的用户"

传统SQL需要写EXISTS子查询，比较繁琐。NopORM的EQL提供了`_some`和`_all`两个集合操作符，让这类查询变得直观简洁。

## 一、问题场景

假设我们有以下实体关系：

```
User (用户)
  ├─ roles: List<Role>     // 用户拥有的角色（多对多）
  └─ orders: List<Order>   // 用户的订单（一对多）

Order (订单)
  └─ status: String        // 订单状态
  └─ amount: BigDecimal    // 订单金额
```

### 需求1：查找有管理员角色的用户

**SQL写法：**
```sql
SELECT u.* FROM t_user u
WHERE EXISTS (
  SELECT 1 FROM t_user_role ur
  JOIN t_role r ON ur.role_id = r.id
  WHERE ur.user_id = u.id AND r.name = 'admin'
)
```

**EQL写法：**
```eql
select u
from com.example.entity.User u
where u.roles._some.name = 'admin'
```

### 需求2：查找所有订单都已完成的客户

**SQL写法：**
```sql
SELECT u.* FROM t_user u
WHERE NOT EXISTS (
  SELECT 1 FROM t_order o
  WHERE o.user_id = u.id AND o.status != 'COMPLETED'
)
```

**EQL写法：**
```eql
select u
from com.example.entity.User u
where u.orders._all.status = 'COMPLETED'
```

对比一下，EQL的写法更接近自然语言描述："用户的角色中**有某个**名字是admin"、"用户的订单**所有**状态都是COMPLETED"。

## 二、_some操作符

### 2.1 基本语义

`_some`表示"存在至少一个关联元素满足条件"。

```eql
-- 有活跃角色的用户
select u
from com.example.entity.User u
where u.roles._some.status = 'ACTIVE'

-- 有金额大于1000的订单的用户
select u
from com.example.entity.User u
where u.orders._some.amount > 1000
```

### 2.2 多条件组合

当多个`_some`条件用AND连接时，要求**同一个元素**同时满足所有条件：

```eql
-- 有角色名为admin且状态为ACTIVE的用户（同一角色）
select u
from com.example.entity.User u
where u.roles._some.name = 'admin'
  and u.roles._some.status = 'ACTIVE'
```

这等价于：
```sql
WHERE EXISTS (
  SELECT 1 FROM t_user_role ur
  JOIN t_role r ON ur.role_id = r.id
  WHERE ur.user_id = u.id 
    AND r.name = 'admin' 
    AND r.status = 'ACTIVE'
)
```

### 2.3 OR语义

用OR连接时，每个条件独立判断：

```eql
-- 有管理员角色或有审核员角色的用户（不同角色即可）
select u
from com.example.entity.User u
where u.roles._some.name = 'admin'
   or u.roles._some.name = 'reviewer'
```

## 三、_all操作符

### 3.1 基本语义

`_all`表示"所有关联元素都满足条件"。

```eql
-- 所有角色都处于活跃状态
select u
from com.example.entity.User u
where u.roles._all.status = 'ACTIVE'

-- 所有订单金额都小于10000
select u
from com.example.entity.User u
where u.orders._all.amount < 10000
```

### 3.2 多条件组合

多个`_all`条件用AND连接时，合并为"所有元素同时满足所有条件"：

```eql
-- 所有订单都已完成且未删除
select u
from com.example.entity.User u
where u.orders._all.status = 'COMPLETED'
  and u.orders._all.deleted = false
```

### 3.3 空集合的语义

对空集合，EQL采用数学上的"空真"(vacuous truth)规则：

| 操作符 | 空集合结果 | 说明 |
|--------|-----------|------|
| `_some` | FALSE | 没有任何元素满足条件 |
| `_all` | TRUE | 在空集合上，所有条件都被视为成立 |

**利用这个特性：**

```eql
-- 没有任何角色的用户（利用空真：空集合的_all.xxx总是成立）
select u
from com.example.entity.User u
where u.roles._all.id is null

-- 有至少一个角色的用户
select u
from com.example.entity.User u
where u.roles._some.id is not null
```

## 四、实战示例

### 4.1 权限检查

查找"有权限访问财务模块"的用户：

```eql
select u
from com.example.entity.User u
where u.roles._some.permissions._some.code = 'FINANCE_VIEW'
```

这里展示了嵌套使用：用户的角色中，有某个角色的权限中，有某个权限的代码是FINANCE_VIEW。

### 4.2 业务规则检查

查找"所有待处理订单都在24小时内创建"的用户：

```eql
select u
from com.example.entity.User u
where u.orders._all.status = 'PENDING'
  and u.orders._all.createTime >= :yesterday
```

### 4.3 复杂组合

查找"至少有一个已完成订单，且所有订单总额超过10000"的用户：

```eql
select u
from com.example.entity.User u
where u.orders._some.status = 'COMPLETED'
  and u.orders._all.amount > 0
having sum(u.orders._all.amount) > 10000
```

> 注意：上面的示例混合了`_some`和`_all`，实际使用时要注意语义，必要时拆成多个查询更清晰。

## 五、与EXISTS子查询对比

`_some`和`_all`本质上是EXISTS子查询的语法糖，但更易读：

| EQL写法 | 等价SQL |
|--------|--------|
| `u.roles._some.name = 'admin'` | `EXISTS (SELECT 1 FROM roles WHERE ... AND name = 'admin')` |
| `u.roles._all.status = 1` | `NOT EXISTS (SELECT 1 FROM roles WHERE ... AND status != 1)` |

如果你更习惯纯SQL风格，也可以显式写EXISTS：

```eql
select u
from com.example.entity.User u
where exists (
  select 1
  from u.roles r
  where r.name = 'admin'
)
```

但大多数情况下，`_some`和`_all`更简洁直观。

## 六、使用建议

1. **优先用`_some`/`_all`**：比手写EXISTS更简洁，语义更清晰
2. **注意AND组合的语义**：同一集合的多个`_some`条件AND连接时，是"同一元素"满足所有条件
3. **利用空真特性**：`_all`对空集合返回TRUE，可以用来判断"没有任何XXX"
4. **复杂场景拆分**：如果条件过于复杂，考虑拆成多个查询或使用显式EXISTS

## 七、快速参考

| 需求 | 操作符 | 示例 |
|------|--------|------|
| 有至少一个满足 | `_some` | `u.roles._some.name = 'admin'` |
| 所有都满足 | `_all` | `u.orders._all.status = 'DONE'` |
| 没有任何满足 | `_all` + 否定 | `u.roles._all.status != 'ACTIVE'` |
| 空集合判断 | `_all` + 恒假 | `u.roles._all.id is null` |

---

更多EQL高级用法，参见 [ORM EQL高级特性](../../03-development-guide/orm-eql-advanced.md)
