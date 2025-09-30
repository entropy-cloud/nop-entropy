# Why OFBiz Is Already an Outdated Technology

Apache OFBiz (Open For Business) is an open-source Enterprise Resource Planning (ERP) system

## Field Type Definitions

The Nop platform distinguishes between the concepts of domain and stdSqlType. Only stdSqlType is mapped to different databases; when more domains are introduced, there is no need to map them separately to different databases.

OFBiz’s data types are located under `/framework/entity/fieldtype`,

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

Whereas the Nop platform’s approach is more flexible. Currently, domains are defined in `/nop/ofbiz/base/ofbiz-base.orm.xml`

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

## Data Source Definitions

The Nop platform’s data sources can be defined using the NopIoC container, without introducing a dedicated datasource definition file. You can also directly leverage data sources defined by the underlying Quarkus or Spring frameworks, implementing multiple data sources by convention on bean names.

For example, `nopDataSource_dev` corresponds to the data source with `querySpace=dev`, whereas `nopDataSource` corresponds to the data source with `querySpace=default`.

This reflects the Nop platform’s approach: when organization can be achieved through general mechanisms, it will never introduce a specialized configuration file with a narrow purpose. Introducing such a configuration file would require implementing parsing, wiring, and application functions, whereas using the standard capabilities of the IoC container requires no additional work at all.

## MVC Framework

## Entity Engine

OFBiz’s `entitymodel.xml` definitions can be directly converted into the Nop platform’s `orm.xml` model files.

## Meta-Model Definition

The XSD syntax used by OFBiz is very bloated and introduces completely unnecessary order dependencies.

## Service Engine

Language-agnostic. Input is a Map, and output is also a Map

You can also override a service by using the same name down in the deployment context (which is first framework, then themes, then applications, then specialpurpose, then hot-deploy)

ECA (Event Condition Action) is much like a trigger. When a service is called, a lookup is performed to see if any ECAs are defined for this event.


## Widget System

Similar to a reporting engine, it can produce multiple output formats: CSV, HTML, PDF, etc.

## Data Model

Can be directly reused

## Service Library

Not fully declarative

## Plugins

## Component

A Component in OFBiz includes the following directory structure

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

The Nop platform’s module concept is similar to OFBiz’s Component, but it adopts Maven’s multi-module mechanism. Front-end web pages and back-end services are stored in different Maven modules; at startup, the class scanning mechanism collects files under the vfs directories of different Maven modules together.

```
module-name/
├── deploy  Deployment scripts and initialization data
├── model   Excel model definitions
├── module-name-api/ - Interface definition module
├── module-name-codegen/ - Code generation support module
├── module-name-dao/ - Entity definitions
     └── src/
       └── main/resources/vfs
            └── module-name/orm/app.orm.xml  Entity model definitions, equivalent to OFBiz’s entitydef
├── module-name-meta/  Metadata definitions; GraphQL interface message types are automatically generated based on these
├── module-name-service/  Service definitions. xbiz files are similar to OFBiz’s servicedef
├── module-name-web/  Front-end page definitions
     └── src/
       └── main/resources/vfs
            └── module-name/pages/{bizObjName}/xxx.page.yaml  Page files
└── docs/ - Module documentation
```

## Theme

You can choose to inherit existing theme files

```
<extends location="component://common-theme/widget/Theme.xml" />
```

Dynamic templates

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
The XML syntax is extremely verbose and has been deprecated; Groovy is now used instead.

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
Obviously not as good as
```groovy
if (!field1 || !field2) {
// code in if
} else {
// code in else
}
```

## Unit Testing

<!-- SOURCE_MD5:377e1a4049b31e69947a522d4365c21f-->
