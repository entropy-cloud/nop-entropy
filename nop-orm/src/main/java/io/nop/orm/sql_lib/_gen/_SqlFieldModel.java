package io.nop.orm.sql_lib._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.orm.sql_lib.SqlFieldModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/orm/sql-lib.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _SqlFieldModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: as
     * 如果指定了as，则重命名该字段值
     */
    private java.lang.String _as ;
    
    /**
     *  
     * xml name: computeExpr
     * 如果指定了computeExpr，则这个值不从数据库获取，直接在java中计算得到。计算表达式在所有其他字段值获取之后执行。
     */
    private io.nop.core.lang.eval.IEvalFunction _computeExpr ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: stdDataType
     * 如果指定这个属性，则从DataSet读取后会执行转型操作。比如按照DATETIME类型读取，但是转换为String返回等
     */
    private io.nop.commons.type.StdDataType _stdDataType ;
    
    /**
     *  
     * xml name: stdSqlType
     * DataSet上提供了getBoolean等一系列读取方法，stdSqlType指定使用哪个方法去读取。
     */
    private io.nop.commons.type.StdSqlType _stdSqlType ;
    
    /**
     * 
     * xml name: as
     *  如果指定了as，则重命名该字段值
     */
    
    public java.lang.String getAs(){
      return _as;
    }

    
    public void setAs(java.lang.String value){
        checkAllowChange();
        
        this._as = value;
           
    }

    
    /**
     * 
     * xml name: computeExpr
     *  如果指定了computeExpr，则这个值不从数据库获取，直接在java中计算得到。计算表达式在所有其他字段值获取之后执行。
     */
    
    public io.nop.core.lang.eval.IEvalFunction getComputeExpr(){
      return _computeExpr;
    }

    
    public void setComputeExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._computeExpr = value;
           
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
     * xml name: stdDataType
     *  如果指定这个属性，则从DataSet读取后会执行转型操作。比如按照DATETIME类型读取，但是转换为String返回等
     */
    
    public io.nop.commons.type.StdDataType getStdDataType(){
      return _stdDataType;
    }

    
    public void setStdDataType(io.nop.commons.type.StdDataType value){
        checkAllowChange();
        
        this._stdDataType = value;
           
    }

    
    /**
     * 
     * xml name: stdSqlType
     *  DataSet上提供了getBoolean等一系列读取方法，stdSqlType指定使用哪个方法去读取。
     */
    
    public io.nop.commons.type.StdSqlType getStdSqlType(){
      return _stdSqlType;
    }

    
    public void setStdSqlType(io.nop.commons.type.StdSqlType value){
        checkAllowChange();
        
        this._stdSqlType = value;
           
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
        
        out.putNotNull("as",this.getAs());
        out.putNotNull("computeExpr",this.getComputeExpr());
        out.putNotNull("name",this.getName());
        out.putNotNull("stdDataType",this.getStdDataType());
        out.putNotNull("stdSqlType",this.getStdSqlType());
    }

    public SqlFieldModel cloneInstance(){
        SqlFieldModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(SqlFieldModel instance){
        super.copyTo(instance);
        
        instance.setAs(this.getAs());
        instance.setComputeExpr(this.getComputeExpr());
        instance.setName(this.getName());
        instance.setStdDataType(this.getStdDataType());
        instance.setStdSqlType(this.getStdSqlType());
    }

    protected SqlFieldModel newInstance(){
        return (SqlFieldModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
