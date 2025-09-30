# Simplifying GraphQL Queries Using Fragment Definitions

GraphQL requires specifying the returned fields in frontend calls, which can be cumbersome when there are many fields. In this case, we can use the Fragment feature of the GraphQL language to define common field sets and then reference these fragments in queries to simplify them.

## 1. Add selection definitions in XMeta with the `F_` prefix

```xml
<meta>
  <selections>
    <selection id="F_defaults">
      userId, userName, status, relatedRoleList{ roleName}
    </selection>
  </selections>
</meta>
```

* Here we stipulate that only fragment definitions with the `F_` prefix are accessible to the frontend. selection also has other uses.
* If `F_defaults` is not configured, it will be automatically inferred based on all non-lazy fields of the GraphQL type. If explicitly specified, the specified content takes precedence.

## 2. Reference fragments in frontend queries

Fragments can be used when invoking backend services via GraphQL:

```graphql
query{
   NopAuthUser__findList{
     ...F_defaults, groupMappings{...F_defaults}
   }
}
```

Alternatively, invoke backend services via REST and use fragments through the `@selection` parameter:

```
/r/NopAuthUser__findList?@selection=...F_defaults,groupMappings
```

* When calling via REST, if the `@selection` parameter is not provided, it is equivalent to returning `F_defaults`.

Under REST, if a selection is only expressed at the object level, it will automatically expand downward.
<!-- SOURCE_MD5:604bf0f126ce3571bde95e61f1f5c8cf-->
