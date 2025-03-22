# Using XML Format to Express JSON Objects

The XLang language defines standard conversion methods between XML and JSON, enabling reversible conversions between XML and JSON.

xjson is a compact mapping method between XML and JSON. It specifies the following rules:

1. `tagName` corresponds to the `type` attribute in JSON.
2. If there are multiple nodes, they automatically correspond to a list object.
3. `j:list="true"` indicates that this node corresponds to a list object.
4. The value of an attribute prefixed with `@:` indicates that the following content is in JSON format.

For example:

```xml
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

When converted to JSON, it corresponds to:

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

The XPL template language supports `outputMode="xjson"`, allowing dynamic construction of JSON objects via the Xpl template language. This method is significantly simpler than directly using JSON format.

For example:

```xml
<actions xpl:if="hasAction">
  <action enabled="${enabled}" />
</actions>
```

In the xdef meta-model definition, `xdef:value="xpl-xjson"` indicates that the corresponding Xpl template will output xjson content. If no output is generated, it corresponds to the JSON output result.

For example, in the XView model's cell single cell:

```xml
<form>
  <cell>
    <gen-control>
      <input type="a" width="${config.width}" />
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