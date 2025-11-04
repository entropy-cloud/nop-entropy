# 1. 对象属性赋值

````xpl
<c:unit>

  <c:script>
    const entity = {
      x : 3,
      y : 'ss',
    }
  </c:script>

  <c:assign obj="${entity}">
     <field name="x" value="${entity.x + 2}" />
     <field name="y" value="vv" />
  </c:assign>


  <c:script>
    $.checkEquals(5, entity.x);
    $.checkEquals("vv", entity.y);
  </c:script>
</c:unit>
````

