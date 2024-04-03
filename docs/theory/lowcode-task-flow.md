# 从零开始编写的下一代逻辑编排引擎

随着低代码概念和产品的流行，很多人都在考虑在自己的项目中引入逻辑编排的概念，将传统上通过手工硬编码生产的逻辑卸载到某个可以灵活配置的逻辑编排引擎上。在本文中，我将介绍一下Nop平台中的逻辑编排引擎NopTaskFlow的设计思想，分析一下NopTaskFlow的设计在数学层面的必然性。在文章的最后我会解释一下为什么NopTaskFlow是下一代逻辑编排引擎，这个所谓的下一代具有什么典型特征。

## 一. 逻辑编排到底在编排什么？

当我们使用传统的编程语言和编程框架进行编程时，本质上是在遵循语言所定义的某种约束规范，也可以看作是某种**最佳实践**。但是当我们自己从零开始编写一个非常灵活、非常底层的逻辑组织框架时，很容易就会打破此前语言内置的形式规范，从而偏离隐含的最佳实践模式。

**可以灵活组织的最小逻辑单元是什么**？传统编程语言的回答现在很标准：函数。那么**函数有什么本质性特征**？

1. 函数具有明确定义的输入和输出

2. 函数可以进行嵌套调用

3. 函数中使用的变量具有复杂的词法作用域

如果更进一步的研究函数的结构，我们还会发现更加复杂的特征，例如

1. 函数参数是传值还是传引用？callByValue? callByRef?callByName?

2. 是否支持函数式参数，也就是所谓的高阶函数？

3. 是否存在独立于返回值的异常处理机制？

4. 是否支持异步返回？

当然，还有最最重要的，**函数不仅仅是认知和组织逻辑的最小单元，还是我们进行抽象的最小单元**。**我们可以复用已有的函数来定义的新的函数**。

那么为什么函数会成为编程语言中最基本的逻辑组织单元，我们现在编写一个逻辑编排引擎的时候还需要基于函数抽象吗？有没有更好的抽象形式？为了搞清楚这个问题，我们有必要懂一点历史。

首先，我们需要清醒的意识到，**计算机编程语言中最初并没有函数概念**，函数概念的建立是一件不平凡的事情。

**早期编程语言（1950s-1960s）**：

- **汇编语言**：在汇编语言中，函数的概念并不明显，程序员通常使用跳转指令来执行代码块。
- **Fortran**：在1955年发布的Fortran语言中，引入了子程序（subroutine）的概念，这可以看作是函数的早期形式。**但Fortran中的子程序不支持返回值**。

**高级编程语言的兴起（1960s-1970s）**：

- **ALGOL 60**：在1960年发布的ALGOL 60中，引入了现代函数概念，支持返回值，并提出了块结构（局部变量作用域），这是编程语言发展中的一个重要里程碑。
- **Lisp**：在1958年开发的Lisp语言中，函数被视为一等公民，这意味着函数可以作为数据传递、存储和返回，这是函数式编程语言的核心特性。

**结构化编程（1970s）**：

