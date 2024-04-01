# 适配

## 页内跳转

1. \_\_back: 调用router.back()
2. \_\_forward: 调用router.forward()
3. 对于后缀名为page.yaml/page.json/page.json5等后缀的链接，使用jsonPage显示

## 特殊链接

### `@query`

对应GraphQL query，它的格式为 `@query:{operationName}/{selection}`,
例如 `@query:NopAuthUser__get/userName,nickName,dept{name}` 会被翻译为

```graphql
query{
   NopAuthUser(id:$id){
       userName, nickName, dept { name}
   }
}
```

对于标准的CRUD动作，例如xxx\_\_get， xxx\_findPage等，在[graphql.ts](https://gitee.com/canonical-entropy/nop-chaos/blob/master/packages/nop-core/src/core/graphql.ts)的operationRegistry中注册了对应的参数列表。
注意：这里注册的是动作的后缀，也就说NopAuthUser\_\_findPage或者NopAuthUser\_\_admin\_findPage，返回的标准方法名都是findPage，它们的参数格式都是

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

对应GraphQL mutation， 它的格式与`@query`类似。

### `@dict`

利用`DictProvider__getDict`服务来加载字典项，例如 `@dict:my/my-dict`

### `@page`

加载页面，动态加载`xui:import`引入的函数库，并resolve页面中用到的action

### `@action`

触发page上定义的action，或者`xui:import`引入的action，例如

```json
{
  "xui:import": "demo.lib.js",
  "api": {
    "url": "@action:demo.myAction"
  }
}
```

`@action:demo.myAction`表示调用demo.lib.js库中的myAction方法。作为api的url来触发时，传给函数的参数为[FetcherRequest](https://gitee.com/canonical-entropy/nop-chaos/blob/master/packages/nop-core/src/core/types.ts)类型

### `@fn`

直接定义的匿名函数

## 自定义事件动作

前台引入了一个特殊的page-action类型的动作，它的参数中必须包含一个特殊的参数`@action`用于指定触发的动作名称。

```json5
{
    "onEvent": {
        "click": { // 监听点击事件
          "actions": [ // 执行的动作列表
            {
              "actionType": "page-action", // 执行自定义action
              "args": { // 
                "@action": "demo.myAction",
                "msg": "派发点击事件"
              }
            }
          ]
        }
    }
}
```
