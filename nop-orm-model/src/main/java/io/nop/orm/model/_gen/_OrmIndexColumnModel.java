package io.nop.orm.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.orm.model.OrmIndexColumnModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [196:14:0:0]/nop/schema/orm/entity.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OrmIndexColumnModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: desc
     * 
     */
    private java.lang.Boolean _desc ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     * 
     * xml name: desc
     *  
     */
    
    public java.lang.Boolean getDesc(){
      return _desc;
    }

    
    public void setDesc(java.lang.Boolean value){
        checkAllowChange();
        
        this._desc = value;
           
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
        
        out.putNotNull("desc",this.getDesc());
        out.putNotNull("name",this.getName());
    }

    public OrmIndexColumnModel cloneInstance(){
        OrmIndexColumnModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OrmIndexColumnModel instance){
        super.copyTo(instance);
        
        instance.setDesc(this.getDesc());
        instance.setName(this.getName());
    }

    protected OrmIndexColumnModel newInstance(){
        return (OrmIndexColumnModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
