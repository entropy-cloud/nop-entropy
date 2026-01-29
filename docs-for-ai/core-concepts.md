# Nop平台核心概念

## 平台概述

Nop平台是基于可逆计算原理的下一代低代码开发平台，采用框架中立设计原则，可以运行在Spring/Quarkus/Solon等多种底层框架之上。

### 可逆计算核心公式
```
App = Delta x-extends Generator<DSL>
```

**核心特性：**
- **Delta定制**：通过差量定制实现软件复用
- **元编程**：基于DSL的代码生成和模型驱动开发
- **框架中立**：业务代码与底层框架解耦
- **可逆性**：支持代码的逆向工程和重构

## 核心架构

### 1. 差量化软件生产线
Nop平台采用差量化软件生产线模式，所有业务开发都基于模型驱动：
- **DSL定义**：使用领域特定语言描述业务模型
- **代码生成**：基于DSL自动生成基础代码
- **Delta定制**：通过差量定制实现个性化需求

### 2. 模块化架构
```
业务应用层
├── 前端界面 (Vue/React)
├── API服务层 (GraphQL/REST)
├── 业务逻辑层 (BizModel)
├── 数据访问层 (IEntityDao)
└── 数据存储层 (Database)

平台支撑层
├── 配置管理 (@InjectValue)
├── 事务管理 (@Transactional)
├── 异常处理 (NopException)
└── 工具类库 (StringHelper等)
```

### 3. 开发模式
- **模型驱动开发**：先定义模型，再生成代码
- **差量定制**：在生成代码基础上进行定制
- **测试驱动**：内置AutoTest自动化测试框架
- **配置驱动**：通过配置实现功能开关和扩展

## 关键组件

### 数据访问层 (IEntityDao)
统一的数据访问接口，支持CRUD操作和复杂查询：
```java
public interface IEntityDao<T> {
    // CRUD操作
    void saveEntity(T entity);
    void updateEntity(T entity);
    void deleteEntity(T entity);
    
    // 查询操作
    T getEntityById(Object id);
    List<T> findAllByQuery(QueryBean query);
    PageBean<T> findPageByQuery(QueryBean query);
}
```

### 业务逻辑层 (BizModel)
业务逻辑处理的核心组件：
```java
public class CrudBizModel<T> {
    // 内置查询方法
    public PageBean<T> findPage(QueryBean query, int pageNo, int pageSize);
    public List<T> findList(QueryBean query);
    
    // 内置CRUD方法
    public T save(T data);
    public T update(T data);
    public void delete(Object id);
}
```

### GraphQL服务
基于GraphQL的API服务：
- **协议中立**：支持GraphQL和REST双协议
- **自动生成**：基于BizModel自动生成GraphQL Schema
- **批量加载**：内置DataLoader实现批量数据加载
- **权限控制**：细粒度的权限控制机制

## 开发规范

### 1. 代码生成规范
- 所有实体类、API接口类由代码生成器生成
- 生成代码基础上通过Delta机制进行定制
- 避免手工编写可由模型推导的代码

### 2. 配置管理规范
- 使用`@InjectValue`注解进行配置注入
- 配置项统一管理，支持环境隔离
- 配置变更自动生效，无需重启

### 3. 异常处理规范
- 统一使用`NopException`异常体系
- 错误码统一管理，支持国际化
- 异常信息包含完整的上下文信息

### 4. 事务管理规范
- 使用`@Transactional`注解声明事务
- 支持多种事务传播级别
- 事务边界清晰，避免长事务

## 最佳实践

### 1. 模块设计
- 模块职责单一，接口清晰
- 模块间依赖最小化
- 支持模块的独立开发和测试

### 2. 数据模型设计
- 实体设计符合领域驱动设计原则
- 关联关系清晰，避免循环依赖
- 支持版本管理和数据迁移

### 3. API设计
- API接口设计符合RESTful原则
- 支持GraphQL的灵活查询
- API版本管理，支持平滑升级

### 4. 性能优化
- 查询优化，避免N+1查询问题
- 缓存策略，合理使用多级缓存
- 批量操作，减少数据库交互次数

## 扩展机制

### 1. Delta定制机制

Delta定制是Nop平台实现可逆计算的核心机制，通过差量定制实现功能扩展，无需修改基础产品源码。

#### 1.1 Delta文件位置

