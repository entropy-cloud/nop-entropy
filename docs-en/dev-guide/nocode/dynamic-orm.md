
# Dynamic Entities Supported by Built-in Workflows

The NopORM engine is a full-fledged ORM engine, not a narrowly scoped ORM tailored specifically for Low-Code development. Based on the generic mechanisms of NopORM, the platform also includes several Low-Code-specific dynamic models.

The nop-dyn module provides a NopDynEntity entity, which includes a nopFlowId field that can automatically associate with the workflow engine, serving as the business entity linked to a workflow instance. No table creation is required—by adjusting the ORM configuration, you can derive new ORM entity objects from the base NopDynEntity (with different entity and property names). Actual data is stored in the nop_dyn_entity table (vertical table) and in the extension fields of the nop_dyn_entity_ext table.

Usage details are as follows:

## Add the `/_vfs/_delta/default/nop/dyn/orm/app.orm.xml` file and include the dynamic entity definition in it

```xml
<orm x:schema="/nop/schema/orm/orm.xdef" x:extends="super" xmlns:x="/nop/schema/xdsl.xdef" x:dump="false">

    <entities>

        <entity name="dyn.AppDynSalaryAdjustment" displayName="Salary Adjustment Request" x:prototype="NopDynEntityTemplate">
            <filters>
                <filter name="nopObjType" value="AppDynSalaryAdjustment"/>
            </filters>

            <aliases>
                <alias name="employeeId" type="String" propPath="extFields.employeeId.string"/>
                <alias name="salary1" type="Double" propPath="extFields.salary1.double"/>
                <alias name="salary2" type="Double" propPath="extFields.salary2.double"/>
            </aliases>
        </entity>
    </entities>
</orm>
```

1. `x:prototype="NopDynEntityTemplate"` indicates inheriting certain configurations from the built-in NopDynEntityTemplate in the nop-dyn-dao module. NopDynEntityTemplate has already enabled support for extension fields, which are stored in the nop_dyn_entity_ext table.
2. Dynamic entities are actually stored in the underlying NopDynEntity table, but each dynamic entity corresponds to a different objType constraint. By configuring filters, multiple objects with different attributes are derived from the same business object.
3. Through aliases, extension fields can be renamed to concise names with business meaning. In the XScript scripting language and the EQL query language, aliases can be treated as native entity properties.
4. extFields stores data in a vertical table. The NopDynEntity entity also reserves extension fields like stringValue1 and longValue1. If performance optimization is needed, you can map certain key fields via aliases to these reserved fields. Indexes can be created on reserved fields, providing better performance than the extFields vertical-table extension.

## Complete Implementation

Inheriting from NopDynEntityTemplate is a convenient approach, but it has the limitation that you must customize the app.orm.xml model file in nop-dyn-dao, as the NopDynEntityTemplate node is defined in that file.

```xml
<!--
    You must set tagSet to empty to remove the inherited use-ext-field tag
-->
<entity name="NopDynEntityTemplate" x:abstract="true" registerShortName="true"
        x:prototype="io.nop.dyn.dao.entity.NopDynEntity" tableView="true" tagSet="">
    <relations>
        <to-many name="extFields" refEntityName="io.nop.dyn.dao.entity.NopDynEntityExt" keyProp="fieldName">
            <join>
                <on leftProp="id" rightProp="entityId"/>
            </join>
        </to-many>
    </relations>
</entity>
```

If you prefer not to customize app.orm.xml, copy the definition of NopDynEntityTemplate (including the definition it inherits from NopDynEntity) into another module for use.

> All copied definitions must set x:abstract=true, indicating they are used only as templates and will not be resolved into concrete entity models.

1. tableView indicates that this entity is a view object built on existing tables; no table creation statements need to be generated for this entity.
2. If you need to restrict the view from being updatable, you can set the readonly=true property.

<!-- SOURCE_MD5:3edffbbfbd2ef304bb649789df73ec13-->
