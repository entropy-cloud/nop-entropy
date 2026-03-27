# Nop Platform DB-Migration 模块开发计划

> **文档版本**: 1.0
> **创建日期**: 2026-03-26
> **目标**: 为 Nop 平台实现一个类似 Flyway/Liquibase 的数据库迁移工具，但采用 Nop 平台的模型驱动开发方式

---

## 一、调研结果总结

### 1.1 XDef 代码生成机制调研

#### 核心概念
- **XDef 是 Nop 平台的元模型定义语言**，采用同像约束（homoiconic）设计原则
- 元模型文件的结构与它要约束的最终 XML 实例文件的结构基本一致
- 通过 `xdef:` 命名空间的属性附加约束规则

#### 核心类与组件

| 组件 | 路径 | 作用 |
|------|------|------|
| `IXDefinition` / `XDefinition` | `nop-xlang/src/main/java/io/nop/xlang/xdef/` | XDef 定义接口和实现 |
| `XDefToObjMeta` | `nop-xlang/src/main/java/io/nop/xlang/xdef/translate/` | 将 XDef 转换为 ObjMeta 的转换器 |
| `XCodeGenerator` | `nop-codegen/src/main/java/io/nop/codegen/` | 数据驱动的代码生成器 |
| `XDefinitionLoader` | `nop-xlang/src/main/java/io/nop/xlang/xdef/` | XDef 文件加载器 |
| `RegisterModelDiscovery` | `nop-core/src/main/java/io/nop/core/model/registry/` | 模型发现机制 |

#### 代码生成流程

```
┌─────────────────────────────────────────────────────────────────────┐
│  1. 定义 XDef 元模型                                                 │
│     └── /nop/schema/db-migration/migration.xdef                     │
│                                                                      │
│  2. 注册模型加载器                                                   │
│     └── xdef.register-model.xml 或模块的 register-model.xml         │
│                                                                      │
│  3. XDefToObjMeta 转换                                               │
│     └── 将 XDef 转换为 ObjMeta（Java 对象模型）                      │
│                                                                      │
│  4. XCodeGenerator 代码生成                                          │
│     └── 使用 .xgen 模板文件生成 Java 代码                           │
│                                                                      │
│  5. 编译和注册                                                       │
│     └── mvn install 自动触发代码生成和编译                          │
└─────────────────────────────────────────────────────────────────────┘
```

#### 关键 XDef 属性

| 属性 | 说明 | 示例 |
|------|------|------|
| `xdef:name` | Java 类名映射 | `xdef:name="DbMigrationModel"` |
| `xdef:bean-package` | Java 包名 | `xdef:bean-package="io.nop.db.migration.model"` |
| `xdef:body-type` | 子节点类型 | `list/set/map/union/string` |
| `xdef:key-attr` | 列表节点的 key 属性 | `xdef:key-attr="id"` |
| `xdef:unique-attr` | 唯一属性约束 | `xdef:unique-attr="name"` |
| `xdef:ref` | 引用已定义的结构 | `xdef:ref="ColumnDefinition"` |

#### 扩展点

1. **x:extends 继承机制**：通过 `x:extends` 继承和重写已有定义
2. **自定义 Transformer**：可以实现 `IXDefTransformer` 接口自定义转换逻辑
3. **自定义命名空间**：可以定义自己的命名空间扩展 XDef

---

### 1.2 Nop 模块开发模式调研

#### 标准模块结构

```
{moduleName}/
├── {moduleName}-codegen/      # 代码生成模块（仅构建时使用）
│   ├── postcompile/
│   │   └── gen-xxx.xgen      # 代码生成脚本
│   └── pom.xml
├── {moduleName}-dao/          # 数据访问层（可选）
│   ├── src/main/java/
│   │   └── io/nop/{module}/dao/
│   │       ├── entity/       # 实体类
│   │       └── mapper/       # Mapper 接口
│   └── src/main/resources/_vfs/nop/{module}/
│       └── orm/              # ORM 模型文件
├── {moduleName}-service/      # 服务层（可选）
│   ├── src/main/java/
│   │   └── io/nop/{module}/service/
│   │       ├── biz/          # BizModel
│   │       └── processor/    # Processor
│   └── src/main/resources/_vfs/nop/{module}/
│       └── beans/            # IoC 配置
├── {moduleName}-web/          # Web 层（可选）
│   └── src/main/resources/_vfs/nop/{module}/
│       ├── pages/            # 页面配置
│       └── model/            # 视图模型
├── {moduleName}-app/          # 应用打包模块（可选）
│   ├── src/main/java/
│   │   └── io/nop/{module}/app/
│   │       └── Main.java     # 启动类
│   └── pom.xml
├── {moduleName}-meta/         # 元数据模块（可选）
│   ├── precompile/
│   │   └── gen-meta.xgen     # 元数据生成脚本
│   └── src/main/resources/_vfs/nop/{module}/
│       └── model/            # XMeta 文件
└── model/                     # 模型文件目录
    └── {moduleName}.orm.xml  # ORM 模型
```

#### 模块注册机制

**1. ICoreInitializer 接口**

```java
public interface ICoreInitializer extends IInitializer {
    /**
     * 初始化优先级
     */
    int initializePriority();
    
    /**
     * 执行初始化逻辑
     */
    void initialize();
    
    /**
     * 销毁逻辑
     */
    void destroy();
}
```

**注册方式**：
- 在 `META-INF/services/io.nop.core.initialize.ICoreInitializer` 文件中声明实现类
- 或在 `beans.xml` 中配置

**2. beans.xml 配置**

```xml
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <bean id="myService" class="io.nop.xxx.service.MyService">
        <property name="dependency" ref="otherService"/>
    </bean>
</beans>
```

**3. _module 文件**

```
src/main/resources/_vfs/nop/{module}/_module
```

用于标识模块根目录，帮助平台发现模块资源。

#### POM 配置模式

**父 POM 依赖管理**：

```xml
<parent>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-entropy</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</parent>
```

**典型模块 POM**：

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-entropy</artifactId>
        <version>2.0.0-SNAPSHOT</version>
    </parent>
    
    <artifactId>nop-db-migration-core</artifactId>
    <packaging>jar</packaging>
    
    <dependencies>
        <!-- Nop 核心依赖 -->
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-core</artifactId>
        </dependency>
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-xlang</artifactId>
        </dependency>
        
        <!-- 数据库相关依赖 -->
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-dao</artifactId>
        </dependency>
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-orm</artifactId>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <!-- 代码生成插件 -->
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

#### 代码生成配置

**precompile/gen-model.xgen**：

```xml
<c:script>
    // 从 XDef 生成 Java 模型类
    codeGenerator.renderModel(
        '/nop/schema/db-migration/migration.xdef',
        '/nop/templates/xdsl', 
        '/', 
        $scope
    );
</c:script>
```

