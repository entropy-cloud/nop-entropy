# 高级 EQL 语法使用说明（AI 版）

> 面向大模型：只讲 **怎么写 EQL** 和 **语义直觉**，不介绍内部类名和实现细节。

EQL 可以理解为：**SQL‑92 语法 + JPA/JPQL 风格的属性路径**。

- 大部分语法与 SQL 相同：`SELECT / FROM / WHERE / GROUP BY / HAVING / ORDER BY / JOIN / EXISTS / IN` 等。
- 额外提供：
  - 用实体名和属性路径来写 FROM / WHERE；
  - 自动完成实体之间的 join；
  - 少量集合相关语法（集合当表用、`_some`、`_all`）。

下面只说明这些扩展的**用法**。

---

## 1. FROM 子句：使用「实体」而不是「裸表」

普通 SQL：

```sql
select * from t_order o where o.id = ?
```

EQL 写法（示例）：

```eql
select o
from com.example.entity.Order o
where o.id = :id
```

- `com.example.entity.Order`：实体全类名（具体名字以你的 ORM 模型为准）。
- `o`：别名，后续所有字段访问都通过它。
- `:id`：命名参数，等价于 `?`，但更可读。

> 记忆：把 SQL 里的表名替换为「实体类名」，列名替换为「属性名」。

---

## 2. 属性路径：通过点 `. ` 访问关联对象

在 EQL 中可以直接通过属性路径访问关联对象的字段，而不必手写 join 条件。

### 2.1 简单属性

```eql
select o
from com.example.entity.Order o
where o.status = :status
  and o.createTime >= :fromTime
```

和 SQL 类似，只是字段从 `o.status` 变成了「实体的属性」。

### 2.2 关联属性（to‑one）

例如：订单有一个客户：`Order.customer`。

```eql
select o
from com.example.entity.Order o
where o.customer.name = :customerName
```

语义：

- `o.customer.name` 就是「订单的客户名称」。
- 不需要手写 `join t_customer c on o.customer_id = c.id`，框架会根据实体关系自动补全。

### 2.3 深层路径

如果关系链更长，也可以继续点下去：

```eql
where o.customer.region.country = :countryCode
```

> 只要 ORM 里声明了相应的关联关系，就可以写属性路径，join 会自动补。

---

## 3. JOIN 写法：SQL 风格和属性风格

### 3.1 SQL 风格 JOIN（手写 ON）

你可以像写 SQL 那样显式写 join：

```eql
select o
from com.example.entity.Order o
  join com.example.entity.Customer c on o.customerId = c.id
where c.level = :level
``+

适合：

- 特殊 join 条件；
- 复杂多表关联。

### 3.2 属性风格 JOIN（推荐）

更常见的是用属性语法，让框架根据实体关系生成 ON 条件：

```eql
select o
from com.example.entity.Order o
  left join o.customer c
where c.level = :level
```

这里：

- `left join o.customer c` 表示「把订单的客户连进来，别名叫 c」。
- 实际用哪些列做 join、用 inner 还是 left join，由实体关系配置决定。

### 3.3 多值关联（集合）上的 JOIN

例如：`User.roles` 是用户的角色集合：

```eql
select distinct u
from com.example.entity.User u
  join u.roles r
where r.code = :roleCode
```

理解为：

- 把「用户‑角色表」按照实体关系 join 进来；
- 在 where 里直接用 `r.code` 做过滤。

---

## 4. 集合属性作为表来源

在子查询中，可以直接把集合属性写到 `FROM` 后，用来遍历集合里所有元素。

示例：学院下有班级集合 `College.classes`：

```eql
select c
from com.example.entity.College c
where exists (
  select 1
  from c.classes cls
  where cls.level = :level
)
```

含义：

- `c.classes`：学院 c 的所有班级；
- `from c.classes cls`：遍历这些班级，起别名 `cls`；
- `exists (...)`：只要有一个班级满足 `cls.level = :level` 条件，就算符合。

你可以在内部像普通表一样使用 `cls`：

```eql
where exists (
  select 1
  from c.classes cls
  where cls.level = :level
    and cls.status = 'OPEN'
)
```

> 规则：集合路径只能出现在它所属对象可见的作用域中，比如上例中，外层必须有 `from ... c` 才能在子查询里写 `from c.classes cls`。

---

## 5. 集合谓词 `_some` / `_all`

EQL 为集合判断提供了两个操作符：`_some` 和 `_all`，写法都是「**集合属性 + `_some/_all` + 普通字段**」。

### 5.1 `_some`：存在至少一个元素满足条件

语义：

- 「存在至少一个关联元素满足条件」。
- 多个针对同一集合的 `_some` 条件通过 AND 连接时，要求**同一个元素**同时满足这些条件。

典型写法：

```eql
-- 有角色名为 admin 且状态为 1 的用户（同一角色同时满足两个条件）
select u
from com.example.entity.User u
where u.roles._some.name = 'admin'
  and u.roles._some.status = 1

