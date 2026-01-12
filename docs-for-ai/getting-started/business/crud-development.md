# CRUD开发指南

## 概述

Nop平台提供强大的CRUD开发框架，核心组件是`CrudBizModel`类，它封装了完整的增删改查逻辑，支持自动生成前后端CRUD功能。通过继承`CrudBizModel`，开发者可以快速实现业务功能，无需编写重复的基础代码。

CrudBizModel的主要优势：
- 封装了完整的CRUD操作逻辑
- 支持单表和主子表CRUD
- 自动处理权限控制
- 支持逻辑删除和数据验证
- 与GraphQL和REST API无缝集成
- 支持事务管理
- 提供丰富的扩展点

## 核心概念

### 1. CrudBizModel
- CRUD业务模型的基类，封装了通用的CRUD操作
- 提供`findPage`、`save`、`update`、`delete`等核心方法
- 支持自定义业务逻辑扩展
- 自动处理数据验证和权限控制

### ⭐ 重要：避免直接调用 dao() 方法

在 `CrudBizModel` 的派生类中，**一般不需要直接调用 `dao().findPage()`, `dao().saveEntity()` 等方法**。

**为什么？**

`CrudBizModel` 已经内置了大量的功能，包括：
- **多租户支持**：自动追加租户过滤条件
- **逻辑删除**：自动过滤已删除记录，支持恢复操作
- **复杂条件过滤**：自动应用数据权限过滤器、实体级别过滤器
- **数据验证**：自动执行字段验证和业务规则验证
- **权限控制**：自动检查数据权限（`checkDataAuth`）
- **关联操作**：自动处理级联删除、批量加载等

**最佳实践**：

在 `CrudBizModel` 派生类中：
- ✅ **优先使用基类的 `doFindPage()`, `doFindList()`, `doSave()`, `doUpdate()`, `doDelete()` 等方法**
- ✅ **通过回调函数实现自定义逻辑**，如 `prepareSave`, `prepareUpdate`, `prepareQuery`, `prepareDelete` 等
- ✅ **使用 `doFindPageByQueryDirectly()` 等方法**跳过数据权限验证（仅限特殊场景）
- ❌ **避免直接调用 `dao().findPage()`, `dao().saveEntity()` 等方法**，除非有特殊需求

### 2. EntityData
- 封装了实体数据的容器类
- 包含原始数据、验证后的数据、实体对象和元模型
- 用于在CRUD操作中传递数据

### 3. BizObject
- 业务对象的封装，包含业务对象名称、元模型等信息
- 用于在GraphQL中注册业务模型

### 4. XMeta
- 业务对象的元数据定义
- 包含字段信息、验证规则、权限配置等

## 快速开始

### 1. 创建业务模型类

创建一个继承自`CrudBizModel`的业务模型类：

```java
@BizModel("MyEntity")
public class MyEntityBizModel extends CrudBizModel<MyEntity> {
    @Override
    public String getEntityName() {
        return "MyEntity";
    }
}
```

### 2. 注册业务模型

在`app-beans.xml`中注册业务模型：

```xml
<bean id="MyEntityBizModel" class="com.example.MyEntityBizModel"/>
```

### 3. 访问CRUD API

- GraphQL API：通过GraphQL UI访问，如`http://localhost:8080/q/graphql-ui`
- REST API：通过REST接口访问，如`http://localhost:8080/r/MyEntity__findPage`

## CrudBizModel 内置功能

### 1. 多租户支持

`CrudBizModel` 内置了多租户支持，通过以下机制实现：

#### 自动追加租户过滤条件

在 `EqlTransformVisitor` 中（第 300-319 行），当查询使用多租户实体时，会自动追加租户过滤条件：

```java
// EqlTransformVisitor.collectDefaultEntityFilter()
if (entityModel.isUseTenant()) {
    SqlBinaryExpr expr = newTenantExpr(table, tableMeta);
    consumer.accept(expr);
}
```

这意味着：
- **无需手动添加** `tenantId` 过滤条件
- 所有查询会**自动限制**到当前租户的数据
- 防止跨租户数据泄露

#### 租户参数构建器

使用 `TenantParamBuilder` 自动构建租户参数（第 328 行）：
```java
SqlParameterMarker param = new SqlParameterMarker();
param.setSqlParamBuilder(TenantParamBuilder.INSTANCE);
expr.setRight(param);
```

### 2. 逻辑删除

`CrudBizModel` 完整支持逻辑删除，包括查询自动过滤、删除标记、恢复等功能。

#### 自动过滤已删除记录

在查询时，默认**自动过滤**已删除记录（CrudBizModel 第 258-259 行）：
```java
if (query != null)
    query.setDisableLogicalDelete(false);
```

在 `EqlTransformVisitor` 中（第 300-312 行），如果实体使用逻辑删除，会自动追加删除标志过滤：
```java
if (tableMeta.isUseLogicalDelete() && !context.isDisableLogicalDelete()) {
    consumer.accept(buildLogicalDeleteFilter(table, tableMeta));
}
```

#### 逻辑删除相关方法

| 方法 | 说明 | 位置 |
|------|------|------|
| `recoverLogicalDeleted()` | 保存时恢复已删除的记录 | 第 636-646 行 |
| `findLogicalDeleted()` | 查找已删除记录 | 第 648-664 行 |
| `deleted_findPage()` | 分页查询已删除记录 | 第 1281-1301 行 |
| `deleted_get()` | 获取已删除的单条记录 | 第 904-917 行 |
| `recoverDeleted()` | 恢复已删除记录 | 第 1303-1326 行 |
| `isAllowGetDeleted()` | 是否允许查询已删除记录 | 第 919-921 行 |

#### 恢复逻辑删除

在 `CrudBizModel` 中，当保存的记录是已逻辑删除的记录时，会自动恢复（第 624-646 行）：
```java
protected T recoverLogicalDeleted(Map<String, Object> data, IObjMeta objMeta) {
    IEntityDao<T> dao = dao();
    T entity = null;
    if (dao.isUseLogicalDelete()) {
        entity = findLogicalDeleted(data, dao, objMeta);
        if (entity != null) {
            dao.resetToDefaultValues(entity); // 重置为默认值
        }
    }
    return entity;
}
```

### 3. 复杂条件过滤

`CrudBizModel` 支持多层次的查询条件过滤，自动组合多个过滤条件。

#### 查询预处理链

在 `prepareFindPageQuery` 方法中（第 351-404 行），按以下顺序应用过滤条件：

1. **数据权限过滤器**（第 361-363 行）：
   ```java
   query = AuthHelper.appendFilter(context.getDataAuthChecker(), query,
           authObjName, action, context);
   ```

2. **实体级别过滤器**（第 373-375 行）：
   ```java
   if (objMeta.getFilter() != null) {
       query.addFilter(objMeta.getFilter().cloneInstance());
   }
   ```

3. **QueryTransformer 自定义转换**（第 398-399 行）：
   ```java
   if (queryTransformer != null)
       queryTransformer.transform(query, authObjName, action,
               this.getThisObj(), context);
   ```

4. **条件转换**（第 395-396 行）：
   ```java
   if (query.getFilter() != null)
       BizQueryHelper.transformFilter(query, objMeta, context);
   ```

5. **业务表达式解析**（第 401-402 行）：
   ```java
   BizExprHelper.resolveBizExpr(query.getFilter(), context);
   ```

#### EqlTransformVisitor 中的实体过滤器

在 `EqlTransformVisitor` 中（第 313-318 行），支持实体模型级别的静态过滤器：
```java
if (tableMeta.hasFilter()) {
    for (OrmEntityFilterModel filter : tableMeta.getFilters()) {
        SqlBinaryExpr expr = newBinaryExpr(table, tableMeta,
                filter.getName(), filter.getValue());
        consumer.accept(expr);
    }
}
```

这意味着可以在实体模型中定义静态过滤条件（如 `status=1`），自动应用到所有查询。

