# 1. 编译期循环，body部分被编译多次

````xpl
<c:unit xpl:outputMode="xml">
<macro:script>
 let items = [1,2,3];
</macro:script>

<macro:for var="x" items="#{items}">
  <div>#{x}</div>
</macro:for>
</c:unit>
````

* outputMode: xml
* output

````

<div>1</div>
<div>2</div>
<div>3</div>
````