# Operation Audit

## createdBy

In Excel, if the data domain is set to `createdBy/updatedBy`, then when creating or modifying entities, the current username will be recorded. If the data domain is set to `createTime/updateTime`, the modification time will be recorded.

The `createdBy` will store the value returned by `IContext.getUserRefNo()`. This value defaults to the `userName`. If you wish to store the `userId`, you need to configure `nop.auth.use-user-id-for-audit-fields` as true.

If there is no user information available in the context, it will use the default `SYS_USER_NAME`. You can configure this via `nop.orm.sys-user-name`.
