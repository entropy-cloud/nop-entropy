# Optimistic Locking

## Enable Optimistic Locking

In the Excel model, if you set a field’s 【Data Domain】 to version, the entity’s versionProp configuration will be set; its type should be INTEGER or BIGINT.

When updating the database, a version condition will be added automatically, and version will be incremented by 1 automatically.

If multiple rows are updated, EntityPersisterImpl will throw the `nop.err.orm.update-entity-multiple-rows` exception.
If the update fails and the number of affected rows is 0, the `nop.err.orm.update-entity-not-found` exception will be thrown.

## Disable Optimistic Locking

`entity.orm_disableVersionCheckError(true)` can disable optimistic-lock update checks for the specified entity; when an update based on versionProp fails, no exception will be thrown, but the entity will be set to readonly.

<!-- SOURCE_MD5:829d879553760467317de65475838606-->
