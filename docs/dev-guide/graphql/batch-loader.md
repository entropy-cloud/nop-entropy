# 批量加载

## 对调用结果执行GraphQL的Loader机制实现动态批量数据加载

```javascript
    IEntityDao<NopAuthUser> user = daoProvider.daoFor(NopAuthUser.class);
    List<NopAuthUser> list = user.findAll();
    IServiceContext svcCtx = null; // 在后端模板运行时上下文中一般存在svcCtx
    CompletionStage<Object> future = graphQLEngine.fetchResult(list,
            "NopAuthUser", "...F_defaults,status_label,relatedRoleList", svcCtx);
    output("result.json5", FutureHelper.syncGet(future));
```

IGraphQLEngine.fetchResult可以根据传入的对象和GraphQL类型，字段选择集动态创建GraphQLFieldSelection，然后执行GraphQL的DataLoader数据加载逻辑。

缺省情况下NopGraphQL访问实体数据时会自动实现批量加载逻辑，对应代码在OrmEntityRefFetcher和OrmEntitySetFetcher中，它们会自动处理ORM层面关联属性和关联集合
