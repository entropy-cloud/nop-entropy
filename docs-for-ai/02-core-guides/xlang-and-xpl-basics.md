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

### XScript 的 try/catch/finally（支持，JS 语义）

XScript 支持 `try {} catch(e) {} finally {}`（JS 兼容语法，plan 2258 落地）。语义：

- `catch(e)` 把异常对象绑定到变量 `e`；**catch 体正常执行完成后吞掉异常，继续执行后续代码**（JS 语义）；需要重抛时在 catch 体内显式 `throw e`；
- `finally` 块可选，**无论是否抛异常都执行**；
- `try {} catch(e) {}`（无 finally）合法。

```xml
<source><![CDATA[
    try {
        doStep1();
        doStep2();
    } catch (e) {
        // 失败隔离：记录并继续，主流程不中断
        $log.warn("step failed: {}", e);
    } finally {
        cleanup();
    }
]]></source>
```

**与 Java Bean 的分工（架构偏好，非能力限制）**：try/catch 在 XScript 可表达，但**跨实体事务组合、复杂多路编排 + 各自失败隔离、幂等防御**这类重逻辑仍建议下沉 Java Bean（Guard/Processor 范式），xbiz 只做薄委托——原因是事务边界、类型安全和可测试性，不是"写不出 try/catch"。

### 删除属性（`delete`）

XScript 中可使用 JavaScript 风格的 `delete` 一元表达式（plan 2259 落地）：

```js
let m = {a: 1, b: 2};
delete m.a;          // true —— Map 与 List 删除条目
let r = delete m.a;  // r = true（存在并已删除）；// false（不存在）

delete obj.prop;        // Bean 属性：set null（清空值）
delete obj["key"];      // Bean 按 key 删除
delete list[2];         // List 按索引删除
delete list["tom"];     // List 按对象值删除
delete $scope.tmp;      // scope 变量删除（仅当前帧）
delete $scope["k1"];    // 同上 computed 形式
```

返回 `boolean`：true 表示存在并被删除；false 表示不存在。

**支持的删除对象**：

| 类型 | 行为 |
|------|------|
| `Map<K,V>` | `map.remove(key)` 删除条目 |
| `List<T>` | attr 是 `Integer` 走 `list.remove(int)` 按索引；否则 `list.remove(object)` 按值 |
| `Bean`（含 `IPropGetMissingHook`/`IPropSetMissingHook`） | 普通 setter 设 null；扩展属性通过 `prop_remove(name)` **真删除条目**（`DynamicObject` / `SerializableExtensibleObject` 等已实现）；普通 setter 设 null 后值清空但 key 保留 |
| `IEvalScope` | `scope.removeLocalValue(name)`（仅当前帧） |
| 数组 | 抛 `nop.err.xlang.exec.delete-on-array` |

**编译期错误**（不抛 `XLangException`，编译期即拒绝）：

| 错误码 | 触发条件 |
|--------|----------|
| `nop.err.xlang.delete.not-member-expr` | `delete x`（裸标识符）；只接受 `obj.prop` / `obj["key"]` |
| `nop.err.xlang.delete.not-single-level` | `delete a.b.c`（链式）；只接受单级 |
| `nop.err.xlang.delete.on-class-ref` | `delete MyClass.FIELD`（静态字段）；Java 反射不支持 |

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

## 执行后端选择与降级观测（Execution Backend Selection）

XLang 支持三执行后端：解释器（默认，`nop-xlang`）、java 生成类后端（`nop-xlang-java`）、
truffle 翻译后端（`nop-xlang-truffle`）。后端选择由统一决策树裁决，入口在
`io.nop.xlang.api.XLang#execute`——所有"运行时字符串→Executable 树"编译出口
（`XLangCompileTool.compileSimpleExpr/compileFullExpr/compileTemplateExpr/compileTag/compileXpl` 等）
的求值都经该入口流入。后端不在 classpath（未注册）时行为与纯解释器完全一致。

