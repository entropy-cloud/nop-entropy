# 从可逆计算看DSL的设计要点

低代码平台的可视化设计器本质上是DSL（Domain Specific Language）的结构化编辑器。可视化设计器将编辑的结果序列化成文本格式时所采用的规范就是一种DSL语法定义。

Nop平台基于可逆计算原理，提出了一整套系统化的构建机制来简化DSL的设计和实现，使得我们很容易增加针对自己业务领域的DSL，也很容易在已有DSL的基础上进行扩展。具体来说，Nop平台中所定义的DSL一般采用XML语法格式，符合所谓的XDSL规范要求。XDSL的设计要点如下:

## 一. DSL优先而不是可视化设计优先

很多低代码平台的设计重心是可视化设计器的简便易用，导致它的DSL格式设计随意、混乱冗长，不适合程序员人工阅读编写。XDSL强调DSL文本形式的设计应该简洁、直观，可以手工编写，也方便程序自动处理。可视化设计可以看作是文本形式DSL的另外一种形式的表象，可视化表象和文本形式之间可以按照规范化的规则进行可逆转换。

在这种设计思想下，同一个DSL可以具有多种可视化设计器，比如NopORM模型对应的DSL是app.orm.xml这种模型文件，而它的可视化设计器可以是Excel、PowerDesigner或者PDMiner。我们还可以增加更多的可视化设计器，只要它们的设计文件可以和orm.xml模型文件实现双向转换。

在具体的业务应用中，我们还可以增加采用定制化的可视化设计器，比如一个局部的细节设计器只负责设计模型文件的某个部分，然后通过差量合并运算将局部设计结果合并到整体模型中。

## 二. DSL的语法通过元模型来定义，而所有的DSL共享同样的元模型定义语言。

DSL的价值在于它所抽象出来的具有业务价值的领域语义空间，至于它**采用什么样的语法形式本质上是一个次要问题**。XDSL统一采用XML语法形式，这样就可以引入统一的XDefinition元模型语言来规范具体的DSL语法。

> 元模型是描述模型的模型。类似于元数据是描述数据的数据。

```xml
<orm x:schema="/nop/schema/orm/orm.xdef"  xmlns:x="/nop/schema/xdsl.xdef">
	...
</orm>
```

* 在模型文件的根节点上，我们通过`x:schema`来指定元模型定义文件。

* [orm.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/orm.xdef)这个元模型使用[xdef.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xdef.xdef)这个元元模型来定义。

* xdef.xdef采用xdef.xdef自身来定义，所以我们不需要更高层次的元元元模型。

### 统一的元模型语言促进DSL之间的无缝嵌套

在Nop平台中，大量的DSL元模型定义中会引用已经定义的其他DSL模型。例如 [api.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/api.xdef)和[xmeta.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xmeta.xdef)都会引用已定义的[schema.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/schema/schema.xdef)

不同的DSL使用同样的类型定义，也便于复用同样的可视化设计组件、转换工具、校验规则等。

### 根据元模型自动提供IDE插件

Nop平台提供了一个IDEA插件[nop-idea-plugin](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-idea-plugin)。它会根据`x:schema`指定的元模型自动校验DSL语法，并实现自动语法提示，链接跳转等功能，对于函数类型的DSL节点，它还可以提供断点调试功能。当我们增加一个新的DSL语言的时候，不需要单独为它开发IDEA插件工具，直接就可以得到IDEA开发支持。

根据元模型，我们还可以自动推导得到可视化设计器。而不需要为每个DSL单独引入可视化设计器。

## 三. 所有DSL都需要提供分解、合并机制

一个DSL文件复杂到一定程度，必然需要引入分解、合并、库抽象等管理复杂性的机制。XDSL定义了一组标准化的Delta差量语法，具体参见[xdsl.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xdsl.xdef)

```xml
<meta x:extends="_NopAuthUser.xmeta"
	  x:schema="/nop/schema/xmeta.xdef" xmlns:x="/nop/schema/xdsl.xdef" >

	<x:post-extends>
		<biz-gen:GenDictLabelFields xpl:lib="/nop/core/xlib/biz-gen.xlib"/>
	</x:post-extends>
</meta>
```

`x:extends`用于继承已有的模型文件，而`x:gen-extends`和`x:post-extends`是内置的元编程机制(Meta Programming)，它们用于实现可逆计算理论中的Generator部分，动态生成DSL模型对象，然后再进行Delta合并。

`x:override`用于指定合并节点时所采用的合并策略，具体定义参见[可逆计算理论中的Delta合并算法](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/dev-guide/xlang/x-override.md)

## 四. 通过差量文件系统管理所有DSL文件

Nop平台将所有的模型文件纳入统一的虚拟文件系统来管理。这个虚拟文件系统提供了类似Docker技术中UnionFS文件系统的功能，内部不同的目录构成不同的层，高层目录中的文件会自动覆盖低层目录中的相同虚拟路径下的文件。
具体来说，`/_vfs/_delta/default/a.xml`会自动覆盖`/_vfs/a.xml`文件。在代码中所有使用虚拟文件路径`/a.xml`的地方在运行时实际加载的文件是`/_vfs/_delta/default/a.xml`文件。也就是说，我们**不用修改原有的源代码，只需要在delta目录下增加同名的文件，就可以自动改变实际加载的模型内容**。

