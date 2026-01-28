# XDef 元模型参考文档

## 概述

XDef是Nop平台的核心元模型语言，用于定义实体模型、关系模型、视图模型、表单模型等。XDef基于XML格式，采用声明式编程思想，支持类型推导、约束定义、关系声明、元属性配置等功能。

**位置**：通常存放在 `_vfs/app/{moduleId}/model/*.xdef`

**核心价值**：通过XDef元模型，Nop平台可以自动生成：
- 实体类（Java POJO）
- DAO接口和实现
- XView模型文件
- GraphQL Schema定义
- SQL建表语句
- 各种配置文件

## ⚠️ 重要：语法区分

**通用 XDef 语法**：简单属性使用 Attribute，列表和对象使用子节点
- ✅ `<user name="string" age="integer"/>`
- ❌ `<user><name>string</name></user>`

**特定领域语法**（如 ORM）：使用领域特定的 Attribute
- ✅ `<column name="userId" stdSqlType="varchar" length="32"/>`

**本文档描述的是 ORM 等特定领域的语法**，如需通用 XDef 语法，请参考 [xdef-core-concepts.md](./xdef-core-concepts.md)

## 核心概念

### 1. 实体模型（Entity Model）

**定义**：描述数据库表的实体结构，包括字段定义、约束、主键等。

**示例**：
```xml
<entity name="NopAuthUser" table="nop_auth_user">
    <columns>
        <!-- 字段定义 -->
        <column name="userId" type="string" length="32" primary="true"/>
        <column name="userName" type="string" length="100" not-null="true"/>
        <column name="email" type="string" length="200" unique="true"/>
        <column name="status" type="int" defaultValue="1"/>
        <column name="createTime" type="datetime"/>
        <column name="updateTime" type="datetime"/>
    </columns>
    
    <!-- 索引定义 -->
    <indexes>
        <index name="idx_user_email" unique="true">
            <index-column name="email"/>
        </index>
        <index name="idx_user_status">
            <index-column name="status"/>
        </index>
    </indexes>
</entity>
```

### 2. 关系模型（Relation Model）

**定义**：描述实体之间的关系。

**类型**：
- `one-to-one`: 一对一关系
- `one-to-many`: 一对多关系
- `many-to-one`: 多对一关系
- `many-to-many`: 多对多关系

**示例**：
```xml
<entity name="NopAuthUser" table="nop_auth_user">
    <!-- 一对多：一个用户有多个角色 -->
    <relation name="roles" type="one-to-many" targetEntity="NopAuthRole" 
              mappedBy="userId" cascade="all,delete-orphan" 
              fetch="lazy"/>
    
    <!-- 多对一：多个角色属于一个部门 -->
    <relation name="dept" type="many-to-one" targetEntity="NopAuthDept"
              foreignKey="deptId" fetch="lazy"/>
</entity>
```

### 3. 视图模型（View Model）

**定义**：描述查询视图的结构。

**示例**：
```xml
<entity name="NopAuthUserView" view="true">
    <columns>
        <column name="userId" type="string"/>
        <column name="userName" type="string"/>
        <column name="deptName" type="string"
        <column name="roleNames" type="string"/>
    </columns>
</entity>
```

## 语法元素

### 1. 实体定义元素

| 元素 | 说明 | 必填 | 示例 |
|------|------|------|--------|
| `<entity>` | 实体定义根节点 | ✅ | `<entity name="User" table="t_user">` |
| `name` | 实体名称 | ✅ | `name="User"` |
| `table` | 数据表名 | ✅ | `table="t_user"` |
| `view` | 是否为视图 | ❌ | `view="true"` |
| `extends` | 继承父实体 | ❌ | `extends="BaseEntity"` |

### 2. 列(column)定义元素

