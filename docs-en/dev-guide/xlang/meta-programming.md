# Metaprogramming in Low-Code Platforms (Meta Programming)

Among many programming languages, the venerable Lisp has always been a unique presence, a uniqueness often summarized as “Lisp is a programmable programming language.” This means Lisp has powerful metaprogramming capabilities, allowing programmers to freely create new syntactic abstractions. Put simply, programming is writing code, while metaprogramming is writing code that generates code. Lisp provides metaprogramming via macros, which are essentially code generators embedded in the language. Beyond Lisp, modern languages like Scala and Rust also offer macro designs, but macros are generally seen as complex, low-level technologies and rarely make it into the average programmer’s toolbox.

XLang, part of the Nop platform, is one of the core technologies implementing the principles of Reversible Computation. To realize the programming paradigm proposed by the Reversible Computation theory, namely
`App = Delta x-extends Generator<DSL>`,
a new DSL- and Delta-oriented paradigm,
XLang defines a complete, systematic set of Generators covering all aspects of application development. Lisp macros only provide a metaprogramming mechanism for generating Lisp AST, while XLang not only introduces macro functions to generate XLang AST, but also provides the Xpl template syntax for code generation—ranging from local function bodies, to individual model files, and even entire module directories. In particular, all DSLs defined in the Nop platform have a built-in Delta generation mechanism via `x:gen-extends`, allowing model deltas to be dynamically generated and automatically merged during parsing and loading. This creates a new approach to software structure reuse, solving many technical problems that are difficult to handle under traditional programming paradigms. In this article, I will briefly introduce these built-in metaprogramming mechanisms in the Nop platform.

## Macro Functions

XLang defines macro functions similar to Lisp macros. Macro functions are executed at compile time and automatically generate Expression abstract syntax tree (AST) nodes.

Macro functions have special parameter requirements and need the `@Macro` annotation. See [GlobalFunctions](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/functions/GlobalFunctions.java) for concrete examples.

> EvalGlobalRegistry.instance().registerStaticFunctions(GlobalFunctions.class) registers all static functions in the class as global functions available in the XScript scripting language.

```javascript
    @Macro
    public static Expression xpl(@Name("scope") IXLangCompileScope scope, @Name("expr") CallExpression expr) {
        return TemplateMacroImpls.xpl(scope, expr);
    }
```

The first parameter of a macro function must be of type IXLangCompileScope, the second must be of type CallExpression, and the return value must be of type Expression.

When compiling a macro function, the AST corresponding to the function call is passed in as a CallExpression. For example:

```
let result = xpl `<c:if test="${x}">aaa</c:if>`
```

When compiling the xpl macro function, the first argument of the CallExpression is a TemplateStringLiteral—that is, the XML text in the call above, `<c:if test="${x}">aaa</c:if>`. Inside the macro function, we can parse this XML text and construct a new Expression object to return.

By leveraging macro functions together with TemplateStringLiteral in XScript, we can easily embed DSLs with different syntaxes into XScript. For example, **provides a SQL query syntax similar to C# LINQ**.

```
let result = linq `select ff from myObject o  where o.value > 3`
```

The Nop platform currently ships with the following macro functions:

|Function Name|Description|
|---|---|
|xml|Parses XML text into an XNode and wraps it as a LiteralExpression|
|xpl|Parses Xpl template text into an Expression|
|sql|Parses Xpl template text into an Expression that generates SQL statements|
|jpath|Parses a JSON path into a JPath object and wraps it as a LiteralExpression|
|xpath|Parses XSelector text into an XSelector object and wraps it as a LiteralExpression|
|selection|Parses GraphQL-like object property selection text into a FieldSelection object and wraps it as a LiteralExpression|
|order\_by|Parses an order by clause snippet into a List<OrderFieldBean> object and wraps it as a LiteralExpression|
|location|Returns the source location of the calling function and wraps it as a LiteralExpression|
|IF|Implements functionality similar to the IF function in Excel formulas|
|SWITCH|Implements functionality similar to the SWITCH function in Excel formulas|

Because macro functions execute at compile time, implementing parsing via macro functions can optimize runtime performance. For example, to read attribute b of child node a from an XNode:

```
  node.selectOne(xpath `a/@b`)
```

Because xpath is a macro function, it completes parsing at compile time, and at runtime it’s equivalent to passing a constant object to the selectOne function.

Macro functions can implement custom syntactic structures, e.g., IF(X,Y,Z) can be transformed into an if statement.

## Xpl Template Language for Code Generation

The Xpl template language is part of XLang. It uses XML format and includes Turing-complete logical constructs such as `<c:if>` and `<c:for>`. The XML-formatted template language can achieve Lisp’s homoiconicity—that is, the format of the code is the same as the format of the generated data.

