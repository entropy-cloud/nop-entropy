# nop-service-architect Skill

## Skill 概述

**名称**: nop-service-architect（服务架构师）

**定位**: 基于数据库模型和领域模型，设计服务层和BizModel实现，封装业务逻辑

**输入**:
1. Excel ORM模型（`{module}.orm.xlsx`）
2. 领域模型草案（`domain-model-draft.xml`）
3. 服务需求描述（如果有）

**输出**:
1. `{module}.xbiz.xml`（BizModel服务定义）
2. `{module}-api.xml`（API定义，可选）
3. BizModel Java代码（可选）
4. 服务设计文档（`service-design-{module}.md`）

**能力**:
- 继承CrudBizModel快速实现CRUD
- 设计业务方法和扩展点
- 定义事务管理策略
- 设计数据权限控制
- 集成领域模型

**依赖**:
- Nop平台服务层开发指南（docs-for-ai/getting-started/service/service-layer-development.md）
- Nop平台BizModel文档
- Nop平台事务管理文档（docs-for-ai/getting-started/core/transaction-guide.md）

## 核心原则

### 1. BizModel作为领域逻辑容器
- **职责封装**：BizModel封装一个业务领域的所有操作
- **协议中性**：BizModel使用POJO，不依赖特定协议
- **可测试性**：BizModel方法可以直接单元测试

### 2. CRUD优先使用内置方法
- **内置方法**：`findPage()`、`save()`、`update()`、`delete()`、`findList()`等
- **避免重复实现**：不要在BizModel中重新实现CRUD逻辑
- **扩展机制**：通过重写`defaultPrepare*`等回调方法添加自定义逻辑

### 3. View DDD原则
- **实体只包含数据属性**：不在实体上添加业务方法
- **领域逻辑通过get方法暴露**：如`order.getItems()`
- **易变逻辑放在XMeta**：通过`getter`、`domain`、`computed`等属性

### 4. 注解驱动服务设计
- **@BizModel**：标记领域模型类
- **@BizQuery**：标记查询方法
- **@BizMutation**：标记变更方法
- **@BizAction**：标记动作方法

### 5. 事务管理
- **@Transactional**：自动开启事务
- **回调扩展**：`afterSave()`、`afterUpdate()`等

### 6. 数据权限控制
- **字段级权限**：在XMeta中定义数据权限
- **权限检查**：使用`checkDataAuth()`方法
- **数据脱敏**：通过`visibleOn`、`mask`属性控制字段可见性

## 工作流程

### 阶段1：需求分析

**步骤1.1：理解服务需求**
```
分析服务需求描述，理解：
- 需要哪些查询操作
- 需要哪些变更操作
- 需要哪些业务规则
- 需要哪些数据权限
```

**步骤1.2：识别扩展点**
```
识别需要定制化的功能点：
- 需要特殊处理的保存逻辑
- 需要特殊处理的更新逻辑
- 需要特殊处理的删除逻辑
- 需要特殊处理的查询逻辑
```

**步骤1.3：生成服务方法列表**
```
生成服务方法列表：
- 查询方法（@BizQuery）
- 变更方法（@BizMutation）
- 动作方法（@BizAction）
```

### 阶段2：BizModel设计

**步骤2.1：继承CrudBizModel**
```java
@BizModel("{module}")
public class {module}ServiceModel extends CrudBizModel<{module}Entity> {
    @Override
    public String getEntityName() {
        return "{module}";
    }
}
```

**步骤2.2：定义数据属性**
```java
@Data
private {module}Entity entity;
```

**步骤2.3：设计业务方法**

根据需求设计服务方法，使用注解标记方法类型：

