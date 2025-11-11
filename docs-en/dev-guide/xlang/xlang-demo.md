# XLang Comprehensive Example

## I. Define the Metamodel with XDef

For example, [imp.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/excel/imp.xdef)
is the metamodel of the Excel import model. It describes what information we need to provide in order to parse Excel files.

```xml

<imp>
    ....

    <sheet name="!string">
        <normalizeFieldsExpr xdef:value="xpl"/>
        ...
    </sheet>
    ...
</imp>
```

In the metamodel definition, attribute values correspond to the stdDomain (a refined definition of the data type) that the attribute belongs to.
For example, `name="!string"` indicates that the name attribute is of string type (satisfying the format requirements of the string stdDomain), and the exclamation mark before stdDomain denotes non-null, i.e., the name attribute must have a value.

For the type of node content, we specify it via the xdef:value attribute. For example, `xdef:value="xpl"` indicates that the node’s body uses the XPL template language.

## II. Models Must Reference a Metamodel

A concrete model definition needs to indicate the metamodel it corresponds to using the x:schema attribute. The structure of the model must satisfy the constraints defined by the metamodel. This mechanism is similar to requiring a JSON object's structure to conform to a JSON
Schema.
However, XDef metamodels are more powerful than JSON Schema—for instance, they provide executable code types such as xpl and xpl-predicate—whereas JSON
Schema can only specify a few pure data types like Number and String, and does not define functions or other executable types.

For example, [orm.imp.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-persistence/nop-orm-model/src/main/resources/_vfs/nop/orm/imp/orm.imp.xml)
is the import model configuration used when importing database models.

```xml

<imp x:schema="/nop/schema/excel/imp.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    ...
    <sheet name="entity" namePattern=".*" field="entities" multiple="true" keyProp="name" sheetNameProp="tableName">
        <normalizeFieldsExpr>
            <c:script><![CDATA[
                ...
        ]]></c:script>
        </normalizeFieldsExpr>
        ...
    </sheet>
    ...
</imp>
```

In the metamodel, normalizeFieldsExpr corresponds to the xpl stdDomain and maps to the Java type [IEvalAction](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-core/src/main/java/io/nop/core/lang/eval/IEvalAction.java)
interface.
In the concrete model, the body of normalizeFieldsExpr uses the [xpl template language](xpl.md). There is a special case: when the body contains only a single `c:script`
node, we can write [XScript syntax](xscript.md) directly without wrapping it in `c:script`.
For example, the following two forms are equivalent:

```xml

<normalizeFieldsExpr>
    <c:script>
        import xxx.MyHelper;
        return MyHelper.myMethod(a);
    </c:script>
</normalizeFieldsExpr>

<normalizeFieldsExpr>
import xxx.MyHelper;
return MyHelper.myMethod(a);
</normalizeFieldsExpr>
```

## III. XPL Template Language and XScript Can Be Nested

In the xpl template language, we can embed fragments of the XScript scripting language using the `c:script` tag, for example:

```
<c:for var="x" items="${list}">
   <c:script>
      if(x < 0)
        break;
   </c:script>
</c:for>
```

Conversely, within the XScript scripting language, we can embed xpl using the xpl function. There are two ways to call it.

## Template Strings

```
<c:script>
  let list = [1,2,3];
  let result = xpl `<c:script>
                      let a = 1;
                      return a;
                    </c:script>`;
</c:script>
```

* In the design of the XLang language, we did not choose JSX to embed a template language. Instead, we extended JavaScript’s template string mechanism and combined it with a macro function mechanism.
* xpl is a macro function. At compile time, it automatically parses its argument into an Expression AST node and injects it into the compiled output of the current script. This design is similar to LINQ in C#, but it is more general and can embed arbitrary syntax. For example, xpath `/a/b[@a="3"]` represents embedding XPath syntax; xpath is a macro function that parses its argument at compile time to produce an XPath object.
* Each xpl tag is essentially a function. It has a return value that can be assigned to a variable.

## xpl Function Invocation Tag

You can use the xpl function to directly call a specific XPL tag. In this case, the first argument to the xpl function is not XML code but the tag name:

```
let result = xpl('my:MyTag',{x:3})
```

The second argument to xpl is a Map of parameters. Besides passing parameters as a Map, when the number of parameters is small, we can also pass them in the order defined by the tag, for example:

```
xpl('my:MyTag',x,y);
```

In this case, x must be the first parameter defined by the MyTag tag, and y the second.

## IV. Extension Attributes

In the Nop platform’s model files, in addition to using attributes defined in the metamodel, we can add custom attributes at any time. By default, the XDSL model parser treats all namespaced attributes and nodes as extension attributes and does not require them to have corresponding definitions in the metamodel.

However, if we explicitly specify `xdef:check-ns` in the metamodel, it means names within those namespaces cannot be arbitrarily extended and must be among those defined in the metamodel. For example:

```
<imp xdef:check-ns="imp,xpt">
  <xpt:beforeExpand xdef:value="xml" />
</imp>
```

We can customize an existing xdef metamodel from the platform within our own project. For example, we can extend the built-in imp.xdef metamodel definition and add extension attribute configurations to it.

```xml
<!-- /my/my-imp.xdef -->
<imp x:extends="/nop/schema/excel/imp.xdef" xdef:base="/nop/schema/excel/imp.xdef"
     x:schema="/nop/schema/xdef.xdef" xmlns:x="/nop/schema/xdsl.xdef" >
    <imp:MyExt value="!string" />
</imp>
```

In the concrete model definition, just reference the extended metamodel file:

```xml
<imp x:schema="/my/my-imp.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <imp:MyExt value="abc" />
</imp>
```

In the example above, because we used the my-imp.xdef metamodel, we can define the imp:MyExt extension node. If we were using the platform’s built-in imp.xdef metamodel,
then since the imp:MyExt node is not defined in the imp.xdef metamodel and `xdef:check-ns="imp,xpt"` requires checking the validity of names within the imp namespace, a parse-time error would be reported.
<!-- SOURCE_MD5:d129978cdbde82eb8ca57a7451cb3987-->
