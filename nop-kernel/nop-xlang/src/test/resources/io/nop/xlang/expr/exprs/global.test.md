# 1. 全局变量

````expr
$.checkArgument($context.userId == null);
$.checkArgument($scope != null);

````

# 2. 宏函数

location()返回表达式当前源码位置

````expr
logInfo("loc={}",location());
````

# 3. JSON序列化

对应于io.nop.core.lang.json.JSON对象

````expr
 let x = $JSON.stringify({a:1});
 $.checkEquals("{\"a\":1}",x);
 
 let y = $JSON.parse(x);
 $.checkEquals(1,y.a);
````

# 4. Math数学函数

对应于io.nop.core.commons.util.MathHelper对象

````expr
$.checkEquals(1.0, $Math.cos(0));
````