
# XPL

XPL is a template language that uses an XML syntax, supports metaprogramming and multiple output modes, and integrates closely with EL expressions (the XScript scripting language). By defining custom tags, XPL can introduce new syntactic constructs and achieve seamless nesting of multiple DSLs.

## Built-in Tags

The built-in tags in XPL provide basic capabilities such as conditionals, loops, import, and macro processing.

### Control Flow

* `<c:if>`
  Conditional statement.

```xml
<c:if test="${cond}">
  Executed when the condition is true.
</c:if>
```

* `<c:for>`
  Loop statement.
  `index` corresponds to the loop index, `var` corresponds to the variable name during iteration.

```xml
<c:for items="${list}" var="item" index="index">
</c:for>

<c:for begin="0" end="2" var="index" index="index">
</c:for>
```

* `<c:while>`
  while loop statement.

```xml
<c:while test="${cond}">
</c:while>
```

* `<c:break>`
  Break out of the loop.

* `<c:continue>`
  Similar to Java’s `continue` statement; skip the current iteration.

* `<c:return>`
  Return from a custom tag; you can use the `value` attribute to specify the return value.

```xml
  <c:return value="${result}" />
```

* `<c:choose>`
  Multi-branch selection statement.

```xml
 <c:choose>
   <when test="${cond}">
      Executed when the condition is true
   </when>
   <otherwise>
      Executed when none of the other conditions are met
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
  Code to be executed
  </body>

  <catch>
     Catch the exception; $excetion in the context corresponds to the exception object
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
  Embed a scripting language; use the `lang` attribute to specify different script engines such as `groovy`; the default is `xlang`.

```xml
<c:script lang="groovy">
  Code
</c:script>
```

The XLang platform has built-in support for `lang=java`. Use args to specify which variables to fetch from the context; those variables can be accessed directly in the script code.

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

You can register more script engine implementations via ScriptCompilerRegistry. The script engine interface is as follows:
```java
public interface IScriptCompiler {
    IEvalFunction compile(SourceLocation loc, String text,
                          List<? extends IFunctionArgument> args, IGenericType returnType,
                          IXLangCompileScope scope);
}
```

### Compile-time

* `<c:import>`
  Import a constants class or a tag library

```xml
<c:import from="a/b.xlib" />

<c:import class="a.b.c.MyConstants" />
```

When a tag library is imported, the default namespace is inferred from the tag library filename, e.g., `a.xlib => a`, `a!ext.xlib => a`.
You can also explicitly specify the namespace, e.g.,

```xml

<c:import from="xxx.xlib" as="yyy"/>
<yyy:MyTag/>
```

* `<c:include>`

* `<macro:script>`
  Compile the specified content and run it immediately

* `<macro:gen>`
  Macro tags are executed at compile time.

```xml
  <macro:gen xpl:dump="true" >
    The content here is first compiled into an Expression, then this xpl is executed immediately at compile time.
    The XNode output by xpl will be compiled again
  </macro:gen>
```

### Others

* `<c:unit>`
  A virtual tag that serves only for grouping. Its direct compilation result is empty.

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
  Only when the corresponding log level of `io.nop.xpl.logger` is enabled will the log message be assembled and printed.

```xml
  <c:log info="xxx ${myvar} ${myObj.func(3)} sss" />
```

## Custom Tags

### Conditional Tags

A custom tag is a conditional tag when `conditionTag` is set to `true`.

```xml
  <!-- Tag definition -->
  <WhenAdmin conditionTag="true">
    <source>
      <c:script>
        $userContext.isUserInRole('admin')
      </c:script>
    </source>
  </WhenAdmin>

  <!-- Use as a conditional container -->
  <biz:WhenAdmin>
    Execute the content here when the user has the admin role
  </biz:WhenAdmin>
```

A conditional tag can be used directly as a branch in `c:choose`.

```xml
  <c:choose>
    <biz:WhenAdmin>
    </biz:WhenAdmin>

    <c:otherwise>

    </c:otherwise>
  </c:choose>
```

### Compile-time Transformation
Custom tags support a transform configuration, which can transform tag nodes at compile time. For example, in `bo.xlib`

```xml
<Get>
  <attr name="id"/>
  <attr name="ignoreUnknown" optional="true"/>
  <attr name="selection" optional="true" type="io.nop.api.core.beans.FieldSelectionBean"/>
  <attr name="bizObjName" optional="true" />
  <attr name="thisObj" implicit="true"/>
  <attr name="svcCtx" implicit="true"/>

  <transform>
    <bo-gen:TransformBizObjTag xpl:lib="bo-gen.xlib"/>
  </transform>
  ...
