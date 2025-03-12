package io.nop.batch.dsl.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.batch.dsl.model.BatchHistoryStoreModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/batch.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BatchHistoryStoreModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: bean
     * 
     */
    private java.lang.String _bean ;
    
    /**
     *  
     * xml name: onlySaveLastError
     * 如果为true，则只保存最后一次执行失败的信息，否则保存所有执行失败的信息
     */
    private boolean _onlySaveLastError  = false;
    
    /**
     *  
     * xml name: recordInfoExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _recordInfoExpr ;
    
    /**
     *  
     * xml name: recordKeyExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _recordKeyExpr ;
    
    /**
     * 
     * xml name: bean
     *  
     */
    
    public java.lang.String getBean(){
      return _bean;
    }

    
    public void setBean(java.lang.String value){
        checkAllowChange();
        
        this._bean = value;
           
    }

    
    /**
     * 
     * xml name: onlySaveLastError
     *  如果为true，则只保存最后一次执行失败的信息，否则保存所有执行失败的信息
     */
    
    public boolean isOnlySaveLastError(){
      return _onlySaveLastError;
    }

    
    public void setOnlySaveLastError(boolean value){
        checkAllowChange();
        
        this._onlySaveLastError = value;
           
    }

    
    /**
     * 
     * xml name: recordInfoExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getRecordInfoExpr(){
      return _recordInfoExpr;
    }

    
    public void setRecordInfoExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._recordInfoExpr = value;
           
    }

    
    /**
     * 
     * xml name: recordKeyExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getRecordKeyExpr(){
      return _recordKeyExpr;
    }

    
    public void setRecordKeyExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._recordKeyExpr = value;
           
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
        
        out.putNotNull("bean",this.getBean());
        out.putNotNull("onlySaveLastError",this.isOnlySaveLastError());
        out.putNotNull("recordInfoExpr",this.getRecordInfoExpr());
        out.putNotNull("recordKeyExpr",this.getRecordKeyExpr());
    }

    public BatchHistoryStoreModel cloneInstance(){
        BatchHistoryStoreModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BatchHistoryStoreModel instance){
        super.copyTo(instance);
        
        instance.setBean(this.getBean());
        instance.setOnlySaveLastError(this.isOnlySaveLastError());
        instance.setRecordInfoExpr(this.getRecordInfoExpr());
        instance.setRecordKeyExpr(this.getRecordKeyExpr());
    }

    protected BatchHistoryStoreModel newInstance(){
        return (BatchHistoryStoreModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
