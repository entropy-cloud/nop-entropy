# 1. do循环

````expr
let x = 0;
do{
  x ++;
}while(x < 3);
$.checkEquals(3,x);
````

# 2. do循环，break跳出

````expr
let x = 0;
do{
   if(x < 3){
      x ++;
   }else{
      break;
   }
}while(true);
$.checkEquals(3,x);
````

# 3. do循环，continue跳到下一循环

````expr
let count = 0;
let x = 0;
do{
  x ++;
  if(x == 2)
     continue;
  count ++;
}while(x < 3);

$.checkEquals(2,count);
````