# 多租户模型

Nop 平台的多租户支持是**框架内置的薄层**，不是业务功能。ORM 模型上声明 `useTenant="true"` 后，框架自动完成过滤、填充和隔离，开发者不需要手动处理租户 ID。

## 声明租户实体

在 `model/*.orm.xml` 的 `<entity>` 上设置两个属性：

```xml
<entity name="NopAuthUser" tableName="nop_auth_user" useTenant="true" tenantProp="nopTenantId">
    ...
</entity>
```

| 属性 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `useTenant` | 否 | `false` | 是否启用租户隔离 |
| `tenantProp` | 否 | `nopTenantId` | 租户 ID 对应的字段名。不指定时框架自动使用 `nopTenantId` |

### 自动创建租户列

当 `useTenant="true"` 且没有显式声明 `nopTenantId` 列时，`OrmEntityModelInitializer` 自动创建隐式列：

- 列名：`nopTenantId`
- SQL 类型：`VARCHAR(32)`
- 默认值：`"0"`
- 是否非空：`true`（mandatory）

该列在实体类中作为普通属性存在，可通过 `entity.getNopTenantId()` / `setNopTenantId()` 访问。

### XDef Schema

见 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/entity.xdef:20-21`：

```
@useTenant [是否启用租户]
@tenantProp [租户id列] 租户id所对应的column的name。如果useTenant为true，tenantProp的缺省值为nopTenant。
```

## 为什么 ORM 模型不需要预置租户 ID

框架在以下**所有**入口自动填充 `tenantId`：

| 阶段 | 组件 | 行为 |
|------|------|------|
| **save/insert** | `OrmEntityIdGenerator.initTenantId()` | 若租户属性为空，自动设置为 `ContextProvider.currentTenantId()`；若已设置且不匹配当前上下文，抛跨租户异常 |
| **load** | `EntityPersisterImpl.processTenantId()` | 若租户属性未初始化，注入当前租户 ID；若已初始化，校验与当前上下文一致 |
| **session 缓存** | `TenantOrmSessionEntityCache.makeTenantId()` | 实体进入缓存时自动补填租户 ID；缓存按租户分区 |
| **集合加载** | `CollectionPersisterImpl` | 加载子表集合时自动填写当前租户 ID |

因此，ORM 模型只需要声明 `useTenant="true"`，开发者不需要在业务代码中调用 `entity.setNopTenantId(...)`。

## 自动租户过滤

### EQL 编译期

`EqlTransformVisitor` 在编译 EQL 时，对 `useTenant="true"` 的实体对应的表自动追加 `tenantCol = ?` 到 WHERE 子句。运行时通过 `TenantParamBuilder` 从 `ContextProvider.currentTenantId()` 获取当前租户 ID。

### SQL 生成

`GenSqlHelper` 在生成以下 SQL 时自动追加租户条件：

| 场景 | 方法 |
|------|------|
| 按 ID 加载 | `genEntityFilter()` → `genEntityTenantFilter()` |
| 批量加载 | `appendBatchLoadEq()` |
| DELETE/UPDATE | `genDeleteForUpdateSql()` |
| 默认查询 | `addDefaultFilter()` |
| findByExample | `appendExampleFilter()` |

### ORM DAO

`OrmEntityDao` 的所有查询（`findAllByQuery`、`findPageByQuery`、`findFirstByExample`、`deleteByExample` 等）自动包含租户过滤条件。

### EQL/SQL 手工查询

```java
// EQL——自动加租户过滤
List<MyEntity> list = dao().findAllByQuery("select o from MyEntity o where o.status = ?", 1);

// 唯一需要注意：原生 SQL 需要手动包含租户列
// dao().executeNativeSql("...") 不经过 EQL 编译，不会自动过滤
```

### 数据权限（data-auth.xml）与租户过滤的区别

| 维度 | 租户过滤（框架内置） | 数据权限（data-auth.xml） |
|------|---------------------|-------------------------|
| 触发条件 | `useTenant="true"` | 角色匹配的规则 |
| 实现层 | EQL 编译 / SQL 生成 | `CrudBizModel.prepareFindPageQuery()` |
| 过滤方式 | 自动追加 `tenantId = ?` | XPL 表达式生成 QueryBean 条件 |
| 跳过方式 | `ContextProvider.runWithoutTenantId()` | 无对应角色规则 |

两者是互补关系：租户过滤是**硬隔离**（ORM 层强制），数据权限是**软过滤**（BizModel 层追加）。租户过滤始终在数据权限之前执行。

## 临时切换租户

### 在指定租户上下文中运行

```java
ContextProvider.runWithTenant("tenantA", () -> {
    // 此范围内的所有 ORM 操作使用 tenantA
    List<MyEntity> list = dao().findAll();
});
```

### 跳过租户过滤

```java
ContextProvider.runWithoutTenantId(() -> {
    // 此范围内不追加租户过滤条件
    List<MyEntity> list = dao().findAll();
});
```

### 手动获取/设置当前租户

```java
// 获取
String tenantId = ContextProvider.currentTenantId();

