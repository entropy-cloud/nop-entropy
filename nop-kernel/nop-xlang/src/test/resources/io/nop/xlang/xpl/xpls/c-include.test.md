# 1. scope可见性相当于直接粘贴代码过来

````xpl
<c:unit>
  <c:include src="/test/script.xpl" />
  
  <c:script>
    $.checkEquals(1,includedVar);
  </c:script>
  
  <c:collect outputMode="xjson">
     <c:script>
        $.checkEquals(1,includedVar);
     </c:script>
  </c:collect>
</c:unit>
````