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

基于可逆计算理论设计的低代码平台NopPlatform已开源：

- gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- 开发示例：[docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [可逆计算原理和Nop平台介绍及答疑\_哔哩哔哩\_bilibili](https://www.bilibili.com/video/BV1u84y1w7kX/)