### 后端注册 SPI（显式注册表，无 classpath 扫描）

- 契约与注册表在 `nop-xlang` 包 `io.nop.xlang.backend`：`IEvalExecutionBackend`
  （标识/能力集/可用性/不可用原因）+ `IEvalStaticBackend`（扫描清单成员资格 + 生成类绑定查找）
  + `IEvalDynamicBackend`（动态树翻译执行）+ `EvalBackendRegistry`（`@GlobalInstance` 单例）。
- 后端模块经 `ICoreInitializer` + `META-INF/services` 显式注册（先例：
  `XLangCoreInitializer` 内 `JaninoScriptCompiler.register()`）。
- 初始化失败 → 不可用条目（保留原因，不阻断启动）；诊断查询：
  `EvalBackendRegistry.instance().getUnavailableBackends()`（backendId → 原因）。

### 决策树（单跳降级，无跨跳）

```
force-interpreter 开启          → INTERPRETER（静默诊断模式，不记降级事件）
resourcePath ∈ 构建期扫描清单    → java 启用 + 可用 + 绑定命中 → JAVA（生成类执行体）
                                  否则 → INTERPRETER + 降级观测（WARN + 指标）
清单外资源（动态路径）           → truffle 启用 + 可用 + 非 native 部署 → TRUFFLE（池运行时）
                                  否则 → INTERPRETER（未注册/native 为静默；开关关/不可用记观测）
单元级翻译失败（第三分支）       → 该单元降级 INTERPRETER + 降级观测
```

### 生成类加载与模型加载期绑定（java 后端）

xpl 族编译单元（xpl/xgen/xrun）经 `XLang.parseXpl` 装载完成后按绑定决策树裁定执行体
（设计 java 组架构 §五）：

- 扫描清单成员 + 生成类清单条目在 + **Executable 树指纹一致**（hex SHA-256，构建期固化指纹
  vs 运行时树指纹——防 stale；施加对象是树而非源资源，Delta 变更不漏检）→ 绑定生成类执行体
  （`Class.forName` classpath 常规加载，无自定义 ClassLoader），绑定结果随 RCM 模型缓存条目
  复用（资源变更驱动的重载自然重绑定）；
- 已绑定执行体经 `XLang.execute` **直通**（不重复指纹计算、不产生降级观测）；
- 降级（条目缺失/指纹失配/类缺失）→ 解释器兜底 + 分级观测，执行期直通解释器（观测只在绑定期）；
- 清单外资源不做 java 绑定，走动态路径（不记降级事件）。

内存契约与供给缝：`JavaEvalExecutionBackend.setGeneratedClassManifest`（生成类清单：
resourcePath → 生成类名 + 树指纹；`GeneratedClassBindingBinder` 生产 binder 消费）+
`setStaticScanList`（扫描清单 should-set）。缺省空态 = 行为与纯解释器一致。classpath
双清单**文件**产物的自动装载与构建任务接入见下节"构建集成与新模块接入"。

**xlib 多标签单元（每标签条目）**：清单键 = `xlib路径 + '#' + 标签名`（如
`/test/my.xlib#Sum`）；标签体在惰性编译完成点按同一绑定决策树裁定——命中则标签函数
body 替换为生成类绑定执行体（随标签编译缓存复用），指纹 = 标签函数树指纹（签名 +
缺省实参 + 函数体，防参数变更 stale 漏检）。xlib **裸路径**永不在扫描清单内（标签体
经动态出口编译时按非成员走动态路径，无误降级）。宏标签不参与（编译期执行）；强制
node 输出模式变体（xml/html 标签用于 node 上下文）暂不生成（`@node` 变体键首落
不入清单，该变体静默走解释器）。

### 构建集成与新模块接入（java 生成类后端）

