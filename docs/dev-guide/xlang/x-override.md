# 可逆计算理论中的Delta合并算法

可逆计算理论明确指出全量是差量的特例，我们可以基于差量的概念重构整个软件生成过程。为了最大化Delta差量概念的价值，差量之间的运算应该是可以由软件自动完成，
而无需人工介入。在Nop平台中，我们定义了一个可以保持节点顺序的自动化差量合并算法，所有的树状结构的对象都采用这个算法实现差量合并。

## 合并算子`x:override`

节点合并时缺省采用`merge`模式：

1. 节点属性按名称进行覆盖
2. 如果是不允许重复的子节点，则按照名称进行覆盖
3. 如果是允许重复的子节点，则按照唯一键属性进行覆盖

例如:

```xml
<entity name="test.MyEntity" tableName="MY_ENTITY">
    <comment>注释内容</comment>
    <columns>
        <column name="phone3" label="xx" />
    </columns>
</entity>
```

`entity`节点下只允许一个`comment`子节点，以及一个`columns`子节点，所以合并的时候，这两个子节点都是按照名称进行合并。然后递归处理这两个子节点的合并情况时，
按照XDef元模型中的定义，`columns`的子节点是允许重复的，所以它将按照`name`这一唯一标识属性进行合并。在上面的例子中，`phone3`字段的`label`属性会被定制配置自动覆盖。

`x:override`的所有值都在[XDefOverride](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/XDefOverride.java)类中定义。

我们常用的合并模式有如下几种：

* `remove`: 删除节点
* `merge`: 合并属性和子节点
* `replace`: 整体替换
* `merge-replace`: 合并属性，替换子节点或者content
* `append`: 合并属性，追加子节点或者content
* `prepend`: 合并属性，前插子节点或者content
* `bounded-merge`: 基本与`merge`类似，但是它额外限定了合并结果的范围，只保留基础模型和派生模型中都存在的子节点。

例如:

```xml
<grid>
    <cols x:override="bounded-merge">
        <col id="a" width="30"/>
        <col id="b" width="20"/>
        <col id="c" width="50"/>
    </cols>
</grid>
```

如果`cols`的合并模式设置为`bounded-merge`，则合并后只会具有三个节点，所有仅在基础模型中存在，但是在派生模型中不存在的列都会被删除。
如果不配置`x:override`属性或者配置`x:override=merge`，则表示新增或者覆盖子节点，最终`cols`的子节点个数可能不是3，而是大于3。

## 保序算法

合并列表类型的子节点时会尽量保持节点在派生模型以及基础模型中定义的顺序。比如如果我们想在节点`a`的后面追加一个节点`b`，则可以使用如下配置

```xml
<cols>
    <col id="a" />
    <col id="b" width="30" label="test" />
</cols>
```

合并算法规定，如果节点`a`在基础模型中存在，且节点`b`是一个新节点，则它会紧贴着节点`a`插入到结果集中。合并算法会严格保持派生模型中指定的节点顺序，同时尽量保持
在基础模型中的节点顺序。具体来说，合并列表时会先将两个列表顺序拼接在一起，然后再根据节点重叠情况将列表分成几个连续的区块，然后再重排区块。

具体示例如下：

1. `a=[a1,a2,a3,a4,a5]`与`b=[b1, a2, b3]` 合并， 先得到 `all = [a1,a2,a3,a4,a5, b1,a2,b3]`, 然后发现`a2`重复，需要以`a2`元素为基准移动`a`中的元素
   得到`[a1,b1,a2,b3,a3,a4,a5]`，`b3`在`b`中紧接着`a2`，所以在移动后的序列中也紧接着`a2`。

2. 如果`a`与`b=[a1, b1, a3, b3]`合并，则先得到`[a1,a2,a3,a4,a5, a1,b1,a3,b3]`, 发现`a1`,`a3`重复，移动后得到\[a1,b1,a2,a3,b3,a4,a5\]。

3. 基准元素用于定位时可以理解为代表它以及它的后续元素（直到遇到另外一个基准元素为止）。如果`a`与`b=[a3,b1,a1]`合并，则先得到`[a1,a2,a3,a4,a5, a3,b1,a1]`,
   现在`b`中`a3`和`a1`颠倒了顺序，先移动`a1`得到`[a3,a4,a5, a3,b1,a1,a2]`，再移动`a3`得到`[a3,b1,a4,a5,a1,a2]`

