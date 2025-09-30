# Excel Data Model

You can configure the data model through Excel-formatted documents. The specific structure of the data model is defined by the import model file [orm.imp.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-orm/src/main/resources/_vfs/nop/orm/imp/orm.imp.xml).

In the Nop platform, there is no need for manual coding—simply add an `imp.xml` configuration file to enable parsing of Excel files. For example, the [Api model](api-model.md) is implemented using the same mechanism.

The `imp` import specification is relatively flexible regarding Excel format: it does not depend on the order of fields. Non-required fields can be removed from the template if not needed.
You only need to satisfy the following two rules to use the `imp` model for parsing:

1. Regular fields should follow the layout `Field Name - Field Value`, i.e., the cell to the right of the field name contains the field value.
2. For list fields, the rows below the field name contain the list of values. The first column must be the index column in numeric format. Note that the cell containing the field name must span all its columns.

## Configuration

* `registerShortName`: The entity name is the fully qualified class name including the package name. If `registerShortName` is set to `true`, the entity can also be accessed by its short class name without the package.
* `appName`: The prefix name for all submodules. The format must be `xxx-yyy`, for example `nop-sys`. It will automatically become a two-level subdirectory name in the virtual file system, such as `src/resources/_vfs/nop/sys`, and the platform restricts it to two levels to control the module scan scope. The Excel model name should match `appName` (i.e., the Excel file must be named `${appName}.orm.xlsx`), otherwise the automatically generated configuration in the `codegen` module will be incorrect.
* `entityPackageName`: The package name where entity objects reside, usually `xxx.dao.entity`, e.g., `io.nop.sys.dao.entity`.
* `basePackageName`: The parent package name for all submodules, e.g., `io.nop.sys`.
* `maven.groupId`: The `groupId` of the main project.
* `maven.artifactId`: The `artifactId` of the main project.
* `maven.version`: The version of the main project and all submodules.
* `platformVersion`: The version of the Nop platform.
* `dialect`: Generate table creation statements for the corresponding databases. It can be a comma-separated list of names, e.g., `mysql,oracle,postgresql`.
* `deltaDir`: Used only in Delta data models, specifying which delta customization directory to generate into.
* `allowIdAsColName`: Allow a column name of `id`. By default, `id` is reserved for primary key use, and any column named `id` will have its corresponding Java property name automatically renamed to `id_`. If this option is set, it remains `id`. Note that in the Nop platform, `id` is fixed and reserved for primary key usage.

## Data Domains

The concept of a data domain (`domain`) is similar to the `domain` concept in the PowerDesigner design tool. It provides a reusable name for fields that appear repeatedly and have business significance.

1. In general, the type definition set for a column in a data table should be consistent with the specified data domain. For example, the data domain `json-1000` has type `VARCHAR` with length `1000`; then fields using this data domain should also be set to `VARCHAR` with length `1000`. If inconsistent, model validation will warn.
2. Standard domains are standard business data types registered in `StdDomainRegistry` in the Nop platform, e.g., `var-name` indicates it must be a valid Java variable name.
3. During code generation, the following data domains are specially recognized: `version`, `createTime`, `createdBy`, `updateTime`, `updatedBy`, `delFlag`, `tenantId`. Fields marked with these domains will be automatically identified as optimistic lock fields, creation time fields, etc., supported by the ORM engine.
4. When generating `meta` files, a `domain` attribute under the `schema` node is generated automatically. In the frontend layout engine, it will automatically map to display and editing controls defined in `control.xlib`. For example, `domain=phone` corresponds to the `<edit-phone/>` control.

## Dictionary Tables

## Using a Data Table as a Dictionary Table

* Add the `dict` tag to a data table to indicate it can be used as a dictionary table. Dictionary tables should not contain large amounts of data and are typically displayed using dropdown lists.
* Then reference this data table as a dictionary via `obj/{objName}`, for example `obj/LitemallBrand`.

## Add a Dictionary Table in [Dictionary Definition]

Dictionary tables defined here will generate static dictionary files to the `{appName}-dao/src/main/resources/_vfs/nop/dict` directory during code generation.