构建期管线（I11 落地）：`nop-xlang-java` 提供构建任务 `io.nop.xlang.java.gen.task.XlangJavaGenTask`
（独立 main 入口，`main <projectBasedir> [--check]`），模块经 exec-maven-plugin 增量
execution（`generate-test-resources` = postcompile 同相位）接入——参考实现 =
`nop-kernel/nop-xlang-java-e2e` fixture 模块（全真链路样例）。

- **扫描口径**：本模块 `src/main/resources/_vfs` 内 `*.xpl`（单根单元，RCM 装载语义 =
  html 输出模式）+ `*.xlib`（每标签一条目，键 `path#tag`）；`xgen`/`xrun` 排除（live
  仅构建期消费）、`xtask` 排除（仅测试资源人口）、其余 XDSL 排除（非独立编译单元）、
  `_delta/` 子树排除（产物只从基树生成）。排除类型在任务日志显式清点（不静默）。
- **任务行为**：经与运行时相同的编译前端取树（xpl = `XplModelParser` 干净编译；xlib =
  RCM 装载 + 标签惰性编译真实触发）→ 树指纹（与运行时校验同一实现）→ 转译 → 落盘。
  重生成幂等（write-if-changed，逐字节等价）；`--check` 模式只比对不写盘、漂移即非零
  退出（CI stale 哨兵）；同形路径折叠（如 `a-b.xpl` 与 `a_b.xpl` 派生同名类）任务侧
  fail-fast；转译失败不产出半成品（原子性）。
- **产物（全部落盘提交，`Gen_` 前缀 + 文件头生成标记，重生成幂等机械执行不可手改）**：
  - `_gen/` Java 源码：`src/main/java/io/nop/xlang/gen/Gen_<派生名>.java`（包
    `io.nop.xlang.gen`；类名从 resourcePath / `path#tag` 确定性派生）。当轮生成的源码
    不经本轮 main compile（任务在 compile 之后运行），落盘提交下轮编译——改了 `_vfs`
    源必须重跑任务并提交，否则运行时指纹失配降级（stale 检测哨兵即此语义）。
  - 双清单（分离产物）：`src/main/resources/META-INF/nop-xlang/xlang-java-static-scan.txt`
    （扫描清单 should-set，每行一键）+ `xlang-java-generated-classes.txt`（生成类清单，
    每行 `键\t类名FQN\t64位hex指纹`）。运行时经 `ClassLoader.getResources` 多 jar 聚合
    装载（同键异条目/同类名异键 fail-fast）。
  - native reflect 配置：`src/main/resources/META-INF/native-image/<groupId>/<artifactId>/reflect-config.json`
    （生成类 `allPublicMethods` 条目——`Class.forName`+`getDeclaredMethods`+`invoke`
    的最小充分集）。
- **运行时供给闭环**：`XLangJavaBackendInitializer`（ICoreInitializer + services）注册
  后自动装载 classpath 双清单填充供给缝——清单在场即激活（模块接入构建任务即生效）。
- **限制（rollout 前置条件）**：xpl 单元内含 xlib 标签调用（元素形态 `<ns:Tag/>` 或
  `xpl('ns:Tag', args)` 函数形态）当前不可转译（编译为 `ExecutableFunction` 内联调用
  节点，不在转译器支持集内）——含标签调用的单元勿纳入扫描（任务 fail-fast）。

### 漏跑诊断与部署（java 后端）

- **判别子**：classpath 信号无法区分"从未接入构建任务"与"接入后管线漏跑"——接入方
  （部署/应用）设置 `nop.xlang.execution.java-backend.require-manifest=true` 显式声明
  "本部署应有产物"。缺省 `false` = 未接入的合法空态（静默）。
- **漏跑形态**：require-manifest=true 且 classpath 无清单文件 → java 后端注册不可用条目
  （原因 `codegen-pipeline-missed`，经 `EvalBackendRegistry.instance()
  .getUnavailableBackends()` 可查）+ 全局 WARN + 指标计数；全部资源走动态路径/解释器。
