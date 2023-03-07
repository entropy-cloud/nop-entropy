# 递归嵌套调用标签

````xpl
<c:unit xpl:outputMode='text'>
<c:import from="/test/my.xlib" />

<c:script>
const tree = {
  name:'a',
  children: [
    {
       name:'b',
       children: [ {name: 'd'}, {name:'e'}]
    },
    {
       name: 'c'
    }
  ]
}
</c:script>
<my:Nested parent="${tree}" />
</c:unit>
````

* outputMode: text
* output: a,b,d,e,c,