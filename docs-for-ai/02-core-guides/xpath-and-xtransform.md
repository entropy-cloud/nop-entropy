# XPath 选择器与 XT Transform 树转换

本页回答两个问题：

1. **XPath 子集**：Nop 平台内建的简化 XPath 选择器（`io.nop.xlang.xpath`）支持什么语法、怎么在 Java 里用。
2. **XT Transform**：类 XSLT 的声明式 XML 树转换 DSL（`io.nop.xlang.xt`，schema 为 `xt.xdef`）怎么写、怎么执行、错误码有哪些。

两者都位于 `nop-kernel/nop-xlang` 模块。XT 的 `xpath` 属性与 XPath 选择器共用同一套语法与实现。

---

## 一、XPath 选择器（`io.nop.xlang.xpath`）

### 1.1 定位与入口

- 这是平台**自有实现的 XPath 子集**，不是 W3C XPath。selector 解析与 operator 注册都在 `io.nop.xlang.xpath` 包内完成。
- Java 侧统一入口是 `XPathHelper.parseXSelector(String path)`，返回 `IXSelector<XNode>`，可直接用于 `XNode.selectOne/selectMany`：

```java
IXSelector<XNode> xpath = XPathHelper.parseXSelector("/root/child/@a");
String value = (String) node.selectOne(xpath);
```

- 仓库内真实使用方：`nop-ooxml` 等模块通过 `XPathHelper.parseXSelector` 选择 OOXML 节点；XT Transform 的 `xpath` 属性同样走该解析器。

### 1.2 语法

| 语法 | 含义 |
|------|------|
| `/root/child` | 绝对路径（从根开始逐层取直接子节点） |
| `child` / `.//child` | 相对路径（`.` 表示当前节点） |
| `//child` | 级联选择（descendant-or-self，任意深度的后代） |
| `..` | 父节点（用在路径中间段；对根节点使用会抛错，见错误码表） |
| `*` | 通配任意标签 |
| `#tag` | unique-match 标签选择 |
| `[expr]` | 谓词过滤，`expr` 是 XLang 表达式（支持 `@attr` 简写） |
| `[1]` | 按下标取子节点（从 0 开始） |
| `expr1\|expr2` | 联合选择（结果不去重；`\|` 前后不能有空格） |

谓词内是完整 XLang 表达式：`/root/child[@a=='1']`、`/root/item[@id != 'X']` 均可。

### 1.3 取值 operator（路径最后一段）

路径最后一段可以是取值 operator，从选中节点上提取值而不是返回节点本身：

| Operator | 返回 |
|----------|------|
| `$value` | 节点值（内容） |
| `$tag` | 标签名 |
| `$xml` | outer XML 字符串 |
| `$innerXml` | inner XML 字符串 |
| `$text` | 文本 |
| `$html` | outer HTML 字符串 |
| `$innerHtml` | inner HTML 字符串 |
| `$node` | 节点本身（恒等） |

共 8 个，与 `XLangConstants.XPATH_OPERATOR_*` 常量一一对应，全部注册在 `XPathOperatorRegistry`。未注册的 `$xxx` 在**解析期**抛 `ERR_XPATH_UNKNOWN_OPERATOR`（不会静默返回 null）。

### 1.4 表达式内置变量

谓词 / `:[expr]` 内嵌表达式中可用：

| 变量 | 含义 |
|------|------|
| `thisNode` | 当前正在匹配的节点（`@attr` 简写即读它） |
| `root` | 查询根节点 |

### 1.5 边界（当前不支持）

- 标准 XPath 函数库（`fn:substring`、`fn:contains` 等）与 ancestor/following/sibling 等轴不在当前实现内；`//` 与 `..` 覆盖最常用场景。
- 双向 setValue（`updateSelected`）未实现，当前仅支持正向查询。

---

## 二、XT Transform（`io.nop.xlang.xt`）

### 2.1 定位与入口

类 XSLT 的声明式树转换 DSL：把一棵 `XNode` 源树按规则转换为目标树。

- schema：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xt.xdef`（结构性定义以它为准）
- Java 入口：`XtTransform.load(path)`（VFS 路径）→ `transform(source)` / `transform(source, params)`
- 编译产物是 `IXTransformRule` 树 + templates / mappings cache，编译期完成 import 合并、inherits 合并与 ID 存在性检查

```java
XtTransform transform = XtTransform.load("/test/xt/my-transform.xt.xml");
XNode result = transform.transform(sourceNode, Map.of("title", "MyTitle"));
```

### 2.2 文档结构

```xml
<transform x:schema="/nop/schema/xt.xdef"
           xmlns:x="/nop/schema/xdsl.xdef"
           xmlns:xt="/nop/schema/xt.xdef">
    <import from="/test/xt/base.xt.xml" prefix="ext"/>
    <mapping id="content">
        <match tag="section">...</match>
        <default>...</default>
    </mapping>
    <template id="header">...</template>
    <main>
        <!-- 主入口规则 -->
    </main>