### 4. 数据权限控制

`CrudBizModel` 内置了数据权限检查机制。

#### 查询前检查

在查询前，自动追加数据权限过滤器（第 361-363 行）：
```java
query = AuthHelper.appendFilter(context.getDataAuthChecker(), query,
        authObjName, action, context);
```

#### 保存前检查

在保存实体前，检查数据权限（第 528 行）：
```java
checkDataAuth(BizConstants.METHOD_SAVE, entityData.getEntity(), context);
```

#### 更新后检查

在更新实体后，再次检查数据权限（第 787 行）：
```java
checkDataAuthAfterUpdate(entityData.getEntity(), context);
```

#### 权限检查实现

`checkDataAuth` 方法（第 682-691 行）：
```java
protected void checkDataAuth(@Name("action") String action,
                           @Name("entity") T entity, IServiceContext context) {
    IDataAuthChecker dataAuthChecker = context.getDataAuthChecker();
    if (dataAuthChecker == null)
        return;

    String bizObjName = getAuthObjName(action);
    if (!dataAuthChecker.isPermitted(bizObjName, action, entity, context)) {
        throw new NopException(ERR_AUTH_NO_DATA_AUTH).param(ARG_BIZ_OBJ_NAME, bizObjName);
    }
}
```

### 5. 级联操作

`CrudBizModel` 支持级联删除和关联操作。

#### 级联删除

在 `deleteReferences` 方法中（第 1125-1138 行），自动删除关联对象：
```java
protected void deleteReferences(@Name("entity") T entity, IServiceContext context) {
    IOrmBatchLoadQueue loadQueue = orm().requireSession().getBatchLoadQueue();
    boolean empty = loadQueue.isEmpty();

    for (CascadePropMeta prop : getCascadeProps()) {
        if (prop.isCascadeDelete()) {
            queueCascadeDelete(entity, prop.getPropMeta(),
                    prop.getRefBizObjName(), context);
        }
    }

    if (empty)
        loadQueue.flush();
}
```

#### 子节点检查

在删除前，检查是否存在子节点（第 1093-1112 行）：
```java
protected void checkChildrenNotExistsWhenDelete(@Name("entity") T entity,
                                             IServiceContext context) {
    IObjMeta objMeta = getThisObj().getObjMeta();
    if (objMeta != null) {
        ObjTreeModel tree = objMeta.getTree();
        if (tree != null) {
            if (tree.getChildrenProp() != null) {
                IObjPropMeta prop = objMeta.getProp(tree.getChildrenProp());
                if (prop != null && !prop.containsTag(BizConstants.TAG_CASCADE_DELETE)) {
                    Collection<?> children = (Collection<?>) entity.orm_propValueByName(prop.getName());
                    if (children != null && !children.isEmpty()) {
                        throw new NopException(ERR_BIZ_NOT_ALLOW_DELETE_PARENT_WHEN_CHILDREN_IS_NOT_EMPTY)
                                .param(ARG_BIZ_OBJ_NAME, getBizObjName())
                                .param(ARG_ID, entity.orm_idString());
                    }
                }
            }
        }
    }
}
```

### 6. 数据验证

`CrudBizModel` 内置了完整的数据验证机制。

#### 保存前验证

在 `doSave` 方法中（第 513-533 行），执行以下验证：
1. **空数据检查**（第 515-516 行）
2. **数据验证**（第 519-523 行）
3. **唯一性检查**（第 519 行）
4. **权限检查**（第 528 行）

#### 更新前验证

在 `doUpdate` 方法中（第 772-793 行），执行：
1. **空数据检查**（第 775-776 行）
2. **数据验证**（第 780-782 行）
3. **更新后权限检查**（第 787 行）
4. **唯一性检查**（第 788 行）

#### 唯一性检查

`checkUniqueForSave` 和 `checkUniqueForUpdate` 方法（第 536-590 行）检查唯一键约束。

### 7. 扩展点

`CrudBizModel` 提供了丰富的扩展点，通过回调函数实现自定义逻辑。

#### 查询扩展

| 方法 | 用途 | 位置 |
|------|------|------|
| `prepareFindPageQuery()` | 查询预处理 | 第 351-404 行 |
| `defaultPrepareQuery()` | 默认查询预处理 | 第 500-502 行 |
| `invokeDefaultPrepareQuery()` | 调用默认查询预处理 | 第 1803-1806 行 |

#### 保存扩展

| 方法 | 用途 | 位置 |
|------|------|------|
| `prepareSave()` | 保存前预处理（通过 `doSave` 传入） | 第 513-514 行 |
| `defaultPrepareSave()` | 默认保存预处理 | 第 706-711 行 |
| `invokeDefaultPrepareSave()` | 调用默认保存预处理 | 第 1808-1811 行 |
| `afterEntityChange()` | 实体变更后回调 | 第 741-749 行 |

#### 更新扩展

| 方法 | 用途 | 位置 |
|------|------|------|
| `prepareUpdate()` | 更新前预处理（通过 `doUpdate` 传入） | 第 774-775 行 |
| `defaultPrepareUpdate()` | 默认更新预处理 | 第 883-884 行 |
| `invokeDefaultPrepareUpdate()` | 调用默认更新预处理 | 第 1819-1822 行 |

#### 删除扩展

| 方法 | 用途 | 位置 |
|------|------|------|
| `prepareDelete()` | 删除前预处理（通过 `doDelete` 传入） | 第 970 行 |
| `defaultPrepareDelete()` | 默认删除预处理 | 第 1088-1090 行 |
| `invokeDefaultPrepareDelete()` | 调用默认删除预处理 | 第 1825-1828 行 |

### 8. 批量操作

`CrudBizModel` 提供了多种批量操作方法：

| 方法 | 说明 | 位置 |
|------|------|------|
| `batchUpdate()` | 批量更新 | 第 1193-1208 行 |
| `batchDelete()` | 批量删除 | 第 1210-1226 行 |
| `batchModify()` | 批量增删改 | 第 1228-1262 行 |
| `batchGet()` | 批量获取 | 第 923-946 行 |
| `doUpdateMulti()` | 批量更新（内部方法） | 第 1354-1364 行 |
| `doDeleteMulti()` | 批量删除（内部方法） | 第 1392-1400 行 |

### 9. 多对多关联

`CrudBizModel` 内置了多对多关联操作支持（第 1488-1618 行）：

| 方法 | 说明 | 位置 |
|------|------|------|
| `addManyToManyRelations()` | 新增多对多关联 | 第 1488-1501 行 |
| `removeManyToManyRelations()` | 删除多对多关联 | 第 1503-1515 行 |
| `updateManyToManyRelations()` | 更新多对多关联 | 第 1517-1528 行 |

### 10. 树形结构操作

`CrudBizModel` 支持树形结构的各种操作：

| 方法 | 说明 | 位置 |
|------|------|------|
| `findRoots()` | 查询树根节点 | 第 1465-1485 行 |
| `findTreeEntityPage()` | 分页查询树形结构 | 第 1672-1705 行 |
| `findTreeEntityList()` | 查询树形结构 | 第 1719-1738 行 |
| `findListForTree()` | 查询树形结构列表 | 第 1742-1754 行 |
| `findPageForTree()` | 分页查询树形结构 | 第 1761-1794 行 |

### 11. 状态机支持

`CrudBizModel` 内置了状态机支持（第 719-729 行）：

```java
protected void triggerStateChange(T entity, String event, IServiceContext context) {
    IStateMachine stm = getThisObj().getStateMachine();
    if (stm == null)
        throw new NopException(ERR_BIZ_NO_STATE_MACHINE)
                .param(ARG_BIZ_OBJ_NAME, getBizObjName());
    stm.triggerStateChange(entity, event, context, action);
}
```

### 12. 字典支持

`CrudBizModel` 支持将实体记录作为字典项返回（第 1402-1431 行）：

