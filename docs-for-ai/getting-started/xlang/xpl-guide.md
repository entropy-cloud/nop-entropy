# XPL 简明指南

## 1. 概述

XPL是一种采用XML语法格式的模板语言，支持多种输出模式，与XScript脚本语言紧密结合，可通过自定义标签引入新的语法结构。它是XLang语言家族中的核心成员，用于生成XML、HTML、JSON等各种格式的内容。

## 2. 核心特性

- **XML语法基础**：采用熟悉的XML标签语法，易于学习和使用
- **多种输出模式**：支持XNode、JSON、XML、HTML、Text等多种输出格式
- **内置控制逻辑**：提供if、for、while等常用控制标签
- **支持自定义标签**：可扩展自定义标签库，实现代码复用
- **与XScript集成**：通过`<c:script>`标签无缝集成XScript脚本
- **保留源码位置**：便于调试和错误定位

## 3. 内置标签

### 3.1 控制逻辑标签

#### 条件判断
```xml
<c:if test="${condition}">
  条件为true时执行
</c:if>

<c:choose>
  <when test="${condition1}">条件1为true时执行</when>
  <when test="${condition2}">条件2为true时执行</when>
  <otherwise>所有条件都不满足时执行</otherwise>
</c:choose>
```

#### 循环
```xml
<!-- 遍历集合 -->
<c:for items="${list}" var="item" index="index">
  循环变量: ${item}, 下标: ${index}
</c:for>

<!-- 范围循环 -->
<c:for begin="0" end="4" var="i">
  数值: ${i}
</c:for>

<!-- while循环 -->
<c:while test="${count < 10}">
  计数: ${count}
  <c:script>count++;</c:script>
</c:while>
```

#### 循环控制
```xml
<c:for items="${list}" var="item">
  <c:if test="${item == 'stop'}">
    <c:break />
  </c:if>
  <c:if test="${item == 'skip'}">
    <c:continue />
  </c:if>
  处理: ${item}
</c:for>
```

### 3.2 脚本和输出标签

#### 嵌入脚本
```xml
<c:script>
  let x = 1;
  let y = x + 2;
  let z = "结果: " + y;
</c:script>

${z} <!-- 输出结果: 3 -->
```

#### 输出文本
```xml
<c:out value="${variable}" />
<c:print>直接输出文本内容</c:print>
```

### 3.3 导入和包含

#### 导入标签库
```xml
<c:import from="/path/to/library.xlib" />
<c:import from="/path/to/library.xlib" as="alias" />

<!-- 使用导入的标签 -->
<lib:CustomTag param="value" />
<alias:CustomTag param="value" />
```

## 4. 自定义标签

### 4.1 定义标签库

创建标签库文件 `my-lib.xlib`：
```xml
<lib x:schema="/nop/schema/xlib.xdef" xmlns:x="/nop/schema/xdsl.xdef">
  <tags>
    <MyTag outputMode="xml">
      <attr name="param" type="String" required="true" />
      <attr name="optionalParam" type="int" defaultValue="0" />
      
      <source>
        <div class="my-tag">
          <h3>自定义标签</h3>
          <p>参数: ${param}</p>
          <p>可选参数: ${optionalParam}</p>
          <c:slot slot:name="default" />
        </div>
      </source>
    </MyTag>
  </tags>
</lib>
```

### 4.2 使用自定义标签

```xml
<c:import from="/path/to/my-lib.xlib" />

<lib:MyTag param="test" optionalParam="5">
  <p>标签体内容</p>
</lib:MyTag>
```

## 5. 输出模式

XPL支持多种输出模式，可通过`xpl:outputMode`属性设置：

- `none`：不允许输出文本
- `html`：输出HTML文本，自动处理HTML标签
- `xml`：输出XML文本
- `text`：输出纯文本，不进行XML转义
- `node`：输出XNode对象

```xml
<!-- 设置输出模式为HTML -->
<lib:MyTag xpl:outputMode="html" />

<!-- 在标签库中设置默认输出模式 -->
<lib defaultOutputMode="xml">
  <!-- 标签定义 -->
</lib>
```

