# Front-end Configuration

## 1. input-tree has a default max height of max-height:300px

```javascript
  {
    type: 'input-tree',
    treeContainerClassName: 'h-full'
  }
```

h-full removes the max-height setting

## 2. Set the width of the page's aside area

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

See amis-ui/helper/sizing/_width.scss for configurable width classes.

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

## Service call returns a string and is expected to be set into the context as a variable x

```javascript
{
  type: 'service',
  api: {
    url: "xxx"
    responseKey: "x"
  }
}
```

responseKey is recognized by the Nop platform’s ajaxFetch function; AMIS itself does not support it.

## Expressions

* You can use the expression functions GETRENDERERDATA(id, path) and GETRENDERERPROP(id, path) to obtain a specified component’s data and properties, respectively.
* After an HTTP request action completes, subsequent actions can obtain the response via ${responseResult} or ${{outputVar}}.

## Set combo data

1. Assign an id property to the combo so that event actions can target it.
2. The dialog button configuration maps data as {comboIndex: "${index}"}. Since the CRUD row data also has an index variable, when dispatching the action, the index variable is the row number of the CRUD. Therefore, when opening the dialog, first assign the combo’s index to comboIndex.
3. Add a button in the CRUD operation column; setting close: true will close the dialog after the action completes.
4. Add an onEvent configuration to the button: on click, perform the setValue action, setting the parameter index to '${comboIndex}' and value to ${&}. Here, {&} is special syntax used to retrieve the entire context data.

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
              "title": "History",
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
                      "label": "Operation",
                      "buttons": [
                        {
                          "label": "Copy",
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

## Group multiple input controls together, with each input control corresponding to its own variable

```json
 {
  "type": "input-group",
  "name": "input-group",
  "label": "Input group validation",
  "body": [
    {
      "type": "input-text",
      "placeholder": "Please enter a numeric value with length no more than 6",
      "name": "group-input1",
      "label": "Child element 1",
      "validations": {
        "isNumeric": true,
        "maxLength": 6
      }
    },
    {
      "type": "input-text",
      "placeholder": "Please enter text with length no less than 5",
      "name": "group-input2",
      "required": true,
      "validations": {
        "minLength": 5
      }
    }
  ]
}
```

## API returns list data

When returning list data, the normalizeApiResponseData function automatically wraps the list as {items: list}, ensuring the returned data is always a Map object when used.

## Table Editing

* During row editing, the context contains the variable index, corresponding to the current row’s index.
* TableRow has an itemIndex property.
* IRow.change(values) can modify values.
* TableStore.getRowById(id) returns an IRow.
* props.store.row obtains the current row; store.rowIndex corresponds to the row index.

## Store operations

* store.changeValue('x',123)
*

## Debugging

You can insert a debugger statement into an onClick event.

```
"onClick": "debugger; props.store.closeDialog()"
```

## Parameters in the URL
AMIS makes special assumptions about parameters in the URL. For example, with `url: '/test?filter_type=1'`, when there is a type filter in the picker control’s filter section, it will override the filter_type variable in the URL; you need to put it in the data section.

```
{
  url: '/test',
  data:{
    filter_type: 1
  }
}
```
<!-- SOURCE_MD5:987543875049212f02a8763f02eb627a-->
