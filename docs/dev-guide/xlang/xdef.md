# XDef元模型定义语言

Nop平台中所有的DSL语言统一采用XML格式，而不是自定义的表观语法格式，这样可以简化DSL设计并提供统一的IDE开发工具。具体来说，所有的DSL采用统一的XDef元模型定义DSL的具体语法（XML的结构），
然后利用Nop平台内置的一系列机制自动生成代码，实现DSL的解析、验证等功能。

XDef元模型文件的作用类似于XSD(XML Schema Definition)文件，都是为XML格式增加语法约束。但是XDef相比于XSD更加简单易用，而且提供了更加强大的约束能力。

## XDef语法示例

我们来看一个简单的工作流DSL定义：工作流包含多个步骤，每个步骤完成后指定下一个可执行的步骤。

```xml
<workflow name="Test" x:schema="/nop/schema/my-wf.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <steps>
        <step id="a" displayName="StepA" next="b">
            <source>
                <c:script>
                    import app.MyHelper;
                    MyHelper.doSomething();
                </c:script>
            </source>
        </step>

        <step id="b" displayName="StepB" joinType="and" />
    </steps>
</workflow>
```

对应的元模型为

```xml

<workflow name="!string" x:schema="/nop/schema/xdef.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <steps xdef:body-type="list" xdef:key-attr="id">
        <step id="!string" displayName="string" internal="!boolean=false"
              joinType="enum:io.nop.wf.core.model.WfJoinType" next="string">
            <source xdef:value="xpl"/>
        </step>
    </steps>
</workflow>
```

首先我们看到，XDef元模型与它所描述的模型之间是一种同态关系，简单的说，将模型XML中的值替换成类型描述符就可以得到XDef元模型。

* `name="!string"` 表示`name`属性为`string`类型，字符`!`表示属性值不能为空。
* `xdef:body-type="list"` 表示节点解析后对应于列表类型，`xdef:key-attr="id"`表示列表中每个元素都必须具有一个`id`属性，通过`id`属性可以区分不同的元素。
* `internal="!boolean=false"` 表示`internal`属性不为空，类型为`boolean`，缺省值为`false`
* `joinType="enum:io.nop.wf.core.model.WfJoinType"` 表示`joinType`属性的值为`WfJoinType`类型，它是一个枚举值。
* `xdef:value="xpl"` 表示`source`节点的内容(包含直接的文本内容以及所有的子节点)为Xpl模板语言的代码段，解析后可以直接得到一个`IEvalAction`对象（类似于JavaScript中的`Function`对象）。

xdef文件中的所有属性（除去`xdef`名字空间以及`x`名字空间中的内置属性）的值类型都是`def-type`类型，它的格式为 `(!~#)?{stdDomain}:{options}={defaultValue}`。

* `!`表示必填属性，`~`表示内部属性，`#`表示可以使用编译期表达式
* `stdDomain`是比数据类型更严格的格式限制，例如`stdDomain=email`等，具体值参见字典定义[core/std-domain](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/src/main/resources/_vfs/dict/core/std-domain.dict.yaml)
* 某些`def-type`定义需要`options`参数，例如`enum:xxx.yyy`，通过`options`来设置具体的字典名称
* 可以为属性指定缺省值

## XDSL公共语法

在XML的根节点上必须通过`x:schema`属性引入元模型定义。例如`x:schema="/nop/schema/my-wf.xdef"`表示模型由`my-wf.xdef`元模型来约束。