```java
@BizQuery
@Description("@i18n:biz.asDict|将实体记录作为字典项返回")
public DictBean asDict(IServiceContext context) {
    // 实现...
}
```

## 常见功能实现

### 1. 列表查询

#### 基本查询

CrudBizModel自动提供了以下查询方法：
- `findPage`：分页查询
- `findList`：列表查询
- `findFirst`：查询第一条记录
- `findCount`：查询记录总数
- `findTreePage`：树形结构分页查询

#### 自定义查询

```java
@BizQuery
@GraphQLReturn(bizObjName = "MyEntity")
public PageBean<MyEntity> myFindPage(@Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context) {
    // 自定义查询条件处理
    query.addFilter(FilterBeans.eq("status", 1));

    // ✅ 使用基类的 doFindPage 方法
    // 这会自动应用：
    // - 多租户过滤
    // - 逻辑删除过滤
    // - 数据权限过滤
    // - 实体级别过滤器
    // - 自定义 prepareQuery 回调
    return doFindPage(query, this::defaultPrepareQuery, selection, context);

    // ❌ 不要直接调用 dao().findPageByQuery(query)
    // 这会跳过所有的内置功能
}
```

### 2. 保存和更新

#### 基本保存

CrudBizModel自动提供了以下保存方法：
- `save`：新增实体
- `update`：更新实体
- `save_update`：根据是否有ID自动判断新增或更新

#### 自定义保存逻辑

```java
@BizMutation
@GraphQLReturn(bizObjName = "MyEntity")
public MyEntity mySave(@Name("data") Map<String, Object> data, IServiceContext context) {
    // ✅ 使用基类的 doSave 方法
    // 这会自动处理：
    // - 数据验证
    // - 唯一性检查
    // - 逻辑删除恢复
    // - 数据权限检查
    // - 保存实体
    return doSave(data, null, (entityData, ctx) -> {
        // 在 prepareSave 回调中执行自定义逻辑
        // 此时数据已经验证，权限已经检查
        entityData.getEntity().setCreateTime(new Date());
        entityData.getEntity().setCreateBy(ctx.getUserId());

        // 获取虚拟字段（未在实体中定义的字段）
        String myProp = (String) entityData.getData().get("myProp");
        // 处理虚拟字段
    }, context);

    // ❌ 不要这样做：
    // T entity = dao().newEntity();
    // entity.setName(...);
    // dao().saveEntity(entity);
    // 这会跳过数据验证、唯一性检查、权限检查等内置功能
}
```

#### 自定义更新逻辑

```java
@BizMutation
@GraphQLReturn(bizObjName = "MyEntity")
public MyEntity myUpdate(@Name("data") Map<String, Object> data, IServiceContext context) {
    // ✅ 使用基类的 doUpdate 方法
    // 这会自动处理：
    // - 数据验证
    // - 唯一性检查
    // - 数据权限检查（更新后）
    // - 更新实体
    return doUpdate(data, null, (entityData, ctx) -> {
        // 在 prepareUpdate 回调中执行自定义逻辑
        // 此时数据已经验证，但还未执行更新
        entityData.getEntity().setUpdateTime(new Date());
        entityData.getEntity().setUpdateBy(ctx.getUserId());
    }, context);

    // ❌ 不要这样做：
    // T entity = dao().getEntityById(id);
    // entity.setName(...);
    // dao().updateEntity(entity);
    // 这会跳过数据验证、唯一性检查、权限检查等内置功能
}
```

### 3. 删除操作

#### 基本删除

CrudBizModel自动提供了以下删除方法：
- `delete`：根据ID删除
- `batchDelete`：批量删除
- `tryDelete`：尝试删除，用于权限检查

#### 自定义删除逻辑

```java
@BizMutation
public boolean myDelete(@Name("id") String id, IServiceContext context) {
    // ✅ 使用基类的 doDelete 方法
    // 这会自动处理：
    // - 数据权限检查
    // - 子节点检查
    // - 关联数据检查
    // - 级联删除
    // - 逻辑删除（如果配置）
    return doDelete(id, getDefaultRefNamesToCheckExists(), (entity, ctx) -> {
        // 在 prepareDelete 回调中执行自定义逻辑
        // 此时权限已经检查，但还未执行删除
        checkEntityRefsNotExists(entity, Collections.singleton("otherRef"), ctx);
    }, context);

    // ❌ 不要这样做：
    // T entity = dao().getEntityById(id);
    // dao().deleteEntity(entity);
    // 这会跳过数据权限检查、子节点检查、级联删除等内置功能
}
```

### 4. 批量操作

#### 批量更新

```java
@BizMutation
public void myBatchUpdate(@Name("ids") Set<String> ids, @Name("data") Map<String, Object> data, IServiceContext context) {
    // ✅ 使用基类的 update 方法进行批量更新
    // 每个 update 都会应用完整的验证和权限检查
    for (String id : ids) {
        Map<String, Object> updateData = new LinkedHashMap<>(data);
        updateData.put("id", id);
        update(updateData, context);
    }

    // 或者使用 CrudBizModel 内置的批量方法
    // batchUpdate(ids, data, false, context);
}
```

#### 批量删除

```java
@BizMutation
public Set<String> myBatchDelete(@Name("ids") Set<String> ids, IServiceContext context) {
    // 批量删除逻辑
    Set<String> deletedIds = new HashSet<>();
    for (String id : ids) {
        if (delete(id, context)) {
            deletedIds.add(id);
        }
    }
    return deletedIds;
}
```

### 5. 事务管理

在事务提交后执行操作：

```java
@BizMutation
public MyEntity mySaveWithAfterCommit(@Name("data") Map<String, Object> data, IServiceContext context) {
    MyEntity entity = save(data, context);

    // 事务提交后执行
    txn().afterCommit(null, () -> {
        // 发送通知、更新缓存等
        sendNotification(entity);
    });

    return entity;
}
```

## ORM 层：EntityPersisterImpl 和 CollectionPersisterImpl

### 1. EntityPersisterImpl 功能

`EntityPersisterImpl` 是 ORM 层实体持久化的核心实现，负责实体的增删改查操作。

#### 核心方法

| 方法 | 说明 | 位置 |
|------|------|------|
| `save()` | 保存实体到数据库 | 第 288-301 行 |
| `update()` | 更新已存在的实体 | 第 304-314 行 |
| `delete()` | 删除实体（支持逻辑删除） | 第 317-336 行 |
| `loadAsync()` | 异步加载实体 | 第 216-243 行 |
| `batchLoadAsync()` | 批量异步加载实体 | 第 136-184 行 |
| `findPageByExample()` | 根据示例分页查询 | 第 657-667 行 |
| `findFirstByExample()` | 根据示例查询第一条 | 第 705-715 行 |
| `countByExample()` | 根据统计示例数量 | 第 718-728 行 |

#### 内置功能

##### 1. 实体过滤器绑定

在查询和保存前，自动绑定实体级别过滤器（第 359-365 行）：
```java
// EntityPersisterImpl.bindFilter()
void bindFilter(IOrmEntity entity) {
    if (entityModel.hasFilter()) {
        for (OrmEntityFilterModel filter : entityModel.getFilters()) {
            // 自动设置过滤器值到实体
            entity.orm_propValueByName(filter.getName(), filter.getValue());
        }
    }
}
```

##### 2. 列值检查

