# 多数据源配置

多数据源配置介绍视频： [如何在ORM引擎中同时访问多个数据库](https://www.bilibili.com/video/BV1aX4y1Y7Xx/)

## 数据源管理

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

1. 在orm.xml的实体定义上可以配置querySpace

```xml
<entity name="xxx.NopAuthUser" querySpace="test">...</entity>
```

2. 在sql-lib中可以配置querySpace

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

3. 手工创建SQL对象并执行时可以指定querySpace

```java
SQL sql = SQL.begin().sql("select * from xxx where id=?",3).querySpace("test").end();
jdbcTemplate.findFirst(sql);
```
