# 1. 收集xpl输出
````xpl
<c:unit>
<c:script>
 let x;
</c:script>
<c:collect xpl:return="x" outputMode="html">
  <div>x</div>
</c:collect>

<c:script>
$.checkEquals("\n&lt;div>x&lt;/div>",x);
</c:script>
</c:unit>
````

# 2. outputMode=node模式下收集xpl输出
````xpl
<c:unit xpl:outputMode="node">
<c:var name="x" />
<c:collect xpl:return="x" outputMode="xml">
  <div>x</div>
</c:collect>

<c:script>
$.checkEquals("\n&lt;div>x&lt;/div>",x);
</c:script>
${x}
</c:unit>
````

* outputMode: node
* output:
````
<_>

&lt;div&gt;x&lt;/div&gt;
</_>
````