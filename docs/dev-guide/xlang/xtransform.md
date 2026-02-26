# XT Transform 设计文档

## 1. 概述

XT Transform 是 Nop 平台中类似 XSLT 的树转换规则定义语言，用于将 XML 树结构转换为另一种结构。它基于可逆计算原理设计，支持声明式的转换规则定义。

### 1.1 设计目标

- **类似 XSLT 的声明式转换**：使用规则而非代码来定义转换逻辑
- **XPath 节点选择**：使用 XPath 表达式选择源节点
- **模板和映射机制**：支持可重用的模板定义和基于标签名的映射
- **XPL 集成**：支持在转换规则中嵌入 XPL 代码实现复杂逻辑
- **可扩展性**：支持自定义标签输出和规则扩展

### 1.2 与 XSLT 的对比

| 特性 | XSLT | XT Transform |
|------|------|--------------|
| 节点选择 | XPath 1.0 | XPath (IXSelector) |
| 模板匹配 | `<xsl:template match="...">` | `<mapping>` + `<match tag="...">` |
| 循环 | `<xsl:for-each>` | `<xt:each>` |
| 条件 | `<xsl:if>`, `<xsl:choose>` | `<xt:if>`, `<xt:choose>` |
| 值输出 | `<xsl:value-of>` | `<xt:value>` |
| 代码执行 | `<xsl:script>` (有限) | `<xt:gen>`, `<xt:script>` (XPL) |
| 自定义标签 | `<xsl:element>` | 直接使用自定义标签名 |
| 输出模式 | 单一输出 | 支持多种输出模式 |

---

## 2. 语法规范

### 2.1 文档结构

```xml
<?xml version="1.0" encoding="UTF-8"?>
<transform x:schema="/nop/schema/xt.xdef"
           xmlns:x="/nop/schema/xdsl.xdef"
           xmlns:xt="/nop/schema/xt.xdef">
    
    <!-- 导入其他转换规则 -->
    <import from="/path/to/other.xt.xml" prefix="my"/>
    
    <!-- 定义映射规则 -->
    <mapping id="default">
        <match tag="div">...</match>
        <match tag="span">...</match>
        <default>...</default>
    </mapping>
    
    <!-- 定义可重用模板 -->
    <template id="header">...</template>
    <template id="footer">...</template>
    
    <!-- 主入口规则 -->
    <main>
        ...
    </main>
</transform>
```

### 2.2 根元素 `<transform>`

根元素定义了整个转换规则的容器。

| 属性 | 类型 | 说明 |
|------|------|------|
| `x:schema` | 路径 | XDef 模式定义路径，固定为 `/nop/schema/xt.xdef` |

**子元素**：

| 元素 | 基数 | 说明 |
|------|------|------|
| `<import>` | 0..* | 导入其他 xt 转换规则定义 |
| `<mapping>` | 0..* | 按标签名映射到不同的规则 |
| `<template>` | 0..* | 定义可重用的模板 |
| `<main>` | 0..1 | 主入口规则 |

### 2.3 `<import>` - 导入规则

```xml
<import from="/path/to/rules.xt.xml" prefix="ext"/>
```

| 属性 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `from` | `v-path` | 是 | 导入文件的虚拟文件系统路径 |
| `prefix` | `string` | 否 | 为导入的 mapping/template ID 增加前缀，避免命名冲突 |

导入后，可以通过 `prefix:templateId` 或 `prefix:mappingId` 引用导入的规则。

### 2.4 `<mapping>` - 标签映射

Mapping 定义了如何根据输入节点的标签名选择对应的转换规则。

```xml
<mapping id="myMapping" inherits="baseMapping">
    <match tag="div">
        <!-- 匹配 div 标签的规则 -->
    </match>
    <match tag="span">
        <!-- 匹配 span 标签的规则 -->
    </match>
    <default>
        <!-- 默认规则 -->
    </default>
</mapping>
```

