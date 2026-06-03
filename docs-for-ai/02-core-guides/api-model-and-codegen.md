# API 模型与代码生成

本文描述 Nop 平台 API 模型（`*.api.xml`）的结构、代码生成结果，以及生成物之间的设计关系。

## 核心概念：API 接口 vs SPI 接口

API 模型代码生成会产出**两套不同定位的接口**，这是理解整个生成体系的关键：

| | API 接口（`{ServiceName}.java`） | SPI 接口（`{ServiceName}Spi.java`） |
|---|---|---|
| **所属模块** | `*-api/` | `*-service/` 或 `*-core/` |
| **定位** | 外部系统 RPC 调用的**客户端契约** | 模块内部服务实现的**提供方契约** |
| **方法签名** | 包装 `ApiRequest<T>` / `ApiResponse<T>`，带 JAX-RS 注解 | 裸参数 + `FieldSelectionBean` + `IServiceContext` |
| **消费者** | 外部调用方（跨模块、跨服务） | 模块内部 `Impl` 实现类 |
| **覆盖策略** | 强制覆盖（每次重新生成） | 强制覆盖 |
| **方法变体** | 每个方法生成 4 个变体（sync/async x 简单/ApiRequest 包装） | 每个方法生成 1 个变体（sync 或 async） |

两者是**同一业务能力的两种视角**：

- **API 接口**面向 RPC 客户端，用 `ApiRequest/ApiResponse` 做统一请求/响应包装，包含 HTTP 路由注解（`@POST`、`@Path`），可直接通过 HTTP/RPC 调用。
- **SPI 接口**面向服务端实现者，暴露框架级别的上下文参数（`FieldSelectionBean` 用于字段选择，`IServiceContext` 用于请求上下文），是 `Impl` 类需要实现的契约。

**注意**：SPI 接口不是标准的"Java SPI"（`ServiceLoader` 机制），而是 Nop 平台的服务提供者接口。它通过 `_api-impl.beans.xml` 注册到 IoC 容器。

## API 模型结构（`*.api.xml`）

API 模型遵循 XDef schema `/nop/schema/api.xdef`，核心结构：

```xml
<api ext:appName="my-app"
     ext:serviceModuleName="my-app-service"
     ext:servicePackageName="com.example.app.service"
     ext:apiModuleName="my-app-api"
     apiPackageName="com.example.app.api">

    <services>
        <service name="OrderService" displayName="订单服务"
                 className="com.example.app.api.OrderService">
            <method mutation="true" name="createOrder" displayName="创建订单"
                    requestMessage="com.example.app.api.beans.CreateOrderRequest"
                    responseMessage="com.example.app.api.beans.CreateOrderResponse"/>
        </service>
    </services>

    <messages>
        <message name="CreateOrderRequest" displayName="创建订单请求">
            <field propId="1" name="orderId" displayName="订单ID" mandatory="true">
                <schema type="String" precision="50"/>
            </field>
        </message>
        <message name="CreateOrderResponse" displayName="创建订单响应">
            <field propId="1" name="status" displayName="状态">
                <schema type="Integer"/>
            </field>
        </message>
    </messages>
</api>
```

### 必需的 ext 属性

| 属性 | 说明 | 示例 |
|------|------|------|
| `ext:appName` | 应用名 | `nop-wf` |
| `ext:apiModuleName` | API 模块名 | `nop-wf-api` |
| `ext:serviceModuleName` | Service 模块名 | `nop-wf-core` |
| `ext:servicePackageName` | Service 包名 | `io.nop.wf.core.service` |
| `apiPackageName` | API 包名（非 ext 前缀） | `io.nop.wf.api` |
| `ext:metaModuleName` | Meta 模块名（可选，默认从 serviceModuleName 推导） | `nop-wf-meta` |
| `ext:moduleId` | 模块 ID（可选，默认从 appName 推导） | `nop/wf` |

### 方法属性

| 属性 | 说明 |
|------|------|
| `mutation` | `true` = 写操作（`@BizMutation`），`false` = 读操作（`@BizQuery`） |
| `requestMessage` | 请求消息类的全限定名 |
| `responseMessage` | 响应类型（`void` 表示无返回值） |
| `tagSet` | 标签集，影响生成行为（如 `sync` 生成同步方法，`biz` 生成 xbiz） |

### 消息字段属性

| 属性 | 说明 |
|------|------|
| `name` | 字段名 |
| `propId` | 属性序号（用于 `@PropMeta`） |
| `mandatory` | 是否必填 |
| `displayName` | 显示名 |
| `schema/type` | Java 类型 |
| `schema/domain` | 数据域 |
| `schema/precision` | 精度/长度 |
| `schema/dict` | 字典 |

