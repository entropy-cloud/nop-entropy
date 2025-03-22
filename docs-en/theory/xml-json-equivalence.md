# Equivalence of XML, JSON, and Function AST

The overall technical strategy of the Nop platform can be said to revolve around Tree structures. The key challenge for the Nop platform is how to represent these Tree structures. In the Java backend architecture, XML was once the preferred method for representing Tree structures, with Spring, Hibernate, SOA, BPMN, etc., being classic use cases for XML. However, over time, there has been a persistent criticism that XML is cumbersome and inefficient. Is this truly an inherent flaw of XML? With the rise of Web frontends, JSON, which is easier to parse and manipulate in JavaScript, gradually took the place of XML, becoming the de facto standard for expressing complex data objects. Does this mean XML is now obsolete? The rise of low-code development introduced a new requirement: how to structure executable function logic? How can we achieve a natural and intuitive conversion and processing of AST syntax trees? This paper will briefly introduce the basic abstraction of Tree structures in Nop Platform 2.0, as well as the equivalence transformation scheme between XML, JSON, and AST.

The Nop platform believes that **Everything is a Tree**. Trees can be represented through various serialized formats such as XML and JSON. XML and JSON can also perform bidirectional conversion. If an interpreter is assigned to each node of the Tree, it automatically becomes an Abstract Syntax Tree (AST) and gains executable semantics.

## 1. Issues with XML

The DOM structure obtained from XML parsing indeed has serious design flaws, **essentially because its original design intent was for text documents rather than general application data structures**. For example:

```xml
<book>
   <title>aaa</title>
   <!--销售价格-->
   <price>30</price>
</book>
```

To ensure that the text document can be re-serialized to reconstruct the original document without losing any text, DOM inserts additional text nodes, leading to a mismatch between the DOM structure and application semantics. The resulting DOM structure is as follows:

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

In the DOM structure, the `book` node has seven child nodes instead of two, including four text nodes containing only whitespace to preserve the original document's formatting information and one comment node. Additionally, the `price` child node does not contain the number 30 directly but rather a text node with the string "30".

Using the DOM structure to organize application data introduces numerous redundancies while losing the most basic distinction between atomic data types. It is not a universally suitable choice. The earlier proposal by PuTTY EOS that all business data should be centralized in DOM nodes and then accessed via DOM APIs, supposedly simplifying programming and unifying the processing mechanism, strikes me as either misguided or indicative of someone who has been influenced by XML dogmatism.

In addition to the issues with the DOM structure, XML dogmatism has deepened人们对XML的误解。例如，明明可以采用以下表达方式：

```xml
<MyFunction a="xxx" b=”3“ />
```

但XML狗马主义的设计却是：

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

XML's original design aim was to be minimalistic, and in a sense, node attributes are redundant features. Dogmatists often prefer not to use the attribute mechanism, going so far as to advocate for the cancellation of self-closing tags like `<node/>`, since `<node></node>` already indicates a closed tag.

Additionally, because XML originated from markup languages, its primary application scenarios involved adding metadata to business data. This led some people to insist that all business data must be expressed through text nodes, with attributes only used for invisible identifiers like `id`.

This approach was once very popular, exemplified by formats like the Maven pom.xml file and SOAP's XML serialization format.

While the XML format has certain limitations, these issues can be effectively mitigated. On top of the DOM structure, simplified APIs are provided to hide the complexity of DOM operations. For instance, the dom4j library offers APIs like `elements/elementText` for directly accessing non-whitespace nodes. Furthermore, we can even discard the DOM structure and parse XML into a generic Tree structure directly.

For the issue where XML only supports text attributes, the simplest solution is to extend the XML syntax slightly. For numeric and boolean attributes, remove quotes from attribute values, such as:

```xml
<Window enabled=true size=3 />
```

Modern HTML parsers can actually parse this tag structure; they just always interpret the results as text values.

React's JSX syntax can be considered another variant of XML. It extends attribute value types using the `{}` syntax, like:

```xml
<Control size={3} enabled={true}>
</Control>
```

If you still want to adhere strictly to the XML format, you can use template syntax, such as:

```xml
<Control size="${3}" enabled="${true}">
</Control>
```

