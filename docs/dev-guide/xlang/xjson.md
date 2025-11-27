# 使用XML格式来表达json对象

XLang语言中规定了XML和JSON的标准转换方式，可以实现XML和JSON之间的可逆转换。

xjson是一种紧凑的XML和json映射方式，它规定了如下规则：

1. tagName对应于json中的type属性。如果tagName是`_`，则该属性会被忽略
2. body段如果有多个节点则自动对应于列表对象
3. j:list="true"表示本节点对应于列表对象
4. 属性的值或者文本节点的值，如果具有 `@:`前缀，则表示后面是json格式
5. 对于不能作为xml标签名或者属性名的key，可以使用`j:key`属性，例如`<_ j:key='&amp;'>$$</_>`实际会生成`{'&': '$$'}`

例如：

```
<form name="a">
  <actions j:list="true">
    <action enabled="@:false" label="ss" />
  </actions>

  <body>
     <input />
     <input />
  </body>
</form>
```

转换成json对应于

```json
{
  "type": "form",
  "name": "a",
  "actions": [
    {
      "type": "action",
      "enabled": false,
      "label": "ss"
    }
  ],
  "body": [
    {
      "type": "input"
    },
    {
      "type": "input"
    }
  ]
}
```

简单值的列表可以采用如下格式：

```xml
<list j:list="true">
  <_>A</_>
  <_>B</_>
  <_>C</_>
</list>
```

list对应的值为`['A',B','C']`

## Xpl中的xjson

XPL模板语言可以设置outputMode="xjson"，从而通过Xpl模板语言来动态构建json对象。web.xlib中大量利用这种方式来生成amis页面，
它比直接使用json格式要简单直观得多。

```xml
<actions xpl:if="hasAction">
    <action enabled="${enabled}" />
</actions>
```

在xdef元模型定义中，xdef:value="xpl-xjson"表示对应的xpl模板会输出xjson内容。如果没有输出，则返回值对应于xjson的输出结果。

例如，XView模型中cell单元格的gen-control节点是xjson输出节点，对于它的配置，以下两种方式是等价的:

```xml
<form>
    <cell>
        <gen-control>
            <input type="a" width="${config.width}"/>
        </gen-control>
    </cell>

    <cell>
        <gen-control>
            <c:script>
                return {
                type: "a",
                width: config.width
                }
            </c:script>
        </gen-control>
    </cell>
</form>
```

**注意**: gen-control是xpl语法，动态生成内容。因此其中的`${}`是按照xpl语言解析并在后台执行的。如果要生成AMIS的表达式，需要转义 `@{'$'}{expr}`