* 结构化编程的概念最早由Edsger W.
  Dijkstra在1968年的论文[《Go To Statement Considered Harmful》](https://www.dcs.gla.ac.uk/~pat/cpM/choco4/nqueens/Goto-Harmful-Dijkstra.pdf)中提出，他主张通过限制或消除goto语句的使用来改善程序的结构。结构化编程的核心理念是将程序分解为模块化的部分，使用顺序、选择（if-then-else）和循环（while、for）等结构来控制程序的流程。
- **C语言**：1972年发布的C语言，深受Algol 68的影响，其函数定义简洁，支持递归调用，是第一批原生支持结构化编程概念的高级编程语言之一。C语言的流行极大地推动了结构化编程范式的普及。

后续的1980年代是面向对象的天下，函数的地位下降，变得从属于对象，在Java中我们甚至不能在类的外部单独定义函数。而在2000年以后函数式编程逐渐复兴，推动了不可变性和所谓无副作用的纯函数概念的普及。随着多核并行编程、分布式消息系统和大数据处理系统的盛行，函数的概念也在不断地扩展和深化，现代程序语言开始标配async/await机制。

下面我们来分析一下函数概念所带来的隐含假定是什么。

**首先，函数是信息隐藏的一种必然结果**。**信息隐藏必然导致世界被区分为内部和外部**
，内部的小环境如果可以独立于外部存在（也就是说同样的函数可以在不同的外部环境中被调用，函数内部不需要感知到环境的变化），那么**内部和外部的关联一定被限制为只发生在边界上**。内部从外部获取的信息被称为Input，而外部从内部获取的信息被称为Output。边界的维度一般远小于系统整体结构的维度（类比三维球体的边界是二维的球面），这使得函数能提供降低外在复杂度的价值。

> * 如果在函数内部总是在读写全局变量，那么我们实际是在使用procedure抽象，而不是函数抽象。
> 
> * 服务化相当于是约定了Input和Output都是可序列化的值对象。

**第二，函数自动引入了如下因果顺序关系**：

1. 对表达式求值得到函数的Input参数

2. 执行函数

3. 接收到函数输出Output

在调用函数之前，Input的值就会确定下来，而**函数成功执行之后才会产生Output**。如果函数调用失败，我们根本不会得到输出变量。特别的，如果一个函数具有多个输出的情况下，我们总是要么得到全部Output，要么没有得到Output，不会出现得到只观察到部分Output的情况。

> 现在有些逻辑编配框架会在步骤执行的过程中将一些中间结果暴露出来作为Output
> Endpoint，例如将循环过程中的循环下标暴露为一个Endpoint，在循环的过程中不断输出这种临时的Output，这种做法相当于是偏离了函数抽象。

**第三，函数内部拥有独立的变量作用域（名字空间）**。不管一个变量在函数外部叫做什么名字，它作为参数传递给函数之后，我们**在函数内部总是使用局部的输入变量名来指向它**。同时，函数内部使用的临时变量并不会被外部观测到。有名，万物之母。任何一种大规模的、系统化的复用，第一个要求就是避免名字冲突，必须使用局部名称。

**第四，函数组合调用的时候，是通过当前的scope来间接实现信息传递**。例如

```
 output = f(g(input))
 // 实际对应于
 output1 = g(input)
 output = f(output1)
```

一个变量必须存在于一个scope中。当函数`g`返回的时候，它内部的scope在概念层面就被销毁了，而执行函数`f`之前，它内部的scope还不存在，所以函数`g`的返回值一定是先被存放在外部的scope中，然后再转发给函数f。

**第五，函数意味着配对的双向信息流**。我们都知道goto是有害的，因为goto往往是一去不复返，只有天知道它什么时候才会goback，会goback到哪里。但是函数是一种非常自律的、可预测性极强的信息流组织。Input向函数传递信息，然后**Output一定会返回，而且会在原先的调用点返回**（也就是goto和goback是在数学意义上严格配对的）。传统程序语言中函数调用是同步调用，它带有阻塞语义，会自动阻塞当前的执行指令流（相当于是程序世界的时间线）。对于异步调用而言，函数调用返回并不意味着Output可用，因此只能使用回调函数来处理逻辑依赖关系，导致出现所谓的嵌套回调地狱。现代编程语言中的async/await语法相当于是为异步函数也补充了阻塞语义，使得源码中的函数调用顺序仍然可以被看作是时间线演进的顺序。

> 在分布式架构下最灵活的组织方式无疑是事件发送和监听，本质上它是单向信息传递，与goto有异曲同工之妙。灵活是灵活了，但是代价是什么？

## 二. 最小逻辑组织单元 TaskStep

NopTaskFlow的设计目标是提供一种**支持差量运算的结构化逻辑分解方案**，贴近编程语言中的函数概念无疑是一种最省心的选择，而且如果未来需要高性能编译执行，也更容易将编排逻辑翻译为普通的函数实现代码。

NopTaskFlow中的最小逻辑组织单元是所谓的TaskStep，它的执行逻辑如下图所示：

![taskflow](taskflow/taskflow.png)

```xml
<sequential name="parentStep">
  <input name="a"/>
  <steps>
    <xpl name="step1">
      <!-- 步骤中使用的变量信息需要通过input传入，子步骤并不会直接使用父步骤中的变量 -->
      <input name="a"/>
      <!-- source段的返回值类型如果不是Map，则会被认为是RESULT变量 -->
      <source>
        return a + 1
      </source>
      <!-- 输出变量可以动态计算得到。如果不指定source，则返回当前scope中对应名称的变量 -->
      <output name="a2">
        <source>a*2</source>
      </output>
    </xpl>

    <call-step name="step2" libName="test.MyTaskLib" stepName="myStep">
      <!-- 通过source段可以动态计算得到输入参数。RESULT对应于父scope中名为RESULT的变量 -->
      <input name="a">
        <source>RESULT + 1</source>
      </input>
      <!-- 返回到父scope的结果被重命名为b2 -->
      <output name="b" exportAs="b2"/>
    </call-step>
  </steps>
  <output name="b2"/>
</sequantial>
```

TaskFlow内置了sequential/parallel/loop/choose/xpl/call-step等多种步骤类型，相当于是一种图灵完备的函数式编程语言。

> 具体步骤定义可以查看[task.xdef元模型定义](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef)

上面的示例等价于如下代码

```javascript
var { RESULT, a2 } = function step1(a){
  return { RESULT : a + 1, a2: a*2}
}(a);

var { b: b2} = test.MyTaskLib.myStep({a: RESULT+1})
return { b2 }
```

* xpl相当于是一种函数调用，它的source段执行结果如果不是Map结构，则被设置为RESULT变量
* output变量如果指定了source段，则表示动态执行表达式来构建返回值，否则按照名称从当前scope中获取返回值
* 成功调用step之后，会把output返回的结果设置到当前scope中
* 通过`call-step`可以复用已有的步骤定义
* input变量如果指定source段，则相当于是动态计算变量值，否则就按照name属性从当前scope获取
* output如果指定exportAs属性，则设置到父scope中时会重命名

TaskStep对应的接口定义如下所示：

```java
interface ITaskStep {
  String getStepType();

  List<ITaskInputModel> getInputs();

  List<ITaskOutputModel> getOutputs();

  CompletionStage<Map<String, Object>> execute(
        Map<String, Object> inputs, Set<String> outputNames,
        ICancelToken cancelToken, ITaskRuntime taskRt);
}
```

* TaskStep具有inputs和outputs的模型信息，相当于是一种反射元数据，可以通过它们获知函数的参数名和参数类型，返回变量名和返回变量类型等。
* 作为一种通用编排元件，TaskStep强制约定了多输入和多输出结构，所以inputs和outputs都是Map对象。
* **outNames提供了一种类似于GraphQL的结果数据选择能力**。在调用Step的时候我们就指定是否需要用到哪些返回变量。这样在步骤内部实现时，可以选择性的进行性能优化，对于不需要返回的复杂计算可以直接跳过。
* TaskStep缺省支持异步执行，同时通过**cancelToken提供了运行时取消的能力**。TaskFlow在运行的时候会自动添加await语义，自动等待前一个step结束。
* ITaskRuntime是整个任务执行过程中共享的全局信息，包括TaskScope等。

如果需要扩展TaskFlow，最简单的方式是在NopIoC容器中注册一个`ITaskStep`接口的bean，然后通过如下语法调用

```xml
<simple bean="myStepBean">
  <input name="a1"/>
  <input name="a2" />

  <output name="b1"/>
  <output name="b2" />
</simple>
```

如果真的查看一下ITaskStep接口的定义，会发现它的实现要更加复杂一些：

```java
interface ITaskStep {
    // ...

    TaskStepReturn execute(ITaskStepRuntime stepRt);
}
```

* TaskStepReturn的作用是优化同步调用时的性能，并增加了步骤跳转和步骤挂起的能力。
* ITaskStepRuntime则是把函数参数统一管理起来，便于向下传递。

```java
interface ITaskStepRuntime {

    IEvalScope getEvalScope();

    ITaskStepState getState();

    ITaskRuntime getTaskRuntime();

    ICancelToken getCancelToken();

    Set<String> getOutputNames();

    default boolean needOutput(String name) {
        Set<String> names = getOutputNames();
        return names == null || names.contains(name);
    }

    default Object getValue(String name) {
        return getEvalScope().getValue(name);
    }

    default void setValue(String name, Object value) {
        getEvalScope().setLocalValue(name, value);
    }

    default Object getResult() {
        return getLocalValue(TaskConstants.VAR_RESULT);
    }
}
```

**ITaskStepRuntime除了包含所有步骤外部传入的参数之外，还暴露了内部的状态信息**，额外提供了IEvalScope和ITaskStepState变量。其中ITaskStepState是步骤实例的持久化状态信息，可以用于实现实现类似Coroutine的suspend/resume机制。

IEvalScope是步骤内部的变量作用域，它通过父子关系构成了类似词法作用域的作用域链。当从scope中读取变量的时候，如果在当前scope中没找到，就会自动向上到父scope中去找。一般情况下stepScope的父是TaskScope，也就是说在当前步骤的变量作用域中查找不到的话，会在共享的任务级别的变量作用域中查找，并不会在父步骤的scope中查找。

**除非步骤实例上设置了`useParentScope=true`，此时可以实现类似函数闭包的效果，在父步骤的scope中查找**。

```xml
<sequential name="parentStep">
    <input name="a"/>

    <steps>
        <!-- 如果设置了useParentScope=true，则不需要声明input
              就可以直接读取父scope中的变量a
        -->
        <xpl name="step1" useParentScope="true">
            <source>
                return a + 1
            </source>
        </xpl>
    </steps>
</sequential>
```

### 迷失在历史中的先贤真言

正所谓人类从历史中学到的唯一教训就是人类无法从历史中学到任何教训。二十年就是一代人，下一代人面对新的问题时会忘记前人的智慧，一切仍然从拍脑袋开始。

The unbridled use of the go to statement has an immediate consequence that it becomes terribly hard to find a meaningful
set of coordinates in which to describe the process progress.

a programmer independent coordinate system can be maintained to describe the process in a helpful and manageable way.

空间坐标冻结，而时间坐标在流动。
[考不上三本也能给自己心爱的语言加上Coroutine](https://zhuanlan.zhihu.com/p/25964339)

## 三. 比函数更强的是包装后的函数

领域化之后形成稳定的领域坐标系

不是裸函数，而是可以被修饰的富函数(RichFunction)。

## 四. 考不上三本也能实现Coroutine

## 五. 数据驱动的图模式

## 六. TaskFlow与行为树(Behavior Tree)的区别

## 七. TaskFlow与工作流(Workflow)的区别

## 八. 为什么说是下一代

说NopTaskFlow是下一代逻辑编排引擎，可能有些人会不服气：我看这个设计平平无奇，都是早已被反复实现过的东西，创新点在哪？别急，这里的下一代指的不是它的功能多，也不是性能高，而是它基于下一代软件构造理论：可逆计算理论所构建，从而呈现出与现有的软件架构设计迥然不同的典型特征。
**这些特征是与逻辑编排本身无关的**。这里的逻辑是这样的：

1. 可逆计算是下一代软件构造理论

2. Nop平台是可逆计算指导下从零开始构建的下一代低代码开发平台

3. NopTaskFlow是Nop平台的一个组成部分，它自动继承了这个所谓下一代的特征

这是一件真正有趣的事情。

不是针对单个引擎构建，而是大量引擎共享结构共性。

差量化和元编程

信息最小化表达，描述式结构与运行时结构分离