Delta文件必须放在`_vfs/_delta/{deltaDir}`目录下：

```
src/main/resources/_vfs/_delta/default/nop/auth/app.orm.xml
```

- `_vfs/_delta`：固定前缀
- `default`：delta目录名，可自定义
- `nop/auth/app.orm.xml`：原始模型文件的相对路径

#### 1.2 x:extends属性

Delta文件必须使用`x:extends`属性继承原有模型：

```xml
<orm x:schema="/nop/schema/orm/orm.xdef" xmlns:x="/nop/schema/xdsl.xdef"
     x:extends="super">
```

`x:extends="super"`表示继承原始模型。

#### 1.3 Delta操作

**新增字段**：
```xml
<column name="mobile" stdDomain="string" displayName="手机号" length="20" />
```

**修改字段**：
```xml
<column name="userName" stdDomain="string" displayName="用户名"
        length="200" x:override="replace" />
```

**删除字段**：
```xml
<column name="oldField" x:override="remove" />
```

#### 1.4 x:override模式

- `replace`：完全覆盖原有节点
- `merge`：合并属性，并按照标签名合并子节点
- `bounded-merge`：只保留派生节点中定义过的子节点
- `remove`：删除基类中的节点

#### 1.5 Delta合并优先级

Delta合并按以下优先级执行（从高到低）：
1. `x:post-extends`：后置扩展
2. 当前模型
3. `x:gen-extends`：生成期扩展
4. `x:extends`：基础扩展

#### 1.6 三明治架构

Delta定制生成的实体类遵循三明治架构：

```
CustomClass extends _AutoGenClass extends BaseClass
```

- `CustomClass`：手工编写的定制代码，继承自动生成类
- `_AutoGenClass`：代码生成器自动生成的类，继承基础类
- `BaseClass`：平台提供的基础类

这种架构允许开发者在不修改自动生成代码的情况下，对功能进行扩展和定制。

#### 1.7 Delta文件系统和分层叠加

Nop平台的虚拟文件系统（VFS）支持Delta分层叠加机制，类似于Docker的overlay-fs分层文件系统。

**Delta目录结构**：
```
src/main/resources/_vfs/_delta/default/nop/auth/app.orm.xml
```

- `_vfs/_delta`：固定前缀
- `default`：delta目录名，可自定义
- `nop/auth/app.orm.xml`：原始模型文件的相对路径

**分层叠加规则**：
- 如果同时存在`/_vfs/_delta/default/nop/auth/app.orm.xml`和`/nop/auth/app.orm.xml`文件，则实际使用的是`_delta`目录下的版本
- 可以通过配置增加多个delta分层，例如配置`nop.core.vfs.delta-layer-ids=base,hunan`表示启用两个delta层
- Delta合并按优先级执行（从高到低）：`x:post-extends` > 当前模型 > `x:gen-extends` > `x:extends`

**Delta文件继承**：
```xml
<orm x:schema="/nop/schema/orm/orm.xdef" xmlns:x="/nop/schema/xdsl.xdef"
     x:extends="super">
```
`x:extends="super"`表示继承原始模型。

#### 1.8 Delta在服务层的应用

Delta定制机制不仅适用于数据模型，还可以在服务层实现深度定制，无需修改基础产品源码。

**扩展返回结果对象**：
通过`@BizLoader`注解在GraphQL服务层面增加扩展字段，无需修改原有服务函数。

```java
@BizModel("LoginApi")
public class LoginApiBizModelDelta {
    @BizLoader(autoCreateField = true, forType = LoginResult.class)
    @LazyLoad
    public String location(@ContextSource LoginResult result,
                           IServiceContext context) {
        return "loc:" + result.getUserInfo().getUserId();
    }
}
```

**扩展输入请求对象**：
通过`@Priority`注解定义优先级，覆盖原有服务函数。

```java
@BizModel("LoginApi")
public class LoginApiBizModelDelta {
    @Inject
    LoginApiBizModel loginApiBizModel;

    @BizMutation("login")
    @Auth(publicAccess = true)
    @Priority(NORMAL_PRIORITY - 100)
    public CompletionStage<LoginResult> loginAsync(
        @RequestBean LoginRequestEx request, IServiceContext context) {
        request.setAttr("a","123");
        return loginApiBizModel.loginAsync(request, context);
    }
}
```

