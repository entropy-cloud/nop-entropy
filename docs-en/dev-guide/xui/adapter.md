# Adaptation

## In-Page Navigation

1. `___back`: Call `router.back()`
2. `___forward`: Call `router.forward()`
3. For URLs ending with "page.yaml", "page.json", or "page.json5", use `jsonPage` to display

## Special Links

### `@query`

Corresponds to GraphQL query, format: `@query:{operationName}/{selection}`

Example:
`@query:NopAuthUser__get/userName,nickName,dept{name}` translates to:

```graphql
query {
  NopAuthUser(id:$id) {
    userName,
    nickName,
    dept {
      name
    }
  }
}
```

For standard CRUD operations like `xxx___get`, `xxx_findPage` in [graphql.ts](https://gitee.com/canonical-entropy/nop-chaos/blob/master/packages/nop-core/src/core/graphql.ts), the corresponding parameters are registered in the operationRegistry.

Note: The registered parameter is based on the action suffix, e.g., NopAuthUser___findPage or NopAuthUser___admin_findPage. The standard method names are all `findPage`, with parameter formats as follows:

```javascript
{
  // operation: 'query',
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

Corresponds to GraphQL mutation, format similar to `@query`.

### `@dict`

Uses `DictProvider___getDict` service to load dictionary items. Example:
`@dict:my/my-dict`

### `@page`

Loads pages dynamically by importing `xui:import` libraries and resolving actions.

### `@action`

Triggers defined actions or `xui:import` actions. Example:

```json
{
  "xui:import": "demo.lib.js",
  "api": {
    "url": "@action:demo.myAction"
  }
}
```

`@action:demo.myAction` refers to the `myAction` method in `demo.lib.js`. When triggered via API URL, parameters are passed as `[FetcherRequest](https://gitee.com/canonical-entropy/nop-chaos/blob/master/packages/nop-core/src/core/types.ts)`.

### `@fn`

Directly defines anonymous functions.

## Custom Event Actions

Frontend introduces a special `page-action` type action. The parameter must include a special `@action` to specify the trigger action name.

Example:

```json5
{
  "onEvent": {
    "click": { // Listen for click events
      "actions": [ // List of actions to execute
        {
          "actionType": "page-action", // Execute custom action
          "args": { // Arguments
            "@action": "demo.myAction",
            "msg": "Trigger click event"
          }
        }
      ]
    }
  }
}
```
