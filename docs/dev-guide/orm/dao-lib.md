# 在标签库中直接执行SQL语句

`/nop/orm/xlib/dao.xlib` 提供了直接执行 SQL/EQL 语句的能力。在标签的 body 部分可以通过 xpl 模板动态生成 SQL。

## 基本用法

```xml
<dao:FindPage xpl:lib='/nop/orm/xlib/dao.xlib' offset='0' limit='10'>
    select o from NopAuthUser o
    where o.id = ${id}
</dao:FindPage>
```

## 标签列表

| 标签 | 用途 | 返回值 |
|------|------|--------|
| `dao:FindAll` | 查询所有匹配记录 | `List` |
| `dao:FindPage` | 分页查询 | `List`（需指定 offset/limit） |
| `dao:FindFirst` | 查询第一条记录 | 单个对象 |
| `dao:Exists` | 检查是否存在 | `boolean` |
| `dao:ExecuteUpdate` | 执行更新/删除 | 影响行数 |
| `dao:SaveEntity` | 保存实体 | 保存后的实体 |

## 公共属性

| 名称 | 说明 |
|------|------|
| `sqlType` | 值为 `sql` 或 `eql`，指定执行 SQL 还是 EQL 语句（默认 eql） |
| `rowType` | 将查询结果包装为 Java 对象的类名 |
| `querySpace` | 查询空间，对应不同的数据库连接 |
| `timeout` | SQL 执行超时时间（毫秒） |
| `cacheName` | 缓存名称 |
| `cacheKey` | 缓存 key |
| `disableLogicalDelete` | 禁用逻辑删除条件 |

## 参数传递机制

### `${expr}` 自动参数化

在 body 的 EQL/SQL 中，`${expr}` 会被**自动参数化**为 JDBC `?` 参数，防止 SQL 注入：

```
// ${id} 会被转为 ? 参数，id 值从 xpl 作用域传入
<dao:FindFirst xpl:lib="/nop/orm/xlib/dao.xlib">
    select o from NopAuthUser o where o.id = ${id}
</dao:FindFirst>
```

### `${raw(expr)}` 原样拼接

需要原样拼接 SQL 文本（如动态表名、列名）时使用：

```
// ${raw(tableName)} 原样拼接，不做参数化
<dao:ExecuteUpdate xpl:lib="/nop/orm/xlib/dao.xlib">
    delete from ${raw(tableName)} where id = ${id}
</dao:ExecuteUpdate>
```

### `${collection}` 集合展开

集合参数会自动展开为多个 `?` 参数（用于 IN 子句）：

```
// ${ids} 是 List，自动展开为 ?,?,?
<dao:FindAll xpl:lib="/nop/orm/xlib/dao.xlib">
    select o from NopAuthUser o where o.id in (${ids})
</dao:FindAll>
```

> **与 MyBatis 的关键区别**：MyBatis 中 `${}` 是原样替换（有注入风险），xpl sql 模式中 `${}` 默认安全参数化，需要原样拼接时显式使用 `raw()`。

## EQL 语法

EQL（Entity Query Language）是 NopORM 的对象查询语言，语法类似 SQL，支持 `a.b.c` 风格的关联属性（自动转为 JOIN）：

```
// 自动识别 o.dept.name 关联属性，生成 JOIN 查询
<dao:FindAll xpl:lib="/nop/orm/xlib/dao.xlib">
    select o from NopAuthUser o where o.dept.name = ${deptName}
</dao:FindAll>
```

EQL 支持的语法：
- 标准 SQL 语法（SELECT/INSERT/UPDATE/DELETE）
- 关联属性 `a.b.c`（自动 JOIN）
- LEFT JOIN / RIGHT JOIN / FULL JOIN
- 子查询、CTE（WITH 语句）
- FOR UPDATE
- LIMIT / OFFSET

详见 `docs-en/dev-guide/orm/eql.md`。

## 与 orm-reader 的区别

| 场景 | 使用方式 |
|------|----------|
| 在 processor/source 中动态查询 | `dao:FindFirst` / `dao:FindPage`（EQL 在 body 中） |
| 在 batch loader 中批量读取 | `orm-reader`（支持 `<query>` 结构化查询或 `<eql>` EQL） |

`orm-reader` 的 `<query>` 使用结构化查询模型（filter/orderBy），而 `dao:FindFirst` 等标签直接使用 EQL 文本。