**查询方法示例**：
```java
@BizQuery
public PageBean<{module}Entity> find{module}s(
        @Name("query") QueryBean query,
        @Name("pageNo") Integer pageNo,
        @Name("pageSize") Integer pageSize) {
    if (pageNo == null || pageNo <= 0) {
        pageNo = 1;
    }
    if (pageSize == null || pageSize <= 0) {
        pageSize = 20;
    }

    query.setOffset((pageNo - 1) * pageSize);
    query.setLimit(pageSize);

    return dao().findPageByQuery(query);
}

@BizQuery
public {module}Entity find{module}ById(@Name("id") String id) {
    return dao().requireEntityById(id);
}

@BizQuery
public List<{module}Entity> find{module}sByCondition(
        @Name("condition") Map<String, Object> condition) {
    {module}Entity example = new {module}Entity();
    for (Map.Entry<String, Object> entry : condition.entrySet()) {
        String key = entry.getKey();
        Object value = entry.getValue();
        if (key.equals("name")) {
            example.setName((String) value);
        } else if (key.equals("status")) {
            example.setStatus((Integer) value);
        }
    }
    return dao().findAllByExample(example);
}
```

**变更方法示例**：
```java
@BizMutation
@Transactional
public {module}Entity create{module}(@Name("entity") {module}Entity entity) {
    return save(Collections.singletonMap("entity", entity));
}

@BizMutation
@Transactional
public {module}Entity update{module}(@Name("entity") {module}Entity entity) {
    return update(Collections.singletonMap("entity", entity));
}

@BizMutation
@Transactional
public void delete{module}(@Name("id") String id) {
    delete(Collections.singletonMap("id", id));
}
```

**动作方法示例**：
```java
@BizAction
@Transactional
public void approve{module}(@Name("id") String id) {
    {module}Entity entity = dao().requireEntityById(id);
    entity.setStatus(1);
    dao().saveEntity(entity);
}

@BizAction
@Transactional
public void reject{module}(@Name("id") String id, @Name("reason") String reason) {
    {module}Entity entity = dao().requireEntityById(id);
    entity.setStatus(-1);
    entity.setRejectReason(reason);
    dao().saveEntity(entity);
}
```

### 阶段3：扩展点实现

**步骤3.1：实现defaultPrepareSave**
```java
@Override
protected void defaultPrepareSave(EntityData<{module}Entity> entityData,
                                  IServiceContext context) {
    {module}Entity entity = entityData.getEntity();
    if (entity.isNew()) {
        // 新增时的处理逻辑
        if (StringHelper.isEmpty(entity.getId())) {
            entity.setId(IDGenerator.generateId());
        }
        entity.setCreateTime(LocalDateTime.now());
        entity.setStatus(1);
    }

    // 通用处理逻辑
    entity.setUpdateTime(LocalDateTime.now());
}
```

**步骤3.2：实现defaultPrepareUpdate**
```java
@Override
protected void defaultPrepareUpdate(EntityData<{module}Entity> entityData,
                                   IServiceContext context) {
    {module}Entity entity = entityData.getEntity();
    entity.setUpdateTime(LocalDateTime.now());
}
```

**步骤3.3：实现defaultPrepareDelete**
```java
@Override
protected void defaultPrepareDelete(EntityData<{module}Entity> entityData,
                                   IServiceContext context) {
    {module}Entity entity = entityData.getEntity();
    // 删除前的处理逻辑
}
```

**步骤3.4：实现defaultPrepareQuery**
```java
@Override
protected void defaultPrepareQuery(QueryBean query, IServiceContext context) {
    // 添加默认的查询过滤条件
    // 例如：只查询未删除的记录
}
```

**步骤3.5：实现afterSave**
```java
@Override
protected void afterSave(EntityData<{module}Entity> entityData,
                        IServiceContext context) {
    {module}Entity entity = entityData.getEntity();
    // 保存后的处理逻辑
    // 例如：发送通知、更新缓存等
}
```

### 阶段4：数据权限控制

**步骤4.1：实现checkDataAuth**
```java
@Override
protected void checkDataAuth(EntityData<{module}Entity> entityData,
                           IServiceContext context) {
    {module}Entity entity = entityData.getEntity();
    String userId = context.getUserId();
    String tenantId = context.getTenantId();

    // 检查数据权限
    if (!hasPermission(userId, entity, tenantId)) {
        throw new NopException(ERR_FORBIDDEN);
    }
}

private boolean hasPermission(String userId, {module}Entity entity, String tenantId) {
    // 实现数据权限检查逻辑
    // 例如：只允许用户访问自己的数据
    return true;
}
```

