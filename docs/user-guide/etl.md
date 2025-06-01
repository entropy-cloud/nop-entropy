# Nop-CLI 数据库导入导出工具文档

## 概述

`nop-cli` 提供强大的数据库导入导出功能，支持 CSV、CSV.GZ 和 SQL 格式，允许字段重命名、值转换、数据过滤等高级操作。本工具基于配置文件驱动，提供灵活的数据库操作能力。

## 导出命令 (export-db)

### 命令格式

```bash
java -jar nop-cli.jar export-db <配置文件> -o=<输出目录> [-s=<状态文件>]
```

### 配置文件结构

```xml

<export-db
  streaming="!boolean=true"
  threadCount="!int=0"
  batchSize="!int=0"
  concurrencyPerTable="int"
  schemaPattern="string"
  tableNamePattern="string"
  exportAllTables="!boolean=false"
  exportFormats="csv-set"
  fetchSize="int">

  <jdbc-connection>...</jdbc-connection>
  <excludeTableNames>csv-set</excludeTableNames>
  <checkExportable>xpl-fn</checkExportable>
  <beforeExport>xpl</beforeExport>
  <afterExport>xpl</afterExport>

  <tables>
    <table name="!string" from="string" concurrency="int" exportAllFields="!boolean=true">
      <filter>xpl-node</filter>
      <sql>xpl-sql</sql>
      <fields>...</fields>
      <transformExpr>xpl</transformExpr>
      <beforeExport>xpl-fn</beforeExport>
      <afterExport>xpl-fn</afterExport>
    </table>
  </tables>
</export-db>
```

### 关键配置说明

#### 全局配置

| 参数                  | 类型      | 默认值   | 说明                            |
|---------------------|---------|-------|-------------------------------|
| `schemaPattern`     | string  | -     | 数据库模式匹配（Oracle 需要指定）          |
| `tableNamePattern`  | string  | -     | 表名匹配模式（如 `nop_%`）             |
| `exportAllTables`   | boolean | false | 是否导出所有匹配表                     |
| `exportFormats`     | csv-set | -     | 导出格式：csv, csv.gz, sql         |
| `fetchSize`         | int     | -     | JDBC fetchSize（MySQL 流模式需特殊值） |
| `threadCount`       | int     | 0     | 导出线程数（0=自动）                   |
| `batchSize`         | int     | 0     | 批量处理大小                        |
| `streaming`         | boolean | true  | 是否使用流模式导出                     |
| `excludeTableNames` | csv-set | -     | 排除的表名列表                       |

#### JDBC 连接配置

```xml

<jdbc-connection
  username="string"
  password="string"
  dialect="string"
  catalog="string"
  maxConnections="int">
  <driverClassName>string</driverClassName>
  <jdbcUrl>string</jdbcUrl>
</jdbc-connection>
```

#### 表级配置

| 参数                | 类型      | 默认值    | 说明              |
|-------------------|---------|--------|-----------------|
| `name`            | string  | **必填** | 目标表名（建议小写）      |
| `from`            | string  | 同 name | 源表名             |
| `exportAllFields` | boolean | true   | 是否导出所有字段        |
| `concurrency`     | int     | -      | 表级并发度           |
| `sql`             | xpl-sql | -      | 自定义 SQL 查询代替表导出 |
| `transformExpr`   | xpl     | -      | 行数据转换表达式        |

#### 字段配置

```xml

<field
  name="!string"
  from="string"
  stdDataType="std-data-type"
  stdSqlType="std-sql-type"
  ignore="!boolean=false">
  <transformExpr>xpl</transformExpr>
</field>
```

* **`stdSqlType` (标准 SQL 类型):**
* **作用：** 明确指定使用哪种 **JDBC 标准类型** 从数据库读取数据。这解决了数据库原生类型与 JDBC 标准类型之间的映射问题。
* **示例 (Oracle DATE):** Oracle 的原生 `DATE` 类型默认（通过 `getObject()`）会被读取为 Oracle的TIMESTAMP。通过指定
  `stdSqlType` 可以强制使用期望的 JDBC 类型读取：
  *   `stdSqlType = DATETIME` -> 返回 `java.time.LocalDateTime`
  *   `stdSqlType = TIMESTAMP` -> 返回 `java.sql.Timestamp`
  *   `stdSqlType = DATE` -> 返回 `java.time.LocalDate`

* **`stdDataType` (标准数据类型):**
* **作用：** 指定将 **从数据库读取到的值** (已经是 Java 对象) **转换/导出** 为何种 **Java 数据类型**
  。这用于处理存储格式（数据库中的字节表示）与逻辑数据类型不一致的情况。
* **示例：** 如果某个字段在数据库中以 `VARCHAR` 类型存储（因此 `stdSqlType` 通常是 `VARCHAR`，读取结果为 `String`
  ），但逻辑上它表示一个整数，则可以配置 `stdDataType = int`。这将把读取到的 `String` 转换为 `Integer` 或 `int` 后再导出。
* **配置规则：** 此属性是 **可选的**。仅在数据库存储的物理格式 (`stdSqlType` 对应的 Java 类型) 与你最终需要使用的逻辑 Java
  数据类型不一致时才需要配置。

* ignore 如果设置为true，则该字段不会参与导出导出处理，会被自动忽略。
* 如果name和from配置不同，可以实现导入导出时进行字段和表的重命名。

### 导出示例

**配置文件：`test.export-db.xml`**

