# SQL数据类型
NopORM中定义了StdSqlType，统一所有数据库中的字段类型。在dialect.xml中可以通过sqlDataType类型来将各自数据库中的类型映射到StdSqlType上。

```xml
    <sqlDataType name="DOUBLE" stdSqlType="DOUBLE" alias="DOUBLE PRECISION"/>
```

* alias表示这个数据类型在数据库中还有别的名称。

StdSqlType有关联的StdDataType，生成Java实体类时对应的类型由StdDataType来确定，另外也可以单独为列指定StdDataType，这样可以使得Java中的属性类型和数据库中的列类型不一致，比如说Java中是String，而数据库中是BIGINT