| 属性 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `var-name` | 是 | 映射规则的唯一标识 |
| `inherits` | `csv-set` | 否 | 继承其他 mapping 规则，允许覆盖 |

**子元素**：

| 元素 | 基数 | 说明 |
|------|------|------|
| `<match>` | 0..* | 匹配特定标签的规则 |
| `<default>` | 0..1 | 默认规则（当没有匹配的 match 时使用） |

### 2.5 `<template>` - 模板定义

```xml
<template id="myTemplate">
    <!-- 模板规则 -->
</template>
```

| 属性 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `var-name` | 是 | 模板的唯一标识 |

模板内容为 `XtRuleGroupModel`，即一组规则。

### 2.6 `<main>` - 主入口规则

```xml
<main>
    <!-- 主转换规则 -->
</main>
```

`main` 是转换的入口点，转换执行时首先应用 main 规则。

---

## 3. 规则指令

所有规则指令都支持 `xpath` 属性来选择目标节点。

### 3.1 通用属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `xpath` | `xpath` | - | XPath 表达式，选择目标节点 |
| `mandatory` | `boolean` | `false` | 如果为 true，当 xpath 找不到节点时抛出异常 |

### 3.2 `<xt:apply-template>` - 应用模板

```xml
<xt:apply-template id="header" xpath="/root/header" mandatory="true">
    <!-- 可选的子规则 -->
</xt:apply-template>
```

| 属性 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `string` | 是 | 要应用的模板 ID |
| `xpath` | `xpath` | 否 | 选择目标节点，不指定则使用当前节点 |
| `mandatory` | `boolean` | 否 | 是否强制要求找到节点 |

### 3.3 `<xt:apply-mapping>` - 应用映射

```xml
<xt:apply-mapping id="default" xpath="/root/content"/>
```

| 属性 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `string` | 是 | 要应用的映射 ID |
| `xpath` | `xpath` | 否 | 选择目标节点 |
| `mandatory` | `boolean` | 否 | 是否强制要求找到节点 |

根据目标节点的标签名，在指定的 mapping 中查找匹配的规则。

### 3.4 `<xt:copy-node>` - 复制节点

```xml
<xt:copy-node xpath="/root/source"/>
```

完整复制选中的节点（包括标签名、属性和子节点）到输出。

### 3.5 `<xt:copy-body>` - 复制内容

```xml
<xt:copy-body xpath="/root/source"/>
```

复制选中节点的子节点和文本内容到当前输出位置（不包含节点本身的标签和属性）。

### 3.6 `<xt:value>` - 输出值

```xml
<xt:value mandatory="true">
    ${node.text()}
</xt:value>
```

| 属性 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `mandatory` | `boolean` | 否 | 如果为 true，值不能为空 |

内容为 `xt-value` 表达式，计算结果作为当前节点的文本值输出。

### 3.7 `<xt:gen>` - 代码生成

```xml
<xt:gen xpath="/root/data">
    <c:for var="item" items="${$items}">
        <item name="${item.name}">${item.value}</item>
    </c:for>
</xt:gen>
```

| 属性 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `xpath` | `xpath` | 否 | 选择目标节点 |

内容为 `xpl-node`，执行 XPL 代码生成输出节点。XPL 的输出直接作为转换结果。

### 3.8 `<xt:script>` - 脚本执行

```xml
<xt:script xpath="/root/data">
    // XPL 脚本
    let value = $node.attr('name');
    $output.addNode(XNode.make('result', null, value));
</xt:script>
```

| 属性 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `xpath` | `xpath` | 否 | 选择目标节点 |

内容为 `xpl` 脚本，用于执行复杂的转换逻辑。

### 3.9 `<xt:each>` - 循环遍历

```xml
<xt:each xpath="/root/items/item">
    <item copy="true"/>
</xt:each>
```

| 属性 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `xpath` | `xpath` | 是 | 选择一组节点 |

对 xpath 选中的每个节点应用 body 中的规则。

### 3.10 `<xt:choose>` - 条件选择

