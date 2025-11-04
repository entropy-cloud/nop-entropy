# 1. 解构变量声明

````expr
let x = {a:1,b:2,c:3};

let {a,b} = x;
$.checkEquals(1,a);
$.checkEquals(2,b);

let y = [1,2,3];
let [u,v] = y;
$.checkEquals(1,u);
$.checkEquals(2,v);
````

# 2. 解构参数

````expr
let x = {a:1,b:2,c:3};

function f({a,b}){
    $.checkEquals(1,a);
    $.checkEquals(2,b);
}

f(x);
````