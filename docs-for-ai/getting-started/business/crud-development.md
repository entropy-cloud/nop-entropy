# CRUD开发指南

## 概述

Nop平台提供强大的CRUD开发框架，核心组件是`CrudBizModel`类，它封装了完整的增删改查逻辑。通过继承`CrudBizModel`，开发者可以快速实现业务功能，无需编写重复的基础代码。

CrudBizModel的主要优势：
- 封装了完整的CRUD操作逻辑
- 自动处理多租户、逻辑删除、数据权限等
- 自动执行数据验证和唯一性检查
- 支持单表和主子表CRUD
- 与GraphQL和REST API无缝集成
- 支持事务管理
- 提供丰富的扩展点

## 核心概念

### 1. CrudBizModel
- CRUD业务模型的基类，封装了通用的CRUD操作
- 提供`findPage`、`save`、`update`、`delete`等核心方法
- 支持自定义业务逻辑扩展

### 2. EntityData
- 封装了实体数据的容器类
- 包含原始数据、验证后的数据、实体对象和元模型
- 用于在CRUD操作中传递数据

### 3. BizObject
- 业务对象的封装，包含业务对象名称、元模型等信息
- 用于在GraphQL中注册业务模型

### 4. XMeta
- 业务对象的元数据定义
- 包含字段信息、验证规则、权限配置、唯一键等

## ⭐ 重要原则：避免重复实现

### 1. 不要直接调用 dao() 方法

在 `CrudBizModel` 的派生类中，**不要直接调用** `dao().findPage()`, `dao().saveEntity()` 等方法。

**为什么？**

`CrudBizModel` 已经内置了大量的功能，包括：
- **多租户支持**：自动追加租户过滤条件
- **逻辑删除**：自动过滤已删除记录，支持恢复操作
- **数据权限**：自动检查和过滤
- **数据验证**：自动执行字段验证和业务规则验证
- **唯一性检查**：基于XMeta keys配置自动检查
- **关联操作**：自动处理级联删除、批量加载等

### 2. 优先使用基类方法

| 操作 | ✅ 推荐方法 | ❌ 避免直接调用 |
|------|------------|----------------|
| 查询列表 | `findPage()`, `findList()`, `findFirst()` | `dao().findPageByQuery()` |
| 保存数据 | `save()`, `doSave()` | `dao().saveEntity()` |
| 更新数据 | `update()`, `doUpdate()` | `dao().updateEntity()` |
| 删除数据 | `delete()`, `doDelete()` | `dao().deleteEntity()` |

### 3. 通过回调扩展逻辑

| 扩展点 | 用途 |
|---------|------|
| `defaultPrepareQuery()` | 查询前添加过滤条件 |
| `defaultPrepareSave()` | 保存前设置字段值 |
| `defaultPrepareUpdate()` | 更新前设置字段值 |
| `defaultPrepareDelete()` | 删除前执行检查 |

## 快速开始

### 1. 创建业务模型类

```java
@BizModel("MyEntity")
public class MyEntityBizModel extends CrudBizModel<MyEntity> {
    @Override
    public String getEntityName() {
        return "MyEntity";
    }
}
```

### 2. 访问CRUD API

- GraphQL API：`http://localhost:8080/graphql`
- REST API：`http://localhost:8080/r/MyEntity__findPage`

## 典型开发模式

### 1. 标准 CRUD（无自定义逻辑）

```java
@BizModel("MyEntity")
public class MyEntityBizModel extends CrudBizModel<MyEntity> {
    // ✅ 直接使用内置方法，无需重写
}
```

### 2. 添加自定义逻辑

```java
@BizModel("MyEntity")
public class MyEntityBizModel extends CrudBizModel<MyEntity> {
    @Override
    protected void defaultPrepareSave(EntityData<MyEntity> entityData, IServiceContext context) {
        super.defaultPrepareSave(entityData, context);

        // 添加自定义逻辑
        MyEntity entity = entityData.getEntity();
        entity.setCustomField("customValue");
    }
}
```

**注意**：审计字段（createTime, updateTime, createBy, updateBy）由框架自动设置，无需手动设置。

### 3. 自定义查询逻辑

```java
@BizModel("MyEntity")
public class MyEntityBizModel extends CrudBizModel<MyEntity> {
    @Override
    protected void defaultPrepareQuery(QueryBean query, IServiceContext context) {
        super.defaultPrepareQuery(query, context);

        // 添加默认过滤条件
        query.addFilter(FilterBeans.eq("status", 1));        
}
    }
}
```

### 4. 自定义业务方法

```java
@BizModel("MyEntity")
public class MyEntityBizModel extends CrudBizModel<MyEntity> {
    @BizMutation
    @GraphQLReturn(bizObjName = "MyEntity")
    public MyEntity publish(@Name("id") String id, IServiceContext context) {
        // 使用回调进行自定义逻辑
        return doUpdate(Map.of("id", id, "status", 2), null, (entityData, ctx) -> {
            MyEntity e = entityData.getEntity();
            e.setPublishTime(new Date());
            // 注意：updateBy 字段由框架自动设置，无需手动设置
            e.setPublisher(context.getUserId());
        }, context);
    }
}
```

