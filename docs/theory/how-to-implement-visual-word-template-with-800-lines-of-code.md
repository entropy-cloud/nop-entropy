# 如何用800行代码实现类似poi-tl的可视化Word模板

[poi-tl](https://github.com/Sayi/poi-tl)是基于Apache POI项目实现的一种Word模板引擎。相比于手工编程操作POI对象来构造Word文档，poi-tl可以采用普通Word文件作为基础模板，替换其中的自定义标签来生成输出文件，从而实现了某种程度的可视化设计。例如，在模板中通过`{{xxx}}`形式进行标签标注。

![poi-tl-example](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/report/poi-tl-example.png)


然后在执行时传入一些控制规则和数据对象，即可得到输出文件：

```java
LoopRowTableRenderPolicy policy = new LoopRowTableRenderPolicy();

Configure config = Configure.builder()
        .bind("goods", policy).bind("labors", policy).build(); 

XWPFTemplate template = XWPFTemplate.compile(resource, config).render(
  new HashMap<String, Object>() {{
      put("goods", goods);
      put("labors", labors);
    }}
);
```

生成结果：
![poi-tl-example-result](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/report/poi-tl-example-result.jpg)


按照poi-tl的文档说明：模板是Docx格式的Word文档，你可以使用Microsoft office、WPS Office、Pages等任何你喜欢的软件制作模板，也可以使用Apache POI代码来生成模板。所有的标签都是以`{{`开头，以`}}`结尾，标签可以出现在任何位置，包括页眉，页脚，表格内部，文本框等。poi-tl模板遵循"所见即所得"的设计，模板和标签的样式会被完全保留。

poi-tl的功能是很强大的，它内置了判断、循环、图片、Loop表格行、Loop表格列等多种标签。当内置标签不完全适用的时候，还可以通过插件机制来对生成过程进行逻辑定制。

poi-tl的实现原理大致上是先将Word模板文件解析为POI模型对象，然后再识别标签标记，将其转换为自定义的MetaTemplate结构去执行。无论是模板引擎内部的实现，还是扩展插件的实现，都需要对POI对象模型有相当程度的了解。

poi-tl引擎的实现方式可以说是一个比较传统的面向对象编程的思路。它的实现代码其实写得比较复杂，不是那么直观易懂的。在描述式编程大行其道的今天，是否存在更加简单直接的方式将Word文件转化为所见即所得的生成模板？

在Nop平台中，基于可逆计算理论的指导思想，我们也实现了一个Word模板引擎，它的核心代码只有八、九百行，但是提供了超越poi-tl的可扩展性和可视化设计能力。下面我就具体介绍一下这个技术方案的要点。

## Office Open XML(OOXML)是一种DSL

Office软件从2007版本之后，开始采用OpenXML标准文件格式来存储office文档。将docx文件作为zip文件打开，我们可以看到，它就是由一堆XML文件组成的文件包，其中最主要的文档内容都在document.xml文件中，它的大致内容类似：

```xml
<w:p w14:paraId="60F48F74" w14:textId="77777777" w:rsidR="00A81D38" w:rsidRPr="004C00AA" w:rsidRDefault="00A81D38" w:rsidP="00A81D38">
  <w:pPr>
    <w:pStyle w:val="a7"/>
    <w:rPr>
      <w:rFonts w:ascii="Hei" w:eastAsia="Hei"/>
    </w:rPr>
  </w:pPr>
  <w:r w:rsidRPr="004C00AA">
    <w:rPr>
      <w:rFonts w:ascii="Hei" w:eastAsia="Hei" w:hint="eastAsia"/>
      <w:lang w:val="zh-CN"/>
    </w:rPr>
    <w:t>付款通知书</w:t>
  </w:r>
</w:p>
```

直接查看document.xml，我们会发现它实际上是比POI模型对象更加简单直观的。即使不参考任何文档，基于XML的嵌套标签结构和语义化的参数名称，我们也可以对其中具体标签的作用猜测的八九不离十。而且借助于Office的可视化设计能力，我们可以在office软件中做出某项调整，然后再比较调整前后XML文件的变化，即可直观的看到具体属性的作用结果。

Nop平台中提供了一个文件监听工具，发现docx文件修改后，自动将其解压缩到指定目录，并对document.xml文件进行格式化，同时可以反向监听目录，当document.xml文件修改后自动打包生成docx文件，这样就可以像调试html一样调试docx文件的各项属性了。

OpenXML可以被看作是描述办公文档的一种领域特定语言，XML标签结构其实表达的是它内在的抽象语法树(AST)。基于这一认知，我们可以发现，为了实现Word文件的模板化，完全可以类似jsp实现html的模板化，直接在文本结构或者XML结构层面来进行，而没有必要先将XML解析为强类型的POI对象，然后再弱化对象结构，将其转化为模板结构。

这里的一个本质性的问题在于：不同层面的对象具有不同的类型，比如Paragraph和Table节点的对象类型是不同的，属性个数和类型都是不同的，它们的操作方法和遍历方法也是不同的。但是作为模板生成而言，它们都是标签+属性+子节点，在结构层面是完全一致的，没有必要在模板层面进行对象区分！

根据可逆计算理论，在纷繁芜杂的对象世界之下，存在着厚重的结构层，正如形式各异的建筑实体背后都存在着统一的土木工程原理和工具。Nop平台是可逆计算理论的一个开源实现，它的整体技术战略都是围绕着Tree结构来制定的，它将一切AST树都看作是Tree结构，同时将一切Tree结构也都看作是AST树，通过XLang程序语言为Tree的生成（Generation）、转换(Transformation)、验证（Validation）、分析（Analyzation）提供了统一的和通用的技术解决方案。因为OpenXML是一种XML格式的DSL，直接使用Nop平台中的XPL模板语言即可实现docx文件的模板化，不需要进行任何额外的开发！例如：

```xml
<c:for var="order" items="${entity.orders}">
  <w:tr w:rsidR="00AC64A8" w14:paraId="377D9E14" w14:textId="77777777" w:rsidTr="00F65D42">
    <w:tc>
      <w:p w14:paraId="4CB6B484" w14:textId="1AADBFB6" w:rsidR="00AC64A8" w:rsidRDefault="00871FB8" w:rsidP="00F65D42">
        <w:r w:rsidR="006F674D">
          <w:rPr>
            <w:rFonts w:ascii="Hei" w:eastAsia="Hei" w:hAnsi="Hei" w:cs="Hei"/>
          </w:rPr>
          <w:t>${order.saleDate}</w:t>
        </w:r>
      </w:p>
    </w:tc>
    ...
  </w:tr>
</c:for>
```

直接插入`<c:for>`标签即可实现表格行的循环，通过`${order.saleDate}`这样的EL表达式即可实现动态文本输出。

对于比较常用的功能，我们还可以抽象成自定义的标签库，想在哪用就在哪用，定制的难易易程度相比于poi编程直线下降一个量级。

```xml
<c:lib from="/nop/ooxml/xlib/docx-gen.xlib" />
<!-- 输出图片 -->
<docx-gen:Drawing resource="${xxx}" name="yy" width="100" height="200" />
```

当然，以上做法并不罕见，实际上很多人使用FreeMarker模板语言来生成docx文件。使用FreeMarker的缺点在于，需要手动修改并维护模板文件，无法像poi-tl那样将Office软件作为可视化设计器来使用，通过所见即所得的编辑工具来随时对模板文件进行定制调整。Nop平台中提出了一种巧妙的方法，可以实现类似甚至超越poi-tl的可视化设计功能。更重要的是，根据可逆计算的理论分析，这种方法实际上是通用的，它可以被推广到其他基于XML文件格式的各类可视化设计工具中！

## Nop Word Template

Nop Word Template的巧妙之处在于它利用了Word软件内部的一个支持定制扩展信息的可视化元素：超链接。具体做法如下：

# 1. 为模板中需要被替换的文字增加超链接

![link-expr](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs-en/dev-guide/report/word-template/link-expr.png)

链接的文本可以是示例内容，例如快递，而**链接地址的格式为`expr:EL表达式`**，用于表达如何获取数据，例如expr: order.delivery。

超链接这一元素可以保留所有样式设置，而且它的插入位置在文档结构中是准确的（插入域则有时会在段落之外）。同时相比于poi-tl直接在文档中插入文本内容，使用超链接可以更好的保持原有的展现结构。特别是当表达式内容较长时，可以使用示例文字代替，不会出现因文字过长而导致表格变形或者换行等情况。表达式通过超链接来表达可以避免占用显示空间，当鼠标放到超链接上的时候会自动显示相关内容。

如果界面上的显示空间足够，也可以使用链接的文本作为表达式，此时链接地址的格式为`expr:`或者`xpl:`，即链接地址中没有表达式内容时，会使用链接的文本作为表达式。例如图中的`${entity.consignee}`

> expr：表示插入EL表达式，内置的表达式语法接近于JavaScript
>
> xpl: 表示插入xpl模板语言片段，它支持a${b}c这种嵌入式的表达式输出，也支持更复杂的标签结构。

## 2. 超链接可以表示插入完整的代码块

![xpl-tag](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs-en/dev-guide/report/word-template/xpl-tag.png)

通过`xpl:`超链接可以插入完整的xpl标签，在具体的标签实现中可以输出任意代码块。例如

```
<package-diagrams outputMode="xml">
   <source>
       ....
       <w:drawing>
         ...
       </w:drawing>
   </source>
</package-diagrams>
```

## 3. 通过插入配对的超链接来表示嵌套的块结构

![link-xpl](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs-en/dev-guide/report/word-template/link-xpl.png)

可以插入配对的超链接 `xpl:<c:for var="order" items="${entity.orders}>"`和`xpl:</c:for>`表示它们之间的内容需要被包裹到一个`<c:for>`循环标签中。

与poi-tl相比，这种做法更加灵活，可以引入自定义标签，具有严格的变量作用域定义，也不需要引入各种特殊的约定语法。

## 4. 为需要被替换的图片增加超链接

![link-image](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs-en/dev-guide/report/word-template/link-image.png)

通过expr可以指定图片资源对象（表达式的返回结果是IResource接口即可）。与poi-tl的图片嵌入方式相比，这种方式可以对图片大小和显示方式进行可视化调整。

## 5. 直接嵌入EL表达式

在word文本中可以直接插入 `${expr}`这种EL表达式。有的时候因为字体的原因，会导致一个表达式被拆分成多个`<w:t>`标签，导致EL表达式没有被正确解析。
此时可以将表达式文本选中，然后增加超链接，链接内容设置为`xpl:`即可。另外需要注意`${`等字符必须是英文字符，不要误用为中文字符。
如果确实要在最终结果中输出`${`字符，可以采用转义的方式: `${'$'}{'`。

## 6. 通过XplGenConfig配置引入初始化代码

poi-tl是所谓无逻辑「logic-less」的模板引擎，没有复杂的控制结构和变量赋值，只有标签。这种做法降低了模板引擎实现的复杂度，但是也导致数据准备工作需要在java代码中实现，同时导致模板内置渲染策略比较死板，稍微偏离缺省设计场景可能就需要在java中单独编程实现。

如果我们希望实现一个Word模板管理平台，则模板本身必须具备一定的逻辑独立性，很多初始化和数据准备工作应该在模板内部完成，而不是依赖于外部代码来准备上下文数据变量。

![xpl-config](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs-en/dev-guide/report/word-template/gen-config.png)

可以在模板的尾部插入一个XplGenConfig配置表格，它支持如下配置项

* dump: 模板在编译的时候会被转化为XPL模板语言的代码，这里的开关控制是否将转化后结果打印出来方便调试查看

* dumpFile: 当dump=true的时候，控制转换后代码需要被输出到哪个文件中。这个过程会对xml进行格式化(docx文件中的xml缺省是没有任何缩进的，因此查看并不方便)。

* importLibs: 引入自定义的标签库。缺省引入了/nop/ooxml/xlib/docx-gen.xlib

* beforeGen: 在模板生成前执行的初始化代码

* afterGen: 在模板生成后执行的代码

Word模板会被转换为XPL模板语言之后再作为XPL模板编译并输出。转换后的代码通过dumpFile可以查看，大致结构类似

```xml
<c:unit>
  <c:import from="/nop/test/orm-docx.xlib"/>
  <c:import from="/nop/ooxml/xlib/docx-gen.xlib"/>
  <c:out escape="none"><?xml version="1.0" encoding="UTF-8"?>
</c:out>
  <c:unit xpl:outputMode="none">
    <c:script>logInfo("test")</c:script>
  </c:unit>
  <w:document>
      ...
  </w:document>
</c:unit>
```

当xpl模板编译发现错误时，会抛出异常，其中包含有错误信息和准确的行号。行号对应于dumpFile文件中的位置，例如

```
io.nop.api.core.exceptions.NopEvalException:
NopEvalException[seq=1,errorCode=nop.err.commons.text.scan-unexpected-char,
params={pos=19, reader=${model.displayNam[e], expected=}, eof=true},
desc=读取到的下一个字符不是期待的字符[}]]
@_loc=[68:35:0:0]file:/C:/can/entropy-cloud/nop-ooxml/nop-ooxml-docx/dump-tpl.doc.xml
  @@c:unit/w:document/w:body/w:p[2]/w:r/w:t@@[68:12:0:0]file:/C:/can/entropy-cloud/nop-ooxml/nop-ooxml-docx/dump-tpl.doc.xml
```

以上错误信息表示在dump-tpl.doc.xml的第68行的第35列出现语法错误，同时还显示了XLang语言内部的堆栈信息而不是Java函数的堆栈信息，实际对应的代码内容为

```xml
  <w:t>${model.displayName</w:t>
```

具体的模板示例和输出结果

[payment.docx](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-format/nop-ooxml/nop-ooxml-docx/src/test/resources/payment.docx)

[result-payment.docx](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-format/nop-ooxml/nop-ooxml-docx/src/test/resources/result-payment.docx)

## 7. 自动分行显示
如果文本中包含回车，希望输出到word中时也自动换行，此时可以使用`<docx-gen:r-br>`标签。

`docx-gen:r-`为前缀的标签会生成`<w:r>`文本段，在标签中通过`rPr`子节点可以读取到word中配置的样式。

![word-br](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs-en/dev-guide/report/word-template/word-br.png)


## 通用的可视化模板方案

上一节介绍的Word模板方案本质上是一个通用的设计方案，它的适用范围并不限于Word模板。

仔细回顾一下，整个方案中对底层的可视化设计器的唯一要求就是，它允许为指定的内容关联一个可视化设计元素（例如超链接），然后这个元素允许附加一些自定义的元数据（例如通过超链接的URL来保存表达式代码）。如果设计器的产物本身就是一种结构化的DSL，则利用这些标记和关联的元数据，很容易的就可以把它转换为生成模板。

实际上，我们并不一定需要选择超链接来保持模板设计数据。在Office2003版本中，它支持通过xsd（XML Schema Defintion）定义文件为Word ML直接引入自定义标签。自定义的XML标签具有如下展现形式：

![word-custom-tags](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/report/word-custom-tags.jpg)

如果使用这种自定义XML标签的机制，甚至都不需要开发，直接将document.xml作为XPL模板语言编译就可以了。在Office2003中，使用自定义XML标签还有一个好处，Office会根据xsd中的结构定义，为标签参数生成对应的录入界面，有最基本的格式校验。

Office2007之后因为微软和第三方公司之间的专利之争，微软把自定义XML标签的功能从Office中删除了。

Xpl模板语言为了支持这种设计器关联，内部还提供了一系列简化的机制，例如x:decorator机制。

```xml
<Button>
  <x:decorator>
     <MyContainer1 xpl:skipIf="xxx" />
     <MyContainer2 xpl:if="yyy" />
  </x:decorator>
</Button>
```

等价于

```xml
<MyContainer1 xpl:skipIf="xxx">
   <MyContainer2 xpl:if="yyy">
      <Button />
   </MyContainer>
</MyContainer1>
```

x:decorator类似于函数式语言中的Monad，可以将嵌套的节点结构转化为线性结构。而`xpl:skipIf`表示条件为true时跳过本层节点，直接渲染body，而`xpl:if`表示条件为false时，跳过本节点以及所有子节点。

如果一个可视化设计器具有基本的设计能力，那么它一定具有一些可有可无的可视化设计元素，而且这些元素上的某些属性也是无用且无害的。正所谓有一种用处叫做无用之用，一些可有可无的功能会撑起一个灰色的设计空间，允许意料之外的一些演化在其中发生。

如果一个可视化设计器采用的是开放式的设计，而不是封闭式的设计，那么它就应该允许为可扩展的数据引入外部的schema约束条件，并根据schema自动生成可视化的编辑界面！

目前很多低代码平台都号称可以接入外部的组件，只要增加一些额外的描述信息，即可将外部组件引入组件面板，直接拖拽使用了。如果把眼光放远一些，从更宏观的角度去审视，组件树不过是局部的一颗抽象语法树而已，而引入自定义组件不过是说为抽象语法树的某个局部引入了自定义的schema约束。我们可以将整个软件系统（而不仅仅是可拖拽画布）看作是一个硕大无比的抽象语法树，在整个语法树的各个层面都可以引入外部的schema约束，从而添加实现整个软件系统的定制。

## 总结

在没有太多结构知识的情况下，我们能否有效的操作这些结构，能否实现这些结构的模板化？可逆计算理论的回答是可以。

关于可逆计算理论和具体技术实现，可以参考我此前的文章

基于可逆计算理论设计的低代码平台NopPlatform已开源：

- gitee: https://gitee.com/canonical-entropy/nop-entropy
- github: https://github.com/entropy-cloud/nop-entropy