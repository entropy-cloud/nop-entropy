# Transaction Template

In the Nop platform, transactions are managed primarily through `TransactionTemplate`, which is similar in design to the `TransactionTemplate` in the Spring framework but with added asynchronous context support.

To obtain a transaction template, you can use dependency injection:

```inject
@Inject
ITransactionTemplate transactionTemlate;

public void myMethod() {
    transactionTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn -> doSomething());
}
```


## Optimizations in Nop's JdbcTransaction Implementation

1. Lazy connection acquisition: After starting a transaction, the JDBC connection is not immediately retrieved. The connection is only obtained when the database is first accessed.
2. Active connection release: If a `commit` operation is performed, the JDBC connection is actively released (though the transaction itself is not closed). Once committed, subsequent database access will use a new connection.


## TransactionPropagation

The configuration for `TransactionPropagation` is identical to that in the Spring framework. The `TransactionPropagation` class is directly copied from Spring.


## Common Settings
1. `REQUIRE_NEW`: Requires a new transaction to be started, overriding any existing transaction.
2. `REQUIRED`: If no transaction exists, a new one is started; otherwise, the current transaction is joined.


## @Transactional Annotation

In application code, methods that require transactional behavior can be marked with the `@Transactional` annotation. This ensures that the method is executed within a transaction context. However, this is managed by the AOP (Aspect-Oriented Programming) layer provided by the Nop IoC engine, which means only beans registered in `beans.xml` will have transaction support.

Additionally, AOP will generate the necessary transactional logic automatically.

**Note:** The `@Transactional` annotation refers to the Nop platform's own annotation and not the one from Spring. Nop does not rely on Spring for transaction management.


## @BizMutation Annotation

The Nop GraphQL engine supports mutations out of the box, including automatic transaction initiation when a mutation operation is performed. Therefore, methods annotated with `@BizMutation` do not require additional transaction configuration.

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
        // Additional logic for resetting the password
        ...
    }
}
```



For details on multi-source transactions, refer to [multi-datasource.md](multi-datasource.md).

Nop ORM supports transactions across multiple data sources. For example, some entities may be stored in Database A while others are stored in Database B. After a transaction is started, all relevant data sources will participate in the same transaction context.

By default, all data sources belong to the same transaction group (`txnGroup=default`). When a transaction is initiated, it affects all associated data sources within that transaction group. Each data source's transaction is then committed individually.



To listen for transaction events (commit or rollback), you can use:

```java
ITransactionTemplate.addTransactionListener(txnGroup, listener);
```

