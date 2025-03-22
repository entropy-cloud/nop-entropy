# Third-Party Framework Integration

The core modules of the Nop platform are developed from scratch without relying on any third-party frameworks. However, to improve development efficiency, the Nop platform also provides integration support for some third-party frameworks.

For example, it can be integrated with either Spring or Quarkus. The general approach is to first start Spring or Quarkus, then listen for their startup events to initialize the Nop platform.

The startup and shutdown of the Nop platform use the following calls:

```java
CoreInitialization.initialize();
CoreInitialization.destroy();
```

## Initialization Process

In CoreInitialization, the built-in ServiceLoader mechanism of Java is used to load all ICoreInitializer interfaces. The ServiceLoader will automatically discover and load any classes that implement the ICoreInitializer interface in the `META-INF/services` directory.

Here is the definition of the ICoreInitializer interface:

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

The `order` method is used to control the initialization sequence of Initializers, while `isEnabled` controls whether the Initializer is enabled.

To enable an Initializer, you can either:
1. Add a `beans.xml` file to specify `ioc:delay-method` for each bean.
2. Implement a custom ICoreInitializer and register it in the `META-INF/services` directory, setting its `order` to `INITIALIZER_PRIORITY_ANALYZE + 10`.

The `INITIALIZER_PRIORITY_ANALYZE` is a special level that ensures the Initializer will not be executed during code generation. However, if you use `mvn install`, this Initializer will be called during the build process.

## Spring Integration

For detailed configuration of Spring integration, refer to [spring.md](spring.md).

Custom modifications can also be made to Spring and MyBatis for additional reversible capabilities. For more information, refer to [spring-delta.md](spring/spring-delta.md).

## Quarkus Integration

For detailed configuration of Quarkus integration, refer to [quarkus.md](quarkus.md).

## Solon Integration

For integration with the domestic microservice framework Solon, refer to [nop-on-solon.md](integration/nop-on-solon.md).
