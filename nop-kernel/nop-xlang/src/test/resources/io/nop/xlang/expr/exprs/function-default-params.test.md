# 1. 函数参数缺省值

````expr
function f(a,b=1){
  return a + b;
}

$.checkEquals(3,f(2));
````

# 2. 函数参数不允许超出定义参数个数

````expr
function f(a,b=1){
}

f(1,2,3);
````

* errorCode: nop.err.xlang.exec.too-many-args