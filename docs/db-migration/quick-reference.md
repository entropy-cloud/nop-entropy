# Nop 数据库迁移快速参考

## 文件命名规范

```
{类型前缀}{版本号}__{描述}.migration.xml
```

### 类型前缀
- `V` - 版本化迁移（只执行一次）
- `R` - 可重复迁移（校验和变化时重新执行）

### 示例
```
V1.0.0__init_schema.migration.xml
V1.0.1__add_user_status_column.migration.xml
V1.1.0__add_order_table.migration.xml
R__user_statistics_view.migration.xml
```

## 基本结构

```xml
<?xml version="1.0" encoding="UTF-8"?>
<migration xmlns="/nop/schema/db-migration/migration.xdef"
           version="1.0.0"
           description="迁移描述"
           author="作者">
    
    <!-- 前置条件（可选） -->
    <preconditions>
        <tableExists tableName="t_user" expect="exists"/>
    </preconditions>
    
    <!-- 变更集 -->
    <changeset>
        <!-- 变更操作 -->
        <createTable name="t_user">...</createTable>
    </changeset>
    
    <!-- 回滚定义（可选） -->
    <rollback>
        <changes>
            <dropTable name="t_user"/>
        </changes>
    </rollback>
</migration>
```

## 常用变更类型

### 表操作

```xml
<!-- 创建表 -->
<createTable name="t_user" remark="用户表">
    <columns>
        <column name="user_id" type="VARCHAR" size="32" 
                primaryKey="true" nullable="false"/>
        <column name="user_name" type="VARCHAR" size="100" 
                nullable="false"/>
    </columns>
    <uniqueConstraint name="uk_user_name" columnNames="user_name"/>
    <foreignKey name="fk_xxx" columnNames="xxx" 
                refTableName="t_other" refColumnNames="id"/>
</createTable>

<!-- 删除表 -->
<dropTable name="t_user"/>

<!-- 重命名表 -->
<renameTable oldTableName="t_user" newTableName="t_member"/>
```

### 列操作

```xml
<!-- 添加列 -->
<addColumn tableName="t_user">
    <columns>
        <column name="age" type="INTEGER" defaultValue="0"/>
    </columns>
</addColumn>

<!-- 删除列 -->
<dropColumn tableName="t_user" columnName="age"/>

<!-- 修改列 -->
<alterColumn tableName="t_user" columnName="age"
             newType="BIGINT" newNullable="false"/>
```

### 索引操作

```xml
<!-- 创建索引 -->
<createIndex name="idx_user_name" tableName="t_user" 
             columnNames="user_name"/>

<!-- 创建唯一索引 -->
<createIndex name="uk_email" tableName="t_user" 
             unique="true" columnNames="email"/>

<!-- 删除索引 -->
<dropIndex name="idx_user_name" tableName="t_user"/>
```

### 视图操作

```xml
<!-- 创建视图 -->
<createView name="v_user_stats" remark="用户统计">
    <selectSql>
        SELECT user_id, COUNT(*) as order_count
        FROM t_order
        GROUP BY user_id
    </selectSql>
</createView>

<!-- 删除视图 -->
<dropView name="v_user_stats"/>
```

### SQL 操作

```xml
<!-- 标准 SQL -->
<sql>
    <body>
        UPDATE t_user SET status = 1 WHERE status IS NULL;
    </body>
</sql>

<!-- 数据库特定 SQL -->
<sql>
    <body>
        -- 所有数据库都执行
        CREATE INDEX idx_time ON t_user(create_time);
    </body>
    <dbSpecific>
        <sql dbType="mysql">
            -- MySQL 特有
            ALTER TABLE t_user ENGINE=InnoDB;
        </sql>
        <sql dbType="postgresql">
            -- PostgreSQL 特有
            VACUUM ANALYZE t_user;
        </sql>
    </dbSpecific>
</sql>
```

### 数据操作