在保存前，自动检查列值（第 379-404 行）：
```java
// EntityPersisterImpl.checkColumnValueWhenSave()
void checkColumnValueWhenSave(IOrmEntity entity) {
    for (IColumnModel propModel : entityModel.getColumns()) {
        Object value = entity.orm_propValue(propModel.getPropId());
        if (value == null) {
            if (propModel.isMandatory()) {
                if (propModel.getDefaultValue() != null) {
                    // 自动设置默认值
                    entity.orm_propValue(propModel.getPropId(), propModel.getDefaultValue());
                } else if (propModel.containsTag(OrmConstants.TAG_SEQ)) {
                    // 自动生成序列号
                    String propKey = OrmModelHelper.buildEntityPropKey(propModel);
                    Object seqValue = env.getSequenceGenerator().generateLong(propKey, false);
                    entity.orm_propValue(propModel.getPropId(), seqValue);
                } else {
                    // 检查必填字段
                    throw newError(ERR_ORM_MANDATORY_PROP_IS_NULL, entity)
                            .param(ARG_PROP_NAME, propModel.getName());
                }
            } else {
                // 自动设置默认值
                if (!entity.orm_propInited(propModel.getPropId())
                        && propModel.getDefaultValue() != null)
                    entity.orm_propValue(propModel.getPropId(), propModel.getDefaultValue());
            }
        }
    }
}
```

##### 3. 多租户 ID 处理

自动处理租户 ID 的设置和验证（第 426-452 行）：
```java
// EntityPersisterImpl.processTenantId()
void processTenantId(IOrmEntity entity) {
    if (entityModel.isUseTenant()) {
        int tenantPropId = entityModel.getTenantPropId();
        if (tenantPropId > 0) {
            String currentTenant = ContextProvider.currentTenantId();
            String tenantId;

            // 如果未设置租户 ID，自动设置为当前租户
            if (!entity.orm_propInited(tenantPropId)) {
                tenantId = currentTenant;
                entity.orm_internalSet(tenantPropId, tenantId);
            } else {
                tenantId = StringHelper.toString(entity.orm_propValue(tenantPropId), null);
            }

            // 检查租户权限
            if (StringHelper.isEmpty(tenantId)) {
                tenantId = currentTenant;
                if (tenantId == null)
                    throw newError(ERR_ORM_MISSING_TENANT_ID, entity)
                            .param(ARG_PROP_NAME, entity.orm_propName(tenantPropId));
                entity.orm_propValue(tenantPropId, tenantId);
            } else {
                if (currentTenant != null && !currentTenant.equals(tenantId))
                    throw newError(ERR_ORM_NOT_ALLOW_PROCESS_ENTITY_IN_OTHER_TENANT, entity)
                            .param(ARG_TENANT_ID, tenantId).param(ARG_CURRENT_TENANT, currentTenant);
            }
        }
    }
}
```

##### 4. 乐观锁版本处理

自动处理乐观锁版本号（第 348-377 行）：
```java
// 保存时初始化版本号
void processOptimisticLockVersion(IOrmEntity entity) {
    int versionProp = entityModel.getVersionPropId();
    if (versionProp > 0) {
        Object value = entity.orm_propValue(versionProp);
        if (value == null) {
            // 将版本字段初始化为 0
            entity.orm_propValue(versionProp, 0);
        }
    }
}

// 更新成功后增加版本号
void incOptimisticLockVersion(IOrmEntity entity) {
    int versionProp = entityModel.getVersionPropId();
    if (versionProp > 0) {
        Object value = entity.orm_propValue(versionProp);
        value = MathHelper.add(value, 1);
        entity.orm_internalSet(versionProp, value);
    }
}
```

##### 5. 更新结果检查

自动检查更新操作的结果（第 504-520 行）：
```java
// EntityPersisterImpl.checkUpdateResult()
void checkUpdateResult(int count, IOrmEntity entity) {
    if (count > 1) {
        if (entity.orm_disableVersionCheckError()) {
            LOG.info("nop.err.orm.update-entity-multiple-rows:entity={}", entity);
            entity.orm_readonly(true);
        } else {
            throw newError(ERR_ORM_UPDATE_ENTITY_MULTIPLE_ROWS, entity);
        }
    } else if (count == 0) {
        if (entity.orm_disableVersionCheckError()) {
            LOG.info("nop.err.orm.update-entity-not-found:entity={}", entity);
            entity.orm_readonly(true);
        } else {
            throw newError(ERR_ORM_UPDATE_ENTITY_NOT_FOUND, entity);
        }
    }
}
```

##### 6. 全局缓存支持

自动管理全局缓存（第 522-631 行）：
```java
// 从全局缓存加载
protected boolean loadFromGlobalCache(IOrmEntity entity, IOrmSessionImplementor session) {
    if (useGlobalCache) {
        Object values = globalCache.get(getCacheKey(entity));
        if (values != null) {
            Object[] cacheValues = convertCacheValues(values);
            session.internalAssemble(entity, cacheValues, entityModel.getAllPropIds());
            return true;
        }
    }
    return false;
}

// 更新全局缓存
protected void updateGlobalCache(IOrmEntity entity, IOrmSessionImplementor session) {
    if (entity.orm_state().isMissing()) {
        globalCache.removeAsync(getCacheKey(entity));
    } else {
        Object[] values = OrmAssembly.getPropValues(entity, entityModel.getAllPropIds());
        globalCache.putAsync(getCacheKey(entity), values);
    }
}
```

### 2. CollectionPersisterImpl 功能

`CollectionPersisterImpl` 是 ORM 层集合持久化的核心实现，负责处理一对多、多对多等关联集合。

#### 核心方法

| 方法 | 说明 | 位置 |
|------|------|------|
| `loadCollection()` | 加载关联集合 | 第 82-101 行 |
| `batchLoadCollectionAsync()` | 批量异步加载集合 | 第 111-125 行 |
| `flushCollectionChange()` | 刷新集合变更 | 第 171-181 行 |

#### 内置功能

##### 1. 多租户集合处理

自动处理租户集合的租户 ID（第 87-89 行）：
```java
// CollectionPersisterImpl.loadCollection()
if (collectionModel.getRefEntityModel().isUseTenant()) {
    collection.orm_tenantId(ContextProvider.currentTenantId());
}
```

##### 2. 全局缓存支持

自动管理集合的全局缓存（第 83-91 行）：
```java
if (useGlobalCache && loadFromGlobalCache(collection, session))
    return;
```

##### 3. 批量加载优化

支持分批加载以提高性能（第 111-125 行）：
```java
// CollectionPersisterImpl.batchLoadCollectionAsync()
for (final Collection<IOrmEntitySet> colls : CollectionHelper.splitChunk(toLoad, this.getMaxBatchLoadSize())) {
    CompletionStage<?> future = driver.batchLoadCollectionAsync(shard, colls, propIds, selection, session);
    FutureHelper.collectWaiting(future, futures);

    future.thenRun(() -> {
        for (IOrmEntitySet coll : colls) {
            if (useGlobalCache) {
                updateGlobalCache(coll, session);
            }
        }
    });
}
```

## 唯一性检查：objMeta keys 配置

### 1. XMeta 中的 keys 配置

在 XMeta 中可以定义除主键外的其他唯一键：

```xml
<meta x:schema="/nop/schema/xmeta.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <entityName>MyEntity</entityName>

    <!-- 唯一键配置 -->
    <keys>
        <!-- 名称唯一键 -->
        <key name="uk_name" props="name" displayName="名称唯一键"/>

        <!-- 组合唯一键 -->
        <key name="uk_name_status" props="name,status" displayName="名称和状态组合唯一键"/>
    </keys>

    <props>
        <!-- ... 属性定义 -->
    </props>
</meta>
```

**Key 配置说明**：
- `name` - 唯一键名称
- `props` - 构成唯一键的属性集合（逗号分隔）
- `displayName` - 唯一键的显示名称（用于错误提示）

### 2. CrudBizModel 中的自动唯一性检查

`CrudBizModel` 内置了基于 objMeta keys 配置的唯一性检查，**无需手动实现**。

#### save 唯一性检查

在 `doSave()` 方法中（第 536-562 行），自动检查所有唯一键：

