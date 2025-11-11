# 低代码平台中的元编程(Meta Programming)

在众多的编程语言中，爷爷辈的Lisp语言一直是一个独特的存在，这种独特性有人把它总结为"Lisp是可编程的编程语言"
。这指的是Lisp具有强大的元编程能力，可以由程序员自主创造新的语法抽象。
编程通俗的说就是**写代码**, 而所谓的元编程指的是**写生成代码的代码**。
Lisp通过宏提供了元编程的能力，而Lisp宏本质上就是一种内嵌在语言中的代码生成器。除了Lisp语言之外，Scala和Rust这些比较现代的程序语言也提供了所谓的宏的设计，但是宏一般被看作是非常复杂的底层技术，很少进入普通程序员的工具箱。

Nop平台的XLang语言是实现可逆计算原理的核心技术之一，为了落实可逆计算理论所提出的 `App = Delta x-extends Generator<DSL>`
这样一种面向DSL和差量编程的新的编程范式，
XLang定义了一整套系统化的、覆盖应用系统开发方方面面的Generator方案。Lisp的宏仅仅是提供了生成Lisp
AST的元编程机制，而XLang除了引入宏函数用于生成XLang AST之外，还提供了面向代码生成的Xpl模板语法，
生成的范围从局部的函数实现体，到单个模型文件，再到整个模块目录。特别是Nop平台中定义的所有DSL语言都内置了`x:gen-extends`
这样的差量生成机制，可以在模型解析、加载的过程中动态生成模型差量再自动实现差量合并，
从而创造了一种新的软件结构复用手段，解决了很多在传统编程范式下难以处理的技术问题。在本文中，我将简单介绍一下Nop平台中所内置的这些元编程机制。

## 宏函数

XLang语言中也定义了类似Lisp宏的宏函数。所谓宏函数是在编译期执行，自动生成Expression抽象语法树节点的函数。

宏函数具有特殊的参数要求，并且需要增加`@Macro`
注解。具体示例可以参见[GlobalFunctions](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/functions/GlobalFunctions.java)。

> EvalGlobalRegistry.instance().registerStaticFunctions(GlobalFunctions.class) 会将类中的所有静态函数注册为XScript脚本语言中可用的全局函数

```javascript
    @Macro
    public static Expression xpl(@Name("scope") IXLangCompileScope scope, @Name("expr") CallExpression expr) {
        return TemplateMacroImpls.xpl(scope, expr);
    }
```

宏函数的第一个参数必须是IXLangCompileScope类型，第二个参数必须是CallExpression类型，返回值必须是Expression类型。

编译宏函数的时候，会把函数调用所对应的AST作为CallExpression传入。例如

```
let result = xpl `<c:if test="${x}">aaa</c:if>`
```

编译xpl宏函数的时候CallExpression的第一个参数是TemplateStringLiteral，也就是上面调用中的XML文本 `<c:if test="${x}">aaa</c:if>`。
在宏函数中我们可以自行解析这个XML文本，然后构造出新的Expression对象返回。

利用宏函数机制，结合XScript语言中的TemplateStringLiteral，我们可以很容易的将不同语法格式的DSL嵌入到XScript语言中。例如，\*
*提供类似C# LinQ的SQL查询语法*\*。

```
let result = linq `select ff from myObject o  where o.value > 3`
```

目前在Nop平台中，内置了如下宏函数

|函数名|说明|
|---|---|
|xml|解析XML文本得到XNode节点，并包装为LiteralExpression|
|xpl|解析Xpl模板文本得到Expression|
|sql|解析Xpl模板文本得到生成SQL语句的Expression|
|jpath|解析json path得到JPath对象，并包装为LiteralExpression|
|xpath|解析 XSelector文本得到XSeletor对象，并包装为LiteralExpression|
|selection|解析类似GraphQL Query的对象属性选择文本得到 FieldSelection对象，并包装为LiteralExpression|
|order\_by|解析 order by语句片段，得到List<OrderFieldBean>对象，并包装为LiteralExpression|
|location|返回调用函数所在的源码位置，并包装为LiteralExpression|
|IF|实现类似Excel公式中IF函数的功能|
|SWITCH|实现类似Excel公式中SWITCH函数的功能|

因为宏函数在编译期执行，因此用宏函数来实现解析功能可以优化系统执行性能。例如从XNode中读取子节点a的b属性时

```
  node.selectOne(xpath `a/@b`)
```

因为xpath是一个宏函数，所以它在编译期就会完成解析，在运行期相当于是传送一个常量对象给selectOne函数。

通过宏函数可以实现自定义的语法结构，例如IF(X,Y,Z)会被转换为if语句。

## 面向代码生成的Xpl模板语言

