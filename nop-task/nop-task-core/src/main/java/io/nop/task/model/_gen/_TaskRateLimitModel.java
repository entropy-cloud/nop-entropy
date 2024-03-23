package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.TaskRateLimitModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [80:10:0:0]/nop/schema/task/task.xdef <p>
 * 限制对同一个key的调用速率不能超过指定值
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
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
    private double _requestPerSecond ;
    
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
    
    public double getRequestPerSecond(){
      return _requestPerSecond;
    }

    
    public void setRequestPerSecond(double value){
        checkAllowChange();
        
        this._requestPerSecond = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("keyExpr",this.getKeyExpr());
        out.putNotNull("maxWait",this.getMaxWait());
        out.putNotNull("requestPerSecond",this.getRequestPerSecond());
    }

    public TaskRateLimitModel cloneInstance(){
        TaskRateLimitModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(TaskRateLimitModel instance){
        super.copyTo(instance);
        
        instance.setKeyExpr(this.getKeyExpr());
        instance.setMaxWait(this.getMaxWait());
        instance.setRequestPerSecond(this.getRequestPerSecond());
    }

    protected TaskRateLimitModel newInstance(){
        return (TaskRateLimitModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
