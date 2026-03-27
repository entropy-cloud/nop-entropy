# Nop 数据库迁移机制设计总结

## 设计成果

本次设计完成了一个契合 Nop 平台的数据库迁移机制，包含以下核心文件：

### 1. 元模型定义

**文件**: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/db-migration/migration.xdef`

**核心特性**:
- 基于 xdef 元模型定义，支持 Nop 平台的 DSL 特性
- 完整的变更类型定义（15+ 种）
- 前置条件支持（6 种检查类型）
- 回滚机制（自动/手动/不可回滚）
- 数据库方言支持

### 2. 文档

**目录**: `docs/db-migration/`

**包含文件**:
- `README.md` - 项目概述和快速开始
- `design.md` - 详细的设计文档
- `quick-reference.md` - 快速参考手册
- `design-summary.md` - 设计总结（本文件）

### 3. 示例文件

**目录**: `nop-kernel/nop-xdefs/src/test/resources/_vfs/nop/schema/db-migration/examples/`

**包含 5 个示例**:
1. `V1.0.0__init_schema.migration.xml` - 基础表结构创建
2. `V1.0.1__add_user_fields.migration.xml` - 添加列和修改
3. `V1.1.0__init_data.migration.xml` - 数据初始化和数据库特定 SQL
4. `R__user_statistics_view.migration.xml` - 可重复迁移（视图）
5. `V1.2.0__complex_data_migration.migration.xml` - 复杂数据迁移和自定义脚本

## 设计亮点

### 1. 结合 Flyway 和 Liquibase 的优点

| Flyway 优点 | Liquibase 优点 | Nop Migration 实现 |
|------------|---------------|-------------------|
| 文件名即版本号 | 数据库无关 | ✅ 文件名包含版本号 |
| 每个迁移一个文件 | 丰富的变更类型 | ✅ 每个文件一个迁移 |
| 简单易用 | 回滚支持 | ✅ 15+ 种变更类型 |
| 自动执行 | 前置条件 | ✅ 完整的回滚机制 |
| | | ✅ 前置条件检查 |
| | | ✅ 数据库方言支持 |

### 2. 深度集成 Nop 平台

**xdef 元模型**:
- 使用 xdef 定义 DSL，自动生成模型类
- 支持模型验证和类型检查
- 与 Nop 平台其他 DSL 一致

**Delta 定制**:
- 支持在不修改基础迁移文件的情况下定制
- 使用 x:extends 机制继承和覆盖
- 符合可逆计算原理

**XPL 脚本**:
- 支持在迁移中使用 XPL 脚本
- 可以访问 Nop 平台的所有服务
- 实现复杂的业务逻辑

**ORM 集成**:
- 可以从 ORM 模型生成迁移文件
- 使用 Nop 的 stdSqlType，数据库无关
- 与 ORM 实体保持一致

### 3. 企业级特性

**可靠性**:
- ✅ 事务保证（每个迁移在独立事务中执行）
- ✅ 校验和验证（检测文件篡改）
- ✅ 前置条件检查（避免执行失败）
- ✅ 回滚支持（自动/手动）
- ✅ 执行历史记录（可追溯）

**灵活性**:
- ✅ 版本化迁移（一次性执行）
- ✅ 可重复迁移（视图、存储过程等）
- ✅ 数据库特定 SQL（优化和特殊功能）
- ✅ 自定义变更（XPL 脚本）
- ✅ 多环境支持（contexts）

**易用性**:
- ✅ 简单的文件命名规范
- ✅ 直观的 XML DSL
- ✅ 自动生成回滚（简单变更）
- ✅ 详细的错误信息
- ✅ 丰富的文档和示例

## 核心设计决策

### 1. 文件命名规范

**决策**: 使用 `{类型前缀}{版本号}__{描述}.migration.xml` 格式

**理由**:
- 类似 Flyway，易于理解和使用
- 版本号在文件名中，便于排序
- 类型前缀区分版本化和可重复迁移
- `.migration.xml` 扩展名明确标识文件类型

### 2. 变更类型设计

**决策**: 提供抽象的变更类型 + 数据库特定 SQL

**理由**:
- 抽象变更类型保证数据库无关性
- 数据库特定 SQL 支持优化和特殊功能
- 与 Liquibase 的设计理念一致
- 符合 Nop 平台的标准 SQL 类型系统

### 3. 回滚策略

**决策**: 自动生成 + 手动定义 + 不可回滚标记

**理由**:
- 简单变更可以自动生成回滚（提升易用性）
- 复杂变更允许手动定义回滚（灵活性）
- 某些变更（如数据删除）可以标记为不可回滚（安全性）

### 4. 前置条件

**决策**: 提供 6 种内置前置条件 + 自定义条件

**理由**:
- 内置条件覆盖常见场景（表/列/索引存在性）
- 自定义条件支持复杂业务逻辑
- 避免重复执行或执行失败

### 5. 元模型定义

**决策**: 使用 xdef 定义完整的 DSL

**理由**:
- 契合 Nop 平台设计理念
- 自动生成模型类和验证逻辑
- 支持 Delta 定制和继承
- 与其他 Nop DSL 保持一致

## 使用场景

### 1. 新项目初始化

```xml
<!-- V1.0.0__init_schema.migration.xml -->
<migration version="1.0.0" description="初始化数据库">
    <changeset>
        <createTable name="t_user">...</createTable>
        <createTable name="t_role">...</createTable>
        <createTable name="t_user_role">...</createTable>
    </changeset>
