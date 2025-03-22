# Why OFBiz is considered a deprecated technology

Apache OFBiz（Open For Business）is an open-source enterprise resource planning (ERP) system.


## Field Type Definitions

Nop platform's approach is to distinguish between the concepts of `domain` and `stdSqlType`, mapping only to different databases when introducing more domains. In contrast, OFBiz defines its data types in the `/framework/entity/fieldtype` directory:

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

The Nop platform, however, is more flexible. Its current approach defines domains in `/nop/ofbiz/base/ofbiz-base.orm.xml`:

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

The Nop platform allows data sources to be defined using the NopIoC container, without needing a dedicated `datasource` definition file. It can also utilize data sources defined by the underlying Quarkus or Spring frameworks, implementing multiple data sources through bean conventions.

For example:
- `nopDataSource_dev` corresponds to a data source with `querySpace=dev`.
- `nopDataSource` corresponds to a data source with `querySpace=default`.

This approach highlights Nop's flexibility: it uses generic mechanisms to organize data sources, avoiding the need for specialized, narrow-purposed configuration files. Defining such files requires additional parsing, assembly, and application steps, whereas using an IoC container's standard features requires no extra work.


## MVC Framework


## Entity Engine (Entity Engine)

The `entitymodel.xml` defined in OFBiz can be directly converted into the Nop platform's `orm.xml` model file.


## Meta Model Definition

OFBiz's XSD schema is overly verbose and introduces unnecessary order dependencies.


## Service Engine (Service Engine)

Service inputs and outputs are both Maps, regardless of programming language. You can also override services by using the same name within the deployment context (which follows the order: framework, themes, applications, specialpurposes, hot-deploy).

ECA (Event Condition Action) is similar to triggers. When a service is called, it performs a lookup to determine if any ECAs are defined for the given event.


## Widget System (Widget System)

Similar to the Report Engine, it can produce various output formats such as CSV, HTML, PDF, etc.


## Data Model (Data Model)

Can be directly reused.


## Service Library (Service Library)

Not entirely declarative in nature.


## Plugins (Plugins)


## Components (Components)

In OFBiz, a Component is structured as follows:

```
component-name-here/
├── config/ - Properties and translation labels (i18n)
├── data/ - XML data to be loaded into the database
├── entitydef/ - Defined database entities
├── minilang/ - A collection of scripts written in minilang (deprecated)
├── ofbiz-component.xml - The main OFBiz component configuration file
├── servicedef - Defined services.
├── src/
    ├── docs/ - Component documentation source
    └── main/groovy/ - A collection of Groovy scripts
    └── main/java/ - Java source code
    └── test/groovy/ - A collection of Groovy scripts
    └── test/java/ - Java unit-tests
├── testdef - Defined integration-tests
├── webapp - One or more Java webapps including the control servlet
└── widget - Screens, forms, menus, and other widgets
```

The Nop platform's module concept mirrors OFBiz's Component but uses Maven's multi-module approach. Front-end pages and back-office services are each stored in separate Maven modules. During startup, it scans through Maven modules' vfs directories to collect relevant files.


```
module-name/
├── deploy  Deployment scripts and initialization data
├── model   Excel model definitions
├── module-name-api/ - API definition module
├── module-name-codegen/ - Code generation helper module
├── module-name-dao/ - Entity definitions
     └── src/
       └── main/resources/vfs
            └── module-name/orm/app.orm.xml  Entity model definitions, equivalent to OFBiz's entitydef
├── module-name-meta/ - Metadata definitions, used to generate GraphQL query message types
├── module-name-service/ - Service definitions. xbiz files similar to OFBiz's servicedef
├── module-name-web/ - Frontend page definitions
     └── src/
       └── main/resources/vfs
            └── module-name/pages/{bizObjName}/xxx.page.yaml  Page files
└── docs/ - Module documentation
```

## Theme

Can choose to inherit existing theme files

```
<extends location="component://common-theme/widget/Theme.xml" />
```

Dynamic templates

```xml
<templates><!-- Freemarker template used by this theme to render widget models -->
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
XML syntax is very verbose and has been deprecated. Now using Groovy

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

Clearly better
```groovy
if (!field1 || !field2) {
// code in if
} else {
// code in else
}
```

## Unit Tests
