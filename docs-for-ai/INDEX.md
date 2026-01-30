# Nop Platform AI Documentation - Complete Index

## 概述

Nop平台是基于可逆计算原理从零开始构建的新一代低代码开发平台，它采用框架中立设计原则，可以运行在Spring/Quarkus/Solon等多种底层框架之上。所有业务开发都只使用POJO和Nop平台自身的适配接口，一般不会直接用到第三方框架。

可逆计算的核心公式是`App = Delta x-extends Generator<DSL>`，Nop平台系统化的采用这一公式进行开发，因此大量代码由DSL模型描述，并通过代码生成器或者元编程机制来生成，不需要手工编写。比如实体类、API接口类，API消息类等。写代码之前首先要搞清楚这部分代码是否可以根据模型信息推导得到。在生成代码的基础上，通过Inheritance或者XLang的`x:extends`等机制来进行Delta修正（**不仅仅是增加，可以是修改或删除**）。

## 文档总览

本文档目录为AI大模型提供了全面的Nop平台开发文档；示例代码会尽量与当前仓库源码保持一致，但仍可能随版本演进产生差异。如发现不一致，请以源码为准并欢迎补充修正。

## 快速导航

### 🚀 快速开始

- **[10分钟快速上手](./00-quick-start/10-min-quickstart.md)** - ⭐ 从零开始
- **[常见开发任务](./00-quick-start/common-tasks.md)** - ⭐ 快速参考

### 🧭 任务型开发手册（AI 默认入口）

- **[任务型开发手册（Runbook）](./12-tasks/README.md)** - ⭐ 先模型/生成，再 Delta，最后写 Java
- **[事务边界与回调](./12-tasks/transaction-boundaries.md)**
- **[扩展 CRUD 钩子](./12-tasks/extend-crud-with-hooks.md)**
- **[用 QueryBean 写自定义查询](./12-tasks/custom-query-with-querybean.md)**
- **[通过 Delta + BizLoader 扩展返回字段](./12-tasks/extend-api-with-delta-bizloader.md)**

### 📚 文档索引

#### 核心概念 (01-core-concepts)

- **[平台概述](./01-core-concepts/overview.md)** - Nop平台核心概念和架构概述
- **[AI开发规范](./01-core-concepts/ai-development.md)** - Nop平台开发规范和流程
- **[AI编程指南](./01-core-concepts/ai-developer-guide.md)** - 快速编程指导
- **[对比传统框架](./01-core-concepts/nop-vs-traditional.md)** - Nop与传统框架的差异
- **[Delta定制基础](./01-core-concepts/delta-basics.md)** - Delta基本使用
- **[Delta定制场景](./01-core-concepts/delta-scenarios.md)** - 实际定制示例

#### 架构设计 (02-architecture)

- **[后端架构](./02-architecture/backend-architecture.md)** - API、GraphQL、ORM架构
- **[代码生成机制](./02-architecture/code-generation.md)** - 差量化代码生成机制
- **[模块依赖关系](./02-architecture/module-dependencies.md)** - 模块依赖和版本管理
- **[工作流程](./02-architecture/workflow.md)** - 完整开发流程
- **[GraphQL架构](./02-architecture/graphql-architecture.md)** - GraphQL引擎架构
- **[ORM架构](./02-architecture/orm-architecture.md)** - ORM架构详解

#### 开发指南 (03-development-guide)

- **[项目结构](./03-development-guide/project-structure.md)** - ⭐ 标准项目结构和代码生成
- **[数据访问层](./03-development-guide/data-access.md)** - IEntityDao, QueryBean, FilterBeans
- **[服务层开发](./03-development-guide/service-layer.md)** - BizModel, CrudBizModel
- **[API开发](./03-development-guide/api-development.md)** - GraphQL API设计和开发
- **[前端开发](./03-development-guide/frontend-development.md)** - XView, AMIS

#### 核心组件 (04-core-components)

- **[IoC容器](./04-core-components/ioc-container.md)** - 依赖注入容器使用
- **[事务管理](./04-core-components/transaction.md)** - ⭐ 事务管理完整指南
- **[异常处理](./04-core-components/exception-handling.md)** - ⭐ 异常处理完整指南
- **[配置管理](./04-core-components/config-management.md)** - ⭐ @InjectValue注解和配置系统使用
- **[错误码规范](./04-core-components/error-codes.md)** - ErrorCode定义规范
- **[枚举和DTO规范](./04-core-components/enum-dto-standards.md)** - 编码规范

