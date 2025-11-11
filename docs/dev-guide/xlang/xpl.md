# XPL

XPL是一种采用XML语法格式，支持元编程、支持多种输出模式，与EL表达式（XScript脚本语言）紧密结合的一种模板语言。通过自定义标签，XPL可以引入新的语法结构，
实现多种DSL的无缝嵌套。

## 内置标签

XPL内置的标签提供了判断、循环、导入、宏处理等基本功能。

### 控制逻辑

* `<c:if>`
  判断语句。

```xml
<c:if test="${cond}">
  当条件为真时执行。
</c:if>
```

* `<c:for>`
  循环语句。
  `index`对应循环下标, `var`对应于循环过程中的变量名。

```xml
<c:for items="${list}" var="item" index="index">
</c:for>

<c:for begin="0" end="2" var="index" index="index">
</c:for>
```

* `<c:while>`
  while循环语句。

```xml
<c:while test="${cond}">
</c:while>
```

* `<c:break>`
  跳出循环语句

* `<c:continue>`
  类似于java中的`continue`语句，跳过本次循环。

* `<c:return>`
  从自定义标签返回，可以通过`value`属性指定返回值.

```xml
  <c:return value="${result}" />
```

* `<c:choose>`
  多重选择语句。

```xml
 <c:choose>
   <when test="${cond}">
      当条件为true时执行
   </when>
   <otherwise>
      当其他条件都不满足时执行
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
  需要执行的代码
  </body>

  <catch>
     捕获异常，上下文中$excetion对应异常对象
  </catch>

  <finally>

  </finally>
</c:try>
```

### 输出

* `<c:collect>`

* `<c:print>`

* `<c:out>`

### 脚本语言

* `<c:script>`
  嵌入脚本语言，可以用`lang`属性来指定使用不同的脚本引擎，例如`groovy`等，缺省是`xlang`

```xml
<c:script lang="groovy">
  代码
</c:script>
```

XLang平台内置了`lang=java`支持, 通过args指定从上下文中获取哪些变量，在脚本代码中可以直接访问这些变量。

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

可以通过ScriptCompilerRegistry注册更多的脚本引擎支持。脚本引擎接口如下：
```java
public interface IScriptCompiler {
    IEvalFunction compile(SourceLocation loc, String text,
                          List<? extends IFunctionArgument> args, IGenericType returnType,
                          IXLangCompileScope scope);
}
```

### 编译期

* `<c:import>`
  导入常量类或者标签库

```xml
<c:import from="a/b.xlib" />

<c:import class="a.b.c.MyConstants" />
```

引入标签库的时候缺省名字空间根据标签库文件名推定，例如 `a.xlib => a`, `a!ext.xlib => a`。
也可以直接指定名字空间，例如

```xml

<c:import from="xxx.xlib" as="yyy"/>
<yyy:MyTag/>
```

* `<c:include>`

* `<macro:script>`
  编译指定内容, 并直接运行

* `<macro:gen>`
  宏标签会在编译期被运行。

```xml
  <macro:gen xpl:dump="true" >
    这里的内容先被编译为Expression, 然后在编译期会立刻执行此xpl。
    xpl输出的XNode会再次被编译
  </macro:gen>
```

### 其他

* `<c:unit>`
  一个仅起分组作用的虚拟标签。它的直接编译结果为空。

```xml
   <c:unit>
      <div/>
      <input/>
   </c:unit>

   <!-- 等价于 -->
   <div/>
   <input/>
```

* `<c:log>`
  只有`io.nop.xpl.logger`的对应log级别打开时，才拼接日志消息并打印日志。

```xml
  <c:log info="xxx ${myvar} ${myObj.func(3)} sss" />
```

## 自定义标签

### 条件标签

当自定义标签的`conditionTag`设置为`true`时为条件标签

```xml
  <!-- 标签定义 -->
  <WhenAdmin conditionTag="true">
    <source>
      <c:script>
        $userContext.isUserInRole('admin')
      </c:script>
    </source>
  </WhenAdmin>

  <!-- 作为条件容器调用 -->
  <biz:WhenAdmin>
    当用户具有admin角色时执行这里的内容
  </biz:WhenAdmin>
```

条件标签可以直接作为`c:choose`的分支

```xml
  <c:choose>
    <biz:WhenAdmin>
    </biz:WhenAdmin>

    <c:otherwise>

    </c:otherwise>
  </c:choose>
```

### 编译期转换
自定义标签支持transform配置，可以在编译期对标签节点进行转换。例如`bo.xlib`中

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

