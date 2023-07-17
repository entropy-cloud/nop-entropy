# 多数据源配置

NopOrm引擎中每个querySpace对应于一个独立的DataSource。

## 1. 配置多个DataSource

可以配置多个DataSource，命名格式为 nopDataSource_{querySpace}。后缀名对应于querySpace

````xml
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
````

在dao-defaults.beans.xml中，会自动收集所有前缀为nopDataSource_的bean

````xml
    <bean id="nopTransactionManager" ioc:default="true"
          class="io.nop.dao.txn.impl.DefaultTransactionManager">
        <property name="defaultFactory" ref="nopTransactionFactory"/>

        <property name="dataSourceMap">
            <ioc:collect-beans only-concrete-classes="true" as-map="true" name-prefix="nopDataSource_"/>
        </property>
    </bean>
````


## 2. 不同的实体属于不同的数据库
在app.orm.xml模型文件中，针对每个实体可以指定不同的querySpace

````xml
<entity name="test.TestGeo" querySpace="test">
    ...
</entity>
````

## 3. 在sql-lib中可以为SQL语句指定querySpace

````xml
<sql name="getAllLocations" querySpace="test" sqlMethod="findAll">
    <source>
        select location from test_geo
    </source>
</sql>
````

## 4. 直接执行SQL时指定querySpace

构造SQL对象时可以直接指定querySpace

````javascript
jdbcTemplate.executeUpdate(SQL.begin().querySpace("test").sql("update ...").end());
````
