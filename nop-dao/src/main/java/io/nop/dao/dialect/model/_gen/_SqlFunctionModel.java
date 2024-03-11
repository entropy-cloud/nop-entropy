package io.nop.dao.dialect.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.dao.dialect.model.SqlFunctionModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [115:10:0:0]/nop/schema/orm/dialect.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _SqlFunctionModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: className
     * 
     */
    private java.lang.String _className ;
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: testSql
     * 
     */
    private java.lang.String _testSql ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: className
     *  
     */
    
    public java.lang.String getClassName(){
      return _className;
    }

    
    public void setClassName(java.lang.String value){
        checkAllowChange();
        
        this._className = value;
           
    }

    
    /**
     * 
     * xml name: description
     *  
     */
    
    public java.lang.String getDescription(){
      return _description;
    }

    
    public void setDescription(java.lang.String value){
        checkAllowChange();
        
        this._description = value;
           
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

    
    /**
     * 
     * xml name: testSql
     *  
     */
    
    public java.lang.String getTestSql(){
      return _testSql;
    }

    
    public void setTestSql(java.lang.String value){
        checkAllowChange();
        
        this._testSql = value;
           
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
        
        out.putNotNull("className",this.getClassName());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("name",this.getName());
        out.putNotNull("testSql",this.getTestSql());
        out.putNotNull("type",this.getType());
    }

    public SqlFunctionModel cloneInstance(){
        SqlFunctionModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(SqlFunctionModel instance){
        super.copyTo(instance);
        
        instance.setClassName(this.getClassName());
        instance.setDescription(this.getDescription());
        instance.setName(this.getName());
        instance.setTestSql(this.getTestSql());
        instance.setType(this.getType());
    }

    protected SqlFunctionModel newInstance(){
        return (SqlFunctionModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