* 可以通过配置项 nop.core.vfs.delta-layer-ids来指定多个delta层（缺省情况下只有一个default差量层）。
* 在delta目录下的XDSL文件可以通过 `x:extends="super"`来表示继承前一个层中的模型文件。
* 可以将数据库表中保存的模型文件也映射到某个虚拟文件路径，比如wf:MyWf/1.0表示从数据库中的NopWfDefinition表中加载模型文件。

借助于差量文件系统以及XDSL内置的Delta合并算法，我们可以实现系统级别的Delta定制机制，在完全不修改基础产品源代码的情况下，通过增加Delta模块实现对系统的数据模型、业务逻辑、前端界面等进行深度的定制调整，参见[如何在不修改基础产品源码的情况下实现定制化开发](https://zhuanlan.zhihu.com/p/628770810)

## 五. 通过统一的Loader来加载DSL模型

Nop平台中使用统一的ResourceComponentManager来加载所有的DSL模型。

```
OrmModel model = (OrmModel)ResourceComponentManager.instance().loadComponentModel("/nop/auth/orm/app.orm.xml");
```

当我们增加一种新的DSL模型的时候，可以增加一个注册文件，例如orm.register-model.xml

```xml
<model x:schema="/nop/schema/register-model.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       name="orm">
    <loaders>
        <xlsx-loader fileType="orm.xlsx" impPath="/nop/orm/imp/orm.imp.xml"/>
        <xdsl-loader fileType="orm.xml" schemaPath="/nop/schema/orm/orm.xdef"/>
    </loaders>
</model>
```

通过这个注册模型，我们可以指定对于给定的文件类型，如何进行解析得到模型对象。

* xlsx-loader指定如何根据Excel导入模型配置解析Excel模型文件
* xdsl-loader指定DSL文件所必须具有的元模型，并按照元模型进行解析（模型文件的x:schema指定的元模型必须是schemaPath指定的值或者是在它基础上进行扩展的）

基于统一的模型加载器，我们可以实现针对任意模型的代码生成工具

```
java -jar nop-cli.jar gen abc.model.xlsx -t=/nop/templats/my-model
```

gen命令接受一个模型文件参数，然后通过-t参数来指定代码生成模板路径，就可以自动解析模型文件得到模型对象，传入到模板文件中生成代码。具体参见 [数据驱动的差量化代码生成器](https://zhuanlan.zhihu.com/p/540022264)

### 解析缓存和依赖追踪

ResourceComponentManager内部管理了所有DSL模型的解析缓存以及DSL模型文件之间的依赖关系。它的依赖追踪机制类似于前端Vue框架使用的依赖追踪，即动态记录模型解析过程中加载或者使用过的DSL模型，当模型文件的修改时间发生变化的时候，所有依赖它的模型缓存都自动被记为失效。

nop-cli工具还提供了watch功能，可以监听指定目录下为模型文件，当模型文件发生变化的时候自动重新执行代码生成器生成衍生的代码。

### 可逆计算的切入途径

可逆计算原理的核心实现全部被封装在ResourceComponentManager这个抽象之中。在第三方应用中引入可逆计算最简单的方式就是把自己的模型加载函数替换为ResourceComponentManager.loadComponentModel。比如说，为了给Spring和MyBatis框架引入模型文件的Delta定制功能，我们重新实现了beans.xml和mapper.xml的扫描功能，使用ResourceComponentManager来动态生成DOM对象，然后调用Spring和MyBatis的解析器去解析并注册到对应引擎中。

理论层面的分析可以参见[从张量积看低代码平台的设计](https://zhuanlan.zhihu.com/p/531474176)

## 六. 所有的DSL模型对象都支持扩展属性

XDSL模型对象的属性并不是在开发期固化的，它一般从AbstractComponentModel基类继承，支持增加任意的扩展属性。在具体的业务应用中，我们可以选择从已有的元模型继承，增加业务特定的扩展属性。

比如平台中内置了[xmeta.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xmeta.xdef)这个元模型。

我们可以定义xmeta-ext.xdef元模型，它从xmeta.xdef继承，然后增加一些扩展字段

```xml
<meta x:extends="/nop/schema/xmeta.xdef" xmlns:ui="ui" xmlns:graphql="graphql"
      x:schema="/nop/schema/xdef.xdef"
      xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef">

	<props>
		<prop ui:show="string" graphql:type="string" />
	</props>

</meta>
```

以上元模型表示为xmeta模型的prop节点增加ui:show属性和graphql:type属性。

然后在具体的meta文件中我们就可以使用xmeta-ext.xdef来替换原有的xmeta.xdef。

```xml
<meta x:schema="/my/schema/xmeta-ext.xdef">...</meta>
```

* IDEA插件会自动识别并使用扩展的元模型定义来校验Meta文件。
* 使用ResourceComponentManager.loadComponentModel加载的模型对象上会包含扩展属性。

也就是说，在不修改平台内置元模型定义的情况下，我们可以随时为已有的模型对象增加扩展属性，并在编程中像内置属性那样使用它们。
