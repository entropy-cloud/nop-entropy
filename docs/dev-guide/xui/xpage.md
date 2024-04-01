# 前端配置

## 1. input-tree缺省设置了最大高度 max-height:300px

```javascript
  {
    type: 'input-tree',
    treeContainerClassName: 'h-full'
  }
```

h-full可以去除max-height设置

## 2. 设置page的aside区域的宽度

```javascript
 {
   type: 'page',
   aside: [{
     type : 'input-text'
   }],
   asideResizor: true
   asideClassName: w-60
 } 
```

可配置的宽度类参见 amis-ui/helper/sizing/\_width.scss

|Class|Properties|
|---|---|
|w-px|width: 0.0625rem|
|w-0|width: 0|
|w-none|width: 0|
|w-0.5|width: 0.125rem|
|w-1|width: 0.25rem|
|w-1.5|width: 0.375rem|
|w-2|width: 0.5rem|
|w-2.5|width: 0.625rem|
|w-3|width: 0.75rem|
|w-3.5|width: 0.875rem|
|w-4|width: 1rem|
|w-5|width: 1.25rem|
|w-6|width: 1.5rem|
|w-7|width: 1.75rem|
|w-8|width: 2rem|
|w-9|width: 2.25rem|
|w-10|width: 2.5rem|
|w-11|width: 2.75rem|
|w-12|width: 3rem|
|w-14|width: 3.5rem|
|w-16|width: 4rem|
|w-18|width: 4.5rem|
|w-20|width: 5rem|
|w-24|width: 6rem|
|w-28|width: 7rem|
|w-32|width: 8rem|
|w-36|width: 9rem|
|w-40|width: 10rem|
|w-44|width: 11rem|
|w-48|width: 12rem|
|w-52|width: 13rem|
|w-56|width: 14rem|
|w-60|width: 15rem|
|w-64|width: 16rem|
|w-72|width: 18rem|
|w-80|width: 20rem|
|w-96|width: 24rem|
|w-auto|width: auto|
|w-1x|width: 1em|
|w-2x|width: 2em|
|w-3x|width: 3em|
|w-1/2|width: 50%|
|w-1/3|width: 33.333333%|
|w-2/3|width: 66.666667%|
|w-1/4|width: 25%|
|w-2/4|width: 50%|
|w-3/4|width: 75%|
|w-1/5|width: 20%|
|w-2/5|width: 40%|
|w-3/5|width: 60%|
|w-4/5|width: 80%|
|w-1/6|width: 16.666667%|
|w-2/6|width: 33.333333%|
|w-3/6|width: 50%|
|w-4/6|width: 66.666667%|
|w-5/6|width: 83.333333%|
|w-1/12|width: 8.333333%|
|w-2/12|width: 16.666667%|
|w-3/12|width: 25%|
|w-4/12|width: 33.333333%|
|w-5/12|width: 41.666667%|
|w-6/12|width: 50%|
|w-7/12|width: 58.333333%|
|w-8/12|width: 66.666667%|
|w-9/12|width: 75%|
|w-10/12|width: 83.333333%|
|w-11/12|width: 91.666667%|
|w-full|width: 100%|
|w-screen|width: 100vw|
|w-min|width: min-content|
|w-max|width: max-content|
|min-w-0|min-width: 0px|
|min-w-full|min-width: 100%|
|min-w-min|min-width: min-content|
|min-w-max|min-width: max-content|
|max-w-none|max-width: none|
|max-w-0|max-width: 0rem|
|max-w-xs|max-width: 20rem|
|max-w-sm|max-width: 24rem|
|max-w-md|max-width: 28rem|
|max-w-lg|max-width: 32rem|
|max-w-xl|max-width: 36rem|
|max-w-2xl|max-width: 42rem|
|max-w-3xl|max-width: 48rem|
|max-w-4xl|max-width: 56rem|
|max-w-5xl|max-width: 64rem|
|max-w-6xl|max-width: 72rem|
|max-w-7xl|max-width: 80rem|
|max-w-full|max-width: 100%|
|max-w-min|max-width: min-content|
|max-w-max|max-width: max-content|
|max-w-prose|max-width: 65ch|