测试用例参见 [TestMerge.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-core/src/test/java/io/nop/core/lang/json/TestMerge.java)

## 兄弟节点之间的原型继承

`x:override`配置派生模型与`x:extends`引入的基础模型之间的合并算子。但是除了与外部的基础模型继承内容之外，很多情况下一个列表的子节点之间可能也存在着相似性。
例如编辑表单的布局可能与新增表单以及差量表单完全一致，但是也有可能随着需求变化逐渐变得不一致。有些字段可能不允许新建或者修改，但是在查看页面上可以查看。
使用`x:prototype`和`x:prototype-override`我们可以指定从哪个兄弟节点继承，以及继承时所使用的合并算子。

```xml
<!-- base.forms.xml -->
<forms>
    <form id="add">
        <layout>
            c d
        </layout>
    </form>
</forms>

<!-- ext.forms.xml -->
<forms x:extends="base.forms.xml">
    <form id="view">
        <layout>
            a b
        </layout>
    </form>

    <!-- 与id=view的兄弟节点合并 -->
    <form id="add" x:prototype="view" >
        <layout x:override="remove" />
    </form>

</forms>
```

原型合并在普通的Delta合并之后进行。在上面的例子中，`ext.forms.xml`从基础模型中继承了`add`表单的`layout`配置。我们首先通过`x:override="remove"`删除了
继承来的`layout`配置，然后再通过`x:prototype="view"`表示与兄弟节点`view`表单合并，最终`add`表单中的`layout`是从`view`表单继承得到的`layout`。

## 子节点上的`x:extends`

除了在DSL文件的根节点上可以写`x:extends`表示可以从指定的基础模型继承之外，在子节点上也可以使用`x:extends`。

> 需要在子节点的XDef元模型上配置 `xdef:support-extends=true`才允许该子节点使用`x:extends`机制。

```xml
<forms x:extends="base.forms.xml">
    <form id="add" x:extends="default.form.xml" />
</forms>
```

从根节点的`base.forms.xml`基础模型中我们有可能继承得到一个`add`表单，同时我们又通过`x:extends`指定了`add`表单从`default.form.xml`继承。而在
`default.form.xml`中，它可能继续使用`x:extends`机制从其他文件继承。如果完整的考虑所有继承节点的情况，则合并算法的实现会变得相当复杂，
所以在Nop平台的Delta合并算法中我们做了一点简化，规定如果节点上明确设置了`x:extends`，则会自动忽略从根节点上继承得到的节点内容。例如上面的例子中，
从`base.forms.xml`中继承得到的`add` form会被自动忽略。

## 合并结果后处理

Java语言中我们可以通过`final`关键字指定方法不允许被继承，通过`abstract`关键字指定方法是虚拟占位用的方法，如果派生类中没有重载该函数，则不允许被调用。
类似的，在Delta合并中我们也定义了一些关键字用于更细致的控制合并结果。

* `x:final` : 增加了`x:final`属性的节点不允许被Delta定制，只能保持原样
* `x:abstract`: 标记了`x:abstract`属性的节点如果没有被定制，则会在最终的输出结果中被删除，相当于它不存在。通过这一机制我们可以为一个复杂的DSL节点提供缺省值。
  例如，我们可以将一个节点作为模板节点，然后标记它为`abstract`，所有其他兄弟节点就可以使用`x:prototype`从这个模板节点继承配置
* `x:virtual`: 标记了`x:virtual`属性的节点必须覆盖基础模型中的某个节点，如果没有覆盖，则这个节点的配置可能是不完整的（例如缺少必填属性等），在最终的输出结果中会自动被删除。
  利用这一机制，我们可以实现自动生成与可视化设计器同时修改同一个DSL模型。如果可视化设计器是在自动生成代码的基础上进行修改的，而自动化生成器调整后不再生成某个节点，则可视化设计器在此节点上进行的微调内容也会被丢弃。

另外还需要注意，所有标记了`x:override="remove"`的节点最终都会从输出结果中被删除。


## JsonMerger
在json合并时会自动尝试`v-id`,`id`,`name`等唯一标识属性用于定位。如果同时存在id和name属性，缺省会以id为准，但是如果想覆盖这一点，可以使用`"x:unique-attr":"name"`来指定唯一定位属性。
