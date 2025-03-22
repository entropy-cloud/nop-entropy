# Excel Data Model

Through the Excel formatted document, data models can be configured. The specific structure of the data model is defined by importing the `[orm.imp.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-orm/src/main/resources/_vfs/nop/orm/imp/orm.imp.xml)` model file.

In the Nop platform, manual programming is not required. Only the addition of the `imp.xml` configuration file is needed to enable Excel file parsing. For example, the [Api Model](api-model.md) also uses the same mechanism.

The `imp` import specification for Excel has relatively flexible requirements and does not depend on the order in which fields appear. Non-mandatory fields can be directly removed from the template if they are not required.

Two rules must be satisfied to use the `imp` model for parsing:

1. For regular fields, the layout should be `field name - field value`, where the field name cell's right side contains the field value.
2. For list fields, the field name cell's below cell contains the list of values. The first column must be a numerical index column in numerical format.

The field name cell must cover all columns.

---


## Configuration


## `registerShortName`
- The entity name is the fully qualified class name that includes the package name.
- If `registerShortName` is set to `true`, the short class name (without the package name) can also be used to access the entity.


## `appName`
- The prefix name for all submodules.
- The format must be `xxx-yyy`, such as `nop-sys`.
- It will automatically become a two-level subdirectory name in the virtual file system, such as `src/resources/_vfs/nop/sys`.
- The platform restricts it to two levels to control module scanning.

The entity's name should match `appName` (i.e., the Excel file name must be `${appName}.orm.xlsx`). Otherwise, auto-generation of the `codegen` module's configuration will be incorrect.


## `entityPackageName`
- The package name where the entity objects reside.
- Typically, it is `xxx.dao.entity`, such as `io.nop.sys.dao.entity`.


## `basePackageName`
- The parent package name for all submodules, such as `io.nop.sys`.
- `maven.groupId`: The main project's groupId.
- `maven.artifactId`: The main project's artifactId.
- `maven.version`: The version of the main project and all submodules.
- `platformVersion`: The Nop platform version.
- `dialect`: The dialect for generating table statements. It can be a comma-separated list, such as `mysql,oracle,postgresql`.
- `deltaDir`: Used only in delta modeling to specify the delta customization directory.


- Allows using 'id' as the column name.
- By default, 'id' is preserved for primary key usage. If this option is set, all columns named 'id' will be automatically renamed to 'id_' in Java code.
- If `allowIdAsColName` is enabled, 'id' remains as 'id'.

Note: In the Nop platform, 'id' is always exposed for primary key usage.

---



The concept of a data domain (`domain`) is similar to the `domain` concept in PowerDesigner. It provides a reusable name for frequently occurring fields with business meaning.

1. Typically, the data table's column type should match the specified data domain. For example, if the data domain is `json-1000`, its data type is `VARCHAR(1000)`. If the field's type does not match, model validation will fail.
2. Standard domains are registered in `StdDomainRegistry` within the Nop platform. Each domain must follow specific naming conventions, such as `var-name` for Java variable names.

3. During code generation, special handling is applied for the following data domains: `version`, `createTime`, `createdBy`, `updateTime`, `updatedBy`, `delFlag`, and `tenantId`. Fields marked with these domains will be recognized as optimistic locking fields or creation/copy timestamps in the ORM engine.

4. When generating metadata (`meta` files), the system automatically assigns the `domain` attribute to the schema node defined in `_control.xlib`. For example, if `domain=phone`, it corresponds to `<edit-phone/>`.

---





- Add a `dict` tag to the data table to indicate that it can be used as a dictionary. Dictionary tables typically contain small amounts of data and are displayed using dropdown lists.
- Use `{objName}` in URLs to reference this data table as a dictionary, such as `obj/LitemallBrand`.

---




Herein are defined dictionary tables which, upon code generation, will generate static dictionary files into the `${appName}-dao/src/main/resources/_vfs/nop/dict` directory.

* **Name**: Reference name of the dictionary, e.g., `mall/order-status`
* **Chinese Name/English Name**: Display name of the dictionary in Chinese and English
* **Value Type**: Type of the dictionary value, generally either `int` or `string`


## Dictionary Item Configuration

Within the dictionary configuration:

* **Value**: Value of the dictionary item (`value`)
* **Name**: Label of the dictionary item (`label`)
* **Code**: If this property is configured, a constant will be generated in the `DaoConstants` class, e.g., `mall/order-status` will have `CREATED` corresponding to the generated code.

