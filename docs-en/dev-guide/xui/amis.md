
# AMIS Framework Extensions

The Nop platform has made certain extensions to Baidu’s open-source AMIS framework.

## 1.1 Automatic Conversion between XML and JSON

When manually authoring and reading, the XML format has certain advantages over JSON, especially when integrating external template engines for dynamic generation. The Nop platform adds an XML syntax form to AMIS, enabling bidirectional conversion between XML and JSON according to a few simple rules. The specific rules are:

1. The `type` property corresponds to the tag name.

2. Properties of simple types correspond to XML attribute names.

3. Properties of complex types correspond to XML child nodes.

4. For list types, mark the node with `j:list=true`.

5. The `body` property is specially recognized; you don’t need to explicitly mark `j:list`.

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

Corresponding XML format

```xml
<operation label="Operation">
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

The XPL template language in the Nop platform provides various simplifications for dynamically generating XML, for example

```xml
<button xpl:if="xxx" label="${'$'}{grade}" icon="${icon}">
</button>
```

At template runtime, `xpl:if` represents a conditional expression. The entire node is generated only when the expression returns `true`. When generating all XML attributes, if an attribute value is `null`, it will be automatically ignored and not output in the final result. With this `null` attribute filtering mechanism, we can easily control which attributes are generated.

AMIS’s syntax design is relatively tidy; once converted to XML, it is very close to ordinary HTML or Vue templates. In contrast, the LowCodeEngine DSL design looks more like a serialization protocol for domain objects rather than a DSL language that is convenient for manual authoring and reading.

> In early versions of AMIS’s DSL design, there were many inconsistencies. For example, the content section of container controls was sometimes called `children`, sometimes `controls`, sometimes `content`, and only recently after refactoring was it generally changed to `body`.

### Configuring gen-control in the XView Model

In the `XView` model’s grid or form configuration, if controls automatically inferred based on data types and data domains do not meet the requirements, we can manually implement `gen-control`. For example

```xml
<form id="add">
   <layout>fldA fldB</layout>
   <cells>
      <cell id="fldA">
         <gen-control>
            return { Write the configuration in JSON format here}
         </gen-control>
      </cell>
   </cells>
</form>
```

In JSON you can inherit existing pages via `x:extends`.

```
 <gen-control>
   // title overrides the title configuration in the inherited page, and initApi.url overrides the url property of the corresponding initApi object. By analogy, properties at arbitrary depth can be overridden.
   return { "x:extends": "/nop/xxx/pages/zzz.page.yaml", title:"xxx", initApi: {url: "xxx"} }
 </gen-control>
```

You can also output in XML format within the `gen-control` section

```xml
<gen-control>
   <input-text />
</gen-control>
```

The XML format will be converted to the corresponding JSON according to the conversion rules introduced earlier. When using XML format within the `gen-control` section, if you want to use the `x:extends` mechanism, because the compiler also handles the `x:extends` property when parsing XView, we must avoid this name and use `xdsl:extends` instead. For example

```xml
<gen-control>
   <dialog xdsl:extends="/nop/xxx/pages/yyy.page.yaml" title="ss" />
