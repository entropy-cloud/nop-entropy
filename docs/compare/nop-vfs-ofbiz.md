# 为什么OFBiz已经是一种落后的技术

Apache OFBiz（Open For Business）是一个开源的企业资源规划（ERP）系统

## 字段类型定义
Nop平台的做法是区分domain和stdSqlType的概念，只由stdSqlType映射到不同的数据库，在引入更多domain的时候，并不需要分别映射到不同的数据库。

OFBiz的数据类型在`/framework/entity/fieldtype`目录下，
```xml
<!-- fieldtypepostgre.xml -->
<fieldtypemodel xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:noNamespaceSchemaLocation="https://ofbiz.apache.org/dtds/fieldtypemodel.xsd">
  <field-type-def type="blob" sql-type="BYTEA" java-type="java.sql.Blob"/>

  <field-type-def type="date-time" sql-type="TIMESTAMPTZ" java-type="java.sql.Timestamp"/>
  <field-type-def type="date" sql-type="DATE" java-type="java.sql.Date"/>
  <field-type-def type="time" sql-type="TIME" java-type="java.sql.Time"/>
  ...
</fieldtypemodel>
```

而Nop平台的做法更加灵活，目前的做法是在`/nop/ofbiz/base/ofbiz-base.orm.xml`定义domain
```xml
<orm>
  <domains>
    <domain name="blob" stdSqlType="VARBINARY" precision="100000000"/>
    <domain name="date-time" stdSqlType="DATETIME"/>
    <domain name="date" stdSqlType="DATE"/>
    <domain name="time" stdSqlType="TIME"/>
    ...
  </domains>
</orm>
```

## 数据源定义

Nop平台的数据源可以使用NopIoC容器来定义，并不需要引入一个特定的datasource定义文件。也可以直接利用底层Quarkus框架或者Spring框架定义的数据源，通过约定bean的名称来实现多数据源。

例如`nopDataSource_dev`对应于`querySpace=dev`的数据源，而`nopDataSource`对应于`querySpace=default`的数据源。

这里体现了Nop平台的做法：能够利用通用机制实现的组织，绝不会引入一个特定的仅具有狭窄用途的配置文件。引入这种配置文件还需要实现解析、装配、应用等功能，而利用IoC容器的标准功能，根本不需要做任何额外工作。

## 实体定义
OFBiz的`entitymodel.xml`定义可以直接转换为Nop平台的`orm.xml`模型文件。

## 元模型定义
OFBiz使用的xsd语法非常臃肿，而且引入了完全不必要的顺序依赖。
