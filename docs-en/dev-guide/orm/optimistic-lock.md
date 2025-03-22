# Optimal Locking

## Enable Optimistic Locking

In the Excel model, specify the [Data Domain] for the field as version. This will set the entity's versionProp configuration, which should be of type INTEGER or BIGINT.

When updating the database, the version condition is automatically added, and the version is incremented by 1.

If multiple records are updated, the EntityPersisterImpl will throw the `nop.err.orm.update-entity-multiple-rows` exception.
If the update fails and no records are updated, the `nop.err.orm.update-entity-not-found` exception will be thrown.

## Disable Optimistic Locking

The `entity.orm_disableVersionCheckError(true)` method can disable optimistic lock checking for a specific entity. When updating based on versionProp, if the update fails, it won't throw an exception but will set the entity to readonly.
