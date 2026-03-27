# Nop 数据库迁移机制设计文档

## 1. 设计目标

### 1.1 核心原则

1. **易用性**：类似 Flyway，每个迁移一个文件，文件名即版本号
2. **数据库无关**：类似 Liquibase，使用抽象的变更类型，支持多数据库方言
3. **Nop 平台集成**：契合 Nop 平台的可逆计算理念，支持 Delta 定制
4. **可靠性**：支持回滚、前置条件检查、校验和验证

### 1.2 与现有方案对比

| 特性 | Flyway | Liquibase | Nop Migration |
|------|--------|-----------|---------------|
| 易用性 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 数据库无关 | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 回滚支持 | ⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| DSL 表达能力 | ⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 差量定制 | ❌ | ❌ | ⭐⭐⭐⭐⭐ |
| XPL 脚本支持 | ❌ | ❌ | ⭐⭐⭐⭐⭐ |

## 2. 核心概念

### 2.1 迁移文件命名规范

```
{类型前缀}{版本号}__{描述}.migration.xml
```

- **类型前缀**：
  - `V` - 版本化迁移（Versioned Migration）：只执行一次
  - `R` - 可重复迁移（Repeatable Migration）：每次校验和变化都重新执行

- **版本号**：语义化版本号，格式为 `主版本.次版本.补丁版本`
  - 示例：`1.0.0`, `1.0.1`, `1.1.0`, `2.0.0`

- **描述**：简短的迁移描述，使用下划线分隔单词

- **示例**：
  ```
  V1.0.0__init_schema.migration.xml
  V1.0.1__add_user_status_column.migration.xml
  V1.1.0__add_order_table.migration.xml
  R__refresh_statistics_view.migration.xml
  ```

### 2.2 迁移类型

#### 2.2.1 版本化迁移（Versioned Migration）

- 只执行一次
- 按版本号顺序执行
- 执行后记录到数据库迁移历史表
- 示例场景：
  - 创建表
  - 添加列
  - 创建索引
  - 数据初始化

#### 2.2.2 可重复迁移（Repeatable Migration）

- 每次校验和变化时重新执行
- 不记录版本号，只记录校验和
- 示例场景：
  - 创建/更新视图
  - 创建/更新存储过程
  - 刷新统计信息

### 2.3 变更类型（Change Types）

| 变更类型 | 说明 | 自动回滚 |
|---------|------|---------|
| `createTable` | 创建表 | ✅ |
| `dropTable` | 删除表 | ✅ |
| `renameTable` | 重命名表 | ✅ |
| `addColumn` | 添加列 | ✅ |
| `dropColumn` | 删除列 | ⚠️ |
| `alterColumn` | 修改列 | ⚠️ |
| `createIndex` | 创建索引 | ✅ |
| `dropIndex` | 删除索引 | ✅ |
| `createView` | 创建视图 | ✅ |
| `dropView` | 删除视图 | ✅ |
| `sql` | 执行 SQL | ❌ |
| `insert` | 插入数据 | ❌ |
| `update` | 更新数据 | ❌ |
| `delete` | 删除数据 | ❌ |
| `customChange` | 自定义变更 | ❌ |

✅ = 自动生成回滚 | ⚠️ = 部分支持 | ❌ = 需手动定义回滚

## 3. 使用示例

### 3.1 基础示例：创建表

文件名：`V1.0.0__create_user_table.migration.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<migration xmlns="/nop/schema/db-migration/migration.xdef"
           version="1.0.0"
           description="创建用户表"
           author="dev-team">
    
    <changeset>
        <createTable name="t_user" remark="用户表">
            <columns>
                <column name="user_id" type="VARCHAR" size="32" 
                        primaryKey="true" nullable="false" remark="用户ID"/>
                <column name="user_name" type="VARCHAR" size="100" 
                        nullable="false" remark="用户名"/>
                <column name="email" type="VARCHAR" size="200" remark="邮箱"/>
                <column name="status" type="INTEGER" 
                        defaultValue="1" remark="状态：1-正常，0-禁用"/>
                <column name="create_time" type="TIMESTAMP" 
                        nullable="false" remark="创建时间"/>
                <column name="update_time" type="TIMESTAMP" 
                        remark="更新时间"/>
            </columns>
            
            <uniqueConstraint name="uk_user_name" columnNames="user_name"/>
            <uniqueConstraint name="uk_email" columnNames="email"/>
        </createTable>
        
        <createIndex name="idx_create_time" tableName="t_user" 
                     columnNames="create_time"/>
    </changeset>
    
    <!-- 回滚定义（可选，不定义会自动生成） -->
    <rollback>
        <changes>
            <dropIndex name="idx_create_time" tableName="t_user"/>
            <dropTable name="t_user"/>
        </changes>
    </rollback>
</migration>
```

