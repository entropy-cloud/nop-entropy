# How to Implement Automatically Generated Read-Only Fields

The system stores `family` and `name` fields and wants to automatically generate a readable `fullName` field for queries.

## 1. Prevent Modification
First, configure the `fullName` field in `meta` to prevent its creation and modification.

```xml
<prop name="fullName" insertable="false" updatable="false">
</prop>
```

Here, `insertable=false` and `updatable=false` only control whether the Web layer allows creating and updating, not affecting the ORM layer.
Even if set to `false`, submissions will be ignored by the system.

## 2. Configure autoExpression for Automatic Concatenation

```xml
<prop name="fullExpr" insertable="false" updatable="false">
    <autoExpr when="save,update">
        return entity.familyName + entity.givenName;
    </autoExpr>
</prop>
```

The Nop platform uses the `autoExpression` mechanism for similar cases, such as generating order numbers. For example, see [coderule.md](../biz/coderule.md).

When using `OrmEntityCopier` to update entities, `autoExpression` logic is triggered. The specific usage can be found in the built-in `CrudBizModel`'s `save/update` methods.

## 3. Implementing Automatic Field Computation in Other Ways

### 3.1 Using CrudBizModel's Callback Methods
Typically, we inherit from `CrudBizModel` to implement additional logic in its default `prepareSave` and `prepareUpdate` methods.

```java
@BizModel("NopAuthUser")
@Locale("zh-CN")
public class NopAuthUserBizModel extends CrudBizModel<NopAuthUser> {
    @BizAction
    @Override
    protected void defaultPrepareSave(EntityData<NopAuthUser> entityData, IServiceContext context) {
        super.defaultPrepareSave(entityData, context);
        NopAuthUser entity = entityData.getEntity();
        entity.setFullName(entity.getFamilyName() + entity.getGivenName());
    }
}
```

However, this method only works when the entity is submitted via `save/update` calls. If saved via other means, this logic won't execute.

### 3.2 Using ORM Engine's Entity Life Cycle Callbacks
Implement `orm_preSave` in your entity class to trigger before saving. The ORM engine will call these callbacks just before attempting to save the entity. This approach is lower-level than `CrudBizModel` callbacks and ensures no logic is missed.

```java
public class NopAuthUser extends _NopAuthUser {
    public ProcessingResult orm_preSave() {
        this.setFullName(getFamilyName() + getGivenName());
        return ProcessingResult.CONTINUE;
    }
}
```

If the callback returns `STOP`, the entity's save operation will be skipped. This is similar to Hibernate's life cycle callbacks.

### 3.3 Implementing Additional Logic in OrmInterceptor
The NopOrm engine provides an `IOrmInterceptor` interface with methods like `preSave`, `postSave`, etc., allowing you to intercept all CRUD operations. Define your logic in the interceptor located at `{moduleId}/orm/app.orm-interceptor.xml`.

```xml
<interceptor class="YourModule_OrmInterceptor" />
```

Implement `xpl:propertySetter` or similar templates in the XML file to define custom behavior for specific fields, such as automatically setting `fullName`.


```xml
<interceptor x:schema="/nop/schema/orm/orm-interceptor.xdef" xmlns:x="/nop/schema/xdsl.xdef">
  <entity name="io.nop.auth.dao.entity.NopAuthUser">
    <pre-save id="syncFullName">
      <source>
        entity.fullName = entity.familyName + entity.givenName
      </source>
    </pre-save>
  </entity>
</interceptor>
```

### 3.4 Display Field Used Only for Viewing
If the `fullName` field is not stored in the database and only needed for display purposes, you can add this as an extended field in the metadata.

```xml
<prop name="fullName">
  <schema type="String" />

  <getter>
    return entity.familyName + entity.givenName
  </getter>
</prop>
```

* Through the `getter` function, an expression is evaluated to compute and return the value.
* By default, both `insertable` and `updatable` properties are set to false. If the field is defined in the entity, it will inherit properties like `insertable=true` from the generated `_NopAuthUser.xmeta` file.

### 3.5 Implementing Loading Logic with BizLoader
If the computation logic is complex or not suitable for placement in metadata getter methods, you can implement it directly in a Java method.

```java
public class NopAuthUserBizModel {
    @BizLoader(autoCreateField = true)
    public String fullName(@ContextSource NopAuthUser entity, IServiceContext context) {
        return entity.getFamilyName() + entity.getGivenName();
    }
}
```

* `autoCreateField` indicates that if the `fullName` property is not defined in metadata, it will be automatically created. If all fields are explicitly defined in metadata, this configuration can be removed, as its default value is false.
* To ensure all fields are explicitly defined in metadata, you must explicitly define the `fullName` property.

### 3.6 Implementing Loader in Xbiz File
All BizActions and BizLoaders implemented in Java can be implemented in xbiz using XPL template language. Xbiz acts as a low-code layer that overlays over the lower Java layer, allowing it to override any method in the high-layer Java code. This approach is similar to Docker's layered file system overlay.

```xml
<biz>
  <loaders>
    <loader name="fullName" autoCreateField="true">
      <arg name="entity" kind="ContextSource" />
      <source>
        return entity.familyName + entity.givenName
      </source>
    </loader>
  </loaders>
</biz>
```

* If both xbiz and BizModel classes define the same function name, the xbiz implementation takes precedence and overrides the Java layer definition.

## 4. Implementing Automatic Calculation of Redundant Fields
In the Amis framework, you can leverage its built-in dynamic expression evaluation mechanism to automatically calculate values for fields that are not stored in the database but are needed for display purposes.

```json
{
  "type": "tpl",
  "tpl": "${familyName + givenName}"
}
```
