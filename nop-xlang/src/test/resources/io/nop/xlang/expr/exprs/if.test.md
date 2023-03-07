# 1. if/else语句

````expr
let x = 1;
if(x > 1){
  x = 0;
}else{
  x = 3;
}
$.checkEquals(3,x);
````

# 2. if语句多个分支，执行第二个分支

````expr
let x = 0;
if(x > 3){
  x = 1;
}else if(x >= 0){
  x = 2;
}else{
  x = 3;
}
$.checkEquals(2,x);
````

# 3. if语句多个分支，执行缺省分支

````expr
let x = 0;
if(x > 3){
  x = 1;
}else if(x > 0){
  x = 2;
}else{
  x = 3;
}
$.checkEquals(3,x);
````

# 4. 复杂判断条件

````expr
let x = 0;
if(x >= 0 && x != 3){
   x= 5;
}

$.checkEquals(5,x);
````

# 5. 三目运算符

````expr
let x = 3;
let y = x == 3 ? 2: 1;
$.checkEquals(2,y);
````