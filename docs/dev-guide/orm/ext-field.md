# 扩展字段配置

在Excel数据模型中为数据表增加use-ext-field标签，即可启用全局扩展字段支持。扩展字段将保存到nop_sys_ext_field表中。

nop_sys_ext_field表的结构如下：

| 列名 | 类型 |
|-------|------|
| entity_name | VARCHAR |
| entity_id   | VARCHAR | 
| field_name  | VARCHAR |
| field_type  | INTEGER |
| decimal_value | DECIMAL |
| date_value  | DATE |
| timestamp_value | TIMESTAMP |
| string_value | VARCHAR|

根据field_type字段类型的设置，具体的字段值保存到decimal_value等不同的字段中。

## ORM配置

代码生成器根据use-ext-field标签会生成如下配置

````xml
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
````

关于扩展字段的使用可以参见 [TestExtFields.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-orm/src/test/java/io/nop/orm/dao/TestExtFields.java)

在Java程序中我们可以通过如下配置来访问扩展字段

````java
IOrmKeyValueTable field = (IOrmKeyValueTable)entity.getExtFields().prop_get("fldA");
entity.getExtFields().prop_set("fldA",value);
````

### 扩展字段别名

为了简化访问，我们可以为扩展字段增加别名

````xml
<entity>
    <relations> 
        <to-many name="extFields" ... />
    </relations>

    <aliases>
        <alias name="extFldA" propPath="extFields.fldA.string" type="String"/>
        <alias name="extFldB" propPath="extFields.fldB.boolean" type="Boolean" />
    </aliases>
</entity>
````

重新生成代码之后，我们就可以通过 entity.getExtFldA()和entity.setExtFldA(value)这两个方法来访问扩展属性。

不仅如此，在EQL查询语法中，可以直接使用扩展字段来进行过滤和排序，扩展字段的使用方式与实体上的内置字段完全一致
````sql
select o.extFldA 
from MyEntity o
where o.extFldA = '123'
order by o.extFldA
````

利用别名机制我们可以实现扩展字段和内置字段之间的平滑过渡：初次开发的时候可以先使用扩展字段，等到性能出现瓶颈时再在实体上增加基本字段，
此时可以保持Java代码中属性名不变。