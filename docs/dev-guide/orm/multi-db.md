# 多数据源配置

NopOrm引擎中每个querySpace对应于一个独立的DataSource。

## 1. 配置多个DataSource

可以配置多个DataSource，命名格式为 nopDataSource\_{querySpace}。后缀名对应于querySpace

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

在dao-defaults.beans.xml中，会自动收集所有前缀为nopDataSource\_的bean

```xml
    <bean id="nopTransactionManager" ioc:default="true"
          class="io.nop.dao.txn.impl.DefaultTransactionManager">
        <property name="defaultFactory" ref="nopTransactionFactory"/>

        <property name="dataSourceMap">
            <ioc:collect-beans only-concrete-classes="true" as-map="true" name-prefix="nopDataSource_"/>
        </property>
    </bean>
```

## 2. 不同的实体属于不同的数据库

在app.orm.xml模型文件中，针对每个实体可以指定不同的querySpace

```xml
<entity name="test.TestGeo" querySpace="test">
    ...
</entity>
```

## 3. 在sql-lib中可以为SQL语句指定querySpace

```xml
<sql name="getAllLocations" querySpace="test" sqlMethod="findAll">
    <source>
        select location from test_geo
    </source>
</sql>
```

## 4. 直接执行SQL时指定querySpace

构造SQL对象时可以直接指定querySpace

```javascript
jdbcTemplate.executeUpdate(SQL.begin().querySpace("test").sql("update ...").end());
```

## 5. 直接为数据源指定dialect

一般情况下会根据DataSource获取到Connection，然后猜测得到对应的数据库方言。如果需要，也可以直接指定

```yaml
nop:
  dao:
    config:
      query-space-to-dialect:  test=h2gis
```

上面示例指定querySpace=test对应的数据源使用h2gis这个方言

## 6. 数据库事务

在NopOrm引擎中，只有ormTemplate.flush()调用的时候才会执行数据库操作，只有在flush函数中才会真正打开JDBC事务。因此业务处理异常时一般还没有执行数据库更新动作，不涉及到数据库回滚的问题。

对于多数据源配置，我们可以配置多个querySpace对应于一个事务组，则提交的时候会先执行所有数据库访问操作，等所有数据库操作都执行成功之后再逐个commit。

```
nop.dao.config.txn-group-map= test=default
```

以上配置表示querySpace=test的数据库操作归属于default这个事务组
