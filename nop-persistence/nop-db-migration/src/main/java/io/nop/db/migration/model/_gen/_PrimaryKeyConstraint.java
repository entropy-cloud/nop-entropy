package io.nop.db.migration.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.db.migration.model.PrimaryKeyConstraint;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db-migration/migration.xdef <p>
 * 主键约束（可选，也可以在列上直接指定 primaryKey）
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _PrimaryKeyConstraint extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: columnNames
     * 主键列名列表（逗号分隔）
     */
    private java.util.Set<java.lang.String> _columnNames ;
    
    /**
     *  
     * xml name: name
     * 约束名
     */
    private java.lang.String _name ;
    
    /**
     * 
     * xml name: columnNames
     *  主键列名列表（逗号分隔）
     */
    
    public java.util.Set<java.lang.String> getColumnNames(){
      return _columnNames;
    }

    
    public void setColumnNames(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._columnNames = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  约束名
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
        
        out.putNotNull("columnNames",this.getColumnNames());
        out.putNotNull("name",this.getName());
    }

    public PrimaryKeyConstraint cloneInstance(){
        PrimaryKeyConstraint instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(PrimaryKeyConstraint instance){
        super.copyTo(instance);
        
        instance.setColumnNames(this.getColumnNames());
        instance.setName(this.getName());
    }

    protected PrimaryKeyConstraint newInstance(){
        return (PrimaryKeyConstraint) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
