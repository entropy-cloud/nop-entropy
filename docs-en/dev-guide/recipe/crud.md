
# CRUD-related

## 1. Pass fields not present on the entity to the backend

Add a prop in meta and set its virtual attribute to true to mark it as a virtual field; it will not be automatically copied onto the entity.

If reading is not allowed, configure published=false; then when querying the backend, this property will not be read from the entity.

```xml
<meta>
  <props>
    <prop name="myProp" published="false" virtual="true">
        <schema stdDomain="string" />
    </prop>
  </props>
</meta>
```

On the backend, you can read it via entityData

```java
class MyEntityBizModel extends CrudBizModel {
    @BizMutation
    public MyEntity myMethod(@Name("data") Map<String, Object> data, IServiceContext ctx) {
        return doSave(data, null, (entityData, ctx) -> {
            String myProp = (String) entityData.getData().get("myProp");
            //...
        }, ctx);
    }
}
```

## 2. Execute an operation after a transaction commits successfully

Use the ITransactionTemplate.afterCommit(null, action) function.

CrudBizModel already has a transactionTemplate injected; you can use it via this.txn().

## 3. Extend CrudBizModel's built-in save/update operations to add business-specific handling

If only a small number of fields need to be updated, in principle you can use a custom bean as the parameter and call dao().save(entity) directly.
However, if you need to accept a large number of fields, anticipate future extensions that need to be saved, and require customized processing logic during saving, then do not use a custom JavaBean as the parameter; instead, use built-in functions such as doSave.

```javascript
    @BizMutation
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    @BizMakerChecker(tryMethod = METHOD_TRY_SAVE)
    public MyEntity my_save(@Name("data") Map<String, Object> data, IServiceContext context) {
        // If you need to restrict receiving only certain fields, you can configure this in meta. If there is no special restriction, set inputSelection to null.
        FieldInputSelection inputSelection = getObjMeta().getFieldSelection("my_selectio");
        return doSave(data, inputSelection, this::myPrepareSave, context);
    }
```

* The inputSelection parameter can be used to restrict which parameters from the frontend are accepted
* The prepareSave callback can be used to customize additional business logic executed before the entity is actually saved


## 4. Require parameters to be mandatory in queries
You can configure ui:queryMandatory on propMeta, or set mandatory on the cells of the query form.

## 5. Automatically set properties on the entity

### Automatically set default values on ORM save
1. If defaultValue is set in the Excel data model, when creating a new entity, if the field is not set, it will be automatically populated with the default value.
2. On save, if a field is required to be non-null but the current value is null, it will also be set to the default value.
3. On save, if a field's current value is null and it has the seq tag, a sequence number will be automatically generated and set on the entity.

### OrmEntityCopier executes autoExpr configured in XMeta
At the XMeta level, if a prop is configured with autoExpr, then when the frontend does not submit this property, autoExpr will be executed automatically to set it.
```xml
<prop name="orderNo">
  <autoExpr when="save">
    <app:GenOrderNo/>
  </autoExpr>
</prop>
```

<!-- SOURCE_MD5:e040ec6c26c5cbc7bfa9b4ad0b96f439-->
