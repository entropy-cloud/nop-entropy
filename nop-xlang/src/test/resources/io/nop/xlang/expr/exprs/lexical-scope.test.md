# 1. 变量名冲突

````expr
let x = 1;
let x = 2;
````

* errorCode: nop.err.xlang.declare-var-conflicts

# 2. 不同block的变量名不冲突

````expr
{
let x = 1;
}

{
let x = 2;
}
````

# 3. if语句的变量名不冲突

````expr
for(let x=1;x<2;x++){
}

for(let x=2;x<3;x++){
}

let x = 3;
````

# 4. 函数内外的变量名不冲突

````expr
let x = 2;
let y = 5;

function f(){
  let x = 3;
  return x + 2;
}

function g(){
  let x = 2;
  return ()=>{
    let x = 1;
    return x + y;
  };
}


$.checkEquals(6,g()());
````

# 5. 嵌套block的变量名不能重名

````expr
let x = 2;
for(let x = 1;x<3;x++){}
````

* errorCode: nop.err.xlang.declare-var-conflicts

# 6. 变量名和参数名不能重名

````expr
function f(a){
  {
    let a = 3;
  }
}
````

* errorCode: nop.err.xlang.declare-var-conflicts

# 7. 参数名不能重名

````expr
function h(a,{a,c}){
}
````

* errorCode: nop.err.xlang.param-name-conflicted

# 8. 函数参数名与外部变量名不冲突

````expr
let x = 3;
function f(x){
  x = x + 1;
  return x;
}

let y = f(x);
$.checkEquals(4,y);
$.checkEquals(3,x);
````