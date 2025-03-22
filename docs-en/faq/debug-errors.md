# Debug Error

## 1. BizModel Layer Type Conversion Error

```
2024-01-13 22:34:08 [http-nio-8000-exec-7] ERROR io.nop.core.exceptions.ErrorMessageManager - nop.build-error-message
java.lang.ClassCastException: class com.ly.ale.pojo.AleYearPlanPO cannot be cast to class io.nop.orm.IOrmEntity (com.ly.ale.pojo.AleYearPlanPO and io.nop.orm.IOrmEntity are in unnamed module of loader 'app')
	at io.nop.graphql.orm.fetcher.OrmEntityIdFetcher.get(OrmEntityIdFetcher.java:19)
	at io.nop.graphql.core.engine.GraphQLExecutor.hookFetch(GraphQLExecutor.java:372)
	at io.nop.graphql.core.engine.GraphQLExecutor.fetchSelection(GraphQLExecutor.java:356)
```

This error occurs because the method in BizModel is annotated with `@GraphQLReturn(bizObjName=BIZ_OBJ_NAME_THIS_OBJ)`, which enforces that the returned type must match the current entity type. Therefore, when attempting to return a different DTO (Data Transfer Object), a `ClassCastException` is thrown.

```
@GraphQLReturn(bizObjName = BIZ_OBJECT_NAME_THIS_OBJECT)
public List<T> myMethod() {
    return List<PO> object;
}
```
