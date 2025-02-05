# 关于XLang语言的第三轮答疑

## 1. XLang是一个框架还是一个编程语言？

XLang不是一种传统的编程语言，但是如果你问DeepSeek， DeepSeek会回答说：

> XLang兼具第四代语言的高抽象特性和第五代语言的理论创新。其核心定位是“支持可逆计算的元语言”，既通过低代码和领域特定语法提升开发效率，又通过结构空间理论和差量运算重新定义了程序构造的底层规则。因此，XLang可被视为第四代语言的一种进化形态，或称为“第四代+语言”，在低代码与理论创新之间实现了独特平衡

现在主流的编程语言都是所谓的第三代编程语言（3GL），它们通常以一个main函数作为程序的入口点。整个语言的设计目的本质上就是编写main这个可执行函数，编译器的作用是将程序语言编写的执行逻辑翻译为硬件模型可以执行的指令。但是第四代编程语言（4GL）开始强调描述性，通过高度抽象的声明式语法和图形化的编程工具来简化开发。换句话说，4GL的核心是”非过程化“，强调向更高层次抽象的提升，而不是向下层硬件模型的适配。

随着LLVM的发展，硬件级别的优化实际上是一个独立的问题，不同的编程语言可以共用通用的编译后端。这不构成编程语言的本质性要求。我期望`Moonbit`这种新开发的编程语言能够提供一个非常紧凑、小型的工具链。只要把XScript的AST翻译到`Moonbit`的AST，后续就自动处理。
XLang包含一个子语言XScript，它的语法故意选择了JavaScript语法的一个子集加少量扩展，类型系统也选择了类似Java类型的简化类型系统，这样可以保证在AST层面无损的翻译到所有主流程序语言。

虽然Nop平台是基于XLang语言所建立的一个完整的低代码平台，但是并不意味着XLang本身是一个框架。XLang目前虽然运行在JVM之上，依赖底层Java语言提供的一些帮助库和实现类，但是并不意味着它依赖Java。完全可以将XLang的全部内容移植到其他程序语言，如`Lisp`或者`Rust`上。编写业务代码的时候可以只使用XLang语言，而不用接触到底层的Java语言。

## 2. 图灵机能够实现图灵完备的根本原因在于图灵机可以被看作是一种虚拟机，它可以模拟所有其他的自动计算机器。
对于这句话，科班出身的同学可能会提出反对意见：图灵完备是通过图灵机定义的，所以“图灵机是图灵完备的” 是一种基于定义的必然结论。对这个反对意见我的观点是，如果纠缠在数学定义上说图灵完备是通过图灵机定义的没有问题，但这仅仅是因为历史上的一种偶然情况所导致的。
在概念层面上，图灵完备和NP完备是类似的两个对于计算领域的划分。每一个NP完备的问题的计算复杂度都是相当的，解决了其中任何一个问题，就自动可以解决所有NP完备的问题，但我们并没有把NP完备定义为背包问题完备。
类似的，图灵完备是一种抽象的计算能力，所有的计算机器在这个能力边界上是等价的，并没有某一个计算机器具有更特别的能力。只不过因为历史上偶然的原因，这种能力被命名为图灵完备。图灵完备性可以被定义为一个计算系统能够执行任何可计算函数的能力，它一样可以被命名为Lambda演算完备。
在物理学中，所有的概念都不依赖于任何特定问题或者特定参照系的选择。如果坚持认为图灵完备是通过图灵机定义的，那也不过是一种抽象的计算能力使用一种特殊的理想模型去刻画时产生的一个具体表现而已，但是我们关心的不是这个具体形式，而是这个具有普适性的能力本身。


## 3. XLang语言的发展目标是取代Java这种通用语言吗？

XLang的发展目标并不是取代`C++`、`Java`这种主流编程语言，实际上它们应该是一种互补关系。

```xml
<c:script lang="groovy">
  这里可以使用groovy语法
</c:script>
```

XLang采用XML语法，在需要表达执行逻辑时，会使用Xpl模板语言这个子语言，Xpl模板语言是图灵完备的，在其中还可以通过`<c:script>`标签来嵌入XScript脚本。如果指定了`lang="groovy"`则可以使用groovy语言来实现脚本。通过类似的方式，实际上可以集成任何其他语言来使用。

