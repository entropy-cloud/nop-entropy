# Meta Programming in Low-Code Platforms

In the realm of programming languages, Lisp has always stood out as a unique entity. Its uniqueness is often summarized with the phrase "Lisp is a programmable programming language." This implies that Lisp possesses strong meta-programming capabilities, allowing developers to create new syntax abstractions on their own. In simple terms, **writing code** refers to creating programs, while **meta-programming** involves **writing code that generates code**.

Lisp achieves this through macros. Essentially, a Lisp macro is a built-in code generator embedded within the language itself. While modern languages like Scala and Rust also employ macros, they are typically viewed as complex lower-level tools, rarely used by average developers.

Nop platform's XLang language is considered a core technology for implementing reversible computing principles, as outlined in the equation `App = Delta x-extends Generator<DSL>`. This represents a new programming paradigm focused on DSLs (Domain-Specific Languages) and differential programming. XLang offers a comprehensive system of structured, scalable solutions based on the Generator pattern.

Lisp's macros provide meta-programming capabilities, while XLang goes further by introducing macro functions. These macro functions operate at compile time to automatically generate abstract syntax trees (ASTs). For instance, a macro function might take a scope and an expression as parameters and return a CallExpression representing the AST of the generated code.

Macro functions in XLang require specific parameters:
- The first parameter must be of type `IXLangCompileScope`.
- The second parameter must be a `CallExpression`.
- The return type is always an `Expression`.

When compiling macro functions, the corresponding AST (Abstract Syntax Tree) is passed as a `CallExpression`. For example:

```javascript
let result = xpl `<c:if test="${x}">aaa</c:if>`;
```

Here, `xpl` is a macro function that takes two parameters:
1. A `TemplateStringLiteral` containing the XML text `<c:if test="${x}">aaa</c:if>`.
2. The variable `x`.

The macro function then generates a `CallExpression` representing this XML structure.

By leveraging macro functions, developers can embed various syntax formats into XScript, such as:
- C#-style LINQ (`let result = linq `select ff from myObject o where o.value > 3`;`)

Nop platform's built-in macros, like `x:gen-extends`, enable dynamic generation of model differences during analysis and loading. This allows for efficient resolution of traditional programming challenges through a novel approach to code generation.

In summary, the Nop platform's meta-programming capabilities, particularly through XLang's macro functions, offer powerful tools for addressing complex technical challenges in a manner that is both intuitive and highly effective.


| Function Name | Description |
|--------------|-------------|
| xml          | Parses XML text to get an XNode node, and wraps it as a LiteralExpression. |
| xpl          | Parses XPL template text to get an Expression. |
| sql          | Parses XPL template text to generate SQL statements, getting an Expression. |
| jpath        | Parses JSON path text to get a JPath object, and wraps it as a LiteralExpression. |
| xpath        | Parses XPath text to get an XSelector object, and wraps it as a LiteralExpression. |
| selection    | Parses similar to GraphQL Query, getting a FieldSelection object, wrapped as a LiteralExpression. |
| order\_by     | Parses the `order by` clause, obtaining a List<OrderFieldBean> object, wrapped as a LiteralExpression. |
| location      | Returns the source code location of the function call, wrapping it as a LiteralExpression. |
| IF           | Implements similar to Excel's IF function. |
| SWITCH       | Implements similar to Excel's SWITCH function. |

Because macros are executed at compile time, using macros for parsing can optimize system performance. For example, when retrieving the `a` attribute of node `b` from an XNode:

```xml
node.selectOne(xpath `a/@b`)
```

Since XPath is a macro, it will be parsed during compilation, and at runtime, it simply passes a constant value to `selectOne`.

Macros enable custom syntax, such as converting `IF(X, Y, Z)` into an `if` statement.


## XPL Template Language

XPL is part of the XLang language. It uses XML format and includes tags like `<c:if>` and `<c:for>`, providing comprehensive logical operation rules. The XML-based template language supports Lisp-like behavior, where the code format matches the generated data format.

General template languages (like Freemarker or Velocity) lack this similarity and are mainly used for text generation rather than code generation. XPL template language is designed specifically for code generation, offering multiple output modes:

