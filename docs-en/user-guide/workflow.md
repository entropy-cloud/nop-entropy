

# Process Engine Backend Variables

* wfRt: IWorkflowRuntime type
* wf: IWorkflow type
* wfVars: IWorkflowVarSet type, corresponding to `wf.getGlobalVars()`


## Arguments

The arg definition has a persist property. If set to true, it will be saved to the wfVars collection and stored in the `nop_wf_var` table.


## Condition Evaluation

```xml
<when>
  <eq name="wfVars.day" value="${7}" />
  <eq name="wfVars.day" value="@:7" />
</when>
```

In condition evaluation, if the value is not a string type, it can be identified using expression syntax or prefix notation to indicate numeric types.

**********************************#

