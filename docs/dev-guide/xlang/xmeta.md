# 元数据

Nop平台提供了标准化的对象元数据模型XMeta，所有需要定义对象结构的地方都统一使用XMeta模型来定义。

* XMeta和XDef可以相互转换。XDef对应于XML结构，而XMeta对应于对象属性结构
* NopGraphQL会根据XMeta配置生成对外暴露的GraphQL类型。

## 实体元数据

Nop平台的代码生成器会自动根据ORM模型为每个实体生成一个对应的XMeta文件，每个实体属性都会对应生成一个对象prop配置，在此基础上还会根据一对多、多对多等关联配置生成生成一些辅助属性。

> 缺省配置下会使用/nop/template/meta模板生成到{appName}-meta模块中。

## 对象配置

对象级别存在如下配置

```xml
<meta>
    <entityName xdef:value="string"/>

    <primaryKey xdef:value="word-set"/>

    <!--
    用于显示的字段，例如displayName等。选择控件会使用该字段
    -->
    <displayProp xdef:value="string"/>

    <keys xdef:key-attr="name" xdef:body-type="list">
        <!--
        除主键之外的其他唯一键
        -->
        <key name="!string" props="!word-set" displayName="string" xdef:name="ObjKeyModel"/>
    </keys>

    <!--
    过滤条件。会追加到GraphQL的query查询条件中。因为在update和view的时候也会使用检查这里的过滤条件，
    所以一般就是简单的等于条件的过滤，暂时不考虑更复杂的查询条件。更复杂的业务相关的查询条件应该写在Biz或者sql-lib文件中
    -->
    <filter xdef:value="filter-bean"/>

    <!-- 排序条件。追加到GraphQL的query查询条件中 -->
    <orderBy xdef:ref="query/order-by.xdef"/>

    <!--
    树形结构
    @parentProp 对应于parentId等指向父节点的字段
    @rootParentValue 根节点字段查找的初始值，例如: __null 表示查找根节点为 null 的，0 则表示查找根节点为 0 的
    @childrenProp 对应于父对象中对应于子对象的集合属性，例如children
    @levelProp 树形结构的级别树形，例如level=1表示一级节点，2表示二级节点等。
    @rootLevelValue 根节点所对应的level字段的值
    -->
    <!-- 确定 parentId 根节点属性值需要按以下顺序：levelProp-> parentProp 进行判断，如果没有配置则使用parentId=__null来过滤得到根节点 -->
    <tree isLeafProp="string" parentProp="!string" childrenProp="string"
          levelProp="string" rootLevelValue="string" xdef:name="ObjTreeModel"/>
</meta>
```

* entityName指定对应的实体名称。如果没有对应于ORM层面的实体对象，则这里可以为空。meta元数据并不是只能应用于数据库实体
* primaryKey指定主键字段列表。如果没有主键，则可以为空
* displayProp用于指定显示字段，当作为下拉选项或者选中选项时，该字段会被用于对象显示。
* key指定除主键外的其他唯一键。当新建实体或者修改实体时会自动检查唯一键不冲突。
* filter可以指定对象级别自动设置的过滤条件，比如`<eq name="status" value="1" />`可以用于过滤活跃状态的实体。新建或者修改操作时会自动设置filter中的属性值，确保不会突破过滤条件限制。
* orderBy指定对象级别的缺省排序条件
* tree用于补充树形结构相关的字段信息

## prop配置

prop节点支持如下属性配置：

|名称|缺省值|说明|
|---|---|---|
|tagSet||逗号分隔的扩展标签，在代码生成时会使用|
|published|true|是否发布为GraphQL属性，可以通过服务访问|
|insertable|true|是否允许save操作的参数中包含此属性|
|updatable|true|是否允许通过update操作修改此属性|
|queryable|false|是否允许在查询条件中包含此属性|
|sortable|false|是否允许按照此属性进行排序|
|lazy|false|利用REST协议访问实体对象时是否缺省不返回此属性|
|allowFilterOp||允许针对此字段执行哪些查询运算，例如gt,ge,contains,like等，缺省只允许in,eq|
|ui:filterOp|eq|生成前台查询表单时缺省按照什么查询运算生成查询条件|
|ui:control||可以直接指定缺省使用哪种控件来展现此属性，在control.xlib中会根据这里的配置查找实际对应的控件|
|ui:labelProp||如果指定了labelProp，则生成表单和表格时查看模式下实际显示的是label字段, GraphQL每次请求也会多返回label字段用于前台显示|
|ui:maskPattern||如果指定了掩码模式，则GraphQL返回前台的数据会自动调用StringHelper.maskPattern函数进行掩码操作|
|biz:codeRule||如果指定了编码规则，则新建实体的时候如果前台没有提交编码值，则会根据编码规则名称查找编码规则配置自动生成一个编码|
|ui:maxUploadSize||文件上传时最大允许的文件大小，可以是20M这种写法|
|ui:editGrid|sub-grid-edit|对于子表集合属性，如果tagSet中包含grid标签，则表示在新建和修改时使用嵌入表格来编辑子表，主子表同时提交。通过此参数指定使用子表的哪个表格配置来编辑|
|ui:viewGrid|sub-grid-view|对于子表集合属性，指定使用子表的哪个表格配置来展现数据|

