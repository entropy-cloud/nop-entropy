# 1. c:return返回数据

````xpl
<c:unit>
<c:script>
 let x = 1;
</c:script>

<c:if test="${x==1}">
  <c:return value="${2}" />
</c:if>

<c:script>
  3;
</c:script>

</c:unit>
````

* return : 2