</migration>
```

### 2. 迭代开发

```xml
<!-- V1.1.0__add_order_table.migration.xml -->
<migration version="1.1.0" description="添加订单表">
    <changeset>
        <createTable name="t_order">...</createTable>
        <createIndex name="idx_user_id" tableName="t_order">...</createIndex>
    </changeset>
</migration>
```

### 3. 数据库优化

```xml
<!-- V1.2.0__optimize_indexes.migration.xml -->
<migration version="1.2.0" description="优化索引">
    <changeset>
        <sql>
            <dbSpecific>
                <sql dbType="mysql">
                    ALTER TABLE t_user ENGINE=InnoDB;
                </sql>
                <sql dbType="postgresql">
                    VACUUM ANALYZE t_user;
                </sql>
            </dbSpecific>
        </sql>
    </changeset>
</migration>
```

### 4. 数据迁移

```xml
<!-- V1.3.0__migrate_user_data.migration.xml -->
<migration version="1.3.0" description="迁移用户数据">
    <changeset>
        <customChange>
            <implementation>
                <c:script>
                    // 使用 XPL 脚本进行复杂数据迁移
                </c:script>
            </implementation>
        </customChange>
    </changeset>
</migration>
```

### 5. 视图管理

```xml
<!-- R__user_statistics_view.migration.xml -->
<migration type="repeatable" description="用户统计视图">
    <changeset>
        <dropView name="v_user_stats"/>
        <createView name="v_user_stats">...</createView>
    </changeset>
</migration>
```

## 后续实现建议

### 1. 核心组件

**需要实现的 Java 类**:

```
io.nop.db.migration/
├── model/                          # 模型类（从 xdef 自动生成）
│   ├── DbMigrationModel.java
│   ├── DbChangeset.java
│   └── ...
├── core/                           # 核心引擎
│   ├── MigrationExecutor.java      # 迁移执行器
│   ├── MigrationHistory.java       # 历史记录管理
│   ├── ChecksumCalculator.java     # 校验和计算
│   └── ...
├── changes/                        # 变更实现
│   ├── CreateTableChange.java
│   ├── AddColumnChange.java
│   └── ...
├── preconditions/                  # 前置条件
│   ├── TableExistsPrecondition.java
│   └── ...
├── dialect/                        # 数据库方言
│   ├── MySQLDialect.java
│   ├── PostgreSQLDialect.java
│   └── ...
└── cli/                            # 命令行工具
    └── MigrationCommand.java
```

### 2. Maven 集成

```xml
<plugin>
    <groupId>io.nop</groupId>
    <artifactId>nop-db-migration-maven-plugin</artifactId>
    <version>${nop.version}</version>
    <executions>
        <execution>
            <phase>compile</phase>
            <goals>
                <goal>migrate</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### 3. 自动测试

```java
@Test
public void testMigration() {
    MigrationExecutor executor = new MigrationExecutor();
    executor.setLocations("classpath:/db/migration/");
    executor.migrate();
    
    // 验证迁移结果
    assertNotNull(dao.getTable("t_user"));
    assertEquals(3, dao.count("t_user"));
}
```

### 4. GraphQL API

```graphql
type DbMigration {
    id: String!
    version: String
    description: String
    executedAt: DateTime
    success: Boolean
}

type Query {
    DbMigration_findPage(query: QueryBean): PageBean_DbMigration
}

type Mutation {
    DbMigration_migrate: Boolean
    DbMigration_rollback(version: String): Boolean
    DbMigration_validate: Boolean
}
```

## 实现优先级

### Phase 1: 核心功能（MVP）

1. **元模型编译**
   - 编译 xdef 文件生成模型类
   - 验证 DSL 定义

2. **基础变更类型**
   - createTable
   - dropTable
   - addColumn
   - dropColumn
   - createIndex
   - dropIndex

3. **核心引擎**
   - MigrationExecutor
   - MigrationHistory
   - ChecksumCalculator

4. **命令行工具**
   - migrate 命令
   - info 命令
   - validate 命令

### Phase 2: 增强功能

1. **更多变更类型**
   - alterColumn
   - createView
   - dropView
   - sql
   - insert/update/delete

2. **前置条件**
   - 所有内置前置条件
   - 自定义前置条件

3. **回滚机制**
   - 自动回滚生成
   - 手动回滚定义

4. **数据库方言**
   - MySQL
   - PostgreSQL
   - Oracle
   - SQL Server

### Phase 3: 高级功能

1. **XPL 脚本支持**
   - customChange 实现
   - 与 Nop 平台集成

2. **Delta 定制**
   - 支持 x:extends
   - Delta 目录支持

3. **ORM 集成**
   - 从 ORM 模型生成迁移
   - 与 ORM 实体同步

4. **GraphQL API**
   - 迁移管理 API
   - 状态查询 API

## 总结

本次设计完成了一个功能完整、易于使用、深度集成 Nop 平台的数据库迁移机制。它结合了 Flyway 的易用性和 Liquibase 的数据库无关性，并利用 Nop 平台的可逆计算原理实现了 Delta 定制能力。

**核心价值**:
1. **易用性** - 简单的文件命名和直观的 DSL
2. **可靠性** - 完善的回滚和校验机制
3. **灵活性** - 支持多种变更类型和数据库方言
4. **集成性** - 与 Nop 平台深度集成
5. **扩展性** - 支持自定义变更和前置条件

**下一步**:
1. 实现核心 Java 类（Phase 1）
2. 编写单元测试
3. 创建 Maven 插件
4. 编写用户文档
5. 添加更多数据库方言支持
