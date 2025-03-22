# Action Decorator

In BizModel, each operation function and loader function will be encapsulated into the IServiceAction interface.

```java
public interface IServiceAction {
  Object invoke(Object request, FieldSelectionBean selection, IServiceContext context);
}
```

* `request` is the data passed to the action, typically a Map structure, or a corresponding strong-typed RequestBean.
* `selection` is the field selection mechanism sent from the front end.
* `context` is the service invocation context environment, from which you can get IUserContext, etc.
* The return value is the result of the service method in BizModel, which may be synchronous or asynchronous.

## Registering Decorators

1. Follow CacheActionDecoratorCollector to implement IActionDecoratorCollector.
2. In beans.xml, register actionDecoratorCollector. **Note:** The registered Bean must implement IActionDecoratorCollector interface, not IServiceActionDecorator.
3. When BizObjectManager starts up, it will automatically collect the decoratorCollector from the IoC container and use it to generate actionDecorator.

```java
public interface IServiceActionDecorator extends IOrdered {
  IServiceAction decorate(IServiceAction action);
}
```

* ServiceActionDecorator initializes by decorating serviceAction, resulting in a decorated IServiceAction.
* The decorator has an order priority setting, which is considered when arranging decorators. The one with the lower order value comes first.
