# Multiple Data Sources Configuration

**Introduction Video:** [How to access multiple databases in an ORM engine](https://www.bilibili.com/video/BV1aX4y1Y7Xx/)

---

## Data Source Management
In the NopOrm engine, each `querySpace` corresponds to a separate `DataSource`.

You can configure multiple `DataSource` instances. The naming format for these `DataSource` instances is `nopDataSource_{querySpace}`. The suffix of the name corresponds to `querySpace`.

---

```xml
<bean id="nopDataSource_test" class="com.zaxxer.hikari.HikariDataSource">
  <constructor-arg index="0">
    <bean class="com.zaxxer.hikari.HikariConfig">
      <property name="driverClassName" value="org.h2.Driver"/>
      <property name="jdbcUrl" value="jdbc:h2:mem:test"/>
      <property name="username" value="sa"/>
      <property name="password" value=""/>
      <property name="maximumPoolSize" value="8"/>
    </bean>
  </constructor-arg>
</bean>
```

---

The `dao-defaults.beans.xml` file configures the `nopTransactionManager`, which automatically collects all `DataSource` definitions with names prefixed by `nopDataSource_`.

```xml
<bean id="nopTransactionManager" class="io.nop.dao.txn.impl.DefaultTransactionManager" ioc:default="true">
  <property name="defaultFactory" ref="nopTransactionFactory"/>

  <property name="dataSourceMap">
    <ioc:collect-beans only-concrete-classes="true" as-map="true" name-prefix="nopDataSource_"/>
  </property>

  <property name="txnGroupMapConfig" value="@cfg:nop.dao.config.txn-group-map|"/>
  <property name="transactionMetrics" ref="nopDaoMetrics"/>
</bean>
```

---

At runtime, you can dynamically add or remove `querySpace` by calling methods on the `DefaultTransactionManager` object, such as `addQuerySpace` and `removeQuerySpace`.

---

## Using Multiple Data Sources

### 1. Configuring `querySpace` in `orm.xml`
You can define `querySpace` for entities in `orm.xml`.

```xml
<entity name="xxx.NopAuthUser" query-space="test">...</entity>
```

### 2. Configuring `querySpace` in `sql-lib`
You can also configure `querySpace` within `sql-lib`.

```xml
<sql-lib x:schema="/nop/schema/orm/sql-lib.xdef" xmlns:x="/nop/schema/xdsl.xdef">
  <sqls>
    <sql name="getAllLocations" query-space="test" sql-method="findAll">
      <source>
        select location from test_geo
      </source>
    </sql>
  </sqls>
</sql-lib>
```

### 3. Manually Creating SQL Objects with `querySpace`
You can create SQL objects manually and specify `querySpace` during execution.

```javascript
SQL sql = SQL.begin().sql("select * from xxx where id=?", 3).querySpace("test").end();
jdbcTemplate.findFirst(sql);
```

### 4. Specifying the Dialect Directly for Data Sources
In general cases, we will retrieve the DataSource, then get the Connection, and infer the database dialect based on the DataSource. If needed, you can directly specify the dialect.

```yaml
nop:
  dao:
    config:
      query-space-to-dialect: test=h2gis
```

This configuration specifies that for `querySpace=test`, the corresponding data source will use the `h2gis` dialect.


## Database Transactions

In the NopOrm engine, only when `ormTemplate.flush()` is called will database operations be executed. The actual JDBC transaction is opened only within the `flush` function. Therefore, if a business exception occurs during processing, no database update actions are performed, and there's no risk of database rollback.

For multiple data source configurations, you can configure multiple `querySpace`s to belong to one transaction group. When submitting, all database access operations will be executed in sequence, and only after all operations are successful will they be committed individually.

```yaml
nop.dao.config.txn-group-map= test=default
```

This configuration means that for `querySpace=test`, the operations belong to the `default` transaction group.


## Excel Model Configuration
In table configurations, you can add a `[查询空间]` (Query Space) configuration. This allows you to specify the `querySpace`.


## Dynamic Data Source Switching

Using `QuerySpaceEnv`, you can dynamically switch the data source in default cases.

```javascript
return QuerySpaceEnv.runInQuerySpace("test", () -> {
  return doMyWork();
});
```

This code will set the `defaultQuerySpace` of `QuerySpaceEnv` to "test" during the execution of `doMyWork()`. If no specific `querySpace` is specified, all ORM operations will use the "test" data source.

* Operations with a specified `querySpace` are not affected by `QuerySpaceEnv`.

