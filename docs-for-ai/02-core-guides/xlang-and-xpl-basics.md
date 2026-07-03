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
| `xml` (默认) | 输出 XML 节点树 | 自动 XML 转义 |
| `text` | 输出纯文本 | 无转义 |
| `html` | 输出 HTML | 自动 HTML 转义 |
| `sql` | 输出 SQL (sql-lib) | **自动参数化** (转 `?` + JDBC 参数) |

**sql 模式特别说明**（用于 `.sql-lib.xml` 的 `<source>`，其类型为 `xpl-sql`，等价于 `xpl:outputMode="sql"`）：

- `${expr}` → 自动转为 JDBC `?` 参数，防 SQL 注入
- `${raw(expr)}` → 原样拼接 SQL 文本（跳参数化），用于动态表名/列名
- `${collection}` → 展开为多个 `?` 参数（IN 子句）
- 外部纯文本保持为 SQL 字面

> **与 MyBatis 的关键区别：** MyBatis 中 `${}` 是原样替换（有注入风险），XPL sql 模式中 `${}` 默认安全参数化，需要原样拼接时显式使用 `raw()`。两者默认行为相反。

sql-lib 的 XDEF 参见 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/sql-lib.xdef`。

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