Xpl模板语言是XLang语言的一部分，它采用XML格式，包含`<c:if>`和`<c:for>`
等图灵完备的逻辑运算语法规则。**XML格式的模板语言可以实现Lisp同像性，即代码的格式与生成的数据的格式相同**。

一般的模板语言（例如Freemarker或者Velocity）并不具有同像性，而且它们都只是用于文本生成，并不是真正的支持**代码生成**。
Xpl模板语言为了支持代码生成，它提供了多种输出模式：

1. node模式：输出XNode节点。**这种方式会保留源代码位置信息**，即在最终得到的结果中我们可以知道每个属性和节点到底是那一段源码生成的。
2. xml模式：输出XML文本，自动对属性和文本内容进行XML转义。
3. html模式：输出XHTML文本，除`<br/>`等少数标签之外，大部分标签都采用完整格式输出，即总是输出`<div></div>`而不会输出`<div/>`
4. text模式：不允许输出节点和属性，只允许输出文本内容，而且不需要进行XML转义。
5. xjson模式：输出XNode节点自动按照固定规则转换为JSON对象。
6. sql模式：输出SQL语句，对于表达式输出结果，自动变换为SQL参数

例如对于以下SQL输出，

```
<filter:sql>
  o.id in (select o.id from MyTable o where o.id = ${entity.id})
</filter:sql>
```

实际会生成 `o.id in (select o.id from MyTable o where o.id = ? )`，表达式的值不会直接拼接到SQL文本中，而是会被替换为SQL参数。

## 编译期表达式

Xpl模板语言内置了`<macro:gen>`和`<macro:script>`等标签，它们会在编译期自动执行。

* `<macro:script>`表示在编译期执行表达式，比如可以在编译期动态解析Excel模型文件得到一个模型对象等

```xml

<macro:script>
    import test.MyModelHelper;

    const myModel = MyModelHelper.loadModel('/nop/test/test.my-model.xlsx');
</macro:script>
```

得到编译期变量之后，后续表达式可以使用编译期表达式来访问该对象，例如 `#{myModel.myFunc(3)}`

