package io.nop.orm.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.orm.model.OrmUniqueKeyModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [185:10:0:0]/nop/schema/orm/entity.xdef <p>
 * 唯一键
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OrmUniqueKeyModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: columns
     * 逗号分隔的列名（name）的列表
     */
    private java.util.List<java.lang.String> _columns ;
    
    /**
     *  
     * xml name: comment
     * 
     */
    private java.lang.String _comment ;
    
    /**
     *  
     * xml name: constraint
     * 唯一键在数据库中所对应的约束名
     */
    private java.lang.String _constraint ;
    
    /**
     *  
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     * 
     * xml name: columns
     *  逗号分隔的列名（name）的列表
     */
    
    public java.util.List<java.lang.String> getColumns(){
      return _columns;
    }

    
    public void setColumns(java.util.List<java.lang.String> value){
        checkAllowChange();
        
        this._columns = value;
           
    }

    
    /**
     * 
     * xml name: comment
     *  
     */
    
    public java.lang.String getComment(){
      return _comment;
    }

    
    public void setComment(java.lang.String value){
        checkAllowChange();
        
        this._comment = value;
           
    }

    
    /**
     * 
     * xml name: constraint
     *  唯一键在数据库中所对应的约束名
     */
    
    public java.lang.String getConstraint(){
      return _constraint;
    }

    
    public void setConstraint(java.lang.String value){
        checkAllowChange();
        
        this._constraint = value;
           
    }

    
    /**
     * 
     * xml name: displayName
     *  
     */
    
    public java.lang.String getDisplayName(){
      return _displayName;
    }

    
    public void setDisplayName(java.lang.String value){
        checkAllowChange();
        
        this._displayName = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  
     */
    
    public java.lang.String getName(){
      return _name;
    }

    
    public void setName(java.lang.String value){
        checkAllowChange();
        
        this._name = value;
           
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
        
        out.put("columns",this.getColumns());
        out.put("comment",this.getComment());
        out.put("constraint",this.getConstraint());
        out.put("displayName",this.getDisplayName());
        out.put("name",this.getName());
    }

    public OrmUniqueKeyModel cloneInstance(){
        OrmUniqueKeyModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OrmUniqueKeyModel instance){
        super.copyTo(instance);
        
        instance.setColumns(this.getColumns());
        instance.setComment(this.getComment());
        instance.setConstraint(this.getConstraint());
        instance.setDisplayName(this.getDisplayName());
        instance.setName(this.getName());
    }

    protected OrmUniqueKeyModel newInstance(){
        return (OrmUniqueKeyModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
