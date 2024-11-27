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
     * xml name: fields
     * 
     */
    private KeyedList<io.nop.batch.dsl.model.BatchWriteFieldModel> _fields = KeyedList.emptyList();
    
    /**
     *  
     * xml name: keyFields
     * 
     */
    private java.util.Set<java.lang.String> _keyFields ;
    
    /**
     *  
     * xml name: querySpace
     * 
     */
    private java.lang.String _querySpace ;
    
    /**
     *  
     * xml name: tableName
     * 
     */
    private java.lang.String _tableName ;
    
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
     * xml name: fields
     *  
     */
    
    public java.util.List<io.nop.batch.dsl.model.BatchWriteFieldModel> getFields(){
      return _fields;
    }

    
    public void setFields(java.util.List<io.nop.batch.dsl.model.BatchWriteFieldModel> value){
        checkAllowChange();
        
        this._fields = KeyedList.fromList(value, io.nop.batch.dsl.model.BatchWriteFieldModel::getName);
           
    }

    
    public io.nop.batch.dsl.model.BatchWriteFieldModel getField(String name){
        return this._fields.getByKey(name);
    }

    public boolean hasField(String name){
        return this._fields.containsKey(name);
    }

    public void addField(io.nop.batch.dsl.model.BatchWriteFieldModel item) {
        checkAllowChange();
        java.util.List<io.nop.batch.dsl.model.BatchWriteFieldModel> list = this.getFields();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.batch.dsl.model.BatchWriteFieldModel::getName);
            setFields(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_fields(){
        return this._fields.keySet();
    }

    public boolean hasFields(){
        return !this._fields.isEmpty();
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

    
    /**
     * 
     * xml name: querySpace
     *  
     */
    
    public java.lang.String getQuerySpace(){
      return _querySpace;
    }

    
    public void setQuerySpace(java.lang.String value){
        checkAllowChange();
        
        this._querySpace = value;
           
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

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._fields = io.nop.api.core.util.FreezeHelper.deepFreeze(this._fields);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("allowInsert",this.isAllowInsert());
        out.putNotNull("allowUpdate",this.isAllowUpdate());
        out.putNotNull("fields",this.getFields());
        out.putNotNull("keyFields",this.getKeyFields());
        out.putNotNull("querySpace",this.getQuerySpace());
        out.putNotNull("tableName",this.getTableName());
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
        instance.setFields(this.getFields());
        instance.setKeyFields(this.getKeyFields());
        instance.setQuerySpace(this.getQuerySpace());
        instance.setTableName(this.getTableName());
    }

    protected BatchJdbcWriterModel newInstance(){
        return (BatchJdbcWriterModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