## Service调用返回string类型，希望设置到上下文中成为一个变量x

```javascript
{
  type: 'service',
  api: {
    url: "xxx"
    responseKey: "x"
  }
}
```

responseKey是Nop平台的ajaxFetch函数负责识别的，amis本身并不支持。

## 表达式

* 可以通过表达式函数GETRENDERERDATA(id, path)和GETRENDERERPROP(id, path)分别获取指定组件的数据和属性。
* http 请求动作执行结束后，后面的动作可以通过 ${responseResult}或${{outputVar}}来获取请求响应结果

## 设置combo数据

1. 需要给 combo 设置个 id 属性，用来给事件动作指定目标用。
2. 弹窗按钮配置了数据映射 {comboIndex: "${index}"} 因为 crud 的行数据上也有 index 变量，派送动作时获取 index 变量是 crud 所在行的序号。所以弹出弹窗的时候，先把 combo 的序号赋值给 comboIndex
3. crud 操作栏里面添加了个按钮，close: true 设置是让动作完成后关闭弹窗。
4. 按钮里面添加了 onEvent 配置，click 时做 setValue 动作，并设置参数 index 为 '${comboIndex}' 值为 ${\&}。其中 {\&} 是特殊语法，用来取整个上下数据。

```json
      {
        "type": "combo",
        "name": "combo",
        "id": "thecombo",
        "multiple": true,
        "value": [
          {
            "engine": ""
          }
        ],
        "items": [
          {
            "name": "engine",
            "type": "input-text"
          },
          {
            "label": "Copy",
            "type": "button",
            "actionType": "dialog",
            "size": "md",
            "dialog": {
              "title": "历史记录",
              "actions": [],
              "data": {
                "comboIndex": "${index}"
              },
              "body": [
                {
                  "type": "crud",
                  "api": "/amis/api/mock2/sample",
                  "columns": [
                    {
                      "label": "Engine",
                      "name": "engine"
                    },
                    {
                      "type": "operation",
                      "label": "操作",
                      "buttons": [
                        {
                          "label": "复制",
                          "type": "button",
                          "close": true,
                          "onEvent": {
                            "click": {
                              "actions": [
                                {
                                  "componentId": "thecombo",
                                  "actionType": "setValue",
                                  "args": {
                                    "index": "${comboIndex}",
                                    "value": "${&}"
                                  }
                                }
                              ]
                            }
                          }
                        }
                      ]
                    }
                  ]
                }
              ]
            }
          }
        ]
      }
```

## 将多个输入控件组织到一起，但是每个输入控件对应一个变量

```json
 {
  "type": "input-group",
  "name": "input-group",
  "label": "输入组合校验",
  "body": [
    {
      "type": "input-text",
      "placeholder": "请输入长度不超过6的数字类型",
      "name": "group-input1",
      "label": "子元素一",
      "validations": {
        "isNumeric": true,
        "maxLength": 6
      }
    },
    {
      "type": "input-text",
      "placeholder": "请输入长度不少于5的文本",
      "name": "group-input2",
      "required": true,
      "validations": {
        "minLength": 5
      }
    }
  ]
}
```

## API返回列表数据

返回列表数据时会normalizeApiResponseData函数会自动将列表包装为 {items:list}，这样确保返回的data在使用时始终是Map对象。

## 表格编辑

* 行编辑时上下文环境中存在变量index，对应于当前行的下标
* TableRow具有itemIndex属性
* IRow.change(values)可以修改值
* TableStore.getRowById(id)得到IRow
* props.store.row可以得到当前行，store.rowIndex对应行下标

## store操作

* store.changeValue('x',123)
* 

## 调试

可以在onClick事件中插入debugger指令。

```
"onClick": "debugger; props.store.closeDialog()"
```
