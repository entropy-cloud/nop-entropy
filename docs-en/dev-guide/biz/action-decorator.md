
# Action Decorator

Every operation function and loader function in BizModel is encapsulated as an IServiceAction.

```java

public interface IServiceAction {
  Object invoke(Object request, FieldSelectionBean selection, IServiceContext context);
}
````

* request is the request payload passed to the action; typically a Map, but it can also be the corresponding strongly-typed RequestBean
* selection is the field selection mechanism sent from the frontend
* context is the service invocation context object, through which you can obtain IUserContext, etc.
* The return value is the service method’s return value in BizModel; it may be synchronous or asynchronous.

## Registering Decorators

1. Implement the IActionDecoratorCollector interface following the pattern of CacheActionDecoratorCollector
2. Register actionDecoratorCollector in beans.xml. **Note: The registered bean must be IActionDecoratorCollector rather than IServiceActionDecorator**
3. When BizObjectManager starts, it automatically collects the decoratorCollectors registered in the IoC container and then uses them to generate actionDecorators



```java
public interface IServiceActionDecorator extends IOrdered {
  IServiceAction decorate(IServiceAction action);
}
```

* During initialization, the ServiceActionDecorator is applied to the serviceAction to build a decorated IServiceAction
* Decorators have a priority order; before application, they are queued by priority, with smaller order values taking precedence.

<!-- SOURCE_MD5:60a463859ba43d2e11a39c6c411cbc0a-->
