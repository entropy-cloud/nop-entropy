# Nop平台与传统框架开发差异

**目标读者**：从传统框架迁移到Nop平台的开发者、AI助手

**核心原则**：Nop平台通过元编程和代码生成提供高度自动化，开发者应充分利用平台能力，避免重复实现已有功能。

## 一眼识别：哪些“传统框架写法”在 Nop 文档里是危险信号

当你在 Nop 相关文档/示例里看到下面这些内容时，通常意味着**示例未核对源码**、或者属于“可选集成 demo”但被误写成平台默认方式：

### 1) 注解集合差异（高频踩坑）

Nop 平台的常态是：**尽量少的注解 + 大量模型驱动/配置驱动 + 统一扩展点**。

- ✅ Nop 常用（需要以源码为准，至少应能在仓库找到真实定义/使用）：
  
  - `@Inject`（依赖注入）
  - `@InjectValue`（配置/值注入）
  - `@PostConstruct`（生命周期回调）
  - `@NopTestConfig`（测试容器初始化控制）
  - 业务/GraphQL 相关注解（例如 `@BizModel/@BizQuery/@BizMutation` 等，具体以模块实现为准）

- ❌ 传统框架常见但**Nop平台不直接支持**`：
  
  - Spring 组件与配置：`@Component/@Service/@Repository/@Configuration/@Bean/@Autowired`
  - Spring Web：`@RestController/@Controller/@RequestMapping/@GetMapping/@PostMapping`
  - Spring Test：`@SpringBootTest` 等
  - AOP/韧性/调度（如果不是仓库真实实现/真实注解）：`@Aspect/@Retryable/@CircuitBreaker/@Scheduled` 等

> 允许在“可选集成/对接第三方框架”主题内提及这些注解，但必须明确它们属于 **集成示例**，不是 Nop 平台原生实践。

### 2) IoC 注入规则差异（必须遵守）

- NopIoC **不支持** `@Inject` 注入 `private` 字段。
  - ✅ 字段注入：使用 `protected` 或 package-private
  - ✅ 更推荐：setter 注入（显式、可测试、也避免 private 字段注入限制）
- 配置/值注入使用 `@InjectValue`，不要写 Spring 的 `@Value`。

### 3) 测试体系差异（NopAutoTest）

Nop 平台对 JUnit5 的集成不是通过 Spring Test 体系完成的。

- ✅ 常见基类（仓库中真实存在）：
  - `io.nop.autotest.junit.JunitBaseTestCase`：启动/重启容器，支持 IoC 注入；不包含录制回放
  - `io.nop.autotest.junit.JunitAutoTestCase`：在 AutoTest 录制/回放机制上扩展
- ✅ 典型配置方式：在测试类上使用 `@NopTestConfig(...)`
- ❌ 避免写法：`@SpringBootTest`、以及仓库里不存在的 `JunitExtension` 之类类名

### 4) “横切能力”的实现方式差异（日志/审计/拦截）

传统框架常用 AOP 注解把审计、鉴权、限流等横切逻辑织入业务方法。

Nop 更常见的方式是使用**统一扩展点**：

- GraphQL/RPC：实现 `IGraphQLLogger`（例如 `nop-auth-service` 中的 `GraphQLAuditLogger`）
- ORM：实现/配置 ORM 的 interceptor/listener（例如 `IOrmInterceptor`、`IOrmDaoListener` 等）

这些扩展点能在平台层集中处理横切逻辑，避免业务代码散落大量注解。

---

## 差异1：数据访问层

```java
// ✅ 使用统一 IEntityDao<T> 泛型接口
@BizModel("User")
public class UserBizModel extends CrudBizModel<NopAuthUser> {
    @BizQuery
    public User getUserById(String userId) {
        return dao().requireEntityById(userId);
    }
}

// ✅ 通过IDaoProvider获取（仅在必要时）
@Inject
protected IDaoProvider daoProvider;

