# How to Add Extended Fields Without Modifying Tables

[Video Demonstration](https://www.bilibili.com/video/BV1wL411D7g7)

In the Excel data model, adding the `use-ext-field` tag to a data table enables global extended field support. The extended fields will be stored in the `nop_sys_ext_field` table.

![use-ext-field.png](use-ext-field.png)

The structure of the `nop_sys_ext_field` table is as follows:

| Column Name | Type |
|--------------|------|
| entity_name   | VARCHAR |
| entity_id     | VARCHAR |
| field_name    | VARCHAR |
| field_type    | INTEGER |
| decimal_value | DECIMAL |
| date_value    | DATE |
| timestamp_value | TIMESTAMP |
| string_value  | VARCHAR |

Based on the `field_type` setting, the specific field values are stored in corresponding columns such as `decimal_value`.

## ORM Configuration

At compile time, the `<orm-gen:ExtFieldsSupport>` tag identifies the `use-ext-field` configuration and generates associated properties.

```xml
<entity name="xxx.MyEntity">
  <relations>
    <to-many name="extFields" refEntityName="io.nop.sys.dao.entity.NopSysExtField" keyProp="fieldName">
      <join>
        <on leftProp="id" rightProp="entityId"/>
        <on leftValue="xxx.MyEntity" rightProp="entityName"/>
      </join>
    </to-many>
  </relations>
</entity>
```

> In a one-to-many relationship, if `keyProp` is set, it indicates that this property is the unique identifier. The `IOrmEntitySet` collection provides methods like `prop_get/prop_set` to access and modify corresponding entries based on this property.

For usage of extended fields in Java, refer to [TestExtFields.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-orm/src/test/java/io/nop/orm/dao/TestExtFields.java).

In a Java program, extended fields can be accessed through the following configuration:

```java
IOrmKeyValueTable field = (IOrmKeyValueTable) entity.getExtFields().prop_get("fldA");
entity.getExtFields().prop_set("fldA", value);
```

In XScript and EQL languages, `extFields.fldA.string` is equivalent to `entity.getExtFields().prop_get("fldA").getString()`. The `fldA` effectively retrieves the unique record from the one-to-many relationship using the `keyProp`.

### Alias for Extended Fields

To simplify access, extended fields can be assigned aliases:

```xml
<entity>
  <relations>
    <to-many name="extFields" ... />
  </relations>

  <aliases>
    <alias name="extFldA" propPath="extFields.fldA.string" type="String"/>
    <alias name="extFldB" propPath="extFields.fldB.int" type="Integer"/>
  </aliases>
</entity>
```

Adding the `alias` configuration allows `extFldA` and `extFldB` to become properties of the entity. In Java, these can be accessed via `entity.prop_get("fldA")`.


In XScript, you can access extended fields using the `entity.extFldA` property style, which is consistent with standard entity properties.

> If the ORM model file defines an alias (`alias`), it will generate corresponding getter/setter methods. This allows you to access extended fields via `entity.getExtFldA()` and `entity.setExtFldA(value)` in Java.
> 
> Once a getter/setter method is generated, you cannot use `entity.prop_get` to retrieve the value anymore. The `prop_get` method is intended for accessing non-existent extended properties on the entity. If you want to uniformly access both built-in and extended fields of an entity, you can use either:
> - `entity.orm_propValueByName(name)` method
> - Or the reflection-based `BeanTool.getProperty(entity, propName)` method.

Not only that, but in EQL syntax, you can directly use extended fields for filtering and sorting. The usage of extended fields is consistent with built-in entity fields.

```sql
select o.extFldA
from MyEntity o
where o.extFldA = '123'
order by o.extFldA
```

Using the alias mechanism allows for a smooth transition between extended and built-in fields: initially, use extended fields, and later add basic fields to the entity when performance bottlenecks occur.


## GraphQL Access

Adding `extFldA` and `extFldB` properties in the xmeta file enables GraphQL access to these extended fields.

```xml
<prop name="extFldA" displayName="Extended Field A" queryable="true" sortable="true" insertable="true" updatable="true">
    <schema type="String" domain="email" />
</prop>
```


## Dedicated Extended Fields Table

By default, all extended fields are stored in the `nop_sys_ext_field` table. This can lead to a single table becoming too large and performing poorly. To address this, you can add a `local-ext` tag to the entity table, which will generate a corresponding extended field table automatically. The name of the extended field table typically follows the pattern `original_table_name + '_ext'`, such as `nop_sys_notice_template_ext`.

The structure of the extended field table resembles `nop_sys_ext_field`, but it lacks the `entityName` field, so no filtering by entity name is required.


## Cross-Table Transformation

Many low-code platforms dynamically modify the database structure by using a vertical table approach. However, this "write-dead" approach is not suitable for dynamic schema changes. The Nop platform differs in that its built-in horizontal table transformation is a standard mathematical transformation, not limited to simple vertical tables. Any one-to-many or many-to-one relationships can be transformed into a one-to-one mapping using the EQL layer and the appropriate table structure.



```xml
<entity name="io.nop.app.SimsExam">
    <aliases>
        <alias name="extFldA" propPath="ext.fldA.string" type="String"/>
        <alias name="extFldB" propPath="ext.fldB.boolean" type="Boolean" notGenCode="true"/>
    </aliases>

    <relations>
        <to-many name="ext" refEntityName="io.nop.app.SimsExtField" keyProp="fieldName">
            <join>
                <on leftProp="id" rightProp="entityId"/>
                <on leftValue="io.nop.app.SimsExam" rightProp="entityName"/>
            </join>
        </to-many>
    </relations>
</entity>

* Any `to-many` relationship can configure the `keyProp` property to distinguish a single record in the collection.
* `ext.fldA.string` is equivalent to `((IOrmEntitySet) entity.getExt()).prop_get("fldA").getString()`
* Using an alias mechanism, we can assign an alias for complex property paths. For example, `extFldA` corresponds to `ext.fldA.string`.
* If `notGenCode` is marked, the corresponding getter and setter methods will not be generated in the Java code. Instead, you should use `entity.prop_get("extFldB")` to retrieve the value.
* In XScript or XPL template languages, extended properties are accessed using the same syntax as regular properties. For example, `entity.extFldB = true`.
* In EQL query language, it will automatically recognize `keyProp` and perform structural transformations based on that.

```sql
select o.children.myKey.value from MyEntity o
// Will be converted to:
select u.value from MyEntity o left join Children u on o.id = u.parent_id and u.key = 'myKey'
```

A collection will only be able to map a unique record if it has some kind of unique identifier. In an ORM using EQL, this is exactly what `o.a.b.c` represents as an associated property. The ORM will handle the transformation of this path into a join-based query.

```sql
select o.name from MyEntity o
where o.children.myKey1.intValue = 3 and o.children.myKey2.strValue like 'a%'
// Will be converted to:
select o.name from MyEntity o left join Children u1 on o.sid = u1.parent_id and u1.key = 'myKey1'
left join Children u2 on o.sid = u2.parent_id and u2.key = 'myKey2'
where u1.intValue = 3 and u2.strValue like 'a%'
```

A one-to-many relationship table will become a one-to-one relationship if a `key` filter condition is added.

```sql
select o.key1, o.children.myKey1.value, o.children.myKey2.value from MyEntity o
// Will be converted to:
select o.key1, u1.value, u2.value from MyEntity o left join Children u1 on o.sid = u1.parent_id and u1.key = 'myKey1'
left join Children u2 on o.sid = u2.parent_id and u2.key = 'myKey2'
```

According to the rules, we extract the relation table from `o.children.myKey`. On the mathematical level, this is a deterministic local transformation rule.
