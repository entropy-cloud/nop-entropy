# XPL

XPL is a template language that uses XML syntax, supports meta-programming, supports multiple output modes, and tightly integrates with the EL expression (XScript scripting language). Through custom tags, XPL can introduce new syntax structures while enabling seamless nesting of various Domain-Specific Languages (DSLs).

## Built-in Tags

The built-in tags in XPL provide functionality for conditional statements, loops, imports, macros, and more basic operations.

### Control Logic

* `<c:if>`  
  Conditional statement.

```xml
<c:if test="${cond}">
  When the condition is true, this block executes.
</c:if>
```

* `<c:for>`  
  Looping statement.  
  `index` corresponds to the loop index, `var` corresponds to the variable in the loop.

```xml
<c:for items="${list}" var="item" index="index">
</c:for>

<c:for begin="0" end="2" var="index" index="index">
</c:for>
```

* `<c:while>`  
  While loop statement.

```xml
<c:while test="${cond}">
</c:while>
```

* `<c:break>`  
  Break out of the loop statement.

* `<c:continue>`  
  Similar to Java's `continue` statement, skip the current iteration.

* `<c:return>`  
  Return from a custom tag. The `value` attribute can be used to specify the return value.

```xml
<c:return value="${result}" />
```

* `<c:choose>`  
  Multiple choice statement.

```xml
<c:choose>
  <when test="${cond}">
    When the condition is true.
  </when>
  <otherwise>
    When none of the conditions are met.
  </otherwise>
</c:choose>
```

* `<c:throw>`  

```xml
<c:throw errorCode="xxx.yyy.zz" params="${{a:1,b:2}}" />
```

* `<c:try>`  

```xml
<c:try>
  <body>
    Code to be executed.
  </body>

  <catch>
    exception occurs here, corresponding to the $excetion variable in context.
  </catch>

  <finally>
  </finally>
</c:try>
```

### Output

* `<c:collect>`  

* `<c:print>`  

* `<c:out>`  

### Scripting Language

* `<c:script>`  
  Embedded scripting language. The `lang` attribute can be used to specify different script engines, such as Groovy, with a default of xlang.

```xml
<c:script lang="groovy">
  This is the code.
</c:script>
```

The XLang platform includes support for `lang=java`, allowing scripts to access variables from the context by specifying them in the `args` attribute within `<c:script>` tags.

```xml
<c:unit>
  <c:script>
    let x = 1;
    let y = 2;
  </c:script>

  <c:script lang="java" args="x:int,y:int" returnType="int">
    return x + y;
  </c:script>
</c:unit>
```

Additional script engines can be registered using the ScriptCompilerRegistry. The interface for script compilation is as follows:

```java
public interface IScriptCompiler {
    IEvalFunction compile(SourceLocation loc, String text,
                          List<? extends IFunctionArgument> args, IGenericType returnType,
                          IXLangCompileScope scope);
}
```

### Compilation Phase

* `<c:import>`  
  Import constants or taglibs.

```xml
<c:import from="a/b.xlib" />

<c:import class="a.b.c.MyConstants" />
```

When importing taglibs, the namespace is determined by the taglib filename, such as `a.xlib => a` or `a!ext.xlib => a`. You can also explicitly specify the namespace, for example:
```xml
<c:import from="a/b.xlib" xmlns:a="http://example.com/ns/a" />
```

```xml
<c:import from="xxx.xlib" as="yyy"/>
<yyy:MyTag/>
```

* `<c:include>`

* `<macro:script>`
  Compile specified content, and directly execute

* `<macro:gen>`
  Macro tag will be executed at compile time.

```xml
<macro:gen xpl:dump="true">
  Herein content is first compiled as Expression, then immediately executed in compile time.
  The XNode output by xpl will be compiled again
</macro:gen>
```

### Other

* `<c:unit>`
  A tag that only serves grouping function. Its direct compilation result is empty.

```xml
<c:unit>
  <div/>
  <input/>
</c:unit>

<!-- Equivalent to -->
<div/>
<input/>
```

