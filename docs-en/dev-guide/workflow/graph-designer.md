# General Workflow Designer

Workflow, logic flow, approval flow, etc., are all similar in their visual representation. They define a set of nodes and connections that can be dragged and edited.

The Nop platform provides a built-in general workflow designer, which can be customized based on the meta-model to generate a specific workflow designer. The specific steps are as follows:

## Steps to Generate the Workflow Designer

1. Design a specific workflow designer model based on graph-designer.xdef, such as oa-flow.graph-designer.xml
2. Use FlowBuilderGenerator to generate a JS library from oa-flow.graph-designer.xml. The front-end can load this workflow definition library using the SystemJs package dynamic loading mechanism.
3. In the front-end's amis page, there is an embedded nop-graph-designer control. It loads workflow graph data via initApi and saves it via saveApi. The internal editor identifies the workflow type and uses the dynamically loaded workflow definition library.

## Workflow Data

When editing in the front-end editor, the data is divided into two parts: `diagram` (for visual representation, such as node position, size, style, etc.) and `data` (additional business data attached to nodes and connections).

## Workflow Model Conversion

The meta-model of NopWorkflow is defined by xwf.xdef. It is a general workflow model supporting concepts like step, action, transition, and actor. However, it is not specifically designed for approval workflows, which lack concepts like signature and approval-specific terms.

Therefore, the core concepts in the workflow model are more comprehensive than those of approval workflows. NopWorkflow's approach uses meta-programming to convert approval workflows into a base workflow model, allowing it to balance ease of use with the flexibility of the underlying engine.

## Framework-Independent Low-Code Solution

The front-end module `@nop-chaos/nop-core` provides an abstract RenderContext type. This allows low-code rendering and inter-component communication, as well as remote calls, to be defined in a framework-independent manner.

```javascript
type RenderContext = {
    /**
     * Renders a JSON object into a virtual DOM type. Different frameworks may implement this differently.
     */
    render: (name: string, schema: SchemaType, props: any, ctx: any) => VDomType,

    /**
     * Dynamically executes an AJAX call.
     */
    executor: (request: FetcherRequest, ctx: any) => Promise<FetcherResult>,

    /**
     * Triggers a custom action via event bubbling.
     */
    onEvent: (event: string, data: any, ctx: any) => any

    /**
     * Listens to events triggered by sibling or parent nodes.
     * @param source The identifier of the sibling or parent node
     * @param handler The callback function
     */
    observeEvent: (source: string, handler: OnEventType) => void
}
```

* The `render` function can be used to encapsulate amis or fomily, etc., for JSON-based front-end low-code frameworks.
* The `executor` function can perform remote API calls via the description-based mechanism, executing asynchronous AJAX requests and handling errors.
* The `onEvent` function can be used to trigger custom actions on parent nodes.
* The `observeEvent` function can monitor events from sibling or parent nodes.
