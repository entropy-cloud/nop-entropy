# Transaction Template

In the Nop platform, transaction scope is primarily controlled via TransactionTemplate, whose design is similar to the transaction template object in the Spring framework, with the addition of asynchronous context support.

In code, you can obtain the transaction template via dependency injection.

```
@Inject
ITransactionTemplate transactionTemlate;

public void myMethod(){
   transactionTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn-> doSomething());
}
```

Because the Nop platform’s JdbcTransaction implementation applies the following optimizations:

1. Lazy connection acquisition: After a transaction is started, a JDBC connection is not obtained immediately; the connection is acquired only upon the first access to the database.
2. Eager connection release: If a commit is executed, the JDBC connection is proactively released (the transaction is not closed at this point). Since the transaction has been committed, subsequent database access can obtain a new connection.

## TransactionPropagation
This setting is the same as in the Spring framework; the TransactionPropagation class itself was copied from Spring. The two most commonly used options are:

1. REQUIRE_NEW: Requires starting a new transaction; the current transaction is suspended.
2. REQUIRED: If there is no current transaction, start a new one; otherwise, join the current transaction.

## @Transactional Annotation

In application code, you can mark Java methods that need to run in a transactional context with the @Transactional annotation. A constraint is that AOP enhancements are executed by the NopIoC engine; therefore, only beans registered in the beans.xml file have transaction support.
Additionally, AOP generates code ahead of time.

**Note that this Transactional is an annotation of the Nop platform, not the Spring framework. The internal functionality of the Nop platform does not depend on the Spring framework.**

## @BizMutation

The NopGraphQL engine automatically starts a transaction for mutation operations. Therefore, as long as a method is annotated with @BizMutation, there is no need to additionally add the @Transactional annotation.

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

## Multi-Data-Source Transactions

See [multi-datasource.md](multi-datasource.md)

The NopORM engine supports using multiple data sources concurrently; for example, some tables may reside in database A and others in database B, and once mapped to entity objects they can exist within the same OrmSession.
By default, all data sources belong to the same transaction group (txnGroup=default). When a transaction is opened, it is considered the transaction group to be opened, and transactions of different data sources are attached to this transaction group.
When the transaction group is committed, the transactions of the underlying data sources that were opened are committed one by one.

## Listen for Transaction Commit or Rollback Events

`ITransactionTemplate.addTransactionListener(txnGroup, listener)` can register transaction events.

## Spring Transaction Integration
When nop.dao.use-parent-transaction-factory is set to true, NopSpringTransactionFactory is enabled. It uses Spring transactions to implement ITransactionFactory, but this implementation does not support asynchronous transactions.
<!-- SOURCE_MD5:69d8615cc79fd14801d6a9802678f6df-->