1. **node Mode**: Outputs an XNode node. This mode retains source code location information, allowing you to know which attributes and nodes were generated from the source code.
2. **xml Mode**: Outputs XML text, automatically escaping attributes and content.
3. **html Mode**: Outputs XHTML text, with most tags using full formatting (e.g., `<div></div>`) instead of short tags like `<div/>`.
4. **text Mode**: Disallows node and attribute output, only allowing text content without XML escaping.
5. **xjson Mode**: Converts XNode nodes into JSON objects following specific rules.
6. **sql Mode**: Outputs SQL statements, converting expressions into parameters.

For example, generating a complex SQL query:

```xml
<filter:sql>
  o.id in (select o.id from MyTable o where o.id = ${entity.id})
</filter:sql>
```

At runtime, this becomes `o.id in (select o.id from MyTable o where o.id = ? )`, with the value replaced by a parameter rather than being concatenated directly into the SQL text.


## Compiled Expressions

XPL template language includes tags like `<macro:gen>` and `<macro:script>`, which are executed at compile time. For example:

```xml
<macro:script>
  import test.MyModelHelper;

  const myModel = MyModelHelper.loadModel('/nop/test/test.my-model.xlsx');
</macro:script>
```

After compilation, subsequent expressions can use the compiled variables, such as `${myModel.myFunc(3)}`.

The language also supports compiled expressions using `#{expr}` syntax. These are executed and replaced at compile time, passing their results directly to runtime.

For instance:

```xml
<div xpl:if="#{false}">This node will be automatically removed during compilation.</div>
```

At compile time, if the value of `xpl:if` is `false`, the node is removed from the final output.


The `<macro:gen>` content is part of the Xpl template syntax. It will first compile the body, then execute it, collect the output result, and finally compile the generated result.

The `<macro:script>` content is part of the XScript syntax, and it will discard the return value.


## Custom Macros

In the Xpl template language, macros can be defined within the tag library. The difference between a macro tag and a regular tag lies in that the macro's source segment is immediately executed after compilation, followed by collecting the output generated during execution.

For example, you can define a macro tag like `<sql:filter>`, which can perform the following structure transformation:

```xml
<sql:filter>and o.fld = :param</sql:filter>
```

This will be transformed into:

```xml
<c:if test="${!_.isEmpty(param)}">
  and o.fld = ${param}
</c:if>
```

The specific implementation can be found in the [sql.xlib](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-orm/src/main/resources/_vfs/nop/orm/xlib/sql.xlib) library.

```xml
<filter macro="true" outputMode="node">
  <slot name="default" slotType="node"/>

  <source>
    <c:script>
      import io.nop.core.lang.sql.SqlHelper;
      import io.nop.core.lang.sql.SQL;

      const sb = SqlHelper.markNamedParam(slot_default.contentValue);
      const cond = sb.markers.map(marker => "!_.isEmpty("+marker.name+")")
        .join(" and ");
      const sqlText = sb.renderText(marker => {
        return "${" + marker.name + "}";
      });
    </c:script>

    <c:if xpl:ignoreTag="true" test="${'$'}{${cond}}">${sqlText}</c:if>
  </source>
</filter>
```

The above macro will transform the node content, generating a `<c:if>` node, which is then compiled by the template engine. This achieves the same effect as manually writing the corresponding node.

* By setting `slotType="node"`, the slot directly reads the node's content without parsing.
* The `xpl:ignoreTag` attribute ensures that the current node and its children are not treated as Xpl tags, so they are output as plain XML nodes.
* The `test="${'$'}{${cond}}"` expression is correctly identified and transformed into `test="${cond}"`.

**Note:** This macro resembles Lisp's macros in simplicity. It provides a lightweight AST transformation mechanism similar to an embedded code generator.


## Compiling to AST

The `<c:ast>` tag can be used to obtain the Abstract Syntax Tree (AST) of the content. For example:

```xml
<c:ast>Content</c:ast>
```

This will return the AST corresponding to "Content".