* Name: The reference name of the dictionary table, e.g., `mall/order-status`.
* Chinese name, English name: The display names of the dictionary.
* Value type: The type of the dictionary value, typically `int` or `string`.

In dictionary item configuration:

* Value: The `value` of the dictionary item.

* Name: The `label` of the dictionary item.

* Code: If this attribute is configured, a constant definition will be generated in the `DaoConstants` constants class. For example, in `mall/order-status`, the `CREATED` code corresponds to generating:

```java
public interface _AppMallDaoConstants {

  /**
    * Order status: Unpaid
    */
  int ORDER_STATUS_CREATED = 101;
  ...
}
```

## Data Tables

Table names map directly to object names in GraphQL using camel case, so table names should be globally unique in principle (table names across different modules should not conflict). It is recommended that each module has its own special table name prefix, such as `litemall_xxx`, `nop_wf_xxx`, etc. Built-in table names in the Nop platform have the prefix `nop_`.

For each table in the Excel model, you can add a configuration field [Is View]. If set to true, SQL generation will skip it.

## Table Tags

* `dict`: Mark as a dictionary table; elsewhere it can be used as a dictionary via `obj/{objName}`.
* `mapper`: Generate a Mapper definition file and Mapper interface class similar to MyBatis for the table.
* `no-web`: A data object used by the backend; do not generate a separate frontend page entry for it.
* `no-tenant`: When tenant support is globally enabled, this table also does not automatically enable tenant filtering, such as the `nop_auth_user` and `nop_auth_tenant` tables.
* `kv-table`: Mark the current entity to implement the `IOrmKeyValueTable` interface. This interface requires the table to have fields such as `fieldName`, `fieldType`, `stringValue`, etc. See the field design of the `nop_sys_ext_field` table for details.
* `use-ext-field`: Add support for extension fields to the current entity, saving extension field values as data rows in the `nop_sys_ext_field` table. For a detailed introduction to extension fields, see [ext-field.md](../orm/ext-field.md).
* `many-to-many`: Mark an intermediate table for many-to-many associations. Based on this tag, multiple helper functions will be generated on the Java entity to automatically handle many-to-many associations. See [many-to-many.md](../orm/many-to-many.md).
* `not-gen`: Indicates a table defined in another model. When generating code from this model, do not generate entity definitions for this table.
* `view`: Read-only table; adding, updating, and deleting are not allowed.
* `log`: Log table; adding and updating are not allowed. Inserts are performed automatically by backend programs; only delete operations are allowed.
* `not-pub`: Indicates it will not participate in GraphQL object construction and will not be exposed externally.

## Field Tags

* `seq`: Automatically generate primary keys using `SequenceGenerator`. The Nop built-in implementations are `UuidSequenceGenerator` and `SysSequenceGenerator` (specified as the default generator on the bean with id `nopSequenceGenerator` in `/_vfs/nop/orm/beans/orm-defaults.beans.xml`). When the field type is a string, both default to generating UUIDs; if the field is numeric, they default to generating random numbers. If `SysSequenceGenerator` disables UUID, it will use the `nop_sys_sequence` table to record incrementing integer sequences and, when the field is a string, use its string form as the field value.
  In the `nop_sys_sequence` table, you can specify a sequence for each entity, named {entityName}@{colPropName}.

* `seq-default`: Different from `seq` in that if there is no sequence for the specified entity field in the `nop_sys_sequence` table, it will automatically choose the sequence named `default`. The configuration `nop.sys.seq.default-seq-init-next-value` sets the initial value of the sequence named `default`, defaulting to `1`.

* `var`: Indicates a randomly generated variable. In the automated testing framework, this field will be recorded as a variable and will be replaced with the variable name when recorded into the data file. For example, for the `userId` field in the `NopAuthUser` table, when creating a new user, the value of the `userId` column in the automatically recorded `nop_auth_user.csv` is `@var:NopAuthUser@userId`.

* `disp`: The display name of the data record. It will be used as `label` in dictionary tables or selection lists. If no other column is marked with `disp`, the primary key is used as the default display name.

* `parent`: Indicates whether the column is a parent attribute. If marked as `parent`, the default frontend list will provide an "Add child" dropdown menu item under "More" in the "Operations" column to directly add child data. If, in the [Association List], the [Associated Property Name] is also specified for this column, the frontend list will by default show a tree structure for parent-child data.

