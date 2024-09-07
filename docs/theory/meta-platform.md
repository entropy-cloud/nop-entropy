# 如何开发一个能够开发低代码平台的平台

假设我们现在有一个能够开发低代码平台的平台X，那么我们可以看一下抽象层面上它的运作原理。

1. 首先低代码平台不仅仅限于可视化表单编辑，而应该包括一切可以通过模型抽象简化编程的技术平台。这意味着我们这个平台X应该具有所有技术平台共享的通用知识，并且能够把这种知识落实为一种通用的技术实现。比如说开发一个ORM模型和一个工作流模型，它们应该共享50%以上的公共知识，这样才能说一个平台X可以用于开发这两个不同的模型。那么问题就来了，这些公共知识到底是什么？

2. 如果用平台X来开发工作流， `WorkflowDesigner = PlatformX<WorkflowDesignerSpec>` ，在概念层面上PlatformX起到一个生成器的作用，它根据WorkflowDesignerSpec的定义生成一个WorkflowDesigner。

WorkflowDesignerSpec对应于一个工作流设计器模型，PlatformX同样可以提供一个设计器来设计这个Spec。

`WorkflowDesignerSpecDesigner = PlatformX<SpecSpec>` ，也就是说PlatformX根据Spec的Spec应该生成一个Spec的Designer。也就是说模型由元模型定义，元模型由元元模型定义，这个链条可以一直继续下去。在Nop平台中xdef定义元模型，而xdef.xdef定义元元模型，从而截断了这个链条。最终我们只需要xdef即可，xdef采用xdef来定义它自身。

3. 每一个可视化设计器背后都存在一个心智模型，它对应着这个设计器的输入和输出结果是什么。可视化设计器所提供的内容看作是这个模型的所见即所得的可视化形式。

```
   WorkflowEditView ~ WorkflowDefinition
```

可视化的编辑形式(VisualModel)和文本化保存的工作流模型文件(Text DSL)之间可以看作是一种可逆转换关系。

4. 将上面的概念系统化，并补充差量概念用于填补不同抽象层次之间的信息粒度差异，实现多种DSL的无缝拼接，就很自然的得到可逆计算理论的核心公式

```
  App = Delta x-extends Generator<DSL>
```
