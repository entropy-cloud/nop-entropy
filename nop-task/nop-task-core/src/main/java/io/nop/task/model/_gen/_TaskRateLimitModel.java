package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [74:10:0:0]/nop/schema/task/task.xdef <p>
 * 限制对同一个key的调用速率不能超过指定值
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _TaskRateLimitModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: keyExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _keyExpr ;
    
    /**
     *  
     * xml name: maxWait
     * 
     */
    private int _maxWait ;
    
    /**
     *  
     * xml name: requestPerSecond
     * 
     */
    private int _requestPerSecond ;
    
    /**
     * 
     * xml name: keyExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getKeyExpr(){
      return _keyExpr;
    }

    
    public void setKeyExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._keyExpr = value;
           
    }

    
    /**
     * 
     * xml name: maxWait
     *  
     */
    
    public int getMaxWait(){
      return _maxWait;
    }

    
    public void setMaxWait(int value){
        checkAllowChange();
        
        this._maxWait = value;
           
    }

    
    /**
     * 
     * xml name: requestPerSecond
     *  
     */
    
    public int getRequestPerSecond(){
      return _requestPerSecond;
    }

    
    public void setRequestPerSecond(int value){
        checkAllowChange();
        
        this._requestPerSecond = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("keyExpr",this.getKeyExpr());
        out.put("maxWait",this.getMaxWait());
        out.put("requestPerSecond",this.getRequestPerSecond());
    }
}
 // resume CPD analysis - CPD-ON
