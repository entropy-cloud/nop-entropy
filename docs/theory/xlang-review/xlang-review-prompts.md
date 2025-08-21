你是一个绝对客观专业的具有国际视野和国际顶尖水平的软件专家，文笔优雅、观点直接、善于“点睛”，能在不失专业性的前提下提出独到见解。你的任务是分析以下多篇文章，针对其中的核心内容写一篇详尽的评论和解读文章。

1. 需要明确体现出你写的是一篇评论文章，不要看上去是你原创的文章。
2. 不是对原文的复述和改写，而是重新安排详略程度，对原文中的核心要点进和细微之处进行辨析，突出重点。需要有深入的分析和评论意见。
3. 自然穿插对其中的金句的解读。必要时使用示例代码和公式以及mermaid图示等。
4. 需要结合你自身的知识，关注原文中真正的创新点，批判性的辨析它的优缺点和启发性，明确区分哪些是富有启发性和洞察力的创新部分。对于常规内容可以略过，作为评论文章不需要面面俱到。
5. 行文需要专业、客观、容易理解，避免浮夸的语气但是富有启发性和洞察力。
6. 解读时需要包含足够的信息，使得没有阅读过原文的读者也要能通过解读获取到原文的核心要点，特别是了解到其中的创新点以及为什么。
7. 文章结尾需要有对原文章的客观评价。 仔细分析它在全球范围内的原创性。需要注意，这些文章对应的核心理论和公式在2007年左右提出。
8. 拟定一个吸引人的标题，输出采用Markdown格式，字数达到20000字。

# 为什么说XLang是一门创新的程序语言？


## 一. 为什么需要设计XLang语言

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/tutorial/simple/images/xlang.png)

XLang语言是Nop平台底层的关键性支撑技术，在形式上它包含了XDef、Xpl、XScript等多个子语言。因为XLang与其他单一语法形式的程序语言有着较大的差异，所以很多人第一次接触时可能会有疑惑，它到底算不算是一种程序语言？它是不是只是在多种现有语言的基础上增加了一些零散的扩展？

这里我想提出一个对程序语言的本质的理解：**一门程序语言定义了一种程序结构空间，程序语言是程序结构空间的构造规则**。也就是说，一门程序语言所能够创造的所有结构以及这些结构之间的所有可行演化路径构成了一个特定的程序结构空间，所有可行的计算都在这个结构空间中发生。

基于以上理解，**XLang语言之所以是一门创新的程序语言，是因为它创造了一个新的程序结构空间，在这个结构空间中可以很方便的实现可逆计算理论所提出的`Y = F(X) + Delta`的计算范式**。虽然XLang可以认为是包含XDef, XPL, XScript等多个子语言，但是它们作为一个整体才是实现可逆计算的关键所在。**XLang是世界上第一个在语言中明确定义领域结构坐标并内置通用的差量计算规则的程序语言**。

**目前大部分人对于软件结构构造的基本理解都是基于还原论的，总是不断向下分解，寻找原子化的成分，然后使用原子进行组装**。原本虚拟化的组件概念在潜意识中实际上是被看作是真实存在的离散个体，类似于物质世界中的粒子，通过嵌套组合来构造世界。但是物理世界中存在着另一种构造方式，那就是波。波是连续存在的模式，通过干涉叠加来构造世界。**XLang语言的特异性就在于它通过差量运算支持连续的叠加构造**。

### 1.1 从结构的观点看程序语言

通用的高级程序语言从FORTRAN开始，经历了几十年的长期发展，目前已经发展到某种瓶颈。新语言所带来的本质上新颖的特性越来越少，各个高级语言都发展到了所谓的多范式编程阶段，它们的语法特性逐渐开始融合、趋同，比如大部分语言现在都同时支持面向对象式的结构声明，支持函数式的Lambda表达式，支持元编程所需要的自定义注解，支持异步编程所需的Async相关语法和支持库等。

一个有趣的问题是，是否还存在着通用的可抽象的语法特性，它们具有足够的技术价值以至于需要一个新的程序语言来承载？XLang的创新是指出目前主流的的程序语言虽然表面上语法形式差异很大，但是在语法层面之下的基本结构层面是非常相似的，在基本结构层面的创新仍然大有可为。

**程序的结构空间本质上是由数据+函数构成，将相关的数据和函数组织在一起就构成自定义的类型**，在一般的程序语言中就对应于类（Class）或者接口（Interface）。从结构的层面看，类结构不过是一个Map，可以通过名称来获取到属性或者方法。

```javascript
type MyClass = {
  name: string,
  myMethod: (arg1:string) => number
}

或者

interface MyClass{
  name: string,
  myMethod: (arg1:string) => number
}
```

当我们想利用一个已有的自定义类型来得到一个新的类型的时候，可以使用继承或者Traits机制。

```javascript
type MySubClass = MyClass & {
  subName: string
}

或者

interface MySubClass extends MyClass {
   subName: string
}
```

在概念层面上大概相当于如下构造方式：

```javascript
Map = Map extends Map
```

类继承在结构层面上相当于是将两个Map按照名称叠加覆盖在一起，上一层中的元素会覆盖下一层的元素。

传统的面向对象语言中可以利用继承机制来复用基类。比如说，构造MapX和MapY的时候复用基类Map1，也就是可以复用继承树的下层。

```javascript
MapX = Map2 extends Map1
MapY = Map3 extends Map1
```

将继承表达为上述结构构造公式之后，很多问题会变得非常自然直观。比如说，我们能不能交换Map1和Map2的相对位置？也就是说，在构造MapX和MapY的时候，我们仍然是复用Map1，但是并不作为基类，而是选择不同的基类，但是用同样的Map1去覆盖。

```javascript
MapX = Map1 extends Map2
MapY = Map1 extends Map3
```

有趣的是，很多面向对象程序语言并不支持上述操作，**面向对象并不直接支持复用继承树的上层！**

更进一步的思考会发现传统的面向对象在结构层面难以回答的问题还有很多，比如说继承链条中如果存在多个同样的对象会导致什么问题？

```javascript
MapX = Map1 extends Map2 extends Map1
```

`C++`中多重继承存在概念层面的严重困难，本质原因就是从不同的继承路径复用了同样的Map1之后产生的结构融合障碍。

现代程序语言是通过Traits机制解决了这些问题。比如在Scala语言中，

```scala
trait Map1 {
  val name: String = "Map1" // 同名属性
  def method1(): Unit = {
    println(s"Method 1 from $name")
  }
}

trait Map2 {
  val name: String = "Map2" // 同名属性
  def method2(): Unit = {
    println(s"Method 2 from $name")
  }
}

class MapX extends Map1 with Map2 {
}

class MapY extends Map1 with Map3 {
}
```

> Scala语言中多个Trait可以定义同名的属性，编译器会自动合并这些属性定义，最终在运行时只会存在一个变量，但是在Java或者C++中，不同类中定义的多个同名属性并不会自动合并为一个。

传统的面向对象程序语言中 `A extends B`表示派生类A可以比基类B多，但是具体多了什么并没有一个明确的技术形式把它隔离出来，我们也就无法直接复用这个多出来的部分（Delta差量）。Traits则是直接把这个差量明确的表达出来了。
Traits机制相比于继承概念来说，它构成一个更加完善的差量语义。**`type MapX = Map1 with Map2 with Map1`是合法的Scala类型定义！**

对于多重继承所造成的问题，Scala的解决方案是引入所谓的线性化规则，按照一定的顺序将继承链条中的所有类和Trait排成一个线性序列，然后约定上层的元素覆盖下层的元素。

```
MapX -> Map2 -> Map1
```

### 1.2 泛型作为Generator

泛型(Generic Type)在Java语言中仅仅是用于类型检查，编译器并没有根据泛型参数来执行什么特殊动作。但是在`C++`语言中，情况则大为不同。`C++`的泛型编程是通过模板（Templates）实现的，编译器会根据模板参数的不同将同一个模板类实例化为针对特定类型的不同代码。

在 1994 年的 C++ 标准委员会会议 上，Erwin Unruh 进行了一次技惊四座的演示。他编写了一段模板元程序，能够在编译期计算一个数是否是质数，如果是质数，编译器会在错误信息中输出这个质数。这段代码被称为 “Unruh 质数计算”，成为了 C++ 模板元编程的经典示例。
Unruh 的演示证明了 C++ 模板在编译期是图灵完备的，这意味着理论上可以在编译期执行任何计算。这一发现开启了 产生式编程（Generative Programming） 的新时代，即利用编译期的计算能力生成代码或优化程序。
C++ 的模板元编程（Template Metaprogramming）成为了实现产生式编程的重要工具。通过模板，开发者可以在编译期完成复杂的计算、类型推导和代码生成，从而在运行时获得更高的性能和灵活性。