Common template languages (such as Freemarker or Velocity) are not homoiconic; moreover, they are used for text generation only and do not truly support code generation. To support code generation, the Xpl template language provides multiple output modes:

1. node mode: Outputs XNode nodes. This mode preserves source code location information, i.e., in the final result we can trace which segment of source generated each attribute and node.
2. xml mode: Outputs XML text, automatically escaping attributes and text content.
3. html mode: Outputs XHTML text. Except for a few tags like `<br/>`, most tags are output in full form—i.e., always output `<div></div>` rather than `<div/>`.
4. text mode: Disallows outputting nodes and attributes; only text content is allowed, and XML escaping is not required.
5. xjson mode: Outputs XNode nodes which are automatically converted into JSON objects according to fixed rules.
6. sql mode: Outputs SQL statements; expression results are automatically converted into SQL parameters.

For example, for the following SQL output:

```
<filter:sql>
  o.id in (select o.id from MyTable o where o.id = ${entity.id})
</filter:sql>
```

it will actually generate `o.id in (select o.id from MyTable o where o.id = ? )`; the value of the expression will not be directly concatenated into the SQL text, but will be replaced with an SQL parameter.

## Compile-Time Expressions

The Xpl template language has built-in `<macro:gen>` and `<macro:script>` tags that automatically execute at compile time.

- `<macro:script>` indicates executing an expression at compile time—for example, dynamically parsing an Excel model file at compile time to obtain a model object:

```xml

<macro:script>
    import test.MyModelHelper;

    const myModel = MyModelHelper.loadModel('/nop/test/test.my-model.xlsx');
</macro:script>
```

After obtaining compile-time variables, subsequent expressions can use compile-time expressions to access the object, e.g., `#{myModel.myFunc(3)}`.