`<bo-gen:TransformBizObjTag>`标签在编译期执行，它会识别bizObjName属性并自动生成thisObj属性的表达式。例如对于如下标签调用

```xml
<bo:Get bizObjName="NopAuthUser" selection="items{roleMappings}" id="3" />
```

它经过transform段进行处理之后，将被自动转换为如下标签节点进行编译

```xml
<bo:Get thisObj="${inject('nopBizObjectManager').getBizObject('NopAuthUser')}" selection="${selection('items{roleMappings}')}" />
```


## 动态属性

* 如果属性值返回`null`, 则该属性在输出时会被自动忽略。

```xml
  <input class="${null}" />

  <!-- 实际输出 -->
  <input />
```

* `xpl:attrs`可以指定一个Map, 批量输出一组动态属性。如果属性值为`null`, 一样不输出

```xml
  <input xpl:attrs="{name:'a',class:null}" />

  <!-- 实际输出 -->
  <input name="a" />
```

另外，需要注意，如果节点上已经存在某属性，则`xpl:attrs`指定的属性将会被忽略

```xml
  <input name="x" xpl:attrs="{name:'b'}" />

  <!-- 实际输出  -->
  <input name="x" />
```

## 输出模式

xpl标签既有返回值，又有输出。输出具有多种模式

* `none` 不允许输出文本
* `html` 输出html文本，自动识别`<script>`、`<div>`等需要使用完整封闭形式的标签
* `xml` 输出xml文本，如果`xpl:allowUnknownTag`为`true`, 则未识别的所有带名字空间的标签也会被输出
* `text` 不能输出xml标签，但是可以输出文本内容，而且输出的文本不会进行xml转义
* `node` 所有输出的内容自动被转化为`XNode`。

1. 定义标签库时，可以通过在根结点上标记`defaultOutputMode`来设置本库中标签的缺省输出模式。
2. 也可以在定义每个自定义标签时指定`outputMode`
3. 在调用标签时，可以通过`xpl:outputMode`来设置未识别的标签的输出模式。如果自定义标签已经有自己的输出模式，则外部调用时再设置也是无效的。

## thisLib

在自定义标签库中，可以使用`thisLib`来指向当前标签库。
例如在`web.xlib`中，  `<thisLib:LoadPage>`对应于 `<web:LoadPage xpl:lib="/nop/web/xlib/web.xlib">`

引入`thisLib`这个特殊的名字空间的原因在于，外部引用标签库的时候有可能通过`as`来修改最终使用时的名字空间。例如

```xml
<c:import from="/nop/web/xlib/web.xlib" as="myweb" />
<myweb:GenPage page="xx" />
```

## slot机制

xpl模板标签的slot机制类似于Vue组件中的slot机制。

### 1. 在标签库中定义标签时声明slot

```xml
<!-- /test/my-ext.xlib -->
<lib x:schema="/nop/schema/xlib.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <tags>
        <MyTagExt outputMode="xml">
            <!-- 声明一个名称为ext的slot -->
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

* 在标签实现中通过`xpl:slot`属性来引用外部传入的slot实现，通过`xpl:slotArgs`向slot传递参数。具体能够传递哪些参数需要在slot定义时指定
* `xpl:slot`可以标记在任何标签上，并不是只能标记在`c:unit`标签上。与vue的slot类似，当`xpl:slot`指定的slot不存在时，会继续执行该标签，否则会用slot替换该标签。
  也就是说`xpl:slot`所在的标签相当于是提供了slot的缺省值。

### 2. 调用标签时指定slot

```xml
<my-ext:MyTagExt xpl:lib="/test/my-ext.xlib">
  <ext xpl:slotScope="x">
     <x>${x}</x>
  </ext>