public void someMethod() {
    IUserDao<NopAuthUser> userDao = daoProvider.daoFor(NopAuthUser.class);
}
```

| 传统框架                                  | Nop平台                                          |
| ------------------------------------- | ---------------------------------------------- |
| `userDao.findById(id)`                | `dao().requireEntityById(id)`                  |
| `userDao.findAll()`                   | `dao().findAll()`                              |
| `userDao.findByUsername(name)`        | `dao().findFirstByExample(example)` 或QueryBean |
| `@Autowired private IUserDao userDao` | `dao()`方法或`IDaoProvider`                       |

---

## 差异2：事务管理

```java
// ✅ @BizMutation自动开启事务，无需@Transactional
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> {
    @BizMutation
    public Order submitOrder(SubmitOrderRequest request) {
        Order order = dao().requireEntityById(orderId);
        order.setStatus(status);
        dao().saveEntity(order);
        return order;
    }

    @BizQuery
    public Order getOrderById(String orderId) {
        return dao().requireEntityById(orderId);
    }
}
```

| 传统框架                                | Nop平台                      |
| ----------------------------------- | -------------------------- |
| `@Transactional`                    | `@BizMutation`（自动开启）       |
| `@Transactional(readOnly = true)`   | `@BizQuery`（查询）            |
| `@Transactional(propagation = ...)` | `txn().runInTransaction()` |

---

## 差异3：CrudBizModel使用规范

```java
// ✅ 使用父类方法，自动实现逻辑删除、数据权限检查
@BizModel("User")
public class UserBizModel extends CrudBizModel<NopAuthUser> {
    @Override
    protected void defaultPrepareSave(EntityData<NopAuthUser> entityData, IServiceContext context) {
        super.defaultPrepareSave(entityData, context);
        NopAuthUser entity = entityData.getEntity();
        entity.setCustomField("customValue");
    }

    @BizMutation
    public NopAuthUser createUser(@Name("data") Map<String, Object> data, IServiceContext context) {
        return doSave(data, null, (entityData, ctx) -> {
            NopAuthUser entity = entityData.getEntity();
            entity.setCustomField("customValue");
        }, context);
    }

    @BizQuery
    public PageBean<NopAuthUser> findUsers(QueryBean query, int pageNo, int pageSize) {
        return findPage(query, pageNo, pageSize);
    }
}
```

| 功能    | 直接调用dao()的影响 |
| ----- | ------------ |
| 逻辑删除  | ❌ 需要手动处理     |
| 数据权限  | ❌ 需要手动检查     |
| 数据验证  | ❌ 需要手动验证     |
| 唯一性检查 | ❌ 需要手动实现     |
| 审计字段  | ❌ 需要手动设置     |

---

## 差异4：审计字段

```java
// ✅ 审计字段由框架自动设置，无需手动处理
```

| 传统框架                             | Nop平台    |
| -------------------------------- | -------- |
| `user.setCreateTime(new Date())` | 删除（框架自动） |
| `user.setCreateBy(userId)`       | 删除（框架自动） |
| `user.setUpdateTime(new Date())` | 删除（框架自动） |
| `user.setUpdateBy(userId)`       | 删除（框架自动） |

---

## 差异5：唯一性检查

```xml
<!-- ✅ 在XMeta中配置唯一键 -->
<meta x:schema="/nop/schema/xmeta.xdef">
    <keys>
        <key name="uk_username" props="userName" displayName="用户名"/>
        <key name="uk_code_region" props="code,region" displayName="编码"/>
    </keys>
</meta>
```

| 传统框架                                  | Nop平台              |
| ------------------------------------- | ------------------ |
| `if (userDao.existsByUsername(name))` | 在XMeta配置keys       |
| 手动抛出异常                                | 框架自动抛出NopException |
| update时排除自己                           | 框架自动处理             |

---

## 差异6：多租户、逻辑删除、数据权限

```java
// ✅ 框架自动处理
@BizModel("User")
public class UserBizModel extends CrudBizModel<NopAuthUser> {
    @BizQuery
    public NopAuthUser getUserById(String userId) {
        // 自动添加：WHERE tenant_id = ?
        return dao().requireEntityById(userId);
    }

