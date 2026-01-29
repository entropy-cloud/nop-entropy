# XPL模板语言

## 概述

XPL是Nop平台的模板语言，采用XML语法格式，用于生成XML、HTML、JSON等各种格式的内容。XPL与XScript脚本语言紧密结合，可通过自定义标签引入新的语法结构。

**位置**：通常存放在`_vfs/pages/`目录
**扩展名**：`.xml`或`.xpl`

**核心特性**：
- XML语法基础：采用熟悉的XML标签语法
- 多种输出模式：支持XNode、JSON、XML、HTML、Text等
- 内置控制逻辑：提供if、for、while等常用控制标签
- 支持自定义标签：可扩展自定义标签库
- 与XScript集成：通过`<c:script>`标签无缝集成XScript脚本
- 保留源码位置：便于调试和错误定位

## 基本语法

### 1. 变量和表达式

```xml
<!-- 基本变量输出 -->
${user.userName}
${order.items.length}

<!-- 表达式计算 -->
${user.userName + " - " + user.statusLabel}

<!-- 属性动态值 -->
<input class="${condition ? 'active' : ''}" />
```

### 2. 条件判断

```xml
<!-- if结构 -->
<c:if test="${user.status == 1}">
    <c:set var="statusLabel" value="正常"/>
</c:if>
<c:if test="${user.status == 2}">
    <c:set var="statusLabel" value="已禁用"/>
</c:if>

<!-- choose结构 -->
<c:choose>
  <c:when test="${condition1}">条件1为true时执行</c:when>
  <c:when test="${condition2}">条件2为true时执行</c:when>
  <c:otherwise>所有条件都不满足时执行</c:otherwise>
</c:choose>
```

### 3. 循环

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

<!-- 循环控制 -->
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

### 4. 嵌入脚本

```xml
<c:script>
  let x = 1;
  let y = x + 2;
  let z = "结果: " + y;
</c:script>

${z} <!-- 输出结果: 3 -->

<!-- 输出文本 -->
<c:out value="${variable}" />
<c:print>直接输出文本内容</c:print>
```

## 模板结构

### 1. 网格模板

```xml
<grid name="userList">
    <!-- 列定义 -->
    <cols>
        <col id="userId" width="100px"/>
        <col id="userName" width="200px" sortable="true"/>
        <col id="email" width="300px"/>
    </cols>

    <!-- 数据绑定 -->
    <rows var="users">
        <c:for items="${userList.users}" var="user">
            <row>
                <cell>${user.userId}</cell>
                <cell>${user.userName}</cell>
                <cell>${user.email}</cell>
            </row>
        </c:for>
    </rows>
</grid>
```

### 2. 表单模板

```xml
<form name="userForm">
    <!-- 字段定义 -->
    <fields>
        <field id="userName" label="用户名" />
        <field id="email" label="邮箱" />
        <field id="status" label="状态" options="1:正常,2:已禁用"/>
    </fields>
</form>

<!-- 动态表单 -->
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

### 3. 页面模板

```xml
<page name="userPage">
    <layout>
        <section name="header">
            <h1>${pageTitle}</h1>
        </section>

        <section name="content">
            <grid name="userList"/>
            <simple-page/>
        </section>
    </layout>
</page>
```

## 自定义标签

### 1. 定义标签库

创建标签库文件`my-lib.xlib`：

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

### 2. 使用自定义标签

```xml
<!-- 导入标签库 -->
<c:import from="/path/to/my-lib.xlib" />

<!-- 使用标签 -->
<lib:MyTag param="test" optionalParam="5">
  <p>标签体内容</p>
</lib:MyTag>

<!-- 局部引入标签库 -->
<lib:CustomTag xpl:lib="/path/to/library.xlib" param="value" />
```

## 动态属性

### 1. 动态属性值

```xml
<!-- 如果属性值为null，该属性会被自动忽略 -->
<input class="${condition ? 'active' : null}" />

<!-- 动态添加多个属性 -->
<div xpl:attrs="${
  id: 'my-div',
  class: 'container',
  style: null  <!-- 此属性会被忽略 -->
}">
  内容
