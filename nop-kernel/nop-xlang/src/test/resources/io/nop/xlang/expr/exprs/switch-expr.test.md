# 1. 测试SWITCH函数

````expr

let x = 3;
let y = SWITCH(x,3,'A',4,'B');
$.checkEquals(y,"A");

y = SWITCH(x,2,'A','C');
$.checkEquals(y,"C");

y = SWITCH(x,"3",'A',3,'B',"4","C");
$.checkEquals(y,"B");
````