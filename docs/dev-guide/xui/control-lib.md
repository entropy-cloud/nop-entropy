# 根据control.xlib动态推导得到前端控件

## XView模型
XView模型提供了与实现技术无关的视图大纲的定义，在最终使用的`page.yaml`文件中我们使用如下标签来根据XView模型动态生成JSON文件

```yaml

x:gen-extends: |
    <web:GenPage view="NopDynFile.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />

title: 这里的配置将会覆盖动态生成的title配置
```

* `page.yaml`可以直接手工编写，XView模型仅仅提供了一种动态生成辅助手段
* 动态生成是在`x:gen-extends`阶段进行，它会生成当前页面的一个基类，然后在此基础上我们可以通过Delta修正技术来对生成的细节进行微调。

## Control映射
`<web:GenPage>`标签将XView模型转换为具体的前端代码时，需要额外补充`control.xlib`标签库信息，它负责实现逻辑上的字段结构向具体的前端控件的映射。
利用这种机制，针对同一个`xview.xml`模型，我们使用不同的`control.xlib`就可以产生不同的生成结果，比如针对移动端和网页端我们可以使用不同的控件映射库。

在XView模型中我们可以配置controlLib属性，如果不配置，则缺省使用`/nop/web/xlib/control.xlib`。

在`control.xlib`中我们可以选择为每种类型定义edit,view, query三种控件。例如`<edit-userId>`, `<view-userId>`和`<query-userId>`表示对于userId类型，在编辑状态、查看状态以及查询状态下分别采用什么控件去显示。

具体为字段查找对应控件时会遵循如下顺序：

1. XView中form的cell或者grid的col上配置的control，这里相当于直接指定控件类型
2. XMeta中prop上配置的`ui:control`
3. XView中form的cell或者grid的col上配置的domain
4. XMeta中prop上配置的domain
5. XView中form的cell或者grid的col上配置的stdDomain
6. XMeta中prop上配置的stdDomain
7. 根据XMeta上配置的`ext:kind`推定的关联关系
8. 根据XMeta上字段类型推定的StdDataType

* stdDomain是在StdDomainRegistry中定义的标准数据域，它包括xpl、xml、csv-set、prop-name等扩展类型。

## 编辑模式
在XView中form的cell或者grid的col上还可以配置editMode，不同的editMode对应不同的控件。上面列举的edit、view、query三种控件实际上对应三种不同的editMode。
这种editMode也是可以根据需要扩充的。比如说在列表页面中编辑的时候，我们希望启用一个特殊的编辑模式 list-edit。则可以配置

```
<grid editMode="list-edit">
</grid>
```

则对于所有控件会优先查找`<list-edit-xx>`这种控件，如果查找不到，再尝试查找标准的`<edit-xx>`控件。

借助于这种字段映射机制，我们实现字段级别的抽象。比如很多字段的类型都是String，但是userId和roleId具有不同的业务含义，它们应该对应于不同的控件，则可以在Excel数据模型中为它们选择不同的domain。
生成代码时通过`control.xlib`就可以映射到不同的控件。

> 在后台服务层的处理中，我们也可以根据domain或者stdDomain的配置来实现字段级别的领域抽象。
