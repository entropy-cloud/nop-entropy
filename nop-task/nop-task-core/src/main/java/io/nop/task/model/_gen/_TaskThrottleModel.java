package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.TaskThrottleModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/task.xdef <p>
 * 限制对同一个key的调用并发数不能超过指定值
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _TaskThrottleModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: global
     * 
     */
    private boolean _global  = false;
    
    /**
     *  
     * xml name: keyExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _keyExpr ;
    
    /**
     *  
     * xml name: maxConcurrency
     * 
     */
    private int _maxConcurrency ;
    
    /**
     *  
     * xml name: maxWait
     * 
     */
    private int _maxWait ;
    
    /**
     * 
     * xml name: global
     *  
     */
    
    public boolean isGlobal(){
      return _global;
    }

    
    public void setGlobal(boolean value){
        checkAllowChange();
        
        this._global = value;
           
    }

    
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
     * xml name: maxConcurrency
     *  
     */
    
    public int getMaxConcurrency(){
      return _maxConcurrency;
    }

    
    public void setMaxConcurrency(int value){
        checkAllowChange();
        
        this._maxConcurrency = value;
           
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
        
        out.putNotNull("global",this.isGlobal());
        out.putNotNull("keyExpr",this.getKeyExpr());
        out.putNotNull("maxConcurrency",this.getMaxConcurrency());
        out.putNotNull("maxWait",this.getMaxWait());
    }

    public TaskThrottleModel cloneInstance(){
        TaskThrottleModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(TaskThrottleModel instance){
        super.copyTo(instance);
        
        instance.setGlobal(this.isGlobal());
        instance.setKeyExpr(this.getKeyExpr());
        instance.setMaxConcurrency(this.getMaxConcurrency());
        instance.setMaxWait(this.getMaxWait());
    }

    protected TaskThrottleModel newInstance(){
        return (TaskThrottleModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
