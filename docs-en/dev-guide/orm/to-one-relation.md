# One-to-One or One-to-Many Association

In an Excel data model, you only need to configure a one-to-many relationship from the child table to the parent table using the **to-one** association:
- If you need to add a collection property on the parent entity, simply set the **relatedPropName** for the **to-one** association.

For example:
- In the `nop_auth_user_role` table, the `userId` field is associated with the `nop_auth_user` table. You only need to add a **to-one** association in the configuration of `nop_auth_user_role`, setting `relatedPropName` as "user".

Example code for the generated entity class:

```java
class NopAuthUserRole {
    String userId;
    NopAuthUser user;

    // ...
}

class NopAuthUser {
    // ...
    Set<NopAuthUserRole> roleMappings;
}
```

## Avoiding Circular Dependencies
To prevent circular dependencies between data tables, you can set `ignoreDepends="true"` in the **to-one** association configuration. This corresponds to setting the **Ignore Association** property to TRUE in the Excel model, such as in the `app.orm.xml` file for relationships between User and Department tables.

## Supporting Association Queries
After defining the association properties, you can use association queries directly in the backend. However, to expose GraphQL, you need to configure it in the meta file:
- This is important for security reasons to avoid exposing too many functionalities to the frontend, which could lead to security vulnerabilities.
- For example, if the frontend keeps querying large tables via complex associations, this could be a security risk.

### Adding Queryable and Sortable Tags to Associations
- If the **to-one** association has `queryable="true"` and `sortable="true"`, it means all properties of the associated object can be queried.
- If `sortable="true"`, it means these properties can also be used as sorting conditions.

### Configuring Individual Queryable Fields
- If you don't want to expose all properties of an association, you can configure them individually in the meta file.

For example, see the configuration in `NopAuthOpLog.xmeta`:
```xml
<meta>
    <props>
        <prop name="session.loginAddr" displayName="Login Address" queryable="true" sortable="true">
        </prop>
    </props>
</meta>
```
- `queryable="true"` indicates the field is queryable.
- `sortable="true"` indicates the field can be used for sorting.

You can also set properties like `insertable`, `updatable`, etc., in the meta file.

### Using Association Properties in the Frontend
- In the frontend's XView model, you can use fields like `session.loginAddr` directly.

For example, in an XView grid:

```xml
<grid>
    <cols>
        <col id="sessionId"/>
        <col id="session.loginAddr" sortable="true"/>
    </cols>
</grid>
```

- `sortable="true"` for the column indicates whether it can be sorted in the frontend.
- The order of fields is preserved based on the Nop platform's Delta merging strategy, which prioritizes node order as much as possible.

For detailed information on overriding or extending this behavior, refer to [x-override.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/dev-guide/xlang/x-override.md).
