# AMIS Framework Extension

The Nop platform has made certain extensions to the Google open-source AMIS framework.

## 1.1 XML and JSON Automatic Conversion

When manually writing and reading data, the XML format has certain advantages over the JSON format, especially when integrating external template engines for dynamic generation. The Nop platform added an XML syntax representation to the AMIS framework, enabling simple rule-based conversion between XML and JSON. The specific rules are as follows:

1. The `type` attribute corresponds to the tag name.
2. Simple type attributes correspond to XML attribute names.
3. Complex type attributes correspond to XML child nodes.
4. If it's a list type, mark the node with `j:list=true`.
5. The `body` attribute will be specially identified and does not require explicit marking of `j:list`.

For example:

```json
{
    "type": "operation",
    "label": "Operation",
    "buttons": [
        {
            "label": "Detail",
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
                            "label": "Grade",
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
    ]
}
```

Corresponding XML format:

```xml
<operation label="操作">
    <buttons j:list="true">
        <button label="详情" level="link" actionType="dialog">
            <dialog titl="查看详情">
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

The Nop platform's XPL template language simplifies dynamic XML generation with various optimizations, such as:

```xml
<button xpl:if="xxx" label="${'$'}{grade}" icon="${icon}"/>
```

In the template execution phase, `xpl:if` represents a conditional expression. Only when the expression returns `true` will the node be generated. Additionally, if an attribute's value is `null`, it will be automatically ignored during generation. This `null` attribute filtering mechanism allows us to simply control which attributes are generated.

### AMIS Syntax Design

The syntax design of **AMIS** is relatively consistent. After being converted into XML, it closely resembles standard HTML or Vue templates. In comparison, the **LowCodeEngine**'s DSL design resembles a serialized protocol for domain objects rather than a language designed for manual writing and reading by developers.

> The early versions of AMIS also had many inconsistencies in their DSL design, such as container controls being referred to as `children`, `controls`, or `content` at different times. After recent refactoring, they were standardized as `body`.

### Configuration in XView Model

In the **XView** model's grid or form configuration:

- If predefined controls do not meet requirements based on data type and domain, you can manually implement **gen-control**.
- For example, in XML format:

```xml
<form id="add">
  <layout>fldA fldB</layout>
  <cells>
    <cell id="fldA">
      <gen-control>
        return { 这里写JSON格式的配置 }
      </gen-control>
    </cell>
  </cells>
</form>
```

- In JSON, inheritance can be achieved using `x:extends`:

```json
<gen-control>
  // title will override the inherited page's title configuration, while initApi.url will override the corresponding initApi object's url property. This pattern continues for any depth of properties.
  return {
    "x:extends": "/nop/xxx/pages/zzz.page.yaml",
    title: "xxx",
    initApi: { url: "xxx" }
  }
</gen-control>
```

- In the **gen-control** section, you can also use XML output:

```xml
<gen-control>
  <input-text />
</gen-control>
```

- XML will be converted to JSON following the previously mentioned conversion rules. When using `x:extends` in the **gen-control** section, the compiler may interpret it as `x:gen-extends`, so we must avoid using `x:extends` and instead use `xdsl:extends`.

### Example Usage of xdsl:extends

```xml
<gen-control>
  <dialog xdsl:extends="/nop/xxx/pages/yyy.page.yaml" title="ss" />
</gen-control>
```

## Reversible Data Decomposition

The **Nop** platform is based on reversible computing theory and implements a generic decomposition and merging mechanism for JSON and XML. It allows large JSON files to be decomposed into multiple smaller files, effectively complementing AMIS with a module-based syntax. The most commonly used methods are:

- `x:extends`: Represents inheritance of an external file.
- `x:gen-extends`: Dynamically generates objects that can be inherited.

### Example Configuration

```yaml
x:gen-extends:
  - web:GenPage view="NopAuthDept.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib"
  - xpl:lib="/nop/web/xlib/web.xlib"

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

This example demonstrates that:

1. **NopAuthDept.view.xml** is used to dynamically generate a CRUD page.
2. A bulk actions button (`Test`) is added within the `crud-grid`.
3. Clicking the `Test` button displays a dialog with content inherited from `test.page.yaml`.
4. The `title` attribute overrides the inherited dialog's title to "Test Dialog".
5. `x:extends` operates similarly to a wildcard import, allowing inheritance at any level.
6. For external JSON files, replace `test.page.yaml` with your actual file path.

By using **ResourceLoader**, you can load standard JSON files and automatically apply reversible data decomposition and merging operations supported by the platform. This allows for complex transformations during compilation, enabling developers to focus on business logic rather than data structure management.

For detailed documentation, refer to the official **Nop** platform documentation.



## 1.3 Action Modularization

The DSL (Domain-Specific Language) of AMIS (Advanced Mobile Integration System) itself only supports writing JavaScript code embedded within the page and does not directly support importing externally written JavaScript functions. The Nop platform introduced a `xui:import` attribute into AMIS, enabling the importation of external JavaScript libraries and treating their functions as event response functions.

> This mechanism is universal and can be used for integrating other low-code engines.

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

The above example demonstrates importing the `demo.lib.js` library and using `demo.testAction` to reference its functions.

