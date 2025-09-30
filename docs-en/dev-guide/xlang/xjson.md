
# Use XML Format to Express JSON Objects

The XLang language defines a standard method for converting between XML and JSON, enabling reversible conversion between XML and JSON.

xjson is a compact mapping between XML and JSON, with the following rules:

1. tagName corresponds to the type property in JSON.
2. If the body section has multiple nodes, it automatically corresponds to a list object.
3. j:list="true" indicates that this node corresponds to a list object.
4. If an attribute value has the `@:` prefix, the remainder is in JSON format.

For example:

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

The corresponding JSON after conversion is

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

## xjson in Xpl

The XPL template language can set outputMode="xjson", allowing you to dynamically construct JSON objects via Xpl templates. The web.xlib extensively leverages this approach to generate amis pages; it is much simpler and more intuitive than using raw JSON directly.

```xml
<actions xpl:if="hasAction">
    <action enabled="${enabled}" />
</actions>
```

In the xdef meta-model definition, xdef:value="xpl-xjson" indicates that the corresponding Xpl template outputs xjson content. If nothing is output, the return value corresponds to the xjson output result.

For example, in the XView model, the gen-control node of a cell is an xjson output node. For its configuration, the following two approaches are equivalent:

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

<!-- SOURCE_MD5:57f6420328316aad33c71c405a604c7b-->