**Maven 生命周期映射**：

| 阶段 | 配置 | 说明 |
|------|------|------|
| generate-sources | precompile | 生成模型类 |
| compile | aop | 生成 AOP 代理类 |
| generate-test-resources | postcompile | 生成测试资源 |

---

### 1.3 现有数据库相关代码调研

#### 已有能力清单

| 模块 | 核心类 | 功能 | 可复用程度 |
|------|--------|------|-----------|
| **nop-dbtool** | `DataBaseUpgrader` | 数据库升级器 | ⭐⭐⭐⭐⭐ 可直接使用 |
| **nop-dbtool** | `OrmDbDiffer` | ORM-数据库差异比较，生成 DDL | ⭐⭐⭐⭐⭐ 可直接使用 |
| **nop-orm** | `DdlSqlCreator` | DDL SQL 创建器 | ⭐⭐⭐⭐⭐ 可直接使用 |
| **nop-orm** | `DataBaseSchemaInitializer` | Schema 初始化 | ⭐⭐⭐⭐ 可参考 |
| **nop-dbtool** | `JdbcMetaDiscovery` | JDBC 元数据发现 | ⭐⭐⭐⭐⭐ 可直接使用 |
| **nop-dbtool** | `OrmModelDiffer` | ORM 模型差异比较 | ⭐⭐⭐⭐ 可参考 |
| **nop-dao** | `DialectManager` | 数据库方言管理 | ⭐⭐⭐⭐⭐ 可直接使用 |
| **nop-dao** | `IDialect` 及其实现 | 多数据库方言支持 | ⭐⭐⭐⭐⭐ 可直接使用 |

#### 关键组件分析

**1. DataBaseUpgrader（数据库升级器）**

```
位置: nop-persistence/nop-dbtool/nop-dbtool-core/src/main/java/io/nop/dbtool/core/upgrade/DataBaseUpgrader.java

功能:
- 根据ORM模型自动生成数据库升级脚本
- 比较ORM模型与现有数据库结构的差异
- 生成增量DDL语句

使用场景:
- 开发期自动同步数据库结构
- 生产环境数据库升级

限制:
- 基于ORM模型，不支持版本化迁移
- 没有迁移历史记录管理
```

**2. DdlSqlCreator（DDL SQL 创建器）**

```
位置: nop-persistence/nop-orm/src/main/java/io/nop/orm/ddl/DdlSqlCreator.java

功能:
- 根据IEntityModel生成CREATE TABLE语句
- 根据IEntityModel生成DROP TABLE语句
- 支持多种数据库方言

关键方法:
- createTable(IEntityModel table): 生成建表SQL
- dropTable(IEntityModel table, boolean ifExists): 生成删表SQL
- createTables(Collection<IEntityModel> tables): 批量生成

数据库方言支持:
- MySQL: ddl_mysql.xlib
- PostgreSQL: ddl_postgresql.xlib
- Oracle: ddl_oracle.xlib
- SQL Server: ddl_sqlserver.xlib
- H2: ddl_h2.xlib
```

**3. OrmDbDiffer（ORM-数据库差异比较）**

```
位置: nop-persistence/nop-dbtool/nop-dbtool-core/src/main/java/io/nop/dbtool/core/diff/OrmDbDiffer.java

功能:
- 比较ORM模型定义与实际数据库结构
- 生成增量DDL语句（ALTER TABLE等）
- 支持表、列、索引、约束的比较

使用场景:
- 数据库结构同步
- 差异分析
- 升级脚本生成
```

**4. JdbcMetaDiscovery（JDBC 元数据发现）**

```
位置: nop-persistence/nop-dbtool/nop-dbtool-core/src/main/java/io/nop/dbtool/core/meta/JdbcMetaDiscovery.java

功能:
- 通过JDBC读取数据库元数据
- 获取表、列、索引、约束信息
- 转换为平台内部的数据结构

关键方法:
- discoverSchema(String schemaName): 发现指定schema的元数据
- getTableMeta(String tableName): 获取表的元数据
```

**5. IDialect（数据库方言接口）**

```
位置: nop-dao/src/main/java/io/nop/dao/dialect/IDialect.java

实现:
- MySQLDialect
- PostgreSQLDialect
- OracleDialect
- SQLServerDialect
- H2Dialect
- SQLiteDialect

功能:
- SQL语法适配
- 数据类型映射
- 分页语句生成
- 批量插入支持
```

#### DDL 模板文件

```
位置: nop-orm/src/main/resources/_vfs/nop/orm/xlib/ddl/

文件:
- ddl_mysql.xlib       # MySQL DDL模板
- ddl_postgresql.xlib  # PostgreSQL DDL模板
- ddl_oracle.xlib      # Oracle DDL模板
- ddl_sqlserver.xlib   # SQL Server DDL模板
- ddl_h2.xlib          # H2 DDL模板

模板标签:
- CreateTable: 建表语句
- DropTable: 删表语句
- CreateIndex: 建索引语句
- DropIndex: 删索引语句
- AddColumn: 加列语句
- AlterColumn: 修改列语句
```

#### 需要新增的部分

| 功能 | 说明 | 优先级 |
|------|------|--------|
| 迁移历史表管理 | 记录已执行的迁移 | P0 |
| 版本号解析和排序 | 语义化版本管理 | P0 |
| 迁移文件发现 | 扫描和加载迁移文件 | P0 |
| 迁移执行引擎 | 按顺序执行迁移 | P0 |
| 校验和计算 | 检测迁移文件变化 | P1 |
| 回滚机制 | 执行回滚操作 | P1 |
| 前置条件检查 | 迁移前的条件验证 | P2 |
| 迁移上下文管理 | 环境、标签过滤 | P2 |

---

### 1.4 已有 migration.xdef 调研

#### 文件位置
```
nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/db-migration/migration.xdef
```

#### 已定义的模型结构

**根模型：DbMigrationModel**

```xml
<migration xdef:name="DbMigrationModel" 
           xdef:bean-package="io.nop.db.migration.model"
           xdef:bean-extends-type="io.nop.xlang.xdsl.AbstractDslModel"
           version="!string"
           description="string"
           author="string"
           type="enum:io.nop.db.migration.MigrationType"
           runOn="enum:io.nop.db.migration.RunOnChange"
           failOnError="!boolean=true"
           ignore="!boolean=false">
```

#### 支持的变更类型

