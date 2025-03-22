# Dictionary Translation

In the ORM model, dictionary tables can be defined directly and fields can have associated dictionary tables. In the GraphQL layer, corresponding label fields are automatically generated for these fields, such as generating `status_label` from `status`. For reference, see the implementation of `GenDictLabelFields` in [meta-gen.xlib](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/src/main/resources/_vfs/nop/core/xlib/meta-gen.xlib).

## Specifying Dictionary for Fields

If manually added, dictionary associations can be specified using the `prop` schema configuration.

```xml
<meta>
    <props>
        <prop name="status">
            <schema dict="wf/wf-step-status" />
        </prop>
    </props>
</meta>
```

## Dictionary Files

Dictionary files should be stored in the `_vfs/dict` directory, such as `wf/wf-step-status` corresponding to `_vfs/dict/core/wf/wf-step-status.dict.yaml`.

The YAML file stores objects of type `DictBean`, for example:

```yaml
label: Step State
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

`DictProvider` checks if the locale in the dictionary definition matches the external request locale. If not, it performs I18n translation.

Translation rules are as follows:
1. `dict.label.{dictName}`
2. `dict.option.label.{dictName}.{option.value}`

## Dictionary Tables in Databases

When importing `nop-sys-dao`, it automatically identifies dictionary definitions in the format `sys/xxx` and stores them in `nop_sys_dict` and `nop_sys_dict_option` tables.

## Java Enumerations

Dictionaries can be directly specified as Java Enum names, such as `dict="io.nop.xlang.xdef.XDefOverride"`. For those without `/`, they are treated as class names. `DictProvider` will attempt to load based on the class name.

```java
@Locale("zh-CN")
public enum XDefOverride {
    @Option("remove")
    @Description("Delete nodes in the base class")
    REMOVE("remove"),

    @Option("replace")
    @Description("Fully override existing nodes")
    REPLACE("replace")
}
```

In Java classes, field properties can be specified using `@Option`, `@Label`, and `@Description`.

## Using Business Tables as Dictionaries

In Excel models, if a table has been added with a `dict` label, it can be used as a dictionary via `obj/{bizObjName}`. This usage requires one column to have a `disp` label, which will be used as the display name, while the dictionary value is the record's primary key.

## Using SQL Statements as Dictionaries

In `sql-lib.xml`, SQL statements ending with `_dict` can be used as dictionaries. For example, `sql/test.my_dict` corresponds to `/_vfs/{moduleId}/sql/test.sql-lib.xml`.

```eql
<eql name="my_dict">
    select o.fldA as label, o.fldB as value
    from MyEntity o
</eql>
```

The SQL query returns field names that must match `DictOptionBean` properties in the Java class. These will be automatically wrapped into `DictOptionBean` objects.

## Configuration Options

* nop.core.dict.return-normalized-label
- Default: true
- Will concatenate the value and label of the dictionary together for display.