### 3.2 带前置条件的迁移

文件名：`V1.0.1__add_status_column.migration.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<migration xmlns="/nop/schema/db-migration/migration.xdef"
           version="1.0.1"
           description="添加用户状态列">
    
    <!-- 前置条件：表必须存在，列不存在 -->
    <preconditions>
        <tableExists tableName="t_user" expect="exists"/>
        <columnExists tableName="t_user" columnName="status" expect="notExists"/>
    </preconditions>
    
    <changeset>
        <addColumn tableName="t_user">
            <columns>
                <column name="status" type="INTEGER" 
                        defaultValue="1" remark="状态：1-正常，0-禁用"/>
            </columns>
        </addColumn>
    </changeset>
    
    <rollback>
        <changes>
            <dropColumn tableName="t_user" columnName="status"/>
        </changes>
    </rollback>
</migration>
```

### 3.3 多数据库方言支持

文件名：`V1.1.0__create_order_table.migration.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<migration xmlns="/nop/schema/db-migration/migration.xdef"
           version="1.1.0"
           description="创建订单表">
    
    <changeset>
        <createTable name="t_order" remark="订单表">
            <columns>
                <column name="order_id" type="VARCHAR" size="32" 
                        primaryKey="true" remark="订单ID"/>
                <column name="user_id" type="VARCHAR" size="32" 
                        nullable="false" remark="用户ID"/>
                <column name="amount" type="DECIMAL" 
                        size="10" decimalDigits="2" remark="金额"/>
                <column name="order_time" type="TIMESTAMP" remark="下单时间"/>
            </columns>
            
            <foreignKey name="fk_user_id" 
                        columnNames="user_id"
                        refTableName="t_user" 
                        refColumnNames="user_id"
                        onDelete="CASCADE"/>
        </createTable>
        
        <!-- 针对不同数据库的特殊处理 -->
        <sql>
            <body>
                -- 标准SQL，所有数据库都执行
                CREATE INDEX idx_order_time ON t_order(order_time);
            </body>
            <dbSpecific>
                <sql dbType="mysql">
                    -- MySQL 特有的优化
                    ALTER TABLE t_order ENGINE=InnoDB;
                </sql>
                <sql dbType="postgresql">
                    -- PostgreSQL 特有的优化
                    CREATE INDEX CONCURRENTLY idx_user_order ON t_order(user_id, order_time);
                </sql>
            </dbSpecific>
        </sql>
    </changeset>
</migration>
```

### 3.4 数据迁移

文件名：`V1.2.0__init_user_data.migration.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<migration xmlns="/nop/schema/db-migration/migration.xdef"
           version="1.2.0"
           description="初始化用户数据">
    
    <changeset>
        <insert tableName="t_user">
            <columns>
                <column name="user_id" value="admin"/>
                <column name="user_name" value="管理员"/>
                <column name="email" value="admin@example.com"/>
                <column name="status" valueNumeric="1"/>
                <column name="create_time" valueDate="2024-01-01T00:00:00"/>
            </columns>
        </insert>
        
        <!-- 批量插入也可以使用 SQL -->
        <sql>
            <body>
                INSERT INTO t_user (user_id, user_name, email, status, create_time)
                VALUES 
                    ('user001', '张三', 'zhangsan@example.com', 1, NOW()),
                    ('user002', '李四', 'lisi@example.com', 1, NOW()),
                    ('user003', '王五', 'wangwu@example.com', 1, NOW);
            </body>
        </sql>
    </changeset>
    
    <!-- 数据迁移通常不可回滚 -->
    <rollback impossible="true"/>
</migration>
```

### 3.5 可重复迁移：视图