* `masked`: Indicates that it needs to be masked when printed in logs.

* `not-pub`: Indicates it will not be returned to the frontend.

* `sort`: Indicates the default sort field for the list. `sort-desc` indicates descending sort by this field. When generating `meta`, it corresponds to the `orderBy` section.

* `not-gen`: Used only in Delta models. Indicates that the field definition will be inherited from the base class and no Java property will be generated for this field.

* `del`: Used only in Delta models. Indicates that the field definition will be removed from the ORM model, so the field will not be used when accessing the database and the generated table creation statements will not include this field.

* `clock`: Marks that the field is determined dynamically based on the invocation time, so it needs to be recorded as a variable during automated unit test recording.

* `like`: Mark on text fields. It will automatically generate `ui:filterOp="contains"` on the property, indicating fuzzy query.

## Field Display

During code generation, list and form layout definitions are automatically generated in the `view.xml` page structure file. The list displays fields by default in the order of the model. For form layouts, if the number of fields exceeds 10, they are displayed in two columns; otherwise they are displayed in a single column.

To reduce manual adjustments to `view.xml`, you can configure the most commonly used display controls in the model:

* `X` indicates the field is not shown on the UI and used internally by the program,
* `R` indicates a read-only field,
* `C` indicates it cannot be modified but can be inserted,
* `S` indicates it occupies a single row,
* `L` indicates it is not displayed on the list.

## Field Data Types

Must be one of the types defined in `StdSqlType`, including `BOOLEAN`, `TINYINT`, `INTEGER`, `BIGINT`, `CHAR`, `VARCHAR`, `DATE`, `DATETIME`, `TIMESTAMP`, `DECIMAL`, `FLOAT`, `DOUBLE`, etc.

## Association Property Tags

In general, we only define `to-one` associations. The [Left Object] corresponds to the current table, and the [Associated Object] corresponds to the parent table. In a `join`, the [Left Property] corresponds to the foreign key property (not the database column name, but the Java property name, i.e., the camel case form of the column name). The [Associated Property] is the collection property in the parent entity corresponding to the child entity, and the [Right Property] of the `join` is the primary key property of the parent table.

* `pub`: Association properties are, by default, only used in backend programming and not exposed as GraphQL interfaces. They are exposed only after being marked with `pub`.
* `cascade-delete`: Delete associated objects when deleting the current object. Generally, `ref-cascade-delete` is used instead of `cascade-delete`.
* `ref-cascade-delete`: Automatically delete child table collection objects when the parent table is deleted.
* `ref-insertable`: Allow submitting child table data when the parent table is submitted, inserting them in one batch.
* `ref-updatable`: Allow updating child table data when the parent table is submitted.
* `ref-grid`: When generating the UI automatically, add a child table grid on the parent table editing page. Child table data will be submitted together with parent table data.
* `ref-connection`: Add a Connection-like paginated query property on the parent table for the child table, similar to the Relay framework. See [connection.md](../graphql/connection.md) for details.
* `ref-query`: Set `graphql:findMethod="findList"` on the parent table’s associated child collection property to support paginated queries on the child table. If no limit parameter is passed and no fetchSize is configured, by default only maxPageSize records are retrieved.

`ref-xx` denotes tags added to the property on the parent table corresponding to the child table. For example, adding the `ref-pub` tag on the child table association corresponds to adding the `pub` tag on the `parent.children` property.

**[Property Name] is the property name pointing from the child table to the parent table; [Associated Property Name] is the reverse, the collection property name pointing from the parent table to the child table.** If [Associated Property Name] is not set, all `ref-xxx` configurations are invalid.

In reverse engineering, the [Property Name] of the left object will be automatically generated based on existing database column names (for non-reverse engineering, you need to fill in the [Property Name] yourself). Suppose the current table is `PurchaseOrder`, it has a column `SUPPLIER_ID` associated with the parent table `Supplier`. A `to-one` association will be automatically generated for the `PurchaseOrder` table, with the [Property Name] as `supplier` (corresponding to `Supplier#getSupplier()` in Java). The [Associated Property Name] is the collection property name in the parent entity corresponding to the child entity. Unless you need to use this collection in memory, do not set the [Associated Property Name]. Especially when the number of items in the child table collection exceeds 1000, manipulating them in memory may consume too much memory and cause poor performance.