```java
public interface _AppMallDaoConstants {

    /**
     * Order status: unpaid
     */
    int ORDER_STATUS_CREATED = 101;
    ...
}
```


## Data Table

The table name will be directly mapped to the GraphQL object name following camelCase rules, thus requiring global uniqueness (no conflicts between different modules). It is recommended that each module has a unique prefix for its table names, such as `litemall_xxx`, `nop_wf_xxx`, etc. Built-in tables in the Nop platform will have the prefix `nop_`.


## Data Table Tags

* **`dict`**: Marks the table as a dictionary. Other parts can use `${objName}` to access data from this dictionary
* **`mapper`**: Generates MyBatis-like mapper definitions and interface classes for the table
* **`no-web`**: Indicates that this table is used in the back-end, not generated for front-end entry points
* **`no-tenant`**: When global tenant support is enabled, this table does not automatically apply tenant filtering (e.g., `nop_auth_user` and `nop_auth_tenant` tables)
* **`kv-table`**: Marks the entity as requiring implementation of the `IOrmKeyValueTable` interface. This interface requires fields such as `fieldName`, `fieldType`, and `stringValue`, referencing `nop_sys_ext_field` table design
* **`use-ext-field`**: Enables extended field support, storing extended field values in the `nop_sys_ext_field` table. Detailed documentation is available in [ext-field.md](../orm/ext-field.md)
* **`many-to-many`**: Indicates a many-to-many relationship table. Based on this tag, multiple helper functions will be generated for Java entities to automatically handle M2M relationships. Detailed documentation is available in [many-to-many.md](../orm/many-to-many.md)
* **`not-gen`**: Indicates that this table is defined elsewhere. When generating code for this model, no entity definitions are generated for this table
* **`view`**: Read-only table; disallows insert, update, and delete operations (except for back-end management)
* **`log`**: Log table; disallows inserts and updates. Inserts are handled automatically by the back-end, while deletes are allowed
* **`not-pub`**: Indicates that this table is not involved in GraphQL object construction and is not exposed to external systems



* **`seq`**: Uses `SequenceGenerator` to auto-generate primary keys. Nop provides built-in generators such as `UuidSequenceGenerator` and `SysSequenceGenerator` (located in `/_vfs/nop/orm/beans/orm-defaults.beans.xml`). For string fields, UUIDs are generated by default; for numeric fields, random numbers are generated. If `SysSequenceGenerator` is disabled for UUIDs, the `nop_sys_sequence` table will be used to store incrementing integer sequences. For each entity, you can specify a custom sequence in `nop_sys_sequence`, using `{entityName}@{colPropName}`
* **`seq-default`**: Similar to `seq`, but if no specific sequence is defined for a field in `nop_sys_sequence`, it will automatically use the default sequence named `default-seq-init-next-value`. The default starting value is `1`
* **`var`**: Indicates a randomly generated variable. In automated testing frameworks, this field will be recorded as a variable. For example, the `userId` field in `NopAuthUser` table will have its value replaced with `@var:NopAuthUser@userId` when generating test data files


The following document describes the configuration options for fields in a typical ORM system. Each field can be configured with various properties to control its behavior and appearance.


## Field Display
- `disp`：Field display name. If not specified elsewhere, the field's primary key is used as the display name.
- `parent`：Indicates whether this field is a parent field. If marked as `parent`, the default behavior will display hierarchical data in the front-end list's more column, allowing for easier addition of child data. If both the association list and the association property are specified, the front-end list will display tree-like structure.


## Field Masking
- `masked`：Indicates whether the field value should be masked when logged. This is commonly used for sensitive information like passwords or credit card numbers.


## Field Accessibility
- `not-pub`：Indicates that this field is not accessible in the front-end interface.
- `sort`：Default sorting field for the list. `sort-desc` indicates that the sorting should be in descending order.
- `not-gen`：Used to indicate fields that are not automatically generated. These fields will inherit their definition from a base class and will not have corresponding Java properties generated.


- `del`：Indicates whether this field is deletable through the ORM. This setting affects how the database interacts with the application, specifically in delete operations.


- `clock`: Marks fields that are timestamped based on the system clock for automatic recording during automation testing.
- `like`: Indicates a similarity match operation when searching text fields. The default behavior is to use a containment check (`ui:filterOp="contains"`).


```markdown
- `X`：Indicates that this field should not be displayed in the interface, while still being used internally.
- `R`：Read-only field; cannot be edited but can be viewed.
- `C`：Field is read-only and does not allow modifications, but allows insertions.
- `S`：Field occupies a single row in the display.
- `L`：Indicates that this field should not be displayed in lists.
```


