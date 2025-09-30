# Multi-Data-Source Configuration

Intro video on multi-data-source configuration: [How to access multiple databases in the ORM engine](https://www.bilibili.com/video/BV1aX4y1Y7Xx/)

## Data Source Management
In the NopOrm engine, each querySpace corresponds to an independent DataSource.

You can configure multiple DataSources with the naming pattern `nopDataSource_{querySpace}`. The suffix corresponds to the querySpace.

```xml
    <bean id="nopDataSource_test"
          class="com.zaxxer.hikari.HikariDataSource">
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

In dao-defaults.beans.xml, nopTransactionManager is configured to automatically collect all data source definitions whose IDs start with the prefix `nopDataSource_`.

```xml

<bean id="nopTransactionManager" ioc:default="true"
      class="io.nop.dao.txn.impl.DefaultTransactionManager">
  <property name="defaultFactory" ref="nopTransactionFactory"/>

  <property name="dataSourceMap">
    <ioc:collect-beans only-concrete-classes="true" as-map="true" name-prefix="nopDataSource_"/>
  </property>

  <property name="txnGroupMapConfig" value="@cfg:nop.dao.config.txn-group-map|"/>
  <property name="transactionMetrics" ref="nopDaoMetrics"/>
</bean>
```

At runtime, you can call addQuerySpace/removeQuerySpace on the DefaultTransactionManager object to dynamically add or remove data sources.

## Using Multiple Data Sources

### 1. Configure querySpace on entity definitions in orm.xml

```xml

<entity name="xxx.NopAuthUser" querySpace="test">...</entity>
```

### 2. Configure querySpace in sql-lib

```xml

<sql-lib x:schema="/nop/schema/orm/sql-lib.xdef" xmlns:x="/nop/schema/xdsl.xdef">

  <sqls>
    <sql name="getAllLocations" querySpace="test" sqlMethod="findAll">
      <source>
        select location from test_geo
      </source>
    </sql>
  </sqls>
</sql-lib>
```

### 3. Specify querySpace when creating and executing an SQL object manually

```javascript
SQL sql=SQL.begin().sql("select * from xxx where id=?",3).querySpace("test").end();
  jdbcTemplate.findFirst(sql);
```

### 4. Specify a dialect for a data source directly

In general, the database dialect is inferred by obtaining a Connection from the DataSource. If needed, you can specify it explicitly.

```yaml
nop:
  dao:
    config:
      query-space-to-dialect:  test=h2gis
```

The example above specifies that the data source corresponding to querySpace=test uses the h2gis dialect.

## Database Transactions

In the NopOrm engine, database operations are only executed when ormTemplate.flush() is called, and a JDBC transaction is only actually opened within the flush function. Therefore, when a business exception occurs, typically no database update has been performed yet, so rollback is not involved.

For multiple data source configurations, we can map multiple querySpaces to a single transaction group. On commit, it will first execute all database access operations; once all database operations have succeeded, it will then commit them one by one.

```
nop.dao.config.txn-group-map= test=default
```

The above configuration indicates that database operations for querySpace=test belong to the "default" transaction group.

## Excel Model Configuration
In the table configuration, add a [Query Space] setting through which you can specify the querySpace.

## Dynamically Switching Data Sources

You can dynamically switch the actual data source used by default via QuerySpaceEnv.

```javascript
return QuerySpaceEnv.runInQuerySpace("test", () -> {
    return doMyWork();
});
```

The code above sets QuerySpaceEnv's defaultQuerySpace to "test" during the execution of the doMyWork() method. All ORM operations within doMyWork() that do not explicitly specify a querySpace will use the "test" data source.

* All explicitly specified querySpaces are not affected by QuerySpaceEnv.
<!-- SOURCE_MD5:a968a3587d4598251e8f1961e8a0b1f4-->
