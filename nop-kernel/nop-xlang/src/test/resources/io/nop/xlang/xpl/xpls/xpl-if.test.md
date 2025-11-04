# 1. 内置xpl:if属性

```xpl
<c:unit xpl:outputMode="xml">
<div xpl:if="true">1</div>
<div xpl:if="false">2</div>
</c:unit>
```

* outputMode: xml
* output

````

<div>1</div>
````

# 2. 内置xpl:skipIf属性

skipIf当条件满足时跳过本层

````xpl
<c:unit xpl:outputMode="xml">
<c:script>
 let x = 1;
</c:script>
<div id="a">
<div xpl:skipIf="x == 1">
  <span>${x}</span>
</div>
</div>
</c:unit>
````

* outputMode: xml
* output

````

<div id="a">
<span>1</span></div>
````