| 元素 | 说明 | 必填 | 示例 |
|------|------|------|--------|
| `<column>` | 字段定义 | ✅ | `<column name="userId">` |
| `name` | 字段名 | ✅ | `name="userId"` |
| `type` | 字段类型 | ✅ | `type="string"` |
| `length` | 字段长度 | ❌ | `length="100"` |
| `precision` | 数值精度 | ❌ | `precision="10"` |
| `scale` | 小数位数 | ❌ | `scale="2"` |
| `not-null` | 是否非空 | ❌ | `not-null="true"` |
| `unique` | 是否唯一 | ❌ | `unique="true"` |
| `primary` | 是否主键 | ❌ | `primary="true"` |
| `auto` | 是否自增 | ❌ | `auto="true"` |
| `defaultValue` | 默认值 | ❌ | `defaultValue="1"` |

### 3. 索引(index)定义元素

| 元素 | 说明 | 必填 | 示例 |
|------|------|------|--------|
| `<index>` | 索引定义 | ✅ | `<index name="idx_user_email">` |
| `unique` | 是否唯一索引 | ❌ | `unique="true"` |
| `<index-column>` | 索引列定义 | ✅ | `<index-column name="email"/>` |

### 4. 关系(relation)定义元素

| 元素 | 说明 | 必填 | 示例 |
|------|------|------|--------|
| `<relation>` | 关系定义 | ✅ | `<relation name="roles">` |
| `name` | 关系名称 | ✅ | `name="roles"` |
| `type` | 关系类型 | ✅ | `type="one-to-many"` |
| `targetEntity` | 目标实体 | ✅ | `targetEntity="NopAuthRole"` |
| `mappedBy` | 映射字段 | ✅ | `mappedBy="userId"` |
| `foreignKey` | 外键字段 | ❌ | `foreignKey="deptId"` |
| `joinColumn` | 连接列 | ❌ | `joinColumn="dept_name"` |
| `fetch` | 加载方式 | ❌ | `fetch="lazy"` |
| `cascade` | 级联操作 | ❌ | `cascade="all,delete-orphan"` |

### 5. 元数据(meta)定义元素

| 元素 | 说明 | 忝填 | 示例 |
|------|------|------|--------|
| `<meta>` | 元数据定义 | ❌ | `<meta extends="BaseEntity">` |
| `displayName` | 显示名称 | ❌ | `displayName="用户名称"` |
| `displayProp` | 显示字段 | ❌ | `displayProp="userName"` |

## 类型系统

### 基础类型

| 类型 | 对应Java类型 | 说明 |
|------|-----------|--------------|------|
| `string` | `String` | 字符串 |
| `text` | `String` | 文本 |
| `int` | `Integer` | 整数 |
| `long` | `Long` | 长整数 |
| `short` | `short` | 短整数 |
| `byte` | `byte` | 字节 |
| `boolean` | `Boolean` | 布尔 |
| `date` | `Date` | 日期 |
| `datetime` | `Date` | 日期时间 |
| `timestamp` | `Timestamp` | 时间戳 |
| `decimal` | `BigDecimal` | 高精度小数 |
| `float` | `Float` | 浮点数 |
| `double` | `Double` | 双精度浮点 |
| `blob` | `byte[]` | 二进制数据 |
| `clob` | `String` | 大文本 |

### 扩展类型

| 类型 | 说明 | 示例 |
|------|------|------|
| `json` | JSON对象 | `json` |
| `xml` | XML对象 | `xml` |
| `jsonb` | JSON二进制 | `jsonb` |

## 约束定义

### 1. 基础约束

| 约束 | 说明 | 示例 |
|------|------|--------|--------|
| `not-null` | 非空约束 | `not-null="true"` |
| `unique` | 唯一约束 | `unique="true"` |
| `defaultValue` | 默认值 | `defaultValue="1"` |

### 2. 长度和精度

| 类型 | 约束 | 示例 |
|------|---------|--------|
| `string` | `length` | `length="100"` |
| `decimal` | `precision`, `scale` | `precision="10", scale="2"` |

### 3. 自动生成字段

| 属性 | 说明 | 示例 |
|--------|------|--------|
| `auto` | 自增主键 | `auto="true"`
| `version` | 乐观锁版本号 | `version="true"`

## 高级特性

### 1. 继承

