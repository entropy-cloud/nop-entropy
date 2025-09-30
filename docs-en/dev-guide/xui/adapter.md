
# Adaptation

## In-Page Navigation

1. __back: calls router.back()
2. __forward: calls router.forward()
3. For links with suffixes like page.yaml/page.json/page.json5, use jsonPage to display

## Special Links

### `@query`

Corresponds to a GraphQL query. Its format is `@query:{operationName}/{selection}`,
for example `@query:NopAuthUser__get/userName,nickName,dept{name}` will be translated into

```graphql
query{
   NopAuthUser(id:$id){
       userName, nickName, dept { name}
   }
}
```

For standard CRUD actions, such as xxx__get, xxx_findPage, etc., the corresponding parameter lists are registered in the operationRegistry of [graphql.ts](https://gitee.com/canonical-entropy/nop-chaos/blob/master/packages/nop-core/src/core/graphql.ts).
Note: what is registered here is the action suffix; that is, NopAuthUser__findPage or NopAuthUser__admin_findPage both map to the standard method name findPage, and their parameter format is

```javascript
{
    //  operation: 'query',
    arguments: [
        {
            name: 'query',
            type: 'QueryBeanInput',
            builder: argQuery
        }
    ]
}
```

### `@mutation`

Corresponds to a GraphQL mutation. Its format is similar to `@query`.

### `@dict`

Loads dictionary entries using the `DictProvider__getDict` service, for example `@dict:my/my-dict`

### `@page`

Loads a page, dynamically loads function libraries imported via `xui:import`, and resolves actions used in the page

### `@action`

Triggers an action defined on the page, or an action imported via `xui:import`, for example

```json
{
  "xui:import": "demo.lib.js",
  "api": {
    "url": "@action:demo.myAction"
  }
}
```

`@action:demo.myAction` means calling the myAction method in the demo.lib.js library. When used as the API URL to trigger, the parameter passed to the function is of type [FetcherRequest](https://gitee.com/canonical-entropy/nop-chaos/blob/master/packages/nop-core/src/core/types.ts)

### `@fn`

A directly defined anonymous function

## Custom Event Actions

The frontend introduces a special action of type page-action. Its arguments must include a special parameter `@action` to specify the name of the action to trigger.

```json5
{
    "onEvent": {
        "click": { // Listen for click events
          "actions": [ // List of actions to execute
            {
              "actionType": "page-action", // Execute a custom action
              "args": { // Arguments
                "@action": "demo.myAction",
                "msg": "Dispatch click event"
              }
            }
          ]
        }
    }
}
```

<!-- SOURCE_MD5:972dcd4cdcf0794da369a29e0db8a5b2-->
