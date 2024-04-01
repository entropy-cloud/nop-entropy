package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.LoopTaskStepModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [210:14:0:0]/nop/schema/task/task.xdef <p>
 * 类似于for循环语句，不断执行body
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _LoopTaskStepModel extends io.nop.task.model.TaskStepsModel {
    
    /**
     *  
     * xml name: indexName
     * 
     */
    private java.lang.String _indexName ;
    
    /**
     *  
     * xml name: itemsExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _itemsExpr ;
    
    /**
     *  
     * xml name: maxCount
     * 
     */
    private int _maxCount  = 0;
    
    /**
     *  
     * xml name: until
     * 
     */
    private io.nop.core.lang.eval.IEvalPredicate _until ;
    
    /**
     *  
     * xml name: varName
     * 
     */
    private java.lang.String _varName ;
    
    /**
     *  
     * xml name: varType
     * 
     */
    private io.nop.core.type.IGenericType _varType ;
    
    /**
     * 
     * xml name: indexName
     *  
     */
    
    public java.lang.String getIndexName(){
      return _indexName;
    }

    
    public void setIndexName(java.lang.String value){
        checkAllowChange();
        
        this._indexName = value;
           
    }

    
    /**
     * 
     * xml name: itemsExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getItemsExpr(){
      return _itemsExpr;
    }

    
    public void setItemsExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._itemsExpr = value;
           
    }

    
    /**
     * 
     * xml name: maxCount
     *  
     */
    
    public int getMaxCount(){
      return _maxCount;
    }

    
    public void setMaxCount(int value){
        checkAllowChange();
        
        this._maxCount = value;
           
    }

    
    /**
     * 
     * xml name: until
     *  
     */
    
    public io.nop.core.lang.eval.IEvalPredicate getUntil(){
      return _until;
    }

    
    public void setUntil(io.nop.core.lang.eval.IEvalPredicate value){
        checkAllowChange();
        
        this._until = value;
           
    }

    
    /**
     * 
     * xml name: varName
     *  
     */
    
    public java.lang.String getVarName(){
      return _varName;
    }

    
    public void setVarName(java.lang.String value){
        checkAllowChange();
        
        this._varName = value;
           
    }

    
    /**
     * 
     * xml name: varType
     *  
     */
    
    public io.nop.core.type.IGenericType getVarType(){
      return _varType;
    }

    
    public void setVarType(io.nop.core.type.IGenericType value){
        checkAllowChange();
        
        this._varType = value;
           
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
        
        out.putNotNull("indexName",this.getIndexName());
        out.putNotNull("itemsExpr",this.getItemsExpr());
        out.putNotNull("maxCount",this.getMaxCount());
        out.putNotNull("until",this.getUntil());
        out.putNotNull("varName",this.getVarName());
        out.putNotNull("varType",this.getVarType());
    }

    public LoopTaskStepModel cloneInstance(){
        LoopTaskStepModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(LoopTaskStepModel instance){
        super.copyTo(instance);
        
        instance.setIndexName(this.getIndexName());
        instance.setItemsExpr(this.getItemsExpr());
        instance.setMaxCount(this.getMaxCount());
        instance.setUntil(this.getUntil());
        instance.setVarName(this.getVarName());
        instance.setVarType(this.getVarType());
    }

    protected LoopTaskStepModel newInstance(){
        return (LoopTaskStepModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