```xml
<xt:choose>
    <when test="${@type = 'A'}">
        <typeA/>
    </when>
    <when test="${@type = 'B'}">
        <typeB/>
    </when>
    <otherwise>
        <unknown/>
    </otherwise>
</xt:choose>
```

**子元素**：

| 元素 | 基数 | 说明 |
|------|------|------|
| `<when>` | 1..* | 条件分支，`test` 属性为 `xt-expr` 表达式 |
| `<otherwise>` | 0..1 | 默认分支 |

### 3.11 `<xt:if>` - 条件判断

```xml
<xt:if test="${@enabled = 'true'}">
    <enabled/>
</xt:if>
```

| 属性 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `test` | `xt-expr` | 是 | 条件表达式 |

### 3.12 自定义标签输出

```xml
<output xmlns:xt="/nop/schema/xt.xdef" 
        xt:xpath="/root/source" 
        attr1="${@name}"
        attr2="${value}">
    <!-- 子规则 -->
</output>
```

自定义标签直接输出为对应的输出节点：

- 标签名：直接使用定义的标签名（如 `output`）
- `xt:xpath`：选择源节点
- `xt:attrs`：批量设置属性的表达式（返回 Map）
- 其他属性：支持表达式 `${...}`

---

## 4. 表达式语法

### 4.1 xt-expr 表达式

XT 表达式基于 XLang 表达式语言，支持以下特性：

```javascript
// 属性访问
@attrName          // 当前节点的属性
$node.attr('name') // 显式访问

// 变量引用
$node              // 当前节点
$root              // 根节点
$context           // 转换上下文
$output            // 输出构建器

// 条件表达式
@type = 'A' ? 'TypeA' : 'TypeB'

// 函数调用
string-length(@name)
```

### 4.2 XPath 选择器

XT 使用简化的 XPath 语法：

```xml
<!-- 绝对路径 -->
/root/element

<!-- 相对路径 -->
child/subchild

<!-- 属性选择 -->
element[@attr='value']

<!-- 通配符 -->
//element          // 任意深度的后代
*                  // 任意元素
```

---

## 5. 执行模型

### 5.1 转换流程

```
┌─────────────────┐
│ 加载 XtTransform │
│     Model       │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   编译为可执行   │
│ IXTransformRule │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 创建转换上下文   │
│ IXTransformCtx  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  执行 main 规则  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   输出结果 XNode │
└─────────────────┘
```

### 5.2 规则应用顺序

1. **选择目标节点**：通过 `xpath` 选择源节点
2. **应用规则**：根据规则类型执行相应操作
3. **递归处理**：对子规则递归执行
4. **输出结果**：将转换结果添加到输出树

### 5.3 输出构建

转换过程中，输出通过 `XNode` 树逐步构建：

```java
public interface IXTransformOutput {
    // 添加子节点
    void addChild(XNode node);
    
    // 设置当前节点的值
    void setValue(Object value);
    
    // 添加属性
    void addAttr(String name, Object value);
    
    // 获取当前输出节点
    XNode getCurrentNode();
}
```

---

## 6. API 设计

### 6.1 核心接口

```java
/**
 * 转换规则接口
 */
public interface IXTransformRule {
    /**
     * 应用转换规则
     * @param parent 父输出节点
     * @param node 当前源节点
     * @param context 转换上下文
     */
    void apply(XNode parent, XNode node, IXTransformContext context);
}

/**
 * 转换上下文接口
 */
public interface IXTransformContext extends IXPathContext<XNode> {
    /**
     * 获取转换模型
     */
    XtTransformModel getTransformModel();
    
    /**
     * 获取模板
     */
    IXTransformRule getTemplate(String id);
    
    /**
     * 获取映射
     */
    XtMappingModel getMapping(String id);
    
    /**
     * 根据标签名获取匹配的规则
     */
    IXTransformRule getRuleForTag(String mappingId, String tagName);
    
    /**
     * 获取输出构建器
     */
    IXTransformOutput getOutput();
    
    /**
     * 创建子上下文
     */
    IXTransformContext childContext(XNode newNode);
}
```