文件名：`R__user_statistics_view.migration.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<migration xmlns="/nop/schema/db-migration/migration.xdef"
           type="repeatable"
           description="用户统计视图">
    
    <changeset>
        <dropView name="v_user_statistics"/>
        
        <createView name="v_user_statistics" 
                    remark="用户统计视图">
            <selectSql>
                SELECT 
                    u.user_id,
                    u.user_name,
                    COUNT(o.order_id) as order_count,
                    COALESCE(SUM(o.amount), 0) as total_amount
                FROM t_user u
                LEFT JOIN t_order o ON u.user_id = o.user_id
                GROUP BY u.user_id, u.user_name
            </selectSql>
        </createView>
    </changeset>
</migration>
```

### 3.6 自定义变更（XPL 脚本）

文件名：`V1.3.0__complex_data_migration.migration.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<migration xmlns="/nop/schema/db-migration/migration.xdef"
           version="1.3.0"
           description="复杂数据迁移">
    
    <changeset>
        <customChange>
            <implementation>
                <!-- 使用 XPL 脚本实现复杂的数据迁移逻辑 -->
                <c:script>
                    let users = dao.findListByQuery(
                        new QueryBean().eq('status', 1)
                    );
                    
                    for (user in users) {
                        // 复杂的业务逻辑处理
                        if (user.email == null || user.email == '') {
                            user.email = user.userName + '@default.com';
                            dao.updateEntity(user);
                        }
                    }
                    
                    // 记录迁移日志
                    log.info('迁移了 {} 个用户', users.size());
                </c:script>
            </implementation>
        </customChange>
    </changeset>
</migration>
```

### 3.7 数据库类型过滤

```xml
<?xml version="1.0" encoding="UTF-8"?>
<migration xmlns="/nop/schema/db-migration/migration.xdef"
           version="1.4.0"
           description="数据库特定优化">
    
    <changeset>
        <!-- 只在 MySQL 和 PostgreSQL 上执行 -->
        <dbTypeFilter dbTypes="mysql,postgresql">
            <changes>
                <sql>
                    <body>
                        CREATE INDEX idx_composite ON t_order(user_id, order_time, amount);
                    </body>
                </sql>
            </changes>
        </dbTypeFilter>
        
        <!-- 只在 Oracle 上执行 -->
        <dbTypeFilter dbTypes="oracle">
            <changes>
                <sql>
                    <body>
                        -- Oracle 特有的表空间设置
                        ALTER TABLE t_order MOVE TABLESPACE users;
                    </body>
                </sql>
            </changes>
        </dbTypeFilter>
    </changeset>
</migration>
```

## 4. 执行机制

### 4.1 迁移历史表

Nop 会自动创建迁移历史表来记录已执行的迁移：

```sql
CREATE TABLE _nop_db_migration_history (
    id VARCHAR(32) PRIMARY KEY,
    version VARCHAR(50),                    -- 版本号
    description VARCHAR(200),               -- 描述
    type VARCHAR(20),                       -- 类型：versioned/repeatable
    file_name VARCHAR(200),                 -- 文件名
    checksum VARCHAR(64),                   -- 校验和（MD5）
    author VARCHAR(100),                    -- 作者
    executed_at TIMESTAMP,                  -- 执行时间
    execution_time_ms BIGINT,               -- 执行耗时（毫秒）
    success BOOLEAN,                        -- 是否成功
    error_message TEXT                      -- 错误信息（如果失败）
);
```

### 4.2 执行流程

```
1. 扫描迁移文件
   └─ 从 classpath:/db/migration/ 目录读取所有 *.migration.xml 文件
   
2. 解析和排序
   ├─ 解析 XML 文件为 DbMigrationModel
   ├─ 提取版本号和元数据
   └─ 按版本号排序（版本化迁移）或按名称排序（可重复迁移）
   
3. 校验和计算
   └─ 计算文件内容的 MD5 校验和
   
4. 前置条件检查
   └─ 对每个迁移执行 preconditions 检查
   
5. 执行迁移
   ├─ 检查迁移历史表，跳过已执行的版本化迁移
   ├─ 检查校验和，对变化的可重复迁移重新执行
   └─ 在事务中执行变更集
   
6. 记录历史
   └─ 将执行结果记录到迁移历史表
```

### 4.3 回滚机制

```
1. 自动回滚
   └─ 对于简单的变更（createTable, addColumn, createIndex 等），
      Nop 自动生成对应的回滚 SQL
   
2. 手动回滚
   └─ 对于复杂变更，在 rollback 段中定义回滚逻辑
   
3. 不可回滚
   └─ 标记 rollback impossible="true"，表示此迁移不可回滚
```

## 5. 目录结构

### 5.1 标准目录结构