Nop平台中所有的DSL语言都具有一些公共的属性和子节点，相当于是为所有DSL引入一些公共的语法，`x:schema`属性就是这个公共语法的一部分。这些公共语法在`xdsl.xdef`
元模型中定义，所以我们要在根节点上通过属性`xmlns:x="/nop/schema/xdsl.xdef"`表示x名字空间对应于DSL公共语法空间。具体介绍参见
[xdsl.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/xdsl.xdef)和
[XDSL：通用的领域特定语言设计](https://zhuanlan.zhihu.com/p/612512300)

XDef元模型定义语言的能力足够强大，它可以被用于描述XDef元模型自身，具体参见[xdef.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/xdef.xdef)

在`xdef.xdef`这个元元模型定义文件中，`xdef`名字空间必须被看作是普通属性空间，不能被解释为XDef元属性，所以在根节点上我们增加了属性定义`xmlns:meta="/nop/schema/xdef.xdef"`，使用`meta`名字空间来表达元属性。

```xml
<workflow xmlns:meta="/nop/schema/xdef.xdef">
    <steps meta:body-type="list" meta:key-attr="id">
        ...
    </steps>
</workflow>
```

等价于

```xml
<workflow xmlns:xdef="/nop/schema/xdef.xdef">
    <steps xdef:body-type="list" xdef:key-attr="id">
        ...
    </steps>
</workflow>
```

## 复用节点定义

在xdef文件中可以通过`xdef:ref`来引用已有的元模型定义。

1. 引入外部xdef文件

```xml
<form id="!string" xdef:ref="form.xdef" />
```

2. 引用内部节点
   在任意节点上可以增加`xdef:name`属性，将它标记为命名节点。然后就可以通过`xdef:ref`来引用。

```xml
<steps>
    <step id="!string" xdef:name="WorkflowStepModel">
        ...
    </step>

    <join id="!string" xdef:ref="WorkflowStepModel" xdef:name="WfJoinStepModel">
    </join>
</steps>
```

> 注：目前因为实现上的原因，`id`等作为集合元素唯一区分的属性需要被重复，而其他属性则可直接引用自其他节点，无需重复定义。

在代码生成的时候，`xdef:name`会被看作是节点对应的Java类名，`xdef:ref`会被看作是当前节点类的基类。

`xdef:ref="WorkflowStepModel" xdef:name="WfJoinStepModel"`对应于代码生成 `class WfJoinStepModel extends WorkflowStepModel`

为了简化节点复用，XDef语言还规定了一种特殊的、仅用于复用的特殊节点`xdef:define`，例如

```xml
<workflow>
    <xdef:define xdef:name="WorkflowStepModel" id="!string">
        ....
    </xdef:define>

    <steps xdef:body-type="list" xdef:key-attr="id">
        <step xdef:ref="WorkflowStepModel" id="!string"/>
    </steps>
</workflow>
```

`xdef:define`是定义一个可重用的部分，相当于定义基类，然后在节点上可以通过`xdef:ref`来继承这个基类。`xdef:name`相当于是基类的类名。

## 集合节点定义

除了上面介绍的`xdef:body-type="list"`来表示集合节点之外，xdef语言还提供了一种简化的集合节点定义方式: 使用`xdef:unique-attr`表示集合元素的唯一表示属性。

```xml
<arg name="!string" xdef:unique-attr="name" value="any" />
```

具有`xdef:unique-attr`属性的节点会被解析为集合属性，属性名一般为 `节点名驼峰变换+'s'`，比如`<task-step xdef:unique-attr="id">`对应于`taskSteps`。
我们也可以通过`xdef:bean-prop`属性来指定对应的属性名，例如可以指定`xdef:bean-prop="taskStepList"`。

```xml
<!--
  以下 DSL 定义等价于代码：
  bp.taskSteps.add({id: 'a', displayName: 'A'})
  bp.taskSteps.add({id: 'b', displayName: 'B'})
-->
<task-step id="a" displayName="A"></task-step>
<task-step id="b" displayName="B"></task-step>
```

使用`xdef:body-type="list"`方式来定义集合属性的好处在于，它允许集合中包含不同类型的子节点，例如

```xml

<steps xdef:body-type="list" xdef:key-attr="name" xdef:bean-sub-type-prop="type" xdef:bean-child-name="step"
       xdef:bean-body-type="List&lt;io.nop.wf.core.model.WfStepModel>">
    <step name="!string" xdef:bean-tag-prop="type" />
    <join name="!string" xdef:bean-tag-prop="type" />
</steps>
```

* `xdef:bean-body-type`用于指定生成的集合属性类型名
* `xdef:bean-child-name="step"`表示自动为模型对象增加`getStep(String name)`方法，用于按照唯一标识属性来获取子节点
* `xdef:bean-tag-prop="type"`表示节点的标签名称（`step`、`join`）在json序列化时将被解析为`type`属性的值
* `xdef:bean-sub-type-prop="type"`表示json反序列化的时候，根据`type`属性来确定子节点类型

```xml
<!--
  以下 DSL 定义转换为 JSON 后的结构为：
  {
    "steps": [{
      "type": "step",
      "name": "a"
    }, {
      "type": "join",
      "name": "b"
    }]
  }
-->
<steps>
    <step name="a"/>
    <join name="b"/>
</steps>
```
