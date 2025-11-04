# 条件标签

````xpl
<c:unit>
  <c:import from="/test/cond.xlib" />
  
  <c:script>
     let x = 1;
     let y = 1;
  </c:script>
  
  <cond:Test a="true">
    <c:script>
       x = 3;
    </c:script>
  </cond:Test>
  
  <cond:Test a="false">
    <c:script>
       y = 3;
    </c:script>
  </cond:Test>
  
  <c:script>
     $.checkEquals(3,x);
     $.checkEquals(1,y);
  </c:script>
</c:unit>
````