```
src/main/resources/
└── db/
    └── migration/
        ├── V1.0.0__init_schema.migration.xml
        ├── V1.0.1__add_user_status_column.migration.xml
        ├── V1.1.0__create_order_table.migration.xml
        ├── V1.2.0__init_user_data.migration.xml
        └── R__user_statistics_view.migration.xml
```

### 5.2 多环境支持

```
src/main/resources/
└── db/
    ├── migration/
    │   ├── common/                          # 通用迁移
    │   │   ├── V1.0.0__init_schema.migration.xml
    │   │   └── V1.1.0__add_user_table.migration.xml
    │   ├── dev/                             # 开发环境
    │   │   └── V1.2.0__add_test_data.migration.xml
    │   ├── test/                            # 测试环境
    │   │   └── V1.2.0__add_test_data.migration.xml
    │   └── prod/                            # 生产环境
    │       └── V1.3.0__optimize_indexes.migration.xml
    └── migration-config.xml                 # 迁移配置
```

通过 `contexts` 属性控制：

```xml
<migration version="1.2.0"
           description="添加测试数据">
    <contexts>dev,test</contexts>
    <changeset>
        <!-- 只在 dev 和 test 环境执行 -->
    </changeset>
</migration>
```

## 6. 配置选项

### 6.1 应用配置

在 `application.yaml` 中配置：

```yaml
nop:
  db:
    migration:
      enabled: true                              # 是否启用迁移
      locations:                                 # 迁移文件位置
        - classpath:/db/migration/
      table: _nop_db_migration_history          # 迁移历史表名
      baseline-on-migrate: true                 # 是否在首次迁移时创建基线
      baseline-version: 0.0.0                   # 基线版本号
      out-of-order: false                       # 是否允许乱序执行
      validate-on-migrate: true                 # 是否在迁移前验证
      clean-disabled: true                      # 是否禁用 clean 命令（生产环境必须为 true）
      encoding: UTF-8                           # 文件编码
      placeholder-replacement: true             # 是否启用占位符替换
      placeholders:                             # 自定义占位符
        table_prefix: t_
        schema: myapp
```

### 6.2 运行命令

```bash
# 执行迁移
java -jar app.jar --db-migration migrate

# 查看迁移状态
java -jar app.jar --db-migration info

# 回滚到最后 N 个版本
java -jar app.jar --db-migration rollback --count 1

# 回滚到指定版本
java -jar app.jar --db-migration rollback --version 1.0.0

# 校验迁移文件
java -jar app.jar --db-migration validate

# 修复迁移历史表
java -jar app.jar --db-migration repair
```

## 7. 最佳实践

### 7.1 版本号管理

1. **语义化版本**：使用 `主版本.次版本.补丁版本` 格式
   - 主版本：重大架构变更
   - 次版本：新增功能、表、列
   - 补丁版本：修复、小改动

2. **版本号递增规则**：
   ```
   1.0.0 -> 1.0.1 (小改动)
   1.0.1 -> 1.1.0 (新功能)
   1.1.0 -> 2.0.0 (重大变更)
   ```

3. **避免跳号**：版本号应连续，避免出现 `1.0.0, 1.0.5, 1.1.0` 这样的情况

### 7.2 迁移文件编写

1. **单一职责**：每个迁移文件只做一件事
   ```xml
   <!-- ✅ 好的做法：一个迁移只创建一个表 -->
   <migration version="1.0.0" description="创建用户表">
       <createTable name="t_user">...</createTable>
   </migration>
   
   <!-- ❌ 不好的做法：一个迁移创建多个表 -->
   <migration version="1.0.0" description="初始化所有表">
       <createTable name="t_user">...</createTable>
       <createTable name="t_order">...</createTable>
       <createTable name="t_product">...</createTable>
   </migration>
   ```

2. **使用前置条件**：避免重复执行或执行失败
   ```xml
   <preconditions>
       <tableExists tableName="t_user" expect="exists"/>
       <columnExists tableName="t_user" columnName="email" expect="notExists"/>
   </preconditions>
   ```

3. **明确回滚策略**：对于复杂的迁移，明确定义回滚逻辑
   ```xml
   <rollback>
       <changes>
           <dropTable name="t_user"/>
       </changes>
   </rollback>
   ```

