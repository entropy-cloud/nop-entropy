# 1. 测试type关键字作为变量名

````expr
  function getConvertMethod(type){
     return 'Convert' + type.$lastPart('.');
  }
  $.checkEquals('ConvertInteger',getConvertMethod('java.lang.Integer'));
````