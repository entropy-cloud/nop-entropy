
# Flow Editor

```
FlowEditor(flowData)

FlowEditor = FlowEditorGenerator<flowEditorComponents, flowEditorStore, flowEditorSchema>
```

* flowEditorComponents provide atomic UI components; in principle they are business-agnostic and only involve local information.
* flowEditorStore provides business logic encapsulation. Events on UI components will trigger methods in the Store. Shared state and behaviors that span multiple atomic components are aggregated in the store.
* flowEditorSchema is responsible for organizing atomic components and providing the form definition
 
* Components and function objects can be passed directly, or loaded dynamically as modules
* The render function is responsible for converting the schema into a virtual DOM

<!-- SOURCE_MD5:2dbf759df2f22b974ca8c3333f15e334-->
