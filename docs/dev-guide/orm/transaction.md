# 事务模板
在Nop平台中主要通过TransactionTemplate来控制事务范围，它的设计类似于Spring框架中的事务模板对象，只是增加了异步上下文支持。

在代码中我们可以通过依赖注入来获得事务模板。

````
@Inject
ITransactionTemplate transactionTemlate;
````

因为Nop平台的JdbcTransaction的实现做了如下优化：
1. 延迟获取连接: 开启事务后并没有立刻获取JDBC连接，只有第一次访问到数据库时才真的获取连接。
2. 主动释放连接: 如果执行了commit操作，则主动释放JDBC连接（此时事务并没有关闭）。因为事务已经提交，后面再访问数据库的时候可以获取新的连接。

# @Transactional注解
在应用代码中可以通过 @Transactional注解来标记Java方法需要在事务环境中执行。限制条件是AOP增强由NopIoC引擎负责执行，因此只有在beans.xml文件中注册的bean才具有事务支持。
而且AOP会提前生成代码，