```xml
<!-- 插入数据 -->
<insert tableName="t_user">
    <columns>
        <column name="user_id" value="admin"/>
        <column name="user_name" value="管理员"/>
        <column name="status" valueNumeric="1"/>
        <column name="create_time" valueDate="2024-01-01T00:00:00"/>
    </columns>
</insert>

<!-- 更新数据 -->
<update tableName="t_user">
    <columns>
        <column name="status" valueNumeric="0"/>
    </columns>
    <where>user_id = 'test'</where>
</update>

<!-- 删除数据 -->
<delete tableName="t_user">
    <where>status = 0</where>
</delete>
```

### 自定义变更

```xml
<!-- 使用 XPL 脚本 -->
<customChange>
    <implementation>
        <c:script>
            let userDao = inject('dao_t_user');
            let users = userDao.findListByQuery(new QueryBean());
            for (user in users) {
                // 复杂的业务逻辑
                user.status = 1;
                userDao.updateEntity(user);
            }
        </c:script>
    </implementation>
</customChange>
```

### 数据库类型过滤

```xml
<!-- 只在特定数据库上执行 -->
<dbTypeFilter dbTypes="mysql,postgresql">
    <changes>
        <sql>
            <body>CREATE FULLTEXT INDEX idx_ft ON t_user(user_name);</body>
        </sql>
    </changes>
</dbTypeFilter>
```

## 前置条件

```xml
<preconditions>
    <!-- 表存在 -->
    <tableExists tableName="t_user" expect="exists"/>
    
    <!-- 表不存在 -->
    <tableExists tableName="t_old" expect="notExists"/>
    
    <!-- 列存在 -->
    <columnExists tableName="t_user" columnName="email" expect="exists"/>
    
    <!-- 索引存在 -->
    <indexExists indexName="idx_user_name" expect="exists"/>
    
    <!-- 外键存在 -->
    <foreignKeyExists constraintName="fk_user_role" expect="exists"/>
    
    <!-- 自定义条件 -->
    <customCondition expect="true">
        <expression>
            1 == 1
        </expression>
    </customCondition>
</preconditions>
```

## 回滚定义

```xml
<!-- 自动回滚（简单变更会自动生成） -->
<rollback/>

<!-- 手动回滚 -->
<rollback>
    <changes>
        <dropTable name="t_user"/>
    </changes>
</rollback>

<!-- 不可回滚 -->
<rollback impossible="true">
    <comment>数据初始化，不可回滚</comment>
</rollback>
```

## 属性参考

### migration 根元素

| 属性 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| version | string | ✅ | - | 版本号（语义化版本） |
| description | string | ❌ | - | 迁移描述 |
| author | string | ❌ | - | 作者 |
| type | enum | ❌ | versioned | 类型：versioned/repeatable |
| runOn | enum | ❌ | onChange | 运行时机：always/onChange/never |
| failOnError | boolean | ❌ | true | 失败时是否抛出异常 |
| ignore | boolean | ❌ | false | 是否忽略此迁移 |

### column 元素

| 属性 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| name | string | ✅ | - | 列名 |
| type | string | ✅ | - | 数据类型（stdSqlType） |
| size | int | ❌ | - | 列大小 |
| decimalDigits | int | ❌ | - | 小数位数 |
| nullable | boolean | ❌ | true | 是否允许 NULL |
| defaultValue | string | ❌ | - | 默认值 |
| remark | string | ❌ | - | 列注释 |
| primaryKey | boolean | ❌ | false | 是否为主键 |
| autoIncrement | boolean | ❌ | false | 是否自增 |

## 数据类型映射

| stdSqlType | MySQL | PostgreSQL | Oracle | SQL Server |
|------------|-------|------------|--------|------------|
| VARCHAR | VARCHAR | VARCHAR | VARCHAR2 | NVARCHAR |
| INTEGER | INT | INTEGER | NUMBER(10) | INT |
| BIGINT | BIGINT | BIGINT | NUMBER(19) | BIGINT |
| DECIMAL | DECIMAL | NUMERIC | NUMBER | DECIMAL |
| TIMESTAMP | TIMESTAMP | TIMESTAMP | TIMESTAMP | DATETIME2 |
| DATE | DATE | DATE | DATE | DATE |
| TEXT | TEXT | TEXT | CLOB | NVARCHAR(MAX) |
| BOOLEAN | TINYINT(1) | BOOLEAN | NUMBER(1) | BIT |

