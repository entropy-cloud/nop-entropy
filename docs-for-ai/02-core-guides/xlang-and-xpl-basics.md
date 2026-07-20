# XLang、XPL、XLib 与 xrun 基础

本页只回答一个高频问题：

**当你在当前仓库里遇到 `.xpl`、`.xlib`、`.xrun`、`.xgen` 时，应该怎么快速理解它们的角色和最基本写法。**

## 默认结论

1. `.xpl` 是模板或执行片段，常见于代码生成和文本输出。
2. `.xlib` 是可复用的 XLang 库入口。
3. `.xrun` 是 runner / CLI 任务入口，常通过 `xpl:lib` 调用 XLib。
4. `.xgen` 常用于生成链路中的模板或预编译生成。
5. 日常开发只需要记住少量通用控制语法，不要一开始就深挖整套 XLang 语义。

## 先区分这四类文件

| 文件类型 | 常见用途 | 真实例子 |
|------|---------|---------|
| `.xpl` | 模板片段、文本输出、生成逻辑 | `nop-wf/nop-wf-web/src/main/resources/_vfs/nop/wf/xlib/dingflow-gen/impl_GenComponents.xpl` |
| `.xlib` | 复用库、可调用动作集合 | `nop-task/nop-task-core/src/main/resources/_vfs/nop/task/xlib/task.xlib` |
| `.xrun` | runner / CLI 任务入口 | `nop-runner/nop-cli-core/tasks/gen-web.xrun` |
| `.xgen` | 生成阶段模板 | `nop-wf/nop-wf-web/precompile/gen-page.xgen` |

## 最常见的基础语法

### 文本输出模板

```xml
<c:unit xpl:outputMode="text">
    ...
</c:unit>
```

这表示模板输出的是文本，而不是普通 XML 树。

### 循环

```xml
<c:for var="item" items="${items}">
    ...
</c:for>
```

### 条件

```xml
<c:if test="${condition}">
    ...
</c:if>
```

### 表达式

```xml
${model.name}
```

XLang 表达式支持 `===`/`!==` 严格相等运算符，语义与 `==`/`!=` 相同（均不进行类型转换），与 JavaScript/TypeScript 语法兼容。

### 原样输出 (c:print)

```xml
<c:print>view = ${view}</c:print>
```

`c:print` 将其内容（文本和子元素）原样输出——内容中的 `${...}` 表达式**保持字面量，不做求值替换**。这在需要将包含 `${...}` 的模板文本延迟到后续阶段再编译时非常有用。

输出模式控制内容的序列化方式：

| 输出模式 | 内容处理方式 |
|---------|-------------|
| `node` | 保持已解析的 XNode 子树结构，子节点和文本按字面量输出 |
| `text` | `node.contentText()`，纯文本。若包含 XML 子元素则回退至 `xml` 模式 |
| `xml` | `node.innerXml()`，原始 XML 文本 |
| `html` | `node.innerHtml()`，原始 HTML 文本 |

> 输出模式由当前编译作用域决定，不同加载上下文可能有不同的默认值（如 `XplModelParser` 默认 `html`）。

`c:print` 也可通过 `xpl:is` 动态绑定到任意元素上：

```xml
<source xpl:is="c:print">view = ${view}</source>
```

`xpl:is="c:print"` 效果与 `<c:print>` 相同，外层标签名和额外属性均被忽略。注意未命名空间的宿主元素需要 `xpl:allowUnknownTag="true"`。

### 动态标签 (xpl:is)

`xpl:is` 是任何 XPL 元素上可用的属性，用于**动态改写标签处理器**，类似于 Vue 的 `is` 属性。

```xml
<!-- 等价于 <c:if test="${cond}">...</c:if> -->
<div xpl:is="c:if" test="${cond}">...</div>
```

工作机制：
1. XPL 编译器先解析 `xpl:is` 属性值，得到目标标签名（如 `c:if`）
2. 按目标标签名查找对应的 `IXplTagCompiler`
3. 用目标标签编译器处理当前元素，**外层标签名和额外属性均被忽略**

当 `xpl:is` 值是固定字符串或标识符时在编译期确定；如果是复杂表达式则回退到 `unknownCompiler` 在运行时动态解析。

`xpl.xdef` 中 `xpl:is` 的类型定义为 `#xml-name`，因此支持标识符和字符串字面量，但不支持任意复杂表达式。

### 内联脚本 / XScript

```xml
<c:script>
    ...
</c:script>
```

在 `<source>` 或 `<c:script>` 中也可直接用 CDATA 包裹 XLang 脚本（JavaScript 兼容语法，支持 `===`/`!==`、`let`/`const`、箭头函数等）：

