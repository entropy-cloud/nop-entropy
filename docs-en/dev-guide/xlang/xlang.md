# Overview

XLang is a general-purpose programming language specifically designed for the low-code development domain. It is based on XML syntax and TypeScript syntax, enabling seamless integration with Java.

The key difference between XLang and general template languages or scripting languages lies in its theoretical foundation, which is rooted in reversible computation theory (see [reversible computation: the next generation of software construction theory](https://zhuanlan.zhihu.com/p/64004026)). It aims to establish a comprehensive set of mechanisms around AST (Abstract Syntax Tree), including definition, generation, transformation, decomposition, merging, and compilation. Its design goal is to become a standardized meta-language for Domain Specific Languages (DSLs).

Based on the domain models described by XLang, it naturally possesses reversible semantics, allowing for automatic visualization design, differential customization, etc. This makes it a critical technology in the Nop platform's support for low-code development.

> Text representation and visual display can be considered as two semantically equivalent representations of the same model. Visual design can be viewed as a reversible transformation between these two representations.

XLang is composed of sub-languages such as XDef, Xpl, XScript, XDsl, XPath, and XTransform.

## XDef

**Detailed description see [xdef.md](xdef.md)**

XDef is the domain model definition syntax in XLang, similar to XSD (XML Schema) for XML-based languages and JSON Schema for JSON-based languages. The key difference lies in two aspects:

1. The schema description aligns structurally with the domain description, meaning that the Tree structure of the schema matches that of the domain model.
2. All collection elements are defined by a unique attribute via `xdef:key-attr`, ensuring that each node in the domain description has a stable and unique XPath path.

For example, consider the following `tasks` list structure:

```xml
<tasks interval="1000">
  <task id="a" label="Task A" status="1" />
  <task id="b" label="Task B" status="2" />
</tasks>
```

XDef describes this as follows:

```xml
<!--
@interval When tasks are ongoing, they will be checked periodically, with the interval set to 3 seconds by default.
-->
<tasks interval="number:3000" xdef:key-attr="id">
  <!--
    @id Unique task ID
    @label Task name
    @status Task status
                  0: Initial state, not executable.  
                  1: Ready state, executable.  
                  2: Ongoing state, still executing.  
                  3: Error state, cannot be retried.  
                  4: Normal completion.  
                  5: Error state, can be retried.
  -->
  <task id="!string" label="string" status="enum:nop/task/task-status" />
</tasks>
```

If using JSON Schema for description, it would be:

```json
{
  "interval": "number:3000",
  "xdef:key-attr": "id"
}
```

```markdown
{
  "type": "object",
  "properties": {
    "interval": {
      "default": 3000,
      "description": "When there are tasks running, the system will automatically detect them at regular intervals. The interval here is set to 3 seconds by default.",
      "type": "number"
    },
    "items": {
      "items": {
        "id": {
          "description": "Unique identifier for the task",
          "type": "string"
        },
        "label": {
          "description": "Label or name of the task",
          "type": "string"
        },
        "status": {
          "description": "Status of the task:\n0: Initial state, not operational.\n1: Ready state, operational.\n2: Running state, not yet completed.\n3: Error state, cannot retry.\n4: Completed state, normal end.\n5: Error state, can be retried.",
          "enum": [0, 1, 2, 3, 4, 5],
          "type": "number"
        }
      },
      "type": "array",
      "required": ["id"]
    }
  },
  "type": "object"
}
```

JSON Schema's structure closely mirrors the nested relationships of the raw data but adds an unnecessary `properties` layer. When describing individual properties, it requires using a complex description object.

In contrast, XDef matches the original data structure exactly. It essentially removes redundant parts from the original data and simply adds local type annotations where needed. This makes it significantly cleaner and more readable compared to JSON Schema.

XSD's level of redundancy exceeds even JSON Schema by another layer. In default cases, it treats the order of child nodes as important and requires them to be preserved, which introduces unnecessary complexity for the domain structure definition.

For each array element, XDef explicitly defines the unique property used to distinguish different sub-elements. By leveraging this property, one can efficiently and reliably implement a diff algorithm (similar to Vue's virtual DOM diff), which is essential for managing domain structures.

Using XDef to define a domain structure means you're also defining the differential structure itself. This allows for straightforward calculation of differences between two domain structures:

```markdown
Delta = D1 - D2
D1 = D2 + Delta
```

## Xpl

**Detailed documentation see [xpl.md](xpl.md)**

Xpl is a template language that uses XML syntax, resembling FreeMarker in its structure. It supports essential logical tags like conditional and loop statements while allowing the inclusion of custom tags through libraries.

Unlike traditional template languages, XPL has been specifically designed for LowCode development, incorporating the following unique features:

1. **Multiple Output Modes**: Outputs can be Text, XNode (AST node), JSON, XML, or a combination thereof. XNode outputs preserve source code location information, aiding in debugging and traceability.

2. **Customizable Tags**: The same code can be tailored for different tenants or deployment environments by adding custom tags to the libraries used. For example, applying different customization switches allows compiling to web endpoints or mobile apps while maintaining a unified codebase.

3. **Meta-Programming Support**: Macros and expressions allow for dynamic logic in the template language during compilation. This enables meta-programming where templates can generate their own runtime modules, ensuring tight integration with runtime environments.

4. **Nested Meta-Programming**: Combining macros with custom tags allows embedding domain-specific languages (DSLs) seamlessly within XPL templates, enabling domain-specific extensions without code duplication.

The following is an example of how differences between two domain structures can be calculated using Xpl:

```markdown
Delta = D1 - D2
D1 = D2 + Delta
```

**Detailed documentation: [xpl.md](xpl.md)**

```
## XScript

**Detailed description see [xscript.md](xscript.md)**

XScript is a script language syntax similar to TypeScript. In XPL, XScript can be introduced using the `<c:script>` tag. XScript uses a subset of TypeScript syntax.

### Characteristics of XScript

1. Removed class definition parts and related prototype aspects.
2. Only allows Java-compatible types to be used; no new types can be created.
3. Undefined is replaced with null.
4. Generator and async syntax are removed.
5. Import statement is modified; only classes and custom tags can be imported.
6. The === operator is replaced, and == for type conversion is prohibited.

### Added Features

1. Compiled expressions
2. Execution of XPL tags
3. Calling extended methods. Extended functions can be registered statically for existing Java objects. For example, `"abc".$firstPart('.')` actually calls `StringHelper.firstPart("abc", '.')`
4. Security restrictions. Variables starting with `$` are preserved as system variables and cannot be declared or set in XScript. Accessing `System`, `Class` etc., is prohibited.
```

## XPL Tags

XPL tags not only serve as a frontend template engine but also execute backend business logic directly. Additionally, they support integration with any DSL sub-language via custom macro tags, such as the `<macro:gen>` tag.

### Example Usage of XPL

```xml
<my:MyDSL>
  <MyDSL macro="true">
    <slot name="default" slotType="node" />
  </MyDSL>
</my:MyDSL>
```

## XScript

**Detailed description see [xscript.md](xscript.md)**

XScript is a script language syntax similar to TypeScript. In XPL, XScript can be introduced using the `<c:script>` tag. XScript uses a subset of TypeScript syntax.

### Characteristics of XScript

1. Removed class definition parts and related prototype aspects.
2. Only allows Java-compatible types to be used; no new types can be created.
3. Undefined is replaced with null.
4. Generator and async syntax are removed.
5. Import statement is modified; only classes and custom tags can be imported.
6. The === operator is replaced, and == for type conversion is prohibited.

### Added Features

1. Compiled expressions
2. Execution of XPL tags
3. Calling extended methods. Extended functions can be registered statically for existing Java objects. For example, `"abc".$firstPart('.')` actually calls `StringHelper.firstPart("abc", '.')`
4. Security restrictions. Variables starting with `$` are preserved as system variables and cannot be declared or set in XScript. Accessing `System`, `Class` etc., is prohibited.

```
<c:script>
  // Execute compile-time expression
  let x = #{ a.f(3) }

  // Execute xpl tag
  let y = xpl('my:MyTag',{a:1,b:x+3})
</c:script>
```

## XDsl

**Detailed description see [xdsl.md](xdsl.md)**

XLang is an open-source language. Its XPL tag syntax is highly flexible, allowing the introduction of various DSL styles through macro tags. Macro tags essentially extend the compiler by adding new syntax structures and program semantics, without needing to embed them in the base language beforehand, similar to Lisp macros.

At the same time, XLang can also define independent DSL models using XDef descriptions, such as workflow models (xwf) and ORM models (xorm). These DSL models are uniformly stored in XML format but with added structural constraints compared to standard XML. XLang provides a series of tools for developing new DSLs, including:

1. Generation of parsers and validators based on XDef descriptions.
2. Automatic generation of visual designers through XMeta configurations, allowing fine adjustments to designer details.
3. Built-in `x:extends` and `x:gen-extends` syntax structures. All DSLs inherently support incremental programming and metaprogramming capabilities without requiring additional inheritance or abstract mechanisms to be designed.

```
<beans x:extends="base.beans.xml">
  <x:gen-extends>
    <c:import from="ioc-ext.xlib" />

    <ioc-ext:CollectAppBeans />

    <beans>
      <c:for begin="1" end="2" var="index">
        <bean id="x_${index}" class="my.XXXX" />
      </c:for>
    </beans>
  </x:gen-extends>

  <bean id="a" x:override="remove" />

  <bean id="b" feature:on="my.xxx.enabled and !my.yyy.enabled">
    <property name="f1" value="3" />
  </bean>
</beans>
```

For example, the Nop platform's ioc engine uses a configuration syntax similar to Spring 1.0 and inherits the `x:extends` capability from XDsl.

1. `<x:extends="base.beans.xml">` indicates inheritance from existing configuration files using a custom mechanism, which can modify existing configurations by removing `id=a` bean definitions.
2. `<x:gen-extends>` nodes introduce metaprogramming generators that dynamically generate `beans` nodes based on the delta merge algorithm.
3. The `<feature:on>` attribute enables conditional bean definitions.

For detailed syntax of `<feature:on>` and `<feature:off>`, refer to [feature-expr.md](feature-expr.md).

Compared to Spring's ioc mechanism, all dynamic generation and extension capabilities in XDsl are implemented at the XDsl layer. After decomposition and merging, a compliant `ioc.xdef` constraint model is generated, which is then parsed by `DslModelParser`. While Spring-specific mechanisms like `BeanPostProcessor` are internally optimized for Spring's ioc framework, they cannot be directly applied to other DSLs and may disrupt Spring's desired declarative nature by introducing subtle dependency timing issues.

In contrast, different DSLs share the same extension mechanisms in XDsl, allowing them to focus on their core domain models. The `x:gen-extends` syntax essentially represents a code generation technology (constructive programming) during compilation, effectively concatenating and transforming `XNode` structures or directly generating `beans` nodes without requiring additional abstraction layers.

The complexity introduced by extensions and abstract abstractions is resolved at compile time in XDsl, ensuring that only the core domain model remains relevant during runtime. This approach eliminates the need for complex dependency management and avoids introducing unnecessary overheads during execution.

## XPath

**Detailed description see [xpath.md](xpath.md)**

The original **XPath language** is designed for querying XML documents. It assumes that the target is a plain text document, and all operations are performed on text nodes. Therefore, it is not suitable for arbitrary tree structures.

On the other hand,
- The **XPath syntax** is quite unique.
- Introducing extension functions is not very convenient.

Thus, the **XLang language** defines a simplified version of XPath, which has the following characteristics:

1. Can be used with adapter for any tree structure, not limited to XML documents.
2. Uses XScript directly as the filter expression language, without separately defining XPath expressions. This allows us to leverage existing functions and libraries.
3. Not only for data retrieval from tree structures but also for setting attribute values based on the defined XPath path for XDsl-defined elements.


## Example Code Snippet

```xml
<xt:transform>
    <xt:script>
        <![CDATA[
            <xpl:prefix id="prefix-1"/>
        ]]>
    </xt:script>
    
    <xt:mapping name="myMapping">
        <div>
            <xt:attrs name="myAttr">
                <!-- Dynamic attribute values can be generated using ${variable} -->
                <xpl:value>${node.title}-sub</xpl:value>
            </xt:attrs>
            <xt:xpath name="childNode" expression="child"/>
            <xt:apply name="myMapping"/>
        </div>
    </xt:mapping>
</xt:transform>
```



**Detailed information can be found in [xtransform.md](xtransform.md)**

The **XTransform syntax** is similar to XPath but specifically designed for XML transformations. Unlike XSLT, it avoids unnecessary complexity in syntax design.

The **XLang language** defines an **XTransform grammar** for AST conversion, which is essentially a simplified version of XSLT. It has the following characteristics:

1. Uses `XSelector` for node selection.
2. Attributes can be generated using attribute expressions. If an expression returns `null`, the corresponding attribute is removed.
3. Natural nesting of elements allows for the natural expression of generation processes.



```xml
<xt:transform>
    <xt:script>
        <![CDATA[
            <xpl:prefix id="prefix-1"/>
        ]]>
    </xt:script>

    <xt:mapping name="myMapping">
        <div>
            <!-- Attributes to be copied can be specified using xt:attrs -->
            <div xt:xpath="root" xt:attrs="{a, b, c}">
                <!-- Dynamic attribute values can be generated using ${variable} -->
                <div xt:xpath="child" title="%{$node.title}-sub">
                    <xt:apply name="myMapping"/>
                </div>
            </div>
        </div>
    </xt:mapping>
</xt:transform>
```

