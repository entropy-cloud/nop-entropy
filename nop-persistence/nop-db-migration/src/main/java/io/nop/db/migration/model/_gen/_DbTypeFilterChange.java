package io.nop.db.migration.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.db.migration.model.DbTypeFilterChange;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db-migration/migration.xdef <p>
 * 数据库类型过滤
 * 只有在指定的数据库类型上才执行子变更
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _DbTypeFilterChange extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: changes
     * 
     */
    private KeyedList<io.nop.db.migration.model.DbChangeModel> _changes = KeyedList.emptyList();
    
    /**
     *  
     * xml name: dbTypes
     * 数据库类型列表（逗号分隔）
     */
    private java.util.Set<java.lang.String> _dbTypes ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: changes
     *  
     */
    
    public java.util.List<io.nop.db.migration.model.DbChangeModel> getChanges(){
      return _changes;
    }

    
    public void setChanges(java.util.List<io.nop.db.migration.model.DbChangeModel> value){
        checkAllowChange();
        
        this._changes = KeyedList.fromList(value, io.nop.db.migration.model.DbChangeModel::getId);
           
    }

    
    public io.nop.db.migration.model.DbChangeModel getChange(String name){
        return this._changes.getByKey(name);
    }

    public boolean hasChange(String name){
        return this._changes.containsKey(name);
    }

    public void addChange(io.nop.db.migration.model.DbChangeModel item) {
        checkAllowChange();
        java.util.List<io.nop.db.migration.model.DbChangeModel> list = this.getChanges();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.db.migration.model.DbChangeModel::getId);
            setChanges(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_changes(){
        return this._changes.keySet();
    }

    public boolean hasChanges(){
        return !this._changes.isEmpty();
    }
    
    /**
     * 
     * xml name: dbTypes
     *  数据库类型列表（逗号分隔）
     */
    
    public java.util.Set<java.lang.String> getDbTypes(){
      return _dbTypes;
    }

    
    public void setDbTypes(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._dbTypes = value;
           
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

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._changes = io.nop.api.core.util.FreezeHelper.deepFreeze(this._changes);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("changes",this.getChanges());
        out.putNotNull("dbTypes",this.getDbTypes());
        out.putNotNull("id",this.getId());
        out.putNotNull("type",this.getType());
    }

    public DbTypeFilterChange cloneInstance(){
        DbTypeFilterChange instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(DbTypeFilterChange instance){
        super.copyTo(instance);
        
        instance.setChanges(this.getChanges());
        instance.setDbTypes(this.getDbTypes());
        instance.setId(this.getId());
        instance.setType(this.getType());
    }

    protected DbTypeFilterChange newInstance(){
        return (DbTypeFilterChange) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
