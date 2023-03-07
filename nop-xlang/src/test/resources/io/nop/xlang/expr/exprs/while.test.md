# 1. while循环

````expr
let x = 0;
while(x < 3){
  x ++;
}
$.checkEquals(3,x);
````

# 2. while循环，break跳出

````expr
let x = 0;
while(true){
   if(x < 3){
      x ++;
   }else{
      break;
   }
}
$.checkEquals(3,x);
````

# 3. while循环，continue跳到下一循环

````expr
let count = 0;
let x = 0;
while(x < 3){
  x ++;
  if(x == 2)
     continue;
  count ++;
}

$.checkEquals(2,count);
````

# 4. while局部变量作用域

```expr
let x = 0;
while(x < 3){
  let y = 4;
  x += y;
}
let y = 2;
$.checkEquals(4,x);
```