4. **使用数据库无关的类型**：使用 Nop 平台的 `stdSqlType`
   ```xml
   <!-- ✅ 使用标准类型 -->
   <column name="amount" type="DECIMAL" size="10" decimalDigits="2"/>
   
   <!-- ❌ 避免使用数据库特定类型 -->
   <column name="amount" type="NUMERIC(10,2)"/>
   ```

### 7.3 团队协作

1. **版本控制**：所有迁移文件必须纳入版本控制
2. **命名规范**：团队统一迁移文件的命名风格
3. **代码审查**：迁移文件应该像代码一样进行审查
4. **测试环境先行**：先在测试环境验证迁移，再应用到生产环境

### 7.4 生产环境建议

1. **备份**：执行迁移前备份数据库
2. **维护窗口**：在低峰期执行大型迁移
3. **监控**：监控迁移执行时间和资源消耗
4. **回滚计划**：准备好回滚方案
5. **禁用 clean**：生产环境必须设置 `clean-disabled: true`

## 8. 与 Nop 平台集成

### 8.1 Delta 定制

Nop 的数据库迁移支持 Delta 定制，可以在不修改基础产品迁移文件的情况下进行定制：

```
基础产品迁移文件：
/db/migration/V1.0.0__init_schema.migration.xml

定制产品迁移文件（在 _delta 目录下）：
/db/migration/_delta/myapp/V1.0.0__init_schema.migration.xml
```

定制文件可以：
- 继承基础文件并添加变更
- 覆盖基础文件中的某些变更
- 添加新的迁移文件

### 8.2 与 ORM 模型集成

Nop 的 ORM 模型可以自动生成迁移文件：

```bash
# 从 ORM 模型生成迁移文件
nop-cli db-migration gen \
  --orm model/myapp.orm.xml \
  --output db/migration \
  --version 1.0.0
```

### 8.3 与 GraphQL API 集成

可以通过 GraphQL API 查看和管理迁移：

```graphql
query {
  DbMigration_findPage {
    items {
      version
      description
      executedAt
      success
    }
  }
}

mutation {
  DbMigration_migrate
}
```

## 9. 故障排查

### 9.1 常见问题

1. **迁移文件未找到**
   - 检查 `nop.db.migration.locations` 配置
   - 确认文件在 classpath 下
   - 检查文件扩展名是否为 `.migration.xml`

2. **版本冲突**
   - 检查迁移历史表，确认哪些版本已执行
   - 使用 `repair` 命令修复历史表
   - 避免修改已执行的迁移文件

3. **校验和不匹配**
   - 检查迁移文件是否被修改
   - 如果是故意修改，使用 `repair` 命令更新校验和
   - 如果是意外修改，恢复原文件或创建新的迁移

4. **回滚失败**
   - 检查 rollback 定义是否正确
   - 确认数据库状态是否允许回滚
   - 某些变更（如删除列）可能无法完全回滚

### 9.2 日志调试

启用调试日志：

```yaml
logging:
  level:
    io.nop.db.migration: DEBUG
```

查看详细日志：
- 迁移文件加载过程
- 前置条件检查结果
- SQL 执行详情
- 回滚执行过程

## 10. 扩展开发

### 10.1 自定义变更类型

可以实现自定义的变更类型：

```java
@ChangeType("myCustomChange")
public class MyCustomChange extends AbstractChange {
    @Override
    public void execute(ExecutionContext context) {
        // 实现自定义变更逻辑
    }
    
    @Override
    public String generateRollbackSql(ExecutionContext context) {
        // 生成回滚 SQL
        return "DROP TABLE ...";
    }
}
```

### 10.2 自定义前置条件

```java
@PreconditionType("myCustomCondition")
public class MyCustomPrecondition extends AbstractPrecondition {
    @Override
    public boolean check(ExecutionContext context) {
        // 实现自定义条件检查
        return true;
    }
}
```

## 11. 总结

Nop 数据库迁移机制的设计充分考虑了：

1. **易用性**：类似 Flyway 的简单文件命名和执行方式
2. **灵活性**：类似 Liquibase 的数据库无关性和丰富的变更类型
3. **可靠性**：完善的前置条件检查、回滚机制和校验和验证
4. **扩展性**：支持自定义变更类型、前置条件和 XPL 脚本
5. **平台集成**：与 Nop 平台的 ORM、GraphQL、Delta 定制深度集成

通过这个机制，团队可以高效、安全地管理数据库结构的演进，同时保持与 Nop 平台的一致性和可定制性。
