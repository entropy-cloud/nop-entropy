# 1. 单个true分支
````xpl
<c:unit>
<c:script>
   let ret = 0;
</c:script>

<c:choose>
  <when test="${true}">
    <c:script> ret = 1 </c:script>
  </when>
</c:choose>
</c:unit>
````

* outputMode: none
* return: 1


# 2. 两个分支
````xpl
<c:unit>
<c:script>
   let ret = 0;
   let x = 2;
</c:script>

<c:choose>
  <when test="${x == 1}">
    <c:script> ret = 1 </c:script>
  </when>
  <when test="${x == 2}">
    <c:script> ret = 2 </c:script>
  </when>
</c:choose>
</c:unit>
````

* outputMode: none
* return: 2


# 2. otherwise
````xpl
<c:unit>
<c:script>
   let ret = 0;
   let x = 3;
</c:script>

<c:choose>
  <when test="${x == 1}">
    <c:script> ret = 1 </c:script>
  </when>
  <when test="${x == 2}">
    <c:script> ret = 2 </c:script>
  </when>
  <otherwise>
    <c:script> ret = 3 </c:script>
  </otherwise>
</c:choose>
</c:unit>
````

* outputMode: none
* return: 3
