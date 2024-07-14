package io.nop.batch.dsl.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.batch.dsl.model.BatchJdbcWriterModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/batch.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BatchJdbcWriterModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: allowInsert
     * 
     */
    private boolean _allowInsert  = true;
    
    /**
     *  
     * xml name: allowUpdate
     * 
     */
    private boolean _allowUpdate  = false;
    
    /**
     *  
     * xml name: tableName
     * 
     */
    private java.lang.String _tableName ;
    
    /**
     *  
     * xml name: uniqueKey
     * 
     */
    private java.util.Set<java.lang.String> _uniqueKey ;
    
    /**
     * 
     * xml name: allowInsert
     *  
     */
    
    public boolean isAllowInsert(){
      return _allowInsert;
    }

    
    public void setAllowInsert(boolean value){
        checkAllowChange();
        
        this._allowInsert = value;
           
    }

    
    /**
     * 
     * xml name: allowUpdate
     *  
     */
    
    public boolean isAllowUpdate(){
      return _allowUpdate;
    }

    
    public void setAllowUpdate(boolean value){
        checkAllowChange();
        
        this._allowUpdate = value;
           
    }

    
    /**
     * 
     * xml name: tableName
     *  
     */
    
    public java.lang.String getTableName(){
      return _tableName;
    }

    
    public void setTableName(java.lang.String value){
        checkAllowChange();
        
        this._tableName = value;
           
    }

    
    /**
     * 
     * xml name: uniqueKey
     *  
     */
    
    public java.util.Set<java.lang.String> getUniqueKey(){
      return _uniqueKey;
    }

    
    public void setUniqueKey(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._uniqueKey = value;
           
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
        
        out.putNotNull("allowInsert",this.isAllowInsert());
        out.putNotNull("allowUpdate",this.isAllowUpdate());
        out.putNotNull("tableName",this.getTableName());
        out.putNotNull("uniqueKey",this.getUniqueKey());
    }

    public BatchJdbcWriterModel cloneInstance(){
        BatchJdbcWriterModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BatchJdbcWriterModel instance){
        super.copyTo(instance);
        
        instance.setAllowInsert(this.isAllowInsert());
        instance.setAllowUpdate(this.isAllowUpdate());
        instance.setTableName(this.getTableName());
        instance.setUniqueKey(this.getUniqueKey());
    }

    protected BatchJdbcWriterModel newInstance(){
        return (BatchJdbcWriterModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
