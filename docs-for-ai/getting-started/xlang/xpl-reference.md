# XPL 模板语言参考文档

## 概述

XPL是Nop平台的模板引擎，基于XPL模板语法生成各种文本格式的内容。XPL模板引擎使用XML格式的XView模型定义来驱动页面生成、SQL生成、代码生成等。

**位置**：通常存放在 `_vfs/pages/` 目录
**扩展名**：`.xml` 或 `.xpl``

**核心价值**：
- 通过声明式模板定义来生成页面、SQL、代码、配置等
- 支持条件分支、循环、变量、函数调用等
- 集成数据查询和绑定
- 实现模型驱动的内容生成

## 核心概念

### 1. XPL语法基础

### 基本变量和表达式

```xml
<!-- 基本变量 -->
<var name="user" type="User"/>
<var name="order" type="Order"/>
<var name="items" type="List<OrderItem>"/>
```

### 2. 输出语法

```xml
<!-- 文本输出 -->
${user.userName}
${order.items.length}

<!-- 表达式计算 -->
${user.userName + " - " + user.statusLabel}
```

### 3. 条件分支

```xml
<!-- if-else 结构 -->
<c:if test="${user.status == 1}">
    <c:set var="statusLabel" value="正常"/>
</c:if>
<c:set var="statusLabel" value="${user.statusLabel}"/>

<c:if test="${user.status == 2}">
    <c:set var="statusLabel" value="已禁用"/>
</c:if>

<c:else>
    <c:set var="statusLabel" value="${user.statusLabel}"/>
</c:else>
```

### 4. 循环遍历

```xml
<!-- 遍历订单项 -->
<c:if test="${user.items != null}">
    <c:for items="${user.items}" var="item">
        <c:set var="total" value="${total + item.price * item.quantity}"/>
    </c:for>
</c:if>
```

### 5. 函数调用

```xml
<!-- 调用内置函数 -->
<c:set var="email" value="${StringHelper.toLowerCase(user.email)}"/>
<c:set var="formatted" value="${StringHelper.format('订单号：{0}', order.id)}"/>
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
</page>
```

## 数据绑定

### 1. 基础变量绑定

```xml
<!-- 绑定单个值 -->
<var name="userName" value="${user.userName}"/>
<var name="orderStatus" value="${order.status}"/>

<!-- 绑定对象属性 -->
<var name="user" value="${user}"/> <!-- 完整对象 -->
<var name="items" value="${order.items}"/>  <!-- 集合 -->
```

### 2. 表格数据绑定

```xml
<!-- 遍历订单项 -->
<c:for items="${order.items}" var="item">
    <grid name="itemGrid">
        <rows>
            <row>
                <cell>${item.name}</cell>
                <cell>${item.price}</cell>
                <cell>${item.quantity}</cell>
                <cell>${item.price * item.quantity}</cell>
            </row>
        </rows>
    </grid>
</c:for>
```

### 3. 表单数据绑定

```xml
<!-- 读取用户数据 -->
<form name="userForm" data="${user}">
    <fields>
        <field id="userName" label="用户名" />
        <field id="email" label="邮箱" />
    </fields>
</form>
```

## 内置函数和对象

### 1. 工具类函数

```xml
<!-- 字符串函数 -->
<c:set var="lowerEmail" value="${StringHelper.toLowerCase(user.email)}"/>
<c:set var="upperName" value="${StringHelper.toUpperCase(user.userName)}"/>
<c:set var="isEmail" value="${StringHelper.isEmail(user.email)}"/>

<!-- 日期函数 -->
<c:set var="today" value="${DateHelper.today()}"/>
<c:set var="formatDate" value="${DateHelper.format(today, 'yyyy-MM-dd')}"/>
```

### 2. 对象操作

```xml
<!-- 获取属性 -->
<c:set var="userName" value="${user.userName}"/>

<!-- 检查属性 -->
<c:if test="${!user.userName}">
    <c:set var="userName" value="匿名用户"/>
</c:if>