* `<c:log>`
  Only when `io.nop.xpl.logger` corresponding log level is opened, the log message will be concatenated and printed.

```xml
<c:log info="xxx ${myvar} ${myObj.func(3)} sss" />
```

## Custom Tags

## Custom Tag

### Conditional Tags

When the `conditionTag` attribute of a custom tag is set to `true`, it becomes a conditional tag.

```xml
<!-- Tag definition -->
<WhenAdmin conditionTag="true">
  <source>
    <c:script>
      $userContext.isUserInRole('admin')
    </c:script>
  </source>
</WhenAdmin>

<!-- As a conditional container -->
<biz:WhenAdmin>
  When the user has admin role, this content is executed
</biz:WhenAdmin>
```

Conditional tags can directly serve as `c:choose` branches.

```xml
<c:choose>
  <biz:WhenAdmin>
    </biz:WhenAdmin>

  <c:otherwise>
    </c:otherwise>
  </c:otherwise>
</c:choose>
```

### Compile-time Conversion
Custom tags support `transform` configuration. They can perform conversion on tag nodes at compile time. For example, in `bo.xlib`.

```xml
<Get>
  <attr name="id"/>
  <attr name="ignoreUnknown" optional="true"/>
  <attr name="selection" optional="true" type="io.nop.api.core.beans.FieldSelectionBean"/>
  <attr name="bizObjName" optional="true"/>
  <attr name="thisObj" implicit="true"/>
  <attr name="svcCtx" implicit="true"/>

  <transform>
    <bo-gen:TransformBizObjTag xpl:lib="bo-gen.xlib"/>
  </transform>
  ...
</Get>
```

`<bo-gen:TransformBizObjTag>` tag is executed at compile time. It identifies the `bizObjName` attribute and automatically generates the `thisObj` property expression. For example, calling this tag with:

```xml
<bo:Get bizObjName="NopAuthUser" selection="items{roleMappings}" id="3" />
```

After transformation by `transform`, it will be converted into:

```xml
<bo:Get thisObj="${inject('nopBizObjectManager').getBizObject('NopAuthUser')}" selection="${selection('items{roleMappings}')}" />
```

## Dynamic Attributes

* If the attribute value returns `null`, then the attribute will be automatically ignored in output.

```xml
<input class="${null}" />

<!-- Actual output -->
<input />
```

* `<xpl:attrs>` can specify a Map to batch output dynamic attributes. If the attribute value is `null`, it won't be output.

```xml
<xpl:attrs>
  <attr name="class" value="${null}"/>
</xpl:attrs>
```


```xml
  <input xpl:attrs="{name:'a',class:null}" />

  <!-- Actual Output -->
  <input name="a" />
```

> **Note:** If a node already has an attribute, the `xpl:attrs` specified attribute will be ignored.

---

```xml
  <input name="x" xpl:attrs="{name:'b'}" />

  <!-- Actual Output -->
  <input name="x" />
```

---


## Output Modes

The `xpl` tags support both return values and outputs, with multiple modes available:

- **`none`**: Prevents output of text content
- **`html`**: Outputs HTML text, automatically wrapping tags like `<script>` and `<div>` in their closing forms
- **`xml`**: Outputs XML text. If `xpl:allowUnknownTag` is set to `true`, it also outputs unrecognized tags with namespaces
- **`text`**: Prevents output of XML tags but allows text content, without performing XML escaping
- **`node`**: Converts all output into `XNode` objects

1. During library definition, the default output mode can be set using the root node's `defaultOutputMode`
2. The `outputMode` can also be specified per custom tag during its definition
3. When calling a tag, `xpl:outputMode` can be used to set the output mode for unrecognized tags. If a custom tag already has its own output mode, specifying it again when calling will have no effect.

---


## thisLib

In a custom tag library, `thisLib` is used to reference the current library. For example:

- In `web.xlib`, `<thisLib:LoadPage>` corresponds to `<web:LoadPage xpl:lib="/nop/web/xlib/web.xlib">`

