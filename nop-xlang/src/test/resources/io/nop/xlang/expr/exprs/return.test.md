# 1. 通过return语句从while循环中返回

````expr
function f(x){
  function g(y){
     while(true){
       if(x > y){
          return x;
       }else{
          x ++;
       }
     }
  }
  
  return g(5);
}

$.checkEquals(6,f(2));
````

# 2. 通过return语句从block返回

````expr
function f(){
  let x=  3;
  {
    if(x > 2)
       return 1;
  }
  return 0;
}

$.checkEquals(1,f());
````

# 3. return json

````expr
function f(){
  return {
     a:[3],
     }
}
````

# 4. root return

````expr
return {
        type:'number',
        value: 'add'
    }
````