// 设置（直接修改当前上下文的租户 ID，影响后续所有操作）
context.setTenantId("tenantB");
```

### 登录时设置租户

`AuthHttpServerFilter` 在认证通过后自动从 `UserContext.getTenantId()` 设置到 `IContext`。用户也可通过 HTTP 请求头 `nop-tenant` 传递租户。

## 跨租户保护

框架在以下场景检测跨租户访问并抛出 `ERR_ORM_NOT_ALLOW_PROCESS_ENTITY_IN_OTHER_TENANT`：

- `EntityPersisterImpl.processTenantId()`：加载时实体已有租户 ID 且与当前上下文不匹配
- `OrmEntityIdGenerator.initTenantId()`：保存时实体已设置租户 ID 且与当前上下文不匹配

## 缓存隔离

| 缓存层级 | 隔离方式 |
|----------|----------|
| Session 缓存 | `TenantOrmSessionEntityCache` 按租户分区存储 |
| 全局缓存 | `EntityPersisterImpl.getCacheKey()` 使用 `tenantId:id` 作为缓存键 |
| EQL 查询计划 | `TenantCachedQueryPlan` 按 `tenantId` 缓存已编译计划 |

## VFS 租户层

VFS 路径解析的第一个步骤是检查 `/_tenant/{tenantId}/...`（见 `vfs-and-resource-resolution.md`），支持不同租户使用不同版本的 DSL 配置、页面文件和 i18n 资源。

## 常见选型

| 场景 | 做法 |
|------|------|
| 新实体需要租户隔离 | `orm.xml` 中设 `useTenant="true"`，重新生成 |
| 现有实体新增租户 | `orm.xml` 中设 `useTenant="true"`，数据库补 `nop_tenant_id` 列和迁移脚本 |
| 不需要租户隔离的实体 | 不设 `useTenant`（默认为 `false`），框架完全忽略 |
| 混合使用（部分实体租户、部分不租户） | 各自在 `orm.xml` 独立声明，框架按实体模型分别处理 |
| 数据初始化 SQL | 必须手动包含租户列，因为原生 SQL 不经过 ORM |

## 源码锚点

| 组件 | 路径 |
|------|------|
| 租户 ID 持有 | `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/context/IContext.java` |
| 上下文工具 | `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/context/ContextProvider.java` |
| EQL 编译注入 | `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java` |
| SQL 生成注入 | `nop-persistence/nop-orm/src/main/java/io/nop/orm/sql/GenSqlHelper.java` |
| 加载时填充 | `nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/EntityPersisterImpl.java` |
| 保存时填充 | `nop-persistence/nop-orm/src/main/java/io/nop/orm/id/OrmEntityIdGenerator.java` |
| Session 缓存隔离 | `nop-persistence/nop-orm/src/main/java/io/nop/orm/session/TenantOrmSessionEntityCache.java` |
| 自动创建租户列 | `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/init/OrmEntityModelInitializer.java` |
| 认证过滤器设置租户 | `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java` |
| 实体模型接口 | `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/IEntityModel.java` |
| ORM 租户缓存计划 | `nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/TenantCachedQueryPlan.java` |
| VFS 租户层 | `nop-kernel/nop-core/src/main/java/io/nop/core/resource/store/DeltaResourceStore.java` |
| XDef Schema | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/entity.xdef` |

## 相关文档

- `orm-model-design.md` — ORM 模型设计规范
- `model-first-development.md` — 模型优先开发流程
- `auth-and-permissions.md` — 数据权限（软过滤）与租户隔离的配合
- `vfs-and-resource-resolution.md` — VFS Tenant 层解析
- `safe-api-reference.md` — 安全 API 速查