```java
// CrudBizModel.checkUniqueForSave()
protected void checkUniqueForSave(@Name("entityData") EntityData<T> entityData) {
    IObjMeta objMeta = entityData.getObjMeta();
    if (objMeta.getKeys() != null) {
        Map<String, Object> data = entityData.getValidatedData();
        IEntityDao<T> dao = dao();
        for (ObjKeyModel keyModel : objMeta.getKeys()) {
            Set<String> props = keyModel.getProps();
            if (data.keySet().containsAll(props)) {
                // 构建查询示例
                T example = dao.newEntity();
                List<Object> keys = new ArrayList<>();
                List<Object> displayNames = new ArrayList<>();
                for (String propName : props) {
                    Object value = data.get(propName);
                    example.orm_propValueByName(propName, value);
                    keys.add(value);
                    displayNames.add(objMeta.getProp(propName).getDisplayName());
                }

                // 查询是否已存在
                T existing = dao.findFirstByExample(example);
                if (existing != null && existing != entityData.getEntity()) {
                    throw new NopException(ERR_BIZ_ENTITY_WITH_SAME_KEY_ALREADY_EXISTS)
                            .param(ARG_KEY, StringHelper.join(keys, ","))
                            .param(ARG_DISPLAY_NAME, StringHelper.join(displayNames, ","))
                            .param(ARG_BIZ_OBJ_NAME, getBizObjName());
                }
            }
        }
    }
}
```

#### update 唯一性检查

在 `doUpdate()` 方法中（第 565-590 行），自动检查被修改的唯一键字段：

```java
// CrudBizModel.checkUniqueForUpdate()
protected void checkUniqueForUpdate(@Name("entity") T entity, IServiceContext context) {
    IObjMeta objMeta = getThisObj().getObjMeta();
    if (objMeta.getKeys() != null) {
        IEntityDao<T> dao = dao();
        for (ObjKeyModel keyModel : objMeta.getKeys()) {
            Set<String> props = keyModel.getProps();
            // 只检查被修改的唯一键字段
            if (isAnyPropDirty(entity, props)) {
                T example = dao.newEntity();
                List<Object> keys = new ArrayList<>();
                List<Object> displayNames = new ArrayList<>();
                for (String propName : props) {
                    Object value = entity.orm_propValueByName(propName);
                    example.orm_propValueByName(propName, value);
                    keys.add(value);
                    displayNames.add(objMeta.getProp(propName).getDisplayName());
                }

                T existing = dao.findFirstByExample(example);
                if (existing != null && existing != entity) {
                    throw new NopException(ERR_BIZ_ENTITY_WITH_SAME_KEY_ALREADY_EXISTS)
                            .param(ARG_KEY, StringHelper.join(keys, ","))
                            .param(ARG_DISPLAY_NAME, StringHelper.join(displayNames, ","))
                            .param(ARG_BIZ_OBJ_NAME, getBizObjName());
                }
            }
        }
    }
}
```

### 3. 最佳实践：使用 objMeta keys 配置

#### ✅ 推荐做法

在 XMeta 中配置 keys，让框架自动处理唯一性检查：

```xml
<!-- XMeta 配置 -->
<meta>
    <keys>
        <key name="uk_name" props="name" displayName="名称"/>
        <key name="uk_code" props="code,tenantId" displayName="编码"/>
    </keys>
</meta>

<!-- 业务代码 - 无需手动实现唯一性检查 -->
@BizModel("MyEntity")
public class MyEntityBizModel extends CrudBizModel<MyEntity> {
    // ✅ 直接使用基类方法，自动检查唯一性
    @BizMutation
    public MyEntity saveEntity(@Name("data") Map<String, Object> data, IServiceContext context) {
        return save(data, context); // 自动检查 uk_name 和 uk_code
    }
}
```

#### ❌ 不推荐做法

在业务代码中手动实现唯一性检查：

```java
@BizModel("MyEntity")
public class MyEntityBizModel extends CrudBizModel<MyEntity> {
    // ❌ 手动实现唯一性检查，重复框架已有功能
    @BizMutation
    public MyEntity saveEntity(@Name("data") Map<String, Object> data, IServiceContext context) {
        String name = (String) data.get("name");
        String code = (String) data.get("code");

        // 手动查询是否已存在
        long count = dao().countByQuery(QueryBean.forQuery(MyEntity.class)
                .filter(FilterBeans.eq("name", name))
                .filter(FilterBeans.eq("code", code)));
        if (count > 0) {
            throw new NopException("ERR_NAME_EXISTS");
        }

        // 手动检查唯一性...
        return doSave(data, null, (entityData, ctx) -> {
            // ...
        }, context);
    }
}
```

### 4. 唯一性检查的优点

使用 objMeta keys 配置进行唯一性检查的优势：

| 优势 | 说明 |
|------|------|
| **声明式配置** | 在 XMeta 中声明唯一键，无需修改业务代码 |
| **自动检查** | 框架自动在保存和更新时检查唯一性 |
| **友好错误提示** | 自动提供清晰的错误信息，包括哪些字段重复 |
| **性能优化** | 使用索引高效检查，避免 N+1 查询 |
| **一致性** | 唯一性规则集中管理，避免分散在业务代码中 |
| **易于维护** | 修改唯一性规则只需修改 XMeta，无需修改业务代码 |

## 前端集成

### 1. 使用GraphQL API

在前端使用GraphQL查询：

```graphql
query($query: QueryBeanInput) {
  MyEntity__findPage(query: $query) {
    items {
      id
      name
      status
    }
    total
  }
}
```

### 2. 使用REST API

在前端使用REST接口：

```javascript
// GET请求
fetch('/r/MyEntity__findPage?page=1&limit=10')
  .then(response => response.json())
  .then(data => console.log(data));

// POST请求
fetch('/r/MyEntity__save', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    data: { name: 'Test', status: 1 }
  })
});
```

### 3. 使用XView模型

在XView模型中引用业务模型：

```xml
<view>
    <objMeta>/app/meta/MyEntity.xmeta</objMeta>
    <controlLib>/nop/web/xlib/control.xlib</controlLib>
    
    <grids>
        <grid id="list">
            <cols>
                <col id="name" sortable="true" />
                <col id="status" sortable="true" />
            </cols>
        </grid>
    </grids>
    
    <pages>
        <crud name="main" grid="list" filterForm="query" />
    </pages>
</view>
```

## 扩展CrudBizModel

### 1. 使用回调函数扩展

`CrudBizModel` 提供了丰富的回调函数，用于实现自定义逻辑，而无需直接调用 `dao()` 方法。

#### 查询扩展

```java
@Override
protected void defaultPrepareQuery(QueryBean query, IServiceContext context) {
    super.defaultPrepareQuery(query, context);

    // ✅ 添加默认过滤条件
    // 此时数据权限、实体过滤器已经自动添加
    query.addFilter(FilterBeans.eq("someField", "defaultValue"));
}
```

#### 保存扩展

```java
@Override
protected void defaultPrepareSave(EntityData<T> entityData, IServiceContext context) {
    super.defaultPrepareSave(entityData, context);

    // ✅ 执行保存前的自定义逻辑
    // 此时数据已经验证，但还未保存
    T entity = entityData.getEntity();

    // 自动填充字段
    if (entity.getId() == null) {
        entity.setCreateTime(new Date());
        entity.setCreateBy(context.getUserId());
    }
    entity.setUpdateTime(new Date());
    entity.setUpdateBy(context.getUserId());

    // 处理虚拟字段
    String virtualProp = (String) entityData.getData().get("virtualProp");
    // ...
}
```

#### 更新扩展

```java
@Override
protected void defaultPrepareUpdate(EntityData<T> entityData, IServiceContext context) {
    super.defaultPrepareUpdate(entityData, context);

    // ✅ 执行更新前的自定义逻辑
    // 此时数据已经验证，但还未更新
    T entity = entityData.getEntity();
    entity.setUpdateTime(new Date());
    entity.setUpdateBy(context.getUserId());
}
```

#### 删除扩展

