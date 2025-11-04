# 1. 字符串拼接

````expr
let x = "\n";
let y = null;
x+=y;
x+="s";
x=x+'v';
$.checkEquals("\nnullsv",x);
````
