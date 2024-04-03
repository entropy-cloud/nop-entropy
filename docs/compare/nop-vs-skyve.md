# 从可逆计算看开源低代码平台Skyve的设计

[Skyve](https://github.com/skyvers/skyve) 是一个Java语言编写的开源的业务软件构建平台。它支持无代码和低代码的快速应用开发。支持不同的数据库引擎：MySQL、SQL 服务器和 H2 数据库引擎。Skyve的设计采用了一种相对比较传统的后端低代码实现方案，也是目前比较流行的低代码和无代码方案。在本文中，我们将把Skyve的设计和Nop平台的设计做个对比分析，从而帮助大家理解Nop平台的独特之处。

## 一. 多租户定制

Skyve是一个多租户系统，它提供了一个有趣的特性：Customer Override，简单的说就是每个租户都可以具有专属于自己的定制配置，从而使得每个租户都可以具有自己独特的功能实现。

Skyve的做法是在/src/main/java/customers/{tenantId}/{modelPath}目录下建立模型文件，从而覆盖/src/main/java/modules/{moduleId}目录下的对应文件。Skyve的这一方案类似于Docker的分层文件系统设计，每个租户相当于是一个定制层，高层的文件覆盖低层的文件。很多低代码平台本质上都采用了类似的定制方案。但是如果和Nop平台基于可逆计算理论实现的Delta定制机制对比，我们可以发现Skyve的方案只是一种非常原始的AdHoc的设计，并没有真正发掘出Delta定制的能力。

1. Skyve的定制是针对每种模型文件都特殊编写的，而不是基于通用的差量文件系统概念。如果要新增一种模型文件，Skyve需要修改FileSystemRepository的实现。
2. 根据文件路径加载模型对象这件事情并没有被抽象为统一的ResourceLoader机制，没有提供模型解析缓存和资源依赖追踪（当依赖的文件发生变化时模型缓存自动失效）。
3. 定制文件整体覆盖原始文件，而不能像Nop平台那样从原始文件继承，在定制文件中只包含差量修订的部分。

在Nop平台中**通过统一的方式来加载所有的模型文件**

```
model = ResourceComponentManager.instance().loadComponentModel(resourcePath)
```

> 参考[custom-model.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/dev-guide/model/custom-model.md)可以配置文件类型所对应的模型加载器

对于/nop/auth/model/NopAuthUser/NopAuthUser.xmeta文件，我们可以增加一个`/_delta/default/nop/auth/model/NopAutUser/NopAuthUser.xmeta`文件，装载时优先查找的是\_delta目录下的文件。缺省启用的是default这个delta层，我们可以通过nop.core.vfs.delta-layer-ids参数来明确指定启用的delta层列表，也就是说，**Delta定制是可以是多层的**，而不是Skyve这种单层的Delta定制。

> 在历史上我们使用过三层定制： platform -- 定制和修正平台内置功能， product --基础产品通用功能，app -- 特定应用定制功能

在定制文件中，我们可以使用x:extends="super"来表示继承上一层的配置，在本文件中仅需要增加差量描述。

```xml
<meta x:extends="super">
    <props>
      <!-- 删除基础模型中的字段 -->
      <prop name="fieldA" x:override="remove" />
      <prop name="fieldB">
         <!-- 为fieldB增加字典表配置 -->
         <schema dict="xxx/yyy" />
      </prop>
    </props>
</meta>
```

除了使用x:extends="super"之外，我们还可以明确指定继承的基础模型，例如

```xml
 <meta x:extends="/nop/app/base.xmeta">
 </meta>
```

**x:extends是非常有效的一种Tree结构分解机制，它也可以应用于JSON文件**，例如对于前端界面JSON，我们可以通过类似方式将一个庞大的页面分解为多个子文件

```json
{
  type: "page",
  body: {
     ...
     {
        type: 'action',
        dialog: {
           "x:extends" : "xxx/formA.page.json",
           "title" : "zzz", // 这里可以覆盖x:extends继承得到的属性
        }
     }
  }
}
```

## 二. 领域特定模型

Skyve的设计目标是将尽可能多的通过元数据而不是代码来定义模型，所以它提供了Document、View等多个XML格式的领域模型，从而使得我们可以使用XML文件描述很大一部分业务逻辑，而无需编写Java代码。

Skyve采用了XSD（XML Schema）语言来规范XML模型文件的格式，然后通过JAXB（Java Architecture for XML Binding)技术来实现XML解析。与此类似，Nop平台采用了元模型定义语言XDefinition来定义模型文件的格式，但是它的设计思想和XSD有着较大的区别：