| 变更类型 | XDef 名称 | 说明 |
|---------|-----------|------|
| 创建表 | `CreateTableChange` | 创建新表，包含列、约束、索引 |
| 删除表 | `DropTableChange` | 删除表 |
| 重命名表 | `RenameTableChange` | 重命名表 |
| 添加列 | `AddColumnChange` | 添加新列 |
| 删除列 | `DropColumnChange` | 删除列 |
| 修改列 | `AlterColumnChange` | 修改列定义 |
| 创建索引 | `CreateIndexChange` | 创建索引 |
| 删除索引 | `DropIndexChange` | 删除索引 |
| 创建视图 | `CreateViewChange` | 创建视图 |
| 删除视图 | `DropViewChange` | 删除视图 |
| 执行 SQL | `SqlChange` | 执行自定义 SQL |
| 插入数据 | `InsertDataChange` | 插入数据 |
| 更新数据 | `UpdateDataChange` | 更新数据 |
| 删除数据 | `DeleteDataChange` | 删除数据 |
| 自定义变更 | `CustomChange` | XPL 脚本实现 |
| 执行标记 | `ExecuteMarkChange` | 流程控制 |
| 数据库过滤 | `DbTypeFilterChange` | 按数据库类型过滤 |

#### 前置条件类型

| 类型 | XDef 名称 | 说明 |
|------|-----------|------|
| 表存在检查 | `TableExistsPrecondition` | 检查表是否存在 |
| 列存在检查 | `ColumnExistsPrecondition` | 检查列是否存在 |
| 索引存在检查 | `IndexExistsPrecondition` | 检查索引是否存在 |
| 外键存在检查 | `ForeignKeyExistsPrecondition` | 检查外键是否存在 |
| 自定义条件 | `CustomConditionPrecondition` | XPL 表达式 |

#### 回滚机制

```xml
<rollback xdef:name="RollbackDefinition" impossible="!boolean=false">
    <changes>
        <!-- 与 changeset 相同的结构 -->
    </changes>
</rollback>
```

**策略**：
1. 如果没有定义 rollback，尝试自动生成回滚 SQL（仅限简单变更）
2. 如果定义了 rollback，使用定义的回滚逻辑
3. 如果 `impossible="true"`，标记此迁移不可回滚

#### 示例文件

已在 `nop-xdefs/src/test/resources/_vfs/nop/schema/db-migration/examples/` 目录下创建了示例：

1. `V1.0.0__init_schema.migration.xml` - 初始化 Schema
2. `V1.0.1__add_user_fields.migration.xml` - 添加字段
3. `V1.1.0__init_data.migration.xml` - 初始化数据
4. `V1.2.0__complex_data_migration.migration.xml` - 复杂数据迁移
5. `R__user_statistics_view.migration.xml` - 可重复迁移（视图）

---

## 二、实现方案

### 2.1 模块结构设计

#### 推荐的模块结构

```
nop-db-migration/
├── nop-db-migration-codegen/     # 代码生成模块
│   ├── postcompile/
│   │   └── gen-model.xgen        # 从 migration.xdef 生成模型类
│   └── pom.xml
│
├── nop-db-migration-core/        # 核心实现模块
│   ├── src/main/java/io/nop/db/migration/
│   │   ├── model/               # 生成的模型类（_gen/）
│   │   ├── core/                # 核心实现
│   │   │   ├── MigrationEngine.java          # 迁移执行引擎
│   │   │   ├── MigrationHistoryManager.java  # 历史记录管理
│   │   │   ├── MigrationFileScanner.java     # 文件扫描器
│   │   │   ├── MigrationVersionComparator.java # 版本比较器
│   │   │   └── MigrationExecutor.java        # 迁移执行器
│   │   ├── change/               # 变更执行器
│   │   │   ├── IChangeExecutor.java          # 变更执行接口
│   │   │   ├── CreateTableExecutor.java      # 建表执行器
│   │   │   ├── AddColumnExecutor.java        # 加列执行器
│   │   │   ├── SqlExecutor.java              # SQL执行器
│   │   │   └── ...
│   │   ├── precondition/         # 前置条件检查器
│   │   │   ├── IPreconditionChecker.java
│   │   │   ├── TableExistsChecker.java
│   │   │   └── ...
│   │   ├── rollback/             # 回滚机制
│   │   │   ├── RollbackGenerator.java        # 回滚SQL生成器
│   │   │   └── RollbackExecutor.java         # 回滚执行器
│   │   ├── checksum/             # 校验和计算
│   │   │   └── MigrationChecksumCalculator.java
│   │   ├── exception/            # 异常定义
│   │   │   └── MigrationException.java
│   │   └── initialize/           # 平台集成
│   │       └── DbMigrationInitializer.java   # ICoreInitializer 实现
│   ├── src/main/resources/
│   │   ├── _vfs/nop/db-migration/
│   │   │   ├── _module           # 模块标识
│   │   │   ├── beans/            # IoC 配置
│   │   │   │   └── default.beans.xml
│   │   │   ├── sql/              # SQL 模板
│   │   │   │   └── migration-history.sql-lib.xml
│   │   │   └── xlib/             # XPL 函数库
│   │   │       └── migration.xlib
│   │   └── META-INF/services/
│   │       └── io.nop.core.initialize.ICoreInitializer
│   └── pom.xml
│
├── nop-db-migration-dao/         # DAO 层（迁移历史表）
│   ├── src/main/java/io/nop/db/migration/dao/
│   │   ├── entity/
│   │   │   └── NopDbMigrationHistory.java    # 迁移历史实体
│   │   └── mapper/
│   │       └── NopDbMigrationHistoryMapper.java
│   ├── src/main/resources/_vfs/nop/db-migration/
│   │   └── orm/
│   │       └── app.orm.xml      # ORM 模型定义
│   └── pom.xml
│
├── nop-db-migration-service/     # 服务层（可选）
│   ├── src/main/java/io/nop/db/migration/service/
│   │   ├── biz/
│   │   │   └── MigrationBizModel.java       # GraphQL 服务
│   │   └── processor/
│   │       └── MigrationProcessor.java      # 迁移处理器
│   └── pom.xml
│
├── nop-db-migration-cli/         # 命令行工具（可选）
│   ├── src/main/java/io/nop/db/migration/cli/
│   │   └── MigrationCli.java                # CLI 入口
│   └── pom.xml
│
├── nop-db-migration-app/         # 测试应用（可选）
│   ├── src/main/java/io/nop/db/migration/app/
│   │   └── MigrationAppMain.java            # 启动类
│   └── pom.xml
│
└── model/                         # 模型文件
    └── nop-db-migration.orm.xml  # ORM 模型定义
```

#### 模块依赖关系

