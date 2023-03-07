# 1. 循环中的闭包函数

````expr
let list = [];
for(let x = 1;x<3;x++){
   list.push(y=>{
      return x + y;
   });
}
$.checkEquals(2, list[0](1));
$.checkEquals(3, list[1](1));

````

# 2. 闭包返回闭包

````expr
let k = 1, z=3;
let x = 3;
const y = 2;

function f(z){
  function g(){
    return x;
  }
  
  x *= z;
  
  return ()=>{
    function h(){
       return y + g();
    }
 
    return h; 
  }
}

const h = f(2);
$.checkEquals(8, h()());

````

# 3. 闭包引用

````expr
let x = 2;

function g(){
  x += 0;
  return (y) => {
     return x < y;
  }
}
x = 3;

let h = g();
$.checkEquals(false, h(1));
$.checkEquals(true, h(4));
x= 5;
$.checkEquals(true, h(6));
$.checkEquals(false, h(5));
````

# 4. 递归函数

````expr
function f(n){
  if(n <= 0)
     return n;
  return 1 + f(n-1);
}

$.checkEquals(3,f(3));
````

# 5. 嵌套递归

````expr
let x = 0;

function f(n){
  if(n <= 0)
    return n;
  return 1 + h(n-1) + x;
}
x = 1;
x = 0;

function h(n){
  if(n <= 0)
     return 0;
     
  return 1 + f(n-1) + x;
}

$.checkEquals(3,f(3));
````