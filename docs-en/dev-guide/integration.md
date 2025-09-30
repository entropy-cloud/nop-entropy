# Third-Party Framework Integration

All core modules of the Nop platform are written from scratch without relying on any third-party frameworks. However, to improve development efficiency, the Nop platform also provides integration support for some third-party frameworks.
It can be integrated with the Spring or Quarkus frameworks. The basic approach is to start the Spring or Quarkus framework first, and then listen for their startup events to start the Nop platform.

The Nop platform is started and stopped using the following calls:

```
CoreInitialization.initialize();
CoreInitialization.destroy();
```

## Initialization Process

CoreInitialization uses Java’s built-in ServiceLoader mechanism to load all ICoreInitializer interfaces.

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

order is used to control the initialization order of Initializers, and isEnabled is used to control whether the Initializer is enabled.

Create a `io.nop.core.initialize.ICoreInitializer` file under the `META-INF/services` directory; the class names added to it will be automatically loaded.

The Nop platform currently loads in the following order

1. ReflectionHelperMethodInitializer
2. XLangCoreInitializer in the `nop-xlang` module
3. ConfigInitializer in the nop-config module, responsible for starting the config service and loading configurations
4. VirtualFileSystemInitializer in the nop-core module, responsible for initializing the virtual file system
5. DaoDialectInitializer in the nop-dao module, responsible for loading the dialect selection model
6. IocCoreInitializer in the nop-ioc module, responsible for initializing the BeanContainer
7. CodeGenAfterInitialization in the nop-codegen module, responsible for outputting record files such as `nop-vfs-index`; it should be the last Initializer to run

If you need to execute certain functionality after the Nop platform has finished initializing, you can use two approaches

1. Configure the bean’s `ioc:delay-method` in `beans.xml`; after all beans in the IoC container have been initialized, the bean’s delayMethod will be invoked
2. You can also implement a custom ICoreInitializer and register it in `META-INF/services`; order can be set to `INITIALIZER_PRIORITY_ANALYZE + 10`.

`INITIALIZER_PRIORITY_ANALYZE` is a special level. Initializers whose order is greater than this will not be executed during the code generation phase; otherwise, when you run the code generation tool via `mvn install`, this Initializer will be invoked.

## Spring Integration

For Spring integration configuration, see [spring.md](spring.md)

You can also modify Spring and MyBatis to add partial Reversible Computation support; see [spring-delta.md](spring/spring-delta.md)

## Quarkus Integration

For Quarkus integration configuration, see [quarkus.md](quarkus.md)

## Solon Integration

For integration with the domestic microservice framework Solon, see [nop-on-solon.md](integration/nop-on-solon.md)
<!-- SOURCE_MD5:b1012ad793fa7175c84ec0c0bbf6492f-->
