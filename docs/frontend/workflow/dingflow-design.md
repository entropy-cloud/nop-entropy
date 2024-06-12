# 流程设计器

```
FlowEditor(flowData)

FlowEditor = FlowEditorGenerator<flowEditorComponents, flowEditorStore, flowEditorSchema>
```

* flowEditorComponents提供原子UI组件，原则上与业务无关且只涉及到局部信息。
* flowEditorStore提供业务逻辑封装。UI组件上的事件会触发Store中的方法。跨越多个原子组件的共享状态和行为都汇集在store中。
* flowEditorSchema 负责组织原子组件，提供表单定义
 
* 可以直接传递组件和函数对象，也可以作为module动态加载
* render函数负责将schema转换为虚拟DOM