</div>
```

### 2. 属性优先级

如果节点上已经存在某属性，则`xpl:attrs`指定的同名属性会被忽略：

```xml
<!-- 实际输出时name属性值为"override" -->
<input name="override" xpl:attrs="{name: 'from-attrs'}" />
```

## XPL专用属性

### 1. 条件执行

```xml
<!-- 仅当条件为true时执行标签 -->
<div xpl:if="${condition}">
  条件为true时显示
</div>
```

### 2. 忽略标签解析

```xml
<!-- 忽略标签解析，直接输出 -->
<c:if test="${condition}" xpl:ignoreTag="true">
  此标签不会被解析
</c:if>

<!-- 允许未识别的标签 -->
<unknown:tag xpl:allowUnknownTag="true" />
```

## 输出模式

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

## 内置函数

```xml
<!-- 字符串函数 -->
<c:set var="lowerEmail" value="${StringHelper.toLowerCase(user.email)}"/>
<c:set var="upperName" value="${StringHelper.toUpperCase(user.userName)}"/>
<c:set var="formatted" value="${StringHelper.format('订单号：{0}', order.id)}"/>

<!-- 日期函数 -->
<c:set var="today" value="${DateHelper.today()}"/>
<c:set var="formatDate" value="${DateHelper.format(today, 'yyyy-MM-dd')}"/>
```

## 高级特性

### 1. 模板继承

```xml
<!-- 基础模板 -->
<grid name="baseGrid">
    <cols>
        <col id="id" width="80px"/>
    </cols>
</grid>

<!-- 继承基础模板，添加额外列 -->
<grid name="userGrid" x:extends="baseGrid">
    <cols>
        <col id="id" width="80px"/>
        <col id="userName" width="120px"/>
        <col id="email" width="200px" sortable="true"/>
    </cols>
</grid>
```

### 2. 异常处理

```xml
<c:try>
    <!-- 代码中可能抛异常 -->
    ${userDao.saveEntity(user)}

<c:catch exception="e">
    <!-- 异常处理 -->
    <span class="error">${e.message}</span>
</c:catch>
```

## 实际应用

### 1. 生成HTML列表

```xml
<ul>
  <c:for items="${items}" var="item">
    <li class="${item.active ? 'active' : ''}">
      ${item.name}
    </li>
  </c:for>
</ul>
```

### 2. 生成JSON配置

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

### 3. 数据表格渲染

```xml
<grid name="userTable" api="/api/User/list">
    <cols>
        <col id="id" width="50px" sortable="true"/>
        <col id="userName" width="150px"/>
        <col id="email" width="250px"/>
        <col id="status" width="100px"/>
    </cols>

    <rows var="users" api="/api/User/list?status=1"/>
</grid>
```

### 4. 详情页渲染

```xml
<page name="userDetailPage">
    <layout>
        <section name="basic">
            <h1>${user.userName}</h1>
            <p>邮箱：${user.email}</p>
            <p>状态：${user.statusLabel}</p>
        </section>

        <section name="roles">
            <h2>角色列表</h2>
            <ul>
                <c:for items="${user.roles}" var="role">
                    <li>${role.name} - ${role.description}</li>
                </c:for>
            </ul>
        </section>
    </layout>
</page>
```

## 最佳实践

1. **保持模板简洁**：模板主要用于生成结构，复杂逻辑应封装到自定义标签或XScript中
2. **使用自定义标签**：将重复的代码片段封装为自定义标签，提高代码复用性
3. **合理使用输出模式**：根据需要选择合适的输出模式
4. **避免嵌套过深**：模板嵌套不宜过深，建议不超过3-4层
5. **使用描述性标签名**：自定义标签名应具有描述性，便于理解和维护
6. **合理使用动态属性**：利用`xpl:attrs`简化动态属性的处理
7. **保持代码风格一致**：使用统一的缩进和命名规范
8. **输出转义**：所有输出变量都应该经过转义（HTML、XML等），避免XSS攻击

## 注意事项

1. **模板文件编码**：确保XPL模板文件使用UTF-8编码
2. **语法正确性**：XML结构必须正确，所有标签都要闭合
3. **变量作用域**：注意变量的生命周期和作用域
4. **null值处理**：利用动态属性自动忽略null值的特性
5. **性能考虑**：大数据集使用分页，避免一次性加载大量数据