### 1. 同态设计

XDef明确采用了同态映射的设计思想，XDef元模型的结构与模型自身的结构保持一致，只是在模型语法结构的基础上增加一些标注信息。例如[view.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/xui/xview.xdef)

```xml
<!--
包含表单定义，表格定义，以及页面框架组织
-->
<view bizObjName="string" x:schema="/nop/schema/xdef.xdef" xdef:check-ns="auth"
      xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="xdef">

    <grids xdef:key-attr="id" xdef:body-type="list">
        <grid id="!xml-name" xdef:ref="grid.xdef"/>
    </grids>
   ...
</view>
```

它所描述的模型结构如下所示：

```xml
<view x:schema="/nop/schema/xui/xview.xdef" bizObjName="NopAuthUser"
      xmlns:x="/nop/schema/xdsl.xdef" xmlns:j="j">
    <grids>
        <grid id="list" >
            <cols>
                <!--用户名-->
                <col id="userName" mandatory="true" sortable="true"/>

                <!--昵称-->
                <col id="nickName" mandatory="true" sortable="true"/>
            </cols>
        </grid>
    </grids>
</view>
```

基本上只需要将原始模型文件作为模板，把具体的值替换为对应的stdDomain定义即可。例如id="!xml-name"表示id属性是非空属性，而且它的格式必须满足xml-name定义要求，即必须符合XML名称规范要求。

> 通过StdDomainRegistry.registerDomainHandler(handler)可以注册自定义的stdDomain