</gen-control>
```

## 1.2 Reversible Computation-Based Decomposition

Based on Reversible Computation theory, the Nop platform implements a general split/merge mechanism for JSON and XML, allowing large JSON files to be decomposed into multiple smaller files according to general rules, effectively adding a form of module organization syntax to AMIS. The two most commonly used syntaxes are `x:extends` to denote inheritance of an external file, and `x:gen-extends` to denote dynamically generating a JSON object that can be inherited.

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

The above example indicates that a CRUD page is first dynamically generated based on the configuration in `NopAuthDept.view.xml`, and then a `Test` button is added to the bulk actions area. Clicking this button will pop up a dialog, and the implementation code for the dialog reuses the existing `test.page.yaml` file. The `title` property overrides the content inherited via `x:extends`, setting the dialog title to `Test Dialog`.

`x:extends` is essentially a general operator that performs inheritance-like operations on a tree structure, analogous to object-oriented inheritance.

For any external JSON file, we only need to change the normal JSON file loading function to the Nop platform’s `ResourceLoader` call to automatically obtain the decomposition and merging operations defined by Reversible Computation, and support compile-time metaprogramming, allowing a series of complex structural transformations to be performed at compile time.

For a detailed introduction, see

[Design of Low-Code Platforms from the Perspective of Tensor Products](https://zhuanlan.zhihu.com/p/531474176)

## 1.3 Action Modularization

AMIS’s DSL itself only supports writing JS code snippets embedded in the page and does not directly support importing externally authored JS functions. The Nop platform introduces an `xui:import` property for AMIS, allowing external JS libraries to be imported and their functions to be used as event handlers.

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

The above example indicates that we import a `demo.lib.js` library and then reference its function via `demo.testAction`.

The syntax `url: "@action:demo.testAction"` is an `action` triggering mechanism provided by us based on AMIS’s environment abstraction. It intercepts AMIS’s `fetcher` calls, recognizes the `@action:` prefix, maps it to the loaded JS function, and passes the parameters specified by `data` when invoking.

The script library code is stored in `demo.lib.xjs` (note that the extension is xjs rather than js. Through the graalvm-js script engine, we invoke the rollup bundling tool to convert xjs to a js file and package it as a SystemJs module structure).

```javascript
/* @x:gen-extends:
  <!-- XPL template language can be used here to generate JS code -->
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

An xjs file can be authored in the format of a normal ESM module file. By adding `@x:gen-extends` in the comment section, we endow it with compile-time dynamic generation capabilities (this capability is used for dynamic generation in the workflow editor).

Functions exported via `export` are interface functions exposed for external invocation. `import` calls will be converted into SystemJs dependencies. There is a special handling: for files under the `/parts/` directory, we invoke rollup to bundle their code together with the main file’s code. That is, files under `parts` are considered internal implementation files and will not be exposed as externally accessible JS libraries.
For the packaged result, see [demo.lib.js](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-auth/nop-auth-app/_dump/nop-app/nop/auth/pages/DemoPage/demo.lib.js)

Besides `action` invocations, external library functions can be used anywhere where embedding JS scripts is allowed. For this, we provide another prefix `@fn:`. When using it, you need to explicitly pass function parameters (`action` functions already agree on `options` and `page` as parameters).

```javascript
"onClick":"@fn:demo.myListener(event,props)"
```

Reconsidering the `onClick` invocation process, we will find that the process of looking up the function implementation based on the function name is quite similar to the event bubbling processing of DOM components. In event bubbling, the event name is propagated, and the search proceeds upward layer by layer. When a handler is found, it is invoked. AMIS’s `action` handling process is for each component to check whether its `handleAction` can handle the corresponding `actionType`. If it cannot, it calls the parent component’s `onAction` to handle it.

If we directly agree that the event name propagated upward is the function name, then the event bubbling handling process can be regarded as resolving the function name within lexical scopes. `xui:import` introduced at different levels is equivalent to creating different lexical scopes. We always look up the corresponding function in the nearest lexical scope; if not found, we continue searching upward in the parent scope.

## 1.4 GraphQL Simplification

GraphQL always requires specifying the list of return fields. However, for a low-code platform, which fields a form contains can be determined through model analysis. Therefore, we can infer the fields needed by the form from the form model without manual specification.

The Nop platform adds an extension to AMIS that allows us to construct GraphQL requests using the following syntax

```javascript
url: "@graphql:NopAuthUser__get/{@formSelection}?id=$id"
```

For details, see [graphql-java.md](../graphql/graphql-java.md)

## 1.5 Multilingual Internationalization

AMIS’s JSON format can be easily read and processed. Therefore, many structural transformation tasks can be completely decoupled from the AMIS framework and handled uniformly by the backend.
The Nop platform provides a unified i18n string replacement mechanism for JSON, defining the following two approaches:

1. Use a prefix-guided syntax to identify and replace all values with `@i18n:`.

2. For each key that needs to be internationalized, add a corresponding `@i18n:key` property.
   For example

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

## 1.6 Access Control

The Nop platform defines access-related properties such as `xui:roles` and `xui:permissions`. After receiving JSON-formatted page data, it automatically verifies whether the access properties are satisfied and removes all nodes that do not meet the permission requirements. This processing is performed on the JSON structure and does not involve any knowledge specific to frontend frameworks.

## 1.7 Vue Component Integration

AMIS is developed on top of React, while the frontend of the Nop platform is mainly developed based on Vue 3.0. To facilitate the integration of third-party Vue components, the Nop platform provides a generic wrapper component. In an AMIS configuration file we can use it as follows

```javascript
{
  "type": "vue-form-item",
  "vueComponent": "Vue component name",
  "props": {
    Properties passed to the Vue component
  }
}
```

## 1.8 Complex GraphQL Calls

```
api:{
  url: '@graphql:query($id:String){ NopAuthUser_get(id:$id){nickName}}',
  data: {
    id: "3"
  }
}
```

Use the `@graphql:` prefix to denote a GraphQL request. In this case, you need to use the complete GraphQL syntax, and parameter types must be specified. The `data` property can pass the `variables` parameters required by the GraphQL request.

## Implementing AMIS Controls Using Vue 3

```javascript
import appva from '../../views/breadcrumb_example/Elbutton.vue'
 function CustomComponent(props) {
    let dom = React.useRef(null);
    React.useEffect(function () {
      var app = createApp({ render: () => h(appva) })
      app.mount(dom.current);
      return () => app.unmount()
    });
    return React.createElement('div', {
      ref: dom
    });
  }
  amisLib.Renderer({
    test: /(^|\/)my-custom/
  })(CustomComponent);
```

## Using Service to Provide Scope

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

1. A general solution is lacking for custom transformation of frontend control values.
2. A general solution is lacking to edit the value of a specified field via a modal dialog.

<!-- SOURCE_MD5:b4f9c407fe66179d246b2aefd53be41b-->
