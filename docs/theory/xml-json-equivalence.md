# XML、JSON和函数AST的等价性

Nop平台的整体技术战略可以说是围绕着Tree结构来制定的，如何表达Tree结构是Nop平台所需要解决的第一个战术问题。在Java后端架构中，XML曾经是表达Tree结构的首选方式，Spring、Hibernate、SOA、BPMN等都是XML的经典应用案例，但是长期以来，一直也存在一种声音，批评XML繁琐冗长、性能低下，这是否真的是XML先天存在的缺陷？随着Web前端的兴起，便于在javascript中解析和操作的JSON格式逐渐取代了XML的地位，成为表达复杂数据对象的事实标准，这是否意味着XML已经完全过时？低代码的兴起带来了一个新的需求，我们如何对可执行的函数逻辑进行结构化表达？如何以自然、直观的方式实现对AST语法树的转换和处理？本文将简单介绍Nop Platform2.0中对Tree结构的基本抽象，以及XML、JSON和AST之间的等价转换方案。

Nop平台认为，**Everything is Tree**，Tree可以通过XML和JSON等多种序列化形式进行展现，XML和JSON之间可以实现双向转换。如果为Tree的每个节点指定一个解释器，则它自动成为AST抽象语法树，并获得可执行的语义。

## 一. XML存在的问题

XML解析得到的DOM结构确实存在着很严重的设计问题，这**本质上是因为它的设计初衷是针对文本文档而不是通用的应用数据结构**。以下面的文本为例

```xml
<book>
   <title>aaa</title>
   <!--销售价格-->
   <price>30</price>
</book>
```

为了保证文本文档读取之后，能够完全保真的重新序列化得到原始文档，DOM会插入额外的文本节点，导致DOM结构与应用语义不匹配。

```javascript
Element("book")
   Text(Blank)
   Element("title")
      Text("aaa")
   Text(Blank)
   Comment("销售价格")
   Text(Blank)
   Element("price")
      Text("30")
   Text(Blank)
```

在DOM结构中，book节点的子节点不是两个，而是7个，因为额外需要4个仅包含空格与换行符的文本节点用于保持原始文件中的格式信息，一个注释节点用于保存注释信息。同时price子节点的内容不是数字30，而是一个文本节点Text，Text节点再具有String类型的内容"30"。

显然，使用DOM结构来组织应用数据会引入大量的冗余因素，同时丧失了最基本的原子数据类型区分，并不是一种普遍适用的选择。当年普元EOS鼓吹所谓的XML数据总线技术，把所有业务数据都取到DOM节点中，然后再通过DOM API来存取，据说可以简化编程、统一处理机制。对此，我的第一反应就是，这是脑子有坑吗？还是说创始人是从国外回来的，被XML原教旨主义给洗脑了？

XML格式除了受到DOM结构的拖累之外，原教旨主义的一些设计范例也加深了人们对于XML的误解。例如明明可以采用如下表达方式

```xml
<MyFunction a="xxx" b=”3“ />
```

原教旨主义的设计却是

```xml
<function>
    <name>MyFunction</name>
    <args>
        <arg>
            <name>a</name>
            <value>xxx</value>
        </arg>
        <arg>
            <name>b</name>
            <value>3</value>
        </arg>
    </args>
</function>
```

XML最早的设计意图是采用一种极简设计，而节点属性在某种意义上是冗余的特性，所以原教旨主义者倾向于不使用属性机制。甚至有人认为应该取消标签自闭合的语法，例如`<node/>`，因为`<node></node>`已经可以表示标签封闭了。

同时，因为XML是从标记语言演变而来，它的原始应用场景下标记是在业务数据上的附加部分，也就是说删除所有标签和属性之后，剩下的文本信息就是所有业务数据，所以也有人一直坚持业务数据必须通过文本节点来表达，属性仅仅用于id这种非用户可见的标识。

类似的做法一度非常盛行，比如maven工程所使用的pom.xml文件格式，SOAP协议所使用的XML序列化格式等。

XML格式虽然存在一定的问题，但是这些问题并非不可克服。在DOM结构之上，可以提供一些简化的API来屏蔽DOM结构的复杂性。例如dom4j库提供了elements/elementText等API，可以直接获取到非空白节点。更进一步，其实我们也可以抛弃DOM结构，从XML直接解析得到某种通用的Tree结构即可。

对于XML只支持文本属性的问题，最简单的解决方案是对XML语法增加一点扩展，对于数字和布尔属性，去除属性值两侧的引号，例如

