# Flow Editor Generator

```
FlowEditor(flowData)
```

```python
FlowEditor = FlowEditorGenerator<FlowEditorComponents, FlowEditorStore, FlowEditorSchema>
```

* FlowEditorComponents provides atomic UI components. These components are business-agnostic and only handle local data.
* FlowEditorStore encapsulates business logic. Events from UI components trigger methods in the store. Shared state and behavior across multiple atomic components are managed within the store.
* FlowEditorSchema is responsible for organizing atomic components and defining form structures.

* The component and function objects can be directly passed, or alternatively used as dynamic modules.
* The render function converts the schema into a virtual DOM.