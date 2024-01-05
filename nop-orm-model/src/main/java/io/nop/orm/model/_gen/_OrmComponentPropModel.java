package io.nop.orm.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.orm.model.OrmComponentPropModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [176:14:0:0]/nop/schema/orm/entity.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OrmComponentPropModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: column
     * 
     */
    private java.lang.String _column ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     * 
     * xml name: column
     *  
     */
    
    public java.lang.String getColumn(){
      return _column;
    }

    
    public void setColumn(java.lang.String value){
        checkAllowChange();
        
        this._column = value;
           
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
        
        out.put("column",this.getColumn());
        out.put("name",this.getName());
    }

    public OrmComponentPropModel cloneInstance(){
        OrmComponentPropModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OrmComponentPropModel instance){
        super.copyTo(instance);
        
        instance.setColumn(this.getColumn());
        instance.setName(this.getName());
    }

    protected OrmComponentPropModel newInstance(){
        return (OrmComponentPropModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