- **native image**：生成类作为普通类直编进镜像（closed-world：无运行期编译/无自定义
  ClassLoader）；truffle 模块镜像排除 = 应用侧 Maven profile/exclusion 配方（结构性）
  + `deployment-form=native-image` 标记（行为性 belt-and-suspenders——若依赖仍在
  classpath，动态分支静默走解释器）。GraalVM 环境下的真实镜像构建先例：
  `nop-kernel-cli`（`-Pnative`）与 `nop-demo/nop-quarkus-demo`（`-Pnative`）；
  trace 模式（`-Dnop.codegen.trace.enabled=true`）运行构建任务时
  `GraalvmConfigGenerator` 管线（vfs-index/reflect delta）随任务真实执行。
- **性能基准复跑**：三后端（解释器/java/truffle）执行基准 = `nop-benchmark/nop-benchmark-xlang`
  （JMH main 入口，含静态三向对比/动态对比/Context 池梯度/翻译缓存容量敏感性）；
  复跑命令与数据报告指针见模块 README（truffle JIT 生效形态需 GraalVM JDK，
  报告含环境裁定与复跑口径）。

### 配置开关（`nop.xlang.execution.*`，`XLangConfigs`）

| 配置项 | 缺省 | 语义 |
|---|---|---|
| `nop.xlang.execution.java-backend-enabled` | `true` | java 后端启用（仅对已注册后端生效） |
| `nop.xlang.execution.truffle-backend-enabled` | `true` | truffle 后端启用（仅对已注册后端生效） |
| `nop.xlang.execution.force-interpreter` | `false` | 强制解释器诊断模式：全路由短路 + 不记降级事件 |
| `nop.xlang.execution.deployment-form` | `auto` | 部署形态标记：`auto`（探测系统属性 `org.graalvm.nativeimage.kind`）/ `jvm` / `native-image`；native 下 truffle 结构性不适用 |
| `nop.xlang.execution.java-backend.require-manifest` | `false` | 声明本部署应存在 java 后端生成产物（构建任务已接入）；true 且清单缺席 = 漏跑缺陷（不可用条目 + 全局 WARN） |

不存在"全局默认后端"配置项（默认语义 = auto 按判据裁决）。

### 降级观测命名契约

- **WARN 日志**：logger `io.nop.xlang.backend.EvalBackendObservation`，消息键
  `nop.xlang.execution.backend-degraded`，格式 `backend={}, reason={}, sourceKey={}`。
- **指标**：counter `nop.xlang.execution.backend-degradation`，tags `backend`
  （`java`/`truffle`）与 `reason`（`config-disabled` / `unavailable` /
  `unit-translation-failure` / `generated-binding-missing` / `generated-fingerprint-mismatch` /
  `tenant-divergent-tree` / `codegen-pipeline-missed` / `backend-unavailable`——
  `backend-unavailable` 仅在裁决后执行期后端失活的窄竞态路径出现；
  `codegen-pipeline-missed` 在初始化装载时全局一次（漏跑缺陷，见"漏跑诊断与部署"））。
- **分级语义（java 绑定降级）**：
  - stale 缺陷族（每次 WARN，缺陷应响亮）：`generated-binding-missing`（清单条目缺失或
    清单内类缺失/入口约定违规）、`generated-fingerprint-mismatch`（树指纹失配且无租户上下文
    ——模型变更后 `_gen/` 未重生成的哨兵）；
  - 预期稳态（非缺陷）：`tenant-divergent-tree`（树指纹失配且绑定时租户上下文活跃——租户
    delta 合并树结构性无生成类，解释器兜底为预期行为）。WARN 按 sourceKey 去重
    （once-per-path，有界去重集 ≤1024），**指标计数不衰减**。
- 查询计数（诊断/测试）：
  `EvalBackendObservation.degradationCount(backendId, reason)`。
- 静默不记事件的边界：force-interpreter 诊断模式、native 部署形态结构性排除、
  后端未注册（classpath 缺席）、清单外资源动态路径。

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