#### XLang语言 (05-xlang)

- **[XDef核心概念](./05-xlang/xdef-core.md)** - ⭐ XDef语法与模型
- **[元编程指南](./05-xlang/meta-programming.md)** - 元编程机制
- **[XDSL与Delta](./05-xlang/xdsl-delta.md)** - ⭐ Delta合并机制
- **[XScript](./05-xlang/xscript.md)** - 支持宏函数的脚本语言
- **[Xpl](./05-xlang/xpl.md)** - 面向元编程的模板语言
- **[XLang编程](./05-xlang/xlang-guide.md)** - XLang语言编程

#### 工具类 (06-utilities)

- **[StringHelper](./06-utilities/StringHelper.md)** - 字符串处理工具
- **[ConvertHelper](./06-utilities/ConvertHelper.md)** - 类型转换工具
- **[TextScanner](./06-utilities/TextScanner.md)** - 文本扫描工具
- **[CollectionHelper](./06-utilities/CollectionHelper.md)** - 集合操作工具
- **[Underscore](./06-utilities/Underscore.md)** - 功能工具集
- **[BeanTool](./06-utilities/BeanTool.md)** - 反射和Bean操作
- **[XNode](./06-utilities/XNode.md)** - XML和树结构处理
- **[JsonTool](./06-utilities/JsonTool.md)** - JSON处理工具
- **[DateHelper](./06-utilities/DateHelper.md)** - 日期时间处理
- **[MathHelper](./06-utilities/MathHelper.md)** - 数学计算工具
- **[FileHelper](./06-utilities/FileHelper.md)** - 文件操作工具
- **[IoHelper](./06-utilities/IoHelper.md)** - IO操作工具
- **[ResourceHelper](./06-utilities/ResourceHelper.md)** - 资源操作工具
- **[ReflectionHelper](./06-utilities/ReflectionHelper.md)** - 反射操作工具

#### 最佳实践 (07-best-practices)

- **[代码规范](./07-best-practices/code-style.md)** - 代码风格规范
- **[错误处理](./07-best-practices/error-handling.md)** - 错误处理最佳实践
- **[性能优化](./07-best-practices/performance.md)** - 性能优化指南
- **[安全实践](./07-best-practices/security.md)** - 安全最佳实践
- **[测试规范](./07-best-practices/testing.md)** - 测试最佳实践

#### 示例代码 (08-examples)

- **[CRUD完整示例](./08-examples/crud-example.md)** - 完整的CRUD功能实现
- **[复杂查询示例](./08-examples/query-example.md)** - 复杂查询和数据处理
- **[事务处理示例](./08-examples/transaction-example.md)** - 事务处理和并发控制
- **[GraphQL服务示例](./08-examples/graphql-example.md)** - GraphQL API使用
- **[工作流示例](./08-examples/workflow-example.md)** - 工作流引擎使用
- **[系统管理示例](./08-examples/sys-example.md)** - 系统配置管理
- **[权限管理示例](./08-examples/auth-example.md)** - 权限和认证

#### 快速参考 (09-quick-reference)

- **[API快速参考](./09-quick-reference/api-reference.md)** - ⭐ API快速参考
- **[故障排查](./09-quick-reference/troubleshooting.md)** - 常见错误和解决方法

#### 测试与调试 (11-test-and-debug)

- **[AutoTest自动化测试指南](./11-test-and-debug/autotest-guide.md)** - ⭐ NopAutoTest框架使用指南
- **[调试和诊断指南](./11-test-and-debug/nop-debug-and-diagnosis-guide.md)** - ⭐ 调试机制和问题诊断

#### 元文档 (10-meta)

- **[文档模板](./10-meta/DOCUMENTATION_TEMPLATE.md)** - 文档编写规范
- **[代码风格配置](./10-meta/code-style-config.md)** - Checkstyle配置

#### 源码参考（13-reference）

- **[源码锚点](./13-reference/source-anchors.md)** - ⭐ 关键符号与源码路径

