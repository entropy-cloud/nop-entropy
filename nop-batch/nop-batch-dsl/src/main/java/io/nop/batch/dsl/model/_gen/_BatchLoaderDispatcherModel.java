package io.nop.batch.dsl.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.batch.dsl.model.BatchLoaderDispatcherModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/batch.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BatchLoaderDispatcherModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: executor
     * 
     */
    private java.lang.String _executor ;
    
    /**
     *  
     * xml name: fetchThreadCount
     * 
     */
    private int _fetchThreadCount  = 1;
    
    /**
     *  
     * xml name: loadBatchSize
     * 
     */
    private int _loadBatchSize ;
    
    /**
     *  
     * xml name: partitionFn
     * 如果没有指定partitionIndexField，可以执行代码动态计算得到partitionIndex
     */
    private io.nop.core.lang.eval.IEvalFunction _partitionFn ;
    
    /**
     *  
     * xml name: partitionIndexField
     * 
     */
    private java.lang.String _partitionIndexField ;
    
    /**
     * 
     * xml name: executor
     *  
     */
    
    public java.lang.String getExecutor(){
      return _executor;
    }

    
    public void setExecutor(java.lang.String value){
        checkAllowChange();
        
        this._executor = value;
           
    }

    
    /**
     * 
     * xml name: fetchThreadCount
     *  
     */
    
    public int getFetchThreadCount(){
      return _fetchThreadCount;
    }

    
    public void setFetchThreadCount(int value){
        checkAllowChange();
        
        this._fetchThreadCount = value;
           
    }

    
    /**
     * 
     * xml name: loadBatchSize
     *  
     */
    
    public int getLoadBatchSize(){
      return _loadBatchSize;
    }

    
    public void setLoadBatchSize(int value){
        checkAllowChange();
        
        this._loadBatchSize = value;
           
    }

    
    /**
     * 
     * xml name: partitionFn
     *  如果没有指定partitionIndexField，可以执行代码动态计算得到partitionIndex
     */
    
    public io.nop.core.lang.eval.IEvalFunction getPartitionFn(){
      return _partitionFn;
    }

    
    public void setPartitionFn(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._partitionFn = value;
           
    }

    
    /**
     * 
     * xml name: partitionIndexField
     *  
     */
    
    public java.lang.String getPartitionIndexField(){
      return _partitionIndexField;
    }

    
    public void setPartitionIndexField(java.lang.String value){
        checkAllowChange();
        
        this._partitionIndexField = value;
           
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
        
        out.putNotNull("executor",this.getExecutor());
        out.putNotNull("fetchThreadCount",this.getFetchThreadCount());
        out.putNotNull("loadBatchSize",this.getLoadBatchSize());
        out.putNotNull("partitionFn",this.getPartitionFn());
        out.putNotNull("partitionIndexField",this.getPartitionIndexField());
    }

    public BatchLoaderDispatcherModel cloneInstance(){
        BatchLoaderDispatcherModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BatchLoaderDispatcherModel instance){
        super.copyTo(instance);
        
        instance.setExecutor(this.getExecutor());
        instance.setFetchThreadCount(this.getFetchThreadCount());
        instance.setLoadBatchSize(this.getLoadBatchSize());
        instance.setPartitionFn(this.getPartitionFn());
        instance.setPartitionIndexField(this.getPartitionIndexField());
    }

    protected BatchLoaderDispatcherModel newInstance(){
        return (BatchLoaderDispatcherModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
