# 为什么Nop平台坚持使用XML而不是JSON或者YAML

目前在开发领域似乎形成了一种政治正确：XML是一种过时的技术，不应该再被广泛的使用。对于Nop平台大量使用XML来表达领域模型，有网友调侃道：信息都用xml表达，你这是反（潮流而）动啊。在此前的文章[XML、JSON和函数AST的等价性](https://zhuanlan.zhihu.com/p/554294376)中，我已经对XML和JSON的等价性进行了说明。基于这种等价性，在Nop平台中，XML和JSON是自动支持双向转换的，本质上用哪种表达方式都不影响模型的语义，完全可以用JSON文件来存储模型信息。但是，目前的情况下，XML的表现力相比JSON格式而言存在一些优势，在本文中我将补充一些具体的分析。

## JSON存在的问题

JSON格式的主要问题是缺少注释机制以及多行文本表示形式。这些问题在YAML格式中已经被解决，而YAML格式兼容JSON格式。所以在Nop平台中我们一般倾向于使用YAML格式来替代JSON，例如使用page.yaml来存放前端JSON页面。

```yaml
x:gen-extends: |
  <web:GenPage view="NopAuthUser.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />

body:
  name: crud-grid
  filter:
    id: crud-filter
    actions:
      - id: submit-button
        icon: fa fa-snapchat
```

在XML中嵌入JSON格式是非常简单的一件事情。但是在JSON中嵌入XML就需要进行字符串转义，会大大影响数据的可读性。如果使用YAML格式，嵌入XML就很简单直观了。

JSON乃至YAML格式所存在的另一个问题是它缺少特殊定义的类型属性。JSON对象中所有的属性原则上地位是平等的，在JSON规范中并没有规定可以用于快速区分JSON对象结构的类型属性，这导致它在反序列化的时候难以进行性能优化。

反序列化的时候我们往往需要根据类型属性来决定反序列化得到的对象类型，但是因为JSON格式没有规定type属性一定是第一个属性，可能在解析完整个对象结构之后才能对类型数据做出判断，这导致无谓的临时对象构建过程和额外的内存消耗。

相比较而言，XML总是突出tagName这个特殊属性，可以帮助我们在视觉层面快速识别特定的局部结构。在简单结构的情况下，XML表达形式可能会比JSON格式更简洁、直观。

```
 <row a="1" b="xyz" />

 {
 	"a": 1,
 	"b": "xyz",
 	"type": "row"
 }
```

## XML存在的问题

毫无疑问，XML在实际使用过程中存在一些明显的问题，最本质的原因是XML最早的设计目标是一种面向文本数据的标签语言，对于具有丰富数据类型的应用数据缺乏规范化的表达方式。以IBM、SUN为首的大企业所大力推行的一系列XML相关标准又以严谨为名引入了大量复杂的设计，逐步成就了XML冗长繁琐的刻板印象。

以[XSD(XML Schema Definition)](https://www.w3school.com.cn/schema/schema_intro.asp)语言为例，如果对比JSON Schema语言，我们可以很容易的得到结论：XML Schema的信息密度远比JSON Schema要低。

JSON Schema可以很自然的声明嵌套结构，而XML Schema却强制把一切结构都拆解为type和element两层结构。

```xml
<xs:element name="personinfo">
  <xs:complexType>
    <xs:sequence>
      <xs:element name="firstname" type="xs:string"/>
      <xs:element name="lastname" type="xs:string"/>
      <xs:element name="address" type="xs:string"/>
    </xs:sequence>
  </xs:complexType>
</xs:element>
```

很多人在使用XSD的时候还总是倾向于使用xs:sequence定义，强制为子节点引入顺序依赖，这背离了声明式编程的基本思想，在使用层面往往也没有什么实际的价值，只是带来不必要的麻烦。

> 声明式编程很大程度上就是要淡化顺序依赖这个概念，摆脱命令式编程所强行引入的非因果约束

而在JSON schema中属性之间根本就无法定义顺序关系

```json
{
  "type": "object",
  "properties": {
    "firstname": {
      "type": "string"
    },
    "lastname": {
      "type": "string"
    },
    "address": {
      "type": "string"
    }
  }
}
```

## Nop平台中的XML

Nop平台虽然大量使用XML语法，但是它并没有全盘接收目前的XML规范标准，而是只采纳了其中一个语法和特性子集。例如NopXML去除了外部Entity支持（由此避免了很多安全漏洞），简化了名字空间概念（仅在根节点处理xmlns配置），去除了Instruction概念（仅识别根节点上的Instruction）。 Nop平台在解析和处理XML的时候也没有使用现有的JAXB (Java Architecture for XML Binding) 技术，而是自行编写了XML解析器。解析得到的结果不是标准XML技术中所定义的DOM结构，而是面向应用层重新设计的XNode结构。

```java
class XNode{
	SourceLocation loc;
	String tagName;
	Map<String, ValueWithLocation> attributes;
	List<XNode> children;
	ValueWithLocation content;

	XNode parent;
}

class ValueWithLocation{
	SourceLocation location;
	Object value;
}
```

XNode结构中记录了属性和节点的源码位置，并将attribute和content的值类型修改为Object类型，从而克服了XML原始设计中只针对文本文档的缺陷，使得它可以更高效的表达复杂的业务对象结构。

Nop平台采用XDef元模型语言替代了XML Schema的作用，它利用了前缀引导语法的设计思想，比JSON Schema更紧凑直观。

> 前缀引导语法的设计参见 [DSL分层语法设计及前缀引导语法](https://zhuanlan.zhihu.com/p/548314138)

```xml
<person firstname="!string" lastname="!string" address="string" />
```

XDef元模型定义的结构基本与它所需要约束的数据结构相似，可以看作是将具体的某个模型数据中的值替换为类型声明后得到的结果。

对于XPath和Xslt转换语法，Nop平台同样没有采用，而是设计了替代的XSelector和XTransform语法。

更为重要的是，Nop平台引入了XML格式的模板语言XPL（XLang Template Language）。它是一个图灵完备的模板语言，支持判断、循环、标签抽象、模板元编程等概念，是Nop平台基于XML格式实现可逆计算的关键技术之一。

具体介绍参见 [xpl.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/dev-guide/xlang/xpl.md)

**Nop平台坚持使用XML文件格式最重要的原因就在于JSON乃至YAML格式缺少功能与XPL类似的可用于复杂结构代码生成的模板语言**。

## XPL的特点

1. 具有类似Lisp的同像特性：XPL为XML格式，输出结果也是XML格式
2. 具有多种输出模式，当outputMode=node时会直接输出XNode节点，在输出的过程中会记录属性和节点的来源位置。这在代码生成和断点调试时特别有用，它意味着我们始终可以跟踪当前节点的最初源码位置，调试或者报错时可以直接定位到源码行。
3. 支持编译期运行，本质上类似于Lisp中的宏的作用。
4. 支持自定义标签库，标签库支持Delta定制。也就是说XPL模板语言支持函数级别的差量化定制。

具体示例可以参见视频 [Nop平台中如果通过XBiz配置文件实现后台服务函数](https://www.bilibili.com/video/BV1aN411B7Ju/)
