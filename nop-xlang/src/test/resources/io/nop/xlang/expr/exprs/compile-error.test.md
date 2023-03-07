# 1. break语句必须放在循环语句内部

````expr
function f(){
  if(true)
    break;
}
````

* errorCode: nop.err.xlang.break-statement-not-in-loop