</Get>
```

The `<bo-gen:TransformBizObjTag>` tag is executed at compile time; it recognizes the `bizObjName` attribute and automatically generates the expression for the `thisObj` attribute. For example, for the following tag invocation

```xml
<bo:Get bizObjName="NopAuthUser" selection="items{roleMappings}" id="3" />
```

After being processed by the transform section, it is automatically converted to the following tag node for compilation

```xml
<bo:Get thisObj="${inject('nopBizObjectManager').getBizObject('NopAuthUser')}" selection="${selection('items{roleMappings}')}" />
```

## Dynamic Attributes

* If an attribute's value evaluates to `null`, the attribute will be automatically omitted from the output.

```xml
  <input class="${null}" />

  <!-- Actual output -->
  <input />
```

* `xpl:attrs` can specify a Map to output a batch of dynamic attributes. If an attribute value is `null`, it is still omitted.

```xml
  <input xpl:attrs="{name:'a',class:null}" />

  <!-- Actual output -->
  <input name="a" />
```

Additionally, note that if the node already has an attribute, the attribute specified by `xpl:attrs` will be ignored.

```xml
  <input name="x" xpl:attrs="{name:'b'}" />

  <!-- Actual output  -->
  <input name="x" />
```

## Output Modes

An xpl tag has both a return value and output. Output supports multiple modes

* `none` No text output allowed
* `html` Output HTML text; automatically recognizes `<script>`, `<div>`, etc., that require full closing form
* `xml` Output XML text; if `xpl:allowUnknownTag` is `true`, all unrecognized namespaced tags will also be output
* `text` XML tags cannot be output, but textual content can be output, and the text will not be XML-escaped
* `node` All output content is automatically converted to `XNode`.

1. When defining a tag library, you can set the default output mode for tags in this library by marking `defaultOutputMode` on the root node.
2. You can also specify `outputMode` when defining each custom tag.
3. When invoking tags, you can use `xpl:outputMode` to set the output mode for unrecognized tags. If a custom tag already has its own output mode, setting it again from the outside has no effect.

## thisLib

In a custom tag library, you can use `thisLib` to refer to the current tag library.
For example, in `web.xlib`, `<thisLib:LoadPage>` corresponds to `<web:LoadPage xpl:lib="/nop/web/xlib/web.xlib">`

The reason for introducing the special namespace `thisLib` is that when a tag library is referenced externally, its namespace used at call time may be modified via `as`. For example

```xml
<c:import from="/nop/web/xlib/web.xlib" as="myweb" />
<myweb:GenPage page="xx" />
```

## Slot Mechanism

The slot mechanism of xpl template tags is similar to the slot mechanism in Vue components.

### 1. Declare slots when defining tags in a tag library

```xml
<!-- /test/my-ext.xlib -->
<lib x:schema="/nop/schema/xlib.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <tags>
        <MyTagExt outputMode="xml">
            <!-- Declare a slot named ext -->
            <slot name="ext" >
                <arg name="x" />
            </slot>

            <source>
                <c:unit xpl:slot="ext" xpl:slotArgs="{x:3}" />
            </source>
        </MyTagExt>
    </tags>
</lib>
```

* In the tag implementation, use the `xpl:slot` attribute to reference the slot implementation provided by the caller, and use `xpl:slotArgs` to pass parameters to the slot. Which parameters can be passed must be declared when the slot is defined.
* `xpl:slot` can be placed on any tag; it is not limited to `c:unit`. Similar to Vue’s slots, when the slot specified by `xpl:slot` does not exist, the tag continues to execute; otherwise, the slot replaces the tag. In other words, the tag bearing `xpl:slot` effectively provides a default value for the slot.

### 2. Specify slots when invoking a tag

```xml
<my-ext:MyTagExt xpl:lib="/test/my-ext.xlib">
  <ext xpl:slotScope="x">
     <x>${x}</x>
  </ext>