## 配置选项

```yaml
nop:
  db:
    migration:
      enabled: true                           # 启用迁移
      locations:                              # 迁移文件位置
        - classpath:/db/migration/
      table: _nop_db_migration_history        # 历史表名
      baseline-on-migrate: true               # 首次迁移创建基线
      baseline-version: 0.0.0                 # 基线版本
      out-of-order: false                     # 允许乱序执行
      validate-on-migrate: true               # 迁移前验证
      clean-disabled: true                    # 禁用 clean（生产环境）
```

## 命令行操作

```bash
# 执行迁移
java -jar app.jar --db-migration migrate

# 查看状态
java -jar app.jar --db-migration info

# 回滚（最后1个版本）
java -jar app.jar --db-migration rollback --count 1

# 回滚到指定版本
java -jar app.jar --db-migration rollback --version 1.0.0

# 校验
java -jar app.jar --db-migration validate

# 修复历史表
java -jar app.jar --db-migration repair
```

## 最佳实践

### ✅ 推荐做法

1. **单一职责**：每个迁移只做一件事
2. **语义化版本**：使用主版本.次版本.补丁版本
3. **版本控制**：所有迁移文件纳入版本控制
4. **测试先行**：先在测试环境验证
5. **备份数据**：执行前备份生产数据库
6. **前置条件**：使用 preconditions 避免重复执行
7. **明确回滚**：对于复杂迁移明确定义回滚逻辑

### ❌ 避免的做法

1. **修改已执行的迁移**：创建新的迁移代替
2. **跳号版本**：版本号应连续
3. **一个迁移多个表**：拆分为多个迁移
4. **生产环境 clean**：必须禁用 clean 命令
5. **忘记回滚定义**：至少标记为 impossible
6. **使用数据库特定类型**：使用 stdSqlType

## 常见问题

### Q: 如何处理已执行的迁移需要修改？

**A**: 创建新的迁移文件来修正，不要修改已执行的迁移。

```xml
<!-- 错误做法：修改 V1.0.0 -->
<!-- 正确做法：创建 V1.0.1 -->
<migration version="1.0.1" description="修复用户表">
    <changeset>
        <alterColumn tableName="t_user" columnName="email"
                     newNullable="false"/>
    </changeset>
</migration>
```

### Q: 如何跳过某些环境执行？

**A**: 使用 contexts 属性。

```xml
<migration version="1.0.0">
    <contexts>dev,test</contexts>  <!-- 只在开发和测试环境执行 -->
</migration>
```

### Q: 如何处理大型表的迁移？

**A**: 分批处理，使用自定义脚本。

```xml
<customChange>
    <implementation>
        <c:script>
            let batchSize = 1000;
            let offset = 0;
            while (true) {
                let users = userDao.findPage(offset, batchSize);
                if (users.isEmpty()) break;
                
                for (user in users) {
                    // 处理逻辑
                }
                
                offset += batchSize;
            }
        </c:script>
    </implementation>
</customChange>
```

### Q: 校验和不匹配怎么办？

**A**: 
1. 如果是故意修改：使用 `repair` 命令更新历史表
2. 如果是意外修改：恢复原文件或创建新迁移

```bash
java -jar app.jar --db-migration repair
```

## 调试技巧

### 启用调试日志

```yaml
logging:
  level:
    io.nop.db.migration: DEBUG
```

### 查看迁移历史

```sql
SELECT * FROM _nop_db_migration_history 
ORDER BY executed_at DESC;
```

### 手动标记迁移为已执行

```sql
INSERT INTO _nop_db_migration_history 
(id, version, description, file_name, checksum, success, executed_at)
VALUES 
('uuid', '1.0.0', '初始化', 'V1.0.0__init.migration.xml', 'md5hash', true, NOW());
```
