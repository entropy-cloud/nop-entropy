# Bulk Loading

## GraphQL Loader Mechanism for Dynamic Bulk Data Loading

```javascript
    IEntityDao<NopAuthUser> user = daoProvider.daoFor(NopAuthUser.class);
    List<NopAuthUser> list = user.findAll();
    IServiceContext svcCtx = null; // Service context typically exists in the backend template runtime context
    CompletionStage<Object> future = graphQLEngine.fetchResult(list,
            "NopAuthUser", "...F_defaults,status_label,relatedRoleList", svcCtx);
    output("result.json5", FutureHelper.syncGet(future));
```

The `graphQLEngine.fetchResult` method can dynamically create a GraphQL field selection based on the input object and GraphQL type. This method then executes the GraphQL data loader logic.

By default, the `NopGraphQL` query for entity data will automatically implement bulk loading logic, which corresponds to the code in `OrmEntityRefFetcher` and `OrmEntitySetFetcher`. These classes handle ORM-level relationships and collections by processing associated entities.