XDef元模型是如此之简单直观，以至于OpenAI的ChatGPT已经可以直接理解它的定义，参见[GPT驱动低代码平台生产完整应用的已验证策略](https://zhuanlan.zhihu.com/p/614745000)

### 2. 可执行类型

在XSD或者JSON Schema这种模式定义语言中，只规定了基础数据类型，而没有定义具有执行语义的代码类型。在XDef元模型中，我们可以指定stdDomain=expr/xpl等类型，从而将XML文本自动解析为表达式对象或者Xpl模板对象。

借助于这一机制，我们可以将图灵完备的脚本语言、模板语言嵌入到领域特定语言（DSL）中。而在另一方面，借助于Xpl模板语言中的编译期宏处理的能力，我们可以在模板语言中无缝嵌入任意的领域特定语言，从而**实现通用语言和DSL语言之间的无缝融合**。

Nop平台提供了一个IDEA插件[nop-idea-plugin](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-idea-plugin)。只要提供了XDef元模型定义，这个插件就可以自动实现语法提示、语法校验、链接跳转等功能，特别的，它还**提供了断点调试能力**，可以对DSL代码进行单步调试。也就是说，我们可以很容易开发一个领域特定语言（只需要定义XDef元模型），无需特殊编程即可为这个领域特定语言提供一系列的开发工具支持。具体参见[plugin-dev.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/dev-guide/ide/plugin-dev.md)

### 3. 领域坐标系

在Skyve中，XSD仅仅是作为XML序列化工具所使用的一种辅助信息，没有其他的作用。而在Nop平台中，XDef元模型定义不仅仅是定义了领域模型结构本身，它同时**提供了领域概念在领域模型空间中的定位坐标系**!

在Nop平台的领域模型中，每一个节点都对应一个从根节点开始的唯一路径（也就是它的唯一定位坐标），例如`/view/grids[@id="list"]/cols/col[@id="fieldA"]/label`表示id为list的表格的id为fieldA的列所具有的label属性。

> XPath语法也可以用于在Tree结构中定位，但是一个XPath原则上可能匹配多个节点、属性，因此不是一对一的描述，无法作为定位坐标来使用。

在XDef定义中，对于每一个集合元素，一般我们都会额外配置一个xdef:key-attr属性来表示它的子节点的唯一标识。例如上面的例子中view的grids集合元素所对应的XDef定义为

```xml
    <grids xdef:key-attr="id" xdef:body-type="list">
        <grid id="!xml-name" xdef:ref="grid.xdef"/>
    </grids>
```

这种做法其实和Vue/React这种前端框架的虚拟DOM Diff算法所需要的key属性设置是一样的。

根据xdef:key-attr设置，如果我们希望为已有的表格列增加属性，就可以使用如下方式

```xml
<view x:extends="_NopAuthUser.view.xml">
    <grids>
      <grid id="list">
        <cols>
           <!-- 删除已有的列 -->
           <col id="fieldB" x:override="remove" />
           <col id="fieldA" width="增加新的配置">
           </col>
        </cols>
      </grid>
    </grids>
</view>
```

**一般的类继承机制无法实现覆盖基类中某个列表中某个特定元素的属性！**

基于领域模型的差量计算，有很多涉及到架构抽象的功能都可以由平台统一实现，而不用内置在特定的领域模型内部。例如，NopIoC依赖注入容器采用了类似Spring 1.0的配置语法，它可以利用统一的Delta定制机制来去除系统内置的bean定义，而无需在引擎中内置任何关于bean exclusion的处理代码，所以NopIoC可以在4000行左右的代码量实现超越SpringBoot的动态配置能力。

```xml
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       x:extends="super" x:dump="true">
    <bean id="nopDataSource" x:override="remove" />

    <bean id="nopHikariConfig" x:override="remove" />

    <alias name="dynamicDataSource" alias="nopDataSource" />
</beans>
```

以上例子是Nop平台和基于SpringBoot的若依Ruoyi框架集成时所定制的[dao-defaults.beans.xml](https://gitee.com/canonical-entropy/nop-for-ruoyi/blob/master/ruoyi-admin/src/main/resources/_vfs/_delta/default/nop/dao/beans/dao-defaults.beans.xml)。它删除了Nop平台缺省提供的数据源定义，为Ruoyi框架内置的dynamicDataSource设置了一个别名，从而使得Nop平台可以直接使用该数据源。

### 4. 元编程

Skyve中所有模型都是手工编写的，或者是第一次代码生成时固定生成的。如果我们发现一些经常出现的结构模式，也很难把它们抽象出来。即，**Skyve并没有提供在内置模型的基础上进行进一步二次抽象的机制**。

可逆计算理论指出软件构造可以遵循如下公式：

```
  App = Delta x-extends Generator<DSL>
```

Generator是可逆计算理论中的一个非常关键的部分。Nop平台的领域模型内置了x:gen-extends和x:post-extends这种元编程机制，在模型解析和加载的时候完成动态代码生成。借助于这一机制，大量的通用结构变换可以从运行时引擎中剥离出来，推前到编译期执行，可以极大的简化运行时引擎设计并提高系统整体性能。

以工作流为例，一般实现会签功能时我们都需要在引擎中增加某些特殊的处理逻辑，而在概念层面上，会签步骤实际上是一种冗余设计：它可以被拆解为一个普通步骤+一个隐含的汇聚步骤，因此在NopWorkflow的设计中，支持会签功能仅仅需要在x:post-extends段中增加一个`<wf:CounterSignSupport/>`调用，它负责识别会签步骤，并自动根据会签步骤上的属性设置把它展开成两个步骤节点。

这种元编程机制非常强大，因为它类似于数学定理推导：只需要考虑如何符号变换得到最终需要的结果，完全不用考虑复杂的运行时状态依赖关系。

在NopORM引擎中，JSON对象支持和扩展字段支持也是通过编译期运行技术实现的，ORM引擎本身并没有内置相关知识。具体参见[orm-gen.xlib](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-orm/src/main/resources/_vfs/nop/orm/xlib/orm-gen.xlib)

### 5. 自定义扩展

Skyve中的模型对象属性是固定的，我们只能单方面接受Skyve的设计，无法在不修改Skyve核心代码的情况下为模型对象增加自定义扩展属性。而Nop平台的设计思想是Delta差量无处不在，在任何设计中都需要采用如下配对结构(base, delta)，因此在模型对象的设计中必须为扩展属性预留空间。Nop平台中的一般约定是：除了XDef元模型中定义的属性之外，缺省情况下具有名字空间的属性都是扩展属性。例如

```xml
<prop name="fieldA" ui:show="C">...</prop>
```

在XDef元模型中我们并没有定义ui:show属性，但是因为它具有名字空间，所以在解析时会直接作为扩展属性保存到模型对象上，并不会抛出验证失败异常。

> (base, delta)配对设计体现在Nop平台的方方面面，比如Nop平台中所有传递的消息结构都是 (data, headers)配对。实际上很多情况下，meta data都可以看作是对data的某种delta补充信息，而data和meta data在不同的使用场景下是可以互相转化的。如果当前的处理逻辑不需要涉及到某些信息，它们可以作为meta data来保存、传递，而在下一个阶段需要被处理时，原有的部分data就可以转换为meta data，而原先的部分meta data会转换为data来处理。所谓meta data是描述数据的数据这一说法并不完全准确，在实践中，meta data完全可以包含与当前应用无关但是也无害的附加信息（**无用且无害**）。

### 6. 领域语言工作台

Skyve的做法是一种比较传统的做法，它针对每个模型单独实现具体功能。而Nop平台的做法是试图提供一个领域语言工作台(Language Workbench)，为开发领域特定语言提供一系列的技术支撑，从而使得我们可以根据领域需求快速的开发一个对应的领域特定语言。参见[XDSL：通用的领域特定语言设计](https://zhuanlan.zhihu.com/p/612512300)。领域语言工作台可以看作是一种面向语言编程(Language Oriented Programming)范式。IDEA的开发商JetBrains公司曾经发布了一个产品[MPS](https://www.jetbrains.com/mps/)专门用于实现LOP。Nop平台的设计目标和MPS大致上一致的，只是它是基于系统化的可逆计算理论，在基本的软件构造原理和技术路线方面与MPS有着本质性差异。

在Nop平台中，所有的领域模型都是采用统一的元模型机制进行定义的，它们都符合基础的XDSL语法规范（XDSL语法规范由元模型[xdsl.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/xdsl.xdef)定义）。借助于XDSL所提供的通用能力，我们自己定义的DSL可以自动获得差量合并、元编程、断点调试、可视化设计等能力。**例如对于工作流引擎，我们只需要编写最内核的流程运行时，无需额外工作即可得到可视化流程设计器、流程断点调试、差量定制、继承已有流程模板等能力**。

基于XDSL，我们还很自然的实现了多个DSL之间的无缝嵌入。比如在流程引擎中嵌入规则引擎，在规则引擎的动作中触发流程步骤等。

## 三. 具体模型对比

除了以上通用机制上的差异，在具体的领域模型实现上面，Nop平台相比于Skyve也要更加精细化，而且抽象程度更高，更易于扩展。

### 1. 数据模型

Skyve中的Document模型描述了对象属性结构以及对象之间的关联关系。它们既负责描述前后端之间的接口结构，又负责描述数据存储层的持久化数据结构。而在Nop平台中我们是通过XMeta模型和ORM两个模型来完成类似的功能。

Skyve的底层基于Hibernate框架技术，因此在获得Hibernate强大能力的情况下，也继承了Hibernate的相关缺点。NopOrm引擎是基于可逆计算原理从零开始设计并实现的新一代的ORM引擎，它经过理论分析，将对象查询语法EQL定义为SQL语法的最小面向对象扩展： EQL = SQL + AutoJoin，在理论层面克服了Hibernate的一些固有缺陷，同时最大限度的保留了SQL的原生能力。具体设计可以参见[低代码平台需要什么样的ORM引擎(1)](https://zhuanlan.zhihu.com/p/543252423)。

Skyve没有区分接口层的结构模型和存储层的结构模型，实际在面向复杂业务场景很难隔离不同层面的需求影响，也难以适应长期的结构演化。在存储层我们希望数据结构减少冗余性，而在接口层我们可能需要针对同一份数据返回多种衍生数据。

Nop平台**基于数据模型可以自动生成GraphQL服务**，它内置了业务常见的一系列功能：

* 复合主键支持
* 字段自动加解密支持
* 卡号等字段生成掩码
* 根据字典表配置自动为字段生成对应的Label字段（在XMeta配置中采用元编程机制生成）
* 批量加载优化（解决Hibernate常见的N+1问题）
* 逻辑删除
* 乐观锁
* 自动记录修改人和修改时间
* 自动记录实体修改前和修改后的字段值
* 内置MakerChecker审批机制，开启后修改操作需要经过审批才会提交
* 主子表一次性提交
* 递归删除子表数据
* 扩展字段支持
* 分库分表
* 分布式事务

具体设计可以参见[低代码平台需要什么样的ORM引擎(2)](https://zhuanlan.zhihu.com/p/545063021)

### 2. 后台服务扩展

Skyve通过Bizlet来实现后台逻辑扩展。

```java
class Bizlet{
      public void preSave(T bean) throws Exception {
    }

    public void preDelete(T bean) throws Exception {
    }

    public void postRender(T bean, WebContext webContext) {
    }
}
```

这一设计明显是和增删改查逻辑绑定在了一起。而且它的设计还不完整，我们无法通过一种简单的方式拦截查询操作，在查询前和查询后增加附加行为。查询直接调用存储层接口执行，并不经过Bizlet处理。

Nop平台的NopGraphQL引擎分解到对象层面对应于BizModel模型，它是一个通用的服务模型，并不限于CRUD服务的实现。CrudBizModel仅仅是一个提供缺省动作定义的基类。借助于XMeta中包含的元数据信息，CrudBizModel可以自动实现非常复杂的参数校验以及主子表结构保存、复制等功能。NopGraphQL引擎内置了非常灵活的数据权限过滤功能，可以通过简单的描述配置精确控制复杂对象图上的数据访问权限。具体参见视频[Nop平台如何配置列表过滤条件以及如何增加数据权限](https://www.bilibili.com/video/BV1Ac411H7my/)

另外一个需要关注的设计要点是Nop平台强调了**业务逻辑表达的框架无关性**。传统的服务实现都是依赖于某个具体框架的，比如Skyve的后台服务会用到WebContext对象，它直接包含了HttpServletRequest对象和HttpServletResponse对象，这导致它必然和Web运行环境绑定在一起，我们编写的业务代码难以迁移到非Web环境中使用。而在Nop平台中，GraphQL引擎的入口参数和返回对象都是POJO对象，没有任何特定运行时环境依赖。NopGraphQL可以看作是一个纯逻辑的运行引擎，它的输入可以来源于各种渠道，例如可以从批处理文件中读取请求对象，自动将在线服务转化为批处理服务（基于NopOrm引擎会自动实现批量提交优化）。此外，还可以直接对接Kafka消息队列，将GraphQL服务直接转化为消息处理服务（返回消息可以发送到一个Reply Topic上）。

![](../arch/BizModel.svg)

基于POJO的设计也极大的降低了单元测试的难度，无需和服务器整合即可对单个服务函数进行测试。

具体NopGraphQL的设计可以参见[低代码平台中的GraphQL引擎](https://zhuanlan.zhihu.com/p/589565334)

### 3. 显示模型

Skyve通过View模型来描述界面的主体结构，这个View模型可以看作是只具有少数固定组件的前端框架。

```xml
<view xmlns="http://www.skyve.org/xml/view"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    name="_residentInfo" title="Resident Info"
    xsi:schemaLocation="http://www.skyve.org/xml/view ../../../../schemas/view.xsd">
    <form border="true" borderTitle="Resident Info">
        <column percentageWidth="30" responsiveWidth="4" />
        <column />
        <row>
            <item>
                <default binding="parent.residentName" />
            </item>
        </row>
    </form>
    <form border="true" borderTitle="Resident Photo">
        <column percentageWidth="30" responsiveWidth="4" />
        <column />
        <row>
            <item showLabel="false">
                <contentImage binding="parent.photo" />
            </item>
        </row>
    </form>
</view>
```

Nop平台中的XView模型定位与Skyve的View模型类似，但是它采用更加面向业务的抽象方式，将表单、表格、布局、动作、页面等概念抽象出来，特别是可以通过NopLayout布局语言实现表单布局信息和表单具体控件内容信息的隔离。例如

```xml
<view>
    <forms>
        <form id="edit" size="lg">
            <layout>
                ========== intro[商品介绍] ================
                goodsSn[商品编号] name[商品名称]
                counterPrice[市场价格]
                isNew[是否新品首发] isHot[是否人气推荐]
                isOnSale[是否上架]
                picUrl[商品页面商品图片]
                gallery[商品宣传图片列表，采用JSON数组格式]
                unit[商品单位，例如件、盒]
                keywords[商品关键字，采用逗号间隔]
                categoryId[商品所属类目ID] brandId[Brandid]
                brief[商品简介]
                detail[商品详细介绍，是富文本格式]

                =========specs[商品规格]=======
                !specifications

                =========goodsProducts[商品库存]=======
                !products

                =========attrs[商品参数]========
                !attributes

            </layout>
            <cells>
                <cell id="specifications">
                    <gen-control>
                        <input-table addable="@:true" editable="@:true"
                                     removable="@:true" needConfirm="@:false">
                            <columns j:list="true">
                                <input-text name="specification" label="规格名" required="true"/>
                                <input-text name="value" label="规格值" required="true">
                                </input-text>
                                <input-text name="picUrl" label="图片" required="true"/>
                            </columns>
                        </input-table>
                    </gen-control>
                    <selection>id,specification,value,picUrl</selection>
                </cell>
              </cells>
      </form>
  </forms>
</view>
```

NopLayout布局语言可以用非常紧凑的方式来表达复杂的界面布局规则。而单个字段的展示控件会根据数据模型中定义的数据类型和数据域信息自动推定，无需表达。如果自动推定的控件无法满足要求，我们可以使用cell的gen-control配置来单独为该字段指定展示控件。

> 有趣的是，NopLayout这种布局语法也是ChatGPT可以很容易理解并模仿使用的。参见[如何克服GPT的输入token限制，产生复杂的DSL](https://zhuanlan.zhihu.com/p/615685144)

具体NopLayout语法的规则可以参见[低代码平台中的表单布局语言:NopLayout](https://zhuanlan.zhihu.com/p/592131885)

Skyve的View模型设计还存在一个问题：如果缺省的界面模型无法满足要求怎么办？Skyve目前的回答是无能为力，如果超出模型原始设计，则我们只能放弃整个页面，使用其他技术从零开始编写。而在Nop平台中，利用差量合并机制，我们可以实现部分继承，然后补充部分差量描述信息。

Nop平台的前端使用百度AMIS框架，这是一个非常优秀、强大的前端低代码框架。关于它的介绍可以参见[为什么说百度AMIS框架是一个优秀的设计](https://zhuanlan.zhihu.com/p/599773955)。我们前端使用的页面描述是编译期根据XView模型生成的JSON描述，在自动生成JSON的基础上，我们可以进行少量差量定制，因此只要在AMIS能力范围之内的页面，都可以通过部分继承来复用XView模型的能力，而不用从头开始编写。

```yaml
# main.page.yaml页面文件缺省根据XView模型生成

x:gen-extends: |
  <web:GenPage view="NopAuthUser.view.xml" page="main"
        xpl:lib="/nop/web/xlib/web.xlib" />
```

一个有趣的问题是，如果AMIS的能力也不足以描述前端页面结构怎么办？首先，可以通过自定义组件来补足AMIS的能力，因为所有的前端控件结构最终都可以被表达为某种抽象语法树(AST)，进而可以被序列化为某种JSON结构，所以AMIS的JSON形式原则上是完备的，不存在无法描述的情况（最极端的情况是整个页面用一个自定义组件来显示，它读取body配置再把它解释为特定的界面控件内容）。
另外，我们也可以利用代码生成和元编程机制重新为XView模型提供一套解释器，将XView模型翻译为Vue源码或者React源码等。

### 4. 代码生成

Skyve提供了一个Maven插件，可以根据XML模型配置自动生成实体类代码等。Skyve的代码生成器实现得比较简单，就是在Java代码中通过文本拼接的方式来输出。Nop平台的代码生成器XCodeGenerator是一个更加系统化的解决方案。

首先XCodeGenerator支持增量式代码生成，生成的代码和手工修改的增强代码隔离存放，互不影响，可以随时根据模型重新生成而不会覆盖手工修改的部分。

第二，XCodeGenerator是一个数据驱动的代码生成器，通过模板目录结构即可控制代码生成过程中的判断和循环。例如 /{!enabled}{entityModel.name}.java表示仅当enabled属性设置为true时，才会为每个实体生成对应的java文件。

第三，XCodeGenerator和Nop平台的其他模型一样支持Delta定制。即我们可以在不修改平台内置生成模板的情况下，通过在delta目录下增加对应文件来覆盖内置的模板文件。

第四，XCodeGenerator支持针对自定义模型生成，并可以在Nop平台之外独立使用。例如，除了内置的数据模型、API模型之外，我们可以定义一个针对自己业务领域的Excel格式的领域模型，然后只要补充一个imp.xml描述文件，即可自动将Excel文件解析为领域模型对象，然后应用自定义代码生成模板即可生成目标文件。

关于XCodeGenerator的详细介绍可以参见[数据驱动的差量化代码生成器](https://zhuanlan.zhihu.com/p/540022264)

### 5. 报表工具

Skyve使用JasperReport来生成报表，这对于复杂的中国式报表需求来说肯定是不足够的。Nop平台提供了一个采用Excel为可视化设计器的中国式报表引擎NopReport，它以3000行左右的代码量实现了中国式报表理论所特有的层次坐标展开机制。具体介绍可以参见[采用Excel作为设计器的开源中国式报表引擎：NopReport](https://zhuanlan.zhihu.com/p/620250740)。[演示视频](https://www.bilibili.com/video/BV1Sa4y1K7tD/)

对于Word模板导出，Nop平台也提供了一个采用Word为可视化设计器的Word模板引擎。它利用了Nop平台内置的XPL模板语言，仅通过800行左右的代码量就将Word文件转换为支持动态生成的模板文件。具体介绍可以参见[如何用800行代码实现类似poi-tl的可视化Word模板](https://zhuanlan.zhihu.com/p/537439335)

### 6. 自动化测试

Skyve提供了一个有趣的自动化测试支持，它可以根据数据模型和View模型自动生成对应的WebDriver自动化测试用例。

```xml
<automation uxui="external" userAgentType="tablet" testStrategy="Assert"
    xsi:schemaLocation="http://www.skyve.org/xml/sail ../../../skyve/schemas/sail.xsd"
    xmlns="http://www.skyve.org/xml/sail"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">

    <interaction name="Menu Document Numbers">
        <method>
            <navigateList document="DocumentNumber" module="admin"/>
            <listGridNew document="DocumentNumber" module="admin"/>
            <testDataEnter/>
            <save/>
            <testDataEnter/>
            <save/>
            <delete/>
        </method>
    </interaction>
```

Skyve具有模型知识，因此它生成的测试用例可以自动利用模型中已经表达的信息，例如testDataEnter表示为表单中存在的每个字段随机生成对应的测试值。

Nop平台对于模型信息的使用要更加深入，除了在输入端应用模型信息之外，我们可以充分利用模型驱动的优势，捕获系统中所有的副作用，并把它们都录制下来，从而将依赖于复杂状态的测试用例转化为无副作用的纯逻辑测试用例。具体参见[低代码平台中的自动化测试](https://zhuanlan.zhihu.com/p/569315603)

## 四. 理论的价值

Nop平台与所有其他低代码平台的本质性区别在于，它是基于一个新的软件构造理论--可逆计算理论，首先建立一个最小的概念集，然后采用严密的逻辑推导逐步构建庞大的技术体系。可逆计算理论在理论层面克服了组件理论的局限性，为系统级的、粗粒度的软件复用扫除了理论障碍，而Nop平台作为可逆计算理论的参考实现，它提供了统一的技术工具来解决众多领域建模过程中出现的共性问题。

Nop平台解决问题的方式与其他平台有着显著区别。以Excel模型解析为例，一般的做法实际上是针对某个特定的业务需求规定一个模型格式，然后编写一个对应的Excel解析函数。针对不同的模型文件，我们会编写多个不同的解析函数。而在Nop平台中，我们是规定了一种将Excel结构映射为领域对象结构的规则，然后编制一个统一的Generic的解析器。如果采用范畴论的术语，可以说Nop平台中的Excel模型解析器是从Excel范畴(包含无穷多个不同的Excel文件格式)到领域对象范畴(包含无穷多个不同的领域对象结构)之间的映射函子（Functor）。如果我们再定义一个逆向的从DSL模型对象范畴到Excel范畴的报表导出函子，则它们实际上可以构成一对伴随函子（Adjoint Functor）。

所谓的函子(Functor)是定义了Domain A中的每一个对象到另一个Domain B中的对象的一种”保结构“的映射。
范畴论解决问题的主要方式就是通过函子概念。具体来说，如果要解决某个问题，我们先把它扩大化为一个函子映射问题，一次性解决一个包含所有相关问题的问题集，从而间接的实现解决某个特定问题的目的。这种把问题扩大化的解决方案无疑是疯狂的，它如果能成功，那唯一的可能只能是它所应用的领域存在数学层面可以明确定义的、稳定可靠的科学规律，it is science。

有些人可能对于可逆理论不感兴趣，觉得理论仅仅是学术界发文章时的一种说辞，与软件工程的实践是脱节的。但是统计学习之父 Vapnik有一句名言： nothing is more practical than a good theory。可逆计算理论相当于扩大了我们解决问题时的解空间，揭示了众多前所未见的技术可能性。基于可逆计算理论的指导，Nop平台以非常小的技术成本（目前代码量在十几万行）捕获了软件结构空间中的统一的构造规律，定义了一条切实可行的通向智能低代码开发的技术路线，可以清晰的看到我们要向何处发展，目前我们处在什么位置。未来几年，我们一定会看到差量、Delta、可逆、生成式这样的术语频繁的在各个技术领域中出现，它们的综合性应用必然会导向可逆计算理论。

> 一个有趣的事情是深度学习理论的计算模式对应于 `Y = Sigma( W*X + B) + Delta`。考虑到残差连接之后，深度学习的公式与可逆计算理论的构造公式如出一辙。本身可逆计算解决问题时也必然涉及到多个模型深度嵌套的问题，恰如深度学习中的多层神经网络。

建立Nop平台交流群之后，在交流中我经常得到的反馈是：啊，原来还可以这样啊。这很正常，一个人无法理解自己尚未理解的事物，实际操作一下Nop平台的开发示例，可能会帮助我们更好的理解可逆计算理论。只有当为了特定业务需求需要去定制平台（或自己编写的基础产品）中已有的功能、机制的时候，才可能体会到Nop平台与其他所有公开技术之间的巨大差异。

Nop平台的开源地址：

- gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- 开发示例：[docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [可逆计算原理和Nop平台介绍及答疑\_哔哩哔哩\_bilibili](https://www.bilibili.com/video/BV1u84y1w7kX/)

建议Skyvue将模型定制的逻辑进行标准化，这样在增加新的模型类型的时候不需要FileSystemRepository