The introduction of `thisLib` allows modifying the namespace when referencing external libraries, for example:

```xml
<c:import from="/nop/web/xlib/web.xlib" as="myweb" />
<myweb:GenPage page="xx" />
```

---


## Slot Mechanism

The slot mechanism in `xpl` tags is similar to Vue components' slot system.


### 1. Defining Slots in the Library

```xml
<!-- /test/my-ext.xlib -->
<lib x:schema="/nop/schema/xlib.xdef" xmlns:x="/nop/schema/xdsl.xdef">
  <tags>
    <MyTagExt outputMode="xml">
      <!-- Declare a slot named "ext" -->
      <slot name="ext">
        <arg name="x" />
      </slot>

      <source>
        <c:unit xpl:slot="ext" xpl:slotArgs="{x:3}" />
      </source>
    </MyTagExt>
  </tags>
</lib>
```

- In the implementation, `xpl:slot` is used to reference externally passed slots, and `xpl:slotArgs` passes parameters to the slot. The exact parameters depend on how slots are defined.
- Unlike Vue's slot, `xpl:slot` can be applied to any tag, not just `<c:unit>`. If no slot is provided, it will fall back to the current tag.


### 2. Specifying Slots When Calling

```xml
<my-ext:MyTagExt xpl:lib="/test/my-ext.xlib">
  <ext xpl:slotScope="x">
    ${x}
  </ext>
</my-ext:MyTagExt>
```

- When calling a tag, `xpl:slotScope` can be used to introduce `xpl:slotArgs` parameters. This allows leveraging JavaScript destructuring for variable renaming.
- If `xpl:slotScope` is not specified, `xpl:slotArgs` will still be accessible but without the scope context.

Here is the translated English version of the provided Chinese technical document, maintaining the original Markdown format including headers, lists, and code blocks:

---

# Slot Configuration and Usage

Slots are a fundamental concept in the system, allowing for flexible configuration and parameterization of components. Below is a detailed explanation of how slots work and how they can be utilized.

## Slot Definition

1. **Slot Compilation**: A slot is compiled into a function `slot_{name}`, where `{name}` represents the slot's identifier.
2. **Slot Arguments**: The arguments passed to this function are represented by `xpl:slotArgs`, which must be an object (Map).
3. **Slot Scope**: The `xpl:slotScope` attribute defines the scope within which these arguments are accessible, effectively declaring a parameter list for the slot.

In simpler terms:
- A slot is compiled into a function.
- Arguments (`xpl:slotArgs`) are passed to this function.
- The scope (`xpl:slotScope`) determines where these parameters are available.

---

## Parameter Configuration

### 1. Default Slot Parameters
- **Slot Compilation**: By default, slots are compiled into functions that accept parameters specified in their scope.
- **Parameter Renaming**: These parameters can be renamed using the `xpl:slotArgs` attribute.

### 2. Custom Slot Parameters
- **Explicit Parameter Inclusion**: Use `xpl:slotScope` to explicitly include parameters in the slot's function signature.
- **Renaming Flexibility**: This allows for parameter renaming, making the slot's interface more flexible.

### 3. Default Parameter Type
- **Default Behavior**: If no type is specified, slots are compiled as functions expecting a single parameter (`xpl:slotType` defaults to `renderer`).
- **Output Customization**: The output mode can be customized using the `outputMode` attribute in the slot's configuration.

---

## Example Usage

### Example 1: Basic Slot Configuration
```xml
<Validator macro="true">
    <slot name="default" slotType="node"/>
</Validator>
```
- In this example, a slot named "default" is created.
- The `slotType` attribute specifies that the slot will handle node-type data.