- Compile-time expressions use the form `#{expr}`. A compile-time expression is executed immediately when it is compiled; only its return value is retained for runtime.
- Compile-time expressions can be used within regular expressions, e.g., ${ x \> #{MyConstants.MIN\_VALUE} }.
- During compilation, the Xpl template language automatically executes compile-time expressions and optimizes based on the results. For example, `<div xpl:if="#{false}>` allows the compiler to know that xpl:if is false, so this node will be automatically deleted.

The content of `<macro:gen>` is Xpl template syntax: it first compiles the body, then executes the body, collects the output, and finally compiles the generated result. The content of `<macro:script>` is XScript syntax, and it discards its return value.

## Custom Macro Tags

Tags in the Xpl template language’s tag library can define macro tags. Unlike regular tags, the source section of a macro tag is executed immediately after compilation, and the content output during execution is then compiled.

For example, we can define a macro tag `<sql:filter>` that performs the following structural transformation:

```xml

<sql:filter>and o.fld = :param</sql:filter>
        transforms into
<c:if test="${!_.isEmpty(param)}">
and o.fld = ${param}
</c:if>
```

See the specific implementation in the [sql.xlib](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/xlib/sql.xlib) tag library.

```xml

<filter macro="true" outputMode="node">
    <slot name="default" slotType="node"/>

    <source>
        <c:script>
            import io.nop.core.lang.sql.SqlHelper;
            import io.nop.core.lang.sql.SQL;

            const sb = SqlHelper.markNamedParam(slot_default.contentValue);
            const cond = sb.markers.map(marker=> "!_.isEmpty("+marker.name+")").join(" and ");
            const sqlText = sb.renderText(marker =>{
            return "${" + marker.name + "}";
            });
        </c:script>

        <c:if xpl:ignoreTag="true" test="${'$'}{${cond}}">
            ${sqlText}
        </c:if>
    </source>
</filter>
```

The macro tag above performs structural transformation on the node content, generating a `<c:if>` node. The template engine then compiles the output `<c:if>` node, yielding a result equivalent to manually writing the corresponding node.

- Use a slot with `slotType="node"` to read node content directly. When slotType=node, the slot content is not parsed and is passed as an XNode variable.
- `xpl:ignoreTag` indicates that the current node and its children should not be recognized as xpl tags; `<c:if>` is output directly as a normal XML node.
- The expression in `test="${'$'}{$cond}"` is recognized; after executing the expression, it becomes `test="${cond}"`.

**Macro tags are similar to macros in Lisp. They provide a lightweight AST transformation mechanism—a kind of embedded code generator.**

## Compiling to AST

You can obtain the abstract syntax tree (Expression type) corresponding to the content via the `<c:ast>` tag.

```xml

<Validator ignoreUnknownAttrs="true" macro="true">

    <!-- The runtime attribute indicates a variable that exists at runtime. This attribute only applies when the tag is a macro tag. -->
    <attr name="obj" defaultValue="$scope" runtime="true" optional="true"/>

    <!-- slotType=node means pass the content as an XNode to the source section. Without this, the content would be compiled before being passed. -->
    <slot name="default" slotType="node"/>
    <description>
        Use the macro tag mechanism to parse the XNode into a Validator model, and transform it into a call to ModelBasedValidator.
        The source section of a macro tag executes at compile time; its output is what gets compiled.
    </description>
    <source>

        <!-- Parse the tag body at compile time into a ValidatorModel and save it as the compile-time variable validatorModel -->
        <c:script><![CDATA[
                    import io.nop.biz.lib.BizValidatorHelper;

                    let validatorModel = BizValidatorHelper.parseValidator(slot_default);
                    // Obtain the AST corresponding to <c:script>
                    let ast = xpl `
                         <c:ast>
                            <c:script>
                               import io.nop.biz.lib.BizValidatorHelper;
                               if(obj == '$scope') obj = $scope;
                               BizValidatorHelper.runValidatorModel(validatorModel,obj,svcCtx);
                            </c:script>
                         </c:ast>
                     `
                    // Replace the identifier name in the AST with the model object parsed at compile time. This avoids dynamic loading and parsing at runtime.
                    return ast.replaceIdentifier("validatorModel",validatorModel);
                ]]></c:script>
    </source>
</Validator>
```

- The source section of a macro tag executes at compile time. `BizValidatorHelper.parseValidator(slot_default)` means parsing the tag node to obtain a ValidatorModel object (which exists at compile time).
- In the XScript scripting language (syntax similar to TypeScript), XML-formatted Xpl template code can be embedded via the xpl template function.
- `ast = xpl <c:ast>...</c:ast>` means executing the xpl template function; `<c:ast>` indicates obtaining only the AST of its child nodes, not executing their content.
- `ast.replaceIdentifier("validatorModel",validatorModel)` replaces the identifier named validatorModel in the AST with the compile-time variable ValidatorModel. This is effectively a constant replacement: replacing the variable name with the value it represents. Because validatorModel is a model parsed at compile time, there is no need for any dynamic parsing at runtime.
- The source section can return an AST node (Expression type) directly, without necessarily constructing the AST via XNode output (the previous section’s example constructed the AST via output).
- `<attr name="obj" runtime="true">` indicates that the obj attribute is a runtime attribute; in the source section it corresponds to an Expression, rather than its value. Without `runtime=true`, the attribute can be used in the source section, but because the source runs at compile time, the attribute value must be a fixed value or a compile-time expression.

```xml

<biz:Validator obj="${entity}"/>
```

## Delta Generation and Merge Mechanism for XDSL

All DSLs in the Nop platform support the x-extends Delta merge mechanism, through which the computation model required by the Reversible Computation theory is achieved:

> App = Delta x-extends Generator<DSL>

Specifically, all DSLs support `x:gen-extends` and `x:post-extends` configuration sections. These are compile-time Generators that use the XPL template language to dynamically generate model nodes, allowing multiple nodes to be generated at once and then merged in sequence. The merge order is defined as follows:

```
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

The merge result is:

```
F x-extends E x-extends model x-extends D x-extends C x-extends B x-extends A
```

The current model overrides the results of `x:gen-extends` and `x:extends`, while `x:post-extends` overrides the current model.

With `x:extends` and `x:gen-extends`, we can effectively decompose and compose DSLs. See [XDSL: A General-Purpose Domain-Specific Language Design](https://zhuanlan.zhihu.com/p/612512300) for details.

## Data-Driven Delta-Based Code Generator

To realize the software construction pattern required by the Reversible Computation theory at the system level, the Nop platform provides a data-driven Delta-based code generator, XCodeGenerator.

Typical code generators are customized for specific purposes. For example, a common MyBatis code generator has its control logic implemented in a dedicated CodeGenerator class that reads templates, constructs output file paths, initializes context model variables, and executes loops. If you want to adjust generation details, you usually have to modify this CodeGenerator class.

XCodeGenerator takes a different approach. It treats the template path as a micro-formatted DSL, encoding conditions and loop logic in the path format, so the template’s organization controls the generation process. For example:

```
/src/{package.name}/{model.webEnabled}{model.name}Controller.java.xgen
```

This pattern indicates iterating over each model under a package and generating a Controller.java class for each Model whose webEnabled attribute is true.

Based on this design, simply adjusting the directory structure of template files lets you control the target code directory structure and generation timing.

See [Data-Driven Delta-Based Code Generator](https://zhuanlan.zhihu.com/p/540022264) for details.

XCodeGenerator can be integrated with the Maven build tool to run code generation before and after Java compilation, acting similarly to Java’s annotation processor (APT) technology—but it is much simpler and more intuitive to use.

See [How to Integrate Nop Platform’s Code Generator](https://zhuanlan.zhihu.com/p/613448320) for integration details.

<!-- SOURCE_MD5:376c14f9cf59021c9ea0eb5f04650359-->
