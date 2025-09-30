
# Step Decorators

You can introduce decorators via the decorator element to enhance task steps, achieving AOP-like behavior.
When adding a new decorator implementation, implement the ITaskStepDecorator interface and then register a bean named `nopTaskStepDecorator_{decoratorName}` in NopIoC.

For example, the `nop-task-ext` package defines the following decorator

```xml
<bean id="nopTaskStepDecorator_transaction" class="io.nop.task.ext.dao.TransactionTaskStepDecorator">
     <ioc:condition>
         <on-class>io.nop.dao.txn.ITransactionTemplate</on-class>
     </ioc:condition>
</bean>
```

This allows us to use the decorator via `<decorator name="transaction"/>`.

## Transaction Decorator

> Requires the nop-task-ext module

You can use the transaction decorator to control the transactional scope of steps.

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

The decorator accepts the following parameters to control transaction details

* `txn:txnGroup` Specifies the transaction group; usually it does not need to be set and corresponds to 'default'.
* `txn:propagation` Specifies whether to start a new transaction, corresponding to the values of the TransactionPropagation enum.

<!-- SOURCE_MD5:b33c23789b6350313c109de9f7066968-->
