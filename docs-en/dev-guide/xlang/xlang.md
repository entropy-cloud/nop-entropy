# Brief Description

XLang is a general-purpose programming language specifically designed for the LowCode domain. Its syntax is based on XML and TypeScript, and it can be seamlessly integrated with the Java language.

Unlike typical template or scripting languages, XLang’s theoretical foundation is Reversible Computation (see [Reversible Computation: The Next-Generation Software Construction Theory](https://zhuanlan.zhihu.com/p/64004026)). It attempts to build a complete set of descriptive, AST (Abstract Syntax Tree)-oriented mechanisms around the core concept of AST, including definition, generation, transformation, decomposition, merging, and compilation. Its design goal is to become a standardized metalanguage for defining and interpretively executing DSLs (Domain Specific Languages). Domain models described with XLang naturally possess reversible semantics, enabling automatic visual design and Delta-based customization; therefore, it is a key technology in the Nop platform for supporting LowCode development.

> Textual representation and visual presentation can be viewed as two semantically equivalent representations of the same model. Visual design can be seen as a reversible transformation between these two representations.

The XLang language consists of the sublanguages XDef, Xpl, XScript, XDsl, XPath, and XTransform.

## XDef

**For details, see [xdef.md](xdef.md)**

XDef is the domain model definition syntax in XLang, analogous to the XSD language (based on XML syntax) and JSON Schema (based on JSON syntax). XDef differs from them in two key ways:

1. The schema description and the domain description are isomorphic; that is, the Tree structure of the schema is consistent with the Tree structure of the domain model.
2. All collection elements define a unique attribute via `xdef:key-attr`, ensuring every node in the domain description has a stable and unique XPath path.

For example, for the following `tasks` list structure:

```
<tasks interval="1000">
  <task id="a" label="Task A" status="1" />
  <task id="b" label="Task B" status="2" />
</tasks>
```

The XDef description is as follows:

```
<!--
@interval When a task is in progress, it will be rechecked after a period, and the interval is configured here; default is 3s.
-->
<tasks interval="number=3000" xdef:key-attr="id" xdef:body-type="list">
  <!--
    @id Unique task id
    @label Task name
    @status Task status
                  0: Initial state, not operable.  1: Ready, operable state.
                  2: In progress, not yet finished.  3: Error, not retryable.
                  4: Finished normally.             5: Error, retryable.
  -->
  <task id="!string" label="string" status="dict:nop/task/task-status" />
</tasks>
```

If described using JSON Schema, it would be:

```
{
   "type": "object",
   "properties": {
        "interval": {
          "default": 3000,
          "description": "When a task is in progress, it will be rechecked periodically; the interval is configured here; default is 3s.",
          "type": "number"
        },
        "items": {
          "items": {
            "id": {
              "description": "Unique task id",
              "type": "string"
            },
            "label": {
              "description": "Task name",
              "type": "string"
            },
            "status": {
              "description": "Task status: \n0: Initial state, not operable.\n1: Ready, operable state.\n2: In progress, not yet finished.\n3: Error, not retryable.\n4: Finished normally.\n5: Error, retryable.",
              "enum": [0, 1, 2, 3, 4, 5],
              "type": "number"
            }
          },
          "type": "array",
          "required": ["id"]
        },
   }
}
```

The JSON Schema structure has a nested relationship similar to the original data structure but inserts redundant `properties` levels, and describing a single attribute requires using complex description objects. In contrast, the XDef description is entirely consistent with the original data structure; it can essentially be viewed as removing redundant parts of the original data and adding local type annotations, making it significantly more concise and intuitive than JSON Schema.

XSD is even more redundant than JSON Schema, and by default it assumes the order of child nodes is important and must be preserved, which clearly introduces unnecessary complications for defining domain structures.

For each collection element, XDef requires explicitly defining a unique attribute to distinguish different child elements. Leveraging this attribute, one can implement an efficient and stable diff algorithm for domain structures (similar to the virtual DOM diff in Vue). Therefore, defining a domain structure with XDef also means defining the Delta structure of that domain; we can easily compute the difference and the sum of two domain structures.

```
  Delta = D1 - D2
  D1 = D2 + Delta
```

## Xpl

**For details, see [xpl.md](xpl.md)**

Xpl is a template language using XML syntax, similar to FreeMarker. It has built-in logic tags for conditionals and loops, and supports importing custom tags and grouping a set of tags into a tag library. Compared to typical template languages, XPL introduces the following features specifically for LowCode development:

1. Multiple output modes. Beyond text output, you can choose to output `XNode` (a general structure corresponding to AST nodes), JSON, XML, and Text. Outputting `XNode` preserves source location information, making it easy to trace multi-stage compiled code back to original source locations during debugging.
2. Custom tag support for customization. The same code can incorporate different tag implementations for different tenants or deployment environments, producing different runtime code.
   For example, the same set of business code can, by applying different customization switches, be compiled into multiple runnable programs for the web and different mini-program platforms. Using the same mechanism, product customerization can also
   be achieved via tag customization. A tag is essentially a function, so the Xpl language inherently provides function-level customization capabilities.
3. Support for metaprogramming. Through macro tags and macro expressions, you can execute at compile time with Turing-complete programming capabilities fully consistent with runtime. Combining metaprogramming with custom tags enables descriptive AST transformations and seamless nesting of DSLs with different styles.

```
<c:if test="${condition}">
  Execute when the condition is true
</c:if>

<c:for items="${list}" var="item" index="index">
  Here you can access the loop variable ${item}, and the zero-based loop index ${index}
</c:for>

  <!--
   The default output mode of the macro:gen tag is XNode. Its body is compiled and then automatically executed; the nodes output during execution are the final compiled code.
  -->
  <macro:gen>
     <c:script>
        let a = 'amount';
     </c:script>
     <input name="${a}" value="${'$'}{c}" />
  </macro:gen>

  <!--
  The final compiled result of the above macro is equivalent to directly compiling the following node
  --->
  <input name="amount" value="${c}" />

  <!-- Import a custom tag library -->
  <c:import from="/my/app.xlib" />

  <!-- Define a tag -->
  <app:CustomTag width="${1}" title="abc">
     body content
  </app:Custom>

app.xlib

<lib>
  <CustomTag>
    <arg name="width" type="Integer" />
    <arg name="title" type="String" />
    <slot name="default" />

    <source>
       <div style="width:${width}px">
         <span>${title}</span>
         <c:slot slot:name="default" />
       </div>
    </source>
  </CustomTag>


</lib>
```

XPL tags can be used not only as a front-end template engine but also to directly execute back-end business logic. Through macro tags, they can integrate with sublanguages of any DSL syntax. For example:

```
my.xlib
<lib>
  <MyDSL macro="true">
     <slot name="default" slotType="node" />

     <source>
        <c:script>
          import my.MyDSLParser;
          let model = new MyDSLParser().parseFromNode($slots.default);
        </c:script>

        <!--
        Perform other compile-time processing based on the parsed model object
        -->
     </source>
 </MyDSL>
</lib>

Usage
<my:MyDSL>
  A grammar format that MyDSLParser can parse
</my:MyDSL>
```

## XScript

**For details, see [xscript.md](xscript.md)**

XScript is a scripting language with syntax similar to TypeScript. In XPL, you can include XScript code via the `<c:script>` tag. XScript adopts a subset of TypeScript syntax.

1. The class definition part and `prototype`-related features are removed. To simplify interoperability with Java, only types that already exist in Java are allowed; you cannot create new types.
2. Only type declarations compatible with Java are allowed.
3. `undefined` is removed; only `null` is used.
4. `generator` and `async` syntax are removed.
5. The `import` syntax is modified; it only supports importing classes and tag libraries.
6. `===`-related syntax is removed; `==` comparisons are forbidden from performing type coercion.

The following features are added:

1. Compile-time expressions
2. Executing XPL tags
3. Calling extension methods. You can register static extension functions for Java objects to add methods to existing classes. For example, `"abc".$firstPart('.')` actually calls `StringHelper.firstPart("abc",'.')`.
4. Security restrictions. All variable names prefixed with `$` are reserved as system variable names; you cannot declare or set variables prefixed with `$` in XScript. Access to sensitive objects such as `System` and `Class` is prohibited.

```
<c:script>
  // Execute compile-time expressions
  let x = #{ a.f(3) }

  // Execute an XPL tag
  let y = xpl('my:MyTag',{a:1,b:x+3})
</c:script>
```

## XDsl

**For details, see [xdsl.md](xdsl.md)**

XLang is an open language. Its XPL tag syntax is very flexible; various DSL expression styles can be introduced via macro tags. Macro tags effectively extend the compiler, allowing new syntax structures and program semantics to be introduced without requiring them to be baked into the base language. This is similar to the role of macros in Lisp.

At the same time, XLang can define standalone DSL models via XDef descriptions, such as the workflow model xwf and the ORM entity-relationship mapping model xorm. These DSL models are uniformly stored in XML format but add strict structural constraints on top of XML. XLang provides a range of support for developing new DSLs, including:

1. Automatically generating parsers and validators for DSL models according to XDef descriptions.
2. Automatically generating visual designers; design details can be finely adjusted through XMeta configuration.
3. Built-in `x:extends` and `x:gen-extends` syntax structures; all DSLs automatically possess Delta programming and metaprogramming capabilities, can freely invoke all Xpl tag libraries, and do not require additional abstraction mechanisms for inheritance or libraries.

```
<beans x:extends="base.beans.xml" >
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

Taking the built-in IoC engine in the Nop platform as an example, it uses a configuration syntax similar to Spring 1.0 and, as an XDsl, automatically inherits the capabilities of `x:extends`.

1. `x:extends="base.beans.xml"` indicates inheritance from an existing configuration file, allowing the custom mechanism to adjust the existing file, e.g., remove the `bean` definition with `id=a`.
2. The `x:gen-extends` node introduces a metaprogramming generator that performs dynamic code generation to produce a `beans` node, which is then merged with the existing `beans` node using a unified Delta merge algorithm.
3. The `feature:on` switch can control that certain `bean` definitions are enabled only when configuration switches are turned on.

For the specific syntax of feature:on and feature:off, see [feature-expr.md](feature-expr.md).

Compared to Spring’s IoC mechanism, all dynamic generation and extension features are uniformly implemented at the XDsl level. After decomposition and merging, a descriptive model conforming to `ioc.xdef` is produced and then parsed by `DslModelParser`. The DSL parser only needs to handle the true domain structure model and does not need to consider any extra decomposition/merging mechanisms. In Spring, built-in mechanisms such as profile and BeanPostProcessor are specially written for the Spring IoC framework and cannot be applied to other DSL development. Moreover, their existence undermines the descriptiveness Spring advocates, leading to very subtle dependencies regarding the timing of bean initialization; without carefully tracing the Spring execution process, it is practically impossible to determine the timing of bean scanning and initialization.

In XLang, different DSLs share the same extension mechanism, allowing them to focus only on the core domain model structures. The entire effect of `x:gen-extends` can be seen as a compile-time code generation technique (production-style programming): continuously concatenating, transforming, and outputting strings or `XNode`. Regardless of how complex the internal process is or how many XPL tag libraries are introduced, the final input sent to the DSL parser is only the DSL model text conforming to the XDef definition. All complexity brought by extensions and secondary abstractions is completely eliminated through compile-time code generation; it does not carry over into the program runtime and incurs no runtime cost.

## XPath

**For details, see [xpath.md](xpath.md)**

The original XPath language is a query language for XML documents. It assumes the application target is a pure text document, and the nodes operated are text nodes, so it is not suitable for executing queries on arbitrary Tree structures. Furthermore, the expression syntax used in XPath is rather peculiar, and it is not convenient to introduce extension functions. Therefore, XLang redefines a simplified version of XPath with the following features:

1. Through adapters, it can be applied to queries on any tree structure, not limited to XML documents.
2. It directly uses XScript as the filtering expression language, without separately defining an expression language for XPath, thereby reusing existing functions and tag libraries.
3. Beyond retrieving data from tree structures, for all domain elements defined in XDsl, a unique XPath path can be specified. Thus, attributes can also be set via XPath.

```
  IXSelector selector = XPathHelper.parseXSelector("a/b[id=aaa]/@attr")
  node.updateSelected(selector,"sss");
  node.selectOne(selector);

  // Via an adapter, you can select from any data structure
  selector.selectOne(adapter, object);

  // The filtering expression is ordinary XScript, with added recognition of @attribute names
  IXSelector complexSelector = XPathHelper.parseXSelector("//b[@a > 3 || @b != 2]/child");
  List<XNode> children = node.selectMany(complexSelector);
```

## XTransform

**For details, see [xtransform.md](xtransform.md)**

Similar to XPath syntax, the xslt transformation syntax in the XML ecosystem applies only to XML text, and in practical applications xslt’s syntax design also introduces unnecessary complexity.
XLang defines XTransform, a syntax dedicated to AST transformation. It is a simplified version of xslt with the following features:

1. Use `XSelector` to select nodes.
2. Use attribute expressions to generate attributes; returning `null` in an attribute expression indicates deletion of the attribute.
3. Express the generation process naturally through nested structures.

```
<xt:transform>
  <xt:script>
     XPL executed at compile time
  </xt:script>

  <xt:mapping name="myMapping">

  </xt:mapping>

  <div>
     <!--
      xt:attrs specifies attributes to copy.
     -->
     <div xt:xpath="root" >
        <!--
          xt:attrs can specify attribute names to copy directly.
          You can dynamically generate attributes via transformation-time expressions  %{}.
        -->
        <div xt:xpath="child" xt:attrs="{a,b,c}" title="%{$node.title}-sub" >
             <xt:apply name="myMapping" />
        </div>
     </div>

  </div>

</xt:transform>
```
<!-- SOURCE_MD5:da72593edc54ed017127ff2741f329ee-->