### Example 2: Detailed Slot Configuration
```xml
<biz:Validator xpl:lib="/nop/core/xlib/biz.xlib" fatalSeverity="100">
    <check id="checkTransferCode" errorCode="test.not-transfer-code"
           errorDescription="扫入的码不是流转码">
        <eq name="flowMode" value="1"/>
    </check>
</biz:Validator>
```
- Here, the `biz:Validator` tag is used with a macro attribute set to "true".
- The slot named "checkTransferCode" processes specific error codes and descriptions.
- The `<eq>` tag within the check slot verifies whether the flow mode is set to 1.

---

## XPL Attributes Reference

### 1. Core XPL Attributes
The following attributes are essential in XPL configuration:
- `xpl:enableNs` / `xpl:disableNs`
- `xpl:attrs`
- `xpl:frame`
- `xpl:decorator`
- `xpl:lib`
- `xpl:outputMode`
- `xpl:is`
- `xpl:invert`
- `xpl:return`
- `xpl:if`

### 2. Slot-Specific Attributes
For slots, the following attributes are particularly important:
- `xpl:slotArgs`
- `xpl:slotScope`
- `xpl:slotType`
- `xpl:outputMode`

---

## Example Macro Usage

```xml
<Validator macro="true" xpl:lib="/nop/core/xlib/validator.xlib">
    <source>
        <c:script><![CDATA[
            import io.nop.xlang.filter.BizValidatorHelper;

            let validatorModel = BizValidatorHelper.parseValidator(slot_default);
            // ... additional logic ...
        ]]></c:script>
    </source>
</Validator>
```
- In this example, the `macro` attribute is set to "true", indicating that the Validator tag will act as a macro.
- The `<source>` block contains embedded code that will be processed during compilation.

---

# Summary

Slots provide a flexible and powerful way to configure and parameterize components within the system. By leveraging attributes like `xpl:slotArgs`, `xpl:slotScope`, and `xpl:slotType`, developers can create highly customizable and reusable components.

- `xpl:disableNs` Ignore specified namespace, do not treat them as part of the XPL library.
- `xpl:enableNs` Undo `xpl:ignoreNs`, restore recognition of specified namespaces.

```xml
<!-- c:if tag will not be resolved, but treated as text -->
<c:if test="${x}" xpl:ignoreNs="*">
  <!-- Child nodes also will not be resolved -->
  <my:MyTag>
    <!-- Use xpl:ignoreTag=false to restore tag resolution -->
    <c:if test="${xx}" xpl:checkNs="*">
      ...
    </c:if>
  </my:MyTag>
</c:if>
```

2. Expression Control
- `xpl:ignoreExpr` Whether to recognize expressions. If set to `true`, EL expressions in custom tags will be treated as text.

```xml
<div xpl:ignoreExpr="true">
  ${This expression will not be resolved}
</div>
```

3. Tag Identity
- `xpl:is` Can modify the identity of XPL tags.

```xml
<div xpl:is="my:MyTag">
</div>

<!-- Equivalent to -->
<my:MyTag>
</my:MyTag>
```

4. Conditional Control
- `xpl:if` Controls tag execution, similar to `c:if`.

```xml
<div xpl:if="selectors.contains('a')">
</div>

<!-- If using compile-time expressions, XPL engine will optimize automatically -->
<div xpl:if="#{myVar}">
</div>
```

5. Skip Control
- `xpl:skipIf` If set to `true`, skip the current tag and process its content (body).

```xml
<my:MyTag xpl:skipIf="true">
<body/>
</my:MyTag>

<!-- Equivalent to -->
<body/>
```

6. Unknown Tag Handling
- `xpl:allowUnknownTag` Whether to allow unrecognized tags with namespaces. Default is `false`, throwing an error if a namespace tag cannot be identified as a custom tag.

7. Output Mode
- `xpl:outputMode` Set the output mode of the XPL compiler. Note that during `c:macro` and `x:extends`, the default is `xpl:outputMode=node`.

8. Library Control
- `xpl:lib` Import libraries in the local scope. Once out of scope, imported libraries become unavailable.

```xml
<my:MyTag xpl:lib="my.xlib" />

<!-- xpl:lib imports only affect current nodes -->
<xpl:decorator>
  <my:MyTag xpl:lib="my.xlib" />
</xpl:decorator>
```

