package io.nop.db.migration.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.db.migration.model.RenameTableChange;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db-migration/migration.xdef <p>
 * 重命名表
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _RenameTableChange extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: newTableName
     * 新表名
     */
    private java.lang.String _newTableName ;
    
    /**
     *  
     * xml name: oldTableName
     * 旧表名
     */
    private java.lang.String _oldTableName ;
    
    /**
     *  
     * xml name: schemaName
     * 模式名（可选）
     */
    private java.lang.String _schemaName ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
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
     * xml name: newTableName
     *  新表名
     */
    
    public java.lang.String getNewTableName(){
      return _newTableName;
    }

    
    public void setNewTableName(java.lang.String value){
        checkAllowChange();
        
        this._newTableName = value;
           
    }

    
    /**
     * 
     * xml name: oldTableName
     *  旧表名
     */
    
    public java.lang.String getOldTableName(){
      return _oldTableName;
    }

    
    public void setOldTableName(java.lang.String value){
        checkAllowChange();
        
        this._oldTableName = value;
           
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
        
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("id",this.getId());
        out.putNotNull("newTableName",this.getNewTableName());
        out.putNotNull("oldTableName",this.getOldTableName());
        out.putNotNull("schemaName",this.getSchemaName());
        out.putNotNull("type",this.getType());
    }

    public RenameTableChange cloneInstance(){
        RenameTableChange instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RenameTableChange instance){
        super.copyTo(instance);
        
        instance.setId(this.getId());
        instance.setNewTableName(this.getNewTableName());
        instance.setOldTableName(this.getOldTableName());
        instance.setSchemaName(this.getSchemaName());
        instance.setType(this.getType());
    }

    protected RenameTableChange newInstance(){
        return (RenameTableChange) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
