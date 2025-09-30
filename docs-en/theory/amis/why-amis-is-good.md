
# Why Baidu's AMIS Framework Is an Excellent Design

[AMIS](https://aisuda.bce.baidu.com/amis/zh-CN/docs/index) is an open-source front-end low-code framework from Baidu. Fairly speaking, it is currently one of the most exquisitely designed low-code frameworks in the front-end open-source community, representing the highest level of low-code open source in China. AMIS adopts a JSON syntax format, with a large number of mature, out-of-the-box components built in, which can be easily integrated and extended. In practical terms, AMIS provides relatively rich online documentation and supports online testing. Based on my observation, typical back-end developers can, through self-exploration, grasp the basics of AMIS in a short time and quickly build fairly complex CRUD pages.

The built-in components of AMIS are mainly those commonly used in back-office management software, but its design itself is generic and not constrained by the scenarios it currently supports. In this article, I will analyze some design highlights of the AMIS framework and introduce the improvements we made when integrating AMIS into the Nop platform.

> The code implementation quality of AMIS is not particularly high; especially because it has evolved over a long period, its internal implementation contains a lot of redundant code, leaving ample room for improvement. Over the past one or two years, Baidu has invested resources to carry out significant code refactoring in AMIS, and the overall situation is still in the process of continuous improvement. This article mainly focuses on analysis at the conceptual design level and does not delve into concrete implementation code.

## I. Env: Environment Abstraction

A notable difference between AMIS and other open-source low-code frameworks is that it explicitly defines an [environment abstraction Env](https://aisuda.bce.baidu.com/amis/zh-CN/docs/start/getting-started#%E6%8E%A7%E5%88%B6-amis-%E7%9A%84%E8%A1%8C%E4%B8%BA), which includes all execution logic related to input/output and page navigation. This design significantly reduces the difficulty of integrating the AMIS framework, making it easy to plug into other underlying UI frameworks. For example, in the Nop platform, we develop the main UI framework using Vue 3.0 and implement single-page routing via Vue Router. For specific pages or embedded components, we can use AMIS, which is based on React technology.

```javascript
 const env = {
      // Execute ajax requests
     fetcher(options) { ... },
     // Implement single-page navigation via Router
     updateLocation(location,replace){ ... },
      // Open a new browser window
     jumpTo(to,action){ ... },
      // Similar to window.alert, pop up a message
     alert,
      // Similar to window.confirm, pop up a confirmation dialog
     confirm,
     // Pop up notifications and error prompts
     notify(type,msg)
 }
 // Render a page based on a JSON configuration
 renderAmis(
      // JSON-formatted page schema description
      jsonPage,
      // Pass in additional data
      {
        data:{
          myVar: 123,
        }
      },
      // Pass in the environment object
      env
    );
```

If we regard AMIS’s JSON page definition as a domain-specific language (DSL), then the AMIS framework can be viewed as the virtual machine responsible for interpreting and executing this DSL. When AMIS needs to perform AJAX calls, pop up messages, or navigate pages, it invokes the corresponding methods on env; thus env can be viewed as a virtualized abstraction interface specifically responsible for performing input/output operations.

Once all input/output actions are virtualized, output = AmisPage(Input), the AMIS page can be seen as a local processing function with limited scope, and can be easily woven into the external business processing flow.

## II. Api: Duality of Values and Functions

For wrapping remote service calls, the AMIS framework provides a declarative definition method, the so-called [Api object](https://aisuda.bce.baidu.com/amis/zh-CN/docs/types/api).

```typescript
interface ApiObject{
  /**
   * API request method
   */
  method?: 'get' | 'post' | 'put' | 'delete' | 'patch' | 'jsonp';

  /**
   * API target address
   */
  url: SchemaUrlPath;

  /**
   * Controls the payload. When key is `&` and the value is `$$`, all raw data is flattened into data.
   * When the value is $$, assign all raw data to the corresponding key.
   * When the value starts with $, set the variable value to the key.
   */
  data?: {
    [propName: string]: any;
  };

  /**
   * Payload format
   */
  dataType?: 'json' | 'form-data' | 'form';

  /**
   * If this is a file download interface, configure this.
   */
  responseType?: 'blob';

  /**
   * Carry headers. Usage is the same as data and can use variables.
   */
  headers?: {
    [propName: string]: string | number;
  };

  /**
   * Set send conditions.
   */
  sendOn?: SchemaExpression;

  /**
   * Default is append mode; set this to true if you want full replacement.
   */
  replaceData?: boolean;

  /**
   * Whether to auto-refresh. When values in the url change, automatically refresh data.
   *
   * @default true
   */
  autoRefresh?: boolean;

  /**
   * When auto-refresh is on, by default variable changes are tracked via the API url.
   * If you want to monitor variables outside the url, configure traceExpression.
   */
  trackExpression?: string;

  /**
   * If set, the same interface with the same parameters within the specified time (ms) will use cache.
   */
  cache?: number;

  // Transform request data submitted to the backend
  requestAdaptor?: (api: ApiObject) => ApiObject;

  // Transform response data returned from the backend
  adaptor?: (payload: object, response: fetcherResult, api: ApiObject) => any;

  /**
   * Do not pop up prompt on failure
   */
  silent?: boolean

   /**
   * On success or failure, look up the corresponding message object here and prompt via toast
   */
   messages?: { [name: string]: string }
}
```

The Api object is a reactive data structure: when its parameter data changes, it automatically executes remote calls, and the returned result can be transformed and cached.

Traditionally, front-end code is written from the function perspective: when an event is triggered, a handler function executes; if we want to modify a value of an associated component, we call an update function on that component. For example:

```javascript
<select id="a" onchange="handleChange">
</select>
<select id="b">
</selection>

<script>
function handleChange(value){
    fetchRelatedOptions(value).then(result=>{
        getComponent('b').setOptions(result)
    })
}
</script>
```

In a more modern front-end framework like AMIS, most of the time we adopt a data perspective: we don’t regard a function as an action executed upon an event; rather, we treat a function as a dynamic producer of result data. When certain conditions occur, the result data is automatically updated; that update process is exactly the function being executed. For example, implementing the linkage of two drop-down lists in AMIS:

```json
{
    "type": "form",
    "body":[
        {
           "type": "select",
           "name": "a",
           "options" : [ ...]
        },
        {
            "type": "select",
            "name": "b",
            "source": {
                "method" : "post",
                "url" : "/get-related-options",
                "data": {
                     "value": "${a}"
                 }
            }
        }
    ]
}
```

The name of the first select component is a, indicating its selected value corresponds to a variable named a in the contextual environment; the select component can be viewed as the viewer and modifier of that variable. The source property of the second select corresponds to an Api object; it listens to changes on variable a via data-binding expressions, automatically performing an AJAX call to fetch a new list of options whenever a changes.

If we focus not only on the function call process triggered by a single event but highlight the overall structure—observing the complete runtime process of the application—we find its structure is "... data -> function -> data -> function -> data ...". This information-transmission network has two dual perspectives: one is "function -> data -> function", the other is "data -> function -> data". We can interpret the information flow as functions calling other functions with parameters, or as data changes triggering reactive functions to produce new data.

> A side-effecting void function essentially does have a return value: it changes some state variables that are not explicitly expressed.

Compared to Vue 3.0’s reactive design, we can find interesting connections. AMIS’s Api object is similar to Vue 3.0’s ComputedRef type, except it triggers not a synchronous front-end function call, but an asynchronous remote service call. If we want to implement a mechanism similar to the Api object in Vue 3.0, we could extend Vue as follows:

```javascript
interface Api<T> extends Ref<T>{
  /**
   * The config object used when building the Api
   */
  readonly config: ApiConfig

  /**
   * Current value, fully equivalent to vue's ref.value
   */
  readonly value: T | undefined

  /**
   * Whether input parameters have changed such that remote loading should execute.
   * If currently active, a load is automatically initiated.
   * In suspended state, shouldRefresh is recorded but the request is not actually initiated.
   */
  readonly shouldRefresh: boolean

  /**
   * Whether remote data loading is in progress
   */
  readonly loading: boolean

  readonly loaded: boolean

  /**
   * Whether remote loading has timed out. On timeout, success is set to false.
   */
  readonly timeouted: boolean

  /**
   * Whether the value has been successfully obtained. If the request times out, the set value is false.
   */
  readonly success: boolean | undefined

  /**
   * Exception object on remote call failure
   */
  readonly error: any

  suspend(): void

  resume(): void

  /**
   * Cancel the current load operation
   */
  cancel(): void

  /**
   * Reload the value. 'immediately' skips debounce.
   * In a suspended state, calling reload also executes.
   */
  reload(immediately?: boolean): Promise<T|undefined>

  /**
   * An rxjs-like interface. Triggers this function each time the api's value changes.
   */
  subscribe(
    next: (value: T) => void,
    error?: (err: any) => void,
    complete?: () => void
  ): StreamUnsubscribe

  /**
   * Apply a transform function to get the result
   *
   * @param fn
   */
  transform<R>(fn: (v: T | undefined) => R): Ref<R>

    /**
     * Destroy this object; its value no longer updates or remains valid
     */
    destroy(): void
}

interface ApiConfig {
  ... Properties and methods defined in ApiObject

  /**
   * How to merge the newly fetched value with the previous one;
   * default is replacement; when specified as append, merge as arrays.
   */
  merger?: (v1: T | undefined, v2: T) => T

  /**
   * Default value before any actual successful load
   */
  defaultValue?: T

  /**
   * If greater than 0, periodically fetch data (milliseconds)
   */
  pollInterval?: number

  /**
   * Cache key for the API return result; can be a direct value or computed dynamically.
   * If unspecified, use JSON.stringify(req) as the key.
   */
  cacheKey?: any | ((req: ApiRequest) => string)

  /**
   * If greater than 0, cache data for a period; thereafter, if parameters remain the same,
   * do not repeat fetching.
   */
  cacheTimeout?: number

  cacheStorage?: string

  /**
   * If greater than 0, treat requests exceeding specified time as timeout,
   * auto-cancel and throw a timeout exception.
   */
  completeTimeout?: number

  connectTimeout?: number

  /**
   * For streaming data, if there's no data for a period, treat as timeout.
   */
  idleTimeout?: number

  /**
   * Callback when value is successfully fetched from the backend
   */
  onSuccess?: (value: T) => void

  /**
   * Whether timeout, backend exception, or manual cancellation, any non-normal fetch
   * triggers this callback.
   */
  onFailure?: (err: any) => void

  /**
   * During data fetching, progress information may be returned
   */
  onProgress?: (progress: any) => void

  onStreamComplete?: () => void

  /**
   * Debounce delay (milliseconds)
   */
  debounce?: number

  /**
   * Throttle delay (milliseconds). The difference from debounce
   * is that the start point of debounce keeps being pushed back.
   */
  throttle?: number

  /**
   * Whether data is streaming and keeps returning values
   */
  stream?: boolean

  /**
   * Whether API performs load eagerly after creation; default false.
   * When false, the first load is only triggered when the value is actually accessed.
   */
  eager?: boolean

  /**
   * If true, the API is created in a suspended state; unless reload() is explicitly called,
   * data loading will not execute automatically.
   */
  suspended?: boolean

  /**
   * Optional exception handling on remote load errors
   */
  fallback?: (err: any, api: Api<T>) => any

  /**
   * Retry count on load error; default 0
   */
  retries?: number

  retryDelay?: number

  maxRetryDelay?: number
}
```

If we view Api not as a function call mechanism, but as a reactive data stream object (Stream), then certain control constructs can be naturally incorporated, such as debounce delays, retry, pollInterval periodic refresh, cancel/suspend controls, etc. As an extension of Ref, in addition to the value itself, the Api object can have attributes such as loading, status, error, thereby fully stateful-izing the remote call process. In this case, the role of Api is basically similar to the [useSWR mechanism](https://juejin.cn/post/6943397563114455048) in React Hooks.

AMIS’s Api object does not directly provide streaming data support, but it offers a dedicated [Service container](https://aisuda.bce.baidu.com/amis/zh-CN/components/service) that can serve a similar function.

```javascript
 {
    "type": "service",
    "api": "/amis/api/mock2/page/initData",
    "body": {
      "type": "panel",
      "title": "$title",
      "body": "The API returns a Map; the value of the 'date' property is ${date}"
    }
  }
```

Service interface definition:

```javascript
interface ServiceSchema{
  /**
   * Specifies the Service component for data fetching.
   */
  type: 'service';

  /**
   * On page initialization, set an API to fetch data.
   * The sending payload will carry the current data (including querystring params).
   * The fetched data will be merged into data for components to use.
   */
  api?: SchemaApi;

  /**
   * WebSocket address for real-time data
   */
  ws?: string;

  /**
   * Fetch data by calling an external function
   */
  dataProvider?: ComposedDataProvider;

  /**
   * Content area
   */
  body?: SchemaCollection;

  /**
   * @deprecated Replaced by api's sendOn.
   * This change shows that normalization of Api in AMIS is ongoing.
   */
  fetchOn?: SchemaExpression;

  /**
   * Fetch by default?
   */
  initFetch?: boolean;

  /**
   * Fetch by default? Decide via expression.
   *
   * @deprecated Replaced by api's sendOn.
   */
  initFetchOn?: SchemaExpression;

  /**
   * API to fetch remote schema
   */
  schemaApi?: SchemaApi;

  /**
   * Whether to load schemaApi by default
   */
  initFetchSchema?: boolean;

  /**
   * Configure via expression.
   * @deprecated Replaced by api's sendOn.
   */
  initFetchSchemaOn?: SchemaExpression;

  /**
   * Whether to poll-fetch
   */
  interval?: number;

  /**
   * Silent polling?
   */
  silentPolling?: boolean;

  /**
   * Condition to stop polling.
   */
  stopAutoRefreshWhen?: SchemaExpression;

  messages?: SchemaMessage;

  name?: SchemaName;
}
// ServiceStore adds status variables like fetching, error, etc.
const ServiceStore = iRendererStore
  .named('ServiceStore')
  .props({
    msg: '',
    error: false,
    fetching: false,
    saving: false,
    busying: false,
    checking: false,
    initializing: false,
    schema: types.optional(types.frozen(), null),
    schemaKey: ''
  })
```

Service supports data polling or returning streaming data via websocket.

```javascript
{
  "type": "service",
  "ws": {
    "url": "ws://localhost:8777?name=${name}",
    "data": {
      "name": "${name}"
    }
  },
  "body": {
    "label": "Name",
    "type": "static",
    "name": "returnVar"
  }
}
```

You can set only ws to fetch all data via websocket; or set both api and ws, where api fetches full data and ws fetches real-time updates.

Service-fetched data is readable by sibling nodes. In this sense, viewing Service as a container concept is a historical misnomer; essentially, it is a more flexibly organized Api object that supports streaming data.

> Beyond dynamic data fetching, another use of Service is to fetch page fragment definitions dynamically, generating pages from the returned schema. However, according to the analysis in this article, fetching data and fetching schema are inherently orthogonal and should ideally be designed separately.

Besides Api, AMIS also provides a lightweight dynamic calculation mechanism similar to Vue 3.0’s computed: the formula component.

```javascript
      {
        "type": "input-text",
        "name": "b",
        "label": "B"
      },
      {
        "type": "formula",
        "name": "b",
        "formula": "''",
        "condition": "${radios}",
        "initSet": false
      }
```

The above example shows an interesting use of formula. Both components share the name b, but the formula component adds a condition: when the radios variable changes, it executes the formula and sets variable b to empty. The formula component acts like a reset of variable b triggered by changes to the radios option.

The formula component can only write embedded expressions and cannot perform more complex function abstractions. Since Api is essentially a higher-order data type than computed, we can, to some extent, use Api to simulate the computed mechanism. For example:

```javascript
{
    url: "@action:myFunc",
    data: {
       a: "${a}" 
    }
}
```

@action:myFunc is an extended function call mechanism we added to the AMIS platform based on the Env abstraction in the Nop platform. The above Api definition means that when variable a changes, it triggers the myFunc function in the context environment. In myFunc, we can execute some front-end logic without necessarily initiating a remote call.

Since version 1.4.0, the Service container can set a dataProvider property to specify a data-loading function, which can partially simulate Vue’s computed properties.

```javascript
{
    "type": "service",
    "data": {
      "x": 123
    },
    "dataProvider": "const timer = setInterval(() => { setData({:data.x, date: new Date().toString()}) }, 1000); return () => { clearInterval(timer) }",
    "body": {
      "type": "tpl",
      "tpl": "Current time: ${date}, ${x}"
    }
}
```

Within the dataProvider function, you can access the data parameter via data and update the store’s data by calling setData.

In an ideal reactive front-end framework, we can expect to bind component properties using static values, dynamic expressions, reactive Ref references, or asynchronous Api objects (stream data) with state tracking. However, this is not currently possible in AMIS’s implementation, so a select component uses the options property to set static option lists, while dynamic option lists require a separate source property. This also implies which component properties support asynchronous Api calls must be implemented individually for each component.

## III. Data Chain: State Tree and Lexical Scope

Before Vue 3.0, the Vuex framework provided single state-tree management for Vue components.

```javascript
const Counter = {
  template: `<div>{{ count }}</div>`,
  computed: {
    count () {
      return this.$store.state.count
    }
  }
}
```

From today’s perspective, Vuex’s design is clearly more convoluted than necessary. When we need to use a variable from the store within a component, why must we detour via the this pointer, pull the variable from the store, and then wrap it as a computed property on the object? Why not directly use the variable from the store?

In Vue 3.0, the [Pinia framework](https://pinia.web3doc.top/introduction.html) replaces Vuex, with behavior more aligned to intuition.

```javascript
const store = useStore();
const { count } = storeToRefs(store)
// You can directly use store variables, or deconstruct ref variables from the store and then use them
return () => <div> {mainStore.count} --- {count} </div>
```

Combined with JSX syntax, we can access corresponding state variables directly via JavaScript variable names. Variables are looked up one lexical scope at a time according to JavaScript’s lexical scoping rules. If multiple variables share the same name, the nearest one is resolved.

AMIS’s so-called [data chain concept](https://aisuda.bce.baidu.com/amis/zh-CN/docs/concepts/datascope-and-datachain) is essentially a set of lexical scopes maintained by the AMIS framework. When we set the name property for a component in AMIS’s DSL, it binds the component to a variable of the same name in the current data domain (two-way data binding). When using expression syntax, variables in the expression are also resolved in the data domain. Therefore, the so-called data domain can be viewed as a lexical scope in the DSL. AMIS establishes the following lookup rules:

1. First attempt to find the variable in the current component’s data domain; when found, complete rendering via data mapping and stop.
2. If not found in the current data domain, look upward in the parent component’s data domain, repeating steps 1 and 2.
3. Continue searching up to the top-level node, i.e., the page node, then stop.
4. If there are parameters in the URL, one more level above is also searched, so you can often directly use ${id} to get querystring parameters.

Vuex maintains a single-state tree, managing the entire application’s state variables as a single-rooted tree, an idea inherited from Redux. In practice, we often do not fully leverage the hierarchical structure of the tree. In AMIS, only a few container components create new data domains (besides the top-level Page, these include CRUD, Dialog, IFrame, Form, Service, etc.). When these container components are nested to produce the page, they automatically mount their own store into a global StoreTree structure, and the entire page naturally forms a StateTree. With the help of the data-chain lookup mechanism, when writing business logic we can use only the most relevant local variable names to simplify logic expressions. For example, in a table component’s cell, the data domain is first the data row, and other columns can be accessed directly via variable names.

```json
{
    "type": "crud",
    "api": "/amis/api/mock2/sample",
    "columns": [
      {
        "name": "version",
        "label": "Engine version"
      },
      {
        "label": "Next Version",
        "type": "tpl",
        "tpl": "${version+1}"
      }
    ]
  }
```

AMIS provides a dedicated [Combo component](https://aisuda.bce.baidu.com/amis/zh-CN/components/form/combo), which fully leverages hierarchical object data to implement editing and display of complex object structures.

For finer control of information transmission on the data chain, AMIS provides the following mechanisms:

1. The canAccessSuperData property can be used to prevent accessing data from parent domains.

2. Resetting a data value to __undefined in data overrides the parent domain’s value.
   
   ```javascript
   {
    "type": "dialog",
    "title": "Add",
    "data": {
         "status": "__undefined"
    },
    ...
   }
   ```

## IV. Forms: Validation and Interactions

A form field model adds extra functionality on top of business field values, such as:

1. Field label (label)
2. Whether the field is valid (validated) and whether it is being validated (validating)
3. Whether the field is required (required)
4. Whether the field is visible (visible)
5. Whether the input control has lost focus (focus/blur)
6. Whether the input is disabled (disabled)
7. Error message on validation failure (error)
8. Whether the field value has been modified and what the previous value was
9. Auxiliary prompts such as remark (hint text) and desc (item description)

AMIS is similar to other front-end frameworks: it uses a FormItem higher-order wrapper component to enhance ordinary controls.

```javascript
class MyControl extends React.Component {
  render() {
    const {value, onChange} = this.props;

    // Access all data in the current domain via this.props.data
    // Update other fields by calling this.props.onBulkChange({a: 1, b: 2})
    return (
      <div>
        <p>This is a custom component</p>
        <p>Current value: {value}</p>
        <a
          className="btn btn-default"
          onClick={() => onChange(Math.round(Math.random() * 10000))}
        >
          Modify randomly
        </a>
      </div>
    );
  }
}

FormItem({
  type: 'my-control',
})(MyControl)
```

After wrapping with FormItem and registering in the Renderer factory, the control can be used in forms via type:"my-control" and will obtain additional display supports like label and remark.

Leveraging AMIS’s robust data-chain management and Api abstraction, form validation and interactions are very straightforward and simple. For asynchronous validation, just configure the control’s validateApi property, and control the validation trigger conditions via api’s sendOn.

```javascript
{
    "label": "email",
    "type": "input-text",
     "name": "email",
     "validateApi": "/amis/api/mock2/form/formitemSuccess",
     "required": true
}
```

AMIS has a built-in naming convention: all properties matching the xxxOn pattern, like visibleOn/requiredOn, are treated as expressions whose results are passed as the final visible/required etc. properties to the component.

Thanks to reactive data-binding expressions, complex in-form interactions can be achieved without writing event listeners. For example:

```javascript
      {
        "name": "idIsNumber",
        "type": "switch",
        "label": "id is numeric type"
      },
      {
        "name": "id",
        "type": "input-text",
        "label": "id",
        "visibleOn": "${!idIsNumber}",
        "validations": {
          "isEmail": true
        }
      },
      {
        "name": "id",
        "type": "input-number",
        "label": "id",
        "visibleOn": "${idIsNumber}",
        "validations": {
          "isNumeric": true
        }
      }
```

We can place multiple controls with the same name in a form and then use visibleOn expressions for conditional switching, thereby achieving different validation rules under different conditions. In a data-driven front-end model, data is no longer subordinate to specific controls but managed by a store independent of UI controls, so multiple controls can share the same data.

> AMIS also recognizes other suffix conventions. For example, valueExpr indicates the field is an expression, effectively mapping to value. However, the xxExpr suffix appears to have been deprecated and is not covered in the AMIS docs. The current approach tends to recognize expressions based on their prefix/syntax, e.g., value: "${a.b.c}" is automatically recognized as an expression. In the Nop platform we consistently apply the [prefix-guided syntax](https://zhuanlan.zhihu.com/p/548314138) design philosophy: by adding specific value prefixes to denote different value types, the object’s overall structure can remain unchanged—value-level changes are confined within the value expression scope and do not escalate to the object level (no need to add new properties). According to this design, AMIS’s visibleOn/disabledOn conventions are redundant and could be replaced by using expression conventions everywhere. But considering compatibility concerns, AMIS likely won’t make this change. Currently, the AMIS framework lacks global, unified processing in many places; expression recognition and handling are often delegated to each control, which may lead to subtle inconsistencies in practice.

AMIS inherits and amplifies the excellent design of the DOM model: all controls have name and id and can be located by name or id. AMIS cleverly introduces a target concept, using name as a descriptive locator, further leveraging name’s value. For example, using target, AMIS enables cross-form interactions.

```javascript
 {
      "title": "Search Criteria",
      "type": "form",
      "target": "my_crud",
      "body": [
        {
          "type": "input-text",
          "name": "keywords",
          "label": "Keywords:"
        }
      ],
      "submitText": "Search"
    },
    {
      "type": "crud",
      "name": "my_crud",
      "api": "/amis/api/mock2/sample",
      ...
    }
}    
```

By setting the form’s target to the name of another control, the form data can be submitted as parameters to the specified control.

Another common use case is to trigger the reload method on the target control and optionally pass specific parameters:

```javascript
  {
          "type": "action",
          "actionType": "reload",
          "label": "Send to form2",
          "target": "form2?name=${name}&email=${email}"
 }
```

Refresh multiple target controls at once:

```javascript
{
  "type": "action",
  "actionType": "reload",
  "label": "Refresh target components",
  "target": "target1,target2"
}
```

AMIS standardizes the reload semantics for components, so Formula, Service, Select, and other components support reload; they perform default refresh behavior (e.g., Service re-executes the Api call; Select reloads its source options list).

A natural extension is to support invoking arbitrary methods on the target component, not just the default reload. Since version 1.7.0, AMIS supports a more flexible component-method invocation mechanism:

```javascript
// Set form values
{
    "actionType": "setValue",
    "componentId": "myForm",
    "args": {
       "value": "${globalData}"
    }
 },
// Switch tab option
{
     "actionType": "changeActiveKey",
     "componentId": "tabs-change-receiver",
     "args": {
        "activeKey": 2
      }
}
```

If we design a locating mechanism, the simplest requirements should be threefold:

1. Absolute positioning
2. Relative positioning
3. Composite relative positioning

In AMIS, id is the absolute locator; getComponentById(id) always finds the root node first and then searches downward layer by layer. name is the relative locator; getComponentByName(name) finds the component with the specified name in the current scope. When not found, AMIS calls parent.getComponentByName(name) to continue the search, so nodes in other branches may be found. getComponentByName supports composite name properties; a.b.c means finding the component by name=a, then a child by name=b, and so on.

In AMIS’s latest action-trigger design, componentId only supports absolute id definitions, not relative positioning by name, which is a regression in design. Without relative positioning, combining multiple pages easily leads to conflicts. If we use meaningless UUIDs, it becomes difficult to support hand-written authoring, degrading the DSL to a mere accessory of visual editors.

## V. Action: Triggering and Orchestration

[Action buttons](https://aisuda.bce.baidu.com/amis/zh-CN/components/action) are the most common way to trigger page behaviors. The Action component in AMIS cooperates with container components and encodes abundant knowledge related to front-end page models, effectively reducing configuration workload in typical application scenarios. First, the Action component has a built-in workflow:

1. Prompt confirmText to ask whether to proceed
2. If inside a form, check whether fields specified by required are valid
3. Check whether batch-operation conditions requiredSelected are satisfied
4. Execute the operation and start a countDown to disable the button
5. If messages are configured, display corresponding prompts via toast on success or failure
6. On success, if a feedback window is configured, pop it up to show returned results
7. On success, if redirect is configured, navigate to the specified page
8. On success, refresh specified controls according to reload configuration
9. If close is configured and the button is inside a dialog, try to close the dialog

Action triggers use event bubbling-like handling: when a component’s handleAction does not handle the action, it calls the parent component’s onAction to handle it. AMIS’s form and dialog controls recognize standard actions such as reset/reload/submit/clear/confirm/cancel/close; when events propagate to these containers, default behaviors execute automatically. In the example below, clicking Button A automatically closes the dialog containing the current page; clicking Button B resets the form found according to default locating rules on the current page:

```javascript
{
    "type":"action",
    "label":"Button A",
    "actionType": "close"
},
{
    "type":"action",
    "label":"Button B",
    "actionType": "reset"
}
```

Action can also trigger popping up a dialog; the basic structure is:

```javascript
{
    "type":"action",
    "actionType":"dialog",
    "dialog": {
        "data": {
          // Pass initialization data to the form via the data section.
          // By default the dialog inherits the original page’s data chain,
          // and can directly access data on the original page.
        },
        "body":{
          // Specific content inside the dialog. From within the dialog you
          // can still access components on the original page via component id, etc.
        }
    }
}
```

For example:

```javascript
{
  "type": "page",
  "body": {
    "type": "form",
    "body": [
      {
        "type": "input-text",
        "label": "data",
        "name": "myData",
        "id": "myData",
        "addOn": {
          "type": "action",
          "label": "Set",
          "actionType": "dialog",
          "dialog": {
            "actions": [],
            "data": {
              "myValue": "${myData}"
            },
            "body": {
              "type": "form",
              "body": [
                {
                  "type": "input-text",
                  "label": "myValue",
                  "name": "myValue"
                },
                {
                  "label": "Modify",
                  "type": "action",
                  "close": true,
                  "onEvent": {
                    "click": {
                      "actions": [
                        {
                          "componentId": "myData",
                          "actionType": "setValue",
                          "args": {
                            "value": "${myValue}"
                          }
                        }
                      ]
                    }
                  }
                }
              ]
            }
          }
        }
      }
    ]
  }
}
```

The example above demonstrates adding an auxiliary [Set] button next to an input field. Clicking it pops up a form; after entering information, clicking the form’s button copies the information to the input below and automatically closes the dialog.

No matter how complex a built-in action handling flow is, it will never fully cover all business needs. Since version 1.7.0, AMIS has added an [event action](https://aisuda.bce.baidu.com/amis/zh-CN/docs/concepts/event-action) mechanism, allowing multiple custom actions to be executed during a single event response. These actions can be parallelized, sequenced, executed asynchronously, perform loops and branch selection, and have dependencies, essentially forming a small logic-flow orchestration system.

The basic structure for event actions is:

```javascript
 "onEvent": {
    "click": { // Listen to the event
      "actions": [ // List of actions to execute
        {
          "actionType": "toast", // Execute a toast prompt
          "args": { // Action arguments
            "msgType": "info",
            "msg": "${__rendererData|json}"
          },
           "expression": "expression === \"okk\"" // Only execute when the condition is satisfied
           "stopPropagation":false, // Whether to prevent executing the next action
           "preventDefault":false, // Whether to prevent the control’s default event handler
           "outputVar": "" // If the action has a return result, specify the output variable name
        },
        // Subsequent actions
      ]
    },
```

When executing an action, you can use ${event.data} to obtain the event object’s data, and ${__rendererData} to obtain the component’s current data domain.

AMIS provides many flow-control directives, such as actionType=loop/break/continue/switch/parallel. Subsequent actions automatically wait for the previous action to finish before executing. After an http request action completes, subsequent actions can obtain the response via ${responseResult} or ${{outputVar}}.

## VI. Extensions in the Nop Platform

The Nop platform is a next-generation, DSL-oriented low-code development platform built from scratch based on the principles of Reversible Computation. Its front end can use any rendering layer based on JSON or XML. Previously, I investigated overseas [Appsmith](https://www.appsmith.com/) and Alibaba’s [LowCodeEngine](https://lowcode-engine.cn/index), but ultimately chose AMIS for the example because integrating other technologies requires more work and imposes more constraints.

## 6.1 Automatic Conversion Between XML and JSON

When hand-writing and reading, XML has certain advantages over JSON, especially when integrating external template engines for dynamic generation. The Nop platform adds XML syntax expressions for AMIS, enabling bidirectional conversion between XML and JSON based on a few simple rules:

1. The type property corresponds to the tag name.
2. Simple-typed properties correspond to XML attributes.
3. Complex-typed properties correspond to XML child nodes.
4. For list types, mark j:list=true on the node.
5. The body property is specially recognized and does not require explicit j:list.

For example:

```json
{
        "type": "operation",
        "label": "Operations",
        "buttons": [
          {
            "label": "Details",
            "type": "button",
            "level": "link",
            "actionType": "dialog",
            "dialog": {
              "title": "View Details",
              "body": {
                "type": "form",
                "body": [
                  {
                    "type": "input-text",
                    "name": "browser",
                    "label": "Browser"
                  },
                  {
                    "type": "control",
                    "label": "grade",
                    "body": {
                      "type": "tag",
                      "label": "${grade}",
                      "displayMode": "normal",
                      "color": "active"
                    }
                  }
                ]
              }
            }
          }
```

Corresponding XML format:

```xml
<operation label="Operations">
   <buttons j:list="true">
     <button label="Details" level="link" actionType="dialog">
        <dialog titl="View Details">
            <body>
              <input-text name="browser" label="Browser" />
              <control label="grade">
                <body>
                  <tag label="${grade}" displayMode="normal" color="active" />
                </body>
              </control>
            </body>
        </dialog>
     </button>
   </buttons>
</operation>
```

The XPL template language in the Nop platform offers many simplifications for dynamically generating XML, e.g.:

```xml
<button xpl:if="xxx" label="${'$'}{grade}" icon="${icon}">
</button>
```

At template runtime, xpl:if is a conditional expression; the node is generated only when the expression returns true. When generating all XML attributes, if the attribute value is null, it is automatically ignored and will not appear in the final output. With this null-attribute filtering mechanism, we can easily control which attributes are generated.

AMIS’s syntax design is relatively regular; when converted to XML, it closely resembles ordinary HTML or Vue templates. In contrast, the DSL of LowCodeEngine feels more like a serialization protocol for domain objects than a DSL that is easy to hand-write and read.

> Early versions of AMIS’s DSL contained many inconsistencies; for example, content areas of container controls were sometimes called children, sometimes controls, and sometimes content. Only recently, after refactoring, were they generally standardized to body.

## 6.2 Reversible Computation Decomposition

Based on Reversible Computation theory, the Nop platform implements a general decomposition/merge mechanism for JSON and XML, enabling large JSON files to be split into multiple smaller files according to general rules—effectively adding a kind of module organization syntax to AMIS. The most commonly used syntaxes are x:extends to inherit from an external file and x:gen-extends to dynamically generate JSON objects that can be inherited.

```yaml
x:gen-extends: |
  <web:GenPage view="NopAuthDept.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />

body:
   name: crud-grid
   bulk-actions:
       - type: action
         id: test-button
         label: 'Test'
         actionType: dialog
         dialog:
            "x:extends": test.page.yaml
            "title": "Test Dialog"
```

In the above example, we first generate a CRUD page dynamically according to the configuration in NopAuthDept.view.xml, then add a Test button to the bulk-action area. Clicking this button pops up a dialog whose implementation reuses the existing test.page.yaml file. The title property overrides what is inherited via x:extends, setting the dialog title to Test Dialog.

x:extends is essentially a general operator that performs inheritance-like operations on a tree structure, akin to object-oriented inheritance.

For any external JSON file, you just need to change the loader for a normal JSON file to the ResourceLoader provided by the Nop platform to automatically obtain the decomposition/merge operations defined by Reversible Computation. It also supports compile-time metaprogramming, allowing a series of complex structural transformations at compile time.

For details, see

    [Design of Low-Code Platforms from the Perspective of Tensor Products](https://zhuanlan.zhihu.com/p/531474176)

## 6.4 Action Modularization

AMIS’s DSL itself only supports embedding JS snippet code within the page and does not directly support importing external JS functions. The Nop platform introduces an xui:import property to AMIS, allowing external JS libraries to be imported and functions within them to be used as event handlers.

> This mechanism is general and can be used to integrate other low-code engines.

```
type: page
xui:import: /nop/auth/pages/DemoPage/demo.lib.js
body:
  type: form
  api:
    url: "@action:demo.testAction"
    data:
      a: 1
```

The example above shows importing a demo.lib.js library and then referencing functions within it via demo.testAction.

The syntax url: "@action:demo.testAction" is an action-trigger mechanism we provide on top of AMIS’s environment abstraction. It intercepts AMIS’s fetcher calls, recognizes the @action: prefix, maps it to already loaded JS functions, and passes in the parameters specified by data when calling.

The script library code is stored in demo.lib.xjs (note the suffix is xjs rather than js; we use the graalvm-js script engine to invoke rollup to convert xjs to js and package it as a SystemJs module structure).

```javascript
/* @x:gen-extends:
  <!--XPL template language can be used here to generate JS code -->
 */
import { ajaxFetch} from '@nop/utils'

import {myAction} from './sub.lib.js'

import {myAction2} from './parts/sub2.lib.js'

import {ajaxRequest} from '@nop/utils'

export function testAction(options, page){
    page.env.alert("xx");
    ajaxFetch(options)
    ajaxRequest(options)
    myAction(options,page)
    myAction2(options,page)

    return Promise.resolve({
        status: 200 ,
        data: {
            status: 0
        }
    })
}
```

xjs files can be authored in ordinary ESM module format. By adding @x:gen-extends in comments, we endow it with compile-time dynamic generation capabilities (used in dynamic generation of workflow editors).

export’d functions are interfaces exposed for external calls. import calls are transformed into SystemJs dependencies. There is a special treatment for files under /parts/: we invoke rollup to bundle their code together with the main file’s code—i.e., files under parts are considered internal implementation and will not be exposed as externally accessible JS libraries.
See the bundled result in [demo.lib.js](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-auth/nop-auth-app/_dump/nop-app/nop/auth/pages/DemoPage/demo.lib.js)

Besides action invocation, external library functions can be used anywhere JS scripts can be embedded. To this end, we provide another prefix @fn:, which requires explicit function arguments (action’s function arguments are already standardized as options, page).

```javascript
"onClick":"@fn:demo.myListener(event,props)"
```

Reconsidering the onClick call process, we find that the process of resolving a function implementation by name is very similar to the event bubbling process in DOM components. During event bubbling, the event name is passed upward; once a handler is found at some level, it processes the event. AMIS’s action-response handling checks each component’s handleAction for the specified actionType; if not handled, it calls the parent component’s onAction.

If we directly treat the event name being passed upward as the function name, then event bubbling can be viewed as resolving a function name in a lexical scope. xui:import at different levels is akin to creating distinct lexical scopes; we always look up the function in the nearest scope first, and if not found, continue upward in the parent scope.

Object-oriented technology has long dominated GUI development. Its core essence is the organizational relationship among ComponentTree + StateTree + ActionTree. Components form the component tree—usually a static structure that corresponds one-to-one with source code. Once constructed, it can stably exist in memory and be reused multiple times; events and data flow through this tree. Business state information also forms a state tree, from which components can pull data. The component tree and state tree are not necessarily layer-by-layer aligned, but parent-child relationships remain stable. When an event occurs, it bubbles up along the ActionTree and is handled by some level’s handler. In theory, the Tree structure of ActionTree need not coincide with the ComponentTree; but for ease and stability of reasoning, we generally prefer lexical scopes determined at compile time rather than dynamic scopes affected by runtime state. Thus, actions are associated with a certain level of components, and the parent-child order remains consistent with the component tree (though not necessarily layer-by-layer aligned).

## 6.5 GraphQL Simplification

GraphQL always requires specifying the list of return fields, but for a low-code platform, what fields a form contains can be inferred from the model. Therefore, we can automatically infer the needed fields from the form model instead of manually specifying them.

The Nop platform adds an extension for AMIS so that we can construct GraphQL requests via the following syntax:

```javascript
url: "@graphql:NopAuthUser__get/{@formSelection}?id=$id"
```

For a detailed introduction, see my previous article: [GraphQL Engine in a Low-Code Platform](https://zhuanlan.zhihu.com/p/589565334)

## 6.6 Multilingual Internationalization

AMIS’s JSON format can be easily read and processed. Therefore, many structural transformations can be handled entirely by the backend, independent of the AMIS framework. The Nop platform provides a unified i18n string-replacement mechanism for JSON, stipulating two ways:

1. Recognize and replace all values with the @i18n: prefix.
2. For each key requiring internationalization, add a corresponding @i18n:key property. For example:
   
   ```javascript
   {
   label: "@i18n:common.batchDelete|Batch Delete"
   }
   or
   {
   label: "Batch Delete"
   "@i18n:label" : "common.batchDelete"
   }
   ```

## 6.7 Access Control

The Nop platform defines permission-related properties such as xui:roles and xui:permissions. After receiving JSON-formatted page data, it automatically validates whether the permission attributes are satisfied and removes all nodes that do not meet permission requirements. This processing is performed on the JSON structure and does not involve any front-end framework-specific knowledge.

## 6.7 Vue Component Integration

AMIS is built using React, while the Nop platform’s front end is primarily developed with Vue 3.0. To facilitate the integration of third-party Vue components, the Nop platform provides a generic wrapper component. In AMIS’s configuration file, we can use it like this:

```javascript
{
  "type": "vue-form-item",
  "vueComponent": "VueComponentName",
  "props": {
    // Props passed to the Vue component
  }
}
```

## Summary

Baidu’s AMIS framework is a finely designed, low-integration-cost front-end low-code framework. The Nop platform makes various improvements and extensions on top of AMIS, providing solutions to some common problems. The Nop platform’s wrapper code for AMIS has been uploaded to gitee:
[nop-chaos](https://gitee.com/canonical-entropy/nop-chaos.git)
For those interested in integrating AMIS, please refer to it.

Open-source addresses for the Nop platform:

- gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Development examples: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Principles of Reversible Computation and Introduction & Q&A on the Nop Platform — bilibili](https://www.bilibili.com/video/BV1u84y1w7kX/)

<!-- SOURCE_MD5:e1fe88414a95f5a9863bbd45eb398c4d-->
