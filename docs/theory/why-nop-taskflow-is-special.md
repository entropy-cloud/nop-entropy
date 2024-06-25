# 为什么NopTaskFlow是一个独一无二的逻辑编排引擎

NopTaskFlow是基于可逆计算原理从零开始编写的下一代逻辑编排引擎。因为逻辑编排引擎并不是一个新鲜事物，国内外都有很多开源实现，所以很多人可能对于NopTaskFlow的独特性有所怀疑。为什么它号称是下一代逻辑编排引擎，有什么其他引擎没有的特性？在本文中，我简单分析一下NopTaskFlow与现有开源实现之间的明显差异。

关于NopTaskFlow的详细介绍，参见[从零开始编写的下一代逻辑编排引擎 NopTaskFlow](https://mp.weixin.qq.com/s/2mFC0nQon_l2M82tOlJVhg)

## 一. 最小化信息表达

现有的逻辑编排引擎都是针对某种特定的需求场景进行编写，因此往往引入大量特定于某种使用场景的实现细节。比如说，引入了Vertx框架依赖/Redis依赖/RPC框架依赖/数据库依赖，引入了某种特定的任务队列或者定时调度器等。这使得这些逻辑引擎的适用范围受到极大的限制，也很难轻量化的进行测试。

NopTaskFlow采用了所谓的最小化信息表达的设计，它所实现的相当于是一种纯粹的流程组织规则，并不涉及到任何特定的运行时环境。特别的，我们可以在不启动特殊线程池、不依赖任务队列、也不依赖数据库的情况下执行异步的Task。

NopTaskFlow非常强大，可以说在逻辑编排领域的一切设计模式都可以使用NopTaskFlow来实现，但是它又有着最小化的外部依赖，仅当我们确实需要用到某个特性的时候才会在局部引入某个外部依赖。比如，只有当TaskStep必须用到数据库事务的时候，我们才会通过`<decorator name="transactional" />`来引入类似AOP的事务处理机制，引入对底层数据库事务引擎的依赖。

NopTaskFlow与NopIoC依赖注入容器很好的集成在一起，可以直接使用NopIoC容器来管理复杂的步骤，也可以使用强大的Xpl模板语言来实现步骤抽象，隔离各种外部信息结构。换句话说，NopTaskFlow专注于如何有效的组织、编排业务逻辑，而**如何将业务逻辑抽象为可编排的函数形式并不属于NopTaskFlow要解决的问题**。如何进行函数抽象是一个独立的问题，它由Xpl模板语言和IoC依赖注入容器等通用机制负责解决。

在很多逻辑编排引擎中都特殊规定了很多专用接口，用于集成外部REST服务、调用外部脚本等。在NopTaskFlow中，我们并不会为实现业务逻辑编排而专门设计某种抽象机制，而是利用现成的、已经实现最小化信息表达的封装形式。

> 关于最小化信息表达的介绍，参见[业务开发自由之路：如何打破框架束缚，实现真正的框架中立性](https://mp.weixin.qq.com/s/v2_x4gre4uMfz3yYNPe9qA)。

如果从数学层面理解，NopTaskFlow相当于只引入必要的数学假设，在非常抽象的概念层面完成推理，并且可以直接复用其他已经建立的抽象规律。而一般的逻辑编排引擎则只是实现了某些特例，在推理时使用了很多不必要的实现细节，而且还需要为每个特例引入专门的适配工作。很多传统上的标准做法都没有达到Nop平台最小化信息表达的要求，比如说**开发Web服务函数的时候如果需要指定REST路径，或者无法同时通过REST/GraphQL/gRPC/消息队列/批处理引擎等多种调用方式调用同一个服务函数，则没有达到最小化信息表达**。

## 二. 丰富的结构层次

NopTaskFlow中的结构层次远比一般的逻辑编排引擎要丰富。一般的逻辑编排引擎仅仅是提供一种类似Function或者Procedure的简单抽象结构，在实际的概念细节方面往往缺乏完整性和一致性，**基本上都达不到程序语言设计中函数抽象的严谨程度**，一般也不支持复杂的嵌套组织关系和二次抽象能力。

NopTaskFlow的基本逻辑组织单元是TaskStep，它的定义相当于是一种增强型的函数：

1. TaskStep采用无状态设计，具有明确的输入和输出，并且输入和输出都具有严格的数据类型和Schema约束。很多逻辑编排引擎将步骤设计为对象类型，通过成员变量来实现输入输出，这增加了实现编译优化和动态模型更新的难度。有些还会引入全局的ThreadLocal上下文变量，为异步和并发处理制造了不必要的复杂性。

2. TaskStep具有内部变量作用域，并且TaskStep可以构成堆栈结构，形成类似的堆栈的作用域链。一般的逻辑编排引擎只有全局作用域和步骤局部作用域，对于父子步骤之间的作用域关系缺少管控机制。

3. TaskStep支持装饰器（decorator）概念。大量通用的特性，如调用超时时间、失败重试策略等，都可以通过TaskStep的装饰器来实现。它类似于一般程序语言中的AOP处理机制，可以对TaskStep所提供的函数抽象进行进一步的增强。一般的逻辑编排引擎缺乏这种通用的切面增强机制。

4. TaskStep支持类似协程（Coroutine）的暂停（中断）和恢复（继续执行）的功能，可以实现失败重试，集成TCC事务。

5. 借助于Nop平台内置的元编程能力可以实现类似宏函数的编译期处理能力。Nop平台的元编程可以在DSL结构层面执行而不是在AST语法树层面执行，因此它在形式层面更加灵活，可以实现多种风格的DSL之间的无缝嵌入。
   
   > 关于元编程的进一步介绍，参见[低代码平台中的元编程(Meta Programming)](https://mp.weixin.qq.com/s/LkTIVGSrK9zomPW4bNiqqA)

在NopTaskFlow中，我们可以在多个层面实现通用功能的封装，可以在最适合的粒度上选择最精简的抽象模式。

## 三. 多重表象

我无意中发现很多人对NopTaskFlow的反对意见竟然是因为它采用了XML格式。都2024年了，还有人在使用XML这种被淘汰了的老古董吗？但是这种对技术的看法实在是非常肤浅。Nop平台强调的是技术中立的信息表达，同样的信息可以具有多种不同的表象形式(Representation)，这些不同的表象之间可以自由的进行转换。

在Nop平台中，XML和JSON、YAML之间是自动支持双向转换的。比如说，我们可以使用task.yaml文件来定义逻辑编排：

```yaml
xmlns:x: /nop/schema/xdsl.xdef
x:schema: /nop/schema/task/task.xdef
steps:
    - type: sequential
      name: test
      steps:
          - type: xpl
            name: step1
            source: >
                return "OK1";
          - type: xpl
            name: step2
            inputs:
                - name: result
                  source: RESULT
            source: >
                return result == "OK1" ? "OK" : "FAIL";
```

上面的YAML配置等价于下面的XML配置

```xml
<task x:schema="/nop/schema/task/task.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <steps>
        <sequential name="test">
            <steps>
                <xpl name="step1">
                    <source>
                        return "OK1";
                    </source>
                </xpl>

                <xpl name="step2">
                    <input name="result">
                        <source>RESULT</source>
                    </input>
                    <source>
                        return result == "OK1" ? "OK" : "FAIL";
                    </source>
                </xpl>
            </steps>
        </sequential>
    </steps>
</task>
```

在定义具有复杂嵌套结构的逻辑的时候，特别是当支持元编程的时候，XML形式相比于YAML形式会更有优势。关于XML和JSON的优劣分析，可以参见[为什么Nop平台坚持使用XML而不是JSON或者YAML](https://zhuanlan.zhihu.com/p/651450252)

更进一步，Nop平台中可视化也被视为是信息结构的一种表象（可视化表象 vs 文本表象），因此它试图建立一系列的自动推理机制，从字段级别的 `可视化表象 <=>文本表象`的自动转换关系，自动推导得到表单级别、页面级别的自动转换关系，从而自动得到NopTaskFlow的可视化设计器，而不需要专门针对NopTaskFlow编写一个专用的可视化设计器。



## 四. 可逆计算

NopTaskFlow是Nop平台中定义的DSL森林中的一个具体实例，它的实现大量使用了Nop平台XLang语言所提供的基础设施，因此自动满足可逆计算原理，自动支持Delta差量定制机制。

在Nop平台中，所有的XDSL都具有一些共性特征，并且借助于XDef元模型语言可以统一结构层面的语义，无缝融合在一起。Nop平台所实现的分解模式可以表达为如下公式：

```
App = G<DSL1> + G<DSL2> + G<DSL3> + Delta
App ~ [DSL1, DSL2, DSL3, Delta]
```

每一个DSL可以看作是一个特性分解维度，整个应用程序可以看作是由特性向量+Delta差量构成。

关于XDSL的进一步介绍，可以参见[XDSL：通用的领域特定语言设计](https://mp.weixin.qq.com/s/usInt7_odzvFzuiIUPw4iQ)
