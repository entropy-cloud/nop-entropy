# 1. 通过decorator定义多重嵌套
````xpl
<c:unit xpl:outputMode="xml">
<c:script>
  let x = 1;
</c:script>

<div id="c">
  <xpl:decorator>
     <div xpl:if="x == 1" id="a" />
     <div xpl:if="x > 0" id="b" />
  </xpl:decorator>
</div>
</c:unit>
````

* outputMode:xml
* output
````

<div id="a">
<div id="b">
<div id="c"/></div></div>
````