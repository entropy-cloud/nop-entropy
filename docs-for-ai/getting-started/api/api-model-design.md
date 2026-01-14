# API模型设计指南

## 概述

Nop平台使用XML定义API模型，自动生成GraphQL API和REST API，支持API文档自动生成和测试。

## 核心概念

### 1. API模型
- 定义：描述API接口结构和参数的模型
- 格式：XML，如`app-api.xml`
- 内容：查询、变更、类型、输入、枚举等

### 2. GraphQL API
- 自动生成GraphQL Schema
- 支持查询、变更、订阅操作
- 支持类型系统和指令

### 3. REST API
- 自动从GraphQL转换为REST API
- 支持HTTP方法映射
- 支持参数绑定和结果转换

## 设计流程

### 1. 设计API模型
通过XML定义API接口

### 2. 生成API代码
```shell
nop-cli gen model/nop-auth.api.xml -t=/nop/templates/api
```

### 3. 部署和访问
- GraphQL API：`http://localhost:8080/graphql`
- REST API：`http://localhost:8080/api/[resource]`
- API文档：`http://localhost:8080/q/graphql-ui`

### 4. 测试和优化
- 使用GraphQL UI测试API
- 进行性能测试和安全测试
- 优化API设计

## 设计注意事项

### 1. API命名
- **查询**：使用动词+名词，如`queryUsers`
- **变更**：使用动词+名词，如`createUser`
- **类型**：使用名词，如`User`
- **输入**：使用名词+Input，如`UserInput`

### 2. 字段设计
- **名称**：使用驼峰命名，如`userName`
- **类型**：选择合适的GraphQL类型
- **描述**：添加清晰的字段描述
- **必填性**：明确字段是否必填

### 3. 参数设计
- **数量**：每个API参数不宜过多
- **类型**：使用强类型定义
- **默认值**：为可选参数设置合理默认值
- **验证**：添加参数验证规则

### 4. 结果设计
- **层次**：避免过深的嵌套结构
- **分页**：查询结果使用分页设计
- **错误处理**：统一错误格式和错误码
- **扩展**：支持扩展字段和定制返回

## 示例模型

### XML模型示例

```xml
<graphql:schema xmlns:graphql="http://nop-xlang.github.io/schema/graphql.xdef">
  <!-- 类型定义 -->
  <graphql:type name="User">
    <graphql:field name="id" type="ID!" description="用户ID" />
    <graphql:field name="name" type="String!" description="姓名" />
    <graphql:field name="email" type="String" description="邮箱" />
    <graphql:field name="orders" type="[Order!]!" description="订单列表" />
  </graphql:type>
  
  <graphql:type name="Order">
    <graphql:field name="id" type="ID!" description="订单ID" />
    <graphql:field name="userId" type="ID!" description="用户ID" />
    <graphql:field name="totalAmount" type="Float!" description="总金额" />
    <graphql:field name="status" type="OrderStatus!" description="订单状态" />
  </graphql:type>
  
  <!-- 枚举定义 -->
  <graphql:enum name="OrderStatus">
    <graphql:value name="PENDING" description="待处理" />
    <graphql:value name="PAID" description="已支付" />
    <graphql:value name="SHIPPED" description="已发货" />
    <graphql:value name="COMPLETED" description="已完成" />
  </graphql:enum>
  
  <!-- 输入定义 -->
  <graphql:input name="UserInput">
    <graphql:field name="name" type="String!" description="姓名" />
    <graphql:field name="email" type="String" description="邮箱" />
  </graphql:input>
  
  <!-- 查询定义 -->
  <graphql:query name="user">
    <graphql:param name="id" type="ID!" description="用户ID" />
    <graphql:return type="User" description="查询用户" />
  </graphql:query>
  
  <graphql:query name="users">
    <graphql:param name="query" type="UserQuery" description="查询参数" />
    <graphql:return type="[User!]!" description="查询用户列表" />
  </graphql:query>
  
  <!-- 变更定义 -->
  <graphql:mutation name="createUser">
    <graphql:param name="input" type="UserInput!" description="用户信息" />
    <graphql:return type="User!" description="创建用户" />
  </graphql:mutation>
</graphql:schema>
```

## 最佳实践

1. **使用强类型**：为所有字段定义明确的类型
2. **添加描述**：为类型、字段、参数添加清晰描述
3. **分页设计**：查询结果使用分页，避免返回过多数据
4. **错误处理**：使用统一的错误格式和错误码
5. **版本管理**：考虑API版本管理策略
6. **安全设计**：添加认证和授权机制

## 注意事项

- API模型是系统对外的接口，应仔细设计
- 考虑向后兼容性，避免频繁变更API
- 合理设计API的粒度，避免过粗或过细
- 考虑性能和安全性
- 支持API测试和文档自动生成

## 扩展功能

### 1. 权限控制
- 为API添加权限指令
- 实现字段级权限控制
- 支持角色权限配置

### 2. 缓存机制
- 为查询添加缓存指令
- 支持缓存失效和更新
- 优化查询性能

### 3. 监控和日志
- 记录API调用日志
- 监控API性能指标
- 实现告警机制