```
┌─────────────────────────────────────────────────────────────┐
│                    nop-db-migration-app                      │
│                   (测试应用，可选)                            │
└──────────────────────┬──────────────────────────────────────┘
                       │
        ┌──────────────┼──────────────┐
        │              │              │
        ▼              ▼              ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│  -service    │ │    -cli      │ │              │
│  (可选)      │ │  (可选)      │ │              │
└──────┬───────┘ └──────┬───────┘ │              │
       │                │         │              │
       └────────────────┼─────────┘              │
                        │                        │
                        ▼                        │
              ┌──────────────────┐               │
              │   -core          │◄──────────────┤
              │  (核心实现)      │               │
              └────────┬─────────┘               │
                       │                         │
                       ▼                         │
              ┌──────────────────┐               │
              │   -dao           │◄──────────────┤
              │  (数据访问层)    │               │
              └────────┬─────────┘               │
                       │                         │
        ┌──────────────┼─────────────────────────┤
        │              │                         │
        ▼              ▼                         ▼
┌──────────────┐ ┌──────────────┐     ┌──────────────┐
│   nop-dao    │ │   nop-orm    │     │  nop-xlang   │
│  (数据库)    │ │   (ORM)      │     │  (XLang)     │
└──────────────┘ └──────────────┘     └──────────────┘
        │              │                     │
        └──────────────┼─────────────────────┘
                       │
                       ▼
              ┌──────────────────┐
              │   nop-core       │
              │   (核心)         │
              └──────────────────┘
```

---

### 2.2 核心类设计

#### 2.2.1 MigrationEngine（迁移引擎）

```java
/**
 * 数据库迁移引擎
 * 
 * 职责：
 * 1. 扫描迁移文件
 * 2. 比较迁移历史
 * 3. 按顺序执行待执行的迁移
 * 4. 记录迁移历史
 */
public class MigrationEngine {
    
    private final MigrationHistoryManager historyManager;
    private final MigrationFileScanner fileScanner;
    private final MigrationExecutor executor;
    private final IDialect dialect;
    
    /**
     * 执行数据库迁移
     * 
     * @param context 迁移上下文
     * @return 迁移结果
     */
    public MigrationResult migrate(MigrationContext context) {
        // 1. 扫描迁移文件
        List<DbMigrationModel> migrations = fileScanner.scan(context.getMigrationPaths());
        
        // 2. 获取已执行的迁移
        Set<String> executedVersions = historyManager.getExecutedVersions();
        
        // 3. 过滤待执行的迁移
        List<DbMigrationModel> pendingMigrations = migrations.stream()
            .filter(m -> !executedVersions.contains(m.getVersion()))
            .sorted(MigrationVersionComparator.INSTANCE)
            .collect(Collectors.toList());
        
        // 4. 按顺序执行
        List<MigrationRecord> records = new ArrayList<>();
        for (DbMigrationModel migration : pendingMigrations) {
            // 检查前置条件
            if (!checkPreconditions(migration, context)) {
                continue;
            }
            
            // 执行迁移
            MigrationRecord record = executor.execute(migration, context);
            records.add(record);
            
            // 记录历史
            historyManager.recordMigration(record);
        }
        
        return new MigrationResult(records);
    }
    
    /**
     * 回滚到指定版本
     */
    public MigrationResult rollback(String targetVersion, MigrationContext context) {
        // 实现回滚逻辑
    }
}
```

#### 2.2.2 MigrationHistoryManager（历史记录管理器）

```java
/**
 * 迁移历史记录管理器
 * 
 * 使用数据库表存储迁移历史
 */
public class MigrationHistoryManager {
    
    private final IOrmTemplate ormTemplate;
    
    public static final String TABLE_NAME = "nop_db_migration_history";
    
    /**
     * 获取已执行的迁移版本
     */
    public Set<String> getExecutedVersions() {
        QueryBean query = new QueryBean();
        query.setFilter(FilterBeans.alwaysTrue());
        List<NopDbMigrationHistory> records = ormTemplate.findList(query);
        return records.stream()
            .map(NopDbMigrationHistory::getVersion)
            .collect(Collectors.toSet());
    }
    
    /**
     * 记录迁移执行
     */
    public void recordMigration(MigrationRecord record) {
        NopDbMigrationHistory history = new NopDbMigrationHistory();
        history.setVersion(record.getVersion());
        history.setDescription(record.getDescription());
        history.setChecksum(record.getChecksum());
        history.setExecutionTime(record.getExecutionTime());
        history.setSuccess(true);
        history.setInstalledOn(new Date());
        history.setInstalledBy(record.getInstalledBy());
        
        ormTemplate.save(history);
    }
    
    /**
     * 检查迁移表是否存在，不存在则创建
     */
    public void ensureHistoryTableExists() {
        // 使用 DdlSqlCreator 生成建表 SQL
        // 或者直接执行预定义的 SQL
    }
}
```

#### 2.2.3 MigrationFileScanner（文件扫描器）

```java
/**
 * 迁移文件扫描器
 * 
 * 扫描指定路径下的 .migration.xml 文件
 */
public class MigrationFileScanner {
    
    /**
     * 扫描迁移文件
     * 
     * @param paths 迁移文件路径列表
     * @return 解析后的迁移模型列表
     */
    public List<DbMigrationModel> scan(List<String> paths) {
        List<DbMigrationModel> migrations = new ArrayList<>();
        
        for (String path : paths) {
            IVirtualFile file = VirtualFileSystem.instance().getResource(path);
            if (file.exists() && path.endsWith(".migration.xml")) {
                DbMigrationModel model = loadMigration(file);
                migrations.add(model);
            }
        }
        
        return migrations;
    }
    
    /**
     * 加载迁移文件
     */
    private DbMigrationModel loadMigration(IVirtualFile file) {
        // 使用 DslModelParser 加载
        DslModelParser parser = new DslModelParser();
        return (DbMigrationModel) parser.parseFromResource(file);
    }
}
```

#### 2.2.4 MigrationExecutor（迁移执行器）

```java
/**
 * 迁移执行器
 * 
 * 执行单个迁移文件中的所有变更
 */
public class MigrationExecutor {
    
    private final Map<String, IChangeExecutor> executors;
    private final IDialect dialect;
    
    /**
     * 执行迁移
     */
    public MigrationRecord execute(DbMigrationModel migration, MigrationContext context) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 检查前置条件
            for (IPrecondition precondition : migration.getPreconditions()) {
                if (!checkPrecondition(precondition, context)) {
                    throw new MigrationException("Precondition failed: " + precondition);
                }
            }
            
            // 2. 执行变更集
            for (DbChangeModel change : migration.getChangeset()) {
                executeChange(change, context);
            }
            
            // 3. 计算校验和
            String checksum = MigrationChecksumCalculator.calculate(migration);
            
            // 4. 构建记录
            long executionTime = System.currentTimeMillis() - startTime;
            return new MigrationRecord(
                migration.getVersion(),
                migration.getDescription(),
                checksum,
                executionTime,
                context.getInstalledBy()
            );
            
        } catch (Exception e) {
            throw new MigrationException("Migration failed: " + migration.getVersion(), e);
        }
    }
    
    /**
     * 执行单个变更
     */
    private void executeChange(DbChangeModel change, MigrationContext context) {
        String changeType = change.getType();
        IChangeExecutor executor = executors.get(changeType);
        
        if (executor == null) {
            throw new MigrationException("Unknown change type: " + changeType);
        }
        
        executor.execute(change, context, dialect);
    }
}
```

