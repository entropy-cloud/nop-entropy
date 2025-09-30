# Entity Modification State Tracking

All entities inherit from OrmEntity, which provides a modification state tracking mechanism.

1. `orm_propDirty(propId)` can determine whether a field has been modified.
2. `orm_propOldValue(propId)` returns the pre-modification value. If it has not been modified, it returns the current value.
3. `orm_propValue(propId)` returns the field's current value.
4. `orm_dirtyOldValues()` and `orm_dirtyNewValues()` return the pre- and post-modification values for all modified fields; the keys of the returned Map are property names.

## Modification Listeners

By implementing the IOrmInterceptor interface, you can listen to events such as preSave/preUpdate/preDelete and record modification logs within them. The nop-sys module provides a default modification log table and a default implementation.

When NopORM starts, it looks in the IoC container for all implementations of the IOrmInterceptor interface.

As long as the IOrmInterceptor implementation classes are registered in the NopIoC container, they will be automatically collected and applied during OrmSessionFactoryBean initialization.

> For using NopIoC, see [ioc.md](../ioc.md). By default, NopIoC does not use class scanning; to register beans you need to add bean definitions in the beans.xml file.

## Configure OrmInterceptor via Xpl

The built-in XplOrmInterceptorFactoryBean in NopOrm provides a flexible mechanism for defining OrmInterceptors. Its role is similar to entity-level triggers.

You only need to add a `/{moduleId}/orm/app.orm-interceptor.xml` file in each module to implement OrmInterceptors using the Xpl template language.

```xml

<interceptor>
    <entity name="io.nop.auth.dao.entity.NopAuthUser">
        <post-save id="syncToEs">
            // You can write xpl scripts here; entity corresponds to the current entity
        </post-save>
    </entity>
</interceptor>
```
<!-- SOURCE_MD5:85d97a52eb66a817ec17a9edafec783d-->
