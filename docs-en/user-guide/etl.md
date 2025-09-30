# Nop-CLI Database Import/Export Tool Documentation

## Overview

`nop-cli` provides powerful database import and export capabilities, supporting CSV, CSV.GZ, and SQL formats, and enabling advanced operations such as field renaming, value transformation, and data filtering. The tool is configuration-driven and offers flexible database operations.

## Export Command (export-db)

### Command Syntax

```bash
java -jar nop-cli.jar export-db <config-file> -o=<output-dir> [-s=<state-file>]
```

### Configuration File Structure

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

### Key Configuration Notes

#### Global Configuration

| Parameter            | Type    | Default | Description                                       |
|---------------------|---------|---------|---------------------------------------------------|
| `schemaPattern`     | string  | -       | Database schema pattern (required for Oracle)     |
| `tableNamePattern`  | string  | -       | Table name pattern (e.g., `nop_%`)                |
| `exportAllTables`   | boolean | false   | Whether to export all matching tables             |
| `exportFormats`     | csv-set | -       | Export formats: csv, csv.gz, sql                  |
| `fetchSize`         | int     | -       | JDBC fetchSize (MySQL streaming mode needs a special value) |
| `threadCount`       | int     | 0       | Number of export threads (0=auto)                 |
| `batchSize`         | int     | 0       | Batch processing size                             |
| `streaming`         | boolean | true    | Whether to use streaming mode for export          |
| `excludeTableNames` | csv-set | -       | List of table names to exclude                    |

#### JDBC Connection Configuration

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

#### Table-Level Configuration

| Parameter           | Type     | Default | Description                                 |
|--------------------|----------|---------|---------------------------------------------|
| `name`             | string   | required| Target table name (recommended lowercase)   |
| `from`             | string   | same as name | Source table name                        |
| `exportAllFields`  | boolean  | true    | Whether to export all fields                |
| `concurrency`      | int      | -       | Table-level concurrency                     |
| `sql`              | xpl-sql  | -       | Custom SQL query to replace table export    |
| `transformExpr`    | xpl      | -       | Row-level transformation expression         |

#### Field Configuration

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

- `stdSqlType` (Standard SQL Type):
- Purpose: Explicitly specifies which JDBC standard type to use when reading data from the database. This addresses mapping issues between native database types and JDBC standard types.
- Example (Oracle DATE): Oracle’s native `DATE` type by default (via `getObject()`) will be read as Oracle TIMESTAMP. By specifying `stdSqlType`, you can force reading as the desired JDBC type:
  - `stdSqlType = DATETIME` -> returns `java.time.LocalDateTime`
  - `stdSqlType = TIMESTAMP` -> returns `java.sql.Timestamp`
  - `stdSqlType = DATE` -> returns `java.time.LocalDate`

- `stdDataType` (Standard Data Type):
- Purpose: Specifies into which Java data type the value read from the database (already a Java object) should be converted/exported. This is used when the physical storage format (byte-level representation in the database) does not match the logical data type.
- Example: If a field is stored as `VARCHAR` in the database (so `stdSqlType` is typically `VARCHAR` and the read result is `String`), but logically it represents an integer, you can set `stdDataType = int`. This converts the read `String` into `Integer` or `int` before export.
- Configuration rules: This property is optional. Configure it only when the physical storage format (Java type corresponding to `stdSqlType`) differs from the logical Java data type you ultimately need.

- If `ignore` is set to true, the field will not participate in export processing and will be automatically ignored.
- If `name` and `from` are configured differently, you can rename fields and tables during import/export.

### Export Example

Configuration file: `test.export-db.xml`

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

Execution command:

```bash
java -jar nop-cli.jar export-db test.export-db.xml -o=data
```

## Import Command (import-db)

### Command Syntax

```bash
java -jar nop-cli.jar import-db <config-file> -i=<input-dir> [-s=<state-file>]
```

### Configuration File Structure

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

### Key Configuration Notes

#### Global Configuration

| Parameter            | Type    | Default | Description                                 |
|---------------------|---------|---------|---------------------------------------------|
| `importAllTables`   | boolean | true    | Whether to import all tables in the directory |
| `checkKeyFields`    | boolean | true    | Whether to check record existence based on keyFields |
| `tableNamePattern`  | string  | -       | Table name pattern                          |
| `threadCount`       | int     | 0       | Number of import threads (0=auto)           |
| `batchSize`         | int     | 0       | Batch commit size                           |

#### Table-Level Configuration

| Parameter           | Type     | Default | Description                                   |
|--------------------|----------|---------|-----------------------------------------------|
| `format`           | string   | -       | File format suffix (e.g., csv)                |
| `importAllFields`  | boolean  | true    | Whether to import all fields                  |
| `allowUpdate`      | boolean  | false   | Whether to update if record exists (false=skip) |
| `maxSkipCount`     | int      | -       | Maximum number of records to skip             |
| `keyFields`        | csv-list | -       | List of unique key fields                     |
| `transformExpr`    | xpl      | -       | Row-level transformation expression           |

keyFields are used to check for duplicates when inserting into the database. Existing records will be updated or skipped. By default, de-duplication is based on the primary key.

### Import Example

Configuration file: `test.import-db.xml`

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
        <!-- Only import orders with amount greater than 100 -->
        xpl: return $input.amount > 100
      </filter>
    </table>
  </tables>
</import-db>
```

Execution command:

```bash
# Basic import
java -jar nop-cli.jar import-db test.import-db.xml -i=data

# Import with state saving
java -jar nop-cli.jar import-db test.import-db.xml -i=data -s=import-status.json
```

## Advanced Features

### Data Transformation (XPL Expressions)

- `transformExpr`: Supports data transformation at both field and table levels
- Available variables:
  - `value`: current field value
  - `input`: current row data
- Example: `value.toUpperCase()`

### Conditional Filtering

- `filter`: Supports filtering records during export/import
- Example: `<filter><eq name='status' value='1' /></filter>`

### Custom Processing

- Lifecycle hooks:
  - `beforeExport`/`afterExport`
  - `beforeImport`/`afterImport`
  - `beforeExport` (table-level)/`afterExport` (table-level)

## Best Practices

1. Incremental import: Use the `-s` parameter to save import state to implement Delta imports
2. Large table handling:

- Export: set `streaming="true"` and `fetchSize`
- Import: set `batchSize=1000` and `threadCount=4`

3. Safe transformations: Handle nulls and exceptions in `transformExpr`
4. Performance optimization:

- Use `concurrencyPerTable` to process multiple tables in parallel
- Use `csv.gz` for large files to reduce I/O

## Notes

1. For MySQL streaming export, set streaming=true; the system will automatically set fetchSize to a special value based on the dialect configuration.
2. For Oracle, correctly set `schemaPattern`.

With proper configuration, nop-cli can efficiently handle various database migration, data synchronization, and backup/restore scenarios.
<!-- SOURCE_MD5:ba70b87cb1693ba487beb99405ee59e0-->