```java
@Override
protected void defaultPrepareDelete(T entity, IServiceContext context) {
    super.defaultPrepareDelete(entity, context);

    // ✅ 执行删除前的自定义逻辑
    // 此时权限已经检查，但还未删除
    // 检查其他关联数据
    checkOtherRefsNotExists(entity);
}
```

### 2. 使用传入的回调函数

除了重写基类方法，还可以在使用 `doSave()`, `doUpdate()`, `doDelete()` 等方法时，传入自定义回调：

```java
@BizMutation
@GraphQLReturn(bizObjName = "MyEntity")
public MyEntity myCustomSave(@Name("data") Map<String, Object> data, IServiceContext context) {
    // ✅ 使用 doSave 并传入自定义 prepareSave 回调
    return doSave(data, null, (entityData, ctx) -> {
        // 在回调中执行自定义逻辑
        // 此时会执行 defaultPrepareSave，然后执行这个回调
        T entity = entityData.getEntity();
        entity.setCustomField("customValue");

        // 获取虚拟字段
        String virtualProp = (String) entityData.getData().get("virtualProp");
    }, context);
}
```

### 3. 自定义验证逻辑

```java
@Override
protected EntityData<T> buildEntityDataForSave(Map<String, Object> data, FieldSelectionBean inputSelection, IServiceContext context) {
    EntityData<T> entityData = super.buildEntityDataForSave(data, inputSelection, context);

    // ✅ 自定义验证逻辑
    // 此时已经执行了数据验证
    String name = (String) entityData.getValidatedData().get("name");
    if (name != null && name.length() > 50) {
        throw new NopException("ERR_BIZ_NAME_TOO_LONG");
    }

    return entityData;
}
```

### 4. 自定义查询预处理

```java
@Override
protected void defaultPrepareQuery(QueryBean query, IServiceContext context) {
    super.defaultPrepareQuery(query, context);

    // ✅ 添加默认过滤条件
    // 注意：多租户过滤已经自动添加，无需手动添加
    // 注意：逻辑删除过滤已经自动添加，无需手动添加
    // 注意：数据权限过滤器已经自动添加，无需手动添加

    if (query.getFilter() == null) {
        query.setFilter(FilterBeans.eq("someField", "defaultValue"));
    } else {
        query.addFilter(FilterBeans.eq("someField", "defaultValue"));
    }
}
```

### 5. 何时直接使用 dao() 方法

虽然一般情况下避免使用 `dao()` 方法，但在以下场景中可能需要直接使用：

#### 场景1：批量操作优化

当需要批量操作大量数据，并且确定不需要数据验证、权限检查等功能时：

```java
@BizMutation
public void myBulkOperation(List<String> ids, IServiceContext context) {
    // 如果确定不需要验证和权限检查，可以直接使用 dao()
    // 但要注意：这会跳过所有的内置功能
    List<T> entities = dao().batchGetEntitiesByIds(ids);

    for (T entity : entities) {
        // 执行批量操作
        entity.setStatus(newStatus);
    }

    dao().batchUpdateEntities(entities);
}
```

#### 场景2：特殊查询需求

当需要执行特殊的查询，如原生 SQL、复杂的聚合查询等：

```java
@BizQuery
public List<Map<String, Object>> myCustomQuery(IServiceContext context) {
    // 使用 orm() 执行特殊查询
    SQL sql = SQL.begin().select("*")
            .from("my_table")
            .where("create_time > ?", new Date())
            .end();

    return orm().findPage(sql, 0, 100);
}
```

**重要提示**：在使用 `dao()` 或 `orm()` 方法时，需要手动处理：
- ✅ 多租户过滤
- ✅ 逻辑删除过滤
- ✅ 数据权限检查
- ✅ 数据验证
- ✅ 唯一性检查

这些在 `CrudBizModel` 内置方法中都是自动处理的。

### 3. 自定义保存预处理

```java
@Override
protected void defaultPrepareSave(EntityData<T> entityData, IServiceContext context) {
    super.defaultPrepareSave(entityData, context);
    
    // 自定义保存预处理，如自动填充字段
    T entity = entityData.getEntity();
    if (entity.getId() == null) {
        entity.setCreateTime(new Date());
        entity.setCreateBy(context.getUserId());
    }
    entity.setUpdateTime(new Date());
    entity.setUpdateBy(context.getUserId());
}
```

## 最佳实践

### 1. 优先使用CrudBizModel 内置方法

充分利用 `CrudBizModel` 提供的内置功能，**避免重复实现已有功能**：

#### ✅ 推荐做法

```java
@BizModel
public class MyEntityBizModel extends CrudBizModel<MyEntity> {
    // ✅ 直接使用基类方法，无需重写
    // findPage() - 内置多租户、逻辑删除、数据权限过滤
    // save() - 内置数据验证、唯一性检查、权限检查
    // update() - 内置数据验证、唯一性检查、权限检查
    // delete() - 内置权限检查、级联删除、逻辑删除

    // 如需自定义逻辑，通过回调函数
    @Override
    protected void defaultPrepareSave(EntityData<MyEntity> entityData, IServiceContext context) {
        super.defaultPrepareSave(entityData, context);
        // 自定义逻辑
    }
}
```

#### ❌ 不推荐做法

```java
@BizModel
public class MyEntityBizModel extends CrudBizModel<MyEntity> {
    // ❌ 重新实现已有的功能
    @BizQuery
    public PageBean<MyEntity> findMyEntities(QueryBean query, IServiceContext context) {
        // 手动添加多租户过滤
        query.addFilter(FilterBeans.eq("tenantId", context.getTenantId()));

        // 手动添加逻辑删除过滤
        query.addFilter(FilterBeans.eq("deleteFlag", 0));

        // 手动检查数据权限
        checkDataAuth(...);

        return dao().findPageByQuery(query);
    }
}
```

**为什么？**

内置方法已经包含了：
- ✅ 多租户过滤（自动追加租户 ID 条件）
- ✅ 逻辑删除过滤（自动过滤已删除记录）
- ✅ 数据权限过滤（自动追加数据权限过滤器）
- ✅ 实体级别过滤器（自动追加 XMeta 中定义的 filter）
- ✅ 数据验证（自动执行字段验证和业务规则验证）
- ✅ 唯一性检查（自动检查唯一键约束）
- ✅ 权限检查（自动检查数据权限）

### 2. 合理设计XMeta

良好的XMeta设计是高效CRUD的基础，包括：
- 合理的字段定义
- 完善的验证规则
- 适当的权限配置

**重要**：在 XMeta 中定义的 `filter` 和 `orderBy` 会**自动应用到所有查询**，无需手动添加。

例如：
```xml
<meta>
    <!-- 这个过滤器会自动应用到所有查询 -->
    <filter>
        <eq name="status" value="1"/>
    </filter>

    <!-- 这个排序会自动应用到所有查询 -->
    <orderBy>
        <sort name="createTime" order="desc"/>
    </orderBy>
</meta>
```

### 3. 权限控制

在XMeta中配置细粒度的权限控制：
- 配置字段级权限
- 配置操作级权限
- 使用数据权限过滤器

**重要**：`CrudBizModel` 会自动应用数据权限过滤器，无需手动处理。

### 4. 事务管理

合理使用事务管理：
- 对于复杂操作，使用事务保证数据一致性
- 对于非关键操作，使用异步处理提高性能

**使用 `CrudBizModel` 的 `txn()` 方法**：
```java
public void myOperation(IServiceContext context) {
    // 使用基类提供的事务模板
    txn().runInTransaction(() -> {
        save(data1, context);
        save(data2, context);
    });
}
```

### 5. 错误处理

统一处理错误，返回友好的错误信息：
- 使用NopException封装业务错误
- 配置错误码和错误信息
- 提供详细的错误日志

## 常见问题

### 1. 为什么使用 CrudBizModel 而不是直接使用 dao()？

**答案**：