    @BizQuery
    public List<NopAuthUser> findUsers() {
        // 自动添加：WHERE deleted = 0 AND tenant_id = ?
        return dao().findAll();
    }

    @BizMutation
    public void deleteUser(@Name("userId") String userId) {
        // 自动设置：deleted = 1, delete_time = ?, delete_by = ?
        delete(Map.of("id", userId));
    }

    @BizQuery
    public List<NopAuthUser> findUsersByDepartment(Long deptId,IServiceContext context) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("deptId",deptId));
        return findPage(query,null,context);
    }
}
```

| 传统框架                      | Nop平台         |
| ------------------------- | ------------- |
| `WHERE tenant_id = ?`     | 框架自动添加        |
| `WHERE deleted = 0`       | 框架自动过滤        |
| 手动权限检查                    | 框架自动检查        |
| `entity.setDeleted(true)` | `delete()` 方法 |

---

## 差异7：模型驱动与代码生成

```java
// ✅ 基于orm.xml模型自动生成
// 1. 在app-mall.orm.xml中定义实体模型
// 2. mvn clean install
// 3. xxx-codegen模块自动生成：Entity类、XMeta、XView、GraphQL API、默认BizModel
// 4. 开发者只需编写自定义业务逻辑

@BizModel("User")
public class UserBizModel extends CrudBizModel<NopAuthUser> {
    @BizMutation
    public NopAuthUser register(@Name("data") Map<String, Object> data, IServiceContext context) {
        return doSave(data, null, (entityData,ctx)->{
            // 自定义业务逻辑
        },context);
    }
}
```

| 传统框架           | Nop平台                   |
| -------------- | ----------------------- |
| 手动编写Entity类    | orm.xml模型（自动生成）         |
| 手动编写DAO接口      | 不需要（使用IEntityDao）       |
| 手动编写Service类   | 继承CrudBizModel（只写自定义逻辑） |
| 手动编写Controller | 自动生成GraphQL API         |

---

## 差异8：异常处理

```java
// ✅ 定义ErrorCode接口
@Locale("zh-CN")
public interface MyErrors {
    String ARG_USER_ID = "userId";
    String ARG_USER_NAME = "userName";

    ErrorCode ERR_USER_NOT_FOUND = define("my.err.user.not-found", "用户[{userId}]不存在", ARG_USER_ID);
    ErrorCode ERR_USER_ALREADY_EXISTS = define("my.err.user.already-exists", "用户[{userName}]已存在", ARG_USER_NAME);
}

// ✅ 使用NopException
@BizModel("User")
public class UserBizModel extends CrudBizModel<NopAuthUser> {
    @BizMutation
    public NopAuthUser createUser(@Name("data") Map<String, Object> data, IServiceContext context) {
        if (StringHelper.isEmpty(userName)) {
            throw new NopException(MyErrors.ERR_INVALID_USER_NAME)
                .param(MyErrors.ARG_USER_NAME, userName);
        }
        return save(data, context);
    }
}
```

| 传统框架               | Nop平台          |
| ------------------ | -------------- |
| `RuntimeException` | `NopException` |
| 自定义异常类             | ErrorCode接口    |
| 硬编码错误信息            | 占位符+参数（国际化）    |

---

## 相关文档

- [CRUD开发指南](./business/crud-development.md)
- [复杂业务开发指南](./business/complex-business-development.md)
- [服务层开发指南](../03-development-guide/service-layer.md)
- [异常处理指南](../04-core-components/exception-handling.md)
- [代码风格标准](../07-best-practices/code-style.md)
- [模块结构指南](../03-development-guide/module-structure-guide.md)

---

## 核心原则

- ✅ 充分利用平台能力
- ❌ 避免重复实现已有功能
- ✅ 遵循平台约定和最佳实践
- ❌ 不要用传统框架的思维在Nop平台上开发

**记住：** 在Nop平台上，如果你发现自己实现了很多通用功能（如审计字段、唯一性检查、事务管理等），很可能做错了。先查阅文档，看看平台是否已经提供了这些功能。
