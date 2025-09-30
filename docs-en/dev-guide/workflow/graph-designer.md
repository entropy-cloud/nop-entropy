# General Flow Designer

Workflow, logic flow, and approval flow visual designers share similar structures: they define a set of nodes and connections, allow dragging nodes, editing node properties, and so on.

The Nop Platform provides a built-in general flow designer that can generate a specific flow designer based on a meta-model definition. The steps are as follows:

## Steps to Generate a Flow Designer

1. Design a concrete flow designer model based on graph-designer.xdef, for example, oa-flow.graph-designer.xml
2. FlowBuilderGenerator generates a JS library from oa-flow.graph-designer.xml; the frontend can dynamically load this flow definition library using the SystemJs package loading mechanism
3. On the frontend amis page, the nop-graph-designer control is built in. It loads flowchart data via initApi and saves it via saveApi.
   Its internal editor will recognize the flow type and use the dynamically loaded flow definition library.

## Flowchart Data

When editing in the frontend editor, data is split into two parts: diagram and data. diagram corresponds to the graphical display data, such as node position, size, style, etc., while data refers to additional business data attached to nodes and connections.

## Flow Model Transformation

The NopWorkflow meta-model is defined by the xwf.xdef meta-model. It is a general workflow model that supports core workflow concepts such as step/action/transition/actor, but it is not a flow engine specifically designed for approval flows.
Therefore, core concepts such as countersign and OR-sign, which are specific to approvals, are not present. A workflow model is far more powerful than an approval flow model; however, in terms of configuration and usage, for approval scenarios the approval flow model is simpler and easier to use.

NopWorkflow uses metaprogramming to transform the approval flow model into the underlying workflow model, thereby balancing the model’s ease of use with the generality of the underlying engine.

## Framework-agnostic Low-code Solution

The frontend `@nop-chaos/nop-core` module provides the RenderContext abstraction, which defines low-code rendering, inter-component communication, and remote invocation in a framework-agnostic manner.

```javascript
type RenderContext = {
    /**
     * Render a JSON object into a virtual DOM type. Different frameworks have different implementations
     */
    render: (name:string, schema: SchemaType, props:any, ctx:any) => VDomType,

    /**
     * Dynamically execute an AJAX call,
     */
    executor: (request: FetcherRequest, ctx:any) => Promise<FetcherResult>,

    /**
     * Bubble up to trigger a custom action
     */
    onEvent: (event:string, data:any, ctx:any) => any

    /**
     * Listen to events triggered by sibling or parent nodes
     * @param source Identifier of the sibling or parent node
     * @param handler Callback function
     */
    observeEvent: (source: string, handler: OnEventType) => void
}
```

* The render function can be used to wrap any frontend low-code framework that renders from JSON, such as amis or fomily
* The executor function can execute AJAX requests based on declarative remote API calls and return results asynchronously, automatically handling loading states and error message prompts
* The onEvent function can be used to notify the parent node that an event occurred on the current object
* observeEvent can be used to listen for events occurring in parent or sibling nodes

<!-- SOURCE_MD5:fa0a882373bd321aff5185d33b88c275-->