**步骤4.2：定义字段级权限**
在XMeta中定义字段级权限：
```xml
<field name="sensitiveData" visibleOn="${hasPermission('sensitive:view')}"/>
```

### 阶段5：XMeta配置

**步骤5.1：生成.xmeta.xml**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<xmeta x:schema="/nop/schema/xmeta.xdef"
       xmlns:x="/nop/schema/xdsl.xdef">
    <entity name="{module}Entity">
        <fields>
            <field name="id" domain="id" required="true"/>
            <field name="name" domain="name" required="true" maxLength="100"/>
            <field name="status" domain="status" required="true"/>
            <field name="createTime" domain="createTime"/>
            <field name="updateTime" domain="updateTime"/>
        </fields>

        <!-- getter方法 -->
        <getter name="getStatusName" computed="true">
            <expression>
                switch(this.status) {
                    case 0: return '待处理';
                    case 1: return '已处理';
                    case -1: return '已拒绝';
                    default: return '未知';
                }
            </expression>
        </getter>

        <!-- computed属性 -->
        <computed name="formattedCreateTime">
            <expression>
                DateTimeHelper.formatDate(this.createTime, 'yyyy-MM-dd HH:mm:ss');
            </expression>
        </computed>
    </entity>
</xmeta>
```

## AI推理策略

### 1. 服务方法设计推理
- **方法职责判断**：
  - 查询操作：@BizQuery
  - 变更操作：@BizMutation
  - 动作操作：@BizAction

- **参数设计**：
  - 使用@Name注解标记参数名
  - 使用合理的参数类型（String、Integer、Entity、Map等）

- **返回类型**：
  - 查询方法：Entity、List<Entity>、PageBean<Entity>
  - 变更方法：Entity、void
  - 动作方法：void

### 2. 扩展点推理
- **判断是否需要defaultPrepareSave**：
  - 需要设置默认值（如ID、状态、创建时间）
  - 需要执行业务规则验证

- **判断是否需要defaultPrepareUpdate**：
  - 需要更新时间戳
  - 需要执行业务规则验证

- **判断是否需要afterSave**：
  - 需要发送通知
  - 需要更新缓存
  - 需要记录日志

### 3. View DDD遵循推理
- **属性放置判断**：
  - 判断应该放在实体上还是XMeta中
  - 稳定属性放在实体上
  - 易变逻辑放在XMeta中

- **get方法暴露判断**：
  - 判断哪些数据应该通过getter暴露
  - 提供便捷的领域信息访问

### 4. 事务管理推理
- **判断是否需要事务**：
  - 变更操作通常需要事务
  - 查询操作通常不需要事务
  - 涉及多个数据库操作需要事务

- **判断事务边界**：
  - 事务边界尽可能小
  - 避免在事务中执行耗时操作

### 5. 数据权限推理
- **判断是否需要数据权限**：
  - 多租户系统需要数据权限
  - 敏感数据需要数据权限

- **判断数据权限范围**：
  - 行级权限：只能访问自己的数据
  - 列级权限：只能访问部分字段
  - 操作级权限：只能执行特定操作

## 验证点

### 1. BizModel验证
- [ ] BizModel是否正确继承CrudBizModel
- [ ] 是否正确使用内置方法
- [ ] 是否通过重写方法扩展功能

### 2. 注解验证
- [ ] 查询方法是否使用@BizQuery
- [ ] 变更方法是否使用@BizMutation
- [ ] 动作方法是否使用@BizAction
- [ ] 参数是否使用@Name注解

### 3. View DDD遵循验证
- [ ] 实体是否只包含数据属性
- [ ] 是否不在实体上添加业务方法
- [ ] 易变逻辑是否通过XMeta实现
- [ ] 是否正确使用get方法

### 4. 事务管理验证
- [ ] 变更方法是否使用@Transactional注解
- [ ] 事务边界是否合理

### 5. 数据权限验证
- [ ] 是否在XMeta中定义权限
- [ ] 是否在BizModel中实现checkDataAuth()

## 输出产物

### 1. BizModel服务定义（`.xbiz.xml`）
```xml
<?xml version="1.0" encoding="UTF-8"?>
<xbiz x:schema="/nop/schema/xbiz.xdef"
      xmlns:x="/nop/schema/xdsl.xdef">
    <bizModel name="{module}ServiceModel"
              entityName="{module}Entity">

        <!-- 查询方法 -->
        <query name="find{module}s">
            <params>
                <param name="query" type="QueryBean"/>
                <param name="pageNo" type="Integer"/>
                <param name="pageSize" type="Integer"/>
            </params>
            <return type="PageBean<{module}Entity>"/>
        </query>

        <query name="find{module}ById">
            <params>
                <param name="id" type="String"/>
            </params>
            <return type="{module}Entity"/>
        </query>

        <!-- 变更方法 -->
        <mutation name="create{module}" transactional="true">
            <params>
                <param name="entity" type="{module}Entity"/>
            </params>
            <return type="{module}Entity"/>
        </mutation>

        <mutation name="update{module}" transactional="true">
            <params>
                <param name="entity" type="{module}Entity"/>
            </params>
            <return type="{module}Entity"/>
        </mutation>

        <mutation name="delete{module}" transactional="true">
            <params>
                <param name="id" type="String"/>
            </params>
            <return type="void"/>
        </mutation>

        <!-- 动作方法 -->
        <action name="approve{module}" transactional="true">
            <params>
                <param name="id" type="String"/>
            </params>
            <return type="void"/>
        </action>
    </bizModel>