#### 2.2.5 IChangeExecutor（变更执行器接口）

```java
/**
 * 变更执行器接口
 */
public interface IChangeExecutor {
    
    /**
     * 执行变更
     * 
     * @param change 变更模型
     * @param context 执行上下文
     * @param dialect 数据库方言
     */
    void execute(DbChangeModel change, MigrationContext context, IDialect dialect);
    
    /**
     * 生成回滚 SQL（可选）
     */
    default String generateRollbackSql(DbChangeModel change, IDialect dialect) {
        return null;
    }
}
```

#### 2.2.6 具体变更执行器示例

**CreateTableExecutor**

```java
public class CreateTableExecutor implements IChangeExecutor {
    
    private final DdlSqlCreator ddlCreator;
    
    @Override
    public void execute(DbChangeModel change, MigrationContext context, IDialect dialect) {
        CreateTableChange createTable = (CreateTableChange) change;
        
        // 1. 转换为 IEntityModel
        IEntityModel entityModel = convertToEntityModel(createTable);
        
        // 2. 生成 DDL
        String sql = ddlCreator.createTable(entityModel);
        
        // 3. 执行 SQL
        context.getJdbcTemplate().execute(sql);
    }
    
    @Override
    public String generateRollbackSql(DbChangeModel change, IDialect dialect) {
        CreateTableChange createTable = (CreateTableChange) change;
        return "DROP TABLE IF EXISTS " + createTable.getName();
    }
    
    private IEntityModel convertToEntityModel(CreateTableChange change) {
        // 将 CreateTableChange 转换为 Nop 平台的 IEntityModel
        // 复用 DdlSqlCreator 的能力
    }
}
```

**SqlExecutor**

```java
public class SqlExecutor implements IChangeExecutor {
    
    @Override
    public void execute(DbChangeModel change, MigrationContext context, IDialect dialect) {
        SqlChange sqlChange = (SqlChange) change;
        
        // 1. 获取适合当前数据库的 SQL
        String sql = getSqlForDialect(sqlChange, dialect);
        
        // 2. 是否按分号分割
        if (sqlChange.isSplitStatements()) {
            String[] statements = sql.split(";");
            for (String statement : statements) {
                if (StringHelper.isNotBlank(statement)) {
                    context.getJdbcTemplate().execute(statement.trim());
                }
            }
        } else {
            context.getJdbcTemplate().execute(sql);
        }
    }
    
    private String getSqlForDialect(SqlChange change, IDialect dialect) {
        // 优先使用数据库特定的 SQL
        for (DbSpecificSql specific : change.getDbSpecific()) {
            if (specific.getDbType().equalsIgnoreCase(dialect.getName())) {
                return specific.getBody();
            }
        }
        
        // 否则使用标准 SQL
        return change.getBody();
    }
}
```

---

### 2.3 数据库迁移历史表设计

#### 表结构

```sql
CREATE TABLE nop_db_migration_history (
    id VARCHAR(36) PRIMARY KEY,
    version VARCHAR(100) NOT NULL UNIQUE,        -- 迁移版本号
    description VARCHAR(500),                     -- 迁移描述
    type VARCHAR(20) NOT NULL,                   -- 迁移类型：VERSIONED/REPEATABLE
    checksum VARCHAR(64),                         -- 校验和
    installed_on TIMESTAMP NOT NULL,             -- 安装时间
    execution_time INTEGER NOT NULL,             -- 执行时长（毫秒）
    success BOOLEAN NOT NULL DEFAULT TRUE,       -- 是否成功
    installed_by VARCHAR(100),                   -- 安装人
    context VARCHAR(100),                        -- 执行上下文
    labels VARCHAR(500),                         -- 标签
    error_message TEXT,                          -- 错误信息（失败时）
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_migration_version ON nop_db_migration_history(version);
CREATE INDEX idx_migration_installed_on ON nop_db_migration_history(installed_on);
```

#### ORM 模型定义

```xml
<!-- model/nop-db-migration.orm.xml -->
<orm appName="nop-db-migration" defaultSchema="nop_db_migration">
    <entities>
        <entity name="NopDbMigrationHistory" tableName="nop_db_migration_history">
            <columns>
                <column name="id" stdDomain="string" primary="true"/>
                <column name="version" stdDomain="string" mandatory="true"/>
                <column name="description" stdDomain="string"/>
                <column name="type" stdDomain="string" mandatory="true"/>
                <column name="checksum" stdDomain="string"/>
                <column name="installedOn" code="installed_on" stdDomain="timestamp" mandatory="true"/>
                <column name="executionTime" code="execution_time" stdDomain="int" mandatory="true"/>
                <column name="success" stdDomain="boolean" mandatory="true"/>
                <column name="installedBy" code="installed_by" stdDomain="string"/>
                <column name="context" stdDomain="string"/>
                <column name="labels" stdDomain="string"/>
                <column name="errorMessage" code="error_message" stdDomain="string"/>
                <column name="createTime" code="create_time" stdDomain="timestamp" mandatory="true"/>
            </columns>
            
            <uniqueKey columnNames="version"/>
        </entity>
    </entities>
</orm>
```

---

### 2.4 配置和集成

#### 2.4.1 beans.xml 配置