## 生成物完整清单

模板位置：`nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/api/`

### 1. API 模块（`{apiModuleName}/`，如 `nop-wf-api/`）

| 生成文件 | 覆盖策略 | 说明 |
|---------|---------|------|
| `model/{appName}.api.xml` | 强制覆盖 | API 模型的重新序列化副本 |
| `{ServiceName}.java` | 强制覆盖 | `@BizModel` 标注的 Java 接口，面向外部 RPC 客户端 |
| `beans/{MessageName}.java` | **保留**（仅首次生成） | 用户可编辑的 Bean 类，继承 `_MessageName` |
| `beans/_gen/_{MessageName}.java` | 强制覆盖 | 生成的基类，包含所有字段、getter/setter、`@PropMeta` |

**API 接口每个方法生成 4 个变体：**

```java
// 变体 1: async + 简单请求
CompletionStage<ApiResponse<ResponseBean>> methodAsync(RequestBean request, String selection);

// 变体 2: sync + 简单请求
ApiResponse<ResponseBean> method(RequestBean request, String selection);

// 变体 3: async + ApiRequest 包装
CompletionStage<ApiResponse<ResponseBean>> api_methodAsync(ApiRequest<RequestBean> request, ICancelToken cancelToken);

// 变体 4: sync + ApiRequest 包装
ApiResponse<ResponseBean> api_method(ApiRequest<RequestBean> request, ICancelToken cancelToken);
```

### 2. Service 模块（`{serviceModuleName}/`，如 `nop-wf-core/`）

| 生成文件 | 覆盖策略 | 说明 |
|---------|---------|------|
| `{ServiceName}Spi.java` | 强制覆盖 | SPI 接口，面向服务端实现者 |
| `impl/{ServiceName}Impl.java` | **保留**（仅首次生成） | 用户可编辑的实现骨架 |
| `_api-impl.beans.xml` | 强制覆盖 | IoC 注册，映射 Spi → Impl，位于 `{serviceModule}/beans/` |
| `{bizObjName}/_{ServiceName}.xbiz` | 强制覆盖 | 生成的 Biz 模型（当 service 有 `@biz` tag） |
| `{bizObjName}/{bizObjName}.xbiz` | **保留**（仅首次生成） | 用户可编辑的 Biz 模型，`x:extends` 生成文件 |

**SPI 接口每个方法生成 1 个变体：**

```java
// tagSet 包含 "sync" 时：
ResponseBean method(RequestBean request, FieldSelectionBean selection, IServiceContext ctx);

// 默认（async）：
CompletionStage<ResponseBean> methodAsync(RequestBean request, FieldSelectionBean selection, IServiceContext ctx);
```

### 3. Meta 模块（`{metaModuleName}/`，如 `nop-wf-meta/`）

| 生成文件 | 覆盖策略 | 说明 |
|---------|---------|------|
| `{ServiceName}/_{MessageName}.xmeta` | 强制覆盖 | 消息字段的元数据定义 |
| `{ServiceName}/{MessageName}.xmeta` | **保留**（仅首次生成） | 用户可编辑的 xmeta，`x:extends` 生成文件 |

### 4. 前端页面（`api-web/` 模板集）

模板位置：`nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/api-web/`

| 生成文件 | 覆盖策略 | 说明 |
|---------|---------|------|
| `auth/_{appName}-api.action-auth.xml` | 强制覆盖 | API 方法的权限配置与菜单 |
| `pages/{ServiceName}/_{ServiceName}.view.xml` | 强制覆盖 | 生成的视图定义 |
| `pages/{ServiceName}/{ServiceName}.view.xml` | **保留** | 用户可编辑的视图 |
| `pages/{ServiceName}/main.page.yaml` | 强制覆盖 | 主页面 AMIS YAML |
| `pages/{ServiceName}/{methodName}.page.yaml` | 强制覆盖 | 每个方法对应的 AMIS 页面 |

## 覆盖策略规则

| 规则 | 说明 |
|------|------|
| `//__XGEN_FORCE_OVERRIDE__` | 模板中的此标记表示文件每次生成都覆盖 |
| `_` 前缀文件 | 强制覆盖（如 `_WfStartRequestBean.java`、`_WorkflowService.xbiz`） |
| `_gen/` 目录 | 强制覆盖 |
| 无 `_` 前缀的用户文件 | 仅首次创建（保留文件），如 `WfStartRequestBean.java`、`WorkflowServiceImpl.java` |

