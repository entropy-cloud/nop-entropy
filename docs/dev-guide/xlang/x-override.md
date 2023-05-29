# 可逆计算理论中的Delta合并算法

可逆计算理论明确指出全量是差量的特例，我们可以基于差量的概念重构整个软件生成过程。为了最大化Delta差量概念的价值，差量之间的运算应该是可以由软件自动完成，
而无需人工介入。在Nop平台中，我们定义了一个可以保持节点顺序的自动化差量合并算法，所有的树状结构的对象都采用这个算法实现差量合并。

# 合并算子 x:override
节点合并时缺省采用merge模式：
1. 节点属性按名称进行覆盖
2. 如果是不允许重复的子节点，则按照名称进行覆盖
3. 如果是允许重复的子节点，则按照唯一键属性进行覆盖

例如:
````xml
<entity name="test.MyEntity" tableName="MY_ENTITY">
    <comment>注释内容</comment>
    <columns>
        <column name="phone3" label="xx" />
    </columns>
</entity>
````

entity节点下只允许一个comment子节点，以及一个columns子节点，所以合并的时候，这两个子节点都是按照名称进行合并。然后递归处理这两个子节点的合并情况时，
按照XDef元模型中的定义，columns的子节点是允许重复的，所以它将按照name这一唯一标识属性进行合并。在上面的例子中，phone3字段的label属性会被定制配置自动覆盖。

`x:override`的所有值都在[XDefOverride](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/src/main/java/io/nop/xlang/xdef/XDefOverride.java)类中定义。

我们常用的合并模式有如下几种：
* remove: 删除节点
* merge: 合并属性和子节点
* replace: 整体替换
* merge-replace: 合并属性，替换子节点或者content
* append: 合并属性，追加子节点或者content
* prepend: 合并属性，前插子节点或者content
* bounded-merge: 基本与merge类似，但是它额外限定了合并结果的范围，只保留基础模型和派生模型中都存在的子节点。

例如:
````xml
<grid>
    <cols x:override="bounded-merge">
        <col id="a" width="30"/>
        <col id="b" width="20"/>
        <col id="c" width="50"/>
    </cols>
</grid>
````
如果cols的合并模式设置为bounded-merge，则合并后只会具有三个节点，所有仅在基础模型中存在，但是在派生模型中不存在的列都会被删除。
如果不配置`x:override`属性或者配置`x:override=merge`，则表示新增或者覆盖子节点，最终cols的子节点个数可能不是3，而是大于3。

# 保序算法
合并列表类型的子节点时会尽量保持节点在派生模型以及基础模型中定义的顺序。比如如果我们想在节点a的后面追加一个节点b，则可以使用如下配置

````xml
<cols>
    <col id="a" />
    <col id="b" width="30" label="test" />
</cols>
````

合并算法规定，如果节点a在基础模型中存在，且节点b是一个新节点，则它会紧贴着节点a插入到结果集中。合并算法会严格保持派生模型中指定的节点顺序，同时尽量保持
在基础模型中的节点顺序。具体来说，合并列表时会先将两个列表顺序拼接在一起，然后再根据节点重叠情况将列表分成几个连续的区块，然后再重排区块。

具体示例如下：
1. `a=[a1,a2,a3,a4,a5]`与`b=[b1, a2, b3]` 合并， 先得到 `all = [a1,a2,a3,a4,a5, b1,a2,b3]`, 然后发现a2重复，需要以a2元素为基准移动a中的元素
得到`[a1,b1,a2,b3,a3,a4,a5]`，b3在b中紧接着a2，所以在移动后的序列中也紧接着a2。 

2. 如果a与`b=[a1, b1, a3, b3]`合并，则先得到`[a1,a2,a3,a4,a5, a1,b1,a3,b3]`, 发现a1,a3重复，移动后得到[a1,b1,a2,a3,b3,a4,a5]。

3. 基准元素用于定位时可以理解为代表它以及它的后续元素（直到遇到另外一个基准元素为止）。如果a与`b=[a3,b1,a1]`合并，则先得到`[a1,a2,a3,a4,a5, a3,b1,a1]`,
现在b中a3和a1颠倒了顺序，先移动a1得到`[a3,a4,a5, a3,b1,a1,a2]`，再移动a3得到`[a3,b1,a4,a5,a1,a2]`

测试用例参见 [TestMerge.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-core/src/test/java/io/nop/core/lang/json/TestMerge.java)

# 兄弟节点之间的原型继承

`x:override`配置派生模型与`x:extends`引入的基础模型之间的合并算子。但是除了与外部的基础模型继承内容之外，很多情况下一个列表的子节点之间可能也存在着相似性。
例如编辑表单的布局可能与新增表单以及差量表单完全一致，但是也有可能随着需求变化逐渐变得不一致。有些字段可能不允许新建或者修改，但是在查看页面上可以查看。
使用`x:prototype`和`x:prototype-override`我们可以指定从哪个兄弟节点继承，以及继承时所使用的合并算子。

````xml
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
````

原型合并在普通的Delta合并之后进行。在上面的例子中，ext.forms.xml从基础模型中继承了add表单的layout配置。我们首先通过`x:override="remove"`删除了
继承来的layout配置，然后再通过`x:prototype="view"`表示与兄弟节点view表单合并，最终add表单中的layout是从view表单继承得到的layout。

# 子节点上的`x:extends`
除了在DSL文件的根节点上可以写`x:extends`表示可以从指定的基础模型继承之外，在子节点上也可以使用x:extends。

> 需要在子节点的XDef元模型上配置 `xdef:support-extends=true`才允许该子节点使用`x:extends`机制。

````xml
<forms x:extends="base.forms.xml">
    <form id="add" x:extends="default.form.xml" />
</forms>
````

从根节点的base.forms.xml基础模型中我们有可能继承得到一个add表单，同时我们又通过`x:extends`指定了add表单从default.form.xml继承。而在
default.form.xml中，它可能继续使用`x:extends`机制从其他文件继承。如果完整的考虑所有继承节点的情况，则合并算法的实现会变得相当复杂，
所以在Nop平台的Delta合并算法中我们做了一点简化，规定如果节点上明确设置了`x:extends`，则会自动忽略从根节点上继承得到的节点内容。例如上面的例子中，
从base.forms.xml中继承得到的add form会被自动忽略。