```xml
<entity name="Employee" table="t_employee">
    <columns>
        <column name="id" type="long" primary="true" auto="true"/>
        <column name="name" type="string" length="50"/>
        <column name="deptId" type="long"/>
    </columns>
    
    <!-- 继承父实体 -->
    <meta extends="BaseEntity">
        <displayName>员工</displayName>
    </meta>
</entity>
```

### 2. 虚拟属性

XDef支持虚拟属性，通过`virtual="true"`定义，这些字段不会映射到数据库表：

```xml
<entity name="UserView" view="true">
    <columns>
        <!-- 虚拟字段：deptName不是实际表字段，而是关联查询结果 -->
        <column name="deptName" type="string" virtual="true" 
                join="dept" />
    </columns>
</entity>
```

### 3. 枚举类型

```xml
<enum name="UserStatus">
    <value name="ACTIVE">正常</value>
    <value name="INACTIVE">未激活</value>
    <value name="DISABLED">已禁用</value>
</enum>
```

### 4. 组合主键

```xml
<entity name="OrderItem" table="t_order_item">
    <columns>
        <!-- 组合主键 -->
        <column name="orderId" type="long"/>
        <column name="itemId" type="long"/>
    </columns>
    
    <id name="pk_order_item" columns="orderId,itemId"/>
</entity>
```

## 扩展功能

### 1. 命名策略

XDef支持自定义命名策略：
- 表名：实体名转蛇形（User → t_user）
- 列名：属性名转蛇形（userName → user_name）
- 主键：id或实体名+Id

### 2. 自定义类型

```xml
<xdef>
    <type name="phone" type="string">
        <custom-type>com.myproject.Phone</custom-type>
    </type>
    
    <converter>
        <class>com.myproject.PhoneConverter</class>
    </converter>
</xdef>
```

### 3. 审计属性

```xml
<column name="createdBy" type="string" audit="true"/>
<column name="updatedAt" type="string" audit="true"/>
<column name="deletedAt" type="datetime" audit="true"/>
```

### 4. 懿注解定义

```xml
<!-- 定义哪些注解会自动生成 -->
<meta>
    <annotation>jakarta.persistence.Entity</annotation>
    <annotation>jakarta.persistence.Table</annotation>
    <annotation>jakarta.persistence.Column</annotation>
</meta>
```

## 最佳实践

### 1. 命名规范
- 表名：使用小写加下划线
- 列名：使用小写加下划线
- 实体名：帕斯卡命名（User, UserProfile）

### 2. 字段设计
- 使用合适的数据类型（如金额使用BigDecimal）
- 字段长度要合理（不要过大或过小）
- 必填字段设置`not-null="true"`
- 唯一字段设置`unique="true"`

### 3. 索引设计
- 为经常查询的字段创建索引
- 为外键创建索引
- 联合索引使用`id`前缀（如`idx_user_email`）

### 4. 关系设计
- 一对多：`one-to-many`，通常使用`fetch="lazy"`
- 多对一：`many-to-one`，通常使用`fetch="lazy"`
- 多对多：`many-to-many`，通常需要中间表

### 5. 性能优化
- 避免过多的关联查询（N+1问题）
- 合理使用懒加载和急加载
- 考虑使用DTO投影，只查询需要的字段
- 使用批量操作减少数据库往返

### 6. 可维护性
- 保持XDef文件结构清晰
- 使用注释说明复杂逻辑
- 合理分组相关字段
- 定期重构大实体

## 注意事项

1. **表名冲突**：确保不同模块的表名不冲突
2. **关系级联**：避免循环依赖
3. **外键命名**：遵循外键命名规范（`dept_id`, `user_id`）
4. **虚拟字段**：明确区分实际字段和虚拟字段
5. **类型安全**：优先使用标准Java类型
6. **审计字段**：使用审计属性（`createdBy`, `updatedAt`, `deletedAt`）

## 相关文档

- [ORM架构](../../architecture/backend/orm-architecture.md)

## 总结

XDef是Nop平台核心的元模型语言，通过XML声明式定义实现模型驱动开发。它支持完整的数据库模型定义，包括实体、关系、视图、枚举等，并能自动生成对应的Java代码、配置文件和SQL语句。合理使用XDef可以大幅提高开发效率，减少手工编码工作量。
