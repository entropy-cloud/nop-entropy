# Why the Nop Platform Sticks with XML Instead of JSON or YAML

In today’s development world, a kind of political correctness seems to have formed: XML is an outdated technology and should no longer be widely used. Because the Nop platform uses XML extensively to express domain models, some people joke: if you express everything in XML, you’re being counter-(trend)revolutionary. In a previous article, Equivalence Among XML, JSON, and Function ASTs (https://zhuanlan.zhihu.com/p/554294376), I have already explained the equivalence between XML and JSON. Based on this equivalence, XML and JSON are automatically supported for bidirectional conversion in the Nop platform. Essentially, using either form does not affect the semantics of the model; you can absolutely store model information in JSON files. However, under current circumstances, XML has some advantages over JSON in terms of expressiveness. In this article I will add some concrete analysis.

## Problems with JSON

The main issues with JSON are the lack of a commenting mechanism and a way to represent multi-line text. These problems have been addressed in YAML, which is compatible with JSON. Therefore, in the Nop platform, we generally prefer YAML as a replacement for JSON, for example using page.yaml to store front-end JSON pages.

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

Embedding JSON in XML is very straightforward. Embedding XML in JSON, however, requires string escaping, which greatly harms readability. If you use YAML, embedding XML becomes simple and intuitive.

Another issue with JSON—and even YAML—is the lack of a specially defined type attribute. In JSON objects, all properties are, in principle, equal; the JSON specification does not define a type attribute for quickly distinguishing object structures, which makes performance optimization during deserialization difficult.

During deserialization, we often need a type attribute to decide the target object type. But since JSON does not specify that a type property must appear first, you may only be able to determine the type after parsing the entire object structure, leading to unnecessary temporary object construction and extra memory consumption.

By contrast, XML always highlights the element tag name (tagName) as a special discriminator, helping us quickly recognize particular local structures visually. For simple structures, XML can be more concise and intuitive than JSON.

```
 <row a="1" b="xyz" />

 {
 	"a": 1,
 	"b": "xyz",
 	"type": "row"
 }
```

## Problems with XML

There is no doubt that XML has some obvious problems in practice. Fundamentally, XML was originally designed as a tag language for text data and lacks normalized ways to express rich application data types. A series of XML-related standards aggressively promoted by big companies like IBM and Sun introduced a lot of complex designs in the name of rigor, gradually cementing XML’s stereotype of being verbose and cumbersome.

Take XSD (XML Schema Definition) (https://www.w3school.com.cn/schema/schema_intro.asp) as an example. Compared with JSON Schema, it is easy to conclude that XML Schema has much lower information density.

JSON Schema can naturally declare nested structures, whereas XML Schema forces everything to be split into two layers: type and element.

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

Many people also tend to use xs:sequence in XSD, which forces order dependencies among child nodes. This runs counter to the basic idea of declarative programming and usually has little practical value in use—only introducing unnecessary trouble.

> Declarative programming is largely about de-emphasizing order dependency, freeing ourselves from the non-causal constraints forcibly introduced by imperative programming.

In JSON Schema, you cannot define ordering relationships among properties at all.

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

## XML in the Nop Platform

Although the Nop platform heavily uses XML syntax, it does not fully adopt the existing XML standards. Instead, it only adopts a subset of the syntax and features. For example, NopXML removes external entity support (thereby avoiding many security vulnerabilities), simplifies the namespace concept (handling xmlns only on the root node), and removes the Instruction concept (only recognizing Instructions on the root element). The Nop platform also does not use existing JAXB (Java Architecture for XML Binding) technology for parsing and processing XML; it implements its own XML parser. The result of parsing is not the DOM structure defined by standard XML technologies, but an XNode structure redesigned for the application layer.

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

The XNode structure records the source locations of attributes and nodes, and changes the value types of attributes and content to Object, thereby overcoming XML’s original text-document-oriented design flaw and making it more efficient at expressing complex business object structures.

The Nop platform replaces XML Schema with the XDef meta-model language. Leveraging a prefix-guided syntax design, it is more compact and intuitive than JSON Schema.

> For the design of prefix-guided syntax, see DSL Layered Syntax Design and Prefix-Guided Syntax (https://zhuanlan.zhihu.com/p/548314138)

```xml
<person firstname="!string" lastname="!string" address="string" />
```

The structure defined by the XDef meta-model basically resembles the data structure it constrains; you can think of it as taking a concrete model instance and replacing the actual values with type declarations.

For XPath and XSLT transformation syntax, the Nop platform likewise does not adopt them but instead designs alternative syntaxes: XSelector and XTransform.

More importantly, the Nop platform introduces an XML-based template language, XPL (XLang Template Language). It is a Turing-complete template language that supports conditionals, loops, tag abstraction, and template metaprogramming. It is one of the key technologies enabling Reversible Computation on the Nop platform based on XML.

For details, see xpl.md (https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/dev-guide/xlang/xpl.md)

**The most important reason the Nop platform insists on the XML file format is that JSON and even YAML lack a template language like XPL for generating code with complex structures.**

## Features of XPL

1. Lisp-like homoiconicity: XPL is in XML format, and its output is also in XML format.
2. Multiple output modes. When outputMode=node, it directly emits XNode nodes and records the source locations of attributes and nodes during output. This is particularly useful for code generation and breakpoint debugging. It means we can always trace the current node to its original source location, and debugging or error reporting can directly pinpoint the source line.
3. Supports compile-time execution, essentially similar to macros in Lisp.
4. Supports custom tag libraries, and tag libraries support Delta customization. In other words, the XPL template language supports function-level Delta customization.

For concrete examples, see the video [How to implement back-end service functions in the Nop platform via XBiz configuration files](Nop平台中如果通过XBiz配置文件实现后台服务函数)
<!-- SOURCE_MD5:88cf22815577b85308eeca4e78fc1dc6-->
