# SQL Data Types
StdSqlType is defined in NopORM to unify field types across all databases. In dialect.xml, you can use the sqlDataType type to map each database’s native types to StdSqlType.

```xml
    <sqlDataType name="DOUBLE" stdSqlType="DOUBLE" alias="DOUBLE PRECISION"/>
```

* alias indicates that this data type has alternative names in the database.

StdSqlType is associated with StdDataType. When generating Java entity classes, the corresponding type is determined by StdDataType. Additionally, you can specify StdDataType for a column independently, which allows the Java property type to differ from the database column type—for example, String in Java while BIGINT in the database.
<!-- SOURCE_MD5:b671cd9f6fb2ed2c5bb6fc303c314925-->
