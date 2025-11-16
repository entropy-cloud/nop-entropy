# NopORM 集合操作增强设计说明

## 概述

为了提供更强大、更直观的集合操作能力，NopORM 在现有 `_some` 和 `_all` 操作符基础上，引入新的集合操作语法：`collection[filter].function()` 元素访问操作符。

## 1. 集合过滤聚合语法

### 1.1 基础语法

```javascript
// 三种使用方式：
o.collection[filter_conditions]  // 只有过滤条件
o.collection[order by sort_spec]  // 只有排序条件  
o.collection[filter_conditions order by sort_spec]  // 过滤 + 排序
```

### 1.2 支持的聚合函数

#### 计数操作
```javascript
// 简单计数
o.roles[status = 1].count() >= 2

// 带条件计数
o.orders[status = 'COMPLETED' and amount > 100].count() >= 5
```

#### 求和操作
```javascript
// 字段求和
o.orders[status = 'COMPLETED'].sum(amount) > 10000

// 数值计算
o.items[category = 'ELECTRONICS'].sum(price * quantity) > 5000
```

#### 平均值操作
```javascript
o.scores[examType = 'FINAL'].avg(score) >= 60
o.products[price > 0].avg(rating) > 4.0
```

#### 极值操作
```javascript
o.transactions[year = 2024].max(amount) < 5000
o.products[category = 'A'].min(price) > 100
```

#### 存在性判断
```javascript
o.roles[roleType = 'SPECIAL'].exists()
// 等价于 o.roles[roleType = 'SPECIAL'].count() >= 1
// 返回布尔值，可直接在逻辑表达式中使用
```

### 1.3 复杂过滤条件

#### 逻辑组合
```javascript
// AND 条件
o.orders[status = 'COMPLETED' and amount > 100 and createTime > '2024-01-01']

// OR 条件  
o.roles[roleName = 'admin' or roleName = 'manager' or level >= 3]

// 复杂逻辑
o.products[(category = 'A' or category = 'B') and (price > 100 or hasDiscount = true)]
```

#### 嵌套属性访问
```javascript
o.orders[customer.age > 18 and customer.region = 'North'].sum(amount)
o.roles[department.budget > 100000].count()
```

### 1.4 多级集合操作

```javascript
// 嵌套集合过滤
o.departments[employees[salary > 10000].count() > 5].count() >= 1

// 复杂聚合链
o.companies[
    departments[budget > 100000].count() >= 2 
    and departments[employees.count() > 50].sum(budget) > 500000
].exists()
```

## 2. 集合元素访问语法

### 2.1 基础元素访问操作符

#### 首元素访问
```javascript
// 只有排序
o.orders[order by createTime desc].first().amount > 1000

// 过滤 + 单字段排序
o.orders[status = 'PENDING' order by createTime desc].first().amount > 1000

// 过滤 + 多字段排序
o.orders[priority = 'HIGH' order by priority desc, createTime asc].first().status = 'URGENT'

// 复杂过滤 + 排序
o.orders[status = 'COMPLETED' and amount > 100 order by createTime desc].first().customer.name = 'John'
```

#### 末元素访问
```javascript
o.orders[status = 'PROCESSING' order by createTime asc].last().status = 'COMPLETED'
```

### 2.2 排序规格语法

#### 方向说明符
```javascript
// 降序
[order by createTime desc]
[order by amount desc, id asc]

// 升序（可省略，默认）
[order by createTime] 
[order by createTime asc]
```

#### 复杂排序表达式
```javascript
// 函数表达式排序
[order by lower(name) asc, length(description) desc]

// 计算字段排序
[order by year(createTime) desc, month(createTime) asc]

// 嵌套属性排序
[order by customer.age desc, customer.name asc]
```

## 3. 边界情况和空集合处理

### 3.1 空集合返回值（与SQL语义一致）

```javascript
// 聚合函数在空集合时返回（与SQL标准一致）：
o.orders[status = 'INVALID'].count()     // 返回 0
o.orders[status = 'INVALID'].sum(amount) // 返回 null
o.orders[status = 'INVALID'].avg(score)  // 返回 null
o.orders[status = 'INVALID'].max(amount) // 返回 null
o.orders[status = 'INVALID'].min(amount) // 返回 null

// 元素访问在空集合时返回：
o.orders[status = 'INVALID'].first()     // 返回 null
o.orders[status = 'INVALID'].last()      // 返回 null
```

### 3.2 空值处理说明

```javascript
// 当聚合函数返回null时，比较操作自然返回false
o.orders[status = 'INVALID'].sum(amount) > 1000  // 返回false

// 如果需要显式处理null值，可以使用$nvl函数
o.orders[status = 'COMPLETED'].sum(amount).$nvl(0) > 10000
```

## 4. 与Collection Operator语法的兼容性

### 4.1 与 `_some` / `_all` 的对应关系

```javascript
// _some 的等价表达
o.roles._some.status = 1
⇔ o.roles[status = 1].exists()

// _all 的等价表达  
o.roles._all.status = 1
⇔ o.roles[status != 1].count() = 0
⇔ !o.roles[status != 1].exists()

// 复杂条件
o.roles._some.roleName = 'admin' or o.roles._some.status = 1
⇔ o.roles[roleName = 'admin' or status = 1].exists()
```

### 4.2 混合使用

```javascript
// 与_some/_all语法可以混合使用
o.roles._some.status = 1 and o.orders[amount > 100].count() >= 5

// 组合使用存在性判断和计数
o.roles[roleType = 'ADMIN'].exists() and o.orders[status = 'PENDING'].count() < 10
```

