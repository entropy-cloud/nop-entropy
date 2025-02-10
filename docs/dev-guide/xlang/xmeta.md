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
* filter可以指定对象级别自动设置的过滤条件，比如`<eq name="status" value="1" />`
  可以用于过滤活跃状态的实体。新建或者修改操作时会自动设置filter中的属性值，确保不会突破过滤条件限制。
* orderBy指定对象级别的缺省排序条件
* tree用于补充树形结构相关的字段信息

## prop配置

prop节点支持如下属性配置：

| 名称               | 缺省值           | 说明                                                                                                       |
|------------------|---------------|----------------------------------------------------------------------------------------------------------|
| tagSet           |               | 逗号分隔的扩展标签，在代码生成时会使用                                                                                      |
| published        | true          | 是否发布为GraphQL属性，可以通过服务访问                                                                                  |
| insertable       | true          | 是否允许save操作的参数中包含此属性                                                                                      |
| updatable        | true          | 是否允许通过update操作修改此属性                                                                                      |
| queryable        | false         | 是否允许在查询条件中包含此属性                                                                                          |
| sortable         | false         | 是否允许按照此属性进行排序                                                                                            |
| lazy             | false         | 利用REST协议访问实体对象时是否缺省不返回此属性                                                                                |
| allowFilterOp    |               | 允许针对此字段执行哪些查询运算，例如gt,ge,contains,like等，缺省只允许in,eq                                                        |
| ui:filterOp      | eq            | 生成前台查询表单时缺省按照什么查询运算生成查询条件                                                                                |
| ui:control       |               | 可以直接指定缺省使用哪种控件来展现此属性，在control.xlib中会根据这里的配置查找实际对应的控件                                                     |
| ui:labelProp     |               | 如果指定了labelProp，则生成表单和表格时查看模式下实际显示的是label字段, GraphQL每次请求也会多返回label字段用于前台显示                                |
| ui:maskPattern   |               | 如果指定了掩码模式，则GraphQL返回前台的数据会自动调用StringHelper.maskPattern函数进行掩码操作                                           |
| biz:codeRule     |               | 如果指定了编码规则，则新建实体的时候如果前台没有提交编码值，则会根据编码规则名称查找编码规则配置自动生成一个编码                                                 |
| ui:maxUploadSize |               | 文件上传时最大允许的文件大小，可以是20M这种写法                                                                                |
| ui:editGrid      | sub-grid-edit | 对于子表集合属性，如果tagSet中包含grid标签，则表示在新建和修改时使用嵌入表格来编辑子表，主子表同时提交。通过此参数指定使用子表的哪个表格配置来编辑                           |
| ui:viewGrid      | sub-grid-view | 对于子表集合属性，指定使用子表的哪个表格配置来展现数据                                                                              |
| depends          |               | 如果指定了depends，则GraphQL请求数据的时候会自动加载这些关联属性，view.xml生成ajax调用的时候也会将这些属性返回到前台（如果depends的属性前有~，则只在后台加载，不返回到前台）。 |

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

## 依赖数据加载

```xml

<prop name="myProp" depends="~a.bMappings,otherProp">
  <getter>
    return entity.a.bMappings.size() + entity.otherProp;
  </getter>
</prop>
```

上面的示例中myProp是一个计算属性，它会根据实体上的关联属性动态计算得到，计算表达式在getter段中定义。

假设otherProp是一个lazy属性（prop上标记了lazy=true）, `a.bMappings`是一个关联表的关联集合，如果直接执行
`entity.a.bMappings.size()+entity.otherProp`会导致三次延迟加载调用，
在列表中返回这个属性时会出现大量数据库访问。通过在depends属性中指明用到的关联属性，后台ORM引擎会自动对这些属性进行批量加载，加载完毕后再调用getter计算表达式。

* `~a.bMappings`具有特殊前缀字符`~`，它表示这个关联属性仅用于在后台读取，不会返回到前台。
* `otherProp`是一个普通属性，它会在后台加载，并在`view.xml`翻译ajax调用的时候作为GraphQL selection的一部分。

