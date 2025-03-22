# ORM Extension Mechanism

## Adding Extended Logic When Saving Entities

NopORM automatically sets the 【Creator】field when saving entities. If you wish to add similar extensions, you can implement the IOrmInterceptor interface. NopORM will automatically search for this interface in the IoC container during initialization.

```java
interface IOrmInterceptor extends IOrdered {
    default ProcessResult preSave(IOrmEntity entity) {
        return ProcessResult.CONTINUE;
    }

    default ProcessResult preUpdate(IOrmEntity entity) {
        return ProcessResult.CONTINUE;
    }

    default ProcessResult preDelete(IOrmEntity entity) {
        return ProcessResult.CONTINUE;
    }

    default void postReset(IOrmEntity entity) {

    }

    default void postSave(IOrmEntity entity) {

    }

    default void postUpdate(IOrmEntity entity) {

    }

    default void postDelete(IOrmEntity entity) {

    }

    default void postLoad(IOrmEntity entity) {

    }

    default void preFlush() {

    }

    default void postFlush(Throwable exception) {

    }
}
```

You can also define a file in each module at the path `{moduleId}/orm/app.orm-interceptor.xml` to define your `IOrmInterceptor`.

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

The `OrmEntity` also defines the `IOrmEntityLifecycle` interface, which can be directly implemented on entities to handle callbacks like `orm_preSave`.

## Field Encryption

If a column's `tagSet` contains encryption tags, the field will be automatically encrypted when stored in the database and automatically decrypted when read into Java properties. The default encryption method is `AESTextCipher`.

If you want to add additional processing at the field level, you can implement the `IOrmColumnBinder` interface to override `nopOrmColumnBinderEnhancer`. The default implementation is `DefaultOrmColumnBinderEnhancer`.