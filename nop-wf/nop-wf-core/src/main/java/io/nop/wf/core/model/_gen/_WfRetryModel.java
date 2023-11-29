package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [216:14:0:0]/nop/schema/wf/wf.xdef <p>
 * 执行source的时候如果发生异常，则可以重试
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _WfRetryModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: exception-filter
     * 上下文环境中存在$exception变量，返回false表示异常不可被恢复，不能继续重试
     */
    private io.nop.core.lang.eval.IEvalPredicate _exceptionFilter ;
    
    /**
     *  
     * xml name: exponentialDelay
     * 
     */
    private boolean _exponentialDelay  = true;
    
    /**
     *  
     * xml name: maxRetryCount
     * 
     */
    private int _maxRetryCount  = 0;
    
    /**
     *  
     * xml name: maxRetryDelay
     * 
     */
    private int _maxRetryDelay  = 0;
    
    /**
     *  
     * xml name: retryDelay
     * 
     */
    private int _retryDelay  = 0;
    
    /**
     * 
     * xml name: exception-filter
     *  上下文环境中存在$exception变量，返回false表示异常不可被恢复，不能继续重试
     */
    
    public io.nop.core.lang.eval.IEvalPredicate getExceptionFilter(){
      return _exceptionFilter;
    }

    
    public void setExceptionFilter(io.nop.core.lang.eval.IEvalPredicate value){
        checkAllowChange();
        
        this._exceptionFilter = value;
           
    }

    
    /**
     * 
     * xml name: exponentialDelay
     *  
     */
    
    public boolean isExponentialDelay(){
      return _exponentialDelay;
    }

    
    public void setExponentialDelay(boolean value){
        checkAllowChange();
        
        this._exponentialDelay = value;
           
    }

    
    /**
     * 
     * xml name: maxRetryCount
     *  
     */
    
    public int getMaxRetryCount(){
      return _maxRetryCount;
    }

    
    public void setMaxRetryCount(int value){
        checkAllowChange();
        
        this._maxRetryCount = value;
           
    }

    
    /**
     * 
     * xml name: maxRetryDelay
     *  
     */
    
    public int getMaxRetryDelay(){
      return _maxRetryDelay;
    }

    
    public void setMaxRetryDelay(int value){
        checkAllowChange();
        
        this._maxRetryDelay = value;
           
    }

    
    /**
     * 
     * xml name: retryDelay
     *  
     */
    
    public int getRetryDelay(){
      return _retryDelay;
    }

    
    public void setRetryDelay(int value){
        checkAllowChange();
        
        this._retryDelay = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("exceptionFilter",this.getExceptionFilter());
        out.put("exponentialDelay",this.isExponentialDelay());
        out.put("maxRetryCount",this.getMaxRetryCount());
        out.put("maxRetryDelay",this.getMaxRetryDelay());
        out.put("retryDelay",this.getRetryDelay());
    }
}
 // resume CPD analysis - CPD-ON