```xml

<export-db
  tableNamePattern="nop_%"
  exportAllTables="true"
  exportFormats="csv,csv.gz"
  fetchSize="1000">

  <jdbc-connection>
    <driverClassName>com.mysql.cj.jdbc.Driver</driverClassName>
    <jdbcUrl>jdbc:mysql://localhost:3306/test</jdbcUrl>
    <username>root</username>
    <password>123456</password>
  </jdbc-connection>

  <excludeTableNames>nop_temp_table,nop_audit_log</excludeTableNames>

  <tables>
    <table name="customers" exportAllFields="false">
      <fields>
        <field name="id" from="customer_id"/>
        <field name="name">
          <transformExpr>value.toUpperCase()</transformExpr>
        </field>
        <field name="email" ignore="true"/>
      </fields>
    </table>
  </tables>
</export-db>
```

**执行命令：**

```bash
java -jar nop-cli.jar export-db test.export-db.xml -o=data
```

## 导入命令 (import-db)

### 命令格式

```bash
java -jar nop-cli.jar import-db <配置文件> -i=<输入目录> [-s=<状态文件>]
```

### 配置文件结构

```xml

<import-db
  threadCount="!int=0"
  batchSize="!int=0"
  concurrencyPerTable="int"
  schemaPattern="string"
  tableNamePattern="string"
  importAllTables="!boolean=true"
  checkKeyFields="!boolean=true">

  <jdbc-connection>...</jdbc-connection>
  <excludeTableNames>csv-set</excludeTableNames>
  <checkImportable>xpl-fn</checkImportable>
  <beforeImport>xpl</beforeImport>
  <afterImport>xpl</afterImport>

  <tables>
    <table
      name="!string"
      from="string"
      format="string"
      concurrency="int"
      importAllFields="!boolean=true"
      allowUpdate="boolean"
      maxSkipCount="int">

      <keyFields>csv-list</keyFields>
      <filter>xpl-fn</filter>
      <fields>...</fields>
      <transformExpr>xpl</transformExpr>
      <beforeImport>xpl-fn</beforeImport>
      <afterImport>xpl-fn</afterImport>
    </table>
  </tables>
</import-db>
```

### 关键配置说明

#### 全局配置

| 参数                 | 类型      | 默认值  | 说明                    |
|--------------------|---------|------|-----------------------|
| `importAllTables`  | boolean | true | 是否导入目录下所有表            |
| `checkKeyFields`   | boolean | true | 是否根据 keyFields 检查记录存在 |
| `tableNamePattern` | string  | -    | 表名匹配模式                |
| `threadCount`      | int     | 0    | 导入线程数（0=自动）           |
| `batchSize`        | int     | 0    | 批量提交大小                |

#### 表级配置

| 参数                | 类型       | 默认值   | 说明                |
|-------------------|----------|-------|-------------------|
| `format`          | string   | -     | 文件格式后缀（如 csv）     |
| `importAllFields` | boolean  | true  | 是否导入所有字段          |
| `allowUpdate`     | boolean  | false | 存在时是否更新（false=忽略） |
| `maxSkipCount`    | int      | -     | 最大跳过记录数           |
| `keyFields`       | csv-list | -     | 唯一键字段列表           |
| `transformExpr`   | xpl      | -     | 行数据转换表达式          |

keyFields用于插入数据库的时候进行重复性检查。已经存在的记录会进行更新或者跳过。取胜情况下会按照主键判重。

### 导入示例

**配置文件：`test.import-db.xml`**

```xml

<import-db importAllTables="true" checkKeyFields="true">

  <jdbc-connection>
    <driverClassName>org.postgresql.Driver</driverClassName>
    <jdbcUrl>jdbc:postgresql://localhost:5432/prod</jdbcUrl>
    <username>admin</username>
    <password>secret</password>
  </jdbc-connection>

  <tables>
    <table
      name="orders"
      format="csv.gz"
      importAllFields="false"
      allowUpdate="true"
      maxSkipCount="100">

      <keyFields>order_id,customer_id</keyFields>

      <fields>
        <field name="order_date"/>
        <field name="status" from="order_status"/>
      </fields>

      <filter>
        <!-- 只导入金额大于100的订单 -->
        xpl: return $input.amount > 100
      </filter>
    </table>
  </tables>
</import-db>
```

**执行命令：**

```bash
# 基本导入
java -jar nop-cli.jar import-db test.import-db.xml -i=data

# 带状态保存的导入
java -jar nop-cli.jar import-db test.import-db.xml -i=data -s=import-status.json
```

## 高级功能

### 数据转换 (XPL表达式)

- `transformExpr`：支持在字段级和表级进行数据转换
- 可用变量：
  - `value`：当前字段值
  - `input`：当前行数据
- 示例：`value.toUpperCase()`

### 条件过滤

- `filter`：支持在导出/导入时过滤记录
- 示例：`<filter><eq name='status' value='1' /></filter>`

### 自定义处理

- 生命周期钩子：
  - `beforeExport`/`afterExport`
  - `beforeImport`/`afterImport`
  - `beforeExport`（表级）/`afterExport`（表级）

## 最佳实践

1. **增量导入**：结合 `-s` 参数保存导入状态，实现增量导入
2. **大表处理**：

- 导出：设置 `streaming="true"` 和 `fetchSize`
- 导入：设置 `batchSize=1000` 和 `threadCount=4`

3. **安全转换**：在 `transformExpr` 中处理空值和异常
4. **性能优化**：

- 使用 `concurrencyPerTable` 并行处理多个表
- 大文件使用 `csv.gz` 格式减少 IO

## 注意事项

1. MySQL 流模式导出需要设置streaming=true，系统会根据dialect中的配置自动设置fetchSize为特殊值。
2. Oracle 需要正确设置 `schemaPattern`

通过合理配置，nop-cli 可以高效处理各种数据库迁移、数据同步和备份恢复场景。
