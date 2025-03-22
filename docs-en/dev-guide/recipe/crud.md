# CRUD Related

## 1. Passing Fields That Do Not Exist on the Entity to the Backend

In the `meta` section, add a `prop` and set its `virtual` attribute to `true`. This indicates that it is a virtual field and will not be automatically copied to the entity.

If reading is not allowed, you need to configure `published="false"` so that queries from the backend will not attempt to read this property.

```xml
<meta>
  <props>
    <prop name="myProp" published="false" virtual="true">
      <schema stdDomain="string" />
    </prop>
  </props>
</meta>
```

The backend can access `myProp` via `entityData`.

```java
class MyEntityBizModel extends CrudBizModel {
    @BizMutation
    public MyEntity myMethod(@Name("data") Map<String, Object> data, IServiceContext ctx) {
        return doSave(data, null, (entityData, ctx) -> {
            String myProp = (String) entityData.getData().get("myProp");
            // ...
        }, ctx);
    }
}
```

## 2. Executing an Action After a Transaction is Successfully Committed

Use the `ITransactionTemplate.afterCommit` method.

The `CrudBizModel` has been injected with `transactionTemplate`, which can be accessed via `this.txn()`. 

```java
class MyEntityBizModel extends CrudBizModel {
    @BizMutation
    public MyEntity myMethod(@Name("data") Map<String, Object> data, IServiceContext ctx) {
        return doSave(data, null, (entityData, ctx) -> {
            // ...
        }, ctx);
    }
}
```

## 3. Extending Built-in save/update Operations and Adding Business Logic

For small updates that only affect a few fields:
- Use a custom `Bean` as the parameter.
- Call `dao().save(entity)` directly.

However, for handling a large number of fields or future expansions where custom logic is needed:
- Avoid using custom `JavaBean` as parameters.
- Instead, use built-in methods like `doSave`.

```javascript
@BizMutation
@GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
@BizMakerChecker(tryMethod = METHOD_TRY_SAVE)
public MyEntity my_save(@Name("data") Map<String, Object> data, IServiceContext context) {
    FieldInputSelection inputSelection = getObjMeta().getFieldSelection("my_selectio");
    return doSave(data, inputSelection, this::myPrepareSave, context);
}
```

* **`inputSelection` parameter**: Used to limit which fields are accepted from the frontend.
* **`prepareSave` callback**: Used for custom business logic before saving.

## 4. Querying with Required Parameters

Set `queryMandatory` or `queryForm.cell` to `mandatory` in `propMeta`.

```xml
<meta>
  <props>
    <prop name="myProp">
      <ui:queryMandatory />
    </prop>
  </props>
</meta>
```

## 5. Automatically Setting Entity Properties

### ORM save with Default Values

1. Set `defaultValue` in the Excel data model. If a field is not set when creating an entity, it will be assigned the default value.
2. When saving, if a field is required but its value is `null`, it will be set to the default value.
3. If a field has a `seq` attribute and its value is `null`, a sequence number will be generated and assigned.

### OrmEntityCopier for Copying Data

Use `OrmEntityCopier` from the XMeta layer if you need to copy data from one entity to another automatically.

```xml
<prop name="orderNo">
  <autoExpr when="save">
    <app:GenOrderNo />
  </autoExpr>
</prop>
```
