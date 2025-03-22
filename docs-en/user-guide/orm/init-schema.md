# ORM Database Initialization

## Incremental Updates on Startup

* The application automatically performs the following changes during initialization: table creation, field redefinition, unique constraint addition, and unique constraint removal. However, it does not perform table or field deletions because field deletion cannot distinguish between renames and deletes, and table deletion could accidentally delete tables outside of the intended scope.
* Enable automatic upgrades by adding the dependency `io.github.entropy-cloud:nop-dbtool-core` to your project and setting `nop.orm.db-differ.auto-upgrade-database` to `true`.
