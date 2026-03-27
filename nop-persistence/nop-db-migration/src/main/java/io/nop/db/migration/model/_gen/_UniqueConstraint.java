package io.nop.db.migration.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.db.migration.model.UniqueConstraint;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db-migration/migration.xdef <p>
 * 唯一约束
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _UniqueConstraint extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: columnNames
     * 列名列表（逗号分隔）
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
     *  列名列表（逗号分隔）
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

    public UniqueConstraint cloneInstance(){
        UniqueConstraint instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(UniqueConstraint instance){
        super.copyTo(instance);
        
        instance.setColumnNames(this.getColumnNames());
        instance.setName(this.getName());
    }

    protected UniqueConstraint newInstance(){
        return (UniqueConstraint) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
