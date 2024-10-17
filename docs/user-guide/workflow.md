# 工作流

## 流程引擎后台变量

* wfRt: IWorkflowRuntime类型
* wf: IWorkflow类型
* wfVars: IWorkflowVarSet类型，对应于`wf.getGlobalVars()`

## 参数
arg定义具有persist属性，如果设置为true，则会保存到wfVars集合中，存储到`nop_wf_var`表中。

## 条件判断

```xml
<when>
  <eq name="wfVars.day" value="${7}" />
  <eq name="wfVars.day" value="@:7" />
</when>
```

条件判断中值如果不是字符串类型，则可以用表达式语法或者前缀引导语法来标识数值类型。