### 6.2 编译器接口

```java
/**
 * XT 转换编译器
 */
public interface IXtTransformCompiler {
    /**
     * 编译转换模型为可执行规则
     */
    IXTransformRule compile(XtTransformModel model);
    
    /**
     * 编译规则组
     */
    IXTransformRule compileRuleGroup(XtRuleGroupModel group);
    
    /**
     * 编译单个规则
     */
    IXTransformRule compileRule(XtRuleModel rule);
}
```

### 6.3 执行入口

```java
/**
 * XT 转换执行器
 */
public class XtTransform {
    /**
     * 加载并编译转换规则
     */
    public static XtTransform load(String path);
    
    /**
     * 执行转换
     */
    public XNode transform(XNode source);
    
    /**
     * 执行转换并返回结果
     */
    public XNode transform(XNode source, Map<String, Object> params);
}
```

---

## 7. 实现架构

### 7.1 模块结构

```
io.nop.xlang.xt/
├── IXTransform.java              # 转换接口
├── IXTransformRule.java          # 规则接口
├── IXTransformContext.java       # 上下文接口
├── model/                        # 模型类（已由 XDef 生成）
│   ├── XtTransformModel.java
│   ├── XtRuleModel.java
│   ├── XtRuleGroupModel.java
│   └── ...
├── core/
│   ├── XtTransformCompiler.java  # 编译器
│   ├── XtTransformContext.java   # 上下文实现
│   ├── XtTransformOutput.java    # 输出构建器
│   └── XtTransformImpl.java      # 转换实现
├── rules/                        # 规则实现
│   ├── ApplyTemplateRule.java
│   ├── ApplyMappingRule.java
│   ├── CopyNodeRule.java
│   ├── CopyBodyRule.java
│   ├── ValueRule.java
│   ├── GenRule.java
│   ├── ScriptRule.java
│   ├── EachRule.java
│   ├── ChooseRule.java
│   ├── IfRule.java
│   └── CustomTagRule.java
└── loader/
    └── XtTransformLoader.java    # 资源加载器
```

### 7.2 类图

```
┌─────────────────────┐
│   XtTransformModel  │  (模型层 - 已存在)
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ IXtTransformCompiler│
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  IXTransformRule    │  (规则层 - 需实现)
└──────────┬──────────┘
           │
     ┌─────┴─────┐
     ▼           ▼
┌─────────┐ ┌─────────┐
│ApplyRule│ │EachRule │ ...
└─────────┘ └─────────┘
           │
           ▼
┌─────────────────────┐
│ IXTransformContext  │  (上下文 - 需实现)
└─────────────────────┘
```

---

## 8. 使用示例

### 8.1 基本转换

```xml
<!-- transform.xt.xml -->
<transform x:schema="/nop/schema/xt.xdef" xmlns:xt="/nop/schema/xt.xdef">
    <main>
        <html>
            <body>
                <xt:apply-mapping id="content" xpath="/root"/>
            </body>
        </html>
    </main>
    
    <mapping id="content">
        <match tag="section">
            <div class="section">
                <h2><xt:value>${@title}</xt:value></h2>
                <xt:copy-body/>
            </div>
        </match>
        <match tag="paragraph">
            <p><xt:copy-body/></p>
        </match>
        <default>
            <xt:copy-node/>
        </default>
    </mapping>
</transform>
```

### 8.2 Java 调用

```java
// 加载转换规则
XtTransform transform = XtTransform.load("/transforms/my-transform.xt.xml");

// 执行转换
XNode source = XNode.parseText("<root><section title='Hello'><paragraph>World</paragraph></section></root>");
XNode result = transform.transform(source);

// 输出结果
System.out.println(result.xml());
```

### 8.3 带参数的转换