**通过XBiz模型实现扩展**：
Nop平台支持从高代码到低代码再到无代码开发模式的平滑过渡，可以通过XBiz模型文件配置服务函数。

```xml
<!-- /_delta/default/nop/auth/model/LoginApi/LoginApi.xbiz -->
<biz x:schema="/nop/schema/biz/xbiz.xdef" xmlns:x="/nop/schema/xdsl.xdef"
     x:extends="super" xmlns:bo="bo" xmlns:c="c">
    <actions>
        <query name="myMethod" >
            <arg name="msg" type="String" optional="true" />
            <return type="String" />
            <source>
                return "hello:" + msg;
            </source>
        </query>
    </actions>
</biz>
```

#### 1.9 Delta在第三方框架集成中的应用

Nop平台的Delta定制机制可以很容易地和任何设计良好的第三方框架集成，极大提升第三方框架的可扩展性。

**集成优势**：
1. **自动缓存**：模型文件解析结果自动缓存，减少重复读取
2. **Delta定制**：无需修改原始文件，在`resources/_delta/default`目录下创建同名文件即可
3. **数据库存储**：支持将模型文件保存到数据库中，通过dao文件协议访问
4. **模型拆分**：通过`x:extends`语法实现大模型文件的拆分和重组
5. **依赖跟踪**：ResourceComponentManager自动跟踪模型文件依赖关系，自动失效缓存
6. **元编程**：通过`x:gen-extends`和`x:post-extends`等机制动态生成模型节点
7. **无需import机制**：模型文件内部无需设计特定的import/plugin机制

**集成示例**：
将第三方框架的模型加载器调用替换为`ResourceComponentManager.loadComponentModel(path)`，然后把模型文件移动到`resources/_vfs`目录下。

```json
{
  "x:gen-extends": "<chain-gen:GenBaseFlow xpl:lib='/xlib/chain-gen.xlib'/>",
  "steps": [
    {
      "x:extends": "my-step.chain.json",
      "name": "step1",
      "ui:hidden": false
    }
  ]
}
```

**元模型增强**：
如果为模型文件补充xdef元模型，则可以获得更多功能：
1. 支持XML、JSON、YAML等多种格式定义模型文件
2. 自动生成模型文件对应的Java类
3. 在IDEA中实现语法校验、自动补全、断点调试
4. 通过Excel定义模型内容，自动实现复杂嵌套结构的双向转换
5. 模型节点自动支持扩展属性

#### 1.10 Delta定制的实际应用场景

**产品化实现**：
Delta定制代码存放在单独的目录中，可以与程序主应用的代码相分离。例如将delta定制文件打包为`nop-platform-delta`模块，需要使用此定制时只要引入对应模块即可。

**应用定制**：
在开发具体应用时，可以使用delta定制机制来修正平台bug，或者增强平台功能。

**示例**：
`app-mall`项目通过定制`/_delta/default/nop/web/xlib/control.xlib`标签库来增加更多的字段控件支持。例如增加了`<edit-string-array>`控件，则在Excel数据模型中只要设置字段的数据域为`string-array`，则前端界面就会自动使用AMIS的`input-array`控件来编辑该字段。

**框架中立性**：
Delta定制的规则非常通用直观，与具体的应用实现无关。例如，如果要扩展Hibernate框架内置的`MySQLDialect`，在Nop平台中只需要增加文件`/_vfs/default/nop/dao/dialect/mysql.dialect.xml`，就可以确保所有用到MySQL方言的地方都会更新为使用新的Dialect模型。

### 2. 插件机制
支持插件化扩展：
- 插件独立开发，动态加载
- 插件间依赖管理
- 插件热部署和卸载

### 3. 配置扩展
通过配置实现功能扩展：
- 配置驱动的功能开关
- 运行时配置动态调整
- 配置的版本管理和回滚

## 学习路径

### 入门阶段
1. 理解可逆计算基本概念
2. 掌握Nop平台核心架构
3. 熟悉开发工具和流程

### 进阶阶段
1. 深入理解Delta定制机制
2. 掌握XLang元编程
3. 学习性能优化技巧

### 高级阶段
1. 平台扩展和定制开发
2. 复杂业务场景实践
3. 架构设计和性能调优

---

**注意：** 本文档是docs-for-ai目录的核心概念文档，所有外部依赖的概念都已内化到本文档中，确保docs-for-ai目录的自包含性。