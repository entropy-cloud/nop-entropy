# 1. import class 仅局部可见

````xpl
<c:unit>

  <c:script>
    const entity = {
      x : 3,
      y : 4,
    }
  </c:script>

  <c:check errorCode="test.error" errorDescription="验证错误">
    <eq name="entity.x" value="3" />
    <eq name="entity.y" value="4" />
  </c:check>

  <c:check errorCode="test.error2" params="${{x:entity.x}}" errorDescription="验证错误">
    <eq name="entity.x" value="2" />
    <eq name="entity.y" value="4" />
  </c:check>

</c:unit>
````

* errorCode: test.error2
