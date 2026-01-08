# XLang 简明指南

## 1. 概述

XLang是面向LowCode领域设计的通用程序语言，基于可逆计算理论，以AST（抽象语法树）为核心概念，建立了一整套描述式的面向AST的定义、生成、转换、分解、合并、编译机制。

### 核心设计理念
- **可逆计算理论**：围绕AST建立统一的描述和转换机制
- **XML语法基础**：结合TypeScript语法，与Java语言无缝集成
- **领域特定语言支持**：提供标准化的元语言，用于定义和解释执行DSL
- **LowCode友好**：自然支持可视化设计、差量化定制等特性

## 2. 主要子语言

### 2.1 XDef - 领域模型定义语言

XDef是XLang中的领域模型定义语法，类似于XSD或JSON Schema，但更加简洁直观：

- 与领域描述同形，schema的Tree结构与领域模型的Tree结构一致
- 所有集合元素通过`xdef:key-attr`定义唯一属性，确保每个节点有稳定的xpath路径
- 支持类型标注、默认值、描述注释等

**示例**：
```xml
<tasks interval="number=3000" xdef:key-attr="id" xdef:body-type="list">
  <task id="!string" label="string" status="dict:nop/task/task-status" />
</tasks>
```

### 2.2 Xpl - XML模板语言

Xpl是采用XML语法的模板语言，内置判断、循环等逻辑标签，支持自定义标签：

- 多种输出模式：XNode、JSON、XML、Text等
- 自定义标签支持定制，实现函数级别的客户化定制
- 保留源码位置信息，便于调试

**示例**：
```xml
<c:if test="${condition}">
  条件为true时执行
</c:if>

<c:for items="${list}" var="item" index="index">
  循环变量: ${item}, 下标: ${index}
</c:for>
```

### 2.3 XScript - 脚本语言

XScript是语法类似于TypeScript的脚本语言，在XPL中通过`<c:script>`标签引入：

- 采用TypeScript语法的子集，去除了类定义、prototype等复杂特性
- 只使用`null`，不使用`undefined`
- 支持调用Java类和扩展方法
- 安全性限制，禁止访问敏感对象

**示例**：
```javascript
<c:script>
  let a = 1;
  let b = a + 2;
  let c = "hello" + b;
  let d = StringHelper.firstPart(c, "."); // 调用扩展方法
</c:script>
```

### 2.4 XDsl - 领域特定语言设计

XDsl提供了通用的DSL设计框架，支持：

- 根据XDef描述自动生成解析器、验证器
- 自动生成可视化设计器
- 内置`x:extends`语法，支持差量继承和合并

**示例**：
```xml
<beans x:extends="base.beans.xml">
  <bean id="a" x:override="remove" />
  <bean id="b" feature:on="my.xxx.enabled">
     <property name="f1" value="3" />
  </bean>
</beans>
```

### 2.5 XPath - 树形结构查询语言

XLang重新定义了XPath的简化版本：

- 可应用于任意树形结构，不限于XML文档
- 使用XScript作为过滤表达式语言
- 支持查询和设置属性值

**示例**：
```java
IXSelector selector = XPathHelper.parseXSelector("a/b[id=aaa]/@attr");
node.updateSelected(selector, "sss"); // 设置属性值
String value = node.selectOne(selector); // 获取属性值
```

### 2.6 XTransform - AST转换语言

XTransform是专用于AST转换的简化版本，类似于xslt但更加简洁：

- 使用`XSelector`选择节点
- 通过属性表达式生成属性
- 利用嵌套结构自然表达生成过程

**示例**：
```xml
<xt:transform>
  <div xt:xpath="root">
    <div xt:xpath="child" xt:attrs="{a,b,c}" title="%{$node.title}-sub">
      <!-- 转换内容 -->
    </div>
  </div>
</xt:transform>
```

## 3. 基本使用示例

### 3.1 创建XDef定义

```xml
<!-- user.xdef -->
<user xdef:key-attr="id">
  <id>!string</id>
  <name>string</name>
  <age>int</age>
  <addresses xdef:key-attr="type" xdef:body-type="list">
    <address type="!string" value="string" />
  </addresses>
</user>
```

### 3.2 使用Xpl模板生成XML

```xml
<!-- user-template.xpl -->
<users>
  <c:for items="${users}" var="user">
    <user id="${user.id}">
      <name>${user.name}</name>
      <age>${user.age}</age>
      <addresses>
        <c:for items="${user.addresses}" var="addr">
          <address type="${addr.type}" value="${addr.value}" />
        </c:for>
      </addresses>
    </user>
  </c:for>
</users>
```

### 3.3 使用XDsl扩展现有配置

```xml
<!-- extended-config.xml -->
<config x:extends="base-config.xml">
  <!-- 继承基础配置 -->
  <setting name="timeout" value="5000" /> <!-- 覆盖基础配置 -->
  <setting name="newSetting" value="value" /> <!-- 添加新配置 -->
  <setting name="oldSetting" x:override="remove" /> <!-- 删除基础配置 -->
</config>
```

## 4. 最佳实践

1. **优先使用XDef定义领域模型**：确保模型结构清晰，便于可视化设计和差量化定制
2. **合理使用Xpl模板**：将复杂逻辑封装为自定义标签，提高代码复用性
3. **使用XScript编写业务逻辑**：保持与Java的无缝集成，便于调用现有代码
4. **利用XDsl的继承机制**：实现配置的模块化和可扩展性
5. **使用XPath进行树形结构操作**：统一查询和更新操作，简化代码
6. **保持描述式风格**：避免在模板中编写复杂的业务逻辑，将逻辑封装到Java或XScript中

## 5. 应用场景

- **LowCode平台**：支持可视化设计和差量化定制
- **领域特定语言开发**：快速定义和实现DSL
- **多端代码生成**：通过不同的标签实现，生成适配不同平台的代码
- **配置管理**：支持配置的继承、合并和差量化更新
- **AST转换**：实现不同语言或格式之间的转换

## 6. 总结

XLang是一个面向LowCode领域的通用程序语言，基于可逆计算理论，以AST为核心，提供了一整套描述式的面向AST的定义、生成、转换机制。它由多个子语言组成，包括XDef、Xpl、XScript、XDsl、XPath和XTransform等，每个子语言专注于解决特定的问题，共同构成了一个完整的语言生态系统。

XLang的设计目标是成为定义和解释执行DSL的标准化元语言，支持可视化设计、差量化定制等LowCode开发所需的特性。它的语法简洁直观，与Java语言无缝集成，适合用于构建复杂的LowCode平台和领域特定语言。