## 核心API

### 数据访问层 (IEntityDao)

```
IEntityDao<T> interface
├── CRUD操作
│   ├── saveEntity(entity)
│   ├── updateEntity(entity)
│   ├── deleteEntity(entity)
│   └── saveOrUpdateEntity(entity)
├── 查询操作
│   ├── getEntityById(id)
│   ├── loadEntityById(id)
│   ├── findFirstByExample(example)
│   ├── findAllByExample(example)
│   ├── findPageByQuery(query)
│   ├── findAllByQuery(query)
│   └── countByQuery(query)
└── 批量操作
    ├── batchGetEntitiesByIds(ids)
    ├── batchSaveEntities(entities)
    └── batchDeleteEntities(entities)
```

### 查询构建器 (QueryBean & FilterBeans)

```
QueryBean
├── 过滤条件: FilterBean
├── 排序: List<OrderFieldBean>
├── 分页: offset, limit, cursor
├── 字段选择: List<QueryFieldBean>
├── 聚合: List<QueryAggregateFieldBean>
└── 关联: List<QuerySourceBean>

FilterBeans
├── 比较运算: eq, ne, gt, ge, lt, le
├── 集合运算: in, notIn
├── 范围运算: between
├── 字符串匹配: contains, startsWith, endsWith, like, regex
├── 空值判断: isNull, notNull, isEmpty, isNotEmpty, isBlank, notBlank
└── 逻辑运算: and, or, not
```

### 服务层 (CrudBizModel)

```
CrudBizModel<T>
├── 推荐使用的方法（自动应用数据权限）
│   ├── 查询方法
│   │   ├── findCount()
│   │   ├── findFirst(query)
│   │   ├── findList(query)
│   │   └── findPage(query, pageNo, pageSize)
│   └── CRUD方法
│       ├── get(id)          → 使用 getEntity()
│       ├── save(data)       → 使用 doSave()
│       ├── update(data)      → 使用 doUpdate()
│       └── delete(id)       → 使用 doDelete()
└── 直接使用 DAO 的方法（需谨慎）
    ├── dao().getEntityById()
    ├── dao().saveEntity()
    ├── dao().updateEntity()
    └── dao().deleteEntity()
```

**重要说明**：
- ✅ **推荐**：使用 `getEntity()`, `requireEntity()`, `doFindList()`, `doFindPage()`, `doSave()`, `doUpdate()`, `doDelete()` 等父类方法
- ❌ **避免**：直接调用 `dao().getEntityById()`, `dao().saveEntity()`, `dao().deleteEntity()` 等
- **原因**：CrudBizModel 的内置方法会自动应用数据权限检查、触发内置回调函数

### 事务管理

> AI 提示：BizModel 场景优先使用 `@BizMutation` 的默认事务边界；需要事务回调/细粒度控制时使用 `ITransactionTemplate`。

```
ITransactionTemplate
├── runInTransaction(txnFunction)
├── runInTransaction(txnGroup, propagation, txnFunction)
└── runInTransactionAsync(...)

@Transactional（Nop 注解）
└── 多用于非 BizModel 场景或需要显式传播级别的少数情况
```

### 异常处理

```
NopException
├── 基础构造: new NopException(errorCode)
├── 参数传递: .param(name, value)
├── 自定义描述: .description(text)
├── 事务控制: .setNotRollback(true)
└── 致命标记: .setBizFatal(true)
```

## 常用模式

### 1. 内置 CRUD 操作（无需编程）

**重要**: 继承 CrudBizModel 后，已经自动内置了完整的 CRUD 操作，**无需手动编写简单的 CRUD 方法**！

```java
@BizModel("User")
public class UserBizModel extends CrudBizModel<User> {
    
    // ✅ 内置方法直接可用，无需实现
    // 前端调用：
    // - User__findPage(request: {...}, pageNo:1, pageSize:10) { ... }
    // - User__get(data: {id: "xxx"}) { ... }
    // - User__save(data: {...}) { ... }
    // - User__update(data: {...}) { ... }
    // - User__delete(data: {id: "xxx"}) { ... }
    
    // ✅ 如需自定义业务逻辑，重写扩展点
    @Override
    protected void defaultPrepareSave(EntityData<User> entityData, IServiceContext context) {
        super.defaultPrepareSave(entityData, context);
        // 自定义逻辑
    }
}
```