```xml
<!-- src/main/resources/_vfs/nop/db-migration/beans/default.beans.xml -->
<beans x:schema="/nop/schema/beans.xdef" 
       xmlns:x="/nop/schema/xdsl.xdef">
    
    <!-- 迁移引擎 -->
    <bean id="migrationEngine" class="io.nop.db.migration.core.MigrationEngine">
        <property name="historyManager" ref="migrationHistoryManager"/>
        <property name="fileScanner" ref="migrationFileScanner"/>
        <property name="executor" ref="migrationExecutor"/>
    </bean>
    
    <!-- 历史记录管理器 -->
    <bean id="migrationHistoryManager" 
          class="io.nop.db.migration.core.MigrationHistoryManager">
        <property name="ormTemplate" ref="nopOrmTemplate"/>
    </bean>
    
    <!-- 文件扫描器 -->
    <bean id="migrationFileScanner" 
          class="io.nop.db.migration.core.MigrationFileScanner"/>
    
    <!-- 迁移执行器 -->
    <bean id="migrationExecutor" 
          class="io.nop.db.migration.core.MigrationExecutor">
        <property name="executors">
            <map>
                <entry key="createTable" value-ref="createTableExecutor"/>
                <entry key="dropTable" value-ref="dropTableExecutor"/>
                <entry key="addColumn" value-ref="addColumnExecutor"/>
                <entry key="dropColumn" value-ref="dropColumnExecutor"/>
                <entry key="alterColumn" value-ref="alterColumnExecutor"/>
                <entry key="createIndex" value-ref="createIndexExecutor"/>
                <entry key="dropIndex" value-ref="dropIndexExecutor"/>
                <entry key="createView" value-ref="createViewExecutor"/>
                <entry key="dropView" value-ref="dropViewExecutor"/>
                <entry key="sql" value-ref="sqlExecutor"/>
                <entry key="insert" value-ref="insertDataExecutor"/>
                <entry key="update" value-ref="updateDataExecutor"/>
                <entry key="delete" value-ref="deleteDataExecutor"/>
                <entry key="customChange" value-ref="customChangeExecutor"/>
            </map>
        </property>
    </bean>
    
    <!-- 变更执行器 -->
    <bean id="createTableExecutor" 
          class="io.nop.db.migration.change.CreateTableExecutor"/>
    <bean id="sqlExecutor" 
          class="io.nop.db.migration.change.SqlExecutor"/>
    <!-- 其他执行器... -->
</beans>
```

#### 2.4.2 ICoreInitializer 实现

```java
/**
 * 数据库迁移模块初始化器
 */
public class DbMigrationInitializer implements ICoreInitializer {
    
    @Override
    public int initializePriority() {
        return CoreConstants.INITIALIZER_PRIORITY_ANALYZE + 100;
    }
    
    @Override
    public void initialize() {
        // 1. 注册变更执行器
        registerChangeExecutors();
        
        // 2. 注册前置条件检查器
        registerPreconditionCheckers();
        
        // 3. 自动执行迁移（可选）
        if (isAutoMigrationEnabled()) {
            executeAutoMigration();
        }
    }
    
    @Override
    public void destroy() {
        // 清理资源
    }
    
    private void registerChangeExecutors() {
        // 注册到 IoC 容器
    }
    
    private void registerPreconditionCheckers() {
        // 注册前置条件检查器
    }
    
    private boolean isAutoMigrationEnabled() {
        return AppConfig.getConfigProvider().getConfigValue(
            "nop.db.migration.auto-enabled", 
            Boolean.class, 
            false
        );
    }
    
    private void executeAutoMigration() {
        MigrationEngine engine = AppBeanProvider.getBean(MigrationEngine.class);
        MigrationContext context = buildMigrationContext();
        engine.migrate(context);
    }
}
```

#### 2.4.3 application.yaml 配置

```yaml
# 数据库迁移配置
nop:
  db:
    migration:
      # 是否启用自动迁移
      auto-enabled: true
      
      # 迁移文件路径（支持多个）
      paths:
        - /nop/db-migration/migrations
        
      # 迁移表名
      table-name: nop_db_migration_history
      
      # 失败时的行为
      fail-fast: true
      
      # 执行上下文（dev/test/prod）
      context: dev
      
      # 要执行的标签（逗号分隔）
      labels: base,init
      
      # 是否验证校验和
      validate-checksum: true
```

---

### 2.5 代码生成配置

#### 2.5.1 nop-db-migration-codegen/pom.xml

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-db-migration</artifactId>
        <version>2.0.0-SNAPSHOT</version>
    </parent>
    
    <artifactId>nop-db-migration-codegen</artifactId>
    
    <dependencies>
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-codegen</artifactId>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <configuration>
                    <mainClass>io.nop.codegen.task.CodeGenTask</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

#### 2.5.2 postcompile/gen-model.xgen

```xml
<c:script>
    // 从 migration.xdef 生成 Java 模型类
    codeGenerator.renderModel(
        '/nop/schema/db-migration/migration.xdef',
        '/nop/templates/xdsl', 
        '/', 
        $scope
    );
    
    // 生成 DAO 层代码（迁移历史表）
    codeGenerator.withTargetDir("../nop-db-migration-dao/src/main/java")
        .renderModel(
            '../../nop-db-migration-dao/src/main/resources/_vfs/nop/db-migration/orm/app.orm.xml',
            '/nop/templates/orm-entity', 
            '/', 
            $scope
        );
</c:script>
```

---

## 三、开发计划

### 3.1 阶段一：基础框架（P0，2周）

#### 目标
- 完成核心模块搭建
- 实现基本的迁移执行能力
- 支持 CREATE TABLE、ADD COLUMN、SQL 等基础变更

#### 任务清单

| 任务 | 优先级 | 工作量 | 负责人 | 状态 |
|------|--------|--------|--------|------|
| 创建模块结构 | P0 | 2h | - | 待开始 |
| 配置 pom.xml 和依赖 | P0 | 1h | - | 待开始 |
| 实现代码生成脚本（gen-model.xgen） | P0 | 2h | - | 待开始 |
| 实现 MigrationHistoryManager | P0 | 4h | - | 待开始 |
| 实现 MigrationFileScanner | P0 | 2h | - | 待开始 |
| 实现 MigrationVersionComparator | P0 | 2h | - | 待开始 |
| 实现 MigrationExecutor | P0 | 4h | - | 待开始 |
| 实现 MigrationEngine | P0 | 4h | - | 待开始 |
| 实现 CreateTableExecutor | P0 | 3h | - | 待开始 |
| 实现 AddColumnExecutor | P0 | 2h | - | 待开始 |
| 实现 SqlExecutor | P0 | 2h | - | 待开始 |
| 实现 DbMigrationInitializer | P0 | 3h | - | 待开始 |
| 编写单元测试 | P0 | 4h | - | 待开始 |
| 集成测试 | P0 | 4h | - | 待开始 |

**总计**: 约 39 小时（约 1 周）

---

### 3.2 阶段二：完善变更类型（P1，2周）

#### 目标
- 支持所有定义的变更类型
- 实现回滚机制
- 实现前置条件检查

#### 任务清单

| 任务 | 优先级 | 工作量 | 状态 |
|------|--------|--------|------|
| 实现 DropTableExecutor | P1 | 2h | 待开始 |
| 实现 RenameTableExecutor | P1 | 2h | 待开始 |
| 实现 DropColumnExecutor | P1 | 2h | 待开始 |
| 实现 AlterColumnExecutor | P1 | 3h | 待开始 |
| 实现 CreateIndexExecutor | P1 | 2h | 待开始 |
| 实现 DropIndexExecutor | P1 | 1h | 待开始 |
| 实现 CreateViewExecutor | P1 | 2h | 待开始 |
| 实现 DropViewExecutor | P1 | 1h | 待开始 |
| 实现 InsertDataExecutor | P1 | 2h | 待开始 |
| 实现 UpdateDataExecutor | P1 | 2h | 待开始 |
| 实现 DeleteDataExecutor | P1 | 1h | 待开始 |
| 实现 CustomChangeExecutor | P1 | 4h | 待开始 |
| 实现 DbTypeFilterExecutor | P1 | 2h | 待开始 |
| 实现前置条件检查器（5种） | P1 | 4h | 待开始 |
| 实现回滚机制 | P1 | 6h | 待开始 |
| 实现校验和计算 | P1 | 2h | 待开始 |
| 编写测试用例 | P1 | 8h | 待开始 |

