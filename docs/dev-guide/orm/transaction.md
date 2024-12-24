# 事务模板

在Nop平台中主要通过TransactionTemplate来控制事务范围，它的设计类似于Spring框架中的事务模板对象，只是增加了异步上下文支持。

在代码中我们可以通过依赖注入来获得事务模板。

```
@Inject
ITransactionTemplate transactionTemlate;

public void myMethod(){
   transactionTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn-> doSomething());
}
```

因为Nop平台的JdbcTransaction的实现做了如下优化：

1. 延迟获取连接: 开启事务后并没有立刻获取JDBC连接，只有第一次访问到数据库时才真的获取连接。
2. 主动释放连接: 如果执行了commit操作，则主动释放JDBC连接（此时事务并没有关闭）。因为事务已经提交，后面再访问数据库的时候可以获取新的连接。

## TransactionPropagation
这里的设置与Spring框架相同，本身TransactionPropagation类就是从Spring框架中拷贝来。一般常用的是两种

1. REQUIRE_NEW: 要求新启动一个事务，当前事务挂起
2. REQUIRED: 如果当前没有事务，则新启动一个事务，否则加入当前事务

## @Transactional注解

在应用代码中可以通过 @Transactional注解来标记Java方法需要在事务环境中执行。限制条件是AOP增强由NopIoC引擎负责执行，因此只有在beans.xml文件中注册的bean才具有事务支持。
而且AOP会提前生成代码，

**注意这里的Transactional是Nop平台的注解，而不是Spring框架的注解。Nop平台内部功能不依赖于Spring框架**

## @BizMutation

NopGraphQL引擎对应mutation操作会自动开启事务。所以只要方法上增加了@BizMutation注解就不用再额外增肌@Transactional注解。

```java
@BizModel("NopAuthUser")
@Locale("zh-CN")
public class NopAuthUserBizModel extends CrudBizModel<NopAuthUser> {
    @Description("@i18n:common.resetUserPassword")
    @BizMutation
    @BizAudit(logRequestFields = "userId")
    public void resetUserPassword(@Name("userId") String userId,
                                  @Name("password") String password,
                                  IServiceContext context) {
        NopAuthUser user = this.get(userId, false, context);
        ...
    }
}
```

## 多数据源事务

参见 [multi-datasource.md](multi-datasource.md)

NopORM引擎支持同时使用多个数据源，例如一些表存放在数据库A中，另一些表存放在数据库B中，它们映射到实体对象后可以存在于同一个OrmSession中。
缺省情况下，所有的的数据源都属于同一个事务组(txnGroup=default)，当打开事务的时候会认为打开的是事务组，然后不同数据源的事务都挂接在这一个事务组中。
事务组提交的时候会逐一提交打开的下层数据源的事务。

## 监听事务提交或者回滚事件

`ITransactionTemplate.addTransactionListener(txnGroup, listener)`可以注册事务事件
