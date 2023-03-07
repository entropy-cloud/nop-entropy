(function(root, factory){
  // 先判断是否支持AMD(define是否存在)
  if(typeof define === 'function' && define.amd) {
    // 如果有依赖，例如：define(['vue'], factory); 下面同理，采用对应的模块化机制，加载依赖项
    define(factory);
  }   
}(this, function(){
  return {
    test: function(){
    	alert("k")
    }
  };
}))