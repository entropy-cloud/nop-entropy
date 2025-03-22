# SQL Data Types
NopORM defines `StdSqlType`, which unifies field types across all databases. In `dialect.xml`, you can map database-specific types to `StdSqlType` using the `sqlDataType` type.

```xml
    <sqlDataType name="DOUBLE" stdSqlType="DOUBLE" alias="DOUBLE PRECISION"/>
```

* Alias indicates another name for this data type in the database.

The `StdSqlType` is related to `StdDataType`, which determines the corresponding Java entity class type during generation. Additionally, you can specify `StdSqlType` individually for columns. This ensures that Java property types may not match database column types, such as a String in Java versus a BIGINT in the database.
