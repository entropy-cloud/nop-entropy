NopOrm encapsulates differences between different databases using the `Dialect` model.

# Inheritance and Customization of Dialect

- [Default Dialect](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-dao/src/main/resources/_vfs/nop/dao/dialect/default.dialect.xml)
- [MySQL Dialect](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-dao/src/main/resources/_vfs/nop/dao/dialect/mysql.dialect.xml)
- [PostgreSQL Dialect](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-dao/src/main/resources/_vfs/nop/dao/dialect/postgresql.dialect.xml)

Refer to the example above. The `mysql.dialect.xml` and `postgresql.dialect.xml` files both inherit from `default.dialect.xml`. Compared to Hibernate, which constructs a `Dialect` object programmatically, using dialect model files provides higher information density and more intuitive expressions. More importantly, the `postgresql.dialect.xml` file can clearly identify configurations that have been added, modified, or removed compared to `default.dialect.xml`.

Since the entire Nop platform is built on reversible computation principles, the parsing and validation of dialect model files can be handled by the generic `DslModelParser`. This parser supports Delta customization, meaning that you can customize system models without modifying `default.dialect.xml`, including adding a `default.dialect.xml` file in the `/_delta` directory.

```xml
<!-- /_delta/myapp/nop/dao/dialect/default.dialect.xml -->
<dialect x:extends="raw:/nop/dao/dialect/default.dialect.xml">
  Here, only describe the differences in this part
</dialect>
```

Delta customization is similar to Docker's overlay file system, allowing multiple Delta layers to be stacked. Unlike Docker, however, Delta customization extends beyond the file level to include internal delta structural operations. With the help of `xdef` metadata definitions, all model files in the Nop platform automatically support Delta customization.