</my-ext:MyTagExt>
```

* 调用标签时通过`xpl:slotScope`来引入`xpl:slotArgs`传入的参数，此时可以利用javascript的解构语法来给变量重命名。
* 如果不写`xpl:slotScope`，实际上仍然可以访问到`xpl:slotArgs`传入的参数
* slot编译为一个函数 slot\_{name}, `xpl:slotArgs`就是向这个函数传递的参数，必须是一个Map。而在标签调用时指定的`xpl:slotScope`相当于是声明这个函数的参数列表，此时可以重命名参数

换句话说：

1. slot是将调用方的body内容，放入实现方的`xpl:slot`位置
2. slotArgs 是实现方向调用方传递的参数，在调用方的body中，可以使用这些参数
3. 调用方可以使用`xpl:slotScope` 来明确引入实现方的参数，同时可以重命名

### 3. 指定slotType=node

缺省情况下slot的类型为renderer，编译为一个函数，这个函数的输出模式可以在slot上指定outputMode来定制。除了编译为函数之外，还可以将指定slotType=node，此时
slot就保持XNode节点形式直接传入，而不会被转化为函数。此时也不能通过`xpl:slot="slotName"`来调用这个slot。

一般使用`renderType=node`都是将传入的节点作为模型对象或者元数据来使用，特别是结合编译期宏标签机制，可以实现不同的DSL无缝嵌套在一起。

例如biz.xlib中Validator标签

```xml
<Validator ignoreUnknownAttrs="true" macro="true">
    <!--
    runtime标识是运行期存在的变量。这个属性仅当标签是宏标签的时候起作用
    -->
    <attr name="obj" defaultValue="$scope" runtime="true" optional="true"/>

    <!-- slotType=node表示保持XNode节点内容传入到source段中。如果不设置这个属性，则会编译后传入 -->
    <slot name="default" slotType="node"/>

    <source>
        <!-- 在编译期解析标签体得到ValidatorModel, 保存为编译期的变量validatorModel -->
        <c:script><![CDATA[
           import io.nop.xlang.filter.BizValidatorHelper;

            let validatorModel = BizValidatorHelper.parseValidator(slot_default);
            ...返回编译得到的Expression对象
        ]]></c:script>
    </source>
</Validator>
```

* slot的`name=default`和`slotType=node`表示调用时整个标签的body作为XNode类型的节点对象，名称为slot\_default
* Validator标签的`macro=true` 表示它是宏标签。它的source段在编译期会运行，输出的结果是一个表达式对象，然后再对该表达式对象进行编译。
  宏标签相当于是一种内嵌的代码生成器

调用时

```xml
<biz:Validator xpl:lib="/nop/core/xlib/biz.xlib" fatalSeverity="100"
               obj="${entity}">

    <check id="checkTransferCode" errorCode="test.not-transfer-code"
           errorDescription="扫入的码不是流转码">
        <eq name="flowMode" value="1"/>
    </check>
</biz:Validator>
```

这里`<biz:Validator>`内部嵌套的实际是`validator.xdef`元模型所定义的验证专用的DSL

## xpl专用属性

XPL内置了一些通用属性，所有标签都可以指定这些属性。xpl属性的处理顺序为
`xpl:enableNs` --\>  `xpl:disableNs` --\> `xpl:attrs` -\> `xpl:frame` -\> `xpl:decorator`
\--\> `xpl:lib` --\> `xpl:outputMode` --\> `xpl:is` --\> `xpl:invert` --\> `xpl:return`
\--\> `xpl:if`

1. `xpl:disableNs`/`xpl:enableNs`
   `xpl:disableNs` 忽略指定的名字空间，不把它们看作是xpl标签库中的标签
   `xpl:enableNs` 取消`xpl:ignoreNs`的作用，恢复识别指定名字空间对应的标签库

```xml
  <!-- c:if标签不会被解析，而是作为文本被直接输出 -->
  <c:if test="${x}" xpl:ignoreNs="*">
     <!-- 子节点也不会被解析-->
     <my:MyTag>
         <!--  通过xpl:ignoreTag=false恢复对标签的解析 -->
         <c:if test="${xx}" xpl:checkNs="*">
            ...
         </c:if>
     </my:MyTag>
  </c:if>
```

2. `xpl:ignoreExpr`
   是否识别表达式。如果设置为`true`，则非自定义标签中用到的EL表达式将被作为文本直接输出。

```xml
<div xpl:ignoreExpr="true">
  ${这个表达式不会被解析}
</div>
```

3. `xpl:is`
   可以改变识别的xpl标签名

```xml
<div xpl:is="my:MyTag">
</div>

<!-- 等价于 -->
<my:MyTag>
</my:MyTag>
```

4. `xpl:if`
   控制标签是否运行，相当于简化`c:if`调用

```xml
  <div xpl:if="selectors.contains('a')">
  </div>

  <!-- 如果使用编译期表达式，xpl引擎内部会实现自动优化 -->
  <div xpl:if="#{myVar}">
  </div>
```

5. `xpl:skipIf`
   如果为`true`, 则跳过本层标签，直接编译标签的`body`。相当于是控制跳过嵌套的层次

```xml
  <my:MyTag xpl:skipIf="true">
     <body/>
  </my:MyTag>

  <!-- 等价于 -->
  <body/>