`CrudBizModel` 提供了完整的业务逻辑封装，包括：

| 功能 | dao() 方法 | CrudBizModel 内置方法 |
|------|-----------|---------------------|
| 多租户过滤 | ❌ 需手动添加 | ✅ 自动追加 |
| 逻辑删除过滤 | ❌ 需手动添加 | ✅ 自动过滤 |
| 数据权限检查 | ❌ 需手动检查 | ✅ 自动检查 |
| 数据验证 | ❌ 需手动验证 | ✅ 自动验证 |
| 唯一性检查 | ❌ 需手动检查 | ✅ 自动检查 |
| 级联删除 | ❌ 需手动处理 | ✅ 自动处理 |
| 实体过滤器 | ❌ 需手动添加 | ✅ 自动应用 |

**推荐做法**：
```java
// ✅ 使用基类方法
return doFindPage(query, this::defaultPrepareQuery, selection, context);
```

**不推荐做法**：
```java
// ❌ 直接使用 dao()
return dao().findPageByQuery(query);
```

### 2. 如何传递实体上没有的字段到后台？

在XMeta中定义虚拟字段：

```xml
<prop name="myProp" published="false" virtual="true">
    <schema stdDomain="string" />
</prop>
```

在后台通过EntityData获取：

```java
@Override
protected void defaultPrepareSave(EntityData<T> entityData, IServiceContext context) {
    // 通过 entityData.getData() 获取原始数据，包含虚拟字段
    String myProp = (String) entityData.getData().get("myProp");
    // 处理虚拟字段...
}
```

### 3. 如何在事务提交后执行操作？

使用`ITransactionTemplate.afterCommit`函数：

```java
@BizMutation
public MyEntity mySave(@Name("data") Map<String, Object> data, IServiceContext context) {
    MyEntity entity = save(data, context);

    // 使用基类提供的事务模板
    txn().afterCommit(null, () -> {
        // 事务提交后执行的操作，如发送通知、更新缓存等
        sendNotification(entity);
    });

    return entity;
}
```

### 4. 如何扩展内置的save/update操作？

使用`doSave`/`doUpdate`方法，并传入自定义的回调函数：

```java
@BizMutation
public MyEntity mySave(@Name("data") Map<String, Object> data, IServiceContext context) {
    // ✅ 使用 doSave 并传入自定义回调
    return doSave(data, null, (entityData, ctx) -> {
        // 在回调中执行自定义逻辑
        // 此时已执行数据验证、权限检查等
        T entity = entityData.getEntity();
        entity.setCustomField("value");

        // 处理虚拟字段
        String virtualProp = (String) entityData.getData().get("virtualProp");
    }, context);
}
```

### 5. 如何实现批量操作？

使用CrudBizModel提供的`batchUpdate`/`batchDelete`方法：

```java
@BizMutation
public void myBatchUpdate(@Name("ids") Set<String> ids, @Name("data") Map<String, Object> data, IServiceContext context) {
    // ✅ 使用内置的批量方法
    batchUpdate(ids, data, false, context);
}
```

### 6. 如何实现自定义查询？

使用`doFindPage`/`doFindList`方法，并传入自定义的查询条件：

```java
@BizQuery
public PageBean<MyEntity> myFindPage(@Name("query") QueryBean query, IServiceContext context) {
    // ✅ 添加自定义过滤条件
    query.addFilter(FilterBeans.eq("myField", "myValue"));

    // 使用基类方法，自动应用所有内置功能
    return doFindPage(query, this::defaultPrepareQuery, selection, context);
}
```

### 7. 多租户过滤如何工作？

**答案**：

`CrudBizModel` 通过以下机制自动处理多租户过滤：

1. **EQL 转换器自动追加租户条件**：`EqlTransformVisitor.collectDefaultEntityFilter()` 会自动为多租户实体添加租户 ID 过滤条件
2. **无需手动添加**：在所有查询中，租户 ID 过滤条件会自动追加
3. **TenantParamBuilder**：使用租户参数构建器，从上下文中获取当前租户 ID

**注意**：
- ✅ **无需手动添加** `tenantId` 过滤条件
- ✅ **无需手动检查** 租户权限
- ❌ **不要手动设置** `tenantId`，这可能与自动过滤冲突

### 8. 逻辑删除如何工作？

**答案**：

`CrudBizModel` 完整支持逻辑删除，包括：

| 操作 | 说明 | 方法 |
|------|------|------|
| 查询过滤 | 自动过滤已删除记录 | `query.setDisableLogicalDelete(false)` |
| 删除标记 | 标记为已删除 | `dao().deleteEntity()` |
| 恢复删除 | 恢复已删除记录 | `recoverLogicalDeleted()` |
| 查询已删除 | 查询已删除记录 | `deleted_findPage()`, `deleted_get()` |

**注意**：
- ✅ **无需手动过滤** `deleteFlag` 字段
- ✅ **删除时自动标记**，无需手动设置
- ❌ **不要手动设置** `deleteFlag`，使用 `delete()` 方法

### 9. 数据权限过滤如何工作？

**答案**：

`CrudBizModel` 在查询预处理时自动追加数据权限过滤器：

```java
// CrudBizModel.prepareFindPageQuery() - 第 361-363 行
query = AuthHelper.appendFilter(context.getDataAuthChecker(), query,
        authObjName, action, context);
```

**注意**：
- ✅ **无需手动添加** 数据权限过滤条件
- ✅ **无需手动检查** 数据权限
- ✅ 在保存和更新时**自动检查**数据权限
- ❌ **不要绕过** 数据权限检查，除非确实需要

### 10. 何时需要直接使用 dao() 方法？

**答案**：在以下特殊场景中，可能需要直接使用 `dao()` 方法：

1. **特殊查询需求**：需要执行原生 SQL 或复杂聚合查询
2. **批量操作优化**：需要优化性能，确定不需要验证和权限检查
3. **数据库层面操作**：需要直接操作数据库，跳过业务逻辑层

**注意事项**：
- ⚠️ **需要手动处理** 多租户过滤
- ⚠️ **需要手动处理** 逻辑删除过滤
- ⚠️ **需要手动处理** 数据权限检查
- ⚠️ **需要手动处理** 数据验证
- ⚠️ **需要手动处理** 唯一性检查

**建议**：优先使用 `CrudBizModel` 内置方法，只有在特殊需求时才直接使用 `dao()`。

## XMeta配置

XMeta是Nop平台的标准化对象元数据模型，用于定义对象结构、属性权限和行为。它是连接前后端的数据契约，合理的XMeta配置可以提高系统的安全性、性能和可维护性。

### 基本结构