9. Return Value Handling
- `xpl:return` After tag execution, set the result to a specified variable.

```xml
<my:MyTag xpl:return="x">
  <!-- This tag's execution result is saved to variable x -->
  <let x="#[my:MyTag()]"/>
</my:MyTag>
```

10. Inversion Control
- `xpl:invert` For return values of type `boolean`, `xpl:invert` indicates whether to invert the result.

```xml
<biz:WhenAdmin>
  When has admin role, execute this content
</biz:WhenAdmin>

<biz:WhenAdmin xpl:invert="true">
  When does not have admin role, execute this content
</biz:WhenAdmin>
```

All properties of XPL are fully defined in the [xpl.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/xpl.xdef) file.


## Decorator

Any tag supports a decorator sub-node `<xpl:decorator>`, which can transform nested structures into linear ones.

```xml
<div>
 <xpl:decorator>
   <test:MyTag a="1" />
   <test:MyTag2>
      <xpl:decorated/>
      <child/>
   </test:MyTag2>
 </xpl:decorator>

 content
</div>

<!-- Equivalent -->
<test:MyTag a="1">
  <test:MyTag2>
     <div >
      content
     </div>
     <child/>
  </test:MyTag2>
</test:MyTag>
```

## Xpl Template Language Meaning

xpl template language is the cornerstone of the XLang family. It surpasses all open-source template languages with its design and has filled many syntactic features for meta-programming.

In XScript scripting language, you can embed xpl template language in the following way:

```javascript
let result = xpl `<c:if></c:if>`;
```

Similarly, in xpl template language, you can embed scripting language using `<c:script>`. It also supports embedding various DSLs through custom tags.

When the output mode is set to "xjson," xpl template language automatically converts XML output into JSON objects. This allows us to use XML format to develop AMIS pages.

```xml
<form name="a">
    <body>
        <crud name="list">
            <api url="xxx" />
        </crud>
    </body>
</form>
```

The boundaries of logical structures can be naturally resolved in xpl template language, which is the most crucial part.

## Code Generation Characteristics
XLang is built with some special namespaces, such as "x" and "xpl," which are processed during compilation. If you want to generate an attribute like `x:abstract`, you can use the prefix `xgen-`. For example:

```xml
<form id="query" xgen-x:abstract="true" />
```

In the actual output, the `xgen-` prefix will be removed, resulting in:

```xml
<form id="query" x:abstract="true" />
```

## Calling xpl in Java

### Using xpl Tags
Refer to `DdlSqlCreator.java`.

```javascript
public String dropUniqueKey(IEntityModel table, OrmUniqueKeyModel uniqueKey) {
    Map<String, Object> args = new HashMap<>();
    args.put("table", table);
    args.put("uniqueKey", uniqueKey);
    args.put("dialect", dialect);
    return XLang.getTagAction(dmlLibPath, "DropUniqueKey").generateText(XLang.newEvalScope(args));
}
```

XLang's `TagAction` method takes the tag library path and tag name as parameters to retrieve xpl tags. It then passes them through `IEvalScope`.

### Available Functions
- `generateText()`: For outputting text.
- `invoke()`: For executing actions.

## Common Issues

### 1. Outputting `${a}` as Text Instead of Evaluating the Expression

```xml
<control value="${'$'}{a}" />
```

### 2. Outputting `<a:b/>` with a Namespace

```xml
<c:unit xpl:is="${'a:b'+''}" />
```

> Directly writing `${'a:b'}` or `a:b` will be treated as tags and only evaluated dynamically if used in a dynamic expression.

If `xpl:allowUnknownTag` is set, it will automatically ignore all unknown tags:

```xml
<a:b xpl:allowUnknownTag="true" />
```

If `xpl:ignoreTag` is set to "true," it will ignore all tags, not just unknown ones:
```xml
<xpl:ignoreTag="true"><a:b/></xpl:ignoreTag>
```

