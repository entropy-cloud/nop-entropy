package io.nop.dao.dialect.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.dao.dialect.model.SqlTemplateModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [104:10:0:0]/nop/schema/orm/dialect.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _SqlTemplateModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: argTypes
     * 
     */
    private java.util.List<io.nop.commons.type.StdSqlType> _argTypes ;
    
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
     * xml name: onlyForWindowExpr
     * 
     */
    private boolean _onlyForWindowExpr  = false;
    
    /**
     *  
     * xml name: returnFirstArgType
     * 返回类型是否与第一个参数的类型相同
     */
    private boolean _returnFirstArgType  = false;
    
    /**
     *  
     * xml name: returnType
     * 
     */
    private io.nop.commons.type.StdSqlType _returnType ;
    
    /**
     *  
     * xml name: source
     * 
     */
    private java.lang.String _source ;
    
    /**
     *  
     * xml name: testSql
     * 如果非空，则在单元测试中调用此函数来测试数据库是否支持此函数
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
     * xml name: argTypes
     *  
     */
    
    public java.util.List<io.nop.commons.type.StdSqlType> getArgTypes(){
      return _argTypes;
    }

    
    public void setArgTypes(java.util.List<io.nop.commons.type.StdSqlType> value){
        checkAllowChange();
        
        this._argTypes = value;
           
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
     * xml name: onlyForWindowExpr
     *  
     */
    
    public boolean isOnlyForWindowExpr(){
      return _onlyForWindowExpr;
    }

    
    public void setOnlyForWindowExpr(boolean value){
        checkAllowChange();
        
        this._onlyForWindowExpr = value;
           
    }

    
    /**
     * 
     * xml name: returnFirstArgType
     *  返回类型是否与第一个参数的类型相同
     */
    
    public boolean isReturnFirstArgType(){
      return _returnFirstArgType;
    }

    
    public void setReturnFirstArgType(boolean value){
        checkAllowChange();
        
        this._returnFirstArgType = value;
           
    }

    
    /**
     * 
     * xml name: returnType
     *  
     */
    
    public io.nop.commons.type.StdSqlType getReturnType(){
      return _returnType;
    }

    
    public void setReturnType(io.nop.commons.type.StdSqlType value){
        checkAllowChange();
        
        this._returnType = value;
           
    }

    
    /**
     * 
     * xml name: source
     *  
     */
    
    public java.lang.String getSource(){
      return _source;
    }

    
    public void setSource(java.lang.String value){
        checkAllowChange();
        
        this._source = value;
           
    }

    
    /**
     * 
     * xml name: testSql
     *  如果非空，则在单元测试中调用此函数来测试数据库是否支持此函数
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
        
        out.putNotNull("argTypes",this.getArgTypes());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("name",this.getName());
        out.putNotNull("onlyForWindowExpr",this.isOnlyForWindowExpr());
        out.putNotNull("returnFirstArgType",this.isReturnFirstArgType());
        out.putNotNull("returnType",this.getReturnType());
        out.putNotNull("source",this.getSource());
        out.putNotNull("testSql",this.getTestSql());
        out.putNotNull("type",this.getType());
    }

    public SqlTemplateModel cloneInstance(){
        SqlTemplateModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(SqlTemplateModel instance){
        super.copyTo(instance);
        
        instance.setArgTypes(this.getArgTypes());
        instance.setDescription(this.getDescription());
        instance.setName(this.getName());
        instance.setOnlyForWindowExpr(this.isOnlyForWindowExpr());
        instance.setReturnFirstArgType(this.isReturnFirstArgType());
        instance.setReturnType(this.getReturnType());
        instance.setSource(this.getSource());
        instance.setTestSql(this.getTestSql());
        instance.setType(this.getType());
    }

    protected SqlTemplateModel newInstance(){
        return (SqlTemplateModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
