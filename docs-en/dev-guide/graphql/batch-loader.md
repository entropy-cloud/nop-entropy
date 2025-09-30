
# Batch Loading

## Applying GraphQL's Loader mechanism to invocation results to implement dynamic batch data loading

```javascript
    IEntityDao<NopAuthUser> user = daoProvider.daoFor(NopAuthUser.class);
    List<NopAuthUser> list = user.findAll();
    IServiceContext svcCtx = null; // The svcCtx is generally present in the backend template runtime context
    CompletionStage<Object> future = graphQLEngine.fetchResult(list,
            "NopAuthUser", "...F_defaults,status_label,relatedRoleList", svcCtx);
    output("result.json5", FutureHelper.syncGet(future));
```

IGraphQLEngine.fetchResult can dynamically create a GraphQLFieldSelection based on the supplied objects, GraphQL type, and field selection set, and then execute GraphQL's DataLoader data loading logic.

By default, when NopGraphQL accesses entity data, it automatically performs batch loading. The corresponding code resides in OrmEntityRefFetcher and OrmEntitySetFetcher, which automatically handle ORM-level association properties and associated collections.

<!-- SOURCE_MD5:8759b08ba2cbff925d2486e6b7ee0b31-->