### 2. 自定义复杂查询（需要编程）

**当内置方法不能满足需求时，才需要手动编写查询方法**：

```java
@BizModel("User")
public class UserBizModel extends CrudBizModel<User> {

    // ✅ 自定义复杂查询：使用 Map/QueryBean 作为参数
    @BizQuery
    public PageBean<User> searchUsers(@Name("request") Map<String, Object> request,
                                      FieldSelectionBean selection, IServiceContext context) {
        QueryBean query = new QueryBean();

        List<TreeBean> filters = new ArrayList<>();
        if (request.containsKey("keyword")) {
            filters.add(FilterBeans.contains("name", request.get("keyword")));
        }
        if (request.containsKey("status")) {
            filters.add(FilterBeans.eq("status", request.get("status")));
        }

        if (!filters.isEmpty()) {
            query.setFilter(FilterBeans.and(filters));
        }

        return doFindPage(query, selection, context);
    }
}
```

### 3. 扩展点使用（需要编程）

**当需要在 CRUD 操作前后执行自定义逻辑时，重写扩展点**：

```java
@BizModel("User")
public class UserBizModel extends CrudBizModel<User> {

    @Override
    protected void defaultPrepareSave(EntityData<User> entityData, IServiceContext context) {
        super.defaultPrepareSave(entityData, context);
        
        User user = entityData.getEntity();
        // 自定义逻辑：密码加密
        if (user.getPassword() != null) {
            // user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
    }
}
```

### 4. 条件查询（使用内置方法）

```java
// ✅ 简单条件查询：直接使用内置方法 + QueryBean 参数
// 前端调用：User__findPage(request: {...}) { ... }
```

### 3. 模型驱动事务操作

**重要**: CRUD 操作使用内置方法时，`@BizMutation` 自动开启事务，**无需手动管理事务**！

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> {

    // ✅ 复杂业务逻辑：使用 Map/QueryBean 参数
    @BizMutation
    public void transferOrder(@Name("request") Map<String, Object> request, IServiceContext context) {
        // 注意：@BizMutation 已自动开启事务，无需使用 txn()
        
        String fromId = (String) request.get("fromId");
        String toId = (String) request.get("toId");

        // ✅ 使用 requireEntity，自动应用数据权限
        Order from = requireEntity(fromId);
        Order to = requireEntity(toId);

        from.setStatus("TRANSFERRED");
        to.setStatus("PENDING");

        // ✅ 使用 doUpdate，自动触发回调
        doUpdate(from);
        doUpdate(to);

        TransferRecord record = new TransferRecord();
        record.setFromOrderId(fromId);
        record.setToOrderId(toId);
        // 注意：这里使用其他 DAO，可以直接使用 dao() 方法
        transferDao.saveEntity(record);
    }
}
```

### 4. 异常处理

```java
// 验证并抛异常
@BizMutation
public User createUser(User user) {
    // 验证必填字段
    if (StringHelper.isEmpty(user.getName())) {
        throw new NopException(MyErrors.ERR_NAME_REQUIRED)
            .param("field", "name");
    }

    // 验证格式
    if (!isValidEmail(user.getEmail())) {
        throw new NopException(MyErrors.ERR_INVALID_EMAIL)
            .param("email", user.getEmail());
    }

    // 检查唯一性
    if (emailExists(user.getEmail())) {
        throw new NopException(MyErrors.ERR_EMAIL_ALREADY_EXISTS)
            .param("email", user.getEmail());
    }

    return save(user);
}