Nop平台中困扰JPA框架的N+1问题得到了彻底的解决，具体的解决方案就是提供一个独立的数据批量加载通道，不影响直接数据获取。不需要为了优化获取性能写很多特殊的代码。

```javascript
Set<MyEntity> entities = ...;
// 插入一条额外的批量加载调用语句
dao.batchLoadProps(entities, Arrays.asList("prop2", "prop3.prop4"));

for(MyEntity entity: entities){
   // 这时数据已经全部加载到内存中，再访问实体的关联属性就不会触发延迟加载调用，不会访问数据库
   entity.getProp3().getProp4();
}
```

## 对象元数据

xmeta文件定义了后台服务对象的元数据，描述了对象具有哪些属性，以及这些属性是否可以修改，是否可以查询等信息。
NopGraphQL引擎返回的对象信息完全由XMeta来定义。如果一个属性在XMeta中没有定义，则即使实体上具有这个字段，前台GraphQL和REST请求也无法访问到该字段。

## 定义关联属性

实体模型中的关联对象生成到XMeta模型中体现为如下配置

```xml

<props>
  <prop name="parent">
    <schema bizObjName="NopAuthDepartment"/>
  </prop>

  <prop name="children">
    <schema>
      <item bizObjName="NopAuthDepartment"/>
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

## 属性映射mapToProp

meta中配置`<prop name="xyz" mapToProp="abc.xyz">` 则前台看到的属性名就是`xyz`，而不是`abc.xyz`。
mapToProp的含义是后台执行时将GraphQL请求中的字段名翻译为mapToProp对应的属性访问路径去获取数据。
在view模型中使用的也是prop的name，而不是mapToProp。如果前台要直接使用`abc.xyz`，也需要在meta中配置`<prop name="abc.xyz">`，这种设计是出于安全性考虑，不直接允许访问关联对象


## 根据domain自动推定prop配置

`meta-gen.xlib`的DefaultMetaPostExtends标签为所有模型驱动自动生成的meta增加了post-extends处理

```xml

<DefaultMetaPostExtends outputMode="node">
  <attr name="_dsl_root" implicit="true"/>

  <source>
    <thisLib:GenDictLabelFields/>
    <thisLib:GenConnectionFields/>
    <thisLib:GenCodeRuleAutoExpr/>
    <thisLib:GenMaskingExpr/>
    <thisLib:GenFilterOp/>
    <thisLib:GenPropForDomain/>
  </source>
</DefaultMetaPostExtends>
```

* GenDictLabelFields 自动根据dict配置为每个字段值字段生成一个对应的label字段
* GenConnectionFields 自动为具有connection标签的关联属性生成Relay Connection字段
* GenCodeRuleAutoExpr 自动为具有`biz:codeRule`属性的字段增加autoExpr配置
* GenMaskingExpr 自动为具有`ui:maskPattern`属性的字段增加transformOut配置
* GenFilterOp 自动为具有like标签的字段增加`ui:filterOp`配置
* GenPropForDomain 自动根据domain对象的属性生成prop配置

这其中GenPropForDomain会执行`meta-prop.xlib`中的标签进行转换

```xml

<domain-csv-list outputMode="node">
  <attr name="propNode"/>

  <source>
    <prop name="${propNode.getAttr('name')}">
      <schema type="List&lt;String>"/>

      <transformIn>
        return value?.$toCsvListString();
      </transformIn>

      <transformOut>
        return value?.$toCsvList();
      </transformOut>
    </prop>
  </source>
</domain-csv-list>
```

比如以上标签为domain=csv-list的字段增加了schema和transformIn、transformOut配置。

## 常见问题

### 1. 如何根据domain设置自动设置字段不支持查询

可以定制`meta-gen.xlib`的DefaultMetaPostExtends标签，在其中加入自定义的推理逻辑。也可以在`meta-prop.xlib`
中针对指定domain直接生成指定属性

```xml

<domain-my-domain outputMode="node">
  <attr name="propNode"/>
  <source>
    <prop name="${propNode.getAttr('name')}" queryable="false">
    </prop>
  </source>
</domain-my-domain>
```