</xbiz>
```

### 2. API定义（`-api.xml`，可选）
```xml
<?xml version="1.0" encoding="UTF-8"?>
<api x:schema="/nop/schema/api.xdef"
     xmlns:x="/nop/schema/xdsl.xdef">

    <!-- 查询API -->
    <operation name="find{module}s" type="query">
        <params>
            <param name="query" type="QueryBean"/>
            <param name="pageNo" type="Integer"/>
            <param name="pageSize" type="Integer"/>
        </params>
        <return type="PageBean<{module}Entity>"/>
    </operation>

    <!-- 变更API -->
    <operation name="create{module}" type="mutation">
        <params>
            <param name="entity" type="{module}Entity"/>
        </params>
        <return type="{module}Entity"/>
    </operation>

    <!-- 动作API -->
    <operation name="approve{module}" type="action">
        <params>
            <param name="id" type="String"/>
        </params>
        <return type="void"/>
    </operation>
</api>
```

### 3. BizModel Java代码（可选）
```java
@BizModel("{module}")
public class {module}ServiceModel extends CrudBizModel<{module}Entity> {

    @Override
    public String getEntityName() {
        return "{module}";
    }

    @Data
    private {module}Entity entity;

    @BizQuery
    public PageBean<{module}Entity> find{module}s(
            @Name("query") QueryBean query,
            @Name("pageNo") Integer pageNo,
            @Name("pageSize") Integer pageSize) {
        // 实现...
    }

    @BizMutation
    @Transactional
    public {module}Entity create{module}(@Name("entity") {module}Entity entity) {
        return save(Collections.singletonMap("entity", entity));
    }

    @Override
    protected void defaultPrepareSave(EntityData<{module}Entity> entityData,
                                      IServiceContext context) {
        // 实现...
    }
}
```

### 4. 服务设计文档（`service-design-{module}.md`）
包含：
- 服务方法列表
- 业务规则说明
- 扩展点说明
- 数据权限说明
- 事务管理说明

## 下一步工作

当前skill完成服务层设计，生成以下产物：
1. `{module}.xbiz.xml`（BizModel服务定义）
2. `{module}-api.xml`（API定义，可选）
3. BizModel Java代码（可选）
4. 服务设计文档（`service-design-{module}.md`）

这些产物将传递给下一个skill（nop-batch-engineer）用于批处理设计。