### 继承与 Delta 模式

```
_WfStartRequestBean.java  ←──  WfStartRequestBean.java
(强制覆盖，生成字段)         (保留文件，用户扩展)

_{MessageName}.xmeta      ←──  {MessageName}.xmeta
(强制覆盖，生成 props)       (保留文件，用户定制)

_{ServiceName}.xbiz       ←──  {bizObjName}.xbiz
(强制覆盖，生成 actions)     (保留文件，用户定制)
```

## IoC 注册机制

`_api-impl.beans.xml` 为每个 service 注册 Spi → Impl 的 IoC 映射：

```xml
<bean id="io.nop.wf.core.service.WorkflowServiceSpi" ioc:default="true"
      class="io.nop.wf.core.service.impl.WorkflowServiceImpl"/>
```

`ioc:default="true"` 表示这是一个默认实现，可被 Delta 定制覆盖。文件位置：`{serviceModuleName}/src/main/resources/_vfs/{moduleId}/beans/_api-impl.beans.xml`

## API 接口与 SPI 接口的方法映射

框架在运行时自动完成 API 接口到 SPI 接口的桥接：

```text
外部调用方
  → {ServiceName}.java (API 接口, ApiRequest/ApiResponse 包装)
  → [框架自动拆包/装包]
  → {ServiceName}Spi.java (SPI 接口, 裸参数 + FieldSelectionBean + IServiceContext)
  → {ServiceName}Impl.java (实现类)
```

API 接口中的 `api_method(ApiRequest<T> request, ICancelToken cancelToken)` 变体是框架内部使用的完整形式，框架从中提取请求数据、构建 `FieldSelectionBean` 和 `IServiceContext`，然后调用 SPI 方法。

## 与 ORM 模型生成的关系

API 模型生成是独立于 ORM 模型生成的。在标准业务模块中：

- **ORM 生成**（`/nop/templates/orm`）：从 `*.orm.xml` 生成 Entity、DAO、BizModel、XMeta、页面等 CRUD 相关产物
- **API 生成**（`/nop/templates/api`）：从 `*.api.xml` 生成 RPC 接口、Message Bean、SPI、实现骨架等 RPC 相关产物

两者可以并存于同一模块。ORM 生成面向 CRUD 场景，API 生成面向跨模块/跨服务 RPC 调用场景。

## 什么时候使用 API 模型

| 场景 | 是否使用 API 模型 |
|------|-----------------|
| 模块需要给外部系统提供强类型 RPC 接口 | 是 |
| 跨模块、跨服务的方法调用 | 是 |
| 只需 CRUD 操作，已有 ORM 生成覆盖 | 否（用 BizModel 即可） |
| 模块内部 BizModel 间调用 | 否（用 `I*Biz` 接口，见 `add-cross-module-biz-interface.md`） |
| 只在模块内部使用的临时 DTO | 否（用 `@DataBean`，见 `dto-json-and-message-beans.md`） |

## API 接口与 `I*Biz` 接口的区别

| | API 接口（`{ServiceName}.java`） | `I*Biz` 接口 |
|---|---|---|
| **定位** | 外部系统 RPC 调用 | 模块内部 BizModel 间调用 |
| **位置** | `*-api/` | `*-dao/.../biz/` |
| **方法签名** | `ApiRequest/ApiResponse` 包装 | 裸参数，带 `@BizQuery`/`@BizMutation` 注解 |
| **生成方式** | 由 `*.api.xml` codegen 生成 | 手动编写 |
| **IoC 注册** | `_api-impl.beans.xml` | Nop IoC 自动发现 `@BizModel` |

## 常见误区

1. 在 SPI 实现中直接使用 `ApiRequest/ApiResponse`——SPI 接口不使用这些包装类
2. 手动修改 `_` 前缀的生成文件——应该修改源模型或保留层文件
3. 把所有 DTO 都放进 `*-api/`——`*-api/` 只放外部 RPC 接口的 Message Bean
4. 混淆 API 接口与 `I*Biz` 接口——前者面向外部 RPC，后者面向模块内部 BizModel 间调用
5. 在 Impl 中手写框架已自动处理的参数拆包/装包逻辑

## CRUD API 代码生成

除从 `*.api.xml` 手工定义的 API 接口外，Nop 平台还支持从 ORM 模型自动生成 CRUD API 接口和强类型 InputBean/OutputBean。

模板位置：`nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/crud-api/`

