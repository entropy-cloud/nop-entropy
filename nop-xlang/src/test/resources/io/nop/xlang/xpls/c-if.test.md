# 1. `<c:if>`标签
````xpl
<c:unit>
  <c:script>
    let x = 1;
  </c:script>
  
  <c:if test="${x == 1}">
    <c:script>
       x = 2;
    </c:script>
  </c:if>
</c:unit>
````

* return: 2

# 2. `<c:if>`输出xml

````xpl
<div xpl:outputMode="html">
  <div />
  <c:script>
     let x = 1;
  </c:script>
  <c:if test="${x == 1}">
    <div>A${x+1}B</div>
  </c:if>
</div>
````

* outputMode: xml
* output:
````

<div>
<div></div>
<div>A2B</div></div>
````