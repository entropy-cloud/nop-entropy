# 1. 不允许重复定义变量

````xpl
<c:unit xpl:outputMode="xml">
 <c:script>
    const x = 1;
 </c:script>
 
 <div>
   <c:if test="${true}">
      <c:script>
         let x = 2;
      </c:script>
   </c:if>
 </div>
</c:unit>
````

* errorCode: nop.err.xlang.declare-var-conflicts

# 2. 不同的xpl scope互不影响

````xpl
<c:unit xpl:outputMode="text">
   <c:unit>
      <c:script>
         let x = 1;
      </c:script>
      <c:script>
        $.checkEquals(1, x);
      </c:script>
      <c:unit>
         <c:script>
           $.checkEquals(1,x);
         </c:script>
      </c:unit>
   </c:unit>
   
   <c:unit>
      <c:script>
         let x = 2;
      </c:script>
      <c:script>
        $.checkEquals(2, x);
      </c:script>
   </c:unit>
</c:unit>
````