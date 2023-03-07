# 1. for(begin to end)

````xpl
<c:for var="i" begin="1" end="3" xpl:outputMode="xml">
  ${i}<br/>
</c:for>
````

* outputMode: xml
* output

````

  1
<br/>
  2
<br/>
  3
<br/>
````

# 2. for(var of items)

````xpl
<c:for var="x" items="${[1,2,3]}" xpl:outputMode="xml" index="index">
  ${x}<br/><c:script>$.checkEquals(x,index+1)</c:script>
</c:for>
````

*outputMode: xml
*output

````

  1
<br/>
  2
<br/>
  3
<br/>
````

# 3. for循环变量不允许修改

````xpl
<c:for var="i" begin="1" end="3">
<c:script>
 i++;
</c:script>
</c:for>
````

* errorCode: nop.err.xlang.identifier-not-allow-change

# 4. 通过script从for循环跳出

````xpl
<c:unit>
<c:script>
  let count = 0;
</c:script>
<c:for var="i" begin="1" end="3">
<c:script>
 if(i == 2)
    break;
 count ++;
</c:script>
</c:for>

<c:script>
  $.checkEquals(1,count);
</c:script>
</c:unit>
````

# 4. 通过`<c:break>`标签从for循环跳出

````xpl
<c:unit>
<c:script>
  let count = 0;
</c:script>
<c:for var="i" begin="1" end="3">
  <c:if test="${i == 2}">
    <c:break />
  </c:if>
  <c:script>
    count ++;
  </c:script>
</c:for>

<c:script>
  $.checkEquals(1,count);
</c:script>
</c:unit>
````

# 4. 循环下标index

````xpl
<c:unit>
<c:for var="i" begin="1" end="3" index="index">
  <c:script>
    $.checkEquals(i-1,index);
  </c:script>
</c:for>

<c:for var="i" begin="1" end="3" index="index">
  <c:script>
    $.checkEquals(i-1,index);
  </c:script>
</c:for>
</c:unit>
````