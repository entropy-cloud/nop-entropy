# 数据库模型设计指南

## 概述

Nop平台使用XML定义数据库模型，自动生成数据库表结构、实体类、DAO接口等，支持多种数据库方言。

## 核心概念

### 1. 数据库模型
- 定义：描述数据库表结构和关系的模型
- 格式：XML
  - model目录下：如`nop-auth.orm.xml`（使用模块名）
  - _vfs目录下：如`app.orm.xml`
- 内容：实体、字段、关系、索引等

### 2. ORM映射
- 自动将模型映射到数据库表
- 支持多种数据库：MySQL、PostgreSQL、Oracle等
- 支持字段类型映射和转换

### 3. 关系定义
- 一对一关系
- 一对多关系
- 多对多关系
- 自关联关系

## 设计流程

### 1. 设计实体
- 定义实体名称、表名
- 设置主键和索引
- 定义字段和数据类型

### 2. 定义关系
- 设置外键关系
- 定义级联操作
- 配置加载策略

### 3. 生成代码
```shell
nop-cli gen model/nop-auth.orm.xml -t=/nop/templates/orm
```

### 4. 同步数据库
- 生成SQL脚本
- 执行SQL脚本创建表结构
- 同步数据结构变更

## 设计注意事项

### 1. 实体设计
- **命名规范**：实体名使用单数形式，表名使用复数形式
- **主键设计**：优先使用UUID或雪花ID，避免自增ID
- **字段命名**：使用下划线命名，如`user_name`

### 2. 字段设计
- **数据类型**：选择合适的数据类型，避免过度设计
- **约束**：合理使用NOT NULL、UNIQUE等约束
- **默认值**：为必填字段设置合理默认值
- **长度**：根据实际需求设置字段长度

### 3. 关系设计
- **外键**：使用外键约束确保数据完整性
- **级联操作**：谨慎使用级联删除，避免意外数据丢失
- **加载策略**：根据访问模式设置懒加载或急加载

### 4. 索引设计
- **主键索引**：自动生成
- **唯一索引**：用于唯一约束
- **普通索引**：用于频繁查询的字段
- **联合索引**：用于多字段查询

### 5. 性能考虑
- **避免大表**：合理拆分表，避免单表数据量过大
- **避免冗余字段**：使用关联查询替代冗余字段
- **合理使用视图**：复杂查询使用视图简化

## 示例模型

### XML模型示例

```xml
<orm:entities xmlns:orm="http://nop-xlang.github.io/schema/orm.xdef">
  <orm:entity name="User" table="user">
    <orm:field name="id" type="string" primary="true" length="32" />
    <orm:field name="name" type="string" required="true" length="100" />
    <orm:field name="email" type="string" unique="true" length="200" />
    <orm:field name="create_time" type="datetime" default="now()" />
    
    <orm:relation name="orders" type="one-to-many" target="Order" inverse="user" />
    
    <orm:index name="idx_email" type="unique">
      <orm:index-field name="email" />
    </orm:index>
  </orm:entity>
  
  <orm:entity name="Order" table="order">
    <orm:field name="id" type="string" primary="true" length="32" />
    <orm:field name="user_id" type="string" required="true" length="32" />
    <orm:field name="total_amount" type="decimal" precision="10" scale="2" />
    
    <orm:relation name="user" type="many-to-one" target="User" />
  </orm:entity>
</orm:entities>
```

## 最佳实践

1. **遵循命名规范**：统一实体、表、字段命名
2. **合理设计关系**：避免过于复杂的关系结构
3. **考虑性能**：设计时考虑查询性能和数据量
4. **版本管理**：对模型文件进行版本控制
5. **测试验证**：生成SQL后进行验证，确保正确性

## 注意事项

- 模型设计是系统设计的基础，应仔细设计
- 避免频繁修改模型结构，特别是生产环境
- 合理使用索引，避免过多索引影响性能
- 考虑数据迁移和升级策略
- 支持增量更新，不影响现有数据

## 相关文档

- [IEntityDao使用指南](./entitydao-usage.md) - 数据访问接口详解
- [数据层开发](./data-layer-development.md) - 数据层开发指南
- [数据处理指南](./data-processing.md) - 数据处理指南
- [服务层开发指南](../service/service-layer-development.md) - BizModel开发详解

## 总结

数据库模型设计是Nop平台开发的基础，通过XML定义模型，自动生成数据库表结构、实体类、DAO接口等。

**核心要点**：

1. **模型定义**: 使用XML定义实体、字段、关系
2. **代码生成**: 自动生成实体类、DAO接口等
3. **数据库同步**: 生成SQL脚本，同步数据库结构
4. **关系设计**: 支持一对一、一对多、多对多关系
5. **索引设计**: 合理设计主键、唯一索引、普通索引
6. **性能考虑**: 避免大表、冗余字段，合理使用索引

遵循这些最佳实践，可以设计出高性能、可维护的数据库模型。