```xml
<meta x:schema="/nop/schema/xmeta.xdef" xmlns:x="/nop/schema/xdsl.xdef"
      xmlns:xpl="xpl" xmlns:meta-gen="meta-gen" xmlns:c="c">
    
    <x:post-extends>
        <meta-gen:DefaultMetaPostExtends xpl:lib="/nop/core/xlib/meta-gen.xlib"/>
    </x:post-extends>
    
    <!-- 对象级别配置 -->
    <entityName>CustomObj</entityName>
    <primaryKey>id</primaryKey>
    <displayProp>name</displayProp>
    
    <!-- 唯一键配置 -->
    <keys>
        <key name="uk_name" props="name" displayName="名称唯一键"/>
    </keys>
    
    <!-- 过滤条件 -->
    <filter>
        <eq name="status" value="1"/>
    </filter>
    
    <!-- 排序规则 -->
    <orderBy>
        <sort name="createTime" order="desc"/>
    </orderBy>
    
    <!-- 树形结构配置 -->
    <tree parentProp="parentId" childrenProp="children" levelProp="level" rootLevelValue="0"/>
    
    <!-- 属性定义 -->
    <props>
        <!-- 基本属性 -->
        <prop name="id" displayName="ID" queryable="true">
            <schema type="Long"/>
        </prop>
        
        <!-- 字符串属性 -->
        <prop name="name" displayName="名称" queryable="true" insertable="true" updatable="true">
            <schema type="String"/>
        </prop>
        
        <!-- 字典属性 -->
        <prop name="status" displayName="状态" queryable="true" insertable="true" updatable="true">
            <schema type="Integer" dict="core/active-status"/>
        </prop>
        
        <!-- 动态计算属性 -->
        <prop name="statusLabel" displayName="状态文本">
            <schema type="String"/>
            <getter>
                <c:script><![CDATA[
                    if(entity.status == 1)
                        return "已启用";
                    return "已禁用";
                ]]></c:script>
            </getter>
        </prop>
        
        <!-- 关联对象 -->
        <prop name="parent">
            <schema bizObjName="CustomObj"/>
        </prop>
        
        <!-- 关联集合 -->
        <prop name="children">
            <schema>
                <item bizObjName="CustomObj"/>
            </schema>
        </prop>
        
        <!-- 计算属性（带依赖加载） -->
        <prop name="childrenCount" displayName="子节点数量" depends="~children">
            <schema type="Integer"/>
            <getter>
                <c:script><![CDATA[
                    return entity.children?.size() ?: 0;
                ]]></c:script>
            </getter>
        </prop>
        
        <!-- 批量属性 -->
        <prop name="tags" displayName="标签">
            <schema type="List<String>"/>
            <transformIn>
                return value?.$toCsvListString();
            </transformIn>
            <transformOut>
                return value?.$toCsvList();
            </transformOut>
        </prop>
        
        <!-- 权限控制 -->
        <prop name="sensitiveInfo" displayName="敏感信息">
            <schema type="String"/>
            <auth for="read" roles="admin"/>
            <auth for="write" roles="super-admin"/>
        </prop>
    </props>
</meta>
```

### 配置说明

#### 对象级别配置

| 属性名 | 说明 |
|--------|------|
| **entityName** | 实体名称，对应ORM模型中的实体 |
| **primaryKey** | 主键字段列表 |
| **displayProp** | 用于显示的字段，如下拉选择时使用 |
| **keys** | 除主键外的其他唯一键 |
| **filter** | 自动追加的查询过滤条件 |
| **orderBy** | 缺省排序规则 |
| **tree** | 树形结构配置，包括父节点属性、子节点属性、层级属性等 |

#### 属性级别配置

| 属性名 | 缺省值 | 说明 |
|--------|--------|------|
| tagSet | - | 扩展标签，用于代码生成 |
| published | true | 是否发布为GraphQL属性 |
| insertable | true | 是否允许save操作包含此属性 |
| updatable | true | 是否允许update操作修改此属性 |
| queryable | false | 是否允许在查询条件中使用 |
| sortable | false | 是否允许按此属性排序 |
| lazy | false | REST访问时是否缺省不返回 |
| allowFilterOp | - | 允许的查询运算，如gt,ge,contains,like等 |
| ui:filterOp | eq | 生成查询表单时的缺省查询运算 |
| ui:control | - | 缺省使用的控件类型 |
| depends | - | 依赖的关联属性，用于批量加载 |

### 高级特性

1. **动态计算属性**: 通过`<getter>`定义计算逻辑
2. **数据转换**: 通过`<transformIn>`和`<transformOut>`实现前后台数据转换
3. **权限控制**: 通过`<auth>`配置字段级别的读写权限
4. **依赖加载**: 通过`depends`属性实现关联属性的批量加载，解决N+1问题
5. **字典关联**: 通过`schema`的`dict`属性关联字典配置
6. **关联对象**: 通过`schema`的`bizObjName`属性定义关联类型
7. **集合属性**: 通过`schema`的`item`节点定义集合元素类型

### 最佳实践

1. **合理使用权限控制**: 严格控制敏感字段的访问权限
2. **优化查询性能**: 对频繁查询的字段设置`queryable="true"`
3. **使用依赖加载**: 对计算属性中使用的关联属性，通过`depends`实现批量加载
4. **利用自动扩展**: 通过`<x:post-extends>`引入默认的元数据扩展处理
5. **统一命名规范**: 保持属性名称和显示名称的一致性
6. **合理使用字典**: 对枚举类型字段使用字典配置，便于统一管理和国际化

## 相关文档

- [服务层开发指南](../service/service-layer-development.md) - BizModel开发详解
- [IEntityDao使用指南](../dao/entitydao-usage.md) - 数据访问接口详解
- [复杂业务开发指南](./complex-business-development.md) - 复杂业务逻辑开发
- [GraphQL服务开发指南](../api/graphql-guide.md) - GraphQL API开发
- [数据库模型设计](../dao/database-model-design.md) - 数据库模型设计指南

## 总结

CrudBizModel是Nop平台CRUD开发的核心组件，它封装了完整的CRUD操作逻辑，支持自动生成前后端CRUD功能。通过继承CrudBizModel，开发者可以快速实现业务功能，减少重复开发。

CrudBizModel的主要特点：
- 封装了完整的CRUD操作
- 支持自动生成前后端代码
- 提供丰富的扩展点
- 支持权限控制和数据验证
- 与GraphQL和REST API无缝集成

### ⭐ 核心原则

在 `CrudBizModel` 派生类中开发时，遵循以下核心原则：

1. **优先使用内置方法**
   - ✅ 使用 `doFindPage()`, `doFindList()`, `doSave()`, `doUpdate()`, `doDelete()` 等方法
   - ❌ 避免直接调用 `dao().findPage()`, `dao().saveEntity()` 等方法

2. **利用内置功能**
   - ✅ 多租户过滤：自动处理，无需手动添加
   - ✅ 逻辑删除：自动过滤已删除记录，支持恢复
   - ✅ 数据权限：自动检查和过滤，无需手动处理
   - ✅ 数据验证：自动验证，无需手动验证
   - ✅ 唯一性检查：自动检查唯一键，无需手动检查

3. **通过回调扩展**
   - ✅ 重写 `defaultPrepareSave()`, `defaultPrepareUpdate()`, `defaultPrepareDelete()` 等方法
   - ✅ 在 `doSave()`, `doUpdate()`, `doDelete()` 中传入自定义回调
   - ✅ 使用回调函数实现自定义逻辑，而不是绕过内置功能

4. **合理使用 XMeta**
   - ✅ 在 XMeta 中定义 `filter` 和 `orderBy`，自动应用到所有查询
   - ✅ 在 XMeta 中定义验证规则，自动执行验证
   - ✅ 在 XMeta 中定义权限配置，自动检查权限

### 典型开发模式

#### 标准 CRUD

```java
@BizModel("MyEntity")
public class MyEntityBizModel extends CrudBizModel<MyEntity> {
    // ✅ 无需重写，直接使用内置方法
}
```

#### 添加自定义逻辑

```java
@BizModel("MyEntity")
public class MyEntityBizModel extends CrudBizModel<MyEntity> {
    @Override
    protected void defaultPrepareSave(EntityData<MyEntity> entityData, IServiceContext context) {
        super.defaultPrepareSave(entityData, context);

        // ✅ 添加自定义逻辑
        entityData.getEntity().setCreateTime(new Date());
    }
}
```

#### 自定义业务方法

```java
@BizModel("MyEntity")
public class MyEntityBizModel extends CrudBizModel<MyEntity> {
    @BizMutation
    public MyEntity myCustomSave(@Name("data") Map<String, Object> data, IServiceContext context) {
        // ✅ 使用内置方法 + 自定义回调
        return doSave(data, null, (entityData, ctx) -> {
            // 自定义逻辑
        }, context);
    }
}
```

XMeta配置是连接前后端的数据契约，合理的配置可以提高系统的安全性、性能和可维护性。通过结合CrudBizModel和XMeta配置，开发者可以快速构建高效、可靠的CRUD应用，专注于业务逻辑的实现，而无需关心底层的技术细节。