* 编译期表达式采用 `#{expr}`这种形式。编译期表达式会在编译到该表达式的时候立刻执行，直接保留到运行期的是它的返回结果。
* 在普通的表达式中可以使用编译期表达式，例如 ${ x \> #{MyConstants.MIN\_VALUE} }
* Xpl模板语言在编译期时会自动执行编译期表达式，并根据执行结果进行优化，例如`<div xpl:if="#{false}>` 在编译期可以获知xpl:
  if的值是false，此节点会被自动删除。

`<macro:gen>`的内容是Xpl模板语法，它会先编译body，再执行body，收集输出结果，然后再编译生成的结果。而`<macro:script>`
的内容是XScript语法，并且它会丢弃返回结果

## 自定义宏标签

Xpl模板语言中的标签库中可以定义宏标签。宏标签与普通标签的区别在于，宏标签的source段在编译之后会立刻执行，然后再收集执行过程中输出的内容进行编译。

比如，我们可以定义一个宏标签`<sql:filter>`,它可以实现如下结构变换

```xml

<sql:filter>and o.fld = :param</sql:filter>
        变换为
<c:if test="${!_.isEmpty(param)}">
and o.fld = ${param}
</c:if>
```

具体实现在[sql.xlib](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/xlib/sql.xlib)
标签库中

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

上述的宏标签会对节点内容进行结构变换，生成`<c:if>`节点，然后模板引擎会再对输出的`<c:if>`节点进行编译，效果等价于手工编写对应节点。

* 通过`slotType="node"`的slot来直接读取节点内容。slotType=node时表示不解析slot的内容，直接把它作为XNode类型的变量。
* `xpl:ignoreTag`表示不将当前节点以及子节点识别为xpl标签，将`<c:if>`直接作为普通XML节点输出。
* `test="${'$'}{$cond}"`中的表达式会被识别，执行表达式后生成`test="${cond}"`

\*\* 宏标签类似于Lisp语言中的宏，它提供了一种简易的AST语法树变换机制，相当于是一种内嵌的代码生成器 \*\*

## 编译得到AST

通过`<c:ast>`标签可以得到内容部分所对应的抽象语法树（Expression类型）。

```xml

<Validator ignoreUnknownAttrs="true" macro="true">

    <!--runtime标识是运行期存在的变量。这个属性仅当标签是宏标签的时候起作用-->
    <attr name="obj" defaultValue="$scope" runtime="true" optional="true"/>

    <!--slotType=node表示保持XNode节点内容传入到source段中。如果不设置这个属性，则会编译后传入-->
    <slot name="default" slotType="node"/>
    <description>
        利用宏标签机制将XNode按照Validator模型解析，并转化对ModelBasedValidator调用。
        宏标签的source段在编译期执行，它的输出结果才是最终要编译的内容
    </description>
    <source>

        <!--在编译期解析标签体得到ValidatorModel, 保存为编译期的变量validatorModel-->
        <c:script><![CDATA[
                    import io.nop.biz.lib.BizValidatorHelper;

                    let validatorModel = BizValidatorHelper.parseValidator(slot_default);
                    // 得到<c:script>对应的抽象语法树
                    let ast = xpl `
                         <c:ast>
                            <c:script>
                               import io.nop.biz.lib.BizValidatorHelper;
                               if(obj == '$scope') obj = $scope;
                               BizValidatorHelper.runValidatorModel(validatorModel,obj,svcCtx);
                            </c:script>
                         </c:ast>
                     `
                    // 将抽象语法树中的标识名称替换为编译期解析得到的模型对象。这样在运行期就不需要动态加载模型并解析
                    return ast.replaceIdentifier("validatorModel",validatorModel);
                ]]></c:script>
    </source>
</Validator>
```

* 宏标签的source段在编译的时候执行， BizValidatorHelper.parseValidator(slot\_default)
  表示解析标签节点得到ValidatorModel对象（这个对象是在编译期存在）。
* 在XScript脚本语言（语法类似TypeScript）中，可以通过xpl模板函数来嵌入XML格式的Xpl模板代码。
* ast = xpl `<c:ast>...</c:ast>` 表示执行xpl模板函数，`<c:ast>`表示仅仅是得到它的子节点所对应的AST语法树，而不是执行其中的内容
* ast.replaceIdentifier("validatorModel",validatorModel) 表示将ast语法树中的名称为validatorModel的标识符替换为编译期变量
  ValidatorModel。这相当于是一种常量替换，将变量名替换为变量所代表的具体的值。
  因为validatorModel是在编译期解析得到的模型对象，所以在运行期完全不需要再进行任何动态解析过程。
* source段可以直接返回AST语法树节点（对应于Expression类型），而不一定需要通过输出XNode来动态生成AST语法树。（上一节的例子是通过输出来构造AST语法树）
* `<attr name="obj" runtime="true">`
  表示obj属性为运行时属性，在source段中它对应于一个Expression，而不是它的值。如果没有标记runtime=true，则在source段中可以使用，但是因为宏标签的source段是在编译期运行，所以调用时属性值只能是固定值或者编译期表达式。

```xml

<biz:Validator obj="${entity}"/>
```

## XDSL的差量生成与合并机制

Nop平台中所有的DSL都支持x-extends差量合并机制，通过它实现了可逆计算理论所要求的计算模式

> App = Delta x-extends Generator<DSL>

具体来说，所有的DSL都支持`x:gen-extends`和 `x:post-extends`
配置段，它们是编译期执行的Generator，利用XPL模板语言来动态生成模型节点，允许一次性生成多个节点，然后依次进行合并，具体合并顺序定义如下：

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

合并结果为

```
F x-extends E x-extends model x-extends D x-extends C x-extends B x-extends A
```

当前模型会覆盖`x:gen-extends`和`x:extends`的结果，而`x:post-extends`会覆盖当前模型。

借助于`x:extends`和`x:gen-extends`
我们可以有效的实现DSL的分解和组合。具体介绍参见 [XDSL：通用的领域特定语言设计](https://zhuanlan.zhihu.com/p/612512300)

## 数据驱动的差量化代码生成器

为了在系统级别实现可逆计算理论所要求的软件构造模式，Nop平台提供了一个数据驱动的差量化代码生成器XCodeGenerator。

一般的代码生成器都是针对某个特定目的定制的，比如常见的MyBatis的代码生成器，它的控制逻辑由一个特定的CodeGenerator类来实现，它负责读取模板，构造生成文件路径，并初始化上下文模型变量，执行循环逻辑。如果我们希望调整代码生成的细节，则一般需要修改这个CodeGenerator类。

XCodeGenerator的做法与传统的代码生成器不同，它将模板路径看作是一种微格式的DSL，把判断和循环逻辑编码在路径格式中，从而由模板自身的组织结构来控制代码生成过程。例如

```
/src/{package.name}/{model.webEnabled}{model.name}Controller.java.xgen
```

以上模式可以表示遍历package下的每个model，对每个webEnabled属性设置为true的Model都生成一个Controller.java类。

基于这种设计，我们只需要调整模板文件的目录结构，就可以控制目标代码的目录结构和生成时机。

具体介绍参见 [数据驱动的差量化代码生成器](https://zhuanlan.zhihu.com/p/540022264)

XCodeGenerator可以与maven打包工具集成在一起，在Java代码编译前和编译后执行代码生成动作，从而起到某种类似Java注解处理器(APT)技术的作用。只是它的使用远比APT要简单、直观。

具体集成方式可以参见 [如何集成Nop平台的代码生成器](https://zhuanlan.zhihu.com/p/613448320)
