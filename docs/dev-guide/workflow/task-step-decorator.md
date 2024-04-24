# 步骤装饰器

可以通过decorator引入装饰器，对任务步骤进行增强，实现类似AOP的效果。
新增装饰器实现时，可以实现ITaskStepDecorator接口，然后在NopIoC中注册名为`nopTaskStepDecorator_{decoratorName}`的bean。

例如`nop-task-ext`包中定义了如下decorator

```xml
<bean id="nopTaskStepDecorator_transaction" class="io.nop.task.ext.dao.TransactionTaskStepDecorator">
     <ioc:condition>
         <on-class>io.nop.dao.txn.ITransactionTemplate</on-class>
     </ioc:condition>
</bean>
```

这样我们就可以通过`<decorator name="transaction"/>`来使用这个装饰器。

## 事务装饰器

> 需要引入nop-task-ext模块

可以通过transaction装饰器来控制步骤事务范围。

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

decorator上可以通过如下参数来控制事务细节

* `txn:txnGroup` 指定事务分组，一般不用指定，对应于default
* `txn:propagation` 指定是否新建事务，对应于 TransactionPropagation枚举类中的值