<!-- 对象遍历 -->
<c:for items="${order.items}" var="item">
    <c:if test="${item.price > 100}">
        <c:set var="expensive" value="true"/>
    </c:if>
</c:for>
```

### 3. 集合操作

```xml
<!-- 获取大小 -->
<c:set var="count" value="${user.roles.size()}"/>

<!-- 过滤列表 -->
<c:filter items="${user.roles}" var="role" filter="${role.status == 1}">
    <c:set var="activeRoles" value="${activeRoles + [role]}"/>
</c:filter>

<!-- 连接字符串 -->
<c:set var="allNames" value="${StringHelper.join(activeRoles, ', ')}"/>
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

### 2. 条件渲染

```xml
<c:if test="${user.status == 1}">
    <span class="status-active">正常</span>
</c:if>

<c:if test="${user.status == 2}">
    <span class="status-disabled">已禁用</span>
</c:if>

<c:if test="${user.status == 3}">
    <span class="status-locked">已锁定</span>
</c:if>
```

### 3. 循环控制

```xml
<!-- 限制迭代次数 -->
<c:for items="${user.roles}" var="role" begin="0" end="5">
    ${role.name}
</c:for>

<!-- 循环索引 -->
<c:for var="i" begin="0" end="${users.size()}">
    第${i + 1}位用户：${users[i].name}
</c:for>
```

### 4. 异常处理

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

### 1. 数据表格渲染

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

### 2. 表单渲染

```xml
<form name="userForm" data="${user}">
    <fields>
        <field id="userName" label="用户名" />
        <field id="email" label="邮箱" />
        <field id="status" label="状态" options="1:正常,2:已禁用"/>
    </fields>
</form>
```

### 3. 详情页渲染

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

### 4. 批量操作表格

```xml
<grid name="orderTable" api="/api/Order/list">
    <cols>
        <col id="orderId" width="100px"/>
        <col id="orderDate" width="150px"/>
        <col id="totalAmount" width="120px"/>
        <col id="status" width="80px"/>
    </cols>
    
    <!-- 批量导入数据 -->
    <rows var="orders" api="/api/Order/batch-import"/>
</rows>
</grid>
```

## 最佳实践

### 1. 模板设计
- **保持简洁**：每个模板职责单一
- **可复用**：抽象公共模板供其他模板继承
- **清晰命名**：模板名称和变量名要见名知意
- **合理分组**：相关文件放在一起

### 2. 性能优化
- **懒加载**：大数据集使用懒加载
- **分页查询**：避免一次性加载大量数据
- **缓存数据**：对频繁访问的数据进行缓存

### 3. 安全考虑
- **输出转义**：所有输出变量都应该经过转义（HTML、XML等）
- **XSS防护**：避免XSS攻击，所有输出都经过HTML转义
- **SQL注入**：使用参数化查询，避免SQL注入

### 4. 国际化
- **使用i18n**标签：所有用户可见文本都应该国际化
- **使用字典**：常见文本使用字典键

### 5. 调试技巧
- **添加注释**：在复杂模板中添加注释说明
- **打印变量**：使用`<c:out>`打印调试信息
- **断点调试**：在模板中添加断点，查看数据结构

## 注意事项

1. **模板文件编码**：确保XPL模板文件使用UTF-8编码
2. **语法正确性**：XML结构必须正确，所有标签都要闭合
3. **变量作用域**：注意变量的生命周期和作用域
4. **API调用**：合理使用API调用，避免过度调用
5. **错误处理**：对可能的异常进行优雅降级处理

## 相关文档

- [视图层开发](../frontend/view-layer-development.md)
- [前端开发指南](../frontend/frontend-development.md)
- [GraphQL服务开发](../api/graphql-guide.md)

## 总结

XPL模板引擎是Nop平台核心的前端内容生成引擎，通过声明式的XView模型驱动，可以快速生成页面、表单、表格等内容。合理使用XPL模板可以大幅提高前端开发效率，减少手工编写前端代码的工作量。通过模板继承和复用，可以实现一致的页面风格和高效的开发流程。
