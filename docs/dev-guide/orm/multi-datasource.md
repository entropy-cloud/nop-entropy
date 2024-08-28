# 多数据源配置

多数据源配置介绍视频： [如何在ORM引擎中同时访问多个数据库](https://www.bilibili.com/video/BV1aX4y1Y7Xx/)

## 数据源管理
NopOrm引擎中每个querySpace对应于一个独立的DataSource。

可以配置多个DataSource，命名格式为 `nopDataSource_{querySpace}`。后缀名对应于querySpace

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

dao-defaults.beans.xml中配置了nopTransactionManager，它自动收集所有前缀为`nopDataSource_`的数据源定义

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

在运行时可以调用DefaultTransactionManager对象上的addQuerySpace/removeQuerySpace来动态增减数据源。

## 使用多数据源

### 1. 在orm.xml的实体定义上可以配置querySpace

```xml

<entity name="xxx.NopAuthUser" querySpace="test">...</entity>
```

### 2. 在sql-lib中可以配置querySpace

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

### 3. 手工创建SQL对象并执行时可以指定querySpace

```javascript
SQL sql=SQL.begin().sql("select * from xxx where id=?",3).querySpace("test").end();
  jdbcTemplate.findFirst(sql);
```

### 4. 直接为数据源指定dialect

一般情况下会根据DataSource获取到Connection，然后猜测得到对应的数据库方言。如果需要，也可以直接指定

```yaml
nop:
  dao:
    config:
      query-space-to-dialect:  test=h2gis
```

上面示例指定querySpace=test对应的数据源使用h2gis这个方言

## 数据库事务

在NopOrm引擎中，只有ormTemplate.flush()调用的时候才会执行数据库操作，只有在flush函数中才会真正打开JDBC事务。因此业务处理异常时一般还没有执行数据库更新动作，不涉及到数据库回滚的问题。

对于多数据源配置，我们可以配置多个querySpace对应于一个事务组，则提交的时候会先执行所有数据库访问操作，等所有数据库操作都执行成功之后再逐个commit。

```
nop.dao.config.txn-group-map= test=default
```

以上配置表示querySpace=test的数据库操作归属于default这个事务组


## Excel模型配置
在表配置中，增加一个【查询空间】配置，通过它可以指定querySpace

## 动态切换数据源

通过QuerySpaceEnv可以动态切换default情况下实际使用的数据源

```javascript
return QuerySpaceEnv.runInQuerySpace("test", () -> {
    return doMyWork();
});
```

以上代码会在doMyWork()方法执行期间，将QuerySpaceEnv的defaultQuerySpace设置为"test"，doMyWork()
方法中的所有ORM操作如果没有明确指定querySpace，则会使用"test"数据源。

* 所有明确指定的querySpace不会受到QuerySpaceEnv的影响。
