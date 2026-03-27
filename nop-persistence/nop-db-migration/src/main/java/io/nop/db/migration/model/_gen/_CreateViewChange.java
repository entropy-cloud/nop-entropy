package io.nop.db.migration.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.db.migration.model.CreateViewChange;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db-migration/migration.xdef <p>
 * 创建视图
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _CreateViewChange extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: name
     * 视图名
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: remark
     * 视图注释
     */
    private java.lang.String _remark ;
    
    /**
     *  
     * xml name: schemaName
     * 模式名（可选）
     */
    private java.lang.String _schemaName ;
    
    /**
     *  
     * xml name: selectSql
     * 
     */
    private java.lang.String _selectSql ;
    
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
     * xml name: name
     *  视图名
     */
    
    public java.lang.String getName(){
      return _name;
    }

    
    public void setName(java.lang.String value){
        checkAllowChange();
        
        this._name = value;
           
    }

    
    /**
     * 
     * xml name: remark
     *  视图注释
     */
    
    public java.lang.String getRemark(){
      return _remark;
    }

    
    public void setRemark(java.lang.String value){
        checkAllowChange();
        
        this._remark = value;
           
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
     * xml name: selectSql
     *  
     */
    
    public java.lang.String getSelectSql(){
      return _selectSql;
    }

    
    public void setSelectSql(java.lang.String value){
        checkAllowChange();
        
        this._selectSql = value;
           
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
        out.putNotNull("name",this.getName());
        out.putNotNull("remark",this.getRemark());
        out.putNotNull("schemaName",this.getSchemaName());
        out.putNotNull("selectSql",this.getSelectSql());
        out.putNotNull("type",this.getType());
    }

    public CreateViewChange cloneInstance(){
        CreateViewChange instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(CreateViewChange instance){
        super.copyTo(instance);
        
        instance.setId(this.getId());
        instance.setName(this.getName());
        instance.setRemark(this.getRemark());
        instance.setSchemaName(this.getSchemaName());
        instance.setSelectSql(this.getSelectSql());
        instance.setType(this.getType());
    }

    protected CreateViewChange newInstance(){
        return (CreateViewChange) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