```xml
<source><![CDATA[
    if (order.status != 'SUBMITTED')
        throw new NopException("erp.purchase.order-not-submitted")
            .param("orderId", order.id);
]]></source>
```

### XScript 中嵌入 XPL 标签调用（`xpl\`...\`` 模板字面量）

在 XScript 脚本中通过 `` xpl`...` `` 标签模板语法调用 XPL 标签或编译 XPL 片段。这是编译期宏（`@Macro`），在 AST 构建阶段被执行并替换为编译后的表达式。

两种模式：

**模式一：调用 XPL 标签**

```javascript
// 调用 biz:Validator
xpl`biz:Validator`, {
    fatalSeverity: 100,
    obj: {entity: order}
};
```

反引号内第一个参数为标签名，后续参数传递给标签属性。

**模式二：编译 XPL 片段**

```javascript
// 编译内联 XPL
xpl`<c:if test="${x > 0}">positive</c:if>`
```

反引号内为 XPL XML 片段。

此外还有 `tpl\`...\``（模板表达式编译）、`sql\`...\``（SQL 片段编译）等类似宏。

> 转义：用双反引号（`` `` ``）表示字面反引号，非 TypeScript 的反斜杠。

```xml
<run:GenWithCache xpl:lib="/nop/codegen/xlib/run.xlib" .../>
```

## 实体属性访问 (Entity Property Access in XScript)

### `entity.id` 固定返回主键

`OrmEntity` 对 `id` 做了特殊识别，不经过 JavaBean 属性映射，直接返回主键值：

- 单列主键（如 `userId`）→ 返回值本身（`String`/`Long` 等）
- 复合主键 → 返回 `OrmCompositePk` 对象

不存在 `set_id()`，主键通过具体列 setter 设置（`entity.userId = 'xxx'`）。

### 普通列用点号，命名空间属性用括号

```javascript
entity.approveStatus = 'SUBMITTED';        // 动态扩展列
objMeta['wf:wfName'];                      // 含有冒号的命名空间属性
```

### XScript 推荐写法速查

| 场景 | 推荐 | 不推荐 |
|------|------|--------|
| 读写实体扩展列 | `entity.approveStatus` | `entity.prop_get('approveStatus')` |
| 读 objMeta 命名空间属性 | `objMeta['wf:wfName']` | `objMeta.prop_get('wf:wfName')` |
| 抛异常 | `throw new NopScriptError("code").param(...)` | `throw new Error(...)` |
| 构造 Map | `{}` | `new java.util.HashMap()` |
| 导入 Java 类 | `import full.ClassName;` | `Java.type('full.ClassName')` |
| 获取实体 ID | `entity.id` | `entity.orm_idString()` |

## xbiz action source 内置变量

xbiz 的 `<mutation>`/`<query>` 的 `<source>`（`<c:script>`）在执行时，求值作用域（`IEvalScope`）中注入了以下变量。这些变量不需要声明，可直接使用。

### 局部变量（作用域继承或参数注入）

| 变量 | 类型 | 说明 |
|------|------|------|
| `svcCtx` | `IServiceContext` | 服务请求上下文。承载 `IUserContext`（用户身份/角色/数据权限）、缓存与事务上下文。用 `svcCtx.getUserId()` 获取用户 ID，`svcCtx.getUserContext()` 获取完整用户上下文（含 `userName`/`deptId`/`roles` 等） |
| `thisObj` | `IBizObject` | 当前 BizObject 实例。用 `thisObj.invoke(actionName, args, request, svcCtx)` 调用其他 action（如 `thisObj.invoke("requireEntity", {id}, null, svcCtx)`） |
| `gqlCtx` | `IGraphQLExecutionContext` | GraphQL 执行上下文 |
| 各 `<arg>` 声明的参数名 | 声明类型 | action 参数按名称注入。如 `<arg name="id" type="String"/>` → source 中直接用 `id` |

### 全局变量（`$` 前缀，通过 `EvalGlobalRegistry` 注册）

全局变量以 `$` 开头，在任意 XScript 求值环境中可用（不限于 xbiz）：

| 变量 | 类型 | 说明 |
|------|------|------|
| `$context` | `IContext` | kernel 线程上下文（`ContextProvider.currentContext()`）。承载 `locale`/`tenantId`/`userId`/`traceId` 等 |
| `$scope` | `IEvalScope` | 当前求值作用域 |
| `$JSON` | `JsonTool`（静态方法） | JSON 序列化/反序列化工具 |
| `$Math` | `MathHelper`（静态方法） | 数学工具 |
| `$Date` | `DateHelper`（静态方法） | 日期工具 |
| `$String` | `StringHelper`（静态方法） | 字符串工具 |
| `_` | `Underscore`（静态方法） | underscore.js 风格集合/对象工具 |
| `$config` | `AppConfig`（静态方法） | 应用配置 |
| `$beans` | BeanProvider | IoC 容器 Bean 访问 |

