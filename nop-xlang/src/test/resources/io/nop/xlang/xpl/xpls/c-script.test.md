# 1. 通过lang指定java语言

````xpl
<c:unit>
  <c:script>
    let x = 1;
    let y = 2;
  </c:script>

  <c:script lang="java" args="x:int,y:int" returnType="int">
    return x + y;
  </c:script>


</c:unit>
````

* return: 3
