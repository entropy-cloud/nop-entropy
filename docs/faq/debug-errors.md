# 调试错误

## 1. BizModel层类型转换错误

```
2024-01-13 22:34:08 [http-nio-8000-exec-7] ERROR io.nop.core.exceptions.ErrorMessageManager - nop.build-error-message
java.lang.ClassCastException: class com.ly.ale.pojo.AleYearPlanPO cannot be cast to class io.nop.orm.IOrmEntity (com.ly.ale.pojo.AleYearPlanPO and io.nop.orm.IOrmEntity are in unnamed module of loader 'app')
	at io.nop.graphql.orm.fetcher.OrmEntityIdFetcher.get(OrmEntityIdFetcher.java:19)
	at io.nop.graphql.core.engine.GraphQLExecutor.hookFetch(GraphQLExecutor.java:372)
	at io.nop.graphql.core.engine.GraphQLExecutor.fetchSelection(GraphQLExecutor.java:356)
```

这是因为BizModel上的方法标记了 `@GraphQLReturn(bizObjName=BIZ_OBJ_NAME_THIS_OBJ)`，它导致强制要求返回的类型为当前实体对象。因此当
实际返回其他的DTO对象时出现转型错误。

```
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public List<T> myMethod() {
        返回 List<PO> 对象
    }
```