For each column in the target database, NopOrm will automatically generate a corresponding Java property with a camel case property name, e.g., `SUPPLIER_ID` corresponds to the Java property `supplierId`. For association object properties, it will guess the Java property name based on the database column names involved in the association. The rules are as follows:

1. If the column name is `XXX_ID`, then the object property name is `xxx`.
2. If the column name is not `XXX_ID` but `YYY`, then the object property name is `yyyObj`, i.e., add the `Obj` suffix to avoid conflicts with existing property names.

> Some may prefer column names that directly correspond to Java property names, such as the column `UserName`. However, according to database standard conventions, column names are usually normalized to all uppercase or all lowercase characters, making it impossible to recognize mixed-case scenarios when migrating between different databases.
> Therefore, the Nop platform assumes all column names will be automatically converted to uppercase and separated by underscores, and then named according to camel case rules when converted to Java properties.

Note that when converting to Java property names according to camel case rules, the first letter is generally lowercase. However, considering the Java Bean specification, there are special cases.
Suppose the database column name is `S_USER_NAME`, its corresponding Java Getter method is `String#getSUserName()`. According to the JavaBean specification, if the first two letters are both uppercase after removing the `get` prefix, then the Java property name also keeps the first letter uppercase. Therefore, the Java property name is `SUserName`, not `sUserName`.

### ref-grid Configuration
In the child table’s corresponding view.xml, you can configure the `sub-grid-edit` and `sub-grid-view` grids to control child table fields.
In the parent table’s xmeta file, you can set `ui:editGrid` and `ui:viewGrid` on the property corresponding to the child table to customize which child table grid to use. Their default values are `sub-grid-edit` and `sub-grid-view`.

### Sorting Conditions for Associated Collections
You can specify sorting conditions for an associated collection through the [Sorting Conditions] configuration, which corresponds to `refSet.sort`. For example, configure [Sorting Conditions] as `orderNo asc` in `nop-rule.orm.xlsx` for NopAuth.

## External Tables

Sometimes you need to introduce entities from other modules. In this case, write the full entity name and insert a placeholder sheet in which only the primary key is defined, and add the `not-gen` tag on the table.

For example, the following `course_evaluation` table references the `nop_auth_user` table.

![](ref-external-table.png)

The `nop_auth_user` table is defined in the `nop-auth` module, so you need to use the fully qualified class name again to represent the associated entity. Also insert a definition sheet for the `nop_auth_user` table.

![](external-table.png)

As it is an external table, you do not need to generate code for this table, so add the `not-gen` tag. You also need to specify the module where this table resides; the `Module ID` format is `A/B`, corresponding to a two-level directory structure.
For example, resources generated for the `nop-auth` package are stored in `/_vfs/nop/auth`. Its module name corresponds to `nop/auth`.

If the Excel model defines the parameter `Belonging Model`, then `biz:moduleId` will be added to both the automatically generated `_app.orm.xml` and the meta definitions of the associated fields.

Frontend controls are inferred by default from meta configurations via the `control.xlib` library. The `edit-relation` control will call the `XuiHelper.getRelationPickerUrl`
function to generate the link to the `picker` page. The link format is `"/" + moduleId + "/pages/" + bizObjName + "/picker.page.yaml"`. If the meta configuration of the associated child table sets the `biz:moduleId` attribute, then `moduleId` will use the configured value; otherwise, it will be automatically inferred from the path of the meta file.

## Common Issue Diagnosis
1. An association property is defined in the Excel model, but it is missing in the generated `app.orm.xml` file.
Note that association properties are defined in the [Association List]. For list structures, the first column of each item is the index column, which must be set to an integer value. ![](images/relation.png)

2. A dictionary is defined in Excel, but no `dict.yaml` file is generated.
Note that the first column of the dictionary must be numeric. When parsing a list structure, the first column is used to determine which content belongs to the list. The first column is the index column.

<!-- SOURCE_MD5:6577690688c43b5720c1c89ae5afe450-->
