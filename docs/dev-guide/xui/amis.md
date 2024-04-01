# AMIS框架扩展

Nop平台对百度开源的AMIS框架进行了一定的扩展。

## 1.1 XML与JSON的自动转化

在手工编写和阅读的时候，XML格式相对于JSON格式是有一定优势的，特别是集成外部模板引擎用于动态生成的时候。Nop平台为AMIS增加了XML格式的语法表达形式，可以按照简单的几条规则实现XML和JSON之间的双向转换。具体规则如下:

1. `type`属性对应于标签名

2. 简单类型的属性对应于XML的属性名

3. 复杂类型的属性对应于XML的子节点

4. 如果是列表类型，则在节点上标注`j:list=true`

5. `body`属性会被特殊识别，不用明确标注`j:list`

例如：

```json
{
        "type": "operation",
        "label": "操作",
        "buttons": [
          {
            "label": "详情",
            "type": "button",
            "level": "link",
            "actionType": "dialog",
            "dialog": {
              "title": "查看详情",
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

对应于XML格式

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

Nop平台中的XPL模板语言为动态生成XML提供了诸多简化帮助，例如

```xml
<button xpl:if="xxx" label="${'$'}{grade}" icon="${icon}">
</button>
```

作为模板运行时`xpl:if`表示条件表达式，只有表达式返回`true`时整个节点才会被生成。所有XML属性生成的时候，如果属性值为`null`，则它将被自动忽略，不会输出到最终的结果中。借助于这一`null`属性过滤机制，我们可以很简单的控制哪些属性会被生成。

AMIS的语法设计相对比较规整，转成XML之后很接近于普通的HTML或者Vue模板。相比之下，LowCodeEngine的DSL设计更像是针对领域对象的一种序列化协议，而不是一种便于手工编写和阅读的DSL语言。

> AMIS早期版本的DSL设计中也存在着大量不一致的地方，比如容器控件的内容部分，有时叫做`children`，有时叫做`controls`，有时叫做`content`，最近重构后才普遍改成了`body`。

### XView模型中配置gen-control

在`XView`模型的grid或者form配置中，如果根据数据类型和数据域自动推定的控件不满足要求，我们可以手工实现`gen-control`。例如

```xml
<form id="add">
   <layout>fldA fldB</layout>
   <cells>
      <cell id="fldA">
         <gen-control>
            return { 这里写json格式的配置}
         </gen-control>
      </cell>
   </cells>
</form>
```

在json中可以通过`x:extends`来继承已有的页面。

```
 <gen-control>
   // title会覆盖继承的页面中的title配置, 而initApi.url会覆盖对应的initApi对象中的url属性。依此类推，可以覆盖任意深度的属性
   return { "x:extends": "/nop/xxx/pages/zzz.page.yaml", title:"xxx", initApi: {url: "xxx"} }
 </gen-control>
```

在`gen-control`段中也可以采用XML格式输出

```xml
<gen-control>
   <input-text />
</gen-control>
```

XML格式将按照前面介绍的转换规则转换为对应的json. 在`gen-control`段中使用xml格式时，如果要用`x:extends`机制，则因为编译器解析XView时也要处理`x:extends`属性,
所以我们必须回避这个名字，使用`xdsl:extends`来代替。例如

```xml
<gen-control>
   <dialog xdsl:extends="/nop/xxx/pages/yyy.page.yaml" title="ss" />