### 核心接口

| 接口 | 位置 | 说明 |
|------|------|------|
| `ICrudApi<I, O>` | `nop-api-core` → `io.nop.api.core.api` | 通用 CRUD 泛型接口，I=输入类型，O=输出类型 |
| `ICrudTreeApi<O>` | `nop-api-core` → `io.nop.api.core.api` | 树形操作接口（findRoots/findTreeEntityPage/findTreeEntityList） |
| `CrudInputBase` | `nop-api-core` → `io.nop.api.core.api` | InputBean 基类，含 `_chgType` + `@JsonAnySetter`/`@JsonAnyGetter` |

`ICrudApi<I, O>` 的方法集：get/findPage/findList/findFirst/findCount/save/update/delete/saveOrUpdate/batchDelete/batchGet 等，其中 save/update/saveOrUpdate 使用强类型 `I` 输入。

`CrudInputBase` 的 `@JsonAnySetter` 吸收未声明的 JSON 属性（如 `_chgType_items`、`_writeMode_dept`）到 `_extAttrs`，`@JsonAnyGetter` 将其展开回 JSON 根级。

### 生成触发点

在 `*-meta/precompile/` 中新增 `gen-crud-api.xgen`：

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<c:script>
codeGenerator.withTargetDir("../{appName}-api/src/main/java/")
    .renderModel('/{moduleId}/orm/app.orm.xml','/nop/templates/crud-api', '/',$scope);
</c:script>
```

数据源是 ORM 模型（不是 xmeta），可用变量来自 `DefineLoopForOrm` + 额外的 `apiPackageName`/`apiPackagePath`。

### 字段过滤规则

- **InputBean 字段**：`meta-gen:IsColInsertable(col) || meta-gen:IsColUpdatable(col)` 的列 + `insertable || updatable` tag 的关系
- **OutputBean 字段**：`!col.tagSet.contains('not-pub')` 的列 + `pub` tag 的关系
- **树形检测**：`entityModel.getColumnByTag('parent') != null` 时 `Api` 接口额外继承 `ICrudTreeApi<O>`

### 生成物清单（每个 ORM 实体）

| 生成文件 | 覆盖策略 | 说明 |
|---------|---------|------|
| `{apiPackage}/beans/_gen/_{EntityName}InputBean.java` | 强制覆盖 | InputBean 基类，extends CrudInputBase |
| `{apiPackage}/beans/{EntityName}InputBean.java` | 保留 | 用户扩展 InputBean |
| `{apiPackage}/beans/_gen/_{EntityName}OutputBean.java` | 强制覆盖 | OutputBean 基类 |
| `{apiPackage}/beans/{EntityName}OutputBean.java` | 保留 | 用户扩展 OutputBean |
| `{apiPackage}/crud/_{EntityName}Api.java` | 强制覆盖 | API 基接口，extends ICrudApi<I, O> [+, ICrudTreeApi<O>] |
| `{apiPackage}/crud/{EntityName}Api.java` | 保留 | `@BizModel` 标注的用户扩展接口 |

### InputBean 关系字段

- to-one → `{RefEntityName}InputBean`（同一 `_gen` 包内的生成基类）
- to-many → `List<{RefEntityName}InputBean>`

### OutputBean 关系字段

- to-one → `Map<String, Object>`
- to-many → `List<Map<String, Object>>`

OutputBean 使用 `Map` 避免同模块循环依赖。

### 与 ORM 生成和 API 模型生成的关系

```
ORM 生成（/nop/templates/orm）  → Entity, DAO, BizModel, XMeta
API 生成（/nop/templates/api）  → 自定义 RPC 接口（从 .api.xml）
CRUD API 生成（/nop/templates/crud-api） → 强类型 CRUD 接口（从 ORM 模型）
```

三者并存于同一模块，互不冲突。

## 源码参考

| 内容 | 位置 |
|------|------|
| API XDef schema | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/api.xdef` |
| API 生成模板 | `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/api/` |
| 前端生成模板 | `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/api-web/` |
| API 模型类 | `nop-network/nop-rpc/nop-rpc-model/src/main/java/io/nop/rpc/model/` |
| 代码生成库 | `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/codegen/xlib/gen.xlib` |

## 相关文档

- `./api-and-graphql.md`
- `./dto-json-and-message-beans.md`
- `./model-first-development.md`
- `../03-runbooks/debug-codegen-and-generated-files.md`
- `../03-runbooks/add-cross-module-biz-interface.md`
- `../01-repo-map/domain-module-pattern.md`
