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
    
    return doFindPage(query, this::defaultPrepareQuery, selection, context);
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
    return doSave(data, null, (entityData, ctx) -> {
        // 保存前的自定义逻辑
        entityData.getEntity().setCreateTime(new Date());
        entityData.getEntity().setCreateBy(ctx.getUserId());
        
        // 获取虚拟字段
        String myProp = (String) entityData.getData().get("myProp");
        // 处理虚拟字段
    }, context);
}
```

#### 自定义更新逻辑

```java
@BizMutation
@GraphQLReturn(bizObjName = "MyEntity")
public MyEntity myUpdate(@Name("data") Map<String, Object> data, IServiceContext context) {
    return doUpdate(data, null, (entityData, ctx) -> {
        // 更新前的自定义逻辑
        entityData.getEntity().setUpdateTime(new Date());
        entityData.getEntity().setUpdateBy(ctx.getUserId());
    }, context);
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
    return doDelete(id, getDefaultRefNamesToCheckExists(), (entity, ctx) -> {
        // 删除前的自定义逻辑，如检查关联数据
        checkEntityRefsNotExists(entity, Collections.singleton("children"), ctx);
    }, context);
}
```

### 4. 批量操作

#### 批量更新

```java
@BizMutation
public void myBatchUpdate(@Name("ids") Set<String> ids, @Name("data") Map<String, Object> data, IServiceContext context) {
    // 批量更新逻辑
    for (String id : ids) {
        Map<String, Object> updateData = new LinkedHashMap<>(data);
        updateData.put("id", id);
        update(updateData, context);
    }
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

### 1. 自定义验证逻辑

```java
@Override
protected EntityData<T> buildEntityDataForSave(Map<String, Object> data, FieldSelectionBean inputSelection, IServiceContext context) {
    EntityData<T> entityData = super.buildEntityDataForSave(data, inputSelection, context);
    
    // 自定义验证逻辑
    String name = (String) entityData.getValidatedData().get("name");
    if (name != null && name.length() > 50) {
        throw new NopException("ERR_BIZ_NAME_TOO_LONG");
    }
    
    return entityData;
}
```

### 2. 自定义查询预处理

```java
@Override
protected void defaultPrepareQuery(QueryBean query, IServiceContext context) {
    // 自定义查询预处理，如添加默认过滤条件
    if (query.getFilter() == null) {
        query.setFilter(FilterBeans.eq("tenantId", context.getTenantId()));
    } else {
        query.addFilter(FilterBeans.eq("tenantId", context.getTenantId()));
    }
}
```

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

### 1. 优先使用CrudBizModel

充分利用CrudBizModel提供的功能，减少重复开发。对于复杂业务逻辑，可以通过扩展CrudBizModel来实现。

### 2. 合理设计XMeta

良好的XMeta设计是高效CRUD的基础，包括：
- 合理的字段定义
- 完善的验证规则
- 适当的权限配置

### 3. 权限控制

在XMeta中配置细粒度的权限控制：
- 配置字段级权限
- 配置操作级权限
- 使用数据权限过滤器

### 4. 事务管理

合理使用事务管理：
- 对于复杂操作，使用事务保证数据一致性
- 对于非关键操作，使用异步处理提高性能

### 5. 错误处理

统一处理错误，返回友好的错误信息：
- 使用NopException封装业务错误
- 配置错误码和错误信息
- 提供详细的错误日志

## 常见问题

### 1. 如何传递实体上没有的字段到后台？

在XMeta中定义虚拟字段：

```xml
<prop name="myProp" published="false" virtual="true">
    <schema stdDomain="string" />
</prop>
```

在后台通过EntityData获取：

```java
String myProp = (String) entityData.getData().get("myProp");
```

### 2. 如何在事务提交后执行操作？

使用`ITransactionTemplate.afterCommit`函数：

```java
txn().afterCommit(null, () -> {
    // 事务提交后执行的操作
});
```

### 3. 如何扩展内置的save/update操作？

使用`doSave`/`doUpdate`方法，并传入自定义的回调函数：

```java
return doSave(data, null, (entityData, ctx) -> {
    // 自定义处理逻辑
}, context);
```

### 4. 如何实现批量操作？

使用CrudBizModel提供的`batchUpdate`/`batchDelete`方法，或自定义实现：

```java
@BizMutation
public void myBatchUpdate(@Name("ids") Set<String> ids, @Name("data") Map<String, Object> data, IServiceContext context) {
    // 批量更新逻辑
}
```

### 5. 如何实现自定义查询？

使用`doFindPage`/`doFindList`方法，并传入自定义的查询条件：

```java
return doFindPage(query, this::defaultPrepareQuery, selection, context);
```

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

## 总结

CrudBizModel是Nop平台CRUD开发的核心组件，它封装了完整的CRUD操作逻辑，支持自动生成前后端CRUD功能。通过继承CrudBizModel，开发者可以快速实现业务功能，减少重复开发。

CrudBizModel的主要特点：
- 封装了完整的CRUD操作
- 支持自动生成前后端代码
- 提供丰富的扩展点
- 支持权限控制和数据验证
- 与GraphQL和REST API无缝集成

XMeta配置是连接前后端的数据契约，合理的配置可以提高系统的安全性、性能和可维护性。通过结合CrudBizModel和XMeta配置，开发者可以快速构建高效、可靠的CRUD应用，专注于业务逻辑的实现，而无需关心底层的技术细节。