```markdown
# Validator Configuration

- The `<Validator>` component is used to configure validation logic.
- Below is the XML configuration for the validator:

```xml
<Validator ignoreUnknownAttrs="true" macro="true">
```

## Attributes

1. `runtime`: Indicates whether runtime attributes should be processed.

2. Optional attributes:
   - `optional="true"`
   - `defaultValue="$scope"`

3. `<attr>` tags define attributes to validate:

```xml
<attr name="obj" defaultValue="$scope" runtime="true" optional="true"/>
```

## Slot Configuration

1. `<slot>` defines a slot for the validator's output.

2. The `slotType` attribute specifies the type of slot:

```xml
<slot name="default" slotType="node"/>
```

3. Description:
   - The macro functionality processes the component at runtime.
   - Use XML macros to embed dynamic content into templates.
   - Ensure that compiled content is stored in variables for runtime use.

## Code Block

Here's an example of a validator configuration using `<c:script>` tags:

```xml
<c:script>
  import io.nop.biz.lib.BizValidatorHelper;
  
  let validatorModel = BizValidatorHelper.parseValidator(slot_default);
  let ast = xpl `
    <c:ast>
      <c:script>
        import io.nop.biz.lib.BizValidatorHelper;
        if(obj == '$scope') obj = $scope;
        BizValidatorHelper.runValidatorModel(validatorModel, obj, svcCtx);
      </c:script>
    </c:ast>
  `;
  
  // Replace the AST with the compiled model
  return ast.replaceIdentifier("validatorModel", validatorModel);
</c:script>
```

# Runtime Attribute Handling and Expression Evaluation

The `obj` attribute is treated as a runtime attribute, not its value. If the `runtime=true` attribute is not marked, it can be used in the source segment. However, due to the macro tag's execution during compilation, the attribute value at runtime can only be a fixed value or an expression evaluated at compile time.

```xml
<biz:Validator obj="${entity}"/>
```

## XDSL Delta Generation and Merging Mechanism

All DSLs in the Nop platform support delta merging via the `x-extends` mechanism. This mechanism implements the reversible computation theory required by the Delta x-extends Generator<DSL>.

> App = Delta x-extends Generator<DSL>

### Detailed Explanation of Merging Order

The merging order is defined as follows:

1. `x:gen-extends`
2. `x:post-extends`

```xml
<model x:extends="A,B">
    <x:gen-extends>
        <C/>
        <D/>
    </x:gen-extends>

    <x:post-extends>
        <E/>
        <F/>
    </x:post-extends>
</model>
```

### Merging Result

The final merged result is:

```xml
<F x-extends="E,F" x-extends="D,C" x-extends="B,A"/>
```

### Overriding Behavior

1. The current model overrides `x:gen-extends` and `x:extends`.
2. `x:post-extends` overrides the current model.

Using `x:extends` and `x:gen-extends`, we can effectively implement the decomposition and composition of DSLs. For detailed information, refer to [XDSL: General Domain-Specific Language Design](https://zhuanlan.zhihu.com/p/612512300).

## Data-Driven Delta Code Generation

To implement reversible computation theory at the system level, Nop platform provides a data-driven delta code generation tool called XCodeGenerator.

### Customization of Generators

Most generators are tailored to specific purposes, such as MyBatis's code generator. Its control logic is implemented by a specific `CodeGenerator` class, which reads templates, constructs file paths, and initializes context variables. If you need to adjust code generation details, you typically modify the `CodeGenerator` class.

### Unique Approach of XCodeGenerator

XCodeGenerator differs from traditional generators in that it treats template paths as a micro-DSL. It encodes judgment and loop logic within the path format, allowing the generator to control the code generation process through the template's structure. For example:

```xml
/src/{package.name}/{model.webEnabled}{model.name}Controller.java.xgen
```

This pattern generates a `Controller.java` class for each enabled model where the `webEnabled` attribute is set to true.

### Design Advantages

By adjusting the template directory structure, you can control both the target code's directory structure and generation timing. For detailed information, refer to [Data-Driven Delta Code Generation](https://zhuanlan.zhihu.com/p/540022264).

XCodeGenerator can be integrated with Maven packaging tools. It performs code generation both before and after Java compilation, acting similarly to a Java annotation processor (APT). However, its usage is simpler and more intuitive than APT.

For integration details, refer to [Integrating Nop Platform's Code Generator](https://zhuanlan.zhihu.com/p/613448320).