The following data types are supported based on `StdSqlType`:
- `BOOLEAN`
- `TINYINT`
- `INTEGER`
- `BIGINT`
- `CHAR`
- `VARCHAR`
- `DATE`
- `DATETIME`
- `TIMESTAMP`
- `DECIMAL`
- `FLOAT`
- `DOUBLE`


By default, associations are configured as `to-one`. The following properties control association behavior:
- `pub`: Indicates whether this field is exposed through GraphQL. If not set, it remains internal.
- `cascade-delete`: Indicates that when a parent record is deleted, all associated records should also be deleted. This is typically handled via `ref-cascade-delete` in the ORM.
- `ref-cascade-delete`: Handles deletion of child records when the parent is deleted.
- `ref-insertable`: Allows inserting child records when the parent is inserted.
- `ref-updatable`: Allows updating child records when the parent is updated.
- `ref-grid`: Displays a grid of child data in the parent's interface.
- `ref-connection`: Handles pagination of child data using a connection mechanism, similar to relay. For details, refer to [connection.md](../graphql/connection.md).
- `ref-query`: Facilitates fetching child data with pagination. If no limit is provided, only maxPageSize is fetched.


During reverse engineering:
- Fields marked with `to-one` will have corresponding associations in the ORM.
- Child tables will have fields defined based on their parent's structure.

For example, if a `PurchaseOrder` entity has a `SUPPLIER_ID`, it will automatically generate a `to-one` association to `Supplier` and include a `supplier` property. If no specific association is needed, the field remains as `SUPPLIER_ID`.


In memory manipulation may cause excessive memory consumption, leading to performance degradation.

For each column in the target database, NopOrm will automatically generate a corresponding Java attribute. The attribute name follows camel case convention, such as `SUPPLIER_ID` corresponds to `supplierId`. For related object attributes, the Java attribute name is inferred based on the relationship involving the database column name.

The naming rule is as follows:

1. If the field name is `XXX_ID`, the object attribute name will be `xxx`.
2. If the field name is not `XXX_ID` but `YYY`, the object attribute name will be `yyyObj`. Adding `Obj` suffix helps avoid conflicts with existing attribute names.

> Some users are accustomed to column names directly corresponding to Java attribute names, such as `UserName`. However, according to database standards, column names are typically standardized to all uppercase or all lowercase characters, causing issues during database migrations due to case sensitivity. Therefore, Nop assumes that all column names should be automatically converted to uppercase using underscores as separators. When converting to Java attribute names, the camel case convention is applied.

> Note that according to JavaBean conventions, there are special cases. For example, if the database column name is `S_USER_NAME`, the corresponding Java Get method is `String#getSUserName()`. Removing the `get` prefix and retaining uppercase letters for consecutive letters results in the attribute name `SUserName` instead of `sUserName`.


### Sorting Conditions for Reference Sets

The sorting condition for reference sets can be configured using `[sorting condition]`. For example, configuring `refSet.sort` in `nop-rule.orm.xlsx` with `[sorting condition]` set to `orderNo asc`.


## External Tables and Module Structure

When importing external modules, the full name of the entity must be specified. For example, adding an `not-gen` tag to an external table like `course_evaluation` and referencing it in `nop_auth_user` requires defining it in its module.


## Example: External Table Relation
The `nop_auth_user` table is defined in the `nop-auth` module, so use the fully qualified name. For example:
- Table name: `nop_auth_user`
- Module path: `/_vfs/nop/auth`

When generating Java attributes for columns like `SUPPLIER_ID`, the attribute name becomes `supplierId`. For non-`XXX_ID` columns like `yyy`, the attribute becomes `yyyObj`.



Controls on the frontend, such as `control.xlib`, are configured by `[meta configuration]`. For example, the `edit-relation` control calls `XuiHelper.getRelationPickerUrl` to generate links like `/{moduleId}/pages/{bizObjName}/picker.page.yaml`. If a child table's meta configuration includes `biz:moduleId`, it will use that value; otherwise, it will be determined based on the meta file path.


1. **Missing Association Attributes in Excel Model**  
   - The Excel model defines association attributes like `relatedField`.
   - However, the generated `app.orm.xml` may not include them.
   - Ensure `[association list]` is defined in the XML with the correct data type for the first column (usually a number).  
   - Example: ![images/relation.png](images/relation.png)

2. **Missing Dictionary Entries**  
   - If a dictionary is defined in Excel but not generated in `dict.yaml`, check if the first column of the list is numeric.

