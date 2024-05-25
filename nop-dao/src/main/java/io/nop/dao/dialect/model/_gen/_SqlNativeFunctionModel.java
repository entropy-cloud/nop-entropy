package io.nop.dao.dialect.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.dao.dialect.model.SqlNativeFunctionModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [132:10:0:0]/nop/schema/orm/dialect.xdef <p>
 * sql数据库内部支持的原生函数。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _SqlNativeFunctionModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: argTypes
     * 指定函数参数的类型
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
     * xml name: hasParenthesis
     * 数据库引擎是否要求函数调用要有括号。在eql语法中总是要求函数调用有括号，但是数据库引擎可能强制要求没有。
     * 例如oracle中的sysdate
     */
    private java.lang.Boolean _hasParenthesis  = true;
    
    /**
     *  
     * xml name: maxArgCount
     * 
     */
    private java.lang.Integer _maxArgCount ;
    
    /**
     *  
     * xml name: minArgCount
     * 
     */
    private java.lang.Integer _minArgCount ;
    
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
     * xml name: realName
     * 数据库中的函数名。在dialect中声明的函数名为标准函数名，它会尽量在多个数据库之间保持一致。realName为对应的数据库中的实现函数名
     */
    private java.lang.String _realName ;
    
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
     * xml name: special
     * 需要特殊语法支持的函数
     */
    private java.lang.Boolean _special ;
    
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
     * xml name: argTypes
     *  指定函数参数的类型
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
     * xml name: hasParenthesis
     *  数据库引擎是否要求函数调用要有括号。在eql语法中总是要求函数调用有括号，但是数据库引擎可能强制要求没有。
     * 例如oracle中的sysdate
     */
    
    public java.lang.Boolean getHasParenthesis(){
      return _hasParenthesis;
    }

    
    public void setHasParenthesis(java.lang.Boolean value){
        checkAllowChange();
        
        this._hasParenthesis = value;
           
    }

    
    /**
     * 
     * xml name: maxArgCount
     *  
     */
    
    public java.lang.Integer getMaxArgCount(){
      return _maxArgCount;
    }

    
    public void setMaxArgCount(java.lang.Integer value){
        checkAllowChange();
        
        this._maxArgCount = value;
           
    }

    
    /**
     * 
     * xml name: minArgCount
     *  
     */
    
    public java.lang.Integer getMinArgCount(){
      return _minArgCount;
    }

    
    public void setMinArgCount(java.lang.Integer value){
        checkAllowChange();
        
        this._minArgCount = value;
           
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
     * xml name: realName
     *  数据库中的函数名。在dialect中声明的函数名为标准函数名，它会尽量在多个数据库之间保持一致。realName为对应的数据库中的实现函数名
     */
    
    public java.lang.String getRealName(){
      return _realName;
    }

    
    public void setRealName(java.lang.String value){
        checkAllowChange();
        
        this._realName = value;
           
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
     * xml name: special
     *  需要特殊语法支持的函数
     */
    
    public java.lang.Boolean getSpecial(){
      return _special;
    }

    
    public void setSpecial(java.lang.Boolean value){
        checkAllowChange();
        
        this._special = value;
           
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
        
        out.putNotNull("argTypes",this.getArgTypes());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("hasParenthesis",this.getHasParenthesis());
        out.putNotNull("maxArgCount",this.getMaxArgCount());
        out.putNotNull("minArgCount",this.getMinArgCount());
        out.putNotNull("name",this.getName());
        out.putNotNull("onlyForWindowExpr",this.isOnlyForWindowExpr());
        out.putNotNull("realName",this.getRealName());
        out.putNotNull("returnFirstArgType",this.isReturnFirstArgType());
        out.putNotNull("returnType",this.getReturnType());
        out.putNotNull("special",this.getSpecial());
        out.putNotNull("testSql",this.getTestSql());
        out.putNotNull("type",this.getType());
    }

    public SqlNativeFunctionModel cloneInstance(){
        SqlNativeFunctionModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(SqlNativeFunctionModel instance){
        super.copyTo(instance);
        
        instance.setArgTypes(this.getArgTypes());
        instance.setDescription(this.getDescription());
        instance.setHasParenthesis(this.getHasParenthesis());
        instance.setMaxArgCount(this.getMaxArgCount());
        instance.setMinArgCount(this.getMinArgCount());
        instance.setName(this.getName());
        instance.setOnlyForWindowExpr(this.isOnlyForWindowExpr());
        instance.setRealName(this.getRealName());
        instance.setReturnFirstArgType(this.isReturnFirstArgType());
        instance.setReturnType(this.getReturnType());
        instance.setSpecial(this.getSpecial());
        instance.setTestSql(this.getTestSql());
        instance.setType(this.getType());
    }

    protected SqlNativeFunctionModel newInstance(){
        return (SqlNativeFunctionModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
