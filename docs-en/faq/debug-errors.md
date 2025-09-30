# Debugging Errors

## 1. Type Casting Error in the BizModel Layer

```
2024-01-13 22:34:08 [http-nio-8000-exec-7] ERROR io.nop.core.exceptions.ErrorMessageManager - nop.build-error-message
java.lang.ClassCastException: class com.ly.ale.pojo.AleYearPlanPO cannot be cast to class io.nop.orm.IOrmEntity (com.ly.ale.pojo.AleYearPlanPO and io.nop.orm.IOrmEntity are in unnamed module of loader 'app')
	at io.nop.graphql.orm.fetcher.OrmEntityIdFetcher.get(OrmEntityIdFetcher.java:19)
	at io.nop.graphql.core.engine.GraphQLExecutor.hookFetch(GraphQLExecutor.java:372)
	at io.nop.graphql.core.engine.GraphQLExecutor.fetchSelection(GraphQLExecutor.java:356)
```

This occurs because the method on the BizModel is annotated with `@GraphQLReturn(bizObjName=BIZ_OBJ_NAME_THIS_OBJ)`, which enforces that the return type must be the current entity object. Therefore, when
a different DTO object is actually returned, a casting error occurs.

```
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public List<T> myMethod() {
        return List<PO> objects
    }
```
<!-- SOURCE_MD5:41bf9800fd97f43cc29637edc5164271-->
