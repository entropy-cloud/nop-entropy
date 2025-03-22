# Why the Baidu AMIS Framework is an Excellent Design

[AMIS](https://aisuda.bce.baidu.com/amis/zh-CN/docs/index) is an open-source front-end low-code framework developed by Baidu. To be fair, it is one of the most refined low-code frameworks in the current front-end open-source community and represents the highest level of domestic front-end open-source low-code development. AMIS employs a JSON-based syntax format and incorporates numerous ready-to-use mature components, making it highly extensible and integrable. From a practical usage perspective, AMIS provides relatively comprehensive online documentation and supports online testing. In my observations, typical backend developers can generally learn the basics of AMIS within a short timeframe by means of self-exploration and rapidly construct complex增删改查 pages.

The built-in components in AMIS are primarily those commonly used in backend management software, yet its design inherently embodies a universal approach that is not confined to its current supported use cases. In this text, I will analyze certain design aspects of the AMIS framework and introduce improvements made when integrating AMIS into the Nop platform.

> While the overall quality of AMIS's code implementation is not particularly high, especially considering its lengthy development history, which has led to a significant amount of redundant code within its internal implementations, there remains substantial room for improvement. In recent years, Baidu has invested resources into AMIS and performed extensive code refactorings, with ongoing improvements still being made. This text primarily focuses on conceptual design-level analysis and does not encompass specific implementation-level code.

## I. Env: Environment Abstraction

The most significant distinction of AMIS compared to other open-source low-code frameworks is its explicit definition of [Environment Abstraction Env](https://aisuda.bce.baidu.com/amis/zh-CN/docs/start/getting-started#%E6%8E%A7%E5%88%B6-amis-%E7%9A%84%E8%A1%8C%E4%B8%BA), which encompasses all execution logic related to input, output, and page transitions. This design significantly reduces the integration difficulty of the AMIS framework, enabling it to be easily integrated into other underlying UI frameworks. For instance, in the Nop platform, we employ Vue 3.0 for the main UI framework with Vue Router implementing single-page transitions. In specific pages or embedded components, however, we can utilize React-based AMIS to achieve the desired functionality.

```javascript
 const env = {
      // Execute AJAX requests
     fetcher(options) { ... },
     // Implement single-page switching via Router
     updateLocation(location, replace){ ... },
      // Open a new browser window
     jumpTo(to, action){ ... },
      // Similar to window.alert, display alert messages
     alert,
      // Similar to window.confirm, display confirmation messages
     confirm,
     // Display notification messages and error prompts
     notify(type, msg)
 }
 // Render the page based on JSON configuration
 renderAmis(
      // Page schema description in JSON format
      jsonPage,
      // Additional data to be passed
      {
        data:
          myVar: 123,
      },
      // Pass the environment object
      env
    );
```

If we treat AMIS's JSON page definition as a domain-specific language (DSL), then **the AMIS framework can be viewed as a virtual machine responsible for executing this DSL**. When AMIS needs to execute AJAX calls or display alerts, pop-ups, or navigate pages, it invokes the corresponding methods from the `env` object. Thus, **Env can be regarded as a virtualized abstraction interface that specifically handles input and output operations**.

All input and output actions are virtualized after this process, leading to the equation `output = AmisPage(Input)`. Therefore, AMIS pages can be viewed as localized processing functions within a confined scope, making them easily integrable into external business workflows.

## II. Api: Value and Function as a Pair

For encapsulating remote service calls, the AMIS framework provides a descriptive definition method, known as the [Api object](https://aisuda.bce.baidu.com/amis/zh-CN/docs/types/api). This approach allows for a flexible yet precise declaration of API endpoints and their associated operations.
```typescript
interface ApiObject {
  /**
   * API request type
   */
  method?: 'get' | 'post' | 'put' | 'delete' | 'patch' | 'jsonp';

  /**
   * API target URL
   */
  url: SchemaUrlPath;

  /**
   * Used to control data carrying. When the key is `&` and the value is `$$`, it will flatten all original data into the `data` property. When the value starts with `$`, variable values are assigned to the corresponding key.
   */
  data?: {
    [propName: string]: any;
  };

  /**
   * Data format of the request body
   */
  dataType?: 'json' | 'form-data' | 'form';

  /**
   * For file download interfaces, configure this if necessary
   */
  responseType?: 'blob';

  /**
   * Headers that can be used similarly to data, supporting variable values
   */
  headers?: {
    [propName: string]: string | number;
  };

  /**
   * Conditions for sending the request
   */
  sendOn?: SchemaExpression;

  /**
   * Default is append mode; set to true if you want to completely replace
   */
  replaceData?: boolean;

  /**
   * Whether to automatically refresh when URL values change, refreshing data automatically
   */
  autoRefresh?: boolean;

  /**
   * When auto-refreshing, the default behavior is to track variable changes in the URL. If you want to monitor variables outside the URL, configure `traceExpression`
   */
  trackExpression?: string;

  /**
   * If set, the same parameter in a request within the specified time (in ms) will use cached data
   */
  cache?: number;

  // Data transformation function for request data before submission
  requestAdaptor?: (api: ApiObject) => ApiObject;

  // Response data transformation function after fetching
  adaptor?: (payload: object, response: fetcherResult, api: ApiObject) => any;

  /**
   * Do not display error messages on failure
   */
  silent?: boolean

  /**
   * On successful or failed remote calls, find the corresponding message object via `toast` method for prompting
   */
  messages?: { [name: string]: string }
}
```

ApiObject is a reactive data structure that automatically triggers remote calls when its parameters change and can transform and cache the returned results.

Traditionally, front-end development has been function-oriented, where event triggers execute specific functions. For example:

```javascript
<select id="a" onchange="handleChange">
</select>
<select id="b">
</selection>

<script>
function handleChange(value) {
    fetchRelatedOptions(value).then(result => {
        getComponent('b').setOptions(result)
    })
}
</script>
```

In modern frameworks like AMIS, we typically use a **data-oriented approach**. Instead of viewing functions as actions triggered by events, we see them as producers of result data that dynamically updates based on conditions. For example, in AMIS, implementing two dropdown lists with interdependence:
```json
{
    "type": "form",
    "body": [
        {
            "type": "select",
            "name": "a",
            "options": [ ... ]
        },
        {
            "type": "select",
            "name": "b",
            "source": {
                "method": "post",
                "url": "/get-related-options",
                "data": {
                    "value": "${a}"
                }
            }
        }
    ]
}
```

The first dropdown select control has the name attribute set to "a", which means its selected value corresponds to a variable named "a" in the context. This dropdown can be seen as both an observer and a modifier of this variable, allowing it to view and modify the value of "a". The second select control's source property is linked to an API type object that uses a data binding expression to observe changes in the "a" variable. When "a" changes, it automatically performs an AJAX call to fetch new dropdown options.

If we **do not only focus on the function calls triggered by single events** but instead emphasize the overall structure of the application's operation, we can see its structure as "...data --> function --> data --> function --> data...". **This information flow network has two dual observation perspectives. One is "function --> data --> function", and the other is "data --> function --> data"**. We can interpret the flow of information as either a function calling another function with parameters or data triggering response functions to generate new data after changes.

> Void functions that have side effects are not truly without return values; they simply modify some implicitly unexpressed state variables.

When comparing Vue 3.0's reactive design, we can find an interesting connection. AMIS's API object is similar to Vue 3.0's ComputedRef type, but instead of triggering a synchronous frontend function, it activates an asynchronous remote service call. To implement a mechanism similar to the API object in Vue 3.0, we could extend Vue with the following:

```javascript
interface Api<T> extends Ref<T> {
  /**
   * Configuration object used when building the Api.
   */
  readonly config: ApiConfig;

  /**
   * Current value, equivalent to vue's ref.value.
   */
  readonly value: T | undefined;

  /**
   * Whether input parameters have changed, triggering a remote loading action. If in active state, it will automatically initiate the loading action.
   * In suspended state, it will record shouldRefresh but not actually perform the request.
   */
  readonly shouldRefresh: boolean;

  /**
   * Whether a remote data loading action is currently in progress.
   */
  readonly loading: boolean;
  readonly loaded: boolean;

  /**
   * Whether the remote load timed out. Setting success to false when it times out.
   */
  readonly timeouted: boolean;

  /**
   * Whether the request was successful. If the request times out, success is set to false.
   */
  readonly success: boolean | undefined;

  /**
   * The exception object if a remote call fails.
   */
  readonly error: any;

  suspend(): void;
  resume(): void;

  /**
   * Cancel the current loading operation.
   */
  cancel(): void;

  /**
   * Reload the value, with an optional immediately flag. If in suspended state, calling reload will also execute.
   */
  reload(immediately?: boolean): Promise<T | undefined>;

  /**
   * Similar to rxjs's interface. Triggers this function whenever the api's value changes.
   */
  subscribe(
    next: (value: T) => void,
    error?: (err: any) => void,
    complete?: () => void
  ): StreamUnsubscribe;

  /**
   * Applies a transformation function to get the result.
   *
   * @param fn
   */
  transform<R>(fn: (v: T | undefined) => R): Ref<R>;

  /**
   * Destroys the object, making its value no longer update or valid.
   */
  destroy(): void;
}

interface ApiConfig {
  ... attributes and methods defined in ApiObject;

  /**
   * Determines how values are merged. If not specified, defaults to replacement; specify 'append' for array merging.
   */
  merger?: (v1: T | undefined, v2: T) => T;

  /**
   * Default value to use if no actual loading is successful.
   */
  defaultValue?: T;

  /**
   * Poll interval in milliseconds when specified.
   */
  pollInterval?: number;

  /**
   * Key used for caching the Api's return results. Can be specified directly or dynamically computed. If not specified, uses JSON.stringify(req) as the key.
   */
  cacheKey?: any | ((req: ApiRequest) => string);

  /**
   * Cache timeout in milliseconds if value is greater than 0.
   */
  cacheTimeout?: number;

  cacheStorage?: string;

  /**
   * If value is greater than 0, requests exceeding this timeout will automatically cancel and throw a timeout error.
   */
  completeTimeout?: number;
  connectTimeout?: number;

  /**
   * For stream data, consider it expired if no data is received for a period.
   */
  idleTimeout?: number;

  /**
   * Callback function invoked when data is successfully retrieved.
   */
  onSuccess?: (value: T) => void;

  /**
   * Callback function invoked if the request fails for any reason, including timeouts or cancellations.
   */
  onFailure?: (err: any) => void;

  /**
   * Invoked during remote data retrieval with progress information.
   */
  onProgress?: (progress: any) => void;
  onStreamComplete?: () => void;

  /**
   * Delay period in milliseconds for debouncing.
   */
  debounce?: number;

  /**
   * Throttling wait time in milliseconds. Unlike debounce, the start time of the next operation is continuously pushed forward.
   */
  throttle?: number;

  /**
   * Whether to treat data as a stream, constantly returning values.
   */
  stream?: boolean;

  /**
   * Whether the Api should automatically trigger loading upon creation. Defaults to false. When set to false, loading only occurs when explicitly accessed.
   */
  eager?: boolean;

  /**
   * If true, the Api will be in suspended state by default unless manually reloaded.
   */
  suspended?: boolean;

  /**
   * Optional error handler for remote calls.
   */
  fallback?: (err: any, api: Api<T>) => any;

  /**
   * Number of retries if loading fails. Defaults to 0.
   */
  retries?: number;
  retryDelay?: number;
  maxRetryDelay?: number;
}

If we do not treat the API as a function invocation mechanism but instead view it as a reactive data stream object (Stream), then certain program controls can be naturally integrated into this concept, such as debounce delay trigger, retry re-attempt, pollInterval timed refresh, and cancel/suspend controls, etc. As an extension mechanism of Ref, in addition to having a value property, the API object can also possess loading、status、error等 attributes, thereby transforming the entire remote call process into a stateful one. In this case, the function of the API object is essentially similar to the [useSWR mechanism](https://juejin.cn/post/6943397563114455048) in React Hooks.

The AMIS API object does not directly provide stream data support, but it offers a dedicated [Service container](https://aisuda.bce.baidu.com/amis/zh-CN/components/service), which can serve a similar purpose.

```javascript
{
    "type": "service",
    "api": "/amis/api/mock2/page/initData",
    "body": {
      "type": "panel",
      "title": "$title",
      "body": "The result returned by the API is a Map, where the value of the date property is ${date}"
    }
  }
```

The Service interface is defined as:

```javascript
interface ServiceSchema {
  /**
   * Specifies a service data pull control.
   */
  type: 'service';

  /**
   * When the page initializes, you can set an API to pull data. The sent data will include current data (containing query parameters), and the retrieved data will be merged into the data for component use.
   */
  api?: SchemaApi;

  /**
   * WebSocket address for real-time data acquisition.
   */
  ws?: string;

  /**
   * Data fetched via external functions.
   */
  dataProvider?: ComposedDataProvider;

  /**
   * Content area.
   */
  body?: SchemaCollection;

  /**
   * Deprecated: Replace with API's sendOn. From this change, it can be seen that the standardization of the Api object in AMIS is also an ongoing process.
   */
  fetchOn?: SchemaExpression;

  /**
   * Whether to pull data by default?
   */
  initFetch?: boolean;

  /**
   * Whether to pull data by default through expression.
   *
   * Deprecated: Replace with API's sendOn.
   */
  initFetchOn?: SchemaExpression;

  /**
   * Remote Schema API configuration.
   */
  schemaApi?: SchemaApi;

  /**
   * Whether to load schemaApi by default.
   */
  initFetchSchema?: boolean;

  /**
   * Configuration via expression. Deprecated: Replace with API's sendOn.
   */
  initFetchSchemaOn?: SchemaExpression;

  /**
   * Whether to perform auto-refresh.
   */
  interval?: number;

  /**
   * Whether to perform silent polling.
   */
  silentPolling?: boolean;

  /**
   * Condition to stop auto-refresh.
   */
  stopAutoRefreshWhen?: SchemaExpression;

  messages?: SchemaMessage;

  name?: SchemaName;
}
// ServiceStore adds fetching, error, etc., state variables.
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

Services support polling for data or receiving stream data via WebSocket.

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

You can set only the ws to receive all data, or you can set both api and ws, allowing api to fetch complete data while ws handles real-time updates.

**The data fetched by Service can be accessed by sibling nodes, so from this perspective, viewing Service as a container concept is a historical legacy. Essentially, it's a more flexible and stream-oriented API object.**

> In addition to dynamically fetching data, Services also have the role of dynamically retrieving page fragment definitions using the returned schema to generate pages. However, based on the analysis in this document, the functionality of fetching data and fetching the schema are fundamentally unrelated, and it's better to separate them.

In addition to the Api object, AMIS provides a lightweight dynamic computation mechanism similar to Vue 3.0's computed property: the formula component.
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

The example demonstrates an interesting application of the `formula` component. While both components have the same name "b," the `formula` component includes a condition that triggers the formula execution when the `radios` variable changes, resetting the value of variable `b` to an empty string. The `formula` component essentially acts as a reset mechanism triggered by changes in the `radios` options.

The `formula` component is limited to embedded expressions and cannot handle more complex function abstractions. Since the API is fundamentally a higher-order data type compared to `computed`, we can, to some extent, use APIs to simulate the behavior of computed properties. For example:

```javascript
{
  url: "@action:myFunc",
  data: {
    a: "${a}" 
  }
}
```

Here, `@action:myFunc` is an extension function calling mechanism based on AMIS' Env abstraction in the Nop platform. The API definition indicates that when variable `a` changes, it will trigger the `myFunc` function in the context environment, allowing front-end logic to be executed without necessarily initiating a remote call.

Starting from version 1.4.0, the Service container can use the `dataProvider` property to set data loading functions, effectively simulating some aspects of Vue's computed properties.

```javascript
{
  "type": "service",
  "data": {
    "x": 123
  },
  "dataProvider": "const timer = setInterval(() => { setData({ data: { x }, date: new Date().toString() }) }, 1000); return () => { clearInterval(timer) }",
  "body": {
    "type": "tpl",
    "tpl": "Current time: ${date},${x}"
  }
}
```

The `dataProvider` function can access data parameters via the `data` attribute and update store data by calling `setData`.

In an ideal responsive frontend framework, we might expect binding component attributes to support static values, dynamic expressions, reactive `ref` references, or state-tracking asynchronous API objects (streaming data). However, as currently implemented in AMIS, this is not achievable. Therefore, the `select` component must use the `options` attribute for static configuration and the `source` attribute for dynamically fetching options lists. This implies that support for asynchronous API calls in component properties must be implemented individually for each control.

## 2. Data Chain: State Trees and Scoping

In Vue 3.0 and earlier, the Vuex framework provided a global single-state tree management system for entire applications.

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

From a modern perspective, Vuex's design appears overly complex. Why must we bind store variables into component properties via computed properties and indirect `$store` access? Why not directly use store variables?

In Vue 3.0, the [Pinia framework](https://pinia.web3doc.top/introduction.html) replaces Vuex, offering a more intuitive approach.

```javascript
const store = useStore();
const { count } = storeToRefs(store)
// Can use store variables directly or destructure refs from store and then use them
return () => <div> {mainStore.count} --- {count} </div>
```
Combining JSX syntax, we can directly access the corresponding state variables using JavaScript variable names. **Variables are looked up in a hierarchical manner according to JavaScript's lexical scoping rules**. If there are multiple variables with the same name, the most recently declared variable is found.

In AMIS, so-called [data chain concept](https://aisuda.bce.baidu.com/amis/zh-CN/docs/concepts/datascope-and-datachain), in essence, refers to a set of lexical scoping contexts managed by the AMIS framework. When setting the `name` attribute for components in AMIS DSL syntax, it indicates that this control should be bound to the corresponding variable in the current data domain (two-way data binding). When using expression syntax, variables within expressions are also resolved in the data domain. Therefore, the so-called data domain can be viewed as the lexical scoping context in DSL syntax. AMIS specifies the following lookup rules:

1. First, it attempts to find the variable in the current component's data domain. If found, rendering is completed using data mapping, and the search process stops.
2. If no variable is found in the current data domain, it searches upwards in the parent component's data domain, repeating steps 1 and 2.
3. This process continues until the top-level node (the `page` node) is reached, at which point the search ends.
4. However, if the URL contains parameters, an additional upward search is performed beyond this level, making it common to directly use `${id}` to access URL parameters in configurations.

Vuex maintains a so-called single state tree, where all application state variables are managed as a single root tree—this concept is inherited from the Redux framework. However, in practical usage, we often fail to effectively utilize the hierarchical structure of the tree. In contrast, AMIS allows only a few container components (excluding the top-level `Page` component and others like CRUD, Dialog, IFrame, Form, Service) to create new data domains. When these container components are nested and combined into a page, their respective Stores are automatically attached to the global StoreTree structure. This naturally forms a StateTree for the entire page. **Using the data chain lookup mechanism**, we can write business logic using only the most relevant local variable names, simplifying the logic expression. For example, in a table control's cell, the data domain first looks within the data row and allows direct access to other columns via variable name.

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

AMIS provides a dedicated [Combo component](https://aisuda.bce.baidu.com/amis/zh-CN/components/form/combo) to fully leverage object data hierarchies and implement complex object structures in editing and display.

To fine-tune information transmission along the data chain, AMIS provides the following mechanisms:

1. The `canAccessSuperData` attribute controls whether parent-level data domains can be accessed.
2. By resetting data values to `__undefined`, we override parent data domain values. For example:
   
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

## Four. Forms: Validation and Interactions

The form field model extends existing business field values by adding:

1. Field label (`label`)
2. Whether the field is valid (`validated`, `validating`)
3. Whether the field is required (`required`)
4. Whether the field is visible (`visible`)
5. Whether focus or blur events occur on input controls (`focus/blur`)
6. Whether input fields are disabled (`disabled`)
7. Error messages displayed upon validation failure
8. Whether the field value has been modified and what the original value was
9. Auxiliary input prompts such as `remark` and `desc` for form item descriptions

Similar to other frontend frameworks, AMIS achieves this by wrapping ordinary controls with a high-order `FormItem` component.
```javascript
class MyControl extends React.Component {
  render() {
    const { value, onChange } = this.props;

    // 可以通过 this.props.data 获取当前数据域中的所有的数据
    // 通过this.props.onBulkChange({a: 1, b: 2}) 来更新其他字段的值
    return (
      <div>
        <p>A custom component</p>
        <p>Current value: {value}</p>
        <a
          className="btn btn-default"
          onClick={() => onChange(Math.round(Math.random() * 10000))}
        >
          Random modify
        </a>
      </div>
    );
  }
}

FormItem({
  type: 'my-control',
})(MyControl)
```

After wrapping MyControl with FormItem and registering it with the Renderer factory, this custom control can be used in forms through `type:"my-control"` to access label, remark, etc. additional display features.

With AMIS's powerful data chain management and Api object abstraction, form validation and interactivity are highly intuitive and straightforward. For example, for asynchronous validation, simply configure the validateApi property of the control. Through api.sendOn configuration, you can further control the conditions under which validation is initiated.

AMIS has an attribute naming convention: all attributes following the xxxOn pattern (such as visibleOn/requiredOn) will be treated as expressions and executed as such. The result of these expressions is passed to the component as the final visible/required property value.

Using responsive data binding expressions, you can achieve complex form control interactivity without writing event listener functions. For example:

```javascript
{
    "name": "idIsNumber",
    "type": "switch",
    "label": "ID 是数字类型"
},
{
    "name": "id",
    "type": "input-text",
    "label": "ID",
    "visibleOn": "${!idIsNumber}",
    "validations": {
        "isEmail": true
    }
},
{
    "name": "id",
    "type": "input-number",
    "label": "ID",
    "visibleOn": "${idIsNumber}",
    "validations": {
        "isNumeric": true
    }
}
```

You can place multiple controls with the same name in a form and use visibleOn expressions to switch their visibility, thereby implementing different validation rules under different conditions. **In a data-driven frontend model, data is managed by stores independent of UI controls, allowing multiple controls to share the same data**.

AMIS internally recognizes additional suffix conventions, such as `valueExpr`, which indicates that the field is an expression type corresponding to the `value` attribute, etc. However, the `xxExpr` suffix's formatting method has likely been deprecated, and there is no related documentation in the AMIS manual. At present, it seems that AMIS prefers to determine whether a syntax is an expression by checking for the presence of prefix or suffix, such as `value: "${a.b.c}"`, which will be automatically identified as an expression. In the Nop platform, we consistently adhere to [prefix-guided syntax](https://zhuanlan.zhihu.com/p/548314138) design philosophy by adding specific value prefixes to identify different value types while maintaining the overall object structure intact. This allows us to limit changes in value levels to the value's expression range without affecting the object level (no new attributes are needed). Following this design approach, AMIS conventions like `visibleOn`/`disabledOn` are redundant and can be entirely replaced with expression conventions. However, due to compatibility considerations, AMIS is unlikely to make this modification in the future. The AMIS framework currently lacks a unified global handling mechanism in many areas, with expression recognition and processing typically delegated to individual controls. This may lead to subtle inconsistencies in actual usage.

**AMIS inherits and enhances the excellent design of the DOM model: all controls have `name` and `id`, enabling location via `name` or `id`.** AMIS cleverly introduces the concept of `target`, using `name` directly as a descriptive positioning coordinate, thereby maximizing the value of `name`. For example, through `target`, AMIS can implement cross-form interoperations.

```javascript
{
  "title": "Query Conditions",
  "type": "form",
  "target": "my_crud",
  "body": [
    {
      "type": "input-text",
      "name": "keywords",
      "label": "Keyword: "
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
```

By setting the `target` of a form to another control's name, the form's data can be submitted as parameters to the specified control.

Another common usage is to trigger the `reload` method on the `target` control, allowing specific parameters to be passed:

```javascript
{
  "type": "action",
  "actionType": "reload",
  "label": "Send to form2",
  "target": "form2?name=${name}&email=${email}"
}
```

Multiple targets can be refreshed in a single action:

```javascript
{
  "type": "action",
  "actionType": "reload",
  "label": "Refresh target components",
  "target": "target1,target2"
}
```

AMIS standardizes the `reload` semantics for components, so `Formula`, `Service`, and `Select` components all support this operation. For example:
- Service components will re-execute API calls.
- Select components will reload their source options.

A natural extension is to support calling any method on the target component, not just the default `reload`. Starting from version 1.7.0, AMIS supports a more flexible method invocation mechanism:

```javascript
// Set form values
{
  "actionType": "setValue",
  "componentId": "myForm",
  "args": {
    "value": "${globalData}"
  }
},
// Switch tab options
{
  "actionType": "changeActiveKey",
  "componentId": "tabs-change-receiver",
  "args": {
    "activeKey": 2
  }
}
```

If designing a positioning mechanism, the simplest requirements should be:
1. Absolute positioning
2. Relative positioning
3. Combined relative positioning

  In AMIS, the id is used for absolute positioning. The getComponentById(id) method always starts by locating the root node and then searches recursively within it. The name is used for relative positioning, and getComponentByName(name) searches for a component with the specified name within the current scope. **If no match is found, AMIS will call parent.getComponentByName(name) to continue searching in other branches, potentially finding nodes in different branches**. The getComponentByName method supports compound names, such as a.b.c, where it first finds the component by name 'a' and then searches for 'b' within that component's children, continuing recursively.

In the latest design of action triggering in AMIS, componentId only supports absolute definitions using id and does not support relative positioning via name. This appears to be a regression in design. Without relative positioning, it becomes easy to encounter conflicts when combining multiple pages. Using meaningless UUIDs would make manual coding infeasible and could lead to the DSL reverting to being an appendage of the visualization editor.

## 5. Action: Triggering and Configuration of Actions

[Action Button](https://aisuda.bce.baidu.com/amis/zh-CN/components/action) is the most common way to trigger page actions in AMIS. The Action component works alongside external container components, embedding a wealth of frontend page model-related knowledge, significantly reducing the configuration workload for typical application scenarios.

First, the Action component incorporates a standard workflow for handling tasks:

1. It prompts with confirmText to ask whether the action should be executed.
2. If used within a form, it checks if required fields have passed validation based on the required configuration.
3. It verifies whether bulk operation conditions specified by requiredSelected are met.
4. The operation is initiated, starting a countdown timer to prevent reverse counting.
5. If messages are configured, success or failure notifications are displayed using toast prompts.
6. Upon successful execution, if feedback windows are configured, a feedback window is displayed with the result.
7. If redirect is configured, the user is navigated to a specified page.
8. After successful execution, depending on reload configuration, a specific control may be refreshed.
9. If close is configured and the button is part of a dialog, it attempts to close the dialog.

Actions use an event-bubbling approach: **if the component's handleAction function does not handle the action, it calls the parent component's onAction function instead**. AMIS form controls and dialog controls recognize standard actions such as reset/reload/submit/clear/confirm/cancel/close, so when the event reaches these container components, they execute default behaviors. In the following example, clicking Button A automatically closes the current page's dialog, while clicking Button B resets the form according to the default lookup rules.

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

Actions can also be used to trigger dialog pops. The basic call structure is:

```javascript
{
    "type":"action",
    "actionType":"dialog",
    "dialog": {
        "data": {
          // Initialization data is passed to the form via the data field, and in cases of missing data, the dialog inherits the original page's data chain,
          // allowing direct access to the original page's components
        },
        "body":{
          // The specific content of the dialog. Within the dialog, components can still be accessed using IDs, etc., as in the original page
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

The above example demonstrates how to add a "[Set]" button next to an input box. When clicked, it opens a form where information can be entered, and after the user clicks the form's button, the entered information is copied to the input box below and the dialog is automatically closed.

Regardless of how complex the built-in action handling flows are within a framework, they fall short when dealing with unpredictable business requirements. AMIS introduced an **[event action](https://aisuda.bce.baidu.com/amis/zh-CN/docs/concepts/event-action) mechanism** in version 1.7.0 and later, allowing multiple custom actions to be executed during a single event response. These actions can be executed **in parallel, sequentially, or asynchronously**, and can include loops, conditional selections, and have dependencies between them, effectively implementing a small-scale logic flow configuration system.

The basic structure for calling event actions is as follows:
```javascript
"eventListener": {
    "click": { // Click event listener
      "actions": [ // List of actions to perform
        {
          "actionType": "toast", // Perform toast notification action
          "args": { // Action arguments
            "msgType": "info",
            "msg": "${__rendererData|json}"
          },
          "expression": "expression === 'okk'", // Execute only if condition is met
          "stopPropagation": false, // Whether to prevent executing the next action
          "preventDefault": false, // Whether to prevent the default event handling function from being called
          "outputVar": "" // Variable name for output results of this action, if applicable
        },
        // Additional actions will follow here...
      ]
    },
```

When performing actions, you can access event data using `${event.data}` and component data domain using `${__rendererData}`.

AMIS provides multiple flow control directives such as "loop", "break", "continue", "switch", and "parallel". Subsequent actions will automatically wait for the previous action to complete before executing. After an HTTP request action completes, subsequent actions can access the response result using `${responseResult}` or `${{outputVar}}`.

## Six. Extensions in the Nop Platform

The Nop platform is built from scratch based on reversible computation principles and is designed for Domain-Specific Language (DSL) development as a new generation of low-code development platforms. Its frontend can use any rendering layer that supports JSON or XML formats. Previously, I examined foreign technologies like Appsmith (https://www.appsmith.com/) and Alibaba's LowCodeEngine (https://lowcode-engine.cn/index/), but ultimately chose AMIS as the example because integrating other technologies requires more work and imposes more restrictions.

## 6.1 Automatic Conversion Between XML and JSON

When manually writing and reading data, XML has certain advantages over JSON, particularly when using external template engines for dynamic generation. The Nop platform adds XML syntax support to AMIS, enabling bidirectional conversion between XML and JSON through simple rules. The specific rules are as follows:

1. The "type" attribute corresponds to the tag name.
2. Attributes of simple types correspond to XML attribute names.
3. Attributes of complex types correspond to XML child nodes.
4. If it's a list type, mark the node with `j:list=true`.
5. The "body" attribute is specially identified and does not require explicit marking as `j:list`.

For example:
```json
{
    "type": "operation",
    "label": "Operation",
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

Its corresponding XML format is:

```xml
<operation label="Operation">
    <buttons j:list="true">
        <button label="Details" level="link" actionType="dialog">
            <dialog title="View Details">
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

The Nop platform's XPL template language simplifies dynamic XML generation in several ways, such as:

```xml
<button xpl:if="xxx" label="${'$'}{grade}" icon="${icon}">
</button>
```

Here, `xpl:if` indicates a conditional expression, and only if the expression returns true will the node be generated. Additionally, any attribute with a null value will be automatically ignored during generation. This null attribute filtering mechanism allows for easy control over which attributes are generated.

The syntax design of AMIS is relatively consistent and closely resembles standard HTML or Vue templates when converted to XML. In contrast, the Low-Code Engine's DSL is more akin to a serialization protocol for domain objects rather than a language designed for manual writing and reading.

> In early versions of AMIS, there were numerous inconsistencies in its DSL design, such as inconsistencies in how container controls handle their content—sometimes referred to as `children`, other times as `controls`, and sometimes as `content`. This was later standardized with the `body` label in a recent refactor.

## 6.2 Reversible Computation Decomposition

The Nop platform leverages reversible computation theory to implement a generic decomposition and merging mechanism for JSON and XML. It can decompose large JSON files into multiple smaller files based on general rules, effectively complementing AMIS with a module-based organizational syntax. The two most commonly used syntaxes are `x:extends`, which indicates inheritance from an external file, and `x:gen-extends`, which dynamically generates JSON objects that can be inherited.
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

The above example demonstrates that the system dynamically generates a CRUD page based on NopAuthDept.view.xml configuration and adds a bulk actions zone with a Test button. Clicking this button triggers a dialog, which is implemented by reusing the existing test.page.yaml file. The title attribute overrides the `x:extends` inheritance to set the dialog's title to "Test Dialog".

The `x:extends` mechanism is similar to a general-purpose operation for tree structure execution, akin to object-oriented inheritance.

For any JSON-formatted external files, we only need to modify the loading function for standard JSON files to use Nop's ResourceLoader, enabling reverse computation of decomposition and merging operations with support for compile-time meta-programming. This allows for complex structure transformations during compilation.

Detailed information is provided in

    [Understanding Low-Code Platforms Through Tensor Products](https://zhuanlan.zhihu.com/p/531474176)

## 6.4 Action Module

The AMIS DSL itself only supports writing embedded JS snippets within pages and does not directly support importing external JS functions. The Nop platform introduces an `xui:import` attribute for AMIS, allowing the integration of external JS libraries and their functions as event response handlers.

> This mechanism is generalizable and can be used to integrate other low-code engines

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

The example illustrates importing a demo.lib.js library and referencing its functions via `demo.testAction`.

`url: "@action:demo.testAction"` is a syntax provided by Nop's environment abstraction. It intercepts AMIS' fetcher calls, identifies the `@action:` prefix, maps to loaded JS functions, and triggers them with data parameters specified in the request.

The script library (`demo.lib.xjs`, note the `.xjs` suffix instead of `.js`) is stored in demo.lib.js. We use GraalVM-JS to transpile and package it into SystemJS modules via rollup.

```javascript
/* @x:gen-extends:
  <!--Here, XPL template language can be used to generate JS code -->
 */
import { ajaxFetch } from '@nop/utils'

import { myAction } from './sub.lib.js'

import { myAction2 } from './parts/sub2.lib.js'

import { ajaxRequest } from '@nop/utils'

export function testAction(options, page) {
    page.env.alert("xx");
    ajaxFetch(options)
    ajaxRequest(options)
    myAction(options, page)
    myAction2(options, page)

    return Promise.resolve({
        status: 200 ,
        data: {
            status: 0
        }
    })
}
```

xjs files can be written in standard ESM module format. We add compile-time dynamic generation capability to them via annotations (`@x:gen-extends`), which is utilized during the workflow editor's dynamic generation process.
# 6.5 GraphQL Simplification

GraphQL always requires specifying the list of returned fields, but for a low-code platform, the question of which fields are present in a form is something that can be determined by analyzing the model, so we can automatically determine the required fields without manually specifying them.

The Nop platform adds an extension to AMIS that allows us to construct GraphQL requests using the following syntax:

```javascript
url: "@graphql:NopAuthUser__get/{@formSelection}?id=$id"
```

For detailed information, please refer to my previous article [GraphQL Engine in a Low-Code Platform](https://zhuanlan.zhihu.com/p/589565334).

## 6.6 Internationalization

AMIS's JSON format can be easily read and processed. Therefore, many structural transformation tasks can be decoupled from the AMIS framework and handled uniformly by the backend.

The Nop platform provides a unified i18n string replacement mechanism for JSON, which supports two types of usage:

1. Use prefix-based syntax to identify and replace all values with `@i18n:`

2. For each key that needs internationalization, add a corresponding `@i18n:key` attribute
   For example:
   ```javascript
   {
     label: "@i18n:common.batchDelete|Bulk Delete"
   }
   OR
   {
     label: "Bulk Delete"
     "@i18n:label" : "common.batchDelete"
   }
   ```

## 6.7 Authorization Control

The Nop platform defines attributes like `xui:roles` and `xui:permissions` for permissions, and automatically validates these properties in the JSON-formatted page data, removing any nodes that do not meet the permission requirements. This process is performed at the JSON level without involving any knowledge of specific frontend frameworks.

## 6.8 Vue Component Integration
AMIS is built on React technology, while the front-end of the Nop platform is mainly developed using Vue 3.0 technology. To facilitate the integration of third-party Vue components, the Nop platform provides a generic wrapper component. In the AMIS configuration file, you can use it as shown below:

```javascript
{
  "type": "vue-form-item",
  "vueComponent": "Component name of Vue",
  "props": {
    Properties passed to the Vue component
  }
}
```

## Summary

The Baidu AMIS framework is a sophisticated low-code front-end framework with low integration complexity. The Nop platform has made certain improvements and extensions based on the AMIS framework, providing solutions for some common issues. The encapsulated code for AMIS in the Nop platform has been uploaded to Gitee at [nop-chaos](https://gitee.com/canonical-entropy/nop-chaos.git). For those interested in integrating AMIS, please refer to it.

The open-source address of the Nop platform is:

- Gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- GitHub: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Development example: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Inverse Computing Principle and Introduction to Nop Platform plus Q&A\_Bilibili](https://www.bilibili.com/video/BV1u84y1w7kX/)

