# 通过NopTaskFlow逻辑编排实现后台服务函数

[视频介绍](https://www.bilibili.com/video/BV19J4m1J78t/)

在微服务架构下，服务是某种粗粒度的可以被复用的单元。在跨业务领域的协同中，我们可以引入服务编排引擎，来对微服务调用进行灵活组合。
但是当我们聚焦到一个服务内部的逻辑结构的时候，我们需要更加高效、简洁的实现，一般的服务编排引擎就很难处理了。

1. 服务编排很多内置了REST调用或者某种远程调用假定，调用本地函数时反而不够简单直接。
2. 服务调用一般输入输出都是可序列化的值对象（例如JSON），无法通过引用直接共享复杂的领域模型对象。
3. 服务编排引擎一般没有引入本地环境抽象，无法指定某几个步骤在同一个数据库事务中执行或者使用同一个OrmSession。
4. 服务编排很多强制要求引入某些重型基础设施，比如消息队列、REDIS、持久化数据库等，无法以无第三方依赖、无持久化的轻量级形态运行。

NopTaskFlow采用了最小化信息表达的设计原则，将逻辑编排中最核心的纯逻辑部分抽象出来，可以同时支持重量级的分布式服务编排，也可以支持轻量级的服务内函数级别的细粒度逻辑编排。通过[XLang语言](../xlang/index.md)内置的元编程机制，我们可以按需引入持久化、事务处理、分布式RPC调用等机制。

> 关于最小化信息表达，参见[业务开发自由之路：如何打破框架束缚，实现真正的框架中立性](https://zhuanlan.zhihu.com/p/682910525)

在Nop平台中，NopTaskFlow相当于是提供了一种通用的对函数进行结构化分解和组织的机制，在任何需要使用函数的地方，都可以被替换为调用NopTaskFlow来实现。

> 关于NopTaskFlow的介绍，参见[从零开始编写的下一代逻辑编排引擎 NopTaskFlow](https://zhuanlan.zhihu.com/p/691166138)

逻辑编排框架的一个典型应用场景是用于实现后台服务函数，即原先我们手工编写后台服务函数，现在改成调用NopTask服务编排模型。

在Nop平台中，我们可以在xbiz模型文件中定义服务函数。

```xml
<!-- /nop/demo/model/Demo/Demo.xbiz -->
<biz>
  <actions>
    <mutation name="callTask">
      <arg name="a" type="java.lang.Integer" mandatory="false"/>
      <arg name="b" type="java.lang.Integer" mandatory="false"/>
      <arg name="_selection" type="io.nop.api.core.beans.FieldSelectionBean" kind="FieldSelection"/>
      <arg name="svcCtx" type="io.nop.core.context.IServiceContext" kind="ServiceContext"/>
      <return>
        <schema>
          <props>
            <prop name="sum" type="java.lang.Integer"/>
          </props>
        </schema>
      </return>
      <source>
        const taskFlowManager = inject('nopTaskFlowManager');
        const task = taskFlowManager.getTask('test/DemoTask',1);
        const taskRt = taskFlowManager.newTaskRuntime(task,false,svcCtx);
        taskRt.setInput('a',a);
        taskRt.setInput('b',b);
        return task.executeAsync(taskRt,_selection?.sourceFields);
      </source>
    </mutation>
  </actions>
</biz>
```

> 在`Demo.xbiz`业务对象模型中增加`callTask`方法，对外会暴露为`/r/Demo__callTask`这个REST服务端点。

如果手工编写NopTaskFlow的集成代码，那么就会出现类似上面示例的大段模式化代码，它不可避免的会导致信息重复表达。例如，task中已经定了输入变量是a和b,
在xbiz模型中我们仍然需要重复声明参数的定义。

Nop平台强调最小化信息表达，这意味着一切能自动推导得到的信息都应该是推导得到，而不需要明确表达。设想一下，**如果Web框架与逻辑编排引擎是一体化设计的**，我们只需要表达如下信息即可：

```xml
<mutation name="callTask" task:name="test/DemoTask"/>
```

根据`task:name`定位到TaskFlowModel之后，我们可以根据逻辑编排模型中的信息自动推导得到REST服务函数的输出输出参数类型以及服务函数的具体实现代码。

Nop平台的Web框架和逻辑编排框架是独立设计的，它们互相并没有对方的知识，但是我们**可以通过编译期元编程的机制将它们两者无缝粘结在一起**。

```xml
<biz>
  <x:post-extends>
    <biz-gen:TaskFlowSupport xpl:lib="/nop/core/xlib/biz-gen.xlib"/>
  </x:post-extends>

  <actions>
    <mutation name="callTask" task:name="test/DemoTask"/>
  </actions>
</biz>
```

`x:post-extends`是Nop平台中所有DSL都具有的一个编译期后处理机制，在其中我们可以执行代码来对当前模型进行修正。[`<biz-gen:TaskFlowSupport>`](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-task/nop-task-core/src/main/resources/_vfs/nop/task/xlib/task-gen.xlib)标签会识别`task:name`属性，并自动推导得到action的完整定义。

> `<biz-gen:TaskFlowSupport>`类似于引入了一个数学定理，应用它将自动实现数学推导。这是一种数学层面上的纯形式变换，与框架的运行时没有任何关系。

我们还可以对以上调用方式进行进一步的简化，将`<x:post-extends>`抽象到某个基础模型中。

```xml
<biz x:extends="/nop/biz/lib/common.xbiz">
  <actions>
    <mutation name="callTask" task:name="test/DemoTask"/>
  </actions>
</biz>
```

目前根据数据模型生成的所有xbiz模型都自动引入了`<biz-gen:TaskFlowSupport>`，所以可以直接使用`task:name`配置。

```xml
<biz x:extends="_NopAuthUser.xbiz">
  <actions>
    <mutation name="callTask" task:name="test/DemoTask"/>
  </actions>
</biz>
```

**动态更新:**

Nop平台基于元编程自动推导得到新模型的过程可以看作是一种**响应式推导**：当被依赖的模型发生变化（被修改）的时候，已经推导得到的模型缓存会自动失效。例如，在上面的示例中，`test/DemoTask`这个逻辑编排任务被修改后，`callTask`的action定义会重新生成，确保action定义与task的定义永远保持一致。

> 具体的实现机制类似于vue的响应式数据跟踪，在引用模型的时候自动记录模型文件之间的依赖关系。