prop还具有如下节点配置：

```xml

<prop>
    <!--
        配置字段级别的权限约束

        @for 如果为all，则表示所有操作都可以匹配这个权限约束。
        如果设置为read，则表示当读取的时候使用此约束。此时如果没有配置write所对应的auth，则实际不允许修改
        如果设置为write，则表示读取和修改的时候都使用此约束
    -->
    <auth for="!xml-name" xdef:unique-attr="for" xdef:name="ObjPropAuthModel" roles="csv-set"
          permissions="multi-csv-set"/>

    <!--对应graphql的argument-->
    <arg xdef:name="ObjPropArgModel" name="!var-name" mandatory="!boolean=false" displayName="string"
         xdef:unique-attr="name">
        <description xdef:value="string"/>
        <schema xdef:ref="schema.xdef"/>
    </arg>

    <!--新增或者修改的时候如果前台没有发送本字段的值，则可以根据autoExpr来自动计算得到-->
    <autoExpr when="!csv-set" xdef:bean-body-prop="source" xdef:name="ObjConditionExpr" xdef:value="xpl"/>

    <!--对前台输入的值进行适配转换-->
    <transformIn xdef:value="xpl"/>

    <!--后台返回的值可能需要进行格式转换-->
    <transformOut xdef:value="xpl"/>

    <!--根据当前实体生成动态属性。getter和setter都是后台实体对象层的功能，类似Java对象上的get/set-->
    <getter xdef:value="xpl"/>

    <!--对外部传入的值进行处理，可能会设置entity对象的属性-->
    <setter xdef:value="xpl"/>
</prop>
```

* 通过auth配置可以指定字段级别的权限控制规则。read和write可以具有不同的权限设置
* 通过arg配置可以设置GraphQL协议所支持的请求参数。也就是说prop实际可能对应的是一个支持请求参数的动态属性函数。
* autoExpr用于新增或者修改的时候自动计算属性值，比如根据子表中的商品价格自动计算汇总价格等，执行上下文中存在entity对象，对应于当前实体。
* transformIn用于将前台提交的参数值转换为后台使用的值，执行上下文中存在value, data等变量，data对应于前台传入的请求参数，value对应于属性值
* transformOut用于将实体上的属性值转换为返回到前台的结果值。transformOut根据上下文中的value,data等变量进行处理，返回结果值
* getter和setter相当于替代实体上的setXX和getXX方法，上下文中存在entity, value等参数

ui:filterOp实际对应前端生成的控件的name格式为 `filter_{name}__{filterOp}`，例如`filter_userStatus__in`

## 对象元数据

xmeta文件定义了后台服务对象的元数据，描述了对象具有哪些属性，以及这些属性是否可以修改，是否可以查询等信息。
NopGraphQL引擎返回的对象信息完全由XMeta来定义。如果一个属性在XMeta中没有定义，则即使实体上具有这个字段，前台GraphQL和REST请求也无法访问到该字段。

## 定义关联属性

实体模型中的关联对象生成到XMeta模型中体现为如下配置

```xml
<props>
  <prop name="parent">
    <schema bizObjName="NopAuthDepartment" />
  </prop>

  <prop name="children">
     <schema>
        <item bizObjName="NopAuthDepartment" />
     </schema>
  </prop>
</props>
```

* schema如果具有item，则表示是集合属性，集合元素的类型由 item节点的bizObjName属性来指定。
* 如果是关联对象，则通过schema的bizObjName属性来指定关联类型

## 查询关联属性

通过GraphQL的findPage当方法查询时，可以直接查询关联对象上的字段，但是要求在xmeta中设置允许queryable。这是从安全性角度考虑，避免客户端可以任意查询所有字段导致安全漏洞。

如果未正确定义，控制台可能出现的错误信息：`desc=未定义的查询字段:parent.name`

```xml
<prop name="parent.name" queryable="true">

</prop>
```

这样就可以查询

```json
{
  "query": [
    {
      "$type": "eq",
      "name": "parent.name",
      "value": "aaa"
    }
  ]
}
```

需要注意的是，在parent属性上设置queryable并不会自动使得parent的所有属性都开放查询。必须逐个属性指定。
