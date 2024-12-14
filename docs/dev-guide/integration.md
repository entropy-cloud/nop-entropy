# 第三方框架集成

Nop平台的核心模块都是从零开始编写，不依赖任何第三方框架。但是为了提高开发效率，Nop平台也提供了一些第三方框架的集成支持。
它可以与Spring或者Quarkus框架集成在一起使用。基本做法都是先启动Spring或者Quarkus框架，然后监听Spring或者Quarkus的启动事件来启动Nop平台。

Nop平台的启动和停止使用如下调用：

```
CoreInitialization.initialize();
CoreInitialization.destroy();
```

## 初始化过程

CoreInitialization中会使用Java内置的ServiceLoader机制加载所有的ICoreInitializer接口。

```java
public interface ICoreInitializer {
    default int order() {
      return NORMAL_PRIORITY;
    }

    default boolean isEnabled() {
        return true;
    }

    void initialize();

    default void destroy() {
    }
}
```

order用于控制Initializer的初始化顺序，isEnabled用于控制是否启用该Initializer。

在`META-INF/services`目录下新建`io.nop.core.initialize.ICoreInitializer`文件，在其中加入的类名都会被自动装载。

Nop平台加载时目前是按照如下顺序

1. ReflectionHelperMethodInitializer
2. XLangCoreInitializer 在`nop-xlang`模块中
3. ConfigInitializer 在nop-config模块中，负责启动config服务，并加载配置
4. VirtualFileSystemInitializer 在nop-core模块中，负责初始化虚拟文件系统
5. DaoDialectInitializer 在nop-dao模块中，负责加载dialect选择模型
6. IocCoreInitializer 在nop-ioc模块中，负责初始化BeanContainer
7. CodeGenAfterInitialization 在nop-codegen模块中，负责输出`nop-vfs-index`等记录文件，它应该是最后一个执行的Initializer

如果在Nop平台初始化完毕后执行某些功能，可以采用两种方式

1. 在`beans.xml`中配置bean的`ioc:delay-method`，在IoC容器所有bean初始化完毕后会调用bean的delayMethod
2. 也可以实现一个自定义的ICoreInitializer，并在`META-INF/services`中注册，order可以设置为 `INITIALIZER_PRIORITY_ANALYZE + 10`。

`INITIALIZER_PRIORITY_ANALYZE`是一个特殊的级别，order大于它的Initializer在代码生成阶段不会被执行，否则通过`mvn install`执行代码生成工具的时候会调用这个Initializer

## Spring集成

Spring集成配置参见[spring.md](spring.md)

也可以改造Spring和MyBatis，加入部分可逆计算支持，参见[spring-delta.md](spring/spring-delta.md)

## Quarkus集成

Quarkus集成配置参见[quarkus.md](quarkus.md)

## Solon集成

与国产微服务框架Solon的集成配置参见[nop-on-solon.md](integration/nop-on-solon.md)