```

6. `xpl:allowUnknownTag`
   是否允许未识别的带名字空间的标签。缺省为`false`, 当带名字空间的标签不能被识别为自定义标签时，会抛出异常。

7. `xpl:outputMode`
   设置xpl编译器的输出模式。注意，在`c:macro`和`x:extends`运行期间，缺省设置了`xpl:outputMode=node`

8. `xpl:lib`
   在局部范围内引入标签库。当超出标签范围后，引入的标签库不可见。

```xml
 <my:MyTag xpl:lib="my.xlib" />

 <!-- xpl:lib引入的标签库仅对当前节点有效，对这里的节点不可见，因此编译时会报错 -->
 <my:MyTag />
```

9. `xpl:return`
   执行完标签后将把返回值设置为指定变量

```xml
  <my:MyTag  xpl:return="x">
    此标签的执行结果被保存到变量x。相当于 let x = #[my:MyTag()]
  </my:MyTag>
```

10. `xpl:invert`
    对于返回`boolean`值的自定义标签，`xpl:invert`表示对返回值取反。

```xml
  <biz:WhenAdmin>
    当具有admin角色的时候执行这里的内容
  </biz:WhenAdmin>

  <biz:WhenAdmin xpl:invert="true">
     当【不具有】admin角色的时候执行这里的内容
  </biz:WhenAdmin>
```

Xpl语言所内置的所有属性，完整定义在[xpl.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xpl.xdef)文件中

## 装饰器decorator

任何标签都支持名为`<xpl:decorator>`的装饰子节点, 它可以将嵌套结构变换为线性结构。

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

<!-- 等价于 -->
<test:MyTag a="1">
  <test:MyTag2>
     <div >
      content
     </div>
     <child/>
  </test:MyTag2>
</test:MyTag>
```

## Xpl模板语言的意义

xpl模板语言是XLang语言家族中的关键性成员，它的设计超越了现在所有的开源模板语言，特别是为元编程补充了大量语法特性。在XScript脚本语言中可以通过

```js
let result = xpl `<c:if></c:if>`
```

这种方式嵌入xpl模板语言，而在Xpl模板语言中，也可以用 `<c:script>`来嵌入脚本语言，并且可以通过各种自定义标签嵌入不同语义和形式的DSL语言。

Xpl模板语言可以设置不同的输出模式，当输出模式为xjson时，可以将xml输出自动转化为json对象，因此我们可以用XML格式来编写AMIS页面

```xml
<form name="a">
    <body>
        <crud name="list">
            <api url="xxx" />
        </crud>
    </body>
</form>
```

各种逻辑结构的形式边界在xpl模板语言中可以被很自然的消解，这才是最关键的部分。

## 代码生成特性
XLang内置了一些特殊的名字空间，比如x,xpl等，它们会在编译期被处理。如果就是要生成`x:abstract`这样的属性，可以使用`xgen-`前缀，例如

```xml
<form id="query" xgen-x:abstract="true" />
```

则实际输出时会移除`xgen-`前缀，最终生成

```xml
<form id="query" x:abstract="true" />
```

## 在Java中调用xpl

### 调用xpl标签
参考DdlSqlCreator.java

```javascript
    public String dropUniqueKey(IEntityModel table, OrmUniqueKeyModel uniqueKey) {
        Map<String, Object> args = new HashMap<>();
        args.put("table", table);
        args.put("uniqueKey", uniqueKey);
        args.put("dialect", dialect);
        return XLang.getTagAction(dmlLibPath, "DropUniqueKey").generateText(XLang.newEvalScope(args));
    }
```

XLang.getTagAction可以根据标签库路径和标签名获得xpl标签，然后通过IEvalScope传入标签参树。TagAction提供了generateText, invoke等多种函数，分别用于输出文本和作为函数调用返回执行值。

## 常见问题

### 1. 如何输出表达式`${a}`，而不是执行表达式

```xml
<control value="${'$'}{a}" />
```

### 2. 如何输出带名字空间的节点，比如`<a:b/>`

```xml
<c:unit xpl:is="${'a:b'+''}" />
```

> 直接写`${'a:b'}`或者`a:b`会被作为标签解析，必须是动态表达式才会在运行时输出

另外如果设置了`xpl:allowUnknownTag`，则会自动忽略所有未知的标签。

```xml
<a:b xpl:allowUnknownTag="true" />
```

如果设置了`xpl:ignoreTag="true"`则会忽略所有标签，而不仅仅是未知的标签。
