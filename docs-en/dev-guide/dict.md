# Dictionary Table Translation

In the ORM model, you can define dictionary tables directly and associate fields with a dictionary table. The GraphQL layer will then automatically generate the corresponding label fields for those fields, for example, status produces status\_label. See the implementation of the GenDictLabelFields tag in [meta-gen.xlib](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/src/main/resources/_vfs/nop/core/xlib/meta-gen.xlib).

## Assign a dictionary table to a field

If added manually, you can specify the dictionary table via the prop's schema configuration.

```xml
<meta>
    <props>
        <prop name="status">
            <schema dict="wf/wf-step-status" />
        </prop>
    </props>
</meta>
```

## Dictionary table files

You can store dictionary table files under the `_vfs/dict` directory. For example, `wf/wf-step-status` corresponds to `_vfs/dict/core/wf/wf-step-status.dict.yaml`.

This YAML file stores a Java object of type DictBean, for example:

```yaml
label: Step Status
locale: zh-CN
valueType: int
description:
options:
  - label: Created
    value: 0
    description:

  - label: Paused
    value: 10
    description:
```

## Internationalization

When loading dictionary tables, DictProvider checks whether the locale defined in the dictionary matches the externally requested locale. If they differ, it will automatically perform I18n translation.

The translation rules are:

1. dict.label.{dictName}
2. dict.option.label.{dictName}.{option.value}

## Dictionary tables maintained in the database

If you include the nop-sys-dao module, it will automatically recognize dictionary table definitions in the form `sys/xxx`. They are stored in the tables nop\_sys\_dict and nop\_sys\_dict\_option.

## Java enum classes

A dict can directly specify a Java Enum class name, for example, `dict="io.nop.xlang.xdef.XDefOverride"`. For a dictName that does not contain `/` and can be treated as a class name, DictProvider will attempt to load it as a class.

```java

@Locale("zh-CN")
public enum XDefOverride {
    @Option("remove")
    @Description("Remove nodes from the base class")
    REMOVE("remove"),

    @Option("replace")
    @Description("Completely override the original node")
    REPLACE("replace")
}
```

In Java classes, you can use `@Option`, `@Label`, and `@Description` to specify option attributes.

## Use a business table as a dictionary table

In the Excel data model, if you add the dict tag to a table, you can use `obj/{bizObjName}` to treat the business table as a dictionary table. This requires one column to have the disp tag; it will be used as the display name, and the dictionary item's value is the record's primary key.

## Use an SQL statement as a dictionary table

In sql-lib.xml, SQL statements whose names end with `_dict` can be used as dictionary tables. For example, `sql/test.my_dict` corresponds to the SQL statement named my\_dict in `/_vfs/{moduleId}/sql/test.sql-lib.xml`.

```
  <eql name="my_dict">
    select o.fldA as label, o.fldB as value
    from MyEntity o
  </eql>
```

The field names returned by the dictionary table SQL must match the property names of the Java class DictOptionBean; they will be automatically wrapped as DictOptionBean objects.

## Configuration options

- nop.core.dict.return-normalized-label  
Default is true; it will display the dictionary's value and label concatenated together.

<!-- SOURCE_MD5:110c0d2d10d64ad5267c42f2050f005f-->
