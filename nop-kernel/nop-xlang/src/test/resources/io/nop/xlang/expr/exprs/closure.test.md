# 1. 闭包变量

````expr
let x = 1;
function add(y){ return x + y;}

$.checkEquals(4, add(3))

````

# 2. 闭包变量，函数先使用再声明

````expr
let x = 1;
add(3);

function add(y){
    return x + y;
}

$.checkEquals(4,add(3))
````

# 3. 闭包变量，返回闭包函数

````expr
let x = 1;
function add(y){
    return ()=>{ return x + y }
}
$.checkEquals(4, add(3)())

````

# 4. 闭包变量，lambda表达式

````expr
let x = 1;
function add(y){
    return ()=>x + y
}
$.checkEquals(4, add(3)())

````

# 5. 闭包变量，变量引用

```expr
let x = 1;
function add(y){
    return ()=>{ return x + y }
}
 x = 4;
 $.checkEquals(7, add(3)())
  
```

# 6. 通过闭包访问可变变量

````expr
let x = 1; 
function add(y){ 
    return ()=>{ return x + y }
} 
let h = add(3); 
x=4; 
$.checkEquals(7,h())
````

# 7. 函数指针

````expr
  function m(v){
     return v + 1;
  }
  
  $.checkEquals(3,h(m));
  
  function h(g2){
     return g2(2);
  }
````

# 8. 闭包变量，闭包函数引用

````expr
let x = 1;
  function f(y){
     return x + y;
  }
  
  function k(y){
    return y + 2;
  }
  
  function g(a){ // closure: x needed in f
     let z = 3;
     z ++;
     // f本身访问了闭包变量
     return f(z) + k(z);
  }
   
  $.checkEquals(5, f(4));
  $.checkEquals(6, k(4));
 
  $.checkEquals(11,h(g));
  
  function h(g2){
     return g2(2);
  }
````