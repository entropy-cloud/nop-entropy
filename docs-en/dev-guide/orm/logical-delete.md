# Logical Deletion

## 1. Model Configuration
In the Excel model, specify `delFlag` and `delVersion` fields as `Boolean` and `Long` types respectively.

When performing logical deletion, set `delFlag` to 1 and `delVersion` to the current timestamp. In normal existence, both values are set to 0.

Use `deleteVersionProp` to prevent unique key conflicts during logical deletion with existing entities.

This means that if an entity has a property `name` that is required to be unique, you need to create a composite unique key by combining `name` and `delVersion` when enabling logical deletion.

When creating new entries, `delVersion` is set to 0. When performing logical deletion, it is set to the timestamp of the deletion moment. This allows new records with the specified `name` to be inserted without conflicts with already logically deleted records.

## 2. EQL Queries
By default, EQL queries and associated collection queries will consider logic deletion conditions, only querying records where `delFlag=0`.

Both `QueryBean` and SQL objects support `disableLogicalDelete`, which disables the logic deletion filter condition.

The `IOrmEntity` also has a method `orm_disableLogicalDelete()`. If this method is called, it performs actual physical deletion. Otherwise, `session.delete(entity)` only sets the `deleteFlag` property of the entity.
