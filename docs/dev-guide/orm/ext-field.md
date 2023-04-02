# 如何在不改表的情况下为实体增加扩展字段

[视频演示](https://www.bilibili.com/video/BV1wL411D7g7)

在Excel数据模型中为数据表增加use-ext-field标签，即可启用全局扩展字段支持。扩展字段将保存到nop_sys_ext_field表中。

![](use-ext-field.png)

nop_sys_ext_field表的结构如下：

| 列名              | 类型        |
| --------------- | --------- |
| entity_name     | VARCHAR   |
| entity_id       | VARCHAR   |
| field_name      | VARCHAR   |
| field_type      | INTEGER   |
| decimal_value   | DECIMAL   |
| date_value      | DATE      |
| timestamp_value | TIMESTAMP |
| string_value    | VARCHAR   |

根据field_type字段类型的设置，具体的字段值保存到decimal_value等不同的字段中。

## ORM配置

在编译期，[`<orm-gen:ExtFieldsSupport>`](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-orm/src/main/resources/_vfs/nop/orm/xlib/orm-gen.xlib)标签会识别use-ext-field配置，并自动生成关联属性：

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

> 一对多关联的配置中如果设置了keyProp，则表示这个属性是唯一标识属性。IOrmEntitySet集合提供了prop_get/prop_set等扩展方法，可以直接根据这个属性来存取对应的集合条目。

关于扩展字段的使用可以参见 [TestExtFields.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-orm/src/test/java/io/nop/orm/dao/TestExtFields.java)

在Java程序中我们可以通过如下配置来访问扩展字段

```java
IOrmKeyValueTable field = (IOrmKeyValueTable)entity.getExtFields().prop_get("fldA");
entity.getExtFields().prop_set("fldA",value);
```

### 扩展字段别名

为了简化访问，我们可以为扩展字段增加别名

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

增加alias配置之后，extFldA和extFldB会成为实体上的属性，在java中可以通过entity.prop_get(fieldName)来获取扩展属性。
在XScript中可以通过entity.extFldA这种属性方式来存取，与普通实体属性完全一致。

> 如果是根据定义了alias的orm模型文件来生成代码，则会自动生成对应get/set方法，这样在java中我们就可以通过entity.getExtFldA()和entity.setExtFldA(value)这两个方法来访问扩展属性。
> 
> 如果生成了get/set方法，就不能再使用entity.prop_get方法来获取属性值了。因为prop_get是用于获取实体上不存在的扩展属性的方法。如果希望通过统一的方式来获取实体内置字段和扩展字段，可以使用entity.orm_propValueByName(name)方法，或者使用BeanTool.getProperty(entity, propName)反射机制来获取。

不仅如此，在EQL查询语法中，可以直接使用扩展字段来进行过滤和排序，扩展字段的使用方式与实体上的内置字段完全一致

```sql
select o.extFldA 
from MyEntity o
where o.extFldA = '123'
order by o.extFldA
```

利用别名机制我们可以实现扩展字段和内置字段之间的平滑过渡：初次开发的时候可以先使用扩展字段，等到性能出现瓶颈时再在实体上增加基本字段，
此时可以保持Java代码中属性名不变。

## GraphQL访问

在xmeta文件中增加extFldA和extFldB等属性的配置，即可通过GraphQL来实现扩展属性访问。

```xml
    <prop name="extFldA" displayName="扩展字段A" queryable="true" sortable="true" insertable="true" updatable="true">
        <schema type="String" domain="email" />
    </prop>
```

## 专属的扩展字段表

缺省情况下系统中所有的扩展字段都存放在nop_sys_ext_field表中，这样可能会导致单个表数据量过大，性能低下。为了缓解这个问题，我们可以为实体表再增加
local-ext标签，则系统会自动为当前实体生成一个配对的扩展字段表，扩展表表名一般为原表名+'_ext'，例如nop_sys_notice_template_ext。

扩展表的结构与nop_sys_ext_field类似， 只是缺少entityName字段，不需要按照实体名进行过滤。