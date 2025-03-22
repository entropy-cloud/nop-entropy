# XLang Combined Example


## Using XDef to Define Meta Models

For example, `[imp.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/excel/imp.xdef)` is the meta model used for Excel data import. It defines what information is needed to parse an Excel file.


## Example XML Structure:
```xml
<imp>
    ...
    <sheet name="!string">
        <normalizeFieldsExpr xdef:value="xpl"/>
        ...
    </sheet>
    ...
</imp>
```

In the meta model definition, attribute values correspond to specific stdDomain definitions. For instance, `name="!string"` indicates that the `name` property is of string type (satisfying the `stdDomain` requirements for strings), and the exclamation mark `!` signifies that the property is non-empty, meaning the `name` attribute must have a value.

For node content types, we specify them using the `xdef:value` attribute. For example, `xdef:value="xpl"` means the node's body uses XPL template language.


## Models Must Reference Meta Models

Specific models need to reference their corresponding meta models using the `x:schema` attribute. The model's structure must comply with the meta model's definition. This mechanism is similar to how JSON objects' structures must adhere to JSON Schema constraints, but with more powerful capabilities through XDef meta models.

For example, `[orm.imp.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-orm-model/src/main/resources/_vfs/nop/orm/imp/orm.imp.xml)` is the configuration file used for importing database models. It specifies how to map database entities to their respective data types and relationships.


```xml
<imp x:schema="/nop/schema/excel/imp.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    ...
    <sheet name="entity" namePattern=".*" field="entities" multiple="true" keyProp="name" sheetNameProp="tableName">
        <normalizeFieldsExpr>
            <c:script><![CDATA[
                ...
            ]]></c:script>
            ...
        </normalizeFieldsExpr>
        ...
    </sheet>
    ...
</imp>
```

In this structure, `normalizeFieldsExpr` corresponds to the `xpl` template in the meta model and maps to the `IEvalAction` interface in `[IEvalAction.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-core/src/main/java/io/nop/core/lang/eval/IEvalAction.java)`. For specific models, the body may use either XPL templates directly or XScript scripting without the `<c:script>` wrapper.



In XPL template language, we can embed XScript scripts using the `<c:script>` tag. For example:
```xml
<normalizeFieldsExpr>
    <c:script><![CDATA[
        import xxx.MyHelper;
        return MyHelper.myMethod(a);
    ]]></c:script>
</normalizeFieldsExpr>

<normalizeFieldsExpr>
    import xxx.MyHelper;
    return MyHelper.myMethod(a);
</normalizeFieldsExpr>
```

Both code snippets are equivalent. The second example avoids using the `<c:script>` wrapper when only one script node is present.

```
<c:for var="x" items="${list}">
   <c:script>
      if(x < 0)
        break;
   </c:script>
</c:for>
```

In XScript script language, we can embed XPL template language using the xpl function. At this point, there are two ways to call it.

## Template String

```
<c:script>
  let list = [1,2,3];
  let result = xpl `<c:for var='x' items="${list}">
                      <my:MyTag x="${x}" />
                    </c:for>`;
</c:script>
```

In the XPL template expression, `${list}` is used to represent the variable from the context.

> In the design of XLang language, we did not choose to use JSX syntax to embed template language. Instead, we extended JavaScript's template string mechanism by combining template strings and macro functions.  
> XPL is a macro function. During compilation, it automatically parses its parameters into Expression abstract syntax trees and inserts them into the current script's compiled result. This design is similar to C#'s LINQ mechanism,  
> but it is more versatile and can support embedded any syntax. For example, `xpath "/a/b[@a="3"]"` can represent embedding XPath syntax, where XPath is a macro function that automatically parses its parameters into XPath objects during compilation.

## XPL Function Call Tag

We can use the xpl function to directly call an XPL label. At this point, the first parameter of the xpl function is not XML code but the tag name.

```
let result = xpl('my:MyTag',{x:3});
```

The second parameter of the xpl function is a Map parameter set. Except for passing parameters in Map form, when the number of parameters is small, we can pass parameters in the order defined.

```
xpl('my:MyTag',x,y);
```

In this case, x must be the first parameter defined in MyTag label, and y is the second parameter.

## Four. Extended Attributes

In Nop platform's model file, besides using the attributes defined in the meta-model, we can add custom attributes at any time. By default, the XDSL parser treats all namespace attributes and nodes as extended attributes and does not require them to be defined in the meta-model.

However, if in the meta-model we explicitly set `xdef:check-ns`, it means that these namespaces cannot be extended arbitrarily. Only the names defined in the meta-model are allowed.

For example:

```
<imp xdef:check-ns="imp,xpt">
  <xpt:beforeExpand xdef:value="xml" />
</imp>
```

We can customize the XDSL meta-model in our project. For example, we can inherit the built-in imp.xdef meta-model and add extended attributes to it.

```xml
<!-- /my/my-imp.xdef -->
<imp x:extends="/nop/schema/excel/imp.xdef" xdef:base="/nop/schema/excel/imp.xdef"
     x:schema="/nop/schema/xdef.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <imp:MyExt value="!string" />
</imp>
```

As long as we reference the extended meta-model file, the specific model definitions can be defined accordingly.

```xml
<imp x:schema="/my/my-imp.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <imp:MyExt value="abc" />
</imp>
```

In the above example, my-imp.xdef is used as the meta-model. Therefore, MyExt extension node is defined in my-imp.xdef. If we use the built-in imp.xdef meta-model,  
then because imp:MyExt is not defined in imp.xdef and `xdef:check-ns="imp,xpt"` requires checking the validity of the 'imp' namespace, parsing will fail.

