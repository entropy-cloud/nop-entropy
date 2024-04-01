# 内置工作流支持的动态实体

NopORM引擎是一个完整的ORM引擎，不是专门为LowCode开发定制的一种狭窄用途的ORM引擎。基于NopORM的通用机制，平台也内置了一些LowCode专用的动态模型。

nop-dyn模块提供了一个NopDynEntity实体，它具有nopFlowId字段，可以自动与工作流引擎关联，作为工作流实例关联的业务实体来使用。无需建表，只要调整
ORM配置即可利用基础的NopDynEntity分化出新的ORM实体对象（实体名和属性名不同），实际数据保存在nop\_dyn\_entity表（纵表）以及nop\_dyn\_entity\_ext表的扩展字段中。

具体使用方法如下：

## 增加`/_vfs/_delta/default/nop/dyn/orm/app.orm.xml`文件，在其中增加动态实体定义

```xml
<orm x:schema="/nop/schema/orm/orm.xdef" x:extends="super" xmlns:x="/nop/schema/xdsl.xdef" x:dump="false">

    <entities>

        <entity name="dyn.AppDynSalaryAdjustment" displayName="调薪申请" x:prototype="NopDynEntityTemplate">
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

1. `x:prototype="NopDynEntityTemplate`表示从nop-dyn-dao模块内置的NopDynEntityTemplate模板继承一些配置。NopDynEntityTemplate已经开启了扩展字段支持，
   扩展字段会存放在nop\_dyn\_entity\_ext表中。
2. 动态实体实际存放在底层的NopDynEntity表中，只是每个动态实体都对应于不同的objType限制条件。通过filter配置过滤条件，从而从同一个业务对象中分化出多个具有不同属性的对象。
3. 通过alias可以将扩展字段重命名为具有业务含义的更加简洁的名称，在XScript脚本语言以及EQL查询语言中，alias可以看作是实体原生属性来使用。
4. extFields是将数据作为纵表保存，在NopDynEntity实体上还预留了stringValue1, longValue1等扩展字段，如果需要优化性能，可以将一些关键字段
   通过alias映射到这些预留字段上。预留字段上还可以建立索引，性能比extFields纵表扩展要好。

## 完整实现

从NopDynEntityTemplate继承是一种便捷的方式，但是它有一个限制就是必须定制nop-dyn-dao中的app.orm.xml模型文件，因为NopDynEntityTemplate节点是定义在这个文件中。

```xml
<!--
    必须将tagSet设置为空，去除继承的use-ext-field标签
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

如果不想定制app.orm.xml，则需要将NopDynEntityTemplate的定义（包括它继承的NopDynEntity）的定义拷贝到其他模块中使用。

> 拷贝过来的定义都要设置x:abstract=true，这表示它们仅仅作为模板使用，不会最终解析为具体的实体模型。

1. tableView表示本实体是在已有表的基础上所作的视图对象，不需要为本实体生成建表语句。
2. 如果需要限制视图不允许更新，可以设置readonly=true属性