Alternatively, use the prefix syntax introduced in my previous article [DSL分层语法设计及前缀引导语法](https://zhuanlan.zhihu.com/p/548314138):

```xml
<Control size="@:3" enabled="@:true">
</Control>
```


## 2. XNode: Generic Tree Structure

In the Nop platform, XNode is used to represent a generic Tree structure:

```java
class XNode implements ITreeStructure {
    String comment;
    String tagName;
    Map<String, ValueWithLocation> attributes;
    List<XNode> children;
    ValueWithLocation content;
}
class ValueWithLocation {
    SourceLocation location;
    Object value;
}
```

- **XNode** omits support for namespaces, which is rarely used in business scenarios but significantly impacts performance.
- **Comment** is simplified to only allow annotations above nodes.
- During XML parsing, all empty text nodes are ignored.
- If a node has no child nodes, its content is stored in the `content` field.
- Both attributes and node values are of type Object, along with their SourceLocation.

Unlike typical template languages, Nop's Xpl template language features specialized syntax designed for code generation.

1. **Xpl Template** supports multiple output modes. If `outputMode=node` is selected, it will output an XNode instead of text content. The `ValueWithLocation` class then stores the source location corresponding to the output value. This mechanism is simpler and more stable than JavaScript's SourceMap, while offering better debugging capabilities via `XNode.dump()`, which prints attribute and node source locations.

2. **XNode attributes** cannot be null. If an attribute's value is set to null, it effectively removes that attribute. This feature simplifies the generation of optional attributes:

```xml
<prop name="a" mandatory="${model.mandatory ? true: null}" />
```

For cases where `mandatory=false`, the following is generated:

```xml
<prop name="a" />
```


## 3. XML to JSON Conversion

In the Nop platform, the conversion between XML and JSON follows the diagram shown below:

<img title="" src="xml-to-json.png" alt="" data-align="center" width="360">

The XDefinition meta-model (similar to XML Schema Definition, XSD) defines the structure of an XNode (Tree), while the XMeta meta-model (similar to JSON Schema) defines the structure of DataBean objects (Classes). XDefinition and XMeta are converted between each other through specific rules, such as mapping XNode nodes to objects, node attributes to object properties, and converting XML names to camelCase for object attribute names.

```java
objMeta = new XDefToObjMeta().transform(xdef);
bean = new DslXNodeToJsonTransformer(forEditor, xdef).transformToObject(node);
node = new DslModelToXNodeTransformer(objMeta).transformToXNode(bean);
```

In the process of converting an XNode to a DataBean, there is a subtle aspect to note: the transformation result may not be unique. We can transform an XNode into different structures of DataBean based on specific usage requirements. If we view DataBean as a form of representation for complex domain model structures, then at least two distinct purposes must be considered: one for editing the domain model within a visualization editor and another for interpreting the domain model in an interpreter. If the domain model contains function definitions, the function can be defined using text-based code during editing, while execution requires compiling the function definition into an executable `IEvalAction` object. In the Nop platform, the `forEditor` parameter is used to distinguish between these two usage scenarios, thereby determining whether to compile function code during XNode transformation.

Currently available workflow engines or rule engines do not explicitly support multiple representation concepts, leading to a significant amount of repetitive code when implementing both editors and runtime engines.

In the Nop platform, even when an XDefinition meta-model is not defined, a simplified bidirectional conversion mechanism between XML and JSON is still provided. For example:

```xml
<root a="1">
  <child name="c1">child value</child>
</root>
```

is converted to

```json
{
    "$tag": "root",
    "a": "1",
    "$body": [
        { "$tag": "child", "name": "c1", "$body": "child value" }
    ]
}
```

Using the `$tag` attribute to represent XML tag names and the `$body` attribute to represent XML node content.

The JSON format used in the [Baidu AMIS framework](https://aisuda.bce.baidu.com/amis/zh-CN/components/form/index) is quite similar, using the `type` attribute for tag names and the `body` attribute for node content:

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

However, the consistency of AMIS formats is not very good. While early container components had messy body property names (e.g., `controls/content/body`), later versions were cleaned up to some extent but still lack automatic XML conversion capabilities and require additional descriptive information.

A disadvantage of JSON in practice is its lack of a comment mechanism. To address this, the Nop platform supports JSON5 and YAML formats internally while treating them as extensions of the JSON object format for consistent processing.
```json
{
    "$fn":"myFunc",
    "a": 1,
    "b": "xx"
}
```

If we treat the function name as a component name and the function parameters as component parameters, then the actual function call can be implemented using a visual component designer. Currently, most low-code platforms implement their visual designers based on form models. While many have attempted to decouple the visual designer from specific component libraries, we can take it further by also separating layout logic out of the designer. **A designer without built-in layout logic can still be used to configure function combination logic.**

In the Nop platform, the primary approach for converting XNode into executable logic is through the custom tag library mechanism in the XPL template language. For example, in the action definition within a workflow:

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

Each node corresponds to a custom tag, which is translated into executable code by the tag library. If we consider XNode as an abstract syntax tree (AST), then these custom tags are akin to the syntax-directed translation in compiler theory. This translation process is context-independent, meaning that regardless of how the tags are nested, the corresponding tag name will always translate into the same executable logic.

> The early implementation of the Baidu AMIS framework deviated from this approach. Instead of translating based solely on the `type` name, it considered the full path of the JSON nodes.

In common workflow engines, simplifying user configuration often requires embedding numerous business-specific implementations directly into the engine. Consequently, the designer also needs to be customized and modified accordingly. In contrast, the Nop platform leverages meta-programming and the XTransform mechanism to isolate these extensions entirely outside the engine. For example:

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

The underlying engine does not need to embed numerous executable actions. Instead, using AST transformation at the XNode level allows for extensive structural modifications, which are then translated into executable semantics via the custom tag library mechanism and the XPL template language.