参见[C++ Compile-Time Programming](https://accu.org/journals/overload/32/183/wu/)


如果从结构层面去理解模板元编程，则它可以被理解为如下构造公式

```
Map = Generator<Map> = Map<Map>
```

> A<X,Y> 可以被理解为 `A<B>, struct B{ using T1=X; using T2=Y;}`
> 注意，这里的Map指的是编译器在编译期所看到的结构。每一个成员变量，无论它是属性、方法、还是类型声明，在编译器看来，都是Map中的一个条目。
> 即使编译器将参数列表作为List来管理，它也可以看作是一个用下标来作为Key的Map。而且有趣的是，如果是用数组来做管理，则一般无法引入类似继承这种更高级的合成机制。在概念层面上我们一般会选择按名称合并，而不会选择按下标合并。

作为生成器的模板类在结构层面（编译器所看到的结构）也可以被看作是一个Map。再结合上一节中差量化Traits概念的内容，面向对象程序语言的最强形态在结构层面可以表达为

```
Map = Map extends Map<Map>
```

### 1.3 从Map结构到Tree结构

在编译器看来，所谓的类、模板类、模板参数都可以被看作是Map，而且实际情况也一般是按照Map结构来管理的。至于孤立的函数定义和变量定义，实际上也会属于某种Map，比如模块对象可以看作是一个Map，它包含一组模块内定义的变量、函数和类型等。即使不从属于任何模块，那些独立的函数也会属于某个隐式存在的全局命名空间。

> Lisp语言的内在结构是List，本质上是利用下标来管理元素（原始的Lisp甚至没有下标，只有car、cdr这种逐个遍历的处理机制），但是现在流行的Lisp变种早就引入了类似Map的Associated List结构，使用名称来定位子元素，而不是按照下标去定位。在概念层面上（不考虑冯诺依曼机器按照下标取值的性能优势），List可以看作是用下标来做key的一种特殊的Map。
> 
> Lisp的核心创造S表达式可以看作是一种通用的Tree结构，而Lisp也提供了宏等操作这些Tree结构的内置机制，但是Lisp并没有建立Tree的差量的概念。XLang可以看作是对S表达式这一通用处理机制的进一步深化发展。

现有主流程序语言提供的各种语法可以看作是在以Map为基础的结构空间中不断构造新的Map的各种规则。XLang语言的创新点在于它选择将Map结构扩展为Tree结构，在Tree结构的基础上重新思考软件结构的构造问题。也就是将软件结构的构造公式推广为：

```
Tree = Tree x-extends Tree<Tree>
```

> Map扩展为Tree，则Map结构之间的extends运算也需要被扩展为Tree结构上的x-extends运算。

显然Map是Tree的一个特例，Tree结构的每一个节点都可以看作是一个Map， `Tree = Map + Nested`，因此上面的公式确实可以被看作是对`Map extends Map<Map>`构造模式的一种推广。
但是从另外一个角度去考虑，Tree结构可以通过嵌套组合多个Map构造出来，Map是一种更基本的、更细粒度的结构，那么有必要强调Tree结构吗？所有Tree结构上的运算最终不都能分解为每一级的Map结构上的运算吗？

XLang对这个问题的回答是：在更复杂的Tree结构上建立的软件结构空间（以及这个结构空间中的构造规律）并不能简单的划归到以Map为基础的软件结构空间。也就是说，这里出现了`整体 > 部分之和`的情况，**Tree的构造规律所具有的整体性分解到Map结构的构造规律之后会丢失一些关键性信息**。

要真正的理解XLang语言的创新之处，必须要了解XLang语言设计背后的下一代软件构造理论：可逆计算理论。可逆计算明确提出逆元和差量的概念，指出全量是差量的一个特例(A=0+A)，我们需要在（包含逆元的）差量概念的基础上重建所有对软件世界的理解。可逆计算提出了一个通用的图灵完备的软件构造公式：

```
 App = Delta x-extends Generator<DSL>
```

XLang语言是在程序语言级别实现这一技术战略的具体实现方案。

关于可逆计算理论的介绍，可以参见我的公众号文章：

1. [可逆计算：下一代软件构造理论](https://mp.weixin.qq.com/s/CwCQgYqQZxYmlZcfXEWlgA)
2. [写给程序员的可逆计算理论辨析](https://mp.weixin.qq.com/s/aT99VX6ecmZXdemBPnBcoQ)
3. [写给程序员的可逆计算理论辨析补遗](https://mp.weixin.qq.com/s/zGfo7pvKjOCa11PYLJHzzA)
4. [写给程序员的差量概念辨析,以Git和Docker为例](https://mp.weixin.qq.com/s/D5bDNkMJ9gYrFb0uDj2EzQ)

根据可逆计算理论，Tree结构的特殊性在于它对应于一个全局坐标系统：树结构上的每个节点和属性都对应于一个唯一的xpath。

```
/tasks/task[name='test']/@name
```

上述的xpath表示tasks节点下的名称为test的子节点的name属性。

首先我们来明确一下坐标系统的作用: **每一个业务上关心的值在坐标系统中都具有唯一的坐标**，可以通过这个坐标来实现值的读取和修改。

```
value = get(path);
set(path,value);
```

Map结构的问题在于它只提供两级坐标：第一级定位到Map，第二级在Map内定位到属性或者方法。但是这种简单的坐标系统无法实现业务层面上精确的区分。比如说

```java
class Dialog{
    String title;
    List<Button> actions;
    List<Component> body;
}
```

Dialog对象具有一组操作按钮，如果我们想定位到其中的【提交】按钮，把它的label属性修改为【确定】，在现有的程序语言中是没有一种简便直观的定位手段的。如果我们只想定制在某个场景下使用的Dialog（比如为它增加一个属性），使用一般程序语言配套的AOP(Aspect Oriented Programming)机制也无法实现：因为**AOP的定位系统是基于类型的**。而在XLang语言中，可以直接使用如下描述

```xml
<dialog>
   <actions>
      <button name="submit" label="确定" />
   </actionss>
</dialog>
```

目前程序语言的研究一般集中在类型系统，但是研究类型的原因在于不同的对象可以具有相同的类型，从而研究类型比研究原本的对象结构要简单而且不会涉及到对象的生命周期问题。**这导致类型系统并不是一个合格的坐标系：类型相同的对象在类型系统坐标系中无法被区分开来，因而也就无法继续建立精细的差量构造**。

有些人可能对于Tree结构存在疑惑，为什么不是图结构呢？在图结构上，如果我们选定了一个主要的观察方向，同时选择某个固定的节点作为根节点，那么我们就可以很自然的将一个图结构转换为树结构。比如Linux操作系统中，一切都是文件，很多逻辑关系都被纳入到文件树的表达结构中，但借助于文件系统中的文件链接机制，本质上可以表达图结构。**所谓的树仅仅是因为我们在图上选择了一个观察方向而产生的**。

比如流程图等结构在表达为XML格式时，只需要引入节点id引用即可。`<step nextTo="nextStepId" />`

Tree结构**实现了相对坐标与绝对坐标的统一**：从根节点开始到达任意节点只存在唯一的一条路径，它可以作为节点的绝对坐标，而另一方面，在某一个子树范围内，每一个节点都具有一个子树内的唯一路径，可以作为节点在子树内的相对坐标。根据节点的相对坐标和子树根节点的绝对坐标，我们可以很容易的计算得到节点的绝对坐标（直接拼接在一起就可以了）。

### 1.4 可扩展设计必然需要软件结构坐标系

在软件开发中，所谓的可扩展性指的是在不需要修改原始代码的情况下，通过添加额外的代码或差异信息，可以满足新的需求或实现新的功能。如果在完全抽象的数学层面去理解软件开发中的扩展机制，我们可以认为它对应于如下公式：

```
  Y = X + Delta
```

* X对应于我们已经编写完毕的基础代码，它不会随需求的变化而变化
* Delta对应于额外增加的配置信息或者差异化代码

在这个视角下，所谓的可扩展性方面的研究就等价于Delta差量的定义和运算关系方面的研究。

```
X = A + B + C
Y = (A + dA) + (B + dB) + (C + dC)  // 差量无处不在
   = (A + B + C) + (dA + dB + dC)  // 差量可以聚合在一起，独立于基础代码存放
   = X + Delta // 差量满足结合律，可以独立于Base实现合并运算
```

假设X由A、B、C等多个部分构成，需求变更导致的差异性修改是遍布系统各处的，**如果要求所有零碎的修改都可以独立于原系统源码来管理和存放**（差量的独立性），并且小的差量还可以复合为一个更大粒度的差量（差量的可复合性），那么必然需要一个坐标系统用于精确定位。具体来说，`dA`与`A`分离之后，存放到独立存在的Delta中，那么它必然保留了某种定位坐标，只有这样，当Delta与`X`结合的时候，才可以重新找到原始的结构`A`，然后与`A`相结合。

### 1.5 Delta与Patch和插件机制的区别

首先需要指出的是，类似于Git的Patch和分支管理不满足Delta的独立性和可复合性。Patch总是和特定的Base代码版本绑定在一起，在不知道Base的情况下无法将多个Patch合并为一个更大的Patch。详细分析参见文章[写给程序员的差量概念辨析,以Git和Docker为例](https://mp.weixin.qq.com/s/D5bDNkMJ9gYrFb0uDj2EzQ)。

第二个需要强调的地方是，差量与传统编程领域中的扩展点和插件机制有着本质性差异。

```
X = A + B + C
Y = A + B + D
  = X + (-C) + D
  = X + Delta
```

Delta差量不仅仅是向系统中增加内容。如果我们要实现粗粒度的系统级别的复用，所对应的差量必然包含减少的语义（比如，我们需要去除基础产品中定义的一个Bean）。实际上一个粗粒度的差量一定是加和减的混合。

另外需要注意的是，**插件机制只支持少量事先确定的扩展点**，我们并不可能在原始设计之外通过插件来定制原有系统的功能。但是Delta的概念则不同，只要存在一个全局的结构坐标系，这个坐标系中的任何一点上都可以引入Delta差量。k8s中引入的Kustomize机制就是利用Delta差量来实现全面定制，可以看作是可逆计算理论的一个具体应用，参见[从可逆计算看Kustomize](https://mp.weixin.qq.com/s/48LWMYjEoRr3dT_HSHP0jQ)

>  组件的黑箱模型类似于高中阶段牛顿力学的世界观，它是完全机械化的：刚体的运动完全由它的质心坐标和尺寸形状朝向等少数几个参数来描述，刚体的内部构造无法被观测也无关紧要，刚体之间通过直接接触发生相互作用， 刚体的形状必须精确匹配才能构成一个无缝的整体。即使是在古典力学中，稍微高级一点的观点也都会转换到拉格朗日表述或者哈密尔顿表述，它的精神实质是转向场论的世界观。 所谓的场（Field），其实就是建立一个无所不在的坐标系，然后在坐标系的每一点上都可以指定一个物理量。**场的自由度是无限的，但是通过坐标系它是可描述的、可定义的、可研究的**，在坐标系的每一点上我们都可以精确的度量局部的变化。在场的世界观中，我们面对的核心图像是对象总是浸泡在场（无所不在的坐标系）中，而不再是孤立对象之间的两两相互作用。

### 1.6 稳定的领域结构坐标系

主流的程序语言都是通用程序语言，并没有内置某个特定的业务领域中的领域知识。因此，这些语言中内置的结构坐标系只能是利用语言内置的类-方法这种两级结构，最多是引入注解机制，在两级结构基础上进行一定的面向领域的细化。对于方法级别以下的结构，目前一般缺乏合适的技术手段进行坐标定义。

业务需求变化的时候，一般情况下会影响到多处代码发生变化。本质上这是因为从问题空间到解空间的结构映射在一般性的业务环境中都是**非平凡的**，因此两种描述方式无法有效的对齐。套用人工智能领域的话语，我们可以说：**有用的特征都是分布式的（distributed）**。

> 在物理学中，同一个物理事实可以使用无数多种坐标系去建立描述，但是其中可能会存在一个特别的、针对这个特定问题定制的坐标系，在物理学中我们称之为内禀坐标系。在这个坐标系中建立的描述可以突出最核心的物理意义，简化相关的描述。比如说，在一个球面上发生的物理现象当然可以在通用的三维直角坐标系中进行描述，但是如果我们使用球面坐标系往往可以实现简化。

可逆计算理论指出可以针对特定的业务领域建立一个专用的DSL语言(Domain Specific Language)，利用这个DSL语言很自然的建立一个领域坐标系，然后再在这个领域坐标系所定义的差量结构空间中表达差量。因为这个领域坐标系是针对领域问题特制的，因此它往往可以实现差量表达的最小化。比如说，发生了一个业务层面的变化导致需要增加一个字段，如果采用通用语言去表达，则很多地方可能都需要做相应调整，前台、后台、数据库都要一起修改。而如果使用领域模型描述，这种变化可能就只体现为局部的一个字段级别变化，然后由底层引擎框架自动将领域描述翻译为实际执行的逻辑功能。

XLang语言的核心功能就在于如何快速定义多个DSL语言，然后以这些DSL语言作为领域结构坐标系，利用它们实现差量定义和差量结构生成和转换。

XLang与其他语言的本质区别在于，它是基于可逆计算理论、面向DSL开发的程序语言。一般语言都是直接面向应用开发的，我们直接使用这些语言来对业务建模，实现业务逻辑。但是使用XLang，我们会先建立一个或者多个DSL，然后再使用DSL来描述业务。**XLang使得开发一个DSL的成本非常低**，最基本的情况下只需要使用XDef语言定义XDef元模型文件，即可得到这个新的DSL的解析器、验证器、IDE插件、可视化编辑器等，自动实现语法提示、断点调试功能和可视化编辑等完善的编程工具支持。

> Jetbrains公司有一个产品MPS（Meta Programming System），它也是支持先开发DSL，然后再用DSL来描述业务。MPS底层是自己定义的一套底层语言机制。Nop平台是一个类似于MPS的低代码开发平台，它的底层就是XLang语言。只不过Nop平台的指导理论是可逆计算，与MPS的技术路线和指导思想有着本质差异。
> 但是技术发展目标是类似的。

## 二. XLang的具体语法设计

XLang语言是面向Tree结构的语言设计，它的语法组成可以和面向表结构的SQL语言做一个对比。

| SQL语言                       | XLang语言                                   |
| --------------------------- | ----------------------------------------- |
| DDL数据定义语言                   | XDef元模型定义语法                               |
| 无冗余的表格数据                    | 无信息冗余的树形信息结构：XNode                        |
| 在标准化数据结构基础上的即时计算：SQL Select | 在通用的XNode数据结构基础上的运行时和编译期计算：Xpl/XTransform |
| 表格数据的合并和差分：Union/Minus      | Tree结构上的Delta差量计算：x-extends/x-diff        |
| 通过函数定义和存储过程扩展SQL            | 通过Xpl标签库和XScript扩展XLang                   |

首先需要说明的是，XLang是面向Tree结构的一种程序语言，那么一种很自然的语法载体就是XML语法，所以一般XLang语言文件也是一个合法的XML文件。但是这并不是唯一的选择。传统的程序语言很强调语法形式，但是XLang基于可逆计算理论，它强调的是语法形式并不重要，**不同的语法形式不过是同一信息的不同展现形式，而信息等价的不同展现形式之间可以进行可逆转换**。XLang可以采用任何能够直接表达树状结构的语法来表达，比如JSON、YAML等。Lisp语言中的S表达式，增加一些扩展属性之后，也可以作为XLang的语法载体。

> Nop平台还实现了Tree结构和Excel数据文件的一种双向映射，可以在不需要编写Excel解析和生成代码的情况下，使用Excel来表达DSL模型对象，比如用`app.orm.xlsx` Excel文件来表达ORM DSL，它等价于`app.orm.xml`这种XML格式的DSL文件。

### 2.1 XDSL的基本语法结构

XLang语言本身是图灵完备的，但它的设计用途主要不是作为一种通用程序语言，而是作为一种快速开发新的DSL语言的元语言来使用。也就是说虽然可以将XLang作为一种胶水语言来使用，但更多的是用它来开发一种DSL语言嵌入在Java语言环境中使用。

所有基于XLang开发的DSL语言具有一些统一的语法结构，这些DSL统称为XDSL。

```xml
<state-machine x:schema="/nop/schema/state-machine.xdef"
     x:extends="base.state-machine.xml">
    <x:gen-extends>
       <app:GenStateMachineDelta1/>
       <app:GenStateMachineDelta2/>
    </x:gen-extends>

    <x:post-extends>
       <app:PostProcessGeneratedModel />
    </x:post-extends>

    <!-- x:override=remove表示在最终的合并结果中删除这个节点 -->
    <state id="commit" x:override="remove" />

    <on-exit>
       <c:if test="${abc}">
           <c:log info="${xyz}" />
        </c:if>
    </on-exit>
</state-machine>
```

参考上面的示例，所有的XDSL都支持如下语法：

1. `x:schema`引入XDef元模型，类似于JSON Schema，用于约束DSL的语法结构。

2. `x:extends`表示继承已有的DSL文件，将两个DSL模型按照Tree结构逐层合并在一起。

3. `x:override`在执行`x:extends`时用于指定如何合并两个对应节点，`x:override=remove`表示删除语义。

4. `x:gen-extends`使用Xpl模板语言动态生成多个Tree结构节点，然后依次按照Delta合并算法合并在一起。

5. `x:post-extends`同样是使用Xpl模板语言来动态生成多个Tree结构节点，只是它的执行时刻与`x:gen-extends`不同。

6. DSL中如果希望嵌入脚本代码，可以直接使用Xpl模板语言，比如`on-exit`回调函数。

```xml
<model x:extends="A,B">
   <x:gen-extends>
      <C/>
      <D/>
   </x:gen-extends>
   <x:post-extends>
      <E/>
      <F/>
   </x:post-extends>
</model>
```

完整的合并顺序为

```
F -> E -> Model -> D -> C -> B -> A
```

任意的XML或者JSON文件格式都可以引入上面的XLang差量运算语法。比如我们为AMIS(百度开源的一个JSON格式的前端界面定义语言)引入了如下分解方案：

```yaml
x:gen-extends: |
   <web:GenPage view="NopSysCheckerRecord.view.xml" page="main"
        xpl:lib="/nop/web/xlib/web.xlib" />

body:
   x:extends: add-form.page.yaml
   title: 覆盖add-form.page.yaml中定义已有的标题
```

AMIS的JSON格式本身并没有提供分解合并机制，导致一个完整应用的JSON文件很大，也很难进行人工维护。通过引入XLang的`x:gen-extends`语法，可以根据View模型自动基础页面结构，在页面内部还可以使用`x:extends`引入已有的文件。

XLang语言内置了可逆计算支持，也就是`App = Delta x-exends Generator<DSL>`这种计算模式。`x:gen-extends`和`x:post-extends`对应于Generator，是一种元编程机制，可以在编译期作为内置的代码生成器生成模型节点。`x:extends`语法用于合并两个模型节点。

进一步的介绍参见 [XDSL：通用的领域特定语言设计](https://zhuanlan.zhihu.com/p/612512300)

下一代低代码平台-Nop平台中已经定义了多种DSL，比如工作流模型Workflow, 规则模型Rule， 数据模型ORM，组件编配模型BeanDefinition， 批处理模型Batch，二进制消息模型Record等。一般并不需要专门针对自定义的DSL编写运行时引擎，可以通过XLang的元编程机制在编译期将自定义的DSL翻译为已有的DSL语言，或者将多个DSL无缝集成在一起，构成一个新的DSL。参见[为什么SpringBatch是一个糟糕的设计](https://mp.weixin.qq.com/s/1F2Mkz99ihiw3_juYXrTFw)这篇文章中介绍的DSL森林的解决方案。

### 2.2 XDef元模型定义语言

XML格式存在着一系列的国际化标准，比如XSD(XML Schema Definition)，XSLT(EXtensible Stylesheet Language)，但是这些标准的底层假定都是和DOM模型一样，它们都是应用于文本结构的处理，所有的属性都是字符串格式，这使得它们无法应用于通用的Tree结构处理。

XLang引入了XDef元模型定义语言来取代XSD。XDef比XSD要简单、直观得多，而且可以提供比XSD强大得多的结构约束。

```xml
<state-machine x:schema="/nop/schema/xdef.xdef">
   <state id="!var-name" displayName="string" xdef:unique-attr="id" />
   <on-exit xdef:value="xpl" />
</state-machine>
```

与XSD和JSON Schema不同，XDef采用的是一种同态设计，即元模型定义的结构和它所要约束的XML格式之间基本完全一致，将XML节点的属性值替换为对应的类型声明即可。比如

* `id="!var-name"`表示id属性的格式满足var-name格式要求，不能包含特殊字符，不能以数字为前缀，`!`表示属性值不能为空。

* `<on-exit xdef:value="xpl"/>` 表示on-exit节点的内容是Xpl模板语言格式。读取模型文件时会自动解析得到IEvalAction类型的可执行函数。

* `xdef:unqiue-attr="id"`表示当前节点可以出现多个，构成一个列表，列表中的元素通过id属性来作为唯一标识。、

值得注意的是XDef元模型定义语言由`xdef.xdef`来定义。也就是说`state-machine.xml`是一个DSL语言，它的语法结构由元模型`state-machine.xdef`来定义，而`state-machine.xdef`的根节点上标注`x:schema='/nop/schema/xdef.xdef'`，表示这个元模型文件由`xdef.xdef`来约束，最终`xdef.xdef`仍然由`xdef.xdef`来完成约束，从而完成闭环。

所有XDSL领域特定语言共享的XDSL语法由`xdsl.xdef`元模型来定义。IDEA插件会自动根据`xdsl.xdef`中的定义来自动识别`x:extends`、`x:gen-extends`等语法，实现语法提示 、文件跳转等功能。

### 2.3 Xpl模板语言

XLang需要引入一种模板语言用于编译期的代码生成，但是它没有使用常见的velocity、FreeMarker等模板语言，而是重新设计了一种新的Xpl模板语言。

Xpl模板语言是一种图灵完备的语言，它提供了`c:for`、`c:if`、`c:choose`、`c:break`、`c:continue`等语法节点。

```xml
<c:for var="num" items="${numbers}">
    <!-- 检查数字是否为7 -->
    <c:if test="${num == 7}">
        <p>遇到数字 7，停止遍历。</p>
        <c:break /> <!-- 中断循环 -->
    </c:if>

    <!-- 使用 c:choose 判断数字的奇偶性 -->
    <c:choose>
        <when test="${num % 2 == 0}">
            <p>${num} 是偶数。</p>
        </when>
        <otherwise>
            <p>${num} 是奇数。</p>
        </otherwise>
    </c:choose>
</c:for>
```

Xpl模板中通过\${expr}表示嵌入XScript表达式，除此之外，Xpl中还提供了一个专用的`c:script`节点来执行XScript语句。

```xml
<c:script>
  import my.MyDSLParser;
  let model = new MyDSLParser().parseFromNode(path);
</c:script>
```

XScript的语法类似于JavaScript，但是增加了一些扩展语法，比如import语句可以引入Java类。

#### XML模板语言和表达式语法的相互嵌入

XLang没有采用jsx语法实现类XML语法，而是沿用XML语法，扩展JavaScript中的Template表达式语法。

```javascript
let resut = xpl `<my:MyTag a='${data}' />`
const y = result + 3;
```

等价于

```xml
<my:MyTag a='${data}' xpl:return="result" />
<c:script>
  const y = result + 3;
</c:script>
```

XLang修改了JavaScript中的Template表达式语法的解析格式，将反引号字符之间的内容识别为一个在编译期待解析的字符串，而不是一个Expression列表。这使得XLang可以利用这个语法形式扩展支持更多的DSL格式，比如引入类似C#的LinQ语法

```javascript
const result = linq `select sum(amount) from myList where status > ${status}`
```

#### 多种输出模式

与一般的模板语言不同，Xpl模板语言特别针对编译期代码生成进行了优化设计。一般的模板语言总是直接输出文本内容，这样在用于代码生成的时候会丢失原始代码位置，为了克服这个问题，又需要引入SourceMap机制，要求代码生成器在生成过程中额外记录生成代码和原始代码之间的对应关系。Xpl模板语言的做法则不同，它引入了多种输出模式，用于编译期代码生成时使用`outputMode=node`模式，此时并不是直接输出文本内容，而是输出XNode节点。

```java
class XNode{
    SourceLocation loc;
    String tagName;
    Map<String, ValueWithLocation> attributes;
    List<XNode> children;
    ValueWithLocation content;

    XNode parent;
}

class ValueWithLocation{
    SourceLocation location;
    Object value;
}
```

XNode结构中记录了属性和节点的源码位置，同时将attribute和content的值类型修改为Object类型，从而克服了XML原始设计中只针对文本文档的缺陷，使得它可以更高效的表达复杂的业务对象结构。

### 2.4 可扩展语法

类似于Lisp语言，XLang中可以通过宏函数和标签函数等机制扩展XLang的语法。可以通过`<c:lib>`来引入新的语法节点，然后在该节点内部再通过宏函数等机制实现结构转换。

```xml
<c:lib from="/nop/core/xlib/biz.xlib" />
<biz:Validator fatalSeverity="100"
               obj="${entity}">

    <check id="checkTransferCode" errorCode="test.not-transfer-code"
           errorDescription="扫入的码不是流转码">
        <eq name="entity.flowMode" value="1"/>
    </check>
</biz:Validator>
```

`<biz:Validator>`引入一个验证用的DSL，Validator标签在编译的时候会利用宏函数机制解析节点内容，将它翻译为XLang的Expression来运行。

## 三. XLang应用实例：差量化的组件模型

所有涉及到差量概念的软件实践都可以遵循可逆计算理论的技术路线，很多情况下都可以直接使用XLang来实现差量合并和分解，从而完全避免在运行时引擎中引入差量概念，简化运行时的实现。这里介绍一个在前端低代码/无代码平台的组件模型中的应用实例。

目前前端无代码/低代码平台的功能本质上就是通过可视化界面实现组件的嵌套组合。但是**组件封装在实际应用中经常出现困难：封装的组件难以直接满足需求，但完全从零开始编写一个新的组件成本又太高**。UIOTOS这个无代码平台提出了一种页面继承的做法。

![](nop/uiotos.webp)

具体来说，在UIOTOS中可以引入已有的页面作为基础页面，然后在上层设置属性覆盖底层页面的属性。详细介绍参见[UIOTOS的文档](https://www.yuque.com/liuhuo-nc809/uiotos/fa6vnvggwl9ubpwg#rsHSa)

为了实现这一特性，UIOTOS做了大量特殊的设计，并且在运行时引擎中引入了大量与属性继承相关的代码。但是，如果基于XLang语言，可以把差量计算完全压缩到编译期来执行，运行时引擎只需要知道普通组件结构即可，不需要有任何差量分解、合并的知识。

```xml
<component x:schema="component.xdef">
  <import from="comp:MyComponent/1.0.0"/>

  <component name="MyComponent" x:extends="comp:MyComponent/1.0.0">
    <state>
      <a>1</a>
    </state>
    <props>
      <prop name="a" x:override="remove"/>
      <prop name="b"/>
    </props>

    <component name="SubComponent" x:extends="ss">
      <prop name="ss"/>
    </component>

    <template x:override="merge">
      这里可以只显示Delta修正的部分

      <form x:extends="a.form.xml">
        <actions>
          <action name="ss" x:id="ss"/>
        </actions>
      </form>
    </template>
  </component>

  <template>
    <MyComponent/>
    <MyComponentEx/>
  </template>
</component>
```

* Component的template段用于表达如何通过子组件组合实现。
* 使用子组件的时候可以通过import语法引入已有的组件，也可以通过component语法定义一个局部组件。
* 如果将Component模型作为XLang的XDSL来实现，则可以使用`x:extends`语法来基于已有的组件进行Delta定制。完全不需要UIOTOS那种特殊设计，直接使用`x:extends`语法就可以实现差量化组件定义。
* 局部组件内部还可以包含自己的局部组件，同样可以被定制。也就是说Delta定制可以修改整个组件树，而不是简单的某个组件类的属性或者方法。
* Delta合并要求每个节点都必须具有唯一坐标，如果DSL节点没有可利用的id或者name属性，可以使用XLang内置的`x:id`扩展属性，这些属性在Delta合并完成之后会被自动删除，因此不会影响到运行时DSL引擎的处理。
* `x:extends`在模型加载的时候被执行，送入运行时引擎时所有x名字空间的属性都已经被处理并自动删除了。因此运行时引擎完全不需要有任何`x:extends`相关的知识，这和UIOTOS的做法形成鲜明对比：Delta差量可以被一种通用引擎一劳永逸的实现，而不需要针对每个特定需求引入差量处理机制。
* 通过`comp:MyComponent/1.0.0`这种扩展格式的虚拟文件路径来引用组件，当通过虚拟文件系统加载的时候可以自动实现租户隔离和版本升级隔离。

完整的讲解可以参见B站视频 [与UIOTOS作者的交流以及支持Delta概念的前端低代码平台的设计](https://www.bilibili.com/video/BV1ask2YhEfp/)。

引入XLang之后，实现Delta组件基本不需要做任何工作，而且这种做法可以被推广到所有需要Delta编辑的DSL模型。比如，有些人在后端服务应用的开发中也引入类似的组件模型。

基于可逆计算理论设计的低代码平台NopPlatform已开源：

- gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- 开发示例：[docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [可逆计算原理和Nop平台介绍及答疑\_哔哩哔哩\_bilibili](https://www.bilibili.com/video/BV14u411T715/)



# 关于"为什么XLang是一门创新的程序语言"一文的答疑

我在上一篇文章[为什么XLang是一门创新的程序语言](https://mp.weixin.qq.com/s/O4VeA7Dw8cRF7HTHxi6pNw)中介绍了XLang语言的设计思想，指出XLang语言之所以是一门创新的程序语言，是因为它创造了一个新的程序结构空间，在这个结构空间中可以很方便的实现可逆计算理论所提出的`Y = F(X) + Delta`的计算范式。本文是对一些反馈的问题所做的进一步的解释。

## 1. 如何基于XLang将差量计算压缩到编译期执行？

> 为了实现属性继承，UIOTOS做了大量特殊的设计，并且在运行时引擎中引入了大量与属性继承相关的代码。但是，如果基于XLang语言，可以把差量计算完全压缩到编译期来执行，运行时引擎只需要知道普通组件结构即可，不需要有任何差量分解、合并的知识。

![](nop/uiotos.webp)

UIOTOS是一个用于IOT领域的无代码平台，它引入了一种容器组件，在这个组件中可以应用已经存在的页面，然后利用属性机制机制覆盖页面对象中的属性，从而实现不重新实现页面，但是又能灵活定制该页面内容的效果。

```json
{
  type: "container",
  baseUrl: "a.page.json",
  overrideProps: {
    "form/title": "sss",
    "actions/0/label": "vvv"
  }
}
```

大致方案如上述所示，基本相当于是通过baseUrl引入页面对象，然后通过多个继承属性来覆盖页面对象中的内容。通过类似JsonPath的语法，可以修改页面对象中的任何嵌套内容，因此它和一般的组件框架中调用组件，然后给组件传递参数是不同的。

UIOTOS在前端运行时框架中专门针对属性继承编写了不少代码，并且需要引入一个特殊的container控件。UIOTOS的方案有一个限制，就是它只能覆盖已有页面中的属性值，并不能改变被继承页面的结构。另外有一个同学做的低代码平台采用了一个功能类似的方案，它不需要引入特殊的container的组件，对于任何组件都可以进行差量定制。具体做法就是在组件中引入一个特殊的overwrite属性。

```json
{
  "component": "MyComponent",
  "version" : "1.0",
  "properties": {
     "a": 1, // 直接设置组件属性
   },
   "overwrite": [
    "这里记录可视化编辑器中对组件的编辑动作"
 ]
}
```

基本操作模式是在编辑器中推拽一个组件之后，如果发现组件有些细节需要调整，可以进入组件定制模式，在可视化设计器中对组件进行微调。**调整过程中的操作步骤被自动录制下来，作为overwrite保存在页面文件中**。这个方案可以任意调整组件结构，比UIOTOS的方案适应性更强，但是记录操作动作会比较冗长，多个动作也难以压缩成一个精简的最终结果（相当于没有利用结合律进行化简）。

> 根据可逆计算理论，A=0+A，全量是差量的一个特例，我们可以用统一的形式来定义全量和差量，这样差量的差量就也是一个普通的差量，可以实现更复杂的逻辑推理。使用overwrite这种动作模式来表达差量是不合适的。

无论是上面的哪种方案在编辑器和前端运行时框架里都要加入差量概念相关的处理代码。但是如果使用XLang语言作为底层模型的表达语言，则可以把差量计算完全压缩到编译期来执行，运行时引擎只需要知道普通组件结构即可，不需要有任何差量分解、合并的知识。具体做法是通过`x:extends`来实现组件继承。

```xml
<component x:schema="component.xdef">
  <import from="comp:MyComponent/1.0.0"/>

  <component name="MyComponentEx" x:extends="comp:MyComponent/1.0.0">
    <props>
      <prop name="a" x:override="remove"/>
      <prop name="b"/>
    </props>

    <template x:override="merge">
      这里可以只显示Delta修正的部分

      <form x:extends="a.form.xml">
        <actions>
          <action name="ss" x:id="ss"/>
        </actions>
      </form>
    </template>
  </component>

  <template>
    <MyComponent/>
    <MyComponentEx/>
  </template>
</component>
```

如果不需要定制，直接通过import引入组件来使用即可。如果需要定制，则启用局部组件定义，通过`x:extends`引入已有的组件。XLang定义了Tree结构之间的Delta合并算法，通过统一的DslNodeLoader来加载模型文件时会自动执行这个算法。伪代码如下：

```javascript
 function loadDeltaModel(path){
    rootNode = VirtualFileSystem.loadXml(path);
    for each node with x:extends attribute  // 递归遍历rootNode以及rootNode的子节点
        baseNode = loadDeltaNode(node.removeAttr('x:extends'));
        genNodes = processGenExtends(node);

        for each genNode in genNodes
            baseNode = new DeltaMerger().merge(baseNode, genNode);
        node = new DeltaMerger().merge(baseNode,node);

    processPostExtends(node);
    return node;
 }
```

`DslNodeLoader.loadDeltaModel("comp:MyComponent/1.0.0")`返回的XNode是最终合并后的节点，其中已经不包含任何x名字空间的属性和子节点。

**Loader可以看作是一种即时编译器，它加载模型文件的时候进行的结构转换可以看作是编译过程的一部分**。

### 在结构层而不是对象层定义的差量运算

> 维特根斯坦说过，语言的边界就是我们世界的边界。可逆计算理论进一步的诠释是：**一种程序语言定义了一个软件结构空间，各种根据已有结构产生新结构的复用机制相当于是这个结构空间中的变换规律**。

一个真正实用的DSL必然需要考虑可扩展性的问题，需要内置一些分解、合并、复用的机制。但是目前大部分DSL设计者都习惯于在具有语义的对象层引入这些结构运算，导致每个DSL设计者实际上都是拍脑袋设计，缺乏通用性和内在的一致性。

XLang提供了一整套标准化的做法，一劳永逸的解决所有DSL的可扩展性问题。DSL引擎只要考虑最小化的运行时问题即可。XLang完全只在编译期（模型解析和加载）执行，运行期完全没有任何XLang相关的内容。这里的关键就在于**XLang是在对象之下的结构层实现Delta合并运算**。所谓结构层就是XNode层面，类似Lisp中的S表达式，它本身是没有任何语义的。**脱离语义，正是Delta合并运算通用性的表现**。

举例来说，Spring的`beans.xml`可以看作是组件装配领域的DSL。Spring1.0引入了parent属性用于实现某种继承功能，引入了Import语法来实现复杂文件分解和复用。Spring2.0引入了自定义节点，简化复杂结构Bean的配置。SpringBoot则引入了@ConditionalOnProperty注解，允许通过配置开关来选择是否启用Bean。为了实现这些功能，都需要Spring框架在内核中专门编写相应的处理代码。

```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:myns="http://www.example.com/schema/myns"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
           http://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.example.com/schema/myns
           http://www.example.com/schema/myns/myns.xsd">

    <import resource="classpath:config/services.beans.xml"/>

    <!-- 父Bean -->
    <bean id="parentBean" class="com.example.ParentClass">
        <property name="commonProperty" value="commonValue"/>
    </bean>

    <!-- 子Bean，继承父Bean的配置 -->
    <bean id="childBean" parent="parentBean">
        <property name="extProp" value="extValue"/>
    </bean>

     <!-- 使用自定义命名空间配置Bean -->
    <myns:customBean id="customBean" customProperty="customValue"/>
</beans>
```

```java
@Component
@ConditionalOnProperty(name = "mycomponent.enabled", havingValue = "true", matchIfMissing = false)
public class MyComponent {

    public MyComponent() {
        System.out.println("MyComponent is initialized!");
    }

    public void doSomething() {
        System.out.println("MyComponent is doing something!");
    }
}
```

而如果是使用XLang，这些功能完全不需要开发

```xml
<beans x:extends="config/base.beans.ml">
   <x:gen-extends>
     <c:include src="config/services.beans.xml" />

     <beans>
        <myns:customBean id="customBean" customProperty="customValue"
                      xpl:lib="/example/myns.xlib" />
     </beans>
   </x:gen-extends>

   <bean id="parentBean" class="com.example.ParentClass">
        <property name="commonProperty" value="commonValue"/>
   </bean>

   <bean id="childBean" x:prototype="parentBean">
        <property name="extProp" value="extValue"/>
   </bean>

   <bean id="myComponent" class="com.example.MyComponent"
         feature:on="mycomponent.enabled">
       <property name="propA" ref="xxx" feature:on="mycomponent.xxx.enabled" />
   </bean>
</beans>
```

* 首先，我们注意到，在不引入任何可扩展机制的情况下，Spring1.0中bean的定义就是一个完备的组件装配模型。也就是说，任何可以通过`get/set`函数和`constructor`构造器来装配的bean，都可以使用`beans.xml`这个DSL来描述式的定义装配逻辑。在数学上，我们可以说Spring1.0定义了一个完备的装配运算集合。

* XLang的做法是在已有的DSL基础上引入一组Delta差量运算，但是这些**Delta运算的结果是将`DSL + Delta`结构划归为原始的DSL结构**。这里有趣的是，Spring2.0引入的自定义名字空间的做法是不能化归到1.0语法的，也就是说**用Spring2.0语法配置的bean并不能保证一定可以使用Spring1.0语法来配置，即使Spring1.0语法是一个完备运算集**。Spring2.0的自定义名字空间，比如`<myns:customBean>`会触发Java中的一个NamespaceHandler去处理，它在Java中执行的逻辑可以任意复杂，甚至可能会隐含的引入顺序依赖（bean的声明顺序有可能影响到装配结果），实际上会破坏了Spring1.0的POJO声明式装配设计。

* `x:extends`可以继承已有的DSL文件，功能类似于Spring1.0的import语法。

* `x:gen-extends`中执行Xpl模板语言来动态生成bean的定义。这里可以使用Xpl模板语言内置的`c:include`来引入外部XNode节点，同样可以取代Spring1.0的import语法。

* `x:gen-extends`段中通过Xpl自定义标签的功能可以模拟Spring2.0的自定义名字空间机制。只不过Xpl标签的做法是代码生成，无论标签函数的执行逻辑多么复杂，只要它能生成我们期望的bean配置定义即可。比如上面的`<myns:customBean>`可能实际会生成多个bean的定义。真正在运行时起作用的是标签函数的生成结果。`x:gen-extends`本身是在编译期执行。

```xml
 <myns:customBean id="customBean" customProperty="customValue"
                      xpl:lib="/example/myns.xlib" />

实际展开成如下两个Bean的定义：

<bean id="customBean" class="com.example.CustomBean">
    <property name="customProperty" value="customValue" />
    <property name="otherProperty" ref="otherBean" />
</bean>

<bean id="otherBean" class="com.example.OtherBean" />
```

* XLang中，同级的兄弟节点之间可以通过`x:prototype`属性来指定继承关系。这可以取代Spring1.0语法中的parent属性的作用。同时XLang中的节点进行合并时，可以通过`x:override`来精细的控制合并逻辑：到底是覆盖、合并还是删除等。需要强调的是，XLang的这种机制是在任何节点上都可以实施的，比如在property也可以指定`x:prototype`继承其他Property的配置。但是Spring中的parent属性只能用于bean的定义的继承。

* XLang中每一个节点上都可以通过`feature:on`和`feature:off`这种特性开关来控制条件加载。当特性开关不满足时，对应的节点会被自动删除，实际上并不会进入运行时。这可以取代SpringBoot的条件Bean的作用。同样的，feature开关在任何节点上都可以使用，比如在`<property>`节点上可以控制是否配置某个属性。但是在SpringBoot中条件开关只能作用于bean的创建，是没有任何声明式的机制来控制是否配置某个property的。

综上所述，Spring框架中内置了很多用于增加扩展性的机制，这些机制都需要专门针对组件装配这个领域，针对Spring这个特殊的框架进行编写。如果迁移到另外一个运行时引擎，这些机制都需要重新编写。比如Quarkus框架也支持Bean的装配定义，所有这些扩展方案它都需要自己实现一遍。Spring实现完毕之后，Hibernate等框架也无法利用这些机制来实现自己的扩展。

XLang的关键是将XML或者JSON解析为XNode节点之后，在XNode这个层面完成Delta运算，而不是将XNode转换为强类型的BeanDefinition之后再执行Delta运算。因此它所做的这些功能可以自动应用到其他的DSL上，比如MyBatis的mapper文件，Hibernate的hbm文件等。

其实类似的可扩展性问题在很多领域都存在，比如Facebook发明的GraphQL服务协议中引入了类型扩展语法

```graphql
type User {
  id: ID!
  name: String!
  email: String!
}

extend type User {
  age: Int
  email: String @deprecated(reason: "Use 'contactEmail' instead")
  contactEmail: String!
}
```

在`graphql-java`包的实现中，是先将graphql定义解析为TypeDefinition和TypeExtensionDefinition，然后在对象层实现类型合并。

在XLang的技术体系中，NopGraphQL框架使用XMeta元数据模型来定义Type，所以可以直接使用XLang内置的XNode层面的差量机制实现扩展，无需专门设计一个TypeExtension语法。在NopGraphQL的运行时，也无需具有任何类型扩展的知识。对于运行时框架而言，类型就是类型，不存在类型+类型扩展的情况

### Loader as Generator

在XLang的技术体系中，Loader这个抽象具有特殊地位。因为所有可逆计算相关的内容原则上都是在Loader抽象中完成，所以第三方框架集成XLang原则上非常简单：直接将DSL的Loader替换为XLang的Delta Loader即可。

一个通用的模型加载器可以看作是具有如下类型定义：

```
Loader :: Path -> Model
```

对于一种通用设计，我们需要意识到一件事情，所谓的代码编写并不仅仅是为了应对眼前的需求，而是需要同时考虑到未来的需求变化，需要考虑到系统在时空中的演化。 换句话说，编程所面向的不是当前的、唯一的世界，而是**所有可能的世界**。在形式上，我们可以引入一个Possible算子来描述这件事情。

```
Loader :: Possible Path -> Possible Model
Possible Path = deltaPath + stdPath
```

stdPath指模型文件所对应的标准路径，而deltaPath指对已有的模型文件进行定制时所使用的差量定制路径。举个例子，在base产品中我们内置了一个业务处理流程main.wf.xml，在针对客户A进行定制时，我们需要使用一个不同的处理流程，但是我们并不想修改base产品中的代码。此时，我们可以增加一个delta差量模型文件`/_delta/a/main.wf.xml`，它表示针对客户a定制的main.wf.xml，Loader会自动识别这个文件的存在，并自动使用这个文件，而所有已经存在的业务代码都不需要被修改。

如果我们只是想对原有的模型进行微调，而不是要完全取代原有模型，则可以使用`x:extends`继承机制来继承原有模型。XLang中的DeltaLoader的执行逻辑在数学层面上由下面的公式描述

```
Loader<Possible Path> = Loader<deltaPath + stdPath>
                      = Loader<deltaPath> x-extends Loader<stdPath>
                      = DeltaModel x-extends Model
                      = Possible Model
```

这里需要强调的是，Loader抽象实际上具有非常广阔的应用场景，大量的与可扩展性相关的功能都可以下放到Loader层面统一实现，比如说多租户定制问题可以由一个识别租户参数的Loader来解决。更进一步的介绍参见[从张量积看低代码平台的设计](https://mp.weixin.qq.com/s/BFCTN73pH8ZZID3Dukhx3Q)

## 2. 如何理解XLang中差量叠加与波的类比关系？

> 物理世界中存在着另一种构造方式，那就是波。波是连续存在的模式，通过干涉叠加来构造世界。XLang语言的特异性就在于它通过差量运算支持连续的叠加构造。

传统上构造好X之后，如果要修改，一定是要修改X本身。而如果是一种叠加方式，则可以**通过额外补充一个Delta，在不直接改变X的情况下得到一个新的Y**。

```
X = A + B + C
Y = A + B + D
  = X + (-C + D)
  = X + Delta
```

传统的软件工程体系下，即使可以做到某种增量式开发，往往也是需要特殊设计很多扩展点，并不是任意地方的修改都可以通过Delta方式进行定制修改的。特别是传统的软件工程中增量一般都意味着增加新的功能，很少涉及到逆向减少已有的功能特性这种设计。Delta定制使得我们可以**通过增加实现减少**。

**使用XLang开发的系统无需做任何额外的工作，就自动支持Delta定制。这使得产品化产品的开发成本得以大幅降低**。比如说一个银行核心系统在打包成jar包之后，无需修改基础产品的代码，所有针对特定用户的定制修改、二次开发都可以作为Delta差量单独存放，通过切换Delta就可以实现多个不同的定制版本。同样的机制可以用于多租户定制。

Delta定制可以实现精确到单个属性的定制。而传统软件工程只能提供少数事先定义的扩展点，很难实现所有地方都支持细粒度定制。比如我只想定义一个按钮的某个属性，往往都需要增加一个新的组件或者页面。**所有业务层面关注的概念都可以逐个进行定制**，这是所谓的连续性的一种表现。

## 3. 在已经开发好的标准系统中能否引入XLang？

> XLang是一种创新的技术，它能否应用到已有的系统中？这样在遇到定制需求后，可以使用XLang表达出差量，然后基于标准系统和差量表述生成定制版系统。

首先需要明确的是，差量需要在差量化的结构空间中表达。传统的软件是使用通用语言来表达，它所在的结构空间也就由通用程序语言来定义。通用程序语言如`Java`,`C#`等表达差量的时候非常受限，无法实现细粒度的差量定义。

在面向对象程序语言中，能够直接利用的差量机制只有继承机制。Nop平台的做法是采用一种所谓的三明治架构，代码生成的时候采用如下生成方案

```java
class NopAuthUser extends _NopAuthUser{

    // 这里可以增加额外的方法，从基类继承模型驱动生成的代码。
}

class _NopAuthUser extends OrmEntity{
}
```

也就是说，模型驱动生成的类从系统内置的基础类继承，这样可以自动得到一些内置的属性和方法，而最外层再生成一个类从代码生成的类继承，这样手工修改的代码可以和自动生成的代码隔离。代码生成的时候，我们约定如下规则: **文件名以下划线为前缀的文件以及`_gen`目录下的所有文件都会被自动覆盖，其它文件只有不存在时才新建**。这样的话，当模型发生变动的时候，可以直接重新生成，不会导致手工修改的内容丢失，从而实现模型驱动的增量式开发。

虽然不能从面向对象语言中直接得到更多的差量化支持，但是在架构层面可以自行构建更多的差量机制。最基本的方案是所有使用到XML/JSON/YAML等配置文件或者模型文件的地方都可以引入XLang。

![](../tutorial/simple/images/solon-chain.png)

在上面的示例中，Chain是一种可以通过json文件定义的模型对象，它的解析和装载可以通过`Chain.parseByUrl`函数进行。如果使用XLang来改造，可以直接将·`Chain.parseByUrl`替换为`ResourceComponentManager.loadComponentModel(path)`，然后把json文件移动到`resources/_vfs`目录下即可。在这个json就可以使用`x:extends`，`x:post-extends`，`x:override`等XLang的Delta差量语法。

Nop平台提供了一个`nop-spring-delta`模块，其中对Spring的`beans.xml`文件和MyBatis的`mapper.xml`文件增加了Delta定制支持，可以把这些XML文件放到`resources/_vfs`目录下。具体做法如下：

```java
@Service
@ConditionalOnProperty(name = "nop.spring.delta.mybatis.enabled", matchIfMissing = true)
public class NopMybatisSessionFactoryCustomizer implements SqlSessionFactoryBeanCustomizer {
    @Override
    public void customize(SqlSessionFactoryBean factoryBean) {

        List<IResource> resources = ModuleManager.instance().findModuleResources(false, "/mapper", ".mapper.xml");

        if (!resources.isEmpty()) {
            List<Resource> locations = new ArrayList<>(resources.size());
            for (IResource resource : resources) {
                // 忽略自动生成的mapper文件，它们只能作为基类存在
                if (resource.getName().startsWith("_"))
                    continue;

                XDslExtendResult result = DslNodeLoader.INSTANCE.loadFromResource(resource);
                XNode node = result.getNode();
                node.removeAttr("xmlns:x");

                String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                        "<!DOCTYPE mapper\n" +
                        "        PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\"\n" +
                        "        \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n" + node.xml();
                locations.add(new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8), resource.getPath()));
            }
            factoryBean.addMapperLocations(locations.toArray(new Resource[0]));
        }
    }
}
```

* `ModuleManager.instance().findModuleResources(false, "/mapper", ".mapper.xml")`会在各个模块的mapper目录下查找`mapper.xml`文件。这个过程会自动考虑Delta目录下的文件。如果在`_vfs/_delta/{deltaId}/`目录下存在同名的文件，则会自动选择Delta目录下的版本。Nop平台内置的VirutalFileSystem是一种类似Docker的分层文件系统，上一层的文件会覆盖下一层的同名文件。每个Delta目录就构成一个独立分层，可以通过`nop.core.vfs.delta-layer-ids`来指定多个Delta层。

* 通过XLang提供的DslNodeLoader加载XML的文件的时候，会根据根节点上的`x:schema`属性读取到对应的XDef元模型，然后按照元模型规范定义实现节点的Delta合并。

* 合并完成之后得到一个XNode节点，可以将它转换为XML的DOM节点，这里直接序列化为XML，送入MyBatis的工厂bean。MyBatis本身不需要做任何改造，只是为它增加了一种新的获取mapper文件的方式而已。

## 4. XLang语言的差量计算和Delta机制在提高可扩展性和定制化的同时，是否会引入额外的性能开销？

首先，XLang实现DSL的差量合并和Delta定制时，主要是在模型加载时刻通过统一`ResourceComponentManager.loadComponentModel`函数进行，在这个过程中实现了模型缓存、模型编译依赖追踪（依赖文件变化时自动使得模型缓存失效）。

在开发阶段通过延迟加载、即时编译、并行加载等技术可以减少系统初始化时间。

对于正式发布版本，可以通过maven打包工具在编译期执行合并，最终生成合并后的模型文件到`_delta`目录下，并在模型根节点上标注`x:validated="true"`。运行时会优先加载`_delta`目录下的模型文件（这个文件是最终合并后的结果），并且因为已经标注为模型已验证，会自动跳过合并过程，所以再复杂的差量合并逻辑也不会影响运行时性能。


## 5. Lang是不是就是一套标注，底层引擎能理解这套标注，解析后进行差量化合并

你可以认为是在通用的XNode节点（相当于是一种通用的AST）基础上引入了新的差量运算语法规则，也就是你说的`x:extends`, `x:override`这种标注。


* 那如果把这套标注和解析引擎做成一套通用的xml的输入和输出，那是不是就可以实现万物的差量化？不管是程序逻辑还是流程，还是表单，或者其它任意的内容都可以用xml定义出来，能定义出来就一定可以实现差量化合并，合并完后就交给执行引擎
是的，但是这种理解是局部的，而且包含各种误解。比如这种理解完全绑定在所谓的XML实现形式上，但其实XLang根本与具体形式无关，就是关于通用的Tree结构上的运算规律。

* 那是不是XLang就是一套属性标准定义，增加，删除，或者修改节点定义用的描述，差量化合并就是把主文件和delta进行合并，合并的规则就是XLang语言的定义规范用来合并，合并后就成了一份新的dsl描述（可以是xml，也可以是json，也可以是任何tree结构），得到这份新的dsl后剩下交给执行引擎，怎么解析这份dsl是执行引擎的事情，与XLang无关。
大致上是的。但是还需要理解`x:gen-extends`和`x:post-extends`的作用，最终形成对`App = Delta x-extends Generator<DSL>`这样一个完整的计算模式的认知。

## 6. XLang这种模式对于二次开发和ISV生态开发是否会存在挑战？如果调试bug，因为各自都拿不到其它开发团队的delta，如何诊断数据在哪个逻辑被修改了
首先所有模型文件都在`_vfs`虚拟文件目录下统一管理，而且都具有XDef元模型定义。一般只需要查看元模型定义就可以知道DSL的具体语法结构，而且IDEA插件还可以自己进行语法提示，支持断点调试等。
系统启动时，所有Delta合并的结果会输出到dump目录下，这里可以看到每个模型文件最终的合并结果以及每个属性、节点的来源位置。如果要进一步追踪合并的过程，可以在根节点上标注`x:dump="true"`。
详细调试方式参见[debug.md](../dev-guide/debug.md)



# 关于"为什么XLang是一门创新的程序语言"一文的进一步解释

XLang语言是下一代开源低代码平台Nop平台底层的关键性支撑技术。传统的支持泛型元编程的程序语言在结构层面对应于构造公式 `Map = Map extends Map<Map>`，而XLang相当于是将这一公式扩展为`Tree = Tree x-extends Tree<Tree>`。也就是将Map扩展为Tree，同时将Map结构之间的extends运算被扩展为Tree结构上的`x-extends`运算，特别是`x-extends`增加了逆向删除的语义。

XLang语言之所以是一门创新的程序语言，是因为它创造了一个新的程序结构空间，在这个结构空间中可以很方便的实现可逆计算理论所提出的`Y = F(X) + Delta`的计算范式。这一概念的创新性很强，是超出了传统计算机科学思维惯性的一种研究视角，所以一些科班出身的朋友理解起来反而出现了额外的思维障碍。

此前我在公众号上发表了两篇专门讲解XLang语言的设计原理的文章，[为什么说XLang是一门创新的程序语言?](https://mp.weixin.qq.com/s/O4VeA7Dw8cRF7HTHxi6pNw)和[关于"为什么说XLang是一门创新的程序语言"一文的答疑](https://mp.weixin.qq.com/s/XtqjqoC8bhDSuCwGhrMbnw)，并用DeepSeek生成了一篇通俗解释[DeepSeek的通俗版解释：XLang为什么是一门创新的编程语言？](https://mp.weixin.qq.com/s/GsGrmaXMqKmmrYW7EuAuig)。一位朋友在知乎上留言：

> 耐性读完了您的两篇大作，我要说完全看不懂，那是昧着良心说话，但是看懂一点点比完全看不懂更让人困惑：您这XLang是干什么用的？是一个超级的注册表形式的数据结构吗？要怎么实现你说的（或者我以为的）宏伟目标？函数在你这语言中是否是一等公民？

这种疑惑很常见，本质上是因为XLang所依据的理论原理来自于数学和物理学，所以仅受过计算机科班教育的同学将XLang的概念向自己熟悉的计算机领域的概念映射时，会出现种种心理上的不适的感觉。**一个人很难理解他尚未理解的事物**，而他已经接受的某些事物往往会被不自觉的看作是天经地义、理所当然的，对于偏离现有理解的认知会自动的无视甚至抗拒。在本文中我再针对性的进行一些解释，如果有问题欢迎留言讨论。

## 一. XLang是干什么用的？

**XLang的主要用途是用于快速开发和扩展领域特定语言，实现所谓的面向语言编程范式(Language Oriented Programming)**。面向语言编程范式并不是我发明的一个概念，它是计算机科学领域中已经存在了很多年的概念，比如1994年的这篇论文[[PDF] Language-Oriented Programming | Semantic Scholar](https://www.semanticscholar.org/paper/Language-Oriented-Programming-Ward/825a90a7eaebd7082d883b198e1a218295e0ed3b)。

我们平时实现业务都是使用通用程序语言，而面向语言编程范式强调领域特定语言（DSL, Domain Specific Language）的作用，开发业务时总是先开发一个专用于这个业务领域的DSL，然后再用这个DSL去表达业务逻辑。开发了IDEA集成开发工具的JetBrains公司就有一个相关的技术产品[MPS(Meta Programming System)](https://www.jetbrains.com/mps/)。

使用XLang来开发一个新的DSL非常简单，简单到只需要增加一个XDef元模型定义文件，然后你就可以立刻得到一个新的DSL。Nop平台提供了一个通用的IDEA插件，可以自动读取XDef元模型，实现自定义DSL的语法提示、链接跳转、断点调试等功能，后续还会自动实现类型推导等。Nop平台的基础设施自动根据XDef元模型文件生成模型类定义，自动实现解析器和验证器，并自动生成可视化编辑器等。

使用XLang定义的DSL语言不需要自己去考虑扩展性问题（也不用设计相关语法），而且也不需要考虑多个DSL如何无缝集成在一起使用的问题。它们由Nop平台的底层基础设施统一实现。在DSL文件中通过`x:extends`，`x:gen-extends`等内置语法自动实现模型分解合并、编译期元编程等。

如果要扩展一个已有的DSL的语法也非常简单，只需要增加一个XDef元模型文件，指定这个XDef元模型文件从已有的XDef元模型文件继承就可以。

使用XLang的DSL所开发的所有的软件产品都自动支持所谓的Delta定制机制，也就是在完全不修改已有源代码的情况下，在Delta目录下增加Delta文件，就可以修改、删除已有逻辑，当然也可以实现新增逻辑。

XLang所提供的这些能力是**此前所有程序语言都不具备的创新能力**。它对于Nop平台解决粗粒度的、系统级别的软件复用至关重要。也就是说，ToB市场中最难解决的定制化开发问题在Nop平台的技术架构下可以得到本质上的改善，特别是基础产品的架构可以完全不受到定制开发的影响。

* **一个使用XLang开发的银行核心应用，在完全不修改基础产品源码的情况下，通过在Delta目录下增加Delta差量文件就可以定制从数据模型，到业务逻辑，再到前端显示界面的所有逻辑**。

* 一个使用XLang开发的低代码平台，在完全不需要修改平台本身的代码的情况下，通过同样的Delta定制方案，就可以定制这个低代码平台中所有可视化设计器的界面和编辑功能，甚至可以定制被编辑的模型对象。

## 二. XLang是一个超级的注册表形式的数据结构吗？

用注册表去理解XLang就相当于是用链表去理解Lisp：虽然有关系但是关系不大。

在使用现有的程序语言去做抽象时，我们一般所定义都是离散的扩展点，这些离散的扩展点可以用一个类似注册表的结构管理起来。这种思想非常根深蒂固，比如华为的TinyEngine低代码引擎在提到自己2.0版本的设计时，特别强调了**通过注册表机制，可以轻松地对组件、API等内容进行替换，实现灵活的插拔和定制**。参见[TinyEngine低代码引擎2.0新特性介绍](https://mp.weixin.qq.com/s/oX73EX3ZFpk3i6MupiYKZA)。

对于一个已有的实现，比如ABC，如果我们想把它改造成可扩展的抽象，最常见的方案就是变量抽取，比如 `A{X:B}C`，将B替换为一个变量X，然后通过一个变量映射的Map（本质上就是一种注册表）为X指定具体的值。为了减少配置量，我们还可以给它指定一个缺省值B，并宣称这是一种设计原则，即所谓的约定大于配置。**这种方案相当于是在有可能需要修改的地方挖个洞，然后根据需求，为这个洞填上不同的内容**。

> 这种方案也可以被解释为增加一层间接性：任何解决不了的问题都可以通过增加一次指针跳转来解决，如果仍然解决不了，就再增加一层。

如果只有少数地方有可能需要扩展，那么事先挖几个洞是很简单的事情。但是如果事前不知道哪里需要变化，而且很多地方都可能会发生变化呢？ **如果所有地方都挖上洞，那么原有的架构就完全空洞化了，它还有什么存在的意义呢**？挖洞不仅仅有成本，而且会影响系统的运行时性能，也增加了系统的理解难度。事先挖的洞有可能用不上，甚至还会为真正需要的扩展制造障碍。比如 `ABC`挖洞后成为`{X}{Y}C`，结果实际需求既不是替换X，也不是替换Y，而是替换Y的一部分和C的一部分，这要怎么办？

**每个扩展点可以被看作是一个扩展自由度，扩展点不断增加相当于扩展空间的自由度在不断增加**，那么当扩展空间的自由度无限增加的时候，我们能不能建立一种合适的抽象手段？传统的软件构造理论中对于这个问题的回答是否定的。在现有的理论体系中，我们需要依赖于事前的预测来预置扩展点，而不可能在事后不修改原始系统代码的情况下增加或者修改扩展点，当然也不可能在不修改源码的情况下去除扩展点。

**XLang解决无限扩展自由度问题的方法，是仿照物理学引入新的概念：坐标系。本质上是从刚体力学的世界观转向场论的世界观**。在高中阶段我们所学习的牛顿物理学是所谓古典力学中的刚体力学。它的世界观是完全机械化的：刚体的运动完全由它的质心坐标和尺寸形状朝向等少数几个参数来描述，刚体的内部构造无法被观测也无关紧要，刚体之间通过直接接触发生相互作用，刚体的形状必须精确匹配才能构成一个无缝的整体（可以对比一下软件组件的黑箱模型）。即使是在古典力学中，稍微高级一点的观点也都会转换到拉格朗日表述或者哈密尔顿表述，它的精神实质是转向**场论的世界观**。所谓的场（Field），其实就是**建立一个无所不在的坐标系，然后在坐标系的每一点上都可以指定一个物理量**。场的自由度是无限的，但是通过坐标系它是可描述的、可定义的、可研究的，在坐标系的每一点上我们都可以精确的度量局部的变化。基于同样的精神，可逆计算的基本设定是首先建立一个足够精细和通用的领域描述坐标系，在这个坐标系中我们能够做到指哪打哪和打哪指哪（**坐标的唯一性**）。

在可逆计算理论中，**所谓的一个坐标系统，就是为系统中涉及到的每一个值都赋予一个唯一的坐标**。

```
value = get(path);
set(path, value);
```

从形式上看，坐标系的实现类似于一种注册表机制。但是坐标系是一个抽象的概念，它的形式非常多样化。比如文件系统可以看作是一个坐标系，其中的坐标就是文件路径，每个文件路径都对应于一个文件，而每个文件也有一个唯一的文件路径（不考虑文件链接的情况，或者我们只考虑canonical路径）。**一般情况下我们并不会把文件系统看作是一种注册表，更不会把一种DSL语言看做是一种注册表**。

XLang语言中的坐标系具体如何实现？答案很简单，**每个DSL语言都自动定义了一个坐标系统**。这个概念听起来有点微妙，但是如果学习过微分几何中的活动标架法，就可以很快理解它。一般情况下我们都是在一个选定的坐标系统中来定义运动，但是活动标架法利用运动本身的内在特性自动定义了一个附着在运动轨迹上的内禀坐标系统，换句话说**运动在坐标系中发生，同时运动本身生成了一个坐标系**。类似的，业务逻辑使用DSL来表达，同时表达业务的时候使用的DSL的抽象语法树及其节点属性就自动构成了一个坐标系。具体来说，抽象语法树上的每个属性具有唯一的xpath，比如`/task/steps[@name=a]/@name` 表示步骤a的name属性。**因为任何逻辑都是需要用程序语言来表达，所以不存在这个坐标系没有覆盖的情况，所有业务必然是在这个坐标系所定义的坐标空间中发生**。

任何一种程序语言都可以解析为抽象语法树AST，而AST的每个节点和属性都具有唯一的xpath，因此任何一种程序语言都定义了一个内在的坐标系。但是问题在于通用程序语言的坐标在业务层面是不稳定的。比如说，在数据模型层面增加了一个字段，用通用语言表达的时候，可能很多地方都需要手动修改代码。但是如果是模型驱动架构，在数据模型这个DSL中可能只需要修改一个地方，然后会自动推导得到其他地方的修改。我们可以说，增加字段这个变化在数据模型所定义的坐标系中只会产生一个局部的扰动。如果学习过狄拉克提出的Delta函数，我们在形式上可以把它表达为 $ Field*\delta(x-x_0)$。

XLang语言所定义的所有DSL相比于一般的Tree结构，**需要引入一个额外的约定：所有的列表元素都必须具有一个可以用作唯一标识的属性**，比如name、id等，如果业务层面上没有这样的属性，XLang还提供了内置的`x:id`可供使用。引入这个规则之后，DSL的每个Tree节点都会得到一个稳定的坐标，不会因为新增或者删除节点导致大量节点的坐标发生漂移。这里的处理方案其实很容易理解也很自然，前端领域的React框架和Vue框架在实现虚拟DOM Diff算法的时候，都要求列表结构必须引入一个key属性，从而保证Diff算法的稳定性。

> DeepSeek的评价: 这一约束实际上暗合计算机科学中的命名哲学——正如DNS通过域名解耦IP地址、UUID通过唯一标识解耦存储位置，XLang通过强制唯一标识将树节点的逻辑身份与其物理位置分离，实现了从“脆弱的位置耦合”到“稳定的身份抽象”的范式跃迁。

## 三. 怎么实现XLang的宏伟目标？

整个可逆计算理论的技术路线体现为如下核心公式：

```
App = Delta x-extends Generator<DSL>
```

XLang解决问题的独特方式本质上就是反复应用上述公式，**不断通过横向和纵向分解将问题空间向众多的DSL子空间投影，然后通过Delta差量来将这些子空间粘结为一个整体**。如果学习过微分流形理论，就可以很容易的理解这里的内在逻辑。详细介绍参见[Nop如何克服DSL只能应用于特定领域的限制?](https://mp.weixin.qq.com/s/6TOVbqHFmiFIqoXxQrRkYg)

比如说，XLang为面向动态相似性的复用提供一条标准化的技术路线。

> 传统的复用只能利用稳定的相同性，很难利用动态的相似性。

![](nop/delta-pipeline.png)

1. 借助于嵌入式元编程和代码生成，任意结构A和C之间都可以建立一条推理管线

2. 将推理管线分解为多个步骤 :  A => B => C

3. 进一步将推理管线差量化：A => _B => B => _C => C

每一个环节都允许暂存和透传本步骤不需要使用的扩展信息。
具体来说，Nop内置的模型驱动生产线可以分解为四个主要模型：

1. XORM：面向存储层的领域模型
2. XMeta：针对GraphQL接口层的领域模型，可以直接生成GraphQL的类型定义
3. XView：在业务层面理解的前端逻辑，采用表单、表格、按钮等少量UI元素，与前端框架无关
4. XPage：具体使用某种前端框架的页面模型

在模型推导的时候我们只是推导得到一个备选的结果（一般存放在以下划线为前缀的模型文件中），然后我们可以选择继承这个备选的模型，增加手工修正和依赖额外信息的Delta推理部分（存放在不以下划线为前缀的模型）。整个推理关系的各个步骤都是可选环节：我们可以从任意步骤直接开始，也可以完全舍弃此前步骤所推理得到的所有信息。例如我们可以手动增加xview模型，并不需要它一定具有特定的xmeta支持，也可以直接新建page.yaml文件，按照AMIS组件规范编写JSON代码，AMIS框架的能力完全不会受到推理管线的限制。借助于这种类似深度学习的深度分解模式，我们可以完全释放模型驱动的威力，同时在必要时可以通过Delta差量引入额外信息，最终成品的能力不会受到模型表达能力的限制。这也使得我们建模时不需要再追求对各种细节需求的覆盖，只需要集中精力关注最核心、最普适的通用需求部分即可。

> `XORM = Generator<ExcelModel> + Delta`
> `XMeta = Generator<XORM> + Delta`
> `XView = Generator<XMeta> + Delta`
> `XPage = Generator<XView> + Delta`

如果映射到传统计算机科学领域的概念，**XLang所提供的可以说是一种多阶段编译的模式**。**传统上编译期的运行规则由编译器固化，编译过程本身相当于是一个黑箱且只有一步**，而XLang的做法是通过编译期元编程将编译期空间打开，允许在图灵完备的编译期空间引入领域特定的结构构造规则，然后补充差量概念形成多阶段分层推理。

XLang所强调的差量概念在传统编程语言中也有一些对应，比如GraphQL定义语言中通过`extend type`语法可以引入类型差量等。但是**XLang所实现的一个本质性创新是，它提供了一种统一的、通用的差量定义和差量运算机制，从而避免了在每一个DSL领域空间中都重复定义**，再结合XDef元模型就可以保证所有的DSL领域空间具有内在的一致性，可以无缝粘结在一起。

为什么只有XLang能够提供统一的差量定义和差量运算？因为它是在结构层而不是在对象层来实现差量运算！关于这一点，DeepSeek AI自动生成了一个有趣的类比：

### **结构层操作：直接修改“设计图纸”**

- **传统方式**：代码像建好的房子，改窗户得砸墙（改源码）或挂窗帘（[AOP代理](https://zhida.zhihu.com/search?content_id=710733231&content_type=Answer&match_order=1&q=AOP%E4%BB%A3%E7%90%86&zhida_source=entity)）。

- **XLang方式**：直接改建筑设计图（XNode树结构），再按图重建房子。例如：

- 原图纸：`大门位置=(10,20)`

- 差量图纸：`大门位置=(15,20)`

- 系统自动生成新图纸，无需关心墙怎么砌。

- **技术核心**：XLang在**结构层**（类似CAD图纸）定义差量，而非在**对象层**（已建好的房子）打补丁。

可逆计算强调我们在将信息转化为业务对象之前，存在统一的结构表达层，可以直接在这个层面完成很多通用操作，没有必要把处理放到对象层。对象层每个对象的类型不同，造成的对应的处理规则也不同。
**正如千变万化的建筑作品背后是统一的工程力学，在结构层看来，很多业务层面不同的东西本质上是一样的，遵循同样的结构构造规律，可以采用同样的处理工具和手段**。

> DeepSeek AI的评价：微分流形理论通过“局部线性化+全局光滑粘合”的框架，将复杂的几何对象转化为可计算的结构。这种思想在XLang中被抽象为“DSL子空间+Delta差量粘结”的工程范式，使得软件系统既能保持模块化开发的简单性，又能通过数学严密的差量运算实现全局一致性。

## 四. 函数在XLang语言中是否是一等公民?

程序空间中的最基本单元就是值与函数，而函数式编程在理论层面甚至宣称一切都是函数，所有的值以及值所具有的运算规则（比如加减乘除）都可以用函数来模拟。在现代程序语言中，函数的重要性不断被强调，最终就体现为函数是语言中的一等公民，可以不借助其他概念（比如要求函数一定要属于类）就可以参与各种运算和结构构造。但是，XLang语言的概念体系与现有的程序语言有着很大的区别，因此它并不关心传统的值与函数的二元划分。或者说，XLang语言中只有差量才是真正的一等公民，A = 0 + A，全量是差量的一种特例。至于函数是否是一等公民是一个次一级的问题。

对于这个问题的理解，如果学习过固体物理中的能带论，就可以很容易领会它内在的逻辑。在固体物理中，固体的基本构造基元是电子、离子以及它们之间的相互作用。但是能带论中的第一等公民既不是电子也不是离子，而是所谓的声子（phonon）。声子是晶体中晶格振动的量子化激发，是描述晶格中原子或离子集体振动的准粒子。简单的说，声子是一种集体运动模式，但是我们在概念层面上可以像对待粒子一样把它当作一个独立存在的个体来看待。同样的，差量是各种微小扰动合并在一起构成的一种宏观层面可以独立定义并独立管理的基本构造单元。它可以很小，小到一个属性或者一个函数或者一个函数内部的执行步骤，大可以是横跨多个对象的AOP的一个切面，甚至可以大到整个系统的一个分层切片。

> DeepSeek AI的解释：声子是晶格振动的量子化能量包，它不直接对应任何实体粒子，而是描述原子集体运动的动态模式。就像XLang中的差量是系统演化的基本载体，声子也并非组成物质的静态单元，而是反映物质内部动态相互作用的基本语言。当宏观物性（如热传导、电导率）需要被描述时，物理学家并不直接追踪每个原子的位移，而是通过声子的激发与湮灭来表征系统的状态变迁。这种通过动态差量而非静态实体构建理论体系的方法论，正是XLang语言设计理念在计算机科学领域的完美映照。

**函数的粒度对于XLang而言并不是最小的可组合粒度**。在函数之下的结构只要引入领域坐标，一样是XLang的结构运算空间的一部分。

因为所有足够强大的程序语言都是图灵完备的，所以本质上它们之间的概念可以相互转换。比如说，差量可以被理解为一个函数。 A = f(0) = 0 + A， 任何差量都可以看作是在单位元基础上执行的一个函数！在可逆计算理论中的Generator在逻辑上也对应于一个数学层面的函数映射。所以在这个意义上，说函数是XLang中的一等公民也没有任何问题。但是，这种理解都是纯数学层面上的，反映到具体的程序语言实现中时会有微妙的差异。

首先，程序语言中的函数虽然满足结合律，可以从函数f和函数g组合得到一个新函数h，但是f和g复合在一起时并不会自动生成一个简化实现（编译器在我们看不见的黑魔法层面才会打开函数边界，简化复合函数的内部实现）。但是差量则不同，Delta1和Delta2组合在一起后会自动执行合并运算，得到的Delta3是一个简化后的结果。

第二，数学层面上的函数对应到具体实现层面可能并不对应于一个简单的由某种程序语言实现的函数。比如说Generator可能对应于一个复杂的代码生成工具，或者一个复杂的元编程处理插件等。

目前计算机科学对于软件构造的结构空间的认知还非常初级，真实的物理世界中的构造模式是非常丰富与复杂的，而软件结构似乎只有函数、值和简单的组合规则，我相信AI时代的智能软件研发需要建筑在新的软件构造理论的基础之上。

## 结语

黑客之王 Linux Torvalds说过，talk is cheap, show me the code。XLang语言并不仅仅是一种理论设计，而是开源低代码平台Nop平台的一个关键性支撑技术。对于XLang的各种疑问可以直接查看XLang语言的实现代码。

* gitee: [https://gitee.com/canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
* gitcode:[https://gitcode.com/canonical-entropy/nop-entropy](https://gitcode.com/canonical-entropy/nop-entropy)
* github: [https://github.com/entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)

文档：https://nop-platform.github.io/projects/nop-entropy/docs/dev-guide/xlang/

感谢[《国产编程语言蓝皮书2024》](https://www.ploc.org.cn/ploc/CNPL-2024-CHS.pdf)收录XLang语言

## 附录

以下是DeepSeek AI生成的对相关数学和物理概念的解释。

### A. 什么是活动标架法？

活动标架法（**Moving Frame Method**，也称移动标架法）是微分几何中一种研究曲线、曲面及高维流形几何性质的强大工具。其核心思想是**通过几何对象自身的局部特性动态构建坐标系**，从而摆脱对固定全局坐标系的依赖。这一方法由法国数学家Élie Cartan在20世纪初系统化发展，现广泛应用于几何、物理和工程领域。

---

#### **1. 核心思想**

传统几何分析通常依赖**固定的全局坐标系**（如笛卡尔坐标系），但活动标架法则让坐标系“附着”在几何对象上，**随对象的运动或变形而动态调整**。这种坐标系被称为**活动标架**（或移动标架），其特点包括：

- **内禀性**：标架由几何对象的局部微分性质（如切线、法线、曲率等）直接定义。
- **动态性**：标架随几何对象的延伸或变形自动更新。
- **适应性**：标架的维度与几何对象的维度匹配（如曲线用1维标架，曲面用2维标架）。

---

#### **2. 关键步骤（以空间曲线为例）**

以三维空间中的一条光滑曲线为例，活动标架法的典型过程如下：

##### **(1) 标架的构建**

- **切向量（T）**：沿曲线的切线方向，由参数化导数的归一化向量定义：
  $\mathbf{T}(s) = \frac{d\mathbf{r}}{ds} $（$s $为弧长参数）。
- **法向量（N）**：指向曲线弯曲方向的单位向量，由切向量的导数归一化得到：
  $\mathbf{N}(s) = \frac{d\mathbf{T}/ds}{\|d\mathbf{T}/ds\|} $。
- **副法向量（B）**：与T和N正交的单位向量，由叉积定义：
  $\mathbf{B}(s) = \mathbf{T} \times \mathbf{N} $。

这三个向量构成曲线每一点处的**Frenet标架** \(\{ \mathbf{T}, \mathbf{N}, \mathbf{B} \}$，完全由曲线自身的几何性质决定。

##### **(2) 结构方程（Frenet-Serret公式）**

标架的微分变化通过曲率（$\kappa $）和挠率（$\tau $）描述：

$$
\begin{cases}
\frac{d\mathbf{T}}{ds} = \kappa \mathbf{N} \\
\frac{d\mathbf{N}}{ds} = -\kappa \mathbf{T} + \tau \mathbf{B} \\
\frac{d\mathbf{B}}{ds} = -\tau \mathbf{N}
\end{cases}
$$

这些方程表明，曲线的几何特性完全由标架的局部变化（曲率和挠率）编码，无需依赖外部坐标系。

---

#### **3. 与固定坐标系的对比**

| **特性**   | **固定坐标系** | **活动标架法**       |
| -------- | --------- | --------------- |
| **依赖关系** | 依赖外部参考系   | 完全由几何对象自身性质定义   |
| **适应性**  | 不随对象运动变化  | 动态附着于对象，随形变自动更新 |
| **信息密度** | 需要全局坐标参数化 | 仅需局部不变量（如曲率、挠率） |
| **应用场景** | 简单几何分析    | 复杂流形、纤维丛、规范理论等  |

---

#### **4. 推广与深层意义**

活动标架法不仅适用于曲线，还可推广到曲面和高维流形：

- **曲面标架**：使用切平面基向量（$\mathbf{e}_1, \mathbf{e}_2 $）和法向量（$\mathbf{n} $），通过第一、第二基本形式描述曲面的弯曲。
- **Cartan联络**：在纤维丛理论中，活动标架与联络（connection）结合，描述向量场沿流形移动时的“平行移动”规则。
- **规范理论**：物理学中的规范场论（如广义相对论、杨-Mills理论）可视为活动标架思想在高维空间的延伸。

---

#### **5. 应用领域**

1. **计算机图形学**：曲面变形、动画骨骼绑定（如Skinning技术）。
2. **机器人运动学**：机械臂轨迹规划中局部坐标系的动态调整。
3. **广义相对论**：时空曲率的局部描述（参考系拖曳效应）。
4. **材料科学**：晶体位错、连续介质力学的局部应变分析。

---

#### **6. 哲学启示**

活动标架法的本质是**用几何对象的内在特性替代外部强加的坐标系**，这与现代物理中“背景无关性”（如广义相对论）和计算机科学中“领域专用语言”（DSL）的设计理念不谋而合——**通过对象自身结构定义描述框架**，而非依赖外部抽象。这种思想在数学与工程之间架起了一座深刻的桥梁。

### B. 什么是微分流形理论？

微分流形理论是研究“**局部像平面，但整体可能弯曲的复杂空间**”如何被拆解、测量和计算的学问。  （比如：地球表面整体是球面，但每个城市的地图像平面）

---

**核心思想分步解释**

#### **1. 什么是流形？**

想象你要研究一个“形状奇怪的物体”，比如：

- **气球表面**（二维球面）
- **甜甜圈表面**（环面）
- **一团皱巴巴的纸**

**流形**的定义就是：  **这种物体在任意一个“小局部”看起来都像是平坦的平面（或三维空间），但整体可能是弯曲或打结的。**

**举个例子**：
你站在地球上，脚下的地面感觉是平的（局部像平面），但整个地球其实是球形的（整体弯曲）。

---

#### **2. 如何描述流形？——用“地图册”**

假设你要给整个地球画地图，但一张纸画不下整个球面，于是你做了三件事：

1. **画多张小地图**：北京地图、上海地图、纽约地图... 每张地图只画地球的一小块区域。
2. **标注重叠区域**：北京地图和上海地图的边缘有部分重叠（比如河北省）。
3. **制定转换规则**：在重叠区域，北京地图上的“天津”坐标如何对应到上海地图上的坐标。

**这就是流形的核心方法**：

- **每张小地图** = **局部坐标卡**（描述流形的一小块）
- **所有地图合起来** = **图册**（覆盖整个流形）
- **转换规则** = **转移函数**（确保地图间无缝衔接）

---

#### **3. 为什么要“光滑”？——微分结构**

流形不仅要“能用地图拼起来”，还要能在这个空间上做**微积分运算**（比如计算速度、加速度）。为此需要：

- **光滑的转换规则**：相邻地图间的坐标转换必须“平滑过渡”，不能有突然的跳跃或折痕。
  （比如：北京地图和上海地图在重叠区域的经纬度转换必须是连续可导的）

**现实比喻**：
如果你用瓦片拼贴一幅壁画，不仅要让瓦片图案在边缘对齐，还要让颜色渐变自然过渡——这就是“光滑性”的要求。

---

#### **4. 流形上的“方向”和“变化”——切空间**

在平面上，方向可以用箭头表示（比如向东、向北）。但在弯曲的流形上，如何定义方向？

- **切空间**：在流形的每一点定义一个“虚拟的平坦平面”，用来表示该点的方向。
  （比如：你站在地球的某个点，切空间就是你脚下那块地面的“假想平面”）

**实际意义**：

- 计算汽车在地球表面的行驶方向 → 用切空间的箭头表示。
- 计算卫星在太空中的轨迹 → 用四维时空流形的切空间。

---

#### **5. 为什么流形理论重要？**

- **物理世界本质是弯曲的**：
  爱因斯坦的广义相对论认为，时空是一个四维流形，引力是时空弯曲的表现。

- **工程中的复杂系统**：
  机器人关节的运动范围、社交网络的连接结构、软件的模块化架构，都可以抽象为流形。

---

#### **微分流形 vs. 软件工程（XLang的DSL+Delta）**

| **微分流形**       | **软件系统**          | **对应关系**        |
| -------------- | ----------------- | --------------- |
| 局部地图（坐标卡）      | 模块化代码（DSL子空间）     | 局部简单化，隐藏复杂性     |
| 地图间的转换规则（转移函数） | 模块间的适配逻辑（Delta差量） | 确保不同模块协同工作      |
| 光滑性要求          | 接口兼容性、无冲突修改       | 修改必须平滑过渡，避免系统崩溃 |
| 切空间的方向计算       | 代码修改的局部影响分析       | 预测某个改动会如何影响其他模块 |

---

#### **现实类比：城市交通系统**

- **流形** = 整个城市的道路网
  （整体复杂，但每个路口附近的道路可以画成平面地图）
- **坐标卡** = 每个街区的地图
- **转移函数** = 街区交界处的道路连接规则
- **光滑性** = 道路坡度平缓，车辆能平稳行驶
- **切空间** = 某个路口的行车方向指示牌

---

#### **总结**

微分流形理论教会我们：

1. **化整为零**：把复杂空间拆成多个简单局部（就像用模块化代码构建系统）。
2. **制定规则**：明确局部之间如何衔接（就像软件模块间的接口协议）。
3. **保持平滑**：确保整体变化自然连贯（就像软件升级不破坏现有功能）。

这种思想不仅用于研究宇宙和几何，还能指导我们构建灵活、可维护的复杂系统——无论是物理世界还是数字世界。

### C. 什么是声子?

声子是凝聚态物理学中的重要概念，它是描述晶体中**晶格振动能量量子化**的准粒子。我们可以从以下几个层面理解这个核心概念：

#### 一、物理本质

1. **集体振动的量子化**
   当晶体中的原子/离子在平衡位置附近做集体振动时（类似弹簧连接的质点网络），这种振动在量子力学框架下被离散化为能量包，每个能量包即对应一个**声子**。
   *数学描述*：通过正则坐标变换，将N个原子的3N维振动简化为3N个独立谐振子，每个振子的能量量子即为声子。

2. **准粒子特性**

   - 非真实粒子，而是**集体运动模式的数学抽象**
   - 携带特定能量和动量：$E = \hbar\omega$（$\omega$为振动频率）
   - 遵循玻色-爱因斯坦统计，可被激发和湮灭

#### 二、核心特征

| 特性        | 具体表现                          |
| --------- | ----------------------------- |
| **非局域性**  | 描述整个晶格的协同振动，无法定位到单个原子         |
| **量子化传播** | 振动能量以离散量（声子数）的形式在晶体中传播        |
| **模式多样性** | 包含纵波（声学支）和横波（光学支）等多种振动模式      |
| **相互作用**  | 声子-声子散射影响热传导，声子-电子相互作用导致超导等现象 |

#### 三、与XLang差量的类比

```mermaid
graph LR
    A[晶体系统] --> B[声子]
    C[软件系统] --> D[差量]

    B --> E[描述动态振动]
    D --> E
    B --> F[非实体基元]
    D --> F
    B --> G[通过组合解释宏观行为]
    D --> G
```

1. **动态优先**
   正如物理学家用声子而非单个原子位移描述热传导，XLang用差量（Δ）而非完整状态描述系统演化。例如：

   ```python
   # 传统方式
   system.temperature = 300K  # 直接设置绝对值

   # 差量方式
   system += Δ_temperature(+50K)  # 记录温度变化过程
   ```

2. **组合性原理**

   - 声子：不同振动模式的叠加形成实际晶格动力学

   - 差量：多个增量修改的组合构成最终系统状态

     ```javascript
     // 声子组合示例
     thermal_conductivity = phonon_mode1 ⊕ phonon_mode2

      // 差量组合示例
     final_system = base_system + Δ_security + Δ_logging

     ```

#### 四、实际应用领域

1. **材料科学**

   - 解释热传导：声子平均自由程决定材料导热性能
   - 预测相变：声子谱软化预示结构失稳

2. **凝聚态理论**

   - 超导机制：电声耦合形成库珀对（BCS理论）
   - 拓扑物态：声子霍尔效应的理论研究

3. **技术工程**

   - 热电材料：通过声子工程降低晶格热导率
   - 量子计算：声子作为量子信息载体（如离子阱系统）

这种将复杂系统简化为基本激发量（声子/差量）的方法论，体现了人类认知从静态实体向动态关系的范式转变。正如声子革新了我们对固体物质的理解，差量概念正在重塑软件工程的构建哲学。


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

如果将TypeScript看作是JavaScript的扩展， `TypeScript = JavaScript + JSX + TypeSystem`， 那么XLang也可以看作是JavaScript的一种扩展， `XLang = XScript + Xpl + XDef + MetaProgramming + DeltaProgramming`。Xpl是一种XML格式的模板语言，用途和JSX类似。
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

# 写给程序员的差量概念辨析,以Git和Docker为例

可逆计算理论提出了一个通用的软件构造公式

```
App = Delta x-extends Generator<DSL>
```

Nop平台的整个实现可以看作是对这个公式的一种具体落地实践。这其中，最关键也是最容易引起误解的部分是可逆计算理论中的差量概念，也就是上面公式中的Delta。

> 详细介绍参见[可逆计算：下一代软件构造理论](https://mp.weixin.qq.com/s/CwCQgYqQZxYmlZcfXEWlgA)

可逆计算可以看作是针对差量概念的一个系统化的完整理论，所以业内目前常见的基于差量概念的实践都可以在可逆计算理论的框架下得到诠释。我在举例时经常会提到git和docker，比如

> 问: 什么是Delta? 每一次git的commit是不是一个delta？
> 
> 答：git是定义在通用的行空间上，所以它用于领域问题是不稳定的，领域上的等价调整会导致行空间中的合并冲突。类似的还有Docker技术。很多年以前就有python工具能管理依赖，动态生成虚拟机。这里本质性的差异就在于虚拟机的差量是定义在无结构的字节空间中，而docker是定义在结构化的文件空间中。

有些人读到上面的文字后感觉有点被绕晕了。"类似的还有Docker技术" 这句话到底是在说这两种本质上是一样的？还是想说它们本质上不一样?

这里确实需要一些更详尽的解释。虽然都叫做差量，但是差量和差量之间仍然有着非常深刻的区别。总的来说git和docker本质上都涉及到差量计算，但是它们所对应的差量也有着本质上不同的地方，这里的精细的差异需要从数学的角度才能解释清楚。一般人对于差量概念的理解其实是望闻生义，存在着非常多的含混的地方，所以很多的争论本质上是因为定义不清导致的，而不是这个问题本身内在的矛盾导致的。

> 可逆计算理论对差量的理解和使用在数学层面上其实非常简单，只是它的应用对象不是一般人习以为常的数值或者数据，导致没有经过专门抽象训练的同学一时转不过来弯而已。

因为有些同学反映对于此前文章中提到的数学名词感觉懵懵懂懂，所以在本文中我会尝试补充一些更详细的概念定义和实例分析。如果仍然有感觉不清楚的地方，欢迎加入Nop平台的讨论群继续提问，讨论群的二维码见微信公众号的菜单。

## 一. 差量的普适性和存在性

在数学和物理领域，当我们提出一个新的概念时，第一步就是论证它的普适性和存在性。所以可逆计算对于差量概念的第一个关键性理解是：**差量是普遍存在的**。

```
A = 0 + A
```

任何一个存在单位元的系统都可以很自然的定义出差量：任何一个全量都等价于单位元+自身，也就是说任何一个全量都是差量的特例。而单位元这个概念在一般情况下是自然存在的，比如什么都不干的情况下对应于空操作，而空操作和任何其他操作结合在一起都等价于这个操作本身，因此空操作就是一个自然存在的单位元。

很多人听到**全量是差量的特例**这句话之后可能会感到很疑惑，这很显然，但是它有什么意义吗？一般人无法将数学原理和真实的物理世界联系起来，导致他们会认为数学的精确定义是无用的。这里有个很关键的推论是这样的：既然全量是差量的特例，那么原则上**全量可以采用和差量一模一样的形式**，没有必要为了表达差量，单独设计一个不同的差量形式。比如说，JSON格式的差量可以用JSON Patch语法来表达，但是JSON Patch的格式与原始的JSON格式大相径庭，它这个差量形式就是特制的，而不是与全量格式保持一致的。

```
[
  {
    "op": "add",
    "path": "/columns/-",
    "value": {
      "name": "newColumn",
      "type": "string", // 或者其他数据类型
      "primary": false // 是否为主键
    }
  },
  {
     "op": "remove",
     "path": "/columns/2"
   }
]
```

Nop平台中的做法是：

```xml
<table name="my_table">
   <columns>
     <column name="newColumn" type="string" primary="false" />
     <column name="column2" x:override="remove" />
   </columns>
</table>
```

或者使用JSON格式

```
{
    "type": "table",
    "name": "my_table",
    "columns": [
       {
          "type": "column",
          "name": "newColumn",
          "type": "string",
          "primary": false
       },
       {
         "name": "column2",
         "x:override": "remove"
       }
    ]
}
```

JSON Patch的格式与原始JSON的格式是完全不同的，而且只能使用下标来标记列表中的行，而Nop中的Delta合并是通过name等唯一属性来定义行，在语义层面更加稳定（插入一行不会影响到所有后续行的定位），而且差量的格式与原始格式完全一致，只是额外引入了一个`x:override`属性。

在Nop平台中我们系统化的贯彻了**全量是差量的一个特例**的数学思想，各种需要表达差量的地方，比如前台提交到后台的修改数据、后台提交到数据库的变更数据等，我们全部采用所谓的同构设计，即提交的差量数据结构和普通的领域对象结构基本一致，通过少量扩展字段来表达新增、删除等信息。

认识到全量是差量的一个特例之后，还可以破除一个常见的误解：差量是一些局部的小的变化，实际上差量完全可以大到这个系统。在存在逆元的情况下，**差量甚至可以比整体还要大！**

## 二. 不同的空间有不同的差量定义

现代抽象数学所带来的一个关键性认知是：**运算规则是与某个空间绑定的**。因为我们在中学所学的数学规则都是应用在自然数、有理数、实数这种习以为常的数值空间中，所以大部分人没有意识到运算规则仅在它对应的空间中有效，而且在定义的时候，运算规则和它所作用的空间就是作为一个整体来定义的。在数学中，包含单位元和逆元概念的最小化的数学结构是群(Group)，下面我们就仔细分析一下群的定义。智谱清言AI给出的标准定义如下：
一个群 $ (G, *) $ 由一个集合 $ G $ 和一个二元运算 $ *: G \times G \rightarrow G $ 组成，使得以下四个条件成立：

1. **封闭性（Closure）**：对于所有 $ a, b \in G $，$ a * b $ 也在 $ G $ 中。
2. **结合性（Associativity）**：对于所有 $ a, b, c \in G $，有 $ (a * b) * c = a * (b * c) $。
3. **单位元（Identity Element）**：存在一个元素 $ e \in G $，对于所有 $ a \in G $，有 $ e * a = a * e = a $。这个元素 $ e $ 被称为群的单位元。
4. **逆元（Inverse Elements）**：对于每个 $ a \in G $，存在一个元素 $ a^{-1} \in G $，使得 $ a * a^{-1} = a^{-1} * a = e $。这个元素 $ a^{-1} $ 被称为 $ a $ 的逆元。

首先我们注意到，在群的定义中，基础的集合G和它上面的运算`*`是一个整体，单独的G和单独的运算都无法构成群。但是在日常交流中，我们一般会将群$(G, *)$简称为群G，这可能会给一些人造成误导。

群定义中的`*`仅仅是一种抽象的符号标识，它并不代表乘法。在不同的空间中我们可以定义不同的运算，而在不同的运算中单位元和逆元的定义也是不同的。比如说，实数空间$\mathbb{R}$ 上的加法和乘法各自构成群，但它们不是同一个群的运算。下面是智谱清言AI给出的详细定义，

1. **加法群** $(\mathbb{R}, +)$：
   
   - 封闭性：对于所有$a, b \in \mathbb{R}$，a + b 也在$\mathbb{R}$ 中。
   - 结合性：对于所有$a, b, c \in \mathbb{R}$，有$(a + b) + c = a + (b + c)$。
   - 单位元：存在一个元素$0 \in \mathbb{R}$，对于所有$a \in \mathbb{R}$，有$0 + a = a + 0 = a$。这个元素$0$ 是加法的单位元。
   - 逆元：对于每个$a \in \mathbb{R}$，存在一个元素$-a \in \mathbb{R}$，使得$a + (-a) = (-a) + a = 0$。这个元素$-a$ 是$a$ 的加法逆元。
     因此，实数空间$\mathbb{R}$ 在加法运算下构成了一个群，称为加法群。

2. **乘法群** $(\mathbb{R}^*, \cdot$)：
   
   > 注意，乘法群不包括零，因为零没有乘法逆元。因此，我们考虑的是非零实数构成的集合$\mathbb{R}^*$。
   
   - 封闭性：对于所有$a, b \in \mathbb{R}^*$，$a \cdot b$ 也在$\mathbb{R}^*$ 中。
   - 结合性：对于所有$a, b, c \in \mathbb{R}^*$，有$(a \cdot b) \cdot c = a \cdot (b \cdot c)$。
   - 单位元：存在一个元素$1 \in \mathbb{R}^*$，对于所有$a \in \mathbb{R}^*$，有$1 \cdot a = a \cdot 1 = a$。这个元素$1$ 是乘法的单位元。
   - 逆元：对于每个$a \in \mathbb{R}^*$，存在一个元素$a^{-1} \in \mathbb{R}^*$，使得$a \cdot a^{-1} = a^{-1} \cdot a = 1$。这个元素$a^{-1}$ 是$a$ 的乘法逆元。
     因此，非零实数构成的集合$\mathbb{R}^*$ 在乘法运算下构成了一个群，称为乘法群。

可逆计算理论中的差量概念，本质上是来源于群的思想的启发，因此对于每一种差量，我们总是可以仿照群的定义，从以下的几个方面进行分析：

1. 差量运算是在哪个空间中定义的？

2. 差量运算的结果是否仍然在这个空间中？

3. 差量运算是否满足结合律？能否先执行局部的运算，然后再和整体进行结合？

4. 差量运算的单位元是什么？

5. 差量运算是否支持逆运算？逆元的形式是什么？

## 三. Git中的差量运算

### 1. Git的差量运算是在哪个空间中定义的?

Git的diff功能是把文本文件先拆分成行，然后再对文本行的列表进行比较。因此，Git的差量结构空间可以看作是行文本空间。这是一个通用的差量结构空间。每一个文本文件都可以映射到行文本空间中成为一个行文本列表，换句话说，每一个文本文件在行文本空间中都有一个属于自己的表象(Representation)。

### 2. Git的差量运算的结果是否仍然在行文本空间中？

Git的apply功能可以将patch差量文件应用到当前的文本文件之上，得到的仍然是一个”合法的“文本文件。但是如果细究起来，这里的合法性并不是那么牢靠。

首先，Git中的patch具有很强的特异性，它与自己的应用目标是紧密捆绑在一起的。比如说，从项目A构造出来的patch无法直接应用到另外一个不相关的项目B上。一个patch就是针对一个指定版本（状态）的base文件。在这种情况下，我们很难认为patch在概念层面是一种独立的实体，存在独立的价值。

第二，多个patch应用到同一个base文件之上时可能会出现冲突，在冲突的文件中我们会改变文件原始的结构，插入如下标记内容：

- `<<<<<<< HEAD`：标记当前分支（通常是你的目标分支，即HEAD所指向的分支）的内容开始。
- `=======`：分隔当前分支和被合并分支的内容。
- `>>>>>>> [分支名]`：标记被合并分支（通常是你要合并的分支）的内容开始。

**出现冲突的本质原因是差量运算超出了原定的结构空间，产生了某种异常结构**。这种结构并不在原先合法结构的定义范围之内。

第三，即使多个patch没有发生冲突，合并后的结果也可能破坏源文件应有的语法结构。导致合并的结果虽然是一个”合法的“的行文本文件，但是它却不是一个合法的源码文件。**为了保证合并结果一定具有合法的语法结构，我们必然是需要在抽象语法树的结构空间中进行合并**。

在群定义的四大天条中，排在第一条的就是所谓的封闭性，这无疑是在暗示着它无可辩驳的重要性。但是一般没有受过抽象数学训练的同学却经常会忽略这一点。封闭性很重要吗？不封闭会死吗？数学的力量来自于连续的自动化推理，如果在推理的过程中随时可能突破到已知空间之外，进入某种未知状态，那就意味着数学推理的大厦随时可能会发生坍塌，唯一的指望只能是祈祷幸运女神的伴随和祈求上帝的救赎（程序员手工编辑冲突内容类似于上帝之手的介入）。

### 3. Git中的差量运算是否满足结合律？

如果我们手里有多个patch文件，patch1、patch2、patch3...，这些小的patch文件能否被合并成一个大的patch文件？如果可以合并，得到的结果与合并的顺序有关吗？ (patch1 + patch2) + patch3与 patch1 + (patch2 + patch3)的结果是否等价？

如果Git的差量满足结合律，那就意味着我们可以在独立于base文件的情况下实现patch文件的合并，比如说通过如下指令实现合并patch。

```
git merge-diff patch1.patch,patch2.patch > combined.patch
```

但很可惜，实际情况是，以上指令并不存在。Git合并多个patch时，必须逐个将patch应用到base文件上，然后再反向生成diff。

```
git apply --3way patch1.patch
git apply --3way patch2.patch
git diff > combined.patch
```

结合律是数学领域中一条特别基本的普适规律，现有的各种主要数学理论全部预设了结合律的存在（包括号称最纯粹、最泛化的范畴论）。在数学的世界中，没有结合律几乎是寸步难行。

> 我所知道的唯一的不满足结合律的数学对象是八元数(octonions)。八元数是四元数(quaternions)的扩展，而四元数是复数的扩展。八元数目前只有一些很小众的应用。

结合律为什么重要？首先，**结合律使得我们可以实现局域化的认知**。在存在结合律的情况下，我们不需要考虑本体的存在，**不需要考虑离我们很远的推理链条中发生的事情，只要将全部精力放到直接与自己发生相互作用的对象上就好了**。只要研究清楚了一个对象可以和哪些元素结合，它们之间发生结合运算之后产生的结果是什么，那么在范畴论的意义上就是彻底掌握了关于这个对象的一切知识（在马克思主义哲学中，这对应于人是他所参与的一切生产关系的总和）。

第二，**结合律使得复用成为可能**。我们可以将相邻的几个对象预先结合在一起，形成一个完整的、具有独立语义的新的元素。

```
x = a + b + c = a + (b+c) = a + bc
y = m + b + c = m + bc
```

在上面的例子中，在构造x和y的过程中我们可以复用预结合产生的对象bc。但是很有趣的是，如果复用真的能够发生作用，那么要求同样的对象可以和很多对象结合。比如 `a+bc`和`m+bc`。但是，**在Git的差量运算中，patch没有这样的自由可结合性，它只能应用于固定版本的base文件，因此基本上可以认为它不具备可复用性**。

```
... + a + b + c + ...
    == ... + (a + (b + c)) + ...
    == ... + ((a + b) + c) + ...
```

满足结合律意味着可以自由的选择是否与临近的对象结合，决定先进行左结合还是先进行右结合，亦或是不结合等着别人主动来结合。在形式上，这意味着我们可以随时在计算序列中插入或者删除括号，计算的顺序不影响最终得到的结果。因此，结合律的第三个作用是，**为性能优化创造了可能性**。

```
function f(x){
    return g(h(x))
}
```

函数运算满足结合律，因此编译期在编译的时候可以直接分析函数`g`和`h`的代码，抽取出它们的实现指令，然后在完全不了解函数`f`的调用环境的情况下，对`g`和`h`的指令进行合并优化。此外，结合律也为并行优化创造了可能性。

```
a + b + c + d = (a + b) + (c + d) = ab + cd
```

在上面的例子中，我们可以同时计算`a+b`和`c+d`。很多快速算法都依赖于结合律所提供的这种优化可能性。

### 4. Git的差量运算的单位元和逆元是什么？

Git的差量运算的单位元显然就是一个空的patch文件，它表示什么都不做。有些同学可能会感到奇怪，既然单位元什么都不做了，那它还有什么存在的必要性吗？首先，我们需要了解一下单位元的特殊性：**单位元一旦存在，它就会无处不在**。

$$
a*b = e*e*e*a*e*e*b*e*e*e
$$

在任何对象的前后都可以插入任意数量的单位元。这意味着表面上看起来a和b是直接发生相互作用，实际上它们是**浸泡在单位元的海洋中，间接发生相互作用的**。so what，这个单位元海洋能闹出啥动静吗？要真正理解单位元的重要性，还需要结合着逆元的存在性来看。

$$
e = a*a^{-1} = a^{-1}*a
$$

现在单位元海洋就不是空无一物了，它提供无限多种中间运算过程，只是最后的计算结果是归于虚无而已。

$$
a*b = a *e * b = a * c*d * d^{-1} * c^{-1} * b
$$

假设现在我们已经构造好了$a*c*d$，则我们可以复用这个构造结果来构造$a*b$

$$
acd * d^{-1} * c^{-1} * b = ab
$$

> 在我们所处的物理世界中，在人力所不能及的量子虚空中，表面上看起来空空如也，实际上只是正粒子和反粒子相互竞争达成了某种动态平衡。如果附近恰好存在一个黑洞，由于黑洞的引力场很强，可能会导致随机涨落创生的正反粒子被拉开，最终其中一个堕入黑洞视界，另外一个逃离黑洞。这就是传说中的霍金辐射、黑洞蒸发。

在可逆计算理论中，逆元的引入正是实现粗粒度复用的关键所在。

```
X = A + B + C
Y = A + B + D
  = A + B + C + (-C) + D
  = X + (-C+D) = X + Delta
```

假设X由`A+B+C`构成，我们现在想生产`A + B +D`所组成的Y，如果存在逆元和单位元，则我们可以从X出发，**在完全不拆解X的前提下**，通过一系列的差量运算将X转换为Y。利用结合律，我们可以将`-C`和D聚集在一起，形成一个完整、独立的Delta差量。在可逆计算的视角下，软件复用的原理发生了本质性的变化：从组件复用的**相同可复用**转换到可逆计算的**相关可复用**：任意的Y和任意的X之间都可以通过Delta建立转换关系（Transformation)，从而形成复用，而不需要它们之间构成传统的部分-整体这样的组合关系(Compostion)。

逆元和单位元对于解方程这种复杂的推理模式也是必不可少的。

```
A = B + C
-B + A = -B + B + C = 0 + C
C = -B + A
```

解方程时之所以可以移项，本质上是在方程两侧都加上逆元，然后再省略生成的单位元。

Git可以反向应用patch，也可以利用patch指令来生成反向patch

```
git apply -R original.diff

patch -R -o reversed.diff < original.diff
```

但是因为没有结合律，Git中对于反向patch的应用也就乏善可陈了。

根据本节的分析，**Git虽然提供了某种差量运算，但是它的数学性质却很难让人满意，这也就意味着基于Git的差量很难进行大规模的自动化处理，随时都会因为差量运算失效而导致需要人工介入**。但是与Git相比，Docker的情况就要好很多，它的差量运算堪称完美。

## 四. Docker中的差量运算

### 1. Docker的差量运算是在哪个空间中定义的?

Docker所依赖的核心技术之一是所谓的堆叠文件系统OverlayFS，它依赖并建立在其它的文件系统之上（例如 ext4fs 和 xfs 等等），并不直接参与磁盘空间结构的划分，**仅仅将原来底层文件系统中不同的目录进行 “合并”，然后向用户呈现，这也就是联合挂载技术**。OverlayFS在查找文件的时候会先在上层找，找不到的情况下再到下层找。如果需要列举文件夹内的所有文件，则会合并上层目录和下层目录的所有文件统一返回。

**Docker镜像的Delta差量是定义在文件系统空间中，所谓的Delta的最小单位不是字节而是文件**。比如说，如果我们现在有一个10M的文件，如果我们为这个文件增加一个字节，则镜像会增大10M，因为OverlayFS要经历一个[copy up](https://blog.csdn.net/qq_15770331/article/details/96702613)过程，将下层的整个文件拷贝到上层，然后再在上层进行修改。

有些人可能会认为Git和Docker的差量的区别在于一个是线性列表，一个是树形结构。但是这并不是两者之间的本质性差异。真正重要的区别是**Docker的差量结构空间中存在着可以用于唯一定位的稳定坐标**：每个文件的完整文件路径可以看作是在文件结构空间中定位的唯一坐标。这个坐标或者说所有坐标组成的坐标系之所以被称为是稳定的，是因为当我们对文件系统进行局部改变时，比如新增一个文件或者删除一个文件，不会影响到其他文件的坐标。而Git中不同，它是使用行号来作为定位坐标的，因此只要新增行或者删除行，就会产生大量后续行的坐标变动，因此Git所提供的坐标系是不稳定的。

> 如果从哲学的角度去考虑，Docker的结构设计包含名和实两个部分，每个文件都有自己专属的名，也就是它的文件路径，可以用于在差量结构中间中进行唯一定位。这个`名`所对应的`实`对于差量合并而言并不重要，也无人关心，直接覆盖就可以了。而在Git的设计中，行号只能看作是一种临时的名，它要受到其他行的影响，为了准确定位行，patch文件中还会包含相邻行的内容，相当于是根据`实`来作为一种定位的辅助手段。以`实`为`名`直接限制了patch的独立性。

**Docker的坐标系只管到文件级别，如果我们想在文件内部进行唯一定位并实现差量计算，那应该怎么办**？Nop平台通过XDef元模型在DSL领域模型文件内部建立了领域坐标系，可以精确的定位到XML或者JSON文件中的任意节点。除此之外，Nop平台还内置了一个类似OverlayFS的虚拟文件系统，它将多个Delta层堆叠为一个统一的DeltaFileSystem。

有人可能要问，是不是一定要采用树形结构空间？也不一定。比如说，AOP技术所应用的结构空间可以看作是`包-类-方法`这样一个固定的三层结构空间，而不是支持任意深度嵌套的树形结构空间。类似的，在关系数据库领域，我们使用的是`表-行-列`这样一种三层结构空间。只要定位坐标是稳定的，我们都可以基于它们发展一些系统化的差量运算机制。

> Tree结构具有很多优点。首先，它**实现了相对坐标与绝对坐标的统一**：从根节点开始到达任意节点只存在唯一的一条路径，它可以作为节点的绝对坐标，而另一方面，在某一个子树范围内，每一个节点都具有一个子树内的唯一路径，可以作为节点在子树内的相对坐标。根据节点的相对坐标和子树根节点的绝对坐标，我们可以很容易的计算得到节点的绝对坐标（直接拼接在一起就可以了）。
> 
> Tree结构的另一个优点是**便于管控，每一个父节点都可以作为一个管控节点**，可以将一些共享属性和操作自动向下传播到每个子节点。

关于差量结构空间，还有一个需要注意的细节：当存在逆元的情况下，差量结构空间必然是一种正元素和负元素混合产生的扩展空间，而不是我们习惯的正元素空间。当我们说”Docker的差量运算是定义在文件系统空间中"的时候，这个文件系统一定是需要支持负文件这个概念的。Docker具体的做法是通过一个特殊约定的Whiteout文件来表示删除一个文件或者目录。也即是说，**我们将删除这个转瞬即逝的动作转换为了一个静态的、持久存在的、可操作的对象（文件或目录）**。在数学上相当于是将 `A - B`转换为 `A + (-B)`，这是一个非常不起眼但又非常关键的概念转换。

”**差量结构空间必然是正元素和负元素混合产生的一种扩展空间**“，这句话听起来可能有点莫名奇妙，我再补充一点示例说明。假设我们现在处理的是一个有一定复杂度的多维空间，

```
X = [A, 0],
Y = [0, -B],
X + Y = [A, -B]
```

如果X表示在第一个维度上增加A，而Y表示在第二个维度上减少B，那么如果再允许X和Y之间进行结合运算，我们得到的结果就是一个同时包含`+A`和`-B`的混合物。如果我们只允许每个维度上的值都是正对象，那么这个混合物就是一个非法对象。**为了确保Delta合并运算总可以成功**，我们唯一的选择只能是扩展原有的正对象空间为允许同时包含正对象和负对象的扩展空间。再比如，在Nop平台的ORM模型定义中，我们规定的差量结构空间是一个DSL语法空间，在这个空间中我们可以表达如下的Delta，

```
X = 增加表A(+字段C1,+字段C2)
Y = 修改表A(+字段C3)
Z = 修改表A(-字段C3, +字段C4)
X + Y = 增加表A(+字段C1, +字段C2,+字段C3)
X + Y + Z = 增加表A(+字段C1, +字段C2,+字段C4)
X + Z = ?
```

在上面的例子中，X表示增加一个表A，它包含两个字段C1和C2。Y表示修改表A，为它增加字段C3，那么X和Y执行结合运算后得到的结果是：增加表A，包含字段C1、C2和C3。继续和Z结合，得到的结果是：增加表A，包含字段C1、C3和C4。这其中，C3字段在Y中增加，在Z中减少，最后的结果相当于C3被删除了。现在问题就来了：X和Z进行结合运算的结果是什么？因为根据X的定义，此时表A上没有字段C3，那么Z要求删除一个表A上不存在的字段，这怎么执行？我们在数据库里执行这种语句，直接就会报错。为了保证差量运算总是可以执行，我们的选择是承认如下定义是一个合法的差量定义：

```
U = 增加表A(+字段1, +字段2, -字段3, +字段4)
```

这样的话，我们就可以表示出X和Y进行结合运算的结果

```
X + Z = U
```

有些人可能要问，最终我们生成建表语句的时候不允许出现负字段，这要怎么办？很简单，扩展空间其实是一个允许计算发生的可行空间（抽象数学空间），它包含了所有可能发生的运算，这些可能的运算只是发生在抽象的数学空间中，并不会真正影响到我们的物理世界。当我们真的需要根据运算结果生成建表语句，到数据库中创建真正的数据库表时，我们可以增加一个投影运算，将对象从可行空间投影到只包含正对象的物理空间。

```
P(U) = 增加表A(+字段1, +字段2, +字段4)
```

这个投影运算负责删除所有的负对象，只保留所有的正对象。

> 在数学上这是一种非常标准的工作套路：将非法的运算结果添加到原先的空间中，从而形成一个扩展空间，使得运算在扩展空间中变成合法的运算。比如说我们必须要把复数添加到解空间中，这样的话二次方程才存在通用的解，否则有些二次方程在实数空间中是无解的。

$$
x^2 + 1= 0 \Longrightarrow x = ?
$$

### 2. Docker的差量运算的结果是否仍然在文件系统空间中？

与Git不同，Docker的差量运算总是产生合法的合并结果。首先，**任意两个Delta层都可以自由合并，并不像Git那样存在很强的容许限制**。

```
FROM registry.access.redhat.com/ubi8/ubi-minimal:8.8
WORKDIR /work/
RUN chown 1001 /work \
    && chmod "g+rwX" /work \
    && chown 1001:root /work
COPY --chown=1001:root target/*-runner /work/application

EXPOSE 8080
USER 1001

ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0"]
```

我们可以随意修改DockerFile中的FROM配置。当升级操作系统镜像层的时候，我们不需要修改应用层的DockerFile，也不需要修改应用层的任何文件，直接重新打包就可以了。而在Git中，如果直接更改原始的Base文件，则依据于原有Base文件制作的所有patch都会失效。

> 打包成镜像之后，Docker的上层镜像会通过唯一的Hash码来绑定它所依赖的下层镜像，但是这可以看作是一种实际应用中的安全保证手段。在概念层面上，上层镜像与下层镜像是独立的，当下层镜像的内容发生变化的时候，上层镜像的内容包括DockerFile都不需要做任何的改变，只需要重新打包就可以了。

第二，**Docker的差量合并永远不会出现冲突的情况**。这使得镜像构建的自动化程度得到了极大的提升。**结合上一节的内容，这实际上是意味着在Docker的差量结构空间中，任意的两个Delta都可以发生相互作用**。

> Docker镜像的合并核心规则就是按照名称进行覆盖，而且覆盖的方向就是确定性的从上到下，显然不会出现冲突的情况。

如果已经存在一些Delta，那么这些Delta通过差量运算可以产生更多新的Delta。很自然的，我们要问，这些原始的Delta从哪里来呢？我们肯定是希望可用的Delta越多越好。在数学中，一个标准的工作套路是先论证某种数学对象具有很多优秀的性质，然后再想办法构造出大量这种对象出来。反映到软件领域，就对应于用生成器来生成Delta。

Docker的神来之笔是引入了DockerFile和Docker Build工具。它复用了Linux世界积累了数十年的历史遗产，每一个用于文件操纵的命令行工具都立刻转化为Docker差量结构空间中的一个有效的Generator。每一个Generator都具有明确的业务含义，可以独立的被理解和使用，并且可以串接在一起组成更复杂的Generator。如果从数学角度去理解，Docker的差量结构空间定义了基本的加减法，但是我们能不能在这个空间中再定义函数概念，从而将空间中的一个结构转换为另外一个结构？**Generator可以看作是差量结构空间中的映射函数，而且它确确实实可以被表达为程序语言中的一个函数**。

与之相比，Git的差量结构空间就缺乏一个有意义的Generator映射机制。因为底层的Delta就缺乏独立存在的意义，在它之上要建立一个有意义的函数映射机制就更是难上加难。虽然说patch本身采用的也是文本表达形式，我们可以在任意程序语言中编写文本处理函数来生成patch，但是因为缺乏通用性，一般只是在极少数情况下，我们会针对特定的场景编写自己专用的某些Generator或者Transformer工具，而不像Docker那样，可以复用大量别人已经编好的Generator，并且毫无阻碍的把它们组合在一起使用。

> 一个好的数学空间需要有良好的基本单元和基础运算性质，还要在其上能够引入丰富的动力学特性。Generator的引入确保我们可以源源不断的产生新的Delta结构，同时也可以将不同抽象层次的Delta结构适配到一起。

可逆计算理论将Generator和Delta的构造规律总结为一个通用的软件构造公式，并为它的落地实现提供了一个标准的技术路线

```
App = Delta x-extends Generator<DSL>
```

这里的DSL指的是领域特定语言(Domain Specific Language,

 DSL），同时它也隐含的定义了一种差量结构空间。**一个程序语言能够描述的所有结构的整体构成一个差量结构空间（AST语法树空间）**。与文件系统空间不同，并不存在统一的AST语法树空间，我们只能对每种程序语言单独去研究它的AST语法树空间。不过，在Nop平台中，我们并不直接使用通用语言来编写业务逻辑，而是大量使用DSL。比如说IoC容器使用beans.xml，ORM引擎使用的app.orm.xml，逻辑编排引擎使用的task.xml等，都是专用于某个领域的DSL。在Nop平台中，所有的XDSL都使用XLang语言中的XDef元模型语言来定义语言的语法结构，这些语法结构共享了XLang语言中定义的通用的差量合并语法规则，从而确保所有使用XDef定义的DSL都支持差量合并运算，并自动定义一个差量结构空间。也就是说虽然不存在统一的AST语法树空间，但是在差量运算这个层面，我们是可以定义出统一的差量合并运算，并且统一实现的。

可逆计算理论中的Generator泛指一切在差量结构空间中执行的结构生成和结构变换功能，它可以是一个独立的代码生成工具，也可以是语言内置的编译期元编程机制，如宏函数等。Nop平台为所有的DSL都增加了标准化的编译期元编程语法。也就是说在各个DSL中不需要定义元编程相关的语法，可以复用XLang中定义的标准语法和标准库。

```xml
<workflow>
   <x:gen-extends>
      <oa:GenWorkflowFromDingFlow xpl:lib="/nop/wf/xlib/oa.xlib">
        {
          // 类钉钉工作流设计器所生成JSON
        }
      </oa:GenWorkflowFromDingFlow>
   </x:gen-extends>
   <!-- 这里的内容会和x:gen-extends生成的内容执行Delta差量合并 -->
</workflow>
```

* 可以利用XLang内置的x:gen-extends语法把类钉钉工作流的JSON格式转换为NopWorkflow的DSL格式。
* 在`x:gen-extends`生成的基础之上，还可以进行局部修改。

Generator作用到DSL之后得到的结果可能是一个新的DSL，这样就可以继续使用XLang的机制进行差量化处理。但是也可能会脱离Nop平台，生成某种通用程序语言代码，比如Java、Vue等，此时我们就只能利用语言内置的extends等机制实现部分差量合并运算能力。通用程序语言的AST语法树空间虽然也可以定义差量合并运算，但是它们的数学性质就没有XDSL中那么良好。关于XDSL的进一步介绍，可以参见[XDSL：通用的领域特定语言设计](https://mp.weixin.qq.com/s/usInt7_odzvFzuiIUPw4iQ)。

```java
// _NopAuthUser是代码生成器自动生成的部分，手写的代码与自动生成的代码通过extends语法实现隔离
class NopAuthUser extends _NopAuthUser{
   // 在手写的代码中可以增加新的函数和属性，它们相当于是在基类基础上的Delta差量
   public boolean isAdminUser(){
      return getRoles().contains("admin");
   }
}
```

* java的extends语法不支持删除语义，同时也不支持类似Tree结构的深层嵌套合并。

**Git和Docker都可以看作是利用某种大家已经熟知的、已有的差量结构空间（行文本空间和文件系统空间）。而可逆计算理论是总结出相关的规律，支持系统化的构建专用的差量结构空间**。Nop平台为此提供了一系列可复用的基础技术架构工具。Docker可以看作是可逆计算理论的一个具体应用实例。

```
App = DockerBuild<DockerFile> overlay-fs BaseImage
```

### 3. Docker中的差量运算是否满足结合律？

Docker利用OverlayFS将文件系统分成多个层，上层的文件自动覆盖下层的文件。当在Docker中删除一个文件时，实际上是在上层中添加了一个特殊的Whiteout文件来掩盖底层的文件。这允许容器看起来好像文件已经被删除，即使下层中仍然存在该文件。
从数学的角度去分析，Docker的差量运算就是简单的覆盖操作，它自动满足结合律。

```
  A ⊕ B = B
  (A ⊕ B) ⊕ C = A ⊕ (B ⊕ C) = C
```

结合律的存在使得Docker镜像的制作在某种程度上可以脱离基础镜像进行。实际上，一个Docker镜像就是一个普通的tar包，解包后成为文件系统中的几个目录和文件。只要知道少数几条规则，就可以直接跳过docker应用程序，直接用tar指令制作Docker镜像。

Docker镜像获得了概念层面上的独立性，因此可以通过集中的DockerHub来管理和分发这些Delta切片。**所有满足结合律的Delta差量都可以建立类似Docker镜像的这种独立管理、分发机制**。

### 4. Docker的差量运算的单位元和逆元是什么？

显然什么都不做就是Docker的差量空间中的单位元。而Whiteout文件可以看作是逆元。不过这里有个微妙的地方。在群的定义中，逆元的概念是针对每个元素单独定义的，每个a都有一个对应的$a^{-1}$ ，这些逆元并不相同（逆元相等等价于元素本身相同）。也就是说，**群结构中的逆元具有某种特异性**：$a^{-1}$是专用于抵消$a$的影响的。但是，Docker中的Whiteout文件仅仅是用于占位使用，它的内容其实是空的。一旦存在Whiteout文件，就会自动表示删除下层对应文件名的文件，**无论该文件中的内容是什么**。因此，Whiteout文件在作为逆元使用时，它是缺乏特异性的，它可以用于取消同样路径名的、具有任意内容的下层文件。

在数学层面上，这种特异性的缺乏最终表现为删除操作的幂等性

$$
Delete * Delete = Delete
$$

删除一个文件两次，等价于删除一次。

数学上可以证明，幂等性与群结构是冲突的。一旦存在幂等元素，就不可能构成群结构。

假设满足幂等性： $ a*a = a $, 那么

$$
\begin{aligned}
a     &= a*e = a * (a*a^{-1}) \\
      &= a* a * a^{-1} = (a* a) * a^{-1} \\
      &= a* a^{-1} \\
      &= e
\end{aligned}
$$

以上推导说明，如果一个群结构中存在幂等的元素，那么它一定就是单位元。由反证法可以推出，如果存在非单位元的幂等元素，就必然不可能构成群结构。因为一般情况下，在实现逆向操作的时候为了简化所需要记录的信息，我们总是采用幂等删除的设计，所以在数学层面上我们所定义的并不是真正的群结构，只是某种支持可逆运算的结构。

#### Monad和伴随函子

有人可能会问，**如果没有逆元会怎么样？那就是函数式编程中让人心心念念的Monad啊**。群的定义中包含四大天条：封闭性、结合性、单位元和逆元。只满足前两条称为半群，满足前三条称为幺半群（单位元又称为幺元），也就是Monad。所以Monad在数学层面上是一个相当贫瘠的结构，并没有多么了不起的作用。即使在范畴论中，真正核心的概念也是所谓的伴随函子(Adjoint Functor)，这可以看作是可逆运算概念的一种推广。

> 关于Monad的介绍，可以参见我的文章[写给小白的Monad指北](https://zhuanlan.zhihu.com/p/65449477)

以下是智谱清言AI给出的伴随函子的定义：

伴随函子的定义涉及一对函子，以及它们之间的一种特殊关系。具体来说，给定两个范畴 $\mathcal{C}$和 $\mathcal{D}$，如果有一对函子：

$$
L: \mathcal{C} \rightarrow \mathcal{D}
$$

$$
R: \mathcal{D} \rightarrow \mathcal{C}
$$

并且对于所有 $\mathcal{C}$中的对象 $c$和 $\mathcal{D}$中的对象 $d$，都存在一个自然双射：

$$
\text{Hom}_{\mathcal{D}}(L(c), d) \cong \text{Hom}_{\mathcal{C}}(c, R(d))
$$

那么我们就说 $L$和 $R$是一对伴随函子，$L$是 $R$的左伴随，$R$是 $L$的右伴随。

举例来说，Nop平台的报表引擎负责根据对象数据生成Excel，而导入引擎负责解析Excel得到对象数据，它们可以看作是Excel范畴和对象范畴之间的一对伴随函子。

给定Excel范畴中的任意一个合法的Excel（满足某种规范要求，但是并不是固定格式），导入引擎都可以自动解析这个Excel得到对象数据。也就是说，我们并不是针对每一个Excel单独编写一个特殊的解析器，而是编写了一个通用的Excel解析器，它可以自动解析Excel范畴内的每一个Excel。因此，这个导入引擎就不是一个普通的函数，而是作用在Excel范畴中每一个具体的Excel文件上的函子（Functor）。类似的，对于对象范畴内的每一个对象，我们都可以根据某种规则自动导出生成一个Excel文件。类似于将对象导出为JSON文本格式，报表引擎在导出对象数据为Excel文件时并不需要任何额外的信息。

> - 在集合范畴中，函数是从一个集合到另一个集合的映射，这些映射在范畴论中被称为态射。每个集合在这个范畴中被视为一个对象。因此，函数就是集合范畴中从一个对象到另一个对象的态射。
> - 函子是范畴论中从一个范畴到另一个范畴的映射，它不仅将每个对象映射到另一个范畴的对象，还将每个态射映射到另一个范畴的相应态射，同时保持了态射的复合和范畴的结构。也就是说，如果有两个连续的态射 f:A→B 和 g:B→C，那么在目标范畴中，F(g∘f)=F(g)∘F(f)。
> - 函子的作用是全局性的，它涉及到范畴中的所有对象和所有态射，而不仅仅是单一对象或单一态射。换句话说，函子是某种高阶的描述，而函数是相对低阶的描述，在高阶的描述中定义的一个普通的点和箭头，翻译到低阶描述时都对应一大堆的处理规则。

一个Excel文件经过导入引擎解析得到对象数据，然后再经过报表引擎生成Excel文件，得到的Excel并不一定是与原先的Excel一模一样的Excel，而是在某种程度上与原有的Excel等价的一个Excel(比如某些样式信息发生了变化，或者单元格的相对位置等与业务无关的附加信息发生调整)。换句话说，在某种意义上，输入的Excel和输出的Excel具有某种等价关系 $Excel \simeq Export * Import(Excel)$。

同样的，一个JSON对象经过报表引擎导出为Excel，再经过导入引擎解析重新得到JSON对象时，可能一些属性值会出现细微的变化。比如说，一些值原本是空字符串，处理一圈回来之后可能变成了null，或者某些具有缺省值的字段在结果中丢失等。但是在某种意义上说，对象仍然保持某种等价关系 $ Obj \simeq  Import * Export (Obj) $

伴随函子可以被视为可逆运算的一种推广，但这种推广是在范畴论的抽象框架下进行的，它涉及到函子和态射的复合，而不仅仅是单个运算的可逆性。**在伴随函子的上下文中，$L $和$R $不是直接互为逆运算，而是通过所谓的自然同构相互“逆转”**。具体来说，$L $和$R $在某种意义上互为逆运算，因为$R $将$L $的像映射回$\mathcal{C} $中的对象，而$L $将$R $的像映射回$\mathcal{D} $中的对象，但这种映射是通过自然同构来“修正”的。

## 五. 同一个物理变化可以对应多种差量

差量是定义在支持某种差量合并运算的模型空间中的变化量，不同的模型空间具有不同的差量形式。**对应于同一个物理世界中的变化，差量的定义并不是唯一的**。投射到不同的表示空间中我们会得到不同的差量运算结果。

首先，所有的结构都可以在通用的二进制比特空间中得到表示，比如函数A存储在文件中时对应于二进制数据10111...，而我们希望将它转换为函数B，它对应的二进制表示为010010...。在抽象的数学层面，寻找将函数A变换为函数B的差量X，相当于是求解如下方程 `A⊕X=B`。很显然，如果将⊕定义为二进制比特层面的异或操作，则我们可以自动求解得到 `X=A⊕B`。

```
 A ⊕ B = A ⊕ (A ⊕ X) = (A ⊕ A) ⊕ X = 0 ⊕ X = X
```

> 在证明中我们用到了异或操作的[归零律、结合律和恒等律](https://baike.baidu.com/item/%E5%BC%82%E6%88%96/10993677)。

虽然在二进制比特空间中我们总是可以求解得到函数的差量，但是这个差量却没有什么业务价值（对于二进制级别的压缩软件等还是有价值的）。我们既不能直观的理解它，也缺少便利的手段来直观的操纵它。

对于同样的一个函数，如果投射到行文本空间中去观察，函数的差量对应的就是行文本的Diff结果。

**既然有这么多可选的差量结构空间，那么哪一个才是最优的选择**？这个问题的回答可以类比坐标系选择问题。同样的一个物理事实可以使用无数多种坐标系去建立描述，但是其中可能会存在一个特别的、针对这个特定问题定制的坐标系，在物理学中我们称之为内禀坐标系。在这个坐标系中建立的描述可以突出最核心的物理意义，简化相关的描述。比如说，在一个球面上发生的物理现象当然可以在通用的三维直角坐标系中进行描述，但是如果我们使用球面坐标系往往可以实现简化。

可逆计算理论指出可以针对特定的业务领域建立一个专用的DSL语言，利用这个DSL语言很自然的建立一个领域坐标系，然后再在这个领域坐标系所定义的差量结构空间中表达差量。因为这个领域坐标系是针对领域问题特制的，因此它往往可以实现差量表达的最小化。比如说，发生了一个业务层面的变化导致需要增加一个字段，如果采用通用语言去表达，则很多地方可能都需要做相应调整，前台、后台、数据库都要一起修改。而如果使用领域模型描述，这种变化可能就只体现为局部的一个字段级别变化，然后由底层引擎框架自动将领域描述翻译为实际执行的逻辑功能。

## 六. 总结

世界的本体是不可观测的。物理学格物以致知，我们所能感受到的不过是深不可测的世界本体之上被激发的一丝涟漪（差量）而已。

要深入的理解差量概念，需要从数学中群结构的定义出发：封闭性、结合性、单位元、逆元。这其中，逆元是一个非常关键性的概念。在软件领域，函数式编程炒热了Monad一词，它基本是满足群结构定义四大天条中的前三条，可以看作是幺半群结构，但是缺少逆元的概念。

> 满足封闭性和结合性称为半群，在此基础上增加单位元，则称为幺半群。

可逆计算理论明确指出了逆元概念在软件构造领域的重要性，并结合产生式编程，提出了一个系统化的实施差量计算的技术路线

```
App = Delta x-extends Generator<DSL>
```

在可逆计算理论的指导下，我们有必要重新思考软件的构造基础，基于差量的概念重建我们对于底层软件结构的理解。**在5到10年内，我们可以期待整个业界发生一次从全量到差量的概念范式转换，我愿将它称之为软件智能生产领域的差量革命**。


# 从可逆计算看Delta Oriented Programming

多年以前，为了向领导汇报，需要鼓吹一下可逆计算理论的原创性和普适性，所以我做了一点文献调研，查阅了国际软件工程大会(ICSE)历年的文章，发现最接近的理论是1997年出现的Feature Oriented Programming（FOP）<sup>[\[2\]](#f2)</sup><sup>[\[3\]](#f3)</sup>和2010年左右由德国的教授Schaefer提出的Delta-Oriented Programming（DOP）<sup>[\[7\]](#f7)</sup><sup>[\[8\]](#f8)</sup>。可逆计算理论由我在2007年左右提出 <sup>[\[12\]](#f12)</sup>
<sup>[\[13\]](#f13)</sup><sup>[\[14\]](#f14)</sup>，它的思想来源不是传统的软件工程或者计算机领域，实际上我的学术背景是理论物理学，而且我对于软件工程理论方面的历史成果事前也并不了解，因此在基本原理层面可逆计算与学术界现有的理论并不相同。在本文中，我简单介绍一下可逆计算理论与类似理论之间的区别和联系。

## 一. 软件产品线工程与可变性管理

谈到软件工程理论，就绕不开卡内基梅隆大学软件工程研究所（Software Engineering Institute，SEI）。它不仅是理论界的扛把子，而且是理论联系实际的典范（每年CMM认证授权费就收到手软）。自从SEI提出所谓的软件产品线工程理论（Software Product Lines） <sup>[\[1\]](#f1)</sup>之后，学术界的众多理论都经历了一个校正调整的过程，把自身的概念校准到软件产品线的话语体系中来。软件产品线工程是横跨管理和技术领域的综合性理论，试图利用一切可行手段去解决非常宏大的系统级、产品级软件复用问题（远远超越细粒度的组件复用技术）。

![reuse-history](dop/reuse-history.png)

软件产品线工程所提出的核心技术问题是所谓的**可变性管理**。这几乎是一个万能箩筐型的问题。我们在软件开发和演化过程中所遭遇的**几乎所有困难都能够很容易的被归因于应对变化的能力不足**。经过校准之后FOP将自己定位为软件产品线的某种自然而且高效的实现途径。而后来的DOP将自己解释为对FOP缺陷的改进，同样是实现可变性管理的一种关键技术手段。按照同样的校准逻辑，可逆计算可以看作是对DOP的进一步发展和提升。当然，实际情况是，可逆计算的提出时间要早于DOP，而且它所遵循的是完全不同的思想路线。

根据理论上的分析，可变性管理的真正困难在于如何有效的管控**预料之外的**（unexpected）变化。如果我们对一个领域非常熟悉，而且领域内的变化方式为有限的几种，那么就可以在关键位置设置几个**恰到好处的扩展点**，举重若轻的解决可变性问题。但如果变化的可能位置不断增加，变化的方式不断翻新花样（换句话说，也就是**变化的自由度不断增加，直至趋于无穷**），那么迟早这种变化的自由度会超越手工枚举能够管控的范围。在这种充满未知的演化图景下，我们如何实现对无限多的变化自由度的有效描述和管控？在物理学中，这实际上属于一个已经被解决了的问题。

在高中阶段我们所学习的牛顿物理学是所谓古典力学中的刚体力学。它的世界观是完全机械化的：刚体的运动完全由它的质心坐标和尺寸形状朝向等少数几个参数来描述，刚体的内部构造无法被观测也无关紧要，刚体之间通过直接接触发生相互作用，刚体的形状必须精确匹配才能构成一个无缝的整体（可以对比一下软件组件的黑箱模型）。即使是在古典力学中，稍微高级一点的观点也都会转换到拉格朗日表述或者哈密尔顿表述，它的精神实质是转向**场论的世界观**。所谓的场（Field），其实就是**建立一个无所不在的坐标系，然后在坐标系的每一点上都可以指定一个物理量**。场的自由度是无限的，但是通过坐标系它是可描述的、可定义的、可研究的，在坐标系的每一点上我们都可以精确的度量局部的变化。基于同样的精神，可逆计算的基本设定是首先建立一个足够精细和通用的领域描述坐标系，在这个坐标系中我们能够做到指哪打哪和打哪指哪（**坐标的唯一性**）。建立场的观念之后，我们就可以在下一节对FOP和DOP进行一些理论分析了。

## 二. 从面向特征(FOP)到面向差量(DOP)

面向特征编程，顾名思义，其最核心的概念就是所谓的特征（Feature）。那么，什么是特征？按照文献 <sup>[\[3\]](#f3)</sup>中的定义

> A feature is a unit of functionality of a software system that satisfies a requirement, represents a design decision, and provides a potential configuration option.

![fop](dop/fop.gif)

比如按照上面的特征模型，车（Car）必须具有引擎（Engine）这一特征，引擎可以是燃油的或者是电动的，甚至是混动的。而变速箱(Transmission)可以是自动的或者手动的，但不能既是自动的，又是手动的。按照软件产品线工程的设想，具体软件开发类似买车时在配置菜单中打勾做选择（也可以类比于到饭馆点菜），选择指定的特征之后，由生成器负责将它们转换为可执行代码，自动生成可运行的程序。

![fosd](dop/fosd.png)

FOP最基本的洞见在于特征（我们在业务层面所关注的内容）往往不能很好的和面向对象（组件）或者函数分解结构对齐，而几乎必然会成为一种横切关注点（crosscutting concern）。这其实也很好理解。特征是在问题空间中有价值的、可识别的结构，而组件/函数是在解空间中的有效抽象和描述，从问题空间到解空间的结构映射在一般性的业务环境中都是**非平凡的**，因此两种描述方式无法有效的对齐。套用人工智能领域的话语，我们可以说：**有用的特征都是分布式的（distributed）**。

在软件产品线工程中，实现特征定义和组合的一种基本技术手段是类似C语言的预处理机制（条件编译）。

![preprocessor](dop/preprocessor.png)

FOP对于软件产品线工程的贡献在于，它提供了更为规范和强大的特征定义和组合机制<sup>[\[5\]](#f5)</sup><sup>[\[6\]](#f6)</sup>。

1. 定义语言无关的特征结构树（Feature Structure Tree, FST）
2. 通过语言无关的Tree Superimposition来实现特征组合

所谓的FST就是一个通用的树形结构，每个节点具有名称(name)和类型(type)，其中子节点的名称各不相同，从而可以区分开来。Tree Superimposition就是两棵树之间的合并过程，节点按照名称逐级合并，合并节点的类型需要匹配。

> Superimposition is the process of composing software artifacts by merging their corresponding substructures.

![fst](dop/fst.png)

![compose](dop/compose.png)

![superimposition](dop/superimposition.png)

早期的FOP并没有意识到树结构以及树结构合并算法的通用性，它所采用的是对已有语言进行语法扩展的路数。

![fop](dop/fop.png)

Apel在2008-2009年左右发表的一系列工作<sup>[\[4\]](#f4)</sup><sup>[\[5\]](#f5)</sup><sup>[\[6\]](#f6)</sup>将FOP推进到一个新的抽象高度。不仅仅限于代码文件，文档、测试用例等等一切相关artifact都可以纳入特征结构树的管辖范围。FeatureHouse<sup>[\[6\]](#f6)</sup>通过为EBNF语法规则增加FOP相关标注的方式，允许为任意语法结构引入通用的合并规则（不再需要为FOP引入特定的程序语言），从而极大的扩展了FOP的应用范围。

> FEATUREHOUSE relies  on  three  ingredients:
> 
> (1) a  language-independent  model  of  software  artifacts
> 
> (2) superimposition  as  a  language-independent  composition paradigm
> 
> (3)  an artifact language specification based on attribute grammars.

![featurehouse](dop/featurehouse.png)

根据上一节的分析，FOP的这一系列做法其实非常容易理解。所谓FST树，就是一种通用的描述坐标系，所有的artifact都**必然**可以分解到这个坐标系中获得一个唯一的、确定的表示。之所以是树形结构，是因为树结构中**任意节点到根节点的路径都是唯一的**，因此可以作为坐标来使用。确定坐标之后，坐标点上的合并过程完全是Generic的，与具体的业务逻辑和业务结构完全无关。这一点，在Apel引入的Feature Algebra形式代数<sup>[\[4\]](#f4)</sup>中表达的非常清楚。

![feature-algebra](dop/feature-algebra.png)

如果与AOP做个对比，可以发现一个非常有意思的情况。AOP的pointcut能力非常强大，可以直接使用正则表达式这种复杂算子来实现过滤选择，但是丧失了坐标的唯一性，难以建立Feature Algebra。同时，AOP与程序语言深度绑定，难以扩展到其他artifact层面。所以，**强大的表达能力并不是我们所需要追求的全部**，可逆计算非常强调可逆性，强大到破坏了可逆性的行为是需要被限制，甚至被禁止的。

FOP的理论看似已经非常完善，但从可逆计算的角度看，它仍然存在很大的发展空间。2010年，Schaefer发现了FOP的一个不足之处，提出了所谓的Delta Oriented Programming（面向差量编程）。Schaefer的发现是

> It is not possible to start  from  an  existing  legacy  application  comprising  a  larger  set of features and to **remove** features.

如果**抛弃所有关于feature的业务解读，直接把它定义为功能的差量(Delta)**，立刻就可以发现FOP只包含覆盖和新增操作，没有定义**删除**操作！DOP最初是引入了一个类Java语法:DeltaJ <sup>[\[10\]](#f10)</sup>

![deltaj](dop/deltaj.png)

![delta-spl](dop/delta-spl.png)

后来DOP也学习FeatureHouse，引入了DeltaEcore<sup>[\[11\]](#f11)</sup>，可以为任意语法引入差量结构。

![delta-core](dop/delta-core.png)

最早的时候，DOP需要包含一个core product，所有的delta作用到core product之后产生最终的产品。但是，按照可逆计算理论，在存在单位元的情况下，差量和全量之间是可以互相转化的。Schaefer很快也发现了这一点，立马又灌了一篇论文<sup>[\[8\]](#f8)</sup>，指出不需要core product，仅依赖delta module就可以构建所有系统。

从可逆计算的角度去观察DOP，会发现它仍然存在很大的发展空间，最明显的是它缺少Generator的部分。不过，与可逆计算相比，DOP对于Delta的认知也仍然处于比较初级的程度。taowen前两天提到一篇论文<sup>[\[15\]](#f15)</sup>，其中描述了一个与差量有关的技术XVCL，它与DOP也有一些相似的地方。在下一节我将分析一下可逆计算中的差量概念与DOP和XVCL等技术之间的区别。

## 三. XVCL与Frame Technology

XVCL宣称自己的理论基础源于所谓的Frame technology，而Frame technology宣称自己的概念源于人工智能领域的Frame，而Frame这个概念由Minsky于1975年发明（没办法，为了发论文每个人都得给自己的概念找个体面的祖师爷）。简单的理解起来，Frame就是一个结构模板（architype），其中挖了一些洞，叫做slot，可以被定制，基本上和vue component没多大区别。

> 1. 选择一个example X
> 2. 将X内部容易变化的细节部分标记出来，把它转化为frame参数（slot），同时将example X的原始内容作为缺省值（slot的body）（。。。好吧，这就是一个vue组件）

网上有一篇2008年对Frame technology发明人Bassett的访谈<sup>[\[17\]](#f17)</sup>，这篇文章还是包含了一些有趣的观点的（基本都是我基于可逆计算理论表达过的观点，这算英雄所见略同，还是历史就是一个loop？）：

1. Frame可以有效描述 "A与B很相似，**除了**..."这种情况，也可以描述 "A与B+C很相似，**除了**..."这种情况
2. 程序员通过拷贝粘贴修改代码即耗费人力，又不可靠，通过frame指令实现对代码的增删改，又快又准
3. Frame可以互相嵌套构成Frame Tree，可以架设在任意语言所表达的结构之上（比如说在自然语言所写的文本文档中增加frame标记就可以实现文档的frame扩展）
4. 在Frame的观点下，maintenance不再是与development相互割裂的过程。整个开发的过程和维护的过程一样，都是在外部逐步增加frame差量来实现的（不需要修改原始的frame，在原frame中只需要增加标记）。
5. similar programs often differ by small amounts of code，**差异部分可以被局限在frame delta中**，一般只有总量的5% - 15%
6. 任何领域都有所谓的自然粒度（natural graininess）。具体的实现技术，如类、函数等会将自然的结构拆分的稀碎（比如成百上千很小的类和函数），导致不必要的复杂性
7. Frame基于semi-lattice数学结构，可以处理多重继承问题（基本和scala语言的trait机制类似，通过规定覆盖顺序来避免继承导致的概念冲突）
8. Frame就是一个architype，它可以被看作是一个模糊集合（fuzzy set）的中心元素。与其他抽象技术不同，**并不需要事前确保frame的抽象正确无误**。如果发现还需要增加变化点，只要在原始frame中增加slot标记就好了（完全兼容此前的代码，也没有任何运行时成本）。

以上观点听起来很神奇，但对应的XVCL实现代码却是朴实无华的。

![x-frame](dop/x-frame2.png)

XVCL类似于一种模板语言，其中可以定义变量、判断、循环等。adapt类似于函数调用，可以嵌套调用其他frame。而break标签用于标记一个扩展点，类似vue中的slot标签。因为类似模板语言，所以可以用于生成代码，也可以用于生成文档。例如生成Use Case文档。

![x-frame](dop/x-frame.png)

XVCL与vue组件毕竟是不同的，它提供了某种差量编程的能力：当通过adapt标签来调用其他的frame的时候，可以通过insert-before/insert/insert-after等对定制内容进行较为复杂的控制（adapt的时候insert标签对应部分将修改基础frame中break标签所标记的部分），使用insert empty也可以实现删除缺省内容的效果。

![xvcl](dop/xvcl.png)

相比于C语言的预处理器，XVCL是一种进步，因为它**可以应用于任意文本格式的文件**，而不仅仅限于特定程序语言（虽然任何模板语言都能干这个），同时XVCL具有更严格的变量作用域规则，并提供了**受控的差量定制机制**（C语言预处理器无法提供slot定制功能）。

不过，如果按照可逆计算理论的分析框架，XVCL所建立的坐标系统实际上是比FeatureHouse<sup>[\[6\]](#f6)</sup>要更弱的： XVCL通过把系统分解为frame，并在frame中增加break标记构造了一个可用于支持定制的坐标系统，但是frame文件之间基本上处于无组织的状态，而FeatureHouse好歹按照目录结构把所有artifact都管理起来，并在每一级目录上都定义了合并算子。frame-break这种两级抽象基本类似于class-member结构，FeatureHouse通过Feature Structure Tree可以毫无压力的模拟frame机制。而DOP相比于XVCL，也提供了更多的feature组合能力。

虽然FOP、DOP和Frame Technology都采用了差量概念，但它们所定义的差量与可逆计算理论中的差量是有着明显区别的：可逆计算指出差量不仅仅是要表达差异，更重要的是它意味着可逆的运算结构，从而可以有效的限制系统的熵增。基于可逆计算进行设计时，引入任何机制都需要进行配对设计（正向与逆向）。在下一节中我将更详细的阐述一下可逆计算的不同之处。

## 四. 可逆计算有什么不同之处？

在可逆计算的介绍文章<sup>[\[18\]](#f18)</sup>中，我首先阐述了一个启发式的观点：可逆计算可以看作是**图灵机理论和Lambda演算理论之外实现所谓图灵完备的通用计算的第三条逻辑路径**。强调这个观点的目的是为了指出，**可逆计算并不是一种简单的程序技巧或者是仅适用于某个领域的设计模式**，它是一种具有普适性的计算结构，可以用于各种抽象层面、各种应用领域。可逆计算与物理学中熵的概念息息相关，它所揭示的规律也并不仅仅限于软件应用系统。

可逆计算引入了可逆的差量这一核心概念，明确指出**全量= 单位元+差量**, 因此**全量和差量是可以互相转化的**。这一概念的一个基本推论是：**全量和差量可以是同构的，应该用同一个schema去约束**。这与DOP和XVCL的做法有着很大的区别，在DOP和XVCL中，差量都是**以修改动作的形式出现**，差量的表达方式与全量的表达方式截然不同，这样的话如何去描述差量的差量？差量与全量同构的情况下，**差量的差量仍然是一个普通的差量**。很多人将差量的作用看作是 base + patch，认为base是主要的，patch是次要的。但是，实际上base也可以被看作是patch的patch，base与patch之间是对偶关系，在理论上没有必要把它们区别对待。**差量具有独立存在的价值，它并不需要依附于base才能够被理解**。

在XVCL中，我们需要主动在代码中插入break标签来标记扩展点。但是可逆计算的普适性决定了以下事实：**DSL模型既是在坐标系中定义的实体，同时它又构成了坐标系本身**。我们没有必要单独为了扩展点在模型中增加额外的描述信息，而只需要采用类似FeatureHouse的做法，在EBNF规则/Schema定义中增加少量标注信息即可。

在FOP中，特征模型特别高层，基本退化为了开关树，难以容纳复杂的领域逻辑，而具体的特性表达手段又特别低层，一般与通用程序语言语法类似，或直接依附于通用程序语言（如Java等），这导致根据特性组合编译为具体产品的过程必然与很多细节知识绑定在一切，难以扩展到复杂的应用场景。这从FOP论文所举的实例多半为玩具项目也可窥见一斑。可逆计算明确了DSL的核心地位，为渐进式的引入领域知识指明了方向。

1. **DSL与DSL如何无缝融合**？DSL的本征结构采用Tree结构描述，但是每一个节点都可以具有不同的表观层文本语法，并且可以关联不同的可视化界面。因此不同的DSL可以无缝嵌套在一起
2. **不同的DSL如何共享部分信息，避免出现逻辑冲突**？$ DSL_2 = Generator\langle DSL_1\rangle  + Delta $, 可以从DSL1模型中反向抽取共享信息，然后通过生成器再传播到DSL2模型中，非共享的部分直接通过Delta机制进行描述即可。

FOP虽然引入了Feature Structure Tree这种通用的树形结构，但是与业内其他主流实践类似，它也**陷入到了类型理论的逻辑陷阱中难以自拔**。类型理论可以看作是提供了一个两层坐标系统：任何结构都具有一个确定的类型，根据属性名或者方法名我们可以定位到指定的子结构（相当于是一种局部相对坐标）。定制过程也分为两步：1. 根据类型找到对应的对象  2. 打开对象，修改其中的属性或者方法。但是，**以类型作为坐标系统，明显是不精确和不完善的**。

作为一个坐标系统，最基本的能力是提供如下两个操作

1. value = get(path)
2. set(path, value)

所有需要识别的对象在此坐标系中都应该具有唯一的存取坐标。

但是类型的本义是对相同的结构进行归并表达：**不同的对象具有相同的类型**。这导致使用类型在领域空间中进行唯一定位并不是理所当然的一件事情。最基本的一个问题是，如何定位数组中的某个元素（数组中的元素具有同样的类型）？我们可以使用数组下标来作为数组中的局部坐标，但是**这一坐标表示一般情况下是不稳定的**。当我们在数组中插入一个元素时，所有后续元素对应的下标都会跟着变化。参考一下虚拟DOM的diff算法的实现，我们就可以发现，有必要为数组中的每个元素规定一个特殊的id属性，用于作为该元素稳定的坐标表示。当然，肯定可以通过对类型系统进行扩展，把这一概念包含到类型定义中，但是如果一个模型对象始终只有一个实例，那么有多大必要将自己局限在类型理论的范畴内讨论问题是很值得存疑的。

FOP的特征结构树对应的是包-类-方法这样的组织层级，当我们需要进行特化处理的时候，必然会导致引入大量不必要的类型。可逆计算按照领域结构进行组织（DSL模型本身可能对应多个层级，而不是类-方法这样两层），在领域结构上明确规定坐标的唯一性，因此结构具有唯一的坐标表示，而生成器相当于是一个映射函数，它将一个对象的坐标表示映射到一个新的坐标表示（类似物理系统在相空间中的演化）。生成器本身也是在同样的坐标系下进行表达的，可以通过同样的动力学过程驱动它的演化。与FOP相比，可逆计算明显具有更好的适应性。

FOP和DOP是学术界对于如何构建复杂系统所进行的有益的理论探索，它们也确实带给我一些新的启发。特别的，我在可逆计算中吸收了特性选择器的观念，允许在任意模型节点上通过`feature:on='特性选择表达式'`这一标注来决定是否启用特性相关部分，这一处理过程在模型结构解析之前发生，因此是完全通用的机制，与特定DSL模型无关。

## 五. 可逆计算的开源实现

能坚持看到这里的，估计也没几个人了，握个手吧。如果你对可逆计算确实感兴趣，可能觉得纯理论说得云山雾绕，没多大意思，不如show me the code。这个可以有。我已经开源了一个可逆计算的参考实现**Nop Platform 2.0**。后端使用java（不依赖spring框架），前端使用vue3.0，第一阶段的开源部分主要包含从模型定义到GraphQL服务这条链路所涉及到的所有技术。计划中主要展示如下内容：

1. 模型驱动的代码生成：只需输入一个Excel格式的数据模型，即可得到前后端全套可运行代码，可以完成对多主子表数据结构的增删改查操作。

2. 渐进式的模型增强：在已生成代码的基础上，可以进行增量式的微调。手工调整代码与自动生成代码相互隔离，互不影响。

3. 基于同态映射的可视化设计：代码和可视化模型可以看作是同一逻辑结构的两种同态表示，根据元模型定义自动生成对应的可视化设计器。

4. 多版本差量定制：无需修改主程序代码，即可为不同部署版本定制不同的实现逻辑。定制代码以差量形式存放，粒度可以精确到单个函数和单个按钮展现。

5. 设计器与应用的协同演化：设计器本身也是模型驱动的产物，它并不是固化的工具。设计器可以针对特定应用进行定制优化，同时随着应用功能演进不断增强。设计器定制逻辑与应用定制完全相同。

6. 编译期元编程：通过元模型可以随时定义新的模型，并自动得到对应的解析器、验证器、可视化设计器等。大量的模型构造和转化工作在编译期完成，大幅简化运行期结构，同时可以将同一模型适配到多种运行时引擎上。

7. GraphQL服务、消息服务和批处理服务的统一：无需编程，同一份业务处理逻辑可以发布为在线GraphQL服务，或者消息队列处理服务，或者批处理文件处理任务等，并自动实现批量加载和批量提交优化。

8. 模型驱动的自动化测试：无需编写测试代码，在系统调试过程中可以自动录制服务输入输出数据以及数据库更改记录，并实现回放测试。自动化测试引擎会自动识别随机生成的主键和主子表关联等信息，并执行对应校验代码。

9. 基于变化检测的即时更新：自动识别模型文件变更，并实现即时重编译。通过FileWatch监视文件变化主动触发重编译。

10. 分库分表、多租户、分布式：对于复杂应用场景的内置支持。

后续还将逐步开源IDE插件和WorkflowEngine、 RuleEngine、ReportEngine、JobEngine等运行时引擎，并集成GraalVM虚拟机，基于Truffle框架实现XLang运行时，支持编译为二进制程序等。

项目地址：

- gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- 开发示例：[docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [可逆计算原理和Nop平台介绍及答疑\_哔哩哔哩\_bilibili](https://www.bilibili.com/video/BV1u84y1w7kX/)

## 参考

<span id="f1">\[1\]: [Software Product Lines Essentials](https://resources.sei.cmu.edu/asset_files/Presentation/2008_017_001_24246.pdf)</span>

<span id="f2">\[2\]: [An Overview of Feature-Oriented Software Development](http://www.jot.fm/issues/issue_2009_07/column5.pdf)</span>

<span id="f3">\[3\]: [Feature-Oriented Software Development:A Short Tutorial on Feature-Oriented Programming,Virtual Separation of Concerns, and Variability-AwareAnalysis](https://www.cs.cmu.edu/~ckaestne/pdf/gttse11.pdf)</span>

<span id="f4">\[4\]: [An Algebra for Features and Feature Composition](https://www.infosun.fim.uni-passau.de/cl/publications/docs/AMAST2008.pdf)</span>

<span id="f5">\[5\]: [Superimposition: A Language-Independent Approach to Software Composition](https://www.se.cs.uni-saarland.de/publications/docs/MIP-0711.pdf)</span>

<span id=f6>\[6\]: [FEATUREHOUSE: Language-Independent, Automated Software Composition](https://www.infosun.fim.uni-passau.de/cl/publications/docs/ICSE2009fh.pdf)</pan>

<span id=f7>\[7\]: [Delta Oriented Programming](https://homepages.dcc.ufmg.br/~figueiredo/disciplinas/lectures/dop_v01.pdf)</span>

<span id=f8>\[8\]: [Pure Delta-oriented Programming](https://www.se.cs.uni-saarland.de/apel/FOSD2010/49-schaefer.pdf)</span>

<span id=f9>\[9\]: [Refactoring Delta-Oriented Software Product Lines](https://www.isf.cs.tu-bs.de/cms/team/schulze/pubs/2013/AOSD2013-DOR.pdf)</span>

<span id=f10>\[10\]:[https://deltajava.org/](https://deltajava.org/)</span>

<span id=f11>\[11\]: [DeltaEcore—A Model-Based Delta Language Generation Framework](https://subs.emis.de/LNI/Proceedings/Proceedings225/81.pdf)</span>

<span id=f12>\[12\]: [Witrix架构分析](http://www.blogjava.net/canonical/archive/2007/09/23/147641.html)</span>

<span id=f13>\[13\]: [从编写代码到制造代码](http://www.blogjava.net/canonical/archive/2009/02/15/254784.html)</span>

<span id=f14>\[14\]: [模型驱动的数学原理](http://www.blogjava.net/canonical/archive/2011/02/07/343919.html)</span>

<span id=f15>\[15\]: [XVCL: a mechanism for handling variants insoftware product lines](https://core.ac.uk/download/pdf/82147954.pdf)</span>

<span id=f16>\[16\]: [ANALYSIS AND DEBUGGING OF META-PROGRAMS IN XVCL](https://core.ac.uk/download/pdf/48627012.pdf)</span>

<span id=f17>\[17\]: [Frame technology](http://www.stephenibaraki.com/cips/v46/bassett.html)</span>

<span id=f18>\[18\]: [可逆计算：下一代软件构造理论](https://zhuanlan.zhihu.com/p/64004026)
</span>

# Nop如何克服DSL只能应用于特定领域的限制？

   Nop平台可以看作是一个语言工作台（Language Workbench），它为DSL(领域特定语言，Domain Specific Language)的设计和研发提供了完整的理论支撑和底层工具集。使用Nop平台来开发，主要是使用DSL来表达业务逻辑，而不是使用通用的程序语言来表达。有些人可能会有疑问：既然DSL是所谓的领域特定语言，那岂不是意味着它只能应用于某个特定领域，这样在描述业务的时候是不是会存在本质性的限制？以前ROR(Ruby On Rails)框架流行的时候，热炒过一段时间DSL的概念，但现在不也悄无声息了，Nop又有何特异之处？对这个问题的回答很简单：Nop平台是基于可逆计算理论从零开始构建的下一代低代码平台，而可逆计算理论是一个系统化的关于DSL设计和构建的理论，它在理论层面解决了传统DSL设计和应用所存在的问题。

## 一.  横向DSL分解: DSL特性向量空间

图灵机能够实现图灵完备的根本原因在于图灵机可以被看作是一种虚拟机，它可以模拟所有其他的自动计算机器，而如果我们不断提升虚拟机的抽象层次，就会得到可以直接"运行"所谓领域特定语言(DSL)的虚拟机，但是因为DSL关注的重点是特定领域概念，它必然无法以最便利的方式表达所有通用计算逻辑（否则它就成为了通用语言），必然会导致某种信息溢出，成为所谓的Delta项。

**在第一代、第二代、第三代程序语言的发展过程中，不断的提升抽象层次，但它们仍然都是通用程序语言，但是发展到第四代程序语言，我们很可能得到的不是另一种通用程序语言，而是大量领域特定语言所构成的DSL森林**，通过它们我们可以形成对原有程序结构的一种新的表示和认知。

根据可逆计算的核心构造公式 `App = Delta x-extends Generator<DSL>`，我们可以连续应用Delta分解，得到如下公式

```
App = G1<DSL1> + G2<DSL2> + ... + Delta
App ~ [DSL1, DSL2, ..., Delta]
```

我们可以利用一系列的DSL语言，通过Generator和Delta项把它们粘结在一起。**如果我们把Generator看作是一种可以被忽略的背景知识（在表达业务的时候不需要明确表达），则可以将每个DSL看作是一个特性维度（Feature），而应用程序可以投影到多维的特性空间**。每一个DSL确实存在表达上的限制，但是多个特性维度组合在一起，再加上额外的Delta信息，就可以建立一个完整的描述。

## 二. 纵向DSL分解：多阶段、多层次的软件生产线

前一节介绍的是可逆计算理论中的横向DSL分解，与此类似，我们在纵向也可以引入多层级的DSL分解，它可以克服传统模型驱动架构（MDA）的固有缺陷。

![](../tutorial/delta-pipeline.png)

在日常开发中，我们经常可以发现一些逻辑结构之间存在相似性和某种**不精确的衍生关系**，例如后端数据模型与前端页面之间密切的关联，对于最简单的情况，我们可以根据数据模型直接推导得到它对应的增删改查页面，或者反向根据表单字段信息推导得到数据库存储结构。但是，这种不精确的衍生关系很难被现有的技术手段所捕获和利用，如果强行约定一些关联规则，则只能应用于非常受限的特定场景，而且还会导致与其他技术手段的不兼容性，难以复用已有的工具技术，也难以适应需求从简单到复杂的动态演化。 这正是**传统模型驱动架构所面临的两难抉择：模型要发挥最大作用就必须内置大量知识进行自动推理，但是内置的知识越多就越容易绑定在某个应用场景，难以处理预料之外的新需求**。

Nop平台基于可逆计算理论为实现这种面向动态相似性的复用提供了标准的技术路线：

1. 借助于嵌入式元编程和代码生成，**任意结构A和C之间都可以建立一条推理管线**

2. **将推理管线分解为多个步骤 :  A =\> B =\> C**

3. **进一步将推理管线差量化**：A =\> `_B` =\> B =\> `_C` =\> C

4. **每一个环节都允许暂存和透传本步骤不需要使用的扩展信息**

具体来说，Nop内置的模型驱动生产线可以分解为四个主要模型：

1. XORM：面向存储层的领域模型

2. XMeta：针对GraphQL接口层的领域模型，可以直接生成GraphQL的类型定义

3. XView：在业务层面理解的前端逻辑，采用表单、表格、按钮等少量UI元素，与前端框架无关

4. XPage：具体使用某种前端框架的页面模型

在模型推导的时候我们只是推导得到一个备选的结果（一般存放在以下划线为前缀的模型文件中），然后我们可以选择继承这个备选的模型，增加手工修正和依赖额外信息的Delta推理部分（存放在不以下划线为前缀的模型）。整个推理关系的各个步骤都是可选环节：**我们可以从任意步骤直接开始，也可以完全舍弃此前步骤所推理得到的所有信息**。例如我们可以手动增加`xview`模型，并不需要它一定具有特定的`xmeta`支持，也可以直接新建`page.yaml`文件，按照AMIS组件规范编写JSON代码，AMIS框架的能力完全不会受到推理管线的限制。借助于这种类似深度学习的深度分解模式，我们可以完全释放模型驱动的威力，同时在必要时可以通过Delta差量引入额外信息，最终成品的能力不会受到模型表达能力的限制。这也使得**我们建模时不需要再追求对各种细节需求的覆盖，只需要集中精力关注最核心、最普适的通用需求部分即可**。

> `XORM = Generator<ExcelModel> + Delta`
>
> `XMeta = Generator<XORM> + Delta`
>
> `XView = Generator<XMeta> + Delta`
>
> `XPage = Generator<XView> + Delta`

## 三. 非编程指的是非命令式编程

Nop是**Nop is nOt Programming**的递归缩写，Nop非编程指的是它不是传统意义上的命令式编程，而是尽可能的扩大描述式编程的应用范围。**所谓的DSL**可以看作是一种对于业务逻辑的描述式表达方法，它**关注的是如何用业务领域内部的概念来描述业务本身**，而不是用通用程序语言的术语来表达如何一步步的实现业务功能。反过来思考，如果我们可以找到一种针对当前业务的描述式表达方式，然后用最精简的文本结构把它保存下来，那自然就成为一种DSL。

实际上，在传统编程领域，当我们希望提高编程的抽象层次，提升软件的灵活性和适应性的时候，也会不断强调领域概念的重要性，比如说DDD(Domain Driven Design)中的统一语言（Ubiquitous Language）。但是，在传统编程领域，领域概念最终仍然是由通用语言内部的通用程序结构来承载的，因此它在表达的自由性和丰富性方面受到底层通用语言语法结构的限制。另一方面，很多设计优秀的程序框架，本身底层的心智模型就对应于一个隐式存在的DSL，但是我们并没有把它明确表达出来。比如说，SpringBoot的条件化bean组装机制完全可以在spring1.0的语法基础上扩充少量条件标签来实现，但是SpringBoot的实现中却没有定义这样一种DSL，结果最终SpringBoot丧失了自己引以为傲的声明式组装能力，也丧失了对于全局组装结果的直观洞察能力。详细分析，参见[如果重写SpringBoot，我们会做哪些不同的选择？](https://mp.weixin.qq.com/s/_ZVXESRqjSbObmrkDZoGMQ)

Nop平台没有使用Spring和Quarkus这种第三方框架，而是选择从零开始编写IoC/ORM/Workflow/BatchJob等一系列底层引擎，这里最重要的原因就是要根据可逆计算理论对这些引擎的设计进行改造。**每一个具有独立存在价值的引擎都必然对应于一个内在的模型，而这个模型也必然对应着一种DSL语言**。Nop平台的关键点是**为每一种引擎都明确定义出一个DSL，然后借助于Nop平台的基础设施，自动的实现DSL的解析、验证、缓存、分解合并、元编程等**。在这种情况下，所有的引擎都只需要处理自己特有的运行时逻辑即可，而且因为大量的可扩展设计都可以在编译期借助于Nop平台来完成，所以引擎的运行时结构可以得到极大的简化。在Nop平台中，每个引擎的代码量一般会比对应的开源实现要小一个数量级，同时还提供更丰富的功能、更好的可扩展性、更优异的性能。参见[Nop平台与SpringCloud的功能对比 ](https://mp.weixin.qq.com/s/Dra8yf2O5VMJyEPox4dGBw)。


> Nop平台的应用范围不仅仅是强调可视化编辑的低代码领域，它提供了大量底层引擎，可以对标SpringCloud生态的相应部分，有能力成为AI时代的一种类似SpringCloud的基础技术底座。

## 四. 统一的元模型，统一的DSL结构构造规律

对于DSL的反对意见最常见的是以下三点：

1. 构造和维护DSL的成本很高

2. 不同的DSL语法形式差别很大，导致学习成本也很高

3. DSL和通用语言之间的交互困难

因此，很多人推荐使用嵌入在通用语言中的所谓内部DSL(Internal DSL)。内部DSL利用宿主通用编程语言的语法和结构来构建领域特定的语言，不需要独立的语法分析器或编译器，可以利用现有编程语言的工具和生态系统，比如代码编辑、调试、打包和部署等，而且学习成本相对较低，因为用户不需要学习一套全新的语言。

但是，常见的内部DSL所存在的问题是，它们过于强调DSL语法与自然语言之间的表观相似性（实际上也不是与自然语言相似，只是与英语相似），实际上只是引入了不必要的形式复杂性。此外，一般内部DSL只是利用底层语言内置的类型系统来进行形式约束，往往并不能保证DSL语法的稳定性，完全可能存在多种等价的表达方式来表达同样的领域逻辑，所谓的DSL语法仅仅是一种不成文的约定而已。内部DSL的解析一般要依赖于底层语法的解析器，难以脱离于底层语言之外进行解析和转换，这也导致内部DSL的语法和语义与底层语言纠缠在一起，很少有人会关注内部DSL自身的概念完整性、可扩展性和结构可逆性。

Nop平台的做法是引入统一的XDef元模型来规范化所有DSL的语法和语义结构，并提供了统一的开发和调试工具来辅助DSL的开发。只要掌握了元模型，就可以立刻掌握所有DSL的语法和分解合并、差量定制等标准方案，不需要针对每个DSL单独学习。使用Nop平台来开发一个新的引擎时，我们可以通过`xdef:ref`来引用已有的DSL，实现多个DSL的自然融合，通过XPL模板语言实现描述式向命令式的自动转换。具体参见[低代码平台中的元编程(Meta Programming)](https://mp.weixin.qq.com/s/LkTIVGSrK9zomPW4bNiqqA)。


# 从张量积看低代码平台的设计

软件设计中的一个基本问题是可扩展性问题。处理可扩展性问题的一个基本策略是将新的变化要素看作是一个新的维度，然后考察这个维度与已有维度之间的相互作用关系。

例如，现在针对Order对象编写好了一个OrderProcess处理逻辑，如果作为SAAS软件发布，则需要增加租户维度。最简单的情况下，租户仅仅是引入数据库层面的过滤字段，
即租户维度相对独立，它的引入不影响具体的业务处理逻辑（租户相关的逻辑独立于特定的业务处理过程，可以在存储层被统一定义并解决）。

但是更复杂一些的扩展性要求是每个租户可以有自己定制的业务逻辑，则此时租户维度无法保持独立性，必然需要与其他业务技术维度发生相互作用。本文将介绍一个启发式的观点，
它将类似租户扩展这一类具有普遍性的可扩展性问题类比于张量空间通过张量积所实现的扩张过程，并结合可逆计算理论，为这类可扩展性问题提供一个统一的技术解决方案。

## 一.  线性系统与向量空间

数学中最简单的一类系统是线性系统，它满足线性叠加规律

$$
f(\lambda_1 v_1 + \lambda_2 v_2) = \lambda_1 f(v_1) + \lambda_2 f(v_2)
$$

我们知道，任何一个向量都可以分解为基向量的线性组合

$$
\mathbf v = \sum_i \lambda_i \mathbf e_i
$$

因此，作用于向量空间上的线性函数，其结构本质上是非常简单的，它完全由函数在基向量上的值来确定。

$$
f(\mathbf v) = \sum_i \lambda_i f(\mathbf e_i)
$$

只要知道了函数f在所有基向量上的值$f(\mathbf e_i)$，我们就可以直接计算出函数f在$\mathbf e_i$所张成的向量空间中的任意向量处的值。

按照数学的精神，如果一个数学性质很好，我们就专门以该性质为前提来定义所需要研究的数学对象（**数学性质定义了数学对象，而不是数学对象具有某种数学性质**）。那么体现在软件框架设计领域，**如果我们主动要求一个框架设计满足线性叠加规律**，那它的设计应该是什么样子？

首先我们需要从不那么数学的角度重新审视一下线性系统的含义。

1. $f(\mathbf v)$ 可以看作是在一个具有复杂结构的参数对象上执行某种操作。

2. $\mathbf v = \sum_i 易变的参数\cdot 标识性的参数$。有些参数是具有特殊标识作用的相对固化的参数，而其他参数是每次请求都发生变化的易变的参数。

3. **f先作用于标识性的参数（这一作用结果可以事先确定）得到一个计算结果，然后再把这个计算结果和其他参数进行结合运算**

举个具体的例子，比如前台提交请求，需要触发后台的一组对象上的操作。

$$
request = \{ obj1：data1, obj2: data2, ... \}
$$

整理成向量形式

$$
request = data1* \mathbf {obj1} + data2* \mathbf {obj2} + ...
$$

当我们研究**所有可能的请求**时，我们会发现所有请求构成一个向量空间，**每个objName对应向量空间中的一个基向量**。

后端框架的处理逻辑对应于

$$
\begin{aligned}
process(request) &= data1* route(\mathbf {obj1}) + data2* route(\mathbf {obj2}) + ...\\
&= route(\mathbf {obj1}).handle(data1) + route(\mathbf {obj2}).handle(data2) + ...
\end{aligned}
$$

框架越过易变的参数data，先作用于对象名参数上，根据对象名路由到某个处理函数，然后再调用该处理函数，传入data参数。

> 这里我们需要注意到  $\lambda_i f(\mathbf e_i)$本质上是 $\langle \lambda_i, f(\mathbf e_i)\rangle $，即参数与$f(\mathbf e_i)$的结合并不一定是简单的数值乘法，而可以被扩展为某种内积运算的结果，在软件代码层面，它就体现为函数调用。

## 二. 张量积和张量空间

在数学中，一个基本问题是如何从一些较小的、较简单的数学构造出发，自动生成更大的、更复杂的数学结构，而张量积（Tensor Product）的概念正是这种自动化的构造方式的一种自然结果（这里所谓的自然性在范畴论中获得了精确的数学定义）。

首先，我们来看一下线性函数的推广：多重线性函数。

$$
f(\lambda_1 u_1+\lambda_2 u_2,v) = \lambda_1 f(u_1,v) + \lambda_2 f(u_2,v) \\
f(u,\beta_1 v_1+ \beta_2 v_2) = \beta_1 f(u,v_1) + \beta_2 f(u,v_2)
$$

作用于向量空间上的线性函数可以看作一个单参数函数，它接收一个向量，产生一个值。而类似于单参数函数向多参数函数的推广，多重线性函数具有多个参数，每个参数都对应一个向量空间（可以看作是一个独立的变化维度），当固定考察某个参数时（例如固定参数u，考察参数v或者固定参数v，考察参数u），它都满足线性叠加规律。类似于线性函数，多重线性函数的值同样由它在基向量上的值所决定

$$
f(\sum_i \lambda_i \mathbf u_i,\sum_j \beta_j \mathbf v_j)= 
\sum_{ij} \lambda_i \beta_j f(\mathbf u_i,\mathbf v_j)
$$

$f(\mathbf u_i,\mathbf v_j)$实际上等价于传入一个tuple，即

$$
f(\mathbf u_i, \mathbf v_j)\cong f(tuple(\mathbf u_i,\mathbf v_j)) \cong f(\mathbf u_i\otimes  \mathbf v_j )
$$

即我们可以忘记f是一个多参数的函数，而把它看作是一个接收了复杂参数形式 $\mathbf u_i \otimes \mathbf v_j$的单参数的函数。回到最初的多重线性函数  $f(\mathbf u,\mathbf v)$，我们现在可以在新的视角下把它看作是 一个**新的向量空间上的线性函数**

$$
f(\mathbf u\otimes \mathbf v)=\sum _{ij} \lambda_i \beta_j f(\mathbf u_i \otimes \mathbf v_j)
$$

$$
\mathbf u \otimes \mathbf v = (\sum_i \mathbf \lambda_i \mathbf u_i) 
\otimes (\sum_j \beta _j \mathbf v_j)  
= \sum _{ij} \lambda_i \beta_j \mathbf u_i \otimes \mathbf v_j
$$

> $f(\mathbf u,\mathbf v)$和$f(\mathbf u\otimes \mathbf v)$中的f其实并不是同一个函数，只是具有某种等价性，这里把它们的符号都记为f而已。

$\mathbf u \otimes \mathbf v$被称作是向量$\mathbf u$和向量$\mathbf v$的张量积，它可以被看作是一个新的向量空间中的向量，这个空间就是所谓的张量空间，它的基是 $\mathbf u_i \otimes \mathbf v_j$。

如果$\mathbf u \in U$ 是m维向量空间，而$\mathbf v \in V$是n维向量空间，则张量空间$U\otimes V$包含了所有形如\\sum _i T_{ij} \\mathbf u\_i \\otimes \\mathbf v\_j$的向量，它对应于一个$m\\times n$维的向量空间（它也被称为是$U$和$V的张量积空间）。

> $U\otimes V$是由所有形如$\mathbf u\otimes \mathbf v$这样的张量积所张成的空间，这里的张成指的是线性张成，即这些向量的所有线性组合所构成的集合。这个空间中的元素比单纯的$\mathbf u \otimes \mathbf v$这种形式的向量要多，即不是所有张量空间中的向量都能写成$\mathbf u \otimes \mathbf v$的形式。例如
> 
> $$
> \\begin{aligned}
> \\mathbf u\_1 \\otimes \\mathbf v\_1 + 4 \\mathbf u\_1 \\otimes \\mathbf v\_2

 + 3 \\mathbf u\_2 \\otimes \\mathbf v\_1
 + 6 \\mathbf u\_2 \\otimes \\mathbf v\_2
   \&= (2\\mathbf u\_1 + 3 \\mathbf u\_2)\\otimes (\\mathbf v\_1
 + 2 \\mathbf v\_2) \\
   \&=
   \\mathbf u \\otimes \\mathbf v
   \\end{aligned}

> $$
> 
> 但是 $2 \mathbf u_1 \otimes \mathbf v_1 + 3 \mathbf u_2 \otimes \mathbf v_2$无法被分解为$\mathbf u \otimes \mathbf v$这种形式，只能保持线性组合的形式。
> 
> 在物理上，这对应于所谓的量子纠缠态。

张量积是从简单结构出发构造复杂结构的一种免费的策略（Free），这里的免费（在范畴论中具有严格的数学意义）指的是这个构造过程没有添加任何新的运算规则，就是从两个集合中各取一个组成一对放在那里而已。

> 本质上$\mathbf u \otimes \mathbf v $中$\mathbf u$和$\mathbf v$并没有发生任何直接的相互作用，$\mathbf v$对$\mathbf u$的影响仅在外部函数$f$作用到$\mathbf u\otimes \mathbf v$上才会展现。即当 $f(\mathbf u \otimes \mathbf v) \ne f(\mathbf u)$的时候，我们才会发现$\mathbf v$的存在会影响到f作用到$\mathbf u$上的结果。

借助于张量积的概念，可以认为多重线性函数等价于张量空间上的普通线性函数，当然，这种说法是很不严谨的。稍微严格一点的说法是：

对于任意的（每一个）多重线性函数 $\phi: U\times V\times W ...\rightarrow X$, 都对应**存在一个唯一的**张量空间$U\otimes V\otimes W...$上的线性函数 $\psi$, 使得 $\phi(\mathbf u, \mathbf v,\mathbf w,...) = \psi(\mathbf u \otimes \mathbf v\otimes \mathbf w...)$

或者说任何作用于向量空间的积$U\times V\times W...$上的多重线性函数，都可以被分解为一个两步的映射过程，即先映射到张量积，然后再应用张量空间上的线性函数。

在上一节中，我们介绍了线性系统和向量空间的概念，指出软件框架可以模拟线性系统的作用过程，结合本节介绍的张量积的概念，我们很容易得到一个通用的可扩展性设计方案：从接收向量参数扩展到接收张量参数，**不断增加的可变性需求可以通过张量积来吸收**。例如，

$$
process(request) = data * route(\mathbf {objName} \otimes \mathbf {tenantId})
$$

增加租户概念可能导致对系统中所有业务对象的处理逻辑都发生变化，但是在框架层面我们只需要对route函数进行增强，允许它接收objName和tenantId所组成的张量积，然后动态加载对应的处理函数即可。

如果再仔细思考一下这里的处理逻辑，我们会发现如果把软件框架实现为一个线性系统，那么它的核心其实是一个以张量积为参数的**Loader函数**。

在软件系统中，Loader函数的概念无处不在，但它的作用其实并没有得到充分的认知。回顾一下NodeJs的情况，所有被调用的库函数在形式上都是通过require(path)函数装载得到的，即我们调用函数 f(a)的时候，本质上执行的是require("f").call(null, a)。如果我们对require函数进行增强，允许它根据更多的标识性参数进行动态加载，显然我们可以实现函数级别的可扩展设计。Webpack和Vite中所使用的HMR模块热更新机制，可以被理解为一种Reactive的Loader，它监控依赖文件的变化，然后重新打包、加载并替换当前正在使用的函数指针。

可逆计算理论为Loader函数提供了新的理论层面的诠释，并**带来了一个统一的、通用的技术实现方案**。在后面的内容中，我将介绍在Nop Platform2.0（可逆计算的开源实现）中所使用的技术方案的概况和其基本原理。

## 三. Everything is Loader

> 程序员问函数：汝从哪里来，欲往哪里去？
> 
> 函数答曰：生于Loader，归于data

函数式编程的箴言是一切都是函数，everything is function。但是考虑到可扩展性，这个function就不可能是变动不居的，在不同的场景下，我们最终实际应用的必然是不同的函数。如果程序的基本结构是 f(data)，我们可以用一种系统化的方式将其改造为

loader("f")(data)。很多框架、插件的设计都可以从这个角度去审视。

* Ioc容器：
  
  buildBeanContainer(beansFile).getBean(beanName, beanScope).methodA(data)
  
  $$
  Loader(beansFile\otimes beanName\otimes beanScope \otimes methodName)
$$

* 插件系统
  
  serviceLoader(extensionPoint).methodA(data)
  
  $$
  Loader(extensionPoint \otimes methodName)
$$

* 工作流：
  
  getWorkflow(wfName).getStep(stepName).getAction(actionName).invoke(data)
  
  $$
  Loader(wfName\otimes stepName \otimes actionName)
$$

当我们在系统的各个层面都识别出相似的Loader结构之后，一个有趣的问题是：这些Loader内在的一致性到底有多高？它们之间能不能复用代码？工作流引擎、IoC引擎、报表引擎、ORM引擎...，林林总总的引擎都需要加载自身特定的模型，它们目前大多是各自为战，能否抽象出一个**系统级的、统一的Loader**来负责模型加载？如果可以，那么具体有哪些公共逻辑可以在这个统一的Loader中实现？

低代码平台的设计目标是实现代码逻辑的模型化，而模型以序列化的形式保存时就形成模型文件。可视化设计的输入输出是模型文件，所以其实可视化只是模型化的一个附带收益。一个统一的低代码平台最基本的一个工作应该是 统一管理所有模型，实现所有模型的资源化。Loader机制必然是这样的低代码平台中的一个核心组件。

我们来看一个日常开发中常见的函数

```java
JsonUtils.readJsonObject(String classPath, Class beanClass)
```

这是一个通用的Java配置对象加载函数，它读取classpath下的一个json文件，并通过JSON反序列化机制把它转换为指定类型的java对象，然后在编程中我们就可以直接使用这个对象了。而如果配置文件格式错误，比如说字段名写错了，或者数据格式错了，则在类型转换阶段可以被检测出来。如果配置了@Max,@NotEmpty这样的一些验证器注解，我们甚至可以在反序列化的时候进行一些业务相关的校验。显而易见，各类模型文件的加载和解析其实都可以看作是这一函数的变种。以工作流模型加载为例，

```java
workflowModel = workflowLoader.getWorkflow(wfName);
```

相比于较为原始的json解析，工作流模型的加载器一般具有以下增强:

1. 可能从数据库中加载，而不限于从class path下的某个文件加载

2. 模型文件格式可能采用xml格式，而不限于是json格式

3. 模型文件中可以配置可执行的脚本代码，而不限于是配置string/boolean/number等少数原始类型的数据项。

4. 模型文件的格式校验更加严格，比如检查属性值在枚举项范围之内，属性值满足特定的格式要求等。

Nop Platform 2.0是可逆计算理论的一个开源实现，它可以看作是支持领域特定语言(DSL)开发的一个低代码平台。在Nop平台中，定义了统一的模型加载器

```java
interface IResourceComponentManager{
    IComponentModel loadComponent(String componentPath);
}
```

1. 通过模型文件的后缀名可以识别模型类型，因此不需要传入componentClass这种类型信息

2. 模型文件中通过x:schema="xxx.xdef"来引入模型所需要满足的schema定义文件，从而实现比java类型约束更严密的格式和语义校验。

3. 通过增加expr等字段类型，允许在模型文件中直接定义可执行代码块，并自动解析为可执行函数对象

4. 通过虚拟文件系统，支持模型文件的多种存储方式。例如可以规定一种路径格式，指向存储在数据库中的模型文件。

5. 加载器自动收集模型解析过程中的依赖关系，根据依赖关系自动更新模型解析缓存。

6. 如果配备一个FileWatcher，可以实现当模型依赖发生变化时，主动推送更新后的模型。

7. 通过DeltaMerger和XDslExtender实现模型的差量分解和组装。在第五节中会更详细的介绍这一点（它也是Nop平台与其他平台技术显著的差异之处）。

在Nop平台中，所有的模型文件都是通过统一的模型加载器加载的，同时，**所有的模型对象也都是通过元模型（Meta Model）定义自动生成的**。在这种情况下，回看上面的工作流模型的处理过程

```java
getWorkflow(wfName).getStep(stepName).getAction(actionName).invoke(data)
```

getWorkflow通过统一的组件模型加载器负责实现，不需要特殊编写，同时getStep/getAction等方法也通过元模型定义自动生成，同样不需要特殊编写。因此，整个Loader的实现可以说是完全自动化的

$$
Loader(wfName\otimes stepName \otimes actionName)
$$

换一个角度去理解，Loader的参数可以看作是一个多维坐标（**一切可用于唯一定位的信息都是坐标**）：每个wfName对应一个虚拟文件路径path，而path是在虚拟文件系统中定位所需的坐标参数，同时stepName/actionName等是在模型文件内部进行唯一定位所需的坐标参数。Loader接收一个坐标，返回一个值，所以它也可以被看作是定义了一个坐标系。

可逆计算理论在某种意义上正是要建立并维护这样一个坐标系统，并研究在这个坐标系统中模型对象的演化和发展。

## 四. Loader as Multiple Dispatch

函数代表了某种静态化的计算（代码本身是确定性的），而Loader提供了一种计算机制，它的计算结果是返回的函数，所以Loader是一种高阶函数。如果Loader不是简单的根据参数定位到某个已经存在的代码块，而是可以根据传入的参数动态的生成对应的函数内容，则Loader可以作为元编程机制的一种切入点。

在程序语言理论中，有一种语言内置的元编程机制称为多重派发（Multiple Dispatch），它在Julia语言中得到了广泛的应用。多重派发与这里所定义的Loader机制有诸多相似之处，实际上Loader可以看作是对多重派发的一种超越类型系统的扩展。

考察一个函数调用f(a,b)，如果是采用面向对象语言来实现，我们将选择把第一个参数a实现为类型A的对象，而函数f是类型A上定义的一个成员函数，b为传给函数f的一个参数。面向对象的调用形式a.f(b)是所谓单重派发的，即根据函数的第一个参数a（this指针）的类型，动态的查询类型A的虚拟函数表，确定所需要调用的具体函数。也就是说，

```text
在面向对象的观点下　f::A->(B->C)
```

> a.f(b)在实现层面对应于一个函数 f(a,b)，a为隐式传递的this指针

而所谓的多重派发，指的是调用函数时，根据**所有参数**的运行时的类型，选择一个＂最适合＂的实现函数来进行调用，即

```text
在多重派发的观点下　f:: A x B -> C， AxB为A和B构成的元组
```

> Julia语言可以在编译期根据调用函数时给定的参数的类型，动态的生成一个特化的代码版本，从而优化程序性能。例如 f(int,int)和f(int, double)在Julia语言中可能会生成两个不同的二进制代码版本。

如果采用向量空间的观点，我们可以把不同的类型看作是不同的基向量，例如 3实际上对应于 3 int , 而"a"实际上对应于 "a" string（类比于 $\lambda_i \mathbf e_i$），不同类型的值原则上是相互分离的，类型不匹配的时候不允许发生相互关系（不考虑类型自动转换的情况），恰如不同的基向量之间相互独立。在这个意义上，多重派发 f(3, "a") 可以被理解为  $[3,"a"]\cdot Loader(int \otimes string)$

类型信息是在编译期附加到数据之上的一种描述性信息，本质上它并没有什么特异之处。在这个意义上，Loader可以看作是一种更通用的、作用于任意基向量组成的张量积上的一种多重派发。

## 五. Loader as Generator

一个通用的模型加载器可以看作是具有如下类型定义：

```
    Loader :: Path -> Model
```

对于一种通用设计，我们需要意识到一件事情，所谓的代码编写并不仅仅是为了应对眼前的需求，而是需要同时考虑到未来的需求变化，需要考虑到系统在时空中的演化。 换句话说，编程所面向的不是当前的、唯一的世界，而是**所有可能的世界**。在形式上，我们可以引入一个Possible算子来描述这件事情。

```
    Loader :: Possible Path -> Possible Model
    Possible Path = stdPath + deltaPath
```

stdPath指模型文件所对应的标准路径，而deltaPath指对已有的模型文件进行定制时所使用的差量定制路径。举个例子，在base产品中我们内置了一个业务处理流程main.wf.xml，在针对客户A进行定制时，我们需要使用一个不同的处理流程，但是我们并不想修改base产品中的代码。此时，我们可以增加一个delta差量模型文件`/_delta/a/main.wf.xml`，它表示针对客户a定制的main.wf.xml，Loader会自动识别这个文件的存在，并自动使用这个文件，而所有已经存在的业务代码都不需要被修改。

如果我们只是想对原有的模型进行微调，而不是要完全取代原有模型，则可以使用x:extends继承机制来继承原有模型。

```java
Loader<Possible Path> = Loader<stdPath + deltaPath> 
                      = Loader<deltaPath> x-extends Loader<stdPath>
                      = DeltaModel x-extends Model
                      = Possible Model
```

在Nop平台中，模型加载器实际上是分解为两个步骤来实现

```java
interface IResource{
    String getStdPath(); // 文件的标准路径
    String getPath(); // 实际文件路径
}

interface IVirtualFileSystem{
    IResource getResource(Strig stdPath);
}


interface IResourceParser{
    IComponentModel parseFromResource(IResource resource);
}
```

IVirtualFileSystem提供了一个类似Docker容器所使用的overlayfs的差量文件系统，而IResourceParser负责对一个具体的模型文件进行解析。

可逆计算理论提出了一个通用的软件构造公式

```java
App = Delta x-extends Generator<DSL>
```

基于这一理论，我们可以把Loader看作是Generator的一个特例，把Path看作是一种极小化的DSL。当根据path加载得到一个模型对象之后，我们可以继续应用可逆计算的公式对此模型对象进行转换和差量修订，最终得到我们所需要的模型对象。举个例子，

在Nop平台中我们定义了一种ORM实体对象的定义文件orm.xml，它的作用类似于Hibernate中的hbm文件，大致格式如下:

```xml
<orm x:schema="/nop/schema/orm/orm.xdef" xmlns:x="/nop/schema/xdsl.xdef">
  <entities>
    <entity name="xxx" tableName="xxx">
       <column name="yyy" code="yyy" stdSqlType="VARCHAR" .../>
       ...
    </entity>
  </entities>
</orm>
```

现在需要为这个模型文件提供一个可视化设计器，我们需要做什么？在Nop平台中，我们只需要增加如下一句描述：

```xml
<orm>
   <x:gen-extends>
      <orm-gen:GenFromExcel path="my.xlsx" xpl:lib="/nop/orm/xlib/orm-gen.xlib" />
   </x:gen-extends>
    ...
</orm>
```

x:gen-extends是XLang语言内置的元编程机制，它是在编译期执行的代码生成器，可以动态生成模型的基类。`<orm-gen:GenFromExcel>`是一个自定义标签函数，它的作用是读取并解析Excel模型，然后按照orm.xml格式的要求来生成orm定义文件。Excel文件的格式如下图所示：

[excel-orm](excel-orm.png)

Excel模型文件的格式其实非常接近于日常中我们使用的需求文档格式（示例中的Excel文件格式本身就是从需求文档中拷贝粘贴得来的）。只需要编辑Excel文件即可实现对ORM实体模型的可视化设计，而且这种设计修改是**即时生效**的！（借助于IResourceComponentManager的依赖追踪能力，只要Excel模型文件发生修改，orm模型就会被重新编译）。

有些人可能对Excel的编辑方式不满意，希望采用类似PowerDesigner这种图形化的设计器。No Problem！只需要调换一下元编程生成器即可，真的就是一句话的事情。

```xml
<orm>
   <x:gen-extends>
      <orm-gen:GenFromPdm path="my.pdm" xpl:lib="/nop/orm/xlib/orm-gen.xlib" />
   </x:gen-extends>
    ...
</orm>
```

现在我们就可以愉快的在PowerDesigner中设计实体模型了。

上面这个例子集中体现了可逆计算理论中所谓**表象转换**（Representation Transformation）的概念。真正重要的是核心的ORM模型对象，可视化设计只是在使用这个模型对象的某种表象，不同表象之间可以进行可逆转换。**表象并不是唯一的！** 而且我们需要注意到，表象转换完全不需要涉及到运行时（即设计器不需要知道ORM引擎的任何相关信息），它完全是形式层面的事情（类似于数学层面的某种形式变换）。目前很多低代码平台的设计器无法脱离特定的运行时支持而存在，这实际上是一个不必要的限制。

现在还有一个有趣的问题。为了支持`<orm-gen:GenFromExcel>`，我们是否需要编写一个特定的Excel模型文件的解析器，用于解析具有示例格式的Excel文档？在Nop平台中，这个回答是：**不需要**。

orm模型本质上是一个Tree结构的对象，这个Tree结构需要满足的约束条件在orm.xdef文件中已经进行了定义。Excel模型是orm模型的一个可视化表象，它也必然可以映射为一个Tree结构。如果这种映射是通过一些确定性的规则可以描述的，则我们就可以使用一个统一的Excel解析器来完成模型解析。

```java
interface ExcelModelParser{
    XNode parseExcelModel(ExcelWorkbook wk, XDefinition xdefModel);
}
```

所以，实际情况是，**只要定义了xdef元模型文件，我们就可以使用Excel对模型文件进行设计**。而在定义了xdef元模型的情况下，模型的解析、分解、合并、差量定制、IDE提示、断点调试器等都是自动得到的，无需额外进行编程。

在Nop平台中，基本的技术战略就是xdef是世界的源起，只要有了xdef元模型，你就自动拥有了前后端的一切。如果你不满意，差量定制会帮助你进行微调和改进。

> 在示例的Excel模型文件中，格式是相对自由的。你可以随意的增删行列，只要它能够以某种**自然的**方式转换为Tree结构即可。如果采用高比格的范畴论的术语，我们可以说ExcelModelParser并不是一个从单个Excel模型对象转换到单个Tree模型对象的转换函数，而是一个作用于整个Excel范畴，将其映射为Tree范畴的一个函子（函子作用于范畴中的每一个对象上，并把它们映射为目标范畴中的一个对象）。范畴论解决问题的方式就是这么夸张，它**通过解决范畴中的每一个问题，然后宣称一个具体的问题被解决了**。这么疯狂的方案如果能够成功，那么唯一的原因就是：It's science。

最后重新强调一下可逆计算的关键点：

1. 全量是差量的一种特例，因此原先的配置文件本身就是合法的差量描述，可逆计算改造可以完全不需要修改已经存在的配置文件。以百度的amis框架为例，在Nop平台中为amis的json文件增加可逆计算支持，只是把装载接口从JsonPageLoader变成IResourceComponentManager，原则上不需要改变原有的配置文件，也不需要变动任何应用层面的逻辑。

2. 在进入强类型世界之前，存在统一的弱类型的结构层。可逆计算可以适用于任意Tree结构（包括且不限于json、yaml、xml、vue）等。可逆计算本质上是一个形式变换问题，
   它可以完全不涉及到任何运行时框架，可以成为多阶段编译的上游部分。可逆计算为领域特定语言、领域特定模型的构造、编译、转换等提供了一系列的基础架构支撑。
   只要使用可逆计算内置的合并操作和动态生成操作，即可以通用的方式实现领域模型的分解、合并、抽象。这种机制既可以用于后端的Workflow和BizRule, 也可以应用于前端页面。同样的，它可以应用于AI模型，分布式计算模型等。唯一的要求就是，这些模型需要以某种结构化的Tree形式来表达。比如，将这一技术应用于k8s，本质上与k8s目前力推的kustomize完全一致。[https://zhuanlan.zhihu.com/p/64153956](https://zhuanlan.zhihu.com/p/64153956)

3. 任何根据名称加载数据、对象、结构的接口，例如loader、resolver、require等函数，都可以成为可逆计算的切入点。 表面上看起来路径名已经是最简单的、无内在结构的原子概念，但可逆计算指出任何量都是差量计算的结果，都存在内在的演化动力。我们可以不把路径名被看作是指向一个静态对象的符号，而把它看作是指向一个计算结果的符号，一个指向可能的未来世界的符号。 Path -\> Possible Path -\> Possible Model

## 小结

简单总结一下本文中所介绍的内容

1. 线性系统好

2. 多重线性系统可以化归为线性系统

3. 线性系统的核心是 Loader:: Path -\> Model

4. Loader可以扩展为 Possible Path -\> Possible Model，加载 = 合成

5. 可逆计算理论提供了更加深刻的理论解释
