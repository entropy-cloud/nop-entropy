package io.nop.dao.dialect.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [85:10:0:0]/nop/schema/orm/dialect.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
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

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("name",this.getName());
        out.put("useSqlState",this.getUseSqlState());
        out.put("values",this.getValues());
    }
}
 // resume CPD analysis - CPD-ON