现有的编程语言有效解决的问题空间已经很大，很多关于过程计算逻辑的表达完全没有必要再发明一种新的语法去处理，复用已有的程序语法甚至语言运行时就可以了。
XLang关注的是现有编程语言没有有效处理的部分，通过引入差量概念和可逆性概念，XLang可以解决很多必须使用`F(X)+Delta`这种计算模式才能有效解决的问题。也就是说，XLang所解决的问题与现有编程语言在很大程度上是不重叠的。但是这并不意味着XLang是一个DSL，它的语法和语义都是通用的，并不是某个业务领域相关的。最终在使用层面，XLang可以和任何第三代编程语言合作来解决问题：XScript这个部分可以替换为任何其他第三代编程语言。

如果将TypeScript看作是JavaScript的扩展， `TypeScript = JavaScript + TypeSystem + JSX`， 那么XLang也可以看作是JavaScript的一种扩展， `XLang = JavaScript + Xpl + MetaProgramming + DeltaProgramming`。Xpl是一种XML格式的模板语言，用途和JSX类似。
这里具有特异性的部分是 DeltaProgramming和MetaProgramming。这里，XLang并不强依赖JavaScript语法，XScript这个子语言部分可以被替换为任何其他第三代编程语言。

有些人对于XLang语言能力的误解可能是它采用XML语法形式，因此无法将它和一个常规的编程语言联系在一起。但是如果你仔细思考一下就会发现，TypeScript的做法是在JavaScript语法内部嵌入类XML的JSX语法，在JSX内部还可以嵌入JavaScript代码段，它是一个正经的编程语言，那么反过来，在XML格式中嵌入JavaScript语法不和TypeScript的做法是等价的吗？

XLang的最外层的入口不是简单的main函数，而是结构各异、语义多样化的各种DSL，甚至是可视化模型而已。

## 4. XLang能建立自己的生态吗？

当然能。但是XLang的生态中包含的内容并不是司空见惯的、每个新语言似乎都要重复开发的通用功能，比如JSON解析器、HTTP客户端等。XLang的正常使用一般会有一个宿主语言，比如Java，它可以直接复用宿主语言中实现的功能，最多是按照某种标准接口封装一下。这类似于TypeScript直接复用底层JavaScript宿主语言的生态。
未来的发展方向应该是多种语法形式的代码可以跨语言进行复用，比如都基于WASM字节码，或者利用GraalVM的polyglot语言互通机制。

XLang的生态中共享的应该主要是各种DSL语言的XDef元模型定义，以及Xpl模板语言所编写的各种代码生成器和元编程结构转换等。

再次强调一下，XLang的主要用途是用于快速开发和扩展领域特定语言，实现所谓的面向语言编程范式(Language Oriented Programming)。业务开发本质上是独立于具体程序语言的，这类似于物理事实独立于坐标系统。通用语言类似于通用的欧几里得坐标系统，在局部我们可以使用更有效的专用的坐标系统，也就是DSL。随着技术的发展，描述式编程和命令式编程可以更好的融合，DSL所占据的描述式的子空间可以越来越大。多个DSL通过`G<DSL1> + G<DSL2> +Delta`可以无缝粘结在一起，必须补充Delta差量的概念才能打破DSL只能适用于单一领域的限制。

## 5. 有没有直观的例子能说明XLang的具体用法？

