# Database Dialects

NopOrm encapsulates differences across databases through the Dialect model.

## Dialect Inheritance and Customization

[default dialect](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-dao/src/main/resources/_vfs/nop/dao/dialect/default.dialect.xml)

[mysql dialect](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-dao/src/main/resources/_vfs/nop/dao/dialect/mysql.dialect.xml)

[postgresql dialect](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-dao/src/main/resources/_vfs/nop/dao/dialect/postgresql.dialect.xml)

Referencing the examples above, both mysql.dialect.xml and postgresql.dialect.xml inherit from default.dialect.xml. Compared to Hibernate, which constructs Dialect objects programmatically, using dialect model files offers significantly higher information density and a more intuitive expression. More importantly, in postgresql.dialect.xml you can clearly identify the configurations that are added, modified, and removed relative to default.dialect.xml.

Because the entire Nop platform is built on the principles of Reversible Computation, parsing and validating dialect model files can be done by the general DslModelParser, with automatic support for Delta customization. That is, without modifying the default.dialect.xml file or any references to default.dialect.xml (for example, without changing the x:extends attribute in postgresql.dialect.xml), we can add a default.dialect.xml file under the /_delta directory to customize system-built-in model files.

```xml
<!-- /_delta/myapp/nop/dao/dialect/default.dialect.xml -->
<dialect x:extends="raw:/nop/dao/dialect/default.dialect.xml">
  Only the Delta changes need to be described here
</dialect>
```

Delta customization is similar to the overlay fs delta file system in Docker, allowing multiple Delta layers to be stacked. Unlike Docker, Delta customization occurs not only at the file level; it also extends to intra-file delta structural operations. With the help of the xdef meta-model definition, all model files in the Nop platform automatically support Delta customization.

## Adding Support for New Databases
1. Following mysql.selector.yaml, define how to select the appropriate dialect name based on JDBC-returned ProductName and other information.
2. Define a new dialect following mysql.dialect.xml.
3. Define a tag library for generating SQL statements following ddl_mysql.xlib. If no special syntax needs customization, you can remove the tag definitions in it.

Without modifying the Nop platform source code, simply add the files under the corresponding directories beneath the _delta/default directory of a JAR.

Note that in dialect.xml, errorCode needs a standardized mapping. In DialectSQLExceptionTranslator, exception translation is performed based on the errorCode mapping defined in the dialect model, translating database exceptions into standard error codes.

<!-- SOURCE_MD5:e86a34c74e7c20eef2a0f46ff0de2aa6-->
