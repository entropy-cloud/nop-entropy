# NopORM 集合操作符 `_some` 和 `_all` 使用说明

## 概述

NopORM 提供了 `_some` 和 `_all` 两个集合操作符，用于对关联集合进行条件查询。

## 1. `_some` 操作符

### 语义
`_some` 表示"存在至少一个关联元素"满足条件。当多个`_some`条件通过AND连接时，要求**同一个关联元素**同时满足所有这些条件。

### SQL 转换
```sql
-- EQL
o.roles._some.name = 'admin' and o.roles._some.status = 1

-- 转换后的 SQL
exists(
    select 1 from Role r 
    where r.parent_id = o.sid 
      and r.name = 'admin'
      and r.status = 1
)
```

### 使用示例
```sql
-- 查找有活跃管理员角色的用户（同一角色同时满足两个条件）
select u from User u 
where u.roles._some.name = 'admin' and u.roles._some.status = 1

-- 查找有管理员角色或有审核角色的用户
select u from User u 
where u.roles._some.name = 'admin' or u.roles._some.name = 'reviewer'

-- 查找有角色名称为admin且级别大于3的用户
select u from User u 
where u.roles._some.name = 'admin' and u.roles._some.level > 3
```

## 2. `_all` 操作符

### 语义
`_all` 表示"所有"关联元素都满足指定条件。

### SQL 转换
```sql
-- EQL: 简单条件
o.roles._all.status = 1

-- 转换后的 SQL
not exists(
    select 1 from Role r 
    where r.parent_id = o.sid 
      and r.status <> 1
)

-- EQL: 多个条件
o.roles._all.status = 1 and o.roles._all.active = true

-- 转换后的 SQL
not exists(
    select 1 from Role r 
    where r.parent_id = o.sid 
      and (r.status <> 1 or r.active <> true)
)
```

### 使用示例
```sql
-- 查找所有角色都活跃的用户
select u from User u 
where u.roles._all.status = 1

-- 查找所有角色都活跃且未删除的用户
select u from User u 
where u.roles._all.status = 1 and u.roles._all.deleted = false

-- 查找没有任何角色被标记为管理员的用户
select u from User u 
where u.roles._all.is_admin = false
```

## 3. 空集合的特殊处理

对于空关联集合：
- `collection._some P` 总是返回 FALSE（不存在任何元素满足P）
- `collection._all P` 总是返回 TRUE（空集上所有条件都满足，称为"空真"）

示例：
```sql
-- 用户没有任何角色
u.roles._some.name = 'admin'  -- 返回 FALSE
u.roles._all.status = 1       -- 返回 TRUE

-- 实际应用：查找有角色的用户
u.roles._some.id is not null  -- 有至少一个角色

-- 查找没有任何角色的用户
u.roles._all.id is null       -- 利用空真特性，所有角色的id都为null（即没有角色）
```

## 4. 条件合并规则

### `_some` 条件自动合并
所有针对同一集合的 `_some` 条件自动合并到同一个 EXISTS 子句：

```sql
-- EQL: 多个AND条件
u.roles._some.name = 'admin' and u.roles._some.status = 1 and u.roles._some.level > 3

-- 转换后的 SQL
exists(
    select 1 from Role r 
    where r.parent_id = u.sid 
      and r.name = 'admin'
      and r.status = 1
      and r.level > 3
)
```

```sql
-- EQL: 多个OR条件  
u.roles._some.name = 'admin' or u.roles._some.name = 'reviewer' or u.roles._some.status = 1

-- 转换后的 SQL
exists(
    select 1 from Role r 
    where r.parent_id = u.sid 
      and (r.name = 'admin' or r.name = 'reviewer' or r.status = 1)
)
```

### 混合 AND/OR 的合并
```sql
-- EQL: 复杂逻辑
(u.roles._some.name = 'admin' or u.roles._some.name = 'reviewer') 
and u.roles._some.status = 1

-- 转换后的 SQL
exists(
    select 1 from Role r 
    where r.parent_id = u.sid 
      and (r.name = 'admin' or r.name = 'reviewer')
      and r.status = 1
)
```

### `_all` 条件合并规则
```
-- AND 条件合并
(all P) AND (all Q) = all (P AND Q)

-- OR 条件不合并
(all P) OR (all Q) = all (P) OR all (Q)
```

```sql
-- EQL: AND 条件合并
u.roles._all.status = 1 and u.roles._all.active = true

-- 转换后的 SQL
not exists(
    select 1 from Role r 
    where r.parent_id = u.sid 
      and (r.status <> 1 or r.active <> true)
)

-- EQL: OR 条件不合并
u.roles._all.status = 1 or u.roles._all.active = true

-- 转换后的 SQL
not exists(select 1 from Role r where r.parent_id = u.sid and r.status <> 1)
or not exists(select 1 from Role r where r.parent_id = u.sid and r.active <> true)
```

## 5. 否定规则（考虑NULL值）

在理想情况下（不考虑NULL值）：
```
NOT (collection._some P) = collection._all (NOT P)
NOT (collection._all P) = collection._some (NOT P)
```

实际SQL中需要考虑NULL值的影响：
```sql
-- 如果name可能为NULL，以下两个表达式不完全等价
NOT u.roles._some.name = 'admin'
-- 不完全等价于（当存在NULL值时）
u.roles._all.name <> 'admin'

-- 更精确的转换应该考虑NULL
u.roles._all[name <> 'admin' OR name IS NULL]
```