### 5. 批量操作

```java
@BizModel("MyEntity")
public class MyEntityBizModel extends CrudBizModel<MyEntity> {
    @BizMutation
    public void batchPublish(@Name("ids") Set<String> ids, IServiceContext context) {
        // ✅ 使用内置方法批量操作
        for (String id : ids) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", id);
            data.put("status", 2);
            update(data, context);
        }
    }
}
```

## XMeta 配置

### 1. 对象级别配置
filter和orderBy会全局起作用，始终携带这些条件。
```xml
<meta x:schema="/nop/schema/xmeta.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <entityName>MyEntity</entityName>
    <primaryKey>id</primaryKey>

    <!-- 唯一键配置 -->
    <keys>
        <key name="uk_name" props="name" displayName="名称"/>
    </keys>

    <!-- 自动应用的过滤器 -->
    <filter>
        <eq name="status" value="1"/>
    </filter>

    <!-- 默认排序 -->
    <orderBy>
        <sort name="createTime" order="desc"/>
    </orderBy>

    <!-- 属性定义 -->
    <props>
        <!-- ... -->
    </props>
</meta>
```

### 2. 配置说明

| 配置项 | 作用 |
|--------|------|
| **keys** | 定义除主键外的唯一键，框架会自动检查唯一性 |
| **filter** | 自动应用到所有查询的过滤条件 |
| **orderBy** | 所有查询的默认排序规则 |

### 3. 唯一性检查

在 XMeta 中定义 `keys` 后，框架会自动进行唯一性检查：

```xml
<!-- 单字段唯一键 -->
<keys>
    <key name="uk_name" props="name" displayName="名称"/>
</keys>

<!-- 组合唯一键 -->
<keys>
    <key name="uk_code_tenant" props="code,tenantId" displayName="编码"/>
</keys>
```

**使用方式**：
- ✅ 在 XMeta 中配置 keys
- ✅ 直接使用 `save()` 和 `update()` 方法
- ❌ 不要手动实现唯一性检查

**优势**：
- 声明式配置，易于维护
- 框架自动检查，性能优化
- 清晰的错误提示

## 常见问题

### Q1: BizMutation 需要手动开启事务吗？

**答案**：

**不需要**。`@BizMutation` 注解的方法会**自动开启事务**：

```java
// ✅ 正确：无需手动开启事务
@BizMutation
public MyEntity saveEntity(@Name("data") Map<String, Object> data, IServiceContext context) {
    return save(data, context);
    // 框架自动在方法执行前开启事务，方法执行后提交事务
}
```

**事务机制**：
- `GraphQLTransactionOperationInvoker` 检测到操作类型为 `mutation` 时，自动使用事务调用器
- `BizActionInvoker` 使用 `ITransactionTemplate.runInTransaction()` 执行，传播类型为 `REQUIRED`
- 如果方法执行成功，自动提交事务
- 如果方法抛出异常，自动回滚事务

**注意**：
- ✅ 使用 `@BizMutation` 注解标记修改操作，自动开启事务
- ❌ 不需要在 `@BizMutation` 方法中手动使用 `@Transactional` 或 `txn()` 开启事务
- ℹ️ `@BizQuery` 注解的方法（查询操作）不会自动开启事务

### Q2: 什么时候需要手动开启事务？

**答案**：

仅在以下特殊场景需要手动开启事务：
- 需要在方法执行中调用多个独立的 `@BizMutation` 方法
- 需要嵌套事务控制
- 需要手动控制事务提交或回滚时机

```java
// 特殊场景：需要手动开启事务
@BizMutation
public void complexOperation(IServiceContext context) {
    txn().runInTransaction(() -> {
        // 执行多个需要事务的操作
        save(data1, context);
        update(data2, context);
        delete(id, context);
    });
}
```

### Q3: 为什么不要直接调用 dao() 方法？

**答案**：

`CrudBizModel` 内置方法已经处理了：
- ✅ 多租户过滤
- ✅ 逻辑删除过滤
- ✅ 数据权限检查
- ✅ 数据验证
- ✅ 唯一性检查

直接调用 `dao()` 会跳过这些功能，需要手动处理，容易遗漏和出错。

### Q4: 如何实现唯一性检查？

**答案**：

在 XMeta 中配置 keys，让框架自动检查：

```xml
<!-- XMeta 配置 -->
<keys>
    <key name="uk_name" props="name" displayName="名称"/>
</keys>

<!-- 业务代码 - 无需手动检查 -->
@BizModel("MyEntity")
public class MyEntityBizModel extends CrudBizModel<MyEntity> {
    // ✅ 直接使用基类方法，自动检查唯一性
}
```

### Q5: 多租户和逻辑删除如何工作？

