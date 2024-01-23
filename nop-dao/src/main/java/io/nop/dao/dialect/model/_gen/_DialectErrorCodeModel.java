package io.nop.dao.dialect.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.dao.dialect.model.DialectErrorCodeModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [94:10:0:0]/nop/schema/orm/dialect.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _DialectErrorCodeModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: useSqlState
     * 设置此属性为true时，使用SQLState变量来映射，而不是ErrorCode。例如PostgreSQL数据库
     */
    private java.lang.Boolean _useSqlState ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.util.Set<java.lang.String> _values ;
    
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

    
    /**
     * 
     * xml name: useSqlState
     *  设置此属性为true时，使用SQLState变量来映射，而不是ErrorCode。例如PostgreSQL数据库
     */
    
    public java.lang.Boolean getUseSqlState(){
      return _useSqlState;
    }

    
    public void setUseSqlState(java.lang.Boolean value){
        checkAllowChange();
        
        this._useSqlState = value;
           
    }

    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.util.Set<java.lang.String> getValues(){
      return _values;
    }

    
    public void setValues(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._values = value;
           
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
        
        out.putNotNull("name",this.getName());
        out.putNotNull("useSqlState",this.getUseSqlState());
        out.putNotNull("values",this.getValues());
    }

    public DialectErrorCodeModel cloneInstance(){
        DialectErrorCodeModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(DialectErrorCodeModel instance){
        super.copyTo(instance);
        
        instance.setName(this.getName());
        instance.setUseSqlState(this.getUseSqlState());
        instance.setValues(this.getValues());
    }

    protected DialectErrorCodeModel newInstance(){
        return (DialectErrorCodeModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
