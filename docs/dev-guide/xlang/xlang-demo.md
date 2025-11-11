# XLang综合示例

## 一. 使用XDef来定义元模型

例如 [imp.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/excel/imp.xdef)
是Excel导入模型的元模型，它描述了为了实现Excel文件解析，我们需要提供哪些信息。

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

在元模型的定义中，属性值对应于属性所对应的stdDomain(对数据类型的一种细化定义)。
例如 `name="!string"`表示name属性是字符串类型（满足string这个stdDomain的格式要求），而stdDomain前的!符号表示属性非空，即name属性必须有值。

对于节点内容的类型，我们通过xdef:value属性来指定，例如`xdef:value="xpl"`表示节点的body部分使用XPL模板语言。

## 二. 模型必须引用元模型

具体的模型定义需要通过x:schema属性来表示它所对应的元模型。模型的结构必须满足元模型的定义要求。这一机制类似于JSON对象的结构需要满足JSON
Schema的约束。
只不过XDef元模型比JSON Schema更强大，比如它提供了xpl、xpl-predicate等可执行代码类型，而JSON
Schema只能指定Number,String等少数纯数据类型，没有定义函数等可执行类型。

例如 [orm.imp.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-persistence/nop-orm-model/src/main/resources/_vfs/nop/orm/imp/orm.imp.xml)
是导入数据库模型时使用的导入模型配置

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

normalizeFieldsExpr在元模型中对应的stdDomain是xpl，对应的Java类型为[IEvalAction](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-core/src/main/java/io/nop/core/lang/eval/IEvalAction.java)
接口。
在具体的模型中，normalizeFieldsExpr的body使用[xpl模板语言](xpl.md)。这里有一个特殊情况，当body段只有一个`c:script`
节点的时候，我们可以直接写[XScript脚本语法](xscript.md)，而不需要使用`c:script`来包裹。
例如，以下两种方式是等价的

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

## 三. XPL模板语言和XScript语言可以相互嵌套

在xpl模板语言中，我们可以通过`c:script`标签来嵌入XScript脚本语言的片段，例如

```
<c:for var="x" items="${list}">
   <c:script>
      if(x < 0)
        break;
   </c:script>
</c:for>
```

反过来，在XScript脚本语言中，我们可以通过xpl函数来嵌入xpl模板语言。此时存在两种调用方式

## 模板字符串

```
<c:script>
  let list = [1,2,3];
  let result = xpl `<c:script>
                      let a = 1;
                      return a;
                    </c:script>`;
</c:script>
```

* 在XLang语言的设计中，我们没有选择使用jsx语法来嵌入模板语言，而是扩展了JavaScript中的模板字符串机制，将模板字符串和宏函数机制结合在一起。
* xpl是一个宏函数，在编译期它会自动解析它的参数得到Expression抽象语法树节点，然后插入到当前脚本的编译结果中。这一设计类似于C#语言中的LinQ机制，
但它更为通用，可以支持嵌入任意语法。例如 xpath `/a/b[@a="3"]` 可以表示嵌入xpath语法，xpath是一个宏函数，它在编译期会自动解析它的参数得到XPath对象。
* 每个xpl标签相当于是一个函数，它具有返回值，可以赋值给变量。

## xpl函数调用标签

可以通过xpl函数来表示直接调用某个XPL标签，此时xpl函数的第一个参数不是XML格式的代码，而是标签名

```
let result = xpl('my:MyTag',{x:3})
```

xpl函数的第二个参数是Map形式的参数集合。除了以Map形式来传入参数集合之外，当参数个数比较少时，我们也可以按照参数的定义顺序来传递参数，例如

```
xpl('my:MyTag',x,y);
```

这种情况下x必须是MyTag标签定义中的第一个参数，而y是第二个参数。

## 四. 扩展属性

在Nop平台的模型文件中，除了使用元模型中定义的属性之外，我们可以随时增加自定义属性。缺省情况下，XDSL模型解析器将所有带名字空间的属性和节点看作是扩展属性，不会要求它们在元模型中有相应的定义。

但是，如果在元模型中，我们明确指定了`xdef:check-ns`，则表示这些名字空间中的名称不能随意扩展，只能是元模型中定义的那些名称。例如

```
<imp xdef:check-ns="imp,xpt">
  <xpt:beforeExpand xdef:value="xml" />
</imp>
```

我们可以在自己的项目中定制平台中已有的xdef元模型。例如我们可以继承平台内置的imp.xdef元模型定义，为它增加扩展属性配置。

```xml
<!-- /my/my-imp.xdef -->
<imp x:extends="/nop/schema/excel/imp.xdef" xdef:base="/nop/schema/excel/imp.xdef"
     x:schema="/nop/schema/xdef.xdef" xmlns:x="/nop/schema/xdsl.xdef" >
    <imp:MyExt value="!string" />
</imp>
```

在具体的模型定义中，只要引用扩展的元模型文件即可。

```xml
<imp x:schema="/my/my-imp.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <imp:MyExt value="abc" />
</imp>
```

在上面的示例中，我们使用了my-imp.xdef元模型，所以我们可以定义imp:MyExt扩展节点。而如果使用平台内置的imp.xdef元模型，
则因为imp:MyExt节点在imp.xdef元模型中没有定义，同时`xdef:check-ns="imp,xpt"`要求检查imp空间中名称的有效性，所以解析的时候会报错。