**答案**：

框架自动处理，无需手动干预：
- **多租户**：查询时自动追加租户ID过滤条件
- **逻辑删除**：查询时自动过滤已删除记录，删除时自动标记
- **恢复**：使用 `recoverDeleted()` 方法恢复已删除记录

### Q6: 何时需要直接使用 dao() 方法？

**答案**：

仅在以下特殊场景：
- 需要执行原生SQL或复杂聚合查询
- 需要批量优化，确定不需要验证和权限检查

**注意**：直接使用 `dao()` 时需要手动处理多租户、逻辑删除、数据权限等。

### Q7: 如何处理虚拟字段？

**答案**：

在 XMeta 中定义虚拟字段，在回调中获取：

```xml
<!-- XMeta -->
<prop name="virtualProp" published="false" virtual="true">
    <schema type="String"/>
</prop>
```

```java
// 业务代码
@Override
protected void defaultPrepareSave(EntityData<T> entityData, IServiceContext context) {
    super.defaultPrepareSave(entityData, context);

    // 获取虚拟字段
    String virtualProp = (String) entityData.getData().get("virtualProp");
    // 处理虚拟字段...
}
```

### Q8: 如何在事务提交后执行操作？

**答案**：

使用基类提供的 `txn().afterCommit()` 方法：

```java
@BizMutation
public MyEntity mySave(@Name("data") Map<String, Object> data, IServiceContext context) {
    MyEntity entity = save(data, context);

    // 事务提交后执行（如发送通知、更新缓存等）
    txn().afterCommit(null, () -> {
        sendNotification(entity);
    });

    return entity;
}
```

### Q9: 审计字段（createTime, updateTime, createBy, updateBy）需要手动设置吗？

**答案**：

**不需要**，框架会自动设置这些字段：

| 字段 | 触发时机 | 自动设置值 |
|------|----------|-----------|
| `createTime` | save | 当前时间戳 |
| `createBy` | save | 当前用户ID |
| `updateTime` | save / update | 当前时间戳 |
| `updateBy` | save / update | 当前用户ID |


**注意**：
- ✅ 不要手动设置 `createTime`, `updateTime`, `createBy`, `updateBy`



## 最佳实践总结

### ✅ DO（推荐做法）

1. **优先使用内置方法**
   - 使用 `findPage()`, `save()`, `update()`, `delete()` 等方法
   - 利用回调函数扩展逻辑
   - 在 XMeta 中配置 keys、filter、orderBy

2. **利用内置功能**
   - 多租户过滤：自动处理
   - 逻辑删除：自动过滤和恢复
   - 数据权限：自动检查
   - 数据验证：自动验证
   - 唯一性检查：基于 XMeta keys 自动检查

3. **通过回调扩展**
   - 重写 `defaultPrepareSave()`, `defaultPrepareUpdate()` 等
   - 在 `doSave()`, `doUpdate()` 中传入自定义回调

4. **合理配置 XMeta**
   - 在 XMeta 中定义唯一键
   - 在 XMeta 中定义过滤器和排序
   - 在 XMeta 中定义验证规则

### ❌ DON'T（避免做法）

1. **不要直接调用 dao() 方法**
   - 避免使用 `dao().findPageByQuery()`, `dao().saveEntity()` 等
   - 这会跳过内置的验证、权限、唯一性检查

2. **不要手动实现唯一性检查**
   - 框架已基于 XMeta keys 自动检查
   - 手动实现会导致重复逻辑

3. **不要手动添加多租户过滤**
   - 框架自动追加租户条件
   - 手动添加可能冲突或遗漏

4. **不要手动处理逻辑删除**
   - 框架自动过滤已删除记录
   - 手动处理容易出错

5. **不要手动设置审计字段**
   - `createTime`, `updateTime`, `createBy`, `updateBy` 由框架自动设置
   - 手动设置可能导致数据不一致

6. **不要在 BizMutation 方法中手动开启事务**
   - `@BizMutation` 已自动开启事务
   - 手动使用 `@Transactional` 或 `txn()` 会导致嵌套事务

## 相关文档

- [服务层开发指南](../service/service-layer-development.md) - BizModel开发详解
- [IEntityDao使用指南](../dao/entitydao-usage.md) - 数据访问接口详解
- [GraphQL服务开发指南](../api/graphql-guide.md) - GraphQL API开发
- [数据库模型设计](../dao/database-model-design.md) - 数据库模型设计指南

## 总结

CrudBizModel 是 Nop 平台 CRUD 开发的核心组件。通过继承 CrudBizModel 并合理使用 XMeta 配置，开发者可以快速构建高效、可靠的 CRUD 应用。

**核心原则**：
1. 优先使用基类内置方法
2. 通过回调扩展自定义逻辑
3. 在 XMeta 中配置验证规则和唯一键
4. 避免重复实现框架已有功能

遵循这些原则，可以充分利用框架能力，减少重复代码，提高开发效率。