```xml
<Window enabled=true size=3 />
```

HTML解析引擎其实可以解析上述的标签结构，只是它解析到的结果总是被强制规定为文本值而已。

React引入的JSX语法可以看作是另一种XML的变种，它通过 `{}`语法将属性值类型扩展为表达式类型，例如

```xml
<Control size={3} enabled={true}>
</Control>
```

如果希望仍然严格符合XML格式，则可以引入模板语法，例如

```xml
<Control size="${3}" enabled="${true}">
</Control>
```

或者使用我在上一篇文章[DSL分层语法设计及前缀引导语法](https://zhuanlan.zhihu.com/p/548314138)中介绍的前缀语法

```xml
<Control size="@:3" enabled="@:true">
</Control>
```

## 二. XNode: 通用的Tree结构

Nop平台中通过XNode来表达通用的Tree结构

```java
class XNode implements ITreeStructure{
    String comment;
    String tagName;
    Map<String, ValueWithLocation> attributes;
    List<XNode> children;
    ValueWithLocation content;
}
class ValueWithLocation{
    SourceLocation location;
    Object value;
}
```

1. XNode取消了XML中命名空间的支持，这个特性在业务中很少用到，但是对性能有明显的影响。

2. 对于Comment，简化为只支持在节点上方增加注释。

3. 从XML中解析得到XNode时，直接忽略所有空白文本节点。

4. 如果不存在子节点，则通过content存放节点内容。

5. 属性和节点值都为Object类型，并且保存了对应的源码位置(SourceLocation)。

与一般的模板语言不同，Nop平台中的Xpl模板语言具有为代码生成专门设计的语法特性。

1. Xpl模板具有多种输出模式，如果选择了outputMode=node，则它会输出XNode节点，而不是文本内容，ValueWithLocation中会保存输出值所对应的源码位置。这种机制相比于Javascript中的SourceMap机制要简单并且稳定的多。同时XNode.dump()调用会在注释中打印属性和节点的源码位置，便于调试时诊断问题。

2. XNode的属性值不允许为null，如果将XNode的属性值设置为null则等价于删除该属性。利用这一特性可以简化可选属性的生成。

```xml
<prop name="a" mandatory="${model.mandatory ? true: null}" />
对于mandatory=false的情况，实际会生成
<prop name="a" />
```

## 三. XML和JSON之间的双向转换

在Nop平台中，XML和JSON之间的转化路线如下图所示

<img title="" src="xml-to-json.png" alt="" data-align="center" width="360">

XDefinition元模型（类似于XML Schema Definition，XSD）定义了XNode的结构（Tree），而XMeta元模型（类似于JSON Schema）定义了DataBean对象结构（Class）。XDefinition和XMeta之间按照一定的规则实现双向转换，例如XNode节点映射为对象，节点属性映射为对象属性，XML名称通过camelCase变换得到对象属性名等。

```java
objMeta = new XDefToObjMeta().transform(xdef);
bean = new DslXNodeToJsonTransformer(forEditor,xdef).transformToObject(node);
node = new DslModelToXNodeTransformer(objMeta).transformToXNode(bean);
```

从XNode转化到DataBean的过程有一个微妙的地方，即转换的结果并不一定是唯一的，我们可以针对不同的使用目的把XNode转换成不同结构的DataBean。如果把DataBean看作是复杂的领域模型结构的一种对象形式的表象(Representation)，则我们至少具有两种使用目的，一是在可视化编辑器中编辑领域模型，二是在解释器中执行领域模型。如果领域模型中包含函数定义，则编辑的时候只需要使用文本形式的代码定义，而在执行的时候，我们需要编译函数定义得到可执行的`IEvalAction`对象等。在Nop平台中，我们通过forEditor参数来区分这两种使用场景，从而确定在转换XNode的时候是否需要对函数代码进行编译。

目前常见的工作流引擎或者规则引擎等并没有明确的多重表象的概念，在实现编辑器和运行时引擎时重复性代码比较多。

在Nop平台中，当没有定义XDefinition元模型的时候，也定义了XML和JSON之间一种简易的双向转换机制。例如

```xml
<root a="1">
   <child name="c1">child value</child>
</root>
```

被转换为

```json
{
    "$tag": "root",
    "a": "1",
    "$body": [
       { "$tag": "child", "name":"c1", "$body": "child value"}
    ]
}
```

通过`$tag`属性来表示XML标签名，通过`$body`属性来表示XML节点的内容。

[百度AMIS框架](https://aisuda.bce.baidu.com/amis/zh-CN/components/form/index)中所使用的JSON格式其实与此很接近，它是使用type属性来表示标签名，使用body属性来表示节点内容。

```json
{
  "type": "page",
  "body": {
    "type": "form",
    "api": "/amis/api/mock2/form/saveForm",
    "body": [
      {
        "type": "input-text",
        "name": "name",
        "label": "姓名："
      },
      {
        "name": "email",
        "type": "input-email",
        "label": "邮箱："
      }
    ]
  }
}
```

不过，amis格式的一致性并不好，并不是所有组件的格式都符合这一规则。早期的时候，容器组件的body命名很混乱，controls/content/body等多种名称并存，后来经过统一清理，才算好了一些，但目前仍然无法自动转换为XML格式，必须增加额外的描述信息才可以。

JSON格式在使用中的一个不便之处是缺少注释机制。为此，Nop平台在底层实际也支持JSON5和YAML格式，只是把它们都看作是JSON的一种扩展表现形式，内部处理统一按照JSON对象进行。

## 四. 从XNode到AST

函数调用可以被看作是一种通用的Tree结构定义，一切结构都可以用`(名称,参数，参数,...)`形式去表达，这一洞见是Lisp语言的立身之本。

如果我们仔细观察一下函数调用的形式

```javascript
myFunc(1,"xx")
```

它等价于使用命名参数的形式

```javascript
myFunc({a:1,b:"xx"})
```

> 按位置传递参数是历史遗留的传统，因为在机器语言层面，参数是通过堆栈传递的。但是在现代的软件框架设计中，已经全面转向了按名称传递参数，比如GraphQL中loader函数的参数都是具名的。Java语言无法通过反射获取函数参数名被认为是一种需要被修正的缺陷。在前端vuex框架中，函数参数payload被强制约束为唯一的Map类型的对象。

如果再把函数名移动到括号内部，则可以得到JSON格式的一种函数调用形式

```json
{
    "$fn":"myFunc",
    "a": 1,
    "b": "xx"
}
```

如果把函数名看作是组件名称，而把函数参数看作是组件的参数，那么函数调用实际上可以使用可视化组件设计器来实现编辑。目前低代码领域常见的可视化设计器都是针对表单模型来实现的，虽然有很多人已经在尝试实现可视化设计器与具体组件库的解耦，但是我们其实可以更进一步，把布局逻辑也剥离出来，而**一个没有内置布局逻辑的设计器是可以用来配置函数组合逻辑的**。

在Nop平台中，将XNode转化为可执行的逻辑的主要方案是Xpl模板语言中的自定义标签库机制。例如，在工作流的动作定义中

```xml
<action id="approve">
 <source>
   <c:lib from="/test/oa.xlib" />
   <oa:SendMail receiver="a@b.com">
      <content>data</content>
   </oa:SendMail>
 </source>
</action>
```

每一个节点可以对应到一个自定义标签，由标签库负责将节点内容翻译为可执行的代码。如果把XNode看作是抽象语法树，则自定义标签相当于是编译原理中的语法制导翻译。这一翻译过程是上下文无关的，即无论标签嵌套结构是什么样的，只要找到对应的标签名就翻译到同样的可执行逻辑。

> 百度AMIS框架早期的实现就背离了这一点，从json到组件的翻译鼻并不是根据type名称来唯一确定，而是需要考虑json节点的全路径。

在常见的工作流引擎中，为了简化用户配置，往往需要在引擎中内置大量业务相关的特殊实现，设计器也要需要进行相应定制改造。而在Nop平台中，借助于元编程和XTransform机制，我们可以把大量扩展完全剥离到引擎之外实现。例如

```xml
配置文件中的
<email-action>
   <content>data</content>
</email-action>
可以通过如下转换规则转换为引擎内置支持的action节点
<xt:mapping>
   <match tag="email-action">
     <action xt:node=".">
       <source>
          <c:lib from="/test/oa.xlib" />
          <oa:SendMail receiver="a@b.com">
             <xt:copy-node xpath="content" />
          </oa:SendMail>
       </source>
     </action>
   </match>
</xt:mapping>
```

底层引擎无需内置大量可执行动作，通过AST转换我们可以在XNode层面进行大量结构变换，最后再通过XPL模板语言的自定义标签机制来获得可执行的语义。
