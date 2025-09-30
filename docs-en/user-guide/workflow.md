# Workflow

## Workflow Engine Backend Variables

* wfRt: of type IWorkflowRuntime
* wf: of type IWorkflow
* wfVars: of type IWorkflowVarSet, corresponds to `wf.getGlobalVars()`

## Parameters
An arg definition includes a persist attribute; if set to true, it will be saved to the wfVars collection and stored in the `nop_wf_var` table.

## Condition Evaluation

```xml
<when>
  <eq name="wfVars.day" value="${7}" />
  <eq name="wfVars.day" value="@:7" />
</when>
```

In condition evaluation, if a value is not a string type, you can use expression syntax or prefix-guided syntax to denote a numeric type.
<!-- SOURCE_MD5:ccaf9ca4da18716bce6e5c0efe8fd49a-->
