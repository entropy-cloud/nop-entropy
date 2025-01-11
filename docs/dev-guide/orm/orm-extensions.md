# ORM扩展机制

## 保存实体时增加扩展逻辑

NopORM在保存实体时会自动设置【创建人】等字段。如果希望增加一个类似的扩展，在保存实体时执行某种回调逻辑，可以实现IOrmInterceptor接口。
NopORM引擎在初始化的时候会自动查找IoC容器中注册的IOrmInterceptor接口。

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


在每个模块中，还可以定义一个`/{moduleId}/orm/app.orm-interceptor.xml`文件，在其中通过xpl模板语言来定义IOrmInterceptor。

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

OrmEntity实体上还定义了IOrmEntityLifecycle接口，可以直接在实体上实现orm_preSave等回调函数。

## 字段加解密

如果column的tagSet中包含enc标签，则字段存储在数据库中时会自动加密，在读取到Java属性中时会自动解密。加解密缺省使用的是AESTextCipher。

如果要扩展字段级别的额外处理逻辑，可以实现IOrmColumnBinder接口，覆盖nopOrmColumnBinderEnhancer这个bean。它的缺省实现是DefaultOrmColumnBinderEnhancer。