### 否定示例
```sql
-- 没有管理员角色的用户（精确处理NULL）
NOT u.roles._some.name = 'admin'
-- 等价于（考虑NULL值）
u.roles._all[name <> 'admin' OR name IS NULL]

-- 不是所有角色都活跃的用户  
NOT u.roles._all.status = 1
-- 等价于
u.roles._some.status <> 1 or u.roles._some.status is null
```

## 6. NULL 值处理

### `_some` 操作符中的NULL处理
```sql
-- 查找有活跃管理员角色的用户（包括deleted为NULL的情况）
u.roles._some.name = 'admin' 
and (u.roles._some.deleted = false or u.roles._some.deleted is null)

-- 或者使用$nvl函数
u.roles._some.name = 'admin' and u.roles._some.deleted.$nvl(false) = false
```

### `_all` 操作符中的NULL处理
```sql
-- 以下查询：要求所有角色的deleted都不等于true（包括NULL值）
u.roles._all.deleted <> true
-- 这会排除那些有deleted=true角色的用户，但允许deleted=NULL或deleted=false

-- 如果要求所有角色都明确未删除（排除NULL）
u.roles._all.deleted = false  -- 这会要求deleted必须明确为false

-- 要求所有角色都非NULL且状态为1
u.roles._all.status = 1 and u.roles._all.status is not null
```

### 最佳实践
```sql
-- 对于可能为NULL的布尔字段
u.roles._some.active = true  -- 只匹配active=true的记录
u.roles._some.active = false -- 只匹配active=false的记录，不包含NULL

-- 如果需要包含NULL情况的处理
u.roles._some.active = true or u.roles._some.active is null

-- 明确处理三值逻辑
u.roles._all.status = 1 and u.roles._all.status is not null
```

## 7. 操作符选择指南

| 场景 | 推荐操作符 | 示例 | SQL 效果 | 性能考虑 |
|------|------------|------|----------|----------|
| 同一实体满足多个条件 | `_some` | `roles._some.name='admin' and roles._some.status=1` | 单个 EXISTS | 在关联表有索引时性能良好 |
| 同一实体满足任一条件 | `_some` | `roles._some.name='admin' or roles._some.name='reviewer'` | 单个 EXISTS | 性能较好，易于优化 |
| 所有实体都满足条件 | `_all` | `roles._all.status=1` | NOT EXISTS | 在大型集合上注意性能影响 |
| 不存在满足条件的实体 | `_all` + 否定 | `roles._all.status <> 1` | NOT EXISTS(status=1) | 注意NULL值处理 |
| 复杂OR条件 | `_some` | `roles._some.status=1 or roles._some.active=true` | 单个 EXISTS | 优于 `_all` 的OR条件 |
| 简单AND条件 | `_all` | `roles._all.status=1 and roles._all.active=true` | 单个 NOT EXISTS | 可接受，注意集合大小 |
| 处理空集合 | `_all` | `roles._all.id is null` | 利用空真特性 | 性能最佳 |

## 8. 性能优化建议

1. **索引策略**：为关联表的外键字段和常用查询字段建立复合索引
   ```sql
   -- 对于roles表的_some查询优化
   create index idx_roles_parent_status on roles(parent_id, status);
   
   -- 对于_all查询，需要优化反连接
   create index idx_roles_parent_id on roles(parent_id);
   -- 根据数据分布考虑查询字段的索引
   ```

2. **性能特点**：
   - `_some`：当匹配记录较多时性能较好，找到第一个匹配项即可返回
   - `_all`：当不匹配记录较少时性能较好，找到第一个不匹配项即可返回
   - 实际性能取决于数据分布和索引情况

3. **查询优化**：
   - 优先使用 `_some`，特别是在OR条件下
   - 避免在 `_all` 中使用复杂的OR条件组合
   - 考虑使用子查询替代复杂的 `_all` 条件
   - 对于大型集合，考虑分页或限制查询范围

## 9. 最佳实践

1. **优先使用 `_some`**：语义清晰，性能较好
2. **利用自动合并**：系统会自动优化多个 `_some` 条件
3. **明确处理 NULL**：使用 `coalesce` 或显式 NULL 检查
4. **谨慎使用 `_all`**：在大型集合上注意性能影响
5. **合理设计索引**：根据查询模式优化关联表索引
6. **测试边界情况**：特别是空集合和NULL值的情况
7. **理解空真特性**：利用`_all`的空真特性处理空集合场景

### 复杂场景示例
```sql
-- 查找有管理员角色，且所有角色都未删除的用户
select u from User u 
where u.roles._some.name = 'admin' 
  and u.roles._all.deleted = false

-- 查找要么所有角色都活跃，要么有特殊权限的用户
select u from User u 
where u.roles._all.status = 1 
   or u.roles._some.permission = 'special'

-- 查找没有任何角色的用户（利用空真特性）
select u from User u 
where u.roles._all.id is null

-- 查找有角色但没有任何管理员角色的用户
select u from User u 
where u.roles._some.id is not null  -- 有角色
  and u.roles._all.name <> 'admin' and u.roles._all.name is not null  -- 没有管理员角色
```
