Title: Are there any compelling reasons to choose XML over JSON or YAML for a new project today?

Question Details:

It feels like the software development industry has almost completely moved on from XML. For any new project, JSON (or its more human-friendly superset, YAML) seems to be the default choice for data exchange, configuration, and APIs.

The common arguments are that XML is verbose, parsing is complex, and it's a legacy from the SOAP/enterprise Java era. In contrast, JSON is lightweight, native to JavaScript, and generally easier to work with.

But is the choice always this simple? Are there specific domains or advanced use cases where XML's features might still provide a significant advantage that makes it the superior choice, despite its known drawbacks?

Answer:

While the traditional XML technology stack (e.g., DOM, XSD) has its design flaws, this doesn't discredit the XML syntax itself. By selecting a lean subset of XML features and designing a new toolchain for it, it's possible to build efficient and powerful systems.

## 1. Flaws of the Traditional XML Stack

XML's poor reputation is largely due to design issues in its accompanying technology stack:

1.  **DOM: Designed for Text, Not Data**: The standard output of XML parsing is a DOM tree. To ensure a lossless round-trip for text information, it retains all whitespace text nodes and comment nodes. For example, in the structure `<book><title>a</title></book>`, the children of the `book` node include not only the `title` element but also potentially multiple whitespace text nodes with no business meaning, which contradicts the intuition of an application data model.
2.  **Lack of Native Types**: XML natively supports only text attributes. `size="3"` is parsed into the string `"3"`, requiring additional type conversion at the application layer.
3.  **Cumbersome Design Standards**: Early standards like XSD and SOAP were designed to be extremely verbose. For instance, XSD forces the separation of type definitions and element declarations, whereas JSON Schema allows for natural nesting.

## 2. Expressiveness Limitations of JSON/YAML

However, the alternatives, JSON and YAML, are not perfect either, especially when building complex Domain-Specific Languages (DSLs), where they also have their limitations:

*   **Problems with JSON**: It lacks native support for comments and multi-line strings, which is very inconvenient when writing complex configurations or embedding code snippets.
*   **YAML's Improvements and New Problems**: While YAML solves the above issues, both it and JSON lack a **type identifier that is specially treated at the syntax level**. In principle, all properties within an object are equal.

This means you need to use a regular property (like `"type": "row"`) to designate the node type. This not only adds redundancy but can also impact deserialization efficiency. In contrast, an XML element's tag name (`tagName`) is an inherent, high-priority discriminator at the syntax level, which helps in quickly identifying the structure.

For example, for a simple structure, XML is more concise:
```xml
<row a="1" b="xyz" />
```
Whereas the equivalent information in JSON would be:
```json
{
  "type": "row",
  "a": 1,
  "b": "xyz"
}
```

## 3. The Solution: A Reformed XML Data Model, `XNode`

The solution to these problems is to **retain XML's structured syntax but completely discard its outdated technology stack**. The Nop Platform (an open-source low-code platform) achieves this by implementing a custom data model called `XNode`.

`XNode` is not a standard DOM; it is a lighter, more intuitive tree structure designed specifically for application data.
A simplified Java class definition for `XNode` is as follows:
```java
class XNode {
    SourceLocation loc; // Records the node's location in the source file
    String tagName;
    Map<String, ValueWithLocation> attributes; // Attribute values can be any object
    List<XNode> children;
    ValueWithLocation content; // Node content can also be any object
}

class ValueWithLocation {
    Object value; // Stores the value uniformly as an Object type
    SourceLocation location;
}
```
By using a custom parser to parse XML files directly into an `XNode` tree, the following improvements are achieved:
*   **Ignoring Non-Essential Nodes**: Insignificant whitespace text nodes are automatically ignored during parsing.
*   **Support for Native Types**: Syntax like `enabled=true` can be parsed, storing `true` (a boolean) directly in the `XNode`'s attributes, avoiding manual type conversion.
*   **Retaining Source Location**: `XNode` records the source file location of every node and attribute, greatly facilitating error tracing and debugging.
*   **Modernized Tooling**: The more compact `XDef` meta-model language replaces XSD. For example, the definition for a `person` model can be extremely concise:

```xml
<!-- Define the model using an instance with type declarations -->
<person firstname="!string" lastname="!string" />
```

## 4. A Key Advantage: XML as a Programmable Model (AST)

A core technical idea in the Nop Platform is that the same information can have multiple representations: **XML and YAML are just different representations of the same model (a tree structure), and they can be converted into one another**. A model can be written in either XML or YAML, and it will be parsed into the same in-memory AST object.

Taking a single step in an "order discount rule" as an example, the same business logic can have two completely equivalent textual representations:

**YAML format** (`.task.yaml`):
```yaml
steps:
  - type: xpl
    name: discount_step
    when: "order.getOriginalPrice() < 100"
    source: |
      order.setRealPrice(order.getOriginalPrice());
```

**XML format** (`.task.xml`):
```xml
<steps>
    <xpl name="discount_step">
        <when><![CDATA[order.getOriginalPrice() < 100]]></when>
        <source>
            order.setRealPrice(order.getOriginalPrice());
        </source>
    </xpl>
</steps>
```

Both formats are defined by the same `task.xdef` meta-model and are parsed into identical in-memory objects (ASTs) in the Nop Platform, allowing developers to choose the format they prefer.

Since they are equivalent, why favor XML syntax? The answer lies in the excellent foundation it provides for a templating language like **XPL (XLang Template Language)**. XPL transforms XML from static data into a **programmable model**.

For example, we can use an XPL template to dynamically generate a piece of configuration.

```xml
<form-item name="${field.name}" label="${field.label}">
    <!-- c:if is a custom XPL tag that determines whether to output its inner XML content based on the test condition -->
    <c:if test="${field.required}">
        <validation rule="required" message="${field.label} is required." />
    </c:if>
</form-item>
```

**Features of XPL include:**

1.  **Homoiconicity**: The XPL template itself is in XML format, the data it processes is XML (`XNode`), and the result it generates is also XML (`XNode`). This "code-is-data" characteristic provides powerful support for metaprogramming.
2.  **Structured Output and Debugging Support**: XPL's output mode can directly generate an `XNode` tree rather than plain text. This means each part of the generated result can retain a reference to its source location in the template. When a runtime issue occurs, it's much easier to trace it back to the template code that generated the node, significantly simplifying the debugging process.
3.  **Building DSLs with Custom Tags**: Developers can define custom tags like `<oa:SendMail>`. In the XPL engine, each tag can be associated with translation logic that transforms these declarative XML nodes into executable code. This offers a natural way to construct Domain-Specific Languages (DSLs).
4.  **AST-Level Transformation Capabilities**: With `XTransform`, complex structural transformations can be applied to the `XNode` tree before execution. For instance, a custom `<email-action>` tag, which the engine doesn't recognize, can be mapped via transformation rules into a combination of one or more basic actions that the engine does support. This enhances the framework's extensibility without modifying the core engine.

### Conclusion

So, is there any reason to choose XML for a new project today?

*   **For most conventional scenarios, such as web APIs and simple application configurations, the answer is usually "no."** JSON and YAML are more lightweight, easier to use, and a more practical choice in these areas.
*   **But if you are building a low-code platform, a complex rule engine, or any system that heavily relies on metaprogramming and DSLs, the answer is "yes."**

In this case, you are not choosing the old, cumbersome XML, but rather a **tree-structured description language that acts as an executable model with powerful metaprogramming capabilities**. Its core advantage lies in the ability of toolchains like XPL to leverage XML's structure and homoiconicity for complex, structure-aware code generation and transformation.
