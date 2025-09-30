
# How to add extension fields to an entity without altering the table

[Video demo](https://www.bilibili.com/video/BV1wL411D7g7)

In the Excel data model, add the `use-ext-field` tag to a data table to enable global extension field support. Extension fields will be stored in the `nop_sys_ext_field` table.

![](use-ext-field.png)

The structure of the `nop_sys_ext_field` table is as follows:

|Column Name|Type|
|---|---|
|entity\_name|VARCHAR|
|entity\_id|VARCHAR|
|field\_name|VARCHAR|
|field\_type|INTEGER|
|decimal\_value|DECIMAL|
|date\_value|DATE|
|timestamp\_value|TIMESTAMP|
|string\_value|VARCHAR|

Depending on the setting of the `field_type` field, the actual value is stored in different columns such as `decimal_value`.

## ORM Configuration

At compile time, the [`<orm-gen:ExtFieldsSupport>`](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-orm/src/main/resources/_vfs/nop/orm/xlib/orm-gen.xlib) tag recognizes the `use-ext-field` configuration and automatically generates an association property:

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

> If `keyProp` is set in the one-to-many association configuration, it indicates that this property is a unique identifier. The `IOrmEntitySet` collection provides extended methods such as `prop_get/prop_set`, allowing you to directly access collection entries by this property.

For how to use extension fields, see [TestExtFields.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-orm/src/test/java/io/nop/orm/dao/TestExtFields.java)

In Java, we can access extension fields with the following configuration:

```java
IOrmKeyValueTable field = (IOrmKeyValueTable)entity.getExtFields().prop_get("fldA");
entity.getExtFields().prop_set("fldA",value);
```

In the XScript scripting language and in the EQL query language, `extFields.fldA.string` is equivalent to `entity.getExtFields().prop_get("fldA").getString()`.

`fldA` is essentially the unique record retrieved from a one-to-many collection according to `keyProp`.

### Extension field aliases

To simplify access, we can add aliases for extension fields:

```xml
<entity>
    <relations>
        <to-many name="extFields" ... />
    </relations>

    <aliases>
        <alias name="extFldA" propPath="extFields.fldA.string" type="String"/>
        <alias name="extFldB" propPath="extFields.fldB.int" type="Integer" />
    </aliases>
</entity>
```

After adding the `alias` configuration, `extFldA` and `extFldB` become properties on the entity. In Java, you can get extension properties via `entity.prop_get(fieldName)`.
In XScript, you can access them via `entity.extFldA` just like ordinary entity properties.

> If code is generated from an ORM model file that defines `alias`, corresponding get/set methods will be generated, so in Java you can access extension properties via `entity.getExtFldA()` and `entity.setExtFldA(value)`.
> 
> If get/set methods are generated, you can no longer use `entity.prop_get` to get the property value, because `prop_get` is for retrieving extension properties that do not physically exist on the entity. If you want a unified way to access both built-in and extension fields, use `entity.orm_propValueByName(name)` or reflection via `BeanTool.getProperty(entity, propName)`.

Moreover, in EQL query syntax, you can directly use extension fields for filtering and sorting; extension fields behave exactly the same as built-in fields on the entity:

```sql
select o.extFldA
from MyEntity o
where o.extFldA = '123'
order by o.extFldA
```

By leveraging aliases, we can achieve a smooth transition between extension fields and built-in fields: during initial development, you can first use extension fields; when performance becomes a bottleneck, add base fields to the entity—while keeping the property names unchanged in Java code.

## GraphQL Access

Add configurations for properties such as `extFldA` and `extFldB` in the xmeta file to access extension properties via GraphQL.

```xml
    <prop name="extFldA" displayName="Extension Field A" queryable="true" sortable="true" insertable="true" updatable="true">
        <schema type="String" domain="email" />
    </prop>
```

## Dedicated extension field tables

By default, all extension fields in the system are stored in the `nop_sys_ext_field` table, which may lead to an excessive data volume for a single table and poor performance. To mitigate this, you can add the
`local-ext` tag for the entity table, and the system will automatically generate a paired extension field table for the current entity. The extension table name is usually `original_table_name+'_ext'`, for example `nop_sys_notice_template_ext`.

The structure of the extension table is similar to `nop_sys_ext_field`, except it lacks the `entityName` field, so it does not need to be filtered by entity name.

## Vertical-to-horizontal table transformation

Many low-code platforms store all data in a vertical table to enable dynamic schema changes, and their vertical-to-horizontal transformation is a hardcoded special case. The Nop platform is different:
its built-in horizontal/vertical transformation is a standard mathematical transformation. Not only ordinary vertical tables—any one-to-many association can be converted into a one-to-one association, and one-to-one or many-to-one properties behave in EQL exactly the same as native table columns.

In the [TestExtFields](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-orm/src/test/java/io/nop/orm/dao/TestExtFields.java) unit test:

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
```

* You can configure the `keyProp` attribute on any `to-many` association to distinguish a unique record within the collection.
* `ext.fldA.string` is equivalent to `((IOrmEntitySet)entity.getExt()).prop_get("fldA").getString()`.
* Through the alias mechanism, we can create a shortcut for complex paths used to access extension fields. For example, in the configuration above, `extFldA` is equivalent to `ext.fldA.string`.
* If `notGenCode` is marked, no Java get/set methods will be generated for that property during code generation; you will need to get values via `entity.prop_get("extFldB")`.

In XScript or the XPL template language, the access syntax for extension properties and ordinary properties is exactly the same; you can directly use `entity.extFldB = true`.

In the EQL query language, `keyProp` is also automatically recognized and a structural transformation is performed.

```sql
select o.children.myKey.value from MyEntity o
// Will be transformed into:
select u.value from MyEntity o  left join Children u on o.id = u.parent_id and u.key = 'myKey'
```

That is, as long as a collection has some unique identifier, it can be mathematically flattened into an associated property with a unique access path. The ORM engine’s EQL query language is responsible for converting associated properties like `o.a.b.c` into join queries.

```sql
select o.name from MyEntity o
where o.children.myKey1.intValue = 3 and o.children.myKey2.strValue like 'a%'

-- Will be transformed into:

select o.name from MyEntity o left join Children u1
on o.sid = u1.parent_id and u1.key = 'myKey1'
left join Children u2
on o.sid = u2.parent_id and u2.key = 'myKey2'
where u1.intValue = 3
and u2.strValue like 'a%'
```

A one-to-many association table naturally becomes a one-to-one association table if you add a `key` filter condition:

```sql
select o.key1,o.children.myKey1.value,o.children.myKey2.value
from MyEntity o

-- Will be transformed into:
select o.key1, u1.value, u2.value
from MyEntity o left join Children u1 on
on o.sid = u1.parent_id and u1.key = 'myKey1'
left join Children u2
on o.sid = u2.parent_id and u2.key = 'myKey2'
```

According to the rules, extracting the associated table from `o.children.myKey` is, at the mathematical level, a deterministic local transformation rule.

<!-- SOURCE_MD5:82ca4a7f6145a6b74804d4d17cd06c27-->
