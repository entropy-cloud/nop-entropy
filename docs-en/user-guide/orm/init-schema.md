# ORM Database Initialization

## Automatically apply incremental updates to the database at startup

* Only automatically performs changes such as adding tables, redefining columns, adding columns, adding unique constraints, removing unique constraints, and redefining unique constraints; it does not delete tables or columns: column deletions cannot be distinguished from renames, and table deletions may lead to accidental deletion of tables outside the comparison scope.
* To enable automatic upgrade, add the dependency io.github.entropy-cloud:nop-dbtool-core to the project and set nop.orm.db-differ.auto-upgrade-database to true
<!-- SOURCE_MD5:0ed0080a1c25fceeb279f54a98497b09-->