### 全局函数（无 `$` 前缀，通过 `EvalGlobalRegistry.registerStaticFunctions` 注册）

全局函数不带 `$` 前缀，在任意 XScript 求值环境中可直接调用：

| 函数 | 返回类型 | 说明 |
|------|---------|------|
| `now()` | `java.sql.Timestamp` | 当前时间戳（委托 `CoreMetrics.currentTimestamp()`） |
| `today()` | `LocalDate` | 当前日期 |
| `currentDateTime()` | `LocalDateTime` | 当前日期时间 |
| `inject('beanName')` | Object | 获取 IoC 容器中的 Bean（含 `I*Biz` 接口） |
| `optional(expr)` | Object | 安全访问，null 时返回 null 不报错 |

> 完整列表见 `GlobalFunctions.java`（`nop-xlang/.../functions/GlobalFunctions.java`），包含 `now`/`today`/`currentDateTime`/`inject`/`optional`/`OR`/`AND`/`IF`/`SWITCH`/`get`/`getByPropPath` 等。

### 关键区别：`svcCtx` vs `$context`

| | `svcCtx` | `$context` |
|---|---|---|
| 类型 | `IServiceContext`（nop-core） | `IContext`（nop-api-core） |
| 层级 | 服务请求上下文 | kernel 线程上下文 |
| 承载 | `IUserContext`、缓存、事务、ORM Session | locale、tenantId、traceId、userId/userName |
| 获取用户 ID | `svcCtx.getUserId()` | `$context.getUserId()` |
| 获取机制 | 作用域继承（`ServiceContextImpl` 构造时注入 `CoreConstants.VAR_SVC_CTX`） | 全局变量（`EvalGlobalRegistry` 注册，`ContextProvider.currentContext()` 惰性解析） |

两者都能获取 `userId`，但 `svcCtx` 是首选——它额外提供 `getUserContext()`（含角色/部门）、缓存和事务上下文。`$context` 用于只需 locale/tenantId 的轻量场景。

### 获取当前用户与时间的正确写法

```javascript
// 用户 ID（两种等价方式，svcCtx 首选）
svcCtx.getUserId()
$context.getUserId()

// 完整用户上下文（含 userName/deptId/roles）
const userCtx = svcCtx.getUserContext();
userCtx.getUserName();
userCtx.getDeptId();
userCtx.isUserInRole("manager");

// 当前时间（全局函数 now()，委托 CoreMetrics.currentTimestamp()，返回 java.sql.Timestamp）
now()
today()              // LocalDate
currentDateTime()    // LocalDateTime

// 当前日期字符串（用 $Date 全局变量）
$Date.formatJavaDate(now(), 'yyyy-MM-dd');
```

> `now()`/`today()`/`currentDateTime()` 是 XLang 全局函数（`GlobalFunctions` 静态方法，经 `EvalGlobalRegistry.registerStaticFunctions` 注册），最终委托 `CoreMetrics`——测试时可通过 `CoreMetrics.registerClock()` 替换为 mock clock。

### 完整示例

以下展示 xbiz action source 中内置变量的典型用法：

```xml
<mutation name="approve" displayName="通过">
    <arg name="id" type="String" mandatory="true"/>
    <arg name="svcCtx" kind="ServiceContext"/>
    <source>
        <c:script><![CDATA[
            // thisObj: 调用 requireEntity 获取实体
            const entity = thisObj.invoke("requireEntity", {id}, null, svcCtx);

            // 状态守卫
            if (entity.approveStatus !== 'SUBMITTED') {
                throw new NopScriptError("nop.err.wf.approve.invalid-status")
                    .param("bizObjName", thisObj.bizObjName)
                    .param("currentStatus", entity.approveStatus);
            }

            // 状态迁移
            entity.approveStatus = 'APPROVED';

            // 回写审计字段（svcCtx 获取当前用户，now() 获取当前时间）
            entity.approvedBy = svcCtx.getUserId();
            entity.approvedAt = now();

            // inject() 获取 IoC Bean 做业务联动
            inject('biz_LeaveBalance').deduct(entity.userId, entity.days);

            return entity;
        ]]></c:script>
    </source>
</mutation>
```

## 仓库里的真实参考