**总计**: 约 46 小时（约 1.5 周）

---

### 3.3 阶段三：高级特性（P2，1周）

#### 目标
- 实现可重复迁移
- 实现上下文过滤
- 实现标签过滤
- 优化性能

#### 任务清单

| 任务 | 优先级 | 工作量 | 状态 |
|------|--------|--------|------|
| 实现可重复迁移（R__ 前缀） | P2 | 4h | 待开始 |
| 实现上下文过滤 | P2 | 2h | 待开始 |
| 实现标签过滤 | P2 | 2h | 待开始 |
| 实现迁移文件校验和验证 | P2 | 2h | 待开始 |
| 实现迁移失败时的错误处理 | P2 | 3h | 待开始 |
| 性能优化（批量执行） | P2 | 4h | 待开始 |
| 日志和监控 | P2 | 2h | 待开始 |
| 文档编写 | P2 | 4h | 待开始 |

**总计**: 约 23 小时（约 3 天）

---

### 3.4 阶段四：工具和集成（P3，1周）

#### 目标
- 提供 CLI 工具
- 提供 GraphQL API
- 提供可视化界面（可选）

#### 任务清单

| 任务 | 优先级 | 工作量 | 状态 |
|------|--------|--------|------|
| 实现 MigrationBizModel（GraphQL 服务） | P3 | 4h | 待开始 |
| 实现 CLI 工具 | P3 | 6h | 待开始 |
| 实现 Web 界面（可选） | P3 | 8h | 待开始 |
| 与 Spring Boot 集成示例 | P3 | 2h | 待开始 |
| 与 Quarkus 集成示例 | P3 | 2h | 待开始 |
| 编写完整文档 | P3 | 4h | 待开始 |

**总计**: 约 26 小时（约 3-4 天）

---

### 3.5 总体时间表

| 阶段 | 内容 | 时间 | 优先级 |
|------|------|------|--------|
| 阶段一 | 基础框架 | 第1-2周 | P0 |
| 阶段二 | 完善变更类型 | 第3-4周 | P1 |
| 阶段三 | 高级特性 | 第5周 | P2 |
| 阶段四 | 工具和集成 | 第6周 | P3 |
| **总计** | **全部功能** | **6周** | - |

---

## 四、使用示例

### 4.1 基本使用

#### 1. 创建迁移文件

```xml
<!-- /nop/db-migration/migrations/V1.0.0__create_user_table.migration.xml -->
<migration xmlns="/nop/schema/db-migration/migration.xdef"
           version="1.0.0"
           description="创建用户表"
           author="dev-team">
    
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
            
            <uniqueConstraint name="uk_user_name" columnNames="user_name"/>
        </createTable>
    </changeset>
    
    <rollback>
        <changes>
            <dropTable name="t_user"/>
        </changes>
    </rollback>
</migration>
```

#### 2. 执行迁移

**方式一：自动执行（推荐）**

```yaml
# application.yaml
nop:
  db:
    migration:
      auto-enabled: true
      paths:
        - /nop/db-migration/migrations
```

启动应用时自动执行迁移。

**方式二：编程方式**

```java
@Inject
MigrationEngine migrationEngine;

public void executeMigration() {
    MigrationContext context = new MigrationContext();
    context.setMigrationPaths(Arrays.asList("/nop/db-migration/migrations"));
    context.setInstalledBy("admin");
    
    MigrationResult result = migrationEngine.migrate(context);
    System.out.println("Executed " + result.getRecords().size() + " migrations");
}
```

**方式三：GraphQL API**

```graphql
mutation {
    Migration_migrate(
        paths: ["/nop/db-migration/migrations"]
        context: "prod"
    ) {
        success
        executedCount
        records {
            version
            description
            executionTime
        }
    }
}
```

**方式四：CLI 工具**

```bash
java -jar nop-db-migration-cli.jar migrate \
  --paths=/nop/db-migration/migrations \
  --context=prod \
  --labels=base,init
```

---

### 4.2 高级用法

#### 1. 使用前置条件

```xml
<migration version="1.1.0" description="添加字段">
    <preconditions>
        <tableExists tableName="t_user" expect="exists"/>
        <columnExists tableName="t_user" columnName="phone" expect="notExists"/>
    </preconditions>
    
    <changeset>
        <addColumn tableName="t_user">
            <columns>
                <column name="phone" type="VARCHAR" size="20"/>
            </columns>
        </addColumn>
    </changeset>
</migration>
```

#### 2. 使用数据库特定 SQL

```xml
<migration version="1.2.0" description="执行数据库特定SQL">
    <changeset>
        <sql>
            <body>INSERT INTO t_config(key, value) VALUES('db_type', 'unknown')</body>
            <dbSpecific>
                <sql dbType="mysql">
                    <body>INSERT INTO t_config(key, value) VALUES('db_type', 'mysql')</body>
                </sql>
                <sql dbType="postgresql">
                    <body>INSERT INTO t_config(key, value) VALUES('db_type', 'postgresql')</body>
                </sql>
            </dbSpecific>
        </sql>
    </changeset>
</migration>
```

#### 3. 使用自定义变更（XPL）

```xml
<migration version="1.3.0" description="复杂数据迁移">
    <changeset>
        <customChange>
            <implementation>
                <c:script>
                    // 使用 XPL 实现复杂逻辑
                    let users = dao.findListByQuery(
                        new QueryBean().setFilter(eq("status", 1))
                    );
                    
                    for (user in users) {
                        user.setDisplayName(user.getUserName() + '_' + user.getUserId());
                        dao.update(user);
                    }
                </c:script>
            </implementation>
        </customChange>
    </changeset>
</migration>
```

#### 4. 可重复迁移

```xml
<!-- R__user_statistics_view.migration.xml -->
<migration type="repeatable"
           description="用户统计视图"
           runOn="onChange">
    <changeset>
        <createView name="v_user_statistics" remark="用户统计视图">
            <selectSql>
                SELECT 
                    u.user_id,
                    u.user_name,
                    COUNT(o.order_id) as order_count,
                    SUM(o.amount) as total_amount
                FROM t_user u
                LEFT JOIN t_order o ON u.user_id = o.user_id
                GROUP BY u.user_id, u.user_name
            </selectSql>
        </createView>
    </changeset>
    
    <rollback>
        <changes>
            <dropView name="v_user_statistics"/>
        </changes>
    </rollback>
</migration>
```