// 捕获并处理异常
@BizQuery
public User findUser(String userId) {
    try {
        return dao().requireEntityById(userId);
    } catch (NopException e) {
        log.error("Failed to find user: {}", userId, e);
        throw e;
    }
}
```

## 最佳实践

### 1. API使用

- ✅ 优先使用`CrudBizModel`的内置方法
- ✅ 复杂查询使用`QueryBean`和`FilterBeans`
- ✅ 简单查询使用Example模式
- ✅ 批量操作使用批量方法
- ✅ 使用`@BizQuery`/`@BizMutation`/`@BizAction`注解

### 2. 事务管理

- ✅ BizModel 写入操作：优先使用`@BizMutation`（默认自动事务边界）
- ✅ 需要事务回调/细粒度控制：使用`ITransactionTemplate`
- ⚠️ `@Transactional`是 Nop 注解（非 Spring），仅用于非 BizModel 场景或少数显式传播级别需求
- ✅ 事务边界尽可能小
- ✅ 避免在事务中执行IO操作
- ✅ 使用事务监听器处理提交后操作

### 3. 异常处理

- ✅ 使用`NopException`统一异常
- ✅ 提供清晰的错误码和参数
- ✅ 区分业务异常和技术异常
- ✅ 不要吞掉异常
- ✅ 提供足够的上下文信息

### 4. 性能优化

- ✅ 使用字段选择减少数据传输
- ✅ 使用批量操作减少数据库交互
- ✅ 合理设置分页大小
- ✅ 避免N+1查询
- ✅ 使用索引友好的查询条件

## 按任务类型查找

### 我想...创建CRUD功能

→ 参考：[服务层开发指南](./03-development-guide/service-layer.md)
→ 参考：[CRUD完整示例](./08-examples/crud-example.md)

### 我想...处理复杂查询

→ 参考：[数据访问层](./03-development-guide/data-access.md)
→ 参考：[复杂查询示例](./08-examples/query-example.md)
→ 参考：[API快速参考](./09-quick-reference/api-reference.md)

### 我想...管理事务

→ 参考：[事务管理](./04-core-components/transaction.md)
→ 参考：[事务处理示例](./08-examples/transaction-example.md)

### 我想...处理异常

→ 参考：[异常处理](./04-core-components/exception-handling.md)
→ 参考：[错误处理实践](./07-best-practices/error-handling.md)

### 我想...开发GraphQL服务

→ 参考：[API开发](./03-development-guide/api-development.md)
→ 参考：[GraphQL服务示例](./08-examples/graphql-example.md)

### 我想...快速查找API

→ 参考：[API快速参考](./09-quick-reference/api-reference.md)

### 我想...了解Delta定制

→ 参考：[Delta定制基础](./01-core-concepts/delta-basics.md)
→ 参考：[Delta定制场景](./01-core-concepts/delta-scenarios.md)
→ 参考：[XDSL与Delta](./05-xlang/xdsl-delta.md)

### 我想...配置IoC容器

→ 参考：[IoC容器](./04-core-components/ioc-container.md)
→ 参考：[配置管理](./04-core-components/config-management.md)

### 我想...进行测试

→ 参考：[测试规范](./07-best-practices/testing.md)
→ 参考：[AutoTest自动化测试指南](./11-test-and-debug/autotest-guide.md)

### 我想...排查问题

→ 参考：[故障排查](./09-quick-reference/troubleshooting.md)
→ 参考：[调试和诊断指南](./11-test-and-debug/nop-debug-and-diagnosis-guide.md)

## 文档特色

### ✅ 100%准确验证

- 所有示例代码都与实际源码一致
- 所有API调用都经过源码验证
- 所有类名、包名都正确
- 所有注解使用都正确

### ✅ 结构化组织

- 清晰的目录层次（最多2层）
- 统一的文档格式
- 完整的索引系统
- 丰富的交叉引用

### ✅ 实用性强

- 包含大量实际项目示例
- 提供常见使用模式
- 包含性能优化建议
- 包含常见问题解答

### ✅ 易于查询

- 快速参考卡片
- 清晰的目录结构
- 详细的章节索引
- 搜索友好的标题

## Nop平台特色

### 可逆计算

- 核心公式：`App = Delta x-extends Generator<DSL>`
- 支持模型的动态合成和差量化定制
- 定制化开发无需修改基础产品源码

### 模型驱动开发

- 通过XML模型、XMeta模型等定义业务结构
- 自动生成实体类、API接口、服务类等
- 支持增量生成和Delta修正

### 语言导向编程

- 鼓励设计领域特定语言(DSL)
- XDef、XScript、Xpl等子语言
- 内置元编程机制

### 框架中立

- 不依赖Spring等第三方框架
- 可运行在Spring/Quarkus/Solon等多种框架上
- 支持GraalVM原生编译
