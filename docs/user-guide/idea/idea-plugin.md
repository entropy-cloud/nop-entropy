# XLang DSL Plugin

在Nop平台中，所有的DSL都采用XML语法格式，使用统一的xdef元模型来提供规范化的形式约束和基本的属性语义。基于xdef元模型，我们可以实现统一的语法提示、关联分析、断点调试等功能，而无需针对每个DSL语言单独编写IDE插件。

> 插件的编译、安装可以参考文档： [idea.md](../../dev-guide/ide/idea.md)

## DSL语法格式

XLang DSL采用XML格式，根节点上必须通过x:schema属性来指定所对应的xdef元模型，例如

```xml
<beans x:schema="/nop/schema/beans.xdef" 
       xmlns:x="/nop/schema/xdsl.xdef" ...>
</beans>
```

## 语法提示

输入标签名、属性名、属性值的时候，会弹出xdef中定义的相关信息。

![idea-completion](idea-completion.jpg)

## 语法检查

插件会根据xdef定义检查标签名、属性名以及属性值的格式。不符合要求的语法元素会被增加Error标记。

![idea-check](idea-check.jpg)

## 快速文档

鼠标悬停在标签名、属性名以及属性值上时，会显示xdef文件中定义的文档
![idea-quick-doc](idea-quick-doc.jpg)

## 路径链接

鼠标悬停在路径格式的属性值上，同时按CTRL键，会提示跳转到路径所对应的文件。
对于XPL模板标签，则提示跳转到标签库的定义处。
![idea-link](idea-link.png)

## DSL文档格式增强

在 DSL 中的文档内容，推荐采用如下形式：

<example>
    <!-- [这是节点名称]
    > - 第一级列表 #1
    > - 第一级列表 #2
    > - 第一级列表 #3
    >   - 第二级列表 #1
    >   - 第二级列表 #2
    >   - 第二级列表 #3

    @type [这是属性名称]
        > (可选) 属性使用说明
        > - 说明 1
        > - 说明 2
    @name [这也是属性名称]
        > 说明 xxx
    -->
    <xdef:define type="generic-type" name="string" />

</example>

* 文档最开始的 [xxx] 表示标签或属性名称为 xxx；
* 其余行开头的 > （含一个空格）可仅用于多级列表开头，以避免因行首空白被移除而无法正确渲染 markdown 多级列表的问题；

节点文档渲染结果：
![](node-doc.png)

属性文档渲染结果：
![](attr-doc.png)

为了避免恶意链接，markdown 中的链接和图片的地址均完整显示，以方便用户确认链接是否可信：
![](link-ref.png)

## 断点调试

![](idea-runner.png)

![](idea-runner2.png)

在XScript脚本或者Xpl模板片段中可以增加断点。
插件增加了一个与Run和Debug指令平级的执行器XLangDebug，通过它启动后会同时启动Java调试器和启动XLang脚本语言调试器。

![idea-executor](idea-executor.png)
![idea-test-executor](idea-test-executor.png)

![xlang-debugger](xlang-debugger.png)

为了调试XLang，需要引入nop-xlang-debugger模块

```xml
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-xlang-debugger</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```
