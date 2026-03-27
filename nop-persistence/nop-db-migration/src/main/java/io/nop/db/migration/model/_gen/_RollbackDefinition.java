package io.nop.db.migration.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.db.migration.model.RollbackDefinition;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db-migration/migration.xdef <p>
 * 回滚定义（Rollback）
 * 定义如何回滚此迁移
 * 策略：
 * 1. 如果没有定义 rollback，Nop 会尝试自动生成回滚 SQL（仅限简单变更）
 * 2. 如果定义了 rollback，则使用定义的回滚逻辑
 * 3. 如果设置了 rollbackImpossible="true"，则标记此迁移不可回滚
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _RollbackDefinition extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: changes
     * 回滚变更集，结构与 changeset 相同
     * 通常包含与 changeset 相反的操作
     */
    private KeyedList<io.nop.db.migration.model.DbChangeModel> _changes = KeyedList.emptyList();
    
    /**
     *  
     * xml name: impossible
     * 
     */
    private boolean _impossible  = false;
    
    /**
     * 
     * xml name: changes
     *  回滚变更集，结构与 changeset 相同
     * 通常包含与 changeset 相反的操作
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
     * xml name: impossible
     *  
     */
    
    public boolean isImpossible(){
      return _impossible;
    }

    
    public void setImpossible(boolean value){
        checkAllowChange();
        
        this._impossible = value;
           
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
        out.putNotNull("impossible",this.isImpossible());
    }

    public RollbackDefinition cloneInstance(){
        RollbackDefinition instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RollbackDefinition instance){
        super.copyTo(instance);
        
        instance.setChanges(this.getChanges());
        instance.setImpossible(this.isImpossible());
    }

    protected RollbackDefinition newInstance(){
        return (RollbackDefinition) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
