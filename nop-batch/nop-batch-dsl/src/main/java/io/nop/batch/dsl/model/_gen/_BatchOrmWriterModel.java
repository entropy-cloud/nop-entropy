package io.nop.batch.dsl.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.batch.dsl.model.BatchOrmWriterModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/batch.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BatchOrmWriterModel extends io.nop.core.resource.component.AbstractComponentModel {
    
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
     * xml name: entityName
     * 
     */
    private java.lang.String _entityName ;
    
    /**
     *  
     * xml name: keyFields
     * 
     */
    private java.util.Set<java.lang.String> _keyFields ;
    
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
     * xml name: entityName
     *  
     */
    
    public java.lang.String getEntityName(){
      return _entityName;
    }

    
    public void setEntityName(java.lang.String value){
        checkAllowChange();
        
        this._entityName = value;
           
    }

    
    /**
     * 
     * xml name: keyFields
     *  
     */
    
    public java.util.Set<java.lang.String> getKeyFields(){
      return _keyFields;
    }

    
    public void setKeyFields(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._keyFields = value;
           
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
        out.putNotNull("entityName",this.getEntityName());
        out.putNotNull("keyFields",this.getKeyFields());
    }

    public BatchOrmWriterModel cloneInstance(){
        BatchOrmWriterModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BatchOrmWriterModel instance){
        super.copyTo(instance);
        
        instance.setAllowInsert(this.isAllowInsert());
        instance.setAllowUpdate(this.isAllowUpdate());
        instance.setEntityName(this.getEntityName());
        instance.setKeyFields(this.getKeyFields());
    }

    protected BatchOrmWriterModel newInstance(){
        return (BatchOrmWriterModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