## 6. 动态属性

### 6.1 动态属性值

```xml
<!-- 如果属性值为null，该属性会被自动忽略 -->
<input class="${condition ? 'active' : null}" />

<!-- 动态添加多个属性 -->
<div xpl:attrs="${{ 
  id: 'my-div',
  class: 'container',
  style: null  <!-- 此属性会被忽略 -->
}}">
  内容
</div>
```

### 6.2 优先级

如果节点上已经存在某属性，则`xpl:attrs`指定的同名属性会被忽略：

```xml
<!-- 实际输出时name属性值为"override" -->
<input name="override" xpl:attrs="{name: 'from-attrs'}" />
```

## 7. XPL专用属性

XPL提供了一些内置属性，可用于控制标签的行为：

### 7.1 条件执行

```xml
<!-- 仅当条件为true时执行标签 -->
<div xpl:if="${condition}">
  条件为true时显示
</div>
```

### 7.2 忽略标签解析

```xml
<!-- 忽略标签解析，直接输出 -->
<c:if test="${condition}" xpl:ignoreTag="true">
  此标签不会被解析
</c:if>

<!-- 允许未识别的标签 -->
<unknown:tag xpl:allowUnknownTag="true" />
```

### 7.3 局部标签库

```xml
<!-- 局部引入标签库，仅对当前标签有效 -->
<lib:CustomTag xpl:lib="/path/to/library.xlib" param="value" />
```

## 8. 基本使用示例

### 8.1 生成HTML列表

```xml
<ul>
  <c:for items="${items}" var="item">
    <li class="${item.active ? 'active' : ''}">
      ${item.name}
    </li>
  </c:for>
</ul>
```

### 8.2 动态生成表单

```xml
<form action="${action}" method="post">
  <c:for items="${fields}" var="field">
    <div class="form-group">
      <label for="${field.name}">${field.label}</label>
      <input 
        type="${field.type}" 
        id="${field.name}" 
        name="${field.name}" 
        value="${field.value}" 
        required="${field.required ? 'required' : null}"
      />
    </div>
  </c:for>
  <button type="submit">提交</button>
</form>
```

### 8.3 生成JSON配置

```xml
<config>
  <name>${appName}</name>
  <version>${appVersion}</version>
  <features>
    <c:for items="${features}" var="feature">
      <feature name="${feature.name}" enabled="${feature.enabled}" />
    </c:for>
  </features>
</config>
```

## 9. 最佳实践

1. **保持模板简洁**：模板主要用于生成结构，复杂逻辑应封装到自定义标签或XScript中
2. **使用自定义标签**：将重复的代码片段封装为自定义标签，提高代码复用性
3. **合理使用输出模式**：根据需要选择合适的输出模式
4. **避免嵌套过深**：模板嵌套不宜过深，建议不超过3-4层
5. **使用描述性标签名**：自定义标签名应具有描述性，便于理解和维护
6. **合理使用动态属性**：利用`xpl:attrs`简化动态属性的处理
7. **保持代码风格一致**：使用统一的缩进和命名规范

## 10. 应用场景

- **网页模板生成**：生成HTML页面，支持动态内容
- **配置文件生成**：生成XML、JSON等配置文件
- **代码生成**：生成Java、SQL等代码文件
- **邮件模板**：生成动态邮件内容
- **报表生成**：生成各种格式的报表
- **API响应生成**：生成JSON或XML格式的API响应

## 11. 总结

XPL是一种功能强大、灵活易用的模板语言，具有以下特点：

- 采用熟悉的XML语法，易于学习和使用
- 支持多种输出模式，适应不同场景需求
- 提供丰富的内置标签，满足常用的控制逻辑需求
- 支持自定义标签，实现代码复用和扩展
- 与XScript无缝集成，支持复杂业务逻辑
- 保留源码位置信息，便于调试

XPL是构建LowCode平台和领域特定语言的重要工具，适合用于各种模板生成场景，能够显著提高开发效率和代码复用性。