-- 有管理员角色或审核角色的用户（任一角色满足其一即可）
select u
from com.example.entity.User u
where u.roles._some.name = 'admin'
   or u.roles._some.name = 'reviewer'
```

可以把 `_some` 当成「对集合做 EXISTS 查询」的语法糖：

- `_some` 关注「**是否至少有一个**」满足条件；
- 同一集合上的多个 `_some` 条件会自动合并到一个判断里，避免生成多层嵌套。

### 5.2 `_all`：所有元素都满足条件

语义：

- 「所有关联元素都满足条件」。
- 可以理解为「不存在不满足条件的元素」。

典型写法：

```eql
-- 所有角色都处于活跃状态
select u
from com.example.entity.User u
where u.roles._all.status = 1

-- 所有角色都活跃且未删除
select u
from com.example.entity.User u
where u.roles._all.status = 1
  and u.roles._all.deleted = false
```

直觉上可以把 `_all` 看作是「对集合做 NOT EXISTS 反例检查」的语法糖：

- `_all` 关注「**是否所有元素**」满足条件；
- 多个 `_all` 条件用 AND 连接时，会合并到同一个「所有条件都成立」的判断里。

### 5.3 空集合的语义

对空集合，EQL 采用数学上的「空真」规则：

- `collection._some P`：总是 **FALSE**（没有任何元素满足）；
- `collection._all P`：总是 **TRUE**（在空集合上，所有条件都被视为成立）。

常见用法示例：

```eql
-- 查有至少一个角色的用户
u.roles._some.id is not null

-- 查没有任何角色的用户（利用空真）
u.roles._all.id is null
```

> 使用建议：
> - 需要「有至少一个满足」时，用 `_some`；
> - 需要「所有都满足」或借助空真处理「没有任何元素」场景时，用 `_all`；
> - 如果更习惯纯 SQL，可以改写成 `EXISTS` / `NOT EXISTS + 集合表来源`，语义与这里一致。

---

## 6. 默认过滤的直观理解

在多数场景，EQL 查询会自动附加一些「隐式过滤条件」，例如：

- 逻辑删除：只查「未删除」数据；
- 多租户：只查当前租户数据；
- 实体级 Filter：根据实体配置自动附加固定过滤条件。

对使用者来说：

- 一般不需要在 EQL 里重复写这些条件；
- 只要 ORM 模型配置正确，EQL 会自动带上这些保护；
- 如果需要绕过其中某些行为（例如查含删除数据），应优先考虑提供专门接口或配置，而不是在普通 EQL 里硬编码绕过逻辑。

---

## 7. 从 SQL 迁移到 EQL 的速记表

| 需求 | SQL 思路 | EQL 写法示例 |
|------|----------|--------------|
| 简单查询 | `select * from t_order o` | `select o from com.example.entity.Order o` |
| 按主键查 | `where o.id = ?` | `where o.id = :id` |
| 访问关联字段 | 手写 join + `c.name` | `o.customer.name` |
| 关联过滤 | `join t_customer c on o.customer_id = c.id and c.level = ?` | `join o.customer c` 然后 `where c.level = :level` |
| 遍历集合 | 额外 from 子表 + on 条件 | `from o.items i` 或 `join o.items i` |
| 判断存在某元素 | `exists (select 1 from ...)` | `_some` 或 `exists + 集合表来源` |
| 所有元素满足条件 | NOT EXISTS 组合 | `_all` |

> 实际写 EQL 时，可以先按 SQL 写出查询，再把「表名 → 实体名」「列名 → 属性/路径」，剩下的 join 条件交给 EQL 自动处理。这样既符合直觉，又能充分利用 ORM 的能力。
