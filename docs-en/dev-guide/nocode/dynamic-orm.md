# Built-in Workflow Support for Dynamic Entities

The NopORM engine is a complete ORM engine, not a narrow-purpose ORM tailored specifically for LowCode development. Based on the general mechanisms of NopORM, some LowCode-specific dynamic models are also built into the platform.

The `nop-dyn` module provides a `NopDynEntity` entity that includes a `nopFlowId` field. This field can automatically associate with the workflow engine, allowing the entity to be used as a business entity associated with workflow instances. No table needs to be created; simply adjust the ORM configuration to split the base `NopDynEntity` into new ORM entity objects (with different names and property names), where actual data is stored in the `nop_dyn_entity` table (horizontal) and the extension fields of the `nop_dyn_entity_ext` table.

The specific usage method is as follows:

## Add the file `_/_vfs/_delta/default/nop/dyn/orm/app.orm.xml` and include dynamic entity definitions within it.

```xml
<orm x:schema="/nop/schema/orm/orm.xdef" x:extends="super" xmlns:x="/nop/schema/xdsl.xdef" x:dump="false">
    <entities>
        <entity name="dyn.AppDynSalaryAdjustment" displayName="Salary Adjustment Application" x:prototype="NopDynEntityTemplate">
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

1. `x:prototype="NopDynEntityTemplate"` indicates that the entity inherits from the `NopDynEntityTemplate` template built into the `nop-dyn-dao` module. The `NopDynEntityTemplate` has enabled extension field support, where extension fields are stored in the `nop_dyn_entity_ext` table.
2. Dynamic entities are actually stored in the base `NopDynEntity` table, with each dynamic entity corresponding to a different `objType` condition. By configuring filters, multiple objects with different attributes can be generated from a single business object.
3. Aliases allow extension fields to be renamed with more meaningful names in XScript and EQL languages. In these scripts, aliases function as native properties of the entity.
4. `extFields` stores data horizontally in the `nop_dyn_entity` table. The `NopDynEntity` entity also reserves fields like `stringValue1`, `longValue1`, etc., for extension purposes. For performance optimization, key fields can be mapped to these reserved fields via aliases. Additionally, indexes can be created on these extension fields for improved performance.

## Complete Implementation

Inheriting from `NopDynEntityTemplate` is a convenient approach, but it has a limitation: you must customize the `nop-dyn-dao` module's `app.orm.xml` model file, as the `NopDynEntityTemplate` node is defined within this file.

```xml
<!--
    Must set tagSet to empty and remove inherited use-ext-field tags
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

If you do not want to customize `app.orm.xml`, you need to copy the definition of `NopDynEntityTemplate` (including its inherited `NopDynEntity`) into other modules for use.

> Copied definitions must set `x:abstract="true"`, indicating they are used as templates and will not be resolved into specific entity models at runtime.

1. The `tableView` attribute indicates that this is a view object created based on an existing table, so no CREATE TABLE statement is generated for it.
2. If you need to restrict the view from being updated, set the `readonly="true"` attribute.