The syntax `url: "@action:demo.testAction"` is provided by AMIS's environment abstraction as an `action` trigger mechanism. It intercepts AMIS's `fetcher` calls, identifies the `@action:` prefix, maps it to loaded JavaScript functions, and passes the specified `data` parameter when the action is triggered.

The script library's code resides in `demo.lib.xjs` (note: the suffix is .xjs, not .js; we use GraalVM-JS to transpile and bundle .xjs files into .js using Rollup).

```javascript
/* @x:gen-extends:
  <!--XPL template language can be used to generate JavaScript code -->
 */
import { ajaxFetch } from '@nop/utils'
import { myAction } from './sub.lib.js'
import { myAction2 } from './parts/sub2.lib.js'
import { ajaxRequest } from '@nop/utils'

export function testAction(options, page) {
  page.env.alert("xx")
  ajaxFetch(options)
  ajaxRequest(options)
  myAction(options, page)
  myAction2(options, page)

  return Promise.resolve({
    status: 200,
    data: {
      status: 0
    }
  })
}
```

The .xjs file follows standard ESM module formatting. We generate it using GraalVM-JS to transpile and bundle the code, leveraging Rollup for .parts/ directory files.

`export` functions are exposed to external use as interface functions. `import` statements are converted into SystemJs dependencies. Special handling is applied for `/parts/` directory files, where Rollup bundles their code alongside the main file, treating them as internal implementations not exposed externally.

After bundling, the result can be viewed in the .js file at [demo.lib.js](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-auth/nop-auth-app/_dump/nop-app/nop/auth/pages/DemoPage/demo.lib.js)

In addition to `action` calls, external JavaScript functions can be used anywhere that allows JavaScript code embedding. For this purpose, we provide another prefix `@fn:` for explicit function invocation, requiring explicit parameter transmission (with `action` function parameters predefined as `options` and `page`).

```javascript
"onClick":"@fn:demo.myListener(event,props)"
```

Reexamining the `onClick` calling process, we observe that it follows a similar pattern to standard DOM component event bubbling. When an event bubbles up, it passes the event name, which is then matched layer by layer until the corresponding response function is found. AMIS's `action` processing is handled by each component checking if its `handleAction` method can process the `actionType`. If not, it defers to the parent component's `onAction` method for processing.



## 1.4 GraphQL Simplification

GraphQL Always Requires Specifying The Return Field List. However, For A Low-Code Platform, Determining Which Fields Are Present In The Form Can Be Derived From The Model Analysis. Therefore, We Can Automatically Determine The Required Fields Without Manually Specifying Them.

The Nop Platform Adds An Extension To AMIS That Allows Constructing GraphQL Queries Using The Following Syntax:

```javascript
url: "@graphql:NopAuthUser__get/{@formSelection}?id=$id"
```

For Detailed Information, Refer to [graphql-java.md](../graphql/graphql-java.md).


## 1.5 Internationalization

AMIS's JSON format is easy to read and process. Therefore, many structural changes can be handled outside of the AMIS framework by the backend.

The Nop Platform provides a unified i18n string replacement mechanism for JSON. It defines two ways:

1. Use prefix-based syntax to identify and replace all values with `@i18n:`.
2. For each key that needs internationalization, add the corresponding `@i18n:key` attribute.

Examples:

```javascript
{
  label: "@i18n:common.batchDelete|Bulk Delete"
}
```

Or:

```javascript
{
  label: "Bulk Delete",
  "@i18n:label": "common.batchDelete"
}
```


## 1.6 Access Control

The Nop Platform defines attributes like `xui:roles` and `xui:permissions` related to permissions. Upon receiving JSON data for a page, it automatically verifies these permission attributes and removes any nodes that do not meet the requirements. This process is performed at the JSON structure level without involving any frontend framework-specific knowledge.


## 1.7 Vue Component Integration

AMIS is built on React technology, while Nop's frontend is based on Vue 3.0. To facilitate integration of third-party Vue components, Nop provides a generic wrapper component. In AMIS configuration files, you can use it as follows:

```javascript
{
  "type": "vue-form-item",
  "vueComponent": "Vue Component Name",
  "props": {
    "prop1": "Prop Value"
  }
}
```


## 1.8 Complex GraphQL Calls

```json
api:{
  url: "@graphql:query($id:String){ NopAuthUser_get(id:$id){nickname}}",
  data: {
    id: "3"
  }
}
```

GraphQL requests prefixed with `@graphql:` require complete GraphQL syntax. Variables can be passed using the `data` property.


## 1.9 Implementing AMIS Controls With Vue 3

```javascript
import appva from '../../views/breadcrumb_example/Elbutton.vue'

function CustomComponent(props) {
  let dom = React.useRef(null);
  
  React.useEffect(function() {
    const app = createApp({ render: () => h(appva) });
    app.mount(dom.current);
    return () => app.unmount();
  }, []);

  return React.createElement('div', { ref: dom });
}

amisLib.Renderer({
  test: /(^|\/)my-custom/
})(CustomComponent);
```


## 1.10 Using Service For Scope

```json
{
  "type": "page",
  "body": {
    "type": "service",
    "dataProvider": "setData({'id': data.contractId})",
    "body": [
      {
        "type": "tpl",
        "tpl": "data:${id}"
      }
    ]
  }
}
```

## Issues

1. Lacks a universal solution for custom conversion of front-end control values.
2. Lacks a universal solution for editing specific field values through a modal dialog.

