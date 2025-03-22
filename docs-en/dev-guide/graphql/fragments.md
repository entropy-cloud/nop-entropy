# Utilize Fragment to Simplify GraphQL Queries

GraphQL requires specifying the returned fields in the frontend. When dealing with a large number of fields, this can become cumbersome. At this point, we can leverage the `Fragment` functionality in GraphQL to define common field sets and then reference these Fragments during queries, thereby simplifying the process.

## 1. Define Selections in XMeta with `F_` Prefix

In XMeta, we can add selection definitions by using the `F_` prefix.

```xml
<meta>
  <selections>
    <selection id="F_defaults">
      userId, userName, status, relatedRoleList{ roleName }
    </selection>
  </selections>
</meta>
```

Here, we have defined a selection with the ID `F_defaults`, which includes fields like `userId`, `userName`, `status`, and `relatedRoleList` with its own subfield `roleName`. The `F_` prefix ensures that only frontend-optimized Fragments are created.

This setup enforces the use of `F_` as a prefix for Fragment definitions, making it accessible to the frontend. Selections also have other uses beyond this example.

If `F_defaults` is not configured, GraphQL will automatically infer all non-lazy fields. If explicitly defined, the specified fields take precedence.

## 2. Reference Fragments in Frontend Queries

When querying the backend using GraphQL, we can reference these Fragments.

```graphql
query {
  NopAuthUser__findList {
    ...F_defaults, groupMappings { ...F_defaults }
  }
}
```

Alternatively, we can use REST to call the backend service and include `@selection` parameters to utilize Fragments.

```
/r/NopAuthUser__findList?@selection=...F_defaults,groupMappings
```

If the `@selection` parameter is not provided in a REST call, it defaults to returning `F_defaults`.

In cases where selections are nested within objects (e.g., `groupMappings`), the Fragments will automatically expand accordingly.
