# Nop 数据库迁移机制

## 概述

Nop 数据库迁移机制是一个类似 Flyway/Liquibase 的数据库版本管理工具，专门为 Nop 平台设计。它结合了 Flyway 的易用性和 Liquibase 的数据库无关性，并深度集成到 Nop 平台的可逆计算架构中。

## 核心特性

### 1. 易用性（类似 Flyway）
- ✅ 每个迁移一个文件
- ✅ 文件名即版本号
- ✅ 简单直观的 XML DSL
- ✅ 零配置即可开始

### 2. 数据库无关（类似 Liquibase）
- ✅ 使用抽象的变更类型
- ✅ 支持多数据库方言
- ✅ 自动类型映射
- ✅ 数据库特定 SQL 支持

### 3. Nop 平台集成
- ✅ 基于 xdef 元模型定义
- ✅ 支持 Delta 定制
- ✅ XPL 脚本支持
- ✅ 与 ORM 模型联动

### 4. 企业级特性
- ✅ 完善的回滚机制
- ✅ 前置条件检查
- ✅ 校验和验证
- ✅ 多环境支持
- ✅ 执行历史记录

## 快速开始

### 1. 创建第一个迁移

在 `src/main/resources/db/migration/` 目录下创建文件：

**V1.0.0__init_schema.migration.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<migration xmlns="/nop/schema/db-migration/migration.xdef"
           version="1.0.0"
           description="初始化用户表">
    
    <changeset>
        <createTable name="t_user" remark="用户表">
            <columns>
                <column name="user_id" type="VARCHAR" size="32" 
                        primaryKey="true" nullable="false"/>
                <column name="user_name" type="VARCHAR" size="100" 
                        nullable="false"/>
                <column name="email" type="VARCHAR" size="200"/>
                <column name="create_time" type="TIMESTAMP" 
                        nullable="false"/>
            </columns>
            <uniqueConstraint name="uk_email" columnNames="email"/>
        </createTable>
    </changeset>
</migration>
```

### 2. 配置应用

在 `application.yaml` 中启用迁移：

```yaml
nop:
  db:
    migration:
      enabled: true
      locations:
        - classpath:/db/migration/
```

### 3. 执行迁移

```bash
# 启动应用时自动执行
java -jar app.jar

# 或手动执行
java -jar app.jar --db-migration migrate
```

## 文档

- [设计文档](./design.md) - 详细的设计理念和实现机制
- [快速参考](./quick-reference.md) - 常用语法和命令速查
- [设计总结](./design-summary.md) - 设计决策和实现建议

## 示例

完整的示例文件位于：
`nop-kernel/nop-xdefs/src/test/resources/_vfs/nop/schema/db-migration/examples/`

## 对比其他方案

| 特性 | Flyway | Liquibase | Nop Migration |
|------|--------|-----------|---------------|
| 易用性 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 数据库无关 | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 回滚支持 | ⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| DSL 表达能力 | ⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 差量定制 | ❌ | ❌ | ⭐⭐⭐⭐⭐ |
| XPL 脚本支持 | ❌ | ❌ | ⭐⭐⭐⭐⭐ |
| ORM 集成 | ⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐⭐ |

## 当前状态

### ✅ 已完成

1. **元模型定义** (`migration.xdef`)
   - 位置: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/db-migration/migration.xdef`
   - 完整的迁移元模型定义
   - 支持所有核心变更类型（createTable, alterColumn, sql, 等）
   - 支持前置条件、回滚、数据库特定SQL等高级特性

2. **项目结构** (`nop-db-migration`)
   - 位置: `nop-persistence/nop-db-migration/`
   - Maven 项目结构已创建
   - 基础依赖配置完成

3. **枚举类定义**
   - `MigrationType`: VERSIONED, REPEATABLE
   - `RunOnChange`: ALWAYS, ON_CHANGE, NEVER
   - `PreconditionExpect`: EXISTS, NOT_EXISTS
   - `ForeignKeyAction`: CASCADE, SET_NULL, NO_ACTION, RESTRICT

4. **示例文件**
   - 位置: `nop-kernel/nop-xdefs/src/test/resources/_vfs/nop/schema/db-migration/examples/`
   - 包含 5 个示例迁移文件
   - 涵盖常见使用场景

5. **设计文档**
   - 完整的设计文档和说明

### 🚧 待完成

1. **模型类生成**
   - 需要运行 Maven 构建以从 xdef 生成 Java 模型类
   - 命令: `./mvnw clean install -DskipTests`

2. **核心实现**
   - MigrationRunner: 迁移执行引擎
   - ChangeExecutor: 变更执行器
   - DatabaseDialect: 数据库方言
   - ChecksumCalculator: 校验和计算

3. **CLI 工具**
   - 命令行工具支持
   - 迁移状态查询
   - 手动执行迁移

4. **测试**
   - 单元测试
   - 集成测试
   - 多数据库兼容性测试
