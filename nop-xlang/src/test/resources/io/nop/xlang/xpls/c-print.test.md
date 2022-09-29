# 1. 直接输出文本，不解析表达式和标签

````xpl
<c:unit xpl:outputMode="xml">
<c:print>
<c:script>
    let x = 1;
 </c:script>
  
  <c:if test="${x == 1}">
    <c:script>
       x = 2;
    </c:script>
  </c:if>
</c:print>
</c:unit>
````

* outputMode: xml
* output
````
<c:script>
    let x = 1;
 </c:script>
<c:if test="${x == 1}">
    <c:script>
       x = 2;
    </c:script>
</c:if>
````