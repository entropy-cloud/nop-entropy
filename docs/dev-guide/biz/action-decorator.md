# Action装饰器

BizModel的每个操作函数和Loader函数都会被封装为IServiceAction接口。

```java

public interface IServiceAction {
  Object invoke(Object request, FieldSelectionBean selection, IServiceContext context);
}
````

* request是传递给action的请求数据，一般情况下是Map结构，也可以是对应的强类型的RequestBean
* selection是前台发送过来的字段选择机制
* context是服务调用上下文环境对象，通过它可以获取IUserContext等
* 返回值就是BizModel中服务方法的返回值，可能同步可能异步。

## 注册Decorator

1. 仿照CacheActionDecoratorCollector实现IActionDecoratorCollector接口
2. 在beans.xml中注册actionDecoratorCollector。**注意:注册的Bean必须是IActionDecoratorCollector接口而不是IServiceActionDecorator接口**
3. BizObjectManager启动的时候会自动搜集IoC容器中注册的decoratorCollector，然后用于生成actionDecorator



```java
public interface IServiceActionDecorator extends IOrdered {
  IServiceAction decorate(IServiceAction action);
}
```

* ServiceActionDecorator在初始化的时候会作用于serviceAction，从而构建得到一个装饰后的IServiceAction
* decorator具有优先级顺序设置，在应用之前会按照优先级排队，order值小的排在前面。