---

## 五、技术决策

### 5.1 为什么不用 Flyway/Liquibase？

| 对比项 | Flyway/Liquibase | Nop DB-Migration |
|--------|------------------|------------------|
| **模型驱动** | ❌ 配置驱动 | ✅ 模型驱动（XDef） |
| **类型安全** | ❌ 字符串配置 | ✅ 强类型模型 |
| **IDE 支持** | ⚠️ 有限 | ✅ 完整的 IDEA 插件支持 |
| **差量定制** | ❌ 不支持 | ✅ 支持 x:extends |
| **平台集成** | ⚠️ 需要适配 | ✅ 原生集成 Nop 平台 |
| **代码生成** | ❌ 不支持 | ✅ 支持从 XDef 生成代码 |
| **扩展性** | ⚠️ Java SPI | ✅ XPL 脚本 + Java |

### 5.2 技术选型

| 技术 | 选型 | 原因 |
|------|------|------|
| 元模型定义 | XDef | Nop 平台标准，支持类型安全和 IDE 提示 |
| 代码生成 | XCodeGenerator | Nop 平台内置，支持增量生成 |
| IoC 容器 | NopIoC | 轻量级，与平台原生集成 |
| 数据库方言 | IDialect | 复用现有实现，已支持多种数据库 |
| DDL 生成 | DdlSqlCreator | 复用现有能力，已成熟稳定 |
| 脚本引擎 | XPL | 平台内置，支持复杂业务逻辑 |

### 5.3 复用现有组件

**高度复用（直接使用）**：
- `DdlSqlCreator`: DDL SQL 生成
- `IDialect` 及其实现：数据库方言
- `OrmDbDiffer`: 差异比较
- `JdbcMetaDiscovery`: 元数据发现

**参考实现（借鉴思路）**：
- `DataBaseUpgrader`: 升级流程
- `DataBaseSchemaInitializer`: 初始化流程

**全新实现**：
- 迁移历史管理
- 版本号解析和排序
- 文件扫描和加载
- 前置条件检查
- 回滚机制

---

## 六、风险和挑战

### 6.1 技术风险

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| DDL 生成的兼容性问题 | 中 | 复用已验证的 DdlSqlCreator，增加测试覆盖 |
| 大型迁移的性能问题 | 中 | 支持批量执行，提供进度反馈 |
| 回滚的可靠性 | 高 | 提供自动回滚生成，允许手动定义回滚 |
| 数据库锁表问题 | 中 | 支持事务控制，提供超时配置 |

### 6.2 业务风险

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 迁移失败导致数据不一致 | 高 | 事务控制，失败自动回滚 |
| 版本冲突 | 中 | 校验和验证，冲突提示 |
| 生产环境误操作 | 高 | 上下文过滤，确认机制 |

---

## 七、后续规划

### 7.1 短期规划（3个月内）

- [ ] 完成核心功能开发（阶段一、二）
- [ ] 提供基本的 CLI 工具
- [ ] 编写完整的使用文档
- [ ] 提供 Spring Boot 集成示例

### 7.2 中期规划（6个月内）

- [ ] 提供 GraphQL API
- [ ] 提供 Web 管理界面
- [ ] 支持迁移文件的在线编辑和验证
- [ ] 提供迁移历史可视化

### 7.3 长期规划（1年内）

- [ ] 支持多数据源迁移
- [ ] 支持云原生数据库（如 TiDB、OceanBase）
- [ ] 提供迁移最佳实践库
- [ ] 支持从 Flyway/Liquibase 迁移

---

## 八、参考资料

### 8.1 Nop 平台文档

- [XDef 核心概念](../05-xlang/xdef-core.md)
- [代码生成基础](../02-architecture/code-generation.md)
- [项目结构说明](../03-development-guide/project-structure.md)
- [BizModel 开发指南](../03-development-guide/bizmodel-guide.md)

### 8.2 外部参考

- [Flyway 官方文档](https://flywaydb.org/documentation/)
- [Liquibase 官方文档](https://www.liquibase.org/get-started)
- [可逆计算理论](https://zhuanlan.zhihu.com/p/64004026)

### 8.3 相关代码

- `nop-dbtool`: 数据库工具模块
- `nop-orm`: ORM 引擎
- `nop-dao`: 数据访问层
- `nop-codegen`: 代码生成器

---

## 附录 A：文件命名规范

### A.1 迁移文件命名

**版本化迁移**：
```
V{version}__{description}.migration.xml

示例：
V1.0.0__init_schema.migration.xml
V1.0.1__add_user_fields.migration.xml
V1.1.0__add_order_table.migration.xml
```

**可重复迁移**：
```
R__{description}.migration.xml

示例：
R__user_statistics_view.migration.xml
R__update_statistics.migration.xml
```

### A.2 版本号规范

采用语义化版本：`主版本.次版本.补丁版本`

- **主版本**: 不兼容的 API 变更
- **次版本**: 向后兼容的功能新增
- **补丁版本**: 向后兼容的问题修正

**排序规则**：
- 按版本号数值比较
- 主版本优先，其次次版本，最后补丁版本
- 示例：1.0.0 < 1.0.1 < 1.1.0 < 2.0.0

---

## 附录 B：错误码定义

| 错误码 | 说明 |
|--------|------|
| `MIGRATION_001` | 迁移文件格式错误 |
| `MIGRATION_002` | 迁移版本号重复 |
| `MIGRATION_003` | 迁移文件不存在 |
| `MIGRATION_004` | 前置条件检查失败 |
| `MIGRATION_005` | 变更执行失败 |
| `MIGRATION_006` | 回滚失败 |
| `MIGRATION_007` | 校验和不匹配 |
| `MIGRATION_008` | 数据库连接失败 |
| `MIGRATION_009` | 不支持的变更类型 |
| `MIGRATION_010` | 迁移历史表不存在 |

---

## 附录 C：检查清单

### C.1 开发前检查

- [ ] 已理解 XDef 元模型定义
- [ ] 已理解 Nop 模块开发模式
- [ ] 已了解现有数据库相关代码
- [ ] 已阅读 migration.xdef 定义
- [ ] 已配置开发环境

### C.2 开发中检查

- [ ] 遵循 Nop 平台代码规范
- [ ] 使用模型驱动开发方式
- [ ] 复用现有组件（DdlSqlCreator、IDialect 等）
- [ ] 编写单元测试
- [ ] 编写集成测试

### C.3 发布前检查

- [ ] 所有测试通过
- [ ] 文档完整
- [ ] 示例代码可运行
- [ ] 性能测试通过
- [ ] 兼容性测试通过

---

**文档结束**
