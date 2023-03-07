package io.nop.dao.dialect.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [115:10:0:0]/nop/schema/orm/dialect.xdef <p>
 * sql数据库内部支持的原生函数。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _SqlNativeFunctionModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: argTypes
     * 指定函数参数的类型
     */
    private java.util.List<io.nop.core.lang.sql.StdSqlType> _argTypes ;
    
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
     * xml name: realName
     * 数据库中的函数名。在dialect中声明的函数名为标准函数名，它会尽量在多个数据库之间保持一致。realName为对应的数据库中的实现函数名
     */
    private java.lang.String _realName ;
    
    /**
     *  
     * xml name: returnType
     * 
     */
    private io.nop.core.lang.sql.StdSqlType _returnType ;
    
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
    
    public java.util.List<io.nop.core.lang.sql.StdSqlType> getArgTypes(){
      return _argTypes;
    }

    
    public void setArgTypes(java.util.List<io.nop.core.lang.sql.StdSqlType> value){
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
     * xml name: returnType
     *  
     */
    
    public io.nop.core.lang.sql.StdSqlType getReturnType(){
      return _returnType;
    }

    
    public void setReturnType(io.nop.core.lang.sql.StdSqlType value){
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

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("argTypes",this.getArgTypes());
        out.put("description",this.getDescription());
        out.put("hasParenthesis",this.getHasParenthesis());
        out.put("maxArgCount",this.getMaxArgCount());
        out.put("minArgCount",this.getMinArgCount());
        out.put("name",this.getName());
        out.put("realName",this.getRealName());
        out.put("returnType",this.getReturnType());
        out.put("special",this.getSpecial());
        out.put("testSql",this.getTestSql());
        out.put("type",this.getType());
    }
}
 // resume CPD analysis - CPD-ON
