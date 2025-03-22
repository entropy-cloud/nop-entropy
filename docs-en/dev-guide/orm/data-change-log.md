# Entity State Tracking

All entities inherit from `OrmEntity`, which provides a state tracking mechanism for entities.

1. `orm_propDirty(propId)` can determine if a specific property has been modified.
2. `orm_propOldValue(propId)` returns the old value of a property. If the property has not been modified, it returns the current value.
3. `orm_propValue(propId)` returns the current value of a property.
4. `orm_dirtyOldValues()` and `orm_dirtyNewValues()` return the modified properties' old and new values respectively, with the keys being the property names.


## Modification Listener

Implementing `IOrmInterceptor` allows you to listen to events such as `preSave`, `preUpdate`, and `preDelete`. The nop-sys module provides a default log table and a default implementation for logging modifications.

When NopORM starts, it searches the IoC container for all implementations of `IOrmInterceptor`.

To register your `IOrmInterceptor` implementation, add it to the NopIoC container. Once registered, during initialization, `OrmSessionFactoryBean` will automatically discover all `IOrmInterceptor` implementations and apply them.

> For details on using NopIoC, refer to [ioc.md](../ioc.md). NopIoC does not use class scanning by default; you must explicitly define beans in the `beans.xml` file.


## Configuring `OrmInterceptor` Using Xpl

The built-in `XplOrmInterceptorFactoryBean` provides a flexible mechanism for defining `OrmInterceptor`. It acts similarly to an entity-level trigger mechanism.

You can configure `OrmInterceptor` by adding an `app.orm-interceptor.xml` file in each module's directory. This allows you to use Xpl scripting to implement custom logic for `OrmInterceptor`.

```xml
<interceptor>
    <entity name="io.nop.auth.dao.entity.NopAuthUser">
        <post-save id="syncToEs">
            // Here you can write Xpl scripts, where 'entity' corresponds to the current entity
        </post-save>
    </entity>
</interceptor>
```