</my-ext:MyTagExt>
```

* When invoking the tag, use `xpl:slotScope` to import the parameters passed via `xpl:slotArgs`; you can leverage JavaScript destructuring syntax to rename variables at this point.
* If `xpl:slotScope` is omitted, you can still access the parameters passed via `xpl:slotArgs`.
* A slot is compiled into a function `slot_{name}`; `xpl:slotArgs` are the arguments passed to this function and must be a Map. The `xpl:slotScope` specified when invoking the tag is equivalent to declaring the parameter list of this function, where you can rename the parameters.

In other words:

1. A slot injects the caller’s body content into the implementer’s `xpl:slot` location
2. `slotArgs` are parameters passed from the implementer to the caller; they can be used in the caller’s body
3. The caller can use `xpl:slotScope` to explicitly import the implementer’s parameters and optionally rename them

### 3. Specify slotType=node

By default, a slot’s type is renderer; it is compiled into a function whose output mode can be customized by specifying `outputMode` on the slot. Besides compiling to a function, you can set `slotType=node`; in this case the slot is passed directly as an `XNode` without being converted to a function. You cannot invoke such a slot via `xpl:slot="slotName"`.

In general, `renderType=node` is used to treat the incoming node as a model object or metadata; combined with compile-time macro tags, it enables seamless nesting of different DSLs.

For example, the `Validator` tag in `biz.xlib`

```xml
<Validator ignoreUnknownAttrs="true" macro="true">
    <!--
    The runtime flag indicates variables that exist at runtime. This attribute only takes effect when the tag is a macro tag
    -->
    <attr name="obj" defaultValue="$scope" runtime="true" optional="true"/>

    <!-- slotType=node means the XNode node content is passed into the source section as is. If this attribute is not set, it is passed after compilation -->
    <slot name="default" slotType="node"/>

    <source>
        <!-- At compile time, parse the tag body to obtain a ValidatorModel and save it as the compile-time variable validatorModel -->
        <c:script><![CDATA[
           import io.nop.xlang.filter.BizValidatorHelper;

            let validatorModel = BizValidatorHelper.parseValidator(slot_default);
            ...return the compiled Expression object
        ]]></c:script>
    </source>
</Validator>
```

* The slot with `name=default` and `slotType=node` means that at invocation time, the entire tag body is passed as an `XNode` object named `slot_default`.
* `macro=true` on the `Validator` tag indicates it is a macro tag. Its `source` section runs at compile time, producing an Expression object, which is then compiled.
  A macro tag is essentially an embedded code generator.

When invoked

```xml
<biz:Validator xpl:lib="/nop/core/xlib/biz.xlib" fatalSeverity="100"
               obj="${entity}">

    <check id="checkTransferCode" errorCode="test.not-transfer-code"
           errorDescription="The scanned code is not a transfer code">
        <eq name="flowMode" value="1"/>
    </check>
</biz:Validator>
```

Here, the content nested inside `<biz:Validator>` is actually a validation-specific DSL defined by the `validator.xdef` meta-model.

## xpl-specific Attributes

XPL provides a set of common attributes that all tags can specify. The processing order for xpl attributes is
`xpl:enableNs` -->  `xpl:disableNs` --> `xpl:attrs` -> `xpl:frame` -> `xpl:decorator`
--> `xpl:lib` --> `xpl:outputMode` --> `xpl:is` --> `xpl:invert` --> `xpl:return`
--> `xpl:if`

1. `xpl:disableNs`/`xpl:enableNs`
   `xpl:disableNs` ignores specified namespaces and does not treat them as tags from xpl tag libraries.
   `xpl:enableNs` cancels the effect of `xpl:ignoreNs`, restoring recognition of the tag libraries corresponding to the specified namespaces.

```xml
  <!-- The c:if tag will not be parsed; it is directly output as text -->
  <c:if test="${x}" xpl:ignoreNs="*">
     <!-- Child nodes will also not be parsed-->
     <my:MyTag>
         <!--  Restore tag parsing via xpl:ignoreTag=false -->
         <c:if test="${xx}" xpl:checkNs="*">
            ...
         </c:if>
     </my:MyTag>
  </c:if>
```

2. `xpl:ignoreExpr`
   Whether to recognize expressions. If set to `true`, EL expressions used in non-custom tags will be output directly as text.

```xml
<div xpl:ignoreExpr="true">
  ${This expression will not be parsed}
</div>
```

3. `xpl:is`
   You can change the recognized xpl tag name.

```xml
<div xpl:is="my:MyTag">
</div>

<!-- Equivalent to -->
<my:MyTag>
</my:MyTag>
```

4. `xpl:if`
   Control whether the tag runs; equivalent to a simplified `c:if` call.

```xml
  <div xpl:if="selectors.contains('a')">
  </div>

  <!-- If a compile-time expression is used, the xpl engine will automatically optimize internally -->
  <div xpl:if="#{myVar}">
  </div>
```

5. `xpl:skipIf`
   If `true`, skip the current tag layer and compile the tag’s `body` directly. This effectively controls skipping a nesting level.

```xml
  <my:MyTag xpl:skipIf="true">
     <body/>
  </my:MyTag>

  <!-- Equivalent to -->
  <body/>