</transform>
```

| 元素 | 说明 |
|------|------|
| `<import from="..." prefix="..."/>` | 导入其他 xt 文件的 templates / mappings，登记为 `prefix:原id`（prefix 为空时用原 id）；同 id 冲突抛 `ERR_XT_IMPORT_CONFLICT`，循环 import 抛 `ERR_XT_CIRCULAR_REFERENCE` |
| `<mapping id="..." inherits="父id列表">` | 按源节点标签名分发规则；`<match tag="...">` 命中特定标签，`<default>` 兜底；`inherits` 合并父 mapping（子同名 match 覆盖父，子无 default 继承父，多重继承 csv 后者覆盖前者） |
| `<template id="...">` | 可复用规则组，通过 `<xt:apply-template id="..."/>` 调用 |
| `<main>` | 转换入口规则 |

编译期会校验所有 `apply-template` / `apply-mapping` 引用的 id 存在（已合并 imports 之后），缺失在 `new XtTransform(model)` 时即抛错并附 source location。

### 2.3 规则指令（13 个实现类）

所有 `xt:*` 指令支持通用属性：`xpath`（选择目标节点，缺省用当前节点）与 `mandatory`（true 时 xpath 找不到节点抛 `ERR_XT_MANDATORY_NODE_NOT_FOUND`）。

| 指令 | 实现类 | 说明 |
|------|--------|------|
| `<xt:apply-template id="...">` | `ApplyTemplateRule` | 应用指定 template；循环调用抛 `ERR_XT_CIRCULAR_REFERENCE` |
| `<xt:apply-mapping id="...">` | `ApplyMappingRule` | 按选中节点标签名在 mapping 中分发（match → default）；mapping 不存在抛 `ERR_XT_MAPPING_NOT_FOUND` |
| `<xt:copy-node xpath="..."/>` | `CopyNodeRule` | 整节点（含属性与子节点）复制到输出 |
| `<xt:copy-body xpath="..."/>` | `CopyBodyRule` | 复制选中节点的子节点到当前输出位置 |
| `<xt:value>` | `ValueRule` | body 为 `xt-value` 表达式，求值结果作为当前输出节点的值 |
| （内部）`ValueOutputRule` | `ValueOutputRule` | 处理规则组自身的 `value` 属性（如 `<div>x_${@a}</div>` 的文本体），不直接出现在 DSL |
| `<xt:gen xpath="...">` | `GenRule` | body 为 `xpl-node`（XPL node 输出），生成结果作为输出节点；多根输出自动展开 |
| `<xt:script xpath="...">` | `ScriptRule` | body 为 `xpl` 脚本，scope 内可用 `output`（`IXtTransformOutput`）直接构建输出 |
| `<xt:each xpath="...">` | `EachRule` | 对 xpath 选中的每个节点应用 body；每轮迭代 `node`/`thisNode` 切换为当前迭代节点，循环结束恢复 |
| `<xt:choose>` + `<when test="...">` + `<otherwise>` | `ChooseRule` | 多分支条件，第一个命中的 `when` 生效，否则走 `otherwise` |
| `<xt:if test="...">` | `IfRule` | 单条件分支 |
| 自定义标签（任意非 `xt:` 标签） | `CustomTagRule` | 直接输出同名节点；`xt:xpath` 选源节点，`xt:attrs`（expr，返回 Map）批量设属性，其他属性值是 `xt-value` 表达式 |
| （内部）`CompositeRule` | `CompositeRule` | 顺序组合多条规则，不直接出现在 DSL |

### 2.4 xt 表达式（`xt-expr` / `xt-value`）

`test="!xt-expr"` 属性与 `xt-value` 类型属性使用 `XtExprParser` 解析：

- **`@attrName`**：当前节点属性简写，等价 `node.getAttr('attrName')`。
- **内置变量**：`${node}` / `${thisNode}`（当前节点）、`${root}`（源根）、`${output}`（输出构建器）、`${$params.xxx}`（转换参数）、`${$context}`（转换上下文）。`@` 与 `$` 简写仅在这些 xt 表达式语境中生效；XPL 脚本体（`xt:script`/`xt:gen` 的 body）内用不带 `$` 的 `output` 等普通变量名。
- **历史语法**：旧写法 `%{expr}`（如 `%{node.getAttr('id')}`）仍然兼容，新代码统一用 `${...}` + `@` 简写。
- `transform(source, params)` 传入的参数同时以 `${参数名}` 直接暴露（例如 params 里的 `items` 可写 `items="${items}"`）；与内置变量同名时内置变量优先。

### 2.5 执行模型

1. `new XtTransform(model)` / `XtTransform.load(path)`：编译 main / templates / mappings，处理 import 与 inherits 合并，做编译期 ID 检查。
2. `transform(source, params)`：构造 `XtTransformContext`（注入 `node/thisNode/root/output/params/context` 六个内置变量与各参数项），从 main 规则开始递归执行。
3. 输出经 `IXtTransformOutput`（节点栈：`pushNode`/`popNode`）逐步构建；单根结果直接返回该根节点。
4. `apply-template` / `apply-mapping` 执行期有 visited 集合做循环引用检测（所有 child context 共享同一份状态）。

---

## 三、错误码清单

XPath（定义在 `XLangErrors.java`）：

| 错误码 | code | 触发点 |
|--------|------|--------|
| `ERR_XPATH_UNKNOWN_OPERATOR` | `nop.err.xlang.xpath.unknown-operator` | 路径中使用了未注册的 `$xxx` operator（解析期） |
| `ERR_XPATH_ROOT_NOT_ALLOW_PARENT_SELECTOR` | `nop.err.xlang.xpath.root-not-allow-parent-selector` | 对根节点使用 `..`（选择期） |

XT：

| 错误码 | code | 触发点 |
|--------|------|--------|
| `ERR_XT_TEMPLATE_NOT_FOUND` | `nop.err.xt.template-not-found` | `apply-template` 引用不存在的 template（编译期）/运行期兜底 |
| `ERR_XT_MAPPING_NOT_FOUND` | `nop.err.xt.mapping-not-found` | `apply-mapping` 引用不存在的 mapping（编译期）；`inherits` 引用不存在的父 mapping；运行期 `getCompiledRuleForTag` 兜底 |
| `ERR_XT_MANDATORY_NODE_NOT_FOUND` | `nop.err.xt.mandatory-node-not-found` | `mandatory=true` 且 xpath 未选中节点（运行期） |
| `ERR_XT_XPATH_ERROR` | `nop.err.xt.xpath-error` | 规则执行期 xpath 求值抛异常（包装cause） |
| `ERR_XT_RULE_COMPILE_ERROR` | `nop.err.xt.rule-compile-error` | 规则编译异常（包装 cause，附 ruleType） |
| `ERR_XT_CIRCULAR_REFERENCE` | `nop.err.xt.circular-reference` | template/mapping 循环引用（运行期）；import / inherits 循环（编译期） |
| `ERR_XT_IMPORT_CONFLICT` | `nop.err.xt.import-conflict` | import 与本文件直接定义产生同 id 冲突（编译期） |

所有失败路径均 fail-fast 抛 `NopException`，不存在静默返回 null 的分支（mapping 存在但 tag 未命中且无 default 属于合法的"无规则可应用"，输出为空）。

---

## 四、与 XSLT 的差异

| 特性 | XSLT | XT Transform |
|------|------|--------------|
| 节点选择 | XPath 1.0 | 平台自有 XPath 子集（`IXSelector`） |
| 模板匹配 | `<xsl:template match="...">` | `<mapping>` + `<match tag="...">` 按标签名分发 |
| 复用机制 | `xsl:call-template` | `<template>` + `<xt:apply-template>`，支持 `<import>` 前缀隔离与 `inherits` 继承 |
| 循环 | `<xsl:for-each>` | `<xt:each>` |
| 条件 | `<xsl:if>`, `<xsl:choose>` | `<xt:if>`, `<xt:choose>` |
| 值输出 | `<xsl:value-of>` | `<xt:value>` |
| 代码执行 | `<xsl:script>` (有限) | `<xt:gen>`, `<xt:script>`（完整 XPL/XScript） |
| 自定义标签 | `<xsl:element>` | 直接写目标标签名（`CustomTagRule`） |
| 表达式 | XPath 表达式 | XLang 表达式 + `@attr` 简写 + `$params` 等内置变量 |
| 检查时机 | — | 编译期做 import 合并 / ID 存在性检查，运行期做 mandatory 与循环引用检查 |

---

## 相关文档

- `./xlang-and-xpl-basics.md`（XPL / XScript 基础，`xt:script`/`xt:gen` 的 body 语法）
- `./xdef-and-xdsl.md`（XDef / XDSL 机制，`xt.xdef` 也是一套 XDSL）
- `../04-reference/source-anchors.md`（`XPT-001`/`XPT-002`/`XPT-003` 实现锚点）
