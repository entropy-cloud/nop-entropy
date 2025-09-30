# Logical Deletion

## 1. Data Model Configuration
In the Excel model, specify fields with domain delFlag and delVersion, of Boolean and Long types respectively.

During logical deletion, set delFlag to 1 and delVersion to the current timestamp. When the record exists normally, both values are set to 0.

deleteVersionProp is used to avoid conflicts where the unique key of a logically deleted record still exists and clashes with the unique key of a normal entity.

That is, suppose the entity has a property name that must be unique. When logical deletion is enabled, you need to define a composite unique key on the fields name and deleteVersion.

When creating a new record, deleteVersion is 0. Upon logical deletion, deleteVersion is set to the timestamp at the time of deletion. This allows inserting new records with the same name later without conflicting with records that have been logically deleted.

## deleteVersionProp
If deleteVersionProp is set but deleteFlagProp is not, deleteFlagProp will automatically be set to deleteVersionProp. In this case, the initial setting is deleteVersion=0, and upon deletion deleteVersion is set to the timestamp.

## 2. EQL Queries
By default, EQL queries and association collection queries both take logical deletion into account and only query records where `delFlag=0`.

Both QueryBean and SQL objects support the disableLogicalDelete setting to disable the logical-deletion filter.

IOrmEntity also provides the `orm_disableLogicalDelete()` method, which allows actually performing a physical delete; otherwise, `session.delete(entity)` will only set the entity’s deleteFlag property.

## Access Control

By default, records marked as logically deleted cannot be read via CrudBizModel’s get/batchGet/findPage methods.

CrudBizModel provides dedicated methods such as deleted_get/deleted_findPage/recoverDeleted for managing records that have been logically deleted.
These methods are disabled by default; accessing them will throw the nop.err.biz.not-allow-get-deleted exception.

Configuring biz:allowGetDeleted=true at the root node of the meta file lifts this restriction. At that point, in principle, you should use the operation-permission mechanism to enforce access restrictions and ensure that only certain roles can call these methods.
<!-- SOURCE_MD5:170878a7cd8fc664c455314ef64a7e7d-->
