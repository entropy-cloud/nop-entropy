# ORM Extension Mechanism

## Add Extension Logic When Saving Entities

NopORM automatically sets fields such as [Creator] when saving an entity. If you want to add a similar extension to execute some callback logic when saving an entity, you can implement the IOrmInterceptor interface.
During initialization, the NopORM engine will automatically look up implementations of the IOrmInterceptor interface registered in the IoC container.

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

In each module, you can also define a `/{moduleId}/orm/app.orm-interceptor.xml` file, in which you define an IOrmInterceptor using the xpl template language.

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

The OrmEntity class also defines the IOrmEntityLifecycle interface, allowing you to implement callbacks such as orm_preSave directly on the entity.

## Field Encryption/Decryption

If a column's tagSet contains the enc tag, the field will be automatically encrypted when stored in the database and automatically decrypted when read into the Java property. By default, AESTextCipher is used for encryption/decryption.

To extend additional field-level processing logic, implement the IOrmColumnBinder interface and override the nopOrmColumnBinderEnhancer bean. Its default implementation is DefaultOrmColumnBinderEnhancer.

<!-- SOURCE_MD5:b66f65dad980edef6649eb5ba1679b07-->
