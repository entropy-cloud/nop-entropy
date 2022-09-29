# 1. import class

````xpl
<c:unit>
  <c:import class="io.nop.api.core.ApiConstants" />
  <c:script>
     $.checkEquals("nop-version",ApiConstants.HEADER_VERSION);
  </c:script>
  
  <c:unit>
    <c:import class="io.nop.xlang.ast.XLangOutputMode" />
    <c:script>
      $.checkEquals("html", XLangOutputMode.html.toString());
    </c:script>
  </c:unit>
</c:unit>
````

# 2. import class 仅局部可见

````xpl
<c:unit>
  
  <c:unit>
    <c:import class="io.nop.xlang.ast.XLangOutputMode" />
    <c:script>
      $.checkEquals("html", XLangOutputMode.html.toString());
    </c:script>
  </c:unit>
  
  <c:script>
     XLangOutputMode.html;
  </c:script>
</c:unit>
````

* errorCode: nop.err.xlang.unresolved-identifier