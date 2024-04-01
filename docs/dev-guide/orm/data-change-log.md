# 实体修改状态跟踪

所有实体都从OrmEntity继承，在OrmEntity上提供了修改状态跟踪机制

1. orm\_propDirty(propId) 可以判断某个字段是否已经被修改
2. orm\_propOldValue(propId) 返回修改前的值。如果没有被修改，则返回当前的值
3. orm\_propValue(propId) 返回字段当前的值
4. orm\_dirtyOldValues()和orm\_dirtyNewValues() 返回所有被修改的字段的修改前和修改后的值，返回的Map的key为属性名

## 修改监听器

实现IOrmInterceptor接口，可以监听preSave/preUpdate/preDelete等事件，在其中记录修改日志。nop-sys模块提供了一个缺省的修改日志表，以及缺省实现

NopORM启动的时候会查找在IoC容器中查找所有IOrmInterceptor接口的实现类，

只要将IOrmInterceptor接口实现类注册到NopIoC容器中，OrmSessionFactoryBean初始化的时候就可以自动搜集得到所有的IOrmInterceptor，并自动应用。

> NopIoC的使用参见[ioc.md](../ioc.md)。NopIoC缺省不使用类扫描机制，注册bean需要在beans.xml文件中增加bean的定义

## 通过Xpl配置OrmInterceptor

NopOrm内置引入的XplOrmInterceptorFactoryBean提供了一种灵活的OrmInterceptor定义机制。它的作用类似于是实现了实体级别的触发器机制。

只需要在各个模块增加`/{moduleId}/orm/app.orm-interceptor.xml`文件，就可以使用Xpl模板语言来实现OrmInterceptor。

```xml

<interceptor>
    <entity name="io.nop.auth.dao.entity.NopAuthUser">
        <post-save id="syncToEs">
            // 在这里可以写xpl脚本，entity对应于当前实体
        </post-save>
    </entity>
</interceptor>
```