1. `nop-wf/nop-wf-web/src/main/resources/_vfs/nop/wf/xlib/dingflow-gen/impl_GenComponents.xpl`
   这里可以看到 `c:unit`、`c:for`、`c:if`、`${...}`、`c:script`。
2. `nop-task/nop-task-core/src/main/resources/_vfs/nop/task/xlib/task.xlib`
   这里可以看到 `xpl:is` 这类动态标签用法。
3. `nop-runner/nop-cli-core/tasks/gen-web.xrun`
   这里可以看到 `xpl:lib` 调用 XLib 的入口模式。
4. `nop-kernel/nop-core/precompile/src/main/java/io/nop/core/type/PredefinedGenericTypes.java.xgen`
   这里可以看到文本输出型 `.xgen` 模板。

## schema 在哪里

常见 schema 位于：

`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/`

最常用的几个：

1. `xpl.xdef`
2. `xlib.xdef`
3. `xdsl.xdef`

## XPL 输出模式 (xpl:outputMode)

XPL 模板有多种输出模式，通过 `xpl:outputMode` 控制 `${expr}` 的输出行为：

| 输出模式 | 用途 | `${expr}` 行为 |
|---------|------|---------------|
| `xml` | 输出 XML 节点树 | 自动 XML 转义 |
| `text` | 输出纯文本 | 无转义 |
| `html` | 输出 HTML | 自动 HTML 转义 |
| `sql` | 输出 SQL (sql-lib) | **自动参数化** (转 `?` + JDBC 参数) |

> 默认输出模式由加载上下文决定（如 `XplModelParser` 默认 `html`），建议显式设置 `xpl:outputMode`。

**sql 模式特别说明**（用于 `.sql-lib.xml` 的 `<source>`，其类型为 `xpl-sql`，等价于 `xpl:outputMode="sql"`）：

- `${expr}` → 自动转为 JDBC `?` 参数，防 SQL 注入
- `${raw(expr)}` → 原样拼接 SQL 文本（跳参数化），用于动态表名/列名
- `${collection}` → 展开为多个 `?` 参数（IN 子句）
- 外部纯文本保持为 SQL 字面

> **与 MyBatis 的关键区别：** MyBatis 中 `${}` 是原样替换（有注入风险），XPL sql 模式中 `${}` 默认安全参数化，需要原样拼接时显式使用 `raw()`。两者默认行为相反。

sql-lib 的 XDEF 参见 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/sql-lib.xdef`。

## 标签定义 outputMode（xlib 层面）

与模板上的 `xpl:outputMode` 不同，xlib 中**标签定义**上的 `outputMode` 属性设置该标签 `<source>` body 的默认编译输出模式：

```xml
<lib xmlns:x="/nop/schema/xdsl.xdef" ...>
    <tags>
        <!-- outputMode 设置 GenPage 的 source body 默认编译模式 -->
        <GenPage outputMode="xjson">
            <attr name="view" mandatory="true" type="String"/>
            <source>
                <!-- 此 body 按 xjson 模式编译 -->
                <c:include src="web/impl_GenPage.xpl"/>
            </source>
        </GenPage>
    </tags>
</lib>
```

`outputMode` 和 `xpl:outputMode` 是两个不同层级的概念：

| 层级 | 属性 | 作用范围 | 作用 |
|------|------|---------|------|
| 标签库 xlib | `<tag outputMode="...">` | 该标签的 `<source>` body **编译期默认** | 设置 source body 的默认输出模式 |
| 模板 xpl | `<elem xpl:outputMode="...">` | 该元素及其子元素的 **运行时** 输出 | 控制 `${expr}` 的求值输出方式 |

> 标签定义的 `outputMode` 可以用 `xpl:outputMode` 在 source 内部临时覆盖。如果某个子模板有自己的 `xpl:outputMode`，以子模板的为准。

## 默认工作方式

1. 先判断文件是 `.xpl`、`.xlib`、`.xrun` 还是 `.xgen`。
2. 再看它是文本输出模板、可复用库，还是 runner 任务入口。
3. 只在需要时去查对应 schema 和实现锚点。
4. 如果任务只是追生成链路，不要把本页和 `model -> codegen -> meta -> web` 链路文档混为一谈。

## 不要默认做的事

1. 还没分清文件角色，就把所有 XLang 文件当成同一种 DSL。
2. 一上来就大范围读源码，而不是先看真实模板例子和 schema。
3. 把 `.xgen` 的生成链路说明写回本页；那属于代码生成 runbook 的主题。

## 相关文档

- `./xdef-and-xdsl.md`
- `./model-first-development.md`
- `../03-runbooks/debug-codegen-and-generated-files.md`
- `../04-reference/source-anchors.md`
