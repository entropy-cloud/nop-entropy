# 1. ignoreTag后不应该删除xpl:lib属性

```xpl
<biz:Validator xpl:ignoreTag="true" xpl:lib="a.xlib" xpl:outputMode="xml" />
```

* outputMode: xml
* output:

````

<biz:Validator xpl:lib="a.xlib"/>
````

# 2. node属性模式下，ignoreTag后不应该删除xpl:lib属性

```xpl
<biz:Validator xpl:ignoreTag="true" xpl:lib="a.xlib" xpl:outputMode="node" />
```

* outputMode: node
* output:

````
<_>
    <biz:Validator xpl:lib="a.xlib"/>
</_>
````

## 3. 使用`xpl:is`动态输出标签名

```xpl
<c:unit xpl:is="${'c:for' + ''}" xpl:outputMode="xml" />
```

* outputMode: xml
* output:

```

<c:for/>
```