可以参考如下文章：
- [从可逆计算看Kustomize](https://mp.weixin.qq.com/s/48LWMYjEoRr3dT_HSHP0jQ)
- [逻辑编排引擎NopTaskFlow与SolonFlow的设计对比](https://mp.weixin.qq.com/s/rus4sPKvO-C78cOjSd0ivA)
- [XDSL：通用的领域特定语言设计](https://mp.weixin.qq.com/s/usInt7_odzvFzuiIUPw4iQ)
- [写给程序员的可逆计算理论辨析](https://mp.weixin.qq.com/s/aT99VX6ecmZXdemBPnBcoQ)
- [写给程序员的可逆计算理论辨析补遗](https://mp.weixin.qq.com/s/zGfo7pvKjOCa11PYLJHzzA)

详细语法内容见Nop平台的文档 [XLang语言](https://nop-platform.github.io/projects/nop-entropy/docs/dev-guide/xlang/)

一般情况下我们并不是直接使用XLang来开发业务应用，而是先用XLang定义一个DSL语言，然后具体业务使用这个DSL语言来开发。XLang通过XDef元模型定义语言来定义DSL的结构，Nop平台的`nop-xdefs`模块收集了所有已经定义好的DSL语言的元模型文件。
一般开发自己的DSL的时候也不需要从头开始，可以直接组合这些已有的XDef元模型定义。比如在规则模型中通过`xdef:ref`复用变量定义模型`var-define.xdef`。

```xml
<rule>
  <input name="!var-name" xdef:ref="schema/var-define.xdef" xdef:name="RuleInputDefineModel"
         computed="!boolean=false" mandatory="!boolean=false" xdef:unique-attr="name"/>
  ...
</rule>
```

其实XLang的具体合并算法非常简单，本质上类似于React和Vue中的虚拟DOM Diff算法，只是更加简单。XLang中约定列表中的元素一定具有name、id这种唯一标识，因此具有稳定的xpath可以作为领域坐标，diff计算和merge计算的时候直接按照坐标合并即可。

在语法形式上，XLang相当于是在普通XML格式的基础上引入了`x:schema`、`x:extends`、`x:override`等少数特殊标注，底层的语言引擎能理解这套标注，解析后执行差量合并算法。`x:schema`会引入XDef元模型，通过XDef元模型我们可以定义XML中的节点和属性分别是什么类型。如果指定了类型是xpl，则该属性可以按照Xpl模板语言来解析。

```xml
<task x:schema="/nop/schema/xdef.xdef">
  <steps xdef:body-type="list" xdef:key-attr="name">
    <xpl name="!string">
      <source xdef:value="xpl" />
    </xpl>
  </steps>
</task>
```

上面的元模型定义了`task.xml`的结构，它指出steps是一组步骤定义，每个步骤的类型是xpl，它具有source属性，这个属性使用xpl模板语言来解析。

具体的task.xml的示例

```xml
<task x:schema="/nop/schema/task.xdef">
  <steps>
    <xpl name="test">
      <source>
        logInfo("hello world");
      </source>
    </xpl>
  </steps>
</task>
```

注意到`task.xdef`元模型和它所描述的`task.xml`模型文件的结构基本是一模一样的。`task.xdef`相当于是在一个XML模板上增加一些注解，说明模板中的属性类型。然后具体的`task.xml`是将模板的属性和子节点填充为具体的值后的结果。

元模型的根节点上的`x:schema="/nop/schema/xdef.xdef"`表示`task.xdef`是一个元模型定义文件，它的结构由`xdef.xdef`来约束。而`task.xml`根节点上的`x:schema="/nop/schema/task.xdef`表示`task.xml`是一个模型文件，它的结构由`task.xdef`来约束。
如果去查看`xdef.xdef`的定义，会发现它的`x:schema`指向的仍然是`xdef.xdef`。也就是说，模型的结构由XDef元模型来定义，而XDef元模型也使用XDef元模型来定义。

下面是一个网友的理解：

> 我今天算彻底理解了你的差量化合并的原理，XLang就是一套属性标准定义，增加，删除，或者修改节点定义用的描述，差量化合并就是把主文件和delta进行合并，合并的规则就是XLang语言的定义规范用来合并，合并后就成了一份新的dsl描述（可以是xml，也可以是json，也可以是任何tree结构），得到这份新的dsl后交给执行引擎，怎么解析并处理这份dsl是执行引擎的事情。

XLang定义了DSL，并自动实现DSL的分解、合并、差量化定制，得到合并后的DSL之后原则上就与XLang无关了，执行引擎可以使用任何其他技术去解析XML/YAML来进行后续处理。如果深度使用Nop平台，则执行引擎可以利用XDef元模型自动实现DSL的解析，其中可执行的代码段直接复用Xpl模板语言。


## 6.  XLang是怎么在语言级定义差量的？（不是公式）
XLang通过xdef元模型定义XDSL，XDSL的每一个语法要素有唯一的、稳定的领域坐标，然后用XDSL去表达业务，相当于是将业务投影到XDSL所定义的坐标系中。在这个坐标系的任何一点上都可以定义Delta差量。更进一步大量坐标处产生的Delta差量可以独立出来，作为一个大的Delta差量在概念上被识别、管理，相当于是一种整体性的变化模式成为一个独立的认知实体。

XLang可以看作是一个元语言，它不是直接表达差量，是先定义一个DSL，然后在这个DSL语言中定义语言级别的差量。但是从抽象层面上说，XLang中的所有子语言都是通过XDef元模型语言来定义的，而XDef元模型语言自身也是通过XDef元模型语言来定义，所以它自身的所有子语言也都自动定义了语言级别的差量的概念。

很多人感到难以理解就是没有意识到这里的元性质，并不是直接说A解决什么问题，而是通过一个逻辑的阶梯，在更高的元层面解决，然后投射到下一层，在下一层具体体现。
