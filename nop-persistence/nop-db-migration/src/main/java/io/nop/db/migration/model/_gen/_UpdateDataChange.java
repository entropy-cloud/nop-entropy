package io.nop.db.migration.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.db.migration.model.UpdateDataChange;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db-migration/migration.xdef <p>
 * 更新数据
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _UpdateDataChange extends io.nop.db.migration.model.DbChangeModel {
    
    /**
     *  
     * xml name: columns
     * 
     */
    private KeyedList<io.nop.db.migration.model.UpdateColumnModel> _columns = KeyedList.emptyList();
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: schemaName
     * 模式名（可选）
     */
    private java.lang.String _schemaName ;
    
    /**
     *  
     * xml name: tableName
     * 表名
     */
    private java.lang.String _tableName ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     *  
     * xml name: where
     * 
     */
    private java.lang.String _where ;
    
    /**
     * 
     * xml name: columns
     *  
     */
    
    public java.util.List<io.nop.db.migration.model.UpdateColumnModel> getColumns(){
      return _columns;
    }

    
    public void setColumns(java.util.List<io.nop.db.migration.model.UpdateColumnModel> value){
        checkAllowChange();
        
        this._columns = KeyedList.fromList(value, io.nop.db.migration.model.UpdateColumnModel::getName);
           
    }

    
    public io.nop.db.migration.model.UpdateColumnModel getColumn(String name){
        return this._columns.getByKey(name);
    }

    public boolean hasColumn(String name){
        return this._columns.containsKey(name);
    }

    public void addColumn(io.nop.db.migration.model.UpdateColumnModel item) {
        checkAllowChange();
        java.util.List<io.nop.db.migration.model.UpdateColumnModel> list = this.getColumns();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.db.migration.model.UpdateColumnModel::getName);
            setColumns(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_columns(){
        return this._columns.keySet();
    }

    public boolean hasColumns(){
        return !this._columns.isEmpty();
    }
    
    /**
     * 
     * xml name: id
     *  
     */
    
    public java.lang.String getId(){
      return _id;
    }

    
    public void setId(java.lang.String value){
        checkAllowChange();
        
        this._id = value;
           
    }

    
    /**
     * 
     * xml name: schemaName
     *  模式名（可选）
     */
    
    public java.lang.String getSchemaName(){
      return _schemaName;
    }

    
    public void setSchemaName(java.lang.String value){
        checkAllowChange();
        
        this._schemaName = value;
           
    }

    
    /**
     * 
     * xml name: tableName
     *  表名
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
     * xml name: 
     *  
     */
    
    public java.lang.String getType(){
      return _type;
    }

    
    public void setType(java.lang.String value){
        checkAllowChange();
        
        this._type = value;
           
    }

    
    /**
     * 
     * xml name: where
     *  
     */
    
    public java.lang.String getWhere(){
      return _where;
    }

    
    public void setWhere(java.lang.String value){
        checkAllowChange();
        
        this._where = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._columns = io.nop.api.core.util.FreezeHelper.deepFreeze(this._columns);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("columns",this.getColumns());
        out.putNotNull("id",this.getId());
        out.putNotNull("schemaName",this.getSchemaName());
        out.putNotNull("tableName",this.getTableName());
        out.putNotNull("type",this.getType());
        out.putNotNull("where",this.getWhere());
    }

    public UpdateDataChange cloneInstance(){
        UpdateDataChange instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(UpdateDataChange instance){
        super.copyTo(instance);
        
        instance.setColumns(this.getColumns());
        instance.setId(this.getId());
        instance.setSchemaName(this.getSchemaName());
        instance.setTableName(this.getTableName());
        instance.setType(this.getType());
        instance.setWhere(this.getWhere());
    }

    protected UpdateDataChange newInstance(){
        return (UpdateDataChange) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