</gen-control>
```

## 1.2 可逆计算分解

Nop平台基于可逆计算理论针对JSON和XML实现了通用的分解合并机制，可以按照通用的规则将很大的JSON文件分解为多个小型文件，相当于是为AMIS补充了某种模块组织语法。最常用的是两个语法，`x:extends`用于表示继承外部的某个文件，`x:gen-extends`表示动态生成可以被继承的JSON对象。

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

以上示例表示，首先根据`NopAuthDept.view.xml`的配置动态生成一个CRUD页面，然后再在批量操作按钮区增加一个`Test`按钮，点击这个按钮的时候会弹出一个对话框，对话框的实现代码是复用已有的`test.page.yaml`文件。`title`属性会覆盖`x:extends`继承的内容，将对话框的标题设置为`Test Dialog`。

`x:extends`相当于是某种在Tree结构上执行的，类似面向对象的继承操作的通用操作符。

对于任意的JSON格式的外部文件，我们只需要将普通的JSON文件的加载函数修改为Nop平台所提供的`ResourceLoader`调用即可自动获得可逆计算所定义的分解、合并操作，并支持编译期元编程，允许在编译期进行一系列复杂的结构变换。

具体介绍参见

[从张量积看低代码平台的设计](https://zhuanlan.zhihu.com/p/531474176)

## 1.3 Action模块化

AMIS的DSL本身只支持编写嵌入在页面中的JS片段代码，并不直接支持引入外部编写的JS函数。Nop平台为AMIS引入了一个`xui:import`属性，允许引入外部的JS库，把其中的函数作为事件响应函数来使用。

> 这一机制是通用的，可以用于集成其他的低代码引擎

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

以上示例表示，我们导入一个`demo.lib.js`库，然后通过`demo.testAction`引用其中的函数。

`url: "@action:demo.testAction"`这一语法是我们在AMIS的环境抽象基础上所提供的一个`action`触发机制。它通过拦截AMIS的`fetcher`调用，识别`@action:`前缀，然后映射到已加载的JS函数上，调用时传入`data`指定的参数。

脚本库的代码存放在`demo.lib.xjs`中（注意后缀名是xjs而不是js，我们会通过graalvm-js脚本引擎调用rollup打包工具将xjs转换为js文件，并打包成SystemJs模块结构）。

```javascript
/* @x:gen-extends:
  <!--这里可以用XPL模板语言来生成js代码 -->
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

xjs文件可以按照普通的ESM模块文件的格式进行编写。我们通过在注释区增加`@x:gen-extends`为它增加了编译期动态生成的能力（这一能力在工作流编辑器的动态生成中会使用）。

`export`的函数是暴露给外部调用的接口函数。`import`调用会被转化为SystemJs的dependency。这里有一个特殊处理，对于`/parts/`目录下的文件，我们会调用rollup把它的代码和主文件的代码打包在一起，即`parts`下的文件认为是内部实现文件，不会暴露为外部可访问的js库。
打包后生成的结果参见文件 [demo.lib.js](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-auth/nop-auth-app/_dump/nop-app/nop/auth/pages/DemoPage/demo.lib.js)

除了`action`调用之外，外部库函数可以用在一切允许嵌入js脚本的地方，为此我们提供了另一个前缀`@fn:`，使用它的时候需要明确传递函数参数（`action`的函数参数已经约定为`options`,`page`）。

```javascript
"onClick":"@fn:demo.myListener(event,props)"
```

重新思考一下`onClick`的调用过程，我们会发现根据函数名查找到函数实现体的过程很类似于DOM组件的事件冒泡处理过程。事件冒泡时传递的是事件名，逐层向上查找，找到响应函数后处理。AMIS的`action`响应处理过程是由每个组件检查自己的`handleAction`是否可以处理对应的`actionType`，如果不能处理，则调用父组件传入的`onAction`来进行处理。

**如果我们直接约定向上传递的事件名就是函数名，则事件冒泡处理的过程可以被看作是一个在词法作用域中解析函数名的过程**。在不同层级引入的`xui:import`相当于是创建了不同的词法作用域，我们总是在最近的词法作用域中查找对应的函数，如果未找到，则继续向上在父作用域中查找。

## 1.4 GraphQL简化

GraphQL总是需要指定返回字段列表，但是对于一个低代码平台来说，表单中具有哪些字段是一件可以根据模型分析得到的事情，所以我们可以根据表单模型自动推定它需要哪些字段而不需要手工指定。

Nop平台为AMIS增加了一个扩展，使得我们可以通过如下语法构造GraphQL请求

```javascript
url: "@graphql:NopAuthUser__get/{@formSelection}?id=$id"
```

具体介绍可以参见[graphql-java.md](../graphql/graphql-java.md)

## 1.5 多语言国际化

AMIS的JSON格式，可以很容易的被读取和处理。因此很多结构变换工作完全可以脱离AMIS框架，由后端进行统一的处理。
Nop平台中对JSON提供了统一的i18n字符串替换机制，它规定了如下两种方式：

1. 使用前缀引导语法识别并替换所有具有`@i18n:`的值

2. 为每个需要被国际化的key，增加对应的`@i18n:key`属性
   例如
   
   ```javascript
   {
   label: "@i18n:common.batchDelete|批量删除"
   }
   或者
   {
   label: "批量删除"
   "@i18n:label" : "common.batchDelete"
   }
   ```

## 1.6 权限控制

Nop平台规定了`xui:roles`和`xui:permissions`等权限相关的属性，在接收到JSON格式的页面数据之后，会自动验证权限属性是否满足，并删除所有不满足权限要求的节点。这一处理过程在JSON结构上进行，不涉及到任何前端框架特有的知识。

## 1.7 Vue组件集成

AMIS底层是基于React技术开发，而Nop平台的前端主要基于Vue3.0技术开发，为了便于集成第三方的vue组件, Nop平台提供了一个通用的包装组件。在AMIS的配置文件中我们可以这样使用

```javascript
{
  "type": "vue-form-item",
  "vueComponent": "Vue组件名",
  "props": {
    传给vue组件的属性
  }
}
```

## 1.8 复杂GraphQL调用

```
api:{
  url: '@graphql:query($id:String){ NopAuthUser_get(id:$id){nickName}}',
  data: {
    id: "3"
  }
}
```

通过`@graphql:`前缀来表示graphql请求，此时需要使用完整的graphql语法，参数需要指定类型。 通过`data`属性可以传递graphql请求所需的`variables`参数。

## 使用Vue3实现AMIS控件

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

## 问题

1. 缺少一种通用的方案，对前台控件的值进行自定义转换
2. 缺少一种通用的方案，可以通过弹出框来编辑指定字段的值。