```

6. `xpl:allowUnknownTag`
   Whether to allow unrecognized namespaced tags. Default is `false`; if a namespaced tag cannot be recognized as a custom tag, an exception is thrown.

7. `xpl:outputMode`
   Set the xpl compiler’s output mode. Note that during `c:macro` and `x:extends` execution, `xpl:outputMode=node` is set by default.

8. `xpl:lib`
   Introduce a tag library within a local scope. Once outside the tag’s scope, the imported tag library is not visible.

```xml
 <my:MyTag xpl:lib="my.xlib" />

 <!-- The tag library introduced by xpl:lib is only effective for the current node; it is not visible here, so compilation will fail -->
 <my:MyTag />
```

9. `xpl:return`
   After executing the tag, set the return value to the specified variable.

```xml
  <my:MyTag  xpl:return="x">
    The execution result of this tag is saved in variable x, equivalent to let x = #[my:MyTag()]
  </my:MyTag>
```

10. `xpl:invert`
    For custom tags that return `boolean` values, `xpl:invert` negates the return value.

```xml
  <biz:WhenAdmin>
    Execute the content here when the admin role is present
  </biz:WhenAdmin>

  <biz:WhenAdmin xpl:invert="true">
     Execute the content here when [not] possessing the admin role
  </biz:WhenAdmin>
```

All built-in attributes of the Xpl language are fully defined in the [xpl.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xpl.xdef) file.

## Decorator

Any tag supports a decorating child node named `<xpl:decorator>`; it can transform a nested structure into a linear one.

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

<!-- Equivalent to -->
<test:MyTag a="1">
  <test:MyTag2>
     <div >
      content
     </div>
     <child/>
  </test:MyTag2>
</test:MyTag>
```

## Significance of the Xpl Template Language

The xpl template language is a key member of the XLang language family. Its design surpasses all current open-source template languages, notably adding extensive syntactic features for metaprogramming. In the XScript scripting language, you can embed xpl like this

```js
let result = xpl `<c:if></c:if>`
```

This embeds the xpl template language; conversely, in the Xpl template language you can embed scripting languages via `<c:script>`, and you can embed DSLs with different semantics and forms through various custom tags.

The Xpl template language can set different output modes; when the output mode is xjson, XML output can be automatically converted to JSON objects, so we can author AMIS pages in XML format

```xml
<form name="a">
    <body>
        <crud name="list">
            <api url="xxx" />
        </crud>
    </body>
</form>
```

In the Xpl template language, the formal boundaries between various logical structures can be naturally dissolved; this is the most crucial part.

## Code Generation Features
XLang has some special namespaces built in, such as x and xpl, which are handled at compile time. If you need to generate attributes like `x:abstract` as-is, you can use the `xgen-` prefix, for example

```xml
<form id="query" xgen-x:abstract="true" />
```

The `xgen-` prefix will be removed in the actual output, ultimately producing

```xml
<form id="query" x:abstract="true" />
```

## Calling xpl from Java

### Invoke xpl Tags
See DdlSqlCreator.java

```javascript
    public String dropUniqueKey(IEntityModel table, OrmUniqueKeyModel uniqueKey) {
        Map<String, Object> args = new HashMap<>();
        args.put("table", table);
        args.put("uniqueKey", uniqueKey);
        args.put("dialect", dialect);
        return XLang.getTagAction(dmlLibPath, "DropUniqueKey").generateText(XLang.newEvalScope(args));
    }
```

XLang.getTagAction can obtain an xpl tag by the tag library path and tag name, then pass tag arguments via IEvalScope. TagAction provides multiple functions such as generateText and invoke, used respectively for producing text output and returning execution values as function calls.

## FAQ

### 1. How do I output the expression `${a}` rather than evaluate it?

```xml
<control value="${'$'}{a}" />
```

### 2. How do I output a namespaced node, e.g., `<a:b/>`

```xml
<c:unit xpl:is="${'a:b'+''}" />
```

> Writing `${'a:b'}` or `a:b` directly will be parsed as a tag; it must be a dynamic expression to be output at runtime

Additionally, if `xpl:allowUnknownTag` is set, all unknown tags will be automatically ignored.

```xml
<a:b xpl:allowUnknownTag="true" />
```

If `xpl:ignoreTag="true"` is set, all tags will be ignored, not just unknown ones.

<!-- SOURCE_MD5:2440c7de6ff0c8e37fd41af0b6195df8-->
