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

## MVC框架

## 实体引擎(Entity Engine)

OFBiz的`entitymodel.xml`定义可以直接转换为Nop平台的`orm.xml`模型文件。

## 元模型定义

OFBiz使用的xsd语法非常臃肿，而且引入了完全不必要的顺序依赖。

## 服务引擎(Service Engine)

与程序语言无关

## 组件系统(Widget System)

类似于报表引擎，可以产生多种输出形式。CSV,HTML,PDF等

## 数据模型(Data Model)

可以直接复用

## 服务库(Service Library)

并不是完全的声明式表达

## 插件(Plugins)

## 组件(Component)

OFBiz中的Component包含如下目录结构

```
component-name-here/
├── config/ - Properties and translation labels (i18n)
├── data/ - XML data to load into the database
├── entitydef/ - Defined database entities
├── minilang/ - A collection of scripts written in minilang (deprecated)
├── ofbiz-component.xml - The OFBiz main component configuration file
├── servicedef - Defined services.
├── src/
     ├── docs/ - component documentation source
     └── main/groovy/ - A collection of scripts written in Groovy
     └── main/java/ - java source code
     └── test/groovy/ - A collection of scripts written in Groovy
     └── test/java/ - java unit-tests
├── testdef - Defined integration-tests
├── webapp - One or more Java webapps including the control servlet
└── widget - Screens, forms, menus and other widgets
```

Nop平台的模块概念类似于OFBiz的Component，但是它采用了maven多模块机制，前台的web页面和后台的服务分别存放在不同的maven模块中，
启动时通过类扫描机制将不同的maven模块下的vfs目录下的文件收集在一起

```
module-name/
├── deploy  部署脚本和初始化数据
├── model   Excel模型定义
├── module-name-api/ - 接口定义模块
├── module-name-codegen/ - 代码生成辅助模块
├── module-name-dao/ - 实体定义
     └── src/
       └── main/resources/vfs
            └── module-name/orm/app.orm.xml  实体模型定义，等价于OFBiz的entitydef
├── module-name-meta/  元数据定义，根据它自动生成GraphQL接口消息类型
├── module-name-service/  服务定义。xbiz文件类似于OFBiz的servicedef
├── module-name-web/  前端页面定义
     └── src/
       └── main/resources/vfs
            └── module-name/pages/{bizObjName}/xxx.page.yaml  页面文件
└── docs/ - 模块文档
```

## Theme

可以选择继承已有的theme文件

```
<extends location="component://common-theme/widget/Theme.xml" />
```

动态模板

```xml

<templates><!-- Freemarker template use by this theme to render widget model-->
  <template name="screen" type="html" content-type="UTF-8" encoding="none"
            encoder="html" compress="false">
    <template-file widget="screen" location="component://common-
theme/template/macro/HtmlScreenMacroLibrary.ftl"/>
    <template-file widget="form" location="component://common-
theme/template/macro/HtmlFormMacroLibrary.ftl"/>
    <template-file widget="tree" location="component://common-
theme/template/macro/HtmlTreeMacroLibrary.ftl"/>
    <template-file widget="menu" location="component://common-
theme/template/macro/HtmlMenuMacroLibrary.ftl"/>
  </template>
  ...
</templates>
```

## MiniLang
XML格式的语法非常啰嗦，现已废弃，改用Groovy

```xml

<if>
  <condition>
    <or>
      <if-empty field="field1"/>
      <if-empty field="field2"/>
    </or>
  </condition>
  <then>
    <!-- code in if -->
  </then>
  <else>
    <!-- code in else -->
  </else>
</if>
```
显然不如
```groovy
if (!field1 || !field2) {
// code in if
} else {
// code in else
}
```

## 单元测试
