# Why the Nop Platform Uses XML Instead of JSON or YAML

In the development field, there seems to be a form of political correctness: XML is considered an outdated technology and should no longer be widely used. For the Nop platform, which heavily uses XML to represent domain models, some netizens humorously comment that all information is expressed using XML, which is against the tide. In a previous article titled "[The Equivalence of XML, JSON, and Function AST](https://zhuanlan.zhihu.com/p/554294376)," I explained the equivalence between XML and JSON. Based on this equivalence, both XML and JSON are automatically supported for conversion in the Nop platform, and the choice of which format to use does not affect the semantic meaning of the model. Therefore, it is entirely possible to store model information using JSON files. However, given the current situation, XML has some advantages over JSON in terms of expression.

## Issues with JSON

The primary issue with JSON is the lack of a comment mechanism and multi-line text representation. These issues are resolved in YAML, which is compatible with JSON. Therefore, in the Nop platform, we generally prefer to use YAML over JSON, such as storing front-end JSON pages in `page.yaml`.

```yaml
x:gen-extends: |
  <web:GenPage view="NopAuthUser.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />
```

In XML, embedding JSON is a straightforward matter. However, embedding XML within JSON requires string escaping, which significantly affects data readability. In contrast, embedding XML within YAML is simple and intuitive.

Another issue with both JSON and YAML is the lack of special defined type attributes. In JSON, all properties are theoretically on equal footing, and there is no specification in the JSON standard to quickly distinguish the structure of JSON objects. This makes it difficult to perform efficient deserialization during reverse engineering.

During deserialization, we often need to determine the type of the object being deserialized based on type attributes. However, because JSON does not define a specific `type` attribute that must always be the first property, this determination can only be made after fully parsing the entire object structure. This leads to unnecessary temporary object construction and additional memory consumption.

In comparison, XML always emphasizes the `tagName` special attribute, which helps in visually identifying specific local structures. In simple structures, XML expressions are often more concise and intuitive compared to JSON.

```xml
<row a="1" b="xyz" />

{
  "a": 1,
  "b": "xyz",
  "type": "row"
}
```

## Issues with XML

It is undeniable that XML has some obvious issues in practical use. The primary reason is that XML was originally designed as a text data markup language, and for data types with rich variations, it lacks standardized expression methods. For instance, major corporations like IBM and SUN have actively promoted a series of XML-related standards, which have gradually established XML's reputation as a cumbersome and rigid technology.

Taking XSD (XML Schema Definition) as an example, if you compare it to JSON Schema, the information density of XSD is significantly lower. While JSON Schema can naturally define nested structures, XSD forces all structures to be broken down into `type` and `element` layers.

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

When using XSD, many developers tend to use `xs:sequence` to enforce order on child elements. This enforces a specific sequence on the child nodes, which deviates from the fundamental concept of declarative programming. While this might seem useful in some cases, it introduces unnecessary complexity and overhead.

> Declarative programming is essentially about reducing reliance on sequentiality. Command-line programming imposes an unnecessary burden of ordering and causality.
>
> However, in JSON schema, there's no way to define the order of properties. This means that the structure of JSON objects cannot be enforced to follow a specific sequence.

In summary, while XML may have some advantages in terms of expression over JSON in simple structures, it also has significant limitations in practical use. The choice between XML and JSON ultimately depends on specific requirements and use cases. However, for the Nop platform, XML is considered more suitable due to its inherent properties.

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

The Nop platform extensively uses XML syntax but does not fully comply with current XML standards. It only incorporates a specific subset of XML grammar, such as:

- Removal of Entity support (preventing potential security vulnerabilities)
- Simplified namespace handling (only processed at the root level)
- Elimination of Instruction support (processed only at the root level)

Additionally, the Nop platform does not utilize existing Java-based XML binding technologies like JAXB. Instead, it develops its own XML parser.

The resulting structure is not based on standard DOM but instead follows an application-specific XNode design.

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

The XNode structure records both attribute and node locations, converting the attribute's and content's value types to Object. This design addresses XML's limitation of primarily handling text documents, enabling more efficient representation of complex business object structures.


## Replacement of XML Schema with XDef Meta-model

Instead of using XML Schema, the Nop platform employs an XDef meta-model for data constraints. This approach is similar to XML Schema in structure but offers a more compact and intuitive design. It utilizes prefix-based syntax, aligning closely with JSON Schema in functionality.

```xml
<person firstname="!string" lastname="!string" address="string" />
```

The XDef meta-model defines constraints that mirror the data structure, allowing for precise type enforcement on specific properties. This reduces ambiguity and enhances data integrity.



While XPath and XSLT are widely used in XML processing, the Nop platform opts for its own XSelector and XTransform syntax. These alternatives provide more flexibility and better alignment with application-specific requirements.



The XPL (XML Processing Language) template language is a Turing-complete template language that supports conditional statements, loops, tag abstraction, and template-based programming. It is a key technology in the Nop platform for reversible computation.

**[xpl.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/dev-guide/xlang/xpl.md)**



Despite advancements in JSON and YAML, the Nop platform continues to prioritize XML due to its support for more complex structures through XPL. The availability of robust tools for parsing and manipulating XML makes it a suitable choice for structured data processing.

---



1. Lisp-like Syntax: XPL is designed for XML input and output, mirroring some aspects of Lisp.
2. Multiple Output Modes: When `outputMode` is set to "node," XPL outputs XNode structures, recording both attribute and node locations. This is particularly useful during debugging or when tracking the origin of nodes.
3. Compile-Time Execution: XPL exhibits behavior similar to Lisp's macros, allowing for compile-time optimizations.
4. Customizable Tag Libraries: XPL supports Delta-defined custom tags, enabling function-level customization.



The following example demonstrates how XPL can be used in conjunction with XBiz configuration files to define backend services:

```xml
<service xmlns:xpl="https://example.com/xpl">
  <xpl:define name="backend-service">
    <xpl:property name="url">http://example.com/api</xpl:property>
    <xpl:property name="method">GET</xpl:property>
  </xpl:define>
</service>
```

