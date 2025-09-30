# How to Implement Automatically Generated Read-Only Fields

The system stores two fields, [Family Name] and [Given Name], and we want to automatically generate a [Full Name] field that can be used for querying.

## 1. Prohibit modifications
First, configure in the meta to prohibit creation and updates for the [Full Name] field.

```xml
<prop name="fullName" insertable="false" updatable="false">

</prop>
```

Here, `insertable=false` and `updatable=false` only control whether the Web service layer allows creation and updates, and are unrelated to the ORM data storage layer. If set to false, submissions to the backend will be automatically ignored.

## 2. Configure autoExpr for automatic concatenation

```xml
<prop name="fullExpr" insertable="false" updatable="false">
    <autoExpr when="save,update">
      return entity.familyName + entity.givenName
    </autoExpr>
</prop>
```

In the Nop platform, coding rules such as order numbers are implemented using the autoExpr mechanism; see [coderule.md](../biz/coderule.md).

The autoExpr logic runs only when entity updates are performed via OrmEntityCopier. For usage details, see the save/update functions provided by the built-in CrudBizModel.

## 3. Other approaches to implement auto-calculated fields

### 3.1 Via callback functions provided by CrudBizModel

For typical CRUD operations, we usually extend CrudBizModel. It provides defaultPrepareSave/defaultPrepareUpdate callback functions where you can execute additional extension logic.

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

However, this approach is only executed when save/update is invoked from the frontend; if the entity is saved through other means, this logic will not run.

### 3.2 Via entity lifecycle callbacks provided by the ORM engine

In the entity class, you can implement callbacks such as orm_preSave. The ORM engine executes these callbacks before saving the entity, so this approach is lower-level than the CrudBizModel callbacks and ensures it will not be missed.

```java
public class NopAuthUser extends _NopAuthUser {
  public ProcessingResult orm_preSave() {
    this.setFullName(getFamilyName() + getGivenName());
    return ProcessingResult.CONTINUE;
  }
}
```

* If the callback returns STOP, the entity’s save will be skipped. This logic is similar to lifecycle callbacks in Hibernate.

### 3.3 Implement additional processing in OrmInterceptor

The NopOrm engine provides the IOrmInterceptor interface with callbacks such as preSave/postSave, allowing interception of CRUD operations on each entity.

In each module, you can also define a `/{moduleId}/orm/app.orm-interceptor.xml` file, where an IOrmInterceptor can be defined using the xpl template language.

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

### 3.4 Display-only field used at view time
If the fullName field does not need to be stored in the database and is only required for display in certain views, you can add an extension field in the meta.

```xml
<prop name="fullName">
  <schema type="String" />

  <getter>
    return entity.familyName + entity.givenName
  </getter>
</prop>
```

* A getter function can evaluate an expression to compute the returned value.
* By default, the insertable and updatable attributes are false. If the field is defined on the entity, attributes such as `insertable=true` will be inherited from automatically generated files like `_NopAuthUser.xmeta`.

### 3.5 Implement loading logic using BizLoader
If the computation is complex or you prefer not to place it in the meta file’s getter function, you can implement the loading logic in a Java method.

```
public class NopAuthUserBizModel {
   @BizLoader(autoCreateField=true)
   public String fullName(@ContextSource NopAuthUser entity, IServiceContext context){
      return entity.getFamilyName() + entity.getGivenName();
   }
}
```

* autoCreateField indicates that if the fullName prop is not defined in the meta, the attribute will be created automatically. If you want all fields to be explicitly defined in the meta, remove the autoCreateField configuration; its default is false. In that case, you must explicitly define the fullName attribute in the meta.

### 3.6 Implement the Loader in an xbiz file
Any BizAction and BizLoader implemented in Java can be implemented in xbiz using the xpl template language. xbiz serves as a low-code editing layer over the high-code Java implementation layer, and can override any function in the high-code layer. This approach is similar to Docker’s layered filesystem overlay.

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

If the same-named function is defined both in the xbiz file and in the BizModel class, the xbiz-defined function takes precedence and overrides the function defined in the Java layer.

## 4. Implement redundant auto-calculated field on the frontend

In the frontend (amis), you can use its built-in dynamic expression mechanism.

```json
{
  "type": "tpl",
  "tpl": "${familyName + givenName}"
}
```
<!-- SOURCE_MD5:c3381c1d9fa8751acb49a470c1e1bd54-->
