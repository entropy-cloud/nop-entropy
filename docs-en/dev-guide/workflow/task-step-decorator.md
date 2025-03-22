# Step Decorator

You can enhance task steps by using a decorator. This is similar to AOP (Aspect-Oriented Programming) effects.

When implementing a new decorator, you can implement the `ITaskStepDecorator` interface and register it in NopIoC with the name `nopTaskStepDecorator_{decoratorName}`.

For example, the following decorator is defined in the `nop-task-ext` package:

```xml
<bean id="nopTaskStepDecorator_transaction" class="io.nop.task.ext.dao.TransactionTaskStepDecorator">
    <ioc:condition>
        <on-class>io.nop.dao.txn.ITransactionTemplate</on-class>
    </ioc:condition>
</bean>
```

Using `<decorator name="transaction"/>`, you can utilize this decorator.

## Transaction Decorator

You need to import the `nop-task-ext` module.

You can control step transaction ranges using the `transaction` decorator.

For example, in the following XML configuration:

```xml
<sequential name="parentStep">
    <decorator name="transaction"/>

    <steps>
        <xpl name="step1">
            <source><![CDATA[
                import io.nop.sys.dao.entity.NopSysDict;

                const daoProvider = inject("nopDaoProvider");
                const dao = daoProvider.dao("NopSysDict");
                const dict = new NopSysDict();
                dict.setDictName("sys/test2");
                dict.setDisplayName("test");
                dao.saveEntity(dict);
                dao.flushSession();
            ]]></source>
        </xpl>

        <xpl name="step2">
            <source>
                <c:throw errorCode="test-error"/>
            </source>
        </xpl>
    </steps>
</sequential>
```

The `transaction` decorator can control transaction details using parameters like `txn:txnGroup` and `txn:propagation`.

* `txn:txnGroup` - Specify transaction grouping, usually not specified, corresponds to the default value.
* `txn:propagation` - Specify whether to create a new transaction, corresponds to the `TransactionPropagation` enum's values.
