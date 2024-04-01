# 简要说明

XLang是面向LowCode领域所专门设计的一种通用的程序语言。XLang的语法以XML语法和TypeScript语法为基础，可以与Java语言无缝集成。

XLang与一般的模板语言或者脚本语言的区别在于，它的理论基础是可逆计算理论（参见[可逆计算：下一代软件构造理论](https://zhuanlan.zhihu.com/p/64004026)），它试图围绕着AST(抽象语法树，Abstract Syntax Tree)
这一基本概念，建立一整套**描述式的**面向AST的定义、生成、转换、分解、合并、编译机制。它的设计目标是成为定义和解释执行DSL(领域特定语言，Domain Specific Language)的一种标准化的元语言。
基于XLang所描述的领域模型很自然的具有可逆语义，可以自动实现可视化设计、差量化定制等，因此它是Nop平台中支持LowCode开发的一种关键性技术。

> 文本表示和可视化展示可以看作是同一模型的两个语义等价的表示。所谓可视化设计可以看作是这两种表示之间的一种可逆转换。

XLang语言由XDef、Xpl、XScript、XDsl、XPath、XTransform等子语言组成。

## XDef

**详细介绍参见[xdef.md](xdef.md)**

XDef是XLang中的领域模型定义语法，作用类似于采用XML语法的XSD语言和采用JSON语法的JSON Schema。XDef与它们的区别在于，它强调两点:

1. schema描述与领域描述本身是同形的，也就是说schema的Tree结构与领域模型的Tree结构是一致的。
2. 所有的集合元素都通过`xdef:key-attr`定义有唯一属性，从而确保领域描述的每个节点都有稳定且唯一的xpath路径。

比如对于如下的`tasks`列表结构

```
<tasks interval="1000">
  <task id="a" label="任务A" status="1" />
  <task id="b" label="任务B" status="2" />
</tasks>
```

XDef描述如下：

```
<!--
@interval 当有任务进行中，会每隔一段时间再次检测，而时间间隔就是通过此项配置，默认 3s
-->
<tasks interval="number:3000" xdef:key-attr="id" xdef:body-type="list">
  <!--
    @id 任务的唯一id
    @label 任务名称
    @status 任务状态
                  0: 初始状态，不可操作。  1: 就绪，可操作状态。
                  2: 进行中，还没有结束。  3：有错误，不可重试。
                  4: 已正常结束。        5：有错误，且可以重试
  -->
  <task id="!string" label="string" status="enum:nop/task/task-status" />
</tasks>
```

而如果采用json schema描述，则为

```
{
   "type": "object",
   "properties": {
        "interval": {
          "default": 3000,
          "description": "当有任务进行中，会每隔一段时间再次检测，而时间间隔就是通过此项配置，默认 3s。",
          "type": "number"
        },
        "items": {
          "items": {
            "id": {
              "description": "任务唯一id",
              "type": "string"
            },
            "label": {
              "description": "任务名称",
              "type": "string"
            },
            "status": {
              "description": "任务状态： \n0: 初始状态，不可操作。\n1: 就绪，可操作状态。\n2: 进行中，还没有结束。\n3：有错误，不可重试。\n4: 已正常结束。\n5：有错误，且可以重试。",
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

json schema的结构与原始数据结构有着类似的嵌套关系，但插入了多余的`properties`层级，且对单个属性进行描述时需要使用复杂的描述对象。
而XDef描述与原始数据结构完全一致，基本可以看作是删除原始数据中的冗余部分，然后加上局部的类型标注即可，明显比json schema要更加简洁、直观。

XSD的冗余程度比起json schema来还要更上一层楼，而且在缺省情况下它认为子节点的顺序很重要，需要保留，这明显为领域结构的定义引入了不必要的麻烦。

对于每一个集合元素，XDef需要明确定义用于区分不同子元素的唯一属性，借助这一属性，可以高效、稳定的实现领域结构的diff算法（类似于vue框架中虚拟DOM的diff），
因此使用XDef定义一个领域结构，同时也意味着定义了该领域结构的差量化结构，我们可以很容易的计算两个领域结构的差与和。

```
  Delta = D1 - D2
  D1 = D2 + Delta
```

## Xpl

**详细介绍参见[xpl.md](xpl.md)**

Xpl是采用XML语法的模板语言，它类似于FreeMarker模板语言，内置了判断、循环等逻辑标签，同时允许引入自定义标签，将一组标签封装为标签库等。 相比于一般的模板语言，XPL专门针对LowCode开发引入了如下特性：

1. 具有多种输出模式。不仅仅是输出文本内容，可以选择输出`XNode`(对应于AST节点的通用结构)、JSON、XML、Text等。输出`XNode`会保留源码位置信息，从而使得 通过多阶段编译产生的代码在调试时可以很容易地追溯到原始代码位置。
2. 自定义标签支持定制。同样的代码，可以选择针对不同的租户、不同的部署环境引入不同的标签实现，从而编译得到不同的运行代码。
   例如同样一套业务代码，通过应用不同的定制开关，可以编译得到在web端和不同小程序端运行的多套可运行程序。基于同样的机制，产品客户化所需的代码定制功能也可以
   通过标签定制来实现。一个标签就相当于是一个函数，因此Xpl语言本质上提供了函数级别的客户化定制能力。
3. 支持元编程。通过宏标签和宏表达式可以实现编译期运行，在编译期具有和运行期完全一致的、图灵完备的编程能力。元编程与自定义标签相结合，可以实现描述式的AST转换， 可以实现不同风格的DSL的无缝嵌套。

```
<c:if test="${condition}">
  条件为true时执行
</c:if>

<c:for items="${list}" var="item" index="index">
  在这里可以访问到循环变量 ${item}, 从0开始的循环下标 ${index}
</c:for>

  <!--
   macro:gen标签缺省的输出模式为XNode，它的body部分编译后会被自动执行，执行过程中输出的节点为最终编译的代码
  -->
  <macro:gen>
     <c:script>
        let a = 'amount';
     </c:script>
     <input name="${a}" value="${'$'}{c}" />
  </macro:gen>

  <!--
  上面的宏定义的最终编译结果相当于是直接编译如下节点
  --->
  <input name="amount" value="${c}" />

  <!-- 引入自定义标签库 -->
  <c:import from="/my/app.xlib" />

  <!-- 定义标签 -->
  <app:CustomTag width="${1}" title="abc">
     body内容
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

XPL标签不仅仅可以作为前端模板引擎来使用，它也可以直接执行后端的业务逻辑。同时它通过宏标签可以支持与任意DSL语法的子语言的集成。例如

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
        根据解析得到的model对象执行其他编译期处理
        -->
     </source>
 </MyDSL>
</lib>

使用时
<my:MyDSL>
  MyDSLParser能够解析的语法格式
</my:MyDSL>
```

## XScript

**详细介绍参见[xscript.md](xscript.md)**

XScript是语法类似于TypeScript的脚本语言。在XPL中可以通过`<c:script>`标签来引入XScript脚本。XScript采用了TypeScript语法的一个子集。

1. 去除了类定义的部分以及与`prototype`相关的部分。为简化与Java的互操作，只允许使用Java中已经存在的类型，不能新建类型.
2. 只允许与Java兼容的类型声明。
3. 去除了`undefined`，只使用`null`。
4. 去除了`generator`和`async`语法。
5. 修改了`import`语法，仅支持导入类和标签库。
6. 去除了`===`相关的语法，禁止`==`进行类型转换。

增加了以下特性

1. 编译期表达式
2. 执行XPL标签
3. 调用扩展方法。可以为Java中的对象注册静态的扩展函数，从而为已存在的类增加方法。例如 `"abc".$firstPart('.')` 实际调用的是`StringHelper.firstPart("abc",'.')`
4. 安全性限制。所有以`$`为前缀的变量名保留为系统变量名，无法在XScript脚本中声明或者设置以`$`为前缀的变量。禁止访问`System`, `Class`等敏感对象。

```
<c:script>
  // 执行编译期表达式
  let x = #{ a.f(3) }

  // 执行xpl标签
  let y = xpl('my:MyTag',{a:1,b:x+3})
</c:script>
```

## XDsl

**详细介绍参见[xdsl.md](xdsl.md)**

XLang是一个开放的语言，它的XPL标签语法非常灵活，可以通过宏标签引入各种风格的DSL表达方式，而宏标签相当于是对编译器的扩展，可以引入新的语法结构，
新的程序语义等，而不需要事先把它们都内置在基础语言内部，这一点类似于Lisp中宏的作用。

与此同时，XLang还可以通过XDef描述来定义独立的DSL模型，例如工作流模型xwf，ORM实体关系映射模型xorm等。这些DSL模型统一采用XML格式存储，
但是在XML的基础上增加了严格的结构约束。XLang为开发新的DSL提供了一系列的帮助，包括

1. 根据XDef描述自动生成DSL模型的解析器、验证器。
2. 自动生成可视化设计器，通过XMeta配置可以对设计器细节进行精细调整。
3. 内置`x:extends`和`x:gen-extends`语法结构，所有DSL自动具有差量编程和元编程的能力，可以自由调用所有Xpl标签库，无需额外设计继承、库等抽象机制。

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

以Nop平台内置的ioc引擎为例，它采用了类似spring1.0的配置语法，并作为XDsl自动继承了x:extends的能力。

1. `x:extends="base.beans.xml"`表示从已有的配置文件继承，可以通过custom机制对已有配置文件进行定制调正，例如删除`id=a`的`bean`定义
2. `x:gen-extends`节点引入元编程生成器，执行动态代码生成`beans`节点，然后再和已有的`beans`节点按照统一的delta合并算法合并在一起。
3. 通过`feature:on`开关可以控制只有配置开关打开的时候才启用某些`bean`的定义。

关于feature:on和feature:off的具体语法，参见[feature-expr.md](feature-expr.md)

与spring的ioc机制进行对比，所有动态生成和扩展的功能都是由XDsl层面统一实现，分解合并完成之后会生成一个符合`ioc.xdef`约束的描述式模型，然后再交由
`DslModelParser`去解析。DSL解析器只需要处理真正的领域结构模型即可，而无需考虑任何额外的分解合并机制。而在spring中，profile、BeanPostProcessor等 内置机制都是特殊针对spring
ioc框架特殊编写的，无法应用于其他DSL的开发，而且它们的存在也破坏了spring所推崇的描述性，导致bean的初始化时机出现非常微妙的 依赖关系，不仔细跟踪spring的执行过程实际上无法确定bean的扫描和初始化时机。

在XLang中不同的DSL共享了同样的扩展机制，使得它们只需要关注最核心的领域模型结构即可，而`x:gen-extends`的全部作用可以被看作是编译期的一种代码生成技术（产生式编程），
简单的说就是不断拼接、转换、输出字符串或者`XNode`，无论内部执行过程多复杂，引入多少XPL标签库，最终实际送到DSL解析器里的只有符合XDef定义的DSL模型文本。所有由扩展
和二次抽象需要所带来的复杂性经过编译期代码生成处理之后就完全被消解掉了，它们不会延续到程序运行阶段，也不会带来任何运行期成本。

## XPath

**详细介绍参见[xpath.md](xpath.md)**

原始的XPath语言是针对XML文档的查询语言，它假设的应用目标是纯文本文档，所操作的节点都是文本节点，因此并不适用于在任意的Tree结构上执行查询。另外一方面，
XPath中使用的表达式语法比较特殊，引入扩展函数也不是很方便。因此，XLang语言中重新定义了XPath的一个简化版本，它具有如下特性：

1. 可以通过适配器应用于任意树形结构上的查询，而不仅限于XML文档
2. 直接使用XScript作为过滤表达式语言，不单独定义XPath使用的表达式语言，因此可以复用已有的函数和标签库
3. 不仅用于从树形结构上获取数据，对于XDsl中定义的所有领域元素，都可以规定它所对应的唯一的XPath路径，因此还可以通过XPath来设置属性值。

```
  IXSelector selector = XPathHelper.parseXSelector("a/b[id=aaa]/@attr")
  node.updateSelected(selector,"sss");
  node.selectOne(selector);

  // 通过adapter可以从任意数据结构上选择
  selector.selectOne(adapter, object);

  // 过滤表达式就是普通的XScript，只是增加了对@属性名的识别
  IXSelector complexSelector = XPathHelper.parseXSelector("//b[@a > 3 || @b != 2]/child");
  List<XNode> children = node.selectMany(complexSelector);
```

## XTransform

**详细介绍参见[xtransform.md](xtransform.md)**

与XPath语法类似，XML体系中的xslt转换语法仅适用于XML文本，而且就实际应用而言，xslt在语法设计上也引入了不必要的复杂性。
XLang语言中定义了一个专用于AST转换的XTransform语法，它相当于是xslt的一个简化版本，具有如下特性:

1. 使用`XSelector`来选择节点。
2. 通过属性表达式来生成属性，属性表达式返回`null`时表示删除属性。
3. 利用嵌套结构自然地表达生成过程。

```
<xt:transform>
  <xt:script>
     编译期执行的xpl
  </xt:script>

  <xt:mapping name="myMapping">

  </xt:mapping>

  <div>
     <!--
      xt:attrs用于指定需要复制的属性。
     -->
     <div xt:xpath="root" >
        <!--
          xt:attrs可以指定需要直接拷贝过来的属性名
          可以通过转换期表达式  %{}来动态生成属性
        -->
        <div xt:xpath="child" xt:attrs="{a,b,c}" title="%{$node.title}-sub" >
             <xt:apply name="myMapping" />
        </div>
     </div>

  </div>

</xt:transform>
```
