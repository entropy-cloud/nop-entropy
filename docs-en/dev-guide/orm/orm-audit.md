# Operation Audit

## createdBy

In Excel, if the data domain is set to `createdBy/updatedBy`, the current username will be recorded when an entity is created or modified; if the domain is `createTime/updateTime`, the modification time will be recorded.

createdBy stores the value returned by `IContext.getUserRefNo()`, which by default corresponds to userName. If you want to store userId, configure `nop.auth.use-user-id-for-audit-fields` to true.

If there is no user information in the context, SYS is used as the default username, which can be configured via `nop.orm.sys-user-name`.

## Field Change Logging

Adding the audit tag to the entity model enables field change logging. The records are stored in the NopSysChangeLog table and include entity ID, property, old value, and new value.

* Disable this feature via `nop.orm.audit.enabled=false`; it is enabled by default (true)
* Deletion will be recorded as `prop=deleted`, with the new value set to 1
* Inserts are not logged by default unless the entity is tagged with `audit-save`
<!-- SOURCE_MD5:52f0fad0ee4d609907daeac2948260c5-->
