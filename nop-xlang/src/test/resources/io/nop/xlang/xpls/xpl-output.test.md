# 1. 输出html, null属性被自动忽略
````xpl
<html xpl:outputMode="html">
  <tr class="${'a'}" style="${null}">
     <td/>
  </tr>
</html>
````

* outputMode: html
* output:

````

<html>
<tr class="a">
<td></td></tr></html>
````

# 2. 输出扩展属性，明确指定的属性以指定的表达式为准
xpl:attrs总是在其他属性输出之后执行。这一行为与vue3的属性输出策略不同。

````xpl
<div a="1" xpl:attrs="${{a:'11',b:'22',c:'33'}}" b="${null}" xpl:outputMode="xml" />
````

* outputMode: xml
* output
````

<div a="1" c="33"/>
````

# 3. 输出普通文本，不进行xml转义
````xpl
<c:unit xpl:outputMode="text">
Map&lt;String,Object>
</c:unit>
````

* outputMode: xml
* output:
````

Map<String,Object>

````