```xml
<transform x:schema="/nop/schema/xt.xdef" xmlns:xt="/nop/schema/xt.xdef">
    <main>
        <report title="${$params.title}">
            <xt:each xpath="/root/data/item">
                <item id="${@id}" value="${$params.prefix}_${@name}"/>
            </xt:each>
        </report>
    </main>
</transform>
```

```java
Map<String, Object> params = new HashMap<>();
params.put("title", "Monthly Report");
params.put("prefix", "ITEM");

XNode result = transform.transform(source, params);
```

---

## 9. 错误处理

### 9.1 错误码定义

| 错误码 | 说明 |
|--------|------|
| `ERR_XT_TEMPLATE_NOT_FOUND` | 引用的模板不存在 |
| `ERR_XT_MAPPING_NOT_FOUND` | 引用的映射不存在 |
| `ERR_XT_MANDATORY_NODE_NOT_FOUND` | mandatory=true 时节点未找到 |
| `ERR_XT_XPATH_ERROR` | XPath 表达式语法错误 |
| `ERR_XT_RULE_COMPILE_ERROR` | 规则编译错误 |
| `ERR_XT_CIRCULAR_REFERENCE` | 循环引用检测 |

### 9.2 错误处理策略

1. **编译期检查**：模板 ID、映射 ID 的存在性检查
2. **运行期处理**：XPath 找不到节点时的处理策略
3. **调试支持**：保留源码位置信息用于错误定位

---

## 10. 性能优化

### 10.1 编译缓存

转换规则编译后可缓存，避免重复编译：

```java
// 编译后的规则是线程安全的，可复用
IXTransformRule compiledRule = compiler.compile(model);
```

### 10.2 XPath 优化

- 预编译 XPath 表达式
- 使用索引优化节点查找
- 避免不必要的节点遍历

### 10.3 输出优化

- 使用节点池减少对象创建
- 批量操作减少树修改次数

---

## 11. 扩展机制

### 11.1 自定义规则

可以通过实现 `IXTransformRule` 接口添加自定义规则：

```java
public class MyCustomRule implements IXTransformRule {
    @Override
    public void apply(XNode parent, XNode node, IXTransformContext context) {
        // 自定义转换逻辑
    }
}
```

### 11.2 自定义函数

在表达式中可以使用 XLang 内置函数和自定义函数：

```xml
<xt:value>${my:customFunc(@name)}</xt:value>
```

---

## 12. 未来规划

### 12.1 短期目标

- [ ] 完成核心规则实现
- [ ] 完成编译器和加载器
- [ ] 完成单元测试覆盖

### 12.2 中期目标

- [ ] 支持 JSON 输入/输出
- [ ] 支持流式处理大文件
- [ ] IDE 插件支持（语法高亮、自动补全）

### 12.3 长期目标

- [ ] 双向转换（正向和逆向）
- [ ] 转换规则可视化编辑器
- [ ] 与工作流引擎集成

---

## 附录 A: XDef 完整定义

参见 `/nop/schema/xt.xdef`

## 附录 B: 模型类列表

| 模型类 | 说明 |
|--------|------|
| `XtTransformModel` | 转换模型根节点 |
| `XtImportModel` | 导入模型 |
| `XtMappingModel` | 映射模型 |
| `XtMappingMatchModel` | 映射匹配模型 |
| `XtTemplateModel` | 模板模型 |
| `XtRuleGroupModel` | 规则组模型 |
| `XtRuleModel` | 规则基类 |
| `XtApplyTemplateModel` | 应用模板规则 |
| `XtApplyMappingModel` | 应用映射规则 |
| `XtCopyNodeModel` | 复制节点规则 |
| `XtCopyBodyModel` | 复制内容规则 |
| `XtValueModel` | 值输出规则 |
| `XtGenModel` | 代码生成规则 |
| `XtScriptModel` | 脚本执行规则 |
| `XtEachModel` | 循环遍历规则 |
| `XtChooseModel` | 条件选择规则 |
| `XtChooseWhenModel` | 条件分支模型 |
| `XtIfModel` | 条件判断规则 |
| `XtCustomTagModel` | 自定义标签规则 |