## 5. SQL 转换策略

### 5.1 基础转换规则

#### 过滤聚合转换（保持SQL语义）
```javascript
// EQL: o.orders[status = 'COMPLETED'].sum(amount) > 10000

// SQL:
(select sum(amount) from Order ord 
 where ord.user_id = o.id and ord.status = 'COMPLETED') > 10000

// 注意：空集合时返回null，与SQL标准一致
```

#### 存在性判断转换
```javascript
// EQL: o.roles[roleType = 'SPECIAL'].exists()

// SQL:
exists (
  select 1 from Role r 
  where r.user_id = o.id and r.role_type = 'SPECIAL'
)
```

#### 元素访问转换（同时过滤和排序）
```javascript
// EQL: o.orders[status = 'PENDING' order by createTime desc].first().amount > 1000

// SQL:
exists (
  select 1 from Order ord 
  where ord.user_id = o.id
    and ord.amount > 1000
    and ord.status = 'PENDING'
    and ord.id = (
      select ord2.id from Order ord2 
      where ord2.user_id = o.id and ord2.status = 'PENDING'
      order by ord2.createTime desc
      limit 1
    )
)
```

#### $nvl 函数转换
```javascript
// EQL: o.orders[status = 'COMPLETED'].sum(amount).$nvl(0) > 10000

// SQL:
coalesce((
  select sum(amount) from Order ord 
  where ord.user_id = o.id and ord.status = 'COMPLETED'
), 0) > 10000
```

### 5.2 优化转换策略

#### Group Join 优化
```javascript
// 多个集合条件自动优化
o.roles[roleType = 'A'].count() >= 2 
and o.roles[roleType = 'B'].count() = 0

// 优化为 Group Join:
select o from User o left join (
  select user_id,
         count(case when role_type = 'A' then 1 else null end) as count_a,
         count(case when role_type = 'B' then 1 else null end) as count_b
  from Role group by user_id
) t on o.id = t.user_id
where t.count_a >= 2 and t.count_b = 0
```

#### 窗口函数优化
```javascript
// 多个元素访问操作
o.orders[status = 'PENDING' order by createTime desc].first().amount > 1000
and o.orders[status = 'COMPLETED' order by createTime asc].first().customerName = 'John'

// 优化为：
select o from User o
where o.id in (
  select user_id from (
    select user_id,
      first_value(amount) over (
        partition by user_id 
        order by case when status = 'PENDING' then createTime end desc
      ) as pending_first_amount,
      first_value(customer_name) over (
        partition by user_id 
        order by case when status = 'COMPLETED' then createTime end asc
      ) as completed_first_customer
    from Order
  ) t 
  where t.pending_first_amount > 1000 
    and t.completed_first_customer = 'John'
    and t.pending_first_amount is not null
    and t.completed_first_customer is not null
)
```

## 6. 性能优化提示

### 6.1 优化器提示语法

```sql
-- 强制使用 Group Join 优化
select o from User o
/*@group_join(o.roles) */
where o.roles[roleType = 'A'].count() >= 2

-- 禁用特定优化
select o from User o  
/*@no_window_function */
where o.orders[status = 'PENDING' order by createTime desc].first().amount > 1000

-- 建议使用索引
select o from User o
/*@index(orders.createTime) */
where o.orders[order by createTime desc].first().amount > 1000
```

### 6.2 最佳实践

```javascript
// 推荐：使用 exists() 进行存在性判断
o.roles[roleType = 'SPECIAL'].exists()

// 推荐：在复杂逻辑中合理组合使用
o.orders[status = 'COMPLETED'].count() >= 5 
and o.orders[status = 'PENDING'].exists()

// 推荐：同时使用过滤和排序
o.orders[status = 'PENDING' and priority = 'HIGH' order by createTime asc].first().assignee = 'currentUser'

// 注意：聚合函数返回null时比较结果为false，通常不需要$nvl
o.orders[status = 'COMPLETED'].sum(amount) > 10000  // 空集合时返回false

// 避免：不必要的复杂表达式
// o.roles[roleType = 'SPECIAL'].exists() == true  // 冗余
```

## 7. 错误处理和验证

### 7.1 编译时验证

```javascript
// 无效字段名 - 编译时报错
o.orders[invalidField = 1].count()  // 错误：字段 invalidField 不存在

// 类型不匹配 - 编译时报错  
o.orders[status = 123].count()  // 错误：status 字段类型为字符串，不能与数字比较

// 无效聚合函数 - 编译时报错
o.orders[status = 'COMPLETED'].invalidFunc()  // 错误：函数 invalidFunc 不存在
```

## 8. 语法规范总结

- **所有集合操作**统一使用函数调用形式：`collection[filter order by ...].function()`
- **语法组合灵活**：过滤和排序都是可选的，可以单独或组合使用
- **无参数函数**：`count()`, `exists()`
- **有参数函数**：`sum(field)`, `avg(field)`, `max(field)`, `min(field)`
- **元素访问**：`first()`, `last()` 返回单个元素，可继续访问属性
- **布尔表达式**：`exists()` 直接作为布尔值使用，无需 `== true`
- **SQL语义一致**：聚合函数在空集合时的行为与SQL标准完全一致
- **空值处理**：聚合函数返回null时，比较操作自然返回false
- **默认值处理**：特殊情况下可使用 `$nvl` 函数提供默认值
- **一致性**：所有操作符遵循统一的语法模式和调用约定