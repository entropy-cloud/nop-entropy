# 通用流程设计器

工作流、逻辑流、审批流等可视化设计器基本结构类似，都是定义一组节点、连线，然后可以拖拽节点，编辑节点属性等。

Nop平台提供了一个内置的通用流程设计器，可以根据元模型定义具体生成一个特定的流程设计器。具体步骤如下：

## 流程设计器生成步骤

1. 根据graph-designer.xdef设计一个具体的流程设计器模型，例如oa-flow.graph-designer.xml
2. FlowBuilderGenerator根据oa-flow.graph-designer.xml生成一个js库，前端可以使用SystemJs包加载机制动态加载这个流程定义库
3. 在前端的amis页面中，已经内置了nop-graph-designer控件，它通过initApi加载流程图数据，通过saveApi保存流程图数据。
   它内部的editor会识别流程类型，并使用动态加载的流程定义库。

## 流程图数据

前端编辑器进行编辑的时候，数据分成两个部分diagram和data，其中diagram对应于图形化展示数据，比如节点的位置、大小、样式等，而data则是附加在节点上和连线上的额外的业务数据

## 流程模型转换

NopWorkflow的元模型由xwf.xdef元模型定义，它是一个通用的工作流模型，内部支持 step/action/transition/actor等工作流核心概念，但是它并不是为审批流特殊设计的流程引擎，
因此核心概念中没有会签、或签等审批专用概念。工作流模型远比审批流模型要强大，但是在配置和使用层面，如果针对审批场景，审批流模型更简单、易用。

NopWorkflow的做法是通过元编程，将审批流模型转化为底层的工作流模型来应用，这样就可以同时兼顾模型的易用性和底层引擎的通用性。

## 与具体框架无关的低代码方案

前端的`@nop-chaos/nop-core`模块提供了RenderContext抽象，它将低代码的渲染和组件间通讯机制、远程调用机制都以框架无关的方式定义出来。

```javascript
type RenderContext = {
    /**
     * 将json对象渲染为虚拟DOM类型。不同的框架实现不同
     */
    render: (name:string, schema: SchemaType, props:any, ctx:any) => VDomType,

    /**
     * 动态执行ajax调用，
     */
    executor: (request: FetcherRequest, ctx:any) => Promise<FetcherResult>,

    /**
     * 向上冒泡触发自定义动作
     */
    onEvent: (event:string, data:any, ctx:any) => any

    /**
     * 监听兄弟节点或者父节点触发的事件
     * @param source 兄弟节点或者父节点的标识
     * @param handler 回调函数
     */
    observeEvent: (source: string, handler: OnEventType) => void
}
```

* render函数可以用于封装amis或者fomily等任何基于json进行渲染的前端低代码框架
* executor函数可以根据描述式的远程API调用执行AJAX请求，并异步返回结果，自动处理loading和错误消息提示等问题
* onEvent函数可以用于通知父节点本对象有事件发生
* observeEvent可以用于监听父节点或者兄弟节点中发生的事件
